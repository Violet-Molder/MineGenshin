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
        float[] row = NA_TABLE[stage - 1];
        return row[Math.max(1, Math.min(row.length, level)) - 1];
    }

}
