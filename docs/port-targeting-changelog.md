# 参考2（1.21.1）· 索敌 / 追击 更新日志

**这个文件是追加式的**：26.2 这边每动一次「索敌 · 追击 · 突进」相关的东西，
就在**最下面**加一条「本次要同步什么」，**绝不修改上面的历史条目** ——
你只要从头往下看一遍，就知道自己的工程还差哪几条。

| 文件 | 作用 |
|---|---|
| `docs/port-targeting-to-reference2.patch.md` | **基线**：第一次移植的完整 diff（新文件全文 + 锚点改动 + 排错） |
| 本文件 | **增量**：基线之后每一次改动要怎么补 |

覆盖范围：`CombatTargeting` / `TargetPolicy` / `ChaseReach`（索敌）、
`AttackApproach`（转向 / 突进 / 吸附）、以及它们依赖的 `ActionStateMachine` 钩子。

每条按同一结构写：

```
① 本次改了什么（26.2 这边）—— 现象 + 原因
② 1.21.1 怎么改 —— 逐文件 diff / 新文件全文
③ 怎么验证
④ 注意事项 / 影响
```

> 锚点行号不要死记：**按代码内容找**（每段都给了能找到的唯一片段）。
> 包名按你工程的来：`net.luoshu.imaginarybranch.*`。

---

# 第 1 条 · 追击可行性 + 保护性原则 + 突进延后开始

## 1.1 本次改了什么（26.2 这边）

三个现象，一起解决：

1. **隔着方块还去追**：目标在墙后 / 楼上 / 地板下，距离在索敌范围内 → 人一路撞墙到超时，
   伤害也没打出来。
2. **高度差**：飞到别人头上够不着；更常见的是目标在地板下面 ——
   冲过去只会停在地板上方干等，因为高度差永远补不平（表现是「在他头顶逗留一会儿」）。
3. **突进必须能延后**：有些招式的前几帧本身就是动作（起跳 → 人消失 → 化作细长螺旋 → 突刺），
   位移发生在「变身」之后，不能一按下就冻结动画冲出去。

对应三件事：

| 改动 | 名字 | 一句话 |
|---|---|---|
| 索敌可行性 | `ChaseReach`（新文件） | 身体过不去、且被拦下的地方也够不着 → 不锁、不追 |
| 保护性原则 | `AttackApproach` 里的两个探测器 | **只要停下来就开打**，不管因为什么 |
| 突进延后 | 每招的 `dashStartDelay` | 前 n 刻先播变身，第 n 刻才冻结并冲 |

---

## 1.2 ① 新增文件：`ChaseReach.java`

路径：`src/main/java/net/luoshu/imaginarybranch/combat/targeting/ChaseReach.java`

**全文照抄**（1.21.1 的 `ClipContext` / `BlockHitResult` / `level().clip(...)` 与 26.2 一致，不用改）：

```java
package net.luoshu.imaginarybranch.combat.targeting;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 「这一次追击能不能把目标带进攻击距离」—— 索敌的可行性判定。
 *
 * <h2>要解决的两个现象</h2>
 * <ol>
 *   <li><b>隔着方块还去追</b>：目标在墙后 / 楼上 / 地板下，距离在索敌范围内，
 *       于是玩家一路撞墙撞到超时，伤害也没打出来。</li>
 *   <li><b>高度差</b>：飞到别人头上够不着；更常见的是目标就在<b>地板下面</b>——
 *       人冲过去只会停在地板上方干等他，因为高度差永远补不平。</li>
 * </ol>
 *
 * <h2>判定方式：把「身体能不能过去」夹出来</h2>
 * 从玩家身上取两根采样线（脚、腰）射向目标。任一条被方块挡住，就说明身体过不去；
 * 这时再看<b>被拦下的位置离目标多远</b>：
 *
 * <pre>
 * 一路通畅            → 追得上（能贴到目标身前）
 * 被拦下，但拦停点     → 也追得上：冲过去撞墙就停在那里，那个位置本来就够得着
 *   离目标 ≤ 攻击距离
 * 被拦下且够不着       → 追不上，不锁、不追（这才是「隔了两三个方块就别追」）
 * </pre>
 *
 * <p>第二条是刻意的：<b>「被墙拦停」不等于「追不上」</b>，
 * 只要拦停的地方已经在攻击距离内，那一次冲撞本身就是有效的接近。
 *
 * <h2>纵向也要卡一道</h2>
 * 「足够近」是用直线距离算的，而直线距离对<b>垂直方向</b>特别宽松：
 * 你站在地板上、目标在地板下 2 格，直线距离只有 2 格 —— 看着够得着，其实中间隔着地板。
 * 所以拦停点还要过一道高度检查：<b>拦停点到目标的垂直落差 &gt; 攻击距离 × {@value #VERTICAL_REACH_FACTOR}</b>
 * 就不算追得上。地板下、天花板上的目标都会被这一条挡掉。
 *
 * <p>只做<b>方块</b>判定（{@code Block.COLLIDER}）：草、水、火把这些不挡路的东西不参与，
 * 其他生物也不参与 —— 怪挡怪不该让人放弃追击。
 */
public final class ChaseReach {

    /**
     * 拦停点到目标的高度落差容差 = 攻击距离 × 这个系数。
     *
     * <p>0.75 而不是 1.0：直线距离会低估纵向的阻隔（见类注释），
     * 卡紧一点才能把「地板下面 2~3 格」这种典型情况挡在索敌之外。
     */
    public static final double VERTICAL_REACH_FACTOR = 0.75;

    /** 拦停点到目标的距离允许比攻击距离多这么多格（身体有宽度，射线有误差）。 */
    public static final double STOP_SLACK = 0.5;

    /**
     * 身体采样高度（相对脚底，格）：脚 + 腰。
     *
     * <p>只打一条「脚 → 脚」的射线会从 1 格高的缝里穿过去，而身体其实过不去；
     * 加一条腰的高度能挡住绝大多数「看着通、其实钻不过」的情况。
     */
    private static final double[] BODY_SAMPLES = {0.2, 1.2};

    private ChaseReach() {
    }

    /**
     * 这一次追击能不能把目标带进攻击距离。
     *
     * @param attackRange 这一招的生效攻击距离（格）
     * @return {@code true} = 值得追（含「撞墙停下也够得着」）；{@code false} = 别锁、别追
     */
    public static boolean canCloseIn(Player player, LivingEntity target, double attackRange) {
        double range = Math.max(0.5, attackRange);

        Vec3 feet = player.position();
        Vec3 goal = target.position();

        // 已经在攻击距离内：连突进都不用，挡不挡墙都无所谓
        if (feet.distanceTo(goal) <= range) {
            return true;
        }

        for (double offset : BODY_SAMPLES) {
            Vec3 from = feet.add(0, offset, 0);
            Vec3 to = goal.add(0, offset, 0);

            BlockHitResult hit = player.level().clip(new ClipContext(
                    from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

            if (hit.getType() == HitResult.Type.MISS) {
                continue; // 这一层通得过
            }

            if (isReachableFrom(hit.getLocation(), goal, range)) {
                continue; // 被拦下了，但拦停的位置本来就够得着 → 照追
            }

            return false;
        }

        return true;
    }

    /** 从被拦下的位置能不能打到目标：距离够近，而且高度落差没超过容差。 */
    private static boolean isReachableFrom(Vec3 stopPoint, Vec3 target, double attackRange) {
        if (stopPoint.distanceTo(target) > attackRange + STOP_SLACK) {
            return false;
        }
        return Math.abs(stopPoint.y - target.y) <= attackRange * VERTICAL_REACH_FACTOR;
    }
}
```

