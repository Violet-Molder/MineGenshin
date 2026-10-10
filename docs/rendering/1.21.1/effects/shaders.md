# 5. 着色器：1.21.1 还有 Core Shader JSON


## 5.1 结论先讲

26.2 **删掉了 core shader JSON**，换成在 Java 里描述的渲染管线。1.21.1 反过来：

```text
assets/<namespace>/shaders/core/<name>.json     ← 声明顶点/片元着色器与采样器
assets/<namespace>/shaders/core/<name>.vsh      ← GLSL 顶点
assets/<namespace>/shaders/core/<name>.fsh      ← GLSL 片元
```

JSON 里可以声明 attributes、samplers、uniforms（Mojang 反序列化成 `ShaderInstance` 的字段）。
文件没有列出来的 uniform 也可以由 Java 侧 `getUniform("名字")` 拿到 ——
`ShaderInstance` 内置一批常用 uniform（`ModelViewMat` / `ProjMat` / `ColorModulator` /
`FogStart` / `FogEnd` / `GameTime` / `ScreenSize` …），见
`net/minecraft/client/renderer/ShaderInstance.java:163` 起。

## 5.2 最小 core shader

```json
{
  "blend": { "func": "add", "srcrgb": "srcalpha", "dstrgb": "1-srcalpha" },
  "vertex": "minegenshin:glow",
  "fragment": "minegenshin:glow",
  "attributes": [ "Position", "Color", "UV0" ],
  "samplers": [ { "name": "Sampler0" } ],
  "uniforms": [ { "name": "ModelViewMat", "type": "matrix4x4", "count": 16, "values": [ ] } ]
}
```

> 这里也能写 `blend`，但只要你把着色器挂进 `RenderType` 的 `ShaderStateShard`，
> 混合状态就由 `RenderType` 决定 —— 两个地方都写时以 `RenderType` 的为准，容易看错。

## 5.3 `#moj_import`

1.21.1 的 GLSL 支持 `#moj_import <文件名>`，由 `GlslPreprocessor` 处理
（`com/mojang/blaze3d/preprocessor/GlslPreprocessor.java:20`）。它有三个来源：

1. 原版内置的通用头（雾、光照、投影辅助等，见 assets 里的 `minecraft:shaders/include/...`）；
2. 你自己 mod 的 `assets/<ns>/shaders/include/<name>.glsl`；
3. 相对路径导入。

写惯 26.2 的人最容易忘的一点：**1.21.1 的 include 不是编译期宏系统**，
它只是文本替换，`#define` 是 GLSL 自己的预处理。

## 5.4 Uniform 与 Sampler

```java
ShaderInstance shader = MyShaders.GLOW;
shader.safeGetUniform("MyTime").set((float)(System.currentTimeMillis() % 100000) / 1000f);
shader.setSampler("Sampler0", textureLocation);   // 采样器按名字绑定
shader.apply();
```

采样器名字必须与 JSON 里 `samplers` 声明的名字一致；绑定贴图用 `RenderSystem.setShaderTexture(0, ...)`
只对「0 号采样器」有效，其余都要走 `setSampler`。

## 5.5 注册自己的 `ShaderInstance`

NeoForge 21.1 提供 `RegisterShadersEvent`（`net/neoforged/neoforge/client/event/RegisterShadersEvent.java:27`），
它跑在 mod 事件总线上：

```java
@SubscribeEvent
static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
    event.registerShader(
        new ShaderInstance(event.getResourceProvider(), "minegenshin:glow",
                           DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP),
        shader -> MyShaders.GLOW = shader);
}
```

Photon 2.2.8 用的就是这一套：它在 `PhotonShaders#registerShaders`
（`PhotonShaders.java:85`）里一口气注册掉自己全部 core shader。

## 5.6 接到 `RenderType`

```java
new RenderStateShard.ShaderStateShard(() -> MyShaders.GLOW)
```

就这么一句 —— 26.2 里「渲染管线对象 + 命名渲染类型」的两步，在 1.21.1 上合成了一步。

## 5.7 着色器出问题时怎么查

| 症状 | 先看什么 |
| --- | --- |
| 一片纯色（黑 / 白 / 洋红） | 采样器没绑 → 贴图是未初始化纹理 |
| 顶点全挤在原点 | `ModelViewMat` / `ProjMat` 没在 JSON 里声明 |
| 编译报错但日志只有一行 | 打开 `-Dmixin.debug` 没用，要看 `ShaderInstance` 的编译日志（GLSL 报错带行号） |
| 换维度 / 换资源包后失效 | 资源重载会重建 `ShaderInstance`，缓存里存了旧实例 |

---
