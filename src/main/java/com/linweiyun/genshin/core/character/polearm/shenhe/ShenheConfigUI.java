package com.linweiyun.genshin.core.character.polearm.shenhe;

import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.linweiyun.genshin.client.render.gui.component.CustomToggle;
import com.linweiyun.genshin.client.render.character.CharacterRenderDispatcher;
import com.linweiyun.genshin.client.render.character.GenshinPreviewPlayer;
import com.linweiyun.genshin.client.render.character.appearance.CharacterAppearanceBones;
import com.linweiyun.genshin.config.character.ShenheTalentConfig;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.ICharacterConfigUI;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.PGCharacterData;
import com.linweiyun.genshin.core.character.appearance.SockType;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderRepository;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.content.attribute.AttributeType;
import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ToggleGroupElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Locale;

/**
 * 申鹤的<b>角色配置页</b>（按键 N 打开）—— LDLib2 构建，科技风。
 *
 * <h2>页面上有什么</h2>
 * <ol>
     *   <li><b>3D 模型预览</b>：左边一块 {@link Scene}，用<b>游戏内的同一套渲染三件套</b>
     *       把申鹤画进去（{@code CharacterRenderDispatcher}），鼠标左键拖拽转视角、滚轮缩放
     *       —— 交互是 {@code Scene} 自带的。播的是 {@link #PREVIEW_ANIMATION}（闲置-喝茶）
     *       那一条姿势，另外把模型自带那块浮游屏摆远、缩小（见 {@link #PREVIEW_SCREEN_PARK}）。</li>
 *   <li><b>外观</b>：左右腿<b>各自</b>两组控件 —— 穿不穿鞋（{@link Toggle}）+
 *       裸腿/白丝/黑丝（{@link Selector}），再加一个猫耳挂件的显隐开关。
 *       点一下立刻在预览里生效。</li>
 *   <li><b>角色面板 / 技能倍率</b>（右下那口信息框，可切页）：
 *       <ul>
 *         <li><b>角色面板</b>（默认页）：基础属性（生命值上限 / 攻击力 / 防御力 是「白字总值 +
 *             绿字额外值」，绿字 = 总值 − base，百分比加成已折算成确定值；元素精通与体力上限
 *             只有总值）＋ 进阶属性 ＋ 元素属性，见 {@link #buildStatsScroller}；</li>
 *         <li><b>技能倍率</b>：{@link ShenheTalentConfig} 里登记过的那些项，按分组列进
 *             {@link ScrollerView}，改完立刻写本地配置并发服务端；
 *             <b>只有作弊模式（权限等级 ≥ 2）才有这一页与页签</b>，见 {@link #buildInfoPane}。</li>
 *       </ul></li>
 * </ol>
 *
 * <h2>为什么预览用的是「页面自己的掩码」而不是角色的掩码</h2>
 * 玩家点选后要<b>先看到效果再决定</b>，所以页面持有一个 {@code int[] previewMask}
 * 作为预览专用外观；角色数据只在这个数组被写入时跟着改一次
 * （{@code PGCharacter#setLegSock / setLegShoes / setCatEarsVisible}），两边始终一致但互不干扰：
 * 就算以后要做「确认后才提交」，也只需要把写角色数据那几行挪到按钮里。
 *
 * <h2>组件高度为什么要在 Java 里再写一遍</h2>
 * {@code Label}/{@code Toggle}/{@code Selector}/{@code TextField} 在自己构造器里设的高度
 * 会先落到 STYLESHEET 之上（{@code UIElement#internalSetup} 把构造期的 INLINE 降成 DEFAULT
 * 是「构造结束」那一刻的事），而 {@code ModularUI} 首帧算样式时 lss 还没结算 ——
 * 所以高度这一处仍由 Java 的 INLINE 兜第一帧。静态颜色、背景、间距全在
 * {@code assets/minegenshin/lss/character_config.lss}（lss 的 STYLESHEET 优先级本来就压得住
 * 组件构造器里那些 DEFAULT 背景，本页内置控件的科技风配色都走它）。
 */
public class ShenheConfigUI implements ICharacterConfigUI {

    private static final Identifier STYLESHEET =
            Identifier.parse("minegenshin:lss/character_config.lss");

    /**
     * 预览机位：模型脚底在世界原点，视点中心要抬到「角色重心」的高度。改这四个即可。
     *
     * <h2>{@link #PREVIEW_CENTER_Y} 怎么定的</h2>
     * <b>不是</b>「模型包围盒高度的一半」—— 按 {@code shenhe.geo.json} 的包围盒算，中位线是 0.93
     * （本地 y 从 -0.72 的浮游台面到 2.58 的帽顶），可底下那截是<b>台面</b>，
     * 角色本体只占 <b>0 → 2.14</b>（脚在 y=0）。视点落在 0.93 = 角色重心的<b>下方</b>，
     * 于是角色整只浮在框的上半、下面空一大片（玩家口径：「位置太靠上了，居中甚至靠下一点」）。
     *
     * <p>用上一版的实机截图标定过：场景高 3.23 格 ≈ 1132 px、角色从帽顶到脚底 ≈ 732 px；
     * 视点 0.95 时上方留白 182 px、下方 218 px，真正居中要 1.03。
     * 1.15 试完玩家口径是「再往下一些」，于是再抬 0.15（≈ 50 px）到 <b>1.30</b>。
     * 再想微调只动这一个数：<b>值越大模型越靠下</b>。
     *
     * <p>{@code zoom} 是<b>相机到视点的距离</b>（透视；{@code Scene#camZoom()} 在默认的
     * {@code useOrtho=false} 下直接返回 zoom，见 LDLib2 的 {@code Scene} /
     * {@code WorldSceneRenderer#setCameraLookAt}），所以这个值<b>越大 = 相机越远 = 模型越小</b>。
     * 想让整只模型（连它旁边那块屏幕）再小一点、再远一点，就往上加这一处即可。
     */
    private static final float PREVIEW_CENTER_Y = 1.30f;
    private static final float PREVIEW_ZOOM = 2.8f;
    private static final float PREVIEW_YAW = 115f;
    private static final float PREVIEW_PITCH = 12f;

