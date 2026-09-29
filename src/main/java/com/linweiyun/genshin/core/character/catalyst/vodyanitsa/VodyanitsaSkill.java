package com.linweiyun.genshin.core.character.catalyst.vodyanitsa;

import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.talent.SkillBase;
import com.linweiyun.genshin.core.character.catalyst.vodyanitsa.attack.VodyanitsaChargedAttack;
import com.linweiyun.genshin.core.character.catalyst.vodyanitsa.attack.VodyanitsaElementalBurst;
import com.linweiyun.genshin.core.character.catalyst.vodyanitsa.attack.VodyanitsaElementalSkill;
import com.linweiyun.genshin.core.character.catalyst.vodyanitsa.attack.VodyanitsaNormalAttack;
import com.linweiyun.genshin.core.character.catalyst.vodyanitsa.attack.VodyanitsaPlungeAttack;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

/**
 * 沃雅妮莎的技能调度器 —— 继承 {@link SkillBase}，将每一类攻击转发给
 * 对应的独立攻击类。
 *
 * <p>每个攻击类的命名：{@link VodyanitsaNormalAttack}（普攻）/
 * {@link VodyanitsaChargedAttack}（重击）/
 * {@link VodyanitsaElementalSkill}（战技）/
 * {@link VodyanitsaElementalBurst}（爆发）/
 * {@link VodyanitsaPlungeAttack}（下落攻击）。
 */
public class VodyanitsaSkill extends SkillBase {
    public static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);

    /** 歌咏系统的索敌/判定半径（格）—— 外部（如 {@link VodyanitsaTalent}）会引用。 */
    public static final int SONG_RANGE = 13;
    /** 歌咏系统持续时长（刻）—— 320 刻 = 16 秒。 */
    public static final int SONG_DURATION_TICKS = 320;
    /** 歌咏攻击间隔（刻）—— 60 刻 = 3 秒。 */
    public static final int SONG_ATTACK_INTERVAL_TICKS = 60;
    /** 歌咏治疗间隔（刻）—— 30 刻 = 1.5 秒。 */
    public static final int SONG_HEAL_INTERVAL_TICKS = 30;
    /** 歌唱抗性削减来源标识。 */
    public static final String SONG_RES_SOURCE = "vodyanitsa_song_res";
    /** 歌唱抗性削减持续时长（刻）—— 120 刻 = 6 秒。 */
    public static final int SONG_RES_DURATION_TICKS = 120;

    @Override
    public int getMaxCombo() { return 3; }

    // ==================== 招式分发 ====================

    @Override
    public void attack(Player player, PGCharacter character, int comboStage) {
        VodyanitsaNormalAttack.execute(player, character, comboStage);
    }

    @Override
    public void chargeAttack(Player player, PGCharacter character) {
        VodyanitsaChargedAttack.execute(player, character);
    }

    @Override
    public void elementalSkill(Player player, PGCharacter character, int skillTime) {
        VodyanitsaElementalSkill.execute(player, character, skillTime);
    }

    @Override
    public void elementalBurst(Player player, PGCharacter character) {
        VodyanitsaElementalBurst.execute(player, character);
    }

    @Override
    public void plungingAttack(Player player, PGCharacter character) {
        VodyanitsaPlungeAttack.execute(player, character);
    }
}