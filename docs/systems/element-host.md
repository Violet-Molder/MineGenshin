# 元素载体：可附着宿主

## 这个系统负责什么

回答"元素能挂在什么东西上、谁决定收不收、挂上之后发生什么"。

以前附着有两条平行的路：生物走 `ElementalAttachmentHelper`（有覆盖、损耗、筛查规则），
方块走 `BlockElementHelper`（静态 `if (state.is(Blocks.WATER))` 判定 + 每次现造一个临时容器）。
结果是**每加一种可附着方块就要把整套处理再抄一遍**，方块永远是一条支线。

现在所有载体共用同一个抽象 `ElementalHost`：附着、筛查、反应只认宿主，不认"它是实体还是方块"。

## 一次附着的完整链

```
任意来源（角色攻击 / 环境 / 反应的二次写入）
   → ElementalAttachmentHelper.attach(host, element, source, profile)     ← 唯一入口
       ① host.acceptsElement(...)      第一段筛查：这次附着收不收
            不收 → 元素不写进容器，且不尝试反应（先手元素保留 = 共存）
       ② 写进 host.container()
       ③ 触发反应（在入口内部，不用调用方记得调）
            └─ host.acceptsReaction(...)  第二段筛查：这个反应能不能在这个宿主上发生
       ④ host.onElementAttached(element) / onElementDetached(element)     ← 元素本体的附加效果
   → 返回 AttachResult（这次挂上没有 + 出了什么反应，供伤害侧取增幅倍率）
   → 伤害侧另算：ElementalAttachable.isImmuneToElementDamage(element)     ← 免疫只把伤害归零

   ElementalAttachmentHelper.attachInternal(host, ...)   ← 反应内部/自身附着的二次写入
       同样写进容器、同样问宿主筛查，但**不再触发反应**（否则"反应生附着、附着再反应"会递归）
```

顺序上有两条硬约束：

- **附着先于伤害判断**：免疫、护盾裁决都不该让"元素没挂上"。挂元素与掉血是两件事。
- **没挂上就没有反应**：宿主拒收这次附着时，反应同样不发生 —— 不允许元素绕过附着直接反应。
- **反应由附着入口触发，不由调用方触发**。这是踩过的坑：以前调用方各自决定"要不要调反应"，
  于是攻击管线记得调、方块入口记得调、环境路径忘了调 —— 表现就是"给怪挂冰后推进水里，冻结不发生、
  `NoAI` 也不生效"。现在只要经过入口，反应一定会被尝试一次。

## 宿主契约

`core/system/about/host/ElementalHost`：

| 方法 | 回答什么 | 说明 |
|---|---|---|
| `container()` | 容器在哪 | 可写 `StatusContainer`；宿主无效时返回 `null` |
| `isValid()` | 宿主还在不在 | 实体活着 / 方块所在 chunk 已加载 |
| `acceptsElement(...)` | 这次附着收不收 | 第一段筛查，语义见下 |
| `acceptsReaction(...)` | 这个反应能不能出 | 第二段筛查，默认全允许 |
| `onElementAttached` / `onElementDetached` | 附着/分离时做什么 | 元素本体附加效果的落点（改成了空实现的载体除外） |
| `entity()` / `level()` / `blockPos()` | 载体视图 | 元素钩子按需取用，方块宿主没有 `entity()` |

实现一个宿主只需要回答这几件事，不用关心元素怎么衰减、反应怎么配对、飘字发到哪。

## 三个内置宿主

| 宿主 | 载体 | 容器存哪 | 第一段筛查由谁回答 |
|---|---|---|---|
| `EntityHost` | `LivingEntity` | `AttachmentRegistration.CONTAINER`（附件，随实体存档） | 实体自己实现的 `ElementalAttachable.onAttachElement`（mixin 默认全收，怪物可覆盖） |
| `BlockHost` | 一个坐标上的方块 | Chunk 数据（`BlockElementStore` → `ChunkBlockElements`） | `BlockElementRules` 规则表（没有任何规则命中 = 不收） |
| `CharacterHost` | 出战角色 | `PGCharacterData` 的状态容器 | 恒收（给谁挂、什么时候挂由来源技能决定） |

