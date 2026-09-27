package com.linweiyun.genshin.core.system.poise;

import com.linweiyun.genshin.core.system.poise.impact.ImpactLevel;

/**
 * 「<b>当前正在结算的那个伤害点</b>的冲击类型」—— 和 {@link HitPoise} 同一个作用域的上下文。
 *
 * <h2>为什么也要一个静态载体</h2>
 * 冲击类型写在招式数据的 {@code Hit.impact} 上，而 {@code ModDamageSpec} 是在
 * 「这一招的伤害回调」里建的 —— 中间隔着角色的技能方法，一路不传参数。
 * 所以照 {@link HitPoise} 的做法，在伤害点触发回调的那一小段时间里把它挂出来：
 *
 * <pre>
 * ActionState.fireDamagePoint(i)
 *   ├─ 记下旧值，把 hits[i].impact 放进来   ← push
 *   ├─ 跑 onActiveStart 回调（技能在这里建 ModDamageSpec）
 *   │    └─ ModDamageSpec.Builder.build() 自动读走这个冲击类型
 *   └─ 恢复旧值                             ← restore
 * </pre>
 *
 * <p>默认是 <b>{@code null}</b>（= 没指定）：真值由 {@code WeaponPoiseTable} 按攻击者的武器类型查，
 * 查不到才退回 {@code ModDamageSpec.defaultImpact(AttackType)}。
 * 原因是「同一个普攻」在不同武器上差得很远（单手剑 3、法器 2、弓照文献是 1），
 * 在这里钉死任何一档都必然对别的武器是错的。
 *
 * <p>安全性同 {@link HitPoise}：伤害结算全在服务端主线程同步跑，push/restore 成对出现，
 * 不会串到别的招式上；**不是**跨 tick 状态。
 */
public final class HitImpact {

    /** 没有显式配过时的值：{@code null} = 没指定，交给武器表 / 攻击类型表决定。 */
    public static final ImpactLevel DEFAULT_IMPACT = null;

    private static ImpactLevel current = DEFAULT_IMPACT;

    private HitImpact() {
    }

    /** 当前伤害点的冲击类型；不在任何伤害点里、或这一下没指定时是 {@code null}。 */
    public static ImpactLevel current() {
        return current;
    }

    /**
     * 进入一个伤害点。
     *
     * @param impact 这一下的冲击类型；null = 没指定（这是正常取值，不是异常）
     * @return 旧值，供 {@link #restore(ImpactLevel)} 复原
     */
    public static ImpactLevel push(ImpactLevel impact) {
        ImpactLevel old = current;
        current = impact != null ? impact : DEFAULT_IMPACT;
        return old;
    }

    /** 离开这个伤害点。 */
    public static void restore(ImpactLevel old) {
        current = old != null ? old : DEFAULT_IMPACT;
    }
}