    /**
     * 预览里播的那条动画：申鹤模型里的 {@code extra48} = YSM 里的「闲置-喝茶」，一条<b>循环的
     * 静态姿势</b>（34 根骨骼全是常量值，没有时间轴，计时没有意义）。
     *
     * <p>单开一个常量而不是散在 {@link GenshinPreviewPlayer} 里写死：以后换姿势、或者想让
     * 别的角色也这么干，改造这里一行即可。名字在当前角色的动画文件里不存在时控制器会自己退回
     * idle（见 {@code PlayerAnimationController#resolvePreviewAnimation}），不会切进空状态。
     */
    private static final String PREVIEW_ANIMATION = "extra48";

    /** 浮游屏（内屏 + 外框，本来就是屏幕整体）与它右上角那颗齿轮：两根都是模型的顶层骨骼。 */
    private static final String SCREEN_BONE = "ysmGlow_texiao";
    private static final String SCREEN_GEAR_BONE = "ysmGlow_texiao2";

    /**
     * 预览里把模型自带那块浮游屏「摆远 + 缩小」的量。
     *
     * <h2>为什么要在页面里单独挪它</h2>
     * 这块屏在模型里是<b>贴在角色胸口正前方</b>的（pivot z = -8 = 往前 0.5 格），常态在游戏里
     * 由 {@code CharacterPropBones} 整组藏掉，只有「木偶·重击」那几秒才出场 —— 页面预览不吃那份
     * 隐藏，于是屏幕就贴在申鹤胸口上、还把角色挡掉一块。这里只在<b>本页的这趟渲染</b>里把它往前
     * 推开一截并缩到 {@value #PREVIEW_SCREEN_SCALE} 倍：离角色远一点，本身也小一点。
     *
     * <p>单位是<b>模型单位（1/16 格）</b>，和动画里 {@code position} 的写法一致；负的 z 是「往模型
     * 正前方推」—— 页面的相机就摆在模型正前方，所以推出去在画面里就是「离胸口更远」。
     * 嫌不够就加大这两条：{@code PUSH} 更负 = 更远，{@code SCALE} 更小 = 更小。
     */
    private static final float PREVIEW_SCREEN_PUSH = -8f;
    private static final float PREVIEW_SCREEN_SCALE = 0.6f;

    /**
     * 齿轮要补的那段位移（模型单位）：两块骨骼的 pivot 不一样（见 shenhe.geo.json，
     * 屏幕 {@code (-0.25, 22, -8)}、齿轮 {@code (-7.5, 22.5, -8.025)}），各自绕自己的 pivot 缩完之后
     * 想和「整组绕屏幕 pivot 缩」重合，就要补 {@code (1 - 缩放) * (屏幕 pivot - 齿轮 pivot)}。
     * 不补的话齿轮会从屏幕右上角飘出去。
     */
    private static final float GEAR_SCALE_COMP_X =
            (1f - PREVIEW_SCREEN_SCALE) * (-0.25f - -7.5f);
    private static final float GEAR_SCALE_COMP_Y =
            (1f - PREVIEW_SCREEN_SCALE) * (22f - 22.5f);
    private static final float GEAR_SCALE_COMP_Z =
            (1f - PREVIEW_SCREEN_SCALE) * (-8f - -8.025f);

    /** 全亮：预览里不需要跟着世界光照变暗（和第三人称那条路用的同一个值）。 */
    private static final int FULL_BRIGHT = 15728880;

    /**
     * 预览专用的骨骼覆盖：把浮游屏与齿轮推远、缩小（数值推导见 {@link #PREVIEW_SCREEN_PUSH}）。
     *
     * <p>走 {@code RenderPassInfo.BoneUpdater} 而不是改动画/改 geo：这是「本页这一趟渲染」的临时
     * 偏移，改动画会污染别处，改 geo 会连游戏里那块胸屏一起动。做法与外观变体
     * （{@code CharacterAppearanceBones}）完全同一条路。
     *
     * <p>位移是<b>叠加</b>在动画当前值上的（先读再写），所以换姿势、换动画都不会丢掉动画自己的偏移。
     */
    private static final RenderPassInfo.BoneUpdater<GeoRenderState> PREVIEW_SCREEN_PARK =
            (renderPassInfo, snapshots) -> {
                snapshots.ifPresent(SCREEN_BONE, snapshot -> {
                    snapshot.setTranslateZ(snapshot.getTranslateZ() + PREVIEW_SCREEN_PUSH);
                    snapshot.setScale(snapshot.getScaleX() * PREVIEW_SCREEN_SCALE,
                            snapshot.getScaleY() * PREVIEW_SCREEN_SCALE,
                            snapshot.getScaleZ() * PREVIEW_SCREEN_SCALE);
                });
                snapshots.ifPresent(SCREEN_GEAR_BONE, snapshot -> {
                    snapshot.setTranslateX(snapshot.getTranslateX() + GEAR_SCALE_COMP_X);
                    snapshot.setTranslateY(snapshot.getTranslateY() + GEAR_SCALE_COMP_Y);
                    snapshot.setTranslateZ(
                            snapshot.getTranslateZ() + PREVIEW_SCREEN_PUSH + GEAR_SCALE_COMP_Z);
                    snapshot.setScale(snapshot.getScaleX() * PREVIEW_SCREEN_SCALE,
                            snapshot.getScaleY() * PREVIEW_SCREEN_SCALE,
                            snapshot.getScaleZ() * PREVIEW_SCREEN_SCALE);
                });
            };

