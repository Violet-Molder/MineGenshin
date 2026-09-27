# 战斗 · 动作与动画

## 这个系统负责什么

把"按下一个键"变成"角色做出动作、在正确的帧产生伤害或特效"。包括动作定义（步骤、时长、伤害点、可打断窗口）、状态机推进、打断规则、以及动作与 GeckoLib 动画的绑定。

## 三个概念

| 概念 | 类 | 说明 |
|---|---|---|
| 动作定义 | `action/ActionDefinition`、`action/ActionSet`、`action/ActionKind` | 一次动作由若干 `ActionStep` 组成，声明总时长、伤害点、保护窗口 |
| 动作运行时 | `action/ActionState` | 按 tick 推进：递增计数器 → 到 `hitDelay` 触发 `onActiveStart` → 到达 `totalDuration` 结束 |
| 动作管理 | `action/ActionManager` | per-player、per-side 的管理器（键为 `C:UUID` / `S:UUID`，单机同 JVM 用 side 区分），负责排队、打断、驱动当前动作 |

`ActionState` 的三个窗口：

```
0          prepareTicks        protectDuration        duration
├─ 准备期 ───┼──── 执行期 ────────────┼──── 后摇 ────┤
   可打断          不可打断                可取消
```

`onCastStart` 在构造时（tick 0）触发一次——"触发即生效"的逻辑放这里，而不是等第一个 tick。

## 服务端与客户端各自做什么

| 侧 | 类 | 职责 |
|---|---|---|
| 服务端 | `action/ActionManager`、`action/ServerActionExecutor`、`action/ServerTickScheduler` | 权威的动作推进、位移、伤害与结算 |
| 服务端 | `core/system/control/Controllable#interruptAction` | 动作被打断的统一落点：命中经 `ControlService.onHit` 判断后调它，玩家侧转 `ActionManager.interrupt(InterruptReason.DAMAGE)` |
| 客户端 | `client/combat/AttackApproach`、`client/combat/BurstDive` | 输入手感与本地表现（突进、落点） |
| 客户端 | `animation/state/ActionStateMachine`、`PlayerAnimationController` | 客户端动作状态机与动画驱动（**当前仍在公共包，属待整理项**） |
| 客户端 | `client/combat/action/ResourceDrivenActionHandler`、`CharacterAnimationRegistry` | 由资源数据驱动的动作处理（实现与登记表都在客户端） |
| 公共 | `core/system/combat/animation/action/CharacterActions`、`CharacterActionHandler` | 契约与查表，双端可用 |

客户端与服务端各有一份状态机，靠 RPC 对齐（攻击请求包、动作同步包）。**两边不一致的典型后果**：动作打不出伤害、位移对不上、索敌锁不释放。

## 动画是怎么绑上去的

1. 角色包内声明动作资源（如 `VesnaAnimations`、`VesnaResources.ACTION_DATA`）。
2. `animation/config/CharacterAnimations`、`LocomotionAnims`、`FirstPersonAnims` 把动作映射到 GeckoLib 动画键。
3. `core/system/combat/animation/action/CharacterActionHandler`（接口）与 `client/combat/action/ResourceDrivenActionHandler`（通用实现）在动作进入某步骤时切换到对应动画。
4. 模型与动画文件布局：`assets/minegenshin/character/<角色id>/`（实体资源是 `entity/<id>/`）。
5. 动画键名与 `.animation.json` 中的名字不一致时，`CategoryGeoModel` 会在日志里给出可读的排查提示。

## 按键到动作

1. `client/keybindings/KeyMappingRegistry` 注册按键；普攻/战技/闪避/爆发在 `setDown` 里 **0 延迟触发**（不等 tick）。
2. 触发即发攻击请求包 → 服务端 `ActionManager` 排动作。
3. 其余按键（切人、模式、界面）在 `client/keybindings/KeyInputHandler` 的 `ClientTickEvent.Post` 里做边沿检测。
4. 眩晕状态下由 `KeyInterceptionHandler` 抑制按键。

## 加一个新动作

1. 数据：在角色/武器的动作数据里加 `ActionDefinition`（步骤、时长、伤害点、保护窗口）。
2. 动画：在对应的动画配置里挂动画键，资源文件补动画。
3. 数值：伤害点里引用 `ModDamageSpec`（倍率、元素）。
4. 绑定按键：已有四键通常复用 `ActionKind`，不需要新按键。
5. 打断策略：需要特殊打断规则时改 `InterruptReason` 的判定，而不是在动作里写 if。

## 常见坑

| 现象 | 原因 |
|---|---|
| 动作卡住不结束 | 状态机没有推进（例如索敌锁未释放导致 `isLocked` 一直为真） |
| 服务端不动、客户端动了 | 双端状态机不同步，检查攻击请求包与动作同步 |
| 伤害在错误的帧出现 | `hitDelay` 与动画关键帧对不上（动画时长改过但数据没改） |
| 后摇无法取消 | `protectDuration` 覆盖了整个动作，检查三个窗口的数值 |
| 动画不播 | 动画键名与 `.animation.json` 不一致，或资源目录不在 `character/<id>/` |
| 拆包后动作互相打断 | 一个 `ActionManager` 管多个队伍角色时没按"当前出战角色"过滤 |
