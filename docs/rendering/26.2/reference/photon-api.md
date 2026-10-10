# 9. Photon2 Java API 与运行时注入


## 9.1 两条路线：内置 Executor 与自定义 Executor

在游戏里播放一个 FX，只有两种做法：

1. **用内置 Executor**：`BlockEffectExecutor`（绑方块）或 `EntityEffectExecutor`（绑实体）。
   它们负责"跟谁、跟哪儿、什么时候销毁"，你只管配置 offset/rotation/scale/delay；
2. **自己实现 `IEffectExecutor`**：当锚点不是方块也不是实体（骨骼、屏幕坐标、自算轨迹）时走这条。
   它只有三个必须回答的问题：`getLevel()` 给谁、每 tick 干什么、每帧干什么。

无论哪条路，最终都会落到 `FXRuntime`：`fx.createRuntime()` → `runtime.emit(executor)` →
引擎每 tick / 每帧回调 → `runtime.destroy(force)`。

## 9.2 加载：FXHelper

`com.lowdragmc.photon.client.fx.FXHelper`（客户端类）：

| 方法 | 语义 |
|---|---|
| `getFX(Identifier)` | 读 `assets/<ns>/fx/<path>.fx` 并缓存；**失败返回 `null`** |
| `getFX(Identifier, boolean useCache)` | `useCache=false` 时绕过缓存直接重读 |
| `listAllFX()` | 列出所有可加载 id（模组 jar + 资源包 + 已挂载 `.fxpack`），结果缓存 |
| `clearCache()` | 清空定义缓存与 id 列表缓存；返回清掉的条目数 |

id 的写法是 `命名空间:路径`，路径**不带** `fx/` 前缀和 `.fx` 后缀：
`minegenshin:character/test/aura_body` → `assets/minegenshin/fx/character/test/aura_body.fx`。
`getFX` 返回 `null` 是"正常结果"而不是异常——资源没做出来时整条链路应当静默空转
（本仓库的 `TestCharacterFx` 就是这么处理的，见 §10）。

## 9.3 内置 Executor：BlockEffectExecutor 与 EntityEffectExecutor

两者都继承 `FXEffectExecutor`（`client/fx/FXEffectExecutor.java`），共用一份配置面与一套去重/回收逻辑。

**`BlockEffectExecutor`**（`client/fx/BlockEffectExecutor.java`）：

- 绑在方块坐标上：Root 位置 = 方块坐标 `+ (0.5, 0.5, 0.5)`，再叠加配置的 offset；
- 有静态缓存 `CACHE: Map<BlockPos, List<BlockEffectExecutor>>`；
- 每 tick 检查锚点：区块没加载、方块种类变了、或（`checkState=true` 时）**精确的 BlockState 变了**
  → `runtime.destroy(forcedDeath)` 并立刻从缓存退休（`retire`）；
- 反过来，运行结束的实例也会自己从缓存里摘掉，不会一直挂到下一次同位置启动。

**`EntityEffectExecutor`**（`client/fx/EntityEffectExecutor.java`）：

- 绑在实体上：Root 每帧跟 `entity.getEyePosition(partialTicks)`（眼睛位置）走，加 offset；
- 构造时选 `AutoRotate`：`NONE`（只用配置的 rotation）、`FORWARD`（实体前向）、
  `LOOK`（视线方向）、`XROT`（按可视身体朝向绕 Y，注意源码里还带了 `-90 - visualRotationY` 的补偿）；
- 每 tick 检查 `entity.isAlive()`：死了就 `destroy(forcedDeath)` + 立刻退休；
- 缓存 `CACHE: Map<Entity, List<EntityEffectExecutor>>`。

**去重规则（`FXEffectExecutor.shouldSkipStart`）**：当 `allowMulti = false`（默认）时，
如果同一锚点上已经有一个**同一个 FX 实例或同一个 fx location** 的执行器还在跑，本次 `start()` 直接跳过。
所以"我自己管理生命周期"的代码要显式 `setAllowMulti(true)`，否则会被静默跳过——
本仓库的 `TestCharacterFx.tickBody` 就是先自己判重、再开 `allowMulti`（`TestCharacterFx.java:213-245`）。

