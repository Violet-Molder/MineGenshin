# 5. GeckoLib：骨骼动画与渲染层

1.21.1 线用的是 **GeckoLib 4.9.3**（26.2 线是 GeckoLib 5.5.6，API 差别很大，不能照抄）。
这一章讲这一类库的核心概念、渲染一趟的完整顺序、动画怎么驱动、骨骼数据什么时候能读、
自定义渲染层怎么写，以及 4.x 特有的坑。

## 5.1 概念：模型 / 骨骼 / 动画 / 控制器

| 概念 | 对应类 | 说明 |
| --- | --- | --- |
| 模型（几何） | `GeoModel` → `BakedGeoModel` | 从 `.geo.json` 烘焙出来的骨骼树 + 立方体 |
| 骨骼 | `GeoBone` | 有父子关系、枢轴、旋转/位移/缩放，运行时值**只有渲染那一刻存在** |
| 动画 | `Animation` / `RawAnimation` | 从 `.animation.json` 读，按名字索引 |
| 控制器 | `AnimationController` | 决定「现在播哪条动画」的状态机 |
| 动画管理器 | `AnimatableManager` | 一个动画对象上的全部控制器 |
| 渲染器 | `GeoRenderer` / `GeoEntityRenderer` / `GeoObjectRenderer` | 把上面三者画出来 |

「可动画对象」是 `GeoAnimatable`；实体要额外实现 `GeoEntity`（提供 tick 与实例缓存），
被替换的玩家走 `GeoReplacedEntity` + `GeoReplacedEntityRenderer` 这一路。

## 5.2 一趟渲染的调用顺序

以 `GeoEntityRenderer` 为例（`software/bernie/geckolib/renderer/GeoEntityRenderer.java`）：

```text
GeoEntityRenderer#render(entity, yaw, partialTick, poseStack, buffers, light)   :197
 └─ GeoRenderer#defaultRender(...)                                             :126
     ├─ preRender(...)                                                         :232
     ├─ actuallyRender(...)     ← 摆位、算朝向（GeoEntityRenderer 覆写）        :178
     ├─ renderRecursively(...)  ← 逐骨骼遍历、写顶点                            :260
     ├─ applyRenderLayers(...)  ← 跑挂在渲染器上的所有层                        :220
     └─ postRender(...)                                                        :240
```

`GeoRenderEvent` 给了三个可以介入的点：

| 事件 | 作用 |
| --- | --- |
| `GeoRenderEvent.*.Pre`（`:356`） | 渲染前，可取消 |
| `GeoRenderEvent.*.CompileRenderLayers`（`:442`） | 一次性给渲染器挂层 |
| `GeoRenderEvent.*.Post` | 渲染后 |

## 5.3 动画：控制器与状态

```java
@Override
public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    controllers.add(new AnimationController<>(this, "main", 5, this::mainState));
}

private PlayState mainState(AnimationState<MyEntity> state) {
    if (state.isMoving()) return state.setAndContinue(RawAnimation.begin().thenLoop("walk"));
    return state.setAndContinue(RawAnimation.begin().thenLoop("idle"));
}
```

| 方法 | 位置 | 说明 |
| --- | --- | --- |
| `ControllerRegistrar#add(...)` | `AnimatableManager.java:175` | 注册控制器；名字唯一 |
| `AnimationController#setAnimation(...)` | `:361` | 立即切换动画 |
| `AnimationController#getStateHandler()` | `:271` | 状态函数入口 |
| `AnimationState#setAndContinue(...)` | — | 只在本控制器状态变化时切换（推荐） |
| `AnimationState#isMoving()` / `getLimbSwing()` | — | 行走判断，取自实体移动 |
| `AnimatableManager#tryTriggerAnimation(String)` | `:123` | 触发一次性动画（攻击、受击） |
| `AnimationController#setAnimationSpeed(float)` | — | 速度倍率；**0 = 冻结** |

`RawAnimation` 的几种收尾：`thenLoop`（循环）、`thenPlay`（播一次）、`thenWait(刻)`（停若干刻）。
过渡用 `setTransitionLength(刻)`；默认 5 刻。

## 5.4 骨骼数据：只有渲染那一刻存在

GeckoLib 4 的骨骼值是 `GeoBone` 上的可变字段，每帧由动画求值写一遍
（`cache/object/GeoBone.java`）：

