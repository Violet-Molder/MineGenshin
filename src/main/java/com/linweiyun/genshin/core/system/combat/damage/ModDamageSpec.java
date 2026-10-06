package com.linweiyun.genshin.core.system.combat.damage;

import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.elementlib.core.system.combat.decay.DecayGroup;
import com.linweiyun.elementlib.core.system.combat.decay.DecayGroups;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.elementlib.api.ElementalReactionType;
import com.linweiyun.genshin.core.system.poise.HitImpact;
import com.linweiyun.genshin.core.system.poise.impact.ImpactLevel;
import com.linweiyun.genshin.core.system.poise.WeaponPoiseTable;

import javax.annotation.Nullable;
import java.util.List;
import com.linweiyun.genshin.core.system.registry.register.ModReactionTypes;

/**
 * 伤害规格 —— 定义一次攻击的完整参数规格
 *
 * 核心设计：攻击衰减系统
 * 每次攻击都携带衰减标签和（可选的）衰减组别：
 * - 衰减标签（来自AttackType）：决定哪些攻击共用同一套计时计数器
 * - 衰减组别（DecayGroup）：决定三个序列（元素量、伤害、削韧）和清除时间
 *
 * 附着机制：
 * - 每次攻击都施加附着，实际附着量 = 基础元素量 × 元素量序列系数
 * - 系数为0 = 无附着，系数为1 = 全额附着
 *
 * 衰减组别的指定方式：
 * - 方式1：不指定（decayGroup=null）→ 战斗系统根据攻击者+角色+攻击类型查找对应组别
 * - 方式2：直接指定 → 使用指定的组别（用于特殊攻击覆盖默认行为）
 *
 * 计时计数器位置：
 * - 存储在目标实体身上（不是攻击者身上）
 * - 按 (目标, 攻击者, 角色, 标签) 独立计算
 * - 第一次命中时创建，清除时间到期后销毁
 *
 * 数据流向：
 *   技能/效果 → 创建 DamageSpec → 嵌入 ModDamageSource
 *     → ModDamageSource 进入 MC 伤害管线
 *     → 战斗事件拦截 → 从 DamageSource 取出 DamageSpec → 读取全部数据
 *     → 战斗系统解析衰减组别（使用规格指定的或查找默认的）
 *     → 查询目标身上的计时计数器，获取当前命中次数
 *     → 从三个序列中读取对应系数
 *     → 实际元素量 = 基础元素量 × 元素量系数
 *     → 实际伤害 = 计算伤害 × 伤害系数
 *     → 实际削韧 = 基础削韧 × 削韧系数
 */
public class ModDamageSpec {

    // ========== 核心伤害数据 ==========
    private final AttackType attackType;                 // 攻击类型 —— 决定伤害分类和衰减标签
    private final GenshinElement element;                 // 伤害元素 —— 决定伤害的元素属性

    // ========== 基础伤害区 - 倍率 ==========
    private final float atkMultiplier;                   // 攻击力倍率（技能面板描述的那个）
    private final float hpMultiplier;                    // 生命值倍率
    private final float defMultiplier;                   // 防御力倍率
    private final float emMultiplier;                    // 元素精通倍率
    private final float skillMultiplierBonus;            // 技能倍率提升（行秋4命、深渊buff等，不是技能面板倍率）
    private final float flatDamageBonus;                 // 基础伤害附加（申鹤冰凌等）


    // ========== 元素附着数据 ==========
    private final float elementAmount;                   // 基础元素量 —— 本次攻击的原始元素量

    // ========== 衰减系统 ==========
    private final DecayGroup decayGroup;                // 衰减组别 —— 决定三个序列（元素量、伤害、削韧）和清除时间

    // ========== 攻击者角色信息 ==========
    private final PGCharacter attackerCharacter;       // 攻击者的PGCharacter —— 非玩家攻击者时为null

    // ========== 剧变反应标记 ==========
    private final ElementalReactionType transformativeReactionType;

