# 12. 附录

## 12.1 类名速查

**顶点与状态**

| 用途 | 类 / 方法 |
| --- | --- |
| 顶点契约 | `com.mojang.blaze3d.vertex.VertexConsumer` |
| 顶点组装 | `BufferBuilder`、`MeshData`、`BufferUploader` |
| 变换栈 | `com.mojang.blaze3d.vertex.PoseStack` |
| 顶点格式 | `VertexFormat`、`DefaultVertexFormat` |
| 状态 | `com.mojang.blaze3d.systems.RenderSystem`、`GlStateManager` |
| 渲染类型 | `net.minecraft.client.renderer.RenderType`、`RenderStateShard` |
| 多缓冲 | `net.minecraft.client.renderer.MultiBufferSource.BufferSource` |
| 光照 | `net.minecraft.client.renderer.LightTexture` |
| 着色器 | `net.minecraft.client.renderer.ShaderInstance` |
| 后处理 | `net.minecraft.client.renderer.PostChain` / `PostPass` |

**实体与模型**

| 用途 | 类 |
| --- | --- |
| 实体渲染器 | `net.minecraft.client.renderer.entity.EntityRenderer` / `EntityRenderDispatcher` |
| GeckoLib 渲染器 | `software.bernie.geckolib.renderer.GeoRenderer` / `GeoEntityRenderer` / `GeoObjectRenderer` |
| GeckoLib 层 | `software.bernie.geckolib.renderer.layer.GeoRenderLayer` |
| GeckoLib 模型 | `software.bernie.geckolib.model.GeoModel`、`cache.object.BakedGeoModel` |
| GeckoLib 骨骼 | `cache.object.GeoBone`、`animation.state.BoneSnapshot` |
| GeckoLib 动画 | `animation.AnimationController` / `AnimatableManager` / `AnimationState` / `PlayState` |

**Photon**

| 用途 | 类 |
| --- | --- |
| 运行时 | `com.lowdragmc.photon.client.fx.FX` / `FXRuntime` / `FXHelper` |
| 执行器 | `IEffectExecutor` / `IFXEffectExecutor` / `EntityEffectExecutor` / `BlockEffectExecutor` |
| 对象 | `client.gameobject.IFXObject` / `FXObject` |
| 材质 | `client.gameobject.emitter.data.material.IMaterial` / `kila.KilaMaterial` |
| 模型源 | `client.gameobject.emitter.data.model.IModelSource` / `IDynamicMesh` |
| 动态光 | `client.light.PhotonLights` / `DynamicLight` / `FogVolume` |
| 注册表 | `com.lowdragmc.photon.PhotonRegistries` |

## 12.2 NeoForge 渲染事件速查

| 事件 | 总线 | 用途 |
| --- | --- | --- |
| `RenderLevelStageEvent` | 游戏 | 世界内分阶段渲染 |
| `RenderFrameEvent` | 游戏 | 每帧前后 |
| `EntityRenderersEvent.RegisterRenderers` | mod | 注册渲染器 |
| `RegisterShadersEvent` | mod | 注册 core shader |
| `RegisterParticleProvidersEvent` | mod | 粒子工厂 |
| `RegisterGuiLayersEvent` | mod | HUD 层 |
| `RegisterClientReloadListenersEvent` | mod | 资源重载监听 |
| `RenderHandEvent` / `RenderLivingEvent` / `RenderNameTagEvent` | 游戏 | 特定对象渲染 |
| `ViewportEvent` / `ComputeFovModifierEvent` | 游戏 | 相机 |

`RenderLevelStageEvent.Stage` 可选值：`AFTER_SKY`、`AFTER_SOLID_BLOCKS`、
`AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS`、`AFTER_CUTOUT_BLOCKS`、`AFTER_ENTITIES`、`AFTER_BLOCK_ENTITIES`、
`AFTER_TRANSLUCENT_BLOCKS`、`AFTER_TRIPWIRE_BLOCKS`、`AFTER_PARTICLES`、`AFTER_WEATHER`、`AFTER_LEVEL`。

## 12.3 1.21.1 与 26.2 对照

| 概念 | 1.21.1 | 26.2 |
| --- | --- | --- |
| 渲染模型 | 立即模式（写顶点 → `endBatch`） | 三段式（extract / submit / render） |
| 渲染数据 | 自己缓存 | `RenderState` + `DataTicket` |
| 资源 ID | `ResourceLocation` | `Identifier` |
| 存档序列化 | `serializeNBT(Provider)` / `CompoundTag` | `ValueOutput` / `ValueInput` |
| 着色器 | core shader JSON（`.json` + `.vsh` + `.fsh`） | Java 侧 `RenderPipeline` |
| 渲染类型 | `RenderType` 内联全部状态 | 命名渲染类型 + `RenderPipeline` |
| GeckoLib | 4.9.3（`software.bernie.geckolib`） | 5.5.6（`com.geckolib`） |
| 几何接管 | `GeoRenderIntercept` 空实现 | 提交接管 + GPU 蒙皮 |
| Photon | 2.2.8 | 26.2.2.3 |

## 12.4 事实来源与核对方式

| 来源 | 路径 |
| --- | --- |
| MC + NeoForge | `build/moddev/artifacts/neoforge-21.1.250-sources.jar` |
| Photon | `photon-neoforge-1.21.1-2.2.8-sources.jar` |
| LDLib2 | `ldlib2-neoforge-1.21.1-2.2.42-sources.jar` |
| GeckoLib | `geckolib-neoforge-1.21.1-4.9.3-sources.jar` |
| Photon 2.2.8 变更 | Modrinth `photon-editor` 的 `mc1.21.1-2.2.8-neoforge` changelog |

正文里的 `Foo.java:123` 都是这样核出来的；**与官方文档站冲突时以 jar 为准**。

## 12.5 术语中英对照

| 中文 | 英文 / 类名 |
| --- | --- |
| 立即模式 | immediate mode |
| 顶点消费者 | `VertexConsumer` |
| 渲染类型 | `RenderType` |
| 渲染状态部件 | `RenderStateShard` |
| 多缓冲 | `MultiBufferSource` |
| 骨骼 | bone（`GeoBone`） |
| 骨骼快照 | `BoneSnapshot` |
| 渲染层 | render layer（`GeoRenderLayer`） |
| 蒙皮 | skinning |
| 后处理 | post-processing（`PostChain`） |

## 12.6 未覆盖 / 未确认

- 1.21.1 的 GPU 蒙皮与几何接管尚未接入，第 7 章只覆盖默认 CPU 路径；
- Photon 编辑器内部实现（时间轴、资源面板、fx 包格式）未逐类展开；
- Iris 兼容层细节（`IrisCompositeMode` 各档行为）未逐条核对；
- 官方文档站与 jar 有出入的地方，本文以 jar 为准，未逐条列出差异。