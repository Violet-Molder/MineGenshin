package com.linweiyun.genshin.core.character.polearm.shenhe;

import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.claymore.ClaymoreSkill;
import com.linweiyun.genshin.core.character.polearm.shenhe.attack.ShenheChargedAttack;
import com.linweiyun.genshin.core.character.polearm.shenhe.attack.ShenheElementalBurst;
import com.linweiyun.genshin.core.character.polearm.shenhe.attack.ShenheElementalSkill;
import com.linweiyun.genshin.core.character.polearm.shenhe.attack.ShenheNormalAttack;
import com.linweiyun.genshin.core.character.polearm.shenhe.attack.ShenhePlungeAttack;
import com.linweiyun.genshin.core.system.combat.action.data.ActionStep;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * 申鹤的<b>技能调度器</b> —— 继承 {@link ClaymoreSkill}，将每一类攻击转发给
 * 对应的独立攻击类。
 *
 * <p>每个攻击类的命名：{@link ShenheNormalAttack}（普攻）/
 * {@link ShenheChargedAttack}（重击）/
 * {@link ShenheElementalSkill}（战技）/
 * {@link ShenheElementalBurst}（爆发）/
 * {@link ShenhePlungeAttack}（下落攻击）。
 *
 * <p>每一类的攻击实施 + 实施后的效果都写在那一个类里，不再分散在多个文件中。
 * 天赋触发也由攻击类自己调用（如 {@link ShenheElementalSkill} 调用
 * {@link com.linweiyun.genshin.core.character.polearm.shenhe.ShenheTalent#grantIcyQuills}）。
 */
public class ShenheSkill extends ClaymoreSkill {
    public static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);

    /** 普攻段数 = 3。 */
    @Override
    public int getMaxCombo() { return 3; }

    // ==================== 持续重击 ====================

    /**
     * 持续重击播哪个动画。
     *
     * <p>这一段本来该是<b>木偶（桑多涅）</b>的 —— 木偶角色本体还没做，
     * 所以现在把她的重击动画套在申鹤身上验收：
     * {@link ShenheResources#CHARGED_ATTACK_ANIMATION}（在单独的文件
     * {@code character/shenhe/shenhe_puppet.animation.json} 里，
     * 由 {@link ShenheResources#RENDER_DATA} 挂成额外动画文件）。
     *
     * <p>不覆盖的话，默认会沿用动作表里那段重击借来的动画名
     * （{@code SkillBase} 的兜底重击就是点按战技那一段，名字是 {@code "skill"}），
     * 和她这套「坐飞行坐骑 + 屏幕 + FJO」的表现对不上。
     */
    @Override
    protected String getChargedAttackAnimation(PGCharacter character, @Nullable ActionStep source) {
        return ShenheResources.CHARGED_ATTACK_ANIMATION;
    }

    // ==================== 招式分发 ====================

    @Override
    public void attack(Player player, PGCharacter character, int comboStage) {
        ShenheNormalAttack.execute(player, character, comboStage);
    }

    @Override
    public void chargeAttack(Player player, PGCharacter character) {
        ShenheChargedAttack.execute(player, character);
    }

    @Override
    public void elementalSkill(Player player, PGCharacter character, int skillTime) {
        ShenheElementalSkill.execute(player, character, skillTime);
    }

    @Override
    public void elementalBurst(Player player, PGCharacter character) {
        ShenheElementalBurst.execute(player, character);
    }

    @Override
    public void plungingAttack(Player player, PGCharacter character) {
        ShenhePlungeAttack.execute(player, character);
    }
}