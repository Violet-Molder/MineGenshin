package com.linweiyun.genshin.client.render.entity;

import com.linweiyun.genshin.content.entities.area.TalismanSpiritArea;
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

public class FieldTalismanSpiritRender extends EntityRenderer<TalismanSpiritArea> {
  private static final ResourceLocation EXPERIENCE_ORB_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/experience/experience_orb.png");
  private static final RenderType RENDER_TYPE = RenderType.itemEntityTranslucentCull(EXPERIENCE_ORB_LOCATION);

  public FieldTalismanSpiritRender(EntityRendererProvider.Context context) {
    super(context);
    this.shadowRadius = 0.15F;
    this.shadowStrength = 0.75F;
  }

  @Override
  protected int getBlockLightLevel(TalismanSpiritArea entity, BlockPos blockPos) {
    return Mth.clamp(super.getBlockLightLevel(entity, blockPos) + 7, 0, 15);
  }

  @Override
  public void render(TalismanSpiritArea entity, float entityYaw, float partialTicks, PoseStack poseStack,
                     MultiBufferSource bufferSource, int packedLight) {
    poseStack.pushPose();
    // 经验球图标表 4×4，这里固定取左上角那一格
    int icon = 0;
    float u0 = (icon % 4 * 16 + 0) / 64.0F;
    float u1 = (icon % 4 * 16 + 16) / 64.0F;
    float v0 = (icon / 4 * 16 + 0) / 64.0F;
    float v1 = (icon / 4 * 16 + 16) / 64.0F;
    float rr = (entity.tickCount + partialTicks) / 2.0F;
    int rc = (int)((Mth.sin(rr + 0.0F) + 1.0F) * 0.5F * 255.0F);
    int bc = (int)((Mth.sin(rr + (float) (Math.PI * 4.0 / 3.0)) + 1.0F) * 0.1F * 255.0F);
    poseStack.translate(0.0F, 0.1F, 0.0F);
    poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
    poseStack.scale(0.3F, 0.3F, 0.3F);
    VertexConsumer buffer = bufferSource.getBuffer(RENDER_TYPE);
    PoseStack.Pose pose = poseStack.last();
    vertex(buffer, pose, -0.5F, -0.25F, rc, 255, bc, u0, v1, packedLight);
    vertex(buffer, pose, 0.5F, -0.25F, rc, 255, bc, u1, v1, packedLight);
    vertex(buffer, pose, 0.5F, 0.75F, rc, 255, bc, u1, v0, packedLight);
    vertex(buffer, pose, -0.5F, 0.75F, rc, 255, bc, u0, v0, packedLight);
    poseStack.popPose();
    super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
  }

  private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, int r, int g, int b, float u, float v, int lightCoords) {
    buffer.addVertex(pose, x, y, 0.0F)
            .setColor(210, 77, 77, 128)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(lightCoords)
            .setNormal(pose, 0.0F, 1.0F, 0.0F);
  }

  @Override
  public ResourceLocation getTextureLocation(TalismanSpiritArea entity) {
    return EXPERIENCE_ORB_LOCATION;
  }
}
