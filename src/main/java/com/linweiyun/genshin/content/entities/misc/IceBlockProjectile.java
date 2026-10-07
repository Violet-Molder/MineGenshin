package com.linweiyun.genshin.content.entities.misc;

import net.minecraft.nbt.CompoundTag;

import com.linweiyun.genshin.content.entities.ModEntities;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;

/**
 * 「悬空冰块」—— 冰史莱姆碎冰技的投射物（见 {@code CryoSlimeShardGoal}），<b>原版冰块</b>（{@link Blocks#ICE}）实体化。
 *
 * <h2>为什么不用 {@code Display.BlockDisplay}</h2>
 * 原版展示实体看起来最合适（自带 {@code Transformation} 旋转、自动同步），
 * 但 {@code Display#setTransformation} / {@code BlockDisplay#setBlockState}
 * <b>都是 private</b> —— 只能通过 {@code /summon} 的 NBT 配置，
 * 代码里既设不了方块也改不了旋转。所以走自定义实体 + 自定义渲染器。
 *
 * <h2>两个阶段</h2>
 * <pre>
 * CHARGE  悬在施法者头顶（每 tick 跟随），自转较慢
 *   ↓ 蓄力满
 * FLY     朝目标直线飞（带制导），自转很快
 *   ↓ 撞到 / 撞墙 / 到达
 * 碎裂：方块的破坏粒子 + 玻璃碎裂音 + 范围伤害 → discard
 * </pre>
 *
 * <h2>同步</h2>
 * 只同步一个 {@code DATA_FLYING} 布尔：位置由引擎自己同步，
 * 方块状态是常量、贴图/模型交给渲染器，旋转角度由两端各自拿 {@code tickCount} 算 ——
 * 这样不需要为「转了多少度」额外发包。
 */
public class IceBlockProjectile extends Entity {

    /** 悬停蓄力刻数（要在头顶转几圈，所以给足时间）。 */
    public static final int CHARGE_TICKS = 36;

    /** 悬停在施法者头顶多高。 */
    private static final double HOVER_HEIGHT = 1.4D;

    /** 最长存活刻数，兜底防止打转不消失。 */
    private static final int MAX_LIFE = 100;

    /** 蓄力时的自转角速度（度/刻）：36 刻 × 20° = 720° = 正好转两圈。 */
    private static final float SPIN_CHARGE = 20.0F;

    /** 飞行时的自转角速度（度/刻）。 */
    private static final float SPIN_FLY = 55.0F;

    /** 飞行速度（格/刻）—— 调低过：太快看不清冰块，也躲不掉。 */
    private static final double FLY_SPEED = 0.5D;

    /** 碎裂伤害。 */
    private static final float IMPACT_DAMAGE = 6.0F;

    /** 碎裂伤害的判定盒半边长（宽/高各一半）。 */
    private static final double IMPACT_SIZE_XZ = 2.0D;
    private static final double IMPACT_SIZE_Y = 1.5D;

    /** 客户端靠它决定用哪个转速算角度。 */
    private static final EntityDataAccessor<Boolean> DATA_FLYING =
            SynchedEntityData.defineId(IceBlockProjectile.class, EntityDataSerializers.BOOLEAN);

    /** 客户端渲染用的缩放：1.0 = 整块冰；冰刺用 1/3 左右。 */
    private static final EntityDataAccessor<Float> DATA_SCALE =
            SynchedEntityData.defineId(IceBlockProjectile.class, EntityDataSerializers.FLOAT);

    /** 施法者（只服务端用，用来决定伤害来源和头顶位置）。 */
    @Nullable
    private LivingEntity caster;

    /** 目标（只服务端用，用来制导）。 */
    @Nullable
    private LivingEntity target;

    /** 发射瞬间锁定的落点，目标丢失时往这里飞。 */
    private Vec3 aim = Vec3.ZERO;

    private int age;

