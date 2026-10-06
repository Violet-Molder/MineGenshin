# 韧性（削韧）与控制

韧性系统回答一个问题：**什么时候敌人会吃控制**。

没破韧 → 敌人照常掉血、照常变红，但不吃任何控制（僵直、击退、悬浮、聚怪牵引），
也不会被打断动作；破韧 → 上面这些才生效。
玩家走的是同一套规则，只是多一个「霸体强度」可以堆。

## 关键类

| 类 | 位置 | 职责 |
|---|---|---|
| `PoiseTiers` | `core/system/poise/` | 档位总表：抗牵引等级 → 韧性档位，以及每档数值（条长 / 每秒衰减 / 重置时间）。**1 档已按口径上调到条长 100**（2026-09-25），其余仍是本项目手感值 |
| `PoiseState` | `core/system/poise/` | 运行期状态：攒了多少削韧、破没破、驻留计时、暂停计时 |
| `PoiseService` | `core/system/poise/` | 累积 / 自恢复 / 破韧 / 驻留 / 联机系数 / 霸体系数，**唯一收口** |
| `PoiseTickHandler` | `core/system/poise/` | 每 tick 推进（`EntityTickEvent.Post`），照 `ShieldTickHandler` 写 |
| `HitPoise` | `core/system/poise/` | 「当前伤害点的削韧系数」的窄作用域载体，见下 |
| `HitImpact` | `core/system/poise/` | 「当前伤害点的冲击类型」的窄作用域载体，与 `HitPoise` 同款 |
| `ImpactLevel` / `ImpactSolver` | `core/system/poise/impact/` | 硬直等级 0–9（文献「常用冲击类型」表）与重量判定 + 冲量施加 |
| `WeaponPoiseTable` | `core/system/poise/` | 武器类型的基准削韧表（普攻 / 重击 / 下坠），以及对应的硬直等级 |
| `ReactionPoiseTable` | `core/system/poise/` | 反应自带的削韧与冲击（超载 90、扩散 130…），含「对角色」那一行 |
| `PoiseFreezeBreak` | `core/system/poise/` | 冻结 → 直接破韧这条通道（一次冻结只破一次） |
| `ControlType` | `core/system/control/` | 控制分类：削韧 / 软控 / 击退 / 悬浮 / 牵引 / 冻结，每档带「谁执行、暂停不暂停韧性恢复」 |
| `ControlRequest` | `core/system/control/` | **一次控制请求**的包裹：类型 + 削韧 + 冲击 + 来源 + 按住时长 |
| `Controllable` | `core/system/control/` | **所有控制类效果唯一的入口** `applyControl`，以及**实体内置方法** `interruptAction` / `cancelOngoingAction` / `knockback` / `freezeAction` |
| `ControlService` | `core/system/control/` | 没有实体可挂的那部分：命中入口 `onHit`、静止窗口 `openStandStill`、破绽窗口 `openGap`、直通口 `force`、护盾读口、强控按住韧性恢复 |
| `MobServerAiStepMixin` | `mixin/mixins/` | **通用打断的 AI 闸门**：静止窗口里把目标的 `serverAiStep()` 整段掐掉 —— AI 总闸以下（Goal / Brain / 导航 / 三个控制器）一次全停 |
| `TickActionSuppressor` | `core/system/control/` | **非 AI 管线的动作压制**接入点：原版那些写在 `tick()` / `aiStep()` 里的状态机（苦力怕引信、劫掠兽计时器）各写一个 mixin 实现它，核心代码不点名任何一只怪 |
| `MovementHold` | `core/system/control/` | AI 持握**租约**：借 `setNoAi` 按住一阵，到期自动还原 |
| `GatherPull` | `content/skill_node/` | **牵引节点**：两个 AABB + 4 档初始削韧 + 先削韧后牵引 + 持续模式 |
| `Levitate` | `content/skill_node/` | **悬浮节点**：强控，未破韧 / 有盾完全不生效 |

附件注册在 `AttachmentRegistration.POISE`（同步给客户端，血条下面那条削韧条读它）。

`Controllable` 挂在 `TeyvatLiving` 上，而原版 `LivingEntity` 由 `LivingEntityTeyvatMixin`
注入 `NonTeyvatEntity`（`NonTeyvatEntity extends TeyvatLiving`）—— 所以**每一个活体都有这个入口与内置方法**，
原版生物直接拿到默认实现，本模组的怪与首领只覆盖想改的那一个方法。

## 一个入口：所有控制类效果都从这里进

用户口径（2026-09-25）：**所有控制类效果走一个入口，包括冻结的 NoAI**，
然后**实体在这里判断是削韧、还是控制、还是直接免疫拦住**。
落点就是 `Controllable.applyControl(ControlRequest)`：

```
一次命中 / 一次技能施加
  → Controllable.applyControl(请求)
      ├─ ① 请求带削韧（> 0）→ 先把这一笔落进韧性条（PoiseService，有盾时内部挡掉）
      └─ ② verdict(请求)：这次算削韧、算控制、还是免疫？
             REFUSED → 免疫：控制不生效
             POISE   → 没破韧：只有削韧那一笔落地
             WINDOW  → 破绽窗口那一次：控制生效，且无视抗打断系数
             CONTROL → 破韧期间：控制生效（命中类还要过抗打断系数）
```

