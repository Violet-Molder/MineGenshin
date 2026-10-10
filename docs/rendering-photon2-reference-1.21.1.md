# Minecraft 1.21.1 渲染与 Photon2 完全参考

> **适用版本**：Minecraft `1.21.1` · NeoForge `21.1.250` · Java `21` · Photon2 `2.2.8` · LDLib2 `2.2.42` · GeckoLib `4.9.3`
> （本仓库 1.21.1 线的依赖，见 `gradle.properties`）
>
> **这份文档是什么**：把「1.21.1 的渲染管线怎么运转」和「Photon2 特效怎么接进模组」一次讲完，
> 面向**要动手写代码的人**：自定义渲染层、粒子与光束、后处理、骨骼挂点、特效触发链路。
>
> **它不是什么**：不是 26.2 那篇《[Minecraft 26.2 渲染与 Photon2 完全参考](rendering-photon2-reference.md)》的翻译 —— 两条写法不同。
> 本文每条结论都尽量给出来源；行号取自本机 Gradle 缓存里的
> `neoforge-21.1.250-sources.jar`、`photon-neoforge-1.21.1-2.2.8-sources.jar`、
> `ldlib2-neoforge-1.21.1-2.2.42-sources.jar`、`geckolib-neoforge-1.21.1-4.9.3-sources.jar`。

---

## 0. 导读

### 0.1 这篇文档解决什么问题

看完本文，你应该能独立回答下面这些具体问题：

- 这一帧里什么地方可以插入自己的渲染？（§1、§2）
- 顶点到底怎么变成画面？（§2、§3）
- 想加一个自定义着色器 / 渲染类型，1.21.1 上要改哪几个文件？（§3）
- GeckoLib 4 的骨骼什么时候可以被读到？怎么把 FX 挂到武器上？（§5、§6、§10）
- Photon2 2.2.8 的 `.fx`、材质、模型源、动态光、后处理分别是什么？（§8、§9）
- 特效不出现 / 抖动 / 偏位 / 光影下黑掉，先查哪儿？（§11）

### 0.2 记法约定

| 写法 | 含义 |
| --- | --- |
| `Foo.java:123` | 该文件第 123 行，文件来自上述 source jar 或本仓库 |
| **立即模式** | 1.21.1 的渲染模型：写顶点 → 关批 → 画 |
| **三段式** | 26.2 的渲染模型：extract → submit → render |

### 0.3 先看这三句（懒人版）

1. **1.21.1 没有 RenderState**：渲染数据必须在动手写顶点那一刻就在手边；
2. **1.21.1 还有 core shader JSON**：自定义着色器 =
   `assets/<ns>/shaders/core/*.json` + `.vsh` + `.fsh`；
3. **Photon 2.2.8 挂在 NeoForge 的 `RenderLevelStageEvent` 上**：它不是另一套渲染系统，
   而是接进原版粒子体系的一套 FX 运行时。

---

## 1. 心智模型：1.21.1 的一帧是怎么画出来的

### 1.1 从 tick 到画面

```text
客户端主线程循环
  ├─ tick（20/s）：逻辑、网络、状态机
  └─ render（不限帧）：GameRenderer → LevelRenderer#renderLevel → …… → GUI
```

tick 与 render 在同一个线程里交替发生，所以：

- 「渲染线程」就是「客户端主线程」；
- 在渲染回调里做重活会同时吃掉 TPS 与 FPS；
- 服务端线程完全碰不到渲染类。

### 1.2 `renderLevel` 的顺序

`net/minecraft/client/renderer/LevelRenderer.java:914` 起的 `renderLevel` 是这一帧的总调度：

| 阶段 | 内容 | 位置 |
| --- | --- | --- |
| 准备 | 相机、雾、清屏、投影 | 方法开头 |
| 天空 | `renderSky` | `:1587` |
| 区块（不透明批次） | `RenderType.solid()` / `cutoutMipped()` / `cutout()` | `:965`–`:969` |
| 实体 | `EntityRenderDispatcher` 遍历 | 中段 |
| 区块（半透明批次） | `RenderType.translucent()` / `tripwire()` | `:1177`、`:1196` |
| 云 | `renderClouds` | `:1712` |
| 之后 | 天气、手、GUI（`GameRenderer` 接着做） | — |

### 1.3 能插的地方：事件，不是阶段枚举

| 事件 | 位置 | 常见用途 |
| --- | --- | --- |
| `RenderLevelStageEvent` | 上表各阶段之间 | 世界内几何、粒子、光束 |
| `RenderFrameEvent.Pre/Post` | 整帧前后 | 全局资源准备 / 收尾 |
| `RenderHandEvent` | 第一人称手 | 替换手部渲染 |
| `RenderGuiEvent` / `RegisterGuiLayersEvent` | GUI | HUD 元素 |
| `ViewportEvent` / `ComputeFovModifierEvent` | 相机 | 抖动、视场角 |

`RenderLevelStageEvent.Stage` 的完整列表见 §2.7。Photon 用的是 `AFTER_BLOCK_ENTITIES` 与
`AFTER_PARTICLES`（`PhotonClientListeners.java:93`–`:104`）。

### 1.4 与 26.2 的差别（写代码前先看这张表）

| 关注点 | 1.21.1 | 26.2 |
| --- | --- | --- |
| 渲染模型 | 立即模式（写顶点 → endBatch） | 三段式（extract / submit / render） |
| 渲染数据容器 | 没有；自己缓存 | `RenderState` + `DataTicket` |
| 着色器 | core shader JSON（`.json` + `.vsh` + `.fsh`） | Java 侧渲染管线对象 |
| 渲染类型 | `RenderType` 内联全部状态 | 命名渲染类型 + `RenderPipeline` |
| GeckoLib | 4.9.3（无 RenderState） | 5.5.6（RenderState + 票据） |
| 几何接管 | `GeoRenderIntercept` 空实现 | 自研提交接管 + GPU 蒙皮 |
| 后处理 | `PostChain` / `PostPass` + `shaders/post/*.json` | 自研后处理管线 |

