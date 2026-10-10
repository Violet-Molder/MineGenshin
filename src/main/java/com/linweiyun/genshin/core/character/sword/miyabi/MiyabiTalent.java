package com.linweiyun.genshin.core.character.sword.miyabi;

import com.linweiyun.genshin.config.character.MiyabiTalentConfig;

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

    /** 覆雪每层的基础加成，每级再 +1%。 */
    public static final float SNOW_COVER_PER_STACK = 0.08f;
    public static final float SNOW_COVER_PER_LEVEL = 0.01f;

    /** 飞雪每消耗一层烈霜的加成：基础 10%，每级 +1.5%。 */
    public static final float RIME_PER_STACK_BASE = 0.10f;
    public static final float RIME_PER_STACK_PER_LEVEL = 0.015f;

    public static float swordQi(int level) {
        int lv = Math.max(1, Math.min(SWORD_QI.length, level));
        return (float) MiyabiTalentConfig.value("myb-qi-" + lv, SWORD_QI[lv - 1]);
    }

    public static float deepSnow(int level) {
        int lv = Math.max(1, Math.min(DEEP_SNOW.length, level));
        return (float) MiyabiTalentConfig.value("myb-deep_snow-" + lv, DEEP_SNOW[lv - 1]);
    }

    public static float deepSnowConduce(int level) {
        int lv = Math.max(1, Math.min(DEEP_SNOW_CONDUCE.length, level));
        return (float) MiyabiTalentConfig.value("myb-deep_snow_conduce-" + lv, DEEP_SNOW_CONDUCE[lv - 1]);
    }

    public static float flyingSnowTotal(int level) {
        int lv = Math.max(1, Math.min(FLYING_SNOW.length, level));
        return (float) MiyabiTalentConfig.value("myb-flying_snow-" + lv, FLYING_SNOW[lv - 1]);
    }

    public static float frostMoon(int level) {
        int lv = Math.max(1, Math.min(FROST_MOON.length, level));
        return (float) MiyabiTalentConfig.value("myb-frost_moon-" + lv, FROST_MOON[lv - 1]);
    }

    /** 覆雪每层给星超导伤害的加成。 */
    public static float snowCoverPerStack(int level) {
        int lv = Math.max(1, Math.min(10, level));
        return (float) MiyabiTalentConfig.value("myb-snow_cover-" + lv,
                SNOW_COVER_PER_STACK + SNOW_COVER_PER_LEVEL * (lv - 1));
    }

    /** 飞雪每消耗一层烈霜给本次伤害的加成。 */
    public static float rimePerStack(int level) {
        int lv = Math.max(1, Math.min(10, level));
        return (float) MiyabiTalentConfig.value("myb-rime-" + lv,
                RIME_PER_STACK_BASE + RIME_PER_STACK_PER_LEVEL * lv);
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
    private static final float[][] NA_TABLE = {
            {0.4042f, 0.4371f, 0.4700f, 0.5170f, 0.5499f, 0.5875f, 0.6392f, 0.6909f, 0.7426f, 0.7990f, 0.8554f, 0.9118f, 0.9682f, 1.0246f, 1.0810f},
            {0.4868f, 0.5264f, 0.5660f, 0.6226f, 0.6622f, 0.7075f, 0.7698f, 0.8320f, 0.8943f, 0.9622f, 1.0301f, 1.0980f, 1.1660f, 1.2339f, 1.3018f},
            {0.2808f, 0.3036f, 0.3265f, 0.3592f, 0.3820f, 0.4081f, 0.4440f, 0.4800f, 0.5159f, 0.5551f, 0.5942f, 0.6334f, 0.6726f, 0.7118f, 0.7510f},
            {0.5917f, 0.6398f, 0.6880f, 0.7568f, 0.8050f, 0.8600f, 0.9357f, 1.0114f, 1.0870f, 1.1696f, 1.2522f, 1.3347f, 1.4173f, 1.4998f, 1.5824f},
            {0.6244f, 0.6752f, 0.7260f, 0.7986f, 0.8494f, 0.9075f, 0.9874f, 1.0672f, 1.1471f, 1.2342f, 1.3213f, 1.4084f, 1.4956f, 1.5827f, 1.6698f},
            {0.7224f, 0.7812f, 0.8400f, 0.9240f, 0.9828f, 1.0500f, 1.1424f, 1.2348f, 1.3272f, 1.4280f, 1.5288f, 1.6296f, 1.7304f, 1.8312f, 1.9320f},
    };

    public static float normalAttackMultiplier(int stage, int level) {
        if (stage < 1 || stage > NA_TABLE.length) {
            return 0f;
        }
        int lv = Math.max(1, Math.min(NA_TABLE[stage - 1].length, level));
        return (float) MiyabiTalentConfig.value("myb-na" + stage + "-" + lv,
                NA_TABLE[stage - 1][lv - 1]);
    }

    private static final float[] CHARGED_TABLE = {
            1.3304f, 1.4387f, 1.5470f, 1.7017f, 1.8100f, 1.9338f, 2.1039f, 2.2741f, 2.4443f, 2.6299f,
            2.8155f, 3.0012f, 3.1868f, 3.3725f, 3.5581f
    };

    public static float chargedAttackMultiplier(int level) {
        int lv = Math.max(1, Math.min(CHARGED_TABLE.length, level));
        return (float) MiyabiTalentConfig.value("myb-charged-" + lv, CHARGED_TABLE[lv - 1]);
    }

    public static float defaultNormalAttack(int stage, int level) {
        return at(row(NA_TABLE, stage), level);
    }

    public static float defaultCharged(int level) {
        return at(CHARGED_TABLE, level);
    }

    public static float defaultSwordQi(int level) {
        return at(SWORD_QI, level);
    }

    public static float defaultFrostMoon(int level) {
        return at(FROST_MOON, level);
    }

    public static float defaultDeepSnow(int level) {
        return at(DEEP_SNOW, level);
    }

    public static float defaultDeepSnowConduce(int level) {
        return at(DEEP_SNOW_CONDUCE, level);
    }

    public static float defaultFlyingSnow(int level) {
        return at(FLYING_SNOW, level);
    }

    private static float[] row(float[][] table, int stage) {
        return table[Math.max(1, Math.min(table.length, stage)) - 1];
    }
}
