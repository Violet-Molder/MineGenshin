package com.linweiyun.genshin.core.system.combat.attack;

import com.linweiyun.elementlib.core.attachment.StatusContainer;
import com.linweiyun.elementlib.core.attachment.ElementalAttachments;
import com.linweiyun.genshin.core.character.util.CharacterKeys;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.elementlib.core.system.about.AttachmentProfile;
import com.linweiyun.elementlib.core.system.about.AttachmentSource;
import com.linweiyun.elementlib.core.system.about.ElementalAttachable;
import com.linweiyun.elementlib.core.system.about.ElementalAttachmentHelper;
import com.linweiyun.elementlib.core.system.about.host.EntityHost;
import com.linweiyun.genshin.core.system.combat.damage.CombatMath;
import com.linweiyun.genshin.core.system.combat.damage.DamageTrace;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.elementlib.core.system.combat.decay.DecayCounterManager;
import com.linweiyun.elementlib.core.system.combat.decay.DecayResult;
import com.linweiyun.elementlib.core.system.combat.decay.DecaySpec;
import com.linweiyun.elementlib.core.system.reaction.ElementalReactionManager;
import com.linweiyun.elementlib.core.system.reaction.ReactionContext;
import com.linweiyun.elementlib.core.system.reaction.ReactionResult;
import com.linweiyun.genshin.core.system.shield.ShieldService;
import com.linweiyun.genshin.core.system.shield.ShieldService.AttachDecision;
import com.linweiyun.elementlib.api.ElementalReactionType;
import com.linweiyun.genshin.core.system.registry.register.ModReactionTypes;
import net.minecraft.world.entity.LivingEntity;
import com.linweiyun.elementlib.core.system.about.AttachContext;
import com.linweiyun.elementlib.core.system.about.AttachResult;

/**
 * <b>直伤管线</b> —— 角色技能打出来的那一下。
 *
 * <pre>
 * ① 衰减：算元素量与伤害系数（连续打同一个目标会递减）
 * ② 附着：把元素挂到目标身上
 * ③ 反应：挂上之后尝试触发反应（反应飘字由 ElementalReactionManager 内部处理）
 * ④ 乘区：伤害 = 基础区 × 倍率区 × 暴击区 × 增伤区 × 防御区 × 抗性区
 *                  ×（增幅反应）反应倍率 × 反应加成区 × 衰减系数
 * ⑤ 免疫：目标免疫这个元素时把伤害归零 —— <b>只归零伤害</b>，②③ 已经发生过了
 * </pre>
 *
 * <p>日志里每个乘区一个【】，只写真正生效的项（见 {@code DamageTrace}）。
 */
final class DirectDamagePipeline {

    private DirectDamagePipeline() {
    }

