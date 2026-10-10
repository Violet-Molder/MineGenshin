package com.linweiyun.genshin.client.render.entity;

import com.linweiyun.genshin.content.entities.area.StellarPrismEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

public class StellarPrismRenderer extends EntityRenderer<StellarPrismEntity> {

    private static final float PRISM_SCALE = 0.5F;

    public StellarPrismRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
    }

    @Override
    public void render(StellarPrismEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        BlockState blockState = entity.blockState();
        if (blockState.getRenderShape() != RenderShape.MODEL) {
            return;
        }
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(entity.spinYaw(partialTicks)));
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.spinPitch(partialTicks)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.spinRoll(partialTicks)));
        poseStack.scale(PRISM_SCALE, PRISM_SCALE, PRISM_SCALE);
        poseStack.translate(-0.5D, -0.5D, -0.5D);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                blockState, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(StellarPrismEntity entity) {
        return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
    }
}