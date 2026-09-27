package com.linweiyun.genshin.client.render.optimize;

import org.jspecify.annotations.Nullable;

/**
 * GeckoLib 模型几何优化的每帧读数，供 F3 的 {@code mg-render} 一行使用。
 *
 * <h2>口径</h2>
 * <ul>
 *   <li>{@code models}：这一采样窗口里走了优化路径的 GeckoLib 模型渲染次数。
 *       接管点已经从角色上移到 {@code GeoRenderer#submitRenderTasks}，所以角色、本模组实体、
 *       其它模组的 GeckoLib 实体都并进这一栏，不再只统计角色；</li>
 *   <li>{@code walk}：这些渲染里「骨骼遍历 + 写顶点」的 CPU 时间合计；
 *       不含 GeckoLib 的动画求值（那部分在 {@code renderPosed} 之前就做完了）；</li>
 *   <li>{@code v}：实际写进顶点缓冲的顶点数（骨骼被隐藏时会小于模型顶点总数）；</li>
 *   <li>{@code gpu}：这些渲染里有几次走的是 GPU 蒙皮（顶点常驻显存、每帧只上传骨骼矩阵）。
 *       它的 CPU 时间只有「算骨骼调色板」那一段，所以同样并进 {@code walk} ——
 *       这样同一条读数就能直接对比 CPU 蒙皮与 GPU 蒙皮的每模型开销；</li>
 *   <li>{@code us/model}：单个模型平摊下来多少微秒，多角色场景下看这个更直观；</li>
 *   <li>{@code (x.x% of frame)}：{@code walk} 占一帧总时间的百分比。
 *       绝对微秒数会随场景规模一起变，占比才是能横向比的东西 —— 帧时间取自
 *       {@link FrameTimeStats}（每个 {@code FlipFrameEvent} 采一次）；</li>
 *   <li>{@code gpu}：本模组这批 GPU 蒙皮绘制在 GPU 上占的时间轴跨度
 *       （{@link com.linweiyun.genshin.client.render.optimize.gpu.SkinnedGpuTimer}）。
 *       它才是「顶点搬到显存之后 GPU 有没有变贵」的答案，{@code walk} 只说明 CPU 省了多少。
 *       设备不支持时间戳查询时这一栏整体不显示；</li>
 *   <li>{@code gpu off (原因)}：这一帧走了优化路径，但 GPU 蒙皮被环境挡下了
 *       （{@code shader pack} = 光影包正在生效，{@code vertex format} = 管线被外部改过）。
 *       两者都会让 GPU 路径自带的顶点格式与运行时实际使用的布局对不上，
 *       所以自动让位给 CPU 蒙皮 —— 见
 *       {@link com.linweiyun.genshin.client.render.optimize.gpu.SkinnedPipelineGuard}；</li>
 *   <li>{@code fallback}：本窗口里因为「编译不了」而回退 GeckoLib 原路径的次数，
 *       正常应恒为 0。</li>
 * </ul>
 *
 * <h2>为什么采样窗口就是「一帧」</h2>
 * 采样由 F3 调试屏的 {@code display} 驱动 —— 调试屏打开时每帧调一次，
 * 于是「上次读取到这次读取之间」恰好是一帧。F3 关闭时读数照常累加但不显示，
 * 重新打开的第一帧会因为断档被丢弃（见 {@link #pollLine(long, long, String)}），
 * 所以不会看到一坨攒了很久的数字。
 *
 * <p>采样本身只是几个 {@code System.nanoTime()} 和字段累加，开销可以忽略。</p>
 */
public final class RenderOptimizeStats {

    /** 距上次采样超过这个间隔就认为断档（F3 刚打开、暂停、卡顿），丢弃累积值。 */
    private static final long MAX_SAMPLE_GAP_NANOS = 200_000_000L;

    private static long lastPollNanos;
    private static long walkNanos;
    private static int models;
    private static int vertices;
    private static int gpuModels;
    private static int fallbacks;

    private RenderOptimizeStats() {
    }

    /** 记一次优化路径的模型渲染。 */
    public static void record(long nanos, int writtenVertices) {
        walkNanos += nanos;
        models++;
        vertices += writtenVertices;
    }

    /** 记一次「编译不了、回退原路径」。 */
    public static void recordFallback() {
        fallbacks++;
    }

