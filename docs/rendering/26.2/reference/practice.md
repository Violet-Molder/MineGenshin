# 10. 项目实战：把渲染接到特效上


## 10.1 现有接线总览

本仓库已经把四种典型触发方式做成了可直接抄的范例，全部在
`src/main/java/com/linweiyun/genshin/client/fx/`（`TestCharacterFx.java` 是入口）：

| 范例 | 触发时机 | 锚点 | 用到的 API | 关键文件 |
|---|---|---|---|---|
| 1 | 常驻（原神模式 + test 出战） | 角色眼睛 | 内置 `EntityEffectExecutor` | `TestCharacterFx.java:213` |
| 2 | 常驻 | 手上武器的骨骼 | `FxAnchor` + 骨骼缓存 | `TestCharacterFx.java:248`、`FxAnchor.java` |
| 3 | 普攻动作期间 | 武器**尖**（骨骼 + 轴向偏移） | `FxAnchor` + `ActionStateMachine` | `TestCharacterFx.java:263`、`:337` |
| 4 | 技能释放后 | 世界定点，朝前飞 | 自写 `IEffectExecutor` | `FixedPointExecutor.java`、`TestCharacterFx.java:283` |

四个特效 id 与资源路径（`TestCharacterFx.java:88-105` 的常量 + 类注释）：

```
minegenshin:character/test/aura_body        → assets/minegenshin/fx/character/test/aura_body.fx
minegenshin:character/test/aura_weapon      → assets/minegenshin/fx/character/test/aura_weapon.fx
minegenshin:character/test/attack_tip       → assets/minegenshin/fx/character/test/attack_tip.fx
minegenshin:character/test/skill_projectile → assets/minegenshin/fx/character/test/skill_projectile.fx
```

四个 id 对应的 `.fx` 还没做出来也不影响游戏：`FXHelper.getFX` 返回 `null`，整条链路静默空转。

## 10.2 常驻跟随：FxAnchor 的骨架

`FxAnchor`（`client/fx/FxAnchor.java`）是一个**每帧推 Root 位姿**的通用锚点，生命周期只有三步：

```
每客户端 tick：
    anchor.setWanted(条件);     // true = 我想要它亮着
    anchor.tick();              // 内部按需创建 / 销毁 Runtime
```

`tick()` 的判断顺序（`FxAnchor.java:79-92`）：

1. `fx == null`（资源没导出）→ 直接返回，什么都不做；
2. `wanted == false` → `destroy(false)`，残留自然消散；
3. `runtime == null`、`!runtime.isValid()` 或 `runtime.isFinished()` → 重新 `createRuntime()` + `emit(...)`。

**`isValid()` 这一条是缓存 Runtime 的唯一正确存活检查**：切世界、`/photon_client clear_particles`、
其它模组清空粒子之后，引擎会悄悄丢弃粒子，Runtime 自己不会知道；`isValid()` 用
"引擎代际 + Root 心跳"两个信号把它捕获（`FXRuntime.isValid` 的注释写得很细，包括"暂停游戏不算失效"）。

`FxAnchor` 自己实现 `IEffectExecutor`，两个回调分工明确（`FxAnchor.java:121-132`）：

```java
@Override public void updateFXObjectTick(IFXObject object) { /* 低频逻辑：这里什么都不做 */ }

@Override public void updateFXObjectFrame(IFXObject object, float partialTicks) {
    if (runtime == null || object != runtime.getRoot()) return;   // 只动 Root
    AnchorPose pose = poseProvider.pose(player, partialTicks);
    if (pose == null) return;                                     // 本帧不动 = 保持上一帧
    object.updatePos(pose.position());
    object.updateRotation(pose.rotation());
    object.updateScale(pose.scale());
}
```

位姿由 `PoseProvider` 提供（`@FunctionalInterface`，`FxAnchor.java:40`），返回 `null` 的语义是
"这一帧不动"。随机源用固定种子 `RandomSource.create(20260928L)`（`FxAnchor.java:51`），
让带随机函数的资产在重播时可复现。

> **常驻特效记得在编辑器里开 Looping**。没开也不会错——播完会被 `isFinished()` 分支重建——
> 但会多一次无谓的重建（这个取舍写在 `TestCharacterFx` 的类注释里）。

