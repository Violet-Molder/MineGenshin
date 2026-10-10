# 8. Photon2：从编辑器到运行时


> 这一章是**使用手册**：第 8 章讲"怎么把效果做出来"（编辑器、粒子系统、材质、Timeline、后处理、着色器图），
> 第 9 章讲"代码怎么接管它"，第 10 章是能照着抄的完整案例，第 11 章是出事时怎么查。
> 阅读顺序建议：先看 §8.1 建立心智模型 → 跟着 §8.2 做第一个效果 → 需要什么查 §8.4 的模块表。
>
> 本节事实来源：官方文档站 <https://low-drag-mc.github.io/LowDragMC-Doc/zh/photon2/>（访问 2026-09-28）
> 与本机 Photon `26.2.2.3` 源码（`C:\Users\Linweiyun\.gradle\caches\...\photon-neoforge-26.2\26.2.2.3\*-sources.jar`）。
> 两者不一致的地方，本文以源码为准，并会写明差异。官方文档站描述的是 MC 1.21.1 时代，概念通用、命名偶有出入。

## 8.1 Photon2 是什么：先把心智模型建立起来

### 8.1.1 一句话定义

Photon2 是一套**跑在 Minecraft 客户端里的实时 VFX 工具**：它把粒子、拖尾（Trail）、光束（Beam）、
时间轴（Timeline）、着色器图（Shader Graph）和基于图的后处理（Post Processing）整合在**同一个编辑器**里，
做出来的东西既能手动播放，也能由模组代码接管生命周期。

### 8.1.2 它不是光影包

新人最容易搞混的一点：Photon2 和 Iris/OptiFine 的"光影包（shaderpack）"**不是一回事**，它们是两个层面的东西：

| | 光影包（Iris / OptiFine shaderpack） | Photon2 |
|---|---|---|
| 改什么 | **替换原版的渲染管线**：天空、水、阴影、后处理整条链 | **在画面之上叠加内容**：粒子、拖尾、光束、自己的后处理 |
| 谁写、写什么 | 写一大堆 GLSL，按光影包的格式组织 | 在编辑器里搭对象树；需要时再写 Shader Graph |
| 和模组的关系 | 模组通常"兼容/让位"，不改它 | 模组可以**直接调用它**，把特效绑到实体、骨骼、方块上 |
| 会不会互相影响 | 会：光影包替换实体管线时，走自定义管线的模组要主动让位（见 §7.4 的回退闸门） | 会：Photon 的合成时机与光影包有关，官方提供 `/photon_iris` 诊断命令 |

所以「我装了光影包」和「我的特效能不能显示」是两个独立问题；特效不显示时先确认是哪一层的问题（§11 有排查表）。

### 8.1.3 心智模型：Authored 与 Runtime

Photon2 里所有东西都分成两半，理解这条分界线，后面就不会乱：

```
你在编辑器里做的（Authored，文件）        游戏里跑起来的（Runtime，对象）
  .fxproj  可编辑工程                      FXRuntime   一次播放实例
    └── 导出                            ←── 由 FX#createRuntime() 创建
  .fx      运行时定义（按路径引用资源）       ├── FXObject 树（Empty / Emitter / Field …）
  .fxpack  连依赖一起打包的分发包            ├── 每个 Emitter 的 ParticleRuntime（运行时数据）
                                          └── 每帧提交的后处理请求
```

- **文件是模板**：`.fx` 只是"效果长什么样"的描述，加载一次可以反复实例化；
- **实例才是对象**：同一个 `.fx` 可以同时在十个角色身上播放，每个 `FXRuntime` 有自己独立的状态；
- **改运行时不会改文件**：Java 侧注入的数据都写在 Runtime 上（§9.6），想永久改参数得回编辑器。

### 8.1.4 三条并行的使用路径

| 你想干什么 | 走哪条 | 入口 |
|---|---|---|
| 先把效果做出来、看看好不好看 | 编辑器 | `/photon_editor`（仅单人世界） |
| 快速绑定一下、验证导出文件对不对 | 命令 | `/photon fx …`（§8.2.6） |
| 让效果跟着角色/武器/技能走、多人可见 | Java API | `FXHelper` + Executor（§9、§10） |

官方文档里写得很直接：**如果效果的生命周期由模组管理，就用 Java API，不要用命令**。
命令适合验证，不适合做玩法。

### 8.1.5 环境要求（本仓库的实际条件）

| 项 | 要求 | 本仓库 |
|---|---|---|
| 运行侧 | 只在**客户端**（渲染与播放 API 全是客户端类） | 同 |
| 依赖 | Photon2 与 LDLib2 版本要互相兼容 | Photon `26.2.2.3` + LDLib2 `26.2.2.41.a` |
| 编辑器 | **只能在单人世界打开**（要访问本机项目与资源文件） | 同 |
| 资源位置 | `<游戏目录>/ldlib2/assets/`（可被多个工程共享） | 打包时进 `assets/<命名空间>/…` |

## 8.2 编辑器与项目：从零做出第一个效果

### 8.2.1 打开编辑器

装好互相兼容的 Photon 与 LDLib2，进**创造模式的单人世界**：

```mcfunction
/photon_editor
```

打不开的常见原因就两个：在多人服务器里（编辑器需要本机文件系统），或者 Photon/LDLib2 版本不匹配。

### 8.2.2 界面：六个区域各干什么

编辑器用的是 LDLib2 的编辑器框架，布局和普通资源编辑器接近，但多了实时场景和时间轴：

| 区域 | 用途 | 你在这里做什么 |
|---|---|---|
| FX Hierarchy | 对象树 | 创建/命名/排序/复制对象，设置父子关系 |
| Scene | 实时预览 | 看形状、拖动对象、控制播放与重启 |
| Inspector | 属性面板 | 改 Transform、发射器参数、模块开关、材质与渲染设置 |
| Resources | 资源库 | 建/复用材质、Shader 图、曲线、渐变、颜色、网格 |
| Timeline | 时间轴 | 排演出、做属性动画、播声音、提交后处理 |
| History | 操作历史 | 撤销/重做 |

最大化之后这几块可以同屏；选中一个对象，各个面板会联动到它 —— 这是排查"改了半天没变化"的第一招：
先确认 Inspector 顶端显示的是不是你以为的那个对象。

### 8.2.3 三种文件：`.fxproj` / `.fx` / `.fxpack`

| 文件 | 是什么 | 什么时候用 |
|---|---|---|
| `.fxproj` | **可编辑工程**：存对象树、Timeline、对资源（材质/图/曲线/网格）的引用 | 你日常编辑保存的就是它 |
| `.fx` | **运行时定义**：压缩过的、按路径引用资源的播放文件 | 本机跑、依赖已存在时 |
| `.fxpack` | **分发包**：zip 格式，把效果**和它依赖的资源**一起装进去 | 要发给别人 / 打进模组 jar |

除了这三种，还有一批可复用资源文件（`*.material.nbt`、`*.shader_graph.nbt`、`*.shader_function.nbt`、
`*.fullscreen_graph.nbt`、`*.render_graph.nbt`、`*.mesh.nbt`），它们由 Resources 面板管理。

> **最容易犯的错**：把 `.fxproj` 当成能发布的东西。它引用的资源路径是你的本机环境，
> 别人拿到跑不起来。要发布就用 `.fx`（依赖齐全）或 `.fxpack`（依赖打包）。

### 8.2.4 资源放在哪、id 怎么算

- 文件资源放 `<游戏目录>/ldlib2/assets/` 下，可以被多个工程共享；**内置资源是只读的**，别指望改它。
- 导出时选的目标路径就是运行时 id 的来源。官方例子里导出到
  `/ldlib2/assets/photon/fx/first_effect.fx`，对应运行时 id 就是 **`photon:first_effect`**。
- 打进模组时资源在 `assets/<你的命名空间>/fx/<路径>.fx`，id 就是 `<你的命名空间>:<路径>`。
- 放进资源包的路径**用小写字母、不要空格**（这是资源包本身的规矩，不是 Photon 的）。
- 从编辑器外面改了资源文件，要先**资源重载**再看预览，否则看到的是旧定义。

### 8.2.5 命名规则（这决定了 Java 能不能找到你的对象）

- 需要通过代码控制的对象，**必须起稳定且唯一的名字** —— Java 侧用
  `FXRuntime#findObject("名字")` 找它（§9.5）。
- 序列化与 Timeline 绑定用的是 **UUID**，名字只是给人看的、允许重复；
  但你要用 Java 找它时，重名就等于埋雷（`findObject` 只回第一个匹配）。
- Empty 对象除了分组，还有一个用途：**给 Java 提供一个稳定的控制点**（§8.3）。

### 8.2.6 案例：做出第一个效果并在世界里播放

这是官方"快速开始"的完整流程，我把它补齐了每一步的**判断标准**：

1. **建工程**：`File → New → FX Project`，存成 `first-effect.fxproj`。
2. **建发射器**：在 root 下新建 Particle Emitter。第一个效果就用官方给的一组保守参数：

   | 设置 | 值 | 说明 |
   |---|---|---|
   | Duration | 40 ticks | 一个循环的长度（tick，不是秒；20 tick = 1 秒） |
   | Looping | 开 | 循环发射 |
   | Start Lifetime | 20 | 单个粒子活 20 tick |
   | Start Speed | 0.05 | 初始速度（格/tick 量级，别用 1 起步，会飞太远） |
   | Start Size | 0.2 | 初始尺寸 |
   | Emission Rate | 2 | 每 tick 生成 2 个 |
   | Shape | Sphere | 从一个球体里生成 |

   *判断标准*：结构一变 Scene 会重新预览。想对比参数就按 restart / pause 两个控件。
3. **给材质**：`Resources → Material` 里选内置的 `circle` 等粒子贴图，拖到发射器 Renderer 的 materials 列表。
   Renderer 决定"怎么画"（层、排序、裁剪、Mask、Model 模式、实例化），Material 决定"用什么着色器和贴图"。
4. **加运动与颜色**：开 `Color over Lifetime`，把末端 alpha 拉成 0（粒子淡出）；
   想让它生成后继续加速就开 `Velocity over Lifetime` 或 `Force over Lifetime`。
5. **保存**：`File → Save` 写 `.fxproj`。
6. **导出**：`File → Export → FX`，导出到 `/ldlib2/assets/photon/fx/first_effect.fx`。
   引用了自定义材质/图/网格/贴图时，改用 FX Pack 导出。
7. **在游戏里播放**（关掉编辑器）：绑到脚下方块

   ```mcfunction
   /photon fx photon:first_effect block ~ ~-1 ~ 0 0 0 1 1 1 0 false true
   ```

   *看不到效果时按顺序查*：① 是不是还在编辑器里；② 用的 id 是否等于导出的路径；
   ③ 换过导出文件却还用旧定义，就 `/photon_client clear_client_fx_cache` 清掉 FX 定义缓存；
   ④ 粒子全被清过之后不残留，就 `/photon_client clear_particles`。

### 8.2.7 命令速查（验证用）

```mcfunction
/photon_editor                       # 打开编辑器（单人）
/photon fx <id> block  <pos> [offset] [rotation] [scale] [delay] [forcedDeath] [allowMulti] [checkState]
/photon fx <id> entity <selector>    [offset] [rotation] [scale] [delay] [forcedDeath] [allowMulti] [autoRotate]
/photon fx remove block  <pos> <force> [id]
/photon fx remove entity <selector> <force> [id]
/photon_client clear_particles        # 清粒子 + Executor 缓存，并让缓存的 Runtime 失效
/photon_client clear_client_fx_cache  # 清 FX 定义缓存与列表缓存
/photonfx list | test <effect> [weight] | clear   # 后处理测试（2.2.0+）
/photon_iris status | dump | overlay on | mode auto  # 光影兼容诊断（2.2.2+）
```

几个参数值得单独记：

- `offset / rotation / scale`：分别是在锚点上的**局部位移**（默认 `0 0 0`）、
  **角度制欧拉角**（默认 `0 0 0`）、root 缩放（默认 `1 1 1`）。
- `delay`：启动延迟，单位 tick。
- `forcedDeath`：锚点消失时是否**立刻删掉残留粒子**（默认 false，粒子会飘完自己的寿命）。
- `allowMulti`：同一锚点上允不允许再挂一个同名 FX（默认 false）。
- `autoRotate`：实体挂载时用哪种朝向 —— `none`（只用配置的 rotation）/ `forward`（沿实体前进方向）/
  `look`（跟视线）/ `xrot`（跟身体朝向）。

## 8.3 对象层级与三种空间

### 8.3.1 层级：root → Empty → Emitter

每个 Runtime 都有一个**始终存在的 root 对象**。你在编辑器里建的对象要么直接挂在 root 下，
要么用 Empty 组织成层级。父级的 Transform、`active`、`visible`、`time scale` 会影响整棵子树。

Empty 的四个正经用途（它不是"占位废物"）：

1. 把多个发射器当作一个整体来移动/旋转（比如"武器上的一整套光效"）；
2. 给 Timeline 提供一个共享的旋转轴心（pivot）；
3. 一次性激活/重启/变速整棵子树；
4. 给 Java 代码一个稳定的命名控制点（`findObject("muzzle")` 这种）。

### 8.3.2 Transform 的四条规则

| 操作 | 结果 |
|---|---|
| 保持世界变换重新设父级 | 重新计算局部值，对象在世界里**不动** |
| 移动父级 | Local 空间的子对象跟着走 |
| 旋转父级 | 子对象的位置和朝向绕父级轴心一起转 |
| 缩放父级 | 子对象继承缩放（**非等比缩放会改变向量基准**，见下） |

> 非等比缩放（比如 `1 1 2`）会让"方向"的含义变形：Custom 空间和轨道运动会跟着变。
> 调试时不要只在 `1 1 1` 下看，要在真实缩放值下看一遍。

### 8.3.3 `active` / `visible` / `timeScale` 的区别

| 字段 | 关掉会怎样 |
|---|---|
| `selfActive = false` | 该对象**和它的子级**都停止 tick 与渲染（真停） |
| `selfTimelineVisible = false` | 只隐藏渲染，**不改编辑器里的 `selfVisible`**（临时看不见） |
| `selfTimeScale` | 乘到模拟时间上，子级继承层级结果（慢动作/加速） |

### 8.3.4 关键区分：Transform 继承 ≠ Simulation Space

这是全章最容易混的一对概念，用一句话记：

> **Transform 继承决定"以后新生成的粒子从哪来"；Simulation Space 决定"已经生成的粒子存在哪"。**

| Simulation Space | 行为 | 典型用途 |
|---|---|---|
| `LOCAL` | 已生成的粒子继续跟着发射器层级平移/旋转/缩放 | 挂在实体身上的光环、武器光效 |
| `WORLD` | 生成后就留在世界里；之后移动发射器只影响**新**粒子 | 走位留下的烟雾、拖尾残影 |
| `CUSTOM` | 相对另一个 FX 对象的 Transform 保存 | 多个发射器共享一个独立动画空间 |

世界空间下移动发射器**不会**拖动旧粒子 —— 这句话解释了 90% 的"为什么我的烟雾跟着我跑"。

### 8.3.5 案例：一个 FX 里的两种空间

目标：角色身上有一圈跟着转的光环，同时脚下走过的地面留下逐渐消散的烟。

1. root 下建 Empty，命名 `aura_pivot`（以后 Java 侧就用这个名字控制整圈光环）。
2. `aura_pivot` 下建 Particle Emitter `aura_ring`：Shape 用 **Circle**（半径 0.9、thickness 0），
   Simulation Space 用 **LOCAL** —— 这样角色转身、跳跃时光环整体跟着走。
3. root 下再建 Particle Emitter `ground_smoke`：Shape 用 **Box**（薄薄一层），
   Simulation Space 用 **WORLD** —— 粒子生成后停在世界里，角色走开就会看到一串"脚印"。

这样两个发射器在同一个 `.fx` 里、同一套参数体系，唯一区别就是空间选择。
把这两个空间的区别刻进肌肉记忆，后面所有"位置不对"的问题都能自己回答一半。

## 8.4 粒子发射器与模块

这一节是 Photon2 的主体。**读法建议**：先看 §8.4.1 弄清"一个发射器由哪几块组成"，
然后按你需要什么查后面的小节 —— 不要试图一次记住所有参数。

### 8.4.1 一个发射器由什么组成

```
ParticleEmitter
├─ 顶层参数（ParticleConfig）：时长、循环、初始值、上限…
├─ Emission / Shape：从哪生成、什么时候生成
├─ 模块（可开关，逐个叠加）：生命、速度、受力、物理…
└─ Renderer + Material：长什么样、怎么画
```

**顶层参数**（Inspector 直接显示的那一排）：

