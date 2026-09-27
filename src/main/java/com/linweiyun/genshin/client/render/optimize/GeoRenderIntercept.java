package com.linweiyun.genshin.client.render.optimize;

import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.GeoRendererInternals;
import com.geckolib.renderer.base.RenderPassInfo;
import com.linweiyun.genshin.client.render.optimize.geo.CompiledGeoModel;
import com.linweiyun.genshin.client.render.optimize.geo.GeoCompileCache;
import com.linweiyun.genshin.client.render.optimize.gpu.BoneMatrixPalette;
import com.linweiyun.genshin.client.render.optimize.gpu.SkinnedFeatureRenderer;
import com.linweiyun.genshin.client.render.optimize.gpu.SkinnedMesh;
import com.linweiyun.genshin.client.render.optimize.gpu.SkinnedMeshCache;
import com.linweiyun.genshin.client.render.optimize.gpu.SkinnedPipelines;
import com.linweiyun.genshin.client.render.optimize.gpu.SkinnedSubmit;
import com.linweiyun.genshin.client.render.optimize.walk.BoneWalker;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.neoforged.neoforge.client.submit.RenderPhaseKey;
import net.neoforged.neoforge.client.submit.RenderPhaseKeys;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * 几何提交的总接管点：所有 GeckoLib 渲染器的 {@code submitRenderTasks} 都从这里决定
 * 「走 GPU 蒙皮 / 走 CPU 优化 / 回退 GeckoLib 原路径」。
 *
 * <h2>为什么是这里，而不是每个渲染器各写一遍</h2>
 * GeckoLib 的几何提交统一收在 {@link GeoRenderer#submitRenderTasks} 这一个<b>接口 default 方法</b>里
 * （{@code GeoRenderer.java:172-195}）：{@code GeoEntityRenderer} / {@code GeoReplacedEntityRenderer} /
 * {@code GeoObjectRenderer} / {@code GeoBlockRenderer} / {@code GeoItemRenderer} 都不覆写它，
 * 上游的 {@code performRenderPass}（{@code GeoRenderer.java:125}）也只调这一处；
 * 连内置层 {@code TextureLayerGeoLayer} 的重绘也是回过头来调
 * {@code renderer.submitRenderTasks(...)}（{@code TextureLayerGeoLayer.java:70}）。
 * 所以本模组用一个 mixin 在这条 default 方法的 HEAD 处注入（见
 * {@code mixin/mixins/GeoRendererSubmitTasksMixin}），把整段逻辑交给这里 ——
 * 角色、本模组实体、其它模组的 GeckoLib 实体因此走的是同一份代码。
 *
 * <p><b>刻意不管的一处</b>：{@code GeoArmorRenderer} 自己覆写了 {@code submitRenderTasks}
 * （{@code GeoArmorRenderer.java:264}），它按护甲槽位逐段挑骨骼来画，几何并不是「整个模型」。
 * 覆写者不会走接口 default 方法，所以护甲渲染保持 GeckoLib 原样 —— 这正合适：
 * 护甲用的 {@code ARMOR_*} 管线本来也不在 skinned 白名单里。</p>
 *
 * <h2>等价性从哪来</h2>
 * 默认实现本身很短：{@code renderType == null} 直接不提交；missing model 交给
 * {@code submitMissingModelRender}；否则把「几何生成」包进
 * {@code submitCustomGeometry} 的延迟回调，并在回调里用 {@code renderPosed} 应用本趟的
 * {@code BoneSnapshot}（动画、骨骼显隐、挂点层的 skipRender 都靠它）。
 * 这里<b>只替换回调里那句 {@code model.render(...)}</b>：外层
 * 「null 检查 → missing model → submitCustomGeometry → push/set/renderPosed/pop」与默认实现逐句相同
 * （见 {@link #submitGeometry} 与 {@link #submitDefault}），
 * 回退时调用的也正是原来那一句 {@code model.render(...)}。
 *
 * <h2>三类接管结果</h2>
 * <ul>
 *   <li><b>GPU 蒙皮</b>：顶点常驻显存，每帧只把骨骼矩阵写进常量缓冲；
 *       条件下满足才走，且管线必须能从该 RenderType 派生出来（见
 *       {@link SkinnedPipelines#skinnedFor}）；</li>
 *   <li><b>CPU 优化</b>：几何预编译 + 零分配骨骼遍历 + 顶点直写
 *       （{@link BoneWalker#render}），提交结构与默认实现完全一致；</li>
 *   <li><b>回退</b>：优化总开关关掉、模型编译不了、missing model 时，
 *       按 GeckoLib 原路径提交（前两者在本方法内直接用 {@code model.render}；
 *       missing model 返回 {@code false}，由调用方走 GeckoLib 自己的
 *       {@code submitMissingModelRender}，这样任何子类覆写都仍然生效）。</li>
 * </ul>
 */
public final class GeoRenderIntercept {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);

    /** GPU 蒙皮提交阶段出过一次错就只打一条日志，避免每帧刷屏。 */
    private static boolean gpuSubmitWarned;

    private GeoRenderIntercept() {
    }

    /**
     * 尝试接管一次几何提交。
     *
     * @return {@code true} = 已经处理完这次提交（调用方不要再走 GeckoLib 原路径）；
     *         {@code false} = 什么都没提交，调用方应按 GeckoLib 原路径继续
     *         （目前只有 missing model 一种情况）
     */
    public static boolean trySubmit(RenderPassInfo<?> renderPassInfo, OrderedSubmitNodeCollector renderTasks,
                                    @Nullable RenderType renderType) {
        // 与默认实现一致：renderType 为 null 表示这次渲染本来就不提交任何东西。
        if (renderType == null) {
            return true;
        }

        final BakedGeoModel model = renderPassInfo.model();
        if (model.isMissingno()) {
            // 交回调用方：GeckoLib 的 submitMissingModelRender 是 default 方法，别的渲染器可能覆写它，
            // 在这里复刻会绕过那些覆写。
            return false;
        }

        final int flags = RenderOptimize.characterFlags();
        final CompiledGeoModel compiled = flags == 0 ? null : GeoCompileCache.get(model);
        if (compiled == null && flags != 0) {
            RenderOptimizeStats.recordFallback();
        }

        final int packedLight = renderPassInfo.packedLight();
        final int packedOverlay = renderPassInfo.packedOverlay();
        final int renderColor = renderPassInfo.renderColor();

        // GPU 蒙皮分支：顶点常驻显存、每帧只上传骨骼矩阵，能用就整段接管。
        // 条件不满足（开关关掉 / 出过错 / 该 RenderType 派生不出 skinned 管线）就落到下面的 CPU 路径。
        if (compiled != null && canSkinOnGpu(renderType)
                && submitSkinnedRender(renderPassInfo, renderTasks, renderType, compiled,
                        packedLight, packedOverlay, renderColor)) {
            return true;
        }

        submitGeometry(renderPassInfo, renderTasks, renderType, compiled, packedLight, packedOverlay, renderColor, flags);
        return true;
    }

    /**
     * GeckoLib 默认实现的逐句复刻，供「调用不了 {@code super}」的调用方在
     * {@link #trySubmit} 返回 {@code false} 时使用（{@code CharacterRenderer} 就是这种情况：
     * 它的直接父类是 {@code GeoObjectRenderer} 这个类，Java 不允许它写
     * {@code GeoRenderer.super.submitRenderTasks(...)}）。
     *
     * <p>对接口注入的 mixin 路径用不到这个方法：那边不取消注入就等价于执行了这段代码。</p>
     */
    public static void submitDefault(RenderPassInfo<?> renderPassInfo, OrderedSubmitNodeCollector renderTasks,
                                     @Nullable RenderType renderType) {
        if (renderType == null) {
            return;
        }

        final BakedGeoModel model = renderPassInfo.model();
        if (model.isMissingno()) {
            submitMissingModel(renderPassInfo, renderTasks);
            return;
        }

        submitGeometry(renderPassInfo, renderTasks, renderType, null,
                renderPassInfo.packedLight(), renderPassInfo.packedOverlay(), renderPassInfo.renderColor(), 0);
    }

    /**
     * 「把几何生成包进 submitCustomGeometry 的延迟回调」这一段本身。
     *
     * <p>结构与 {@code GeoRenderer.java:187-194} 逐句相同，只有回调体按 {@code compiled} 分流。
     * pose 的 push/restore/pop 必须留在原位置：{@code renderPosed} 之前要把「提交时捕获的位姿」
     * 装回栈上，它之后要把栈还原，否则挂点层与同帧后续的渲染都会串位。</p>
     */
    private static void submitGeometry(RenderPassInfo<?> renderPassInfo, OrderedSubmitNodeCollector renderTasks,
                                       RenderType renderType, @Nullable CompiledGeoModel compiled,
                                       int packedLight, int packedOverlay, int renderColor, int flags) {
        renderTasks.submitCustomGeometry(renderPassInfo.poseStack(), renderType, (pose, vertexConsumer) -> {
            final PoseStack poseStack = renderPassInfo.poseStack();

            poseStack.pushPose();
            poseStack.last().set(pose);

            renderPassInfo.renderPosed(() -> {
                if (compiled == null) {
                    // 优化没生效（开关全关 / 编译不了）：原样走 GeckoLib 的模型渲染。
                    renderPassInfo.model().render(renderPassInfo, vertexConsumer, packedLight, packedOverlay, renderColor);
                    return;
                }

                final long began = System.nanoTime();
                final int written = BoneWalker.render(compiled, poseStack, renderPassInfo, vertexConsumer,
                        packedLight, packedOverlay, renderColor, flags);
                RenderOptimizeStats.record(System.nanoTime() - began, written);
            });

            poseStack.popPose();
        });
    }

    /**
     * missing model 的提交：逐句复刻 {@link GeoRendererInternals#submitMissingModelRender}
     * （{@code GeoRendererInternals.java:195-204}）—— 换用 missing 贴图的 cutout RenderType，
     * 其余 push/set/renderPosed/pop 与普通几何完全一样。
     */
    private static void submitMissingModel(RenderPassInfo<?> renderPassInfo, OrderedSubmitNodeCollector renderTasks) {
        renderTasks.submitCustomGeometry(renderPassInfo.poseStack(),
                RenderTypes.entityCutout(MissingTextureAtlasSprite.getLocation()),
                (pose, vertexConsumer) -> {
                    final PoseStack poseStack = renderPassInfo.poseStack();

                    poseStack.pushPose();
                    poseStack.last().set(pose);
                    renderPassInfo.renderPosed(() -> renderPassInfo.model().render(renderPassInfo, vertexConsumer,
                            renderPassInfo.packedLight(), renderPassInfo.packedOverlay(), renderPassInfo.renderColor()));
                    poseStack.popPose();
                });
    }

    /**
     * GPU 蒙皮的前置条件。
     *
     * <h2>为什么不再写死 {@code ENTITY_CUTOUT}</h2>
     * 以前只认 {@code RenderPipelines.ENTITY_CUTOUT}，于是实体的 solid / translucent / offset
     * 变体全都回退 CPU。现在判断条件是「该 RenderType 用的基管线能不能派生出一条 skinned 版本」
     * （{@link SkinnedPipelines#skinnedFor}），派生出来的管线连深度、混合、shader define
     * 都与它平时走的那条原版管线一致 —— 白名单之外（例如带 DISSOLVE 的变体）依旧返回 null，
     * 自然回退 CPU 优化路径，画面上不会出现「该剔除的没剔除、该发光的没发光」这类偏差。
     *
     * <p>此外 {@link SkinnedPipelines#skinnedFor} 还会过一道环境自检：光影包生效、或管线的
     * 顶点格式 / 顶点着色器被外部替换时同样返回 null。GPU 路径自带顶点格式，只有管线还是
     * 原版那条、派生管线也还是我们那条时才读得对；CPU 路径没有这个约束。</p>
     */
    private static boolean canSkinOnGpu(RenderType renderType) {
        return RenderOptimize.gpuSkinningEnabled()
                && !SkinnedFeatureRenderer.isDisabled()
                && SkinnedPipelines.skinnedFor(renderType.pipeline()) != null;
    }

    /**
     * 提交一次 GPU 蒙皮绘制。
     *
     * <h2>为什么骨骼矩阵要在「提交阶段」算</h2>
     * 顶点缓冲是常驻显存、整帧不变的，唯一按模型变化的是骨骼矩阵。它必须在提交阶段算出来写进常量缓冲，
     * 因为真正执行绘制时（{@code executeGroup}）已经处在「正在遍历本帧已提交列表」的循环里，
     * 那时再往提交列表里塞新节点是无效的。
     *
     * <p>{@code renderPosed} 只做两件事：把本趟的 {@code BoneSnapshot} 应用到骨骼上、挂上骨骼位置监听，
     * 然后执行传入的任务 —— 和 CPU 路径调用的是同一个方法、同一时机，所以动画与挂点层的行为完全一致。</p>
     *
     * @return 是否成功接管（{@code false} 表示调用方应继续走 CPU 路径）
     */
    private static boolean submitSkinnedRender(RenderPassInfo<?> renderPassInfo,
                                              OrderedSubmitNodeCollector renderTasks,
                                              RenderType renderType,
                                              CompiledGeoModel compiled,
                                              int packedLight, int packedOverlay, int renderColor) {
        final RenderPipeline pipeline = SkinnedPipelines.skinnedFor(renderType.pipeline());
        if (pipeline == null) {
            return false;
        }

        try {
            final SkinnedMesh mesh = SkinnedMeshCache.get(compiled);
            if (mesh == null) {
                return false;
            }

            final PoseStack poseStack = renderPassInfo.poseStack();
            final PoseStack.Pose pose = poseStack.last().copy();
            final int[] runs = new int[Math.max(2, compiled.boneCount * 2)];
            final int[] runCount = new int[1];
            final GpuBufferSlice[] palette = new GpuBufferSlice[1];

            poseStack.pushPose();
            poseStack.last().set(pose);
            try {
                renderPassInfo.renderPosed(() -> {
                    final long began = System.nanoTime();
                    final GpuBufferSlice slice = BoneMatrixPalette.compute(compiled, poseStack, renderPassInfo,
                            packedLight, packedOverlay, renderColor, runs, runCount);
                    if (slice != null) {
                        // 与 CPU 路径同口径：只算真正会画出来的顶点（骨骼被隐藏时少于模型顶点总数），
                        // 否则关掉/打开 gpu-skinning 时 F3 的 v 与 us/model 没法直接对照。
                        RenderOptimizeStats.recordGpu(System.nanoTime() - began, visibleVertices(runs, runCount[0]));
                    }
                    palette[0] = slice;
                });
            } finally {
                poseStack.popPose();
            }

            if (palette[0] == null || runCount[0] == 0) {
                return false;
            }

            renderTasks.submitSpecial(phaseFor(renderType),
                    new SkinnedSubmit(mesh, palette[0], runs, runCount[0], renderType, pipeline));
            return true;
        } catch (Throwable t) {
            // GPU 蒙皮出问题只影响这一帧的这一个模型：记一次回退，然后交给调用方走 CPU 路径重画。
            if (!gpuSubmitWarned) {
                gpuSubmitWarned = true;
                LOGGER.warn("[RenderOptimize] GPU 蒙皮提交失败，该模型本帧回退 CPU 路径（后续同样情况不再刷屏）", t);
            }
            RenderOptimizeStats.recordFallback();
            return false;
        }
    }

    /**
     * 这次提交该进哪个阶段。
     *
     * <p>必须与原版 {@code SubmitNodeCollection#submitCustomGeometry}（{@code SubmitNodeCollection.java:348-358}）
     * 的分流一致：描边 → outline；带混合（半透明）→ translucentCustomGeometry；其余 → solid。
     * 一律塞 solid 会让半透明几何在不透明阶段就被画掉（混合次序与深度写入都变了），
     * 而描边会完全跑到错误的时机。</p>
     */
    private static RenderPhaseKey<SubmitNode> phaseFor(RenderType renderType) {
        if (renderType.isOutline()) {
            return RenderPhaseKeys.OUTLINE;
        }
        if (renderType.hasBlending()) {
            return RenderPhaseKeys.TRANSLUCENT_CUSTOM_GEOMETRY;
        }
        return RenderPhaseKeys.SOLID;
    }

    /** 提交区间里实际会画出的顶点数（{@code runs} 每两个 int 一条：[首顶点, 顶点数]）。 */
    private static int visibleVertices(int[] runs, int runCount) {
        int visible = 0;
        for (int i = 0; i < runCount; i++) {
            visible += runs[2 * i + 1];
        }
        return visible;
    }
}
