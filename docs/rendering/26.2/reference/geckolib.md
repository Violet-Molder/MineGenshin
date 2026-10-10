# 5. GeckoLib 5：骨骼动画与渲染层


## 5.1 一趟渲染的调用顺序（这才是 5.5.6 的真实骨架）

入口是 `GeoRenderer#performRenderPass`（`com/geckolib/renderer/base/GeoRenderer.java:107-139`），顺序如下（每一步都写在同一段方法体里）：

```
performRenderPass(renderState, poseStack, renderTasks, cameraState, boneUpdaters)
 1. poseStack.pushPose()
 2. renderType = getRenderType(renderState, getTextureLocation(renderState))
 3. renderPassInfo = RenderPassInfo.create(this, renderState, poseStack, cameraState, renderType != null)
 4. 把你传入的 boneUpdaters 逐个 addBoneUpdater(...)
 5. firePreRenderEvent(...)             —— 事件能被取消，取消则整趟不画
 6. preRenderPass(renderPassInfo, renderTasks)          GeoRenderer.java:144
 7. scaleModelForRender(renderPassInfo, 1, 1)           GeoRenderer.java:149
 8. adjustRenderPose(renderPassInfo)                    GeoRenderer.java:160
 9. preApplyRenderLayers(renderPassInfo, renderTasks)   GeoRendererInternals.java:132
       ├─ layer.preRender(...)                     （隐藏骨骼、登记数据都在这里做）
       └─ layer.addPerBoneRender(...)              （把每骨骼任务登记进去）
10. renderPassInfo.captureModelRenderPose()             RenderPassInfo.java:322
11. submitRenderTasks(renderPassInfo, renderTasks, renderType)      GeoRenderer.java:172
       └─ renderTasks.submitCustomGeometry(poseStack, renderType, (pose, vc) -> {
              poseStack.last().set(pose);
              renderPassInfo.renderPosed(() -> model.render(renderPassInfo, vc, light, overlay, color));
          })
12. submitPerBoneRenderTasks(renderPassInfo, renderTasks)            GeoRendererInternals.java:161
13. applyRenderLayers(renderPassInfo, renderTasks)                   GeoRendererInternals.java:140
       └─ layer.submitRenderTask(...)              （想额外画东西的层在这里提交）
14. poseStack.popPose()
15. postRenderPass(renderPassInfo, renderTasks)                      GeoRenderer.java:200
```

两个容易被忽略的点：

- **第 3 步就把模型烘焙好了**：`RenderPassInfo.create` 里做 `renderer.getGeoModel().getBakedModel(...)`，并把两个内置 updater（`renderer::applyAnimationControllers`、`renderer::adjustModelBonesForRender`）挂上去（`RenderPassInfo.java:279-291`）。
- **第 11 步是"提交"而不是"立即画"**：`submitCustomGeometry` 的回调在真正的绘制阶段才执行，回调拿到的是提交时刻捕获的 pose，而模型渲染读的是 `RenderPassInfo` 里的 PoseStack（本项目 `BoneMountGeoLayer.submitSubModelAtBone` 的注释写清了这一点，`BoneMountGeoLayer.java:295-303`）。

数据准备在更早的 `fillRenderState`（`GeoRendererInternals.java:115-130`），它按顺序做：

```
captureDefaultRenderState()   → 填入 INSTANCE_ID / ANIMATABLE_MANAGER / PARTIAL_TICK / RENDER_COLOR /
                                PACKED_OVERLAY / IS_MOVING / ANIMATABLE_CLASS / TICK
addRenderData()               → 你自己的覆写（renderer 级别的数据）
getGeoModel().addAdditionalStateData()
每个 layer.addRenderData()    → 你自己的覆写（layer 级别的数据）
fireCompileRenderStateEvent()
setMolangQueryValues()
AnimationProcessor.extractControllerStates()   → 控制器状态存进 DataTickets.ANIMATION_CONTROLLER_STATES
```