    // ========== 月曜/星烁 附加乘区 ==========
    private final float lunarBaseBonus;                  // 月反应基础伤害倍率加成 (角色天赋提供, 如基于生命值)
    private final float lunarBaseFlat;                   // 月反应基础伤害附加

    // ========== 月感电多角色贡献者 ==========
    private List<PGCharacter> lunarContributors;         // 参与月感电反应的角色列表（雷暴云伤害用）

    // ========== 星扩散 附加乘区 ==========
    private final float stellarCoefficient;              // 星辉基础系数 (风:0.75, 冰:2.0/3.0)
    private final float stellarBaseBonusMult;            // 星扩散基础伤害倍率加成
    private final float stellarBaseBonusFlat;            // 星扩散基础伤害附加
    private final boolean stellarReactionDamage;         // 是不是「反应造成的」星烁伤害（只影响日志口径）
    private List<PGCharacter> stellarContributors;       // 参与星扩散反应的角色列表（星辉风旋伤害用）

    // ========== 伤害类型 - 决定走哪条计算管线 ==========
    public enum DamageType {
        DIRECT,
        TRANSFORMATIVE,
        QUICKEN,
        LUNAR,
        STELLAR
    }

    private final DamageType damageType;

    // ========== 运行时计算结果 ==========
    private boolean crit;                               // 本次伤害是否暴击（计算时由战斗系统设置）

    /**
     * 大权区加成（{@code 大权区 = 1 + 这个值}）。
     *
     * <p><b>按招式显式打开</b>，不是全局属性：例如薇斯娜的「整肃」只作用在灵剑那几段上
     * （翔风剑二阶第二段、三阶两段、大招），她那几段以外的伤害不该吃。
     * 天赋造伤害时调 {@link #withSovereignty(float)} 把当前层数盖上来。
     */
    private float sovereigntyBonus = 0f;

    /** 队伍级星扩散基础倍率提升（运行时覆盖，见 {@link #withStellarBaseBonusMult(float)}）。 */
    private float stellarBaseBonusMultValue = Float.NaN;

    /** 给这一条伤害打开大权区（值 = 角色当前的大权加成，0.6 = +60%）。 */
    public ModDamageSpec withSovereignty(float bonus) {
        this.sovereigntyBonus = Math.max(0f, bonus);
        return this;
    }

    public float getSovereigntyBonus() {
        return sovereigntyBonus;
    }

    /**
     * 增伤区加成（{@code 增伤区 = 1 + 元素伤害加成 + 效果加成 + 这个值}）。
     *
     * <p>和 {@link #sovereigntyBonus} 一样是<b>按招式</b>盖上去的：天赋里算好的
     * 「这一招额外多少增伤」调 {@link #withDamageBonus(float)} 覆盖。
     *
     * <p>⚠️ 别和 {@code skillMultiplierBonus} 弄混：那个是<b>倍率区</b>
     * （{@code 1 + 倍率提升}），这个才是「伤害提升 X%」的增伤区。
     * 沃雅妮莎「遥久之歌期间大招额外一档」就是落在这一区。
     */
    private float damageBonus = 0f;

    public ModDamageSpec withDamageBonus(float bonus) {
        this.damageBonus = bonus;
        return this;
    }

    public float getDamageBonus() {
        return damageBonus;
    }

    /**
     * 这一条伤害的<b>削韧值</b>（poise damage）。
     *
     * <p>{@code NaN} = 没显式设过，取 {@link #defaultPoise(AttackType)} 的默认值。
     * 护盾结算（{@code ShieldService#absorbDamage}）用它决定「这一下削掉多少韧性」，
     * 也是 {@code ShieldBreakType.POISE}（只靠削韧破盾）的依据。
     */
    private float poiseDamage = Float.NaN;

    /**
     * 这一下的削韧系数 —— 来自招式数据里的 {@code Hit.poise}。
     *
     * <p>由 {@link Builder#build()} 从 {@code HitPoise.current()} 自动读走，
     * 所以角色技能<b>一行都不用改</b>：它在「这一招的第几个伤害点」里建的伤害规格，
     * 自然就带着那个伤害点的系数。
     */
    private float hitPoiseCoefficient = 1.0f;

