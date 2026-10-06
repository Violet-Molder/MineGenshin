package com.linweiyun.genshin.core.system.combat.attack;

import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.genshin.core.system.combat.damage.CombatEntityAccessor;
import com.linweiyun.genshin.core.system.combat.damage.CombatMath;
import com.linweiyun.genshin.core.system.combat.damage.DamageLabels;
import com.linweiyun.genshin.core.system.combat.damage.DamageTrace;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.core.system.reaction.StellarGlimmer;
import com.linweiyun.genshin.core.system.reaction.StellarGlimmerBranch;
import com.linweiyun.elementlib.api.ElementalReactionType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import com.linweiyun.genshin.core.system.registry.register.ModReactionTypes;

/**
 * <b>伤害乘区</b> —— 纯计算，不碰日志。
 *
 * <p>每个方法的公式就是它名字下面那一行；管线会把它们拼成
 * 「缩略公式 / 展开公式」，并按同样的参数顺序写出数值（见 {@code DamageTrace}）。
 *
 * <table border="1">
 *   <caption>乘区一览</caption>
 *   <tr><th>乘区</th><th>公式</th><th>参数顺序（数值行就是照这个顺序写的）</th></tr>
 *   <tr><td>基础伤害区</td>
 *       <td>{@code ATK×atkMult + HP×hpMult + DEF×defMult + EM×emMult + flat}</td>
 *       <td>ATK atkMult HP hpMult DEF defMult EM emMult flat</td></tr>
 *   <tr><td>倍率区</td><td>{@code 1 + 技能倍率提升}</td><td>skillBonus</td></tr>
 *   <tr><td>暴击区</td><td>{@code 暴击 ? 1 + CDG : 1}</td><td>暴击 CR CDG</td></tr>
 *   <tr><td>增伤区</td><td>{@code 1 + 元素伤害加成 + 效果加成}</td><td>元素伤害加成 效果加成</td></tr>
 *   <tr><td>防御区</td><td>{@code (攻方等级×5+500) / (攻方等级×5+500 + 守方防御)}</td><td>攻方等级 守方防御</td></tr>
 *   <tr><td>抗性区</td>
 *       <td>{@code res<0 ? 1-res/2 : (res≤0.75 ? 1-res : 1/(1+4res))}</td><td>抗性（已含减抗 / 增抗）</td></tr>
 *   <tr><td>精通加成（增幅）</td><td>{@code 2.78×EM/(EM+1400)}</td><td>EM</td></tr>
 *   <tr><td>精通加成（剧变）</td><td>{@code 16×EM/(EM+2000)}</td><td>EM</td></tr>
 *   <tr><td>精通加成（月曜）</td><td>{@code 6×EM/(EM+2000)}</td><td>EM</td></tr>
 *   <tr><td>反应加成区</td><td>{@code 1 + 精通加成 (+ 星烁加成 / 月曜专属加成)}</td>
 *       <td>EM [星烁加成 / 月曜专属加成]</td></tr>
 *   <tr><td>等级系数</td><td>{@code 等级×5+500}</td><td>等级</td></tr>
 *   <tr><td>擢升区 / 大权区</td><td>占位，恒 {@code 1}</td><td>1 / 1</td></tr>
 * </table>
 *
 * <p>谁用哪些区：
 * <ul>
 *   <li>直伤：基础 × 暴击 × 增伤 × 防御 × 抗性 ×（增幅时）反应倍率 × 反应加成 × 衰减；</li>
 *   <li>剧变：等级系数 × 反应倍率 × 反应加成 × 抗性；</li>
 *   <li>月曜：基础 × 提升 × 倍率 × 反应加成 × 抗性 × 暴击 × 擢升（无视防御、不吃增伤）；</li>
 *   <li>星烁：基础 × 反应加成 × 抗性 × 暴击 × 擢升 × 大权（无视防御、不吃增伤）。</li>
 * </ul>
 */
public final class DamageZones {

    private DamageZones() {
    }

    // ============================================================
    // 基础伤害区
    // ============================================================

