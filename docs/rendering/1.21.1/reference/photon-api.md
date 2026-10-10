# 9. Photon2 Java API 与运行时注入

上一章讲「是什么」，这一章讲「代码里怎么写」：怎么把一份 `.fx` 播起来、怎么让它跟着东西走、
怎么从服务端触发、怎么把游戏状态喂进去、怎么正确结束。

## 9.1 三件套的分工

```text
FX              静态定义（文件）
FXRuntime       一次播放实例：有 root、有存活判定
IEffectExecutor 播放上下文：挂在哪、每刻/每帧怎么更新
```

启动一次播放的标准写法：

```java
FX fx = FXHelper.getFX(ResourceLocation.fromNamespaceAndPath("minegenshin", "skill_burst"));
EntityEffectExecutor executor = new EntityEffectExecutor(fx, level, player,
        EntityEffectExecutor.AutoRotate.LOOK);
executor.setOffset(0, 1.0, 0);       // 相对锚点偏移（格）
executor.setRotation(0, 0, 0);       // 度
executor.setScale(1, 1, 1);
executor.start();                    // 建 FXRuntime 并 emit
```

## 9.2 两种回调：tick 与 frame

`IEffectExecutor`（`client/fx/IEffectExecutor.java`）只有两个必须理解的钩子：

| 回调 | 频率 | 该写什么 | 不该写什么 |
| --- | --- | --- | --- |
| `updateFXObjectTick(IFXObject)` | 每刻（20/s） | 低频逻辑：锚点没了就销毁、状态切换 | 逐帧插值 |
| `updateFXObjectFrame(IFXObject, float)` | 每帧 | 位置 / 旋转 / 缩放跟随 | 逻辑判断、查表 |

**两个回调都写位置会互相打架**：tick 按整刻跳，frame 又按 `partialTick` 插值，
两个值叠加起来就是抖动。规则：**tick 推进状态，frame 只做插值呈现**。

## 9.3 内置执行器

| 执行器 | 锚点 | 备注 |
| --- | --- | --- |
| `EntityEffectExecutor` | 实体 | `AutoRotate`：`NONE` / `FORWARD` / `LOOK` / `XROT`；实体死时自动销毁 |
| `BlockEffectExecutor` | 方块 | 固定坐标 |
| `FXEffectExecutor` | — | 上面两个的基类：管 `runtime`、`start()`、变换参数 |

自己写执行器时，直接实现 `IEffectExecutor` 最省事（见 `FixedPointExecutor`）。

## 9.4 从服务端触发

服务端**不能**碰 `FX` / `FXHelper`（全是 `@OnlyIn(Dist.CLIENT)`）。做法：

```java
// 服务端：只发意图
PacketDistributor.sendToPlayersTrackingEntity(entity, new PlayFxPayload(fxId, offset, rotation));
// 客户端：收到后构造 FX 与执行器
```

本项目的 RPC 走 LDLib2 的网络层，具体包名见「网络、事件与数据生成」那一篇。

## 9.5 把游戏状态喂给特效

1.21.1 上没有 RenderState 票据，能用的通道有三条：

| 通道 | 写法 | 适合 |
| --- | --- | --- |
| 执行器每帧改根变换 | `runtime.root.updatePos(...)` | 跟随、朝向 |
| 时间轴信号 | `IEffectExecutor#onTimelineSignal(channel, name, data, time)` | 「播到第 N 刻做一件事」 |
| 自定义材质 / 着色器参数 | 在 `IMaterial` 里读 `MaterialContext` | 颜色、强度这类连续量 |

全局信号订阅走 `client/fx/timeline/PhotonSignals.java`。

## 9.6 生命周期：什么时候该结束

- `FXRuntime#isValid()` 决定实例还该不该活；**存活判据必须是单调递增的 tick 计数**，
  不要用时间轴时钟（`clear()` 会把它重置）。
- 实体没了 / 退世界 / 资源重载：实体执行器会自动 `retire`，但你的自定义执行器要自己处理
  `LevelEvent.Unload` 与资源重载（`FXHelper.clearCache()`）。
- `remove(true)` 会立刻清掉残留粒子，`remove(false)` 让它自然消散 —— 看特效设计。

## 9.7 扩展点

`PhotonRegistries`（`PhotonRegistries.java`）把所有可扩展点摊开：

| 注册表 | 类型 | 扩展什么 |
| --- | --- | --- |
| `photon:fx_object` | `FXObjectType` | 自定义 FX 对象 |
| `photon:material` | `IMaterial` | 自定义材质 |
| `photon:number_function` | `NumberFunction` | 曲线 / 表达式 |
| `photon:shape` | `IShape` | 发射形状 |
| `photon:model_source` | `IModelSource` | 模型源（含动态网格注入） |
| `photon:timeline_track` | `TrackType` | 时间轴轨道 |
| `photon:animated_property` | `AnimatedPropertyType` | 可动画属性 |

注册用 LDLib2 的注解，例如 `@LDLRegisterClient(name = "json_model", registry = "photon:model_source")`。

## 9.8 一个最小自定义执行器

```java
public final class FixedPointExecutor implements IEffectExecutor {
    private final Level level;
    private final Vec3 forward;
    private final double speed;
    private Vec3 origin;          // tick 起点
    private FXRuntime runtime;

    @Override public Level getLevel() { return level; }

    @Override public void updateFXObjectTick(IFXObject fxObject) {
        if (runtime == null || fxObject != runtime.root) return;
        origin = origin.add(forward.scale(speed));      // 只在 tick 推进
    }

    @Override public void updateFXObjectFrame(IFXObject fxObject, float partialTick) {
        if (runtime == null || fxObject != runtime.root) return;
        Vec3 pos = origin.add(forward.scale(speed * partialTick));   // 只在 frame 插值
        fxObject.updatePos(new Vector3f((float) pos.x, (float) pos.y, (float) pos.z));
    }
}
```

这就是项目里 `client/fx/FixedPointExecutor` 的骨架：**位移由代码给、特效里把模拟空间设成 `WORLD`**；
反过来把位移全写在特效里、`speed` 传 0 也成立。

## 9.9 排错

| 症状 | 原因 |
| --- | --- |
| 特效抖 | tick 与 frame 都写了位置 |
| 特效跟着相机乱转 | 用了 `LOOK` 但锚点其实是世界坐标 |
| 退出世界后还在播 | 没处理 `LevelEvent.Unload`；或存活判据用了时间轴时钟 |
| 服务端崩在 `NoClassDefFoundError` | 服务端碰了 `client` 包 |
| 资源重载后特效变样 | 缓存了旧的 `FX` 实例（要用 `FXHelper.getFX`） |

下一章：[10. 项目实战](/doc/rendering-1.21.1-reference-practice)。