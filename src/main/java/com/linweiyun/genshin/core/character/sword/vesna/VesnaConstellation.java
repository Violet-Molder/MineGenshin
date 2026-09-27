package com.linweiyun.genshin.core.character.sword.vesna;

import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.talent.ConstellationBase;
import com.linweiyun.genshin.core.system.reaction.StellarGlimmerBranch;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

/**
 * 薇斯娜的<b>命之座</b>（C1 / C2 / C4 / C6；3 / 5 命未实现）。
 *
 * <p>这些效果原来内联在 {@code Vesna} 与技能里，现在搬到这里、调用点只剩一行：
 *
 * <table border="1">
 *   <caption>命座口径</caption>
 *   <tr><th>命座</th><th>效果</th><th>调用点</th></tr>
 *   <tr><td>1 命</td><td>巡风列装下最高境界翔风剑次数 3 → 4；本次进入模式后第一次免剑气；
 *       模式下星扩散反应伤害 +20%</td>
 *       <td>{@link VesnaSkill#elementalSkill} / {@link VesnaSkill#advanceAfterCast} /
 *       {@code Vesna.canUseElementalSkill} / {@code Vesna.getStellarGlimmerBonus}</td></tr>
 *   <tr><td>2 命</td><td>进入巡风列装直接拿满整肃（要求已解锁突破天赋 1）；满层整肃时攻击力 +40%</td>
 *       <td>{@link #onWindriderEnter} / {@link #tick}</td></tr>
 *   <tr><td>4 命</td><td>「仪典·冬之凯风」强化：突破 4 那两项加成改为三倍</td>
 *       <td>{@link VesnaTalent#tick} → {@link #winterRiteMultiplier}</td></tr>
 *   <tr><td>6 命</td><td>星扩散伤害擢升 20%；三阶翔风剑后开 5 秒「变移」窗口</td>
 *       <td>{@code Vesna.getOwnElevationBonus} / {@link VesnaSkill#elementalSkill} /
 *       {@link VesnaSkill#onCastStart}</td></tr>
 * </table>
 *
 * <p>⚠️ 状态本体留在主类上（{@code bianyiWindowEndTick} 是
 * {@code @DescSynced @Persisted(key = "bianyiWindowEndTick")}，键属于存档格式）：
 * 这里只负责「什么时候写、写成什么」。
 */
