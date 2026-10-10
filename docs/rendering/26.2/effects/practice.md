# 7. 实战：把渲染接到 Photon


## 7.1 「什么时候召唤粒子」只有四种时机

Photon 的播放模型是「**逐帧请求 / 显式生命周期**」，所以接到游戏里的方式也是有限的几种。
下表是这四种以及各自的正确做法：

| # | 时机 | 锚点 | 用什么 | 生命周期由谁管 |
|---|---|---|---|---|
| 1 | **常驻跟随角色** | 实体 | `EntityEffectExecutor` | 你每 tick 判断条件，条件不成立就 `destroy` |
| 2 | **常驻跟随手上的武器** | 武器骨骼 | 自定义 `IEffectExecutor` + 骨骼缓存 | 同上 |
| 3 | **仅在攻击动画期间** | 武器尖 | 同上 + 动作状态机门禁 | 状态机说了算 |
| 4 | **技能后定点生成、朝前飞** | 固定世界坐标 | 一次性 Executor（自己推进位置） | 自己走完或到时刻自毁 |

四者的共同点：**都要有一个「锚点」，而且锚点必须是客户端可得的**。
差别只在锚点从哪来：实体位置、骨骼位姿、还是一个写死的世界坐标。

## 7.2 用 `EntityEffectExecutor` 做常驻跟随（最简单的一档）

```java
EntityEffectExecutor executor = new EntityEffectExecutor(
        fx, level, player, EntityEffectExecutor.AutoRotate.NONE);
executor.setOffset(0, 0, 0);      // 相对眼睛位置
executor.setForcedDeath(true);    // 锚点没了立刻清残留
executor.setAllowMulti(true);     // 去重自己管，避免 start() 被静默跳过
executor.start();
```

它已经在做三件你不用自己写的事：

1. root 每帧跟随**插值后的眼睛位置**；
2. 实体死亡时结束效果；
3. `allowMulti=false` 时按 FX 去重，避免同一个实体叠一堆同样的效果。

**但有一件事它不会做：重建。** 切世界、`/photon_client clear_particles` 之后，
粒子引擎会把粒子直接丢掉，Runtime 根本没机会走到 `isFinished()`。
所以缓存实例时必须这样判断：

```java
FXRuntime runtime = executor == null ? null : executor.getRuntime();
if (runtime != null && runtime.isValid() && !runtime.isFinished()) {
    return;                       // 还活着，什么都不用做
}
// 否则重建
```

`isValid()` 是**缓存 Runtime 时唯一正确的存活检查**（O(1)，可以每 tick 调），
它用 host generation + root heartbeat 检测「粒子被引擎丢了」这种情况。

## 7.3 跟随武器：骨骼 → 世界坐标 → Photon root

这是整个链路里唯一有技术含量的一环，完整数据流如下：

```
GeckoLib 渲染趟（渲染线程，每帧）
  └─ WeaponAnchorGeoLayer.addPerBoneRender(...)
       └─ PerBoneRender 回调：
            Matrix4f bonePose = poseStack.last().pose();   ← 相机相对
            Vector3f local    = bonePose.getTranslation();
            Vec3     world    = cameraState().pos + local;  ← 加回相机位置
            WeaponAnchorCache.put(player, world, rotation); ← 按「渲染帧」打时间戳

Photon 帧回调（同一线程，每帧）
  └─ IEffectExecutor#updateFXObjectFrame(object, partialTicks)
       └─ 从缓存取出世界位姿 → object.updatePos(...) / updateRotation(...)
```

四个必须注意的点：

1. **只动 root**。`FXRuntime` 有一个恒存在的空 root（`FXRuntime.ROOT_UUID`），
   动它等于动整棵树；去动子对象会和 Authored 层级互相打架。
2. **缓存要按渲染帧过期**，不能按实体刻。角色在第一人称、离屏、被别的模组挡住时根本不渲染，
   这时候应该**保持上一帧的位姿**，而不是把特效瞬移到世界原点。
3. **坐标系假设要加保险**。本项目用「相机相对 PoseStack + `CameraRenderState.pos`」这条公式，
   代码里加了一道自检：结果离玩家超过若干格就整帧丢弃。坐标系假设被将来改动打破时，
   宁可特效停在上一帧，也不要它飞到天边。
4. **PoseStack 是渲染瞬间的快照**。骨骼位置不是实体状态，是「动画采样 + 层级变换累乘」的结果，
   只有在渲染那一瞬间才成立 —— 这也是为什么必须借渲染层来抓。

