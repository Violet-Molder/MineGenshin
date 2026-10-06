package com.linweiyun.genshin.core.system.combat.damage;

import com.linweiyun.genshin.content.entities.teyvat.TeyvatLiving;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import net.minecraft.world.entity.LivingEntity;

/**
 * 统一从攻击方 / 被攻击方身上获取等级、防御、元素抗性。
 * 来源优先级：
 *   1. PGCharacter     → 角色数据里读
 *   2. TeyvatLiving    → Mixin 注入的实体数据
 *   3. 默认值兜底
 */
public final class CombatEntityAccessor {
    private CombatEntityAccessor() {}

    /** ITeyvatEntity 和普通 LivingEntity 的默认等级 */
    private static final int DEFAULT_MOB_LEVEL = 10;
    /** ITeyvatEntity 和普通 LivingEntity 的默认元素抗性 10% */
    private static final float DEFAULT_MOB_RESISTANCE = 0.10f;

    // ==================== 等级 ====================

    /**
     * 获取攻击方等级
     */
    public static int getAttackerLevel(LivingEntity attacker, PGCharacter attackerCharacter) {
        if (attackerCharacter != null) {
            return attackerCharacter.getData().getLevel();
        }
        if (attacker instanceof TeyvatLiving ml && ml.getMonsterLevel() > 0) {
            return ml.getMonsterLevel();
        }
        return DEFAULT_MOB_LEVEL;
    }

    /**
     * 获取被攻击方等级（剧变反应等级系数用，防御公式本身不用）
     */
    public static int getDefenderLevel(LivingEntity defender, PGCharacter defenderCharacter) {
        if (defenderCharacter != null) {
            return defenderCharacter.getData().getLevel();
        }
        if (defender instanceof TeyvatLiving ml && ml.getMonsterLevel() > 0) {
            return ml.getMonsterLevel();
        }
        return DEFAULT_MOB_LEVEL;
    }

    public static float getDamageBonus(PGCharacter character, GenshinElement element) {
        if (character == null) return 0;
        return getElementBonusFromCharacter(character, element);
    }

    // ==================== 防御力 ====================

    /**
     * 获取被攻击方防御力
     * PGCharacter → 角色 DEF 属性
     * TeyvatLiving / 普通 LivingEntity → 怪物防御力 = 等级系数 = level×500+500
     */
    public static double getDefenderDefense(LivingEntity defender, PGCharacter defenderCharacter) {
        if (defenderCharacter != null) {
            return defenderCharacter.getData().getAttributeTotalValue(ModAttributes.DEF.value());
        }
        if (defender instanceof TeyvatLiving ml && ml.getMonsterLevel() > 0) {
            return ml.getDefense();
        }
        return CombatMath.levelCoefficient(DEFAULT_MOB_LEVEL);
    }

    // ==================== 元素加成 ====================
    private static float getElementBonusFromCharacter(PGCharacter character, GenshinElement elemental) {
        var data = character.getData();
        // ⚠️ 物理也要单独一档：增伤区是「1 + 元素伤害加成」，物理伤害那一份就是
        //    PHYSICAL_BONUS（物伤杯走的就是它）。原来这里没有 FYSIKOS 的分支 →
        //    物理伤害恒吃 0 加成，而抗性那边（getDefenderResistance）是<b>有</b>物理分支的，
        //    一正一反正好对不上。
        if (elemental == ModElements.FYSIKOS.get()) return physicalBonus(data);
        if (elemental == ModElements.PYRO.get())    return (float) data.getAttributeTotalValue(ModAttributes.PYRO_BONUS.value());
        if (elemental == ModElements.HYDRO.get())   return (float) data.getAttributeTotalValue(ModAttributes.HYDRO_BONUS.value());
        if (elemental == ModElements.DENDRO.get())  return (float) data.getAttributeTotalValue(ModAttributes.DENDRO_BONUS.value());
        if (elemental == ModElements.ELECTRO.get()) return (float) data.getAttributeTotalValue(ModAttributes.ELECTRO_BONUS.value());
        if (elemental == ModElements.ANEMO.get())   return (float) data.getAttributeTotalValue(ModAttributes.ANEMO_BONUS.value());
        if (elemental == ModElements.CYRO.get())    return (float) data.getAttributeTotalValue(ModAttributes.CYRO_BONUS.value());
        if (elemental == ModElements.GEO.get())     return (float) data.getAttributeTotalValue(ModAttributes.GEO_BONUS.value());
        return 0.0f;
    }

    /** 物理伤害加成（物伤杯 / 物理伤害加成词条）。 */
    private static float physicalBonus(com.linweiyun.genshin.core.character.PGCharacterData data) {
        return (float) data.getAttributeTotalValue(ModAttributes.PHYSICAL_BONUS.value());
    }

    // ==================== 元素抗性 ====================

    /**
     * 获取被攻击方对应元素抗性（小数，如 0.3 = 30%）
     */
    public static float getDefenderResistance(LivingEntity defender, PGCharacter defenderCharacter, GenshinElement element) {
        if (element == ModElements.FYSIKOS.get()) {
            return getPhysicalResistance(defender, defenderCharacter);
        }
        if (defenderCharacter != null) {
            return getElementResistanceFromCharacter(defenderCharacter, element);
        }
        if (defender instanceof TeyvatLiving ml) {
            return ml.getElementResistance(element);
        }
        return DEFAULT_MOB_RESISTANCE;
    }



    private static float getElementResistanceFromCharacter(PGCharacter c, GenshinElement element) {
        var data = c.getData();
        if (element == ModElements.PYRO.get())    return (float) data.getAttributeTotalValue(ModAttributes.PYRO_RES.value());
        if (element == ModElements.HYDRO.get())   return (float) data.getAttributeTotalValue(ModAttributes.HYDRO_RES.value());
        if (element == ModElements.DENDRO.get())  return (float) data.getAttributeTotalValue(ModAttributes.DENDRO_RES.value());
        if (element == ModElements.ELECTRO.get()) return (float) data.getAttributeTotalValue(ModAttributes.ELECTRO_RES.value());
        if (element == ModElements.ANEMO.get())   return (float) data.getAttributeTotalValue(ModAttributes.ANEMO_RES.value());
        if (element == ModElements.CYRO.get())    return (float) data.getAttributeTotalValue(ModAttributes.CYRO_RES.value());
        if (element == ModElements.GEO.get())     return (float) data.getAttributeTotalValue(ModAttributes.GEO_RES.value());
        return 0.0f;
    }

    private static float getPhysicalResistance(LivingEntity defender, PGCharacter defenderCharacter) {
        if (defenderCharacter != null) {
            return (float) defenderCharacter.getData().getAttributeTotalValue(ModAttributes.PHYSICAL_RES.value());
        }
        if (defender instanceof TeyvatLiving ml) {
            return ml.getPhysicalResistance();
        }
        return DEFAULT_MOB_RESISTANCE;
    }
}