**回收与通知**：`FXEffectExecutor` 提供 `setOnFinished(Consumer<FXRuntime>)`，在
"自然播完 / 被销毁 / 被粒子引擎丢弃"时**恰好触发一次**；
`runtimeEnded()` 的判据是 `runtime.isFinished() || !runtime.isValid()`。

## 9.4 配置面：offset / rotation / scale / delay / forcedDeath / allowMulti

`IFXEffectExecutor`（`client/fx/IFXEffectExecutor.java`）给出的配置入口：

| 方法 | 单位 / 语义 |
|---|---|
| `setOffset(double x, double y, double z)` / `setOffset(Vector3f)` | 相对锚点的位移（格） |
| `setRotation(double x, double y, double z)` | **角度制**欧拉角，内部转成四元数 `rotationXYZ(x, y, z)` |
| `setRotation(Quaternionf)` | 直接给四元数（弧度语义），不做任何换算 |
| `setScale(double x, double y, double z)` / `setScale(Vector3f)` | Root 缩放 |
| `setDelay(int)` | 启动延迟（tick），在 `emit` 之后设置到每个对象上 |
| `setForcedDeath(boolean)` | 锚点消失时是否**立即**清掉可见残留（默认等粒子自然消亡） |
| `setAllowMulti(boolean)` | 同一锚点是否允许并存多个相同 FX |

> 这两套 `setRotation` 混用会得到约 57.3 倍的旋转错误：角度制重载内部做 `toRadians`，
> 四元数重载不做。本仓库 `AnchorPose` 的类注释专门写下了这条坑。

## 9.5 自定义 Executor：`IEffectExecutor` 的三个回调

`IEffectExecutor`（`client/fx/IEffectExecutor.java`）的完整接口只有这些：

```java
public interface IEffectExecutor {
    Level getLevel();                                            // 必须：这个 FX 属于哪个 Level
    default RandomSource getRandomSource() { return getLevel().getRandom(); }

    default void updateFXObjectTick(IFXObject fxObject) {}       // 每 tick、每个对象一次（低频逻辑）
    default void updateFXObjectFrame(IFXObject fxObject, float partialTicks) {} // 每帧、每个对象一次（平滑跟随）

    default void onTimelineSignal(String channel, String name, CompoundTag data, double time) {}
    default PostEffectStack postEffectSink() { return PostEffectStack.GLOBAL; }
}
```

写自定义执行器的三条经验（都能在本仓库 `client/fx/` 里找到对照实现）：

1. **只动 Root**：`updateFXObjectFrame` 里判 `object == runtime.getRoot()` 再写位姿；
   动子对象会和编辑器里编排好的层级互相打架（`FxAnchor.Executor.updateFXObjectFrame`）。
2. **tick 推进、frame 插值**，两个回调各管一半：tick 里按整刻推进状态，frame 里按
   `partialTicks` 算出该帧的实际位姿。两边都写位置会得到"一帧跳、一帧插值"的抖动
   （`FixedPointExecutor` 的类注释把这条写死了）。
3. **给随机源一个固定种子**：`RandomSource.create(20260928L)` 之类的稳定种子能让使用随机函数的
   资产在重播时可复现（`FxAnchor.java:51`、`FixedPointExecutor.java`）。

## 9.6 运行时数据注入：RuntimeValue 与 ParticleRuntime

**`RuntimeValue<T>`**（`client/gameobject/RuntimeValue.java`）是实例侧的一个命名槽：

| 方法 | 语义 |
|---|---|
| `get()` | 有覆盖就返回覆盖值，否则返回资产里的配置值 |
| `authored()` | 永远返回资产配置值（编辑器里的实时检查用） |
| `set(T)` | 写入覆盖值 |
| `setRaw(Object)` | 泛型采样路径用；普通集成代码不要用 |
| `clear()` | 删除覆盖，回到资产配置 |
| `isOverridden()` | 是否有非 null 覆盖 |

**`ParticleRuntime`**（`client/gameobject/emitter/particle/ParticleRuntime.java`）是粒子发射器实例的槽汇总，
分三类：

