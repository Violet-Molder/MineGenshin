package com.linweiyun.genshin.content.entities.teyvat.skill.vesna;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/**
 * 灵剑实体渲染器 —— 不画任何几何体，粒子由实体的 clientTick() 生成。
 * <p>作用仅是让 MC 客户端认为该 EntityType 有渲染器，避免被跳过。
 */
public class VesnaSpiritSwordRenderer extends EntityRenderer<VesnaSpiritSwordEntity> {

    public VesnaSpiritSwordRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(VesnaSpiritSwordEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        // 不绘制几何体；粒子由实体自身生成
    }

    @Override
    public ResourceLocation getTextureLocation(VesnaSpiritSwordEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
