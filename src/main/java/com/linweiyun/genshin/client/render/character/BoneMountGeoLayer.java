package com.linweiyun.genshin.client.render.character;

import com.linweiyun.genshin.core.character.util.CharacterHelper;

import com.linweiyun.genshin.client.render.optimize.RenderOptimize;
import com.linweiyun.genshin.client.render.optimize.geo.CompiledBone;
import com.linweiyun.genshin.client.render.optimize.geo.CompiledGeoModel;
import com.linweiyun.genshin.client.render.optimize.geo.GeoCompileCache;
import com.linweiyun.genshin.client.render.optimize.walk.BoneWalker;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animation.state.BoneSnapshot;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.google.common.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import com.mojang.math.Axis;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.system.combat.action.data.BoneMountContent;
import com.linweiyun.genshin.core.system.combat.action.data.BoneMountSource;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterBoneMount;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderRepository;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * 骨骼替换层 —— 把任意内容画到角色模型的指定骨骼上。
 *
 * <h2>做什么</h2>
 * <ol>
 *   <li><b>取内容</b>：按每个挂点的 {@link BoneMountSource} 解析出 {@link BoneMountContent}；</li>
 *   <li><b>空的不动</b>：没内容就跳过这个挂点，那根骨骼照原样渲染（模型自带的部件还在）；</li>
 *   <li><b>隐藏原骨骼</b>：有内容时把这根骨骼自身的几何体跳过渲染
 *       （{@code BoneSnapshot.skipRender}），父级骨骼（剑鞘、手臂）不受影响；</li>
 *   <li><b>画内容</b>：用 {@link PerBoneRender} 拿到「已经摆到该骨骼位姿」的 PoseStack，把内容画上去。</li>
 * </ol>
 *
 * <h2>两种内容形态</h2>
 * <ul>
 *   <li><b>整个物品模型</b>：走原版 {@link ItemStackRenderState}。武器是 GeckoLib 的
 *       {@code GeoItem}（特殊物品模型 {@code geckolib:geckolib}），提交后由 GeckoLib 自己的
 *       {@code GeckolibItemSpecialRenderer} 画出 geo 模型；普通物品模型也照样工作。</li>
 *   <li><b>源模型里的一根骨骼</b>：拿到源模型的 {@link BakedGeoModel} 后，把除目标骨骼
 *       以外的骨骼全部跳过，只把那根骨骼（及其祖先链的位移、以及它的子树）画出来 ——
 *       等效于「只把那根骨骼抠出来」。优化路径沿预编译骨骼树直走（见
 *       {@link #renderIsolatedBone}），不被接管的模型才回退到「全树隐藏 + 整模型渲染」。</li>
 * </ul>
 *
 * <h2>校验原则：画不出来就干脆别挂</h2>
 * 「隐藏目标骨骼」和「画内容」是绑在一起的。如果内容其实画不出来却照样隐藏了目标骨骼，
 * 结果就是<b>角色身上凭空少一块</b> —— 而且不报错，极难排查。所以解析阶段就把这些情况挡掉：
 *
 * <table border="1">
 *   <caption>解析失败的情况</caption>
 *   <tr><th>情况</th><th>结果</th></tr>
 *   <tr><td>内容为空（没装备 / 来源返回 null）</td><td>跳过，目标骨骼保持原样</td></tr>
 *   <tr><td>源模型反解不出来（不是 geo 物品 / 资源没加载）</td><td>跳过 + 一次 warn</td></tr>
 *   <tr><td><b>源模型里没有指定的源骨骼</b></td><td><b>跳过 + 一次 warn</b>，目标骨骼保持原样</td></tr>
 *   <tr><td>源骨骼存在但整个子树没有任何几何体</td><td>跳过 + 一次 warn</td></tr>
 *   <tr><td>角色模型上没有目标骨骼</td><td>本来就不生效，补一条 warn 方便排查</td></tr>
 * </table>
 *
 * <p>warn 按「<b>模型对象身份 + 骨骼名</b>」去重，所以资源重载（F3+T）之后如果还没修好会再提醒一次，
 * 而正常运行时不会每帧刷屏。
 *
 * <h2>挂点从哪来</h2>
 * {@link CharacterRenderData#boneMounts()}：每个角色在自己的 {@code XxxResources} 里声明。
 * 没声明的角色这一层直接空转。
 */
public final class BoneMountGeoLayer<T extends GeoAnimatable, O, R extends GeoRenderState>
        extends GeoRenderLayer<T, O, R> {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);

    /** 每个渲染趟要用的数据：已解析好的挂点 + 各自要画的东西。 */
    private static final DataTicket<List<ResolvedMount>> MOUNTS =
            DataTicket.create("minegenshin_bone_mounts", new TypeToken<>() {
            });

    /** 已经提醒过的组合，避免每帧刷屏。 */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    public BoneMountGeoLayer(GeoRenderer<T, O, R> renderer) {
        super(renderer);
    }

    /** 解析结果：目标骨骼 + 画法。 */
    private record ResolvedMount(CharacterBoneMount mount,
                                 @Nullable ItemStackRenderState itemState,
                                 @Nullable BakedGeoModel subModel,
                                 @Nullable Identifier subTexture,
                                 @Nullable String subBone) {
    }

    // ==================== 1. 取内容（渲染状态提取阶段） ====================

    @Override
    public void addRenderData(T animatable, @Nullable O relatedObject, R renderState, float partialTick) {
        if (!(relatedObject instanceof Player player)) {
            return;
        }

        String characterId = CharacterHelper.getActiveCharacterId(player);
        if (characterId == null) {
            return;
        }

        List<CharacterBoneMount> mounts = boneMountsFor(characterId);
        if (mounts.isEmpty()) {
            return;
        }

        PGCharacter character = currentCharacter(player);
        List<ResolvedMount> resolved = new ArrayList<>(mounts.size());

        for (CharacterBoneMount mount : mounts) {
            ResolvedMount entry = resolveMount(mount, player, character, characterId);
            if (entry != null) {
                resolved.add(entry);
            }
        }

        if (!resolved.isEmpty()) {
            renderState.addGeckolibData(MOUNTS, List.copyOf(resolved));
        }
    }

    /**
     * 解析一个挂点；<b>返回 {@code null} 表示这个挂点这一帧不生效</b>
     * （目标骨骼保持原样，不会被隐藏）。
     */
    @Nullable
    private ResolvedMount resolveMount(CharacterBoneMount mount, Player player,
                                       @Nullable PGCharacter character, String characterId) {
        BoneMountContent content = mount.source().resolve(player, character);
        if (content == null || content.isEmpty()) {
            // 没装备 / 来源主动返回空 —— 正常情况，不提醒
            return null;
        }

        if (!content.isSubBone()) {
            // 形态①：整个物品模型
            ItemStackRenderState itemState = new ItemStackRenderState();
            Minecraft.getInstance().getItemModelResolver()
                    .updateForLiving(itemState, content.stack(),
                            ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, player);

            return itemState.isEmpty() ? null : new ResolvedMount(mount, itemState, null, null, null);
        }

        // 形态②/③：源模型里的一根骨骼
        Identifier modelId = content.modelId();
        Identifier textureId = content.textureId();

        if (modelId == null) {
            GeoItemModelResolver.Resolved itemModel =
                    GeoItemModelResolver.resolve(content.stack(), player);
            if (itemModel == null) {
                warnOnce("model|" + characterId + "|" + mount.boneName(),
                        "[骨骼替换] 角色 '{}' 的挂点 '{}'：物品 '{}' 不是 GeckoLib geo 物品或模型还没加载，"
                                + "本帧不改模型（要用整个物品模型请改用 BoneMountSource.WEAPON_SLOT）",
                        characterId, mount.boneName(), content.stack().getItem());
                return null;
            }
            modelId = itemModel.modelId();
            textureId = itemModel.textureId();
        }

        BakedGeoModel baked = GeoItemModelResolver.bakedModel(modelId);
        if (baked == null) {
            warnOnce("baked|" + modelId,
                    "[骨骼替换] 找不到已加载的 geo 模型 '{}'（挂点 '{}'），本帧不改模型", modelId, mount.boneName());
            return null;
        }

        String sourceBone = content.sourceBone();

        // 源骨骼必须真的存在：否则下面会隐藏目标骨骼却画不出任何东西，角色身上凭空少一块
        GeoBone bone = baked.getBone(sourceBone).orElse(null);
        if (bone == null) {
            warnOnce("srcbone|" + System.identityHashCode(baked) + "|" + sourceBone,
                    "[骨骼替换] 源模型 '{}' 里没有骨骼 '{}'（挂点 '{}' 要挂到角色骨骼 '{}'）："
                            + "这个挂点整体不生效，角色骨骼保持原样。模型里的骨骼名有：{}",
                    modelId, sourceBone, mount.boneName(), mount.boneName(), boneNames(baked));
            return null;
        }

        // 骨骼在、但整个子树一个方块都没有 → 同样画不出东西
        if (!hasGeometry(bone)) {
            warnOnce("emptybone|" + System.identityHashCode(baked) + "|" + sourceBone,
                    "[骨骼替换] 源模型 '{}' 的骨骼 '{}' 及其子树没有任何几何体（挂点 '{}'）："
                            + "这个挂点整体不生效。是不是骨骼名写成了父级、而方块挂在子级上？",
                    modelId, sourceBone, mount.boneName());
            return null;
        }

        return new ResolvedMount(mount, null, baked, textureId, sourceBone);
    }

    // ==================== 2. 隐藏原骨骼 ====================

    @Override
    public void preRender(RenderPassInfo<R> renderPassInfo, SubmitNodeCollector renderTasks) {
        List<ResolvedMount> mounts = renderPassInfo.getGeckolibData(MOUNTS);
        if (mounts == null || mounts.isEmpty()) {
            return;
        }

        // 骨骼显隐靠 BoneUpdater：它在主模型提交之前被消费，正好赶上这一趟
        renderPassInfo.addBoneUpdater((info, snapshots) -> {
            for (ResolvedMount resolved : mounts) {
                snapshots.ifPresent(resolved.mount().boneName(),
                        snapshot -> snapshot.skipRender(true));
            }
        });
    }

    // ==================== 3. 登记每骨骼渲染 ====================

    @Override
    public void addPerBoneRender(RenderPassInfo<R> renderPassInfo,
                                 BiConsumer<GeoBone, PerBoneRender<R>> consumer) {
        List<ResolvedMount> mounts = renderPassInfo.getGeckolibData(MOUNTS);
        if (mounts == null || mounts.isEmpty()) {
            return;
        }

        BakedGeoModel characterModel = renderPassInfo.model();

        for (ResolvedMount resolved : mounts) {
            String targetBone = resolved.mount().boneName();
            GeoBone bone = characterModel.getBone(targetBone).orElse(null);

            if (bone == null) {
                // 目标骨骼不存在 → 本来就什么都不会发生（隐藏和绘制都走 getBone），补一条 warn
                warnOnce("dstbone|" + System.identityHashCode(characterModel) + "|" + targetBone,
                        "[骨骼替换] 角色模型 '{}' 里没有骨骼 '{}'：这个挂点不会生效。模型里的骨骼名有：{}",
                        characterModel.properties().identifier(), targetBone, boneNames(characterModel));
                continue;
            }

            consumer.accept(bone, (info, bone2, tasks) -> submitAtBone(info, resolved, tasks));
        }
    }

    // ==================== 4. 画内容 ====================

    /** PoseStack 进来时已经摆在该骨骼的位姿上，这里只补挂点自己的微调。 */
    private void submitAtBone(RenderPassInfo<R> renderPassInfo, ResolvedMount resolved,
                              SubmitNodeCollector renderTasks) {
        if (resolved.itemState() != null) {
            submitItemAtBone(renderPassInfo, resolved, renderTasks);
        } else if (resolved.subModel() != null) {
            submitSubModelAtBone(renderPassInfo, resolved, renderTasks);
        }
    }

    private void submitItemAtBone(RenderPassInfo<R> renderPassInfo, ResolvedMount resolved,
                                  SubmitNodeCollector renderTasks) {
        PoseStack poseStack = renderPassInfo.poseStack();
        poseStack.pushPose();
        applyMountTransform(poseStack, resolved.mount());

        resolved.itemState().submit(poseStack, renderTasks,
                renderPassInfo.packedLight(), OverlayTexture.NO_OVERLAY, 0);

        poseStack.popPose();
    }

    /**
     * 把源模型里的一根骨骼画在目标骨骼上。
     *
     * <p>{@code submitCustomGeometry} 的回调拿到的是「提交时就已捕获的骨骼位姿」，
     * 而 {@link BakedGeoModel#render} 读的是 RenderPassInfo 里的 PoseStack ——
     * 所以这里用一个按捕获位姿初始化过的临时 PoseStack 建一条新的 RenderPassInfo，
     * 不依赖外层的栈在绘制时的状态。
     */
    private void submitSubModelAtBone(RenderPassInfo<R> renderPassInfo, ResolvedMount resolved,
                                      SubmitNodeCollector renderTasks) {
        BakedGeoModel source = resolved.subModel();
        Identifier texture = resolved.subTexture();
        String sourceBone = resolved.subBone();
        CharacterBoneMount mount = resolved.mount();

        if (source == null || texture == null || sourceBone == null) {
            return;
        }

        renderTasks.submitCustomGeometry(renderPassInfo.poseStack(),
                RenderTypes.entityCutout(texture),
                (pose, buffer) -> {
                    PoseStack local = new PoseStack();
                    local.last().set(pose);

                    applyMountTransform(local, mount);

                    RenderPassInfo<R> sourceInfo = RenderPassInfo.create(
                            getRenderer(), renderPassInfo.renderState(), local,
                            renderPassInfo.cameraState(), true);

                    renderIsolatedBone(source, sourceBone, local, sourceInfo, buffer,
                            renderPassInfo.packedLight(), OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
                });
    }

    private static void applyMountTransform(PoseStack poseStack, CharacterBoneMount mount) {
        // 模型坐标是「像素」，1 格 = 16 像素
        poseStack.translate(mount.offsetX() / 16f, mount.offsetY() / 16f, mount.offsetZ() / 16f);

        if (mount.rotationX() != 0) poseStack.mulPose(Axis.XP.rotationDegrees(mount.rotationX()));
        if (mount.rotationY() != 0) poseStack.mulPose(Axis.YP.rotationDegrees(mount.rotationY()));
        if (mount.rotationZ() != 0) poseStack.mulPose(Axis.ZP.rotationDegrees(mount.rotationZ()));

        if (mount.scale() != 1.0f) poseStack.scale(mount.scale(), mount.scale(), mount.scale());
    }

    /**
     * 只渲染 {@code wantedBone} 这一支。
     *
     * <h2>优化路径：沿预编译骨骼树直走</h2>
     * 目标骨骼拿到的矩阵是「模型根位姿 · 根到目标每一级的 {@code prepMatrixForBone}」，
     * 这一点在「全树隐藏 + 只开这一支」和「只走这一支」两条路上完全相同（隐藏只影响几何写不写，
     * 不影响祖先链的位姿累乘）。所以优化路径可以直接沿
     * {@link CompiledBone#pathFromRoot()} 把祖先链摆完，再让 {@link BoneWalker} 写这一支的几何：
     * <b>不建任何 {@code BoneSnapshot}、不做全树递归、不碰共享的 baked model</b>。
     *
     * <h2>兜底路径：全树隐藏</h2>
     * 源模型不被预编译接管（含非 {@code CuboidGeoBone} 的自定义骨骼），或者优化总开关被关掉时，
     * 回退到兜底做法：全模型先隐藏，再把目标骨骼的祖先链与它自己打开。
     *
     * <p>GL5 没有 {@code GeoBone.setHidden}，骨骼显隐是渲染趟内的
     * {@code frameSnapshot}；兜底路径自己建、自己清，不会污染共享的 baked model。
     * 优化路径根本不碰 {@code frameSnapshot}，所以连这个风险都没有。</p>
     *
     * @param poseStack  已经摆好「源模型根位姿」的栈（调用方负责它的生命周期）
     * @param sourceInfo 传给骨骼位置监听的渲染趟信息
     */
    private static <SR extends GeoRenderState> void renderIsolatedBone(
            BakedGeoModel model, String wantedBone, PoseStack poseStack,
            RenderPassInfo<SR> sourceInfo, VertexConsumer buffer,
            int packedLight, int packedOverlay, int color) {

        final int flags = RenderOptimize.characterFlags();
        if (flags != 0) {
            final CompiledGeoModel compiled = GeoCompileCache.get(model);
            final CompiledBone target = compiled == null ? null : compiled.bone(wantedBone);

            if (target != null) {
                BoneWalker.renderBoneSubtree(target, poseStack, sourceInfo, buffer,
                        packedLight, packedOverlay, color, flags);
                return;
            }
        }

        renderIsolatedBoneLegacy(model, wantedBone, sourceInfo, buffer, packedLight, packedOverlay, color);
    }

    /**
     * 兜底实现：全模型隐藏 + 只把目标骨骼这一支打开，然后整模型渲染一次。
     *
     * <p>它同时是「优化关掉时的同场景对照档」—— 把 {@code render-optimize.character-geometry}
     * 关掉，挂点渲染就完全回到这条路上。</p>
     */
    private static <SR extends GeoRenderState> void renderIsolatedBoneLegacy(
            BakedGeoModel model, String wantedBone, RenderPassInfo<SR> sourceInfo,
            VertexConsumer buffer, int packedLight, int packedOverlay, int color) {

        List<GeoBone> touched = new ArrayList<>();

        try {
            for (GeoBone root : model.topLevelBones()) {
                hideAll(root, touched);
            }

            model.getBone(wantedBone).ifPresent(bone -> {
                // 祖先链：自身不画，但子级要往下走
                for (GeoBone node = bone; node != null; node = node.parent()) {
                    snapshot(node, touched).skipChildrenRender(false);
                }
                // 目标骨骼：画自己 + 子树
                snapshot(bone, touched).skipRender(false).skipChildrenRender(false);
            });

            model.render(sourceInfo, buffer, packedLight, packedOverlay, color);
        } finally {
            for (GeoBone bone : touched) {
                bone.frameSnapshot = null;
            }
        }
    }

    private static void hideAll(GeoBone bone, List<GeoBone> touched) {
        snapshot(bone, touched).skipRender(true).skipChildrenRender(true);
        for (GeoBone child : bone.children()) {
            hideAll(child, touched);
        }
    }

    private static BoneSnapshot snapshot(GeoBone bone, List<GeoBone> touched) {
        if (bone.frameSnapshot == null) {
            bone.frameSnapshot = BoneSnapshot.create(bone);
            touched.add(bone);
        }
        return bone.frameSnapshot;
    }

    // ==================== 工具 ====================

    /** 这根骨骼自己或它的子树里有没有方块。{@code CuboidGeoBone} 是 GL5 里 {@code GeoBone} 的唯一实现。 */
    private static boolean hasGeometry(GeoBone bone) {
        if (bone instanceof CuboidGeoBone cuboid && cuboid.cubes.length > 0) {
            return true;
        }
        for (GeoBone child : bone.children()) {
            if (hasGeometry(child)) {
                return true;
            }
        }
        return false;
    }

    /** 模型里所有骨骼名，拼成一行，方便对着改。 */
    private static String boneNames(BakedGeoModel model) {
        List<String> names = new ArrayList<>();
        for (GeoBone root : model.topLevelBones()) {
            collectBoneNames(root, names);
        }
        return String.join(", ", names);
    }

    private static void collectBoneNames(GeoBone bone, List<String> out) {
        out.add(bone.name());
        for (GeoBone child : bone.children()) {
            collectBoneNames(child, out);
        }
    }

    private static void warnOnce(String key, String message, Object... args) {
        if (WARNED.add(key)) {
            LOGGER.warn(message, args);
        }
    }

    // ==================== 数据来源 ====================

    /** 角色声明的骨骼挂点；没声明就是空列表。 */
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
