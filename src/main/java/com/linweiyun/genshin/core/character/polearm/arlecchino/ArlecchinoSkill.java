package com.linweiyun.genshin.core.character.polearm.arlecchino;

import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.polearm.arlecchino.attack.ArlecchinoChargedAttack;
import com.linweiyun.genshin.core.character.polearm.arlecchino.attack.ArlecchinoElementalBurst;
import com.linweiyun.genshin.core.character.polearm.arlecchino.attack.ArlecchinoElementalSkill;
import com.linweiyun.genshin.core.character.polearm.arlecchino.attack.ArlecchinoNormalAttack;
import com.linweiyun.genshin.core.character.polearm.arlecchino.attack.ArlecchinoPlungeAttack;
import com.linweiyun.genshin.core.character.talent.SkillBase;
import net.minecraft.world.entity.player.Player;

/**
 * 阿蕾奇诺的<b>技能调度器</b> —— 继承 {@link SkillBase}，将每一类攻击转发给
 * 对应的独立攻击类。
 *
 * <p>每个攻击类的命名：{@link ArlecchinoNormalAttack}（普攻）/
 * {@link ArlecchinoChargedAttack}（重击）/
 * {@link ArlecchinoElementalSkill}（战技）/
 * {@link ArlecchinoElementalBurst}（爆发）/
 * {@link ArlecchinoPlungeAttack}（下落攻击）。
 *
 * <p>每一类的攻击实施 + 实施后的效果都写在那一个类里，不再分散在多个文件中。
 *
 * <p>她目前没有突破天赋与命座实现，所以 {@link ArlecchinoTalent} /
 * {@link ArlecchinoConstellation} 是空壳。
 */
public class ArlecchinoSkill extends SkillBase {

    // ==================== 参数覆盖 ====================

    @Override
    public int getMaxCombo() { return 5; }

    // ==================== 招式分发 ====================

    @Override
    public void attack(Player player, PGCharacter character, int comboStage) {
        ArlecchinoNormalAttack.execute(player, character, comboStage);
    }

    @Override
    public void chargeAttack(Player player, PGCharacter character) {
        ArlecchinoChargedAttack.execute(player, character);
    }

    @Override
    public void elementalSkill(Player player, PGCharacter character, int skillTime) {
        ArlecchinoElementalSkill.execute(player, character, skillTime);
    }

    @Override
    public void elementalBurst(Player player, PGCharacter character) {
        ArlecchinoElementalBurst.execute(player, character);
    }

    @Override
    public void plungingAttack(Player player, PGCharacter character) {
        ArlecchinoPlungeAttack.execute(player, character);
    }
}