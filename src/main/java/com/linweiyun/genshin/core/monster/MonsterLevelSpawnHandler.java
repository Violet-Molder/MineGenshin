package com.linweiyun.genshin.core.monster;

import com.linweiyun.genshin.content.entities.teyvat.TeyvatLiving;
import com.linweiyun.genshin.core.world.TeyvatWorldInvasion;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import org.slf4j.Logger;

import java.util.Objects;

@EventBusSubscriber
public class MonsterLevelSpawnHandler {
    public static final Logger LOGGER = ModLog.getLogger(LogGroup.CORE);

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!TeyvatWorldInvasion.get(serverLevel).isInvaded()) return;
        if (!(event.getEntity() instanceof Monster monster)) return;
        if (!(monster instanceof TeyvatLiving monsterLevel)) return;
        if (monsterLevel.getMonsterLevel() != 0) return;

        try {
            Vec3 pos = monster.position();
            long seed = monster.getUUID().getMostSignificantBits() ^ monster.getRandom().nextLong();
            int lv = MonsterLevelCalculator.getMonsterLevelNatural(serverLevel, pos, seed);
            monsterLevel.setMonsterLevel(lv);

            applyScaledStats(monsterLevel, lv, 1.0f);
        } catch (Exception e) {
            LOGGER.error("Failed to apply monster stats", e);
        }
    }

    public static void applyScaledStats(TeyvatLiving entity, int level, float environmentMultiplier) {
        entity.setEnvironmentMultiplier(environmentMultiplier);

        float healthMult = entity.getHealthMultiplier();
        float attackMult = entity.getAttackMultiplier();

        float scaledHealth = MonsterStatCalculator.calculateHealth(level, healthMult, environmentMultiplier);
        float scaledAttack = MonsterStatCalculator.calculateAttack(level, attackMult, environmentMultiplier);

        var living = (net.minecraft.world.entity.LivingEntity) entity;
        Objects.requireNonNull(living.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(scaledHealth);
        living.setHealth(scaledHealth);
        entity.setEntityStats(entity.getEntityStats().withAttack(scaledAttack));
    }
}