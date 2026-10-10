# 4. 实体渲染与渲染状态

1.21.1 没有 `RenderState` 体系（那是 1.21.4+ 的事），实体渲染是「直接吃实体对象」的。
这一章讲清楚：实体渲染器拿到什么、数据从哪来、注册在哪、以及**没地方放数据时该怎么办**。

## 4.1 `EntityRenderer` 的契约

`net/minecraft/client/renderer/entity/EntityRenderer.java`：

```java
public abstract class EntityRenderer<T extends Entity> {                                  // :26
    public boolean shouldRender(T entity, Frustum frustum, double x, double y, double z)  // :52 剔除
    public void render(T entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) // :89 真正画
    protected void renderNameTag(...)                                                     // :185 名牌
    public ResourceLocation getTextureLocation(T entity)                                   // 贴图
}
```

`render` 的五个参数就是你能用的全部信息：

| 参数 | 含义 | 怎么用 |
| --- | --- | --- |
| `entity` | 实体对象本体 | 直接读字段（状态、动画进度、朝向） |
| `entityYaw` | 插值后的偏航角 | 一般只在 `rotate` 里用 |
| `partialTick` | 帧内插值比例 0–1 | 位置/旋转插值都靠它 |
| `poseStack` | 相机相对变换栈 | 你的所有变换都写在它上面 |
| `bufferSource` | 顶点分桶 | `getBuffer(type)` 拿消费者 |
| `packedLight` | 打包光照 | 传给顶点 |

`shouldRender` 是视锥剔除入口：默认实现足够好，只有在「模型比碰撞箱大很多」时才考虑覆写。

## 4.2 数据从哪来：没有 RenderState 时的三种放法

26.2 会先把渲染需要的数据 extract 进 `RenderState`；1.21.1 只能在 `render` 里现取。
所以「数据放哪儿」是 1.21.1 架构上最需要提前决定的：

| 放法 | 什么时候更新 | 优点 | 代价 |
| --- | --- | --- | --- |
| 实体字段 / 同步数据 | 服务端 tick + 同步 | 简单、一致 | 同步带宽；纯客户端表现也要走包 |
| 渲染器里的 `Map<Entity, Data>` | `render` 里懒更新 | 客户端专属数据不用同步 | 必须处理实体卸载，否则内存泄漏 |
| 全局缓存 + 实体 id | tick 写、`render` 读 | 逻辑与渲染分离 | 要自己定义失效规则（死亡/换维度/重载） |

判断标准：**这份数据「逻辑上属于实体」还是「只是画的时候顺手算」**。
属于逻辑的放实体并同步；只是渲染的（骨骼世界坐标、拖尾历史）放渲染器侧的缓存，并在
`EntityRemoveEvent` / `LevelEvent.Unload` 里清。

## 4.3 朝向与插值

```java
float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
float headYaw = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
float ageInTicks = entity.tickCount + partialTick;
```

- `*O` 是「上一 tick 的值」，`rotLerp` 在两者之间按 `partialTick` 插值 —— 少了它转向就是阶梯状的；
- `tickCount + partialTick` 是画呼吸、摇摆这类周期动画的标准时间轴；
- 位置插值用 `Mth.lerp(partialTick, oldX, x)`，或者直接 `entity.getPosition(partialTick)`。

## 4.4 注册渲染器

```java
@SubscribeEvent
static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
    event.registerEntityRenderer(ModEntities.MY_ENTITY.get(), MyRenderer::new);
    event.registerBlockEntityRenderer(ModBlockEntities.MY_BE.get(), MyBeRenderer::new);
}
```

- `EntityRendererProvider.Context` 里有 `getEntityRenderDispatcher()`、`getItemRenderer()`、
  `getBlockRenderDispatcher()`、`getModelSet()` —— 需要这些东西时从构造函数拿，别 `new`；
- 注册是 mod 总线事件，只在加载期触发一次；
- 模型层（LayerDefinition）走 `EntityRenderersEvent.RegisterLayerDefinitions`。

## 4.5 实体渲染器里能做的事

| 想做 | 怎么做 |
| --- | --- |
| 跟随实体移动 | `poseStack.translate(x - cam.x, y - cam.y, z - cam.z)` |
| 让模型朝向 | `poseStack.mulPose(Axis.YP.rotationDegrees(180 - yaw))`（原版惯例） |
| 加挂件/光环 | 不用改渲染器，写一个 `RenderLayer` 或用 `RenderLivingEvent` |
| 改命中箱 / 名牌 | `RenderNameTagEvent` / `RenderHighlightEvent` |
| 加自定义状态 | 渲染器构造函数里存一个缓存，`render` 里按需更新 |

## 4.6 本项目的实体渲染链

```text
MinegenshinClient#registerEntityRenderers（EntityRenderersEvent.RegisterRenderers）
  ├─ 普通实体：（ElementalOrbRenderer、StellarVortexRenderer、IceBlockProjectileRenderer …）
  └─ GeckoLib 模型实体：GeoEntityRenderer + CategoryGeoModel
```

资源解析链是「本 MOD 统一布局 → 角色目录 / GeckoLib 原生根 → GeckoLib 自带缓存」：

| 缓存 | 管什么 | 注册方式 |
| --- | --- | --- |
| `AssetGeoCache` | `assets/minegenshin/<类别>/<id>/…` 的统一布局 | `RegisterClientReloadListenersEvent` |
| `GenshinGeoCache` | 角色目录与 GeckoLib 原生扫描根 | 同上 |
| GeckoLib 自带 | `geo/`、`animations/` 下的默认位置 | GeckoLib 内部 |

三个缓存都在资源重载时重建，所以**不要在别处再存一份路径字符串**，统一走 `GenshinAssets`。

## 4.7 排错

| 症状 | 原因 |
| --- | --- |
| 渲染器没被调用 | 注册在 mod 总线之外 / 实体没进视锥 / `shouldRender` 返回 false |
| 位置对不上、总偏半格 | `PoseStack` 原点理解错（相机相对 vs 世界绝对） |
| 转向是一格一格的 | 忘了 `partialTick` 插值 |
| 内存一直涨 | 渲染器里的 `Map` 没在实体卸载时清 |
| 模型比判定箱大/小 | 模型空间单位是 1/16 方块，缩放要按这个换算 |

下一章：[5. GeckoLib：骨骼动画与渲染层](/doc/rendering-1.21.1-reference-geckolib) 讲动画模型这一路。