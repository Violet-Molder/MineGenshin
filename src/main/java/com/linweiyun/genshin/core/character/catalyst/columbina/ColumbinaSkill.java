package com.linweiyun.genshin.core.character.catalyst.columbina;

import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.talent.SkillBase;
import com.linweiyun.genshin.core.character.catalyst.columbina.attack.ColumbinaChargedAttack;
import com.linweiyun.genshin.core.character.catalyst.columbina.attack.ColumbinaElementalBurst;
import com.linweiyun.genshin.core.character.catalyst.columbina.attack.ColumbinaElementalSkill;
import com.linweiyun.genshin.core.character.catalyst.columbina.attack.ColumbinaNormalAttack;
import com.linweiyun.genshin.core.character.catalyst.columbina.attack.ColumbinaPlungeAttack;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

/**
 * 哥伦比娅的<b>技能调度器</b> —— 继承 {@link SkillBase}，将每一类攻击转发给
 * 对应的独立攻击类。
 *
 * <p>每个攻击类的命名：{@link ColumbinaNormalAttack}（普攻）/
 * {@link ColumbinaChargedAttack}（重击）/
 * {@link ColumbinaElementalSkill}（战技）/
 * {@link ColumbinaElementalBurst}（爆发）/
 * {@link ColumbinaPlungeAttack}（下落攻击）。
 */
public class ColumbinaSkill extends SkillBase {
    public static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);

    @Override
    public int getMaxCombo() { return 3; }

    // ==================== 招式分发 ====================

    @Override
    public void attack(Player player, PGCharacter character, int comboStage) {
        ColumbinaNormalAttack.execute(player, character, comboStage);
    }

    @Override
    public void chargeAttack(Player player, PGCharacter character) {
        ColumbinaChargedAttack.execute(player, character);
    }

    @Override
    public void elementalSkill(Player player, PGCharacter character, int skillTime) {
        ColumbinaElementalSkill.execute(player, character, skillTime);
    }

    @Override
    public void elementalBurst(Player player, PGCharacter character) {
        ColumbinaElementalBurst.execute(player, character);
    }

    @Override
    public void plungingAttack(Player player, PGCharacter character) {
        ColumbinaPlungeAttack.execute(player, character);
    }
}