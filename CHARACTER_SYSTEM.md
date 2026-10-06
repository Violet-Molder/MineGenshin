# 角色系统详解（以薇斯娜为例）

> MineGenshin 26.2 · NeoForge 26.2.0.88 · GeckoLib 5.5.6 · Java 25
> 覆盖：**动作系统 / 技能系统 / 渲染系统 / 骨骼替换**
> 本文是唯一的主文档，`RENDER_SYSTEM.md` 只作为指向本文的入口保留。

---

## 目录

- [0. 怎么读这份文档](#0-怎么读这份文档)
  - [0.1 四层结构](#01-四层结构)
  - [0.2 全局数据流](#02-全局数据流)
- [第一部分 · 使用层（改数值就能看到效果）](#第一部分--使用层改数值就能看到效果)
  - [1.1 动作时序 —— VesnaResources.ACTION_DATA](#11-动作时序--vesnaresourcesaction_data)
  - [1.2 技能数值 —— VesnaTalent 的数据表与 TOML](#12-技能数值--vesnatalent-的数据表与-toml)
  - [1.3 两个开关 —— character.toml](#13-两个开关--charactertoml)
  - [1.4 骨骼替换 —— CharacterBoneMount](#14-骨骼替换--characterbonemount)
  - [1.5 按键](#15-按键)
  - [1.6 加一个新角色要动哪几行](#16-加一个新角色要动哪几行)
- [第二部分 · 配置层（每个角色写一次）](#第二部分--配置层每个角色写一次)
  - [2.1 VesnaResources](#21-vesnaresources)
  - [2.2 VesnaAnimations](#22-vesnaanimations)
  - [2.3 VesnaTalent](#23-vesnatalent)
  - [2.4 CharacterAnimationRegistry](#24-characteranimationregistry)
- [第三部分 · 动作系统（框架层）](#第三部分--动作系统框架层)
  - [3.1 一次普攻的完整逻辑链](#31-一次普攻的完整逻辑链)
  - [3.2 四个计时器与优先级](#32-四个计时器与优先级)
  - [3.3 前摇的两种：三窗口与打断规则](#33-前摇的两种三窗口与打断规则)
  - [3.4 连击窗口与收尾动画](#34-连击窗口与收尾动画)
  - [3.5 定身怎么实现](#35-定身怎么实现)
  - [3.6 动画保护](#36-动画保护)
  - [3.7 远端同步](#37-远端同步)
  - [3.8 关掉动作系统时发生什么](#38-关掉动作系统时发生什么)
  - [3.9 出手前置判断：动画之前的那一次检查](#39-出手前置判断动画之前的那一次检查)
- [第四部分 · 技能系统](#第四部分--技能系统)
  - [4.1 天赋回调契约](#41-天赋回调契约)
  - [4.2 服务端路由 ActionManager](#42-服务端路由-actionmanager)
  - [4.3 ActionState 的计时语义](#43-actionstate-的计时语义)
  - [4.4 薇斯娜的数值](#44-薇斯娜的数值)
  - [4.5 薇斯娜各技能逐条](#45-薇斯娜各技能逐条)
  - [4.6 伤害结算链](#46-伤害结算链)
  - [4.7 元素附着与衰减](#47-元素附着与衰减)
  - [4.8 冷却与能量](#48-冷却与能量)
  - [4.9 索敌与技能实体](#49-索敌与技能实体)
  - [4.10 辉映·星烁（星扩散 / 星超导）](#410-辉映星烁星扩散--星超导)
  - [4.11 户口与状态：两件不同的事](#411-户口与状态两件不同的事)
  - [4.12 整肃与大权（薇斯娜突破 1）](#412-整肃与大权薇斯娜突破-1)
  - [4.13 角色自带位移 vs 通用位移（申鹤 E 突刺的两个坑）](#413-角色自带位移-vs-通用位移申鹤-e-突刺的两个坑)
  - [4.14 长按（E hold）要同时满足三件事](#414-长按e-hold要同时满足三件事)
  - [4.15 领域实体（AreaEntity）的寿命：为什么曾经永远不会消亡](#415-领域实体areaentity的寿命为什么曾经永远不会消亡)
  - [4.16 天赋等级：手动次数是唯一可写的东西](#416-天赋等级手动次数是唯一可写的东西)
  - [4.17 满命「翔风剑·变移」怎么实现的](#417-满命翔风剑变移怎么实现的)
  - [4.18 角色效果（buff）的存档：实例 id ≠ 注册名](#418-角色效果buff的存档实例-id--注册名)
- [第五部分 · 渲染系统](#第五部分--渲染系统)
  - [5.1 渲染接管链路](#51-渲染接管链路)
  - [5.2 路径解析规则](#52-路径解析规则)
  - [5.3 动画控制器在渲染里的位置](#53-动画控制器在渲染里的位置)
  - [5.4 渲染层机制](#54-渲染层机制)
- [第六部分 · 骨骼替换](#第六部分--骨骼替换)
  - [6.1 原理](#61-原理)
  - [6.2 GeckoLib 5 的骨骼生命周期](#62-geckolib-5-的骨骼生命周期)
  - [6.3 一次替换的完整逻辑链](#63-一次替换的完整逻辑链)
  - [6.4 替换多个骨骼](#64-替换多个骨骼)
  - [6.5 从其他位置取内容](#65-从其他位置取内容)
  - [6.6 从源模型里挑骨骼（拆分剑身/剑鞘）](#66-从源模型里挑骨骼拆分剑身剑鞘)
  - [6.7 失败时的结果（重要）](#67-失败时的结果重要)
  - [6.8 常见坑](#68-常见坑)
- [第七部分 · 底层（写好就不怎么需要动）](#第七部分--底层写好就不怎么需要动)
  - [7.1 包结构](#71-包结构)
  - [7.2 网络层清单](#72-网络层清单)
  - [7.3 GeckoLib 5 集成要点](#73-geckolib-5-集成要点)
  - [7.4 关键类索引](#74-关键类索引)
- [第八部分 · 索敌与近战突进](#第八部分--索敌与近战突进)
  - [8.1 为什么要重做索敌](#81-为什么要重做索敌)
  - [8.2 索敌策略：能打谁](#82-索敌策略能打谁)
  - [8.3 软锁定：迟滞与丢锁](#83-软锁定迟滞与丢锁)
  - [8.4 每一招的交战形态（Engagement）](#84-每一招的交战形态engagement)
  - [8.5 一次攻击的完整流程](#85-一次攻击的完整流程)
  - [8.6 转向：快，但不瞬移](#86-转向快但不瞬移)
  - [8.7 突进期间动画怎么处理](#87-突进期间动画怎么处理)
  - [8.8 伤害与音效为什么按「到位」起算](#88-伤害与音效为什么按到位起算)
  - [8.9 位移：有目标就不推了](#89-位移有目标就不推了)
  - [8.10 召唤物索敌模式](#810-召唤物索敌模式)
  - [8.11 手感调参速查](#811-手感调参速查)
  - [8.12 追击可行性：追得到才追](#812-追击可行性追得到才追)
  - [8.13 保护性原则：停下来就开打](#813-保护性原则停下来就开打)
- [第九部分 · 资产路径与图标](#第九部分--资产路径与图标)
  - [9.1 统一布局](#91-统一布局)
  - [9.2 为什么需要自己扫资源](#92-为什么需要自己扫资源)
  - [9.3 路径规则工具](#93-路径规则工具)
  - [9.4 图标解析](#94-图标解析)
  - [9.5 迁移清单](#95-迁移清单)
  - [9.6 一个角色要用自己的一整套资源](#96-一个角色要用自己的一整套资源)
  - [9.7 数据生成：物品模型与物品定义](#97-数据生成物品模型与物品定义)
- [第十部分 · 多文件动画 / 第一人称 / 音效编排](#第十部分--多文件动画--第一人称--音效编排)
  - [10.1 多文件动画](#101-多文件动画)
  - [10.2 第一人称：一个开关，两套做法](#102-第一人称一个开关两套做法)
  - [10.3 第一人称动画怎么写（Blockbench + GeckoLib）](#103-第一人称动画怎么写blockbench--geckolib)
  - [10.4 音效：序列与随机的组合](#104-音效序列与随机的组合)
  - [10.5 音效文件放哪、怎么被加载](#105-音效文件放哪怎么被加载)
  - [10.6 设计思路：这几个功能为什么这么做](#106-设计思路这几个功能为什么这么做)
- [第十一部分 · 圣遗物与属性上限](#第十一部分--圣遗物与属性上限)
  - [11.1 圣遗物套装框架](#111-圣遗物套装框架)
  - [11.2 赤色证明（Scarlet Proof）](#112-赤色证明scarlet-proof)
  - [11.3 属性上限解放（AttributeFix 等价物）](#113-属性上限解放attributefix-等价物)
- [第十二部分 · 祈愿与命座](#第十二部分--祈愿与命座)
  - [12.1 祈愿（抽卡）](#121-祈愿抽卡)
  - [12.2 命座系统（06）](#122-命座系统06)
  - [12.3 抽到重复角色 → 升命座 / 满命补偿](#123-抽到重复角色--升命座--满命补偿)
- [第十三部分 · 武器](#第十三部分--武器)
  - [13.1 一把武器的组成（WeaponItem）](#131-一把武器的组成weaponitem)
  - [13.2 蝶变（五星单手剑）](#132-蝶变五星单手剑)
- [第十四部分 · 沃雅妮莎（Vodyanitsa）](#第十四部分--沃雅妮莎vodyanitsa)
  - [14.1 成长表（里程碑 + 线性插值）](#141-成长表里程碑--线性插值)
  - [14.2 技能实现](#142-技能实现)
  - [14.3 不产生通用位移](#143-不产生通用位移)
  - [14.4 突破天赋 1（突破--1）](#144-突破天赋-1突破--1)
- [附录 A · 已知问题与待办](#附录-a--已知问题与待办)
- [附录 B · 术语表](#附录-b--术语表)

---

## 0. 怎么读这份文档

### 0.1 四层结构

代码按「改动频率」分层，从上到下越来越少改：

| 层 | 你在这里干什么 | 代表文件 | 改动频率 |
|---|---|---|---|
| **① 使用层** | 调数值、调手感、开关功能 | `VesnaResources.ACTION_DATA`、`VesnaTalent` 数据表、`character.toml`、`CharacterBoneMount` | 天天改 |
| **② 配置层** | 加角色、写这个角色的动画目录 | `XxxResources`、`XxxAnimations`、`XxxTalent`、`CharacterAnimationRegistry` | 一个角色写一次 |
| **③ 框架层** | 一般不动；要加新机制时才碰 | `combat.animation.*`、`combat.action.*`、`client.render.character.*` | 偶尔 |
| **④ 底层** | 基本不动 | GeckoLib 集成、网络同步、mixins、包结构 | 几乎不动 |

**读法建议**：想调东西 → 只看第一部分；想知道「为什么按下左键会有伤害」→ 第三 + 第四部分；想做模型替换 → 第六部分。

### 0.2 全局数据流

```
                         ┌──────────────── 客户端 ────────────────┐
按键（GLFW 回调，比 tick 早）
   │
   ├──► ActionStateMachine ──► PlayerAnimationController ──► GeckoLib 动画
   │      （动画唯一权威）            （逐帧选动画）              （模型渲染）
   │            │
   │            └──► NetworkManager.animationStateRPCPacket ──┐
   │                                                          │
   └──► ActionServer.*RPC ──────────────────────────────┐     │
                                                        │     │
                         ┌──────────────── 服务端 ──────▼─────▼──┐
                         │  ActionManager（伤害唯一权威）          │
                         │      └─► ActionSet（Talent 从           │
                         │            XxxResources 构建）          │
                         │            └─► TalentBase 回调          │
                         │                  └─► ModDamageSpec      │
                         │                        └─► hurtServer   │
                         │                                        │
                         │  ServerAnimationTicker ──► 广播给其他玩家│
                         └────────────────────────────────────────┘
```

**核心约定：动画归客户端，伤害归服务端，两条链路互不阻塞。**
按键当帧就出动画（0 延迟）；伤害按服务端自己的时间轴结算。

---

# 第一部分 · 使用层（改数值就能看到效果）

## 1.1 动作时序 —— `VesnaResources.ACTION_DATA`

**文件**：`core/character/sword/vesna/VesnaResources.java`

这是整个动作系统**唯一的时序数据源**。客户端动画和服务端伤害都读它，改一处两端同时生效。

```java
        comboSteps.put(1, new ActionStep(
        "attack_1",                                          // ① 动画名
        40,                                                  // ② 总时长（刻）
        0,                                                   // ③ 保护期结束刻（0 = 整段可打断；见 3.3）
        2,                                                   // ④ 优先级
        List.of(new Move(0, 1.2)),        // ⑤ 位移
                List.of(new Hit(3, 2.0, 1.5, 1.0, 0.0, 6.0, false)), // ⑥ 伤害点
        List.of(new SoundRef(3, "vesna_attack_1", 1.0f, 1.0f)), // ⑦ 音效
        0, 2, 0, 8                                            // ⑧ 能量/冷却/连击窗口
));
```

### 字段逐条

| # | 字段 | 作用 | 落到哪 |
|---|---|---|---|
| ① | `animation` | 动画名，必须和 `vesna.animation.json` 里的 key 一致 | 客户端：`changeState` 用它；不存在会被[动画保护](#36-动画保护)拦下 |
| ② | `duration` | 动画总刻数 | 客户端 `animationTick`；服务端 `ActionState.totalDuration` |
| ③ | `protectDuration` | **执行期结束刻**：`[prepareTicks, protectDuration)` 期间不可打断 | 服务端 `isProtected()`；客户端拿它当硬直（`protectDuration <= prepareTicks` 时退化成 `min(duration/4, 8)`），见 [3.3](#33-前摇的两种三窗口与打断规则) |
| ③b | `prepareTicks` | **准备阶段长度**（吟唱/读条），这段可以被打断。`0` = 触发即执行 | 服务端 `isProtected()` 的下界；客户端 `lockDelayFrames`（锁延后这么多刻才开始） |
| ④ | `priority` | 优先级 | **目前只作数据记录**（客户端层级由动作类别固定给 2/3/4，见 3.2） |
| ⑤ | `moves` | `(delay, speed)` 位移，沿视线方向加冲量 | 服务端 `ServerActionExecutor` → `ServerTickScheduler` |
| ⑥ | `hits` | `(delay, forward, yOffset, damage, damageSp, scope, ignoreInvuln)` | 服务端：**只当时间轴用**，每个 hit 触发一次 `onActiveStart`（伤害在天赋里算）；客户端：取第一个 delay 决定「没模型时摆臂」的时机 |
| ⑦ | `sounds` | `(delay, name, volume, pitch)` | 客户端 `ResourceDrivenActionHandler` 排进延迟任务队列本地播放 |
| ⑧ | `skillCharge / finalCharge / cooldown / comboWindow` | 能量回复 / 冷却 / 连击窗口 | `comboWindow` 服务端用来判定连段是否过期；其余见 [4.8](#48-冷却与能量)（`cooldown` 目前无人读） |
| ⑨ | `attackRange` | 这一招够得着多远 | 客户端索敌/突进判定（`effectiveAttackRange()`） |
| ⑩ | `engagement` | **这一招的交战形态**：远程还是近战、要不要突进、索敌多远 | 客户端索敌与突进，见 [8.4](#84-每一招的交战形态engagement) |
| ⑪ | `moveAllowsVertical` | 这一段的 `moves` 位移**要不要带 Y 轴**（默认 **false** = 只走水平面） | 服务端 `ServerActionExecutor`，见下 |
| ⑫ | `dashStartDelay` | 突进**从第几刻才开始**（默认 0 = 按下即冻结冲出）；前几刻先播「起跳/变身」 | 客户端 `ResourceDrivenActionHandler`（延后调 `AttackApproach.begin`），见 [8.7](#87-突进期间动画怎么处理) |

> ⚠️ **`hits` 条数 = 打几下**。天赋不读 `Hit.damage`，所以 Vesna 普攻 4 段配了 4 个 hit 就是打 4 次。

### 已配置的分段

| 段/动作 | 动画名 | 时长 | 准备 | 执行期(到) | hits 的 delay |
|---|---|---|---|---|---|
| 普攻 1 | `attack_1` | 40 | 0 | — | 3 |
| 普攻 2 | `attack_2` | 48 | 0 | — | 3 |
| 普攻 3 | `attack_3` | 25 | 0 | — | 3, 6 |
| 普攻 4 | `attack_4` | 30 | 0 | — | 10, 12, 14, 16 |
| 普攻 5 | `attack_5` | 50 | 0 | — | 2, 4, 6, 8 |
| 普攻 6 | `attack_2`（复用第 2 段） | 48 | 0 | — | 5, 10, 15, 20 |
| 战技 E（巡风列装 / 翔风剑） | `skill_no_energy` 等 | 20 | **0** | **8**（三阶 16） | 6 |
| 大招 Q | `final` | 40 | 0 | 40 | 10 |
| 闪避 | `dodge_front` | 12 | 0 | — | 无 |

> **「执行期到 8」是什么意思**：第 0~8 刻不可打断（位移 + 动画 + 伤害点都在里面），
> 第 8~20 刻是后摇、随便取消。E 是唯一配了执行期的常规技能 —— 理由见
> [3.3](#33-前摇的两种三窗口与打断规则)；普攻和闪避都是 `0`（整段可打断，靠位移取消来连招）。
> 数据里的 `priority`（E=3 / Q=4）只是记录，客户端层级由动作类别固定给，见 [3.2](#32-四个计时器与优先级)。

普攻第 6 段**复用第 2 段的完整动画**（`attack_2`，48 刻）。这里踩过一次坑，值得记下来：

> 第 6 段原本用的是 `air_attack_long` + 收尾 `air_attack_end`。
> 后来发现那两个其实是**「第 2 段被拆成两半」的产物** ——
> `air_attack_long` 的 timeline 有 7 秒，但身体骨骼的最后一个关键帧在 **0.5 秒**
> （之后全靠 hold 撑），只有特效骨骼 `slash_b` 跑到 7 秒；`air_attack_end` 才是收尾。
> 拆开用就得额外接一次收尾状态，中间还夹一段很长的保持 ——
> 而第 6 段根本不需要那么长的恢复。**直接播完整的第 2 段动画**最省事：
> 动作连贯、`duration` 和动画长度天然对齐（48 刻 = 2.4 秒），也不需要收尾机制。

**收尾动画机制本身保留着**（`withComboEnd` + 那 12 刻短锁的修复，见 [3.4](#34-连击窗口与收尾动画)），
只是 Vesna 现在没有哪一段用它了。`air_attack_long` / `air_attack_end` 作为独立动画仍在文件里，
`SPECIAL_ANIMS` 里也留着名字 —— 以后想做「长攻击 + 独立收尾」的动作随时能接上。

目前**整张表都是近战**（默认形态）；给某一段改成远程就是链式加一个 `.ranged()`。

### 常见调整

| 想改什么 | 改哪 |
|---|---|
| 出手太慢 | 调小该段 `duration`，或调小 `hits[].delay` |
| 不能连招 / 连招太黏 | 硬直来自 `protectDuration - prepareTicks`，没配执行期时是 `min(duration/4, 8)`；想更灵敏就把 `duration` 改小 |
| 攻击没位移 | 加/改 `moves`（`speed` 是沿视线的冲量） |
| 想多打几下 | 往 `hits` 里加条目 |
| 闪避距离 | `dodgeStep` 的 `Move(0, 2.0)` |
| 大招定身太久 | `burstStep` 的 `protectDuration`（同时是硬直和定身） |
| 技能「CD 转了但没效果」 | 给那一段配执行期：`protectDuration` = 最后一个 `hits[].delay` 之后一点，见 [3.3](#33-前摇的两种三窗口与打断规则) |
| 想做有吟唱（可被打断）的技能 | `.withPrepareTicks(n)`：前 n 刻算准备阶段，之后才进执行期 |
| 这一招改成远程（不突进、够得着才锁） | 链式加 `.ranged()`，通常配 `.withAttackRange(x)` |
| 这一招不想突进 | `.withEngagement(Engagement.melee().withDash(false))` |
| 这一招索敌/突进参数不一样 | 见 [8.4](#84-每一招的交战形态engagement) 的字段全表 |
| 位移会「原地起飞」 | `moves` 默认只走水平面；要沿视线冲（上挑、跳劈）就 `.withVerticalMove(true)` |

## 1.2 技能数值 —— `VesnaTalent` 的数据表与 TOML

**文件**：`core/character/sword/vesna/VesnaTalent.java`

所有倍率表都是 `float[15]`，索引 = `技能等级 - 1`，由 `private static float at(float[] table, int skillLevel)` 做 1..15 钳制：

| 表 | 用途 | 首值 → 末值 |
|---|---|---|
| `SKILL_DAMAGE` | 首次 E 进巡风列装的 AOE | 0.40 → 0.95 |
| `XFJ_LV1` | 翔风剑一阶 | 与 `SKILL_DAMAGE` 完全相同 |
| `XFJ_LV2_MAIN` | 翔风剑二阶主伤害 | 0.60 → 1.425（= 1.50 × 一阶） |
| `XFJ_LV2_SWORD` | 翔风剑二阶灵剑 | 1.12 → 2.66（= 2.80 × 一阶） |
| `XFJ_LV3_SWORD` | 翔风剑三阶 4 段 AOE | 0.448 → 1.064 |
| `XFJ_LV3_FINAL` | 翔风剑三阶收尾灵剑 | 1.568 → 3.724 |
| `WIND_BELL_DAMAGE` | 风铃弹射物 | 0.104 → 0.247 |

普攻倍率**不在这张表里**，走 TOML：

```toml
# config/minegenshin/character.toml
[shenhe.talent.normal-attack]
    nab1 = 0.433   # 第 1 段基础倍率
    nap1 = 0.0482  # 第 1 段每级成长
    ...
    nab5 = 0.656
    nap5 = 0.0733
```

读取公式（`VesnaTalent.attack`）：

```java
multiplier = ShenheTalentConfig.getNABase(stage) + ShenheTalentConfig.getNAPerLevel(stage) * (naLevel - 1)
```

> ⚠️ `getNABase` 的 `switch` 只写了 `case 1..5`，**第 6 段落到 `default -> 0.0`**，所以薇斯娜第 6 段普攻基础倍率永远是 0（见附录 A）。

## 1.3 两个开关 —— `character.toml`

```toml
[character_system]
    # 使用专属模型 + GeckoLib 动画的角色 ID
    custom_model_characters = ["vesna"]
    # 启用完整动作系统（前摇 / 硬直 / 移动封锁 / 延迟伤害）的角色 ID
    action_system_characters = ["vesna"]
```

| 开关 | 开 | 关 |
|---|---|---|
| `custom_model_characters` | 模型替换 + 播放动作动画 | 原版渲染；按键后在**实际造成伤害那一帧**摆一次手臂 |
| `action_system_characters` | 完整前后摇、移动封锁、延迟伤害 | 所有按键及时响应，服务端当场结算 |

**这两个是临时脚手架**：现在的模型是借来的，等自有模型到位就把角色 ID 加进名单，以后可以删掉。

## 1.4 骨骼替换 —— `CharacterBoneMount`

**文件**：`core/system/combat/action/data/CharacterBoneMount.java`

在角色的 `RENDER_DATA` 里声明「把什么画到哪根骨骼上」：

```java
// 最简：武器槽的整把武器 → blade_right
CharacterBoneMount.of("blade_right")

// 带微调（偏移单位是像素，1 格 = 16）
CharacterBoneMount.of("blade_right")
        .withScale(0.45f)
        .withOffset(0f, 2f, 0f)
        .withRotation(0f, 90f, 0f)
```

| 参数 | 含义 | 怎么调 |
|---|---|---|
| `boneName` | 角色模型上的**目标**骨骼名 | 看 geo.json 的骨骼表 |
| `scale` | 统一缩放 | 先把 scale 调到 0.1 找到位置，再放大回来 |
| `offsetX/Y/Z` | 沿骨骼局部坐标平移（像素） | 武器插在手里偏了就用它挪 |
| `rotationX/Y/Z` | 绕局部轴旋转（度） | 朝向不对用它 |

完整用法见[第六部分](#第六部分--骨骼替换)。

## 1.5 按键

| 按键 | 动作 | 说明 |
|---|---|---|
| 鼠标左键 | 普攻 | 按下即出招；按住 20 刻触发重击 |
| **C** | 战技 | 短 CD ≠ 长 CD 的角色走按住判定（20 刻阈值），否则按下即放 |
| **X** | 闪避 | 按输入方向选 `dodge_front / back / left / right` |
| **R** | 大招 | |
| **H** | 抽卡 | 原来占着 R |
| G / V / O / U / B / N / K | 模式 / 切人 / 编队 / 角色信息 / 背包 / 升格 / 配置 | |

**为什么重写 `KeyMapping.setDown`**：`setDown` 在 GLFW 回调里、比 tick 更早被调用，在其中直接触发动作 = 「按下的当帧就出动画」。这是手感的关键，不要改成 tick 轮询。

`InputEvent.InteractionKeyMappingTriggered` 里屏蔽了原版左键（否则一出手就把脚下方块挖了）。

## 1.6 加一个新角色要动哪几行

1. `core/character/{weapon}/{name}/` 下写角色类 + `XxxTalent`
2. 写 `XxxResources.java`：`RENDER_DATA` + `ACTION_DATA`
3. 写 `XxxAnimations.java`：常态动画名 + 动作动画名单 + 过渡刻数（+ 音效表）
4. 在 `CharacterAnimationRegistry.registerAll()` 加一行
5. 放模型/动画 json，把角色 ID 加进 `character_system` 的两个名单

**不写 `ACTION_DATA` 也能动**：`TalentBase.buildDefaultActionSet` 会用
`CharacterActionData.fallback(maxCombo)` 兜底，普攻/战技/大招照常走天赋回调，只是时序是通用量级。

---

# 第二部分 · 配置层（每个角色写一次）

## 2.1 `VesnaResources`

`core/character/sword/vesna/VesnaResources.java` —— 两个静态常量，没有逻辑：

| 常量 | 内容 |
|---|---|
| `RENDER_DATA` | 模型路径 `default`、贴图 `default_texture.png`、动画路径 `vesna`、常态动画映射、体型缩放、骨骼挂点 |
| `ACTION_DATA` | 全部动作时序（见 1.1） |

## 2.2 `VesnaAnimations`

`core/character/sword/vesna/VesnaAnimations.java` —— 实现 `CharacterAnimations`：

| 方法 | 返回 | 说明 |
|---|---|---|
| `locomotion()` | `LocomotionAnims` | 14 个常态动画名（站/走/跑/倒走/蹲/蹲走/睡/爬/水面待机/水面走/水面倒走/游泳/跳/下落） |
| `specialAnims()` | `Set<String>` | **只用来决定过渡刻数**（名单内 0 刻硬切），不是存在性白名单 |
| `exitTransitionTicks()` | `int` | 常态动画互切的过渡刻数，Vesna 是 5 |
| `soundForState(String)` | `String` 或 null | 状态→音效；Vesna 目前是空表 |

> 名字写错不会导致模型坏掉 —— 存在性由 [`AnimationAvailability`](#36-动画保护) 直接查 GeckoLib 缓存判断。

## 2.3 `VesnaTalent`

`core/character/sword/vesna/VesnaTalent.java` —— 继承 `TalentBase`，负责：

- 覆盖 `getMaxCombo()` 返回 **6**
- 覆盖 `buildActionSet` 按 `stateKey` 换战技动画
- 实现 `attack` / `chargeAttack` / `elementalSkill` 三个回调
- 定义倍率表与 `VESNA_WIND_BELL_DECAY` 衰减组

**没有覆盖 `elementalBurst` 和 `dodge`** → 大招扣能量/CD 但无伤害（见附录 A）。

## 2.4 `CharacterAnimationRegistry`

`client/combat/action/CharacterAnimationRegistry.java` —— 角色登记表（客户端），由 `MinegenshinClient.onClientSetup` 调用：

```java
public static void registerAll() {
    CharacterActions.register(Vesna.ID, ResourceDrivenActionHandler.INSTANCE, VesnaAnimations.INSTANCE);
    registerPlaceholder("shenhe");
    registerPlaceholder("arlecchino");
    registerPlaceholder("columbina");
    registerPlaceholder("raiden_shogun");
}
```

所有角色共用同一个 [`ResourceDrivenActionHandler`](#31-一次普攻的完整逻辑链)，因为时序都在各自的 `ACTION_DATA` 里。
确有特殊编排时，把第二个参数换成自己的 `CharacterActionHandler` 实现。

---

# 第三部分 · 动作系统（框架层）

## 3.1 一次普攻的完整逻辑链

```
① 玩家按左键
   KeyMappingRegistry.ATTACK_KEY.setDown(true)        ← GLFW 回调，比 tick 早

② ActionStateMachine.tryAttack(player)
   ├─ currentPriority >= PRIO_FINAL(4) → 丢掉（大招霸体）
   ├─ canInterrupt(PRIO_ATTACK=2)？ 不行就丢掉
   └─ CharacterActions.getFor(player).attack(player)

③ ResourceDrivenActionHandler.attack(player)
   ├─ 取当前出战角色的 ActionSet（由 VesnaTalent 从 VesnaResources.ACTION_DATA 构建，按 stateKey 缓存）
   ├─ stage = ActionStateMachine.comboStage（越界钳到 1）
   ├─ def = set.getNormalAttack(stage)
   ├─ **canCast 前置判断**（不通过就整个丢掉：不推进连段、不播动画、不发请求，见 3.9）
   ├─ comboStage 推进（到顶回 1）
   └─ engageAndPlay(def, PRIO_ATTACK, 服务端请求)
        ├─ 索敌：按 step.engagement 算出的 Params 调 CombatTargeting.acquire
        │        已有锁且有效 → 保持；本招式范围内没有 → 沿用现有锁定
        ├─ 近战 + 目标在攻击距离外？
        │     是 → play(...) 起手（不排音效/摆臂）
        │          AttackApproach.begin（冻结动画 + 每 tick 滑向目标 + 突进）
        │          到位 → resumeFromApproach + 摆臂 + 音效 + 发服务端请求
        │     否 → AttackApproach.faceTarget（只转向，几刻滑过去）
        │          play(...) 照常排音效 + 发服务端请求
        └─ play(...)
             ├─ totalTicks   = step.duration
             ├─ lockDelay    = step.prepareTicks        （准备阶段：锁还没生效）
             ├─ lockFrames   = step.protectDuration - prepareTicks
             │                 没配执行期 → min(duration/4, 8)
             ├─ movementLock = 有执行期 ? 执行期本身 : min(lockFrames, 5)
             ├─ AnimationAvailability.existsFor(player, 动画名)？
             │     否 → 只打一条 warn，不切动画（避免模型变原始姿态）
             └─ ActionStateMachine.changeState(动画名, 2, totalTicks, lockFrames, movementLock, lockDelay)
                  ├─ 本地播状态音效 + sendStateToServer（给其他玩家看）
                  └─ 排「没模型时在伤害帧摆臂」的延迟任务
        （最后一段且有 comboEndAnim → queueFollowUpState(end, ticks)，在发请求前一起做）

④ 服务端 ActionManager.requestNormalAttack(sp, character, stage)
   ├─ resolveNextComboIndex：用客户端给的 stage（((stage-1) % size) + 1）
   ├─ request(player, character, def)
   │    ├─ 动作系统关闭 → fireImmediately：按 hits 条数当场全部结算完
   │    └─ 否则 → start()：new ActionState
   │              ├─ tick 0：onCastStart（触发即生效：换姿态/开模式/扣资源）
   │              └─ scheduleStepMovement（位移进 ServerTickScheduler）
   └─ ActionState 按 hits[].delay 计时

⑤ 到 delay → onActiveStart → VesnaTalent.attack(player, character, stage)
   ├─ 倍率 = ShenheTalentConfig.getNABase(stage) + getNAPerLevel(stage) * (naLevel-1)
   ├─ AreaEntityCollector 扫面前 2.5 格、半径 1.0 的盒
   ├─ ModDamageSpec.builder(NORMAL_ATTACK, ANEMO).multiplier(...).elementAmount(4f)...
   └─ target.hurtServer(serverLevel, source, 0f)     ← 0f 是哑值，真伤害在 mixin 里算

⑥ 客户端：animationTick 归零
   ├─ 有收尾动画 → 接上它（不回常态）
   └─ 否则 resetToDefault() → 回常态 + 发复位包

⑦ 服务端 ServerAnimationTicker 到期兜底复位（客户端崩了/丢包时）
```

## 3.2 四个计时器与优先级

**文件**：`client/combat/state/ActionStateMachine.java`

| 计时器 | 含义 | 谁来读 |
|---|---|---|
| `animationTick` | 动画剩余总刻数，归零自动回常态 | 渲染（决定是否还在动作状态） |
| `lockDelayFrames` | **准备阶段**剩余刻数（吟唱/读条） | `actionLocked()` / `movementFrozen()` —— 它 > 0 时两个锁都还没生效 |
| `actionLockFrames` | **执行期**剩余刻数（伤害已经/正在打出来） | `canInterrupt`（能不能接新动作） |
| `movementLockFrames` | 定身剩余刻数 | `MovementInputUpdateEvent`（冻结输入） |

| 优先级 | 值 | 谁 |
|---|---|---|
| `PRIO_NORMAL` | 1 | 常态（走跑跳蹲游） |
| `PRIO_ATTACK` | 2 | 普攻 / 战技 / 重击 |
| `PRIO_DODGE` | 3 | 闪避 |
| `PRIO_FINAL` | 4 | 大招（绝对霸体） |

> 优先级由**动作类别**固定给（调用方传 2/3/4），不取 `ActionStep.priority`。
> 原因：Vesna 资源里战技写的是 3，直接沿用会让「闪避(3) 打不断战技(3)」，与层级模型冲突。
> 现在层级只用来判「大招期间不可打断」，执行期是**任何层级都进不来**的（见 3.3）。

其它静态状态：`comboStage`、`comboWindowFrames`、`attackHoldTimer`、`isAttackButtonDown`、`skillHoldTimer`、`chargedAttackTriggered`、`CLIENT_TASKS`（延迟任务队列，1 tick 一步）。

## 3.3 前摇的两种：三窗口与打断规则

「前摇」这个词太笼统，把两件完全不同的事混在一起了。按**能不能被打断**拆开：

```
0                  prepareTicks              protectDuration        duration
├── 准备阶段（可打断）──┼──── 执行期（不可打断）────┼─── 后摇（可取消）───┤
   吟唱 / 读条            位移 + 动画 + 伤害点          效果已结算完
```

| 窗口 | 是什么 | 能被打断吗 | 数据字段 |
|---|---|---|---|
| **准备阶段** | 吟唱 / 读条 / 蓄力前段 —— 技能**还没开始发生** | ✅ 能。挨打、走开、跳跃、高级动作都能让它作废 | `prepareTicks`（0 = 没有这一段） |
| **执行期** | 技能**真正在发生**：位移、动画、伤害点全在这里 | ❌ 不能。任何来源、任何层级都不行 | `protectDuration`（结束刻） |
| **后摇** | 效果已经结算完，只剩收招表现 | ✅ 能。移动/跳跃/下一招都可以取消 | `duration - protectDuration` |

**关键认识**：翔风剑那种「按下去就是位移 + 动画，然后打伤害」的招式**没有准备阶段** ——
那个位移不是前摇，它本身就是技能在执行。所以它的 `prepareTicks = 0`、
执行期必须一路盖到伤害点之后。

```java
canInterrupt(请求优先级):
    动作系统关闭        → true（所有按键及时响应）
    当前 priority >= 4   → false（大招不可打断）
    actionLocked()       → false（执行期内谁都进不来 —— 连闪避/大招也不行）
    否则                 → true（准备阶段与后摇随便接）
```

> 以前这里还有一条 `请求优先级 > 当前 → true`（高级打断低级）。删掉它的原因很具体：
> 闪避(3) 能越过战技(2) 的执行期，翔风剑照样会被顶掉 ——
> 而「执行期不可打断」要的正是绝对。想取消？等它播完，后摇才是设计上留的取消窗口。

**服务端同一条规则**（`ActionManager.request`）：`current.isProtected()` 时**拒绝一切新请求**。
两端必须一致，否则会出现「客户端播了动画、服务端什么都没做」——
以前服务端写的是「保护期内仅技能可打断」，和客户端的层级判定正好错开。

**常态输入打断后摇**：`onClientTick` 里，若 `!movementFrozen() && !actionLocked() && animationTick > 0 && 不在常态`，检测到移动/跳跃/蹲下就 `resetToDefault()` 并给服务端发一次 `InterruptReason.JUMP`。

> 定身期间不做这个判定 —— 那期间输入被换成了 `FrozenInput`，直接读会把「玩家其实按着 W」误判成移动请求，一按攻击就被自己取消。

**突进的两个特殊处理**（都在 `ActionStateMachine`）：

1. **飞行期间锁不倒数**：突进属于执行期，最长能飞 `MAX_APPROACH_TICKS`(20) 刻。
   让锁在飞行途中流失，就会出现「人还在飞，锁已经过期」→ 一个移动输入把整刀连同
   还没发出的服务端请求一起吞掉。
2. **到位时执行期重新起算**：`resumeFromApproach(totalTicks, lockFrames, movementLock)`
   把动画时钟、硬直、定身一起拨回满值 —— **到位 = 执行期开始**，保护时间一个不少。
   不重算的话：飞 8 刻 → 落地时锁已经没了 → 刚接上的动画立刻被取消，伤害也没了。

**触发即生效**（`ActionDefinition.onCastStart` → `TalentBase.onCastStart`）：
在 tick 0 触发一次，早于任何伤害点。换姿态 / 开模式 / 扣资源这类东西放这里，
放伤害点上就会出现「CD 已经转了，但准备阶段被打断，模式没进去」——
薇斯娜巡风列装踩的就是这个坑，见 [1.1](#11-动作时序--vesnaresourcesaction_data) 的 E 行与 3.3 上面的说明。

## 3.4 连击窗口与收尾动画

**连击窗口**：`changeState` 时若优先级 ≥ 2，`comboWindowFrames = 30`；窗口**只在硬直结束后才开始流失**，归零时 `comboStage = 1`。
所以闪避/格挡期间连段不会掉。

**收尾动画**（`comboEndAnim`）：最后一段打完如果不接东西，模型会停在那一帧。用法：

```java
attack6Step.withComboEnd("air_attack_end", 60);   // VesnaResources
```

`ResourceDrivenActionHandler` 在**最后一段且动画真的存在**的情况下调
`ActionStateMachine.queueFollowUpState(name, ticks)`。状态机 `animationTick` 归零时：

```
有收尾 → startFollowUp()：changeState(收尾, 同优先级, 它自己的时长, FOLLOW_UP_LOCK_TICKS, 0)
                          收尾播完才 resetToDefault
无收尾 → resetToDefault()
```

任何新的 `changeState` / `resetToDefault` 都会清空收尾槽，所以不会串到别的动作后面。

> ⚠️ **收尾状态必须带一个短锁**（`FOLLOW_UP_LOCK_TICKS = 12`）。
> 原来这里传的是 `0`，结果是：收尾状态一建立，下一帧就被「1 级常态输入打断后摇」那条规则
> 顶掉了 —— 玩家打起来基本都按着 WASD，于是收尾动画**一帧都看不到**，
> 表现就是「第六段打完没有任何收尾」。
>
> 现在前 12 刻（0.6 秒）不会被移动取消（**但输入没被冻结，人照样能走**），
> 之后随便打断 —— 这就是「后摇可以被打断」，只是先演一下。
> 闪避（优先级 3）和大招（4）不受这个锁限制，随时能取消。

## 3.5 定身怎么实现

参考2 在 1.21.1 是「就地改 `Input.forwardImpulse`」，26.2 的 `Input` 已变成 record，改不了。所以这里换了一条路：

```
movementFrozen()（= lockDelayFrames <= 0 && movementLockFrames > 0）
   └─ MovementInputUpdateEvent（在 LocalPlayer.aiStep 里、applyInput 之前触发）
        ├─ event.getInput().keyPresses = Input.EMPTY        清跳跃/潜行/疾跑
        └─ player.input = FROZEN_INPUT                       换成一个 moveVector 恒为零的实例
   └─ 下一次 onClientTick 开头 restoreFrozenInput()：把玩家原本的输入实例还回去
```

> 定身现在是**延后启动**的：准备阶段（`lockDelayFrames > 0`）里两个锁都还没开始倒数，
> 所以吟唱期间人能走开（那正是打断吟唱的方式）。`prepareTicks = 0` 的动作不受影响 ——
> 它们的 `lockDelayFrames` 一直是 0，`movementFrozen()` 与旧写法等价。

`FrozenInput` 还重写了 `makeJump()` 为空 —— 原版自动跳跃会对共享实例调 `makeJump()`，它是就地置位，**不改的话被调用一次之后每次定身都会自己起跳**。

> 旧的「aiStep 后还原坐标」式移动锁（`MovementBlockMixin`）已删除：它会每 tick 把玩家拉回去，产生橡皮筋感。

## 3.6 动画保护

**文件**：`client/combat/state/AnimationAvailability.java`

GeckoLib 拿到**动画文件里没有的名字**时，`AnimationTimeline.create()` 返回 null，控制器那一帧不产生任何骨骼动画 →
模型以**原始姿态**渲染：所有部件、附着物、特效全露出来、角色呆站着，而且要等到下一个存在的动画才恢复。

处理方式是「验证 + 跳过」：

```
切动画前
   └─ AnimationAvailability.existsFor(player, 名字)
        ├─ 存在   → changeState 正常切
        └─ 不存在 → 打一条 warn，不调 changeState
                    伤害 / 位移 / 音效 / 服务端请求 全部照常
```

- **判定来源是 GeckoLib 烘培好的动画缓存**（`GeckoLibResources.getBakedAnimations()`），不是角色手写的名单
  —— 手写名单正是 `"burst"` 写错成 `"final"` 那次事故的来源。
- **三种「无法判断」的情况一律放行**：拿不到角色 ID、角色没有渲染定义、动画文件还没加载出来。
  只有「文件确实加载了、里面确实没有这个名字」才判不存在。
- **常态动画同样过这道关**：缺失退 `idle`，`idle` 也没有就保持当前动画不动 —— 覆盖「冲刺结束 / 上岸 / 落地」这类瞬时切换。
- 缓存按「动画文件对象身份」判断是否过期，资源重载（F3+T）后自动失效。

### 另一半：动画播完不能留空档（`thenPlayAndHold`）

上面那道关管的是「名字不存在」。这里管的是**名字存在、但动画播完了**：

**现象**：每段普攻的最后一刻会闪一下原始姿态（就是上面说的那种「全露 + 呆站」）。
**原因**：动作动画用 `thenPlay`（播一次即失效），而动作长度和状态时长是对齐的
（`attack_1` 动画 2.0 秒 = 这一段 40 刻）—— 动画在最后一刻播完，状态机要到**下一帧**
才切走，中间那一帧没有任何动画驱动模型，GeckoLib 就画原始姿态。

**修法**：`PlayerAnimationController.pickAction` 改用 **`thenPlayAndHold`**
（播完停在最后一帧，等状态机接手）：

- 段末不再有空档；
- 「hold on last frame」型的动画（`air_attack_long` 这类）现在真的会保持住 ——
  正好是配合收尾动画 `xxx_end` 用的那一类；
- 状态机切走时才换动画，其它逻辑一点没变。

> `ActionStateMachine` 里另有三行收尾日志
> （`[收尾] 排队 …` / `[收尾] 接上 …` / `[收尾] … 被 … 顶掉`），
> 专门用来定位「排了收尾动画却没播」：最常见的原因就是被下一次出手顶掉了
> （连点、或者长按触发了充能重击）。

## 3.7 远端同步

```
客户端切状态
   └─ NetworkManager.sendAnimationStateToServer(state, ticks)      ← LDLib2 RPC
        └─ 服务端 ServerAnimationTicker.apply(player, state, ticks)
             ├─ setData(ANIMATION_STATE_ATTACHMENT, new AnimationState(state, ticks))
             ├─ player.syncData(...)   → NeoForge 推给所有能看到该玩家的客户端（含本人）
             └─ 登记倒计时

其他玩家的客户端
   └─ PlayerAnimationController 读 player.getData(ANIMATION_STATE_ATTACHMENT)
   └─ AnimationStateSync.tickRemoteStateSounds()：状态变化时按状态名查本地音效表播放
```

- **本地玩家不走这条路**：`AnimationStateSync.stateOf` 对本地玩家直接返回 `ActionStateMachine.currentState`，0 延迟。
- **倒计时兜底**：客户端崩了或丢包时，`ServerAnimationTicker` 到期把状态复位，避免别人眼里永远定格在出招姿势。

## 3.8 关掉动作系统时发生什么

`action_system_characters` 不含该角色时：

| 位置 | 行为 |
|---|---|
| 客户端 `changeState` | 硬直与定身强制写 0 |
| 客户端 `canInterrupt` | 恒真，任何按键都能立刻接上 |
| 服务端 `ActionManager.request` | 走 `fireImmediately`：**按 `hits` 条数**当场把回调全部跑完，不进 `ActionState`；位移照排 |
| 索敌 / 转向 | 照旧（几乎不花时间，不影响「立刻响应」） |
| **突进** | **关掉** —— 突进会把服务端请求推到到位那一刻，和「没有延迟伤害」冲突 |
| 摆臂 | 该角色若也不在 `custom_model_characters`，按键后摆一次手臂当反馈 |

## 3.9 出手前置判断：动画之前的那一次检查

**问题**：本 MOD 是「客户端管动画、服务端管结算」，按键那一帧客户端就把动画切了，
请求才发出去。于是凡是**服务端会拒绝**的条件，都会变成
**「动画播了，但什么都没有」** —— 典型就是薇斯娜的特殊 E：能量不足 6 点时
服务端直接 return（能量不扣、技能不生效），客户端**照样把动画播完**。

**解法**：在播动画之前问一次 `PGCharacter.canCast(player, kind, skillTime)`。
不通过就**连动画都不播、请求也不发**，只给一条提示。

```
按键
  └─ ResourceDrivenActionHandler.attack / skill / ultimate / dodge / tickCharge
       ├─ canCast(...) 不通过 → 什么都不做（不播动画、不发请求）+ 节流提示
       └─ 通过 → engageAndPlay(...) → 动画 + RPC → 服务端（再判一次，它才是权威）
```

### 加一个新条件

判断写在**角色类**里（不是天赋里）：

```java
// Vesna.java —— 已经有的那条：巡风列装状态下每次 E 要 6 点能量
@Override
public boolean canUseElementalSkill(Player player, int skillTime) {
    if (windriderActive) return vesnaEnergy >= SPECIAL_SKILL_ENERGY_COST;
    return super.canUseElementalSkill(player, skillTime);
}
```

要加的是「全新的招式门槛」，就直接覆盖总入口：

```java
@Override
public boolean canCast(Player player, ActionKind kind, int skillTime) {
    // 例：必须拿着弓才能放这一招
    if (kind == ActionKind.ELEMENTAL_SKILL_TAP && !player.getMainHandItem().is(Items.BOW)) {
        return false;
    }
    return super.canCast(player, kind, skillTime);
}
```

再覆盖一条提示（不想要提示就留空）：

```java
@Override
public void sendCastFailedMessage(Player player, ActionKind kind) {
    player.sendSystemMessage(Component.translatable("message.minegenshin.need_bow"));
}
```

### 三条硬约束

| 约束 | 为什么 |
|---|---|
| **只读双端都有的数据** | 这个方法**会在客户端跑**，而 `talent` 字段在客户端是 `null`（反序列化不走子类构造器）→ 只能用 `data`（同步过的角色数据）这类双端状态，**不要**调 `getTalent()` |
| **不要有副作用** | 长按会每刻重试地调它（失败就下一 tick 再来），副作用会重复触发 |
| **提示走 `sendCastFailedMessage`** | 那边有 20 刻节流，直接在 `canCast` 里发消息会刷屏 |

### 两端同一套规则

客户端 `ResourceDrivenActionHandler.canCast(...)` 与服务端 `ActionManager`
（战技 / 大招）现在调的是**同一个方法** —— 一处规则，两端一致。
服务端那边仍然要判（客户端数据可能差一两刻：CD 是服务端设的、下一 tick 才同步过来），
它是权威；客户端这次判断只是为了**别把动画白播出去**。

---

# 第四部分 · 技能系统

## 4.1 天赋回调契约

**文件**：`core/character/talent/TalentBase.java`

不是接口，是带空实现的基类。可覆盖的成员：

| 成员 | 默认 | 说明 |
|---|---|---|
| `int getMaxCombo()` | 1 | 连段数 |
| `int getChargeTicks()` | 20 | 重击蓄力阈值（刻） |
| `void attack(Player, PGCharacter, int comboStage)` | 空 | 普攻 |
| `void chargeAttack(Player, PGCharacter)` | 空 | 重击 |
| `void elementalSkill(Player, PGCharacter, int skillTime)` | 空 | 战技（0 = 短按，1000 = 长按） |
| `void elementalBurst(Player, PGCharacter)` | 空 | 大招 |
| `void dodge(Player, PGCharacter)` | 空 | 闪避 |
| `void onCastStart(Player, PGCharacter, ActionKind)` | 空 | **触发那一刻**（tick 0）：换姿态/开模式/扣资源 |
| `ActionSet buildActionSet(PGCharacter, String stateKey)` | 委托给下面 | 状态 key → 动作集 |
| `protected ActionSet buildDefaultActionSet(PGCharacter)` | 见下 | 从 `ACTION_DATA` 生成 |

`buildDefaultActionSet` 的接线（每个动作都有 `onCastStart` + `onActiveStart`，`onComplete`/`onInterrupt` 从不设置）：

| ActionKind | 步骤来源 | `onCastStart`（tick 0） | `onActiveStart`（伤害点） |
|---|---|---|---|
| `NORMAL_ATTACK` | `combo.getStep(s)`，`s = 1..min(maxCombo, combo.maxCombo())` | `onCastStart(..., NORMAL_ATTACK)` | `attack(player, character, s)` |
| `ELEMENTAL_SKILL_TAP` | `skill.tap()` | `onCastStart(..., ELEMENTAL_SKILL_TAP)` | `elementalSkill(..., 0)` |
| `ELEMENTAL_SKILL_HOLD` | `skill.hold()`（为 null 就没有） | 同上（HOLD） | `elementalSkill(..., 1000)` |
| `ELEMENTAL_BURST` | `burst.step()` | 同上（BURST） | `elementalBurst(...)` |
| `DODGE` | `dodge.step()` | 同上（DODGE） | `dodge(...)` |
| `CHARGED_ATTACK` | **`skill.tap()`**（复用战技段） | 同上（CHARGED_ATTACK） | `chargeAttack(...)` |
| `PLUNGING_ATTACK` / `SPECIAL` | 从不接线 | — | — |

`actionData == null` 时用 `CharacterActionData.fallback(maxCombo)` 兜底，避免动作全部失效。

`PGCharacter.getActionSet(player)` 按 `getActionStateKey(player)` 缓存构建结果 —— Vesna 用它做「巡风列剑阶数不同、战技动画不同」。

## 4.2 服务端路由 `ActionManager`

**文件**：`core/system/combat/action/ActionManager.java`

```java
request(player, character, def):
    动作系统关闭              → fireImmediately(def)，return true
    换角色                    → 打断旧动作 + resetCombo
    当前动作未结束：
        isProtected()         → return false（执行期内不接受任何新动作，与客户端 canInterrupt 一致）
        否则（准备阶段/后摇）  → 打断 +（非连招才）resetCombo
    start(player, character, def)
```

- `start` → `new ActionState(def, ctx)`，构造器里先触发 **`onCastStart`（触发即生效）**，
  再触发 delay=0 的 hit；服务端另外排位移。
- `fireImmediately`（动作系统关闭）→ 同样先发一次 `onCastStart`，再按 `hits.size()` 次调用 `onActiveStart`（空 hits 则 1 次），位移照排。
- `resolveNextComboIndex`：`requestedStage >= 1` 时用客户端给的段数取模；否则走服务端自己的 `lastComboIndex` + `comboWindow` 判定。
- `requestDodge`：无 CD/能量门槛，直接进 `request`。
- `interrupt(reason)`：`SWITCH_CHARACTER | DEATH | JUMP` 是强制打断（并清 `activeCharacter`）；其余只在非执行期生效。

## 4.3 `ActionState` 的计时语义

**文件**：`core/system/combat/action/ActionState.java`

- 构造：`totalDuration = step.duration`、`protectDuration = step.protectDuration`、
  `prepareTicks = step.prepareTicks`、`hitDelays[] = step.hits[].delay`（保持列表顺序）。
- 构造器按顺序做两件事：**先 `onCastStart`（tick 0，触发即生效）**，再 `checkHits()`（delay=0 的伤害）。
- **每个 hit 触发一次 `onActiveStart`**，没有 per-hit 参数 —— 所以天赋里的逻辑会整段重跑。
- `hitDelays` 为空时（如闪避）走 `firesWithoutHits`，**在 tick 0 触发一次**，否则回调永远不会被调用。
- `finish()` 触发 `onComplete`；`interrupt(reason)` 触发 `onInterrupt` 且**不会**触发 `onComplete`。
- `isProtected() = protectDuration > prepareTicks && prepareTicks <= tickCount < protectDuration` ——
  只有**执行期**不可打断，准备阶段（`tickCount < prepareTicks`）照旧可以被打断，见 [3.3](#33-前摇的两种三窗口与打断规则)。

## 4.4 薇斯娜的数值

**`Vesna.java`**

| 常量/字段 | 值 |
|---|---|
| `ID` / `UID` | `"vesna"` / `115001` |
| `WIND_RIDER_DURATION_TICKS` | 300（15 秒） |
| `ENERGY_PER_QIQI` | 6 |
| `SPECIAL_SKILL_ENERGY_COST` | 6（一层剑气） |
| `WIND_RIDER_ENTER_ENERGY` | 12（开 E 给两层） |
| `vesnaMaxEnergy` | 18（**角色自己的剑气池，和大招能量无关**） |
| 战技 CD / 大招 CD / 大招能量 | 360 / 200 / 80 |

同步字段（`@DescSynced @Persisted`）：`vesnaEnergy`、`vesnaMaxEnergy`、`windriderActive`、`windriderRemainingTicks`、`xiangfengJianLevel`、`lv3UsesInWindrider`。

**不同步**：`entryCast`（这一刀是不是「进入巡风列装」的那一下）。
它是纯服务端运行时状态：触发那一刻由 `onCastStart` 置位、伤害点由 `elementalSkill` 消费，
故意不加注解，不存档也不发包。

`getActionStateKey(player)`：未激活 → `"default"`；激活 → `"windrider_" + xiangfengJianLevel`（只有 `_0/_1/_2` 可达）。
这个 key 决定战技用哪套动作定义 —— 这是**同一个技能按状态换动画**的机制。

## 4.5 薇斯娜各技能逐条

### 普攻 `attack(player, character, stage)`

1. 倍率 = `ShenheTalentConfig.getNABase(stage) + getNAPerLevel(stage) * (naLevel - 1)`
2. `AreaEntityCollector(level, player.position(), player.position() + look*2.5, inflate 1.0)` 扫目标，排除自己
3. `ModDamageSpec.builder(NORMAL_ATTACK, ANEMO).multiplier(multiplier).elementAmount(ULTRA_STRONG=4f).attackerCharacter(character)`
4. `target.hurtServer(serverLevel, source, 0f)`
5. 服务端 + 处于巡风列装：按段数生成风铃弹射物 —— 第 1/2/4/5 段各 1 个、第 3 段 2 个、第 6 段 3 个，每个 `vesna.addEnergy(1)`

> 因为每个 `hit` 都会重跑这个回调，实际生成数量要乘以该段的 hit 条数（见附录 A）。

### 重击 `chargeAttack`

`TargetSeeker(player, 10.0, LINE_OF_SIGHT)` 找主目标 → **找不到就整段无事发生** →
以主目标为中心半径 1.5 的 AOE，用 `ModDamageSpec.stellarDirect(STELLAR_SWIRL_ICE, CYRO, 1.0f, 0.5f)` +
`setStellarContributors(List.of(character))`；巡风列装下再补 2 个风铃。

### 战技 `elementalSkill(player, character, skillTime)`

分两支（`skillTime` 参数其实没被读），但**分哪一支是在「触发那一刻」定的，不是在这里**：

**触发那一刻**（`VesnaTalent.onCastStart`，tick 0）：
未激活 → `activateWindriderMode()`（active=true、剩余 300、能量=12、阶级=0、三阶次数=0）
+ `markEntryCast()` + 提示「§a进入巡风列装」；已激活 → 什么都不做（模式里的每一刀不用重新进）。

> **为什么模式必须在触发时进**：CD 是在服务端受理请求那一刻就设的。
> 原来模式在伤害点（第 6 刻）才开，中间那 6 刻被打断就变成
> 「CD 转了、模式没进去」—— 技能白按。现在受理 = 进模式 = 设 CD 同一刻，
> 且执行期（`protectDuration = 8`）盖住伤害点，谁也打断不了。

**伤害点（第 6 刻）** `elementalSkill(...)`：

- `consumeEntryCast()` 为 true（这一刀是入门刀）→ 只结算入门 AOE：
  以视线前方 2 格为中心、半径 2.5，倍率 `SKILL_DAMAGE`、附着 `WEAK(1f)`。**不碰能量、不推阶级。**
  （判断依据是触发时打的标记，不是 `isWindriderActive()` —— 后者那时早就 true 了，
  用它会把入门刀误当成翔风剑。）
- 否则（翔风剑）→ `consumeEnergy(6)` → `castLevel = getXiangfengJianLevel()`
  → `castXiangFengJian` → `advanceAfterCast` → 提示一/二/三阶。

`castXiangFengJian`（先找主目标，**找不到也照样扣能量**）：

| castLevel | 效果 |
|---|---|
| 0（一阶） | 以主目标为中心半径 2.0 的 AOE，倍率 `XFJ_LV1` |
| 1（二阶） | `XFJ_LV2_MAIN` AOE + 生成灵剑 `XFJ_LV2_SWORD` |
| 2（三阶） | 4 次 `XFJ_LV3_SWORD` AOE（只有第一次带 WEAK 附着）+ 收尾灵剑 `XFJ_LV3_FINAL` |

`advanceAfterCast`：三阶用满 3 次 → `exitWindriderMode()`；否则阶级 +1。
能量经济：进场 12 点 = 刚好放 2 次；第 3 次三阶打完退出。

**三阶的突进时序**（`VesnaTalent.THREE_STAGE_DASH_DELAY`）：三阶 E 在原神里是
「起跳 → 人消失 → 化作细长螺旋 → 朝目标突刺」，位移在变身之后，
所以 `windrider_2` 这一档的动作配了 `dashStartDelay = 8` —— 前 8 刻先播变身，
第 8 刻才冻结并冲过去。同时执行期自动算成
`8 + 最后一个伤害点(6) + 2 = 16` 刻，把「变身 + 突刺」整段罩住。
一阶/二阶保持默认（按下即冻结）。**换了真正的薇斯娜动画后按帧数改这一个常量**，填 0 即恢复原行为。

**星扩散判定**：`StellarGlimmer.hasSwirl(vesna)` ——
身上是**辉映·星扩散**时她的技能伤害转化成「星扩散-风」。
注意它只认星扩散（文案只写了星扩散），如果身上是**辉映·星超导**（优先级更高）就不转化，
见 [4.10](#410-辉映星烁星扩散--星超导)。

### 大招：跃起下坠刺击（60 能量）

Q 不再是普通大招，而是「跃起 → 摆姿态 → 锁落点 → 斜向下扎过去 → 落地范围伤害」：

```
0 ──── 8 刻 ──────────── 18 刻 ──────────── 40 刻
│  跃起 + 转身（动画）    │   下坠（代码）     │  后摇
                          ↑ 这一刻锁落点并发给服务端
                          ↑ 落地伤害：以落点为圆心方圆 12 格、倍率随大招等级（263.2%~625.1%）
```

| 环节 | 谁做 |
|---|---|
| 跃起 / 转成下坠姿态 | **动画**（`burst_dive`，独立文件，见 [4.5 上](#大招跃起下坠刺击60-能量)） |
| 世界坐标的抬升与下坠 | **代码** `client/combat/BurstDive.java` |
| 落点 | 下坠开始那一刻按「锁定目标的位置」算一次，**之后不再追踪**（敌人跑了就打空） |
| 落点传给服务端 | `ActionServer.sendBurstLandingToServer` → `BurstLanding` |
| 落地伤害 | `VesnaTalent.elementalBurst`（服务端，第 18 刻） |
| 环绕特效 | 动画里把模型自带的特效 cube 骨骼按圆周排布并自转 |

时序靠一个数对齐：`hits[].delay = 18 = jumpTicks(8) + diveTicks(10)`，
和 `ActionStep.withDiveBurst(8, 10, 2.5, 16.0)` 是同一个来源（`DiveBurst.landTick()`）。

**落地伤害**：以落点为圆心、方圆 `BURST_RADIUS = 12` 格内所有非施法者，
倍率走 **`BURST_SWORD_DAMAGE`（Lv1~Lv15：263.2% → 625.1%）**、强附着、大招衰减组别。
身上有**辉映·星扩散**时整段转成「星扩散-风」（走星烁管线：吃星烁加成与精通，不吃增伤/防御）——
**转星扩散时用的是同一个倍率**（文案里「灵剑伤害」与「灵剑星扩散伤害」两行同值，
所以 `dealAoeAnemoDamage` 的星烁分支里 `stellarCoefficient` 直接传 `multiplier`，
不再用以前那个写死的 0.5）。

其余数值：**元素能量 60**、**冷却 15 秒**（`Vesna` 构造器的 burst 冷却改成 `15 * 20`）。

**风元素微粒**（`content/skill_node/ElementalOrbSpawner`，`isParticle = true`）：

| 来源 | 规则 |
|---|---|
| 普通攻击（六段） | 每一段各有 `NORMAL_ATTACK_PARTICLE_CHANCE = 0.5` 的概率产 1 个 → 一整套期望 **3 个左右**（随机、不固定） |
| 翔风剑的**灵剑段** | 固定产 **1 个**（二阶第二段 + 三阶收尾灵剑都走 `spawnSpiritSword`，所以只在那一个方法里生成）；**不管有没有转化成星扩散都产** |

**闪避**：只有动画和 `Move(0, 2.0)` 位移（未变）。

## 4.6 伤害结算链

```
ModDamageSpec（倍率/元素/附着/衰减组/攻击者）
    └─ ModDamageSource.from(spec, player)
         └─ target.hurtServer(serverLevel, source, 0f)      ← 0f 是哑值
              └─ LivingEntityHurtMixin @HEAD, cancellable
                   ├─ 世界没被入侵 → 直接返回（原版按 0 伤害走一遍，等于无事发生）
                   ├─ HurtEntityHelper.calculateFinalModDamage(source, character, target)
                   │    ├─ 按 DamageType 分发到四条管线（见下表）
                   │    └─ 每次结算输出一条 DamageTrace 日志（公式 + 每个值）
                   └─ 应用：玩家走 PGCharacterData.hurtHP，其他实体 setHealth
                        + 伤害数字 + 广播事件 + 音效
```

### 四条管线（文件分工）

| 伤害类型 | 实现 | 公式 |
|---|---|---|
| `DIRECT` 直伤 | `attack/DirectDamagePipeline` | 衰减/附着/反应 → **基础 × 暴击 × 增伤 × 防御 × 抗性 × 反应** × 衰减 |
| `TRANSFORMATIVE` 剧变 | `attack/TransformativeDamage` | 等级系数 × 反应倍率 × 反应加成 × 抗性 |
| `LUNAR` 月曜 | `attack/LunarDamage` | 基础 × 提升 × 倍率 × 反应加成 × 抗性 × 暴击 × 擢升 |
| `STELLAR` 星烁 | `attack/StellarDamage` | 基础 × 反应加成 × 抗性 × 暴击 × 擢升 × 大权 |

其它零件：

| 文件 | 职责 |
|---|---|
| `attack/DamageZones` | **所有乘区**（基础/暴击/增伤/防御/抗性/精通/等级/擢升/大权），算的时候顺手写日志 |
| `attack/AttackerResolver` | 「这个实体对应哪个角色」—— 只有玩家挂角色，所以 `attacker != null` 就是「攻击者是角色」 |
| `damage/DamageTrace` | 一次伤害的完整记录（公式 + 每个值），`ENABLED = false` 可关 |
| `damage/CombatMath` | 纯数学：等级系数、防御区、抗性区、增伤区 |
| `attack/HurtEntityHelper` | 薄门面：分发 + 直伤特有的「先跑角色效果 `onAttacked` 钩子」 |

**关键点**：
- 真正算伤害的是这套管线，不是 `hurtServer` 的参数。
- `ModDamageSpec.stellarDirect(type, element, X, Y)` 里 **X（elementAmount）不被使用**，Y 才是 `stellarCoefficient`。
- **擢升区不再只是占位**：`DamageZones.elevationZone(attacker, branch)` = `1 + PGCharacter.getElevationBonus(分支)`，
  由角色按分支给（目前只有薇斯娜满命的「星扩散擢升 20%」）。
  它和「反应加成区」里那份星烁加成<b>不是一回事</b>：星烁加成与元素精通**加算**
  （`1 + EM + 星烁加成`），擢升是**独立乘区**，位置在暴击之后、大权之前。
  文案写「擢升」的加成走这里，写「反应伤害提升」的走 `getStellarGlimmerBonus`。
  只有 `STELLAR` / `LUNAR` 两条管线有擢升区（直伤公式里没有），所以它天然不会漏到普通直伤上。
- 整个伤害链路有硬门槛：`TeyvatWorldInvasion.get(level).isInvaded()`，未入侵世界一切伤害为 0。
- 直伤管线内部的顺序是 **衰减 → 过盾 → 附着 → 反应 → 乘区 → 免疫**：挂元素排在免疫之前，
  所以"免疫这个元素的伤害"不会顺带吞掉附着（元素生物就是靠这条拿到"免疫冰伤害但仍会挂冰"的表现）。
- 月曜**无视防御、不吃增伤区**；星烁**不吃增伤与防御**；只有直伤四个乘区全吃。

### 伤害日志（DamageTrace）

每次伤害结算一条，**4 行**：头部 + 缩写公式 + 展开公式 + 数值。每个大乘区一个【】。

```
[伤害] 直伤 | 攻击类型=普通攻击 元素=风 攻击者=薇斯娜 目标=僵尸 反应=无
  缩写公式 伤害 = 基础区 × 暴击区 × 倍率区 × 增伤区 × 防御区 × 抗性区 × 大权区 × 衰减区
  展开公式 伤害 = 【攻击力 × 攻击力倍率 + 附加伤害】 × 【1 + 暴击伤害】 × 【1 + 倍率提升】 × 【1】 × 【(攻方等级×5+500)/(攻方等级×5+500+守方防御)】 × 【1 - 原始抗性】 × 【1 + 大权加成】 × 【衰减伤害系数】
  数值　　 伤害 = 【1144.625 × 0.130 + 0】 × 【1 + 0.966】 × 【1 + 0】 × 【1】 × 【(90×5+500)/(90×5+500+815)】 × 【1 - 0.100】 × 【1 + 0】 × 【1】 = 141.714
```

四条规矩（第 4 条是最容易写错的一条）：

1. **每个大乘区一个【】**，展开公式与数值的【】个数与顺序完全对应；
2. **公式里不写逻辑判断**（没有 `?:`）—— 只写表达式，实际结果看数值；
3. **只有「值为 0 的属性项」可以省略**（基础区那 4 个属性里没参与的那些）；
   其余项哪怕没赋额外值（0 或 1）也要写出来（`1 + 倍率提升` 就写成 `1 + 0`）；
4. ⭐ **数值行必须是把数字代进公式，不是把算好的乘区结果填进去**：

   | 展开公式里的那一块 | ✅ 数值行 | ❌ 错误的写法 |
   |---|---|---|
   | `【1 + 暴击伤害】` | `【1 + 0.966】` | `【1.966】` |
   | `【1 - 原始抗性】` | `【1 - 0.100】` | `【0.900】` |
   | `【(攻方等级×5+500)/(攻方等级×5+500+守方防御)】` | `【(90×5+500)/(90×5+500+815)】` | `【0.538】` |
   | `【1 + 6×元素精通/(元素精通+2000) + 月曜专属加成】` | `【1 + 6×200/(200+2000) + 0.100】` | `【1.645】` |

   直接把结果填进去，看日志的人就**没法把数字和公式里的名字一一对应起来** ——
   而这正是这条日志存在的唯一意义。单值乘区（`反应倍率` / `衰减伤害系数` / `倍率`）
   本来就没东西可代，照写值即可。

四条管线都遵守这套（`DirectDamagePipeline` / `TransformativeDamage` / `LunarDamage` / `StellarDamage`）；
星烁、月曜是多人公式，还会多出「单人公式 / 加权公式 / 逐人数值 / 加权数值」几行，样例见 `DamageTrace` 类注释。

各乘区的公式清单见 `DamageZones` 的类注释。

## 4.7 元素附着与衰减

附着参数**只有一份真相**：`core/system/about/AttachmentProfile`。技能填的 `elementAmount()`
经 `AttachmentProfile.forAmount(x)` 映射成档次（这就是"元素量 → 附着档次"的唯一映射）：

| 元素量 x | 档次 | 初始附着量（损耗前） | 损耗后实际量（×0.8） | 每 20 刻衰减 | 时长 |
|---|---|---|---|---|---|
| 1.0 | `WEAK` | 1.0 | 0.8 | 0.084 | 9.5s |
| 1.5 | `MEDIUM` | 1.5 | 1.2 | 0.112 | 10.75s |
| 2.0 | `STRONG` | 2.0 | 1.6 | 0.133 | 12s |
| 4.0 | `ULTRA_STRONG` | 4.0 | 3.2 | 0.188 | 17s |

- 衰减速率不是另抄的常数，而是按原神公式 `t = 7 + 2.5x`、`v = 0.8x / t` 算出来的；
  `AttachmentType` 只是这些预设的薄封装（`getInitialAmount()` / `getDecayPer20Ticks()` 都从
  `AttachmentProfile` 读），**不要**在别处再存一套数字。
- `AttachmentProfile.PERMANENT`（衰减 0、无限时长、被消耗后周期补充）就是**环境自附着**：
  完整水源自带 1U 水、冰族自带冰（`BlockElementRules.selfAura` + `BlockSelfAura`）。
  "冰打水面能冻结"靠的正是水这边这份先手元素，而不是"状态对不上就改状态"。
  注意它**不过外部筛查**（水不收水，但水当然自带水），见「元素载体：可附着宿主」。
- **附加效果由子元素承载**：冰的减速与冻的禁 AI 都归**寒（`COLD`）**，冰/冻元素本体只负责附着。
  寒是**独立元素**（不并入 `mainElement` —— 一旦并入，它会被反应消耗与扩散传染当成冰处理），
  由 `ColdAura` 每 tick 伴随同步（有冰/冻就补、冰和冻都没了就撤），效果在 `ColdElement` 应用。
  **豁免只表达一次**：冰史莱姆不收寒 → 挂冰但不受冰影响。细节见 `docs/systems/element-host.md`。

附着的入口只有一个：`ElementalAttachmentHelper.attach(宿主, ...)`。宿主是
`core/system/about/host/ElementalHost` —— 生物 `EntityHost`、方块 `BlockHost`、出战角色 `CharacterHost`。
宿主上的三段判断互相独立：**收不收这次附着 / 收不收某个反应 / 吃不吃这个元素的伤害**。
两条由此确立的规则：拒收附着 = 不反应；免疫不吞附着。载体细节见「元素载体：可附着宿主」。

攻击侧还有一处必须知道的接线：原神模式每次攻击的**伤害点**会调
`attack/ElementalAttackSweep`，按这一招的攻击距离取一次范围，把范围内的可附着方块送进同一个附着入口
（实体走伤害管线顺带附着，方块直接附着 —— 方块没有血量）。它自带三道门禁：服务端 / 原神模式 / 角色元素非空。
附着进去之后反应由入口内部触发，所以环境挂水、自身附着、扩散传染与攻击附着的行为是同一套。

`DecayGroup(clearTimeTicks, 元素序列, 伤害序列, 削韧序列)`：
- `clearTimeTicks` 内没再命中就重置计数
- 序列是系数数组，`getCoefficient(i)` 在 `i >= 长度` 时返回 0
- `DEFAULT_ELEMENT = [1,0,0] × 8`（长度 24）= **第 1 次附着、之后每 3 次附着 1 次**

薇斯娜的 `VESNA_WIND_BELL_DECAY = DecayGroup(50, DEFAULT_ELEMENT, DEFAULT_DAMAGE, DEFAULT_POISE)`，
只被风铃弹射物使用 —— 即「风铃每 3 次命中附着 1 次」。

> ✅ 早期 `DecayCounterManager` 把非默认衰减组统统归到 `"custom"`，导致风铃和 E 共用同一个计数器（附录 A #11）；
> **现已改为每组各一份 key**：默认普攻组用 `"default"`，其余用 `"g" + identityHashCode`（见 `DecayCounterManager.groupKeyOf`）。
> 计数器只在运行时存活、不落存档，所以用身份哈希当身份是安全的。

## 4.8 冷却与能量

| 数据 | 字段 | 说明 |
|---|---|---|
| 战技 CD | `elementalSkillCooldownTick` | 每 tick 递减；归零时把 `elementalSkillStacks` 补满 |
| 大招 CD | `elementalBurstCooldownTick` | |
| 大招能量 | `currentObtainingEnergy` / `maxObtainingEnergy` | `addElementalEnergy` 会乘 `ER` 属性并钳制 |
| 层数 | `elementalSkillStacks` / `elementalSkillMaxStacks` | 默认 1/1 |

按键 → CD 的流程：

```
ResourceDrivenActionHandler.skill → ActionServer.triggerCharacterSkill(isLong)
   → ActionManager.requestElementalSkill
        ├─ canUseElementalSkill(player, skillTime)？ 否 → 提示并 return
        ├─ request(...)  启动动作
        │     └─ new ActionState → onCastStart（tick 0：进巡风列装 + markEntryCast）  ← 「触发即生效」
        └─ applyElementalSkillCooldown(player, skillTime)     ← 同一刻上 CD
```

薇斯娜把 `applyElementalSkillCooldown` 覆盖成「只在**进入巡风列装的那一刀**上写 `setElementalSkillCooldownTick(18*20)`」。

> **判断依据必须是「这一刀是不是入门刀」，不能是 `windriderActive`。**
> 模式现在在「触发那一刻」就开了（`onCastStart` 跑在 `request()` 里面，早于 `applyElementalSkillCooldown`），
> 到这里 `windriderActive` 已经是 `true` —— 用它判断会**整个跳过设 CD**，
> 连锁反应是：CD 没设 → `PGCharacterData` 不标 dirty → 整包同步不触发 →
> **客户端连模式/剑气都收不到**（能量条不出现、图标不显示 CD、还能直接再开一次技能）。
> 现在用的是 `Vesna.entryCast`（触发时打、伤害点消费）。

> **CD 与模式必须同一刻**：这两步都在服务端受理请求那一次调用里完成（先 `request`，再设 CD），
> 中间没有空隙。模式原来放在伤害点（第 6 刻）才开，才会出现「CD 转了、模式没进去」。
> 详见 [3.3](#33-前摇的两种三窗口与打断规则)。

### 客户端怎么拿到技能状态（两条同步路）

| 路径 | 内容 | 到不到客户端 |
|---|---|---|
| `syncRealtimeState()` → LDLib2 增量包（`minegenshin:character_sync`） | 只有真正变化的那几个字段 | ❌ **到不了**：客户端没绑 `ownerPlayer`，`handleCharacterSyncPacket` 直接返回；而且这个包名和「角色自己的字段」共用，索引空间不同，不能混用 |
| `PGCharacterData.dirty` → `CharacterTickHandler` 整包同步 | 整份角色 NBT（含子类的 `@Persisted`，即 `windriderActive` / `vesnaEnergy` / 阶级…） | ✅ **这条路才是通的** |

所以「想让客户端看到什么」= **标 dirty**。`PGCharacterData.tick()` 只在 CD 在转的时候标，
CD 一没设就再也不会标 —— 这正是上面那个连锁反应的中间一环。
薇斯娜的状态变化（开/退模式、剑气增减、阶级变化）统一走 `Vesna.syncSkillState()`：
常规同步 + `getData().markDirty()`，保证 HUD 在下一 tick 就拿到。

## 4.9 索敌与技能实体

| 工具 | 签名 | 说明 |
|---|---|---|
| `AreaEntityCollector(Level, Vec3 pos1, Vec3 pos2[, float inflate])` | `List<LivingEntity> execute()` | AABB 内活着的非旁观实体，**不排除施法者**，调用方自己过滤 |
| `TargetSeeker(Entity, double range, TargetingType)` | `LivingEntity execute()`（可能 null） | `TargetingType`：`LINE_OF_SIGHT` / `RADIUS`；`SourceType`：`CHARACTER` / `TRACKING_COOP` 等；`TargetFilter`：`ALL` / `NON_PLAYER` / `HOSTILE_ONLY` |
| `VesnaAttackProjectile.create(Level, Vesna, Vec3, int skillLevel)` | 弹射物 | 16 刻贝塞尔生成 → 0.8 速度追踪，命中即结算 `WIND_BELL_DAMAGE` 并消失 |
| `VesnaSpiritSwordEntity.create(Level, Vesna, Vec3 from, Vec3 to, float mult, float aoe, boolean stellar, float elementAmount)` | 灵剑 | 悬停 10 刻 + 飞行 6 刻，第 16 刻在 `to` 半径 `aoe` 内结算一次伤害后消失 |

## 4.10 辉映·星烁（星扩散 / 星超导）

**命名关系**（重要，别把「星烁」当成第三条反应）：

```
辉映·星烁（Radiance: Stellar Glimmer）   ← 统称
  ├─ 辉映·星超导（Radiance: Stellar Conduce）   优先级高
  └─ 辉映·星扩散（Stellar Swirl）              优先级低
```

| 规则 | 落在哪 |
|---|---|
| 同一角色身上两者**只能有一个**，星超导优先 | `RadianceStellarConduceEffect.onEffectAdded` 把星扩散摘掉；`RadianceStellarSwirlEffect.canApplyWith` 在已有星超导时**拒绝上场** |
| 「加星烁反应伤害」= 星扩散 + 星超导都加 | `ICharacterEffect.getStellarGlimmerBonus(branch)` 对两个分支返回同一个值 |
| 「只加星扩散」（薇斯娜天赋那一类） | 只对 `SWIRL` 返回非 0；薇斯娜的技能转化用 `StellarGlimmer.hasSwirl(...)` 判定 |
| 查询现在处于哪个分支 | `StellarGlimmer.branchOf / hasSwirl / hasConduce` |
| 反应加成区怎么算 | `DamageZones.stellarGlimmerReactionBonus` = 1 + 精通 + `StellarGlimmer.bonusOf(角色, 分支)` |

**分支 → 反应类型 → 抗性元素**（`StellarGlimmerBranch`）：

| 分支 | 反应类型 | 抗性按 |
|---|---|---|
| 星扩散 | `STELLAR_SWIRL_WIND` / `STELLAR_SWIRL_ICE` | 风 / 冰 |
| 星超导 | `STELLAR_CONDUCE_ELECTRO` / `STELLAR_CONDUCE_ICE` | 雷 / 冰 |

**给角色/效果加加成的两种写法**：

```java
// ① 文案写「星烁反应伤害提升 20%」→ 两个分支都给
@Override public float getStellarGlimmerBonus(StellarGlimmerBranch branch) { return 0.20f; }

// ② 文案只写「星扩散伤害提升 20%」→ 只给星扩散
@Override public float getStellarGlimmerBonus(StellarGlimmerBranch branch) {
    return branch == StellarGlimmerBranch.SWIRL ? 0.20f : 0f;
}
```

效果走 `ICharacterEffect`，角色天赋走 `PGCharacter.getStellarGlimmerBonus`（两边会在
`StellarGlimmer.bonusOf` 里相加）。

> **目前还没有角色会挂星超导** —— 接口、effect（`radiance_stellar_conduce` 已注册）、
> 互斥与优先级、加成分支都已经就位，等有角色了直接 `CharacterEffectHelper.addEffect` 即可。

## 4.11 户口与状态：两件不同的事

这两个概念以前被混成一个接口（`IStellarSwirlParticipant`，**已删除**），
结果是「能进星烁状态」被当成了「有转化能力」—— 一个只该拿加成、不该改反应的角色
也会把别人的反应转成星烁反应。现在拆成两个接口：

| | 户口天赋 | 状态 |
|---|---|---|
| 是什么 | **转化 + 系统自身的基础加成**（一般绑在同一个天赋里） | 辉映效果本身（加伤） |
| 接口 | `IStellarHousehold` | `IStellarStateHolder` |
| 管什么 | ① 把反应**转**成星烁反应（from 元素 → to 元素）② 星烁反应**基础伤害倍率**提升 | 只加伤（`getStellarGlimmerBonus(branch)`），**不改转化** |
| 例子 | 薇斯娜 `stellarHousehold()`：风 → 星扩散-风 + 攻击力档位基础加成 | 申鹤 `implements IStellarStateHolder`（只拿加成，不转化） |
| 判定 | `ReactionPriorityCalculator.hasStellarSwirlHousehold(level)` 扫全队 | `StellarGlimmer.branchOf / hasSwirl / hasConduce`（看身上有没有那个 effect） |

**关键结论**：*有户口的人不一定有状态，有状态的人不一定有户口*。
「转化」和「基础倍率提升」是户口天赋；「进入月曜 / 星烁状态并获得加成」任何人都可以，
但拿到状态不等于拿到转化。

```java
/** 户口：record(branch, fromElement, toElement, baseBonusMult) */
public record StellarHousehold(StellarGlimmerBranch branch, ElementType fromElement,
                               ElementType toElement, float baseBonusMult) {
    public boolean converts() { return fromElement != toElement; }
}

// 薇斯娜：风 → 星扩散-风，基础倍率 = floor(攻击力/100) × 0.7%（上限 14%）
@Override public StellarHousehold stellarHousehold() {
    return new StellarHousehold(StellarGlimmerBranch.SWIRL, ModElements.ANEMO, ModElements.ANEMO,
            stellarSwirlBaseBonusMult());
}
```

给伤害盖章的两条路（都在 `ModDamageSpec` 上）：

| 字段 | 谁盖章 | 落进哪个乘区 |
|---|---|---|
| `withStellarBaseBonusMult(m)` | **户口**天赋自己（`StellarGlimmer.swirlBaseBonusMult(level)`） | 基础区的「星烁基础倍率提升」 |
| `withSovereignty(b)` | 角色天赋按段决定（薇斯娜的整肃） | 大权区 |

## 4.12 整肃与大权（薇斯娜突破 1）

**整肃**是层数资源，**大权区**是它唯一的作用点。

| 规则 | 实现 |
|---|---|
| 层数怎么存 | `decreeTicks[6]`（`MAX_DECREE_STACKS`），**每层一个自己的剩余刻数**（400 刻），先叠的先掉 —— 文案写的是「每层独立计算持续时间」，一个总时长做不到 |
| 怎么叠 | `grantDecree()`：有空位用空位；**6 层满了就挤掉最早的那层**（剩余刻数最少的那格）→ 也就是「第 7 次放 E 刷新第 1 个」，本质是个容量 6 的**队列** |
| 什么时候**清空** | **只有「进入巡风列装」那一下**（= 施放元素战技「操典·制胜有道」）；以及**退场**（切到别的角色） |
| 什么时候**只叠不清** | 翔风剑（模式期内的特殊元素战技）：**先叠层、再结算伤害**，这一刀自己那层也算进去；**绝不清层** |
| 大招 | **先结算落地伤害、再叠 1 层**，模式外额外给 6 点剑气 |
| 加成 | `getSovereigntyBonus() = 层数 × 10%` → 大权区 = `1 + 层数 × 10%` |
| 作用范围 | **只有「灵剑」那几段**：翔风剑二阶第二段、三阶四段 AOE、三阶收尾灵剑、大招。其余招式（入门刀、一阶、二阶第一段）传 0 |
| 日志 | 叠层 `[整肃] 叠 1 层 → 当前 N 层（大权 +M%）`；清层 `[整肃] 清空（原 N 层）`；伤害日志里的 `大权区` 就是验算点 |

### 叠层与结算的先后（两次都踩过）

| | 顺序 | 为什么 |
|---|---|---|
| 翔风剑 | `grantDecree()` → 再 `castXiangFengJian(...)` | 反过来（旧的「打完才叠」）每次都比实际少一层 —— 表现就是「丢一段」：二阶第二段本该吃 2 层，实际只吃到 1 层 |
| 大招 | `dealAoeAnemoDamage(...)` → 再 `grantDecree()` | 大招是「凑到满层」的那一下；它自己不该吃这一层 |

这条「一前一后」是刻意不对称的，设计上让**第 6 层永远没有伤害吃得到**：
第六层只可能来自 ①三阶最后一段翔风剑（放完就退出巡风列装，且大招已经用过），
或 ②退出巡风列装之后的大招 —— 这两种情况下，下一次造成伤害之前玩家必然要按 E
重新进入巡风列装，而那一下会**清空整肃**。所以「层数上限 6」不会因为第 6 层
没法用而变弱，纯粹是个队列容量。

> 注意：这条结论只在「大招叠层在伤害之后」时成立。如果哪天把大招改成先叠层，
> 大招自己就会吃到第 6 层，第 6 层就从「永远用不到」变成「只有大招吃得到」。

⚠️ **踩过的两个坑**（都表现为「日志里大权区一直是 1」）：

1. `onCastStart` 曾经**无条件** `clearDecree()` —— 于是每次翔风剑在伤害点之前
   先把上一层清掉，`getSovereigntyBonus()` 永远是 0，伤害当然都是 1.0 倍。
   现在只有「入口那一刀」清层（进模式的分支），翔风剑分支直接 `return`。
2. 退场判定曾经比**对象引用**（`getCurrentCharacter() != this`）：角色列表反序列化后
   可能换实例，于是一边在场上一边被判定成退场，每刻清一次。
   现在按 `getCharacterUUID()` 比（见 `Vesna.tick`）。

### 命座对整肃/翔风剑的改动

| 命座 | 效果 | 落在哪 |
|---|---|---|
| C1 | 巡风列装下最高境界（三阶）翔风剑 **3 次 → 4 次** | `Vesna.maxLv3UsesInWindrider()`，`VesnaTalent.advanceAfterCast` 用它当上限 |
| C1 | 每次进入巡风列装后**首次**三阶翔风剑**不消耗剑气**（连 `canUseElementalSkill` 的门槛也放行） | `Vesna.lv3CastIsFreeNow()`（判据是 `lv3UsesInWindrider == 0`，不用新字段） |
| C1 | 巡风列装模式下造成的**星扩散反应伤害 +20%** | `Vesna.getStellarGlimmerBonus(SWIRL)` 覆写 → 进反应加成区里那份「星烁加成」 |
| C2 | 进入巡风列装时**直接获得满层整肃** | `Vesna.activateWindriderMode()` → `fillDecreeToMax()`（跑在 talent 清层之后，所以是「清完填满」） |
| C2 | **满层整肃**时攻击力 +40%（临时修饰符 `vesna_c2_atk`，每刻重算） | `Vesna.updateC2Bonus()`（在 `tick` 里，掉了层下一次 tick 自己摘掉） |
| C4 | 突破天赋「仪典·冬之凯风」（队伍元素构成给攻击力/元素精通）**效果 ×3** | `Vesna.updateA4Bonuses()` 里乘 `C4_WINTER_RITE_MULTIPLIER`（6%→18%、25→75） |
| C6 | 三阶翔风剑之后 5 秒内，普攻/E 点按变成**翔风剑·变移** | `Vesna.bianyiWindowEndTick` + `getActionStateKey` 返回 `"bianyi"`（见 4.17） |
| C6 | 薇斯娜造成的**星扩散反应伤害擢升 20%** | `Vesna.getElevationBonus(SWIRL)` → 独立擢升区（不是反应加成区） |

C2 两个效果都要求**已解锁突破天赋 1「仪典·春之行列」**（`Vesna.hasSpringRiteTalent()` =
`getAscensionPhase() >= 1`）—— 没解锁时即使 2 命也不生效。
C4 强化的那个天赋本身要求**突破 4**（`updateA4Bonuses` 里已有的门槛），所以 C4 也隐含这个前置。

## 4.13 角色自带位移 vs 通用兜底位移（申鹤 E 突刺的坑）

**现象**：申鹤 E 不放突刺了 —— 只剩一下通用的小幅前移（或者干脆不动）。

她的位移本来完全由自己的 `DashSystem` 负责（`RushesForward` 算方向 → 每刻推位置 +
沿途扫伤害）。移植之后这条路上**有两个坑，两个都得堵**：

### 坑 1：她自己的客户端突刺变成了死代码（主因）

`DashSystem` 的设计是「客户端按格推位置、服务端沿途扫伤害」。但移植后
**角色天赋只在服务端跑**：客户端只播动画 + 发包（`ResourceDrivenActionHandler.skill()`
→ `ActionServer.triggerCharacterSkill()`），`onActiveStart` 由服务端的 `ActionState` 触发。
于是 `ShenheTalent.elementalSkill` 里那个 `if (level.isClientSide()) DashSystem.startDash(...)`
**永远不会被执行** —— 人不动，只剩服务端在扫伤害。

**修复：给框架加一个「客户端本地钩子」**，让声明需要的角色在客户端也跑一次这一招的
`onActiveStart`：

```java
// PGCharacter：默认不跑，需要的角色自己打开
public boolean runsTalentOnClient() { return false; }

// Shenhe
@Override public boolean runsTalentOnClient() { return true; }

// ResourceDrivenActionHandler.engageAndPlay：出手那一刻（客户端）先本地跑一次，再发服务端请求
Consumer<LivingEntity> dispatch = serverCall == null ? null : target -> {
    fireLocalTalentHook(player, def);      // → 客户端侧 DashSystem.startDash(...)
    serverCall.accept(target);
};
```

她两端的 `elementalSkill` 于是各跑各的那一半：客户端推位置（表现）、服务端扫伤害
（判定），和移植前完全一致。

> ⚠️ **不要试图用「服务端设速度」来代替客户端位移**（我试过，是错的）：
> `setDeltaMovement` + `hurtMarked` 的位移由**摩擦、玩家输入、客户端预测**共同决定 ——
> 冲不到 10 格、到了还在飘，而且服务端的扫掠位置根本没动。
> 服务端驱动玩家位移的唯一正确姿势是「技能自己在客户端按格走」。

### 坑 1.5：突进必须「走满距离就立刻刹车」

`DashSystem` 现在<b>按距离走，不按速度走</b>：

```java
double step = Math.min(state.stepPerTick, state.remaining());   // 最后一步只走零头
player.move(MoverType.SELF, state.direction.scale(step));       // move 不掺摩擦，走多少是多少
state.traveled += player.position().subtract(before).length();  // 按实际位移记账

if (state.remaining() <= 0.05 || 被墙挡住) {
    player.setDeltaMovement(Vec3.ZERO);   // ★ 立即刹车：清零残留动量，绝不靠摩擦慢慢停
    player.hurtMarked = true;
    states.remove(uuid);
}
```

要点三条：

| 要点 | 为什么 |
|---|---|
| 用 `player.move(MoverType.SELF, …)` 而不是 `setDeltaMovement` | `move` 只做碰撞，**不掺摩擦** → 走多少就是多少 |
| 每刻 `step = min(每刻步长, 剩余距离)` | 永远不会冲过头，**到 10 格就是 10 格** |
| 走满（或撞墙）时立刻 `setDeltaMovement(ZERO)` | 「到点立即刹车」，不留惯性滑行 |

### 坑 2：通用位移和她抢人（所以要按招关掉）

`ResourceDrivenActionHandler.engageAndPlay(...)` 对**所有**招式都会做「转向 + 出手瞬间的一小步吸附」
（`AttackApproach.faceTarget` / `stepToward`）—— 玩家说的「通用的小幅度移动」就是这一步：

```java
// ShenheResources.ACTION_DATA
private static final Engagement NO_GENERIC_MOVE =
        Engagement.melee().withDash(false).withAdhesion(0, 0);   // 0 = 关掉吸附
```

- `withAdhesion(0, 0)`：不要出手瞬间的吸附小步；
- `withDash(false)`：不要框架的近战突进（她的突刺由 `DashSystem` 负责）；
- **转向保留**（默认）：`RushesForward` 按视线方向算位移，先转向目标才是「朝目标突刺」。

再到角色上透出 `getActionData()`：

```java
@Override public CharacterActionData getActionData() { return ShenheResources.ACTION_DATA; }
```

### 顺带：`moves` 也要清空

兜底动作表 `CharacterActionData.fallback(...)` 给每一段都塞了 `Move(0, 1.0)`（服务端冲量）。
申鹤现在不在 `action_system_characters` 名单里，`fireImmediately(...)` 会跳过 `moves`，
**但哪天把她加进动作系统，这个冲量就会立刻和她的突刺打架** ——
所以 `ShenheResources` 里两段战技的 `moves` 一律写 `List.of()`。

| 角色怎么位移 | 这一招怎么写 |
|---|---|
| 靠动作系统推（框架突进/吸附） | 留兜底或自己配 `Move`；`engagement` 用 `melee()` |
| **有自己的位移系统**（申鹤 `DashSystem`、薇斯娜三阶 E） | `moves` 清空 + `withDash(false).withAdhesion(0, 0)`；需要客户端跑天赋的再把 `runsTalentOnClient()` 打开 |

## 4.14 长按（E hold）要同时满足三件事

申鹤「E 长按效果丢失」的排查结论 —— 长按要能放出来，三件事缺一不可：

| # | 条件 | 在哪 |
|---|---|---|
| 1 | **短 CD ≠ 长 CD**（客户端的短/长按判定就是比这个） | 角色构造器**必须**用三冷却重载：`PGCharacter(uuid, star, name, element, ascend, skillShort, skillLong, burst, maxEnergy, textureId, map)`。用两冷却重载时 `PGCharacter` 会把 short/long 设成同一个值 → `ActionStateMachine.hasSkillHoldVariant()` 恒 false → 长按分支是死代码 |
| 2 | **`SkillData.hold() != null`** | `TalentBase.buildDefaultActionSet` 只在 hold 非 null 时才注册 `ELEMENTAL_SKILL_HOLD`；兜底表的 `SkillData` 是 `(step, null)` |
| 3 | 服务端认得 `skillTime >= 1000` | `ActionManager` 用 `skillTime >= 1000` 取 hold；`TalentBase` 给 hold 段挂的就是 `elementalSkill(..., 1000)`，角色的 `elementalSkill` 自己按 `skillType < 1000` 分流 |

申鹤的短 CD 200 / 长 CD 300 本来就满足第 1 条，缺的是第 2 条 ——
`ShenheResources.ACTION_DATA` 的 `SkillData(tap, hold)` 补上就恢复了。

> ⚠️ **构造器参数怎么数**（这一条被误判过一次）：三冷却重载是 **11 个参数**，
> 数「数字」要数 **3 个整数 + 1 个 float**（skillShort / skillLong / burst / maxEnergy）：
>
> ```java
> super(135001, 5, name, CYRO, ATK,
>       10 * 20, 15 * 20, 10 * 20, 80f, "shenhe", Map.of(...));
> //    ↑short 200  ↑long 300  ↑burst 200  ↑能量 80 → 共 11 个参数 = 三冷却重载
> ```
>
> 两冷却重载只有 10 个参数（少一个整数）。判断依据以**实参个数**为准，
> 不要凭「看起来只有几个数字」下结论；`Shenhe.java` 里也补了同样的注释。

> 长按判定本身（`pressSkill` / `releaseSkill` / `SKILL_HOLD_TICKS`）**不受**
> `character_system.action_system_characters` 名单影响，所以只补动作数据表即可，
> 不用把她加进动作系统名单（那会连带改变前摇/硬直/延迟伤害的表现）。

## 4.15 领域实体（`AreaEntity`）的寿命：为什么曾经永远不会消亡

**现象**：申鹤大招的领域「一直存在，不会自然消亡」。

**排查过程**（先排除掉两条常被怀疑的）：

- 倒计时机制**没问题**：`ServerLevel.tickNonPassenger` 在 `entity.tick()` 之前自增 `tickCount`
  （26.2 源码），`discard()` → `remove(DISCARDED)` 也正常；
- 生成时 `duration` 也**不是** -1/0：`TalismanSpiritArea` 构造器 `setDuration(600)`；
- `CharacterTickHandler` 里那道 `isInvaded` 提前返回**只管角色 tick**，不碰实体 tick ——
  领域由原版实体循环驱动，和那道门控无关。

**真正的根因：寿命状态根本没落盘。**

```java
// 旧代码：@Persisted 对**实体**是无效的（LDLib2 只 mixin 了 BlockEntity），
// readAdditionalSaveData / addAdditionalSaveData 是空的，注释却写「LDLib2 会自动持久化」
protected void serverTick() {
    if (this.duration != -1 && this.tickCount >= this.duration) { this.discard(); }
}
```

`tickCount` 也不进存档 → **每次区块卸载/重载，实体都被构造器「满血复活」**
（duration=600、tickCount=0、owner/character=null），于是「永远死不掉」；
再叠上「实体离开 ENTITY_TICKING 距离时服务端不 tick 它，而客户端在视距内照样渲染」，
看起来就是一个不消失的领域。

**修复**（`AreaEntity`）：

| 改动 | 说明 |
|---|---|
| 寿命改成**绝对到期时刻** `expireGameTime`（`level().getGameTime()`），第一次 tick 定死 | 相对倒计时会被「重载」和「不 tick」两件事各自坑一次；绝对时刻两个都不怕 |
| `addAdditionalSaveData` / `readAdditionalSaveData` 真正读写 `mg_duration` / `mg_expire` / `mg_owner` / `mg_character` | 这才是实体的存档通道 |
| `getOwner()` / `getOwnerCharacter()` 按 UUID 重新解析 | 重载后引用丢失的拥有者/角色能找回来（否则是个「僵尸领域」：看得见、不打伤害、不加冰伤） |
| 新增 `refreshLifetime()` | 「被刷新就续命」的领域（雷暴云 `refreshDuration()`）改用这个，不要再写 `tickCount = 0` |
| `ModEntities` 的 `MobCategory.CREATURE` → `MISC`（+ `clientTrackingRange(10)`） | 领域不是生物；顺带消掉加载期那句 `Entity minegenshin:talisman_spirit has no attributes` |

**日志**（留着当证据，都是低频的）：
`[镇灵之鼎] 生成：持续 600 刻（30 秒）` → 到期时 `[领域] talisman_spirit 到期移除（原定 600 刻）`。
要看「还剩多少」把 `AreaEntity.LIFETIME_DEBUG_LOG` 打开（默认 `false`，打开后每 100 刻一条心跳；
**没有心跳 = 它根本没在 tick**，往区块 `ENTITY_TICKING` 那边查，不要去动倒计时）。

> 一句话总结这一条：**`@Persisted` 对实体无效**，实体上任何「必须活过重载」的状态
> （寿命、拥有者、阶段…）都得自己写进 `addAdditionalSaveData`。

## 4.16 天赋等级：手动次数是唯一可写的东西

三个天赋（普攻 / 战技 / 爆发）各有一个等级，但它现在是**派生值**：

```
有效等级 = 基础 1 级 + 手动升级次数 + 命座/天赋加成
```

| 概念 | 字段 / 方法 | 说明 |
|---|---|---|
| 手动次数 | `PGCharacterData.manualNormalAttack / manualElementalSkill / manualElementalBurst` | **唯一可以被升级改写的量**，每个上限 `MAX_MANUAL_TALENT_UPGRADES = 9` → 手动最多到 **10 级** |
| 有效等级 | `normalAttackLevel / elementalSkillLevel / elementalBurstLevel` | 仍是这几个字段（伤害计算、UI 都直接读），但只由 `recalculateTalentLevels()` 写 |
| 上限 | `getNormalAttackLevelCap() / getElementalSkillLevelCap() / getElementalBurstLevelCap()` | 普攻 10；战技 10（3 命 +3 → 13）；爆发 10（5 命 +3 → 13） |

**为什么非要分开记手动次数**（升级消耗的口径）：

> 手动升到 4 级（升了 3 次）之后抽到 3 命（战技 +3）→ 显示 7 级，
> 但玩家真正手动升过的只有 3 次，所以**下一次消耗的仍然是「4→5」那一档**，
> 而不是「7→8」那一档。

所以消耗查表的入参一律是**手动次数**：`TalentUpgradeCost.primogem(manual)` /
`experienceLevels(manual)`（`core/character/talent/TalentUpgradeCost.java`）。
三个升级 RPC（`NetworkManager` 的 `upgradeNormalAttack/ElementalSkill/ElementalBurstRPCPacket`）
都按这个来，并且**先判「还能不能升」再判材料**（满级时该报满级，而不是报「原石不足」）。

> 两张消耗表现在还是**扁平表**（每次都是 320 原石 + 5 级经验，和改造前数值一致），
> 只是**索引变成了手动次数** —— 要分档就直接改 `TalentUpgradeCost` 里那两个数组
> （下标 0 = 1→2 级、下标 8 = 9→10 级）。

调试命令（直接设手动次数，不用去刷原石）：

```
/minegenshin character talent <玩家> <uuid> <normal|skill|burst> <0-9>
```

它会回一条「手动 N 次 → 有效等级 X/上限 Y（命座 Z）」，
用来看「手动 3 次 + 3 命 = 7 级，但下一次仍是 4→5 档」这种口径。

**通用命座加成**（全角色无例外，见 `recalculateTalentLevels`）：

| 命座 | 效果 |
|---|---|
| 3 命 | 元素战技等级 **+3**，上限同时 **+3**（10 → 13） |
| 5 命 | 元素爆发等级 **+3**，上限同时 **+3**（10 → 13） |

命座变化（`setConstellation` / `upgradeConstellation`）会立刻 `recalculateTalentLevels()`，
所以抽到 3 命那一刻战技等级就上去了；UI 的标签每帧刷新（`ScreenAscension.refreshLabels`），
上限用的是上面那三个 `getXLevelCap()`。

> ⚠️ **老存档迁移**：改造前只有「有效等级」、没有手动次数。第一次读到等级时
> `migrateTalentUpgradesIfNeeded()` 会倒推手动次数（`有效等级 - 1`，clamp 到 0~9）并重算，
> 否则老角色一升级就会先被当成 1 级 + 1 次、等级直接掉回去。
> 迁移必须在**任何 recalc 之前**跑（`setConstellation` / `upgradeConstellation` 里也是），
> 这一点踩过一次坑。旧上限 14 的角色会被 clamp 回 10（新规则就是手动封顶 10 级）。

## 4.18 角色效果（buff）的存档：**实例 id ≠ 注册名**

2026-09-21 那次「一进世界就 Ticking player 崩服」，根因就在这一条上。

```
CharacterEffectEvent.characterEffectTick（每刻）
  └─ PGCharacterData.getEffectContainer()            ← 每刻从 NBT 重新反序列化
       └─ CharacterEffectContainer.fromListTag
            └─ CharacterEffectInstance.fromTag
                 └─ 注册表.getValue(存的 id)          ← 崩在这里
```

### 为什么崩

1. `CHARACTER_EFFECT_REGISTRY` 是 `DefaultedMappedRegistry`（`ModRegistries` 里 `.defaultKey(minegenshin:empty)`），
   语义是「查到不存在的键 → 返回默认值」。但 **`minegenshin:empty` 从来没注册过** →
   它内部的默认 Holder 是 null → 查一个不存在的键时 `defaultValue.value()` **空指针**。
2. 而存档里为什么会有查不到的 id：`CharacterEffectInstance` 曾经**只有一个 `effect_id`**，
   同时当「实例身份」和「注册名」用。申鹤短按/长按那两套 A4 加成是**参数化实例**
   （`DamageBonusEffect(0.15f, …)`），它们各自起了一个名字当身份
   （`minegenshin:shenhe_ascend2_tap` / `_hold`）—— 这两个名字**不在注册表里**，
   读档时被当成注册名查表 → 触发上面那条 NPE。
3. 之所以一崩就崩死：这个 tick 遍历**全队 4 个角色**，而这个 buff 又是发给全队的；
   再加上容器每刻重新反序列化，所以坏条目在谁身上都是「每刻必崩」。

### 修了三层

| 层 | 改动 | 作用 |
|---|---|---|
| 注册表 | 注册 `minegenshin:empty` → `CharacterEffectInstance.DummyEffect.INSTANCE`（`ModCharacterEffects.EMPTY_EFFECT`） | 默认键有真实注册项，`DefaultedMappedRegistry` 的 `defaultValue` 不再为 null（vanilla 源码里 `register` 遇到默认键就会记下来） |
| 查表 | `CharacterEffectInstance.lookupEffect()`：**先 `containsKey` 再 `getValue`**；未知 id 只打一次日志并点名 | 任何情况下都不会再 NPE；以后再有坏 id，日志直接告诉你是哪一个 |
| 数据 | **两个 id 分开存**：`effect_type` = 注册名（查表用）、`effect_id` = 实例身份（去重/覆盖/移除用）。参数化效果通过新增的 `ICharacterEffect.writeInstanceData()` / `createFromInstanceData()` 自己写参数、自己重建 | 参数化 buff 读档后能真正恢复（`DamageBonusEffect` 已实现：加成值 + 作用攻击类型 + 元素） |

### 两条要记住的规矩

- **能交给 LDLib2 就交给 LDLib2**：`@Persisted` / `@DescSynced` 为主
  （他已经给 `LivingEntity` 和 `PGCharacterData` 补了类型支持）。
  只有「**对象 ↔ 对象 ID**」这种转换（`Player` ↔ UUID、效果 ↔ 注册名）才自己补一层转换函数 ——
  效果容器正是这种情况：列表本身仍由 LDLib2 持久化，只有「效果引用」需要自己转。
- ⚠️ **`DefaultedMappedRegistry.getKey()` 对未注册的实例不会返回 null，而是返回默认键**
  （`minegenshin:empty`）。所以不要拿 `getKey()` 当身份判断，
  要用 `CharacterEffectInstance.registeredKeyOf()`（键存在 **且** 取出来就是它自己才算）。

> 老存档没有 `effect_type` 时，会退回用 `effect_id` 查一次；查不到就变成占位效果并打一条 warn
> —— 那条 buff 会失效，但**不会再崩**，而且存回去时会规范化成 `effect_type=empty`。

## 4.17 满命「翔风剑·变移」怎么实现的

**机制**：三阶翔风剑之后开一个 **5 秒窗口**；窗口内**点按普攻或 E** 不再走正常连招，
而是打「变移」；打完窗口立刻关闭（`consumeBianyiWindow()`），普攻/战技恢复正常队列。

**关键设计：不动框架，只换 `ActionSet`。** 客户端和服务端都从
`character.getActionSet(player)` 取表，而它按 `getActionStateKey(player)` 缓存：

```java
// Vesna.getActionStateKey
if (hasBianyiWindow(player)) return "bianyi";      // ← 窗口内
if (!windriderActive) return "default";
return "windrider_" + xiangfengJianLevel;
```

`VesnaTalent.buildActionSet` 在 `"bianyi"` 这一支里
`clearNormalCombo()` + `addNormalAttack(变移)` + `addSkillTap(变移)`（大招/闪避/重击/长按照旧）。
于是双端自动选中同一招，`ResourceDrivenActionHandler` / `ActionManager` **一行都不用改**。

| 细节 | 做法 | 为什么 |
|---|---|---|
| 窗口怎么记 | `Vesna.bianyiWindowEndTick`（**绝对到期时刻**，`@DescSynced @Persisted`） | 客户端不用等服务端每刻同步，自己拿本地 `getGameTime()` 比就知道窗口在不在（每刻整包同步太贵） |
| 变移的 `ActionKind` | **`ActionKind.SPECIAL`**（不是 `ELEMENTAL_SKILL_TAP`） | 用 E 点按的 kind 会被 `Vesna.canUseElementalSkill` 的剑气/CD 门槛拦住；SPECIAL 落到 `canCast` 的 `default -> true`，也不触发 `applyElementalSkillCooldown` → 不扣剑气、不转 CD |
| 双端判「放不放得出来」 | 客户端也改用 `def.kind`（`ResourceDrivenActionHandler.skill`） | 之前客户端按「按了哪个键」推 kind、服务端按 `def.kind` —— 变移会只被客户端拦住，两端分叉 |
| 消费时机 | 服务端 `VesnaTalent.onCastStart(kind == SPECIAL)` | `onCastStart` 跑在 `ActionState` 构造时（触发即生效），窗口一放就关 |
| 连招恢复 | 变移 def 的 kind 不是 combo → 服务端 `resetCombo()`；窗口关掉后 key 回到 `windrider_N`/`default` | 「释放后普通攻击和战技恢复正常队列」 |

**伤害**（`VesnaTalent.bianyiDamage`，一次出手两段）：

| 段 | 倍率 | 走哪条管线 |
|---|---|---|
| ① 风元素伤害 | 150% 攻击力 | 普通直伤（`dealAoeAnemoDamage(..., stellarSwirl=false)`） |
| ② 灵剑 | 200% 攻击力 | **星扩散状态下转成「星扩散-风」直伤**（`stellarSwirl = hasRadianceStellarSwirl`）→ 走星烁管线，吃户口基础倍率与大权 |
| ③ 风翎 | —— | **只在巡风列装模式下**额外唤出，复用风铃弹射物 `VesnaAttackProjectile.create(...)`（仓库里没有独立的「风翎」内容） |

> 「灵剑不生成真实实体」：②这一段就是**直接结算的一段 AOE 伤害**，不 new `VesnaSpiritSwordEntity`。
> 文案里「灵剑造成的伤害」的真正含义 = 星扩散状态下所有灵剑伤害都转成星扩散-风直伤。

**C6 的擢升 20%**：走独立的擢升区（`Vesna.getElevationBonus(SWIRL)`），
不塞进反应加成区 —— 那里已经躺着 C1 的 +20%，而且两者口径不同（见 4.6 的关键点）。

---

# 第五部分 · 渲染系统

## 5.1 渲染接管链路

```
Minecraft 渲染玩家
  └─ AvatarRenderer.submit(...)                      ← 原版玩家渲染器
       └─ mixin: AvatarRendererMixin                 ← @Inject(HEAD, cancellable)
            └─ CharacterRenderDispatcher.handleSubmit(state, poseStack, buffer, camera)
                 ├─ 玩家不在原神模式？        → return false（走原版）
                 ├─ 拿不到当前角色 ID？      → return false
                 ├─ custom_model_characters 不含该角色？ → return false（走原版）
                 │    （同时把缓存的动画代理 playerEntity 置空）
                 ├─ 角色没有渲染定义？        → return false
                 └─ doRender(...)
                      ├─ MODELS[charId]     : CharacterPlayerModel（模型/贴图/动画路径）
                      ├─ RENDERERS[charId]  : CharacterRenderer（+ BoneMountGeoLayer）
                      ├─ ANIMATABLES[player]: GenshinReplacedPlayer（每玩家一份）
                      ├─ animatable.setPlayerEntity(player)
                      └─ renderer.performRenderPass(animatable, player, poseStack, buffer, camera, light, partialTick)
                           └─ CharacterModel.getAnimationResource → 对应的 .animation.json
                           └─ PlayerAnimationController 逐帧决定播哪个动画
```

三个缓存都是**按角色 ID** 建的（模型/渲染器），动画代理是**按玩家**建的 —— 同屏两个同角色玩家共用模型，但各播各的动画。

## 5.2 路径解析规则

`CharacterRenderData` 存的是**相对 `assets/minegenshin/` 的路径字符串**，目录由 `GenshinAssets` 拼：

| 方法 | 输入（Vesna） | 输出（相对路径） |
|---|---|---|
| `GenshinAssets.defaultModelPath()` | — | `character/linweiyun/linweiyun.geo.json` |
| `GenshinAssets.defaultTexturePath()` | — | `character/linweiyun/textures/linweiyun.png` |
| `GenshinAssets.defaultAnimationPath()` | — | `character/linweiyun/linweiyun.animation.json` |
| `GenshinAssets.characterAnimationPath(id)` | `"vesna"` | `character/vesna/vesna.animation.json` |
| `GenshinAssets.characterModelPath(id)` | `"vesna"` | `character/vesna/vesna.geo.json`（专属模型时） |

`GenshinGeoModel` 再把它翻成 GeckoLib 的缓存键（`Identifier`，**不带扩展名**）。

对应磁盘位置：

```
assets/minegenshin/character/linweiyun/linweiyun.geo.json
assets/minegenshin/character/linweiyun/textures/linweiyun.png
assets/minegenshin/character/vesna/vesna.animation.json
```

> 这三行是**逻辑路径**，也是代码里写的路径。自带资源的模型与动画实际收在资源包
> （整包一个文件，详见 9.1）里；
> 要覆盖其中某一条，把同名文件放进对象目录下的 `local/` 即可 —— 明文直读、同名优先，
> 逻辑路径与缓存键都不变。

## 5.3 动画控制器在渲染里的位置

`PlayerAnimationController.create(animatable)` 建一个名为 `movement_controller` 的控制器。
它注册在 `GenshinReplacedPlayer.registerControllers`，**在渲染状态提取阶段（`fillRenderState`）每帧被调用一次**。

```
fillRenderState(animatable, player, renderState, partialTick)
  ├─ captureDefaultRenderState（DataTickets：instanceId / tick / light ...）
  ├─ addRenderData（本层）
  ├─ 各渲染层 addRenderData        ← BoneMountGeoLayer 在这里解析挂点内容
  ├─ fireCompileRenderStateEvent
  └─ AnimationProcessor.extractControllerStates  ← 状态处理器在这里跑
```

控制器每帧做四件事（`PlayerAnimationController.handle`）：

1. 取目标状态：本地玩家读 `ActionStateMachine.currentState`，其他玩家读同步附件
2. 选动画：动作状态 → `pickAction`（先过 `AnimationAvailability`）；否则 → `pickLocomotion`（缺失退 idle）
3. 选过渡：目标是动作 / 刚从动作回常态 / 什么都还没在播 → `setTransitionTicks(0)` 硬切；否则用 `exitTransitionTicks`
4. `state.setAndContinue(target)`

> GeckoLib 5 的非 triggered 路径是**直接读 `transitionTicks` 字段**（`AnimationController#initializeNewAnimation`），
> 所以回调里设的值当帧就生效。

## 5.4 渲染层机制

GeckoLib 5 的一次渲染趟顺序（`GeoRenderer.performRenderPass`）：

```
firePreRenderEvent
preRenderPass                     ← 模型还没摆姿势
scaleModelForRender / adjustRenderPose
preApplyRenderLayers              ← 各层的 preRender（隐藏骨骼的地方）
                                    + 各层的 addPerBoneRender（登记每骨骼任务）
captureModelRenderPose
submitRenderTasks                 ← 主模型；骨骼更新（BoneUpdater）在这里被消费
submitPerBoneRenderTasks          ← 每骨骼任务在这里执行，PoseStack 已摆到该骨骼
applyRenderLayers
```

**关键**：想隐藏骨骼要在 `preRender` 阶段 `renderPassInfo.addBoneUpdater(...)`，
因为骨骼更新在 `submitRenderTasks` 之前被消费。`performRenderPass` 也支持从外面直接传一个
`BoneUpdater`（第一人称藏头就是这么做的，见 [10.2](#102-第一人称一个开关两套做法)）。

> **`adjustRenderPose` 里没有平移**（`CharacterRenderer` 覆盖成空实现）。
> 基类 `GeoObjectRenderer` 默认会 `translate(0.5, 0.51, 0.5)` —— 那是给「以方块角为原点」
> 的摆件模型用的；我们的角色模型以原点为中心（cube 对称于 x=0，`visible_bounds_offset = [0,1.75,0]`），
> 原版交给我们的 pose 也只有实体位置，所以那半格会把模型推到斜后方 0.7 格。
> GeckoLib 自己的实体渲染器（`GeoEntityRenderer` / `GeoReplacedEntityRenderer`）同样没有这句平移。

---

# 第六部分 · 骨骼替换

## 6.1 原理

骨骼替换 = **隐藏目标骨骼自身的几何体 + 在那根骨骼的位姿上画别的东西**。

- 隐藏用 `BoneSnapshot.skipRender(true)`（GL5 已经没有 `GeoBone.setHidden`）
- 画东西用 `PerBoneRender`：回调拿到的 `PoseStack` 已经摆在该骨骼的位姿上

父级骨骼（手臂、剑鞘）不受影响，因为只跳过这一根。

## 6.2 GeckoLib 5 的骨骼生命周期

```
preRender 阶段
   renderPassInfo.addBoneUpdater(updater)                收进 boneUpdaters

submitRenderTasks 之前
   RenderPassInfo.renderPosed(task) 内部：
        compileBoneUpdates()  → 对每个 updater 调 snapshots.get(boneName)
                                首次访问时创建 BoneSnapshot 并挂到 bone.frameSnapshot
        snapshot.apply()      → bone.frameSnapshot = snapshot      ← 生效
        task.run()            → 模型渲染，CuboidGeoBone.render 检查 frameSnapshot.isHidden()
   finally
        snapshot.cleanup()    → bone.frameSnapshot = null          ← 摘掉
```

所以 `skipRender` 是**渲染趟内的临时状态**，不会污染共享的 baked model，下一帧槽位空了骨骼自己就回来。

## 6.3 一次替换的完整逻辑链

```
渲染状态提取
  BoneMountGeoLayer.addRenderData(animatable, player, renderState, partialTick)
    ├─ boneMountsFor(player)：角色 ID → CharacterRenderData.boneMounts()
    ├─ currentCharacter(player)：玩家附件里的当前出战角色
    └─ 对每个挂点 resolveMount：
         content = mount.source().resolve(player, character)
         ├─ 没内容 → 跳过（骨骼照原样渲染）
         ├─ 形态① 整个物品模型：
         │     Minecraft.getItemModelResolver().updateForLiving(state, stack, THIRD_PERSON_RIGHT_HAND, player)
         │     → 存进 renderState 的 MOUNTS DataTicket
         └─ 形态②/③ 源模型的一根骨骼：
               modelId/textureId 来自 content，或从物品反解（GeoItemModelResolver）
               GeckoLibResources.getBakedModels().getModel(modelId) → BakedGeoModel
               → 存进 MOUNTS

preRender
  对每个解析结果 addBoneUpdater → snapshots.ifPresent(目标骨骼).skipRender(true)

addPerBoneRender
  对每个解析结果：model().getBone(目标骨骼) → consumer.accept(bone, 渲染任务)

每骨骼渲染（PoseStack 已摆在该骨骼）
  ├─ 形态①：applyMountTransform → ItemStackRenderState.submit(...)
  └─ 形态②/③：submitCustomGeometry
        └─ 用捕获到的位姿建临时 PoseStack + RenderPassInfo
             └─ renderIsolatedBone(源模型, 源骨骼, ...)
                  ├─ 全模型 hideAll（skipRender + skipChildrenRender）
                  ├─ 目标骨骼的祖先链 skipChildrenRender(false)
                  ├─ 目标骨骼 skipRender(false)  ← 它和它的子树一起出来
                  ├─ sourceModel.render(...)
                  └─ finally：把所有动过的 frameSnapshot 置回 null
```

## 6.4 替换多个骨骼

`CharacterBoneMount` 是**列表**，一个角色可以挂任意多根骨骼，互不影响：

```java
public static final CharacterRenderData RENDER_DATA = new CharacterRenderData(
        ID, "default", "default_texture.png", "vesna",
        CharacterRenderData.defaultAnimMapping(), 1.0f,

        // ①②：武器槽那一个物品，拆成两根骨骼分别挂到角色的两根骨骼上
        CharacterBoneMount.of("blade_right", BoneMountSource.weaponSubBone("blade")),
        CharacterBoneMount.of("shealth",     BoneMountSource.weaponSubBone("sheath")),

        // ③：第三根骨骼用完全不同的来源（另一个模型 / 另一个槽位）
        CharacterBoneMount.of("head",  BoneMountSource.ofSlot(ArtifactInventory.SLOT_CIRCLET)),
        CharacterBoneMount.of("torso", BoneMountSource.subModel(
                Identifier.parse("minegenshin:item/cape/cape"),              // item/<物品id>/<id>.geo.json
                Identifier.parse("minegenshin:item/cape/textures/cape.png"),// item/<物品id>/textures/<id>.png
                "cape_root"))
);
```

规则：

- **每个挂点独立取内容、独立隐藏、独立微调**（scale/offset/rotation 各自一套）
- 同一个来源可以被多个挂点复用（如上例两个 `weaponSubBone` 都读武器槽）
- 任何一个挂点取到空 → 只跳过它自己，其它照常
- 列表里骨骼名重复没有意义（后一个会覆盖前一个的渲染位置），别这么写

## 6.5 从其他位置取内容

`BoneMountSource` 是函数式接口，**返回什么由你决定**：

```java
@FunctionalInterface
public interface BoneMountSource {
    @Nullable BoneMountContent resolve(Player player, PGCharacter character);
}
```

内置来源：

| 工厂 | 内容 |
|---|---|
| `BoneMountSource.WEAPON_SLOT` | 武器槽的整把武器（默认） |
| `BoneMountSource.ofSlot(int slot)` | 装备栏任意槽位的整件物品（`ArtifactInventory.SLOT_*`） |
| `BoneMountSource.fixed(ItemStack)` | 写死一个物品，做固定外观 |
| `BoneMountSource.of(Function<PGCharacter, ItemStack>)` | 任意逻辑产出物品 |
| `BoneMountSource.weaponSubBone(String)` | 武器槽物品的**某一根骨骼** |
| `BoneMountSource.slotSubBone(int, String)` | 任意槽位物品的某一根骨骼 |
| `BoneMountSource.subModel(modelId, textureId, boneName)` | 任意 geo 模型的一根骨骼，不依赖物品 |

**自己写一个来源**（比如从配置、从别的实体、从数据包读）：

```java
public static final BoneMountSource FROM_CONFIG = (player, character) -> {
    if (character == null) return null;
    String modelName = MyConfig.sheathModelFor(character.getTextureId());
    if (modelName == null) return null;                     // 不挂，骨骼保持原样
    return BoneMountContent.subBone(                        // 或 .whole(stack) / .model(...)
            character.getData().getWeapon(), "sheath");
};
```

三种内容形态任意组合：

| 工厂 | 用途 |
|---|---|
| `BoneMountContent.whole(stack)` | 整个物品模型（普通模型 + GeckoLib geo 物品都行） |
| `BoneMountContent.subBone(stack, "骨骼名")` | 物品 geo 模型里的一根骨骼子树 |
| `BoneMountContent.model(modelId, textureId, "骨骼名")` | 直接指定 geo 模型的一根骨骼 |

- 反解不出物品的 geo 模型（不是 GeoItem、渲染器还没建、资源没加载）时，
  `BoneMountGeoLayer` **自动退回「整个物品模型」**，不会崩也不会空着。
- 返回 `null` / `BoneMountContent` 为空 → 这个挂点这一帧不生效，**骨骼不会被隐藏**，
  所以「没装备 = 用模型自带部件」是自动成立的。

**远端玩家也一样**：内容从角色数据里取，而角色数据跟着 `PLAYER_CHARACTERS_ATTACHMENT` 同步，
所以你看到别人的武器也是他们实际装备的那把。

## 6.6 从源模型里挑骨骼（拆分剑身/剑鞘）

武器槽只能放**一个**物品，但那个物品的 geo 模型里可以有很多根骨骼。拆法：

```java
// 假设 test_sword.geo.json 里有 "blade" 和 "sheath" 两根骨骼
CharacterBoneMount.of("blade_right", BoneMountSource.weaponSubBone("blade")),
CharacterBoneMount.of("shealth",     BoneMountSource.weaponSubBone("sheath"))
```

渲染时：

1. 读武器槽那一把剑，反解出它的 geo 模型与贴图（`GeoItemModelResolver`）
2. 对 `blade_right` 挂点：把源模型里**除了 `blade` 及其祖先链和子树以外的骨骼全部隐藏**，整模型渲染一次
3. 对 `shealth` 挂点：同样操作，但保留的是 `sheath`

等于把「一个模型」按骨骼切成了两块，分别挂到角色的两根骨骼上。

**祖先链会自动打开**：`blade` 如果有父骨骼（比如 `weapon_root`），父骨骼自身不渲染但会继续往下走，
所以子骨骼的世界位姿是对的。

## 6.7 失败时的结果（重要）

骨骼替换有一步是**隐藏目标骨骼**。如果内容其实画不出来却照样隐藏了目标骨骼，
结果就是**角色身上凭空少一块，而且不报错** —— 这是这套机制里最难查的一类问题。
所以解析阶段会把这些情况全部挡掉，原则是：**画不出来就干脆别挂**。

举例：从 A 模型取 1、2 两根骨骼，分别替换角色的 Y、U 两根骨骼，但 A 里其实只有 1：

| 挂点 | 结果 |
|---|---|
| Y ← A.1 | 正常：Y 自身几何体被隐藏，A 的 1 号骨骼画在 Y 的位姿上 |
| U ← A.2 | **整个挂点不生效**：U 不被隐藏，角色原来的那部分照常显示；日志里一条 warn |

完整对照表：

| 情况 | 结果 | 日志 |
|---|---|---|
| 内容为空（没装备 / 来源主动返回 null） | 跳过，目标骨骼保持原样 | 无（正常情况） |
| 源模型反解不出来（物品不是 geo 物品、渲染器还没建） | 跳过，目标骨骼保持原样 | warn 一次 |
| 找不到已加载的 geo 模型 | 跳过，目标骨骼保持原样 | warn 一次 |
| **源模型里没有指定的源骨骼** | **跳过，目标骨骼保持原样** | warn 一次，**并把该模型里所有骨骼名列出来** |
| 源骨骼存在但整个子树没有方块（比如名字写成了父级） | 跳过，目标骨骼保持原样 | warn 一次 |
| 角色模型上没有目标骨骼 | 本来就不生效（隐藏和绘制都走 `getBone`） | warn 一次，列出角色模型的骨骼名 |
| 目标骨骼存在、内容也画得出来 | 隐藏目标骨骼 + 在它的位姿上画内容 | 无 |

warn 的示例：

```
[骨骼替换] 源模型 'minegenshin:item/test_sword' 里没有骨骼 'sheath'（挂点 'sheath' 要挂到角色骨骼 'sheath'）：
这个挂点整体不生效，角色骨骼保持原样。模型里的骨骼名有：root, blade, handle, guard
```

提示按「**模型对象身份 + 骨骼名**」去重：正常运行时不会每帧刷屏；
资源重载（F3+T）后模型对象换了新的，如果还没修好会再提醒一次。

**设计取舍**：源骨骼缺失时**不会**退回「画整个模型」——
那会把一整把剑糊在一根手骨上，比什么都不画更糟。要么改对名字，要么改用 `BoneMountSource.WEAPON_SLOT`（整个物品模型）。

## 6.8 常见坑

| 现象 | 原因 | 处理 |
|---|---|---|
| **角色身上少一块**（某个部件消失） | 见上表：内容画不出来但目标骨骼被隐藏了。**当前的实现已挡住这种情况**，如果还出现，检查是不是用了自己的 `BoneMountSource` 返回了非空但无意义的内容 | 看日志里的 warn |
| 骨骼没隐藏，原来那把剑还在 | 挂点内容为空（槽位没东西 / 来源返回 null） | 这是设计行为；确认来源真的返回了内容 |
| 武器巨大 / 插在奇怪的位置 | 物品模型是照「物品展示」尺寸做的 | 调 `withScale` / `withOffset` / `withRotation`；先把 scale 调到 0.1 找位置 |
| 提示找不到骨骼 | `boneName` 和 geo.json 里的名字不一致 | 看 warn 里列出的骨骼名；注意是 `qing_xie` 不是 `qiang_xie` |
| 整个模型变成原始姿态 | 动画名不存在（不是骨骼替换的问题） | 见 [3.6 动画保护](#36-动画保护) |
| 子骨骼模型位置不对 | 源骨骼的父级也带了变换 | 这是正常的层级变换；用挂点的 offset 补偿 |
| 换了武器模型不变 | 物品不是 GeckoLib geo 物品 | `weaponSubBone` 会 warn 并跳过；整把武器用 `WEAPON_SLOT` 仍然能显示，只是不能拆骨骼 |
| 挂点写了但完全没反应 | 角色没在 `custom_model_characters` 名单里 | 那一层根本不跑（走原版渲染） |

---

# 第七部分 · 底层（写好就不怎么需要动）

## 7.1 包结构

```
com.linweiyun.genshin
├── core.system.combat.animation            ← 动作 / 动画【表现层】
│   ├── CharacterAnimationRegistry          角色登记表
│   ├── action/   CharacterActionHandler, CharacterActions, ResourceDrivenActionHandler
│   ├── config/   CharacterAnimations, LocomotionAnims, DefaultCharacterAnimations
│   ├── state/    ActionStateMachine, PlayerAnimationController,
│   │             AnimationStateSync, AnimationAvailability
│   ├── animatable/ IPlayerAnimatableProxy
│   └── server/   ServerAnimationTicker
│
├── core.system.combat.action               ← 动作 / 伤害【结算层】
│   ├── ActionManager, ActionState, ActionDefinition, ActionSet, ActionContext,
│   │   ActionKind, InterruptReason, ServerActionExecutor, ServerTickScheduler
│   └── data/     CharacterActionData, CharacterRenderData, CharacterRenderRepository,
│                 CharacterBoneMount, BoneMountSource, BoneMountContent
│
├── core.character.{weapon}.{name}          ← 角色（Vesna, Shenhe, ...）
│   ├── Xxx.java            角色数据 / 能量 / 模式
│   ├── XxxTalent.java      技能实现
│   ├── XxxResources.java   渲染数据 + 动作时序
│   └── XxxAnimations.java  动画目录
│
├── core.character.talent.TalentBase        ← 天赋基类
├── core.attachment                          ← 附件与同步数据
├── core.network                             ← RPC 与网络
├── client.keybindings                       ← 按键
├── client.render.character                  ← 渲染器与渲染层
└── mixin.mixins                             ← 原版钩子
```

**分层约定**：`combat.action` 管结算，`combat.animation` 管表现，
两者只通过「客户端按键 → RPC → 服务端 ActionManager」相连。

## 7.2 网络层清单

### 客户端 → 服务端（动作请求，LDLib2 `@RPCPacket`）

| RPC 名 | 参数 | 落到 |
|---|---|---|
| `characterNormalAttackRPCPacket` | `int comboStage, int targetEntityId` | `ActionManager.requestNormalAttack` |
| `characterChargedAttackRPCPacket` | `int targetEntityId` | `requestChargedAttack` |
| `characterActiveSkillRPCPacket` | `int isLong, int targetEntityId` | `requestElementalSkill` |
| `characterActiveBurstRPCPacket` | `int targetEntityId` | `requestElementalBurst` |
| `characterDodgeRPCPacket` | — | `requestDodge` |
| `characterInterruptRPCPacket` | `int reasonOrdinal` | `ActionManager.interrupt` |
| `minegenshin:animation_state` | `String state, int ticks` | `ServerAnimationTicker.apply` |

> **所有出手类请求都带 `targetEntityId`**（`-1` = 没有目标）。服务端收到就
> `CombatTargeting.lock(sp, target)` 更新自己那份锁 —— 召唤物问「主人在打谁」
> 永远拿到最新目标，不用额外同步包。这个锁同时决定服务端要不要跳过动作自带的位移。

### 动画状态同步（不走包）

| 环节 | 实现 |
|---|---|
| 数据 | `core.attachment.AnimationState`（LDLib2 `@Persisted` 字段 + `PersistedParser` 自动编解码） |
| 载体 | `AttachmentRegistration.ANIMATION_STATE_ATTACHMENT`，`serialize` + `sync` |
| 触发 | 服务端 `player.setData(...)` 后 `player.syncData(...)` → NeoForge 推给所有能看到该玩家的客户端 |
| 兜底 | `ServerAnimationTicker` 倒计时到期强制复位 |

### 角色数据同步

`PLAYER_CHARACTERS_ATTACHMENT`（`.sync(PlayerCharactersAttachment.STREAM_CODEC)`）承载整个角色列表，
包括装备栏 —— 所以远端玩家的武器、渲染数据、动画配置在客户端都是可读的。

## 7.3 GeckoLib 5 集成要点

与 GeckoLib 4（参考1 / 参考2 用的版本）的主要差异，踩过的坑都在这：

| 项 | GeckoLib 4 | GeckoLib 5 |
|---|---|---|
| 包名 | `software.bernie.geckolib` | `com.geckolib` |
| 状态回调参数 | `AnimationState<T>` | `AnimationTest<T>`（record：animatable / renderState / manager / controller） |
| 取控制器 | `state.getController()` | `state.controller()` |
| 取当前动画 | `controller.getCurrentAnimation()` | `controller.getCurrentAnimationPoint().animation().name()` |
| 过渡刻数 | `controller.transitionLength(int)` | `controller.setTransitionTicks(int)`（直接读字段，当帧生效） |
| 骨骼隐藏 | `GeoBone.setHidden(boolean)` | 渲染趟内的 `BoneSnapshot.skipRender(boolean)` |
| 控制器求值时机 | 每 tick | **每渲染帧**（在 `fillRenderState` 里） |
| 实例缓存 | `createInstanceCache(this)` | 显式区分单例/实例：`createInstanceCache(this, false)` |

其它要点：

- GeckoLib 自身的扫描根是**硬编码**的 `assets/<ns>/geckolib/{models,animations}`，
  里面的路径前缀后缀会被 `GeckoLibResources.stripPrefixAndSuffix` 剥掉。
  **本 MOD 不受这条限制**：`GenshinGeoCache` 自己扫 `character/`、`item/`、`entity/`，
  `AssetGeoCache` 扫 `item/`、`block/`、`entity/`，所以资源放进对象目录就能加载 —— 见第九部分。
- `RawAnimation` 是**值相等**的，所以每帧重新 `RawAnimation.begin().thenPlay(name)` 不会导致动画重播。
- `AnimationTimeline.create(raw, model, transitionTicks)` 会在动画前插一个 transition 阶段；
  从「原始姿态」插值进来就是那个阶段干的 —— 所以「什么都没在播」时要硬切。
- 物品的 geo 模型走原版「特殊物品模型」体系（`geckolib:geckolib`），
  用 `ItemStackRenderState.submit(...)` 提交即可，由 `GeckolibItemSpecialRenderer` 画出来。

## 7.4 关键类索引

| 想知道… | 看这里 |
|---|---|
| 这一帧播哪个动画 | `client/animation/state/PlayerAnimationController.java` |
| 什么时候能打断、连击怎么算 | `client/client/combat/state/ActionStateMachine.java` |
| 动画名存不存在 | `client/animation/state/AnimationAvailability.java` |
| 按键到动作的转发 | `client/combat/action/ResourceDrivenActionHandler.java` |
| 某个动作的时序从哪来 | `core/character/talent/TalentBase.java` + 角色的 `XxxResources` |
| 服务端怎么路由技能请求 | `core/system/combat/action/ActionManager.java` |
| 伤害怎么算出来的 | `core/system/combat/attack/HurtEntityHelper.java` + `mixin/mixins/LivingEntityHurtMixin.java` |
| 模型怎么被接管的 | `client/render/character/CharacterRenderDispatcher.java` + `mixin/mixins/AvatarRendererMixin.java` |
| 第一人称怎么画的 | `client/render/character/FirstPersonCharacterRenderer.java` |
| 骨骼替换怎么做 | `client/render/character/BoneMountGeoLayer.java` |
| 某个角色有哪些挂点 | 角色的 `XxxResources.RENDER_DATA` |
| 打谁 / 索敌参数 | `core/system/combat/targeting/CombatTargeting.java`（`Params` 是逐招式的） |
| 转向与突进怎么算 | `client/combat/AttackApproach.java` |
| 资源放在哪 / 路径怎么拼 | `core/asset/GenshinAssets.java` + `GeoPathOverrides.java` |
| 角色音效怎么加载 | `client/sound/CharacterSounds.java` + `mixin/mixins/SoundManagerMixin.java` |
| 物品模型怎么生成的 | `data/generators/ModModeProvider.java`（见 [9.7](#97-数据生成物品模型与物品定义)） |

---

# 第八部分 · 索敌与近战突进

> 这一部分是**战斗手感**的核心：打谁、怎么转向、够不着怎么贴上去、位移怎么给。

## 8.1 为什么要重做索敌

旧的 `TargetSeeker` + `LOCKED_TARGET` 附件事后校验只有一个「过期时间」：

- 一旦锁错，只能等它自然过期，或者靠丢视角也很难主动丢 —— **试错代价高**
- 没有迟滞，站在两个怪中间会一会儿锁 A 一会儿锁 B
- 锁定后不转向，也不管够不够得着

新的 `core.system.combat.targeting` 包用 AAA 常见的**双阈值迟滞 + 宽限**模型重做，
旧的 `TargetSeeker` 原样保留（召唤物的独立索敌还在用它），互不影响。

## 8.2 索敌策略：能打谁

`TargetPolicy`，默认策略（**不给任何额外信息时就是这个**）：

| 能不能打 | 对象 |
|---|---|
| ✅ | 原版 `Enemy`（僵尸、骷髅……） |
| ✅ | `Mob` 里 `isAggressive()` 的，或已经把你当目标的 |
| ✅ | 本 MOD 的 `TeyvatLiving`（提瓦特怪物） |
| ❌ | 玩家（自己、队友、其它玩家都不打） |
| ❌ | 弹射物 |
| ❌ | 非生物（掉落物、船、盔甲架……）**除非登记过** |

**特殊非生物目标的预留口子**（元素方碑这类）：

```java
// 等你加了元素方碑，注册一行就行，不用改 TargetPolicy 里任何逻辑
TargetPolicy.registerTargetableNonLiving(entity -> entity instanceof ElementalMonument);
```

**打分**默认是「越近越好 + 正前方加分」：

```java
distanceScore = 1 / (1 + distanceSq)      // 距离为主
score = distanceScore + max(0, facingDot) * 0.5
```

想换目标偏好（比如优先残血、优先精英怪），实现一个自己的 `TargetPolicy`，塞进
`CombatTargeting.Params` 传给 `CombatTargeting.acquire(player, params)` 即可。

## 8.3 软锁定：迟滞与丢锁

`CombatTargeting` —— 「玩家现在瞄着谁」。

### 双阈值迟滞（防 A/B 互抖）

下面这些常量是**近战默认值**，也是「招式没写索敌参数」时的兜底
（远程招式不适用，见 [8.4](#84-每一招的交战形态engagement)）：

| 参数 | 值 | 作用 |
|---|---|---|
| `ACQUIRE_RANGE` | 6 格 | **首次**索敌的最大距离 |
| `KEEP_RANGE` | 9 格 | **保持**锁定的最大距离（比索敌远） |
| `ACQUIRE_ANGLE` | 55° | 索敌视角半角（正前方这个锥形内才索） |
| `KEEP_ANGLE` | 80° | 保持锁定的视角半角（比索敌宽） |
| `SWITCH_MARGIN` | 1.25 | 换目标需要分数高出 25% |
| `RANGED_KEEP_MARGIN` | 2 格 | 远程招式的保持圈余量（纯粹防抖） |

「索敌圈比保持圈小」就是经典迟滞：走进两个怪的中间不会来回跳，
因为已经锁上的那个有 9 格 / 80° 的宽容度，而新目标要明显更好才抢得走。

> 索敌距离**故意不大**：近战只要比攻击距离（默认 3 格）多一点就够了，差额交给突进补。
> 设成十几格会锁到根本没在打你的怪。

### 丢锁（不会绑死）

| 情况 | 行为 |
|---|---|
| 目标死亡 / 卸载 | **立刻**丢 |
| 超出保持圈 或 背对超过保持角 | 累计 10 刻（`LOSE_GRACE_TICKS`）后丢 —— 短暂抖动不掉锁 |
| **追不上了**（隔着方块、在地板下、飞在天花板上面） | 同样累计 10 刻后丢，见 [8.12](#812-追击可行性追得到才追) |
| 连续 100 刻（5 秒）没发起过攻击 | 自动松锁 |
| 代码里 `CombatTargeting.release(player)` | 随时手动丢 |

**转头丢锁就是靠保持角 + 宽限**：转身背对目标超过 80°，10 刻后就掉了 ——
这正是你说的「转向也很难丢弃对象」的修复。

### 参数是「每一招」带进来的

保持圈不再是全局常量，而是**跟着招式走**：每次出手把这一招的
`CombatTargeting.Params`（索敌距离/角度 + 保持距离/角度 + 攻击距离 + 追不追人）记到玩家状态上，
随后每刻的保持校验也用它。

于是可以做到「远程招式锁 12 格、近战招式锁 6 格」而不互相打架 ——
放完远程再冲上去砍，锁不会因为上一条参数还留着而飘。

### 双端各自一份

| 端 | key | 谁在维护 | 干什么用 |
|---|---|---|---|
| 客户端 | `C:<uuid>` | `ActionStateMachine` 每 tick 调 `CombatTargeting.tick` | 转向、突进判定（要零延迟） |
| 服务端 | `S:<uuid>` | 攻击请求包喂 `CombatTargeting.lock`，`CharacterTickHandler` 每 tick 校验 | 召唤物问「主人正在打谁」、决定要不要跳过动作自带位移 |

客户端是本机玩家的移动/朝向权威，服务端只需要知道「玩家锁了谁」。
服务端那份由 RPC 更新，不需要额外的同步包。

> **服务端那份也必须每刻 tick**（`core/character/CharacterTickHandler`）：否则锁只会被喂进来、
> 永远不会过期，`ActionManager.scheduleStepMovement` 里那句 `if (isLocked) return`
> 就从此一直成立 —— 服务端再也不执行动作自带的 `moves` 位移。
> 玩家下线时 `ServerAnimationTicker.onPlayerLoggedOut` 里 `clear` 掉，避免静态表堆积。

## 8.4 每一招的交战形态（Engagement）

**近战还是远程，判定单位是「招式」，不是「角色」。**
同一个角色完全可能既有近战普攻又有远程战技（弓手砍人、法师抡杖），按角色分只能二选一。

### 两种大形态

| | 近战招式（默认） | 远程招式 `Engagement.ranged()` |
|---|---|---|
| 突进 | 目标在攻击距离外 → 贴上去 | **永不突进** |
| 转向 | 滑向目标（[8.6](#86-转向快但不瞬移)） | 一样滑向目标 |
| 索敌距离 | 默认 6 格（比攻击距离大，差额交给突进） | **= 攻击距离**（够得着才锁） |
| 保持圈 | 默认 9 格 | 索敌 + 2 格（只防抖） |

> 远程招式「索敌 = 攻击距离」的意思很直接：**够不着就不锁、也不存在需要突进的情况**。
> 你不会因为按了一下弓就自己冲上去。
>
> ⚠️ 所以远程招式**一定要配 `withAttackRange(x)`** —— 不配的话那一招的攻击距离是近战默认的
> 3 格，索敌也跟着只有 3 格，等于「贴脸才算远程」。

### 写在哪：`ActionStep.engagement`，不是 `Hit`

```java
ActionStep step = new ActionStep("skill_arrow", 25, 0, 2, moves, hits, sounds, 0, 0, 120, 8)
        .withAttackRange(12f)   // 这一招够得着 12 格
        .ranged();              // ← 远程：不突进、索敌 = 12 格
```

**为什么不写进 `Hit`**：一个招式可以有多个 hit（各自 `delay` / `forward` / `scope`），
而「这一招是不是远程」是**整段动作**的属性 —— 它决定的是「要不要贴上去、索敌多远」，
不是某一次伤害结算的属性。写进 hit 会出现「同一招里第一个 hit 说是远程、
第二个说是近战」这种没有意义的组合，所以放在 step 上，整招共用一份。

### 字段全表

| 字段 | 默认 | 说明 |
|---|---|---|
| `ranged` | `false` | 远程招式开关；开了就永不突进 + 索敌跟攻击距离走 |
| `dash` | `true` | 允不允许突进（远程招式里这个开关无效） |
| `acquireRange` | `USE_DEFAULT` | 索敌距离；近战兜底 6 格，远程兜底 = 攻击距离 |
| `keepRange` | `USE_DEFAULT` | 保持锁定距离；近战兜底 9 格，远程兜底 = 索敌 + 2 |
| `acquireAngle` / `keepAngle` | `USE_DEFAULT` | 索敌 / 保持的视角半角（度） |
| `stopDistance` | `USE_DEFAULT`（1.4） | 突进贴到离目标几格停下 |
| `dashSpeed` | `USE_DEFAULT`（0.85） | 突进速度（格/刻） |
| `maxApproachTicks` | `0`（= 20） | 突进最多几刻，超时就照打 |
| `turnSpeed` | `USE_DEFAULT`（45） | 转向最大角速度（度/刻） |
| `adhesionBand` | `USE_DEFAULT`（1.5） | 吸附带：目标在「攻击距离 + 这个」以内才在出手时推一小步；写 0 = 这一招不吸附 |
| `adhesionStep` | `USE_DEFAULT`（0.22） | 出手那一下的推力（冲量）；写 0 = 这一招不吸附 |

`USE_DEFAULT = -1` 表示「不覆盖，用全局默认」，所以只写关心的那一两项就行：

```java
// 1. 远程招式 —— 最常见的写法
step.ranged();

// 2. 近战但不想突进（原地挥砍的大招）
step.withEngagement(Engagement.melee().withDash(false));

// 3. 长枪：够得着 5 格，索敌 8 格，冲过去停 3 格
step.withAttackRange(5f).withEngagement(
        Engagement.melee().withAcquireRange(8).withDashProfile(3.0, 0.9, 24));

// 4. 飞刀：索敌 12 格、只转向、甩得从容一点
step.withEngagement(
        Engagement.ranged().withAcquireRange(12).withAngles(40, 65).withTurnSpeed(28));
```

### 所有招式都走同一条链路

`engageAndPlay()` 是普攻 / 重击 / 战技 / 大招**共用**的出手流程，
所以给战技标 `ranged()` 也照样生效（闪避除外 —— 闪避不索敌，方向由玩家输入决定，
自动转向会把「往后闪」变成「往怪身上闪」）。

## 8.5 一次攻击的完整流程

```
按下左键 / 战技 / 大招 / 重击
  └─ ActionStateMachine → ResourceDrivenActionHandler
       └─ engageAndPlay(def, priority, serverCall)
            ① 取这一招的交战形态：step.engagement
            ② 取这一招的攻击距离：step.effectiveAttackRange()
            ③ 索敌：CombatTargeting.acquire(player, 按本招式算出的 Params)
                    已有锁且仍有效 → 优先保持（不会因为换了招式就乱换目标）
                    本招式范围内没找到 → 沿用现有锁定（够不着也还是对着他转）
                    然后 lock(player, target, params) 把参数记进状态
                     ★ 近战招式还会先过一道「追不追得上」（ChaseReach，见 8.12）：
                       身体过不去、且被拦下的地方也够不着 → 直接不算候选
            ④ 分情况：
               ┌ 没目标                    → 照旧：播动画 + 服务端请求
               │                             位移交给动作自带的 moves
               ├ 远程招式 / 目标在攻击距离内 → 只转向（几刻内滑过去，不位移）
               │                             → 立刻播动画 + 发请求（带目标 id）
               └ 近战 + 目标在距离外        → 起手动画冻在那一帧
                                             → 每 tick 滑向目标 + 朝目标直线冲（**全向，含 Y**）
                                             → 进到「停靠距离 + 0.6」就算到位
                                               ：解冻 + 计时拨回满值
                                               + 摆臂 + 音效 + 发请求（带目标 id）
                                             ★ 起点可以延后：step.dashStartDelay > 0 时
                                               前 n 刻先播「起跳 / 变身」，第 n 刻才冻结并冲
                                             ★ 追不动了（撞墙 / 距离不再缩小，见 8.13）
                                               也按「到位」处理：当场停下开打
```

> **到位判定留了宽容度**（`ARRIVE_SLACK = 0.6`）：冲刺的时候目标也在动，
> 要求精确贴住会出现「人已经贴脸了，但因为它挪了一步所以永远没到位」——
> 表现就是贴着目标鬼畜。差不多就出手，剩下的交给命中判定。
>
> **突进途中不再按剩余距离收力**（`step = min(dashSpeed, 剩余到目标的距离)`）：
> 早期写的是 `min(dashSpeed, distance - 到位线)`，靠近到位线时力度会被压得很小，
> 而目标正在往外走（怪的速度量级差不多就是 0.2 格/刻）时两边刚好抵消 ——
> 距离永远卡在到位线上一点点，人就**跟着目标跑**直到 20 刻超时，动画全程冻着。
>
> **再加一条兜底**：已经进到这一招的攻击距离、却连续 `ARRIVE_PATIENCE_TICKS`（3）刻
> 没进到位线 → 直接开打（反正已经打得到了）。目标贴着到位线来回动时不会再有「跟着跑」。
>
> **突进是全向的**（含竖直分量）：目标是飞的、或者站在你脚下时，
> 只推水平分量会永远差一段高度 → 到位判定迟迟不成立 → 在头顶/脚底反复横跳。
> 现在朝目标位置直线过去。

三个要点：

- **转向和动画并行**，不是「先转完再出手」—— 转向不会给按键加任何延迟；
- **远程招式走的是中间那条**，只转不冲；
- 请求统一带目标 id，服务端每个招式（普攻 / 重击 / 战技 / 大招）都会
  更新自己那份锁，召唤物问「主人在打谁」永远拿到最新的。

### 吸附：每一刀各自带一小步（不是一直吸）

上面第三条分档里，还差中间那一段：目标**就在够得着的边缘**，退半步、绕着你走。

要的手感是「**攻击自带吸附**」，而不是「人像磁铁一样被一直拖着走」。所以吸附是
**出手那一瞬间的一次推力**（`AttackApproach.stepToward`），一次一小步：

```
第 1 刀：目标在 2.4 格外 → 这一刀把它带进 ~1.9
目标挪开半步 → 人站着不动（这一刀已经打完了）
第 2 刀：再带一小步，又贴回去
```

因为每次只走一小段、而连招之间间隔很短，看起来就是「每一刀都在往目标身上贴」，
既不会站着干挥，也不会像被吸住一样持续平移。

| 参数 | 值 | 作用 |
|---|---|---|
| `ADHESION_BAND` | 1.5 格 | 目标在「攻击距离 + 这个」以内才推；更远是突进的活 |
| `ADHESION_STEP_SPEED` | 0.22 格/刻 | 推力上限（冲量），实际位移 ≈ 半个格 |
| `ADHESION_TRAVEL_FACTOR` | 2.5 | 冲量 → 位移的换算（地面摩擦 ≈0.4/刻），用来按缺口收力 |
| `ADHESION_MIN_DISTANCE` | 0.9 格 | 比这还近就不推（推了会穿模/穿过去丢锁） |

**这四个默认值是怎么定出来的**（不是拍脑袋，每一步都有依据）：

| 数 | 推导 |
|---|---|
| 生效范围 = `攻击距离 + 1.5` | 「小步移动」的量级：对手一次绕后/退半步大约 **0.5~1 格**，加上一点余量取 1.5。再大就该突进了（突进是从远处冲，不是微调） |
| 一步 = 0.22 冲量 ≈ 0.5 格 | 目标是要「补上对手挪开的那半步」。地面每刻摩擦约 ×0.6（即每刻掉 0.4），冲量 `v` 大约滑出 `v/0.4 = 2.5v` 格；要 0.5 格 → `v ≈ 0.2` ✔ |
| `TRAVEL_FACTOR = 2.5` | 上面那条换算的倒数，用来「按缺口收力」：缺口小的时候自动推得轻，避免贴脸时撞一下 |
| `MIN_DISTANCE = 0.9` | 玩家碰撞箱半宽 0.3 + 目标半宽 ~0.3 + 一点余量 → 再近就该停手，否则会从目标身上穿过去、把锁也丢了 |

连招间隔（0.5~2.5 秒一刀）比单次位移的余量长得多，所以「每刀补半步」足以跟住对手，
不会出现「人明明够得着却站着干挥」——这就是**用跟随替代频繁突进**的落点。

**逐招式覆盖**（`Engagement.withAdhesion(band, step)`，不写就用上面的全局默认）：

```java
// 大剑：带得更宽、一步更沉（一刀就要贴上去）
step.withEngagement(Engagement.melee().withAdhesion(2.0, 0.30));

// 短刀：贴身才吸、步子轻
step.withEngagement(Engagement.melee().withAdhesion(1.2, 0.16));

// 这一招完全不要吸附（远程、或者纯炮台）
step.withEngagement(Engagement.ranged().withAdhesion(0, 0));
```

三个取舍：

| 取舍 | 为什么 |
|---|---|
| **一次推力，不是每刻跟随** | 每刻跟 = 「被吸住一直贴过去」，那不是吸附是磁铁；而且玩家自己的移动会被一直干扰 |
| **只管水平** | 竖直交给突进和原版重力 —— 吸附要是也去动 Y，玩家一跳就被拽回去 |
| **按缺口收力**（`gap / TRAVEL_FACTOR`） | 贴脸时推满会往前撞一下、甚至穿过去；缺口小的时候要收着劲 |

**和突进的分工**：缺口 ≤ 1.5 格 → 推一小步；缺口更大 → 走突进（从远处冲过去）。
所以吸附不该频繁触发，突进也不该被小碎步触发。

> **那 `ActionStep.moves` 呢？** 服务端的 `moves`（`ServerActionExecutor`）在锁着目标时**仍然是关的**
> （见 [8.10 位移](#810-位移有目标就不推了)）—— 它是「沿视线推一段固定冲量」，
> 在玩家实体身上本来也不可靠，还会和服务端的权威位置打架。
> 「每一刀带一小步」这件事由客户端吸附负责：**0 延迟、方向永远对着目标**。

## 8.6 转向：快，但不瞬移

一按就把人掰到目标方向，看起来像贴图被翻转了一下 —— 少了「发力」的过程。
`AttackApproach` 用**衰减式追角**：

```
每刻转动量 = clamp(剩余夹角 × TURN_GAIN, TURN_MIN_SPEED, TURN_MAX_SPEED)
```

| 参数 | 值 | 作用 |
|---|---|---|
| `TURN_GAIN` | 0.45 | 每刻消掉剩余夹角的比例，越大越急 |
| `TURN_MIN_SPEED` | 6 度/刻 | 小角度也保持一点惯性，最后几度不会顿住 |
| `TURN_MAX_SPEED` | 45 度/刻 | = 900°/秒，大角度也不会一帧到位 |
| `TURN_TOLERANCE` | 1.5 度 | 小于这个就算转到了，直接吸附 |
| `TURN_BUDGET_TICKS` | 16 刻 | 一次动作最多持续转向多久（突进期间不消耗） |

180° 大转身大约 **6~7 刻（0.3 秒）** 转完：前几刻按上限甩过去，最后几度收着劲靠上去。
曲线是「先快后慢」的指数衰减，所以既有反应速度，又能看出转身。

**两条硬约束**（都踩过坑，改这个类时别破坏）：

1. **绝不过冲**。转动量必须夹在「剩余夹角」以内 —— 用 `Mth.approachDegrees` 实现。
   早期版本写成 `yRot + sign*speed`，而 `TURN_MIN_SPEED` 有 10°：剩余夹角只剩 5° 时
   会一步跨到对面、下一帧再跨回来，于是**镜头左右乱晃**
   （±5° 的极限环，一次攻击抖大半秒）。
2. **只转身体，绝不碰镜头**。写的是 `yBodyRot`（模型朝向），**不是** `yRot`。
   本机玩家的 `yRot` 就是镜头朝向，碰它哪怕只转 5°，玩家也会觉得画面被拽了一下 ——
   所以玩家的视角永远 100% 归玩家。`yBodyRot` 只影响别人/第三人称看到的模型朝向
   （`CharacterRenderDispatcher` 就是拿它摆模型的）。

其它细节：

- **突进期间不消耗转向预算** —— 追着人跑的时候当然要一直对着他；
  到位后剩下的预算继续用完，最后那几度是在攻击动画里转完的。
- **远端玩家看到的身体朝向**：`yBodyRot` 不走网络（原版也不发），
  所以别人看到的是「身体跟着头走」，而不是精确对着目标 —— 只影响观感，
  伤害判定走的是服务端的目标 id，不受影响。
- 想连身体都不转（完全交给动画），把 `tickFacing` 里的 `setBodyYaw` 调用去掉即可。

## 8.7 突进期间动画怎么处理

三种做法里选了**冻结在起手帧**：

| 方案 | 评价 |
|---|---|
| 专门准备一套突进动画 | 要额外美术资源，而且每段攻击都要一套 —— 成本高 |
| **让动画停在某一帧**（选这个） | 不需要新资源，正好对应「手搭到剑柄上 → 保持 → 到位继续」 |
| 干脆不管，到位才播动画 | 按下去到起步之间没有反馈，手感空 |

实现只有两行：

```java
// AttackApproach.begin → 状态机标记冻结
ActionStateMachine.setApproachFrozen(true);

// PlayerAnimationController 每帧：冻结期间把动作暂停在起手帧
if (ActionStateMachine.isApproachFrozen() && 目标动画 == 当前动画) {
    return PlayState.PAUSE;
}
```

`PlayState.PAUSE` 是 GeckoLib 的「继续动画但冻结在当前进度」，所以起手动作会**定住**
而不是回到第 0 帧。到位时 `setApproachFrozen(false)`，动画从冻结那一帧接着往下播。

**动画时间轴也一起冻结**：`ActionStateMachine` 的动画倒数整块被 `if (!approachFrozen)` 包住。
不这么做的话，动作的 `duration` 比突进耗时短时，「动画自然播完 → `resetToDefault`」会抢在
到位之前发生 —— 那一刀连同还没发出去的服务端请求一起被吞掉（**有动画、没伤害、冷却也不扣**）。
硬直与定身的倒数同样在冻结期间暂停，到位时由
`resumeFromApproach(总时长, 硬直, 定身)` 一起重新起算（飞过去本身属于执行期）。

### 从第几刻开始冻结：`dashStartDelay`

默认是「按下那一帧就冻结」，但有些招式的**前几帧本身就是动作**，必须先播出来：

```
薇斯娜三阶 E（原神效果）：
   0 ──────── 8 ────────── 冻结 ──── 到位 ──── 继续播
   │  起跳 · 人消失 · 化作细长螺旋   │  朝目标突刺   │  突刺收招
```

配了 `ActionStep.withDashStartDelay(n)` 之后：前 n 刻动画照常播（人还在原地），
第 n 刻才冻结并突进。实现上就是把 `AttackApproach.begin` 塞进一个 n 刻的延迟任务：

```java
play(...);                                   // 先播起手，不冻结
int seq = ActionStateMachine.actionSequence();
ActionStateMachine.queueClientWork(n, () -> {
    if (ActionStateMachine.actionSequence() != seq) return;   // 中途换招 → 突进作废
    ... AttackApproach.begin(...);                            // 第 n 刻才冻 + 冲
});
```

> **`actionSequence` 那道校验必须有**：延迟任务挂在静态队列上，
> `resetToDefault` 会清队列，但普通换招（`changeState`）不会 ——
> 不校验的话，前 n 刻里接了别的招，n 刻后还会凭空补一次突进。

**时间零点跟着推后**：不管最后有没有真的冲（也可能 n 刻后目标已经走进攻击范围了），
「解冻 / 出手」都发生在第 n 刻，`hits[].delay` 一律从那一刻起算。
所以写 delay 时不用管这个值 —— 它只负责把「动作开始」整体往后推 n 刻。
突进自己有 `MAX_APPROACH_TICKS` 兜底，所以冻结不会卡死。

## 8.8 伤害与音效为什么按「到位」起算

`ActionStep.hits[].delay` 天然是「相对动画起点」的。有突进时动画起点被推后了，
所以到位那一刻要把计时器拨回满值：

```java
// ActionStateMachine.resumeFromApproach
approachFrozen = false;
animationTick = Math.max(1, totalTicks);   // ← 从这一刻重新起算
```

同时**服务端请求也是到位才发**，所以服务端的 `ActionState` 同样从到位那刻开始计时 ——
两边对齐，伤害不会因为距离远近而飘。

> 这也是为什么「发起请求」被包在 `AttackApproach` 的回调里，而不是按键当帧就发。

**音效同理**：`ActionStep.sounds` / `soundCues` 里的 `delay` 也是「相对动画起点」，
所以突进时**不在按键那帧排音效**，而是到位那一刻和摆臂一起排
（`play(..., feedbackNow = false)` 把两件事一起推后）。
不这么做的话，`delay = 3` 的挥砍声会在人还没冲过去时就响掉 —— 声音和动作完全脱节。

## 8.9 位移：有目标就不推了

`ActionManager.scheduleStepMovement` 加了一条：

```java
if (CombatTargeting.isLocked(player)) return;   // 锁着目标 → 不排动作自带的位移
ServerActionExecutor.execute(player, def.step, character.getTextureId());
```

对应你说的三条：

| 情况 | 行为 |
|---|---|
| 近战、已经在目标跟前 | **不推** —— 否则打两下就穿到怪背后、丢目标 |
| 目标想跑 | 靠 `AttackApproach` 的突进贴上去，而不是靠固定位移 |
| **远程招式** | 也不推 —— 够得着就站着放，够不着也不冲（这是远程的定义） |
| **没有目标** | 完整执行每一段的 `moves`，保持原来的手感 |

## 8.10 召唤物索敌模式

`SummonTargeting` 三个模式：

| 模式 | 行为 | 用在哪 |
|---|---|---|
| `OWNER_TARGET`（**默认**） | 优先咬主人当前锁定的目标；主人没在打就保留自己的目标；自己也没有才独立找 | 大多数召唤物 |
| `OWNER_TARGET_ELSE_SELF` | 严格跟主人，主人没目标就现找（不留旧目标） | 完全听指挥的召唤物 |
| `INDEPENDENT` | 完全独立索敌 | 有自己 AI 的召唤物 |

**为什么按「玩家」而不是「角色」取目标**：召唤物是角色召唤的，但那个角色可能已经挂后台
（玩家切人了），而玩家本身一直在场。所以问的是 `CombatTargeting.current(player)`，
服务端那份锁由攻击请求包喂进来，读得到。

用法（`VesnaAttackProjectile` 已经是这样）：

```java
private static final SummonTargeting SUMMON_TARGETING = SummonTargeting.defaultMode();

target = SUMMON_TARGETING.resolve(
        getOwnerPlayer(),            // 召唤者的玩家实体
        target,                      // 自己已有的目标
        SEARCH_RADIUS,               // 自己的索敌半径
        () -> new TargetSeeker(...).execute());   // 独立索敌时用哪个方法
```

薇斯娜的风铃就是 `OWNER_TARGET`：**优先打薇斯娜（玩家）当前锁定的那个目标**。

## 8.11 手感调参速查

### 按招式调（最常用）

| 想改什么 | 改哪 |
|---|---|
| 这一招是远程 | `step.ranged()`（不突进 + 索敌 = 攻击距离） |
| 这一招够得着多远 | `step.withAttackRange(x)`，或让它按 `hits[0].forward + scope` 自动推 |
| 这一招不突进 | `step.withEngagement(Engagement.melee().withDash(false))` |
| 这一招索敌多远 / 多宽 | `Engagement.melee().withAcquireRange(8).withAngles(40, 65)` |
| 这一招冲多快 / 停几格 / 冲多久 | `Engagement.melee().withDashProfile(停几格, 速度, 最多几刻)` |
| 这一招转多快 | `Engagement.ranged().withTurnSpeed(28)` |
| 技能「CD 转了但效果没出」（按下去就被打断） | 给这一段配**执行期**：构造参数 `protectDuration` 盖住最后一个 `hits[].delay`（`0` = 整段可打断），见 [3.3](#33-前摇的两种三窗口与打断规则) |
| 想做有吟唱、能被打断的技能 | `.withPrepareTicks(n)`：前 n 刻是准备阶段 |
| 执行期太长、按着不舒服 | 执行期只需要「盖住伤害点 + 1~2 刻」，不是越长越好；剩下的都留给后摇 |

写在 `XxxResources.ACTION_DATA` 里，普攻每一段、战技、大招各写各的。

### 按全局调（所有招式都受影响）

| 想改什么 | 改哪 |
|---|---|
| 近战默认索敌远/近 | `CombatTargeting.ACQUIRE_RANGE` / `KEEP_RANGE` |
| 索敌角度宽/窄 | `ACQUIRE_ANGLE` / `KEEP_ANGLE` |
| 太黏 / 太容易换目标 | `SWITCH_MARGIN`（调大更难换）、`KEEP_RANGE`、`IDLE_RELEASE_TICKS` |
| 转头丢锁太快/太慢 | `KEEP_ANGLE` 与 `LOSE_GRACE_TICKS` |
| 转向快慢 / 手感 | `AttackApproach.TURN_GAIN`（急缓）、`TURN_MIN_SPEED`（收尾顿挫）、`TURN_MAX_SPEED`（上限） |
| 默认突进速度 / 最长时间 | `AttackApproach.DASH_SPEED`（1.0 格/刻）/ `MAX_APPROACH_TICKS` |
| 突进「到没到位」太苛刻 | `AttackApproach.ARRIVE_SLACK`（到位宽容度，默认 0.6 格） |
| 目标乱动时人一直跟着跑不出手 | `AttackApproach.ARRIVE_PATIENCE_TICKS`（够得着连续 3 刻就强制开打）；同时确认没在按剩余距离收力 |
| 吸附范围 / 一小步多大 | `AttackApproach.ADHESION_BAND`（1.5 格）、`ADHESION_STEP_SPEED`（0.22 格/刻冲量），或逐招 `.withAdhesion(带, 力度)` |
| 吸附贴脸时把人撞穿 | `AttackApproach.ADHESION_MIN_DISTANCE`（默认 0.9 格以内就不推了） |
| 位移会「原地起飞」 | `ActionStep.moveAllowsVertical`（默认 **false** = 位移只走水平面）；要保留上挑/跳劈就 `.withVerticalMove(true)` |
| 隔着墙/地板还去追 | 见 [8.12](#812-追击可行性追得到才追)：`ChaseReach`（近战自动判；不想要就把 `Params.forChase` 换回不判的模式） |
| 追不到时在旁边逗留半天 | 见 [8.13](#813-保护性原则停下来就开打)：`DASH_BLOCKED_PATIENCE_TICKS` / `DASH_STALL_PATIENCE_TICKS`（默认各 2 刻） |
| 突进要等前几帧播完才开始 | `ActionStep.withDashStartDelay(n)`（起跳/变身类招式，见 [8.7](#87-突进期间动画怎么处理)） |
| 谁能被索敌 | 注册自己的 `TargetPolicy`，或 `registerTargetableNonLiving` |

### 招式里写 -1 / 0 代表什么（哨兵与默认值）

所有数值字段的**哨兵是 `-1`**（`Engagement.USE_DEFAULT`），判定条件是 `> 0` ——
也就是说写 `-1`、写 `0`、写任何负数，**效果都是「这一项不覆盖，用默认」**。
默认值分两处定义，跟着字段归口：

| 招式字段 | 写 -1 时实际用的值 | 在哪里定义 |
|---|---|---|
| `acquireRange` 索敌距离 | 近战 **6.0** 格；远程 **= 这一招的攻击距离** | `CombatTargeting.ACQUIRE_RANGE` / `Params.forRange(attackRange)` |
| `keepRange` 保持距离 | 近战 **9.0** 格；远程 **= 索敌 + 2.0** | `CombatTargeting.KEEP_RANGE` / `RANGED_KEEP_MARGIN` |
| `acquireAngle` 索敌半角 | **55°** | `CombatTargeting.ACQUIRE_ANGLE` |
| `keepAngle` 保持半角 | **80°** | `CombatTargeting.KEEP_ANGLE` |
| `stopDistance` 停几格 | **1.4** 格 | `AttackApproach.STOP_DISTANCE` |
| `dashSpeed` 突进速度 | **1.0** 格/刻 | `AttackApproach.DASH_SPEED` |
| `maxApproachTicks` 最多冲几刻 | **20** 刻 | `AttackApproach.MAX_APPROACH_TICKS` |
| `turnSpeed` 转向角速度 | **45** 度/刻 | `AttackApproach.TURN_MAX_SPEED` |
| `adhesionBand` 吸附带 | **1.5** 格（写 **0 是真关掉**） | `AttackApproach.ADHESION_BAND` |
| `adhesionStep` 吸附推力 | **0.22**（写 **0 是真关掉**） | `AttackApproach.ADHESION_STEP_SPEED` |

解析发生在这四个地方（`-1` 就在这里被换成真值，所以想找默认值只需要看这四处）：

| 字段组 | 解析处 |
|---|---|
| 索敌/保持的距离与角度 | `ResourceDrivenActionHandler.targetingParams()` → `CombatTargeting.Params.forChase(攻击距离)`（近战）/ `forRange(攻击距离)`（远程） |
| 突进的三件套 + 到位线 | `AttackApproach.begin()` |
| 转向角速度 | `AttackApproach.startFacing()` |
| 吸附的两项 | `AttackApproach.stepToward()` |

**三个容易踩的点**：

1. **吸附是唯一的例外**：它的判定是 `>= 0`，所以 `-1` = 用默认、`0` = 这一招不吸附。
   `withAdhesion(0, 0)` 是「关掉」，不是「用默认」。
2. **`keepRange` 会被兜底抬高**：`Params` 的紧凑构造器强制执行
   `keepRange = max(keepRange, acquireRange)`（`keepAngle` 同理）——
   只写 `withRange(12, -1)` 的话，保持距离会是 12 而不是 9，不会出现「刚锁上就掉」。
3. **`attackRange` 为 0 时兜底成 3.0 格**（`AttackApproach.begin`），远程招式的索敌距离也跟着这个值走。

---

## 8.12 追击可行性：追得到才追

**文件**：`core/system/combat/targeting/ChaseReach.java`

索敌原来只看「距离 + 视角」，于是会出现几种很蠢的情况：
目标在墙后、在楼上、在**地板下面** —— 距离都在范围内，可人冲过去只会撞墙，
然后一路追到 `MAX_APPROACH_TICKS` 超时才出手（表现就是「贴着墙蹭」「在够不着的目标旁边逗留」）。

`ChaseReach.canCloseIn(player, target, attackRange)` 把「身体能不能真的过去」夹出来：

```
从玩家身上取两根采样线（脚 0.2 / 腰 1.2 高度）射向目标
   ├─ 一路通畅                      → 追得上（能贴到目标身前）
   ├─ 被方块挡下，但拦停点          → 也追得上：冲过去撞墙就停在那儿，
   │   离目标 ≤ 攻击距离 + 0.5        而那个位置本来就够得着（撞墙 ≠ 追不上）
   └─ 被挡下且够不着                → 追不上：不锁、不追
```

**高度还要单独卡一道**：直线距离对垂直方向特别宽松 ——
你站地板上、目标在地板下 2 格，直线距离只有 2 格「看着够得着」，其实中间隔着地板。
所以拦停点还要过 `|Δy| ≤ 攻击距离 × 0.75`（`ChaseReach.VERTICAL_REACH_FACTOR`），
地板下 / 天花板上的目标都会被挡掉。

**另外一条一票否决：目标在脚下 + 头上有盖子 → 完整隔断。**
典型是房子 1L / -1L（-1L 两格高、天花板就是 1L 的地板）里刷了怪 ——
它就在你正下方，拦停点离它只有 2~3 格，上面那道高度容差**拦不住**。
`ChaseReach.isSealedBelow(...)` 用两步判（都很保守，避免误杀）：

```
① 拦停点比目标高不到 0.5 格 → 只是「旁边有堵墙」，不是盖子 → 放过
② 从目标头顶往上打到拦停点下方：打不到方块 → 放过（它只是贴着墙，绕一下就能打）
   打到方块 → 它被盖住了 → 一票否决：不锁、不追
```

「站悬崖上打崖下的怪」不会被误伤：崖壁在它**旁边**，它头顶到崖顶那条竖线是空的。

只判**方块**（`Block.COLLIDER`）：草、水、火把不参与；其他生物也不参与（怪挡怪不该让人放弃追击）。

**什么时候才判**：只有<b>会突进的招式</b>（`CombatTargeting.Params.chase() == true`）。
远程招式索敌距离 = 攻击距离、本来就不追人，隔着墙照样锁（那是射击的事）。
参数由 `ResourceDrivenActionHandler.targetingParams()` 生成：
近战 → `Params.forChase(攻击距离)`，远程 → `Params.forRange(攻击距离)`。

**用在两处**：`findBest()`（索敌时跳过追不到的目标）和 `isValid()`（保持校验，
追不到就进宽限计时，最终丢锁）。

## 8.13 保护性原则：停下来就开打

**文件**：`client/combat/AttackApproach.java`

> 突进的目的是**快速把人带进自己的攻击范围**，不是百分百贴身。
> 所以只要接近这件事做不下去了，就当场停下来出手 —— **不管因为什么**。

除了「到位」和「够得着」两条正常出口，再加一条兜底出口，用两个互相独立的迹象判断：

| 迹象 | 判据 | 常量 |
|---|---|---|
| ① 身体没动（撞墙 / 被方块顶住） | 本刻实际位移 < 0.05 格 | `DASH_BLOCKED_PATIENCE_TICKS`（2 刻） |
| ② 距离没缩小（目标在方块后面 / 头顶打转 / 跑得一样快） | 本刻距离没比上刻少 0.05 格 | `DASH_STALL_PATIENCE_TICKS`（2 刻） |

任一连续成立 2 刻 → `finish(player, true)`：原地急停 + 立刻走「到位」那条路
（解冻动画、排音效、发服务端请求）。**从「停下来」到「出手」最多 2 刻，不会再逗留到超时。**

这条是<b>为了兜住没想到的情况</b>：8.12 的可行性判定只能挡住已知的几种
（墙、地板、天花板），而「本来看着能追、追到一半追不动了」永远会有新花样
（目标瞬移、被别的怪挤开、卡在栅栏缝里……）。有了它，新花样的最坏表现是
「冲了一下就开打」，而不是「一路吸附追到天荒地老」。

> 它和 `ARRIVE_PATIENCE_TICKS`（够得着连续 3 刻就开打）不冲突，是两级兜底：
> 前者管「已经够得着但进不了到位线」，后者管「根本没能再靠近」。

---

# 第九部分 · 资产路径与图标

## 9.1 统一布局

```
assets/minegenshin/
├── character/<角色id>/      ← 角色：一个角色一个文件夹，内部再按类型分
│      vesna.geo.json          （可选，缺项时共用 character/linweiyun/linweiyun.geo.json）
│      vesna.animation.json
│      vesna_fp.animation.json （可选，第一人称单独一个文件）
│      textures/vesna.png      （角色模型贴图；缺项时共用 character/linweiyun/textures/linweiyun.png）
│      textures/avatar.png     ← 列表头像
│      textures/avatar_hud.png ← HUD / 圣遗物佩戴者叠加头像
│      textures/pose_prepare.png  ← 编队立绘：可选中的角色
│      textures/pose_already.png  ← 编队立绘：已在队伍
│      textures/skill.png / burst.png ← 元素战技 / 元素爆发图标
│      sounds.json             （可选：只有要字幕/多音色/权重才需要写）
│      sounds/
│            attack_1.ogg      ← 丢进来就生效：事件名 = <角色id>_<文件名>
│            skill.ogg
│      local/<名字>.geo.json    ← 自己的模型 / 动画放这里：明文直读，同名时优先
├── item/<物品id>/           ← 物品：入口文件 + 贴图与图标
│      definition.json         物品定义（原版入口 items/<id>.json）
│      model.json              平面模型（原版入口 models/item/<id>.json）
│      textures/texture.png    平面贴图（图集 sprite：minegenshin:item/<id>/texture）
│      textures/icon.png       GUI 图标，ItemIcons 的第一优先
│      <id>.geo.json / <id>.animation.json / textures/<id>.png   （geo 物品才需要）
├── block/<方块id>/          ← 方块：入口文件 + 贴图 + 方块物品
│      blockstate.json         方块状态（原版入口 blockstates/<id>.json）
│      model.json              方块模型（原版入口 models/block/<id>.json）
│      textures/texture.png    方块贴图（图集 sprite：minegenshin:block/<id>/texture）
│      blockitem/{definition,model}.json + blockitem/textures/…
├── entity/<实体id>/         ← 提瓦特实体
│      <id>.geo.json / <id>.animation.json / textures/<id>.png
├── gui/<名字>.png           ← 共用界面贴图（血条 / 遮罩 / 边框 / 按钮 / 占位图）
├── icon/<分类>/<名字>.png    ← 跨对象图标（elemental/pyro.png 等）
└── lang/                     ← 原版语言文件（唯一剩下的原版入口）
```

**模型与动画：资源包 + `local/` 明文目录。** 自带角色的模型与动画**不再以一份份可读 JSON
出现在仓库与发行包里**，而是收进一个资源包（整包一个文件，具体位置属实现细节）。
对外只有三条约定：

1. **逻辑路径不变**：代码里照旧写 `character/vesna/vesna.geo.json`，后缀判断、缓存键、
   目录索引全部沿用本文档的规则，读取侧只换了「字节从哪来」；
2. **自己的模型 / 动画放 `local/`**：`character/<角色id>/local/vesna.geo.json`、
   `entity/<实体id>/local/<id>.animation.json` 这类路径**明文直读**，放进去重启即生效，
   同名时**`local/` 里那份优先于资源包**；
3. **磁盘优先于资源包**：同一逻辑路径仓库里也有文件时以文件为准，方便临时对照调试。

`local/` 只是「从哪读、谁优先」，**不算资源身份**：`character/vesna/local/vesna.geo.json` 与
`character/vesna/vesna.geo.json` 是同一个键，所以 `CharacterRenderData` 和调用方一个字都不用改。

**纹理与音频各占一个子目录**：一个角色以后会有很多张图（模型贴图、头像、立绘、图标）和很多条语音，
所以对象内部按类型再分一层 `textures/` 与 `sounds/`；模型与动画留在对象根（文件名自带类型后缀）。

**原版那四个「钉死」的入口怎么处理**：不是留在原版根目录，而是由本 MOD 的重定向层
（`core/asset/AssetRedirects` + `FileToIdConverterRedirectMixin`）在读取时把
`blockstates/`、`items/`、`models/`、`textures/` 改写到上面的布局，只对本 MOD 命名空间生效。
所以 `assets/minegenshin/` 下**不再有** `items/ models/ blockstates/ textures/` 四个目录，
只剩 `lang/` 这一个原版硬性入口。四条通道的一一对应与实测证据见 `docs/systems/render-asset.md`。

代码里只写**文件名**，目录由 `GenshinAssets` 拼：

```java
// 最省事：三个文件都叫 <角色id>
CharacterRenderData.character(ID, animMapping, bodyScale, mounts...)

// 要分别指定文件名
new CharacterRenderData(ID, "body.geo.json", "body.png", "body.animation.json", mapping, scale, mounts...)
```

**一句话总结**：一个角色 = 一个文件夹。默认角色只放动画（模型贴图共用），
但文件夹本身支持放全套 —— 要不要共用是**你的选择**，不是框架的限制。

## 9.2 为什么需要自己扫资源

GeckoLib 5.5.6 的扫描根是**硬编码常量**，没有公开接口能加根：

```java
public static final Identifier ANIMATIONS_PATH = GeckoLibConstants.id("geckolib/animations");
public static final Identifier MODELS_PATH     = GeckoLibConstants.id("geckolib/models");
// → bakeAllResources → resourceManager.listResources(rootPath, ...)
```

所以 `character/vesna/vesna.geo.json` 它**扫不到**。本 MOD 的解法不是改 GeckoLib
（mixin 太脆），而是**自己扫、自己烘培、自己缓存**：

`GenshinGeoCache`（一个 `PreparableReloadListener`）
→ 扫 `character/` `item/` `entity/` 三个根，只认 `.geo.json` / `.animation.json` 后缀
→ 用 GeckoLib 的**公开** API 烘培（`GeckoLibGsonLoader` + `MathParser`，结果与它自己烘的一模一样）

`GenshinGeoModel` 覆盖两个 public 非 final 的方法：

```java
@Override public BakedGeoModel getBakedModel(Identifier location) {
    BakedGeoModel own = GenshinGeoCache.model(location);
    return own != null ? own : super.getBakedModel(location);   // 先问自家，再回落 GeckoLib
}

@Override public Animation getBakedAnimation(T animatable, String name) { ... }
```

**为什么覆盖这两个就够**：全工程的模型/动画查找只有两个入口 ——
`RenderPassInfo.create`（模型）和 `AnimationProcessor.getOrCreateAnimation`（动画），
两者都只走 `GeoModel` 的这两个方法，没有旁路。

**为什么这样最安全**：不碰 GeckoLib 任何内部状态、不加 mixin，
整合包里其它 MOD 的解析完全不受影响 —— 这正是「只对本 MOD 生效」的要求。
副作用是个好事：我们自己的物品/实体资源**也不再受「必须放 geckolib/ 下」的限制**了。

## 9.3 路径规则工具

需要临时改目录（或者按条件改）时，**不要满工程改字符串**，注册一条规则：

```java
GeoPathOverrides.forKind(GeoAssetKind.MODEL, (kind, owner, original) -> ...);         // 只改模型
GeoPathOverrides.forOwner(myModel, (kind, owner, original) -> ...);                   // 只改这一个实例
GeoPathOverrides.forOwnerClass(TeyvatEntityModel.class, (kind, owner, original) -> ...);// 只改提瓦特实体
GeoPathOverrides.forNamespace("minegenshin", (kind, owner, original) -> ...);         // 只改本 MOD
```

规则链**后注册的先问**，返回 `null` 表示「这条不管」，全部不管就用默认约定。

`GenshinAssets.installDefaults()`（客户端启动时调用）装的是默认规则：
**只接管本 MOD 命名空间的 character 资源**，其它资源族一律不碰。

## 9.4 图标解析

**为什么不能直接画物品贴图**：原版平面物品的 `textures/item/<名字>.png` 本身就是图标，
直接画没问题；但 GeckoLib 的 **geo 物品**那张 png 是 **3D 模型的 UV 展开图集**
（`test_sword` 是 32×32），直接当 2D 精灵画出来是一坨错位色块。

`ItemIcons.pathOf(stack)` 的解析顺序：

1. `minegenshin:item/<物品名>/textures/icon.png` ← **物品自己的图标**（对象目录内）
2. `minegenshin:icon/item/<物品名>.png`（本 MOD 的通用图标目录，兼容旧资源）
3. `<物品自己命名空间>:icon/item/<物品名>.png`（让别的 MOD 也能自带图标）
4. `minegenshin:item/<物品名>/textures/texture.png`（平面物品贴图，贴图本身就是图标）
5. `<该物品自己的命名空间>:textures/item/<物品名>.png`（别的 MOD 的原版平面贴图）
6. `minegenshin:item/<物品名>/textures/<物品名>.png`（geo 物品贴图兜底）

结果带缓存，按资源是否存在判断。

**接入点只有两个函数**（5 个调用点全走它们）：

| 文件 | 函数 |
|---|---|
| `ScreenArtifactEquip.java` | `tex(ItemStack)`（装备槽 / 列表 / 详情共 4 处调用） |
| `BackpackMenu.java` | `getItemTexturePath(ItemStack)`（图标模式网格） |

> ⚠️ 想让 geo 物品在 GUI 里显示正确，**得补一张图标** `item/<名字>/textures/icon.png`；
> 没补就退回它的贴图（UV 图集）。

## 9.5 已完成迁移（当前布局的由来）

「按对象」这套布局已经全部落地，下面是这次搬家实际发生的对应关系，留作追溯用：

| 旧位置 | 现位置 |
|---|---|
| `textures/character_avatar/<id>.png` | `character/<id>/textures/avatar.png` |
| `textures/character_avatar/hud/<id>.png` | `character/<id>/textures/avatar_hud.png` |
| `textures/character_party_pose/<id>_prepare.png` | `character/<id>/textures/pose_prepare.png` |
| `textures/character_party_pose/<id>_already.png` | `character/<id>/textures/pose_already.png` |
| `textures/skill/<id>_skill.png` | `character/<id>/textures/skill.png` |
| `textures/character_avatar/selected_border.png` | `gui/selected_border.png`（六个角色共用，不是角色资产） |
| `textures/skill/cd.png` | `gui/skill_cd.png`（同理） |
| `textures/gui/**`、`textures/empty.png` | `gui/**`、`gui/empty.png` |
| `textures/elemental/**` | `icon/elemental/**` |
| `geckolib/models/entity/large_cryo_slime.geo.json` | `entity/large_cryo_slime/large_cryo_slime.geo.json` |
| `geckolib/animations/entity/large_cryo_slime.animation.json` | `entity/large_cryo_slime/large_cryo_slime.animation.json` |
| `textures/entity/large_cryo_slime.png` | `entity/large_cryo_slime/textures/large_cryo_slime.png` |
| `icon/primogem_icon.png` | `item/primogem/textures/icon.png` |

第三轮又把**纹理**统一收进对象目录的 `textures/` 子目录（音频本来就在 `character/<id>/sounds/`）：

| 第二轮位置 | 当前位置 |
|---|---|
| `character/<id>/avatar.png` 等 5 张角色图 | `character/<id>/textures/*.png` |
| `character/linweiyun/textures/linweiyun.png` | `character/linweiyun/textures/linweiyun.png` |
| `item/<id>/texture.png`、`item/<id>/icon.png` | `item/<id>/textures/{texture,icon}.png` |
| `entity/<id>/<id>.png` | `entity/<id>/textures/<id>.png` |
| `items/<id>.json`、`models/item/<id>.json`、`textures/item/<id>.png` | `item/<id>/definition.json`、`item/<id>/model.json`、`item/<id>/textures/texture.png` |

> `textures/` 这一层是**布局**而不是资源身份：镜像给原版入口时会去掉它，
> 所以 `item/<id>/textures/texture.png` 的图集 sprite id 仍是 `minegenshin:item/<id>/texture`。

大型冰史莱姆原来走 GeckoLib 的**默认模型**（按实体 id 猜 `geckolib/` 与 `textures/entity/`），
现在改接 `CategoryGeoModel`（`MinegenshinClient` 里一行）：模型 / 动画 / 贴图全部出自
`entity/large_cryo_slime/` 一个目录，不再依赖 GeckoLib 的路径猜测。

## 9.6 一个角色要用自己的一整套资源

默认是「模型贴图共用、动画独立」，因为本 MOD 现在只有一个身体模型。
**但只要这个角色有自己的美术，就把三件套都放进它自己的文件夹** ——
路径不用改任何代码，`GenshinAssets` 已经按 `<角色id>` 拼好了。

### 四种写法，从省事到完全自定义

```java
// ① 默认：共用模型 + 共用贴图 + 自己的动画
//    character/linweiyun/linweiyun.geo.json
//    character/linweiyun/textures/linweiyun.png
//    character/vesna/vesna.animation.json
CharacterRenderData.character("vesna", mapping, 1.0f, mounts...);

// ② 全套自有：三个文件都在自己文件夹里，文件名 = 角色 id
//    character/vesna/vesna.geo.json / vesna.png / vesna.animation.json
CharacterRenderData.characterWithOwnModel("vesna", mapping, 1.0f, mounts...);

// ③ 文件名不想叫 <角色id>：显式给路径
new CharacterRenderData(
        "vesna",
        "character/vesna/body.geo.json",        // 模型：相对 assets/minegenshin/
        "character/vesna/textures/body.png",      // 贴图
        "character/vesna/body.animation.json",  // 主动画
        mapping, 1.0f, mounts...);

// ④ 连目录层级都要自定义：还是显式给路径，随便放
new CharacterRenderData(
        "vesna",
        "character/vesna/model/body_v2.geo.json",
        "character/vesna/tex/body_v2.png",
        "character/vesna/anim/main.animation.json",
        mapping, 1.0f, mounts...)
        .withAnimationFile("character/vesna/anim/fp.animation.json")     // 额外动画文件，想加几个加几个
        .withAnimationFile("character/vesna/anim/emote.animation.json");
```

> ⚠️ 路径是**相对 `assets/minegenshin/`**、**带扩展名**的。
> 模型写 `.geo.json`、动画写 `.animation.json`（`.json` 也行）、贴图写 `.png`。
> 内部会剥掉后缀变成 GeckoLib 的 id（`character/vesna/body.geo.json`
> → `minegenshin:character/vesna/body`）。

### 每类资源放哪、谁决定路径

| 资源 | 默认位置 | 谁决定 | 怎么改 |
|---|---|---|---|
| 模型 | `character/linweiyun/linweiyun.geo.json` | `CharacterRenderData.modelPath` | ①②③④ 任一 |
| 贴图 | `character/linweiyun/textures/linweiyun.png` | `CharacterRenderData.texturePath` | ①②③④ 任一 |
| 动画 | `character/<id>/<id>.animation.json` | `CharacterRenderData.animationPath` | ①②③④ 任一 |
| 额外动画 | 无 | `withAnimationFile(...)` | 链式追加 |
| 音效文件 | `character/<id>/sounds/*.ogg` | 约定（`GenshinAssets`） | 见 [10.5](#105-音效文件放哪怎么被加载) |
| 音效定义 | `character/<id>/sounds.json`（**可选**） | 约定（`GenshinAssets`） | 见 [10.5](#105-音效文件放哪怎么被加载) |
| 图标 | `item/<名字>/icon.png`（本 MOD 通用图标目录 `icon/item/<名字>.png` 仍兼容） | `ItemIcons` | 见 [9.4](#94-图标解析) |

### 整套资产都搬走（不想放在 `character/` 下）

如果连目录前缀都不要（比如所有角色都想塞进 `models/`），**不要改字符串**，
注册一条全局规则，一处生效：

```java
// 客户端启动时（和 GenshinAssets.installDefaults() 同一个位置）
GeoPathOverrides.register((kind, owner, original) -> {
    if (!"minegenshin".equals(original.getNamespace())) return null;   // 别碰别人的资源
    if (!(owner instanceof MyModel model)) return null;                // 只改我关心的模型
    String file = original.getPath().substring(original.getPath().lastIndexOf('/') + 1);
    return switch (kind) {
        case MODEL     -> GenshinAssets.id("models/" + model.assetCharacterId() + "/" + file);
        case ANIMATION -> GenshinAssets.id("anims/"  + model.assetCharacterId() + "/" + file);
        case TEXTURE   -> GenshinAssets.id("tex/"    + model.assetCharacterId() + "/" + file + ".png");
    };
});
```

规则链**后注册的先问**，返回 `null` 就交给下一条，全不管才用默认约定。
按模型类过滤（`forOwnerClass`）、按命名空间过滤（`forNamespace`）、只改某一类
（`forKind`）都有现成的入口，见 [9.3](#93-路径规则工具)。

> 想让角色模型自己回答「我是谁」，实现 `GenshinAssets.CharacterAssetOwner` 即可
> （`GenshinGeoModel` 已经实现了，所以规则里能直接读 `assetCharacterId()`）。

### 加一个自带全套资源的新角色，需要的只有这些

```
src/main/resources/assets/minegenshin/character/lumine/
      lumine.png
      local/lumine.geo.json          ← 模型 / 动画明文放这里（自带资源走资源包，见 9.1）
      local/lumine.animation.json
      sounds.json                 （可选）
      sounds/*.ogg                （可选）
```

```java
// 1. 资源和数值
public final class LumineResources {
    public static final CharacterRenderData RENDER_DATA =
            CharacterRenderData.characterWithOwnModel("lumine", mapping, 1.0f, mounts...);
    public static final CharacterActionData ACTION_DATA = ...;
}
// 2. 动画名单（哪些名字会被当成「特殊动作」）
// 3. 天赋（服务端数值）
// 4. 注册一行：CharacterAnimationRegistry.registerAll()
```

除了第 4 步那一行，**没有任何地方需要写 `character/lumine/` 这个路径**。

## 9.7 数据生成：物品模型与物品定义

**文件**：`data/generators/ModModeProvider.java` + `data/generators/DataGenerators.java`

### 为什么「每个物品都必须有一条定义」

原版 `ModelProvider` 写完文件后会**校验**：本命名空间里注册过的每个物品都必须有一条
「物品定义」（`assets/<ns>/items/<物品名>.json`），缺一个就直接抛：

```
IllegalStateException: Missing item model definitions for: [minegenshin:xxx]
```

所以**不能有物品被漏掉** —— 哪怕它根本不打算用自己的贴图。
这就是「漆黑碎片没有自己的模型，也得在数据生成里登记一条」的原因。

### 三种物品，三种写法

| 情况 | 怎么写 | 产出 |
|---|---|---|
| 有自己的贴图（普通平面物品） | 生成器按注册表遍历，自动写出定义 + 模型 | `item/<物品id>/model.json`（parent `item/generated`，layer0 = `minegenshin:item/<id>/texture`）+ `item/<物品id>/definition.json` |
| **借原版模型**（漆黑碎片 = 下界之星） | 在 `ModModeProvider.BORROWED_MODELS` 里登记 | **只有** `item/<物品id>/definition.json`，里面 `"model": "minecraft:item/nether_star"` |
| 定义 / 模型是手写的（select 形状、geo 物品、特殊渲染器） | 加进 `ModModeProvider.HAND_WRITTEN_MODELS` | 数据生成不生成、也不校验它；手写的 `item/<物品id>/{definition,model}.json` 原样生效 |

**产出路径已经在本项目自己的布局里**：`ModModeProvider` 不再继承原版 `ModelProvider`
（后者的产出目录写死在 `items/`、`models/`），而是直接用
`output.createPathProvider(RESOURCE_PACK, "item")` + `file(id.withSuffix("/definition"), "json")`
配出 `item/<物品id>/definition.json` 与 `item/<物品id>/model.json`，
并自带「本命名空间每个物品都要有定义，漏了就抛」的等价校验。
贴图是资源不是生成物，放 `item/<物品id>/textures/texture.png`。

**漆黑碎片（`dark_fragment`）现在走的是第二种**：生成出来的就是
```json
{ "hand_animation_on_swap": false,
  "model": { "type": "minecraft:model", "model": "minecraft:item/nether_star" } }
```
手写的那份 `src/main/resources/assets/minegenshin/items/dark_fragment.json` 已经删掉
（留着会和新生成的撞路径 —— 同一个 `<ns>:items/<名字>` 有两份资源）。

**为什么手写的要「跳过」而不是「生成一份」**：geo 物品的定义长这样
（`test_sword`，`type: minecraft:special` + `geckolib:geckolib`），
数据生成只会写平面模型的定义，生成出来就是把这份特殊定义**盖掉**，物品立刻变成透明方块。
所以 `test_sword` 在 `HAND_WRITTEN_MODELS` 里 —— **以后每加一个 geo 物品都要加一行**。

> ⚠️ **`ClientItem.Properties` 的字段是平铺在顶层的**，不是放在 `properties` 对象里：
> ```json
> { "model": {...}, "oversized_in_gui": true, "hand_animation_on_swap": false,
>   "swap_animation_scale": 1.95 }
> ```
> 写成 `"properties": { ... }` 会被 codec **静默忽略**（DFU 不报未知字段），
> 表现就是「明明写了 `oversized_in_gui` 却一点变化都没有」。
> `test_sword.json` 之前就是这么写的，已经修掉。

### 怎么跑

```bash
gradlew runData            # 输出到 src/generated/resources/（已在 build.gradle 里挂进 sourceSet）
```

| 记住 | 说明 |
|---|---|
| 加了新物品 | 必须跑一次 `runData`，否则打包出来的 jar 里没有它的物品定义 → 物品显示成紫黑方块 |
| 输出目录 | `src/generated/resources`，**要提交**（它不是临时产物，是最终资源的一部分） |
| `--existing src/main/resources` | 数据生成会以手写资源为准来判断，别让两边出现同一个文件 |

---

# 第十部分 · 多文件动画 / 第一人称 / 音效编排

## 10.1 多文件动画

### 用法

动画不再要求全塞一个文件里。`CharacterRenderData` 加了一个「额外动画文件」列表：

```java
public static final CharacterRenderData RENDER_DATA = CharacterRenderData.character(
        ID, mapping, 1.0f, CharacterBoneMount.of("blade_right"))
        .withAnimationFile("character/vesna/vesna_fp.animation.json")   // 第一人称
        .withAnimationFile("character/vesna/vesna_extra.animation.json"); // 动作包
```

磁盘上就是普通文件，**放哪都行**（路径走 `GenshinAssets` 的解析，不再固定目录）：

```
character/vesna/vesna.animation.json        ← 主文件（三件套）
character/vesna/vesna_fp.animation.json     ← 第一人称
character/vesna/vesna_extra.animation.json  ← 想加多少加多少
```

### 查找顺序

```
PlayerAnimationController 要播 "fp_attack_1"
  └─ GenshinGeoModel.getBakedAnimation
       └─ GenshinGeoCache.animation(主文件, [回退文件...], 名字)
            ① 主文件里有 → 用
            ② 没有 → 按 withAnimationFile 的顺序一个个问
            ③ 都没有 → 回落 GeckoLib 自己的缓存 → 还没有就是「动画不存在」
```

### 兼容性

这条路用的是 **GeckoLib 原生的回退机制**（`GeoModel.getAnimationResourceFallbacks`），
不是我自己发明的：

```java
@Override public Identifier[] getAnimationResourceFallbacks(T animatable) { return animationFallbacks; }
```

GeckoLib 的 `BakedAnimationCache.getAnimation(file, fallbackFiles, name)` 本来就是这么设计的。
我只是在自己的缓存里也实现了一遍同样的语义。

**连带改的地方**：`AnimationAvailability`（动画存在性校验）现在会把**所有动画文件**的名字收集起来，
所以放在第一人称文件里的 `fp_attack_1` 也能通过校验 —— 不然它会被自己的「动画保护」拦下来。

## 10.2 第一人称：一个开关，两套做法

### 开关

在角色的 `XxxAnimations` 里覆盖一个方法：

```java
@Override
public FirstPersonAnims firstPerson() {
    return FirstPersonAnims.on();          // 开
    // return FirstPersonAnims.DISABLED;   // 关（默认）
    // return FirstPersonAnims.on(FirstPersonAnims.FirstPersonCamera.DEFAULT.withOffset(0, 1.7f, 0.2f));
}
```

### 两套做法**不需要两套代码**

| 模式 | 怎么配 | 运行时行为 |
|---|---|---|
| **A · 写一套第一人称动画** | `firstPerson()` 开，且动画文件里**真的有** `fp_attack_1` | 播 `fp_<状态名>` |
| **B · 复用现有动画 + 转角度** | `firstPerson()` 开，但**没有** `fp_*` 动画 | 照播普通动画，靠 `camera()` 的机位把角度摆正 |
| **关** | `DISABLED`（默认） | 第一人称不做特殊处理 |

关键在于**运行时按「动画名存不存在」自动选**：

```java
// PlayerAnimationController.pickAction
if (firstPerson.enabled() && isFirstPerson(player)) {
    String fpName = firstPerson.resolve(animationName, true);   // "attack_1" → "fp_attack_1"
    if (fpName != null && AnimationAvailability.existsFor(player, fpName)) {
        return RawAnimation.begin().thenPlay(fpName);            // 有 fp_ 就用 → 模式 A
    }
}
// 没有 fp_ → 照播普通动画（模式 B）
return RawAnimation.begin().thenPlay(animationName);
```

所以你可以**先上 B（零美术成本）再逐步补 A（哪一段不满意就补哪一段）** ——
写一个 `fp_attack_2` 就只有第二段走 A，其余还在 B。这个渐进路径是刻意留的。

### `fp_` 只对动作动画生效

```java
public String resolve(String stateName, boolean isActionState) {
    if (!enabled) return null;
    if (!isActionState) return null;      // ← 常态动画不查 fp_
    return prefix + stateName;
}
```

因为**第一人称真正要看的只有武器挥动**。第三人称动画要照顾全身（腿、披风、站姿），
第一人称镜头里根本看不到；反过来第一人称也不需要单独的走跑跳 —— 复用就够了。
所以默认只给「普攻 / 战技 / 大招 / 闪避」这些动作留 `fp_` 版本。

前缀可改：`FirstPersonAnims.on("fp1_")`。

### ⚠️ fp 动画必须带上特效骨骼的轨道

**这是踩过的坑，写 fp 动画前必读。**

这套模型的特效骨骼（`slash_a..g` / `sfa..sfh` / `out_size` / `obs2` / `bone9` / `bone12` …）
在 **rest pose 下是可见的**。第三人称动画靠「t = 0 把它们的 scale 写成 0、
到挥砍那一瞬再放大」来控制特效出现的时机（`attack_1` 一共写了 **49 根骨骼**）。

所以如果 fp 动画**只写手臂**（我最初那版只写了 5 根），这些特效骨骼就没有任何轨道
→ 保持 rest pose → **一播动画满屏特效**，而不是跟着剑走。

**规则**：**以第三人称动画为底，只替换手臂 / 躯干 / 头那几根**；长度和特效轨道原样保留。

现成的工具（本机 `像素原神\tools\` 下的 `merge-fp-anims.ps1`，已移出仓库）就是干这个的 —— 它把第三人称动画整份拷过来，
再用 fp 文件里写的那几根覆盖掉，长度取两者较大值：

```powershell
# 本机执行策略禁止直接跑 .ps1，用 Invoke-Expression 绕过
# 工具已移出仓库，放在本机 像素原神\tools\minegenshin-repo-20261006\tools\ 下
$code = Get-Content -Raw "G:\AI\Codex\Skill\像素原神\tools\minegenshin-repo-20261006\tools\merge-fp-anims.ps1"
Invoke-Expression $code
```

> 注意它会**直接覆盖** `<角色>_fp.animation.json`：想改 fp 姿势就改它覆盖的那几根，
> 特效轨道永远来自第三人称文件，不用手动同步。
> 换角色时传 `-CharacterDir`（脚本按 `fp_<名字>` → `<名字>` 自动配对）。

Vesna 的四个 fp 动画（`fp_attack_1/2/3`、`fp_skill_no_energy`）已经按这个规则合并过了。

### 相机空间渲染器：`FirstPersonCharacterRenderer`

**第一人称下原版根本不渲染玩家实体本体**（只渲染 `ItemInHandRenderer` 里那两只通用手臂），
所以第三人称那条链路（`AvatarRendererMixin` → `CharacterRenderDispatcher`）
第一人称**一次都不会被调用** —— 这就是「已经开了第一人称，看到的还是原版那只手」的原因。

补上的东西是 `client/render/character/FirstPersonCharacterRenderer.java`：

```
RenderHandEvent（NeoForge 游戏总线，每只手各发一次）
  ├─ 该不该接管？
  │    Genshin 模式 + 有角色 + customModel 开着 + 这个角色的 FirstPersonAnims 开着
  │    + 手里不是弓/弩/地图/望远镜/三叉戟/盾/正在使用中
  ├─ 接管 → event.setCanceled(true)（原版手臂不画），只在主手那一趟画一次模型
  └─ 在同一张 pose 上摆好模型 → performRenderPass(...)
```

**为什么可以直接用事件给的那张 `PoseStack`**：它已经是**相机空间**的
（原点在相机、`-Z` 是视线方向，上下摆动也算进去了），和原版画手臂用的是同一张。
所以只要把模型摆到「相机脚下的那个位置」就行：

```
模型脚底（相机空间）= ( -offsetX, -(眼高 + offsetY), +offsetZ )
```

相机往**前**移 0.15 格，模型就相对落在相机**后面** 0.15 格 —— 手臂和武器正好进画面，
这就是 `FirstPersonCamera.offsetZ` 默认值的来历。

三个实现细节：

| 细节 | 处理 |
|---|---|
| 模型朝向 | MC 实体模型的局部前方是 `-Z`，相机前方也是 `-Z` —— **不用转**（第三人称那句 `YP(180 - yaw)` 是给世界坐标系的） |
| 模型原点 | 模型以原点为中心导出，`CharacterRenderer.adjustRenderPose` 已经把基类（摆件渲染器）那半格补偿覆盖成空 —— 所以这里**不需要**任何原点补偿 |
| 藏头 | 相机在头壳内部，而模型不剔背面 → 不藏头就会「从里面看内壁」。用 GeckoLib 的逐趟骨骼覆盖藏掉 `head` 及其子树（`hair`/`ear_*`/`eyes`…） |
| 动画实例共用 | 模型/渲染器/`animatable` 都从 `CharacterRenderDispatcher.targetFor(...)` 拿 —— 动画播到哪存在 animatable 里，各拿一份切视角时动画会跳 |

**哪些情况不接管**：原版对「弓 / 弩 / 地图 / 望远镜 / 三叉戟 / 盾 / 正在吃东西喝药」
都有一套专门表现（拉弓、摊地图、瞄准镜…），我们的模型画不了那些，
这些情况直接让原版上 —— 拉弓拉了个空是最难受的。

**注册方式**：`RenderHandEvent` 是**游戏总线**事件，所以由 `MinegenshinClient.onClientSetup`
显式 `NeoForge.EVENT_BUS.addListener(...)`，不靠 `@EventBusSubscriber` 猜总线。

> ⚠️ 机位数值（`FirstPersonCamera`）大概率还要进游戏微调一次 —— 看不到武器就
> `offsetZ` 调大、手臂挡视线就 `pitch` 往下压，见下一节。

## 10.3 第一人称动画怎么写（Blockbench + GeckoLib）

我按这个模型的骨骼写了一份可用的起点：`assets/minegenshin/character/vesna/vesna_fp.animation.json`，
里面是 `fp_attack_1`（斜上撩）、`fp_attack_2`（横扫）、`fp_attack_3`（突刺）、`fp_skill_no_energy`。

### 核心思路：第一人称 = 「以相机为基准的武器挥动」

第三人称动画的设计基准是**别人看你**；第一人称的基准是**你自己看**。差别就一句话：

> **第三人称里「手臂抬到肩膀高度」是第一人称里「手臂抬到糊住整个屏幕」。**

所以第一人称动画不是把第三人称动画等比缩小，而是**重心完全不同**：
动作幅度小、武器始终在画面右下/中下方、挥动轨迹要「扫过」屏幕中心。

### Blockbench 里的具体做法

1. **不用建新模型，用同一个 `.geo.json`**。第一人称动画和第三人称动画共享骨骼，
   只是另一个 `.animation.json` 文件。我把它们分成两个文件就是为了互不干扰。
2. **打开 Blockbench → 加载角色模型的 `.geo.json`**（Geo 模型格式）。
   仓库里自带的模型收在资源包中（见 9.1），本机改模型用的是自己留档的那份明文；
   只想覆盖某一条时把它放进 `character/<角色id>/local/`，明文直读、同名优先。
3. **建动画时把 `Loop` 设成 `Once`**（攻击是一次性的），`Animation Length` 按刻数 / 20 设
   （普攻 1 段是 40 刻 → 2.0 秒；我文件里写的 0.75 是「挥动本身」的长度，比动作总时长短，
   剩下的时间是收招，会由状态机自动回常态）。
4. **命名一定要带前缀**：`fp_attack_1`、`fp_attack_2`…… 名字是唯一的对接点。
5. **只动这几根骨骼**（其他别碰，省事也不容易出错）：
   - `arm_right` / `arm_bot_right` —— 主挥动
   - `blade_right` —— 手腕微调（武器的朝向）
   - `torso` —— 反向小幅旋转，让身体有「使劲」的感觉
   - `head` —— 跟随目标轻微转动
6. **在 Blockbench 里直接开相机预览**：视图 → 相机 → 第一人称预设。没有预设就手动把相机
   摆到头部骨骼的位置朝前看。**这一步别省** —— 你就是靠它判断「武器有没有出画面」。
7. **导出**：`File → Export → Animations`，选 `GeckoLib Animation` 格式，
   导出覆盖到 `vesna_fp.animation.json`。

### 数值参考（我文件里用的结构）

```json
"fp_attack_1": {
  "loop": false,
  "animation_length": 0.75,
  "bones": {
    "arm_right": {
      "rotation": {
        "0.0":  [0, 0, 0],        // 起始：正持
        "0.15": [-95, 0, 30],     // 抬手蓄力
        "0.35": [-15, 0, -25],    // 挥下去
        "0.75": [-5, 0, -5]       // 收招
      }
    }
  }
}
```

三个时间点就够表达一次挥砍：**0 = 起手，~0.2L = 抬到最高，~0.45L = 挥到位，L = 收回**。

> ⚠️ **轴向可能要翻符号**。我这套数字是按「手臂默认垂下、X 负值向前抬」这个常见约定写的，
> 你这个模型的 rest pose 我没法看。**进 Blockbench 一拖就知道**：
> 如果手臂往反方向转，把该轴的符号反过来即可。这也是为什么我把它做成独立文件 ——
> 调坏了不影响第三人称。

### 用「复用模式」时的机位调法

不想做美术时走模式 B，调 `FirstPersonCamera`（**相机相对眼睛的偏移**，单位格/度）：

```java
FirstPersonAnims.on(FirstPersonAnims.FirstPersonCamera.DEFAULT
        .withOffset(0f, 0f, 0.25f)         // 相机再往前 0.25，让武器更进画面
        .withRotation(6f, 0f, 0f)          // 模型略微低头
        .withScale(1.0f))
```

| 现象 | 调哪个 |
|---|---|
| 看不到武器 / 只看到一点点 | `offsetZ` 调大（相机前移 = 模型后退） |
| 整个模型离脸太近、糊住屏幕 | `offsetZ` 调**小**（甚至给负数，相机往后） |
| 画面偏上/偏下 | `offsetY`（正 = 相机抬高 = 模型看起来往下） |
| 手臂挡住视线 | `pitch` 往下压一点、`offsetZ` 前移 |
| 武器太大/太小 | `scale` |
| 左右不对称 | `offsetX`（正 = 相机右移 = 模型看起来偏左） |
| 模型被近裁剪面切掉 | 目前没有现成开关（`fovPadding` 是预留字段没接）——先把 `offsetZ` 调大把模型推远一点 |

> 「相机前移」和「模型后退」是一回事，只是说法不同：这里所有数值描述的都是**相机**，
> 因为第一人称的机位直觉就是「相机在哪、朝哪」。换算公式见上一节。

## 10.4 音效：序列与随机的组合

### 用法

一个「时间点」上的音效编排是一个 `SoundCue`，两种模式：

```java
step.withSoundCue(SoundCue.play(0, "vesna_attack_1_a"))                    // 这一格固定播
    .withSoundCue(SoundCue.play(6, "vesna_attack_1_b"))
    .withSoundCue(SoundCue.pickOne(12,                                      // 这一格从候选里抽
            SoundRef.silent(),                                              //   25% 不出声
            new SoundRef(0, "vesna_ha",   1f, 1f, 3),                       //   其余按权重
            new SoundRef(0, "vesna_eat",  1f, 1f, 2)))
    .withSoundCue(SoundCue.play(18, "vesna_swing_end"));
```

**你要的那个例子**（序列四段、第三段随机抽四个）就是这样：
四个 `withSoundCue`，第三个用 `pickOne`。

### 三个设计点

1. **权重用整数，不用百分比**。想要「七成 A、三成 B」就写 `7` 和 `3` ——
   作者不必让数字加起来等于 100，加一条候选也不用重算其他条目。
2. **「无音效」是一个普通候选** `SoundRef.silent()`，不是特例。
   这样「30% 没喊」和「30% 喊 A」在数据里长得一样，抽取逻辑只有一份。
3. **`PLAY_ALL` 也保留**：同一格可以叠播多条（比如「挥剑声 + 人声」同时）。

### 随机是确定性的（可复现，不是「跨客户端一致」）

**先说清楚一件事**：这些编排音效走的是 `playLocalSound`（本地播放、不发包），
所以**只有出手的本人听得到**；别人那边只有 `AnimationStateSync` 按状态名补的
那一条 `soundForState`。要「所有人都听到整套编排」，得把解析结果发服务端再广播 ——
目前没做（记在附录 A.5）。

在这个前提下，随机种子取「玩家 UUID + 动作序号」：

```java
long seed = player.getUUID().getLeastSignificantBits()
        ^ (long) ActionStateMachine.actionSequence() * 0x9E3779B97F4A7C15L;
```

`actionSequence` 在每次 `changeState` 时 +1。于是：

- **同一段连招的每一段各不相同** —— 序号在变，不会连着四次都是「哈！」
- **可复现**：同一个人的同一刀，抽取结果固定，排查问题时能精确重放

这是「确定性随机」而不是「同步随机」：省一个网络字段，也不怕丢包。

### 和旧写法的关系

`ActionStep.sounds`（`List<SoundRef>`）**原样保留**，每条当成「这一格固定播」。
新旧叠加，所以 Vesna 现有的 `SoundRef` 一条都不用改。

`SoundRef` 加了一个 `weight` 字段，但**保留了 4 参构造器**，旧代码零改动。

### 顺带一提

排期用的是 `queueClientWork`，而那个队列在 `resetToDefault()` 时会被清空 ——
所以**动作被打断时后面的语音不播**是自动成立的，不用额外写判断。

## 10.5 音效文件放哪、怎么被加载

### 布局：音效也在角色文件夹里

```
assets/minegenshin/character/vesna/
      sounds/                   ← 音效文件夹：ogg 丢进来就生效
            attack_1.ogg
            attack_2.ogg
            skill.ogg
      sounds.json               ← 可选（进阶：字幕 / 多音色 / 音量音高 / 权重）
```

一个角色一个目录，整包拷走就能用。

### 用法：**只有文件夹是必须的**

把 `attack_1.ogg` 放进 `character/vesna/sounds/`，事件名就自动是
`minegenshin:vesna_attack_1`（= `<角色id>_<文件名>`），动作数据里直接写这个名字即可：

```java
// VesnaResources.ACTION_DATA
new SoundRef(3, "vesna_attack_1", 1.0F, 1.0F)
```

```
character/vesna/sounds/attack_1.ogg   →   minegenshin:vesna_attack_1   ✔ 就这么简单
```

- **不需要**写任何定义文件、不需要注册、不需要改代码；
- 文件名已经带了角色前缀（`vesna_attack_1.ogg`）也不会变成 `vesna_vesna_attack_1`；
- 整个 `character/vesna/` 复制成 `character/lumine/` 之后，文件名不用改也不会和 vesna 撞名。

### `sounds.json`（可选）：自动发现表达不了的东西

只有需要**字幕 / 一个事件多套音色 / 每条音色不同的音量音高 / 抽取权重**时才写它。
格式和原版 `assets/<命名空间>/sounds.json` **完全一致**，只有一处不同：
`name` 相对**本角色的 sounds 目录**。

```json
{
  "attack_heavy": {
    "subtitle": "subtitles.minegenshin.vesna.attack_heavy",
    "sounds": [
      { "name": "heavy_1", "pitch": 0.95, "weight": 3 },
      { "name": "heavy_2", "weight": 1 }
    ]
  }
}
```

| 字段 | 说明 |
|---|---|
| key（`attack_heavy`） | 事件名短名 → 自动带角色前缀：`minegenshin:vesna_attack_heavy` |
| key 已经带前缀 | 原样使用，不会变成 `vesna_vesna_...` |
| key 写了命名空间（`other_mod:foo`） | 照用 |
| `name`（`heavy_1`） | = `character/vesna/sounds/heavy_1.ogg` |
| `name` 带命名空间 | 按「命名空间根目录下的路径」理解，例如 `minegenshin:character/common/sounds/whoosh` 就是共用音效 |
| `volume` / `pitch` / `weight` / `stream` | 和原版同义 |
| `preload` | ⚠️ **本 MOD 不生效**：原版在音频引擎启动那一次才会消费预加载队列，而我们的表是在之后补进去的。写了不会报错，只是没预加载（首次播放现场读文件） |
| `subtitle` | 和原版同义（翻译键）；记得自己在 `lang/*.json` 里补上这个键，不然开字幕会显示原始键名 |

**同名事件以 `sounds.json` 为准**（写了定义就听定义的，自动发现那条被覆盖）。

> **原版声音事件名不能这样引用**（`"minecraft:entity.player.attack.weak"`）：
> 那是「事件」，不是文件。要用共用音效就把 ogg 放进一个共用角色目录里按上面的写法引。

### 怎么和 `SoundRef` 对上

动作数据里写的是**事件名**，所以两边要一致：

```java
// VesnaResources.ACTION_DATA
new SoundRef(3, "vesna_attack_1", 1.0F, 1.0F)
//             └─ 事件 id：minegenshin:vesna_attack_1
```

```
character/vesna/sounds/attack_1.ogg     ← 文件按这个名字放就通了
```

音效只有定义没有 ogg 时不会报错：日志里会说哪个文件不存在，其余动作照常。

### 加载链路（为什么需要一层自定义加载）

原版有两个写死的约定，缺一不可：

| 约定 | 后果 |
|---|---|
| 事件定义只读 `assets/<命名空间>/sounds.json`（一个命名空间**只有一份**） | 没法一个角色一个文件 |
| `Sound#getPath()` 固定拼 `sounds/<名字>.ogg` | 声音文件只能在 `assets/<命名空间>/sounds/` 下 |

所以本 MOD 绕开这两条：

```
声音引擎（重）加载：SoundManager#apply 的最后一行 soundEngine.reload()
  └─ SoundEngineLoadEvent（NeoForge 的 mod 总线事件）
       └─ CharacterSounds.onSoundEngineLoad
            ① 扫 character/*/sounds/**/*.ogg   → 每个 ogg 直接登记成事件（自动发现）
            ② 再扫 character/*/sounds.json     → 可选的定义，同名覆盖 ①
            ③ 造 CharacterSound（继承原版 Sound，只覆写 getPath()）
            ④ 结果补进 registry（事件 → 声音列表）与 soundCache（文件 → 资源）
```

四个设计点：

1. **`CharacterSound` 覆写 `getPath()`** 是让文件离开 `sounds/` 的唯一必要改动 ——
   校验（文件在不在）和播放（`SoundBufferLibrary` 取流）都走这个方法，改一处两边都通。
   因为它是**子类**，所以不需要 mixin、也不会影响原版和其它 MOD 的音效。
2. **触发时机用 `SoundEngineLoadEvent`**，而不是往 `SoundManager#apply` 里注入：
   那个方法的第一个参数 `SoundManager.Preparations` 是 protected 嵌套类，
   而 Mixin 要求 `@Inject` 的 handler 签名**逐参数完全一致**（用 `Object` 顶替会在启动阶段直接崩：
   `InvalidInjectionException: Invalid descriptor`）。改用事件后，mixin 只剩
   「把两张表暴露出来」这一件事（`CharacterSoundSink` 鸭子接口），**一句 `@Inject` 都没有**。
3. **自动发现优先于定义文件**：只丢 ogg 就能用；要字幕/多音色再写 `sounds.json`。
4. **扫不到 / 文件缺失就跳过**，不抛异常：音效是可选内容，不该让整个重载失败。

### 未注册的音效也能响

角色音效是数据文件定义的，没走过 `DeferredRegister`，注册表里查不到。
所以 `ActionStateMachine.playLocalSound` 查不到时会现造一个：

```java
SoundEvent soundEvent = BuiltInRegistries.SOUND_EVENT.getValue(id);
if (soundEvent == null) {
    soundEvent = SoundEvent.createVariableRangeEvent(id);   // ← 数据音效走这条
}
```

客户端按 location 去声音注册表找文件，找得到就响。这一条同时修掉了
「`SoundRef` 里配的 `volume` / `pitch` 从来没生效」的问题（以前写死 1.0/1.0）。

## 10.6 设计思路：这几个功能为什么这么做

### 多文件动画：接 GeckoLib 的原生机制，不自己发明

GeckoLib 的 `BakedAnimationCache.getAnimation(file, fallbackFiles, name)` **本来就支持回退文件**，
`GeoModel.getAnimationResourceFallbacks` 就是给它用的钩子。所以：

- 不新增「动画注册表」概念，只加一个 `List<String>` 路径
- 查找语义完全对齐 GeckoLib（主 → 回退 → 放弃）
- 代价是 `AnimationAvailability` 要跟着改（它得知道全部文件的名字，否则会把 `fp_*` 当成不存在的动画拦下来）

**教训**：加功能前先看依赖库有没有现成机制。这里如果自己写一套「动画名 → 文件」的映射，
就会和 GeckoLib 的查找逻辑出现两套语义，早晚对不上。

### 第一人称：把「两套做法」做成一个开关的两种结果

用户要「写一套 fp 动画」和「复用现有动画转角度」两套。直觉是写两个模式枚举 + 两套分支。
实际做法是**只在动画名上分叉**：

```
有 fp_attack_1 → 播它        （模式 A）
没有           → 播 attack_1 （模式 B，机位补正）
```

好处有三：

1. **没有模式枚举**，也没有「模式 A 但动画没写全」的半吊子状态
2. **可以逐段迁移**：补一个 `fp_attack_2` 就只有第二段换新，其余照旧
3. **降级是自动的**：动画写错名字/被资源重载弄丢，会自动回到模式 B，而不是变成原始姿态

代价是「模式」不可显式指定 —— 想强制走 B 就得删掉 fp 动画。这个取舍我认为值得。

### 音效：把「序列」和「随机」拆成正交的两个维度

一开始想做成 `SoundPlan` 带一堆 flag。想清楚后发现是**两个正交维度**：

- **时间轴**：哪几刻播 → 由多个 `SoundCue` 按 delay 排列表达
- **每一格怎么播**：全播 or 抽一个 → 由 `PickMode` 表达

拆开之后，「序列四段、第三段四选一」就自然落在「四个 cue + 第三个是 PICK_ONE」上，
不需要为组合场景设计专门字段。**组合爆炸来自把两个维度揉进一个结构**。

「无音效」放进候选列表而不是加 flag，也是同一个思路 —— 它只是权重为 1 的一个普通选项。

### 远程/近战：判定单位选「招式」，不选「角色」

最直觉的做法是给 `PGCharacter` 加一个 `isRanged()`。但同一个角色既有近战普攻
又有远程战技是常态（弓手砍人、法师抡杖），按角色分只能二选一，
最后一定会变成「角色级默认 + 招式级例外」的两层结构 —— 那不如一开始就只有招式级。

所以 `Engagement` 挂在 `ActionStep` 上，角色那一层不存在这个字段。
「远程」也不是一个新分支，而是**一套参数的预设**：不突进 + 索敌 = 攻击距离。
`engageAndPlay()` 里没有任何 `if (ranged)`，只是参数不同、走同一条流程。

### 转向：用「衰减追角」而不是固定角速度

固定角速度（每刻转 X 度）有两个毛病：小角度会显得一顿一顿（最后 3 度也按全速转），
大角度又容易在到位瞬间「啪」地停住。衰减式（每刻转掉剩余夹角的一部分）
天然是「先快后慢」，起手有劲、收尾柔和，而且**永远转不满一帧**。

上下限不是凑数：`TURN_MAX_SPEED` 保证「不瞬移」，`TURN_MIN_SPEED` 保证
「最后几度不磨蹭」。两个都调过才敢说是「快，但看得出转身」。

转向的**预算制**（`TURN_BUDGET_TICKS`）也是必要的：没有预算，目标一直绕圈时
玩家会一直被自动转向拽着走；有预算，最多半秒多就还给你。

### 角色音效：子类覆写，不 mixin 原版方法

要让文件离开 `assets/<ns>/sounds/`，直觉是 mixin `Sound#getPath()`。
但 `Sound` 不是 final、`getPath()` 也不是 final —— **那就继承**：
只对我们自己造的那几个 `Sound` 生效，原版和其它 MOD 一个字节都不受影响。

往 `SoundManager#apply` 注入的第一次尝试也失败得很值得记：那个方法收一个 protected 嵌套类
`Preparations`，Mixin 对 handler 签名要求逐参数一致，`Object` 顶替直接启动崩溃。
结论不是「换个写法硬注」，而是**换钩子**：NeoForge 的 `SoundEngineLoadEvent`
正好在 `apply` 的最后一行触发，时机等价、参数全是公开类型。混合器只在
「必须拦住原版流程」的地方用，能继承/能换事件的一律不用。

---

# 第十一部分 · 圣遗物与属性上限

## 11.1 圣遗物套装框架

| 东西 | 在哪 |
|---|---|
| 套装定义 | `content/items/artifact/ArtifactSet.java`（record） |
| 套装注册 | `core/system/registry/register/ArtifactSets.java` |
| 套装效果 | `content/effect/character/artifact/*` —— 和角色效果同一套容器（`ICharacterEffect`） |
| 部位物品 | `content/items/artifact/<套装>/<套装><部位>.java`，再挂到 `ModItems` + `ModModeProvider` 的 `generateFlatItem` |

`ArtifactSet(setId, twoPcEffect, fourPcEffect, hasTwoPcEffect, hasFourPcEffect)`：

- `setId` 3 位数字（001 起），同一套装的所有部位共享；
- 2 / 4 件套效果是 `DeferredHolder<ICharacterEffect, ?>`：**穿齐就挂常驻效果**，
  效果类自己决定是改属性（`addAttributeFlatModifier` / 百分比）还是只当一个「标记」
  （四件套常驻标记 + 触发出来的临时 buff，是两种效果，见下）；
- UID 规则 `32EIII`：`3` = 圣遗物类，`2` = 套装（圣遗物**物品**是 `1`），
  `E` = 效果位（0 无 / 2 只有 2 件套 / 4 两套都有），`III` = 套装编号。
  例：魔女套（2+4 件套）= `324001`，只有 2 件套 = `322001`。

## 11.2 血红之证（Scarlet Proof）

| 件数 | 效果 | 落在哪 |
|---|---|---|
| 2 件 | 攻击力 +18% | `ScarletProof2`：`addAttributePercentModifier(ATK, "scarlet_proof2", 0.18)`，装卸随效果走 |
| 4 件 | 暴击率 +16%，且**触发星扩散反应后 10 秒内**星扩散反应伤害 +40% | 常驻标记 `ScarletProof4`（只表示「穿着四件」）+ 触发出来的 `ScarletProofBuffEffect`（真正改属性的那 10 秒） |

四件套的 40% 走**反应加成区里「精通区后面」的那一份星烁加成**：

```
反应加成区 = 1 + 16 × 元素精通 / (元素精通 + 2000) + 星烁加成
                                                    └─ 这里，只给星扩散
```

所以 `ScarletProofBuffEffect.getStellarGlimmerBonus(branch)` **只在 `SWIRL` 分支返回 0.40**
（文案只写了星扩散，星超导不加）。这也正是 4.11 里「加成分支要分开写」的例子。

触发链（`SwirlReaction`）：

```
星扩散反应结算
  └─ applyScarletProofBuff(ctx.attackerEntity() as Player, 触发角色)
       ├─ 触发角色身上没有 ScarletProof4 → 什么都不做
       └─ 有 → CharacterEffectHelper.addEffect(ScarletProofBuffEffect, 200 刻)
                再触发只刷新时间（onEffectOverride），不叠层
```

| 文件 | 干什么 |
|---|---|
| `content/effect/character/artifact/ScarletProof2.java` | 2 件套：攻击力 +18%（基类 `ArtifactSetEffect`） |
| `content/effect/character/artifact/ScarletProof4.java` | 4 件套常驻标记（不加属性） |
| `content/effect/character/artifact/ScarletProofBuffEffect.java` | 10 秒 buff：暴击率 +16% + 星扩散 +40% |
| `content/items/artifact/scarlet_proof/Scarlet{Flower,Plume,Sands,Goblet,Circlet}.java` | 五个部位 |
| `ArtifactSets.SCARLET_PROOF` | `new ArtifactSet(2, …)` → UID `324002` |

注册名：`scarlet_proof2` / `scarlet_proof4` / `scarlet_proof_buff`；
物品 id：`scarlet_flower` / `scarlet_plume` / `scarlet_sands` / `scarlet_goblet` / `scarlet_circlet`；
图标 `item/scarlet_*/textures/texture.png`，物品定义与模型走 `ModModeProvider` 的数据生成，落在 `item/<物品id>/{definition,model}.json`。

## 11.3 千岩牢固（Tenacity of the Millelith）

| 件数 | 效果 | 落在哪 |
|---|---|---|
| 2 件 | 生命值上限 +20% | `TenacityOfTheMillelith2`：`addAttributePercentModifier(MAX_HP, "tenacity_of_the_millelith2", 0.20)`，装卸随效果走 |
| 4 件 | **元素战技命中敌人后**，队伍中附近的所有角色攻击力 +20%，持续 **3 秒**；每 **0.5 秒**至多触发一次；**后台也能触发** | 常驻标记 `TenacityOfTheMillelith4`（只表示「穿着四件」）+ 触发出来的 `TenacityOfTheMillelithBuff`（真正改属性的那 3 秒） |

触发链：

```
伤害统一入口 HurtEntityHelper.calculateFinalModDamage
  └─ 直伤结算完 → TenacityOfTheMillelith4.notifySkillHit(damageSource, 攻击者角色, 目标)
       ├─ spec.getAttackType() != ELEMENTAL_SKILL → 返回
       ├─ 攻击者不是角色 / 穿着四件套的标记不在身上 → 返回
       ├─ 还在 0.5 秒闸门里（PGCharacterData.tenacity4GateTick）→ 返回
       └─ 通过 → 给队伍 4 个角色各挂一个 TenacityOfTheMillelithBuff（60 刻）
                再触发只刷新时长（onEffectOverride），不叠层
```

| 设计 | 为什么 |
|---|---|
| 触发点放在 `HurtEntityHelper.calculateFinalModDamage` 的直伤分支 | 这是**唯一**一处同时拿得到「攻击类型 / 攻击者角色 / 被命中目标」的地方；后台打的战技伤害也走同一条管线，所以「后台也能触发」不需要额外接 |
| 0.5 秒闸门是**角色**身上的持久化绝对游戏刻（`PGCharacterData.tenacity4GateTick`） | 和蝶变的 `weaponPassiveGateTick`、漩流颂歌的 `whirlflow_reaction_window_end` 同一形态：换人 / 存档 / 重登都不会串，老存档读出来是 0 = 「从没触发过」 |
| 「队伍中附近的所有角色」= 同一玩家的队伍全员 | 本 MOD 的角色是**数据**（`PGCharacter`）不是实体，4 个角色共用玩家坐标，没有各自的位置可比距离（沃雅妮莎 6 命、漩流颂歌 4 件套同一口径） |
| 2 件套走 `addAttributePercentModifier`，4 件套的 buff 也走百分比 | 生命值上限 / 攻击力都是「本身就是百分数」的属性 —— 用 flat 会把 20% 变成 +20 点绝对攻击力 |

| 文件 | 干什么 |
|---|---|
| `content/effect/character/artifact/TenacityOfTheMillelith2.java` | 2 件套：生命值上限 +20% |
| `content/effect/character/artifact/TenacityOfTheMillelith4.java` | 4 件套常驻标记 + **触发入口 `notifySkillHit`** |
| `content/effect/character/artifact/TenacityOfTheMillelithBuff.java` | 3 秒 buff：攻击力 +20% |
| `content/items/artifact/tenacity_of_the_millelith/Tenacity{Flower,Plume,Sands,Goblet,Circlet}.java` | 五个部位 |
| `ArtifactSets.TENACITY_OF_THE_MILLELITH` | `new ArtifactSet(3, …)` → UID `324003` |

注册名：`tenacity_of_the_millelith2` / `tenacity_of_the_millelith4` / `tenacity_of_the_millelith_buff`；
物品 id：`tenacity_flower` / `tenacity_plume` / `tenacity_sands` / `tenacity_goblet` / `tenacity_circlet`；
模型走 `ModModeProvider` 的 `generateFlatItem`（**贴图待补**，先按普通平面物品生成）；
满命补偿的随机套装从 2 选 1 变成 **3 选 1**（魔女套 / 血红之证套 / 千岩牢固套，见 `WishSystem.giveMaxConstellationCompensation`）。

> ⚠️ **「护盾强效提升 30%」未实现**：官方文案里四件套还有这一条，但本 MOD 目前**没有护盾及其相关机制**
> （全仓只有渲染时对原版 `Items.SHIELD` 的一个判断），所以这一份暂时不做。
> `TenacityOfTheMillelithBuff` 的类注释里留了位置：将来做护盾系统时在这里加属性并一起给。

## 11.4 属性上限解放（AttributeFix 等价物）

原版给每个 `RangedAttribute` 都写死了 `maxValue`（生命 1024、攻击力 2048、护甲 30、韧性 20、
攻速 1024、移速 20、幸运 1024）—— 数值堆过头就被**静默截断**，日志里看不出任何异常。
做法照 `Darkhax-Minecraft/AttributeFix`：

| 步骤 | 实现 |
|---|---|
| 打开 `maxValue` | mixin `mixin/mixins/AccessorRangedAttribute`：`@Accessor("maxValue") @Mutable minegenshin$setMaxValue(double)`（`minValue` 同理） |
| 启动时统一放宽 | `content/attribute/AttributeCapHandler.applyCapRelief()`，在 `Minegenshin.commonSetup`（`FMLCommonSetupEvent`）里 `event.enqueueWork(...)` 调用（要等注册表就绪） |
| 放宽到多少 | `config/entity/EntityAttributeCapConfig`（`EntityConfig` 里注册），默认 **1_000_000**，可逐条改，`enable-cap-relief = false` 可整体关掉（和 AttributeFix 同时装时用） |

只处理 **`minecraft:` 命名空间**的 `RangedAttribute`：`minecraft:max_health / attack_damage /
armor / armor_toughness / attack_speed / movement_speed / luck` 各有一条配置项，
其余原版属性（比如 `scale`、`jump_strength`）保持原样 —— 它们不是「数值堆出来」的那类。
本模组自己的属性不走这条路（走 `content/attribute` 的 `AttributeInstance` / `AttributeContainer`，没有原版那种截断）。

启动日志（每条属性一行 + 一条汇总）：

```
[AttributeCap] minecraft:max_health 上限从 1024.0 提高到 1000000.0
[AttributeCap] 属性上限解放完成：扫描 7 条原版属性，放宽 7 条
```

关掉时打 `[AttributeCap] 已关闭属性上限解除`。
汇总那条是必须的：「跑了但一条都没改」和「根本没跑」光看日志长得一样，没有汇总就分不清。

> ⚠️ **`runData`（数据生成）不会触发 `FMLCommonSetupEvent`** —— 它的入口是
> `DatagenModLoader`，跳到注册数据收集就结束了。所以这条路径只能在正式进游戏时验证，
> 别拿 `runData` 的日志当「没生效」的证据。

---

# 第十二部分 · 祈愿与命座

## 12.1 祈愿（抽卡）

| 环节 | 在哪 |
|---|---|
| 入口 | H 键：`KeyMappingRegistry.WISH_KEY` → `KeyInputHandler` → `NetworkManager.wishEventToServer()` → `WishSystem.performWish(player)` |
| 消耗 | `WishSystem.WISH_COST = 160` 原石；**纯权重随机、没有保底**（`rollEntry`） |
| 卡池文件 | `src/main/resources/data/minegenshin/wish/standard.json`（路径硬编码在 `WishSystem`），结构 `WishConfig → WishPool(rolls) → WishEntry(type/id/weight/count)` |
| 缓存 | 静态 `cachedConfig`，`clearCache()` 目前**没有任何调用者** → 改完 json 要重启（或自己接一个 reload） |
| 物品掉落 | `handleItemDrop`：`new ItemStack(...)` → `NetworkManager.giveItemToPlayer` |
| 界面 | `ScreenWish` 整个文件是注释掉的 —— 现在只有按键，没有抽卡 GUI |

> ⚠️ `handleItemDrop` **没有**调 `ArtifactItem.initializeArtifactStackIfNeeded` ——
> 所以「卡池里直接放圣遗物」抽到的是**未激活空壳**（主副词条全空、`activated=false`）。
> 满命补偿那条路特意补上了这一步，别照抄 `handleItemDrop`。

## 12.2 命座系统（0~6）

| 事项 | 落在哪 |
|---|---|
| 字段 | `PGCharacterData.constellation`：`@Persisted(key = "constellation")` + `@DescSynced`（**早就存在**，以前没人用、连 getter 都没有），`copy()` 里也早就带上了 |
| 上限 | `PGCharacterData.MAX_CONSTELLATION = 6` |
| 写入 | `PGCharacterData.setConstellation(int)`（clamp 0~6 + `markDirty()`）、`upgradeConstellation()`（满命返回 `false`） |
| 角色门面 | `PGCharacter.getConstellation()` / `hasConstellation(n)` / `isConstellationMax()` / `addConstellation()` / `setConstellation(int)` |
| 同步 | `markDirty()` → `CharacterTickHandler` 的 dirty 检测 → 整包同步（这条路是通的）；`PGCharacter.addConstellation()` 另外再 `syncRealtimeState()` 双保险 |
| 天赋里怎么读 | 一律 `character.hasConstellation(n)`；「必须先解锁某个突破天赋」这类前置由天赋自己再判一次（例：薇斯娜 C2 要求 `hasSpringRiteTalent()`） |
| 调试命令 | `/minegenshin character constellation <玩家> <uuid> <0-6>`（抽一次只 +1，试 C2/C6 用这个）<br>`/minegenshin character talent <玩家> <uuid> <normal\|skill\|burst> <0-9>`（设手动升级次数，见 4.16） |
| 文案键 | `constellation.minegenshin.<角色 id>.<n>.desc`（薇斯娜 C1/C2 已写）；**角色命座名还没定，留空等你取名** |

> ⚠️ **一定要拿「玩家实际持有的那一份」角色实例**：
> `ModCharacters.getById(...)` / `getByUUID(...)` 每次调用都是**新建一个模板角色**，
> 改它的命座等于改了个没人看的副本。抽卡与命令都必须走
> `PlayerCharactersAttachment.getCharacterByUUID(uuid)`。

## 12.3 抽到重复角色 → 升命座 / 满命补偿

`WishSystem.handleCharacterDrop`：

```
没有这个角色        → addCharacter（授予）
已有、命座 < 6      → owned.addConstellation()  → 消息 message.minegenshin.wish.constellation_up
已有、命座 = 6      → giveMaxConstellationCompensation()
                        └─ 随机挑一套（魔女套 / 血红之证套）→ 整套 5 件
                           每件都先 ArtifactItem.initializeArtifactStackIfNeeded(stack) 再发
```

- 薇斯娜已入池：`standard.json` 里 `{ "type": "character", "id": "minegenshin:vesna", "weight": 10 }`
  （`minegenshin:vesna` / UID `115001`）。
- 旧的「重复角色补 75 原石」已经**换成升命座**；原石补偿常量 `DUPLICATE_COMPENSATION` 一并删除，
  只有满命时才有补偿（改成一整套圣遗物）。

---

# 第十三部分 · 武器

## 13.1 一把武器的组成（`WeaponItem`）

| 部分 | 落在哪 |
|---|---|
| 星级 / 主词条档 / 副词条 | `star`、`tier`、`subStatAttribute`（角色类构造器里设，例如 `EverlastingMoonglow`） |
| 主词条数值 | `WeaponStatData.calculateMainStatValue(star, tier, level, ascended)`：从该档的**整数**基础攻击力起步（五星 `tier1~4` = 44/46/48/49），逐级加成长 |
| 副词条数值 | `WeaponStatData.getSubStatValue(...)`：`tier` 同时决定副词条档（五星 `tier3` → `SUB_5_TIER48_*`） |
| 主词条小数修正 | `WeaponItem.mainStatDelta`（默认 0）—— `tier` 只给整数，个别武器主词条是小数时用差值补齐 |
| 武器被动 | `WeaponItem.onAbilityCast(...)` / `onLeaveField(...)` / `onHeal(...)` / `getHealingBonus()`（默认空实现 / 返回 0，带被动的武器覆写） |
| 物品模型 | 数据生成：`ModModeProvider`。**每个物品都必须有一条定义**，否则 `runData` 直接报 `Missing item model definitions` |

**UID 规则**：`100000 + 星级×10000 + 武器类型位×1000 + tier×100`（类型位：长柄1 / 单手2 / 双手3 / 弓4 / 法器5）。

## 13.2 蝶变（五星单手剑）

| 项 | 值 |
|---|---|
| 主词条 | 攻击力 **47.54**（五星 tier3 的 48 + `mainStatDelta = -0.46`） |
| 副词条 | 暴击伤害 **9.6%**（五星 tier3 → `SUB_5_TIER48_CRIT_DMG = 0.096`） |
| 模型 | 暂时**直接用钻石剑**：数据生成里 `borrowModel(..., minecraft:item/diamond_sword)` |

**武器效果：三种风按顺序轮换**

```
装备者每次施放 元素战技 / 元素爆发 → 依次获得下一种风：
  ① 忠忱之风：暴击伤害 +56%，10 秒
  ② 叛弃之风：造成的星扩散反应伤害 +36%，10 秒
  ③ 丰获之风：恢复 5 点元素能量（每 4 秒至多通过这种方式恢复 5 点）
三种循环；装备者退场时移除这些效果并重置顺序（下次从①重新开始）。
```

实现要点：

| 设计 | 为什么 |
|---|---|
| 轮换序号与限速闸门放在**角色**上（`PGCharacterData.weaponPassiveStage` / `weaponPassiveGateTick`，`@Persisted @DescSynced`） | 武器被动是「装备者」的属性；放武器 NBT 里换装/换人会漏掉「退场重置」 |
| 忠忱 / 叛弃做成 `ICharacterEffect`（`diebian_loyal_wind` / `diebian_forsaken_wind`） | 持续时间、到期移除、覆盖刷新、客户端显示全都由效果容器接管；叛弃之风只要覆写 `getStellarGlimmerBonus(SWIRL)` 就落在「反应加成区里那份星烁加成」上 |
| 丰获之风直接回能（`addElementalEnergy(5)`） | 它是瞬时效果、没有 10 秒时长，不需要做成 effect；4 秒限制用 `weaponPassiveGateTick` 记账 |
| 触发点：`ActionManager` 受理 E/Q **成功之后** | 服务端权威、且是「触发即生效」那一刻，不会出现「CD 转了但被动没触发」 |
| 退场清理：`PlayerCharactersAttachment.setCurrentCharacterIndex` 切人时通知旧角色 | 一处收口，任何带被动的武器都能复用同一个钩子 |

> ⚠️ 加新的「手写模型 / 特殊渲染」物品时，要往 `ModModeProvider.HAND_WRITTEN_MODELS` 白名单里加一行，
> 否则数据生成会报 `Missing item model definitions`（`runData` 会直接失败）。

> ⚠️ **移除效果一律用 `CharacterEffectHelper.removeEffect(...)`**，
> 不要用 `CharacterEffectContainer.removeEffectsOfType(Class)` ——
> 后者只是 `effects.removeIf(...)`，**不会触发 `onEffectRemoved`**。
> 对「在 `onEffectAdded` 里加了属性修饰符」的效果来说，那就是修饰符永远留在角色身上
> （蝶变的忠忱之风踩过这个坑；`RadianceStellarConduceEffect` 顶掉星扩散那句也是同一个写法，
> 以后给它加属性/清理逻辑时要一起改）。

**多种效果会互相顶掉吗**（蝶变的三种风就是这个问题）：
`CharacterEffectHelper.addEffect` 只按**实例 id** 查重 ——
id 不同就各自独立存在（忠忱 / 叛弃可以同时挂），id 相同才走 `onEffectOverride`（默认只刷新时长，不会先把旧的摘掉再重加）。
唯一会「顶掉」的机制是效果自己实现的互斥 `ICharacterEffect.canApplyWith(container)`（辉映·星超导顶掉星扩散用的），
三种风没有实现它，所以互不影响。

## 13.3 漩流颂歌（五星法器）

| 项 | 值 |
|---|---|
| 主词条 | 攻击力 **44.34**（五星 **tier1** 的 44 + `mainStatDelta = +0.34`） |
| 副词条 | 生命值 **14.4%**（五星 tier1 → `SUB_5_TIER44_HP_PERCENT = 0.144`） |
| 模型 | 暂时**借用书**：数据生成里 `borrowModel(..., minecraft:item/book)` |
| 类 | `content/items/weapon/catalyst/WhirlflowHymn.java`、效果 `content/effect/character/impl/WhirlflowHymnEffects.java` |

**武器效果：告真的蜜酿**

```
① 装备者治疗加成 +4%。
② 装备者每完成一次治疗（含后台治疗）→ 获得一层「告真的蜜酿」：
     装备者自身：生命值上限每层 +4%，最多 3 层；
     同时当前场上角色获得攻击力：按装备者「上一层生命值上限」换算，每超出 40000 点的 1000 点 → +0.4%
     （每层最多 8%），再按当前层数叠加；
     每层 10 秒；重复触发刷新时长并加层（按受益人各自计层）。
③ 附近的队伍成员触发 冻结 / 星扩散 后的 5 秒内，上述两份加成各提高 75%（×1.75）。
④ 后台照常生效。
```

实现要点：

| 设计 | 为什么 |
|---|---|
| 治疗加成走**已有**的 `ICharacterEffect.getHealingBonus()` → `CharacterEffectContainer` 聚合 → `PGCharacter.getHealingBonus()` → 治疗时乘进治疗量 | 治疗加成是「他打出的治疗」的乘区，**不改角色属性**；这条链路本来就为圣遗物/天赋存在，武器只要覆写 `getHealingBonus()` 返回 `0.04f`，不要再新开一套属性系统 |
| 「完成治疗」的钩子收口在 `WeaponItem.onHeal(healer, character, target, amount)`，由治疗的**唯一出口**分发（`DamageIndicatorFactory.heal` 的调用侧，例如沃雅妮莎的 `songHeal`） | 后台治疗（遥久之歌那种）和前台治疗走同一条出口，一处收口才不会漏；服务端判定（`level().isClientSide()` 先返回） |
| 一次治疗要发**两拨人**，所以拆成两个效果：`whirlflow_hymn_mead`（挂**装备者**，生命值上限 +4%/层）、`whirlflow_hymn_mead_atk`（挂**当前场上角色**，攻击力那一份） | 效果只能有一个宿主；层数各存各的 `intData`，正好对上「按受益人各自计层」 |
| 攻击力那一边记住「蜜酿是谁酿的」：效果 `intData` 里存**装备者角色 UUID**（`OWNER_KEY`） | 受益人可能换人/换装，算攻击力时要拿**装备者**的生命值上限，不能拿受益人的 |
| 「附近队友触发冻结 / 星扩散」→ `WhirlflowHymn.markReactionTriggers(...)` 里先卡「触发者必须是玩家」，再遍历**冻结点 20 格内的所有玩家**，各自给队伍里装备了本武器的角色写 5 秒窗口（`PGCharacterData.whirlflowReactionWindowEnd`，绝对到期刻） | 用**绝对游戏刻**而不是倒计时，存档/重登都不会卡住；按「附近玩家」而不是「触发者自己的队伍」遍历，其他玩家打出的冻结也能给旁边的人开窗 |
| 窗口开启时**立刻**重算一次属性（`refreshMeadEffects`）；窗口过期后的降档依赖 `onEffectTick` 重算 | 触发那一刻就要吃到 1.75 倍，不能等下一个刻才生效。⚠️ 但 `CharacterEffectEvent` 只在 `TeyvatWorldInvasion.isInvaded()` 为真时才 tick 效果 —— **非入侵状态下效果既不 tick 也不减时长**，那时 1.75 会一直挂到效果因别的原因消失为止（见 14.6；这是**既有引擎行为**，不止影响本武器） |

> ⚠️ 生命值上限 +4% 用的是 `addAttributeTempPercentModifier`，**移除**必须走 `removeAttributeModifier(..., HP_SOURCE)`
> 并且要在 `onEffectRemoved` 里做 —— 换成容器的 `removeEffectsOfType(...)` 会**不触发移除回调**，那 4%/层 就永远留在角色身上（蝶变踩过同样的坑，见 13.2）。

> 📌 **攻击力那一份的层数口径（已确认）**：生命值上限的 +4%/层会**反过来喂给**攻击力换算，
> 所以「每层值」是**按上一层那一刻的生命值上限**现算的，再**乘当前层数**：
>
> | 层数 | 换算用的生命值上限 | 每层攻击力 | 合计 |
> |---|---|---|---|
> | 1 | 50000 | (50000−40000)/1000 × 0.4% = 4.0% | **4.0%** |
> | 2 | 52000 | 4.8% | 4.8% × 2 = **9.6%** |
> | 3 | 54000 | 5.6% | 5.6% × 3 = **16.8%** |
>
> 因为 3 层合计会超过 8%，所以 **8% 是「每层」的上限**（3 层理论最多 24%）—— 否则 2 层就会和上面这个例子自相矛盾。
> 实现上把「不算蜜酿的生命值上限」记在效果实例的 `BASE_HP_KEY` 里当基准（第一次换算时采样，加层不重新采样），
> 见 `WhirlflowHymnEffects.applyAtkBonus`。
>
> 📌 **「附近」与触发者口径（已确认）**：半径 **20 格**；触发者**必须是玩家**（其他玩家打出来的冻结/星扩散也算，
> 怪物打出来的不算）。开窗按「谁在附近」算：冻结点 20 格内的**每个玩家**，各自给自己队伍里装备了本武器的角色开窗 ——
> 所以 A 打出的冻结也能给站在旁边的 B 的漩流颂歌开窗。

---

# 第十四部分 · 沃雅妮莎（Vodyanitsa）

水系五星**法器**、突破属性**生命值**、注册名 `vodyanitsa` / UID `145002`。

## 14.1 成长表（里程碑 + 线性插值）

别的角色（申鹤/哥伦比娅）是**每个等级一条 TOML**（近 300 条）。沃雅妮莎只有**里程碑**数值
（1 / 20 / 20+ / 40 / 40+ / … / 90+ / 100），所以 `VodyanitsaAttributeConfig` 里：

- `LEVELS` + `HP/ATK/DEF` 三张里程碑表（`20+` 记在 **21 级** —— 突破后基础属性会跳一档）；
- `getAllHp/getAllAtk/getAllDef()` 在里程碑之间**线性插值**出 1~100 级，返回逐级 `List<Integer>`；
- 接口和其它角色完全一致，**以后要逐级精确值/可配置，把表展开成 TOML 即可，调用方不用改**。

突破属性 = 生命值（`CharacterAscendAttribute.HP`）；表里 0 / 7.2 / 14.4 / 21.6 / 28.8% 的节奏与突破档一致。

## 14.2 技能实现

| 部分 | 数据 | 实现 |
|---|---|---|
| 普攻「水色咏叹」四段 | `NA_1..NA_4`（Lv1~15） | `VodyanitsaTalent.attack`（攻击力倍率，水元素） |
| 重击「向前掷水球」 | `CHARGE` | 视线前方 4 格、半径 1.5 格 |
| 元素战技「宣叙·晨声纷流」 | `SKILL_DAMAGE` | 自身周围 4 格范围水伤（**生命值上限倍率**，用 `hpMultiplier`）+ 获得遥久之歌；CD 16 秒 |
| 元素爆发「终奏·伴尔沉沦」 | `BURST_DAMAGE` + `BURST_SONG_BONUS` | 6 格范围水伤（生命值上限倍率），处于遥久之歌时再加一档；能量 60 / CD 15 秒 |
| 下落攻击 | 框架后补的一档（见下） | ⏳ 缺数值 + 接线 |

**下落攻击（框架后补的一档）**：这套框架原本**完全没有下落攻击**（`TalentBase` 没回调、动作数据没槽位）。

| 已完成（机制底座，其它角色行为不变） | 还需 |
|---|---|
| `ActionSet.getPlungingAttack()` / Builder `.plungingAttack(def)`；`CharacterActionData.withPlunge(step)`（**不填就没有**这一档）；`TalentBase.plungingAttack(player, character)` 回调 + 「填了才注册」 | ① 数值表（**等给**：下落期间伤害 / 落地范围伤害，倍率口径）② 接线：客户端「离地且下坠时按攻击键」→ 走 `ActionKind.PLUNGING_ATTACK`；因为普攻请求包是按连段序号传的，还需要给下落攻击一条自己的请求路径（`characterPlungingAttackRPCPacket` + `ActionManager.requestPlungingAttack`） |

**「遥久之歌」16 秒**（`Vodyanitsa.tick` 驱动，全部服务端）：

| 定时 | 做什么 |
|---|---|
| 每 **3 秒** | `SummonTargeting.defaultMode().resolve(player, null, 13, …)`：**优先玩家当前锁定的目标**，没锁就按 13 格找最近的；打一次生命值上限倍率水伤（`SKILL_DAMAGE`），并给目标挂 **水抗/冰抗 −（16.5%→39%，按表）持续 6 秒** |
| 每 **1.5 秒** | 给**当前场上角色**回血：`HEAL_BASE + HEAL_PCT × 沃雅妮莎生命值上限`，按目标生命上限夹取 |

⚠️ **减抗必须记账**：歌每 3 秒挑一个目标，16 秒里可能打好几个不同敌人 ——
只记「最后一个」的话，先被打的那些减抗摘不掉。所以用 `Map<UUID, 到期刻>`（`songShredUntil`），
每刻 `clearExpiredSongShred(player)` 逐个摘。

## 14.3 不产生通用位移

她的 `VodyanitsaResources.ACTION_DATA` 里所有招式：

```java
Engagement.melee().withDash(false).withAdhesion(0, 0).withAcquireRange(13).withKeepRange(15)
```

`moves` 一律为空（只有闪避保留自己的 `Move`）→ **只转向，不会自己往前挪**。
详见 4.13 的「角色自带位移 vs 通用位移」。

## 14.4 突破天赋 1（突破 ≥ 1）

| 效果 | 实现 |
|---|---|
| 遥久之歌期间，队伍附近角色进入**辉映·星扩散**时持续时间 **+4 秒** | `VodyanitsaTalent.songCovers(...)` + `SwirlReaction.applyRadianceToParticipants` 里加时长 |
| 遥久之歌期间触发星扩散时**改为创造流荡风旋** | 同一处判定；流荡风旋与星辉风旋其它行为一致，只是多一层降风抗。<br>⚠️ **两个坑**：① 判定必须排在 `triggerWindDamage` **之前**，否则这一下的风伤吃不到自己刚降的抗；② 「合并进已有风旋」那一支原来**漏了**这段判定 —— 场上只要已经有一个星辉风旋，后面怎么触发都转不成流荡风旋（风抗一直是 0.1）。现在两支都判、且都在打风伤之前。 |
| **创造**流荡风旋时降低附近敌人**风抗 35%（6 秒）** | `VodyanitsaTalent.shredWindAround(...)`（`ANEMO_RES`，多目标记账 + 到期清理） |
| **引爆**流荡风旋时同样降风抗 | `StellarVortexEntity.flowingSwirl` 标记（`@Persisted`）+ `explode()` 里调同一个工具 |
| 施放战技时把**当前场上的星辉风旋转化**为流荡风旋 | `VodyanitsaTalent.elementalSkill` 里扫描 26 格内的风旋 → `markFlowingSwirl()`（要求突破 ≥ 1） |

> 「转化」和「改创」都只是给风旋**打标记**：降风抗的两处（创造 / 引爆）都调
> `shredWindAround(...)`，风旋的其它行为一行没改 —— 以后做了模型，只要让流荡风旋用另一套外观即可。

---

## 14.5 突破天赋 2 与命座（1 / 2 / 4 / 6）

| 内容 | 实现 |
|---|---|
| **突破天赋 2**「领唱 / 重唱」（突破 ≥ 4） | `VodyanitsaSongEffects`：效果挂在**受益人**身上，在 `ICharacterEffect.onAttacked` 里判定「水/冰伤害」或（场上半径 20 格内有流荡风旋、或**引爆后 5 秒内**）「星扩散反应伤害」→ **消耗 1 层**并把固定值**加到基础区的「附加伤害」**（`withFlatDamageBonus`，与基础值加算 —— 和申鹤冰凌同一机制、同一钩子）；层数存效果实例的 `intData`；加伤档 = `(生命值上限 − 40000) / 1000` × **260（星扩散）/ 140（水冰）**，上限 **6500 / 3500**；施放战技时按突破 ≥4 发放（**当前场上 25 层领唱 / 其余角色 10 层重唱**，各 30 秒，`onEffectOverride` 取更长的时长与更多的层数 → 即「施放战技刷新层数」） |
| **1 命**「聚光灯下的水华」 | `VodyanitsaBuffs.Spotlight`（5 秒）：`songHeal` 里给队伍 4 人各挂一份，`onEffectAdded` 加**临时固定值攻击力 = 1% × 沃雅妮莎生命值上限** |
| **2 命**「穿彻风雪的余响」 | `DuetElement`（水/冰**暴击伤害 +50%**）/ `DuetStellar`（**星扩散反应伤害暴击伤害 +60%**）两个效果，**唤春角笛命中敌人那一刻**（施放战技命中时）就挂给**当前场上角色**，5 秒；遥久之歌每次跳动（`songAttack`）也会再挂一次并刷新；遥久之歌**持续时间 +9 秒** |
| **4 命**「柔波摇漾的低诉」 | `songHeal` 里分档：受治疗者生命值 **<40% → 本次治疗 ×1.5**；**≥40% → `addC4HpStack()`**：沃雅妮莎生命值上限 **+20%**、**6 秒**、**最多 3 层、每层独立倒计时**（`c4HpTicks[3]`，形态同整肃 `decreeTicks`，满层挤掉最早那层；`tick` 里即使遥久之歌结束也继续倒计时） |
| **6 命** | ①「黑与白的双音」**改为对队伍附近所有角色生效**（2 命只挂当前场上角色 —— 这是刻意区分的口径）；②`Glimmer`：遥久之歌期间队伍附近角色 **星扩散反应伤害擢升 30%** + **水/冰伤害 +60%**（效果时长 = 遥久之歌剩余时长） |
| 为命座打通的**两条链路** | ①**按元素/反应类型的暴击伤害**：`ICharacterEffect.getCritDamageBonus(元素, 是否星烁)` → 容器聚合 → `PGCharacter.getCritDamageBonus` → `DamageZones.rollCrit(attacker, spec)` / `rollCrit(attacker, 元素, 是否星烁)`（直伤传 spec；星烁管线手里只有 reactionType，元素由 `StellarGlimmerBranch.damageElementOf` 推出）<br>②**效果侧的擢升**：`ICharacterEffect.getElevationBonus(分支)` → 容器聚合 → `PGCharacter.getElevationBonus = 容器 + getOwnElevationBonus`。⚠️ 角色天赋自己那份要覆写 **`getOwnElevationBonus`**（薇斯娜 6 命已从 `getElevationBonus` 改名过去）—— 直接覆写 `getElevationBonus` 会把队友给你的效果加成一起吞掉 |

> 触发条件里那个「流荡风旋」判定：`VodyanitsaSongEffects.flowingSwirlActive(player)`
> = 以玩家为球心**半径 20 格**内存在 `flowingSwirl` 的星辉风旋，**或** `level.getGameTime() - 最近一次引爆刻 ≤ 5 秒`
> （引爆刻由 `StellarVortexEntity.explode()` 里的 `markFlowingDetonation` 盖章）。

## 14.6 待办 / 已知口径

| 事项 | 说明 |
|---|---|
| **指令前缀（已改成 `minegenshin`）** | 全部指令现在都在**同一个** `minegenshin` 根下：`/minegenshin character …`、`/minegenshin primogem …`。<br>⚠️ 踩过的坑：Brigadier 的根子节点是 `putIfAbsent`，**同名根只能注册一次**，第二个会被**静默丢弃**（一整棵子树凭空消失）。所以 `PrimogemCommand` 不再自己 `register`，只提供 `buildPrimogemNode()`，由 `CharacterCommand` 把它和 `character` 一起挂到同一个根上。以后再加指令也要往这个根里挂，**不要**新开一个 `Commands.literal("minegenshin")` |
| **怪物抗性：减抗是<b>乘算</b>（写 percent），但读取必须用 total** | 规则是「抗性 ×(1 − 降低比例)」，也就是**基于目标自身抗性**的乘算 —— 属性系统里正好对应**百分比**修饰符：<br>`getTotalValue() = base × (1 + percent) + flat` → `0.1 抗性 + percent(-0.165) = 0.1 × 0.835 = 0.0835` ✔（写成 flat 才是加算，错误；流荡风旋的「降低风抗 100%」= percent -1.0 → 抗性 ×0 = 0 ✔）。<br>真正的 bug 在**读取端**：`TeyvatEntityStats` 的抗性 getter 以前读 **`getBaseValue()`**，而修饰符改的是 percent/flat —— 怪物身上任何减抗都等于没写（沃雅妮莎 E、申鹤领域、流荡风旋全中招）。现在 8 个抗性 getter 都读 `getTotalValue()`（`attack()` 仍是 base）。<br>排查口诀：日志里「抗性区 = 1 − 抗性」没变 → 先看这个属性是不是被 base/total 读错了。 |
| **倍率区 ≠ 增伤区，「技能倍率提升」别往倍率区塞** | 两个区在不同的地方：<br>· **倍率区** = `1 + 倍率提升`，字段是 `ModDamageSpec.skillMultiplierBonus`（行秋 4 命那类「技能倍率提高」）；<br>· **增伤区** = `1 + 元素伤害加成 + 效果加成 + 招式增伤`，字段才是 `ModDamageSpec.damageBonus`（`withDamageBonus(...)`，按招式盖，和 `sovereigntyBonus` 同一形态）。<br>文案写「伤害提升 X%」的一律进**增伤区**。沃雅妮莎大招「处于遥久之歌时额外一档」原来被加进了 `hpMultiplier`（基础区），现在改走 `withDamageBonus`，日志里会显示成 `增伤区 【1 + 招式增伤】`。 |
| **武器副词条成长是「每 5 级一档」** | 那张倍率表（`WeaponSubStatConfig.MULT_01…MULT_19`）的键名就是档位：`lv1 / lv5 / lv10 / … / lv90`，共 19 档。查档必须走 `WeaponSubStatConfig.getMultiplier(level)`。<br>⚠️ `WeaponLevelData.getSubStatMultiplier` 原来按 `level - 1` **直接索引**这张表 → 等于「升一级跳一档」，副词条两下就顶到接近满级（1 级 1.0 → 2 级 1.162 → 5 级 1.565…）。现在它只是转调 `WeaponSubStatConfig.getMultiplier(level)`。 |
| **主词条/副词条的数值来自 `run/config`，改 Java 默认值对已有存档无效** | `StringDoubleValue` 这类值是**写进 config 文件**的：新装的默认值只在**第一次生成**时落盘，之后改 Java 里的默认值只会影响「重新生成的配置」。<br>典型例子：五星暴击头 `crit-rate.percent_per_level` 的 Java 默认值是 `0.0132`（→ 满级 `0.047 + 20×0.0132 = 31.1%`），但 `run/config/minegenshin/artifact.toml` 里存的是旧的 `0.013`（→ 30.7%）。<br>**所以改数值类默认值后，要同时改 `run/config/minegenshin/*.toml`（或让玩家删掉那一项让配置重新生成）。** |
| **三条「来源 / 归属」口径（已确认）** | ① **冻元素记两个源**：「冻元素」是冻结反应生成物、本身没有来源，所以要分别记下**水来源角色**和**冰来源角色**；星扩散收贡献者时从冻元素里读**冰**的来源，这样后台挂冰的角色不会掉出名单。<br>② **星扩散归属**：**风段** = 本次星扩散的触发者；**冰段**（星璇爆炸）= **结算到该星璇的最后一次星扩散的触发者** —— 也就是「最后一次让它生成 / 升级」的那次，「影响范围之外触发的星扩散不算」。<br>③ **月感电归属**：雷暴云每 2 秒周期结算的伤害源 = **造成月感电反应的那一下的触发者**（最近一次），**不是**挂水/挂雷的人，也不是 dot 自己。 |
| **剧变反应的「等级系数」不是防御区那个等级项** | `TransformativeDamage` 的等级系数区要用**按等级查表的剧变等级系数**（`DamageZones.levelCoefficient(level)`）。<br>⚠️ 日志里原来印的是 `等级 × 5 + 500` —— 那是**防御区**用的等级项，和算出来的伤害完全对不上（数值行印的和末尾结果不是同一个东西，一看就「对不上」）。 |
| **星烁多人权重：单人必须按 1.0 算** | `StellarDamage.combine` 的 `0.6 / 0.3 / 0.05 / 0.05` 只在**多人一起触发**时是分配比例。原来哪怕只有 1 个贡献者也乘 0.6 → **实际伤害只有理论值的 60%**，而日志印的是没乘权重的单人公式，公式和结果对不上。<br>现在 `size() == 1` 直接按 1.0 返回。**这也说明：看到日志公式和末尾结果对不上，往往就是内部结算真的错了，不只是排版问题。** |
| **圣遗物词条只能抽一次：客户端不许重抽，缺标记也不许重抽** | 三个坑叠在一起会出「穿一次再卸下来，副词条变了一套，之后又正常了」：<br>① **客户端本地重抽**：客户端原来会在本地也调一次 `initializeArtifactStackIfNeeded` 补激活 —— 里面是 `buildInitialStats(new Random())`，**客户端的随机数和服务端那次不是同一份**，本地背包于是存着另一套词条，等它被同步/卸下写回服务端就把玩家真正的词条顶掉了。**现在客户端一律不抽**，改由服务端把权威结果**推回去**（`artifactActivatedRPCPacket` 带 `ItemStack`，照抄 `artifactLevelUpRPCPacket` 的写法；`ClientHandler.applyActivatedArtifactClientHandler` 只把它放进槽位）。<br>⚠️ 注意：**背包 attachment 的同步不保证在点按钮那一刻就到**，而且**界面也不是每刻重建的**（只在点击/选择时重建）—— 所以服务端收到请求抽完词条后，必须**主动推一份权威数据回客户端**（`artifactActivatedRPCPacket` 带 `ItemStack`，照抄 `artifactLevelUpRPCPacket` 的写法；`ClientHandler.applyActivatedArtifactClientHandler` 把它放进槽位），并且客户端收到后要**显式重画当前界面**（`ScreenArtifactEquip.refreshIfOpen()`，靠 `OPEN_STATE` 记住当前界面状态、界面关闭时置空；同时把 `st.selectedEnt` 里那份旧拷贝**重新从背包读一份**，否则详情面板还是旧的）。<br>⚠️ 少了「显式推送」→ 按钮看起来完全没反应、哪里都没抽；少了「显式重画」→ 抽是抽了，但面板要等玩家再点一次别的圣遗物才更新。这两个坑都踩过了。<br>② **判定太宽**：`initializeArtifactStackIfNeeded` 只看 `activated`，于是只要有**任何一条路径**漏了把它置 true，下一次右键/装备/卸下就会**整件重抽主副词条**。现在要求「**未激活 且 主词条还没抽**」两个条件同时成立才抽；已经有词条的只把标记补上（并打一条日志）。<br>**口诀：词条是服务端的权威数据，`activated` 只是一个标记 —— 两者永远不要用一次重抽去「修复」。** |
| **直伤管线必须用「带 spec」的暴击判定** | `DamageZones` 有两个版本：`rollCrit(attacker)`（只用统一 CDG）和 `rollCrit(attacker, spec)`（统一 CDG **+ 按元素/反应类型的额外暴击伤害**，走 `PGCharacter.getCritDamageBonus(element, stellar)`）。<br>⚠️ `DirectDamagePipeline` 原来调的是**不带 spec** 的那个 —— 于是所有「按元素/按反应类型」加暴击伤害的效果（沃雅妮莎 2 命的「黑与白的双音」水/冰暴伤 +50%）**全都静默失效**：效果挂上了、容器也聚合了，就是没人读。星烁管线（`StellarDamage`）一直用的是带 spec 的版本，所以那边是好的。现在直伤也改成 `rollCrit(attacker, spec)`。<br>排查：暴击那一跳日志里 `【1 + 暴击伤害】` 的值如果没变，先看这一层用的是哪个重载。 |
| **「领唱 / 重唱」是<b>二选一</b>，但「有没有流荡风旋」的判定曾经恒真** | 判定口径（已确认）：**场上有流荡风旋（或刚引爆 5 秒内）→ 只认星扩散反应伤害**，此时**水/冰增伤失效**；否则只认水/冰伤害。两档数值不同（星扩散 260/档、水冰 140/档）。<br>⚠️ 曾经的 bug 不在这里，而在 `VodyanitsaSongEffects.flowingSwirlActive`：`lastDetonationTick` 初始化成 **`Long.MIN_VALUE`**，于是 `gameTime - lastDetonationTick` **溢出**，`if (diff <= 100 && gameTime >= lastDetonationTick)` 在**从没引爆过**时也成立 → 判定恒为 true → 一直按星扩散模式走 → 沃雅妮莎自己的水伤一层都不消耗、附加伤害永远是 0（看起来像「突破 4 没生效」）。现在用 `-1` 当哨兵值并显式要求 `lastDetonationTick >= 0`。<br>**教训：用「极大/极小值」当哨兵值做减法判断，一定会溢出。** 另外该方法现在会在返回 true 时打日志（引爆窗口：引爆刻/距今多少刻；或场上有风旋：**坐标 + 距离**），用来排查「明明没有风旋却判定为有」。 |
| **`atkMultiplier` 默认是 0，不是 1** | 曾经默认 1.0，于是「纯生命值倍率」的战技/大招也会莫名其妙带一项 `攻击力 × 1`（日志里就是 `【8 × 1 + 1154 × 0.457】` 那种）。现在 `ModDamageSpec.Builder` 的 `atkMultiplier` 默认 **0**，和 `hpMultiplier / defMultiplier / emMultiplier` 一致；`baseZoneText` 只写倍率非 0 的项，所以纯生命值伤害的基础区只剩 `【生命值 × 倍率 + 附加伤害】`。<br>⚠️ 攻击力倍率的招式必须**显式** `.multiplier(x)`（仓库里所有角色都显式写了，改默认值不影响它们）。`ModDamageSpec.lunarDirectHp` 也从 1.0f 改成 0f。 |
| **远程角色：伤害要结算在「索敌到的目标」身上** | 「生效攻击距离」只决定**客户端**要不要继续往前贴（`ActionStep.withAttackRange`）；如果**服务端**的伤害判定框还是「以玩家为中心」写的，那 13 格射程就只是白设 —— 人停在 13 格外、判定框却只有 3 格，表现就是「还是必须贴身」。<br>法器/弓这类远程角色的正确写法：用 `SummonTargeting.defaultMode().resolve(player, null, RANGE, 半径搜索)` 索敌（优先玩家锁定目标），拿到目标后**直接把伤害 `hurtServer` 到它身上**；「以玩家为中心的 AoE」只作为贴脸时的溅射保留。沃雅妮莎的普攻/重击/战技/大招现在都是「① 13 格索敌目标 → 直接结算；② 玩家周围一圈溅射（跳过①已经打过的那个，避免重复）」。 |
| **「索敌半径」≠「生效攻击距离」** | `Engagement.withAcquireRange(13)` 只决定**选得到多远的目标**；框架「够得着就停下出手」看的是 `ActionStep.effectiveAttackRange()`，默认是 `DEFAULT_MELEE_RANGE`（近战 3 格）。**两个都要设**，只设前者角色仍会贴脸出手 —— 沃雅妮莎（法器 13 格）就是这么变成近战的，现在普攻 4 段 / 重击 / 战技 / 大招都显式 `withAttackRange(13f)`（见 `VodyanitsaResources.ATTACK_RANGE`）。 |
| **新角色必须登记到 `CharacterAnimationRegistry`** | `CharacterActions.getFor(player)` 是**按角色 ID 查表**的：查不到就返回 `CharacterActionHandler.EMPTY`，于是普攻 / 战技 / 爆发的按键**不播动画、不发请求、一条日志都没有**（`ActionManager` 那几行 INFO 也不会打）—— 看起来就像「按键完全没反应」。<br>沃雅妮莎就是因为漏了 `registerPlaceholder(Vodyanitsa.ID)` 才三个键全哑。**加角色时一定要往 `CharacterAnimationRegistry.registerAll()` 补一行**（就算没有专属动画，也要挂上通用的 `ResourceDrivenActionHandler` + `DefaultCharacterAnimations`，动作时序来自角色自己的 `ACTION_DATA`）。 |
| **改物品注册名 = 老存档必崩，必须登记** | 物品改了注册名（`diebian` → `beyond_the_chrysalis`）之后，老存档里那份 NBT 还是旧 id，`ItemStack.CODEC` 一解析就抛 `Unknown registry key … minegenshin:diebian` —— **整份角色数据都读不出来**，玩家会卡在「无效的玩家数据 / Couldn't place player in world」，进不去世界。<br>解法：`ModSyncAccessors.deserializeFromTag` 在反序列化**之前**先把 tag 里的旧 id 改写掉，映射表是 `ModSyncAccessors.LEGACY_ITEM_IDS`（递归匹配 `"id": <旧 id>`，只认表里登记过的值，不误伤别的字符串）。**以后凡是改物品注册名，都要往这张表补一条。** |
| **读不出来的角色不能留在列表里** | `PlayerCharactersAttachment.getCharacterByUUID` 会和 `bindAllOwners` 里的 `removeIf(c -> c == null)` 一起挡住 null —— 反序列化失败会在 `ownedCharacters` 里留下 null，不挡的话玩家一进世界就在 HUD 的 `getCurrentCharacter()` 上 NPE 崩掉（比「少一个角色」严重得多）。 |
| **效果只在「入侵状态」下 tick（保持现状）** | `CharacterEffectEvent.characterEffectTick`（服务端 PlayerTick）第 25 行有一句 `if (!TeyvatWorldInvasion.get(sl).isInvaded()) return;` —— 没进入侵时所有角色效果都不 tick、也不减持续时间，`onEffectTick` 里的「每刻重算 / 到期降档」全都不会跑。<br>**结论：保持现状。** 入侵一旦开始就没有退回的途径，而所有效果机制本来就只在入侵后才可能出现，所以这条不构成实际影响。以后写效果时注意：**别把「会在非入侵状态下发生」的逻辑只挂在 `onEffectTick` 里**（需要的话在触发点上立刻算一次，漩流颂歌的 `refreshMeadEffects` 就是这么做的） |
| **法器「漩流颂歌」** | 已实现，见 **13.3** |
| 贴图 | 沃雅妮莎仍走原版渲染（`textureId = vodyanitsa`），模型后做 |

---

# 附录 A · 已知问题与待办

> 以下都是**代码里实际存在、尚未修改**的问题。有些会影响数值，需要你确认设计意图后再动。

## A.1 技能 / 伤害

| # | 位置 | 问题 |
|---|---|---|
| 1 | `VesnaTalent.attack` | 第 6 段对同一目标连调两次 `hurtServer`，叠加该段 4 个 hit → 一次按键最多 8 个伤害事件 |
| 2 | `VesnaTalent.attack` | 风铃数量按**回调次数**而非段数生成：三段 2×2=4 个、四/五段各 4 个、六段 4×3=12 个 |
| 3 | `ShenheTalentConfig.getNABase` | `switch` 只写到 `case 5`，第 6 段落到 `default -> 0.0` → 第 6 段基础倍率恒为 0 |
| 4 | `VesnaTalent.elementalSkill` | `castXiangFengJian` 找不到目标就返回，但能量已扣、`advanceAfterCast` 照跑 → 白扣 |
| 5 | 星扩散调用 | 三个调用点把倍率当作 `elementAmount` 传，`stellarCoefficient` 硬编码 0.5 → 所有星扩散伤害恒为 `ATK × 0.5`，与技能等级无关 |
| 6 | `VesnaTalent` | 未覆盖 `elementalBurst` → 大招扣 80 能量 + 200 刻 CD 但无任何伤害/效果 |
| 7 | `VesnaTalent` | 未覆盖 `dodge` → 闪避只有动画 + `Move(0,2.0)` 位移 |
| 8 | `TalentBase` | `CHARGED_ATTACK` 复用 `skill.tap()` → 薇斯娜重击播的是战技动画 `skill_no_energy` |
| 9 | `VesnaResources` | `ActionStep.cooldown`（360 / 400）无人读取，真实 CD 硬编码在 `Vesna.applyElementalSkillCooldown` 和构造函数里 |
| 10 | `ActionManager` | `buffered` 只被赋 null，动作缓冲（预输入）是死代码；执行期内被拒的请求直接丢弃而非排队 |
| 11 | `DecayCounterManager` | ~~非默认衰减组统统归到 `"custom"` 分组 → 风铃与 E 共用一个计数器~~ **已修复**：现为每组各一份 key（`groupKeyOf`），计数器只在运行时存活 |
| 12 | `VesnaTalent` 注释 | `VESNA_WIND_BELL_DECAY` 的 Javadoc 写「100 序列」，实际是 50 |
| 13 | `VesnaAttackProjectile` | 残留调试字段 `@Persisted @DescSynced private int test`（赋值 15222） |
| 14 | 伤害链路 | 未入侵世界（`TeyvatWorldInvasion.isInvaded()` 为 false）时所有 `ModDamageSource` 伤害为 0 |

## A.2 动作 / 动画

| # | 位置 | 问题 |
|---|---|---|
| 15 | `ArlecchinoTalent` / `ColumbinaTalent` / `RaidenShogunTalent` | 用 `comboStage % getMaxCombo()`，第 N 段算成第 0 段，倍率串位（有边界判断，不会崩） |
| 16 | `ActionStep.priority` | 客户端层级不读它，目前只是数据记录 |
| 17 | `character_system` 配置 | COMMON 配置，客户端与服务端各读自己的文件；专用服务器上两边不一致会错位 |
| 18 | `CharacterAnimationRegistry` | 非 Vesna 角色只有角色壳，没有 `ACTION_DATA` → 走兜底时序、通用动画名（会被动画保护拦下） |

## A.3 渲染 / 骨骼替换

| # | 位置 | 问题 |
|---|---|---|
| 19 | `CharacterWeaponMount` → `CharacterBoneMount` | 已重命名并支持多骨骼；旧的单骨骼写法仍可用（`CharacterBoneMount.of(name)`） |
| 20 | 骨骼挂点数值 | 默认「原样」；武器模型照物品展示尺寸做，第一次接上几乎肯定要调 scale/offset/rotation |
| 21 | `GeoItemModelResolver` | 从物品反解 geo 模型依赖 GeckoLib 的 `GeoRenderProvider`；反解失败会静默退回「整个物品模型」，排查时看日志 |
| 22 | ~~缺图标~~ | ✅ 已随测试内容清理（`test_sword` 已删除）。现在的约定：geo 物品补一张 `item/<物品id>/textures/icon.png`，否则物品槽画的是它的 UV 图集 |
| 23 | ~~test_sword 的 `display`~~ | ✅ 已随测试内容清理 |
| 24 | ~~test_sword 的网格~~ | ✅ 已随测试内容清理 |
| 25 | 物品定义格式 | `oversized_in_gui` / `hand_animation_on_swap` / `swap_animation_scale` **平铺在顶层**；早先写成 `"properties": {...}` 被 codec 静默忽略，已改成顶层（见 [9.7](#97-数据生成物品模型与物品定义)） |
| 26 | ~~杂项文件~~ | ✅ 已删除：`assets/color.txt`、`code.txt`、`render.txt`、`latest.log`、`data/.../untitled-1.java` 这批误粘文件都清掉了 |
| 27 | 角色模型原点 | ✅ 已修：`CharacterRenderer.adjustRenderPose` 去掉了基类（摆件渲染器）的 `translate(0.5, 0.51, 0.5)` —— 模型以原点为中心导出，那半格会把角色推离判定箱约 0.7 格。第一人称的「反向补偿」也一起删了 |

## A.4 索敌 / 突进

| # | 位置 | 问题 |
|---|---|---|
| 28 | `CombatTargeting` 的清理 | ✅ 已修：服务端每刻 tick（`CharacterTickHandler`）+ 下线时 `onPlayerRemoved`（`ServerAnimationTicker.onPlayerLoggedOut`）。修之前服务端的锁永不过期，`moves` 位移在首次攻击后永久失效 |
| 29 | 召唤物索敌 | 目前只改了薇斯娜的风铃；`VesnaSpiritSwordEntity` 还是纯独立索敌，要跟主人需要接 `SummonTargeting` |
| 30 | 突进 | 位移在客户端直接改 `deltaMovement`，服务端跟随。目标高速移动时可能追不上（有 `MAX_APPROACH_TICKS` 兜底，超时也会继续出刀） |
| 31 | 转向只写 `yBodyRot` | ✅ 已改成只转身体：镜头（`yRot`）完全归玩家，一点都不会被拽。代价是**远端玩家看到的身体朝向**不精确（`yBodyRot` 不走网络，别人看到身体跟着头走）——只影响观感，伤害判定用服务端的目标 id |
| 32 | 远程招式的落点 | 「远程」目前只影响**索敌与突进**（不冲上去、够得着才锁）；真正的弹道 / 投射物还是各技能自己实现，框架不代劳 |
| 33 | 自动转向只覆盖动作窗口 | 转向预算 16 刻用完就还给玩家；长按鼠标持续攻击时每一下都会重新起算，观感上接近「一直锁着」 |

## A.5 第一人称 / 音效 / 多文件动画

| # | 位置 | 问题 |
|---|---|---|
| 34 | 第一人称机位数值 | 渲染链路已经做完了（`FirstPersonCharacterRenderer`：拦 `RenderHandEvent` → 在相机空间画模型），但 `FirstPersonCamera` 的默认机位是**估的**，第一次进游戏大概率要微调（`offsetZ` 调大让武器进画面），见 [10.3](#103-第一人称动画怎么写blockbench--geckolib) |
| 35 | `vesna_fp.animation.json` 的数值 | 我按常见轴向约定写的模板，**没有进游戏验证过**。手臂转反了就翻该轴符号；用 Blockbench 的相机预览对着调最快 |
| 36 | `FirstPersonAnims.FirstPersonCamera` | 只在模式 B（复用动画）下有意义；模式 A 走 fp_ 动画时它不参与 |
| 37 | 音效 ogg | 目录与加载链路都好了（`character/<角色id>/sounds/*.ogg` **丢进去就响**）；缺的是音频文件本身 |
| 38 | `actionSequence` | 只在 `changeState` 时 +1；如果某次动作没走 `changeState`（比如动画缺失被跳过），序号不变，同一刀可能抽到同一个变体 |
| 39 | **编排音效只有本人听得到** | `SoundCue` 走 `playLocalSound`（本地播放），别人只听到 `soundForState` 那一条。要让所有人听到整套编排，得把 cue 解析结果随动画状态同步发出去由服务端广播 |
| 40 | `"preload": true` 无效 | 原版只会在音频引擎启动那一次消费预加载队列，我们的表在那之后才补进去 → 该字段写了没效果（已在 [10.5](#105-音效文件放哪怎么被加载) 标注） |
| 41 | RPC 用实体数字 id | `ActionServer` 的四个出手包都传 `targetEntityId`；若目标在包到达前卸载且 id 被复用，服务端会锁到无关实体（`SummonTargeting` 会读这个锁）。彻底修要改传 UUID |
| 42 | `SoundCue.play(delay, names)` | 会把 `delay` 同时写进每个 `SoundRef`，但 cue 路径只读 `cue.delay` → `SoundRef.delay` 被静默忽略（无副作用，知道即可） |

---

# 附录 B · 术语表

| 术语 | 含义 |
|---|---|
| **常态动画**（Locomotion） | 站/走/跑/蹲/睡/爬/游/跳这类循环动画 |
| **动作动画**（Special / Action） | 普攻、战技、大招、闪避这类一次性动画 |
| **准备阶段**（prepareTicks） | 吟唱/读条 —— 技能还没开始发生，**可以被打断**（走开、跳跃、挨打都会作废） |
| **执行期**（protectDuration） | 技能真正在发生的部分（位移 + 动画 + 伤害点），**任何来源都打断不了** |
| **硬直**（actionLockFrames） | 执行期的剩余刻数，同级或更低优先级不能打断 |
| **定身**（movementLock） | 输入被冻结的时长，用于执行期（大招整段、技能前 8 刻、普攻起手 5 刻） |
| **后摇** | 执行期结束到动画播完之间，可被常态输入打断 —— **唯一的设计内取消窗口** |
| **触发即生效**（onCastStart） | tick 0 的回调：换姿态/开模式/扣资源放这里，不能放伤害点 |
| **连击窗口** | 硬直结束后还能接续下一段的刻数（30） |
| **收尾动画**（comboEndAnim） | 最后一段连招打完自动接上的动画 |
| **资源 → 天赋链路** | `XxxResources.ACTION_DATA` → `XxxTalent.buildActionSet()` → `ActionSet` → 天赋回调 |
| **动画保护** | 切动画前验证动画名真实存在，不存在就不切 |
| **骨骼挂点**（BoneMount） | 「把某个内容画到模型某根骨骼上」的配置 |
| **源骨骼**（sourceBone） | 从源模型里挑出来的那根骨骼 |
| **目标骨骼**（boneName） | 角色模型上被替换的那根骨骼 |
| **原始姿态** | 动画缺失时模型呈现的状态：所有部件与特效全露、角色呆站 |
| **软锁定**（soft lock） | 自动索敌选中的目标；可被转头/超距/闲置自然丢掉，不是硬绑定 |
| **迟滞**（hysteresis） | 保持圈比索敌圈大，避免两个近处目标之间来回抖 |
| **宽限**（grace） | 违反保持条件后还能撑几刻才真丢锁，用来滤掉瞬时抖动 |
| **突进**（approach / dash） | 目标在攻击距离外时先冲过去再出手；期间动画冻结在起手帧。**全向**（含 Y），到位留 0.6 格宽容度 |
| **吸附**（adhesion） | **出手瞬间**朝目标推一小步（一次一小段，不是持续跟随）；缺口大于 1.5 格时改走突进 |
| **攻击距离**（attackRange） | 这一段攻击的生效距离；近战索敌距离通常大于它，差额就是要突进补的 |
| **交战形态**（Engagement） | 逐招式的「怎么接近目标」：远程/近战、要不要突进、索敌多远 |
| **远程招式** | `Engagement.ranged()`：不突进 + 索敌 = 攻击距离。**判定单位是招式，不是角色** |
| **衰减追角** | 每刻转掉剩余夹角的一个比例，先快后慢；转向「滑过去」而不是瞬移的实现方式 |
| **索敌参数**（`CombatTargeting.Params`） | 一次索敌/保持用的四个数（索敌距离/角度、保持距离/角度），由招式带进来 |
| **召唤物索敌模式** | `SummonTargeting`：跟主人目标 / 严格跟主人 / 完全独立 |
| **资产根** | `assets/minegenshin/{character,item,entity,icon}/`；模型动画由 `GenshinGeoCache` 自己扫 |
| **回退动画文件** | 主文件查不到动画名时继续往下找的那几个文件（第一人称、动作包） |
| **`fp_` 前缀** | 第一人称动画的命名前缀；运行时按该名字存不存在自动选模式 A / B |
| **模式 A / B** | A = 有 `fp_*` 动画；B = 没有，复用普通动画 + 机位补正。**不是枚举，是同一开关的两种结果** |
| **SoundCue** | 一个时间点上的音效编排；`PLAY_ALL` 全播，`PICK_ONE` 按权重抽一条 |
| **确定性随机** | 种子 = 玩家 UUID + 动作序号；各客户端算出同一结果，省掉同步字段 |
| **角色音效定义**（`character/<id>/sounds.json`） | 逐角色的声音事件定义；短名自动带角色前缀，`name` 相对本角色的 `sounds/` |
| **`CharacterSound`** | 覆写了 `getPath()` 的原版 `Sound` 子类 —— 让声音文件可以待在角色目录里 |