    /**
     * 这一下的<b>冲击类型</b> —— 来自招式数据里的 {@code Hit.impact}。
     *
     * <p>由 {@link Builder#build()} 从 {@link HitImpact#current()} 自动读走；
     * 破韧那一刻 {@code PoiseService.onBreak} 会把它交给冲击解算器。
     *
     * <p><b>{@code null} 是正常取值</b>：意思是「招式没写」，具体是哪一档由
     * {@link #getHitImpact(PGCharacter)} 按攻击者的武器类型查
     * {@link WeaponPoiseTable}，查不到再退回 {@link #defaultImpact(AttackType)}。
     */
    private ImpactLevel hitImpact = HitImpact.DEFAULT_IMPACT;

    /**
     * 按攻击类型查表得到的<b>基准削韧</b>。
     *
     * <h2>刻度</h2>
     * 这一张表走的是<b>文献口径</b>（削韧与韧性条同一套刻度，见方案 §5 的方案 C）：
     * 普攻 10 点、战技 15 点、大招 25 点，而护盾那边用一个显式折算
     * （{@code ShieldService.POISE_TO_SHIELD}）把两套刻度解耦 ——
     * 所以换刻度不会波及护盾的既有数值，换的时候也只改这一张表。
     *
     * <h2>具体值还是占位</h2>
     * 用户已裁定「韧性表暂时不写」，所以这里是按攻击类型给的<b>统一占位值</b>，
     * 不区分角色。以后要按角色逐招细分（文献的逐角色削韧表），
     * 用 {@link #withPoiseDamage(float)} 在招式里覆盖，或者把表接进角色配置。
     *
     * <p>反应类伤害（剧变 / 月曜 / 星烁 / 扩散）在这里是 0：
     * 它们的削韧按「反应」那张表走（超载 90、扩散 130…），属于后续批次，
     * 不能拿「攻击类型」这张表去套。
     */
    public static float defaultPoise(AttackType attackType) {
        return switch (attackType) {
            case NORMAL_ATTACK -> 10f;
            case CHARGED_ATTACK -> 15f;
            case PLUNGING_ATTACK -> 20f;
            case ELEMENTAL_SKILL -> 15f;
            case ELEMENTAL_BURST -> 25f;
            // 怪物的一次普通攻击 —— 玩家就是靠它被打到破韧、动作被中断的
            case MONSTER -> 10f;
            default -> 0f;
        };
    }

    /**
     * 实际削韧值 = 基准削韧 × 这一下的系数（{@code Hit.poise}）。
     *
     * <p>基准的取法分三层，<b>顺序固定</b>：
     * <ol>
     *   <li>招式自己写的 {@link #withPoiseDamage(float)}（最优先，角色逐招微调用它）；</li>
     *   <li>{@link WeaponPoiseTable}：普攻 / 重击 / 下坠由<b>武器类型</b>决定；</li>
     *   <li>{@link #defaultPoise(AttackType)}：战技 / 爆发 / 怪物这类武器表管不着的兜底占位值。</li>
     * </ol>
     */
    public float getPoiseDamage() {
        return getPoiseDamage(null);
    }

    /**
     * 实际削韧值，并允许按<b>攻击者的武器类型</b>取基准。
     *
     * <p>和 {@link #getPoiseDamage()} 的差别只有一处：没有显式配过基准时，
     * 先问 {@link WeaponPoiseTable}，拿不到才落回攻击类型表。
     */
    public float getPoiseDamage(@Nullable PGCharacter attacker) {
        float base;
        if (!Float.isNaN(this.poiseDamage)) {
            base = this.poiseDamage;
        } else {
            float weaponBase = WeaponPoiseTable.basePoise(
                    WeaponPoiseTable.weaponOf(attacker), this.attackType);
            base = Float.isNaN(weaponBase) ? defaultPoise(this.attackType) : weaponBase;
        }
        return base * this.hitPoiseCoefficient;
    }

