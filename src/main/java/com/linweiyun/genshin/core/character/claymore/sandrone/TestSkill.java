package com.linweiyun.genshin.core.character.claymore.sandrone;

import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.talent.SkillBase;
import net.minecraft.world.entity.player.Player;

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
