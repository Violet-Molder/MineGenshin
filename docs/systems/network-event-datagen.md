# 网络、事件与数据生成

## 网络

本项目的网络层基于 LowDragLib2 的 RPC（`@RPCPacket` / `RPCPacketDistributor` / `RPCSender`），而不是原版自定义包。

| 类 | 位置 | 方向 | 职责 |
|---|---|---|---|
| `NetworkManager` | `core/network/` | 服务端 → 客户端 / 客户端 → 服务端 | 对外统一入口：`set...ToPlayer` 下发数据，`...ToServer` 上报输入 |
| `ClientHandler` | `core/network/` | 客户端 | 接收服务端数据后写入本地附件/状态 |
| `ActionServer` | `core/network/` | 客户端 → 服务端 | 动作请求（攻击、切人、技能）的入口 |
| `DamageIndicatorRpc` | `core/network/` | 服务端 → 客户端 | 伤害飘字（纯表现单向下发），另含方块位置飘字 |

### 一条同步链的写法

1. 服务端在数据变更后调用 `NetworkManager.setXxxToPlayer(player, value)`。
2. 客户端 `ClientHandler` 收到后写进自己的附件，界面/渲染读附件即可。
3. 反向输入（按键、请求）走 `...ToServer`，服务端在 handler 里做校验与状态变更。
4. 关键时机（登录、死亡重生）必须补一次全量下发——参见"附件与数据同步"。

**纪律**：公共侧代码不得引用客户端类。需要触达客户端表现时，走 `api/` 下的契约：
新增 `api/damage/DamageIndicatorData`（数据）与 `api/damage/DamageIndicatorSink`（注入点），公共侧只填数据并调用 `show(...)`，
客户端在 `MinegenshinClient.onClientSetup` 安装实现（`client/damage/DamageIndicatorClientBridge`）。
专用服务器上没有实现，调用即空操作——这是"公共侧不加载客户端类"的标准写法。

## 事件与钩子

### 事件网络（26.2.0.5 起）

「发生了什么」的统一广播走 **NeoForge 的事件总线**（`NeoForge.EVENT_BUS`），事件类定义在
`elementlib` 的 `api/event/`（元素与攻击）与 `minegenshin` 的 `event/game/`（角色、队伍、伤害、技能释放）。
广播统一走 `ElibEvents.post(...)`（它兜住监听者异常 —— NeoForge 的 `EventBus` 会记日志后**重抛**）；
监听者在 `GenshinEvents.init()` 里登记，那个类就是「谁在听什么」的唯一清单。

> **什么该订阅、什么该内部驱动**：普通攻击产球**不是**监听者 —— 它是伤害结算自身的一部分，
> 由伤害管线在确认掉血的那一步直接调用 `NormalAttackOrbProducer#tryProduce`，
> 与「造成实际伤害」事件的广播同一时刻、同一级别。判断标准：这件事是「这件事的一部分」，
> 还是「知道这件事之后另外做点什么」。前者内部驱动，后者才订阅。

| 事件 | id | 触发点 |
|---|---|---|
| 元素附着成功 | `elementlib:element_attached` | `ElementalAttachmentHelper.doAttach`（写入后、反应前） |
| 元素反应发生 | `elementlib:element_reacted` | `ElementalReactionManager.runReactions`（每个成立的反应） |
| 攻击行为 | `elementlib:attack_performed` | `ElibAttackPipeline`（每次出手，含对空；内部子步骤不发） |
| 击中目标 | `elementlib:attack_hit` | `ElibAttackPipeline`（每个被触及的宿主，实体与方块都算） |
| 伤害结算完成 | `minegenshin:damage_calculated` | `LivingEntityHurtMixin`（扣血前；区分过盾 / 免疫 / 钩子取消） |
| 造成实际伤害 | `minegenshin:damage_dealt` | `LivingEntityHurtMixin`（扣血后；血量真的变小才发） |
| 切人 / 登场 / 退场 | `minegenshin:character_switched` 等 | `PlayerCharactersAttachment.switchTo`（改索引的唯一入口） |
| 入队 / 退队 | `minegenshin:party_member_joined` 等 | `PlayerCharactersAttachment.setPartyCharacter` / `removePartyCharacter` |
| 释放战技 / 爆发 | `minegenshin:elemental_skill_cast` 等 | `ActionManager.requestElementalSkill` / `requestElementalBurst` 成功分支 |

事件是**只读通知**，不提供取消：要拦行为走宿主筛查、`BlockEvent.BreakEvent`、`ControlService` 这些既有裁决通道。