## 1.3 ② 改 `CombatTargeting`：`Params` 多带两个信息

**为什么要带**：可行性判定要在两个地方跑 —— 索敌（挑候选）和保持（每刻校验），
而这两个地方只有 `Params` 可用。所以把「这一招打多远」和「这一招追不追人」放进 `Params`。

### (1) `Params` record 加两个分量 + 两个新工厂

```diff
+    /** {@link Params#DEFAULT} 用的攻击距离占位值（不判可行性，用不到它）。 */
+    public static final double DEFAULT_PROBE_RANGE = 3.0;
+
     /**
      * 一次索敌/保持用的参数 —— <b>由招式带进来</b>，不是全局写死的。
      *
      * @param acquireRange 首次索敌距离（格）
      * @param acquireAngle 索敌视角半角（度）
      * @param keepRange    保持锁定的距离（格）
      * @param keepAngle    保持锁定的视角半角（度）
+     * @param attackRange  这一招的生效攻击距离（格）—— 可行性判定用它
+     * @param chase        要不要做「追击可行性」判定（{@link ChaseReach}）
      * @param policy       目标筛选策略
      */
     public record Params(double acquireRange, double acquireAngle,
-                         double keepRange, double keepAngle, TargetPolicy policy) {
+                         double keepRange, double keepAngle,
+                         double attackRange, boolean chase, TargetPolicy policy) {
 
-        /** 近战默认：用类里的全局常量。 */
+        /** 不判可行性的默认（召唤物 / 工具代码用）：只按距离与视角筛。 */
         public static final Params DEFAULT = new Params(
-                ACQUIRE_RANGE, ACQUIRE_ANGLE, KEEP_RANGE, KEEP_ANGLE, TargetPolicy.DEFAULT);
+                ACQUIRE_RANGE, ACQUIRE_ANGLE, KEEP_RANGE, KEEP_ANGLE,
+                DEFAULT_PROBE_RANGE, false, TargetPolicy.DEFAULT);
 
         public Params {
             keepRange = Math.max(keepRange, acquireRange);
             keepAngle = Math.max(keepAngle, acquireAngle);
             policy = policy == null ? TargetPolicy.DEFAULT : policy;
         }
 
+        /** 近战招式用：索敌 &gt; 攻击距离，差额交给突进 —— 所以要判「追不追得上」。 */
+        public static Params forChase(double attackRange) {
+            return new Params(ACQUIRE_RANGE, ACQUIRE_ANGLE, KEEP_RANGE, KEEP_ANGLE,
+                    Math.max(0.5, attackRange), true, TargetPolicy.DEFAULT);
+        }
+
         /** 远程招式用：索敌距离 = 攻击距离，保持圈只多留一点余量防抖。 */
         public static Params forRange(double attackRange) {
             double range = Math.max(0.5, attackRange);
-            return new Params(range, ACQUIRE_ANGLE, range + RANGED_KEEP_MARGIN, KEEP_ANGLE,
-                    TargetPolicy.DEFAULT);
+            return new Params(range, ACQUIRE_ANGLE, range + RANGED_KEEP_MARGIN, KEEP_ANGLE,
+                    range, false, TargetPolicy.DEFAULT);
         }
     }
```

### (2) 索敌时跳过追不到的目标

`acquire(...)` 里那一行：

```diff
-        LivingEntity best = findBest(player, p.acquireRange(), p.acquireAngle(), p.policy());
+        LivingEntity best = findBest(player, p);
```

`findBest` 整体换成按 `Params` 工作的版本：

```java
    @Nullable
    private static LivingEntity findBest(Player player, Params params) {
        AABB box = player.getBoundingBox().inflate(params.acquireRange());
        List<LivingEntity> candidates = player.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> params.policy().isTargetable(player, e));

        LivingEntity best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (LivingEntity candidate : candidates) {
            double distanceSq = player.distanceToSqr(candidate);
            if (distanceSq > params.acquireRange() * params.acquireRange()) {
                continue;
            }
            if (angleTo(player, candidate) > params.acquireAngle()) {
                continue;
            }

            // 够不着的（墙后 / 地板下 / 天花板上面）直接不算候选 ——
            // 与其锁上去撞墙，不如把索敌位留给真正打得到的那只。
            if (params.chase() && !ChaseReach.canCloseIn(player, candidate, params.attackRange())) {
                continue;
            }

            double score = scoreOf(player, candidate, params.policy());
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        return best;
    }
```

### (3) 保持校验：追不到就进宽限计时（最终丢锁）

