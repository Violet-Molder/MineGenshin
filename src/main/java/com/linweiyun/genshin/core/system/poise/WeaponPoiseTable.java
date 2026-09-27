package com.linweiyun.genshin.core.system.poise;

import com.linweiyun.genshin.content.items.weapon.WeaponItem;
import com.linweiyun.genshin.content.items.weapon.bow.Bow;
import com.linweiyun.genshin.content.items.weapon.catalyst.Catalyst;
import com.linweiyun.genshin.content.items.weapon.claymore.Claymore;
import com.linweiyun.genshin.content.items.weapon.polearm.Polearm;
import com.linweiyun.genshin.content.items.weapon.sword.Sword;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.genshin.core.system.poise.impact.ImpactLevel;

import javax.annotation.Nullable;

/**
 * <b>武器类型的基准削韧表</b> —— 文献那五张逐武器表（单手剑 / 双手剑 / 长柄武器 / 法器 / 弓）
 * 的代码化，用来回答「这一下普攻，起手削多少韧」。
 *
 * <h2>数值怎么来的</h2>
 * 五张表是<b>逐角色逐招</b>的：{@code 班尼特-普通攻击1 = 38.7}、{@code 北斗-普通攻击1 = 85.67}……
 * 直接照抄就等于把每个角色的每一段都写进代码，本阶段不需要那么细。这里按
 * <b>武器类型 × 招式大类</b>取<b>中位数</b>：
 *
 * <table border="1">
 *   <caption>基准削韧（取首次出现的那个数，复合写法如「45 x2」「19.2 + 20」只算第一下）</caption>
 *   <tr><th>武器</th><th>普攻</th><th>重击</th><th>下坠期间</th><th>低空坠地</th><th>高空坠地</th></tr>
 *   <tr><td>单手剑</td><td>50（n=143）</td><td>60（n=40）</td><td>25（n=30）</td><td>100（n=30）</td><td>150（n=30）</td></tr>
 *   <tr><td>双手剑</td><td>107.4（n=100）</td><td>81.7（n=51）</td><td>35（n=23）</td><td>150（n=23）</td><td>200（n=23）</td></tr>
 *   <tr><td>长柄武器</td><td>45.8（n=147）</td><td>120（n=33）</td><td>25（n=29）</td><td>100（n=28）</td><td>150（n=28）</td></tr>
 *   <tr><td>法器</td><td>10.2（n=100）</td><td>90（n=33）</td><td>5（n=28）</td><td>50（n=28）</td><td>100（n=28）</td></tr>
 *   <tr><td>弓</td><td>15.7（n=111）</td><td>20（n=44）</td><td>10（n=21）</td><td>50（n=21）</td><td>100（n=21）</td></tr>
 * </table>
 *
 * <p>中位数与文献的直觉一致：<b>双手剑最重、法器最轻、长柄居中</b>。
 * 拿这些数去对普通生物那条韧性（{@link PoiseTiers#profileOf(int) 1 档}，条长 100）：
 * 单手剑普攻 2 下、长柄 3 下、双手剑 1 下、弓 7 下、法器 10 下破 ——
 * 逐武器的实际张数写在 {@code PoiseTiers.TIER_PROFILES} 上方的表里。
 *
 * <p><b>这张表没有动过</b>，是文献中位数的原值：如果觉得法器 / 弓太慢，
 * 改这里是「降低角色的削韧模板」那条路（另一条路是抬高 {@code PoiseTiers} 的条长，
 * 2026-09-25 那次走的就是后者）。两个旋钮各调各的，互不覆盖。
 *
 * <h2>战技 / 元素爆发为什么不在这张表里</h2>
 * 文献里 {@code 战技（…）} 与 {@code 爆发（…）} 两类的削韧<b>取决于技能本身</b>，
 * 与武器类型没有统计关系（同是单手剑，行秋 E 是 0、琴 E 是 300）。那两个攻击类型
 * 仍旧走 {@code ModDamageSpec.defaultPoise} 的占位值 + 角色自己
 * {@code withPoiseDamage(...)} 覆盖，不要往这张表里塞。
 *
 * <p>反应类（超载 / 扩散 / 月感电…）同样不在这里 —— 它们走
 * {@code ReactionPoiseTable}。
 */
public final class WeaponPoiseTable {

    /** 五种武器 —— 项目的武器类是一对一的，所以直接按类认。 */
    public enum WeaponClass {
        SWORD,
        CLAYMORE,
        POLEARM,
        CATALYST,
        BOW,
        /** 认不出来（怪物攻击、环境伤害、没有攻击者）。 */
        UNKNOWN
    }

