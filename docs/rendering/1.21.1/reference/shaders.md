# 3. 着色器、渲染管线与 GPU 数据

`RenderType` 里那个 `ShaderStateShard` 指向的到底是什么？这一章把「一份 core shader 从文件到画面」
的每一步讲清楚：文件放哪、JSON 每个字段什么意思、GLSL 怎么写、Java 侧怎么注册与传参、
后处理怎么接、坏了怎么查。

## 3.1 core shader 三件套与加载流程

1.21.1 的着色器是**资源**，不是代码：

```text
assets/<namespace>/shaders/core/<name>.json     ← 声明：用哪两个着色器、有哪些属性/采样器/uniform
assets/<namespace>/shaders/core/<name>.vsh      ← 顶点着色器源码
assets/<namespace>/shaders/core/<name>.fsh      ← 片元着色器源码
```

加载流程：

```text
资源重载
  → NeoForge 触发 RegisterShadersEvent（mod 事件总线）
    → 你 new ShaderInstance(resourceProvider, "ns:name", vertexFormat)
      → 读 <name>.json，按字段把 .vsh/.fsh 交给 GlslPreprocessor 展开 #moj_import
        → 编译链接，失败就把 GLSL 错误（带行号）打到日志
          → onLoaded 回调里拿到可用的 ShaderInstance
```

**关键点**：资源重载会重建 `ShaderInstance`，所以不要在任何静态字段里长期持有它；
用「供应商 + 在 onLoaded 里赋值」的写法。

## 3.2 JSON 字段逐个解释

```json
{
  "blend": { "func": "add", "srcrgb": "srcalpha", "dstrgb": "1-srcalpha" },
  "vertex": "minegenshin:glow",
  "fragment": "minegenshin:glow",
  "attributes": [ "Position", "Color", "UV0", "UV2" ],
  "samplers": [ { "name": "Sampler0" }, { "name": "Sampler1" } ],
  "uniforms": [
    { "name": "ModelViewMat", "type": "matrix4x4", "count": 16, "values": [ ] },
    { "name": "GlowStrength", "type": "float", "count": 1, "values": [ 1.0 ] }
  ]
}
```

| 字段 | 含义 | 注意 |
| --- | --- | --- |
| `blend` | 这份着色器默认的混合方程 | 挂进 `RenderType` 后由 `TransparencyStateShard` 决定，这里写了也会被覆盖 |
| `vertex` / `fragment` | 引用的 shader 名（**不带后缀**） | 名字就是 `shaders/core/<name>.vsh|fsh` |
| `attributes` | 顶点着色器的输入变量名 | 名字必须与 `VertexFormat` 的元素一一对应：`Position` / `Color` / `UV0` / `UV1` / `UV2` / `Normal` |
| `samplers` | 采样器名字 | 名字随便取，但 Java 侧 `setSampler("Sampler0", tex)` 要用同一个 |
| `uniforms` | 需要 Java 侧填的参数 | 没在这里声明的也能用 `getUniform("名字")` 拿到（只要 shader 里真的用了） |

`attributes` 与 `VertexFormat` 的对应关系是**按声明顺序取**的：
你少写一个 `UV2`，顶点数据里的光照就会顺位当成别的属性用 —— 这是「画面莫名其妙」的常见来源。

## 3.3 GLSL 速查（1.21.1 的版本）

原版用的是 GLSL 150 core（对应 GL 3.2 core），要点：

```glsl
#version 150

in vec3 Position;          // 顶点属性（名字与 JSON 的 attributes 对应）
in vec4 Color;             // 顶点色
in vec2 UV0;
in ivec2 UV2;              // 光照：打包成两个 0–15 的整数
uniform sampler2D Sampler0;
uniform mat4 ModelViewMat; // 内置 uniform，见 3.5
uniform mat4 ProjMat;
out vec4 fragColor;        // 顶点 → 片元
out vec2 texCoord;
```

| 主题 | 1.21.1 的写法 |
| --- | --- |
| 采样 | `texture(Sampler0, texCoord)`（`texture2D` 已废弃） |
| 光照 | `texelFetch(Sampler2, UV2 / 16, 0)` 取原版光照贴图 |
| 雾 | 用原版 include：`#moj_import <fog.glsl>`，再调 `linear_fog(...)` |
| 精度 | 桌面 GL 不写也行；写移动端要 `precision mediump float;`（1.21.1 的 GLSL ES 直译器对整数/浮点混算不宽容，`1` 要写 `1.0`） |
| 输出 | 片元着色器必须显式 `out vec4`（core profile 没有 `gl_FragColor`） |

## 3.4 `#moj_import`

`GlslPreprocessor`（`com/mojang/blaze3d/preprocessor/GlslPreprocessor.java:20`）在编译前做文本展开：

```glsl
#moj_import <minecraft:fog.glsl>      // 命名空间路径
#moj_import <light.glsl>
#moj_import "local_helpers.glsl"      // 相对当前 shader 文件
```

常见内置 include（都在 `assets/minecraft/shaders/include/`）：`fog.glsl`（雾）、`light.glsl`（原版光照）、
`projection.glsl`（投影辅助）、`matrix.glsl`（矩阵工具）。自己写的 include 放
`assets/<你的命名空间>/shaders/include/`。

**它不是宏系统**：`#moj_import` 只是把文件内容拼进来，条件编译用 GLSL 自己的 `#define` / `#ifdef`。

## 3.5 内置 uniform 与采样器