即：**renderer 的 `addRenderData` 先于所有 layer 的 `addRenderData`**，layer 之间按注册顺序。`GeoObjectRenderer` 还额外把 `packedLight` 塞进 `DataTickets.PACKED_LIGHT`（`GeoObjectRenderer.java:70-76`）。

## 5.2 `GeoRenderLayer` 的四个钩子

`com/geckolib/renderer/layer/GeoRenderLayer.java:27-90`：

| 钩子 | 行 | 时机 | 用途 |
|---|---|---|---|
| `addRenderData(animatable, relatedObject, renderState, partialTick)` | 69 | 提取阶段，早于渲染 | 把这一帧要用的数据写进 renderState（用 DataTicket） |
| `preRender(renderPassInfo, renderTasks)` | 76 | 渲染趟开始、模型提交之前 | 改骨骼（`addBoneUpdater`）、预计算、一次性准备工作 |
| `submitRenderTask(renderPassInfo, renderTasks)` | 81 | 主模型与每骨骼任务之后 | 再画一层（换纹理、叠加特效） |
| `addPerBoneRender(renderPassInfo, consumer)` | 90 | `preApplyRenderLayers` 里被调用 | 把「一根骨骼 + 一个回调」登记进去，回调执行时 PoseStack 已经摆到那根骨骼的位姿 |

回调形态是 `PerBoneRender`（`renderer/base/PerBoneRender.java:14,20`）：

```java
@FunctionalInterface
public interface PerBoneRender<R extends GeoRenderState> {
    void submitRenderTask(RenderPassInfo<R> renderPassInfo, GeoBone bone, SubmitNodeCollector renderTasks);
}
```

注册方式（照抄本项目 `WeaponAnchorGeoLayer.java:70-93` 的写法）：

```java
@Override
public void addPerBoneRender(RenderPassInfo<R> info, BiConsumer<GeoBone, PerBoneRender<R>> consumer) {
    GeoBone bone = info.model().getBone("RightHand").orElse(null);
    if (bone == null) {
        return;                            // 骨骼不存在：什么都不做，不要抛异常
    }
    consumer.accept(bone, (pass, b, tasks) -> {
        Matrix4f pose = new Matrix4f(pass.poseStack().last().pose());   // 已含骨骼位姿
        // … 提交或记录
    });
}
```

执行点在 `GeoRendererInternals.submitPerBoneRenderTasks`（`:161-193`）：它先 `renderPosed(...)` 应用所有骨骼快照，把 PoseStack 重置到 `getModelRenderMatrixPose()`，再对每根骨骼 `RenderUtil.transformToBone(poseStack, bone)`，然后调用你的回调。

`preRender` 里改骨骼的正确姿势是 `addBoneUpdater`（`RenderPassInfo.java:177-185`）：

```java
renderPassInfo.addBoneUpdater((info, snapshots) ->
        snapshots.ifPresent("Head", snapshot -> snapshot.skipRender(true)));
```

注意注释的原话：*"Can only be called prior to the renderer submitting this pass for rendering. Updaters added after that point will be ignored"*，迟到的 updater 只会打一条 error 日志。原因是 updater 列表被 `DeferredCache` 在第一次 `renderPosed` 时锁定并消费（`RenderPassInfo.java:50, 293-306`）。

## 5.3 骨骼位姿的生命周期：快照、监听器、定位器

**骨骼没有"持久位置"。** 渲染趟里那根骨骼的矩阵，是「动画采样结果 + 层级变换累乘」的临时产物；GeckoLib 在 `addBonePositionListener` 的注释里把这件事写死了：*"Use this to capture bone matrix positions at the time of render, which is the only time they actually have a position of any kind"*（`RenderPassInfo.java:189-194`）。因此想在别处用骨骼位置，只有两条合法路径：

