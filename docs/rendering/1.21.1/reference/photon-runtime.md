# 8. Photon2：从编辑器到运行时

Photon 是一个「粒子 / 光束 / 拖尾 / 后处理」的运行时 + 编辑器。这一章讲清楚它在 1.21.1 上的
数据模型、一帧里到底做了什么、编辑器里那些面板对应到哪个概念，以及 **2.2.8 新增了什么**。

> **先说一条容易误传的事**：Photon 2.2.8 **没有** GeckoLib 集成 ——
> 2.2.8 的 sources jar 与 all.jar 里搜 `geckolib` / `bernie` 命中数都是 0，
> 上游 `Low-Drag-MC/Photon` 与 `Low-Drag-MC/LDLib2` 的提交历史里也没有。
> 本项目里跟 GeckoLib 有关的部分在 [5. GeckoLib](/doc/rendering-1.21.1-reference-geckolib)。

## 8.1 资源与目录

```text
assets/<namespace>/fx/<name>.fx     ← 一个特效工程：对象树 + 时间轴 + 对材质/模型的引用
assets/photon/…                     ← 编辑器资源库（材质、曲线、渐变、网格、贴图）
config/photon-client.toml           ← 客户端配置（Bloom、动态光、体积光）
```

`.fx` 是**工程文件**，不是「编译产物」：运行时读它、按里面的对象树建 FX 对象。
读取入口是 `FXHelper`（`client/fx/FXHelper.java`）：

| 方法 | 行号 | 作用 |
| --- | --- | --- |
| `getFX(ResourceLocation)` | `:51` | 按名字取一份 FX（带缓存） |
| `listAllFX()` | `:63` | 列出所有可用的 `.fx`（编辑器用） |
| `clearCache()` | `:43` | 清解析缓存（`/photon clear client cache fx` 就是它） |

## 8.2 数据模型：FX / FXRuntime / FXObject / Emitter

```text
FX                   一份 .fx（静态定义）
 └─ FXRuntime        一次播放实例（有 root、有生命周期）
     └─ IFXObject    对象树
         ├─ Emitter（粒子 / 拖尾 / 光束 / 区域拖尾）
         ├─ LightObject（2.2.8 新增）
         └─ FogVolumeObject（2.2.8 新增）
```

`IFXObject`（`client/gameobject/IFXObject.java:34`）是所有对象的公共面：变换、显隐、延迟、
时间缩放、生命周期：

| 方法 | 行号 | 说明 |
| --- | --- | --- |
| `emit(IEffectExecutor)` | `:208` | 挂到某个执行器上开始播 |
| `updatePos(Vector3f)` / `updateRotation(...)` | `:250` / `:254` | 每帧改根变换（跟随用） |
| `setDelay(int)` | `:75` | 延迟若干刻 |
| `setSelfTimeScale(float)` | `:138` | 时间缩放（慢动作） |
| `remove(boolean force)` | `:179` | 结束（`force` = 立刻清掉残留粒子） |

一个关键实现细节：`FXObject` **直接继承原版 `Particle`**（`client/gameobject/FXObject.java:38`），
所以 Photon 的粒子走的是原版粒子体系（排序、渲染管线都一样），不是另开一套渲染系统。

## 8.3 一帧里 Photon 做了什么

Photon 2.2.8 在 1.21.1 上挂的是 NeoForge 事件（`client/PhotonClientListeners.java`）：

| 事件 | 行号 | 用途 |
| --- | --- | --- |
| `ClientTickEvent.Post` | `:76` | 推进 tick、更新动态光 |
| `RenderFrameEvent.Post` | `:47` | 每帧收尾 |
| `RenderLevelStageEvent` | `:93`–`:104` | `AFTER_BLOCK_ENTITIES` 画一部分、`AFTER_PARTICLES` 画另一部分 |
| `RenderGuiEvent.Post` | `:111` | 编辑器预览 |
| `LevelEvent.Unload` | `:68` | 退世界清场 |
| `ChunkEvent.Load` / `TagsUpdatedEvent` | `:53` / `:60` | 动态光体素世界、资源重载 |

粒子本体交给 `PhotonParticleManager`（`client/PhotonParticleManager.java`），
它继承 LDLib2 的 `client.scene.ParticleManager`。

## 8.4 材质：从贴图到 KilaMaterial

材质接口是 `IMaterial`，内置实现（`.../emitter/data/material/`）：

| 实现 | 什么时候用 |
| --- | --- |
| `TextureMaterial` / `SpriteMaterial` / `BlockTextureSheetMaterial` | 普通贴图 / 图集精灵 |
| `CustomShaderMaterial` / `ShaderInstanceMaterial` | 想套自己的着色器 |
| `ShaderGraphMaterial` | 用着色器图（LDLib2 节点图）搭材质 |
| **`KilaMaterial`** | 2.2.8 新增：一体式 VFX 材质 |

