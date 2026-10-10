# Minecraft 1.21.1 渲染管线与 Photon2 特效开发详解

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

## 1. 先建立正确的心智模型

### 1.1 1.21.1 的渲染是「立即模式」

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

### 1.2 一帧里到底按什么顺序发生

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

### 1.3 渲染发生在哪个线程

渲染只在渲染线程（客户端主线程）上发生：tick 与渲染跑在同一个线程里。1.21.1 的差别是
`RenderSystem` 提供了一组「稍后再执行」的入口：`RenderSystem.recordRenderCall(...)`
（`com/mojang/blaze3d/systems/RenderSystem.java:127`），`setShaderColor` / `setShaderTexture`
这类状态设置在非渲染线程调用时会自动走它。

实践结论：

- 可以在 tick 里准备数据（算位置、挑特效），但**写顶点、绑贴图、切 `RenderType` 都在渲染回调里做**。
- 服务端不能碰渲染类。发光 / 粒子 / 特效要么纯客户端判断，要么用网络包把「意图」传过来。

---

## 2. Blaze3D 在 1.21.1 里长什么样

### 2.1 包结构

| 包 | 内容 |
| --- | --- |
| `com.mojang.blaze3d.vertex` | `PoseStack`、`VertexConsumer`、`BufferBuilder`、`MeshData`、`BufferUploader`、`VertexFormat`、`Tesselator` |
| `com.mojang.blaze3d.systems` | `RenderSystem`（着色器、贴图、混合、深度、矩阵） |
| `com.mojang.blaze3d.platform` | `GlStateManager`、`Window` |
| `com.mojang.blaze3d.preprocessor` | `GlslPreprocessor`（`#moj_import` 在 `:20`） |
| `net.minecraft.client.renderer` | `RenderType`、`RenderStateShard`、`ShaderInstance`、`MultiBufferSource`、`LightTexture`、`PostChain` / `PostPass` |
| `net.minecraft.client.renderer.entity` | `EntityRenderer`、`EntityRenderDispatcher` |

### 2.2 `PoseStack`

`PoseStack`（`com/mojang/blaze3d/vertex/PoseStack.java`）是 26.2 之后仍然保留的东西，方法集几乎没变：

```java
poseStack.pushPose();                   // :57
poseStack.translate(x, y, z);           // :23 双精度 / :27 float
poseStack.mulPose(quaternionf);         // :45
poseStack.scale(sx, sy, sz);            // :32
poseStack.rotateAround(q, x, y, z);     // :51
poseStack.popPose();                    // :61
Matrix4f m = poseStack.last().pose();   // :65
```

差别不在 `PoseStack` 本身，而在**谁在用它**：1.21.1 的实体渲染直接把 `PoseStack` 喂给
`VertexConsumer`，没有 RenderState 中转。

### 2.3 `VertexConsumer` / `BufferBuilder` / `MeshData`

`com/mojang/blaze3d/vertex/VertexConsumer.java` 的契约很小（`:16` 起）：

```java
addVertex(float x, float y, float z)   // 开始一个顶点
setColor(int r, int g, int b, int a)
setUv(float u, float v)
setUv1(int u, int v)
setUv2(int u, int v)                   // 打包后的光照值
setNormal(float x, float y, float z)
```

写顶点的顺序**必须**与 `VertexFormat` 声明的元素顺序一致。写错顺序不会报错，只是画面错位
（UV 跑到颜色上、法线跑到 UV 上）。

构建一批顶点用 `BufferBuilder`（`com/mojang/blaze3d/vertex/BufferBuilder.java:13`）：

```java
BufferBuilder bb = Tesselator.getInstance()
        .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

bb.addVertex(pose.last(), x, y, z).setColor(255, 255, 255, 255);
// …… 一个 quad 的四个顶点

MeshData mesh = bb.buildOrThrow();
BufferUploader.drawWithShader(mesh);   // com/mojang/blaze3d/vertex/BufferUploader.java:24
```

`BufferUploader` 只有两个入口：`drawWithShader(MeshData)` 用当前着色器画，
`draw(MeshData)`（`:37`）用 `MeshData` 自带的着色器画。

### 2.4 `RenderType` 与 `RenderStateShard`

`RenderType.create(...)` 是自定义渲染类型唯一的入口
（`net/minecraft/client/renderer/RenderType.java:1125`）：

```java
public static RenderType.CompositeRenderType create(
        String name,
        VertexFormat format,
        VertexFormat.Mode mode,
        int bufferSize,
        RenderType.CompositeState state);
```

`CompositeState` 由 `RenderStateShard` 拼出来，常用的有：

| `RenderStateShard` | 作用 |
| --- | --- |
| `TRANSLUCENT_TRANSPARENCY` / `ADDITIVE_TRANSPARENCY` | 混合方程 |
| `NO_DEPTH_TEST` / `LEQUAL_DEPTH_TEST` | 深度测试 |
| `COLOR_DEPTH_WRITE` / `COLOR_WRITE` | 深度与颜色写入 |
| `CULL` / `NO_CULL` | 面剔除 |
| `LIGHTMAP` / `NO_LIGHTMAP` | 是否吃世界光照 |
| `ShaderStateShard` | 把 `RenderType` 绑到某个 `ShaderInstance` 的供应商 |