```diff
     private static boolean isValid(Player player, LivingEntity target, State state) {
         ...
         double distanceSq = player.distanceToSqr(target);
         if (distanceSq > state.params.keepRange() * state.params.keepRange()) {
             return false;
         }
 
-        return angleTo(player, target) <= state.params.keepAngle();
+        if (angleTo(player, target) > state.params.keepAngle()) {
+            return false;
+        }
+
+        // 追不上的目标不再保持：隔着方块、在地板下、飞在天花板上面 ——
+        // 锁着它只会让人一直对着打不到的方向转，还占着索敌位。
+        return !state.params.chase()
+                || ChaseReach.canCloseIn(player, target, state.params.attackRange());
     }
```

> `import` 不用加：`ChaseReach` 跟 `CombatTargeting` 同一个包。

### (4) 顺手改一句注释（可选）

类注释的「丢锁」列表里加一条：

```diff
  *   <li>超出保持范围、或转身背对超过 {@link #KEEP_ANGLE}° → 累计 {@link #LOSE_GRACE_TICKS} 刻后丢</li>
+ *   <li><b>追不上了</b>（隔着方块、在地板下、飞在天花板上面）→ 同样进宽限计时，
+ *       见 {@link ChaseReach}；只有会突进的招式才判（{@link Params#chase()}）</li>
```

## 1.4 ③ 改 `ApproachParams.targeting(...)`：近战要走「判可行性」那条

你的 `AttackApproach.ApproachParams#targeting(double attackRange)` 里：

```diff
         public CombatTargeting.Params targeting(double attackRange) {
             CombatTargeting.Params base = ranged
                     ? CombatTargeting.Params.forRange(acquireRange > 0 ? acquireRange : attackRange)
-                    : CombatTargeting.Params.DEFAULT;
+                    : CombatTargeting.Params.forChase(attackRange);
 
             double acquire = acquireRange > 0 ? acquireRange : base.acquireRange();
             double keep = keepRange > 0 ? keepRange : base.keepRange();
             double acquireAngle = this.acquireAngle > 0 ? this.acquireAngle : base.acquireAngle();
             double keepAngle = this.keepAngle > 0 ? this.keepAngle : base.keepAngle();
 
-            return new CombatTargeting.Params(acquire, acquireAngle, keep, keepAngle, base.policy());
+            return new CombatTargeting.Params(acquire, acquireAngle, keep, keepAngle,
+                    base.attackRange(), base.chase(), base.policy());
         }
```

**远程招式不受影响**：`forRange(...)` 的 `chase = false` —— 隔着墙照样锁（那是射击的事）。
想给某一招关掉可行性判定（比如「穿墙剑气」）：`Params.DEFAULT`（`chase = false`）或者自己 new 一个 `chase = false` 的。

## 1.5 ④ 改 `AttackApproach`：保护性原则（停下来就开打）

> 突进的目的是**快速把人带进自己的攻击范围**，不是百分百贴身。
> 所以只要接近这件事做不下去了，就当场停下来出手 —— **不管因为什么**。

### (1) 加常量（放在 `ARRIVE_PATIENCE_TICKS` 后面）

```java
    // ==================== 保护性原则（追不动了就开打） ====================

    /** 本刻实际位移小于这个值（格）就当成「人被挡住了」。 */
    public static final double DASH_BLOCKED_EPSILON = 0.05;

    /** 连续这么多刻「身体没动」→ 立刻停手开打。 */
    public static final int DASH_BLOCKED_PATIENCE_TICKS = 2;

    /** 距离每刻缩小不到这个值（格）就当成「没在接近」。 */
    public static final double DASH_PROGRESS_EPSILON = 0.05;

    /** 连续这么多刻「没接近」→ 立刻停手开打。 */
    public static final int DASH_STALL_PATIENCE_TICKS = 2;
```

### (2) 加字段（放在 `nearTicks` 旁边）

```java
    // 保护性原则用的两个「还没停下来吗」探测器
    /** 上一刻玩家所在位置 —— 用来判断「身体到底有没有动」。 */
    @Nullable
    private static Vec3 lastPos;
    /** 上一刻到目标的距离 —— 用来判断「还在不在接近」。 */
    private static double lastDistance = Double.MAX_VALUE;
    /** 连续多少刻身体没动 / 距离没缩小。 */
    private static int blockedTicks;
    private static int stallTicks;
```

### (3) `begin(...)` 里重置

```diff
         AttackApproach.dashing = true;
         AttackApproach.nearTicks = 0;
+        AttackApproach.blockedTicks = 0;
+        AttackApproach.stallTicks = 0;
+        AttackApproach.lastPos = null;
+        AttackApproach.lastDistance = Double.MAX_VALUE;
```

### (4) `clearDash()` 里也重置

```diff
     private static void clearDash() {
         dashing = false;
         dashTicksLeft = 0;
         onArrive = null;
+
+        // 保护性原则的探测器也要清：下一次突进从零开始数
+        nearTicks = 0;
+        blockedTicks = 0;
+        stallTicks = 0;
+        lastPos = null;
+        lastDistance = Double.MAX_VALUE;
     }
```

### (5) `tick(...)` 里插入判定（**锚点：`够得着就已经能打中了，别墨迹。` 那一整块之后、`// 全向突进` 之前**）

```java
        // ── 保护性原则：接近这件事只要做不下去了，就当场停下来开打 ──
        //
        // 突进的目的是「快速把人带进自己的攻击范围」，不是「百分百贴身」。
        // 所以撞墙、卡住、目标在方块后面或头顶打转、目标跑得和自己一样快……
        // 不管因为什么，只要停下来就出手，绝不继续追。
        //
        // 没有这一条时，追不到的情况会一路追满 MAX_APPROACH_TICKS(20 刻) 才出手，
        // 表现就是「在地板下那个目标的头顶逗留一会儿」「贴着墙蹭半天」。
        // 用两个互相独立的迹象判断「停下来了」，任一成立都算：
        if (lastPos != null && player.position().distanceTo(lastPos) < DASH_BLOCKED_EPSILON) {
            blockedTicks++;                 // ① 身体几乎没动 → 被方块挡住了
        } else {
            blockedTicks = 0;
        }
        lastPos = player.position();

        if (distance >= lastDistance - DASH_PROGRESS_EPSILON) {
            stallTicks++;                   // ② 距离不再缩小 → 追不上（或目标一样快）
        } else {
            stallTicks = 0;
        }
        lastDistance = distance;

        if (blockedTicks >= DASH_BLOCKED_PATIENCE_TICKS
                || stallTicks >= DASH_STALL_PATIENCE_TICKS) {
            // hardStop = true：原地急停 + 立刻走「到位」那条路（解冻动画、结算伤害）
            finish(player, true);
            return;
        }
```

