package com.linweiyun.genshin.core.system.combat.attack;

import com.linweiyun.genshin.content.effect.character.CharacterEffectInstance;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.elementlib.core.system.about.ElementalAttachable;
import com.linweiyun.genshin.core.system.combat.damage.DamageTrace;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.genshin.core.system.poise.ReactionPoiseTable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Objects;

/**
 * <b>伤害入口</b> —— 所有伤害都从这里进，按 {@link ModDamageSpec.DamageType} 分发到四条管线。
 *
 * <h2>四条管线去哪了</h2>
 * <table border="1">
 *   <caption>管线分工</caption>
 *   <tr><th>伤害类型</th><th>实现</th><th>公式摘要</th></tr>
 *   <tr><td>{@code DIRECT} 直伤</td><td>{@link DirectDamagePipeline}</td>
 *       <td>基础 × 暴击 × 增伤 × 防御 × 抗性 × 反应 × 衰减</td></tr>
 *   <tr><td>{@code TRANSFORMATIVE} 剧变</td><td>{@link TransformativeDamage}</td>
 *       <td>等级系数 × 反应倍率 × 反应加成 × 抗性</td></tr>
 *   <tr><td>{@code LUNAR} 月曜</td><td>{@link LunarDamage}</td>
 *       <td>基础 × 提升 × 倍率 × 反应加成 × 抗性 × 暴击 × 擢升</td></tr>
 *   <tr><td>{@code STELLAR} 星烁</td><td>{@link StellarDamage}</td>
 *       <td>基础 × 反应加成 × 抗性 × 暴击 × 擢升 × 大权</td></tr>
 * </table>
 *
 * <p>乘区本身（暴击/增伤/防御/抗性/精通/等级）统一在 {@link DamageZones}，
 * 「攻击者是不是角色」在 {@link AttackerResolver}。
 * 这个类只做两件事：<b>分发</b>、以及直伤特有的「先跑一遍角色效果钩子再算基础伤害」。
 *
 * <h2>日志</h2>
 * 每次伤害结算都会输出一条 {@link DamageTrace}：用的公式 + 公式里每一个值。
 * 关掉它把 {@link DamageTrace#ENABLED} 设成 false。
 */
public final class HurtEntityHelper {

    private HurtEntityHelper() {
    }

    /**
     * 统一伤害入口。
     *
     * @param damageSource 伤害来源（里面塞着 {@link ModDamageSpec}）
     * @param attacker     攻击者角色；非角色攻击者为 null
     * @param target       被打的目标
     * @return 最终伤害（还没乘原版的任何东西，mixin 会拿它当结算值）
     */
    public static float calculateFinalModDamage(ModDamageSource damageSource,
                                                PGCharacter attacker,
                                                LivingEntity target) {
        ModDamageSpec spec = damageSource.getSpec();

        // 非直伤 → 各自独立的管线（不跑附着/衰减，也不吃增伤区与防御区）
        if (spec.getDamageType() != ModDamageSpec.DamageType.DIRECT) {
            LivingEntity sourceEntity = (LivingEntity) damageSource.getEntity();
            float dealt = switch (spec.getDamageType()) {
                case TRANSFORMATIVE -> TransformativeDamage.calculate(sourceEntity, target, spec);
                case LUNAR -> LunarDamage.calculate(sourceEntity, attacker, target, spec);
                case STELLAR -> StellarDamage.calculate(attacker, target, spec);
                // 激化是把加成加进直伤，本身不单独结算
                case QUICKEN -> 0f;
                default -> 0f;
            };
            // 反应自带的削韧与冲击（超载 90/击飞、扩散 130…）。
            // 放在这里是因为这是唯一同时拿得到「反应类型 + 目标 + 伤害来源」的地方；
            // 这一笔不受「伤害被免疫归零」影响 —— 反应发生了就是发生了。
            ReactionPoiseTable.apply(target, spec.getTransformativeReactionType(), damageSource);
            return applyElementImmunity(target, spec.getElement(), dealt);
        }

        // 直伤：先给角色效果一次改写伤害的机会（onAttacked），再进直伤管线
        fireAttackedHooks(damageSource, attacker, target);
        float finalDamage = DirectDamagePipeline.calculate(damageSource, attacker, target);

        // 直伤的免疫在 DirectDamagePipeline 里已经按「免疫区」结算过（附着与反应也都在那里跑完了），
        // 这里再问一次是幂等的：已经免疫过就是 0。
        return applyElementImmunity(target, spec.getElement(), finalDamage);
    }

    /**
     * 元素免疫 —— <b>只把伤害归零，不拦附着、不拦反应</b>。
     *
     * <p>谁免疫什么由目标自己回答（{@link ElementalAttachable#isImmuneToElementDamage}）：
     * 元素生物免疫自己的主元素，普通史莱姆免疫自己那种元素。附着与反应在伤害管线里
     * 已经发生过，所以「打上去会挂元素、会反应、但不掉血」。
     */
    private static float applyElementImmunity(LivingEntity target, GenshinElement element,
                                              float damage) {
        if (damage <= 0f) {
            return damage;
        }
        return ElementalAttachable.isImmuneToDamage(target, element) ? 0f : damage;
    }

    /**
     * 让攻击者身上的每个效果都有机会在「挨打/出手」时改点东西
     * （申鹤冰凌那种按次数加伤就挂在这里）。
     */
    private static void fireAttackedHooks(ModDamageSource damageSource, PGCharacter attacker,
                                          LivingEntity target) {
        if (attacker == null) {
            return;
        }
        Player holder = damageSource.getEntity() instanceof Player p ? p : null;
        for (CharacterEffectInstance effect
                : new ArrayList<>(attacker.getData().getEffectContainer().getEffects())) {
            if (effect == null) {
                continue;
            }
            Objects.requireNonNull(effect.getEffect()).onAttacked(holder, attacker, target, effect, damageSource);
        }
    }
}
