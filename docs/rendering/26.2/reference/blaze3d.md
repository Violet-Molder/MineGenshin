# 2. Blaze3D API 地图


## 2.1 分层总览

26.2 的 Blaze3D 按「谁拥有什么」可以分成四层，写代码时先想清楚自己在哪一层：

| 层 | 代表类 | 你什么时候碰它 |
|---|---|---|
| 提交层 | `SubmitNodeCollector` / `OrderedSubmitNodeCollector` | 想画自定义几何、往帧里塞东西 |
| 管线层 | `RenderPipeline` / `CompiledRenderPipeline` / `RenderSetup` / `RenderType` | 想把「怎么画」定死：着色器、混合、深度、顶点格式 |
| 顶点层 | `VertexFormat` / `VertexFormatElement` / `VertexConsumer` / `MeshData` | 想定义自己的顶点长什么样 |
| 设备层 | `GpuDevice` / `CommandEncoder` / `RenderPass` / `GpuBuffer` / `GpuBufferSlice` | 想自己开缓冲、传 uniform、发绘制命令 |

## 2.2 `PoseStack` —— 坐标变换栈

**`PoseStack`** 解决一个核心问题：**"这个顶点要画在屏幕上的哪里？"** 它在帧的提交阶段使用，初始原点永远等于**本地玩家相机位置**。

它内部维护了一摞**4×4 变换矩阵**，每层记录一次累积的平移/旋转/缩放。典型用法是四步走：

```
pushPose()  →  translate() → mulPose()/scale()  →  popPose()
  ↑ 保存当前状态      ↑ 移到目标位置     ↑ 调整朝向/大小       ↑ 恢复之前的状态
```

---

### `pushPose()` / `popPose()` —— 状态保存与恢复

**`pushPose()`** 把当前栈顶的变换矩阵**复制一份压到栈上**。从此之后你做的所有 `translate` / `scale` / `mulPose` 都只影响新栈顶，不影响下面的。

**`popPose()`** 丢弃栈顶，恢复到 `pushPose()` 之前的状态。

> **两条铁律：**
> 1. **必须成对出现**——`LevelRenderer.checkPoseStack` 会在帧末检查栈是否为空，多推一次直接抛 `IllegalStateException("Pose stack not empty")`。
> 2. **每画一个独立物体，包一组 `pushPose → ... → popPose`**。不要画了五样东西才弹一次。
> 3. 不能在 `pushPose` 之前 `popPose`，会抛 `ArrayIndexOutOfBoundsException`。

**很多新手困惑「几何不是丢了吗？」**

不会丢。`submitCustomGeometry` / `submitModel` 在 `popPose` 之前就已经把几何**登记进帧的提交收集器**了——几何数据已经不在 PoseStack 手里。`popPose` 丢掉的是**变换矩阵**，不是画好的图案。

可以想象成在一张纸上画画：`pushPose` = 复印一张新纸，`translate`/`scale` = 挪动复印纸的位置。画完（submit）后把复印纸的图案取走（几何已提交），然后 `popPose` = 扔掉这张挪过的复印纸，拿出原来的那张——纸回到原位，方便画下一个东西。

**项目中的例子**（`MobHealthBarHud.java:235-385`）：
```java
// 画多行血条，每行一个独立变换
for (int i = 0; i < bars; i++) {
    poseStack.pushPose();                     // 保存当前
    poseStack.translate(relX, baseY, relZ);   // 移到第 i 行位置
    poseStack.mulPose(camRot);                // 始终面向相机
    poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));  // 翻正
    // ... 画血条
    poseStack.popPose();                      // 恢复，不影响下一行
}
```

---

### `translate(x, y, z)` —— 平移

把坐标系原点从当前位置移动 `(x, y, z)` 距离。

有三种重载：
```java
void translate(double xo, double yo, double zo);   // double 精度
void translate(float xo, float yo, float zo);       // float 精度（更常用）
void translate(Vec3 offset);                         // Vec3 快捷方式
```

**在提交阶段**，平移的值必须是**相对相机的偏移**。因为 `PoseStack` 原点 = 相机位置，所以：

```java
Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
poseStack.translate(
    (float)(worldX - camera.x),
    (float)(worldY - camera.y),
    (float)(worldZ - camera.z)
);
```

**项目中的例子**（`VesnaAttackProjectileRenderer.java:71`）：
```java
// 把弹射物纹理抬高 0.1 格，避免嵌入地面
poseStack.translate(0.0F, 0.1F, 0.0F);
```

**项目中的例子**（`VesnaEnergyHud.java:157`）：
```java
// 把 HUD 图标放在玩家头顶的某个相对位置
Vec3 relative = basePos.subtract(cameraPos);
poseStack.translate(relative.x, relative.y, relative.z);
```

> **常见的坑**：忘了减相机位置，特效会贴在镜头上跟着跑。
> 更坑的是，相机在 `(0,0,0)` 时测试不出问题——因为减去 0 等于没减。

---

### `scale(x, y, z)` —— 缩放

沿三个轴分别缩放。缩放中心是当前的坐标系原点。

```java
void scale(float xScale, float yScale, float zScale);
```

- `xScale`：X 轴放大倍数。1 = 不放大，2 = 两倍大，0.5 = 一半大
- `yScale` / `zScale`：同上。设为负数可以翻转（比如 `-1` 沿轴镜像）