写一个自定义 `RenderType` 的标准姿势：

```java
public static final RenderType.CompositeRenderType MY_GLOW =
    RenderType.create("minegenshin_glow",
        DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
        VertexFormat.Mode.QUADS,
        1536,
        false, false,
        RenderType.CompositeState.builder()
            .setShaderState(new RenderStateShard.ShaderStateShard(() -> MyShaders.GLOW)) // 见 §5.5
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
            .setCullState(RenderStateShard.NO_CULL)
            .setLightmapState(RenderStateShard.LIGHTMAP)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .createCompositeState(false));
```

> 与 26.2 的重要差别：26.2 的 `RenderType` 与「渲染管线对象」是分开的（命名渲染类型 +
> `RenderPipeline`）。1.21.1 没有渲染管线对象，状态全部内联在 `RenderType` 里，
> 着色器就是一份 core shader JSON。

### 2.5 `RenderSystem` 状态

1.21.1 里能直接改的状态都在 `RenderSystem`
（`com/mojang/blaze3d/systems/RenderSystem.java`）：

```java
RenderSystem.setShader(() -> MyShaders.GLOW);      // :700
RenderSystem.setShaderTexture(0, textureLocation); // :714
RenderSystem.setShaderColor(r, g, b, a);           // :418
RenderSystem.enableBlend();                        // :196
RenderSystem.blendFunc(src, dst);                  // :206
RenderSystem.depthMask(boolean);                   // :191
RenderSystem.recordRenderCall(() -> { ... });      // :127
```

只要走 `RenderType` + `MultiBufferSource`，这些状态就不用自己设 —— 它们由 `RenderType` 统一管。

### 2.6 顶点格式与属性

常用预设都在 `DefaultVertexFormat`：`POSITION`、`POSITION_COLOR`、`POSITION_TEX`、
`POSITION_COLOR_TEX_LIGHTMAP`、`POSITION_COLOR_NORMAL`、`NEW_ENTITY`、`BLOCK`。

粒子 / 特效最常用 `POSITION_COLOR_TEX_LIGHTMAP`：`addVertex → setColor → setUv → setUv2(light)`。
`setUv2` 接的是打包后的光照值（`LightTexture#pack`），不是 UV1。

---

## 3. 坐标空间完全指南

这一节两条线几乎一样，差别只有类名（1.21.1 用 `ResourceLocation`，26.2 用 `Identifier`）。

### 3.1 五个空间

| 空间 | 原点 | 单位 | 谁在用 |
| --- | --- | --- | --- |
| 世界空间 | 世界原点 | 方块 | 实体坐标、粒子位置、Photon FX 根 |
| 相机空间 | 相机 | 方块 | `RenderLevelStageEvent` 给的 `PoseStack` 起点 |
| 实体空间 | 实体脚底 | 方块 | `EntityRenderer#render` 的入参 |
| 模型空间 | 模型根 | 1/16 方块 | Blockbench / `.geo.json` / GeckoLib 骨骼 |
| 屏幕空间 | 窗口左上 | 像素 | GUI、`GuiGraphics` |

### 3.2 三条换算公式

```text
模型空间 → 世界空间： 世界坐标 = 实体坐标 + 模型坐标 / 16 （再叠加骨骼的旋转与缩放）
世界空间 → 相机空间： 相机空间 = 世界坐标 - 相机坐标
世界空间 → 屏幕空间： 屏幕坐标 = 投影 × 模型视图 × 世界坐标
```

### 3.3 角度还是弧度

| 位置 | 单位 |
| --- | --- |
| 实体 / 相机朝向（`yRot`、`xRot`） | 度 |
| `Mth.sin/cos/rotLerp` 的入参 | 度 |
| `Quaternionf.rotationXYZ(...)` | 弧度 |
| `PoseStack.mulPose(...)` 的参数 | 四元数（弧度来源） |
| GeckoLib 的 `.geo.json` / `.animation.json` | 度（加载期转弧度） |
| GeckoLib 运行时（`GeoBone#getRotX`） | 弧度 |

### 3.4 常见症状对照表

| 症状 | 通常原因 |
| --- | --- |
| 特效整体偏了半格 | 用了摆件（`GeoObjectRenderer`）的 +0.5 平移补偿 |
| 模型上下颠倒 | 模型空间与世界空间的 X 轴反向 |
| 特效跟手但方向反 | 用了 `yRot` 直接当弧度，或四元数欧拉序不对 |
| 位置一格一格跳 | 在 tick 里写了位置，又在 frame 里写了一遍 |

---

## 4. GeckoLib 4 的渲染管线（1.21.1 线用 4.9.3）

### 4.1 本项目走的是哪条路

角色与生物的模型都走 GeckoLib，但渲染器不是 `GeoEntityRenderer` 就是 `GeoObjectRenderer`：

| 场景 | 用的渲染器 | 例子 |
| --- | --- | --- |
| 标准生物 | `GeoEntityRenderer<T extends Entity & GeoAnimatable>` | `LargeCryoSlime` |
| 玩家 / 摆件（自己摆位） | `GeoObjectRenderer` + 项目自己的 `CharacterRenderer` | 出战角色 |
| 被替换的实体（玩家本体） | `GeoReplacedEntity` 体系 | `GenshinReplacedPlayer` |

