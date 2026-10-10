# 2. Blaze3D 是什么、怎么写

「Blaze3D」是 Mojang 那层渲染封装。写特效只需要记住**四件事**：顶点、状态、渲染类型、多缓冲。

## 2.1 顶点：`VertexConsumer`

```java
vc.addVertex(pose, x, y, z)      // 位置（格）
  .setColor(255, 255, 255, 255)  // 颜色 0–255
  .setUv(0f, 0f)                 // 主贴图 0–1
  .setUv2(light & 0xFFFF, light >> 16);   // 光照 0–15 + 0–15
```

**顺序必须与 `VertexFormat` 一致**。最常用的格式是
`DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP`：位置 → 颜色 → UV → 光照。

## 2.2 状态：`RenderSystem`

只有在手写绘制时才需要自己设：`setShader`（换着色器）、`setShaderTexture`（绑贴图）、
`setShaderColor`（整体调色）、`enableBlend` / `blendFunc`（混合）、`depthMask`（深度写入）。
走 `RenderType` 时这些都由类型决定，别手动改。

## 2.3 渲染类型：`RenderType`

```java
RenderType.create(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, compositeState)
```

`CompositeState` 由 `RenderStateShard` 拼成，最常用的五个：

| Shard | 什么时候改 |
| --- | --- |
| `ShaderStateShard` | 要用自己的着色器 |
| `TransparencyStateShard` | 加法发光 / 半透明 |
| `WriteMaskStateShard` | 半透明叠加时只写颜色不写深度 |
| `CullStateShard` | 光效一般 `NO_CULL` |
| `LightmapStateShard` | 自发光用 `NO_LIGHTMAP` |

## 2.4 多缓冲：`MultiBufferSource`

```java
var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
VertexConsumer vc = buffers.getBuffer(MY_TYPE);
// …… 写顶点
buffers.endBatch();     // 漏了这句 = 什么都没有
```

同一 `RenderType` 的顶点连着写；换类型会触发一次关批（状态切换的代价就在这里）。

## 2.5 一句话记住

> **顶点决定形状，`RenderType` 决定长相，`endBatch` 决定它到底出不出现在屏幕上。**

深入：[完全参考 2. Blaze3D API 地图](/doc/rendering-1.21.1-reference-blaze3d)（每个方法、单位、坑都有）。