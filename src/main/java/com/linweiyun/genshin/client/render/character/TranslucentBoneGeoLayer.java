package com.linweiyun.genshin.client.render.character;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.cache.model.cuboid.GeoCube;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.google.common.reflect.TypeToken;
import com.linweiyun.genshin.core.character.util.CharacterHelper;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderRepository;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * 半透明骨骼层 —— 把「贴图里带半透明像素的骨骼」改用<b>混合管线</b>渲染。
 *
 * <h2>为什么需要这一层</h2>
 * GeckoLib 的 {@code GeoRenderer#getRenderType} 默认返回 {@code RenderTypes.entityCutout}，
 * 也就是<b>扣像（alpha test）</b>管线：α 只当阈值用（低于 0.1 直接丢掉，高于就写成不透明），
 * 根本不做混合。YSM 转过来的模型里，发光屏幕的内屏是一个
 * <b>α≈0.13 的纯色块</b>（申鹤：{@code shenhe.png} 的 UV (287,40) 3×2 区域，
 * RGBA = 131,178,240,33），编辑器里看到的是「贴图真实 α」所以是半透明的，
 * 一到游戏里就变成一块不透明的蓝 —— 这就是「编辑器半透明、进游戏实心」的原因。
 *
 * <h2>做法：同一根骨骼，跑两趟</h2>
 * <ol>
 *   <li><b>主渲染趟</b>（cutout）：用 {@code BoneSnapshot#skipRender} 把这几根骨骼<b>自己的方块</b>
 *       藏掉。注意<b>不动子级</b>（不设 {@code skipChildrenRender}）——子骨骼的几何体本来就该
 *       照原样走不透明管线。屏幕外框、花纹、齿轮都在别的骨骼/方块上，它们<b>完全不受影响</b>。</li>
 *   <li><b>额外一趟</b>（translucent）：用 {@link PerBoneRender} 拿到「已经摆到该骨骼位姿」的
 *       PoseStack，把这几根骨骼自己的方块用 {@code RenderTypes.entityTranslucent} 重画一遍。
 *       提交节点带混合，会自动进原版的<b>半透明阶段</b>（在不透明几何之后），所以能正确
 *       和背后的身体、外框、齿轮做混合与深度测试。</li>
 * </ol>
 *
 * <h2>位姿为什么要补一句 {@code translateAwayFromPivotPoint}</h2>
 * {@code PerBoneRender} 进来的位姿是 GeckoLib 的 {@code RenderUtil.transformToBone} 摆好的，
 * 它停在「骨骼 pivot · 骨骼旋转」这一步
 * （{@code transformToBone} = 祖先链 prep · {@code translateToPivotPoint}），
 * 而方块渲染期待的位姿是完整的 {@code prepMatrixForBone}
 * （= {@code translate(pivot) · rotate · scale · translate(-pivot)}）。
 * 两者恰好差一个「离开 pivot」，所以这里补 {@code translateAwayFromPivotPoint} ——
 * 不补的话整块屏幕会按 pivot 的量（竖直方向 22/16 格）平移出去。
 *
 * <p>方块直接遍历 {@code CuboidGeoBone#cubes} 画，而不是调 {@code bone.render(...)}：
 * 主渲染趟刚把 {@code skipRender(true)} 挂在同一根骨骼的快照上，走 {@code bone.render}
 * 会被那句隐藏检查挡掉（{@code CuboidGeoBone#render} 里读的是 {@code frameSnapshot}）。
 * 直接画方块也顺带说明「这一趟只画这根骨骼自己，不碰子树」。
 *
 * <h2>别人藏掉的骨骼，这一趟也不画</h2>
 * {@link PerBoneRender} 是<b>绕开</b> {@code BoneSnapshot#skipRender} 的一条路
 * （{@code GeoRendererInternals#submitPerBoneRenderTasks} 里直接 {@code transformToBone} 之后
 * 遍历方块，全程不读 {@code isHidden()}），所以别的渲染趟挂在这根骨骼上的隐藏对它无效。
 * 申鹤胸口那块屏幕「闲置、奔跑时也常驻」就是这么来的：主趟被 {@code CharacterPropBones}
 * 整组藏掉，这一层又用 {@code entityTranslucent} 原样画了一遍。
 * 现在这一层会自己先问一句「本帧有没有<b>别人</b>先把它藏了」——判据与理由写在
 * {@link #addPerBoneRender}。
 *
 * <h2>名单从哪来</h2>
 * {@link CharacterRenderData#translucentBones()}：每个角色在自己的 {@code XxxResources}
 * 里用 {@code withTranslucentBones("骨骼名", ...)} 声明。没声明的角色这一层直接空转。
 * 申鹤声明的是 {@code ysmGlow_texiao}（屏幕整体：外框 + 花纹 + 内屏，两个方块同属一根骨骼）。
 *
 * <p>项目里的先例：{@code BoneMountGeoLayer}（同样「藏原骨骼 + {@code PerBoneRender} 画内容」）、
 * {@code CharacterAppearanceBones}（{@code BoneUpdater} 藏骨骼）。
 */
public final class TranslucentBoneGeoLayer<T extends GeoAnimatable, O, R extends GeoRenderState>
        extends GeoRenderLayer<T, O, R> {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);

    /** 这一趟要改用半透明管线渲染的骨骼名（由渲染状态提取阶段填入）。 */
    private static final DataTicket<List<String>> BONES =
            DataTicket.create("minegenshin_translucent_bones", new TypeToken<>() {
            });

    /** 提醒过的「模型里没有这根骨骼」，避免每帧刷屏。 */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    public TranslucentBoneGeoLayer(GeoRenderer<T, O, R> renderer) {
        super(renderer);
    }

    // ==================== 1. 取名单（渲染状态提取阶段） ====================

    @Override
    public void addRenderData(T animatable, @Nullable O relatedObject, R renderState, float partialTick) {
        if (!(relatedObject instanceof Player player)) {
            return;
        }

        String characterId = CharacterHelper.getActiveCharacterId(player);
        if (characterId == null) {
            return;
        }

        CharacterRenderData data = CharacterRenderRepository.get(characterId);
        if (data == null) {
            return;
        }

        List<String> bones = data.translucentBones();
        if (!bones.isEmpty()) {
            renderState.addGeckolibData(BONES, bones);
        }
    }

    // ==================== 2. 主渲染趟藏掉自己的方块 + 登记「半透明那一趟」 ====================

    /**
     * 这一根骨骼的两件事必须<b>一起</b>在这里做完：
     *
     * <ol>
     *   <li>给主渲染趟（cutout）挂一句 {@code skipRender(true)} —— 这几根骨骼自己的方块
     *       不画，改由下面半透明那一趟重画；</li>
     *   <li>顺手问一句「<b>这一帧有没有别人先把它藏掉了</b>」——有的话，半透明那一趟
     *       同样<b>不画</b>（别人藏了就是不该出现，这一层没有资格把它画回来）。</li>
     * </ol>
     *
     * <h2>为什么「先问再藏」必须写在同一个 updater 里，而且只能问一次</h2>
     * <ul>
     *   <li>{@code BoneSnapshot} 只有一个布尔量：先把 {@code skipRender(true)} 写进去再读，
     *       分不出是谁藏的 —— 所以这句必须问在<b>自己动手之前</b>；</li>
     *   <li>外部（角色自己的 {@code CharacterPropBones} / {@code CharacterAppearanceBones} 等）
     *       的 updater 是在 {@code GeoRenderer#performRenderPass} 里、层之前加进去的，
     *       执行顺序在 {@code RenderPassInfo#boneUpdates} 里天然排在前面；</li>
     *   <li>{@link RenderPassInfo#renderPosed} 一个 pass 里跑两次（{@code submitRenderTasks}
     *       与 {@code submitPerBoneRenderTasks} 各一次），快照是同一批 ——
     *       第二次读到的就是自己第一次写进去的 {@code true}，所以答案只在<b>第一次</b>算。</li>
     * </ul>
     */
    @Override
    public void addPerBoneRender(RenderPassInfo<R> renderPassInfo,
                                 BiConsumer<GeoBone, PerBoneRender<R>> consumer) {
        List<String> bones = renderPassInfo.getGeckolibData(BONES);
        if (bones == null || bones.isEmpty()) {
            return;
        }

        BakedGeoModel model = renderPassInfo.model();

        for (String boneName : bones) {
            GeoBone bone = model.getBone(boneName).orElse(null);

            if (bone == null) {
                warnOnce("missing|" + System.identityHashCode(model) + "|" + boneName,
                        "[半透明骨骼] 角色模型 '{}' 里没有骨骼 '{}'：声明的半透明骨骼不生效"
                                + "（贴图里的半透明像素会照旧被 cutout 画成实心）",
                        model.properties().identifier(), boneName);
                continue;
            }

            if (!(bone instanceof CuboidGeoBone cuboid) || cuboid.cubes.length == 0) {
                warnOnce("empty|" + System.identityHashCode(model) + "|" + boneName,
                        "[半透明骨骼] 角色模型 '{}' 的骨骼 '{}' 自身没有方块：声明的半透明骨骼不生效"
                                + "（方块是不是挂在子级上了？）",
                        model.properties().identifier(), boneName);
                continue;
            }

            // 这一根骨骼的答案；updater 与半透明那一趟的回调共用同一个实例
            ExternalHide externalHide = new ExternalHide();

            // 只 skipRender（自己），不 skipChildrenRender：子骨骼照旧走不透明管线。
            renderPassInfo.addBoneUpdater((info, snapshots) -> snapshots.ifPresent(boneName, snapshot -> {
                if (!externalHide.asked) {
                    externalHide.asked = true;
                    externalHide.hidden = snapshot.isHidden() || snapshot.areChildrenHidden();
                }

                snapshot.skipRender(true);
            }));

            consumer.accept(bone, (info, targetBone, tasks) -> {
                if (externalHide.hidden) {
                    return;
                }

                submitTranslucent(info, cuboid, tasks);
            });
        }
    }

    /**
     * 「本帧这根骨骼被别的渲染趟藏了吗」的答案。
     *
     * <p>一个 pass 里的 {@code BoneUpdater} 与 {@code PerBoneRender} 回调是两个 lambda，
     * 共用这个小盒子：前者在 {@code renderPosed} 开头跑，后者在同一次 {@code renderPosed}
     * 之后跑，所以回调读到的永远是本帧已经算好的答案。
     */
    private static final class ExternalHide {
        private boolean asked;
        private boolean hidden;
    }

    // ==================== 3. 用混合管线画这根骨骼自己的方块 ====================

    private void submitTranslucent(RenderPassInfo<R> renderPassInfo, CuboidGeoBone bone,
                                   SubmitNodeCollector renderTasks) {
        Identifier texture = getTextureResource(renderPassInfo.renderState());
        RenderType renderType = RenderTypes.entityTranslucent(texture);

        int packedLight = renderPassInfo.packedLight();
        int packedOverlay = renderPassInfo.packedOverlay();
        int renderColor = renderPassInfo.renderColor();

        PoseStack poseStack = renderPassInfo.poseStack();

        // 进来的 PoseStack 已经摆在该骨骼位姿上；回调拿到的是「提交时捕获的那份位姿」，
        // 反过来用同一条栈还原，延迟绘制时才不依赖外层栈当时的状态（与 BoneMountGeoLayer 同法）。
        renderTasks.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> {
            poseStack.pushPose();
            poseStack.last().set(pose);

            // transformToBone 停在 pivot 上，补回 prepMatrixForBone 的最后一步
            bone.translateAwayFromPivotPoint(poseStack);

            for (GeoCube cube : bone.cubes) {
                poseStack.pushPose();
                cube.render(poseStack, vertexConsumer, packedLight, packedOverlay, renderColor);
                poseStack.popPose();
            }

            poseStack.popPose();
        });
    }

    private static void warnOnce(String key, String message, Object... args) {
        if (WARNED.add(key)) {
            LOGGER.warn(message, args);
        }
    }
}
