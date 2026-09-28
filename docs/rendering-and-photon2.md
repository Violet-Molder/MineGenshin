# Minecraft 渲染管线与 Photon2 特效开发详解

> **本文针对的版本**：Minecraft `26.2` · NeoForge `26.2.0.88` · Java `25` · Photon `26.2.2.3` · LDLib2 `26.2.2.41.a` · GeckoLib `5.5.6`
>
> **本文与 `docs/rendering_guide_for_photon2.md` 的关系**：那份文档的 Blaze3D 部分是 1.20/1.21 时代的 API
> （`RenderSystem.setShader`、`BufferUploader.drawWithShader`、`Tesselator.end()`、`RenderType.create`），
> 在 26.2 里**已经不存在了**；它的 Photon2 部分只有标题和目录，正文从未写出。本文是重新写的完整版本：
> 上半部分是**在本仓库实际使用的版本上核对过**的渲染管线，下半部分是 Photon2 的架构与接入方式。
>
> **怎么核对本文的说法**：正文里出现的每个类名/方法名都可以在下面两个源码 jar 里直接搜到——
> `build/moddev/artifacts/minecraft-patched-26.2.0.88-sources.jar`（Minecraft + NeoForge）、
> `~/.gradle/caches/modules-2/files-2.1/com.lowdragmc.photon/...`（Photon）。
> 凡是本文没核到的，会明确写「未确认」，不猜。

---

## 1. 先建立正确的心智模型

### 1.1 26.2 的渲染是「三段式」，不是「边算边画」

1.21 之前，实体渲染是「拿到实体 → 直接往 `BufferBuilder` 里塞顶点 → 立刻画」。26.2 已经改成：

```
① 提取（Extract）   把「要画什么」从游戏状态里摘出来，存进一堆 *RenderState 纯数据对象
② 提交（Submit）    遍历 RenderState，把几何提交给 SubmitNodeCollector（只是登记，不是绘制）
③ 绘制（Draw）      统一在管线内部按 RenderType/管线状态排序，成批画出去
```

这个改动带来的直接后果，也是本文要反复强调的一条：

> **实体渲染时 PoseStack 的原点是相机，不是世界原点。**
> 世界坐标 = `CameraRenderState.pos` + PoseStack 顶上的平移。

任何「我要在世界某处放个东西」的代码，都必须自己把相机位置加回去。忘了这一步的典型症状是：
特效/模型**死死跟着镜头跑**，你往前它就往前，看起来像贴在屏幕上。

### 1.2 一帧里到底按什么顺序发生

26.2 的 `RenderLevelStageEvent` 已经**没有 `Stage` 枚举**了，改成了一组子事件。
下面这份顺序抄自 `RenderLevelStageEvent` 的类注释（`neoforge/.../client/event/RenderLevelStageEvent.java:35-43`）：

| 顺序 | 子事件 | 触发位置 |
|---|---|---|
| 1 | `AfterSky` | `LevelRenderer.addSkyPass` 末尾，天空画完 |
| 2 | `AfterOpaqueBlocks` | `addMainPass` 早期，实心/镂空区块几何画完 |
| 3 | `AfterOpaqueFeatures` | 不透明「features」（实体、方块实体、粒子）画完 |
| 4 | `AfterTranslucentFeatures` | 半透明 features（实体、方块实体）画完 |
| 5 | `AfterTranslucentBlocks` | 半透明区块几何画完 |
| 6 | `AfterTranslucentParticles` | 半透明粒子画完 |
| 7 | `AfterWeather` | `addWeatherPass` 末尾，天气画完，世界边界之前 |
| 8 | `AfterLevel` | `GameRenderer.renderLevel` 里 `LevelRenderer.renderLevel` 返回之后（最后一个） |

另外两个必须知道的事件：

| 事件 | 用途 | 触发时机 |
|---|---|---|
| `ExtractLevelRenderStateEvent` | **提取自定义渲染状态**。自定义数据必须在这里摘出来、存进 `LevelRenderState`，否则后面的阶段拿不到 | 每个渲染帧的状态提取阶段 |
| `SubmitCustomGeometryEvent` | **在实体/方块实体/粒子渲染器之外提交自定义几何** | 「粒子提交之后、不透明提交渲染之前」 |

`SubmitCustomGeometryEvent` 提供 `getSubmitNodeCollector()` / `getPoseStack()` / `getLevelRenderState()`，
这是**不写渲染器也能画东西**的正规入口。

### 1.3 渲染发生在哪个线程

渲染在**渲染线程**（客户端主线程，也就是跑 `Minecraft.getInstance()` 那个线程）上。
但要注意两类异步：

- 粒子并行更新（Photon 的 `parallelUpdate`）会把粒子模拟拆到 worker 上，**渲染与提交仍在主线程**；
- 资源加载/重载有自己的线程，**不要在重载回调里碰 `RenderSystem`/GL 状态**。

---

## 2. Blaze3D 在 26.2 里长什么样

先把包和类列清楚——**这是判断一份文档/教程是否过时的最快方法**。下面全部来自 26.2 源码 jar。

### 2.1 包结构

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

### 2.2 `PoseStack` —— 唯一还需要你天天用的「旧」东西

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

### 2.3 `SubmitNodeCollector` —— 26.2 提交几何的总入口

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

### 2.4 缓冲区与绑定组

26.2 里 `GpuBuffer` / `GpuBufferSlice` 是一等公民，配合 `CommandEncoder` / `RenderPass` 使用。
对写特效的人来说，直接手搓 buffer 的场景只有两类：

1. **自定义实例数据**（把每个粒子的 transform/颜色塞进 buffer，一次 draw 画一批）——Photon 的 GPU Instancing 就是这条路；
2. **常量缓冲**（`Std140Builder` / `Std140SizeCalculator` + uniform block）。

**std140 对齐**是最容易错的地方：`vec3` 在 std140 里占 16 字节（不是 12），`mat3` 是三列、每列占 16 字节。
Photon 的骨骼矩阵缓冲就踩过这个坑，源码里专门留了注释说明「法线块不能紧接矩阵块之后」。

### 2.5 渲染状态

