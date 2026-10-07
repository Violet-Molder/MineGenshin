package com.linweiyun.genshin.client.render.character;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.util.CharacterHelper;
import com.linweiyun.genshin.core.system.combat.action.data.BoneMountContent;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterBoneMount;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderRepository;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 骨骼挂点层：把角色声明的挂点内容（默认是武器槽里那把武器的整个模型）
 * 画在模型的指定骨骼上。
 *
 * <p>做法是把挂点骨骼自己的方块藏起来（那个位置本来就不该有方块），
 * 再在已经摆到该骨骼位姿的 PoseStack 上画内容 —— 逐骨骼回调
 * {@link GeoRenderLayer#renderForBone} 提供的正是这个位姿。
 *
 * <p>挂点内容在 {@link #preRender} 里解析一次（每趟渲染一次），逐骨骼回调只查这份表：
 * {@code BoneMountSource} 要读槽位、建物品栈，放在回调里会变成「每根骨骼一次」。
 *
 * <p>只支持「整个物品模型」这种内容形态；要搬「源 geo 模型里的某根骨骼」时本层会跳过该挂点
 * 并打一条警告，目标骨骼保持原样。
 */
public final class BoneMountGeoLayer<T extends GeoAnimatable> extends GeoRenderLayer<T> {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);

    /** 已经提醒过的组合，避免每帧刷屏。 */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /** 本趟渲染要画的挂点：骨骼名 + 内容。 */
    private record ResolvedMount(CharacterBoneMount mount, ItemStack stack) {
    }

    /** 本趟渲染解析好的挂点；在 {@link #preRender} 里算，逐骨骼回调只读。 */
    private List<ResolvedMount> frameMounts = List.of();

    public BoneMountGeoLayer(GeoRenderer<T> renderer) {
        super(renderer);
    }

    // ==================== 1. 每趟渲染解析一次挂点，并藏掉挂点骨骼自己的方块 ====================

    @Override
    public void preRender(PoseStack poseStack, T animatable, BakedGeoModel model, RenderType renderType,
                          MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                          int packedLight, int packedOverlay) {
        this.frameMounts = List.of();

        Player player = playerOf(animatable);
        if (player == null) {
            return;
        }

        PGCharacter character = currentCharacter(player);
        List<ResolvedMount> resolved = new ArrayList<>();
        for (CharacterBoneMount mount : boneMountsFor(player)) {
            GeoBone bone = model.getBone(mount.boneName()).orElse(null);
            if (bone == null) {
                warnOnce("dstbone|" + System.identityHashCode(model) + "|" + mount.boneName(),
                        "[骨骼替换] 模型里没有骨骼 '{}'：这个挂点不会生效。模型里的骨骼名有：{}",
                        mount.boneName(), boneNames(model));
                continue;
            }

            BoneMountContent content = mount.source().resolve(player, character);
            if (content == null || content.isEmpty()) {
                // 没装备 / 来源主动返回空 —— 正常情况，不提醒，骨骼保持原样
                continue;
            }

            if (content.isSubBone()) {
                warnOnce("subbone|" + mount.boneName(),
                        "[骨骼替换] 挂点 '{}' 声明的是「源模型里的某根骨骼」形态，本层不支持，本帧跳过",
                        mount.boneName());
                continue;
            }

            bone.setHidden(true);
            resolved.add(new ResolvedMount(mount, content.stack()));
        }

        this.frameMounts = resolved;
    }

    // ==================== 2. 在挂点骨骼上画内容 ====================

    @Override
    public void renderForBone(PoseStack poseStack, T animatable, GeoBone bone, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        for (ResolvedMount resolved : frameMounts) {
            if (!resolved.mount().boneName().equals(bone.getName())) {
                continue;
            }

            poseStack.pushPose();
            applyMountTransform(poseStack, resolved.mount());
            Minecraft.getInstance().getItemRenderer().renderStatic(
                    resolved.stack(),
                    ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    poseStack,
                    bufferSource,
                    Minecraft.getInstance().level,
                    0);
            poseStack.popPose();
            return;
        }
    }

    private static void applyMountTransform(PoseStack poseStack, CharacterBoneMount mount) {
        // 模型坐标是「像素」，1 格 = 16 像素
        poseStack.translate(mount.offsetX() / 16f, mount.offsetY() / 16f, mount.offsetZ() / 16f);

        if (mount.rotationX() != 0) poseStack.mulPose(Axis.XP.rotationDegrees(mount.rotationX()));
        if (mount.rotationY() != 0) poseStack.mulPose(Axis.YP.rotationDegrees(mount.rotationY()));
        if (mount.rotationZ() != 0) poseStack.mulPose(Axis.ZP.rotationDegrees(mount.rotationZ()));

        if (mount.scale() != 1.0f) poseStack.scale(mount.scale(), mount.scale(), mount.scale());
    }

    // ==================== 共用小工具 ====================

    @Nullable
    static Player playerOf(GeoAnimatable animatable) {
        return animatable instanceof GenshinReplacedPlayer replaced ? replaced.getPlayerEntity() : null;
    }

    private static String boneNames(BakedGeoModel model) {
        List<String> names = new ArrayList<>();
        for (GeoBone bone : model.topLevelBones()) {
            collectBoneNames(bone, names);
        }
        return String.join(", ", names);
    }

    private static void collectBoneNames(GeoBone bone, List<String> out) {
        out.add(bone.getName());
        for (GeoBone child : bone.getChildBones()) {
            collectBoneNames(child, out);
        }
    }

    private static void warnOnce(String key, String message, Object... args) {
        if (WARNED.add(key)) {
            LOGGER.warn(message, args);
        }
    }

    // ==================== 对外查询 ====================

    /** 某个角色声明的骨骼挂点。 */
    public static List<CharacterBoneMount> boneMountsFor(String characterId) {
        if (characterId == null) {
            return List.of();
        }
        CharacterRenderData data = CharacterRenderRepository.get(characterId);
        return data == null ? List.of() : data.boneMounts();
    }

    /** 当前出战角色声明的骨骼挂点。 */
    public static List<CharacterBoneMount> boneMountsFor(Player player) {
        return boneMountsFor(CharacterHelper.getActiveCharacterId(player));
    }

    /** 当前出战角色；没戴饰品 / 数据还没同步时为 null。 */
    @Nullable
    public static PGCharacter currentCharacter(Player player) {
        PlayerCharactersAttachment attachment =
                player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        return attachment == null ? null : attachment.getCurrentCharacter();
    }
}
