package com.linweiyun.genshin.content.entities.teyvat.skill.vesna;

import com.linweiyun.genshin.content.entities.ModEntities;
import com.linweiyun.genshin.content.skill_node.TargetSeeker;
import com.linweiyun.genshin.core.character.sword.vesna.Vesna;
import com.linweiyun.genshin.core.character.sword.vesna.VesnaSkill;
import com.linweiyun.genshin.core.system.combat.targeting.SummonTargeting;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.genshin.core.sync.ISyncManagedEntity;
import com.linweiyun.genshin.core.system.about.AttachmentType;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import com.lowdragmc.lowdraglib2.syncdata.storage.IManagedStorage;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import com.linweiyun.genshin.core.system.combat.CombatAim;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class VesnaAttackProjectile extends Entity implements ISyncManagedEntity {
    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CONTENT);

    // ==================== 参数 ====================
    private static final double SEARCH_RADIUS = 10.0;
    private static final double FLY_SPEED = 0.8;
    private static final int SPAWN_DURATION = 16;
    private static final double SPAWN_RADIUS = 3.2;
    private static final double BACK_DISTANCE = 3.5;
    private static final double BACK_HEIGHT = 0.9;
    private static final float MAX_TURN_RATE = 12f;
    private static final int MAX_LIFE_TIME = 200;

    private static final int PHASE_SPAWNING = 0;
    private static final int PHASE_ATTACKING = 1;

    // ==================== LDLib2 同步字段 ====================

    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    @Override public Entity getSelf() { return this; }
    @Override public IManagedStorage getSyncStorage() { return syncStorage; }
    @Override public void notifyPersistence() {}

    @Persisted(key = "character") @DescSynced
    private Vesna character;

    /**
     * 这只风铃的索敌模式。
     *
     * <p>{@link SummonTargeting#OWNER_TARGET}：优先咬玩家（召唤者）当前锁定的目标；
     * 玩家没在打就保留自己的目标，自己也没有才独立找。
     * 想让它完全独立索敌，把这里换成 {@link SummonTargeting#INDEPENDENT} 即可。
     */
    private static final SummonTargeting SUMMON_TARGETING = SummonTargeting.defaultMode();

    @Persisted(key = "target") @DescSynced
    private LivingEntity target;

    @Persisted(key = "test") @DescSynced
    private int test = 100;

    @Persisted(key = "phase") @DescSynced
    private int phase = PHASE_SPAWNING;

    @Persisted(key = "spawn_age") @DescSynced
    private int spawnAge = 0;

    @Persisted(key = "spawn_yaw") @DescSynced
    private float spawnYaw = 0f;

    @Persisted(key = "c_x") @DescSynced private double cx;
    @Persisted(key = "c_y") @DescSynced private double cy;
    @Persisted(key = "c_z") @DescSynced private double cz;

    @Persisted(key = "p1_x") @DescSynced private double p1x;
    @Persisted(key = "p1_y") @DescSynced private double p1y;
    @Persisted(key = "p1_z") @DescSynced private double p1z;

    @Persisted(key = "e_x") @DescSynced private double ex;
    @Persisted(key = "e_y") @DescSynced private double ey;
    @Persisted(key = "e_z") @DescSynced private double ez;

    @Persisted(key = "lock_yaw") @DescSynced
    private float attackLockYaw = 0f;

    @Persisted(key = "scatter") @DescSynced
    private float scatterAngleOffset = 0f;

    /** 生成时的技能等级（用于命中伤害） */
    @Persisted(key = "skill_level") @DescSynced
    private int skillLevel = 1;

    // ==================== 瞬态 ====================

    private boolean hasHit = false;
    private boolean initialSynced = false;

    // ==================== 构造 / 工厂 ====================

    public VesnaAttackProjectile(EntityType<? extends VesnaAttackProjectile> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = false;
    }

    public static VesnaAttackProjectile create(Level level, Vesna ownerCharacter, Vec3 pos, int skillLevel) {
        VesnaAttackProjectile p = ModEntities.VESNA_ATTACK_PROJECTILE.get()
                .create(level, EntitySpawnReason.EVENT);
        if (p == null) return null;

        Player ownerPlayer = ownerCharacter.getData().getOwnerPlayer();
        if (ownerPlayer == null) return null;

        p.character = ownerCharacter;
        p.setNoGravity(true);
        p.test = 15222;
        p.skillLevel = skillLevel;

        p.cx = pos.x;
        p.cy = pos.y + 1.0;
        p.cz = pos.z;

        p.spawnYaw = CombatAim.yaw(ownerPlayer);
        p.scatterAngleOffset = -60f + level.getRandom().nextFloat() * 120f;

        float targetAngle = p.spawnYaw + p.scatterAngleOffset;
        double targetRad = Math.toRadians(targetAngle);
        p.ex = p.cx + (-Math.sin(targetRad)) * SPAWN_RADIUS;
        p.ey = p.cy;
        p.ez = p.cz + Math.cos(targetRad) * SPAWN_RADIUS;

        float backAngle = p.spawnYaw + 180f;
        double backRad = Math.toRadians(backAngle);
        p.p1x = p.cx + (-Math.sin(backRad)) * BACK_DISTANCE;
        p.p1y = p.cy + BACK_HEIGHT;
        p.p1z = p.cz + Math.cos(backRad) * BACK_DISTANCE;

        p.setPos(p.cx, p.cy, p.cz);
        p.setYRot(backAngle);
        p.setXRot(0f);

        p.attackLockYaw = p.spawnYaw;
        p.phase = PHASE_SPAWNING;
        p.spawnAge = 0;

        return p;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    // ==================== tick ====================

    @Override
    public void tick() {
        super.tick();

        if (tickCount >= MAX_LIFE_TIME) { discard(); return; }
        if (hasHit) { discard(); return; }

        if (!level().isClientSide()) {
            if (character == null) { discard(); return; }
            if (!initialSynced) {
                initialSynced = true;
                sync(true);
            }

            // 索敌模式：优先咬主人（玩家）当前锁定的目标 —— 角色可能已经挂后台，所以问玩家
            target = SUMMON_TARGETING.resolve(
                    getOwnerPlayer(),
                    target,
                    SEARCH_RADIUS,
                    () -> new TargetSeeker(
                            this,
                            SEARCH_RADIUS,
                            TargetSeeker.TargetingType.RADIUS,
                            TargetSeeker.SourceType.TRACKING_COOP,
                            character,
                            TargetSeeker.TargetFilter.HOSTILE_ONLY,
                            new Vec3(cx, cy, cz)
                    ).execute());
        } else {
            if (character == null) return;
        }

        if (phase == PHASE_SPAWNING) {
            tickSpawning();
        } else {
            tickAttacking();
        }

        if (!level().isClientSide()) {
            passivelySync();
        }
    }

    // ==================== 阶段 1：生成 ====================

    private void tickSpawning() {
        spawnAge++;
        float u = Math.min(1f, (float) spawnAge / SPAWN_DURATION);

        Vec3 prevPos = position();
        Vec3 newPos = bezier(u);
        Vec3 delta = newPos.subtract(prevPos);

        setDeltaMovement(delta);
        setPos(newPos.x, newPos.y, newPos.z);

        Vec3 tangent = bezierTangent(u);
        if (tangent.lengthSqr() > 1e-6) {
            Vec3 dir = tangent.normalize();
            setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
        }

        if (spawnAge >= SPAWN_DURATION) {
            enterAttackPhase();
        }
    }

    private Vec3 bezier(float t) {
        float omt = 1f - t;
        return new Vec3(cx, cy, cz).scale(omt * omt)
                .add(new Vec3(p1x, p1y, p1z).scale(2 * omt * t))
                .add(new Vec3(ex, ey, ez).scale(t * t));
    }

    private Vec3 bezierTangent(float t) {
        float omt = 1f - t;
        return new Vec3(p1x - cx, p1y - cy, p1z - cz).scale(2 * omt)
                .add(new Vec3(ex - p1x, ey - p1y, ez - p1z).scale(2 * t));
    }

    private void enterAttackPhase() {
        phase = PHASE_ATTACKING;
        Player ownerPlayer = getOwnerPlayer();
        attackLockYaw = (ownerPlayer != null) ? CombatAim.yaw(ownerPlayer) : spawnYaw;
    }

    // ==================== 阶段 2：攻击 ====================

    private void tickAttacking() {
        boolean hasTarget = (target != null && target.isAlive());

        Vec3 moveDir;
        if (hasTarget) {
            Vec3 toTarget = target.getBoundingBox().getCenter().subtract(position());
            moveDir = (toTarget.lengthSqr() > 1e-6) ? toTarget.normalize() : yawToDir(attackLockYaw);
        } else {
            moveDir = yawToDir(attackLockYaw);
        }

        if (hasTarget) {
            Vec3 ahead = position().add(moveDir.scale(FLY_SPEED * 3));
            if (isBlocked(position(), ahead)) {
                Vec3 left = rotateDirY(moveDir, +45f);
                Vec3 right = rotateDirY(moveDir, -45f);
                if (!isBlocked(position(), position().add(left.scale(FLY_SPEED * 3)))) {
                    moveDir = left;
                } else if (!isBlocked(position(), position().add(right.scale(FLY_SPEED * 3)))) {
                    moveDir = right;
                }
            }
        }

        float desiredYaw = (float) Math.toDegrees(Math.atan2(-moveDir.x, moveDir.z));
        float newYaw = rotateTowards(getYRot(), desiredYaw, MAX_TURN_RATE);
        setYRot(newYaw);

        setDeltaMovement(moveDir.scale(FLY_SPEED));
        Vec3 beforePos = position();
        move(MoverType.SELF, getDeltaMovement());

        if (!hasTarget) {
            double dx = getX() - cx;
            double dy = getY() - cy;
            double dz = getZ() - cz;
            if (dx * dx + dy * dy + dz * dz >= SEARCH_RADIUS * SEARCH_RADIUS) {
                discard();
                return;
            }
            if (horizontalCollision && position().distanceToSqr(beforePos) < 1e-4) {
                discard();
                return;
            }
        } else {
            if (getBoundingBox().intersects(target.getBoundingBox())) {
                onHitTarget(target);
            }
        }
    }

    private static Vec3 yawToDir(float yaw) {
        double rad = Math.toRadians(yaw);
        return new Vec3(-Math.sin(rad), 0, Math.cos(rad));
    }

    private static Vec3 rotateDirY(Vec3 dir, float deltaYaw) {
        double rad = Math.toRadians(deltaYaw);
        double c = Math.cos(rad);
        double s = Math.sin(rad);
        return new Vec3(dir.x * c - dir.z * s, 0, dir.x * s + dir.z * c);
    }

    private static float rotateTowards(float current, float target, float maxDelta) {
        float diff = target - current;
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        if (Math.abs(diff) <= maxDelta) return target;
        return current + Math.signum(diff) * maxDelta;
    }

    private boolean isBlocked(Vec3 from, Vec3 to) {
        HitResult hit = level().clip(new ClipContext(
                from, to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this));
        return hit.getType() != HitResult.Type.MISS;
    }

    // ==================== 命中 ====================

    @Override
    public boolean hurtServer(ServerLevel serverLevel, DamageSource damageSource, float v) {
        return false;
    }

    private void onHitTarget(LivingEntity target) {
        if (hasHit) return;
        hasHit = true;

        if (character == null) { discard(); return; }
        if (!(level() instanceof ServerLevel serverLevel)) { discard(); return; }

        Player ownerPlayer = getOwnerPlayer();
        if (ownerPlayer == null) { discard(); return; }

        float mult = VesnaSkill.getWindBellDamageMultiplier(skillLevel);
        ModDamageSpec spec = ModDamageSpec.builder(AttackType.ELEMENTAL_SKILL, ModElements.ANEMO.get())
                .multiplier(mult)
                .elementAmount(AttachmentType.WEAK.getInitialAmount())
                .decayGroup(VesnaSkill.VESNA_WIND_BELL_DECAY)
                .attackerCharacter(character)
                .build();
        ModDamageSource source = ModDamageSource.from(spec, ownerPlayer);
        target.hurtServer(serverLevel, source, 0f);

        discard();
    }

    // ==================== 持久化 ====================

    @Override
    protected void readAdditionalSaveData(ValueInput valueInput) {
        loadManagedPersistentData(valueInput);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueOutput) {
        saveManagedPersistentData(valueOutput, false);
    }

    private Player getOwnerPlayer() {
        if (character == null) return null;
        return character.getData().getOwnerPlayer();
    }
}