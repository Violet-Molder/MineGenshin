# 6. 坐标空间、矩阵与四元数

位置和朝向错了，90% 是这一章的问题。它把「五个空间、两种角度单位、三种变换写法」讲清楚，
最后给一张症状对照表，出问题直接查。

## 6.1 五个空间

| 空间 | 原点 | 单位 | 谁在用 |
| --- | --- | --- | --- |
| 世界 | 世界原点 | 方块 | 实体坐标、方块坐标、Photon FX 根 |
| 相机 | 相机 | 方块 | `RenderLevelStageEvent` 给的那个 `PoseStack` 起点 |
| 实体 | 实体脚底 | 方块 | `EntityRenderer#render` 的入参坐标系 |
| 模型 | 模型根 | **1/16 方块** | Blockbench / `.geo.json` / GeckoLib 骨骼 |
| 屏幕 | 窗口左上 | 像素 | GUI、`GuiGraphics` |

两个最容易记错的换算：

```text
模型 → 实体： 模型坐标 / 16，再沿骨骼链变换（父骨骼 × 子骨骼），加上挂点偏移
实体 → 相机： 相机坐标 = 实体坐标 + 局部坐标 - 相机坐标
```

**模型单位是 1/16 方块**：模型文件里写 16 就是 1 格。所以「把特效抬高 0.1 格」在模型空间里要写 1.6。

## 6.2 `PoseStack` 的语义

```java
poseStack.pushPose();                      // 存一份当前变换
poseStack.translate(x, y, z);              // 平移（float 或 double 重载）
poseStack.mulPose(quaternionf);            // 旋转（四元数）
poseStack.scale(sx, sy, sz);               // 缩放
Matrix4f m = poseStack.last().pose();      // 取出当前矩阵
poseStack.popPose();                       // 还原
```

三条规则：

1. **右乘**：每次 `translate` / `mulPose` / `scale` 都是「在当前坐标系里再变换一次」。
   所以先平移再旋转，旋转发生在平移之后的局部坐标系里 —— 顺序写反，结果完全不同。
2. **必须成对**：`pushPose` 与 `popPose` 数量要相等，否则后面所有渲染都跟着你的变换走。
3. **不要跨帧保存**：`PoseStack` 的生命周期就是一次绘制。

推荐的书写顺序（和模型导出习惯一致）：**平移 → 旋转 → 缩放**。

## 6.3 JOML：矩阵与向量

Minecraft 用 JOML。几个日常会用到的：

```java
Matrix4f m = new Matrix4f().translate(x, y, z).rotateY(rad).scale(s);
Vector3f local = new Vector3f(0, 1, 0);
m.transformPosition(local);            // 点：会吃平移
m.transformDirection(local);           // 方向：不吃平移
Vector3f world = m.transformPosition(new Vector3f(modelX, modelY, modelZ));
Quaternionf q = new Quaternionf().rotationXYZ(rx, ry, rz);   // 参数是弧度
Quaternionf look = new Quaternionf().lookAlong(forward, up); // 朝某个方向看
```

`PoseStack.last()` 给的是 `Matrix4f`（`:65`）；直接用 `m.transformPosition(...)` 就能把模型空间的点换算到
当前坐标系 —— 这是「骨骼挂点算世界坐标」的标准做法。

## 6.4 欧拉角还是四元数

| 用哪种 | 什么时候 |
| --- | --- |
| 欧拉角（`xRot` / `yRot` / `zRot`） | 从实体、玩家输入、朝向来的时候；需要人读的时候 |
| 四元数 | 做插值、累积旋转、避免万向节死锁时 |

互相转换：

```java
Quaternionf q = new Quaternionf().rotationYXZ(yawRad, pitchRad, rollRad);   // 注意顺序
float[] euler = new float[3];
q.getEulerAnglesYXZ(euler);                                                  // 取回（弧度）
```

**欧拉顺序不是随便挑的**：实体朝向是「先 Y 再 X」（YXZ），模型动画文件里常见的是 XYZ。
顺序不同，同样的三个角画出来方向不一样。

## 6.5 度还是弧度：一张表

| 位置 | 单位 |
| --- | --- |
| 实体朝向 `yRot` / `xRot`、`Mth.rotLerp` | **度** |
| `Quaternionf.rotationXYZ(...)` / `mulPose(...)` | **弧度** |
| GeckoLib 的 `.geo.json` / `.animation.json` | 度（加载期换算成弧度） |
| GeckoLib 运行时 `GeoBone#getRotX` / `BoneSnapshot#getRotX` | 弧度 |
| Photon `IFXEffectExecutor#setRotation(x, y, z)` | 度（内部转四元数） |
| Photon 时间轴里的旋转属性 | 度 |
| 玩家视角俯仰限制 | ±90 度 |

换算：`弧度 = 度 × Mth.DEG_TO_RAD`，`度 = 弧度 × Mth.RAD_TO_DEG`。

## 6.6 精度：double 与 float

- 实体位置是 `double`（远距离不抖的关键）；
- GPU 只要 `float`。所以在**画之前**先把世界坐标减去相机坐标，得到小的局部坐标，再转 `float`；
- 反过来（先把 `double` 转 `float` 再减）在远离原点处会明显抖动 —— 这是「走远了模型抖」的根因。

## 6.7 症状对照表

| 症状 | 原因 |
| --- | --- |
| 模型整体偏半格 | 用了摆件（`GeoObjectRenderer`）的 +0.5 平移补偿；见 [5. GeckoLib](/doc/rendering-1.21.1-reference-geckolib) |
| 模型上下颠倒 | 模型空间与世界空间的 X 轴反向 |
| 朝向差 90° / 反向 | 把 `yRot`（度）当弧度，或欧拉顺序写错 |
| 位置对、缩放不对 | 模型单位是 1/16 方块，缩放倍数按它换算 |
| 远处抖动 | 先转 `float` 再减相机坐标 |
| 旋转跟着父级跑偏 | `popPose` 漏了，或变换顺序写反 |
| 骨骼挂点位置对不上 | 读了上一帧的 `GeoBone`（应该用 `BoneSnapshot` 且只在渲染期读） |

下一章：[7. GPU 蒙皮与渲染性能](/doc/rendering-1.21.1-reference-gpu-skinning)。