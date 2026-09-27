package com.linweiyun.genshin.client.performance;

import com.linweiyun.genshin.core.system.performance.DamageNumberThrottle;

/**
 * 飘字的<b>每帧开销读数</b>（渲染优化模块的观测端）。
 *
 * <h2>为什么要有它</h2>
 * 「飘字快不快」在游戏里是看不见的：帧率被地形、实体、粒子一起决定，
 * 靠肉眼和 FPS 数字判断不出飘字这一层到底花了几毫秒。这里把这条链路上
 * <b>两段自己可控的 CPU 时间</b>直接量出来，交给 F3 调试屏显示：
 *
 * <ol>
 *   <li><b>plan</b>：每帧的可见性剔除 / 限量 / 锚点位姿烘焙
 *       （{@link IndicatorFramePlanner#plan}）—— 纯 CPU，随飘字条数线性增长；</li>
 *   <li><b>vert</b>：真正往顶点缓冲里写几何的时间，也就是所有
 *       {@code submitCustomGeometry} 回调里跑的那一段（字形两趟 + 逐顶点渐变上色）。
 *       它在香草的 feature 渲染阶段执行，是渲染侧最实的开销。</li>
 * </ol>
 *
 * <p>另外顺带报出「提交了几条 / 活跃几条」「字形缓存多少条」「生成侧合并命中率」，
 * 这三项正好对应新系统三条优化路径各自的成效：限量剔除、排版缓存、生成侧合并。</p>
 *
 * <h2>读数怎么用</h2>
 * <pre>
 * plan 0.03ms   → 每帧规划自身耗时，几十微秒量级
 * vert 0.41ms   → 每帧写飘字顶点耗时，这是主指标
 * drawn 12/40   → 这一帧提交 12 条，活跃 40 条（差额被剔除或超上限丢掉）
 * glyph 18      → 字形缓存条数（不同文字各一条；稳态下不再增长）
 * merge 31/46   → 生成侧合并：46 次飘字请求里 31 次被并进已有数字
 * </pre>
 *
 * <p>要对比 {@code performance.toml} 里开关的效果，就在同一场景下切换
 * {@code indicator-render.batch_submit} 或 {@code damage-number.merge}，
 * 看 {@code vert} 与 {@code drawn} 的变化。F3 覆盖层自己也有开销，
 * 测的时候可以把其它调试条目关掉（F3 的调试选项菜单里能逐条开关）。</p>
 *
 * <p>所有计数都由渲染主线程读写（F3 与飘字提交是同一条线程），所以不加同步。
 * 采集本身是每帧 2~4 次 {@code System.nanoTime()}，可以忽略。</p>
 */
public final class IndicatorPerfStats {

    /** 上一帧规划耗时（纳秒） */
    private static long planNanos;
    /** 本帧顶点生产累计耗时（纳秒），每次规划时归零 */
    private static long vertexNanos;
    /** 本帧写进顶点缓冲的顶点数（阴影趟算两份），每次规划时归零 */
    private static int vertexCount;
    /** 上一帧活跃条数 / 实际提交条数 */
    private static int activeCount;
    private static int drawnCount;

    private IndicatorPerfStats() {}

    /**
     * 一帧规划结束：记下耗时与条数，并把本帧的顶点计时归零。
     *
     * @param nanos   本轮 {@link IndicatorFramePlanner#plan} 的耗时
     * @param active  本轮活跃飘字条数（剔除前）
     * @param planned 本轮真正要提交的条数（剔除 + 限量后）
     */
    public static void recordPlan(long nanos, int active, int planned) {
        planNanos = nanos;
        activeCount = active;
        drawnCount = planned;
        // 归零放在这里：顶点生产发生在提交之后的 feature 渲染阶段，
        // 所以「这一刻起累计」正好覆盖本帧全部飘字几何
        vertexNanos = 0L;
        vertexCount = 0;
    }

    /** 累加一段顶点生产耗时（每次 {@code submitCustomGeometry} 回调各一次）。 */
    public static void addVertexNanos(long nanos) {
        vertexNanos += nanos;
    }

    /**
     * 累加一次提交里写出的顶点数。
     *
     * <p>这条读数专门用来量「一条飘字到底多贵」：顶点数 ≈ 条数 × 字数 × 4 × 趟数，
     * 所以关掉 {@code indicator-render.shadow} 之后这个数字会直接减半，
     * 比盯着帧率更能说明开关到底省了什么。</p>
     */
    public static void addVertices(int count) {
        vertexCount += count;
    }

    /** 本帧写出的顶点数（调试用）。 */
    public static int vertexCount() {
        return vertexCount;
    }

    /** 本帧顶点生产累计耗时（纳秒）；调试用。 */
    public static long vertexNanos() {
        return vertexNanos;
    }

    /** 上一帧规划耗时（纳秒）；调试用。 */
    public static long planNanos() {
        return planNanos;
    }

    /** 上一帧活跃条数（调试用）。 */
    public static int activeCount() {
        return activeCount;
    }

    /** 本帧提交条数（调试用）。 */
    public static int drawnCount() {
        return drawnCount;
    }

    /**
     * F3 调试屏用的单行摘要。
     *
     * <p>用 ASCII 而不是中文：调试屏默认字体不保证有中文字形，缺字形会直接丢字
     * （除非玩家开了「强制使用 Unicode 字体」）。要加中文就先确认这点。</p>
     */
    public static String line() {
        StringBuilder sb = new StringBuilder(96);
        sb.append("MineGenshin indicators: plan ").append(ms(planNanos))
                .append("ms | vert ").append(ms(vertexNanos))
                .append("ms (").append(vertexCount).append("v)")
                .append(" | drawn ").append(drawnCount).append('/').append(activeCount)
                .append(" | glyph ").append(IndicatorGlyphCache.size());

        long[] merge = mergeStats();
        if (merge != null && merge[0] > 0L) {
            // merge 统计来自服务端台账；纯客户端连外部服务器时拿不到（保持 0 → 这一段不显示）
            sb.append(" | merge ").append(merge[1]).append('/').append(merge[0]);
        }
        return sb.toString();
    }

    /** 纳秒 → 保留两位小数的毫秒字符串（只在调试路径上调用）。 */
    private static String ms(long nanos) {
        return String.format("%.2f", nanos / 1_000_000.0);
    }

    /** 合并台账统计：{ 请求数, 合并数, 跟踪目标数 }；拿不到时返回 null。 */
    private static long[] mergeStats() {
        try {
            return DamageNumberThrottle.stats();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
