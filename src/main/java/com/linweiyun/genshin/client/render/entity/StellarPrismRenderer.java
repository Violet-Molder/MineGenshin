package com.linweiyun.genshin.client.render.entity;

import com.linweiyun.genshin.content.entities.area.StellarPrismEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.FallingBlockRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/** 棱镜渲染器：原版冰块方块，边长 0.5 米，三轴自转。 */
public class StellarPrismRenderer
        extends EntityRenderer<StellarPrismEntity, StellarPrismRenderer.PrismRenderState> {

    private static final float PRISM_SCALE = 0.5F;

    public StellarPrismRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
    }

    public static class PrismRenderState extends FallingBlockRenderState {
        public float yaw;
        public float pitch;
        public float roll;
    }

    @Override
    public PrismRenderState createRenderState() {
        return new PrismRenderState();
    }

    @Override
    public void extractRenderState(StellarPrismEntity entity, PrismRenderState state, float partialTicks) {
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

        state.yaw = entity.spinYaw(partialTicks);
        state.pitch = entity.spinPitch(partialTicks);
        state.roll = entity.spinRoll(partialTicks);
    }

    @Override
    public void submit(PrismRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        BlockState blockState = state.movingBlockRenderState.blockState;
        if (blockState.getRenderShape() != RenderShape.MODEL) {
            return;
        }

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.roll));
        poseStack.scale(PRISM_SCALE, PRISM_SCALE, PRISM_SCALE);
        poseStack.translate(-0.5D, -0.5D, -0.5D);
        collector.submitMovingBlock(poseStack, state.movingBlockRenderState, state.outlineColor);
        poseStack.popPose();

        super.submit(state, poseStack, collector, camera);
    }
}
