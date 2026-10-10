# 3. 坐标空间完全指南

位置不对时，先回答三个问题：**在哪个空间？单位是什么？角度还是弧度？**

## 3.1 五个空间

| 空间 | 原点 | 单位 |
| --- | --- | --- |
| 世界 | 世界原点 | 方块 |
| 相机 | 相机 | 方块 |
| 实体 | 实体脚底 | 方块 |
| 模型 | 模型根 | **1/16 方块** |
| 屏幕 | 窗口左上 | 像素 |

`RenderLevelStageEvent` 给的 `PoseStack` 是**相机相对**的：世界坐标要先减相机坐标。

## 3.2 三条常用换算

```text
世界 → 相机： pos - camera.getPosition()
模型 → 实体： 模型坐标 / 16，沿骨骼链变换
相机 → 屏幕： ProjMat × ModelViewMat × pos
```

## 3.3 度与弧度

| 位置 | 单位 |
| --- | --- |
| 实体 `yRot` / `xRot`、`Mth.rotLerp` | 度 |
| `Quaternionf.rotationXYZ` / `mulPose` | 弧度 |
| GeckoLib `.geo.json` / `.animation.json` | 度（加载期换算） |
| GeckoLib `GeoBone#getRotX` | 弧度 |
| Photon `setRotation` / 时间轴旋转 | 度 |

换算：`rad = deg * Mth.DEG_TO_RAD`，`deg = rad * Mth.RAD_TO_DEG`。

## 3.4 变形的书写顺序

```java
poseStack.pushPose();
poseStack.translate(x, y, z);      // 1 平移
poseStack.mulPose(quaternion);     // 2 旋转
poseStack.scale(sx, sy, sz);       // 3 缩放
// …… 写顶点
poseStack.popPose();
```

`PoseStack` 是**右乘**：后写的变换发生在先写变换之后的局部坐标系里；`push` / `pop` 必须成对。

## 3.5 症状对照

| 症状 | 原因 |
| --- | --- |
| 偏半格 | 摆件原点补偿（`GeoObjectRenderer` 的 +0.5） |
| 上下颠倒 / 反向 | 模型 X 轴反向；度当弧度 |
| 远处抖动 | 先转 `float` 再减相机坐标 |
| 缩放不对 | 忘了模型单位是 1/16 方块 |
| 旋转跟着父级跑偏 | `popPose` 漏了 / 变换顺序反了 |

深入：[完全参考 6. 坐标空间、矩阵与四元数](/doc/rendering-1.21.1-reference-transform)。