`CharacterHost` 存在的理由：玩家实体只有一个，但队伍里有多个角色，附着必须跟着角色走。
以前这类附着靠 `attach(player, 角色容器, ...)` 这种"目标与容器不是同一个东西"的写法实现，
现在它就是宿主抽象的一个正常实现（见 `core/status/CharacterChillHandler`）。

## 三段判断是三条独立的轴

这是本次收口的核心：**"收不收附着"、"收不收反应"、"吃不吃伤害"必须能分开表达**。

| 判断 | 钩子 | 默认 | 典型用法 |
|---|---|---|---|
| 收不收这次附着 | `acceptsElement` → 生物侧 `ElementalAttachable.onAttachElement` | 生物 `true`；方块"规则表不匹配即拒收" | 大型冰史莱姆拒绝水；水方块只收冰 |
| 收不收这个反应 | `acceptsReaction` → `ElementalAttachable.onReactElement` | `true` | 允许挂水、但不接受冻结 → 只冰水共存、不生成冻元素 |
| 吃不吃这个元素的伤害 | `ElementalAttachable.isImmuneToElementDamage` | `false` | 元素生物免疫同元素伤害（照样挂冰、照样反应，只是伤害 0） |

由此得到两条以前拿不到的规则：

- **拒收附着 = 不反应**。最典型的症状是大型冰史莱姆：它在 `onAttachElement` 里已经明确拒绝水，
  但以前水打上去照样触发冻结反应、照样生成冻元素 —— 规则写了一处、行为走了另一处。现在不会了。
- **免疫不吞附着**。免疫写在 `hurtServer` 的提前 `return false` 里时，而附着恰好是在伤害管线
  *内部* 做的，于是"免疫这个元素的伤害"被扩大成"连附着都不会发生"。现在免疫只在伤害结算处把伤害归零。

不要指望 `acceptsElement=false` + `acceptsReaction=true` 表达"拒收附着但允许反应"：
拒收附着会让反应根本不会被尝试，那个组合是死配置。真需要纯反应型载体时得另开一个筛查位。

## 环境自附着：方块"本来就是水 / 就是冰"

反应需要先手 + 后手。水方块被冰打之所以能冻结，是因为水这边**本来就有先手的水元素**；
冰方块被火打能融化，是因为冰这边本来就有先手的冰元素。这份"天性"就是环境自附着：

| 方块 | 自带元素 | 说明 |
|---|---|---|
| `minecraft:water`（**只认完整水源 `level=0`**） | `HYDRO` | 满量 1U、不衰减（`AttachmentProfile.PERMANENT`），被反应消耗后周期补回 |
| 冰 / 浮冰砖 / 蓝冰 | `CYRO` | 冰族自带冰，打上去的冰只是"后手" |
| 浮冰（`FROSTED_ICE`） | **无** | 它的元素是冻结反应写进去的 `FROZEN`；再额外挂一份永久冰，会让"冰化了没有"的判据永远为真 |

实现是 `BlockElementRules.selfAura(state)`（声明这个方块自带什么）+ `BlockSelfAura.ensure(...)`（把它写进容器）。

两条细节，都是踩过的坑：

- **自带元素不过外部筛查**：走 `attachInternalTo(container, host=null, ...)`。水本来就不收水
  （"只收冰"那条规则是给**外来附着**定的），但水当然自带水；拿宿主去筛会把方块自己的天性筛掉，
  实测症状是"冰打水面不结冰"。
- **自带元素不触发反应**：它只回答"我是水 / 我是冰"，反应必须由后手附着触发，所以走 `attachInternal`。

## 附加效果子元素：寒（COLD）

"冰的减速"与"冻的禁 AI"本质上是**同一个东西**在起作用，所以它们不再各自挂在冰/冻元素本体上，
而是集中到一个**效果载体**元素上 —— 元素本体只负责附着，载体负责影响：

| | 附着（原因） | 效果（后果） |
|---|---|---|
| 冰 `CYRO` / 冻 `FROZEN` | 自己负责 | 不负责，只表示"身上有什么元素" |
| 寒 `COLD` | 伴随冰/冻存在 | 减速（有冰族时 -10%）+ 禁 AI（有冻时） |

