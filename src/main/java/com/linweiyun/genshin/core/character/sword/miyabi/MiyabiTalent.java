package com.linweiyun.genshin.core.character.sword.miyabi;

import com.linweiyun.genshin.core.character.talent.TalentBase;
import com.linweiyun.genshin.core.character.util.capability.IStellarHousehold;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.reaction.StellarGlimmerBranch;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;

/**
 * 星见雅的突破天赋。
 *
 * <p>目前只有一条<b>星超导户口</b>：本角色在队伍里时星超导成立，并按她自己的攻击力
 * 给全队星超导的基础伤害提升（每 100 点攻击力一档、每档 +0.7%、上限 +14%）。
 */
public class MiyabiTalent extends TalentBase {

    /** 每 100 点攻击力提升一档。 */
    public static final double BONUS_ATK_PER_STAGE = 100.0;

    /** 每档 +0.7%。 */
    public static final float BONUS_PER_STAGE = 0.007f;

    /** 上限 +14%。 */
    public static final float BONUS_CAP = 0.14f;

    /** 重击剑气的冰伤倍率（占攻击力），索引 = 技能等级 - 1。 */
    private static final float[] SWORD_QI = {
            0.92f, 1.035f, 1.15f, 1.265f, 1.38f, 1.495f, 1.61f, 1.725f, 1.84f,
            2.10f, 2.36f, 2.62f, 2.88f
    };

    /** 深雪的冰伤倍率（占攻击力），索引 = 技能等级 - 1。 */
    private static final float[] DEEP_SNOW = {
            2.35f, 2.569f, 2.788f, 3.007f, 3.226f, 3.445f, 3.663f, 3.882f, 4.101f, 4.32f
    };

    /** 深雪在辉映·星超导下的星超导倍率（占攻击力）。 */
    private static final float[] DEEP_SNOW_CONDUCE = {
            2.95f, 3.278f, 3.606f, 3.933f, 4.261f, 4.589f, 4.917f, 5.244f, 5.572f, 5.90f
    };

    /** 飞雪的星超导总倍率（占攻击力）。 */
    private static final float[] FLYING_SNOW = {
            4.32f, 4.80f, 5.28f, 5.76f, 6.24f, 6.72f, 7.20f, 7.68f, 8.16f, 8.64f
    };

    /** 霜月的星超导倍率（占攻击力）。 */
    private static final float[] FROST_MOON = {
            1.22f, 1.358f, 1.496f, 1.633f, 1.771f, 1.909f, 2.047f, 2.184f, 2.322f, 2.46f
    };

    /** 冰伤转星超导时的倍率加成。 */
    public static final float CONDUCE_MULTIPLIER_BONUS = 0.30f;

    /** 星雪状态下冰伤转星超导的额外倍率加成（基础区）。 */
    public static final float SNOW_STATE_BASE_BONUS = 0.30f;

    /** 星雪状态下星超导伤害的额外提升（特殊倍率区）。 */
    public static final float SNOW_STATE_SPECIAL_BONUS = 0.30f;

    /** 覆雪每层的基础加成，每级再 +1%。 */
    public static final float SNOW_COVER_PER_STACK = 0.08f;
    public static final float SNOW_COVER_PER_LEVEL = 0.01f;

    /** 飞雪每消耗一层烈霜的加成：基础 10%，每级 +1.5%。 */
    public static final float RIME_PER_STACK_BASE = 0.10f;
    public static final float RIME_PER_STACK_PER_LEVEL = 0.015f;

    public static float swordQi(int level) {
        return at(SWORD_QI, level);
    }

    public static float deepSnow(int level) {
        return at(DEEP_SNOW, level);
    }

    public static float deepSnowConduce(int level) {
        return at(DEEP_SNOW_CONDUCE, level);
    }

    public static float flyingSnowTotal(int level) {
        return at(FLYING_SNOW, level);
    }

    public static float frostMoon(int level) {
        return at(FROST_MOON, level);
    }

    /** 覆雪每层给星超导伤害的加成。 */
    public static float snowCoverPerStack(int level) {
        return SNOW_COVER_PER_STACK + SNOW_COVER_PER_LEVEL * Math.max(0, level - 1);
    }

    /** 飞雪每消耗一层烈霜给本次伤害的加成。 */
    public static float rimePerStack(int level) {
        return RIME_PER_STACK_BASE + RIME_PER_STACK_PER_LEVEL * Math.max(0, level - 1);
    }

    private static float at(float[] table, int level) {
        int index = Math.max(1, Math.min(table.length, level)) - 1;
        return table[index];
    }

    /** 按星见雅自己的攻击力算这份户口给全队的星超导基础伤害提升。 */
    public float stellarBaseBonusMult(Miyabi miyabi) {
        double atk = miyabi.getData().getAttributeTotalValue(ModAttributes.ATK.value());
        int stages = (int) Math.floor(atk / BONUS_ATK_PER_STAGE);
        return Math.min(BONUS_CAP, stages * BONUS_PER_STAGE);
    }

    /**
     * 星见雅的星超导户口。
     *
     * <p>{@code from} 取冰、{@code to} 取雷：星超导的两段就是雷段与冰段。
     */
    public IStellarHousehold.StellarHousehold stellarHousehold(Miyabi miyabi) {
        return new IStellarHousehold.StellarHousehold(
                StellarGlimmerBranch.CONDUCE,
                ModElements.CYRO.get(),
                ModElements.ELECTRO.get(),
                stellarBaseBonusMult(miyabi));
    }
}
