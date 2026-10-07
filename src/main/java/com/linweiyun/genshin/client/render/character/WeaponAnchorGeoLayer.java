package com.linweiyun.genshin.client.render.character;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterBoneMount;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * 武器锚点层：把「手上那把武器」所在骨骼的世界位姿写进 {@link WeaponAnchorCache}，
 * 供特效（Photon）当锚点用。它不画任何东西。
 *
 * <p>取的是角色声明的第一根挂点骨骼（{@code CharacterBoneMount} 列表的第一项）；
 * 没声明挂点的角色不做任何事。骨骼名在 {@link #preRender} 里取一次，逐骨骼回调只做比较。
 *
 * <p>坐标系：实体渲染时 PoseStack 是相对相机的，所以
 * {@code 骨骼世界坐标 = 相机世界坐标 + PoseStack 顶上的平移}；朝向直接取矩阵的旋转部分。
 * 结果离玩家超过 {@link #SANITY_DISTANCE} 格时整帧丢弃，宁可让特效停在上一帧。
 */
public final class WeaponAnchorGeoLayer<T extends GeoAnimatable> extends GeoRenderLayer<T> {

    /** 结果离玩家超过这个距离（格）就认为坐标系假设不成立，丢弃这一帧。 */
    private static final double SANITY_DISTANCE = 8.0;

    /** 本趟渲染要抓的骨骼名；在 {@link #preRender} 里取。 */
    @Nullable
    private String frameBone;

    public WeaponAnchorGeoLayer(GeoRenderer<T> renderer) {
        super(renderer);
    }

    @Override
    public void preRender(PoseStack poseStack, T animatable, BakedGeoModel model, RenderType renderType,
                          MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                          int packedLight, int packedOverlay) {
        this.frameBone = weaponBone(BoneMountGeoLayer.playerOf(animatable));
    }

    @Override
    public void renderForBone(PoseStack poseStack, T animatable, GeoBone bone, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        String wanted = this.frameBone;
        Player player = BoneMountGeoLayer.playerOf(animatable);
        if (wanted == null || player == null || !wanted.equals(bone.getName())) {
            return;
        }
        capture(player, poseStack);
    }

    /** 角色声明的第一个挂点骨骼 = 手上的武器。没声明就返回 null。 */
    @Nullable
    private static String weaponBone(@Nullable Player player) {
        if (player == null) {
            return null;
        }
        List<CharacterBoneMount> mounts = BoneMountGeoLayer.boneMountsFor(player);
        return mounts.isEmpty() ? null : mounts.get(0).boneName();
    }

    private static void capture(Player player, PoseStack poseStack) {
        Matrix4f bonePose = new Matrix4f(poseStack.last().pose());

        Vector3f local = bonePose.getTranslation(new Vector3f());
        Quaternionf rotation = bonePose.getUnnormalizedRotation(new Quaternionf());

        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 world = new Vec3(camera.x + local.x, camera.y + local.y, camera.z + local.z);

        if (world.distanceToSqr(player.position()) > SANITY_DISTANCE * SANITY_DISTANCE) {
            return;
        }

        WeaponAnchorCache.put(player, new Vector3f((float) world.x, (float) world.y, (float) world.z), rotation);
    }
}
