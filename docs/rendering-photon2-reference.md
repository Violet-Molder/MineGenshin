# Minecraft 26.2 渲染与 Photon2 完全参考

> **适用版本**：Minecraft `26.2` · NeoForge `26.2.0.88` · Java `25` · Photon2 `26.2.2.3` · LDLib2 `26.2.2.41.a` · GeckoLib `5.5.6`（本仓库 1.0.1 的依赖）
>
> **这份文档是什么**：把「26.2 的渲染管线怎么运转」和「Photon2 特效怎么接进模组」两件事一次讲完，
> 面向**要动手写代码的人**：自定义渲染层、粒子与光束、后处理、骨骼挂点、特效触发链路。
>
> **核对方式**：正文里的类名、方法名、事件名都能在本机源码里搜到，出处写在对应章节里；
> 关键的几处核对入口在 [§0.3](#03-怎么核对本文的每条说法)。凡是没核对到的结论都会显式标注「未确认」，
> 不会用「大概」「应该是」糊过去。

## 0. 导读

### 0.1 版本矩阵

| 组件 | 版本 | 在本文里的角色 |
|---|---|---|
| Minecraft | 26.2 | 渲染管线与 `RenderState` 体系的事实标准 |
| NeoForge | 26.2.0.88 | 渲染事件的来源（`RenderLevelStageEvent` 等） |
| Java | 25 | 工具链 |
| Photon2 | 26.2.2.3 | 特效引擎（FX / 粒子 / 后处理） |
| LDLib2 | 26.2.2.41.a | Photon2 的 UI 与基础设施依赖 |
| GeckoLib | 5.5.6 | 骨骼动画与模型渲染 |

### 0.2 三条主线与推荐阅读路线

全文分三块，按你的目的挑路线读，不必从头看到尾：

| 你的目的 | 建议路线 |
|---|---|
| 写一个特效，接到角色骨骼上 | 1 → 8 → 9 → 10（再回 6 查坐标换算） |
| 写自定义渲染层 / 自定义几何 | 1 → 2 → 3 → 4 → 5 |
| 模型方向不对、位置乱飞 | 6（症状对照表） |
| 掉帧、特效一多就卡 | 7 → 11 |
| 打包后特效丢失 / 多人不同步 | 8 → 11 |

三条主线分别是：

1. **渲染管线**（第 1–3 章）：一帧的提取 / 提交 / 绘制三段，Blaze3D 的 API 地图，着色器与 GPU 数据。
2. **模型与骨骼**（第 4–7 章）：实体渲染状态、GeckoLib 5 的渲染层与骨骼快照、坐标与矩阵、GPU 蒙皮与性能。
3. **Photon2**（第 8–11 章）：从编辑器资产到运行时对象、Java API、本项目的真实接线、排错与性能。

### 0.3 怎么核对本文的每条说法

本文的每个技术结论都来自三处之一，你可以顺着查：

| 证据 | 位置 |
|---|---|
| Minecraft + NeoForge 源码 | `build/moddev/artifacts/minecraft-patched-26.2.0.88-sources.jar` |
| Photon2 源码 | `~/.gradle/caches/modules-2/files-2.1/com.lowdragmc.photon/*/*-sources.jar` |
| GeckoLib 源码 | `~/.gradle/caches/modules-2/files-2.1/com.software.bernie.geckolib/*/*-sources.jar` |
| 本项目真实用法 | `src/main/java/com/linweiyun/genshin/**`（正文里给到文件与行号） |

在源码 jar 里查一个类是否真的存在（不用解压整包）：

```powershell
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead("build\moddev\artifacts\minecraft-patched-26.2.0.88-sources.jar")
$zip.Entries | Where-Object { $_.FullName -like "*SubmitNodeCollector*" } | Select-Object -ExpandProperty FullName
```

本文里出现的「**未确认**」标记，表示作者没有在源码里找到直接证据，只能给出经验判断；
贴进项目前请自己核一遍。

### 0.4 与仓库里其它文档的关系

| 文档 | 定位 |
|---|---|
| 本文 | 渲染 + Photon2 的完整参考（按 26.2 源码重写） |
| [资源、渲染与界面](/doc/sys-render-asset) | 本项目的资源路径规则、外观掩码、界面注册方式 |
| `docs/rendering-and-photon2.md` | 同期整理的渲染 + Photon2 说明（篇幅接近，结论没有逐条源码核对） |
| `docs/rendering_guide_for_photon2.md` | 早期草稿：面向 1.21 时代的 Blaze3D API，**其中 `RenderSystem.setShader`、`Tesselator.end()` 一类写法在 26.2 已不存在** |
| `docs/photon2-java-guide.md` | Photon2 的 Java 使用指南（另一篇视角，可对照） |
| [性能优化系统](/doc/sys-performance) | 本项目的性能预算与已有优化 |
| [实体 AI 指南](/doc/entity-ai) | 实体行为侧（与渲染无关时的入口） |

> **一句话结论**：如果你只想记住三件事 ——
> ① 26.2 的渲染是「提取 → 提交 → 绘制」三段式，提交时 `PoseStack` 的原点是**相机**；
> ② 所有跨帧保留的游戏对象都不能塞进渲染状态，渲染状态每帧重建；
> ③ Photon2 的特效是**客户端**的运行时对象，服务端只能通过 payload 请求它生成。

## 1. 心智模型：26.2 的一帧是怎么画出来的

### 1.1 三段式：提取 → 提交 → 绘制

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
[§6.2](#62-关键结论提交阶段-posestack-的原点是相机)）。

### 1.2 一帧的真实顺序

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

### 1.3 线程模型：客户端主线程**就是**渲染线程

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

### 1.4 渲染状态（RenderState）的生命周期

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

### 1.5 相机空间：提交几何时 `PoseStack` 的原点是相机

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

### 1.6 NeoForge 挂载点：什么时候插自己的代码

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

### 1.7 案例：把一条线钉在世界坐标的一个点上

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

## 2. Blaze3D API 地图

### 2.1 分层总览

26.2 的 Blaze3D 按「谁拥有什么」可以分成四层，写代码时先想清楚自己在哪一层：

| 层 | 代表类 | 你什么时候碰它 |
|---|---|---|
| 提交层 | `SubmitNodeCollector` / `OrderedSubmitNodeCollector` | 想画自定义几何、往帧里塞东西 |
| 管线层 | `RenderPipeline` / `CompiledRenderPipeline` / `RenderSetup` / `RenderType` | 想把「怎么画」定死：着色器、混合、深度、顶点格式 |
| 顶点层 | `VertexFormat` / `VertexFormatElement` / `VertexConsumer` / `MeshData` | 想定义自己的顶点长什么样 |
| 设备层 | `GpuDevice` / `CommandEncoder` / `RenderPass` / `GpuBuffer` / `GpuBufferSlice` | 想自己开缓冲、传 uniform、发绘制命令 |

### 2.2 `PoseStack` —— 坐标变换栈

**`PoseStack`** 解决一个核心问题：**"这个顶点要画在屏幕上的哪里？"** 它在帧的提交阶段使用，初始原点永远等于**本地玩家相机位置**。

它内部维护了一摞**4×4 变换矩阵**，每层记录一次累积的平移/旋转/缩放。典型用法是四步走：

```
pushPose()  →  translate() → mulPose()/scale()  →  popPose()
  ↑ 保存当前状态      ↑ 移到目标位置     ↑ 调整朝向/大小       ↑ 恢复之前的状态
```

---

#### `pushPose()` / `popPose()` —— 状态保存与恢复

**`pushPose()`** 把当前栈顶的变换矩阵**复制一份压到栈上**。从此之后你做的所有 `translate` / `scale` / `mulPose` 都只影响新栈顶，不影响下面的。

**`popPose()`** 丢弃栈顶，恢复到 `pushPose()` 之前的状态。

> **两条铁律：**
> 1. **必须成对出现**——`LevelRenderer.checkPoseStack` 会在帧末检查栈是否为空，多推一次直接抛 `IllegalStateException("Pose stack not empty")`。
> 2. **每画一个独立物体，包一组 `pushPose → ... → popPose`**。不要画了五样东西才弹一次。
> 3. 不能在 `pushPose` 之前 `popPose`，会抛 `ArrayIndexOutOfBoundsException`。

**很多新手困惑「几何不是丢了吗？」**

不会丢。`submitCustomGeometry` / `submitModel` 在 `popPose` 之前就已经把几何**登记进帧的提交收集器**了——几何数据已经不在 PoseStack 手里。`popPose` 丢掉的是**变换矩阵**，不是画好的图案。

可以想象成在一张纸上画画：`pushPose` = 复印一张新纸，`translate`/`scale` = 挪动复印纸的位置。画完（submit）后把复印纸的图案取走（几何已提交），然后 `popPose` = 扔掉这张挪过的复印纸，拿出原来的那张——纸回到原位，方便画下一个东西。

**项目中的例子**（`MobHealthBarHud.java:235-385`）：
```java
// 画多行血条，每行一个独立变换
for (int i = 0; i < bars; i++) {
    poseStack.pushPose();                     // 保存当前
    poseStack.translate(relX, baseY, relZ);   // 移到第 i 行位置
    poseStack.mulPose(camRot);                // 始终面向相机
    poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));  // 翻正
    // ... 画血条
    poseStack.popPose();                      // 恢复，不影响下一行
}
```

---

#### `translate(x, y, z)` —— 平移

把坐标系原点从当前位置移动 `(x, y, z)` 距离。

有三种重载：
```java
void translate(double xo, double yo, double zo);   // double 精度
void translate(float xo, float yo, float zo);       // float 精度（更常用）
void translate(Vec3 offset);                         // Vec3 快捷方式
```

**在提交阶段**，平移的值必须是**相对相机的偏移**。因为 `PoseStack` 原点 = 相机位置，所以：

```java
Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
poseStack.translate(
    (float)(worldX - camera.x),
    (float)(worldY - camera.y),
    (float)(worldZ - camera.z)
);
```

**项目中的例子**（`VesnaAttackProjectileRenderer.java:71`）：
```java
// 把弹射物纹理抬高 0.1 格，避免嵌入地面
poseStack.translate(0.0F, 0.1F, 0.0F);
```

**项目中的例子**（`VesnaEnergyHud.java:157`）：
```java
// 把 HUD 图标放在玩家头顶的某个相对位置
Vec3 relative = basePos.subtract(cameraPos);
poseStack.translate(relative.x, relative.y, relative.z);
```

> **常见的坑**：忘了减相机位置，特效会贴在镜头上跟着跑。
> 更坑的是，相机在 `(0,0,0)` 时测试不出问题——因为减去 0 等于没减。

---

#### `scale(x, y, z)` —— 缩放

沿三个轴分别缩放。缩放中心是当前的坐标系原点。

```java
void scale(float xScale, float yScale, float zScale);
```

- `xScale`：X 轴放大倍数。1 = 不放大，2 = 两倍大，0.5 = 一半大
- `yScale` / `zScale`：同上。设为负数可以翻转（比如 `-1` 沿轴镜像）

**同时更新法线矩阵**（`Pose.normal()`），非等比缩放（如 `scale(1, 2, 1)`）后法线不会乱。

**项目中的例子**（`MobHealthBarHud.java:527`）：
```java
// 血条大小：水平方向 2 倍，垂直方向 -2 倍（反转 Y 轴，因为 GUI 坐标 Y 向下）
poseStack.scale(2.0f, -2.0f, 2.0f);
```

**项目中的例子**（`VesnaAttackProjectileRenderer.java:73`）：
```java
// 把弹射物纹理缩小到 30%
poseStack.scale(0.3F, 0.3F, 0.3F);
```

> **结合 `translate` 的坑**：`translate(1,0,0)` 后再 `scale(2,2,2)`，
> 缩放中心在 `(1,0,0)` 而不是 `(0,0,0)`——因为变换是后乘，
> 先平移到了 1，缩放是以 1 为中心缩放原本的顶点。
> 想让物体在原点放大后移动到 1，正确的顺序是：`scale → translate`。

---

#### `mulPose(Quaternionfc)` —— 旋转

绕任意轴旋转，使用四元数描述旋转量。

```java
void mulPose(Quaternionfc by);       // 用四元数旋转
void mulPose(Matrix4fc matrix);      // 乘任意 4×4 变换矩阵
```

常见旋转方式（`org.joml.Quaternionf`）：
```java
import org.joml.Quaternionf;
import com.mojang.math.Axis;

// 绕 Y 轴转 180°（把背面翻过来）
poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));

// 绕 X 轴转 45°
poseStack.mulPose(Axis.XP.rotationDegrees(45.0F));

// 直接用实体朝向
poseStack.mulPose(entityRenderState.rotation);

// 面向相机（Billboard 效果）
poseStack.mulPose(cameraRenderState.orientation);
```

**项目中的例子**（`VesnaAttackProjectileRenderer.java:72`）：
```java
// 弹射物始终面向玩家相机
poseStack.mulPose(camera.orientation);
```

**项目中的例子**（`MobHealthBarHud.java:238`）：
```java
// 血条始终面对玩家，并且翻转为正面朝外
poseStack.mulPose(camRot);                              // 面向相机
poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));    // 翻正
```

**项目中的例子**（`CharacterConfigPage.java:92`）：
```java
// 预览角色在配置界面里转 180°，让正面朝外
poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
```

> **变换是后乘的**，也就是**后调用的变换先作用在顶点上**：
> ```java
> poseStack.translate(1, 0, 0);                      // 第 1 步：平移
> poseStack.mulPose(Axis.YP.rotationDegrees(90));    // 第 2 步：旋转
> // 顶点实际经历的变换顺序是：先旋转 → 再平移
> // 结果是：在 (1,0,0) 处有一个转了 90° 的物体
> ```
> 写模型位移时的惯例顺序是 `translate`（到挂点）→ `mulPose`（朝向）→ `scale`（大小）。

---

#### `rotateAround(Quaternionfc, float px, float py, float pz)` —— 绕指定点旋转

以世界坐标上 `(px, py, pz)` 为旋转中心旋转，而不是以坐标系原点。

```java
void rotateAround(Quaternionfc rotation, float pivotX, float pivotY, float pivotZ);
```

等效的手动实现：
```java
// rotateAround(q, px, py, pz) 等价于：
poseStack.translate(pivotX, pivotY, pivotZ);    // 先把原点移到旋转中心
poseStack.mulPose(q);                            // 旋转
poseStack.translate(-pivotX, -pivotY, -pivotZ); // 移回去
```

**使用场景**：想让关节绕自身中心旋转而不是绕坐标系原点。

---

#### `last()` —— 取当前累积变换

```java
PoseStack.Pose last();
```

返回 `PoseStack.Pose`，包含两个字段：

| 方法 | 返回 | 说明 |
|---|---|---|
| `pose()` | `Matrix4f` | 4×4 位姿矩阵。传入 `VertexConsumer.addVertex()` 决定顶点位置 |
| `normal()` | `Matrix3f` | 3×3 法线矩阵。非等比缩放后必须用这个算光照法线 |

**项目中的例子**（`BoneWalker.java:204`）：
```java
// 取骨骼的最终变换矩阵，传给 VertexConsumer 画顶点
final Matrix4f pose = poseStack.last().pose();
// 取法线矩阵，算光照
final Matrix3f normal = poseStack.last().normal();
```

> **不要跨帧/跨 lambda 持有 `last()` 的返回值！**
> `last().pose()` 返回的是栈顶矩阵的**视图引用**，不是快照。
> 如果你在 lambda（比如 `submitCustomGeometry` 的回调）里用，等到真正运行时栈顶可能已经被 `popPose()` 弹掉了。
> **安全的做法**：`new Matrix4f(poseStack.last().pose())` 拷一份。

**❌ 反面：lambda 捕获了视图引用，执行时栈顶已变**

```java
// 在提交阶段循环画多个方块，每个方块有自己的 translate
for (BlockPos pos : positions) {
    poseStack.pushPose();
    poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);

    // ❌ 坏：lambda 捕获了 poseStack.last().pose() 的视图引用
    Matrix4f badRef = poseStack.last().pose();
    collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
        // 等到这个 lambda 真正执行时，上面的 popPose 可能已经跑了，
        // badRef 指向的栈顶内容已经变了，
        // 画出来的所有方块都在同一个位置！
        buffer.addVertex(badRef, 0f, 0f, 0f).setColor(0xFFFFFFFF);
    });

    poseStack.popPose();
}
// 结果：所有方块堆在同一坐标，最后一个循环覆盖了前面的
```

**✅ 正面：lambda 捕获快照副本，无论何时执行都不受影响**

```java
for (BlockPos pos : positions) {
    poseStack.pushPose();
    poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);

    // ✅ 好：用 new Matrix4f() 在提交时就拍好快照
    Matrix4f snapshot = new Matrix4f(poseStack.last().pose());
    collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
        // snapshot 是独立副本，不依赖栈顶状态
        buffer.addVertex(snapshot, 0f, 0f, 0f).setColor(0xFFFFFFFF);
    });

    poseStack.popPose();
}
// 结果：每个方块正确画在自己对应的世界坐标上
```

> 这条同样适用于 `poseStack.last().normal()`。法线矩阵也是视图引用，跨 lambda 也要拷：`new Matrix3f(poseStack.last().normal())`。

### 2.3 提交层：`SubmitNodeCollector` —— 几何的"收件箱"

`net.minecraft.client.renderer.SubmitNodeCollector` 是接口，继承 `OrderedSubmitNodeCollector`。
**你在提交阶段拿到它之后，调用它的各种 `submit*` 方法把几何登记进去，管线在后面真正的绘制阶段再取出画到屏幕上。**

> **提交 ≠ 绘制。** `submit*` 只是把几何数据记到帧的"待绘制列表"里。真正的 GPU 绘制发生在后面。
> 所以提交阶段做不了的事（比如读像素、写深度缓冲）也就别想了。

#### 控制绘制顺序：`order(int)`

在调任何 `submit*` 之前，可以先指定**绘制序号**：

```java
collector.order(1)                          // 设当前绘制序号为 1
    .submitModel(model, state, pose, ...);  // 序号 1 的这一批
collector.order(0)                          // 改成 0
    .submitModel(model, state, pose, ...);  // 序号 0 的这一批，画在更前面
```

同序号内不保证相对顺序。不调 `order()` 默认是 0。低序号先画（先画 = 被后面的覆盖，适合背景）。

---

#### `submitCustomGeometry(pose, renderType, callback)` —— 自定义几何

最灵活的提交方式。你自己在回调里写顶点，画任何形状。

```java
void submitCustomGeometry(
    PoseStack poseStack,                                  // 变换（位置/朝向/大小）
    RenderType renderType,                                // 怎么画（着色器/混合/深度）
    SubmitNodeCollector.CustomGeometryRenderer renderer   // 写顶点的回调
);
```

第三个参数是一个函数式接口，回调里拿到 `(PoseStack.Pose pose, VertexConsumer consumer)`：

| 拿到的东西 | 干什么用 |
|---|---|
| `pose.pose()` | 4×4 变换矩阵，传给 `consumer.addVertex()` 决定顶点位置 |
| `pose.normal()` | 3×3 法线矩阵，光照计算用（`setNormal`） |
| `consumer` | `VertexConsumer`，一个个地写顶点 |

**典型流程**（摘自 `VesnaEnergyHud.java:170-184`）：
```java
// 在世界中某个位置画三个能量槽方块，每个方块调用一次 submitCustomGeometry
for (int i = 0; i < 3; i++) {
    float yLow = baseY + i * (SEG_HEIGHT + SEG_GAP);
    float yHigh = yLow + SEG_HEIGHT;

    // 背景槽（半透明灰）
    collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
        Matrix4f matrix = pose.pose();
        drawRect(buffer, matrix, 0,
            -halfW, yLow, halfW, yHigh,
            0.10f, 0.10f, 0.15f, 0.60f);   // RGBA
    });

    // 填充槽（黄色，z 偏移 -0.001 防 Z-fighting）
    if (fillRatio > 0.001f) {
        float fillYHigh = yLow + SEG_HEIGHT * fillRatio;
        collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
            Matrix4f poseMatrix = pose.pose();
            drawRect(buffer, poseMatrix, -0.001f,
                -halfW, yLow, halfW, fillYHigh,
                1.0f, 0.82f, 0.35f, 1.0f);
        });
    }
}
```

**另一个例子**（`MobHealthBarHud.java:250-277`）—— 用不同的 RenderType 画血条的背景层、缓冲层、填充层三层叠加：
```java
// 背景层
collector.submitCustomGeometry(poseStack, bgType, (pose, buffer) -> {
    drawTexturedQuad(buffer, matrix, ...);  // 带纹理的血条底框
});
// 填充层
collector.submitCustomGeometry(poseStack, barType, (pose, buffer) -> {
    drawTexturedQuad(buffer, matrix, ...);  // 按血量比例宽度的彩色条
});
```

**注意事项：**
- **不要把 lambda 跨帧持有。** 回调在后面的绘制阶段才执行，如果 lambda 里引用了外层变量，确保它们不会被提前释放。
- **`PoseStack` 快照**：如果回调延迟执行，`poseStack` 可能已经被 `popPose()` 弹了。安全的做法是在提交时用 `new Matrix4f(poseStack.last().pose())` 拷一份传给 lambda。

---

#### `submitModel(pose, renderType, ...)` —— 提交模型

用于提交**已定义好的模型**（实体模型、方块实体模型、物品模型）。

完整签名（`OrderedSubmitNodeCollector.java:66`）：
```java
<S> void submitModel(
    S model,                          // 模型实例（如 EntityModel、BakedGeoModel）
    S renderState,                    // 渲染状态（含动画变换、叠加层颜色等）
    PoseStack poseStack,              // 变换
    RenderType renderType,            // 渲染类型
    int lightCoords,                  // 光照坐标（天空光 + 方块光，编码成 int）
    int overlayCoords,                // 叠加层坐标（受伤闪白用）
    int tintedColor,                  // 染色 -1 表示不染色
    @Nullable Sprite sprite,          // 纹理精灵（null=从 renderState 取）
    int outlineColor,                 // 轮廓颜色（0 = 无轮廓）
    @Nullable CrumblingOverlay crumblingOverlay  // 破坏叠加（null = 无）
);
```

**典型用法**（GeckoLib 实体层）：  
```java
// 在实体渲染层的 submit 方法里
collector.order(1)
    .submitModel(
        this.getParentModel(),        // 模型
        state,                        // 实体渲染状态
        poseStack,                    // 父级传下来的 PoseStack
        this.renderType(),            // 纹理对应的 RenderType
        lightCoords,                  // 光照
        OverlayTexture.NO_OVERLAY,    // 无受伤闪白
        state.outlineColor,           // 轮廓
        null                          // 无自定义精灵
    );
```

项目参考：`GeoRenderIntercept.java:147-151` 用 `submitModel` 提交完整 GeckoLib 模型。

---

#### `submitText(pose, x, y, text, dropShadow, mode, bgColor, color, maxWidth, maxLines)` —— 提交文字

在世界中（而非 GUI）渲染文字。常用于血条上的等级标签、名字标签。

```java
void submitText(
    PoseStack poseStack,               // 变换
    float x, float y,                  // 局部坐标偏移（文字左下角）
    TextVisitable text,                // 文字内容（用 Component.literal().getVisualOrderText() 转换）
    boolean dropShadow,                // 是否带投影
    Font.DisplayMode mode,             // SEE_THROUGH / NORMAL / POLYGON_OFFSET
    int backgroundColor,               // 背景色 ARGB（0 = 透明背景）
    int color,                         // 文字色 ARGB
    int maxWidth,                      // 最大宽度像素（0 = 不限）
    int maxLines                       // 最大行数（0 = 不限）
);
```

**项目中的例子**（`MobHealthBarHud.java:529-542`）—— 在怪物头顶显示 "Lv.N"：
```java
collector.submitText(
    poseStack,
    -width / 2.0f,                     // 居中：向左偏移文字宽度的一半
    0.0f,                              // 基线高度
    Component.literal(text).getVisualOrderText(),  // "Lv.42"
    true,                              // 带投影
    Font.DisplayMode.SEE_THROUGH,      // 透视模式（透过方块也能看到）
    0xF000F0,                          // 背景色（半透明黑底）
    0xFFFFFFFF,                        // 白色文字
    0,                                 // 不限宽
    0                                  // 不限行
);
```

---

#### `submitNameTag(pose, renderState, ...)` —— 提交名字标签

实体的名字标签提交入口，由 `LivingEntityRenderer` 内部调用，一般不需要你手动调。

#### `submitItem(pose, renderState, ...)` —— 提交物品模型

用于在世界中渲染物品模型，常用于骨骼挂点上的手持物品渲染。

**项目参考**：`BoneMountGeoLayer.java:283-292` 在骨骼位姿上用 `Minecraft.getInstance().getItemModelResolver().updateForLiving(...)` 构造物品状态，然后提交物品模型到挂点上。

#### `submitBlockModel(pose, renderState, ...)` —— 提交方块模型

提交已经烘焙好的方块模型，方块实体的渲染走这个。一般不需要手动调。

#### `submitShadow(pose, radius, pieces)` —— 提交阴影

提交实体底部的圆形阴影。`radius` 控制阴影大小，`pieces` 是多块阴影的集合（例如分段的生物阴影）。

```java
void submitShadow(
    PoseStack poseStack,
    float radius,                              // 阴影半径
    List<EntityRenderState.ShadowPiece> pieces // 阴影段（多段合成复杂形状）
);
```

默认由 `EntityRenderDispatcher` 自动提交，一般不需要手动调。

---

#### `submitFlame(pose, renderState, rotation)` —— 提交火焰

在实体身上绘制火焰效果（着火时的橙黄色火焰包裹）。`rotation` 是火焰纹理的旋转。

```java
void submitFlame(
    PoseStack poseStack,
    EntityRenderState renderState,    // 实体的渲染状态
    Quaternionf rotation              // 火焰纹理朝向
);
```

默认由 `EntityRenderDispatcher` 自动提交。如果你需要自定义着火效果，可以拦截后自己调这个。

---

#### `submitLeash(pose, leashState)` —— 提交拴绳

绘制拴绳的曲线。`leashState` 包含绳子两端的位置信息。

```java
void submitLeash(
    PoseStack poseStack,
    EntityRenderState.LeashState leashState  // 拴绳状态（起点/终点坐标）
);
```

默认由 `LivingEntityRenderer` 自动提交。

---

#### `submitShapeOutline(pose, shape, renderType, color, width, afterTerrain)` —— 提交形状轮廓

用于调试渲染——画一个 `VoxelShape` 的轮廓线。碰撞箱轮廓、方块选取框都走这个。

```java
void submitShapeOutline(
    PoseStack poseStack,
    VoxelShape shape,                 // 轮廓形状
    RenderType renderType,            // 渲染类型（线框用 RenderTypes.LINES）
    int color,                        // ARGB 颜色
    float width,                      // 线宽
    boolean afterTerrain              // true = 地形之后画（不被方块遮挡）
);
```

**典型场景**：`F3+B` 调试碰撞箱就是通过这个方法渲染的。一般不需要手动调。

---

#### `submitQuadParticleGroup(particles)` —— 提交粒子批

批量提交纹理面片粒子，用于粒子特效的批量渲染。

```java
void submitQuadParticleGroup(
    QuadParticleRenderState particles  // 一批纹理面片粒子的集合
);
```

粒子系统内部使用，一般不需要手动调。

### 2.4 `RenderPipeline`：26.2 的「怎么画」

一条管线把「着色器 + 顶点布局 + 混合 + 深度/模板 + 颜色目标 + 绑定组」打包成一个**不可变对象**，
用 Builder 造、用 NeoForge 事件注册。Builder 的真实方法
（`com/mojang/blaze3d/pipeline/RenderPipeline.java:226-420`）：

```java
RenderPipeline.builder(RenderPipeline.Snippet... snippets)      // 用现成的片段起步
    .withLocation("minegenshin:pipeline/glow")                  // 管线 id
    .withVertexShader("core/entity")                            // 资源路径：assets/<ns>/shaders/core/entity.vsh
    .withFragmentShader("core/entity")
    .withShaderDefine("ALPHA_CUTOUT")
    .withBindGroupLayout(BindGroupLayouts.GLOBALS)              // 声明 uniform/纹理绑定
    .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)   // 顶点格式
    .withPrimitiveTopology(PrimitiveTopology.QUADS)
    .withCull(false)
    .withDepthStencilState(DepthStencilState.DEFAULT)
    .withColorTargetState(ColorTargetState.DEFAULT)
    .build();
```

两个工程上的要点：

- **`Snippet` 是复用单位**。原版自己的管线就是这么搭的：`GLOBALS_SNIPPET` → `MATRICES_FOG_SNIPPET`
  → `ENTITY_SNIPPET` 层层叠加（`net/minecraft/client/renderer/RenderPipelines.java:26-93`）。
  自定义管线也应该从 `MATRICES_FOG_SNIPPET` 这类片段起步，别从零拼，否则漏掉矩阵 UBO 就会画出一片空白。
- **注册必须走事件**：`RegisterRenderPipelinesEvent#registerPipeline(RenderPipeline)`
  （`neoforge-src:.../RegisterRenderPipelinesEvent.java:36`），事件在**默认管线注册之后**触发，
  且只在客户端、mod 总线上。

```java
// 注册入口（NeoForge 26.2）：mod 总线、仅客户端
@SubscribeEvent
static void onRegisterPipelines(RegisterRenderPipelinesEvent event) {
    event.registerPipeline(MyPipelines.GLOW);
}
```

### 2.5 `RenderType` 与 `RenderTypes`

`RenderType` 是「管线 + 那一层绘制时附带的状态」的组合体（名字、纹理、输出目标、分层变换），
26.2 的构造签名是（`net/minecraft/client/renderer/rendertype/RenderType.java:35`）：

```java
public static RenderType create(String name, RenderSetup state);

public static final RenderType LINES = RenderType.create(
        "lines",
        RenderSetup.builder(RenderPipelines.LINES)
                .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                .createRenderSetup()
);
```

也就是说：**`RenderType` 没被删，改的是它的入参形状** —— 以前是一长串「缓冲器 + 格式 + 模式」，
现在是「先挑管线（`RenderSetup.builder(RenderPipeline)`），再补纹理/输出目标/分层变换」。
`RenderTypes` 是原版常量的集中地：既有现成的常量，也有 58 个静态工厂方法。调试与自绘时最常用的几个
（都在 `RenderTypes.java` 里，括号是行号）：

| 工厂 / 常量 | 用途 | 顶点怎么给 |
|---|---|---|
| `lines()` / `linesTranslucent()`（`:624`、`:628`） | 画线（可半透明） | `POSITION_COLOR_NORMAL_LINE_WIDTH`，记得 `setLineWidth` |
| `debugFilledBox()`（`:636`） | 实心盒（调试图元） | 四顶点一面的四边形 |
| `debugQuads()`（`:644`） | 独立的四边形集合 | 每 4 个顶点一个面 |
| `debugPoint()`（`:640`） | 画点 | 单顶点 |
| `debugTriangleFan()`（`:648`） | 三角扇（画圆形/扇形指示器） | 首顶点 + 后续扇形顶点 |
| `outline(Identifier texture)`（`:552`） | 描边 | 见该方法的实现 |

写自定义几何时先在这张表里找现成的，找不到再 `RenderType.create(...)` 自己造 —— 少写一条管线，
就少一份"混合/深度不对"的排查工作。

### 2.6 顶点与几何数据

| 用途 | 类 | 关键点 |
|---|---|---|
| 顶点格式描述 | `com.mojang.blaze3d.vertex.VertexFormat`、`VertexFormatElement` | 元素的语义/分量数/偏移；与管线的 `withVertexBinding` 必须一致 |
| 常用格式 | `com.mojang.blaze3d.vertex.DefaultVertexFormat` | `POSITION_COLOR`、`POSITION_TEX` 等常量 |
| 写顶点 | `com.mojang.blaze3d.vertex.VertexConsumer` | `addVertex(PoseStack.Pose, float,float,float)` 之后链式设属性 |
| 一段几何的数据容器 | `com.mojang.blaze3d.vertex.MeshData` | `vertexBuffer()` / `indexBuffer()` / `drawState()` / `sortQuads(...)`，实现 `AutoCloseable` |

`MeshData` 是 26.2 里替代旧 `BufferBuilder.end()` 的东西：构建出来的几何**可以排序、可以缓存**，
`sortQuads(...)` 就是给半透明四方体排索引用的（`com/mojang/blaze3d/vertex/MeshData.java:67`）。

### 2.7 设备、命令编码器与渲染通道

自己发绘制命令时按这个顺序（全部在客户端渲染线程）：

```java
GpuDevice device = RenderSystem.getDevice();                       // 设备
CommandEncoder encoder = device.createCommandEncoder();            // 编码器
try (RenderPass pass = encoder.createRenderPass(() -> "mg-glow", colorTextureView, Optional.empty())) {
    pass.setPipeline(MyPipelines.GLOW);
    pass.setUniform("Globals", globalSettingsUniform);             // 按名字绑 uniform 缓冲
    pass.bindTexture("Sampler0", textureView, null);
    pass.setVertexBuffer(0, vertexBufferSlice);
    pass.setIndexBuffer(indexBuffer, IndexType.SHORT);
    pass.drawIndexed(indexCount, 1, 0, 0, 0);
}
encoder.submit();
```

`RenderPass` 的可用方法（`com/mojang/blaze3d/systems/RenderPass.java`，节选）：
`setPipeline`、`setUniform(String, GpuBuffer|GpuBufferSlice)`、`bindTexture`、`setVertexBuffer`、
`setIndexBuffer`、`setViewport`、`enableScissor`/`disableScissor`、`drawIndexed`、`draw`、
`multiDrawIndexed`、`drawIndexedIndirect`、`drawMultipleIndexed`、`pushDebugGroup`/`popDebugGroup`。

`GpuDevice` 负责建资源（`com/mojang/blaze3d/systems/GpuDevice.java`）：
`createCommandEncoder()`、`createBuffer(label, usage, size|ByteBuffer)`、`createTexture(...)`、
`createTextureView(...)`、`createSampler(...)`、`precompilePipeline(RenderPipeline)`。

`RenderSystem` 在 26.2 里已经退化成「全局状态与环境」的门面，剩下的常用成员是
（`com/mojang/blaze3d/systems/RenderSystem.java`）：`getDevice()`、`isOnRenderThread()`、
`assertOnRenderThread()`、`getModelViewStack()`、`getProjectionMatrixBuffer()`、
`setShaderLights(GpuBufferSlice)`/`setShaderFog(GpuBufferSlice)`、`getSequentialBuffer(PrimitiveTopology)`、
`setGlobalSettingsUniform(GpuBuffer)`。

### 2.8 缓冲与 uniform：`Std140Builder`

往 UBO 里塞数据只有一条路：按 std140 规则排布，再用 `Std140Builder` 写
（`com/mojang/blaze3d/buffers/Std140Builder.java`）：

```java
int size = new Std140SizeCalculator().putVec3().putFloat().get();   // 先算大小（含对齐填充）
GpuBuffer buffer = device.createBuffer(() -> "mg-glow-data", GpuBuffer.USAGE_UNIFORM, size);

try (MemoryStack stack = MemoryStack.stackPush()) {
    Std140Builder builder = Std140Builder.onStack(stack, size);
    builder.putVec3(glowColor.x, glowColor.y, glowColor.z);
    builder.putFloat(intensity);
    encoder.writeToBuffer(buffer.slice(), builder.get());            // 上传
}
```

`Std140Builder` 提供 `putFloat` / `putInt` / `putVec2|3|4` / `putIVec2|3|4` / `putMat4f` / `align(int)`
（`Std140Builder.java:38-148`）；`Std140SizeCalculator` 有同名的 `putXxx()`，两边**必须一一对应**，
否则数据会错位——这类 bug 的症状通常是「前几个参数对、后面的全是垃圾值」。

> std140 的两条硬规则（GPU 规范，不是 MC 特有）：`vec3` 按 `vec4` 对齐（占 12 字节、吃 16 字节），
> 数组元素按 16 字节步长排。`putVec3` 之后紧跟 `putFloat` 是安全的（正好填满一个 `vec4`），
> 但 `putVec3` 后面跟 `putVec3` 就会出洞。

### 2.9 已消失 / 已改名的旧 API

这一节专门用来纠正网上（以及本仓库早期文档里）仍然常见的 1.21 写法。逐条都在 26.2 的源码里核对过：

| 旧写法（1.21 及以前） | 26.2 的现状 | 替代 |
|---|---|---|
| `RenderSystem.setShader(Supplier<ShaderInstance>)` | **类里已没有这个方法**（只剩 `setShaderFog` / `setShaderLights`） | 用 `RenderPipeline` + `RenderType`；`RenderSystem.java:113-125` |
| `Tesselator.getInstance().begin(...)` / `Tesselator.end()` | **`Tesselator` 类已不存在** | 提交几何走 `SubmitNodeCollector`；要自己的数据容器用 `MeshData` |
| `BufferUploader.drawWithShader(...)` | **`BufferUploader` 类已不存在** | `RenderPass#setVertexBuffer/setIndexBuffer + drawIndexed` |
| `ShaderInstance`（与 `assets/.../shaders/core/*.json` 配对） | **类已不存在** | `RenderPipeline`（代码/事件注册）取代了 core shader JSON |
| `RenderType.create(...)` 的旧签名 | **方法还在，签名换了** | `RenderType.create(String, RenderSetup)`，管线由 `RenderSetup.builder(RenderPipeline)` 提供 |
| `PoseStack.last().pose()` 直接丢给渲染器 | 仍在用 | 但要意识到它是**相机相对**坐标（§1.5） |

**结论**：任何还在教 `RenderSystem.setShader` + `ShaderInstance` + core shader JSON 的资料，都是 1.21 及以前的，
在 26.2 上照抄必然编译不过。26.2 的心智模型是「管线是代码里的不可变对象，由事件注册」。

### 2.10 案例：一次自定义绘制要动哪些类

这一节把前面散落的类按「你的需求」串成三条可执行路线。三条路线都真实可用，选哪条取决于你要画的是
「随帧合批的一次性几何」还是「自己长期持有的缓冲」。

| 你的需求 | 走哪条路 | 涉及的类（按调用顺序） |
|---|---|---|
| 每帧往世界里塞一点几何（指示器、连线、简单面片） | **提交层** | `SubmitCustomGeometryEvent` → `SubmitNodeCollector#submitCustomGeometry` → 回调里用 `VertexConsumer` 写顶点 |
| 自己造几何、自己管显存、自己发绘制命令 | **设备层** | `ByteBufferBuilder` + `BufferBuilder` → `MeshData` → `GpuDevice#createBuffer` → `CommandEncoder#createRenderPass` → `RenderPass#drawIndexed` |
| 想让这批几何有自己的着色器/混合/顶点格式 | **管线层 + 上面任一条** | `RenderPipeline.builder(...)` + `RegisterRenderPipelinesEvent`，再用 `RenderSetup.builder(pipeline)` 包成 `RenderType` |

设备层的完整范例在 vanilla 里就有：`net/minecraft/client/renderer/DebugCrosshairRenderer.java`（3D 调试准星）。
它把「建缓冲 → 建通道 → 绑管线 → 画索引」这条链完整走了一遍，值得逐行读：

```java
// ① 建顶点：BufferBuilder + ByteBufferBuilder，格式必须与管线的顶点绑定一致
try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(FORMAT.getVertexSize() * vertexCount)) {
    BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.LINES, FORMAT);
    builder.addVertex(x0, y0, z0).setColor(0xFFFFFFFF).setNormal(1f, 0f, 0f).setLineWidth(4f);
    builder.addVertex(x1, y1, z1).setColor(0xFFFFFFFF).setNormal(1f, 0f, 0f).setLineWidth(4f);

    try (MeshData mesh = builder.buildOrThrow()) {
        // ② 上传到显存（32 = GpuBuffer.USAGE_VERTEX）
        GpuBuffer vertexBuffer = RenderSystem.getDevice().createBuffer(() -> "my lines", 32, mesh.vertexBuffer());
    }
}

// ③ 每帧绘制：拿顺序索引缓冲 + 写动态变换 + 开 pass
GpuBuffer indexBuffer = RenderSystem.getSequentialBuffer(PrimitiveTopology.LINES).getBuffer(indexCount);
GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelViewStack));

try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
        .createRenderPass(() -> "my lines", colorTexture, Optional.empty(), depthTexture, OptionalDouble.empty())) {
    pass.setPipeline(RenderPipelines.LINES);
    RenderSystem.bindDefaultUniforms(pass);
    pass.setVertexBuffer(0, vertexBuffer.slice());
    pass.setIndexBuffer(indexBuffer, RenderSystem.getSequentialBuffer(PrimitiveTopology.LINES).type());
    pass.setUniform("DynamicTransforms", transforms);
    pass.drawIndexed(indexCount, 1, 0, 0, 0);
}
```

这段里每一句都能在 `DebugCrosshairRenderer.java:28-90` 找到出处。三个容易忽略的点：

1. **`MeshData` 要关**（它实现了 `AutoCloseable`，内部是直接内存）。
2. **`ByteBufferBuilder`/`BufferBuilder` 是构建期用的**，画的时候只用 `GpuBuffer`；不要每帧重新建缓冲。
3. **动态变换走 `RenderSystem.getDynamicUniforms().writeTransform(...)`**，
   再 `pass.setUniform("DynamicTransforms", slice)` 绑上去——名字是固定的，写错就是所有东西堆在原点。

### 2.11 派生：一次改动覆盖「一族」管线

§3.8 讲的是从零加一条管线。这一节讲另一种更常见、也更容易出错的做法：
**不改原版，而是从原版管线派生出一族自己的版本**。

#### 什么时候需要派生

当你的改动是「顶点阶段换一套算法」，而片元阶段、混合、深度、剔除、剪裁全部照旧时，
手搓一条管线是不够用的：原版 `entity` 管线有一整族变体

| 变体 | 差别 |
|---|---|
| `ENTITY_SOLID` / `ENTITY_SOLID_Z_OFFSET_FORWARD` | 不透明，带/不带深度偏移 |
| `ENTITY_CUTOUT` / `ENTITY_CUTOUT_CULL` / `ENTITY_CUTOUT_Z_OFFSET` | 镂空，带/不带背面剔除 |
| `ENTITY_TRANSLUCENT` / `ENTITY_TRANSLUCENT_CULL` | 半透明 |
| `ENTITY_TRANSLUCENT_EMISSIVE` | 半透明自发光 |

手搓一条只能覆盖其中一个，剩下的全部回退到原版路径 —— 表现就是「优化/改动只生效了一半」，
而且只在特定材质或特定光照下才看得出来，极难排查。

#### `toBuilder()` 带过来什么

`RenderPipeline#toBuilder()` 会把原管线原样搬过来：location、两个着色器、**全部 shader defines**、
全部 bind group layout、深度/模板状态、多边形模式、剔除、颜色目标、顶点格式、图元拓扑
（`com/mojang/blaze3d/pipeline/RenderPipeline.java:163-195`）。于是「只换顶点着色器」变成三行：

```java
private static RenderPipeline derive(RenderPipeline base) {
    return base.toBuilder()
            // location 必须唯一：注册表按 location 建索引，沿用基管线会直接撞名
            .withLocation(Minegenshin.id("pipeline/skinned_" + base.getLocation().getPath().replace('/', '_')))
            .withVertexShader(Minegenshin.id("core/entity_skinned"))   // 换掉顶点着色器
            .withBindGroupLayout(SKIN_DATA_LAYOUT)                     // 追加一组常量缓冲
            .withVertexBinding(0, SkinnedMesh.FORMAT)                  // 覆盖 0 号槽的顶点格式
            .build();
}

public static void registerPipelines(RegisterRenderPipelinesEvent event) {
    for (RenderPipeline base : WHITELIST) {
        event.registerPipeline(derive(base));
    }
}
```

两个方法名字很像、行为相反，这是派生做法最需要记牢的一点：

- `withBindGroupLayout(...)` 是**追加**（`RenderPipeline.java:283-290`）——挂上自己的一组 uniform；
- `withVertexBinding(int, VertexFormat)` 是**同号覆盖**（`:328-331`）——替换某个槽位的顶点布局。

片元着色器**刻意不动**：ALPHA_CUTOUT、半透明混合、覆盖层采样这些行为全都来自基管线，
所以一条派生逻辑能覆盖整族。本项目 `SkinnedPipelines.java:100-116` 就是这么做的。

#### 白名单纪律：基管线用到的 define，你的着色器必须都有分支

不是所有基管线都能派生。本项目刻意排除了两个：

| 被排除的基管线 | 原因 |
|---|---|
| `ENTITY_CUTOUT_DISSOLVE` | 带 `DISSOLVE` define，还会多加一组 DISSOLVE_MASK 采样器；顶点着色器里没有对应分支 |
| `BREEZE_WIND` | 带 `APPLY_TEXTURE_MATRIX`；我们的顶点着色器不支持纹理矩阵 |

硬派生这两条的后果是「该溶解的没溶解、该套纹理矩阵的没套」——画面差异很隐蔽。
**做法**：先把候选基管线的 define 集合列出来，逐个确认你的着色器都有等价分支，再放进白名单。

#### 两条死规则

1. **静态字段里只准构建「描述对象」。** 管线注册发生在设备初始化之前，那时
   `RenderSystem.getDevice()` 还不能用；在静态初始化里建 `GpuBuffer`、调 `precompilePipeline` 一律失败
   （`SkinnedPipelines.java:34-36` 的类注释专门写了这条）。
2. **注册事件是 mod 总线、仅客户端。** 订阅总线写错不会报错，只是「什么也没发生」。

#### 派生之外的第三条路：留一道回退闸门

派生出的管线只有在「基管线还是原版那条、顶点格式也没被别人改过」时才是安全的。
光影包会替换实体管线，别的模组可能改顶点格式，所以本项目在取派生管线时先过一道环境自检：

```java
public static @Nullable RenderPipeline skinnedFor(RenderPipeline base) {
    RenderPipeline derived = DERIVED.get(base);
    if (derived == null) {
        return null;                                    // 不在白名单 / 还没注册
    }
    return SkinnedPipelineGuard.allows(base, derived) ? derived : null;   // 被环境挡下 → 回退
}
```

返回 `null` 的语义是「**现在不能走这条路，请走原版路径**」，而不是抛异常。
被挡下的原因会显示在 F3 上（`RenderOptimizeStats.java:30-35`），玩家装了光影包时你能立刻看出是回退而不是崩坏。

## 3. 着色器、渲染管线与 GPU 数据

### 3.1 资源包里的着色器长什么样

26.2 的着色器资源分四个目录，分工明确（用改动后的原版 jar 直接列条目可核对：
`assets/minecraft/shaders/**` 共 84 个条目，其中 `core/` 62 个、`post/` 11 个、`include/` 10 个，
**一个 `.json` 都没有**）：

| 目录 | 放什么 | 谁引用 |
|---|---|---|
| `assets/<ns>/shaders/core/*.vsh` / `*.fsh` | 管线用的顶点/片元着色器源码 | `RenderPipeline.Builder#withVertexShader/withFragmentShader` |
| `assets/<ns>/shaders/include/*.glsl` | 被 `#moj_import` 引入的公共代码 | 上面两类源码 |
| `assets/<ns>/shaders/post/*.vsh` / `*.fsh` | 后处理用的着色器 | 后处理链（`assets/<ns>/post_effect/*.json`） |
| `assets/<ns>/post_effect/*.json` | 后处理链的**配置**（哪些 pass、输入输出、uniform） | `ShaderManager#getPostChain` |

扩展名与目录由 `ShaderType` 决定（`com/mojang/blaze3d/shaders/ShaderType.java`）：

```java
VERTEX("vertex", ".vsh"),
FRAGMENT("fragment", ".fsh");
// 目录与后缀的转换器：
public FileToIdConverter idConverter() { return new FileToIdConverter("shaders", this.extension); }
```

所以 `withVertexShader("core/entity")` 指向 `assets/<ns>/shaders/core/entity.vsh`，
`withFragmentShader("core/entity")` 指向 `assets/<ns>/shaders/core/entity.fsh`。

> **Namespace 的坑**：原版自己写 `"core/entity"` 是给 `minecraft` 命名空间用的。
> 模组自己的着色器要写全限定名，例如本项目 `SkinnedPipelines` 用的是
> `withVertexShader(Minegenshin.id("core/entity_skinned"))`
> （`src/main/java/com/linweiyun/genshin/client/render/optimize/gpu/SkinnedPipelines.java:105`），
> 对应资源 `assets/minegenshin/shaders/core/entity_skinned.vsh`。

### 3.2 `ShaderManager`：管的是源码，不是「编译好的对象」

`net.minecraft.client.renderer.ShaderManager` 是**客户端资源重载监听器**
（`SimplePreparableReloadListener<ShaderManager.Configs>`）并实现 `AutoCloseable`，
公开的关键成员（`net/minecraft/client/renderer/ShaderManager.java`）：

```java
public static final String SHADER_PATH = "shaders";        // :49
public static final int MAX_LOG_LENGTH = 32768;            // :48

public @Nullable String getShader(Identifier id, ShaderType type);                      // :202
public @Nullable String getShaderSource(Identifier id, ShaderType type);                // :248（内部 Configs 上）
public @Nullable PostChain getPostChain(Identifier id, Set<Identifier> allowedTargets);  // :185
```

要点：

- `getShader(...)` 返回的是**源码字符串**。也就是说 26.2 里「着色器」在 Java 侧就是一个文本资源，
  **编译/链接的产物由管线承担**（`GpuDevice#precompilePipeline(RenderPipeline)` 返回
  `CompiledRenderPipeline`，见 §2.7）。
- 加载失败不是抛异常到调用方，而是走构造时传入的 `Consumer<Exception> recoveryHandler`；
  日志会被 `MAX_LOG_LENGTH` 截断——着色器很长时看不到完整报错，这是排查时容易误判的一点。
- 资源包重载（游戏内重载资源包）会重新执行 `Configs` 的加载：改完 `.vsh` / `.fsh` 刷新资源即可生效，
  不需要重启游戏。

### 3.3 `#moj_import`：预处理器的 include 机制

实现类是 `com.mojang.blaze3d.preprocessor.GlslPreprocessor`，用一条正则识别两种写法
（`GlslPreprocessor.java:21`）：

```glsl
#moj_import <minecraft:fog.glsl>        // 命名空间写法：assets/minecraft/shaders/include/fog.glsl
#moj_import "local_helper.glsl"         // 相对当前文件所在目录
```

引入点挂在 `ShaderManager` 上：加载每个着色器源码时会注入一个 `applyImport(boolean isRelative, String path)`
（`ShaderManager.java:105`），由它把 `#moj_import` 展开成真正的文件内容。

三条实践规则：

1. **启动期用的着色器不能 import**。原版源码里就写着这句注释
   （`assets/minecraft/shaders/core/gui.vsh:3`、`ui`/`position_tex_color` 等）：
   `// Can't moj_import in things used during startup, when resource packs don't exist.`
   你的着色器如果要在资源包系统就绪之前编译（比如加载界面、主菜单），就别用 `#moj_import`。
2. **别写循环导入**：A 引 B、B 引 A 会直接把编译打进死循环或报错。
3. **公共函数放 `shaders/include/`**，两边都用 `<ns:xxx.glsl>` 引，避免复制粘贴出的版本漂移。

### 3.4 自定义 `RenderPipeline`：注册时机与两条硬约束

注册流程只有两步：**先构建描述对象，再在事件里登记**。

```java
public final class MyPipelines {
    /** 自定义常量缓冲：名字必须与 GLSL 里的块名一致。 */
    public static final BindGroupLayout GLOW_LAYOUT = BindGroupLayout.builder()
            .withUniform("GlowData", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .build();

    /** 静态字段只构建描述对象：管线注册发生在设备初始化之前，此时拿不到 GpuDevice。 */
    public static final RenderPipeline GLOW = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation("minegenshin:pipeline/glow")
            .withVertexShader(Minegenshin.id("core/glow"))
            .withFragmentShader(Minegenshin.id("core/glow"))
            .withBindGroupLayout(GLOW_LAYOUT)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build();

    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(GLOW);
    }
}
```

两条必须记住的约束，都来自本项目的真实实现（`SkinnedPipelines.java:34-36`、`:100-116`）：

- **注册时机早于设备初始化**：管线注册时 `RenderSystem.getDevice()` 还不能用，
  所以静态字段里**只准构建描述对象**，不要建 `GpuBuffer`、不要 `precompilePipeline`。
- **`withBindGroupLayout` 是追加、`withVertexBinding` 是同号覆盖**：
  派生原版管线时，前者把新的一组 uniform 挂上去（`RenderPipeline.java:283-290`），
  后者替换指定槽位的顶点格式（`:328-331`）。本项目就是靠这两条把整族 `entity` 管线派生成了 GPU 蒙皮版本。

### 3.5 顶点属性：26.2 是**按名字**接，不是按 location

这是最容易写错的一条，先说结论：**`VertexFormat` 里的属性名要和顶点着色器里的 `in` 变量名逐字对上**，
而不是靠 `layout(location = N)` 的序号。证据在 `VertexFormat` 自己身上
（`com/mojang/blaze3d/vertex/VertexFormat.java:28`、`:55-59`）：

```java
this.elements.putIfAbsent(element.name(), element);                      // 按名字存
public @Nullable VertexFormatElement getElement(String attributeName);   // 按名字查
public boolean contains(String attributeName);
```

所以一个顶点属性要「三处名字一致」：

| 环节 | 写什么 | 名字从哪来 |
|---|---|---|
| 管线 | `withVertexBinding(0, <格式>)` | 格式里声明的属性名 |
| 顶点着色器 | `in vec3 Position; in vec2 UV0; …` | 必须与格式里的名字**逐字相同** |
| 提交几何 | `VertexConsumer#addVertex(...)` 后按顺序设置属性（`setColor`/`setUv`/`setNormal`/`setLight`） | 顺序按格式的声明顺序 |

本项目的 GPU 蒙皮就是标准示范。顶点格式（`SkinnedMesh.java:42-47`）：

```java
public static final VertexFormat FORMAT = VertexFormat.builder(0)
        .addAttribute("Position", GpuFormat.RGB32_FLOAT)
        .addAttribute("UV0",      GpuFormat.RG32_FLOAT)
        .addAttribute("Normal",   GpuFormat.RGBA8_SNORM)
        .addAttribute("BoneIds",  GpuFormat.RGBA16_UINT)
        .build();
```

对应着色器（`assets/minegenshin/shaders/core/entity_skinned.vsh:11-15`）：

```glsl
// 属性名必须与 SkinnedMesh.FORMAT 逐字对应：管线编译期是按名字把顶点格式接到着色器输入上的。
in vec3 Position;
in vec2 UV0;
in vec3 Normal;
in uvec4 BoneIds;
```

一个格式最多 16 个元素（`VertexFormat.MAX_VERTEX_ELEMENTS = 16`）；`VertexFormat.builder(stepRate)` 的
`stepRate` 是给实例化用的（大于 0 表示逐实例推进，见 §7.2）。
名字对不上、或类型不匹配的典型症状：模型塌成一个点、整块全黑、纹理乱贴、骨骼动画完全不动。

### 3.6 常量缓冲（UBO）与 std140

自定义 uniform 数据走「声明 → 建缓冲 → 上传 → 绑定」四步。声明用
`BindGroupLayout.builder()`（`com/mojang/blaze3d/pipeline/BindGroupLayout.java`）：

```java
BindGroupLayout layout = BindGroupLayout.builder()
        .withSampler("Sampler0")
        .withUniform("GlowData", UniformType.UNIFORM_BUFFER)
        .build();
```

`BindGroupLayout.ensureCompatible(...)` 会在**重名**时直接抛
`IllegalArgumentException("Duplicate bind name ...")`：多组布局里同一个名字只能出现一次，
派生管线时特别容易踩（父管线的 `Sampler0` 你已经不能再声明）。

上传与绑定（承接 §2.8 的 `Std140Builder`）：

```java
int size = new Std140SizeCalculator().putVec4().putFloat().get();
GpuBuffer glowData = device.createBuffer(() -> "mg-glow", GpuBuffer.USAGE_UNIFORM, size);
try (MemoryStack stack = MemoryStack.stackPush()) {
    Std140Builder b = Std140Builder.onStack(stack, size);
    b.putVec4(color.r(), color.g(), color.b(), color.a());
    b.putFloat(strength);
    encoder.writeToBuffer(glowData.slice(), b.get());
}
// 绘制时：
pass.setUniform("GlowData", glowData);      // 名字与 BindGroupLayout/GLSL 块名一致
```

本项目把这套用在了蒙皮矩阵上：`BoneMatrixPalette` 把所有骨骼矩阵打进一块 std140 常量缓冲
（链路细节见 [§7.1](#71-本项目的骨骼调色板链路可直接照抄的-gpu-蒙皮实现)），
那是最值得抄的现成实现。

### 3.7 着色器出问题时怎么查

| 现象 | 先查什么 |
|---|---|
| 画面全黑 / 全白 | 管线是否 `withColorTargetState(...)`、是否漏了 `MATRICES_FOG_SNIPPET` 一类矩阵片段 |
| 报「找不到着色器」 | 资源路径与命名空间：`assets/<ns>/shaders/core/xxx.vsh` 是否存在，大小写是否一致 |
| 编译失败但日志被截断 | 日志上限是 `ShaderManager.MAX_LOG_LENGTH`（32768），长着色器要看完整报错得自己精简源码 |
| 顶点错位/扭曲 | `withVertexBinding` 的格式与 `layout(location=N)` 是否一致（§3.5） |
| uniform 里的值忽大忽小 | std140 对齐：`Std140SizeCalculator` 与 `Std140Builder` 的调用序列必须完全对应 |
| 加载界面/主菜单阶段崩 | 这些阶段资源包系统还没就绪，不能 `#moj_import`（§3.3） |

可用的自查入口（都真实存在）：

- `GpuDevice#isDebuggingEnabled()` / `getLastDebugMessages()`（`com/mojang/blaze3d/systems/GpuDevice.java:156-164`）
  —— 设备层最后几条调试消息，比翻全量日志直接。
- `GpuDevice#clearPipelineCache()` / `precompilePipeline(RenderPipeline, ShaderSource)`
  —— 想单独试编译一条管线时用。
- `RenderSystem.setErrorCallback(GLFWErrorCallbackI)`（`RenderSystem.java:173`）
  —— 想捕捉后端错误时挂回调。

### 3.8 案例：从零加一条自定义管线，跑到屏幕上

目标：在自己的模组里加一条管线，用它画一个「带自己参数、能控制强度」的发光面片。
下面按**动手顺序**列，每一步都写清「做完怎么知道这步是对的」。

#### 第 1 步：写着色器（两个文件）

`src/main/resources/assets/minegenshin/shaders/core/mg_glow.vsh`：

```glsl
#version 330

// 公用的矩阵块就这两行——名字（ProjMat / ModelViewMat）由 include 决定，不能改
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;      // 属性名必须与管线里的顶点格式逐字一致（§3.5）
in vec4 Color;

out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
}
```

`.../core/mg_glow.fsh`：

```glsl
#version 330

in vec4 vertexColor;
out vec4 fragColor;

// 自己的常量缓冲：块名、字段顺序必须与 Java 侧 BindGroupLayout + Std140Builder 完全一致
layout(std140) uniform GlowData {
    vec4 GlowColor;
    float Strength;
};

void main() {
    fragColor = vec4(vertexColor.rgb * GlowColor.rgb * Strength, vertexColor.a);
}
```

**怎么知道对了**：文件名与路径必须严格等于 `assets/<你的命名空间>/shaders/core/<管线里写的路径>`（§3.1）。
名字写错不会报"文件缺失"，而是在编译时报输入变量找不到 —— 这类错误的日志还会被
`ShaderManager.MAX_LOG_LENGTH` 截断（§3.2）。

#### 第 2 步：在 Java 里声明这条管线

```java
public final class MgPipelines {
    /** 与 fsh 里的块名、字段顺序严格对应。 */
    public static final BindGroupLayout GLOW_DATA = BindGroupLayout.builder()
            .withUniform("GlowData", UniformType.UNIFORM_BUFFER)
            .build();

    public static final RenderPipeline GLOW = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET)
            .withLocation("minegenshin:pipeline/mg_glow")
            .withVertexShader(Minegenshin.id("core/mg_glow"))
            .withFragmentShader(Minegenshin.id("core/mg_glow"))
            .withBindGroupLayout(GLOW_DATA)                                  // 追加自己的一组绑定
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .build();
}
```

**怎么知道对了**：这里只构建描述对象，**不要**碰 `RenderSystem.getDevice()`（§3.4 的第一条约束）。
想提前验证能不能编译，可以等设备起来之后调 `device.precompilePipeline(MgPipelines.GLOW)`。

#### 第 3 步：注册

```java
@SubscribeEvent
static void onRegister(RegisterRenderPipelinesEvent event) {
    event.registerPipeline(MgPipelines.GLOW);
}
```

**怎么知道对了**：注册事件在**默认管线之后**、**mod 总线**上触发；如果启动时抛
`Duplicate bind name 'X' in bind group layout`，说明你的 `BindGroupLayout` 与父片段里的名字撞了
（`BindGroupLayout.ensureCompatible`），改名或去掉重复声明即可。

#### 第 4 步：决定怎么画

两种画法任选（§2.10 有对比）：

- 一次性几何 → 在 `SubmitCustomGeometryEvent` 里 `submitCustomGeometry(pose, 自己的 RenderType, ...)`；
- 自己管缓冲 → `BufferBuilder` 建 `MeshData`，上传成 `GpuBuffer`，
  在 `RenderPass` 里 `setPipeline(MgPipelines.GLOW)` + `setUniform("GlowData", buffer)` + `drawIndexed`。

#### 第 5 步：常见失败对照

| 现象 | 最可能的原因 |
|---|---|
| 什么都不显示 | 没绑定自己的 uniform（`setUniform("GlowData", …)`）或 UBO 里 `Strength` 是 0 |
| 只在特定角度可见 | 背面被剔除：`withCull(true)`，先设 `false` 试 |
| 前后遮挡关系不对 | 深度状态：`withDepthStencilState(...)` 传了不写深度的状态 |
| 颜色比预期暗一倍 | 色彩空间/混合：`ColorTargetState` 的 `BlendFunction` 选错（直通 vs 半透明） |
| 报找不到 `GlowData` | GLSL 块名、`BindGroupLayout.withUniform` 名字、`setUniform` 名字，三者必须完全一致 |
| 参数值忽大忽小 | std140 对齐：`Std140SizeCalculator` 与 `Std140Builder` 的序列不一致（§3.6） |

## 4. 实体渲染与 RenderState 系统

> 本节所有 `包/路径/类.java:行` 出处编号都来自本机源码，可直接跟着查：
> Minecraft 与 NeoForge 补丁 → `build/moddev/artifacts/minecraft-patched-26.2.0.88-sources.jar`；
> NeoForge 事件 → `neoforge-26.2.0.88-sources.jar`；GeckoLib → `geckolib-neoforge-26.2-5.5.6-sources.jar`；
> 本项目 → `src/main/java/com/linweiyun/genshin/**`。

### 4.1 三段式：提取 → 提交 → 绘制

26.2 的世界渲染不再"拿着实体直接往缓冲里塞顶点"，而是被拆成三段，每一段的输入输出都是纯数据：

| 段 | 干什么 | 输入 | 输出 | 入口 |
|---|---|---|---|---|
| 提取 Extract | 把"这一帧要画什么"从游戏状态里摘成 RenderState | `Level` + `Camera` + `DeltaTracker` | `LevelRenderState`（一堆 *RenderState） | `LevelExtractor.extract` |
| 提交 Submit | 把 RenderState 翻译成绘制任务（还不画） | `LevelRenderState` | `SubmitNodeStorage` 里的提交节点 | `LevelRenderer.submitFeatures` |
| 绘制 Draw | 按 RenderType / 管线排序成批画出去 | 提交节点 + 帧图 | 屏幕像素 | `FrameGraphBuilder.execute` |

写代码的人最需要记住的一句话是：**提取阶段之后，实体对象就可能被丢弃或改变，所有渲染要用的东西必须已经在 RenderState 里**。GeckoLib 把同一句话写进了 API 注释（`com/geckolib/renderer/base/GeoRendererInternals.java:112-114`：*"The animatable is discarded from the rendering context after this, so any data needed for rendering should be captured in the renderState provided"*）。

一帧里的调用链（同一根线程，见下节）：

```
GameRenderer.extract()                    net/minecraft/client/renderer/GameRenderer.java:383
  └─ minecraft.levelExtractor.extract()   GameRenderer.java:393
       ├─ levelRenderState.reset()        net/minecraft/client/renderer/extract/LevelExtractor.java:109
       ├─ entityRenderDispatcher.prepare()  LevelExtractor.java:121
       ├─ extractVisibleEntities()        LevelExtractor.java:229  →  output.entityRenderStates.add(state):252
       ├─ ExtractLevelRenderStateEvent    LevelExtractor.java:214  （第三方自定义状态在这里塞）
       └─ …
GameRenderer.render()                     GameRenderer.java:400
  └─ renderLevel()                        GameRenderer.java:429,529
       └─ LevelRenderer.render()          net/minecraft/client/renderer/LevelRenderer.java:161
            ├─ submitFeatures()           LevelRenderer.java:174,282
            ├─ featureRenderDispatcher.prepareFrame()  LevelRenderer.java:176
            ├─ 组装 FrameGraphBuilder      LevelRenderer.java:178-241
            └─ frame.execute()            LevelRenderer.java:242
```

### 4.2 线程模型：提取 / 提交 / 绘制全在渲染线程

- 客户端启动时把主线程改名并登记为渲染线程：`net/minecraft/client/main/Main.java:278-279`
  （`Thread.currentThread().setName("Render thread")` + `RenderSystem.initRenderThread()`）。
- 之后所有 GL/GPU 调用都必须过这道断言：`com/mojang/blaze3d/systems/RenderSystem.java:80/88/92`
  （`initRenderThread` / `isOnRenderThread` / `assertOnRenderThread`）。
- 结论：**客户端主线程 = 渲染线程**；提取、提交、绘制三段串行跑在同一根线程里，中间不存在"渲染线程和逻辑线程并发"。真正跑在 worker 线程上的是区块几何编译（`SectionRenderDispatcher`），那是另一条链路。

对本模组的实际意义：渲染层里读实体状态、写静态缓存（如 `WeaponAnchorCache`）都不需要加锁，但**不要**在三段中的任何一段里做阻塞 IO 或等待主线程 tick。

### 4.3 一帧内的事件时序（NeoForge 26.2）

| 顺序 | 事件 | 触发位置 | 能做什么 |
|---|---|---|---|
| 提取阶段末尾 | `ExtractLevelRenderStateEvent` | `LevelExtractor.java:214`，所有原版状态提取完之后 | 用 `LevelRenderState#setRenderData(ContextKey, Object)` 存自定义状态；不在这里提取的状态，后面的阶段拿不到 |
| 提交阶段 | `SubmitCustomGeometryEvent` | `LevelRenderer.java:295`，粒子提交之后、不透明提交渲染之前 | 拿到 `getSubmitNodeCollector()` / `getPoseStack()` / `getLevelRenderState()`，提交**不属于任何实体/方块实体**的自定义几何 |
| 绘制阶段 | `RenderLevelStageEvent` 的八个子事件 | 见下表 | 已经进入帧图执行；此时只能提交，不能改 RenderState |
| 帧首/帧尾 | `RenderFrameEvent.Pre` / `Post` | `net/neoforged/neoforge/client/event/RenderFrameEvent.java:41,52` | 每帧一次的记账点（本项目用它数渲染帧号） |
| 启动期 | `RegisterRenderPipelinesEvent` / `RegisterFeatureRenderersEvent` | NeoForge 注册阶段 | 注册自定义渲染管线 / 自定义 FeatureRenderer（第 7 章用到） |

`RenderLevelStageEvent` 的八个子事件按类注释给出的顺序依次触发（`RenderLevelStageEvent.java:29-42`；子类定义行号 101/110/119/128/137/146/155/164）：
`AfterSky` → `AfterOpaqueBlocks` → `AfterOpaqueFeatures` → `AfterTranslucentFeatures` → `AfterTranslucentBlocks`
→ `AfterTranslucentParticles` → `AfterWeather` → `AfterLevel`。
语义见表中"触发位置"列。八个子事件都不可取消，都带 `getLevelRenderState()` / `getPoseStack()` / `getModelViewMatrix()` / `getRenderableSections()`。

### 4.4 实体渲染器：五个该覆写的方法

`net/minecraft/client/renderer/entity/EntityRenderer.java`：

| 方法 | 行 | 语义 |
|---|---|---|
| `S createRenderState()` | 159 | 造一个空的、可复用的状态对象（抽象方法） |
| `final S createRenderState(T, float)` | 161 | 组合流程：`createRenderState()` → `extractRenderState(...)` → `finalizeRenderState(...)` → NeoForge 的 `RenderStateExtensions.onUpdateEntityRenderState` |
| `void extractRenderState(T, S, float)` | 169 | 把实体的坐标、朝向、动画进度等抄进 state；默认实现已填好 `x/y/z`（用 `Mth.lerp` 做插值）、`ageInTicks`、包围盒、`lightCoords` |
| `void submit(S, PoseStack, SubmitNodeCollector, CameraRenderState)` | 105 | 提交几何；默认实现只提交名牌 |
| `Vec3 getRenderOffset(S)` | 101 | 相对实体原点的偏移，提交时会被加进去 |

状态字段见 `net/minecraft/client/renderer/entity/state/EntityRenderState.java:17-39`：`entityType`、`x/y/z`、`ageInTicks`、`boundingBoxWidth/Height`、`distanceToCameraSq`、`isInvisible`、`lightCoords`（默认 15728880 = 全亮）、`outlineColor`、`nameTag`、`shadowRadius`、`partialTick`。

提交侧由 `EntityRenderDispatcher.submit` 统一收口（`net/minecraft/client/renderer/entity/EntityRenderDispatcher.java:147-181`）：

```java
// 26.2 真实实现（简化版），注意 (x, y, z) 已经是「实体 - 相机」
public <S extends EntityRenderState> void submit(S renderState, CameraRenderState camera,
                                                double x, double y, double z,
                                                PoseStack poseStack, SubmitNodeCollector out) {
    EntityRenderer<?, ? super S> renderer = this.getRenderer(renderState);
    Vec3 pos = renderer.getRenderOffset(renderState);
    poseStack.pushPose();
    poseStack.translate(x + pos.x(), y + pos.y(), z + pos.z());
    renderer.submit(renderState, poseStack, out, camera);
    if (renderState.displayFireAnimation) { /* 火焰层 */ }
    if (!renderState.shadowPieces.isEmpty()) { /* 影子 */ }
    poseStack.popPose();
}
```

调用点在做减法这件事很关键，见第 6 章：`LevelRenderer.submitEntities` 传进去的是 `state.x - camX, state.y - camY, state.z - camZ`（`LevelRenderer.java:724-733`）。

### 4.5 给实体加一层：`RenderLayer`

26.2 的渲染层仍是 `net/minecraft/client/renderer/entity/layers/RenderLayer.java`，但签名已经是提交式的：

```java
public abstract class RenderLayer<S extends EntityRenderState, M extends EntityModel<? super S>> {
    public RenderLayer(RenderLayerParent<S, M> renderer) { … }              // :20
    public M getParentModel() { … }                                        // :64
    public abstract void submit(PoseStack poseStack, SubmitNodeCollector collector,
                                int lightCoords, S state, float yRot, float xRot);  // :68
}
```

挂上去：`LivingEntityRenderer.addLayer(RenderLayer<S, M>)`（`LivingEntityRenderer.java:56`）。参照实现最短的是 `EyesLayer`（`layers/EyesLayer.java:20-25`）：

```java
submitNodeCollector.order(1)
    .submitModel(this.getParentModel(), state, poseStack, this.renderType(),
                 lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
```

`order(int)` 是层内的绘制序号，同号内不保证相对顺序；`OrderedSubmitNodeCollector.submitModel` 的参数表见
`net/minecraft/client/renderer/OrderedSubmitNodeCollector.java:66`（`model / state / poseStack / renderType / lightCoords / overlayCoords / tintedColor / sprite / outlineColor / crumblingOverlay`），
提交自定义几何是同一个接口的 `submitCustomGeometry(PoseStack, RenderType, CustomGeometryRenderer)`（`:186`）。

常用 RenderType 工厂（`net/minecraft/client/renderer/rendertype/RenderTypes.java`）：

| 方法 | 行 | 用途 |
|---|---|---|
| `entitySolid(Identifier)` | 435 | 不透明 |
| `entityCutout(Identifier, boolean)` / `entityCutout(Identifier)` | 447 / 451 | 镂空（绝大多数实体纹理） |
| `entityCutoutZOffset(...)` | 455 / 459 | 镂空 + 深度偏移（贴在皮肤上的图案） |
| `entityTranslucent(Identifier, boolean)` / `(Identifier)` | 479 / 483 | 半透明 |
| `entityTranslucentEmissive(...)` | 487 / 491 | 半透明自发光 |
| `eyes(Identifier)` | 511 | 眼睛（全亮） |

光照与覆盖层的"覆写"就是参数：

- 亮度：`net/minecraft/util/LightCoordsUtil.java:9,13` —— `FULL_BRIGHT = 15728880`，`pack(block, sky)`。
  想让某一层全亮，就把 `LightCoordsUtil.FULL_BRIGHT` 传给 `lightCoords`（本项目在
  `src/main/java/com/linweiyun/genshin/client/render/character/CharacterRenderDispatcher.java:319` 直接写常量 `15728880`）。
- 覆盖层：`net/minecraft/client/renderer/texture/OverlayTexture.java:15,48` —— `NO_OVERLAY`、`pack(u, v)`（受伤红光、附魔闪烁都在这张 16×16 图里）。

一个可以照抄的自定义层（自发光层）：

```java
public final class FullBrightEyesLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>>
        extends RenderLayer<S, M> {

    private final Identifier texture;

    public FullBrightEyesLayer(LivingEntityRenderer<?, S, M> parent, Identifier texture) {
        super(parent);
        this.texture = texture;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
                       S state, float yRot, float xRot) {
        if (state.isInvisible) {
            return;
        }
        collector.order(1).submitModel(
                this.getParentModel(), state, poseStack,
                RenderTypes.eyes(this.texture),
                LightCoordsUtil.FULL_BRIGHT,   // 自发光：忽略环境光
                OverlayTexture.NO_OVERLAY,
                state.outlineColor,
                null);
    }
}
```

### 4.6 方块实体 / 物品 / 粒子：三套并行的 state 体系

| 对象 | 渲染器接口 | 状态基类 | 提交入口 |
|---|---|---|---|
| 实体 | `EntityRenderer<E, S>` | `EntityRenderState` | `EntityRenderer.submit(...)` |
| 方块实体 | `BlockEntityRenderer<BE, S>`（`blockentity/BlockEntityRenderer.java:18,20,26`） | `BlockEntityRenderState`（`blockentity/state/BlockEntityRenderState.java:17-22`） | `LevelRenderer.submitBlockEntities`（`LevelRenderer.java:735`） |
| 物品 | `ItemStackRenderState`（`item/ItemStackRenderState.java:29`） | 同类自身 | `ItemStackRenderState.submit(poseStack, collector, light, overlay, outlineColor)`（:112） |
| 粒子 | `ParticleGroupRenderState`（`state/level/ParticleGroupRenderState.java:11`） | `QuadParticleRenderState` 等（`state/level/QuadParticleRenderState.java:16,23,103`） | `ParticlesRenderState.submit(...)`（`state/level/ParticlesRenderState.java:22`） |

方块实体的提取是"候选 + 淘汰"两段：`BlockEntityRenderDispatcher.tryExtractRenderState(...)`（`blockentity/BlockEntityRenderDispatcher.java:104-105`）先 `createRenderState()` 再 `extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress)`；被视锥剔除的直接丢弃。它的基类 `extractBase` 只抄 `blockPos` / `blockEntityType` / `lightCoords` / `breakProgress`（`blockentity/state/BlockEntityRenderState.java:24-30`）。

物品渲染走的是 `ItemStackRenderState` 自己的状态机（`updateForLiving` / `updateForTopItem` 等），提交时它把里面的 `BakedQuad` 列表交给 `submitItem`。本项目在 `BoneMountGeoLayer.resolveMount` 里用 `Minecraft.getInstance().getItemModelResolver().updateForLiving(itemState, stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, player)` 造物品状态，再在骨骼位姿上提交（`BoneMountGeoLayer.java:143-152` 与 `:283-292`）。

### 4.7 生命周期与常见坑

1. **RenderState 每帧重建，不要跨帧持有。** 提取开始时 `LevelRenderState.reset()` 清空所有列表（`state/level/LevelRenderState.java:37-49`），提交开始时 `LevelRenderer.submitFeatures` 又把这些列表当帧用完即清：`levelRenderState.entityRenderStates.clear()`（`LevelRenderer.java:285`）。抓着一个 `EntityRenderState` 字段在下一帧读，读到的可能是别的实体或已清空的列表元素。
2. **状态里只放纯数据。** 放 `Level` / `Entity` 引用等于把渲染数据结构变成对逻辑线程状态的强引用：实体卸载后你还拿着它，或者读到一个正在被 tick 改写的对象。需要世界数据就在提取阶段抄成数值。
3. **PoseStack 必须配平。** `LevelRenderer` 在关键节点后都会断言栈为空：`checkPoseStack`（`LevelRenderer.java:306,575,718-722`，抛 `IllegalStateException("Pose stack not empty")`）。`submit` 里 push 了忘了 pop，报错点在几百行之外，很难定位。
4. **不要在自己的渲染层里调用 `Minecraft.getInstance().level` 之外的世界查询来"补数据"** —— 提取阶段拿到并塞进 state 才是正路；提交/绘制阶段世界对象可能已经被别的模组或异步区块更新改动。
5. **别在提取阶段提交几何、别在提交阶段改 state。** `SubmitCustomGeometryEvent` 的注释写得很直白：自定义几何用它（`SubmitCustomGeometryEvent.java:25-29`），而自定义状态用 `ExtractLevelRenderStateEvent`（`ExtractLevelRenderStateEvent.java:24-27`）。

### 4.8 案例：一个 boss 实体的三层外观 + 一条世界坐标光环

前七节都是零件，这一节把它们装成一台能跑的机器。需求：

- 自研实体 `VesnaBoss` 身上要有三层外观——本体（普通贴图）、自发光眼睛（不吃环境光）、
  半透明能量壳（蓄力越满越亮）；
- 脚下地面上有一圈光环，它**不跟着实体转身**，必须钉在世界坐标上。

先把「谁提交什么」拆清楚，这张表决定后面所有代码：

| 要画的东西 | 属于谁 | 在哪提交 | RenderType |
|---|---|---|---|
| 本体 | 实体 | `EntityRenderer.submit` | `RenderTypes.entityCutout(TEXTURE)` |
| 自发光眼睛 | 实体 | 同一个 `submit`，`order(1)` 排在本体之后 | `RenderTypes.eyes(GLOW)` |
| 半透明能量壳 | 实体 | 同一个 `submit`，`order(2)` | `RenderTypes.entityTranslucent(TEXTURE, false)` |
| 地面光环 | 整个世界（不属于任何实体） | `SubmitCustomGeometryEvent` | 自定义管线，或 `entityTranslucent` |

判据只有一条：**位置能从实体状态算出来的，归实体渲染器；必须由「这一帧整幅画面」决定的，归事件。**

#### 第一步：状态类

提取阶段只抄数值，不抄对象引用（§4.7 第 2 条）：

```java
public class VesnaBossRenderState extends EntityRenderState {
    /** 0..1，蓄力进度：能量壳的 alpha 与眼睛的强度都看它。 */
    public float charge;
    /** 这一帧要不要画地面光环，以及光环的世界坐标。 */
    public boolean ringVisible;
    public double ringX, ringY, ringZ;
}
```

#### 第二步：渲染器

三层都在 `submit` 里，靠 `order(n)` 排队次：

```java
public class VesnaBossRenderer extends EntityRenderer<VesnaBoss, VesnaBossRenderState> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("minegenshin", "textures/entity/vesna_boss.png");
    private static final Identifier GLOW =
            Identifier.fromNamespaceAndPath("minegenshin", "textures/entity/vesna_boss_glow.png");

    private final VesnaBossModel model;

    public VesnaBossRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new VesnaBossModel(context.bakeLayer(VesnaBossModel.LAYER));
    }

    @Override
    public VesnaBossRenderState createRenderState() {
        return new VesnaBossRenderState();
    }

    @Override
    public void extractRenderState(VesnaBoss entity, VesnaBossRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);   // 父类已填好 x/y/z 插值、包围盒、lightCoords
        state.charge = entity.charge(partialTick);
        state.ringVisible = entity.isCharging();
        state.ringX = entity.getX();
        state.ringY = entity.getY();
        state.ringZ = entity.getZ();
    }

    @Override
    public void submit(VesnaBossRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.isInvisible) {
            return;
        }

        // 层 0：本体
        collector.order(0).submitModel(this.model, state, poseStack,
                RenderTypes.entityCutout(TEXTURE),
                state.lightCoords, OverlayTexture.NO_OVERLAY,
                state.outlineColor, null);

        // 层 1：眼睛全亮，不随天色一起变暗
        collector.order(1).submitModel(this.model, state, poseStack,
                RenderTypes.eyes(GLOW),
                LightCoordsUtil.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                state.outlineColor, null);

        // 层 2：半透明能量壳，alpha 跟着蓄力走
        if (state.charge > 0.01f) {
            int alpha = Mth.clamp((int) (state.charge * 200.0f), 0, 255);
            int tint = (alpha << 24) | 0x00B0E8FF;     // ARGB
            collector.order(2).submitModel(this.model, state, poseStack,
                    RenderTypes.entityTranslucent(TEXTURE, false),
                    state.lightCoords, OverlayTexture.NO_OVERLAY,
                    tint, null, state.outlineColor, null);
        }
    }
}
```

两个最容易踩的点：

- 带颜色（tint）的那次提交用的是**十参数**重载
  `submitModel(model, state, poseStack, renderType, lightCoords, overlayCoords, tintedColor, sprite, outlineColor, crumblingOverlay)`
  （`OrderedSubmitNodeCollector.java:66-77`）。八参数的简写版没有 `tintedColor`，它内部固定传 `-1`
  （`:79-89`），所以想调透明度必须用长的那条。原版给自己上色的写法可以对照
  `LivingEntityRenderer.java:98-100`。另外这个 `tint` 是 **ARGB 整数**，alpha 在最高 8 位，
  不是"一个 0..1 的浮点乘上去"。
- 三次 `submitModel` 会把同一个模型画三遍，这意味着**同一个模型的顶点数被算了三次**。
  想让能量壳"鼓起来"，别用 `poseStack.scale(...)` 直接包住整段——那会把这一层的位置也一起放大，
  壳就飘走了。正确做法是在需要缩放的那一层里 `pushPose()` → `scale` → 提交 → `popPose()`，
  并且确认 `popPose()` 在下一次 `submitModel` 之前执行。

#### 第三步：注册

本模组的真实入口是 `MinegenshinClient`（类上是 `@EventBusSubscriber(modid = Minegenshin.MOD_ID, value = Dist.CLIENT)`，
`MinegenshinClient.java:56`），注册方法长这样（`:171-190`）：

```java
@SubscribeEvent
public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
    event.registerEntityRenderer(ModEntities.VESNA_BOSS.get(), VesnaBossRenderer::new);
}
```

`EntityRenderersEvent.RegisterRenderers`（`EntityRenderersEvent.java:93-104`）在客户端初始化期触发一次，
注册进去的渲染器被缓存进原版的实体渲染器映射。同一族的另外两个：

- `RegisterLayerDefinitions`（`:66-81`）：注册 `ModelLayerLocation` → `LayerDefinition`，
  也就是上面 `context.bakeLayer(VesnaBossModel.LAYER)` 里的那张定义必须先在这里登记；
- `AddLayers`（`:128`）：给**已经存在**的渲染器（原版或其他模组的）追加渲染层，在映射填完之后触发。

#### 第四步：地面光环 —— 提取 + 提交

光环不属于任何实体，走「自定义状态 + 自定义几何」这一对事件。要点是**键必须全局唯一**：
`ContextKey` 的相等性看的是构造时塞进去的 `Identifier`（`ContextKey.java:5-16`），
用本模组的命名空间起名就不会跟别人撞。

```java
// 1) 唯一键，放在客户端类里 static final
private static final ContextKey<List<Vec3>> VESNA_RINGS =
        new ContextKey<>(Identifier.fromNamespaceAndPath("minegenshin", "vesna_rings"));

// 2) 提取阶段：把这一帧所有要画的光环算成「世界坐标」塞进 LevelRenderState
@SubscribeEvent
public static void extractRings(ExtractLevelRenderStateEvent event) {
    LevelRenderState level = event.getRenderState();          // 注意：不是 getLevelRenderState()
    List<Vec3> rings = new ArrayList<>();
    for (Entity entity : event.getLevel().entitiesForRendering()) {
        if (entity instanceof VesnaBoss boss && boss.isCharging()) {
            rings.add(new Vec3(boss.getX(), boss.getY() + 0.05, boss.getZ()));
        }
    }
    level.setRenderData(VESNA_RINGS, rings);                  // BaseRenderState.setRenderData
}

// 3) 提交阶段：转成「相机相对」再提交几何
@SubscribeEvent
public static void submitRings(SubmitCustomGeometryEvent event) {
    List<Vec3> rings = event.getLevelRenderState().getRenderData(VESNA_RINGS);
    if (rings == null || rings.isEmpty()) {
        return;
    }
    Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
    PoseStack poseStack = event.getPoseStack();
    for (Vec3 ring : rings) {
        poseStack.pushPose();
        poseStack.translate(ring.x - camera.x, ring.y - camera.y, ring.z - camera.z);
        event.getSubmitNodeCollector().submitCustomGeometry(
                poseStack,
                RING_RENDER_TYPE.get(),                        // 自定义管线，见第 3 章
                (pose, buffer) -> RingMesh.emit(pose, buffer));
        poseStack.popPose();
    }
}
```

四条容易记错的地方：

- 提取侧的取值方法是 `ExtractLevelRenderStateEvent#getRenderState()`（`ExtractLevelRenderStateEvent.java:68`），
  提交侧却是 `SubmitCustomGeometryEvent#getLevelRenderState()`（`SubmitCustomGeometryEvent.java:52`）。
  两个事件命名不对称，照抄的时候最容易在这里编译不过。
- `setRenderData` 写进去的东西每帧都会没：`LevelRenderState.reset()` 里第一件事就是 `resetRenderData()`
  （`LevelRenderState.java:37,52`）。所以**每次提取都要重新写一遍**，不存在"写一次就一直在"。
- `SubmitCustomGeometryEvent` 在**粒子提交之后、不透明几何之前**触发（`SubmitCustomGeometryEvent.java:28`）。
  想让光环压在实体上面，靠的是它自己的 RenderType 与深度设定，而不是"晚点提交"。
- 它只给 `PoseStack` 和 `SubmitNodeCollector`，**不给实体**。你需要的一切必须在提取阶段就算好放进
  `LevelRenderState`，这也正是 `SubmitCustomGeometryEvent.java:24-26` 那段注释在强调的事。

#### 第五步：交卷前自检

按这五条对一遍，能挡掉本章九成的返工：

1. `createRenderState()` 一帧会被调用很多次，里面只允许 `new`，不许查世界、不许读配置。
2. `extractRenderState` 里 `super.extractRenderState(...)` 必须调，否则实体不会跟着插值移动。
3. `order(n)` 之间是稳定次序，但**同号内不保证先后**——两个 `order(1)` 谁先谁后是未定义的。
4. 一个 `pushPose()` 必须配一个 `popPose()`；`LevelRenderer` 每帧末尾会断言栈为空（`LevelRenderer.java:718-722`），
   报错行离出错处往往有几百行之远。
5. 只要某一层引用了别的对象（玩家、光照、别的实体），那些数据必须在**提取阶段**就抄进 State，
   不能在提交时现查。

### 4.9 补充：给「别人的」渲染器挂层时，数据从哪儿来

§4.8 的例子里，三层外观和光环都是**自己的**实体、自己的渲染器，所以状态字段直接在
`VesnaBossRenderState` 上加就行。但如果目标是**原版或别的模组**的渲染器（想给僵尸加一层、
给玩家加个轮廓），你既改不了它的 RenderState 类，也改不了它的 `extractRenderState`。
NeoForge 为此留了一条通道：**给已有渲染器注册「提取后写数据」的 modifier**。

#### 三个组件

| 组件 | 位置 | 作用 |
|---|---|---|
| `RegisterRenderStateModifiersEvent` | `net/neoforged/neoforge/client/renderstate/` | 在 **mod 总线**、客户端注册 modifier |
| `ContextKey<T>` | `net/minecraft/util/context/ContextKey.java` | 你申请的一个「插槽」，唯一性由构造时的 `Identifier` 决定 |
| `BaseRenderState#setRenderData/getRenderData` | `net/neoforged/neoforge/client/renderstate/BaseRenderState.java` | 所有 `EntityRenderState` 都继承到的读写口 |

`registerEntityModifier` 按**渲染器类**注册，子类一并生效；modifier 在所有原版数据提取完之后执行，
所以你能读到已经填好的 `x/y/z`、`lightCoords` 等字段。

```java
/** 唯一插槽：用本模组的命名空间 + 唯一路径，避免跟别人撞。 */
private static final ContextKey<Float> HURT_GLOW =
        new ContextKey<>(Identifier.fromNamespaceAndPath(Minegenshin.MOD_ID, "hurt_glow"));

@EventBusSubscriber(modid = Minegenshin.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class HurtGlowState {

    @SubscribeEvent
    public static void onRegisterModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(
                new TypeToken<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>>() {},
                (entity, state) -> {
                    float hurt = entity.hurtTime > 0 ? entity.hurtTime / 10f : 0f;
                    float lowHp = 1f - Mth.clamp(
                            entity.getHealth() / Math.max(1f, entity.getMaxHealth()), 0f, 1f);
                    state.setRenderData(HURT_GLOW, Math.max(hurt, lowHp));   // 只放纯数据
                });
    }
}
```

层里照常读，读不到就当「这一帧不画」：

```java
Float glow = state.getRenderData(HURT_GLOW);
if (glow == null || glow <= 0.02f) {
    return;
}
```

#### 四条容易踩的坑

1. **只放纯数据。** 插槽里塞 `Entity` 引用等于把渲染阶段的线程安全问题留给自己（§4.2、§4.7）。
   需要什么就在 modifier 里抄成数值、`Vec3`、枚举。
2. **每帧都会清空。** `RenderStateExtensions.onUpdateEntityRenderState` 开头就调 `resetRenderData()`，
   modifier 每帧都会重新跑；不要指望「写一次一直在」。
3. **modifier 按类缓存。** 注册表按渲染器类做了一次缓存（`Reference2ObjectOpenHashMap`），
   所以**注册必须发生在客户端初始化期**，运行期再注册不会生效。
4. **它只解决「数据怎么带过去」，不解决「画成什么样」。** 层本身仍然是 §4.5 的写法：
   `LivingEntityRenderer.addLayer(...)`，或走 `EntityRenderersEvent.AddLayers` 给已有渲染器追加
   （`EntityRenderersEvent.java:128`，`:203` 的 `getRenderer(EntityType)`）。

> 泛型提示：`addLayer` 需要具体的 `<S, M>`，而 `getRenderer(...)` 返回的是通配类型。
> 给少量实体挂层时，直接对具体类型实例化更清楚；要「一次性给一批生物挂同一种层」，
> 用 `@SuppressWarnings` + 原始类型转换是常见折中。

## 5. GeckoLib 5：骨骼动画与渲染层

### 5.1 一趟渲染的调用顺序（这才是 5.5.6 的真实骨架）

入口是 `GeoRenderer#performRenderPass`（`com/geckolib/renderer/base/GeoRenderer.java:107-139`），顺序如下（每一步都写在同一段方法体里）：

```
performRenderPass(renderState, poseStack, renderTasks, cameraState, boneUpdaters)
 1. poseStack.pushPose()
 2. renderType = getRenderType(renderState, getTextureLocation(renderState))
 3. renderPassInfo = RenderPassInfo.create(this, renderState, poseStack, cameraState, renderType != null)
 4. 把你传入的 boneUpdaters 逐个 addBoneUpdater(...)
 5. firePreRenderEvent(...)             —— 事件能被取消，取消则整趟不画
 6. preRenderPass(renderPassInfo, renderTasks)          GeoRenderer.java:144
 7. scaleModelForRender(renderPassInfo, 1, 1)           GeoRenderer.java:149
 8. adjustRenderPose(renderPassInfo)                    GeoRenderer.java:160
 9. preApplyRenderLayers(renderPassInfo, renderTasks)   GeoRendererInternals.java:132
       ├─ layer.preRender(...)                     （隐藏骨骼、登记数据都在这里做）
       └─ layer.addPerBoneRender(...)              （把每骨骼任务登记进去）
10. renderPassInfo.captureModelRenderPose()             RenderPassInfo.java:322
11. submitRenderTasks(renderPassInfo, renderTasks, renderType)      GeoRenderer.java:172
       └─ renderTasks.submitCustomGeometry(poseStack, renderType, (pose, vc) -> {
              poseStack.last().set(pose);
              renderPassInfo.renderPosed(() -> model.render(renderPassInfo, vc, light, overlay, color));
          })
12. submitPerBoneRenderTasks(renderPassInfo, renderTasks)            GeoRendererInternals.java:161
13. applyRenderLayers(renderPassInfo, renderTasks)                   GeoRendererInternals.java:140
       └─ layer.submitRenderTask(...)              （想额外画东西的层在这里提交）
14. poseStack.popPose()
15. postRenderPass(renderPassInfo, renderTasks)                      GeoRenderer.java:200
```

两个容易被忽略的点：

- **第 3 步就把模型烘焙好了**：`RenderPassInfo.create` 里做 `renderer.getGeoModel().getBakedModel(...)`，并把两个内置 updater（`renderer::applyAnimationControllers`、`renderer::adjustModelBonesForRender`）挂上去（`RenderPassInfo.java:279-291`）。
- **第 11 步是"提交"而不是"立即画"**：`submitCustomGeometry` 的回调在真正的绘制阶段才执行，回调拿到的是提交时刻捕获的 pose，而模型渲染读的是 `RenderPassInfo` 里的 PoseStack（本项目 `BoneMountGeoLayer.submitSubModelAtBone` 的注释写清了这一点，`BoneMountGeoLayer.java:295-303`）。

数据准备在更早的 `fillRenderState`（`GeoRendererInternals.java:115-130`），它按顺序做：

```
captureDefaultRenderState()   → 填入 INSTANCE_ID / ANIMATABLE_MANAGER / PARTIAL_TICK / RENDER_COLOR /
                                PACKED_OVERLAY / IS_MOVING / ANIMATABLE_CLASS / TICK
addRenderData()               → 你自己的覆写（renderer 级别的数据）
getGeoModel().addAdditionalStateData()
每个 layer.addRenderData()    → 你自己的覆写（layer 级别的数据）
fireCompileRenderStateEvent()
setMolangQueryValues()
AnimationProcessor.extractControllerStates()   → 控制器状态存进 DataTickets.ANIMATION_CONTROLLER_STATES
```

即：**renderer 的 `addRenderData` 先于所有 layer 的 `addRenderData`**，layer 之间按注册顺序。`GeoObjectRenderer` 还额外把 `packedLight` 塞进 `DataTickets.PACKED_LIGHT`（`GeoObjectRenderer.java:70-76`）。

### 5.2 `GeoRenderLayer` 的四个钩子

`com/geckolib/renderer/layer/GeoRenderLayer.java:27-90`：

| 钩子 | 行 | 时机 | 用途 |
|---|---|---|---|
| `addRenderData(animatable, relatedObject, renderState, partialTick)` | 69 | 提取阶段，早于渲染 | 把这一帧要用的数据写进 renderState（用 DataTicket） |
| `preRender(renderPassInfo, renderTasks)` | 76 | 渲染趟开始、模型提交之前 | 改骨骼（`addBoneUpdater`）、预计算、一次性准备工作 |
| `submitRenderTask(renderPassInfo, renderTasks)` | 81 | 主模型与每骨骼任务之后 | 再画一层（换纹理、叠加特效） |
| `addPerBoneRender(renderPassInfo, consumer)` | 90 | `preApplyRenderLayers` 里被调用 | 把「一根骨骼 + 一个回调」登记进去，回调执行时 PoseStack 已经摆到那根骨骼的位姿 |

回调形态是 `PerBoneRender`（`renderer/base/PerBoneRender.java:14,20`）：

```java
@FunctionalInterface
public interface PerBoneRender<R extends GeoRenderState> {
    void submitRenderTask(RenderPassInfo<R> renderPassInfo, GeoBone bone, SubmitNodeCollector renderTasks);
}
```

注册方式（照抄本项目 `WeaponAnchorGeoLayer.java:70-93` 的写法）：

```java
@Override
public void addPerBoneRender(RenderPassInfo<R> info, BiConsumer<GeoBone, PerBoneRender<R>> consumer) {
    GeoBone bone = info.model().getBone("RightHand").orElse(null);
    if (bone == null) {
        return;                            // 骨骼不存在：什么都不做，不要抛异常
    }
    consumer.accept(bone, (pass, b, tasks) -> {
        Matrix4f pose = new Matrix4f(pass.poseStack().last().pose());   // 已含骨骼位姿
        // … 提交或记录
    });
}
```

执行点在 `GeoRendererInternals.submitPerBoneRenderTasks`（`:161-193`）：它先 `renderPosed(...)` 应用所有骨骼快照，把 PoseStack 重置到 `getModelRenderMatrixPose()`，再对每根骨骼 `RenderUtil.transformToBone(poseStack, bone)`，然后调用你的回调。

`preRender` 里改骨骼的正确姿势是 `addBoneUpdater`（`RenderPassInfo.java:177-185`）：

```java
renderPassInfo.addBoneUpdater((info, snapshots) ->
        snapshots.ifPresent("Head", snapshot -> snapshot.skipRender(true)));
```

注意注释的原话：*"Can only be called prior to the renderer submitting this pass for rendering. Updaters added after that point will be ignored"*，迟到的 updater 只会打一条 error 日志。原因是 updater 列表被 `DeferredCache` 在第一次 `renderPosed` 时锁定并消费（`RenderPassInfo.java:50, 293-306`）。

### 5.3 骨骼位姿的生命周期：快照、监听器、定位器

**骨骼没有"持久位置"。** 渲染趟里那根骨骼的矩阵，是「动画采样结果 + 层级变换累乘」的临时产物；GeckoLib 在 `addBonePositionListener` 的注释里把这件事写死了：*"Use this to capture bone matrix positions at the time of render, which is the only time they actually have a position of any kind"*（`RenderPassInfo.java:189-194`）。因此想在别处用骨骼位置，只有两条合法路径：

1. **每骨骼回调里就地取矩阵**：`addPerBoneRender` + `poseStack.last().pose()`（见上一节）。
2. **登记位置监听器**：`renderPassInfo.addBonePositionListener(boneName | GeoBone, listener)`（`RenderPassInfo.java:189-199`）或 `addLocatorPositionListener(locatorName, listener)`（`:203-210`），回调签名是
   `void accept(@Nullable Vec3 worldPos, @Nullable Vec3 modelPos, @Nullable Vec3 localPos)`（`RenderPassInfo.java:271-274`）。
   监听器在 `renderPosed` 里被装到骨骼/定位器上、在 `finally` 里清掉（`RenderPassInfo.java:213-256`），并由 `RenderUtil.prepMatrixForBoneAndUpdateListeners` 在执行时刷新（`com/geckolib/util/RenderUtil.java:90-103`）。

骨骼的可写数据走 `BoneSnapshot`（`com/geckolib/animation/state/BoneSnapshot.java`）：

- 读：`getScaleX/Y/Z`（46-58）、`getTranslateX/Y/Z`（61-73）、`getRotX/Y/Z`（76-86）、`isHidden()`（92）、`areChildrenHidden()`（97）；
- 写：`setScale/setTranslation/setRotation`（117/147/178）、`skipRender(boolean)`（213）、`skipChildrenRender(boolean)`（220）；
- 执行：`apply()`（244）把快照写回骨骼，`cleanup()`（249）用完复原。
- 取值入口是函数式接口 `BoneSnapshots`：`get(String)` 返回 `Optional<BoneSnapshot>`、`ifPresent(String, Consumer)`（`renderer/base/BoneSnapshots.java:13-25`）。

定位器 `GeoLocator` 是模型里声明的"空挂点"（`com/geckolib/cache/model/GeoLocator.java:12-27`，字段 `parent/name/offsetX.../rotX...`），监听器刷新在 `updatePositionListeners(PoseStack, RenderPassInfo)`（`:82`）。

### 5.4 层间传数据：`DataTicket`

GeckoLib 的 RenderState 是一个"类型安全的 Map"。票据用 `DataTicket.create(id, Class)` 或 `DataTicket.create(id, TypeToken)` 造（`com/geckolib/constant/dataticket/DataTicket.java:30,38`），去重靠 `(Type, id)`（`:15-16`），所以**必须自己建票**，不要复用别人的 id。

```java
private static final DataTicket<Player> OWNER =
        DataTicket.create("minegenshin_weapon_anchor_owner", Player.class);   // WeaponAnchorGeoLayer.java:47-49
```

读写都在 `GeoRenderState` 的三个 default 方法上（`renderer/base/GeoRenderState.java:29,43,111`）：`addGeckolibData` / `getGeckolibData` / `getOrDefaultGeckolibData`。内置票据在 `com/geckolib/constant/DataTickets.java:33-72`，常用的有：`ANIMATABLE_MANAGER`、`ANIMATABLE_INSTANCE_ID`、`PARTIAL_TICK`、`TICK`、`PACKED_LIGHT`、`PACKED_OVERLAY`、`RENDER_COLOR`、`POSITION`、`VELOCITY`、`IS_MOVING`、`ANIMATION_CONTROLLER_STATES`、`PER_SLOT_RENDER_DATA`。

对**实体**渲染，`EntityRenderState` 本身就被注入成了 `GeoRenderState`：GeckoLib 用 duck-typing mixin 给原版类补上 `getDataMap()`（`com/geckolib/mixin/client/EntityRenderStateMixin.java:17-52`），所以 `GeoEntityRenderer` 的 RenderState 直接就是原版的 `EntityRenderState` 子类，不需要另造一层。

### 5.5 本项目的真实接法

角色渲染不继承 `GeoEntityRenderer`，而是自己组装（`CharacterRenderDispatcher.java:330-352`）：

```java
CharacterPlayerModel model = ...;                          // 每个角色一份
CharacterRenderer renderer = new CharacterRenderer(model);
renderer.withRenderLayer(new BoneMountGeoLayer(renderer));          // 骨骼挂点：把武器画到骨骼上
renderer.withRenderLayer(new TranslucentBoneGeoLayer(renderer));    // 半透明骨骼
renderer.withRenderLayer(new WeaponAnchorGeoLayer(renderer));       // 只读位姿，喂给 Photon
```

每帧的调用（`:319`）：

```java
target.renderer().performRenderPass(target.animatable(), player, poseStack, bufferSource,
                                    cameraState, 15728880, partialTick, submitUpdater);
```

这走的是 `GeoObjectRenderer.performRenderPass` 的 8 参重载（`GeoObjectRenderer.java:65-76`）：它先 `fillRenderState(animatable, relatedObject, createRenderState(animatable, null), partialTick)`，把 `packedLight` 写进 `DataTickets.PACKED_LIGHT`，再把可选的 `BoneUpdater` 转成单元素 list 交给 `GeoRenderer.super.performRenderPass`。

`submitUpdater` 是"多个 updater 串起来"的组合体（`CharacterRenderDispatcher.combine`，`:323-334`）：外观、道具、面部、傀儡、武器的五组 updater 依次跑；调试开关打开时再包一层探针。

骨骼数据还有三个不走渲染层的来源：

| 类 | 职责 | 关键点 |
|---|---|---|
| `render/geo/AssetGeoCache.java:34-63` | 自己实现的资源包缓存：把 `item` / `block` / `entity` 三类资源里的 `.geo.json` 烘焙成 `BakedGeoModel`、动画烘焙成 `BakedAnimations`，按 `Identifier` 查 | 它实现 `PreparableReloadListener`，资源重载时整体替换 `volatile` 字段；渲染层只调用 `model(...)` / `animation(...)`，不要自己去读文件 |
| `CharacterAppearanceOptionBones.updaterFor(player, character)` | 外观可选项（武器/部件的显隐、换装）的 `BoneUpdater`：按外观掩码 + 当前武器类型 + 动画状态算出骨骼名单 | 飞行时换成"放飞骨骼"上那一份（长柄飞行是骑着武器当扫帚，武器不能再被手臂动画拖着走）；`fly_start` 前摇单独一套显隐，否则会出现"手里还亮着一把、身下又冒出一把" |
| `CharacterPropBones` | 道具骨骼（`MFly` / `FJO` / `tea` / `ysmGlow_texiao*`）的显隐判定 | `SCREEN_FORWARD_PUSH = -8.0f` 这类数字的单位是"模型像素"（1 格 = 16），换算成方块要除以 16 |

角色模型的 `adjustRenderPose` 被**故意清空**（`CharacterRenderer.java:41-44`），理由是 `GeoObjectRenderer` 的基类实现带 `translate(0.5, 0.51, 0.5)`——那是给以方块角为原点的摆件模型准备的补偿，而角色模型以原点为中心、实体提交给它的 pose 本身就没有半格偏移（`GeoObjectRenderer.java:52-55` 对比 `EntityRenderDispatcher.submit`）。

三条渲染层各自的分工：

| 层 | 关键实现 | 说明 |
|---|---|---|
| `BoneMountGeoLayer` | `addRenderData` 解析挂点（`:137-181`）→ `preRender` 里 `snapshot.skipRender(true)` 隐藏原骨骼（`:230-243`）→ `addPerBoneRender` 登记（`:248-274`）→ `submitAtBone` 画内容（`:276-331`） | 画不出来的情况一律"不隐藏、不挂"，避免角色凭空少一块（类注释 `:60-88`） |
| `TranslucentBoneGeoLayer` | 同上，改半透明 RenderType | 与挂点层同一套骨架 |
| `WeaponAnchorGeoLayer` | 只在 `addPerBoneRender` 里读矩阵，不提交任何几何（`:70-111`） | 结果写进 `WeaponAnchorCache`，供 Photon 的帧回调使用 |

`WeaponAnchorGeoLayer` 的完整数据流（这是"骨骼位姿怎么跨系统传递"的标准范例）：

```
提取阶段：addRenderData()        → renderState.addGeckolibData(OWNER, player)      :61-67
渲染趟中：addPerBoneRender()     → 只对「第一个挂点骨骼」登记回调                    :70-93
回调执行：capture(player, info)  → Matrix4f bonePose = new Matrix4f(poseStack.last().pose())
                                   local  = bonePose.getTranslation(new Vector3f())
                                   world  = cameraState.pos + local                :96-110
                                   WeaponAnchorCache.put(player, world, rotation)
读取侧：  WeaponAnchorCache.fresh(player)  → 只在 3 个渲染帧内有效，否则返回 null   WeaponAnchorCache.java:33,58-64
```

缓存用"渲染帧号"做新鲜度，帧号由 `RenderFrameEvent.Pre` 每帧自增（`CharacterRenderDispatcher.java:113-115`）。为什么不用实体刻：角色在第一人称、离屏或被其他模组挡住时根本不会被渲染，此时缓存自然过期，调用方保持上一帧位姿而不是把特效瞬移到原点（`WeaponAnchorCache.java:20-31` 的注释）。

另外，几何提交被统一收口到 `GeoRenderIntercept.trySubmit`（`optimize/GeoRenderIntercept.java:117`），角色、本模组实体、其它模组的 GeckoLib 实体走同一份代码；`CharacterRenderer.submitRenderTasks` 只是把默认实现显式留了一份兜底（`CharacterRenderer.java:63-68`）。

### 5.6 从零写一个自定义 `GeoRenderLayer`：最小可抄骨架

```java
public final class MuzzleAnchorLayer<T extends GeoAnimatable, O, R extends GeoRenderState>
        extends GeoRenderLayer<T, O, R> {

    /** 自己的票据：id 全局唯一，类型写具体类，别用 Object。 */
    private static final DataTicket<Player> OWNER =
            DataTicket.create("example_muzzle_owner", Player.class);

    public MuzzleAnchorLayer(GeoRenderer<T, O, R> renderer) {
        super(renderer);
    }

    /** ① 提取阶段：把这一帧要用的东西摘出来。 */
    @Override
    public void addRenderData(T animatable, @Nullable O relatedObject, R renderState, float partialTick) {
        if (relatedObject instanceof Player player) {
            renderState.addGeckolibData(OWNER, player);
        }
    }

    /** ② 渲染趟内：登记「某根骨骼被摆好那一刻」的回调。 */
    @Override
    public void addPerBoneRender(RenderPassInfo<R> info,
                                 BiConsumer<GeoBone, PerBoneRender<R>> consumer) {
        Player player = info.getGeckolibData(OWNER);
        if (player == null) {
            return;
        }
        info.model().getBone("Muzzle").ifPresent(bone -> consumer.accept(bone, (pass, b, tasks) -> {
            Matrix4f pose = new Matrix4f(pass.poseStack().last().pose());
            Vector3f local = pose.getTranslation(new Vector3f());
            Vec3 world = new Vec3(pass.cameraState().pos.x + local.x,
                                  pass.cameraState().pos.y + local.y,
                                  pass.cameraState().pos.z + local.z);
            // ③ 在这里做你要做的事：缓存位姿、喂给粒子系统、或者用 tasks 提交几何。
        }));
    }
}
```

挂到渲染器上：`renderer.withRenderLayer(new MuzzleAnchorLayer<>(renderer))`（`GeoObjectRenderer.java:105-118`）；渲染器由 GeckoLib 创建时也可以走一次性事件 `CompileEntityRenderLayersEvent`（`com/geckolib/event/entity/CompileEntityRenderLayersEvent.java:19`）——它只在渲染器第一次初始化时触发一次，`GeoRenderLayersContainer.getRenderLayers()` 里懒触发（`renderer/layer/GeoRenderLayersContainer.java:24-40`）。

三个硬约束：

1. **不要缓存 `GeoBone` / `RenderPassInfo` / `PoseStack`**：它们只在渲染趟内有效（骨骼对象是共享的烘焙模型的一部分，快照才是每趟的）。
2. **不要在自己的层里 `poseStack.pushPose()` 后不 pop**：整趟的 push/pop 由 `performRenderPass` 管（`GeoRenderer.java:108,137`），层里属于"借用"，配平即可。
3. **渲染只读，数据在提取阶段备好**：`adjustModelBonesForRender` 的注释明确写着 *"No manipulation of the RenderState is permitted here"*（`GeoRenderer.java:163-165`）。

### 5.7 案例：把「手上那把武器」交给 Photon（完整走一遍）

这是本项目真实在跑的接线（`client/render/character/WeaponAnchorGeoLayer.java` +
`WeaponAnchorCache.java` + `client/fx/FxAnchor.java`），也是「骨骼位姿怎么跨系统传递」的标准答案。

需求：技能起手时要在**武器尖端**放一圈特效。难点在于——武器尖端不是一个实体状态，
它只在骨骼被摆好的那一瞬间才存在。所以这一层要做的事只有一件：**把那个瞬间的位姿抄下来**，
它自己不画任何东西。

#### 数据怎么过夜：先想清楚读者是谁

| 问题 | 答案 | 结果 |
|---|---|---|
| 谁写 | 模型的提交阶段（渲染线程） | 普通 `HashMap` 即可，不需要锁 |
| 谁读 | Photon 的每帧回调（同样是渲染线程） | 不需要 `volatile`、不需要并发容器 |
| 什么时候失效 | 角色没被渲染的那一帧就不该更新 | 新鲜度用**渲染帧号**，不用实体刻 |

最后一行是这套设计的重点。用实体刻做时间戳会有一个很难查的 bug：角色切到第一人称、
走到屏幕外、或者被别的模组挡住时**根本不会被渲染**，可实体刻照常在走，于是缓存"看着很新"，
特效就被钉在一个几秒前的位置上。用渲染帧号就自然多了——没渲染，帧号不涨，缓存过期，
调用方保持上一帧的位姿，比把特效瞬移到原点体面得多。

#### 第一步：票据

```java
/** 把「本趟渲染属于哪个玩家」从提取阶段带到渲染阶段的票据。 */
private static final DataTicket<Player> OWNER =
        DataTicket.create("minegenshin_weapon_anchor_owner", Player.class);
```

票据的 id 自己起，类型写具体类。GeckoLib 的去重键是 `(Type, id)`（`DataTicket.java:15-16`），
所以"id 一样但类型不同"也是两张不同的票，不要靠 id 省事。

#### 第二步：提取阶段只做一件事——记住这是谁的渲染

```java
@Override
public void addRenderData(T animatable, @Nullable O relatedObject, R renderState, float partialTick) {
    if (relatedObject instanceof Player player) {
        renderState.addGeckolibData(OWNER, player);
    }
}
```

这里传进来的 `relatedObject` 是渲染器绑定的那个世界对象（对角色渲染管线来说就是玩家）。
**渲染趟里拿不到它**，所以必须现在塞进 State。

#### 第三步：登记「这根骨骼被摆好那一刻」的回调

```java
@Override
public void addPerBoneRender(RenderPassInfo<R> info,
                             BiConsumer<GeoBone, PerBoneRender<R>> consumer) {
    Player player = info.getGeckolibData(OWNER);
    if (player == null) {
        return;
    }
    String boneName = weaponBone(player);          // 角色声明的第一个挂点骨骼 = 手上的武器
    if (boneName == null) {
        return;
    }
    GeoBone bone = info.model().getBone(boneName).orElse(null);
    if (bone == null) {
        return;                                     // 模型里没这根骨骼：静默跳过，别抛异常
    }
    consumer.accept(bone, (pass, ignoredBone, tasks) -> capture(player, pass));
}
```

三层 `return` 是刻意的：没声明挂点的角色、模型里没有这根骨骼的角色、不是玩家在渲染的场合，
都直接不做事。渲染层是**全局**挂上去的，对全项目所有走这条管线的角色都会跑，所以"什么都不做"
必须是它的默认行为。

#### 第四步：取位姿——注意坐标系

```java
private static void capture(Player player, RenderPassInfo<?> info) {
    Matrix4f bonePose = new Matrix4f(info.poseStack().last().pose());

    Vector3f local = bonePose.getTranslation(new Vector3f());
    Quaternionf rotation = bonePose.getUnnormalizedRotation(new Quaternionf());

    Vec3 camera = info.cameraState().pos;
    Vec3 world = new Vec3(camera.x + local.x, camera.y + local.y, camera.z + local.z);

    if (world.distanceToSqr(player.position()) > SANITY_DISTANCE * SANITY_DISTANCE) {
        return;                                     // 坐标系假设被打破：宁可这一帧不更新
    }
    WeaponAnchorCache.put(player,
            new Vector3f((float) world.x, (float) world.y, (float) world.z), rotation);
}
```

关键就是那句 `world = camera + local`：提交阶段 `PoseStack` 的原点是**相机**（§6.2 有完整推导），
所以从矩阵里 `getTranslation` 出来的是相机相对坐标，必须加上 `cameraState().pos` 才是世界坐标。

`SANITY_DISTANCE = 8.0` 那道检查看着多余，其实是防御性的：它假设"武器骨骼离玩家不会超过 8 格"。
一旦将来的渲染管线改了坐标系（比如某个版本改成以世界原点为基准），结果就会离谱地远，
这时整帧丢弃，特效停在上一帧，比飞到天边容易定位得多。

#### 第五步：读取侧怎么用

```java
// 渲染层写入
WeaponAnchorCache.put(player, worldPos, worldRot);

// Photon 每帧回调读取（client/fx/FxAnchor.java）
WeaponAnchorCache.Entry entry = WeaponAnchorCache.fresh(player);
if (entry == null) {
    return;                      // 这一帧没渲染过：保持上一次的位姿，不要重置
}
AnchorPose pose = entry.toPose();
```

`fresh(...)` 的判定是 `renderFrame() - entry.frame() > 3` 就返回 `null`（`WeaponAnchorCache.java:33,58-64`），
留 3 帧余量是为了不让帧序抖动引起闪一下。缓存超过 64 条时会顺手清掉过期项，避免长时间挂机后越攒越多。

#### 第六步：挂到渲染器上，然后自检

```java
renderer.withRenderLayer(new WeaponAnchorGeoLayer<>(renderer));
```

`withRenderLayer` 出自 GeckoLib 基类（`GeoObjectRenderer.java:105-118`）；走 GeckoLib 自己的
渲染器创建流程时，也可以用一次性事件 `CompileEntityRenderLayersEvent`（`CompileEntityRenderLayersEvent.java:19`），
它只在渲染器第一次初始化时触发。

自检清单：

1. **有没有提交几何？** 这一层一个顶点都不该提交。只读层的成本接近 0，一旦开始画东西，
   它的成本就变成"每个角色每帧多画一遍模型"。
2. **有没有缓存 `GeoBone` / `RenderPassInfo` / `PoseStack`？** 它们只在渲染趟内有效
   （骨骼对象是共享的烘焙模型的一部分）。要留就留**算出来的数值**。
3. **失败路径是不是静默的？** 没挂点、缺骨骼、离得太远——三种情况都应该悄悄跳过，
   让特效停在原地，而不是抛异常或瞬移。
4. **读的地方是不是也判了空？** 写入和读取是两条独立的路径，读侧必须自己处理 `null`。

> 另一种写法：`RenderPassInfo#addBonePositionListener(String boneName, BonePositionListener listener)`
> （`RenderPassInfo.java:188-196`）是 GeckoLib 直接提供的同款入口，注释原话是
> *"the only time they actually have a position of any kind"*。本项目没用它，而是自己走
> `addPerBoneRender` + 缓存，原因是**读侧（Photon 回调）需要一个"最近 3 帧内有效"的窗口**，
> 而监听器只在渲染那一刻回调一次，没有地方挂这个时间戳。两套都能用，按读侧的形态选。

## 6. 坐标空间、矩阵与四元数

### 6.1 一条渲染路径上的所有空间

```
① 模型本地空间   顶点在 .geo 模型里的坐标（单位是「模型像素」，1 格 = 16）
② 骨骼空间       骨骼的 pivot / 旋转 / 缩放，靠这条链累乘到模型根
③ 实体局部空间   模型根位姿（renderer 的 scale / rotate / 微调都作用在这里）
④ 相机相对空间   MC 提交几何时 PoseStack 的坐标原点 —— 世界坐标减去相机坐标
⑤ 视图空间       ModelViewMat（相机旋转 + 平移）
⑥ 裁剪空间       ProjMat * viewPosition
⑦ 屏幕空间       NDC → 视口
```

②→③ 由 GeckoLib/原版模型渲染做（`RenderUtil.transformToBone`、`BoneSnapshot.apply`），
③→④ 由渲染器自己决定（`adjustRenderPose` 之类），④→⑤→⑥ 由着色器里的 `ModelViewMat` / `ProjMat` 做 ——
这三条变换在自定义管线里必须自己写对（本项目 `entity_skinned.vsh` 只做 ④→⑤→⑥，见第 7 章）。

### 6.2 关键结论：提交阶段 PoseStack 的原点是相机

证据链：

1. `LevelRenderer.submitEntities` 传入的坐标已经减掉了相机：`entityRenderDispatcher.submit(state, cameraRenderState, state.x - camX, state.y - camY, state.z - camZ, ...)`（`net/minecraft/client/renderer/LevelRenderer.java:724-733`）。
2. `EntityRenderDispatcher.submit` 直接把这个值当作 `translate`：`poseStack.translate(relativeX, relativeY, relativeZ)`（`net/minecraft/client/renderer/entity/EntityRenderDispatcher.java:147-166`）。
3. 方块实体同理：`poseStack.translate(blockPos.getX() - camX, blockPos.getY() - camY, blockPos.getZ() - camZ)`（`LevelRenderer.java:735-745`）。
4. 相机本身的位置就在 `CameraRenderState.pos`（`state/level/CameraRenderState.java:16`），朝向在 `orientation`（`:23`），视图旋转矩阵在 `viewRotationMatrix`（`:30`）。

所以两条换算是全文最常被写错的地方：

```java
// 世界坐标 → 提交用坐标（我要在世界某点放东西）
Vec3 local = world.subtract(cameraRenderState.pos);
poseStack.pushPose();
poseStack.translate(local.x, local.y, local.z);   // 之后按需要 rotate / scale
submitNodeCollector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> { … });
poseStack.popPose();

// 提交用坐标 → 世界坐标（我从 PoseStack 里读到一个位置）
Vec3 world = cameraRenderState.pos.add(
        poseStack.last().pose().getTranslation(new Vector3f()));
```

本项目里这两条都有现成的实现：

- 正向（世界 → 提交）：`BoneMountGeoLayer.submitItemAtBone` 在骨骼位姿上 `translate(offset / 16f, ...)` 后提交物品（`BoneMountGeoLayer.java:283-292,333-345`）。
- 反向（提交 → 世界）：`WeaponAnchorGeoLayer.capture` 把骨骼矩阵的平移加上 `cameraState.pos` 得到骨骼的世界坐标（`WeaponAnchorGeoLayer.java:96-110`）。

忘了减相机的典型症状是"特效/模型死死跟着镜头飞"，而且在相机位置为 (0,0,0) 的测试场景里看不出问题 —— 这是它最坑的地方。

### 6.3 PoseStack 的语义

`com/mojang/blaze3d/vertex/PoseStack.java` 的真实 API：

| 方法 | 行 | 说明 |
|---|---|---|
| `pushPose()` | 51 | 复制栈顶，在栈顶之上工作 |
| `popPose()` | 61 | 丢弃栈顶 |
| `translate(double x, double y, double z)` / `(float…)` / `(Vec3)` | 27 / 31 / 35 | 平移 |
| `scale(float x, float y, float z)` | 39 | 缩放（同时更新法线矩阵） |
| `mulPose(Quaternionfc)` | 43 | 旋转（后乘） |
| `mulPose(Matrix4fc)` / `(Transformation)` | 81 / 85 | 乘任意矩阵 |
| `rotateAround(Quaternionfc, float px, float py, float pz)` | 47 | 绕指定点旋转 |
| `last()` | 69 | 取栈顶 `Pose` |
| `isEmpty()` | 73 | 栈是否为空（配平检查用） |
| `Pose.pose()` / `Pose.normal()` / `Pose.set(Pose)` | 105 / 109 / 99 | 4×4 位姿矩阵 / 3×3 法线矩阵 / 覆盖 |

**组合顺序**：`Pose` 里的三个操作都是"右乘新矩阵"（`mulPose` 语义），也就是说**后调用的变换先作用在顶点上**。

```java
poseStack.translate(1, 0, 0);                                     // 先：把物体挪到 (1,0,0)
poseStack.mulPose(Axis.YP.rotationDegrees(90));                   // 后：再绕 Y 轴自转
// 顶点经历了「先自转、再平移」，得到的是「在 (1,0,0) 处一个转了 90° 的物体」
```

写模型位移时的惯例顺序是 `translate`（到挂点）→ `mulPose`（朝向）→ `scale`（大小），本项目 `applyMountTransform` 就是这个顺序（`BoneMountGeoLayer.java:333-345`），GeckoLib 的骨骼链也是 `translate → rotate → scale`（`com/geckolib/util/RenderUtil.java:90-103`）。

法线不会自动"跟着转"：`Pose.normal()` 是 `pose` 的逆转置 3×3（`PoseStack.java:94-97`），非等比缩放之后必须用它而不是直接拿 `pose` 的左上角去变换法线。

### 6.4 角度、弧度与四元数

Minecraft 与 GeckoLib 的"度/弧度"分工是这样的：

| 位置 | 单位 | 证据 |
|---|---|---|
| 实体 `yRot` / `xRot`、`Axis.*.rotationDegrees(deg)` | 度 | `com/mojang/math/Axis.java:7-22`（`XP = angle -> new Quaternionf().rotationX(angle)`，`rotationDegrees` 才乘 `π/180`） |
| 纯 JOML 的四元数 API | 弧度 | JOML 1.10.8 `org.joml.Quaternionf`：只有 `rotationX/Y/Z`、`rotationXYZ`、`rotationZYX`、`rotationYXZ`、`rotationAxis`，没有"Deg"版本（`javap -classpath joml-1.10.8.jar org.joml.Quaternionf`） |
| GeckoLib 的模型 JSON / 动画文件 | 度 | 加载期统一换算：`GeometryBone.java:70`、`GeometryCube.java:53`、`GeometryLocator.java:39` 用 `Math.toRadians`；关键帧在 `AnimationProcessor.java:266` 用 `Mth.wrapDegrees(value * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD` |
| GeckoLib 运行时（`GeoBone.roty`、`BoneSnapshot.getRotX`） | 弧度 | 上一条的换算结果直接喂给 JOML 的弧度 API：`RenderUtil.optionalRotateZYX` → `quat.rotationZYX((float)z, (float)y, (float)x)`（`RenderUtil.java:193-213`） |

由此得出两条实战规则：

1. 用 `Mth.DEG_TO_RAD` / `Mth.RAD_TO_DEG` 或 `Axis.XP.rotationDegrees(...)` 明确标注单位，**不要**把模型里抄来的数字直接塞进 `new Quaternionf().rotationY(...)`。
2. 从 `BoneSnapshot.getRotX()` 读出来的值要拿去和人写的角度比较时，先 `* Mth.RAD_TO_DEG`。

本项目的 Photon 锚点把这套单位约定固化成了 `AnchorPose`（`client/fx/AnchorPose.java:17-37`）：它明确写着四元数是"弧度语义"，并提供两个把度数转成四元数的工厂：

```java
public static Quaternionf yaw(float yawDegrees) {
    return new Quaternionf().rotationY((float) Math.toRadians(yawDegrees));
}

public static Quaternionf look(float yawDegrees, float pitchDegrees) {
    return new Quaternionf().rotationYXZ((float) Math.toRadians(-yawDegrees),
                                         (float) Math.toRadians(pitchDegrees), 0f);
}
```

四元数的三个常用操作：`new Quaternionf(other)`（拷贝）、`normalize()`（归一化，避免多帧累乘后漂移）、`rotationYXZ`（先 Y 后 X 后 Z，MC 实体的偏航/俯仰就是这一组）。从矩阵里取朝向用 `Matrix4f#getUnnormalizedRotation(Quaternionf)` 或 `getNormalizedRotation(...)`；本项目取的是前者，因为它只需要朝向、不需要归一化（`WeaponAnchorGeoLayer.java:98-99`）。

### 6.5 症状 → 原因 → 修法

| 症状 | 原因 | 修法 |
|---|---|---|
| 模型/特效死死跟着镜头，人往前走它也跟着走 | 把世界坐标直接 `translate` 进了提交阶段（原点其实是相机） | 减掉 `cameraRenderState.pos`，或用 `LevelRenderer.submitEntities` 那样传 `pos - camPos` |
| 在 (0,0,0) 附近正常，走远后越偏越多 | 同上（相机位置偏移被当成绝对坐标） | 同上；测试时务必离开世界原点 |
| 模型整体被推到斜后方半格 | 用了摆件渲染器的默认补偿 `translate(0.5, 0.51, 0.5)`（`GeoObjectRenderer.java:52-55`） | 实体/角色渲染器覆写 `adjustRenderPose` 为空，本项目就是这么做的（`CharacterRenderer.java:41-44`） |
| 模型倒置 / 镜像 | 骨骼旋转符号：GeckoLib 加载期把 X/Y 取负、Z 不变（`GeometryBone.java:70`） | 不要在运行时再补一次负号；要改就在模型源文件改 |
| 旋转方向反了 | 度/弧度混用（差 57.3 倍），或 `yaw` 的正负约定搞反 | 用 `AnchorPose.yaw/look` 这类已封装度数的工厂；`Mth` 里换算 |
| 非等比缩放后光照变暗/发黑 | 用 `pose` 而不是 `normal()` 变换法线 | 用 `poseStack.last().normal()`（`PoseStack.java:109`） |
| 远处的模型不随雾衰减，只有地形褪色 | 自定义管线里雾距离用的是视图空间坐标，没有换回相机相对世界坐标 | 本项目 `entity_skinned.vsh` 用 `transpose(mat3(ModelViewMat)) * (viewRelative - ModelViewMat[3].xyz)` 还原 |
| 多帧之后旋转抖动、角度漂移 | 四元数没归一化，或每帧把上帧结果再乘一次 | 每次乘完 `normalize()`；动画驱动的位置每帧从源头重算，不要累加 |

### 6.6 案例：三个坐标 bug 的完整修复过程

下面三个都来自本项目真实踩过的坑。写法是「症状 → 怎么取证 → 定位 → 修法 → 验证」，
你可以把它当成排查模板：**先证明坐标在哪一步错了，再改代码**，别凭感觉调数值。

#### bug 1：特效贴着镜头跑

**症状**：粒子/模型一直糊在屏幕中央，玩家往前走它也跟着走，看起来像屏幕空间效果。

**取证**：

1. 把特效的位置参数打印出来（世界坐标），确认它确实是你要的那个点；
2. 在渲染回调里打印 `poseStack.last().pose()` 的平移分量 —— 如果它约等于 0，
   说明顶点被放在「相机原点」附近。

**定位**：提交阶段的 `PoseStack` 原点是相机（§1.5）。代码把世界坐标直接塞进了栈，没有减相机位置。

**修法**：

```java
// 错：直接把世界坐标丢进提交用的 PoseStack
pose.translate(world.x, world.y, world.z);

// 对：先减相机位置，得到相机相对坐标
Vec3 camera = levelRenderState.cameraRenderState.pos;
pose.translate(world.x - camera.x, world.y - camera.y, world.z - camera.z);
```

**验证**：站着不动转 360° 视角，特效应该始终钉在世界的同一点；走到远处再看一遍。

#### bug 2：第一人称或离屏时，挂点「冻住」

**症状**：切到第一人称、或者把角色转出屏幕后，挂在武器骨骼上的特效停在最后一帧的位置不动，
甚至粘在半空中。

**取证**：这个 bug 的取证关键是意识到「**没被渲染的实体根本不会产生骨骼位姿**」。
在抓位姿的地方打点记录"本帧有没有真的抓到"，会看到一段段空缺。

**定位**：骨骼位姿只在渲染那一刻存在（§5.3）。角色不可见时渲染器根本不会被调用，
于是缓存里只有旧数据。项目里的做法是给缓存加**帧龄上限**并用渲染帧号而不是 tick 计时
（`WeaponAnchorCache.java:33` 的 `MAX_AGE_FRAMES = 3`、`:63` 的 `fresh()`），
超过 3 帧没更新就返回 `null`。

**修法**：拿不到新鲜位姿时要**明确选择一种降级行为**，而不是继续用旧值：

```java
var pose = WeaponAnchorCache.fresh(uuid);
if (pose == null) {
    // 选项 A：本帧不跟随（特效停住但不会飞出去，适合常驻光环）
    return;
    // 选项 B：回退到实体自身的世界坐标（适合武器光效，至少还在角色身上）
    // fallbackTo( player.position(), player.getYRot() );
}

// 另外：结果与角色实际位置差太远时整帧丢弃 —— 宁可停住也不要它飞到天边
if (worldPos.distanceToSqr(player.position()) > SANITY_DISTANCE * SANITY_DISTANCE) {
    return;
}
```

（`SANITY_DISTANCE = 8.0` 与这段检查在 `WeaponAnchorGeoLayer.java:52`、`:105`。）

**验证**：第一人称 / 第三人称 / 把角色藏到方块后面，三种情况分别看特效：应该「停住或回退」，
而不是飞到天上或跟到镜头里。

#### bug 3：远处只有地形褪色，模型不褪

**症状**：走远之后地形被雾遮住，但角色/自定义模型依然清晰，像浮在雾上。

**取证**：把雾相关的那段 GLSL 输出成纯色，看它在视图空间里算出来的距离是不是"离相机越远越大"。
如果它几乎不随距离变，说明取错了坐标。

**定位**：自定义管线的顶点着色器里，雾距离必须按「相机相对的世界坐标」算，
而 `ModelViewMat` 已经在相机空间里；直接拿它算会混入模型自身的旋转/缩放。

**修法**（本项目的真实写法，`assets/minegenshin/shaders/core/entity_skinned.vsh`）：

```glsl
// 先把视图空间坐标还原成"相机相对的世界坐标"，再交给原版的雾函数
vec3 cameraRelative = transpose(mat3(ModelViewMat)) * (viewRelative - ModelViewMat[3].xyz);
```

**验证**：走到雾的边界处，模型与地形应该**同时**被雾吞掉；换个距离再看一次。

## 7. GPU 蒙皮与渲染性能

### 7.1 本项目的骨骼调色板链路（可直接照抄的 GPU 蒙皮实现）

整体数据流（每一步都能在仓库里查到）：

```
编译期（资源加载后只做一次）
  GeoCompileCache / CompiledGeoModel    把 geo 模型编译成「骨骼数组 + 顶点区间」
  SkinnedMesh.compile(model)            把顶点打包成 GpuBuffer（顶点常驻显存）
        VertexFormat FORMAT             Position(vec3) / UV0(vec2) / Normal(RGBA8_SNORM) / BoneIds(RGBA16_UINT)

每帧（每个被接管的模型一次：GeoRenderIntercept.java:241）
  BoneMatrixPalette.compute(...)        深度优先前序走骨骼树 → 写 MATRICES / NORMAL_MATRICES
        → SkinDataStorage.write(...)    写进一块环形常量缓冲，返回 GpuBufferSlice
        → SkinnedSubmit                 记录 mesh / skinData / runs / 管线，交给 FeatureRenderer

绘制期
  SkinnedFeatureRenderer.executeGroup → draw(submit, encoder)
        renderPass.setPipeline(submit.pipeline())
        renderPass.setUniform("SkinData", submit.skinData())
        renderPass.setVertexBuffer(0, mesh.buffer().slice())
        renderPass.drawIndexed(quads * 6, 1, 0, firstVertex, 0)
  顶点着色器 entity_skinned.vsh 用 Bones[bone] 做蒙皮、NormalBones[bone] 转法线
```

关键实现点逐条拆开：

**① 常量缓冲的布局与容量**（`optimize/gpu/SkinDataStorage.java`）

```text
MAX_BONES = 128                                                     :31
SIZE = 128*64 (mat4 Bones[128]) + 128*48 (mat3 NormalBones[128]) + 16 (ivec4 LightOverlay) + 16 (vec4 Color)
     = 14368 字节                                                   :37
blockSize = ceilDiv(SIZE, minUniformOffsetAlignment) * 该对齐       :106
```

注释写明了为什么要自己写一份而不能直接用原版的 `DynamicUniformStorage`：原版对 blockSize 做的是**向下取整**（`Mth.roundToward`），14368 在 64 字节对齐的驱动上会被截成 14336，写入直接 `BufferOverflowException`（`SkinDataStorage.java:16-24`）。这是"照抄原版"时最容易踩的坑。

缓冲本体是 `MappableRingBuffer`（`net/minecraft/client/renderer/MappableRingBuffer.java:13,20,39,50`）：`USAGE_MAP_WRITE | USAGE_UNIFORM`，每写一条前进一个 block，帧末 `rotate()` 翻页（翻页时机挂在 `FlipFrameEvent` 上，`MinegenshinClient.java:136-139`）；容量不够时翻倍重建，**旧缓冲要留到帧末再关**，因为这一帧已经发出去的绘制还在读它（`SkinDataStorage.java:117-160`）。

**② std140 布局的对齐规则**（`optimize/gpu/BoneMatrixPalette.java:25-28,175-210`）

```java
/** std140 里 Bones[] 的定长字节数。法线块必须从这里开始，不能紧接「已写骨骼数」之后。 */
private static final int NORMAL_BLOCK_OFFSET = SkinDataStorage.MAX_BONES * 64;
/** std140 里 LightOverlay 的起始偏移 = Bones + NormalBones 的字节数。 */
private static final int LIGHT_OVERLAY_OFFSET = SkinDataStorage.MAX_BONES * 64 + SkinDataStorage.MAX_BONES * 48;

// std140 的 mat3 是「3 列，每列 4 个 float」：写 3 个分量后必须跳过第 4 个。
data.position(NORMAL_BLOCK_OFFSET);
for (int i = 0; i < boneCount; i++) {
    for (int column = 0; column < 3; column++) {
        data.putFloat(...); data.putFloat(...); data.putFloat(...);
        data.putFloat(0f);                       // 补齐到 vec4
    }
}
```

要点：

- **数组按定长偏移，不按写入长度**：着色器里是 `mat4 Bones[128]`，即使这一帧只用了 7 根骨骼，`NormalBones[]` 也必须从 `128 * 64` 开始写。写成 `position(写过的字节数)` 会让每根骨骼读到别人的法线，表现为光照逐帧乱跳。
- **mat3 在 std140 里占 48 字节**（3 列 × 16），不是 36。
- `ivec4 LightOverlay` 里 `.xy` 是方块光/天空光、`.zw` 是覆盖层 uv（和原版 `UV2`/`UV1` 的分工一致，`BoneMatrixPalette.java:198-203`）。
- 尺寸可以先用 `com/mojang/blaze3d/buffers/Std140SizeCalculator.java:8-44`（`putMat4f()` / `putVec4()` / `putIVec4()` / `align(int)`）算一遍，原版自己的投影矩阵 UBO 就是这么算的（`RenderSystem.java:38`）。

**③ 可见区间与 draw call 合并**（`BoneMatrixPalette.java:81-137`）

顶点在缓冲里的排布顺序 = 骨骼树的深度优先前序，所以"一根骨骼的几何"永远是连续区间；隐藏骨骼时**游标必须照样推过去**（顶点还在缓冲里占位），否则后面所有兄弟骨骼的区间都会前移，画到别人的顶点上（`collect` 的注释写的正是这个）。相邻可见区间会被合并成一条 run（`appendRun`），每条 run 最终对应一次 `drawIndexed`（`SkinnedFeatureRenderer.java:160-167`），所以 **runs 条数就是这次渲染的 draw call 数**。

**④ 管线派生**（`optimize/gpu/SkinnedPipelines.java`）

```java
public static final BindGroupLayout SKIN_DATA_LAYOUT = BindGroupLayout.builder()
        .withUniform("SkinData", UniformType.UNIFORM_BUFFER)            // :38-40
        .build();

private static RenderPipeline derive(RenderPipeline base) {
    return RenderPipeline.builder(base)                                 // 从原版管线派生
            .withLocation(Minegenshin.id("pipeline/skinned_" + ...))
            .withBindGroupLayout(SKIN_DATA_LAYOUT)
            .withVertexShader(...)                                      // :101-110
            .build();
}

public static void registerPipelines(RegisterRenderPipelinesEvent event) {
    for (RenderPipeline base : WHITELIST) { event.registerPipeline(derive(base)); }   // :111-116
}
```

白名单是 8 条原版实体管线（`entity_solid`、`entity_solid_z_offset_forward`、`entity_cutout`、`entity_cutout_cull`、`entity_cutout_z_offset`、`entity_translucent`、`entity_translucent_cull`、`entity_translucent_emissive`，`SkinnedPipelines.java:57-65`），派生时把顶点格式、图元拓扑、深度/混合/stencil 原样带过来，只换 shader 与绑定组。**这就是"不改视觉、只换顶点变换"的正确姿势**：任何一条不在白名单里的 RenderType（比如光影包替换过的实体管线）都会走回 CPU 蒙皮。

**⑤ 顶点格式必须与着色器输入逐字对齐**（`optimize/gpu/SkinnedMesh.java:41-47`）

```java
public static final VertexFormat FORMAT = VertexFormat.builder(0)
        .addAttribute("Position", GpuFormat.RGB32_FLOAT)   // 0 偏移，vec3
        .addAttribute("UV0",      GpuFormat.RG32_FLOAT)    // 12 偏移，vec2
        .addAttribute("Normal",   GpuFormat.RGBA8_SNORM)   // 20 偏移，4 字节法线
        .addAttribute("BoneIds",  GpuFormat.RGBA16_UINT)   // 24 偏移，4 个 u16 = 骨骼号 + 法线修正位
        .build();                                          // 32 字节/顶点
```

着色器侧必须同名同序（`src/main/resources/assets/minegenshin/shaders/core/entity_skinned.vsh`）：

```glsl
in vec3 Position;
in vec2 UV0;
in vec3 Normal;
in uvec4 BoneIds;

layout(std140) uniform SkinData {
    mat4 Bones[128];
    mat3 NormalBones[128];
    ivec4 LightOverlay;
    vec4 Color;
};
```

管线编译期是按**名字**把顶点格式接到着色器输入上的，改一处不改另一处 = 属性错位（`SkinnedMesh.java:26-31` 的注释）。

**⑥ 提交节点的形态**：`SkinnedSubmit implements SubmitNode`（`optimize/gpu/SkinnedSubmit.java:21`），并刻意**不实现** `BatchableSubmit` —— 每个模型一块顶点缓冲、每帧一块骨骼数据，合批只会让状态切换更糟；它只在 `prepare()` 里抓一次 `renderType.prepare()`（`:80-88`），把动态变换、裁剪、纹理定下来。

### 7.2 26.2 里可用的 GPU 数据手段（除本项目的 UBO 方案外）

| 手段 | 真实 API | 适用 |
|---|---|---|
| 常量缓冲（UBO） | `GpuDevice#createBuffer(label, USAGE_MAP_WRITE / USAGE_UNIFORM, size)`（`com/mojang/blaze3d/systems/GpuDevice.java:140,148`）、`GpuBuffer#slice(offset, length)`（`buffers/GpuBuffer.java:45`）、`GpuBufferSlice#map(read, write)`（本项目 `SkinDataStorage.write`）、`RenderPass#setUniform(String, GpuBufferSlice)`（`systems/RenderPass.java:106,110`） | 每帧少量、结构固定的数据（骨骼矩阵、光照、颜色） |
| 环形暂存 | `MappableRingBuffer`（`net/minecraft/client/renderer/MappableRingBuffer.java:13-60`）、`StagingBuffer`（`com/mojang/blaze3d/vertex/StagingBuffer.java:121-127`） | 一帧内多次写入、每帧翻页 |
| 顶点缓冲 | `GpuDevice#createBuffer(..., ByteBuffer data)` 上传 + `RenderPass#setVertexBuffer(slot, slice)`（`:150`） | 静态几何常驻显存（本项目做法） |
| 索引缓冲 | `RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS)` + `IndexType`（`SkinnedFeatureRenderer.java:118-126`） | 顺序索引（`i,i+1,i+2,i+2,i+3,i`），配合 `drawIndexed(..., baseVertex)` |
| 绘制 | `RenderPass#drawIndexed(indexCount, instanceCount, firstIndex, vertexOffset, firstInstance)`（`:170`） | 每次 run 一次调用 |
| 实例化 | `drawIndexed` 的 `instanceCount` 参数（同签名） | 同一网格多份数据（本项目暂未使用，见下条备注） |
| GPU 计时 | `GpuDevice#createTimestampQueryPool` + `CommandEncoder#writeTimestamp` + `GpuQueryPool#getValues`（`optimize/gpu/SkinnedGpuTimer.java:19,42,60,105,121`） | 量自己那批绘制的 GPU 时间 |

> 未确认：本项目没有实例化渲染的实例，`drawIndexed` 的 `instanceCount` 语义（每实例读取哪些逐实例属性）需要按具体管线设计再核；本节只确认了签名存在。

### 7.3 性能预算与测量

**先分清你量的是哪一段**：

| 读数 | 量的是什么 | 在哪看 |
|---|---|---|
| CPU 提交（walk / us per model） | 骨骼遍历 + 写顶点缓冲的 CPU 时间 | 本项目 F3 的 `mg-render` 一行，由 `optimize/RenderOptimizeStats.java:60,94` 汇总 |
| GPU 蒙皮真实 GPU 时间 | 本模组那批 skinned 绘制的 GPU 时间轴跨度 | `optimize/gpu/SkinnedGpuTimer.java:69`（三格轮转、隔帧取回、乘 `timestampPeriod` 换成纳秒；驱动不支持时整栏隐藏） |
| 实体提取数量 | 这一帧提取了多少个实体状态 | F3 的实体渲染统计（`DebugEntryEntityRenderStats` → `LevelExtractor.entityStatistics()`，`extract/LevelExtractor.java:538`） |
| 区块编译 / GPU 利用率 | 地形侧开销 | 原版 metrics 采样器，`MetricSampler.createExtractSampler(name, MetricCategory.…, supplier)`（`client/profiling/ClientMetricsSamplersProvider.java:36-55`） |

**预算怎么定**：帧时间是可以直接换算的硬指标 —— 60 FPS 时一帧 16.6 ms，144 FPS 时 6.9 ms；任何一个"每帧都跑一遍"的自定义渲染代码，只要超过帧时间的几个百分点就值得优化。本项目的 `RenderOptimizeStats` 把 CPU 开销表示成帧时间的百分比而不是绝对微秒，原因就在注释里：绝对数字随场景规模变，占比才能横向比较（`RenderOptimizeStats.java:20-23`）。

**优化顺序（经验值，按收益从高到低）**：

1. **先减 draw call 与状态切换**：合批相同 RenderType、合并连续可见区间（本项目 `appendRun`）、避免每根骨骼一次 `drawIndexed`。
2. **再减少每帧 CPU 工作**：把"每帧重算"的东西编译期算好（本项目的 `CompiledGeoModel`）、把"每个模型一份"的数据合并成一块缓冲（本项目的骨骼矩阵 UBO 方案）。
3. **然后才是 GPU 侧**：顶点常驻显存（GPU 蒙皮）省的是 CPU 上传，不省 GPU 光栅化；如果瓶颈在填充率，要动的是分辨率 / 粒子数 / 是否做全屏后处理，而不是蒙皮。
4. **最后才是降质量**：降低模型面数、关闭某些层。顺序反了会"优化了半天没感觉"。

**两个必须遵守的工程约束**（都是本项目踩出来的）：

- **失败要能回退，不要崩帧**：`SkinDataStorage.write`、`SkinnedMesh.compile`、`SkinnedGpuTimer` 三处都是"失败 → 返回 null → 回退 CPU 蒙皮"，只有"设备不支持"这种不可恢复的错误才永久停用（`SkinDataStorage.java:44-47,117-140`）。
- **外部环境会打破你的假设**：光影包会替换实体管线、其他模组可能改顶点格式，所以 `SkinnedPipelineGuard` 会在这两种情况下主动让位给 CPU 蒙皮，并把原因显示在 F3 上（`RenderOptimizeStats.java:30-35`）。写自定义管线时都要留这样一道闸门。

> 未确认：本项目没有对"GPU 蒙皮 vs CPU 蒙皮"的实测帧率对比数据（只有逐帧的 CPU/GPU 读数）。要不要据此给出"提升 xx%"的结论，需要一次单独的性能对比测试。

### 7.4 案例：把 CPU 蒙皮换成 GPU 蒙皮，到底要动什么

**先看两种做法的差别**（这是决定"值不值得做"的唯一依据）：

| | CPU 蒙皮（GeckoLib 默认路径） | GPU 蒙皮（本项目 `optimize/gpu` 这条路） |
|---|---|---|
| 每帧做什么 | 遍历骨骼 → 在 CPU 上把该骨骼的顶点逐个变换 → 写进顶点缓冲 | 只算骨骼矩阵（≤128 根）写进一块 UBO，顶点变换交给顶点着色器 |
| 顶点量变大时的代价 | 线性上涨（顶点越多，CPU 越忙） | 基本不变（矩阵数量与顶点数无关） |
| 每帧上传量 | 整个模型的顶点数据 | 一块 `mat4[128] + mat3[128] + …` 的常量缓冲 |
| 实现复杂度 | 低（框架已经做好） | 高（要自己派生管线、自己管缓冲、自己做回退） |
| 什么时候值得 | 顶点少、模型少 | 同屏角色多、模型顶点多、CPU 侧"walk"时间占比高 |

**要动的六个文件**（按数据流顺序，每个职责都别越界）：

| 文件（`src/main/java/com/linweiyun/genshin/client/render/optimize/gpu/`） | 职责 |
|---|---|
| `BoneMatrixPalette.java` | 骨骼树的遍历顺序 + 每根骨骼的矩阵打包（深度优先前序，与顶点区间一一对应） |
| `SkinDataStorage.java` | 把打包好的矩阵写进常量缓冲（失败返回 `null`，**不抛异常**） |
| `SkinnedMesh.java` | 顶点格式与几何区间（`FORMAT` 的属性名必须与着色器逐个对上，见 §3.5） |
| `SkinnedFeatureRenderer.java` | 每帧收集可见区间、合并连续 run、每个 run 一次 `drawIndexed` |
| `SkinnedPipelines.java` | 从原版 `entity` 管线派生出 skinned 版本（换顶点着色器、换顶点格式、追加 `SkinData` 绑定组） |
| `SkinnedPipelineGuard.java` | 判断"现在还能不能走 GPU 这条路"，不能就整体让位给 CPU 蒙皮 |

**两条工程约束必须照抄**（都是本项目踩出来的）：

1. **失败要能回退，不要崩帧**：`SkinDataStorage.write`、`SkinnedMesh.compile`、`SkinnedGpuTimer`
   在出错时一律返回 `null` 并回退 CPU 蒙皮；只有"设备根本不支持"这种不可恢复的情况才永久停用
   （`SkinDataStorage.java:44-47`、`:117-140`）。
2. **外部环境会打破你的假设**：光影包会替换实体管线、别的模组可能改顶点格式，
   所以 `SkinnedPipelineGuard` 会在这两种情况下主动让位，并把原因写进 F3
   （`RenderOptimizeStats.java:30-35`）。任何"替换原版渲染路径"的优化都要有这道闸门。

**怎么确认它真的在工作**：

1. 打开 F3：`RenderOptimizeStats` 会把 `walk`（CPU 侧骨骼遍历 + 写顶点的耗时）、
   占帧百分比 `(x.x% of frame)`、以及 GPU 侧计时（`SkinnedGpuTimer` 的纳秒读数）显示出来；
2. `walk` 明显下降、帧率不变或上升，说明 CPU 侧省下来了；
3. 如果 F3 上出现"让位给 CPU 蒙皮"的原因，说明它其实没生效 —— 先解决那个原因，别急着调参。

**一句话结论**：GPU 蒙皮省的是 **CPU 时间**，不是"画面更好"；顶点少的时候做它反而更亏
（多了一条管线、多了一层回退逻辑）。判断标准只有一个：**`walk` 占帧时间的比例**。

## 8. Photon2：从编辑器到运行时

> 这一章是**使用手册**：第 8 章讲"怎么把效果做出来"（编辑器、粒子系统、材质、Timeline、后处理、着色器图），
> 第 9 章讲"代码怎么接管它"，第 10 章是能照着抄的完整案例，第 11 章是出事时怎么查。
> 阅读顺序建议：先看 §8.1 建立心智模型 → 跟着 §8.2 做第一个效果 → 需要什么查 §8.4 的模块表。
>
> 本节事实来源：官方文档站 <https://low-drag-mc.github.io/LowDragMC-Doc/zh/photon2/>（访问 2026-09-28）
> 与本机 Photon `26.2.2.3` 源码（`C:\Users\Linweiyun\.gradle\caches\...\photon-neoforge-26.2\26.2.2.3\*-sources.jar`）。
> 两者不一致的地方，本文以源码为准，并会写明差异。官方文档站描述的是 MC 1.21.1 时代，概念通用、命名偶有出入。

### 8.1 Photon2 是什么：先把心智模型建立起来

#### 8.1.1 一句话定义

Photon2 是一套**跑在 Minecraft 客户端里的实时 VFX 工具**：它把粒子、拖尾（Trail）、光束（Beam）、
时间轴（Timeline）、着色器图（Shader Graph）和基于图的后处理（Post Processing）整合在**同一个编辑器**里，
做出来的东西既能手动播放，也能由模组代码接管生命周期。

#### 8.1.2 它不是光影包

新人最容易搞混的一点：Photon2 和 Iris/OptiFine 的"光影包（shaderpack）"**不是一回事**，它们是两个层面的东西：

| | 光影包（Iris / OptiFine shaderpack） | Photon2 |
|---|---|---|
| 改什么 | **替换原版的渲染管线**：天空、水、阴影、后处理整条链 | **在画面之上叠加内容**：粒子、拖尾、光束、自己的后处理 |
| 谁写、写什么 | 写一大堆 GLSL，按光影包的格式组织 | 在编辑器里搭对象树；需要时再写 Shader Graph |
| 和模组的关系 | 模组通常"兼容/让位"，不改它 | 模组可以**直接调用它**，把特效绑到实体、骨骼、方块上 |
| 会不会互相影响 | 会：光影包替换实体管线时，走自定义管线的模组要主动让位（见 §7.4 的回退闸门） | 会：Photon 的合成时机与光影包有关，官方提供 `/photon_iris` 诊断命令 |

所以「我装了光影包」和「我的特效能不能显示」是两个独立问题；特效不显示时先确认是哪一层的问题（§11 有排查表）。

#### 8.1.3 心智模型：Authored 与 Runtime

Photon2 里所有东西都分成两半，理解这条分界线，后面就不会乱：

```
你在编辑器里做的（Authored，文件）        游戏里跑起来的（Runtime，对象）
  .fxproj  可编辑工程                      FXRuntime   一次播放实例
    └── 导出                            ←── 由 FX#createRuntime() 创建
  .fx      运行时定义（按路径引用资源）       ├── FXObject 树（Empty / Emitter / Field …）
  .fxpack  连依赖一起打包的分发包            ├── 每个 Emitter 的 ParticleRuntime（运行时数据）
                                          └── 每帧提交的后处理请求
```

- **文件是模板**：`.fx` 只是"效果长什么样"的描述，加载一次可以反复实例化；
- **实例才是对象**：同一个 `.fx` 可以同时在十个角色身上播放，每个 `FXRuntime` 有自己独立的状态；
- **改运行时不会改文件**：Java 侧注入的数据都写在 Runtime 上（§9.6），想永久改参数得回编辑器。

#### 8.1.4 三条并行的使用路径

| 你想干什么 | 走哪条 | 入口 |
|---|---|---|
| 先把效果做出来、看看好不好看 | 编辑器 | `/photon_editor`（仅单人世界） |
| 快速绑定一下、验证导出文件对不对 | 命令 | `/photon fx …`（§8.2.6） |
| 让效果跟着角色/武器/技能走、多人可见 | Java API | `FXHelper` + Executor（§9、§10） |

官方文档里写得很直接：**如果效果的生命周期由模组管理，就用 Java API，不要用命令**。
命令适合验证，不适合做玩法。

#### 8.1.5 环境要求（本仓库的实际条件）

| 项 | 要求 | 本仓库 |
|---|---|---|
| 运行侧 | 只在**客户端**（渲染与播放 API 全是客户端类） | 同 |
| 依赖 | Photon2 与 LDLib2 版本要互相兼容 | Photon `26.2.2.3` + LDLib2 `26.2.2.41.a` |
| 编辑器 | **只能在单人世界打开**（要访问本机项目与资源文件） | 同 |
| 资源位置 | `<游戏目录>/ldlib2/assets/`（可被多个工程共享） | 打包时进 `assets/<命名空间>/…` |

### 8.2 编辑器与项目：从零做出第一个效果

#### 8.2.1 打开编辑器

装好互相兼容的 Photon 与 LDLib2，进**创造模式的单人世界**：

```mcfunction
/photon_editor
```

打不开的常见原因就两个：在多人服务器里（编辑器需要本机文件系统），或者 Photon/LDLib2 版本不匹配。

#### 8.2.2 界面：六个区域各干什么

编辑器用的是 LDLib2 的编辑器框架，布局和普通资源编辑器接近，但多了实时场景和时间轴：

| 区域 | 用途 | 你在这里做什么 |
|---|---|---|
| FX Hierarchy | 对象树 | 创建/命名/排序/复制对象，设置父子关系 |
| Scene | 实时预览 | 看形状、拖动对象、控制播放与重启 |
| Inspector | 属性面板 | 改 Transform、发射器参数、模块开关、材质与渲染设置 |
| Resources | 资源库 | 建/复用材质、Shader 图、曲线、渐变、颜色、网格 |
| Timeline | 时间轴 | 排演出、做属性动画、播声音、提交后处理 |
| History | 操作历史 | 撤销/重做 |

最大化之后这几块可以同屏；选中一个对象，各个面板会联动到它 —— 这是排查"改了半天没变化"的第一招：
先确认 Inspector 顶端显示的是不是你以为的那个对象。

#### 8.2.3 三种文件：`.fxproj` / `.fx` / `.fxpack`

| 文件 | 是什么 | 什么时候用 |
|---|---|---|
| `.fxproj` | **可编辑工程**：存对象树、Timeline、对资源（材质/图/曲线/网格）的引用 | 你日常编辑保存的就是它 |
| `.fx` | **运行时定义**：压缩过的、按路径引用资源的播放文件 | 本机跑、依赖已存在时 |
| `.fxpack` | **分发包**：zip 格式，把效果**和它依赖的资源**一起装进去 | 要发给别人 / 打进模组 jar |

除了这三种，还有一批可复用资源文件（`*.material.nbt`、`*.shader_graph.nbt`、`*.shader_function.nbt`、
`*.fullscreen_graph.nbt`、`*.render_graph.nbt`、`*.mesh.nbt`），它们由 Resources 面板管理。

> **最容易犯的错**：把 `.fxproj` 当成能发布的东西。它引用的资源路径是你的本机环境，
> 别人拿到跑不起来。要发布就用 `.fx`（依赖齐全）或 `.fxpack`（依赖打包）。

#### 8.2.4 资源放在哪、id 怎么算

- 文件资源放 `<游戏目录>/ldlib2/assets/` 下，可以被多个工程共享；**内置资源是只读的**，别指望改它。
- 导出时选的目标路径就是运行时 id 的来源。官方例子里导出到
  `/ldlib2/assets/photon/fx/first_effect.fx`，对应运行时 id 就是 **`photon:first_effect`**。
- 打进模组时资源在 `assets/<你的命名空间>/fx/<路径>.fx`，id 就是 `<你的命名空间>:<路径>`。
- 放进资源包的路径**用小写字母、不要空格**（这是资源包本身的规矩，不是 Photon 的）。
- 从编辑器外面改了资源文件，要先**资源重载**再看预览，否则看到的是旧定义。

#### 8.2.5 命名规则（这决定了 Java 能不能找到你的对象）

- 需要通过代码控制的对象，**必须起稳定且唯一的名字** —— Java 侧用
  `FXRuntime#findObject("名字")` 找它（§9.5）。
- 序列化与 Timeline 绑定用的是 **UUID**，名字只是给人看的、允许重复；
  但你要用 Java 找它时，重名就等于埋雷（`findObject` 只回第一个匹配）。
- Empty 对象除了分组，还有一个用途：**给 Java 提供一个稳定的控制点**（§8.3）。

#### 8.2.6 案例：做出第一个效果并在世界里播放

这是官方"快速开始"的完整流程，我把它补齐了每一步的**判断标准**：

1. **建工程**：`File → New → FX Project`，存成 `first-effect.fxproj`。
2. **建发射器**：在 root 下新建 Particle Emitter。第一个效果就用官方给的一组保守参数：

   | 设置 | 值 | 说明 |
   |---|---|---|
   | Duration | 40 ticks | 一个循环的长度（tick，不是秒；20 tick = 1 秒） |
   | Looping | 开 | 循环发射 |
   | Start Lifetime | 20 | 单个粒子活 20 tick |
   | Start Speed | 0.05 | 初始速度（格/tick 量级，别用 1 起步，会飞太远） |
   | Start Size | 0.2 | 初始尺寸 |
   | Emission Rate | 2 | 每 tick 生成 2 个 |
   | Shape | Sphere | 从一个球体里生成 |

   *判断标准*：结构一变 Scene 会重新预览。想对比参数就按 restart / pause 两个控件。
3. **给材质**：`Resources → Material` 里选内置的 `circle` 等粒子贴图，拖到发射器 Renderer 的 materials 列表。
   Renderer 决定"怎么画"（层、排序、裁剪、Mask、Model 模式、实例化），Material 决定"用什么着色器和贴图"。
4. **加运动与颜色**：开 `Color over Lifetime`，把末端 alpha 拉成 0（粒子淡出）；
   想让它生成后继续加速就开 `Velocity over Lifetime` 或 `Force over Lifetime`。
5. **保存**：`File → Save` 写 `.fxproj`。
6. **导出**：`File → Export → FX`，导出到 `/ldlib2/assets/photon/fx/first_effect.fx`。
   引用了自定义材质/图/网格/贴图时，改用 FX Pack 导出。
7. **在游戏里播放**（关掉编辑器）：绑到脚下方块

   ```mcfunction
   /photon fx photon:first_effect block ~ ~-1 ~ 0 0 0 1 1 1 0 false true
   ```

   *看不到效果时按顺序查*：① 是不是还在编辑器里；② 用的 id 是否等于导出的路径；
   ③ 换过导出文件却还用旧定义，就 `/photon_client clear_client_fx_cache` 清掉 FX 定义缓存；
   ④ 粒子全被清过之后不残留，就 `/photon_client clear_particles`。

#### 8.2.7 命令速查（验证用）

```mcfunction
/photon_editor                       # 打开编辑器（单人）
/photon fx <id> block  <pos> [offset] [rotation] [scale] [delay] [forcedDeath] [allowMulti] [checkState]
/photon fx <id> entity <selector>    [offset] [rotation] [scale] [delay] [forcedDeath] [allowMulti] [autoRotate]
/photon fx remove block  <pos> <force> [id]
/photon fx remove entity <selector> <force> [id]
/photon_client clear_particles        # 清粒子 + Executor 缓存，并让缓存的 Runtime 失效
/photon_client clear_client_fx_cache  # 清 FX 定义缓存与列表缓存
/photonfx list | test <effect> [weight] | clear   # 后处理测试（2.2.0+）
/photon_iris status | dump | overlay on | mode auto  # 光影兼容诊断（2.2.2+）
```

几个参数值得单独记：

- `offset / rotation / scale`：分别是在锚点上的**局部位移**（默认 `0 0 0`）、
  **角度制欧拉角**（默认 `0 0 0`）、root 缩放（默认 `1 1 1`）。
- `delay`：启动延迟，单位 tick。
- `forcedDeath`：锚点消失时是否**立刻删掉残留粒子**（默认 false，粒子会飘完自己的寿命）。
- `allowMulti`：同一锚点上允不允许再挂一个同名 FX（默认 false）。
- `autoRotate`：实体挂载时用哪种朝向 —— `none`（只用配置的 rotation）/ `forward`（沿实体前进方向）/
  `look`（跟视线）/ `xrot`（跟身体朝向）。

### 8.3 对象层级与三种空间

#### 8.3.1 层级：root → Empty → Emitter

每个 Runtime 都有一个**始终存在的 root 对象**。你在编辑器里建的对象要么直接挂在 root 下，
要么用 Empty 组织成层级。父级的 Transform、`active`、`visible`、`time scale` 会影响整棵子树。

Empty 的四个正经用途（它不是"占位废物"）：

1. 把多个发射器当作一个整体来移动/旋转（比如"武器上的一整套光效"）；
2. 给 Timeline 提供一个共享的旋转轴心（pivot）；
3. 一次性激活/重启/变速整棵子树；
4. 给 Java 代码一个稳定的命名控制点（`findObject("muzzle")` 这种）。

#### 8.3.2 Transform 的四条规则

| 操作 | 结果 |
|---|---|
| 保持世界变换重新设父级 | 重新计算局部值，对象在世界里**不动** |
| 移动父级 | Local 空间的子对象跟着走 |
| 旋转父级 | 子对象的位置和朝向绕父级轴心一起转 |
| 缩放父级 | 子对象继承缩放（**非等比缩放会改变向量基准**，见下） |

> 非等比缩放（比如 `1 1 2`）会让"方向"的含义变形：Custom 空间和轨道运动会跟着变。
> 调试时不要只在 `1 1 1` 下看，要在真实缩放值下看一遍。

#### 8.3.3 `active` / `visible` / `timeScale` 的区别

| 字段 | 关掉会怎样 |
|---|---|
| `selfActive = false` | 该对象**和它的子级**都停止 tick 与渲染（真停） |
| `selfTimelineVisible = false` | 只隐藏渲染，**不改编辑器里的 `selfVisible`**（临时看不见） |
| `selfTimeScale` | 乘到模拟时间上，子级继承层级结果（慢动作/加速） |

#### 8.3.4 关键区分：Transform 继承 ≠ Simulation Space

这是全章最容易混的一对概念，用一句话记：

> **Transform 继承决定"以后新生成的粒子从哪来"；Simulation Space 决定"已经生成的粒子存在哪"。**

| Simulation Space | 行为 | 典型用途 |
|---|---|---|
| `LOCAL` | 已生成的粒子继续跟着发射器层级平移/旋转/缩放 | 挂在实体身上的光环、武器光效 |
| `WORLD` | 生成后就留在世界里；之后移动发射器只影响**新**粒子 | 走位留下的烟雾、拖尾残影 |
| `CUSTOM` | 相对另一个 FX 对象的 Transform 保存 | 多个发射器共享一个独立动画空间 |

世界空间下移动发射器**不会**拖动旧粒子 —— 这句话解释了 90% 的"为什么我的烟雾跟着我跑"。

#### 8.3.5 案例：一个 FX 里的两种空间

目标：角色身上有一圈跟着转的光环，同时脚下走过的地面留下逐渐消散的烟。

1. root 下建 Empty，命名 `aura_pivot`（以后 Java 侧就用这个名字控制整圈光环）。
2. `aura_pivot` 下建 Particle Emitter `aura_ring`：Shape 用 **Circle**（半径 0.9、thickness 0），
   Simulation Space 用 **LOCAL** —— 这样角色转身、跳跃时光环整体跟着走。
3. root 下再建 Particle Emitter `ground_smoke`：Shape 用 **Box**（薄薄一层），
   Simulation Space 用 **WORLD** —— 粒子生成后停在世界里，角色走开就会看到一串"脚印"。

这样两个发射器在同一个 `.fx` 里、同一套参数体系，唯一区别就是空间选择。
把这两个空间的区别刻进肌肉记忆，后面所有"位置不对"的问题都能自己回答一半。

### 8.4 粒子发射器与模块

这一节是 Photon2 的主体。**读法建议**：先看 §8.4.1 弄清"一个发射器由哪几块组成"，
然后按你需要什么查后面的小节 —— 不要试图一次记住所有参数。

#### 8.4.1 一个发射器由什么组成

```
ParticleEmitter
├─ 顶层参数（ParticleConfig）：时长、循环、初始值、上限…
├─ Emission / Shape：从哪生成、什么时候生成
├─ 模块（可开关，逐个叠加）：生命、速度、受力、物理…
└─ Renderer + Material：长什么样、怎么画
```

**顶层参数**（Inspector 直接显示的那一排）：

| 参数 | 含义 | 单位 / 默认行为 |
|---|---|---|
| `duration` | 单次循环长度 | **tick**（20 tick = 1 秒） |
| `looping` | 循环结束后是否重来 | — |
| `prewarm` | 第一帧可见之前**预先模拟**多少 tick | tick；让循环烟雾第一帧就像"已经跑了一会" |
| `startDelay` | 每次开始时延迟多久 | 函数（可以做随机延迟） |
| `startLifetime` | 新粒子的生命期 | tick |
| `startSpeed` | 沿 Shape 方向的初始速度 | 格/tick 量级 |
| `startSize` | 初始尺寸（X/Y/Z） | 格 |
| `startRotation` | 初始旋转（X/Y/Z） | 度 |
| `startColor` | 初始颜色 | 后续 Color 模块与材质会继续相乘 |
| `maxParticles` | 同时存在的粒子上限 | **性能闸门**，先设小再往上调 |
| `parallelUpdate` | 允许互相独立的粒子并行更新 | 省 CPU，但自定义逻辑里不能有共享可变状态 |

**两条生命周期规则**，不知道就会误判"特效没结束"：

1. 非循环发射器在 `duration` 之后**停止生成**，但已有粒子/拖尾还在时，**整个 FX 不会结束**；
2. 循环发射器永远不会自己结束 —— 谁创建它，谁负责在合适的时候销毁（§9.3）。

`prewarm` 是个好用的开关，但它会**增加启动那一刻的模拟成本**；频繁生成的效果别把值设太大。

#### 8.4.2 Emission 与 Shape：粒子从哪来、什么时候来

**Emission（什么时候生成）**有三种来源：

| 来源 | 行为 | 什么时候用 |
|---|---|---|
| Emission Rate | 按发射器时间**连续**生成 | 火焰、光环、持续的烟 |
| Distance Rate | 按发射器**移动距离**生成 | 脚印、移动轨迹（走得快就多） |
| Burst | 在循环内的指定时间一次性生成一批 | 爆炸、爆发、每段技能的起手 |

Burst 的参数是 `time / count / cycles / interval / probability`；发射器会**跟踪每个 Burst 的 cycle**，
所以循环时不会错误地重放旧状态。

**Shape（从哪生成、初始速度朝哪）**：Shape 内部还有自己的 position / rotation / scale，
所以想要不同的生成轴心时**不必再加一个 Empty**。

| Shape | 重要设置 | 常见用途 |
|---|---|---|
| Dot | 无 | 单一原点（起手闪光） |
| Box | 尺寸 + Emit From 模式 | 体积、平面、方盒表面 |
| Circle | radius / thickness / arc | 环形、径向喷射（光环、魔法阵） |
| Cone | angle / radius / thickness / arc | 火焰、喷射、方向扩散 |
| Cylinder | radius / thickness / arc | 柱体、圆形墙 |
| Sphere | radius / thickness / arc | 爆炸、Aura、球壳 |
| Mesh | Mesh Source 的顶点/三角面 | 从模型几何体上生成 |
| Function | 位置/方向表达式 | 程序化路径、数学体积 |

两个细节：

- **thickness = 0 只在外表面生成**（shell，轮廓清晰）；值越大越往中心填，
  想要云雾/爆炸的体积感就填满内部。
- **Arc**：圆形/锥形 Shape 可以只用一个角度区间，并选择怎么遍历（随机分布还是按顺序推进），
  配合 loop / ping-pong / spread 与 speed 决定发射怎么前进。
- **Function Shape** 用表达式算位置与方向，**必须保持确定性**，并且要防止除零、开负数 ——
  求出 NaN 的位置在渲染和碰撞里都不安全。

#### 8.4.3 生命周期与速度类模块

这类模块的输入都是**归一化的粒子年龄（age，0→1）**；`by Speed` 系列则是"先把速度按配置范围重映射到 0→1"。
它们的作用方式都是**与 Start 值相乘或相加**，不会直接覆盖粒子状态。

| 模块 | 输入 | 影响 |
|---|---|---|
| Color over Lifetime | age | 用渐变或颜色函数乘到颜色/alpha |
| Size over Lifetime | age | 缩放 X/Y/Z 尺寸 |
| Rotation over Lifetime | age | 追加 roll / pitch / yaw |
| Velocity over Lifetime | age | 追加线性/轨道/径向速度与速度修正 |
| Force over Lifetime | age | 把加速度积分成速度 |
| Color by Speed | 速度 | 按速度乘颜色 |
| Size by Speed | 速度 | 按速度缩放尺寸 |
| Rotation by Speed | 速度 | 按速度追加旋转 |
| Lifetime by Emitter Speed | 发射器速度 | 调整新粒子分到的生命期 |

三条实用经验：

1. **`by Speed` 的范围要接近粒子的真实速度**。如果配置范围远高于实际速度，函数会永远停在第一段 ——
   表现就是"我明明设了渐变，颜色却一直不变"。
2. **模块的 enable 也能被 Timeline / Java 改写**（2.2.0 起），所以"临时关掉一层"不需要改配置。
3. **生效顺序是相乘/累加链**：`Start Color × Color over Lifetime × Color by Speed` = 进材质前的实际颜色；
   尺寸同理；旋转与运动是各自累加。

**调试这类模块的三步法**（官方推荐，实测有效）：把 Start 值换成简单常量 → **只开一个模块** →
用一条清晰的 0→1 线性曲线确认输入范围 → 再加第二个模块。

#### 8.4.4 运动、受力与力场

| 模块 | 干什么 | 注意 |
|---|---|---|
| Velocity over Lifetime | 追加 linear / orbital / offset / radial / speed 修正 | 有线性的 `ValueSpace`，决定向量按哪个基解释 |
| Force over Lifetime | **改变速度**（不是直接改位移） | 适合重力、风、可控弯曲；变化中的力曲线会沿生命期积分 |
| Inherit Velocity | 把发射器运动传给新粒子或存活粒子 | 和 Local Space 不是一回事：World 空间粒子可以继承初速，但不继续跟随 |
| Force Field | 独立的 FX 对象（`ForceFieldObject`），带自己的 Transform 和配置 | 支持方向力、围绕它的吸引/重力、drag、涡旋（vortex） |

用 Force Field 的方式：在 Particle Emitter 上开启 **External Forces** 并设 multiplier；
`influence filter/list` 可以只让指定的 Field 生效或排除它 —— 所以同一个 FX 里可以并存多套力场。

**计算顺序**（官方明确写了）：Start Speed 与 Inherit Velocity 建立初始运动 →
每 tick 再由 Velocity / Force / Physics / External Field / Noise 各自叠加。
**同一个效果不要让多个模块重复做同一件事**，否则参数之间会互相打架、很难调。

#### 8.4.5 物理、噪声、光照、UV

这四个模块作用在粒子的不同阶段，混在一起记容易乱：

| 模块 | 阶段 | 要点 |
|---|---|---|
| Physics | 与世界交互 | 要碰 Minecraft 的碰撞形状才开。核心是 `gravity`、碰撞响应、`bounce`（保留法线方向速度）、`friction`（削减切向速度） |
| Noise | 扰动模拟状态 | 可以扰动位置/旋转/尺寸；`frequency` 是场变化速度，Quality 用算力换平滑；低频做烟，高频做火花/电流 |
| Lighting | 改打包光照 | `Light over Lifetime` 给 Sky/Block Light 函数；固定亮度适合自发光外观（真正的 HDR 发光要写在材质/着色器里） |
| UV Animation | 贴图选帧 | 把贴图切成 tiles（列×行），按 `frameOverTime` 选帧，配 `startFrame`、`cycle` |

三个坑：

1. **碰撞是每粒子 CPU 成本**，还会访问世界数据 —— 只在玩家**真的看得见**接触时开。
2. **速度过大可能在两个 tick 之间穿过薄几何体**（隧穿）；要么降速，要么改效果设计。
3. `Light Value` 和材质里的 Emission 解决不同问题：前者影响 Minecraft 的光照着色，
   后者才是给 Bloom 用的发光。既调不出来就别硬调，先想清楚要哪一个。

#### 8.4.6 拖尾与子发射器：把多个发射器串起来

**Particle Trail**：给一定比例的粒子挂上拖尾。

| 设置 | 用途 |
|---|---|
| `ratio` | 多少比例的粒子获得拖尾 |
| `lifetime` | 拖尾自己的寿命函数 |
| `dieWithParticles` | 跟所属粒子一起消失，还是自己慢慢消散 |
| `sizeAffectsWidth` / `sizeAffectsLifetime` | 粒子尺寸乘到拖尾宽度/寿命 |
| `inheritParticleColor` / `colorOverLifetime` | 拖尾颜色 |
| `trailType` | 拖尾的实现/几何体类型 |

> 拖尾有自己的材质与渲染路径，所以**同一个发射器可能把普通粒子和拖尾送进不同的渲染 pass**。
> 这就是"为什么我改了材质，粒子变了但拖尾没变"。

**Sub Emitter（子发射器）**：用事件把发射器串成多阶段效果。

| 事件 | 触发时机 |
|---|---|
| Birth | 父粒子创建时 |
| Death | 父粒子结束时 |
| Collision | 父粒子报告碰撞时 |
| Tick | 按配置的间隔重复 |

每个条目可以配目标发射器、概率、Tick 间隔，以及颜色/尺寸/旋转/寿命/时长的继承开关。
引用的是**同一个 FX Runtime 里的另一个发射器**，靠名字解析并缓存。

> **绝对不要写出环**：发射器生成自己、或两个互相生成，数量会指数爆炸；
> `probability` 和 `interval` **不能**把这种循环变安全。要链式（A→B→C），并给出保守的 `maxParticles`。

**三种"直接画几何体"的发射器**（它们不生成一团独立粒子，而是画连接几何）：

| 发射器 | 几何来源 | 适合 |
|---|---|---|
| Trail | 把移动的发射器采样成有序 section | 武器挥砍、移动拖尾、飘带 |
| Beam | 连接端点或射线结果 | 激光、连线、直线能量束 |
| AraTrail | **会模拟**带物理的移动线段 | 鞭子、电流式运动、平滑尾巴 |

选择口诀：几何要精确跟随移动的 Transform → Trail；端点点位比运动历史重要 → Beam；
拖尾自身需要模拟运动 → AraTrail；大量独立粒子各要一小截拖尾 → Particle Trails。

三种发射器都有**强类型 Runtime 层**（`TrailRuntime` / `BeamRuntime` / `AraTrailRuntime`），
所以 Timeline 能动画它们的字段，Java 也能写同样的槽（§9.6）。

#### 8.4.7 案例：三段式起手特效（Burst → 扩散 → 残留）

目标：技能起手瞬间一圈火花向外扩散，随后留下缓缓上升的余烬。

1. **Emitter 1「spark_burst」**（爆发环）
   - Duration 5、Looping 关；Shape 用 **Circle**（radius 0.2、**thickness 0** 只在外沿）；
   - Emission 用 **Burst**：`time 0 / count 60 / cycles 1`；
   - Start Lifetime 12、Start Speed 0.35、Start Size 0.12；
   - `Size over Lifetime` 用一条从 1 → 0.2 的曲线（粒子越小越像火花）；
   - `Color over Lifetime` 用 `#FFFFFF → #FFC64B → alpha 0` 的渐变。

2. **Emitter 2「ember_rise」**（余烬上浮）
   - Duration 60、Looping 开；Shape 用 **Box**（一层薄平面）；
   - Emission Rate 6；Start Lifetime 45、Start Speed 0.02；
   - `Force over Lifetime` 给一个**向上**的力（0, 0.004, 0）——力改的是速度，摸起来像"越飘越快"；
   - Simulation Space 选 **WORLD**，这样起手结束后余烬留在原地，角色走开不会拖着它们。

3. **把两段串起来**：在 `spark_burst` 上加 **Sub Emitter** 条目，
   Event 用 **Death**，目标指到 `ember_rise`，`probability 0.35`（不是每个火花都留余烬，看起来更自然）。

**调参顺序**（照这个顺序调，比乱试快得多）：先调 Shape 与 Burst 的**数量**看分布 →
再调 Start Lifetime / Speed 看范围 → 最后才调颜色与渐变。
颜色放最后，是因为它最容易被误当成"位置不对"的原因。

### 8.5 数值函数（NumberFunction）：让参数动起来

#### 8.5.1 一个概念：消费者给 time，函数给值

Photon 里大量设置不是"写死一个数"，而是一个**函数**。你选函数类型，Photon 在运行时把
"时间"喂进去、取出值。**同一个 Curve 在不同字段里含义可能不同**，因为"时间"的定义不同：

| 字段 | 喂进去的 time 是什么 |
|---|---|
| `over Lifetime` 系列模块 | 归一化的粒子年龄（0 → 1） |
| `by Speed` 系列模块 | 先把当前速度按配置范围重映射到 0 → 1 |
| Emission / Start 值 | 发射器/粒子的创建时间 |
| Trail 相关字段 | 归一化长度、整条拖尾的时间、或单段的时间 |
| Additional GPU Data | 可选的时间来源 |

**所以在模块之间复制曲线之前，先确认字段说明** —— 同一张曲线图放在"生命期"和"速度"里，
形状一样但含义完全不同。

#### 8.5.2 标量函数与颜色函数

| 标量类型 | 结果 |
|---|---|
| Constant | 恒定值 |
| Random Constant | 在范围内按粒子/发射器随机取值 |
| Curve | 按 time 求值的可编辑曲线 |
| Random Curve | 用一个稳定随机因子在两条曲线之间取 |
| NumberFunction3 | 三条函数组合成 X/Y/Z（尺寸、旋转、力、速度都用它） |

| 颜色类型 | 结果 |
|---|---|
| Color | 一个 RGBA 颜色 |
| Random Color | 在若干颜色之间随机 |
| Gradient | 按 time 在颜色 stop 与 alpha stop 之间插值 |
| Random Gradient | 用稳定随机值在两条渐变之间混合 |
| HDR 变体 | 保留 0–1 显示范围以上的值，用于 Bloom/发光 |

> 只有支持 HDR 颜色的消费端才能接 HDR 函数 —— 编辑器会**直接拒绝不兼容的拖放**，
> 而不是悄悄把数据截断。看到拖不进去就是"这个字段吃不了 HDR"，不是 bug。

#### 8.5.3 资源值 vs 内联值

- 曲线、渐变、颜色**既能内联写在配置里，也能保存成资源**；
- 多个发射器/材质要共用同一个值 → 存成资源，拖进字段会保持**引用**；
- 复制内联值会产生**独立数据**（改一个不影响另一个）。

这是"我改了曲线，为什么只有一半的粒子变了"的答案：那半用的是复制出来的内联副本。

#### 8.5.4 Timeline 与函数的关系

Timeline 的动画属性可以存关键帧、曲线片段、渐变片段或表达式片段；更新时它把**采样后的具体值**
写进目标的 RuntimeValue，**不会修改你的曲线资源**。所以"动画跑完之后参数还是我设的值"。

#### 8.5.5 案例：伤害数字的"弹出感"

想要的效果：数字出现时先放大、再回落到正常大小，同时从亮黄渐变成白。

| 字段 | 函数 | 曲线 |
|---|---|---|
| `Size over Lifetime` | Curve | (0, 0.6) → (0.15, 1.35) → (0.4, 1.0) → (1, 0.9) |
| `Color over Lifetime` | Gradient | (0) `#FFF2A8` → (0.4) `#FFFFFF` → (1) 白色但 alpha 0.85 |

两条曲线都存成**资源**（因为暴击/普通伤害可能共用同一套手感），
Java 侧只改"用哪套资源、数字内容、位置"，不改曲线本身。

### 8.6 渲染、材质与网格：粒子到底长什么样

#### 8.6.1 三层：Simulation / Renderer / Material

一句话分工：**Simulation 产生粒子状态，Renderer 决定几何与绘制顺序，Material 决定着色器、贴图、深度与混合。**

| Renderer 设置 | 用途 |
|---|---|
| `materials` | 这个几何体用哪几个材质槽 |
| `layer` | Opaque 还是 Translucent 管线 |
| `cull box` | 给这个发射器单独设视锥裁剪盒（不设就用默认） |
| `order in layer` | Photon 各个 pass 之间的**稳定顺序** |
| `vertex sorting mode` | 透明几何的顶点排序方式（支持的渲染器才有） |
| `composite mode` | 选择延迟 FX 的合成时机 |
| `custom mask` | 给后处理写命名的 Mask/Depth 信息 |

#### 8.6.2 可用的材质类型

| 材质 | 说明 |
|---|---|
| Texture Material | 贴图 + discard 阈值 + HDR 倍数/模式 + Pixel Art 开关 |
| Sprite Material | 使用已注册的 Minecraft 粒子 Sprite |
| Shader Graph Material | 把图资源编译成粒子着色器变体（§8.9） |
| Custom Shader Material | 加载 Core Shader，并暴露 Curve/Gradient 采样器 |
| Block Atlas | 绑定 Minecraft 方块图集 |

材质状态与渲染状态是**分开的**，所以同一个材质可以复用到不同几何体上。

#### 8.6.3 Tile 与 Model 两种渲染

- **Tile**：面向相机（或指定方向）的四边形 —— 大多数粒子用这个，便宜。
- **Model**：每个粒子渲染一个网格，支持 Wireframe/Shaded、多材质；
  Model Source 提供时还能用方块 UV。

**Mesh Source**（Shape 与 Model 共用同一套来源）：内置图元（plane/quad/cube/sphere/cylinder/capsule）、
`.obj`、Minecraft JSON 模型、以及编辑器管理的可复用 `.mesh.nbt`。
同一个资源可以**同时**驱动"从哪里生成"和"长什么样"，不需要维护两份。

#### 8.6.4 合批与实例化：为什么会突然掉帧

实例化渲染会把每个粒子的记录上传，用**一次 draw call** 画一大批；有效渲染 pass 相同的对象会被合进同一批。

所以下面这些操作会**打断合批**（表现就是特效一多就掉帧）：

- 每个发射器用不同的材质/渲染设置 → 每次覆盖都会生成一个兼容的 override pass；
- 频繁改渲染覆盖（`layer`、`orderInLayer`、混合等）→ 每个"有效值不同"的组合各自成批；
- 大量透明几何 + 顶点排序。

好消息是：**清除全部渲染覆盖后会恢复共享的快路径**。所以调节性能的第一招往往是
"别在运行时逐帧改渲染参数"，把这些参数放回编辑器。

#### 8.6.5 透明与深度

| 设置 | 作用 | 用错的后果 |
|---|---|---|
| Depth Test | 让粒子不被它背后的几何穿出 | 关掉会看到"隔着墙的粒子" |
| Depth Mask | 把粒子的深度写进深度缓冲 | 乱写会让后面本该出现的透明效果消失 |
| Blend Mode | 混合方式，必须与着色器的颜色约定匹配 | HDR/Bloom 路径要用支持预乘的合成方式 |

#### 8.6.6 案例：让特效正确出现在角色身后 / 身前

症状：技能特效不管在角色前面还是后面，都糊在角色脸上（或反过来被角色整块遮住）。

排查与修法：

1. **先确认层**：`layer` 选错（比如该 translucent 的用了 opaque）会让半透明粒子直接盖住角色；
2. **再确认深度测试**：想"被角色挡住"就必须开 Depth Test；想"永远在最上面"（准星、屏幕提示）
   才关掉它；
3. **最后确认顺序**：同一层里有多个发光元素时用 `order in layer` 固定顺序，
   否则它们会随帧闪动；
4. 多发光元素叠加时，考虑用 **custom mask + 后处理**做统一发光，而不是叠十几个半透明面片
   （后者既不便宜也不好看）。

### 8.7 Timeline：让特效按时间演出

#### 8.7.1 这一节解决什么问题

§8.4–§8.6 讲的是「一个发射器怎么发射、粒子长什么样」。但真实的技能特效往往不是「一个发射器一直放着」，
而是**分阶段**的：前摇先亮一圈法阵、蓄力时法阵收缩、爆发瞬间炸开、然后淡出。

如果你用 Java 每 tick 判断「现在到第几个阶段了」，再手动去改对象可见性、位置、速度，代码会迅速变成一团
`if (tick > 20 && tick < 60)`；而特效作者（美术）根本改不动这团代码。

Timeline（时间轴）就是 Photon 给这件事的标准答案：**把「什么时候发生什么」从代码里搬到编辑器里**，
排成一条可视化的时间线，Java 侧只负责「什么时候开始播」和「播完告诉我一声」。

一句话对照：

| 你想要的 | 该用什么 |
|---|---|
| 一个常驻光环、一条常驻拖尾 | 直接发射器 + 循环，不需要 Timeline |
| 「起手 → 蓄力 → 爆发 → 余韵」这种分段演出 | Timeline |
| 特效要和游戏逻辑对暗号（比如"蓄力完成时弹伤害数字"） | Timeline 的 Signal（信号） |
| 整屏的闪光、扭曲、残影 | Timeline 的 Post Process 轨道（详见 §8.8） |

**空 Timeline 不花钱**：源码里 `TimelinePlayer.isEmpty()` 为真时 `isFinished()` 直接返回 `true`，
不会有任何每帧求值。所以给一个简单特效留一条空 Timeline 是无害的，等需要时再排。

> 术语对照：Timeline（时间轴）、Track（轨道）、Clip（片段）、Track Group（轨道组）、
> Signal（信号）、Marker（标记）、Playhead（播放头）。

#### 8.7.2 心智模型：一份数据 + 一个播放器

Timeline 只有两个角色，分清楚就再也不会绕：

| 角色 | 是什么 | 真实类 | 生命周期 |
|---|---|---|---|
| `Timeline` | **数据**：一串 `Track`，每个 Track 里放 Clip / Key / Signal | `client/fx/timeline/Timeline.java` | 跟着 `.fx` 资产，只读 |
| `TimelinePlayer` | **运行时**：一条属于这次播放的主时钟 | `client/fx/timeline/TimelinePlayer.java` | 每次播放一个新的，可读写 |

`TimelinePlayer` 挂在 FX 的 Root 空对象上，由 Root 每 tick / 每帧驱动。它对外暴露的方法就这些
（都在 `TimelinePlayer.java`，括号里是行号）：

| 方法 | 作用 |
|---|---|
| `begin(IEffectExecutor)` | 开始播放，重置时钟到 0 并立即求值一次 `t=0`（`:85`） |
| `tick()` / `tick(float rate)` | 推进一个游戏刻（`:100` / `:112`） |
| `frame(float partialTicks)` | 每渲染帧的插值通道：重采样动画 + 提交后处理请求（`:147`） |
| `isFinished()` | 没有未来内容了就为真（`:126`） |
| `getDuration()` | 本次播放的内容总长度（tick）（`:131`） |
| `stop()` | 彻底结束：静音所有声音，并让 `isFinished()` 变真（`:136`） |
| `stopAllAudio()` | 只停声音（`:334`） |

**一个必须记住的时钟细节**：`tick(rate)` 的实现是**先求值、再推进**——

```java
public void tick(float rate) {
    evaluate(localTime);                  // 先按"当前"时间求值
    localTime += Math.max(0f, rate);      // 再往前走
}
```

这带来两个后果，都是好消息：

1. 摆在 `t = 0` 的内容在**第一个刻到来之前**就已经生效了，不会出现"前 50ms 什么都没发生"；
2. 恰好摆在 `t = duration` 的内容（例如结尾那一下 Signal）会在 `isFinished()` 变成 `true` **之前**被求值，
   **不会被吞掉**。

`isFinished()` 的判据是三者之一：Timeline 为空、被 `stop()`、或者 `localTime > duration`。
注意它是"`>`"而不是"`>=`"，配合上面那条"先求值再推进"，正好让结尾那一帧来得及生效。

#### 8.7.3 两个速度：模拟按 tick，画面按帧

Photon 里「特效跑多快」有两层，别混：

| 层 | 谁在推 | 单位 | 结果 |
|---|---|---|---|
| 模拟（粒子状态、Timeline 主时钟） | Root 对象每 tick 调 `tick(rate)` | 游戏刻（1 刻 = 1/20 秒） | 确定性；同一份资产同样的 seed 每次结果一致 |
| 画面（Transform 动画采样、后处理请求） | 每渲染帧调 `frame(partialTicks)` | 渲染帧（几十~几百 fps） | 平滑；`partialTicks` 是"当前刻走到哪儿了"的小数 |

这就是为什么 Transform 动画是平滑的、而粒子物理是离散的：`frame()` 里会把动画在
`lastEvalTime + partialTicks` 这个**小数时间**上重新采样一遍（`TimelinePlayer.java:147-155`）。

再往上一层还有 `FXRuntime#setRate(rate)`：它是**整份播放的倍率**，`tick(rate)` 里的 `rate` 就来自它
（`0` 冻结、`2` 表示一个游戏刻推进两个 Timeline 刻）。它和下面的 SpeedTrack 是两件事：

- `setRate` 是**调用方**（Java 代码）说的"整体快慢"；
- SpeedTrack 是**作者**（编辑器里）说的"这一段让某个对象慢下来"。

源码注释明确写了冲突时的优先级：轨道覆盖到 Root 时，**作者的显式意图赢过调用方**。
所以如果你的代码 `setRate(0.5)` 却发现没变慢，先去看看 Timeline 里有没有 SpeedTrack 绑在 Root 上。

#### 8.7.4 Track 与 Clip 的结构

一个 Track 就是"一条泳道"，负责一类事情；Clip 是泳道上的一个方块，表示"从第 X 刻到第 Y 刻做这件事"。

| 字段 | 含义 |
|---|---|
| `start` | 这个 Clip 从第几刻开始（主时钟 tick） |
| `duration` | 长度，单位 tick |
| local time | `masterTime - start`，用来在 Clip 内部采样曲线/包络 |
| `seed` | Control Clip 重启对象时使用的**确定种子** |
| random seed | 重启时改为从 Executor 的随机源取一个新种子 |

**一条硬规则：相邻 Clip 要首尾相接，不要重叠。** 那些"同一时刻只能解析一个 Clip"的轨道会直接拒绝重叠，
拖拽、缩放、粘贴走的是同一套校验；被拒绝的编辑不会把原来的位置改坏。这不是为了刁难你——
重叠意味着"这一刻到底听谁的"没有答案。

Clip 的边界包含哪一端由各轨道自己的查找规则决定，这也是为什么**不要靠重叠来补缝**，
而是让 `上一个.start + 上一个.duration == 下一个.start`。

**内置轨道一览**（类名在 `client/fx/timeline/` 下，语义对照官方文档站与源码）：

| 轨道 | 真实类 | 控制什么 | 什么时候用 |
|---|---|---|---|
| Activator | `ActivatorTrack` | 目标对象在 Clip 范围内是否 Active | 让某个发射器"只在第 20~60 刻存在" |
| Control | `ControlTrack` | 在 Clip 边界**重启并播放**某个对象/子树 | 同一泳道依次播多个一次性特效、或需要固定种子重放 |
| Animation | `AnimationTrack` | Transform 或已注册的 RuntimeValue 槽 | 让法阵变大、让亮度爬升 |
| Speed | `SpeedTrack` | 目标对象的 `selfTimeScale` | 蓄力段的"慢动作" |
| Signal | `SignalTrack` | 带 `CompoundTag` 数据的命名事件 | 和 Java 逻辑对暗号 |
| Audio | `AudioTrack` | 声音、音量/音调包络、可选空间位置 | 起手音、爆发音 |
| Post Process | `PostProcessTrack` | 每帧的加权后处理请求 | 屏幕闪光、色偏（详见 §8.8） |
| Group | `TrackGroup` | 只负责组织与 Mute，**不产生对象也不产生 Transform** | 轨道太多时收纳 |

> 有个常见误解：以为 Group 可以当"父级变换"用。不能——它只是文件夹。要层级变换请用 Empty 对象。

**绑定目标（Target）**：把 Hierarchy 里的对象拖到 Target 槽即可。Animation、Activator、Speed、Audio
是**在 Track 级**绑定（整条轨道只管一个对象）；Control 可以**按 Clip**绑定，所以同一条泳道能依次
播放好几个不同对象——这正是它存在的理由。

序列化上，轨道类型走 `photon:timeline_track` 注册表；Clip 保存时间与各自类型专用字段。
如果运行时对象被删掉，UUID 查不到对象时是**安全失效**（不会崩），但应该在编辑器里重新绑定，
否则你会得到"轨道看起来在跑、却什么都没发生"。

#### 8.7.5 三种"控制类"轨道：Activator / Control / Speed

这三个都改"对象的状态"，但改的是三件不同的事，而且它们**共用同一时钟、按确定顺序解析**。

**Activator —— 管"在不在"。** 当前时间被某个 Activator Clip 覆盖时对象是 Active；走出 Clip 就 Inactive，
**既不 tick 也不渲染**。这是最便宜的开关键。

**Control —— 管"什么时候从头开始"。** 进入或切换到某个 Control Clip 时，它会：

1. Reset 目标对象；
2. **递归** Reset 它的所有子对象；
3. 套用 Clip Seed（或者去 Executor 的随机源取一个新 Seed）；
4. 在 Clip 范围内把对象启用并渲染出来。

所以 Control 适合两种场景：同一泳道里"放完一个再放一个"的一次性发射器；以及"用同一个固定种子
把同一棵子树一模一样地重放一遍"。

**Speed —— 管"跑多快"。** SpeedTrack 采样一个标量写进目标的 `selfTimeScale`，子对象继承父级的层级时间缩放：

| 值 | 效果 |
|---|---|
| `0` | 对象还在，但模拟冻结 |
| `0 < s < 1` | 慢动作 |
| `1` | 正常（每游戏刻一个模拟步） |
| `> 1` | 每个游戏刻跑多个**有上限**的模拟子步 |

最后一行要注意：运行时会给每 tick 的子步数量设上限，因此**极大的 Speed 并不能在一个刻里模拟任意长的时间**。
想做"一秒跳过十秒"的效果，请回去调 Clip 的 Start/Duration，而不是把 Speed 拉到 100。
（本机源码里的上限是 `FXObject.MAX_SUBSTEPS = 16`，见 `client/gameobject/FXObject.java:53`。）

**状态恢复这条容易被忽略但很重要**：SpeedTrack 被删除、Mute 或改绑之后，原目标的缩放会**恢复成 1**；
Activator/Control 不再控制某个对象时，它的 Active 与 Visible 也会恢复。这是好事（改错轨道不会把对象永久卡死），
但也意味着你不能指望"用 Mute 来持久地压住某个状态"。

还有一条官方明确提醒的坑：Activator 和 Control 同时存在时，解析器 `TimelineState.resolve` 会**同时**考虑
两者是否存在、当前是否 Active，**不存在简单的"后者覆盖前者"**。两者的组合请以编辑器预览为准，
不要靠脑补推导。

> 想让"整棵子效果一起重启/一起变速"？把 Control 或 Speed 绑到一个 Empty 父对象上，而不是逐个绑子对象。

#### 8.7.6 Animation Track 与录制

Animation Track 采样的是**强类型属性**，然后写回 FX 对象。它有两类目标：

1. **Transform**：`position` / `rotation` / `scale`；
2. **Config Property**：发射器 Runtime 上**已注册的** `RuntimeValue` 槽——也就是 §8.5 里那些模块参数
   （发射率、速度、颜色……），以及每个配置动态生成的 Additional GPU Data 通道。

可动画的类型包括：标量 `float` / `int` / `boolean`、`NumberFunction` 与 `NumberFunction3`、
颜色与 HDR 颜色、Emitter 类型注册的 Renderer / Material Override 字段。

**关键限制：属性菜单来自目标对象的 `FXObjectType`，不是反射。** 代码里写得很直白——不能通过反射动画
任意字段，字段必须有受支持的 `ConfigValueType` 且注册过 `RuntimeBinding`。所以"为什么这个参数不能打关键帧"
的答案通常是"它的类型没被登记成可动画属性"，而不是你操作错了。

编辑器的 Lane 里可以放四种东西：

| 条目 | 用途 |
|---|---|
| Keyframe | 在指定时间采一个值，Key 之间自动插值（曲线 Key 带切线手柄） |
| Curve Clip | 在一段范围内用一个 Curve 表达式 |
| Gradient Clip | 在一段范围内编辑颜色/透明度的 Stop（颜色泳道专用） |
| Expression Clip | 用 Clip 的 local time 算表达式 |

**录制（Record Mode）**的操作顺序：

1. 把 Animation Track 绑到目标对象；
2. 添加并选中你要录的属性；
3. 打开 Record；
4. 播放，并在需要的时间点直接在 Inspector 或 Gizmo 里改目标；
5. 关掉 Record，**从头播一遍**验证插值。

录制期间运行时会**冻结该目标的逐帧动画重放**（`setRecording(true)` 之后 `frame()` 里跳过
`applyAnimations`，见 `TimelinePlayer.java:172-175` 与 `:147-155`），否则你刚做的修改会被每帧重新采样覆盖掉。
需要时可以用 Capture 读取 Authored/Live 值，绕过现有的运行时覆盖。

#### 8.7.7 Signal：让特效和游戏逻辑对暗号

Signal 是 Timeline 里唯一"对外说话"的东西，语义只有一句话：**在第 t 刻，用通道 c 喊一个名字 n，
附带一包数据 d。**

| 概念 | 说明 |
|---|---|
| `Signal` | `(time, name, data)` 三元组，`data` 是任意 `CompoundTag`；同一轨道里允许重名 |
| Channel（通道） | **就是该 Signal 轨道自己的显示名**（`signalTrack.displayName()`） |
| 发送窗口 | `(lastSignalTick, time]`——左开右闭，所以**不重发、也不补发** |
| 触发时机 | 只在**正向、实时**播放时发；编辑器拖 Playhead（Scrub）会关掉 dispatch |

「左开右闭 + 单调推进的时间戳」这套设计的目的是**防重复触发**：`begin()` 会重置窗口，普通求值反复走到同一时刻
也不会二次触发，回放式跳转更不会把中间所有信号补发一遍。

接收端有两条通道，同一条 Signal 会同时走这两条（`TimelinePlayer.dispatchSignals`，`:371-390`）：

1. **执行器自己的钩子**：`IEffectExecutor#onTimelineSignal(channel, name, data, time)`
   —— 只有"当前这次播放"的执行器收得到，适合"播到这个特效的这一刻，让这个特效做点什么"；
2. **全局监听**：`PhotonSignals.register(Listener)`（`client/fx/timeline/PhotonSignals.java`）
   —— 谁注册谁都能收到，适合"特效播到这一刻，让整个游戏做点什么"。

全局监听器的签名是：

```java
public interface Listener {
    void onSignal(@Nullable IEffectExecutor effect, String channel, String name,
                  CompoundTag data, double time);
}
```

两个工程上的细节：`effect` 可能为 `null`（编辑器路径），**必须判空**；某个监听器抛异常会被捕获并记日志
（`"Timeline signal listener threw"`），**不会**影响其它监听器——所以别指望"抛异常来中断"。

Java 侧的典型接法：

```java
// 全局监听：一次注册，收所有特效的信号
PhotonSignals.Listener listener = (effect, channel, name, data, time) -> {
    if (!"skill".equals(channel)) {
        return;                                  // 通道先过滤，减少无效判断
    }
    switch (name) {
        case "charge_full" -> playChargeFullSound();
        case "burst"       -> spawnDamageNumbers(data.getFloat("power").orElse(1f));
        default -> { }
    }
};
PhotonSignals.register(listener);
// 卸载时一定要 unregister，否则监听器会一直挂着
```

```java
// 或者：只关心自己这次播放的执行器
@Override
public void onTimelineSignal(String channel, String name, CompoundTag data, double time) {
    if ("skill".equals(channel) && "burst".equals(name)) {
        // 只改这次播放的运行数据，不碰资产
    }
}
```

#### 8.7.8 Audio 与 Group

**Audio Clip** 选一个 Sound Event，并配置 Category、Attenuation（是否按距离衰减）、Duration，
以及音量/音调函数（曲线形式的 Volume/Pitch 自 2.2.x 起提供）。它的生命周期规则：

- 进入 Clip 时启动 Sound Instance；
- 离开或切到别的 Clip 时请求停止上一个 Instance；
- **Clip 比声音长就循环，Clip 比声音短就在 Clip 末尾截断**——也就是说声音不会拖过 Clip 边界；
- 打开 Attenuation 且绑定了 Target 时，声源位置跟随该 FX 对象；
- 编辑器预览可能会强制成"非定位声音"，保证你在预览相机旁能听到。

**Track Group** 只做两件事：把子轨道收起来、以及提供**层级 Mute**（关掉组 = 关掉组里全部轨道）。
它不会创建 FX 对象、也不会产生 Transform。

#### 8.7.9 编辑器里怎么播放，和游戏内差在哪

Timeline 面板由 Track 树、时间标尺、Clip 泳道和属性编辑器组成；选中轨道或属性后，下方的属性区会跟着变。

| 控件 | 行为 |
|---|---|
| Play / Pause | 推进或冻结主时钟 |
| Stop / Restart | 重置运行时对象，并从时间零重新计算 |
| Loop | 在编辑器里反复播预览范围 |
| Scrub | 按指针所在时间**直接计算**效果，不做正常的前向播放 |

两个必须知道的差异：

1. **Scrub 会关掉 Signal 和 Audio 的发派**（`setSignalDispatch(false)` / `setAudioDispatch(false)`）。
   否则你拖一下播放头就会把中间所有信号连发一遍、声音叠成一片。游戏内的正向播放才会打开这两者。
2. **远距离 Scrub 会触发 Fast Seek**。因为粒子状态不是"某个时间戳的纯函数"（它依赖一路模拟的历史），
   要跳到很远的时刻只能从头快放一遍。编辑器会把连续的 Seek 请求合并，用 Fast Replay 跑，
   不会让指针经过的每个像素都排一次完整模拟。

编辑器用的是**独立的** Particle Manager 与 Post Effect Stack；游戏内用的是执行器真实的 Level、
实体/方块锚点、信号钩子和**全局**后处理栈。所以"编辑器里好好的、进游戏就变了"通常不是玄学，
而是这两套上下文本来就不一样（例如你的 Java 侧 `postEffectSink()` 换成了别的栈）。

**推荐的检查顺序**：先用 Scrub 检查时间布局（谁在第几刻出现、有没有重叠），再从零开始正向播放一遍，
专门验证 Control 的重启、随机种子、Signal、Audio 和后处理淡出这五项——它们**只在正向播放里成立**。

#### 8.7.10 案例：三段式「起手 → 蓄力 → 爆发」

**目标**：做一个 100 刻（5 秒）的技能演出——地面法阵亮起并扩张、蓄力时法阵收缩变亮并放出信号、
爆发瞬间炸开一簇粒子并让屏幕闪一下，最后收尾淡出。

**先想清楚对象树**（Hierarchy）：

```text
Root (Empty)
├─ array    ParticleEmitter   地面法阵，循环发射，常驻
├─ charge   Empty             承载"蓄力"这棵子树，方便整体变速
│   └─ swirl ParticleEmitter  围绕法阵旋转的光点
└─ burst    ParticleEmitter   一次性爆发，不循环
```

**再排轨道**（时长全部以 tick 计，`duration = 100`）：

| 轨道 | 类型 | 目标 | 片段 | 起止（tick） | 说明 |
|---|---|---|---|---|---|
| 起手 | Activator | `array` | 1 个 | 0 – 100 | 法阵全程在 |
| 起手 | Animation | `array` | Scale 曲线 | 0 – 20 | 0.0 → 1.0，缓出，避免"啪"地弹出来 |
| 起手 | Animation | `array` | Color 渐变 | 0 – 20 | 透明度 0 → 0.85 |
| 蓄力 | Speed | `charge` | 1 个 | 20 – 60 | 1.0 → 0.35，慢动作 |
| 蓄力 | Animation | `swirl` | Emission Rate | 20 – 60 | 8 → 30 |
| 蓄力 | Animation | `array` | Scale 曲线 | 20 – 60 | 1.0 → 0.72，配一条轻微"呼吸"的正弦曲线 |
| 暗号 | Signal | — | 2 个 | t=20 / t=60 | `charge_start` / `charge_full` |
| 爆发 | Control | `burst` | 1 个 | 60 – 100 | 固定 seed `20260928`，保证每次炸开形状一致 |
| 爆发 | Activator | `array` | 1 个 | 60 – 72 | 爆发瞬间把法阵压掉 |
| 爆发 | Post Process | — | 1 个 | 60 – 78 | 权重 0 → 1 → 0 的闪光，参数 `intensity` 由 1.4 降到 0.3 |
| 爆发 | Audio | `burst` | 1 个 | 60 – 78 | boom 音效，Attenuation 开，位置跟随 `burst` |
| 收尾 | Animation | `swirl` | Emission Rate | 60 – 100 | 30 → 0，自然熄掉 |

**为什么这么排**（这几条是这个案例真正想教的东西）：

1. **用 Speed 而不是改参数做慢动作。** 如果靠"把 emission rate 调小 + 把速度调小"来表达蓄力，
   你就要同时改五六个参数，而且每个发射器都得改一遍。绑到 `charge` 这个 Empty 父对象上改速度，
   子树里所有对象**一起**慢下来，改一处就够。
2. **爆发用 Control + 固定 Seed。** `burst` 是一个一次性发射器，用 Control 在 t=60 重启它，
   并套用固定种子，这样每次释放技能的碎片形状都一样——对技能特效来说，"每次都一样"通常是优点
   （玩家能记住这个技能的"手感"）。想每次不同就把种子换成 random seed。
3. **法阵的关闭用第二个 Activator，而不是直接调透明度。** Activator 是"在不在"的开关键，
   比把它缩到 0 或把 alpha 压到 0 更干净：不发粒子、不 tick，也不会有"看不见但还在算"的浪费。
4. **信号放在"状态真正切换"的那一帧。** `charge_start` 摆在 t=20（Speed 轨道开始的同一刻），
   `charge_full` 摆在 t=60（爆发的前一刻）。这样 Java 侧的逻辑时刻和玩家看到的画面是同一条时间线，
   不会出现"声音响了但法阵还没亮"。

**Java 侧只需要三行**（更多细节见 §9）：

```java
// 播放：像 §9.5 那样建执行器并 start()；Timeline 会自己跑，你不用管阶段
executor.start();
```

信号接收见 §8.7.7；后处理权重怎么算见 §8.8。

**验收清单**（按顺序过一遍）：

- Scrub 拖到 t=19 / t=21，法阵尺寸过渡应该是连续的，不应有跳变；
- 正向播放时 t=20 只收到一次 `charge_start`，t=60 只收到一次 `charge_full`（在监听器里打日志确认）；
- 蓄力段 `swirl` 的光点明显变慢但**不**卡顿（如果卡顿，检查 Speed 是否给得太极端，触发了每 tick 子步上限）；
- Mute 掉"蓄力"那条 Speed 轨道后再播，法阵应恢复常速——验证状态恢复；
- t=60 的爆发形状连续播三次应完全一致（固定种子生效）。

### 8.8 后处理：给整屏加效果

#### 8.8.1 后处理和粒子特效是两种东西

粒子特效是"往画面里**加**东西"；后处理是"把画好的画面**再处理一遍**"。

打个比方：粒子特效是往舞台上撒纸屑、开灯光；后处理是给摄像机镜头加滤镜——它拿到的是**整张已经画好的画面**
（颜色 + 深度），在整屏范围内做一次或多次像素计算，再放回去。

所以后处理天生适合表达这些"全局感"：

| 想要的效果 | 为什么必须用后处理 |
|---|---|
| 蓄力时屏幕边缘发暗、轻微扭曲 | 它作用在"整个屏幕"，粒子做不到 |
| 受击瞬间整屏红闪一下 | 同上，而且是逐帧可调的权重 |
| 开大时画面变灰再恢复 | 颜色分级属于全屏操作 |
| 让特效区域发光溢出（Bloom） | 需要先有整张画面才能做 |

它**不适合**表达"某个位置冒火花"——那是粒子的活。两者的产物最后会被合成到一起（Photon 是先合成 FX 层、
再跑后处理链，源码注释写得很明确：这样后处理才看得到你的粒子）。

> 术语对照：后处理（Post-Processing / Post FX）、全屏 Pass（Fullscreen Pass）、
> 渲染图（Render Graph）、全屏着色器图（Fullscreen Shader Graph）、权重（Weight）、遮罩（Mask）。

#### 8.8.2 核心心法：逐帧请求，不是开关

这是 Photon 后处理**最容易理解错**的一点，先把它刻进脑子里：

> 后处理没有"打开 / 关闭"这个动作。**你每渲染一帧就请求一次**；哪一帧不请求了，效果下一帧自然就没了。

源码里的入口只有这一行（`client/postfx/PhotonPostFX.java:45`）：

```java
// 只要这一帧想让它生效，就提交一次；停止提交 = 下一帧自动失效
PhotonPostFX.submit(PhotonPostFX.parsePath("wiki_tint_effect"),
                    Map.of("Tint", new float[]{1f, 0.2f, 0.2f, 1f}),
                    0.6f);   // weight：0 = 完全原画面，1 = 完全效果
```

`submit` 的实现只有三行：`effect == null || weight <= 0` 直接丢掉，否则把请求（权重大于 1 会被夹到 1）
放进本帧的请求列表（`PostEffectStack.java:95-98`）。就这么多——**没有状态、没有开关、没有持久化**。

这条模型带来三个直接推论，都是排错时的第一反应：

1. **"效果闪一下就没了"** → 你的请求代码只在某一帧跑了（比如放在了事件回调里，而没有每帧跑）。
2. **"效果关不掉"** → 还有别的地方在持续提交同一个效果（Timeline 的 Post Process Clip 是最常见的元凶）。
3. **"Timeline 里 Mute 掉 Clip 却没反应"** → 先确认没有 Java 侧的请求还在发。

#### 8.8.3 一个后处理效果由哪些东西组成

你不需要一次理解全部，先看这张职责表（整理自官方文档站与源码包结构）：

| 资源 / 运行时 | 职责 | 真实位置 |
|---|---|---|
| Fullscreen Shader Graph | 像素算法本体，以及它对外**暴露的** Sampler/Uniform | `client/postfx/shadergraph/FullscreenShaderGraph.java` |
| Core Shader Pass | 手写 JSON + VSH/FSH 的方案（移植老 Shader 用） | `client/postfx/runtime/CustomShaderPass.java` |
| Render Graph | Pass 的**顺序**、临时 Target、输入输出、Priority | `client/postfx/graph/RenderGraph.java` |
| Post Process Clip | 挂在 Timeline 上的那段"什么时候生效、权重多少、参数覆盖" | `client/fx/timeline/PostProcessClip.java` |
| PostEffectStack | 把同一帧的所有请求**合并**并真正执行 | `client/postfx/runtime/PostEffectStack.java` |

一句话串起来：**Fullscreen Graph 写算法 → Render Graph 把算法接成一条链 → Clip 或 Java 每帧按权重请求 →
Stack 合并请求并执行。**

#### 8.8.4 Render Graph：把 Pass 接成一条链

Render Graph 是一个**有向无环图**（DAG），节点只有两类：纹理资源、全屏 Dispatch。
它描述的是"一个可复用的效果"，**不是**整个游戏的渲染器——别把它当成 Minecraft 的渲染管线。

**输入节点**（Render Graph 能拿到的原料）：

| 输入 | 内容 |
|---|---|
| Scene Color | 全局效果轴上**当前位置**的 HDR 画面（已经包含优先级更低的效果） |
| Scene Depth | 捕获到的不透明场景深度 |
| Custom Mask | 被标记的 Photon 渲染器写入的 `mask group / 255`，其余为黑 |
| Custom Depth | 被标记渲染器的深度，底层预填场景深度 |
| Texture Input | 外部或命名纹理（LUT、噪声……） |
| Effect Weight | 本次执行合并后的请求权重 |

**Pass 的来源有两种**：跑一个 Fullscreen Shader Graph，或者跑一个手写 Core Shader。
Graph 里暴露出来的 Texture Sampler 会变成**只能连线的纹理 Port**；暴露的 Scalar / vec2 / vec3 / vec4
会变成**可填值的 Value Port**；Screen Size、Time、Matrix 这类引擎统一量则自动绑定。

**Target 尺寸**有三种模式：

| 模式 | 含义 |
|---|---|
| Screen Relative | 屏幕 / 效果链尺寸 × Scale |
| Input Relative | 更早的输入 / 资源尺寸 × Scale |
| Absolute | 固定的宽 × 高 |

质量够用的时候，Blur / Bloom 这类链条应该用**半分辨率**——这是后处理省性能最有效的一刀。
默认格式是 `RGBA16F`（支持 HDR）；低精度格式省显存和带宽，`R8` 适合单通道 Mask，但不适合 HDR 颜色。

**临时 Target 是从池子里借的**，只保证活到"本次执行不再需要它"为止。
除非外部系统明确说这个 Target 归它管，**不要假设 Pass 的输出能留到下一帧**。

**Priority 与 Auto Blend**：

- Priority 决定这个效果挂在全局轴的哪个位置。**内置 Bloom 的优先级是 0**：负值在 Bloom **之前**执行
  （所以结果会参与 Bloom，比如"先把发光提亮"），正值在 Bloom **之后**执行（用于处理最终画面）；
  相同 Priority 按 Effect Path 稳定排序。
- Auto Blend 打开时，最终执行的是 `mix(scene, effect, Weight)`；**只有**当你的图故意自己去读 Effect Weight
  并手动混合时才关掉它。

**依赖规则（编译期就会拦你）**：每个 Pass 的输入必须来自输入节点或**更早**的 Pass；
出现环、缺必需纹理、没有 Effect Output、Source Graph 无效、尺寸或类型不兼容，都会让编译失败。
实用建议：让图**从左到右**排，Blackboard 参数**按用途命名**，排错会轻松很多。

#### 8.8.5 动手做一个 Tint 效果（照着做）

下面这个例子把"从零到一个能被 Timeline 驱动的效果"走完一遍（做法对照官方
`post-processing/authoring-a-post-effect` 一页，讲法按"为什么这么做"重写）。

**第一步：建 Fullscreen Graph**（Resources 里新建，命名 `wiki_tint`）

1. 加一个名为 `Scene` 的 Texture/Sampler 类型的 Graph Variable；
2. 加一个名为 `Tint` 的 Color 参数，默认白色；
3. 把 **Fullscreen Position UV** 接到 `Scene` 的 Texture Sample；
4. 采样结果的 RGB 乘 `Tint.rgb`，Alpha 保持采样到的 Alpha；
5. 连到 **Fullscreen Output**，保存。

**关键认识**：需要被 Render Graph、Timeline 或 Java 从外部修改的值，**必须暴露成 Graph Variable**。
图内部的普通常量不会变成 Pass Port，外部也就永远改不到它——"为什么我的参数在 Clip 里找不到"通常就是这个原因。

**第二步：建 Render Graph**（命名 `wiki_tint_effect`）

1. 加一个 Scene Color 输入、一个 Pass、一个 Effect Output；
2. Pass 的 Source 选 `wiki_tint` 这张 Fullscreen Graph；
3. 把 Scene Color 接到 Pass 的 Scene Port；
4. 在 Pass 上设置 `Tint`（或者连 Blackboard 参数）；
5. Pass 的输出接到 Effect Output；
6. **保持 Auto Blend 打开**，保存。

Pass 的 Port 是**镜像** Source Graph 暴露出来的 Variable 的。所以重命名 `Scene` 或 `Tint` 之后，
原来的连线可能变成 Orphan（孤立），需要重新连一次。

**第三步：预览**。打开 Render Graph Preview，让效果盖在真实世界上：

- 把 Weight 拖到 0，画面应该**完全等于原图**；拖到 1，应该是完整效果。这条能过，
  说明"权重混合"这条链路是通的。
- 画面全黑时，**先把 Scene Color 直连 Effect Output**：如果这样能看到原画面，说明输入没问题，
  再去查 Pass 和纹理 Port 的连线。这是最快的二分法。

**第四步：挂到 Timeline 上**

1. 新建一条 Post Process Track；
2. 加一个 Clip，效果选 `wiki_tint_effect`；
3. 设 Start / Duration 和 Fade / Weight 曲线；
4. 在 Clip 的参数里覆盖 `Tint`；
5. 把播放头**拖过 Clip 的两端**，检查 Fade In/Out。

Clip 在有效范围内**每帧**提交请求；Clip 结束或被 Mute，就没有请求了，效果也就没了。

**第五步：打包**。Render Graph 依赖它的 Fullscreen Graph 以及它采样的纹理。导出 FX Pack 之前先保存所有资源，
然后**在一个干净的资源环境里测一遍导出的 Pack**——这样才找得出没被收集进去的全局资源依赖。

#### 8.8.6 Fullscreen Graph 与自定义纹理

Fullscreen Graph 和粒子用的 Shader Graph 是**同一套类型节点**，区别只在几何：这里是全屏四边形，
输入由 Render Graph 的 Pass Port 提供。

三个坐标相关的要点（都是踩过的坑）：

1. **Fullscreen Position** 提供全屏顶点基础与归一化 UV；
2. **Texel Size** 返回 Target Size 的倒数——Blur、Outline、色差的"像素偏移"都该乘它；
3. Scene / Depth 的重建节点使用**当前 Pass 捕获的相机状态**。

**不要写死屏幕分辨率。** 预览窗口、窗口缩放、UI Scale、半分辨率 Pass 都会改变真实的 Target Size。
写死 1920×1080 的效果，在别人的 2K 屏或 Photon 编辑器的小预览窗里就会走样。

**暴露输入的类型对照**：

| Graph Variable | 变成的 Port / 用途 |
|---|---|
| Sampler / Texture | 连 Scene Color、深度派生纹理、前一个 Pass、或 Texture Input |
| `float` | radius、strength、threshold、time scale |
| `vec2` | direction、center、distortion scale |
| `vec3` / `vec4` / color | tint、通道权重、描边颜色 |

注意：**Weight 是引擎管的，不是普通 Pass Port**。如果你的 Shader 想自己参与混合，要用 Effect Weight Node。

**外部纹理**：LUT、Blue Noise、Ramp、Lens Dirt 都可以通过 Texture Input 进来。
把它们放进项目 / FX Pack 的命名空间，并写清期望的 Filter / Wrap。
LUT 的尺寸和打包方式属于"效果的接口约定"——随便换一张图片不会得到有意义的结果。

**Graph Pass 与手写 Pass 怎么选**：

| Fullscreen Graph | 手写 Core Shader |
|---|---|
| 可视化编辑、类型连线 | 直接控制 GLSL |
| 暴露 Variable 决定 Port | 从 JSON 的 Sampler / Uniform 反射出 Port |
| 编译器生成 Stage 胶水代码 | 作者自己维护 JSON/VSH/FSH 的兼容性 |
| 更好重构资源 | 更容易移植已有 Shader |

两者跑在**同一个全屏 Quad 和 Target Pool** 上，也可以在同一条 Render Graph 里混用。

#### 8.8.7 手写 Fullscreen Shader（移植老 Shader 用）

当你要移植一个现成的后处理、写复杂循环、或者 Graph 暂时表达不了某个算法时，就写手写 Pass。
它和 Graph Pass 共享全屏 Quad、HDR Target Pool、Priority、Mask 和混合系统。

**最小资源结构**（通常不需要自己写顶点着色器，Photon 随 LDLib2 提供了 `ldlib2:fast_blit`）：

```text
assets/wiki/shaders/core/postfx/wave_tint.json
assets/wiki/shaders/core/postfx/wave_tint.fsh
```

Render Graph 里填的 **Shader ID 是 `wiki:postfx/wave_tint`**（命名空间 + 相对 `shaders/core/` 的路径，不带后缀）。

**JSON 长这样**：

```json
{
  "vertex": "ldlib2:fast_blit",
  "fragment": "wiki:postfx/wave_tint",
  "samplers": [
    { "name": "DiffuseSampler" }
  ],
  "uniforms": [
    { "name": "DiffuseSampler_TexelSize", "type": "float", "count": 4, "values": [1, 1, 1, 1] },
    { "name": "ScreenSize", "type": "float", "count": 2, "values": [1, 1] },
    { "name": "GameTime", "type": "float", "count": 1, "values": [0] },
    { "name": "Strength", "type": "float", "count": 1, "values": [4] },
    { "name": "TintColor", "type": "float", "count": 4, "values": [1, 1, 1, 1] }
  ]
}
```

**片元着色器**：

```glsl
#version 150

uniform sampler2D DiffuseSampler;
uniform vec4 DiffuseSampler_TexelSize;  // (width, height, 1/width, 1/height)
uniform vec2 ScreenSize;
uniform float GameTime;
uniform float Strength;
uniform vec4 TintColor;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float phase = texCoord.y * 40.0 + GameTime * 6.2831853;
    float offset = sin(phase) * Strength * DiffuseSampler_TexelSize.z; // .z 是 1/width
    vec4 scene = texture(DiffuseSampler, texCoord + vec2(offset, 0.0));
    fragColor = scene * TintColor;
}
```

（`ScreenSize` 在这个最小算法里没参与计算，留在那里只是为了演示引擎 uniform 的声明方式。
用不到的 uniform 可能被 GLSL 编译器优化掉，Photon 会安全地跳过不存在的 Location。）

**接进 Render Graph**：加 Pass 节点 → Source Type 改成 Custom Shader → Shader 填 `wiki:postfx/wave_tint`
→ Pass 会从 JSON **反射**出 `DiffuseSampler`、`Strength`、`TintColor` 这几个 Port
→ 把 Scene Color 或上一个 Pass 的输出连到 `DiffuseSampler` → Pass 输出连到 Effect Output。

`Strength` 和 `TintColor` 既可以直接填常量，也可以连 Blackboard 参数供 Timeline / Java 覆盖。
想让请求的 Weight 驱动某个 uniform，就把 Effect Weight 连到对应 Value Port——
**手写 Shader 里没有强制叫 `Weight` 的内置 uniform**，这一点和粒子 Shader 的直觉不一样。

改完 JSON 的 sampler/uniform 接口之后，要**执行一次资源重载，并在 Pass 上重新选一次 Shader**，
让节点重建 Port。源码损坏时编辑器会暂时保留最后一次有效的 Port 集合（免得一次失败的 reload 把连线全清掉），
但 Render Graph 仍然不会编译成功。

**Sampler 没有固定名字，全靠连线**（和粒子 Custom Shader 最不一样的地方）：

```json
"samplers": [
  { "name": "SceneColor" },
  { "name": "SceneDepth" },
  { "name": "NoiseTexture" }
]
```

这会生成三个**必连**的 Port。它们分别通常接：

| Render Graph 来源 | 常见用途 |
|---|---|
| Scene Color | 原始或前序效果后的 HDR 颜色 |
| Scene Depth | 深度淡出、景深、重建世界位置 |
| Custom Mask | 按 Mask Group 限制作用区域 |
| Custom Depth | 被 Photon 标记对象的深度 |
| 更早的 Pass Output | Blur、Bloom、Composite 等多 Pass 链 |
| Texture Input | LUT、噪声、Ramp 或外部参数纹理 |

**注意**：后处理**不使用** `SamplerSceneColor` / `SamplerSceneDepth` 这类自动绑定名（那是粒子 Shader 的约定）。
数据来源完全由 Render Graph 连线决定；**漏连任意一个 sampler 都会编译失败**。

**`_TexelSize` 约定**：给名为 `DiffuseSampler` 的 sampler 声明同名后缀 uniform `DiffuseSampler_TexelSize`，
Photon 会写入 `(width, height, 1/width, 1/height)`。它**不会**变成 Value Port，专供 Blur / Outline / 像素偏移使用。
Scene、Depth、Mask、Pass Output 的尺寸都是已知的，能正确更新；但 **Texture Input** 用固定资源或运行时参数时，
Executor 不一定知道原图尺寸，这时会保留 JSON 里的默认值——所以需要真实尺寸的算法应该把尺寸**当作独立参数传进来**。

**能被反射成 Port 的只有这几种**：

| JSON 写法 | Port 类型 |
|---|---|
| `"type": "float", "count": 1` | float |
| `"type": "float", "count": 2` | vec2 |
| `"type": "float", "count": 3` | vec3 |
| `"type": "float", "count": 4` | vec4 |

`int`、矩阵、以及 `count` 超出 1–4 的声明**都不会**成为参数 Port。
JSON 里的 `values` 是**每次 Dispatch 的默认值**：执行前先恢复默认，再应用 Pass 常量 / Blackboard 参数 / Effect Weight，
这样上一帧的值不会泄漏到下一次执行。
颜色没有专用反射规则——需要颜色就用 `float/count 4`，在 Render Graph 里以 vec4 传递。

**引擎自带的 Uniform**（不显示为 Port，直接用就行）：

| 名称 | 类型 | 内容 |
|---|---|---|
| `ScreenSize` | vec2 | 当前 Pass 输出 Target 的 (width, height)；半分辨率 Pass 拿到的是半分辨率尺寸 |
| `GameTime` | float | `RenderSystem.getShaderGameTime()`，一个 Minecraft 日周期为 0..1 |
| `ModelViewMat` | mat4 | 捕获该画面的相机 View / ModelView 矩阵 |
| `ProjMat` | mat4 | 捕获该画面的投影矩阵 |
| `<Sampler>_TexelSize` | vec4 | 已知输入纹理的 (width, height, 1/width, 1/height) |

**相机与屏幕空间 Uniform**（较新版本提供，做"重建世界位置"这类效果时需要）：

| 名称 | 类型 | 内容 |
|---|---|---|
| `U_ViewPort` | vec4 | 当前相机在 Pass Target 里的 (x, y, width, height)；编辑器场景可能只占一块子区域 |
| `kg_Time` | float | 与 GameTime 同源的秒值 |
| `kg_ViewMat` / `kg_IViewMat` | mat4 | View 矩阵及其逆 |
| `kg_IModelViewMat` | mat4 | 当前相机路径下同样为逆 View 矩阵 |
| `kg_IProjMat` | mat4 | 投影矩阵的逆 |
| `kg_CameraBlockPos` / `kg_CameraOffset` | vec3 | 精度拆分后的相机位置 |

用屏幕坐标时**先按 Viewport 归一化**，这样同一份 Shader 才能同时适配游戏全屏、编辑器的子窗口和降分辨率 Pass：

```glsl
uniform vec4 U_ViewPort;

vec2 viewportUV = (gl_FragCoord.xy - U_ViewPort.xy) / U_ViewPort.zw;
```

**自己写顶点着色器**（一般不需要）：Pass 用 `DefaultVertexFormat.POSITION`，最小接口就是——

```glsl
#version 150

in vec3 Position;
out vec2 texCoord;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    texCoord = Position.xy * 0.5 + 0.5;
}
```

顶点与片元的 varying 名称、类型必须匹配。全屏 Quad 已经覆盖 NDC 的 -1..1，**不需要**再乘普通世界对象的模型矩阵。

**排错顺序建议**：先写 `fragColor = texture(Input, texCoord);` 验证连线通了，再加 Depth、Mask 和数学；
JSON 必须是合法 JSON（不能有注释和尾随逗号）；加载失败看 `latest.log` 与 Render Graph 的编译错误。

#### 8.8.8 内置 Pass：不用自己写就有的一批积木

Photon 内置了一组可以由 Render Graph Pass 直接选用的 Core Shader，它们是**积木**——
一个完整效果可以接多个 Pass（比如"先横向模糊、再纵向模糊、最后叠加"）。

| 分类 | Pass |
|---|---|
| 颜色 | grayscale、sepia、brightness_contrast、hue_saturation、tint、posterize |
| 镜头/屏幕 | vignette、rgb_shift、pixelate、dot_screen、film、glitch、lens_distortion |
| Filter | blur_h、blur_v、sharpen、radial_blur |
| Composite | bright、add_mix、dof_composite |
| Edge/Mask | outline、show_mask、mask_outline |

Pass 节点会读 Core Shader 的 JSON：**Sampler 变成纹理 Port，Float/Vector Uniform 变成可配置的 Value Port**。
具体某个 Pass 有哪些参数，以节点 Inspector 为准（不同版本的参数会变，不要在文档里背参数名）。

#### 8.8.9 混合、Mask 与 Depth：让效果"只作用在想要的地方"

**请求合并**：同一个 Effect Path 的普通请求会**合并成一次执行**。合并公式是权重的顺序插值，
等价于：

```text
weight_total = 1 - (1 - w1)(1 - w2)(1 - w3) ...
```

也就是"两盏 0.5 的灯叠起来不是 1.0，而是 0.75"——这是从源码里能读出来的实际行为
（`PostEffectStack.blendRequests` 里的 `weight += (1f - weight) * request.weight()`）。
参数则分两类处理：可插值的按权重**从低到高顺序插值**（起点是 schema 默认值），
不可插值的**取权重最高的那条覆盖**。

**什么时候需要两条独立执行**：两个实例要保留**不同参数或不同 Mask Group** 时，把 Timeline Clip 上的
`Independent`（保留参数）打开。代价很直接——**多跑一次全屏执行**。

**Priority 与 Bloom**：效果按 Priority 围绕内置 Bloom（0）排序。
想让结果参与 Bloom（例如"先把亮度抬上去再让 bloom 抓"），用负 Priority；想处理 Bloom **之后**的最终画面，用正 Priority。

**Custom Mask 的正确用法**：

1. 在 Particle / Trail / Beam / AraTrail 渲染器上开启 **Write Custom Mask**；
2. 选一个 8-bit 的 Mask Group 和可选的 Alpha Cutoff；
3. 在 Render Graph 里读 Custom Mask，需要遮挡信息时再读 Custom Depth；
4. 用 `round(mask.r * 255)` 匹配 Group，或者直接启用 Post Process Clip 的 Mask Filter。

规则：**Group 0 是背景**，你要标记的组从 **1..255**。Custom Depth 能区分"标记的特效在几何前面可见"
还是"被几何挡住"。

两个现成的 Mask Pass：`mask_outline` 是可直接使用的逐 Group 描边；`show_mask` 用不同颜色显示 Group ID，
适合调试"我的 Mask 到底写进哪个组了"。

**一个必须知道的区别**：普通全屏 Outline 看的是 **Scene Depth**，它会检测场景里**所有**边缘；
Custom Mask Outline 只针对**被你选中的那些 Photon 渲染器**。两者目的不同，不能互相替代——
想要"只勾出我的技能特效"，就必须走 Custom Mask。

#### 8.8.10 Java 侧怎么用

公开入口是 `client/postfx/PhotonPostFX.java` 和 `IEffectExecutor#postEffectSink()`：

| API | 语义 |
|---|---|
| `PhotonPostFX.submit(effect, params, weight)` | 请求**当帧**生效；`params` 按参数显示名覆盖效果暴露的参数 |
| `PhotonPostFX.parsePath(text)` | 把文本解析成效果路径，接受 `type(path)` 全名或内置效果名 |
| `PhotonPostFX.listEffectPaths()` | 列出所有可请求的效果（Render Graph + Fullscreen Graph） |
| `PhotonPostFX.setTestEffect(path, weight)` / `clearTestEffect()` | 调试用的"钉住一个效果"，排查连线时很有用 |
| `IEffectExecutor#postEffectSink()` | 这个执行器的请求**提交到哪个栈**；默认是 `PostEffectStack.GLOBAL` |

两个栈实例：**`PostEffectStack.GLOBAL`**（游戏世界）与 **`PostEffectStack.EDITOR_SCENE`**（编辑器预览）。
自定义执行器如果想隔离自己，就在 `postEffectSink()` 里返回别的栈。

**执行时机**（决定了"效果什么时候能看见 FX"）：

- 没有光影包、也没有被延迟的 FX 层时，请求在"半透明粒子之后"那一步被消费
  （`PhotonPostFX.onLevelStageAfterParticles()`）；
- 有光影包时推迟到 `LevelRenderer` 整棵调用树返回之后（也就是 pack 的 composite / final 之后）
  （`PhotonPostFX.onLevelRenderComplete()`）；
- 帧边界由 `RenderFrameEvent.Post` 触发 `PhotonPostFX.onFrameEnd()`：回收输出、丢掉过期请求、推进池时钟。

另外注意：`submit` 的入参是 `IResourcePath`（不是 `Identifier`），`parsePath` 是给命令行和调试用的便捷入口。

**一个全局开关会静默吃掉你的请求**：`PostEffectStack.effectsAllowed()` 读的是客户端配置
`enable_custom_effects`（`PhotonConfig.INSTANCE.enableCustomEffects`）。它一旦关掉，
**请求不报错、也不执行**——"代码明明跑了但屏幕上什么都没发生"时先看这里。
（另外还有**每个视图**的开关 `PhotonViewSettings.effects`：栈是共享的，视图的偏好不是。）

#### 8.8.11 客户端配置键（调后处理 / 光影兼容时先看这张表）

| 键 | 默认 | 含义 |
|---|---|---|
| `enable_bloom` | `true` | Photon 自己的 bloom |
| `bloom_mip_level` | 5（2..10） | bloom 的 mip 层数 |
| `bloom_threshold` | 1.001（0..10） | bloom 阈值（HDR 亮度） |
| `bloom_intensity` | 0.7（0..1） | bloom 强度 |
| `enable_bloom_with_iris_shader` | `true` | 与光影包共存时是否仍跑 Photon bloom |
| `iris_composite_mode` | `AUTO` | Photon 把 FX 图像交还给光影包的方式 |
| `iris_use_translucent_particle_program` | `false` | 半透明 FX 是否走 pack 的粒子程序（默认关） |
| `fx_composite_mode` | `LATE`（可选 `VANILLA`） | 半透明 FX 的合成时机；`LATE` 防云层盖脸、防水面切特效 |
| `enable_custom_effects` | `true` | 自定义后处理链总开关（关掉后请求直接丢弃） |
| `enable_custom_effects_with_shader_pack` | `true` | 光影包下是否仍跑自定义后处理链 |
| `postfx_pool_budget_mb` | 256（16..4096） | 后处理池化渲染目标的显存预算 |

> 源码里有一处注释与实现不一致，别被带偏：`client/render/PhotonStage.java` 的 javadoc 提到 `AFTER_LEVEL`，
> 但枚举实际只有 `AFTER_OPAQUE_FEATURES`、`AFTER_TRANSLUCENT_PARTICLES`、`DEFERRED` 三个常量
> （`LAST = AFTER_TRANSLUCENT_PARTICLES`）。以枚举为准。

#### 8.8.12 性能：后处理为什么"看起来没多少代码却很贵"

一句话：**每个独立的全分辨率 Pass 都要把几百万个像素读一遍、写一遍。**

- **能合并就合并**：同一个效果的多条请求合并成一次执行；不要靠"多发几次请求"来加强度，直接调 weight。
- **大范围 Blur 用低分辨率**：Target Size 选 Input/Screen Relative 加一个小于 1 的 Scale。
- **不用 Mask 就别写 Mask**：Custom Mask 只有在本帧真的有效果会读它时才会被填充
  （源码里 `hasPendingMaskConsumer()` 就是干这个的），所以别为了"以后可能用到"而常开。
- **Weight 0 不要让它继续跑**：请求的 weight 小于 `1e-3` 会被合并阶段剔除，但如果你自己每帧提交
  一个 0.0001 的请求，仍要付出判断成本——干脆别提交。
- **Pixel Radius 与 Target Resolution 有关**，换窗口尺寸要重新检查效果（在大屏幕上够看的效果，在小窗口可能糊）。
- **`postfx_pool_budget_mb` 是硬预算**：效果叠太多、Target 尺寸太大时会顶到这个上限，表现为卡顿或掉帧。

#### 8.8.13 案例：受击瞬间的「红色屏幕脉冲」

**目标**：角色被击中的瞬间，屏幕边缘泛红并轻微收缩，0.35 秒内淡出，不影响中间的清晰度。

**路线选择**（先选便宜的）：

| 做法 | 成本 | 何时选 |
|---|---|---|
| 直接用内置 `vignette` + `tint` 两个 Pass 接成一条 Render Graph | 最低（不用写着色器） | 想要的就是"暗角 + 染色"，这个案例正合适 |
| 自己写 Fullscreen Graph | 中 | 需要特定的衰减曲线、噪点、色差 |
| 手写 Core Shader | 最高（要维护 JSON + GLSL） | 需要复杂循环或移植已有 Shader |

**第一步：资源侧（编辑器）**

1. Render Graph 命名 `mg_hurt_vignette`：
   - 输入：Scene Color；
   - Pass A：`tint`，`TintColor` 设为 `(1.0, 0.25, 0.25, 1.0)`，`Strength` 暴露成 Blackboard 参数；
   - Pass B：`vignette`，`Radius` 暴露成 Blackboard 参数，`Softness` 固定 0.45；
   - A 的输出接 B 的输入，B 的输出接 Effect Output；
   - Priority 用正值（**放在 bloom 之后**，因为受击反馈应该作用在最终画面上）。
2. 预览时：Weight 0 必须等于原画面；把 `Strength` 拉到 1、`Radius` 拉到 0.9，应该看到明显的边缘红晕。

**第二步：Java 侧（每帧提交，用一条衰减曲线自己算权重）**

```java
public final class HurtFlash {
    /** 脉冲总时长（tick）。0.35 秒 = 7 刻。 */
    private static final int DURATION = 7;

    private static long startedAt = Long.MIN_VALUE;

    /** 由受击事件调用（客户端）。 */
    public static void trigger() {
        startedAt = System.nanoTime();
    }

    /** 挂在每帧的渲染回调里：只有这一帧想让它生效，就提交一次。 */
    public static void onFrame() {
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
        float t = elapsedMs / (DURATION * 50f);          // 7 刻 × 50ms
        if (t < 0f || t >= 1f) {
            return;                                      // 不提交 → 下一帧效果自动消失
        }
        float weight = (1f - t) * (1f - t);              // 平方衰减：先强后弱，收得干脆
        PhotonPostFX.submit(PhotonPostFX.parsePath("mg_hurt_vignette"),
                Map.of("Strength", 0.9f * weight,
                       "Radius", 0.85f),
                weight);
    }
}
```

**为什么这么写**：

1. **用 `weight` 整体淡出，同时把 `Strength` 也乘一遍**——只调 weight 会同时让"染色"和"暗角"一起淡，
   看起来像是两个效果同步消失；把 `Strength` 也乘上权重，红色会先退、暗角后退，更像真实的受击反馈。
   （这属于美术取向，不是硬规定，可以按项目口味换曲线。）
2. **用平方衰减而不是线性**：线性淡出在前 30% 几乎看不出变化，平方（或 `1 - (1-t)^2` 的缓出）手感更"顿"。
3. **不提交就结束**，不写"关闭效果"的代码——这正是逐帧请求模型的价值。
4. **时间用系统毫秒而不是 tick**：受击反馈是纯客户端表现，用毫秒能跟渲染帧对齐，不会因为卡顿而"跳"一下。
   （若你要和游戏逻辑严格对齐，就改成 tick 计数。）

**验收清单**：

- 连打三次受击，脉冲应该**重新起算**而不是叠加成常亮（`startedAt` 被覆盖）；
- 把 `enable_custom_effects` 关掉再触发，屏幕不应该有任何变化，且日志里**不该有报错**（这是预期行为）；
- 装一个光影包再试一次，效果应该仍在（`enable_custom_effects_with_shader_pack` 默认为真）；
- 用 Timeline 的 Post Process Clip 做同样的效果，确认两者**不要同时存在**——否则你会看到双倍的强度。 

### 8.9 着色器图与自定义着色器

#### 8.9.1 先把三件事分开：材质、着色器、GPU 数据

新手最容易在这里绕晕，因为「粒子长什么样」其实由三个独立的东西共同决定：

| 东西 | 回答的问题 | 载体 |
|---|---|---|
| 材质（Material） | 用哪张贴图、怎么混合、什么渲染状态 | 材质资源（Inspector 的 Materials 列表） |
| 着色器（Shader / Shader Graph） | 每个像素**算什么**颜色 | Shader Graph 资源，或手写的 Core Shader JSON/VSH/FSH |
| GPU 数据（Additional / Custom Data） | 每个粒子**不一样的数值**从哪来 | 发射器的 Additional GPU Data 设置 + Shader 里的读取 |

选哪种方案，照这张表对号入座（整理自官方 `shaders-and-gpu` 一页）：

| 你的需求 | 用什么 |
|---|---|
| 只要贴图、混合、深度，普通粒子 | Texture 或 Sprite Material |
| 逐像素逻辑，但不想写 GLSL | Shader Graph Material |
| 已有 Core Shader JSON/VSH/FSH | Custom Shader Material |
| 处理 Scene Color 或整屏 | Fullscreen Graph + Render Graph（见 §8.8） |
| Shader 里要逐粒子的动画量 | Additional GPU Data |
| 数值由 Timeline 或 Java 控制 | Custom Data Stream |

**给新效果的默认建议：优先用 Shader Graph。** 它会自己声明需要的数据流，并自动适配 Photon 的不同渲染路径
（CPU 粒子 / GPU 实例化的 Tile 与 Model / Trail / Beam / AraTrail）。手写 Shader 的适用场景是"移植现成代码"
或"Graph 暂时表达不了的算法"——代价是那些适配要你自己维护。

> 术语对照：着色器图（Shader Graph）、函数图（Function Graph）、子图（Subgraph）、
> 黑板参数（Blackboard Parameter）、顶点阶段（Vertex Stage）、片元阶段（Fragment Stage）、
> 顶点格式（Vertex Format）、实例化（Instancing）。

#### 8.9.2 从零做一个 Shader Graph（照着做）

需求：做一个带纹理、并且在与方块交界处柔和消隐的粒子。做法对照官方
`shader-graph-getting-started` 一页，这里按"每一步在解决什么"重写。

**第一步：建 Graph 和材质**

1. Resources 里新建一个 Shader Graph，命名 `soft_particle`；
2. 再新建一个 **Shader Graph Material**；
3. 把材质的 Graph 资源指向 `soft_particle`；
4. 把这个材质赋给粒子发射器的 Renderer。

这里有个**必须理解的设计**：**Graph 和材质是两个资源**。多个材质可以复用同一张 Graph，
各自只覆盖贴图、颜色或数值参数。把 Graph 想成"函数"，材质想成"一次调用 + 实际参数"。

**第二步：接出 Fragment 输出**

```text
Particle Data.uv ──> Sample Texture.uv
Texture 参数 ──────> Sample Texture.texture
Sample Texture.rgb × Color 参数.rgb ─> Base Color
Sample Texture.a   × Color 参数.a   ─> Alpha
```

连到 Fragment Output 之后**保存一次**，Photon 会编译 Graph 并刷新材质预览。

**第三步：加柔和交界**

加一个 **Depth Fade** 节点，把它乘到 Alpha 上。它比较"粒子自己的深度"和"场景深度"，
让大尺寸的 Billboard 穿过方块时淡出，而不是出现一条生硬的分割线。

> 坑：Depth Fade 是 **Fragment Stage 的屏幕空间计算**。不要把 `Particle Data` 的 UV 接到 `Screen UV` 上——
> 那会让贴图跟着屏幕动，而不是跟着粒子动。

**第四步：把该暴露的参数暴露出来**

把 `Texture`、`Tint`、`Fade Distance` 设成 Graph 参数，在 Graph 里给默认值，在**材质**里覆盖。
参数名会被序列化——**重命名之后要同步改所有引用旧名字的材质**，否则你会看到一个"参数消失了"的材质。

**第五步：验证渲染路径**（这一步最容易被跳过，然后上线才发现问题）

- Alpha 混合与 Additive 混合都试一遍；
- 普通 Tile 与 GPU Instanced Tile 都试一遍（两者走的是不同的着色器变体）；
- 与方块几何的交界处看一遍；
- 如果项目支持 Iris，开 Iris 再测一遍；
- 对大于 1 的颜色测 HDR / Bloom。

**全黑时的二分法**：先把一个常量颜色直接接到 Base Color。如果这样能看到粒子，说明管线是通的，
问题出在贴图或参数；再把 Texture、Particle Color、Lighting 逐个接回去，很快就能定位。

**保存顺序**：先存 Graph，再存材质，最后存项目。FX Pack 会跟着资源引用收集依赖——
Graph、材质、纹理都必须有可解析的资源路径。

#### 8.9.3 Stage、Function 与 Subgraph：把复杂图拆开

Shader Graph 最终会被编译成 Minecraft 的一个 Render Pass。**先想清楚你的表达式在哪个 Stage 执行**，
能避开大部分的编译错误和插值问题。

| Stage | 执行频率 | 适合算什么 |
|---|---|---|
| Vertex（顶点） | 每个输入顶点一次 | 位置形变、读取 Instance Data、较粗粒度的计算 |
| Fragment（片元） | 每个覆盖到的像素一次 | 纹理/颜色、Alpha/Discard、法线/雾/光照、Depth Fade |

**Additional Data 与 Custom Data 是在 Vertex Stage 读的**。如果某个 Fragment 节点需要它，
编译器会**自动创建 Varying**——所以你不需要手写 `out` / `in`。这一点和手写 Shader 完全不同（见 §8.9.7）。

**输出口的含义**：

| 输出 | 作用 |
|---|---|
| Position | 修改最终顶点位置（连之前必须明确坐标空间） |
| Base Color | 普通的着色颜色 |
| Emission | 增加不受光照影响的 HDR 能量（发光感主要靠它） |
| Alpha | 控制透明度与混合 |
| Discard / Alpha Clip | 直接丢弃不满足条件的像素 |

**一条纪律**：不要用 Discard 去代替正确的 Blend / Depth 状态。Cutout 可以写深度，
半透明边缘一般**不应该**写深度——否则你会看到背景被切掉一块。

**Function Graph（函数图）** 用来封装可复用的计算：声明有类型的输入输出、保存成资源，
然后在粒子 Graph 里实例化。适合 UV 扭曲、调色板映射、Dissolve Mask、共享光照模型这类东西。

两点限制：Function Graph **不保存材质的渲染状态**（贴图/Uniform 参数仍然由调用的 Graph 或材质持有）；
并且**不要形成递归引用**，Photon 会把它报成编译错误。

**常见报错对照**：

| 报错 / 表现 | 先查什么 |
|---|---|
| Type mismatch | 报错端口两侧的 Vector 宽度与隐式转换 |
| 缺少必须输出 | Fragment Output 是否接上、Graph 类型是否选对 |
| 未知 Function / Resource | Function Graph 的路径，以及 FX Pack 依赖有没有收进去 |
| 编译成功但数据全是 0 | Additional Data 不受支持，或者正在走 CPU 路径 |
| 改完还是旧结果 | 保存 Graph → Reload Shader → 清 Photon FX Cache |

#### 8.9.4 核心节点参考（用的时候翻这一小节）

Photon 的 Shader Graph 用的是 KilaGraph 那套通用节点。节点上的 Tooltip 与 Description Panel
会给出**准确**的端口和类型——文档只给分类，别背端口的默认值。

| 分类 | 常用节点 | 干什么用 |
|---|---|---|
| 基础数学 | Add、Subtract、Multiply、Divide、Min、Max | 组合 Mask 和数值 |
| 高级数学 | Power、Exp、Log、Sqrt、Reciprocal | 衰减与响应曲线 |
| 范围 | Clamp、Saturate、Remap、Smoothstep、Step | 归一化并塑造过渡 |
| 三角函数 | Sin、Cos、Tan、Atan2 | 波形、旋转、极坐标 |
| 逻辑 | Compare、Select、And、Or、Not | 不用 Java 分支的条件 Mask |

三条实用经验：

1. **送进 Alpha 的算术结果建议过一遍 Saturate**，免得出现负值或大于 1 的 Alpha 造成怪异混合；
2. **`Smoothstep(edge0, edge1, x)` 是生成稳定柔边的首选**；把两个 Edge 交换就得到反向渐变；
3. **Mask 尽量保持标量**，不要让整条计算链都携带 `vec4`——省下来的不只是寄存器，还有你的脑容量。

**向量与矩阵**：`Compose`/`Split`/`Swizzle` 组合与拆解通道；`Dot` 测方向一致程度；`Cross` 求垂直方向；
`Length`/`Distance`/`Normalize` 用于方向与径向 Mask；`Matrix Multiply`/`Transform` 转换坐标空间
（点和方向的处理不同：**平移不应该影响方向**）。

**UV 与程序化图案**：UV 节点有 Tiling/Offset、Rotate、Polar/Twirl、Flipbook、Screen Mapping；
程序化节点能生成 Noise、Checker、Ellipse、Rectangle、Gradient、Voronoi 类 Mask。
注意：**程序化 Noise 会为每个顶点/像素重算**——图案不需要随时间变化时，用一张贴图通常更便宜。

**法线、雾、光照**：Normal 节点负责解包与转换法线贴图；Lighting 使用世界法线、光源和视线方向；
Fog 节点与 Minecraft 的雾混合。
**一个高频错误**：Photon 的 `Particle Data.litColor` **已经包含**方块光与天空光，而原始 `color` 没有。
除非你故意想双重光照，否则不要把它们再乘一遍。

**四个可以直接抄的小结构**：

```text
Dissolve:     noise - threshold -> Smoothstep -> Alpha
Rim:          1 - Saturate(Dot(normal, viewDirection)) -> Power -> Emission
Flipbook:     ParticleData.uv + frame/tiles -> Sample Texture
Soft particle: texture alpha × Depth Fade -> Alpha
```

#### 8.9.5 Photon 节点参考

这些是 Photon 自己加进图里的节点：它们把通用图编译器接到"粒子实例""场景缓冲""全屏 Pass"上。

| 节点 | 输出 | 在哪个 Graph / Stage 可用 |
|---|---|---|
| Particle Data | position、color、litColor、uv、normal | 粒子 / Function Graph；两个 Stage 都行 |
| Additional Data | 所选 Scalar 或 vec3 Channel | Vertex 读取并自动 Varying；不支持时为 0 |
| Custom Data | 所选 vec4 Stream | Vertex 读取并自动 Varying；Preview/CPU 路径为 0 |
| Viewport | Viewport Size 与屏幕信息 | 屏幕相关计算 |
| Depth Fade | 交界淡出 | Fragment；需要 Scene Depth |
| World to Screen UV | 世界坐标对应的屏幕 UV | 坐标转换 |
| Screen to World | 重建出的世界坐标 | Fragment；需要 Depth / Camera Matrix |

关于 `Particle Data` 的三个字段，值得单独记一下：

- `position` 是**插值后的、相机相对的世界位置**（和 §6.2 的结论一致）；
- `color` 是编写/运行时的粒子颜色；
- `litColor` 已经乘过烘焙光照贴图——这就是上面说的"别再乘一次"的那个。

Trail 的 UV 通常沿**长度轴**走，和 Sprite Frame 的含义不同。

**Additional Data 的下拉列表直接来自 `PhotonGpuChannels` 注册表**：选中某个 Channel 时，编译器会把它标记为
**必需数据**，Shader Graph 的 Instancing 会自动上传，**不需要你在 Inspector 里勾选**。
（手写材质反过来——必须手动开，见 §8.9.9。）

**全屏图专用节点**：Fullscreen Position、Fullscreen Output、Texel Size、Scene Color/Texture Input、Scene Depth。
它们**不能**放进粒子 Shader Graph；反过来 `Particle Data` 在全屏图里也没有意义。

**屏幕空间的三条准确性要求**（官方专门列了出来，都是血泪）：

1. Photon 节点会自己处理 UV 原点和深度约定，**不要手写固定的翻转**；
2. 采样邻近像素要用 Texel Size，**不要写死 `1/1920`**；
3. 除非节点明确说了已经线性化，**深度是非线性的**。

Iris 下 Scene/Depth Buffer 是否可用取决于具体管线——**必须测试"没有 Buffer 时的安全输出"**，
否则玩家装了光影就会看到黑屏。

> 自 2.2.2 起，Photon 节点在编辑器里会说明端口、Stage 限制、Channel 类型和对应 GLSL。
> **连线之前先选中节点打开 Description Panel**，比事后猜端口快得多。

#### 8.9.6 Custom Shader Material：什么时候该用它

Custom Shader Material 直接加载 Minecraft Core Shader 的 **JSON + VSH + FSH**。
它不是被 Shader Graph 淘汰的旧功能——要移植 GLSL、用几何着色器、或写 Graph 还没覆盖的算法时就用它。

**文件放哪**：

```text
assets/<namespace>/shaders/core/<name>.json
assets/<namespace>/shaders/core/<name>.vsh
assets/<namespace>/shaders/core/<name>.fsh
assets/<namespace>/shaders/include/<name>.glsl     # 可选，公共函数
```

材质里填的 **Shader ID 是 `<namespace>:<name>`**——不含 `assets/`、`shaders/core/` 和 `.json`。

```text
assets/wiki/shaders/core/dissolve_particle.json   →   wiki:dissolve_particle
```

即使是拖文件选择器，文件也必须位于某个 `assets/<namespace>/shaders/core/` 下，Photon 才能推导出 Shader ID。

**在编辑器里用**：Emitter 的 Renderer > Materials 加一个 Custom Shader Material → Select Shader
（或直接填 ID）→ 编译成功后展开 Shader Settings → 给自定义 Texture/数字/向量/颜色填值 →
改完 JSON/VSH/FSH 点 **Reload Shader**。预览里出现红色错误文字时**先修编译错误再重载**：
Photon 会缓存失败状态，不会每帧重试创建失败的 GL Program。

**Inspector 怎么识别参数**（决定你起的名字会变成什么控件）：

| JSON 声明 | Inspector 控件 |
|---|---|
| 自定义 sampler（如 `Texture`、`NoiseTexture`） | 纹理选择与预览 |
| `int`，count 1–4 | 整数或整数向量 |
| `float`，count 1–4 | 浮点数或浮点向量 |
| `vec3` 且名称含 `color` / `rgb` | RGB 颜色 |
| `vec4` 且名称含 `color` / `rgba` | RGBA 颜色 |
| `vec4` 且名称含 `hdr` / `emission` | HDR 颜色与强度 |

**保留前缀（必须记住）**：所有以 `Sampler` 开头的 sampler、所有以 `U_` 开头的 uniform 都被视为**内置项**，
不会出现在 Shader Settings 里。自定义参数**不要用这两个前缀**——用了既不绑定也不显示，还很难查。

参数值随材质保存；重载 Shader 时只要名称和类型仍然匹配，Photon 会尽量保留原值；
**删除或改名的参数需要重新配置**。

**和 §2/§3 的 `RenderPipeline` 体系怎么接？** 这一条很容易被误解，说清楚：

- Photon 自己**不**往 `RegisterRenderPipelinesEvent` 里注册你的材质。你在 JSON 里声明的 Shader
  会被 Photon 的材质/渲染状态体系消化成它自己的管线（核心是 `IMaterial.getRenderType(...)`
  配合 `BlendMode.toBlendFunction()`），由 Photon 的批处理统一画。
- 所以**"我想让粒子用我自己的渲染管线"和"我想自己画一批几何"是两件事**。前者走 Photon 材质（本节）；
  后者走 Blaze3D 的管线层（§2.4、§3.4 的 `RenderPipeline` + `RegisterRenderPipelinesEvent`）。
- 26.2 的 Photon 里，1.21 时代那个 `PhotonFXRenderPass` **现在是 M0 桩**，批处理的实际承担者换成了
  `PhotonWorldRenderState` 里的渲染状态批次。读老资料时看到"往 PhotonFXRenderPass 里塞东西"的写法，
  要按"已经变了"来理解。

#### 8.9.7 手写 Core Shader：一个完整的最小例子

这个例子读取粒子顶点、光照贴图和一张由 Inspector 指定的纹理，并暴露 `TintColor` 与 `DiscardThreshold`。

**第 1 步：JSON**（`assets/wiki/shaders/core/wiki_particle.json`）

```json
{
  "vertex": "wiki:wiki_particle",
  "fragment": "wiki:wiki_particle",
  "samplers": [
    { "name": "Sampler2" },
    { "name": "Texture" }
  ],
  "uniforms": [
    { "name": "ModelViewMat", "type": "matrix4x4", "count": 16,
      "values": [1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1] },
    { "name": "ProjMat", "type": "matrix4x4", "count": 16,
      "values": [1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1] },
    { "name": "FogStart", "type": "float", "count": 1, "values": [0] },
    { "name": "FogEnd", "type": "float", "count": 1, "values": [1] },
    { "name": "FogColor", "type": "float", "count": 4, "values": [0, 0, 0, 0] },
    { "name": "FogShape", "type": "int", "count": 1, "values": [0] },
    { "name": "TintColor", "type": "float", "count": 4, "values": [1, 1, 1, 1] },
    { "name": "DiscardThreshold", "type": "float", "count": 1, "values": [0.01] }
  ]
}
```

`Sampler2` 是**内置光照贴图**（保留前缀）；`Texture` 不带保留前缀，所以会出现在 Shader Settings 里供你选图。

**第 2 步：顶点着色器**（`.../wiki_particle.vsh`）

```glsl
#version 330 core

#moj_import <photon:particle.glsl>
#moj_import <minecraft:fog.glsl>

uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out float vertexDistance;
out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    ParticleData data = getParticleData();

    gl_Position = ProjMat * ModelViewMat * vec4(data.Position, 1.0);
    vertexDistance = fog_distance(data.Position, FogShape);
    texCoord0 = data.UV;
    vertexColor = data.Color * texelFetch(Sampler2, data.LightUV / 16, 0);
}
```

**为什么要用 `getParticleData()`**：`photon:particle.glsl` 把 CPU 粒子、Tile/Model 的 GPU 实例化、Trail、Beam、
AraTrail **统一**成同一个输入结构。不要直接假设原版的 `Position`、`UV0` 这些 Attribute 一定存在——
在 GPU 实例化路径下它们本来就不存在。

```glsl
struct ParticleData {
    vec3 Position;
    vec4 Color;
    vec2 UV;
    ivec2 LightUV;
    vec3 Normal;
    vec3 ObjectPosition;
    vec3 ObjectNormal;
};
```

因为 Instancing 和 Buffer Texture Accessor 用到 GLSL 330 的功能，**使用 Photon Helper 的顶点着色器要声明
`#version 330 core`**（片元着色器用 150 也可以，但两者用到的 varying 必须匹配）。

**第 3 步：片元着色器**（`.../wiki_particle.fsh`）

```glsl
#version 150

#moj_import <photon:particle_utils.glsl>

uniform sampler2D Texture;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform vec4 TintColor;
uniform float DiscardThreshold;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = texture(Texture, texCoord0) * vertexColor * TintColor;
    if (color.a < DiscardThreshold) {
        discard;
    }
    fragColor = apply_fog(color, vertexDistance, FogStart, FogEnd, FogColor, FogShape);
}
```

**第 4 步：在材质里填 Shader ID** `wiki:wiki_particle`，为 `Texture` 选一张 PNG，调 `TintColor` 与
`DiscardThreshold`。三个文件里声明的名称、类型、以及 Vertex/Fragment 的 varying **必须完全一致**。

**第 5 步：认识 Renderer Variant**。Photon 会按**实际绘制方式**重新编译同一个 Shader，并注入一个 Define：

| Define | 使用场景 |
|---|---|
| （无） | CPU 路径与材质预览 |
| `PARTICLE_INSTANCE` | Tile 粒子 GPU 实例化 |
| `PARTICLE_MODEL_INSTANCE` | Model 粒子 GPU 实例化 |
| `TRAIL_INSTANCE` | Particle Trail |
| `BEAM_INSTANCE` | Beam |
| `ARA_TRAIL_INSTANCE` | 扁平 AraTrail |
| `ARA_TRAIL_TUBE_INSTANCE` | 管状 AraTrail |

`getParticleData()` 会处理这些差异。**如果你绕过 Helper 自己声明 Attribute，就必须为每一个目标 Define
写一份匹配的布局**——这就是"为什么我用 Helper 好好的，自己写就崩了"的答案。

**几何着色器（ExtendedShader）**：在 JSON 里加一行 `geometry`，对应的 `.../wiki_particle.gsh` 会在
Program Link 前被附加进来：

```json
{
  "vertex": "wiki:wiki_particle",
  "geometry": "wiki:wiki_particle",
  "fragment": "wiki:wiki_particle"
}
```

只有在**确实需要改变或生成图元**时才用它。它**不会**自动适配不同 Renderer 的输入——
顶点着色器仍然要先产出正确的数据。

**两个现成的 Include**：

- `#moj_import <photon:particle.glsl>`：顶点布局与 `ParticleData`；
- `#moj_import <photon:particle_utils.glsl>`：`getCurveValue()` 与 `getGradientValue()`；
- `#moj_import <namespace:file.glsl>`：加载 `assets/<namespace>/shaders/include/file.glsl`。

**编译问题排查顺序**（按出现频率排）：

1. JSON 是不是合法 JSON（不能有注释、不能有尾随逗号）；
2. Shader ID 与 JSON 里的 Vertex/Fragment/Geometry **都不带扩展名**；
3. 打开 `latest.log`，找**最早出现**的那条 JSON / Include / Compile / Link 错误（后面的都是连带的）；
4. 让片元先固定输出 `vec4(1, 0, 1, 1)`，再逐项恢复纹理、varying 和计算；
5. 改完文件点 **Reload Shader**；如果改的是资源包本身，再执行一次 Minecraft 的资源重载。

#### 8.9.8 内置 Uniform 与 Sampler 查询表

名称**区分大小写**，而且必须同时出现在 Shader JSON 和真正使用它的 GLSL Stage 里；
没被 GLSL 实际用到的项可能在编译时被优化掉（这属于正常现象，不是配置丢了）。

**以 `Sampler` 开头的内置 Sampler**（不会出现在 Shader Settings 里）：

| 名称 | 数据来源 | 说明 |
|---|---|---|
| `Sampler0` | RenderSystem 纹理槽 0 | 常用主纹理槽；**只有当前 Render Pass 明确设置了槽 0 时才可靠**。要自己选纹理请用自定义 sampler |
| `Sampler2` | Minecraft 光照贴图 | 用 `texelFetch(Sampler2, lightUV / 16, 0)` 读方块光与天空光 |
| `SamplerBlockAtlas` | Minecraft 方块图集 | Photon 加载材质时绑定方块图集；要配合**图集 UV**使用 |
| `SamplerSceneColor` | 当前 Photon Render Pass 的场景颜色 | 只在有效的 RenderPassPipeline 里可用 |
| `SamplerSceneDepth` | 同一场景的深度 | 深度是非线性的设备深度；重建位置要同时用逆矩阵和视口 |
| `SamplerCurve` | 材质的 Curve Texture | 最多 128 条曲线，每条 128 个采样 |
| `SamplerGradient` | 材质的 Gradient Texture | 最多 128 条渐变，每条 128 个采样 |

两条容易误解的：**`SamplerCurve` / `SamplerGradient` 的数据是在材质面板里编辑的**，不是普通 PNG；
然后 **Scene sampler 是"当前绘制管线"的快照**，不保证包含更晚绘制的透明物体，
所以材质预览或没有活动的 Photon Render Pass 时**拿不到有效的场景纹理**——这是预期行为。

Curve 与 Gradient 的用法：

```glsl
#moj_import <photon:particle_utils.glsl>

uniform sampler2D SamplerCurve;
uniform sampler2D SamplerGradient;

float strength = getCurveValue(SamplerCurve, 0, age01);     // 行号 0..127，第三参是归一化横坐标
vec4  color    = getGradientValue(SamplerGradient, 0, age01);
```

**Photon 动态 Uniform**（每次 Apply 都会更新）：

| 名称 | 类型 | 值 |
|---|---|---|
| `U_CameraPosition` | vec3 | 当前 Photon 相机的世界坐标 |
| `U_InverseProjectionMatrix` | mat4 | 当前投影矩阵的逆 |
| `U_InverseViewMatrix` | mat4 | 当前 ModelView 矩阵的逆 |
| `U_ViewPort` | vec4 | OpenGL Viewport 的 (x, y, width, height) |

正如 §8.9.6 强调的：**`U_` 是保留前缀，而且只会更新上表这四个名字**。
自定义 uniform 以 `U_` 开头的话，既不会自动绑定，也不会出现在 Inspector 里——两头不落。

从片元坐标算屏幕 UV 的标准写法：

```glsl
uniform vec4 U_ViewPort;
vec2 screenUV = (gl_FragCoord.xy - U_ViewPort.xy) / U_ViewPort.zw;
```

**ShaderInstance 认得的 Minecraft 内置 Uniform**（只声明你真正需要的）：`ModelViewMat`、`ProjMat`、
`TextureMat`、`ScreenSize`、`ColorModulator`、`Light0_Direction`、`Light1_Direction`、`FogStart`、`FogEnd`、
`FogColor`、`FogShape`、`GameTime`、`GlintAlpha`、`LineWidth`、`ChunkOffset`。

注意最后一句限定：**不同的 Minecraft 渲染路径不一定为每个值提供有意义的状态**。
粒子材质里常用的是矩阵、雾、颜色、Screen Size 和 Game Time。

**自定义 sampler / uniform**：不在上面列表里的参数由 Inspector 管理，例如

```json
"samplers": [ { "name": "NoiseTexture" } ],
"uniforms": [
  { "name": "NoiseScale", "type": "float", "count": 1, "values": [4] },
  { "name": "EmissionColor", "type": "float", "count": 4, "values": [1, 0.4, 0.1, 1] }
]
```

`NoiseTexture` 变成纹理选择项，`NoiseScale` 是数字，`EmissionColor` 因为名字里含 `emission` 会变成 HDR 颜色控件。
自定义值会复制到同一材质的所有 Renderer Define Variant，并随 FX 保存。

#### 8.9.9 GPU 数据：让每个粒子不一样

这是让 Shader 真正"活起来"的东西：**把模拟产生的逐粒子数值直接喂给材质**，不需要 CPU 每帧重建几何。
它由两部分组成：

1. **内置 Channel**：Normalized Age、Velocity、Emitter Position、Trail Point Life、Beam Direction 等；
2. **最多 4 条 Custom Data Stream**：每条按 `vec4` 采样，并且可以在运行时覆盖。

**内置 Channel 支持表**（`✓` 表示该渲染器支持，不支持的组合返回 0）：

| Channel | 类型 | Particle | Trail / AraTrail | Beam |
|---|---|---|---|---|
| random | float | ✓ | ✓ | ✓ |
| `t`（归一化 Age） | float | ✓ | ✓ | ✓ |
| age / lifetime | float | ✓ | — | — |
| position / velocity | vec3 | ✓ | — | — |
| isCollided | float | ✓ | — | — |
| emitter t / age | float | ✓ | ✓ | ✓ |
| emitter position / velocity | vec3 | ✓ | ✓ | ✓ |
| point t / point life | float | — | ✓ | — |
| beam direction / length | vec3 / float | — | — | ✓ |

两个细节：**`point t` 会沿 Trail 段插值**；**Beam Direction 等于 `end - start`**，
只要你想要的只是方向就记得 `normalize`。

**在 Shader Graph 里读内置 Channel**：加一个 Additional Data 节点并选 Channel。
编译器会**自动启用** Graph 实际读到的 Channel，不需要手动勾选（这点和手写材质相反）。
数据在 Vertex Stage 从固定的 `PhotonData` Record 读取，Fragment 用到时会自动经过 Varying。
非实例化的 CPU 路径与节点预览返回 0。

**配置 Custom Data Stream** 的步骤：

1. 在 Emitter Inspector 里启用 Additional GPU Data Setting；
2. 在 Custom Data 列表里加 Stream，最多 4 条；
3. 选 Stream 类型、Time Source，以及各 Channel 的 Function。

**Stream Index 就是列表顺序（从 0 开始）**，Shader 按 Index 读。给 Channel 改显示名**不会**改变 GPU 布局，
但**移动或删除 Stream 会改变后面所有 Index**——这时 GLSL 里的 Index 也得跟着改。

| Stream 类型 | Inspector 内容 | GPU 值 |
|---|---|---|
| Vector | 1–4 条独立的 Number Function，可分别命名 | `vec4(x, y, z, w)`，未使用的分量为 0 |
| Color | 一条 HDR Color/Gradient Function | `vec4(r, g, b, a)`，HDR 的 RGB 可以大于 1 |

**Time Source 决定采样用的 x 是什么**：

| Source | 采样 x | 可用 Emitter |
|---|---|---|
| Self | Particle/Beam 自身的归一化生命周期；Trail/AraTrail 段的归一化生命 | 全部 |
| Emitter | 所属 Emitter 的归一化时间 | 全部 |
| Length | 当前点在 Trail 长度方向的位置 | Trail、AraTrail |

**同一条 Stream 的所有 Channel 共用同一个 Time Source。** 另外，Random Function 使用"每个粒子、每条 Stream
独立且稳定"的 Random Key——所以 Buffer 每帧重传也**不会**让随机值闪来闪去。

**在 Shader Graph 里读 Custom Data**：加 Custom Data 节点，把 Index 设成 emitter 里的 Stream Index（0..3），
节点**永远输出一个 `vec4`**。Vector Stream 用 Split 取 `x/y/z/w`；Color Stream 可以直接连颜色或 Emission。

```text
Custom Data (Index 0)
        │
      Split
      ├─ x ──> Alpha / Dissolve Threshold
      └─ y ──> UV Offset Strength
```

节点在 Vertex Stage 调用 `photon_custom_data(index)`；Fragment 用到输出时编译器自动建 Varying。
Compiler 也会标记该材质需要 Custom Data，Renderer 随后为每个 Instance 上传固定 4 个 RGBA32F Texel 的
`PhotonCustomData` Record。

**以下情况会输出 `vec4(0)`**（所以**别只看节点预览**，要在真正使用的 GPU 实例化粒子 / Trail / Beam / AraTrail 上验证）：
节点预览、非实例化的 CPU 渲染、没启用 Additional GPU Data、Emitter 没有对应 Stream、Index 大于 3
（负数会被夹成 Stream 0）。

**在 Custom Shader Material 里读 Custom Data**：可以读，但**接口完全不同**。
手写材质走的是传统 **Instance Attribute** 路径——每条 Custom Data Stream 是一个追加的 `vec4` Attribute。

> **不要在手写材质里调用 `photon_custom_data()`。** 它读的是 `PhotonCustomData` 这个 Buffer Texture，
> 而这个 Buffer 只在"同一个 Pass 里确实有 Shader Graph 材质用了 Custom Data"时才会上传，
> 对手写材质不是一个稳定的接口。手写材质应当声明 Attribute。

光是"把逐粒子数据送进 Shader"这一件事，Photon 里就有**两条完全不同的数据通路**，混用是常见的翻车点：

| 通路 | 谁在用 | 位置与布局 |
|---|---|---|
| PhotonData 记录（Buffer Texture，GLSL 里的 `photon_custom_data(i)`） | Shader Graph 材质 | 通道按注册表顺序**固定打包**进 vec4 槽位，**和"启用了哪些通道"无关** |
| 顶点属性尾部（legacy） | 手写 Custom Shader Material | **每个被勾选的通道各占一个 Location**，位置随启用集合变化 |

本机源码把这两条路写得很清楚（`AdditionalGPUDataSetting` 的类注释）：记录路径"一个编译好的 Shader
可以服务任意启用集合"（`PhotonGpuChannels` 的类注释），而顶点属性路径是"每个启用的通道一个属性，
从该 kind 的 base location 开始按注册表顺序排，Custom Data 的 vec4 再接在后面"（`planAttribs`）。

这就解释了两个看起来矛盾的现象：**Shader Graph 里"勾不勾选开关都不影响结果"**，
而**手写材质里"多勾一个开关就花屏"**。下面这张表只适用于**手写（顶点属性）**这条路。

**Attribute Location 的计算**（这是手写路线的核心难点）：如果没有手动启用任何内置 Channel，
Stream 0 的位置是——

| Renderer Define | Stream 0 的 Location |
|---|---|
| `PARTICLE_INSTANCE` | 8 |
| `PARTICLE_MODEL_INSTANCE` | 9 |
| `TRAIL_INSTANCE` | 3 |
| `ARA_TRAIL_INSTANCE` / `ARA_TRAIL_TUBE_INSTANCE` | 3 |
| `BEAM_INSTANCE` | 6 |

手动启用的每一个"可上传"内置 Channel 都会**占用一个 Attribute Location**：

```text
customLocation = rendererBaseLocation
               + enabledUploadableBuiltinChannelCount
               + streamIndex
```

内置 Channel 按支持表里的注册顺序排列。**这意味着：在 Inspector 里多勾一个开关，就可能把所有 Custom Stream
的 Location 往后推一位**——所以发射器设置和 GLSL 必须一起维护。这就是"为什么我加了个勾就花屏了"的答案。
（Beam Direction/Length 是派生值，不占 Attribute。）

对应的顶点着色器骨架（假设没启用内置 Channel，读 Stream 0）：

```glsl
#version 330 core
#moj_import <photon:particle.glsl>

#if defined(PARTICLE_INSTANCE)
layout(location = 8) in vec4 iCustom0;
#define HAS_CUSTOM0
#elif defined(PARTICLE_MODEL_INSTANCE)
layout(location = 9) in vec4 iCustom0;
#define HAS_CUSTOM0
#elif defined(TRAIL_INSTANCE) || defined(ARA_TRAIL_INSTANCE) \
   || defined(ARA_TRAIL_TUBE_INSTANCE)
layout(location = 3) in vec4 iCustom0;
#define HAS_CUSTOM0
#elif defined(BEAM_INSTANCE)
layout(location = 6) in vec4 iCustom0;
#define HAS_CUSTOM0
#endif

out vec4 custom0;

void main() {
    ParticleData particle = getParticleData();
    // ...计算 gl_Position 与其它 varying...
#ifdef HAS_CUSTOM0
    custom0 = iCustom0;
#else
    custom0 = vec4(0.0);        // 材质预览与非实例化 CPU 路径
#endif
}
```

片元侧用一个同名 varying 接过去就行。**GLSL 声明了 Stream，Emitter 也必须真的创建了这个 Stream 并启用
Additional GPU Data**，否则你读到的永远是 0。

**运行时覆盖（Java）**：四种 Emitter Runtime 都暴露 `customData`，可以在不改共享资产的前提下覆盖单条 Stream/Channel：

```java
ParticleEmitter sparks = (ParticleEmitter) runtime.findObject("sparks");

// Stream 0、Channel 0（即 x / r）。RuntimeValue 接受 authored function 类型。
sparks.runtime().customData.slot(0, 0).set(new Constant(0.85f));

// 恢复编辑器里写的值
sparks.runtime().customData.slot(0, 0).clear();
```

Trail、Beam、AraTrail 同样用 `runtime().customData.slot(stream, channel)`；
Color Stream 暴露 Color Binding，Scalar/Vector Stream 暴露对应分量的 Binding。

> **和 Timeline 抢同一个槽**：Timeline Animation 与 Java 写同一个 `RuntimeValue` 槽时，
> **最后一次写入生效**。Timeline Clip 只要每帧都在驱动该槽，Java 的一次性 `set()` 看起来就会被忽略。
> 解法：换一条 Stream、在 Timeline 求值之后更新，或者让**只有一方**拥有这个值。

**性能**：Shader Graph 只上传"编译后的 Pass 真的用到"的 Channel；只有 Graph 读了 Custom Data 才会创建
Custom Stream Buffer。旧式手写 Shader 依赖手动 Toggle，并上传你声明的 Attribute。
实践建议：**让大量粒子共享少量紧凑的 Stream**，不要为了"每个粒子一个数值"去创建大量无法合批的独立材质——
后者会把 Draw Call 打散，比多传几个 float 贵得多。

#### 8.9.10 案例：随生命期渐变的发光 + 自定义槽位控制强度

**目标**：粒子从出生到消亡，颜色从亮橙渐变到暗红，并且**亮度上限由 Java 按当前元素类型动态给**——
火元素 1.0，其他元素 0.4。要求 GPU 实例化路径（大量粒子）下也成立。

**数据设计**（先想清楚"哪个量从哪儿来"）：

| 量 | 来源 | 为什么 |
|---|---|---|
| 生命期进度 `t` | 内置 Channel `t`（归一化 Age） | 每帧自动更新，不用 Java 管 |
| 基础颜色曲线 | 材质里的 Gradient（`SamplerGradient`） | 美术在材质面板里调，改色不用改代码 |
| 亮度上限 | **Custom Data Stream 0 的 x** | 由 Java 每帧按元素类型覆盖 |

**路线 A：Shader Graph（推荐）**

```text
Additional Data (t) ─┬─> Gradient(age) ─> Base Color
                     └─> Emission 乘子
Custom Data (Index 0) ─> Split.x ──> Multiply ──> Emission
Base Color.rgb ─────────────────────> Add ──────> Emission
```

1. Emitter Inspector 启用 **Additional GPU Data Setting**，加 **1 条 Vector Stream**（Stream 0），
   Channel 0（x）用一个常量 Function，默认 `1.0`；
2. Graph 里 `Additional Data` 选 `t`，接进 Gradient 得到基础色；
3. `Custom Data` 节点 Index 设 0，`Split` 取 `x`，乘到 Emission 上；
4. **验证顺序**：先在 GPU 实例化粒子上看（节点预览永远是 0，看了会误判）。

**路线 B：手写 Custom Shader Material**

顶点着色器读 Stream 0 的 Attribute 并转成 varying（按 §8.9.9 的 Location 公式。
这里假设**没有**启用内置 Channel，而且粒子走 `PARTICLE_INSTANCE`，所以 Location = 8）：

```glsl
#version 330 core
#moj_import <photon:particle.glsl>

#if defined(PARTICLE_INSTANCE)
layout(location = 8) in vec4 iCustom0;
#else
const vec4 iCustom0 = vec4(0.0);        // 其它变体 / 预览：读不到就是 0
#endif

out vec4 custom0;

void main() {
    ParticleData data = getParticleData();
    gl_Position = ProjMat * ModelViewMat * vec4(data.Position, 1.0);
    custom0 = iCustom0;
}
```

片元着色器用**材质自带**的 Curve/Gradient 做生命期渐变，再乘上强度上限：

```glsl
#version 150
#moj_import <photon:particle_utils.glsl>

uniform sampler2D SamplerGradient;      // 材质面板里编辑，不是 PNG
uniform float DiscardThreshold;

in float age01;                          // 由顶点着色器传下来的归一化生命期
in vec4  custom0;                        // Stream 0：x = Java 给的强度上限
in vec4  vertexColor;

out vec4 fragColor;

void main() {
    vec4 base   = getGradientValue(SamplerGradient, 0, age01);
    float limit = max(custom0.x, 0.0);
    vec4 color  = base * vertexColor;
    if (color.a < DiscardThreshold) {
        discard;
    }
    // Emission 用 HDR：亮度超过 1 才会被 bloom 抓到
    fragColor = vec4(color.rgb * limit, color.a);
}
```

Java 侧每帧（或元素类型变化时）覆盖：

```java
ParticleEmitter emitter = (ParticleEmitter) runtime.findObject("flame");
float limit = (element == Element.FIRE) ? 1.0f : 0.4f;
emitter.runtime().customData.slot(0, 0).set(new Constant(limit));
```

**为什么把"强度上限"放在 Custom Data 而不是写死在 shader 里**：同一份材质可以被火、冰、雷共用，
差别只在运行时那一个 float。**一个材质 = 一次合批**，这比"每种元素做一份材质"要划算得多。

**验收清单**：

- 元素切成火 / 冰，发光强度应立刻变化，**不需要重新加载资源**；
- 把粒子数量拉到几千（走 GPU 实例化），强度仍然正确——如果只有 CPU 路径对，说明 Attribute 没接上；
- 在节点预览 / 材质预览里看到的是 0（预期），**不要**据此判断接错了；
- Timeline 里若给 Stream 0 的 x 打了关键帧，Java 的 `set` 会被覆盖——确认两者只有一个在写；
- 打开 HDR/Bloom 检查亮度上限 1.0 与 0.4 的差异是否明显（不明显就把上限提到 1.5 以上再试）。

#### 8.9.11 这一节的坑与自检

按"从资源到画面"的顺序自查，基本能覆盖九成的失败：

| 现象 | 先检查 |
|---|---|
| 材质预览红色报错 | JSON 是否合法、Shader ID 是否带错前缀、include 路径是否存在；**先修错误再重载** |
| 画面全黑 | 用常量颜色替掉 Base Color；能显示说明算法问题，不能显示说明管线/混合问题 |
| 参数在 Inspector 里找不到 | 名字是否以 `Sampler` / `U_` 开头（保留前缀会被隐藏）；类型是否可反射（int/矩阵/超宽 count 不可） |
| 参数改了没反应 | 参数是不是只写在 Graph 内部常量上（必须暴露成 Graph Variable）；改完有没有保存 + Reload |
| GPU 数据恒为 0 | 是否在 CPU 预览路径；Additional GPU Data 有没有启用；Stream Index 是否越界 |
| 手写材质读到 0 | 是否误用了 `photon_custom_data()`；Attribute Location 是否因为多勾了一个内置 Channel 而错位 |
| 换渲染器就花屏 | 是否绕过 `getParticleData()` 自己声明了 Attribute（每个 Define 的布局都要照顾到） |
| 装光影后黑屏 | Scene/Depth Buffer 在 Iris 下可能不可用，必须写"没有 Buffer 时的安全输出" |
| 改完还是旧画面 | 保存 → Reload Shader → 清 Photon FX Cache；改资源包再执行一次 Minecraft 资源重载 |

一句话收尾：**先用 Graph 把效果做出来，只有当 Graph 真的表达不了时才回到手写 Shader；
而无论走哪条路，"每粒子一个数值"都应该走 Custom Data，而不是给每个粒子一份材质。**

## 9. Photon2 Java API 与运行时注入

### 9.1 两条路线：内置 Executor 与自定义 Executor

在游戏里播放一个 FX，只有两种做法：

1. **用内置 Executor**：`BlockEffectExecutor`（绑方块）或 `EntityEffectExecutor`（绑实体）。
   它们负责"跟谁、跟哪儿、什么时候销毁"，你只管配置 offset/rotation/scale/delay；
2. **自己实现 `IEffectExecutor`**：当锚点不是方块也不是实体（骨骼、屏幕坐标、自算轨迹）时走这条。
   它只有三个必须回答的问题：`getLevel()` 给谁、每 tick 干什么、每帧干什么。

无论哪条路，最终都会落到 `FXRuntime`：`fx.createRuntime()` → `runtime.emit(executor)` →
引擎每 tick / 每帧回调 → `runtime.destroy(force)`。

### 9.2 加载：FXHelper

`com.lowdragmc.photon.client.fx.FXHelper`（客户端类）：

| 方法 | 语义 |
|---|---|
| `getFX(Identifier)` | 读 `assets/<ns>/fx/<path>.fx` 并缓存；**失败返回 `null`** |
| `getFX(Identifier, boolean useCache)` | `useCache=false` 时绕过缓存直接重读 |
| `listAllFX()` | 列出所有可加载 id（模组 jar + 资源包 + 已挂载 `.fxpack`），结果缓存 |
| `clearCache()` | 清空定义缓存与 id 列表缓存；返回清掉的条目数 |

id 的写法是 `命名空间:路径`，路径**不带** `fx/` 前缀和 `.fx` 后缀：
`minegenshin:character/test/aura_body` → `assets/minegenshin/fx/character/test/aura_body.fx`。
`getFX` 返回 `null` 是"正常结果"而不是异常——资源没做出来时整条链路应当静默空转
（本仓库的 `TestCharacterFx` 就是这么处理的，见 §10）。

### 9.3 内置 Executor：BlockEffectExecutor 与 EntityEffectExecutor

两者都继承 `FXEffectExecutor`（`client/fx/FXEffectExecutor.java`），共用一份配置面与一套去重/回收逻辑。

**`BlockEffectExecutor`**（`client/fx/BlockEffectExecutor.java`）：

- 绑在方块坐标上：Root 位置 = 方块坐标 `+ (0.5, 0.5, 0.5)`，再叠加配置的 offset；
- 有静态缓存 `CACHE: Map<BlockPos, List<BlockEffectExecutor>>`；
- 每 tick 检查锚点：区块没加载、方块种类变了、或（`checkState=true` 时）**精确的 BlockState 变了**
  → `runtime.destroy(forcedDeath)` 并立刻从缓存退休（`retire`）；
- 反过来，运行结束的实例也会自己从缓存里摘掉，不会一直挂到下一次同位置启动。

**`EntityEffectExecutor`**（`client/fx/EntityEffectExecutor.java`）：

- 绑在实体上：Root 每帧跟 `entity.getEyePosition(partialTicks)`（眼睛位置）走，加 offset；
- 构造时选 `AutoRotate`：`NONE`（只用配置的 rotation）、`FORWARD`（实体前向）、
  `LOOK`（视线方向）、`XROT`（按可视身体朝向绕 Y，注意源码里还带了 `-90 - visualRotationY` 的补偿）；
- 每 tick 检查 `entity.isAlive()`：死了就 `destroy(forcedDeath)` + 立刻退休；
- 缓存 `CACHE: Map<Entity, List<EntityEffectExecutor>>`。

**去重规则（`FXEffectExecutor.shouldSkipStart`）**：当 `allowMulti = false`（默认）时，
如果同一锚点上已经有一个**同一个 FX 实例或同一个 fx location** 的执行器还在跑，本次 `start()` 直接跳过。
所以"我自己管理生命周期"的代码要显式 `setAllowMulti(true)`，否则会被静默跳过——
本仓库的 `TestCharacterFx.tickBody` 就是先自己判重、再开 `allowMulti`（`TestCharacterFx.java:213-245`）。

**回收与通知**：`FXEffectExecutor` 提供 `setOnFinished(Consumer<FXRuntime>)`，在
"自然播完 / 被销毁 / 被粒子引擎丢弃"时**恰好触发一次**；
`runtimeEnded()` 的判据是 `runtime.isFinished() || !runtime.isValid()`。

### 9.4 配置面：offset / rotation / scale / delay / forcedDeath / allowMulti

`IFXEffectExecutor`（`client/fx/IFXEffectExecutor.java`）给出的配置入口：

| 方法 | 单位 / 语义 |
|---|---|
| `setOffset(double x, double y, double z)` / `setOffset(Vector3f)` | 相对锚点的位移（格） |
| `setRotation(double x, double y, double z)` | **角度制**欧拉角，内部转成四元数 `rotationXYZ(x, y, z)` |
| `setRotation(Quaternionf)` | 直接给四元数（弧度语义），不做任何换算 |
| `setScale(double x, double y, double z)` / `setScale(Vector3f)` | Root 缩放 |
| `setDelay(int)` | 启动延迟（tick），在 `emit` 之后设置到每个对象上 |
| `setForcedDeath(boolean)` | 锚点消失时是否**立即**清掉可见残留（默认等粒子自然消亡） |
| `setAllowMulti(boolean)` | 同一锚点是否允许并存多个相同 FX |

> 这两套 `setRotation` 混用会得到约 57.3 倍的旋转错误：角度制重载内部做 `toRadians`，
> 四元数重载不做。本仓库 `AnchorPose` 的类注释专门写下了这条坑。

### 9.5 自定义 Executor：`IEffectExecutor` 的三个回调

`IEffectExecutor`（`client/fx/IEffectExecutor.java`）的完整接口只有这些：

```java
public interface IEffectExecutor {
    Level getLevel();                                            // 必须：这个 FX 属于哪个 Level
    default RandomSource getRandomSource() { return getLevel().getRandom(); }

    default void updateFXObjectTick(IFXObject fxObject) {}       // 每 tick、每个对象一次（低频逻辑）
    default void updateFXObjectFrame(IFXObject fxObject, float partialTicks) {} // 每帧、每个对象一次（平滑跟随）

    default void onTimelineSignal(String channel, String name, CompoundTag data, double time) {}
    default PostEffectStack postEffectSink() { return PostEffectStack.GLOBAL; }
}
```

写自定义执行器的三条经验（都能在本仓库 `client/fx/` 里找到对照实现）：

1. **只动 Root**：`updateFXObjectFrame` 里判 `object == runtime.getRoot()` 再写位姿；
   动子对象会和编辑器里编排好的层级互相打架（`FxAnchor.Executor.updateFXObjectFrame`）。
2. **tick 推进、frame 插值**，两个回调各管一半：tick 里按整刻推进状态，frame 里按
   `partialTicks` 算出该帧的实际位姿。两边都写位置会得到"一帧跳、一帧插值"的抖动
   （`FixedPointExecutor` 的类注释把这条写死了）。
3. **给随机源一个固定种子**：`RandomSource.create(20260928L)` 之类的稳定种子能让使用随机函数的
   资产在重播时可复现（`FxAnchor.java:51`、`FixedPointExecutor.java`）。

### 9.6 运行时数据注入：RuntimeValue 与 ParticleRuntime

**`RuntimeValue<T>`**（`client/gameobject/RuntimeValue.java`）是实例侧的一个命名槽：

| 方法 | 语义 |
|---|---|
| `get()` | 有覆盖就返回覆盖值，否则返回资产里的配置值 |
| `authored()` | 永远返回资产配置值（编辑器里的实时检查用） |
| `set(T)` | 写入覆盖值 |
| `setRaw(Object)` | 泛型采样路径用；普通集成代码不要用 |
| `clear()` | 删除覆盖，回到资产配置 |
| `isOverridden()` | 是否有非 null 覆盖 |

**`ParticleRuntime`**（`client/gameobject/emitter/particle/ParticleRuntime.java`）是粒子发射器实例的槽汇总，
分三类：

- 顶层 `ParticleConfig` 参数：`startColor`、`startDelay`、`startLifetime`、`startSpeed`、`startSize`、
  `startRotation`、`duration`、`prewarm`、`maxParticles`、`looping`、`parallelUpdate`；
- 每个模块的 Runtime：`emission`、`shape`、`physics`、`sizeOverLifetime`、`rotationOverLifetime`、
  `forceOverLifetime`、`externalForces`、`lights`、`colorOverLifetime`、`velocityOverLifetime`、
  `inheritVelocity`、`lifetimeByEmitterSpeed`、`colorBySpeed`、`sizeBySpeed`、`rotationBySpeed`、
  `noise`、`uvAnimation`、`trails`、`subEmitters`；
- 渲染 override 与自定义 GPU 数据：`renderer`（`layer` / `orderInLayer` / `vertexSortingMode` /
  `compositeMode` / `writeCustomMask` / `maskGroup` / `maskAlphaCutoff` / `materials` / `cull`）、
  `customData`（`slot(stream, channel)`）。

注入的正确姿势（修改只影响这一次播放，不改写 `.fx` 资产）：

```java
if (runtime.findObject("aura_core") instanceof ParticleEmitter emitter) {
    var v = emitter.runtime();                 // ParticleRuntime：槽是直接字段访问
    v.startSpeed.set(new Constant(0.2f));      // NumberFunction 常量
    v.maxParticles.set(256);
    v.physics.enable.set(true);
    v.physics.gravity.set(new Constant(0.03f));
    v.emission.emissionRate.set(new Constant(24f));
    v.renderer.layer.set(RendererSetting.Layer.Translucent);
    v.renderer.orderInLayer.set(20);
    v.customData.slot(0, 0).set(new Constant(0.85f));   // Stream 0 的第 0 个通道
    // 还原：v.physics.enable.clear(); … clearRenderOverride() 清掉全部渲染覆盖
}
```

两条硬约束：

1. **先查对象、再判类型、最后写槽**；对象名由编辑器里设置，`findObject` 是唯一入口。
2. **`set(null)` 不是"没有覆盖"**：null 只表示"没写"，要回到资产值必须 `clear()`。

渲染覆盖还有一个"批量友好"性质：只有当有效值真的不同（`effectiveEquals` / `effectiveHashCode`）时，
两个发射器才会被拆成不同的 pass；不改就还能合批（`RendererSetting.Runtime` 的注释）。

### 9.7 网络：服务端怎么触发特效

Photon 自带 4 个客户端 payload，注册在 `PhotonNetworking.registerPayloads`
（`playToClient`）：

| payload | 作用 | 关键字段 |
|---|---|---|
| `BlockEffectCommand` | 在方块上启动 FX | `location`、`pos`、`checkState` + 公共字段 |
| `EntityEffectCommand` | 在实体上启动 FX | `location`、实体 id 列表、`autoRotate` + 公共字段 |
| `RemoveBlockEffectCommand` | 移除方块 FX | `pos`、可选 `location`、`force` |
| `RemoveEntityEffectCommand` | 移除实体 FX | 实体 id 列表、可选 `location`、`force` |

公共字段（`command/EffectCommand.java`）：`location`、`offset`、`rotation`、`scale`、`delay`、
`forcedDeath`、`allowMulti`。实体命令的 `autoRotate` 取值就是 §9.3 的四个枚举。

服务端命令（`ServerCommands`，权限 `LEVEL_GAMEMASTERS`）：

```
/photon fx <id> block <x y z> [offset] [rotation] [scale] [delay] [forcedDeath] [allowMulti] [checkState]
/photon fx <id> entity <selector> [offset] [rotation] [scale] [delay] [forcedDeath] [allowMulti] [autoRotate]
/photon fx remove block <x y z> [force] [location]
/photon fx remove entity <selector> [force] [location]
```

分发范围（源码里的实现，直接决定"谁看得见"）：

- 方块命令：`PacketDistributor.sendToPlayersTrackingChunk(level, ChunkPos.containing(pos), command)`
  —— **只给追踪该区块的玩家**；
- 实体命令：`PacketDistributor.sendToAllPlayers(command)` —— **全服广播**（本项目的用法要自己权衡）；
- 接收端：两个 `execute` 都先 `if (LDLib2.isClient())` 再进客户端分支，
  客户端分支里才去 `FXHelper.getFX`、找实体、建执行器、`start()`。

在模组里自己触发时，不必自己造 payload：直接用这两条命令对应的 payload 类型发包即可；
只有当"触发条件/参数不在现有 payload 表达范围"时才需要自定义网络包，且**服务端侧不要引用 Photon 的客户端类**。

### 9.8 版本差异与迁移（本机 26.2.2.3 vs 官方文档站 2.2.x 口径）

| 主题 | 官方文档站（2.2.x）| 本机 26.2.2.3（源码核对）|
|---|---|---|
| 资源 id 类型 | `ResourceLocation.parse(...)` | `net.minecraft.resources.Identifier.fromNamespaceAndPath(...)`（`FXHelper` / `PhotonRegistries`） |
| 启动拼写 | `emmit(...)` 为兼容保留、已弃用 | 一致：`FXRuntime.emmit` 标 `@Deprecated` 并转发到 `emit` |
| 材质渲染缝 | 1.21 的 `ShaderInstance begin/end` | **已不存在**：`IMaterial.getRenderType(...)` + `BlendMode.toBlendFunction()` 直接产出管线（`IMaterial` 类注释） |
| 批处理 | 1.21 的 `PhotonFXRenderPass` 是批处理单元 | **`PhotonFXRenderPass` 现为 M0 桩**，批处理改由 `PhotonWorldRenderState` 的渲染状态批次承担 |
| 注册表注解 | `@LDLRegisterClient(manual = true)` | 该参数已移除，注册表改为无条件加载并自行扫描（`PhotonRegistries` 注释） |
| 项目格式 | `.fxproj` + DataFixer | 一致：`FXProject.VERSION = 5`，`.fx` 里写 `version` 走 `PhotonFXProjectDataFixer` |

> 未确认：本机 Photon 的**延迟层与 Iris 的逐 pass 交互细节**（`IrisTargetResolver` 的选择算法、
> `IrisCompositeMode` 对具体 pack 的判定）只读了公开方法签名与命令入口，没有逐行核对。

### 9.9 最小可运行示例（完整链路）

```java
package com.example.client;

import com.lowdragmc.photon.client.fx.EntityEffectExecutor;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.Constant;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

public final class AuraExample {
    private static final Identifier AURA =
            Identifier.fromNamespaceAndPath("minegenshin", "character/test/aura_body");

    public static FXRuntime play(Player player) {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;

        FX fx = FXHelper.getFX(AURA);
        if (fx == null) return null;                 // 资源还没导出：静默空转

        var executor = new EntityEffectExecutor(fx, level, player,
                EntityEffectExecutor.AutoRotate.NONE);
        executor.setOffset(0.0, 0.0, 0.0);
        executor.setForcedDeath(true);               // 锚点没了就收干净
        executor.setAllowMulti(true);                // 去重自己管
        executor.start();                            // 内部 createRuntime + emit

        FXRuntime runtime = executor.getRuntime();
        if (runtime != null && runtime.findObject("aura_core") instanceof ParticleEmitter emitter) {
            emitter.runtime().emission.emissionRate.set(new Constant(24f));
        }
        return runtime;
    }

    private AuraExample() {}
}
```

缓存的 Runtime 每次 tick 都要用 `isValid()` 复核；`isFinished()` 只说明"没有未来内容"，
循环发射器**永远不会** `isFinished()`。两者在 §11 的排错表里各占一行。

### 9.10 一次完整接入的分步流程（照着做就能跑起来）

前面各节是"零件"，这里把它们装成一台机器。目标：**角色进入原神模式时，眼睛位置常驻一圈光环**。

| 步 | 做什么 | 做完怎么知道对了 |
|---|---|---|
| 1 | 在编辑器里做出效果，把需要代码控制的对象命名（例如 `aura_core`、`aura_pivot`） | 在 FX Hierarchy 里能看到这些名字；名字唯一 |
| 2 | 导出成 `.fx`（有自定义材质/图/网格就导出 `.fxpack`） | 文件出现在 `/ldlib2/assets/<ns>/fx/...`；id 与路径一一对应 |
| 3 | 把资源放进仓库：`assets/<ns>/fx/...` 或 `fxpacks/` | 打包后能在 jar 里看到这两个目录 |
| 4 | 写"加载"：`FXHelper.getFX(id)`，**允许返回 null** | 资源缺失时游戏正常跑、日志不刷异常 |
| 5 | 选锚点：方块/实体 → 内置 Executor；骨骼/自算位置 → 自写 `IEffectExecutor` | 想清楚"这个特效跟着谁、什么时候该消失" |
| 6 | 接生命周期：每 tick 判断"想不想要" → 需要就 `start()`，不需要就 `destroy(false)`；缓存 Runtime 就查 `isValid()` | 切世界、`/photon_client clear_particles` 之后能自动恢复 |
| 7 | 需要动画的参数走 `RuntimeValue` 注入（颜色、强度、进度） | 改注入值立刻在画面上体现，且不改动 `.fx` 资产 |
| 8 | 多人可见的场景改走服务端 payload（§9.7），公共包只发通知 | 专用服务器启动无异常，附近玩家都能看到 |

**三个高频用法片段**（可以直接改改用）：

片段 A —— 加载 + 缓存 + 资源重载失效：

```java
private static FX cached;
private static Identifier cachedId;

static FX fx(Identifier id) {
    if (cached != null && id.equals(cachedId)) return cached;
    FX fx = FXHelper.getFX(id);                 // 失败返回 null，不抛异常
    if (fx != null) { cached = fx; cachedId = id; }
    return fx;                                  // 仍然可能是 null：调用方必须能空转
}

static void onResourceReload() {                // 资源包/数据包重载后
    cached = null;                              // 丢掉引用，下次自动重读
    // 注意：已经跑着的 Runtime 属于旧定义，等它自己播完或主动 destroy(false)
}
```

片段 B —— 一个能用的自定义执行器（骨架 + 每部分为什么）：

```java
public final class MyAnchorExecutor implements IEffectExecutor {
    private final Level level;
    private final Player player;      // 跟随目标
    private Vec3 origin;              // tick 级位置
    private int age;

    public MyAnchorExecutor(Level level, Player player) { this.level = level; this.player = player; }

    @Override public Level getLevel() { return level; }
    @Override public RandomSource getRandomSource() { return RandomSource.create(20260928L); } // 稳定种子

    @Override public void updateFXObjectTick(IFXObject object) {
        age++;                                        // 低频逻辑放 tick
        if (age > LIFETIME_TICKS) { /* 由拥有者 destroy(false) */ }
    }

    @Override public void updateFXObjectFrame(IFXObject object, float partialTicks) {
        if (!(object instanceof FXObject root)) return;           // 只动 Root，别碰子对象
        Vec3 pos = origin.add(forward.scale(speedPerTick * partialTicks));  // 刻内插值
        root.updatePos(pos); root.updateRotation(rotation); root.updateScale(scale);
    }
}
```

要点只有三条：**tick 推进状态、frame 做插值**（两处都写位置就抖动）；
**只动 Root**（子对象的位姿由资产编排）；**给随机源固定种子**（重播可复现）。

片段 C —— 把游戏状态喂给特效（颜色/强度/进度）：

```java
void pushState(FXRuntime runtime, float strength, int rgb) {
    if (runtime == null || !runtime.isValid()) return;             // 先查存活
    if (runtime.findObject("aura_core") instanceof ParticleEmitter emitter) {
        var v = emitter.runtime();
        v.startColor.set(new Constant(rgb));                       // NumberFunction 常量
        v.maxParticles.set((int) (64 + 192 * strength));
        v.customData.slot(0, 0).set(new Constant(strength));       // Stream 0 / Channel 0 → 给着色器
        // 要在下次改回资产值时用 clear()，不要 set(null)
    }
}
```

## 10. 项目实战：把渲染接到特效上

### 10.1 现有接线总览

本仓库已经把四种典型触发方式做成了可直接抄的范例，全部在
`src/main/java/com/linweiyun/genshin/client/fx/`（`TestCharacterFx.java` 是入口）：

| 范例 | 触发时机 | 锚点 | 用到的 API | 关键文件 |
|---|---|---|---|---|
| 1 | 常驻（原神模式 + test 出战） | 角色眼睛 | 内置 `EntityEffectExecutor` | `TestCharacterFx.java:213` |
| 2 | 常驻 | 手上武器的骨骼 | `FxAnchor` + 骨骼缓存 | `TestCharacterFx.java:248`、`FxAnchor.java` |
| 3 | 普攻动作期间 | 武器**尖**（骨骼 + 轴向偏移） | `FxAnchor` + `ActionStateMachine` | `TestCharacterFx.java:263`、`:337` |
| 4 | 技能释放后 | 世界定点，朝前飞 | 自写 `IEffectExecutor` | `FixedPointExecutor.java`、`TestCharacterFx.java:283` |

四个特效 id 与资源路径（`TestCharacterFx.java:88-105` 的常量 + 类注释）：

```
minegenshin:character/test/aura_body        → assets/minegenshin/fx/character/test/aura_body.fx
minegenshin:character/test/aura_weapon      → assets/minegenshin/fx/character/test/aura_weapon.fx
minegenshin:character/test/attack_tip       → assets/minegenshin/fx/character/test/attack_tip.fx
minegenshin:character/test/skill_projectile → assets/minegenshin/fx/character/test/skill_projectile.fx
```

四个 id 对应的 `.fx` 还没做出来也不影响游戏：`FXHelper.getFX` 返回 `null`，整条链路静默空转。

### 10.2 常驻跟随：FxAnchor 的骨架

`FxAnchor`（`client/fx/FxAnchor.java`）是一个**每帧推 Root 位姿**的通用锚点，生命周期只有三步：

```
每客户端 tick：
    anchor.setWanted(条件);     // true = 我想要它亮着
    anchor.tick();              // 内部按需创建 / 销毁 Runtime
```

`tick()` 的判断顺序（`FxAnchor.java:79-92`）：

1. `fx == null`（资源没导出）→ 直接返回，什么都不做；
2. `wanted == false` → `destroy(false)`，残留自然消散；
3. `runtime == null`、`!runtime.isValid()` 或 `runtime.isFinished()` → 重新 `createRuntime()` + `emit(...)`。

**`isValid()` 这一条是缓存 Runtime 的唯一正确存活检查**：切世界、`/photon_client clear_particles`、
其它模组清空粒子之后，引擎会悄悄丢弃粒子，Runtime 自己不会知道；`isValid()` 用
"引擎代际 + Root 心跳"两个信号把它捕获（`FXRuntime.isValid` 的注释写得很细，包括"暂停游戏不算失效"）。

`FxAnchor` 自己实现 `IEffectExecutor`，两个回调分工明确（`FxAnchor.java:121-132`）：

```java
@Override public void updateFXObjectTick(IFXObject object) { /* 低频逻辑：这里什么都不做 */ }

@Override public void updateFXObjectFrame(IFXObject object, float partialTicks) {
    if (runtime == null || object != runtime.getRoot()) return;   // 只动 Root
    AnchorPose pose = poseProvider.pose(player, partialTicks);
    if (pose == null) return;                                     // 本帧不动 = 保持上一帧
    object.updatePos(pose.position());
    object.updateRotation(pose.rotation());
    object.updateScale(pose.scale());
}
```

位姿由 `PoseProvider` 提供（`@FunctionalInterface`，`FxAnchor.java:40`），返回 `null` 的语义是
"这一帧不动"。随机源用固定种子 `RandomSource.create(20260928L)`（`FxAnchor.java:51`），
让带随机函数的资产在重播时可复现。

> **常驻特效记得在编辑器里开 Looping**。没开也不会错——播完会被 `isFinished()` 分支重建——
> 但会多一次无谓的重建（这个取舍写在 `TestCharacterFx` 的类注释里）。

### 10.3 骨骼 → 世界坐标：这次渲染的第几帧？

骨骼位姿**不是实体状态**，它是"动画采样 + 层级变换累乘"的产物，只在渲染那一刻存在。
本项目用 GeckoLib 的 `GeoRenderLayer` 把它抓出来：

- `WeaponAnchorGeoLayer`（`client/render/character/WeaponAnchorGeoLayer.java`）挂在角色的渲染器上
  （`CharacterRenderDispatcher.java:348`）；
- 提取阶段用 `DataTicket` 把"这是哪个玩家"带进渲染阶段（`:63-68`）；
- 渲染阶段用 `addPerBoneRender` 拿到"已经摆到该骨骼位姿"的 `PoseStack`，就地取矩阵（`:70-84`）；
- 取位姿时做**相机空间换算**（`:95-110`）：

```java
Matrix4f bonePose = new Matrix4f(info.poseStack().last().pose());
Vector3f local   = bonePose.getTranslation(new Vector3f());        // 相机空间的平移
Quaternionf rot  = bonePose.getUnnormalizedRotation(new Quaternionf());
Vec3 camera      = info.cameraState().pos;
Vec3 world       = new Vec3(camera.x + local.x, camera.y + local.y, camera.z + local.z);
```

这就是"实体渲染的 PoseStack 原点是相机"那条结论的直接应用。写入前还有一道
`SANITY_DISTANCE = 8.0` 的检查：结果离玩家太远就整帧丢弃——**宁可让特效停在上一帧，
也不要它飞到天边**。

抓到的结果进 `WeaponAnchorCache`（`client/render/character/WeaponAnchorCache.java`）：

- 键是玩家 UUID，值是 `Entry(position, rotation, frame)`；
- 时间戳用**渲染帧号**（`CharacterRenderDispatcher.renderFrame()`，在 `RenderFrameEvent.Pre` 自增，
  `CharacterRenderDispatcher.java:114-118`），不用实体 tick；
- `fresh()` 在超过 `MAX_AGE_FRAMES = 3` 帧没更新时返回 `null`：角色在第一人称、离屏、
  或被别的模组挡住时根本不会渲染，这时调用方保持上一帧位姿（`WeaponAnchorCache.java:33`、`:58-64`）；
- 读写都发生在渲染线程，所以用普通 `HashMap` 就够，不需要并发容器（类注释）。

`AnchorPose`（`client/fx/AnchorPose.java`）是"传给 Photon Root 的位姿"：

```java
public record AnchorPose(Vector3f position, Quaternionf rotation, Vector3f scale) {
    public static Quaternionf yaw(float yawDegrees) {
        return new Quaternionf().rotationY((float) Math.toRadians(yawDegrees));
    }

    public static Quaternionf look(float yawDegrees, float pitchDegrees) {
        return new Quaternionf().rotationYXZ(
                (float) Math.toRadians(-yawDegrees),
                (float) Math.toRadians(pitchDegrees), 0f);
    }
}
```

**角度制还是四元数**在这里也是坑：`AnchorPose` 收的是四元数（弧度语义），
而 `IFXEffectExecutor#setRotation(double,double,double)` 是角度制（§9.4）。两套入口别混。

### 10.4 按动作状态触发

范例 3 的判据是"现在是不是普攻"（`TestCharacterFx.inAttackAnimation`，`TestCharacterFx.java:202-211`）：

```java
String state = ActionStateMachine.currentState;
return state != null
        && (TestAnimations.SPECIAL_ANIMS.contains(state) || state.contains("attack"));
```

两条经验值得抄：

1. **精确名单优先**（`SPECIAL_ANIMS`），再加一层宽松兜底；
2. 判据只读客户端状态机，不碰服务端数据——特效是纯表现层的判断。

想让特效在动作开始的那一刻**单发**（而不是整段常驻），正确的位置是客户端的动作钩子/状态机
返回值处，而不是 `FxAnchor` 的 `PoseProvider`（那个是每帧调的）。

### 10.5 定点生成 + 朝前飞：自写 Executor 的完整例子

`FixedPointExecutor`（`client/fx/FixedPointExecutor.java`）演示了"位置由代码推进"的一类特效：

| 回调 | 做什么 |
|---|---|
| `updateFXObjectTick` | `origin += forward × speed`（整刻推进）；到寿命就在 tick 里 `destroy(false)` |
| `updateFXObjectFrame` | `pos = origin + forward × speed × partialTicks`（刻内插值），写 Root |

```java
this.forward  = Vec3.directionFromRotation(0f, yawDegrees);   // Minecraft 偏航角：0 = +Z，90 = -X
this.rotation = AnchorPose.yaw(yawDegrees);
```

使用侧（`TestCharacterFx.onSkillCast`，`:283-306`）：

```java
var executor = new FixedPointExecutor(level, origin, player.getYRot(), SKILL_FORWARD_SPEED)
        .lifetime(SKILL_LIFETIME_TICKS);
executor.start(fx);
```

`forwardSpeed = 0` 是合法用法：位移完全交给资产的 "Velocity over Lifetime" 模块——
两种做法都成立，取决于特效作者的习惯（类注释）。

### 10.6 从公共包触发：为什么要有 SkillCastHooks

招式本体跑在**公共包**（服务端也会加载），而 Photon 是**客户端**库。
公共包直接引用 Photon / `Minecraft` 的类，专用服务器加载就会崩。

本项目的解法是 `SkillCastHooks`（`core/character/talent/SkillCastHooks.java`）：

- 公共侧只有一个纯接口 + 一张监听表，`fire(player, skillType)` 两端都会走到（`:55`）；
- 客户端在自己的初始化里 `register` 监听器（`TestCharacterFx.java:124`），
  专用服务器上**没有任何监听器，`fire` 就是一次空循环**；
- 监听器自己负责"只处理本地玩家"（`TestCharacterFx.onSkillCast` 里 `player != mc.player` 直接返回），
  别的玩家的特效由他们自己的客户端放。

同样的思路适用于任何"游戏逻辑想叫特效"的场景：**公共包只发通知，客户端自己决定怎么演。**

### 10.7 资源交付：从编辑器到 jar

1. 在游戏内 `/photon_editor` 里做好效果（单人世界）；
2. 导出：`File → Export → FX`（需要自己保证依赖资源已随仓库提供）或 `File → Export → FX Pack`
   （推荐——它会把 Material / Graph / Mesh / Texture / Shader 一起收集，见 §8.3）；
3. 把 pack 里的 `assets/` 内容拷进 `src/main/resources/assets/`（`TestCharacterFx` 类注释给的就是这条路），
   或者把 `.fxpack` 放进 `src/main/resources/fxpacks/`（`FXPacks.MOD_FXPACKS_DIR = "fxpacks"`，
   模组 jar 根目录下的 `fxpacks/` 会被自动挂载）；
4. 进游戏后如果没看到效果，先 `/photon_client clear_client_fx_cache` 再试；
5. 发布前跑一遍 §11.5 的清单。

### 10.8 六个范例的验收清单（怎么确认"真的做对了"）

每个范例都有自己的"看起来对了但其实错了"的坑，按这张表逐条过：

| 范例 | 验收动作 | 通过标准 |
|---|---|---|
| 常驻跟随（§10.2） | 切世界 → 回来；`/photon_client clear_particles` → 等一帧 | 光环自动重新出现，不刷异常 |
| 武器光效跟骨骼（§10.3） | 第一人称 / 第三人称 / 转身 / 把角色藏到方块后 | 特效跟着武器走；被遮挡时**停住不回退到镜头** |
| 按动作触发（§10.4） | 打出普攻、被打断、连续两段 | 只在动作期间亮；打断后立即消失；不会整段常驻 |
| 定点 + 朝前飞（§10.5） | 转向不同角度释放、连续快速释放两次 | 朝向与释放方向一致；两次互不干扰（`allowMulti` 与去重逻辑） |
| 公共包触发（§10.6） | 开专用服务器启动、玩家进服放技能 | 服务器无异常；每个玩家看到自己那份 |
| 服务端触发（§9.7） | 两个客户端同开，一个人放技能 | 双方都看得见；离得太远的玩家不被广播拖累 |

**共同的一条底线**：把 Photon 去掉或让资源缺失，游戏必须**照常跑**（`getFX` 返回 `null`、链路空转）。
这条在你的开发环境里很容易忘，因为在你自己机器上资源永远在。

## 11. 排错手册与性能

### 11.1 症状 → 可能原因 → 排查动作

| 症状 | 可能原因 | 排查动作 |
|---|---|---|
| 完全没有特效 | `.fx` 没导出 / 路径或命名空间写错 / 大小写不对 | `FXHelper.listAllFX()` 或 `/photon_client clear_client_fx_cache` 后重看日志；确认 id 与 `assets/<ns>/fx/<path>.fx` 一一对应 |
| 只有一个客户端能看到 | 特效是本地触发的，别的客户端没收到触发 | 走 §9.7 的 payload（方块命令只发给追踪该区块的玩家，实体命令是全服广播） |
| 特效"贴在镜头上"跟着你跑 | 用了世界坐标却忘了换算；或把 `PoseStack` 当世界坐标用 | 回看 §10.3 的 `相机世界坐标 + PoseStack 平移` 公式 |
| 位置对但一直抖动 | tick 与 frame 两个回调都写了位置 | 只在一处推进状态、另一处插值（§10.5） |
| 模型/特效朝反方向 | 角度制与四元数混用（差 57.3 倍），或偏航定义记错（0 = +Z） | 统一用一套入口；`AnchorPose.yaw/look` 与 `setRotation(double,double,double)` 各司其职 |
| 攻击特效只在动画一半亮 | 动作状态名与判据不匹配 | 用 `ActionStateMachine.currentState` 打印实际状态名，或参考 `TestAnimations.SPECIAL_ANIMS` |
| 打包后特效消失 | 资源没收进 jar（贴图 / Graph / Mesh 漏了） | 用 FX Pack 重新导出；或把 `assets/` 与 `fxpacks/` 一起提交，见 §10.7 |
| 切世界/清粒子后再也没出现 | 缓存了 Runtime 但没查 `isValid()` | 按 `FxAnchor.tick()` 的三段判断重建 |
| 一批特效播完后列表越积越多 | 自己维护的列表没清 | `isFinished() || !isValid()` 就摘掉（`FixedPointExecutor.isDone()`） |
| 装了光影包后 FX 被云/水覆盖、后处理不生效 | Photon 与 pack 的合成时机问题 | 先 `/photon_iris status`，再试 `/photon_iris mode after`；这是诊断开关，正常游戏保持 `auto` |
| 整个屏幕黑掉 | 延迟层/后处理链在错误的时机跑，或 FX 层没合成 | 关 `enable_custom_effects` 定位是不是后处理链；`/photonfx clear` 清掉测试效果 |

### 11.2 命令与开关速查（全部来自 `client/ClientCommands.java` 与 `ServerCommands.java`）

| 命令 | 用途 |
|---|---|
| `/photon_editor` | 打开编辑器（仅单人） |
| `/photon fx <id> block <x y z> [...]` | 在方块上播放 |
| `/photon fx <id> entity <selector> [...]` | 在实体上播放 |
| `/photon fx remove block <x y z> [force] [location]` | 移除方块上的 FX（可只删某个 id） |
| `/photon fx remove entity <selector> [force] [location]` | 移除实体上的 FX（可只删某个 id） |
| `/photon_client clear_particles` | 清空 Photon 粒子 + 执行器缓存（缓存的 Runtime 立刻失效） |
| `/photon_client clear_client_fx_cache` | 清空 FX 定义缓存与 id 列表缓存 |
| `/photon_client convert_shaders` | 转换 `ldlib2/assets` 下的 1.21 时代着色器格式（带备份） |
| `/photon_client convert` | 转换 `ldlib2/assets/photon/fx_old` 下的 Photon 1 特效 |
| `/photonfx list` / `test <effect> [weight]` / `clear` | 后处理效果的列出 / 逐帧测试 / 停止 |
| `/photon_iris status` / `dump` / `overlay on\|off` / `mode auto\|primary\|after\|scene\|off` | 光影包兼容诊断 |

### 11.3 客户端 / 服务端隔离的三条硬规矩

1. **公共包不引用 Photon 的客户端类**（`com.lowdragmc.photon.client.*`）、不引用 `Minecraft`。
   要触发就走"公共侧发通知、客户端侧监听"（`SkillCastHooks` 模式）或走 payload。
2. **所有特效状态都在客户端维护**：Runtime、骨骼缓存、锚点表都是客户端对象；
   服务端只负责"事件发生了"这件事。
3. **专用服务器上跑一遍**是唯一可靠的验证：没有监听器时 `fire` 空转、payload 的客户端分支不会执行。

### 11.4 性能：预算、批处理与测量

先记住三个数字口径：

- **draw call 由批次决定**：Photon 在提取阶段把绘制任务按
  `(renderType, mask, 拓扑)` 合批（`PhotonWorldRenderState` 的 `resolve`：相邻同 renderType、同 mask 的作业合并；
  注释明确"strip 与 fan 不能合并"）；实例化作业走 `InstancedJob` 单独通道。
  所以**同样的特效，材质切得越少、层/掩码越一致，draw call 越少**。
- **提取耗时可直接读**：`PhotonParticleGroup.averageExtractTimeUs()` 维护 60 帧的滚动采样
  （`PhotonParticleGroup.java`），这是"CPU 侧每帧花在 Photon 提取上的时间"。
- **后处理有显存预算**：`postfx_pool_budget_mb`（默认 256MB）限制池化渲染目标；
  超额时按"最久未使用"淘汰（`PhotonConfig` 注释）。

优化的顺序建议（从收益大、风险小开始）：

1. **先降粒子数上限**（`maxParticles`）与发射率，再看效果是否还成立；
2. **合并材质与层**：同一特效里尽量共用一个材质槽；`Layer`、`maskGroup`、`orderInLayer` 一致才能合批；
3. **开 GPU 实例化**（`useGPUInstance`）——前提是顶点数据同构；
4. **后处理**：全屏图（Render Graph / Fullscreen Graph）的 pass 数要克制；`enable_bloom` 是 HDR 操作，
   被推迟到主目标（RGBA8）之后就跑不了（`PhotonPostFX` 注释里明确"不在这里做 bloom"）；
5. **并行更新**：`parallelUpdate` 能把粒子更新下放工作线程，但**带碰撞的物理会自动禁用**
   （`ParticleEmitter` 内部判断）。

测量方式：客户端 F3 看帧时间与 draw call 数量级；用 `averageExtractTimeUs()` 对照
"Photon 提取"和"总帧时间"的比例，再决定是优化特效还是优化场景。

> 未确认：各类模块（Noise、Trail、Beam、AraTrail、后处理图）的**单模块开销量级**我没有逐项实测，
> 上面给的是可核对的结构性结论（合批条件、预算键、采样入口），不是实测数字。

### 11.5 三条硬规则

1. **资产缺失是正常状态**：`FXHelper.getFX` 可能返回 `null`，所有调用点必须能静默空转。
2. **缓存 Runtime 就必须查 `isValid()`**，并且知道 `isFinished()` 与它回答的是两个不同问题。
3. **坐标、角度、单位三件事每次都要问一遍**：相机空间还是世界空间？角度还是弧度？
   tick 还是秒？——本文里出现的每一个"位置乱飞/旋转反了/抖动"都能归到这三问上。

发布前自检（对照 §11.1 的表快速过一遍）：

- [ ] 所有 `.fx` 依赖资源都在仓库里（或已打包进 `.fxpack`），路径全小写、无空格；
- [ ] 特效在"切世界 → 回世界"之后能自动恢复；
- [ ] 多人环境：只有该看见的客户端在播；
- [ ] 专用服务器启动无异常（公共包没有引用客户端类）；
- [ ] 关掉 Photon 或资源缺失时，游戏不崩、不刷日志。

### 11.6 性能优化实战：从「卡」到「不卡」的完整过程

优化最容易犯的错是"凭感觉改参数"。下面这套流程的关键在于**每一步都先取证，再动手**。

#### 第 0 步：先分清"帧率低"和"卡顿"

- **帧率低**：F3 上帧时间一直高（比如恒在 30ms），画面持续发涩；
- **卡顿（spike）**：帧时间平时正常，每隔几秒突然跳一次，感觉像抽帧。

两者的病因完全不同：帧率低多半是**每帧都在做太多事**（粒子太多、pass 太多）；
spike 往往是**某个事件在那一帧做了重活**（大量粒子同时生成、首帧编译、后处理池子扩容）。
如果你把 spike 当成帧率问题去"降粒子数"，改完会发现没效果 —— 这就是先分类的价值。

#### 第 1 步：取证（三个数字）

| 数字 | 从哪来 | 说明什么 |
|---|---|---|
| 总帧时间 | F3 的帧时间 / 游戏内 FPS | 到底有没有问题 |
| Photon 提取耗时 | `PhotonParticleGroup.averageExtractTimeUs()`（60 帧滚动采样） | CPU 侧每帧花在 Photon 提取（合批、排序、提交）上的时间 |
| draw call 数量级 | F3 与场景对照 | 批次是不是被打散了 |

判断标准：**提取耗时占帧时间的比例**。比例很小（几个百分点）就别在 Photon 上使劲了，
瓶颈在别处；比例很大，再往下走。

同时把"卡"发生的那一帧和之前几帧的**场景**记下来（在放什么技能、有多少特效、
是不是第一次播这个效果）—— 这是区分"每帧成本"和"瞬时成本"的唯一依据。

#### 第 2 步：定位（逐个隔离，一次只改一件事）

按这个顺序做减法，每步只动一件事并立刻观察：

1. **关后处理**：`/photonfx clear` 或把 `enable_custom_effects` 关掉。
   如果帧率立刻恢复 → 问题在全屏 pass，去看 pass 数与池化预算（`postfx_pool_budget_mb`）。
2. **把粒子数上限压到 1/4**：`maxParticles` 改小。
   如果恢复 → 问题在粒子数量或逐粒子成本（碰撞、噪声 Quality、UV 动画）。
3. **合并材质与层**：把同一个特效里的材质槽尽量统一，
   `Layer`、`maskGroup`、`orderInLayer` 保持一致。
   如果恢复 → 问题是批次被打散（draw call 太多）。
4. **看是不是 spike**：如果只有"第一次放技能"那一帧卡，
   考虑首帧编译与资源加载；把效果预热一次（例如进图时先播 1 帧透明版本）能明显缓解。

#### 第 3 步：改动（按收益/风险排序）

1. 降 `maxParticles` 与发射率 → **风险最低，收益最直接**；
2. 统一材质/层/掩码，去掉运行时的逐帧渲染覆盖（覆盖会生成 override pass）
   → 恢复合批快路径；
3. 开 GPU 实例化（顶点数据同构时）；
4. 关掉每粒子的昂贵模块（碰撞、高 Quality 噪声）或把它们限制在"看得见的接触"上；
5. 后处理：减少 pass、避免在主目标之后才做 Bloom（HDR 操作被推迟就跑不了）；
6. 最后才考虑降质量（面数、贴图大小）。

#### 第 4 步：复测（同场景、同路径、同时长）

固定一个可重复的场景（同一位置、同一动作序列、开同一个光影包），记录第 1 步的三个数字，
和优化前对比。**"感觉顺了"不算通过** —— 至少要有帧时间或提取耗时其中一个数字变好。

#### 一个真实形状的案例：地面滞留特效把帧率拖垮

**现象**：放一次技能之后，接下来十几秒帧率一直偏低，特效消失后就恢复。

**取证**：提取耗时比例很高（不是后处理的问题）；F3 看 draw call 数量级也偏高。

**定位**：这个效果由三个发射器组成（火花 / 烟雾 / 拖尾），
而代码在每一帧都给它们写运行时覆盖（为了做"强度随距离衰减"）——
**每个"有效值不同"的覆盖都会生成一个兼容的 override pass**，合批被彻底打散。

**修法**：把"随距离衰减"改成**一个共享的注入值**（同一个 `RuntimeValue` 槽，
所有实例写同样的值 → 有效值相同 → 不会拆批），把逐实例的差异改成资产内的随机范围。

**复测**：draw call 数量级明显下降，提取耗时回到正常比例；帧率恢复。

**教训**：运行时覆盖是"每个实例都不一样"的代价。能用资产解决的就别用代码。

#### 三个常见误区

1. **把 `parallelUpdate` 当万能加速**：它会把粒子更新下放工作线程，
   但**带碰撞的物理会自动禁用**；粒子不多时调度成本可能高于收益。
2. **只在编辑器里试开头几秒**：真实运行里 `maxParticles` 会被打满，编辑器预览往往看不到峰值。
3. **先降画质再说**：降分辨率、砍面数是最后一步；先把"批次被打散"和"逐粒子昂贵模块"解决掉，
   通常就不需要牺牲画质了。

### 11.7 三分钟定位法（出事时先跑一遍这个）

```
看不见特效？
├─ 编辑器里能看见吗？
│   ├─ 不能 → 是制作问题：Shape / Emission / 材质有没有给？
│   └─ 能 → 继续
├─ 用命令能放出来吗？（/photon fx <id> block ~ ~-1 ~）
│   ├─ 不能 → 资源问题：id 与 assets/<ns>/fx/<path>.fx 对不上，
│   │          或换了导出文件没清缓存（/photon_client clear_client_fx_cache）
│   └─ 能 → 是代码接线问题：往下看"位置/时机"

看得见但不对？
├─ 贴着镜头跑 → 相机换算漏了（§6.2、§10.3）
├─ 位置对但抖 → tick 与 frame 都写了位置（§9.5、§10.5）
├─ 朝向反了/转了 57 倍 → 角度制与四元数混用（§9.4、§10.3）
├─ 停在最后一帧不动 → 锚点这一帧没更新（角色离屏/第一人称），
│                    确认 `fresh()` 语义与降级策略（§6.6、§10.3）
└─ 只有一部分人看得见 → 触发范围问题（§9.7：方块只发追踪该区块的玩家、实体是全服）

卡？
└─ 按 §11.6 的第 0 步先分类（帧率低 vs spike），再按第 1 步取三个数字
```

这棵树的用法是：**每一步都先排除一类原因**，不要一上来就翻源码。
九成的"特效不见了"都停在前两层（资源路径 / 缓存），九成的"位置不对"都能归到
坐标、角度、单位这三件事上。

## 12. 附录

### 12.1 术语中英对照

读源码、翻 Photon 编辑器、搜英文资料时，同一个概念经常在三种叫法之间跳。下表按本文用到的顺序收口，
中间一列是源码与编辑器里真正出现的英文写法。

| 中文 | 英文 / 源码写法 | 在本文里指什么 |
|---|---|---|
| 提取 | extract | 把「这一帧要画什么」从游戏对象里摘成纯数据（RenderState）的阶段 |
| 提交 | submit | 把几何登记进提交收集器、交给渲染管线排序的阶段 |
| 绘制 | draw | 管线按状态排序后真正发绘制命令的阶段 |
| 渲染状态 | `*RenderState` | 每帧重建的纯数据对象（实体、相机、关卡各有一套） |
| 渲染管线 | `RenderPipeline` | 着色器 + 混合 + 深度 + 顶点格式打包成的不可变描述 |
| 渲染类型 | `RenderType` | 游戏侧「用哪条管线、绑什么纹理、怎么排序」的命名组合 |
| 顶点消费者 | `VertexConsumer` | 往当前几何缓冲写顶点的入口 |
| 顶点格式 | `VertexFormat` / `VertexFormatElement` | 顶点里有哪些属性、各占几个分量 |
| 姿态栈 | `PoseStack` | 矩阵栈，负责从局部空间一路乘到相机空间 |
| 相机空间 | camera space / view space | 提交几何时顶点所处的坐标系，原点是相机 |
| 裁剪空间 | clip space | 顶点着色器输出、投影之后的坐标系 |
| 骨骼 | bone / `GeoBone` | 模型里可动画的节点 |
| 定位点 | locator / `GeoLocator` | 骨骼上标注出来的空挂点（挂武器、挂特效用） |
| 骨骼快照 | bone snapshot | 某一帧骨骼位姿的只读拷贝，可以带出渲染回调使用 |
| 渲染层 | render layer | 挂在主渲染器之后、负责追加几何的一层 |
| 渲染通道 | render pass / pass | 同一批状态下的连续绘制 |
| 特效 | FX（`.fx`） | Photon 编辑器里编辑、运行时按名字实例化的资产 |
| 特效包 | `.fxpack` | 把多个 FX 与依赖资源打进一个文件的容器 |
| 发射器 | emitter | FX 里负责产生粒子 / 光束 / 拖尾的节点 |
| 模块 | module | 挂在发射器上、逐帧改粒子数据的可组合单元 |
| 运行时 | runtime / `FXRuntime` | FX 在游戏里的一份实例及其每帧状态 |
| 运行时数据注入 | RuntimeValue | 每帧往运行时对象里写游戏侧数据（位置、颜色、进度）的通道 |
| 执行器 | executor / `IEffectExecutor` | 决定「特效跟谁、活多久、什么时候销毁」的对象 |
| 时间线 | timeline | 一段带轨道与信号的动画时间轴 |
| 轨道 | track | 时间线上按时间驱动某一类属性的行 |
| 信号 | signal | 时间线上发出的事件点，Java 侧可以接 |
| 后处理 | post-processing | 在画面画完之后对整屏做的效果（辉光、扭曲、暗角） |
| 实例化 | instancing | 一次绘制画一批同类对象，用实例数据区分 |
| 绘制调用 | draw call | 一次实际的绘制命令，数量直接决定 CPU 侧开销 |
| 蒙皮 | skinning | 用骨骼矩阵把静止顶点变形到当前姿态 |
| 矩阵调色板 | matrix palette | 把所有骨骼矩阵打包进 GPU 缓冲的那块数据 |
| 统一缓冲 | UBO / uniform block / std140 | 一次上传、着色器里按块读取的常量数据 |

### 12.2 类名速查

只列正文提到过的承重类；包名照抄即可，改版本后值得逐条复核。

**Minecraft / Blaze3D**

| 用途 | 类 |
|---|---|
| 姿态栈（矩阵栈） | `com.mojang.blaze3d.vertex.PoseStack` |
| 提交几何 | `net.minecraft.client.renderer.SubmitNodeCollector`（父接口 `OrderedSubmitNodeCollector`） |
| 渲染管线 | `com.mojang.blaze3d.pipeline.RenderPipeline` / `RenderPipeline.Snippet` / `CompiledRenderPipeline` |
| 原版管线常量 | `net.minecraft.client.renderer.RenderPipelines` |
| 渲染类型 | `net.minecraft.client.renderer.rendertype.RenderType` / `RenderTypes` / `RenderSetup` |
| 顶点与几何 | `com.mojang.blaze3d.vertex.VertexFormat` / `VertexFormatElement` / `DefaultVertexFormat` / `VertexConsumer` / `MeshData` |
| 设备与通道 | `com.mojang.blaze3d.systems.GpuDevice` / `CommandEncoder` / `RenderPass` / `RenderSystem` |
| 缓冲与 uniform | `com.mojang.blaze3d.buffers.GpuBuffer` / `GpuBufferSlice` / `Std140Builder` / `Std140SizeCalculator` |
| 着色器加载 | `net.minecraft.client.renderer.ShaderManager` + `com.mojang.blaze3d.shaders.ShaderType` |
| 着色器变体定义 | `net.minecraft.client.renderer.ShaderDefines` |
| 渲染状态 | `net.minecraft.client.renderer.state.level.LevelRenderState` / `CameraRenderState` |
| 帧与关卡 | `net.minecraft.client.renderer.GameRenderer` / `LevelRenderer` / `LevelExtractor` |

**NeoForge 事件**（都在 `net.neoforged.neoforge.client.event`）

| 事件 | 用途 |
|---|---|
| `RenderLevelStageEvent.After*` | 帧内八个阶段钩子（顺序见 §1.6） |
| `ExtractLevelRenderStateEvent` | 自定义渲染状态必须在这一步摘出来 |
| `SubmitCustomGeometryEvent` | 不写渲染器也能提交自定义几何 |
| `RenderFrameEvent.Pre` / `.Post` | 渲染帧边界（本项目用它推进渲染帧号） |
| `RegisterRenderPipelinesEvent` | 注册自定义 `RenderPipeline`（mod 总线、仅客户端） |

**GeckoLib 5**

| 用途 | 类 |
|---|---|
| 渲染器骨架（基类） | `com.geckolib.renderer.base.GeoRenderer` / `GeoRendererInternals` |
| 通用对象渲染器 | `com.geckolib.renderer.GeoObjectRenderer` |
| 单趟渲染的上下文对象 | `com.geckolib.renderer.base.RenderPassInfo` |
| 挂在渲染器上的层基类 | `com.geckolib.renderer.layer.GeoRenderLayer` |
| 每骨骼回调 | `com.geckolib.renderer.base.PerBoneRender` / `RenderPassInfo.BoneUpdater` / `BonePositionListener` |
| 骨骼与模型 | `com.geckolib.cache.model.GeoBone` / `BakedGeoModel` / `GeoLocator` |
| 骨骼快照 | `com.geckolib.animation.state.BoneSnapshot` / `com.geckolib.renderer.base.BoneSnapshots` |
| 层间传数据 | `com.geckolib.constant.dataticket.DataTicket` / `com.geckolib.constant.DataTickets` |

**Photon2**（`com.lowdragmc.photon.*`）

| 用途 | 类 |
|---|---|
| 定义与加载 | `client.fx.FX` / `FXData` / `FXHelper` |
| 运行时实例 | `client.fx.FXRuntime` |
| 执行器 | `client.fx.IEffectExecutor` / `IFXEffectExecutor` / `FXEffectExecutor` / `BlockEffectExecutor` / `EntityEffectExecutor` |
| 运行时数据注入 | `client.gameobject.RuntimeValue` / `RuntimeBinding` |
| 对象模型 | `client.gameobject.FXObject` / `IFXObject` / `FXObjectType` |
| 发射器 | `client.gameobject.emitter.Emitter` / `IParticleEmitter`、`...emitter.particle.ParticleEmitter` / `ParticleConfig` / `ParticleRuntime` |
| 拖尾 / 光束 / 模拟拖尾 | `...emitter.aratrail` 与 `TrailRuntime` / `BeamRuntime` / `AraTrailRuntime` |
| 力场对象 | `client.gameobject.ForceFieldObject` + `ForceFieldConfig` |
| 时间线 | `client.fx.timeline.TimelinePlayer` / `Signal` / `PhotonSignals` |
| 后处理 | `client.postfx.PhotonPostFX` / `client.postfx.runtime.PostEffectStack` |
| 附加 GPU 数据 | `client.gameobject.emitter.data.AdditionalGPUDataSetting` / `PhotonGpuChannels` |
| 特效包 | `client.fx.fxpack.FXPacks` |
| 编辑器 | `gui.editor.FXEditor` / `FXProject` |
| 网络 payload | `command.EffectCommand` / `BlockEffectCommand` / `EntityEffectCommand` / `RemoveBlockEffectCommand` / `RemoveEntityEffectCommand` |
| 注册与配置 | `PhotonRegistries` / `PhotonConfig` / `PhotonNetworking` |
| 原版侧渲染衔接 | `client.render.PhotonWorldRenderState` / `PhotonStage` / `PhotonPipelines` / `PhotonParticleGroup` / `PhotonFXLayer` |
| 调试命令 | `client.ClientCommands` / `client.PhotonParticleManager` |

**本项目**（`com.linweiyun.genshin.*`）

| 用途 | 类 |
|---|---|
| 角色渲染调度 | `client.render.character.CharacterRenderDispatcher` |
| 角色渲染器 | `client.render.character.CharacterRenderer` |
| 武器/挂点渲染层 | `client.render.character.WeaponAnchorGeoLayer` / `BoneMountGeoLayer` |
| 挂点缓存 | `client.render.character.WeaponAnchorCache` |
| 特效锚点 | `client.fx.FxAnchor` / `AnchorPose` / `FixedPointExecutor` |
| 示例特效接线 | `client.fx.TestCharacterFx` |
| GPU 蒙皮 | `client.render.optimize.gpu.BoneMatrixPalette` / `SkinDataStorage` / `SkinnedFeatureRenderer` / `SkinnedPipelines` / `SkinnedMesh` / `SkinnedGpuTimer` |
| 公共侧技能钩子 | `core.character.talent.SkillCastHooks` |

### 12.3 一页纸检查清单

#### 新增一个自定义渲染层

- [ ] 渲染层是否只挂在**客户端**（在专用服务器上不能被触碰）？
- [ ] 需要的数据是否在 `ExtractLevelRenderStateEvent` / 实体提取阶段就摘好？拿游戏对象引用会在渲染期崩。
- [ ] 写世界坐标时，有没有把相机位置加回去（提交几何的 `PoseStack` 原点是相机）？
- [ ] 要延迟执行的几何，是否把 `PoseStack.Pose` 拷了出来，而不是在 lambda 里用外层的栈？
- [ ] 是否确认了几何不会被更早/更晚的批次盖掉（提交顺序不等于绘制顺序）？

#### 新增一个特效（FX）

- [ ] `.fx` 与它依赖的贴图 / 着色器放在 `assets/<modid>/` 下的哪条路径，是否与代码里引用的名字一致？
- [ ] 这个特效是**跟随**（实体、骨骼、方块）还是**定点**？锚点从哪来、由谁推进？
- [ ] 生命周期谁管：常驻跟随靠每帧条件判断，定点靠自己的计时或状态机？
- [ ] 世界切换、维度跳转、`/photon` 清理命令之后，特效会不会变成「代码以为还在、引擎已经没有」的状态？重建逻辑写了吗？
- [ ] 需要从游戏侧喂进去的数据（颜色、进度、目标位置）走哪条注入通道，什么时候写、写在第几帧？

#### 从服务端触发

- [ ] 服务端**不加载** Photon 的客户端类；触发只能通过现成的 payload 或自定义网络包？
- [ ] 广播范围：只给附近玩家，还是全服？（按距离裁剪，别让全服一起炸粒子）
- [ ] 专用服务器上跑一遍会不会因为引用客户端类而崩？

#### 发布前

- [ ] 资源是否都打进 jar（`processResources` 之外临时加的目录会被漏掉）？
- [ ] 资源包 / 数据包侧的引用路径是否与开发环境一致（大小写、命名空间）？
- [ ] 性能：粒子数、材质切换、后处理层数是否在预算内？先合并批次、再谈降数量。
- [ ] 文档与[更新日志](/doc/changelog)是否同步（文档站实时渲染仓库里的 Markdown，改完刷新即可）？

### 12.4 本文的边界与维护约定

**事实优先级**：本机源码 jar（Minecraft / NeoForge / Photon / GeckoLib）> 本项目源码 > 官方文档站。
任何一条与源码冲突的描述，以源码为准；本文里凡是没能在源码里找到直接证据的结论，都写了「未确认」。

**核对方法**：正文里出现的类名、方法名、事件名都能在本机搜索到，搜索路径见
[§0.3](#03-怎么核对本文的每条说法)；项目侧结论都给了 `文件:行号`。

**维护约定**：

- 本文是仓库里的 Markdown（`docs/rendering-photon2-reference.md`），文档站请求时实时渲染，改完刷新即生效，不需要构建。
- 新增 / 改名文档时，同步 `web/src/main/java/com/linweiyun/minegenshin/web/docs/DocCatalog.java` 的文档清单
  与 `web/src/main/resources/static/assets/docs.js` 的兜底菜单（两处 slug 必须一致，否则侧边栏会少条目）。
- 影响使用者或存档的渲染 / 特效行为变更，按仓库约定记进根目录更新日志（站点页：[更新日志](/doc/changelog)）的对应版本小节。
- 升级 Minecraft、NeoForge、Photon2、GeckoLib 之后，本文里所有类名与事件名都值得重新核一遍；
  Blaze3D 与 `RenderState` 体系在版本之间改动频繁，本文已按 26.2 的口径写死。