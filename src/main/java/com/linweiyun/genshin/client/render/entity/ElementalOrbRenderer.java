package com.linweiyun.genshin.client.render.entity;

import com.linweiyun.genshin.config.WorldTextColorConfig;
import com.linweiyun.genshin.content.entities.misc.ElementalOrb;
import com.linweiyun.elementlib.core.element.GenshinElement;
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

public class ElementalOrbRenderer extends EntityRenderer<ElementalOrb, ExperienceOrbRenderState> {

    private static final Identifier ORB_TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/experience/experience_orb.png");
    private static final RenderType RENDER_TYPE =
            RenderTypes.entityTranslucentCullItemTarget(ORB_TEXTURE);

    public ElementalOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.15F;
        this.shadowStrength = 0.75F;
    }

    private static class OrbRenderState extends ExperienceOrbRenderState {
        int elementColor = 0xFFFFFF;
    }

    @Override
    protected int getBlockLightLevel(ElementalOrb entity, BlockPos blockPos) {
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

        int color = ((OrbRenderState) state).elementColor;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        poseStack.translate(0.0F, 0.1F, 0.0F);
        poseStack.mulPose(camera.orientation);
        poseStack.scale(0.3F, 0.3F, 0.3F);

        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> {
            vertex(buffer, pose, -0.5F, -0.25F, r, g, b, u0, v1, state.lightCoords);
            vertex(buffer, pose, 0.5F, -0.25F, r, g, b, u1, v1, state.lightCoords);
            vertex(buffer, pose, 0.5F, 0.75F, r, g, b, u1, v0, state.lightCoords);
            vertex(buffer, pose, -0.5F, 0.75F, r, g, b, u0, v0, state.lightCoords);
        });
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

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
    public ExperienceOrbRenderState createRenderState() {
        return new OrbRenderState();
    }

    @Override
    public void extractRenderState(ElementalOrb entity, ExperienceOrbRenderState state,
                                    float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        GenshinElement element = entity.getElement();
        ((OrbRenderState) state).elementColor = getColorForElement(element);
    }

    private static int getColorForElement(GenshinElement element) {
        String id = element.getId();
        return switch (id) {
            case "pyro" -> parseColor(WorldTextColorConfig.PYRO_COLOR.get());
            case "hydro" -> parseColor(WorldTextColorConfig.HYDRO_COLOR.get());
            case "dendro" -> parseColor(WorldTextColorConfig.DENDRO_COLOR.get());
            case "electro" -> parseColor(WorldTextColorConfig.ELECTRO_COLOR.get());
            case "anemo" -> parseColor(WorldTextColorConfig.ANEMO_COLOR.get());
            case "cyro" -> parseColor(WorldTextColorConfig.CYRO_COLOR.get());
            case "geo" -> parseColor(WorldTextColorConfig.GEO_COLOR.get());
            default -> parseColor(WorldTextColorConfig.PHYSICAL_COLOR.get());
        };
    }

    private static int parseColor(String hex) {
        try {
            return Integer.decode(hex.startsWith("#") ? hex : "#" + hex);
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }
}