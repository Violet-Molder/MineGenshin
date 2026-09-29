package com.linweiyun.genshin.core.system.combat.targeting;

import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 软锁定索敌 —— 「玩家现在瞄着谁」。
 *
 * <h2>为什么不用 {@code TargetSeeker} 的硬锁</h2>
 * 旧的 {@code LOCKED_TARGET} 附件事后校验只有「过期时间」，一旦锁错就得等它过期，
 * 或者靠丢视角也很难主动丢掉，代价太高。这套改成了 AAA 常见的<b>迟滞 + 宽限</b>模型：
 * 锁得容易、丢得也容易，但不会在两个近处目标之间来回抖。
 *
 * <h2>迟滞（防止 A/B 互抖）</h2>
 * <ul>
 *   <li><b>索敌</b>：距离 ≤ {@link #ACQUIRE_RANGE}，且在视角 ±{@link #ACQUIRE_ANGLE}° 内</li>
 *   <li><b>保持</b>：距离 ≤ {@link #KEEP_RANGE}（比索敌远），视角 ±{@link #KEEP_ANGLE}°（比索敌宽）</li>
 *   <li>已经锁着的时候，只有候选的分数比当前目标高出 {@link #SWITCH_MARGIN} 才换人</li>
 * </ul>
 * 索敌圈比保持圈小，就是经典的双阈值迟滞 —— 站在两个怪中间时不会一会儿锁 A 一会儿锁 B。
 *
 * <h2>丢锁（不会绑死）</h2>
 * <ul>
 *   <li>目标死亡/卸载 → 立刻丢</li>
 *   <li>超出保持范围、或转身背对超过 {@link #KEEP_ANGLE}° → 累计 {@link #LOSE_GRACE_TICKS} 刻后丢</li>
 *   <li><b>追不上了</b>（隔着方块、在地板下、飞在天花板上面）→ 同样进宽限计时，
 *       见 {@link ChaseReach}；只有会突进的招式才判（{@link Params#chase()}）</li>
 *   <li>连续 {@link #IDLE_RELEASE_TICKS} 刻没有发起过攻击 → 自动松锁</li>
 *   <li>{@link #release} 可以随时手动丢</li>
 * </ul>
 *
 * <h2>双端</h2>
 * 客户端（{@code "C:"}）负责转向与突进判定，要求零延迟；
 * 服务端（{@code "S:"}）的锁由攻击请求包喂进来，供召唤物读取「主人正在打谁」。
 * 两边各自维护一份，互不干扰。
 *
 * <h2>参数是「每一招」带进来的</h2>
 * 上面那几个常量只是<b>默认值</b>（近战普攻）。真正参与判定的是一份
 * {@link Params}：招式通过 {@link #acquire(Player, Params)} 把「这一招够得着多远、
 * 这一招想锁多远」喂进来，锁上之后保持圈也按<b>这一招</b>的参数校验。
 *
 * <p>这样远程招式就能做到「索敌 = 攻击距离」：够不着的不锁、也不突进，
 * 而近战招式仍然是「索敌 &gt; 攻击距离」，差额交给突进补。
 */
public final class CombatTargeting {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.COMBAT);

    // ==================== 迟滞参数（想调手感就改这里） ====================

    /**
     * 首次索敌的最大距离（格）。
     *
     * <p>近战不需要很远的索敌 —— 索敌距离只要比攻击距离大一点，
     * 差额交给突进去补就够了。设太大反而会锁到远处不打你的怪。
     */
    public static final double ACQUIRE_RANGE = 6.0;
    /** 保持锁定的最大距离（格）—— 比索敌远，避免目标稍微走开就掉锁。 */
    public static final double KEEP_RANGE = 9.0;
    /** 索敌视角半角（度）—— 正前方这个锥形内才索敌。 */
    public static final double ACQUIRE_ANGLE = 55.0;
    /** 保持锁定的视角半角（度）—— 比索敌宽，转头不至于立刻掉锁。 */
    public static final double KEEP_ANGLE = 80.0;
    /** 换目标需要的分数优势比例（1.25 = 新目标要明显更好才换）。 */
    public static final double SWITCH_MARGIN = 1.25;
    /** 违反保持条件后还能撑几刻（防抖宽限）。 */
    public static final int LOSE_GRACE_TICKS = 10;
    /** 连续多少刻没攻击就自动松锁（5 秒）。 */
    public static final int IDLE_RELEASE_TICKS = 100;

    // ==================== 逐招式参数 ====================

    /** {@link Params#DEFAULT} 用的攻击距离占位值（不判可行性，用不到它）。 */
    public static final double DEFAULT_PROBE_RANGE = 3.0;

    /**
     * 一次索敌/保持用的参数 —— <b>由招式带进来</b>，不是全局写死的。
     *
     * <p>典型两组：
     * <pre>
     * 近战：acquire 6 / keep 9   → 先锁上，差额靠突进补（要判「追不追得上」）
     * 远程：acquire = 攻击距离   → 够得着才锁，锁上也不突进（不判路径，隔着墙也锁）
     * </pre>
     *
     * @param acquireRange 首次索敌距离（格）
     * @param acquireAngle 索敌视角半角（度）
     * @param keepRange    保持锁定的距离（格）
     * @param keepAngle    保持锁定的视角半角（度）
     * @param attackRange  这一招的生效攻击距离（格）—— 可行性判定用它
     * @param chase        要不要做「追击可行性」判定（{@link ChaseReach}）。
     *                     会突进的近战招式 = true；远程招式 = false
     * @param policy       目标筛选策略
     */
    public record Params(double acquireRange, double acquireAngle,
                         double keepRange, double keepAngle,
                         double attackRange, boolean chase, TargetPolicy policy) {

        /** 不判可行性的默认（召唤物 / 工具代码用）：只按距离与视角筛。 */
        public static final Params DEFAULT = new Params(
                ACQUIRE_RANGE, ACQUIRE_ANGLE, KEEP_RANGE, KEEP_ANGLE,
                DEFAULT_PROBE_RANGE, false, TargetPolicy.DEFAULT);

        public Params {
            // 保持圈比索敌圈小的话会「刚锁上就掉」，这里直接兜住
            keepRange = Math.max(keepRange, acquireRange);
            keepAngle = Math.max(keepAngle, acquireAngle);
            policy = policy == null ? TargetPolicy.DEFAULT : policy;
        }

        /**
         * 近战招式用：索敌 &gt; 攻击距离，差额交给突进 —— 所以要判「追不追得上」。
         *
         * <p>判定会挡掉「隔着两三个方块」「目标在地板下/天花板上面」这类追不到的目标，
         * 见 {@link ChaseReach}。
         */
        public static Params forChase(double attackRange) {
            return new Params(ACQUIRE_RANGE, ACQUIRE_ANGLE, KEEP_RANGE, KEEP_ANGLE,
                    Math.max(0.5, attackRange), true, TargetPolicy.DEFAULT);
        }

        /** 远程招式用：索敌距离 = 攻击距离，保持圈只多留一点余量防抖。 */
        public static Params forRange(double attackRange) {
            double range = Math.max(0.5, attackRange);
            return new Params(range, ACQUIRE_ANGLE, range + RANGED_KEEP_MARGIN, KEEP_ANGLE,
                    range, false, TargetPolicy.DEFAULT);
        }
    }

    /** 远程招式保持圈的余量（格）——纯粹是防抖，不参与「够不够得着」的判断。 */
    public static final double RANGED_KEEP_MARGIN = 2.0;

    // ==================== 状态 ====================

    /**
     * 一个玩家一份。key = {@code "C:"|"S:" + UUID}
     * —— 单机时客户端和服务端同 UUID，必须用 side 区分。
     */
    private static final Map<String, State> STATES = new ConcurrentHashMap<>();

    private static final class State {
        int targetId = 0;
        /** 连续多少刻不满足保持条件。 */
        int invalidTicks = 0;
        /** 最后一次发起攻击的游戏刻。 */
        long lastAttackTick = Long.MIN_VALUE;
        /** 当前目标的策略，换锁时跟着换。 */
        TargetPolicy policy = TargetPolicy.DEFAULT;
        /** 当前这一招的参数；保持判定用它，不用全局常量。 */
        Params params = Params.DEFAULT;
    }

    private CombatTargeting() {
    }

    // ==================== 查询 ====================

    /** 玩家当前锁定的目标；没有就返回 null。 */
    @Nullable
    public static LivingEntity current(Player player) {
        State state = states().get(key(player));
        if (state == null || state.targetId == 0) {
            return null;
        }
        Entity entity = player.level().getEntity(state.targetId);
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    public static boolean isLocked(Player player) {
        return current(player) != null;
    }

    /** 到锁定目标的距离；没锁定返回 {@link Double#MAX_VALUE}。 */
    public static double distanceToTarget(Player player) {
        LivingEntity target = current(player);
        return target == null ? Double.MAX_VALUE : player.distanceTo(target);
    }

    /** 锁定目标的水平方向（已归一化）；没锁定返回 null。 */
    @Nullable
    public static Vec3 directionToTarget(Player player) {
        LivingEntity target = current(player);
        if (target == null) {
            return null;
        }
        Vec3 delta = target.position().subtract(player.position());
        Vec3 horizontal = new Vec3(delta.x, 0, delta.z);
        return horizontal.lengthSqr() < 1.0E-6 ? null : horizontal.normalize();
    }

    // ==================== 索敌 / 换锁 ====================

    /**
     * 默认参数的索敌 —— 等价于 {@code acquire(player, Params.DEFAULT)}，
     * 给「不关心参数、只要最近那个敌人」的调用方（召唤物、工具代码）用。
     */
    @Nullable
    public static LivingEntity acquire(Player player) {
        return acquire(player, Params.DEFAULT);
    }

    /**
     * 按<b>这一招</b>的参数索敌：已经锁着且仍然有效时优先保持，
     * 只有别的目标明显更好（分数高出 {@link #SWITCH_MARGIN}）才换。
     *
     * <p>调用时会把这套参数记到玩家状态上，随后每刻的 {@link #tick(Player)}
     * 保持校验也按它算 —— 所以「远程招式锁得近、近战招式锁得远」不会互相打架。
     *
     * @return 锁定的目标；范围内没有合适目标时返回 null（不会清掉已有锁）
     */
    @Nullable
    public static LivingEntity acquire(Player player, Params params) {
        Params p = params == null ? Params.DEFAULT : params;
        State state = states().computeIfAbsent(key(player), k -> new State());
        state.params = p;

        LivingEntity kept = current(player);
        if (kept != null && isValid(player, kept, state)) {
            return kept;
        }

        LivingEntity best = findBest(player, p);
        if (best == null) {
            return null;
        }

        // 已经有锁（虽然这一刻判定无效，但还没超宽限）时不轻易换人
        if (kept != null && state.invalidTicks <= LOSE_GRACE_TICKS) {
            double keptScore = scoreOf(player, kept, p.policy());
            double bestScore = scoreOf(player, best, p.policy());
            if (bestScore < keptScore * SWITCH_MARGIN) {
                return kept;
            }
        }

        lockOn(state, best, p.policy());
        return best;
    }

    /** 攻击瞬间把当前候选钉成锁定目标（钉多久由后续校验决定，不是死绑）。 */
    public static void lock(Player player, @Nullable Entity target) {
        lock(player, target, null);
    }

    /** 攻击瞬间钉住目标，并记下这一招的参数（保持校验用）。 */
    public static void lock(Player player, @Nullable Entity target, @Nullable Params params) {
        State state = states().computeIfAbsent(key(player), k -> new State());
        state.lastAttackTick = player.level().getGameTime();
        if (params != null) {
            state.params = params;
        }

        if (target instanceof LivingEntity living && living.isAlive()) {
            lockOn(state, living, state.policy);
        }
    }

    /** 手动丢锁。 */
    public static void release(Player player) {
        State state = states().get(key(player));
        if (state != null) {
            state.targetId = 0;
            state.invalidTicks = 0;
        }
    }

    /** 换角色 / 死亡 / 退出时清掉。 */
    public static void clear(Player player) {
        states().remove(key(player));
    }

    // ==================== 每刻校验 ====================

    /**
     * 每 tick 调一次：校验当前锁还成不成立，该丢就丢。
     *
     * <p>双端都要调（客户端由 {@code ActionStateMachine} 驱动，服务端由角色 tick 驱动）。
     */
    public static void tick(Player player) {
        State state = states().get(key(player));
        if (state == null || state.targetId == 0) {
            return;
        }

        LivingEntity target = current(player);

        if (target == null) {
            // 死了 / 卸载了 → 立刻丢
            state.targetId = 0;
            state.invalidTicks = 0;
            return;
        }

        if (!isValid(player, target, state)) {
            if (++state.invalidTicks > LOSE_GRACE_TICKS) {
                state.targetId = 0;
                state.invalidTicks = 0;
            }
            return;
        }

        state.invalidTicks = 0;

        // 太久没出手 → 松锁，免得一直粘着一个没在打的怪
        long idle = player.level().getGameTime() - state.lastAttackTick;
        if (state.lastAttackTick != Long.MIN_VALUE && idle > IDLE_RELEASE_TICKS) {
            state.targetId = 0;
        }
    }

    /** 目标还满足「保持锁定」的条件吗。 */
    private static boolean isValid(Player player, LivingEntity target, State state) {
        if (!target.isAlive() || target.isRemoved()) {
            return false;
        }
        if (!state.policy.isTargetable(player, target)) {
            return false;
        }

        double distanceSq = player.distanceToSqr(target);
        if (distanceSq > state.params.keepRange() * state.params.keepRange()) {
            return false;
        }

        if (angleTo(player, target) > state.params.keepAngle()) {
            return false;
        }

        // 追不上的目标不再保持：隔着方块、在地板下、飞在天花板上面 ——
        // 锁着它只会让人一直对着打不到的方向转，还占着索敌位。
        return !state.params.chase()
                || ChaseReach.canCloseIn(player, target, state.params.attackRange());
    }

    // ==================== 内部工具 ====================

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

    private static double scoreOf(Player player, LivingEntity target, TargetPolicy policy) {
        double distanceSq = player.distanceToSqr(target);
        Vec3 toTarget = target.position().subtract(player.position());
        Vec3 horizontal = new Vec3(toTarget.x, 0, toTarget.z);
        double dot = horizontal.lengthSqr() < 1.0E-6
                ? 1.0
                : horizontal.normalize().dot(player.getLookAngle().multiply(1, 0, 1).normalize());
        return policy.score(player, target, distanceSq, dot);
    }

    /** 目标方向与玩家视线的夹角（度，水平面上算）。 */
    private static double angleTo(Player player, Entity target) {
        Vec3 toTarget = target.position().subtract(player.position());
        Vec3 horizontal = new Vec3(toTarget.x, 0, toTarget.z);
        if (horizontal.lengthSqr() < 1.0E-6) {
            return 0;
        }

        Vec3 look = player.getLookAngle();
        Vec3 lookHorizontal = new Vec3(look.x, 0, look.z);
        if (lookHorizontal.lengthSqr() < 1.0E-6) {
            return 0;
        }

        double dot = Math.clamp(horizontal.normalize().dot(lookHorizontal.normalize()), -1.0, 1.0);
        return Math.toDegrees(Math.acos(dot));
    }

    private static void lockOn(State state, LivingEntity target, TargetPolicy policy) {
        state.targetId = target.getId();
        state.invalidTicks = 0;
        state.policy = policy;
    }

    private static Map<String, State> states() {
        return STATES;
    }

    private static String key(Player player) {
        UUID uuid = player.getUUID();
        return (player.level().isClientSide() ? "C:" : "S:") + uuid;
    }

    /** 调试：当前有几个玩家在锁定状态。 */
    public static int trackedCount() {
        return STATES.size();
    }

    static {
        // 服务端玩家退出时顺手清掉，避免静态表堆积
        if (LOGGER.isDebugEnabled()) {
//            LOGGER.debug("[CombatTargeting] 软锁定索敌已加载：acquire={} keep={} angle={}/{}",
//                    ACQUIRE_RANGE, KEEP_RANGE, ACQUIRE_ANGLE, KEEP_ANGLE);
        }
    }

    /** 服务端：玩家下线时调用。 */
    public static void onPlayerRemoved(ServerPlayer player) {
        clear(player);
    }
}