> **它和 `ARRIVE_PATIENCE_TICKS` 是两级兜底**，不冲突：
> 那个管「已经够得着、但进不了到位线」，这个管「根本没能再靠近」。
> 建议把上面这段放在 `ARRIVE_PATIENCE_TICKS` 那块的**下面**（到位 → 够得着 → 追不动，优先级从高到低）。

## 1.6 ⑤ 突进延后开始（`dashStartDelay`）

**要解决**：三阶 E 那种「起跳 → 人消失 → 化作细长螺旋 → 朝目标突刺」的招式，
位移在变身之后；现在是一按下就冻结动画冲出去，动作就断了。

26.2 那边是数据字段（`ActionStep.dashStartDelay`），你这边时序硬编码在
`MiyabiComboClient` 里，所以**推荐放在客户端那张表里**（改动最小）。

### (1) `ActionStateMachine` 加「动作序号」（延迟任务要用它判断有没有换招）

```diff
     /** 客户端延迟任务队列（1 tick 一步），被打断时整体清空。 */
     private static final List<ClientTask> CLIENT_TASKS = new ArrayList<>();
 
+    /** 动作序号：每次 {@link #changeState} 递增。 */
+    private static int actionSequence = 0;
+
+    /**
+     * 当前动作序号。
+     *
+     * <p>给「延迟 n 刻再做事」的任务用：任务排队时记下序号，执行时对不上就说明
+     * 中途换招了（闪避、被打断、接了下一段），这次任务应当作废。
+     * {@code resetToDefault} 会清空任务队列，但普通换招（{@code changeState}）不清。
+     */
+    public static int actionSequence() {
+        return actionSequence;
+    }
+
     private ActionStateMachine() {
     }
```

`changeState(...)` 里递增（锚点：`currentState = newState;` 那一段）：

```diff
         currentState = newState;
         currentPriority = priority;
         animationTick = totalTicks;
+        actionSequence++;
```

### (2) `MiyabiComboClient`：按段配「第几刻才开始冲」

```java
    /**
     * 这一段的突进从第几刻开始（0 = 按下即冻结冲出去）。
     *
     * <p>有些招式的前几帧本身就是动作（起跳 / 变身 / 化作长枪螺旋），
     * 位移发生在它们之后，所以要先播完这几刻再冻结。
     */
    private static int dashStartDelayFor(int stage) {
        return switch (stage) {
            // 举例：第 5 段是「升空变身 → 突刺」，先播 8 刻再冲
            case 5 -> 8;
            default -> 0;
        };
    }
```

`execute(...)` 里的突进分支改成：

```java
        if (player instanceof LocalPlayer local && target != null) {
            boolean wantsDash = style.wantsDash() && AttackApproach.needsDash(local, target, attackRange);
            int dashDelay = dashStartDelayFor(stage);

            if (wantsDash && dashDelay > 0) {
                // 前 n 刻先播变身：动画照常走，人也还在原地（不冻结、不排音效）
                beginStage(player, stage);

                final int sequence = ActionStateMachine.actionSequence();

                ActionStateMachine.queueClientWork(dashDelay, () -> {
                    // 中途换招了 → 这次突进作废，别再凭空补一次
                    if (ActionStateMachine.actionSequence() != sequence) {
                        return;
                    }

                    // 目标可能已经死了、也可能自己走过来了 → 重新判一次
                    LivingEntity fresh = CombatTargeting.current(local);
                    LivingEntity now = fresh != null ? fresh : target;

                    if (now != null && AttackApproach.needsDash(local, now, attackRange)) {
                        AttackApproach.begin(local, now, style, attackRange, () -> {
                            ActionStateMachine.resumeFromApproach(ticksFor(stage));
                            NetworkManager.sendActionRequestToServer(
                                    NetworkManager.ActionKind.ATTACK, stage, false, now);
                        });
                    } else {
                        // 不用冲了：时间零点同样是第 n 刻，直接发包
                        ActionStateMachine.resumeFromApproach(ticksFor(stage));
                        NetworkManager.sendActionRequestToServer(
                                NetworkManager.ActionKind.ATTACK, stage, false, now);
                    }
                });
                return;
            }

            if (wantsDash) {
                // 默认（0 刻）：按下即冻结，摆臂/音效都等到位那一刻
                beginStage(player, stage);
                AttackApproach.begin(local, target, style, attackRange, () -> {
                    ActionStateMachine.resumeFromApproach(ticksFor(stage));
                    NetworkManager.sendActionRequestToServer(
                            NetworkManager.ActionKind.ATTACK, stage, false, target);
                });
                return;
            }

            AttackApproach.faceTarget(local, target, style);
            AttackApproach.stepToward(local, target, attackRange);
        }
```

> **`actionSequence` 那道校验必须有**：延迟任务挂在静态队列上，
> `resetToDefault` 会清队列，但普通换招（`changeState`）不会 ——
> 不校验的话，前 n 刻里接了别的招，n 刻后还会凭空补一次突进。
>
> **时间零点跟着推后**：不管最后有没有真的冲（也可能 n 刻后目标已经走进攻击距离了），
> 「解冻 / 出手」都发生在第 n 刻，服务端的伤害计时也从那一刻起算。
> 所以各段的硬编码刻数**不用改** —— 它们本来就是「相对动作开始」的。

### (3) 如果你更想把参数放进 `ApproachParams`（可选，改动大一点）

`ApproachParams` 是 10 个分量的 record，加第 11 个分量要把所有 `withXxx` 都补一遍。
要做的话：加 `int dashStartDelay` 分量 → 每个 `withXxx` 末尾补 `dashStartDelay` →
加 `withDashStartDelay(int)` → `MELEE/RANGED` 两个预设填 `0`。
好处是「形态」跟其它突进参数住在一起；坏处是**每段不同还得在 `styleFor(stage)` 里写**，
并不会比 `dashStartDelayFor(stage)` 更省事 —— 所以推荐上面的写法。

