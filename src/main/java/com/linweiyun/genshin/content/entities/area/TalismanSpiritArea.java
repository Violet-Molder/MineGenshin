package com.linweiyun.genshin.content.entities.area;

import com.linweiyun.genshin.config.character.ShenheTalentConfig;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.content.entities.teyvat.TeyvatEntityStats;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TalismanSpiritArea extends AreaEntity {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CONTENT);

    // ========== 领域常量配置 ==========

    // 伤害触发间隔（tick）—— 每1秒（20tick）对范围敌人造成一次伤害
    private static final int DAMAGE_INTERVAL_TICKS = 20;

    // 领域总持续时间（tick）—— 30秒 = 600tick
    private static final int FIELD_DURATION = 30 * 20;

    // 水平半径（格）
    private static final float HORIZONTAL_RADIUS = 6.0f;

    // 垂直半径（格）
    private static final float VERTICAL_RADIUS = 2.0f;

    // 领域唯一标识UUID
    private static final int FIELD_UUID = 612001;

    // 冰伤加成属性来源键 —— 多个领域不叠加
    private static final String CYRO_BONUS_SOURCE = "shenhe.talent.ascend2";

    // 冰伤加成值
    private static final double CYRO_BONUS_VALUE = 0.15;

    // 抗性降低来源键 —— 领域内敌人冰/物抗降低12%
    private static final String RES_SHRED_SOURCE = "shenhe.burst";

    // 抗性降低值
    private static final double RES_SHRED_VALUE = -0.12;

    // ========== 实例字段 ==========

    // 伤害计时器 —— 累计tick数，每 DAMAGE_INTERVAL_TICKS 次触发一次伤害
    private int damageTickCounter = 0;

    // 粒子位置缓存列表 —— 客户端渲染用
    private final List<Vec3> particlePositions = new ArrayList<>();

    // 粒子位置是否已计算 —— 避免每tick重复计算
    private boolean particlesCalculated = false;

    // 上一帧被加成的玩家UUID集合 —— 用于检测离开领域后移除加成
    private final Set<UUID> lastBuffedPlayers = new HashSet<>();

    // 上一帧被降低抗性的敌人UUID集合 —— 用于检测离开领域后还原抗性
    private final Set<UUID> lastShreddedEntities = new HashSet<>();

    /**
     * 构造函数 —— 初始化镇灵之鼎领域
     */
    public TalismanSpiritArea(EntityType<?> type, Level level) {
        super(type, level);
        setShapeType(AreaShapeType.CYLINDER);
        setHorizontalRadius(HORIZONTAL_RADIUS);
        setVerticalRadius(VERTICAL_RADIUS);
        setFieldUUID(FIELD_UUID);
        setDuration(FIELD_DURATION);
        // 生成日志：把「这一次到底打算活多久」写出来。
        // 排查「领域不消亡」时，先看这一行是不是 600 —— 是的话问题在寿命没被推进（看 AreaEntity 的心跳日志）。
        LOGGER.info("[镇灵之鼎] 生成：持续 {} 刻（{} 秒）", FIELD_DURATION, FIELD_DURATION / 20);
    }

    // ========== Tick 逻辑 ==========

    @Override
    protected void serverTick() {
        super.serverTick();

        // 父类可能在持续时间到期后调用 discard() 移除实体
        if (this.isRemoved()) {
            clearAllBuffs();
            clearAllResShreds();
            return;
        }

        damageTickCounter++;

        if (damageTickCounter >= DAMAGE_INTERVAL_TICKS) {
            damageTickCounter = 0;
            applyFieldDamage();
            applyFieldBuff();
            applyResShred();
        }
    }

    @Override
    protected void clientTick() {
        if (!particlesCalculated) {
            calculateParticlePositions();
            particlesCalculated = true;
        }
        spawnCachedParticles();
    }

    // ========== 领域伤害逻辑 ==========

    private void applyFieldDamage() {
        double centerX = this.getX();
        double centerY = this.getY();
        double centerZ = this.getZ();
        float radius = getHorizontalRadius();
        float halfHeight = getVerticalRadius();

        AABB cylinderBounds = new AABB(
                centerX - radius, centerY - halfHeight, centerZ - radius,
                centerX + radius, centerY + halfHeight, centerZ + radius);

        List<LivingEntity> entitiesInRange = this.level().getEntitiesOfClass(
                LivingEntity.class, cylinderBounds, this::isEntityInCylinder);

        Player owner = getOwner();
        PGCharacter ownerCharacter = getOwnerCharacter();

        if (owner == null || ownerCharacter == null) return;

        for (LivingEntity entity : entitiesInRange) {
            if (entity.equals(owner)) continue;
            dealDamageToEntity(entity);
        }
    }

    private boolean isEntityInCylinder(LivingEntity entity) {
        double centerX = this.getX();
        double centerY = this.getY();
        double centerZ = this.getZ();
        float radius = getHorizontalRadius();
        float halfHeight = getVerticalRadius();

        Vec3 entityPos = entity.position();
        double horizontalDistSq =
                (entityPos.x - centerX) * (entityPos.x - centerX)
                        + (entityPos.z - centerZ) * (entityPos.z - centerZ);
        double verticalDist = Math.abs(entityPos.y - centerY);

        return horizontalDistSq <= radius * radius && verticalDist <= halfHeight;
    }

    private void dealDamageToEntity(LivingEntity target) {
        if (!target.isAlive()) return;

        int burstLevel = this.character.getData().getElementalBurstLevel();
        float damageMultiplier = ShenheTalentConfig.getBurstDotDamage(burstLevel);
        ModDamageSpec spec = ModDamageSpec.builder(AttackType.ELEMENTAL_BURST, ModElements.CYRO.get())
                .multiplier(damageMultiplier)
                .elementAmount(1.0f)
                .attackerCharacter(this.character)
                .build();

        ModDamageSource damageSource = ModDamageSource.from(spec, owner);
        if (target.level() instanceof ServerLevel serverLevel) {
            target.hurtServer(serverLevel, damageSource, 0f);
        }
    }

    // ========== 领域冰伤加成 —— 属性系统实现 ==========

    /**
     * 对领域内所有玩家当前角色施加15%冰元素伤害加成
     * 使用属性系统的 tempFlatModifiers，source 为 "shenhe.talent.ascend2"
     * 联机模式下多个领域不叠加（同一 source 的 set 操作覆盖旧值）
     */
    private void applyFieldBuff() {
        // 突破天赋1：角色突破等级>=1时冰伤加成才生效
        if (this.character == null || this.character.getData().getAscensionPhase() < 1) return;

        double centerX = this.getX();
        double centerY = this.getY();
        double centerZ = this.getZ();
        float radius = getHorizontalRadius();
        float halfHeight = getVerticalRadius();

        AABB cylinderBounds = new AABB(
                centerX - radius, centerY - halfHeight, centerZ - radius,
                centerX + radius, centerY + halfHeight, centerZ + radius);

        List<Player> playersInRange = this.level().getEntitiesOfClass(
                Player.class, cylinderBounds, this::isEntityInCylinder);

        Set<UUID> currentPlayers = new HashSet<>();

        for (Player player : playersInRange) {
            currentPlayers.add(player.getUUID());

            PlayerCharactersAttachment attachment =
                    player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            PGCharacter current = attachment.getCurrentCharacter();
            if (current == null) continue;

            current.getData().setAttributeTempFlatModifier(
                    ModAttributes.CYRO_BONUS.value(),
                    CYRO_BONUS_SOURCE,
                    CYRO_BONUS_VALUE);
        }

        // 移除离开领域玩家的加成
        for (UUID departed : new HashSet<>(lastBuffedPlayers)) {
            if (!currentPlayers.contains(departed)) {
                removePlayerBuff(departed);
            }
        }

        lastBuffedPlayers.clear();
        lastBuffedPlayers.addAll(currentPlayers);
    }

    private void removePlayerBuff(UUID playerUuid) {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        ServerPlayer serverPlayer = serverLevel.getServer().getPlayerList().getPlayer(playerUuid);
        if (serverPlayer == null) return;

        PlayerCharactersAttachment attachment =
                serverPlayer.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        PGCharacter current = attachment.getCurrentCharacter();
        if (current == null) return;

        current.getData().removeAttributeModifier(
                ModAttributes.CYRO_BONUS.value(),
                CYRO_BONUS_SOURCE);
    }

    /**
     * 领域消失时清除所有被加成玩家的冰伤加成
     */
    private void clearAllBuffs() {
        for (UUID uuid : lastBuffedPlayers) {
            removePlayerBuff(uuid);
        }
        lastBuffedPlayers.clear();
    }

    // ========== 领域抗性降低 —— 冰/物抗降低12% ==========

    private void applyResShred() {
        double centerX = this.getX();
        double centerY = this.getY();
        double centerZ = this.getZ();
        float radius = getHorizontalRadius();
        float halfHeight = getVerticalRadius();

        AABB cylinderBounds = new AABB(
                centerX - radius, centerY - halfHeight, centerZ - radius,
                centerX + radius, centerY + halfHeight, centerZ + radius);

        List<LivingEntity> entitiesInRange = this.level().getEntitiesOfClass(
                LivingEntity.class, cylinderBounds, this::isEntityInCylinder);

        Player owner = getOwner();
        if (owner == null) return;

        Set<UUID> currentEntities = new HashSet<>();

        for (LivingEntity entity : entitiesInRange) {
            if (entity.equals(owner)) continue;
            currentEntities.add(entity.getUUID());

            TeyvatEntityStats stats = entity.getData(AttachmentRegistration.ENTITY_STATS);
            // 减抗是乘算（基于目标自身抗性），所以走百分比修饰符：0.1 × (1 - 0.12) = 0.088
            stats.attributes().setPercentModifier(ModAttributes.CYRO_RES.value(), RES_SHRED_SOURCE, RES_SHRED_VALUE);
            stats.attributes().setPercentModifier(ModAttributes.PHYSICAL_RES.value(), RES_SHRED_SOURCE, RES_SHRED_VALUE);
        }

        // 移除离开领域敌人的抗性降低
        for (UUID departed : new HashSet<>(lastShreddedEntities)) {
            if (!currentEntities.contains(departed)) {
                removeEntityResShred(departed);
            }
        }

        lastShreddedEntities.clear();
        lastShreddedEntities.addAll(currentEntities);
    }

    private void removeEntityResShred(UUID entityUuid) {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        LivingEntity entity = (LivingEntity) serverLevel.getEntity(entityUuid);
        if (entity == null) return;

        TeyvatEntityStats stats = entity.getData(AttachmentRegistration.ENTITY_STATS);
        stats.attributes().removeModifier(ModAttributes.CYRO_RES.value(), RES_SHRED_SOURCE);
        stats.attributes().removeModifier(ModAttributes.PHYSICAL_RES.value(), RES_SHRED_SOURCE);
    }

    private void clearAllResShreds() {
        for (UUID uuid : lastShreddedEntities) {
            removeEntityResShred(uuid);
        }
        lastShreddedEntities.clear();
    }

    // ========== 临时粒子效果（标注领域范围） ==========
    // TODO: 实际特效添加后，此方法及相关字段应移除

    private void calculateParticlePositions() {
        double centerX = this.getX();
        double centerY = this.getY();
        double centerZ = this.getZ();
        float radius = getHorizontalRadius();
        float halfHeight = getVerticalRadius();

        int circleParticles = 32;
        for (int i = 0; i < circleParticles; i++) {
            double angle = 2 * Math.PI * i / circleParticles;
            double x = centerX + radius * Math.cos(angle);
            double z = centerZ + radius * Math.sin(angle);
            particlePositions.add(new Vec3(x, centerY + halfHeight, z));
            particlePositions.add(new Vec3(x, centerY - halfHeight, z));
        }

        int verticalSteps = 16;
        for (int i = 0; i < verticalSteps; i++) {
            double y = centerY - halfHeight + (2 * halfHeight * i / (verticalSteps - 1));
            for (int j = 0; j < 4; j++) {
                double angle = 2 * Math.PI * j / 4;
                double x = centerX + radius * Math.cos(angle);
                double z = centerZ + radius * Math.sin(angle);
                particlePositions.add(new Vec3(x, y, z));
            }
        }
    }

    private void spawnCachedParticles() {
        for (Vec3 pos : particlePositions) {
            this.level().addParticle(ParticleTypes.END_ROD,
                    pos.x, pos.y, pos.z, 0, 0.05, 0);
        }
    }
}