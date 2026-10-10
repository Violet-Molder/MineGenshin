# 10. 附录


## 10.1 类名速查

**Minecraft / Blaze3D**

| 用途 | 类 |
|---|---|
| 矩阵栈 | `com.mojang.blaze3d.vertex.PoseStack` |
| 提交几何 | `net.minecraft.client.renderer.SubmitNodeCollector` / `OrderedSubmitNodeCollector` |
| 渲染管线 | `com.mojang.blaze3d.pipeline.RenderPipeline`、`net.minecraft.client.renderer.RenderPipelines` |
| 渲染类型 | `net.minecraft.client.renderer.rendertype.RenderType` / `RenderTypes` |
| 顶点 | `VertexFormat`、`VertexFormatElement`、`DefaultVertexFormat`、`VertexConsumer` |
| 缓冲 | `GpuBuffer`、`GpuBufferSlice`、`Std140Builder`、`Std140SizeCalculator` |
| 设备 | `com.mojang.blaze3d.systems.GpuDevice`、`CommandEncoder`、`RenderPass` |
| 相机 | `net.minecraft.client.renderer.state.level.CameraRenderState`（`public Vec3 pos`） |
| 着色器加载 | `net.minecraft.client.renderer.ShaderManager`、`ShaderDefines`、`ShaderType` |
| 渲染状态 | `net.minecraft.client.renderer.state.level.LevelRenderState`、`EntityRenderState` |

**NeoForge 事件**

| 事件 | 用途 |
|---|---|
| `RenderLevelStageEvent.After*` | 帧内各阶段（见 §1.2） |
| `ExtractLevelRenderStateEvent` | 提取自定义渲染状态 |
| `SubmitCustomGeometryEvent` | 提交自定义几何 |
| `RenderFrameEvent.Pre` / `.Post` | 每渲染帧的边界 |
| `ClientTickEvent.Pre` / `.Post` | 客户端 tick |
| `RegisterRenderPipelinesEvent` | 注册自定义 `RenderPipeline` |

**GeckoLib**

| 用途 | 类 |
|---|---|
| 渲染器基类 | `com.geckolib.renderer.base.GeoRenderer` / `GeoRendererInternals` |
| 通用渲染器 | `com.geckolib.renderer.GeoObjectRenderer` |
| 一趟渲染上下文 | `com.geckolib.renderer.base.RenderPassInfo` |
| 渲染层 | `com.geckolib.renderer.layer.GeoRenderLayer` |
| 每骨骼回调 | `com.geckolib.renderer.base.PerBoneRender`、`RenderPassInfo.BoneUpdater`、`RenderPassInfo.BonePositionListener` |
| 骨骼 | `com.geckolib.cache.model.GeoBone` / `BakedGeoModel` / `GeoLocator` |
| 骨骼快照 | `com.geckolib.animation.state.BoneSnapshot` / `renderer.base.BoneSnapshots` |
| 数据票据 | `com.geckolib.constant.dataticket.DataTicket` / `DataTickets` |

**Photon**

| 用途 | 类 |
|---|---|
| 定义 / 加载 | `com.lowdragmc.photon.client.fx.FX` / `FXHelper` |
| 运行时 | `com.lowdragmc.photon.client.fx.FXRuntime` |
| Executor | `IEffectExecutor` / `IFXEffectExecutor` / `FXEffectExecutor` / `BlockEffectExecutor` / `EntityEffectExecutor` |
| 运行时槽 | `com.lowdragmc.photon.client.gameobject.RuntimeValue` |
| 粒子发射器 | `com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter` / `ParticleRuntime` |
| 自定义数据 | `com.lowdragmc.photon.client.gameobject.emitter.data.CustomDataRuntime` |
| 信号 | `com.lowdragmc.photon.client.fx.timeline.PhotonSignals` |
| 后处理 | `com.lowdragmc.photon.client.postfx.PhotonPostFX` / `postfx.runtime.PostEffectStack` |
| 命令包 | `com.lowdragmc.photon.command.BlockEffectCommand` / `EntityEffectCommand` |
| 注册表 | `com.lowdragmc.photon.PhotonRegistries` |

## 10.2 术语中英对照

| 中文 | English |
|---|---|
| 提取 / 提交 / 绘制 | extract / submit / draw |
| 渲染状态 | render state |
| 渲染管线 | render pipeline |
| 渲染类型 | render type |
| 顶点格式 | vertex format |
| 实例化 | instancing |
| 骨骼 | bone |
| 骨骼快照 | bone snapshot |
| 渲染层 | render layer |
| 发射器 | emitter |
| 轨迹 / 光束 | trail / beam |
| 附加 GPU 数据 | additional GPU data |
| 自定义数据流 | custom data stream |
| 全屏图 / 渲染图 | fullscreen graph / render graph |
| 效果槽 | effect stack |
| 常驻 | sustained / persistent |
| 逐帧请求 | per-frame request |

## 10.3 参考

**Photon2 官方文档**

- 手册首页：<https://low-drag-mc.github.io/LowDragMC-Doc/zh/photon2/>
- Java API：<https://low-drag-mc.github.io/LowDragMC-Doc/zh/photon2/java-api/>
- 粒子系统：<https://low-drag-mc.github.io/LowDragMC-Doc/zh/photon2/particle-system/>
- Shader 与 GPU：<https://low-drag-mc.github.io/LowDragMC-Doc/zh/photon2/shaders-and-gpu/>
- 后处理：<https://low-drag-mc.github.io/LowDragMC-Doc/zh/photon2/post-processing/>
- 源码仓库：<https://github.com/Low-Drag-MC/Photon>

**本仓库内的其它文档**

- `docs/photon2-java-guide.md` —— Photon2 的 Java 使用指南（面向 Mod 开发）
- `docs/systems/render-asset.md` —— 本项目的资源、渲染与界面约定
- `docs/systems/performance.md` —— 性能优化系统
- `CHARACTER_SYSTEM.md` —— 角色系统详解

**源码 jar（用来核对本文的每一条说法）**

```
build/moddev/artifacts/minecraft-patched-26.2.0.88-sources.jar        Minecraft + NeoForge
~/.gradle/caches/modules-2/files-2.1/com.lowdragmc.photon/...         Photon
~/.gradle/caches/modules-2/files-2.1/com.geckolib/...                 GeckoLib
```

## 10.4 本文未覆盖 / 未确认的部分

诚实起见，列一下没写透的：

- **Photon 内部渲染管线的逐行细节**（`PhotonStage` 的取值、DrawJob 的收集与合批算法、
  延迟层与 Iris 的交互）——本文给了架构与结论，没有逐方法展开。
- **`RenderUtil.renderPoseToPosition` 的 `modelPos` 中 x 取负的根因**：
  只能从 `BoneSnapshot.translate` 与 `GeoBone.translateToPivotPoint` 的行为反推是
  「Blockbench 模型空间与世界空间 x 轴反向」，没有在 GeoJSON 加载器里逐行核对。
- **`render-asset.md` 里项目的资源路径规则**（`GenshinAssets` 的 fallback 机制）本文只做了引用。
- **Shimmer/Iris 的完整兼容矩阵**：本文只写了「分别测试、用 `/photon_iris dump`」。

如果你在核对本文时发现某一条与源码不符，**以源码为准**，并请顺手改掉本文 —— 这正是这份文档存在的意义。
