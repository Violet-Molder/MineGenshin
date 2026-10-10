package com.linweiyun.genshin.core.character.sword.miyabi;

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
            miyabi.planDeepSnow(level);
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
        if (!"snow".equals(stateKey)) {
            return base;
        }
        return ActionSet.deriveFrom(base)
                .elementalSkillTap(ActionDefinition.builder(ActionKind.ELEMENTAL_SKILL_TAP)
                        .step(MiyabiResources.flyingSnowStep())
                        .onCastStart(ctx -> this.onCastStart(ctx.player, ctx.character, ActionKind.ELEMENTAL_SKILL_TAP))
                        .onActiveStart(ctx -> this.elementalSkill(ctx.player, ctx.character, 0))
                        .build())
                .chargedAttack(ActionDefinition.builder(ActionKind.CHARGED_ATTACK)
                        .step(MiyabiResources.frostMoonStep())
                        .onCastStart(ctx -> this.onCastStart(ctx.player, ctx.character, ActionKind.CHARGED_ATTACK))
                        .onActiveStart(ctx -> this.chargeAttack(ctx.player, ctx.character))
                        .build())
                .build();
    }
}
