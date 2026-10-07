package com.linweiyun.genshin.content.entities.teyvat.skill.vesna;

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

/**
 * 薇斯娜攻击投射物渲染器 —— 渲染成绿色经验球样式。
 */
public class VesnaAttackProjectileRenderer extends EntityRenderer<VesnaAttackProjectile> {

    /** 经验球纹理（使用原版经验球贴图） */
    private static final ResourceLocation ORB_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/experience/experience_orb.png");

    /** 渲染类型：半透明、可剔除 */
    private static final RenderType RENDER_TYPE = RenderType.itemEntityTranslucentCull(ORB_TEXTURE);

    /** 绿色经验球颜色（RGB: 0x00FF00） */
    private static final int GREEN_COLOR = 0x00FF00;

    public VesnaAttackProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.15F;
        this.shadowStrength = 0.75F;
    }

    @Override
    protected int getBlockLightLevel(VesnaAttackProjectile entity, BlockPos blockPos) {
        // 经验球自带发光效果，+7亮度
        return Mth.clamp(super.getBlockLightLevel(entity, blockPos) + 7, 0, 15);
    }

    @Override
    public void render(VesnaAttackProjectile entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        // 计算纹理UV坐标：图标表 4×4，固定取左上角那一格
        int icon = 0;
        float u0 = (icon % 4 * 16) / 64.0F;
        float u1 = u0 + 16.0F / 64.0F;
        float v0 = (icon / 4 * 16) / 64.0F;
        float v1 = v0 + 16.0F / 64.0F;

        // 获取颜色
        int color = GREEN_COLOR;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        // 变换矩阵：上移、面向相机、缩放
        poseStack.translate(0.0F, 0.1F, 0.0F);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.scale(0.3F, 0.3F, 0.3F);

        VertexConsumer buffer = bufferSource.getBuffer(RENDER_TYPE);
        PoseStack.Pose pose = poseStack.last();
        vertex(buffer, pose, -0.5F, -0.25F, r, g, b, u0, v1, packedLight);
        vertex(buffer, pose, 0.5F, -0.25F, r, g, b, u1, v1, packedLight);
        vertex(buffer, pose, 0.5F, 0.75F, r, g, b, u1, v0, packedLight);
        vertex(buffer, pose, -0.5F, 0.75F, r, g, b, u0, v0, packedLight);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
    }

    /**
     * 添加顶点到缓冲区。
     */
    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose,
                               float x, float y, int r, int g, int b,
                               float u, float v, int light) {
        buffer.addVertex(pose, x, y, 0.0F)
                .setColor(r, g, b, 128)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(VesnaAttackProjectile entity) {
        return ORB_TEXTURE;
    }
}
