# 6. Photon2 的渲染架构

> **澄清**：Photon 2.2.8 **没有** GeckoLib 集成（sources jar / all.jar / 上游提交里搜 `geckolib` 命中数为 0）。
> 跟 GeckoLib 有关的部分在 [4. GeckoLib](/doc/rendering-1.21.1-effects-geckolib)。

## 6.1 资源与数据模型

```text
assets/<ns>/fx/<name>.fx      ← 特效工程（对象树 + 时间轴 + 引用）
assets/photon/…               ← 编辑器资源库
config/photon-client.toml     ← 客户端配置

FX（静态） → FXRuntime（一次播放） → IFXObject 树（Emitter / LightObject / FogVolumeObject）
```

`FXObject` 直接继承原版 `Particle`，所以走的是原版粒子体系。

## 6.2 一帧挂在哪些事件上

| 事件 | 用途 |
| --- | --- |
| `ClientTickEvent.Post` | 推进 tick、更新动态光 |
| `RenderLevelStageEvent`（`AFTER_BLOCK_ENTITIES` / `AFTER_PARTICLES`） | 画 FX |
| `RenderFrameEvent.Post` | 帧收尾 |
| `RenderGuiEvent.Post` | 编辑器预览 |
| `LevelEvent.Unload` | 退世界清场 |

## 6.3 材质

| 材质 | 什么时候用 |
| --- | --- |
| `TextureMaterial` / `SpriteMaterial` / `BlockTextureSheetMaterial` | 普通贴图 |
| `CustomShaderMaterial` / `ShaderInstanceMaterial` | 自己的着色器 |
| `ShaderGraphMaterial` | 着色器图 |
| **`KilaMaterial`** | 2.2.8 新增的一体式 VFX 材质（27 个预设，可导出成着色器图） |

## 6.4 模型源

`json_model` / `obj_model` / `gltf_model` / `animated_gltf` / `dynamic_mesh`。
`dynamic_mesh` 是「几何在画的时候还在变」的通道（`IDynamicMesh`：`topology()` + `revision()` + `geometry()`），
也是「别的动画库想接进来」唯一现实的入口。

## 6.5 2.2.8 新增：动态光与体积雾

```java
PhotonLights.add(light);
PhotonLights.attach(entity, template, offset);
PhotonLights.flash(template, durationTicks);
PhotonLights.addFog(fogVolume);

new DynamicLight().point().at(x, y, z).color(1f, .8f, .4f).intensity(8f).range(10f).shadows(true).volumetric(.5f);
```

配置：`dynamic_light.enabled`、`shadowed_lights`（默认 8 盏）、`resolution`、
`voxel_budget_ms`（默认 2ms，超预算自动降级）、`volumetric`、`volumetric_samples`。

## 6.6 排错

| 症状 | 先查 |
| --- | --- |
| 特效不出现 | `.fx` 路径；`/photon clear client cache fx` |
| 只播一次 | 执行器过早结束；存活判据不能用时间轴时钟 |
| 光影下黑掉 | `enable_custom_effects_with_shader_pack` |

深入：[完全参考 8. Photon2 运行时](/doc/rendering-1.21.1-reference-photon-runtime)。