- 顶层 `ParticleConfig` 参数：`startColor`、`startDelay`、`startLifetime`、`startSpeed`、`startSize`、
  `startRotation`、`duration`、`prewarm`、`maxParticles`、`looping`、`parallelUpdate`；
- 每个模块的 Runtime：`emission`、`shape`、`physics`、`sizeOverLifetime`、`rotationOverLifetime`、
  `forceOverLifetime`、`externalForces`、`lights`、`colorOverLifetime`、`velocityOverLifetime`、
  `inheritVelocity`、`lifetimeByEmitterSpeed`、`colorBySpeed`、`sizeBySpeed`、`rotationBySpeed`、
  `noise`、`uvAnimation`、`trails`、`subEmitters`；
- 渲染 override 与自定义 GPU 数据：`renderer`（`layer` / `orderInLayer` / `vertexSortingMode` /
  `compositeMode` / `writeCustomMask` / `maskGroup` / `maskAlphaCutoff` / `materials` / `cull`）、
  `customData`（`slot(stream, channel)`）。

注入的正确姿势（修改只影响这一次播放，不改写 `.fx` 资产）：

```java
if (runtime.findObject("aura_core") instanceof ParticleEmitter emitter) {
    var v = emitter.runtime();                 // ParticleRuntime：槽是直接字段访问
    v.startSpeed.set(new Constant(0.2f));      // NumberFunction 常量
    v.maxParticles.set(256);
    v.physics.enable.set(true);
    v.physics.gravity.set(new Constant(0.03f));
    v.emission.emissionRate.set(new Constant(24f));
    v.renderer.layer.set(RendererSetting.Layer.Translucent);
    v.renderer.orderInLayer.set(20);
    v.customData.slot(0, 0).set(new Constant(0.85f));   // Stream 0 的第 0 个通道
    // 还原：v.physics.enable.clear(); … clearRenderOverride() 清掉全部渲染覆盖
}
```

两条硬约束：

1. **先查对象、再判类型、最后写槽**；对象名由编辑器里设置，`findObject` 是唯一入口。
2. **`set(null)` 不是"没有覆盖"**：null 只表示"没写"，要回到资产值必须 `clear()`。

渲染覆盖还有一个"批量友好"性质：只有当有效值真的不同（`effectiveEquals` / `effectiveHashCode`）时，
两个发射器才会被拆成不同的 pass；不改就还能合批（`RendererSetting.Runtime` 的注释）。

## 9.7 网络：服务端怎么触发特效

Photon 自带 4 个客户端 payload，注册在 `PhotonNetworking.registerPayloads`
（`playToClient`）：

| payload | 作用 | 关键字段 |
|---|---|---|
| `BlockEffectCommand` | 在方块上启动 FX | `location`、`pos`、`checkState` + 公共字段 |
| `EntityEffectCommand` | 在实体上启动 FX | `location`、实体 id 列表、`autoRotate` + 公共字段 |
| `RemoveBlockEffectCommand` | 移除方块 FX | `pos`、可选 `location`、`force` |
| `RemoveEntityEffectCommand` | 移除实体 FX | 实体 id 列表、可选 `location`、`force` |

公共字段（`command/EffectCommand.java`）：`location`、`offset`、`rotation`、`scale`、`delay`、
`forcedDeath`、`allowMulti`。实体命令的 `autoRotate` 取值就是 §9.3 的四个枚举。

服务端命令（`ServerCommands`，权限 `LEVEL_GAMEMASTERS`）：

```
/photon fx <id> block <x y z> [offset] [rotation] [scale] [delay] [forcedDeath] [allowMulti] [checkState]
/photon fx <id> entity <selector> [offset] [rotation] [scale] [delay] [forcedDeath] [allowMulti] [autoRotate]
/photon fx remove block <x y z> [force] [location]
/photon fx remove entity <selector> [force] [location]
```

分发范围（源码里的实现，直接决定"谁看得见"）：

- 方块命令：`PacketDistributor.sendToPlayersTrackingChunk(level, ChunkPos.containing(pos), command)`
  —— **只给追踪该区块的玩家**；