**同时更新法线矩阵**（`Pose.normal()`），非等比缩放（如 `scale(1, 2, 1)`）后法线不会乱。

**项目中的例子**（`MobHealthBarHud.java:527`）：
```java
// 血条大小：水平方向 2 倍，垂直方向 -2 倍（反转 Y 轴，因为 GUI 坐标 Y 向下）
poseStack.scale(2.0f, -2.0f, 2.0f);
```

**项目中的例子**（`VesnaAttackProjectileRenderer.java:73`）：
```java
// 把弹射物纹理缩小到 30%
poseStack.scale(0.3F, 0.3F, 0.3F);
```

> **结合 `translate` 的坑**：`translate(1,0,0)` 后再 `scale(2,2,2)`，
> 缩放中心在 `(1,0,0)` 而不是 `(0,0,0)`——因为变换是后乘，
> 先平移到了 1，缩放是以 1 为中心缩放原本的顶点。
> 想让物体在原点放大后移动到 1，正确的顺序是：`scale → translate`。

---

### `mulPose(Quaternionfc)` —— 旋转

绕任意轴旋转，使用四元数描述旋转量。

```java
void mulPose(Quaternionfc by);       // 用四元数旋转
void mulPose(Matrix4fc matrix);      // 乘任意 4×4 变换矩阵
```

常见旋转方式（`org.joml.Quaternionf`）：
```java
import org.joml.Quaternionf;
import com.mojang.math.Axis;

// 绕 Y 轴转 180°（把背面翻过来）
poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));

// 绕 X 轴转 45°
poseStack.mulPose(Axis.XP.rotationDegrees(45.0F));

// 直接用实体朝向
poseStack.mulPose(entityRenderState.rotation);

// 面向相机（Billboard 效果）
poseStack.mulPose(cameraRenderState.orientation);
```

**项目中的例子**（`VesnaAttackProjectileRenderer.java:72`）：
```java
// 弹射物始终面向玩家相机
poseStack.mulPose(camera.orientation);
```

**项目中的例子**（`MobHealthBarHud.java:238`）：
```java
// 血条始终面对玩家，并且翻转为正面朝外
poseStack.mulPose(camRot);                              // 面向相机
poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));    // 翻正
```

**项目中的例子**（`CharacterConfigPage.java:92`）：
```java
// 预览角色在配置界面里转 180°，让正面朝外
poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
```

> **变换是后乘的**，也就是**后调用的变换先作用在顶点上**：
> ```java
> poseStack.translate(1, 0, 0);                      // 第 1 步：平移
> poseStack.mulPose(Axis.YP.rotationDegrees(90));    // 第 2 步：旋转
> // 顶点实际经历的变换顺序是：先旋转 → 再平移
> // 结果是：在 (1,0,0) 处有一个转了 90° 的物体
> ```
> 写模型位移时的惯例顺序是 `translate`（到挂点）→ `mulPose`（朝向）→ `scale`（大小）。

---

### `rotateAround(Quaternionfc, float px, float py, float pz)` —— 绕指定点旋转

以世界坐标上 `(px, py, pz)` 为旋转中心旋转，而不是以坐标系原点。

```java
void rotateAround(Quaternionfc rotation, float pivotX, float pivotY, float pivotZ);
```

等效的手动实现：
```java
// rotateAround(q, px, py, pz) 等价于：
poseStack.translate(pivotX, pivotY, pivotZ);    // 先把原点移到旋转中心
poseStack.mulPose(q);                            // 旋转
poseStack.translate(-pivotX, -pivotY, -pivotZ); // 移回去
```

**使用场景**：想让关节绕自身中心旋转而不是绕坐标系原点。

---

### `last()` —— 取当前累积变换

```java
PoseStack.Pose last();
```

返回 `PoseStack.Pose`，包含两个字段：

| 方法 | 返回 | 说明 |
|---|---|---|
| `pose()` | `Matrix4f` | 4×4 位姿矩阵。传入 `VertexConsumer.addVertex()` 决定顶点位置 |
| `normal()` | `Matrix3f` | 3×3 法线矩阵。非等比缩放后必须用这个算光照法线 |

**项目中的例子**（`BoneWalker.java:204`）：
```java
// 取骨骼的最终变换矩阵，传给 VertexConsumer 画顶点
final Matrix4f pose = poseStack.last().pose();
// 取法线矩阵，算光照
final Matrix3f normal = poseStack.last().normal();
```

> **不要跨帧/跨 lambda 持有 `last()` 的返回值！**
> `last().pose()` 返回的是栈顶矩阵的**视图引用**，不是快照。
> 如果你在 lambda（比如 `submitCustomGeometry` 的回调）里用，等到真正运行时栈顶可能已经被 `popPose()` 弹掉了。
> **安全的做法**：`new Matrix4f(poseStack.last().pose())` 拷一份。

**❌ 反面：lambda 捕获了视图引用，执行时栈顶已变**