伴随机制在 `core/system/about/ColdAura.tick(entity, container)`，由 `StatusTickHandler` **每 tick** 调一次：

```
容器里有活的冰或冻 → 补一条寒（走宿主筛查）
冰和冻都没了       → 寒一起走
然后：有寒 + 有冰族 → 减速；有寒 + 有冻 → 禁 AI；否则撤销
```

四条要记住的：

- **寒是独立元素，不是"mainElement 归并型"的冰的子元素**。这个代码库里 `mainElement` 的**唯一**含义是
  「参与反应配对时并入主元素」；寒若归并进去，会被反应消耗、被扩散传染当成冰处理（四处要写例外）。
  独立注册后注册表里没有任何反应以寒为配对方 —— **一处例外都不用写**，它天然不参与反应、也不会多算一次冰反应。
  父子关系由伴随机制表达，不靠 `mainElement`。
- **豁免只需表达一次**：冰史莱姆给自己挂冰却完全不受冰影响，实现就是**它不收寒**
  （`ElementalCreature.acceptsElementAttachment`：寒按"是否免疫冰"决定收不收）。
  补寒那一步走宿主筛查，于是"没有寒 → 没有减速、没有禁 AI、也没有冻结位移锁"。
- **效果不放在 `onAttach` / `onDetach` 钩子里**，这是硬事实逼出来的：`doAttach` 里
  `host.onElementAttached(...)` 是在 `container.add(...)` **之前**调的，`StatusContainer.remove/removeFirst`
  也是先 `onRemove()` 再把实例移出列表 —— 钩子里读容器一定读到**旧状态**（冻刚挂上时只看到冰 → 该禁 AI 的只减速；
  寒被移除时以为寒还在 → 减速撤不掉，变成永久减速）。每 tick 重算只有一份判据，代价是最多晚 1 tick。
- **HUD 跳过效果载体**：`GenshinElement.isEffectCarrier()` 为真时血条不出图标 ——
  该显示的是冰/冻本身，寒没有贴图也不该占位。

载体分工：玩家/出战角色的减速走 `core/status/CharacterChillHandler`（角色宿主不挂寒）；
生物走寒；方块宿主没有实体，寒无效果可言。

## 攻击怎么把元素留给环境

原神模式下每次攻击都是"手动取范围 → 遍历 → 结算"，而那条遍历原本只找 `LivingEntity`，
方块永远接不到元素 —— 需求只能另开一条支线（左键事件）去补，内核就分裂了。
`attack/ElementalAttackSweep` 把这段补齐，并且**不新开附着体系**：

```
实体（有血量）  hurtServer → DirectDamagePipeline → ElementalAttachmentHelper.attach
方块（无血量）  ElementalAttackSweep → BlockElementHelper.applyElement → 同一个 attach
```

几何：从玩家眼睛沿视线取「这一招在 `ActionDefinition` 里声明的攻击距离」（下限 2.5 格）再膨胀 1 格，
遍历这个盒子里的方块，**攻击者自己身体占着的那一格会跳过**（不然站在水里挥一刀就把脚下的水冻住）。
盒子体积上限 4096 格，防止某个招式把攻击距离配成离谱的值时遍历爆炸。

三道门禁，顺序即语义：**只在服务端** → **只在原神模式** → **角色元素非空**（物理系不附着）。

调用点有两处，调的是同一个 helper：

| 调用点 | 什么时候走 |
|---|---|
| `ActionState.fireDamagePoint()` | 动作系统**打开**时（永久路径；伤害点分散在时间轴上，每个伤害点各附着一次） |
| `ActionManager.fireImmediately()` | 动作系统**关闭**时（即时结算的过渡分支；一次调用覆盖整段动作） |

> 即时结算那条分支是过渡形态：它的所有 hit 都在同一 tick 结算、站位与朝向相同，
> 而附着是"同元素同来源刷新量"的幂等操作，所以一次调用与逐段调用结果相同，只是少扫几遍盒子。
> 新模型（动作系统打开）落地后这条分支会删掉，届时永久路径一行都不用改。

## 性能：什么时候扫、什么时候落盘

方块元素是高频逻辑，三条纪律：

