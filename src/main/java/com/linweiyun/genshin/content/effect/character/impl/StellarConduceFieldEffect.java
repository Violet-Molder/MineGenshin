package com.linweiyun.genshin.content.effect.character.impl;

import com.linweiyun.genshin.content.effect.character.ICharacterEffect;
import com.linweiyun.genshin.content.effect.character.CharacterEffectInstance;
import com.linweiyun.genshin.content.entities.area.StellarPrismEntity;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.elementlib.core.element.GenshinElement;
import net.minecraft.world.entity.player.Player;

/** 极星辉域给域内角色的冰/雷元素伤害加成。 */
public class StellarConduceFieldEffect implements ICharacterEffect {

    public static final float DAMAGE_BONUS = 0.20f;

    private static final String STACK_SOURCE = "stellar_conduce_stacks";
    private static final float STACK_FIRST = 0.08f;
    private static final float STACK_PER_LAYER = 0.01f;
    private static final float STACK_CAP = 0.20f;

    @Override
    public float getDamageBonus(AttackType attackType, GenshinElement element) {
        if (element == ModElements.CYRO.get() || element == ModElements.ELECTRO.get()) {
            return DAMAGE_BONUS;
        }
        return 0f;
    }

    @Override
    public boolean onEffectTick(Player holder, PGCharacter character, CharacterEffectInstance instance) {
        int stacks = StellarPrismEntity.releasedStacksAt(holder.level(), holder.position());
        float bonus = stacks <= 0 ? 0f : Math.min(STACK_CAP, STACK_FIRST + STACK_PER_LAYER * stacks);
        character.getData().setAttributeTempPercentModifier(
                ModAttributes.CYRO_BONUS.value(), STACK_SOURCE, bonus);
        character.getData().setAttributeTempPercentModifier(
                ModAttributes.ELECTRO_BONUS.value(), STACK_SOURCE, bonus);
        return true;
    }

    @Override
    public void onEffectRemoved(Player holder, PGCharacter character, CharacterEffectInstance instance) {
        character.getData().removeAttributeModifier(ModAttributes.CYRO_BONUS.value(), STACK_SOURCE);
        character.getData().removeAttributeModifier(ModAttributes.ELECTRO_BONUS.value(), STACK_SOURCE);
    }
}