    /**
     * 记一次 GPU 蒙皮的模型渲染。
     *
     * <p>{@code nanos} 是「算骨骼矩阵 + 写进常量缓冲」的耗时，也就是 GPU 路径上剩下的全部
     * CPU 开销（顶点早在编译期就进了显存，每帧不再碰）。并进 {@code walk} 而不是单开一栏，
     * 是为了让「关掉 gpu-skinning 再打开」这组对照能直接看同一个数字的变化。</p>
     *
     * @param writtenVertices 这个模型提交的顶点数（与 CPU 路径同口径：骨骼被隐藏时少于模型顶点总数）
     */
    public static void recordGpu(long nanos, int writtenVertices) {
        walkNanos += nanos;
        models++;
        vertices += writtenVertices;
        gpuModels++;
    }

    /**
     * 取本帧读数并开始下一帧。
     *
     * @param frameNanos 上一帧的总时长（{@link FrameTimeStats#lastFrameNanos()}），0 = 未知
     * @param skinnedGpuNanos 本模组绘制的 GPU 时间轴跨度，0 = 没有读数
     * @param gpuOffNote GPU 路径被环境挡下的原因（见 {@link #format}），{@code null} = 没被挡下
     */
    public static String pollLine(long frameNanos, long skinnedGpuNanos, @Nullable String gpuOffNote) {
        final long now = System.nanoTime();
        final boolean stale = lastPollNanos == 0L || now - lastPollNanos > MAX_SAMPLE_GAP_NANOS;
        if (stale) {
            reset();
        }
        lastPollNanos = now;

        final String line = format(walkNanos, models, vertices, gpuModels, fallbacks, frameNanos, skinnedGpuNanos, gpuOffNote);
        reset();
        return line;
    }

    /** 本帧写入的顶点数（调试用）。 */
    public static int vertices() {
        return vertices;
    }

    /** 本帧走了优化路径的模型数（调试用）。 */
    public static int models() {
        return models;
    }

    private static void reset() {
        walkNanos = 0L;
        models = 0;
        vertices = 0;
        gpuModels = 0;
        fallbacks = 0;
    }

    /**
     * 单行摘要。
     *
     * <p>用 ASCII 而不是中文：调试屏默认字体不保证有中文字形，缺字形会直接丢字。</p>
     */
    private static String format(long nanos, int modelCount, int vertexCount, int gpuCount, int fallbackCount,
                                 long frameNanos, long gpuNanos, @Nullable String gpuOffNote) {
        StringBuilder sb = new StringBuilder(96);
        sb.append("MineGenshin render: ").append(modelCount).append(" model");
        if (gpuCount > 0) {
            sb.append(" (").append(gpuCount).append(" gpu)");
        }
        if (fallbackCount > 0) {
            sb.append(" (").append(fallbackCount).append(" fallback)");
        }
        sb.append(" | walk ").append(ms(nanos)).append("ms");
        // 占比只在两个数都有意义时才报：帧时间未知（刚开始 / 断档）时不显示。
        if (frameNanos > 0L && nanos > 0L) {
            sb.append(" (").append(percent(nanos, frameNanos)).append("% of frame)");
        }
        if (gpuOffNote != null && modelCount > 0) {
            // 有模型走了优化路径却被挡下：把「被谁挡下的」直接写在读数里，
            // 否则这一栏的缺席会被误读成「设备不支持时间戳查询」。
            // 这一支优先于下面的 `gpu`：刚切到光影包时时间戳的滑动平均里还留着切换前的旧值。
            sb.append(" | gpu off (").append(gpuOffNote).append(')');
        } else if (gpuNanos > 0L) {
            sb.append(" | gpu ").append(ms(gpuNanos)).append("ms");
        }
        sb.append(" | ").append(vertexCount).append('v');
        if (modelCount > 0) {
            sb.append(" | ").append(usPerModel(nanos, modelCount)).append("us/model");
        }
        return sb.toString();
    }

    /** 纳秒 → 保留两位小数的毫秒。只在调试路径上调用。 */
    private static String ms(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.2f", nanos / 1_000_000.0);
    }

    /** 一个耗时占另一个的百分比，保留一位小数。 */
    private static String percent(long part, long whole) {
        return String.format(java.util.Locale.ROOT, "%.1f", part * 100.0 / whole);
    }

    /** 纳秒 / 模型数 → 保留一位小数的微秒。 */
    private static String usPerModel(long nanos, int modelCount) {
        return String.format(java.util.Locale.ROOT, "%.1f", nanos / 1000.0 / modelCount);
    }
}