    /**
     * 没显式写过冲击时，<b>按攻击类型</b>给的兜底档。
     *
     * <p>这张表只服务于「武器表管不着」的场合（怪物攻击、环境伤害）；
     * 角色的普攻 / 重击 / 下坠一律先查 {@link WeaponPoiseTable}。
     *
     * <p>取值理由：<b>凡是攻击都必须能打断</b>（≥ 轻击 = 2），这是「破韧后攻击就能打断动作」
     * 那条规则的另外一面；只有完全不产生冲击的来源（{@code SPECIAL}、反应类）才是无影响。
     */
    public static ImpactLevel defaultImpact(AttackType attackType) {
        if (attackType == null) {
            return ImpactLevel.NONE;
        }
        return switch (attackType) {
            case NORMAL_ATTACK, ELEMENTAL_SKILL, MONSTER -> ImpactLevel.LIGHT;
            case CHARGED_ATTACK, ELEMENTAL_BURST -> ImpactLevel.KNOCKBACK_WEAK;
            case PLUNGING_ATTACK -> ImpactLevel.KNOCKBACK;
            // 特殊/环境伤害与三种反应伤害：它们的冲击由反应表 / 调用方自己给
            default -> ImpactLevel.NONE;
        };
    }

    /** 这一下的冲击类型（破韧瞬间用）；{@code null} = 招式没写，见 {@link #getHitImpact(PGCharacter)}。 */
    public ImpactLevel getHitImpact() {
        return hitImpact;
    }

    /**
     * 这一下的冲击类型，允许按攻击者的武器类型补默认值。
     *
     * <p>顺序：招式显式写的 → 武器表 → 攻击类型兜底表。
     * 永远不会返回 {@code null}，破韧冲击的调用方可以放心直接用。
     */
    public ImpactLevel getHitImpact(@Nullable PGCharacter attacker) {
        if (this.hitImpact != null) {
            return this.hitImpact;
        }
        ImpactLevel fromWeapon = WeaponPoiseTable.baseImpact(
                WeaponPoiseTable.weaponOf(attacker), this.attackType);
        return fromWeapon != null ? fromWeapon : defaultImpact(this.attackType);
    }

    /** 显式指定这一下的冲击类型；null 视为无影响，不会被当成默认微颤。 */
    public ModDamageSpec withHitImpact(ImpactLevel impact) {
        this.hitImpact = impact != null ? impact : ImpactLevel.NONE;
        return this;
    }

    /** 显式指定这一招的削韧值（负数夹到 0）。 */
    public ModDamageSpec withPoiseDamage(float value) {
        this.poiseDamage = Math.max(0f, value);
        return this;
    }

    /**
     * 覆盖星扩散的<b>基础倍率提升</b>（基础区上的 {@code × (1 + 基础倍率提升)}）。
     *
     * <p>队伍级的星扩散基础伤害提升（例如薇斯娜按攻击力给的那一档）在造伤害时算好盖上来。
     */
    public ModDamageSpec withStellarBaseBonusMult(float bonus) {
        this.stellarBaseBonusMultValue = Math.max(0f, bonus);
        return this;
    }

    // ========== 构造函数 ==========
    private ModDamageSpec(AttackType attackType, GenshinElement element,
                          float atkMultiplier, float hpMultiplier, float defMultiplier, float emMultiplier,
                          float skillMultiplierBonus, float flatDamageBonus,
                          float elementAmount, DecayGroup decayGroup,
                          PGCharacter attackerCharacter) {
        this(attackType, element,
                atkMultiplier, hpMultiplier, defMultiplier, emMultiplier,
                skillMultiplierBonus, flatDamageBonus,
                elementAmount, decayGroup,
                attackerCharacter,
                DamageType.DIRECT, null, 0f, 0f, 0f, 0f, 0f, false);
    }

    private ModDamageSpec(AttackType attackType, GenshinElement element,
                          float atkMultiplier, float hpMultiplier, float defMultiplier, float emMultiplier,
                          float skillMultiplierBonus, float flatDamageBonus,
                          float elementAmount, DecayGroup decayGroup,
                          PGCharacter attackerCharacter,
                          DamageType damageType,
                          ElementalReactionType reactionType) {
        this(attackType, element,
                atkMultiplier, hpMultiplier, defMultiplier, emMultiplier,
                skillMultiplierBonus, flatDamageBonus,
                elementAmount, decayGroup,
                attackerCharacter, damageType, reactionType, 0f, 0f, 0f, 0f, 0f, false);
    }