## 1.7 ⑥ 可选（推荐）：突进期间别让锁流失，到位时重新起算

这条不是索敌，但和「突进被中途取消」是同一类 bug，一起补上：
**飞过去本身就是执行期**，锁不该在飞行途中倒数完 ——
否则飞得久一点（甚至只是 5 刻）就会出现「刚落位，一个移动输入就把这一刀连同还没发出的服务端请求吞掉」。

**`ActionStateMachine.onClientTick` 里，倒计时那一块包进 `if (!approachFrozen)`**：

```diff
-        // 倒计时：前摇/硬直、移动锁
-        if (actionLockFrames > 0) {
-            actionLockFrames--;
-        }
-        if (movementLockFrames > 0) {
-            movementLockFrames--;
-        }
+        // 倒计时：前摇/硬直、移动锁。
+        // ⚠️ 突进期间冻结：突进属于执行期，最长可以飞 MAX_APPROACH_TICKS 刻；
+        //    让锁在飞行途中流失，会出现「人还在飞，锁已经过期」→
+        //    一个移动输入就把这一刀连同还没发出的服务端请求吞掉。
+        if (!approachFrozen) {
+            if (actionLockFrames > 0) {
+                actionLockFrames--;
+            }
+            if (movementLockFrames > 0) {
+                movementLockFrames--;
+            }
+        }
```

**`resumeFromApproach` 加两个参数**（到位时把硬直与定身一起拨回满值）：

```diff
-    public static void resumeFromApproach(int totalTicks) {
-        approachFrozen = false;
-        approachJustUnfrozen = true;
-        animationTick = Math.max(1, totalTicks);
-    }
+    /** 兼容旧调用：不动锁。 */
+    public static void resumeFromApproach(int totalTicks) {
+        resumeFromApproach(totalTicks, 0, 0);
+    }
+
+    /**
+     * 突进到位：解冻动画 + 整段执行期（动画时钟、硬直、定身）一起重新起算。
+     *
+     * <p>为什么锁也要重算：<b>飞过去本身就是执行期的一部分</b>。
+     * 飞了 10 刻的话，按「从按下那一刻算」的锁早就过期了，
+     * 于是刚接上的动画会被一个移动输入取消，连还没结算的伤害一起丢掉。
+     *
+     * @param lockFrames   到位后的硬直刻数（同 changeState 的 lockFrames）
+     * @param movementLock 到位后的定身刻数
+     */
+    public static void resumeFromApproach(int totalTicks, int lockFrames, int movementLock) {
+        approachFrozen = false;
+        approachJustUnfrozen = true;
+        animationTick = Math.max(1, totalTicks);
+        actionLockFrames = Math.max(0, lockFrames);
+        movementLockFrames = Math.max(0, movementLock);
+    }
```

调用处（`MiyabiComboClient` 的两个到位回调）也一起换：

```diff
-                            ActionStateMachine.resumeFromApproach(ticksFor(stage));
+                            ActionStateMachine.resumeFromApproach(
+                                    ticksFor(stage), 段硬直刻数(stage), 段定身刻数(stage));
```

> 不想动锁的话这一步可以**整条跳过**（保持 `resumeFromApproach(总刻数)`），
> 但要接受「飞得久一点就容易被自己的移动输入取消」这个老问题。

## 1.8 怎么验证

| 现象 | 期望 |
|---|---|
| 隔着一堵墙/两三个方块站着怪 | **不锁**（准星不粘上去），走过去、绕过去到 6 格内才锁 |
| 站在一个方块墙边，怪紧贴墙另一侧 | **照锁**（拦停点够得着就被允许），贴上去撞墙立刻开打 |
| 怪在地板下面（隔着 2~3 格） | **不锁**；站在它正上方按攻击应该是正常空挥，不会出现「悬停一会儿才挥」 |
| 怪飞在头顶、中间有天花板 | 不锁；**中间没有遮挡**时照常锁、照常垂直追上去 |
| 追到一半被挡/被挤/目标瞬移 | 最多 **2 刻**内停手开打（表现为「冲了一下就打」，不再逗留到 20 刻超时） |
| 配了 `dashStartDelay` 的段 | 前 n 刻动画正常播（人在原地），第 n 刻才开始冲；期间闪避/接下一段不会补出一次突进 |
| 远程招式 | 行为**完全不变**（隔着墙也锁，不追人） |

排查时看这几条日志/现象：
- 完全没锁 → 先确认 `Params.chase` 是不是 true（近战走 `forChase`）、
  以及 `ChaseReach` 的采样线是不是被自己脚下的方块挡了（比如站在 1 格高的台阶边缘）。
- 锁了但站着不动 → 看 `AttackApproach.needsDash` 的判定和 `MAX_APPROACH_TICKS`。

## 1.9 注意事项

1. **每条只在「会突进的招式」上生效**：`chase = false`（远程 / `Params.DEFAULT`）时
   `ChaseReach` 一次都不会跑，行为和基线完全一致。
2. **可行性判定会多几次射线**：索敌时每个候选 2 条、保持时每刻 2 条（只有 1 个目标）。
   只在近战招式上跑；实测开销可忽略。
3. **`ChaseReach` 的容差是可以调的**：觉得「太容易不锁」就加大 `STOP_SLACK`（0.5 → 1.0）；
   觉得「地板下还是被锁」就减小 `VERTICAL_REACH_FACTOR`（0.75 → 0.6）。
4. **和「吸附」的分工没变**：吸附还是「出手瞬间推一小步」，`ChaseReach` 只管「值不值得追」。
5. 本条**不改**任何既有行为的老路径（远程索敌、吸附、转向、突进速度/到位线都没动）。

---

# 第 2 条 · 「在脚下 + 头上有盖子」一票否决

> 承接第 1 条。第 1 条那套判定对「目标在<b>正下方、中间隔着一整层地板</b>」还不够狠：
> 拦停点到目标的直线距离可能只有 2~3 格，落在攻击距离里 → 照样会锁、照样会追，
> 追过去就停在地板上方干等（因为高度差永远补不平）。

## 2.1 本次改了什么（26.2 这边）

