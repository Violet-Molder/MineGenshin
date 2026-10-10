# 5. 着色器与渲染类型

1.21.1 的着色器是**资源**：`assets/<ns>/shaders/core/<name>.json + .vsh + .fsh`。

## 5.1 JSON 里写什么

```json
{
  "vertex": "minegenshin:glow", "fragment": "minegenshin:glow",
  "attributes": [ "Position", "Color", "UV0", "UV2" ],
  "samplers": [ { "name": "Sampler0" } ],
  "uniforms": [ { "name": "GlowStrength", "type": "float", "count": 1, "values": [ 1.0 ] } ]
}
```

- `attributes` 的名字必须与 `VertexFormat` 的元素一一对应（顺序也一致）；
- `samplers` 的名字要与 Java 侧 `setSampler("Sampler0", tex)` 一致；
- `blend` 可以写，但挂进 `RenderType` 后由 `TransparencyStateShard` 说了算。

## 5.2 常用内置 uniform

`ModelViewMat`（模型视图）、`ProjMat`（投影）、`ColorModulator`（顶点色乘子）、
`GameTime`（秒）、`ScreenSize`、`FogStart` / `FogEnd` / `FogColor`。
**不用在 JSON 里声明**，shader 里用到就会自动填。

自己的参数：`shader.safeGetUniform("X").set(v)`；采样器：`shader.setSampler("Sampler1", tex)`。

## 5.3 `#moj_import`

```glsl
#moj_import <minecraft:fog.glsl>     // 内置：雾
#moj_import <light.glsl>            // 内置：原版光照
#moj_import "local.glsl"            // 自己的：assets/<ns>/shaders/include/
```

它只是把文件内容拼进来（文本展开），条件编译用 GLSL 自己的 `#define`。

## 5.4 注册与接线

```java
@SubscribeEvent
static void onRegisterShaders(RegisterShadersEvent event) throws IOException {   // mod 总线
    event.registerShader(new ShaderInstance(event.getResourceProvider(), "minegenshin:glow",
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP), shader -> GLOW = shader);
}

private static final RenderType GLOW_TYPE = RenderType.create("minegenshin_glow",
        DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 1536, false, true,
        RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(() -> GLOW))   // 用 Supplier
                .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE).setCullState(RenderStateShard.NO_CULL)
                .createCompositeState(false));
```

**`ShaderStateShard` 收 Supplier**，就是为了资源重载后能拿到新实例；不要缓存 `ShaderInstance` 引用。

## 5.5 后处理

全屏后处理走 `PostChain` / `PostPass`，资源在 `assets/<ns>/shaders/post/<name>.json`（pass 的 shader 放
`shaders/program/`）。Photon 的 Bloom 就建在它上面。

## 5.6 排错

| 症状 | 先查 |
| --- | --- |
| 全黑/全白 | 采样器名字对不上；`fragColor` 没写 |
| 顶点挤在一点 | JSON 漏了 `ModelViewMat` / `ProjMat` |
| 洋红 | 贴图不存在 |
| 重载后失效 | 缓存了旧 `ShaderInstance` |

深入：[完全参考 3. 着色器、渲染管线与 GPU 数据](/doc/rendering-1.21.1-reference-shaders)。