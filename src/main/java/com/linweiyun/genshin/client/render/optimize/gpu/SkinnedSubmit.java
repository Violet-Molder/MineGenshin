package com.linweiyun.genshin.client.render.optimize.gpu;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jspecify.annotations.Nullable;

/**
 * 一帧里"某个角色的 GPU 蒙皮绘制请求"。
 *
 * <p>刻意不实现 {@code BatchableSubmit}：合批提交会被按 batchKey 拆成多个绘制组，
 * 而 GPU 蒙皮的顶点缓冲是每个模型一块、骨骼矩阵每帧一块，本来就不该与其他提交混排。
 *
 * <p>管线按 {@code renderType} 派生：同一个模型可能是 cutout、也可能是 solid / translucent，
 * 各自的深度、混合、shader define 都必须与它平时走的那条原版管线一致，所以这里存的是
 * 「这次绘制该用哪条 skinned 管线」，而不是一个全局常量（见 {@link SkinnedPipelines#skinnedFor}）。
 */
public final class SkinnedSubmit implements SubmitNode {
    private final SkinnedMesh mesh;
    private final GpuBufferSlice skinData;
    private final int[] runs;
    private final int runCount;
    private final RenderType renderType;
    private final RenderPipeline pipeline;
    private @Nullable PreparedRenderType prepared;

    public SkinnedSubmit(SkinnedMesh mesh, GpuBufferSlice skinData, int[] runs, int runCount, RenderType renderType,
                         RenderPipeline pipeline) {
        this.mesh = mesh;
        this.skinData = skinData;
        this.runs = runs;
        this.runCount = runCount;
        this.renderType = renderType;
        this.pipeline = pipeline;
    }

    @Override
    public FeatureRendererType<SkinnedSubmit> featureType() {
        return SkinnedFeatureRenderer.TYPE;
    }

    public SkinnedMesh mesh() {
        return this.mesh;
    }

    public GpuBufferSlice skinData() {
        return this.skinData;
    }

    /**
     * 前序遍历里连续可见顶点区间，每两个 int 一条：[首顶点下标, 顶点数]。
     * 用扁平数组而不是 List，是因为提交数量与攻击频率同阶，这里每帧都会走很多次。
     */
    public int[] runs() {
        return this.runs;
    }

    public int runCount() {
        return this.runCount;
    }

    /** 这次绘制用的 skinned 管线（由基管线派生，见 {@link SkinnedPipelines#skinnedFor}）。 */
    public RenderPipeline pipeline() {
        return this.pipeline;
    }

    public @Nullable PreparedRenderType prepared() {
        return this.prepared;
    }

    /**
     * 在 prepare 阶段抓取当时的动态变换 / 裁剪状态 / 纹理。
     *
     * <p>异常吞掉只留 null：prepare 期间设备状态由外部持有，这里任何异常都不该把整个渲染循环带崩，
     * 只会让这一次绘制被跳过（渲染器仍可继续）。
     */
    public void prepare() {
        try {
            this.prepared = this.renderType.prepare();
        } catch (Throwable t) {
            this.prepared = null;
        }
    }
}