---

## 2. Blaze3D API 地图

### 2.1 顶点侧（`com.mojang.blaze3d.vertex`）

| 类 | 职责 | 关键方法 / 行号 |
| --- | --- | --- |
| `VertexConsumer` | 顶点契约 | `addVertex` `:16`、`setColor` `:18`、`setUv` `:20`、`setUv1` `:22`、`setUv2` `:24`、`setNormal` `:26` |
| `BufferBuilder` | 组装一批顶点 | `addVertex` `:243`、`buildOrThrow()` |
| `MeshData` | 组装完的顶点数据 | `:14` |
| `BufferUploader` | 上传并绘制 | `drawWithShader` `:24`、`draw` `:37` |
| `PoseStack` | 变换栈 | `pushPose` `:57`、`popPose` `:61`、`last()` `:65` |
| `VertexFormat` / `DefaultVertexFormat` | 顶点元素顺序 | `POSITION_COLOR_TEX_LIGHTMAP` 等预设 |
| `Tesselator` | 借出 `BufferBuilder` | `begin(mode, format)` |

### 2.2 状态侧（`com.mojang.blaze3d.systems.RenderSystem`）

| 方法 | 行号 | 说明 |
| --- | --- | --- |
| `setShader(Supplier<ShaderInstance>)` | `:700` | 换着色器 |
| `setShaderTexture(int, ResourceLocation)` | `:714` | 绑采样器贴图 |
| `setShaderColor(float×4)` | `:418` | 顶点色乘子 |
| `enableBlend()` | `:196` | 开混合 |
| `blendFunc(...)` | `:206` | 混合方程 |
| `depthMask(boolean)` | `:191` | 深度写入 |
| `recordRenderCall(RenderCall)` | `:127` | 渲染线程排队执行 |

### 2.3 渲染类型侧（`net.minecraft.client.renderer`）

```java
RenderType.create(name, format, mode, bufferSize, compositeState)                        // RenderType.java:1125
RenderType.create(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, state) // :1131
```

`CompositeState` 的常用部件：

| 部件 | 取值示例 |
| --- | --- |
| 着色器 | `new RenderStateShard.ShaderStateShard(() -> shader)` |
| 混合 | `TRANSLUCENT_TRANSPARENCY` / `ADDITIVE_TRANSPARENCY` / `NO_TRANSPARENCY` |
| 深度 | `LEQUAL_DEPTH_TEST` / `NO_DEPTH_TEST` |
| 剔除 | `CULL` / `NO_CULL` |
| 光照 | `LIGHTMAP` / `NO_LIGHTMAP` |
| 覆盖层 | `OVERLAY` / `NO_OVERLAY` |
| 写入 | `COLOR_WRITE` / `COLOR_DEPTH_WRITE` |

### 2.4 多缓冲侧（`MultiBufferSource`）

```java
VertexConsumer vc = bufferSource.getBuffer(renderType);          // :26
// …… 写顶点
((MultiBufferSource.BufferSource) bufferSource).endBatch();      // :76
((MultiBufferSource.BufferSource) bufferSource).endLastBatch();  // :69
```

`BufferSource` 按 `RenderType` 分桶，遇到**换桶**或显式 `endBatch` 时才真正画。
所以「写完顶点忘了 `endBatch`」= 什么都不显示；「在两个 `RenderType` 之间来回写」= 状态切换爆炸。

### 2.5 光照与贴图

| 类 | 用途 |
| --- | --- |
| `LightTexture` | `pack(blockLight, skyLight)` → 光照值，喂给 `setUv2` |
| `TextureManager` / `AbstractTexture` | 贴图对象 |
| `TextureAtlasSprite` | 图集精灵（粒子 / 方块贴图） |
| `Material` / `RenderMaterial` | 图集 + 贴图路径的组合 |

### 2.6 平台侧（`com.mojang.blaze3d.platform`）

| 类 | 用途 |
| --- | --- |
| `GlStateManager` | 底层 GL 状态（剔除、混合、深度、模板、剪裁） |
| `Window` | 分辨率、缩放、帧缓冲尺寸 |
| `GlDebug` | GL 调试输出 |

### 2.7 NeoForge 渲染事件清单（21.1.250）

`net/neoforged/neoforge/client/event/` 下与本主题相关的常用事件：

| 事件 | 总线 | 用途 |
| --- | --- | --- |
| `RenderLevelStageEvent` | 游戏 | 世界内分阶段渲染（阶段：`AFTER_SKY`、`AFTER_SOLID_BLOCKS`、`AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS`、`AFTER_CUTOUT_BLOCKS`、`AFTER_ENTITIES`、`AFTER_BLOCK_ENTITIES`、`AFTER_TRANSLUCENT_BLOCKS`、`AFTER_TRIPWIRE_BLOCKS`、`AFTER_PARTICLES`、`AFTER_WEATHER`、`AFTER_LEVEL`） |
| `RenderFrameEvent` | 游戏 | 每帧前后 |
| `EntityRenderersEvent.RegisterRenderers` | mod | 注册实体 / 方块实体渲染器 |
| `EntityRenderersEvent.RegisterLayerDefinitions` | mod | 模型层定义 |
| `RegisterShadersEvent` | mod | 注册 core shader（**1.21.1 特有**） |
| `RegisterNamedRenderTypesEvent` | mod | 命名渲染类型 |
| `RegisterParticleProvidersEvent` | mod | 粒子工厂 |
| `RegisterGuiLayersEvent` | mod | HUD 层 |
| `RegisterMenuScreensEvent` | mod | 菜单界面 |
| `RegisterClientReloadListenersEvent` | mod | 资源重载监听（GeckoLib 资源缓存挂这儿） |
| `RegisterColorHandlersEvent` | mod | 物品 / 方块着色 |
| `RegisterClientCommandsEvent` | 游戏 | 客户端命令 |

