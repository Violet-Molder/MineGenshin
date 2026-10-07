# 战斗 · 攻击与伤害管线

## 这个系统负责什么

从"谁打了谁"到"扣多少血、显示什么数字"的完整链路：攻击判定 → 伤害数值计算 → 元素与反应参与 → 衰减（附着冷却）→ 结算与表现。

> **本项目约定（事件网络落地后的口径）**：攻击相关处理一律**订阅事件网络广播的事件**——
> 攻击行为 `elementlib:attack_performed`（对空也算、多段算多段）、
> 击中目标 `elementlib:attack_hit`（实体与方块都算）、
> 造成伤害 `minegenshin:damage_dealt`（血量真的掉了才算，盾全挡/免疫不算）；
> 不得注册 NeoForge 的底层注入钩子、不得直连 Mixin、不得在攻击入口里硬编码别的模块的逻辑。
> 这条替换了早期「不许自行注册监听」的写法——那时还没有事件网络，只能靠「都挤进这一个入口」防分裂。
> 事件系统的设计与事件总表见 `G:\AI\Codex\Skill\像素原神\元素起源\045-事件网络系统-企划书.md`。

> **三个层次别混**：攻击（出手，与有没有目标无关）→ 命中（碰到了实体或方块）→ 造成伤害（血量真的掉了）。
> 被盾全挡下时前两层为真、第三层为假；「普通攻击产球」判定的是第三层，但它由伤害管线
> **内部直接驱动**（与 `minegenshin:damage_dealt` 的广播同一时刻、同一级别），不走事件订阅 ——
> 它属于伤害结算自身的一部分，不是旁观者。

> **方块侧的元素附着**由 `attack/ElementalAttackSweep` 负责：每个**伤害点**按这一招的攻击距离取一次范围，
> 把范围内的「可附着方块」交给 `BlockElementHelper.applyElement` → 宿主附着入口
> （附着、附着内反应、飘字与生物走同一条路）。方块没有血量，所以它不经过 `DirectDamagePipeline`、
> 也不过衰减计数器；但它**受原神模式门禁**（非原神模式下不产生任何元素附着）。
> 以前那条"左键点方块"的旁路（`BlockElementAttackHandler`）已随宿主抽象落地退役 ——
> 它既不在实战攻击路径上、又没有模式门禁，正是"内核分裂"的典型。

## 子包边界

| 包 | 职责 | 不该做的事 |
|---|---|---|
| `core/system/combat/attack/` | 攻击判定与伤害管线 | 不放动作时序、不放动画 |
| `core/system/combat/damage/` | 伤害描述、数值、标签、飘字数据 | 不做命中判定 |
| `core/system/combat/decay/` | 附着/反应冷却计数器 | 不参与伤害数值 |
| `core/system/combat/targeting/` | 索敌与目标策略 | 不产生伤害 |
| `core/system/combat/action/` | 动作状态机与执行（见"动作与动画"） | 不直接改血量 |

## 关键类

| 类 | 职责 |
|---|---|
| `attack/DirectDamagePipeline` | 直伤入口：组织一次伤害从来源到目标的完整流程 |
| `attack/DamageZones` | 范围/区域伤害（圆形、柱状、扇形等）的收集与命中 |
| `attack/HurtEntityHelper` | 对目标施加伤害的辅助（处理无敌帧、免疫、事件顺序） |
| `attack/AttackerResolver` | 从上下文推断攻击者（玩家、召唤物、环境） |
| `attack/ElementalAttackSweep` | 每个伤害点把元素留给范围内的「可附着方块」（方块没有血量，只走附着） |
| `about/host/ElementalHost` · `EntityHost` · `BlockHost` · `CharacterHost` | 可附着宿主：元素挂在什么上、谁能挂、能不能反应（见"元素载体：可附着宿主"） |
| `attack/{Direct,Lunar,Stellar,Transformative}Damage`、`BurstLanding` | 不同伤害类型/来源的专门管线 |
| `damage/ModDamageSpec` | 一次伤害的完整描述（倍率、元素、来源、标签、是否暴击） |
| `damage/ModDamageSource` | 自定义 `DamageSource`（区分原神伤害与原版伤害） |
| `damage/DamageIndicatorFactory` | 由伤害结果生成飘字数据并下发 |
| `damage/DamageTrace` / `DamageLabels` | 伤害追踪与分类标签 |
| `decay/DecayCounter*` | 每实体每元素的衰减计数器（决定本次附着是强/弱、是否消耗） |

## 一次伤害的完整链

