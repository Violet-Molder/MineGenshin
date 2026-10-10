package com.linweiyun.genshin.content.entities.teyvat.skill.miyabi;

import com.linweiyun.genshin.content.entities.area.StellarPrismEntity;

import com.linweiyun.genshin.core.system.registry.register.ModReactionTypes;

import com.linweiyun.genshin.core.system.reaction.StellarGlimmer;

import com.linweiyun.genshin.core.character.sword.miyabi.Miyabi;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.genshin.core.system.combat.CombatAim;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.genshin.content.entities.ModEntities;
import com.linweiyun.elementlib.core.system.about.AttachmentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 星见雅重击那一下的斩击 —— 一枚沿视线方向直线飞出去的斩击模型。
 *
 * <p><b>伤害由它自己按碰撞结算</b>：一路飞过去，碰到谁就给谁一下（同一个目标只吃一次），
 * 撞到方块、或者飞满 {@value #LIFETIME_TICKS} 刻就自己消失。素材侧的斩击实体也是这个做法。
 *
 * <p>模型 / 贴图 / 动画全部来自联动模组，本 MOD 里没有这些文件（见 {@link MiyabiSlashGeoModel}）。
 * 位置由服务端推进、客户端只跟渲染视图。
 */
public class MiyabiSlashEffect extends Entity implements GeoEntity {

    /** 活多久（刻）。 */
    private static final int LIFETIME_TICKS = 40;

    /** 每刻前进多少格。 */
    private static final double SPEED = 1.0;

    /** 生成点：身前多远（格）。 */
    private static final double SPAWN_FORWARD = 1.0;

    /** 生成点：脚底以上多高（格）。 */
    private static final double SPAWN_HEIGHT = 1.0;

    /** 这一刀的倍率（占攻击力）。 */
    private static final float MULTIPLIER = 1.0f;

    private float multiplier = MULTIPLIER;

    private boolean stellar;

    /** 碰撞盒外扩：横向（格）。 */
    private static final double HIT_INFLATE_X = 1.0;

    /** 碰撞盒外扩：竖向（格）—— 斩击是一道横着的弧，站高一级的目标也要够到。 */
    private static final double HIT_INFLATE_Y = 2.0;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** 每刻的位移；客户端不推进，位置由服务端同步过来。 */
    private Vec3 movement = Vec3.ZERO;

    /** 施法者；只有服务端用得到（算伤害源）。 */
    private UUID ownerUuid;

    /** 已经吃这一道剑气一下的目标。 */
    private final Set<UUID> damaged = new HashSet<>();

    public MiyabiSlashEffect(EntityType<? extends MiyabiSlashEffect> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    /**
     * 在施法者身前生成一道斩击。
     *
     * @param spread 相对视线的水平偏角（写成斜边为 1 的那条直角边，也就是正切值）；0 = 正前方，±0.6 ≈ ±31°
     */
    public static void spawn(ServerLevel level, Player owner, double spread) {
        spawn(level, owner, spread, MULTIPLIER, false);
    }

    public static void spawn(ServerLevel level, Player owner, double spread, float multiplier, boolean stellar) {
        MiyabiSlashEffect slash = new MiyabiSlashEffect(ModEntities.MIYABI_SLASH.get(), level);
        // 朝「角色朝向」（身体偏航 yBodyRot，和 moves 位移 / 攻击判定同一套基准）而不是头部视线：
        // 视角是自由的，按视线会让剑气跟着镜头转。
        Vec3 facing = CombatAim.horizontal(owner);

        slash.setPos(owner.getX() + facing.x * SPAWN_FORWARD,
                owner.getY() + SPAWN_HEIGHT,
                owner.getZ() + facing.z * SPAWN_FORWARD);
        slash.launch(facing.x + facing.z * spread, 0.0, facing.z - facing.x * spread);
        slash.ownerUuid = owner.getUUID();
        slash.multiplier = multiplier;
        slash.stellar = stellar;
        level.addFreshEntity(slash);
    }

    public static void spawnDirection(ServerLevel level, Player owner, double dx, double dz,
                                      float multiplier, boolean stellar) {
        MiyabiSlashEffect slash = new MiyabiSlashEffect(ModEntities.MIYABI_SLASH.get(), level);
        slash.setPos(owner.getX() + dx, owner.getY() + SPAWN_HEIGHT, owner.getZ() + dz);
        slash.launch(dx, 0.0, dz);
        slash.ownerUuid = owner.getUUID();
        slash.multiplier = multiplier;
        slash.stellar = stellar;
        level.addFreshEntity(slash);
    }

    /** 设定飞行方向（会被归一化），并按方向摆好朝向 —— 角度记在实体上，渲染器照着转模型。 */
    public void launch(double dx, double dy, double dz) {
        Vec3 direction = new Vec3(dx, dy, dz);
        if (direction.lengthSqr() < 1.0E-6) {
            discard();
            return;
        }

        setYRot((float) (Mth.atan2(dx, dz) * (180.0 / Math.PI)));
        setXRot((float) (Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180.0 / Math.PI)));
        this.movement = direction.normalize().scale(SPEED);
        setDeltaMovement(this.movement);
    }

    @Override
    public void tick() {
        super.tick();
        setNoGravity(true);

        if (level().isClientSide()) {
            return;
        }

        if (tickCount > LIFETIME_TICKS || this.movement.lengthSqr() < 1.0E-6) {
            discard();
            return;
        }

        Vec3 from = position();
        Vec3 to = from.add(this.movement);
        BlockHitResult hit = level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() != HitResult.Type.MISS) {
            discard();
            return;
        }

        setPos(to.x, to.y, to.z);
        setDeltaMovement(this.movement);
        damageTouchedTargets();
    }

    /** 这一格扫到的目标各吃一下 —— 同一个目标只吃一次。 */
    private void damageTouchedTargets() {
        Player owner = owner();
        if (owner == null) {
            return;
        }

        for (Entity touched : level().getEntities(this,
                getBoundingBox().inflate(HIT_INFLATE_X, HIT_INFLATE_Y, HIT_INFLATE_X))) {
            if (!(touched instanceof LivingEntity target) || target == owner
                    || !target.isAlive() || !damaged.add(target.getUUID())) {
                continue;
            }

            PGCharacter character = characterOf(owner);
            ModDamageSpec spec;
            if (stellar) {
                if (character instanceof Miyabi miyabi) {
                    miyabi.setConvertedHit(true);
                }
                spec = ModDamageSpec.stellarDirect(ModReactionTypes.STELLAR_CONDUCE_ICE.get(),
                                ModElements.CYRO.get(), AttachmentType.WEAK.getInitialAmount(), multiplier)
                        .withStellarBaseBonusMult(StellarGlimmer.conduceBaseBonusMult(level()))
                        .withStellarReactionCoefficient(
                                StellarPrismEntity.reactionCoefficient(level(), target.position()));
                spec.setStellarContributors(java.util.List.of(character));
            } else {
                spec = ModDamageSpec.builder(AttackType.CHARGED_ATTACK, ModElements.CYRO.get())
                        .multiplier(multiplier)
                        .elementAmount(AttachmentType.WEAK.getInitialAmount())
                        .attackerCharacter(character)
                        .build();
            }
            target.hurt(ModDamageSource.from(spec, owner), 0f);
            if (character instanceof Miyabi miyabi) {
                miyabi.setConvertedHit(false);
            }
            if (stellar && level() instanceof ServerLevel serverLevel) {
                StellarPrismEntity.recordAttachment(serverLevel, target.position(), ModElements.CYRO.get());
            }
        }
    }

    @Nullable
    private Player owner() {
        return ownerUuid != null && level() instanceof ServerLevel serverLevel
                ? serverLevel.getPlayerByUUID(ownerUuid)
                : null;
    }

    @Nullable
    private static PGCharacter characterOf(Player owner) {
        PlayerCharactersAttachment attachment =
                owner.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        return attachment == null ? null : attachment.getCurrentCharacter();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0,
                state -> state.setAndContinue(RawAnimation.begin().thenLoop("idle"))));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