- **先筛后建**：`BlockElementRules.accepts(state, element)` 不匹配就立刻返回 —— **连容器都不建**。
  旧实现是"先建容器、再让筛查拒掉"，扫一片石头就给每格造一份空 `StatusContainer` 写进 chunk。
- **读不写**：只读一律走 `BlockHost.peekContainer()` / `BlockElementStore.peek()`（不建、不补自附着、不落盘）；
  真要写入才走 `host.container()`。推进衰减、查询状态、迁移判断全部走只读路径。
- **改才提交**：`BlockElementStore.commit` 是幂等的 —— 容器已经在这张表里（同一个对象引用）就直接返回，
  不再 `setData`。一次多段攻击对同一格反复附着，命中的是同一份容器，只会真正标脏一次；
  衰减推进**每 tick 都不提交**（容器是原地改的对象，存档时自然带上）。

浮冰的衰减**不走原版方块 tick 链**，而是集中式推进：`BlockElementTicker` 每 tick 推动登记在案的冻结方块，
每格每 game tick 只推进一次（`BlockElementStore.beginDecayStep`）。

> 为什么必须这样：浮冰身上可能同时挂着多条排期（我们排一条、原版 `onPlace` 又随机排一条 60–120 tick），
> 多余排期会让某格在同一 tick 里多走一步衰减 —— 表现是"一起结的冰不同时化"。
> 而原版 tick 链一旦断掉（重复排期 / 邻居变化 / 区块边界），那格就**再也没人推** ——
> 表现是"一片冰里边缘逐个化、中间一直冻着"。集中式推进与排期条数无关，天然规避这两类问题。

## 方块怎么接入

方块不再是一条支线：`BlockHost` 把"这个坐标能不能附着"交给规则表、把容器交给 Chunk 数据，
剩下的附着与反应和生物完全同路。新增一种"与元素有关的方块"只需要两条注册，
**不用改任何核心代码**（`BlockElementRules` / `BlockElementMigrations`）：

```java
// ① 这个方块能被什么附着
BlockElementRules.register(state -> state.is(MY_BLOCK.get()),
        (state, element) -> element == ModElements.PYRO.get(),
        "my_block");

// ② 元素挂上之后，方块该变成什么样（读容器里的事实 → 改方块状态）
BlockElementMigrations.register(state -> state.is(MY_BLOCK.get()),
        (host, container) -> host.level().setBlock(host.blockPos(), MELTED.get().defaultBlockState(), 3),
        "my_block");
```

内置的两条示范：

| 方块 | 收哪些元素 | 迁移 |
|---|---|---|
| `minecraft:water`（完整水源） | 收冰、收冻 | 容器里出现 `FROZEN` → 变成浮冰，并记下冻之前的水位 |
| 冰族（浮冰 / 冰 / 浮冰砖 / 蓝冰） | 收火、收冰、收冻 | 容器里没有**活的冻元素** → 清掉记录、变回水（水位按记录还原） |

四条纪律：

- **迁移只在这次调用真的挂上了元素时才跑**。否则"冰族没有冻 → 化水"这条规则
  会被水攻击、风攻击误判成融化（早期实测踩过）。
- **方块状态是表现，附着是原因**。元素本体不负责改方块（`BlockHost` 的 `onElementAttached` 故意是空的），
  "水结冰"这类事统一由迁移消费者读容器后决定。
- **"冰化了没有"只看冻元素，不看冰元素**。浮冰存亡的依据是 `FROZEN` 有没有耗尽 ——
  冰元素是"它为什么结冰"的原因，不是"冰还在不在"的依据；拿冰当判据会让任何一份残留的冰元素
  把浮冰永久钉住（实测症状：一片冰永远不化）。
- **原版旁路要切断**：`IceBlockMeltGuardMixin` 让"已经在元素体系里"的冰被**破坏**时不再直接变成水
  （那是原版 `IceBlock.playerDestroy` 的行为：非精准采集 + 下方是固体/液体）；
  `FrostedIceMixin` 让浮冰不再被"周围同类少于 2 个"这条原版规则旁路融化。
  但**没有元素容器的浮冰交还原版**（例如冰霜行者踩出来的那种）—— 它的存亡不归元素体系管。

