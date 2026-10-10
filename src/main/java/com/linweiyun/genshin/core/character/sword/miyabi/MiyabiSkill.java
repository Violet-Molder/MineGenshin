package com.linweiyun.genshin.core.character.sword.miyabi;

import com.linweiyun.genshin.core.element.ModElements;

import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.sword.miyabi.attack.MiyabiChargedAttack;
import com.linweiyun.genshin.core.character.sword.miyabi.attack.MiyabiElementalBurst;
import com.linweiyun.genshin.core.character.sword.miyabi.attack.MiyabiElementalSkill;
import com.linweiyun.genshin.core.character.sword.miyabi.attack.MiyabiNormalAttack;
import com.linweiyun.genshin.core.character.talent.SkillBase;
import com.linweiyun.genshin.core.system.combat.action.ActionDefinition;
import com.linweiyun.genshin.core.system.combat.action.ActionKind;
import com.linweiyun.genshin.core.system.combat.action.ActionSet;
import net.minecraft.world.entity.player.Player;

/**
 * 星见雅的招式。
 *
 * <p>动作表在 {@link MiyabiResources}（时长 / 伤害点 / 削韧），伤害与表现各一招一个类；
 * 这里只处理基类默认行为之外的三件事：
 * <ol>
 *   <li><b>重击</b>：基类默认把重击接到「战技的点按步」上，星见雅要播素材里的重击片段
 *       （{@link MiyabiResources#CHARGED_STEP}，也就是能量不满的那一套）；</li>
 *   <li><b>重击起手</b>：{@value #CHARGE_TICKS} 刻（与素材侧一致），比框架默认的 20 刻短得多；</li>
 *   <li><b>下落攻击</b>：素材里没有下劈那一段，借普攻第二段动画的中间姿态顶
 *       （{@link #PLUNGE_HOLD_TICK} 刻，那一刻正好是举剑下劈）。</li>
 * </ol>
 */
public class MiyabiSkill extends SkillBase {

    /** 长按多少刻算重击。 */
    public static final int CHARGE_TICKS = 6;

    /**
     * 下落攻击停在普攻第二段动画的哪一刻（刻）。
     *
     * <p>单位是<b>刻</b>：1 刻 = 1/20 秒 = 0.05 秒（<b>不是</b> Blockbench 时间轴下面那位 100 进 1 的读数）。
     * Blockbench 上看着对的那一格是 <b>0.17 秒</b> → {@code 0.17 × 20 = 3.4} 刻
     * —— 刀刚劈下去、斩击骨骼（{@code slash_b}）满尺寸那一刻。
     */
    public static final double PLUNGE_HOLD_TICK = 0.3 * 20.0;

    @Override
    public int getMaxCombo() {
        return MiyabiResources.MAX_COMBO;
    }

    @Override
    public int getChargeTicks() {
        return CHARGE_TICKS;
    }

    @Override
    public void onCastStart(Player player, PGCharacter character, ActionKind kind) {
        super.onCastStart(player, character, kind);
        if (kind != ActionKind.ELEMENTAL_SKILL_TAP || !(character instanceof Miyabi miyabi)) {
            return;
        }
        int level = Math.max(1, character.getData().getElementalSkillLevel());
        if (miyabi.isFlyingSnowMode()) {
            miyabi.planFlyingSnow(level);
        } else {
            miyabi.planDeepSnow(player, level);
        }
    }

    @Override
    public void attack(Player player, PGCharacter character, int comboStage) {
        MiyabiNormalAttack.execute(player, character, comboStage);
    }

    @Override
    public void chargeAttack(Player player, PGCharacter character) {
        MiyabiChargedAttack.execute(player, character);
    }

    @Override
    public void elementalSkill(Player player, PGCharacter character, int skillTime) {
        MiyabiElementalSkill.execute(player, character);
    }

    @Override
    public void elementalBurst(Player player, PGCharacter character) {
        MiyabiElementalBurst.execute(player, character);
    }

    @Override
    public String plungingAnimation() {
        return MiyabiResources.PLUNGE_STATE;
    }

    @Override
    public double plungingAnimationHoldTick() {
        return PLUNGE_HOLD_TICK;
    }

    @Override
    public com.linweiyun.elementlib.core.element.GenshinElement attackElement(
            PGCharacter character, ActionKind kind, int comboIndex) {
        boolean conduce = character instanceof Miyabi miyabi && miyabi.isConduce();
        if (kind == ActionKind.NORMAL_ATTACK) {
            return comboIndex >= 4 || conduce ? ModElements.CYRO.get() : ModElements.FYSIKOS.get();
        }
        if (kind == ActionKind.CHARGED_ATTACK) {
            return conduce ? ModElements.CYRO.get() : ModElements.FYSIKOS.get();
        }
        return ModElements.CYRO.get();
    }

    @Override
    public com.linweiyun.genshin.config.character.TalentConfigSource talentConfigSource() {
        return com.linweiyun.genshin.config.character.MiyabiTalentConfig.SOURCE;
    }

