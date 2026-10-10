# 6. Photon2 2.2.8 的渲染架构


> **先说一条容易误传的事**：Photon 2.2.8 **没有** GeckoLib 集成。
> 2.2.8 的 sources jar 与 all.jar 里搜 `geckolib` / `bernie` 的命中数都是 0，
> 上游 `Low-Drag-MC/Photon` 与 `Low-Drag-MC/LDLib2` 的提交历史里也没有。
> 1.21.1 线上「支持 GeckoLib」指的是 **GeckoLib 4.9.3 本身**（见第 4 节）与本项目的接入层，
> 以及 Photon 自 2.2.7 起提供的**动态网格注入**（`IDynamicMesh`，见 §6.4）。

## 6.1 包结构与职责

| 包 | 职责 |
| --- | --- |
| `com.lowdragmc.photon.client.fx` | 运行时：`FX` / `FXRuntime` / `FXHelper` / `IEffectExecutor` |
| `com.lowdragmc.photon.client.gameobject` | 场景对象：`IFXObject` / `FXObject` / `Emitter` 与各类设置 |
| `com.lowdragmc.photon.client.gameobject.emitter.data.material` | 材质：`TextureMaterial` / `SpriteMaterial` / `ShaderGraphMaterial` / `KilaMaterial` |
| `com.lowdragmc.photon.client.gameobject.emitter.data.model` | 模型源：glTF / OBJ / JSON / 动态网格 |
| `com.lowdragmc.photon.client.light` | 2.2.8 新增：动态光、体积光、雾体积 |
| `com.lowdragmc.photon.client.postfx` / `postprocessing` | 后处理与 Bloom |
| `com.lowdragmc.photon.client.compat.iris` | Iris 兼容层 |
| `com.lowdragmc.photon.gui.editor` | 编辑器（场景视图、时间轴、资源面板） |
| `com.lowdragmc.photon.command` / `client.ClientCommands` | `/photon` 命令 |

## 6.2 一帧里 Photon 干了什么

Photon 2.2.8 在 1.21.1 上挂的是 NeoForge 事件
（`com/lowdragmc/photon/client/PhotonClientListeners.java`）：

| 事件 | 用途 |
| --- | --- |
| `ClientTickEvent.Post` `:76` | 推进 FX 的 tick、更新动态光 |
| `RenderFrameEvent.Post` `:47` | 每帧收尾 |
| `RenderLevelStageEvent` `:93` | `AFTER_BLOCK_ENTITIES` 与 `AFTER_PARTICLES` 两处渲染 FX |
| `RenderGuiEvent.Post` `:111` | 编辑器的预览 / 叠加 |
| `LevelEvent.Unload` `:68` | 卸载关卡时清光 |
| `ChunkEvent.Load` `:53` / `TagsUpdatedEvent` `:60` | 动态光的体素世界与资源重载 |

粒子本身由 `PhotonParticleManager`（`client/PhotonParticleManager.java`）承载，
它继承 LDLib2 的 `com.lowdragmc.lowdraglib2.client.scene.ParticleManager` ——
也就是说 Photon 在 1.21.1 上是**接进原版粒子体系**的：`FXObject` 甚至直接 `extends Particle`
（`client/gameobject/FXObject.java:38`）。

## 6.3 Framework：`IFXObject` 与 Emitter 模型

```text
FX（一份 .fx 配置）
 └─ FXRuntime（一次播放实例）
     └─ IFXObject 树
         ├─ ParticleEmitter / TrailEmitter / BeamEmitter / AraTrailEmitter
         ├─ LightObject（2.2.8）
         └─ FogVolumeObject（2.2.8）
```

`IFXObject`（`client/gameobject/IFXObject.java:34`）的核心方法：

```java
emit(IEffectExecutor effect)                 // :208 挂到执行器上
updatePos(Vector3f) / updateRotation(...)    // :250 / :254 每帧改根变换
setDelay(int) / setSelfTimeScale(float)      // :75 / :138
remove(boolean force)                        // :179
```

