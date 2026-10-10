# 10. 项目实战


## 10.1 本项目 1.21.1 的四种特效时机

`client/fx` 目录就是这四种时机的范例（见 `TestCharacterFx` 的类注释）：

| # | 时机 | 锚点 | 用到的 API |
| --- | --- | --- | --- |
| 1 | 常驻，跟随角色 | 角色本身 | `EntityEffectExecutor` |
| 2 | 常驻，跟随手中武器 | 武器骨骼 | `FxAnchor` + 武器挂点层 |
| 3 | 只在攻击动画期间 | 武器尖 | `FxAnchor` + `ActionStateMachine` |
| 4 | 技能释放后定点飞出 | 世界坐标 | `FixedPointExecutor`（`IEffectExecutor`） |

## 10.2 定点飞出的写法

```text
tick  ：origin += forward × speed                       （推进一个整刻）
frame ：pos = origin + forward × speed × partialTicks   （刻内插值，不抖）
```

速度在客户端算、位置由执行器给，特效本身把模拟空间设成 `WORLD` 即可；
反过来把位移全写在特效里、`forwardSpeed` 传 0 也成立，两种都由特效作者决定。

## 10.3 角色渲染器：原点与骨骼规则

`client/render/character/CharacterRenderer` 继承 `GeoObjectRenderer`：

```java
public void performRenderPass(GenshinReplacedPlayer animatable, @Nullable Player related,
                              PoseStack poseStack, MultiBufferSource bufferSource,
                              int packedLight, float partialTick,
                              @Nullable BoneUpdater<BoneRenderState> boneUpdater) {
    this.animatable = animatable;
    this.pendingUpdater = boneUpdater;
    render(poseStack, animatable, bufferSource, null, null, packedLight, partialTick);
}
```

两件必知的事：

1. **原点**：`preRender` 里反向 `translate(-0.5F, -0.51F, -0.5F)` 抵消 `GeoObjectRenderer`
   的摆件补偿，否则模型整体偏到斜后方半格，和判定箱对不上；
2. **骨骼规则**：本帧的 `BoneUpdater` 在 `preRender` 里作用于烘焙模型，
   改好的骨骼状态在本次渲染中一直有效。

## 10.4 资源解析链

```text
CategoryGeoModel（类别 + id）
  └─ AssetGeoCache（本 MOD 统一布局：assets/minegenshin/<类别>/<id>/…）
       └─ GenshinGeoCache（角色目录 / GeckoLib 原生根）
            └─ GeckoLib 自带缓存
```

三个缓存都是 `RegisterClientReloadListenersEvent` 的监听器；
资源包重载 = 模型 / 动画 / 贴图一起刷新，不要在别处再存一份路径。

## 10.5 服务端触发

客户端特效必须由客户端发起。服务端的做法是发网络包（本项目的 RPC 走 LDLib2），
客户端收到后构造 `FX` 与执行器。**服务端不要 import 任何 `client` 包。**

---
