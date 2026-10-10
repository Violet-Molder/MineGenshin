# 2. Blaze3D API 地图

这一章讲「1.21.1 上一次绘制从哪开始、到哪结束」，以及中间每个类**是什么、管什么、什么时候用**。
读完你应该能自己写出一段把三角形画到世界里的代码，并知道每一行为什么这么写。

## 2.1 Blaze3D 是什么

Blaze3D 是 Mojang 自己写的渲染层，夹在「游戏逻辑」和「OpenGL」之间。它存在的理由是：
原版要在 Windows / macOS / Linux、不同 GL 版本、有无光影模组的情况下画同一套东西，
所以把**状态、顶点、着色器、缓冲**都包了一层对象，游戏代码只跟对象打交道。

它在 1.21.1 上的分工是这样的：

```text
你的渲染代码
  → 描述「画什么」   VertexConsumer / BufferBuilder / MeshData
  → 描述「怎么画」   RenderType（格式 + 着色器 + 混合 + 深度 + 剔除）
  → 交给驱动          BufferUploader / RenderSystem / GlStateManager
```

**三条铁律**（后面的每一节都是这三条在不同层面的展开）：

1. 顶点按 `VertexFormat` 声明的**元素顺序**写，顺序错了不报错、画面错；
2. 「怎么画」由 `RenderType` 一次性决定，写顶点之前它就必须是对的；
3. 所有渲染调用都在**渲染线程**（就是客户端主线程），tick 里只准备数据。

## 2.2 顶点侧：谁在描述几何

### `VertexConsumer`：顶点契约

`com.mojang.blaze3d.vertex.VertexConsumer` 是一个接口，只有六个真正要你实现/调用的方法
（`VertexConsumer.java:16` 起）：

| 方法 | 参数含义 | 单位 / 取值范围 |
| --- | --- | --- |
| `addVertex(x, y, z)` | 当前坐标系里一个顶点的位置 | 方块（模型空间则是 1/16 方块） |
| `setColor(r, g, b, a)` | 顶点色，和 `setShaderColor` 相乘 | 0–255 的 int（也有 0–1 的 float 重载） |
| `setUv(u, v)` | 主贴图坐标 | 0–1 |
| `setUv1(u, v)` | 覆盖层（overlay）坐标 | 0–15（受击闪红、附魔光效） |
| `setUv2(u, v)` | 光照贴图坐标 | 0–15 的方块光 / 天空光 |
| `setNormal(x, y, z)` | 法线 | 单位向量 |

最关键的一条约定：**调用顺序必须与 `VertexFormat` 里元素的声明顺序一致**。
`DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP` 的声明顺序是位置 → 颜色 → UV → 光照，
那你就要 `addVertex(...).setColor(...).setUv(...).setUv2(...)` 这样写；
把 `setUv` 写在 `setColor` 前面，驱动不会报错，只会把 UV 当颜色用。

为什么要这么设计：顶点最终是一段**连续字节**（float 数组），
`VertexFormat` 是这段字节的「表结构」，`VertexConsumer` 只是往表里按列填值。
列的顺序错了，读的人（着色器）就按错的列去解释。

### `BufferBuilder`：把一堆顶点攒成一批

一个 `VertexConsumer` 是「一个顶点一个顶点地写」，`BufferBuilder` 是它的实现，
负责把顶点攒进一块内存，攒够了/when you say so 就交给驱动。

```java
BufferBuilder bb = Tesselator.getInstance()
        .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);  // 借一块缓冲
bb.addVertex(pose, 0, 0, 0).setColor(255, 255, 255, 255);                     // 写顶点
bb.addVertex(pose, 1, 0, 0).setColor(255, 255, 255, 255);
bb.addVertex(pose, 1, 1, 0).setColor(255, 255, 255, 255);
bb.addVertex(pose, 0, 1, 0).setColor(255, 255, 255, 255);
MeshData mesh = bb.buildOrThrow();                                            // 定型
```

要点：