第 ② 步 `Controllable.verdict` 的顺序是固定的，**别调换**：

| 顺序 | 判据 | 结果 |
|---|---|---|
| ① | 请求是**冻结** | 无视韧性直接成立；解冻永远放行；`blocksControl` 为真则免疫 |
| ② | 有**霸体盾** | 拒绝（护盾 = 霸体，破绽窗口也挡） |
| ③ | 开着**破绽窗口**且请求是命中类的 | 放行一次（然后窗口用掉） |
| ④ | **没破韧** | 带削韧 → `POISE`（只削韧）；不带削韧 → `REFUSED` |
| ⑤ | 实体 **`blocksControl`**（首领） | 拒绝（削韧照吃，控制不生效） |
| ⑥ | 以上都不是 | 放行 |

**削韧不是单独一条路**：它跟着请求一起进这道门，未破韧时判定就落成 `POISE` ——
所以「未破韧时聚怪照样削韧、只是拉不动」不是例外，而是这条规则的直接结果。
调用方**只问入口，不自己判断**：技能节点用 `ControlService.canApply`（只判不做、也不消费窗口），
命中走 `ControlService.onHit`，要强行施加走 `ControlService.force`。

## 打断动作：破韧期间几乎无控制抗性

⚠️ **口径更正（2026-09-25）**：早先的实现是「打断只发生在破韧那一瞬间」，
判据写死成「硬直等级 ≥ 2」。用户更正为：

> 不是只有破韧那一下有击退，是**破韧期间敌人处于几乎无控制抗性，随便攻击都能打断其目前的动作**。

> 击退是次要的，不一定有，**最主要的是打断他的动作** —— 也就是插入他的 AI 逻辑，
> 让其强行回到静止状态（比如女巫本来在喝药，直接打断其动作）。

再加上「削韧本质上就是给敌人造成一次控制」这一条，所以现在的规则是：

```
破韧期间，每一次命中都
  ├→ 打断动作（主）：Controllable.interruptAction
  │     ├ 破绽窗口 / 直通口那一次     → 放行（不看抗打断系数）
  │     └ 打断强度 ≥ 抗打断系数        → 放行；否则拒绝
  └→ 击退（次）：ImpactSolver —— 这一下推得动才推，推不动就只有打断
```

顺序由 `verdict` 保证：**没破韧根本走不到这里**（第 ④ 步就落成 `POISE`），
**有盾在第 ② 步被拦下**，**首领在第 ⑤ 步被 `blocksControl` 拦下**。

**打断强度**就是硬直等级本身（微颤 1 / 轻击 2 / 击退 3–4 / 击飞 5–9），
**抗打断系数**就是 `minegenshin:super_armor`（越高越耐，与抗削韧共用一个系数）。
于是门槛不再是一个常数，而是落在目标身上：

| 目标 | 系数 | 破韧期间什么能打断它 |
|---|---|---|
| 普通生物（原版 + 本模组） | 1 | 微颤及以上 —— **随便一下** |
| 精英怪（自己配 3 之类） | 3 | 击退及以上；轻击/微颤打不断 |
| 首领（`ITeyvatBoss`） | 10（`PoiseTiers.BOSS_SUPER_ARMOR`） | 最强的一档击飞 9 也不够 → **只能走破绽窗口** |
| 玩家 | 1（堆高就不怕） | 同左：堆到 3 就只剩重冲击能打断 |

**打断动作与击退是实体内置方法**（`Controllable.interruptAction` / `Controllable.knockback`），
所以「推不动」这类最常见的特例只要覆盖一个方法：

| 内置方法 | 怪 | 玩家 |
|---|---|---|
| `interruptAction` | 取消进行中的动作（`cancelOngoingAction`，含女巫喝药那类）→ 正在跑的 Goal 逐个 `stop()` → `getMoveControl().setWait()` → `navigation.stop()` → 清移动意图与水平动量（`haltMovement`）→ 开一个**静止窗口**（`ControlService.openStandStill`，见下节：窗口内 AI 整体不跑，那不叫「停 AI 开关」） | 走 `ActionManager.get(player).interrupt(InterruptReason.DAMAGE)` —— 动作有它自己的中断规则，不去停 Goal |
| `cancelOngoingAction` | 通用 use（`stopUsingItem`）+ 挥击归零（窗口里每刻做，所以挥刀动画也定住）+ **点名女巫喝药** + 把活转给 `TickActionSuppressor`（原版那些不走 AI 的状态机）。女巫那瓶药要三件事一起做（`cancelWitchDrink`：翻标记 + 拿走主手的药 + 拆掉喝药减速）—— 只翻标记她会下一 tick 重新起手、瓶子还一直拿在手里；**本模组怪的自定义读条 / 蓄力直接覆盖这个方法就行（纯 Java，不用 mixin）** | 同上，不走这条 |
| `knockback` | `ImpactSolver.apply`（重量判定 + 冲量施加） | **直接交给原版** —— 我们这套冲量是「替换水平分量」的，落在玩家身上会把疾跑和自己的移动一起抹掉 |

