package com.linweiyun.genshin.core.system.compat;

/**
 * 「其他 MOD 打出的伤害」→「角色口径伤害」的换算公式。
 *
 * <p>公式本身只有一步：
 * <pre>
 *   倍率 = 当前角色攻击力 ÷ 玩家原版攻击力
 *   换算后伤害 = 原始伤害 × 倍率
 * </pre>
 *
 * <p>分母用玩家自己的攻击力属性：原神模式下它没被本 MOD 动过，所以就是原版值；
 * 非原神模式下它已经含了「角色攻击力 × 0.7」那一条，所以不会二次放大。
 *
 * <p>分母或分子非正（没有武器 / 没有角色）时返回 0，调用方据此判定「这次不换算」，
 * 原样放行，避免把伤害打成 0。
 */
public final class DamageConversion {

    private DamageConversion() {
    }

    /** 换算倍率。任一输入非正时返回 0（表示「不换算」）。 */
    public static double rate(double playerAttack, double characterAttack) {
        if (playerAttack <= 0.0D || characterAttack <= 0.0D) {
            return 0.0D;
        }
        return characterAttack / playerAttack;
    }

    /** 把原始伤害换算成角色口径。倍率取不到时原样返回原始伤害。 */
    public static float convert(float rawDamage, double playerAttack, double characterAttack) {
        if (rawDamage <= 0.0F) {
            return rawDamage;
        }
        double rate = rate(playerAttack, characterAttack);
        if (rate <= 0.0D) {
            return rawDamage;
        }
        return (float) (rawDamage * rate);
    }
}
