# 3. 坐标空间完全指南


这一节两条线几乎一样，差别只有类名（1.21.1 用 `ResourceLocation`，26.2 用 `Identifier`）。

## 3.1 五个空间

| 空间 | 原点 | 单位 | 谁在用 |
| --- | --- | --- | --- |
| 世界空间 | 世界原点 | 方块 | 实体坐标、粒子位置、Photon FX 根 |
| 相机空间 | 相机 | 方块 | `RenderLevelStageEvent` 给的 `PoseStack` 起点 |
| 实体空间 | 实体脚底 | 方块 | `EntityRenderer#render` 的入参 |
| 模型空间 | 模型根 | 1/16 方块 | Blockbench / `.geo.json` / GeckoLib 骨骼 |
| 屏幕空间 | 窗口左上 | 像素 | GUI、`GuiGraphics` |

## 3.2 三条换算公式

```text
模型空间 → 世界空间： 世界坐标 = 实体坐标 + 模型坐标 / 16 （再叠加骨骼的旋转与缩放）
世界空间 → 相机空间： 相机空间 = 世界坐标 - 相机坐标
世界空间 → 屏幕空间： 屏幕坐标 = 投影 × 模型视图 × 世界坐标
```

## 3.3 角度还是弧度

| 位置 | 单位 |
| --- | --- |
| 实体 / 相机朝向（`yRot`、`xRot`） | 度 |
| `Mth.sin/cos/rotLerp` 的入参 | 度 |
| `Quaternionf.rotationXYZ(...)` | 弧度 |
| `PoseStack.mulPose(...)` 的参数 | 四元数（弧度来源） |
| GeckoLib 的 `.geo.json` / `.animation.json` | 度（加载期转弧度） |
| GeckoLib 运行时（`GeoBone#getRotX`） | 弧度 |

## 3.4 常见症状对照表

| 症状 | 通常原因 |
| --- | --- |
| 特效整体偏了半格 | 用了摆件（`GeoObjectRenderer`）的 +0.5 平移补偿 |
| 模型上下颠倒 | 模型空间与世界空间的 X 轴反向 |
| 特效跟手但方向反 | 用了 `yRot` 直接当弧度，或四元数欧拉序不对 |
| 位置一格一格跳 | 在 tick 里写了位置，又在 frame 里写了一遍 |

---