---

## 3. 着色器、渲染管线与 GPU 数据

### 3.1 core shader 三件套

```text
assets/<ns>/shaders/core/mygen.json    ← 装配说明（属性、采样器、uniform、混合）
assets/<ns>/shaders/core/mygen.vsh     ← 顶点着色器
assets/<ns>/shaders/core/mygen.fsh     ← 片元着色器
```

JSON 的关键字段：`blend`、`vertex`、`fragment`、`attributes`、`samplers`、`uniforms`。
声明的 uniform 会被反序列化成 `Uniform`；没声明的也能用 `getUniform("名字")` 拿到。
`ShaderInstance` 内置的一批熟面孔（`ShaderInstance.java:163` 起）：

| uniform | 含义 |
| --- | --- |
| `ModelViewMat` | 模型视图矩阵 |
| `ProjMat` | 投影矩阵 |
| `TextureMat` | 贴图矩阵（UV 动画） |
| `ScreenSize` | 屏幕尺寸 |
| `ColorModulator` | 顶点色乘子（来自 `setShaderColor`） |
| `Light0_Direction` / `Light1_Direction` | 方向光 |
| `FogStart` / `FogEnd` / `FogColor` / `FogShape` | 雾 |
| `LineWidth` | 线宽 |
| `GameTime` | 游戏时间（秒） |
| `ChunkOffset` | 区块偏移 |

### 3.2 `#moj_import`

`GlslPreprocessor`（`com/mojang/blaze3d/preprocessor/GlslPreprocessor.java:20`）识别两种写法：

```glsl
#moj_import <minecraft:fog.glsl>     // 命名空间路径
#moj_import "local.glsl"             // 相对路径
```

它在编译前做文本展开（`:68`）；导入失败会把 GLSL 错误原样抛进 `ShaderInstance` 的编译日志。

### 3.3 注册自己的 `ShaderInstance`

```java
@EventBusSubscriber(modid = "minegenshin", bus = EventBusSubscriber.Bus.MOD)
public final class MyShaders {
    public static ShaderInstance GLOW;

    @SubscribeEvent
    static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(
            new ShaderInstance(event.getResourceProvider(), "minegenshin:glow",
                               DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP),
            s -> GLOW = s);
    }
}
```

要点：

- `RegisterShadersEvent` 是 **mod 总线**事件（`RegisterShadersEvent.java:27`，实现 `IModBusEvent`）；
- 资源重载会重新触发它，**不要在静态初始化里锁死引用**，用供应商（lazy）；
- `registerShader(instance, onLoaded)` 的第二个参数是唯一安全的赋值点。

### 3.4 接到 `RenderType`

```java
private static final RenderType MY_TYPE = RenderType.create(
    "minegenshin_glow",
    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
    VertexFormat.Mode.QUADS, 1536, false, true,
    RenderType.CompositeState.builder()
        .setShaderState(new RenderStateShard.ShaderStateShard(() -> MyShaders.GLOW))
        .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
        .setCullState(RenderStateShard.NO_CULL)
        .setLightmapState(RenderStateShard.LIGHTMAP)
        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
        .createCompositeState(false));
```

### 3.5 逐顶点数据 vs uniform

| 方式 | 粒度 | 什么时候用 |
| --- | --- | --- |
| 顶点属性（位置 / 颜色 / UV / 光照） | 每顶点 | 位置、颜色、UV、法线 |
| uniform | 每次绘制 | 时间、屏幕尺寸、材质参数 |
| 贴图 | 采样 | 渐变、噪声、遮罩 |

1.21.1 的 core shader **没有 UBO / std140 那套**：多个 uniform 就是多次 `set`。

### 3.6 后处理

```json
// assets/<ns>/shaders/post/blur.json
{
  "targets": [ "swap" ],
  "passes": [ { "name": "minegenshin:blur", "intarget": "minecraft:main",
                "outtarget": "swap", "uniforms": [ { "name": "Radius", "values": [ 2.0 ] } ] } ]
}
```

运行时由 `PostChain` / `PostPass` 驱动（`PostChain.java:31`、`addPass` `:281`、`process` `:313`）。
Photon 的 Bloom 与自定义后处理都建立在这套之上（§8.6）。

### 3.7 排错

| 症状 | 原因 |
| --- | --- |
| 采样器全黑 | `samplers` 名字与 `setSampler` 名字不一致 |
| 顶点不动 | `ModelViewMat` 未在 JSON 声明 |
| 一次性生效、重载后失效 | 缓存了旧 `ShaderInstance` |
| GLES / 移动启动器报错 | 整数与浮点混算（`1` 要写 `1.0`） |

---

## 4. 实体渲染与「没有 RenderState」的世界

### 4.1 `EntityRenderer` 的契约

```java
public abstract class EntityRenderer<T extends Entity> {                                  // :26
    public boolean shouldRender(T entity, Frustum frustum, double x, double y, double z)  // :52
    public void render(T entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) // :89
    protected void renderNameTag(...)                                                     // :185
}
```

26.2 会先 extract 出 `RenderState` 再渲染；1.21.1 没有这一步，
所以「数据放哪儿」是最需要想清楚的问题：

| 放哪儿 | 什么时候更新 | 代价 |
| --- | --- | --- |
| 实体字段 | 服务端 / 客户端 tick | 同步成本；客户端只读的字段要在 render 里算 |
| 渲染器里的 `Map<实体, 数据>` | 渲染时懒更新 | 必须处理实体卸载，否则内存泄漏 |
| 全局缓存 + 实体 id | tick 写、渲染读 | 需要自己定义失效规则 |