26.2 用 `RenderPipeline` + `BindGroupLayout` 描述「怎么画」：混合（`BlendFunction`/`BlendFactor`/`BlendOp`）、
深度（`DepthStencilState`、`CompareOp`）、剔除、图元拓扑（`PrimitiveTopology`）、顶点格式、着色器都在里面。
`ColorTargetState` / `TextureTarget` / `MainTarget` 描述「画到哪」。

对 Photon 材质来说，对应的概念是「材质里的 Blend / Depth Test / Depth Write / Cull」，
最终都会落到一条 `RenderPipeline` 上。**同一个 pipeline + 同一套纹理与顶点布局，才能合批** —— 这是性能章节的根。

---

## 3. 坐标空间完全指南

这一节建议反复读，因为**渲染 bug 的九成是坐标系搞错**。

### 3.1 五个空间

| 空间 | 原点 | 谁在用 |
|---|---|---|
| 世界空间 | 世界原点 | 游戏逻辑、`Entity.position()`、特效生成位置 |
| 相机相对空间 | **相机** | 所有实体/方块实体/粒子的 PoseStack（26.2 的提交式管线） |
| 模型空间 | 模型根 | GeckoLib 的骨骼坐标、Blockbench 的模型坐标（单位是 1/16 格） |
| 骨骼空间 | 骨骼 pivot | 骨骼动画、`PerBoneRender` 的 PoseStack |
| 屏幕空间 | 视口左上角 | 全屏后处理、UI |

### 3.2 三条换算公式

```
世界坐标   = CameraRenderState.pos + 相机相对坐标
相机相对坐标 = 世界坐标 - CameraRenderState.pos
模型像素    = 格 × 16         （Blockbench 惯例：1 格 = 16 像素）
```

`CameraRenderState.pos` 是 `public Vec3 pos`（`CameraRenderState.java:16`），
在渲染阶段可以从 `RenderPassInfo.cameraState()` 拿到。

### 3.3 角度还是弧度

这是本项目里真实踩过的坑，务必记牢：

| 入口 | 单位 |
|---|---|
| `IFXEffectExecutor#setRotation(double, double, double)`（Photon 的便捷重载） | **角度** |
| `IFXObject#updateRotation(Vector3f)` / JOML `Quaternionf#rotationXYZ` | **弧度** |
| GeckoLib `BoneSnapshot#setRotation` / `GeoBone#baseRotX` | **弧度** |
| `net.minecraft.world.entity.Entity#getYRot()` 等 | **角度** |

混用一次就是经典的「旋转差了 57.3 倍」（180/π）。

### 3.4 常见症状对照表

| 症状 | 原因 |
|---|---|
| 东西死死跟着镜头 | 把相机相对坐标当成世界坐标用了，忘了加 `cameraState().pos` |
| 东西在天上/地下很远 | 反过来：本来就是世界坐标，又加了一次相机位置 |
| 模型上下颠倒 | Blockbench 模型空间与世界空间的 **X 轴反向**（GeckoLib 用 `(-16, +16, +16)` 换算就是这件事） |
| 旋转差 57 倍 | 角度/弧度混用 |
| 缩放后位置偏了 | 非均匀父级缩放会改变子级的基向量，位置也会被一起缩放 |
| 换了个渲染器就偏半格 | 继承了 `GeoObjectRenderer` —— 它的 `adjustRenderPose` 默认 `translate(0.5, 0.51, 0.5)` |

---

## 4. GeckoLib 5 的渲染管线

本项目所有角色模型都走 GeckoLib。它比原版实体渲染多一层「渲染层 + 骨骼回调」的结构，
也是**唯一能拿到骨骼位姿的地方**。

### 4.1 本项目走的是哪条路

GeckoLib 有三类渲染器：`GeoEntityRenderer`（标准实体）、`GeoObjectRenderer`（通用摆件）、
`GeoReplacedEntityRenderer`（替换实体）。**本项目用的是 `GeoObjectRenderer`**：

```
AvatarRenderer.submit(...)                        ← 原版玩家渲染
  └─ AvatarRendererMixin（HEAD，cancellable）     ← 拦掉
       └─ CharacterRenderDispatcher.handleSubmit(...)
            └─ CharacterRenderer.performRenderPass(...)   ← extends GeoObjectRenderer
```

`CharacterRenderer` 覆写了三处（`client/render/character/CharacterRenderer.java`）：

1. **`adjustRenderPose` 覆写成空** —— 去掉 `GeoObjectRenderer` 默认的 `translate(0.5f, 0.51f, 0.5f)`。
   角色模型以原点为中心，不需要摆件那半格补偿。⚠️ 任何新写的 `GeoObjectRenderer` 子类**默认会吃到这半格偏移**。
2. **`submitRenderTasks` 覆写** —— 转给本模组的几何优化系统，失败时回退 GeckoLib 默认实现。
3. 自定义 `GeoModel`（`CharacterPlayerModel`），按 `CharacterRenderData` 决定模型/贴图/动画路径。

### 4.2 一趟渲染的完整调用顺序

下面这份顺序是从 `GeoRenderer` / `GeoObjectRenderer` / `GeoRendererInternals` / `RenderPassInfo` 源码里逐行对出来的，
是本章最重要的一张表：

