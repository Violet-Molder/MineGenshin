package com.linweiyun.genshin.client.render.optimize;

/**
 * 角色几何优化 + 帧时间的 F3 读数提供者。
 *
 * <p>两个静态出口各返回一整行 ASCII 文本：{@link #line()} 是几何优化读数，
 * {@link #frameLine()} 是帧时间读数。调用方把字符串加进 F3 的调试文本即可，
 * 本类不依赖任何调试屏类型。</p>
 */
public final class RenderOptimizeDebugEntry {

    private RenderOptimizeDebugEntry() {
    }

    /**
     * 几何优化那一行（模型数 / 骨骼遍历耗时与占比 / 顶点数）。
     *
     * <p>每调一次就取走本帧的采样并开始下一帧的累计 —— 见
     * {@link RenderOptimizeStats#pollLine(long, long, String)}。F3 关着时不会被调用，
     * 重新打开的第一帧会因为断档被丢弃，不会看到一坨攒了很久的数字。</p>
     */
    public static String line() {
        return RenderOptimizeStats.pollLine(FrameTimeStats.lastFrameNanos(), 0L, null);
    }

    /** 整帧的帧时间与 1% low，见 {@link FrameTimeStats#line()}。 */
    public static String frameLine() {
        return FrameTimeStats.line();
    }
}