- `Mode` 决定顶点怎么连：`QUADS`（四个一组，原版绝大多数面）、`TRIANGLES`（不补成四边形时要自己写三个顶点一组）、`LINES`（两个一组，调试线框）。
- `begin(...)` 借的是**共用的那块缓冲**，所以同一时刻只能有一个 `BufferBuilder` 在写；写完必须 `build*()` 收尾，否则下一次 `begin` 会抛「已在使用」。
- `addVertex(PoseStack.Pose, x, y, z)` 这个重载会用 `Pose` 里的矩阵**立刻换算**成相机空间坐标 —— 你在 `PoseStack` 上做的平移/旋转，就是靠它生效的。
- 1.21.1 里 `end()` 已经不给用了，定型方法是 `buildOrThrow()` / `buildOrNull()`，产物是 `MeshData`。

### `MeshData` 与 `BufferUploader`：定型之后交给驱动

`MeshData`（`MeshData.java:14`）是「一批已经写好的顶点 + 它是怎么画的」。

```java
BufferUploader.drawWithShader(mesh);   // 用「当前 RenderSystem 里那个着色器」画
BufferUploader.draw(mesh);             // 用 MeshData 自己带的绘制状态画
```

两者的区别就是「这批顶点用谁的着色器」：

- 走 `RenderType` + `MultiBufferSource` 的路子，用 `draw` —— 着色器由 `RenderType` 决定；
- 手写 GL 时用 `drawWithShader` —— 你得先 `RenderSystem.setShader(...)` 把着色器设好。

### `VertexFormat`：字节布局的声明

`VertexFormat` 是「一个顶点由哪些元素、按什么顺序组成」。常用的预设都在 `DefaultVertexFormat`：

| 预设 | 元素 | 用在哪 |
| --- | --- | --- |
| `POSITION` | 位置 | 最简几何、调试 |
| `POSITION_COLOR` | 位置 + 颜色 | 纯色面片、线框 |
| `POSITION_TEX` | 位置 + UV | GUI、贴图四边形 |
| `POSITION_COLOR_TEX_LIGHTMAP` | 位置 + 颜色 + UV + 光照 | 粒子、特效、世界内的面片 |
| `POSITION_COLOR_NORMAL` | 位置 + 颜色 + 法线 | 需要方向的面片（比如带光照计算） |
| `NEW_ENTITY` / `BLOCK` | 实体 / 方块模型的完整属性 | 实体、区块 |

挑格式的经验法则：**先用 `POSITION_COLOR_TEX_LIGHTMAP`**。它够画绝大多数特效，
而且能吃世界光照（`setUv2` 传 `LightTexture` 的值）。

### `Tesselator`：缓冲的借出方

`Tesselator.getInstance().begin(mode, format)` 借出一块 `BufferBuilder`。
它是单例，内部持有一块可复用的直接内存缓冲 —— 这就是为什么「同一时刻只能有一个」。

## 2.3 状态侧：`RenderSystem` 与 `GlStateManager`

`RenderSystem`（`com.mojang.blaze3d.systems.RenderSystem`）是「当前渲染状态」的门面：

| 方法 | 行号 | 作用 | 什么时候用 |
| --- | --- | --- | --- |
| `setShader(Supplier<ShaderInstance>)` | `:700` | 换着色器 | 手写绘制；走 `RenderType` 时不需要 |
| `setShaderTexture(int, ResourceLocation)` | `:714` | 给第 N 号采样器绑贴图 | 手写绘制；`RenderType` 的贴图由 `TextureStateShard` 管 |
| `setShaderColor(r,g,b,a)` | `:418` | 顶点色乘子 | 整体调色、淡入淡出 |
| `enableBlend()` / `blendFunc(...)` | `:196` / `:206` | 开混合 / 设混合方程 | 手写绘制；`RenderType` 里配 `TRANSLUCENT_TRANSPARENCY` |
| `depthMask(boolean)` | `:191` | 是否写深度 | 半透明叠加（只读深度、不写） |
| `recordRenderCall(RenderCall)` | `:127` | 把一段状态操作用渲染线程执行 | 从非渲染线程改状态 |

`GlStateManager` 更底层：剔除、模板、剪裁、视口。**能用 `RenderType` 表达的，就别直接动 `GlStateManager`** ——
`RenderType` 会在每批顶点前后把状态设置好，你手动改的状态可能被它覆盖，也可能污染下一批。

## 2.4 `RenderType`：一次绘制的全部规则

`RenderType` 是「怎么画」的总成。自定义一个渲染类型只有一条路：

