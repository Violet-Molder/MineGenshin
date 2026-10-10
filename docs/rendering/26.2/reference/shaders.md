# 3. 着色器、渲染管线与 GPU 数据


## 3.1 资源包里的着色器长什么样

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

## 3.2 `ShaderManager`：管的是源码，不是「编译好的对象」

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

## 3.3 `#moj_import`：预处理器的 include 机制

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

## 3.4 自定义 `RenderPipeline`：注册时机与两条硬约束

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

## 3.5 顶点属性：26.2 是**按名字**接，不是按 location

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

## 3.6 常量缓冲（UBO）与 std140

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
（链路细节见 [§7.1](/doc/rendering-26.2-reference-gpu-skinning#71-本项目的骨骼调色板链路可直接照抄的-gpu-蒙皮实现)），
那是最值得抄的现成实现。

## 3.7 着色器出问题时怎么查

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

## 3.8 案例：从零加一条自定义管线，跑到屏幕上

目标：在自己的模组里加一条管线，用它画一个「带自己参数、能控制强度」的发光面片。
下面按**动手顺序**列，每一步都写清「做完怎么知道这步是对的」。

### 第 1 步：写着色器（两个文件）

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

### 第 2 步：在 Java 里声明这条管线

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

### 第 3 步：注册

```java
@SubscribeEvent
static void onRegister(RegisterRenderPipelinesEvent event) {
    event.registerPipeline(MgPipelines.GLOW);
}
```

**怎么知道对了**：注册事件在**默认管线之后**、**mod 总线**上触发；如果启动时抛
`Duplicate bind name 'X' in bind group layout`，说明你的 `BindGroupLayout` 与父片段里的名字撞了
（`BindGroupLayout.ensureCompatible`），改名或去掉重复声明即可。

### 第 4 步：决定怎么画

两种画法任选（§2.10 有对比）：

- 一次性几何 → 在 `SubmitCustomGeometryEvent` 里 `submitCustomGeometry(pose, 自己的 RenderType, ...)`；
- 自己管缓冲 → `BufferBuilder` 建 `MeshData`，上传成 `GpuBuffer`，
  在 `RenderPass` 里 `setPipeline(MgPipelines.GLOW)` + `setUniform("GlowData", buffer)` + `drawIndexed`。

### 第 5 步：常见失败对照

| 现象 | 最可能的原因 |
|---|---|
| 什么都不显示 | 没绑定自己的 uniform（`setUniform("GlowData", …)`）或 UBO 里 `Strength` 是 0 |
| 只在特定角度可见 | 背面被剔除：`withCull(true)`，先设 `false` 试 |
| 前后遮挡关系不对 | 深度状态：`withDepthStencilState(...)` 传了不写深度的状态 |
| 颜色比预期暗一倍 | 色彩空间/混合：`ColorTargetState` 的 `BlendFunction` 选错（直通 vs 半透明） |
| 报找不到 `GlowData` | GLSL 块名、`BindGroupLayout.withUniform` 名字、`setUniform` 名字，三者必须完全一致 |
| 参数值忽大忽小 | std140 对齐：`Std140SizeCalculator` 与 `Std140Builder` 的序列不一致（§3.6） |