1. **每骨骼回调里就地取矩阵**：`addPerBoneRender` + `poseStack.last().pose()`（见上一节）。
2. **登记位置监听器**：`renderPassInfo.addBonePositionListener(boneName | GeoBone, listener)`（`RenderPassInfo.java:189-199`）或 `addLocatorPositionListener(locatorName, listener)`（`:203-210`），回调签名是
   `void accept(@Nullable Vec3 worldPos, @Nullable Vec3 modelPos, @Nullable Vec3 localPos)`（`RenderPassInfo.java:271-274`）。
   监听器在 `renderPosed` 里被装到骨骼/定位器上、在 `finally` 里清掉（`RenderPassInfo.java:213-256`），并由 `RenderUtil.prepMatrixForBoneAndUpdateListeners` 在执行时刷新（`com/geckolib/util/RenderUtil.java:90-103`）。

骨骼的可写数据走 `BoneSnapshot`（`com/geckolib/animation/state/BoneSnapshot.java`）：

- 读：`getScaleX/Y/Z`（46-58）、`getTranslateX/Y/Z`（61-73）、`getRotX/Y/Z`（76-86）、`isHidden()`（92）、`areChildrenHidden()`（97）；
- 写：`setScale/setTranslation/setRotation`（117/147/178）、`skipRender(boolean)`（213）、`skipChildrenRender(boolean)`（220）；
- 执行：`apply()`（244）把快照写回骨骼，`cleanup()`（249）用完复原。
- 取值入口是函数式接口 `BoneSnapshots`：`get(String)` 返回 `Optional<BoneSnapshot>`、`ifPresent(String, Consumer)`（`renderer/base/BoneSnapshots.java:13-25`）。

定位器 `GeoLocator` 是模型里声明的"空挂点"（`com/geckolib/cache/model/GeoLocator.java:12-27`，字段 `parent/name/offsetX.../rotX...`），监听器刷新在 `updatePositionListeners(PoseStack, RenderPassInfo)`（`:82`）。

## 5.4 层间传数据：`DataTicket`

GeckoLib 的 RenderState 是一个"类型安全的 Map"。票据用 `DataTicket.create(id, Class)` 或 `DataTicket.create(id, TypeToken)` 造（`com/geckolib/constant/dataticket/DataTicket.java:30,38`），去重靠 `(Type, id)`（`:15-16`），所以**必须自己建票**，不要复用别人的 id。

```java
private static final DataTicket<Player> OWNER =
        DataTicket.create("minegenshin_weapon_anchor_owner", Player.class);   // WeaponAnchorGeoLayer.java:47-49
```

读写都在 `GeoRenderState` 的三个 default 方法上（`renderer/base/GeoRenderState.java:29,43,111`）：`addGeckolibData` / `getGeckolibData` / `getOrDefaultGeckolibData`。内置票据在 `com/geckolib/constant/DataTickets.java:33-72`，常用的有：`ANIMATABLE_MANAGER`、`ANIMATABLE_INSTANCE_ID`、`PARTIAL_TICK`、`TICK`、`PACKED_LIGHT`、`PACKED_OVERLAY`、`RENDER_COLOR`、`POSITION`、`VELOCITY`、`IS_MOVING`、`ANIMATION_CONTROLLER_STATES`、`PER_SLOT_RENDER_DATA`。

对**实体**渲染，`EntityRenderState` 本身就被注入成了 `GeoRenderState`：GeckoLib 用 duck-typing mixin 给原版类补上 `getDataMap()`（`com/geckolib/mixin/client/EntityRenderStateMixin.java:17-52`），所以 `GeoEntityRenderer` 的 RenderState 直接就是原版的 `EntityRenderState` 子类，不需要另造一层。

## 5.5 本项目的真实接法

角色渲染不继承 `GeoEntityRenderer`，而是自己组装（`CharacterRenderDispatcher.java:330-352`）：

```java
CharacterPlayerModel model = ...;                          // 每个角色一份
CharacterRenderer renderer = new CharacterRenderer(model);
renderer.withRenderLayer(new BoneMountGeoLayer(renderer));          // 骨骼挂点：把武器画到骨骼上
renderer.withRenderLayer(new TranslucentBoneGeoLayer(renderer));    // 半透明骨骼
renderer.withRenderLayer(new WeaponAnchorGeoLayer(renderer));       // 只读位姿，喂给 Photon
```

