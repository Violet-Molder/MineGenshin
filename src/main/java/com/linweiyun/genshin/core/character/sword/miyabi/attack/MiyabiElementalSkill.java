package com.linweiyun.genshin.core.character.sword.miyabi.attack;

import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.sword.miyabi.Miyabi;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.genshin.core.system.registry.register.ModReactionTypes;
import com.linweiyun.elementlib.core.system.combat.decay.DecayGroups;
import net.minecraft.world.entity.player.Player;

/**
 * 星见雅的战技（能量满的那一套，两段）。
 *
 * <p>伤害点是素材那一套：8 刻起每 2 刻一下、共 11 下，全部写在动作表的 {@code hits[]} 里
 * （多段伤害就是多写几个 hit），这里只负责「每一下打到谁、打多少」。
 */
public final class MiyabiElementalSkill {

    /** 以身前为中心的范围（格）。 */
    private static final double REACH = 3.0;

    /** 扫描盒左右 / 前后外扩（格）。 */
    private static final float WIDTH = 1.5f;

    /** 扫描盒上下外扩（格）。 */
    private static final float HEIGHT = 1.5f;

    private MiyabiElementalSkill() {
    }

    public static void execute(Player player, PGCharacter character) {
        if (!(character instanceof Miyabi miyabi)) {
            return;
        }
        float multiplier = miyabi.nextSkillHitMultiplier();
        if (multiplier <= 0f) {
            return;
        }
        if (miyabi.isSkillHitStellar()) {
            MiyabiDamage.forwardStellar(player, character, ModReactionTypes.STELLAR_CONDUCE.get(),
                    ModElements.CYRO.get(), DecayGroups.DEFAULT_ELEMENTAL_SKILL,
                    REACH, WIDTH, HEIGHT, multiplier);
            return;
        }
        MiyabiDamage.forward(player, character, AttackType.ELEMENTAL_SKILL, ModElements.CYRO.get(),
                DecayGroups.DEFAULT_ELEMENTAL_SKILL, REACH, WIDTH, HEIGHT, multiplier);
    }
}
