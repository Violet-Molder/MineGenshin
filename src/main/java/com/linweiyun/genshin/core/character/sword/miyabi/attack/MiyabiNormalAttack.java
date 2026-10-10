package com.linweiyun.genshin.core.character.sword.miyabi.attack;

import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.sword.miyabi.MiyabiResources;
import com.linweiyun.genshin.core.character.sword.miyabi.Miyabi;
import com.linweiyun.genshin.core.character.sword.miyabi.MiyabiTalent;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.elementlib.core.system.combat.decay.DecayGroups;
import com.linweiyun.genshin.core.system.registry.register.ModReactionTypes;
import net.minecraft.world.entity.player.Player;

/**
 * 星见雅的普通攻击：五段近战斩击，一至三段<b>物理</b>、四至五段<b>冰</b>。
 *
 * <p>处于辉映·星超导状态下时，四、五段转为<b>星超导</b>伤害（倍率上浮
 * {@link MiyabiTalent#CONDUCE_MULTIPLIER_BONUS}），其余各段改为<b>冰</b>伤害
 * —— 这一层冰附魔不可被其他转化覆盖。
 *
 * <p>倍率读她自己的表；伤害点、时长、削韧都写在动作表里
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
        float multiplier = MiyabiTalent.normalAttackMultiplier(stage, level);

        boolean conduce = character instanceof Miyabi miyabi && miyabi.isConduce();
        if (stage >= 4 && conduce) {
            float converted = multiplier * (1f + MiyabiTalent.CONDUCE_MULTIPLIER_BONUS);
            MiyabiDamage.forwardStellar(player, character, ModReactionTypes.STELLAR_CONDUCE.get(),
                    ModElements.CYRO.get(), DecayGroups.DEFAULT_NORMAL_ATTACK,
                    REACH, WIDTH, HEIGHT, converted);
            return;
        }

        MiyabiDamage.forward(player, character, AttackType.NORMAL_ATTACK,
                stage >= 4 || conduce ? ModElements.CYRO.get() : ModElements.FYSIKOS.get(),
                DecayGroups.DEFAULT_NORMAL_ATTACK, REACH, WIDTH, HEIGHT, multiplier);
    }
}