```java
// 在提交阶段循环画多个方块，每个方块有自己的 translate
for (BlockPos pos : positions) {
    poseStack.pushPose();
    poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);

    // ❌ 坏：lambda 捕获了 poseStack.last().pose() 的视图引用
    Matrix4f badRef = poseStack.last().pose();
    collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
        // 等到这个 lambda 真正执行时，上面的 popPose 可能已经跑了，
        // badRef 指向的栈顶内容已经变了，
        // 画出来的所有方块都在同一个位置！
        buffer.addVertex(badRef, 0f, 0f, 0f).setColor(0xFFFFFFFF);
    });

    poseStack.popPose();
}
// 结果：所有方块堆在同一坐标，最后一个循环覆盖了前面的
```

**✅ 正面：lambda 捕获快照副本，无论何时执行都不受影响**

```java
for (BlockPos pos : positions) {
    poseStack.pushPose();
    poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);

    // ✅ 好：用 new Matrix4f() 在提交时就拍好快照
    Matrix4f snapshot = new Matrix4f(poseStack.last().pose());
    collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
        // snapshot 是独立副本，不依赖栈顶状态
        buffer.addVertex(snapshot, 0f, 0f, 0f).setColor(0xFFFFFFFF);
    });

    poseStack.popPose();
}
// 结果：每个方块正确画在自己对应的世界坐标上
```

> 这条同样适用于 `poseStack.last().normal()`。法线矩阵也是视图引用，跨 lambda 也要拷：`new Matrix3f(poseStack.last().normal())`。

## 2.3 提交层：`SubmitNodeCollector` —— 几何的"收件箱"

`net.minecraft.client.renderer.SubmitNodeCollector` 是接口，继承 `OrderedSubmitNodeCollector`。
**你在提交阶段拿到它之后，调用它的各种 `submit*` 方法把几何登记进去，管线在后面真正的绘制阶段再取出画到屏幕上。**

> **提交 ≠ 绘制。** `submit*` 只是把几何数据记到帧的"待绘制列表"里。真正的 GPU 绘制发生在后面。
> 所以提交阶段做不了的事（比如读像素、写深度缓冲）也就别想了。

### 控制绘制顺序：`order(int)`

在调任何 `submit*` 之前，可以先指定**绘制序号**：

```java
collector.order(1)                          // 设当前绘制序号为 1
    .submitModel(model, state, pose, ...);  // 序号 1 的这一批
collector.order(0)                          // 改成 0
    .submitModel(model, state, pose, ...);  // 序号 0 的这一批，画在更前面
```

同序号内不保证相对顺序。不调 `order()` 默认是 0。低序号先画（先画 = 被后面的覆盖，适合背景）。

---

### `submitCustomGeometry(pose, renderType, callback)` —— 自定义几何

最灵活的提交方式。你自己在回调里写顶点，画任何形状。

```java
void submitCustomGeometry(
    PoseStack poseStack,                                  // 变换（位置/朝向/大小）
    RenderType renderType,                                // 怎么画（着色器/混合/深度）
    SubmitNodeCollector.CustomGeometryRenderer renderer   // 写顶点的回调
);
```

第三个参数是一个函数式接口，回调里拿到 `(PoseStack.Pose pose, VertexConsumer consumer)`：

| 拿到的东西 | 干什么用 |
|---|---|
| `pose.pose()` | 4×4 变换矩阵，传给 `consumer.addVertex()` 决定顶点位置 |
| `pose.normal()` | 3×3 法线矩阵，光照计算用（`setNormal`） |
| `consumer` | `VertexConsumer`，一个个地写顶点 |

**典型流程**（摘自 `VesnaEnergyHud.java:170-184`）：
```java
// 在世界中某个位置画三个能量槽方块，每个方块调用一次 submitCustomGeometry
for (int i = 0; i < 3; i++) {
    float yLow = baseY + i * (SEG_HEIGHT + SEG_GAP);
    float yHigh = yLow + SEG_HEIGHT;

    // 背景槽（半透明灰）
    collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
        Matrix4f matrix = pose.pose();
        drawRect(buffer, matrix, 0,
            -halfW, yLow, halfW, yHigh,
            0.10f, 0.10f, 0.15f, 0.60f);   // RGBA
    });

    // 填充槽（黄色，z 偏移 -0.001 防 Z-fighting）
    if (fillRatio > 0.001f) {
        float fillYHigh = yLow + SEG_HEIGHT * fillRatio;
        collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
            Matrix4f poseMatrix = pose.pose();
            drawRect(buffer, poseMatrix, -0.001f,
                -halfW, yLow, halfW, fillYHigh,
                1.0f, 0.82f, 0.35f, 1.0f);
        });
    }
}
```

**另一个例子**（`MobHealthBarHud.java:250-277`）—— 用不同的 RenderType 画血条的背景层、缓冲层、填充层三层叠加：
```java
// 背景层
collector.submitCustomGeometry(poseStack, bgType, (pose, buffer) -> {
    drawTexturedQuad(buffer, matrix, ...);  // 带纹理的血条底框
});
// 填充层
collector.submitCustomGeometry(poseStack, barType, (pose, buffer) -> {
    drawTexturedQuad(buffer, matrix, ...);  // 按血量比例宽度的彩色条
});
```

**注意事项：**
- **不要把 lambda 跨帧持有。** 回调在后面的绘制阶段才执行，如果 lambda 里引用了外层变量，确保它们不会被提前释放。
- **`PoseStack` 快照**：如果回调延迟执行，`poseStack` 可能已经被 `popPose()` 弹了。安全的做法是在提交时用 `new Matrix4f(poseStack.last().pose())` 拷一份传给 lambda。