注册走 NeoForge 事件（本项目 `MinegenshinClient#registerEntityRenderers`）：

```java
event.registerEntityRenderer(ModEntities.LARGE_CRYO_SLIME.get(),
        context -> new GeoEntityRenderer<>(context,
                new CategoryGeoModel<LargeCryoSlime>(AssetCategory.ENTITY, "large_cryo_slime")));
```

**注意**：本项目 1.21.1 的 `client/render/optimize/GeoRenderIntercept` 目前是空实现
（`trySubmit` 恒返回 `false`），也就是说 26.2 那套「几何提交接管 + GPU 蒙皮」在 1.21.1 上还没接，
1.21.1 走的是 GeckoLib 默认渲染路径。文档里凡是 26.2 描述的 `submitRenderTasks` 接管，
在 1.21.1 上都不成立。

### 4.2 一趟渲染的完整调用顺序

以 `GeoEntityRenderer` 为例（`software/bernie/geckolib/renderer/GeoEntityRenderer.java`）：

```text
EntityRenderDispatcher
  └─ GeoEntityRenderer#render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight)   :197
       └─ GeoRenderer#defaultRender(...)                                                              :126
            ├─ preRender(...)                                                                         :232
            ├─ actuallyRender(...)        ← 摆模型、算朝向                                               :178
            ├─ renderRecursively(...)     ← 逐骨骼遍历、写顶点                                          :260
            ├─ applyRenderLayers(...)     ← 跑所有 GeoRenderLayer                                        :220
            └─ postRender(...)                                                                        :240
```

每一层都可能被 `GeoRenderEvent` 拦住：`GeoRenderEvent.Entity.Pre` 可以取消渲染，
`GeoRenderEvent.Entity.CompileRenderLayers` 可以往渲染器上挂新层
（`software/bernie/geckolib/event/GeoRenderEvent.java:356`、`:442`）。

### 4.3 `GeoRenderLayer` 的四个钩子

`GeoRenderLayer`（`software/bernie/geckolib/renderer/layer/GeoRenderLayer.java`）：

| 方法 | 时机 | 典型用途 |
| --- | --- | --- |
| `preRender(...)` `:62` | 本体几何提交前 | 改骨骼状态、准备要挂的东西 |
| `render(...)` `:74` | 本体几何提交后 | 画挂件、画辉光 |
| `renderForBone(...)` `:90` | 每根骨骼 | 只在某根骨骼上画东西（武器、光环） |
| `getDefaultBakedModel(...)` `:38` | 需要模型时 | 自定义层自己的模型 |

### 4.4 骨骼位姿：只有渲染那一瞬间才存在

GeckoLib 4 的骨骼状态是**每帧重算**的：

1. `AnimationProcessor` 把动画采样结果写进 `GeoBone`（旋转 / 位移 / 缩放，弧度）；
2. `renderRecursively` 逐骨骼累乘变换，把顶点写进 `VertexConsumer`；
3. 这一趟结束，骨骼上留下的只有「最后一次算出来的值」。

想在渲染之外拿到骨骼位置，GeckoLib 4 提供的是**快照**：

```java
BoneSnapshot snapshot = new BoneSnapshot(bone);      // animation/state/BoneSnapshot.java:35
float rotX = snapshot.getRotX();                      // :97
float offY = snapshot.getOffsetY();                   // :89
```

`AnimatableManager#getBoneSnapshotCollection()`（`animation/AnimatableManager.java:62`）保存了上一次
动画更新时抓的快照，`clearSnapshotCache()`（`:66`）负责清。项目里的做法是在渲染层的
`preRender` 里读骨骼、把结果存进缓存，渲染外再消费 —— 不要在 tick 里直接读骨骼。

### 4.5 写一个自定义渲染层（最小骨架）

```java
public class MuzzleAnchorLayer<T extends Entity & GeoAnimatable>
        extends GeoRenderLayer<T> {

    public MuzzleAnchorLayer(GeoRenderer<T> renderer) {
        super(renderer);
    }

    @Override
    public void renderForBone(PoseStack poseStack, T animatable, GeoBone bone,
                              RenderType renderType, MultiBufferSource bufferSource,
                              VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        if (!"muzzle".equals(bone.getName())) return;
        poseStack.pushPose();
        // 此时 poseStack 已经在「这根骨骼对齐后的坐标系」里
        poseStack.translate(0, 0.1, 0);
        // …… 写顶点
        poseStack.popPose();
    }
}
```

挂上去有两条路：一次性事件（推荐，见 §4.2 的 `CompileRenderLayers`），
或者给渲染器加 `renderer.addRenderLayer(...)`。

### 4.6 GeckoLib 4 的坑（全部可在源码核对）