**请求没带冲击时按微颤（1）算** —— 这就是「只打断、不推」的默认值：
`applyHitControl` 取 `impact == null ? ImpactLevel.TREMBLE.interruptStrength() : impact.interruptStrength()`，
所以普通敌人（系数 1）连弓和法器都打得断。真正什么都不会发生的只有
`ImpactLevel.NONE`（等级 0）那一种：**既不打断、也不推**。

**击退与打断门槛无关**：`knockback` 在 `applyHitControl` 里是独立一句，
`impact == null` 时它内部直接返回（连微颤那点位移都没有）。
「打断而不推」「推而不打断」都是合法组合。

**别的模组的实体整块跳过**（`PoiseTiers.isForeign`）—— 不敢对别人的 Goal 乱调 `stop()`。

## 静止窗口：三层，从 AI 的总闸往下切

「强行回到静止状态」落地成两件事：**当刻**把正在跑的 Goal 停掉、清动量
（`interruptAction` 里做），以及**接下来很短一段时间**（默认 `HIT_TICKS = 10` 刻 = 0.5 秒）
让目标没法立刻重新起手 —— 后者是静止窗口。

窗口只是一个**服务端时间戳**（`ControlService.STANDSTILL`）：不动 `NoAI`、不进 NBT。
窗口内三层一起按：

| 层 | 谁 | 管什么 | 为什么非得有它 |
|---|---|---|---|
| ① AI 闸门 | `MobServerAiStepMixin` | 窗口内目标的 `serverAiStep()` 被**整段掐掉** | 那是 AI 的**总闸**：`sensing`（探测）、`targetSelector` / `goalSelector`、`navigation`、`customServerAiStep`（Brain 类怪的 `getBrain().tick()` 就在这儿）、`moveControl` / `lookControl` / `jumpControl` 全在它下面。一处拦下 = **走 AI 的动作整类都停** |
| ② 每刻取消钩子 | `ControlService.onServerTick` → `Controllable.cancelOngoingAction` | 窗口里每刻再取消一次（`stopUsingItem` + 挥击归零 + 女巫喝药） | 闸门只管 AI 管线；压一次也不够 —— 女巫下一 tick 就自己接上。**本模组的怪覆盖这个方法就能接进来**（纯 Java，不用 mixin） |
| ③ tick 级动作压制 | `TickActionSuppressor` + 各自的 mixin | 原版那些连闸门都拦不住的状态机：苦力怕引信（`Creeper.tick()`）、劫掠兽的 `attackTick / stunnedTick / roarTick`（`Ravager.aiStep()`） | 它们的私有字段模组碰不到，只能在 mixin 里摸；核心代码通过接口转发，**不点名任何一只怪** |

**为什么不掐整段 `aiStep()`**：`aiStep` 里还有**物理** —— 重力、摩擦、`travel`（动量结算）。
整段掐掉，被击退的怪会停在半空、掉不下来也不滑动。闸门只拦 AI，物理照跑。

**为什么不用原版的 `setNoAi(true)`**：①它是同步标记、进 NBT，忘了松开就留下一只永久定身的怪；
②冻结（`freezeAction`）也抢这个开关，两套机制共用迟早打架；③**它根本不够用** ——
NoAI 的苦力怕照样把引信走满炸掉、NoAI 的史莱姆照样跳。

**同一个 tick 里谁先谁后**：窗口是「打断」那一下开的。目标若在本刻稍后才 tick，
闸门（HEAD 注入）正好赶上；若它本刻已经 tick 过了，压制在**每刻**都做，下一 tick 接着按住 —— 两种都拦得住。

**「别的怪也这样」怎么办**：不改核心代码，加接入点就行 ——
原版 / 别的模组的怪写一个 mixin 实现 `TickActionSuppressor`（照 `CreeperFuseMixin`：
`@Accessor` 开门 + `minegenshin$suppressTickAction()` 里归位 + 把 `@Inject` 打到它推进状态的那个方法 HEAD），
并注册进 `minegenshin.mixins.json`；本模组自己的怪直接覆盖 `Controllable.cancelOngoingAction`（记得幂等）。

想**长时间**按住（牵引、悬浮）不要走这条路，那是 `MovementHold` 租约的职责。

## 一条韧性的生命周期

```
带削韧的攻击命中
  → 有盾？ → 不进韧性条（PoiseService 内部挡掉）
  → 无盾   → 攒削韧（÷ 霸体强度）→ 攒满 = 破韧
破韧
  → 值锁在上限、不再衰减，开始「驻留计时」（重置时间）
  → 强控（悬浮 / 牵引）生效期间，驻留计时被按住
  → 驻留走完 → 削韧值瞬间归零，韧性重新生效
非破韧期间
  → 每 tick 掉「每秒衰减 ÷ 20」，所以哪怕一直被攻击也会恢复
```

## 三个属性

| 属性 | 默认 | 含义 |
|---|---|---|
| `minegenshin:poise` | 1 | **档位**（1–4）。管「每秒衰减多少、破韧后驻留多久」；没配过则按 `PoiseTiers` 从抗牵引等级派生 |
| `minegenshin:poise_max` | 0（= 不覆盖） | **韧性上限**（条有多长）。配了就用它当基准长度；`0` / 负数 = 退回档位长度 |
| `minegenshin:super_armor` | 1（首领 10） | **霸体强度 / 抗打断系数**，越高越耐，**一个系数管两件事**：实际削韧 = 攻击削韧 ÷ 它；打断还要「打断强度 ≥ 它」。`0` 按 `1` 处理 |