**现象**：房子有 1L 和 -1L，-1L 两格高、天花板就是 1L 的地板，里面刷了怪。
玩家站在 1L 上 → 怪被索敌到 → 冲过去只会站在地板上方（或者干脆卡住），
打也打不到。

**结论**：这种是**完整隔断**，连锁都不该锁。

## 2.2 ① `ChaseReach` 加一票否决

在 `canCloseIn` 的采样循环里，**放在 `isReachableFrom` 之前**（它是否决票，优先级更高）：

```diff
             if (hit.getType() == HitResult.Type.MISS) {
                 continue; // 这一层通得过
             }
 
+            // ★ 目标在脚下、而且「头顶那层实心方块」才是拦停它的东西 → 完整隔断。
+            //   典型：房子 1L 地板、-1L 两格高的小间里刷了怪 —— 它就在你正下方，
+            //   中间隔着一整层地板。这种连锁都不该锁（打不到，追过去也只是站在地板上方干等）。
+            if (isSealedBelow(player, target, hit.getLocation())) {
+                return false;
+            }
+
             if (isReachableFrom(hit.getLocation(), goal, range)) {
                 continue; // 被拦下了，但拦停的位置本来就够得着 → 照追
             }
 
             return false;
```

② 同一个类里新增这个方法（放在 `isReachableFrom` 后面）：

```java
    /**
     * 目标是不是被「盖」在你脚下 —— 拦停点明显高于目标，且目标头顶到拦停点之间是实心方块。
     *
     * <p>判据分两步，故意都很保守，避免误杀：
     * <ol>
     *   <li>拦停点不比目标高多少（≤ 0.5 格）→ 那只是「旁边有堵墙」，不是「头上有盖子」，直接放过；</li>
     *   <li>从目标头顶往上打到拦停点下方：打不到方块 → 放过（说明它只是贴着墙，绕一下就能打）。</li>
     * </ol>
     *
     * <p>「站在悬崖上、目标在崖下」这种不会被误判：崖壁在它<b>旁边</b>，
     * 它头顶到崖顶那条竖线是空的 → 第 2 步打不到方块 → 照常走后面的「拦停点够不够得着」判断。
     */
    private static boolean isSealedBelow(Player player, LivingEntity target, Vec3 stopPoint) {
        if (stopPoint.y <= target.getY() + 0.5) {
            return false; // 拦停点就在目标这一层 → 是墙不是盖子
        }

        Vec3 from = target.position().add(0, target.getBbHeight() + 0.05, 0);
        Vec3 to = new Vec3(target.getX(), stopPoint.y - 0.05, target.getZ());
        if (from.y >= to.y) {
            return false; // 目标比拦停点还高（基本不会发生），不判
        }

        BlockHitResult hit = player.level().clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() != HitResult.Type.MISS;
    }
```

**为什么要「拦停点明显高于目标」这一步**：拦停点其实是两条射线里第一条被挡住的位置，
可能是**旁边的墙**（跟目标同高），也可能是**头顶的地板**（明显更高）。
只有后者才叫「盖住了」；前者该交给第 1 条的「拦停点够不够得着」去判。

## 2.3 怎么验证

| 场景 | 期望 |
|---|---|
| 站 1L 地板上，-1L（两格高）里有怪 | **完全不锁**（准星不粘），也不再冲过去悬停 |
| 怪在 1L 的同层、隔着一堵 1 格墙 | 仍然**照锁照打**（第 1 条的规则：拦停点够得着就追） |
| 站在悬崖边，崖下 2~3 格有怪、头顶没东西 | **照锁**（旁边是崖壁不是盖子），冲一下/够得着就打 |
| 怪在隧道里、你站在隧道正上方的地面上 | 不锁（完整隔断） |
| 你在隧道口、怪在隧道里 3 格远（直线通畅） | 照锁（路径通 → 根本不会走到这条判定） |

## 2.4 注意事项

1. 这一条只在**路径被挡**、且拦停点在目标上方时才可能触发；平地上永远不会命中。
2. 「隔着一格墙能不能打到」的**命中遮挡**仍然没做（不好判断就先不做）——
   现在只有「完全盖住」这一种会被拦下来。
3. 判据只用**方块**（`Block.COLLIDER`），不涉及实体。
4. 不改第 1 条的任何参数（`STOP_SLACK` / `VERTICAL_REACH_FACTOR` 都不用动）。

---

# 第 3 条 · 出招期间的身体朝向：无目标不转向

## 3.1 本次改了什么（26.2 这边）

**现象**：视角独立做完之后，走路那套是对的（转鼠标不转身、按 S 正对镜头后退），
但**一放普攻角色就被强行掰向镜头正前方** —— 哪怕根本没锁到人、刚才明明面朝别处。

**原因**（两条规则撞在一起，不是 bug 是相互作用）：

| 环节 | 事实 |
|---|---|
| 动作位移 | `moves` 的冲量是服务端**沿视线方向**给的（`ServerActionExecutor.applyMove` 读 `getLookAngle()`） |
| 身体朝向 | 「视角独立」下身体跟的是**实际位移方向**（`LivingEntityTickHeadTurnMixin` 按 `getX()-xo` 算角度） |

于是「沿视线冲出去」被位移规则读成「他在往那边走」，身体当场转到镜头正前方。
另外还有个顺序 bug：目标**在攻击距离内**时 `faceTarget(...)` 排在 `play(...)` 前面，
而 `play → changeState → AttackApproach.cancel()` 会把刚登记的转向清掉 ——
表现就是「锁到人了却一点都没转」。

**目标行为**：

```
出招时索敌到了  → 身体转向索敌目标
出招时没索敌到  → 身体保持原朝向（一个字段都不改）
例外            → 招式可以要求「身体跟随镜头」（持续重击：木偶那种）
```

## 3.2 ① 新增数据枚举：`ActionBodyFacing`

路径：`src/main/java/net/luoshu/imaginarybranch/combat/action/data/ActionBodyFacing.java`
（26.2 在 `core/system/combat/action/data/`；**纯数据枚举，别放任何客户端引用**）

