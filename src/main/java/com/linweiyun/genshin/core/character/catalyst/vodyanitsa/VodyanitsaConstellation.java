package com.linweiyun.genshin.core.character.catalyst.vodyanitsa;

import com.linweiyun.genshin.content.effect.character.CharacterEffectHelper;
import com.linweiyun.genshin.content.effect.character.CharacterEffectInstance;
import com.linweiyun.genshin.content.effect.character.vodyanitsa.VodyanitsaBuffs;
import com.linweiyun.genshin.content.effect.character.vodyanitsa.VodyanitsaSongEffects;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.talent.ConstellationBase;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.core.system.registry.register.ModCharacterEffects;
import net.minecraft.world.entity.player.Player;

/**
 * 沃雅妮莎的<b>命之座</b>（C1 / C2 / C4 / C6；3 / 5 命暂无实现）。
 *
 * <p>这些效果集中在这里，由 {@link VodyanitsaSkill}
 * 与 {@link Vodyanitsa} 各留一行调用。四个命座各管一件事：
 *
 * <table border="1">
 *   <caption>命座口径</caption>
 *   <tr><th>命座</th><th>效果</th><th>调用点</th></tr>
 *   <tr><td>1 命「聚光灯下的水华」</td><td>队伍 4 人各挂一份 Spotlight（5 秒）</td>
 *       <td>{@link VodyanitsaSkill#songHeal}</td></tr>
 *   <tr><td>2 命「穿彻风雪的余响」</td><td>遥久之歌 +9 秒；「黑与白的双音」</td>
 *       <td>{@link VodyanitsaSkill#startSong} / {@link VodyanitsaSkill#songAttack}</td></tr>
 *   <tr><td>4 命「柔波摇漾的低诉」</td><td>治疗分档 + 生命值上限 +20%（最多 3 层、每层独立计时）</td>
 *       <td>{@link VodyanitsaSkill#songHeal} / {@link #tick}</td></tr>
 *   <tr><td>6 命</td><td>双音改为全队；Glimmer（擢升 30% + 水/冰伤害 +60%）</td>
 *       <td>{@link VodyanitsaSkill#elementalSkill}</td></tr>
 * </table>
 *
 * <p>⚠️ 4 命的层数即使在遥久之歌结束后也要继续独立倒计时 —— 所以它走 {@link #tick}
 * （由 {@code Vodyanitsa.tick} 在歌的判定之前无条件转发），不能挂在歌的持续逻辑里。
 */
public class VodyanitsaConstellation extends ConstellationBase {

    // ==================== 1 命：聚光灯下的水华 ====================

    /** 1 命「聚光灯下的水华」（5 秒）：给队伍 4 人各挂一份。 */
    public void grantSpotlight(Player player, Vodyanitsa vodyanitsa) {
        if (!vodyanitsa.hasConstellation(1)) return;

        var spotlight = ModCharacterEffects.VODYANITSA_SPOTLIGHT_EFFECT.get();
        if (spotlight == null) return;

        var attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        for (int i = 0; i < 4; i++) {
            PGCharacter member = attachment.getPartyCharacter(i);
            if (member == null) continue;
            CharacterEffectHelper.addEffect(
                    player, member,
                    new CharacterEffectInstance(
                            spotlight, VodyanitsaBuffs.DURATION_TICKS, 0, false));
        }
    }

    // ==================== 2 命：穿彻风雪的余响 ====================

    /** 2 命额外延长 9 秒（遥久之歌时长由技能在 startSong 里加上）。 */
    public static final int SONG_C2_EXTRA_TICKS = 9 * 20;

    /** 2 命：遥久之歌持续时间 +9 秒（没到 2 命就是 0）。 */
    public int songDurationBonusTicks(Vodyanitsa vodyanitsa) {
        return vodyanitsa.hasConstellation(2) ? SONG_C2_EXTRA_TICKS : 0;
    }

    /**
     * 2 命「穿彻风雪的余响」：<b>唤春角笛命中敌人</b>时，给队伍中当前场上角色挂「黑与白的双音」（5 秒）。
     *
     * <ul>
     *   <li>平时 → 水/冰伤害暴击伤害 +50%（{@code DuetElement}）；</li>
     *   <li>场上有流荡风旋、或处于引爆后 5 秒内 → 改为星扩散反应伤害暴击伤害 +60%（{@code DuetStellar}）。</li>
     * </ul>
     *
     * <p>6 命时改为对队伍附近所有角色生效（2 命只给当前场上角色）。
     * 施放战技那一刻（唤春角笛那一下）就会调用一次，遥久之歌每次跳动也会再调，所以触发时机和文案一致。
     */
    public void applyDuetBuff(Player player, Vodyanitsa vodyanitsa) {
        if (!vodyanitsa.hasConstellation(2)) return;

        var duet = (VodyanitsaSongEffects.flowingSwirlActive(player)
                ? ModCharacterEffects.VODYANITSA_DUET_STELLAR_EFFECT
                : ModCharacterEffects.VODYANITSA_DUET_ELEMENT_EFFECT).get();
        var attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        if (duet == null || attachment == null) return;

        PGCharacter onField = attachment.getCurrentCharacter();

        // 2 命：只给当前场上角色；6 命：改为对队伍附近所有角色生效
        for (int i = 0; i < 4; i++) {
            PGCharacter member = attachment.getPartyCharacter(i);
            if (member == null) continue;
            if (!vodyanitsa.hasConstellation(6) && member != onField) continue;
            CharacterEffectHelper.addEffect(
                    player, member,
                    new CharacterEffectInstance(duet, VodyanitsaBuffs.DURATION_TICKS, 0, false));
        }
    }

