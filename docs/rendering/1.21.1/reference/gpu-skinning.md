# 7. GPU 蒙皮与渲染性能


## 7.1 1.21.1 的现状

26.2 那套「接管 GeckoLib 几何提交 + GPU 蒙皮」在 1.21.1 上**尚未接入**：

```java
// client/render/optimize/GeoRenderIntercept.java（1.21.1 分支）
public static boolean trySubmit(Object renderPassInfo, Object renderTasks, Object renderType) {
    return false;   // 本实现不自己提交任何几何
}
```

1.21.1 的角色渲染走 GeckoLib 默认的 CPU 路径：逐骨骼遍历、逐顶点写。
**不要**把 26.2 的性能数字搬到这里。

## 7.2 默认路径的成本结构

```text
每个角色每帧：
  骨骼遍历（N 根 × 层级深度）
    → 每根骨骼算一次矩阵
      → 每个立方体的每个顶点做一次变换 → 写进 VertexConsumer
```

成本与「骨骼数 × 顶点数 × 角色数」成正比。可用的优化：

1. **减骨骼 / 减面**（模型侧最有效）；
2. **缓存每帧不变的东西**（贴图、光照、`RenderType` 引用）；
3. **同材质批量写**（减少 `RenderType` 切换）；
4. **粗筛剔除**（谨慎使用，宁可少剔也别误剔）；
5. **限制同屏角色数**（图鉴 / 摆件预览最容易失控）。

## 7.3 间接开销

| 项目 | 说明 |
| --- | --- |
| 资源解析 | `AssetGeoCache` / `GenshinGeoCache` 只在资源重载时重建，不要每帧查 |
| HUD / 飘字 | 走独立 HUD 层与缓存（项目里 `performance/Indicator*`） |
| Photon 粒子 | §8.7：粒子数量与材质档位直接决定 GPU 时间 |
| 动态光 | 2.2.8 新增，按盏计费；`shadowed_lights` 默认 8 盏 |

## 7.4 测量

| 工具 | 用途 |
| --- | --- |
| `F3` 帧时间 | 判断是逻辑还是渲染 |
| `FrameTimeStats` | 项目内的帧时间统计 |
| `LightPassTimer` / `LightDebug` | Photon 动态光耗时与可视化 |
| Photon 编辑器 | 单独预览某个 `.fx` 的开销 |

---