    /**
     * 基础伤害区 {@code ATK×atkMult + HP×hpMult + DEF×defMult + EM×emMult + flat}。
     *
     * <p>⚠️ 这里<b>不</b>乘 {@code (1 + 倍率提升)}：那是<b>倍率区</b>，由管线单独乘一次
     * （{@code DirectDamagePipeline} 里的 {@code × (1f + baseMultiplierBonus)}）。
     * 以前两边都乘 → 实际是 {@code (1+x)²}；而且 {@link #baseZoneText} 不显示这一项，
     * 于是日志的【基础区】数字乘起来对不上末尾结果（正是文档里提醒的「结算真的错了」）。
     */
    public static float baseDamage(PGCharacter attacker, ModDamageSpec spec) {
        var data = attacker.getData();
        double atk = data.getAttributeTotalValue(ModAttributes.ATK.value());
        double hp = data.getAttributeTotalValue(ModAttributes.MAX_HP.value());
        double def = data.getAttributeTotalValue(ModAttributes.DEF.value());
        double em = data.getAttributeTotalValue(ModAttributes.ELEMENTAL_MASTERY.value());
        return (float) (atk * spec.getAtkMultiplier()
                + hp * spec.getHpMultiplier()
                + def * spec.getDefMultiplier()
                + em * spec.getEmMultiplier()
                + spec.getFlatDamageBonus());
    }

    /** 基础区的参数快照（数值行照这个顺序写）。倍率提升不在基础区，所以不在这里。 */
    public static Object[] baseDamageInputs(PGCharacter attacker, ModDamageSpec spec) {
        var data = attacker.getData();
        return new Object[]{
                data.getAttributeTotalValue(ModAttributes.ATK.value()), spec.getAtkMultiplier(),
                data.getAttributeTotalValue(ModAttributes.MAX_HP.value()), spec.getHpMultiplier(),
                data.getAttributeTotalValue(ModAttributes.DEF.value()), spec.getDefMultiplier(),
                data.getAttributeTotalValue(ModAttributes.ELEMENTAL_MASTERY.value()), spec.getEmMultiplier(),
                spec.getFlatDamageBonus()};
    }

    /**
     * 基础伤害区的<b>日志文本</b>（公式 / 数值）。
     *
     * <p>只写倍率非 0 的属性项 —— 大多数招式只有攻击力一项，
     * 把「生命值 × 0 + 防御力 × 0 + 元素精通 × 0」都写出来纯属噪音；
     * 附加伤害非 0 才写。
     */
    public static ZoneText baseZoneText(PGCharacter attacker, ModDamageSpec spec) {
        var data = attacker.getData();
        StringBuilder formula = new StringBuilder();
        StringBuilder value = new StringBuilder();

        appendTerm(formula, value, "攻击力",
                data.getAttributeTotalValue(ModAttributes.ATK.value()), spec.getAtkMultiplier(), "攻击力倍率");
        appendTerm(formula, value, "生命值",
                data.getAttributeTotalValue(ModAttributes.MAX_HP.value()), spec.getHpMultiplier(), "生命值倍率");
        appendTerm(formula, value, "防御力",
                data.getAttributeTotalValue(ModAttributes.DEF.value()), spec.getDefMultiplier(), "防御力倍率");
        appendTerm(formula, value, "元素精通",
                data.getAttributeTotalValue(ModAttributes.ELEMENTAL_MASTERY.value()),
                spec.getEmMultiplier(), "元素精通倍率");

        if (spec.getFlatDamageBonus() != 0f || formula.length() > 0) {
            if (formula.length() > 0) {
                formula.append(" + ");
                value.append(" + ");
            }
            formula.append("附加伤害");
            value.append(DamageTrace.fmt(spec.getFlatDamageBonus()));
        }
        if (formula.length() == 0) {
            // 一个属性倍率都没有 → 基础区就是附加伤害（上面那一段已经处理过 0 的情况）
            return new ZoneText("0", "0");
        }
        return new ZoneText(formula.toString(), value.toString());
    }

    private static void appendTerm(StringBuilder formula, StringBuilder value,
                                   String attributeName, double attributeValue,
                                   float multiplier, String multiplierName) {
        if (multiplier == 0f) {
            return;
        }
        if (formula.length() > 0) {
            formula.append(" + ");
            value.append(" + ");
        }
        formula.append(attributeName).append(" × ").append(multiplierName);
        value.append(DamageTrace.fmt(attributeValue)).append(" × ").append(DamageTrace.fmt(multiplier));
    }

