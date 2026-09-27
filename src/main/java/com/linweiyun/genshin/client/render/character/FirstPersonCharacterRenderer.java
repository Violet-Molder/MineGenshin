package com.linweiyun.genshin.client.render.character;

import com.linweiyun.genshin.core.character.CharacterHelper;

import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderRepository;
import com.linweiyun.genshin.core.system.combat.animation.action.CharacterActions;
import com.linweiyun.genshin.core.system.combat.animation.config.CharacterAnimations;
import com.linweiyun.genshin.core.system.combat.animation.config.FirstPersonAnims;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.linweiyun.genshin.client.render.character.appearance.CharacterPropBones;
import com.linweiyun.genshin.client.render.character.appearance.CharacterFaceBones;
import com.linweiyun.genshin.client.render.character.appearance.CharacterPuppetBones;
import com.linweiyun.genshin.client.render.character.appearance.CharacterAppearanceOptionBones;
import com.mojang.blaze3d.vertex.PoseStack;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;

/**
 * 第一人称下的角色模型 —— <b>把原版那只手换成角色自己的模型</b>。
 *
 * <h2>为什么需要这个类</h2>
 * 原版在第一人称<b>根本不渲染玩家实体</b>（渲染的是 `ItemInHandRenderer` 里那两只
 * 通用手臂 + 手里的物品），所以第三人称那条渲染链路（{@code AvatarRendererMixin}
 * → {@code CharacterRenderDispatcher}）在第一人称下<b>一次都不会被调用</b>。
 * 想在第一次人称看到自己的模型和武器，只能自己画。
 *
 * <h2>画在哪：相机空间</h2>
 * NeoForge 的 {@link RenderHandEvent} 每个手各发一次，带的就是
 * <b>已经摆到相机空间的那张 {@code PoseStack}</b>（原点在相机、{@code -Z} 是视线方向，
 * 上下摆动也已经算进去了）—— 和原版画手臂用的是同一张。所以：
 *
 * <ol>
 *   <li>取消事件 = 原版手臂不画；</li>
 *   <li>在同一张 pose 上把模型摆到相机脚下，再调 {@code performRenderPass} 画一次。</li>
 * </ol>
 *
 * <pre>
 * 模型脚底（相机空间）= ( -offsetX, -(眼高 + offsetY), +offsetZ )
 * </pre>
 *
 * 直觉解释：相机往<b>前</b>移 0.15 格，模型就相对落在相机<b>后面</b> 0.15 格，
 * 于是手臂和武器正好进画面（这就是 {@code FirstPersonCamera.offsetZ} 的默认值）。
 *
 * <h2>哪些情况不接管</h2>
 * 原版第一人称对「弓 / 弩 / 地图 / 望远镜 / 三叉戟 / 盾 / 正在吃东西」都有一套专门表现
 * （拉弓、摊开地图、瞄准镜…），我们的模型画不了那些。这些情况下直接<b>让原版照旧</b>，
 * 而不是用模型硬顶 —— 拉弓拉了个空是最难受的。
 *
 * <h2>开关</h2>
 * 按角色配置：{@code CharacterAnimations.firstPerson()}（{@link FirstPersonAnims}）。
 * {@code DISABLED}（默认）就完全不接管，第一人称还是原版手臂。
 * 开了以后：
 * <ul>
 *   <li>动画文件里有 {@code fp_attack_1} 之类 → 播它（模式 A）；</li>
 *   <li>没有 → 照播第三人称动画，由 {@link FirstPersonAnims.FirstPersonCamera} 摆机位（模式 B）。</li>
 * </ul>
 *
 * <h2>注册在哪</h2>
 * {@link RenderHandEvent} 是<b>游戏总线</b>（{@code NeoForge.EVENT_BUS}）事件，
 * 不是 FML 的 mod 总线事件，所以由 {@code MinegenshinClient.onClientSetup} 显式
 * {@code addListener} 注册 —— 不靠 {@code @EventBusSubscriber} 猜总线。
 */
