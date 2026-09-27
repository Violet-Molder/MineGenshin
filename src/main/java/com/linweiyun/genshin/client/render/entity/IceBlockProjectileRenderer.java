package com.linweiyun.genshin.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.linweiyun.genshin.content.entities.misc.IceBlockProjectile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.FallingBlockRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 悬空冰块的渲染器 —— 把 {@link IceBlockProjectile#blockState()} 那个原版方块画出来，并让它转起来。
 *
 * <h2>怎么画一个方块</h2>
 * 完全照抄原版 {@code FallingBlockRenderer}：渲染状态用
 * {@link FallingBlockRenderState}（里面自带一个 {@code MovingBlockRenderState} 描述「哪里、什么方块、什么光照」），
 * 提交时走 {@code SubmitNodeCollector#submitMovingBlock}。
 * 自己额外做的只有两件事：<b>多转两圈</b>、把方块中心对齐到实体位置。
 *
 * <p>绕 Y 轴转主角度（像陀螺），再叠一个 0.7 倍的 X 轴旋转 —— 只转 Y 会像旋转木马，
 * 叠一个斜轴才有「翻滚着砸过来」的感觉。
 */
public class IceBlockProjectileRenderer
        extends EntityRenderer<IceBlockProjectile, IceBlockProjectileRenderer.IceBlockRenderState> {

    public IceBlockProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
    }

    /** 在方块渲染状态上再挂一个自转角与缩放。 */
    public static class IceBlockRenderState extends FallingBlockRenderState {
        public float spin;
        public float scale = 1.0F;
    }

    @Override
    public IceBlockRenderState createRenderState() {
        return new IceBlockRenderState();
    }

    @Override
    public void extractRenderState(IceBlockProjectile entity, IceBlockRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);

        BlockPos pos = BlockPos.containing(entity.getX(), entity.getBoundingBox().maxY, entity.getZ());
        state.movingBlockRenderState.randomSeedPos = pos;
        state.movingBlockRenderState.blockPos = pos;
        state.movingBlockRenderState.blockState = entity.blockState();
        if (entity.level() instanceof ClientLevel clientLevel) {
            state.movingBlockRenderState.biome = clientLevel.getBiome(pos);
            state.movingBlockRenderState.cardinalLighting = clientLevel.cardinalLighting();
            state.movingBlockRenderState.lightEngine = clientLevel.getLightEngine();
        }

        state.spin = entity.spinDegrees(partialTicks);
        state.scale = entity.scale();
    }

    @Override
    public void submit(IceBlockRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        BlockState blockState = state.movingBlockRenderState.blockState;
        if (blockState.getRenderShape() != RenderShape.MODEL) {
            return;
        }

        poseStack.pushPose();
        // 实体位置 = 方块底面中心（和原版掉落方块一致），先抬到方块中心再转
        poseStack.translate(0.0D, 0.5D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.spin));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.spin * 0.7F));
        // 缩放放在最后：绕方块中心缩，冰块和冰刺共用同一条渲染路径
        poseStack.scale(state.scale, state.scale, state.scale);
        // 方块模型占 (0,0,0)~(1,1,1)，平移半个方块让旋转中心落在方块中心
        poseStack.translate(-0.5D, -0.5D, -0.5D);

        collector.submitMovingBlock(poseStack, state.movingBlockRenderState, state.outlineColor);
        poseStack.popPose();

        super.submit(state, poseStack, collector, camera);
    }
}