`IEffectExecutor`（`client/fx/IEffectExecutor.java`）就是「特效挂在哪、怎么跟」：

| 回调 | 频率 | 该写什么 |
| --- | --- | --- |
| `updateFXObjectTick(IFXObject)` | 每刻 | 低频逻辑：锚点没了就销毁、状态切换 |
| `updateFXObjectFrame(IFXObject, float)` | 每帧 | 高频逻辑：跟随位置 / 旋转 / 缩放 |

**两个回调都写位置会互相打架**（一个按整刻跳、一个按帧插值，加起来就是抖动）：
只在 tick 推进状态，只在 frame 按 `partialTicks` 插值算最终位置。

## 6.4 材质与模型：2.2.8 的 KilaMaterial

材质统一走 `IMaterial`，内置实现包括 `TextureMaterial`、`SpriteMaterial`、
`BlockTextureSheetMaterial`、`CustomShaderMaterial`、`ShaderGraphMaterial`、`ShaderInstanceMaterial`、
以及 2.2.8 新加的 **`KilaMaterial`**（`.../data/material/kila/KilaMaterial.java`）。

KilaMaterial 是「一体式 VFX 材质」：一个材质里打包主贴图、遮罩、溶解、折射、菲涅尔、
MatCap、UV 特效、顶点偏移、投影等模块（同一目录下 `Kila*.java` 各一个模块），
`KilaPresets.ALL`（`KilaPresets.java:515`）提供了 **27 个按类别分组的预设**；
它还能反向导出成着色器图（`kila/export/KilaGraphExport.java`）。

模型源（`.../data/model`）在 1.21.1 上有五种：

| 注册名 | 类 | 说明 |
| --- | --- | --- |
| `json_model` | `JsonModelSource` | 已烘焙的 Minecraft JSON 模型（要 `ModelEvent.RegisterAdditional` 或动态可加载） |
| `obj_model` | `ObjModelSource` | OBJ 网格 |
| `gltf_model` | `GltfModelSource` | glTF（含切线） |
| `animated_gltf` | `AnimatedGltfModelSource` | 带骨骼与动画的 glTF（2.2.7 起） |
| `dynamic_mesh` | `DynamicMeshSource` | **动态网格注入**：实现 `IDynamicMesh`，每帧按 `revision()` 交出新几何 |

`IDynamicMesh`（`.../model/IDynamicMesh.java`）就是「几何在画的时候还在变」这条通道：

```java
PhotonMesh topology();          // 不变的拓扑（顶点数、索引、UV、静止姿势），同一实例复用
long revision();                // 内容变了才 +1（从 1 开始，0 表示静止姿势）
float[] geometry();             // 该版本的 positions + normals（可空：只在 GPU 上时）
int glBuffer();                 // 或者直接给一个 GL buffer
```

> 这条通道才是「别的动画库能不能接进来」的答案：谁把骨骼动画算成顶点流，谁就能当模型源用。
> GeckoLib 并没有替你做这件事，需要自己写一个 `IDynamicMesh` 适配器。

## 6.5 后处理

1.21.1 的原版后处理是 `PostChain` / `PostPass`
（`net/minecraft/client/renderer/PostChain.java:31`、`:281`、`:313`），
资源在 `assets/<ns>/shaders/post/<name>.json`。Photon 在这之上加了：

| 类 | 作用 |
| --- | --- |
| `client.postprocessing.PhotonPostProcessing` | 统一申请渲染目标、做 blit 与 bloom |
| `client.postfx.runtime.PostFXCamera` | 时间轴里 PostProcess 片段用的相机口径 |
| `client/gameobject.emitter.renderpipeline.StackedDistortion` | 叠加式扭曲 |

配置里控制 Bloom 与自定义特效：`photon-client.toml` 的 `enable_bloom`、`bloom_mip_level`、
`bloom_threshold`、`bloom_intensity`、`enable_custom_effects`、`enable_custom_effects_with_shader_pack`、
`postfx_pool_budget_mb`（`PhotonConfig.java:75` 起）。