| 序 | 调用 | 位置 | 说明 |
|---|---|---|---|
| — | `fillRenderState(...)` | `GeoObjectRenderer:72` | **所有 `addRenderData` 在这里跑完**，早于 `performRenderPass` |
| 0 | `renderState.addGeckolibData(DataTickets.PACKED_LIGHT, packedLight)` | `GeoObjectRenderer:74` | 光照值 |
| 1 | `poseStack.pushPose()` | `GeoRenderer:108` | 开栈 |
| 2 | `getRenderType(...)` | `GeoRenderer:110` | 返回 null → 本趟不画几何（但动画与骨骼回调**照跑**） |
| 3 | `RenderPassInfo.create(...)` | `GeoRenderer:111` → `RenderPassInfo:279` | 记 `objectRenderPose`；**自动注册两个 BoneUpdater**：`applyAnimationControllers`、`adjustModelBonesForRender` |
| 4 | 注册外部 `boneUpdaters` | `GeoRenderer:113-117` | 本项目 5 个角色骨骼 updater 在这一批 |
| 5 | `firePreRenderEvent(...)` | `GeoRenderer:119` | 返回 false 可取消整趟 |
| 6 | `preRenderPass(...)` | `GeoRenderer:120` | 用户钩子 |
| 7 | `scaleModelForRender(info, 1, 1)` | `GeoRenderer:121` | 参数为 1 时不缩放 |
| 8 | `adjustRenderPose(...)` | `GeoRenderer:122` | `GeoObjectRenderer` 默认半格平移；**本项目覆写为空** |
| 9 | `preApplyRenderLayers(...)` | `GeoRenderer:123` | 按层注册顺序：先 `layer.preRender(...)`，再 `layer.addPerBoneRender(info, info::addPerBoneRender)` |
| 10 | `captureModelRenderPose()` | `GeoRenderer:124` | 存下 `modelRenderPose`。**此后再调 `getModelRenderMatrixState()` 才合法** |
| 11 | `submitRenderTasks(...)` | `GeoRenderer:125` | `submitCustomGeometry` → `renderPosed(() -> model.render(...))`，**骨骼几何在这里产生** |
| 12 | `submitPerBoneRenderTasks(...)` | `GeoRenderer:126` | 第二次 `renderPosed`，逐骨骼 `transformToBone` 后调你的 `PerBoneRender` |
| 13 | `applyRenderLayers(...)` | `GeoRenderer:127` | 逐层 `layer.submitRenderTask(...)` |
| 14 | `poseStack.popPose()` | `GeoRenderer:130` | 收栈 |
| 15 | `postRenderPass(...)` | `GeoRenderer:132` | 用户钩子 |

**记住三个时间点的边界**：

- 第 9 步之前：可以加 `BoneUpdater`（改骨骼）；
- 第 10 步之前：**必须**加完 `BoneUpdater`，晚加不报错、只静默失效；
- 第 11 步之后：只剩提交，不能改骨骼。

### 4.3 `GeoRenderLayer` 的四个钩子

`GeoRenderLayer<T, O, R>` 是你写自定义渲染层的基类，四个可覆写点：

| 钩子 | 何时被调 | 能做什么 |
|---|---|---|
| `addRenderData(animatable, relatedObject, renderState, partialTick)` | 进 `performRenderPass` **之前** | 从 `animatable`/`relatedObject` 摘数据，塞进 `renderState`（因为后面拿不到它们了） |
| `preRender(renderPassInfo, renderTasks)` | 主流程第 9 步 | 加 `BoneUpdater`、读 renderState 数据 |
| `addPerBoneRender(renderPassInfo, consumer)` | 紧跟 `preRender`，同一循环 | 登记 `PerBoneRender` 回调 |
| `submitRenderTask(renderPassInfo, renderTasks)` | 主流程第 13 步 | 主模型与所有 PerBone 都提交完之后再做点事 |

**`renderState` 是唯一能从提取阶段带到渲染阶段的通道**，用 `DataTicket` 做 key：

```java
private static final DataTicket<List<ResolvedMount>> MOUNTS =
        DataTicket.create("minegenshin_bone_mounts", new TypeToken<>() {});
```

⚠️ `DataTicket.create` 按 `(类型, id)` **全局去重**：不同模组用同一个 id + 同一个类型会拿到同一个对象，数据互相顶掉。
id 不同或类型不同则互不干扰。

### 4.4 骨骼位姿：只有渲染那一瞬间才存在

这是本节的核心事实：

> **骨骼的位置不是一个可以随时查询的实体状态，它是「动画采样 + 层级变换累乘」的结果，只在渲染那一瞬间成立。**

GeckoLib 提供两条拿它的路：

**路 A：`PerBoneRender`（本项目用的）**

```java
info.addPerBoneRender(bone, (passInfo, b, tasks) -> {
    Matrix4f bonePose = new Matrix4f(passInfo.poseStack().last().pose());  // 相机相对
    Vector3f local = bonePose.getTranslation(new Vector3f());
    Quaternionf rot = bonePose.getUnnormalizedRotation(new Quaternionf());
    Vec3 camera = passInfo.cameraState().pos;
    Vec3 world = new Vec3(camera.x + local.x, camera.y + local.y, camera.z + local.z);
    // 存进缓存，给别的系统（比如 Photon）用
});
```

注意两点：

- 回调拿到的 PoseStack 停在**骨骼 pivot** 上（经过祖先链 `prepMatrixForBone` + `translateToPivotPoint`），
  **没有**最后那一步 `translateAwayFromPivotPoint`。要画骨骼自己的方块必须自己补回这一步。
- 它是「提交时捕获的位姿」，延迟绘制时必须 `poseStack.last().set(pose)` 还原。

**路 B：`RenderPassInfo#addBonePositionListener`**

```java
info.addBonePositionListener("long", (worldPos, modelPos, localPos) -> { ... });
```

回调三个参数的**精确含义**（来自 `RenderUtil.providePositionsToListeners` 的源码）：

| 参数 | 相对谁 | 单位 | 何时为 null |
|---|---|---|---|
| `localPos` | 本趟渲染的起点（`RenderPassInfo` 构造时捕获的 PoseStack） | 格 | 永不 |
| `modelPos` | 模型根（`captureModelRenderPose` 捕获），已换算模型单位 | 格（`(-16,+16,+16)` 换算） | 永不 |
| `worldPos` | `localPos` + `DataTickets.POSITION` 的平移 | 格 | **`POSITION` 没写入时恒为 null** |

⚠️ **本项目走 `GeoObjectRenderer`，从不写 `DataTickets.POSITION`，所以 `worldPos` 恒为 null。**
只有 `GeoEntityRenderer` / `GeoBlockRenderer` / `GeoArmorRenderer` / `GeoReplacedEntityRenderer` 会写它。
**结论：在本项目里想拿骨骼世界坐标，必须走路 A（自己加 `cameraState().pos`）。**

### 4.5 `BoneSnapshot` 与骨骼显隐

`BoneSnapshot` 是「这一趟渲染里某根骨骼的变换值」，字段有 scale XYZ / translate XYZ / rot XYZ、
`skipRender`、`skipChildrenRender`。它有两个必须知道的特性：

