package com.linweiyun.genshin.core.system.combat.attack;

import com.linweiyun.genshin.config.reaction.ReactionConfig;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.damage.CombatEntityAccessor;
import com.linweiyun.genshin.core.system.combat.damage.CombatMath;
import com.linweiyun.genshin.core.system.combat.damage.DamageTrace;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.core.system.reaction.ElementalReactionType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * <b>月曜反应管线</b>（月感电 / 月绽放 / 月结晶）。
 *
 * <pre>
 * 单人：
 *   基础区 = 生命值 × 生命值倍率 （写了 hpMult 时）
 *          或 直伤系数 × 攻击力   （否则）
 *   提升区 = 基础区 × (1 + 基础提升) + 基础附加
 *   伤害   = 提升区 × 倍率区 × 反应加成区 × 抗性区 × 暴击区 × 擢升区
 * 多人：每人算一份理论伤害，按 1 / 1÷2 / 1÷12 / 1÷12 / 1÷24… 加权求和
 * </pre>
 *
 * <p>月曜<b>无视防御</b>、<b>不吃增伤区</b> —— 这是它和直伤最大的区别。
 */
final class LunarDamage {

    private LunarDamage() {
    }

    // ==================== 入口 ====================

    static float calculate(LivingEntity attacker, PGCharacter character,
                           LivingEntity target, ModDamageSpec spec) {
        PGCharacter attackerCharacter = character != null
                ? character : AttackerResolver.resolveCharacter(attacker);

        DamageTrace trace = DamageTrace.start("月曜反应");
        if (DamageTrace.active()) {
            trace.headAttack(spec.getAttackType(), ModElements.ELECTRO.get());
            trace.head("反应", ElementalReactionType.LUNAR_CHARGED);
            trace.headEntities(attacker, target,
                    attackerCharacter == null ? null : attackerCharacter.getName());
        }

        List<PGCharacter> contributors = spec.getLunarContributors();
        if (contributors != null && !contributors.isEmpty()) {
            return calculateCombined(contributors, target, spec, trace);
        }
        return calculateSingle(attackerCharacter, target, spec, trace);
    }

    // ==================== 单人 ====================

    private static float calculateSingle(@Nullable PGCharacter character, LivingEntity target,
                                         ModDamageSpec spec, DamageTrace trace) {
        boolean byHp = spec.getHpMultiplier() > 0;
        float hp = character != null
                ? (float) character.getData().getAttributeTotalValue(ModAttributes.MAX_HP.value()) : 0f;
        float atk = character != null
                ? (float) character.getData().getAttributeTotalValue(ModAttributes.ATK.value()) : 0f;
        double directCoefficient = ReactionConfig.LUNAR_DIRECT_BASE_COEFFICIENT.get();

        float base = byHp ? hp * spec.getHpMultiplier() : (float) (directCoefficient * atk);
        float baseBoost = base * (1f + spec.getLunarBaseBonus()) + spec.getLunarBaseFlat();

        float multiplier = spec.getAtkMultiplier();
        float emBonus = DamageZones.emBonus(character, ElementalReactionType.LUNAR_CHARGED);
        float rawResistance = DamageZones.rawResistance(ModElements.ELECTRO.get(), target,
                AttackerResolver.resolveCharacter(target));
        float resistanceZone = CombatMath.resistanceZone(rawResistance);
        DamageZones.CritRoll critRoll = DamageZones.rollCrit(character);
        spec.setCrit(critRoll.isCrit());

        float damage = baseBoost * multiplier * (1f + emBonus + DamageZones.LUNAR_SPECIAL_BONUS)
                * resistanceZone * critRoll.zone() * DamageZones.elevationZone();

        // ── 日志（每个乘区一个【】；0/1 的项也照写，只有基础区里没参与的属性不写） ──
        // 数值行是把数字代进公式（不是填算好的乘区结果），这样才能和公式里的名字一一对应
        if (DamageTrace.active()) {
            float em = (float) DamageZones.elementalMastery(character);
            trace.zone("基础区",
                    byHp ? "生命值 × 生命值倍率" : "直伤系数 × 攻击力",
                    byHp ? DamageTrace.fmt(hp) + " × " + DamageTrace.fmt(spec.getHpMultiplier())
                            : DamageTrace.fmt(atk) + " × " + DamageTrace.fmt(directCoefficient));
            trace.zone("提升区", "基础区 × (1 + 基础提升) + 基础附加",
                    DamageTrace.fmt(base)
                            + " × (1 + " + DamageTrace.fmt(spec.getLunarBaseBonus()) + ")"
                            + " + " + DamageTrace.fmt(spec.getLunarBaseFlat()));
            trace.zone("倍率区", "倍率", DamageTrace.fmt(multiplier));
            trace.zone("反应加成区", "1 + 6×元素精通/(元素精通+2000) + 月曜专属加成",
                    "1 + 6×" + DamageTrace.fmt(em) + "/(" + DamageTrace.fmt(em) + "+2000)"
                            + " + " + DamageTrace.fmt(DamageZones.LUNAR_SPECIAL_BONUS));
            trace.zone("抗性区", "1 - 抗性", "1 - " + DamageTrace.fmt(rawResistance));
            trace.zone("暴击区", "1 + 暴击伤害",
                    critRoll.isCrit() ? "1 + " + DamageTrace.fmt(critRoll.critDamage()) : "1");
            trace.zone("擢升区", "1", DamageTrace.fmt(DamageZones.elevationZone()));
            trace.result(damage);
            trace.log();
        }
        return damage;
    }