public final class FirstPersonCharacterRenderer {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);

    /** 头部骨骼名（模型里 {@code head} 的子骨骼是 hair / ear_left / ear_right / eyes / eyelid / eyebrows）。 */
    private static final String HEAD_BONE = "head";

    /**
     * 第一人称下要藏起来的骨骼 —— 头（连同头发/耳朵/眼睛这些子骨骼）。
     *
     * <p><b>为什么必须藏</b>：相机就在眼睛位置，也就是<b>头部模型内部</b>。
     * 这套模型的渲染类型不剔背面（double-sided），所以从里面看会看到头壳的内壁
     * —— 表现就是「视角像在脑袋/胸腔里，中间看不清」。
     * 第一人称游戏藏头是标准做法，比调机位可靠得多。
     *
     * <p>用法是 GeckoLib 5 的<b>逐趟骨骼覆盖</b>：{@code BoneUpdater} 拿到这次
     * render pass 的骨骼快照，把 head 自己与它的子树都标成不渲染。只影响这一趟，
     * 第三人称、其它玩家、以及动画本身都不受影响。
     */
    private static final RenderPassInfo.BoneUpdater<GeoRenderState> HIDE_HEAD =
            (renderPassInfo, snapshots) -> snapshots.ifPresent(HEAD_BONE, head -> {
                head.skipRender(true);
                head.skipChildrenRender(true);
            });

    /** 已经打过一次日志的角色，避免每帧刷屏。 */
    private static final Set<String> LOGGED = new HashSet<>();

    private FirstPersonCharacterRenderer() {
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }

        // 只有第一人称才轮得到我们（第三人称原版也不画手）
        if (!minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }

        String charId = CharacterHelper.getActiveCharacterId(player);
        if (charId == null || charId.isEmpty() || !AttachmentHelper.isGenshinMode(player)) {
            return;
        }

        // 这个角色没开第一人称 → 原版手臂
        CharacterAnimations animations = CharacterActions.animationsFor(player);
        FirstPersonAnims firstPerson = animations == null ? FirstPersonAnims.DISABLED : animations.firstPerson();
        if (firstPerson == null || !firstPerson.enabled()) {
            return;
        }

        // 原版那套专门表现（拉弓、地图、瞄准镜…）优先，不抢
        if (vanillaHandMatters(player)) {
            return;
        }

        // 两只手都取消：整个第一人称表现交给我们的模型
        event.setCanceled(true);

        // 模型只画一次（两只手各发一次事件，取主手那一趟）
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        CharacterRenderData data = CharacterRenderRepository.get(charId);
        if (data == null) {
            return;
        }

        try {
            render(player, charId, data, firstPerson, event);
        } catch (Exception e) {
            LOGGER.error("[FirstPersonCharacterRenderer] 第一人称渲染角色 '{}' 失败", charId, e);
        }
    }

    // ==================== 真正画 ====================

    private static void render(LocalPlayer player, String charId, CharacterRenderData data,
                               FirstPersonAnims firstPerson, RenderHandEvent event) {
        CharacterRenderDispatcher.RenderTarget target =
                CharacterRenderDispatcher.targetFor(player, charId, data);
        if (target == null) {
            return;
        }

        FirstPersonAnims.FirstPersonCamera camera = firstPerson.camera() == null
                ? FirstPersonAnims.FirstPersonCamera.DEFAULT
                : firstPerson.camera();

        // 相机空间：原点在相机、-Z 是视线方向，所以脚底要落在相机下方一个眼高的位置，
        // 再加上「相机相对眼睛」的偏移（offsetZ 正 = 相机沿视线前移 = 模型往后让开）
        float scale = camera.scale() <= 0f ? 1f : camera.scale() * data.bodyScale();
        float eyeHeight = player.getEyeHeight();

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();

        poseStack.translate(-camera.offsetX(), -(eyeHeight + camera.offsetY()), camera.offsetZ());

        if (scale != 1f) {
            poseStack.scale(scale, scale, scale);
        }
        if (camera.pitch() != 0f) {
            poseStack.mulPose(Axis.XP.rotationDegrees(camera.pitch()));
        }
        if (camera.yaw() != 0f) {
            poseStack.mulPose(Axis.YP.rotationDegrees(camera.yaw()));
        }
        if (camera.roll() != 0f) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(camera.roll()));
        }

        // 模型朝向：MC 的实体模型局部前方是 -Z，而相机前方也是 -Z —— 不用转。
        // 原点也不用挪：CharacterRenderer 已经把摆件渲染器那半格补偿去掉了，
        // 模型的脚底就在我们 translate 的那个点上。

        CameraRenderState cameraState = Minecraft.getInstance().gameRenderer
                .gameRenderState().levelRenderState.cameraRenderState;

        // 藏头：相机在头壳内部，不藏就是「从里面看内壁」
        // 木偶套件（坐骑 / 法吉偶 / 屏幕 / 齿轮）同理要藏 —— 第一人称下它们会糊在镜头边缘。
        // 这里用 hideAllUpdater 而不是 updaterFor：第一人称下四个道具一律不画，
        // 不看动画状态（第三人称那条路在普攻/重击里会让屏幕出场）。
        target.renderer().performRenderPass(target.animatable(), player, poseStack,
                event.getSubmitNodeCollector(), cameraState, event.getPackedLight(),
                event.getPartialTick(),
                CharacterRenderDispatcher.combine(HIDE_HEAD,
                        CharacterRenderDispatcher.combine(CharacterPropBones.hideAllUpdater(),
                                CharacterRenderDispatcher.combine(CharacterFaceBones.updaterFor(player),
                                        CharacterRenderDispatcher.combine(
                                                CharacterPuppetBones.updaterFor(player),
                                                // 手里那把武器：第一人称也跟着「常态显示武器」走
                                                //（关着时和以前一样全藏）
                                                CharacterAppearanceOptionBones.updaterFor(
                                                        player, CharacterHelper.getCurrentCharacter(player)))))));

        poseStack.popPose();

        if (LOGGED.add(charId)) {
            LOGGER.info("[FirstPersonCharacterRenderer] 角色 '{}' 第一人称渲染中：眼高={}, 机位=({}, {}, {}) 旋转=({}, {}, {}) 缩放={}",
                    charId, eyeHeight, camera.offsetX(), camera.offsetY(), camera.offsetZ(),
                    camera.pitch(), camera.yaw(), camera.roll(), scale);
        }
    }

    /**
     * 这个玩家手里拿着的东西，原版有专门的第一人称表现吗。
     *
     * <p>对着 {@code ItemInHandRenderer} 的分支抄的：地图、弩、弓、望远镜、三叉戟、
     * 盾、以及任何「正在使用中」的物品（吃东西、喝药、拉弓…）都有专用变换，
     * 我们的角色模型画不出这些动作，所以这些情况让原版上。
     */
    private static boolean vanillaHandMatters(LocalPlayer player) {
        if (player.isUsingItem() || player.isScoping()) {
            return true;
        }

        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.isEmpty()) {
            return false;
        }

        return mainHand.getItem() instanceof MapItem
                || mainHand.is(Items.BOW)
                || mainHand.is(Items.CROSSBOW)
                || mainHand.is(Items.SPYGLASS)
                || mainHand.is(Items.TRIDENT)
                || mainHand.is(Items.SHIELD);
    }
}
