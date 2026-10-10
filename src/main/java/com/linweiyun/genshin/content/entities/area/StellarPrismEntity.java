package com.linweiyun.genshin.content.entities.area;

import com.linweiyun.genshin.content.entities.ModEntities;
import com.linweiyun.genshin.content.effect.character.CharacterEffectHelper;
import com.linweiyun.genshin.content.effect.character.CharacterEffectInstance;
import com.linweiyun.genshin.content.effect.character.ICharacterEffect;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.util.capability.IStellarStateHolder;
import com.linweiyun.genshin.core.system.registry.register.ModCharacterEffects;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.content.entities.teyvat.TeyvatEntityStats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 极星辉域的棱镜本体。棱镜即领域：渲染为边长 0.5 米的冰块，三轴自转，不可摧毁，寿命 6 秒。
 */
public class StellarPrismEntity extends AreaEntity {

    /** 水平半径，单位格。 */
    public static final float FIELD_RADIUS = 8.0f;

    /** 域底在本体位置以下多少格。 */
    public static final float FIELD_BELOW = 2.0f;

    /** 域顶在本体位置以上多少格。 */
    public static final float FIELD_ABOVE = 6.0f;

    /** 领域寿命，单位刻。 */
    public static final int FIELD_TICKS = 120;

    /** 棱镜碰撞箱边长，单位格。 */
    private static final float PRISM_SIZE = 0.5f;

    private static final float YAW_SPEED = 3.0f;
    private static final float PITCH_SPEED = 1.7f;
    private static final float ROLL_SPEED = 2.3f;

    private static final String RES_SHRED_SOURCE = "stellar_conduce_field";
    private static final float RES_SHRED = -0.40f;
    private static final int RECORD_INTERVAL_TICKS = 80;
    private static final int RECORD_MAX_STACKS = 12;
    private static final float COEF_FIRST = 0.40f;
    private static final float COEF_PER_STACK = 0.05f;

    private int stacks;
    private int recordTimer = RECORD_INTERVAL_TICKS;
    private int releasedStacks;
    private final Set<UUID> shredded = new HashSet<>();

