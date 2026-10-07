package com.linweiyun.genshin.client.render.character;

import com.linweiyun.genshin.client.render.character.bones.BoneRenderState;
import com.linweiyun.genshin.client.render.character.bones.BoneSnapshots;
import com.linweiyun.genshin.client.render.character.bones.BoneUpdater;
import com.linweiyun.genshin.client.render.character.bones.RenderPassView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;

/**
 * 角色模型渲染器：一次调用渲染一个角色的整套模型（含挂点层、半透明层）。
 *
 * <h2>原点</h2>
 * 覆盖 {@link #preRender} 抵消父类 {@code GeoObjectRenderer} 的 +0.5/+0.51/+0.5 平移：
 * 那句平移是给<b>以方块角为原点</b>导出的摆件模型用的，而本模组的角色模型以原点为中心、
 * 人也站在方块中心，带上它会让模型整体偏到斜后方半格，与判定箱和影子对不上。
 *
 * <h2>骨骼规则</h2>
 * {@link #performRenderPass} 收到本帧要用的 {@link BoneUpdater}，在 {@link #preRender}
 * 里应用到烘焙模型上（改好的骨骼状态在本次渲染中一直有效）。
 */
public class CharacterRenderer extends GeoObjectRenderer<GenshinReplacedPlayer> {

    /** 本次渲染要应用的骨骼规则；由 {@link #performRenderPass} 设置。 */
    @Nullable
    private BoneUpdater<BoneRenderState> pendingUpdater;

    public CharacterRenderer(GeoModel<GenshinReplacedPlayer> model) {
        super(model);
    }

    /**
     * 渲染一趟角色模型。
     *
     * @param boneUpdater 本次要应用的骨骼规则；为空表示不改骨骼
     */
    public void performRenderPass(GenshinReplacedPlayer animatable, @Nullable Player related, PoseStack poseStack,
                                  MultiBufferSource bufferSource, int packedLight, float partialTick,
                                  @Nullable BoneUpdater<BoneRenderState> boneUpdater) {
        this.animatable = animatable;
        this.pendingUpdater = boneUpdater;
        try {
            render(poseStack, animatable, bufferSource, null, null, packedLight, partialTick);
        } finally {
            this.pendingUpdater = null;
        }
    }

    @Override
    public void preRender(PoseStack poseStack, GenshinReplacedPlayer animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int colour) {
        BoneUpdater<BoneRenderState> updater = this.pendingUpdater;
        if (updater != null) {
            BoneSnapshots snapshots = new BoneSnapshots(model);
            updater.run(new RenderPassView<>(new BoneRenderState(partialTick), snapshots), snapshots);
        }

        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, colour);
        // 抵消父类的摆件补偿（详见类注释）。要调模型相对实体的位置就改这一句。
        poseStack.translate(-0.5F, -0.51F, -0.5F);
    }
}