# 性能优化系统

战斗节奏上来以后，真正先吃满 CPU 的不是伤害公式，而是**围绕伤害的那一圈副作用**：
飘字的生成与广播、飘字的每帧排版与提交、每次结算都写的日志、
还有一堆「只涨不落」的静态表。这套系统把这些开销收进两个模块统一处理，
并留了一条「在游戏里直接读数」的通道，方便判断改动到底有没有效果。

它不改任何伤害结算结果：所有决策都在**表现层**，把配置关掉就回到逐条飘字的老行为。

| 位置 | 侧 | 职责 |
|---|---|---|
| `core/system/performance/` | 计算侧（服务端 tick 路径） | 生成合并、解析缓存、有上限表、日志限频、每刻快照 |
| `client/performance/` | 渲染侧（客户端每帧路径） | 每帧规划与剔除、字形排版缓存、开销读数、重载失效 |
| `config/PerformanceConfig.java` | 配置 | `minegenshin/performance.toml`，见第四节 |

两个模块都不依赖具体战斗实现，只依赖「伤害飘字」的既有契约
（`api/damage` 的数据结构与 RPC，见 [战斗 · 攻击与伤害管线](docs/systems/combat-attack.md)）。

## 一、计算侧：生成与结算路径

### 1.1 飘字生成合并 `DamageNumberThrottle`

攻速堆高以后，同一个目标每 tick 能刷出好几条飘字，每条都要过一次半径广播、
客户端再各养一条动画。这里在**生成侧**就把连续伤害并成一条会累加的数字，
于是客户端活跃条数从「随攻击次数线性增长」变成「每目标常驻 1 条」。

判定（只在密集攻击下才生效）：

| 条件 | 默认 | 行为 |
|---|---|---|
| 同一目标、同一签名、间隔 ≤ `merge_gap_ms` | 140ms | 并入同一个桶，数字累加 |
| 同一目标在 `flood_window_ms` 内已飘出 ≥ `flood_count` 条 | 500ms / 6 条 | 之后即使间隔变大也继续并入 |
| 以上都不满足 | — | 桶结束，下一条重新单独起一条 |

**签名** = 样式 + 顶色 + 底色 + 斜体 + 文字形状。数字类统一用「数字形状」，
所以 `100` 与 `250` 会并成 `350`；而「月感电」这类固定文案只和完全相同的文案合并，
不会把两种反应并到一条上。

调用约定（重要）：`plan(...)` / `planNumber(...)` 返回的 `Plan` 是**每线程复用**的一只对象，
调用方必须「拿到就读」，不要存进字段或跨调用持有 —— 换来的是每次伤害零分配。
台账按目标 UUID 记状态，8000ms 没动过的目标作废，上限 512 个目标，服务器停机时 `clear()`。

客户端侧的落地：`DamageIndicatorRpc` 的广播多了 `mergeKey` / `merge` 两个字段，
`DamageIndicatorManager.upsert(...)` 按 `mergeKey` 找到那条飘字就地改写
（数字变大、从当前位置重新起跳），找不到或已过期才新起一条。

**观感变化**：默认开启合并，连击数字会累加而不是一条条往外冒。想要原样逐条，把
`damage-number.merge` 关掉即可。

### 1.2 颜色解析缓存 `DamageTextColorCache`

每次生成飘字都要按元素 / 反应去配置里取颜色字符串再 `Integer.decode`，
一次伤害最多走三遍。这里做两级缓存：按配置项记住「上次原始串 → 解析值」
（配置项值不变时只比一次字符串），再加一张十六进制串 → ARGB 的小表。
配置改了字符串自然不同，下一次调用就重解析，**不需要监听配置重载**。

### 1.3 有上限的表 `BoundedLruMap`

战斗里有不少「按 UUID 记上次触发是第几刻」的小表，写成 `HashMap` 只 put 不 remove
就是内存泄漏（实体是不断产生的）。统一换成 `BoundedLruMap.create(...)`：
访问顺序 LRU，超出上限淘汰最久没碰过的键 —— 对冷却记录来说，
淘汰的结果与「冷却自然过期」没有区别。

### 1.4 热路径日志限频 `HotPathLog`

感电触发、月感电刷云、元素战技扫方块这类日志是「每次结算都写」的，
而日志落盘是**同步加锁**的，主线程会卡在 I/O 上。统一改成**先问再拼**：

```java
if (HotPathLog.allow(LOGGER, "electro-charged-tick", "感电触发")) {
    LOGGER.info("...");
}
```

每个 `key` 独立计数，每秒钟最多放行 `logging.hot_path_max_per_second` 条（默认 8），
被压住的条数在下一个窗口开始时用一条汇总补出来 —— 既保住「第一次一定看得见」，
又不会让日志量随攻击速度线性上涨。**先问再拼**是重点：`allow` 为假时连参数字符串都不拼。

### 1.5 每刻快照 `TickSnapshot`

「队伍里有没有某个角色」这类**全维度级**查询，却挂在每实体每刻的判定里：
30 只带附着的怪 × 每刻 40 次 instanceof，而队伍构成一秒都不变一次。
`TickSnapshot` 按「维度 + 游戏刻」缓存一格结论，同一刻第二次问就是一次查表，
一刻的延迟可接受（换人在下一 tick 生效）。显式 `getOrNull` → 自己算 → `put`，不接 lambda，
免掉每次调用一个捕获变量的 lambda 分配。