1. **生命周期只在一趟渲染内**：`renderPosed` 退出时会 `cleanup()` 把 `bone.frameSnapshot` 清成 null，
   下次 `renderPosed` 再 `apply()` 放回去。**在任何 `renderPosed` 之外缓存 `BoneSnapshot` 引用都是错的。**
2. **骨骼显隐是一个共享布尔**，没有「谁藏的」标记。所以两个渲染层都想藏同一根骨骼时，
   后跑的那层会把前一层藏的又画回来。正确做法是**在动手之前先读一次**当前状态，再决定要不要管。

### 4.6 写一个自定义渲染层（最小骨架）

```java
public final class ExampleGeoLayer<T extends GeoAnimatable, O, R extends GeoRenderState>
        extends GeoRenderLayer<T, O, R> {

    private static final DataTicket<List<String>> BONES =
            DataTicket.create("example_hidden_bones", new TypeToken<>() {});

    public ExampleGeoLayer(GeoRenderer<T, O, R> renderer) {
        super(renderer);
    }

    /** 阶段一：提取。此时还能拿到 animatable / relatedObject。 */
    @Override
    public void addRenderData(T animatable, @Nullable O relatedObject, R renderState, float partialTick) {
        if (!(relatedObject instanceof Player player)) return;
        List<String> bones = List.of("RightSword");
        if (!bones.isEmpty()) renderState.addGeckolibData(BONES, bones);
    }

    /** 阶段二：渲染早期。这里是加 BoneUpdater 的最后安全窗口。 */
    @Override
    public void preRender(RenderPassInfo<R> info, SubmitNodeCollector tasks) {
        List<String> bones = info.getGeckolibData(BONES);
        if (bones == null || bones.isEmpty()) return;
        info.addBoneUpdater((i, snapshots) -> {
            for (String name : bones) {
                snapshots.ifPresent(name, snapshot -> snapshot.skipRender(true));
            }
        });
    }

    /** 阶段三：登记每骨骼回调。 */
    @Override
    public void addPerBoneRender(RenderPassInfo<R> info, BiConsumer<GeoBone, PerBoneRender<R>> consumer) {
        List<String> bones = info.getGeckolibData(BONES);
        if (bones == null || bones.isEmpty()) return;
        for (String name : bones) {
            GeoBone bone = info.model().getBone(name).orElse(null);
            if (bone == null) continue;   // 骨骼不存在就整根别动，否则会「凭空少一块」
            consumer.accept(bone, (passInfo, b, tasks) -> submitAtBone(passInfo, tasks));
        }
    }

    private void submitAtBone(RenderPassInfo<R> info, SubmitNodeCollector tasks) {
        PoseStack poseStack = info.poseStack();
        tasks.submitCustomGeometry(poseStack, RenderTypes.entityCutout(someTexture), (pose, buffer) -> {
            poseStack.pushPose();
            poseStack.last().set(pose);
            // 想画骨骼自己的方块，补回 prepMatrixForBone 的最后一步：
            // bone.translateAwayFromPivotPoint(poseStack);
            // ... 写顶点 ...
            poseStack.popPose();
        });
    }
}
```

挂到渲染器上（必须在第一次渲染之前，层列表是懒编译的）：

```java
created.withRenderLayer(new BoneMountGeoLayer(created));
created.withRenderLayer(new TranslucentBoneGeoLayer(created));
```

### 4.7 GeckoLib 的坑（全部可在源码核对）

1. **`addBoneUpdater` 晚加只写一行 error 日志，不抛异常** —— 骨骼改动被无声丢弃。
2. **`getModelRenderMatrixState()` 在 `captureModelRenderPose()` 之前调用会抛 `IllegalStateException`**；
   而且它靠「矩阵还是单位阵」来判断「没 set」，所以捕获到单位阵时也会误判。
3. **`getPreRenderMatrixState()` 就是 `RenderPassInfo` 构造那一刻的 PoseStack**，
   在 `GeoObjectRenderer` 路径上它**已经包含**外层变换（本项目的 `scale(bodyScale)` + 两次 Y 旋转）。
4. **一个 pass 里 `BoneUpdater` 只 compute 一次**，两次 `renderPosed` 复用同一批快照对象。
5. **`PerBoneRender` 完全绕开 `skipRender`** —— 别人藏了骨骼对它无效，得自己判。
6. **`renderType == null` 不阻止动画与骨骼回调**，只是不画几何。
7. **`GeoRenderLayersContainer` 懒编译层列表**，`withRenderLayer` 必须在渲染开始前调完。

---

## 5. 着色器：26.2 把「Core Shader JSON」删掉了

这是本文最需要强调的**版本断裂点**，也是老文档错得最彻底的一节。

### 5.1 结论先讲

| | 1.20 / 1.21 | **26.2** |
|---|---|---|
| 核心着色器定义 | `assets/<ns>/shaders/core/xxx.json` + `.vsh` + `.fsh`，JSON 里写 blend/depthtest/cull/samplers/uniforms/attributes | **没有 JSON 了**。只有 `.vsh` / `.fsh` 源文件 |
| 渲染状态（混合、深度、剔除、顶点格式） | 写在那个 JSON 里 | 写在 Java 的 `RenderPipeline` 里（原版是 `RenderPipelines` 常量，模组用 `RegisterRenderPipelinesEvent` 注册） |
| 后处理链 | `shaders/post/*.json` | `post_effect/*.json`（`PostChainConfig`） |

源码依据（`net/minecraft/client/renderer/ShaderManager.java`）：

```java
public static final String SHADER_PATH = "shaders";
private static final String SHADER_INCLUDE_PATH = "shaders/include/";
private static final FileToIdConverter POST_CHAIN_ID_CONVERTER = FileToIdConverter.json("post_effect");
...
Map<Identifier, Resource> files = manager.listResources("shaders", ShaderManager::isShader);
for (Entry<Identifier, Resource> entry : files.entrySet()) {
    Identifier location = entry.getKey();
    ShaderType shaderType = ShaderType.byLocation(location);   // 按扩展名判断
    if (shaderType != null) loadShader(location, entry.getValue(), shaderType, files, shaderSources);
}
```

`ShaderType` 只有两种，靠**扩展名**识别：