```java
public enum ActionBodyFacing {
    /** 索敌到 → 转向目标；没索敌 → 保持原朝向。出招默认。 */
    TARGET,
    /** 例外：身体由鼠标（镜头）驾驶，镜头紧跟着身体。持续重击用。 */
    CAMERA,
    /** 不接管，照常态「身体跟随实际位移方向」。 */
    MOVEMENT;

    public boolean takesOverBodyFacing() {
        return this != MOVEMENT;
    }
}
```

## 3.3 ② `ActionStep` 加一个字段 + 两个便捷写法

```diff
+        /** 这一段期间身体朝向听谁的；默认 TARGET（索敌到转向目标，没索敌保持）。 */
+        public ActionBodyFacing bodyFacing = ActionBodyFacing.TARGET;
```

```java
        public ActionStep withBodyFacing(ActionBodyFacing value) {
            this.bodyFacing = value == null ? ActionBodyFacing.TARGET : value;
            return this;
        }

        public ActionStep withCameraFacing() {
            return withBodyFacing(ActionBodyFacing.CAMERA);
        }
```

## 3.4 ③ `AttackApproach`：登记接管 + 自动过期

```diff
+    /** 本次动作期间身体朝向听谁的；MOVEMENT = 不接管（常态）。 */
+    private static ActionBodyFacing bodyFacing = ActionBodyFacing.MOVEMENT;
+
+    /**
+     * 登记「本次动作期间身体朝向听谁的」—— 必须在 changeState(...) **之后**调，
+     * 否则会被 changeState 内部那次 cancel() 清掉。
+     */
+    public static void holdBodyFacing(@Nullable ActionBodyFacing policy) {
+        ActionBodyFacing next = policy == null ? ActionBodyFacing.MOVEMENT : policy;
+        bodyFacing = next;
+        if (next == ActionBodyFacing.CAMERA) {
+            ThirdPersonCamera.setFollowBody(true);
+        }
+    }
+
+    public static boolean holdsBodyFacing()        { return bodyFacing.takesOverBodyFacing(); }
+    public static ActionBodyFacing bodyFacing()    { return bodyFacing; }
+    public static boolean wantsCameraFacing()      { return bodyFacing == ActionBodyFacing.CAMERA; }
```

`cancel()` 里收掉（动作结束的唯一出口，**必须放在这里**，调用方不用自己关）：

```diff
     public static void cancel() {
         clearDash();
         facing = false;
         facingTicksLeft = 0;
+
+        if (bodyFacing == ActionBodyFacing.CAMERA) {
+            ThirdPersonCamera.setFollowBody(false);
+        }
+        bodyFacing = ActionBodyFacing.MOVEMENT;
+
         ActionStateMachine.setApproachFrozen(false);
     }
```

## 3.5 ④ 身体朝向判断加一档 `ACTION`

`ThirdPersonCamera.BodyFacing` 加一个取值，并让 `bodyFacing(entity)` 返回它：

```diff
     public enum BodyFacing {
         VANILLA,
         INDEPENDENT,
-        FOLLOW
+        FOLLOW,
+        /** 动作接管：朝向由 AttackApproach 说了算（索敌到转向目标，没索敌保持）。 */
+        ACTION
     }
```

```diff
-        return followBody ? BodyFacing.FOLLOW : BodyFacing.INDEPENDENT;
+        if (followBody) {
+            return BodyFacing.FOLLOW;      // CAMERA 那一档既是「接管」也是「跟随」，跟随优先
+        }
+        return AttackApproach.holdsBodyFacing() ? BodyFacing.ACTION : BodyFacing.INDEPENDENT;
```

两处配套（都不改逻辑，只是别去抢方向盘 / 别拽镜头）：

```diff
         // ② 跟随只在持续型招式期间有效
-        if (followBody && !ActionStateMachine.currentStateLoops()) {
+        if (followBody && !ActionStateMachine.currentStateLoops() && !AttackApproach.wantsCameraFacing()) {
             setFollowBody(false);
         }
```

```diff
             // 自动归位：按着前进/侧移 + 1.5 秒没人为转视角才缓缓归位
-            if (player.tickCount - lastManualTick < ALIGN_DELAY_TICKS || !shouldAlign(player)) {
+            if (player.tickCount - lastManualTick < ALIGN_DELAY_TICKS || !shouldAlign(player)
+                    || facing == BodyFacing.ACTION) {
                 alignWeight = 0.0F;
                 lastYaw = current;
                 return;
             }
```

> 自动归位是**镜头转向身体**，动作接管期间身体可能正转向索敌目标 ——
> 不跳过的话就成了「一攻击镜头被拽到目标方向」，与「镜头 100% 归玩家」冲突。

## 3.6 ⑤ `tickHeadTurn` 混音：不需要改逻辑

那一档本来就只处理 `INDEPENDENT`：

```java
if (facing == ThirdPersonCamera.BodyFacing.INDEPENDENT && self.attackAnim <= 0.0F) {
    ... 身体朝实际位移方向 ...
}
// 其余（含新增的 ACTION / FOLLOW）直接落到下面的 ci.cancel()：身体一个字段都不动
```

**`ACTION` 什么都不做就是它的全部实现** —— 补注释即可，别再加分支。

## 3.7 ⑥ 动作启动处：先起状态、再转向、再登记接管

锚点：26.2 是 `ResourceDrivenActionHandler.engageAndPlay`；
1.21.1 是你的 `MiyabiComboClient.execute(...)`（**顺序是这条的关键**）。

```java
// 1) 不是本机玩家 / 没索敌到目标 → 只放动作，一点方向都不改
if (!(player instanceof LocalPlayer local) || target == null) {
    play(player, def, priority);            // 或你的 beginStage(...)
    holdBodyFacing(player, step);
    dispatch.accept(target);
    return;
}

// 2) 要突进：先起状态，再登记接管，最后才 begin（begin 内部会 startFacing）
if (wantsDash) {
    play(player, def, priority);
    holdBodyFacing(player, step);
    AttackApproach.begin(local, target, engagement, attackRange, () -> { ...到位回调... });
    return;
}

// 3) 目标已在攻击距离内：**先 play 再 faceTarget**
play(player, def, priority);
holdBodyFacing(player, step);
AttackApproach.faceTarget(local, target, engagement);   // ← 顺序反了会被 changeState 的 cancel 清掉
AttackApproach.stepToward(local, target, attackRange, engagement);
dispatch.accept(target);
```