方块侧的入口是 `BlockElementHelper.applyElement(level, pos, element, gauge, decayPerSec)`，
它做三件事：走宿主入口附着 → 真挂上了才尝试反应 → 落盘并让方块状态跟上。

## 加一种新的可附着载体

1. 实现 `ElementalHost`（回答上表那几件事），容器可以自建，也可以复用 `StatusContainer`。
2. 两段筛查按需要实现：能收什么、能不能出某个反应。
3. 只调 `ElementalAttachmentHelper.attach(host, ...)` 这一个入口，不要自己写元素衰减或反应配对。
4. 需要表现（改方块、放粒子）就在 `onElementAttached` / 迁移里做，别写进元素本体。
5. 飘字不用自己抄：反应在 `attach` 内部触发，飘字出口按宿主类型自动选
   （实体 → 目标头上；方块 → `DamageIndicatorFactory.reactionAtBlock`；角色宿主 → 静默）。
   特定反应想静音就覆盖 `ElementalReaction.showsIndicator(ctx)`（默认 `true`）——
   方块上的冻结/融化就是这么处理"水结冰不出字"的。

物品（`ItemStack`）侧目前**只有读取口**（`StatusAccessor` 能读），全库没有任何一处写入 ——
别按"已经支持"来规划功能。`mixin/mixins/BlockEntityElementalMixin` 虽然声明实现了
`ElementalAttachable`，但全库没有任何一处调用它，是个没接线的空壳，别照着它写。

## 常见坑

| 现象 | 原因 |
|---|---|
| 元素挂上了但不掉血 | 目标对该元素免疫（元素生物免疫同主元素）；免疫只归零伤害，附着与反应照常发生 |
| 反应该出却没出 | 宿主拒收了先手附着（拒收 = 不反应），或宿主在第二段筛查里拒绝了该反应 |
| 挂不上元素 | 第一段筛查拒收：生物看 `onAttachElement`，方块看 `BlockElementRules` 有没有命中规则（不匹配即不收） |
| 方块状态自己在变 | 迁移没加"这次真的挂上了元素"的前置判断，没挂上的调用也会触发迁移 |
| 冰打水面不结冰 | 水那一格没有先手元素：自带水缺失，或者你打的是**流动水** —— 流动水既不自带水也不收冰，完全不参与元素体系 |
| 一片冰永远不化 | 融化判据里混进了冰元素（应只看冻元素）；或这格不在推进表里（区块加载时会自愈补登记） |
| 一起结的冰不同时化 | 同一 tick 被重复推进（`beginDecayStep` 已挡住）；现在剩余差异只来自冻元素量的不同 |
| 冻住了却还能被推动、解冻瞬间"弹"回原位 | `setNoAi` 只管 AI 目标，**物理照样跑**（水流推动 / 浮力仍改服务端速度，客户端 NoAI 不预测这段）。冻元素在身时清 `deltaMovement` 并停疾跑（见 `StatusTickHandler`） |
| 方块吃元素但整个服务器卡顿 | 迁移里做了重活，或每 tick 提交/同步整 chunk 元素表 —— `commit` 幂等化、去掉 `.sync()`、改成集中式推进之后不应再出现 |
| 免疫的怪还是掉血 | 免疫走 `isImmuneToElementDamage`（按**主元素**比较）；只在 `hurtServer` 里削伤害不走这条轴 |
| 新增方块后核心代码还是要改 | 说明迁移写进了 `BlockElementHelper` 而不是 `BlockElementMigrations` 注册表 |

## 26.2.0.2 变更：宿主升级为模块

元素容器的存取从「附件 `elementlib:status_container` / `ChunkBlockElements.containers`」搬进了通用模块层：

- 实体：附件 `elementlib:modules`，元素是其中的 `elementlib:element` 模块；
- 方块：区块 `elementlib:chunk_modules` 的持久区；水位仍留在旧附件上；
- 角色：`PGCharacterData.moduleContainer`；
- `ElementalAttachments.container(...)` 与 `BlockElementStore` 已是模块入口的兼容壳，旧存档首次访问时按格迁移。

宿主种类与统一查询见 `docs/systems/module-system.md`。