> 计算侧的实例都按「一个维度一条 tick 线程」的现实做了最小同步：
> `TickSnapshot` 的方法加锁，`HotPathLog` 按 key 加锁，`DamageNumberThrottle` 整体同步
> —— 这些锁在同一线程里是不竞争的，代价可以忽略。

### 1.6 单次编码的飘字广播 `DamageIndicatorRpc#sendToNearby`

飘字是**广播**给半径 48 内所有玩家的，而 LDLib2 的 `rpcToPlayer` 是「对每个玩家
各调一次」的接口 —— 每调一次都要把这组参数重新编码一遍（还附赠一组装箱的 varargs
数组和一个包对象）。十人团围着 BOSS 打，同一条伤害就要编十遍。

`sendToNearby` 把这段拆成「编码一次 + 广播」：先拿 `RPCPacketDistributor` 的编解码器
把参数压成 `byte[]`，组一个 `PacketRPCPacket`，交给 NeoForge 的
`PacketDistributor.sendToPlayersNear(level, null, x, y, z, radius, payload)` ——
距离筛选与逐玩家投递由 `PlayerList.broadcast` 负责。编解码器拿不到
（例如包没注册上）时自动退回原来的逐玩家发送，功能不受影响。

同一个入口还顺手把 `DamageIndicatorFactory#emit` 的随机抖动 / 防重叠 / 攻击者插值
从 `Vec3` 改成 `double` 基本量：原来一次伤害能造出十几个 `Vec3`（候选点最多试 8 次），
现在只剩「记下这个目标上次飘在哪」的那一个。

## 二、渲染侧：每帧路径

飘字现在是**世界空间提交**：每帧在 `SubmitCustomGeometryEvent` 里把每条飘字
摆到它的世界坐标（相机相对位置 → 相机朝向 → 世界尺度），几何活在世界投影里，
透视自带的近大远小就是距离表现。渲染侧的优化都围绕这条路径。

### 2.1 每帧规划 `IndicatorFramePlanner`

每帧做三件事，全部在一条循环里完成：

1. **剔除**：透明度归零的、超过 96 格的、在相机背后的（半空间剔除）不进提交列表；
2. **限量**：超过 `max_rendered_indicators`（默认 128）时按离相机近的优先保留，
   远端飘字本来也看不清；
3. **烘焙锚点位姿**：把「事件位姿 ∘ 平移 ∘ 相机朝向 ∘ 缩放」直接算成一个矩阵
   放进条目，省掉旧写法每条飘字一次 `pushPose/popPose` 的位姿拷贝。

条目与矩阵都是复用的，相机参数以接口传入，**稳态下每帧零分配**；
飘字动画也在这里一次算完（一批飘字共用一个 `now`，坐标直接落 float 字段），
不再像早期那样每条飘字三次 `System.currentTimeMillis()` 加两三个临时 `Vec3`。

### 2.2 字形排版缓存 `IndicatorGlyphCache`

`font.prepareText`（拆字符 → 查字形 → 生成 `TextRenderable`）是每条飘字每帧的大头，
而飘字的文字在整个生命周期里基本不变。这里按「文字 + 是否斜体」缓存排好版的字形
（斜体 / 非斜体各一张 LRU 表，键就是 `String`，查询不新建对象），
条数上限 `glyph_cache_size`（默认 512）。

字形几何是**文字本地坐标**，顶点变换在消费时由位姿完成，所以同一份缓存可以在
不同飘字、不同位姿、不同帧之间复用。

**失效有两条路，缺一不可**：

1. 条数超上限 → LRU 淘汰；
2. **资源重载** → `IndicatorPerfReloadListener` 显式清空。

第 2 条容易被漏掉：缓存的 `TextRenderable` 抓着**排版当时**的图集 UV，
而 `Minecraft#font` 的实例在资源重载后**不会换**（`FontManager#apply` 是就地重建 `FontSet`），
所以「比字体实例」这条判据永远不成立 —— 少了重载钩子，玩家一按 F3+T 飘字就会花屏。
挂点见 `MinegenshinClient#addReloadListeners`。

### 2.3 提交器 `GradientTextRenderer`

走香草命名牌那条管线：把字体图集的 `RenderType` 交给 `submitCustomGeometry`，
每个字形自己发顶点；外面包一层 `VertexConsumer`，按顶点在**文字行内的本地 y**
插值顶色 → 底色，得到真正的逐顶点垂直渐变（不需要遮罩贴图，也不需要每帧重新排版）。

三个细节：

- **阴影两趟**：先按「右下偏移 + 压暗到 25%」发一遍顶点，再按原位姿发正文。
  两趟同缓冲、顺序即层级，所以正文稳定压在阴影上面（SEE_THROUGH 管线没有深度测试，
  真正决定层级的是提交顺序）；
- **同 RenderType 合批**：香草的自定义几何本来就按 RenderType 分桶、共用一个顶点缓冲
  （`CustomFeatureRenderer.Submit.batchKey()` 就是 RenderType），所以多条飘字合成一次
  提交不改变绘制顺序，省掉的是每条一次的位姿拷贝与提交对象；
