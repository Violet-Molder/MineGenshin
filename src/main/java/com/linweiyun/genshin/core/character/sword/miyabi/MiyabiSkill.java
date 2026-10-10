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
 */
public class MiyabiSkill extends SkillBase {

    /** 长按多少刻算重击。 */
    public static final int CHARGE_TICKS = 6;

    /**
     * 下落攻击停在普攻第二段动画的哪一刻（刻）。
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
}
