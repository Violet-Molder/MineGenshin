# 1. 心智模型：一帧是怎么画出来的

写渲染代码之前先要有一张「一帧的流程图」。这一章把它讲透：谁先谁后、每个阶段能做什么、
为什么不能乱来，以及 1.21.1 与新版（三段式）最本质的差别。

## 1.1 tick 与 render 是同一根线程

客户端主线程做的事情是循环的：

```text
while (游戏在跑) {
    跑若干次 tick   // 目标 20 次/秒：逻辑、网络、AI、状态机
    跑一次 render   // 目标 60+ 次/秒：把当前状态画出来
}
```

结论（这三条决定了后面所有写法）：

- **渲染线程 = 客户端主线程**。不存在「另一个渲染线程」，所以渲染里做重活会直接掉 TPS。
- **帧比 tick 密**：一 tick 之间可能画了几帧，所以位置这类连续变化的东西必须用 `partialTick`（帧内插值）算，
  否则会看到一格一格的跳动。
- **服务端线程碰不到任何渲染类**。服务端只能发「播什么、播在哪」的意图。

## 1.2 一帧的顺序：`GameRenderer` → `LevelRenderer#renderLevel`

`net/minecraft/client/renderer/LevelRenderer.java:914` 的 `renderLevel` 是世界渲染的总调度，
顺序如下（行号取自 `neoforge-21.1.250-sources.jar`）：

| # | 阶段 | 内容 | 位置 |
| --- | --- | --- | --- |
| 1 | 准备 | 相机、投影、雾、清屏、视锥剔除 | `renderLevel` 开头 |
| 2 | 天空 | 太阳/月亮/星空、云层底色 | `renderSky` `:1587` |
| 3 | 不透明方块 | `solid` → `cutoutMipped` → `cutout` 三批 | `:965`–`:969` |
| 4 | **实体** | 所有 `EntityRenderer` | 中段 |
| 5 | 方块实体 | 箱子、告示牌等 | 之后 |
| 6 | 半透明方块 | `translucent`、`tripwire` | `:1177`、`:1196` |
| 7 | 粒子 | `ParticleEngine#render` | — |
| 8 | 云 | `renderClouds` | `:1712` |
| 9 | 天气 / 手 / GUI | `GameRenderer` 接着做 | — |

**为什么必须是这个顺序**：它是「从远到近、从底到顶」的画家算法在 Minecraft 上的具体化 ——
不透明的先画（谁挡谁靠深度缓冲解决），半透明的后画（靠排序 + 不写深度），
粒子在方块之后（要正确被遮挡）、在云之前（云是背景）。

## 1.3 每个阶段能做什么、不能做什么

| 阶段 | 适合放什么 | 不要放什么 |
| --- | --- | --- |
| `AFTER_SKY` | 天空盒、星空、极光这类背景 | 需要被地形遮挡的东西 |
| `AFTER_SOLID_BLOCKS` | 贴地投影、地面范围指示器 | 会被半透明方块盖住的东西 |
| `AFTER_ENTITIES` | 跟随实体的光环、血条、武器拖尾 | 依赖粒子排序的东西 |
| `AFTER_BLOCK_ENTITIES` | 箱子上的特效、容器高亮 | — |
| `AFTER_TRANSLUCENT_BLOCKS` | 水下的额外效果 | — |
| `AFTER_PARTICLES` | 屏幕空间叠加、不受遮挡的光晕 | 会被地形遮住的东西 |
| `AFTER_WEATHER` | 雨雪后的屏幕效果 | — |
| `AFTER_LEVEL` | 全屏后处理的前置 | — |

经验法则：**能被地形遮挡的东西放在地形之后、粒子之前；屏幕空间的东西放在粒子之后。**

## 1.4 挂钩点：NeoForge 的事件

1.21.1 没有「渲染阶段对象」这种东西，插入渲染靠事件：

