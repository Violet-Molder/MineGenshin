# 10. 附录


## 10.1 类名速查（1.21.1 / 2.2.8）

| 用途 | 类 |
| --- | --- |
| 顶点消费者 | `com.mojang.blaze3d.vertex.VertexConsumer` |
| 顶点构建 | `com.mojang.blaze3d.vertex.BufferBuilder` / `MeshData` / `BufferUploader` |
| 变换栈 | `com.mojang.blaze3d.vertex.PoseStack` |
| 渲染类型 | `net.minecraft.client.renderer.RenderType` / `RenderStateShard` |
| 着色器 | `net.minecraft.client.renderer.ShaderInstance` |
| 多缓冲 | `net.minecraft.client.renderer.MultiBufferSource.BufferSource` |
| 实体渲染器 | `net.minecraft.client.renderer.entity.EntityRenderer` |
| 后处理 | `net.minecraft.client.renderer.PostChain` / `PostPass` |
| GeckoLib 4 渲染器 | `software.bernie.geckolib.renderer.GeoRenderer` / `GeoEntityRenderer` / `GeoObjectRenderer` |
| GeckoLib 4 动画 | `software.bernie.geckolib.animation.AnimationController` / `AnimatableManager` / `PlayState` |
| GeckoLib 4 骨骼 | `software.bernie.geckolib.cache.object.GeoBone` / `animation.state.BoneSnapshot` |
| Photon 运行时 | `com.lowdragmc.photon.client.fx.FX` / `FXRuntime` / `FXHelper` / `IEffectExecutor` |
| Photon 对象 | `com.lowdragmc.photon.client.gameobject.IFXObject` / `FXObject` |
| Photon 光 | `com.lowdragmc.photon.client.light.PhotonLights` / `DynamicLight` / `FogVolume` |

## 10.2 参考

| 事实 | 来源 |
| --- | --- |
| MC / NeoForge | `neoforge-21.1.250-sources.jar`（`build/moddev/artifacts/`） |
| Photon | `photon-neoforge-1.21.1-2.2.8-sources.jar` |
| LDLib2 | `ldlib2-neoforge-1.21.1-2.2.42-sources.jar` |
| GeckoLib | `geckolib-neoforge-1.21.1-4.9.3-sources.jar` |
| Photon 2.2.8 变更 | Modrinth `photon-editor` 版本 `mc1.21.1-2.2.8-neoforge` 的 changelog |

## 10.3 本文未覆盖 / 未确认的部分

- 1.21.1 上的 GPU 蒙皮 / 几何接管未接入（`GeoRenderIntercept` 仍是空实现），
  所以 26.2 文档第 7 章的性能结论不能照搬到这里；
- Photon 编辑器内部实现（时间轴、资源面板）只在必要处点到，未逐类展开；
- Iris 兼容层的细节（`IrisCompositeMode` 各档行为）未逐条核对；
- 官方文档站对 1.21.1 / Photon 2.2.x 的描述与 jar 有出入时，本文以 jar 为准，
  出入点没有逐条列出。