public class VesnaConstellation extends ConstellationBase {
    public static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);

    // ==================== 1 命 ====================

    /** 命座 1：巡风列装下最高境界翔风剑的次数上限 3 → 4。 */
    public static final int C1_EXTRA_LV3_USES = 1;
    /** 命座 1：巡风列装模式下造成的星扩散反应伤害 +20%。 */
    public static final float C1_STELLAR_SWIRL_BONUS = 0.20f;

    /** 一次巡风列装最多能放几次最高境界（三阶）翔风剑 —— 0 命 3 次，命座 1 时 4 次。 */
    public int maxLv3UsesInWindrider(Vesna vesna) {
        return Vesna.BASE_LV3_USES_IN_WINDRIDER + (vesna.hasConstellation(1) ? C1_EXTRA_LV3_USES : 0);
    }

    /**
     * 这一次施放的「最高境界翔风剑」是不是<b>免剑气</b>的那一次（命座 1）。
     *
     * <p>判据是「本次进入巡风列装以来还没放过三阶」（{@code lv3UsesInWindrider == 0}）——
     * 不用额外加字段，因为那个计数在 {@code activateWindriderMode()} 里会归零。
     *
     * <p>⚠️ 这里<b>不打日志</b>：{@code Vesna.canUseElementalSkill} 的长按重试会每刻调它，
     * 日志留在技能里的调用点（和重构前一样，每次施放一行）。
     */
    public boolean lv3CastIsFreeNow(Vesna vesna) {
        return vesna.hasConstellation(1)
                && vesna.getXiangfengJianLevel() >= 2
                && vesna.getLv3UsesInWindrider() == 0;
    }

    /** 命座 1：巡风列装模式下的星扩散反应伤害加成（反应加成区里的「星烁加成」）。 */
    public float stellarGlimmerBonus(Vesna vesna, StellarGlimmerBranch branch) {
        if (branch == StellarGlimmerBranch.SWIRL && vesna.isWindriderActive() && vesna.hasConstellation(1)) {
            return C1_STELLAR_SWIRL_BONUS;
        }
        return 0f;
    }

    // ==================== 2 命 ====================

    /** 命座 2：满层整肃时攻击力 +40%。 */
    public static final float C2_ATK_BONUS = 0.40f;
    private static final String C2_ATK_SOURCE = "vesna_c2_atk";

    /** 命座 2：进入巡风列装时直接获得满层整肃（需要已解锁突破天赋 1）。 */
    public boolean c2GrantsMaxDecree(Vesna vesna) {
        return vesna.hasConstellation(2) && springRiteUnlocked(vesna);
    }

    /** 「仪典·春之行列」（突破天赋 1）是否已解锁 —— 由天赋判定，命座只当条件用。 */
    private static boolean springRiteUnlocked(Vesna vesna) {
        return vesna.getTalent() instanceof VesnaTalent talent && talent.hasSpringRiteTalent(vesna);
    }

    /** 进入巡风列装时调用：命座 2 把整肃直接拉满（在天赋的清层之后跑，所以清完就填满）。 */
    public void onWindriderEnter(Vesna vesna) {
        if (c2GrantsMaxDecree(vesna) && vesna.getTalent() instanceof VesnaTalent talent) {
            talent.fillDecreeToMax(vesna);
        }
    }

    /**
     * 命座 2：<b>满层整肃</b>时攻击力 +40%（{@link #c2GrantsMaxDecree} 为前置）。
     *
     * <p>和突破 4 那套一样用临时修饰符，且每刻重算 —— 掉了层（自然掉或清空）
     * 下一次 tick 就会自己摘掉，不需要在清层的地方通知它。
     */
    @Override
    public void tick(Player player, PGCharacter character) {
        if (!(character instanceof Vesna vesna)) return;

        vesna.getData().removeAttributeModifier(ModAttributes.ATK.value(), C2_ATK_SOURCE);
        int stacks = vesna.getTalent() instanceof VesnaTalent talent ? talent.decreeStacks(vesna) : 0;
        if (c2GrantsMaxDecree(vesna) && stacks >= Vesna.MAX_DECREE_STACKS) {
            vesna.getData().addAttributeTempPercentModifier(ModAttributes.ATK.value(), C2_ATK_SOURCE, C2_ATK_BONUS);
        }
    }

    // ==================== 4 命 ====================

    /**
     * 命座 4：「仪典·冬之凯风」强化 —— 突破 4 那两项效果（攻击力 / 元素精通）改为<b>三倍</b>。
     *
     * <p>做成「返回倍率」的小方法：突破 4 的重算里只留一行
     * {@code scale = constellation.winterRiteMultiplier(this)}。
     */
    public static final float C4_WINTER_RITE_MULTIPLIER = 3f;

    public float winterRiteMultiplier(Vesna vesna) {
        return vesna.hasConstellation(4) ? C4_WINTER_RITE_MULTIPLIER : 1f;
    }

    // ==================== 6 命 ====================

    /** 命座 6：薇斯娜造成的星扩散反应伤害<b>擢升</b> 20%（走独立的擢升区，不是反应加成区）。 */
    public static final float C6_STELLAR_SWIRL_ELEVATION = 0.20f;

    /** 命座 6：三阶翔风剑之后的「变移」窗口时长（5 秒）。 */
    public static final int BIANYI_WINDOW_TICKS = 5 * 20;

    /** 命座 6：薇斯娜造成的星扩散反应伤害擢升（走 {@code PGCharacter#getOwnElevationBonus}）。 */
    public float elevationBonus(Vesna vesna, StellarGlimmerBranch branch) {
        if (branch == StellarGlimmerBranch.SWIRL && vesna.hasConstellation(6)) {
            return C6_STELLAR_SWIRL_ELEVATION;
        }
        return 0f;
    }

    /** 命座 6：打开「变移」窗口（放完三阶翔风剑那一下调用）。 */
    public void openBianyiWindow(Player player, Vesna vesna) {
        if (!vesna.hasConstellation(6)) return;
        vesna.setBianyiWindowEndTick(player.level().getGameTime() + BIANYI_WINDOW_TICKS);
        LOGGER.info("[薇斯娜] 满命：变移窗口开启（{} 刻）", BIANYI_WINDOW_TICKS);
        vesna.syncSkillState();
    }

    /** 命座 6：消费掉窗口 —— 放过一次变移之后，普攻/战技恢复正常队列。 */
    public void consumeBianyiWindow(Vesna vesna) {
        if (vesna.getBianyiWindowEndTick() == 0L) return;
        vesna.setBianyiWindowEndTick(0L);
        vesna.syncSkillState();
    }
}
