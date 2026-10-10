package com.linweiyun.genshin.core.character.sword.miyabi.attack;

import com.linweiyun.genshin.config.character.ShenheTalentConfig;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.sword.miyabi.MiyabiResources;
import com.linweiyun.genshin.core.character.sword.miyabi.Miyabi;
import com.linweiyun.genshin.core.character.sword.miyabi.MiyabiTalent;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.elementlib.core.system.combat.decay.DecayGroups;
import com.linweiyun.genshin.core.system.reaction.StellarGlimmer;
import com.linweiyun.genshin.core.system.registry.register.ModReactionTypes;
import net.minecraft.world.entity.player.Player;

/**
 * 星见雅的普通攻击：五段近战斩击，每段一下<b>物理</b>伤害。
 *
 * <p>倍率读申鹤 / 薇斯娜那张表（同为五星单手剑）；伤害点、时长、削韧都写在动作表里
 * （见 {@link MiyabiResources}），这里只负责「打到谁、打多少」。
 */
public final class MiyabiNormalAttack {

    /** 攻击距离（格）。 */
    private static final double REACH = 3.0;

    /** 扫描盒左右 / 前后外扩（格）。 */
    private static final float WIDTH = 1.0f;

    /** 扫描盒上下外扩（格）—— 近战够高一级 / 低一级的目标主要靠它。 */
    private static final float HEIGHT = 1.5f;

    private MiyabiNormalAttack() {
    }

    public static void execute(Player player, PGCharacter character, int comboStage) {
        int stage = Math.max(1, Math.min(MiyabiResources.MAX_COMBO, comboStage));
        int level = Math.max(1, character.getData().getNormalAttackLevel());
        float multiplier = (float) ShenheTalentConfig.getNormalAttack(stage, level);

        boolean cryo = stage >= 4;
        Miyabi miyabi = character instanceof Miyabi m ? m : null;
        boolean snow = miyabi != null && miyabi.isSnowState();
        boolean stellar = cryo && (snow || StellarGlimmer.hasConduce(character));
        if (stellar) {
            float converted = multiplier * (1f + MiyabiTalent.CONDUCE_MULTIPLIER_BONUS);
            miyabi.setConvertedHit(true);
            MiyabiDamage.forwardStellar(player, character, ModReactionTypes.STELLAR_CONDUCE_ICE.get(),
                    ModElements.CYRO.get(), DecayGroups.DEFAULT_NORMAL_ATTACK,
                    REACH, WIDTH, HEIGHT, converted);
            return;
        }

        if (snow && !cryo) {
            float converted = multiplier * 1.15f
                    * (1f + MiyabiTalent.CONDUCE_MULTIPLIER_BONUS);
            miyabi.setConvertedHit(true);
            MiyabiDamage.forwardStellar(player, character, ModReactionTypes.STELLAR_CONDUCE_ICE.get(),
                    ModElements.CYRO.get(), DecayGroups.DEFAULT_NORMAL_ATTACK,
                    REACH, WIDTH, HEIGHT, converted);
            return;
        }

        MiyabiDamage.forward(player, character, AttackType.NORMAL_ATTACK,
                cryo ? ModElements.CYRO.get() : ModElements.FYSIKOS.get(),
                DecayGroups.DEFAULT_NORMAL_ATTACK, REACH, WIDTH, HEIGHT, multiplier);
    }
}
