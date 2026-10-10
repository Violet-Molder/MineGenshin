# 3. 坐标空间完全指南


这一节建议反复读，因为**渲染 bug 的九成是坐标系搞错**。

## 3.1 五个空间

| 空间 | 原点 | 谁在用 |
|---|---|---|
| 世界空间 | 世界原点 | 游戏逻辑、`Entity.position()`、特效生成位置 |
| 相机相对空间 | **相机** | 所有实体/方块实体/粒子的 PoseStack（26.2 的提交式管线） |
| 模型空间 | 模型根 | GeckoLib 的骨骼坐标、Blockbench 的模型坐标（单位是 1/16 格） |
| 骨骼空间 | 骨骼 pivot | 骨骼动画、`PerBoneRender` 的 PoseStack |
| 屏幕空间 | 视口左上角 | 全屏后处理、UI |

## 3.2 三条换算公式

```
世界坐标   = CameraRenderState.pos + 相机相对坐标
相机相对坐标 = 世界坐标 - CameraRenderState.pos
模型像素    = 格 × 16         （Blockbench 惯例：1 格 = 16 像素）
```

`CameraRenderState.pos` 是 `public Vec3 pos`（`CameraRenderState.java:16`），
在渲染阶段可以从 `RenderPassInfo.cameraState()` 拿到。

## 3.3 角度还是弧度

这是本项目里真实踩过的坑，务必记牢：

| 入口 | 单位 |
|---|---|
| `IFXEffectExecutor#setRotation(double, double, double)`（Photon 的便捷重载） | **角度** |
| `IFXObject#updateRotation(Vector3f)` / JOML `Quaternionf#rotationXYZ` | **弧度** |
| GeckoLib `BoneSnapshot#setRotation` / `GeoBone#baseRotX` | **弧度** |
| `net.minecraft.world.entity.Entity#getYRot()` 等 | **角度** |

混用一次就是经典的「旋转差了 57.3 倍」（180/π）。

## 3.4 常见症状对照表

| 症状 | 原因 |
|---|---|
| 东西死死跟着镜头 | 把相机相对坐标当成世界坐标用了，忘了加 `cameraState().pos` |
| 东西在天上/地下很远 | 反过来：本来就是世界坐标，又加了一次相机位置 |
| 模型上下颠倒 | Blockbench 模型空间与世界空间的 **X 轴反向**（GeckoLib 用 `(-16, +16, +16)` 换算就是这件事） |
| 旋转差 57 倍 | 角度/弧度混用 |
| 缩放后位置偏了 | 非均匀父级缩放会改变子级的基向量，位置也会被一起缩放 |
| 换了个渲染器就偏半格 | 继承了 `GeoObjectRenderer` —— 它的 `adjustRenderPose` 默认 `translate(0.5, 0.51, 0.5)` |

---