### 4.2 注册渲染器

```java
@SubscribeEvent
static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
    event.registerEntityRenderer(ModEntities.MY_ENTITY.get(), MyRenderer::new);
}
```

方块实体用 `event.registerBlockEntityRenderer(...)`；物品用 `BlockEntityWithoutLevelRenderer`。

### 4.3 实体朝向与插值

```java
float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
float headYaw = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
float ageInTicks = entity.tickCount + partialTick;
```

`*O` 是上一刻的值，`rotLerp` 负责刻内插值 —— 少了它就能看到一格一格的转向。

### 4.4 本项目 1.21.1 的实体渲染链

```text
EntityRenderersEvent.RegisterRenderers（MinegenshinClient）
  ├─ 普通实体渲染器（ElementalOrbRenderer / StellarVortexRenderer / IceBlockProjectileRenderer …）
  └─ GeckoLib 模型实体
       └─ GeoEntityRenderer + CategoryGeoModel（资源按「类别 + id」解析）
```

资源解析顺序：`AssetGeoCache`（本 MOD 统一布局）→ `GenshinGeoCache`（角色 / GeckoLib 原生根）
→ GeckoLib 自带缓存。三者都注册进 `RegisterClientReloadListenersEvent`，
所以资源包重载后模型 / 动画 / 贴图会一起刷新。

---

## 5. GeckoLib 4：骨骼动画与渲染层

### 5.1 类地图（4.9.3）

| 用途 | 类 |
| --- | --- |
| 渲染器基类 | `software.bernie.geckolib.renderer.GeoRenderer` |
| 实体渲染器 | `...renderer.GeoEntityRenderer` |
| 摆件 / 通用对象 | `...renderer.GeoObjectRenderer` |
| 被替换实体 | `...renderer.GeoReplacedEntityRenderer` |
| 渲染层 | `...renderer.layer.GeoRenderLayer` |
| 模型 | `...model.GeoModel` / `DefaultedEntityGeoModel` 等 |
| 骨骼 | `...cache.object.GeoBone` / `BakedGeoModel` |
| 动画控制 | `...animation.AnimationController` / `AnimatableManager` / `AnimationState` / `RawAnimation` / `PlayState` |
| 骨骼快照 | `...animation.state.BoneSnapshot` |
| 缓存 | `...cache.GeckoLibCache` / `...cache.texture.AnimatableTexture` |
| 事件 | `...event.GeoRenderEvent` / `GeckoLibEventsNeoForge` |

### 5.2 一趟渲染

```text
GeoEntityRenderer#render(...)                    :197
 └─ GeoRenderer#defaultRender(...)              :126
     ├─ preRender(...)                          :232
     ├─ actuallyRender(...)                     :178   ← 摆位、算朝向（GeoEntityRenderer 覆写）
     ├─ renderRecursively(...)                  :260   ← 逐骨骼
     ├─ applyRenderLayers(...)                  :220   ← 逐层
     └─ postRender(...)                         :240
```

`GeoRenderEvent.Entity.Pre`（`:356`）可取消渲染；
`GeoRenderEvent.Entity.CompileRenderLayers`（`:442`）用来挂层 —— 项目里的武器挂点层从这里上去。

### 5.3 动画：`AnimatableManager` 与 `AnimationController`

```java
@Override
public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    controllers.add(new AnimationController<>(this, "main", 5, this::animState));
}

private PlayState animState(AnimationState<MyEntity> state) {
    return state.setAndContinue(RawAnimation.begin().thenLoop("idle"));
}
```

- `AnimatableManager.ControllerRegistrar#add(AnimationController...)`（`AnimatableManager.java:175`）；
- `AnimationController#setAnimation(RawAnimation)`（`:361`）；
- `AnimationController#getStateHandler()`（`:271`）是状态判断的入口；
- 手动触发一次性动画：`tryTriggerAnimation("名字")`（`:123` / `:136`）；
- **`PlayState` 只有 `CONTINUE` 与 `STOP`**：想「暂停」得用速度控制（§5.6）。

### 5.4 骨骼：只有渲染那一瞬间存在

`GeoBone`（`cache/object/GeoBone.java:19`）暴露的是**当下这一刻**的值：

| 方法 | 含义 |
| --- | --- |
| `getRotX/Y/Z` `:86`–`:94` | 弧度 |
| `getPosX/Y/Z` `:98`–`:106` | 模型单位 |
| `getScaleX/Y/Z` `:110`–`:118` | 缩放 |
| `getPivotX/Y/Z` `:226`–`:234` | 枢轴 |
| `isHidden` / `setHidden` `:194` / `:198` | 显隐 |

想在渲染之外读，用快照：

```java
BoneSnapshot snap = new BoneSnapshot(bone);   // animation/state/BoneSnapshot.java:35
snap.getRotX(); snap.getOffsetY();            // :97 / :89
```

`AnimatableManager#getBoneSnapshotCollection()`（`:62`）保存上一轮的快照，
`clearSnapshotCache()`（`:66`）清理。**不要**在 tick 里直接读 `GeoBone` —— 那是上一帧的残值。

### 5.5 写一个渲染层

```java
public final class MuzzleGlowLayer<T extends Entity & GeoAnimatable>
        extends GeoRenderLayer<T> {

    public MuzzleGlowLayer(GeoRenderer<T> renderer) { super(renderer); }

    @Override
    public void renderForBone(PoseStack poseStack, T animatable, GeoBone bone,
                              RenderType renderType, MultiBufferSource bufferSource,
                              VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        if (!"muzzle".equals(bone.getName())) return;
        poseStack.pushPose();
        poseStack.translate(0, 0.05, 0);
        // …… 写顶点
        poseStack.popPose();
    }
}
```

