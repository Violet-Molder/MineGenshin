package com.linweiyun.genshin.core.character.polearm.raiden_shogun;

import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.polearm.raiden_shogun.attack.RaidenShogunChargedAttack;
import com.linweiyun.genshin.core.character.polearm.raiden_shogun.attack.RaidenShogunElementalBurst;
import com.linweiyun.genshin.core.character.polearm.raiden_shogun.attack.RaidenShogunElementalSkill;
import com.linweiyun.genshin.core.character.polearm.raiden_shogun.attack.RaidenShogunNormalAttack;
import com.linweiyun.genshin.core.character.polearm.raiden_shogun.attack.RaidenShogunPlungeAttack;
import com.linweiyun.genshin.core.character.talent.SkillBase;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

/**
 * 雷电将军的<b>技能调度器</b> —— 继承 {@link SkillBase}，将每一类攻击转发给
 * 对应的独立攻击类。
 *
 * <p>每个攻击类的命名：{@link RaidenShogunNormalAttack}（普攻）/
 * {@link RaidenShogunChargedAttack}（重击）/
 * {@link RaidenShogunElementalSkill}（战技）/
 * {@link RaidenShogunElementalBurst}（爆发）/
 * {@link RaidenShogunPlungeAttack}（下落攻击）。
 *
 * <p>每一类的攻击实施 + 实施后的效果都写在那一个类里。
 */
public class RaidenShogunSkill extends SkillBase {
    public static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);

    @Override
    public int getMaxCombo() { return 5; }

    // ==================== 招式分发 ====================

    @Override
    public void attack(Player player, PGCharacter character, int comboStage) {
        RaidenShogunNormalAttack.execute(player, character, comboStage);
    }

    @Override
    public void chargeAttack(Player player, PGCharacter character) {
        RaidenShogunChargedAttack.execute(player, character);
    }

    @Override
    public void elementalSkill(Player player, PGCharacter character, int skillTime) {
        RaidenShogunElementalSkill.execute(player, character, skillTime);
    }

    @Override
    public void elementalBurst(Player player, PGCharacter character) {
        RaidenShogunElementalBurst.execute(player, character);
    }

    @Override
    public void plungingAttack(Player player, PGCharacter character) {
        RaidenShogunPlungeAttack.execute(player, character);
    }
}