    static float calculate(ModDamageSource damageSource, PGCharacter attacker, LivingEntity target) {
        // spec 要在角色效果钩子跑完之后重新读一次（钩子可能换掉整个 spec）
        ModDamageSpec spec = damageSource.getSpec();
        LivingEntity sourceEntity = (LivingEntity) damageSource.getEntity();
        PGCharacter targetCharacter = AttackerResolver.resolveCharacter(target);
        boolean hasAttacker = attacker != null;

        // 日志不输出时（见 DamageTrace#active）连头部的字符串都不拼
        boolean tracing = DamageTrace.active();
        DamageTrace trace = DamageTrace.start("直伤");
        if (tracing) {
            trace.headAttack(spec.getAttackType(), spec.getElement())
                    .headEntities(sourceEntity, target, hasAttacker ? attacker.getName() : null);
        }

        // ── ⓪ 基础伤害区 ──
        float baseDamage = hasAttacker ? DamageZones.baseDamage(attacker, spec) : 0f;

        // ── ① 衰减 ──
        DecayResult decayResult = DecayResult.NONE;
        if (spec.hasDecayTag()) {
            DecayCounterManager manager = ElementalAttachments.decayCounter(target);
            long currentTick = target.level().getGameTime();
            String characterKey = hasAttacker ? CharacterKeys.keyOf(attacker) : null;
            DecaySpec decaySpec = new DecaySpec(spec.getDecayTag(), spec.getEffectiveDecayGroup());
            manager.getOrCreateCounter(sourceEntity, characterKey, decaySpec, currentTick);
            decayResult = manager.processHit(sourceEntity, characterKey, decaySpec, currentTick);
        }
        float elementCoefficient = decayResult.getElementCoefficient();

        // ── ② 附着 ──
        // 「元素量 → 附着档次」的映射只有一份真相，在 AttachmentProfile.forAmount。
        AttachmentProfile profile = AttachmentProfile.forAmount(spec.getElementAmount());
        if (spec.getElement().isInstant()) {
            // 瞬发元素（风/岩这类）不参与常规衰减，附着量固定按 1.0 / 0.5 结算
            profile = new AttachmentProfile(
                    profile.getBaseQuantity(), profile.getLossMultiplier(), 1.0f, 0.5f);
        }
        boolean canAttach = spec.hasAuraPotential() && elementCoefficient > 0;
        boolean canReact = canAttach;

        // 宿主 —— 生物是「实体 + 它自带的容器」。附着、两段筛查、元素钩子全部走这一个契约
        // （方块是 BlockHost，同一个 ElementalHost）。
        EntityHost host = EntityHost.of(target);

        // ── ②.5 过盾 ──
        // 护盾可以「吞掉」某些元素的附着（冰盾遇水、遇冰）。盾的判定优先于附着：
        //   BLOCK      → 不附着、不反应、不消耗元素量（这次攻击仍然算「受击」，伤害为 0）
        //   REACT_ONLY → 只反应不附着（盾自挂的元素和它反应，双方一起被吃掉）
        //   ALLOW      → 照常附着 + 反应
        if (canAttach) {
            AttachDecision decision = ShieldService.onElementalAttack(
                    target, spec.getElement(), spec.getElementAmount() * elementCoefficient, true);
            if (decision == AttachDecision.BLOCK) {
                canAttach = false;
                canReact = false;
            } else if (decision == AttachDecision.REACT_ONLY) {
                canAttach = false;
            }
        }

        StatusContainer container = host != null ? host.container() : null;
        if (canAttach && spec.getElement().isInstant()) {
            // 瞬发元素（风/岩）只为触发一次反应而来：宿主连一个候选反应都不收，就不该留下附着。
            if (container == null
                    || !ElementalReactionManager.canElementReact(spec.getElement(), container, host)) {
                canAttach = false;
                canReact = false;
            }
        }

        // ── 附着本身 ──
        // 顺序要求：附着发生在任何免疫/伤害生效判断<b>之前</b>，只受宿主筛查与护盾裁决影响。
        ReactionResult reactionResult = null;
        if (canAttach && host != null) {
            long gameTime = target.level().getGameTime();
            AttachContext attachContext = AttachContext.attack(
                    hasAttacker ? CharacterKeys.keyOf(attacker) : null,
                    hasAttacker ? gameTime : 0L,
                    damageSource.getEntity(),
                    spec.getElementAmount() * elementCoefficient);
            AttachResult attachResult = ElementalAttachmentHelper.attach(
                    host, spec.getElement(), AttachmentSource.NORMAL_ATTACK, profile, attachContext);
            if (!attachResult.attached()) {
                // 宿主拒收这次附着 → 反应同样不发生。
                // 「没挂上去就没有反应」是附着与反应之间的唯一顺序约束；先手元素保留 = 共存。
                canReact = false;
            } else {
                reactionResult = attachResult.reaction();
            }
        }

        ElementalReactionType reactionType =
                reactionResult != null && reactionResult.isReacted() ? reactionResult.getReactionType() : null;
        trace.head("反应", reactionType == null ? "无" : reactionType);

        boolean amplifying = ModReactionTypes.is(reactionType, ModReactionTypes.MELT)
                || ModReactionTypes.is(reactionType, ModReactionTypes.VAPORIZE);

        // ── ④ 乘区 ──
        DamageZones.CritRoll critRoll = DamageZones.rollCrit(attacker, spec);
        spec.setCrit(critRoll.isCrit());
        float baseMultiplierBonus = hasAttacker ? spec.getSkillMultiplierBonus() : 0f;
        float elementalBonus = hasAttacker ? DamageZones.elementalBonus(attacker, spec) : 0f;
        float effectBonus = hasAttacker ? DamageZones.effectBonus(attacker, spec) : 0f;
        // 按招式盖的增伤（也在增伤区；天赋自己算好的那一档）
        float specDamageBonus = hasAttacker ? spec.getDamageBonus() : 0f;
        int attackerLevel = DamageZones.attackerLevel(sourceEntity, attacker);
        double defenderDefense = DamageZones.defenderDefense(target, targetCharacter);
        float defenseZone = CombatMath.defenseZone(attackerLevel, defenderDefense);
        float rawResistance = DamageZones.rawResistance(spec.getElement(), target, targetCharacter);
        float resistanceZone = CombatMath.resistanceZone(rawResistance);
        float amplifyMultiplier = amplifying ? reactionResult.getAmplifyMultiplier() : 1f;
        float emBonus = amplifying ? DamageZones.emBonus(attacker, reactionType) : 0f;
        float decayCoefficient = decayResult.getDamageCoefficient();

        float computedDamage = baseDamage
                * (1f + baseMultiplierBonus)
                * critRoll.zone()
                * (1f + elementalBonus + effectBonus + specDamageBonus)
                * defenseZone
                * resistanceZone
                * (amplifying ? amplifyMultiplier * (1f + emBonus) : 1f)
                * damageBonusSovereignty(spec)
                * decayCoefficient;

        // ── ⑤ 元素免疫 ──
        // 「免疫只拦伤害」：附着与反应在上面已经跑完了（挂得上、能反应、能飘字），
        // 只是这一下伤害按 0 结算。以前免疫写在 hurtServer 的提前 return 里，
        // 而附着是在本管线内部做的 → 免疫等于「连附着都不发生」。
        boolean immuneToDamage =
                ElementalAttachable.isImmuneToDamage(target, spec.getElement());
        float finalDamage = immuneToDamage ? 0f : computedDamage;

        // ── 日志（每个乘区一个【】）──
        //
        // ⚠️ 数值行必须是「把数字代进公式」的写法，而不是乘区的计算结果：
        //     展开公式 伤害 = 【攻击力 × 攻击力倍率 + 附加伤害】 × 【1 + 暴击伤害】 × …
        //     数值　　 伤害 = 【1144.625 × 0.130 + 0】        × 【1 + 0.966】    × …
        //     （直接把 1.966 填进去的话，就没法把数字和公式里的名字对应起来了。）
        //
        // 整块包在 tracing 里：这里是每次直伤最贵的一段字符串拼接（含若干次 fmt），
        // 日志不输出时它纯粹是白干（见 DamageTrace#active）。
        if (tracing) {
            DamageZones.ZoneText baseZone = hasAttacker
                    ? DamageZones.baseZoneText(attacker, spec)
                    : new DamageZones.ZoneText("0", "0");
            DamageZones.ZoneText bonusZone = hasAttacker
                    ? DamageZones.damageBonusZoneText(attacker, spec)
                    : new DamageZones.ZoneText("1", "1");

            trace.zone("基础区", baseZone.formula(), baseZone.value());
            trace.zone("暴击区", "1 + 暴击伤害",
                    critRoll.isCrit() ? "1 + " + DamageTrace.fmt(critRoll.critDamage()) : "1");
            trace.zone("倍率区", "1 + 倍率提升", "1 + " + DamageTrace.fmt(baseMultiplierBonus));
            trace.zone("增伤区", bonusZone.formula(), bonusZone.value());
            trace.zone("防御区", "(攻方等级×5+500)/(攻方等级×5+500+守方防御)",
                    "(" + attackerLevel + "×5+500)/(" + attackerLevel + "×5+500+"
                            + DamageTrace.fmt(defenderDefense) + ")");
            trace.zone("抗性区", "1 - 抗性", "1 - " + DamageTrace.fmt(rawResistance));
            if (amplifying) {
                trace.zone("反应倍率区", "反应倍率", DamageTrace.fmt(amplifyMultiplier));
                float em = (float) DamageZones.elementalMastery(hasAttacker ? attacker : null);
                trace.zone("反应加成区", "1 + 2.78×元素精通/(元素精通+1400)",
                        "1 + 2.78×" + DamageTrace.fmt(em) + "/(" + DamageTrace.fmt(em) + "+1400)");
            }
            trace.zone("大权区", "1 + 大权加成", "1 + " + DamageTrace.fmt(spec.getSovereigntyBonus()));
            trace.zone("衰减区", "衰减伤害系数", DamageTrace.fmt(decayCoefficient));
            if (immuneToDamage) {
                trace.zone("免疫区", "元素免疫", "0");
            }
            trace.result(finalDamage);
            trace.log();
        }
        return finalDamage;
    }

    /** 大权区（按招式显式打开；没打开就是 1）。 */
    private static float damageBonusSovereignty(ModDamageSpec spec) {
        return 1f + spec.getSovereigntyBonus();
    }
}
