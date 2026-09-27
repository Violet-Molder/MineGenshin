package com.linweiyun.genshin.core.system.poise;

/**
 * 「<b>当前正在结算的那个伤害点</b>的削韧系数」—— 一个作用域很窄的上下文。
 *
 * <h2>为什么需要一个静态载体</h2>
 * 削韧系数写在 {@code Hit.poise} 上（招式数据里），而 {@code ModDamageSpec} 是在
 * 「这一招的伤害回调」里建的 —— 从伤害点（{@code ActionState.fireDamagePoint}）
 * 到伤害规格中间隔着角色的技能方法，一路不传参数。要让它自动生效，
 * 只能在伤害点触发回调的那一小段时间里把系数挂出来：
 *
 * <pre>
 * ActionState.fireDamagePoint(i)
 *   ├─ 记下旧值，把 hits[i].poise 放进来   ← push
 *   ├─ 跑 onActiveStart 回调（技能在这里建 ModDamageSpec）
 *   │    └─ ModDamageSpec.Builder.build() 自动读走这个系数
 *   └─ 恢复旧值                            ← restore
 * </pre>
 *
 * <p>安全性：伤害结算全在服务端主线程上同步跑，且 {@code push}/{@code restore} 成对出现
 * （{@code finally} 里恢复），所以不会串到别的招式上。**不是**给跨 tick 用的状态。
 *
 * <p>默认 {@code 1.0} = 该攻击类型的基准削韧；重击、下落攻击这类「更重的一下」
 * 在招式数据里写大于 1 的系数即可。
 */
public final class HitPoise {

    private static float current = 1.0f;

    private HitPoise() {
    }

    /** 当前伤害点的削韧系数；不在任何伤害点里时是 1.0。 */
    public static float current() {
        return current;
    }

    /**
     * 进入一个伤害点。
     *
     * @param coefficient 这一下的削韧系数
     * @return 旧值，供 {@link #restore(float)} 复原
     */
    public static float push(float coefficient) {
        float old = current;
        current = coefficient > 0f ? coefficient : 1.0f;
        return old;
    }

    /** 离开这个伤害点。 */
    public static void restore(float old) {
        current = old;
    }
}
