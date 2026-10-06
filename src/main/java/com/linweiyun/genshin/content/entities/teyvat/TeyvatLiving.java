package com.linweiyun.genshin.content.entities.teyvat;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.genshin.core.system.control.Controllable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * 提瓦特侧活体的公共面。
 *
 * <p>它包含 {@link Controllable} —— <b>打断动作与击退是实体内置方法，
 * 控制只有 {@code applyControl} 一个入口</b>（见那个接口的注释）。
 * 本模组的怪与首领可以直接覆盖它们；原版生物由
 * {@code LivingEntityTeyvatMixin} 注入 {@code NonTeyvatEntity}
 * 拿到同一套默认实现，所以「每一个活体都吃同一套控制规则」。
 */
public interface TeyvatLiving extends Controllable {

    default TeyvatEntityStats getEntityStats() {
        return ((LivingEntity) this).getData(AttachmentRegistration.ENTITY_STATS.get());
    }

    default void setEntityStats(TeyvatEntityStats stats) {
        ((LivingEntity) this).setData(AttachmentRegistration.ENTITY_STATS.get(), stats);
    }

    default int getMonsterLevel() {
        return getEntityStats().level();
    }

    default void setMonsterLevel(int level) {
        setEntityStats(getEntityStats().withLevel(level));
    }

    default float getEnvironmentMultiplier() {
        return getEntityStats().environmentMultiplier();
    }

    default void setEnvironmentMultiplier(float multiplier) {
        setEntityStats(getEntityStats().withEnvironmentMultiplier(multiplier));
    }

    default float getHealthMultiplier() {
        return 1.0f;
    }

    default float getAttackMultiplier() {
        return 1.0f;
    }

    default float getMonsterAttack() {
        return getEntityStats().attack();
    }

    default void setMonsterAttack(float attack) {
        setEntityStats(getEntityStats().withAttack(attack));
    }

    default int getDefense() {
        return getEntityStats().getDefense();
    }

    default float getElementResistance(GenshinElement element) {
        return getEntityStats().getElementResistance(element);
    }

    default float getPhysicalResistance() {
        return getEntityStats().physicalResistance();
    }

    default int getCombatTicks() {
        return getEntityStats().combatTicks();
    }

    default void setCombatTicks(int ticks) {
        setEntityStats(getEntityStats().withCombatTicks(ticks));
    }

    default boolean isTargeting() {
        return getEntityStats().targeting();
    }

    default void setTargeting(boolean targeting) {
        setEntityStats(getEntityStats().withTargeting(targeting));
    }

    default int getCombatDuration() {
        return 100;
    }

    default boolean isInCombat() {
        return isTargeting() || getCombatTicks() > 0;
    }

    default void resetCombat() {
        setCombatTicks(getCombatDuration());
    }

    default void setAiEnabled(boolean enabled) {
        if (this instanceof Mob mob) {
            mob.setNoAi(!enabled);
        }
    }
}
