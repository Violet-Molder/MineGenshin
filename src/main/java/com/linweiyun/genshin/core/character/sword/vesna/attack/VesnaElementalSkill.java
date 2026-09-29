package com.linweiyun.genshin.core.character.sword.vesna.attack;

import com.linweiyun.genshin.core.character.PGCharacter;
import net.minecraft.world.entity.player.Player;

/**
 * 薇斯娜的<b>元素战技</b>。
 *
 * <p>战技逻辑较复杂（巡风列装 / 翔风剑 / 变移），留在 {@code VesnaSkill} 里，
 * 这里只做空壳保编译。
 */
public final class VesnaElementalSkill {

    private VesnaElementalSkill() {
    }

    public static void execute(Player player, PGCharacter character, int skillTime) {
        // 暂不实现：战技全流程在 VesnaSkill.elementalSkill() 里
    }
}