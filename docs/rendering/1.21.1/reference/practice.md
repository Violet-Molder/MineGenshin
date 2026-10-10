# 10. 项目实战

这一章把前九章落到本项目的代码上：四种特效时机、角色渲染器的原点与骨骼规则、资源解析链、
服务端触发、以及「新加一个特效」的操作清单。

## 10.1 四种特效时机

本项目的 `client/fx` 就是这四种时机的范例（见 `TestCharacterFx` 的类注释）：

| # | 时机 | 锚点 | 用什么 |
| --- | --- | --- | --- |
| 1 | 常驻，跟随角色 | 角色本身 | `EntityEffectExecutor` |
| 2 | 常驻，跟随手中武器 | 武器骨骼 | `FxAnchor` + 武器挂点层 |
| 3 | 只在攻击动画期间 | 武器尖（骨骼 + 轴向偏移） | `FxAnchor` + `ActionStateMachine` |
| 4 | 战技释放后，身前生成并朝前飞 | 世界坐标 | `FixedPointExecutor`（自定义 `IEffectExecutor`） |

判断自己该用哪种：**看效果的生命周期跟着谁** —— 跟着实体就是 1/2，跟着动作就是 3，跟着世界就是 4。

## 10.2 跟随角色（最简单的一档）

```java
EntityEffectExecutor exec = new EntityEffectExecutor(fx, level, player,
        EntityEffectExecutor.AutoRotate.LOOK);
exec.setOffset(0, 1.0, 0);   // 眼睛位置再往上 1 格
exec.start();
```

它每帧把 FX 根移到实体眼睛位置（加 offset），实体死亡时自动销毁。
`AutoRotate` 四档：`NONE`（不转）、`FORWARD`（跟朝向）、`LOOK`（跟视线）、`XROT`（只跟俯仰）。

## 10.3 跟随武器骨骼

骨骼挂点只有**渲染期**能读，特效在**每帧回调**里更新，中间要靠缓存过桥：

```text
渲染期（渲染层 renderForBone / preRender）
  → 读武器骨骼的 PoseStack 矩阵 → 变换到世界坐标 → 存进 WeaponAnchorCache
下一帧（执行器 updateFXObjectFrame）
  → 从缓存取锚点 → runtime.root.updatePos / updateRotation
```

约束（三条都不能违）：
1. 读骨骼必须在渲染线程，且**只有渲染那一瞬间的值是真的**；
2. 缓存要按实体 + 骨骼名分键，实体卸载时清理；
3. 不要「tick 里推进、frame 里也推进」——见 [9. Photon2 Java API](/doc/rendering-1.21.1-reference-photon-api)。

## 10.4 只在攻击动画期间生效

```java
if (!ActionStateMachine.isAttacking(player)) { anchor.stop(); return; }
```

这是**状态问题不是渲染问题**：状态在 tick 里判断，渲染/执行器只负责跟随。
把「什么时候该有」写进状态机，比在渲染回调里判断动画播放位置可靠得多。

## 10.5 定点生成后朝前飞

```text
tick  ：origin += forward × speed                        （推进一个整刻）
frame ：pos = origin + forward × speed × partialTicks     （刻内插值，不抖）
```

位置全部由代码给，特效里把模拟空间设成 `WORLD`；
反过来把位移写在特效里、`speed` 传 0 同样成立 —— 看特效作者的习惯，别两边都写。

## 10.6 角色渲染器：原点与骨骼规则

`client/render/character/CharacterRenderer` 继承 `GeoObjectRenderer`，有两处必须知道：

1. **原点补偿**：`preRender` 里反向 `translate(-0.5F, -0.51F, -0.5F)` 抵消父类的摆件补偿，
   否则模型整体偏到斜后方半格，和判定箱、影子对不上。
2. **骨骼规则注入**：本帧的 `BoneUpdater` 在 `preRender` 里应用到烘焙模型上，
   改好的骨骼状态在本次渲染中一直有效 —— 面部表情、武器挂点都是这么塞进去的。

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

## 10.7 资源解析链

```text
CategoryGeoModel（类别 + id）
  └─ AssetGeoCache（assets/minegenshin/<类别>/<id>/…）
       └─ GenshinGeoCache（角色目录 / GeckoLib 原生根）
            └─ GeckoLib 自带缓存
```

三个缓存都是资源重载监听器（`RegisterClientReloadListenersEvent`），
所以**不要在任何地方再存一份路径**：统一走 `GenshinAssets`，重载后自动生效。

## 10.8 服务端触发特效

服务端只发意图，客户端构造 FX：

```java
// 服务端
PacketDistributor.sendToPlayersTrackingEntity(entity, new PlayFxPayload(fxId, offset, rotation));
// 客户端
FX fx = FXHelper.getFX(fxId);
new EntityEffectExecutor(fx, level, player, EntityEffectExecutor.AutoRotate.FORWARD).start();
```

服务端**不要 import** 任何 `com.lowdragmc.photon.client.*`；那会让专用服务器崩在类加载上。

## 10.9 新加一个特效的操作清单

1. 编辑器里做好 `.fx`，放 `assets/<ns>/fx/<name>.fx`；
2. 决定时机（上面四选一），写对应的执行器 / 锚点；
3. 需要骨骼挂点就先在渲染层里读出来、缓存；
4. 触发逻辑放状态机或技能代码，服务端只发包；
5. 检查生命周期：实体死亡、退世界、资源重载三种情况都要能正确结束；
6. 用 Photon 编辑器先量一次开销，再决定是否批量使用。

下一章：[11. 排错手册](/doc/rendering-1.21.1-reference-troubleshooting)。