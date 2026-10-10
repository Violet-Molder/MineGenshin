# 4. GeckoLib 5 的渲染管线


本项目所有角色模型都走 GeckoLib。它比原版实体渲染多一层「渲染层 + 骨骼回调」的结构，
也是**唯一能拿到骨骼位姿的地方**。

## 4.1 本项目走的是哪条路

GeckoLib 有三类渲染器：`GeoEntityRenderer`（标准实体）、`GeoObjectRenderer`（通用摆件）、
`GeoReplacedEntityRenderer`（替换实体）。**本项目用的是 `GeoObjectRenderer`**：

```
AvatarRenderer.submit(...)                        ← 原版玩家渲染
  └─ AvatarRendererMixin（HEAD，cancellable）     ← 拦掉
       └─ CharacterRenderDispatcher.handleSubmit(...)
            └─ CharacterRenderer.performRenderPass(...)   ← extends GeoObjectRenderer
```

`CharacterRenderer` 覆写了三处（`client/render/character/CharacterRenderer.java`）：

1. **`adjustRenderPose` 覆写成空** —— 去掉 `GeoObjectRenderer` 默认的 `translate(0.5f, 0.51f, 0.5f)`。
   角色模型以原点为中心，不需要摆件那半格补偿。⚠️ 任何新写的 `GeoObjectRenderer` 子类**默认会吃到这半格偏移**。
2. **`submitRenderTasks` 覆写** —— 转给本模组的几何优化系统，失败时回退 GeckoLib 默认实现。
3. 自定义 `GeoModel`（`CharacterPlayerModel`），按 `CharacterRenderData` 决定模型/贴图/动画路径。

## 4.2 一趟渲染的完整调用顺序

下面这份顺序是从 `GeoRenderer` / `GeoObjectRenderer` / `GeoRendererInternals` / `RenderPassInfo` 源码里逐行对出来的，
是本章最重要的一张表：

| 序 | 调用 | 位置 | 说明 |
|---|---|---|---|
| — | `fillRenderState(...)` | `GeoObjectRenderer:72` | **所有 `addRenderData` 在这里跑完**，早于 `performRenderPass` |
| 0 | `renderState.addGeckolibData(DataTickets.PACKED_LIGHT, packedLight)` | `GeoObjectRenderer:74` | 光照值 |
| 1 | `poseStack.pushPose()` | `GeoRenderer:108` | 开栈 |
| 2 | `getRenderType(...)` | `GeoRenderer:110` | 返回 null → 本趟不画几何（但动画与骨骼回调**照跑**） |
| 3 | `RenderPassInfo.create(...)` | `GeoRenderer:111` → `RenderPassInfo:279` | 记 `objectRenderPose`；**自动注册两个 BoneUpdater**：`applyAnimationControllers`、`adjustModelBonesForRender` |
| 4 | 注册外部 `boneUpdaters` | `GeoRenderer:113-117` | 本项目 5 个角色骨骼 updater 在这一批 |
| 5 | `firePreRenderEvent(...)` | `GeoRenderer:119` | 返回 false 可取消整趟 |
| 6 | `preRenderPass(...)` | `GeoRenderer:120` | 用户钩子 |
| 7 | `scaleModelForRender(info, 1, 1)` | `GeoRenderer:121` | 参数为 1 时不缩放 |
| 8 | `adjustRenderPose(...)` | `GeoRenderer:122` | `GeoObjectRenderer` 默认半格平移；**本项目覆写为空** |
| 9 | `preApplyRenderLayers(...)` | `GeoRenderer:123` | 按层注册顺序：先 `layer.preRender(...)`，再 `layer.addPerBoneRender(info, info::addPerBoneRender)` |
| 10 | `captureModelRenderPose()` | `GeoRenderer:124` | 存下 `modelRenderPose`。**此后再调 `getModelRenderMatrixState()` 才合法** |
| 11 | `submitRenderTasks(...)` | `GeoRenderer:125` | `submitCustomGeometry` → `renderPosed(() -> model.render(...))`，**骨骼几何在这里产生** |
| 12 | `submitPerBoneRenderTasks(...)` | `GeoRenderer:126` | 第二次 `renderPosed`，逐骨骼 `transformToBone` 后调你的 `PerBoneRender` |
| 13 | `applyRenderLayers(...)` | `GeoRenderer:127` | 逐层 `layer.submitRenderTask(...)` |
| 14 | `poseStack.popPose()` | `GeoRenderer:130` | 收栈 |
| 15 | `postRenderPass(...)` | `GeoRenderer:132` | 用户钩子 |