| 事件 | 频率 | 典型用途 |
| --- | --- | --- |
| `RenderLevelStageEvent` | 上表每个阶段各一次 | 世界内几何、粒子、特效 |
| `RenderFrameEvent.Pre/Post` | 每帧前后 | 全局缓冲准备 / 收尾 |
| `RenderHandEvent` | 第一人称手 | 替换手部渲染 |
| `RenderLivingEvent` | 每个生物 | 修改生物渲染 |
| `RenderNameTagEvent` | 每个名牌 | 改名字显示 |
| `RenderGuiEvent` / `RegisterGuiLayersEvent` | GUI | HUD 元素 |
| `ViewportEvent` / `ComputeFovModifierEvent` | 相机 | 抖动、视场角、倾斜 |
| `RenderHighlightEvent` | 选中方块 | 改描边 |

挂事件的完整骨架：

```java
@EventBusSubscriber(modid = Minegenshin.MOD_ID, value = Dist.CLIENT)
public final class MyRenderEvents {

    @SubscribeEvent
    static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        PoseStack pose = event.getPoseStack();
        Camera camera = event.getCamera();
        float partialTick = event.getPartialTick();
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        // 写顶点……最后别忘 buffers.endBatch();
    }
}
```

`RenderLevelStageEvent` 给你的东西：`getStage()`、`getPoseStack()`（相机相对的变换栈）、
`getCamera()`、`getPartialTick()`、`getProjectionMatrix()`，以及（调 `getFrustum()` 拿到的）视锥。

## 1.5 渲染线程的规则

- 渲染回调里**只做渲染**：不要遍历背包、不要算 A*、不要查实体列表。这些放 tick。
- 从非渲染线程改渲染状态，用 `RenderSystem.recordRenderCall(RenderCall)`（`RenderSystem.java:127`），
  它会把这段操作排到渲染线程执行；`setShader` / `setShaderTexture` 内部也是这么保护的。
- **不要缓存 `PoseStack` / `VertexConsumer` / `ShaderInstance` 的引用跨帧使用**：
  前两者一次绘制内有效，后者在资源重载时会被重新创建。

## 1.6 与 26.2（三段式）的根本差别

| 关注点 | 1.21.1（立即模式） | 26.2（三段式） |
| --- | --- | --- |
| 数据 | 渲染时直接从实体/方块读 | 先 extract 成 `RenderState`，再 submit，再 render |
| 顶点 | 当场写进 `BufferBuilder` | 提交成渲染任务，`SubmitNodeCollector` 统一画 |
| 「写完之后再改一个值」 | 无效（顶点已定型） | 可能有效（还在 RenderState 里） |
| 动画骨骼 | 渲染那一瞬间取 | 快照进 RenderState 带走 |

实践上最容易吃亏的一条：**1.21.1 的渲染数据必须在写顶点那一刻全部就绪**。
26.2 那边可以「先记下来、稍后再补」，这边不行 —— 要补就换个阶段、换个 `RenderType` 再画一遍。

## 1.7 常见误解

| 误解 | 事实 |
| --- | --- |
| 「在渲染事件里改实体坐标就能移动模型」 | 实体坐标是逻辑数据，改了要同步；渲染里改只影响这一帧 |
| 「只要生成了粒子就会一直动」 | 粒子在 tick 与 render 里各自推进；`partialTick` 用于帧内插值 |
| 「渲染事件每帧只触发一次」 | `RenderLevelStageEvent` 按阶段触发，一帧多次 |
| 「`PoseStack` 可以存下来下帧再用」 | 它是一次绘制内的临时栈，跨帧用必炸 |
| 「`double` 精度可以直接画远距离」 | 相机的 `double` 位置减法要早做，画之前转 `float` 的局部坐标 |

下一章：[2. Blaze3D API 地图](/doc/rendering-1.21.1-reference-blaze3d) 讲这条链路上每一段具体用什么类。