- 实体命令：`PacketDistributor.sendToAllPlayers(command)` —— **全服广播**（本项目的用法要自己权衡）；
- 接收端：两个 `execute` 都先 `if (LDLib2.isClient())` 再进客户端分支，
  客户端分支里才去 `FXHelper.getFX`、找实体、建执行器、`start()`。

在模组里自己触发时，不必自己造 payload：直接用这两条命令对应的 payload 类型发包即可；
只有当"触发条件/参数不在现有 payload 表达范围"时才需要自定义网络包，且**服务端侧不要引用 Photon 的客户端类**。

## 9.8 版本差异与迁移（本机 26.2.2.3 vs 官方文档站 2.2.x 口径）

| 主题 | 官方文档站（2.2.x）| 本机 26.2.2.3（源码核对）|
|---|---|---|
| 资源 id 类型 | `ResourceLocation.parse(...)` | `net.minecraft.resources.Identifier.fromNamespaceAndPath(...)`（`FXHelper` / `PhotonRegistries`） |
| 启动拼写 | `emmit(...)` 为兼容保留、已弃用 | 一致：`FXRuntime.emmit` 标 `@Deprecated` 并转发到 `emit` |
| 材质渲染缝 | 1.21 的 `ShaderInstance begin/end` | **已不存在**：`IMaterial.getRenderType(...)` + `BlendMode.toBlendFunction()` 直接产出管线（`IMaterial` 类注释） |
| 批处理 | 1.21 的 `PhotonFXRenderPass` 是批处理单元 | **`PhotonFXRenderPass` 现为 M0 桩**，批处理改由 `PhotonWorldRenderState` 的渲染状态批次承担 |
| 注册表注解 | `@LDLRegisterClient(manual = true)` | 该参数已移除，注册表改为无条件加载并自行扫描（`PhotonRegistries` 注释） |
| 项目格式 | `.fxproj` + DataFixer | 一致：`FXProject.VERSION = 5`，`.fx` 里写 `version` 走 `PhotonFXProjectDataFixer` |

> 未确认：本机 Photon 的**延迟层与 Iris 的逐 pass 交互细节**（`IrisTargetResolver` 的选择算法、
> `IrisCompositeMode` 对具体 pack 的判定）只读了公开方法签名与命令入口，没有逐行核对。

## 9.9 最小可运行示例（完整链路）

```java
package com.example.client;

import com.lowdragmc.photon.client.fx.EntityEffectExecutor;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.Constant;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

public final class AuraExample {
    private static final Identifier AURA =
            Identifier.fromNamespaceAndPath("minegenshin", "character/test/aura_body");

    public static FXRuntime play(Player player) {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;

        FX fx = FXHelper.getFX(AURA);
        if (fx == null) return null;                 // 资源还没导出：静默空转

        var executor = new EntityEffectExecutor(fx, level, player,
                EntityEffectExecutor.AutoRotate.NONE);
        executor.setOffset(0.0, 0.0, 0.0);
        executor.setForcedDeath(true);               // 锚点没了就收干净
        executor.setAllowMulti(true);                // 去重自己管
        executor.start();                            // 内部 createRuntime + emit

        FXRuntime runtime = executor.getRuntime();
        if (runtime != null && runtime.findObject("aura_core") instanceof ParticleEmitter emitter) {
            emitter.runtime().emission.emissionRate.set(new Constant(24f));
        }
        return runtime;
    }

    private AuraExample() {}
}
```

缓存的 Runtime 每次 tick 都要用 `isValid()` 复核；`isFinished()` 只说明"没有未来内容"，
循环发射器**永远不会** `isFinished()`。两者在 §11 的排错表里各占一行。

## 9.10 一次完整接入的分步流程（照着做就能跑起来）

前面各节是"零件"，这里把它们装成一台机器。目标：**角色进入原神模式时，眼睛位置常驻一圈光环**。

