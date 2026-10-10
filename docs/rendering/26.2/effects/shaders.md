# 5. 着色器：26.2 把「Core Shader JSON」删掉了


这是本文最需要强调的**版本断裂点**，也是老文档错得最彻底的一节。

## 5.1 结论先讲

| | 1.20 / 1.21 | **26.2** |
|---|---|---|
| 核心着色器定义 | `assets/<ns>/shaders/core/xxx.json` + `.vsh` + `.fsh`，JSON 里写 blend/depthtest/cull/samplers/uniforms/attributes | **没有 JSON 了**。只有 `.vsh` / `.fsh` 源文件 |
| 渲染状态（混合、深度、剔除、顶点格式） | 写在那个 JSON 里 | 写在 Java 的 `RenderPipeline` 里（原版是 `RenderPipelines` 常量，模组用 `RegisterRenderPipelinesEvent` 注册） |
| 后处理链 | `shaders/post/*.json` | `post_effect/*.json`（`PostChainConfig`） |

源码依据（`net/minecraft/client/renderer/ShaderManager.java`）：

```java
public static final String SHADER_PATH = "shaders";
private static final String SHADER_INCLUDE_PATH = "shaders/include/";
private static final FileToIdConverter POST_CHAIN_ID_CONVERTER = FileToIdConverter.json("post_effect");
...
Map<Identifier, Resource> files = manager.listResources("shaders", ShaderManager::isShader);
for (Entry<Identifier, Resource> entry : files.entrySet()) {
    Identifier location = entry.getKey();
    ShaderType shaderType = ShaderType.byLocation(location);   // 按扩展名判断
    if (shaderType != null) loadShader(location, entry.getValue(), shaderType, files, shaderSources);
}
```

`ShaderType` 只有两种，靠**扩展名**识别：

```java
public enum ShaderType {
    VERTEX("vertex", ".vsh"),
    FRAGMENT("fragment", ".fsh");
    public FileToIdConverter idConverter() { return new FileToIdConverter("shaders", this.extension); }
}
```

所以 26.2 里一个「核心着色器」就是：

```
assets/<namespace>/shaders/<path>.vsh
assets/<namespace>/shaders/<path>.fsh
```

**没有 `.json`**。`blend` / `depth` / `cull` / 顶点属性布局这些全部搬到 `RenderPipeline` 里去了。

> 如果你手上有一份写着 `"blend": {"func": "add", "srcrgb": "srcalpha"}` 的文档或代码，它对 26.2 **无效**。

## 5.2 `RenderPipeline` 描述什么

`com.mojang.blaze3d.pipeline.RenderPipeline` 把「怎么画」的全部状态收进一个不可变对象，主要包含：

- **顶点格式**（`VertexFormat`）与图元拓扑（`PrimitiveTopology`）
- **着色器**（`ShaderSource`：顶点/片元源文件 id）
- **混合**：`BlendFunction`（源因子、目标因子、`BlendEquation`）与 `ColorTargetState`
- **深度**：`DepthStencilState` + `CompareOp`（深度测试）与深度写入开关
- **剔除**：`cull` 开关
- **绑定组布局**：`BindGroupLayout`（uniform / sampler 怎么绑）
- **多边形模式**：`PolygonMode`（填充 / 线框）
- **定义开关**：`ShaderDefines`（`#define` 注入）

**合批的前提**：几何、材质 program、纹理/采样器状态、渲染层、顶点布局**全都相同**才能并成一次 draw。
这就是为什么「给每个粒子单独换一件材质」会把性能打崩。

## 5.3 GLSL 速查

**类型**

| 类别 | 写法 |
|---|---|
| 标量 | `float` `int` `uint` `bool` `double` |
| 向量 | `vec2/3/4`、`ivec*`、`uvec*`、`bvec*`；分量 `.x/.y/.z/.w` 或 `.r/.g/.b/.a` 或 `[i]` |
| 矩阵 | `mat2/3/4`（列主序）、`mat2x3` 等非方阵 |
| 采样器 | `sampler1D/2D/3D/Cube`、`sampler2DArray`、`sampler2DShadow`、`isampler2D`、`usampler2D` |
| 不透明类型 | `sampler*`、`image*`、原子计数器 —— 只能 `uniform` 传递，不能赋值比较 |

**限定符（存储 / 参数 / 精度）**

| 限定符 | 含义 |
|---|---|
| `const` | 编译期常量 |
| `in` / `out` | 顶点：输入属性 / 输出到下一阶段；片元：输入 varying / 输出颜色 |
| `uniform` | 每次 draw 由 CPU 提供，所有顶点/片元相同 |
| `flat` / `noperspective` / `smooth` | varying 插值方式（整数 varying **必须** `flat`） |
| `layout(location = N)` | 顶点属性槽位；片元输出索引 |
| `highp` / `mediump` / `lowp` | 精度限定（桌面 GL 里基本被忽略，写 shader 时按需保留） |

**内置变量**

