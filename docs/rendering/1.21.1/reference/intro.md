# 0. 导读

## 0.1 这本书讲什么

把「Minecraft 1.21.1 的一次渲染是怎么发生的」和「Photon2 特效怎么接进模组」一次讲完，
面向**要动手写代码的人** —— 不是模组使用说明，也不是 API 罗列，而是「为什么这么写、什么时候该用哪个」。

适用版本（本仓库 1.21.1 线的依赖，见 `gradle.properties`）：

| 组件 | 版本 |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.250 |
| Java | 21 |
| Photon | 2.2.8 |
| LDLib2 | 2.2.42 |
| GeckoLib | 4.9.3 |

> 26.2 线（`master`）是另一套写法：三段式渲染、GeckoLib 5.5.6、Photon 26.2.2.3。
> 两套文档的章节一一对应，用页面右上角的版本按钮切换。

## 0.2 一章一页，按需读

| 章 | 内容 | 什么时候读 |
| --- | --- | --- |
| [1. 心智模型](/doc/rendering-1.21.1-reference-frame) | 一帧的顺序、线程、事件挂钩点 | **先读这章** |
| [2. Blaze3D API 地图](/doc/rendering-1.21.1-reference-blaze3d) | 顶点/状态/渲染类型/多缓冲 | 要自己画东西时 |
| [3. 着色器](/doc/rendering-1.21.1-reference-shaders) | core shader、uniform、后处理 | 要写 GLSL 时 |
| [4. 实体渲染](/doc/rendering-1.21.1-reference-entity-render) | 实体渲染器与数据放哪 | 给实体加渲染时 |
| [5. GeckoLib](/doc/rendering-1.21.1-reference-geckolib) | 骨骼、动画、渲染层 | 做动画模型时 |
| [6. 坐标空间](/doc/rendering-1.21.1-reference-transform) | 矩阵、四元数、单位与角度 | 位置/朝向不对时 |
| [7. GPU 蒙皮与性能](/doc/rendering-1.21.1-reference-gpu-skinning) | 成本结构与优化 | 掉帧时 |
| [8. Photon2 运行时](/doc/rendering-1.21.1-reference-photon-runtime) | `.fx`、材质、模型源、动态光 | 做粒子特效时 |
| [9. Photon2 Java API](/doc/rendering-1.21.1-reference-photon-api) | 代码里怎么把特效跑起来 | 写特效触发逻辑时 |
| [10. 项目实战](/doc/rendering-1.21.1-reference-practice) | 本项目已经踩过的四种时机 | 想抄现成写法时 |
| [11. 排错手册](/doc/rendering-1.21.1-reference-troubleshooting) | 按症状查 | 出问题时 |
| [12. 附录](/doc/rendering-1.21.1-reference-appendix) | 类名/事件/对照表 | 查名字时 |

## 0.3 事实从哪来

每一条结论都尽量给出来源，行号取自本机 Gradle 缓存里的源码包：

| 来源 | 路径 |
| --- | --- |
| Minecraft + NeoForge | `build/moddev/artifacts/neoforge-21.1.250-sources.jar` |
| Photon | `photon-neoforge-1.21.1-2.2.8-sources.jar` |
| LDLib2 | `ldlib2-neoforge-1.21.1-2.2.42-sources.jar` |
| GeckoLib | `geckolib-neoforge-1.21.1-4.9.3-sources.jar` |

约定：`Foo.java:123` 表示该文件第 123 行；**官方文档站与 jar 不一致时以 jar 为准**。

## 0.4 本文不覆盖什么

- 26.2 线的写法（见 26.2 版对应章节）；
- 光影（Iris/OptiFine）内部实现，只讲与本项目交界处的行为；
- Photon 编辑器每个面板的逐个操作（只讲面板对应到哪个概念）；
- 数据生成、注册、网络这些与渲染无关的系统（见「系统详解」分类下的文档）。

下一章：[1. 心智模型：一帧是怎么画出来的](/doc/rendering-1.21.1-reference-frame)。