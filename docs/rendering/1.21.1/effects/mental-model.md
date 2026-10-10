# 1. 先建立正确的心智模型

> **本文针对的版本**：Minecraft `1.21.1` · NeoForge `21.1.250` · Java `21` · Photon `2.2.8` · LDLib2 `2.2.42` · GeckoLib `4.9.3`
>
> **本文与 26.2 版的《[渲染与 Photon2 特效](rendering-and-photon2.md)》的关系**：同一套主题的两条技术线。26.2 那边是「三段式」
> （提取渲染状态 → 提交几何 → 统一渲染），1.21.1 这边仍然是**立即模式**：算完直接往顶点缓冲里写，
> `RenderType` 一收尾就画。两篇不能互相照抄 —— 同一件事的写法几乎都不一样。
>
> **事实来源**：本机 Gradle 缓存里的 `neoforge-21.1.250-sources.jar`、
> `photon-neoforge-1.21.1-2.2.8-sources.jar`、`ldlib2-neoforge-1.21.1-2.2.42-sources.jar`、
> `geckolib-neoforge-1.21.1-4.9.3-sources.jar`，以及本项目 1.21.1 分支的源码。
> **与官方文档站不一致时以 jar 为准。**

---


## 1.1 1.21.1 的渲染是「立即模式」

26.2 的渲染被拆成三段：先在客户端逻辑里把渲染需要的数据提取成 RenderState，再提交成渲染任务，
最后统一渲染。1.21.1 **没有这一层**，它的模型是三件事直接串起来：

1. **拿一个顶点消费者**：`MultiBufferSource#getBuffer(RenderType)`；
2. **写顶点**：`VertexConsumer#addVertex(...)` 一整套（位置 / 颜色 / UV / 光照 / 法线）；
3. **收尾**：`MultiBufferSource.BufferSource#endBatch()` 把这一批顶点交给 `RenderType`
   绑定的着色器画出去。

`RenderType` 是这一切的枢纽：它同时决定顶点格式、着色器、混合 / 深度 / 剔除状态、贴图。
写进去的顶点，最终由**这一帧里 `RenderType` 携带的那份状态**画出来 ——
所以「先写顶点、后面再改状态」是无效的，状态必须在 `endBatch` 那一刻是对的。

```
立即模式（1.21.1）
  拿 VertexConsumer → 写顶点 → endBatch → 画

三段式（26.2）
  extract → submit → render
```

两个必须记住的推论：

- **没有任何「提交列表」可以事后修改**：写进 `BufferBuilder` 的顶点当场定型。要在别处补画，
  就把顶点写进另一个 `RenderType` 的缓冲，或者换个阶段再画一遍。
- **动画数据要在写顶点之前就拿到**：1.21.1 的骨骼位姿是「取出来、算一次、写进顶点」，
  写完之后那条骨骼的矩阵就和你无关了。

## 1.2 一帧里到底按什么顺序发生

`LevelRenderer#renderLevel(...)`（`net/minecraft/client/renderer/LevelRenderer.java:914`）
是这一帧的总调度，主要顺序如下（行号取自 `neoforge-21.1.250-sources.jar`）：

| 顺序 | 做什么 | 源码位置 |
| --- | --- | --- |
| 1 | 帧开始、清屏、设置雾与相机 | `renderLevel` 开头 |
| 2 | 画天空 | `renderSky(...)` `:1587` |
| 3 | 画固体 / mipped cutout / cutout 三批区块 | `renderSectionLayer(RenderType.solid())` `:965` 起 |
| 4 | 画实体 | `renderLevel` 中段（`EntityRenderDispatcher`） |
| 5 | 画半透明方块与 tripwire | `:1177`、`:1196` |
| 6 | 画粒子 | `ParticleEngine#render` |
| 7 | 画云 | `renderClouds(...)` `:1712` |
| 8 | 天气、手、GUI | 之后由 `GameRenderer` 继续 |

要在这些位置之间插自己的渲染，1.21.1 上用的是**事件**，不是 26.2 那种阶段枚举：

```java
@SubscribeEvent
static void onRenderLevelStage(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
    PoseStack poseStack = event.getPoseStack();
    var camera = event.getCamera();
    var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
    // 在这里写顶点 / 生成粒子
}
```

`RenderLevelStageEvent.Stage` 在 1.21.1 上的可用值
（`net/neoforged/neoforge/client/event/RenderLevelStageEvent.java:158` 起）：

`AFTER_SKY`、`AFTER_SOLID_BLOCKS`、`AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS`、`AFTER_CUTOUT_BLOCKS`、
`AFTER_ENTITIES`、`AFTER_BLOCK_ENTITIES`、`AFTER_TRANSLUCENT_BLOCKS`、`AFTER_TRIPWIRE_BLOCKS`、
`AFTER_PARTICLES`、`AFTER_WEATHER`、`AFTER_LEVEL`。

Photon 2.2.8 自己就挂在 `AFTER_BLOCK_ENTITIES` 与 `AFTER_PARTICLES` 两个阶段上
（`com/lowdragmc/photon/client/PhotonClientListeners.java:102`），见第 6 节。

## 1.3 渲染发生在哪个线程

渲染只在渲染线程（客户端主线程）上发生：tick 与渲染跑在同一个线程里。1.21.1 的差别是
`RenderSystem` 提供了一组「稍后再执行」的入口：`RenderSystem.recordRenderCall(...)`
（`com/mojang/blaze3d/systems/RenderSystem.java:127`），`setShaderColor` / `setShaderTexture`
这类状态设置在非渲染线程调用时会自动走它。

实践结论：

- 可以在 tick 里准备数据（算位置、挑特效），但**写顶点、绑贴图、切 `RenderType` 都在渲染回调里做**。
- 服务端不能碰渲染类。发光 / 粒子 / 特效要么纯客户端判断，要么用网络包把「意图」传过来。

---