**上限是逐实例的可变属性**（用户口径 2026-09-25）：已经刷出来的实体基本上是固定的，
但**同一个注册表条目（比如僵尸）在不同环境下可以不一样** —— 副本里调高、普通地区保持默认，
只要在实体的属性实例上写 `poise_max` 就行。上限缩小会把当前值一起钳下来，所以中途改是安全的。
和档位长度一样，它也要**乘联机系数**（文献：韧性条实际长度 = 标准长度 × 联机玩家数）。

> 文献里那套「霸体系数」是「越小越耐削、0 = 完全免疫」的反方向。换算只发生在
> `PoiseService.poiseFactorOf` 一处，别在别的地方再翻一次方向。
> 没配过属性时的默认值是 `PoiseTiers.defaultSuperArmor`：首领 10，其余 1。

**分工记清楚**：`poise_max` 管条有多长，`poise` 管每秒衰减与破韧驻留，
`super_armor` 管「这一下削多少、打断门槛多高」。三者互不覆盖。
数值表（默认条长 / 每秒衰减 / 重置时间）只有 `PoiseTiers.TIER_PROFILES` 一处：

| 档位 | 谁在这一档 | 条长 | 每秒衰减 | 破韧驻留 |
|---|---|---|---|---|
| 1 | **普通生物**（原版普通生物 + 本模组的怪） | **100** | 5 | 5 秒 |
| 2 / 3 | 目前没有生物落在这一档（留给以后按怪单独配） | 140 / 200 | 8 / 12 | 5 / 3 秒 |
| 4 | 大体型（劫掠兽、铁傀儡、恶魂、远古守卫者）与 BOSS | 280 | 20 | 2 秒 |

**为什么 1 档是 100**（用户口径 2026-09-25「上调普通怪物的基础韧性值」）：原来条长 30，
单手剑、长柄、双手剑的普攻一下就打穿了，普通怪几乎一直处在破韧状态里，
「没破韧不吃控制」这条主玩法反而看不见。现在的实际张数（抗打断系数 1）：

| 武器 | 普攻削韧 | 破普通怪几下 |
|---|---|---|
| 单手剑 | 50 | 2 |
| 长柄武器 | 45.8 | 3 |
| 双手剑 | 107.4 | 1 |
| 弓 | 15.7 | 7 |
| 法器 | 10.2 | 10 |

武器那张表（`WeaponPoiseTable`）**没有动**，还是文献中位数 —— 觉得法器 / 弓太慢就调那边
（那是「降低角色削韧模板」那条路）。四档的满条恢复时间都在 14–20 秒。

## 削韧从哪来

| 来源 | 值 | 说明 |
|---|---|---|
| 招式自己写的 | `ModDamageSpec.withPoiseDamage(...)` | 最优先。角色逐招微调用它 |
| `WeaponPoiseTable` | 见下表 | 普攻 / 重击 / 下坠**按攻击者的武器类型**查；文献那五张逐武器表的中位数 |
| `ReactionPoiseTable` | 超载 90、扩散 130、感电 130… | **反应**自带的削韧，与武器和攻击类型都无关 |
| `ModDamageSpec.defaultPoise` | 普攻 10 / 重击 15 / 下落 20 / 战技 15 / 大招 25 / 怪物 10 | 兜底占位值：战技、爆发、怪物、环境伤害这类前两张表管不着的 |
| `Hit.poise` | 系数，默认 1.0 | **系数**不是绝对值；招式数据里给「更重的一下」写大于 1 |
| `ShieldService.plainAttackPoise` | 10 | 非原神模式下、不走本 MOD 伤害管线的普通攻击的兜底 |

武器基准表（`WeaponPoiseTable`，取文献逐武器表的**中位数**）：

| 武器 | 普攻 | 重击 | 下坠期间 | 低空坠地 | 高空坠地 |
|---|---|---|---|---|---|
| 单手剑 | 50 | 60 | 25 | 100 | 150 |
| 双手剑 | 107.4 | 81.7 | 35 | 150 | 200 |
| 长柄武器 | 45.8 | 120 | 25 | 100 | 150 |
| 法器 | 10.2 | 90 | 5 | 50 | 100 |
| 弓 | 15.7 | 20 | 10 | 50 | 100 |

拿它去对「低阶敌人 60」这条韧性：单手剑普攻两下破、法器要六下、双手剑一下就破 ——
方向与文献一致。要逐角色微调就用 `withPoiseDamage`，不要改这张表。

> 战技与元素爆发的削韧**取决于技能本身**，与武器类型没有统计关系（同是单手剑，
> 有的 E 是 0、有的 E 是 300），所以不在这张表里。

`Hit.poise` 是怎么传进去的：`ActionState` 在触发某个伤害点之前把它挂到 `HitPoise`，
技能里新建的 `ModDamageSpec` 自动读走 —— 所以角色技能代码一行都不用改。

### 刻度：削韧与韧性条走文献口径，盾量走自己的