    @Override
    public java.util.List<TalentDetail> talentDetails(PGCharacter character, int kind) {
        java.util.List<TalentDetail> rows = new java.util.ArrayList<>();
        if (kind == 0) {
            int na = Math.max(1, character.getData().getNormalAttackLevel());
            addRow(rows, "na1", "na1", na, MiyabiTalent.normalAttackMultiplier(1, na));
            addRow(rows, "na2", "na2", na, MiyabiTalent.normalAttackMultiplier(2, na));
            addRow(rows, "na3", "na3", na, MiyabiTalent.normalAttackMultiplier(3, na));
            addRow(rows, "na4", "na4", na, MiyabiTalent.normalAttackMultiplier(4, na));
            addRow(rows, "na5", "na5", na, MiyabiTalent.normalAttackMultiplier(5, na));
            addRow(rows, "charged", "charged", na, MiyabiTalent.chargedAttackMultiplier(na));
            addRow(rows, "qi", "qi", na, MiyabiTalent.swordQi(na));
            addRow(rows, "frost_moon", "frost_moon", na, MiyabiTalent.frostMoon(na));
        } else if (kind == 1) {
            int sk = Math.max(1, character.getData().getElementalSkillLevel());
            addRow(rows, "deep_snow", "deep_snow", sk, MiyabiTalent.deepSnow(sk));
            addRow(rows, "deep_snow_conduce", "deep_snow_conduce", sk, MiyabiTalent.deepSnowConduce(sk));
            addRow(rows, "flying_snow", "flying_snow", sk, MiyabiTalent.flyingSnowTotal(sk));
            addRow(rows, "snow_cover", "snow_cover", sk, MiyabiTalent.snowCoverPerStack(sk));
            addRow(rows, "rime_per_stack", "rime", sk, MiyabiTalent.rimePerStack(sk));
        }
        return rows;
    }

    private static void addRow(java.util.List<TalentDetail> rows, String labelKey, String configKey,
                               int level, double value) {
        rows.add(new TalentDetail(
                "gui.minegenshin.character_equip.talent.miyabi.detail." + labelKey,
                value,
                "myb-" + configKey + "-" + level));
    }

    @Override
    public double textValue(PGCharacter character, String key) {
        int na = Math.max(1, character.getData().getNormalAttackLevel());
        int sk = Math.max(1, character.getData().getElementalSkillLevel());
        return switch (key) {
            case "na1" -> MiyabiTalent.normalAttackMultiplier(1, na);
            case "na2" -> MiyabiTalent.normalAttackMultiplier(2, na);
            case "na3" -> MiyabiTalent.normalAttackMultiplier(3, na);
            case "na4" -> MiyabiTalent.normalAttackMultiplier(4, na);
            case "na5" -> MiyabiTalent.normalAttackMultiplier(5, na);
            case "charged" -> MiyabiTalent.chargedAttackMultiplier(na);
            case "qi" -> MiyabiTalent.swordQi(na);
            case "frost_moon" -> MiyabiTalent.frostMoon(na);
            case "deep_snow" -> MiyabiTalent.deepSnow(sk);
            case "deep_snow_conduce" -> MiyabiTalent.deepSnowConduce(sk);
            case "flying_snow" -> MiyabiTalent.flyingSnowTotal(sk);
            case "snow_cover" -> MiyabiTalent.snowCoverPerStack(sk);
            case "rime_per_stack" -> MiyabiTalent.rimePerStack(sk);
            default -> 0.0;
        };
    }

    @Override
    public String plungingRecoveryAnimation() {
        return MiyabiResources.PLUNGE_RECOVER_STATE;
    }

    @Override
    public int plungingRecoveryTicks() {
        return MiyabiResources.PLUNGE_CLIP_TICKS;
    }

    @Override
    public String plungingLandingSound() {
        return "imaginary_branch:miyabi_attack_2";
    }

    @Override
    public float plungingLandingSoundVolume() {
        return 1.4F;
    }

    @Override
    protected ActionSet buildDefaultActionSet(PGCharacter character) {
        ActionSet base = super.buildDefaultActionSet(character);

        return ActionSet.deriveFrom(base)
                .chargedAttack(ActionDefinition.builder(ActionKind.CHARGED_ATTACK)
                        .step(MiyabiResources.CHARGED_STEP)
                        .onCastStart(ctx -> this.onCastStart(ctx.player, ctx.character, ActionKind.CHARGED_ATTACK))
                        .onActiveStart(ctx -> this.chargeAttack(ctx.player, ctx.character))
                        .build())
                .build();
    }

    @Override
    public ActionSet buildActionSet(PGCharacter character, String stateKey) {
        ActionSet base = super.buildActionSet(character, stateKey);
        if (Miyabi.SNOW_CHARGE_ACTION_STATE.equals(stateKey)) {
            return ActionSet.deriveFrom(base)
                    .chargedAttack(frostMoonAction())
                    .build();
        }
        if (!Miyabi.SNOW_ACTION_STATE.equals(stateKey)) {
            return base;
        }
        return ActionSet.deriveFrom(base)
                .elementalSkillTap(flyingSnowAction())
                .chargedAttack(frostMoonAction())
                .build();
    }

    /** 星雪的元素战技：换成飞雪的片段。 */
    private ActionDefinition flyingSnowAction() {
        return ActionDefinition.builder(ActionKind.ELEMENTAL_SKILL_TAP)
                .step(MiyabiResources.flyingSnowStep())
                .onCastStart(ctx -> this.onCastStart(ctx.player, ctx.character, ActionKind.ELEMENTAL_SKILL_TAP))
                .onActiveStart(ctx -> this.elementalSkill(ctx.player, ctx.character, 0))
                .build();
    }

    /** 星雪的重击：换成霜月的片段。 */
    private ActionDefinition frostMoonAction() {
        return ActionDefinition.builder(ActionKind.CHARGED_ATTACK)
                .step(MiyabiResources.frostMoonStep())
                .onCastStart(ctx -> this.onCastStart(ctx.player, ctx.character, ActionKind.CHARGED_ATTACK))
                .onActiveStart(ctx -> this.chargeAttack(ctx.player, ctx.character))
                .build();
    }
}