- **顶点变换不装箱**：默认的 `addVertex(Matrix4fc, ...)` 每个顶点都会新建一个 `Vector3f`；
  这里复用同一组临时变量，并且把「锚点位姿 ∘ 本地偏移」在一趟里只算一次
  （早先是每个顶点重算一次 4×4 乘，按 128 条 × 7 字 × 4 顶点 × 2 趟就是每帧上万次多余乘法）。

### 2.4 飘字实例自带缓存 `DamageIndicator`

本地化（`I18n.get` 内含一次 `String.format`）与排版结果都挂在飘字实例上，
只比「文字 / 字体 / 语言」三项变化才重算。被合并的伤害数字直接改写同一条实例
（`applyMerge`），所以文字、颜色、时长这些字段不是 final。

### 2.5 世界空间 HUD 的缓存 `HudRenderCaches`

血条这条路每帧都要对**每个**战斗中的实体跑一遍，所以任何「看起来只要一行」的调用
都会乘以「实体数 × 帧数」。收掉的三处：

| 原来 | 现在 |
|---|---|
| 元素图标每次 `Identifier.fromNamespaceAndPath("minegenshin", "icon/elemental/" + id + ".png")` | 元素 → `RenderType` 查表（`IdentityHashMap`），第一次见到这个元素才拼字符串 |
| 血条每取一次 `RenderTypes.entityTranslucent(贴图)`（一条血条 4~6 次） | `barBgType()` / `barFillType()` 惰性常量 |
| 等级文字每次 `"Lv." + level` + `Font#width` | 等级 → 文案、等级 → 宽度 两张表 |

`RenderTypes.entityTranslucent(...)` 之所以值得单独提，是因为它内部是
`Util.memoize(BiFunction)`：**每次调用都要新建一个缓存键对象**。
一条血条取 4~6 次，就是每实体每帧白造 4~6 个对象。

同一处顺手去掉了两类每帧分配：实体的相机相对坐标改用
`Mth.lerp(partialTick, xo, getX())` 直接算（原来 `getPosition(partialTick)` 加
`subtract(camPos)` 会各建一个 `Vec3`），相机与朝向提到实体循环外；
「主元素 → 是否低量」临时表改成复用（原先那个「还活着的实体 id」集合已经不需要了，见 2.6）。

### 2.6 血条拖尾状态机 `HealthBarTrailTracker`

血条被打掉血时有一段黄色拖尾在收。这一块原先有两个问题，都在 2.6 里改掉了：

**一、进战第一刀没有拖尾。** 血条只在「战斗中 / 有元素附着」时才画，脱战的怪那一帧
在实体循环里就被 `continue` 掉了，拖尾状态也跟着停摆。等它被第一刀打进战斗时，
这一帧读到的血量**已经是掉完的了**——状态里没有「这一刀之前它有多少血」的记录，
只能从当前值起步。而「见到新实体就按满血起步」那种补法更糟：它把不属于这一刀的旧伤害
一并拖出来（就是那版「每次不显示→显示都从满血往下滑」的毛病）。

现在把**采样**和**是否显示**拆开：只要实体在本帧的渲染列表里、又在采样距离
（`TRAIL_SAMPLE_DISTANCE`，32 格，比血条最远的 24 格多留余量）内，就把真实血量
喂进状态机推进，不管这帧画不画血条。这样第一刀落下时，拖尾正好从「这一刀之前」的值
开始收。采样真断档了（离屏超过 1 秒）则按「历史不可信」处理，重新从当前血量起步，
不补旧账。

| 参数 | 值 | 作用 |
|---|---|---|
| `DECAY_PER_SECOND` | `0.3` | 拖尾每秒衰减的比例，与刷新率无关 |
| `STALE_TICKS` | `20` | 采样断档超过 1 秒就重新起步 |
| `MAX_FRAME_SECONDS` | `1.0` | 单帧最多推进 1 秒，卡顿回来不让拖尾一次跳完 |
| `MAX_ENTRIES` | `512` | 表条数兜底上限 |

**二、拖尾速度跟着帧率跑。** 原先每帧固定减 `0.005`：60fps 是每秒 0.3，240fps 就是
每秒 1.2，同一刀在高刷屏上几乎看不出拖尾。现在按真实流逝时间衰减，时间取
「游戏刻 + 本帧 partialTick」之差（暂停时游戏刻不走，同一帧被问两次差值是 0），
60fps 下的观感与原来一致。

实现上换掉了原来的 `HashMap<Integer, Float>`：每次 `put` 都要把 float 装箱成新的
`Float`，实体 id 超出 `Integer` 缓存范围后连键都是新建的。现在是 fastutil 的
`Int2ObjectOpenHashMap`（Minecraft 自带该库，和项目已在用的 joml 同一批），
命中路径零分配；状态是可变对象，只在实体第一次出现时建一次，过期条目每 40 帧扫一遍清掉。

### 2.7 角色几何优化 `client/render/optimize`

前六节治的是飘字。这一节开始治**角色模型本身** —— 它是同屏多个角色时最大的一块
CPU 顶点生产。

**问题规模**（以 `character/linweiyun/linweiyun.geo.json` 为例：83 骨骼 / 204 cube /
1224 面 / 4896 顶点）。GeckoLib 5.5.6 的默认路径里，每个角色每帧的固定开销是：

