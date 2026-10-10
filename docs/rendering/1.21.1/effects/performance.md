# 8. 性能

## 8.1 先定位瓶颈在哪一侧

| 现象 | 瓶颈 |
| --- | --- |
| 帧时间随**模型/骨骼数**线性涨 | CPU 蒙皮（GeckoLib 默认路径） |
| 帧时间随**粒子面积 / 屏幕覆盖**涨 | GPU（Overdraw、Bloom） |
| 开光影后骤降 | GPU + Iris 兼容层 |
| 只在某场景掉帧 | 先数那个场景里有多少实例 |

工具：`F3` 帧时间、项目内 `FrameTimeStats`、Photon 的 `LightPassTimer` / 编辑器预览。

## 8.2 CPU 侧：成本结构与对策

```text
成本 ≈ 骨骼数 × 顶点数 × 同屏实例数
```

按收益排序：

1. **减骨骼、减面**（模型侧，唯一能改量级的手段）；
2. **限制同屏实例数**（图鉴 / 摆件预览最容易失控）；
3. **缓存每帧不变的东西**（`RenderType`、烘焙模型、光照值）；
4. **同材质批量写**（减少 `RenderType` 切换）；
5. **粗筛剔除**（宁可少剔也别误剔）。

> 1.21.1 上 **没有** GPU 蒙皮：`GeoRenderIntercept` 仍是空实现（`trySubmit` 恒 false）。
> 26.2 的优化结论不能搬过来。

## 8.3 GPU 侧：粒子与后处理

- 半透明叠加越多，Overdraw 越重：能 `NO_DEPTH_TEST` 的少用 `TRANSLUCENT`；
- Bloom 与自定义后处理按**屏幕像素**计费：`bloom_mip_level` 别调太高；
- 后处理缓冲池 `postfx_pool_budget_mb` 默认 256 MB，超了会频繁申请。

## 8.4 Photon 动态光/体积光的预算

| 项 | 默认 | 建议 |
| --- | --- | --- |
| 带阴影的光 | `shadowed_lights = 8` | 只给关键少数 |
| 动态光分辨率 | `resolution = 0.5` | 掉帧先降它 |
| 体素预算 | `voxel_budget_ms = 2.0` | 掉帧压到 1ms |
| 体积光采样 | `volumetric_samples = 4` | 降到 2 |
| 体积光 | `volumetric = true` | 非必要关掉 |

## 8.5 三条铁律

1. 渲染回调里不做逻辑计算；
2. tick 里不碰渲染对象；
3. 同一份数据只算一次（骨骼矩阵、光照、贴图坐标）。

深入：[完全参考 7. GPU 蒙皮与渲染性能](/doc/rendering-1.21.1-reference-gpu-skinning)。