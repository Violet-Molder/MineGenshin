package com.linweiyun.genshin.core.system.combat.damage;

/**
 * 一次伤害的两阶段结果。
 *
 * <p>不变式：{@code damaged} 为真时必有 {@code hit} 为真、{@code finalDamage > 0} 且 {@code blockReason == NONE}；
 * {@code killed} 为真时必有 {@code damaged} 为真。{@code blockReason} 只在 {@code hit} 为真、{@code damaged} 为假时有意义。
 */
public record DamageOutcome(boolean hit, boolean damaged, float rawDamage, float shieldAbsorbed,
                            float finalDamage, DamageBlockReason blockReason, boolean killed) {

    /** 为什么没有掉血。 */
    public enum DamageBlockReason {

        /** 掉血了，什么都没挡。 */
        NONE,

        /** 盾把这一下全吃下了，血量没动（附着与反应照常发生）。 */
        SHIELD,

        /** 目标免疫这个元素的伤害；附着与反应照常发生。 */
        IMMUNITY,

        /** {@code CommonHooks.onEntityIncomingDamage} 把这一下取消了。 */
        INCOMING_CANCELLED,

        /** 结算出来的伤害本来就是 0。 */
        ZERO,

        /** 目标在结算前就已经死了（多段连击的后段）。本版不发事件，保留给调试与将来。 */
        DEAD_TARGET,

        /** 世界没有开入侵（非原神模式）。本版不发事件，保留给调试与将来。 */
        NOT_INVADED
    }

    /** 没有进入结算的占位结果。 */
    public static DamageOutcome notHit(DamageBlockReason reason) {
        return new DamageOutcome(false, false, 0f, 0f, 0f, reason, false);
    }
}