| 参数 | 含义 | 单位 / 默认行为 |
|---|---|---|
| `duration` | 单次循环长度 | **tick**（20 tick = 1 秒） |
| `looping` | 循环结束后是否重来 | — |
| `prewarm` | 第一帧可见之前**预先模拟**多少 tick | tick；让循环烟雾第一帧就像"已经跑了一会" |
| `startDelay` | 每次开始时延迟多久 | 函数（可以做随机延迟） |
| `startLifetime` | 新粒子的生命期 | tick |
| `startSpeed` | 沿 Shape 方向的初始速度 | 格/tick 量级 |
| `startSize` | 初始尺寸（X/Y/Z） | 格 |
| `startRotation` | 初始旋转（X/Y/Z） | 度 |
| `startColor` | 初始颜色 | 后续 Color 模块与材质会继续相乘 |
| `maxParticles` | 同时存在的粒子上限 | **性能闸门**，先设小再往上调 |
| `parallelUpdate` | 允许互相独立的粒子并行更新 | 省 CPU，但自定义逻辑里不能有共享可变状态 |

**两条生命周期规则**，不知道就会误判"特效没结束"：

1. 非循环发射器在 `duration` 之后**停止生成**，但已有粒子/拖尾还在时，**整个 FX 不会结束**；
2. 循环发射器永远不会自己结束 —— 谁创建它，谁负责在合适的时候销毁（§9.3）。

`prewarm` 是个好用的开关，但它会**增加启动那一刻的模拟成本**；频繁生成的效果别把值设太大。

### 8.4.2 Emission 与 Shape：粒子从哪来、什么时候来

**Emission（什么时候生成）**有三种来源：

| 来源 | 行为 | 什么时候用 |
|---|---|---|
| Emission Rate | 按发射器时间**连续**生成 | 火焰、光环、持续的烟 |
| Distance Rate | 按发射器**移动距离**生成 | 脚印、移动轨迹（走得快就多） |
| Burst | 在循环内的指定时间一次性生成一批 | 爆炸、爆发、每段技能的起手 |

Burst 的参数是 `time / count / cycles / interval / probability`；发射器会**跟踪每个 Burst 的 cycle**，
所以循环时不会错误地重放旧状态。

**Shape（从哪生成、初始速度朝哪）**：Shape 内部还有自己的 position / rotation / scale，
所以想要不同的生成轴心时**不必再加一个 Empty**。

| Shape | 重要设置 | 常见用途 |
|---|---|---|
| Dot | 无 | 单一原点（起手闪光） |
| Box | 尺寸 + Emit From 模式 | 体积、平面、方盒表面 |
| Circle | radius / thickness / arc | 环形、径向喷射（光环、魔法阵） |
| Cone | angle / radius / thickness / arc | 火焰、喷射、方向扩散 |
| Cylinder | radius / thickness / arc | 柱体、圆形墙 |
| Sphere | radius / thickness / arc | 爆炸、Aura、球壳 |
| Mesh | Mesh Source 的顶点/三角面 | 从模型几何体上生成 |
| Function | 位置/方向表达式 | 程序化路径、数学体积 |

两个细节：

- **thickness = 0 只在外表面生成**（shell，轮廓清晰）；值越大越往中心填，
  想要云雾/爆炸的体积感就填满内部。
- **Arc**：圆形/锥形 Shape 可以只用一个角度区间，并选择怎么遍历（随机分布还是按顺序推进），
  配合 loop / ping-pong / spread 与 speed 决定发射怎么前进。
- **Function Shape** 用表达式算位置与方向，**必须保持确定性**，并且要防止除零、开负数 ——
  求出 NaN 的位置在渲染和碰撞里都不安全。

### 8.4.3 生命周期与速度类模块

这类模块的输入都是**归一化的粒子年龄（age，0→1）**；`by Speed` 系列则是"先把速度按配置范围重映射到 0→1"。
它们的作用方式都是**与 Start 值相乘或相加**，不会直接覆盖粒子状态。

| 模块 | 输入 | 影响 |
|---|---|---|
| Color over Lifetime | age | 用渐变或颜色函数乘到颜色/alpha |
| Size over Lifetime | age | 缩放 X/Y/Z 尺寸 |
| Rotation over Lifetime | age | 追加 roll / pitch / yaw |
| Velocity over Lifetime | age | 追加线性/轨道/径向速度与速度修正 |
| Force over Lifetime | age | 把加速度积分成速度 |
| Color by Speed | 速度 | 按速度乘颜色 |
| Size by Speed | 速度 | 按速度缩放尺寸 |
| Rotation by Speed | 速度 | 按速度追加旋转 |
| Lifetime by Emitter Speed | 发射器速度 | 调整新粒子分到的生命期 |

三条实用经验：

1. **`by Speed` 的范围要接近粒子的真实速度**。如果配置范围远高于实际速度，函数会永远停在第一段 ——
   表现就是"我明明设了渐变，颜色却一直不变"。
2. **模块的 enable 也能被 Timeline / Java 改写**（2.2.0 起），所以"临时关掉一层"不需要改配置。
3. **生效顺序是相乘/累加链**：`Start Color × Color over Lifetime × Color by Speed` = 进材质前的实际颜色；
   尺寸同理；旋转与运动是各自累加。

**调试这类模块的三步法**（官方推荐，实测有效）：把 Start 值换成简单常量 → **只开一个模块** →
用一条清晰的 0→1 线性曲线确认输入范围 → 再加第二个模块。

### 8.4.4 运动、受力与力场

| 模块 | 干什么 | 注意 |
|---|---|---|
| Velocity over Lifetime | 追加 linear / orbital / offset / radial / speed 修正 | 有线性的 `ValueSpace`，决定向量按哪个基解释 |
| Force over Lifetime | **改变速度**（不是直接改位移） | 适合重力、风、可控弯曲；变化中的力曲线会沿生命期积分 |
| Inherit Velocity | 把发射器运动传给新粒子或存活粒子 | 和 Local Space 不是一回事：World 空间粒子可以继承初速，但不继续跟随 |
| Force Field | 独立的 FX 对象（`ForceFieldObject`），带自己的 Transform 和配置 | 支持方向力、围绕它的吸引/重力、drag、涡旋（vortex） |

用 Force Field 的方式：在 Particle Emitter 上开启 **External Forces** 并设 multiplier；
`influence filter/list` 可以只让指定的 Field 生效或排除它 —— 所以同一个 FX 里可以并存多套力场。

**计算顺序**（官方明确写了）：Start Speed 与 Inherit Velocity 建立初始运动 →
每 tick 再由 Velocity / Force / Physics / External Field / Noise 各自叠加。
**同一个效果不要让多个模块重复做同一件事**，否则参数之间会互相打架、很难调。

### 8.4.5 物理、噪声、光照、UV

这四个模块作用在粒子的不同阶段，混在一起记容易乱：

| 模块 | 阶段 | 要点 |
|---|---|---|
| Physics | 与世界交互 | 要碰 Minecraft 的碰撞形状才开。核心是 `gravity`、碰撞响应、`bounce`（保留法线方向速度）、`friction`（削减切向速度） |
| Noise | 扰动模拟状态 | 可以扰动位置/旋转/尺寸；`frequency` 是场变化速度，Quality 用算力换平滑；低频做烟，高频做火花/电流 |
| Lighting | 改打包光照 | `Light over Lifetime` 给 Sky/Block Light 函数；固定亮度适合自发光外观（真正的 HDR 发光要写在材质/着色器里） |
| UV Animation | 贴图选帧 | 把贴图切成 tiles（列×行），按 `frameOverTime` 选帧，配 `startFrame`、`cycle` |

三个坑：

1. **碰撞是每粒子 CPU 成本**，还会访问世界数据 —— 只在玩家**真的看得见**接触时开。
2. **速度过大可能在两个 tick 之间穿过薄几何体**（隧穿）；要么降速，要么改效果设计。
3. `Light Value` 和材质里的 Emission 解决不同问题：前者影响 Minecraft 的光照着色，
   后者才是给 Bloom 用的发光。既调不出来就别硬调，先想清楚要哪一个。

### 8.4.6 拖尾与子发射器：把多个发射器串起来

**Particle Trail**：给一定比例的粒子挂上拖尾。

| 设置 | 用途 |
|---|---|
| `ratio` | 多少比例的粒子获得拖尾 |
| `lifetime` | 拖尾自己的寿命函数 |
| `dieWithParticles` | 跟所属粒子一起消失，还是自己慢慢消散 |
| `sizeAffectsWidth` / `sizeAffectsLifetime` | 粒子尺寸乘到拖尾宽度/寿命 |
| `inheritParticleColor` / `colorOverLifetime` | 拖尾颜色 |
| `trailType` | 拖尾的实现/几何体类型 |

> 拖尾有自己的材质与渲染路径，所以**同一个发射器可能把普通粒子和拖尾送进不同的渲染 pass**。
> 这就是"为什么我改了材质，粒子变了但拖尾没变"。

**Sub Emitter（子发射器）**：用事件把发射器串成多阶段效果。

| 事件 | 触发时机 |
|---|---|
| Birth | 父粒子创建时 |
| Death | 父粒子结束时 |
| Collision | 父粒子报告碰撞时 |
| Tick | 按配置的间隔重复 |

每个条目可以配目标发射器、概率、Tick 间隔，以及颜色/尺寸/旋转/寿命/时长的继承开关。
引用的是**同一个 FX Runtime 里的另一个发射器**，靠名字解析并缓存。

> **绝对不要写出环**：发射器生成自己、或两个互相生成，数量会指数爆炸；
> `probability` 和 `interval` **不能**把这种循环变安全。要链式（A→B→C），并给出保守的 `maxParticles`。

**三种"直接画几何体"的发射器**（它们不生成一团独立粒子，而是画连接几何）：

| 发射器 | 几何来源 | 适合 |
|---|---|---|
| Trail | 把移动的发射器采样成有序 section | 武器挥砍、移动拖尾、飘带 |
| Beam | 连接端点或射线结果 | 激光、连线、直线能量束 |
| AraTrail | **会模拟**带物理的移动线段 | 鞭子、电流式运动、平滑尾巴 |

选择口诀：几何要精确跟随移动的 Transform → Trail；端点点位比运动历史重要 → Beam；
拖尾自身需要模拟运动 → AraTrail；大量独立粒子各要一小截拖尾 → Particle Trails。

三种发射器都有**强类型 Runtime 层**（`TrailRuntime` / `BeamRuntime` / `AraTrailRuntime`），
所以 Timeline 能动画它们的字段，Java 也能写同样的槽（§9.6）。

### 8.4.7 案例：三段式起手特效（Burst → 扩散 → 残留）

目标：技能起手瞬间一圈火花向外扩散，随后留下缓缓上升的余烬。

1. **Emitter 1「spark_burst」**（爆发环）
   - Duration 5、Looping 关；Shape 用 **Circle**（radius 0.2、**thickness 0** 只在外沿）；
   - Emission 用 **Burst**：`time 0 / count 60 / cycles 1`；
   - Start Lifetime 12、Start Speed 0.35、Start Size 0.12；
   - `Size over Lifetime` 用一条从 1 → 0.2 的曲线（粒子越小越像火花）；
   - `Color over Lifetime` 用 `#FFFFFF → #FFC64B → alpha 0` 的渐变。

2. **Emitter 2「ember_rise」**（余烬上浮）
   - Duration 60、Looping 开；Shape 用 **Box**（一层薄平面）；
   - Emission Rate 6；Start Lifetime 45、Start Speed 0.02；
   - `Force over Lifetime` 给一个**向上**的力（0, 0.004, 0）——力改的是速度，摸起来像"越飘越快"；
   - Simulation Space 选 **WORLD**，这样起手结束后余烬留在原地，角色走开不会拖着它们。

3. **把两段串起来**：在 `spark_burst` 上加 **Sub Emitter** 条目，
   Event 用 **Death**，目标指到 `ember_rise`，`probability 0.35`（不是每个火花都留余烬，看起来更自然）。

**调参顺序**（照这个顺序调，比乱试快得多）：先调 Shape 与 Burst 的**数量**看分布 →
再调 Start Lifetime / Speed 看范围 → 最后才调颜色与渐变。
颜色放最后，是因为它最容易被误当成"位置不对"的原因。

## 8.5 数值函数（NumberFunction）：让参数动起来

### 8.5.1 一个概念：消费者给 time，函数给值

Photon 里大量设置不是"写死一个数"，而是一个**函数**。你选函数类型，Photon 在运行时把
"时间"喂进去、取出值。**同一个 Curve 在不同字段里含义可能不同**，因为"时间"的定义不同：

| 字段 | 喂进去的 time 是什么 |
|---|---|
| `over Lifetime` 系列模块 | 归一化的粒子年龄（0 → 1） |
| `by Speed` 系列模块 | 先把当前速度按配置范围重映射到 0 → 1 |
| Emission / Start 值 | 发射器/粒子的创建时间 |
| Trail 相关字段 | 归一化长度、整条拖尾的时间、或单段的时间 |
| Additional GPU Data | 可选的时间来源 |

**所以在模块之间复制曲线之前，先确认字段说明** —— 同一张曲线图放在"生命期"和"速度"里，
形状一样但含义完全不同。

### 8.5.2 标量函数与颜色函数

| 标量类型 | 结果 |
|---|---|
| Constant | 恒定值 |
| Random Constant | 在范围内按粒子/发射器随机取值 |
| Curve | 按 time 求值的可编辑曲线 |
| Random Curve | 用一个稳定随机因子在两条曲线之间取 |
| NumberFunction3 | 三条函数组合成 X/Y/Z（尺寸、旋转、力、速度都用它） |

| 颜色类型 | 结果 |
|---|---|
| Color | 一个 RGBA 颜色 |
| Random Color | 在若干颜色之间随机 |
| Gradient | 按 time 在颜色 stop 与 alpha stop 之间插值 |
| Random Gradient | 用稳定随机值在两条渐变之间混合 |
| HDR 变体 | 保留 0–1 显示范围以上的值，用于 Bloom/发光 |

> 只有支持 HDR 颜色的消费端才能接 HDR 函数 —— 编辑器会**直接拒绝不兼容的拖放**，
> 而不是悄悄把数据截断。看到拖不进去就是"这个字段吃不了 HDR"，不是 bug。

### 8.5.3 资源值 vs 内联值

- 曲线、渐变、颜色**既能内联写在配置里，也能保存成资源**；
- 多个发射器/材质要共用同一个值 → 存成资源，拖进字段会保持**引用**；
- 复制内联值会产生**独立数据**（改一个不影响另一个）。

这是"我改了曲线，为什么只有一半的粒子变了"的答案：那半用的是复制出来的内联副本。

### 8.5.4 Timeline 与函数的关系

Timeline 的动画属性可以存关键帧、曲线片段、渐变片段或表达式片段；更新时它把**采样后的具体值**
写进目标的 RuntimeValue，**不会修改你的曲线资源**。所以"动画跑完之后参数还是我设的值"。

### 8.5.5 案例：伤害数字的"弹出感"

想要的效果：数字出现时先放大、再回落到正常大小，同时从亮黄渐变成白。

| 字段 | 函数 | 曲线 |
|---|---|---|
| `Size over Lifetime` | Curve | (0, 0.6) → (0.15, 1.35) → (0.4, 1.0) → (1, 0.9) |
| `Color over Lifetime` | Gradient | (0) `#FFF2A8` → (0.4) `#FFFFFF` → (1) 白色但 alpha 0.85 |

两条曲线都存成**资源**（因为暴击/普通伤害可能共用同一套手感），
Java 侧只改"用哪套资源、数字内容、位置"，不改曲线本身。

## 8.6 渲染、材质与网格：粒子到底长什么样

### 8.6.1 三层：Simulation / Renderer / Material

一句话分工：**Simulation 产生粒子状态，Renderer 决定几何与绘制顺序，Material 决定着色器、贴图、深度与混合。**

| Renderer 设置 | 用途 |
|---|---|
| `materials` | 这个几何体用哪几个材质槽 |
| `layer` | Opaque 还是 Translucent 管线 |
| `cull box` | 给这个发射器单独设视锥裁剪盒（不设就用默认） |
| `order in layer` | Photon 各个 pass 之间的**稳定顺序** |
| `vertex sorting mode` | 透明几何的顶点排序方式（支持的渲染器才有） |
| `composite mode` | 选择延迟 FX 的合成时机 |
| `custom mask` | 给后处理写命名的 Mask/Depth 信息 |

### 8.6.2 可用的材质类型

| 材质 | 说明 |
|---|---|
| Texture Material | 贴图 + discard 阈值 + HDR 倍数/模式 + Pixel Art 开关 |
| Sprite Material | 使用已注册的 Minecraft 粒子 Sprite |
| Shader Graph Material | 把图资源编译成粒子着色器变体（§8.9） |
| Custom Shader Material | 加载 Core Shader，并暴露 Curve/Gradient 采样器 |
| Block Atlas | 绑定 Minecraft 方块图集 |

材质状态与渲染状态是**分开的**，所以同一个材质可以复用到不同几何体上。

### 8.6.3 Tile 与 Model 两种渲染