    /**
     * 武器派生冲击的<b>最低硬直等级</b>。
     *
     * <p>文献里弓箭普攻的冲击类型多数是 {@code 1}（微颤，力值 0/0），法器也有一半是 1。
     * 照抄的话「弓 / 法器普攻不推人、也（在抗打断系数 ≥ 2 的目标身上）打不断」，
     * 与「手里的武器普攻总该有点手感」这条项目口径不符，所以这里抬到 {@code 2}（轻击，水平 200）。
     *
     * <p>这是<b>手感上的刻意偏离</b>，不是文献值：想完全照文献跑，把这个常量改成
     * {@code 1} 即可（那时候弓 / 法器普攻变成微颤：不推人，但破韧期间照样打断普通敌人）。
     * 抬到 2 的实际含义是「所有武器的普攻都能打断抗打断系数 ≤ 2 的目标」。
     */
    private static final int MIN_WEAPON_IMPACT_LEVEL = 2;

    /** 下坠 / 落地三个阶段 —— 同一招的下落过程与两种落地，削韧与冲击都不同。 */
    public enum LandingPhase {
        /** 下坠期间（还在空中那一段）。 */
        FALL,
        /** 低空坠地冲击。 */
        LOW,
        /** 高空坠地冲击。 */
        HIGH
    }

    private WeaponPoiseTable() {
    }

    // ==================== 认武器 ====================

    /**
     * 攻击者用的是什么武器。
     *
     * <p>先看<b>手里那把</b>（最准），拿不到就退回角色的<b>限定武器类</b>
     * （{@code PGCharacter#getAllowedWeaponClass()}，角色是锁武器类型的，两者等价）。
     * 都认不出来返回 {@link WeaponClass#UNKNOWN}。
     */
    public static WeaponClass weaponOf(@Nullable PGCharacter character) {
        if (character == null) {
            return WeaponClass.UNKNOWN;
        }
        WeaponClass equipped = weaponOfStack(character);
        if (equipped != WeaponClass.UNKNOWN) {
            return equipped;
        }
        return weaponOfClass(character.getAllowedWeaponClass());
    }

