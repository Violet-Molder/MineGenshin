package com.linweiyun.genshin.content.entities.teyvat.skill.miyabi;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 斩击的渲染 —— 模型放大 {@value #SCALE} 倍、按飞行方向摆正，用不吃光照也不剔背面的
 * {@link RenderType#eyes} 画。
 *
 * <p>这三项是素材侧的表现口径，照搬过来斩击的形状与朝向才对得上。
 */
public class MiyabiSlashEffectRenderer extends GeoEntityRenderer<MiyabiSlashEffect> {

    private static final float SCALE = 3.0F;

    public MiyabiSlashEffectRenderer(EntityRendererProvider.Context context) {
        super(context, new MiyabiSlashGeoModel());
    }

    @Override
    public RenderType getRenderType(MiyabiSlashEffect animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.eyes(texture);
    }

    @Override
    public void preRender(PoseStack poseStack, MiyabiSlashEffect animatable, BakedGeoModel model,
                          @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        poseStack.scale(SCALE, SCALE, SCALE);
        poseStack.mulPose(Axis.YP.rotationDegrees(animatable.getYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(-animatable.getXRot()));

        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, colour);
    }
}
