# 7. 实战：把渲染接到 Photon


## 7.1 「什么时候召唤粒子」只有四种时机

本项目的 `client/fx` 就是这四种时机的范例（见 `TestCharacterFx` 的类注释）：

| 时机 | 锚点 | 用的 API |
| --- | --- | --- |
| 常驻，跟随角色 | 角色本身 | 内置 `EntityEffectExecutor` |
| 常驻，跟随手中武器 | 武器骨骼 | 自定义锚点 + `FxAnchor` |
| 只在攻击动画期间 | 武器尖 / 指定骨骼 | 自定义锚点 + `ActionStateMachine` |
| 技能释放后定点飞出 | 世界坐标 | `FixedPointExecutor`（实现 `IEffectExecutor`） |

来自服务端的触发一律走网络包 + 客户端判断，不在服务端构造 `FX`。

## 7.2 常驻跟随：`EntityEffectExecutor`

```java
FX fx = FXHelper.getFX(ResourceLocation.fromNamespaceAndPath("minegenshin", "sword_aura"));
var executor = new EntityEffectExecutor(fx, level, player, EntityEffectExecutor.AutoRotate.LOOK);
executor.setOffset(0, 1.0, 0);
executor.start();
```

它会每帧把 FX 根挪到实体眼睛位置（再加 offset），实体死亡时自动销毁
（`EntityEffectExecutor.java:41` 起）。`AutoRotate` 有 `NONE / FORWARD / LOOK / XROT` 四档。

## 7.3 跟随武器：骨骼 → 世界坐标

1. 在渲染层里读武器骨骼（§4.5 的 `renderForBone`）；
2. 把 `PoseStack` 里那根骨骼的矩阵取出来，变换到世界坐标；
3. 把结果存进 `WeaponAnchorCache` 一类的缓存；
4. 下一帧用 `FxAnchor` / `updatePos` / `updateRotation` 把 FX 根挪过去。

关键约束：**第 1 步必须发生在渲染线程**，第 4 步必须在 frame 回调里，中间的数据用缓存过桥。

## 7.4 只在攻击时生效

```java
if (!ActionStateMachine.isAttacking(player)) { anchor.stop(); return; }
```

动画期间开关特效是状态问题，不是渲染问题：状态变化在 tick 里判断，渲染只负责跟随。

## 7.5 定点生成 + 朝前飞

`FixedPointExecutor`（`client/fx/FixedPointExecutor.java`）的做法值得照抄：

```text
tick  ：origin += forward × speed                       （推进一个整刻）
frame ：pos = origin + forward × speed × partialTicks   （刻内插值，不抖）
```

速度在客户端算、位置由执行器给，特效本身（`.fx`）里把模拟空间设成 `WORLD` 就行；
反过来把位移全写在特效里、`forwardSpeed` 传 0 也成立，两种都由特效作者决定。

## 7.6 把游戏状态喂给着色器

1.21.1 上的通道比 26.2 少（没有 RenderState 票据），实际能用的三条：

1. **uniform**：`shader.safeGetUniform("X").set(v)`，每帧在渲染回调里写；
2. **自定义材质**：在 `IMaterial` 里读写 `MaterialContext`，供着色器图使用；
3. **顶点数据**：把数值塞进 UV2 / 颜色通道，着色器里再还原（省 uniform 但要小心精度）。

---