    /**
     * 预览专用的动画代理：只播 {@link #PREVIEW_ANIMATION} 这一条（见 {@link GenshinPreviewPlayer}）。
     *
     * <p>不用 {@code CharacterRenderDispatcher} 里那份共享实例，是因为那份跟着玩家的真实
     * 状态走 —— 开页面前刚打完一招，预览就会定格在攻击的最后一帧。模型与渲染器照旧从
     * 调度器拿（缓存共用），只有动画这一份是页面自己的。
     */
    private final GenshinPreviewPlayer previewAnimatable = new GenshinPreviewPlayer(PREVIEW_ANIMATION);

    @Override
    public Component title() {
        return Component.translatable("gui.minegenshin.character_config.title");
    }

    @Override
    public ModularUI createConfigUI(Player player, PGCharacter character) {
        // 预览专用外观：初值 = 角色当前值
        int[] previewMask = {character.getAppearance()};

        UIElement root = new UIElement().setId("cc-root");
        // 根元素的 100% x 100% 必须在这里用 INLINE 再写一遍（lss 里那份是同一件事，但不够）：
        // ModularUI.init 会**先**读根元素的 width/height 候选值来决定 taffy 的可用空间，再算样式；
        // 而 lss 的候选是元素注册后才进样式袋的，第一次 init 那一刻读不到 -> 被当成 auto
        // -> available space 变 MAX_CONTENT -> 页面里所有百分比和 aspect-rate 全部失效，
        // 表现就是「刚打开是个接近正方形、内容溢出的错版，拉一下窗口重新 init 才对」。
        root.layout(l -> {
            l.widthPercent(100);
            l.heightPercent(100);
        });
        UIElement window = new UIElement().setId("cc-window");

        UIElement titlebar = new UIElement().setId("cc-titlebar");
        Label title = new Label();
        title.setId("cc-title");
        title.setText(title());
        title.layout(l -> l.height(16));
        titlebar.addChild(title);

        UIElement titleLine = new UIElement().setId("cc-title-line");

        UIElement content = new UIElement().setId("cc-content");
        content.addChildren(buildPreview(player, previewMask),
                buildRightPane(player, character, previewMask));

        UIElement body = new UIElement().setId("cc-body");
        body.addChildren(titlebar, titleLine, content);
        window.addChild(body);
        root.addChild(window);

        var stylesheet = StylesheetManager.INSTANCE.getStylesheetSafe(STYLESHEET);
        return ModularUI.of(UI.of(root, stylesheet), player);
    }

    /**
     * 预览块的两行说明文字：上一条是操作提示（本来就有的），下一条是<b>模型作者署名</b>。
     *
     * <p>署名只在渲染定义声明了作者时才出现（{@link CharacterRenderData#modelAuthor()}），
     * 当前这套模型的作者写在 {@code ShenheResources} 里。点了直接开浏览器 ——
     * 走 {@code Util.getPlatform().openUri}，不弹原版那个确认框：
     * 弹框会把当前的 ModularUI 屏幕换成确认屏，回来时整棵控件树（含 Scene 的渲染资源）
     * 要走一遍 removed/init，代价和风险都比「直接开」大，而这条链接是我们自己写在代码里的。
     */
    private static UIElement finishPreview(UIElement preview, @Nullable CharacterRenderData data) {
        // The hint belongs to the Scene inside this panel: it describes how to drag the model.
        // It used to sit at the right end of the title bar, which is on top of the frame decoration.
        Label hint = new Label();
        hint.setId("cc-preview-hint");
        hint.setText(Component.translatable("gui.minegenshin.character_config.hint"));
        preview.addChild(hint);

        if (data != null && data.modelAuthor() != null) {
            Label credit = new Label();
            credit.setId("cc-credit");
            credit.setText(Component.translatable(
                    "gui.minegenshin.character_config.model_author", data.modelAuthor()));
            credit.layout(l -> l.height(10));
            if (data.modelAuthorUrl() != null) {
                // 可点的那一份只是「鼠标形状 + 高亮」的差别，文字仍是同一行
                credit.addClass("cc-credit-link");
                String url = data.modelAuthorUrl();
                credit.addEventListener(UIEvents.CLICK, event -> Util.getPlatform().openUri(url));
            }
            preview.addChild(credit);
        }
        return preview;
    }

    // ==================== 左边：3D 模型预览 ====================