    private ModDamageSpec(AttackType attackType, GenshinElement element,
                          float atkMultiplier, float hpMultiplier, float defMultiplier, float emMultiplier,
                          float skillMultiplierBonus, float flatDamageBonus,
                          float elementAmount, DecayGroup decayGroup,
                          PGCharacter attackerCharacter,
                          DamageType damageType,
                          ElementalReactionType reactionType,
                          float lunarBaseBonus, float lunarBaseFlat,
                          float stellarCoefficient, float stellarBaseBonusMult, float stellarBaseBonusFlat,
                          boolean stellarReactionDamage) {
        this.attackType = attackType;
        this.element = element;
        this.atkMultiplier = atkMultiplier;
        this.hpMultiplier = hpMultiplier;
        this.defMultiplier = defMultiplier;
        this.emMultiplier = emMultiplier;
        this.skillMultiplierBonus = skillMultiplierBonus;
        this.flatDamageBonus = flatDamageBonus;
        this.elementAmount = elementAmount;
        this.decayGroup = decayGroup;
        this.attackerCharacter = attackerCharacter;
        this.damageType = damageType;
        this.transformativeReactionType = reactionType;
        this.lunarBaseBonus = lunarBaseBonus;
        this.lunarBaseFlat = lunarBaseFlat;
        this.stellarCoefficient = stellarCoefficient;
        this.stellarBaseBonusMult = stellarBaseBonusMult;
        this.stellarBaseBonusFlat = stellarBaseBonusFlat;
        this.stellarReactionDamage = stellarReactionDamage;
    }

    public static ModDamageSpec transformative(ElementalReactionType reactionType, GenshinElement element) {
        return new ModDamageSpec(
                AttackType.SPECIAL, element,
                0f, 0f, 0f, 0f,
                0f, 0f,
                0f, null,
                null,
                DamageType.TRANSFORMATIVE, reactionType, 0f, 0f, 0f, 0f, 0f, false);
    }

    public static ModDamageSpec transformative(ElementalReactionType reactionType,
                                                GenshinElement element, AttackType attackType) {
        return new ModDamageSpec(
                attackType, element,
                0f, 0f, 0f, 0f,
                0f, 0f,
                0f, null,
                null,
                DamageType.TRANSFORMATIVE, reactionType, 0f, 0f, 0f, 0f, 0f, false);
    }

    public static ModDamageSpec lunar(ElementalReactionType reactionType) {
        return new ModDamageSpec(
                AttackType.LUNAR_CHARGED, ModElements.ELECTRO.get(),
                0f, 0f, 0f, 0f,
                0f, 0f,
                0f, null,
                null,
                DamageType.LUNAR, reactionType, 0f, 0f, 0f, 0f, 0f, false);
    }

    public static ModDamageSpec lunarDirect(float atkMultiplier, float elementAmount) {
        return new ModDamageSpec(
                AttackType.LUNAR_CHARGED, ModElements.ELECTRO.get(),
                atkMultiplier, 0f, 0f, 0f,
                0f, 0f,
                elementAmount, null,
                null,
                DamageType.LUNAR, ModReactionTypes.LUNAR_CHARGED.get(), 0f, 0f, 0f, 0f, 0f, false);
    }

    public static ModDamageSpec lunarDirectHp(float hpMultiplier) {
        return new ModDamageSpec(
                AttackType.LUNAR_CHARGED, ModElements.ELECTRO.get(),
                0f, hpMultiplier, 0f, 0f,
                0f, 0f,
                0f, null,
                null,
                DamageType.LUNAR, ModReactionTypes.LUNAR_CHARGED.get(), 0f, 0f, 0f, 0f, 0f, false);
    }

