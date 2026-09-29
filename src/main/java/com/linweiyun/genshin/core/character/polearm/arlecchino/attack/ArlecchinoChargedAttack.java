package com.linweiyun.genshin.core.character.polearm.arlecchino.attack;

import com.linweiyun.genshin.core.character.PGCharacter;
import net.minecraft.world.entity.player.Player;

/**
 * 阿蕾奇诺的<b>重击</b>。
 *
 * <p>目前阿蕾奇诺没有自定义重击动作，沿用 {@code SkillBase} / {@code PolearmCharacter}
 * 的默认触发型重击（蓄力到阈值放一招）。如果以后要自定义重击，在这里实现
 * {@code execute(Player, PGCharacter)} 并在 {@code ArlecchinoSkill.chargeAttack()} 里调用。
 */
public final class ArlecchinoChargedAttack {

    private ArlecchinoChargedAttack() {
    }

    /**
     * 执行重击。
     * 默认空实现 —— 阿蕾奇诺沿用基类的触发型重击。
     */
    public static void execute(Player player, PGCharacter character) {
        // 暂不覆盖：沿用基类默认实现
    }
}