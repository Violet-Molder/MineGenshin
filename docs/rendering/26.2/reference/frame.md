# 1. 心智模型：26.2 的一帧是怎么画出来的


## 1.1 三段式：提取 → 提交 → 绘制

1.21 之前的写法是「拿到实体 → 直接往 `BufferBuilder` 塞顶点 → 立刻画」。26.2 已经把这件事拆成三段，
各段之间只通过**纯数据**交接：

| 段 | 干什么 | 谁在跑 | 拿到什么、产出什么 |
|---|---|---|---|
| 提取 extract | 把「这一帧要画什么」从游戏对象里摘出来，写成 `*RenderState` | `GameRenderer#extract` → `LevelExtractor#extract` | 输入世界/实体/相机，输出 `LevelRenderState`（含 `entityRenderStates` 列表） |
| 提交 submit | 遍历 `RenderState`，把几何**登记**进提交收集器；此时还不绘制 | `LevelRenderer#submitEntities` 等 | 输入 `LevelRenderState` + `PoseStack` + `SubmitNodeCollector`，产出「待绘制节点」 |
| 绘制 draw | 按管线/渲染类型排序后成批发绘制命令 | 渲染管线内部，走 `RenderPass#drawIndexed` / `draw` | 输入已排好的批次，输出实际 GPU 命令 |

**为什么要这么拆**：排序与合批需要先看到全帧的几何。
如果像 1.21 那样「边算边画」，一个半透明的粒子和一个不透明的手持物品谁先画就只能按调用顺序决定，
而正确做法是按渲染类型与深度排序——那必须等整帧的几何都登记完。

对你的直接影响只有一条，但它是本文后面所有坐标问题的根源：

> **提交阶段的 `PoseStack` 顶点坐标是「相机相对」的，不是世界坐标。**

证据就在提交循环里（`net/minecraft/client/renderer/LevelRenderer.java:724-733`）：

```java
private void submitEntities(PoseStack poseStack, LevelRenderState levelRenderState, SubmitNodeCollector output) {
    Vec3 cameraPos = levelRenderState.cameraRenderState.pos;
    double camX = cameraPos.x();
    double camY = cameraPos.y();
    double camZ = cameraPos.z();

    for (EntityRenderState state : levelRenderState.entityRenderStates) {
        this.entityRenderDispatcher.submit(state, levelRenderState.cameraRenderState,
                state.x - camX, state.y - camY, state.z - camZ, poseStack, output);
    }
}
```