- **Tile**：面向相机（或指定方向）的四边形 —— 大多数粒子用这个，便宜。
- **Model**：每个粒子渲染一个网格，支持 Wireframe/Shaded、多材质；
  Model Source 提供时还能用方块 UV。

**Mesh Source**（Shape 与 Model 共用同一套来源）：内置图元（plane/quad/cube/sphere/cylinder/capsule）、
`.obj`、Minecraft JSON 模型、以及编辑器管理的可复用 `.mesh.nbt`。
同一个资源可以**同时**驱动"从哪里生成"和"长什么样"，不需要维护两份。

### 8.6.4 合批与实例化：为什么会突然掉帧

实例化渲染会把每个粒子的记录上传，用**一次 draw call** 画一大批；有效渲染 pass 相同的对象会被合进同一批。

所以下面这些操作会**打断合批**（表现就是特效一多就掉帧）：

- 每个发射器用不同的材质/渲染设置 → 每次覆盖都会生成一个兼容的 override pass；
- 频繁改渲染覆盖（`layer`、`orderInLayer`、混合等）→ 每个"有效值不同"的组合各自成批；
- 大量透明几何 + 顶点排序。

好消息是：**清除全部渲染覆盖后会恢复共享的快路径**。所以调节性能的第一招往往是
"别在运行时逐帧改渲染参数"，把这些参数放回编辑器。

### 8.6.5 透明与深度

| 设置 | 作用 | 用错的后果 |
|---|---|---|
| Depth Test | 让粒子不被它背后的几何穿出 | 关掉会看到"隔着墙的粒子" |
| Depth Mask | 把粒子的深度写进深度缓冲 | 乱写会让后面本该出现的透明效果消失 |
| Blend Mode | 混合方式，必须与着色器的颜色约定匹配 | HDR/Bloom 路径要用支持预乘的合成方式 |

### 8.6.6 案例：让特效正确出现在角色身后 / 身前

症状：技能特效不管在角色前面还是后面，都糊在角色脸上（或反过来被角色整块遮住）。

排查与修法：

1. **先确认层**：`layer` 选错（比如该 translucent 的用了 opaque）会让半透明粒子直接盖住角色；
2. **再确认深度测试**：想"被角色挡住"就必须开 Depth Test；想"永远在最上面"（准星、屏幕提示）
   才关掉它；
3. **最后确认顺序**：同一层里有多个发光元素时用 `order in layer` 固定顺序，
   否则它们会随帧闪动；
4. 多发光元素叠加时，考虑用 **custom mask + 后处理**做统一发光，而不是叠十几个半透明面片
   （后者既不便宜也不好看）。

## 8.7 Timeline：让特效按时间演出

### 8.7.1 这一节解决什么问题

§8.4–§8.6 讲的是「一个发射器怎么发射、粒子长什么样」。但真实的技能特效往往不是「一个发射器一直放着」，
而是**分阶段**的：前摇先亮一圈法阵、蓄力时法阵收缩、爆发瞬间炸开、然后淡出。

如果你用 Java 每 tick 判断「现在到第几个阶段了」，再手动去改对象可见性、位置、速度，代码会迅速变成一团
`if (tick > 20 && tick < 60)`；而特效作者（美术）根本改不动这团代码。

Timeline（时间轴）就是 Photon 给这件事的标准答案：**把「什么时候发生什么」从代码里搬到编辑器里**，
排成一条可视化的时间线，Java 侧只负责「什么时候开始播」和「播完告诉我一声」。

一句话对照：

| 你想要的 | 该用什么 |
|---|---|
| 一个常驻光环、一条常驻拖尾 | 直接发射器 + 循环，不需要 Timeline |
| 「起手 → 蓄力 → 爆发 → 余韵」这种分段演出 | Timeline |
| 特效要和游戏逻辑对暗号（比如"蓄力完成时弹伤害数字"） | Timeline 的 Signal（信号） |
| 整屏的闪光、扭曲、残影 | Timeline 的 Post Process 轨道（详见 §8.8） |

**空 Timeline 不花钱**：源码里 `TimelinePlayer.isEmpty()` 为真时 `isFinished()` 直接返回 `true`，
不会有任何每帧求值。所以给一个简单特效留一条空 Timeline 是无害的，等需要时再排。

> 术语对照：Timeline（时间轴）、Track（轨道）、Clip（片段）、Track Group（轨道组）、
> Signal（信号）、Marker（标记）、Playhead（播放头）。

### 8.7.2 心智模型：一份数据 + 一个播放器

Timeline 只有两个角色，分清楚就再也不会绕：

| 角色 | 是什么 | 真实类 | 生命周期 |
|---|---|---|---|
| `Timeline` | **数据**：一串 `Track`，每个 Track 里放 Clip / Key / Signal | `client/fx/timeline/Timeline.java` | 跟着 `.fx` 资产，只读 |
| `TimelinePlayer` | **运行时**：一条属于这次播放的主时钟 | `client/fx/timeline/TimelinePlayer.java` | 每次播放一个新的，可读写 |

`TimelinePlayer` 挂在 FX 的 Root 空对象上，由 Root 每 tick / 每帧驱动。它对外暴露的方法就这些
（都在 `TimelinePlayer.java`，括号里是行号）：

| 方法 | 作用 |
|---|---|
| `begin(IEffectExecutor)` | 开始播放，重置时钟到 0 并立即求值一次 `t=0`（`:85`） |
| `tick()` / `tick(float rate)` | 推进一个游戏刻（`:100` / `:112`） |
| `frame(float partialTicks)` | 每渲染帧的插值通道：重采样动画 + 提交后处理请求（`:147`） |
| `isFinished()` | 没有未来内容了就为真（`:126`） |
| `getDuration()` | 本次播放的内容总长度（tick）（`:131`） |
| `stop()` | 彻底结束：静音所有声音，并让 `isFinished()` 变真（`:136`） |
| `stopAllAudio()` | 只停声音（`:334`） |

**一个必须记住的时钟细节**：`tick(rate)` 的实现是**先求值、再推进**——

```java
public void tick(float rate) {
    evaluate(localTime);                  // 先按"当前"时间求值
    localTime += Math.max(0f, rate);      // 再往前走
}
```

这带来两个后果，都是好消息：

1. 摆在 `t = 0` 的内容在**第一个刻到来之前**就已经生效了，不会出现"前 50ms 什么都没发生"；
2. 恰好摆在 `t = duration` 的内容（例如结尾那一下 Signal）会在 `isFinished()` 变成 `true` **之前**被求值，
   **不会被吞掉**。

`isFinished()` 的判据是三者之一：Timeline 为空、被 `stop()`、或者 `localTime > duration`。
注意它是"`>`"而不是"`>=`"，配合上面那条"先求值再推进"，正好让结尾那一帧来得及生效。

### 8.7.3 两个速度：模拟按 tick，画面按帧

Photon 里「特效跑多快」有两层，别混：

| 层 | 谁在推 | 单位 | 结果 |
|---|---|---|---|
| 模拟（粒子状态、Timeline 主时钟） | Root 对象每 tick 调 `tick(rate)` | 游戏刻（1 刻 = 1/20 秒） | 确定性；同一份资产同样的 seed 每次结果一致 |
| 画面（Transform 动画采样、后处理请求） | 每渲染帧调 `frame(partialTicks)` | 渲染帧（几十~几百 fps） | 平滑；`partialTicks` 是"当前刻走到哪儿了"的小数 |

这就是为什么 Transform 动画是平滑的、而粒子物理是离散的：`frame()` 里会把动画在
`lastEvalTime + partialTicks` 这个**小数时间**上重新采样一遍（`TimelinePlayer.java:147-155`）。

再往上一层还有 `FXRuntime#setRate(rate)`：它是**整份播放的倍率**，`tick(rate)` 里的 `rate` 就来自它
（`0` 冻结、`2` 表示一个游戏刻推进两个 Timeline 刻）。它和下面的 SpeedTrack 是两件事：

- `setRate` 是**调用方**（Java 代码）说的"整体快慢"；
- SpeedTrack 是**作者**（编辑器里）说的"这一段让某个对象慢下来"。

源码注释明确写了冲突时的优先级：轨道覆盖到 Root 时，**作者的显式意图赢过调用方**。
所以如果你的代码 `setRate(0.5)` 却发现没变慢，先去看看 Timeline 里有没有 SpeedTrack 绑在 Root 上。

### 8.7.4 Track 与 Clip 的结构

一个 Track 就是"一条泳道"，负责一类事情；Clip 是泳道上的一个方块，表示"从第 X 刻到第 Y 刻做这件事"。

| 字段 | 含义 |
|---|---|
| `start` | 这个 Clip 从第几刻开始（主时钟 tick） |
| `duration` | 长度，单位 tick |
| local time | `masterTime - start`，用来在 Clip 内部采样曲线/包络 |
| `seed` | Control Clip 重启对象时使用的**确定种子** |
| random seed | 重启时改为从 Executor 的随机源取一个新种子 |

**一条硬规则：相邻 Clip 要首尾相接，不要重叠。** 那些"同一时刻只能解析一个 Clip"的轨道会直接拒绝重叠，
拖拽、缩放、粘贴走的是同一套校验；被拒绝的编辑不会把原来的位置改坏。这不是为了刁难你——
重叠意味着"这一刻到底听谁的"没有答案。

Clip 的边界包含哪一端由各轨道自己的查找规则决定，这也是为什么**不要靠重叠来补缝**，
而是让 `上一个.start + 上一个.duration == 下一个.start`。

**内置轨道一览**（类名在 `client/fx/timeline/` 下，语义对照官方文档站与源码）：

| 轨道 | 真实类 | 控制什么 | 什么时候用 |
|---|---|---|---|
| Activator | `ActivatorTrack` | 目标对象在 Clip 范围内是否 Active | 让某个发射器"只在第 20~60 刻存在" |
| Control | `ControlTrack` | 在 Clip 边界**重启并播放**某个对象/子树 | 同一泳道依次播多个一次性特效、或需要固定种子重放 |
| Animation | `AnimationTrack` | Transform 或已注册的 RuntimeValue 槽 | 让法阵变大、让亮度爬升 |
| Speed | `SpeedTrack` | 目标对象的 `selfTimeScale` | 蓄力段的"慢动作" |
| Signal | `SignalTrack` | 带 `CompoundTag` 数据的命名事件 | 和 Java 逻辑对暗号 |
| Audio | `AudioTrack` | 声音、音量/音调包络、可选空间位置 | 起手音、爆发音 |
| Post Process | `PostProcessTrack` | 每帧的加权后处理请求 | 屏幕闪光、色偏（详见 §8.8） |
| Group | `TrackGroup` | 只负责组织与 Mute，**不产生对象也不产生 Transform** | 轨道太多时收纳 |

> 有个常见误解：以为 Group 可以当"父级变换"用。不能——它只是文件夹。要层级变换请用 Empty 对象。

**绑定目标（Target）**：把 Hierarchy 里的对象拖到 Target 槽即可。Animation、Activator、Speed、Audio
是**在 Track 级**绑定（整条轨道只管一个对象）；Control 可以**按 Clip**绑定，所以同一条泳道能依次
播放好几个不同对象——这正是它存在的理由。

序列化上，轨道类型走 `photon:timeline_track` 注册表；Clip 保存时间与各自类型专用字段。
如果运行时对象被删掉，UUID 查不到对象时是**安全失效**（不会崩），但应该在编辑器里重新绑定，
否则你会得到"轨道看起来在跑、却什么都没发生"。

### 8.7.5 三种"控制类"轨道：Activator / Control / Speed

这三个都改"对象的状态"，但改的是三件不同的事，而且它们**共用同一时钟、按确定顺序解析**。

**Activator —— 管"在不在"。** 当前时间被某个 Activator Clip 覆盖时对象是 Active；走出 Clip 就 Inactive，
**既不 tick 也不渲染**。这是最便宜的开关键。

**Control —— 管"什么时候从头开始"。** 进入或切换到某个 Control Clip 时，它会：

1. Reset 目标对象；
2. **递归** Reset 它的所有子对象；
3. 套用 Clip Seed（或者去 Executor 的随机源取一个新 Seed）；
4. 在 Clip 范围内把对象启用并渲染出来。

所以 Control 适合两种场景：同一泳道里"放完一个再放一个"的一次性发射器；以及"用同一个固定种子
把同一棵子树一模一样地重放一遍"。

**Speed —— 管"跑多快"。** SpeedTrack 采样一个标量写进目标的 `selfTimeScale`，子对象继承父级的层级时间缩放：

| 值 | 效果 |
|---|---|
| `0` | 对象还在，但模拟冻结 |
| `0 < s < 1` | 慢动作 |
| `1` | 正常（每游戏刻一个模拟步） |
| `> 1` | 每个游戏刻跑多个**有上限**的模拟子步 |

最后一行要注意：运行时会给每 tick 的子步数量设上限，因此**极大的 Speed 并不能在一个刻里模拟任意长的时间**。
想做"一秒跳过十秒"的效果，请回去调 Clip 的 Start/Duration，而不是把 Speed 拉到 100。
（本机源码里的上限是 `FXObject.MAX_SUBSTEPS = 16`，见 `client/gameobject/FXObject.java:53`。）

**状态恢复这条容易被忽略但很重要**：SpeedTrack 被删除、Mute 或改绑之后，原目标的缩放会**恢复成 1**；
Activator/Control 不再控制某个对象时，它的 Active 与 Visible 也会恢复。这是好事（改错轨道不会把对象永久卡死），
但也意味着你不能指望"用 Mute 来持久地压住某个状态"。

还有一条官方明确提醒的坑：Activator 和 Control 同时存在时，解析器 `TimelineState.resolve` 会**同时**考虑
两者是否存在、当前是否 Active，**不存在简单的"后者覆盖前者"**。两者的组合请以编辑器预览为准，
不要靠脑补推导。

> 想让"整棵子效果一起重启/一起变速"？把 Control 或 Speed 绑到一个 Empty 父对象上，而不是逐个绑子对象。

### 8.7.6 Animation Track 与录制

Animation Track 采样的是**强类型属性**，然后写回 FX 对象。它有两类目标：

1. **Transform**：`position` / `rotation` / `scale`；
2. **Config Property**：发射器 Runtime 上**已注册的** `RuntimeValue` 槽——也就是 §8.5 里那些模块参数
   （发射率、速度、颜色……），以及每个配置动态生成的 Additional GPU Data 通道。

可动画的类型包括：标量 `float` / `int` / `boolean`、`NumberFunction` 与 `NumberFunction3`、
颜色与 HDR 颜色、Emitter 类型注册的 Renderer / Material Override 字段。

**关键限制：属性菜单来自目标对象的 `FXObjectType`，不是反射。** 代码里写得很直白——不能通过反射动画
任意字段，字段必须有受支持的 `ConfigValueType` 且注册过 `RuntimeBinding`。所以"为什么这个参数不能打关键帧"
的答案通常是"它的类型没被登记成可动画属性"，而不是你操作错了。

编辑器的 Lane 里可以放四种东西：

| 条目 | 用途 |
|---|---|
| Keyframe | 在指定时间采一个值，Key 之间自动插值（曲线 Key 带切线手柄） |
| Curve Clip | 在一段范围内用一个 Curve 表达式 |
| Gradient Clip | 在一段范围内编辑颜色/透明度的 Stop（颜色泳道专用） |
| Expression Clip | 用 Clip 的 local time 算表达式 |

**录制（Record Mode）**的操作顺序：

1. 把 Animation Track 绑到目标对象；
2. 添加并选中你要录的属性；
3. 打开 Record；
4. 播放，并在需要的时间点直接在 Inspector 或 Gizmo 里改目标；
5. 关掉 Record，**从头播一遍**验证插值。

录制期间运行时会**冻结该目标的逐帧动画重放**（`setRecording(true)` 之后 `frame()` 里跳过
`applyAnimations`，见 `TimelinePlayer.java:172-175` 与 `:147-155`），否则你刚做的修改会被每帧重新采样覆盖掉。
需要时可以用 Capture 读取 Authored/Live 值，绕过现有的运行时覆盖。

### 8.7.7 Signal：让特效和游戏逻辑对暗号

Signal 是 Timeline 里唯一"对外说话"的东西，语义只有一句话：**在第 t 刻，用通道 c 喊一个名字 n，
附带一包数据 d。**

| 概念 | 说明 |
|---|---|
| `Signal` | `(time, name, data)` 三元组，`data` 是任意 `CompoundTag`；同一轨道里允许重名 |
| Channel（通道） | **就是该 Signal 轨道自己的显示名**（`signalTrack.displayName()`） |
| 发送窗口 | `(lastSignalTick, time]`——左开右闭，所以**不重发、也不补发** |
| 触发时机 | 只在**正向、实时**播放时发；编辑器拖 Playhead（Scrub）会关掉 dispatch |

「左开右闭 + 单调推进的时间戳」这套设计的目的是**防重复触发**：`begin()` 会重置窗口，普通求值反复走到同一时刻
也不会二次触发，回放式跳转更不会把中间所有信号补发一遍。