每帧的调用（`:319`）：

```java
target.renderer().performRenderPass(target.animatable(), player, poseStack, bufferSource,
                                    cameraState, 15728880, partialTick, submitUpdater);
```

这走的是 `GeoObjectRenderer.performRenderPass` 的 8 参重载（`GeoObjectRenderer.java:65-76`）：它先 `fillRenderState(animatable, relatedObject, createRenderState(animatable, null), partialTick)`，把 `packedLight` 写进 `DataTickets.PACKED_LIGHT`，再把可选的 `BoneUpdater` 转成单元素 list 交给 `GeoRenderer.super.performRenderPass`。

`submitUpdater` 是"多个 updater 串起来"的组合体（`CharacterRenderDispatcher.combine`，`:323-334`）：外观、道具、面部、傀儡、武器的五组 updater 依次跑；调试开关打开时再包一层探针。

骨骼数据还有三个不走渲染层的来源：

| 类 | 职责 | 关键点 |
|---|---|---|
| `render/geo/AssetGeoCache.java:34-63` | 自己实现的资源包缓存：把 `item` / `block` / `entity` 三类资源里的 `.geo.json` 烘焙成 `BakedGeoModel`、动画烘焙成 `BakedAnimations`，按 `Identifier` 查 | 它实现 `PreparableReloadListener`，资源重载时整体替换 `volatile` 字段；渲染层只调用 `model(...)` / `animation(...)`，不要自己去读文件 |
| `CharacterAppearanceOptionBones.updaterFor(player, character)` | 外观可选项（武器/部件的显隐、换装）的 `BoneUpdater`：按外观掩码 + 当前武器类型 + 动画状态算出骨骼名单 | 飞行时换成"放飞骨骼"上那一份（长柄飞行是骑着武器当扫帚，武器不能再被手臂动画拖着走）；`fly_start` 前摇单独一套显隐，否则会出现"手里还亮着一把、身下又冒出一把" |
| `CharacterPropBones` | 道具骨骼（`MFly` / `FJO` / `tea` / `ysmGlow_texiao*`）的显隐判定 | `SCREEN_FORWARD_PUSH = -8.0f` 这类数字的单位是"模型像素"（1 格 = 16），换算成方块要除以 16 |

角色模型的 `adjustRenderPose` 被**故意清空**（`CharacterRenderer.java:41-44`），理由是 `GeoObjectRenderer` 的基类实现带 `translate(0.5, 0.51, 0.5)`——那是给以方块角为原点的摆件模型准备的补偿，而角色模型以原点为中心、实体提交给它的 pose 本身就没有半格偏移（`GeoObjectRenderer.java:52-55` 对比 `EntityRenderDispatcher.submit`）。

三条渲染层各自的分工：

| 层 | 关键实现 | 说明 |
|---|---|---|
| `BoneMountGeoLayer` | `addRenderData` 解析挂点（`:137-181`）→ `preRender` 里 `snapshot.skipRender(true)` 隐藏原骨骼（`:230-243`）→ `addPerBoneRender` 登记（`:248-274`）→ `submitAtBone` 画内容（`:276-331`） | 画不出来的情况一律"不隐藏、不挂"，避免角色凭空少一块（类注释 `:60-88`） |
| `TranslucentBoneGeoLayer` | 同上，改半透明 RenderType | 与挂点层同一套骨架 |
| `WeaponAnchorGeoLayer` | 只在 `addPerBoneRender` 里读矩阵，不提交任何几何（`:70-111`） | 结果写进 `WeaponAnchorCache`，供 Photon 的帧回调使用 |

`WeaponAnchorGeoLayer` 的完整数据流（这是"骨骼位姿怎么跨系统传递"的标准范例）：

