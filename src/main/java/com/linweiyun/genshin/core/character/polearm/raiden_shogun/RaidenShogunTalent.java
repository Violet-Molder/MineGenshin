package com.linweiyun.genshin.core.character.polearm.raiden_shogun;

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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * 雷电将军的<b>天赋</b>（突破天赋 / 被动）。
 *
 * <p>技能在 {@link RaidenShogunSkill}。
 * 内容和她现在的实现一致：点按/长按 E 发放的冰凌与队伍增伤。
 *
 * <p>⚠️ 效果的实例 id 字符串是 {@code minegenshin:shenhe_ascend2_tap} /
 * {@code minegenshin:shenhe_ascend2_hold} —— 这是<b>沿用下来的原字符串</b>，
 * 不是笔误。效果实例 id 参与查重与同步，重构不改。
 */
public class RaidenShogunTalent extends TalentBase {

    /** 突破天赋 2 的解锁档：突破 ≥ 4。 */
    private static final int ASCEND2_PHASE = 4;
    /** 两份增伤的数值（点按 / 长按都是 15%）。 */
    private static final float ASCEND2_DAMAGE_BONUS = 0.15f;
    /** 增伤的持续刻数：点按 10 秒 / 长按 15 秒。 */
    private static final int ASCEND2_TAP_TICKS = 200;
    private static final int ASCEND2_HOLD_TICKS = 300;
    /** 增伤效果的实例 id（参与查重与同步，不要改名；两个方向各一份）。 */
    private static final ResourceLocation ASCEND2_TAP_ID = ResourceLocation.parse("minegenshin:shenhe_ascend2_tap");
    private static final ResourceLocation ASCEND2_HOLD_ID = ResourceLocation.parse("minegenshin:shenhe_ascend2_hold");

    /**
     * 突破天赋 1：给队伍 4 人各挂一份「冰凌」（点按 5 根 / 200 刻、长按 7 根 / 300 刻）。
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
     * 突破天赋 2：突破 ≥ 4 时给队伍 4 人各挂一份增伤
     * （点按 → 元素战技/元素爆发 +15%（10 秒）；长按 → 普攻/重击/下落 +15%（15 秒））。
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