接收端有两条通道，同一条 Signal 会同时走这两条（`TimelinePlayer.dispatchSignals`，`:371-390`）：

1. **执行器自己的钩子**：`IEffectExecutor#onTimelineSignal(channel, name, data, time)`
   —— 只有"当前这次播放"的执行器收得到，适合"播到这个特效的这一刻，让这个特效做点什么"；
2. **全局监听**：`PhotonSignals.register(Listener)`（`client/fx/timeline/PhotonSignals.java`）
   —— 谁注册谁都能收到，适合"特效播到这一刻，让整个游戏做点什么"。

全局监听器的签名是：

```java
public interface Listener {
    void onSignal(@Nullable IEffectExecutor effect, String channel, String name,
                  CompoundTag data, double time);
}
```

两个工程上的细节：`effect` 可能为 `null`（编辑器路径），**必须判空**；某个监听器抛异常会被捕获并记日志
（`"Timeline signal listener threw"`），**不会**影响其它监听器——所以别指望"抛异常来中断"。

Java 侧的典型接法：

```java
// 全局监听：一次注册，收所有特效的信号
PhotonSignals.Listener listener = (effect, channel, name, data, time) -> {
    if (!"skill".equals(channel)) {
        return;                                  // 通道先过滤，减少无效判断
    }
    switch (name) {
        case "charge_full" -> playChargeFullSound();
        case "burst"       -> spawnDamageNumbers(data.getFloat("power").orElse(1f));
        default -> { }
    }
};
PhotonSignals.register(listener);
// 卸载时一定要 unregister，否则监听器会一直挂着
```

```java
// 或者：只关心自己这次播放的执行器
@Override
public void onTimelineSignal(String channel, String name, CompoundTag data, double time) {
    if ("skill".equals(channel) && "burst".equals(name)) {
        // 只改这次播放的运行数据，不碰资产
    }
}
```

### 8.7.8 Audio 与 Group

**Audio Clip** 选一个 Sound Event，并配置 Category、Attenuation（是否按距离衰减）、Duration，
以及音量/音调函数（曲线形式的 Volume/Pitch 自 2.2.x 起提供）。它的生命周期规则：

- 进入 Clip 时启动 Sound Instance；
- 离开或切到别的 Clip 时请求停止上一个 Instance；
- **Clip 比声音长就循环，Clip 比声音短就在 Clip 末尾截断**——也就是说声音不会拖过 Clip 边界；
- 打开 Attenuation 且绑定了 Target 时，声源位置跟随该 FX 对象；
- 编辑器预览可能会强制成"非定位声音"，保证你在预览相机旁能听到。

**Track Group** 只做两件事：把子轨道收起来、以及提供**层级 Mute**（关掉组 = 关掉组里全部轨道）。
它不会创建 FX 对象、也不会产生 Transform。

### 8.7.9 编辑器里怎么播放，和游戏内差在哪

Timeline 面板由 Track 树、时间标尺、Clip 泳道和属性编辑器组成；选中轨道或属性后，下方的属性区会跟着变。

| 控件 | 行为 |
|---|---|
| Play / Pause | 推进或冻结主时钟 |
| Stop / Restart | 重置运行时对象，并从时间零重新计算 |
| Loop | 在编辑器里反复播预览范围 |
| Scrub | 按指针所在时间**直接计算**效果，不做正常的前向播放 |

两个必须知道的差异：

1. **Scrub 会关掉 Signal 和 Audio 的发派**（`setSignalDispatch(false)` / `setAudioDispatch(false)`）。
   否则你拖一下播放头就会把中间所有信号连发一遍、声音叠成一片。游戏内的正向播放才会打开这两者。
2. **远距离 Scrub 会触发 Fast Seek**。因为粒子状态不是"某个时间戳的纯函数"（它依赖一路模拟的历史），
   要跳到很远的时刻只能从头快放一遍。编辑器会把连续的 Seek 请求合并，用 Fast Replay 跑，
   不会让指针经过的每个像素都排一次完整模拟。

编辑器用的是**独立的** Particle Manager 与 Post Effect Stack；游戏内用的是执行器真实的 Level、
实体/方块锚点、信号钩子和**全局**后处理栈。所以"编辑器里好好的、进游戏就变了"通常不是玄学，
而是这两套上下文本来就不一样（例如你的 Java 侧 `postEffectSink()` 换成了别的栈）。

**推荐的检查顺序**：先用 Scrub 检查时间布局（谁在第几刻出现、有没有重叠），再从零开始正向播放一遍，
专门验证 Control 的重启、随机种子、Signal、Audio 和后处理淡出这五项——它们**只在正向播放里成立**。

### 8.7.10 案例：三段式「起手 → 蓄力 → 爆发」

**目标**：做一个 100 刻（5 秒）的技能演出——地面法阵亮起并扩张、蓄力时法阵收缩变亮并放出信号、
爆发瞬间炸开一簇粒子并让屏幕闪一下，最后收尾淡出。

**先想清楚对象树**（Hierarchy）：

```text
Root (Empty)
├─ array    ParticleEmitter   地面法阵，循环发射，常驻
├─ charge   Empty             承载"蓄力"这棵子树，方便整体变速
│   └─ swirl ParticleEmitter  围绕法阵旋转的光点
└─ burst    ParticleEmitter   一次性爆发，不循环
```

**再排轨道**（时长全部以 tick 计，`duration = 100`）：

| 轨道 | 类型 | 目标 | 片段 | 起止（tick） | 说明 |
|---|---|---|---|---|---|
| 起手 | Activator | `array` | 1 个 | 0 – 100 | 法阵全程在 |
| 起手 | Animation | `array` | Scale 曲线 | 0 – 20 | 0.0 → 1.0，缓出，避免"啪"地弹出来 |
| 起手 | Animation | `array` | Color 渐变 | 0 – 20 | 透明度 0 → 0.85 |
| 蓄力 | Speed | `charge` | 1 个 | 20 – 60 | 1.0 → 0.35，慢动作 |
| 蓄力 | Animation | `swirl` | Emission Rate | 20 – 60 | 8 → 30 |
| 蓄力 | Animation | `array` | Scale 曲线 | 20 – 60 | 1.0 → 0.72，配一条轻微"呼吸"的正弦曲线 |
| 暗号 | Signal | — | 2 个 | t=20 / t=60 | `charge_start` / `charge_full` |
| 爆发 | Control | `burst` | 1 个 | 60 – 100 | 固定 seed `20260928`，保证每次炸开形状一致 |
| 爆发 | Activator | `array` | 1 个 | 60 – 72 | 爆发瞬间把法阵压掉 |
| 爆发 | Post Process | — | 1 个 | 60 – 78 | 权重 0 → 1 → 0 的闪光，参数 `intensity` 由 1.4 降到 0.3 |
| 爆发 | Audio | `burst` | 1 个 | 60 – 78 | boom 音效，Attenuation 开，位置跟随 `burst` |
| 收尾 | Animation | `swirl` | Emission Rate | 60 – 100 | 30 → 0，自然熄掉 |

**为什么这么排**（这几条是这个案例真正想教的东西）：

1. **用 Speed 而不是改参数做慢动作。** 如果靠"把 emission rate 调小 + 把速度调小"来表达蓄力，
   你就要同时改五六个参数，而且每个发射器都得改一遍。绑到 `charge` 这个 Empty 父对象上改速度，
   子树里所有对象**一起**慢下来，改一处就够。
2. **爆发用 Control + 固定 Seed。** `burst` 是一个一次性发射器，用 Control 在 t=60 重启它，
   并套用固定种子，这样每次释放技能的碎片形状都一样——对技能特效来说，"每次都一样"通常是优点
   （玩家能记住这个技能的"手感"）。想每次不同就把种子换成 random seed。
3. **法阵的关闭用第二个 Activator，而不是直接调透明度。** Activator 是"在不在"的开关键，
   比把它缩到 0 或把 alpha 压到 0 更干净：不发粒子、不 tick，也不会有"看不见但还在算"的浪费。
4. **信号放在"状态真正切换"的那一帧。** `charge_start` 摆在 t=20（Speed 轨道开始的同一刻），
   `charge_full` 摆在 t=60（爆发的前一刻）。这样 Java 侧的逻辑时刻和玩家看到的画面是同一条时间线，
   不会出现"声音响了但法阵还没亮"。

**Java 侧只需要三行**（更多细节见 §9）：

```java
// 播放：像 §9.5 那样建执行器并 start()；Timeline 会自己跑，你不用管阶段
executor.start();
```

信号接收见 §8.7.7；后处理权重怎么算见 §8.8。

**验收清单**（按顺序过一遍）：

- Scrub 拖到 t=19 / t=21，法阵尺寸过渡应该是连续的，不应有跳变；
- 正向播放时 t=20 只收到一次 `charge_start`，t=60 只收到一次 `charge_full`（在监听器里打日志确认）；
- 蓄力段 `swirl` 的光点明显变慢但**不**卡顿（如果卡顿，检查 Speed 是否给得太极端，触发了每 tick 子步上限）；
- Mute 掉"蓄力"那条 Speed 轨道后再播，法阵应恢复常速——验证状态恢复；
- t=60 的爆发形状连续播三次应完全一致（固定种子生效）。

## 8.8 后处理：给整屏加效果

### 8.8.1 后处理和粒子特效是两种东西

粒子特效是"往画面里**加**东西"；后处理是"把画好的画面**再处理一遍**"。

打个比方：粒子特效是往舞台上撒纸屑、开灯光；后处理是给摄像机镜头加滤镜——它拿到的是**整张已经画好的画面**
（颜色 + 深度），在整屏范围内做一次或多次像素计算，再放回去。

所以后处理天生适合表达这些"全局感"：

| 想要的效果 | 为什么必须用后处理 |
|---|---|
| 蓄力时屏幕边缘发暗、轻微扭曲 | 它作用在"整个屏幕"，粒子做不到 |
| 受击瞬间整屏红闪一下 | 同上，而且是逐帧可调的权重 |
| 开大时画面变灰再恢复 | 颜色分级属于全屏操作 |
| 让特效区域发光溢出（Bloom） | 需要先有整张画面才能做 |

它**不适合**表达"某个位置冒火花"——那是粒子的活。两者的产物最后会被合成到一起（Photon 是先合成 FX 层、
再跑后处理链，源码注释写得很明确：这样后处理才看得到你的粒子）。

> 术语对照：后处理（Post-Processing / Post FX）、全屏 Pass（Fullscreen Pass）、
> 渲染图（Render Graph）、全屏着色器图（Fullscreen Shader Graph）、权重（Weight）、遮罩（Mask）。

### 8.8.2 核心心法：逐帧请求，不是开关

这是 Photon 后处理**最容易理解错**的一点，先把它刻进脑子里：

> 后处理没有"打开 / 关闭"这个动作。**你每渲染一帧就请求一次**；哪一帧不请求了，效果下一帧自然就没了。

源码里的入口只有这一行（`client/postfx/PhotonPostFX.java:45`）：

```java
// 只要这一帧想让它生效，就提交一次；停止提交 = 下一帧自动失效
PhotonPostFX.submit(PhotonPostFX.parsePath("wiki_tint_effect"),
                    Map.of("Tint", new float[]{1f, 0.2f, 0.2f, 1f}),
                    0.6f);   // weight：0 = 完全原画面，1 = 完全效果
```

`submit` 的实现只有三行：`effect == null || weight <= 0` 直接丢掉，否则把请求（权重大于 1 会被夹到 1）
放进本帧的请求列表（`PostEffectStack.java:95-98`）。就这么多——**没有状态、没有开关、没有持久化**。

这条模型带来三个直接推论，都是排错时的第一反应：

1. **"效果闪一下就没了"** → 你的请求代码只在某一帧跑了（比如放在了事件回调里，而没有每帧跑）。
2. **"效果关不掉"** → 还有别的地方在持续提交同一个效果（Timeline 的 Post Process Clip 是最常见的元凶）。
3. **"Timeline 里 Mute 掉 Clip 却没反应"** → 先确认没有 Java 侧的请求还在发。

### 8.8.3 一个后处理效果由哪些东西组成

你不需要一次理解全部，先看这张职责表（整理自官方文档站与源码包结构）：

| 资源 / 运行时 | 职责 | 真实位置 |
|---|---|---|
| Fullscreen Shader Graph | 像素算法本体，以及它对外**暴露的** Sampler/Uniform | `client/postfx/shadergraph/FullscreenShaderGraph.java` |
| Core Shader Pass | 手写 JSON + VSH/FSH 的方案（移植老 Shader 用） | `client/postfx/runtime/CustomShaderPass.java` |
| Render Graph | Pass 的**顺序**、临时 Target、输入输出、Priority | `client/postfx/graph/RenderGraph.java` |
| Post Process Clip | 挂在 Timeline 上的那段"什么时候生效、权重多少、参数覆盖" | `client/fx/timeline/PostProcessClip.java` |
| PostEffectStack | 把同一帧的所有请求**合并**并真正执行 | `client/postfx/runtime/PostEffectStack.java` |

一句话串起来：**Fullscreen Graph 写算法 → Render Graph 把算法接成一条链 → Clip 或 Java 每帧按权重请求 →
Stack 合并请求并执行。**

### 8.8.4 Render Graph：把 Pass 接成一条链

Render Graph 是一个**有向无环图**（DAG），节点只有两类：纹理资源、全屏 Dispatch。
它描述的是"一个可复用的效果"，**不是**整个游戏的渲染器——别把它当成 Minecraft 的渲染管线。

**输入节点**（Render Graph 能拿到的原料）：

| 输入 | 内容 |
|---|---|
| Scene Color | 全局效果轴上**当前位置**的 HDR 画面（已经包含优先级更低的效果） |
| Scene Depth | 捕获到的不透明场景深度 |
| Custom Mask | 被标记的 Photon 渲染器写入的 `mask group / 255`，其余为黑 |
| Custom Depth | 被标记渲染器的深度，底层预填场景深度 |
| Texture Input | 外部或命名纹理（LUT、噪声……） |
| Effect Weight | 本次执行合并后的请求权重 |

**Pass 的来源有两种**：跑一个 Fullscreen Shader Graph，或者跑一个手写 Core Shader。
Graph 里暴露出来的 Texture Sampler 会变成**只能连线的纹理 Port**；暴露的 Scalar / vec2 / vec3 / vec4
会变成**可填值的 Value Port**；Screen Size、Time、Matrix 这类引擎统一量则自动绑定。

**Target 尺寸**有三种模式：

| 模式 | 含义 |
|---|---|
| Screen Relative | 屏幕 / 效果链尺寸 × Scale |
| Input Relative | 更早的输入 / 资源尺寸 × Scale |
| Absolute | 固定的宽 × 高 |

质量够用的时候，Blur / Bloom 这类链条应该用**半分辨率**——这是后处理省性能最有效的一刀。
默认格式是 `RGBA16F`（支持 HDR）；低精度格式省显存和带宽，`R8` 适合单通道 Mask，但不适合 HDR 颜色。

**临时 Target 是从池子里借的**，只保证活到"本次执行不再需要它"为止。
除非外部系统明确说这个 Target 归它管，**不要假设 Pass 的输出能留到下一帧**。

**Priority 与 Auto Blend**：

- Priority 决定这个效果挂在全局轴的哪个位置。**内置 Bloom 的优先级是 0**：负值在 Bloom **之前**执行
  （所以结果会参与 Bloom，比如"先把发光提亮"），正值在 Bloom **之后**执行（用于处理最终画面）；
  相同 Priority 按 Effect Path 稳定排序。
- Auto Blend 打开时，最终执行的是 `mix(scene, effect, Weight)`；**只有**当你的图故意自己去读 Effect Weight
  并手动混合时才关掉它。

**依赖规则（编译期就会拦你）**：每个 Pass 的输入必须来自输入节点或**更早**的 Pass；
出现环、缺必需纹理、没有 Effect Output、Source Graph 无效、尺寸或类型不兼容，都会让编译失败。
实用建议：让图**从左到右**排，Blackboard 参数**按用途命名**，排错会轻松很多。

### 8.8.5 动手做一个 Tint 效果（照着做）

下面这个例子把"从零到一个能被 Timeline 驱动的效果"走完一遍（做法对照官方
`post-processing/authoring-a-post-effect` 一页，讲法按"为什么这么做"重写）。

**第一步：建 Fullscreen Graph**（Resources 里新建，命名 `wiki_tint`）

1. 加一个名为 `Scene` 的 Texture/Sampler 类型的 Graph Variable；
2. 加一个名为 `Tint` 的 Color 参数，默认白色；
3. 把 **Fullscreen Position UV** 接到 `Scene` 的 Texture Sample；
4. 采样结果的 RGB 乘 `Tint.rgb`，Alpha 保持采样到的 Alpha；
5. 连到 **Fullscreen Output**，保存。