```
输入（按键动作 / 技能 / 被动触发）
   → ActionManager 在关键 tick 触发 onActiveStart（见"动作与动画"）
      → ModDamageSpec 组装：倍率 · 元素 · 来源 · 标签
         → DirectDamagePipeline（或 DamageZones / Transformative 等专门管线）
            ├─ ① 衰减：DecayCounterManager 决定本次附着强度（强/弱）与伤害系数
            ├─ ② 过盾：core/system/shield 裁决 BLOCK / REACT_ONLY / ALLOW
            ├─ ③ 附着：ElementalAttachmentHelper.attach(宿主, ...) 写进目标 StatusContainer
            │        （宿主第一段筛查在这里；元素本体在 `core/element/`，档位在 `core/system/about/`）
            ├─ ④ 反应：core/system/reaction 算倍率并叠加（宿主第二段筛查在这里）
            ├─ ⑤ 乘区：基础 × 暴击 × 增伤 × 防御 × 抗性 × 反应 × 衰减
            └─ ⑥ 免疫：目标免疫该元素 → 伤害归零（附着与反应已经跑完）
            → HurtEntityHelper 施加伤害
            → DamageIndicatorFactory → DamageIndicatorRpc → 客户端飘字
```

关键顺序：**衰减 → 过盾 → 附着 → 反应 → 乘区 → 免疫 → 实际扣血 → 飘字**。改顺序会改变数值表现。
其中"附着排在免疫之前"是有意为之：**免疫只拦伤害，不吞附着** —— 免疫某个元素伤害的单位照样被挂上元素、照样参与反应
（元素生物就是靠这条拿到"免疫同元素伤害但仍会挂冰"的表现）。

## 直伤 vs 反应伤害

- 直伤走 `DirectDamagePipeline`：倍率来自角色/武器/圣遗物，元素来自角色或武器附魔。
- 反应伤害（蒸发/融化/超导…）由 `core/system/reaction/builtin/` 的实现计算，输入是"已附着元素 + 本次攻击元素 + 触发者属性"，倍率走配置。
  增幅类（蒸发/融化）的倍率在触发它的直伤乘区里用；剧变类（超导/感电/扩散）统一由 `TransformativeDamage` 结算，
  倍率项在 `TransformativeDamage` 的 switch 里从 `ReactionConfig` 取 —— **漏加分支会静默落到 1.0**，加新剧变反应时记得补。
- 剧变类（`TransformativeDamage`）与月/星相关伤害有各自管线，**新增伤害种类时先看现有管线是否可复用，不要复制一份新的**。

## 加一种新伤害

1. 伤害类型：在 `ModDamageTypes` 注册（如果需要对死亡消息/数据包区分）。
2. 组装：在调用点用 `ModDamageSpec` 描述倍率与元素（技能类里通常已有类似写法，照抄）。
3. 管线：如果只是普通直伤或已有反应类型，**不需要新管线**；确认要走专门管线时，在 `attack/` 下加实现并让调用点走它。
4. 表现：飘字的颜色/样式一般按元素与反应自动取，需要自定义时改 `damage-indicator.toml` 或 `DamageIndicatorFactory` 的输入。
5. 衰减：需要参与附着冷却的，在 `DecayGroups` 里登记分组。

## 常见坑

| 现象 | 原因 |
|---|---|
| 伤害数字对了但血没掉 | 被护盾、无敌帧或**元素免疫**拦下；免疫只把伤害归零，附着与反应照常发生（见「元素载体：可附着宿主」） |
| 元素挂上了但完全不掉血 | 目标对该元素免疫（`isImmuneToElementDamage`，元素生物按主元素比较）；想连附着一起拒掉要用 `onAttachElement` |
| 反应不触发 | 目标身上没有附着、衰减计数器已把附着消耗完，或宿主拒收了这次附着（拒收 = 不反应） |
| 飘字位置偏移 | `DamageIndicatorFactory` 的落点使用攻击者/目标位置，召唤物场景要传对来源 |
| 方块打不出元素 | `ElementalAttackSweep` 的三道门禁依次是：只在服务端、**只在原神模式**、角色元素非空（物理系不附着）；此外方块还要在 `BlockElementRules` 里登记过才会收这个元素，且必须落在这一招的攻击距离内。非原神模式、物理系角色、未登记方块都不会有元素 |
| 技能自己注册了攻击监听 | 违反项目约定，应改为在动作关键 tick 或 attack 系统入口里处理 |
| 专用服务器崩溃 | `core`/`content` 侧引用了客户端类（飘字是唯一允许跨到客户端的地方，且必须走 RPC） |

## 26.2.0.2 变更：攻击统一入口

新增 `ElibAttackPipeline`（elementlib），一次「攻击动作」包含对空、方块左键、实体左键与动作伤害点：

- `ActionState.fireDamagePoint` 每个伤害点 dispatch 一次（方块附着走这里，替代原 `ElementalAttackSweep` 调用）；
- 方块左键来自 `BlockStateAttackMixin` 注入的 `BlockBehaviour.BlockStateBase#attack`，每次有效交互一次；
- 实体附着仍在 `DirectDamagePipeline`，但容器已走模块层；
- 本模组通过 `ElementLibBridge` 注入「仅原神模式」门禁，并关掉 lib 的默认桥与实体附着。

详见 `docs/systems/module-system.md`。
