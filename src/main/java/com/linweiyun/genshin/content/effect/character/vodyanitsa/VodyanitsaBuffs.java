package com.linweiyun.genshin.content.effect.character.vodyanitsa;

import com.linweiyun.genshin.content.effect.character.CharacterEffectInstance;
import com.linweiyun.genshin.content.effect.character.ICharacterEffect;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.reaction.StellarGlimmerBranch;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import net.minecraft.world.entity.player.Player;

/**
 * 沃雅妮莎的命座 buff（1 / 2 / 6 命）。
 *
 * <p>都用效果系统承载时长与移除（和 A4 的领唱/重唱一套思路），
 * 加伤口径按各自文案落在对应乘区：
 * <ul>
 *   <li>{@link Spotlight}（1 命）：攻击力 —— 临时固定值修饰符</li>
 *   <li>{@link DuetElement} / {@link DuetStellar}（2 命「黑与白的双音」）：<b>暴击区</b>，
 *       按元素/反应类型给，走 {@link ICharacterEffect#getCritDamageBonus}</li>
 *   <li>{@link Glimmer}（6 命）：星扩散反应伤害<b>擢升</b>（擢升区）+ 水/冰伤害（增伤区）</li>
 * </ul>
 */
public final class VodyanitsaBuffs {

    private VodyanitsaBuffs() {
    }

    public static final int DURATION_TICKS = 5 * 20;
    private static final String SPOTLIGHT_SOURCE = "vodyanitsa_spotlight";

    /** 1 命：攻击力 +沃雅妮莎生命值上限的 1%。 */
    public static class Spotlight implements ICharacterEffect {

        @Override
        public void onEffectAdded(Player holder, PGCharacter character, CharacterEffectInstance instance) {
            float bonus = VodyanitsaSongEffects.onePercentOfMaxHp(holder);
            character.getData().addAttributeTempFlatModifier(
                    ModAttributes.ATK.value(), SPOTLIGHT_SOURCE, bonus);
        }

        @Override
        public void onEffectRemoved(Player holder, PGCharacter character, CharacterEffectInstance instance) {
            character.getData().removeAttributeModifier(ModAttributes.ATK.value(), SPOTLIGHT_SOURCE);
        }

        @Override
        public void onEffectOverride(Player holder, PGCharacter character,
                                     CharacterEffectInstance existingInstance,
                                     CharacterEffectInstance newInstance) {
            existingInstance.setDuration(Math.max(existingInstance.getDuration(), newInstance.getDuration()));
        }
    }

    /** 2/6 命「黑与白的双音」：<b>水/冰伤害</b>暴击伤害 +50%。 */
    public static class DuetElement implements ICharacterEffect {
        @Override
        public float getCritDamageBonus(GenshinElement element, boolean stellarReaction) {
            if (element == ModElements.HYDRO.get() || element == ModElements.CYRO.get()) {
                return 0.50f;
            }
            return 0f;
        }

        @Override
        public void onEffectOverride(Player holder, PGCharacter character,
                                     CharacterEffectInstance existingInstance,
                                     CharacterEffectInstance newInstance) {
            existingInstance.setDuration(Math.max(existingInstance.getDuration(), newInstance.getDuration()));
        }
    }

    /** 2/6 命「黑与白的双音」（流荡风旋版）：<b>星扩散反应伤害</b>暴击伤害 +60%。 */
    public static class DuetStellar implements ICharacterEffect {
        @Override
        public float getCritDamageBonus(GenshinElement element, boolean stellarReaction) {
            return stellarReaction ? 0.60f : 0f;
        }

        @Override
        public void onEffectOverride(Player holder, PGCharacter character,
                                     CharacterEffectInstance existingInstance,
                                     CharacterEffectInstance newInstance) {
            existingInstance.setDuration(Math.max(existingInstance.getDuration(), newInstance.getDuration()));
        }
    }

    /** 6 命：遥久之歌期间，队伍附近角色的星扩散反应伤害擢升 30%、水/冰伤害 +60%。 */
    public static class Glimmer implements ICharacterEffect {

        @Override
        public float getElevationBonus(StellarGlimmerBranch branch) {
            return branch == StellarGlimmerBranch.SWIRL ? 0.30f : 0f;
        }

        @Override
        public float getDamageBonus(AttackType attackType, GenshinElement element) {
            if (element == ModElements.HYDRO.get() || element == ModElements.CYRO.get()) {
                return 0.60f;
            }
            return 0f;
        }

        @Override
        public void onEffectOverride(Player holder, PGCharacter character,
                                     CharacterEffectInstance existingInstance,
                                     CharacterEffectInstance newInstance) {
            existingInstance.setDuration(Math.max(existingInstance.getDuration(), newInstance.getDuration()));
        }
    }
}
