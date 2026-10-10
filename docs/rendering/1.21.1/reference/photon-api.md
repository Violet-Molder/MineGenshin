# 9. Photon2 Java API 与运行时注入


## 9.1 三件套：`FX` / `FXRuntime` / `IEffectExecutor`

```text
FX              一份 .fx 工程（静态定义）
FXRuntime       一次播放实例（有生命周期、有 root）
IEffectExecutor 播放上下文（挂在谁身上、每刻/每帧怎么更新）
```

```java
FX fx = FXHelper.getFX(ResourceLocation.fromNamespaceAndPath("minegenshin", "skill_burst"));
var executor = new EntityEffectExecutor(fx, level, player, EntityEffectExecutor.AutoRotate.LOOK);
executor.setOffset(0, 1.0, 0);
executor.setRotation(0, 0, 0);   // 度
executor.setScale(1, 1, 1);
executor.start();
```

`IFXEffectExecutor`（`client/fx/IFXEffectExecutor.java`）负责变换 / 延迟 / 开关，
`IEffectExecutor`（`client/fx/IEffectExecutor.java`）负责每刻与每帧回调：

| 回调 | 频率 | 该写什么 |
| --- | --- | --- |
| `updateFXObjectTick(IFXObject)` | 每刻 | 低频逻辑：锚点没了就销毁、状态切换 |
| `updateFXObjectFrame(IFXObject, float)` | 每帧 | 高频逻辑：跟随位置 / 旋转 / 缩放 |

**两个回调都写位置会互相打架**（一个按整刻跳、一个按帧插值，加起来就是抖动）。

## 9.2 内置执行器

| 类 | 用途 |
| --- | --- |
| `EntityEffectExecutor` | 跟随实体（`AutoRotate`: `NONE` / `FORWARD` / `LOOK` / `XROT`） |
| `BlockEffectExecutor` | 跟随方块位置 |
| `FXEffectExecutor` | 其它执行器的基类 |

## 9.3 时间轴信号

时间轴的 `signal` 轨道会回调到执行器：`IEffectExecutor#onTimelineSignal(channel, name, data, time)`。
全局订阅用 `client/fx/timeline/PhotonSignals.java`。这是一条「特效反过来驱动游戏逻辑」的通道：
特效播到某一刻，通知代码做一件事（放音效、加 buff、切状态）。

## 9.4 扩展点

`PhotonRegistries`（`PhotonRegistries.java`）：

| 注册表 | 类型 | 说明 |
| --- | --- | --- |
| `photon:fx_object` | `FXObjectType` | 自定义 FX 对象类型 |
| `photon:material` | `IMaterial` | 自定义材质 |
| `photon:number_function` | `NumberFunction` | 曲线 / 表达式函数 |
| `photon:shape` | `IShape` | 发射形状 |
| `photon:model_source` | `IModelSource` | 模型源 |
| `photon:timeline_track` | `TrackType` | 时间轴轨道类型 |
| `photon:animated_property` | `AnimatedPropertyType` | 可动画属性 |

注册用 LDLib2 的注解，例如
`@LDLRegisterClient(name = "json_model", registry = "photon:model_source")`。

## 9.5 生命周期

- `FXRuntime#isValid()` 决定实例该不该活；
- 存活判据必须是**单调递增**的 tick 计数 —— 不要用 `PhotonParticleManager` 的
  `time`（时间轴时钟，`clear()` 会重置）；
- 关卡卸载 / 资源重载：`FXHelper.clearCache()`（`:43`）+ `/photon clear client cache fx`；
- 编辑器里的预览用的是隔离的执行器与场景栈，不要拿它当运行时用法。

---