世界坐标被减去相机坐标后才交给渲染器。所以任何「我要把东西放在世界的某个位置」的代码，
都必须自己把相机位置加回去（写法见 [§1.5](#15-相机空间提交几何时-posestack-的原点是相机) 与
[§6.2](/doc/rendering-26.2-reference-transform#62-关键结论提交阶段-posestack-的原点是相机)）。

## 1.2 一帧的真实顺序

`GameRenderer` 把「提取」和「渲染」分成了两个方法，26.2 的客户端主循环会先后调用它们：

```java
public void extract(DeltaTracker deltaTracker, boolean advanceGameTime) {   // GameRenderer.java:383
    boolean readyForLevelRendering = this.minecraft.isGameLoadFinished() && advanceGameTime && this.minecraft.level != null;
    this.extractWindow();
    this.extractOptions();
    if (readyForLevelRendering) {
        this.lightmapRenderStateExtractor.extract(this.gameRenderState.lightmapRenderState, 1.0F);
        this.extractCamera(deltaTracker, worldPartialTicks, cameraEntityPartialTicks);
        this.minecraft.levelExtractor.extract(deltaTracker, this.mainCamera, worldPartialTicks);  // 实体/方块实体/粒子都在这
    }
    this.minecraft.gui.extractRenderState(deltaTracker, readyForLevelRendering, resourcesLoaded);
}

public void render(DeltaTracker deltaTracker, boolean advanceGameTime) {    // GameRenderer.java:400
    RenderSystem.getDevice().createCommandEncoder()
            .clearColorAndDepthTextures(mainRenderTarget.getColorTexture(), ...);       // 先清屏
    this.globalSettingsUniform.update(..., this.gameRenderState.levelRenderState.cameraRenderState.pos, ...);
    if (shouldRenderLevel) {
        this.lightmap.render(this.gameRenderState.lightmapRenderState);
        this.renderLevel(deltaTracker);                       // 天空 → 区块 → 实体 → 粒子 → 天气
        this.tryTakeScreenshotIfNeeded();
        this.minecraft.levelRenderer.doEntityOutline();
        if (this.postEffectId != null && this.effectActive) { // 原版后处理（如旁观者模式）
            this.minecraft.getShaderManager().getPostChain(this.postEffectId, LevelTargetBundle.MAIN_TARGETS)
                    .process(this.mainRenderTarget, this.resourcePool);
        }
    }
}
```

`renderLevel` 内部再由 `LevelRenderer` 依次做：天空 → 不透明区块 → 不透明 features（实体、方块实体、粒子）
→ 半透明 features → 半透明区块 → 半透明粒子 → 天气。每个阶段之间都有对应的 NeoForge 子事件（见 §1.6）。

## 1.3 线程模型：客户端主线程**就是**渲染线程

26.2 里没有独立的渲染线程。客户端启动时把主线程直接改名并注册成渲染线程
（`net/minecraft/client/main/Main.java:278-279`）：

```java
Thread.currentThread().setName("Render thread");
RenderSystem.initRenderThread();
```

所以：

- **实体 tick、GUI 事件、提取、提交、绘制跑在同一个线程上**，只是不同阶段。
- 判断自己在不在渲染线程上，用 `RenderSystem.isOnRenderThread()` / `RenderSystem.assertOnRenderThread()`
  （`com/mojang/blaze3d/systems/RenderSystem.java:88-92`）——写渲染代码时把它们当断言用。
- 在渲染阶段做阻塞 IO（读文件、等网络）会直接卡住整个客户端帧，没有「后台渲染线程」替你兜底。
  贴图/模型要提前加载好，渲染时只查缓存。

## 1.4 渲染状态（RenderState）的生命周期

| 状态对象 | 位置 | 谁建、活多久 |
|---|---|---|
| `LevelRenderState` | `net/minecraft/client/renderer/state/level/LevelRenderState.java` | 每帧提取阶段重建，帧末即废 |
| `EntityRenderState` | `net/minecraft/client/renderer/state/EntityRenderState.java` | 每帧为每个要画的实体重建 |
| `CameraRenderState` | `net/minecraft/client/renderer/state/level/CameraRenderState.java` | 每帧重建，`pos` 就是本帧相机世界坐标 |

三条纪律：

1. **状态里只放纯数据**：坐标、朝向、颜色、光效参数。放 `Entity`、`Level` 这类对象引用，
   轻则在下一帧读到过期的世界，重则在渲染线程上触发并发访问。
2. **不要跨帧持有**：对象下一帧会被重新创建，你手里的引用很快就不是当前帧的了。
3. **自定义数据要在提取阶段摘**：只有 `ExtractLevelRenderStateEvent`（§1.6）里拿得到「这一帧的世界」，
   到渲染阶段再去问世界就已经晚了，而且会踩到线程/生命周期问题。

## 1.5 相机空间：提交几何时 `PoseStack` 的原点是相机

两个方向的换算（`Vec3` 是世界坐标，`Vector3f` 是相机空间）：

```java
// 世界坐标 → 提交时用的坐标
Vec3 world = new Vec3(120.5, 64.0, -330.25);
Vec3 camera = cameraRenderState.pos;                       // CameraRenderState#pos
double relX = world.x - camera.x;
double relY = world.y - camera.y;
double relZ = world.z - camera.z;
poseStack.translate(relX, relY, relZ);

// 提交时拿到的位姿 → 世界坐标（在渲染回调里）
Matrix4f bonePose = new Matrix4f(info.poseStack().last().pose());
Vector3f local = bonePose.getTranslation(new Vector3f());   // 相机空间平移
Vec3 worldPos = new Vec3(camera.x + local.x, camera.y + local.y, camera.z + local.z);
```

忘了这一步的典型症状：模型/特效**死死跟着镜头**，你往前走它也跟着走，看起来像贴在屏幕上。
反过来的症状是「东西在世界的正确位置，但视角一转就跑」——那通常是把相机相对坐标又减了一次。

> 注意：`PoseStack` 里累积的还有实体自身的旋转/缩放。要拿「实体局部坐标 → 世界坐标」，
> 必须像 §10.3 那样在**骨骼回调**里取 `poseStack.last().pose()`，而不是自己拼矩阵。

## 1.6 NeoForge 挂载点：什么时候插自己的代码

26.2 的 `RenderLevelStageEvent` **没有 `Stage` 枚举**，改用一组子事件，它们是普通事件总线事件、不可取消
（`neoforge-src:net/neoforged/neoforge/client/event/RenderLevelStageEvent.java:35-43` 的类注释明确列了顺序）：

| 顺序 | 子事件 | 什么时候触发 |
|---|---|---|
| 1 | `RenderLevelStageEvent.AfterSky` | 天空画完 |
| 2 | `AfterOpaqueBlocks` | 不透明区块几何画完 |
| 3 | `AfterOpaqueFeatures` | 不透明实体/方块实体/粒子画完 |
| 4 | `AfterTranslucentFeatures` | 半透明实体/方块实体画完 |
| 5 | `AfterTranslucentBlocks` | 半透明区块画完 |
| 6 | `AfterTranslucentParticles` | 半透明粒子这一批也画完了 |
| 7 | `AfterWeather` | 天气画完（世界边界之前） |
| 8 | `AfterLevel` | 整个关卡画完（最后一个） |

另外四个必须知道的挂载点：

| 事件/类 | 位置 | 用途 |
|---|---|---|
| `RenderFrameEvent.Pre` / `.Post` | `neoforge-src:.../event/RenderFrameEvent.java` | 渲染帧边界；本项目在这里推进「渲染帧号」（见 §10.3） |
| `ExtractLevelRenderStateEvent` | `neoforge-src:.../event/ExtractLevelRenderStateEvent.java` | 提取自定义渲染状态；提供 `getRenderState()`、`getLevel()`、`getCamera()`、`getFrustum()`、`getDeltaTracker()` |
| `SubmitCustomGeometryEvent` | `neoforge-src:.../event/SubmitCustomGeometryEvent.java` | **不写渲染器也能提交几何**的正规入口：`getSubmitNodeCollector()`、`getPoseStack()`、`getLevelRenderState()`、`getRenderableSections()` |
| `RegisterRenderPipelinesEvent` | `neoforge-src:.../event/RegisterRenderPipelinesEvent.java` | 注册自定义 `RenderPipeline`；`registerPipeline(RenderPipeline)`，**事件总线是 mod 总线**、仅客户端 |

`ExtractLevelRenderStateEvent` 的类注释还写了一句关键约束：想往 `SubmitNodeCollector` 提交自定义几何，
请用 `SubmitCustomGeometryEvent`，不要在提取事件里做。

## 1.7 案例：把一条线钉在世界坐标的一个点上

**目标**：在坐标 `(120.5, 64.0, -330.25)` 画一条长 2 格的竖线，无论玩家怎么走、怎么转视角，它都钉在那里。

**思路**：这是「提交层」的最小完整案例。三次转换一次都不能少 ——
世界坐标 →（减去相机位置）→ 提交用坐标 →（`PoseStack` 原点就是相机）→ 屏幕。

```java
// 事件监听（客户端、NeoForge 事件总线）
@SubscribeEvent
static void onSubmit(SubmitCustomGeometryEvent event) {
    LevelRenderState state = event.getLevelRenderState();
    Vec3 camera = state.cameraRenderState.pos;                 // 本帧相机世界坐标
    Vec3 target = new Vec3(120.5, 64.0, -330.25);              // 你想要的世界坐标

    PoseStack pose = event.getPoseStack();
    pose.pushPose();
    // 关键一步：提交阶段的 PoseStack 原点是相机，所以这里减的是相机位置

    // 写法 1（手算各分量，更显式）：
    pose.translate(target.x - camera.x, target.y - camera.y, target.z - camera.z);
    // 写法 2（Vec3.subtract，更简洁）：
    // pose.translate(target.subtract(camera));

    event.getSubmitNodeCollector().submitCustomGeometry(pose, RenderTypes.LINES,
            (p, consumer) -> {
                consumer.addVertex(p, 0f, 0f, 0f).setColor(0xFF33CCFF).setNormal(0f, 1f, 0f).setLineWidth(3f);
                consumer.addVertex(p, 0f, 2f, 0f).setColor(0xFF33CCFF).setNormal(0f, 1f, 0f).setLineWidth(3f);
            });
    pose.popPose();
}
```

三个必须注意的细节：

1. **只在最外层减一次相机位置**。写到顶点里的坐标是「相对目标点」的局部坐标（这里是 `0,0,0 → 0,2,0`），
   相机换算只做一次。做两次的典型症状是「离原点越远、偏移越大」，做零次就是「贴着镜头跑」。
2. **不要在这个回调里访问 `Level`/`Entity`**。需要什么数据要在提取阶段（`ExtractLevelRenderStateEvent`）
   摘好，或者从 `LevelRenderState` 里读现成的字段；渲染期拿世界对象等于把线程安全问题留给自己。
3. **`RenderTypes.LINES` 的顶点格式是 `POSITION_COLOR_NORMAL_LINE_WIDTH`**
   （见 `RenderPipelines.java:118-127` 的 `LINES_SNIPPET`），所以属性要按 `位置 → 颜色 → 法线 → 线宽`
   依次给全；漏掉 `LineWidth` 线的粗细就会变成未定义值。

**怎么验证**：

1. 站到那条线附近左右平移，线应该稳稳钉在方块边缘上（跟着镜头跑 = 相机换算没做）。
2. 记下坐标，跑到 100 格外再看，线的位置不变（越远越偏 = 相机换算做了两次）。
3. 切换维度、切到第一人称、把窗口缩小，都不应让线抖动或消失。

**如果你想画方块面片而不是线**：把 `RenderTypes.LINES` 换成对应的面片渲染类型（例如
`RenderTypes.debugFilledBox()` 这类现成工厂，具体名以 `RenderTypes.java` 为准），并把顶点按
`PrimitiveTopology.QUADS`（4 个顶点一个面）组织。其余步骤完全一样。
