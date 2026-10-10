package com.linweiyun.genshin.content.entities.teyvat.skill.miyabi;

import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * 斩击的渲染 —— 模型放大 {@value #SCALE} 倍、按飞行方向摆正，用不吃光照也不剔背面的
 * {@link RenderTypes#eyes} 画。
 *
 * <p>这三项是素材侧的表现口径，照搬过来斩击的形状与朝向才对得上。
 */
public class MiyabiSlashEffectRenderer
        extends GeoEntityRenderer<MiyabiSlashEffect, MiyabiSlashEffectRenderer.SlashRenderState> {

    private static final float SCALE = 3.0F;

    /** 实体渲染状态 —— GeckoLib 的渲染管线要求它同时是 {@link GeoRenderState}。 */
    public static class SlashRenderState extends EntityRenderState implements GeoRenderState {
    }

    public MiyabiSlashEffectRenderer(EntityRendererProvider.Context context) {
        super(context, new MiyabiSlashGeoModel());
        withScale(SCALE);
    }

    @Override
    public SlashRenderState createRenderState(MiyabiSlashEffect animatable, Void relatedObject) {
        return new SlashRenderState();
    }

    @Override
    public RenderType getRenderType(SlashRenderState renderState, Identifier texture) {
        return RenderTypes.eyes(texture);
    }

    @Override
    public void adjustRenderPose(RenderPassInfo<SlashRenderState> renderPassInfo) {
        PoseStack poseStack = renderPassInfo.poseStack();
        float yaw = renderPassInfo.getOrDefaultGeckolibData(DataTickets.ENTITY_YAW, 0.0F);
        float pitch = renderPassInfo.getOrDefaultGeckolibData(DataTickets.ENTITY_PITCH, 0.0F);

        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F + yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
        poseStack.translate(0, 0.01F, 0);
    }
}
