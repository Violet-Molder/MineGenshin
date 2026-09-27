package com.linweiyun.genshin.core.system.combat.damage;

/**
 * <b>摔落伤害曲线</b> —— 按「下坠高度（格）」给出这一摔要扣掉<b>当前角色最大生命值的百分之几</b>。
 *
 * <h2>为什么不用原版那套</h2>
 * 原版是「(高度 − 安全高度) × 1 点血」，和角色动辄几千的最大生命值完全不是一个量纲；
 * 原神口径是<b>按最大生命值的百分比扣</b>，而且有一条明确的「多少格之内免伤」。
 *
 * <h2>两条曲线（用户口径，2026-09-27）</h2>
 * 用户给的是「高度 → <b>剩余生命</b>（%）」的反向表，这里换算成「扣血比例 = 1 − 剩余」：
 *
 * <pre>
 * 普通摔落（自由落体）
 *   高度(格)  22    26    30    34    38    40    42
 *   剩余(%)  100  78.7  62.0  32.6  22.9  13.1   0
 *   扣血(%)    0  21.3  38.0  67.4  77.1  86.9 100
 *   ⇒ 22 格以内（含）不掉血，42 格摔死。
 *
 * 下落攻击之后（免伤区间被抬到 48 格）
 *   48 格以内不掉血；48 → 52 线性到扣 10%；之后每 4 格 +18%，72 格扣满。
 * </pre>
 *
 * <h2>2026-09-27 第二轮：免伤区 +10 格、每 2 米一档改每 4 米</h2>
 * 用户口径「范围全部上调 10 格，不管是下落攻击的免伤还是正常下落的免伤区域；
 * 然后每 2 米提升一档改成每 4 米」—— 旧的 12 格免伤 → <b>22 格</b>、
 * 旧的 38 格（下落攻击）→ <b>48 格</b>，档位间距整体翻倍（表里后面那两档原本是 1 格一档，
 * 于是变成 2 格一档），每一档的百分比数值不变。
 *
 * <p><b>两点之间一律线性插值</b>，表头以下/表尾以上分别取 0 与 100%
 * （所以「22 格以内不掉血」和「超过 42 格都是摔死」都不用单独写分支）。
 *
 * <p>想调手感只改下面两张表，别在这里加分支 —— 表就是口径。
 */
public final class FallDamage {

    /** 普通摔落：高度（格），必须严格递增。 */
    private static final double[] PLAIN_HEIGHT = {22.0, 26.0, 30.0, 34.0, 38.0, 40.0, 42.0};

    /** 普通摔落：对应的<b>剩余生命</b>比例（1.0 = 满血，0 = 摔死）。 */
    private static final double[] PLAIN_REMAIN = {1.000, 0.787, 0.620, 0.326, 0.229, 0.131, 0.000};

    /** 下落攻击：免伤区间抬到 48 格；52 格扣 10%；每 4 格 +18%，72 格扣满。 */
    private static final double[] PLUNGE_HEIGHT = {48.0, 52.0, 72.0};

    /** 下落攻击：对应的剩余生命比例。 */
    private static final double[] PLUNGE_REMAIN = {1.000, 0.900, 0.000};

    private FallDamage() {
    }

    /**
     * 这一摔要扣掉最大生命值的百分之几。
     *
     * @param fallHeight 这一摔的<b>实际下坠高度</b>（格，不是原版那个「高度 − 3」）
     * @param plunging   这一摔是不是在<b>下落攻击</b>状态下完成的（免伤区间 48 格）
     * @return 0 ~ 1 的扣血比例（0 = 不掉血，1 = 直接摔死）
     */
    public static float damageFraction(double fallHeight, boolean plunging) {
        if (Double.isNaN(fallHeight) || fallHeight <= 0.0) {
            return 0f;
        }
        double[] heights = plunging ? PLUNGE_HEIGHT : PLAIN_HEIGHT;
        double[] remain = plunging ? PLUNGE_REMAIN : PLAIN_REMAIN;
        return (float) (1.0 - remaining(heights, remain, fallHeight));
    }

    /** 两条曲线各自「多少格以内不掉血」——调试与自检用。 */
    public static double safeHeight(boolean plunging) {
        return plunging ? PLUNGE_HEIGHT[0] : PLAIN_HEIGHT[0];
    }

    /**
     * 分段线性插值：表头以下取首值（满血 = 不掉血），表尾以上取末值（0 = 摔死）。
     */
    private static double remaining(double[] heights, double[] remain, double height) {
        if (height <= heights[0]) {
            return remain[0];
        }
        for (int i = 1; i < heights.length; i++) {
            if (height <= heights[i]) {
                double span = heights[i] - heights[i - 1];
                double ratio = span <= 0.0 ? 1.0 : (height - heights[i - 1]) / span;
                return remain[i - 1] + (remain[i] - remain[i - 1]) * ratio;
            }
        }
        return remain[remain.length - 1];
    }
}