---

### `submitModel(pose, renderType, ...)` —— 提交模型

用于提交**已定义好的模型**（实体模型、方块实体模型、物品模型）。

完整签名（`OrderedSubmitNodeCollector.java:66`）：
```java
<S> void submitModel(
    S model,                          // 模型实例（如 EntityModel、BakedGeoModel）
    S renderState,                    // 渲染状态（含动画变换、叠加层颜色等）
    PoseStack poseStack,              // 变换
    RenderType renderType,            // 渲染类型
    int lightCoords,                  // 光照坐标（天空光 + 方块光，编码成 int）
    int overlayCoords,                // 叠加层坐标（受伤闪白用）
    int tintedColor,                  // 染色 -1 表示不染色
    @Nullable Sprite sprite,          // 纹理精灵（null=从 renderState 取）
    int outlineColor,                 // 轮廓颜色（0 = 无轮廓）
    @Nullable CrumblingOverlay crumblingOverlay  // 破坏叠加（null = 无）
);
```

**典型用法**（GeckoLib 实体层）：  
```java
// 在实体渲染层的 submit 方法里
collector.order(1)
    .submitModel(
        this.getParentModel(),        // 模型
        state,                        // 实体渲染状态
        poseStack,                    // 父级传下来的 PoseStack
        this.renderType(),            // 纹理对应的 RenderType
        lightCoords,                  // 光照
        OverlayTexture.NO_OVERLAY,    // 无受伤闪白
        state.outlineColor,           // 轮廓
        null                          // 无自定义精灵
    );
```

项目参考：`GeoRenderIntercept.java:147-151` 用 `submitModel` 提交完整 GeckoLib 模型。

---

### `submitText(pose, x, y, text, dropShadow, mode, bgColor, color, maxWidth, maxLines)` —— 提交文字

在世界中（而非 GUI）渲染文字。常用于血条上的等级标签、名字标签。

```java
void submitText(
    PoseStack poseStack,               // 变换
    float x, float y,                  // 局部坐标偏移（文字左下角）
    TextVisitable text,                // 文字内容（用 Component.literal().getVisualOrderText() 转换）
    boolean dropShadow,                // 是否带投影
    Font.DisplayMode mode,             // SEE_THROUGH / NORMAL / POLYGON_OFFSET
    int backgroundColor,               // 背景色 ARGB（0 = 透明背景）
    int color,                         // 文字色 ARGB
    int maxWidth,                      // 最大宽度像素（0 = 不限）
    int maxLines                       // 最大行数（0 = 不限）
);
```

**项目中的例子**（`MobHealthBarHud.java:529-542`）—— 在怪物头顶显示 "Lv.N"：
```java
collector.submitText(
    poseStack,
    -width / 2.0f,                     // 居中：向左偏移文字宽度的一半
    0.0f,                              // 基线高度
    Component.literal(text).getVisualOrderText(),  // "Lv.42"
    true,                              // 带投影
    Font.DisplayMode.SEE_THROUGH,      // 透视模式（透过方块也能看到）
    0xF000F0,                          // 背景色（半透明黑底）
    0xFFFFFFFF,                        // 白色文字
    0,                                 // 不限宽
    0                                  // 不限行
);
```

---

### `submitNameTag(pose, renderState, ...)` —— 提交名字标签

实体的名字标签提交入口，由 `LivingEntityRenderer` 内部调用，一般不需要你手动调。

### `submitItem(pose, renderState, ...)` —— 提交物品模型

用于在世界中渲染物品模型，常用于骨骼挂点上的手持物品渲染。

**项目参考**：`BoneMountGeoLayer.java:283-292` 在骨骼位姿上用 `Minecraft.getInstance().getItemModelResolver().updateForLiving(...)` 构造物品状态，然后提交物品模型到挂点上。

### `submitBlockModel(pose, renderState, ...)` —— 提交方块模型

提交已经烘焙好的方块模型，方块实体的渲染走这个。一般不需要手动调。

### `submitShadow(pose, radius, pieces)` —— 提交阴影

提交实体底部的圆形阴影。`radius` 控制阴影大小，`pieces` 是多块阴影的集合（例如分段的生物阴影）。

```java
void submitShadow(
    PoseStack poseStack,
    float radius,                              // 阴影半径
    List<EntityRenderState.ShadowPiece> pieces // 阴影段（多段合成复杂形状）
);
```

默认由 `EntityRenderDispatcher` 自动提交，一般不需要手动调。

---

### `submitFlame(pose, renderState, rotation)` —— 提交火焰

在实体身上绘制火焰效果（着火时的橙黄色火焰包裹）。`rotation` 是火焰纹理的旋转。

```java
void submitFlame(
    PoseStack poseStack,
    EntityRenderState renderState,    // 实体的渲染状态
    Quaternionf rotation              // 火焰纹理朝向
);
```

默认由 `EntityRenderDispatcher` 自动提交。如果你需要自定义着火效果，可以拦截后自己调这个。

---

### `submitLeash(pose, leashState)` —— 提交拴绳

绘制拴绳的曲线。`leashState` 包含绳子两端的位置信息。