    public StellarPrismEntity(EntityType<? extends StellarPrismEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    @Override
    protected void serverTick() {
        super.serverTick();
        if (this.isRemoved()) {
            return;
        }
        if (--recordTimer <= 0) {
            recordTimer = RECORD_INTERVAL_TICKS;
            releasedStacks = stacks;
            stacks = 0;
        }
        if (this.tickCount % 10 == 0) {
            applyRadiance();
            applyResShred();
        }
    }

    /** 域内敌人物理抗性 −40%。 */
    private void applyResShred() {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Set<UUID> current = new HashSet<>();
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, fieldBox(),
                e -> !(e instanceof Player) && e.isAlive())) {
            current.add(entity.getUUID());
            TeyvatEntityStats stats = entity.getData(AttachmentRegistration.ENTITY_STATS);
            stats.attributes().setPercentModifier(ModAttributes.PHYSICAL_RES.value(), RES_SHRED_SOURCE, RES_SHRED);
        }
        for (UUID departed : new HashSet<>(shredded)) {
            if (!current.contains(departed)) {
                removeShred(level, departed);
            }
        }
        shredded.clear();
        shredded.addAll(current);
    }

    private void removeShred(ServerLevel level, UUID uuid) {
        if (level.getEntity(uuid) instanceof LivingEntity entity) {
            entity.getData(AttachmentRegistration.ENTITY_STATS).attributes()
                    .removeModifier(ModAttributes.PHYSICAL_RES.value(), RES_SHRED_SOURCE);
        }
    }

    private void applyRadiance() {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        ICharacterEffect effect = ModCharacterEffects.RADIANCE_STELLAR_CONDUCE_EFFECT.get();
        if (effect == null) {
            return;
        }
        for (Player player : level.getEntitiesOfClass(Player.class, this.fieldBox())) {
            PlayerCharactersAttachment attachment = player.getData(
                    AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            if (attachment == null) {
                continue;
            }
            PGCharacter character = attachment.getCurrentCharacter();
            if (character instanceof IStellarStateHolder holder && holder.canHoldStellarState()) {
                int duration = holder.stellarConduceDurationTicks();
                if (duration > 0) {
                    CharacterEffectHelper.addEffect(player, character,
                            new CharacterEffectInstance(effect, duration, 0, false));
                }
            }
            ICharacterEffect fieldEffect = ModCharacterEffects.STELLAR_CONDUCE_FIELD_EFFECT.get();
            if (fieldEffect != null && character != null) {
                CharacterEffectHelper.addEffect(player, character,
                        new CharacterEffectInstance(fieldEffect, 20, 0, false));
            }
        }
    }

    /** 域内发生一次冰/雷附着时记一层；其他元素不计。 */
    public static void recordAttachment(ServerLevel level, Vec3 at, com.linweiyun.elementlib.core.element.GenshinElement element) {
        if (element != com.linweiyun.genshin.core.element.ModElements.CYRO.get()
                && element != com.linweiyun.genshin.core.element.ModElements.ELECTRO.get()) {
            return;
        }
        StellarPrismEntity prism = find(level, at);
        if (prism != null) {
            prism.stacks = Math.min(RECORD_MAX_STACKS, prism.stacks + 1);
        }
    }

    /** 星超导伤害落在域内时记一次冰雷附着。 */
    public static void recordConduceHit(ServerLevel level, Vec3 at) {
        StellarPrismEntity prism = find(level, at);
        if (prism != null) {
            prism.stacks = Math.min(RECORD_MAX_STACKS, prism.stacks + 1);
        }
    }

    /** 该点当前吃到的星超导反应倍率（1.0 ~ 2.0）。 */
    public static float reactionCoefficient(net.minecraft.world.level.Level level, Vec3 at) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return 1f;
        }
        StellarPrismEntity prism = find(serverLevel, at);
        if (prism == null || prism.releasedStacks <= 0) {
            return 1f;
        }
        float bonus = COEF_FIRST + COEF_PER_STACK * prism.releasedStacks;
        return Math.min(2f, 1f + bonus);
    }

    @Nullable
    private static StellarPrismEntity find(ServerLevel level, Vec3 at) {
        for (StellarPrismEntity prism : level.getEntitiesOfClass(StellarPrismEntity.class, anchorSearchBox(at))) {
            if (prism.containsField(at)) {
                return prism;
            }
        }
        return null;
    }

    /** 该点所在领域当前已释放的冻存层数；不在领域内为 0。 */
    public static int releasedStacksAt(net.minecraft.world.level.Level level, Vec3 at) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return 0;
        }
        StellarPrismEntity prism = find(serverLevel, at);
        return prism == null ? 0 : prism.releasedStacks;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (this.level() instanceof ServerLevel level) {
            for (UUID uuid : shredded) {
                removeShred(level, uuid);
            }
        }
        shredded.clear();
        super.remove(reason);
    }

    /**
     * 在触发点生成或刷新极星辉域。
     *
     * <p>触发点在已有领域内时只刷新寿命；否则新生成一枚棱镜。
     */
    @Nullable
    public static StellarPrismEntity spawnOrRefresh(ServerLevel level, Vec3 at,
                                                    @Nullable Player owner, @Nullable PGCharacter character) {
        for (StellarPrismEntity prism : level.getEntitiesOfClass(StellarPrismEntity.class, anchorSearchBox(at))) {
            if (prism.containsField(at)) {
                prism.refreshLifetime();
                return prism;
            }
        }

        StellarPrismEntity prism = ModEntities.STELLAR_PRISM.get().create(level);
        if (prism == null) {
            return null;
        }
        prism.setPos(at.x, at.y, at.z);
        prism.setOwner(owner, character);
        prism.setDuration(FIELD_TICKS);
        level.addFreshEntity(prism);
        return prism;
    }

    /** 找「锚点可能落在 at 的领域内」的棱镜。 */
    private static AABB anchorSearchBox(Vec3 at) {
        return new AABB(
                at.x - FIELD_RADIUS, at.y - FIELD_ABOVE, at.z - FIELD_RADIUS,
                at.x + FIELD_RADIUS, at.y + FIELD_BELOW, at.z + FIELD_RADIUS);
    }

    public boolean containsField(Vec3 point) {
        double dx = point.x - this.getX();
        double dz = point.z - this.getZ();
        double dy = point.y - this.getY();
        return dx * dx + dz * dz <= (double) FIELD_RADIUS * FIELD_RADIUS
                && dy >= -FIELD_BELOW && dy <= FIELD_ABOVE;
    }

    public AABB fieldBox() {
        return new AABB(
                this.getX() - FIELD_RADIUS, this.getY() - FIELD_BELOW, this.getZ() - FIELD_RADIUS,
                this.getX() + FIELD_RADIUS, this.getY() + FIELD_ABOVE, this.getZ() + FIELD_RADIUS);
    }

    public BlockState blockState() {
        return Blocks.ICE.defaultBlockState();
    }

    public float spinYaw(float partialTick) {
        return (this.tickCount + partialTick) * YAW_SPEED;
    }

    public float spinPitch(float partialTick) {
        return (this.tickCount + partialTick) * PITCH_SPEED;
    }

    public float spinRoll(float partialTick) {
        return (this.tickCount + partialTick) * ROLL_SPEED;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(PRISM_SIZE, PRISM_SIZE);
    }
}
