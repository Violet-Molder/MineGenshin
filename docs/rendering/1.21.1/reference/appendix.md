# 12. 附录


## 12.1 类名速查

**Minecraft / Blaze3D**

| 用途 | 类 |
| --- | --- |
| 顶点契约 | `com.mojang.blaze3d.vertex.VertexConsumer` |
| 顶点组装 / 上传 | `BufferBuilder` / `MeshData` / `BufferUploader` |
| 变换栈 | `PoseStack` |
| 渲染类型 | `net.minecraft.client.renderer.RenderType` / `RenderStateShard` |
| 着色器 | `net.minecraft.client.renderer.ShaderInstance` / `Uniform` |
| 多缓冲 | `MultiBufferSource` / `MultiBufferSource.BufferSource` |
| 实体渲染器 | `net.minecraft.client.renderer.entity.EntityRenderer` / `EntityRenderDispatcher` |
| 后处理 | `PostChain` / `PostPass` |

**GeckoLib 4.9.3**

| 用途 | 类 |
| --- | --- |
| 渲染器 | `software.bernie.geckolib.renderer.GeoRenderer` / `GeoEntityRenderer` / `GeoObjectRenderer` |
| 渲染层 | `software.bernie.geckolib.renderer.layer.GeoRenderLayer` |
| 模型 | `software.bernie.geckolib.model.GeoModel` |
| 骨骼 | `software.bernie.geckolib.cache.object.GeoBone` / `BakedGeoModel` |
| 动画 | `software.bernie.geckolib.animation.AnimationController` / `AnimatableManager` / `AnimationState` / `PlayState` |
| 快照 | `software.bernie.geckolib.animation.state.BoneSnapshot` |
| 事件 | `software.bernie.geckolib.event.GeoRenderEvent` |

**Photon 2.2.8**

| 用途 | 类 |
| --- | --- |
| 运行时 | `com.lowdragmc.photon.client.fx.FX` / `FXRuntime` / `FXHelper` |
| 执行器 | `IEffectExecutor` / `IFXEffectExecutor` / `EntityEffectExecutor` / `BlockEffectExecutor` |
| 对象 | `client.gameobject.IFXObject` / `FXObject` / `emitter.Emitter` |
| 材质 | `emitter.data.material.IMaterial` / `kila.KilaMaterial` |
| 模型源 | `emitter.data.model.IModelSource` / `IDynamicMesh` |
| 动态光 | `client.light.PhotonLights` / `DynamicLight` / `FogVolume` |
| 注册表 | `com.lowdragmc.photon.PhotonRegistries` |
| 命令 | `com.lowdragmc.photon.client.ClientCommands` |

## 12.2 两条线的类名对照

| 概念 | 1.21.1 | 26.2 |
| --- | --- | --- |
| 资源 ID | `ResourceLocation` | `Identifier` |
| 实体渲染 | `EntityRenderer#render(entity, yaw, partialTick, poseStack, buffers, light)` | RenderState 体系 |
| 存档序列化 | `serializeNBT(Provider)` / `CompoundTag` | `ValueOutput` / `ValueInput` |
| GeckoLib | 4.9.3（`software.bernie.geckolib`） | 5.5.6（`com.geckolib`） |
| Photon | 2.2.8（`photon-neoforge-1.21.1`） | 26.2.2.3（`photon-neoforge-26.2`） |
| LDLib2 | 2.2.42 | 26.2.2.42 |

## 12.3 参考

| 事实来源 | 位置 |
| --- | --- |
| MC / NeoForge | `build/moddev/artifacts/neoforge-21.1.250-sources.jar` |
| Photon | `photon-neoforge-1.21.1-2.2.8-sources.jar` |
| LDLib2 | `ldlib2-neoforge-1.21.1-2.2.42-sources.jar` |
| GeckoLib | `geckolib-neoforge-1.21.1-4.9.3-sources.jar` |
| Photon 2.2.8 变更 | Modrinth `photon-editor` 的 `mc1.21.1-2.2.8-neoforge` 版本 changelog |
| 项目代码 | 本仓库 1.21.1 分支 `src/main/java/com/linweiyun/genshin/` |

## 12.4 本文未覆盖 / 未确认的部分

- 1.21.1 上的几何接管与 GPU 蒙皮未接入，§7 的性能结论只覆盖默认路径；
- Photon 编辑器内部实现（时间轴、资源面板、fx 包格式）未逐类展开；
- Iris 兼容层的细节（`IrisCompositeMode` 各档行为）未逐条核对；
- 官方文档站对 1.21.1 / Photon 2.2.x 的描述与 jar 有出入时，本文以 jar 为准，
  出入点没有逐条列出。