    private UIElement buildPreview(Player player, int[] previewMask) {
        UIElement preview = new UIElement().setId("cc-preview");

        Scene scene = new Scene();
        scene.setId("cc-scene");
        // 本页所有「不需要裁剪」的元素都不参与裁剪（三类都写在下面各自的构造点）：
        // LDLib2 会把带 overflow:hidden 的元素的**内容盒**交给 PreciseScissor 量化，内容盒
        // 一旦退化成 0 宽/0 高，量化结果就是 0 像素的 scissor，而 MC 26.2 的
        // RenderPass#enableScissor 对 width<=0 || height<=0 是直接抛
        // IllegalArgumentException（实测日志："Scissor size must be >0, was 0x32"）——表现就是按 N
        // 打开页面立刻崩在 GuiRenderer.executeDraw。Scene 的内容本来就画在「元素矩形大小」
        // 的离屏贴图上再贴回来，裁剪关掉之后不会有任何可见溢出。
        scene.setOverflowVisible(true);
        preview.addChild(scene);

        var level = Minecraft.getInstance().level;
        CharacterRenderData data = CharacterRenderRepository.get(Shenhe.ID);
        if (level == null || data == null) {
            // 理论上到不了这里（页面只在客户端、且有模型数据时才打开）；
            // 真到了就当「只有一块空面板」，不要让异常掀整个页面。
            return finishPreview(preview, data);
        }

        scene.createScene(level);
        scene.setCenter(new Vector3f(0f, PREVIEW_CENTER_Y, 0f));
        scene.setZoom(PREVIEW_ZOOM);
        scene.setCameraYawAndPitch(PREVIEW_YAW, PREVIEW_PITCH);
        scene.setShowHoverBlockTips(false);
        scene.setRenderSelect(false);
        scene.setRenderFacing(false);

        WorldSceneRenderer renderer = scene.getRenderer();
        if (renderer == null) {
            return finishPreview(preview, data);
        }

        // 模型在「场景自带的提交阶段之后、dispatch 之前」提交：
        // 这个钩子拿到的 PoseStack 是场景的世界坐标基准（相机变换在 view 矩阵里），
        // 所以模型按世界坐标摆就行，不用自己去补相机。
        renderer.setAfterBuiltinSubmit(ctx -> {
            CharacterRenderDispatcher.RenderTarget target =
                    CharacterRenderDispatcher.targetFor(player, Shenhe.ID, data);
            if (target == null) {
                return;
            }
            // 预览这份动画代理只是「本帧渲染的是谁」的临时引用，每帧刷新一次
            previewAnimatable.setPlayerEntity(player);
            PoseStack poseStack = ctx.poseStack();
            poseStack.pushPose();
            try {
                // 模型局部前方是 -Z（和原版实体一致），转 180 让正面朝向相机默认那一侧。
                poseStack.mulPose(Axis.YP.rotationDegrees(180f));
                // 这趟渲染吃两份骨骼覆盖：外观变体（藏掉没选中的腿/耳）+ 把浮游屏推远缩小
                // （页面预览不吃游戏里那份「木偶套件常态隐藏」，见 PREVIEW_SCREEN_PARK）。
                var boneUpdater = CharacterRenderDispatcher.combine(
                        CharacterAppearanceBones.forMask(previewMask[0]), PREVIEW_SCREEN_PARK);
                target.renderer().performRenderPass(previewAnimatable, player, poseStack,
                        ctx.submitStorage(), ctx.cameraState(), FULL_BRIGHT, ctx.partialTicks(),
                        boneUpdater);
            } finally {
                // 钩子在别人的渲染流程里跑，异常时也必须把栈还回去
                poseStack.popPose();
            }
        });

        return finishPreview(preview, data);
    }

    // ==================== 右边：外观 + 倍率 ====================

    private UIElement buildRightPane(Player player, PGCharacter character, int[] previewMask) {
        UIElement right = new UIElement().setId("cc-right");
        right.addChildren(buildAppearance(character, previewMask),
                buildInfoPane(player, character));
        return right;
    }

    private UIElement buildAppearance(PGCharacter character, int[] previewMask) {
        UIElement box = new UIElement().setId("cc-appearance");

        Label title = new Label();
        title.addClass("cc-section-title");
        title.setText(Component.translatable("gui.minegenshin.character_config.appearance"));
        title.layout(l -> l.height(13));
        box.addChild(title);

        box.addChild(buildLegRow(character, previewMask, true));
        box.addChild(buildLegRow(character, previewMask, false));
        box.addChild(buildEarRow(character, previewMask));
        return box;
    }

    /**
     * 一条腿的两组控件。
     *
     * <p>「穿不穿鞋」与「裸腿/白丝/黑丝」是<b>独立</b>的两件事：不穿鞋时袜子类型还会决定
     * 露出来的是哪只脚（{@code jiao1 / bs / hs}），这条耦合规则在
     * {@code LegBoneRules} 里，页面这边不用管。
     */
    private UIElement buildLegRow(PGCharacter character, int[] previewMask, boolean left) {
        UIElement row = new UIElement();
        row.addClass("cc-leg-row");

        Label name = new Label();
        name.addClass("cc-leg-name");
        name.setText(Component.translatable(left
                ? "gui.minegenshin.character_config.leg_left"
                : "gui.minegenshin.character_config.leg_right"));
        // width 交给 lss 的 .cc-leg-name（Java 只补 INLINE 高度：组件构造器里那份会被
        // internalSetup 降成 DEFAULT，本该压得住，但首帧算样式时 lss 还没结算，只有 INLINE 一定生效）
        name.layout(l -> l.height(12));

        Toggle shoes = new Toggle();
        shoes.addClass("cc-shoes-toggle");
        shoes.setText(Component.translatable("gui.minegenshin.character_config.shoes"));
        shoes.layout(l -> l.height(12));
        shoes.setOn(character.hasLegShoes(left), false);
        shoes.setOnToggleChanged(on -> {
            character.setLegShoes(left, on);
            applyPreview(character, previewMask);
        });

        Selector<SockType> sock = new Selector<>();
        sock.addClass("cc-sock-selector");
        sock.layout(l -> l.height(12));
        // UIElementProvider.text 造出来的 Label 自带 overflow:hidden（即裁剪），它作为
        // 选择框里的预览项 / 下拉项时会被父容器压缩到 0 宽 —— 同一个 0 宽 scissor 崩法，
        // 而且点开下拉框那一刻才会踩到。这里包一层把裁剪关掉：候选文字很短，放得下。
        UIElementProvider<SockType> sockProvider =
                UIElementProvider.text(s -> Component.translatable(sockKey(s)));
        // cc-sock-item：下拉里的候选行 + 收起来时显示的那一行，用的是同一个元素，lss 里
        // 一处字号/文字色就能同时管住两边（悬浮态靠 LDLib2 自己加的 __hovered__ 类，见 lss）。
        sock.setCandidateUIProvider(
                s -> sockProvider.apply(s).setOverflowVisible(true).addClass("cc-sock-item"));
        // 先给候选、再设选中：选中态是按候选按钮回填的
        sock.setCandidates(List.of(SockType.values()));
        sock.setSelected(character.getLegSock(left), false);
        sock.setOnValueChanged(value -> {
            if (value == null) {
                return;
            }
            character.setLegSock(left, value);
            applyPreview(character, previewMask);
        });

        row.addChildren(name, shoes, sock);
        return row;
    }

