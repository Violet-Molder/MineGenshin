package com.linweiyun.genshin.core.system.combat.action.data;

import com.linweiyun.genshin.core.system.poise.impact.ImpactLevel;

/**
 * 一个「伤害点」：招式在第几刻、相对施法者哪个位置、扫出多大一个球。
 *
 * <p>时序看 {@link #delay}，空间看 {@link #forward} / {@link #yOffset} / {@link #scope}，
 * 打中之后削多少韧看 {@link #poise}。
 *
 * <h2>为什么这里没有伤害数值</h2>
 * 这一份数据只回答<b>「什么时候打、打在哪、这一下削多少韧」</b>，不回答「打多少伤害」。
 * 伤害倍率由角色技能自己结算（{@code SkillBase} / {@code ElementalAttackSweep} 那条链）。
 *
 * <p>这里原先并列着 {@code damage}（基础伤害倍率）、{@code damageSp}（特殊伤害倍率）
 * 和 {@code ignoreInvuln}（是否无视无敌）三个字段，<b>全仓没有任何读取方</b> ——
 * 摆在这里会让人以为「伤害也能在这里配」，等于两套伤害来源。已删除。
 * 需要按「这一下」区分伤害或无敌判定时，走角色技能的伤害点回调，不要往这里加倍率字段。
 *
 * <h2>{@link #poise} 是系数，不是绝对削韧值</h2>
 * {@code 1.0} = 该攻击类型的基准削韧；重击、下落攻击这类「更重的一下」写大于 1 的值。
 * 真正的削韧量 = 攻击类型基准值 × 这个系数，所以数值刻度的选择
 * （见项目调研方案 §5）不会波及到这里，换刻度时这一份不用动。
 */
public final class Hit {

    /** 基准削韧系数 —— 没有特别说明的伤害点都用它。 */
    public static final double DEFAULT_POISE = 1.0;

    /**
     * 默认冲击类型 —— <b>{@code null}，意思是「这一下没显式指定」</b>，
     * 由 {@code WeaponPoiseTable} 按「武器类型 × 攻击类型」查出来。
     *
     * <h2>为什么默认是「没指定」而不是某一档</h2>
     * 同一个「普攻」，单手剑是轻击（3）、法器只有 2、弓照文献是微颤（1）——
     * 写成任何一个固定档都必然对另外几种武器是错的。
     * 所以默认留空，让真正知道攻击者拿什么武器的那一层（伤害规格）去查表；
     * 查不到才退回 {@code ModDamageSpec.defaultImpact(AttackType)}。
     *
     * <p>需要「这一下就是击飞」时在 6 参构造里显式写，写进去的值永远优先。
     */
    public static final ImpactLevel DEFAULT_IMPACT = null;

    /** 延时（刻）—— 相对动作起点。 */
    public final int delay;

    /** 向前距离（格）—— 沿施法者的水平视线方向。 */
    public final double forward;

    /** Y 偏移（格）—— 相对施法者眼睛高度的偏移量。 */
    public final double yOffset;

    /** 伤害范围（格）—— 以该点为中心扫出的球半径。 */
    public final double scope;

    /** 削韧系数 —— {@code 1.0} = 该攻击类型的基准削韧，越大越削韧。 */
    public final double poise;

    /**
     * 这一下的冲击类型 —— 破韧瞬间由 {@code PoiseService.onBreak} 交给
     * {@code ImpactSolver} 判定并施加冲量。
     *
     * <p>{@code null} = 没指定，按攻击者的武器类型查表（见 {@link #DEFAULT_IMPACT}）。
     * 击退、击飞、超载这类更重的招式在 6 参构造里显式写。
     */
    public final ImpactLevel impact;

    /** 用基准削韧系数建一个伤害点。 */
    public Hit(int delay, double forward, double yOffset, double scope) {
        this(delay, forward, yOffset, scope, DEFAULT_POISE, DEFAULT_IMPACT);
    }

    public Hit(int delay, double forward, double yOffset, double scope, double poise) {
        this(delay, forward, yOffset, scope, poise, DEFAULT_IMPACT);
    }

    public Hit(int delay, double forward, double yOffset, double scope, double poise, ImpactLevel impact) {
        this.delay = delay;
        this.forward = forward;
        this.yOffset = yOffset;
        this.scope = scope;
        this.poise = poise;
        this.impact = impact != null ? impact : DEFAULT_IMPACT;
    }
}