## 6.6 2.2.8 新增：动态光与体积雾

这是本版最大的一块新能力，入口全部收在 `PhotonLights`
（`client/light/PhotonLights.java`）：

```java
PhotonLights.add(light)                       // :44  挂一盏光
PhotonLights.remove(light)                    // :48
PhotonLights.attach(entity, template, offset) // :83  把光挂到实体上，返回 AutoCloseable 句柄
PhotonLights.flash(template, durationTicks)   // :95  闪一下
PhotonLights.addFog(fogVolume)                // :62  加一团雾
PhotonLights.addProvider(provider)            // :53  自己当光源提供者
```

`DynamicLight`（`client/light/DynamicLight.java:13`）是链式模板：

```java
new DynamicLight()
    .point()                       // 或 .spot(innerAngle, outerAngle)
    .at(x, y, z)
    .color(1f, 0.8f, 0.4f)
    .intensity(8f)
    .range(10f)
    .shadows(true)
    .volumetric(0.5f);             // 体积光强度
```

配置项（`photon-client.toml`，`PhotonConfig.java:99` 起）：`dynamic_light.enabled`、
`soft_shadows`、`shadowed_lights`（默认 8 盏）、`resolution`、`contact_shadow_steps`、
`screen_shadow_steps`、`voxel_budget_ms`、`volumetric`、`volumetric_density`、`volumetric_samples`。
**体积光是有明确预算的**：`voxel_budget_ms` 默认 2ms，超预算会自动降级。

## 6.7 生命周期：`isValid()` 为什么重要

`FXRuntime#isValid()` 决定「这个特效实例还该不该活着」。1.21.1 上最容易写出的 bug 是
把 `PhotonParticleManager` 的 `time`（时间轴时钟，`clear()` 会重置）当成存活判据 ——
存活判据必须用单调递增的 tick 计数（管理器里那个 `tickCounter`）。

资源重载 / 换维度 / 退出世界时：`FXHelper.clearCache()`（`client/fx/FXHelper.java:43`）
会清掉 `.fx` 解析缓存，`/photon clear client cache fx` 就是它的命令入口。

## 6.8 扩展注册表

`PhotonRegistries`（`PhotonRegistries.java`）把所有可扩展点摊开：

| 注册表 | 类型 | 说明 |
| --- | --- | --- |
| `photon:fx_object` | `FXObjectType` | 自定义 FX 对象类型 |
| `photon:material` | `IMaterial` | 自定义材质 |
| `photon:number_function` | `NumberFunction` | 曲线 / 表达式函数 |
| `photon:shape` | `IShape` | 发射形状 |
| `photon:model_source` | `IModelSource` | 模型源 |
| `photon:timeline_track` | `TrackType` | 时间轴轨道类型 |
| `photon:animated_property` | `AnimatedPropertyType` | 可动画属性 |

注册用 LDLib2 的注解（`@LDLRegisterClient(name = "...", registry = "photon:model_source")`），
Photon 自己注册的 `IModelSource` / `IMaterial` 都是这么写的。

## 6.9 和其它 Mod 的边界

- **Iris / 光影**：`client/compat/iris/*` 是兼容层；`enable_bloom_with_iris_shader`、
  `iris_use_translucent_particle_program` 两个开关决定是否在光影下继续接管。
- **LDLib2**：Photon 是 LDLib2 的插件（`integration/PhotonLDLibPlugin.java`），编辑器、
  节点图、资源浏览器都复用 LDLib2。
- **原版粒子**：因为 `FXObject extends Particle`，Photon 的粒子会走原版粒子排序与渲染管线，
  不是「另开一套渲染系统」。

## 6.10 关键事实速查

```text
mod id              photon
FX 资源             assets/<ns>/fx/<name>.fx      （FXHelper.FX_PATH = "fx/"）
编辑器资源根        LDLib2 的 assets/photon/
客户端配置          config/photon-client.toml（enable_bloom / dynamic_light / volumetric）
命令                /photon （清缓存、特效预览、Photon 1 → 2 转换）
```

---