```
提取阶段：addRenderData()        → renderState.addGeckolibData(OWNER, player)      :61-67
渲染趟中：addPerBoneRender()     → 只对「第一个挂点骨骼」登记回调                    :70-93
回调执行：capture(player, info)  → Matrix4f bonePose = new Matrix4f(poseStack.last().pose())
                                   local  = bonePose.getTranslation(new Vector3f())
                                   world  = cameraState.pos + local                :96-110
                                   WeaponAnchorCache.put(player, world, rotation)
读取侧：  WeaponAnchorCache.fresh(player)  → 只在 3 个渲染帧内有效，否则返回 null   WeaponAnchorCache.java:33,58-64
```

缓存用"渲染帧号"做新鲜度，帧号由 `RenderFrameEvent.Pre` 每帧自增（`CharacterRenderDispatcher.java:113-115`）。为什么不用实体刻：角色在第一人称、离屏或被其他模组挡住时根本不会被渲染，此时缓存自然过期，调用方保持上一帧位姿而不是把特效瞬移到原点（`WeaponAnchorCache.java:20-31` 的注释）。

另外，几何提交被统一收口到 `GeoRenderIntercept.trySubmit`（`optimize/GeoRenderIntercept.java:117`），角色、本模组实体、其它模组的 GeckoLib 实体走同一份代码；`CharacterRenderer.submitRenderTasks` 只是把默认实现显式留了一份兜底（`CharacterRenderer.java:63-68`）。

## 5.6 从零写一个自定义 `GeoRenderLayer`：最小可抄骨架

```java
public final class MuzzleAnchorLayer<T extends GeoAnimatable, O, R extends GeoRenderState>
        extends GeoRenderLayer<T, O, R> {

    /** 自己的票据：id 全局唯一，类型写具体类，别用 Object。 */
    private static final DataTicket<Player> OWNER =
            DataTicket.create("example_muzzle_owner", Player.class);

    public MuzzleAnchorLayer(GeoRenderer<T, O, R> renderer) {
        super(renderer);
    }

    /** ① 提取阶段：把这一帧要用的东西摘出来。 */
    @Override
    public void addRenderData(T animatable, @Nullable O relatedObject, R renderState, float partialTick) {
        if (relatedObject instanceof Player player) {
            renderState.addGeckolibData(OWNER, player);
        }
    }

    /** ② 渲染趟内：登记「某根骨骼被摆好那一刻」的回调。 */
    @Override
    public void addPerBoneRender(RenderPassInfo<R> info,
                                 BiConsumer<GeoBone, PerBoneRender<R>> consumer) {
        Player player = info.getGeckolibData(OWNER);
        if (player == null) {
            return;
        }
        info.model().getBone("Muzzle").ifPresent(bone -> consumer.accept(bone, (pass, b, tasks) -> {
            Matrix4f pose = new Matrix4f(pass.poseStack().last().pose());
            Vector3f local = pose.getTranslation(new Vector3f());
            Vec3 world = new Vec3(pass.cameraState().pos.x + local.x,
                                  pass.cameraState().pos.y + local.y,
                                  pass.cameraState().pos.z + local.z);
            // ③ 在这里做你要做的事：缓存位姿、喂给粒子系统、或者用 tasks 提交几何。
        }));
    }
}
```

挂到渲染器上：`renderer.withRenderLayer(new MuzzleAnchorLayer<>(renderer))`（`GeoObjectRenderer.java:105-118`）；渲染器由 GeckoLib 创建时也可以走一次性事件 `CompileEntityRenderLayersEvent`（`com/geckolib/event/entity/CompileEntityRenderLayersEvent.java:19`）——它只在渲染器第一次初始化时触发一次，`GeoRenderLayersContainer.getRenderLayers()` 里懒触发（`renderer/layer/GeoRenderLayersContainer.java:24-40`）。

三个硬约束：