覆盖 `getRenderType`（`GeoRenderer.java:69`）可以换掉整层的 `RenderType`；
覆盖 `getTextureResource`（`GeoRenderLayer.java:53`）可以换层自己的贴图。

### 5.6 GeckoLib 4 的坑

| 坑 | 事实 | 对策 |
| --- | --- | --- |
| 没有 `PlayState.PAUSE` | `PlayState.java` 只有 `CONTINUE` / `STOP` | 冻结用 `controller.setAnimationSpeed(0)`；解冻再 `setAnimationSpeed(1)` + `forceAnimationReset()` |
| `GeoObjectRenderer` 偏半格 | 父类给方块原点模型加了 `translate(+0.5, +0.51, +0.5)` | 覆盖 `preRender` 反向平移（项目里 `CharacterRenderer` 的做法） |
| 动画不播 | 键名不匹配；或自定义 `getBakedModel` 绕过了骨骼登记 | 核对 `.animation.json` 键名与解析链 |
| 模型动但位置不对 | 骨骼枢轴与模型导出原点不一致 | 在模型文件里改，不要在运行时叠加补偿 |
| 4 与 5 的钩子名不同 | 5.x 的 `getBakedAnimation` 等命名在 4.x 不存在 | 移植时按 4.9.3 的源码逐个核 |

---

## 6. 坐标空间、矩阵与四元数

### 6.1 五个空间与换算

| 空间 | 原点 | 单位 |
| --- | --- | --- |
| 世界 | 世界原点 | 方块 |
| 相机 | 相机 | 方块 |
| 实体 | 实体脚底 | 方块 |
| 模型 | 模型根 | 1/16 方块 |
| 屏幕 | 窗口左上 | 像素 |

```text
模型 → 实体： 模型坐标 / 16，经骨骼链变换，加骨骼挂点偏移
实体 → 世界： 世界 = 实体坐标 + 变换后的局部坐标
世界 → 相机： 相机 = 世界 - 相机坐标
```

### 6.2 度与弧度

| 位置 | 单位 |
| --- | --- |
| 实体朝向、`Mth.rotLerp` | 度 |
| `Quaternionf.rotationXYZ` / `mulPose` | 弧度（四元数） |
| GeckoLib `.geo.json` / `.animation.json` | 度（加载期换算） |
| GeckoLib 运行时 `GeoBone#getRotX` | 弧度 |
| Photon `IFXEffectExecutor#setRotation` | 度（内部转四元数，`IFXEffectExecutor.java:32`） |

### 6.3 矩阵与四元数

```java
Matrix4f m = poseStack.last().pose();
Vector3f v = new Vector3f(x, y, z);
m.transformPosition(v);                        // 局部 → 上一级
poseStack.mulPose(new Quaternionf().rotationXYZ(rx, ry, rz));
```

顺序：**平移 → 旋转 → 缩放**（模型习惯），骨骼链也是这个顺序；
`poseStack` 是右乘，你写代码的顺序就是「从外到内」。

### 6.4 症状对照

| 症状 | 原因 |
| --- | --- |
| 模型上下颠倒 | 模型空间与世界空间的 X 轴反向 |
| 旋转方向反 | 四元数欧拉序（XYZ vs ZYX） |
| 位置对、朝向差 90° | 把 `yRot`（度）当弧度用了 |
| 模型与判定箱错位 | 原点补偿问题（§5.6） |

---

## 7. GPU 蒙皮与渲染性能

### 7.1 1.21.1 的现状

26.2 那套「接管 GeckoLib 几何提交 + GPU 蒙皮」在 1.21.1 上**尚未接入**：

```java
// client/render/optimize/GeoRenderIntercept.java（1.21.1 分支）
public static boolean trySubmit(Object renderPassInfo, Object renderTasks, Object renderType) {
    return false;   // 本实现不自己提交任何几何
}
```

1.21.1 的角色渲染走 GeckoLib 默认的 CPU 路径：逐骨骼遍历、逐顶点写。
**不要**把 26.2 的性能数字搬到这里。

### 7.2 默认路径的成本结构

```text
每个角色每帧：
  骨骼遍历（N 根 × 层级深度）
    → 每根骨骼算一次矩阵
      → 每个立方体的每个顶点做一次变换 → 写进 VertexConsumer
```

成本与「骨骼数 × 顶点数 × 角色数」成正比。可用的优化：

1. **减骨骼 / 减面**（模型侧最有效）；
2. **缓存每帧不变的东西**（贴图、光照、`RenderType` 引用）；
3. **同材质批量写**（减少 `RenderType` 切换）；
4. **粗筛剔除**（谨慎使用，宁可少剔也别误剔）；
5. **限制同屏角色数**（图鉴 / 摆件预览最容易失控）。

### 7.3 间接开销

| 项目 | 说明 |
| --- | --- |
| 资源解析 | `AssetGeoCache` / `GenshinGeoCache` 只在资源重载时重建，不要每帧查 |
| HUD / 飘字 | 走独立 HUD 层与缓存（项目里 `performance/Indicator*`） |
| Photon 粒子 | §8.7：粒子数量与材质档位直接决定 GPU 时间 |
| 动态光 | 2.2.8 新增，按盏计费；`shadowed_lights` 默认 8 盏 |

### 7.4 测量

| 工具 | 用途 |
| --- | --- |
| `F3` 帧时间 | 判断是逻辑还是渲染 |
| `FrameTimeStats` | 项目内的帧时间统计 |
| `LightPassTimer` / `LightDebug` | Photon 动态光耗时与可视化 |
| Photon 编辑器 | 单独预览某个 `.fx` 的开销 |

---

## 8. Photon2 2.2.8：从编辑器到运行时