`ShaderInstance` 构造时会去拿一批「原版约定了名字」的 uniform（`ShaderInstance.java:163` 起）：

| 名字 | 类型 | 内容 |
| --- | --- | --- |
| `ModelViewMat` | mat4 | 模型视图矩阵（相机的世界矩阵 × 你的 PoseStack） |
| `ProjMat` | mat4 | 投影矩阵 |
| `TextureMat` | mat4 | 贴图矩阵（物品附魔光效那种 UV 动画） |
| `ScreenSize` | vec2 | 屏幕宽高（像素） |
| `ColorModulator` | vec4 | 顶点色乘子，来自 `RenderSystem.setShaderColor` |
| `Light0_Direction` / `Light1_Direction` | vec3 | 方向光（物品栏那种固定视角光照） |
| `GlintAlpha` | float | 附魔光效强度 |
| `FogStart` / `FogEnd` / `FogColor` / `FogShape` | — | 雾 |
| `LineWidth` | float | 线宽（`RenderType.lines()` 用） |
| `GameTime` | float | 游戏时间（秒，带小数） |
| `ChunkOffset` | vec3 | 区块偏移（区块着色器用） |

**内置的不用自己声明**：只要 shader 里用到同名变量，Java 侧每帧会自动填。想加自己的：

```java
shader.safeGetUniform("GlowStrength").set(1.5f);   // 不存在时不会抛异常
shader.getUniform("GlowStrength").set(1.5f);       // 不存在时抛异常，用于「必须有」的参数
shader.setSampler("Sampler1", textureLocation);    // 采样器按名字绑定
shader.apply();                                     // 把 uniform 提交给 GPU
```

`apply()` 是否要手动调：走 `RenderType` 时由驱动在设置 shader 时调；
手写绘制时你自己在 `setShader` 后调一次。

## 3.6 注册自己的 `ShaderInstance`

```java
@EventBusSubscriber(modid = Minegenshin.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MyShaders {
    public static ShaderInstance GLOW;

    @SubscribeEvent
    static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), "minegenshin:glow",
                        DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP),
                shader -> GLOW = shader);          // 唯一安全的赋值点
    }
}
```

要点：

- `RegisterShadersEvent` 在 **mod 事件总线**上（`RegisterShadersEvent.java:27`）；
- 第二个参数 `onLoaded` 在编译成功后回调，资源重载会再次回调；
- 顶点格式必须与 shader 的 `attributes` 匹配（这里是位置+颜色+UV+光照）。

## 3.7 接到 `RenderType`

```java
private static final RenderType GLOW = RenderType.create(
        "minegenshin_glow",
        DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
        VertexFormat.Mode.QUADS, 1536, false, true,
        RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(() -> MyShaders.GLOW))
                .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .createCompositeState(false));
```

`ShaderStateShard` 收的是 **Supplier**，不是实例 —— 这正是为了资源重载后能拿到新实例。

## 3.8 后处理（PostChain）

全屏后处理走原版 `PostChain` / `PostPass`（`PostChain.java:31`、`addPass` `:281`、`process` `:313`），
资源是 JSON：

```json
// assets/<ns>/shaders/post/glow_blur.json
{
  "targets": [ "swap" ],
  "passes": [ {
    "name": "minegenshin:glow_blur",
    "intarget": "minecraft:main",
    "outtarget": "swap",
    "uniforms": [ { "name": "Radius", "values": [ 2.0 ] } ]
  } ]
}
```

- `targets` 声明中间渲染目标（`swap` 是内置的来回缓冲名）；
- `intarget` / `outtarget` 是「从哪读、往哪写」；
- pass 的 shader 放在 `shaders/program/<name>.json + .fsh`（注意是 `program` 不是 `core`：后处理没有顶点属性，只有全屏四边形）。

运行时加载：`new PostChain(textureManager, resourceManager, mainRenderTarget, resourceLocation)`，
再用 `process(partialTick)` 跑一遍。Photon 的 Bloom 与自定义后处理就是建在这套上面的。

## 3.9 排错

| 症状 | 先查什么 |
| --- | --- |
| 全黑 / 全白 | 采样器没绑（`setSampler` 名字对不上 JSON）；或 `fragColor` 没赋值 |
| 顶点挤在一点 | JSON 里漏了 `ModelViewMat` / `ProjMat` 声明 |
| 颜色全是洋红 | 引用了不存在的贴图（原版的「缺失纹理」色） |
| 编译错误只有一行 | 打开日志看 `ShaderInstance` 打出的 GLSL 报错（带行号与出错行内容） |
| 重载资源后失效 | 缓存了旧 `ShaderInstance`，改成 Supplier + onLoaded 赋值 |
| 移动端启动器崩 | 整数当浮点用（`1` 要写 `1.0`）、除以 0、UV 越界采样 |

## 3.10 与 26.2 的差别

26.2 **删掉了 core shader JSON**：渲染管线改在 Java 里用 `RenderPipeline` 描述，
状态、混合、顶点格式、uniform 缓冲（std140）都在代码里声明，资源里只留 GLSL。
所以你在 1.21.1 上写的 `.json + .vsh + .fsh` 那一套，到 26.2 需要整体改写；
反过来，26.2 的 `RenderPipeline` 代码在 1.21.1 上不存在。

下一章：[4. 实体渲染与渲染状态](/doc/rendering-1.21.1-reference-entity-render) 讲实体这一路的数据从哪来、放哪。