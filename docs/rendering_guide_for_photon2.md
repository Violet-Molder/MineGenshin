# Minecraft 渲染技术完全文档 — 面向 Photon2 特效开发

> **适用环境：** Minecraft 1.21+ (NeoForge) / Blaze3D / OpenGL 3.3 Core  
> **目标：** 深入掌握 Blaze3D、GLSL Shader、渲染管线、Core Shader JSON 及模型渲染的全部基础知识，熟练使用 Photon2 制作自定义特效  
> **文档版本：** v2.0（大幅扩充版）  
> **最后更新：** 2026-09-28

---

## 目录

1. [Blaze3D 完全入门](#1-blaze3d-完全入门)
2. [GLSL Shader 完全入门](#2-glsl-shader-完全入门)
3. [Minecraft Rendering Pipeline](#3-minecraft-rendering-pipeline)
4. [Minecraft Core Shader JSON](#4-minecraft-core-shader-json)
5. [Minecraft Model Rendering](#5-minecraft-model-rendering)
6. [四元数与三维旋转](#6-四元数与三维旋转)
7. [矩阵变换与坐标系](#7-矩阵变换与坐标系)
8. [JOML 数学库详解](#8-joml-数学库详解)
9. [GPU 蒙皮与高级渲染架构](#9-gpu-蒙皮与高级渲染架构)
10. [Photon2 特效开发实战](#10-photon2-特效开发实战)
11. [附录](#11-附录)

---

## 1. Blaze3D 完全入门

### 1.1 什么是 Blaze3D？

Blaze3D 是 Minecraft 自 1.5+ 以来使用的**内置渲染引擎**，位于 `com.mojang.blaze3d` 包下。它是对底层 **OpenGL 3.3 Core Profile** 的轻量封装，为 Minecraft 提供渲染抽象层。

**核心职责：**

- 管理 OpenGL 状态机（纹理绑定、缓冲区绑定、着色器切换）
- 提供顶点缓冲 (VertexBuffer) 和索引缓冲的管理
- 提供矩阵堆栈 (MatrixStack / PoseStack) 用于变换
- 封装着色器程序的加载与切换
- 处理渲染通道 (RenderTarget / Framebuffer)
- 抽象图形 API（GL / Vulkan / GLES），在 1.20+ 中通过 `GpuDevice` 接口

### 1.2 核心类完全参考

#### 1.2.1 RenderSystem — 全局渲染状态管理器

`com.mojang.blaze3d.systems.RenderSystem`

这是 Minecraft 渲染的**入口点**。所有 OpenGL 调用都应通过此类，它提供线程安全检查（确保渲染代码在渲染线程执行）。

```java
// ===== 线程管理 =====
RenderSystem.assertOnRenderThread();       // 断言当前在渲染线程（否则抛异常）
RenderSystem.assertOnGameThread();         // 断言当前在游戏线程
RenderSystem.isOnRenderThread();           // boolean 检查
RenderSystem.recordRenderThread(Runnable); // 提交任务到渲染线程执行

// ===== 着色器管理 =====
RenderSystem.setShader(ShaderInstance);    // 设置当前着器程序
RenderSystem.getShader();                  // -> ShaderInstance
RenderSystem.setShaderColor(r, g, b, a);   // 设置 ColorModulator uniform (0~1)
RenderSystem.setShaderTexture(int unit, ResourceLocation); // 绑定纹理到纹理单元

// ===== 混合 (Blend) =====
RenderSystem.enableBlend();
RenderSystem.disableBlend();
RenderSystem.blendFunc(srcFactor, dstFactor);
//   srcFactor: GL_SRC_ALPHA, GL_ONE, GL_ZERO, GL_DST_COLOR, GL_SRC_COLOR...
//   dstFactor: GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO, GL_DST_COLOR...
RenderSystem.blendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
RenderSystem.defaultBlendFunc();  // SRC_ALPHA, ONE_MINUS_SRC_ALPHA

// ===== 深度测试 =====
RenderSystem.enableDepthTest();
RenderSystem.disableDepthTest();
RenderSystem.depthFunc(int func);
//   func: GL_ALWAYS, GL_LEQUAL, GL_EQUAL, GL_LESS, GL_GREATER...

// ===== 面剔除 =====
RenderSystem.enableCull();
RenderSystem.disableCull();

// ===== 裁剪 (Scissor) =====
RenderSystem.enableScissor(int x, int y, int width, int height);
RenderSystem.disableScissor();

// ===== 颜色掩码 =====
RenderSystem.colorMask(boolean red, boolean green, boolean blue, boolean alpha);
RenderSystem.depthMask(boolean flag); // 控制深度写入

// ===== 矩阵获取 =====
RenderSystem.getModelViewMatrix();      // -> Matrix4f (当前模型视图矩阵)
RenderSystem.getProjectionMatrix();     // -> Matrix4f (当前投影矩阵)
RenderSystem.getInverseViewRotationMatrix(); // -> Matrix3f

// ===== 顶点缓冲管理（1.20+ 新 API） =====
RenderSystem.getDevice();               // -> GpuDevice
RenderSystem.tryGetDevice();            // -> GpuDevice (可为 null)
RenderSystem.getSequentialBuffer(PrimitiveTopology); // 获取顺序索引缓冲

// ===== 时间 =====
RenderSystem.getShaderGameTime();       // -> float (游戏刻时间)
```

#### 1.2.2 PoseStack — 变换矩阵栈 (替代 MatrixStack)

`com.mojang.blaze3d.vertex.PoseStack`

Minecraft 1.17+ 使用 PoseStack 替代了旧版 MatrixStack。内部管理一个栈结构，每个元素包含：

- `pose()` — `Matrix4f` 模型矩阵（用于顶点位置变换）
- `normal()` — `Matrix3f` 法线矩阵（用于法线变换，是 pose 的逆转置的上三角）

```java
PoseStack stack = new PoseStack();

// ---- 栈操作 ----
stack.pushPose();              // 压入当前变换的副本
stack.popPose();               // 弹出栈顶

// ---- 变换操作 ----
stack.translate(x, y, z);      // 平移 (double 或 float)
stack.scale(x, y, z);          // 缩放 (float)
stack.mulPose(Quaternionf);    // 右乘旋转四元数
stack.mulPoseMatrix(Matrix4f); // 右乘任意矩阵

// ---- 读取 ----
PoseStack.Pose entry = stack.last();
Matrix4f poseMat = entry.pose();     // 4x4 变换矩阵
Matrix3f normalMat = entry.normal(); // 3x3 法线矩阵

// ---- 拷贝 ----
PoseStack.Pose copy = entry.copy();  // 复制当前姿态
stack.last().set(copy);              // 恢复之前保存的姿态
```

**重要：** `PoseStack.Pose` 的 `copy()` 创建独立副本，修改副本不影响原件。这在异步渲染（如 submitCustomGeometry 回调中）非常有用。

#### 1.2.3 BufferBuilder — 顶点缓冲构建器

`com.mojang.blaze3d.vertex.BufferBuilder`

用于在 CPU 端构建顶点数据，然后上传到 GPU。

```java
BufferBuilder builder = Tesselator.getInstance().getBuilder();

// ---- 开始构建 ----
builder.begin(VertexFormat.Mode mode, VertexFormat format);
// mode:
//   QUADS       — 四边形（Minecraft 1.20+ 推荐，GPU 端自动转三角形）
//   TRIANGLES   — 三角形
//   TRIANGLE_STRIP — 三角带
//   LINES       — 线段
//   LINE_STRIP  — 线段带
//
// format: DefaultVertexFormat 中的常量或自定义 VertexFormat

// ---- 添加顶点 ----
// 方式 1: 链式调用
builder.vertex(matrix, x, y, z)        // 设置位置（自动变换）
       .color(r, g, b, a)              // 设置颜色 (0~255)
       .uv(u, v)                       // 设置纹理 UV (float)
       .uv2(u, v)                      // 设置光照 UV (用于方块光)
       .normal(x, y, z)                // 设置法线
       .endVertex();                   // 结束当前顶点

// 方式 2: ByteBuffer 直接写入（性能更高，适合大量顶点）
// 需要理解 VertexFormat 的字节布局

// ---- 结束并提交 ----
BufferBuilder.RenderedBuffer rendered = builder.end();
BufferUploader.drawWithShader(rendered);  // 使用当前着色器绘制
// 或者：
BufferUploader.draw(rendered);             // 不带着色器
```

**顶点格式 (VertexFormat.VertexFormatMode) 说明：**

| 模式 | 字节对齐 | 说明 |
|------|----------|------|
| `SEQUENTIAL` | 4 字节 | 紧凑排列，默认 |
| `STRIDE` | 自定义步长 | 可以在顶点间插额外数据 |

#### 1.2.4 Tesselator — 网格构建器

`com.mojang.blaze3d.vertex.Tesselator`

```java
// 获取实例（每线程一个）
Tesselator tesselator = Tesselator.getInstance();

// 主要用法
BufferBuilder builder = tesselator.getBuilder();
builder.begin(...);
// ... 添加顶点 ...
tesselator.end();    // 等同于 builder.end() + BufferUploader.drawWithShader
```

#### 1.2.5 VertexFormat — 顶点格式定义

`com.mojang.blaze3d.vertex.VertexFormat`

**1.20+ 新 API（使用 builder 构建）：**

```java
VertexFormat format = VertexFormat.builder(0) // stride = 0 表示紧凑排列
    .addAttribute("Position", GpuFormat.RGB32_FLOAT)     // vec3, 12 bytes
    .addAttribute("Color", GpuFormat.RGBA8_UNORM)        // vec4, 4 bytes
    .addAttribute("UV0", GpuFormat.RG32_FLOAT)           // vec2, 8 bytes
    .addAttribute("UV1", GpuFormat.RG16_SINT)            // ivec2, 4 bytes
    .addAttribute("UV2", GpuFormat.RG16_SINT)            // ivec2, 4 bytes
    .addAttribute("Normal", GpuFormat.RGBA8_SNORM)       // vec4, 4 bytes (w 未使用)
    .build();
```

**GpuFormat 枚举：**

| 格式 | GLSL 类型 | 字节数 | 说明 |
|------|-----------|--------|------|
| `RGB32_FLOAT` | `vec3` | 12 | 32 位浮点三维向量 |
| `RG32_FLOAT` | `vec2` | 8 | 32 位浮点二维向量 |
| `R32_FLOAT` | `float` | 4 | 32 位浮点标量 |
| `RGBA8_UNORM` | `vec4` | 4 | 8 位归一化无符号整数 [0,255]→[0,1] |
| `RGBA8_SNORM` | `vec4` | 4 | 8 位归一化有符号整数 [-128,127]→[-1,1] |
| `RG16_SINT` | `ivec2` | 4 | 16 位有符号整数向量 |
| `RGBA16_UINT` | `uvec4` | 8 | 16 位无符号整数向量 |
| `RGB10_A2` | `vec4` | 4 | 10/10/10/2 位打包格式 |

**DefaultVertexFormat 预定义常量：**

```java
DefaultVertexFormat.BLIT_SCREEN      // Position, UV — 全屏 blit
DefaultVertexFormat.POSITION_TEX     // Position, UV
DefaultVertexFormat.POSITION_COLOR   // Position, Color
DefaultVertexFormat.POSITION_TEX_COLOR // Position, UV, Color
DefaultVertexFormat.POSITION_NORMAL  // Position, Normal
DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL // Position, UV, Color, Normal
DefaultVertexFormat.NEW_ENTITY       // Position, Color, UV0, UV1, UV2, Normal — 实体标准
DefaultVertexFormat.PARTICLE         // Position, UV0, Color, UV2 — 粒子标准
```

#### 1.2.6 自定义 VertexFormat 示例（来自项目的 SkinnedMesh）

```java
// 项目中的 GPU 蒙皮顶点格式：32 字节/顶点
VertexFormat FORMAT = VertexFormat.builder(0)
    .addAttribute("Position", GpuFormat.RGB32_FLOAT)     // offset 0,  12 bytes
    .addAttribute("UV0", GpuFormat.RG32_FLOAT)           // offset 12, 8 bytes
    .addAttribute("Normal", GpuFormat.RGBA8_SNORM)       // offset 20, 4 bytes
    .addAttribute("BoneIds", GpuFormat.RGBA16_UINT)      // offset 24, 8 bytes
    .build();
// 总计 32 字节/顶点
```

#### 1.2.7 VertexConsumer — 顶点消费者接口

`com.mojang.blaze3d.vertex.VertexConsumer`

这是 Minecraft 1.20+ 中**推荐**的顶点写出方式。`BufferBuilder` 实现了此接口，提供流畅的链式 API：

```java
// 方式 1: 直接使用
buffer.addVertex(pose, x, y, z)          // 位置 (PoseStack.Pose)
      .setColor(r, g, b, a)              // 颜色 (0~255 int)
      .setUv(u, v)                       // 纹理 UV (float)
      .setOverlay(overlay)               // 覆盖 UV (int, 用于 Hurt 效果)
      .setLight(lightCoord)              // 光照 UV (int, packed)
      .setNormal(pose, nx, ny, nz);      // 法线

// 方式 2: 使用 pose 变换
// 位置 x,y,z 乘以 pose 矩阵自动变换到裁剪空间
// 法线乘以 pose.normal() 自动变换到世界空间
```

**项目中的实际用法示例（来自 StellarVortexRenderer）：**

```java
buffer.addVertex(pose, x, y, 0.0F)
      .setColor(r, g, b, a)
      .setUv(u, v)
      .setOverlay(OverlayTexture.NO_OVERLAY)
      .setLight(lightCoords)
      .setNormal(pose, 0.0F, 1.0F, 0.0F);
```

#### 1.2.8 GpuDevice / GpuBuffer — 1.20+ 新渲染后端

`com.mojang.blaze3d.systems.GpuDevice`  
`com.mojang.blaze3d.buffers.GpuBuffer`

1.20+ 引入了抽象的 GPU 设备接口，支持 OpenGL 和 Vulkan 后端。

```java
GpuDevice device = RenderSystem.getDevice();

// 创建顶点缓冲
GpuBuffer buffer = device.createBuffer(
    () -> "buffer name",                    // 调试标签
    GpuBuffer.USAGE_VERTEX,                 // 用途标志
    ByteBuffer data                         // 顶点数据
);

// 创建常量缓冲 (Uniform Buffer)
GpuBuffer uniformBuffer = device.createBuffer(
    () -> "uniforms",
    GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM,
    sizeInBytes
);

// 映射缓冲（CPU 写入 GPU 可读）
try (GpuBufferSlice.MappedView view = slice.map(false, true)) {
    ByteBuffer data = view.data();
    data.putFloat(...);  // 写入数据
    // 自动 unmap
}
```

#### 1.2.9 RenderPipeline — 渲染管线

`com.mojang.blaze3d.pipeline.RenderPipeline`

1.20+ 引入的管线对象，封装了完整的渲染状态：

```java
RenderPipeline pipeline = RenderPipelines.ENTITY_CUTOUT; // 预定义管线

// 创建派生管线（项目中的用法）
RenderPipeline derived = base.toBuilder()
    .withLocation(newId)                    // 唯一 ID
    .withVertexShader(shaderLocation)       // 替换顶点着色器
    .withFragmentShader(fragLocation)       // 替换片元着色器
    .withBindGroupLayout(layout)            // 追加绑定组布局
    .withVertexBinding(0, vertexFormat)     // 替换顶点格式 (binding 0)
    .build();
```

**预定义管线（`RenderPipelines`）：**

| 常量 | 对应着色器 | 说明 |
|------|-----------|------|
| `ENTITY_SOLID` | `core/entity` | 实体不透明 |
| `ENTITY_SOLID_Z_OFFSET_FORWARD` | `core/entity` | 实体不透明 + Z 偏移 |
| `ENTITY_CUTOUT` | `core/entity` | 实体扣像 (Alpha Test) |
| `ENTITY_CUTOUT_CULL` | `core/entity` | 实体扣像 + 背面剔除 |
| `ENTITY_CUTOUT_Z_OFFSET` | `core/entity` | 实体扣像 + Z 偏移 |
| `ENTITY_TRANSLUCENT` | `core/entity_translucent` | 实体半透明 |
| `ENTITY_TRANSLUCENT_CULL` | `core/entity_translucent` | 实体半透明 + 剔除 |
| `ENTITY_TRANSLUCENT_EMISSIVE` | `core/entity_translucent_emissive` | 实体自发光半透明 |
| `ENTITY_CUTOUT_DISSOLVE` | `core/entity_dissolve` | 实体溶解效果 |
| `BREEZE_WIND` | `core/breeze_wind` | Breeze 风效果 |

#### 1.2.10 BindGroupLayout — 绑定组布局

`com.mojang.blaze3d.pipeline.BindGroupLayout`

用于声明着色器中 Uniform 缓冲的布局：

```java
BindGroupLayout layout = BindGroupLayout.builder()
    .withUniform("SkinData", UniformType.UNIFORM_BUFFER)
    .build();
```

#### 1.2.11 SubmitNodeCollector — 提交节点收集器

`com.mojang.blaze3d.vertex.SubmitNodeCollector` (1.20+)

替代旧版 `RenderType.submit` 渲染架构的新 API：

```java
// 提交自定义几何
collector.submitCustomGeometry(
    poseStack,
    renderType,                    // 渲染类型
    (pose, buffer) -> {           // 绘制回调
        // pose: PoseStack.Pose (已固定)
        // buffer: VertexConsumer
        buffer.addVertex(pose, ...);
    }
);
```

### 1.3 Blaze3D 绘制完整示例

#### 1.3.1 基础四边形绘制

```java
public static void drawQuad(PoseStack poseStack, Identifier texture,
                            float x, float y, float w, float h,
                            float u0, float v0, float u1, float v1,
                            int color) {
    RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
    RenderSystem.setShaderTexture(0, texture);
    RenderSystem.enableBlend();
    RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                           GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

    Matrix4f matrix = poseStack.last().pose();
    BufferBuilder buffer = Tesselator.getInstance().getBuilder();
    buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

    int r = (color >> 16) & 0xFF;
    int g = (color >> 8) & 0xFF;
    int b = color & 0xFF;
    int a = (color >> 24) & 0xFF;

    buffer.vertex(matrix, x,     y + h, 0).uv(u0, v1).color(r, g, b, a).endVertex();
    buffer.vertex(matrix, x + w, y + h, 0).uv(u1, v1).color(r, g, b, a).endVertex();
    buffer.vertex(matrix, x + w, y,     0).uv(u1, v0).color(r, g, b, a).endVertex();
    buffer.vertex(matrix, x,     y,     0).uv(u0, v0).color(r, g, b, a).endVertex();

    BufferUploader.drawWithShader(buffer.end());

    RenderSystem.disableBlend();
}
```

#### 1.3.2 Billboard（公告牌）效果

始终面向相机的四边形，常用于粒子特效：

```java
public static void drawBillboard(PoseStack poseStack, VertexConsumer buffer,
                                  Camera camera, float x, float y, float z,
                                  float width, float height,
                                  int color, int light) {
    poseStack.pushPose();
    poseStack.translate(x, y, z);
    poseStack.mulPose(camera.rotation());  // 始终面向相机

    Matrix4f matrix = poseStack.last().pose();
    float hw = width / 2;
    float hh = height / 2;

    int r = (color >> 16) & 0xFF;
    int g = (color >> 8) & 0xFF;
    int b = color & 0xFF;
    int a = (color >> 24) & 0xFF;

    buffer.addVertex(matrix, -hw, -hh, 0).setColor(r, g, b, a).setUv(0, 1).setLight(light);
    buffer.addVertex(matrix,  hw, -hh, 0).setColor(r, g, b, a).setUv(1, 1).setLight(light);
    buffer.addVertex(matrix,  hw,  hh, 0).setColor(r, g, b, a).setUv(1, 0).setLight(light);
    buffer.addVertex(matrix, -hw,  hh, 0).setColor(r, g, b, a).setUv(0, 0).setLight(light);

    poseStack.popPose();
}
```

---

## 2. GLSL Shader 完全入门

### 2.1 GLSL 基础

GLSL (OpenGL Shading Language) 是运行在 GPU 上的类 C 语言。Minecraft 1.20+ 基于 **OpenGL 3.3 Core Profile**，使用 **GLSL 330** 版本。

```
#version 330  ← 版本声明
// 着色器代码
```

### 2.2 着色器管线

```
顶点数据 → [顶点着色器 (Vertex Shader)] → [细分/几何着色器(可选)] → [片元着色器 (Fragment Shader)] → 帧缓冲
                ↓                               ↓                           ↓
          gl_Position (裁剪坐标)          图元装配                     fragColor (最终颜色)
```

Minecraft Core Shader 只包含：
- **`.vsh`** — 顶点着色器
- **`.fsh`** — 片元着色器

### 2.3 顶点着色器 (Vertex Shader) 完全参考

#### 2.3.1 标准结构（来自原版 entity.vsh）

```glsl
#version 330

// ===== 输入属性 (in) =====
// 这些来自 VertexFormat，顺序必须与格式定义完全一致
in vec3 Position;          // 位置 (必需)
in vec4 Color;             // 颜色 (可选，从顶点颜色获取)
in vec2 UV0;               // 纹理坐标 (Sampler0)
in vec2 UV1;               // 覆盖纹理坐标 (Sampler1，用于受伤闪白效果)
in vec2 UV2;               // 光照纹理坐标 (Sampler2，用于方块光)
in vec3 Normal;            // 法线

// ===== Uniform =====
uniform mat4 ModelViewMat;      // 模型视图矩阵
uniform mat4 ProjMat;           // 投影矩阵
uniform mat3 NormalMat;         // 法线矩阵 (ModelViewMat 的逆转置上三角)
uniform vec2 ScreenSize;        // 屏幕尺寸(像素)
uniform float GameTime;         // 游戏刻时间
uniform vec3 Light0_Direction;  // 光照 0 方向 (主光源)
uniform vec3 Light1_Direction;  // 光照 1 方向 (环境光)

// ===== 输出到片元着色器 =====
out vec4 vertexColor;
out vec2 texCoord0;
out vec2 texCoord1;     // overlay UV
out vec2 texCoord2;     // lightmap UV
out vec4 normal;        // 法线 (vec4 因为需要对齐)
out float vertexDistance;  // 顶点距离 (用于雾计算)

void main() {
    // 标准变换：顶点从模型空间 → 视图空间 → 裁剪空间
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    // 传递数据
    vertexColor = Color;
    texCoord0 = UV0;
    texCoord1 = UV1;
    texCoord2 = UV2;
    normal = ProjMat * ModelViewMat * vec4(Normal, 0.0);

    // 雾距离计算
    vertexDistance = length((ModelViewMat * vec4(Position, 1.0)).xyz);
}
```

#### 2.3.2 项目中自定义顶点着色器 (entity_skinned.vsh 核心逻辑)

```glsl
#version 330

// 自定义属性：骨骼 ID（uvec4：4 个 16 位无符号整数）
in uvec4 BoneIds;

// 自定义 Uniform：骨骼矩阵调色板（std140 布局）
layout(std140) uniform SkinData {
    mat4 Bones[128];           // 骨骼变换矩阵
    mat3 NormalBones[128];     // 骨骼法线矩阵
    ivec4 LightOverlay;        // 光照覆盖参数
    vec4 Color;                // 全局颜色
};

void main() {
    int bone = int(BoneIds.x);  // 取骨骼编号

    // 用骨骼矩阵变换顶点（GPU 蒙皮）
    vec3 viewRelative = (ModelViewMat * Bones[bone] * vec4(Position, 1.0)).xyz;
    gl_Position = ProjMat * vec4(viewRelative, 1.0);

    // 法线用法线矩阵变换
    vec3 normal = NormalBones[bone] * Normal;

    // 雾距离计算（在视图空间中）
    float vertexDistance = length(viewRelative);
}
```

### 2.4 片元着色器 (Fragment Shader) 完全参考

#### 2.4.1 标准结构

```glsl
#version 330

// ===== 输入 (来自顶点着色器) =====
in vec4 vertexColor;         // 顶点颜色（已插值）
in vec2 texCoord0;           // 纹理坐标（已插值）
in vec4 normal;              // 法线（已插值）
in float vertexDistance;     // 顶点距离（已插值）

// ===== Uniform =====
uniform sampler2D Sampler0;       // 主纹理 (纹理单元 0)
uniform sampler2D Sampler1;       // 覆盖纹理 (纹理单元 1)
uniform sampler2D Sampler2;       // 光照纹理 (纹理单元 2)
uniform vec4 ColorModulator;      // 颜色调制器 (由 RenderSystem.setShaderColor 设置)
uniform vec4 FogColor;            // 雾颜色
uniform float FogStart;           // 雾起始距离
uniform float FogEnd;             // 雾结束距离
uniform int FogShape;             // 雾形状 (0=球面, 1=圆柱)

// ===== 输出 =====
out vec4 fragColor;

void main() {
    // 1. 采样纹理
    vec4 texColor = texture(Sampler0, texCoord0);

    // 2. 应用颜色调制 (vertexColor * ColorModulator)
    vec4 color = texColor * vertexColor * ColorModulator;

    // 3. Alpha 测试 (cutout)
    if (color.a < 0.1) {
        discard;  // 丢弃像素
    }

    // 4. 应用雾
    float fogFactor = (vertexDistance - FogStart) / (FogEnd - FogStart);
    fogFactor = clamp(fogFactor, 0.0, 1.0);
    color.rgb = mix(color.rgb, FogColor.rgb, fogFactor);

    // 5. 输出最终颜色
    fragColor = color;
}
```

### 2.5 GLSL 数据类型完全参考

#### 2.5.1 基本类型

| 类型 | 描述 | 字节 | 示例 |
|------|------|------|------|
| `float` | 单精度浮点 | 4 | `1.0` (不能写 `1`) |
| `int` | 有符号整数 | 4 | `42` |
| `uint` | 无符号整数 | 4 | `42u` |
| `bool` | 布尔 | 4 | `true` |
| `double` | 双精度浮点 | 8 | `1.0LF` |

#### 2.5.2 向量类型

| 类型 | 描述 | 成员访问 |
|------|------|----------|
| `vec2` | 2 分量浮点向量 | `.xy`, `.rg`, `.st` |
| `vec3` | 3 分量浮点向量 | `.xyz`, `.rgb`, `.stp` |
| `vec4` | 4 分量浮点向量 | `.xyzw`, `.rgba`, `.stpq` |
| `ivec2/3/4` | 整数向量 | 同上 |
| `uvec2/3/4` | 无符号整数向量 | 同上 |
| `bvec2/3/4` | 布尔向量 | 同上 |

**向量访问器 (Swizzling)：**

```glsl
vec4 v = vec4(1.0, 2.0, 3.0, 4.0);
v.x      // 1.0
v.xy     // vec2(1.0, 2.0)
v.xyz    // vec3(1.0, 2.0, 3.0)
v.rgb    // 同上，使用颜色访问器
v.bgra   // vec4(3.0, 2.0, 1.0, 4.0) — 重排
```

#### 2.5.3 矩阵类型

| 类型 | 描述 | 布局 |
|------|------|------|
| `mat2` | 2x2 浮点矩阵 | 列主序 |
| `mat3` | 3x3 浮点矩阵 | 列主序 |
| `mat4` | 4x4 浮点矩阵 | 列主序 |

```glsl
mat4 m = mat4(1.0);  // 单位矩阵
m[0]     // 第 0 列 (vec4)
m[0][1]  // 第 0 列第 1 行 (float)
m[1].xyz // 第 1 列的前 3 个分量
```

#### 2.5.4 采样器类型

| 类型 | 描述 |
|------|------|
| `sampler2D` | 2D 纹理采样器 |
| `sampler3D` | 3D 纹理采样器 |
| `samplerCube` | 立方体贴图采样器 |
| `sampler2DArray` | 2D 纹理数组采样器 |
| `sampler2DShadow` | 2D 阴影采样器 (深度比较) |

#### 2.5.5 精度限定符

```glsl
// 默认精度（在 vertex shader 中默认 highp）
precision highp float;     // 32 位浮点
precision mediump float;   // 16 位浮点
precision lowp float;      // 10 位浮点
```

在 Minecraft 中通常不显式声明，但移动端移植时需要留意。

### 2.6 GLSL 内置变量

#### 2.6.1 顶点着色器内置变量

```glsl
// 输出（必填）
gl_Position = vec4(...);  // vec4 裁剪空间坐标

// 输入（可选）
gl_VertexID                // int，当前顶点索引
gl_InstanceID              // int，当前实例索引
gl_DrawID                  // int，当前绘制调用 ID (OpenGL 4.0+)
```

#### 2.6.2 片元着色器内置变量

```glsl
// 输入
gl_FragCoord   // vec4 片元在屏幕空间的坐标 (.xy=像素坐标, .z=深度, .w=1/w)
gl_FrontFacing // bool 当前片元是否属于正面朝向的图元
gl_PointCoord  // vec2 点在点精灵中的坐标 [0,1]

// 输出
gl_FragDepth   // float 自定义深度值（不设置时等于 gl_FragCoord.z）
```

### 2.7 GLSL 内置函数完全参考

#### 2.7.1 角度和三角函数

```glsl
radians(degrees)     // 角度→弧度
degrees(radians)     // 弧度→角度
sin(x), cos(x), tan(x)
asin(x), acos(x), atan(x), atan(y, x)
```

#### 2.7.2 指数函数

```glsl
pow(x, y)         // x 的 y 次幂
exp(x)            // e 的 x 次幂
log(x)            // 自然对数
exp2(x)           // 2 的 x 次幂
log2(x)           // 以 2 为底的对数
sqrt(x)           // 平方根
inversesqrt(x)    // 平方根的倒数
```

#### 2.7.3 常用函数

```glsl
abs(x)            // 绝对值
sign(x)           // 符号函数 (-1, 0, 1)
floor(x)          // 向下取整
ceil(x)           // 向上取整
fract(x)          // 小数部分 (= x - floor(x))
mod(x, y)         // 取模 (= x - y * floor(x/y))
min(x, y)         // 最小值
max(x, y)         // 最大值
clamp(x, min, max) // 限制范围
mix(x, y, a)      // 线性插值 (= x*(1-a)+y*a)
step(edge, x)     // 阶跃函数 (x<edge?0:1)
smoothstep(edge0, edge1, x)  // 平滑阶跃（三次 Hermite 插值）
```

**smoothstep 详解：**

```glsl
// smoothstep(edge0, edge1, x)
// edge0 < edge1 时：
//   x ≤ edge0 → 0
//   x ≥ edge1 → 1
//   edge0 < x < edge1 → 三次 Hermite 插值：t = (x-edge0)/(edge1-edge0); t*t*(3-2*t)
float t = (x - edge0) / (edge1 - edge0);
float result = t * t * (3.0 - 2.0 * t);
```

#### 2.7.4 几何函数

```glsl
length(v)         // 向量长度 (sqrt(dot(v,v)))
distance(p0, p1)  // 两点间距离
dot(a, b)         // 点积 (标量)
cross(a, b)       // 叉积 (vec3)
normalize(v)      // 归一化向量
reflect(I, N)     // 反射向量：I - 2*dot(N,I)*N
refract(I, N, eta) // 折射向量
faceforward(N, I, Nref) // 调整 N 朝向 I
```

#### 2.7.5 矩阵函数

```glsl
matrixCompMult(A, B) // 分量乘（不是矩阵乘）
transpose(m)         // 转置
inverse(m)           // 逆矩阵
determinant(m)       // 行列式（OpenGL 4.0+）
```

#### 2.7.6 纹理采样函数

```glsl
// 基础采样
texture(sampler, coord)              // 2D 纹理采样
texture(sampler, coord, bias)        // 带 LOD 偏置
texelFetch(sampler, icoord, lod)     // 直接取纹素（不经过过滤）

// 派生纹理
textureSize(sampler, lod)            // 纹理尺寸 (ivec2)
textureQueryLod(sampler, coord)      // LOD 信息 (vec2)

// 带比较的采样（阴影贴图）
texture(samplerShadow, coord)        // 返回比较结果 [0,1]
```

### 2.8 存储限定符 (Storage Qualifiers)

```glsl
// ===== 输入/输出 =====
in    vec3 Position;      // 从上一阶段输入（顶点着色器从顶点属性接收）
out   vec4 fragColor;     // 输出到下一阶段（片元着色器输出到帧缓冲）

// ===== Uniform（常量，每帧更新一次） =====
uniform mat4 ModelViewMat;

// ===== 布局限定符 =====
layout(location = 0) in vec3 Position;  // 显式指定属性位置
layout(std140) uniform BlockName { ... }; // std140 内存布局
layout(binding = 0) uniform sampler2D Sampler0; // 绑定到纹理单元 0
```

### 2.9 std140 布局规则

std140（Standard 140）是 OpenGL 中 Uniform Buffer Object (UBO) 的标准布局规则，Minecraft 中使用此规则。

**对齐规则：**

| 类型 | 基对齐 | 说明 |
|------|--------|------|
| `float`, `int`, `bool` | 4 字节 | 标量 |
| `vec2` | 8 字节 | 2 个 float |
| `vec3`, `vec4` | 16 字节 | 按 vec4 对齐 |
| `mat4` | 64 字节 | 4 个 vec4（列主序） |
| `mat3` | 48 字节 | 3 个 vec4（每列末尾空 1 个 float） |

**项目中的实际 std140 布局示例：**

```glsl
layout(std140) uniform SkinData {
    mat4 Bones[128];            // 128 × 64 = 8192 字节
    mat3 NormalBones[128];      // 128 × 48 = 6144 字节
    ivec4 LightOverlay;         // 16 字节
    vec4 Color;                 // 16 字节
};                              // 总计: 14368 字节

// Java 侧布局计算：
// Bones[128] = 128 * 64 = 8192
// NormalBones[128] = 128 * 48 = 6144 (每列 4 个 float，只写 3 个跳 1 个)
// LightOverlay = 16
// Color = 16
// 总计 = 14368 字节
```

### 2.10 Mojang 着色器导入机制

Minecraft 支持使用 `#moj_import` 指令导入共享着色器代码：

```glsl
#moj_import <minecraft:fog.glsl>        // 雾计算函数
#moj_import <minecraft:light.glsl>      // 光照计算函数
#moj_import <minecraft:dynamictransforms.glsl>  // 动态变换
#moj_import <minecraft:projection.glsl> // 投影相关
#moj_import <minecraft:sample_lightmap.glsl>    // 光照贴图采样
```

**`fog.glsl` 提供的函数：**

```glsl
float fog_distance(vec3 pos, int shape); // 计算雾距离
vec4 fog_color(vec4 color, float distance, ...); // 应用雾颜色
```

---

## 3. Minecraft Rendering Pipeline

### 3.1 完整帧渲染流程

```
每一帧开始
│
├─ 1. 窗口事件处理 (Window.updateDisplay)
│     ├─ 鼠标/键盘事件分发
│     └─ 窗口大小变化处理
│
├─ 2. 游戏刻更新 (MinecraftClient.runTick)
│     ├─ 世界更新 (World.tick)
│     ├─ 实体更新 (Entity.tick)
│     └─ 粒子更新
│
├─ 3. 渲染准备
│     ├─ 相机设置 (Camera.setup)
│     ├─ 视锥体裁剪准备
│     └─ 光照更新
│
├─ 4. 世界渲染 (WorldRenderer)
│     ├─ 4a. 清除帧缓冲
│     │     ├─ 清除颜色缓冲 (clearColor + glClear)
│     │     ├─ 清除深度缓冲 (glClear(GL_DEPTH_BUFFER_BIT))
│     │     └─ 天空盒/雾颜色设置
│     │
│     ├─ 4b. 渲染天空盒
│     │     ├─ 自定义天空 (如果有)
│     │     ├─ 太阳/月亮
│     │     └─ 星星
│     │
│     ├─ 4c. 渲染地形 (区块)
│     │     ├─ 视锥体裁剪 (Frustum Culling)
│     │     ├─ 排序透明区块
│     │     ├─ 渲染 Solid 层 (不透明)
│     │     ├─ 渲染 Cutout 层 (扣像)
│     │     ├─ 渲染 Cutout Mipped 层 (带 Mipmap 扣像)
│     │     └─ 渲染 Translucent 层 (半透明, 从远到近)
│     │
│     ├─ 4d. 渲染实体 (EntityRenderDispatcher)
│     │     ├─ 遍历所有可见实体
│     │     ├─ 每个实体：
│     │     │   ├─ 获取渲染器 (EntityRenderer)
│     │     │   ├─ 提取渲染状态 (extractRenderState)
│     │     │   └─ 提交渲染 (submit)
│     │     └─ 批量提交优化
│     │
│     ├─ 4e. 渲染自定义粒子
│     │
│     ├─ 4f. 渲染方块实体 (BlockEntity)
│     │
│     └─ 4g. 渲染云/雾后处理
│
├─ 5. 渲染 GUI
│     ├─ 抬头显示 (HUD)
│     ├─ 物品栏 (Inventory)
│     └─ 当前屏幕 (Screen)
│
├─ 6. 手持物品渲染
│     ├─ 第一人称手
│     └─ 手持物品
│
├─ 7. 后处理 (Post-Processing)
│     └─ 应用着色器效果 (如果启用)
│
└─ 8. 交换缓冲 (Swap Buffers)
      └─ 双缓冲交换，显示一帧画面
```

### 3.2 渲染层级深度解析

区块渲染的顺序对最终画面至关重要，尤其是半透明物体的排序：

| 层 | RenderType 组 | 混合 | 深度写入 | 排序方式 | 用途 |
|----|--------------|------|----------|----------|------|
| 1 | `SOLID` | 无 | 开启 | 无 | 石头、泥土、木头等不透明方块 |
| 2 | `CUTOUT` | 无 (Alpha Test) | 开启 | 无 | 树叶、玻璃板、栏杆 |
| 3 | `CUTOUT_MIPPED` | 无 (Alpha Test) | 开启 | 无 | 带 Mipmap 的扣像方块 |
| 4 | `TRANSLUCENT` | 预乘 Alpha | 关闭 | 从远到近 | 水、玻璃、彩色玻璃 |

**为什么半透明要排序：**

```
正确:  不透明 A → 不透明 B → 半透明 C (远) → 半透明 D (近)
        └─ 先画 A 被 C 正确遮挡 ──┘  └─ C 在 D 后面，正确混合 ──┘

错误:  不透明 A → 半透明 C → 不透明 B → 半透明 D
        └─ C 在那半透明着 ──┘     ↑ B 把 C 完全挡住了！(虽然 C 应该部分遮挡 B)
```

### 3.3 实体渲染架构 (1.20+ NeoForge)

#### 3.3.1 传统的 EntityRenderer 模式

```java
// 1. 注册渲染器
EntityRenderers.register(ENTITY_TYPE, MyRenderer::new);

// 2. 渲染器实现
public class MyRenderer extends EntityRenderer<MyEntity, MyRenderState> {

    public MyRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

    @Override
    public MyRenderState createRenderState() { return new MyRenderState(); }

    @Override
    public void extractRenderState(MyEntity entity, MyRenderState state,
                                    float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        // 从实体拷贝数据到渲染状态（线程安全）
        state.myData = entity.getSomeData();
    }

    @Override
    public void submit(MyRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        // 实际渲染逻辑
        poseStack.pushPose();
        // ... 变换和绘制 ...
        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> {
            // 顶点绘制
        });
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
```

#### 3.3.2 GeckoLib 渲染架构

项目中使用的 GeckoLib 渲染链：

```
GeoRenderer.submitRenderTasks()
  ├─ 准备 RenderPassInfo (含有 PoseStack、模型、着色器状态等)
  ├─ 执行骨骼更新 (BoneUpdaters)
  │     └─ 更新 BoneSnapshot (位置/旋转/缩放/显隐)
  ├─ 主渲染趟 (preRenderPosed)
  │     └─ 遍历骨骼树：
  │           ├─ pushPose → prepBone(translate+rotate+scale)
  │           ├─ 画本骨骼自己的方块 → popPose
  │           └─ 递归子骨骼 (相同流程)
  └─ PerBoneRender 趟 (额外绘制层)
        └─ TranslucentBoneGeoLayer 等层的定制绘制
```

**骨骼遍历流程（BoneWalker.render 核心逻辑）：**

```java
// 深度优先前序遍历骨骼树
for (每个根骨骼) {
    pushPose();
    prepBone(poseStack, bone, snapshot, info, reuseRotation);
    //   ├─ snapshot.translate(poseStack)      — 动画位移
    //   ├─ translateToPivot(poseStack)         — 移到轴心
    //   ├─ rotateZYX(poseStack, z, y, x)       — 绕 ZYX 旋转
    //   ├─ snapshot.scale(poseStack)           — 动画缩放
    //   ├─ updateBonePositionListeners          — 通知监听者
    //   └─ translateAwayFromPivot(poseStack)   — 移回轴心

    if (!selfHidden) {
        画本骨骼的所有立方体;
    }

    if (!childrenHidden) {
        for (每个子骨骼) { 递归 walk(); }
    }

    popPose();
}
```

### 3.4 RenderStage 系统 (NeoForge)

NeoForge 提供了渲染阶段钩子系统，允许在管线的特定点插入自定义渲染：

```java
// 在事件总线中注册
@SubscribeEvent
public static void onRenderLevelStage(RenderLevelStageEvent event) {
    if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
        // 在实体渲染之后插入自定义绘制
        PoseStack poseStack = event.getPoseStack();
        // ... 你的渲染代码 ...
    }
}
```

**可用的渲染阶段（部分）：**

| 阶段 | 时机 |
|------|------|
| `AFTER_SKY` | 天空盒渲染后 |
| `AFTER_SOLID_BLOCKS` | 不透明方块后 |
| `AFTER_CUTOUT_BLOCKS` | 扣像方块后 |
| `AFTER_ENTITIES` | 实体渲染后 |
| `AFTER_BLOCK_ENTITIES` | 方块实体后 |
| `AFTER_TRANSLUCENT_BLOCKS` | 半透明方块后 |
| `AFTER_PARTICLES` | 粒子后 |
| `AFTER_WEATHER` | 天气效果后 |
| `AFTER_LEVEL` | 世界渲染全部完成后 |

### 3.5 Uniform 变量枚举

Minecraft 自动设置的 Uniform 变量（由 RenderSystem 或 Core Shader JSON 提供）：

| Uniform 名称 | GLSL 类型 | 设置时机 | 说明 |
|-------------|-----------|----------|------|
| `ModelViewMat` | `mat4` | 每帧 | 模型视图矩阵 (相机×模型变换) |
| `ProjMat` | `mat4` | 每帧 | 投影矩阵 (透视/正交) |
| `NormalMat` | `mat3` | 每帧 | 法线矩阵 (ModelViewMat 的逆转置) |
| `ColorModulator` | `vec4` | 每次 setShaderColor | 颜色调制（默认 1,1,1,1） |
| `Sampler0` | `sampler2D` | 每次 setShaderTexture | 主纹理 |
| `Sampler1` | `sampler2D` | 自动 | 覆盖纹理（受伤闪白） |
| `Sampler2` | `sampler2D` | 自动 | 光照纹理（方块光） |
| `ScreenSize` | `vec2` | 窗口变化 | 屏幕尺寸（像素） |
| `FogStart` | `float` | 每帧 | 雾起始距离 |
| `FogEnd` | `float` | 每帧 | 雾结束距离 |
| `FogColor` | `vec3` | 每帧 | 雾颜色 |
| `FogShape` | `int` | 每帧 | 0=球面, 1=圆柱 |
| `GameTime` | `float` | 每帧 | 游戏时间（刻，可动画） |
| `LineWidth` | `float` | 渲染线时 | 线宽 |
| `Light0_Direction` | `vec3` | 每帧 | 光照 0 方向 |
| `Light1_Direction` | `vec3` | 每帧 | 光照 1 方向 |
| `DynamicTransforms` | `mat4` | 自动 | 动态变换矩阵 |

### 3.6 帧缓冲 (Framebuffer / RenderTarget)

```java
// 创建自定义渲染目标
RenderTarget target = new RenderTarget(boolean useDepth);
target.resize(width, height);
target.setClearColor(r, g, b, a);

// 绑定为当前渲染目标
target.bindWrite(boolean setDepth);

// 恢复主渲染目标
MinecraftClient.getInstance().getMainRenderTarget().bindWrite(true);

// 全屏 Blit (纹理复制)
// 将主帧缓冲内容复制到自定义目标
```

---

## 4. Minecraft Core Shader JSON

### 4.1 完整 JSON Schema

Core Shader JSON 位于 `assets/<namespace>/shaders/core/<name>.json`，声明了一个完整着色器程序。

```json
{
    // ===== 混合模式 (可选) =====
    "blend": {
        "func": "add",
        "srcrgb": "srcalpha",
        "dstrgb": "1-srcalpha",
        "srcalpha": "srcalpha",
        "dstalpha": "1-srcalpha"
    },

    // ===== 深度测试 (可选) =====
    "depthtest": {
        "func": "lequal"
    },

    // ===== 深度写入 (可选) =====
    "depthwrite": true,

    // ===== 面剔除 (可选) =====
    "cull": {
        "mode": "back"
    },

    // ===== 着色器路径 (必需) =====
    "vertex": "minecraft:shaders/core/entity",
    "fragment": "minecraft:shaders/core/entity",

    // ===== 采样器声明 (可选) =====
    "samplers": [
        { "name": "Sampler0" },
        { "name": "Sampler1" }
    ],

    // ===== Uniform 声明 (可选) =====
    "uniforms": [
        { "name": "ColorModulator", "type": "vector4f", "values": [1.0, 1.0, 1.0, 1.0] },
        { "name": "FogStart", "type": "float", "values": [0.0] },
        { "name": "FogEnd", "type": "float", "values": [1.0] },
        { "name": "FogColor", "type": "vector3f", "values": [0.0, 0.0, 0.0] },
        { "name": "FogShape", "type": "int", "values": [0] },
        { "name": "GameTime", "type": "float", "values": [0.0] },
        { "name": "ScreenSize", "type": "vector2f", "values": [0.0, 0.0] }
    ],

    // ===== 顶点属性声明 (可选) =====
    "attributes": [
        "Position",
        "Color",
        "UV0",
        "UV1",
        "Normal"
    ]
}
```

### 4.2 各字段完全参考

#### 4.2.1 blend — 混合模式

`"blend"` 字段控制颜色混合方式（对应 OpenGL `glBlendFunc` / `glBlendEquation`）：

**`func` (混合函数)：**

| 值 | OpenGL 常量 | 说明 |
|----|-------------|------|
| `"add"` | `GL_FUNC_ADD` | 源 + 目标（默认） |
| `"subtract"` | `GL_FUNC_SUBTRACT` | 源 - 目标 |
| `"reverse_subtract"` | `GL_FUNC_REVERSE_SUBTRACT` | 目标 - 源 |
| `"min"` | `GL_MIN` | 取最小值 |
| `"max"` | `GL_MAX` | 取最大值 |

**`srcrgb` / `dstrgb` / `srcalpha` / `dstalpha` (混合因子)：**

| 值 | OpenGL 常量 | 说明 |
|----|-------------|------|
| `"zero"` | `GL_ZERO` | (0,0,0) |
| `"one"` | `GL_ONE` | (1,1,1) |
| `"srccolor"` | `GL_SRC_COLOR` | 源颜色值 |
| `"1-srccolor"` | `GL_ONE_MINUS_SRC_COLOR` | 1 - 源颜色 |
| `"dstcolor"` | `GL_DST_COLOR` | 目标颜色值 |
| `"1-dstcolor"` | `GL_ONE_MINUS_DST_COLOR` | 1 - 目标颜色 |
| `"srcalpha"` | `GL_SRC_ALPHA` | 源 alpha 值 |
| `"1-srcalpha"` | `GL_ONE_MINUS_SRC_ALPHA` | 1 - 源 alpha |
| `"dstalpha"` | `GL_DST_ALPHA` | 目标 alpha 值 |
| `"1-dstalpha"` | `GL_ONE_MINUS_DST_ALPHA` | 1 - 目标 alpha |

**常用混合配方：**

```json
// 标准 Alpha 混合
{ "func": "add", "srcrgb": "srcalpha", "dstrgb": "1-srcalpha" }
// 方程：结果 = 源 × srcAlpha + 目标 × (1 - srcAlpha)

// 叠加混合 (发光特效)
{ "func": "add", "srcrgb": "one", "dstrgb": "one" }
// 方程：结果 = 源 × 1 + 目标 × 1
// 效果：颜色叠加，越叠越亮

// 预乘 Alpha
{ "func": "add", "srcrgb": "one", "dstrgb": "1-srcalpha" }
// 方程：结果 = 源 × 1 + 目标 × (1 - srcAlpha)
// 纹理颜色预乘了 alpha，边缘无黑边

// 乘法混合 (暗色效果)
{ "func": "add", "srcrgb": "dstcolor", "dstrgb": "zero" }
// 方程：结果 = 源 × 目标颜色
// 效果：颜色相乘，结果比两者都暗

// 2X 混合 (亮化)
{ "func": "add", "srcrgb": "dstcolor", "dstrgb": "srccolor" }
// 方程：结果 = 源 × 目标颜色 + 目标 × 源颜色 = 2 × 源 × 目标
```

#### 4.2.2 depthtest — 深度测试

| 值 | OpenGL 常量 | 说明 |
|----|-------------|------|
| `"always"` | `GL_ALWAYS` | 总是通过（用于特效） |
| `"equal"` | `GL_EQUAL` | 等于当前深度时通过 |
| `"lequal"` | `GL_LEQUAL` | 小于等于当前深度时通过（默认） |
| `"gequal"` | `GL_GEQUAL` | 大于等于当前深度时通过 |
| `"less"` | `GL_LESS` | 小于当前深度时通过 |
| `"greater"` | `GL_GREATER` | 大于当前深度时通过 |
| `"never"` | `GL_NEVER` | 永不通过 |

**深度测试策略选择：**

| 效果类型 | 推荐 depthtest | 理由 |
|----------|---------------|------|
| 不透明物体 | `"lequal"` | 标准深度遮挡 |
| 发光特效 | `"always"` | 总是显示，不被遮挡 |
| 护盾/护罩 | `"always"` 或 `"lequal"` | 可以部分被遮挡 |
| 轨迹/残影 | `"always"` | 在一切之上 |
| UI 叠加 | `"always"` | 不受深度影响 |

#### 4.2.3 depthwrite — 深度写入

```json
true   // 写入深度缓冲（默认，效果会遮挡后面的物体）
false  // 不写入深度缓冲（特效不遮挡物体，透明叠加）
```

**何时设为 false：**
- 半透明特效（粒子、护盾、光晕）
- 叠加 UI
- 任何不需要遮挡后续渲染的视觉效果

#### 4.2.4 cull — 面剔除

```json
{ "mode": "back" }     // 剔除背面（默认，从内部看不到）
{ "mode": "front" }    // 剔除正面（从外部看不到）
{ "mode": "none" }     // 不剔除（双面渲染，透明效果常用）
```

**面剔除判断依据：** 三角形的顶点绕行方向（顺时针/逆时针），经过透视变换后的屏幕空间方向。

#### 4.2.5 vertex / fragment — 着色器路径

```json
// 路径规则:
// assets/<namespace>/shaders/core/<path>.vsh
// assets/<namespace>/shaders/core/<path>.fsh
// JSON 中填写 "namespace:shaders/core/path"（不含扩展名）

"vertex": "minecraft:shaders/core/entity"
// → assets/minecraft/shaders/core/entity.vsh

"vertex": "minegenshin:shaders/core/my_effect"
// → assets/minegenshin/shaders/core/my_effect.vsh
```

#### 4.2.6 samplers — 纹理采样器

```json
"samplers": [
    { "name": "Sampler0" },   // 纹理单元 0 → 主纹理
    { "name": "Sampler1" },   // 纹理单元 1 → 覆盖纹理
    { "name": "Sampler2" }    // 纹理单元 2 → 光照纹理
]
```

纹理单元绑定规则：
- `Sampler0` — 默认由 `RenderSystem.setShaderTexture(0, location)` 绑定
- `Sampler1` — 覆盖纹理（受伤闪白），自动由实体渲染系统绑定
- `Sampler2` — 光照纹理（方块光），自动由渲染系统绑定
- **自定义采样器** — 需要在代码中绑定（`renderPass.bindTexture(name, view, sampler)`）

#### 4.2.7 uniforms — Uniform 变量

**完整类型表：**

| JSON type | GLSL 类型 | values 数组长度 | 示例 |
|-----------|-----------|----------------|------|
| `"float"` | `float` | 1 | `[0.5]` |
| `"int"` | `int` | 1 | `[1]` |
| `"vector2f"` | `vec2` | 2 | `[0.5, 1.0]` |
| `"vector3f"` | `vec3` | 3 | `[1.0, 0.0, 0.0]` |
| `"vector4f"` | `vec4` | 4 | `[1.0, 1.0, 1.0, 1.0]` |
| `"matrix4f"` | `mat4` | 16 | 列主序 4x4 矩阵 |

**自定义 Uniform 的代码端绑定：**

```java
// 获取 ShaderInstance 中的 Uniform
ShaderInstance shader = RenderSystem.getShader();
Uniform myUniform = shader.getUniform("MyCustomUniform");

// 更新 Uniform 值
if (myUniform != null) {
    myUniform.set(floatValue);        // float
    myUniform.set(vec4fValue);         // vec4
    myUniform.setSafe(vec3fValue);     // vec3 (线程安全)
}
```

#### 4.2.8 attributes — 顶点属性

```json
"attributes": [
    "Position",   // vec3 — 必须
    "Color",      // vec4 — 可选
    "UV0",        // vec2 — 可选
    "UV1",        // vec2 — 可选 (overlay)
    "UV2",        // vec2 — 可选 (lightmap)
    "Normal"      // vec3 — 可选
]
```

这些名字必须与 `VertexFormat` 中的属性名称**完全一致**，运行时通过名字匹配。

### 4.3 完整示例：渐变发光效果

#### JSON 配置 (`assets/minegenshin/shaders/core/glow_pulse.json`)

```json
{
    "blend": {
        "func": "add",
        "srcrgb": "one",
        "dstrgb": "one"
    },
    "depthtest": {
        "func": "always"
    },
    "depthwrite": false,
    "cull": {
        "mode": "none"
    },
    "vertex": "minegenshin:shaders/core/glow_pulse",
    "fragment": "minegenshin:shaders/core/glow_pulse",
    "samplers": [
        { "name": "Sampler0" }
    ],
    "uniforms": [
        { "name": "ColorModulator", "type": "vector4f", "values": [1.0, 1.0, 1.0, 1.0] },
        { "name": "GameTime", "type": "float", "values": [0.0] },
        { "name": "ScreenSize", "type": "vector2f", "values": [0.0, 0.0] },
        { "name": "PulseColor", "type": "vector3f", "values": [1.0, 0.3, 0.1] },
        { "name": "PulseSpeed", "type": "float", "values": [2.0] }
    ],
    "attributes": [
        "Position",
        "Color",
        "UV0"
    ]
}
```

#### 顶点着色器 (`assets/minegenshin/shaders/core/glow_pulse.vsh`)

```glsl
#version 330

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec2 texCoord;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    texCoord = UV0;
}
```

#### 片元着色器 (`assets/minegenshin/shaders/core/glow_pulse.fsh`)

```glsl
#version 330

in vec4 vertexColor;
in vec2 texCoord;

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;
uniform vec3 PulseColor;
uniform float PulseSpeed;

out vec4 fragColor;

void main() {
    vec4 texColor = texture(Sampler0, texCoord);

    // 脉冲计算
    float pulse = 0.5 + 0.5 * sin(GameTime * PulseSpeed * 0.05);

    // 从边缘到中心脉冲（用 UV 距离中心的距离）
    float dist = length(texCoord - 0.5);
    float edgeGlow = smoothstep(0.5, 0.0, dist); // 边缘发光

    // 组合颜色
    vec3 finalColor = texColor.rgb * vertexColor.rgb * ColorModulator.rgb;
    vec3 glowColor = PulseColor * pulse * edgeGlow;
    finalColor += glowColor;

    float alpha = texColor.a * vertexColor.a * ColorModulator.a;
    alpha *= (0.7 + 0.3 * pulse);

    fragColor = vec4(finalColor, alpha);

    if (fragColor.a < 0.01) {
        discard;
    }
}
```

---

## 5. Minecraft Model Rendering

### 5.1 模型系统架构

Minecraft 模型系统从资源包加载流程：

```
minecraft:models/block/xxx.json       ← 方块模型
minecraft:models/item/xxx.json        ← 物品模型
minecraft:blockstates/xxx.json        ← 方块状态映射
GeckoLib geo.json                     ← GeckoLib 几何体模型 (实体)
```

### 5.2 标准 JSON 方块/物品模型

#### 5.2.1 模型元素 (Elements) 详解

```json
{
    "elements": [
        {
            "from": [0, 0, 0],      // 起始坐标 (16x16x16 世界空间)
            "to": [16, 16, 16],     // 结束坐标
            "rotation": {           // 可选旋转
                "origin": [8, 8, 8],    // 旋转轴心
                "axis": "y",           // 旋转轴 (x, y, z)
                "angle": 45.0,         // 旋转角度 (22.5 的倍数)
                "rescale": false       // 是否重新缩放以适配边界
            },
            "faces": {
                "north": {          // 面方向
                    "uv": [0, 0, 16, 16],  // UV 坐标 (像素)
                    "texture": "#all",     // 纹理变量引用
                    "cullface": "north",   // 条件剔除方向
                    "rotation": 0,         // UV 旋转 (0/90/180/270)
                    "tintindex": 0         // 染色索引 (如草地)
                },
                "east": { ... },
                "south": { ... },
                "west": { ... },
                "up": { ... },
                "down": { ... }
            },
            "shade": true            // 是否受光照影响
        }
    ]
}
```

**面方向对应关系：**

```
北 (north):  Z =