**关键认识**：需要被 Render Graph、Timeline 或 Java 从外部修改的值，**必须暴露成 Graph Variable**。
图内部的普通常量不会变成 Pass Port，外部也就永远改不到它——"为什么我的参数在 Clip 里找不到"通常就是这个原因。

**第二步：建 Render Graph**（命名 `wiki_tint_effect`）

1. 加一个 Scene Color 输入、一个 Pass、一个 Effect Output；
2. Pass 的 Source 选 `wiki_tint` 这张 Fullscreen Graph；
3. 把 Scene Color 接到 Pass 的 Scene Port；
4. 在 Pass 上设置 `Tint`（或者连 Blackboard 参数）；
5. Pass 的输出接到 Effect Output；
6. **保持 Auto Blend 打开**，保存。

Pass 的 Port 是**镜像** Source Graph 暴露出来的 Variable 的。所以重命名 `Scene` 或 `Tint` 之后，
原来的连线可能变成 Orphan（孤立），需要重新连一次。

**第三步：预览**。打开 Render Graph Preview，让效果盖在真实世界上：

- 把 Weight 拖到 0，画面应该**完全等于原图**；拖到 1，应该是完整效果。这条能过，
  说明"权重混合"这条链路是通的。
- 画面全黑时，**先把 Scene Color 直连 Effect Output**：如果这样能看到原画面，说明输入没问题，
  再去查 Pass 和纹理 Port 的连线。这是最快的二分法。

**第四步：挂到 Timeline 上**

1. 新建一条 Post Process Track；
2. 加一个 Clip，效果选 `wiki_tint_effect`；
3. 设 Start / Duration 和 Fade / Weight 曲线；
4. 在 Clip 的参数里覆盖 `Tint`；
5. 把播放头**拖过 Clip 的两端**，检查 Fade In/Out。

Clip 在有效范围内**每帧**提交请求；Clip 结束或被 Mute，就没有请求了，效果也就没了。

**第五步：打包**。Render Graph 依赖它的 Fullscreen Graph 以及它采样的纹理。导出 FX Pack 之前先保存所有资源，
然后**在一个干净的资源环境里测一遍导出的 Pack**——这样才找得出没被收集进去的全局资源依赖。

### 8.8.6 Fullscreen Graph 与自定义纹理

Fullscreen Graph 和粒子用的 Shader Graph 是**同一套类型节点**，区别只在几何：这里是全屏四边形，
输入由 Render Graph 的 Pass Port 提供。

三个坐标相关的要点（都是踩过的坑）：

1. **Fullscreen Position** 提供全屏顶点基础与归一化 UV；
2. **Texel Size** 返回 Target Size 的倒数——Blur、Outline、色差的"像素偏移"都该乘它；
3. Scene / Depth 的重建节点使用**当前 Pass 捕获的相机状态**。

**不要写死屏幕分辨率。** 预览窗口、窗口缩放、UI Scale、半分辨率 Pass 都会改变真实的 Target Size。
写死 1920×1080 的效果，在别人的 2K 屏或 Photon 编辑器的小预览窗里就会走样。

**暴露输入的类型对照**：

| Graph Variable | 变成的 Port / 用途 |
|---|---|
| Sampler / Texture | 连 Scene Color、深度派生纹理、前一个 Pass、或 Texture Input |
| `float` | radius、strength、threshold、time scale |
| `vec2` | direction、center、distortion scale |
| `vec3` / `vec4` / color | tint、通道权重、描边颜色 |

注意：**Weight 是引擎管的，不是普通 Pass Port**。如果你的 Shader 想自己参与混合，要用 Effect Weight Node。

**外部纹理**：LUT、Blue Noise、Ramp、Lens Dirt 都可以通过 Texture Input 进来。
把它们放进项目 / FX Pack 的命名空间，并写清期望的 Filter / Wrap。
LUT 的尺寸和打包方式属于"效果的接口约定"——随便换一张图片不会得到有意义的结果。

**Graph Pass 与手写 Pass 怎么选**：

| Fullscreen Graph | 手写 Core Shader |
|---|---|
| 可视化编辑、类型连线 | 直接控制 GLSL |
| 暴露 Variable 决定 Port | 从 JSON 的 Sampler / Uniform 反射出 Port |
| 编译器生成 Stage 胶水代码 | 作者自己维护 JSON/VSH/FSH 的兼容性 |
| 更好重构资源 | 更容易移植已有 Shader |

两者跑在**同一个全屏 Quad 和 Target Pool** 上，也可以在同一条 Render Graph 里混用。

### 8.8.7 手写 Fullscreen Shader（移植老 Shader 用）

当你要移植一个现成的后处理、写复杂循环、或者 Graph 暂时表达不了某个算法时，就写手写 Pass。
它和 Graph Pass 共享全屏 Quad、HDR Target Pool、Priority、Mask 和混合系统。

**最小资源结构**（通常不需要自己写顶点着色器，Photon 随 LDLib2 提供了 `ldlib2:fast_blit`）：

```text
assets/wiki/shaders/core/postfx/wave_tint.json
assets/wiki/shaders/core/postfx/wave_tint.fsh
```

Render Graph 里填的 **Shader ID 是 `wiki:postfx/wave_tint`**（命名空间 + 相对 `shaders/core/` 的路径，不带后缀）。

**JSON 长这样**：

```json
{
  "vertex": "ldlib2:fast_blit",
  "fragment": "wiki:postfx/wave_tint",
  "samplers": [
    { "name": "DiffuseSampler" }
  ],
  "uniforms": [
    { "name": "DiffuseSampler_TexelSize", "type": "float", "count": 4, "values": [1, 1, 1, 1] },
    { "name": "ScreenSize", "type": "float", "count": 2, "values": [1, 1] },
    { "name": "GameTime", "type": "float", "count": 1, "values": [0] },
    { "name": "Strength", "type": "float", "count": 1, "values": [4] },
    { "name": "TintColor", "type": "float", "count": 4, "values": [1, 1, 1, 1] }
  ]
}
```

**片元着色器**：

```glsl
#version 150

uniform sampler2D DiffuseSampler;
uniform vec4 DiffuseSampler_TexelSize;  // (width, height, 1/width, 1/height)
uniform vec2 ScreenSize;
uniform float GameTime;
uniform float Strength;
uniform vec4 TintColor;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float phase = texCoord.y * 40.0 + GameTime * 6.2831853;
    float offset = sin(phase) * Strength * DiffuseSampler_TexelSize.z; // .z 是 1/width
    vec4 scene = texture(DiffuseSampler, texCoord + vec2(offset, 0.0));
    fragColor = scene * TintColor;
}
```

（`ScreenSize` 在这个最小算法里没参与计算，留在那里只是为了演示引擎 uniform 的声明方式。
用不到的 uniform 可能被 GLSL 编译器优化掉，Photon 会安全地跳过不存在的 Location。）

**接进 Render Graph**：加 Pass 节点 → Source Type 改成 Custom Shader → Shader 填 `wiki:postfx/wave_tint`
→ Pass 会从 JSON **反射**出 `DiffuseSampler`、`Strength`、`TintColor` 这几个 Port
→ 把 Scene Color 或上一个 Pass 的输出连到 `DiffuseSampler` → Pass 输出连到 Effect Output。

`Strength` 和 `TintColor` 既可以直接填常量，也可以连 Blackboard 参数供 Timeline / Java 覆盖。
想让请求的 Weight 驱动某个 uniform，就把 Effect Weight 连到对应 Value Port——
**手写 Shader 里没有强制叫 `Weight` 的内置 uniform**，这一点和粒子 Shader 的直觉不一样。

改完 JSON 的 sampler/uniform 接口之后，要**执行一次资源重载，并在 Pass 上重新选一次 Shader**，
让节点重建 Port。源码损坏时编辑器会暂时保留最后一次有效的 Port 集合（免得一次失败的 reload 把连线全清掉），
但 Render Graph 仍然不会编译成功。

**Sampler 没有固定名字，全靠连线**（和粒子 Custom Shader 最不一样的地方）：

```json
"samplers": [
  { "name": "SceneColor" },
  { "name": "SceneDepth" },
  { "name": "NoiseTexture" }
]
```

这会生成三个**必连**的 Port。它们分别通常接：

| Render Graph 来源 | 常见用途 |
|---|---|
| Scene Color | 原始或前序效果后的 HDR 颜色 |
| Scene Depth | 深度淡出、景深、重建世界位置 |
| Custom Mask | 按 Mask Group 限制作用区域 |
| Custom Depth | 被 Photon 标记对象的深度 |
| 更早的 Pass Output | Blur、Bloom、Composite 等多 Pass 链 |
| Texture Input | LUT、噪声、Ramp 或外部参数纹理 |

**注意**：后处理**不使用** `SamplerSceneColor` / `SamplerSceneDepth` 这类自动绑定名（那是粒子 Shader 的约定）。
数据来源完全由 Render Graph 连线决定；**漏连任意一个 sampler 都会编译失败**。

**`_TexelSize` 约定**：给名为 `DiffuseSampler` 的 sampler 声明同名后缀 uniform `DiffuseSampler_TexelSize`，
Photon 会写入 `(width, height, 1/width, 1/height)`。它**不会**变成 Value Port，专供 Blur / Outline / 像素偏移使用。
Scene、Depth、Mask、Pass Output 的尺寸都是已知的，能正确更新；但 **Texture Input** 用固定资源或运行时参数时，
Executor 不一定知道原图尺寸，这时会保留 JSON 里的默认值——所以需要真实尺寸的算法应该把尺寸**当作独立参数传进来**。

**能被反射成 Port 的只有这几种**：

| JSON 写法 | Port 类型 |
|---|---|
| `"type": "float", "count": 1` | float |
| `"type": "float", "count": 2` | vec2 |
| `"type": "float", "count": 3` | vec3 |
| `"type": "float", "count": 4` | vec4 |

`int`、矩阵、以及 `count` 超出 1–4 的声明**都不会**成为参数 Port。
JSON 里的 `values` 是**每次 Dispatch 的默认值**：执行前先恢复默认，再应用 Pass 常量 / Blackboard 参数 / Effect Weight，
这样上一帧的值不会泄漏到下一次执行。
颜色没有专用反射规则——需要颜色就用 `float/count 4`，在 Render Graph 里以 vec4 传递。

**引擎自带的 Uniform**（不显示为 Port，直接用就行）：

| 名称 | 类型 | 内容 |
|---|---|---|
| `ScreenSize` | vec2 | 当前 Pass 输出 Target 的 (width, height)；半分辨率 Pass 拿到的是半分辨率尺寸 |
| `GameTime` | float | `RenderSystem.getShaderGameTime()`，一个 Minecraft 日周期为 0..1 |
| `ModelViewMat` | mat4 | 捕获该画面的相机 View / ModelView 矩阵 |
| `ProjMat` | mat4 | 捕获该画面的投影矩阵 |
| `<Sampler>_TexelSize` | vec4 | 已知输入纹理的 (width, height, 1/width, 1/height) |

**相机与屏幕空间 Uniform**（较新版本提供，做"重建世界位置"这类效果时需要）：

| 名称 | 类型 | 内容 |
|---|---|---|
| `U_ViewPort` | vec4 | 当前相机在 Pass Target 里的 (x, y, width, height)；编辑器场景可能只占一块子区域 |
| `kg_Time` | float | 与 GameTime 同源的秒值 |
| `kg_ViewMat` / `kg_IViewMat` | mat4 | View 矩阵及其逆 |
| `kg_IModelViewMat` | mat4 | 当前相机路径下同样为逆 View 矩阵 |
| `kg_IProjMat` | mat4 | 投影矩阵的逆 |
| `kg_CameraBlockPos` / `kg_CameraOffset` | vec3 | 精度拆分后的相机位置 |

用屏幕坐标时**先按 Viewport 归一化**，这样同一份 Shader 才能同时适配游戏全屏、编辑器的子窗口和降分辨率 Pass：

```glsl
uniform vec4 U_ViewPort;

vec2 viewportUV = (gl_FragCoord.xy - U_ViewPort.xy) / U_ViewPort.zw;
```

**自己写顶点着色器**（一般不需要）：Pass 用 `DefaultVertexFormat.POSITION`，最小接口就是——

```glsl
#version 150

in vec3 Position;
out vec2 texCoord;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    texCoord = Position.xy * 0.5 + 0.5;
}
```

顶点与片元的 varying 名称、类型必须匹配。全屏 Quad 已经覆盖 NDC 的 -1..1，**不需要**再乘普通世界对象的模型矩阵。

**排错顺序建议**：先写 `fragColor = texture(Input, texCoord);` 验证连线通了，再加 Depth、Mask 和数学；
JSON 必须是合法 JSON（不能有注释和尾随逗号）；加载失败看 `latest.log` 与 Render Graph 的编译错误。

### 8.8.8 内置 Pass：不用自己写就有的一批积木

Photon 内置了一组可以由 Render Graph Pass 直接选用的 Core Shader，它们是**积木**——
一个完整效果可以接多个 Pass（比如"先横向模糊、再纵向模糊、最后叠加"）。

| 分类 | Pass |
|---|---|
| 颜色 | grayscale、sepia、brightness_contrast、hue_saturation、tint、posterize |
| 镜头/屏幕 | vignette、rgb_shift、pixelate、dot_screen、film、glitch、lens_distortion |
| Filter | blur_h、blur_v、sharpen、radial_blur |
| Composite | bright、add_mix、dof_composite |
| Edge/Mask | outline、show_mask、mask_outline |

Pass 节点会读 Core Shader 的 JSON：**Sampler 变成纹理 Port，Float/Vector Uniform 变成可配置的 Value Port**。
具体某个 Pass 有哪些参数，以节点 Inspector 为准（不同版本的参数会变，不要在文档里背参数名）。

### 8.8.9 混合、Mask 与 Depth：让效果"只作用在想要的地方"

**请求合并**：同一个 Effect Path 的普通请求会**合并成一次执行**。合并公式是权重的顺序插值，
等价于：

```text
weight_total = 1 - (1 - w1)(1 - w2)(1 - w3) ...
```

也就是"两盏 0.5 的灯叠起来不是 1.0，而是 0.75"——这是从源码里能读出来的实际行为
（`PostEffectStack.blendRequests` 里的 `weight += (1f - weight) * request.weight()`）。
参数则分两类处理：可插值的按权重**从低到高顺序插值**（起点是 schema 默认值），
不可插值的**取权重最高的那条覆盖**。

**什么时候需要两条独立执行**：两个实例要保留**不同参数或不同 Mask Group** 时，把 Timeline Clip 上的
`Independent`（保留参数）打开。代价很直接——**多跑一次全屏执行**。

**Priority 与 Bloom**：效果按 Priority 围绕内置 Bloom（0）排序。
想让结果参与 Bloom（例如"先把亮度抬上去再让 bloom 抓"），用负 Priority；想处理 Bloom **之后**的最终画面，用正 Priority。

**Custom Mask 的正确用法**：

1. 在 Particle / Trail / Beam / AraTrail 渲染器上开启 **Write Custom Mask**；
2. 选一个 8-bit 的 Mask Group 和可选的 Alpha Cutoff；
3. 在 Render Graph 里读 Custom Mask，需要遮挡信息时再读 Custom Depth；
4. 用 `round(mask.r * 255)` 匹配 Group，或者直接启用 Post Process Clip 的 Mask Filter。

规则：**Group 0 是背景**，你要标记的组从 **1..255**。Custom Depth 能区分"标记的特效在几何前面可见"
还是"被几何挡住"。

两个现成的 Mask Pass：`mask_outline` 是可直接使用的逐 Group 描边；`show_mask` 用不同颜色显示 Group ID，
适合调试"我的 Mask 到底写进哪个组了"。

**一个必须知道的区别**：普通全屏 Outline 看的是 **Scene Depth**，它会检测场景里**所有**边缘；
Custom Mask Outline 只针对**被你选中的那些 Photon 渲染器**。两者目的不同，不能互相替代——
想要"只勾出我的技能特效"，就必须走 Custom Mask。

### 8.8.10 Java 侧怎么用

公开入口是 `client/postfx/PhotonPostFX.java` 和 `IEffectExecutor#postEffectSink()`：

| API | 语义 |
|---|---|
| `PhotonPostFX.submit(effect, params, weight)` | 请求**当帧**生效；`params` 按参数显示名覆盖效果暴露的参数 |
| `PhotonPostFX.parsePath(text)` | 把文本解析成效果路径，接受 `type(path)` 全名或内置效果名 |
| `PhotonPostFX.listEffectPaths()` | 列出所有可请求的效果（Render Graph + Fullscreen Graph） |
| `PhotonPostFX.setTestEffect(path, weight)` / `clearTestEffect()` | 调试用的"钉住一个效果"，排查连线时很有用 |
| `IEffectExecutor#postEffectSink()` | 这个执行器的请求**提交到哪个栈**；默认是 `PostEffectStack.GLOBAL` |

两个栈实例：**`PostEffectStack.GLOBAL`**（游戏世界）与 **`PostEffectStack.EDITOR_SCENE`**（编辑器预览）。
自定义执行器如果想隔离自己，就在 `postEffectSink()` 里返回别的栈。