    /** 发射前在头顶悬停多久（冰刺会设成 0，直接飞出去）。 */
    private int chargeTicks = CHARGE_TICKS;

    /** 蓄力时的起始高度（从身体内部升上来）。 */
    private double chargeStartY;

    /** 蓄力期间的横向偏移（三连冰刺用左/中/右错开）。 */
    private Vec3 chargeOffset = Vec3.ZERO;

    private boolean shattered;

    public IceBlockProjectile(EntityType<? extends IceBlockProjectile> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /**
     * 在施法者头顶召唤一整块冰。
     *
     * @return 生成失败（维度不接受实体等）时返回 null
     */
    @Nullable
    public static IceBlockProjectile create(Level level, LivingEntity caster, @Nullable LivingEntity target) {
        return create(level, caster, target, 1.0F, CHARGE_TICKS);
    }

    /**
     * 召唤一块可缩放、可调蓄力时间的冰（冰刺用它）。
     *
     * @param scale       渲染缩放，冰刺约 1/3
     * @param chargeTicks 悬停蓄力刻数；0 = 生成即飞
     */
    @Nullable
    public static IceBlockProjectile create(Level level, LivingEntity caster, @Nullable LivingEntity target,
                                            float scale, int chargeTicks) {
        IceBlockProjectile projectile = ModEntities.ICE_BLOCK.get().create(level);
        if (projectile == null) {
            return null;
        }
        projectile.caster = caster;
        projectile.target = target;
        projectile.chargeTicks = Math.max(0, chargeTicks);
        projectile.entityData.set(DATA_SCALE, Math.max(0.05F, scale));
        if (target != null) {
            projectile.aim = target.getBoundingBox().getCenter();
        }
        // 从<b>身体内部</b>生成，然后在蓄力期间升到头顶 —— 而不是一出来就在头顶
        projectile.chargeStartY = caster.getY() + caster.getBbHeight() * 0.4D;
        projectile.setPos(caster.getX(), projectile.chargeStartY, caster.getZ());
        projectile.setYRot(caster.getYRot());
        projectile.setXRot(0.0F);
        return projectile;
    }

    /** 蓄力期间的横向偏移（左/中/右三连冰刺错开用）。 */
    public IceBlockProjectile withChargeOffset(Vec3 offset) {
        this.chargeOffset = offset == null ? Vec3.ZERO : offset;
        return this;
    }

    /** 渲染缩放。 */
    public float scale() {
        return this.entityData.get(DATA_SCALE);
    }

    private static double hoverY(LivingEntity caster) {
        return caster.getY() + caster.getBbHeight() + HOVER_HEIGHT;
    }

    /** 渲染用的方块状态；常量，不需要同步。 */
    public BlockState blockState() {
        return Blocks.ICE.defaultBlockState();
    }

    public boolean isFlying() {
        return this.entityData.get(DATA_FLYING);
    }

    /** 当前自转角度（度）。两端各算各的，不用同步。 */
    public float spinDegrees(float partialTick) {
        float ticks = this.tickCount + partialTick;
        return this.isFlying() ? ticks * SPIN_FLY : ticks * SPIN_CHARGE;
    }

    // ==================== 数据 / 存档 ====================

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(DATA_FLYING, false);
        entityData.define(DATA_SCALE, 1.0F);
    }