    /** 手里那把武器的类型；没装备或不是本模组武器返回 {@link WeaponClass#UNKNOWN}。 */
    private static WeaponClass weaponOfStack(PGCharacter character) {
        try {
            var stack = character.getData().getWeapon();
            if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof WeaponItem item)) {
                return WeaponClass.UNKNOWN;
            }
            return weaponOfClass(item.getClass());
        } catch (RuntimeException e) {
            // 角色数据还没建好（离场/初始化中间态）：认不出来就当未知，不要在这里抛
            return WeaponClass.UNKNOWN;
        }
    }

    /** 把一个武器类（可以是武器的实际类，也可以是角色的限定类）映射成枚举。 */
    public static WeaponClass weaponOfClass(@Nullable Class<?> weaponClass) {
        if (weaponClass == null) {
            return WeaponClass.UNKNOWN;
        }
        if (Sword.class.isAssignableFrom(weaponClass)) return WeaponClass.SWORD;
        if (Claymore.class.isAssignableFrom(weaponClass)) return WeaponClass.CLAYMORE;
        if (Polearm.class.isAssignableFrom(weaponClass)) return WeaponClass.POLEARM;
        if (Catalyst.class.isAssignableFrom(weaponClass)) return WeaponClass.CATALYST;
        if (Bow.class.isAssignableFrom(weaponClass)) return WeaponClass.BOW;
        return WeaponClass.UNKNOWN;
    }

    // ==================== 取数 ====================

    /**
     * 这一下的基准削韧（一个伤害点的量）。
     *
     * <p>只有普攻 / 重击 / 下坠这三类由武器类型决定；其余攻击类型（战技、爆发、怪物、反应）
     * 返回 {@link Float#NaN} —— <b>NaN 的含义是「这张表管不着」，不是「削韧为 0」</b>，
     * 调用方要接着去看各自的那张表（{@code ModDamageSpec.defaultPoise} / {@code ReactionPoiseTable}）。
     */
    public static float basePoise(WeaponClass weapon, @Nullable AttackType attackType) {
        if (attackType == null || weapon == null || weapon == WeaponClass.UNKNOWN) {
            return Float.NaN;
        }
        return switch (attackType) {
            case NORMAL_ATTACK -> normalPoise(weapon);
            case CHARGED_ATTACK -> chargedPoise(weapon);
            // 下坠攻击整体按「下坠期间」算；两种落地的值在 landingPoise 单独取
            case PLUNGING_ATTACK -> landingPoise(weapon, LandingPhase.FALL);
            default -> Float.NaN;
        };
    }

    /** 普攻的基准削韧。 */
    public static float normalPoise(WeaponClass weapon) {
        return switch (weapon) {
            case SWORD -> 50.0f;
            case CLAYMORE -> 107.4f;
            case POLEARM -> 45.8f;
            case CATALYST -> 10.2f;
            case BOW -> 15.7f;
            case UNKNOWN -> Float.NaN;
        };
    }

    /** 重击的基准削韧。 */
    public static float chargedPoise(WeaponClass weapon) {
        return switch (weapon) {
            case SWORD -> 60.0f;
            case CLAYMORE -> 81.7f;
            case POLEARM -> 120.0f;
            case CATALYST -> 90.0f;
            case BOW -> 20.0f;
            case UNKNOWN -> Float.NaN;
        };
    }

    /**
     * 下坠 / 落地的基准削韧。
     *
     * <p>三个阶段差得很远：下坠期间是「蹭一下」（单手剑 25），
     * 低空坠地是「砸一下」（100），高空坠地最重（150）。
     * 现在没有 {@code AttackType} 能区分它们，所以只有
     * {@link LandingPhase#FALL} 会被 {@link #basePoise} 自动取到，
     * 另外两个由招式自己在伤害点里写（{@code Hit.poise} 系数）或直接读这里。
     */
    public static float landingPoise(WeaponClass weapon, LandingPhase phase) {
        if (weapon == null || phase == null) {
            return Float.NaN;
        }
        return switch (weapon) {
            case SWORD -> switch (phase) {
                case FALL -> 25.0f;
                case LOW -> 100.0f;
                case HIGH -> 150.0f;
            };
            case CLAYMORE -> switch (phase) {
                case FALL -> 35.0f;
                case LOW -> 150.0f;
                case HIGH -> 200.0f;
            };
            case POLEARM -> switch (phase) {
                case FALL -> 25.0f;
                case LOW -> 100.0f;
                case HIGH -> 150.0f;
            };
            case CATALYST -> switch (phase) {
                case FALL -> 5.0f;
                case LOW -> 50.0f;
                case HIGH -> 100.0f;
            };
            case BOW -> switch (phase) {
                case FALL -> 10.0f;
                case LOW -> 50.0f;
                case HIGH -> 100.0f;
            };
            case UNKNOWN -> Float.NaN;
        };
    }

    /**
     * 这一下的硬直等级 —— 文献那五张表「冲击类型」列的众数。
     *
     * <p>普攻、重击、下坠三处都是<b>该武器类型的众数</b>，
     * 落地两档照文献的固定档位（低空普遍 4、高空普遍 7，法器与弓更轻一档）。
     * 认不出武器、或不是这三类攻击 → {@code null}，表示「这张表管不着」。
     *
     * <p>返回值过了 {@link #MIN_WEAPON_IMPACT_LEVEL} 那道下限（见常量注释）。
     */
    @Nullable
    public static ImpactLevel baseImpact(WeaponClass weapon, @Nullable AttackType attackType) {
        if (attackType == null || weapon == null || weapon == WeaponClass.UNKNOWN) {
            return null;
        }
        return switch (attackType) {
            case NORMAL_ATTACK -> levelOf(normalImpact(weapon));
            case CHARGED_ATTACK -> levelOf(chargedImpact(weapon));
            case PLUNGING_ATTACK -> levelOf(landingImpactLevel(weapon, LandingPhase.FALL));
            default -> null;
        };
    }

    /** 下坠 / 落地各档的硬直等级。 */
    public static int landingImpactLevel(WeaponClass weapon, LandingPhase phase) {
        if (weapon == null || phase == null) {
            return 0;
        }
        return switch (weapon) {
            case SWORD, CLAYMORE, POLEARM -> switch (phase) {
                case FALL -> 2;
                case LOW -> 4;
                case HIGH -> 7;
            };
            case CATALYST -> switch (phase) {
                case FALL -> 2;
                case LOW -> 3;
                case HIGH -> 4;
            };
            case BOW -> switch (phase) {
                case FALL -> 2;
                case LOW -> 2;
                case HIGH -> 3;
            };
            case UNKNOWN -> 0;
        };
    }

    /** 普攻的硬直等级（文献众数）。 */
    public static int normalImpact(WeaponClass weapon) {
        return switch (weapon) {
            case SWORD, CLAYMORE, POLEARM -> 3;
            // 法器普攻在 1/2 之间各占一半：取 2，破韧后能打断动作
            case CATALYST -> 2;
            // 弓普攻几乎是清一色 1（微颤），照样靠 MIN_WEAPON_IMPACT_LEVEL 抬到 2
            case BOW -> 1;
            case UNKNOWN -> 0;
        };
    }

    /** 重击的硬直等级（文献众数）。 */
    public static int chargedImpact(WeaponClass weapon) {
        return switch (weapon) {
            case SWORD -> 2;
            case CLAYMORE -> 3;
            case POLEARM -> 5;
            case CATALYST -> 3;
            case BOW -> 2;
            case UNKNOWN -> 0;
        };
    }

    /** 0–9 的等级取 {@link ImpactLevel}；过下限；0 表示「没有冲击」。 */
    private static ImpactLevel levelOf(int level) {
        int clamped = Math.max(MIN_WEAPON_IMPACT_LEVEL, Math.clamp(level, ImpactLevel.MIN_LEVEL, ImpactLevel.MAX_LEVEL));
        return ImpactLevel.ofLevel(clamped);
    }
}
