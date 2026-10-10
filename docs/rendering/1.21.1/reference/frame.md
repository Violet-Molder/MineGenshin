# 1. 心智模型：1.21.1 的一帧是怎么画出来的


## 1.1 从 tick 到画面

```text
客户端主线程循环
  ├─ tick（20/s）：逻辑、网络、状态机
  └─ render（不限帧）：GameRenderer → LevelRenderer#renderLevel → …… → GUI
```

tick 与 render 在同一个线程里交替发生，所以：

- 「渲染线程」就是「客户端主线程」；
- 在渲染回调里做重活会同时吃掉 TPS 与 FPS；
- 服务端线程完全碰不到渲染类。

## 1.2 `renderLevel` 的顺序

`net/minecraft/client/renderer/LevelRenderer.java:914` 起的 `renderLevel` 是这一帧的总调度：

| 阶段 | 内容 | 位置 |
| --- | --- | --- |
| 准备 | 相机、雾、清屏、投影 | 方法开头 |
| 天空 | `renderSky` | `:1587` |
| 区块（不透明批次） | `RenderType.solid()` / `cutoutMipped()` / `cutout()` | `:965`–`:969` |
| 实体 | `EntityRenderDispatcher` 遍历 | 中段 |
| 区块（半透明批次） | `RenderType.translucent()` / `tripwire()` | `:1177`、`:1196` |
| 云 | `renderClouds` | `:1712` |
| 之后 | 天气、手、GUI（`GameRenderer` 接着做） | — |

## 1.3 能插的地方：事件，不是阶段枚举

| 事件 | 位置 | 常见用途 |
| --- | --- | --- |
| `RenderLevelStageEvent` | 上表各阶段之间 | 世界内几何、粒子、光束 |
| `RenderFrameEvent.Pre/Post` | 整帧前后 | 全局资源准备 / 收尾 |
| `RenderHandEvent` | 第一人称手 | 替换手部渲染 |
| `RenderGuiEvent` / `RegisterGuiLayersEvent` | GUI | HUD 元素 |
| `ViewportEvent` / `ComputeFovModifierEvent` | 相机 | 抖动、视场角 |

`RenderLevelStageEvent.Stage` 的完整列表见 §2.7。Photon 用的是 `AFTER_BLOCK_ENTITIES` 与
`AFTER_PARTICLES`（`PhotonClientListeners.java:93`–`:104`）。

## 1.4 与 26.2 的差别（写代码前先看这张表）

| 关注点 | 1.21.1 | 26.2 |
| --- | --- | --- |
| 渲染模型 | 立即模式（写顶点 → endBatch） | 三段式（extract / submit / render） |
| 渲染数据容器 | 没有；自己缓存 | `RenderState` + `DataTicket` |
| 着色器 | core shader JSON（`.json` + `.vsh` + `.fsh`） | Java 侧渲染管线对象 |
| 渲染类型 | `RenderType` 内联全部状态 | 命名渲染类型 + `RenderPipeline` |
| GeckoLib | 4.9.3（无 RenderState） | 5.5.6（RenderState + 票据） |
| 几何接管 | `GeoRenderIntercept` 空实现 | 自研提交接管 + GPU 蒙皮 |
| 后处理 | `PostChain` / `PostPass` + `shaders/post/*.json` | 自研后处理管线 |

---