| 开销 | 次数 / 帧 / 角色 | 出处 |
|---|---|---|
| 骨骼 push/pop（各拷 Matrix4f + Matrix3f） | 83 × 2 | `GeoBone#positionAndRender` |
| cube push/pop | 204 × 2 | `CuboidGeoBone#render` |
| `new Quaternionf()` | 287 | `RenderUtil#optionalRotateZYX`（83 骨骼 + 204 cube） |
| `new Vector3f()`（法线） | 1224 | `GeoQuad#normalVec()` |
| `new Vector4f()`（顶点） | 4896 | `GeoQuad#render` 的 `pose.transform(new Vector4f(...))` |

合计约 **1.13 万次堆分配 / 帧 / 角色**。8 个角色同屏时就是每帧九万次短命对象，
GC 会以可见的帧时间抖动还回来。

这个新系统做三件事，每件一个独立开关：

| 子项 | 原理 | 收益 |
|---|---|---|
| 几何预编译 | 一个 cube「绕自身轴心旋转」是**烘焙后的常量**，`T(p)·R·T(-p)` 可以直接折进顶点表（`R·(v-p)+p`）与法线表（`R·n`），每个模型只编译一次 | 每帧省掉 204 对 cube 的 push/pop 与 204 次四元数分配 |
| 骨骼遍历零分配 | 复用一个 `Quaternionf`（`Rotations`），分支结构与原版 `optionalRotateZYX` 逐条对齐 | 每帧省 83 次四元数分配 |
| 顶点直写 | 顶点位置用内联矩阵乘法算（`m00·x + m10·y + m20·z + m30`），不走 `pose.transform(new Vector4f(...))`；法线用三个 float 而非 `Vector3f` | 每帧省 4896 个 `Vector4f` + 4896 个 `Vector3f` + 1224 个法线 `Vector3f` |

三项全开时，这条路径上的堆分配**归零**（只剩 `submitCustomGeometry` 那个延迟回调
本身的 1 个 lambda 对象，原路径也有）。

**接管点只有一个**：`CharacterRenderer#submitRenderTasks`。GeckoLib 的默认实现已经把
「动画快照什么时候应用」安排好了 —— 外层是
`submitCustomGeometry → push → last().set(pose) → renderPosed(...) → pop`，
`renderPosed` 负责应用本趟的 `BoneSnapshot`（动画、骨骼显隐、挂点层的 `skipRender`
全挂在它上面）。优化只替换**最里面那一句 `model.render(...)`**，外面一层原样保留，
所以等价性来自「没动的部分没动」，而不是来自复刻。

**等价性依据**（逐项对齐，任何一条不成立都不要开这个开关）：

- 遍历顺序：顶层骨骼顺序 → 每根骨骼「先自己、再子树」，与 `positionAndRender`
  / `renderChildren` 一致，顶点**写出顺序**不变；
- 显隐：`frameSnapshot.isHidden()` 跳过自己的几何、`areChildrenHidden()` 跳过子树；
- 骨骼位姿：动画位移 → 平移到轴心 → 基础旋转＋动画旋转（ZYX）→ 动画缩放 →
  骨骼位置监听 → 平移回轴心，与 `prepMatrixForBoneAndUpdateListeners` 同序；
  缩放刻意仍走 `PoseStack.scale`，因为非均匀缩放会顺带改写法线矩阵
  （乘 1/s 并标记不可信），那是原版语义；
- 法线符号修正：`fixInvertedFlatCube` 的三条判据编译成 3 bit 掩码，但**判定仍在
  法线变换到世界空间之后**做 —— 提前判会得到不一样的结果；
- 顶点属性走的是同一个组合式 `addVertex(...)`，颜色 / UV / overlay / 方块光 / 法线
  参数一模一样。

唯一与位等价不同的地方是浮点结合顺序（`R·(v-p)+p` 对上「先乘 T(p) 再乘 R 再乘 T(-p)」），
误差量级 1e-6，肉眼与光照都不可见。

**回退**：`render-optimize.character-geometry` 关掉（或三个子项全关）时
`RenderOptimize#characterFlags()` 返回 `0`，渲染器直接把 `model.render(...)` 那一句
原样执行 —— 也就是回到优化前的行为，用于同场景对照。模型编译不出来
（遇到非 `CuboidGeoBone` 的自定义骨骼、或面不是 4 个顶点）时同样回退，
并记一次日志、不再重试。缓存挂在 `BakedGeoModel` 的弱引用键上，资源重载换掉模型实例后
条目自动消失，不需要额外的重载钩子。

### 2.8 GPU 蒙皮 `client/render/optimize/gpu`

2.7 治的是**顶点在 CPU 上怎么算得更快**。这一节把这件事整个搬走：顶点缓冲常驻显存，
每帧只把 83 根骨骼的矩阵（14368 字节）传上去，剩下的蒙皮交给顶点着色器。
CPU 每帧只剩「算骨骼矩阵」这一段，4896 个顶点不再经过 CPU 一次。

