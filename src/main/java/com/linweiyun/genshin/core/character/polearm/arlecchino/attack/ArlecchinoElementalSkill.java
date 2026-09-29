package com.linweiyun.genshin.core.character.polearm.arlecchino.attack;

import com.linweiyun.genshin.core.character.PGCharacter;
import net.minecraft.world.entity.player.Player;

/**
 * 阿蕾奇诺的<b>元素战技</b>。
 *
 * <p>目前阿蕾奇诺没有自定义战技，沿用基类默认空实现。
 * 如果以后要实现，在这里写战技逻辑（含伤害结算 + 后续效果）。
 */
public final class ArlecchinoElementalSkill {

    private ArlecchinoElementalSkill() {
    }

    /**
     * 执行元素战技。
     *
     * @param player    攻击者
     * @param character 攻击者角色
     * @param skillTime 按键时长（{@code < 1000} = 点按，{@code >= 1000} = 长按）
     */
    public static void execute(Player player, PGCharacter character, int skillTime) {
        // 暂不实现
    }
}