**记住三个时间点的边界**：

- 第 9 步之前：可以加 `BoneUpdater`（改骨骼）；
- 第 10 步之前：**必须**加完 `BoneUpdater`，晚加不报错、只静默失效；
- 第 11 步之后：只剩提交，不能改骨骼。

## 4.3 `GeoRenderLayer` 的四个钩子

`GeoRenderLayer<T, O, R>` 是你写自定义渲染层的基类，四个可覆写点：

| 钩子 | 何时被调 | 能做什么 |
|---|---|---|
| `addRenderData(animatable, relatedObject, renderState, partialTick)` | 进 `performRenderPass` **之前** | 从 `animatable`/`relatedObject` 摘数据，塞进 `renderState`（因为后面拿不到它们了） |
| `preRender(renderPassInfo, renderTasks)` | 主流程第 9 步 | 加 `BoneUpdater`、读 renderState 数据 |
| `addPerBoneRender(renderPassInfo, consumer)` | 紧跟 `preRender`，同一循环 | 登记 `PerBoneRender` 回调 |
| `submitRenderTask(renderPassInfo, renderTasks)` | 主流程第 13 步 | 主模型与所有 PerBone 都提交完之后再做点事 |

**`renderState` 是唯一能从提取阶段带到渲染阶段的通道**，用 `DataTicket` 做 key：

```java
private static final DataTicket<List<ResolvedMount>> MOUNTS =
        DataTicket.create("minegenshin_bone_mounts", new TypeToken<>() {});
```

⚠️ `DataTicket.create` 按 `(类型, id)` **全局去重**：不同模组用同一个 id + 同一个类型会拿到同一个对象，数据互相顶掉。
id 不同或类型不同则互不干扰。

## 4.4 骨骼位姿：只有渲染那一瞬间才存在

这是本节的核心事实：

> **骨骼的位置不是一个可以随时查询的实体状态，它是「动画采样 + 层级变换累乘」的结果，只在渲染那一瞬间成立。**

GeckoLib 提供两条拿它的路：

**路 A：`PerBoneRender`（本项目用的）**

```java
info.addPerBoneRender(bone, (passInfo, b, tasks) -> {
    Matrix4f bonePose = new Matrix4f(passInfo.poseStack().last().pose());  // 相机相对
    Vector3f local = bonePose.getTranslation(new Vector3f());
    Quaternionf rot = bonePose.getUnnormalizedRotation(new Quaternionf());
    Vec3 camera = passInfo.cameraState().pos;
    Vec3 world = new Vec3(camera.x + local.x, camera.y + local.y, camera.z + local.z);
    // 存进缓存，给别的系统（比如 Photon）用
});
```

注意两点：

- 回调拿到的 PoseStack 停在**骨骼 pivot** 上（经过祖先链 `prepMatrixForBone` + `translateToPivotPoint`），
  **没有**最后那一步 `translateAwayFromPivotPoint`。要画骨骼自己的方块必须自己补回这一步。
- 它是「提交时捕获的位姿」，延迟绘制时必须 `poseStack.last().set(pose)` 还原。

**路 B：`RenderPassInfo#addBonePositionListener`**

```java
info.addBonePositionListener("long", (worldPos, modelPos, localPos) -> { ... });
```

回调三个参数的**精确含义**（来自 `RenderUtil.providePositionsToListeners` 的源码）：

| 参数 | 相对谁 | 单位 | 何时为 null |
|---|---|---|---|
| `localPos` | 本趟渲染的起点（`RenderPassInfo` 构造时捕获的 PoseStack） | 格 | 永不 |
| `modelPos` | 模型根（`captureModelRenderPose` 捕获），已换算模型单位 | 格（`(-16,+16,+16)` 换算） | 永不 |
| `worldPos` | `localPos` + `DataTickets.POSITION` 的平移 | 格 | **`POSITION` 没写入时恒为 null** |

⚠️ **本项目走 `GeoObjectRenderer`，从不写 `DataTickets.POSITION`，所以 `worldPos` 恒为 null。**
只有 `GeoEntityRenderer` / `GeoBlockRenderer` / `GeoArmorRenderer` / `GeoReplacedEntityRenderer` 会写它。
**结论：在本项目里想拿骨骼世界坐标，必须走路 A（自己加 `cameraState().pos`）。**

## 4.5 `BoneSnapshot` 与骨骼显隐

`BoneSnapshot` 是「这一趟渲染里某根骨骼的变换值」，字段有 scale XYZ / translate XYZ / rot XYZ、
`skipRender`、`skipChildrenRender`。它有两个必须知道的特性：