**执行时机**（决定了"效果什么时候能看见 FX"）：

- 没有光影包、也没有被延迟的 FX 层时，请求在"半透明粒子之后"那一步被消费
  （`PhotonPostFX.onLevelStageAfterParticles()`）；
- 有光影包时推迟到 `LevelRenderer` 整棵调用树返回之后（也就是 pack 的 composite / final 之后）
  （`PhotonPostFX.onLevelRenderComplete()`）；
- 帧边界由 `RenderFrameEvent.Post` 触发 `PhotonPostFX.onFrameEnd()`：回收输出、丢掉过期请求、推进池时钟。

另外注意：`submit` 的入参是 `IResourcePath`（不是 `Identifier`），`parsePath` 是给命令行和调试用的便捷入口。

**一个全局开关会静默吃掉你的请求**：`PostEffectStack.effectsAllowed()` 读的是客户端配置
`enable_custom_effects`（`PhotonConfig.INSTANCE.enableCustomEffects`）。它一旦关掉，
**请求不报错、也不执行**——"代码明明跑了但屏幕上什么都没发生"时先看这里。
（另外还有**每个视图**的开关 `PhotonViewSettings.effects`：栈是共享的，视图的偏好不是。）

### 8.8.11 客户端配置键（调后处理 / 光影兼容时先看这张表）

| 键 | 默认 | 含义 |
|---|---|---|
| `enable_bloom` | `true` | Photon 自己的 bloom |
| `bloom_mip_level` | 5（2..10） | bloom 的 mip 层数 |
| `bloom_threshold` | 1.001（0..10） | bloom 阈值（HDR 亮度） |
| `bloom_intensity` | 0.7（0..1） | bloom 强度 |
| `enable_bloom_with_iris_shader` | `true` | 与光影包共存时是否仍跑 Photon bloom |
| `iris_composite_mode` | `AUTO` | Photon 把 FX 图像交还给光影包的方式 |
| `iris_use_translucent_particle_program` | `false` | 半透明 FX 是否走 pack 的粒子程序（默认关） |
| `fx_composite_mode` | `LATE`（可选 `VANILLA`） | 半透明 FX 的合成时机；`LATE` 防云层盖脸、防水面切特效 |
| `enable_custom_effects` | `true` | 自定义后处理链总开关（关掉后请求直接丢弃） |
| `enable_custom_effects_with_shader_pack` | `true` | 光影包下是否仍跑自定义后处理链 |
| `postfx_pool_budget_mb` | 256（16..4096） | 后处理池化渲染目标的显存预算 |

> 源码里有一处注释与实现不一致，别被带偏：`client/render/PhotonStage.java` 的 javadoc 提到 `AFTER_LEVEL`，
> 但枚举实际只有 `AFTER_OPAQUE_FEATURES`、`AFTER_TRANSLUCENT_PARTICLES`、`DEFERRED` 三个常量
> （`LAST = AFTER_TRANSLUCENT_PARTICLES`）。以枚举为准。

### 8.8.12 性能：后处理为什么"看起来没多少代码却很贵"

一句话：**每个独立的全分辨率 Pass 都要把几百万个像素读一遍、写一遍。**

- **能合并就合并**：同一个效果的多条请求合并成一次执行；不要靠"多发几次请求"来加强度，直接调 weight。
- **大范围 Blur 用低分辨率**：Target Size 选 Input/Screen Relative 加一个小于 1 的 Scale。
- **不用 Mask 就别写 Mask**：Custom Mask 只有在本帧真的有效果会读它时才会被填充
  （源码里 `hasPendingMaskConsumer()` 就是干这个的），所以别为了"以后可能用到"而常开。
- **Weight 0 不要让它继续跑**：请求的 weight 小于 `1e-3` 会被合并阶段剔除，但如果你自己每帧提交
  一个 0.0001 的请求，仍要付出判断成本——干脆别提交。
- **Pixel Radius 与 Target Resolution 有关**，换窗口尺寸要重新检查效果（在大屏幕上够看的效果，在小窗口可能糊）。
- **`postfx_pool_budget_mb` 是硬预算**：效果叠太多、Target 尺寸太大时会顶到这个上限，表现为卡顿或掉帧。

### 8.8.13 案例：受击瞬间的「红色屏幕脉冲」

**目标**：角色被击中的瞬间，屏幕边缘泛红并轻微收缩，0.35 秒内淡出，不影响中间的清晰度。

**路线选择**（先选便宜的）：

| 做法 | 成本 | 何时选 |
|---|---|---|
| 直接用内置 `vignette` + `tint` 两个 Pass 接成一条 Render Graph | 最低（不用写着色器） | 想要的就是"暗角 + 染色"，这个案例正合适 |
| 自己写 Fullscreen Graph | 中 | 需要特定的衰减曲线、噪点、色差 |
| 手写 Core Shader | 最高（要维护 JSON + GLSL） | 需要复杂循环或移植已有 Shader |

**第一步：资源侧（编辑器）**

1. Render Graph 命名 `mg_hurt_vignette`：
   - 输入：Scene Color；
   - Pass A：`tint`，`TintColor` 设为 `(1.0, 0.25, 0.25, 1.0)`，`Strength` 暴露成 Blackboard 参数；
   - Pass B：`vignette`，`Radius` 暴露成 Blackboard 参数，`Softness` 固定 0.45；
   - A 的输出接 B 的输入，B 的输出接 Effect Output；
   - Priority 用正值（**放在 bloom 之后**，因为受击反馈应该作用在最终画面上）。
2. 预览时：Weight 0 必须等于原画面；把 `Strength` 拉到 1、`Radius` 拉到 0.9，应该看到明显的边缘红晕。

**第二步：Java 侧（每帧提交，用一条衰减曲线自己算权重）**

```java
public final class HurtFlash {
    /** 脉冲总时长（tick）。0.35 秒 = 7 刻。 */
    private static final int DURATION = 7;

    private static long startedAt = Long.MIN_VALUE;

    /** 由受击事件调用（客户端）。 */
    public static void trigger() {
        startedAt = System.nanoTime();
    }

    /** 挂在每帧的渲染回调里：只有这一帧想让它生效，就提交一次。 */
    public static void onFrame() {
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
        float t = elapsedMs / (DURATION * 50f);          // 7 刻 × 50ms
        if (t < 0f || t >= 1f) {
            return;                                      // 不提交 → 下一帧效果自动消失
        }
        float weight = (1f - t) * (1f - t);              // 平方衰减：先强后弱，收得干脆
        PhotonPostFX.submit(PhotonPostFX.parsePath("mg_hurt_vignette"),
                Map.of("Strength", 0.9f * weight,
                       "Radius", 0.85f),
                weight);
    }
}
```

**为什么这么写**：

1. **用 `weight` 整体淡出，同时把 `Strength` 也乘一遍**——只调 weight 会同时让"染色"和"暗角"一起淡，
   看起来像是两个效果同步消失；把 `Strength` 也乘上权重，红色会先退、暗角后退，更像真实的受击反馈。
   （这属于美术取向，不是硬规定，可以按项目口味换曲线。）
2. **用平方衰减而不是线性**：线性淡出在前 30% 几乎看不出变化，平方（或 `1 - (1-t)^2` 的缓出）手感更"顿"。
3. **不提交就结束**，不写"关闭效果"的代码——这正是逐帧请求模型的价值。
4. **时间用系统毫秒而不是 tick**：受击反馈是纯客户端表现，用毫秒能跟渲染帧对齐，不会因为卡顿而"跳"一下。
   （若你要和游戏逻辑严格对齐，就改成 tick 计数。）

**验收清单**：

- 连打三次受击，脉冲应该**重新起算**而不是叠加成常亮（`startedAt` 被覆盖）；
- 把 `enable_custom_effects` 关掉再触发，屏幕不应该有任何变化，且日志里**不该有报错**（这是预期行为）；
- 装一个光影包再试一次，效果应该仍在（`enable_custom_effects_with_shader_pack` 默认为真）；
- 用 Timeline 的 Post Process Clip 做同样的效果，确认两者**不要同时存在**——否则你会看到双倍的强度。 

## 8.9 着色器图与自定义着色器

### 8.9.1 先把三件事分开：材质、着色器、GPU 数据

新手最容易在这里绕晕，因为「粒子长什么样」其实由三个独立的东西共同决定：

| 东西 | 回答的问题 | 载体 |
|---|---|---|
| 材质（Material） | 用哪张贴图、怎么混合、什么渲染状态 | 材质资源（Inspector 的 Materials 列表） |
| 着色器（Shader / Shader Graph） | 每个像素**算什么**颜色 | Shader Graph 资源，或手写的 Core Shader JSON/VSH/FSH |
| GPU 数据（Additional / Custom Data） | 每个粒子**不一样的数值**从哪来 | 发射器的 Additional GPU Data 设置 + Shader 里的读取 |

选哪种方案，照这张表对号入座（整理自官方 `shaders-and-gpu` 一页）：

| 你的需求 | 用什么 |
|---|---|
| 只要贴图、混合、深度，普通粒子 | Texture 或 Sprite Material |
| 逐像素逻辑，但不想写 GLSL | Shader Graph Material |
| 已有 Core Shader JSON/VSH/FSH | Custom Shader Material |
| 处理 Scene Color 或整屏 | Fullscreen Graph + Render Graph（见 §8.8） |
| Shader 里要逐粒子的动画量 | Additional GPU Data |
| 数值由 Timeline 或 Java 控制 | Custom Data Stream |

**给新效果的默认建议：优先用 Shader Graph。** 它会自己声明需要的数据流，并自动适配 Photon 的不同渲染路径
（CPU 粒子 / GPU 实例化的 Tile 与 Model / Trail / Beam / AraTrail）。手写 Shader 的适用场景是"移植现成代码"
或"Graph 暂时表达不了的算法"——代价是那些适配要你自己维护。

> 术语对照：着色器图（Shader Graph）、函数图（Function Graph）、子图（Subgraph）、
> 黑板参数（Blackboard Parameter）、顶点阶段（Vertex Stage）、片元阶段（Fragment Stage）、
> 顶点格式（Vertex Format）、实例化（Instancing）。

### 8.9.2 从零做一个 Shader Graph（照着做）

需求：做一个带纹理、并且在与方块交界处柔和消隐的粒子。做法对照官方
`shader-graph-getting-started` 一页，这里按"每一步在解决什么"重写。

**第一步：建 Graph 和材质**

1. Resources 里新建一个 Shader Graph，命名 `soft_particle`；
2. 再新建一个 **Shader Graph Material**；
3. 把材质的 Graph 资源指向 `soft_particle`；
4. 把这个材质赋给粒子发射器的 Renderer。

这里有个**必须理解的设计**：**Graph 和材质是两个资源**。多个材质可以复用同一张 Graph，
各自只覆盖贴图、颜色或数值参数。把 Graph 想成"函数"，材质想成"一次调用 + 实际参数"。

**第二步：接出 Fragment 输出**

```text
Particle Data.uv ──> Sample Texture.uv
Texture 参数 ──────> Sample Texture.texture
Sample Texture.rgb × Color 参数.rgb ─> Base Color
Sample Texture.a   × Color 参数.a   ─> Alpha
```

连到 Fragment Output 之后**保存一次**，Photon 会编译 Graph 并刷新材质预览。

**第三步：加柔和交界**

加一个 **Depth Fade** 节点，把它乘到 Alpha 上。它比较"粒子自己的深度"和"场景深度"，
让大尺寸的 Billboard 穿过方块时淡出，而不是出现一条生硬的分割线。

> 坑：Depth Fade 是 **Fragment Stage 的屏幕空间计算**。不要把 `Particle Data` 的 UV 接到 `Screen UV` 上——
> 那会让贴图跟着屏幕动，而不是跟着粒子动。

**第四步：把该暴露的参数暴露出来**

把 `Texture`、`Tint`、`Fade Distance` 设成 Graph 参数，在 Graph 里给默认值，在**材质**里覆盖。
参数名会被序列化——**重命名之后要同步改所有引用旧名字的材质**，否则你会看到一个"参数消失了"的材质。

**第五步：验证渲染路径**（这一步最容易被跳过，然后上线才发现问题）

- Alpha 混合与 Additive 混合都试一遍；
- 普通 Tile 与 GPU Instanced Tile 都试一遍（两者走的是不同的着色器变体）；
- 与方块几何的交界处看一遍；
- 如果项目支持 Iris，开 Iris 再测一遍；
- 对大于 1 的颜色测 HDR / Bloom。

**全黑时的二分法**：先把一个常量颜色直接接到 Base Color。如果这样能看到粒子，说明管线是通的，
问题出在贴图或参数；再把 Texture、Particle Color、Lighting 逐个接回去，很快就能定位。

**保存顺序**：先存 Graph，再存材质，最后存项目。FX Pack 会跟着资源引用收集依赖——
Graph、材质、纹理都必须有可解析的资源路径。

### 8.9.3 Stage、Function 与 Subgraph：把复杂图拆开

Shader Graph 最终会被编译成 Minecraft 的一个 Render Pass。**先想清楚你的表达式在哪个 Stage 执行**，
能避开大部分的编译错误和插值问题。

| Stage | 执行频率 | 适合算什么 |
|---|---|---|
| Vertex（顶点） | 每个输入顶点一次 | 位置形变、读取 Instance Data、较粗粒度的计算 |
| Fragment（片元） | 每个覆盖到的像素一次 | 纹理/颜色、Alpha/Discard、法线/雾/光照、Depth Fade |

**Additional Data 与 Custom Data 是在 Vertex Stage 读的**。如果某个 Fragment 节点需要它，
编译器会**自动创建 Varying**——所以你不需要手写 `out` / `in`。这一点和手写 Shader 完全不同（见 §8.9.7）。

**输出口的含义**：

| 输出 | 作用 |
|---|---|
| Position | 修改最终顶点位置（连之前必须明确坐标空间） |
| Base Color | 普通的着色颜色 |
| Emission | 增加不受光照影响的 HDR 能量（发光感主要靠它） |
| Alpha | 控制透明度与混合 |
| Discard / Alpha Clip | 直接丢弃不满足条件的像素 |

**一条纪律**：不要用 Discard 去代替正确的 Blend / Depth 状态。Cutout 可以写深度，
半透明边缘一般**不应该**写深度——否则你会看到背景被切掉一块。

**Function Graph（函数图）** 用来封装可复用的计算：声明有类型的输入输出、保存成资源，
然后在粒子 Graph 里实例化。适合 UV 扭曲、调色板映射、Dissolve Mask、共享光照模型这类东西。

两点限制：Function Graph **不保存材质的渲染状态**（贴图/Uniform 参数仍然由调用的 Graph 或材质持有）；
并且**不要形成递归引用**，Photon 会把它报成编译错误。

**常见报错对照**：

| 报错 / 表现 | 先查什么 |
|---|---|
| Type mismatch | 报错端口两侧的 Vector 宽度与隐式转换 |
| 缺少必须输出 | Fragment Output 是否接上、Graph 类型是否选对 |
| 未知 Function / Resource | Function Graph 的路径，以及 FX Pack 依赖有没有收进去 |
| 编译成功但数据全是 0 | Additional Data 不受支持，或者正在走 CPU 路径 |
| 改完还是旧结果 | 保存 Graph → Reload Shader → 清 Photon FX Cache |

### 8.9.4 核心节点参考（用的时候翻这一小节）

Photon 的 Shader Graph 用的是 KilaGraph 那套通用节点。节点上的 Tooltip 与 Description Panel
会给出**准确**的端口和类型——文档只给分类，别背端口的默认值。

| 分类 | 常用节点 | 干什么用 |
|---|---|---|
| 基础数学 | Add、Subtract、Multiply、Divide、Min、Max | 组合 Mask 和数值 |
| 高级数学 | Power、Exp、Log、Sqrt、Reciprocal | 衰减与响应曲线 |
| 范围 | Clamp、Saturate、Remap、Smoothstep、Step | 归一化并塑造过渡 |
| 三角函数 | Sin、Cos、Tan、Atan2 | 波形、旋转、极坐标 |
| 逻辑 | Compare、Select、And、Or、Not | 不用 Java 分支的条件 Mask |

三条实用经验：

1. **送进 Alpha 的算术结果建议过一遍 Saturate**，免得出现负值或大于 1 的 Alpha 造成怪异混合；
2. **`Smoothstep(edge0, edge1, x)` 是生成稳定柔边的首选**；把两个 Edge 交换就得到反向渐变；
3. **Mask 尽量保持标量**，不要让整条计算链都携带 `vec4`——省下来的不只是寄存器，还有你的脑容量。

**向量与矩阵**：`Compose`/`Split`/`Swizzle` 组合与拆解通道；`Dot` 测方向一致程度；`Cross` 求垂直方向；
`Length`/`Distance`/`Normalize` 用于方向与径向 Mask；`Matrix Multiply`/`Transform` 转换坐标空间
（点和方向的处理不同：**平移不应该影响方向**）。

