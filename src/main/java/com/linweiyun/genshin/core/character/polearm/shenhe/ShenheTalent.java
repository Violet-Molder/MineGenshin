package com.linweiyun.genshin.core.character.polearm.shenhe;

import com.linweiyun.genshin.content.effect.character.CharacterEffectHelper;
import com.linweiyun.genshin.content.effect.character.CharacterEffectInstance;
import com.linweiyun.genshin.content.effect.character.impl.DamageBonusEffect;
import com.linweiyun.genshin.content.effect.character.shenhe.IcyQuillEffect;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.talent.TalentBase;
import com.linweiyun.genshin.core.system.registry.register.ModCharacterEffects;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * 申鹤的<b>天赋</b>（突破天赋 / 被动）。
 *
 * <p>技能在 {@link ShenheSkill}。
 * 这里只放<b>不由某一招自身打出来</b>的那部分：
 *
 * <h2>突破天赋 1 —— 冰凌</h2>
 * 点按 E 给全队 5 根（10 秒）、长按 7 根（15 秒）。
 * 冰凌的「消耗 + 附加伤害」在 {@code IcyQuillEffect} 里（效果自己管），
 * 这里只负责<b>发放</b>。
 *
 * <h2>突破天赋 2（突破 ≥ 4）—— 点按/长按的队伍增伤</h2>
 * 点按：队伍元素战技 / 元素爆发伤害 +15%，10 秒；
 * 长按：普攻 / 重击 / 下落攻击伤害 +15%，15 秒。
 *
 * <p>两个方法的调用点都在 {@link ShenheSkill#elementalSkill} 里（各一行）——
 * 这样「突破天赋给什么」只在这个文件里看，技能里看到的只是「什么时候发」。
 */
public class ShenheTalent extends TalentBase {

    /** 突破天赋 2 的解锁档：突破 ≥ 4。 */
    private static final int ASCEND2_PHASE = 4;
    /** 两份增伤的数值（点按 / 长按都是 15%）。 */
    private static final float ASCEND2_DAMAGE_BONUS = 0.15f;
    /** 增伤的持续刻数：点按 10 秒 / 长按 15 秒。 */
    private static final int ASCEND2_TAP_TICKS = 200;
    private static final int ASCEND2_HOLD_TICKS = 300;
    /** 增伤效果的实例 id（参与查重与同步，不要改名；两个方向各一份）。 */
    private static final Identifier ASCEND2_TAP_ID = Identifier.parse("minegenshin:shenhe_ascend2_tap");
    private static final Identifier ASCEND2_HOLD_ID = Identifier.parse("minegenshin:shenhe_ascend2_hold");

    /**
     * 突破天赋 1：给队伍 4 人各挂一份「冰凌」。
     *
     * <p>数值由调用方给（点按 5 根 / 200 刻、长按 7 根 / 300 刻）——
     * 字面量与效果定义保持一致。
     *
     * @param count         冰凌根数
     * @param durationTicks 持续刻数
     */
    public void grantIcyQuills(Player player, PGCharacter character, int count, int durationTicks) {
        PlayerCharactersAttachment attachment =
                player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);

        for (int i = 0; i < 4; i++) {
            PGCharacter partyChar = attachment.getPartyCharacter(i);
            if (partyChar != null) {
                CharacterEffectInstance effect = new CharacterEffectInstance(
                        ModCharacterEffects.ICY_QUILL_EFFECT.get(), durationTicks, 1);
                effect.setIntData(IcyQuillEffect.ICY_QUILL_COUNT_KEY, count);
                CharacterEffectHelper.addEffect(player, partyChar, effect);
            }
        }
    }

    /**
     * 突破天赋 2：突破 ≥ 4 时，给队伍 4 人各挂一份增伤。
     *
     * <p>点按（{@code hold = false}）给元素战技 / 元素爆发 +15%（10 秒）；
     * 长按（{@code hold = true}）给普攻 / 重击 / 下落攻击 +15%（15 秒）。
     * 不足突破 4 直接不发（{@code getAscensionPhase() >= 4}）。
     */
    public void grantAscend2DamageBonus(Player player, PGCharacter character, boolean hold) {
        if (character.getData().getAscensionPhase() < ASCEND2_PHASE) return;

        PlayerCharactersAttachment attachment =
                player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);

        for (int i = 0; i < 4; i++) {
            PGCharacter partyChar = attachment.getPartyCharacter(i);
            if (partyChar != null) {
                DamageBonusEffect buff = hold
                        ? new DamageBonusEffect(ASCEND2_DAMAGE_BONUS, AttackType.NORMAL_ATTACK,
                                AttackType.CHARGED_ATTACK, AttackType.PLUNGING_ATTACK)
                        : new DamageBonusEffect(ASCEND2_DAMAGE_BONUS, AttackType.ELEMENTAL_SKILL,
                                AttackType.ELEMENTAL_BURST);
                CharacterEffectInstance instance = new CharacterEffectInstance(
                        hold ? ASCEND2_HOLD_ID : ASCEND2_TAP_ID, buff,
                        hold ? ASCEND2_HOLD_TICKS : ASCEND2_TAP_TICKS, 0);
                CharacterEffectHelper.addEffect(player, partyChar, instance);
            }
        }
    }
}