> **版本澄清**：2.2.8 新增的是**动态光、体积雾、KilaMaterial、材质导出成图、自定义空间、
> FX 对象复制粘贴**；它**没有** GeckoLib 集成 —— `geckolib` / `bernie` 在 2.2.8 的
> sources jar、all.jar 和上游提交历史里的命中数都是 0。1.21.1 的 GeckoLib 是 4.9.3（§5）。

### 8.1 资源布局

```text
assets/<ns>/fx/<name>.fx        ← 一个特效工程（对象树 + 时间轴 + 引用）
assets/photon/…                 ← 编辑器资源（材质、曲线、渐变、网格、贴图）
config/photon-client.toml       ← 客户端配置
```

`FXHelper`（`client/fx/FXHelper.java:34`）：`FX_PATH = "fx/"`（`:41`）、
`getFX(location)`（`:51`）、`listAllFX()`（`:63`）、`clearCache()`（`:43`）。

### 8.2 编辑器

| 面板 | 内容 |
| --- | --- |
| 场景视图 | 对象树可视化编辑、gizmo、网格与辅助线 |
| 时间轴 | 动画 / 子片段 / 声音 / 曲线 / 渐变 / 表达式 / 信号 / 后处理 / 速度 轨道 |
| 资源面板 | 材质、曲线、渐变、网格、贴图、fx 包 |
| Inspector | 选中对象的全部参数 |

编辑器也提供导出（`client/fx/fxpack/FXPackExporter.java`、`FXPacks.java`），
可以把工程打包成 fx 包分发。

### 8.3 渲染入口

Photon 2.2.8 在 1.21.1 上挂在 NeoForge 事件上（`client/PhotonClientListeners.java`）：

| 事件 | 行号 | 用途 |
| --- | --- | --- |
| `ClientTickEvent.Post` | `:76` | 推进 tick、更新动态光 |
| `RenderFrameEvent.Post` | `:47` | 每帧收尾 |
| `RenderLevelStageEvent`（`AFTER_BLOCK_ENTITIES` / `AFTER_PARTICLES`） | `:93`–`:104` | 世界内渲染 FX |
| `RenderGuiEvent.Post` | `:111` | 编辑器预览 / 叠加 |
| `LevelEvent.Unload` | `:68` | 卸载关卡清场 |
| `ChunkEvent.Load` / `TagsUpdatedEvent` | `:53` / `:60` | 动态光体素世界、资源重载 |

粒子由 `PhotonParticleManager`（`client/PhotonParticleManager.java`）承载，
它继承 LDLib2 的 `com.lowdragmc.lowdraglib2.client.scene.ParticleManager`。
`FXObject` 直接 `extends Particle`（`client/gameobject/FXObject.java:38`），
所以 Photon 的粒子会走原版粒子的排序与渲染管线。

### 8.4 材质体系

统一接口是 `IMaterial`，内置实现：

| 实现 | 说明 |
| --- | --- |
| `TextureMaterial` / `SpriteMaterial` / `BlockTextureSheetMaterial` | 普通贴图 / 图集精灵 |
| `CustomShaderMaterial` / `ShaderInstanceMaterial` | 自定义着色器 / 已有 `ShaderInstance` |
| `ShaderGraphMaterial` | 着色器图材质 |
| **`KilaMaterial`** | 2.2.8 新增的一体式 VFX 材质 |

KilaMaterial 把主贴图、遮罩、溶解、折射、菲涅尔、MatCap、UV 特效、顶点偏移、投影等
做成模块（`.../material/kila/Kila*.java`），`KilaPresets.ALL`（`KilaPresets.java:515`）
给出 **27 个按类别分组的预设**，还能反向导出成着色器图
（`kila/export/KilaGraphExport.java`）。

### 8.5 模型源与动态网格注入

| 注册名 | 类 | 说明 |
| --- | --- | --- |
| `json_model` | `JsonModelSource` | 已烘焙的 Minecraft JSON 模型 |
| `obj_model` | `ObjModelSource` | OBJ 网格 |
| `gltf_model` | `GltfModelSource` | glTF（含切线） |
| `animated_gltf` | `AnimatedGltfModelSource` | 带骨骼与动画的 glTF（2.2.7 起） |
| `dynamic_mesh` | `DynamicMeshSource` | 动态网格注入 |

`IDynamicMesh`（`.../model/IDynamicMesh.java`）就是「几何在画的时候还在变」这条通道：

```java
PhotonMesh topology();   // 不变的拓扑；同一实例复用
long revision();         // 内容变了才 +1（从 1 开始，0 = 静止姿势）
float[] geometry();      // 该版本的 positions + normals（可空）
int glBuffer();          // 或者直接给 GL buffer
```

> 「别的动画库能不能接进来」的答案就在这里：谁把骨骼动画算成顶点流，谁就能当模型源用。
> GeckoLib 并没有替你做这件事，需要用 `IDynamicMesh` 写一个适配器。

### 8.6 后处理与光影兼容

| 类 | 作用 |
| --- | --- |
| `client.postprocessing.PhotonPostProcessing` | 渲染目标、blit、Bloom |
| `client.postfx.runtime.PostFXCamera` | 时间轴后处理片段用的相机口径 |
| `client.gameobject.emitter.renderpipeline.StackedDistortion` | 叠加式扭曲 |
| `client.compat.iris.*` | Iris 兼容（`enable_bloom_with_iris_shader`、`iris_use_translucent_particle_program`） |

配置项（`PhotonConfig.java:75` 起）：`enable_bloom`、`bloom_mip_level`、`bloom_threshold`、
`bloom_intensity`、`enable_custom_effects`、`enable_custom_effects_with_shader_pack`、
`postfx_pool_budget_mb`（默认 256 MB）。

### 8.7 动态光与体积雾（2.2.8 新增）