    /**
     * 增伤区的<b>日志文本</b>：只写真正生效的加成条目。
     *
     * <p>例如「火元素普通攻击伤害」只会写 {@code 1 + 火元素伤害加成 + 普通攻击伤害加成}，
     * 没生效的（比如这个角色没有火伤加成）不写。
     */
    public static ZoneText damageBonusZoneText(PGCharacter attacker, ModDamageSpec spec) {
        float elemental = elementalBonus(attacker, spec);
        float effect = effectBonus(attacker, spec);
        float specBonus = spec.getDamageBonus();
        float zone = CombatMath.dmgBonusZone(elemental + effect + specBonus);

        // 展开公式与数值<b>逐项对应</b>：公式里写了哪几项，数值里就把哪几项的数字填进去
        //（不是直接把算好的乘区结果写上去 —— 那样就没法和公式里的名字一一对应了）
        StringBuilder formula = new StringBuilder("1");
        StringBuilder value = new StringBuilder("1");
        if (elemental != 0f) {
            formula.append(" + ").append(DamageLabels.elementBonus(spec.getElement()));
            value.append(" + ").append(DamageTrace.fmt(elemental));
        }
        if (effect != 0f) {
            formula.append(" + ").append(DamageLabels.attackTypeBonus(spec.getAttackType()));
            value.append(" + ").append(DamageTrace.fmt(effect));
        }
        if (specBonus != 0f) {
            // 按招式盖的增伤（天赋自己算好的那一档，例如「遥久之歌期间大招加一档」）
            formula.append(" + 招式增伤");
            value.append(" + ").append(DamageTrace.fmt(specBonus));
        }
        return new ZoneText(formula.toString(), value.toString());
    }

    /** 一段日志文本：公式 / 数值。 */
    public record ZoneText(String formula, String value) {
    }

    // ============================================================
    // 暴击区
    // ============================================================

    /**
     * 暴击区 {@code 骰值<CR ? 1 + CDG : 1} —— <b>并把结果写回 {@code spec.setCrit(...)}</b>
     * （伤害数字的暴击样式要用它）。
     */
    public static float crit(PGCharacter attacker, ModDamageSpec spec) {
        // ⚠️ 必须走「带 spec」的判定：不带 spec 的那一份会丢掉
        // PGCharacter#getCritDamageBonus(元素, 是否星烁) 给的那档额外暴击伤害。
        CritRoll roll = rollCrit(attacker, spec);
        if (spec != null) {
            spec.setCrit(roll.isCrit());
        }
        return roll.zone();
    }

    /** 暴击区（不写回 spec）—— 剧变/星烁这类不属于某一条 spec 的结算用它。 */
    public static float critNoSpec(PGCharacter attacker) {
        return rollCrit(attacker).zone();
    }

    /**
     * 一次暴击判定。
     *
     * <p>一条伤害<b>只掷一次</b>：数值行写 {@code 骰值 CR CDG}，和展开公式
     * {@code (骰值<CR ? 1+CDG : 1)} 里的参数一一对应。
     */
    public record CritRoll(double roll, boolean isCrit, float critRate, float critDamage) {
        /** 暴击区：暴击则 1+CDG，否则 1。 */
        public float zone() {
            return isCrit ? 1f + critDamage : 1f;
        }
    }

    public static CritRoll rollCrit(PGCharacter attacker) {
        float critRate = critRate(attacker);
        float critDamage = critDamage(attacker);
        double roll = Math.random();
        return new CritRoll(roll, attacker != null && roll < critRate, critRate, critDamage);
    }

    /**
     * 带 spec 的暴击判定：暴击伤害 = 统一 CDG + <b>按元素/反应类型的额外暴击伤害</b>。
     *
     * <p>后者走 {@link PGCharacter#getCritDamageBonus(GenshinElement, boolean)} ——
     * 例如沃雅妮莎「黑与白的双音」只加<b>水/冰伤害</b>或<b>星扩散反应伤害</b>的暴击伤害，
     * 统一 CDG 表达不了这种口径。
     */
    public static CritRoll rollCrit(PGCharacter attacker, ModDamageSpec spec) {
        if (spec == null) return rollCrit(attacker);
        return rollCrit(attacker, spec.getElement(), spec.isStellarReactionDamage());
    }

    /** 直接给「元素 + 是否星烁反应」的版本（星烁管线手里没有 spec，只有 reactionType）。 */
    public static CritRoll rollCrit(PGCharacter attacker, GenshinElement element, boolean stellarReaction) {
        float critRate = critRate(attacker);
        float critDamage = critDamage(attacker);
        if (attacker != null && element != null) {
            critDamage += attacker.getCritDamageBonus(element, stellarReaction);
        }
        double roll = Math.random();
        return new CritRoll(roll, attacker != null && roll < critRate, critRate, critDamage);
    }

    public static float critRate(PGCharacter attacker) {
        return attacker == null ? 0f
                : (float) attacker.getData().getAttributeTotalValue(ModAttributes.CR.value());
    }

    public static float critDamage(PGCharacter attacker) {
        return attacker == null ? 0f
                : (float) attacker.getData().getAttributeTotalValue(ModAttributes.CDG.value());
    }

