package com.linweiyun.genshin.content.entities.teyvat.skill.vesna;

import com.linweiyun.genshin.content.entities.ModEntities;
import com.linweiyun.genshin.content.skill_node.AreaEntityCollector;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.sword.vesna.Vesna;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.elementlib.core.system.combat.decay.DecayGroups;
import com.linweiyun.genshin.core.sync.ISyncManagedEntity;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.elementlib.api.ElementalReactionType;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import com.lowdragmc.lowdraglib2.syncdata.storage.IManagedStorage;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.List;
import com.linweiyun.genshin.core.system.registry.register.ModReactionTypes;

/**
 * 薇斯娜灵剑 —— 一次性打击实体。
 * <p>参照 {@code TalismanSpiritArea} / {@code ThunderCloudEntity} 的做法：
 * <ul>
 *   <li>不渲染任何几何体，只靠 {@code level().addParticle(...)} 出粒子</li>
 *   <li>两端逻辑分离：{@code clientTick()} 出粒子，{@code serverTick()} 结算伤害</li>
 * </ul>
 * 生命周期：悬停 {@link #HOVER_TICKS} tick → 飞行 {@link #FLIGHT_TICKS} tick → 服务端结算伤害并 discard。
 */
public class VesnaSpiritSwordEntity extends Entity implements ISyncManagedEntity {
    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CONTENT);

    public static final int HOVER_TICKS = 10;
    public static final int FLIGHT_TICKS = 6;
    public static final int TOTAL_TICKS = HOVER_TICKS + FLIGHT_TICKS;
    private static final int MAX_LIFE_TIME = TOTAL_TICKS + 20;

    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    @Override public Entity getSelf() { return this; }
    @Override public IManagedStorage getSyncStorage() { return syncStorage; }
    @Override public void notifyPersistence() {}

    @Persisted(key = "character") @DescSynced
    private Vesna character;

    @Persisted(key = "from_x") @DescSynced private double fromX;
    @Persisted(key = "from_y") @DescSynced private double fromY;
    @Persisted(key = "from_z") @DescSynced private double fromZ;

    @Persisted(key = "to_x") @DescSynced private double toX;
    @Persisted(key = "to_y") @DescSynced private double toY;
    @Persisted(key = "to_z") @DescSynced private double toZ;

    @Persisted(key = "multiplier") @DescSynced
    private float multiplier;

    @Persisted(key = "aoe_range") @DescSynced
    private float aoeRange;

    @Persisted(key = "stellar_swirl") @DescSynced
    private boolean stellarSwirl;
    /** 大权区加成（整肃层数 ×10%）。 */
    private float sovereigntyBonus;
    @Persisted(key = "element_amount") @DescSynced
    private float elementAmount;

    private boolean damageApplied = false;

    public VesnaSpiritSwordEntity(EntityType<? extends VesnaSpiritSwordEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static VesnaSpiritSwordEntity create(Level level, Vesna character,
                                                Vec3 from, Vec3 to,
                                                float multiplier, float aoeRange,
                                                boolean stellarSwirl, float elementAmount) {
        return create(level, character, from, to, multiplier, aoeRange, stellarSwirl, elementAmount, 0f);
    }

    /**
     * @param sovereigntyBonus 「大权」加成（整肃层数 ×10%）—— 灵剑属于吃整肃的那几段
     */
    public static VesnaSpiritSwordEntity create(Level level, Vesna character,
                                                Vec3 from, Vec3 to,
                                                float multiplier, float aoeRange,
                                                boolean stellarSwirl, float elementAmount,
                                                float sovereigntyBonus) {
        VesnaSpiritSwordEntity e = ModEntities.VESNA_SPIRIT_SWORD.get()
                .create(level, EntitySpawnReason.EVENT);
        if (e == null) return null;

        e.character = character;
        e.fromX = from.x; e.fromY = from.y; e.fromZ = from.z;
        e.toX = to.x;     e.toY = to.y;     e.toZ = to.z;
        e.multiplier = multiplier;
        e.aoeRange = aoeRange;
        e.stellarSwirl = stellarSwirl;
        e.elementAmount = elementAmount;
        e.sovereigntyBonus = sovereigntyBonus;

        e.setPos(from.x, from.y, from.z);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    // ==================== tick ====================

    @Override
    public void tick() {
        super.tick();

        if (tickCount >= MAX_LIFE_TIME) { discard(); return; }

        Vec3 pos = computePosition();
        setPos(pos.x, pos.y, pos.z);

        if (this.level().isClientSide()) {
            clientTick(pos);
        } else {
            serverTick();
        }
    }

    /** 客户端：出粒子 */
    private void clientTick(Vec3 pos) {
        Level level = this.level();
        if (level == null) return;

        // 剑刃：竖直 7 颗 END_ROD
        for (int i = -3; i <= 3; i++) {
            double dy = i * 0.18;
            level.addParticle(ParticleTypes.END_ROD,
                    pos.x, pos.y + dy, pos.z, 0, 0, 0);
        }
        // 剑柄：底部横排 3 颗
        Vec3 handle = pos.add(0, -0.75, 0);
        for (int i = -1; i <= 1; i++) {
            level.addParticle(ParticleTypes.END_ROD,
                    handle.x + i * 0.1, handle.y, handle.z, 0, 0, 0);
        }
        // 落地前 2 tick：落点光晕
        if (tickCount >= TOTAL_TICKS - 2) {
            for (int i = 0; i < 4; i++) {
                double rx = (level.getRandom().nextDouble() - 0.5) * 0.6;
                double rz = (level.getRandom().nextDouble() - 0.5) * 0.6;
                level.addParticle(ParticleTypes.END_ROD,
                        pos.x + rx, pos.y, pos.z + rz, 0, 0.03, 0);
            }
        }
    }

    /** 服务端：落地结算 + 移除 */
    private void serverTick() {
        if (tickCount >= TOTAL_TICKS && !damageApplied) {
            damageApplied = true;
            applyDamage();
            discard();
        }
    }

    // ==================== 位置计算 ====================

    private Vec3 computePosition() {
        Vec3 from = new Vec3(fromX, fromY, fromZ);
        Vec3 to = new Vec3(toX, toY, toZ);
        if (tickCount <= HOVER_TICKS) {
            double bob = Math.sin(tickCount * 0.4) * 0.08;
            return from.add(0, bob, 0);
        }
        float u = Math.min(1f, (float) (tickCount - HOVER_TICKS) / FLIGHT_TICKS);
        return from.lerp(to, u);
    }

    // ==================== 服务端伤害 ====================

    private void applyDamage() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        if (character == null) return;
        Player owner = character.getData().getOwnerPlayer();
        if (owner == null) return;

        Vec3 center = new Vec3(toX, toY, toZ);
        List<LivingEntity> targets = new AreaEntityCollector(serverLevel,
                center.add(-aoeRange, -aoeRange, -aoeRange),
                center.add(aoeRange, aoeRange, aoeRange),
                aoeRange).execute();

        for (LivingEntity target : targets) {
            if (target == owner) continue;

            ModDamageSpec spec;
            if (stellarSwirl) {
                // ⚠️ 形参顺序是 (elementAmount, stellarCoefficient)。星烁管线的伤害只认
                // stellarCoefficient（伤害 = 攻击力 × 系数），elementAmount 只影响附着。
                // 这里原来是 {@code (multiplier, 0.5f)} —— 倍率被当成元素量丢掉、系数写死 0.5，
                // 于是「星扩散状态下的灵剑」伤害恒为 ATK×0.5，与技能等级/阶级完全无关
                //（二阶第二段低 2.2~5.3 倍、三阶收尾低 3.1~7.4 倍、大招灵剑低 5.3~12.5 倍）。
                spec = ModDamageSpec.stellarDirect(
                        ModReactionTypes.STELLAR_SWIRL_WIND.get(), ModElements.ANEMO.get(),
                        elementAmount, multiplier)
                        .withStellarBaseBonusMult(
                                com.linweiyun.genshin.core.system.reaction.StellarGlimmer
                                        .swirlBaseBonusMult(serverLevel))
                        .withSovereignty(sovereigntyBonus);
                spec.setStellarContributors(List.of((PGCharacter) character));
            } else {
                spec = ModDamageSpec.builder(AttackType.ELEMENTAL_SKILL, ModElements.ANEMO.get())
                        .multiplier(multiplier)
                        .elementAmount(elementAmount)
                        .decayGroup(DecayGroups.DEFAULT_ELEMENTAL_SKILL)
                        .attackerCharacter(character)
                        .build()
                        .withSovereignty(sovereigntyBonus);
            }
            ModDamageSource source = ModDamageSource.from(spec, owner);
            target.hurtServer(serverLevel, source, 0f);
        }
    }

    // ==================== 杂项 ====================

    @Override
    public boolean hurtServer(ServerLevel serverLevel, DamageSource damageSource, float v) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueInput) {
        loadManagedPersistentData(valueInput);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueOutput) {
        saveManagedPersistentData(valueOutput, false);
    }
}