| 阶段 | 变量 |
|---|---|
| 顶点 | `gl_Position`（必须写）、`gl_VertexID`、`gl_InstanceID`、`gl_PointSize` |
| 片元 | `gl_FragCoord`、`gl_FrontFacing`、`gl_PointCoord`、`discard`、`gl_FragDepth` |

**常用内置函数**（按用途分组）

| 组 | 函数 |
|---|---|
| 角度 | `radians` `degrees` `sin` `cos` `tan` `asin` `acos` `atan(y,x)` |
| 指数 | `pow` `exp` `log` `exp2` `log2` `sqrt` `inversesqrt` |
| 通用 | `abs` `sign` `floor` `ceil` `trunc` `round` `fract` `mod` `min` `max` `clamp` `mix` `step` `smoothstep` |
| 几何 | `length` `distance` `dot` `cross` `normalize` `faceforward` `reflect` `refract` |
| 矩阵 | `matrixCompMult` `transpose` `inverse` `determinant` |
| 纹理 | `texture(sampler, uv)` `textureLod` `textureGrad` `texelFetch` `textureSize` |
| 导数 | `dFdx` `dFdy` `fwidth`（片元专用，做抗锯齿/法线重建常用） |

**写 shader 的三条纪律**

1. **整数 varying 一定加 `flat`**，否则插值出来的值没有意义。
2. **不要用 `if` 里做纹理采样**（除非是 uniform 分支），不同 GPU 对非一致控制流的处理差异很大。
3. **`discard` 会关掉 early-Z**，大面积使用时性能损失明显；能用 alpha blend 表达就别 discard。

## 5.4 `#moj_import` 与 include 机制

26.2 仍然在加载 `.vsh` / `.fsh` 之前跑一遍 `GlslPreprocessor`，支持：

- **绝对导入**：`#moj_import <namespace:path>` → 解析到 `assets/<namespace>/shaders/include/<path>`
- **相对导入**：`#moj_import "relative/path.glsl"` → 相对当前文件目录

源码依据（`ShaderManager.createPreprocessor`）：

```java
if (isRelative) {
    locationx = parentLocation.withPath(parentPath -> FileUtil.normalizeResourcePath(parentPath + path));
} else {
    locationx = Identifier.parse(path).withPrefix("shaders/include/");
}
```

所以公共函数库的正确位置是：

```
assets/<namespace>/shaders/include/<name>.glsl
```

Photon 自带了一批 include（例如给粒子用的 `photon:particle.glsl`），
自定义材质可以直接 `#moj_import <photon:particle.glsl>` 复用它的 `ParticleData` 结构。

## 5.5 std140 布局

只要往 uniform block / 常量缓冲里塞数据，就必须守 std140：

| 类型 | 对齐 | 占位 |
|---|---|---|
| `float` / `int` / `bool` | 4 | 4 |
| `vec2` | 8 | 8 |
| `vec3` | **16** | **16**（不是 12！） |
| `vec4` | 16 | 16 |
| `mat3` | 16（按列） | **48**（三列 × 16） |
| `mat4` | 16（按列） | 64 |
| 数组 | 元素对齐向上取整到 16 | 每元素占整份对齐后大小 |

典型踩坑：**把 `mat3` 数组紧接在 `mat4` 数组后面写**，导致后面所有数据错位。
正确做法是显式 `data.position(OFFSET)` 跳到下一个块（Photon 的骨骼/法线矩阵缓冲就是这么处理的）。

## 5.6 顶点属性与 location

| 寄存器 | 常见用途 |
|---|---|
| `Position` | 位置 |
| `Color` | 顶点色 |
| `UV0` | 主纹理坐标 |
| `UV1` | 光照（packed light，两个 16 位） |
| `UV2` | overlay（packed overlay） |
| `Normal` | 法线 |
| `LineWidth` | 线宽 |

**自己的自定义属性（比如每实例的 transform、自定义 GPU 数据）必须自己分配 location**，
并且 **location 会随「启用哪些内置通道」而移动** ——
所以发射器设置与 GLSL 里的 `layout(location = N)` 必须一起维护，改一个就要改另一个。

## 5.7 着色器出问题时怎么查

| 症状 | 先查 |
|---|---|
| 全黑 | 编译日志；然后把常量颜色直接接到输出确认管线通不通 |
| 全白/过曝 | 混合模式与颜色约定不匹配（是否 premultiplied）、是否走了 HDR 目标 |
| 数据恒为 0 | 该数据通道没启用，或渲染路径（Instanced / CPU）不支持 —— **不支持时返回 0 而不是报错** |
| 只有部分设备出错 | 精度限定、`discard`、非一致控制流、`mat3` 对齐 |
| 改了 shader 没生效 | 资源重载（F3+T）；再清客户端缓存 |

**通用调试法**：从「常量 → 输出」开始，逐个把输入接回来，一次只加一项。
这比盯着一个复杂 shader 猜要快一个数量级。

---