| 坑 | 事实 | 应对 |
| --- | --- | --- |
| `PlayState` 只有两个值 | `animation/PlayState.java`：只有 `CONTINUE` / `STOP` | 想「暂停」用 `AnimationController#setAnimationSpeed(0)`，解冻时 `setAnimationSpeed(1)` + `forceAnimationReset()` |
| `GeoObjectRenderer` 位置偏半格 | 父类默认 `translate(+0.5, +0.51, +0.5)`，那是给方块原点导出的摆件用的 | 覆盖 `preRender` 里反向 `translate(-0.5F, -0.51F, -0.5F)`（本项目 `CharacterRenderer` 就是这么做的） |
| 动画不播、日志无报错 | 动画键名与 `.animation.json` 里的名字不一致，或 `getBakedModel` 绕过骨骼登记 | 先核对键名，再确认模型供给链（`AssetGeoCache → GenshinGeoCache → GeckoLib 缓存`） |
| 模型正常但不动 | 自定义渲染器覆写了 `render` 却没调 `defaultRender` | 让 `render` 只做入口，几何走父类 |
| 定位器 / 骨骼名写错 | GeckoLib 4 定位器是模型里声明的空挂点，名字必须逐字对上 | 用编辑器核对，不要凭记忆写 |

### 4.7 GeckoLib 4 与 GeckoLib 5 的对照（移植时最容易踩的一栏）

| 概念 | 1.21.1（4.9.3） | 26.2（5.5.6） |
| --- | --- | --- |
| 渲染器基类 | `software.bernie.geckolib.renderer.GeoRenderer` | `com.geckolib.renderer.base.GeoRenderer` |
| 渲染状态 | 没有 RenderState，直接吃实体对象 | `GeoRenderState` + `DataTicket` |
| 骨骼快照 | `animation/state/BoneSnapshot` | `animation/state/BoneSnapshot`（同名前缀，包名改了） |
| 动画暂停 | `setAnimationSpeed(0)` | `PlayState.PAUSE` 存在 |
| 动画钩子名 | `getBakedAnimation` 之类不存在 | 命名整体换了，别照搬 5.x 的钩子 |

---

## 5. 着色器：1.21.1 还有 Core Shader JSON

### 5.1 结论先讲

26.2 **删掉了 core shader JSON**，换成在 Java 里描述的渲染管线。1.21.1 反过来：

```text
assets/<namespace>/shaders/core/<name>.json     ← 声明顶点/片元着色器与采样器
assets/<namespace>/shaders/core/<name>.vsh      ← GLSL 顶点
assets/<namespace>/shaders/core/<name>.fsh      ← GLSL 片元
```

JSON 里可以声明 attributes、samplers、uniforms（Mojang 反序列化成 `ShaderInstance` 的字段）。
文件没有列出来的 uniform 也可以由 Java 侧 `getUniform("名字")` 拿到 ——
`ShaderInstance` 内置一批常用 uniform（`ModelViewMat` / `ProjMat` / `ColorModulator` /
`FogStart` / `FogEnd` / `GameTime` / `ScreenSize` …），见
`net/minecraft/client/renderer/ShaderInstance.java:163` 起。

### 5.2 最小 core shader

```json
{
  "blend": { "func": "add", "srcrgb": "srcalpha", "dstrgb": "1-srcalpha" },
  "vertex": "minegenshin:glow",
  "fragment": "minegenshin:glow",
  "attributes": [ "Position", "Color", "UV0" ],
  "samplers": [ { "name": "Sampler0" } ],
  "uniforms": [ { "name": "ModelViewMat", "type": "matrix4x4", "count": 16, "values": [ ] } ]
}
```

> 这里也能写 `blend`，但只要你把着色器挂进 `RenderType` 的 `ShaderStateShard`，
> 混合状态就由 `RenderType` 决定 —— 两个地方都写时以 `RenderType` 的为准，容易看错。

### 5.3 `#moj_import`

1.21.1 的 GLSL 支持 `#moj_import <文件名>`，由 `GlslPreprocessor` 处理
（`com/mojang/blaze3d/preprocessor/GlslPreprocessor.java:20`）。它有三个来源：

1. 原版内置的通用头（雾、光照、投影辅助等，见 assets 里的 `minecraft:shaders/include/...`）；
2. 你自己 mod 的 `assets/<ns>/shaders/include/<name>.glsl`；
3. 相对路径导入。

写惯 26.2 的人最容易忘的一点：**1.21.1 的 include 不是编译期宏系统**，
它只是文本替换，`#define` 是 GLSL 自己的预处理。

### 5.4 Uniform 与 Sampler

```java
ShaderInstance shader = MyShaders.GLOW;
shader.safeGetUniform("MyTime").set((float)(System.currentTimeMillis() % 100000) / 1000f);
shader.setSampler("Sampler0", textureLocation);   // 采样器按名字绑定
shader.apply();
```

采样器名字必须与 JSON 里 `samplers` 声明的名字一致；绑定贴图用 `RenderSystem.setShaderTexture(0, ...)`
只对「0 号采样器」有效，其余都要走 `setSampler`。

### 5.5 注册自己的 `ShaderInstance`

NeoForge 21.1 提供 `RegisterShadersEvent`（`net/neoforged/neoforge/client/event/RegisterShadersEvent.java:27`），
它跑在 mod 事件总线上：

```java
@SubscribeEvent
static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
    event.registerShader(
        new ShaderInstance(event.getResourceProvider(), "minegenshin:glow",
                           DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP),
        shader -> MyShaders.GLOW = shader);
}
```

Photon 2.2.8 用的就是这一套：它在 `PhotonShaders#registerShaders`
（`PhotonShaders.java:85`）里一口气注册掉自己全部 core shader。