```java
void submitLeash(
    PoseStack poseStack,
    EntityRenderState.LeashState leashState  // 拴绳状态（起点/终点坐标）
);
```

默认由 `LivingEntityRenderer` 自动提交。

---

### `submitShapeOutline(pose, shape, renderType, color, width, afterTerrain)` —— 提交形状轮廓

用于调试渲染——画一个 `VoxelShape` 的轮廓线。碰撞箱轮廓、方块选取框都走这个。

```java
void submitShapeOutline(
    PoseStack poseStack,
    VoxelShape shape,                 // 轮廓形状
    RenderType renderType,            // 渲染类型（线框用 RenderTypes.LINES）
    int color,                        // ARGB 颜色
    float width,                      // 线宽
    boolean afterTerrain              // true = 地形之后画（不被方块遮挡）
);
```

**典型场景**：`F3+B` 调试碰撞箱就是通过这个方法渲染的。一般不需要手动调。

---

### `submitQuadParticleGroup(particles)` —— 提交粒子批

批量提交纹理面片粒子，用于粒子特效的批量渲染。

```java
void submitQuadParticleGroup(
    QuadParticleRenderState particles  // 一批纹理面片粒子的集合
);
```

粒子系统内部使用，一般不需要手动调。

## 2.4 `RenderPipeline`：26.2 的「怎么画」

一条管线把「着色器 + 顶点布局 + 混合 + 深度/模板 + 颜色目标 + 绑定组」打包成一个**不可变对象**，
用 Builder 造、用 NeoForge 事件注册。Builder 的真实方法
（`com/mojang/blaze3d/pipeline/RenderPipeline.java:226-420`）：

```java
RenderPipeline.builder(RenderPipeline.Snippet... snippets)      // 用现成的片段起步
    .withLocation("minegenshin:pipeline/glow")                  // 管线 id
    .withVertexShader("core/entity")                            // 资源路径：assets/<ns>/shaders/core/entity.vsh
    .withFragmentShader("core/entity")
    .withShaderDefine("ALPHA_CUTOUT")
    .withBindGroupLayout(BindGroupLayouts.GLOBALS)              // 声明 uniform/纹理绑定
    .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)   // 顶点格式
    .withPrimitiveTopology(PrimitiveTopology.QUADS)
    .withCull(false)
    .withDepthStencilState(DepthStencilState.DEFAULT)
    .withColorTargetState(ColorTargetState.DEFAULT)
    .build();
```

两个工程上的要点：

- **`Snippet` 是复用单位**。原版自己的管线就是这么搭的：`GLOBALS_SNIPPET` → `MATRICES_FOG_SNIPPET`
  → `ENTITY_SNIPPET` 层层叠加（`net/minecraft/client/renderer/RenderPipelines.java:26-93`）。
  自定义管线也应该从 `MATRICES_FOG_SNIPPET` 这类片段起步，别从零拼，否则漏掉矩阵 UBO 就会画出一片空白。
- **注册必须走事件**：`RegisterRenderPipelinesEvent#registerPipeline(RenderPipeline)`
  （`neoforge-src:.../RegisterRenderPipelinesEvent.java:36`），事件在**默认管线注册之后**触发，
  且只在客户端、mod 总线上。

```java
// 注册入口（NeoForge 26.2）：mod 总线、仅客户端
@SubscribeEvent
static void onRegisterPipelines(RegisterRenderPipelinesEvent event) {
    event.registerPipeline(MyPipelines.GLOW);
}
```

## 2.5 `RenderType` 与 `RenderTypes`

`RenderType` 是「管线 + 那一层绘制时附带的状态」的组合体（名字、纹理、输出目标、分层变换），
26.2 的构造签名是（`net/minecraft/client/renderer/rendertype/RenderType.java:35`）：

```java
public static RenderType create(String name, RenderSetup state);

public static final RenderType LINES = RenderType.create(
        "lines",
        RenderSetup.builder(RenderPipelines.LINES)
                .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                .createRenderSetup()
);
```

也就是说：**`RenderType` 没被删，改的是它的入参形状** —— 以前是一长串「缓冲器 + 格式 + 模式」，
现在是「先挑管线（`RenderSetup.builder(RenderPipeline)`），再补纹理/输出目标/分层变换」。
`RenderTypes` 是原版常量的集中地：既有现成的常量，也有 58 个静态工厂方法。调试与自绘时最常用的几个
（都在 `RenderTypes.java` 里，括号是行号）：

| 工厂 / 常量 | 用途 | 顶点怎么给 |
|---|---|---|
| `lines()` / `linesTranslucent()`（`:624`、`:628`） | 画线（可半透明） | `POSITION_COLOR_NORMAL_LINE_WIDTH`，记得 `setLineWidth` |
| `debugFilledBox()`（`:636`） | 实心盒（调试图元） | 四顶点一面的四边形 |
| `debugQuads()`（`:644`） | 独立的四边形集合 | 每 4 个顶点一个面 |
| `debugPoint()`（`:640`） | 画点 | 单顶点 |
| `debugTriangleFan()`（`:648`） | 三角扇（画圆形/扇形指示器） | 首顶点 + 后续扇形顶点 |
| `outline(Identifier texture)`（`:552`） | 描边 | 见该方法的实现 |

