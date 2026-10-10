# 1. 先建立正确的心智模型

> **本文针对的版本**：Minecraft `26.2` · NeoForge `26.2.0.88` · Java `25` · Photon `26.2.2.3` · LDLib2 `26.2.2.41.a` · GeckoLib `5.5.6`
>
> **1.21.1 线**：同一主题的 1.21.1 版是另一篇 —— [渲染与 Photon2 特效（1.21.1）](rendering-and-photon2-1.21.1.md)。
> 那一篇讲的就是本文提到的「1.20/1.21 时代的 API」（立即模式、core shader JSON、GeckoLib 4），
> 页面上用右上角的版本切换在两条线之间跳。
>
> **本文与 `docs/rendering_guide_for_photon2.md` 的关系**：那份文档的 Blaze3D 部分是 1.20/1.21 时代的 API
> （`RenderSystem.setShader`、`BufferUploader.drawWithShader`、`Tesselator.end()`、`RenderType.create`），
> 在 26.2 里**已经不存在了**；它的 Photon2 部分只有标题和目录，正文从未写出。本文是重新写的完整版本：
> 上半部分是**在本仓库实际使用的版本上核对过**的渲染管线，下半部分是 Photon2 的架构与接入方式。
>
> **怎么核对本文的说法**：正文里出现的每个类名/方法名都可以在下面两个源码 jar 里直接搜到——
> `build/moddev/artifacts/minecraft-patched-26.2.0.88-sources.jar`（Minecraft + NeoForge）、
> `~/.gradle/caches/modules-2/files-2.1/com.lowdragmc.photon/...`（Photon）。
> 凡是本文没核到的，会明确写「未确认」，不猜。

---


## 1.1 26.2 的渲染是「三段式」，不是「边算边画」

1.21 之前，实体渲染是「拿到实体 → 直接往 `BufferBuilder` 里塞顶点 → 立刻画」。26.2 已经改成：

```
① 提取（Extract）   把「要画什么」从游戏状态里摘出来，存进一堆 *RenderState 纯数据对象
② 提交（Submit）    遍历 RenderState，把几何提交给 SubmitNodeCollector（只是登记，不是绘制）
③ 绘制（Draw）      统一在管线内部按 RenderType/管线状态排序，成批画出去
```

这个改动带来的直接后果，也是本文要反复强调的一条：

> **实体渲染时 PoseStack 的原点是相机，不是世界原点。**
> 世界坐标 = `CameraRenderState.pos` + PoseStack 顶上的平移。

任何「我要在世界某处放个东西」的代码，都必须自己把相机位置加回去。忘了这一步的典型症状是：
特效/模型**死死跟着镜头跑**，你往前它就往前，看起来像贴在屏幕上。

## 1.2 一帧里到底按什么顺序发生

26.2 的 `RenderLevelStageEvent` 已经**没有 `Stage` 枚举**了，改成了一组子事件。
下面这份顺序抄自 `RenderLevelStageEvent` 的类注释（`neoforge/.../client/event/RenderLevelStageEvent.java:35-43`）：

| 顺序 | 子事件 | 触发位置 |
|---|---|---|
| 1 | `AfterSky` | `LevelRenderer.addSkyPass` 末尾，天空画完 |
| 2 | `AfterOpaqueBlocks` | `addMainPass` 早期，实心/镂空区块几何画完 |
| 3 | `AfterOpaqueFeatures` | 不透明「features」（实体、方块实体、粒子）画完 |
| 4 | `AfterTranslucentFeatures` | 半透明 features（实体、方块实体）画完 |
| 5 | `AfterTranslucentBlocks` | 半透明区块几何画完 |
| 6 | `AfterTranslucentParticles` | 半透明粒子画完 |
| 7 | `AfterWeather` | `addWeatherPass` 末尾，天气画完，世界边界之前 |
| 8 | `AfterLevel` | `GameRenderer.renderLevel` 里 `LevelRenderer.renderLevel` 返回之后（最后一个） |

另外两个必须知道的事件：

| 事件 | 用途 | 触发时机 |
|---|---|---|
| `ExtractLevelRenderStateEvent` | **提取自定义渲染状态**。自定义数据必须在这里摘出来、存进 `LevelRenderState`，否则后面的阶段拿不到 | 每个渲染帧的状态提取阶段 |
| `SubmitCustomGeometryEvent` | **在实体/方块实体/粒子渲染器之外提交自定义几何** | 「粒子提交之后、不透明提交渲染之前」 |

`SubmitCustomGeometryEvent` 提供 `getSubmitNodeCollector()` / `getPoseStack()` / `getLevelRenderState()`，
这是**不写渲染器也能画东西**的正规入口。

## 1.3 渲染发生在哪个线程

渲染在**渲染线程**（客户端主线程，也就是跑 `Minecraft.getInstance()` 那个线程）上。
但要注意两类异步：

- 粒子并行更新（Photon 的 `parallelUpdate`）会把粒子模拟拆到 worker 上，**渲染与提交仍在主线程**；
- 资源加载/重载有自己的线程，**不要在重载回调里碰 `RenderSystem`/GL 状态**。

---