### 5.6 接到 `RenderType`

```java
new RenderStateShard.ShaderStateShard(() -> MyShaders.GLOW)
```

就这么一句 —— 26.2 里「渲染管线对象 + 命名渲染类型」的两步，在 1.21.1 上合成了一步。

### 5.7 着色器出问题时怎么查

| 症状 | 先看什么 |
| --- | --- |
| 一片纯色（黑 / 白 / 洋红） | 采样器没绑 → 贴图是未初始化纹理 |
| 顶点全挤在原点 | `ModelViewMat` / `ProjMat` 没在 JSON 里声明 |
| 编译报错但日志只有一行 | 打开 `-Dmixin.debug` 没用，要看 `ShaderInstance` 的编译日志（GLSL 报错带行号） |
| 换维度 / 换资源包后失效 | 资源重载会重建 `ShaderInstance`，缓存里存了旧实例 |

---

## 6. Photon2 2.2.8 的渲染架构

> **先说一条容易误传的事**：Photon 2.2.8 **没有** GeckoLib 集成。
> 2.2.8 的 sources jar 与 all.jar 里搜 `geckolib` / `bernie` 的命中数都是 0，
> 上游 `Low-Drag-MC/Photon` 与 `Low-Drag-MC/LDLib2` 的提交历史里也没有。
> 1.21.1 线上「支持 GeckoLib」指的是 **GeckoLib 4.9.3 本身**（见第 4 节）与本项目的接入层，
> 以及 Photon 自 2.2.7 起提供的**动态网格注入**（`IDynamicMesh`，见 §6.4）。

### 6.1 包结构与职责

| 包 | 职责 |
| --- | --- |
| `com.lowdragmc.photon.client.fx` | 运行时：`FX` / `FXRuntime` / `FXHelper` / `IEffectExecutor` |
| `com.lowdragmc.photon.client.gameobject` | 场景对象：`IFXObject` / `FXObject` / `Emitter` 与各类设置 |
| `com.lowdragmc.photon.client.gameobject.emitter.data.material` | 材质：`TextureMaterial` / `SpriteMaterial` / `ShaderGraphMaterial` / `KilaMaterial` |
| `com.lowdragmc.photon.client.gameobject.emitter.data.model` | 模型源：glTF / OBJ / JSON / 动态网格 |
| `com.lowdragmc.photon.client.light` | 2.2.8 新增：动态光、体积光、雾体积 |
| `com.lowdragmc.photon.client.postfx` / `postprocessing` | 后处理与 Bloom |
| `com.lowdragmc.photon.client.compat.iris` | Iris 兼容层 |
| `com.lowdragmc.photon.gui.editor` | 编辑器（场景视图、时间轴、资源面板） |
| `com.lowdragmc.photon.command` / `client.ClientCommands` | `/photon` 命令 |

### 6.2 一帧里 Photon 干了什么

Photon 2.2.8 在 1.21.1 上挂的是 NeoForge 事件
（`com/lowdragmc/photon/client/PhotonClientListeners.java`）：

| 事件 | 用途 |
| --- | --- |
| `ClientTickEvent.Post` `:76` | 推进 FX 的 tick、更新动态光 |
| `RenderFrameEvent.Post` `:47` | 每帧收尾 |
| `RenderLevelStageEvent` `:93` | `AFTER_BLOCK_ENTITIES` 与 `AFTER_PARTICLES` 两处渲染 FX |
| `RenderGuiEvent.Post` `:111` | 编辑器的预览 / 叠加 |
| `LevelEvent.Unload` `:68` | 卸载关卡时清光 |
| `ChunkEvent.Load` `:53` / `TagsUpdatedEvent` `:60` | 动态光的体素世界与资源重载 |

粒子本身由 `PhotonParticleManager`（`client/PhotonParticleManager.java`）承载，
它继承 LDLib2 的 `com.lowdragmc.lowdraglib2.client.scene.ParticleManager` ——
也就是说 Photon 在 1.21.1 上是**接进原版粒子体系**的：`FXObject` 甚至直接 `extends Particle`
（`client/gameobject/FXObject.java:38`）。

### 6.3 Framework：`IFXObject` 与 Emitter 模型

```text
FX（一份 .fx 配置）
 └─ FXRuntime（一次播放实例）
     └─ IFXObject 树
         ├─ ParticleEmitter / TrailEmitter / BeamEmitter / AraTrailEmitter
         ├─ LightObject（2.2.8）
         └─ FogVolumeObject（2.2.8）
```

`IFXObject`（`client/gameobject/IFXObject.java:34`）的核心方法：

```java
emit(IEffectExecutor effect)                 // :208 挂到执行器上
updatePos(Vector3f) / updateRotation(...)    // :250 / :254 每帧改根变换
setDelay(int) / setSelfTimeScale(float)      // :75 / :138
remove(boolean force)                        // :179
```

`IEffectExecutor`（`client/fx/IEffectExecutor.java`）就是「特效挂在哪、怎么跟」：

| 回调 | 频率 | 该写什么 |
| --- | --- | --- |
| `updateFXObjectTick(IFXObject)` | 每刻 | 低频逻辑：锚点没了就销毁、状态切换 |
| `updateFXObjectFrame(IFXObject, float)` | 每帧 | 高频逻辑：跟随位置 / 旋转 / 缩放 |