**UV 与程序化图案**：UV 节点有 Tiling/Offset、Rotate、Polar/Twirl、Flipbook、Screen Mapping；
程序化节点能生成 Noise、Checker、Ellipse、Rectangle、Gradient、Voronoi 类 Mask。
注意：**程序化 Noise 会为每个顶点/像素重算**——图案不需要随时间变化时，用一张贴图通常更便宜。

**法线、雾、光照**：Normal 节点负责解包与转换法线贴图；Lighting 使用世界法线、光源和视线方向；
Fog 节点与 Minecraft 的雾混合。
**一个高频错误**：Photon 的 `Particle Data.litColor` **已经包含**方块光与天空光，而原始 `color` 没有。
除非你故意想双重光照，否则不要把它们再乘一遍。

**四个可以直接抄的小结构**：

```text
Dissolve:     noise - threshold -> Smoothstep -> Alpha
Rim:          1 - Saturate(Dot(normal, viewDirection)) -> Power -> Emission
Flipbook:     ParticleData.uv + frame/tiles -> Sample Texture
Soft particle: texture alpha × Depth Fade -> Alpha
```

### 8.9.5 Photon 节点参考

这些是 Photon 自己加进图里的节点：它们把通用图编译器接到"粒子实例""场景缓冲""全屏 Pass"上。

| 节点 | 输出 | 在哪个 Graph / Stage 可用 |
|---|---|---|
| Particle Data | position、color、litColor、uv、normal | 粒子 / Function Graph；两个 Stage 都行 |
| Additional Data | 所选 Scalar 或 vec3 Channel | Vertex 读取并自动 Varying；不支持时为 0 |
| Custom Data | 所选 vec4 Stream | Vertex 读取并自动 Varying；Preview/CPU 路径为 0 |
| Viewport | Viewport Size 与屏幕信息 | 屏幕相关计算 |
| Depth Fade | 交界淡出 | Fragment；需要 Scene Depth |
| World to Screen UV | 世界坐标对应的屏幕 UV | 坐标转换 |
| Screen to World | 重建出的世界坐标 | Fragment；需要 Depth / Camera Matrix |

关于 `Particle Data` 的三个字段，值得单独记一下：

- `position` 是**插值后的、相机相对的世界位置**（和 §6.2 的结论一致）；
- `color` 是编写/运行时的粒子颜色；
- `litColor` 已经乘过烘焙光照贴图——这就是上面说的"别再乘一次"的那个。

Trail 的 UV 通常沿**长度轴**走，和 Sprite Frame 的含义不同。

**Additional Data 的下拉列表直接来自 `PhotonGpuChannels` 注册表**：选中某个 Channel 时，编译器会把它标记为
**必需数据**，Shader Graph 的 Instancing 会自动上传，**不需要你在 Inspector 里勾选**。
（手写材质反过来——必须手动开，见 §8.9.9。）

**全屏图专用节点**：Fullscreen Position、Fullscreen Output、Texel Size、Scene Color/Texture Input、Scene Depth。
它们**不能**放进粒子 Shader Graph；反过来 `Particle Data` 在全屏图里也没有意义。

**屏幕空间的三条准确性要求**（官方专门列了出来，都是血泪）：

1. Photon 节点会自己处理 UV 原点和深度约定，**不要手写固定的翻转**；
2. 采样邻近像素要用 Texel Size，**不要写死 `1/1920`**；
3. 除非节点明确说了已经线性化，**深度是非线性的**。

Iris 下 Scene/Depth Buffer 是否可用取决于具体管线——**必须测试"没有 Buffer 时的安全输出"**，
否则玩家装了光影就会看到黑屏。

> 自 2.2.2 起，Photon 节点在编辑器里会说明端口、Stage 限制、Channel 类型和对应 GLSL。
> **连线之前先选中节点打开 Description Panel**，比事后猜端口快得多。

### 8.9.6 Custom Shader Material：什么时候该用它

Custom Shader Material 直接加载 Minecraft Core Shader 的 **JSON + VSH + FSH**。
它不是被 Shader Graph 淘汰的旧功能——要移植 GLSL、用几何着色器、或写 Graph 还没覆盖的算法时就用它。

**文件放哪**：

```text
assets/<namespace>/shaders/core/<name>.json
assets/<namespace>/shaders/core/<name>.vsh
assets/<namespace>/shaders/core/<name>.fsh
assets/<namespace>/shaders/include/<name>.glsl     # 可选，公共函数
```

材质里填的 **Shader ID 是 `<namespace>:<name>`**——不含 `assets/`、`shaders/core/` 和 `.json`。

```text
assets/wiki/shaders/core/dissolve_particle.json   →   wiki:dissolve_particle
```

即使是拖文件选择器，文件也必须位于某个 `assets/<namespace>/shaders/core/` 下，Photon 才能推导出 Shader ID。

**在编辑器里用**：Emitter 的 Renderer > Materials 加一个 Custom Shader Material → Select Shader
（或直接填 ID）→ 编译成功后展开 Shader Settings → 给自定义 Texture/数字/向量/颜色填值 →
改完 JSON/VSH/FSH 点 **Reload Shader**。预览里出现红色错误文字时**先修编译错误再重载**：
Photon 会缓存失败状态，不会每帧重试创建失败的 GL Program。

**Inspector 怎么识别参数**（决定你起的名字会变成什么控件）：

| JSON 声明 | Inspector 控件 |
|---|---|
| 自定义 sampler（如 `Texture`、`NoiseTexture`） | 纹理选择与预览 |
| `int`，count 1–4 | 整数或整数向量 |
| `float`，count 1–4 | 浮点数或浮点向量 |
| `vec3` 且名称含 `color` / `rgb` | RGB 颜色 |
| `vec4` 且名称含 `color` / `rgba` | RGBA 颜色 |
| `vec4` 且名称含 `hdr` / `emission` | HDR 颜色与强度 |

**保留前缀（必须记住）**：所有以 `Sampler` 开头的 sampler、所有以 `U_` 开头的 uniform 都被视为**内置项**，
不会出现在 Shader Settings 里。自定义参数**不要用这两个前缀**——用了既不绑定也不显示，还很难查。

参数值随材质保存；重载 Shader 时只要名称和类型仍然匹配，Photon 会尽量保留原值；
**删除或改名的参数需要重新配置**。

**和 §2/§3 的 `RenderPipeline` 体系怎么接？** 这一条很容易被误解，说清楚：

- Photon 自己**不**往 `RegisterRenderPipelinesEvent` 里注册你的材质。你在 JSON 里声明的 Shader
  会被 Photon 的材质/渲染状态体系消化成它自己的管线（核心是 `IMaterial.getRenderType(...)`
  配合 `BlendMode.toBlendFunction()`），由 Photon 的批处理统一画。
- 所以**"我想让粒子用我自己的渲染管线"和"我想自己画一批几何"是两件事**。前者走 Photon 材质（本节）；
  后者走 Blaze3D 的管线层（§2.4、§3.4 的 `RenderPipeline` + `RegisterRenderPipelinesEvent`）。
- 26.2 的 Photon 里，1.21 时代那个 `PhotonFXRenderPass` **现在是 M0 桩**，批处理的实际承担者换成了
  `PhotonWorldRenderState` 里的渲染状态批次。读老资料时看到"往 PhotonFXRenderPass 里塞东西"的写法，
  要按"已经变了"来理解。

### 8.9.7 手写 Core Shader：一个完整的最小例子

这个例子读取粒子顶点、光照贴图和一张由 Inspector 指定的纹理，并暴露 `TintColor` 与 `DiscardThreshold`。

**第 1 步：JSON**（`assets/wiki/shaders/core/wiki_particle.json`）

```json
{
  "vertex": "wiki:wiki_particle",
  "fragment": "wiki:wiki_particle",
  "samplers": [
    { "name": "Sampler2" },
    { "name": "Texture" }
  ],
  "uniforms": [
    { "name": "ModelViewMat", "type": "matrix4x4", "count": 16,
      "values": [1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1] },
    { "name": "ProjMat", "type": "matrix4x4", "count": 16,
      "values": [1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1] },
    { "name": "FogStart", "type": "float", "count": 1, "values": [0] },
    { "name": "FogEnd", "type": "float", "count": 1, "values": [1] },
    { "name": "FogColor", "type": "float", "count": 4, "values": [0, 0, 0, 0] },
    { "name": "FogShape", "type": "int", "count": 1, "values": [0] },
    { "name": "TintColor", "type": "float", "count": 4, "values": [1, 1, 1, 1] },
    { "name": "DiscardThreshold", "type": "float", "count": 1, "values": [0.01] }
  ]
}
```

`Sampler2` 是**内置光照贴图**（保留前缀）；`Texture` 不带保留前缀，所以会出现在 Shader Settings 里供你选图。

**第 2 步：顶点着色器**（`.../wiki_particle.vsh`）

```glsl
#version 330 core

#moj_import <photon:particle.glsl>
#moj_import <minecraft:fog.glsl>

uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out float vertexDistance;
out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    ParticleData data = getParticleData();

    gl_Position = ProjMat * ModelViewMat * vec4(data.Position, 1.0);
    vertexDistance = fog_distance(data.Position, FogShape);
    texCoord0 = data.UV;
    vertexColor = data.Color * texelFetch(Sampler2, data.LightUV / 16, 0);
}
```

**为什么要用 `getParticleData()`**：`photon:particle.glsl` 把 CPU 粒子、Tile/Model 的 GPU 实例化、Trail、Beam、
AraTrail **统一**成同一个输入结构。不要直接假设原版的 `Position`、`UV0` 这些 Attribute 一定存在——
在 GPU 实例化路径下它们本来就不存在。

```glsl
struct ParticleData {
    vec3 Position;
    vec4 Color;
    vec2 UV;
    ivec2 LightUV;
    vec3 Normal;
    vec3 ObjectPosition;
    vec3 ObjectNormal;
};
```

因为 Instancing 和 Buffer Texture Accessor 用到 GLSL 330 的功能，**使用 Photon Helper 的顶点着色器要声明
`#version 330 core`**（片元着色器用 150 也可以，但两者用到的 varying 必须匹配）。

**第 3 步：片元着色器**（`.../wiki_particle.fsh`）

```glsl
#version 150

#moj_import <photon:particle_utils.glsl>

uniform sampler2D Texture;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform vec4 TintColor;
uniform float DiscardThreshold;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = texture(Texture, texCoord0) * vertexColor * TintColor;
    if (color.a < DiscardThreshold) {
        discard;
    }
    fragColor = apply_fog(color, vertexDistance, FogStart, FogEnd, FogColor, FogShape);
}
```

**第 4 步：在材质里填 Shader ID** `wiki:wiki_particle`，为 `Texture` 选一张 PNG，调 `TintColor` 与
`DiscardThreshold`。三个文件里声明的名称、类型、以及 Vertex/Fragment 的 varying **必须完全一致**。

**第 5 步：认识 Renderer Variant**。Photon 会按**实际绘制方式**重新编译同一个 Shader，并注入一个 Define：

| Define | 使用场景 |
|---|---|
| （无） | CPU 路径与材质预览 |
| `PARTICLE_INSTANCE` | Tile 粒子 GPU 实例化 |
| `PARTICLE_MODEL_INSTANCE` | Model 粒子 GPU 实例化 |
| `TRAIL_INSTANCE` | Particle Trail |
| `BEAM_INSTANCE` | Beam |
| `ARA_TRAIL_INSTANCE` | 扁平 AraTrail |
| `ARA_TRAIL_TUBE_INSTANCE` | 管状 AraTrail |

`getParticleData()` 会处理这些差异。**如果你绕过 Helper 自己声明 Attribute，就必须为每一个目标 Define
写一份匹配的布局**——这就是"为什么我用 Helper 好好的，自己写就崩了"的答案。

**几何着色器（ExtendedShader）**：在 JSON 里加一行 `geometry`，对应的 `.../wiki_particle.gsh` 会在
Program Link 前被附加进来：

```json
{
  "vertex": "wiki:wiki_particle",
  "geometry": "wiki:wiki_particle",
  "fragment": "wiki:wiki_particle"
}
```

只有在**确实需要改变或生成图元**时才用它。它**不会**自动适配不同 Renderer 的输入——
顶点着色器仍然要先产出正确的数据。

**两个现成的 Include**：

- `#moj_import <photon:particle.glsl>`：顶点布局与 `ParticleData`；
- `#moj_import <photon:particle_utils.glsl>`：`getCurveValue()` 与 `getGradientValue()`；
- `#moj_import <namespace:file.glsl>`：加载 `assets/<namespace>/shaders/include/file.glsl`。

**编译问题排查顺序**（按出现频率排）：

1. JSON 是不是合法 JSON（不能有注释、不能有尾随逗号）；
2. Shader ID 与 JSON 里的 Vertex/Fragment/Geometry **都不带扩展名**；
3. 打开 `latest.log`，找**最早出现**的那条 JSON / Include / Compile / Link 错误（后面的都是连带的）；
4. 让片元先固定输出 `vec4(1, 0, 1, 1)`，再逐项恢复纹理、varying 和计算；
5. 改完文件点 **Reload Shader**；如果改的是资源包本身，再执行一次 Minecraft 的资源重载。

### 8.9.8 内置 Uniform 与 Sampler 查询表

名称**区分大小写**，而且必须同时出现在 Shader JSON 和真正使用它的 GLSL Stage 里；
没被 GLSL 实际用到的项可能在编译时被优化掉（这属于正常现象，不是配置丢了）。

**以 `Sampler` 开头的内置 Sampler**（不会出现在 Shader Settings 里）：

| 名称 | 数据来源 | 说明 |
|---|---|---|
| `Sampler0` | RenderSystem 纹理槽 0 | 常用主纹理槽；**只有当前 Render Pass 明确设置了槽 0 时才可靠**。要自己选纹理请用自定义 sampler |
| `Sampler2` | Minecraft 光照贴图 | 用 `texelFetch(Sampler2, lightUV / 16, 0)` 读方块光与天空光 |
| `SamplerBlockAtlas` | Minecraft 方块图集 | Photon 加载材质时绑定方块图集；要配合**图集 UV**使用 |
| `SamplerSceneColor` | 当前 Photon Render Pass 的场景颜色 | 只在有效的 RenderPassPipeline 里可用 |
| `SamplerSceneDepth` | 同一场景的深度 | 深度是非线性的设备深度；重建位置要同时用逆矩阵和视口 |
| `SamplerCurve` | 材质的 Curve Texture | 最多 128 条曲线，每条 128 个采样 |
| `SamplerGradient` | 材质的 Gradient Texture | 最多 128 条渐变，每条 128 个采样 |

两条容易误解的：**`SamplerCurve` / `SamplerGradient` 的数据是在材质面板里编辑的**，不是普通 PNG；
然后 **Scene sampler 是"当前绘制管线"的快照**，不保证包含更晚绘制的透明物体，
所以材质预览或没有活动的 Photon Render Pass 时**拿不到有效的场景纹理**——这是预期行为。

Curve 与 Gradient 的用法：

```glsl
#moj_import <photon:particle_utils.glsl>

uniform sampler2D SamplerCurve;
uniform sampler2D SamplerGradient;

float strength = getCurveValue(SamplerCurve, 0, age01);     // 行号 0..127，第三参是归一化横坐标
vec4  color    = getGradientValue(SamplerGradient, 0, age01);
```

**Photon 动态 Uniform**（每次 Apply 都会更新）：

| 名称 | 类型 | 值 |
|---|---|---|
| `U_CameraPosition` | vec3 | 当前 Photon 相机的世界坐标 |
| `U_InverseProjectionMatrix` | mat4 | 当前投影矩阵的逆 |
| `U_InverseViewMatrix` | mat4 | 当前 ModelView 矩阵的逆 |
| `U_ViewPort` | vec4 | OpenGL Viewport 的 (x, y, width, height) |

正如 §8.9.6 强调的：**`U_` 是保留前缀，而且只会更新上表这四个名字**。
自定义 uniform 以 `U_` 开头的话，既不会自动绑定，也不会出现在 Inspector 里——两头不落。

从片元坐标算屏幕 UV 的标准写法：

```glsl
uniform vec4 U_ViewPort;
vec2 screenUV = (gl_FragCoord.xy - U_ViewPort.xy) / U_ViewPort.zw;
```

**ShaderInstance 认得的 Minecraft 内置 Uniform**（只声明你真正需要的）：`ModelViewMat`、`ProjMat`、
`TextureMat`、`ScreenSize`、`ColorModulator`、`Light0_Direction`、`Light1_Direction`、`FogStart`、`FogEnd`、
`FogColor`、`FogShape`、`GameTime`、`GlintAlpha`、`LineWidth`、`ChunkOffset`。

注意最后一句限定：**不同的 Minecraft 渲染路径不一定为每个值提供有意义的状态**。
粒子材质里常用的是矩阵、雾、颜色、Screen Size 和 Game Time。

