package com.linweiyun.genshin.core.system.combat.attack;

import com.linweiyun.genshin.config.reaction.ReactionConfig;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.system.combat.damage.CombatMath;
import com.linweiyun.genshin.core.system.combat.damage.DamageTrace;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.core.system.reaction.StellarGlimmerBranch;
import com.linweiyun.elementlib.api.ElementalReactionType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * <b>星烁反应管线</b> —— 星扩散（{@link StellarGlimmerBranch#SWIRL}）
 * 与星超导（{@link StellarGlimmerBranch#CONDUCE}）共用这一条。
 *
 * <h2>单人理论伤害（不含加权）</h2>
 * <pre>
 * 直伤型（技能自带的星烁伤害）：伤害 = 攻击力 × 星辉系数 × (1 + 基础倍率提升)
 * 反应型（星辉风旋那种区域伤害）：伤害 = (等级×5+500) × 星辉系数 × (1 + 基础倍率提升) + 基础附加
 * 后段（两种一样）：× 反应加成区 × 抗性区 × 暴击区 × 擢升区 × 大权区
 * </pre>
 *
 * <h2>加权：只看「是不是反应伤害」，不看人数</h2>
 * 逐人算出理论伤害后<b>加权求和</b>：{@code 第1名×0.6 + 第2名×0.3 + 第3名×0.05 + 第4名×0.05}
 * （只看前四名；第五名及以后不计入）。和月曜的权重不同。
 *
 * <p>⚠️ 这条加权公式<b>只对「反应伤害」成立</b>（{@code isStellarReactionDamage()} 为真，
 * 即 {@code ModDamageSpec.stellarReaction(...)} 造出来的、星辉风旋那种「反应分配出来的」伤害）：
 * 它<b>永远</b>走加权 —— 单人触发时就是 {@code 单人理论伤害 × 0.6}，不因为只有一个人就免掉。
 *
 * <p>反过来，<b>直伤</b>（{@code stellarDirect(...)}，技能自带的星扩散伤害，例如薇斯娜的灵剑）
 * 是某个角色自己打出的那一下，<b>不参与加权</b>，单人就是理论伤害本身。
 *
 * <p>所以这里只需要区分「直伤 / 反应伤害」，不需要另判人数。
 */
final class StellarDamage {

    private StellarDamage() {
    }

    /** 单人理论伤害的展开公式（不含加权）—— 多人时单独占一行。 */
    private static final String SINGLE_FORMULA_BY_SKILL =
            "单人理论伤害 = 【攻击力 × 星辉系数 × (1 + 基础倍率提升)】"
                    + " × 【1 + 16×元素精通/(元素精通+2000) + 星烁加成】"
                    + " × 【1 - 抗性】 × 【1 + 暴击伤害】 × 【擢升区】 × 【大权区】";

    private static final String SINGLE_FORMULA_BY_REACTION =
            "单人理论伤害 = 【(聚变等级系数 × 星辉系数 × (1 + 基础倍率提升) + 基础附加)】"
                    + " × 【1 + 16×元素精通/(元素精通+2000) + 星烁加成】"
                    + " × 【1 - 抗性】 × 【1 + 暴击伤害】 × 【擢升区】 × 【大权区】";

    private static final String COMBINE_FORMULA =
            "伤害 = 【第1名】 × 0.6 + 【第2名】 × 0.3 + 【第3名】 × 0.05 + 【第4名】 × 0.05";

    // ==================== 入口 ====================

    static float calculate(PGCharacter attacker, LivingEntity target, ModDamageSpec spec) {
        ElementalReactionType reactionType = spec.getTransformativeReactionType();
        StellarGlimmerBranch branch = StellarGlimmerBranch.of(reactionType);

        PGCharacter logAttacker = attacker != null ? attacker : spec.getAttackerCharacter();
        if (logAttacker == null && spec.getStellarContributors() != null
                && !spec.getStellarContributors().isEmpty()) {
            logAttacker = spec.getStellarContributors().get(0);
        }
        boolean tracing = DamageTrace.active();
        DamageTrace trace = tracing ? DamageTrace.start(pipelineName(spec, branch)) : DamageTrace.none();
        if (tracing) {
            trace.headAttack(spec.getAttackType(), StellarGlimmerBranch.damageElementOf(reactionType));
            trace.head("反应", reactionType);
            trace.head("反应", branch == null ? "非星烁" : branch.reactionName());
            trace.headEntities(null, target, logAttacker == null ? null : logAttacker.getName());
        }

        List<PGCharacter> contributors = spec.getStellarContributors();
        if (contributors == null || contributors.isEmpty()) {
            trace.slim("伤害 = 基础区");
            trace.zone("基础区", "0", "0");
            trace.result(0f);
            trace.log();
            return 0f;
        }

        double coefficient = spec.getStellarCoefficient();
        float baseBonusMult = spec.getStellarBaseBonusMult();
        float baseBonusFlat = spec.getStellarBaseBonusFlat();
        boolean byReaction = spec.isStellarReactionDamage();

        List<Result> results = new ArrayList<>();
        // 数值行只给日志用：不输出时连这个列表都不建（每条理论伤害的数值串是这次伤害里最贵的一段）
        List<String> valueTexts = tracing ? new ArrayList<>(contributors.size()) : null;
        for (PGCharacter contributor : contributors) {
            Result result = calculatePerCharacter(contributor, target, reactionType, coefficient,
                    baseBonusMult, baseBonusFlat, byReaction, spec.getSovereigntyBonus());
            results.add(result);
            if (valueTexts != null) {
                valueTexts.add(result.valueText);
            }
        }
        Combined combined = combine(results, byReaction);
        spec.setCrit(combined.crit);

        if (tracing) {
            // 只有「直伤 + 单人」才不加权（那是角色自己打出的那一下）；
            // 「反应伤害」无论几个人都走加权公式（单人 = ×0.6），所以日志照印那条公式。
            boolean noWeighting = results.size() == 1 && !byReaction;
            trace.slim(noWeighting
                    ? "伤害 = 单人理论伤害（直伤，不参与加权）"
                    : "伤害 = 单人理论伤害 × 权重（逐名求和）");
            trace.fullLine(byReaction ? SINGLE_FORMULA_BY_REACTION : SINGLE_FORMULA_BY_SKILL);
            if (!noWeighting) {
                trace.fullLine(COMBINE_FORMULA);
            }
            for (int i = 0; i < valueTexts.size(); i++) {
                trace.valueLine("单人" + (i + 1),
                        valueTexts.get(i) + " = " + DamageTrace.fmt(results.get(i).theoryDamage));
            }
            trace.valueLine("伤害", combineValueText(results, noWeighting));
            trace.result(combined.totalDamage);
            trace.log();
        }
        return combined.totalDamage;
    }

    /** 日志口径：反应造成的星烁伤害写「星烁-星扩散反应伤害」，技能自带直伤写「星烁·直伤」。 */
    private static String pipelineName(ModDamageSpec spec, @Nullable StellarGlimmerBranch branch) {
        if (!spec.isStellarReactionDamage()) {
            return "星烁·直伤";
        }
        String branchName = branch == StellarGlimmerBranch.CONDUCE ? "星超导" : "星扩散";
        return "星烁-" + branchName + "反应伤害";
    }

    // ==================== 单人 ====================

    /**
     * 单个角色的星烁伤害（理论值，不含加权）。
     *
     * @param byReaction {@code true} = 反应型（等级系数打底），{@code false} = 技能直伤型（攻击力打底）
     */
    static Result calculatePerCharacter(PGCharacter character, LivingEntity target,
                                        ElementalReactionType reactionType, double coefficient,
                                        float baseBonusMult, float baseBonusFlat, boolean byReaction) {
        return calculatePerCharacter(character, target, reactionType, coefficient,
                baseBonusMult, baseBonusFlat, byReaction, 0f);
    }

    private static Result calculatePerCharacter(PGCharacter character, LivingEntity target,
                                                ElementalReactionType reactionType, double coefficient,
                                                float baseBonusMult, float baseBonusFlat,
                                                boolean byReaction, float sovereigntyBonus) {
        float atk = character == null ? 0f
                : (float) character.getData().getAttributeTotalValue(ModAttributes.ATK.value());
        int level = com.linweiyun.genshin.core.system.combat.damage.CombatEntityAccessor
                .getAttackerLevel(null, character);
        double levelCoefficient = ReactionConfig.getReactionFusion(level);
        double baseBoost = byReaction
                ? levelCoefficient * coefficient * (1.0 + baseBonusMult) + baseBonusFlat
                : atk * coefficient * (1.0 + baseBonusMult) + baseBonusFlat;

        float em = character == null ? 0f
                : (float) character.getData().getAttributeTotalValue(ModAttributes.ELEMENTAL_MASTERY.value());
        float emBonus = DamageZones.emBonus(character, reactionType);
        float glimmerBonus = DamageZones.stellarGlimmerBonus(character, reactionType);
        // 擢升区按「角色 + 分支」给（例：薇斯娜满命的星扩散擢升 20%）
        StellarGlimmerBranch branch = StellarGlimmerBranch.of(reactionType);
        float rawResistance = DamageZones.rawResistance(
                StellarGlimmerBranch.damageElementOf(reactionType), target,
                AttackerResolver.resolveCharacter(target));
        float resistanceZone = CombatMath.resistanceZone(rawResistance);
        DamageZones.CritRoll critRoll = DamageZones.rollCrit(character,
                StellarGlimmerBranch.damageElementOf(reactionType), true);
        float sovereignty = 1.0f + sovereigntyBonus;

        float damage = (float) (baseBoost * (1f + emBonus + glimmerBonus)
                * resistanceZone * critRoll.zone()
                * DamageZones.elevationZone(character, branch) * sovereignty);

        // 数值行 = 把数字代进展开公式（不是把每个乘区算好的结果填进去），
        // 每一项都要和展开公式里的名字一一对应。
        //
        // 这一段每个贡献者都会拼一条两三百字的串（含十几次 fmt），只在真的会输出时才建：
        // 日志关着的时候它是这条管线里最贵的一次白干。
        String valueText = null;
        if (DamageTrace.active()) {
            float elevationBonus = DamageZones.elevationZone(character, branch) - 1.0f;
            valueText = "【" + (byReaction
                    ? DamageTrace.fmt(levelCoefficient) + " × " + DamageTrace.fmt(coefficient)
                            + " × (1 + " + DamageTrace.fmt(baseBonusMult) + ")"
                            + " + " + DamageTrace.fmt(baseBonusFlat)
                    : DamageTrace.fmt(atk) + " × " + DamageTrace.fmt(coefficient)
                            + " × (1 + " + DamageTrace.fmt(baseBonusMult) + ")"
                            + " + " + DamageTrace.fmt(baseBonusFlat)) + "】"
                    + " × 【1 + 16×" + DamageTrace.fmt(em) + "/(" + DamageTrace.fmt(em) + "+2000)"
                            + " + " + DamageTrace.fmt(glimmerBonus) + "】"
                    + " × 【1 - " + DamageTrace.fmt(rawResistance) + "】"
                    + " × 【" + (critRoll.isCrit()
                            ? "1 + " + DamageTrace.fmt(critRoll.critDamage()) : "1") + "】"
                    + " × 【1 + " + DamageTrace.fmt(elevationBonus) + "】"
                    + " × 【1 + " + DamageTrace.fmt(sovereigntyBonus) + "】";
        }

        return new Result(null, character, damage, critRoll.isCrit(), valueText);
    }

    // ==================== 合并 ====================

    /**
     * 星烁合并：{@code 第1名×0.6 + 第2名×0.3 + 第3/4名×0.05}（只看前四名，第五名及以后不计入）。
     *
     * <p>⚠️ <b>「反应伤害」永远走这条公式</b>（{@code byReaction == true}，即星辉风旋那种
     * 由反应分配出来的伤害）：单人触发时第 1 名照样 ×0.6 —— 它不是哪个角色自己的那一下。
     *
     * <p>只有<b>直伤</b>（{@code stellarDirect}，技能自带的星扩散伤害）在单人时按理论伤害本身计，
     * 不参与加权 —— 那是角色自己打出的伤害。
     */
    static Combined combine(List<Result> contributors, boolean byReaction) {
        if (contributors.isEmpty()) {
            return new Combined(0f, false, null);
        }
        contributors.sort(Comparator.comparingDouble(r -> -r.theoryDamage));

        // 单人 + 直伤：不参与加权，直接就是理论伤害
        if (contributors.size() == 1 && !byReaction) {
            Result only = contributors.get(0);
            return new Combined(only.theoryDamage, only.isCrit, only.character);
        }

        float total = 0f;
        int n = contributors.size();
        if (n >= 1) total += contributors.get(0).theoryDamage * 0.6f;
        if (n >= 2) total += contributors.get(1).theoryDamage * 0.3f;
        if (n >= 3) total += contributors.get(2).theoryDamage * 0.05f;
        if (n >= 4) total += contributors.get(3).theoryDamage * 0.05f;

        return new Combined(total, contributors.get(0).isCrit, contributors.get(0).character);
    }

    /**
     * 加权那一行的数值写法：{@code 【16.2】×0.6 + 【16.2】×0.3}；
     * 「单人 + 直伤」不参与加权时写 {@code 【16.2】 × 1.0}。
     */
    private static String combineValueText(List<Result> sorted, boolean noWeighting) {
        if (noWeighting) {
            return "【" + DamageTrace.fmt(sorted.get(0).theoryDamage) + "】 × 1.0";
        }
        String[] weights = {"0.6", "0.3", "0.05", "0.05"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sorted.size() && i < 4; i++) {
            if (i > 0) {
                sb.append(" + ");
            }
            sb.append('【').append(DamageTrace.fmt(sorted.get(i).theoryDamage)).append('】')
                    .append(" × ").append(weights[i]);
        }
        return sb.toString();
    }

    // ==================== 结果类型 ====================

    /**
     * 单个角色的理论伤害 + 它在日志里的数值写法。
     *
     * <p>{@code valueText} 只给日志用，日志关着时是 {@code null}。</p>
     */
    static final class Result {
        final UUID playerUUID;
        final PGCharacter character;
        final float theoryDamage;
        final boolean isCrit;
        final String valueText;

        Result(UUID playerUUID, PGCharacter character, float theoryDamage, boolean isCrit, String valueText) {
            this.playerUUID = playerUUID;
            this.character = character;
            this.theoryDamage = theoryDamage;
            this.isCrit = isCrit;
            this.valueText = valueText;
        }
    }

    /** 多人合并后的结果。 */
    static final class Combined {
        final float totalDamage;
        final boolean crit;
        final PGCharacter topCharacter;

        Combined(float totalDamage, boolean crit, PGCharacter topCharacter) {
            this.totalDamage = totalDamage;
            this.crit = crit;
            this.topCharacter = topCharacter;
        }
    }
}