两边刻度差约 100 倍，用 `ShieldService.POISE_TO_SHIELD`（= 0.01）显式折算：
扣盾量 = 削韧 × 0.01。改刻度只影响这一个系数，盾的既有数值一个都不用动。

## 护盾 = 霸体

| 有盾时 | 结果 |
|---|---|
| 攻击削韧 | **不进韧性条**（`ShieldService.blocksPoise`）；盾自己照旧被磨 |
| 聚怪牵引 | **无效**（`ShieldService.blocksControl`，`GatherPull` 会先问） |
| 玩家挨打 | 不打断动作 |

盾的模板可以关掉这一条（`ShieldProfile.Builder.noSuperArmor()`），默认是霸体。
判定对玩家和对敌人完全一样。

## 冲击：破韧期间每次命中都推

「削韧」和「冲击」是两件事：削韧决定**什么时候**破韧，冲击决定**被推开多少**。

```
攻击命中 → 攒削韧 → 攒满 = 破韧
         └→ 只要目标是破着韧的（包括刚刚被这一下打破）：
              └→ ImpactSolver：拿这一下的「硬直等级」对目标「重量」做判定
                   ├ 地面 + 竖直力 ≥ 5.5×重量 → 击飞
                   ├ 地面 + 水平力 ≥ 2.0×重量   → 击退
                   ├ 空中/攀爬 + 等级 > 微颤    → 一律击飞
                   └ 够不上                      → 降级（击飞→击退→微颤）
```

⚠️ **口径更正（2026-09-25）**：早先只在**破韧那一瞬间**施加一次冲量，
现在改成「破韧期间每一次命中都推一次」。这同时让文献那条
**「后一次攻击的冲击覆盖前一次」**自然成立 —— 每次都用最新那一下的冲击，不做叠加。

`ImpactLevel` 就是文献「常用冲击类型」那张 0–9 表：无影响 / 微颤 / 轻击 / 击退 / 击飞，
外加 `(击退, 240, 300)` 这类**自定力值**的写法（超导那一行就是）。
**能不能打断动作由 `Controllable.applyHitControl` 按「打断强度 vs 抗打断系数」判**（见上节），
不再是一个写死的等级门槛。

**不是本模组的伤害源**（摔伤、火焰、别的模组）没有「冲击」这个概念，
统一按**微颤**处理：推不动东西，但破韧期间照样能打断普通敌人 ——
玩家与怪物走的是同一个入口，不会再各写一份判据。

重量读 `minegenshin:weight`（默认 100）。原版生物的重量以后按文献那张表逐个配。

「这一下是什么冲击」跟着伤害点走：`Hit.impact` → `HitImpact` → `ModDamageSpec`。
招式**不写**（`null`）时按攻击者的武器类型查 `WeaponPoiseTable`，再兜底到按攻击类型的
`defaultImpact`。所以角色技能代码仍然是**一行都不用改**。

## 首领与破绽窗口

首领（`ITeyvatBoss`）只覆盖**一个方法** `blocksControl` → `true`：**破韧也照样拦**，
连冻结一起免疫。所以它平时打不断、**破韧期间也打不断**，想给它一次控制只有两条路：

```java
// ① 破绽窗口：技能在「抬手 / 蓄力 / 收招 / 破盾瞬间」这一类时刻调一下
ControlService.openGap(boss, 20);   // 20 刻内，下一次命中从正常入口通过
// ② 直通口：条件已经达成，绕过入口直接执行内置效果（剧情、处决演出、调试）
ControlService.force(boss, ControlRequest.poise(0f, source));
```

窗口是**一次性**的：窗口期内第一次命中的打断一定成立（无视韧性与抗打断系数），
然后窗口就用掉了 —— 不会整个窗口期间被反复打断。`ControlService.hasGap` 可以查还剩没有。
窗口只认**命中类**请求，悬浮 / 聚怪那种每刻刷新的技能不会把它白白刷掉。

首领同时还有 `super_armor = PoiseTiers.BOSS_SUPER_ARMOR`（= 10），**削韧也被 ÷10**，
所以它更难被打空韧性条。原版三个 BOSS（末影龙、凋灵、监守者）归的是同一个档
（`PoiseTiers.isBossTier`）—— 项目里还没有实现 `ITeyvatBoss` 的实体。

「部分精英怪也这样」不用改代码：给那只怪配一个 3~5 的 `super_armor`
就得到「轻击打不断、击退/击飞才打断」的表现；
要更像首领（只能靠破绽窗口）就再配高一点并自己在技能里开窗口。

## 冻结：无视韧性，并直接破韧

用户口径「冻结可以无视韧性直接冻结，或者说直接破韧也行」。
它**和其他控制走同一个入口**（`ControlService.apply(entity, ControlRequest.freeze(frozen))`，
内置效果是 `Controllable.freezeAction`），区别只在判定：**冻结不经过「破没破韧」那一步**，
只问实体吃不吃冻结（`blocksControl` → 首领直接免疫）。解冻（`frozen = false`）永远放行，
不然目标会被永远冻住。

破韧那一半照旧：

