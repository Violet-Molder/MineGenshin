# 0. 导读

> **适用版本**：Minecraft `26.2` · NeoForge `26.2.0.88` · Java `25` · Photon2 `26.2.2.3` · LDLib2 `26.2.2.41.a` · GeckoLib `5.5.6`（本仓库 1.0.1 的依赖）
>
> **1.21.1 线**：同一主题的 1.21.1 版是另一篇 —— [Minecraft 1.21.1 渲染与 Photon2 完全参考](rendering-photon2-reference-1.21.1.md)。
> 两条线的写法差别很大（立即模式 vs 三段式、GeckoLib 4 vs 5、Photon 2.2.x vs 26.2.x），不要互相照抄；
> 页面上用右上角的版本切换在两条线之间跳。
>
> **这份文档是什么**：把「26.2 的渲染管线怎么运转」和「Photon2 特效怎么接进模组」两件事一次讲完，
> 面向**要动手写代码的人**：自定义渲染层、粒子与光束、后处理、骨骼挂点、特效触发链路。
>
> **核对方式**：正文里的类名、方法名、事件名都能在本机源码里搜到，出处写在对应章节里；
> 关键的几处核对入口在 [§0.3](#03-怎么核对本文的每条说法)。凡是没核对到的结论都会显式标注「未确认」，
> 不会用「大概」「应该是」糊过去。


## 0.1 版本矩阵

| 组件 | 版本 | 在本文里的角色 |
|---|---|---|
| Minecraft | 26.2 | 渲染管线与 `RenderState` 体系的事实标准 |
| NeoForge | 26.2.0.88 | 渲染事件的来源（`RenderLevelStageEvent` 等） |
| Java | 25 | 工具链 |
| Photon2 | 26.2.2.3 | 特效引擎（FX / 粒子 / 后处理） |
| LDLib2 | 26.2.2.41.a | Photon2 的 UI 与基础设施依赖 |
| GeckoLib | 5.5.6 | 骨骼动画与模型渲染 |

## 0.2 三条主线与推荐阅读路线

全文分三块，按你的目的挑路线读，不必从头看到尾：

| 你的目的 | 建议路线 |
|---|---|
| 写一个特效，接到角色骨骼上 | 1 → 8 → 9 → 10（再回 6 查坐标换算） |
| 写自定义渲染层 / 自定义几何 | 1 → 2 → 3 → 4 → 5 |
| 模型方向不对、位置乱飞 | 6（症状对照表） |
| 掉帧、特效一多就卡 | 7 → 11 |
| 打包后特效丢失 / 多人不同步 | 8 → 11 |

三条主线分别是：

1. **渲染管线**（第 1–3 章）：一帧的提取 / 提交 / 绘制三段，Blaze3D 的 API 地图，着色器与 GPU 数据。
2. **模型与骨骼**（第 4–7 章）：实体渲染状态、GeckoLib 5 的渲染层与骨骼快照、坐标与矩阵、GPU 蒙皮与性能。
3. **Photon2**（第 8–11 章）：从编辑器资产到运行时对象、Java API、本项目的真实接线、排错与性能。

## 0.3 怎么核对本文的每条说法

本文的每个技术结论都来自三处之一，你可以顺着查：

| 证据 | 位置 |
|---|---|
| Minecraft + NeoForge 源码 | `build/moddev/artifacts/minecraft-patched-26.2.0.88-sources.jar` |
| Photon2 源码 | `~/.gradle/caches/modules-2/files-2.1/com.lowdragmc.photon/*/*-sources.jar` |
| GeckoLib 源码 | `~/.gradle/caches/modules-2/files-2.1/com.software.bernie.geckolib/*/*-sources.jar` |
| 本项目真实用法 | `src/main/java/com/linweiyun/genshin/**`（正文里给到文件与行号） |

在源码 jar 里查一个类是否真的存在（不用解压整包）：

```powershell
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead("build\moddev\artifacts\minecraft-patched-26.2.0.88-sources.jar")
$zip.Entries | Where-Object { $_.FullName -like "*SubmitNodeCollector*" } | Select-Object -ExpandProperty FullName
```

本文里出现的「**未确认**」标记，表示作者没有在源码里找到直接证据，只能给出经验判断；
贴进项目前请自己核一遍。

## 0.4 与仓库里其它文档的关系

| 文档 | 定位 |
|---|---|
| 本文 | 渲染 + Photon2 的完整参考（按 26.2 源码重写） |
| [资源、渲染与界面](/doc/sys-render-asset) | 本项目的资源路径规则、外观掩码、界面注册方式 |
| `docs/rendering-and-photon2.md` | 同期整理的渲染 + Photon2 说明（篇幅接近，结论没有逐条源码核对） |
| `docs/rendering_guide_for_photon2.md` | 早期草稿：面向 1.21 时代的 Blaze3D API，**其中 `RenderSystem.setShader`、`Tesselator.end()` 一类写法在 26.2 已不存在** |
| `docs/photon2-java-guide.md` | Photon2 的 Java 使用指南（另一篇视角，可对照） |
| [性能优化系统](/doc/sys-performance) | 本项目的性能预算与已有优化 |
| [实体 AI 指南](/doc/entity-ai) | 实体行为侧（与渲染无关时的入口） |

> **一句话结论**：如果你只想记住三件事 ——
> ① 26.2 的渲染是「提取 → 提交 → 绘制」三段式，提交时 `PoseStack` 的原点是**相机**；
> ② 所有跨帧保留的游戏对象都不能塞进渲染状态，渲染状态每帧重建；
> ③ Photon2 的特效是**客户端**的运行时对象，服务端只能通过 payload 请求它生成。
