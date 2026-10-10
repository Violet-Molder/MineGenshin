# 6. 坐标空间、矩阵与四元数


## 6.1 一条渲染路径上的所有空间

```
① 模型本地空间   顶点在 .geo 模型里的坐标（单位是「模型像素」，1 格 = 16）
② 骨骼空间       骨骼的 pivot / 旋转 / 缩放，靠这条链累乘到模型根
③ 实体局部空间   模型根位姿（renderer 的 scale / rotate / 微调都作用在这里）
④ 相机相对空间   MC 提交几何时 PoseStack 的坐标原点 —— 世界坐标减去相机坐标
⑤ 视图空间       ModelViewMat（相机旋转 + 平移）
⑥ 裁剪空间       ProjMat * viewPosition
⑦ 屏幕空间       NDC → 视口
```

②→③ 由 GeckoLib/原版模型渲染做（`RenderUtil.transformToBone`、`BoneSnapshot.apply`），
③→④ 由渲染器自己决定（`adjustRenderPose` 之类），④→⑤→⑥ 由着色器里的 `ModelViewMat` / `ProjMat` 做 ——
这三条变换在自定义管线里必须自己写对（本项目 `entity_skinned.vsh` 只做 ④→⑤→⑥，见第 7 章）。

## 6.2 关键结论：提交阶段 PoseStack 的原点是相机

证据链：

1. `LevelRenderer.submitEntities` 传入的坐标已经减掉了相机：`entityRenderDispatcher.submit(state, cameraRenderState, state.x - camX, state.y - camY, state.z - camZ, ...)`（`net/minecraft/client/renderer/LevelRenderer.java:724-733`）。
2. `EntityRenderDispatcher.submit` 直接把这个值当作 `translate`：`poseStack.translate(relativeX, relativeY, relativeZ)`（`net/minecraft/client/renderer/entity/EntityRenderDispatcher.java:147-166`）。
3. 方块实体同理：`poseStack.translate(blockPos.getX() - camX, blockPos.getY() - camY, blockPos.getZ() - camZ)`（`LevelRenderer.java:735-745`）。
4. 相机本身的位置就在 `CameraRenderState.pos`（`state/level/CameraRenderState.java:16`），朝向在 `orientation`（`:23`），视图旋转矩阵在 `viewRotationMatrix`（`:30`）。

所以两条换算是全文最常被写错的地方：

```java
// 世界坐标 → 提交用坐标（我要在世界某点放东西）
Vec3 local = world.subtract(cameraRenderState.pos);
poseStack.pushPose();
poseStack.translate(local.x, local.y, local.z);   // 之后按需要 rotate / scale
submitNodeCollector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> { … });
poseStack.popPose();

// 提交用坐标 → 世界坐标（我从 PoseStack 里读到一个位置）
Vec3 world = cameraRenderState.pos.add(
        poseStack.last().pose().getTranslation(new Vector3f()));
```

本项目里这两条都有现成的实现：

- 正向（世界 → 提交）：`BoneMountGeoLayer.submitItemAtBone` 在骨骼位姿上 `translate(offset / 16f, ...)` 后提交物品（`BoneMountGeoLayer.java:283-292,333-345`）。
- 反向（提交 → 世界）：`WeaponAnchorGeoLayer.capture` 把骨骼矩阵的平移加上 `cameraState.pos` 得到骨骼的世界坐标（`WeaponAnchorGeoLayer.java:96-110`）。

忘了减相机的典型症状是"特效/模型死死跟着镜头飞"，而且在相机位置为 (0,0,0) 的测试场景里看不出问题 —— 这是它最坑的地方。

## 6.3 PoseStack 的语义

`com/mojang/blaze3d/vertex/PoseStack.java` 的真实 API：

| 方法 | 行 | 说明 |
|---|---|---|
| `pushPose()` | 51 | 复制栈顶，在栈顶之上工作 |
| `popPose()` | 61 | 丢弃栈顶 |
| `translate(double x, double y, double z)` / `(float…)` / `(Vec3)` | 27 / 31 / 35 | 平移 |
| `scale(float x, float y, float z)` | 39 | 缩放（同时更新法线矩阵） |
| `mulPose(Quaternionfc)` | 43 | 旋转（后乘） |
| `mulPose(Matrix4fc)` / `(Transformation)` | 81 / 85 | 乘任意矩阵 |
| `rotateAround(Quaternionfc, float px, float py, float pz)` | 47 | 绕指定点旋转 |
| `last()` | 69 | 取栈顶 `Pose` |
| `isEmpty()` | 73 | 栈是否为空（配平检查用） |
| `Pose.pose()` / `Pose.normal()` / `Pose.set(Pose)` | 105 / 109 / 99 | 4×4 位姿矩阵 / 3×3 法线矩阵 / 覆盖 |

**组合顺序**：`Pose` 里的三个操作都是"右乘新矩阵"（`mulPose` 语义），也就是说**后调用的变换先作用在顶点上**。

