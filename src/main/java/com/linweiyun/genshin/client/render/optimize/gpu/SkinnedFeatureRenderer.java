package com.linweiyun.genshin.client.render.optimize.gpu;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.neoforged.neoforge.client.event.RegisterFeatureRenderersEvent;
import org.slf4j.Logger;

/**
 * 把 {@link SkinnedSubmit} 真正画出来。
 *
 * <p>分组方式照抄 {@code RenderTypeFeatureRenderer}：prepare 阶段收集、执行阶段按下标取组。
 * 与它的差别是这里不写动态顶点缓冲——顶点早在编译期就常驻显存，每帧只更新骨骼矩阵。
 *
 * <p>整组绘制共用一条 command buffer，并在首尾打一对 GPU 时间戳（见 {@link SkinnedGpuTimer}）：
 * 「CPU 省了多少」看 {@code walk}，「GPU 有没有变贵」看这一对时间戳，两者互不替代。
 */
public final class SkinnedFeatureRenderer implements FeatureRenderer<SkinnedSubmit> {
    public static final FeatureRendererType<SkinnedSubmit> TYPE = FeatureRendererType.create("minegenshin:skinned");
    private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);
    private final List<List<SkinnedSubmit>> groups = new ArrayList<>();
    /** 任一次执行抛异常就永久停用 GPU 路径，之后全部回退 CPU，避免每帧刷屏 + 反复崩帧。 */
    private static volatile boolean disabled;
    /** 渲染器是否真的注册进了 FeatureRenderDispatcher；没注册就提交节点会在 prepare 阶段抛异常。 */
    private static volatile boolean registered;

    public SkinnedFeatureRenderer() {
        // 构造即意味着"马上要被注册"：注册入口只有本类的 register 一种，这里先置位，
        // 让 isDisabled() 在注册完成后立刻放行 GPU 路径。
        registered = true;
    }

    public static boolean isDisabled() {
        return disabled || !registered;
    }

    public static void register(RegisterFeatureRenderersEvent event) {
        event.register(TYPE, new SkinnedFeatureRenderer());
    }

    @Override
    public void prepareGroup(FeatureFrameContext context, List<SkinnedSubmit> submits, boolean strictlyOrdered) {
        List<SkinnedSubmit> prepared = new ArrayList<>(submits.size());

        for (SkinnedSubmit submit : submits) {
            submit.prepare();
            if (submit.prepared() != null) {
                prepared.add(submit);
            }
        }

        // 即使一个都没成功也要占住下标：executeGroup 是按 groupIndex 取组的。
        this.groups.add(prepared);
    }

    @Override
    public void executeGroup(FeatureFrameContext context, int groupIndex, List<SkinnedSubmit> submits, boolean strictlyOrdered) {
        if (groupIndex >= this.groups.size()) {
            return;
        }

        final List<SkinnedSubmit> group = this.groups.get(groupIndex);
        if (group.isEmpty()) {
            return;
        }

        // 整组共用一只 encoder：开始 / 结束两个时间戳与这一组的 render pass 因此落在同一条
        // command buffer 上，时间戳包围的就是本模组这批绘制的 GPU 时间（与 vanilla TimerQuery 同构）。
        final CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        SkinnedGpuTimer.begin(encoder);

        for (SkinnedSubmit submit : group) {
            try {
                this.draw(submit, encoder);
            } catch (Throwable t) {
                disabled = true;
                LOGGER.warn("GPU 蒙皮绘制失败，已永久回退到 CPU 蒙皮路径", t);
                break; // 跳出后仍要落结束时间戳，否则时间戳会缺一半、这一格只能被丢掉
            }
        }

        SkinnedGpuTimer.end(encoder);
    }

    @Override
    public void finishExecute(FeatureFrameContext context) {
        this.groups.clear();
    }

    private void draw(SkinnedSubmit submit, CommandEncoder encoder) {
        PreparedRenderType prepared = submit.prepared();

        if (prepared == null) {
            return;
        }

        RenderTarget renderTarget = prepared.outputTarget().getRenderTarget();
        GpuTextureView colorTexture = RenderSystem.outputColorTextureOverride != null
                ? RenderSystem.outputColorTextureOverride
                : renderTarget.getColorTextureView();
        GpuTextureView depthTexture = renderTarget.useDepth
                ? (RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : renderTarget.getDepthTextureView())
                : null;
        // 顺序索引缓冲按「最长的一条 run」申请就够：整个网格的索引数只是上界，
        // 骨骼被藏起来时（第一人称藏头、挂点层 skipChildrenRender）实际要画的往往只有一小段。
        final int neededIndices = longestRunIndices(submit);
        if (neededIndices <= 0) {
            return;
        }

        RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indexBuffer = sequential.getBuffer(neededIndices);
        IndexType indexType = sequential.type();

        // 注意：encoder 由 executeGroup 传入，和 GPU 时间戳同属一条 command buffer。
        try (RenderPass renderPass = encoder
                .createRenderPass(() -> "MineGenshin skinned entity", colorTexture, Optional.empty(), depthTexture, OptionalDouble.empty())) {
            // 管线来自本次提交：它是由「这个 RenderType 平时用的那条原版管线」派生的
            // （见 SkinnedPipelines#skinnedFor），所以深度 / 混合 / define 都与原路径一致。
            renderPass.setPipeline(submit.pipeline());

            if (prepared.scissorState().enabled()) {
                renderPass.enableScissor(
                        prepared.scissorState().x(),
                        prepared.scissorState().y(),
                        prepared.scissorState().width(),
                        prepared.scissorState().height());
            }

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", prepared.dynamicTransforms());
            renderPass.setUniform("SkinData", submit.skinData());
            renderPass.setVertexBuffer(0, submit.mesh().buffer().slice());

            for (PreparedRenderType.Texture texture : prepared.textures()) {
                renderPass.bindTexture(texture.name(), texture.textureView(), texture.sampler());
            }

            renderPass.setIndexBuffer(indexBuffer, indexType);

            // 顺序索引缓冲内是相对索引（i,i+1,i+2,i+2,i+3,i），所以把区间首顶点当作 baseVertex 即可。
            for (int i = 0; i < submit.runCount(); i++) {
                int firstVertex = submit.runs()[2 * i];
                int quads = submit.runs()[2 * i + 1] / 4;

                if (quads <= 0) {
                    continue;
                }

                renderPass.drawIndexed(quads * 6, 1, 0, firstVertex, 0);
            }
        }
    }

    /** {@code runs} 里最长一条要画的索引数（4 个顶点一个 QUAD，一个 QUAD 6 个索引）。 */
    private static int longestRunIndices(SkinnedSubmit submit) {
        int longestQuads = 0;
        final int[] runs = submit.runs();
        for (int i = 0; i < submit.runCount(); i++) {
            final int quads = runs[2 * i + 1] / 4;
            if (quads > longestQuads) {
                longestQuads = quads;
            }
        }
        return longestQuads * 6;
    }
}
