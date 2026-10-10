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
}
