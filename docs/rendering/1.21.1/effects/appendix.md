# 10. 附录

## 10.1 类名速查

| 用途 | 类 |
| --- | --- |
| 顶点 | `com.mojang.blaze3d.vertex.VertexConsumer` / `BufferBuilder` / `MeshData` / `BufferUploader` |
| 变换 | `com.mojang.blaze3d.vertex.PoseStack` |
| 状态 | `com.mojang.blaze3d.systems.RenderSystem` / `platform.GlStateManager` |
| 渲染类型 | `net.minecraft.client.renderer.RenderType` / `RenderStateShard` |
| 着色器 | `net.minecraft.client.renderer.ShaderInstance` |
| 多缓冲 | `net.minecraft.client.renderer.MultiBufferSource.BufferSource` |
| 后处理 | `net.minecraft.client.renderer.PostChain` / `PostPass` |
| GeckoLib 渲染 | `software.bernie.geckolib.renderer.GeoRenderer` / `GeoEntityRenderer` / `GeoObjectRenderer` |
| GeckoLib 层 | `software.bernie.geckolib.renderer.layer.GeoRenderLayer` |
| GeckoLib 动画 | `animation.AnimationController` / `AnimatableManager` / `PlayState` |
| GeckoLib 骨骼 | `cache.object.GeoBone` / `animation.state.BoneSnapshot` |
| Photon 运行时 | `com.lowdragmc.photon.client.fx.FX` / `FXRuntime` / `FXHelper` |
| Photon 执行器 | `IEffectExecutor` / `IFXEffectExecutor` / `EntityEffectExecutor` |
| Photon 光 | `client.light.PhotonLights` / `DynamicLight` / `FogVolume` |

## 10.2 事件速查

`RenderLevelStageEvent`（阶段：`AFTER_SKY`、`AFTER_SOLID_BLOCKS`、`AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS`、
`AFTER_CUTOUT_BLOCKS`、`AFTER_ENTITIES`、`AFTER_BLOCK_ENTITIES`、`AFTER_TRANSLUCENT_BLOCKS`、
`AFTER_TRIPWIRE_BLOCKS`、`AFTER_PARTICLES`、`AFTER_WEATHER`、`AFTER_LEVEL`）、
`RenderFrameEvent`、`EntityRenderersEvent.RegisterRenderers`、`RegisterShadersEvent`、
`RegisterClientReloadListenersEvent`、`RegisterGuiLayersEvent`、`RenderHandEvent`。

## 10.3 事实来源

`neoforge-21.1.250-sources.jar`、`photon-neoforge-1.21.1-2.2.8-sources.jar`、
`ldlib2-neoforge-1.21.1-2.2.42-sources.jar`、`geckolib-neoforge-1.21.1-4.9.3-sources.jar`
（都在本机 Gradle 缓存里）；**与官方文档站冲突时以 jar 为准**。

## 10.4 这一册与完全参考的关系

这一册讲「怎么做」，[完全参考](/doc/rendering-1.21.1-reference-intro)讲「为什么」与「源码在哪一行」。
遇到本册没讲透的细节，按下面对应章去查：

| 本册 | 参考 |
| --- | --- |
| 1 心智模型 | 参考 1 |
| 2 Blaze3D | 参考 2 |
| 3 坐标 | 参考 6 |
| 4 GeckoLib | 参考 5 |
| 5 着色器 | 参考 3 |
| 6 Photon 架构 | 参考 8 |
| 7 实战 | 参考 10 |
| 8 性能 | 参考 7 |
| 9 排错 | 参考 11 |