### 归属规则（三选一）

| 情况 | 放哪 |
|---|---|
| 跨模块的通用分发入口 | `event/` 下，方法体只做校验与委派（`PlayerLoginEventListeners`；事件网络的监听者清单是 `event/game/GenshinEvents`） |
| 只服务单一模块 | 该模块包内，命名 `XxxHandler`（如 `CharacterEffectHandler`、`ShieldTickHandler`） |
| 客户端专有表现 | `client/` 或 `render/` 内，`@EventBusSubscriber(value = Dist.CLIENT)` |

### 多监听点是允许的

同一事件可以有多个监听点，只要满足其一：单个入口会臃肿；分摊给各模块自己的关注点；若干类分担同一模块内相似职责。真正要合并的只有：同一目标重复注册、同一职责两处各写一遍、Mixin 与事件处理器做同一件事。

### 当前监听点分布（便于定位）

| 事件 | 监听点 |
|---|---|
| `PlayerTickEvent.Post` | `CharacterEffectHandler`（效果）、`CharacterTickHandler`（角色+同步+索敌，顺序敏感不拆）、`DashSystem`、`ServerAnimationTicker` |
| `EntityTickEvent.Post` | `StatusTickHandler`、`CharacterChillHandler`、`ShieldTickHandler`、`CombatTimerHandler` |
| `ClientTickEvent.Post` | `DamageIndicatorRenderer`、`KeyInputHandler`、`KeyMappingRegistry`、`ActionStateMachine`、`KeyInterceptionHandler` |
| `RegisterGuiLayersEvent` | `DamageIndicatorHudRegistration` 与各 HUD 各自注册 |
| `LivingIncomingDamageEvent` | `CombatTimerHandler`（重置战斗计时）、`CompatEventHandler`（兼容处理）；「挨打是否打断动作」不在这条链上，它走命中入口 `ControlService.onHit` |

### 加一个监听

1. 先按上表判定归属，再决定放 `event/` 还是模块包。
2. 用 `@EventBusSubscriber`（classpath 扫描，无需手工注册）；客户端专有的必须带 `Dist.CLIENT`。
3. 攻击相关的一律并入 attack 系统，不要自己注册攻击监听。
4. 需要控制执行顺序时注意：**跨类的监听顺序是不确定的**，有顺序依赖的逻辑要放在同一个方法里按顺序写。

## Mixin

| 位置 | 内容 |
|---|---|
| `mixin/mixins/` | 注入实现：`LivingEntityElementalMixin`（元素附着）、`LivingEntityHurtMixin`（伤害接管）、`PlayerAttackInterceptor`（攻击入口）、`BlockEntityElementalMixin`、`DamageContainerMixin`、`AccessorRangedAttribute` 等 |
| `mixin/interfaces/` | 注入接口（如怪物等级 `IMonsterLevel`、Teyvat 标记） |
| `mixin/MixinConfig.java` | Mixin 插件配置（`minegenshin.mixins.json` 的 `plugin`） |
| `src/main/resources/minegenshin.mixins.json` | 清单：`package`、`mixins`、`client`、`injectors` |

**移动或改名 Mixin 类必须同步改 `minegenshin.mixins.json`**，否则静默不注入（不报错，只是行为消失）。客户端 Mixin 必须列在 `client` 数组里。

## 数据生成

| 类 | 位置 | 产出 |
|---|---|---|
| `DataGenerators` | `data/generators/` | 汇总入口，挂在 `GatherDataEvent` |
| `ModModeProvider` | 同上 | 物品模型与物品定义（含 geo 物品的特殊模型） |
| `ModEntityLootSubProvider` | 同上 | 实体战利品表 |

产物写入 `src/generated/resources/`，被 `sourceSets.main.resources` 纳入；**这是构建产物，不要手改**，改源数据后跑 `./gradlew runData` 重新生成。

## 常见坑

| 现象 | 原因 |
|---|---|
| 客户端收不到数据 | 只改了本地附件没下发；或下发对象不是 `ServerPlayer` |
| 重连后状态回到默认 | 登录流程没有补全量同步 |
| Mixin 行为消失 | 类被移动/改名但 `minegenshin.mixins.json` 没同步 |
| 生成物被覆盖 | 手改了 `src/generated/` 下的文件，下次 `runData` 会覆盖 |
| 监听顺序错乱 | 依赖了跨类监听的执行顺序（应当合并到同一个方法内保证顺序） |
| 专用服务器崩溃 | 公共侧代码里 import 了客户端类（网络与事件层最容易犯） |
