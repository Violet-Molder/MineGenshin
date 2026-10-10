# 4. 实体渲染与 RenderState 系统


> 本节所有 `包/路径/类.java:行` 出处编号都来自本机源码，可直接跟着查：
> Minecraft 与 NeoForge 补丁 → `build/moddev/artifacts/minecraft-patched-26.2.0.88-sources.jar`；
> NeoForge 事件 → `neoforge-26.2.0.88-sources.jar`；GeckoLib → `geckolib-neoforge-26.2-5.5.6-sources.jar`；
> 本项目 → `src/main/java/com/linweiyun/genshin/**`。

## 4.1 三段式：提取 → 提交 → 绘制

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

## 4.2 线程模型：提取 / 提交 / 绘制全在渲染线程

- 客户端启动时把主线程改名并登记为渲染线程：`net/minecraft/client/main/Main.java:278-279`
  （`Thread.currentThread().setName("Render thread")` + `RenderSystem.initRenderThread()`）。
- 之后所有 GL/GPU 调用都必须过这道断言：`com/mojang/blaze3d/systems/RenderSystem.java:80/88/92`
  （`initRenderThread` / `isOnRenderThread` / `assertOnRenderThread`）。
- 结论：**客户端主线程 = 渲染线程**；提取、提交、绘制三段串行跑在同一根线程里，中间不存在"渲染线程和逻辑线程并发"。真正跑在 worker 线程上的是区块几何编译（`SectionRenderDispatcher`），那是另一条链路。

对本模组的实际意义：渲染层里读实体状态、写静态缓存（如 `WeaponAnchorCache`）都不需要加锁，但**不要**在三段中的任何一段里做阻塞 IO 或等待主线程 tick。

## 4.3 一帧内的事件时序（NeoForge 26.2）

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

## 4.4 实体渲染器：五个该覆写的方法

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

## 4.5 给实体加一层：`RenderLayer`

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

## 4.6 方块实体 / 物品 / 粒子：三套并行的 state 体系

| 对象 | 渲染器接口 | 状态基类 | 提交入口 |
|---|---|---|---|
| 实体 | `EntityRenderer<E, S>` | `EntityRenderState` | `EntityRenderer.submit(...)` |
| 方块实体 | `BlockEntityRenderer<BE, S>`（`blockentity/BlockEntityRenderer.java:18,20,26`） | `BlockEntityRenderState`（`blockentity/state/BlockEntityRenderState.java:17-22`） | `LevelRenderer.submitBlockEntities`（`LevelRenderer.java:735`） |
| 物品 | `ItemStackRenderState`（`item/ItemStackRenderState.java:29`） | 同类自身 | `ItemStackRenderState.submit(poseStack, collector, light, overlay, outlineColor)`（:112） |
| 粒子 | `ParticleGroupRenderState`（`state/level/ParticleGroupRenderState.java:11`） | `QuadParticleRenderState` 等（`state/level/QuadParticleRenderState.java:16,23,103`） | `ParticlesRenderState.submit(...)`（`state/level/ParticlesRenderState.java:22`） |

方块实体的提取是"候选 + 淘汰"两段：`BlockEntityRenderDispatcher.tryExtractRenderState(...)`（`blockentity/BlockEntityRenderDispatcher.java:104-105`）先 `createRenderState()` 再 `extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress)`；被视锥剔除的直接丢弃。它的基类 `extractBase` 只抄 `blockPos` / `blockEntityType` / `lightCoords` / `breakProgress`（`blockentity/state/BlockEntityRenderState.java:24-30`）。

物品渲染走的是 `ItemStackRenderState` 自己的状态机（`updateForLiving` / `updateForTopItem` 等），提交时它把里面的 `BakedQuad` 列表交给 `submitItem`。本项目在 `BoneMountGeoLayer.resolveMount` 里用 `Minecraft.getInstance().getItemModelResolver().updateForLiving(itemState, stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, player)` 造物品状态，再在骨骼位姿上提交（`BoneMountGeoLayer.java:143-152` 与 `:283-292`）。

## 4.7 生命周期与常见坑

1. **RenderState 每帧重建，不要跨帧持有。** 提取开始时 `LevelRenderState.reset()` 清空所有列表（`state/level/LevelRenderState.java:37-49`），提交开始时 `LevelRenderer.submitFeatures` 又把这些列表当帧用完即清：`levelRenderState.entityRenderStates.clear()`（`LevelRenderer.java:285`）。抓着一个 `EntityRenderState` 字段在下一帧读，读到的可能是别的实体或已清空的列表元素。
2. **状态里只放纯数据。** 放 `Level` / `Entity` 引用等于把渲染数据结构变成对逻辑线程状态的强引用：实体卸载后你还拿着它，或者读到一个正在被 tick 改写的对象。需要世界数据就在提取阶段抄成数值。
3. **PoseStack 必须配平。** `LevelRenderer` 在关键节点后都会断言栈为空：`checkPoseStack`（`LevelRenderer.java:306,575,718-722`，抛 `IllegalStateException("Pose stack not empty")`）。`submit` 里 push 了忘了 pop，报错点在几百行之外，很难定位。
4. **不要在自己的渲染层里调用 `Minecraft.getInstance().level` 之外的世界查询来"补数据"** —— 提取阶段拿到并塞进 state 才是正路；提交/绘制阶段世界对象可能已经被别的模组或异步区块更新改动。
5. **别在提取阶段提交几何、别在提交阶段改 state。** `SubmitCustomGeometryEvent` 的注释写得很直白：自定义几何用它（`SubmitCustomGeometryEvent.java:25-29`），而自定义状态用 `ExtractLevelRenderStateEvent`（`ExtractLevelRenderStateEvent.java:24-27`）。

## 4.8 案例：一个 boss 实体的三层外观 + 一条世界坐标光环

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

### 第一步：状态类

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

### 第二步：渲染器

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

### 第三步：注册

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

### 第四步：地面光环 —— 提取 + 提交

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

### 第五步：交卷前自检

按这五条对一遍，能挡掉本章九成的返工：

1. `createRenderState()` 一帧会被调用很多次，里面只允许 `new`，不许查世界、不许读配置。
2. `extractRenderState` 里 `super.extractRenderState(...)` 必须调，否则实体不会跟着插值移动。
3. `order(n)` 之间是稳定次序，但**同号内不保证先后**——两个 `order(1)` 谁先谁后是未定义的。
4. 一个 `pushPose()` 必须配一个 `popPose()`；`LevelRenderer` 每帧末尾会断言栈为空（`LevelRenderer.java:718-722`），
   报错行离出错处往往有几百行之远。
5. 只要某一层引用了别的对象（玩家、光照、别的实体），那些数据必须在**提取阶段**就抄进 State，
   不能在提交时现查。

## 4.9 补充：给「别人的」渲染器挂层时，数据从哪儿来

§4.8 的例子里，三层外观和光环都是**自己的**实体、自己的渲染器，所以状态字段直接在
`VesnaBossRenderState` 上加就行。但如果目标是**原版或别的模组**的渲染器（想给僵尸加一层、
给玩家加个轮廓），你既改不了它的 RenderState 类，也改不了它的 `extractRenderState`。
NeoForge 为此留了一条通道：**给已有渲染器注册「提取后写数据」的 modifier**。

### 三个组件

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

### 四条容易踩的坑

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
