package com.linweiyun.genshin.core.character.sword.vesna;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.talent.TalentBase;
import com.linweiyun.genshin.core.character.util.capability.IStellarHousehold;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.reaction.StellarGlimmer;
import com.linweiyun.genshin.core.system.reaction.StellarGlimmerBranch;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

/**
 * 薇斯娜的<b>天赋</b>（突破天赋 / 被动）。
 */
public class VesnaTalent extends TalentBase {
    public static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);

    // ==================== 整肃（突破天赋 1） ====================

    /** 「仪典·春之行列」= 突破天赋 1（给「大权」加成的那个）。命座 2 的前置。 */
    public static final int SPRING_RITE_ASCENSION = 1;
    /** 每层持续 20 秒，<b>每层独立计时</b>。 */
    public static final int DECREE_DURATION_TICKS = 20 * 20;
    /** 每层给大权区的加成（+10%）。 */
    public static final float DECREE_BONUS_PER_STACK = 0.10f;

    /** 「仪典·春之行列」（突破天赋 1）是否已解锁。 */
    public boolean hasSpringRiteTalent(Vesna vesna) {
        return vesna.getData().getAscensionPhase() >= SPRING_RITE_ASCENSION;
    }

    /** 当前有几层整肃。 */
    public int decreeStacks(Vesna vesna) {
        int stacks = 0;
        for (int ticks : vesna.decreeTicks) {
            if (ticks > 0) {
                stacks++;
            }
        }
        return stacks;
    }

    /**
     * 叠一层整肃。
     *
     * <p>6 层是一个<b>队列</b>：满 6 层时第 7 次会<b>挤掉最早的那一层</b>
     * （最早 = 剩余刻数最少的那格），而不是叠不上去。
     */
    public void grantDecree(Vesna vesna) {
        int slot = 0;
        int lowest = Integer.MAX_VALUE;
        for (int i = 0; i < vesna.decreeTicks.length; i++) {
            if (vesna.decreeTicks[i] <= 0) {
                slot = i;                       // 有空位就用空位
                lowest = 0;
                break;
            }
            if (vesna.decreeTicks[i] < lowest) {
                lowest = vesna.decreeTicks[i];
                slot = i;                       // 满了就挤掉最早的那层
            }
        }
        vesna.decreeTicks[slot] = DECREE_DURATION_TICKS;
        LOGGER.info("[整肃] 叠 1 层 → 当前 {} 层（大权 +{}%）",
                decreeStacks(vesna), (int) (sovereigntyBonus(vesna) * 100));
        vesna.syncSkillState();
    }

    /** 清空所有整肃层数（进巡风列装 / 退场时调用；翔风剑不清）。 */
    public void clearDecree(Vesna vesna) {
        int before = decreeStacks(vesna);
        if (before <= 0) {
            return;
        }
        java.util.Arrays.fill(vesna.decreeTicks, 0);
        LOGGER.info("[整肃] 清空（原 {} 层）", before);
        vesna.syncSkillState();
    }

    /** 每刻递减（服务端）。 */
    private void tickDecree(Vesna vesna) {
        boolean changed = false;
        for (int i = 0; i < vesna.decreeTicks.length; i++) {
            if (vesna.decreeTicks[i] > 0 && --vesna.decreeTicks[i] == 0) {
                changed = true;
            }
        }
        if (changed) {
            vesna.syncSkillState();
        }
    }

    /** 把整肃直接拉满（命座 2 用）。 */
    public void fillDecreeToMax(Vesna vesna) {
        java.util.Arrays.fill(vesna.decreeTicks, DECREE_DURATION_TICKS);
        LOGGER.info("[整肃] 命座 2：进入巡风列装 → 直接拉满 {} 层（大权 +{}%）",
                Vesna.MAX_DECREE_STACKS, (int) (sovereigntyBonus(vesna) * 100));
        vesna.syncSkillState();
    }

    /**
     * 大权区加成 = 整肃层数 × 10%。
     *
     * <p>只作用在「灵剑」那几段（翔风剑二阶第二段 / 三阶两段 / 大招）——
     * 具体哪几段由技能造伤害时决定，不是全局生效。
     */
    public float sovereigntyBonus(Vesna vesna) {
        return decreeStacks(vesna) * DECREE_BONUS_PER_STACK;
    }

    // ==================== 星扩散：户口与基础伤害提升 ====================

    /** 每满 100 点攻击力提升一档（<b>不足 100 完全不提升</b>）。 */
    public static final double SWIRL_BONUS_ATK_PER_STAGE = 100.0;
    /** 每档提升 0.7%。 */
    public static final float SWIRL_BONUS_PER_STAGE = 0.007f;
    /** 上限 +14%。 */
    public static final float SWIRL_BONUS_CAP = 0.14f;

    /**
     * 队伍里的角色触发星扩散时，按<b>薇斯娜自己的攻击力</b>给的基础伤害提升。
     */
    public float stellarSwirlBaseBonusMult(Vesna vesna) {
        double atk = vesna.getData().getAttributeTotalValue(ModAttributes.ATK.value());
        int stages = (int) Math.floor(atk / SWIRL_BONUS_ATK_PER_STAGE);
        return Math.min(SWIRL_BONUS_CAP, stages * SWIRL_BONUS_PER_STAGE);
    }

    /**
     * 薇斯娜的<b>星扩散户口</b>：冰扩散 → 星扩散，并按攻击力给全队基础伤害提升。
     */
    public IStellarHousehold.StellarHousehold stellarHousehold(Vesna vesna) {
        return new IStellarHousehold.StellarHousehold(
                StellarGlimmerBranch.SWIRL,
                ModElements.CYRO.get(),
                ModElements.ANEMO.get(),
                stellarSwirlBaseBonusMult(vesna));
    }

    // ==================== 突破 4：队伍元素构成加成 ====================

    /** 临时属性来源名（重复设置前先移除，避免叠上去）。 */
    private static final String A4_ATK_SOURCE = "vesna_a4_atk";
    private static final String A4_EM_SOURCE = "vesna_a4_em";
    /** 每有一位 冰/风 角色：攻击力 +6%。 */
    private static final float A4_ATK_PER_MEMBER = 0.06f;
    /** 每有一位其他元素角色：元素精通 +25。 */
    private static final float A4_EM_PER_MEMBER = 25f;
    /** 突破等级门槛。 */
    private static final int A4_ASCENSION = 4;

    /**
     * 「辉映·星扩散」天赋：<b>只在处于星扩散状态时</b>、按队伍元素构成给自己加属性。
     *
     * <pre>
     * 冰/风角色（含薇斯娜自己）：每人 攻击力 +6%
     * 其他元素角色           ：每人 元素精通 +25
     * </pre>
     *
     * <p>用临时属性修饰符实现；退出星扩散 / 突破不够时只移除自己那两个来源，
     * 不碰圣遗物之类别人加的修饰符。
     *
     * <p>命座 4 的「三倍」走 {@link VesnaConstellation#winterRiteMultiplier} 拿倍率
     * —— 命座分支不在这里写。
     */
    private void updateA4Bonuses(Player player, Vesna vesna) {
        boolean active = vesna.getData().getAscensionPhase() >= A4_ASCENSION
                && StellarGlimmer.hasSwirl(vesna);

        if (!active) {
            vesna.getData().removeAttributeModifier(ModAttributes.ATK.value(), A4_ATK_SOURCE);
            vesna.getData().removeAttributeModifier(ModAttributes.ELEMENTAL_MASTERY.value(), A4_EM_SOURCE);
            return;
        }

        int windOrIce = 0;
        int others = 0;
        var attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        if (attachment != null) {
            for (int i = 0; i < 4; i++) {
                var member = attachment.getPartyCharacter(i);
                if (member == null) {
                    continue;
                }
                var element = member.getElemental();
                String id = element == null ? "" : element.getId();
                if (id.equals("anemo") || id.equals("cryo")) {
                    windOrIce++;
                } else {
                    others++;
                }
            }
        }

        vesna.getData().removeAttributeModifier(ModAttributes.ATK.value(), A4_ATK_SOURCE);
        vesna.getData().removeAttributeModifier(ModAttributes.ELEMENTAL_MASTERY.value(), A4_EM_SOURCE);
        // 命座 4：「仪典·冬之凯风」强化 —— 攻击力与元素精通的效果改为原本的三倍
        float scale = vesna.getConstellationObj() instanceof VesnaConstellation constellation
                ? constellation.winterRiteMultiplier(vesna)
                : 1f;
        if (windOrIce > 0) {
            vesna.getData().addAttributeTempPercentModifier(ModAttributes.ATK.value(), A4_ATK_SOURCE,
                    A4_ATK_PER_MEMBER * windOrIce * scale);
        }
        if (others > 0) {
            vesna.getData().addAttributeTempFlatModifier(ModAttributes.ELEMENTAL_MASTERY.value(), A4_EM_SOURCE,
                    A4_EM_PER_MEMBER * others * scale);
        }
    }

    // ==================== 每刻（由 Vesna.tick 转发） ====================

    /**
     * 每刻跑一次（仅服务端）：整肃倒计时 → 退场清层 → 突破 4 重算。
     *
     * <p>顺序必须和重构前 {@code Vesna.tick} 里的一致：先倒数、再判退场、
     * 最后才是属性重算。
     */
    @Override
    public void tick(Player player, PGCharacter character) {
        if (!(character instanceof Vesna vesna)) return;

        // 整肃每层独立倒计时
        tickDecree(vesna);
        // 退场（切到别的角色）时清空整肃。
        // 比 UUID 而不是比对象：角色列表在反序列化后可能换实例，
        // 比对象引用会把「自己还在场上」误判成退场，每刻清一次 → 大权永远是 1。
        var partyAttachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        if (partyAttachment != null) {
            PGCharacter current = partyAttachment.getCurrentCharacter();
            if (current == null || current.getCharacterUUID() != vesna.getCharacterUUID()) {
                clearDecree(vesna);
            }
        }
        // 突破 4 的「辉映·星扩散」队伍加成（只在星扩散状态下生效）
        updateA4Bonuses(player, vesna);
    }
    private static final float[][] NA_TABLE = {
            {0.4042f, 0.4371f, 0.4700f, 0.5170f, 0.5499f, 0.5875f, 0.6392f, 0.6909f, 0.7426f, 0.7990f, 0.8554f, 0.9118f, 0.9682f, 1.0246f, 1.0810f},
            {0.4868f, 0.5264f, 0.5660f, 0.6226f, 0.6622f, 0.7075f, 0.7698f, 0.8320f, 0.8943f, 0.9622f, 1.0301f, 1.0980f, 1.1660f, 1.2339f, 1.3018f},
            {0.2808f, 0.3036f, 0.3265f, 0.3592f, 0.3820f, 0.4081f, 0.4440f, 0.4800f, 0.5159f, 0.5551f, 0.5942f, 0.6334f, 0.6726f, 0.7118f, 0.7510f},
            {0.5917f, 0.6398f, 0.6880f, 0.7568f, 0.8050f, 0.8600f, 0.9357f, 1.0114f, 1.0870f, 1.1696f, 1.2522f, 1.3347f, 1.4173f, 1.4998f, 1.5824f},
            {0.6244f, 0.6752f, 0.7260f, 0.7986f, 0.8494f, 0.9075f, 0.9874f, 1.0672f, 1.1471f, 1.2342f, 1.3213f, 1.4084f, 1.4956f, 1.5827f, 1.6698f},
            {0.7224f, 0.7812f, 0.8400f, 0.9240f, 0.9828f, 1.0500f, 1.1424f, 1.2348f, 1.3272f, 1.4280f, 1.5288f, 1.6296f, 1.7304f, 1.8312f, 1.9320f},
    };

    public static float normalAttackMultiplier(int stage, int level) {
        if (stage < 1 || stage > NA_TABLE.length) {
            return 0f;
        }
        float[] row = NA_TABLE[stage - 1];
        return row[Math.max(1, Math.min(row.length, level)) - 1];
    }

}
