package com.linweiyun.genshin.content.entities.teyvat.monster.slime;

import com.linweiyun.genshin.config.entity.MobBehaviorConfig;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.content.skill_node.GroundMarker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * <b>技能三：跃起砸落</b> —— 压缩身体蓄力 → 高高跳起 → 悬停定位 →
 * 在玩家脚下画落点 → 砸下来。
 *
 * <pre>
 * COMPRESS 2s   原地压缩蓄力（这 2 秒是给玩家的「要跳了」信号）
 * RISE          起跳上升（关重力，保持可控）
 * HOVER  1.5s   悬停在空中定位
 * MARKING 1s    在目标当前位置画 3×3 落点（{@link GroundMarker}），1 秒扩满
 * LAND          落点扩满的那一刻砸下去，3×3 范围内结算伤害
 * </pre>
 *
 * <p>伤害倍率 3.0（攻击力 × 3）。落点提示抽成了技能节点，别的技能也能用。
 *
 * <p>⚠️ 空中阶段把 {@code noGravity} 打开了，所以 {@link #onStopped()} 里必须恢复 ——
 * 否则技能被打断会留下一只永远浮空的史莱姆。
 */
public class CryoSlimeSlamGoal extends CryoSlimeSkillGoal {

    /** 压缩蓄力刻数（2 秒）。 */
    private static final int COMPRESS_TICKS = 40;

    /** 上升到最高点的刻数。 */
    private static final int RISE_TICKS = 14;

    /** 滞空刻数（0.6 秒）。 */
    private static final int HOVER_TICKS = 12;

    /** 落点扩散刻数（1.5 秒）：玩家用来跑出去的时间。 */
    private static final int MARKER_TICKS = 30;

    /** 起跳竖直初速（调低过：太高会飞出视野，也不好看）。 */
    private static final double RISE_SPEED = 0.5D;

    /** 砸下来的速度。 */
    private static final double FALL_SPEED = 1.6D;

    /** 落点半径（5×5 → 半宽 2.5）。 */
    private static final double SLAM_RADIUS = 2.5D;

    /** 落地伤害倍率（攻击力 × 3）。 */
    private static final float SLAM_MULTIPLIER = 3.0f;

    private enum Phase { NONE, COMPRESS, RISE, HOVER, MARKING, LAND }

    private Phase phase = Phase.NONE;

    private int elapsed;

    private Vec3 landingSpot = Vec3.ZERO;

    public CryoSlimeSlamGoal(LargeCryoSlime slime) {
        super(slime, MobBehaviorConfig.slamCooldownTicks(), 20);
    }

    @Override
    protected boolean extraCanUse(LivingEntity target) {
        return horizontalDistanceTo(target) <= 16.0D;
    }

    @Override
    protected float chance() {
        // 技能是点缀、撞击是主食，但也不能半天不出一招
        return 0.45f;
    }

    @Override
    public boolean canContinueToUse() {
        return this.phase != Phase.NONE;
    }

    @Override
    public void start() {
        this.phase = Phase.COMPRESS;
        this.elapsed = 0;
        this.slime.getNavigation().stop();
        this.slime.setAggressive(true);
    }

    @Override
    public void tick() {
        this.elapsed++;
        this.slime.setSpeed(0.0F);
        this.slime.setZza(0.0F);
        this.slime.setXxa(0.0F);

        switch (this.phase) {
            case COMPRESS -> tickCompress();
            case RISE -> tickRise();
            case HOVER -> tickHover();
            case MARKING -> tickMarking();
            case LAND -> tickLand();
            case NONE -> {
            }
        }
    }

    private void tickCompress() {
        if (this.elapsed >= COMPRESS_TICKS) {
            this.phase = Phase.RISE;
            this.elapsed = 0;
            this.slime.setNoGravity(true);
            this.slime.setDeltaMovement(0.0D, RISE_SPEED, 0.0D);
        }
    }

    private void tickRise() {
        this.slime.setDeltaMovement(0.0D, RISE_SPEED, 0.0D);
        this.slime.move(net.minecraft.world.entity.MoverType.SELF, this.slime.getDeltaMovement());
        if (this.elapsed >= RISE_TICKS) {
            this.phase = Phase.HOVER;
            this.elapsed = 0;
            this.slime.setDeltaMovement(Vec3.ZERO);
        }
    }

    private void tickHover() {
        this.slime.setDeltaMovement(Vec3.ZERO);
        if (this.elapsed >= HOVER_TICKS) {
            this.phase = Phase.MARKING;
            this.elapsed = 0;
            spawnMarker();
        }
    }

    /** 在目标当前位置画落点；扩满的那一刻回调砸落。 */
    private void spawnMarker() {
        if (!(this.slime.level() instanceof ServerLevel serverLevel)) {
            this.phase = Phase.NONE;
            return;
        }
        LivingEntity target = this.slime.currentTarget();
        Vec3 center = target != null ? target.position() : this.slime.position();
        this.landingSpot = center;
        GroundMarker.spawn(serverLevel, center, SLAM_RADIUS, MARKER_TICKS, spot -> this.beginLanding(spot));
    }

    private void tickMarking() {
        this.slime.setDeltaMovement(Vec3.ZERO);
    }

    /** 落点提示扩满 → 开始砸。 */
    private void beginLanding(Vec3 spot) {
        this.landingSpot = spot;
        this.phase = Phase.LAND;
        this.elapsed = 0;
        this.slime.setNoGravity(false);
        // 先挪到落点正上方，再直着砸下来
        this.slime.setPos(spot.x, this.slime.getY(), spot.z);
        this.slime.setDeltaMovement(0.0D, -FALL_SPEED, 0.0D);
    }

    private void tickLand() {
        this.slime.setDeltaMovement(0.0D, -FALL_SPEED, 0.0D);
        this.slime.move(net.minecraft.world.entity.MoverType.SELF, this.slime.getDeltaMovement());
        if (this.slime.onGround() || this.elapsed > 40) {
            slamDamage();
            this.phase = Phase.NONE;
        }
    }

    /** 3×3 范围伤害，倍率 3.0。 */
    private void slamDamage() {
        if (!(this.slime.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 center = this.slime.position();
        AABB area = AABB.ofSize(new Vec3(center.x, center.y + 1.0D, center.z),
                SLAM_RADIUS * 2.0D, 3.0D, SLAM_RADIUS * 2.0D);
        ModDamageSpec spec = ModDamageSpec.builder(AttackType.MONSTER, ModElements.CYRO.get())
                .multiplier(SLAM_MULTIPLIER)
                .elementAmount(0.0f)
                .build();
        ModDamageSource source = ModDamageSource.from(spec, this.slime);
        for (LivingEntity victim : serverLevel.getEntitiesOfClass(LivingEntity.class, area)) {
            if (victim == this.slime || !victim.isAlive()) {
                continue;
            }
            victim.hurt(source, 0f);
        }
        this.slime.resetCombat();
    }

    @Override
    protected void onStopped() {
        this.phase = Phase.NONE;
        this.slime.setNoGravity(false);
        this.slime.setAggressive(false);
        this.slime.getNavigation().stop();
    }
}
