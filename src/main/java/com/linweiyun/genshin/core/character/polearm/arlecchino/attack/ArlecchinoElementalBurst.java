package com.linweiyun.genshin.core.character.polearm.arlecchino.attack;

import com.linweiyun.genshin.core.character.PGCharacter;
import net.minecraft.world.entity.player.Player;

/**
 * 阿蕾奇诺的<b>元素爆发</b>。
 *
 * <p>目前阿蕾奇诺没有自定义大招，沿用基类默认空实现。
 * 如果以后要实现，在这里写大招逻辑（含伤害结算 + 后续效果）。
 */
public final class ArlecchinoElementalBurst {

    private ArlecchinoElementalBurst() {
    }

    /**
     * 执行元素爆发。
     *
     * @param player    攻击者
     * @param character 攻击者角色
     */
    public static void execute(Player player, PGCharacter character) {
        // 暂不实现
    }
}