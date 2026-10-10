# 4. GeckoLib 4 的渲染管线（1.21.1 线用 4.9.3）


## 4.1 本项目走的是哪条路

角色与生物的模型都走 GeckoLib，但渲染器不是 `GeoEntityRenderer` 就是 `GeoObjectRenderer`：

| 场景 | 用的渲染器 | 例子 |
| --- | --- | --- |
| 标准生物 | `GeoEntityRenderer<T extends Entity & GeoAnimatable>` | `LargeCryoSlime` |
| 玩家 / 摆件（自己摆位） | `GeoObjectRenderer` + 项目自己的 `CharacterRenderer` | 出战角色 |
| 被替换的实体（玩家本体） | `GeoReplacedEntity` 体系 | `GenshinReplacedPlayer` |

注册走 NeoForge 事件（本项目 `MinegenshinClient#registerEntityRenderers`）：

```java
event.registerEntityRenderer(ModEntities.LARGE_CRYO_SLIME.get(),
        context -> new GeoEntityRenderer<>(context,
                new CategoryGeoModel<LargeCryoSlime>(AssetCategory.ENTITY, "large_cryo_slime")));
```

**注意**：本项目 1.21.1 的 `client/render/optimize/GeoRenderIntercept` 目前是空实现
（`trySubmit` 恒返回 `false`），也就是说 26.2 那套「几何提交接管 + GPU 蒙皮」在 1.21.1 上还没接，
1.21.1 走的是 GeckoLib 默认渲染路径。文档里凡是 26.2 描述的 `submitRenderTasks` 接管，
在 1.21.1 上都不成立。

## 4.2 一趟渲染的完整调用顺序

以 `GeoEntityRenderer` 为例（`software/bernie/geckolib/renderer/GeoEntityRenderer.java`）：

```text
EntityRenderDispatcher
  └─ GeoEntityRenderer#render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight)   :197
       └─ GeoRenderer#defaultRender(...)                                                              :126
            ├─ preRender(...)                                                                         :232
            ├─ actuallyRender(...)        ← 摆模型、算朝向                                               :178
            ├─ renderRecursively(...)     ← 逐骨骼遍历、写顶点                                          :260
            ├─ applyRenderLayers(...)     ← 跑所有 GeoRenderLayer                                        :220
            └─ postRender(...)                                                                        :240
```

每一层都可能被 `GeoRenderEvent` 拦住：`GeoRenderEvent.Entity.Pre` 可以取消渲染，
`GeoRenderEvent.Entity.CompileRenderLayers` 可以往渲染器上挂新层
（`software/bernie/geckolib/event/GeoRenderEvent.java:356`、`:442`）。

## 4.3 `GeoRenderLayer` 的四个钩子

`GeoRenderLayer`（`software/bernie/geckolib/renderer/layer/GeoRenderLayer.java`）：

| 方法 | 时机 | 典型用途 |
| --- | --- | --- |
| `preRender(...)` `:62` | 本体几何提交前 | 改骨骼状态、准备要挂的东西 |
| `render(...)` `:74` | 本体几何提交后 | 画挂件、画辉光 |
| `renderForBone(...)` `:90` | 每根骨骼 | 只在某根骨骼上画东西（武器、光环） |
| `getDefaultBakedModel(...)` `:38` | 需要模型时 | 自定义层自己的模型 |

## 4.4 骨骼位姿：只有渲染那一瞬间才存在

GeckoLib 4 的骨骼状态是**每帧重算**的：

1. `AnimationProcessor` 把动画采样结果写进 `GeoBone`（旋转 / 位移 / 缩放，弧度）；
2. `renderRecursively` 逐骨骼累乘变换，把顶点写进 `VertexConsumer`；
3. 这一趟结束，骨骼上留下的只有「最后一次算出来的值」。

想在渲染之外拿到骨骼位置，GeckoLib 4 提供的是**快照**：

```java
BoneSnapshot snapshot = new BoneSnapshot(bone);      // animation/state/BoneSnapshot.java:35
float rotX = snapshot.getRotX();                      // :97
float offY = snapshot.getOffsetY();                   // :89
```

`AnimatableManager#getBoneSnapshotCollection()`（`animation/AnimatableManager.java:62`）保存了上一次
动画更新时抓的快照，`clearSnapshotCache()`（`:66`）负责清。项目里的做法是在渲染层的
`preRender` 里读骨骼、把结果存进缓存，渲染外再消费 —— 不要在 tick 里直接读骨骼。

## 4.5 写一个自定义渲染层（最小骨架）

```java
public class MuzzleAnchorLayer<T extends Entity & GeoAnimatable>
        extends GeoRenderLayer<T> {

    public MuzzleAnchorLayer(GeoRenderer<T> renderer) {
        super(renderer);
    }

    @Override
    public void renderForBone(PoseStack poseStack, T animatable, GeoBone bone,
                              RenderType renderType, MultiBufferSource bufferSource,
                              VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        if (!"muzzle".equals(bone.getName())) return;
        poseStack.pushPose();
        // 此时 poseStack 已经在「这根骨骼对齐后的坐标系」里
        poseStack.translate(0, 0.1, 0);
        // …… 写顶点
        poseStack.popPose();
    }
}
```

挂上去有两条路：一次性事件（推荐，见 §4.2 的 `CompileRenderLayers`），
或者给渲染器加 `renderer.addRenderLayer(...)`。

## 4.6 GeckoLib 4 的坑（全部可在源码核对）

| 坑 | 事实 | 应对 |
| --- | --- | --- |
| `PlayState` 只有两个值 | `animation/PlayState.java`：只有 `CONTINUE` / `STOP` | 想「暂停」用 `AnimationController#setAnimationSpeed(0)`，解冻时 `setAnimationSpeed(1)` + `forceAnimationReset()` |
| `GeoObjectRenderer` 位置偏半格 | 父类默认 `translate(+0.5, +0.51, +0.5)`，那是给方块原点导出的摆件用的 | 覆盖 `preRender` 里反向 `translate(-0.5F, -0.51F, -0.5F)`（本项目 `CharacterRenderer` 就是这么做的） |
| 动画不播、日志无报错 | 动画键名与 `.animation.json` 里的名字不一致，或 `getBakedModel` 绕过骨骼登记 | 先核对键名，再确认模型供给链（`AssetGeoCache → GenshinGeoCache → GeckoLib 缓存`） |
| 模型正常但不动 | 自定义渲染器覆写了 `render` 却没调 `defaultRender` | 让 `render` 只做入口，几何走父类 |
| 定位器 / 骨骼名写错 | GeckoLib 4 定位器是模型里声明的空挂点，名字必须逐字对上 | 用编辑器核对，不要凭记忆写 |

## 4.7 GeckoLib 4 与 GeckoLib 5 的对照（移植时最容易踩的一栏）

| 概念 | 1.21.1（4.9.3） | 26.2（5.5.6） |
| --- | --- | --- |
| 渲染器基类 | `software.bernie.geckolib.renderer.GeoRenderer` | `com.geckolib.renderer.base.GeoRenderer` |
| 渲染状态 | 没有 RenderState，直接吃实体对象 | `GeoRenderState` + `DataTicket` |
| 骨骼快照 | `animation/state/BoneSnapshot` | `animation/state/BoneSnapshot`（同名前缀，包名改了） |
| 动画暂停 | `setAnimationSpeed(0)` | `PlayState.PAUSE` 存在 |
| 动画钩子名 | `getBakedAnimation` 之类不存在 | 命名整体换了，别照搬 5.x 的钩子 |

---
