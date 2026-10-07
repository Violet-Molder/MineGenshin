package com.linweiyun.genshin.client.render.entity;

import com.linweiyun.genshin.content.entities.area.ThunderCloudEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public class ThunderCloudRenderer extends EntityRenderer<ThunderCloudEntity> {

    public ThunderCloudRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.shadowStrength = 0.0F;
    }

    @Override
    protected int getBlockLightLevel(ThunderCloudEntity entity, BlockPos blockPos) {
        return 15;
    }

    @Override
    public void render(ThunderCloudEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(ThunderCloudEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
