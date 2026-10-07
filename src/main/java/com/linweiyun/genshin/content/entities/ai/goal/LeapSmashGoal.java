package com.linweiyun.genshin.content.entities.ai.goal;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 「大跳扑击」—— 进入攻击距离后跳起撞人，落地时顺势回到起跳点。
 *
 * <h2>它和原版哪些 Goal 不一样</h2>
 * <ul>
 *   <li>{@link net.minecraft.world.entity.ai.goal.LeapAtTargetGoal}：只会朝着目标跳一下，
 *       落到哪算哪，不保证打到人，也不回原位；</li>
 *   <li>{@link net.minecraft.world.entity.ai.goal.MeleeAttackGoal}：走过去贴脸打，
 *       没有位移演出；</li>
 *   <li>本 Goal：<b>位移本身</b>就是攻击表现 —— 起跳、空中撞上、原路落回。</li>
 * </ul>
 *
 * <h2>状态机</h2>
 * <pre>
 * OUT   起跳到撞上目标（接触判定，不要求精确 2 格）
 *  ↓ 撞击
 * BACK  掉头飞回起跳点
 *  ↓ 落地且贴近起跳点
 * DONE  → GoalSelector 因为 canContinueToUse() 为假而调用 stop()
 * </pre>
 *
 * <p>空中每一刻都重设水平速度（{@code heading * airSpeed}），所以摩擦与重力只影响竖直分量，
 * 弧线的水平落点是可控的：{@code airSpeed = 水平距离 / 滞空刻数}。这也顺带兜住了
 * 「目标在飞的过程中挪了一步」的情况。
 *
 * <h2>标志位</h2>
 * 抢占 {@code MOVE}（不许别人抢移动）、{@code JUMP}（不许叠加别的跳跃）、
 * {@code LOOK}（转头这件事由本 Goal 负责），且 {@link #isInterruptable()} 为 false ——
 * 一次扑击中间被打断会出现「跳到一半悬停」的观感。
 *
 * @param <T> 使用这套扑击的生物类型
 */
public class LeapSmashGoal<T extends PathfinderMob> extends Goal {

    /** 扑击阶段。 */
    public enum Phase {
        /** 起跳 → 撞上目标。 */
        OUT,
        /** 掉头 → 落回起跳点。 */
        BACK,
        /** 已结束（等 GoalSelector 收尾）。 */
        DONE
    }

    protected final T mob;

    /** 触发距离：与目标的水平距离 ≤ 它就起跳。 */
    private final double triggerDistance;

    /** 起跳前允许的朝向偏差（度）。 */
    private static final float FACING_TOLERANCE_DEGREES = 45.0F;

    /** 撞击判定的放宽量：玩家碰撞箱外再放宽这么多格算撞上。 */
    private final double smashPadding;

    /** 一次弧线的滞空刻数估计值，用来反推水平速度。 */
    private final int airborneTicks;

    /** 起跳竖直初速。 */
    private final double launchY;

    /** 回程竖直初速相对起跳的比例。 */
    private final double backLaunchRatio;

    /** 水平速度上下限，防止极近距离/极远距离时速度失控。 */
    private final double minAirSpeed;
    private final double maxAirSpeed;

    /** 单阶段最长刻数，兜底防止卡在空中。 */
    private final int maxPhaseTicks;

    /** 落地后离起跳点多近算「回到原位」。 */
    private final double arriveDistance;

    /** 两次扑击之间的间隔（刻）。 */
    private final int cooldownTicks;

    private Phase phase = Phase.DONE;

    private Vec3 origin = Vec3.ZERO;

    private Vec3 heading = Vec3.ZERO;

    private double airSpeed;

    private int elapsed;

    private long nextAllowedTick;

    public LeapSmashGoal(T mob) {
        this(mob, 2.0D);
    }

    public LeapSmashGoal(T mob, double triggerDistance) {
        this(mob, triggerDistance, 0.35D, 10, 0.42D, 0.85D, 0.12D, 0.85D, 40, 0.75D, 25);
    }

    public LeapSmashGoal(T mob, double triggerDistance, double smashPadding, int airborneTicks,
                         double launchY, double backLaunchRatio, double minAirSpeed, double maxAirSpeed,
                         int maxPhaseTicks, double arriveDistance, int cooldownTicks) {
        this.mob = mob;
        this.triggerDistance = triggerDistance;
        this.smashPadding = smashPadding;
        this.airborneTicks = Math.max(1, airborneTicks);
        this.launchY = launchY;
        this.backLaunchRatio = backLaunchRatio;
        this.minAirSpeed = minAirSpeed;
        this.maxAirSpeed = maxAirSpeed;
        this.maxPhaseTicks = Math.max(1, maxPhaseTicks);
        this.arriveDistance = arriveDistance;
        this.cooldownTicks = Math.max(0, cooldownTicks);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
    }

    // ==================== 触发 ====================

    @Override
    public boolean canUse() {
        if (this.mob.level().getGameTime() < this.nextAllowedTick) {
            return false;
        }
        if (!this.mob.onGround() || this.mob.isPassenger() || this.mob.isInWater()) {
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (this.horizontalDistanceTo(target) > this.triggerDistance) {
            return false;
        }
        // 距离够了还得<b>朝着</b>目标才起跳 —— 只看距离的话，它会横着飞出去。
        // 站着不动时身体朝向由 LookAtPlayerGoal 慢慢转过来，所以不会卡死。
        return this.isFacing(target);
    }

    /** 身体朝向和目标方向的夹角在容差内。 */
    private boolean isFacing(LivingEntity target) {
        double dx = target.getX() - this.mob.getX();
        double dz = target.getZ() - this.mob.getZ();
        if (dx * dx + dz * dz < 1.0E-4D) {
            return true;
        }
        float wantedYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        return Math.abs(Mth.wrapDegrees(wantedYaw - this.mob.getYRot())) <= FACING_TOLERANCE_DEGREES;
    }

    @Override
    public boolean canContinueToUse() {
        return this.phase != Phase.DONE;
    }

    /** 扑击途中不许被抢走控制权：半空中换人接手会僵住。 */
    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    // ==================== 生命周期 ====================

    @Override
    public void start() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            this.phase = Phase.DONE;
            return;
        }

        this.origin = this.mob.position();
        this.heading = this.horizontalDirectionTo(target);
        this.elapsed = 0;
        this.phase = Phase.OUT;

        // 抢占移动：停掉寻路，交出的速度由本 Goal 每 tick 直接写
        this.mob.getNavigation().stop();
        this.mob.setAggressive(true);
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        this.launch(this.airSpeedFor(this.horizontalDistanceTo(target)), this.launchY);
        this.onLeapStarted();
    }

    @Override
    public void stop() {
        this.phase = Phase.DONE;
        this.mob.setAggressive(false);
        this.mob.getNavigation().stop();
        this.nextAllowedTick = this.mob.level().getGameTime()
                + this.cooldownTicks + this.mob.getRandom().nextInt(this.cooldownTicks + 1);
        this.onLeapFinished();
    }

    @Override
    public void tick() {
        this.elapsed++;

        // 空中不给 aiStep 留下移动输入，否则会和这里写的速度叠加
        this.mob.setSpeed(0.0F);
        this.mob.setZza(0.0F);
        this.mob.setXxa(0.0F);

        if (this.phase == Phase.OUT) {
            this.tickOut();
        } else if (this.phase == Phase.BACK) {
            this.tickBack();
        }
    }

    // ==================== 阶段 ====================

    private void tickOut() {
        LivingEntity target = this.mob.getTarget();

        // 目标没了（死了 / 换目标）：不半路悬停，直接掉头回家
        if (target == null || !target.isAlive()) {
            this.beginReturn();
            return;
        }
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        this.keepAirSpeed();

        if (this.mob.getBoundingBox().inflate(this.smashPadding).intersects(target.getBoundingBox())) {
            this.smash(target);
            this.beginReturn();
            return;
        }
        // 没撞上就落地了（目标挪开 / 被挡住）：别再贴地滑，直接掉头回家
        if (this.mob.onGround() && this.elapsed > 2) {
            this.beginReturn();
            return;
        }
        if (this.elapsed > this.maxPhaseTicks) {
            this.phase = Phase.DONE;
        }
    }

    private void tickBack() {
        if (this.mob.onGround()) {
            double remaining = this.horizontalDistanceTo(this.origin);
            if (remaining <= this.arriveDistance || this.elapsed > this.maxPhaseTicks) {
                this.phase = Phase.DONE;
                return;
            }
            // 落点偏了：再补一小跳，直到贴回起跳点或超时
            this.heading = this.horizontalDirectionTo(this.origin);
            this.launch(this.airSpeedFor(remaining), this.launchY * this.backLaunchRatio);
            return;
        }
        this.keepAirSpeed();
    }

    /** 撞击后掉头：方向指向起跳点，速度按剩余距离反推。子类可覆写切「回位」动画。 */
    protected void beginReturn() {
        this.phase = Phase.BACK;
        this.elapsed = 0;
        this.heading = this.horizontalDirectionTo(this.origin);
        this.launch(this.airSpeedFor(this.horizontalDistanceTo(this.origin)),
                this.launchY * this.backLaunchRatio);
    }

    // ==================== 速度 ====================

    /** 水平速度 = 距离 / 滞空刻数，并夹在上下限之间。 */
    private double airSpeedFor(double distance) {
        return Mth.clamp(distance / this.airborneTicks, this.minAirSpeed, this.maxAirSpeed);
    }

    private void launch(double horizontalSpeed, double verticalSpeed) {
        this.airSpeed = horizontalSpeed;
        this.mob.setDeltaMovement(this.heading.x * horizontalSpeed, verticalSpeed, this.heading.z * horizontalSpeed);
    }

    /** 每 tick 重设水平分量，只保留当前竖直分量（重力照常作用）。 */
    private void keepAirSpeed() {
        Vec3 current = this.mob.getDeltaMovement();
        this.mob.setDeltaMovement(this.heading.x * this.airSpeed, current.y, this.heading.z * this.airSpeed);
    }

    // ==================== 撞击 ====================

    /**
     * 造成撞击伤害。
     *
     * <p>默认走原版 {@code doHurtTarget}（吃 ATTACK_DAMAGE、附魔、击退）。
     * 需要接入本 MOD 的元素伤害管线时子类覆写
     * {@link #smash(LivingEntity)}，调用 {@code super} 之后再补自己的东西，
     * 或者完全自己造 {@code ModDamageSource}。
     */
    protected void smash(LivingEntity target) {
        this.mob.swing(InteractionHand.MAIN_HAND);
        this.mob.doHurtTarget(target);
        this.onSmashed(target);
    }

    /** 子类钩子：起跳那一刻（放音效 / 切动画）。 */
    protected void onLeapStarted() {
    }

    /** 子类钩子：撞上那一刻。 */
    protected void onSmashed(LivingEntity target) {
    }

    /** 子类钩子：落地收尾（切回常态动画）。 */
    protected void onLeapFinished() {
    }

    // ==================== 工具 ====================

    protected ServerLevel serverLevel() {
        return (ServerLevel) this.mob.level();
    }

    /** 当前阶段，给动画状态用。 */
    public Phase phase() {
        return this.phase;
    }

    /** 水平方向（已归一化）；距离过近时返回零向量。 */
    private Vec3 horizontalDirectionTo(Vec3 destination) {
        double dx = destination.x - this.mob.getX();
        double dz = destination.z - this.mob.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        return length < 1.0E-4D ? Vec3.ZERO : new Vec3(dx / length, 0.0D, dz / length);
    }

    private double horizontalDistanceTo(Vec3 destination) {
        double dx = destination.x - this.mob.getX();
        double dz = destination.z - this.mob.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private Vec3 horizontalDirectionTo(LivingEntity entity) {
        return horizontalDirectionTo(entity.position());
    }

    private double horizontalDistanceTo(LivingEntity entity) {
        return horizontalDistanceTo(entity.position());
    }
}