```java
poseStack.translate(1, 0, 0);                                     // 先：把物体挪到 (1,0,0)
poseStack.mulPose(Axis.YP.rotationDegrees(90));                   // 后：再绕 Y 轴自转
// 顶点经历了「先自转、再平移」，得到的是「在 (1,0,0) 处一个转了 90° 的物体」
```

写模型位移时的惯例顺序是 `translate`（到挂点）→ `mulPose`（朝向）→ `scale`（大小），本项目 `applyMountTransform` 就是这个顺序（`BoneMountGeoLayer.java:333-345`），GeckoLib 的骨骼链也是 `translate → rotate → scale`（`com/geckolib/util/RenderUtil.java:90-103`）。

法线不会自动"跟着转"：`Pose.normal()` 是 `pose` 的逆转置 3×3（`PoseStack.java:94-97`），非等比缩放之后必须用它而不是直接拿 `pose` 的左上角去变换法线。

## 6.4 角度、弧度与四元数

Minecraft 与 GeckoLib 的"度/弧度"分工是这样的：

| 位置 | 单位 | 证据 |
|---|---|---|
| 实体 `yRot` / `xRot`、`Axis.*.rotationDegrees(deg)` | 度 | `com/mojang/math/Axis.java:7-22`（`XP = angle -> new Quaternionf().rotationX(angle)`，`rotationDegrees` 才乘 `π/180`） |
| 纯 JOML 的四元数 API | 弧度 | JOML 1.10.8 `org.joml.Quaternionf`：只有 `rotationX/Y/Z`、`rotationXYZ`、`rotationZYX`、`rotationYXZ`、`rotationAxis`，没有"Deg"版本（`javap -classpath joml-1.10.8.jar org.joml.Quaternionf`） |
| GeckoLib 的模型 JSON / 动画文件 | 度 | 加载期统一换算：`GeometryBone.java:70`、`GeometryCube.java:53`、`GeometryLocator.java:39` 用 `Math.toRadians`；关键帧在 `AnimationProcessor.java:266` 用 `Mth.wrapDegrees(value * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD` |
| GeckoLib 运行时（`GeoBone.roty`、`BoneSnapshot.getRotX`） | 弧度 | 上一条的换算结果直接喂给 JOML 的弧度 API：`RenderUtil.optionalRotateZYX` → `quat.rotationZYX((float)z, (float)y, (float)x)`（`RenderUtil.java:193-213`） |

由此得出两条实战规则：

1. 用 `Mth.DEG_TO_RAD` / `Mth.RAD_TO_DEG` 或 `Axis.XP.rotationDegrees(...)` 明确标注单位，**不要**把模型里抄来的数字直接塞进 `new Quaternionf().rotationY(...)`。
2. 从 `BoneSnapshot.getRotX()` 读出来的值要拿去和人写的角度比较时，先 `* Mth.RAD_TO_DEG`。

本项目的 Photon 锚点把这套单位约定固化成了 `AnchorPose`（`client/fx/AnchorPose.java:17-37`）：它明确写着四元数是"弧度语义"，并提供两个把度数转成四元数的工厂：

```java
public static Quaternionf yaw(float yawDegrees) {
    return new Quaternionf().rotationY((float) Math.toRadians(yawDegrees));
}

public static Quaternionf look(float yawDegrees, float pitchDegrees) {
    return new Quaternionf().rotationYXZ((float) Math.toRadians(-yawDegrees),
                                         (float) Math.toRadians(pitchDegrees), 0f);
}
```

四元数的三个常用操作：`new Quaternionf(other)`（拷贝）、`normalize()`（归一化，避免多帧累乘后漂移）、`rotationYXZ`（先 Y 后 X 后 Z，MC 实体的偏航/俯仰就是这一组）。从矩阵里取朝向用 `Matrix4f#getUnnormalizedRotation(Quaternionf)` 或 `getNormalizedRotation(...)`；本项目取的是前者，因为它只需要朝向、不需要归一化（`WeaponAnchorGeoLayer.java:98-99`）。

## 6.5 症状 → 原因 → 修法

| 症状 | 原因 | 修法 |
|---|---|---|
| 模型/特效死死跟着镜头，人往前走它也跟着走 | 把世界坐标直接 `translate` 进了提交阶段（原点其实是相机） | 减掉 `cameraRenderState.pos`，或用 `LevelRenderer.submitEntities` 那样传 `pos - camPos` |
| 在 (0,0,0) 附近正常，走远后越偏越多 | 同上（相机位置偏移被当成绝对坐标） | 同上；测试时务必离开世界原点 |
| 模型整体被推到斜后方半格 | 用了摆件渲染器的默认补偿 `translate(0.5, 0.51, 0.5)`（`GeoObjectRenderer.java:52-55`） | 实体/角色渲染器覆写 `adjustRenderPose` 为空，本项目就是这么做的（`CharacterRenderer.java:41-44`） |
| 模型倒置 / 镜像 | 骨骼旋转符号：GeckoLib 加载期把 X/Y 取负、Z 不变（`GeometryBone.java:70`） | 不要在运行时再补一次负号；要改就在模型源文件改 |
| 旋转方向反了 | 度/弧度混用（差 57.3 倍），或 `yaw` 的正负约定搞反 | 用 `AnchorPose.yaw/look` 这类已封装度数的工厂；`Mth` 里换算 |
| 非等比缩放后光照变暗/发黑 | 用 `pose` 而不是 `normal()` 变换法线 | 用 `poseStack.last().normal()`（`PoseStack.java:109`） |
| 远处的模型不随雾衰减，只有地形褪色 | 自定义管线里雾距离用的是视图空间坐标，没有换回相机相对世界坐标 | 本项目 `entity_skinned.vsh` 用 `transpose(mat3(ModelViewMat)) * (viewRelative - ModelViewMat[3].xyz)` 还原 |
| 多帧之后旋转抖动、角度漂移 | 四元数没归一化，或每帧把上帧结果再乘一次 | 每次乘完 `normalize()`；动画驱动的位置每帧从源头重算，不要累加 |