    // ============================================================
    // 增伤区
    // ============================================================

    /** {@code 1 + 元素伤害加成 + 效果加成 + 招式增伤} */
    public static float damageBonus(PGCharacter attacker, ModDamageSpec spec) {
        return CombatMath.dmgBonusZone(elementalBonus(attacker, spec) + effectBonus(attacker, spec)
                + spec.getDamageBonus());
    }

    public static float elementalBonus(PGCharacter attacker, ModDamageSpec spec) {
        return CombatEntityAccessor.getDamageBonus(attacker, spec.getElement());
    }

    public static float effectBonus(PGCharacter attacker, ModDamageSpec spec) {
        return attacker.getData().getEffectContainer()
                .getTotalDamageBonus(spec.getAttackType(), spec.getElement());
    }

    // ============================================================
    // 防御区
    // ============================================================

    /** {@code (攻方等级×5+500) / (攻方等级×5+500 + 守方防御)} */
    public static float defense(LivingEntity attacker, PGCharacter attackerCharacter,
                                LivingEntity defender, PGCharacter defenderCharacter) {
        return CombatMath.defenseZone(attackerLevel(attacker, attackerCharacter),
                defenderDefense(defender, defenderCharacter));
    }

    public static int attackerLevel(LivingEntity attacker, PGCharacter attackerCharacter) {
        return CombatEntityAccessor.getAttackerLevel(attacker, attackerCharacter);
    }

    public static double defenderDefense(LivingEntity defender, PGCharacter defenderCharacter) {
        return CombatEntityAccessor.getDefenderDefense(defender, defenderCharacter);
    }

    public static int defenderLevel(LivingEntity defender, PGCharacter defenderCharacter) {
        return CombatEntityAccessor.getDefenderLevel(defender, defenderCharacter);
    }

    // ============================================================
    // 抗性区
    // ============================================================

    /** {@code res<0 ? 1-res/2 : (res≤0.75 ? 1-res : 1/(1+4res))} */
    public static float resistance(GenshinElement element, LivingEntity defender,
                                   PGCharacter defenderCharacter) {
        return CombatMath.resistanceZone(
                rawResistance(element, defender, defenderCharacter));
    }

    public static float rawResistance(GenshinElement element, LivingEntity defender,
                                      PGCharacter defenderCharacter) {
        return CombatEntityAccessor.getDefenderResistance(defender, defenderCharacter, element);
    }

    // ============================================================
    // 元素精通 / 反应加成区
    // ============================================================

    /**
     * 精通加成：增幅 {@code 2.78×EM/(EM+1400)}；剧变 {@code 16×EM/(EM+2000)}；月曜 {@code 6×EM/(EM+2000)}。
     *
     * <p>⚠️ <b>星烁（星扩散 / 星超导）走的是剧变那一档</b>：它的反应加成区就是
     * {@code 1 + 16×EM/(EM+2000) + 星烁加成}（见 {@code StellarDamage} 的公式与数值行）。
     * 原来这个 switch <b>没有</b>星烁的四个枚举值 → 落到 {@code default -> 0f}，
     * 星烁反应完全吃不到元素精通，而日志却照着自己的公式把 {@code 16×EM/(EM+2000)} 印出来 ——
     * 公式和结果对不上（EM 200 时实际少了约 2.45 倍，EM 500 时约 4.2 倍）。
     */
    public static float emBonus(PGCharacter attacker, ElementalReactionType reactionType) {
        double em = elementalMastery(attacker);
        if (reactionType == null) {
            return 0f;
        }
        if (ModReactionTypes.is(reactionType, ModReactionTypes.MELT)
                || ModReactionTypes.is(reactionType, ModReactionTypes.VAPORIZE)) {
            return (float) ((2.78 * em) / (em + 1400.0));
        }
        if (ModReactionTypes.is(reactionType, ModReactionTypes.LUNAR_CHARGED)
                || ModReactionTypes.is(reactionType, ModReactionTypes.LUNAR_BLOOM)
                || ModReactionTypes.is(reactionType, ModReactionTypes.LUNAR_CRYSTALLIZE)) {
            return (float) ((6.0 * em) / (em + 2000.0));
        }
        if (ModReactionTypes.is(reactionType, ModReactionTypes.OVERLOAD)
                || ModReactionTypes.is(reactionType, ModReactionTypes.SUPERCONDUCT)
                || ModReactionTypes.is(reactionType, ModReactionTypes.ELECTRO_CHARGED)
                || ModReactionTypes.is(reactionType, ModReactionTypes.SWIRL)
                || ModReactionTypes.is(reactionType, ModReactionTypes.BURNING)
                || ModReactionTypes.is(reactionType, ModReactionTypes.BLOOM)
                || ModReactionTypes.is(reactionType, ModReactionTypes.HYPERBLOOM)
                || ModReactionTypes.is(reactionType, ModReactionTypes.BURGEON)
                || ModReactionTypes.is(reactionType, ModReactionTypes.STELLAR_SWIRL_WIND)
                || ModReactionTypes.is(reactionType, ModReactionTypes.STELLAR_SWIRL_ICE)
                || ModReactionTypes.is(reactionType, ModReactionTypes.STELLAR_CONDUCE_ELECTRO)
                || ModReactionTypes.is(reactionType, ModReactionTypes.STELLAR_CONDUCE_ICE)) {
            return (float) ((16.0 * em) / (em + 2000.0));
        }
        return 0f;
    }

