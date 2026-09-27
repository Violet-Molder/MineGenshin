package com.linweiyun.genshin.client.render.character;

import com.linweiyun.genshin.core.character.CharacterHelper;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.client.render.character.appearance.CharacterAppearanceBones;
import com.linweiyun.genshin.client.combat.state.BodyYawSync;
import com.linweiyun.genshin.client.render.character.appearance.CharacterFaceBones;
import com.linweiyun.genshin.client.render.character.appearance.CharacterPropBones;
import com.linweiyun.genshin.client.render.character.appearance.CharacterPuppetBones;
import com.linweiyun.genshin.client.render.character.appearance.CharacterAppearanceOptionBones;
import com.linweiyun.genshin.client.combat.state.AnimationStateSync;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderRepository;
import com.mojang.blaze3d.vertex.PoseStack;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.base.BoneSnapshots;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = Minegenshin.MOD_ID, value = Dist.CLIENT)
public final class CharacterRenderDispatcher {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);

    /** 排查用（见 {@link #probeProps}）：动画日志干净、身后却还闪木偶残影时用的那一组日志。 */
    private static final Logger ANIM_LOGGER = ModLog.getLogger(LogGroup.ANIMATION);

    /** 上一次给本机玩家提交模型时的 frameKey（用来发现「同一帧画了两次」）。 */
    private static long lastSubmitFrameKey = Long.MIN_VALUE;

    /** 上一次打过日志的「本帧会画出哪些道具」，变了才打（见 {@link #probeProps}）。 */
    private static String lastProbedProps = null;

    /**
     * 排查用（用户口径 2026-09-27：「闪一帧、日志里动画没变、模型却换了个姿态」）：
     * <b>每渲染帧一行角色骨骼姿态指纹</b>，并给出与上一帧的差值。
     *
     * <p>为什么整帧都打而不是只打异常帧：异常是"跳变"，但正常动作切换也会跳变，
     * 阈值不好拍脑袋定；全打下来（十几秒约几百行）之后按差值排序看头部，
     * 再对着动画日志一看就知道那一帧的跳变**有没有伴随动画切换** —— 没有伴的就是我们要找的。
     */
    private static final String[] POSE_PROBE_BONES = {
            "Root", "root", "Waist", "UpBody", "UpperBody", "Body", "Robot_Root", "RightSword", "LeftSword"};
    private static int poseProbeFrame = -1;
    private static float poseProbeLast = Float.NaN;

    private static void probePose(Player player, BoneSnapshots snapshots, int frame) {
        if (frame == poseProbeFrame) {
            return;
        }
        poseProbeFrame = frame;

        float sum = 0f;
        StringBuilder detail = new StringBuilder();
        for (String bone : POSE_PROBE_BONES) {
            final var snapshot = snapshots.get(bone).orElse(null);
            if (snapshot == null) {
                continue;
            }
            sum += snapshot.getRotX() * 0.9f + snapshot.getRotY() * 1.7f + snapshot.getRotZ() * 1.3f
                    + snapshot.getTranslateY() * 5f + snapshot.getScaleX() * 11f + snapshot.getScaleY() * 7f;
            if (detail.length() > 0) {
                detail.append(' ');
            }
            detail.append(bone).append("=(")
                    .append(Math.round(snapshot.getRotX())).append(',')
                    .append(Math.round(snapshot.getRotY())).append(',')
                    .append(Math.round(snapshot.getRotZ())).append(")s")
                    .append(Math.round(snapshot.getScaleX() * 100) / 100f)
                    // 位移也要打：实测那些 Δ 跳变都发生在**没被打印的**平移量上（根骨骼被挪了 ~2 单位）
                    .append(" tY=").append(Math.round(snapshot.getTranslateY() * 100) / 100f)
                    .append(" tZ=").append(Math.round(snapshot.getTranslateZ() * 100) / 100f);
        }
        final float delta = Float.isNaN(poseProbeLast) ? 0f : Math.abs(sum - poseProbeLast);
        poseProbeLast = sum;
        // 用户口径 2026-09-27：Δ < 1 的正常抖动不要打，否则日志量太大（一帧一行刷屏）
        // 用户口径：动画已放慢到 4 秒一圈，这一轮**逐帧全打**（慢速下正常帧 Δ 很小，
        // 真出问题的帧一眼就能从数字里挑出来）。看完这轮再把阈值加回去。
        if (delta < 2f) {
            return;
        }
        ANIM_LOGGER.info("姿态 frame={} Δ={} 状态={} 刻={} {}",
                frame, Math.round(delta * 10) / 10f, AnimationStateSync.stateOf(player),
                player.tickCount, detail);
    }

    /**
     * 排查开关：{@code -Dminegenshin.debug.noSupportLayers=true} 时<b>不挂</b>挂点层与半透明层。
     *
     * <p>用户口径（2026-09-27）：动画日志干净、显隐探针也说木偶被藏着，画面上却照样出现它 ——
     * 而这两层是全仓唯一「绕开 {@code BoneSnapshot} 显隐、直接遍历方块」的路径
     * （{@code PerBoneRender}，代码注释里自己写了「别的渲染趟挂在这根骨骼上的隐藏对它无效」）。
     * 所以先把它们摘掉做一次 A/B：摘掉就不闪 ⇒ 问题在这两层；照旧闪 ⇒ 另有其人。
     */
    private static final boolean DEBUG_NO_SUPPORT_LAYERS =
            Boolean.getBoolean("minegenshin.debug.noSupportLayers");

    /** 同上，但走配置项（不用改 JVM 参数）：{@code performance.toml} 的 debug-disable-support-layers。 */
    private static boolean supportLayersDisabled() {
        return DEBUG_NO_SUPPORT_LAYERS
                || (com.linweiyun.genshin.config.PerformanceConfig.DEBUG_DISABLE_SUPPORT_LAYERS != null
                        && com.linweiyun.genshin.config.PerformanceConfig.DEBUG_DISABLE_SUPPORT_LAYERS.get());
    }

    /**
     * 渲染帧序号：{@link #onRenderFramePre} 每帧 +1。
     *
     * <p>「同一帧提交了两次」这类判断必须用<b>真的帧号</b>：早先那版用的是
     * {@code tickCount + partialTick}，单机开菜单暂停时这两个值都不动，
     * 于是世界里每帧照画都算成「同一帧」，日志被刷了 800 多行假警报（用户实测）。
     */
    private static int renderFrameCounter;

    @SubscribeEvent
    public static void onRenderFramePre(RenderFrameEvent.Pre event) {
        renderFrameCounter++;
    }

    /** 当前渲染帧号（排查用；给别的类判断"是不是同一帧"）。 */
    public static int renderFrame() {
        return renderFrameCounter;
    }

    /**
     * 排查用：本机玩家这一帧会不会把木偶套件的哪一根画出来。
     *
     * <p>用户口径（2026-09-27）：「在闪但是日志没问题」+「闪的木偶是侧着的」——
     * 动画那条线已经排除，所以这里直接在<b>提交那一刻</b>看骨骼快照的显隐：
     * 只要有一根（木偶 / 坐骑 / 屏幕 / 红茶 / 主副手武器）是「没被藏」的状态，
     * 就把这一帧记下来，集合一变才打一行，避免每帧刷屏。
     */
    private static void probeProps(Player player, BoneSnapshots snapshots) {
        StringBuilder shown = new StringBuilder();
        for (String bone : new String[]{"FJO", "MFly", "ysmGlow_texiao", "tea", "RightSword", "LeftSword"}) {
            snapshots.get(bone).ifPresent(snapshot -> {
                // 两个条件都要：没被 skip 掉、而且没有被缩成 0（本模组的隐藏是「skip + scale 0」两板斧，
                // 只看 skip 会把「缩到 0 所以画不出来」的那些也算进来）
                boolean collapsed = Math.abs(snapshot.getScaleX()) < 1e-4f
                        && Math.abs(snapshot.getScaleY()) < 1e-4f
                        && Math.abs(snapshot.getScaleZ()) < 1e-4f;
                if (!snapshot.isHidden() && !snapshot.areChildrenHidden() && !collapsed) {
                    shown.append('[').append(bone).append(']');
                }
            });
        }
        String key = shown.toString();
        if (key.equals(lastProbedProps)) {
            return;
        }
        lastProbedProps = key;
        ANIM_LOGGER.info("本帧会画出的道具 {}（状态={}，刻={}）",
                key.isEmpty() ? "（无）" : key, AnimationStateSync.stateOf(player), player.tickCount);
    }

    private static final Map<String, CharacterPlayerModel> MODELS = new HashMap<>();
    private static final Map<String, CharacterRenderer> RENDERERS = new HashMap<>();
    private static final Map<Player, GenshinReplacedPlayer> ANIMATABLES = new WeakHashMap<>();

    private CharacterRenderDispatcher() {}

    /**
     * 把玩家这一趟的渲染换成角色模型。
     *
     * <p><b>没有任何「不做替换」的分支</b>：只要是原神模式、在场角色有 id，就用 GeckoLib 模型
     * 接管这一趟（资源落在哪一份由 {@code AssetFallback} 决定，读不到也有兜底的那一份）。
     */
    public static boolean handleSubmit(AvatarRenderState state, PoseStack poseStack,
                                        SubmitNodeCollector submitNodeCollector,
                                        CameraRenderState cameraState) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;

        Player player = mc.level.getEntity(state.id) instanceof Player p ? p : null;
        if (player == null) return false;

        if (!AttachmentHelper.isGenshinMode(player)) return false;

        String charId = CharacterHelper.getActiveCharacterId(player);
        if (charId == null || charId.isEmpty()) return false;

        CharacterRenderData data = CharacterRenderRepository.get(charId);
        if (data == null) {
            return false;
        }

        try {
            doRender(poseStack, submitNodeCollector, cameraState, player, charId, data, state.bodyRot);
        } catch (Exception e) {
            LOGGER.error("[CharacterRenderDispatcher] 渲染角色 '{}' 失败", charId, e);
        }

        return true;
    }

    private static void doRender(PoseStack poseStack, SubmitNodeCollector bufferSource,
                                  CameraRenderState cameraState, Player player,
                                  String charId, CharacterRenderData data, float stateBodyRot) {
        RenderTarget target = targetFor(player, charId, data);
        if (target == null) {
            return;
        }

        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);

        // 退出本方法时必须回到「进来时那一层」，否则整帧都会崩。
        //
        // 抛异常的那条路已经踩过一次（2026-09-25 的崩溃报告）：
        // GeckoLib 给「变身实体」（本模组的 GenshinReplacedPlayer，不是 Entity）套了默认的
        // Molang 变量 lambda，动画里一旦出现 query.head_y_rotation 这类「LivingEntity 专属」
        // 查询，就会在取变量时 ClassCastException → 异常在这里被 handleSubmit 捕获吞掉，
        // 但 popPose() 被跳过 → 外层 LevelRenderer.checkPoseStack 立刻抛
        // IllegalStateException: Pose stack not empty → ReportedException 崩游戏。
        //
        // 所以：① 自己的 push/pop 用 try/finally 保住；② 顺便把「中间某一层漏 pop」也兜掉
        //（GeckoLib 内部的 push/pop 也是配对的，异常一样会跳过它）。
        // 根因已在资源侧掐掉：申鹤动画/模型里残余的 Molang 表达式与 molang 骨骼全部删除
        //（shenhe.animation.json / shenhe.geo.json，2026-09-25），GeckoLib 不再注册这些查询。
        // 这里只是不让「任何一次渲染失败」毁掉整帧。
        PoseStack.Pose anchor = poseStack.last();
        poseStack.pushPose();
        try {
        renderCharacter(poseStack, bufferSource, cameraState, player, data, target, partialTick, stateBodyRot);
        } finally {
            while (!poseStack.isEmpty() && poseStack.last() != anchor) {
                poseStack.popPose();
            }
        }
    }

    private static void renderCharacter(PoseStack poseStack, SubmitNodeCollector bufferSource,
                                        CameraRenderState cameraState, Player player,
                                        CharacterRenderData data, RenderTarget target, float partialTick,
                                        float stateBodyRot) {
        float bodyScale = data.bodyScale();
        if (bodyScale != 1.0f) {
            poseStack.scale(bodyScale, bodyScale, bodyScale);
        }

        // 本机玩家的身体朝向**读渲染状态**（renderState.bodyRot），不是活实体：
        // 原版 E 背包页（InventoryScreen#renderEntityInInventoryFollowsAngle）不碰实体，
        // 它把「180 + 鼠标角」写进 renderState.bodyRot（yRot 只留头相对身体的差），
        // 活实体那份还是玩家开背包之前的朝向 —— 照它画，角色在 E 页就整只背对镜头，
        // 而且不管朝哪边开背包都一样（2026-09-27 用户反馈）。
        // 场景里两者是同一个值（都来自 entity 的 yBodyRot，见 LivingEntityRenderer#extractRenderState），
        // 所以本机换成渲染状态不会改变第三人称的样子；这么一来本页的变换就是原版的
        // `180 - bodyRot`，「模型跟着鼠标转」这条也照旧（bodyRot 里带着鼠标角）。
        //
        // 别人的身体朝向仍得读同步值 —— 原版客户端根本不发 yBodyRot，
        // 不发就会出现「我这边侧着走、他那边看我始终正对着他」（见 BodyYawSync 类注释）。
        float bodyYaw = player == Minecraft.getInstance().player
                ? stateBodyRot
                : BodyYawSync.renderYaw(player, partialTick);
        poseStack.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        poseStack.mulPose(Axis.YP.rotationDegrees(180));

        // 外观变体：藏掉当前外观没选中的骨骼（腿部三选二 + 猫耳开/关，方案 A）。
        // 每帧都从「角色数据」现读，不缓存在渲染器上 —— 渲染器是按 charId 的单例，
        // 缓存它会在换人 / 页面预览时变成过期值。
        PGCharacter character = CharacterHelper.getCurrentCharacter(player);
        RenderPassInfo.BoneUpdater<GeoRenderState> appearanceUpdater = character == null
                ? null
                : CharacterAppearanceBones.forMask(character.getAppearance());
        // 木偶套件（坐骑 / 法吉偶 / 屏幕 / 齿轮）：按当前动画状态决定这一帧藏哪些，
        // 名单之外的动画（闲置、奔跑、潜行…）四个全藏；顺带把屏幕整体往外推一截
        // —— 见 CharacterPropBones
        RenderPassInfo.BoneUpdater<GeoRenderState> propUpdater = CharacterPropBones.updaterFor(player);
        // 脸：常态藏备用脸（母本 yushe 那棵子树），闭眼姿势换成它的 biyang1 —— 见 CharacterFaceBones
        RenderPassInfo.BoneUpdater<GeoRenderState> faceUpdater = CharacterFaceBones.updaterFor(player);
        // 木偶背后的发条：与状态无关，任何状态都恒转（5 秒一圈、顺时针）—— 见 CharacterPuppetBones
        RenderPassInfo.BoneUpdater<GeoRenderState> puppetUpdater = CharacterPuppetBones.updaterFor(player);
        // 手里那把武器：常态按「常态显示武器」开关给 0 / 1，其余四类永远 0；
        // 动作 / 飞行状态不碰（那是动画自己写的表现）—— 见 CharacterAppearanceOptionBones
        RenderPassInfo.BoneUpdater<GeoRenderState> weaponUpdater = CharacterAppearanceOptionBones.updaterFor(player, character);
        RenderPassInfo.BoneUpdater<GeoRenderState> boneUpdater =
                combine(combine(appearanceUpdater, propUpdater),
                        combine(combine(faceUpdater, puppetUpdater), weaponUpdater));

        // 排查用：本机玩家这一帧提交了几次 + 这一帧会不会画出木偶套件（见 probeProps）
        RenderPassInfo.BoneUpdater<GeoRenderState> submitUpdater = boneUpdater;
        // 排查开关：整包不挂骨骼覆盖（见 PerformanceConfig#DEBUG_DISABLE_BONE_UPDATERS）
        if (com.linweiyun.genshin.config.PerformanceConfig.DEBUG_DISABLE_BONE_UPDATERS != null
                && com.linweiyun.genshin.config.PerformanceConfig.DEBUG_DISABLE_BONE_UPDATERS.get()) {
            submitUpdater = null;
        }
        if (player == Minecraft.getInstance().player && ANIM_LOGGER.isInfoEnabled()) {
            int frameKey = renderFrameCounter;
            if (frameKey == lastSubmitFrameKey) {
                ANIM_LOGGER.info("同一帧第二次提交角色模型（frame={}，状态={}，动画刻={}，partial={}）",
                        frameKey, AnimationStateSync.stateOf(player), player.tickCount, partialTick);
            }
            lastSubmitFrameKey = frameKey;
            submitUpdater = (info, snapshots) -> {
                boneUpdater.run(info, snapshots);
                probeProps(player, snapshots);
                probePose(player, snapshots, renderFrameCounter);
            };
        }

        target.renderer().performRenderPass(target.animatable(), player, poseStack, bufferSource, cameraState,
                15728880, partialTick, submitUpdater);
    }

    /**
     * 把两趟骨骼覆盖合成一趟。
     *
     * <p>{@code performRenderPass} 只收一个 {@link RenderPassInfo.BoneUpdater}，
     * 而「外观变体」与「木偶套件隐藏」是互不相干的两张名单 —— 依次跑一遍即可，
     * 两者操作的都是「把某些骨骼标成不画」，不冲突。
     */
    @Nullable
    public static RenderPassInfo.BoneUpdater<GeoRenderState> combine(
            @Nullable RenderPassInfo.BoneUpdater<GeoRenderState> first,
            @Nullable RenderPassInfo.BoneUpdater<GeoRenderState> second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return (renderPassInfo, snapshots) -> {
            first.run(renderPassInfo, snapshots);
            second.run(renderPassInfo, snapshots);
        };
    }

    // ==================== 渲染三件套 ====================

    /**
     * 一个角色的「模型 + 渲染器 + 动画实例」。
     *
     * <p><b>动画实例必须共用</b>：当前播到哪、过渡到哪都存在 {@code animatable} 里，
     * 第三人称和第一人称各拿一份的话，切视角就会看到动画跳一下。
     */
    public record RenderTarget(CharacterPlayerModel model, CharacterRenderer renderer,
                               GenshinReplacedPlayer animatable) {
    }

    /**
     * 取（必要时创建）某个角色的渲染三件套 —— 第三人称和第一人称都从这里拿。
     *
     * <p>按角色 id 缓存模型与渲染器（含骨骼替换层），按玩家缓存动画实例。
     */
    @Nullable
    public static RenderTarget targetFor(Player player, String charId, CharacterRenderData data) {
        if (player == null || charId == null || charId.isEmpty() || data == null) {
            return null;
        }

        CharacterPlayerModel model = MODELS.computeIfAbsent(charId, k -> {
            CharacterPlayerModel m = new CharacterPlayerModel();
            m.updateRenderData(data);
            return m;
        });

        CharacterRenderer renderer = RENDERERS.computeIfAbsent(charId, k -> {
            CharacterRenderer created = new CharacterRenderer(model);
            if (!supportLayersDisabled()) {
                // 骨骼替换层：按角色的挂点声明，把内容画到指定骨骼上
                created.withRenderLayer(new BoneMountGeoLayer<>(created));
                // 半透明骨骼层：贴图里带半透明像素的骨骼（YSM 发光屏幕）改走混合管线，
                // 否则 GeckoLib 默认的 cutout 会把 α≈0.13 的内屏画成实心
                created.withRenderLayer(new TranslucentBoneGeoLayer<>(created));
            }
            return created;
        });

        GenshinReplacedPlayer animatable = ANIMATABLES.computeIfAbsent(player, k -> new GenshinReplacedPlayer());
        animatable.setPlayerEntity(player);

        // 排查用：这一帧**谁**把这具模型拿去渲染了（世界 / 第一人称 / K 页预览 / U 页预览）。
        // 它们共用同一份 BakedGeoModel，谁先把骨骼快照写一遍都会留在里面 —— 姿态探针里那些
        // 「Δ 跳变但没有动画日志」的帧，就是被这一行里的某个"别的渲染趟"写坏的。
        logRenderPassOncePerFrame(charId);

        return new RenderTarget(model, renderer, animatable);
    }

    /** 每帧每路径只打一次（见 {@link #logRenderPassOncePerFrame} 的调用点）。 */
    private static final Map<String, Integer> RENDER_PASS_LOGGED = new java.util.HashMap<>();

    private static void logRenderPassOncePerFrame(String charId) {
        if (!ANIM_LOGGER.isInfoEnabled()) {
            return;
        }
        final int frame = renderFrameCounter;
        final String path = callerPath();
        // 「世界」是每帧都有的正常那条，不报；只有**别的渲染趟**也动了这具模型时才值得记
        // （用户口径：Δ<1 与每帧都发生的东西都别刷日志）。
        if ("世界".equals(path)) {
            return;
        }
        final String key = frame + "|" + path;
        if (RENDER_PASS_LOGGED.put(key, 1) != null) {
            return;
        }
        if (RENDER_PASS_LOGGED.size() > 64) {
            RENDER_PASS_LOGGED.clear();
        }
        ANIM_LOGGER.info("渲染趟 {} 角色={} frame={}", path, charId, frame);
    }

    /** 从调用栈认一下这次渲染是从哪条路进来的。 */
    private static String callerPath() {
        final StringBuilder stack = new StringBuilder();
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            stack.append(element.getClassName()).append('.');
        }
        final String s = stack.toString();
        if (s.contains("CharacterConfigPage")) {
            return "K页预览";
        }
        if (s.contains("CharacterEquipUI")) {
            return "U页预览";
        }
        if (s.contains("FirstPersonCharacterRenderer")) {
            return "第一人称";
        }
        return "世界";
    }
}