    /**
     * 星烁反应伤害（星辉风旋那种「谁打的一起算」的区域伤害）。
     *
     * <p>和 {@link #stellarDirect} 的区别只在语义/日志：这是<b>反应造成的</b>伤害，
     * 不是某个角色技能自带的星烁直伤。
     */
    public static ModDamageSpec stellarReaction(ElementalReactionType reactionType, GenshinElement element,
                                                float stellarCoefficient, float baseBonusMult,
                                                float baseBonusFlat, List<PGCharacter> contributors) {
        ModDamageSpec spec = new ModDamageSpec(
                AttackType.STELLAR_SWIRL, element,
                1.0f, 0f, 0f, 0f,
                0f, 0f,
                0f, null,
                null,
                DamageType.STELLAR, reactionType, 0f, 0f,
                stellarCoefficient, baseBonusMult, baseBonusFlat, true);
        spec.setStellarContributors(contributors);
        return spec;
    }

    public static ModDamageSpec stellar(ElementalReactionType reactionType, GenshinElement element) {
        return new ModDamageSpec(
                AttackType.STELLAR_SWIRL, element,
                0f, 0f, 0f, 0f,
                0f, 0f,
                0f, null,
                null,
                DamageType.STELLAR, reactionType, 0f, 0f, 0f, 0f, 0f, false);
    }

    public static ModDamageSpec stellarDirect(ElementalReactionType reactionType,
                                               GenshinElement element, float elementAmount) {
        return stellarDirect(reactionType, element, elementAmount, 1.0f);
    }

    /**
     * @param stellarCoefficient 攻击倍率（直伤=攻击力×倍率）
     */
    public static ModDamageSpec stellarDirect(ElementalReactionType reactionType,
                                               GenshinElement element, float elementAmount,
                                               float stellarCoefficient) {
        return new ModDamageSpec(
                AttackType.STELLAR_SWIRL, element,
                1.0f, 0f, 0f, 0f,
                0f, 0f,
                elementAmount, null,
                null,
                DamageType.STELLAR, reactionType, 0f, 0f, stellarCoefficient, 0f, 0f, false);
    }

    /** 是不是「星烁反应造成的」伤害（用于日志区分反应伤害 / 技能直伤）。 */
    public boolean isStellarReactionDamage() {
        return stellarReactionDamage;
    }

    public DamageType getDamageType() {
        return damageType;
    }

    public boolean isTransformative() {
        return damageType == DamageType.TRANSFORMATIVE;
    }

    public boolean isLunar() {
        return damageType == DamageType.LUNAR;
    }

    public boolean isStellar() {
        return damageType == DamageType.STELLAR;
    }

    public ElementalReactionType getTransformativeReactionType() {
        return transformativeReactionType;
    }

    public float getLunarBaseBonus() { return lunarBaseBonus; }
    public float getLunarBaseFlat() { return lunarBaseFlat; }

    public List<PGCharacter> getLunarContributors() { return lunarContributors; }
    public void setLunarContributors(List<PGCharacter> contributors) { this.lunarContributors = contributors; }

    public float getStellarCoefficient() { return stellarCoefficient; }
    public float getStellarBaseBonusMult() {
        return Float.isNaN(stellarBaseBonusMultValue) ? stellarBaseBonusMult : stellarBaseBonusMultValue;
    }
    public float getStellarBaseBonusFlat() { return stellarBaseBonusFlat; }
    public List<PGCharacter> getStellarContributors() { return stellarContributors; }
    public void setStellarContributors(List<PGCharacter> contributors) { this.stellarContributors = contributors; }

    public ModDamageSpec withFlatDamageBonus(float newFlatBonus) {
        ModDamageSpec copy = new ModDamageSpec(
                this.attackType, this.element,
                this.atkMultiplier, this.hpMultiplier, this.defMultiplier, this.emMultiplier,
                this.skillMultiplierBonus, newFlatBonus,
                this.elementAmount, this.decayGroup,
                this.attackerCharacter, this.damageType, this.transformativeReactionType,
                this.lunarBaseBonus, this.lunarBaseFlat,
                this.stellarCoefficient, this.stellarBaseBonusMult, this.stellarBaseBonusFlat,
                this.stellarReactionDamage
        );
        copy.hitPoiseCoefficient = this.hitPoiseCoefficient;
        copy.hitImpact = this.hitImpact;
        return copy;
    }

