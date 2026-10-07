package com.linweiyun.genshin.client.render.character;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import com.linweiyun.genshin.core.character.util.CharacterHelper;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderRepository;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 半透明骨骼层：把角色声明的骨骼改用<b>混合管线</b>渲染。
 *
 * <p>GeckoLib 默认的扣像管线把 α 当阈值用（低于阈值直接丢弃、高于就写成不透明），
 * 贴图里真正的半透明像素会变成一块实心色。本层把名单里的骨骼从默认管线里摘出来
 * （{@link #preRender} 藏起骨骼自己的方块），再用 {@code RenderType.entityTranslucent}
 * 把它自己的 cube 重画一遍（{@link #renderForBone}）。
 *
 * <p>名单在 {@link #preRender} 里取一次，逐骨骼回调只查这份集合。
 * 子骨骼不受影响，仍走默认管线。
 */
public final class TranslucentBoneGeoLayer<T extends GeoAnimatable> extends GeoRenderLayer<T> {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);

    /** 提醒过的「模型里没有这根骨骼」，避免每帧刷屏。 */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /** 本趟要改用混合管线的骨骼名；在 {@link #preRender} 里算。 */
    private Set<String> frameBones = Set.of();

    public TranslucentBoneGeoLayer(GeoRenderer<T> renderer) {
        super(renderer);
    }

    // ==================== 1. 主渲染趟藏掉这几根骨骼自己的方块 ====================

    @Override
    public void preRender(PoseStack poseStack, T animatable, BakedGeoModel model, RenderType renderType,
                          MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                          int packedLight, int packedOverlay) {
        this.frameBones = Set.of();

        List<String> bones = translucentBones(animatable);
        if (bones.isEmpty()) {
            return;
        }

        Set<String> ready = new HashSet<>(bones);
        for (String boneName : bones) {
            GeoBone bone = model.getBone(boneName).orElse(null);
            if (bone == null) {
                if (WARNED.add("missing|" + System.identityHashCode(model) + "|" + boneName)) {
                    LOGGER.warn("[半透明骨骼] 模型里没有骨骼 '{}'：声明的半透明骨骼不生效"
                                    + "（贴图里的半透明像素会照旧被扣像画成实心）", boneName);
                }
                continue;
            }
            if (bone.getCubes().isEmpty()) {
                if (WARNED.add("empty|" + System.identityHashCode(model) + "|" + boneName)) {
                    LOGGER.warn("[半透明骨骼] 模型里的骨骼 '{}' 自身没有方块：声明的半透明骨骼不生效"
                                    + "（方块是不是挂在子级上了？）", boneName);
                }
                continue;
            }
            bone.setHidden(true);
        }

        this.frameBones = ready;
    }

    // ==================== 2. 用混合管线补画这几根骨骼 ====================

    @Override
    public void renderForBone(PoseStack poseStack, T animatable, GeoBone bone, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        if (!frameBones.contains(bone.getName()) || bone.getCubes().isEmpty()) {
            return;
        }

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(getTextureResource(animatable)));
        for (GeoCube cube : bone.getCubes()) {
            getRenderer().renderCube(poseStack, cube, consumer, packedLight, packedOverlay, 0xFFFFFFFF);
        }
    }

    /** 本角色声明为「半透明」的骨骼名。 */
    private static List<String> translucentBones(GeoAnimatable animatable) {
        Player player = BoneMountGeoLayer.playerOf(animatable);
        if (player == null) {
            return List.of();
        }
        String characterId = CharacterHelper.getActiveCharacterId(player);
        if (characterId == null) {
            return List.of();
        }
        CharacterRenderData data = CharacterRenderRepository.get(characterId);
        return data == null ? List.of() : data.translucentBones();
    }
}
