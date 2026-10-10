# 2. Blaze3D 在 26.2 里长什么样


先把包和类列清楚——**这是判断一份文档/教程是否过时的最快方法**。下面全部来自 26.2 源码 jar。

## 2.1 包结构

| 包 | 内容 |
|---|---|
| `com.mojang.blaze3d.systems` | `GpuDevice`、`CommandEncoder`、`RenderPass`、`RenderPassDescriptor`、`RenderSystem`、`ScissorState`、`TransientMemory`、`SamplerCache` |
| `com.mojang.blaze3d.pipeline` | `RenderPipeline`、`BindGroupLayout`、`BlendFunction`、`BlendEquation`、`ColorTargetState`、`DepthStencilState`、`RenderTarget`、`TextureTarget`、`MainTarget`、`CompiledRenderPipeline` |
| `com.mojang.blaze3d.vertex` | `PoseStack`、`VertexFormat`、`VertexFormatElement`、`DefaultVertexFormat`、`VertexConsumer`、`BufferBuilder`、`ByteBufferBuilder`、`MeshData`、`QuadInstance`、`VertexSorting`、`TlsfAllocator`、`UberGpuBuffer`、`StagingBuffer` |
| `com.mojang.blaze3d.buffers` | `GpuBuffer`、`GpuBufferSlice`、`GpuFence`、`Std140Builder`、`Std140SizeCalculator` |
| `com.mojang.blaze3d.platform` | `BlendFactor`、`BlendOp`、`CompareOp`、`PolygonMode`、`Lighting`、`Window`、`NativeImage`、`TextureUtil` 等 |
| `com.mojang.blaze3d.opengl` | `GlDevice`、`GlCommandEncoder`、`GlRenderPass`、`GlRenderPipeline`、`GlBuffer`、`GlTexture`、`GlProgram`、`GlStateManager`…（GL 后端实现） |

**已消失的（1.21 里还在，26.2 没有）**：`Tesselator`、`BufferUploader`、`ShaderInstance`（在 `client.renderer` 下也没了）、
`RenderState`（被 `*RenderState` 一族取代）。

> 判断口诀：**看到 `RenderSystem.setShader(...)` 或 `BufferUploader.draw*` 的文档，直接判定为过时。**

## 2.2 `PoseStack` —— 唯一还需要你天天用的「旧」东西

`PoseStack` 仍然是那个矩阵栈：`pushPose()` / `popPose()` / `last()` / `mulPose()` / `translate()` / `scale()`，
`last()` 返回 `PoseStack.Pose`，`.pose()` 拿 `Matrix4f`、`.normal()` 拿 `Matrix3f`。

一个常被忽略的点：**`PoseStack.Pose` 是值对象，可以拷出来延迟用**。26.2 的提交式渲染里这非常关键：

```java
PoseStack poseStack = info.poseStack();
tasks.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
    // 这个 lambda 真正执行时，外层 poseStack 早就 pop 了，
    // 所以必须用提交时捕获的 pose 还原现场
    poseStack.pushPose();
    poseStack.last().set(pose);
    ... 画 ...
    poseStack.popPose();
});
```

`submitCustomGeometry(PoseStack, RenderType, CustomGeometryRenderer)` 的第三个参数是
`SubmitNodeCollector.CustomGeometryRenderer`，函数式接口，唯一方法是
`void render(PoseStack.Pose pose, VertexConsumer buffer)`（`SubmitNodeCollector.java:13`）。

## 2.3 `SubmitNodeCollector` —— 26.2 提交几何的总入口

`SubmitNodeCollector` 本身只有一个嵌套接口，方法都在父接口 `OrderedSubmitNodeCollector` 上。
下面是从源码里抄下来的方法清单（省略了多行参数）：

| 方法 | 用途 |
|---|---|
| `submitCustomGeometry(PoseStack, RenderType, CustomGeometryRenderer)` | **最常用**：自定义顶点 |
| `submitModel(...)` / `submitModelPart(...)` | 提交原版 `Model` |
| `submitItem(...)` | 提交物品模型 |
| `submitBlockModel(...)` / `submitMovingBlock(...)` / `submitBreakingBlockModel(...)` | 提交方块模型 |
| `submitNameTag(...)` / `submitText(...)` | 文字与名牌 |
| `submitShadow(...)` / `submitFlame(...)` / `submitLeash(...)` | 原版实体附加件 |
| `submitShapeOutline(...)` | 提交 `VoxelShape` 描边 |
| `submitQuadParticleGroup(QuadParticleRenderState)` | 提交四边面粒子组 |
| `submitGizmoPrimitives(...)` | 提交调试 gizmo |

