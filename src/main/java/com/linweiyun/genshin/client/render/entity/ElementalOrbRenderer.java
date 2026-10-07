package com.linweiyun.genshin.client.render.entity;

import com.linweiyun.genshin.config.WorldTextColorConfig;
import com.linweiyun.genshin.content.entities.misc.ElementalOrb;
import com.linweiyun.elementlib.core.element.GenshinElement;
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

public class ElementalOrbRenderer extends EntityRenderer<ElementalOrb> {

    private static final ResourceLocation ORB_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/experience/experience_orb.png");
    private static final RenderType RENDER_TYPE = RenderType.itemEntityTranslucentCull(ORB_TEXTURE);

    private static final float BASE_SCALE = 0.3F;

    public ElementalOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.15F;
        this.shadowStrength = 0.75F;
    }

    @Override
    protected int getBlockLightLevel(ElementalOrb entity, BlockPos blockPos) {
        return Mth.clamp(super.getBlockLightLevel(entity, blockPos) + 7, 0, 15);
    }

    @Override
    public void render(ElementalOrb entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        // 经验球图标表 4×4，这里固定取左上角那一格
        int icon = 0;
        float u0 = (icon % 4 * 16) / 64.0F;
        float u1 = u0 + 16.0F / 64.0F;
        float v0 = (icon / 4 * 16) / 64.0F;
        float v1 = v0 + 16.0F / 64.0F;

        int color = getColorForElement(entity.getElement());
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        poseStack.translate(0.0F, 0.1F, 0.0F);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.scale(BASE_SCALE, BASE_SCALE, BASE_SCALE);

        VertexConsumer buffer = bufferSource.getBuffer(RENDER_TYPE);
        PoseStack.Pose pose = poseStack.last();
        vertex(buffer, pose, -0.5F, -0.25F, r, g, b, u0, v1, packedLight);
        vertex(buffer, pose, 0.5F, -0.25F, r, g, b, u1, v1, packedLight);
        vertex(buffer, pose, 0.5F, 0.75F, r, g, b, u1, v0, packedLight);
        vertex(buffer, pose, -0.5F, 0.75F, r, g, b, u0, v0, packedLight);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
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
    public ResourceLocation getTextureLocation(ElementalOrb entity) {
        return ORB_TEXTURE;
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