    /**
     * 猫耳挂件一行：名字 + 一个显隐开关。
     *
     * <p>耳朵在模型里是 {@code Head > Ear > (EarRight, EarLeft)} 一棵小子树，
     * 101 个动画里一次都没被引用过，所以显隐只能走渲染期的骨骼隐藏
     * （{@code CharacterAppearanceBones} 里的 {@code EarBoneRules}），这里只管翻掩码的那一位。
     *
     * <p>开关做成<b>一个总开关</b>而不是左右各一个：藏父级 {@code Ear} 就等于两只一起藏，
     * 而页面能用上的信息（「这角色现在有没有耳朵」）本来就只有一个 —— 以后真要做单只，
     * 掩码里再各占一位就行，规则表结构不用动。
     */
    private UIElement buildEarRow(PGCharacter character, int[] previewMask) {
        UIElement row = new UIElement();
        row.addClass("cc-leg-row");

        Label name = new Label();
        name.addClass("cc-leg-name");
        name.setText(Component.translatable("gui.minegenshin.character_config.cat_ears"));
        name.layout(l -> l.height(12));

        Toggle ears = new Toggle();
        ears.addClass("cc-ear-toggle");
        ears.setText(Component.translatable("gui.minegenshin.character_config.show"));
        ears.layout(l -> l.height(12));
        ears.setOn(character.isCatEarsVisible(), false);
        ears.setOnToggleChanged(on -> {
            character.setCatEarsVisible(on);
            applyPreview(character, previewMask);
        });

        row.addChildren(name, ears);
        return row;
    }

    // ==================== 右下信息框：角色面板 / 技能倍率 ====================

    /**
     * 右下那口信息框 —— 一个「角色面板 ↔ 技能倍率」的页签区。
     *
     * <h2>非作弊模式下为什么连页签都不建</h2>
     * 倍率是改伤害的调试口（用户口径：「伤害倍率仅作弊模式下可以控制，非作弊模式下不显示这部分」），
     * 所以非作弊时这里只有角色面板、一个切到倍率的入口都没有 —— 不是把页签灰掉，是根本不建。
     *
     * <p>判据是 {@link Permissions#COMMANDS_GAMEMASTER}，即原版「权限等级 ≥ 2」
     * （单机开了作弊、联机里是 OP、创造模式服务器的管理员都算）。客户端手里这份权限是服务端
     * 通过实体事件 24~28 同步过来的（{@code LocalPlayer#permissions()}），所以这里读玩家的
     * {@code permissions()} 就够，不用再问服务端一次。
     */
    private UIElement buildInfoPane(Player player, PGCharacter character) {
        UIElement box = new UIElement().setId("cc-info");

        UIElement stats = buildStatsScroller(character);
        if (!hasCheatPermission(player)) {
            box.addChild(stats);
            return box;
        }

        UIElement talent = buildTalentScroller();
        // 默认页 = 角色面板（用户口径：「默认打开显示的是角色面板」）
        talent.setDisplay(false);

        // 切页只翻 display，控件树不重建 —— 重建会把两个 ScrollerView 的滚动位置一起丢掉。
        // 「互斥」由 ToggleGroupElement 管：组里同一时刻只有一个 __on__，另一个自动翻回 off，
        // 所以这里不需要自己记「现在亮的是谁」，也不需要手动加/去高亮类。
        CustomToggle tabStats = buildTab("gui.minegenshin.character_config.tab.stats");
        CustomToggle tabTalent = buildTab("gui.minegenshin.character_config.tab.talent");
        tabStats.setOnToggleChanged(on -> {
            if (!on) {
                return;
            }
            stats.setDisplay(true);
            talent.setDisplay(false);
        });
        tabTalent.setOnToggleChanged(on -> {
            if (!on) {
                return;
            }
            stats.setDisplay(false);
            talent.setDisplay(true);
        });

        ToggleGroupElement tabs = new ToggleGroupElement();
        tabs.setId("cc-info-tabs");
        tabs.toggleGroup.setAllowEmpty(false);
        tabs.addChildren(tabStats, tabTalent);
        // 组的「默认选中」不能靠 ToggleGroup 自己 —— registerToggle 只记 currentToggle、
        // 不会把那一项点亮，得显式点一次（和背包那边的分类页签同一个写法）。
        tabStats.setOn(true);

        // 顺序 = 布局顺序（flex 竖排）：页签在顶上，两个面板依次占满剩下的高度
        box.addChildren(tabs, stats, talent);
        return box;
    }

