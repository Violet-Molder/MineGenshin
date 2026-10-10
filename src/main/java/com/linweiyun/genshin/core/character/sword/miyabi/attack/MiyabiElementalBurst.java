package com.linweiyun.genshin.core.character.sword.miyabi.attack;

import com.linweiyun.genshin.config.character.ShenheTalentConfig;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.elementlib.core.system.combat.decay.DecayGroups;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 星见雅的大招（{@code final}）：以自身为中心的一圈风伤。
 *
 * <p>伤害点由动作表定（那一段的第 40 刻，素材里 5 秒动画的中后段）。
 */
public final class MiyabiElementalBurst {

    /** 生效半径（格）。 */
    private static final double RADIUS = 5.0;

    private MiyabiElementalBurst() {
    }

    public static void execute(Player player, PGCharacter character) {
        int level = Math.max(1, character.getData().getElementalBurstLevel());
        MiyabiDamage.around(player, character, AttackType.ELEMENTAL_BURST, ModElements.CYRO.get(),
                DecayGroups.DEFAULT_ELEMENTAL_BURST, player.position(), RADIUS,
                ShenheTalentConfig.getBurstCastDamage(level));
    }
}