| 每帧每角色 | CPU 路径（2.7） | GPU 蒙皮 |
|---|---|---|
| 经过 CPU 的顶点 | 4896 | 0 |
| 上传显存的数据 | 顶点缓冲（4896 × 32 字节 ≈ 153KB） | 骨骼矩阵（14368 字节） |
| 顶点缓冲怎么来 | 每帧现写 | 模型编译时写一次，之后只读 |
| CPU 开销落在哪 | 骨骼遍历 + 写顶点 | 算 83 个骨骼矩阵 |

管线不再是「照 `RenderPipelines.ENTITY_CUTOUT` 逐项手搓一条」，而是**从原版 entity 管线派生**：
NeoForge 给 `RenderPipeline` 加了 `toBuilder()`，它把原管线的两个着色器、**全部 shader define**、
全部 bind group layout、深度 / 混合 / 剔除 / 顶点格式 / 图元拓扑原样带过来；我们只换三样东西 ——
顶点着色器换成 `minegenshin:core/entity_skinned`、顶点格式换成 `SkinnedMesh#FORMAT`
（多带一个骨骼索引属性），再以 append 的方式挂上 `SkinData` 常量缓冲。
**片元着色器刻意不动**，所以 cutout 的 `ALPHA_CUTOUT`、半透明的混合、自发光的 define
全都来自基管线本身 —— 一条派生逻辑就覆盖了整个 entity 家族（白名单见
`SkinnedPipelines#WHITELIST`：solid / cutout / cutout_cull / cutout_z_offset /
solid_z_offset_forward / translucent / translucent_cull / translucent_emissive；
刻意排除带 `DISSOLVE` 与 `APPLY_TEXTURE_MATRIX` 的变体，因为那两个 define 分支在我们的
顶点着色器里不存在）。画面因此走的是与原来同一条片元路径，不会「看起来变成另一个渲染器」。

**接管点只有一个，覆盖的是所有 GeckoLib 模型**：GeckoLib 的几何提交收在一个接口 default 方法
`GeoRenderer#submitRenderTasks` 里，`GeoEntityRenderer` / `GeoReplacedEntityRenderer` /
`GeoObjectRenderer` / `GeoBlockRenderer` / `GeoItemRenderer` 都不覆写它，所以本模组用
`GeoRendererSubmitTasksMixin` 在这条方法的 HEAD 注入一次，统一交给 `GeoRenderIntercept`
决定「GPU 蒙皮 / CPU 优化 / 回退原路径」—— 角色、本模组实体、整合包里其它模组的 GeckoLib
实体走的是同一份代码。提交 GPU 节点时按原版 `SubmitNodeCollection#submitCustomGeometry`
的规则分流到 solid / translucentCustomGeometry / outline 三个阶段，半透明几何不会被
塞进不透明阶段去画。（`GeoArmorRenderer` 自己覆写了提交方法、逐装备槽挑骨骼来画，
护甲渲染保持 GeckoLib 原样；它的 `ARMOR_*` 管线本来也不在白名单里。）

这条 mixin 必须写成 `interface` 形态而不是 `abstract class`：Mixin 按混入类自身的形态选混入
子类型，类形态会落到 `SubType.Standard`，而它对「接口目标」的目标类型校验是直接抛异常 ——
`require = 0` 只挡注入点匹配失败，挡不住这类校验，写错会让客户端在混入准备阶段就起不来。
接口形态 `private` 处理器的注入器在 Java 8+ 兼容级别下恒为启用。

**默认开启，但留了一键关掉的开关**：`render-optimize.gpu-skinning`。它是性能开关而不是观感开关 ——
关掉即 `RenderOptimize#gpuSkinningEnabled()` 返回 `false`，每个模型原样走 2.7 的 CPU 路径，
画面对照用同一场景、同一个角色、同一个视角切这一项即可（读数见第三节）。
留这个开关的现实理由是与光影和别的渲染模组共存：那些模组会替换实体管线，
撞车时玩家需要一条能立刻回到原路径的路。

**光影包生效时自动让位，无需玩家手动关**（`SkinnedPipelineGuard`）。这是 CPU 路径与 GPU 路径的
一个本质差别决定的：

- **CPU 路径**把顶点写进<b>原版自己的</b>顶点缓冲（`VertexConsumer#addVertex`，与 GeckoLib
  原路径调用的是同一个方法），缓冲布局由原版决定 —— 谁改了格式都不会错位；
- **GPU 路径**自带一份 32 字节/顶点的格式和一条派生管线，画的时候必须由<b>同一条管线</b>按
  <b>同一份布局</b>去读那块显存。

光影模组（Iris 一类）在渲染管线这一层是按<b>程序身份</b>选着色器的：它维护一张
「顶点格式身份 → 着色器程序」的固定映射表（`ShaderKey`），只认原版那几个格式常量，
<b>认不出的格式一律落进通用兜底程序</b>。我们那条派生管线声明的格式是本模组自己的
32 字节布局，光影包显然不认识 —— 于是它选出来的程序不是我们的 `entity_skinned.vsh`，
而是按它自己的约定去读我们那块缓冲：位置从「UV / 骨骼编号 / 法线修正掩码」这些字段上
取到一些小整数，画出来是<b>散布在角色周围、有的坐标看着很远、颜色发黑</b>的三角形，
而模型本体因为顶点全错位而看不见 —— 也就是「开了光影之后模型消失 + 满屏黑块」。