```java
public static RenderType.CompositeRenderType create(
        String name,                                  // 调试用名字
        VertexFormat format,                          // 顶点布局
        VertexFormat.Mode mode,                       // 顶点怎么连
        int bufferSize,                               // 初始缓冲字节数，小了会自动扩容
        boolean affectsCrumbling,                     // 是否参与「方块被破坏」的裂纹渲染
        boolean sortOnUpload,                         // 上传前是否按距离排序（半透明需要）
        RenderType.CompositeState state);             // 状态组合，见下
```

`CompositeState` 由一组 `RenderStateShard` 拼成，每个 shard 管一件事：

| Shard | 作用 | 常见取值 |
| --- | --- | --- |
| `ShaderStateShard` | 用哪个着色器 | `new RenderStateShard.ShaderStateShard(() -> myShader)` |
| `TextureStateShard` | 绑哪张主贴图 | 位置贴图、图集 |
| `TransparencyStateShard` | 混合方程 | `NO_TRANSPARENCY` / `TRANSLUCENT_TRANSPARENCY` / `ADDITIVE_TRANSPARENCY` / `LIGHTNING_TRANSPARENCY` |
| `DepthTestStateShard` | 深度测试 | `LEQUAL_DEPTH_TEST` / `NO_DEPTH_TEST` |
| `WriteMaskStateShard` | 写哪些通道 | `COLOR_WRITE` / `COLOR_DEPTH_WRITE` / `DEPTH_WRITE` |
| `CullStateShard` | 面剔除 | `CULL` / `NO_CULL` |
| `LightmapStateShard` | 吃不吃世界光照 | `LIGHTMAP` / `NO_LIGHTMAP` |
| `OverlayStateShard` | 吃不吃 overlay | `OVERLAY` / `NO_OVERLAY` |
| `LayeringStateShard` | 视图偏移层级 | `VIEW_OFFSET_Z_LAYERING` |
| `OutputStateShard` | 输出到哪个目标 | 主目标 / 特定附魔光效目标 |

组合出你要的效果，例如「加法混合、不写深度、不吃剔除的自发光面片」：

```java
private static final RenderType GLOW = RenderType.create(
        "minegenshin_glow",
        DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
        VertexFormat.Mode.QUADS,
        1536, false, true,
        RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(() -> MyShaders.GLOW))
                .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)   // 只写颜色，不写深度
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setCullState(RenderStateShard.NO_CULL)
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .createCompositeState(false));
```

`createCompositeState(false)` 的参数是「要不要额外画一层轮廓（outline）」——
原版只有选中方块那种描边会传 `true`，特效一律 `false`。

**什么时候需要自定义 `RenderType`**：现有类型里找不到你要的状态组合时。
只有换贴图、换顶点色，不叫需要新类型 —— 那用现成的类型 + `setShaderTexture` / `setShaderColor` 就够了。
类型开得越多，状态切换越多，性能越差；同一种视觉效果尽量复用一个类型。

## 2.5 `MultiBufferSource`：把顶点分桶再一次性画

世界内的绘制几乎都不直接调 `BufferUploader`，而是把顶点交给 `MultiBufferSource`：

```java
MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
VertexConsumer vc = buffers.getBuffer(MY_TYPE);   // 按 RenderType 分桶
// …… 写顶点
buffers.endBatch();                               // 收尾：把攒下的桶全部画出去
```

它的机制值得记住，因为**忘掉它 = 画面空白**：

- `getBuffer(type)` 给每个 `RenderType` 一个桶（`BufferBuilder`）；
- 你换成另一个 `RenderType` 时，前一个桶会被 `endBatch` 自动画掉（否则状态就串了）；
- `endBatch()` 画掉所有桶；`endLastBatch()` 只画最后一个；
- 一次渲染调用结束前没 `endBatch`，这批顶点就留在缓冲里，直到下一次被别的绘制冲掉。

所以「我写了顶点但什么都没看到」的第一嫌疑永远是：**没 `endBatch`**，
或者顶点写在了错误的 `PoseStack` 状态下（比如已经 `popPose` 之后）。

## 2.6 光照与贴图：两个最容易传错的参数

```java
int packed = LightTexture.pack(blockLight, skyLight);   // 两个参数都是 0–15
vc.setUv2(packed & 0xFFFF, packed >> 16);
```