**两个回调都写位置会互相打架**（一个按整刻跳、一个按帧插值，加起来就是抖动）：
只在 tick 推进状态，只在 frame 按 `partialTicks` 插值算最终位置。

### 6.4 材质与模型：2.2.8 的 KilaMaterial

材质统一走 `IMaterial`，内置实现包括 `TextureMaterial`、`SpriteMaterial`、
`BlockTextureSheetMaterial`、`CustomShaderMaterial`、`ShaderGraphMaterial`、`ShaderInstanceMaterial`、
以及 2.2.8 新加的 **`KilaMaterial`**（`.../data/material/kila/KilaMaterial.java`）。

KilaMaterial 是「一体式 VFX 材质」：一个材质里打包主贴图、遮罩、溶解、折射、菲涅尔、
MatCap、UV 特效、顶点偏移、投影等模块（同一目录下 `Kila*.java` 各一个模块），
`KilaPresets.ALL`（`KilaPresets.java:515`）提供了 **27 个按类别分组的预设**；
它还能反向导出成着色器图（`kila/export/KilaGraphExport.java`）。

模型源（`.../data/model`）在 1.21.1 上有五种：

| 注册名 | 类 | 说明 |
| --- | --- | --- |
| `json_model` | `JsonModelSource` | 已烘焙的 Minecraft JSON 模型（要 `ModelEvent.RegisterAdditional` 或动态可加载） |
| `obj_model` | `ObjModelSource` | OBJ 网格 |
| `gltf_model` | `GltfModelSource` | glTF（含切线） |
| `animated_gltf` | `AnimatedGltfModelSource` | 带骨骼与动画的 glTF（2.2.7 起） |
| `dynamic_mesh` | `DynamicMeshSource` | **动态网格注入**：实现 `IDynamicMesh`，每帧按 `revision()` 交出新几何 |

`IDynamicMesh`（`.../model/IDynamicMesh.java`）就是「几何在画的时候还在变」这条通道：

```java
PhotonMesh topology();          // 不变的拓扑（顶点数、索引、UV、静止姿势），同一实例复用
long revision();                // 内容变了才 +1（从 1 开始，0 表示静止姿势）
float[] geometry();             // 该版本的 positions + normals（可空：只在 GPU 上时）
int glBuffer();                 // 或者直接给一个 GL buffer
```

> 这条通道才是「别的动画库能不能接进来」的答案：谁把骨骼动画算成顶点流，谁就能当模型源用。
> GeckoLib 并没有替你做这件事，需要自己写一个 `IDynamicMesh` 适配器。

### 6.5 后处理

1.21.1 的原版后处理是 `PostChain` / `PostPass`
（`net/minecraft/client/renderer/PostChain.java:31`、`:281`、`:313`），
资源在 `assets/<ns>/shaders/post/<name>.json`。Photon 在这之上加了：

| 类 | 作用 |
| --- | --- |
| `client.postprocessing.PhotonPostProcessing` | 统一申请渲染目标、做 blit 与 bloom |
| `client.postfx.runtime.PostFXCamera` | 时间轴里 PostProcess 片段用的相机口径 |
| `client/gameobject.emitter.renderpipeline.StackedDistortion` | 叠加式扭曲 |

配置里控制 Bloom 与自定义特效：`photon-client.toml` 的 `enable_bloom`、`bloom_mip_level`、
`bloom_threshold`、`bloom_intensity`、`enable_custom_effects`、`enable_custom_effects_with_shader_pack`、
`postfx_pool_budget_mb`（`PhotonConfig.java:75` 起）。

### 6.6 2.2.8 新增：动态光与体积雾

这是本版最大的一块新能力，入口全部收在 `PhotonLights`
（`client/light/PhotonLights.java`）：

```java
PhotonLights.add(light)                       // :44  挂一盏光
PhotonLights.remove(light)                    // :48
PhotonLights.attach(entity, template, offset) // :83  把光挂到实体上，返回 AutoCloseable 句柄
PhotonLights.flash(template, durationTicks)   // :95  闪一下
PhotonLights.addFog(fogVolume)                // :62  加一团雾
PhotonLights.addProvider(provider)            // :53  自己当光源提供者
```

`DynamicLight`（`client/light/DynamicLight.java:13`）是链式模板：

```java
new DynamicLight()
    .point()                       // 或 .spot(innerAngle, outerAngle)
    .at(x, y, z)
    .color(1f, 0.8f, 0.4f)
    .intensity(8f)
    .range(10f)
    .shadows(true)
    .volumetric(0.5f);             // 体积光强度
```

配置项（`photon-client.toml`，`PhotonConfig.java:99` 起）：`dynamic_light.enabled`、
`soft_shadows`、`shadowed_lights`（默认 8 盏）、`resolution`、`contact_shadow_steps`、
`screen_shadow_steps`、`voxel_budget_ms`、`volumetric`、`volumetric_density`、`volumetric_samples`。
**体积光是有明确预算的**：`voxel_budget_ms` 默认 2ms，超预算会自动降级。

### 6.7 生命周期：`isValid()` 为什么重要

