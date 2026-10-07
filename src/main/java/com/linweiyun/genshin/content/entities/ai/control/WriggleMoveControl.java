package com.linweiyun.genshin.content.entities.ai.control;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * 「蠕动 + 小跳」移动控制。
 *
 * <h2>为什么改 MoveControl 而不是加 Goal</h2>
 * Goal 决定「去哪」，MoveControl 决定「怎么走」。原版 {@link MoveControl} 是匀速直线滑行，
 * 想要「一顿一冲、左右摇摆」这种<b>走法</b>上的差异，只能在 MoveControl 层做；
 * 写成 Goal 会变成两个 Goal 抢 MOVE 标志位，路径导航反而用不了。
 *
 * <h2>两种走法</h2>
 * <ul>
 *   <li><b>无目标 —— 蠕动</b>：速度按正弦波在 {@code creepFloor} ~ 1 之间起伏（一冲一顿），
 *       同时叠一个横向摆动分量（左右摆），合起来就是蠕动。</li>
 *   <li><b>有目标 —— 小跳</b>：不再蠕动，改为每 {@code hopInterval} 刻起跳一次，
 *       边跳边靠近，给「进入攻击距离就大跳」做铺垫。</li>
 * </ul>
 *
 * <p>跳跃用的是 {@code getJumpControl().jump()}，和原版 {@link net.minecraft.world.entity.ai.goal.LeapAtTargetGoal}
 * 之外的跳跃逻辑（如 {@link MoveControl} 自己在撞台阶时跳）走同一条路。
 *
 * @param <T> 使用这套走法的生物类型
 */
public class WriggleMoveControl<T extends Mob> extends MoveControl {

    /** 每 tick 最大转向角度 —— 蠕动时身体转不快，这也是「蠕动感」的一部分。 */
    private final float turnSpeed;

    /** 蠕动周期（刻）：一次「冲 - 顿」的完整时长。 */
    private final int creepPeriod;

    /** 蠕动速度的谷底倍率：0 表示完全停住，1 表示不停。 */
    private final float creepFloor;

    /** 横向摆动幅度（相对于前进速度）。 */
    private final float swayAmount;

    /** 有目标时的小跳间隔（刻）。 */
    private final int hopInterval;

    /** 相位计数器，蠕动 / 小跳的节拍都靠它。 */
    private int phase;

    public WriggleMoveControl(T mob) {
        this(mob, 25.0F, 16, 0.25F, 0.35F, 14);
    }

    public WriggleMoveControl(T mob, float turnSpeed, int creepPeriod, float creepFloor,
                              float swayAmount, int hopInterval) {
        super(mob);
        this.turnSpeed = turnSpeed;
        this.creepPeriod = Math.max(2, creepPeriod);
        this.creepFloor = Mth.clamp(creepFloor, 0.0F, 1.0F);
        this.swayAmount = Math.max(0.0F, swayAmount);
        this.hopInterval = Math.max(1, hopInterval);
    }

    @Override
    public void tick() {
        this.phase++;

        // 没有移动指令：原地停住，但保留身体朝向（朝向由 LookControl 负责）
        if (this.operation != Operation.MOVE_TO) {
            stopMoving();
            return;
        }
        this.operation = Operation.WAIT;

        double dx = this.wantedX - this.mob.getX();
        double dz = this.wantedZ - this.mob.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        // 已经到了：停下，避免在原地抖
        if (horizontal < 0.5D) {
            stopMoving();
            return;
        }

        float wantedYaw = (float) (Mth.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        this.mob.setYRot(this.rotlerp(this.mob.getYRot(), wantedYaw, this.turnSpeed));
        this.mob.yHeadRot = this.mob.getYRot();
        this.mob.yBodyRot = this.mob.getYRot();

        float baseSpeed = (float) (this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED));

        if (this.mob.getTarget() != null) {
            hopForward(baseSpeed);
        } else {
            creepForward(baseSpeed);
        }
    }

    /** 有目标：全速前进 + 定时小跳。 */
    private void hopForward(float baseSpeed) {
        this.mob.setSpeed(baseSpeed);
        this.mob.setZza(1.0F);
        this.mob.setXxa(0.0F);

        if (this.mob.onGround() && this.phase % this.hopInterval == 0) {
            this.mob.getJumpControl().jump();
        }
    }

    /** 无目标：一顿一冲 + 左右摆。 */
    private void creepForward(float baseSpeed) {
        double angle = this.phase * (Math.PI * 2.0D / this.creepPeriod);
        float pulse = this.creepFloor + (1.0F - this.creepFloor) * (float) Math.max(0.0D, Math.sin(angle));

        this.mob.setSpeed(baseSpeed * pulse);
        this.mob.setZza(1.0F);
        this.mob.setXxa(this.swayAmount * (float) Math.cos(angle));
    }

    private void stopMoving() {
        this.mob.setZza(0.0F);
        this.mob.setXxa(0.0F);
        this.mob.setSpeed(0.0F);
    }
}