所以画之前做两件事，任一命中就让这一帧的 GPU 蒙皮让位（CPU 路径照常优化，只是顶点不再进显存）：

1. **问光影包**：通过 Iris 的稳定 API（反射调用 `IrisApi#isShaderPackInUse`，本模组不依赖 Iris；
   装了光影但 API 读不到时按「有光影」处理）默认每秒探一次，并有一个软依赖混入在
   换包 / 开关光影的那一刻把缓存立即作废（见下）；
2. **管线自检**：核对「基管线顶点格式还是原版 entity 格式」「派生管线顶点格式还是我们那条」
   「派生管线顶点着色器还是我们的」。这一条不认模组名字，对任何动过管线的模组都成立。

**与 Iris 的软依赖（装了才生效，不装不报错）**。这一组开关的即时性依赖光影自身的重载入口，
所以本模组带了一份独立的混入配置 `minegenshin.iris.mixins.json`，它靠四道互相独立的保险
做到「整合包里没有光影时完全等于不存在」：

| 保险 | 作用 |
|---|---|
| 配置 `"required": false` | 目标类找不到时 Mixin 只记跳过，不当成配置错误 |
| `@Mixin(targets = "net.irisshaders.iris.Iris")` + `@Pseudo` | 配置里不出现任何 Iris 的类型引用，编译期 / 运行期都不需要它在场 |
| `IrisMixinPlugin` 门禁 | 探测不到 Iris 就直接返回 `false`，连尝试注入都不发生（也不会留下「目标类找不到」的警告） |
| 注入器 `require = 0` + `[[dependencies]]` 里 `iris` 标 `optional` | 将来 Iris 改了方法名只丢这一项小优化；没装 Iris 不算缺依赖 |

探测结果会在启动日志里留一行（`[MineGenshin] 检测到 / 未检测到光影模组（iris）`）——
整合包作者看一眼就知道这份软依赖到底有没有被加载，不必进游戏验证。

挂钩本身只做一件事：Iris 调 `reload()`（换包、开关光影都会走到这里）或 `loadShaderpack()`
的那一刻，把上面第 1 条的探测缓存作废，于是自检的滞后从「最长 1 秒」压到「下一帧」。
它不读 Iris 的任何字段、也不改它的状态；没有 Iris 时那 1 秒的轮询照旧是唯一通道。

被挡下时 F3 会照常显示 `N model`，只是 GPU 那一栏换成 `gpu off (shader pack)` /
`gpu off (vertex format)` —— 免得把「让位」误读成「设备不支持时间戳查询」。
想确认问题确实出在这条路径上，把 `render-optimize.gpu-skinning-under-shaders` 打开即可强行放回
GPU 路径（默认关，明知可能画错）。

**什么时候会自动回退到 CPU**（不崩、不黑屏，最坏情况只是这一次渲染慢一点）：

| 条件 | 处理 |
|---|---|
| 拿不到可用的 GPU 设备（软件渲染 / 设备初始化失败） | 不进入 GPU 路径 |
| 模型的骨骼数 > 128（一副调色板塞不进常量缓冲） | 该模型回退 |
| 模型编译失败（非 `CuboidGeoBone`、面不是四边形等，判据与 2.7 同一套） | 该模型回退 |
| 该 RenderType 的管线不在派生白名单里（带 `DISSOLVE` / `APPLY_TEXTURE_MATRIX` 的变体、护甲 / 物品 / 方块管线等） | 该次渲染退回 CPU 优化路径（仍不是 GeckoLib 原始路径） |
| 光影包正在生效（Iris 报告 `isShaderPackInUse`），或管线顶点格式 / 顶点着色器被外部替换 | 该次渲染退回 CPU 优化路径，只记一条 `INFO` / `WARN` |
| 提交或绘制时抛异常 | **永久**回退，之后全部走 CPU 路径，并且只打一条 `WARN` |

「永久回退」这条是有意为之：GPU 路径出错往往每帧都会重犯，若只当次回退就会变成
每帧一次异常 + 每帧一条日志，把日志淹没掉反而找不到原因。所以第一次出错就锁死回到 CPU，
打一条带堆栈的 `WARN` 让人能定位。

**重载钩子必须有**：GPU 路径的顶点缓冲与常量缓冲环是显存对象，而它们的钥匙是「编译后的模型」——
按 F3+T 或换资源包时模型实例会被整个换掉，缓存里那条记录随之消失，**但那不等于缓冲被释放**。
少了 `SkinningPerfReloadListener`（注册名 `skinning_perf_cache`），每重载一次显存就往上抬一截。
这一点与 2.7 相反：那边是纯 CPU 表，键一死条目就没了，不需要钩子。

## 三、怎么量：F3 读数

「飘字快不快」在游戏里本来是看不见的：帧率被地形、实体、粒子一起决定。
这套系统把这条链路上**两段自己可控的 CPU 时间**量出来，挂进 F3 调试屏
（走 NeoForge 的 `RegisterDebugEntriesEvent`，正式扩展点，没有 mixin）：

```
MineGenshin indicators: plan 0.03ms | vert 0.41ms (6144v) | drawn 12/40 | glyph 18 | merge 31/46
```