| 方法 | 含义 | 单位 |
| --- | --- | --- |
| `getRotX/Y/Z` `:86`–`:94` | 旋转 | **弧度** |
| `getPosX/Y/Z` `:98`–`:106` | 位移 | 模型单位（1/16 方块） |
| `getScaleX/Y/Z` `:110`–`:118` | 缩放 | 倍率 |
| `getPivotX/Y/Z` `:226`–`:234` | 枢轴 | 模型单位 |
| `isHidden` / `setHidden` `:194` / `:198` | 显隐 | — |

**渲染之外要读骨骼，用快照**：

```java
BoneSnapshot snap = new BoneSnapshot(bone);   // animation/state/BoneSnapshot.java:35
snap.getRotX();                                // :97 弧度
snap.getOffsetY();                             // :89 模型单位
```

`AnimatableManager#getBoneSnapshotCollection()`（`:62`）保存上一轮的快照，`clearSnapshotCache()`（`:66`）清理。
项目里的惯例是：在渲染层的 `preRender` 里读骨骼 → 存进自己的缓存 → 渲染之外（tick / 特效）消费。
**不要**在 tick 里直接读 `GeoBone`，那是上一帧的残值。

## 5.5 自定义渲染层

```java
public final class MuzzleGlowLayer<T extends Entity & GeoAnimatable> extends GeoRenderLayer<T> {

    public MuzzleGlowLayer(GeoRenderer<T> renderer) { super(renderer); }

    @Override
    public void renderForBone(PoseStack poseStack, T animatable, GeoBone bone, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer,
                              float partialTick, int packedLight, int packedOverlay) {
        if (!"muzzle".equals(bone.getName())) return;      // 只在这一根骨骼上画
        VertexConsumer vc = bufferSource.getBuffer(MY_GLOW); // 进到骨骼坐标系时 poseStack 已对齐
        poseStack.pushPose();
        poseStack.translate(0f, 0.05f, 0f);                  // 模型单位：0.05 格
        // …… 写顶点
        poseStack.popPose();
    }
}
```

| 钩子 | 位置 | 时机 |
| --- | --- | --- |
| `preRender(...)` | `GeoRenderLayer.java:62` | 本体几何提交前（改骨骼状态放这） |
| `render(...)` | `:74` | 本体几何提交后（画挂件放这） |
| `renderForBone(...)` | `:90` | 每根骨骼都会调一次 |
| `getDefaultBakedModel(...)` | `:38` | 层自己的模型 |

挂层：`renderer.addRenderLayer(new MuzzleGlowLayer<>(renderer))`，或监听一次性的
`GeoRenderEvent.*.CompileRenderLayers`。

## 5.6 4.x 特有的坑

| 坑 | 事实 | 对策 |
| --- | --- | --- |
| 没有 `PlayState.PAUSE` | `animation/PlayState.java` 只有 `CONTINUE` / `STOP` | 冻结用 `setAnimationSpeed(0)`，解冻 `setAnimationSpeed(1)` + `forceAnimationReset()` |
| 摆件原点偏半格 | `GeoObjectRenderer` 默认给方块原点模型加了 `translate(+0.5, +0.51, +0.5)` | 在 `preRender` 里反向平移（本项目 `CharacterRenderer` 就是这么做的） |
| 动画不播、日志无报错 | 动画键名与 `.animation.json` 不一致；或自定义 `getBakedModel` 绕过了骨骼登记 | 先核对键名，再确认资源解析链 |
| 换资源包后模型不更新 | 自己缓存了 `BakedGeoModel` | 只缓存键，不缓存烘焙结果；重载走 GeckoLib 缓存 |
| 定位器/骨骼名写错 | 名字必须逐字一致 | 用 Blockbench 核对，别凭记忆写 |

## 5.7 GeckoLib 4 与 5 的对照

| 概念 | 1.21.1（4.9.3） | 26.2（5.5.6） |
| --- | --- | --- |
| 包名 | `software.bernie.geckolib.*` | `com.geckolib.*` |
| 渲染状态 | 没有，直接吃实体对象 | `GeoRenderState` + `DataTicket` |
| 渲染器基类 | `renderer.GeoRenderer` | `renderer.base.GeoRenderer` |
| 动画暂停 | `setAnimationSpeed(0)` | `PlayState.PAUSE` |
| 骨骼快照 | `animation.state.BoneSnapshot` | 同名但包名变了 |
| 层 | `renderer.layer.GeoRenderLayer` | `renderer.layer.GeoRenderLayer`（签名不同） |

移植时的正确姿势：**按 4.9.3 的源码逐个核对方法签名**，不要照抄 5.x 的示例。

下一章：[6. 坐标空间、矩阵与四元数](/doc/rendering-1.21.1-reference-transform)。