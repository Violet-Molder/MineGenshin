# 5. GeckoLib 4：骨骼动画与渲染层


## 5.1 类地图（4.9.3）

| 用途 | 类 |
| --- | --- |
| 渲染器基类 | `software.bernie.geckolib.renderer.GeoRenderer` |
| 实体渲染器 | `...renderer.GeoEntityRenderer` |
| 摆件 / 通用对象 | `...renderer.GeoObjectRenderer` |
| 被替换实体 | `...renderer.GeoReplacedEntityRenderer` |
| 渲染层 | `...renderer.layer.GeoRenderLayer` |
| 模型 | `...model.GeoModel` / `DefaultedEntityGeoModel` 等 |
| 骨骼 | `...cache.object.GeoBone` / `BakedGeoModel` |
| 动画控制 | `...animation.AnimationController` / `AnimatableManager` / `AnimationState` / `RawAnimation` / `PlayState` |
| 骨骼快照 | `...animation.state.BoneSnapshot` |
| 缓存 | `...cache.GeckoLibCache` / `...cache.texture.AnimatableTexture` |
| 事件 | `...event.GeoRenderEvent` / `GeckoLibEventsNeoForge` |

## 5.2 一趟渲染

```text
GeoEntityRenderer#render(...)                    :197
 └─ GeoRenderer#defaultRender(...)              :126
     ├─ preRender(...)                          :232
     ├─ actuallyRender(...)                     :178   ← 摆位、算朝向（GeoEntityRenderer 覆写）
     ├─ renderRecursively(...)                  :260   ← 逐骨骼
     ├─ applyRenderLayers(...)                  :220   ← 逐层
     └─ postRender(...)                         :240
```

`GeoRenderEvent.Entity.Pre`（`:356`）可取消渲染；
`GeoRenderEvent.Entity.CompileRenderLayers`（`:442`）用来挂层 —— 项目里的武器挂点层从这里上去。

## 5.3 动画：`AnimatableManager` 与 `AnimationController`

```java
@Override
public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    controllers.add(new AnimationController<>(this, "main", 5, this::animState));
}

private PlayState animState(AnimationState<MyEntity> state) {
    return state.setAndContinue(RawAnimation.begin().thenLoop("idle"));
}
```

- `AnimatableManager.ControllerRegistrar#add(AnimationController...)`（`AnimatableManager.java:175`）；
- `AnimationController#setAnimation(RawAnimation)`（`:361`）；
- `AnimationController#getStateHandler()`（`:271`）是状态判断的入口；
- 手动触发一次性动画：`tryTriggerAnimation("名字")`（`:123` / `:136`）；
- **`PlayState` 只有 `CONTINUE` 与 `STOP`**：想「暂停」得用速度控制（§5.6）。

## 5.4 骨骼：只有渲染那一瞬间存在

`GeoBone`（`cache/object/GeoBone.java:19`）暴露的是**当下这一刻**的值：

| 方法 | 含义 |
| --- | --- |
| `getRotX/Y/Z` `:86`–`:94` | 弧度 |
| `getPosX/Y/Z` `:98`–`:106` | 模型单位 |
| `getScaleX/Y/Z` `:110`–`:118` | 缩放 |
| `getPivotX/Y/Z` `:226`–`:234` | 枢轴 |
| `isHidden` / `setHidden` `:194` / `:198` | 显隐 |

想在渲染之外读，用快照：

```java
BoneSnapshot snap = new BoneSnapshot(bone);   // animation/state/BoneSnapshot.java:35
snap.getRotX(); snap.getOffsetY();            // :97 / :89
```

`AnimatableManager#getBoneSnapshotCollection()`（`:62`）保存上一轮的快照，
`clearSnapshotCache()`（`:66`）清理。**不要**在 tick 里直接读 `GeoBone` —— 那是上一帧的残值。

## 5.5 写一个渲染层

```java
public final class MuzzleGlowLayer<T extends Entity & GeoAnimatable>
        extends GeoRenderLayer<T> {

    public MuzzleGlowLayer(GeoRenderer<T> renderer) { super(renderer); }

    @Override
    public void renderForBone(PoseStack poseStack, T animatable, GeoBone bone,
                              RenderType renderType, MultiBufferSource bufferSource,
                              VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        if (!"muzzle".equals(bone.getName())) return;
        poseStack.pushPose();
        poseStack.translate(0, 0.05, 0);
        // …… 写顶点
        poseStack.popPose();
    }
}
```

覆盖 `getRenderType`（`GeoRenderer.java:69`）可以换掉整层的 `RenderType`；
覆盖 `getTextureResource`（`GeoRenderLayer.java:53`）可以换层自己的贴图。

## 5.6 GeckoLib 4 的坑

| 坑 | 事实 | 对策 |
| --- | --- | --- |
| 没有 `PlayState.PAUSE` | `PlayState.java` 只有 `CONTINUE` / `STOP` | 冻结用 `controller.setAnimationSpeed(0)`；解冻再 `setAnimationSpeed(1)` + `forceAnimationReset()` |
| `GeoObjectRenderer` 偏半格 | 父类给方块原点模型加了 `translate(+0.5, +0.51, +0.5)` | 覆盖 `preRender` 反向平移（项目里 `CharacterRenderer` 的做法） |
| 动画不播 | 键名不匹配；或自定义 `getBakedModel` 绕过了骨骼登记 | 核对 `.animation.json` 键名与解析链 |
| 模型动但位置不对 | 骨骼枢轴与模型导出原点不一致 | 在模型文件里改，不要在运行时叠加补偿 |
| 4 与 5 的钩子名不同 | 5.x 的 `getBakedAnimation` 等命名在 4.x 不存在 | 移植时按 4.9.3 的源码逐个核 |

---