```java
PhotonLights.add(light)                        // :44
PhotonLights.remove(light)                     // :48
PhotonLights.attach(entity, template, offset)  // :83  返回 AutoCloseable 句柄
PhotonLights.flash(template, durationTicks)    // :95
PhotonLights.addFog(fogVolume)                 // :62
PhotonLights.addProvider(provider)             // :53
```

```java
new DynamicLight()                 // client/light/DynamicLight.java:13
    .point()                       // 或 .spot(innerAngle, outerAngle)
    .at(x, y, z)
    .color(1f, 0.8f, 0.4f)
    .intensity(8f)
    .range(10f)
    .shadows(true)
    .volumetric(0.5f);
```

配置（`PhotonConfig.java:99` 起）：`dynamic_light.enabled`、`soft_shadows`、
`shadowed_lights`（默认 8）、`resolution`、`contact_shadow_steps`、
`screen_shadow_steps`、`voxel_budget_ms`（默认 2ms，超预算自动降级）、
`volumetric`、`volumetric_density`、`volumetric_samples`（默认 4）。

### 8.8 关键事实速查

```text
mod id          photon
FX 资源         assets/<ns>/fx/<name>.fx
编辑器资源根    LDLib2 的 assets/photon/
客户端配置      config/photon-client.toml
命令            /photon（清缓存、预览、Photon 1 → 2 转换）
```

---

## 9. Photon2 Java API 与运行时注入

### 9.1 三件套：`FX` / `FXRuntime` / `IEffectExecutor`

```text
FX              一份 .fx 工程（静态定义）
FXRuntime       一次播放实例（有生命周期、有 root）
IEffectExecutor 播放上下文（挂在谁身上、每刻/每帧怎么更新）
```

```java
FX fx = FXHelper.getFX(ResourceLocation.fromNamespaceAndPath("minegenshin", "skill_burst"));
var executor = new EntityEffectExecutor(fx, level, player, EntityEffectExecutor.AutoRotate.LOOK);
executor.setOffset(0, 1.0, 0);
executor.setRotation(0, 0, 0);   // 度
executor.setScale(1, 1, 1);
executor.start();
```

`IFXEffectExecutor`（`client/fx/IFXEffectExecutor.java`）负责变换 / 延迟 / 开关，
`IEffectExecutor`（`client/fx/IEffectExecutor.java`）负责每刻与每帧回调：

| 回调 | 频率 | 该写什么 |
| --- | --- | --- |
| `updateFXObjectTick(IFXObject)` | 每刻 | 低频逻辑：锚点没了就销毁、状态切换 |
| `updateFXObjectFrame(IFXObject, float)` | 每帧 | 高频逻辑：跟随位置 / 旋转 / 缩放 |

**两个回调都写位置会互相打架**（一个按整刻跳、一个按帧插值，加起来就是抖动）。

### 9.2 内置执行器

| 类 | 用途 |
| --- | --- |
| `EntityEffectExecutor` | 跟随实体（`AutoRotate`: `NONE` / `FORWARD` / `LOOK` / `XROT`） |
| `BlockEffectExecutor` | 跟随方块位置 |
| `FXEffectExecutor` | 其它执行器的基类 |

### 9.3 时间轴信号

时间轴的 `signal` 轨道会回调到执行器：`IEffectExecutor#onTimelineSignal(channel, name, data, time)`。
全局订阅用 `client/fx/timeline/PhotonSignals.java`。这是一条「特效反过来驱动游戏逻辑」的通道：
特效播到某一刻，通知代码做一件事（放音效、加 buff、切状态）。

### 9.4 扩展点

`PhotonRegistries`（`PhotonRegistries.java`）：

| 注册表 | 类型 | 说明 |
| --- | --- | --- |
| `photon:fx_object` | `FXObjectType` | 自定义 FX 对象类型 |
| `photon:material` | `IMaterial` | 自定义材质 |
| `photon:number_function` | `NumberFunction` | 曲线 / 表达式函数 |
| `photon:shape` | `IShape` | 发射形状 |
| `photon:model_source` | `IModelSource` | 模型源 |
| `photon:timeline_track` | `TrackType` | 时间轴轨道类型 |
| `photon:animated_property` | `AnimatedPropertyType` | 可动画属性 |

注册用 LDLib2 的注解，例如
`@LDLRegisterClient(name = "json_model", registry = "photon:model_source")`。

### 9.5 生命周期

- `FXRuntime#isValid()` 决定实例该不该活；
- 存活判据必须是**单调递增**的 tick 计数 —— 不要用 `PhotonParticleManager` 的
  `time`（时间轴时钟，`clear()` 会重置）；
- 关卡卸载 / 资源重载：`FXHelper.clearCache()`（`:43`）+ `/photon clear client cache fx`；
- 编辑器里的预览用的是隔离的执行器与场景栈，不要拿它当运行时用法。

---

## 10. 项目实战

### 10.1 本项目 1.21.1 的四种特效时机

`client/fx` 目录就是这四种时机的范例（见 `TestCharacterFx` 的类注释）：

| # | 时机 | 锚点 | 用到的 API |
| --- | --- | --- | --- |
| 1 | 常驻，跟随角色 | 角色本身 | `EntityEffectExecutor` |
| 2 | 常驻，跟随手中武器 | 武器骨骼 | `FxAnchor` + 武器挂点层 |
| 3 | 只在攻击动画期间 | 武器尖 | `FxAnchor` + `ActionStateMachine` |
| 4 | 技能释放后定点飞出 | 世界坐标 | `FixedPointExecutor`（`IEffectExecutor`） |

### 10.2 定点飞出的写法

```text
tick  ：origin += forward × speed                       （推进一个整刻）
frame ：pos = origin + forward × speed × partialTicks   （刻内插值，不抖）
```