`holdBodyFacing` 小工具（客户端才有意义）：

```java
private static void holdBodyFacing(Player player, ActionStep step) {
    if (!(player instanceof LocalPlayer)) {
        return;
    }
    ActionBodyFacing mode = step.bodyFacing == null ? ActionBodyFacing.TARGET : step.bodyFacing;
    if (mode.takesOverBodyFacing()) {
        AttackApproach.holdBodyFacing(mode);
    }
}
```

**持续重击那条路**（26.2: `beginSustainedChargedAttack`）把原来写死的
`ThirdPersonCamera.setFollowBody(true)` 换成同一套登记，例外就变成「数据说了算」：

```diff
     ActionStateMachine.changeState(def.animationName(), 2, totalTicks, step.protectDuration, 0, 0, true);
-    ThirdPersonCamera.setFollowBody(true);
+    holdBodyFacing(player, step);      // 数据写 CAMERA → 身体跟随镜头
     ActionServer.performChargedAttackToServer(CombatTargeting.current(player));
```

## 3.8 ⑦ 把例外写进数据（26.2: `ClaymoreSkill.buildSustainedChargedAttack`）

```diff
         ).withEngagement(Engagement.melee().withDash(false).withAdhesion(0, 0))
-         .withLoopAnimation(true);
+         .withLoopAnimation(true)
+         .withCameraFacing();       // 例外：持续重击期间身体跟随镜头
```

> 一句话：**默认 TARGET（无目标不转向），例外在数据里显式声明。**

## 3.9 怎么验证

| 场景 | 期望 |
|---|---|
| 空挥（附近没有怪），角色面朝侧面 | **一点不转**，保持原朝向；位移照旧沿视线冲出去 |
| 背对怪、视角里没怪 → 空挥 | 不转（索敌没锁到）；动画照播 |
| 视角里有怪（索敌范围内）→ 普攻/战技/爆发 | 身体**转向那只怪**，镜头一动不动 |
| 目标已在攻击距离内（不突进） | **照样转过去**（这是修掉的顺序 bug：以前这一档完全不转） |
| 侧向走位时放普攻 | 身体不再被位置冲量掰到镜头方向 |
| 木偶 / 大剑**持续重击**（按住左键） | 身体**跟随镜头**（例外照旧），镜头贴着背后；松手立刻落回常态 |
| 闪避 / 走路 / 跑步 | 与之前完全一致（这两条路没接管） |

## 3.10 注意事项

1. **接管是自动过期的**：`changeState` / `resetToDefault` 都会走 `AttackApproach.cancel()`；
   所以登记**必须**在起状态之后，否则当场被清。
2. `ActionBodyFacing` 是**纯数据枚举**，只放 `action/data` 包，别引客户端类
   （`core`/`content` 侧引用客户端类是项目红线）。
3. 想保留老行为（身体跟着位移走）的招式，在数据里显式写 `MOVEMENT`。
4. 没走 `engageAndPlay` 的那几条路（**闪避**、**下落攻击** `PlungeAttack`）不接管，
   行为与本次改动前一致；哪天想让下落攻击也不转，就在它自己的入口按同样方式登记。
5. 残影：动作用的 `moves` 冲量一般几十刻内就衰减到走路阈值以下，
   所以动作结束后身体不会被残余位移再掰一次；真有招式把冲量排到动作末尾，
   就在那一段上把它标成 `MOVEMENT`（那是「人确实往那边走」）。

## 3.11 ⑧ 位移 / 技能方向统一走「角色朝向」

**这是本条的核心**：不只是身体朝向，**攻击往哪走、打哪，看的都是角色面朝哪，不是镜头**。

新增 `core/system/combat/CombatAim`：

```java
public static float yaw(Entity entity)          // 服务端读 BODY_YAW_ATTACHMENT；否则 yBodyRot / getYRot
public static Vec3  direction(Entity entity)    // 朝向 + 视线俯仰（竖直仍归镜头）
public static Vec3  horizontal(Entity entity)
```

替换点（原来全都读视线角）：

| 位置 | 原来 | 现在 |
|---|---|---|
| `ServerActionExecutor.applyMove` | `getLookAngle()` 的水平投影 | `CombatAim.horizontal(entity)` |
| `ServerActionExecutor.previewHitTargets` | `source.getLookAngle()` | `CombatAim.direction(source)` |
| `ElementalAttackSweep.forPlayer` | `player.getLookAngle()` | `CombatAim.direction(player)` |
| `TargetSeeker.collectLineOfSight` / `isInForwardTunnel` | `source.getLookAngle()` | `CombatAim.direction(source)` |
| `HorizonEndVec3.execute` | look 的水平投影 | `CombatAim.horizontal(player)` |
| 角色技能判定框 / AoE 中心：`VesnaSkill`×4、`ShenheSkill`、`RaidenShogunSkill`、`ArlecchinoSkill`、`VodyanitsaSkill`×2、`LinweiyunSkillLogic` | `player.getLookAngle()` | `CombatAim.direction(player)` |
| `VesnaAttackProjectile` 生成角 / 攻击锁角 | `ownerPlayer.getYRot()` | `CombatAim.yaw(ownerPlayer)` |
| `ShieldService.grant` 盾朝向 | `entity.getYRot()` | `CombatAim.yaw(entity)` |
| `BurstDive.desiredLanding` 无目标落点 | `player.getLookAngle()` | `CombatAim.horizontal(player)` |

- **竖直（俯仰）保持视线**：身体没有俯仰角，`moveAllowsVertical` 与技能落点的 Y 还是跟着抬头/低头走。
- **索敌本身仍按视线**（`CombatTargeting` 的索敌锥、`TargetSeeker.hasVisionOf`）：
  玩家用镜头瞄人、按下攻击，身体才转向目标。索敌要是也按身体朝向，
  就会出现「看着敌人却锁不上」。
- 前提是 `BODY_YAW_ATTACHMENT` 在同步（见 `BodyYawSync`）：没收到过就退回视线角，
  行为等于改动前 —— 所以这一条**依赖身体朝向的联机同步先落地**。


