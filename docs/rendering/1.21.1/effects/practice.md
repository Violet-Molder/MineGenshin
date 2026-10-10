# 7. 实战：把渲染接到 Photon

## 7.1 四种时机（本项目 `client/fx` 就是范例）

| 时机 | 锚点 | 用什么 |
| --- | --- | --- |
| 常驻跟随角色 | 角色 | `EntityEffectExecutor` |
| 常驻跟随武器 | 武器骨骼 | `FxAnchor` + 武器挂点层 |
| 只在攻击期间 | 武器尖 | `FxAnchor` + `ActionStateMachine` |
| 技能后定点飞出 | 世界坐标 | `FixedPointExecutor` |

## 7.2 两条回调的界律

| 回调 | 频率 | 写什么 |
| --- | --- | --- |
| `updateFXObjectTick` | 20/s | 推进状态、判断生死 |
| `updateFXObjectFrame` | 每帧 | 按 `partialTick` 插值算位置/朝向 |

**两边都写位置 = 抖动**。

## 7.3 最小启动代码

```java
FX fx = FXHelper.getFX(ResourceLocation.fromNamespaceAndPath("minegenshin", "skill_burst"));
EntityEffectExecutor exec = new EntityEffectExecutor(fx, level, player,
        EntityEffectExecutor.AutoRotate.LOOK);
exec.setOffset(0, 1.0, 0);
exec.start();
```

## 7.4 骨骼挂点怎么接

```text
渲染期：renderForBone / preRender 里读骨骼矩阵 → 变换到世界坐标 → 存缓存
每帧：从缓存取锚点 → runtime.root.updatePos / updateRotation
```

约束：读骨骼只在渲染线程、只在渲染那一刻有效；缓存按实体 + 骨骼名分键并在实体卸载时清。

## 7.5 定点飞出

```text
tick  ：origin += forward × speed
frame ：pos = origin + forward × speed × partialTicks
```

## 7.6 服务端触发

服务端只发包，客户端构造 `FX` 与执行器；服务端不要 import `com.lowdragmc.photon.client.*`。

## 7.7 新加特效的清单

1. `.fx` 放 `assets/<ns>/fx/`；
2. 选时机、写执行器；
3. 需要骨骼挂点就先在渲染层读出来；
4. 触发逻辑进状态机或技能代码，服务端只发包；
5. 覆盖「实体死亡 / 退世界 / 资源重载」三种结束路径；
6. 用编辑器先量开销。

深入：[完全参考 10. 项目实战](/doc/rendering-1.21.1-reference-practice)。