写自定义几何时先在这张表里找现成的，找不到再 `RenderType.create(...)` 自己造 —— 少写一条管线，
就少一份"混合/深度不对"的排查工作。

## 2.6 顶点与几何数据

| 用途 | 类 | 关键点 |
|---|---|---|
| 顶点格式描述 | `com.mojang.blaze3d.vertex.VertexFormat`、`VertexFormatElement` | 元素的语义/分量数/偏移；与管线的 `withVertexBinding` 必须一致 |
| 常用格式 | `com.mojang.blaze3d.vertex.DefaultVertexFormat` | `POSITION_COLOR`、`POSITION_TEX` 等常量 |
| 写顶点 | `com.mojang.blaze3d.vertex.VertexConsumer` | `addVertex(PoseStack.Pose, float,float,float)` 之后链式设属性 |
| 一段几何的数据容器 | `com.mojang.blaze3d.vertex.MeshData` | `vertexBuffer()` / `indexBuffer()` / `drawState()` / `sortQuads(...)`，实现 `AutoCloseable` |

`MeshData` 是 26.2 里替代旧 `BufferBuilder.end()` 的东西：构建出来的几何**可以排序、可以缓存**，
`sortQuads(...)` 就是给半透明四方体排索引用的（`com/mojang/blaze3d/vertex/MeshData.java:67`）。

## 2.7 设备、命令编码器与渲染通道

自己发绘制命令时按这个顺序（全部在客户端渲染线程）：

```java
GpuDevice device = RenderSystem.getDevice();                       // 设备
CommandEncoder encoder = device.createCommandEncoder();            // 编码器
try (RenderPass pass = encoder.createRenderPass(() -> "mg-glow", colorTextureView, Optional.empty())) {
    pass.setPipeline(MyPipelines.GLOW);
    pass.setUniform("Globals", globalSettingsUniform);             // 按名字绑 uniform 缓冲
    pass.bindTexture("Sampler0", textureView, null);
    pass.setVertexBuffer(0, vertexBufferSlice);
    pass.setIndexBuffer(indexBuffer, IndexType.SHORT);
    pass.drawIndexed(indexCount, 1, 0, 0, 0);
}
encoder.submit();
```

`RenderPass` 的可用方法（`com/mojang/blaze3d/systems/RenderPass.java`，节选）：
`setPipeline`、`setUniform(String, GpuBuffer|GpuBufferSlice)`、`bindTexture`、`setVertexBuffer`、
`setIndexBuffer`、`setViewport`、`enableScissor`/`disableScissor`、`drawIndexed`、`draw`、
`multiDrawIndexed`、`drawIndexedIndirect`、`drawMultipleIndexed`、`pushDebugGroup`/`popDebugGroup`。

`GpuDevice` 负责建资源（`com/mojang/blaze3d/systems/GpuDevice.java`）：
`createCommandEncoder()`、`createBuffer(label, usage, size|ByteBuffer)`、`createTexture(...)`、
`createTextureView(...)`、`createSampler(...)`、`precompilePipeline(RenderPipeline)`。

`RenderSystem` 在 26.2 里已经退化成「全局状态与环境」的门面，剩下的常用成员是
（`com/mojang/blaze3d/systems/RenderSystem.java`）：`getDevice()`、`isOnRenderThread()`、
`assertOnRenderThread()`、`getModelViewStack()`、`getProjectionMatrixBuffer()`、
`setShaderLights(GpuBufferSlice)`/`setShaderFog(GpuBufferSlice)`、`getSequentialBuffer(PrimitiveTopology)`、
`setGlobalSettingsUniform(GpuBuffer)`。

## 2.8 缓冲与 uniform：`Std140Builder`

往 UBO 里塞数据只有一条路：按 std140 规则排布，再用 `Std140Builder` 写
（`com/mojang/blaze3d/buffers/Std140Builder.java`）：

```java
int size = new Std140SizeCalculator().putVec3().putFloat().get();   // 先算大小（含对齐填充）
GpuBuffer buffer = device.createBuffer(() -> "mg-glow-data", GpuBuffer.USAGE_UNIFORM, size);

try (MemoryStack stack = MemoryStack.stackPush()) {
    Std140Builder builder = Std140Builder.onStack(stack, size);
    builder.putVec3(glowColor.x, glowColor.y, glowColor.z);
    builder.putFloat(intensity);
    encoder.writeToBuffer(buffer.slice(), builder.get());            // 上传
}
```

`Std140Builder` 提供 `putFloat` / `putInt` / `putVec2|3|4` / `putIVec2|3|4` / `putMat4f` / `align(int)`
（`Std140Builder.java:38-148`）；`Std140SizeCalculator` 有同名的 `putXxx()`，两边**必须一一对应**，
否则数据会错位——这类 bug 的症状通常是「前几个参数对、后面的全是垃圾值」。

> std140 的两条硬规则（GPU 规范，不是 MC 特有）：`vec3` 按 `vec4` 对齐（占 12 字节、吃 16 字节），
> 数组元素按 16 字节步长排。`putVec3` 之后紧跟 `putFloat` 是安全的（正好填满一个 `vec4`），
> 但 `putVec3` 后面跟 `putVec3` 就会出洞。

## 2.9 已消失 / 已改名的旧 API