| 字段 | 含义 |
|---|---|
| `plan` | 每帧规划（剔除 / 限量 / 锚点烘焙）自身耗时，几十微秒量级 |
| `vert` | 每帧写飘字顶点耗时 —— **看这一个**，它就是飘字的真实渲染开销 |
| `(6144v)` | 这一帧写出的顶点数（阴影趟算两份）；关掉 `indicator-render.shadow` 应立刻减半 |
| `drawn/active` | 这一帧提交了几条 / 活跃几条；差值是被剔除或超上限丢掉的 |
| `glyph` | 字形缓存条数（不同文字各一条，稳态下不再增长） |
| `merge` | 生成侧合并命中率：46 次请求里 31 次被并进已有数字（纯客户端连外部服务器时不显示） |

对比开关效果就是：同一场景下切 `indicator-render.batch_submit` 或 `damage-number.merge`，
看 `vert` 与 `drawn` 的变化。F3 覆盖层自身也有开销，测的时候可以把其它调试条目关掉
（F3 的调试选项菜单里能逐条开关）。

GeckoLib 模型几何优化有自己的一对：

```
MineGenshin render: 3 model (2 gpu) | walk 0.62ms (8.9% of frame) | gpu 0.14ms | 14688v | 207.6us/model
MineGenshin frame: 144.2 fps (6.94ms) | 1%low 118.6 fps | window 600/600
```

| 字段 | 含义 |
|---|---|
| `N model` | 这一采样窗口里走了优化路径的 GeckoLib 模型渲染次数（角色、本模组实体、整合包里其它模组的 GeckoLib 实体都并进这一栏） |
| `(N gpu)` | 这 N 次里有多少次走的是 GPU 蒙皮（顶点常驻显存）；一次都没有就不显示这一节 |
| `walk` | 这些渲染里「骨骼遍历 + 写顶点」的 CPU 时间合计（不含 GeckoLib 的动画求值）；GPU 蒙皮的耗时并进同一栏（它的 CPU 时间就是算骨骼矩阵），所以这一栏可以直接对比两条路径 |
| `(x.x% of frame)` | `walk` 占一整帧的百分比。绝对微秒数会随场景规模一起变，占比才能横向比 |
| `gpu` | 本模组这批 GPU 蒙皮绘制**在 GPU 上**占的时间轴跨度（时间戳查询，和 vanilla `GPU: xx%` 同源）。`walk` 只说明 CPU 省了多少，这一栏才回答「顶点搬到显存之后 GPU 有没有变贵」 |
| `gpu off (原因)` | 有模型走了优化路径、但 GPU 蒙皮被环境挡下：`shader pack` = 光影包正在生效，`vertex format` = 管线被外部改过。见第二节的自动让位 |
| `v` | 实际写进顶点缓冲的顶点数；骨骼被隐藏时会小于模型顶点总数 |
| `us/model` | 单模型平摊耗时，多角色场景看这个比看总数直观 |
| `fallback` | 因「编译不了」而回退原路径的次数，正常应恒为 0；出现就说明有模型没被接管 |

第二行是**整帧**的帧时间与 1% low（vanilla 的 F3 没有低帧指标，只有瞬时帧率）：

| 字段 | 含义 |
|---|---|
| `xx.x fps (x.xxms)` | 最近一帧的帧率与帧时长（`Minecraft#getFrameTimeNs`，不含帧率上限的等待） |
| `1%low xx.x fps` | 最近 600 帧（60fps 下约 10 秒）里**最慢的那 1% 帧**的平均帧率。平均帧率会把偶发长帧抹掉，1% low 不会 —— 「改了渲染路径有没有让手感变差」看这个 |
| `window n/600` | 窗口里已经攒了多少帧。不足 100 帧时 1% 会退化成「最慢的一帧」，所以那之前这一栏显示 `--` |

采样窗口就是「一帧」：F3 调试屏的 `display` 每帧调一次，两次读取之间恰好一帧。
F3 关掉再打开的第一帧会因为断档被丢弃，所以不会看到一攒很久的数字。
对照 `render-optimize.*` 的办法就是同一场景下把 `character-geometry` 关掉再打开，
看 `walk`、`us/model` 与第二行的 `1%low` 有没有一起变差。GPU 蒙皮用同一套办法：
同一场景下切 `gpu-skinning`，看 `walk`（关掉时会大幅上升）、`(N gpu)`（关掉后整节消失）
与 `gpu`（那一栏只在有 GPU 蒙皮绘制时才出现，设备不支持时间戳查询时整栏消失，
绘制本身不受影响）。

> **这一行没出现时先看这里**：26.2 把 F3 每个条目的开关持久化在游戏目录的
> `debug-profile.json` 里。文件里没有 `profile` 字段（也就是你在 F3 调试选项界面里
> 手动动过条目）时，它就是一份「自定义档案」，**整份**覆盖默认档案 —— 之后才注册进来的
> mod 条目不在里面，`getStatus()` 取到的是 `NEVER`，于是这行永远不显示。
> 两条路：在同一个调试选项界面里找到 MineGenshin 的条目把它点开（状态存进该文件），
> 或者直接删掉 `debug-profile.json` 让默认档案重新生效（代价是 F3 条目的自定义开关一并重置）。

## 四、配置 `minegenshin/performance.toml`