## 6.6 案例：三个坐标 bug 的完整修复过程

下面三个都来自本项目真实踩过的坑。写法是「症状 → 怎么取证 → 定位 → 修法 → 验证」，
你可以把它当成排查模板：**先证明坐标在哪一步错了，再改代码**，别凭感觉调数值。

### bug 1：特效贴着镜头跑

**症状**：粒子/模型一直糊在屏幕中央，玩家往前走它也跟着走，看起来像屏幕空间效果。

**取证**：

1. 把特效的位置参数打印出来（世界坐标），确认它确实是你要的那个点；
2. 在渲染回调里打印 `poseStack.last().pose()` 的平移分量 —— 如果它约等于 0，
   说明顶点被放在「相机原点」附近。

**定位**：提交阶段的 `PoseStack` 原点是相机（§1.5）。代码把世界坐标直接塞进了栈，没有减相机位置。

**修法**：

```java
// 错：直接把世界坐标丢进提交用的 PoseStack
pose.translate(world.x, world.y, world.z);

// 对：先减相机位置，得到相机相对坐标
Vec3 camera = levelRenderState.cameraRenderState.pos;
pose.translate(world.x - camera.x, world.y - camera.y, world.z - camera.z);
```

**验证**：站着不动转 360° 视角，特效应该始终钉在世界的同一点；走到远处再看一遍。

### bug 2：第一人称或离屏时，挂点「冻住」

**症状**：切到第一人称、或者把角色转出屏幕后，挂在武器骨骼上的特效停在最后一帧的位置不动，
甚至粘在半空中。

**取证**：这个 bug 的取证关键是意识到「**没被渲染的实体根本不会产生骨骼位姿**」。
在抓位姿的地方打点记录"本帧有没有真的抓到"，会看到一段段空缺。

**定位**：骨骼位姿只在渲染那一刻存在（§5.3）。角色不可见时渲染器根本不会被调用，
于是缓存里只有旧数据。项目里的做法是给缓存加**帧龄上限**并用渲染帧号而不是 tick 计时
（`WeaponAnchorCache.java:33` 的 `MAX_AGE_FRAMES = 3`、`:63` 的 `fresh()`），
超过 3 帧没更新就返回 `null`。

**修法**：拿不到新鲜位姿时要**明确选择一种降级行为**，而不是继续用旧值：

```java
var pose = WeaponAnchorCache.fresh(uuid);
if (pose == null) {
    // 选项 A：本帧不跟随（特效停住但不会飞出去，适合常驻光环）
    return;
    // 选项 B：回退到实体自身的世界坐标（适合武器光效，至少还在角色身上）
    // fallbackTo( player.position(), player.getYRot() );
}

// 另外：结果与角色实际位置差太远时整帧丢弃 —— 宁可停住也不要它飞到天边
if (worldPos.distanceToSqr(player.position()) > SANITY_DISTANCE * SANITY_DISTANCE) {
    return;
}
```

（`SANITY_DISTANCE = 8.0` 与这段检查在 `WeaponAnchorGeoLayer.java:52`、`:105`。）

**验证**：第一人称 / 第三人称 / 把角色藏到方块后面，三种情况分别看特效：应该「停住或回退」，
而不是飞到天上或跟到镜头里。

### bug 3：远处只有地形褪色，模型不褪

**症状**：走远之后地形被雾遮住，但角色/自定义模型依然清晰，像浮在雾上。

**取证**：把雾相关的那段 GLSL 输出成纯色，看它在视图空间里算出来的距离是不是"离相机越远越大"。
如果它几乎不随距离变，说明取错了坐标。

**定位**：自定义管线的顶点着色器里，雾距离必须按「相机相对的世界坐标」算，
而 `ModelViewMat` 已经在相机空间里；直接拿它算会混入模型自身的旋转/缩放。

**修法**（本项目的真实写法，`assets/minegenshin/shaders/core/entity_skinned.vsh`）：

```glsl
// 先把视图空间坐标还原成"相机相对的世界坐标"，再交给原版的雾函数
vec3 cameraRelative = transpose(mat3(ModelViewMat)) * (viewRelative - ModelViewMat[3].xyz);
```

**验证**：走到雾的边界处，模型与地形应该**同时**被雾吞掉；换个距离再看一次。