```java
public enum ShaderType {
    VERTEX("vertex", ".vsh"),
    FRAGMENT("fragment", ".fsh");
    public FileToIdConverter idConverter() { return new FileToIdConverter("shaders", this.extension); }
}
```

所以 26.2 里一个「核心着色器」就是：

```
assets/<namespace>/shaders/<path>.vsh
assets/<namespace>/shaders/<path>.fsh
```

**没有 `.json`**。`blend` / `depth` / `cull` / 顶点属性布局这些全部搬到 `RenderPipeline` 里去了。

> 如果你手上有一份写着 `"blend": {"func": "add", "srcrgb": "srcalpha"}` 的文档或代码，它对 26.2 **无效**。

### 5.2 `RenderPipeline` 描述什么

`com.mojang.blaze3d.pipeline.RenderPipeline` 把「怎么画」的全部状态收进一个不可变对象，主要包含：

- **顶点格式**（`VertexFormat`）与图元拓扑（`PrimitiveTopology`）
- **着色器**（`ShaderSource`：顶点/片元源文件 id）
- **混合**：`BlendFunction`（源因子、目标因子、`BlendEquation`）与 `ColorTargetState`
- **深度**：`DepthStencilState` + `CompareOp`（深度测试）与深度写入开关
- **剔除**：`cull` 开关
- **绑定组布局**：`BindGroupLayout`（uniform / sampler 怎么绑）
- **多边形模式**：`PolygonMode`（填充 / 线框）
- **定义开关**：`ShaderDefines`（`#define` 注入）

**合批的前提**：几何、材质 program、纹理/采样器状态、渲染层、顶点布局**全都相同**才能并成一次 draw。
这就是为什么「给每个粒子单独换一件材质」会把性能打崩。

### 5.3 GLSL 速查

**类型**

| 类别 | 写法 |
|---|---|
| 标量 | `float` `int` `uint` `bool` `double` |
| 向量 | `vec2/3/4`、`ivec*`、`uvec*`、`bvec*`；分量 `.x/.y/.z/.w` 或 `.r/.g/.b/.a` 或 `[i]` |
| 矩阵 | `mat2/3/4`（列主序）、`mat2x3` 等非方阵 |
| 采样器 | `sampler1D/2D/3D/Cube`、`sampler2DArray`、`sampler2DShadow`、`isampler2D`、`usampler2D` |
| 不透明类型 | `sampler*`、`image*`、原子计数器 —— 只能 `uniform` 传递，不能赋值比较 |

**限定符（存储 / 参数 / 精度）**

| 限定符 | 含义 |
|---|---|
| `const` | 编译期常量 |
| `in` / `out` | 顶点：输入属性 / 输出到下一阶段；片元：输入 varying / 输出颜色 |
| `uniform` | 每次 draw 由 CPU 提供，所有顶点/片元相同 |
| `flat` / `noperspective` / `smooth` | varying 插值方式（整数 varying **必须** `flat`） |
| `layout(location = N)` | 顶点属性槽位；片元输出索引 |
| `highp` / `mediump` / `lowp` | 精度限定（桌面 GL 里基本被忽略，写 shader 时按需保留） |

**内置变量**

| 阶段 | 变量 |
|---|---|
| 顶点 | `gl_Position`（必须写）、`gl_VertexID`、`gl_InstanceID`、`gl_PointSize` |
| 片元 | `gl_FragCoord`、`gl_FrontFacing`、`gl_PointCoord`、`discard`、`gl_FragDepth` |

**常用内置函数**（按用途分组）

| 组 | 函数 |
|---|---|
| 角度 | `radians` `degrees` `sin` `cos` `tan` `asin` `acos` `atan(y,x)` |
| 指数 | `pow` `exp` `log` `exp2` `log2` `sqrt` `inversesqrt` |
| 通用 | `abs` `sign` `floor` `ceil` `trunc` `round` `fract` `mod` `min` `max` `clamp` `mix` `step` `smoothstep` |
| 几何 | `length` `distance` `dot` `cross` `normalize` `faceforward` `reflect` `refract` |
| 矩阵 | `matrixCompMult` `transpose` `inverse` `determinant` |
| 纹理 | `texture(sampler, uv)` `textureLod` `textureGrad` `texelFetch` `textureSize` |
| 导数 | `dFdx` `dFdy` `fwidth`（片元专用，做抗锯齿/法线重建常用） |

**写 shader 的三条纪律**

1. **整数 varying 一定加 `flat`**，否则插值出来的值没有意义。
2. **不要用 `if` 里做纹理采样**（除非是 uniform 分支），不同 GPU 对非一致控制流的处理差异很大。
3. **`discard` 会关掉 early-Z**，大面积使用时性能损失明显；能用 alpha blend 表达就别 discard。

### 5.4 `#moj_import` 与 include 机制

26.2 仍然在加载 `.vsh` / `.fsh` 之前跑一遍 `GlslPreprocessor`，支持：

- **绝对导入**：`#moj_import <namespace:path>` → 解析到 `assets/<namespace>/shaders/include/<path>`
- **相对导入**：`#moj_import "relative/path.glsl"` → 相对当前文件目录

源码依据（`ShaderManager.createPreprocessor`）：

```java
if (isRelative) {
    locationx = parentLocation.withPath(parentPath -> FileUtil.normalizeResourcePath(parentPath + path));
} else {
    locationx = Identifier.parse(path).withPrefix("shaders/include/");
}
```

所以公共函数库的正确位置是：

```
assets/<namespace>/shaders/include/<name>.glsl
```

Photon 自带了一批 include（例如给粒子用的 `photon:particle.glsl`），
自定义材质可以直接 `#moj_import <photon:particle.glsl>` 复用它的 `ParticleData` 结构。

### 5.5 std140 布局

只要往 uniform block / 常量缓冲里塞数据，就必须守 std140：

| 类型 | 对齐 | 占位 |
|---|---|---|
| `float` / `int` / `bool` | 4 | 4 |
| `vec2` | 8 | 8 |
| `vec3` | **16** | **16**（不是 12！） |
| `vec4` | 16 | 16 |
| `mat3` | 16（按列） | **48**（三列 × 16） |
| `mat4` | 16（按列） | 64 |
| 数组 | 元素对齐向上取整到 16 | 每元素占整份对齐后大小 |

