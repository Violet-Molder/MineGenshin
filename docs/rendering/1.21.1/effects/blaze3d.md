# 2. Blaze3D 在 1.21.1 里长什么样


## 2.1 包结构

| 包 | 内容 |
| --- | --- |
| `com.mojang.blaze3d.vertex` | `PoseStack`、`VertexConsumer`、`BufferBuilder`、`MeshData`、`BufferUploader`、`VertexFormat`、`Tesselator` |
| `com.mojang.blaze3d.systems` | `RenderSystem`（着色器、贴图、混合、深度、矩阵） |
| `com.mojang.blaze3d.platform` | `GlStateManager`、`Window` |
| `com.mojang.blaze3d.preprocessor` | `GlslPreprocessor`（`#moj_import` 在 `:20`） |
| `net.minecraft.client.renderer` | `RenderType`、`RenderStateShard`、`ShaderInstance`、`MultiBufferSource`、`LightTexture`、`PostChain` / `PostPass` |
| `net.minecraft.client.renderer.entity` | `EntityRenderer`、`EntityRenderDispatcher` |

## 2.2 `PoseStack`

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

## 2.3 `VertexConsumer` / `BufferBuilder` / `MeshData`

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

## 2.4 `RenderType` 与 `RenderStateShard`

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

## 2.5 `RenderSystem` 状态

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

## 2.6 顶点格式与属性

常用预设都在 `DefaultVertexFormat`：`POSITION`、`POSITION_COLOR`、`POSITION_TEX`、
`POSITION_COLOR_TEX_LIGHTMAP`、`POSITION_COLOR_NORMAL`、`NEW_ENTITY`、`BLOCK`。

粒子 / 特效最常用 `POSITION_COLOR_TEX_LIGHTMAP`：`addVertex → setColor → setUv → setUv2(light)`。
`setUv2` 接的是打包后的光照值（`LightTexture#pack`），不是 UV1。

---