这一节专门用来纠正网上（以及本仓库早期文档里）仍然常见的 1.21 写法。逐条都在 26.2 的源码里核对过：

| 旧写法（1.21 及以前） | 26.2 的现状 | 替代 |
|---|---|---|
| `RenderSystem.setShader(Supplier<ShaderInstance>)` | **类里已没有这个方法**（只剩 `setShaderFog` / `setShaderLights`） | 用 `RenderPipeline` + `RenderType`；`RenderSystem.java:113-125` |
| `Tesselator.getInstance().begin(...)` / `Tesselator.end()` | **`Tesselator` 类已不存在** | 提交几何走 `SubmitNodeCollector`；要自己的数据容器用 `MeshData` |
| `BufferUploader.drawWithShader(...)` | **`BufferUploader` 类已不存在** | `RenderPass#setVertexBuffer/setIndexBuffer + drawIndexed` |
| `ShaderInstance`（与 `assets/.../shaders/core/*.json` 配对） | **类已不存在** | `RenderPipeline`（代码/事件注册）取代了 core shader JSON |
| `RenderType.create(...)` 的旧签名 | **方法还在，签名换了** | `RenderType.create(String, RenderSetup)`，管线由 `RenderSetup.builder(RenderPipeline)` 提供 |
| `PoseStack.last().pose()` 直接丢给渲染器 | 仍在用 | 但要意识到它是**相机相对**坐标（§1.5） |

**结论**：任何还在教 `RenderSystem.setShader` + `ShaderInstance` + core shader JSON 的资料，都是 1.21 及以前的，
在 26.2 上照抄必然编译不过。26.2 的心智模型是「管线是代码里的不可变对象，由事件注册」。

## 2.10 案例：一次自定义绘制要动哪些类

这一节把前面散落的类按「你的需求」串成三条可执行路线。三条路线都真实可用，选哪条取决于你要画的是
「随帧合批的一次性几何」还是「自己长期持有的缓冲」。

| 你的需求 | 走哪条路 | 涉及的类（按调用顺序） |
|---|---|---|
| 每帧往世界里塞一点几何（指示器、连线、简单面片） | **提交层** | `SubmitCustomGeometryEvent` → `SubmitNodeCollector#submitCustomGeometry` → 回调里用 `VertexConsumer` 写顶点 |
| 自己造几何、自己管显存、自己发绘制命令 | **设备层** | `ByteBufferBuilder` + `BufferBuilder` → `MeshData` → `GpuDevice#createBuffer` → `CommandEncoder#createRenderPass` → `RenderPass#drawIndexed` |
| 想让这批几何有自己的着色器/混合/顶点格式 | **管线层 + 上面任一条** | `RenderPipeline.builder(...)` + `RegisterRenderPipelinesEvent`，再用 `RenderSetup.builder(pipeline)` 包成 `RenderType` |

设备层的完整范例在 vanilla 里就有：`net/minecraft/client/renderer/DebugCrosshairRenderer.java`（3D 调试准星）。
它把「建缓冲 → 建通道 → 绑管线 → 画索引」这条链完整走了一遍，值得逐行读：

```java
// ① 建顶点：BufferBuilder + ByteBufferBuilder，格式必须与管线的顶点绑定一致
try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(FORMAT.getVertexSize() * vertexCount)) {
    BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.LINES, FORMAT);
    builder.addVertex(x0, y0, z0).setColor(0xFFFFFFFF).setNormal(1f, 0f, 0f).setLineWidth(4f);
    builder.addVertex(x1, y1, z1).setColor(0xFFFFFFFF).setNormal(1f, 0f, 0f).setLineWidth(4f);

    try (MeshData mesh = builder.buildOrThrow()) {
        // ② 上传到显存（32 = GpuBuffer.USAGE_VERTEX）
        GpuBuffer vertexBuffer = RenderSystem.getDevice().createBuffer(() -> "my lines", 32, mesh.vertexBuffer());
    }
}

// ③ 每帧绘制：拿顺序索引缓冲 + 写动态变换 + 开 pass
GpuBuffer indexBuffer = RenderSystem.getSequentialBuffer(PrimitiveTopology.LINES).getBuffer(indexCount);
GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelViewStack));

try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
        .createRenderPass(() -> "my lines", colorTexture, Optional.empty(), depthTexture, OptionalDouble.empty())) {
    pass.setPipeline(RenderPipelines.LINES);
    RenderSystem.bindDefaultUniforms(pass);
    pass.setVertexBuffer(0, vertexBuffer.slice());
    pass.setIndexBuffer(indexBuffer, RenderSystem.getSequentialBuffer(PrimitiveTopology.LINES).type());
    pass.setUniform("DynamicTransforms", transforms);
    pass.drawIndexed(indexCount, 1, 0, 0, 0);
}
```

这段里每一句都能在 `DebugCrosshairRenderer.java:28-90` 找到出处。三个容易忽略的点：

1. **`MeshData` 要关**（它实现了 `AutoCloseable`，内部是直接内存）。
2. **`ByteBufferBuilder`/`BufferBuilder` 是构建期用的**，画的时候只用 `GpuBuffer`；不要每帧重新建缓冲。
3. **动态变换走 `RenderSystem.getDynamicUniforms().writeTransform(...)`**，
   再 `pass.setUniform("DynamicTransforms", slice)` 绑上去——名字是固定的，写错就是所有东西堆在原点。