典型踩坑：**把 `mat3` 数组紧接在 `mat4` 数组后面写**，导致后面所有数据错位。
正确做法是显式 `data.position(OFFSET)` 跳到下一个块（Photon 的骨骼/法线矩阵缓冲就是这么处理的）。

### 5.6 顶点属性与 location

| 寄存器 | 常见用途 |
|---|---|
| `Position` | 位置 |
| `Color` | 顶点色 |
| `UV0` | 主纹理坐标 |
| `UV1` | 光照（packed light，两个 16 位） |
| `UV2` | overlay（packed overlay） |
| `Normal` | 法线 |
| `LineWidth` | 线宽 |

**自己的自定义属性（比如每实例的 transform、自定义 GPU 数据）必须自己分配 location**，
并且 **location 会随「启用哪些内置通道」而移动** ——
所以发射器设置与 GLSL 里的 `layout(location = N)` 必须一起维护，改一个就要改另一个。

### 5.7 着色器出问题时怎么查

| 症状 | 先查 |
|---|---|
| 全黑 | 编译日志；然后把常量颜色直接接到输出确认管线通不通 |
| 全白/过曝 | 混合模式与颜色约定不匹配（是否 premultiplied）、是否走了 HDR 目标 |
| 数据恒为 0 | 该数据通道没启用，或渲染路径（Instanced / CPU）不支持 —— **不支持时返回 0 而不是报错** |
| 只有部分设备出错 | 精度限定、`discard`、非一致控制流、`mat3` 对齐 |
| 改了 shader 没生效 | 资源重载（F3+T）；再清客户端缓存 |

**通用调试法**：从「常量 → 输出」开始，逐个把输入接回来，一次只加一项。
这比盯着一个复杂 shader 猜要快一个数量级。

---

## 7. 实战：把渲染接到 Photon

### 7.1 「什么时候召唤粒子」只有四种时机

Photon 的播放模型是「**逐帧请求 / 显式生命周期**」，所以接到游戏里的方式也是有限的几种。
下表是这四种以及各自的正确做法：

| # | 时机 | 锚点 | 用什么 | 生命周期由谁管 |
|---|---|---|---|---|
| 1 | **常驻跟随角色** | 实体 | `EntityEffectExecutor` | 你每 tick 判断条件，条件不成立就 `destroy` |
| 2 | **常驻跟随手上的武器** | 武器骨骼 | 自定义 `IEffectExecutor` + 骨骼缓存 | 同上 |
| 3 | **仅在攻击动画期间** | 武器尖 | 同上 + 动作状态机门禁 | 状态机说了算 |
| 4 | **技能后定点生成、朝前飞** | 固定世界坐标 | 一次性 Executor（自己推进位置） | 自己走完或到时刻自毁 |

四者的共同点：**都要有一个「锚点」，而且锚点必须是客户端可得的**。
差别只在锚点从哪来：实体位置、骨骼位姿、还是一个写死的世界坐标。

### 7.2 用 `EntityEffectExecutor` 做常驻跟随（最简单的一档）

```java
EntityEffectExecutor executor = new EntityEffectExecutor(
        fx, level, player, EntityEffectExecutor.AutoRotate.NONE);
executor.setOffset(0, 0, 0);      // 相对眼睛位置
executor.setForcedDeath(true);    // 锚点没了立刻清残留
executor.setAllowMulti(true);     // 去重自己管，避免 start() 被静默跳过
executor.start();
```

它已经在做三件你不用自己写的事：

1. root 每帧跟随**插值后的眼睛位置**；
2. 实体死亡时结束效果；
3. `allowMulti=false` 时按 FX 去重，避免同一个实体叠一堆同样的效果。

**但有一件事它不会做：重建。** 切世界、`/photon_client clear_particles` 之后，
粒子引擎会把粒子直接丢掉，Runtime 根本没机会走到 `isFinished()`。
所以缓存实例时必须这样判断：

```java
FXRuntime runtime = executor == null ? null : executor.getRuntime();
if (runtime != null && runtime.isValid() && !runtime.isFinished()) {
    return;                       // 还活着，什么都不用做
}
// 否则重建
```

`isValid()` 是**缓存 Runtime 时唯一正确的存活检查**（O(1)，可以每 tick 调），
它用 host generation + root heartbeat 检测「粒子被引擎丢了」这种情况。

### 7.3 跟随武器：骨骼 → 世界坐标 → Photon root

这是整个链路里唯一有技术含量的一环，完整数据流如下：

```
GeckoLib 渲染趟（渲染线程，每帧）
  └─ WeaponAnchorGeoLayer.addPerBoneRender(...)
       └─ PerBoneRender 回调：
            Matrix4f bonePose = poseStack.last().pose();   ← 相机相对
            Vector3f local    = bonePose.getTranslation();
            Vec3     world    = cameraState().pos + local;  ← 加回相机位置
            WeaponAnchorCache.put(player, world, rotation); ← 按「渲染帧」打时间戳

Photon 帧回调（同一线程，每帧）
  └─ IEffectExecutor#updateFXObjectFrame(object, partialTicks)
       └─ 从缓存取出世界位姿 → object.updatePos(...) / updateRotation(...)
```

四个必须注意的点：

1. **只动 root**。`FXRuntime` 有一个恒存在的空 root（`FXRuntime.ROOT_UUID`），
   动它等于动整棵树；去动子对象会和 Authored 层级互相打架。
2. **缓存要按渲染帧过期**，不能按实体刻。角色在第一人称、离屏、被别的模组挡住时根本不渲染，
   这时候应该**保持上一帧的位姿**，而不是把特效瞬移到世界原点。
3. **坐标系假设要加保险**。本项目用「相机相对 PoseStack + `CameraRenderState.pos`」这条公式，
   代码里加了一道自检：结果离玩家超过若干格就整帧丢弃。坐标系假设被将来改动打破时，
   宁可特效停在上一帧，也不要它飞到天边。
4. **PoseStack 是渲染瞬间的快照**。骨骼位置不是实体状态，是「动画采样 + 层级变换累乘」的结果，
   只有在渲染那一瞬间才成立 —— 这也是为什么必须借渲染层来抓。