```
冻住的那一 tick  → PoiseService.forceBreak(entity, "freeze")   ← 判为破韧
冻着的每一 tick  → PoiseService.pauseReset(entity, 5)            ← 破绽窗口不流逝
解冻             → 销账；下次再冻住就再破一次
```

一次冻结只破一次（否则冻住期间驻留会被反复重置，敌人永远在破韧状态）。
**冻结本身照常生效**，破韧只是它的副作用；开关 `poise.toml → freeze.force-break`
（默认开）关掉就完全回到旧行为。

## 聚怪为什么特殊：它自己会削韧

聚怪**不是**「破韧前也生效」的控制 —— 它和别的控制一样要等破韧。
它的特殊之处是**自带削韧**，于是「拉」这件事被拆成了三段：

```
目标进入范围
  → ① 先砸一笔高额「初始削韧」（每个目标每次进入范围只砸一次）
       └ 没打破 → 只削韧、不拉：它照常走、照常打，只是韧性条在掉
       └ 打破了 → ② 开始拉：按住它的移动 AI（MovementHold）+ 清掉水平冲量，
                    持续牵引同时把韧性恢复按住（强控）
```

「低等级聚怪拉不动硬怪」不是另一道门槛，而是**那笔初始削韧不够**：
第 N 档聚怪的初始削韧 = 第 N 档韧性条的长度（`PoiseTiers.profileOf(n).length()`），
所以等级 N 一次就能打破 N 档及更低档的目标。
档位来源见 `PoiseTiers.gatherResist`（原版普通生物 1 / 大体型 4 / 三 BOSS 与其它模组免疫）。
免疫的目标在入队时就被挡住，**任何等级都拉不动**。

牵引等级 4 档：`GatherPull.Level.of(int)` 自带钳位，配置写大了也不会崩。

## 两个通用技能节点

这两个节点不依赖任何角色，谁都能调（技能、怪物 AI、指令调试都行）。

### `GatherPull` —— 牵引

```java
AABB core  = AABB.ofSize(中心, 1.0, 2.0, 1.0);   // 牵引核心：往哪牵引（区域，不是点）
AABB range = AABB.ofSize(中心, 10.0, 6.0, 10.0); // 牵引范围：谁会被算进来

GatherPull pull = new GatherPull(owner, GatherPull.Level.L1, core, range)
        .withBlocksPerSecond(1.0 / 1.5)          // 1 格 / 1.5 秒
        .withPoisePerSecond(4f)                  // 牵引期间再持续削韧（可省）
        .withFilter(target -> !(target instanceof Player));  // 不拉玩家（可省）

pull.execute();                                  // 走一遍（每刻调就是持续聚怪）
GatherPull.Handle handle = pull.sustain(70);     // 或者交给节点自己每刻推进，70 刻后自动停
```

四个要点：

1. **先削韧，破了才拉**：未破韧时它只砸那一笔初始削韧，位移一点都不给。
   初始削韧每个目标**每次进入范围只砸一次**，离开范围后再进来会重新砸 ——
   所以持续使用要**复用同一个实例**（或者用 `sustain()`）。
2. **速度写「每秒几格」**：内部按 `perTick()` 换算成格/刻。给的是**位移**不是加速度 ——
   「每秒几格」是能看见的事实，而加速度的最终速度由摩擦、地面、AI 速度共同决定，换算不可控。
3. **`core` 是区域**：目标进了核心区就不再受力（不然一群怪会叠在同一个坐标上）；
   离得比一步还近时不越过边界，免得在边上反复抖。
4. **只给水平位移**：竖直交给重力与 AI，否则贴地怪会被吸进地里。「抬起来」是悬浮的事。

### `Levitate` —— 悬浮

```java
Levitate.lift(mob);                              // 抬 2.5 格、持续 3 秒
Levitate.lift(mob, 4.0, 100);                    // 抬 4 格、持续 5 秒
Levitate.field(owner, range, 2.5, 60);           // 范围内全部能抬的一起抬
```

返回 `false` 就是**门没过**（有盾 / 没破韧），此时它什么都没做 —— 未破韧的怪不会被抬起来，
只会照常掉血。这道门每刻重判：目标韧性中途恢复、或被套上护盾，悬浮**当场脱落**（它掉回去）。

高度取「开始悬浮时它所在的 Y + 高度」，所以山崖边和坑里的怪抬起来的手感一致。
落地前清掉坠落距离，不会按悬浮高度吃一次摔伤。

### 实战用法：星辉风旋（星璇）

`StellarVortexEntity` 在存在期间每刻调一次牵引：核心是自身周围 ±0.5 格的小盒，
范围就是它的影响范围（等级 3 起半径 5 → 7，节点会跟着重建），
**等级 L1（初始削韧 = 1 档韧性条长 = 100）、速度 1 格 / 1.5 秒、不拉玩家**。
初始削韧跟着档位表走，所以 1 档上调到 100 之后，L1 依旧**正好**打破普通怪（100），
这条耦合不用单独调 —— 要拉开差距就改 `GatherPull` 的等级，别去改档位表。
所以原版普通生物被它砸一下就破韧、随即被拖过去；砸不动的（大体型、BOSS）
只会掉一点韧性条，永远拉不走；有护盾的敌人连削韧都没有（护盾 = 霸体，聚怪整个无效）。

## 接入点（改东西时看这几处）