**自定义 sampler / uniform**：不在上面列表里的参数由 Inspector 管理，例如

```json
"samplers": [ { "name": "NoiseTexture" } ],
"uniforms": [
  { "name": "NoiseScale", "type": "float", "count": 1, "values": [4] },
  { "name": "EmissionColor", "type": "float", "count": 4, "values": [1, 0.4, 0.1, 1] }
]
```

`NoiseTexture` 变成纹理选择项，`NoiseScale` 是数字，`EmissionColor` 因为名字里含 `emission` 会变成 HDR 颜色控件。
自定义值会复制到同一材质的所有 Renderer Define Variant，并随 FX 保存。

### 8.9.9 GPU 数据：让每个粒子不一样

这是让 Shader 真正"活起来"的东西：**把模拟产生的逐粒子数值直接喂给材质**，不需要 CPU 每帧重建几何。
它由两部分组成：

1. **内置 Channel**：Normalized Age、Velocity、Emitter Position、Trail Point Life、Beam Direction 等；
2. **最多 4 条 Custom Data Stream**：每条按 `vec4` 采样，并且可以在运行时覆盖。

**内置 Channel 支持表**（`✓` 表示该渲染器支持，不支持的组合返回 0）：

| Channel | 类型 | Particle | Trail / AraTrail | Beam |
|---|---|---|---|---|
| random | float | ✓ | ✓ | ✓ |
| `t`（归一化 Age） | float | ✓ | ✓ | ✓ |
| age / lifetime | float | ✓ | — | — |
| position / velocity | vec3 | ✓ | — | — |
| isCollided | float | ✓ | — | — |
| emitter t / age | float | ✓ | ✓ | ✓ |
| emitter position / velocity | vec3 | ✓ | ✓ | ✓ |
| point t / point life | float | — | ✓ | — |
| beam direction / length | vec3 / float | — | — | ✓ |

两个细节：**`point t` 会沿 Trail 段插值**；**Beam Direction 等于 `end - start`**，
只要你想要的只是方向就记得 `normalize`。

**在 Shader Graph 里读内置 Channel**：加一个 Additional Data 节点并选 Channel。
编译器会**自动启用** Graph 实际读到的 Channel，不需要手动勾选（这点和手写材质相反）。
数据在 Vertex Stage 从固定的 `PhotonData` Record 读取，Fragment 用到时会自动经过 Varying。
非实例化的 CPU 路径与节点预览返回 0。

**配置 Custom Data Stream** 的步骤：

1. 在 Emitter Inspector 里启用 Additional GPU Data Setting；
2. 在 Custom Data 列表里加 Stream，最多 4 条；
3. 选 Stream 类型、Time Source，以及各 Channel 的 Function。

**Stream Index 就是列表顺序（从 0 开始）**，Shader 按 Index 读。给 Channel 改显示名**不会**改变 GPU 布局，
但**移动或删除 Stream 会改变后面所有 Index**——这时 GLSL 里的 Index 也得跟着改。

| Stream 类型 | Inspector 内容 | GPU 值 |
|---|---|---|
| Vector | 1–4 条独立的 Number Function，可分别命名 | `vec4(x, y, z, w)`，未使用的分量为 0 |
| Color | 一条 HDR Color/Gradient Function | `vec4(r, g, b, a)`，HDR 的 RGB 可以大于 1 |

**Time Source 决定采样用的 x 是什么**：

| Source | 采样 x | 可用 Emitter |
|---|---|---|
| Self | Particle/Beam 自身的归一化生命周期；Trail/AraTrail 段的归一化生命 | 全部 |
| Emitter | 所属 Emitter 的归一化时间 | 全部 |
| Length | 当前点在 Trail 长度方向的位置 | Trail、AraTrail |

**同一条 Stream 的所有 Channel 共用同一个 Time Source。** 另外，Random Function 使用"每个粒子、每条 Stream
独立且稳定"的 Random Key——所以 Buffer 每帧重传也**不会**让随机值闪来闪去。

**在 Shader Graph 里读 Custom Data**：加 Custom Data 节点，把 Index 设成 emitter 里的 Stream Index（0..3），
节点**永远输出一个 `vec4`**。Vector Stream 用 Split 取 `x/y/z/w`；Color Stream 可以直接连颜色或 Emission。

```text
Custom Data (Index 0)
        │
      Split
      ├─ x ──> Alpha / Dissolve Threshold
      └─ y ──> UV Offset Strength
```

节点在 Vertex Stage 调用 `photon_custom_data(index)`；Fragment 用到输出时编译器自动建 Varying。
Compiler 也会标记该材质需要 Custom Data，Renderer 随后为每个 Instance 上传固定 4 个 RGBA32F Texel 的
`PhotonCustomData` Record。

**以下情况会输出 `vec4(0)`**（所以**别只看节点预览**，要在真正使用的 GPU 实例化粒子 / Trail / Beam / AraTrail 上验证）：
节点预览、非实例化的 CPU 渲染、没启用 Additional GPU Data、Emitter 没有对应 Stream、Index 大于 3
（负数会被夹成 Stream 0）。

**在 Custom Shader Material 里读 Custom Data**：可以读，但**接口完全不同**。
手写材质走的是传统 **Instance Attribute** 路径——每条 Custom Data Stream 是一个追加的 `vec4` Attribute。

> **不要在手写材质里调用 `photon_custom_data()`。** 它读的是 `PhotonCustomData` 这个 Buffer Texture，
> 而这个 Buffer 只在"同一个 Pass 里确实有 Shader Graph 材质用了 Custom Data"时才会上传，
> 对手写材质不是一个稳定的接口。手写材质应当声明 Attribute。

光是"把逐粒子数据送进 Shader"这一件事，Photon 里就有**两条完全不同的数据通路**，混用是常见的翻车点：

| 通路 | 谁在用 | 位置与布局 |
|---|---|---|
| PhotonData 记录（Buffer Texture，GLSL 里的 `photon_custom_data(i)`） | Shader Graph 材质 | 通道按注册表顺序**固定打包**进 vec4 槽位，**和"启用了哪些通道"无关** |
| 顶点属性尾部（legacy） | 手写 Custom Shader Material | **每个被勾选的通道各占一个 Location**，位置随启用集合变化 |

本机源码把这两条路写得很清楚（`AdditionalGPUDataSetting` 的类注释）：记录路径"一个编译好的 Shader
可以服务任意启用集合"（`PhotonGpuChannels` 的类注释），而顶点属性路径是"每个启用的通道一个属性，
从该 kind 的 base location 开始按注册表顺序排，Custom Data 的 vec4 再接在后面"（`planAttribs`）。

这就解释了两个看起来矛盾的现象：**Shader Graph 里"勾不勾选开关都不影响结果"**，
而**手写材质里"多勾一个开关就花屏"**。下面这张表只适用于**手写（顶点属性）**这条路。

**Attribute Location 的计算**（这是手写路线的核心难点）：如果没有手动启用任何内置 Channel，
Stream 0 的位置是——

| Renderer Define | Stream 0 的 Location |
|---|---|
| `PARTICLE_INSTANCE` | 8 |
| `PARTICLE_MODEL_INSTANCE` | 9 |
| `TRAIL_INSTANCE` | 3 |
| `ARA_TRAIL_INSTANCE` / `ARA_TRAIL_TUBE_INSTANCE` | 3 |
| `BEAM_INSTANCE` | 6 |

手动启用的每一个"可上传"内置 Channel 都会**占用一个 Attribute Location**：

```text
customLocation = rendererBaseLocation
               + enabledUploadableBuiltinChannelCount
               + streamIndex
```

内置 Channel 按支持表里的注册顺序排列。**这意味着：在 Inspector 里多勾一个开关，就可能把所有 Custom Stream
的 Location 往后推一位**——所以发射器设置和 GLSL 必须一起维护。这就是"为什么我加了个勾就花屏了"的答案。
（Beam Direction/Length 是派生值，不占 Attribute。）

对应的顶点着色器骨架（假设没启用内置 Channel，读 Stream 0）：

```glsl
#version 330 core
#moj_import <photon:particle.glsl>

#if defined(PARTICLE_INSTANCE)
layout(location = 8) in vec4 iCustom0;
#define HAS_CUSTOM0
#elif defined(PARTICLE_MODEL_INSTANCE)
layout(location = 9) in vec4 iCustom0;
#define HAS_CUSTOM0
#elif defined(TRAIL_INSTANCE) || defined(ARA_TRAIL_INSTANCE) \
   || defined(ARA_TRAIL_TUBE_INSTANCE)
layout(location = 3) in vec4 iCustom0;
#define HAS_CUSTOM0
#elif defined(BEAM_INSTANCE)
layout(location = 6) in vec4 iCustom0;
#define HAS_CUSTOM0
#endif

out vec4 custom0;

void main() {
    ParticleData particle = getParticleData();
    // ...计算 gl_Position 与其它 varying...
#ifdef HAS_CUSTOM0
    custom0 = iCustom0;
#else
    custom0 = vec4(0.0);        // 材质预览与非实例化 CPU 路径
#endif
}
```

片元侧用一个同名 varying 接过去就行。**GLSL 声明了 Stream，Emitter 也必须真的创建了这个 Stream 并启用
Additional GPU Data**，否则你读到的永远是 0。

**运行时覆盖（Java）**：四种 Emitter Runtime 都暴露 `customData`，可以在不改共享资产的前提下覆盖单条 Stream/Channel：

```java
ParticleEmitter sparks = (ParticleEmitter) runtime.findObject("sparks");

// Stream 0、Channel 0（即 x / r）。RuntimeValue 接受 authored function 类型。
sparks.runtime().customData.slot(0, 0).set(new Constant(0.85f));

// 恢复编辑器里写的值
sparks.runtime().customData.slot(0, 0).clear();
```

Trail、Beam、AraTrail 同样用 `runtime().customData.slot(stream, channel)`；
Color Stream 暴露 Color Binding，Scalar/Vector Stream 暴露对应分量的 Binding。

> **和 Timeline 抢同一个槽**：Timeline Animation 与 Java 写同一个 `RuntimeValue` 槽时，
> **最后一次写入生效**。Timeline Clip 只要每帧都在驱动该槽，Java 的一次性 `set()` 看起来就会被忽略。
> 解法：换一条 Stream、在 Timeline 求值之后更新，或者让**只有一方**拥有这个值。

**性能**：Shader Graph 只上传"编译后的 Pass 真的用到"的 Channel；只有 Graph 读了 Custom Data 才会创建
Custom Stream Buffer。旧式手写 Shader 依赖手动 Toggle，并上传你声明的 Attribute。
实践建议：**让大量粒子共享少量紧凑的 Stream**，不要为了"每个粒子一个数值"去创建大量无法合批的独立材质——
后者会把 Draw Call 打散，比多传几个 float 贵得多。

### 8.9.10 案例：随生命期渐变的发光 + 自定义槽位控制强度

**目标**：粒子从出生到消亡，颜色从亮橙渐变到暗红，并且**亮度上限由 Java 按当前元素类型动态给**——
火元素 1.0，其他元素 0.4。要求 GPU 实例化路径（大量粒子）下也成立。

**数据设计**（先想清楚"哪个量从哪儿来"）：

| 量 | 来源 | 为什么 |
|---|---|---|
| 生命期进度 `t` | 内置 Channel `t`（归一化 Age） | 每帧自动更新，不用 Java 管 |
| 基础颜色曲线 | 材质里的 Gradient（`SamplerGradient`） | 美术在材质面板里调，改色不用改代码 |
| 亮度上限 | **Custom Data Stream 0 的 x** | 由 Java 每帧按元素类型覆盖 |

**路线 A：Shader Graph（推荐）**

```text
Additional Data (t) ─┬─> Gradient(age) ─> Base Color
                     └─> Emission 乘子
Custom Data (Index 0) ─> Split.x ──> Multiply ──> Emission
Base Color.rgb ─────────────────────> Add ──────> Emission
```

1. Emitter Inspector 启用 **Additional GPU Data Setting**，加 **1 条 Vector Stream**（Stream 0），
   Channel 0（x）用一个常量 Function，默认 `1.0`；
2. Graph 里 `Additional Data` 选 `t`，接进 Gradient 得到基础色；
3. `Custom Data` 节点 Index 设 0，`Split` 取 `x`，乘到 Emission 上；
4. **验证顺序**：先在 GPU 实例化粒子上看（节点预览永远是 0，看了会误判）。

**路线 B：手写 Custom Shader Material**

顶点着色器读 Stream 0 的 Attribute 并转成 varying（按 §8.9.9 的 Location 公式。
这里假设**没有**启用内置 Channel，而且粒子走 `PARTICLE_INSTANCE`，所以 Location = 8）：

```glsl
#version 330 core
#moj_import <photon:particle.glsl>

#if defined(PARTICLE_INSTANCE)
layout(location = 8) in vec4 iCustom0;
#else
const vec4 iCustom0 = vec4(0.0);        // 其它变体 / 预览：读不到就是 0
#endif

out vec4 custom0;

void main() {
    ParticleData data = getParticleData();
    gl_Position = ProjMat * ModelViewMat * vec4(data.Position, 1.0);
    custom0 = iCustom0;
}
```

片元着色器用**材质自带**的 Curve/Gradient 做生命期渐变，再乘上强度上限：

```glsl
#version 150
#moj_import <photon:particle_utils.glsl>

uniform sampler2D SamplerGradient;      // 材质面板里编辑，不是 PNG
uniform float DiscardThreshold;

in float age01;                          // 由顶点着色器传下来的归一化生命期
in vec4  custom0;                        // Stream 0：x = Java 给的强度上限
in vec4  vertexColor;

out vec4 fragColor;

void main() {
    vec4 base   = getGradientValue(SamplerGradient, 0, age01);
    float limit = max(custom0.x, 0.0);
    vec4 color  = base * vertexColor;
    if (color.a < DiscardThreshold) {
        discard;
    }
    // Emission 用 HDR：亮度超过 1 才会被 bloom 抓到
    fragColor = vec4(color.rgb * limit, color.a);
}
```

Java 侧每帧（或元素类型变化时）覆盖：

```java
ParticleEmitter emitter = (ParticleEmitter) runtime.findObject("flame");
float limit = (element == Element.FIRE) ? 1.0f : 0.4f;
emitter.runtime().customData.slot(0, 0).set(new Constant(limit));
```

**为什么把"强度上限"放在 Custom Data 而不是写死在 shader 里**：同一份材质可以被火、冰、雷共用，
差别只在运行时那一个 float。**一个材质 = 一次合批**，这比"每种元素做一份材质"要划算得多。

**验收清单**：

- 元素切成火 / 冰，发光强度应立刻变化，**不需要重新加载资源**；
- 把粒子数量拉到几千（走 GPU 实例化），强度仍然正确——如果只有 CPU 路径对，说明 Attribute 没接上；
- 在节点预览 / 材质预览里看到的是 0（预期），**不要**据此判断接错了；
- Timeline 里若给 Stream 0 的 x 打了关键帧，Java 的 `set` 会被覆盖——确认两者只有一个在写；
- 打开 HDR/Bloom 检查亮度上限 1.0 与 0.4 的差异是否明显（不明显就把上限提到 1.5 以上再试）。

### 8.9.11 这一节的坑与自检

按"从资源到画面"的顺序自查，基本能覆盖九成的失败：

| 现象 | 先检查 |
|---|---|
| 材质预览红色报错 | JSON 是否合法、Shader ID 是否带错前缀、include 路径是否存在；**先修错误再重载** |
| 画面全黑 | 用常量颜色替掉 Base Color；能显示说明算法问题，不能显示说明管线/混合问题 |
| 参数在 Inspector 里找不到 | 名字是否以 `Sampler` / `U_` 开头（保留前缀会被隐藏）；类型是否可反射（int/矩阵/超宽 count 不可） |
| 参数改了没反应 | 参数是不是只写在 Graph 内部常量上（必须暴露成 Graph Variable）；改完有没有保存 + Reload |
| GPU 数据恒为 0 | 是否在 CPU 预览路径；Additional GPU Data 有没有启用；Stream Index 是否越界 |
| 手写材质读到 0 | 是否误用了 `photon_custom_data()`；Attribute Location 是否因为多勾了一个内置 Channel 而错位 |
| 换渲染器就花屏 | 是否绕过 `getParticleData()` 自己声明了 Attribute（每个 Define 的布局都要照顾到） |
| 装光影后黑屏 | Scene/Depth Buffer 在 Iris 下可能不可用，必须写"没有 Buffer 时的安全输出" |
| 改完还是旧画面 | 保存 → Reload Shader → 清 Photon FX Cache；改资源包再执行一次 Minecraft 资源重载 |

一句话收尾：**先用 Graph 把效果做出来，只有当 Graph 真的表达不了时才回到手写 Shader；
而无论走哪条路，"每粒子一个数值"都应该走 Custom Data，而不是给每个粒子一份材质。**