## 2.11 派生：一次改动覆盖「一族」管线

§3.8 讲的是从零加一条管线。这一节讲另一种更常见、也更容易出错的做法：
**不改原版，而是从原版管线派生出一族自己的版本**。

### 什么时候需要派生

当你的改动是「顶点阶段换一套算法」，而片元阶段、混合、深度、剔除、剪裁全部照旧时，
手搓一条管线是不够用的：原版 `entity` 管线有一整族变体

| 变体 | 差别 |
|---|---|
| `ENTITY_SOLID` / `ENTITY_SOLID_Z_OFFSET_FORWARD` | 不透明，带/不带深度偏移 |
| `ENTITY_CUTOUT` / `ENTITY_CUTOUT_CULL` / `ENTITY_CUTOUT_Z_OFFSET` | 镂空，带/不带背面剔除 |
| `ENTITY_TRANSLUCENT` / `ENTITY_TRANSLUCENT_CULL` | 半透明 |
| `ENTITY_TRANSLUCENT_EMISSIVE` | 半透明自发光 |

手搓一条只能覆盖其中一个，剩下的全部回退到原版路径 —— 表现就是「优化/改动只生效了一半」，
而且只在特定材质或特定光照下才看得出来，极难排查。

### `toBuilder()` 带过来什么

`RenderPipeline#toBuilder()` 会把原管线原样搬过来：location、两个着色器、**全部 shader defines**、
全部 bind group layout、深度/模板状态、多边形模式、剔除、颜色目标、顶点格式、图元拓扑
（`com/mojang/blaze3d/pipeline/RenderPipeline.java:163-195`）。于是「只换顶点着色器」变成三行：

```java
private static RenderPipeline derive(RenderPipeline base) {
    return base.toBuilder()
            // location 必须唯一：注册表按 location 建索引，沿用基管线会直接撞名
            .withLocation(Minegenshin.id("pipeline/skinned_" + base.getLocation().getPath().replace('/', '_')))
            .withVertexShader(Minegenshin.id("core/entity_skinned"))   // 换掉顶点着色器
            .withBindGroupLayout(SKIN_DATA_LAYOUT)                     // 追加一组常量缓冲
            .withVertexBinding(0, SkinnedMesh.FORMAT)                  // 覆盖 0 号槽的顶点格式
            .build();
}

public static void registerPipelines(RegisterRenderPipelinesEvent event) {
    for (RenderPipeline base : WHITELIST) {
        event.registerPipeline(derive(base));
    }
}
```

两个方法名字很像、行为相反，这是派生做法最需要记牢的一点：

- `withBindGroupLayout(...)` 是**追加**（`RenderPipeline.java:283-290`）——挂上自己的一组 uniform；
- `withVertexBinding(int, VertexFormat)` 是**同号覆盖**（`:328-331`）——替换某个槽位的顶点布局。

片元着色器**刻意不动**：ALPHA_CUTOUT、半透明混合、覆盖层采样这些行为全都来自基管线，
所以一条派生逻辑能覆盖整族。本项目 `SkinnedPipelines.java:100-116` 就是这么做的。

### 白名单纪律：基管线用到的 define，你的着色器必须都有分支

不是所有基管线都能派生。本项目刻意排除了两个：

| 被排除的基管线 | 原因 |
|---|---|
| `ENTITY_CUTOUT_DISSOLVE` | 带 `DISSOLVE` define，还会多加一组 DISSOLVE_MASK 采样器；顶点着色器里没有对应分支 |
| `BREEZE_WIND` | 带 `APPLY_TEXTURE_MATRIX`；我们的顶点着色器不支持纹理矩阵 |

硬派生这两条的后果是「该溶解的没溶解、该套纹理矩阵的没套」——画面差异很隐蔽。
**做法**：先把候选基管线的 define 集合列出来，逐个确认你的着色器都有等价分支，再放进白名单。

### 两条死规则

1. **静态字段里只准构建「描述对象」。** 管线注册发生在设备初始化之前，那时
   `RenderSystem.getDevice()` 还不能用；在静态初始化里建 `GpuBuffer`、调 `precompilePipeline` 一律失败
   （`SkinnedPipelines.java:34-36` 的类注释专门写了这条）。
2. **注册事件是 mod 总线、仅客户端。** 订阅总线写错不会报错，只是「什么也没发生」。

### 派生之外的第三条路：留一道回退闸门

派生出的管线只有在「基管线还是原版那条、顶点格式也没被别人改过」时才是安全的。
光影包会替换实体管线，别的模组可能改顶点格式，所以本项目在取派生管线时先过一道环境自检：

```java
public static @Nullable RenderPipeline skinnedFor(RenderPipeline base) {
    RenderPipeline derived = DERIVED.get(base);
    if (derived == null) {
        return null;                                    // 不在白名单 / 还没注册
    }
    return SkinnedPipelineGuard.allows(base, derived) ? derived : null;   // 被环境挡下 → 回退
}
```

返回 `null` 的语义是「**现在不能走这条路，请走原版路径**」，而不是抛异常。
被挡下的原因会显示在 F3 上（`RenderOptimizeStats.java:30-35`），玩家装了光影包时你能立刻看出是回退而不是崩坏。
