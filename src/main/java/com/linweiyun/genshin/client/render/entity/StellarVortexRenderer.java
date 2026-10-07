package com.linweiyun.genshin.client.render.entity;

import com.linweiyun.genshin.config.WorldTextColorConfig;
import com.linweiyun.genshin.content.entities.area.StellarVortexEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class StellarVortexRenderer extends EntityRenderer<StellarVortexEntity> {

    private static final ResourceLocation ORB_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/experience/experience_orb.png");
    private static final RenderType RENDER_TYPE = RenderType.itemEntityTranslucentCull(ORB_TEXTURE);

    private static final float BASE_SCALE = 0.3F;
    private static final float LEVEL3_SCALE = 0.9F;

    public StellarVortexRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.15F;
        this.shadowStrength = 0.75F;
    }

    @Override
    protected int getBlockLightLevel(StellarVortexEntity entity, BlockPos blockPos) {
        return Mth.clamp(super.getBlockLightLevel(entity, blockPos) + 7, 0, 15);
    }

    @Override
    public void render(StellarVortexEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        // 经验球图标表 4×4，这里固定取左上角那一格
        int icon = 0;
        float u0 = (icon % 4 * 16) / 64.0F;
        float u1 = u0 + 16.0F / 64.0F;
        float v0 = (icon / 4 * 16) / 64.0F;
        float v1 = v0 + 16.0F / 64.0F;

        boolean level3 = entity.getVortexLevel() >= 3;
        int color = level3 ? getLevel3Color() : getDefaultColor();
        float scale = level3 ? LEVEL3_SCALE : BASE_SCALE;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int alpha = 180;

        poseStack.translate(0.0F, 0.1F, 0.0F);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.scale(scale, scale, scale);

        VertexConsumer buffer = bufferSource.getBuffer(RENDER_TYPE);
        PoseStack.Pose pose = poseStack.last();
        vertex(buffer, pose, -0.5F, -0.25F, r, g, b, alpha, u0, v1, packedLight);
        vertex(buffer, pose, 0.5F, -0.25F, r, g, b, alpha, u1, v1, packedLight);
        vertex(buffer, pose, 0.5F, 0.75F, r, g, b, alpha, u1, v0, packedLight);
        vertex(buffer, pose, -0.5F, 0.75F, r, g, b, alpha, u0, v0, packedLight);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose,
                               float x, float y, int r, int g, int b, int a,
                               float u, float v, int light) {
        buffer.addVertex(pose, x, y, 0.0F)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(StellarVortexEntity entity) {
        return ORB_TEXTURE;
    }

    private static int getDefaultColor() {
        return parseColor(WorldTextColorConfig.ANEMO_COLOR.get());
    }

    private static int getLevel3Color() {
        return parseColor(WorldTextColorConfig.CYRO_COLOR.get());
    }

    private static int parseColor(String hex) {
        try {
            return Integer.decode(hex.startsWith("#") ? hex : "#" + hex);
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }
}
