# 8. 性能


## 8.1 先建立预算概念

1.21.1 的立即模式有一个 26.2 没有的固定成本：**状态切换**。每换一次 `RenderType`，
驱动就要重绑着色器、贴图、混合与深度；写顶点本身反而不贵。

## 8.2 常见瓶颈与对策

| 瓶颈 | 症状 | 对策 |
| --- | --- | --- |
| 顶点数 | 单帧顶点暴涨、FPS 与实体数成反比 | 合并骨骼层级、减面；几何优化（26.2 那套 GPU 蒙皮在 1.21.1 上还没接） |
| 状态切换 | CPU 时间在 `RenderType` 之间跳 | 同材质同状态的顶点尽量连着写 |
| 动态光 | 帧时间随光数量线性上升 | `shadowed_lights` 只给关键少数；`resolution` 降 0.5；`volumetric` 关掉 |
| 体积雾 | 全屏开销 | `volumetric_samples` 4 → 2；`voxel_budget_ms` 压到 1ms |
| 粒子数量 | GPU 满载、画面糊 | 编辑器里限 `Emission` 的 rate over distance / over time |

## 8.3 三条铁律

1. **不要在渲染回调里做逻辑计算**（找实体、遍历背包、算曲线）；
2. **不要在 tick 里碰渲染对象**（`PoseStack`、`VertexConsumer`、`ShaderInstance`）；
3. **同一份数据只算一次**（骨骼矩阵、光照值、贴图坐标），存起来复用。

## 8.4 怎么测

- 原版 `F3` 的帧时间够用来看整体；
- Photon 的动态光有自己的计时器（`LightPassTimer`、`LightDebug`）；
- 本项目自带 `FrameTimeStats` / `RenderOptimizeStats`（1.21.1 上优化路径是关的，
  但计时与统计可用）。

---