    public ModDamageSpec withAttackerCharacter(PGCharacter character) {
        ModDamageSpec copy = new ModDamageSpec(
                this.attackType, this.element,
                this.atkMultiplier, this.hpMultiplier, this.defMultiplier, this.emMultiplier,
                this.skillMultiplierBonus, this.flatDamageBonus,
                this.elementAmount, this.decayGroup,
                character, this.damageType, this.transformativeReactionType,
                this.lunarBaseBonus, this.lunarBaseFlat,
                this.stellarCoefficient, this.stellarBaseBonusMult, this.stellarBaseBonusFlat,
                this.stellarReactionDamage
        );
        // 这些字段不在构造器里（是运行时后填的），换角色时必须一起带过去 ——
        // 否则「先建带贡献者的星烁 spec，再 withAttackerCharacter」会把贡献者列表丢掉，
        // 星扩散的风段 / 冰段伤害直接算成 0。
        copy.damageBonus = this.damageBonus;
        copy.sovereigntyBonus = this.sovereigntyBonus;
        copy.stellarBaseBonusMultValue = this.stellarBaseBonusMultValue;
        copy.lunarContributors = this.lunarContributors;
        copy.stellarContributors = this.stellarContributors;
        copy.hitPoiseCoefficient = this.hitPoiseCoefficient;
        copy.hitImpact = this.hitImpact;
        return copy;
    }

    // ========== Getter ==========
    public AttackType getAttackType() { return attackType; }
    public GenshinElement getElement() { return element; }
    public float getAtkMultiplier() { return atkMultiplier; }
    public float getHpMultiplier() { return hpMultiplier; }
    public float getDefMultiplier() { return defMultiplier; }
    public float getEmMultiplier() { return emMultiplier; }
    public float getSkillMultiplierBonus() { return skillMultiplierBonus; }
    public float getFlatDamageBonus() { return flatDamageBonus; }
    public float getElementAmount() { return elementAmount; }
    public DecayGroup getDecayGroup() { return decayGroup; }
    public @Nullable PGCharacter getAttackerCharacter() { return attackerCharacter; }

    // ========== 便捷判断 ==========
    public boolean isElemental() { return element != ModElements.FYSIKOS.get(); }
    public boolean isPhysical() { return element == ModElements.FYSIKOS.get(); }
    public boolean hasDecayTag() { return attackType.hasDecayTag(); }
    public boolean hasAuraPotential() { return elementAmount > 0 && isElemental(); }

    public boolean isCrit() { return crit; }
    public void setCrit(boolean crit) { this.crit = crit; }

    public DecayGroup getEffectiveDecayGroup() {
        return decayGroup != null ? decayGroup : DecayGroups.DEFAULT_NORMAL_ATTACK;
    }
    public String getDecayTag() { return attackType.getDecayTag(); }


    // ========== 工厂方法 ==========

    /**
     * 创建一个新的 Builder
     * @param attackType 攻击类型（必填）
     * @param element 伤害元素（必填）
     * @return 新的 Builder 实例
     */
    public static Builder builder(AttackType attackType, GenshinElement element) {
        return new Builder(attackType, element);
    }

    /**
     * 快速创建一个物理伤害规格
     */
    public static ModDamageSpec physical(AttackType attackType, float multiplier) {
        return builder(attackType, ModElements.FYSIKOS.get())
                .multiplier(multiplier)
                .build();
    }

    /**
     * 快速创建一个带默认附着的元素伤害规格
     */
    public static ModDamageSpec elemental(AttackType attackType, GenshinElement element, float multiplier) {
        return builder(attackType, element)
                .multiplier(multiplier)
                .elementAmount(1.0f)
                .build();
    }

    // ========== Builder 内部类 ==========