`FXRuntime#isValid()` 决定「这个特效实例还该不该活着」。1.21.1 上最容易写出的 bug 是
把 `PhotonParticleManager` 的 `time`（时间轴时钟，`clear()` 会重置）当成存活判据 ——
存活判据必须用单调递增的 tick 计数（管理器里那个 `tickCounter`）。

资源重载 / 换维度 / 退出世界时：`FXHelper.clearCache()`（`client/fx/FXHelper.java:43`）
会清掉 `.fx` 解析缓存，`/photon clear client cache fx` 就是它的命令入口。

### 6.8 扩展注册表

`PhotonRegistries`（`PhotonRegistries.java`）把所有可扩展点摊开：

| 注册表 | 类型 | 说明 |
| --- | --- | --- |
| `photon:fx_object` | `FXObjectType` | 自定义 FX 对象类型 |
| `photon:material` | `IMaterial` | 自定义材质 |
| `photon:number_function` | `NumberFunction` | 曲线 / 表达式函数 |
| `photon:shape` | `IShape` | 发射形状 |
| `photon:model_source` | `IModelSource` | 模型源 |
| `photon:timeline_track` | `TrackType` | 时间轴轨道类型 |
| `photon:animated_property` | `AnimatedPropertyType` | 可动画属性 |

注册用 LDLib2 的注解（`@LDLRegisterClient(name = "...", registry = "photon:model_source")`），
Photon 自己注册的 `IModelSource` / `IMaterial` 都是这么写的。

### 6.9 和其它 Mod 的边界

- **Iris / 光影**：`client/compat/iris/*` 是兼容层；`enable_bloom_with_iris_shader`、
  `iris_use_translucent_particle_program` 两个开关决定是否在光影下继续接管。
- **LDLib2**：Photon 是 LDLib2 的插件（`integration/PhotonLDLibPlugin.java`），编辑器、
  节点图、资源浏览器都复用 LDLib2。
- **原版粒子**：因为 `FXObject extends Particle`，Photon 的粒子会走原版粒子排序与渲染管线，
  不是「另开一套渲染系统」。

### 6.10 关键事实速查

```text
mod id              photon
FX 资源             assets/<ns>/fx/<name>.fx      （FXHelper.FX_PATH = "fx/"）
编辑器资源根        LDLib2 的 assets/photon/
客户端配置          config/photon-client.toml（enable_bloom / dynamic_light / volumetric）
命令                /photon （清缓存、特效预览、Photon 1 → 2 转换）
```

---

## 7. 实战：把渲染接到 Photon

### 7.1 「什么时候召唤粒子」只有四种时机

本项目的 `client/fx` 就是这四种时机的范例（见 `TestCharacterFx` 的类注释）：

| 时机 | 锚点 | 用的 API |
| --- | --- | --- |
| 常驻，跟随角色 | 角色本身 | 内置 `EntityEffectExecutor` |
| 常驻，跟随手中武器 | 武器骨骼 | 自定义锚点 + `FxAnchor` |
| 只在攻击动画期间 | 武器尖 / 指定骨骼 | 自定义锚点 + `ActionStateMachine` |
| 技能释放后定点飞出 | 世界坐标 | `FixedPointExecutor`（实现 `IEffectExecutor`） |

来自服务端的触发一律走网络包 + 客户端判断，不在服务端构造 `FX`。

### 7.2 常驻跟随：`EntityEffectExecutor`

```java
FX fx = FXHelper.getFX(ResourceLocation.fromNamespaceAndPath("minegenshin", "sword_aura"));
var executor = new EntityEffectExecutor(fx, level, player, EntityEffectExecutor.AutoRotate.LOOK);
executor.setOffset(0, 1.0, 0);
executor.start();
```

它会每帧把 FX 根挪到实体眼睛位置（再加 offset），实体死亡时自动销毁
（`EntityEffectExecutor.java:41` 起）。`AutoRotate` 有 `NONE / FORWARD / LOOK / XROT` 四档。

### 7.3 跟随武器：骨骼 → 世界坐标

1. 在渲染层里读武器骨骼（§4.5 的 `renderForBone`）；
2. 把 `PoseStack` 里那根骨骼的矩阵取出来，变换到世界坐标；
3. 把结果存进 `WeaponAnchorCache` 一类的缓存；
4. 下一帧用 `FxAnchor` / `updatePos` / `updateRotation` 把 FX 根挪过去。

关键约束：**第 1 步必须发生在渲染线程**，第 4 步必须在 frame 回调里，中间的数据用缓存过桥。

### 7.4 只在攻击时生效

```java
if (!ActionStateMachine.isAttacking(player)) { anchor.stop(); return; }
```

动画期间开关特效是状态问题，不是渲染问题：状态变化在 tick 里判断，渲染只负责跟随。

### 7.5 定点生成 + 朝前飞

`FixedPointExecutor`（`client/fx/FixedPointExecutor.java`）的做法值得照抄：

```text
tick  ：origin += forward × speed                       （推进一个整刻）
frame ：pos = origin + forward × speed × partialTicks   （刻内插值，不抖）
```

速度在客户端算、位置由执行器给，特效本身（`.fx`）里把模拟空间设成 `WORLD` 就行；
反过来把位移全写在特效里、`forwardSpeed` 传 0 也成立，两种都由特效作者决定。

