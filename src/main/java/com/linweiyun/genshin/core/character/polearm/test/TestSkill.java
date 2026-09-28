package com.linweiyun.genshin.core.character.polearm.test;

import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.talent.SkillBase;
import net.minecraft.world.entity.player.Player;

/**
 * test 的招式表 —— 只做转发，实现全在 {@link TestSkillLogic}。
 *
 * <p>数值、段数、判定范围全部照抄林薇云（她那份又是照抄申鹤的），
 * 见 {@code LinweiyunSkillLogic} 的类注释。
 */
public class TestSkill extends SkillBase {

    /** 普攻段数：与动作数据的 ComboData 段数一致。 */
    @Override
    public int getMaxCombo() {
        return TestSkillLogic.maxCombo();
    }

    @Override
    public void attack(Player player, PGCharacter character, int comboStage) {
        TestSkillLogic.attack(player, character, comboStage);
    }

    @Override
    public void elementalSkill(Player player, PGCharacter character, int skillType) {
        TestSkillLogic.elementalSkill(player, character, skillType);
    }

    @Override
    public void elementalBurst(Player player, PGCharacter character) {
        TestSkillLogic.elementalBurst(player, character);
    }
}