    public static double elementalMastery(PGCharacter attacker) {
        return attacker == null ? 0.0
                : attacker.getData().getAttributeTotalValue(ModAttributes.ELEMENTAL_MASTERY.value());
    }

    /** 反应加成区（增幅）= {@code 1 + 2.78×EM/(EM+1400)} */
    public static float amplifyingReactionBonus(PGCharacter attacker, ElementalReactionType reactionType) {
        return 1.0f + emBonus(attacker, reactionType);
    }

    /** 反应加成区（月曜）= {@code 1 + 6×EM/(EM+2000) + 月曜专属加成}（后者目前恒 0）。 */
    public static float lunarReactionBonus(PGCharacter attacker, ElementalReactionType reactionType) {
        return 1.0f + emBonus(attacker, reactionType) + LUNAR_SPECIAL_BONUS;
    }

    /** 月曜专属加成（占位）。 */
    public static final float LUNAR_SPECIAL_BONUS = 0f;

    /**
     * 反应加成区（星烁）= {@code 1 + 16×EM/(EM+2000) + 星烁加成}。
     *
     * <p>星烁加成按<b>分支</b>取（见 {@link StellarGlimmerBranch}）：
     * 写「星烁加成」的效果两个分支都算，只写星扩散的（例如薇斯娜天赋）只算星扩散。
     */
    public static float stellarGlimmerReactionBonus(PGCharacter attacker, ElementalReactionType reactionType) {
        return 1.0f + emBonus(attacker, reactionType) + stellarGlimmerBonus(attacker, reactionType);
    }

    /** 星烁反应伤害加成（按分支）。 */
    public static float stellarGlimmerBonus(PGCharacter attacker, ElementalReactionType reactionType) {
        return StellarGlimmer.bonusOf(attacker, StellarGlimmerBranch.of(reactionType));
    }

    // ============================================================
    // 等级系数 / 擢升 / 大权
    // ============================================================

    /** 原神等级系数（反应融合系数）：{@code 等级×5+500} */
    public static double levelCoefficient(int level) {
        return CombatMath.levelCoefficient(level);
    }

    /**
     * 大权区 = {@code 1 + 大权加成}。
     *
     * <p>加成来自 {@code ModDamageSpec#getSovereigntyBonus()} —— <b>按招式显式打开</b>，
     * 例如薇斯娜的「整肃」只作用在灵剑那几段上。没打开的招式恒为 1。
     */
    public static float sovereigntyZone(ModDamageSpec spec) {
        return 1.0f + (spec == null ? 0f : spec.getSovereigntyBonus());
    }

    /**
     * 擢升区 = {@code 1 + 擢升加成}。
     *
     * <p>加成来自 {@link PGCharacter#getElevationBonus(StellarGlimmerBranch)} ——
     * <b>按角色、按分支</b>给（例：薇斯娜满命的「星扩散反应伤害擢升 20%」）。
     *
     * <p>和「反应加成区」的区别：反应加成区里那份星烁加成是<b>和元素精通加算</b>的
     * （{@code 1 + EM + 星烁加成}），而擢升区是<b>独立乘区</b>，位于暴击之后、大权之前。
     */
    public static float elevationZone(@Nullable PGCharacter attacker,
                                      @Nullable StellarGlimmerBranch branch) {
        if (attacker == null || branch == null) {
            return 1.0f;
        }
        return 1.0f + attacker.getElevationBonus(branch);
    }

    /** 没有角色信息时的擢升区（恒 1，兼容月曜那种还不知道施法者的场合）。 */
    public static float elevationZone() {
        return 1.0f;
    }
}
