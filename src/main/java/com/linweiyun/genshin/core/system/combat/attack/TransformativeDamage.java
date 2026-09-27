package com.linweiyun.genshin.core.system.combat.attack;

import com.linweiyun.genshin.config.reaction.ReactionConfig;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.element.GenshinElement;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.damage.CombatEntityAccessor;
import com.linweiyun.genshin.core.system.combat.damage.CombatMath;
import com.linweiyun.genshin.core.system.combat.damage.DamageTrace;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.genshin.core.system.reaction.ElementalReactionType;
import net.minecraft.world.entity.LivingEntity;

/**
 * <b>剧变反应管线</b> —— 超导 / 感电 / 扩散 / 碎冰这类「不吃攻击力」的反应伤害。
 *
 * <pre>
 * 伤害 = 等级系数区 × 反应倍率区 × 反应加成区 × 抗性区
 * </pre>
 *
 * <p>和直伤的区别：<b>没有</b>基础伤害区、暴击区、增伤区、防御区 ——
 * 只受「角色等级、反应本身倍率、元素精通、目标抗性」影响。
 */
final class TransformativeDamage {

    private TransformativeDamage() {
    }

    static float calculate(LivingEntity attacker, LivingEntity target, ModDamageSpec spec) {
        ElementalReactionType reactionType = spec.getTransformativeReactionType();
        PGCharacter character = AttackerResolver.resolveCharacter(attacker);
        PGCharacter targetCharacter = AttackerResolver.resolveCharacter(target);

        DamageTrace trace = DamageTrace.start("剧变反应");
        boolean tracing = DamageTrace.active();
        if (tracing) {
            trace.headAttack(spec.getAttackType(), spec.getElement());
            trace.head("反应", reactionType);
            trace.headEntities(attacker, target, character == null ? null : character.getName());
        }

        int level = CombatEntityAccessor.getAttackerLevel(attacker, character);
        double levelCoefficient = DamageZones.levelCoefficient(level);
        float reactionMultiplier = switch (reactionType) {
            case ELECTRO_CHARGED -> (float) ReactionConfig.ELECTROCHARGED.get();
            case SUPERCONDUCT -> (float) ReactionConfig.SUPERCONDUCT.get();
            case SWIRL -> (float) ReactionConfig.SWIRL.get();
            default -> 1.0f;
        };
        float emBonus = DamageZones.emBonus(character, reactionType);
        GenshinElement damageElement = spec.getElement() != null
                ? spec.getElement()
                : (reactionType == ElementalReactionType.ELECTRO_CHARGED
                        ? ModElements.ELECTRO.get() : ModElements.FYSIKOS.get());
        float rawResistance = DamageZones.rawResistance(damageElement, target, targetCharacter);
        float resistanceZone = CombatMath.resistanceZone(rawResistance);

        float damage = (float) (levelCoefficient * reactionMultiplier * (1f + emBonus) * resistanceZone);

        // 数值行 = 把数字代进公式（不是填算好的乘区结果）
        if (tracing) {
            float em = (float) DamageZones.elementalMastery(character);
            // ⚠️ 等级系数区用的是**剧变反应的等级系数**（按等级查表，见 DamageZones.levelCoefficient），
            //    不是防御区那个「等级×5+500」——写成后者的话日志里的数根本对不上算出来的伤害。
            trace.zone("等级系数区", "等级系数(按等级查表)", DamageTrace.fmt(levelCoefficient));
            trace.zone("反应倍率区", "反应倍率", DamageTrace.fmt(reactionMultiplier));
            trace.zone("反应加成区", "1 + 16×元素精通/(元素精通+2000)",
                    "1 + 16×" + DamageTrace.fmt(em) + "/(" + DamageTrace.fmt(em) + "+2000)");
            trace.zone("抗性区", "1 - 抗性", "1 - " + DamageTrace.fmt(rawResistance));
            trace.result(damage);
            trace.log();
        }
        return damage;
    }
}