**关键认识**：这些方法都**只是登记**。几何什么时候真正画、按什么顺序画，由收集器在后面的绘制阶段决定。
所以「我提交了两批东西，它们一定按我的调用顺序画」是**错的** —— 顺序由 RenderType/管线与排序规则决定。

## 2.4 缓冲区与绑定组

26.2 里 `GpuBuffer` / `GpuBufferSlice` 是一等公民，配合 `CommandEncoder` / `RenderPass` 使用。
对写特效的人来说，直接手搓 buffer 的场景只有两类：

1. **自定义实例数据**（把每个粒子的 transform/颜色塞进 buffer，一次 draw 画一批）——Photon 的 GPU Instancing 就是这条路；
2. **常量缓冲**（`Std140Builder` / `Std140SizeCalculator` + uniform block）。

**std140 对齐**是最容易错的地方：`vec3` 在 std140 里占 16 字节（不是 12），`mat3` 是三列、每列占 16 字节。
Photon 的骨骼矩阵缓冲就踩过这个坑，源码里专门留了注释说明「法线块不能紧接矩阵块之后」。

## 2.5 渲染状态

26.2 用 `RenderPipeline` + `BindGroupLayout` 描述「怎么画」：混合（`BlendFunction`/`BlendFactor`/`BlendOp`）、
深度（`DepthStencilState`、`CompareOp`）、剔除、图元拓扑（`PrimitiveTopology`）、顶点格式、着色器都在里面。
`ColorTargetState` / `TextureTarget` / `MainTarget` 描述「画到哪」。

对 Photon 材质来说，对应的概念是「材质里的 Blend / Depth Test / Depth Write / Cull」，
最终都会落到一条 `RenderPipeline` 上。**同一个 pipeline + 同一套纹理与顶点布局，才能合批** —— 这是性能章节的根。

---

## 2.6 深入：提交层能提交什么

26.2 把「画什么」的入口收在一个收集器上，常见的提交方法（都在参考 2.3 里有逐个说明）：

| 方法 | 什么时候用 |
| --- | --- |
| `order(int)` | 需要控制同阶段内的绘制顺序时 |
| `submitCustomGeometry(pose, renderType, callback)` | 自己写顶点的自定义几何（最常用） |
| `submitModel(pose, renderType, …)` | 提交一个烘焙模型（GeckoLib 也走这条） |
| `submitText` / `submitNameTag` | 世界内文字、名牌 |
| `submitItem` / `submitBlockModel` | 物品、方块模型 |
| `submitShadow` / `submitFlame` / `submitLeash` | 影子、火焰、拴绳 |
| `submitShapeOutline` | 形状轮廓（`afterTerrain` 控制是否在地形之后） |
| `submitQuadParticleGroup` | 粒子批 |

要点：

- `submitCustomGeometry` 的回调是**稍后执行**的，闭包里捕获的必须是已经算好的数据，
  不要在回调里再去读实体；
- `order` 决定同阶段内的先后，不是跨阶段；
- 提交的文字/模型/轮廓各有自己的 `RenderType` 要求，选错表现为「看不见」而不是报错。

## 2.7 26.2 的「怎么画」：`RenderPipeline`

1.21.1 用 `assets/<ns>/shaders/core/*.json + .vsh + .fsh`；26.2 **删掉了 core shader JSON**，
改成在 Java 里用 `RenderPipeline` 描述：状态（混合/深度/剔除）、顶点格式、着色器、常量缓冲都在代码里声明。

| 关注点 | 1.21.1 | 26.2 |
| --- | --- | --- |
| 声明位置 | 资源 JSON | Java `RenderPipeline` |
| 参数传递 | 逐个 `uniform` set | std140 常量缓冲（UBO） |
| 与渲染类型的关系 | `RenderType` 内联全部状态 | 命名渲染类型 + 管线对象分开 |

**实践影响**：同一个着色器在 1.21.1 上改 JSON 就能生效，在 26.2 上要改代码并重新注册；
所以 26.2 侧不要把管线写成「一次性的」，尽量按用途复用。