**武器尖**：骨骼原点在握把附近，尖在骨骼局部轴向上。用骨骼自己的世界旋转去换算这个偏移，
挥砍时特效才会跟着刀尖走：

```java
Vector3f tip = rotation.transform(new Vector3f(0f, TIP_OFFSET, 0f));
Vector3f at  = new Vector3f(boneWorldPos).add(tip);
```

⚠️ 不同武器的「尖」方向不一样（`+Y` / `-Y` / `+Z`），偏移长度也不同。
这两个常数必须**按实际武器模型调**，没有通用值。

### 7.4 用动作状态机做「只在攻击时生效」

客户端的动作状态是**公开可读**的静态字段：

```java
String state = ActionStateMachine.currentState;   // 例："shenhe_attack_1"
```

门禁写法建议**双保险**（精确名单 + 宽松兜底），这样将来动画名改了不会静默失效：

```java
public static boolean inAttackAnimation() {
    String state = ActionStateMachine.currentState;
    if (state == null) return false;
    return SPECIAL_ANIMS.contains(state) || state.contains("attack");
}
```

**不要**在攻击动作里直接 `start()` 一个特效然后不管 —— 动作会被打断、会连招、会中途取消，
必须每 tick 用状态机重新求值「现在该不该亮」，让特效跟着状态走。

### 7.5 定点生成 + 朝前飞

```java
Vec3 origin = player.position().add(look.x * 1.0, 0.3, look.z * 1.0);
new FixedPointExecutor(level, origin, player.getYRot(), speed)
        .lifetime(60)
        .start(fx);
```

位置推进的正确写法是**在 tick 里推进、在 frame 里插值**：

```
tick  ：origin += forward × speed                              （推进一个整刻）
frame ：pos = origin + forward × speed × partialTicks          （刻内插值，不抖）
```

**两个回调都写位置会互相打架**（一个按整刻跳、一个按帧插值，加起来就是抖动）。

速度传 `0` 就是「位移完全交给特效自己做」（编辑器里给 Emitter 开 `WORLD` 模拟空间 + Velocity over Lifetime）——
两种做法都成立，看特效作者的习惯。

### 7.6 从服务端触发

特效是客户端的，但「什么时候放」通常由服务端逻辑决定。好消息是 **Photon 已经自带两个网络包**：
`/photon fx` 命令走的就是它们。

```java
var command = new EntityEffectCommand();
command.setLocation(Identifier.fromNamespaceAndPath("mymod", "slash_trail"));
command.setEntities(List.of(target));
command.setOffset(new Vec3(0, 0.6, 0));
command.setAutoRotate(EntityEffectExecutor.AutoRotate.LOOK);
command.setForcedDeath(true);
PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, command);
```

方块版把 `EntityEffectCommand` 换成 `BlockEffectCommand`，用 `setPos(BlockPos)`，
并按区块发送（`PacketDistributor.sendToPlayersTrackingChunk(level, chunkPos, command)`）。

包在客户端收到后会自己完成：`FXHelper.getFX(...)` → 建 Executor → 应用 offset/rotation/scale/delay → `start()`。

**什么时候才需要自己写包**：需要在播放**之前**注入运行时参数、需要在客户端做条件判断、
或者要用自定义 Executor / 独立 PostEffectStack。这时自己写一个 `CustomPacketPayload`，
在客户端 handler 里 `ctx.enqueueWork(...)` 回到主线程再操作 Photon。

### 7.7 把游戏状态喂给 shader

两条路，按「谁来写」选：

| 需求 | 用什么 |
|---|---|
| 值由 Timeline 或 Java 每帧驱动 | **Custom Data Stream**（每发射器最多 4 条，每条 `vec4`） |
| 一次性/低频的材质级参数 | **Shader Graph 暴露的参数** / Custom Shader Material 的 uniform |

Custom Data 的运行时覆写接口（发射器 Runtime 上）：

```java
values.customData.slot(stream, channel).set(new Constant(0.85f));
values.customData.slot(stream, channel).clear();     // 回到 Authored 值
```

⚠️ **Stream 的 Index 就是编辑器里的列表顺序**。改显示名不影响 GPU 布局，
但**增删或移动 Stream 会改变后续所有 Index** —— 这时候 shader 里的 `Index` 也得跟着改。

⚠️ **Timeline 与 Java 抢同一个槽时，后写的赢**。Timeline 每 tick/每帧都在写，
所以 Java 一次性 `set()` 常常「看起来没生效」。解法：换一个槽、持续更新，或让一个系统独占该值。

---

## 8. 性能

### 8.1 先建立预算概念

一帧的 GPU 时间被三件事吃掉，按常见严重程度排序：

1. **填充率（Fill Rate）** —— 大面积半透明粒子。粒子数不多但每个都铺满屏幕时，瓶颈在这里。
2. **Draw Call / 状态切换** —— 材质、纹理、渲染层一变就拆批。
3. **顶点与带宽** —— 高面数模型、大量实例数据上传。

CPU 侧则是：粒子模拟（每粒子每 tick 的模块计算）、骨骼动画采样、提交阶段的对象分配。

### 8.2 常见瓶颈与对策

| 瓶颈 | 表现 | 对策 |
|---|---|---|
| 不合批 | draw call 数远大于发射器数 | 统一材质/纹理/渲染层/顶点布局；避免每发射器单独 override |
| override 泄漏 | 用着用着越来越慢 | 用完 `clearRenderOverride()` 回到共享渲染 pass |
| 半透明过绘 | GPU 时间随屏幕覆盖率线性涨 | 减少重叠、降低粒子尺寸、用 `discard` 换成 alpha 混合慎用 |
| 每帧上传 | 带宽打满 | 只在数据真正变化时写 `RuntimeValue`；Custom Data 用稳定 Random Key |
| 粒子模拟 | CPU 单核跑满 | 开启 `parallelUpdate`（无碰撞时）；减少模块数 |
| 骨骼采样 | 角色多时掉帧 | 减少同时可见的角色；本项目已有 GPU 蒙皮路径 |
| 后处理 | 全屏 pass 叠加 | 合并请求、降采样、不要滥用 `Independent` |

### 8.3 三条铁律

