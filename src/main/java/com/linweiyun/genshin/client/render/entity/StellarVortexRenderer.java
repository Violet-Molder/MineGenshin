package com.linweiyun.genshin.client.render.entity;

import com.linweiyun.genshin.config.WorldTextColorConfig;
import com.linweiyun.genshin.content.entities.area.StellarVortexEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ExperienceOrbRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class StellarVortexRenderer extends EntityRenderer<StellarVortexEntity, ExperienceOrbRenderState> {

    private static final Identifier ORB_TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/experience/experience_orb.png");
    private static final RenderType RENDER_TYPE =
            RenderTypes.entityTranslucentCullItemTarget(ORB_TEXTURE);

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
    private static final float BASE_SCALE = 0.3F;
    private static final float LEVEL3_SCALE = 0.9F;

    public StellarVortexRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.15F;
        this.shadowStrength = 0.75F;
    }

    private static class VortexRenderState extends ExperienceOrbRenderState {
        int color = 0x80FFD7;
        float scale = BASE_SCALE;
    }

    @Override
    protected int getBlockLightLevel(StellarVortexEntity entity, BlockPos blockPos) {
        return Mth.clamp(super.getBlockLightLevel(entity, blockPos) + 7, 0, 15);
    }

    @Override
    public void submit(ExperienceOrbRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        int icon = state.icon;
        float u0 = (icon % 4 * 16) / 64.0F;
        float u1 = u0 + 16.0F / 64.0F;
        float v0 = (icon / 4 * 16) / 64.0F;
        float v1 = v0 + 16.0F / 64.0F;

        VortexRenderState vState = (VortexRenderState) state;
        int color = vState.color;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int alpha = 180;

        poseStack.translate(0.0F, 0.1F, 0.0F);
        poseStack.mulPose(camera.orientation);
        poseStack.scale(vState.scale, vState.scale, vState.scale);

        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> {
            vertex(buffer, pose, -0.5F, -0.25F, r, g, b, alpha, u0, v1, state.lightCoords);
            vertex(buffer, pose, 0.5F, -0.25F, r, g, b, alpha, u1, v1, state.lightCoords);
            vertex(buffer, pose, 0.5F, 0.75F, r, g, b, alpha, u1, v0, state.lightCoords);
            vertex(buffer, pose, -0.5F, 0.75F, r, g, b, alpha, u0, v0, state.lightCoords);
        });
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
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
    public ExperienceOrbRenderState createRenderState() {
        return new VortexRenderState();
    }

    @Override
    public void extractRenderState(StellarVortexEntity entity, ExperienceOrbRenderState state,
                                    float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        VortexRenderState vState = (VortexRenderState) state;

        if (entity.getVortexLevel() >= 3) {
            vState.color = getLevel3Color();
            vState.scale = LEVEL3_SCALE;
        } else {
            vState.color = getDefaultColor();
            vState.scale = BASE_SCALE;
        }
    }
}