| 文件 | 干什么 |
|---|---|
| `mixin/mixins/LivingEntityHurtMixin` | 全项目伤害唯一收口：护盾之后调一次 `ControlService.onHit`（玩家与怪物同一条路） |
| `core/system/control/Controllable` | **控制唯一入口** + 实体内置方法（打断动作 / 击退 / 冻结的 NoAI） |
| `core/system/control/ControlRequest` | 一次控制请求长什么样（命中类 `hit` / 纯削韧 `poise` / 技能类 `skill` / 冻结 `freeze`） |
| `core/system/control/ControlService` | 命中入口 `onHit`、冲击读取 `impactOf`、静止窗口 `openStandStill`、破绽窗口、直通口 `force` |
| `mixin/mixins/MobServerAiStepMixin` | 通用打断的 **AI 闸门**：静止窗口里掐掉 `serverAiStep()`（AI 总闸以下一次全停） |
| `core/system/control/TickActionSuppressor` | 给「不走 AI 管线」的动作留的口子：写一个 mixin 实现它 + 注册进 `minegenshin.mixins.json`，核心代码不用动 |
| `mixin/mixins/CreeperFuseMixin` / `RavagerActionMixin` | 已经接好的两只：苦力怕引信、劫掠兽的三个计时器 |
| `core/system/poise/impact/ImpactSolver` | 破韧期间的重量判定与冲量施加（每次命中一次） |
| `core/system/poise/WeaponPoiseTable` | 逐武器基准削韧 + 硬直等级（改数值只改这一处） |
| `core/system/poise/ReactionPoiseTable` | 反应自带削韧 + 冲击（对敌 / 对角色两行） |
| `core/system/poise/PoiseFreezeBreak` | 冻结直接破韧（按实体记账，防重复破） |
| `mixin/mixins/LivingEntityHurtMixin` | 削韧值按攻击者的武器类型取（`getPoiseDamage(attacker)`） |
| `content/skill_node/GatherPull` | 聚怪：护盾门、免疫门、初始高额削韧、破韧后才拉 |
| `content/skill_node/Levitate` | 悬浮：门控、吊住目标、按住韧性恢复 |
| `core/system/control/MovementHold` | 破韧后「拉成绝对」这一步用的是它（长按住）；打断那 0.5 秒走的是静止窗口，不是它 |
| `content/entities/area/StellarVortexEntity` | 星璇的持续牵引参数（L1 / 1 格 1.5 秒） |
| `client/render/gui/hud/MobHealthBarHud` + `component/MobPoiseBar` | 血条下面那条削韧条 |

## 常见坑

| 现象 | 原因 |
|---|---|
| 客户端削韧条不动 | 改了 `PoiseState` 却没 `setData` 推一次（NeoForge 附件「原地改对象不算改」） |
| 敌人被打了却不掉韧性 | 有盾（霸体挡掉）、或者这一招的削韧是 0（反应类伤害目前就是 0） |
| 敌人破韧了却推不动、也打不断 | 没破韧（推开与打断都要求破韧）／有盾／目标的抗打断系数（`super_armor`）比这一下的打断强度高 |
| 首领怎么打都不被打断 | 刻意如此：`blocksControl` 破韧也拦 + `super_armor = 10`，只有破绽窗口（`ControlService.openGap`）那一次或直通口（`ControlService.force`）能生效 |
| 破韧的怪被打了还在挥刀 | 那个实体的 `stop()` 没被调到（别的模组实体整块跳过），或它的动作不在 GoalSelector 里 |
| 停掉了 Goal 却还在喝药 / 挥刀 | 那种动作写在 `aiStep` 里、不走 Goal（女巫喝药就是，她用的是自己的 `DATA_USING_ITEM`，`setNoAi(true)` 也拦不住）—— 先查闸门有没有按上（`ControlService.hasStandStill`），再在实体的 `cancelOngoingAction` 里取消 |
| 苦力怕被打了照样炸、劫掠兽照样咆哮 | 这两个的动作**不在 AI 管线里**（引信在 `Creeper.tick()`，计时器在 `Ravager.aiStep()`），AI 闸门拦不住，原版 `setNoAi(true)` 也拦不住。已在 `CreeperFuseMixin` / `RavagerActionMixin` 里接上；再遇到同类原版怪就照着写一个 `TickActionSuppressor` 的 mixin |
| 女巫被打断了却「还在喝、手上一直拿着药瓶」 | 只翻 `setUsingItem(false)` **不够**：主手那瓶药（原版只在喝完时清）与私有 `usingTime` 都还在，她下一 tick 就重新起手。现在 `cancelWitchDrink` 三件事一起做：翻标记 + 拿走主手的药 + 拆掉 `minecraft:drinking` 减速 |
| 打断之后立刻又起手 | 静止窗口太短（`HIT_TICKS`），或者目标在窗口外重新决策；窗口内是「闸门 + 每刻重取消」两层，本模组怪的自定义读条要覆盖 `cancelOngoingAction` 才会被一起按住 |
| 冻结对某只怪无效 | 那只怪的 `blocksControl` 是 true（首领默认如此）—— 冻结和其他控制共用这一条 |
| 覆盖了一个内置方法却连冻结一起没了 | 覆盖面配宽了：`blocksControl` 拦的是**所有**控制，含冻结 |
| 反应打上去不掉韧性 | `poise.toml → reaction.poise` 被关了，或者这个反应不在 `ReactionPoiseTable` 里 |
| 冻住了韧性条却没变化 | `poise.toml → freeze.force-break` 被关了 |
| 削韧条一出现就空 | 目标处于破韧驻留窗口，条被清空是刻意的表现 |
| 玩家死活不被打断 | 霸体强度堆高了，或者身上有盾 |
| 想让某只怪更难破韧 | 加 `poise` 属性调档位；**不要**去改 `PoiseService` 里的判断 |
| 想让某只怪韧性条更长 / 更短 | 设 `minegenshin:poise_max`（逐实例，基准长度，仍乘联机系数）；条长与档位是两件事，别只改 `poise` 就以为变长了 |