1. **不要默认 Instancing 一定更快。** 只有几个粒子时初始化成本占主导；
   几百个相同 quad/model 时收益才明显。用 Profiler 说话。
2. **不要每帧清缓存。** 缓存只应该在资源生命周期事件里清（资源重载会自动清）。
3. **不要给每个实例单独做材质。** 那是把合批彻底掐死的做法。

### 8.4 怎么测

- 用 **Profiler** 看 CPU 侧热点（粒子 tick、渲染层、提交）；
- 用 **Render Statistics / GPU 计时**看 draw call 数与 GPU 时间；
- **用真实的 Max Particle Count 压测**，不要只看编辑器前两秒；
- 对比实验要**一次只改一个变量**（开/关 Instancing、开/关 override、开/关后处理）。

---

## 9. 排错手册

### 9.1 按症状查

| 症状 | 最可能的原因 | 检查点 |
|---|---|---|
| 特效/模型**跟着镜头跑** | 坐标系搞错 | 有没有把相机相对坐标当世界坐标 |
| 特效在天边/地下 | 反过来，多加了相机位置 | 同上，看是否重复相加 |
| 旋转差 ~57 倍 | 角度/弧度混用 | `setRotation`（度）vs `updateRotation`（弧度） |
| 骨骼特效位置对不上 | 用错骨骼 / 偏移轴向不对 | 骨骼名；`WEAPON_TIP_OFFSET` 与轴向 |
| 骨骼特效不动 | 骨骼没渲染 / 缓存过期 / `worldPos` 为 null | 第一人称？离屏？是不是用了 `addBonePositionListener` 却没人写 `POSITION` |
| 特效**完全不出现** | FX id 写错或资源没导出 | `assets/<ns>/fx/<path>.fx`；日志里的加载失败 |
| 改了导出文件没生效 | 定义缓存 | `/photon_client clear_client_fx_cache` |
| 切世界后特效不回来 | 没有重建逻辑 | 是否用 `isValid()` 判断并重建 |
| Java 设的值没生效 | 被 Timeline 覆盖同一个槽 | 换槽 / 持续更新 / Mute 掉那条属性 |
| 模型凭空少一块 | 隐藏了骨骼但内容画不出来 | 「隐藏」和「绘制」必须成对，解析失败就别隐藏 |
| shader 全黑 | 编译失败或输入全 0 | 常量颜色 → 输出；再看编译日志 |
| 数据恒为 0 | 通道没启用 / 渲染路径不支持 | 不支持的通道返回 0，不报错 |
| 两个渲染层互相打架 | 骨骼显隐是共享布尔 | 动手前先读一次当前状态 |
| `BoneUpdater` 不生效 | 加晚了 | 必须在 `captureModelRenderPose` 之前加 |
| `getModelRenderMatrixState` 抛异常 | 提交前调用了 | 只能在 `submitRenderTasks` 之后调 |
| 换了渲染器偏半格 | `GeoObjectRenderer` 默认半格平移 | 覆写 `adjustRenderPose` |

### 9.2 Photon 的调试命令

| 命令 | 用途 |
|---|---|
| `/photon_editor` | 打开编辑器（仅单人世界） |
| `/photon fx <id> block …` / `entity …` | 用命令验证导出文件本身 |
| `/photon fx remove …` | 移除绑定 |
| `/photon_client clear_particles` | 清粒子 + Executor 缓存，并使已缓存 Runtime 失效 |
| `/photon_client clear_client_fx_cache` | 清 FX 定义缓存与列表缓存 |
| `/photonfx list` / `test <effect> [weight]` / `clear` | 后处理调试 |
| `/photon_iris status` / `dump` | Iris 兼容诊断 |

**分诊法**：命令能播而 Java 不能播 → 问题一定在 id / 命名空间 / 客户端线程 / 客户端侧隔离上。

### 9.3 客户端 / 服务端隔离

这是**唯一会让专用服务器崩掉**的一类错误：

> 公共包（`core/`、`content/`）**不可以**直接引用客户端类。

正确做法是加一层纯公共的通知点，客户端在自己初始化时注册监听：

```java
// 公共侧
public final class SkillCastHooks {
    @FunctionalInterface public interface Listener { void onSkillCast(Player player, int skillType); }
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    public static void register(Listener l) { if (l != null && !LISTENERS.contains(l)) LISTENERS.add(l); }
    public static void unregister(Listener l) { LISTENERS.remove(l); }
    public static void fire(Player player, int skillType) { LISTENERS.forEach(l -> l.onSkillCast(player, skillType)); }
}

// 客户端初始化里
SkillCastHooks.register(TestCharacterFx::onSkillCast);
```

专用服务器上没有任何监听器，`fire` 就是一次空循环。

**另一个坑**：`@EventBusSubscriber` 注册的类如果方法是游戏总线事件，NeoForge 会自动判定；
但本项目里有一条既有约定 —— 游戏总线事件**显式注册**更稳妥：

```java
NeoForge.EVENT_BUS.addListener(TestCharacterFx::onClientTick);
```

---

## 10. 附录

### 10.1 类名速查

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

### 10.2 术语中英对照

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

### 10.3 参考

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

### 10.4 本文未覆盖 / 未确认的部分

诚实起见，列一下没写透的：

- **Photon 内部渲染管线的逐行细节**（`PhotonStage` 的取值、DrawJob 的收集与合批算法、
  延迟层与 Iris 的交互）——本文给了架构与结论，没有逐方法展开。
- **`RenderUtil.renderPoseToPosition` 的 `modelPos` 中 x 取负的根因**：
  只能从 `BoneSnapshot.translate` 与 `GeoBone.translateToPivotPoint` 的行为反推是
  「Blockbench 模型空间与世界空间 x 轴反向」，没有在 GeoJSON 加载器里逐行核对。
- **`render-asset.md` 里项目的资源路径规则**（`GenshinAssets` 的 fallback 机制）本文只做了引用。
- **Shimmer/Iris 的完整兼容矩阵**：本文只写了「分别测试、用 `/photon_iris dump`」。

如果你在核对本文时发现某一条与源码不符，**以源码为准**，并请顺手改掉本文 —— 这正是这份文档存在的意义。
