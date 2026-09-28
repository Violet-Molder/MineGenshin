package com.linweiyun.genshin.client.render.character;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterBoneMount;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * <b>把「手上那把武器的骨骼」的世界位姿抓出来</b>，供 Photon 特效当锚点用。
 *
 * <p>和 {@link BoneMountGeoLayer} 是同一套骨架：{@code addRenderData} 拿到玩家 →
 * {@code addPerBoneRender} 拿到「已经摆到该骨骼位姿」的 PoseStack → 就地取矩阵。
 * 区别是它<b>不画任何东西</b>，只是把结果写进 {@link WeaponAnchorCache}。
 *
 * <h2>为什么取的是「第一根挂点骨骼」</h2>
 * 角色的 {@code XxxResources.RENDER_DATA} 里声明的第一个 {@code CharacterBoneMount}
 * 就是「手上那把武器」的位置（test 是 {@code long}）。没声明挂点的角色直接不做事，
 * 所以这一层对全项目是无副作用的。
 *
 * <h2>坐标系</h2>
 * 实体渲染时的 PoseStack 是<b>相对相机</b>的（这是 MC 现代渲染管线的前提，
 * 也是 {@code CameraRenderState#pos} 存在的理由），所以：
 * <pre>
 * 骨骼世界坐标 = 相机世界坐标 + PoseStack 顶上的平移
 * </pre>
 * 朝向则直接取该矩阵的旋转部分。为了防御坐标系假设被将来改动打破，这里有一道
 * {@link #SANITY_DISTANCE} 检查：结果离玩家太远就整帧丢弃，
 * 宁可让特效停在上一帧，也不要它飞到天边。
 */
public final class WeaponAnchorGeoLayer<T extends GeoAnimatable, O, R extends GeoRenderState>
        extends GeoRenderLayer<T, O, R> {

    /** 把「本趟渲染属于哪个玩家」从提取阶段带到渲染阶段的票据。 */
    private static final DataTicket<Player> OWNER =
            DataTicket.create("minegenshin_weapon_anchor_owner", Player.class);

    /** 结果离玩家超过这个距离（格）就认为坐标系假设不成立，丢弃这一帧。 */
    private static final double SANITY_DISTANCE = 8.0;

    public WeaponAnchorGeoLayer(GeoRenderer<T, O, R> renderer) {
        super(renderer);
    }

    // ==================== 1. 提取阶段：记住这是谁的渲染 ====================

    @Override
    public void addRenderData(T animatable, @Nullable O relatedObject, R renderState, float partialTick) {
        if (relatedObject instanceof Player player) {
            renderState.addGeckolibData(OWNER, player);
        }
    }

    // ==================== 2. 登记每骨骼回调 ====================

    @Override
    public void addPerBoneRender(RenderPassInfo<R> renderPassInfo,
                                 BiConsumer<GeoBone, PerBoneRender<R>> consumer) {
        Player player = renderPassInfo.getGeckolibData(OWNER);
        if (player == null) {
            return;
        }
        String boneName = weaponBone(player);
        if (boneName == null) {
            return;
        }
        GeoBone bone = renderPassInfo.model().getBone(boneName).orElse(null);
        if (bone == null) {
            return;
        }
        consumer.accept(bone, (info, ignored, tasks) -> capture(player, info));
    }

    /** 角色声明的第一个挂点骨骼 = 手上的武器。没声明就返回 null。 */
    @Nullable
    private static String weaponBone(Player player) {
        List<CharacterBoneMount> mounts = BoneMountGeoLayer.boneMountsFor(player);
        return mounts.isEmpty() ? null : mounts.get(0).boneName();
    }

    // ==================== 3. 取位姿 ====================

    private static void capture(Player player, RenderPassInfo<?> info) {
        Matrix4f bonePose = new Matrix4f(info.poseStack().last().pose());

        Vector3f local = bonePose.getTranslation(new Vector3f());
        Quaternionf rotation = bonePose.getUnnormalizedRotation(new Quaternionf());

        Vec3 camera = info.cameraState().pos;
        Vec3 world = new Vec3(camera.x + local.x, camera.y + local.y, camera.z + local.z);

        if (world.distanceToSqr(player.position()) > SANITY_DISTANCE * SANITY_DISTANCE) {
            return;
        }

        WeaponAnchorCache.put(player, new Vector3f((float) world.x, (float) world.y, (float) world.z), rotation);
    }
}