    /** 投射物是瞬态的，不存档。 */
    @Override
    protected void readAdditionalSaveData(CompoundTag input) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag output) {
    }

    /** 打不掉：它不是可以被攻击的对象。 */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    // ==================== tick ====================

    @Override
    public void tick() {
        super.tick();
        this.age++;

        if (this.age > MAX_LIFE) {
            this.discard();
            return;
        }

        // 客户端什么都不算：位置由引擎同步，旋转由 tickCount 推
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (this.isFlying()) {
            tickFlying(serverLevel);
        } else {
            tickCharging(serverLevel);
        }
    }

    /** 蓄力：从身体内部升到头顶，边升边自转；期间没有碰撞。 */
    private void tickCharging(ServerLevel level) {
        LivingEntity caster = this.caster;
        if (caster == null || !caster.isAlive()) {
            this.shatter(level, this.position());
            return;
        }

        double topY = hoverY(caster);
        double progress = this.chargeTicks <= 0
                ? 1.0D
                : Math.min(1.0D, (double) this.age / this.chargeTicks);
        double y = this.chargeStartY + (topY - this.chargeStartY) * progress;

        this.setPos(caster.getX() + this.chargeOffset.x, y, caster.getZ() + this.chargeOffset.z);
        this.setYRot(caster.getYRot());

        if (this.age >= this.chargeTicks) {
            this.launch(level);
        }
    }

    /** 发射：解除穿透，朝目标飞。 */
    private void launch(ServerLevel level) {
        this.entityData.set(DATA_FLYING, true);
        this.noPhysics = false;
        this.aim = this.target != null && this.target.isAlive()
                ? this.target.getBoundingBox().getCenter()
                : this.position().add(this.getLookAngle().scale(4.0D));

        // 一开始就指向目标，省得第一帧方向是零向量
        this.setDeltaMovement(this.directionTo(this.aim).scale(FLY_SPEED));
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.GLASS_PLACE, SoundSource.HOSTILE, 1.0F, 0.7F);
    }

    private void tickFlying(ServerLevel level) {
        LivingEntity target = this.target;
        Vec3 destination = target != null && target.isAlive()
                ? target.getBoundingBox().getCenter()
                : this.aim;

        this.setDeltaMovement(this.directionTo(destination).scale(FLY_SPEED));
        this.move(MoverType.SELF, this.getDeltaMovement());

        // 撞到目标
        if (target != null && target.isAlive()
                && this.getBoundingBox().inflate(0.3D).intersects(target.getBoundingBox())) {
            this.shatter(level, this.position());
            return;
        }

        // 撞墙 / 落地 / 飞到了落点
        if (this.horizontalCollision || this.verticalCollision || this.onGround()) {
            this.shatter(level, this.position());
            return;
        }
        if (this.position().distanceToSqr(destination) < 0.36D) {
            this.shatter(level, this.position());
        }
    }

    private Vec3 directionTo(Vec3 destination) {
        Vec3 delta = destination.subtract(this.position());
        return delta.lengthSqr() < 1.0E-6D ? this.getLookAngle() : delta.normalize();
    }

    // ==================== 碎裂 ====================

    /** 碎冰：破坏粒子 + 玻璃碎裂音 + 范围内伤害，然后消失。 */
    private void shatter(ServerLevel level, Vec3 at) {
        if (this.shattered) {
            return;
        }
        this.shattered = true;

        BlockPos pos = BlockPos.containing(at);
        // 2001 = 原版的「方块破坏」效果：粒子形态与音效都由方块自身决定，冰块天然合适
        level.levelEvent(2001, pos, Block.getId(this.blockState()));
        level.playSound(null, at.x, at.y, at.z,
                SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.0F, 0.9F);
        level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 24, 1.0D, 0.8D, 1.0D, 0.05D);

        ModDamageSpec spec = ModDamageSpec.builder(AttackType.MONSTER, ModElements.CYRO.get())
                .multiplier(0.0f)
                .flatBonus(IMPACT_DAMAGE)
                .elementAmount(0.0f)
                .build();
        ModDamageSource source = ModDamageSource.from(spec, this.caster != null ? this.caster : this);
        AABB area = AABB.ofSize(at, IMPACT_SIZE_XZ * 2.0D, IMPACT_SIZE_Y * 2.0D, IMPACT_SIZE_XZ * 2.0D);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (victim == this.caster || !victim.isAlive()) {
                continue;
            }
            victim.hurt(source, 0f);
        }

        this.discard();
    }
}
