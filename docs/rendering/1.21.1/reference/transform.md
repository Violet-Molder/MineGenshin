# 6. 坐标空间、矩阵与四元数


## 6.1 五个空间与换算

| 空间 | 原点 | 单位 |
| --- | --- | --- |
| 世界 | 世界原点 | 方块 |
| 相机 | 相机 | 方块 |
| 实体 | 实体脚底 | 方块 |
| 模型 | 模型根 | 1/16 方块 |
| 屏幕 | 窗口左上 | 像素 |

```text
模型 → 实体： 模型坐标 / 16，经骨骼链变换，加骨骼挂点偏移
实体 → 世界： 世界 = 实体坐标 + 变换后的局部坐标
世界 → 相机： 相机 = 世界 - 相机坐标
```

## 6.2 度与弧度

| 位置 | 单位 |
| --- | --- |
| 实体朝向、`Mth.rotLerp` | 度 |
| `Quaternionf.rotationXYZ` / `mulPose` | 弧度（四元数） |
| GeckoLib `.geo.json` / `.animation.json` | 度（加载期换算） |
| GeckoLib 运行时 `GeoBone#getRotX` | 弧度 |
| Photon `IFXEffectExecutor#setRotation` | 度（内部转四元数，`IFXEffectExecutor.java:32`） |

## 6.3 矩阵与四元数

```java
Matrix4f m = poseStack.last().pose();
Vector3f v = new Vector3f(x, y, z);
m.transformPosition(v);                        // 局部 → 上一级
poseStack.mulPose(new Quaternionf().rotationXYZ(rx, ry, rz));
```

顺序：**平移 → 旋转 → 缩放**（模型习惯），骨骼链也是这个顺序；
`poseStack` 是右乘，你写代码的顺序就是「从外到内」。

## 6.4 症状对照

| 症状 | 原因 |
| --- | --- |
| 模型上下颠倒 | 模型空间与世界空间的 X 轴反向 |
| 旋转方向反 | 四元数欧拉序（XYZ vs ZYX） |
| 位置对、朝向差 90° | 把 `yRot`（度）当弧度用了 |
| 模型与判定箱错位 | 原点补偿问题（§5.6） |

---