## 10.3 骨骼 → 世界坐标：这次渲染的第几帧？

骨骼位姿**不是实体状态**，它是"动画采样 + 层级变换累乘"的产物，只在渲染那一刻存在。
本项目用 GeckoLib 的 `GeoRenderLayer` 把它抓出来：

- `WeaponAnchorGeoLayer`（`client/render/character/WeaponAnchorGeoLayer.java`）挂在角色的渲染器上
  （`CharacterRenderDispatcher.java:348`）；
- 提取阶段用 `DataTicket` 把"这是哪个玩家"带进渲染阶段（`:63-68`）；
- 渲染阶段用 `addPerBoneRender` 拿到"已经摆到该骨骼位姿"的 `PoseStack`，就地取矩阵（`:70-84`）；
- 取位姿时做**相机空间换算**（`:95-110`）：

```java
Matrix4f bonePose = new Matrix4f(info.poseStack().last().pose());
Vector3f local   = bonePose.getTranslation(new Vector3f());        // 相机空间的平移
Quaternionf rot  = bonePose.getUnnormalizedRotation(new Quaternionf());
Vec3 camera      = info.cameraState().pos;
Vec3 world       = new Vec3(camera.x + local.x, camera.y + local.y, camera.z + local.z);
```

这就是"实体渲染的 PoseStack 原点是相机"那条结论的直接应用。写入前还有一道
`SANITY_DISTANCE = 8.0` 的检查：结果离玩家太远就整帧丢弃——**宁可让特效停在上一帧，
也不要它飞到天边**。

抓到的结果进 `WeaponAnchorCache`（`client/render/character/WeaponAnchorCache.java`）：

- 键是玩家 UUID，值是 `Entry(position, rotation, frame)`；
- 时间戳用**渲染帧号**（`CharacterRenderDispatcher.renderFrame()`，在 `RenderFrameEvent.Pre` 自增，
  `CharacterRenderDispatcher.java:114-118`），不用实体 tick；
- `fresh()` 在超过 `MAX_AGE_FRAMES = 3` 帧没更新时返回 `null`：角色在第一人称、离屏、
  或被别的模组挡住时根本不会渲染，这时调用方保持上一帧位姿（`WeaponAnchorCache.java:33`、`:58-64`）；
- 读写都发生在渲染线程，所以用普通 `HashMap` 就够，不需要并发容器（类注释）。

`AnchorPose`（`client/fx/AnchorPose.java`）是"传给 Photon Root 的位姿"：

```java
public record AnchorPose(Vector3f position, Quaternionf rotation, Vector3f scale) {
    public static Quaternionf yaw(float yawDegrees) {
        return new Quaternionf().rotationY((float) Math.toRadians(yawDegrees));
    }

    public static Quaternionf look(float yawDegrees, float pitchDegrees) {
        return new Quaternionf().rotationYXZ(
                (float) Math.toRadians(-yawDegrees),
                (float) Math.toRadians(pitchDegrees), 0f);
    }
}
```

**角度制还是四元数**在这里也是坑：`AnchorPose` 收的是四元数（弧度语义），
而 `IFXEffectExecutor#setRotation(double,double,double)` 是角度制（§9.4）。两套入口别混。

## 10.4 按动作状态触发

范例 3 的判据是"现在是不是普攻"（`TestCharacterFx.inAttackAnimation`，`TestCharacterFx.java:202-211`）：

```java
String state = ActionStateMachine.currentState;
return state != null
        && (TestAnimations.SPECIAL_ANIMS.contains(state) || state.contains("attack"));
```

两条经验值得抄：

1. **精确名单优先**（`SPECIAL_ANIMS`），再加一层宽松兜底；
2. 判据只读客户端状态机，不碰服务端数据——特效是纯表现层的判断。

想让特效在动作开始的那一刻**单发**（而不是整段常驻），正确的位置是客户端的动作钩子/状态机
返回值处，而不是 `FxAnchor` 的 `PoseProvider`（那个是每帧调的）。

## 10.5 定点生成 + 朝前飞：自写 Executor 的完整例子

`FixedPointExecutor`（`client/fx/FixedPointExecutor.java`）演示了"位置由代码推进"的一类特效：