    // ==================== 4 命：柔波摇漾的低诉 ====================

    public static final int C4_HP_STACK_TICKS = 6 * 20;
    private static final String C4_HP_SOURCE = "vodyanitsa_c4_hp";
    /** 每层自己的剩余刻数（0 = 这层不存在）——和整肃 decreeTicks 同形态。 */
    private final int[] c4HpTicks = new int[3];

    /**
     * 4 命：受治疗者生命值 <b>&lt;40%</b> → 本次治疗 ×1.5；
     * <b>≥40%</b> → 给沃雅妮莎叠一层生命值上限（+20%），本次治疗不额外乘。
     *
     * <p>做成「返回倍率」的小方法，是为了让回血循环里只留一行
     * {@code amount *= constellation.healMultiplier(...)} —— 分档与叠层都收在这里。
     *
     * @return 本次治疗的倍率（1.0 或 1.5）
     */
    public double healMultiplier(Vodyanitsa vodyanitsa, PGCharacter beneficiary, double healMax) {
        if (!vodyanitsa.hasConstellation(4)) return 1.0;

        double ratio = healMax <= 0 ? 1.0 : (beneficiary.getData().getCurrentHP() / healMax);
        if (ratio < 0.40) {
            return 1.5;
        }
        addC4HpStack(vodyanitsa);
        return 1.0;
    }

    /** 加一层：有空位用空位，满了挤掉最早那层；然后重算属性。 */
    public void addC4HpStack(Vodyanitsa vodyanitsa) {
        int slot = 0;
        int lowest = Integer.MAX_VALUE;
        for (int i = 0; i < c4HpTicks.length; i++) {
            if (c4HpTicks[i] <= 0) {
                slot = i;
                break;
            }
            if (c4HpTicks[i] < lowest) {
                lowest = c4HpTicks[i];
                slot = i;
            }
        }
        c4HpTicks[slot] = C4_HP_STACK_TICKS;
        refreshC4HpBonus(vodyanitsa);
    }

    /** 每刻跑一次：每层独立倒计时；有层掉了就重算（4 命的层数在歌结束后也继续走）。 */
    @Override
    public void tick(Player player, PGCharacter character) {
        if (!(character instanceof Vodyanitsa vodyanitsa)) return;

        boolean changed = false;
        for (int i = 0; i < c4HpTicks.length; i++) {
            if (c4HpTicks[i] > 0 && --c4HpTicks[i] == 0) {
                changed = true;
            }
        }
        if (changed) {
            refreshC4HpBonus(vodyanitsa);
        }
    }

    private void refreshC4HpBonus(Vodyanitsa vodyanitsa) {
        int stacks = 0;
        for (int ticks : c4HpTicks) {
            if (ticks > 0) stacks++;
        }
        if (stacks > 0) {
            vodyanitsa.getData().setAttributeTempPercentModifier(
                    ModAttributes.MAX_HP.value(),
                    C4_HP_SOURCE, 0.20f * stacks);
        } else {
            vodyanitsa.getData().removeAttributeModifier(
                    ModAttributes.MAX_HP.value(), C4_HP_SOURCE);
        }
    }

    // ==================== 6 命 ====================

    /**
     * 6 命：遥久之歌持续期间，队伍附近角色的星扩散反应伤害擢升 30%、水/冰伤害 +60%
     * （buff 时长给成遥久之歌的时长，效果到期自动摘）。
     */
    public void applyGlimmer(Player player, Vodyanitsa vodyanitsa) {
        if (!vodyanitsa.hasConstellation(6)) return;

        var glimmer = ModCharacterEffects.VODYANITSA_GLIMMER_EFFECT.get();
        var attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        if (glimmer != null && attachment != null) {
            for (int i = 0; i < 4; i++) {
                PGCharacter member = attachment.getPartyCharacter(i);
                if (member == null) continue;
                CharacterEffectHelper.addEffect(
                        player, member,
                        new CharacterEffectInstance(
                                glimmer, vodyanitsa.songTicksRemaining(), 0, false));
            }
        }
    }
}