速度在客户端算、位置由执行器给，特效本身把模拟空间设成 `WORLD` 即可；
反过来把位移全写在特效里、`forwardSpeed` 传 0 也成立，两种都由特效作者决定。

### 10.3 角色渲染器：原点与骨骼规则

`client/render/character/CharacterRenderer` 继承 `GeoObjectRenderer`：

```java
public void performRenderPass(GenshinReplacedPlayer animatable, @Nullable Player related,
                              PoseStack poseStack, MultiBufferSource bufferSource,
                              int packedLight, float partialTick,
                              @Nullable BoneUpdater<BoneRenderState> boneUpdater) {
    this.animatable = animatable;
    this.pendingUpdater = boneUpdater;
    render(poseStack, animatable, bufferSource, null, null, packedLight, partialTick);
}
```

两件必知的事：

1. **原点**：`preRender` 里反向 `translate(-0.5F, -0.51F, -0.5F)` 抵消 `GeoObjectRenderer`
   的摆件补偿，否则模型整体偏到斜后方半格，和判定箱对不上；
2. **骨骼规则**：本帧的 `BoneUpdater` 在 `preRender` 里作用于烘焙模型，
   改好的骨骼状态在本次渲染中一直有效。

### 10.4 资源解析链

```text
CategoryGeoModel（类别 + id）
  └─ AssetGeoCache（本 MOD 统一布局：assets/minegenshin/<类别>/<id>/…）
       └─ GenshinGeoCache（角色目录 / GeckoLib 原生根）
            └─ GeckoLib 自带缓存
```

三个缓存都是 `RegisterClientReloadListenersEvent` 的监听器；
资源包重载 = 模型 / 动画 / 贴图一起刷新，不要在别处再存一份路径。

### 10.5 服务端触发

客户端特效必须由客户端发起。服务端的做法是发网络包（本项目的 RPC 走 LDLib2），
客户端收到后构造 `FX` 与执行器。**服务端不要 import 任何 `client` 包。**

---

## 11. 排错手册与性能

### 11.1 按症状查

| 症状 | 优先怀疑 |
| --- | --- |
| 特效不出现 | `.fx` 路径 / 命名空间写错；`FXHelper` 缓存没清；资源重载后没重建 |
| 只出现一次 | `FXRuntime` 被提前 `remove(force=true)` / `retire` |
| 位置抖动 | tick 与 frame 都写位置（§9.1） |
| 位置偏 | 摆件 +0.5 平移（§5.6）；模型以方块角为原点 |
| 光影下黑掉 | `enable_custom_effects_with_shader_pack` 关着；Iris 接管了管线 |
| 模型不播动画 | GeckoLib 4 的键名 / 模型供给链（§5.6） |
| 动态光不亮 | `dynamic_light.enabled=false`；`shadowed_lights` 用满；光源在相机背后 |
| 一进世界就掉帧 | 动态光 / 体积雾开着且盏数多；`voxel_budget_ms` 太松 |

### 11.2 常用命令与开关

```text
F3                                  帧时间
/photon clear client cache fx       清 .fx 解析缓存
/photon … convert                   Photon 1 → 2 的 fx 转换（权限 2）
config/photon-client.toml           客户端配置
```

### 11.3 性能预算参考

| 项 | 建议上限 | 依据 |
| --- | --- | --- |
| 同屏角色模型 | 依模型面数而定，先测 10 个同模型 | §7.2 CPU 路径 |
| 动态光（带阴影） | ≤ 8 盏 | `shadowed_lights` 默认 8 |
| 体积光采样 | 4（默认） | `volumetric_samples` |
| 动态光体素预算 | 2ms | `voxel_budget_ms` |
| 后处理池 | 256 MB | `postfx_pool_budget_mb` |

---

## 12. 附录

### 12.1 类名速查

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

### 12.2 两条线的类名对照

| 概念 | 1.21.1 | 26.2 |
| --- | --- | --- |
| 资源 ID | `ResourceLocation` | `Identifier` |
| 实体渲染 | `EntityRenderer#render(entity, yaw, partialTick, poseStack, buffers, light)` | RenderState 体系 |
| 存档序列化 | `serializeNBT(Provider)` / `CompoundTag` | `ValueOutput` / `ValueInput` |
| GeckoLib | 4.9.3（`software.bernie.geckolib`） | 5.5.6（`com.geckolib`） |
| Photon | 2.2.8（`photon-neoforge-1.21.1`） | 26.2.2.3（`photon-neoforge-26.2`） |
| LDLib2 | 2.2.42 | 26.2.2.42 |

### 12.3 参考

| 事实来源 | 位置 |
| --- | --- |
| MC / NeoForge | `build/moddev/artifacts/neoforge-21.1.250-sources.jar` |
| Photon | `photon-neoforge-1.21.1-2.2.8-sources.jar` |
| LDLib2 | `ldlib2-neoforge-1.21.1-2.2.42-sources.jar` |
| GeckoLib | `geckolib-neoforge-1.21.1-4.9.3-sources.jar` |
| Photon 2.2.8 变更 | Modrinth `photon-editor` 的 `mc1.21.1-2.2.8-neoforge` 版本 changelog |
| 项目代码 | 本仓库 1.21.1 分支 `src/main/java/com/linweiyun/genshin/` |

### 12.4 本文未覆盖 / 未确认的部分

- 1.21.1 上的几何接管与 GPU 蒙皮未接入，§7 的性能结论只覆盖默认路径；
- Photon 编辑器内部实现（时间轴、资源面板、fx 包格式）未逐类展开；
- Iris 兼容层的细节（`IrisCompositeMode` 各档行为）未逐条核对；
- 官方文档站对 1.21.1 / Photon 2.2.x 的描述与 jar 有出入时，本文以 jar 为准，
  出入点没有逐条列出。