1. **不要缓存 `GeoBone` / `RenderPassInfo` / `PoseStack`**：它们只在渲染趟内有效（骨骼对象是共享的烘焙模型的一部分，快照才是每趟的）。
2. **不要在自己的层里 `poseStack.pushPose()` 后不 pop**：整趟的 push/pop 由 `performRenderPass` 管（`GeoRenderer.java:108,137`），层里属于"借用"，配平即可。
3. **渲染只读，数据在提取阶段备好**：`adjustModelBonesForRender` 的注释明确写着 *"No manipulation of the RenderState is permitted here"*（`GeoRenderer.java:163-165`）。

## 5.7 案例：把「手上那把武器」交给 Photon（完整走一遍）

这是本项目真实在跑的接线（`client/render/character/WeaponAnchorGeoLayer.java` +
`WeaponAnchorCache.java` + `client/fx/FxAnchor.java`），也是「骨骼位姿怎么跨系统传递」的标准答案。

需求：技能起手时要在**武器尖端**放一圈特效。难点在于——武器尖端不是一个实体状态，
它只在骨骼被摆好的那一瞬间才存在。所以这一层要做的事只有一件：**把那个瞬间的位姿抄下来**，
它自己不画任何东西。

### 数据怎么过夜：先想清楚读者是谁

| 问题 | 答案 | 结果 |
|---|---|---|
| 谁写 | 模型的提交阶段（渲染线程） | 普通 `HashMap` 即可，不需要锁 |
| 谁读 | Photon 的每帧回调（同样是渲染线程） | 不需要 `volatile`、不需要并发容器 |
| 什么时候失效 | 角色没被渲染的那一帧就不该更新 | 新鲜度用**渲染帧号**，不用实体刻 |

最后一行是这套设计的重点。用实体刻做时间戳会有一个很难查的 bug：角色切到第一人称、
走到屏幕外、或者被别的模组挡住时**根本不会被渲染**，可实体刻照常在走，于是缓存"看着很新"，
特效就被钉在一个几秒前的位置上。用渲染帧号就自然多了——没渲染，帧号不涨，缓存过期，
调用方保持上一帧的位姿，比把特效瞬移到原点体面得多。

### 第一步：票据

```java
/** 把「本趟渲染属于哪个玩家」从提取阶段带到渲染阶段的票据。 */
private static final DataTicket<Player> OWNER =
        DataTicket.create("minegenshin_weapon_anchor_owner", Player.class);
```

票据的 id 自己起，类型写具体类。GeckoLib 的去重键是 `(Type, id)`（`DataTicket.java:15-16`），
所以"id 一样但类型不同"也是两张不同的票，不要靠 id 省事。

### 第二步：提取阶段只做一件事——记住这是谁的渲染

```java
@Override
public void addRenderData(T animatable, @Nullable O relatedObject, R renderState, float partialTick) {
    if (relatedObject instanceof Player player) {
        renderState.addGeckolibData(OWNER, player);
    }
}
```

这里传进来的 `relatedObject` 是渲染器绑定的那个世界对象（对角色渲染管线来说就是玩家）。
**渲染趟里拿不到它**，所以必须现在塞进 State。

### 第三步：登记「这根骨骼被摆好那一刻」的回调

```java
@Override
public void addPerBoneRender(RenderPassInfo<R> info,
                             BiConsumer<GeoBone, PerBoneRender<R>> consumer) {
    Player player = info.getGeckolibData(OWNER);
    if (player == null) {
        return;
    }
    String boneName = weaponBone(player);          // 角色声明的第一个挂点骨骼 = 手上的武器
    if (boneName == null) {
        return;
    }
    GeoBone bone = info.model().getBone(boneName).orElse(null);
    if (bone == null) {
        return;                                     // 模型里没这根骨骼：静默跳过，别抛异常
    }
    consumer.accept(bone, (pass, ignoredBone, tasks) -> capture(player, pass));
}
```

三层 `return` 是刻意的：没声明挂点的角色、模型里没有这根骨骼的角色、不是玩家在渲染的场合，
都直接不做事。渲染层是**全局**挂上去的，对全项目所有走这条管线的角色都会跑，所以"什么都不做"
必须是它的默认行为。

### 第四步：取位姿——注意坐标系