| 回调 | 做什么 |
|---|---|
| `updateFXObjectTick` | `origin += forward × speed`（整刻推进）；到寿命就在 tick 里 `destroy(false)` |
| `updateFXObjectFrame` | `pos = origin + forward × speed × partialTicks`（刻内插值），写 Root |

```java
this.forward  = Vec3.directionFromRotation(0f, yawDegrees);   // Minecraft 偏航角：0 = +Z，90 = -X
this.rotation = AnchorPose.yaw(yawDegrees);
```

使用侧（`TestCharacterFx.onSkillCast`，`:283-306`）：

```java
var executor = new FixedPointExecutor(level, origin, player.getYRot(), SKILL_FORWARD_SPEED)
        .lifetime(SKILL_LIFETIME_TICKS);
executor.start(fx);
```

`forwardSpeed = 0` 是合法用法：位移完全交给资产的 "Velocity over Lifetime" 模块——
两种做法都成立，取决于特效作者的习惯（类注释）。

## 10.6 从公共包触发：为什么要有 SkillCastHooks

招式本体跑在**公共包**（服务端也会加载），而 Photon 是**客户端**库。
公共包直接引用 Photon / `Minecraft` 的类，专用服务器加载就会崩。

本项目的解法是 `SkillCastHooks`（`core/character/talent/SkillCastHooks.java`）：

- 公共侧只有一个纯接口 + 一张监听表，`fire(player, skillType)` 两端都会走到（`:55`）；
- 客户端在自己的初始化里 `register` 监听器（`TestCharacterFx.java:124`），
  专用服务器上**没有任何监听器，`fire` 就是一次空循环**；
- 监听器自己负责"只处理本地玩家"（`TestCharacterFx.onSkillCast` 里 `player != mc.player` 直接返回），
  别的玩家的特效由他们自己的客户端放。

同样的思路适用于任何"游戏逻辑想叫特效"的场景：**公共包只发通知，客户端自己决定怎么演。**

## 10.7 资源交付：从编辑器到 jar

1. 在游戏内 `/photon_editor` 里做好效果（单人世界）；
2. 导出：`File → Export → FX`（需要自己保证依赖资源已随仓库提供）或 `File → Export → FX Pack`
   （推荐——它会把 Material / Graph / Mesh / Texture / Shader 一起收集，见 §8.3）；
3. 把 pack 里的 `assets/` 内容拷进 `src/main/resources/assets/`（`TestCharacterFx` 类注释给的就是这条路），
   或者把 `.fxpack` 放进 `src/main/resources/fxpacks/`（`FXPacks.MOD_FXPACKS_DIR = "fxpacks"`，
   模组 jar 根目录下的 `fxpacks/` 会被自动挂载）；
4. 进游戏后如果没看到效果，先 `/photon_client clear_client_fx_cache` 再试；
5. 发布前跑一遍 §11.5 的清单。

## 10.8 六个范例的验收清单（怎么确认"真的做对了"）

每个范例都有自己的"看起来对了但其实错了"的坑，按这张表逐条过：

| 范例 | 验收动作 | 通过标准 |
|---|---|---|
| 常驻跟随（§10.2） | 切世界 → 回来；`/photon_client clear_particles` → 等一帧 | 光环自动重新出现，不刷异常 |
| 武器光效跟骨骼（§10.3） | 第一人称 / 第三人称 / 转身 / 把角色藏到方块后 | 特效跟着武器走；被遮挡时**停住不回退到镜头** |
| 按动作触发（§10.4） | 打出普攻、被打断、连续两段 | 只在动作期间亮；打断后立即消失；不会整段常驻 |
| 定点 + 朝前飞（§10.5） | 转向不同角度释放、连续快速释放两次 | 朝向与释放方向一致；两次互不干扰（`allowMulti` 与去重逻辑） |
| 公共包触发（§10.6） | 开专用服务器启动、玩家进服放技能 | 服务器无异常；每个玩家看到自己那份 |
| 服务端触发（§9.7） | 两个客户端同开，一个人放技能 | 双方都看得见；离得太远的玩家不被广播拖累 |

**共同的一条底线**：把 Photon 去掉或让资源缺失，游戏必须**照常跑**（`getFX` 返回 `null`、链路空转）。
这条在你的开发环境里很容易忘，因为在你自己机器上资源永远在。