    /** 当前玩家算不算「作弊模式」—— 只看原版权限等级 2（开作弊 / OP）。 */
    private static boolean hasCheatPermission(Player player) {
        return player != null && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    /**
     * 一个页签 = 一枚「文字画在按钮内部」的按钮式开关。
     *
     * <h2>为什么不用 {@link Label}</h2>
     * Label 没有固有宽度（{@code adaptive-width} 默认 false，Label 的构造器也只写 height），
     * 放进<b>横向</b> flex 里，taffy 量不出内容宽度就把它压到「只剩内边距」的十几像素，
     * 文字于是画到了盒子外面 —— 实机就是两行页签文字叠在一起、外面还套着一个小空框。
     * 纵向 flex 里的那些 Label 之所以没事，是被父级的 align-items:stretch 拉满了整行宽度。
     *
     * <p>{@link CustomToggle} 是项目里现成的按钮式 Toggle（{@code client/render/gui/component}）：
     * 按钮铺满整块、文字用一层 {@code TextElement} 居中盖在按钮里，尺寸由我们写死，
     * 不受「文字量不出宽度」这件事影响。
     *
     * <p>高度照旧只在 Java 里兜首帧（见类注释）。
     */
    private static CustomToggle buildTab(String key) {
        CustomToggle tab = new CustomToggle();
        tab.addClass("cc-tab");
        tab.setButtonText(Component.translatable(key));
        tab.layout(l -> l.height(12));
        return tab;
    }

    /**
     * 一个滚动区（角色面板与倍率列表共用同一套兜底尺寸与拖拽手势）。
     *
     * <p>ScrollerView 的 viewPort 自带 5px 内边距：布局一挤，元素尺寸就只剩内边距撑出来的
     * 10px，内容盒正好变成 0 宽 -> 0 宽 scissor -> MC 26.2 的 RenderPass 直接抛异常。
     * 这是本页唯一必须保留的裁剪（滚动裁剪），所以给它一个比内边距大的最小尺寸：
     * 布局再挤也不会退化成 0。写在 Java 而不是 lss 里：首帧样式还没结算，只有 INLINE
     * 的那份一定生效。
     */
    private static ScrollerView newScroller(String id) {
        ScrollerView scroller = new ScrollerView();
        scroller.setId(id);
        scroller.layout(l -> l.minWidth(24));
        scroller.viewPort.layout(l -> l.minWidth(16).minHeight(16));
        installDragScroll(scroller);
        return scroller;
    }

    /**
     * 技能倍率列表（只有作弊模式才会被建出来，见 {@link #buildInfoPane}）。
     *
     * <p>原来的「技能倍率（0 ~ 100）」标题行不再画了：这一页的身份由页签表示，
     * 节标题只留配置自己的三个分组。
     */
    private UIElement buildTalentScroller() {
        ScrollerView scroller = newScroller("cc-talent-scroller");

        UIElement list = new UIElement().setId("cc-talent-list");
        for (String group : ShenheTalentConfig.groups()) {
            Label groupLabel = new Label();
            groupLabel.addClass("cc-group-title");
            groupLabel.setText(Component.translatable(groupKey(group)));
            groupLabel.layout(l -> l.height(12));
            list.addChild(groupLabel);
            for (String key : ShenheTalentConfig.keysOf(group)) {
                list.addChild(buildTalentRow(key));
            }
        }
        scroller.addScrollViewChild(list);
        return scroller;
    }

    /**
     * 角色面板：照原神的角色数据面板分三段 —— 基础属性 / 进阶属性 / 元素属性。
     *
     * <h2>「总值 + 绿字」是怎么算的</h2>
     * {@code AttributeInstance} 的公式是
     * {@code 总值 = base × (1 + 百分比加成) + 固定值加成}，
     * 所以绿字（额外值）取下式就够，<b>百分比那一份已经被公式折算成确定值</b>了：
     * <pre>额外 = 总值 - base</pre>
     * 其中 base 是角色成长表 + 突破给的那一份（也就是「白字」）。
     *
     * <p>只有生命值上限 / 攻击力 / 防御力 三项带绿字（原神也只给这三项），
     * 元素精通与体力上限是纯总值；进阶 / 元素那些本来就是百分比属性，
     * 显示值 = 总值 × 100。
     */
    private UIElement buildStatsScroller(PGCharacter character) {
        ScrollerView scroller = newScroller("cc-stats-scroller");
        PGCharacterData data = character.getData();

        UIElement list = new UIElement().setId("cc-stats-list");

        list.addChild(buildStatsGroupTitle("base"));
        list.addChild(buildBaseStatRow(data, ModAttributes.MAX_HP.value()));
        list.addChild(buildBaseStatRow(data, ModAttributes.ATK.value()));
        list.addChild(buildBaseStatRow(data, ModAttributes.DEF.value()));
        list.addChild(buildValueStatRow(data, ModAttributes.ELEMENTAL_MASTERY.value()));
        list.addChild(buildValueStatRow(data, ModAttributes.MAX_STAMINA.value()));

        list.addChild(buildStatsGroupTitle("advanced"));
        // 原神口径的进阶属性七项
        AttributeType[] advanced = {
                ModAttributes.CR.value(), ModAttributes.CDG.value(), ModAttributes.HB.value(),
                ModAttributes.IHB.value(), ModAttributes.ER.value(), ModAttributes.CDR.value(),
                ModAttributes.SS.value()};
        for (AttributeType type : advanced) {
            list.addChild(buildPercentStatRow(data, type));
        }

        list.addChild(buildStatsGroupTitle("elemental"));
        // 七元素 + 物理，各给「伤害加成 / 抗性」两条（和原神面板一致）
        AttributeType[] elements = {
                ModAttributes.PYRO_BONUS.value(), ModAttributes.PYRO_RES.value(),
                ModAttributes.HYDRO_BONUS.value(), ModAttributes.HYDRO_RES.value(),
                ModAttributes.DENDRO_BONUS.value(), ModAttributes.DENDRO_RES.value(),
                ModAttributes.ELECTRO_BONUS.value(), ModAttributes.ELECTRO_RES.value(),
                ModAttributes.ANEMO_BONUS.value(), ModAttributes.ANEMO_RES.value(),
                ModAttributes.CYRO_BONUS.value(), ModAttributes.CYRO_RES.value(),
                ModAttributes.GEO_BONUS.value(), ModAttributes.GEO_RES.value(),
                ModAttributes.PHYSICAL_BONUS.value(), ModAttributes.PHYSICAL_RES.value()};
        for (AttributeType type : elements) {
            list.addChild(buildPercentStatRow(data, type));
        }

        scroller.addScrollViewChild(list);
        return scroller;
    }

    private static Label buildStatsGroupTitle(String key) {
        Label title = new Label();
        title.addClass("cc-group-title");
        title.setText(Component.translatable("gui.minegenshin.character_config.stats." + key));
        title.layout(l -> l.height(12));
        return title;
    }

    /**
     * 白框（总值）/ 黄框（基础值）/ 绿框（额外值）的一行 —— 生命值上限、攻击力、防御力这
     * 三项有真实 base 的走这条。
     *
     * <p>基础值取 {@code getAttributeBaseValue}（成长表 + 突破给的那一份），额外值 = 总值 - 基础值。
     * 没有基础值的属性（元素精通、体力上限这类）只画总值那一格 —— 否则会显示成「23  0 + 23」。
     */
    private static UIElement buildBaseStatRow(PGCharacterData data, AttributeType type) {
        double total = data.getAttributeTotalValue(type);
        double base = data.getAttributeBaseValue(type);
        double extra = total - base;
        // 0.5 是显示阈值：算出来不到 0.5 就当没有（原神也是没有就不显示）
        boolean breakdown = base >= 0.5;
        return buildStatRow(type,
                formatValue(total),
                breakdown ? formatValue(base) : "",
                breakdown && extra >= 0.5 ? formatValue(extra) : "");
    }

    /** 纯总值的一行（元素精通 / 体力上限 —— 没有基础值与额外值）。 */
    private static UIElement buildValueStatRow(PGCharacterData data, AttributeType type) {
        return buildStatRow(type, formatValue(data.getAttributeTotalValue(type)), "", "");
    }

    /**
     * 百分比属性的一行（进阶 / 元素属性）：同样是三格，只是都换算成百分比。
     * 基础值有没有由数据说了算（暴击率 5% 这种就有），没有就只画总值。
     */
    private static UIElement buildPercentStatRow(PGCharacterData data, AttributeType type) {
        double total = data.getAttributeTotalValue(type);
        double base = data.getAttributeBaseValue(type);
        double extra = total - base;
        // 这些属性本身存的就是小数，阈值跟着改小：0.0005 = 0.05%
        boolean breakdown = Math.abs(base) >= 0.0005;
        return buildStatRow(type,
                formatPercent(total),
                breakdown ? formatPercent(base) : "",
                breakdown && Math.abs(extra) >= 0.0005 ? formatPercent(extra) : "");
    }

    /**
     * 属性行 = 名字 + 三格数值（玩家口径：「白框写总值，黄框基础值，绿框额外值」）。
     *
     * <p>加号<b>不是独立的一格</b>，而是绿字自己的前缀（用户口径：「+ 号应该在绿字前面」）：
     * 原来把加号当第三格画在黄绿之间，四格并排看着像「四列固定值」，加号还离绿字老远。
     * 并进绿字之后黄框与绿框之间只剩一道缝，一行看着才是「总值 基础值 +额外值」这个式子。
     *
     * <p>空着的那两格<b>元素照样在</b>（只是没文字）—— 列宽写死在 lss 里，各行才对得齐一条竖线；
     * 没有额外值时绿格只清文字、不撤元素，否则会把后面的列往左拉。
     */
    private static UIElement buildStatRow(AttributeType type, String total, String base, String extra) {
        UIElement row = new UIElement();
        row.addClass("cc-stat-row");

        Label name = new Label();
        name.addClass("cc-stat-name");
        name.setText(Component.translatable(type.translationKey()));
        name.layout(l -> l.height(11));

        row.addChildren(name, statCell("cc-stat-value", total), statCell("cc-stat-base", base),
                statCell("cc-stat-extra", extra.isEmpty() ? "" : "+" + extra));
        return row;
    }

    /** 属性行里的一格数值。宽度 / 描边 / 对齐全在 lss，这里只管文字与首帧高度。 */
    private static Label statCell(String styleClass, String text) {
        Label cell = new Label();
        cell.addClass(styleClass);
        cell.setText(Component.literal(text));
        cell.layout(l -> l.height(11));
        return cell;
    }

    /** 大数字加千位分隔（13,226），和原神面板一致。 */
    private static String formatValue(double value) {
        return String.format(Locale.ROOT, "%,d", Math.round(value));
    }

    /** 属性表里存的是小数（0.05 = 5%），显示时换算成百分比。 */
    private static String formatPercent(double ratio) {
        return String.format(Locale.ROOT, "%.1f%%", ratio * 100.0);
    }

    /**
     * 给滚动区加一条「按住内容上下拖」的滚动方式。
     *
     * <h2>为什么需要自己写</h2>
     * LDLib2 的 {@link ScrollerView} 只提供两个入口：滚轮（{@code ScrollerView#onScrollWheel}，
     * 挂在 {@code viewPort} 上）和拖那根几像素宽的滚动条。滚轮一旦指到 {@link TextField} 上，
     * 还会被输入框吃掉去改数值（{@code TextField#onMouseWheel} 在它自己的监听里
     * {@code stopPropagation}）—— 所以「按着列表往上滑」这个手势库里没有，得自己接。
     *
     * <h2>换算</h2>
     * Scroller 的值是归一化的：{@code value ∈ [0,1]} 对应 {@code 0..最大滚动像素}，
     * 也就是 {@code ScrollerView} 里那句 {@code layout.top(-value * max(0, 容器高 - 视口内容高))}。
     * 所以鼠标位移 dy 要除以「容器高 - 视口内容高」再叠加；减号是因为「往上拖 = 看下面的内容」。
     *
     * <p>手势落在输入框里时整段放过 —— 那里的拖动是选字，抢过来会把输入框弄坏。
     *
     * <h2>「拖出去再回来还当按住」是怎么修的</h2>
     * 原来只监听 {@code viewPort} 自己的 {@code MOUSE_UP}，而鼠标在视口外松手时这个事件
     * 到不了这里（它派发给「松手那一刻鼠标底下的元素」），{@code dragging} 就一直停在 true，
     * 再进来时会继续跟着鼠标滚。现在从三处兜：
     * <ul>
     *   <li>{@code MOUSE_LEAVE}：指针一离开视口就结束这次拖动（用户口径：「鼠标离开后就算松开了」）；</li>
     *   <li>每次 {@code MOUSE_MOVE} 先用 {@code isMouseDown(0)} 问一句左键还按着没有 ——
     *       这个值由 {@code ModularUI#mouseReleased} 在任何位置松手时清成 -1，是最权威的兜底；</li>
     *   <li>视口内正常松手时照旧清掉。</li>
     * </ul>
     */
    private static void installDragScroll(ScrollerView scroller) {
        boolean[] dragging = {false};
        float[] lastY = {0f};

        scroller.viewPort.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            dragging[0] = event.button == 0 && !insideField(event.target);
            lastY[0] = event.y;
        });
        scroller.viewPort.addEventListener(UIEvents.MOUSE_MOVE, event -> {
            if (!dragging[0]) {
                return;
            }
            // 左键已经在别处松开（比如拖出窗口再回来）：结束这次拖动，别接着滚
            if (!scroller.viewPort.isMouseDown(0)) {
                dragging[0] = false;
                return;
            }
            float dy = event.y - lastY[0];
            lastY[0] = event.y;
            float range = scroller.getContainerHeight() - scroller.viewPort.getContentHeight();
            if (range <= 0.0f || dy == 0.0f) {
                return;
            }
            scroller.verticalScroller.setNormalizedValue(
                    scroller.verticalScroller.getNormalizedValue() - dy / range);
        });
        scroller.viewPort.addEventListener(UIEvents.MOUSE_UP, event -> dragging[0] = false);
        // 指针离开视口（含离开整个界面）：这次拖动就算结束
        scroller.viewPort.addEventListener(UIEvents.MOUSE_LEAVE, event -> dragging[0] = false);
    }

    /** 手势是不是落在某个输入框里：事件从子元素一路冒上来，所以要认整条祖先链。 */
    private static boolean insideField(UIElement target) {
        for (UIElement element = target; element != null; element = element.getParent()) {
            if (element instanceof TextField) {
                return true;
            }
        }
        return false;
    }

    /**
     * 一行倍率：名字 + 数字框。
     *
     * <p>先 {@code setText(value, false)} 再挂 {@code setTextResponder} —— 反过来的话
     * 初始化那一次就会当成「玩家改了值」，页面一打开就把 24 项全写一遍配置。
     */
    private UIElement buildTalentRow(String key) {
        UIElement row = new UIElement();
        row.addClass("cc-talent-row");

        Label name = new Label();
        name.addClass("cc-talent-name");
        name.setText(Component.translatable(talentKey(key)));
        name.layout(l -> l.height(11));

        TextField field = new TextField();
        field.addClass("cc-talent-field");
        field.layout(l -> l.height(12));
        // 数字框里只有几位数字，不需要裁剪；关掉就少一个可能退化成 0 宽的 scissor。
        field.setOverflowVisible(true);
        field.setNumbersOnlyDouble(0.0, 100.0);
        Double value = ShenheTalentConfig.getByKey(key);
        field.setText(value == null ? "0" : trimNumber(value), false);
        field.setTextResponder(text -> {
            try {
                double parsed = Double.parseDouble(text.trim());
                ShenheTalentConfig.setByKey(key, parsed);
                // 角色倍率表是 COMMON 配置：两端各有一份，服务端那份才决定伤害，得发过去
                NetworkManager.setTalentMultiplierToServer(key, parsed);
            } catch (NumberFormatException ignored) {
                // 敲到一半的内容（空串、"."）不算数，别把它写进配置
            }
        });

        row.addChildren(name, field);
        return row;
    }

    /** 改完立刻刷新预览掩码，并把角色数据整包发回服务端（本地已即时生效）。 */
    private static void applyPreview(PGCharacter character, int[] previewMask) {
        previewMask[0] = character.getAppearance();
        PlayerCharactersAttachment attachment =
                Minecraft.getInstance().player == null
                        ? null
                        : Minecraft.getInstance().player.getData(
                                AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        if (attachment != null) {
            attachment.syncSingleCharacterToServer(character);
        }
    }

    private static String sockKey(SockType sock) {
        return "gui.minegenshin.character_config.sock."
                + (sock == null ? "bare" : sock.name().toLowerCase(Locale.ROOT));
    }

    private static String groupKey(String group) {
        return "gui.minegenshin.character_config.group." + group;
    }

    /** 倍率项的 i18n key 直接复用配置文件里的 key（{@code nab1} / {@code shehe-press-damage} …）。 */
    private static String talentKey(String key) {
        return "gui.minegenshin.character_config.talent." + key;
    }

    /** 配置里存的是 double，显示时不想看到 {@code 0.43300000000000005} 这种尾巴。 */
    private static String trimNumber(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(Math.round(value * 1.0e6) / 1.0e6);
    }
}