```java
private static void capture(Player player, RenderPassInfo<?> info) {
    Matrix4f bonePose = new Matrix4f(info.poseStack().last().pose());

    Vector3f local = bonePose.getTranslation(new Vector3f());
    Quaternionf rotation = bonePose.getUnnormalizedRotation(new Quaternionf());

    Vec3 camera = info.cameraState().pos;
    Vec3 world = new Vec3(camera.x + local.x, camera.y + local.y, camera.z + local.z);

    if (world.distanceToSqr(player.position()) > SANITY_DISTANCE * SANITY_DISTANCE) {
        return;                                     // 坐标系假设被打破：宁可这一帧不更新
    }
    WeaponAnchorCache.put(player,
            new Vector3f((float) world.x, (float) world.y, (float) world.z), rotation);
}
```

关键就是那句 `world = camera + local`：提交阶段 `PoseStack` 的原点是**相机**（§6.2 有完整推导），
所以从矩阵里 `getTranslation` 出来的是相机相对坐标，必须加上 `cameraState().pos` 才是世界坐标。

`SANITY_DISTANCE = 8.0` 那道检查看着多余，其实是防御性的：它假设"武器骨骼离玩家不会超过 8 格"。
一旦将来的渲染管线改了坐标系（比如某个版本改成以世界原点为基准），结果就会离谱地远，
这时整帧丢弃，特效停在上一帧，比飞到天边容易定位得多。

### 第五步：读取侧怎么用

```java
// 渲染层写入
WeaponAnchorCache.put(player, worldPos, worldRot);

// Photon 每帧回调读取（client/fx/FxAnchor.java）
WeaponAnchorCache.Entry entry = WeaponAnchorCache.fresh(player);
if (entry == null) {
    return;                      // 这一帧没渲染过：保持上一次的位姿，不要重置
}
AnchorPose pose = entry.toPose();
```

`fresh(...)` 的判定是 `renderFrame() - entry.frame() > 3` 就返回 `null`（`WeaponAnchorCache.java:33,58-64`），
留 3 帧余量是为了不让帧序抖动引起闪一下。缓存超过 64 条时会顺手清掉过期项，避免长时间挂机后越攒越多。

### 第六步：挂到渲染器上，然后自检

```java
renderer.withRenderLayer(new WeaponAnchorGeoLayer<>(renderer));
```

`withRenderLayer` 出自 GeckoLib 基类（`GeoObjectRenderer.java:105-118`）；走 GeckoLib 自己的
渲染器创建流程时，也可以用一次性事件 `CompileEntityRenderLayersEvent`（`CompileEntityRenderLayersEvent.java:19`），
它只在渲染器第一次初始化时触发。

自检清单：

1. **有没有提交几何？** 这一层一个顶点都不该提交。只读层的成本接近 0，一旦开始画东西，
   它的成本就变成"每个角色每帧多画一遍模型"。
2. **有没有缓存 `GeoBone` / `RenderPassInfo` / `PoseStack`？** 它们只在渲染趟内有效
   （骨骼对象是共享的烘焙模型的一部分）。要留就留**算出来的数值**。
3. **失败路径是不是静默的？** 没挂点、缺骨骼、离得太远——三种情况都应该悄悄跳过，
   让特效停在原地，而不是抛异常或瞬移。
4. **读的地方是不是也判了空？** 写入和读取是两条独立的路径，读侧必须自己处理 `null`。

> 另一种写法：`RenderPassInfo#addBonePositionListener(String boneName, BonePositionListener listener)`
> （`RenderPassInfo.java:188-196`）是 GeckoLib 直接提供的同款入口，注释原话是
> *"the only time they actually have a position of any kind"*。本项目没用它，而是自己走
> `addPerBoneRender` + 缓存，原因是**读侧（Photon 回调）需要一个"最近 3 帧内有效"的窗口**，
> 而监听器只在渲染那一刻回调一次，没有地方挂这个时间戳。两套都能用，按读侧的形态选。
