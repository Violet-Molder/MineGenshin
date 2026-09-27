package com.linweiyun.genshin.client.render.optimize;

import com.linweiyun.genshin.client.render.optimize.gpu.SkinnedGpuTimer;
import com.linweiyun.genshin.client.render.optimize.gpu.SkinnedPipelines;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * 把角色几何优化的每帧读数挂进 F3 调试屏。
 *
 * <p>与飘字的 {@code IndicatorDebugEntry} 同一套做法：走 NeoForge 26.2 的
 * {@code RegisterDebugEntriesEvent} 正式扩展点，挂在默认档案的 {@code IN_OVERLAY}，
 * 按 F3 就能看到，也可以在 F3 的调试选项菜单里单独关掉。
 * 服务器要求精简调试信息（{@code reducedDebugInfo}）时不显示。</p>
 *
 * <p>{@code display} 每帧被调用一次，正好充当读数采样点 —— 见
 * {@link RenderOptimizeStats#pollLine(long, long, String)}。</p>
 *
 * <p>这里输出<b>两行</b>：第一行是几何优化自己的读数（模型数 / 骨骼遍历耗时与占比 /
 * GPU 时间 / 顶点数），第二行是整帧的帧时间与 1% low（{@link FrameTimeStats}）——
 * 前者回答「这段优化省了多少」，后者回答「手感有没有变差」，两者一起看才知道改动是不是真的划算。</p>
 */
public final class RenderOptimizeDebugEntry implements DebugScreenEntry {

    @Override
    public void display(DebugScreenDisplayer displayer,
                        @Nullable Level serverOrClientLevel,
                        @Nullable LevelChunk clientChunk,
                        @Nullable LevelChunk serverChunk) {
        if (!RenderOptimize.statsEnabled()) {
            return;
        }

        // GPU 时间来自时间戳查询：值要等 GPU 执行完才取得到（内部三帧轮转），取不到时返回 0，
        // 那一栏就不显示 —— 不阻塞、不影响这一帧的其它读数。
        // 「没值」有两种成因：设备不支持时间戳查询，或者 GPU 蒙皮被环境挡下（光影包 / 管线被改）。
        // 后者由 SkinnedPipelines 给出一个短标记，直接写在读数里，免得把「让位」误读成「设备不支持」。
        final long skinnedGpuNanos = SkinnedGpuTimer.nanos();
        displayer.addLine(RenderOptimizeStats.pollLine(FrameTimeStats.lastFrameNanos(), skinnedGpuNanos,
                SkinnedPipelines.suppressionNote()));
        displayer.addLine(FrameTimeStats.line());
    }

    @Override
    public boolean isAllowed(boolean reducedDebugInfo) {
        return !reducedDebugInfo;
    }
}