    /**
     * 伤害规格构建器 —— 使用 Builder 模式灵活构建 DamageSpec
     *
     * 使用示例：
     * <pre>
     * // 普通元素攻击（使用默认衰减组别）
     * DamageSpec normal = DamageSpec.builder(AttackType.NORMAL_ATTACK, ModElements.PYRO.get())
     *     .multiplier(1.5f)
     *     .elementAmount(1.0f)
     *     .build();
     *
     * // 申鹤战技（自定义衰减组别：0.1s清除，只有第1次附着）
     * DecayGroup shenheGroup = new DecayGroup(2,
     *     DecaySequence.of(1,0,0,0,0,0,0),
     *     DecaySequence.DEFAULT_DAMAGE,
     *     DecaySequence.DEFAULT_POISE);
     * DamageSpec skill = DamageSpec.builder(AttackType.ELEMENTAL_SKILL, ModElements.CYRO.get())
     *     .multiplier(2.5f)
     *     .elementAmount(1.0f)
     *     .decayGroup(shenheGroup)
     *     .build();
     *
     * // 0伤害但有附着的特殊攻击
     * DamageSpec auraOnly = DamageSpec.builder(AttackType.SPECIAL, ModElements.HYDRO.get())
     *     .multiplier(0.0f)
     *     .elementAmount(1.0f)
     *     .build();
     * </pre>
     */
    // ========== Builder ==========
    public static class Builder {
        private final AttackType attackType;
        private final GenshinElement element;

        private float atkMultiplier = 0.0f;
        private float hpMultiplier = 0.0f;
        private float defMultiplier = 0.0f;
        private float emMultiplier = 0.0f;
        private float skillMultiplierBonus = 0.0f;
        private float flatDamageBonus = 0.0f;
        private float elementAmount = 0.0f;
        private DecayGroup decayGroup = null;
        private PGCharacter attackerCharacter = null;

        private Builder(AttackType attackType, GenshinElement element) {
            this.attackType = attackType;
            this.element = element;
        }

        /** 设置攻击力倍率（旧的 multiplier 方法指向这里，保持兼容） */
        public Builder multiplier(float v) { return atkMultiplier(v); }
        public Builder atkMultiplier(float v) { this.atkMultiplier = v; return this; }
        public Builder hpMultiplier(float v) { this.hpMultiplier = v; return this; }
        public Builder defMultiplier(float v) { this.defMultiplier = v; return this; }
        public Builder emMultiplier(float v) { this.emMultiplier = v; return this; }

        /** 设置技能倍率提升（不是技能面板上的倍率，是额外乘的，如行秋4命、深渊buff） */
        public Builder skillMultiplierBonus(float v) { this.skillMultiplierBonus = v; return this; }

        public Builder flatBonus(float v) { return flatDamageBonus(v); }
        public Builder flatDamageBonus(float v) { this.flatDamageBonus = v; return this; }

        public Builder elementAmount(float v) { this.elementAmount = v; return this; }
        public Builder decayGroup(DecayGroup g) { this.decayGroup = g; return this; }
        public Builder attackerCharacter(PGCharacter c) { this.attackerCharacter = c; return this; }

        public ModDamageSpec build() {
            ModDamageSpec spec = new ModDamageSpec(
                    attackType, element,
                    atkMultiplier, hpMultiplier, defMultiplier, emMultiplier,
                    skillMultiplierBonus, flatDamageBonus,
                    elementAmount, decayGroup,
                    attackerCharacter
            );
            // 这一下是哪个伤害点：系数由 ActionState 在触发伤害回调前挂好
            spec.hitPoiseCoefficient = com.linweiyun.genshin.core.system.poise.HitPoise.current();
            // 冲击类型同理：破韧那一刻由 PoiseService.onBreak 交给 ImpactSolver
            spec.hitImpact = HitImpact.current();
            return spec;
        }
    }

    // ========== 调试输出 ==========

    @Override
    public String toString() {
        return "DamageSpec{" +
                "type=" + attackType +
                ", element=" + element +
                ", atkMult=" + atkMultiplier +
                ", flatBonus=" + flatDamageBonus +
                ", elementAmt=" + elementAmount +
                '}';
    }
}
