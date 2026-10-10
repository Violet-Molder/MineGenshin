# 8. Photon2 2.2.8：从编辑器到运行时


> **版本澄清**：2.2.8 新增的是**动态光、体积雾、KilaMaterial、材质导出成图、自定义空间、
> FX 对象复制粘贴**；它**没有** GeckoLib 集成 —— `geckolib` / `bernie` 在 2.2.8 的
> sources jar、all.jar 和上游提交历史里的命中数都是 0。1.21.1 的 GeckoLib 是 4.9.3（§5）。

## 8.1 资源布局

```text
assets/<ns>/fx/<name>.fx        ← 一个特效工程（对象树 + 时间轴 + 引用）
assets/photon/…                 ← 编辑器资源（材质、曲线、渐变、网格、贴图）
config/photon-client.toml       ← 客户端配置
```

`FXHelper`（`client/fx/FXHelper.java:34`）：`FX_PATH = "fx/"`（`:41`）、
`getFX(location)`（`:51`）、`listAllFX()`（`:63`）、`clearCache()`（`:43`）。

## 8.2 编辑器

| 面板 | 内容 |
| --- | --- |
| 场景视图 | 对象树可视化编辑、gizmo、网格与辅助线 |
| 时间轴 | 动画 / 子片段 / 声音 / 曲线 / 渐变 / 表达式 / 信号 / 后处理 / 速度 轨道 |
| 资源面板 | 材质、曲线、渐变、网格、贴图、fx 包 |
| Inspector | 选中对象的全部参数 |

编辑器也提供导出（`client/fx/fxpack/FXPackExporter.java`、`FXPacks.java`），
可以把工程打包成 fx 包分发。

## 8.3 渲染入口

Photon 2.2.8 在 1.21.1 上挂在 NeoForge 事件上（`client/PhotonClientListeners.java`）：

| 事件 | 行号 | 用途 |
| --- | --- | --- |
| `ClientTickEvent.Post` | `:76` | 推进 tick、更新动态光 |
| `RenderFrameEvent.Post` | `:47` | 每帧收尾 |
| `RenderLevelStageEvent`（`AFTER_BLOCK_ENTITIES` / `AFTER_PARTICLES`） | `:93`–`:104` | 世界内渲染 FX |
| `RenderGuiEvent.Post` | `:111` | 编辑器预览 / 叠加 |
| `LevelEvent.Unload` | `:68` | 卸载关卡清场 |
| `ChunkEvent.Load` / `TagsUpdatedEvent` | `:53` / `:60` | 动态光体素世界、资源重载 |

粒子由 `PhotonParticleManager`（`client/PhotonParticleManager.java`）承载，
它继承 LDLib2 的 `com.lowdragmc.lowdraglib2.client.scene.ParticleManager`。
`FXObject` 直接 `extends Particle`（`client/gameobject/FXObject.java:38`），
所以 Photon 的粒子会走原版粒子的排序与渲染管线。

## 8.4 材质体系

统一接口是 `IMaterial`，内置实现：

| 实现 | 说明 |
| --- | --- |
| `TextureMaterial` / `SpriteMaterial` / `BlockTextureSheetMaterial` | 普通贴图 / 图集精灵 |
| `CustomShaderMaterial` / `ShaderInstanceMaterial` | 自定义着色器 / 已有 `ShaderInstance` |
| `ShaderGraphMaterial` | 着色器图材质 |
| **`KilaMaterial`** | 2.2.8 新增的一体式 VFX 材质 |

KilaMaterial 把主贴图、遮罩、溶解、折射、菲涅尔、MatCap、UV 特效、顶点偏移、投影等
做成模块（`.../material/kila/Kila*.java`），`KilaPresets.ALL`（`KilaPresets.java:515`）
给出 **27 个按类别分组的预设**，还能反向导出成着色器图
（`kila/export/KilaGraphExport.java`）。

## 8.5 模型源与动态网格注入

| 注册名 | 类 | 说明 |
| --- | --- | --- |
| `json_model` | `JsonModelSource` | 已烘焙的 Minecraft JSON 模型 |
| `obj_model` | `ObjModelSource` | OBJ 网格 |
| `gltf_model` | `GltfModelSource` | glTF（含切线） |
| `animated_gltf` | `AnimatedGltfModelSource` | 带骨骼与动画的 glTF（2.2.7 起） |
| `dynamic_mesh` | `DynamicMeshSource` | 动态网格注入 |

`IDynamicMesh`（`.../model/IDynamicMesh.java`）就是「几何在画的时候还在变」这条通道：

```java
PhotonMesh topology();   // 不变的拓扑；同一实例复用
long revision();         // 内容变了才 +1（从 1 开始，0 = 静止姿势）
float[] geometry();      // 该版本的 positions + normals（可空）
int glBuffer();          // 或者直接给 GL buffer
```

> 「别的动画库能不能接进来」的答案就在这里：谁把骨骼动画算成顶点流，谁就能当模型源用。
> GeckoLib 并没有替你做这件事，需要用 `IDynamicMesh` 写一个适配器。

## 8.6 后处理与光影兼容

| 类 | 作用 |
| --- | --- |
| `client.postprocessing.PhotonPostProcessing` | 渲染目标、blit、Bloom |
| `client.postfx.runtime.PostFXCamera` | 时间轴后处理片段用的相机口径 |
| `client.gameobject.emitter.renderpipeline.StackedDistortion` | 叠加式扭曲 |
| `client.compat.iris.*` | Iris 兼容（`enable_bloom_with_iris_shader`、`iris_use_translucent_particle_program`） |

配置项（`PhotonConfig.java:75` 起）：`enable_bloom`、`bloom_mip_level`、`bloom_threshold`、
`bloom_intensity`、`enable_custom_effects`、`enable_custom_effects_with_shader_pack`、
`postfx_pool_budget_mb`（默认 256 MB）。

## 8.7 动态光与体积雾（2.2.8 新增）

```java
PhotonLights.add(light)                        // :44
PhotonLights.remove(light)                     // :48
PhotonLights.attach(entity, template, offset)  // :83  返回 AutoCloseable 句柄
PhotonLights.flash(template, durationTicks)    // :95
PhotonLights.addFog(fogVolume)                 // :62
PhotonLights.addProvider(provider)             // :53
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
`shadowed_lights`（默认 8）、`resolution`、`contact_shadow_steps`、
`screen_shadow_steps`、`voxel_budget_ms`（默认 2ms，超预算自动降级）、
`volumetric`、`volumetric_density`、`volumetric_samples`（默认 4）。

## 8.8 关键事实速查

```text
mod id          photon
FX 资源         assets/<ns>/fx/<name>.fx
编辑器资源根    LDLib2 的 assets/photon/
客户端配置      config/photon-client.toml
命令            /photon（清缓存、预览、Photon 1 → 2 转换）
```

---