## 还没做的（按批次）

1. **牵引接进更多技能**：节点已经能用（星璇已接），但角色技能里的聚怪还没挂上去；
2. **悬浮接进技能**：`Levitate` 节点已就位，还没有哪个角色/怪物用它；
3. **数值填表**：`PoiseTiers.TIER_PROFILES` 的 1 档已定（100 / 5 / 5），
   2 与 3 档仍是没人用的占位；星璇的 `GATHER_POISE_PER_SECOND` 也还是占位；
   逐角色削韧要等角色自己配；`WeaponPoiseTable` 保持文献中位数未动；
4. **重量填表**：原版生物的重量现在统一 100，还没按文献逐个配（大体型应该更重）；
5. **「丰穰之核绽放」与「撞击」两行**：文献有、本项目还没有对应概念，暂时没进反应表；
6. **破绽窗口还没有调用方**：`ControlService.openGap` 已就位，等首领技能在「抬手 / 蓄力 /
   收招」这些时刻挂上去；精英怪要同样的表现得自己配高 `super_armor`；
7. **静止窗口是占位值**：`ControlRequest.HIT_TICKS = 10`（0.5 秒），随手感调 ——
   它决定的是「打断后多久不能立刻起手」，不是定身时长；
8. **精英怪的 `super_armor` 一只都还没配**：现在全员 1（首领 10），要配 3~5 才有
   「轻击打不断、击退/击飞才打断」的表现；
9. **`blocksControl` 目前只有首领这一档**：项目里还没有实现 `ITeyvatBoss` 的实体，
   所以现在真正走这条路的只有原版三 BOSS；
10. **后一次攻击覆盖前一次**：文献里连续受击时后一次的冲击会覆盖前一次 ——
    现在靠「每次命中都单独解算一次、不做叠加」自然成立，没有专门的覆盖逻辑；
11. **护盾的破韧表现、玩家霸体强度来源**：`super_armor` 属性已经能堆，但还没有装备/天赋去给。
12. **`cancelOngoingAction` 只覆盖了原版通用动作**：`stopUsingItem` + 挥击 + 女巫喝药，
    再加 `TickActionSuppressor` 接进来的苦力怕与劫掠兽。本模组怪的自定义读条 / 蓄力状态机
    还没在自己的 `Controllable` 实现里覆盖它；
    原版还有几只的动作同样在 AI 管线之外、**暂时没接**（需要时照 `CreeperFuseMixin` 补）：
    守卫者的激光束（伤害在 `Guardian.aiStep`）、幻翼的俯冲循环、末影人的入水传送；
13. **`poise_max` 还没有人真的去写**：属性与读取链路都已就位，但没有怪物属性表 /
    副本逻辑去给「不同环境下的同一只怪」配不同的上限。

## 26.2.0.2 变更：韧性模块化 + 方块韧性

- 实体/角色韧性改为模块 `minegenshin:toughness`，数据对象仍是 `PoiseState`；
  `PoiseService.get/peek/push` 走 `ElibModuleHosts`，对外行为不变；
- 方块韧性是新模块 `minegenshin:block_toughness`，存在区块瞬态区：
  左键点方块按当前武器普攻削韧扣一笔；动作系统伤害点（普攻 / 重击 / 下坠 / 战技 / 爆发）
  按这一下实际算出来的削韧值扣（技能写过 `withPoiseDamage` 就用技能的值）；同一格同一刻只算一笔；
  归零按「钻石镐等价」破坏（掉落判定走 `BlockDropsEvent`，可被取消）；
- 换算：基础值 = 硬度 × 179（大剑两刀消耗石头 80%），再乘配置倍率
  `minegenshin/poise.toml → block.hardness-multiplier`（默认 3）；
- 判定时机：按**附着 / 迁移之前**的方块状态算这一下的韧性 —— 水在冻结前是流体、不参与，
  所以冰元素打水面只结冰，不会顺手把冻出来的浮冰打碎；
- 不参与：显式 `-1`、硬度 ≤ 0、空气、所有流体（水、岩浆、其它模组的 `LiquidBlock`）；
- 浮冰（`minecraft:frosted_ice`）照常参与，不管它是冻结反应生成的还是别的来源；
- 表现：复用原版裂纹（`destroyBlockProgress`），进度按已消耗比例推进。

模块层说明见 `docs/systems/module-system.md`。
