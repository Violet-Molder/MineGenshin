package com.linweiyun.genshin.client.render.optimize;

/**
 * 帧时间与 <b>1% low</b> 的滑窗统计（F3 读数用）。
 *
 * <h2>为什么自己算</h2>
 * vanilla 的 F3 只有「瞬时帧率」和 {@code T:} 帧率上限，没有低帧指标；GPU 利用率那一栏
 * 也只是 GPU 忙的比例。而「改了渲染路径到底有没有让手感变差」看的就是尾部帧——
 * 平均帧率会把偶发的长帧抹掉，1% low 不会。
 *
 * <h2>口径</h2>
 * 每帧把 {@code Minecraft#getFrameTimeNs()} 丢进一个 {@value #WINDOW} 帧的环形窗口
 * （60fps 下约 10 秒），取数时把窗口里<b>最慢的 1%</b>帧挑出来求平均，再折成帧率：
 * {@code 1% low = 1e9 / mean(最慢的 1% 帧时长)}。这与 Afterburner / CapFrameX 的
 * 常见定义一致（「最慢那 1% 帧的平均帧率」），注意它不是「p99 帧率的倒数」。
 *
 * <p>采样点在 {@code FlipFrameEvent}（每帧恰好一次），所以窗口里是最近若干帧的完整历史，
 * 而不是「打开 F3 之后才开始攒」——读数一打开就有意义。</p>
 *
 * <p>只有渲染主线程碰它，不做同步。</p>
 */
public final class FrameTimeStats {

    /** 滑窗长度。600 帧 ≈ 60fps 下 10 秒。 */
    private static final int WINDOW = 600;
    /** 样本不足这么多帧时不给低帧数：1% 会退化成「最慢的一帧」，报出来只会误导。 */
    private static final int MIN_SAMPLES = 100;

    private static final long[] FRAME_NANOS = new long[WINDOW];
    /** 排序用的暂存，避免每次显示再分配。同样只被渲染线程碰。 */
    private static final long[] SCRATCH = new long[WINDOW];

    private static int count;
    private static int cursor;
    private static long lastFrameNanos;

    private FrameTimeStats() {
    }

    /** 每帧一次（{@code FlipFrameEvent}）。非正数直接忽略：那是「这一刻没有在渲染」。 */
    public static void sample(long frameNanos) {
        if (frameNanos <= 0L) {
            return;
        }

        lastFrameNanos = frameNanos;
        FRAME_NANOS[cursor] = frameNanos;
        cursor = (cursor + 1) % WINDOW;
        if (count < WINDOW) {
            count++;
        }
    }

    /** 最近一帧的帧时长（纳秒）；还没采到样时为 0。 */
    public static long lastFrameNanos() {
        return lastFrameNanos;
    }

    /** 最近一帧的帧率。 */
    public static double fps() {
        return lastFrameNanos <= 0L ? 0.0 : 1e9 / lastFrameNanos;
    }

    /** 窗口里已采集的帧数（窗口填满后恒为 {@value #WINDOW}）。 */
    public static int samples() {
        return count;
    }

    /**
     * {@code percent}% low 帧率：窗口里最慢的 {@code percent}% 帧的平均帧率。
     *
     * @return 样本不足 {@value #MIN_SAMPLES} 帧时为 0（F3 那一栏显示 {@code --}）
     */
    public static double lowFps(int percent) {
        final int n = count;
        if (n < MIN_SAMPLES) {
            return 0.0;
        }

        System.arraycopy(FRAME_NANOS, 0, SCRATCH, 0, n);
        java.util.Arrays.sort(SCRATCH, 0, n);

        // 升序排列 → 最慢的都在尾部
        final int take = Math.max(1, (int) Math.round(n * (percent / 100.0)));
        long sum = 0L;
        for (int i = n - take; i < n; i++) {
            sum += SCRATCH[i];
        }

        final double meanNanos = (double) sum / take;
        return meanNanos <= 0.0 ? 0.0 : 1e9 / meanNanos;
    }

    /**
     * F3 用的一行：瞬时帧率 + 1% low + 窗口状态。
     *
     * <p>用 ASCII：调试屏默认字体不保证有中文字形。</p>
     */
    public static String line() {
        final StringBuilder sb = new StringBuilder(72);
        sb.append("MineGenshin frame: ");

        if (lastFrameNanos <= 0L) {
            sb.append("-- ");
        } else {
            sb.append(String.format(java.util.Locale.ROOT, "%.1f fps (%.2fms) ", fps(), lastFrameNanos / 1_000_000.0));
        }

        final double low = lowFps(1);
        sb.append("| 1%low ");
        if (low <= 0.0) {
            sb.append("--");
        } else {
            sb.append(String.format(java.util.Locale.ROOT, "%.1f fps", low));
        }

        sb.append(" | window ").append(count).append('/').append(WINDOW);
        return sb.toString();
    }
}
