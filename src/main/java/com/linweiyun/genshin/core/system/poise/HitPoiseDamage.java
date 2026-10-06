package com.linweiyun.genshin.core.system.poise;

/**
 * 「当前伤害点算出来的实际削韧值」的窄作用域载体，形状同 {@link HitPoise}。
 * 技能建 ModDamageSpec 时由它把值报出来，攻击管线拿去算方块韧性。
 */
public final class HitPoiseDamage {

    private static float current = Float.NaN;

    private HitPoiseDamage() {
    }

    public static void report(float value) {
        current = value;
    }

    /** 当前伤害点的实际削韧值；没报过是 NaN。 */
    public static float current() {
        return current;
    }

    public static void reset() {
        current = Float.NaN;
    }
}