**武器尖**：骨骼原点在握把附近，尖在骨骼局部轴向上。用骨骼自己的世界旋转去换算这个偏移，
挥砍时特效才会跟着刀尖走：

```java
Vector3f tip = rotation.transform(new Vector3f(0f, TIP_OFFSET, 0f));
Vector3f at  = new Vector3f(boneWorldPos).add(tip);
```

⚠️ 不同武器的「尖」方向不一样（`+Y` / `-Y` / `+Z`），偏移长度也不同。
这两个常数必须**按实际武器模型调**，没有通用值。

## 7.4 用动作状态机做「只在攻击时生效」

客户端的动作状态是**公开可读**的静态字段：

```java
String state = ActionStateMachine.currentState;   // 例："shenhe_attack_1"
```

门禁写法建议**双保险**（精确名单 + 宽松兜底），这样将来动画名改了不会静默失效：

```java
public static boolean inAttackAnimation() {
    String state = ActionStateMachine.currentState;
    if (state == null) return false;
    return SPECIAL_ANIMS.contains(state) || state.contains("attack");
}
```

**不要**在攻击动作里直接 `start()` 一个特效然后不管 —— 动作会被打断、会连招、会中途取消，
必须每 tick 用状态机重新求值「现在该不该亮」，让特效跟着状态走。

## 7.5 定点生成 + 朝前飞

```java
Vec3 origin = player.position().add(look.x * 1.0, 0.3, look.z * 1.0);
new FixedPointExecutor(level, origin, player.getYRot(), speed)
        .lifetime(60)
        .start(fx);
```

位置推进的正确写法是**在 tick 里推进、在 frame 里插值**：

```
tick  ：origin += forward × speed                              （推进一个整刻）
frame ：pos = origin + forward × speed × partialTicks          （刻内插值，不抖）
```

**两个回调都写位置会互相打架**（一个按整刻跳、一个按帧插值，加起来就是抖动）。

速度传 `0` 就是「位移完全交给特效自己做」（编辑器里给 Emitter 开 `WORLD` 模拟空间 + Velocity over Lifetime）——
两种做法都成立，看特效作者的习惯。

## 7.6 从服务端触发

特效是客户端的，但「什么时候放」通常由服务端逻辑决定。好消息是 **Photon 已经自带两个网络包**：
`/photon fx` 命令走的就是它们。

```java
var command = new EntityEffectCommand();
command.setLocation(Identifier.fromNamespaceAndPath("mymod", "slash_trail"));
command.setEntities(List.of(target));
command.setOffset(new Vec3(0, 0.6, 0));
command.setAutoRotate(EntityEffectExecutor.AutoRotate.LOOK);
command.setForcedDeath(true);
PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, command);
```

方块版把 `EntityEffectCommand` 换成 `BlockEffectCommand`，用 `setPos(BlockPos)`，
并按区块发送（`PacketDistributor.sendToPlayersTrackingChunk(level, chunkPos, command)`）。

包在客户端收到后会自己完成：`FXHelper.getFX(...)` → 建 Executor → 应用 offset/rotation/scale/delay → `start()`。

**什么时候才需要自己写包**：需要在播放**之前**注入运行时参数、需要在客户端做条件判断、
或者要用自定义 Executor / 独立 PostEffectStack。这时自己写一个 `CustomPacketPayload`，
在客户端 handler 里 `ctx.enqueueWork(...)` 回到主线程再操作 Photon。

## 7.7 把游戏状态喂给 shader

两条路，按「谁来写」选：

| 需求 | 用什么 |
|---|---|
| 值由 Timeline 或 Java 每帧驱动 | **Custom Data Stream**（每发射器最多 4 条，每条 `vec4`） |
| 一次性/低频的材质级参数 | **Shader Graph 暴露的参数** / Custom Shader Material 的 uniform |

Custom Data 的运行时覆写接口（发射器 Runtime 上）：

```java
values.customData.slot(stream, channel).set(new Constant(0.85f));
values.customData.slot(stream, channel).clear();     // 回到 Authored 值
```

⚠️ **Stream 的 Index 就是编辑器里的列表顺序**。改显示名不影响 GPU 布局，
但**增删或移动 Stream 会改变后续所有 Index** —— 这时候 shader 里的 `Index` 也得跟着改。

⚠️ **Timeline 与 Java 抢同一个槽时，后写的赢**。Timeline 每 tick/每帧都在写，
所以 Java 一次性 `set()` 常常「看起来没生效」。解法：换一个槽、持续更新，或让一个系统独占该值。

---