| 步 | 做什么 | 做完怎么知道对了 |
|---|---|---|
| 1 | 在编辑器里做出效果，把需要代码控制的对象命名（例如 `aura_core`、`aura_pivot`） | 在 FX Hierarchy 里能看到这些名字；名字唯一 |
| 2 | 导出成 `.fx`（有自定义材质/图/网格就导出 `.fxpack`） | 文件出现在 `/ldlib2/assets/<ns>/fx/...`；id 与路径一一对应 |
| 3 | 把资源放进仓库：`assets/<ns>/fx/...` 或 `fxpacks/` | 打包后能在 jar 里看到这两个目录 |
| 4 | 写"加载"：`FXHelper.getFX(id)`，**允许返回 null** | 资源缺失时游戏正常跑、日志不刷异常 |
| 5 | 选锚点：方块/实体 → 内置 Executor；骨骼/自算位置 → 自写 `IEffectExecutor` | 想清楚"这个特效跟着谁、什么时候该消失" |
| 6 | 接生命周期：每 tick 判断"想不想要" → 需要就 `start()`，不需要就 `destroy(false)`；缓存 Runtime 就查 `isValid()` | 切世界、`/photon_client clear_particles` 之后能自动恢复 |
| 7 | 需要动画的参数走 `RuntimeValue` 注入（颜色、强度、进度） | 改注入值立刻在画面上体现，且不改动 `.fx` 资产 |
| 8 | 多人可见的场景改走服务端 payload（§9.7），公共包只发通知 | 专用服务器启动无异常，附近玩家都能看到 |

**三个高频用法片段**（可以直接改改用）：

片段 A —— 加载 + 缓存 + 资源重载失效：

```java
private static FX cached;
private static Identifier cachedId;

static FX fx(Identifier id) {
    if (cached != null && id.equals(cachedId)) return cached;
    FX fx = FXHelper.getFX(id);                 // 失败返回 null，不抛异常
    if (fx != null) { cached = fx; cachedId = id; }
    return fx;                                  // 仍然可能是 null：调用方必须能空转
}

static void onResourceReload() {                // 资源包/数据包重载后
    cached = null;                              // 丢掉引用，下次自动重读
    // 注意：已经跑着的 Runtime 属于旧定义，等它自己播完或主动 destroy(false)
}
```

片段 B —— 一个能用的自定义执行器（骨架 + 每部分为什么）：

```java
public final class MyAnchorExecutor implements IEffectExecutor {
    private final Level level;
    private final Player player;      // 跟随目标
    private Vec3 origin;              // tick 级位置
    private int age;

    public MyAnchorExecutor(Level level, Player player) { this.level = level; this.player = player; }

    @Override public Level getLevel() { return level; }
    @Override public RandomSource getRandomSource() { return RandomSource.create(20260928L); } // 稳定种子

    @Override public void updateFXObjectTick(IFXObject object) {
        age++;                                        // 低频逻辑放 tick
        if (age > LIFETIME_TICKS) { /* 由拥有者 destroy(false) */ }
    }

    @Override public void updateFXObjectFrame(IFXObject object, float partialTicks) {
        if (!(object instanceof FXObject root)) return;           // 只动 Root，别碰子对象
        Vec3 pos = origin.add(forward.scale(speedPerTick * partialTicks));  // 刻内插值
        root.updatePos(pos); root.updateRotation(rotation); root.updateScale(scale);
    }
}
```

要点只有三条：**tick 推进状态、frame 做插值**（两处都写位置就抖动）；
**只动 Root**（子对象的位姿由资产编排）；**给随机源固定种子**（重播可复现）。

片段 C —— 把游戏状态喂给特效（颜色/强度/进度）：

```java
void pushState(FXRuntime runtime, float strength, int rgb) {
    if (runtime == null || !runtime.isValid()) return;             // 先查存活
    if (runtime.findObject("aura_core") instanceof ParticleEmitter emitter) {
        var v = emitter.runtime();
        v.startColor.set(new Constant(rgb));                       // NumberFunction 常量
        v.maxParticles.set((int) (64 + 192 * strength));
        v.customData.slot(0, 0).set(new Constant(strength));       // Stream 0 / Channel 0 → 给着色器
        // 要在下次改回资产值时用 clear()，不要 set(null)
    }
}
```