    // ==================== 多人合并 ====================

    private static float calculateCombined(List<PGCharacter> contributors, LivingEntity target,
                                           ModDamageSpec spec, DamageTrace trace) {
        List<Result> results = new ArrayList<>();
        for (PGCharacter contributor : contributors) {
            int level = CombatEntityAccessor.getAttackerLevel(null, contributor);
            results.add(calculatePerCharacter(contributor, level, target));
        }
        Combined combined = combine(results);
        spec.setCrit(combined.crit);

        // 每个贡献者一个【】，最后再乘权重汇总
        if (DamageTrace.active()) {
            for (int i = 0; i < results.size(); i++) {
                Result result = results.get(i);
                String name = result.character == null ? "?" : result.character.getName().getString();
                trace.zone("第" + (i + 1) + "名", name + " 的单人理论伤害",
                        DamageTrace.fmt(result.theoryDamage));
            }
            trace.zone("合并区", "第1名×1 + 第2名×1/2 + 第3/4名×1/12 + 第5~8名×1/24",
                    DamageTrace.fmt(combined.totalDamage));
            trace.result(combined.totalDamage);
            trace.log();
        }
        return combined.totalDamage;
    }

    /**
     * 单个角色的理论伤害（多人合并用，也可单独调）。
     *
     * <pre>
     * 伤害 = 等级系数 × 月感电倍率 × (1 + 6×EM/(EM+2000)) × 抗性区 × 暴击区 × 擢升区
     * </pre>
     */
    static Result calculatePerCharacter(PGCharacter character, int level, LivingEntity target) {
        double levelCoefficient = DamageZones.levelCoefficient(level);
        double multiplier = ReactionConfig.LUNAR_CHARGED_MULT.get();
        float emBonus = DamageZones.emBonus(character, ElementalReactionType.LUNAR_CHARGED);
        float rawResistance = DamageZones.rawResistance(ModElements.ELECTRO.get(), target,
                AttackerResolver.resolveCharacter(target));
        DamageZones.CritRoll critRoll = DamageZones.rollCrit(character);

        float damage = (float) (levelCoefficient * multiplier * (1f + emBonus)
                * CombatMath.resistanceZone(rawResistance) * critRoll.zone() * DamageZones.elevationZone());
        return new Result(null, character, damage, critRoll.isCrit());
    }

    /** 合并权重：最高 1，第二 0.5，第三第四 1/12，之后 1/24（最多 8 人）。 */
    static Combined combine(List<Result> contributors) {
        if (contributors.isEmpty()) {
            return new Combined(0f, false);
        }
        contributors.sort(Comparator.comparingDouble(r -> -r.theoryDamage));

        float total = 0f;
        int n = contributors.size();
        if (n >= 1) total += contributors.get(0).theoryDamage;
        if (n >= 2) total += contributors.get(1).theoryDamage / 2f;
        if (n >= 3) total += contributors.get(2).theoryDamage / 12f;
        if (n >= 4) total += contributors.get(3).theoryDamage / 12f;
        for (int i = 4; i < n && i < 8; i++) {
            total += contributors.get(i).theoryDamage / 24f;
        }
        return new Combined(total, contributors.get(0).isCrit);
    }

    // ==================== 结果类型 ====================

    /** 单个角色的理论伤害。 */
    static final class Result {
        final UUID playerUUID;
        final PGCharacter character;
        final float theoryDamage;
        final boolean isCrit;

        Result(UUID playerUUID, PGCharacter character, float theoryDamage, boolean isCrit) {
            this.playerUUID = playerUUID;
            this.character = character;
            this.theoryDamage = theoryDamage;
            this.isCrit = isCrit;
        }
    }

    /** 多人合并后的结果。 */
    static final class Combined {
        final float totalDamage;
        final boolean crit;

        Combined(float totalDamage, boolean crit) {
            this.totalDamage = totalDamage;
            this.crit = crit;
        }
    }
}