KilaMaterial 把主贴图、遮罩、溶解、折射、菲涅尔、MatCap、UV 特效、顶点偏移、投影等做成了模块
（同目录下 `Kila*.java` 各一个模块），`KilaPresets.ALL`（`KilaPresets.java:515`）提供
**27 个按类别分组的预设**，还能反向导出成着色器图（`kila/export/KilaGraphExport.java`）。

选择建议：**先用预设**，需要改行为时再进模块 —— 每个模块都会给渲染状态加一次绑定。

## 8.5 模型源：粒子喷出来的形状

`.../emitter/data/model/` 下有五种模型源（注册名即编辑器里看到的名字）：

| 注册名 | 类 | 说明 |
| --- | --- | --- |
| `json_model` | `JsonModelSource` | 已烘焙的 Minecraft JSON 模型（要能被模型库加载） |
| `obj_model` | `ObjModelSource` | OBJ 网格 |
| `gltf_model` | `GltfModelSource` | glTF（含切线） |
| `animated_gltf` | `AnimatedGltfModelSource` | 带骨骼动画的 glTF（2.2.7 起） |
| `dynamic_mesh` | `DynamicMeshSource` | **动态网格注入**：每帧交出新几何 |

`IDynamicMesh`（`.../model/IDynamicMesh.java`）是「几何在画的时候还在变」这条通道：

```java
PhotonMesh topology();   // 不变的拓扑（顶点数、索引、UV、静止姿势），同一实例复用
long revision();         // 内容变了才 +1（从 1 开始，0 = 静止姿势）
float[] geometry();      // 该版本的 positions + normals（可空：只在 GPU 上时）
int glBuffer();          // 或者直接给一个 GL buffer
```

**这是「别的动画库能不能接进来」的答案**：谁把动画算成顶点流，谁就能当模型源用。
GeckoLib 没有替你做这件事，需要自己写 `IDynamicMesh` 适配器。

## 8.6 后处理与光影兼容

| 类 / 配置 | 作用 |
| --- | --- |
| `client.postprocessing.PhotonPostProcessing` | 渲染目标、blit、Bloom |
| `client.postfx.runtime.PostFXCamera` | 时间轴 PostProcess 片段用的相机口径 |
| `client.compat.iris.*` | Iris 兼容 |
| `enable_bloom` / `bloom_mip_level` / `bloom_threshold` / `bloom_intensity` | Bloom 参数（`PhotonConfig.java:75` 起） |
| `enable_custom_effects` / `enable_custom_effects_with_shader_pack` | 是否在光影下接管 |
| `postfx_pool_budget_mb`（默认 256） | 后处理缓冲池上限 |

## 8.7 2.2.8 新增：动态光与体积雾

入口全部收在 `PhotonLights`（`client/light/PhotonLights.java`）：

```java
PhotonLights.add(light)                        // :44  挂一盏光
PhotonLights.attach(entity, template, offset)  // :83  挂到实体上，返回 AutoCloseable
PhotonLights.flash(template, durationTicks)    // :95  闪一下
PhotonLights.addFog(fogVolume)                 // :62  加一团雾
PhotonLights.addProvider(provider)             // :53  自己当光源提供者
```

```java
new DynamicLight()                 // client/light/DynamicLight.java:13
    .point()                       // 或 .spot(innerAngle, outerAngle)
    .at(x, y, z)
    .color(1f, 0.8f, 0.4f)
    .intensity(8f)
    .range(10f)
    .shadows(true)
    .volumetric(0.5f);
```

配置（`PhotonConfig.java:99` 起）：`dynamic_light.enabled`、`soft_shadows`、
`shadowed_lights`（默认 8 盏）、`resolution`、`contact_shadow_steps`、`screen_shadow_steps`、
`voxel_budget_ms`（默认 2ms，超预算自动降级）、`volumetric`、`volumetric_density`、`volumetric_samples`。

**性能直觉**：带阴影的光按盏计费，体积光按屏幕像素计费。先算「同时有几盏」，再谈别的。

## 8.8 排错

| 症状 | 先查 |
| --- | --- |
| 特效不出现 | `.fx` 路径 / 命名空间；缓存没清（`/photon clear client cache fx`） |
| 只出现一次 | 执行器提前 `retire` / `FXRuntime` 被判失效 |
| 光影下黑掉 | `enable_custom_effects_with_shader_pack` 关着；或 Iris 接管了管线 |
| 开了光影掉帧 | 动态光盏数、体积光采样、后处理池预算 |

下一章：[9. Photon2 Java API](/doc/rendering-1.21.1-reference-photon-api) 讲代码里怎么把它跑起来。