# 4. GeckoLib 的渲染管线

1.21.1 线用 **GeckoLib 4.9.3**（26.2 是 5.5.6，API 差别很大，别照抄示例）。

## 4.1 选哪个渲染器

| 场景 | 渲染器 |
| --- | --- |
| 标准生物 | `GeoEntityRenderer<T extends Entity & GeoAnimatable>` |
| 摆件 / 自己摆位的对象（含出战角色） | `GeoObjectRenderer` |
| 替换玩家本体 | `GeoReplacedEntity` + `GeoReplacedEntityRenderer` |

注册走 `EntityRenderersEvent.RegisterRenderers`：

```java
event.registerEntityRenderer(ModEntities.MY_ENTITY.get(),
        ctx -> new GeoEntityRenderer<>(ctx, new MyGeoModel()));
```

## 4.2 一趟渲染顺序

```text
render → defaultRender → preRender → actuallyRender → renderRecursively → applyRenderLayers → postRender
```

- `preRender`：改骨骼状态（本项目在这里注入 `BoneUpdater`）；
- `renderRecursively`：逐骨骼写顶点；
- `applyRenderLayers`：跑所有挂上去的层；
- `GeoRenderEvent.*.CompileRenderLayers`：一次性挂层的事件。

## 4.3 动画控制器

```java
@Override
public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    controllers.add(new AnimationController<>(this, "main", 5, this::state));
}

private PlayState state(AnimationState<MyEntity> s) {
    return s.setAndContinue(RawAnimation.begin().thenLoop(s.isMoving() ? "walk" : "idle"));
}
```

一次性动画：`manager.tryTriggerAnimation("attack")`。
**`PlayState` 只有 `CONTINUE` / `STOP`**：想暂停用 `setAnimationSpeed(0)`，解冻 `setAnimationSpeed(1)` + `forceAnimationReset()`。

## 4.4 骨骼数据只有渲染那一刻是真的

- 渲染期：`GeoBone#getRotX/Y/Z`（弧度）、`getPosX/Y/Z`（模型单位）；
- 渲染之外：用 `BoneSnapshot`（`new BoneSnapshot(bone)`，`getRotX()` 等）；
- 项目惯例：渲染层里读 → 写进自己的缓存 → tick / 特效消费。

## 4.5 坑

| 坑 | 对策 |
| --- | --- |
| 模型偏半格 | `preRender` 里反向 `translate(-0.5F, -0.51F, -0.5F)` |
| 动画不播、日志干净 | 核对 `.animation.json` 的键名与模型解析链 |
| 换资源包不更新 | 别缓存烘焙模型，只缓存键 |
| 冻结不了动画 | 4.x 没有 `PAUSE`，用速度 0 |

深入：[完全参考 5. GeckoLib](/doc/rendering-1.21.1-reference-geckolib)。