### 7.6 把游戏状态喂给着色器

1.21.1 上的通道比 26.2 少（没有 RenderState 票据），实际能用的三条：

1. **uniform**：`shader.safeGetUniform("X").set(v)`，每帧在渲染回调里写；
2. **自定义材质**：在 `IMaterial` 里读写 `MaterialContext`，供着色器图使用；
3. **顶点数据**：把数值塞进 UV2 / 颜色通道，着色器里再还原（省 uniform 但要小心精度）。

---

## 8. 性能

### 8.1 先建立预算概念

1.21.1 的立即模式有一个 26.2 没有的固定成本：**状态切换**。每换一次 `RenderType`，
驱动就要重绑着色器、贴图、混合与深度；写顶点本身反而不贵。

### 8.2 常见瓶颈与对策

| 瓶颈 | 症状 | 对策 |
| --- | --- | --- |
| 顶点数 | 单帧顶点暴涨、FPS 与实体数成反比 | 合并骨骼层级、减面；几何优化（26.2 那套 GPU 蒙皮在 1.21.1 上还没接） |
| 状态切换 | CPU 时间在 `RenderType` 之间跳 | 同材质同状态的顶点尽量连着写 |
| 动态光 | 帧时间随光数量线性上升 | `shadowed_lights` 只给关键少数；`resolution` 降 0.5；`volumetric` 关掉 |
| 体积雾 | 全屏开销 | `volumetric_samples` 4 → 2；`voxel_budget_ms` 压到 1ms |
| 粒子数量 | GPU 满载、画面糊 | 编辑器里限 `Emission` 的 rate over distance / over time |

### 8.3 三条铁律

1. **不要在渲染回调里做逻辑计算**（找实体、遍历背包、算曲线）；
2. **不要在 tick 里碰渲染对象**（`PoseStack`、`VertexConsumer`、`ShaderInstance`）；
3. **同一份数据只算一次**（骨骼矩阵、光照值、贴图坐标），存起来复用。

### 8.4 怎么测

- 原版 `F3` 的帧时间够用来看整体；
- Photon 的动态光有自己的计时器（`LightPassTimer`、`LightDebug`）；
- 本项目自带 `FrameTimeStats` / `RenderOptimizeStats`（1.21.1 上优化路径是关的，
  但计时与统计可用）。

---

## 9. 排错手册

### 9.1 按症状查

| 症状 | 优先怀疑 |
| --- | --- |
| 特效不出现 | `.fx` 路径 / 命名空间写错；`FXHelper` 缓存没清；资源重载后没重建 |
| 特效出现一次就没了 | `FXRuntime` 被 `remove(force=true)`；执行器提前 `retire` |
| 位置抖动 | tick 与 frame 都写位置（§6.3） |
| 位置偏 | 摆件 +0.5 平移（§4.6）；模型以方块角为原点 |
| 光影下黑掉 | `enable_custom_effects_with_shader_pack` 关着；Iris 接管了管线 |
| 模型不播动画 | GeckoLib 4 的键名 / 模型供给链（§4.6） |
| 动态光不亮 | `dynamic_light.enabled=false`；`shadowed_lights` 用满；光的位置在相机背后 |

### 9.2 Photon 的调试命令

```text
/photon clear client cache fx        清 .fx 解析缓存
/photon ... convert                  Photon 1 的 fx 转成 2.x（需要权限 2）
```

(完整子命令见 `com/lowdragmc/photon/client/ClientCommands.java`。)

### 9.3 客户端 / 服务端隔离

- `com.lowdragmc.photon.client.*` 全部是 `@OnlyIn(Dist.CLIENT)`；
- 服务端只能发「播什么特效、播在哪」的意图；
- 本项目 `FixedPointExecutor` 这类类只在客户端 tick 里创建，任何服务端代码都不要 import。

---

## 10. 附录

### 10.1 类名速查（1.21.1 / 2.2.8）

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

### 10.2 参考

| 事实 | 来源 |
| --- | --- |
| MC / NeoForge | `neoforge-21.1.250-sources.jar`（`build/moddev/artifacts/`） |
| Photon | `photon-neoforge-1.21.1-2.2.8-sources.jar` |
| LDLib2 | `ldlib2-neoforge-1.21.1-2.2.42-sources.jar` |
| GeckoLib | `geckolib-neoforge-1.21.1-4.9.3-sources.jar` |
| Photon 2.2.8 变更 | Modrinth `photon-editor` 版本 `mc1.21.1-2.2.8-neoforge` 的 changelog |

### 10.3 本文未覆盖 / 未确认的部分

- 1.21.1 上的 GPU 蒙皮 / 几何接管未接入（`GeoRenderIntercept` 仍是空实现），
  所以 26.2 文档第 7 章的性能结论不能照搬到这里；
- Photon 编辑器内部实现（时间轴、资源面板）只在必要处点到，未逐类展开；
- Iris 兼容层的细节（`IrisCompositeMode` 各档行为）未逐条核对；
- 官方文档站对 1.21.1 / Photon 2.2.x 的描述与 jar 有出入时，本文以 jar 为准，
  出入点没有逐条列出。
