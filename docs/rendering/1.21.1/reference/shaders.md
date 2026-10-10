# 3. 着色器、渲染管线与 GPU 数据


## 3.1 core shader 三件套

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

## 3.2 `#moj_import`

`GlslPreprocessor`（`com/mojang/blaze3d/preprocessor/GlslPreprocessor.java:20`）识别两种写法：

```glsl
#moj_import <minecraft:fog.glsl>     // 命名空间路径
#moj_import "local.glsl"             // 相对路径
```

它在编译前做文本展开（`:68`）；导入失败会把 GLSL 错误原样抛进 `ShaderInstance` 的编译日志。

## 3.3 注册自己的 `ShaderInstance`

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

## 3.4 接到 `RenderType`

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

## 3.5 逐顶点数据 vs uniform

| 方式 | 粒度 | 什么时候用 |
| --- | --- | --- |
| 顶点属性（位置 / 颜色 / UV / 光照） | 每顶点 | 位置、颜色、UV、法线 |
| uniform | 每次绘制 | 时间、屏幕尺寸、材质参数 |
| 贴图 | 采样 | 渐变、噪声、遮罩 |

1.21.1 的 core shader **没有 UBO / std140 那套**：多个 uniform 就是多次 `set`。

## 3.6 后处理

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

## 3.7 排错

| 症状 | 原因 |
| --- | --- |
| 采样器全黑 | `samplers` 名字与 `setSampler` 名字不一致 |
| 顶点不动 | `ModelViewMat` 未在 JSON 声明 |
| 一次性生效、重载后失效 | 缓存了旧 `ShaderInstance` |
| GLES / 移动启动器报错 | 整数与浮点混算（`1` 要写 `1.0`） |

---