| 键 | 默认 | 作用 | 关掉 / 调小的影响 |
|---|---|---|---|
| `damage-number.merge` | `true` | 密集伤害数字合并成一条累加数字 | 关掉回到逐条飘字（观感原样） |
| `damage-number.merge_gap_ms` | `140` | 间隔小于它就并入同一条 | 调小 = 更少合并 |
| `damage-number.flood_window_ms` | `500` | 洪水判定窗口 | — |
| `damage-number.flood_count` | `6` | 窗口内超过这么多条就一律并入 | 调大 = 更少合并 |
| `indicator-render.max_rendered_indicators` | `128` | 单帧最多提交几条（就近保留） | 调小 = 远端飘字更容易被丢 |
| `indicator-render.cull_behind_camera` | `true` | 相机背后的飘字不提交 | 关掉则背后的也照画（看不见，纯浪费） |
| `indicator-render.glyph_cache_size` | `512` | 字形缓存条数 | 调小 = 排版更容易重算 |
| `indicator-render.batch_submit` | `true` | 同 RenderType 合并成一次提交 | 关掉可用来对比这项的收益 |
| `indicator-render.shadow` | `true` | 飘字阴影（阴影趟 + 正文趟） | 关掉顶点写入**减半**，代价是浅色背景上略难读 |
| `indicator-render.shadow_offset` | `0.5` | 阴影往右下偏多少，单位是**字体像素**（跟着飘字大小等比缩放，`1` = 与香草文字阴影同距，`0` = 看不见阴影） | 调大 = 阴影离本体更远；`0–2` 之间，默认取 `0.5` 是因为飘字比原版 HUD 文字大约 1.36 倍命名牌，整像素在屏幕上会显得飘出去 |
| `render-optimize.character-geometry` | `true` | 角色几何优化总开关 | 关掉 = 完全回到 GeckoLib 原路径（同场景对照用） |
| `render-optimize.geo-precompile` | `true` | 把 cube 的轴心变换折进顶点表 | 关掉则每帧现算 cube 变换（仍零分配写顶点） |
| `render-optimize.zero-alloc-walk` | `true` | 骨骼旋转复用四元数 | 关掉恢复「每根骨骼 new 一个」 |
| `render-optimize.direct-vertex` | `true` | 顶点位置直写（内联矩阵乘法） | 关掉恢复「每顶点 new Vector4f」 |
| `render-optimize.debug-stats` | `true` | F3 的 `mg-render` 一行 | 关掉不显示，采样也随之停 |
| `render-optimize.gpu-skinning` | `true` | GPU 蒙皮：顶点常驻显存，每帧只上传骨骼矩阵 | 关掉 = 完全回到 CPU 蒙皮路径（与光影 / 其它渲染模组冲突时用这个） |
| `render-optimize.gpu-skinning-under-shaders` | `false` | 光影包生效时仍强行用 GPU 蒙皮（仅排查用） | 打开会被交给不认识这份布局的兜底程序去读：模型消失、周围散落黑块。想确认「问题是不是出在 GPU 路径」时临时打开 |
| `logging.hot_path_log_throttle` | `true` | 热路径日志限频 | 关掉恢复逐条输出 |
| `logging.hot_path_max_per_second` | `8` | 同一个日志调用点每秒几条 | — |

注意合并、单帧上限、背面剔除、阴影这四项都是**观感开关**，默认值是为了性能；
不想改变观感就按表里的「关掉」列调回去。`render-optimize.*` 那一组则相反：
它是**性能开关**，语义等价，关掉只影响速度、不影响观感，专门留给对照与排查。

## 五、扩展指引

- **加一条高频日志**：`if (HotPathLog.allow(LOGGER, "唯一key", "中文名")) { ... }`，
  key 用常量字符串（表按 key 计数），label 只用于汇总行。
- **加一张按键记录表**：静态字段直接用 `BoundedLruMap.create(...)`，不要裸 `HashMap`。
- **加一条「全维度级」查询**：用 `TickSnapshot`（记得 `put`），并确认一刻的延迟可接受。
- **加一条新的每帧开销**：仿 `IndicatorPerfStats`，在渲染主线程存计数、
  在 F3 条目里读；不要为了让数字好看而把它放到别的线程。
- **改了飘字文字或样式的取法**：先确认 `DamageIndicator#labelFor` 的三项判据
  （文字 / 字体 / 语言）还成立；新增会影响排版结果的输入时，
  连同 `IndicatorGlyphCache` 的重载清理一起检查。

## 六、已知限制

- 这套系统的收益是 **CPU / GC 层面**的：香草本来就把同 RenderType 的自定义几何合批，
  所以 `batch_submit` 省的是位姿拷贝与提交对象、不是 draw call。别期待「FPS 翻倍」。
- 未做正式的游戏内对照测试：读数（第三节）就是为这件事准备的，
  真实收益随场景（同屏飘字条数、字体、分辨率）变化，需要实测确认。
- 飘字仍是「一条伤害一条包」：生成侧合并已经把包数压到「每目标一条」，
  再往下就要改成「按 tick 聚合一次发多条」，那要动协议，收益待实测。
- 世界空间 HUD 的血条几何仍是「一个矩形一次 `submitCustomGeometry`」；
  同屏实体极多时可以照飘字的办法把同 RenderType 的矩形合成单批。
