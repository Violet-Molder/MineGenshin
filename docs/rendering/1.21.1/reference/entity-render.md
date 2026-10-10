# 4. 实体渲染与「没有 RenderState」的世界


## 4.1 `EntityRenderer` 的契约

```java
public abstract class EntityRenderer<T extends Entity> {                                  // :26
    public boolean shouldRender(T entity, Frustum frustum, double x, double y, double z)  // :52
    public void render(T entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) // :89
    protected void renderNameTag(...)                                                     // :185
}
```

26.2 会先 extract 出 `RenderState` 再渲染；1.21.1 没有这一步，
所以「数据放哪儿」是最需要想清楚的问题：

| 放哪儿 | 什么时候更新 | 代价 |
| --- | --- | --- |
| 实体字段 | 服务端 / 客户端 tick | 同步成本；客户端只读的字段要在 render 里算 |
| 渲染器里的 `Map<实体, 数据>` | 渲染时懒更新 | 必须处理实体卸载，否则内存泄漏 |
| 全局缓存 + 实体 id | tick 写、渲染读 | 需要自己定义失效规则 |

## 4.2 注册渲染器

```java
@SubscribeEvent
static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
    event.registerEntityRenderer(ModEntities.MY_ENTITY.get(), MyRenderer::new);
}
```

方块实体用 `event.registerBlockEntityRenderer(...)`；物品用 `BlockEntityWithoutLevelRenderer`。

## 4.3 实体朝向与插值

```java
float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
float headYaw = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
float ageInTicks = entity.tickCount + partialTick;
```

`*O` 是上一刻的值，`rotLerp` 负责刻内插值 —— 少了它就能看到一格一格的转向。

## 4.4 本项目 1.21.1 的实体渲染链

```text
EntityRenderersEvent.RegisterRenderers（MinegenshinClient）
  ├─ 普通实体渲染器（ElementalOrbRenderer / StellarVortexRenderer / IceBlockProjectileRenderer …）
  └─ GeckoLib 模型实体
       └─ GeoEntityRenderer + CategoryGeoModel（资源按「类别 + id」解析）
```

资源解析顺序：`AssetGeoCache`（本 MOD 统一布局）→ `GenshinGeoCache`（角色 / GeckoLib 原生根）
→ GeckoLib 自带缓存。三者都注册进 `RegisterClientReloadListenersEvent`，
所以资源包重载后模型 / 动画 / 贴图会一起刷新。

---