- 方块光 / 天空光各 4 bit，`LightTexture.pack` 把它们拼成一个 int；
- `setUv2(x, y)` 接受的是**拆开后的两个值**，所以要么用 `& 0xFFFF` / `>> 16` 拆，要么直接用 `LightTexture` 提供的辅助方法；
- 想让特效不受世界光照影响（自发光），把 `RenderType` 的 lightmap shard 设成 `NO_LIGHTMAP`，并在着色器里忽略这个属性。

贴图侧：

| 类 | 用途 |
| --- | --- |
| `TextureManager.getTexture(ResourceLocation)` | 拿一张贴图对象（手写绘制时绑它） |
| `RenderType` 的 `TextureStateShard` | 给整批顶点绑一张贴图（推荐） |
| `TextureAtlasSprite` | 方块/物品图集里的一个精灵，适合图集贴图 |

## 2.7 一次完整的手写绘制

把上面所有东西串起来：在世界里画一个跟着相机走的发光四边形。

```java
@SubscribeEvent
static void onRenderLevelStage(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

    PoseStack pose = event.getPoseStack();
    Camera camera = event.getCamera();
    Vec3 cam = camera.getPosition();
    MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
    VertexConsumer vc = buffers.getBuffer(GLOW);          // 1. 选类型（决定怎么画）

    pose.pushPose();                                       // 2. 进自己的坐标系
    pose.translate(10 - cam.x, 70 - cam.y, 10 - cam.z);    //    世界坐标 → 相机相对
    Matrix4f m = pose.last().pose();
    int light = LightTexture.pack(15, 15);

    float r = 0.5f;                                        // 3. 每个顶点：位置 → 颜色 → UV → 光照
    vc.addVertex(m, -r, 0f, -r).setColor(255, 200, 120, 255).setUv(0f, 0f).setUv2(light & 0xFFFF, light >> 16);
    vc.addVertex(m,  r, 0f, -r).setColor(255, 200, 120, 255).setUv(1f, 0f).setUv2(light & 0xFFFF, light >> 16);
    vc.addVertex(m,  r, 0f,  r).setColor(255, 200, 120, 255).setUv(1f, 1f).setUv2(light & 0xFFFF, light >> 16);
    vc.addVertex(m, -r, 0f,  r).setColor(255, 200, 120, 255).setUv(0f, 1f).setUv2(light & 0xFFFF, light >> 16);

    pose.popPose();                                        // 4. 还原
    buffers.endBatch();                                    // 5. 收尾（漏了就没画面）
}
```

逐行解释：

1. **选类型在写顶点之前**。`getBuffer(GLOW)` 拿到的桶，最后就是按 `GLOW` 的状态画出去的。
2. `PoseStack` 是「相机相对」的：世界坐标减去相机坐标才是要用的平移量。
3. `VertexConsumer` 的链式调用**顺序 = 顶点格式顺序**；`setUv2` 传的是拆开的光照。
4. 一定要 `popPose`，否则后面所有渲染都会跟着你的变换走。
5. `endBatch` 把桶画出去；同一阶段里如果还要换类型继续画，可以留到最后统一 `endBatch`。

## 2.8 常见错误对照表

| 症状 | 真正的原因 |
| --- | --- |
| 写了顶点但什么都没有 | 没 `endBatch`；或顶点写在了 `popPose` 之后 |
| 画面全黑 / 全白 | 着色器没设（`setShader`）或采样器没绑贴图 |
| 位置全挤在原点 | `addVertex` 没拿到正确的 `PoseStack.Pose` / 矩阵 |
| UV 看起来像颜色、颜色看起来像 UV | `setUv` 与 `setColor` 的顺序和 `VertexFormat` 不一致 |
| 面片半边被裁掉 | `CULL` 剔除了背面，特效一般用 `NO_CULL` |
| 半透明互相盖住、排序乱 | 没用 `TRANSLUCENT_TRANSPARENCY` + `sortOnUpload` |
| 光影下发光面变黑 | 吃了世界光照 / 光影接管了管线，用 `NO_LIGHTMAP` 并检查 Iris 设置 |
| 出现「BufferBuilder 已在使用」 | 同一时刻借了两个 `BufferBuilder`（忘了 `build*()`） |

下一章：[3. 着色器、渲染管线与 GPU 数据](/doc/rendering-1.21.1-reference-shaders) 讲 `RenderType` 里那个
`ShaderStateShard` 到底指向什么，以及怎么写一份能编译过的 core shader。