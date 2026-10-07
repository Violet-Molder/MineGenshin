package com.linweiyun.genshin.client.render.entity;

import com.linweiyun.genshin.content.entities.misc.IceBlockProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 悬空冰块的渲染器 —— 把 {@link IceBlockProjectile#blockState()} 那个原版方块画出来，并让它转起来。
 *
 * <p>方块本体由 {@code BlockRenderDispatcher#renderSingleBlock} 绘制；
 * 自己额外做的只有两件事：<b>多转两圈</b>、把方块中心对齐到实体位置。
 *
 * <p>绕 Y 轴转主角度（像陀螺），再叠一个 0.7 倍的 X 轴旋转 —— 只转 Y 会像旋转木马，
 * 叠一个斜轴才有「翻滚着砸过来」的感觉。
 */
public class IceBlockProjectileRenderer extends EntityRenderer<IceBlockProjectile> {

    public IceBlockProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
    }

    @Override
    public void render(IceBlockProjectile entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        BlockState blockState = entity.blockState();
        if (blockState.getRenderShape() != RenderShape.MODEL) {
            return;
        }

        float spin = entity.spinDegrees(partialTicks);
        float scale = entity.scale();

        poseStack.pushPose();
        // 实体位置 = 方块底面中心（和原版掉落方块一致），先抬到方块中心再转
        poseStack.translate(0.0D, 0.5D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));
        poseStack.mulPose(Axis.XP.rotationDegrees(spin * 0.7F));
        // 缩放放在最后：绕方块中心缩，冰块和冰刺共用同一条渲染路径
        poseStack.scale(scale, scale, scale);
        // 方块模型占 (0,0,0)~(1,1,1)，平移半个方块让旋转中心落在方块中心
        poseStack.translate(-0.5D, -0.5D, -0.5D);

        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                blockState, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(IceBlockProjectile entity) {
        return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
    }
}