1. **生命周期只在一趟渲染内**：`renderPosed` 退出时会 `cleanup()` 把 `bone.frameSnapshot` 清成 null，
   下次 `renderPosed` 再 `apply()` 放回去。**在任何 `renderPosed` 之外缓存 `BoneSnapshot` 引用都是错的。**
2. **骨骼显隐是一个共享布尔**，没有「谁藏的」标记。所以两个渲染层都想藏同一根骨骼时，
   后跑的那层会把前一层藏的又画回来。正确做法是**在动手之前先读一次**当前状态，再决定要不要管。

## 4.6 写一个自定义渲染层（最小骨架）

```java
public final class ExampleGeoLayer<T extends GeoAnimatable, O, R extends GeoRenderState>
        extends GeoRenderLayer<T, O, R> {

    private static final DataTicket<List<String>> BONES =
            DataTicket.create("example_hidden_bones", new TypeToken<>() {});

    public ExampleGeoLayer(GeoRenderer<T, O, R> renderer) {
        super(renderer);
    }

    /** 阶段一：提取。此时还能拿到 animatable / relatedObject。 */
    @Override
    public void addRenderData(T animatable, @Nullable O relatedObject, R renderState, float partialTick) {
        if (!(relatedObject instanceof Player player)) return;
        List<String> bones = List.of("RightSword");
        if (!bones.isEmpty()) renderState.addGeckolibData(BONES, bones);
    }

    /** 阶段二：渲染早期。这里是加 BoneUpdater 的最后安全窗口。 */
    @Override
    public void preRender(RenderPassInfo<R> info, SubmitNodeCollector tasks) {
        List<String> bones = info.getGeckolibData(BONES);
        if (bones == null || bones.isEmpty()) return;
        info.addBoneUpdater((i, snapshots) -> {
            for (String name : bones) {
                snapshots.ifPresent(name, snapshot -> snapshot.skipRender(true));
            }
        });
    }

    /** 阶段三：登记每骨骼回调。 */
    @Override
    public void addPerBoneRender(RenderPassInfo<R> info, BiConsumer<GeoBone, PerBoneRender<R>> consumer) {
        List<String> bones = info.getGeckolibData(BONES);
        if (bones == null || bones.isEmpty()) return;
        for (String name : bones) {
            GeoBone bone = info.model().getBone(name).orElse(null);
            if (bone == null) continue;   // 骨骼不存在就整根别动，否则会「凭空少一块」
            consumer.accept(bone, (passInfo, b, tasks) -> submitAtBone(passInfo, tasks));
        }
    }

    private void submitAtBone(RenderPassInfo<R> info, SubmitNodeCollector tasks) {
        PoseStack poseStack = info.poseStack();
        tasks.submitCustomGeometry(poseStack, RenderTypes.entityCutout(someTexture), (pose, buffer) -> {
            poseStack.pushPose();
            poseStack.last().set(pose);
            // 想画骨骼自己的方块，补回 prepMatrixForBone 的最后一步：
            // bone.translateAwayFromPivotPoint(poseStack);
            // ... 写顶点 ...
            poseStack.popPose();
        });
    }
}
```

挂到渲染器上（必须在第一次渲染之前，层列表是懒编译的）：

```java
created.withRenderLayer(new BoneMountGeoLayer(created));
created.withRenderLayer(new TranslucentBoneGeoLayer(created));
```

## 4.7 GeckoLib 的坑（全部可在源码核对）

1. **`addBoneUpdater` 晚加只写一行 error 日志，不抛异常** —— 骨骼改动被无声丢弃。
2. **`getModelRenderMatrixState()` 在 `captureModelRenderPose()` 之前调用会抛 `IllegalStateException`**；
   而且它靠「矩阵还是单位阵」来判断「没 set」，所以捕获到单位阵时也会误判。
3. **`getPreRenderMatrixState()` 就是 `RenderPassInfo` 构造那一刻的 PoseStack**，
   在 `GeoObjectRenderer` 路径上它**已经包含**外层变换（本项目的 `scale(bodyScale)` + 两次 Y 旋转）。
4. **一个 pass 里 `BoneUpdater` 只 compute 一次**，两次 `renderPosed` 复用同一批快照对象。
5. **`PerBoneRender` 完全绕开 `skipRender`** —— 别人藏了骨骼对它无效，得自己判。
6. **`renderType == null` 不阻止动画与骨骼回调**，只是不画几何。
7. **`GeoRenderLayersContainer` 懒编译层列表**，`withRenderLayer` 必须在渲染开始前调完。

---
