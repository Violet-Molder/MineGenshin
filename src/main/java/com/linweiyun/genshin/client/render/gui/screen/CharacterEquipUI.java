package com.linweiyun.genshin.client.render.gui.screen;

import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.linweiyun.genshin.client.render.character.CharacterRenderDispatcher;
import com.linweiyun.genshin.client.render.character.GenshinPreviewPlayer;
import com.linweiyun.genshin.client.render.character.appearance.CharacterAppearanceBones;
import com.linweiyun.genshin.client.render.character.appearance.CharacterPropBones;
import com.linweiyun.genshin.client.render.gui.component.CustomToggle;
import com.linweiyun.genshin.config.character.CharacterSystemConfig;
import com.linweiyun.genshin.config.character.CharacterXpConfig;
import com.linweiyun.genshin.content.attribute.AttributeType;
import com.linweiyun.genshin.content.items.artifact.ArtifactItem;
import com.linweiyun.genshin.content.items.artifact.inventory.ArtifactInventory;
import com.linweiyun.genshin.content.items.artifact.type.ArtifactType;
import com.linweiyun.genshin.content.items.component.ArtifactStatsComponent;
import com.linweiyun.genshin.content.items.component.WeaponStatsComponent;
import com.linweiyun.genshin.content.items.development.AdviceBookItem;
import com.linweiyun.genshin.content.items.weapon.WeaponItem;
import com.linweiyun.genshin.content.items.weapon.bow.Bow;
import com.linweiyun.genshin.content.items.weapon.catalyst.Catalyst;
import com.linweiyun.genshin.content.items.weapon.claymore.Claymore;
import com.linweiyun.genshin.content.items.weapon.polearm.Polearm;
import com.linweiyun.genshin.content.stat.TeyvatItemStat;
import com.linweiyun.genshin.core.asset.ItemIcons;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.Backpack;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.PGCharacterData;
import com.linweiyun.genshin.core.character.talent.TalentUpgradeCost;
import com.linweiyun.genshin.core.element.GenshinElement;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderRepository;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.core.system.registry.register.ModDataComponents;
import com.lowdragmc.lowdraglib2.client.scene.SceneRenderContext;
import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.Transform2D;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 角色装备页（按键 U）—— 取代旧的 {@link ScreenArtifactEquip}。
 *
 * <h2>页面上有什么</h2>
 * <ul>
 *   <li><b>顶栏</b>：一排角色头像，<b>从左排起</b>，整条装在一个「自带横向滚动条」的
 *       {@link ScrollerView} 里 —— 列的是<b>玩家拥有的全部角色</b>，不止队伍里那四个
 *       （用户口径：角色列表从左开始、是所有角色、加带自带横向滚动条的 view 容器）。
 *       点头像 = 切到看那个人：在队伍里的走 {@code setCurrentCharacterToServer}
 *       （和数字键切人同一条路，服务端所有换装接口都是「对当前在场角色生效」），
 *       不在队伍里的<b>只能看不能改</b>（见 {@link #isViewOnly}）。</li>
 *   <li><b>左栏</b>：六个竖排页签 —— 属性 / 武器 / 圣遗物 / 命之座 / 天赋 / 资料。</li>
 *   <li><b>中栏</b>：{@link Scene} 3D 预览，鼠标左键拖拽转视角、滚轮缩放。
 *       有专属模型的角色走 {@code CharacterRenderDispatcher}（和第三人称同一套渲染器）；
 *       <b>没有专属模型就回退原版玩家模型</b>（玩家自己的皮肤，见 {@link #submitVanillaPlayer}）。
 *       圣遗物页还在这一层上叠了五枚「环绕角色的圣遗物」（{@link #refreshOrbs}），
 *       可以按住拖动、点一下进更换子页面。</li>
 *   <li><b>右栏</b>：当前页签的详情/列表/按钮。</li>
 *   <li><b>子页面</b>：更换圣遗物 / 升级。打开时整层内容换成那一页（见 {@link #openSubPage}），
 *       左上角一个「返回」回主视图。</li>
 * </ul>
 *
 * <h2>为什么「升级」是二级页面</h2>
 * 用户口径：点升级后不要直接升，要另开一页读玩家背包 + 模组背包里的所有经验书，
 * 玩家自己挑一种、自己定数量（+ / − 或直接填），上面显示经验值与升级后的属性，
 * 到了当前突破上限就显示 MAX 并禁止再加。所以本页只发
 * {@link NetworkManager#sendEquipLevelUpBatchToServer}，由服务端按坐标复核那一格
 * 是不是经验书、把本数夹到实际数量以内，再落经验。
 *
 * <h2>拖拽换装</h2>
 * 右栏列表里的格子可以按住往上面五个槽位拖：起手在格子上调
 * {@code UIElement#startDrag}，槽位吃 {@code DRAG_PERFORM}（LDLib2 自带的拖放生命周期），
 * 松手在哪个槽位就把那件装备穿上。列表的两个来源（模组背包分类 / 玩家主物品栏）走两条
 * 不同的服务端包，见 {@link Owned}。
 */
public final class CharacterEquipUI {

    private static final Identifier STYLESHEET =
            Identifier.parse("minegenshin:lss/character_equip.lss");

    /** 升级目标的两个哨兵值（真实定义在服务端那条 RPC 上，这里只是别名，避免两边写岔）。 */
    private static final int TARGET_CHARACTER = NetworkManager.EQUIP_LEVEL_TARGET_CHARACTER;
    private static final int TARGET_WEAPON = NetworkManager.EQUIP_LEVEL_TARGET_WEAPON;

    /** 全亮：预览里不跟着世界光照变暗（和第三人称那条路同一个值）。 */
    private static final int FULL_BRIGHT = 15728880;

    /**
     * 预览机位。{@code centerY} 越大模型越靠下、{@code zoom} 越大模型越小
     * （推导见 {@code ShenheConfigUI} 的同名字段）。
     *
     * <p>这一页的预览框比配置页窄一些（右边还站着一整栏详情），所以相机稍微拉远一档。
     */
    private static final float PREVIEW_CENTER_Y = 1.20f;
    private static final float PREVIEW_ZOOM = 3.1f;
    private static final float PREVIEW_YAW = 115f;
    private static final float PREVIEW_PITCH = 12f;

    /** 常态姿势：模型里没有 {@code extra48}（闲置-喝茶）的角色会自己退回 idle。 */
    private static final String PREVIEW_ANIMATION_IDLE = "extra48";
    /** 圣遗物页专用姿势：闭眼 + 双手合掌放在胸前（模型里没有就退回 idle）。 */
    private static final String PREVIEW_ANIMATION_EQUIP = "extra_equip";

    /** 一页 = 左栏一枚页签。{@link #key()} 拼 i18n。 */
    private enum Page {
        STATS, WEAPON, ARTIFACT, CONSTELLATION, TALENT, PROFILE;

        String key() {
            return "gui.minegenshin.character_equip.page." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** 盖在主视图上的二级页面。{@link #NONE} = 没开。 */
    private enum SubPage {
        NONE, ARTIFACT_CHANGE, WEAPON_CHANGE, LEVEL_UP
    }

    /** 五个圣遗物槽位，顺序 = {@link ArtifactInventory} 的 SLOT_* 常量。 */
    private static final ArtifactType[] ARTIFACT_TYPES = {
            ArtifactType.FLOWER, ArtifactType.PLUME, ArtifactType.SANDS,
            ArtifactType.GOBLET, ArtifactType.CIRCLET};

    /**
     * 五枚圣遗物环绕角色的默认落点（占环绕层宽高的比例，0~1），顺序同
     * {@link #ARTIFACT_TYPES}（花 / 羽 / 沙 / 杯 / 冠）。
     *
     * <p>位置按原神那个页面的实测比例摆：<b>围着角色绕一圈</b> ——
     * 右侧三枚（花偏上、羽居中、沙偏下）、左侧两枚（冠偏上、杯偏下），
     * 每枚的落点是元素<b>中心</b>（布局里补一个 -50%/-50% 的 translate）。
     * 玩家可以按住任意一枚拖到别处，位置存在 {@link State#orbX} / {@link State#orbY}。
     */
    private static final float[] ORB_DEFAULT_X = {0.80f, 0.84f, 0.72f, 0.18f, 0.16f};
    private static final float[] ORB_DEFAULT_Y = {0.38f, 0.56f, 0.74f, 0.66f, 0.40f};

    /** 玩家物品栏里能拿来穿的格子：0~35（快捷栏 + 主背包），跳过盔甲与副手。 */
    private static final int INVENTORY_MAIN_SLOTS = 36;

    private CharacterEquipUI() {
    }

    /**
     * 当前打开着的这一份页面状态。
     *
     * <p>页面的控件树不是每帧重建的，只在点击 / 换页 / 换人时重建一次。服务端把
     * 「刚激活的圣遗物」推回来时（{@code ClientHandler#applyActivatedArtifactClientHandler}），
     * 本地这份拷贝得显式重画一遍 —— 否则玩家点了「激活」，面板会一直停在「未激活」，
     * 直到他再点一下别的才更新。宿主 Screen 关闭时清空（见 {@code ScreenCharacterEquip#removed()}）。
     */
    @Nullable
    private static State OPEN_STATE;

    /** 页面关闭：松开这份引用，别让一棵已经没人画的控件树被网络回调继续写。 */
    public static void clearOpenState() {
        OPEN_STATE = null;
    }

    /** 背包数据被外部改动（服务端推回了激活后的圣遗物）后，让打开着的页面立刻重画。 */
    public static void refreshIfOpen() {
        State st = OPEN_STATE;
        if (st == null) {
            return;
        }
        pullFresh(st);
        rebuild(st);
    }

    // ====================================================================
    //  页面装配
    // ====================================================================

    public static ModularUI createModularUI(Player player) {
        var stylesheet = StylesheetManager.INSTANCE.getStylesheetSafe(STYLESHEET);

        UIElement root = new UIElement().setId("ce-root");
        // 根元素的 100% x 100% 必须在 Java 里用 INLINE 再写一遍：ModularUI.init 先读根元素的
        // 尺寸候选、再算样式，lss 那份那一刻还没进样式袋 —— 只写在 lss 里 = 第一次打开必错版
        // （近似正方形、内容溢出），拉一次窗口才对。推导见 character_config.lss 文件头。
        root.layout(l -> {
            l.widthPercent(100);
            l.heightPercent(100);
        });

        PlayerCharactersAttachment attachment =
                player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        if (attachment == null || attachment.getCurrentCharacter() == null) {
            root.addChild(new Label().setText(
                    Component.translatable("gui.minegenshin.artifact_equip.no_character")));
            return ModularUI.of(UI.of(root, stylesheet), player);
        }

        State st = new State(player, attachment,
                player.getData(AttachmentRegistration.BACKPACK_ATTACHMENT));
        st.partyIndex = Math.max(0, attachment.getCurrentCharacterIndex());
        st.character = attachment.getCurrentCharacter();

        // 布局整个照原神「角色详情页」那一套：
        //
        //   顶栏  左「元素图标 + 元素名 / 角色名」  中「一排圆形头像」        右「关闭 ✕」
        //   左栏  属性 / 武器 / 圣遗物 / 命之座 / 天赋 / 资料（竖排，当前页高亮）
        //   中间  整屏 3D 预览（圣遗物页再叠一圈环绕的角色装备）
        //   右栏  这一页的详情卡（角色面板 / 武器卡 / 圣遗物详情 / 命座节点 / 天赋列表 / 资料）
        //   底栏  左「提升指南」提示  右「本页的主要操作键」（升级 / 上限突破 / 替换 / 强化 …）
        //
        // 所有区块都是绝对定位的兄弟节点（见 character_equip.lss 的百分比），
        // 这样窗口一改大小，整套跟着等比走 —— 不会出现「窗口小了内容残缺」。
        UIElement window = new UIElement().setId("ce-window");
        UIElement body = new UIElement().setId("ce-body");

        // ---------------- 顶栏 ----------------
        UIElement top = new UIElement().setId("ce-top");
        UIElement topLeft = new UIElement().setId("ce-top-left");
        st.elemIcon = new UIElement().setId("ce-top-elem");
        st.topName = new Label();
        st.topName.setId("ce-top-name");
        topLeft.addChildren(st.elemIcon, st.topName);

        st.stripScroll = new ScrollerView();
        st.stripScroll.setId("ce-strip-scroll");
        // 横向滚动条自动显隐（AUTO）：角色少的时候不占位置，多了就出现。
        st.stripScroll.scrollerStyle(s -> s
                .mode(ScrollerMode.HORIZONTAL)
                .horizontalScrollDisplay(ScrollDisplay.AUTO)
                .verticalScrollDisplay(ScrollDisplay.NEVER));
        // 视口自带 5px 内边距 + 一圈内凹底：在顶栏这一行上是多余的，抹平（见 lss 同名规则）。
        st.stripScroll.viewPort.style(s -> s.background(IGuiTexture.EMPTY));
        st.stripScroll.viewPort.layout(l -> l.paddingAll(0).minWidth(16).minHeight(16));
        st.strip = new UIElement().setId("ce-strip");
        st.stripScroll.addScrollViewChild(st.strip);

        Button close = new Button();
        close.setId("ce-close");
        close.setText(Component.literal("✕"));
        close.setOnClick(event -> Minecraft.getInstance().setScreen(null));
        top.addChildren(topLeft, st.stripScroll, close);

        // ---------------- 左栏 / 右栏 / 底栏 ----------------
        st.menu = new UIElement().setId("ce-menu");
        st.panel = new UIElement().setId("ce-panel");
        // 命之座 / 天赋这两页的详情卡：点右侧节点才在左边缘弹出来（原神同一处）。
        st.detailCard = new UIElement().setId("ce-detail-card");
        st.detailCard.layout(l -> l.display(TaffyDisplay.NONE));

        st.bottom = new UIElement().setId("ce-bottom");
        st.bottomLeft = new UIElement().setId("ce-bottom-left");
        st.bottomRight = new UIElement().setId("ce-bottom-right");
        st.bottom.addChildren(st.bottomLeft, st.bottomRight);

        st.viewOnlyNote = new Label();
        st.viewOnlyNote.addClass("ce-viewonly");
        st.viewOnlyNote.setText(Component.translatable("gui.minegenshin.character_equip.view_only"));
        st.viewOnlyNote.layout(l -> l.display(TaffyDisplay.NONE).height(13));

        st.sub = new UIElement().setId("ce-sub");
        st.sub.layout(l -> l.display(TaffyDisplay.NONE));

        // 主体 = 顶栏 + 左栏 + 右栏 + 底栏，子页面打开时整层收起（模型那一层不收起 ——
        // 原神换武器 / 换圣遗物的时候角色照样站在中间）。
        st.main = new UIElement().setId("ce-main");
        st.main.addChildren(st.menu, st.panel, st.detailCard, top, st.bottom);

        // 屏幕左右边缘那对翻角色的箭头（原神同一位置）
        Button prev = new Button();
        prev.setId("ce-prev");
        prev.setText(Component.literal("‹"));
        prev.setOnClick(event -> stepCharacter(st, -1));
        Button next = new Button();
        next.setId("ce-next");
        next.setText(Component.literal("›"));
        next.setOnClick(event -> stepCharacter(st, 1));
        st.main.addChildren(prev, next);

        // 中间那层 3D 预览铺在整窗上：后面加的元素画在上层、命中测试也优先，
        // 所以压在它上面的左栏 / 右栏 / 顶栏点得动，空白处左键拖拽仍然是转视角。
        st.stage = buildStage(st);

        body.addChildren(st.stage, st.main, st.viewOnlyNote, st.sub);
        window.addChild(body);
        root.addChild(window);

        fillMenu(st);
        rebuild(st);

        // LDLib2 每客户端 tick 会给根元素派一次 tick：在这里比一次「页面上那些数据的指纹」，
        // 变了才重建（升级 / 换装的结果是服务端算完再推回来的，本地没法同步知道）。
        //
        // 三道保险，缺一条就会退化成「必须切出去再切回来才刷新」：
        // 1) pullFresh 每 tick 把附件 / 背包重新从玩家身上取一遍 —— 数据附件被整份换人也跟着走；
        // 2) st.dirty 是「本地点了写操作，等回包」的立即重画位（点完那一刻先把按钮态刷对）；
        // 3) 指纹覆盖所有显示中的数值（等级 / 经验 / 突破 / 命座 / 天赋 / 武器精炼 / 圣遗物经验
        //    / 装备栏 / 背包 / 玩家物品栏），任何一项变了就整页重画。
        String[] stamp = {dataStamp(st)};
        root.addEventListener(UIEvents.TICK, event -> {
            if (OPEN_STATE != st) {
                return;
            }
            pullFresh(st);
            if (st.dirty) {
                st.dirty = false;
                rebuild(st);
                stamp[0] = dataStamp(st);
                return;
            }
            String now = dataStamp(st);
            if (!now.equals(stamp[0])) {
                stamp[0] = now;
                rebuild(st);
            }
        });
        OPEN_STATE = st;

        return ModularUI.of(UI.of(root, stylesheet), player);
    }

    // ====================================================================
    //  顶栏：全角色头像条（横向滚动 + 切换 / 观看）
    // ====================================================================

    private static void fillStrip(State st) {
        st.strip.clearAllChildren();
        st.avatarButtons.clear();

        PGCharacter viewed = st.character;
        for (PGCharacter member : st.attachment.getOwnedCharacters()) {
            if (member == null) {
                continue;
            }
            boolean viewedNow = viewed != null
                    && member.getCharacterUUID() == viewed.getCharacterUUID();
            UIElement avatar = new UIElement().addClass("ce-avatar");
            avatar.style(s -> s.background(SpriteTexture.of(avatarTexture(member))));
            if (viewedNow) {
                avatar.addClass("ce-avatar-viewed");
            } else {
                int uuid = member.getCharacterUUID();
                avatar.addEventListener(UIEvents.CLICK, event -> viewCharacter(st, uuid));
            }
            st.avatarButtons.add(avatar);
            st.strip.addChild(avatar);
        }
    }

    /**
     * 把「正在看的角色」换成 {@code uuid} 那一位。
     *
     * <p>在队伍里 → 顺手切人（{@code setCurrentCharacterToServer}，本地立刻生效、
     * 服务端换装接口从此作用在这位身上）；不在队伍里 → 只是「看」，写操作全套禁用
     * （服务端那几条换装 / 升级包都只认 {@code getCurrentCharacter()}，不改在场角色就点它们，
     * 会改到别人身上 —— 所以宁可禁用）。
     */
    private static void viewCharacter(State st, int uuid) {
        PGCharacter target = st.attachment.getCharacterByUUID(uuid);
        if (target == null) {
            return;
        }
        if (st.character != null && target.getCharacterUUID() == st.character.getCharacterUUID()) {
            return;
        }
        int partyIdx = partyIndexOf(st.attachment, uuid);
        if (partyIdx >= 0 && partyIdx != st.attachment.getCurrentCharacterIndex()) {
            st.attachment.setCurrentCharacterToServer(partyIdx);
        }
        st.partyIndex = partyIdx;
        st.character = target;
        clearSelection(st);
        st.artifactSlot = ArtifactInventory.SLOT_FLOWER;
        st.subPage = SubPage.NONE;
        st.orbX = ORB_DEFAULT_X.clone();
        st.orbY = ORB_DEFAULT_Y.clone();
        fillStrip(st);
        rebuild(st);
    }

    /** 这个 uuid 在队伍第几位（0~3）；不在队伍里返回 -1。 */
    private static int partyIndexOf(PlayerCharactersAttachment attachment, int uuid) {
        for (int i = 0; i < 4; i++) {
            PGCharacter member = attachment.getPartyCharacter(i);
            if (member != null && member.getCharacterUUID() == uuid) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 正在看的角色是不是<b>当前在场</b>的那位。不是 = 只能看，不能改。
     *
     * <p>服务端每一条换装 / 升级 / 命座接口都是「对 {@code getCurrentCharacter()} 生效」，
     * 页面里看着另一个人的数据却发这些包，改的是场上那位 —— 所以这种状态下所有写操作
     * 一律禁用并在顶栏下面挂一条提示。
     */
    private static boolean isViewOnly(State st) {
        PGCharacter current = st.attachment.getCurrentCharacter();
        if (st.character == null || current == null) {
            return true;
        }
        return st.character.getCharacterUUID() != current.getCharacterUUID();
    }

    private static String avatarTexture(PGCharacter character) {
        return "minegenshin:character/" + character.getTextureId() + "/textures/avatar_hud.png";
    }

    // ====================================================================
    //  左栏：页签
    // ====================================================================

    private static void fillMenu(State st) {
        st.menu.clearAllChildren();
        for (Page page : Page.values()) {
            CustomToggle tab = new CustomToggle();
            tab.addClass("ce-nav");
            // 原神左栏那一条是「小菱形 + 名字」，当前页更亮更大
            tab.setButtonText(Component.literal("◆ ").append(Component.translatable(page.key())));
            tab.layout(l -> l.height(15));
            tab.setOn(page == st.page, false);
            tab.setOnToggleChanged(on -> {
                if (st.page == page) {
                    // 没挂 ToggleGroup 的 Toggle 默认「允许空选」，点自己这一枚会翻成 off；
                    // 页面不该跟着变，把高亮掰回去就行（notifyChange=false，不会递归）。
                    if (!on) {
                        tab.setOn(true, false);
                    }
                    return;
                }
                if (!on) {
                    return;
                }
                st.page = page;
                st.subPage = SubPage.NONE;
                rebuild(st);
            });
            st.tabs.put(page, tab);
            st.menu.addChild(tab);
        }
    }

    /**
     * 翻到上一位 / 下一位已拥有角色 —— 原神屏幕左右边缘那对箭头。
     *
     * <p>顺序就是 {@code getOwnedCharacters()} 的顺序，到头绕回另一端（和原神一样循环）。
     */
    private static void stepCharacter(State st, int delta) {
        List<PGCharacter> owned = st.attachment.getOwnedCharacters();
        if (owned == null || owned.size() < 2 || st.character == null) {
            return;
        }
        int current = -1;
        for (int i = 0; i < owned.size(); i++) {
            PGCharacter member = owned.get(i);
            if (member != null && member.getCharacterUUID() == st.character.getCharacterUUID()) {
                current = i;
                break;
            }
        }
        if (current < 0) {
            return;
        }
        int next = Math.floorMod(current + delta, owned.size());
        PGCharacter target = owned.get(next);
        if (target != null) {
            viewCharacter(st, target.getCharacterUUID());
        }
    }

    // ====================================================================
    //  中栏：3D 预览 + 环绕的圣遗物
    // ====================================================================

    private static UIElement buildStage(State st) {
        UIElement stage = new UIElement().setId("ce-stage");

        Scene scene = new Scene();
        scene.setId("ce-scene");
        // 关掉裁剪：Scene 的画布本来就按元素矩形做离屏贴图，不会有可见溢出；
        // 而带 overflow 的元素一旦被布局压成 0 尺寸，LDLib2 会量化出 0 像素的 scissor，
        // MC 26.2 的 RenderPass 对 0 尺寸是直接抛异常（详见 ShenheConfigUI 同类注释）。
        scene.setOverflowVisible(true);
        stage.addChild(scene);

        // 圣遗物环绕层：盖在 Scene 上，自己不参与命中（allowHitTest=false），
        // 五枚 orb 才是可点的子元素 —— 这样点 orb 不会顺带把视角也转了。
        st.orbLayer = new UIElement().setId("ce-orb-layer");
        st.orbLayer.setAllowHitTest(false);
        st.orbLayer.layout(l -> l
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0).top(0)
                .widthPercent(100).heightPercent(100));
        stage.addChild(st.orbLayer);

        Label hint = new Label();
        hint.setId("ce-stage-hint");
        hint.setText(Component.translatable("gui.minegenshin.character_equip.hint"));
        hint.layout(l -> l.height(9));
        stage.addChild(hint);

        // 拖动 orb：起手在 orb 上（MOUSE_DOWN），后续的 MOUSE_MOVE / MOUSE_UP 都挂在 stage 上
        // 收 —— 事件会从被 hover 的元素一路冒泡上来，所以指针短暂离开那一枚也不会丢事件。
        stage.addEventListener(UIEvents.MOUSE_MOVE, event -> {
            if (st.orbDragSlot < 0) {
                return;
            }
            if (!stage.isMouseDown(0)) {
                st.orbDragSlot = -1;
                return;
            }
            float w = st.orbLayer.getSizeWidth();
            float h = st.orbLayer.getSizeHeight();
            if (w <= 0f || h <= 0f) {
                return;
            }
            float dx = event.x - st.orbDragStartX;
            float dy = event.y - st.orbDragStartY;
            if (Math.abs(dx) > 3f || Math.abs(dy) > 3f) {
                st.orbDragMoved = true;
            }
            int slot = st.orbDragSlot;
            st.orbX[slot] = clamp(st.orbDragStartNx + dx / w, 0.05f, 0.95f);
            st.orbY[slot] = clamp(st.orbDragStartNy + dy / h, 0.06f, 0.94f);
            UIElement orb = st.orbBySlot[slot];
            if (orb != null) {
                float nx = st.orbX[slot];
                float ny = st.orbY[slot];
                orb.layout(l -> l.leftPercent(nx * 100f).topPercent(ny * 100f));
            }
        });
        stage.addEventListener(UIEvents.MOUSE_UP, event -> {
            if (event.button == 0) {
                st.orbDragSlot = -1;
            }
        });

        var level = Minecraft.getInstance().level;
        if (level == null) {
            return stage;
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
            return stage;
        }

        // 每帧现读 st.character —— 换人 / 换装备之后不用重建 Scene，下一帧就是新模型。
        renderer.setAfterBuiltinSubmit(ctx -> renderPreview(st, ctx));
        return stage;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 重建「环绕角色的五枚圣遗物」。只在圣遗物页出现，其它页整层 display:none。
     *
     * <p>位置是归一化坐标（{@link State#orbX} / {@link State#orbY}），布局时换算成
     * 相对环绕层的百分比 + 一个 -50% 的 translate（把元素<b>中心</b>对到那个点）。
     */
    private static void refreshOrbs(State st) {
        if (st.orbLayer == null) {
            return;
        }
        st.orbLayer.clearAllChildren();
        for (int i = 0; i < st.orbBySlot.length; i++) {
            st.orbBySlot[i] = null;
        }
        boolean show = st.page == Page.ARTIFACT && st.subPage == SubPage.NONE;
        st.orbLayer.layout(l -> l.display(show ? TaffyDisplay.FLEX : TaffyDisplay.NONE));
        if (!show) {
            return;
        }

        PGCharacterData data = st.character.getData();
        ArtifactInventory inv = data == null ? null : data.getArtifactInventory();
        for (int slot = 0; slot < ARTIFACT_TYPES.length; slot++) {
            ItemStack stack = inv == null ? ItemStack.EMPTY : inv.getItem(slot);
            UIElement orb = new UIElement().addClass("ce-orb");
            if (stack.isEmpty()) {
                orb.addClass("ce-orb-empty");
                // 空槽位按原神那样画一枚「这个部位长什么样」的淡色剪影
                orb.style(s -> s.background(SpriteTexture.of(partIcon(slot))));
                Label mark = new Label();
                mark.addClass("ce-orb-mark");
                mark.setText(Component.literal("!"));
                mark.layout(l -> l.positionType(TaffyPosition.ABSOLUTE).right(2).top(-3));
                orb.addChild(mark);
            } else {
                orb.style(s -> s.background(SpriteTexture.of(ItemIcons.pathOf(stack))));
            }
            if (slot == st.artifactSlot) {
                orb.addClass("ce-orb-on");
            }
            float nx = st.orbX[slot];
            float ny = st.orbY[slot];
            orb.layout(l -> l.positionType(TaffyPosition.ABSOLUTE)
                    .leftPercent(nx * 100f).topPercent(ny * 100f));
            orb.style(s -> s.transform2D(new Transform2D().translatePercent(-50f, -50f)));

            int sl = slot;
            orb.addEventListener(UIEvents.MOUSE_DOWN, event -> {
                if (event.button != 0) {
                    return;
                }
                st.orbDragSlot = sl;
                st.orbDragStartX = event.x;
                st.orbDragStartY = event.y;
                st.orbDragStartNx = st.orbX[sl];
                st.orbDragStartNy = st.orbY[sl];
                st.orbDragMoved = false;
            });
            orb.addEventListener(UIEvents.MOUSE_UP, event -> {
                if (event.button != 0 || st.orbDragSlot != sl) {
                    return;
                }
                boolean moved = st.orbDragMoved;
                st.orbDragSlot = -1;
                if (!moved) {
                    // 点一下（没拖动）= 选中这一部位并进更换页
                    st.artifactSlot = sl;
                    openSubPage(st, SubPage.ARTIFACT_CHANGE);
                }
            });
            st.orbBySlot[slot] = orb;
            st.orbLayer.addChild(orb);
        }
    }

    private static String slotKey(int slot) {
        return switch (slot) {
            case ArtifactInventory.SLOT_FLOWER -> "gui.minegenshin.character_equip.slot.flower";
            case ArtifactInventory.SLOT_PLUME -> "gui.minegenshin.character_equip.slot.plume";
            case ArtifactInventory.SLOT_SANDS -> "gui.minegenshin.character_equip.slot.sands";
            case ArtifactInventory.SLOT_GOBLET -> "gui.minegenshin.character_equip.slot.goblet";
            case ArtifactInventory.SLOT_CIRCLET -> "gui.minegenshin.character_equip.slot.circlet";
            default -> "gui.minegenshin.artifact_equip.weapon";
        };
    }

    /**
     * 空槽位里那枚剪影贴图（花 / 羽 / 沙 / 杯 / 冠）。
     *
     * <p>原神空槽画的不是文字而是部位本身的图形，这五张小图就是照着那个形状画的
     * （纯白剪影，底色透明，见 {@code assets/minegenshin/gui/character_equip/}）。
     */
    private static String partIcon(int slot) {
        return switch (slot) {
            case ArtifactInventory.SLOT_FLOWER -> "minegenshin:gui/character_equip/part_flower.png";
            case ArtifactInventory.SLOT_PLUME -> "minegenshin:gui/character_equip/part_plume.png";
            case ArtifactInventory.SLOT_SANDS -> "minegenshin:gui/character_equip/part_sands.png";
            case ArtifactInventory.SLOT_GOBLET -> "minegenshin:gui/character_equip/part_goblet.png";
            case ArtifactInventory.SLOT_CIRCLET -> "minegenshin:gui/character_equip/part_circlet.png";
            default -> "minegenshin:gui/character_equip/part_flower.png";
        };
    }

    /**
     * 画出当前查看的角色。
     *
     * <p>两条路：
     * <ol>
     *   <li><b>有专属模型</b>（{@code CharacterSystemConfig.customModel}）→ 走
     *       {@link CharacterRenderDispatcher#targetFor}，和第三人称完全同一套渲染器 + 外观掩码；</li>
     *   <li><b>没有专属模型</b> → 回退原版玩家（{@link #submitVanillaPlayer}）。
     *       皮肤就是玩家自己那份，所以「第三人称看到什么样，这里就什么样」。</li>
     * </ol>
     */
    private static void renderPreview(State st, SceneRenderContext ctx) {
        PGCharacter viewed = st.character;
        if (viewed == null || st.previewAnimatable == null) {
            return;
        }
        String charId = viewed.getTextureId();
        CharacterRenderData data = CharacterRenderRepository.get(charId);

        PoseStack poseStack = ctx.poseStack();
        poseStack.pushPose();
        try {
            // 模型局部前方是 -Z（和原版实体一致），转 180 让正面朝向相机默认那一侧。
            poseStack.mulPose(Axis.YP.rotationDegrees(180f));
            if (data != null && data.isValid() && CharacterSystemConfig.customModel(charId)) {
                CharacterRenderDispatcher.RenderTarget target =
                        CharacterRenderDispatcher.targetFor(st.player, charId, data);
                if (target == null) {
                    return;
                }
                st.previewAnimatable.setPlayerEntity(st.player);
                // 外观掩码从「正在看的这个角色」现读，不从当前玩家读 —— 换人那一刻预览要跟着变。
                // 木偶套件（飞行坐骑 / 法吉偶 / 屏幕 / 齿轮）这一趟一律不画：第三人称站着的时候
                // 它们本来就是藏着的（见 CharacterPropBones#updaterFor 的名单），
                // 这一页要的就是「这个角色常态站在那」的样子 —— 不藏的话坐骑会挂在脚底、
                // 屏幕贴在胸口，和用户口径「第三人称是什么样就什么样」不符。
                RenderPassInfo.BoneUpdater<GeoRenderState> bones = CharacterRenderDispatcher.combine(
                        CharacterAppearanceBones.forMask(viewed.getAppearance()),
                        CharacterPropBones.hideAllUpdater());
                target.renderer().performRenderPass(st.previewAnimatable, st.player, poseStack,
                        ctx.submitStorage(), ctx.cameraState(), FULL_BRIGHT, ctx.partialTicks(), bones);
            } else {
                submitVanillaPlayer(ctx, st.player, poseStack);
            }
        } finally {
            // 钩子在别人的渲染流程里跑，异常时也必须把栈还回去
            poseStack.popPose();
        }
    }

    /** 这一页当前该播哪条动画：圣遗物页「闭眼合掌」，其它页常态。 */
    private static String previewAnimationFor(State st) {
        if (st.page == Page.ARTIFACT) {
            return PREVIEW_ANIMATION_EQUIP;
        }
        return PREVIEW_ANIMATION_IDLE;
    }

    /** 动画名变了就换一个 {@link GenshinPreviewPlayer}（它把动画名存在构造参数里）。 */
    private static void ensurePreviewAnimatable(State st) {
        String want = previewAnimationFor(st);
        if (st.previewAnimatable != null && want.equals(st.previewAnimationName)) {
            return;
        }
        st.previewAnimatable = new GenshinPreviewPlayer(want);
        st.previewAnimationName = want;
    }

    /**
     * 把玩家本体交给原版 {@code EntityRenderDispatcher} 画一遍（有皮肤带皮肤）。
     *
     * <h2>为什么需要 force-vanilla 旁路</h2>
     * {@code AvatarRendererMixin} 在 {@code AvatarRenderer.submit} 的 HEAD 处拦下来，
     * 只要「这个玩家当前是某个有专属模型的原神角色」就把原版渲染整个取消、换成 GeckoLib 模型。
     * 预览要的是<b>原版那个玩家</b>，所以提交前先压一个旁路开关，
     * 让 {@code CharacterRenderDispatcher} 这一趟直接放手（见 {@link CharacterRenderDispatcher#pushForceVanilla()}）。
     *
     * <h2>为什么要改朝向</h2>
     * 抽取出来的 render state 带着玩家在世界里的真实 yaw，照原样画就会出现「背对镜头」。
     * 页面里固定成 0（模型局部前方），配合上面那记 180 度旋转，正好正面朝相机 ——
     * 和自定义模型那条路的朝向约定一致。
     */
    private static void submitVanillaPlayer(SceneRenderContext ctx, Player player, PoseStack poseStack) {
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        EntityRenderState state = dispatcher.extractEntity(player, ctx.partialTicks());
        if (state instanceof LivingEntityRenderState living) {
            living.yRot = 0f;
            living.bodyRot = 0f;
            living.xRot = 0f;
            // 定格的姿势：行走摆动会把预览里的腿摆成走路中间帧
            living.walkAnimationPos = 0f;
            living.walkAnimationSpeed = 0f;
            living.deathTime = 0f;
        }
        CharacterRenderDispatcher.pushForceVanilla();
        try {
            dispatcher.submit(state, ctx.cameraState(), 0.0, 0.0, 0.0, poseStack, ctx.submitStorage());
        } finally {
            CharacterRenderDispatcher.popForceVanilla();
        }
    }

    // ====================================================================
    //  重建
    // ====================================================================

    /**
     * 拿「正在看的这个角色」的<b>最新</b>那一份对象。
     *
     * <p>服务端每次推 {@code playerCharactersRPCPacket} 都是把整份
     * {@code PlayerCharactersAttachment} 反序列化一遍，里面的 {@link PGCharacter}
     * 对象会<b>换人</b> —— 页面里存的那份引用于是指向一棵没人再更新的旧树。
     * 所有读数据的地方都过这一道，拿到的就一定是当前这份附件里的对象。
     */
    @Nullable
    private static PGCharacter viewed(State st) {
        if (st.character == null) {
            return null;
        }
        PGCharacter fresh = st.attachment.getCharacterByUUID(st.character.getCharacterUUID());
        if (fresh != null) {
            st.character = fresh;
        }
        return st.character;
    }

    /**
     * 把「玩家身上的那份数据」重新取一遍。
     *
     * <p>服务端每推一次同步包都是把整份附件反序列化一遍，里面的对象会<b>换人</b>；
     * 直接在页面里存一份引用，就会出现「点了升级、界面纹丝不动，切出去再切回来才变」。
     * 所有读数据的地方都过这一道，拿到的就一定是这一刻玩家身上那份。
     */
    private static void pullFresh(State st) {
        PlayerCharactersAttachment attachment =
                st.player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        if (attachment != null) {
            st.attachment = attachment;
        }
        st.backpack = st.player.getData(AttachmentRegistration.BACKPACK_ATTACHMENT);
    }

    /** 本地点了一记写操作：下一 tick 先把界面按最新数据重画一次（不等指纹比对）。 */
    private static void markDirty(State st) {
        st.dirty = true;
    }

    /**
     * 按当前状态把控件树重画一遍。
     *
     * <p>页面不是每帧重建的：只在换页 / 换人 / 换选中 / 服务端数据变了（见
     * {@code createModularUI} 里那条 {@code TICK} 比对）时走这里。
     */
    private static void rebuild(State st) {
        pullFresh(st);
        if (st.main == null || viewed(st) == null || st.character.getData() == null) {
            return;
        }

        // 页签高亮跟当前页走（notifyChange=false，不会反过来又触发一次切换）
        st.tabs.forEach((page, tab) -> tab.setOn(page == st.page, false));
        st.viewOnlyNote.layout(l -> l.display(isViewOnly(st) ? TaffyDisplay.FLEX : TaffyDisplay.NONE));

        // 顶栏头像条也重画：换人 / 队伍变动 / 新抽到角色都会走到这里
        fillStrip(st);

        // 圣遗物页换姿势（闭眼合掌），其它页回到常态；动画名变了就换一个预览实体
        ensurePreviewAnimatable(st);

        boolean sub = st.subPage != SubPage.NONE;
        st.main.layout(l -> l.display(sub ? TaffyDisplay.NONE : TaffyDisplay.FLEX));
        st.sub.layout(l -> l.display(sub ? TaffyDisplay.FLEX : TaffyDisplay.NONE));

        if (sub) {
            st.sub.clearAllChildren();
            switch (st.subPage) {
                case ARTIFACT_CHANGE -> buildArtifactChangePage(st);
                case WEAPON_CHANGE -> buildWeaponChangePage(st);
                default -> buildLevelUpPage(st);
            }
            refreshOrbs(st);
            return;
        }

        fillTop(st);
        st.panel.clearAllChildren();
        st.detailCard.clearAllChildren();
        st.detailCard.layout(l -> l.display(TaffyDisplay.NONE));
        st.bottomLeft.clearAllChildren();
        st.bottomRight.clearAllChildren();
        switch (st.page) {
            case STATS -> buildStatsPage(st);
            case WEAPON -> buildWeaponPage(st);
            case ARTIFACT -> buildArtifactPage(st);
            case CONSTELLATION -> buildConstellationPage(st);
            case TALENT -> buildTalentPage(st);
            case PROFILE -> buildProfilePage(st);
        }
        fillBottomLeft(st);
        refreshOrbs(st);
    }

    /** 顶栏左侧那行「元素名 / 角色名」+ 元素图标。 */
    private static void fillTop(State st) {
        GenshinElement element = st.character.getElemental();
        if (st.elemIcon != null) {
            st.elemIcon.style(s -> s.background(element == null
                    ? IGuiTexture.EMPTY
                    : SpriteTexture.of("minegenshin:icon/elemental/" + element.getId() + ".png")));
        }
        if (st.topName != null) {
            String elementName = element == null ? "" : I18n.get(element.getTranslationKey());
            st.topName.setText(Component.literal(elementName.isEmpty() ? "" : elementName + " / ")
                    .append(st.character.getName()));
        }
    }

    /** 底栏左侧那格：原神那里是「提升指南」，这里就放一句当前场合的操作提示。 */
    private static void fillBottomLeft(State st) {
        if (st.bottomLeft == null) {
            return;
        }
        String hint = switch (st.page) {
            case ARTIFACT -> "gui.minegenshin.character_equip.orb_drag_hint";
            default -> "gui.minegenshin.character_equip.hint";
        };
        Label tip = new Label();
        tip.addClass("ce-bottom-tip");
        tip.setText(Component.translatable(hint));
        tip.layout(l -> l.height(11));
        st.bottomLeft.addChild(tip);
    }

    /** 打开一层子页面（更换圣遗物 / 升级）。升级页每次进来都把材料选择清掉。 */
    private static void openSubPage(State st, SubPage page) {
        st.subPage = page;
        if (page == SubPage.LEVEL_UP) {
            st.matSource = -1;
            st.matIndex = -1;
            st.matCount = 1;
            st.matExpValue = 0;
        } else {
            // 更换页每次进来都是「什么都没选」：右栏那张详情卡先空着等点格子
            clearSelection(st);
        }
        rebuild(st);
    }

    // ====================================================================
    //  主页面：属性 / 武器 / 圣遗物 / 资料
    // ====================================================================

    /** 属性页：等级 + 经验条 + 三段属性表 + 升级/突破。 */
    private static void buildStatsPage(State st) {
        PGCharacterData data = st.character.getData();

        // 原神右栏那块就是：名字 → 星级 → 等级/经验 → 属性表 → 详细信息 → 好感 → 简介
        st.panel.addChild(characterNamePlate(st));

        int cap = levelCap(data.getAscensionPhase());
        UIElement levelRow = new UIElement().addClass("ce-row");
        levelRow.addChildren(
                label(Component.translatable("gui.minegenshin.character_equip.level_format",
                        data.getLevel(), cap), "ce-level"),
                label(Component.translatable("gui.minegenshin.character_equip.exp",
                        data.getCurrentExp(), data.getMaxExp()), "ce-exp-num"));
        st.panel.addChild(levelRow);
        st.panel.addChild(expBar((double) data.getCurrentExp() / Math.max(1.0, data.getMaxExp())));

        ScrollerView scroller = newScroller("ce-stats-scroller");
        UIElement list = new UIElement().setId("ce-stats-list");
        list.addChild(baseStatRow(st, ModAttributes.MAX_HP.value()));
        list.addChild(baseStatRow(st, ModAttributes.ATK.value()));
        list.addChild(baseStatRow(st, ModAttributes.DEF.value()));
        list.addChild(valueStatRow(st, ModAttributes.ELEMENTAL_MASTERY.value()));
        list.addChild(valueStatRow(st, ModAttributes.MAX_STAMINA.value()));
        if (st.showDetails) {
            AttributeType[] advanced = {
                    ModAttributes.CR.value(), ModAttributes.CDG.value(), ModAttributes.HB.value(),
                    ModAttributes.IHB.value(), ModAttributes.ER.value(), ModAttributes.CDR.value(),
                    ModAttributes.SS.value()};
            for (AttributeType type : advanced) {
                list.addChild(percentStatRow(st, type));
            }
        }
        scroller.addScrollViewChild(list);
        st.panel.addChild(scroller);

        // 「详细信息」——原神里点开才铺开进阶词条，就是这个折叠
        st.panel.addChild(actionButton(
                Component.translatable(st.showDetails
                        ? "gui.minegenshin.character_equip.detail_less"
                        : "gui.minegenshin.character_equip.detail_info"),
                true,
                () -> {
                    st.showDetails = !st.showDetails;
                    rebuild(st);
                }));

        st.panel.addChild(friendshipRow());

        Label profile = label(Component.translatable("gui.minegenshin.character_equip.profile_intro",
                st.character.getName(), I18n.get(st.character.getElemental() == null
                        ? "" : st.character.getElemental().getTranslationKey())), "ce-story");
        st.panel.addChild(profile);

        // 底栏右侧：升级 / 上限突破（原神也是这两枚）
        boolean viewOnly = isViewOnly(st);
        boolean canAscend = data.getAscensionPhase() < 6 && data.getLevel() >= cap;
        st.bottomRight.addChildren(
                actionButton(Component.translatable("gui.minegenshin.character_equip.level_up"),
                        !viewOnly, () -> openLevelUpPage(st, TARGET_CHARACTER)),
                actionButton(Component.translatable("gui.minegenshin.character_equip.ascend"),
                        !viewOnly && canAscend,
                        () -> {
                            NetworkManager.sendAscendCharacterToServer();
                            markDirty(st);
                        }));
    }

    /** 右栏顶部那块：名字（大字）+ 星级 + 元素图标。 */
    private static UIElement characterNamePlate(State st) {
        UIElement box = new UIElement().addClass("ce-nameplate");
        UIElement line = new UIElement().addClass("ce-row");
        line.addChild(label(st.character.getName(), "ce-nameplate-name"));
        GenshinElement element = st.character.getElemental();
        if (element != null) {
            UIElement icon = new UIElement().addClass("ce-elem-sm");
            icon.style(s -> s.background(SpriteTexture.of(
                    "minegenshin:icon/elemental/" + element.getId() + ".png")));
            line.addChild(icon);
        }
        box.addChild(line);
        box.addChild(label(
                Component.literal("★".repeat(Math.max(1, st.character.getStarRating()))), "ce-star"));
        return box;
    }

    /**
     * 好感度那一行。模组里没有好感度数据（原版 PGCharacterData 没这一项），
     * 所以只按原神的位置画一条空槽 + 破折号，不编数字。
     */
    private static UIElement friendshipRow() {
        UIElement row = new UIElement().addClass("ce-row");
        row.addChildren(
                label(Component.translatable("gui.minegenshin.character_equip.friendship"), "ce-name"),
                expBar(0.0),
                label(Component.literal("—"), "ce-value-dim"));
        return row;
    }

    /** 武器页：身上那把的详情 + 可穿的武器列表（模组背包 + 玩家物品栏）+ 动作按钮。 */
    /**
     * 武器页：照原神那一页的版面 —— 武器悬浮在角色身旁（3D 场景里画），
     * <b>右栏只有一张武器详情卡</b>（名字 / 星级 / 等级 / 精炼 / 主副词条），
     * 底部一排是「替换 / 强化 / 突破 / 卸下」。
     *
     * <p>上一版把整个背包格子塞进右栏，和原神的版面不符：原神要换武器是开一层
     * 独立的「武器选择」页（见 {@link #buildWeaponChangePage}），那里才是大网格 + 详情 + 对比。
     */
    private static void buildWeaponPage(State st) {
        PGCharacterData data = st.character.getData();
        ItemStack equipped = data.getWeapon();

        // 原神这一页的右栏只有武器自己那张卡（角色名字在顶栏），不再压一块角色名牌
        st.panel.addChild(weaponSummary(equipped));

        boolean viewOnly = isViewOnly(st);
        WeaponStatsComponent stats = equipped.isEmpty() ? null
                : equipped.getOrDefault(ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT);
        boolean hasWeapon = !equipped.isEmpty() && stats != null;

        // 底栏右侧：原神那一排就是「替换 / 强化 / 突破」
        st.bottomRight.addChildren(
                actionButton(Component.translatable("gui.minegenshin.character_equip.change"), !viewOnly,
                        () -> openSubPage(st, SubPage.WEAPON_CHANGE)),
                actionButton(Component.translatable("gui.minegenshin.character_equip.level_up"),
                        !viewOnly && hasWeapon,
                        () -> openLevelUpPage(st, TARGET_WEAPON)),
                actionButton(Component.translatable("gui.minegenshin.character_equip.ascend"),
                        !viewOnly && hasWeapon && stats.canAscend(),
                        () -> {
                            NetworkManager.sendAscendWeaponToServer();
                            markDirty(st);
                        }),
                actionButton(Component.translatable("gui.minegenshin.character_equip.unequip"),
                        !viewOnly && hasWeapon,
                        () -> {
                            NetworkManager.sendUnequipArtifactToServer(ArtifactInventory.SLOT_WEAPON);
                            clearSelection(st);
                            markDirty(st);
                        }));
    }

    /**
     * 武器选择页（点武器页的「替换」进来）—— 原神那一页的版面：
     * 左边一大片格子（模组背包 + 玩家物品栏里这个角色能拿的那几类），
     * 右边是选中那把的详情 + 「装备」。
     *
     * <p>已经拿在手上的那一把排在第一格、标「装备中」，其余按来源排下去。
     */
    private static void buildWeaponChangePage(State st) {
        st.sub.addChild(subHeader(st, Component.translatable(
                "gui.minegenshin.character_equip.weapon_change_title")));

        UIElement body = new UIElement().setId("ce-sub-body");
        List<Owned> items = ownWeapons(st);

        ScrollerView left = newScroller("ce-weapon-change-scroller");
        left.addScrollViewChild(bigGrid(items, 8, st));
        body.addChild(left);

        UIElement right = new UIElement().setId("ce-change-right");
        right.layout(l -> l.width(196).flexShrink(0));
        ItemStack sel = st.sel == null ? ItemStack.EMPTY : st.sel.stack();
        right.addChild(weaponSummary(sel));

        boolean viewOnly = isViewOnly(st);
        boolean sameSlot = st.sel != null && st.sel.source() != Source.EQUIPPED;
        UIElement buttons = buttonRow();
        buttons.addChild(actionButton(
                Component.translatable("gui.minegenshin.character_equip.equip"),
                !viewOnly && sameSlot && sel.getItem() instanceof WeaponItem,
                () -> equip(st, ArtifactInventory.SLOT_WEAPON)));
        right.addChild(buttons);
        body.addChild(right);
        st.sub.addChild(body);
    }

    /**
     * 圣遗物页<b>右栏</b>：当前选中部位的详情 + 更换/升级/卸下。
     *
     * <p>用户口径是「左侧不直接显示圣遗物、装备的圣遗物出现在角色周围」——
     * 所以这一页的列表主体是模型周围那五枚 orb（见 {@link #refreshOrbs}），
     * 右栏只留「点开那一枚之后看到的东西」。
     */
    private static void buildArtifactPage(State st) {
        PGCharacterData data = st.character.getData();
        ArtifactInventory inv = data.getArtifactInventory();

        ScrollerView scroller = newScroller("ce-artifact-detail");
        UIElement box = new UIElement().setId("ce-artifact-detail-box");
        // 原神那一页的右上角只有这几样：角色面板四行 + 「圣遗物详情」+ 套装效果。
        // 装备本身不在这里列 —— 五枚圣遗物绕在角色周围（见 refreshOrbs），
        // 点其中一枚才进更换页，这是用户口径里明确要的版面。
        box.addChild(baseStatRow(st, ModAttributes.MAX_HP.value()));
        box.addChild(baseStatRow(st, ModAttributes.ATK.value()));
        box.addChild(baseStatRow(st, ModAttributes.DEF.value()));
        box.addChild(valueStatRow(st, ModAttributes.ELEMENTAL_MASTERY.value()));
        box.addChild(actionButton(
                Component.translatable("gui.minegenshin.character_equip.artifact_detail"), true,
                () -> openSubPage(st, SubPage.ARTIFACT_CHANGE)));
        box.addChild(sectionTitle("gui.minegenshin.character_equip.set_effect"));
        box.addChild(note("gui.minegenshin.character_equip.set_effect_none"));
        scroller.addScrollViewChild(box);
        st.panel.addChild(scroller);

        boolean viewOnly = isViewOnly(st);
        boolean hasArtifact = !inv.getItem(st.artifactSlot).isEmpty();
        // 底栏右侧：原神是「快速装备 R / 替换 F」，这里对应「更换 / 卸下 / 强化」
        st.bottomRight.addChildren(
                actionButton(Component.translatable("gui.minegenshin.character_equip.change"), true,
                        () -> openSubPage(st, SubPage.ARTIFACT_CHANGE)),
                actionButton(Component.translatable("gui.minegenshin.character_equip.unequip"),
                        !viewOnly && hasArtifact,
                        () -> {
                            NetworkManager.sendUnequipArtifactToServer(st.artifactSlot);
                            markDirty(st);
                        }),
                actionButton(Component.translatable("gui.minegenshin.character_equip.level_up"),
                        !viewOnly && hasArtifact,
                        () -> openLevelUpPage(st, st.artifactSlot)));
    }

    /** 资料页：角色基本信息 + 模型作者署名（可点开主页）。 */
    private static void buildProfilePage(State st) {
        PGCharacter character = st.character;
        PGCharacterData data = character.getData();
        GenshinElement element = character.getElemental();
        st.panel.addChild(characterNamePlate(st));

        ScrollerView scroller = newScroller("ce-profile-scroller");
        UIElement list = new UIElement().setId("ce-profile-list");
        list.addChild(sectionTitle("gui.minegenshin.character_equip.page.profile"));
        list.addChild(infoRow("gui.minegenshin.character_equip.info_element",
                Component.literal(element == null ? "-" : element.getId())));
        list.addChild(infoRow("gui.minegenshin.character_equip.info_level",
                Component.translatable("gui.minegenshin.character_equip.level_format",
                        data.getLevel(), levelCap(data.getAscensionPhase()))));
        list.addChild(infoRow("gui.minegenshin.character_equip.info_ascension",
                Component.literal(String.valueOf(data.getAscensionPhase()))));
        list.addChild(infoRow("gui.minegenshin.character_equip.info_constellation",
                Component.translatable("gui.minegenshin.character_equip.constellation_count",
                        character.getConstellation(), PGCharacterData.MAX_CONSTELLATION)));
        list.addChild(sectionTitle("gui.minegenshin.character_equip.model"));

        CharacterRenderData render = CharacterRenderRepository.get(character.getTextureId());
        if (render != null && render.modelAuthor() != null) {
            Label credit = new Label();
            credit.addClass("ce-note");
            credit.setText(Component.translatable(
                    "gui.minegenshin.character_config.model_author", render.modelAuthor()));
            credit.layout(l -> l.height(11));
            if (render.modelAuthorUrl() != null) {
                credit.addClass("ce-link");
                String url = render.modelAuthorUrl();
                credit.addEventListener(UIEvents.CLICK, event -> Util.getPlatform().openUri(url));
            }
            list.addChild(credit);
        } else {
            list.addChild(note("gui.minegenshin.character_equip.model_none"));
        }
        list.addChild(note("gui.minegenshin.character_equip.no_model"));
        scroller.addScrollViewChild(list);
        st.panel.addChild(scroller);
    }

    /** 当前突破阶段能到的等级上限（和服务端那条突破校验同一个算式）。 */
    private static int levelCap(int ascensionPhase) {
        return ascensionPhase <= 0 ? 20 : Math.min((ascensionPhase + 3) * 10, 90);
    }

    // ====================================================================
    //  通用小控件
    // ====================================================================

    private static Label sectionTitle(String key) {
        Label title = new Label();
        title.addClass("ce-section");
        title.setText(Component.translatable(key));
        title.layout(l -> l.height(11));
        return title;
    }

    private static Label note(String key) {
        Label label = new Label();
        label.addClass("ce-note");
        label.setText(Component.translatable(key));
        label.layout(l -> l.height(10));
        return label;
    }

    private static UIElement infoRow(String key, Component value) {
        UIElement row = new UIElement().addClass("ce-row");
        row.addChildren(label(Component.translatable(key), "ce-name"), label(value, "ce-value"));
        return row;
    }

    private static Label label(Component text, String styleClass) {
        Label label = new Label();
        label.addClass(styleClass);
        label.setText(text);
        label.layout(l -> l.height(11));
        return label;
    }

    /** 一条经验进度：底槽 + 按比例宽度的填充（宽度是 INLINE，颜色在 lss）。 */
    private static UIElement expBar(double ratio) {
        float percent = (float) Math.max(0.0, Math.min(1.0, ratio)) * 100f;
        UIElement track = new UIElement().addClass("ce-bar-track");
        UIElement fill = new UIElement().addClass("ce-bar-fill");
        fill.layout(l -> l.widthPercent(percent));
        track.addChild(fill);
        return track;
    }

    private static UIElement buttonRow() {
        return new UIElement().addClass("ce-row");
    }

    /**
     * 一枚动作按钮。{@code enabled=false} 时只是压暗 + 点了不做事 ——
     * 不撤元素是因为按钮行的两枚按钮要一直并排对位。
     */
    private static Button actionButton(Component text, boolean enabled, Runnable action) {
        Button button = new Button();
        button.addClass("ce-action");
        if (!enabled) {
            button.addClass("ce-action-off");
        }
        button.setText(text);
        // 定高 + 最小宽：底栏那一排与子页面里的两枚都用它，宽度由 lss 决定
        button.layout(l -> l.height(16).minWidth(46));
        button.setOnClick(event -> {
            if (enabled) {
                action.run();
            }
        });
        return button;
    }

    private static ScrollerView newScroller(String id) {
        ScrollerView scroller = new ScrollerView();
        scroller.setId(id);
        scroller.addClass("ce-scroller");
        scroller.viewPort.layout(l -> l.minWidth(16).minHeight(16));
        installDragScroll(scroller);
        return scroller;
    }

    /**
     * 给滚动区加一条「按住内容上下拖」的滚动方式（滚轮那套 LDLib2 自带，但拖手势没有）。
     *
     * <p>三处兜「拖到外面松手再回来还当按住」：指针离开视口即结束、每次移动先问
     * {@code isMouseDown(0)}、视口内正常松手时也清掉。
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

    // ====================================================================
    //  命之座 / 天赋
    // ====================================================================

    /**
     * 命之座页：六行节点 + 选中那一层的详情。
     *
     * <p>用户口径：「命之座的激活、介绍、名字都是点击对应项后显示的」——
     * 所以左半边只列六行（圆点 + 名字），右半边那张卡才是「名字 / 解锁状态 / 介绍」，
     * 升级（服务端要 GM 权限）的按钮也挂在卡上。
     */
    private static void buildConstellationPage(State st) {
        PGCharacter character = st.character;
        int owned = character.getConstellation();

        // 原神这一页：六个节点竖排在右栏，点哪一个才在左边弹出那一层的详情卡。
        Label count = new Label();
        count.addClass("ce-note");
        count.setText(Component.translatable("gui.minegenshin.character_equip.constellation_count",
                owned, PGCharacterData.MAX_CONSTELLATION));
        count.layout(l -> l.height(10));
        st.panel.addChild(count);

        ScrollerView scroller = newScroller("ce-con-scroller");
        UIElement list = new UIElement().setId("ce-con-list");
        for (int n = 1; n <= PGCharacterData.MAX_CONSTELLATION; n++) {
            list.addChild(constellationRow(st, n));
        }
        scroller.addScrollViewChild(list);
        st.panel.addChild(scroller);

        int n = clampConstellation(st.selConstellation);
        boolean unlocked = character.hasConstellation(n);
        st.detailCard.layout(l -> l.display(TaffyDisplay.FLEX));
        ScrollerView detailScroll = newScroller("ce-con-detail");
        UIElement detail = new UIElement().addClass("ce-detail");
        detail.addChild(label(Component.translatable("gui.minegenshin.character_equip.constellation_level", n),
                "ce-note"));
        detail.addChild(label(Component.literal(constellationName(character, n)), "ce-title-name"));
        detail.addChild(label(Component.translatable(unlocked ? "gui.minegenshin.character_equip.unlocked"
                : "gui.minegenshin.character_equip.locked"),
                unlocked ? "ce-value" : "ce-note"));
        detail.addChild(label(Component.literal(constellationDesc(character, n)), "ce-name"));
        detailScroll.addScrollViewChild(detail);
        st.detailCard.addChild(detailScroll);

        boolean canUpgrade = !isViewOnly(st) && hasCheatPermission(st.player)
                && owned < PGCharacterData.MAX_CONSTELLATION;
        st.bottomRight.addChild(actionButton(
                Component.translatable("gui.minegenshin.character_equip.constellation_up"), canUpgrade,
                () -> {
                    NetworkManager.sendUpgradeConstellationToServer();
                    markDirty(st);
                }));
    }

    private static UIElement constellationRow(State st, int n) {
        boolean unlocked = st.character.hasConstellation(n);
        UIElement row = new UIElement().addClass("ce-con-row");
        if (n == clampConstellation(st.selConstellation)) {
            row.addClass("ce-list-row-on");
        }
        UIElement dot = new UIElement().addClass("ce-con-dot");
        if (unlocked) {
            dot.addClass("ce-con-dot-on");
        }
        Label name = new Label();
        name.addClass(unlocked ? "ce-con-name-on" : "ce-con-name");
        name.setText(Component.literal(constellationName(st.character, n)));
        name.layout(l -> l.height(11));
        row.addChildren(dot, name);
        row.addEventListener(UIEvents.CLICK, event -> {
            st.selConstellation = n;
            rebuild(st);
        });
        return row;
    }

    private static int clampConstellation(int value) {
        return Math.max(1, Math.min(PGCharacterData.MAX_CONSTELLATION, value));
    }

    /** 命座名字：先找「这个角色这一层」的专属名，找不到退回通用的「一层 / 二层 …」。 */
    private static String constellationName(PGCharacter character, int n) {
        String id = character.getTextureId();
        return tr("gui.minegenshin.character_equip.constellation." + id + "." + n + ".name",
                "constellation.minegenshin." + id + "." + n + ".name",
                "gui.minegenshin.character_equip.constellation." + n);
    }

    /** 命座介绍：专属 {@code .desc} → 专属 {@code .applied} → 没有就写一句「暂无介绍」。 */
    private static String constellationDesc(PGCharacter character, int n) {
        String id = character.getTextureId();
        return tr("gui.minegenshin.character_equip.constellation." + id + "." + n + ".desc",
                "constellation.minegenshin." + id + "." + n + ".desc",
                "constellation.minegenshin." + id + "." + n + ".applied",
                "gui.minegenshin.character_equip.constellation_desc_none");
    }

    /** 天赋页：五行（普攻 / 战技 / 爆发 / 固有天赋 1、2）+ 选中那一项的详情。 */
    private static void buildTalentPage(State st) {
        // 原神这一页：右边竖排一列天赋（图标 + 名字 + Lv.N），点哪一个才在左边弹出详情卡。
        ScrollerView scroller = newScroller("ce-talent-scroller");
        UIElement list = new UIElement().setId("ce-talent-list");
        for (int kind = TALENT_NORMAL; kind <= TALENT_PASSIVE_2; kind++) {
            list.addChild(talentRow(st, kind));
        }
        scroller.addScrollViewChild(list);
        st.panel.addChild(scroller);

        int kind = clampTalent(st.selTalent);
        boolean unlocked = talentUnlocked(st, kind);
        boolean active = kind <= TALENT_BURST;

        st.detailCard.layout(l -> l.display(TaffyDisplay.FLEX));
        UIElement card = new UIElement().addClass("ce-detail");
        card.addChild(label(Component.translatable(talentKindKey2(kind)), "ce-note"));
        card.addChild(label(Component.literal(talentName(st.character, kind)), "ce-title-name"));
        if (active) {
            card.addChild(label(Component.translatable("gui.minegenshin.character_equip.talent_level",
                    talentLevel(st, kind), talentLevelCap(st, kind)), "ce-value"));
        }

        // 「天赋介绍 / 详细属性」两个小页签，和原神一样点了才切
        UIElement tabs = buttonRow();
        CustomToggle intro = new CustomToggle();
        intro.addClass("ce-subtab");
        intro.setButtonText(Component.translatable("gui.minegenshin.character_equip.talent_tab_intro"));
        intro.layout(l -> l.height(14).flex(1));
        intro.setOn(st.talentTab == 0, false);
        intro.setOnToggleChanged(on -> {
            if (on && st.talentTab != 0) {
                st.talentTab = 0;
                rebuild(st);
            } else if (!on && st.talentTab == 0) {
                intro.setOn(true, false);
            }
        });
        CustomToggle detailTab = new CustomToggle();
        detailTab.addClass("ce-subtab");
        detailTab.setButtonText(Component.translatable("gui.minegenshin.character_equip.talent_tab_detail"));
        detailTab.layout(l -> l.height(14).flex(1));
        detailTab.setOn(st.talentTab == 1, false);
        detailTab.setOnToggleChanged(on -> {
            if (on && st.talentTab != 1) {
                st.talentTab = 1;
                rebuild(st);
            } else if (!on && st.talentTab == 1) {
                detailTab.setOn(true, false);
            }
        });
        tabs.addChildren(intro, detailTab);
        card.addChild(tabs);

        ScrollerView detailScroll = newScroller("ce-talent-detail");
        // 这一层不再加 .ce-detail：外面那张 card 本身就是带边框的卡，套两层框很难看
        UIElement detail = new UIElement();
        if (st.talentTab == 0) {
            detail.addChild(label(Component.translatable(unlocked
                            ? "gui.minegenshin.character_equip.unlocked"
                            : "gui.minegenshin.character_equip.locked"),
                    unlocked ? "ce-value" : "ce-note"));
            detail.addChild(label(Component.literal(talentDesc(st.character, kind)), "ce-name"));
        } else {
            detail.addChild(label(Component.translatable("gui.minegenshin.character_equip.talent_level",
                    active ? talentLevel(st, kind) : 0,
                    active ? talentLevelCap(st, kind) : 0), "ce-value"));
            detail.addChild(label(Component.translatable("gui.minegenshin.character_equip.talent_desc_none"),
                    "ce-name"));
        }
        if (active && talentCanUpgrade(st, kind)) {
            int manual = manualTalentUpgrades(st, kind);
            detail.addChild(label(Component.translatable("gui.minegenshin.character_equip.upgrade_cost",
                    TalentUpgradeCost.primogem(manual), TalentUpgradeCost.experienceLevels(manual)),
                    "ce-value-dim"));
        }
        detailScroll.addScrollViewChild(detail);
        card.addChild(detailScroll);
        st.detailCard.addChild(card);

        boolean canUpgrade = !isViewOnly(st) && talentCanUpgrade(st, kind);
        st.bottomRight.addChild(actionButton(
                Component.translatable("gui.minegenshin.character_equip.level_up"), canUpgrade,
                () -> upgradeTalent(st, kind)));
    }

    /** 天赋详情卡顶上那行「普攻 / 元素战技 / 元素爆发 / 固有天赋」的类别名。 */
    private static String talentKindKey2(int kind) {
        return "gui.minegenshin.character_equip.talent.kind." + talentKindKey(kind);
    }

    /** 天赋的三类（0~2 有等级可升）+ 两条固有天赋（3、4，只有解锁状态）。 */
    private static final int TALENT_NORMAL = 0;
    private static final int TALENT_SKILL = 1;
    private static final int TALENT_BURST = 2;
    private static final int TALENT_PASSIVE_1 = 3;
    private static final int TALENT_PASSIVE_2 = 4;

    private static UIElement talentRow(State st, int kind) {
        boolean unlocked = talentUnlocked(st, kind);
        UIElement row = new UIElement().addClass("ce-list-row");
        if (kind == clampTalent(st.selTalent)) {
            row.addClass("ce-list-row-on");
        }
        UIElement dot = new UIElement().addClass("ce-con-dot");
        if (unlocked) {
            dot.addClass("ce-con-dot-on");
        }
        Label name = new Label();
        name.addClass(unlocked ? "ce-con-name-on" : "ce-con-name");
        name.setText(Component.literal(talentName(st.character, kind)));
        name.layout(l -> l.height(11));
        row.addChildren(dot, name);
        if (kind <= TALENT_BURST) {
            row.addChild(label(Component.literal(String.valueOf(talentLevel(st, kind))), "ce-value"));
        }
        row.addEventListener(UIEvents.CLICK, event -> {
            st.selTalent = kind;
            rebuild(st);
        });
        return row;
    }

    private static int clampTalent(int value) {
        return Math.max(TALENT_NORMAL, Math.min(TALENT_PASSIVE_2, value));
    }

    private static int talentLevel(State st, int kind) {
        PGCharacterData data = st.character.getData();
        return switch (kind) {
            case TALENT_SKILL -> data.getElementalSkillLevel();
            case TALENT_BURST -> data.getElementalBurstLevel();
            default -> data.getNormalAttackLevel();
        };
    }

    private static int talentLevelCap(State st, int kind) {
        PGCharacterData data = st.character.getData();
        return switch (kind) {
            case TALENT_SKILL -> data.getElementalSkillLevelCap();
            case TALENT_BURST -> data.getElementalBurstLevelCap();
            default -> data.getNormalAttackLevelCap();
        };
    }

    private static int manualTalentUpgrades(State st, int kind) {
        PGCharacterData data = st.character.getData();
        return switch (kind) {
            case TALENT_SKILL -> data.getManualElementalSkill();
            case TALENT_BURST -> data.getManualElementalBurst();
            default -> data.getManualNormalAttack();
        };
    }

    /**
     * 固有天赋的解锁门槛：突破 1 解第一条、突破 4 解第二条
     * （和服务端 {@code getAscensionPhase() >= n} 那套判据一致）。
     */
    private static boolean talentUnlocked(State st, int kind) {
        int phase = st.character.getData().getAscensionPhase();
        return switch (kind) {
            case TALENT_PASSIVE_1 -> phase >= 1;
            case TALENT_PASSIVE_2 -> phase >= 4;
            default -> true;
        };
    }

    private static boolean talentCanUpgrade(State st, int kind) {
        PGCharacterData data = st.character.getData();
        return switch (kind) {
            case TALENT_SKILL -> data.canUpgradeElementalSkill();
            case TALENT_BURST -> data.canUpgradeElementalBurst();
            case TALENT_NORMAL -> data.canUpgradeNormalAttack();
            default -> false;
        };
    }

    private static void upgradeTalent(State st, int kind) {
        switch (kind) {
            case TALENT_SKILL -> NetworkManager.sendUpgradeElementalSkillToServer();
            case TALENT_BURST -> NetworkManager.sendUpgradeElementalBurstToServer();
            case TALENT_NORMAL -> NetworkManager.sendUpgradeNormalAttackToServer();
            default -> {
            }
        }
        markDirty(st);
    }

    private static String talentKindKey(int kind) {
        return switch (kind) {
            case TALENT_SKILL -> "skill";
            case TALENT_BURST -> "burst";
            case TALENT_PASSIVE_1 -> "passive1";
            case TALENT_PASSIVE_2 -> "passive2";
            default -> "normal";
        };
    }

    /** 天赋名字：角色专属 → 通用（普攻 / 元素战技 / 元素爆发 / 固有天赋 1、2）。 */
    private static String talentName(PGCharacter character, int kind) {
        String id = character.getTextureId();
        String kindKey = talentKindKey(kind);
        return tr("gui.minegenshin.character_equip.talent." + id + "." + kindKey + ".name",
                "gui.minegenshin.character_equip.talent." + kindKey + ".name",
                "gui.minegenshin.character_equip.talent." + kindKey);
    }

    private static String talentDesc(PGCharacter character, int kind) {
        String id = character.getTextureId();
        String kindKey = talentKindKey(kind);
        return tr("gui.minegenshin.character_equip.talent." + id + "." + kindKey + ".desc",
                "gui.minegenshin.character_equip.talent." + kindKey + ".desc",
                "gui.minegenshin.character_equip.talent_desc_none");
    }

    /**
     * 依次去问 i18n，第一个「不是原样返回键名」的就算命中。
     *
     * <p>{@code I18n.get} 在缺失时把键名原样返回，所以「值 != 键」就是命中的判据 ——
     * 这样不用 {@code I18n.exists}（那一条在客户端语言表没加载完时行为不稳），
     * 也就能一次串起「角色专属 → 通用」这条链。
     */
    private static String tr(String... keys) {
        for (String key : keys) {
            String value = I18n.get(key);
            if (!value.equals(key)) {
                return value;
            }
        }
        return keys[keys.length - 1];
    }

    // ====================================================================
    //  子页面：更换圣遗物
    // ====================================================================

    /**
     * 更换圣遗物子页面（点模型周围那一枚 orb 进来）。
     *
     * <p>左边一大片格子（模组背包 + 玩家物品栏里同部位的那些），右边是选中那一件的详情与
     * 「装备 / 激活」。<b>未激活的圣遗物穿不上</b>（服务端 {@code isValidForSlot} 也拦），
     * 所以未激活的那一格压暗、详情卡上给一枚「激活」按钮 —— 背包里那件可以现场激活，
     * 物品栏里那件服务端没有激活口子，只能提示先放回背包。
     */
    private static void buildArtifactChangePage(State st) {
        st.sub.addChild(subHeader(st, Component.translatable(
                "gui.minegenshin.character_equip.change_title",
                Component.translatable(slotKey(st.artifactSlot)))));

        UIElement body = new UIElement().setId("ce-sub-body");
        List<Owned> items = ownArtifacts(st, st.artifactSlot);

        ScrollerView left = newScroller("ce-change-scroller");
        left.addScrollViewChild(bigGrid(items, 8, st));
        body.addChild(left);

        UIElement right = new UIElement().setId("ce-change-right");
        right.layout(l -> l.width(196).flexShrink(0));
        ItemStack sel = st.sel == null ? ItemStack.EMPTY : st.sel.stack();
        right.addChild(artifactDetail(sel, st.artifactSlot));

        boolean viewOnly = isViewOnly(st);
        boolean sameSlot = st.sel != null && st.sel.source() != Source.EQUIPPED;
        boolean activated = isActivated(sel);
        UIElement buttons = buttonRow();
        buttons.addChild(actionButton(
                Component.translatable("gui.minegenshin.character_equip.equip"),
                !viewOnly && sameSlot && activated,
                () -> equip(st, st.artifactSlot)));
        buttons.addChild(actionButton(
                Component.translatable("gui.minegenshin.character_equip.activate"),
                !viewOnly && sameSlot && !activated && st.sel.source() == Source.BACKPACK, () -> {
                    NetworkManager.sendActivateArtifactToServer(st.sel.index());
                    markDirty(st);
                }));
        right.addChild(buttons);
        body.addChild(right);
        st.sub.addChild(body);
    }

    // ====================================================================
    //  子页面：升级
    // ====================================================================

    /**
     * 升级子页面。用户口径：点「升级」不直接升，要另开一页读<b>玩家背包 + 模组背包</b>
     * 里所有的经验书，自己挑一种、自己定数量（+ / − 或直接填），上面显示经验值与升级后的
     * 属性；到了当前突破上限就显示 MAX 并禁止再加。
     */
    private static void buildLevelUpPage(State st) {
        st.sub.addChild(subHeader(st, Component.translatable(
                "gui.minegenshin.character_equip.level_up_title", levelTargetName(st, st.levelTarget))));

        UIElement body = new UIElement().setId("ce-sub-body");
        List<Mat> mats = levelMaterials(st);

        ScrollerView left = newScroller("ce-mat-scroller");
        UIElement matList = new UIElement().setId("ce-mat-list");
        if (mats.isEmpty()) {
            matList.addChild(note("gui.minegenshin.character_equip.no_material"));
        } else {
            for (Mat mat : mats) {
                matList.addChild(materialRow(st, mat));
            }
        }
        left.addScrollViewChild(matList);
        body.addChild(left);

        UIElement right = new UIElement().addClass("ce-detail");
        right.layout(l -> l.width(200).flexShrink(0));

        Label afterLevel = label(Component.literal(""), "ce-value-extra");
        Label afterDetail = label(Component.literal(""), "ce-value-dim");
        Runnable refresh = () -> refreshLevelUpPreview(st, afterLevel, afterDetail);

        right.addChild(levelHeadLine(st));
        right.addChild(levelBar(st));
        right.addChild(afterLevel);
        right.addChild(afterDetail);
        right.addChild(quantityRow(st, mats, refresh));
        right.addChild(actionButton(
                Component.translatable("gui.minegenshin.character_equip.level_up"),
                !isViewOnly(st) && canLevelUp(st) && st.matSource >= 0 && st.matCount > 0,
                () -> {
                    NetworkManager.sendEquipLevelUpBatchToServer(
                            st.levelTarget, st.matSource, st.matIndex, st.matCount);
                    markDirty(st);
                }));
        body.addChild(right);

        st.sub.addChild(body);
        refresh.run();
    }

    private static UIElement subHeader(State st, Component title) {
        UIElement head = new UIElement().setId("ce-sub-head");

        Button back = new Button();
        back.addClass("ce-action");
        back.setText(Component.translatable("gui.minegenshin.character_equip.back"));
        back.layout(l -> l.height(16).width(46));
        back.setOnClick(event -> {
            st.subPage = SubPage.NONE;
            clearSelection(st);
            rebuild(st);
        });

        Label label = new Label();
        label.setId("ce-sub-title");
        label.setText(title);
        label.layout(l -> l.height(15));

        head.addChildren(back, label);
        return head;
    }

    /** 升级页顶上那行「名字 + Lv.x / 上限」。 */
    private static UIElement levelHeadLine(State st) {
        UIElement row = new UIElement().addClass("ce-row");
        row.addChildren(label(Component.literal(levelTargetName(st, st.levelTarget)), "ce-name"),
                label(Component.literal(currentLevelText(st)), "ce-value"));
        return row;
    }

    /** 升级页那条经验进度（角色 / 武器 / 圣遗物各取自己那一份）。 */
    private static UIElement levelBar(State st) {
        double ratio;
        if (st.levelTarget == TARGET_CHARACTER) {
            PGCharacterData data = st.character.getData();
            ratio = (double) data.getCurrentExp() / Math.max(1.0, data.getMaxExp());
        } else if (st.levelTarget == TARGET_WEAPON) {
            ItemStack weapon = st.character.getData().getWeapon();
            WeaponStatsComponent stats = weapon.isEmpty() ? null : weapon.getOrDefault(
                    ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT);
            int star = weapon.getItem() instanceof WeaponItem item ? item.getStar() : 1;
            ratio = stats == null ? 0.0
                    : (double) stats.exp / Math.max(1.0, stats.getExpToNextLevel(star));
        } else {
            ItemStack artifact = st.character.getData().getArtifactInventory().getItem(st.levelTarget);
            ArtifactStatsComponent stats = artifact.isEmpty() ? null : artifact.getOrDefault(
                    ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT);
            int star = artifact.getItem() instanceof ArtifactItem item ? item.getStar() : 1;
            ratio = stats == null ? 0.0
                    : (double) stats.exp / Math.max(1.0, stats.getExpToNextLevel(star));
        }
        return expBar(ratio);
    }

    /** 「− 数量 + ／ 快捷放入」那一行。数量只有点按钮或改了输入框才动，不整页重建。 */
    private static UIElement quantityRow(State st, List<Mat> mats, Runnable refresh) {
        UIElement row = new UIElement().addClass("ce-row");
        row.addChild(label(Component.translatable("gui.minegenshin.character_equip.use_count"), "ce-name-sm"));

        int available = availableMaterialCount(st, mats);
        Button minus = stepButton("−", () -> {
            st.matCount = Math.max(1, st.matCount - 1);
            refresh.run();
        });
        TextField field = new TextField();
        field.addClass("ce-count-field");
        field.setOverflowVisible(true);
        field.layout(l -> l.height(16).width(40));
        field.setNumbersOnlyInt(1, Math.max(1, available));
        field.setText(String.valueOf(Math.max(1, Math.min(st.matCount, Math.max(1, available)))), false);
        field.setTextResponder(text -> {
            int parsed;
            try {
                parsed = Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                // 敲到一半的内容（空串之类）不算数
                return;
            }
            st.matCount = Math.max(1, Math.min(parsed, Math.max(1, availableMaterialCount(st, mats))));
            refresh.run();
        });
        Button plus = stepButton("＋", () -> {
            st.matCount = Math.min(Math.max(1, availableMaterialCount(st, mats)), st.matCount + 1);
            refresh.run();
        });
        row.addChildren(minus, field, plus);

        Button fill = new Button();
        fill.addClass("ce-action");
        fill.setText(Component.translatable("gui.minegenshin.character_equip.quick_fill"));
        fill.layout(l -> l.height(16).flex(1));
        fill.setOnClick(event -> {
            st.matCount = Math.max(1, availableMaterialCount(st, mats));
            rebuild(st);
        });
        row.addChild(fill);
        return row;
    }

    private static Button stepButton(String text, Runnable action) {
        Button button = new Button();
        button.addClass("ce-step");
        button.setText(Component.literal(text));
        button.layout(l -> l.height(16).width(16));
        button.setOnClick(event -> action.run());
        return button;
    }

    /** 当前选中的那种书还有几本（没选就 0）。 */
    private static int availableMaterialCount(State st, List<Mat> mats) {
        for (Mat mat : mats) {
            if (mat.source() == st.matSource && mat.index() == st.matIndex) {
                return mat.stack().getCount();
            }
        }
        return 0;
    }

    /** 一行材料：图标 + 名字 + 剩余本数 + 一本多少经验。点一下选中它。 */
    private static UIElement materialRow(State st, Mat mat) {
        UIElement row = new UIElement().addClass("ce-list-row");
        if (mat.source() == st.matSource && mat.index() == st.matIndex) {
            row.addClass("ce-list-row-on");
        }
        UIElement icon = new UIElement().addClass("ce-mat-icon");
        icon.style(s -> s.background(SpriteTexture.of(ItemIcons.pathOf(mat.stack()))));
        row.addChildren(icon,
                label(mat.stack().getHoverName().copy(), "ce-name"),
                label(Component.literal("x" + mat.stack().getCount()), "ce-value"),
                label(Component.translatable("gui.minegenshin.character_equip.exp_per_book",
                        mat.expValue()), "ce-value-dim"));
        row.addEventListener(UIEvents.CLICK, event -> {
            st.matSource = mat.source();
            st.matIndex = mat.index();
            st.matExpValue = mat.expValue();
            st.matCount = 1;
            rebuild(st);
        });
        return row;
    }

    /** 把「+ 进去这么多经验之后会变成什么样」重新算一遍，写进那两行预览。 */
    private static void refreshLevelUpPreview(State st, Label levelLabel, Label detailLabel) {
        int exp = Math.max(0, st.matExpValue) * Math.max(0, st.matCount);
        if (st.levelTarget == TARGET_CHARACTER) {
            int[] preview = previewCharacterExp(st, exp);
            PGCharacterData data = st.character.getData();
            int cap = levelCap(data.getAscensionPhase());
            if (preview[0] >= cap) {
                levelLabel.setText(Component.translatable("gui.minegenshin.character_equip.exp_max"));
            } else {
                levelLabel.setText(Component.translatable("gui.minegenshin.character_equip.level_format",
                        preview[0], cap));
            }
            detailLabel.setText(Component.translatable("gui.minegenshin.character_equip.exp_to_add",
                    exp, preview[1]));
        } else if (st.levelTarget == TARGET_WEAPON) {
            int[] preview = previewWeaponExp(st, exp);
            levelLabel.setText(preview == null
                    ? Component.translatable("gui.minegenshin.character_equip.exp_max")
                    : Component.translatable("gui.minegenshin.character_equip.level_format",
                            preview[0], preview[1]));
            detailLabel.setText(Component.translatable("gui.minegenshin.character_equip.exp_to_add",
                    exp, preview == null ? 0 : preview[2]));
        } else {
            int[] preview = previewArtifactExp(st, exp);
            levelLabel.setText(preview == null
                    ? Component.translatable("gui.minegenshin.character_equip.exp_max")
                    : Component.translatable("gui.minegenshin.character_equip.level_format",
                            preview[0], preview[1]));
            detailLabel.setText(Component.translatable("gui.minegenshin.character_equip.exp_to_add",
                    exp, preview == null ? 0 : preview[2]));
        }
    }

    /** 这个升级目标还能不能升（满级 / 到突破上限就是 false）。 */
    private static boolean canLevelUp(State st) {
        PGCharacterData data = st.character.getData();
        if (st.levelTarget == TARGET_CHARACTER) {
            return data.getLevel() < levelCap(data.getAscensionPhase());
        }
        if (st.levelTarget == TARGET_WEAPON) {
            ItemStack weapon = data.getWeapon();
            if (weapon.getItem() instanceof WeaponItem item) {
                WeaponStatsComponent stats = weapon.getOrDefault(
                        ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT);
                return stats.level < stats.getMaxLevel();
            }
            return false;
        }
        ItemStack artifact = data.getArtifactInventory().getItem(st.levelTarget);
        if (artifact.getItem() instanceof ArtifactItem item) {
            ArtifactStatsComponent stats = artifact.getOrDefault(
                    ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT);
            return stats.level < stats.getMaxLevel(item.getStar());
        }
        return false;
    }

    /** 升级页顶上那行「当前的等级」。 */
    private static String currentLevelText(State st) {
        PGCharacterData data = st.character.getData();
        if (st.levelTarget == TARGET_CHARACTER) {
            return I18n.get("gui.minegenshin.character_equip.level_format",
                    data.getLevel(), levelCap(data.getAscensionPhase()));
        }
        if (st.levelTarget == TARGET_WEAPON) {
            ItemStack weapon = data.getWeapon();
            WeaponStatsComponent stats = weapon.getOrDefault(
                    ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT);
            return I18n.get("gui.minegenshin.character_equip.level_format",
                    stats.level, stats.getMaxLevel());
        }
        ItemStack artifact = data.getArtifactInventory().getItem(st.levelTarget);
        ArtifactStatsComponent stats = artifact.getOrDefault(
                ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT);
        int star = artifact.getItem() instanceof ArtifactItem item ? item.getStar() : 1;
        return I18n.get("gui.minegenshin.character_equip.level_format",
                stats.level, stats.getMaxLevel(star));
    }

    private static String levelTargetName(State st, int target) {
        PGCharacterData data = st.character.getData();
        if (target == TARGET_CHARACTER) {
            return st.character.getName().getString();
        }
        if (target == TARGET_WEAPON) {
            ItemStack weapon = data.getWeapon();
            return weapon.isEmpty()
                    ? I18n.get("gui.minegenshin.character_equip.page.weapon")
                    : weapon.getHoverName().getString();
        }
        ItemStack artifact = data.getArtifactInventory().getItem(target);
        return artifact.isEmpty()
                ? I18n.get(slotKey(target))
                : artifact.getHoverName().getString();
    }

    private static void openLevelUpPage(State st, int target) {
        st.levelTarget = target;
        clearSelection(st);
        openSubPage(st, SubPage.LEVEL_UP);
    }

    /**
     * 预演「再加 {@code exp} 点经验」之后的角色等级。
     *
     * <p>升级表走 {@link CharacterXpConfig#getAllXp()}（和服务端 {@code PGCharacter#tryLevelUp}
     * 同一张），并且<b>卡在当前突破阶段的等级上限</b>上 —— 到顶之后多出来的经验不保留
     * （用户口径：「如果到达当前突破等级最高的等级则显示 max … 溢出部分就不保留了」）。
     *
     * @return {@code [升级后等级, 溢出的经验, 升级后这一级里攒下的经验]}
     */
    private static int[] previewCharacterExp(State st, int exp) {
        PGCharacterData data = st.character.getData();
        List<Integer> table = CharacterXpConfig.getAllXp();
        int level = data.getLevel();
        int cap = levelCap(data.getAscensionPhase());
        int current = Math.max(0, data.getCurrentExp());
        int remain = Math.max(0, exp);

        while (level < cap && remain > 0) {
            int index = Math.max(0, Math.min(level - 1, table.size() - 1));
            int need = Math.max(1, table.get(index)) - current;
            if (remain >= need) {
                remain -= need;
                level++;
                current = 0;
            } else {
                current += remain;
                remain = 0;
            }
        }
        return new int[]{level, remain, current};
    }

    /**
     * 预演武器的升级结果。走的是一份 {@code copy()}：服务端那条
     * {@code WeaponStatsComponent#addExp} 是同一段逻辑，但这里绝不能写回玩家手里那把武器。
     *
     * @return {@code [等级, 等级上限, 这一级里的经验, 溢出（storedExp）]}
     */
    @Nullable
    private static int[] previewWeaponExp(State st, int exp) {
        ItemStack weapon = st.character.getData().getWeapon();
        if (!(weapon.getItem() instanceof WeaponItem item)) {
            return null;
        }
        WeaponStatsComponent stats = weapon.getOrDefault(
                ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT).copy();
        stats.addExp(exp, item.getStar());
        return new int[]{stats.level, stats.getMaxLevel(), stats.exp, stats.storedExp};
    }

    /** 预演圣遗物的升级结果（副词条是随机的，这里只是「大概会到几级」）。 */
    @Nullable
    private static int[] previewArtifactExp(State st, int exp) {
        ItemStack artifact = st.character.getData().getArtifactInventory().getItem(st.levelTarget);
        if (!(artifact.getItem() instanceof ArtifactItem item)) {
            return null;
        }
        ArtifactStatsComponent stats = artifact.getOrDefault(
                ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT).copy();
        stats.addExp(exp, item.getStar(), item.getType());
        return new int[]{stats.level, stats.getMaxLevel(item.getStar()), stats.exp};
    }

    // ====================================================================
    //  装备来源：模组背包 + 玩家物品栏
    // ====================================================================

    /**
     * 一件「身上穿的 / 背包里的 / 物品栏里的」东西从哪来 —— 换装时按它决定发哪一条包。
     *
     * <p>用户口径：「武器列表同时读取玩家物品栏和原神背包里对应类型的武器 + 圣遗物也这样」。
     * 两个来源在服务端是<b>两套坐标系</b>（模组背包分类下标 vs 原版物品栏槽位），
     * 对应的 RPC 也不一样（见 {@link NetworkManager#sendEquipOrSwapArtifactToServer}、
     * {@link NetworkManager#sendEquipFromInventoryToServer}），所以来源必须跟着件一起传。
     */
    private enum Source {
        BACKPACK, INVENTORY, EQUIPPED
    }

    /** 列表里的一件：物品 + 来源 + 那一格的下标（{@link Source#EQUIPPED} 时下标 = 装备位）。 */
    private record Owned(ItemStack stack, Source source, int index) {
    }

    /**
     * 一本经验书 + 它在哪个坐标上。
     *
     * <p>{@code source} 取值是 {@link NetworkManager#EQUIP_MATERIAL_SOURCE_INVENTORY} /
     * {@link NetworkManager#EQUIP_MATERIAL_SOURCE_BACKPACK}，服务端按它决定去翻哪一份列表。
     */
    private record Mat(ItemStack stack, int source, int index, int expValue) {
    }

    /** 当前角色能穿的武器：身上那把 + 模组背包武器分类 + 玩家主物品栏。 */
    private static List<Owned> ownWeapons(State st) {
        List<Owned> out = new ArrayList<>();
        ItemStack equipped = st.character.getData().getWeapon();
        if (!equipped.isEmpty()) {
            out.add(new Owned(equipped.copy(), Source.EQUIPPED, ArtifactInventory.SLOT_WEAPON));
        }
        Class<? extends WeaponItem> allowed = st.character.getAllowedWeaponClass();
        if (st.backpack != null) {
            List<ItemStack> list = st.backpack.getCategoryList(Backpack.Category.WEAPONS);
            for (int i = 0; i < list.size(); i++) {
                ItemStack stack = list.get(i);
                if (!stack.isEmpty() && allowed.isInstance(stack.getItem())) {
                    out.add(new Owned(stack.copy(), Source.BACKPACK, i));
                }
            }
        }
        collectInventory(st.player, stack -> allowed.isInstance(stack.getItem()), out);
        return out;
    }

    /** 某个部位能穿的圣遗物：身上那件 + 模组背包圣遗物分类 + 玩家主物品栏里同部位的。 */
    private static List<Owned> ownArtifacts(State st, int slot) {
        List<Owned> out = new ArrayList<>();
        ArtifactInventory inventory = st.character.getData().getArtifactInventory();
        ItemStack equipped = inventory.getItem(slot);
        if (!equipped.isEmpty()) {
            out.add(new Owned(equipped.copy(), Source.EQUIPPED, slot));
        }
        ArtifactType wanted = slotType(slot);
        if (wanted != null && st.backpack != null) {
            List<ItemStack> list = st.backpack.getCategoryList(Backpack.Category.ARTIFACTS);
            for (int i = 0; i < list.size(); i++) {
                ItemStack stack = list.get(i);
                if (stack.getItem() instanceof ArtifactItem artifact && artifact.getType() == wanted) {
                    out.add(new Owned(stack.copy(), Source.BACKPACK, i));
                }
            }
        }
        if (wanted != null) {
            collectInventory(st.player,
                    stack -> stack.getItem() instanceof ArtifactItem artifact && artifact.getType() == wanted,
                    out);
        }
        return out;
    }

    /** 玩家主物品栏（快捷栏 + 主背包，跳过盔甲与副手）里符合条件的那几格。 */
    private static void collectInventory(Player player, Predicate<ItemStack> filter, List<Owned> out) {
        var inventory = player.getInventory();
        for (int i = 0; i < INVENTORY_MAIN_SLOTS && i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && filter.test(stack)) {
                out.add(new Owned(stack.copy(), Source.INVENTORY, i));
            }
        }
    }

    private static ArtifactType slotType(int slot) {
        return slot >= 0 && slot < ARTIFACT_TYPES.length ? ARTIFACT_TYPES[slot] : null;
    }

    /** 升级材料：模组背包的 develop 分类 + 玩家主物品栏里的经验书。 */
    private static List<Mat> levelMaterials(State st) {
        List<Mat> out = new ArrayList<>();
        if (st.backpack != null) {
            List<ItemStack> list = st.backpack.getCategoryList(Backpack.Category.DEVELOPMENT);
            for (int i = 0; i < list.size(); i++) {
                ItemStack stack = list.get(i);
                if (stack.getItem() instanceof AdviceBookItem book) {
                    out.add(new Mat(stack.copy(), NetworkManager.EQUIP_MATERIAL_SOURCE_BACKPACK,
                            i, book.getExpValue()));
                }
            }
        }
        var inventory = st.player.getInventory();
        for (int i = 0; i < INVENTORY_MAIN_SLOTS && i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof AdviceBookItem book) {
                out.add(new Mat(stack.copy(), NetworkManager.EQUIP_MATERIAL_SOURCE_INVENTORY,
                        i, book.getExpValue()));
            }
        }
        return out;
    }

    // ====================================================================
    //  列表格子 / 选中 / 换装
    // ====================================================================

    private static UIElement grid(List<Owned> items, int columns, State st) {
        return gridOf(items, columns, st, false);
    }

    private static UIElement bigGrid(List<Owned> items, int columns, State st) {
        return gridOf(items, columns, st, true);
    }

    private static UIElement gridOf(List<Owned> items, int columns, State st, boolean big) {
        UIElement grid = new UIElement().addClass("ce-grid");
        if (items.isEmpty()) {
            grid.addChild(note("gui.minegenshin.character_equip.empty_list"));
            return grid;
        }
        UIElement row = new UIElement().addClass("ce-grid-row");
        grid.addChild(row);
        for (int i = 0; i < items.size(); i++) {
            if (i > 0 && i % columns == 0) {
                row = new UIElement().addClass("ce-grid-row");
                grid.addChild(row);
            }
            row.addChild(big ? bigSlot(items.get(i), st) : smallSlot(items.get(i), st));
        }
        return grid;
    }

    private static UIElement smallSlot(Owned owned, State st) {
        UIElement slot = new UIElement().addClass("ce-slot");
        if (!owned.stack().isEmpty()) {
            slot.style(s -> s.background(SpriteTexture.of(ItemIcons.pathOf(owned.stack()))));
        }
        if (owned.stack().getItem() instanceof ArtifactItem && !isActivated(owned.stack())) {
            slot.addClass("ce-slot-dim");
        }
        if (isSelected(st, owned)) {
            slot.addClass("ce-slot-selected");
        }
        slot.addEventListener(UIEvents.CLICK, event -> select(st, owned));
        return slot;
    }

    private static UIElement bigSlot(Owned owned, State st) {
        UIElement slot = new UIElement().addClass("ce-big-slot");
        if (!owned.stack().isEmpty()) {
            slot.style(s -> s.background(SpriteTexture.of(ItemIcons.pathOf(owned.stack()))));
        }
        if (owned.stack().getItem() instanceof ArtifactItem && !isActivated(owned.stack())) {
            slot.addClass("ce-slot-dim");
        }
        if (isSelected(st, owned)) {
            slot.addClass("ce-big-slot-selected");
        }
        // 正穿在身上那一件打一枚「装备中」角标（原神武器页那一格就是这么标的）
        if (owned.source() == Source.EQUIPPED) {
            Label tag = new Label();
            tag.addClass("ce-equipped-tag");
            tag.setText(Component.translatable("gui.minegenshin.character_equip.equipped"));
            tag.layout(l -> l.positionType(TaffyPosition.ABSOLUTE).left(0).bottom(0).widthPercent(100));
            slot.addChild(tag);
        }
        slot.addEventListener(UIEvents.CLICK, event -> select(st, owned));
        return slot;
    }

    private static boolean isSelected(State st, Owned owned) {
        return st.sel != null && st.sel.source() == owned.source() && st.sel.index() == owned.index();
    }

    private static void select(State st, Owned owned) {
        st.sel = owned;
        rebuild(st);
    }

    private static void clearSelection(State st) {
        st.sel = null;
    }

    /**
     * 把当前选中的那一件穿到 {@code slot} 上。
     *
     * <p>这里只发一条包，界面上的数字交给「服务端推回来 → {@code TICK} 比对到指纹变了再重画」
     * 这条链（见 {@link #dataStamp}）。本地不自己改一份：服务端会夹数量、复核合法性，
     * 本地先改会出现「界面显示换了、服务端没换」这种两边打架的状态。
     */
    private static void equip(State st, int slot) {
        Owned sel = st.sel;
        if (sel == null || sel.source() == Source.EQUIPPED || isViewOnly(st)) {
            return;
        }
        if (slot == ArtifactInventory.SLOT_WEAPON) {
            if (!(sel.stack().getItem() instanceof WeaponItem)) {
                return;
            }
        } else if (!isActivated(sel.stack()) || !ArtifactInventory.isValidForSlot(slot, sel.stack())) {
            return;
        }
        if (sel.source() == Source.INVENTORY) {
            NetworkManager.sendEquipFromInventoryToServer(slot, sel.index());
        } else {
            NetworkManager.sendEquipOrSwapArtifactToServer(slot, sel.index());
        }
        markDirty(st);
        if (slot >= 0 && slot < ARTIFACT_TYPES.length) {
            st.artifactSlot = slot;
        }
        clearSelection(st);
        rebuild(st);
    }

    /** 这件东西算不算「已激活」——武器无所谓，圣遗物没激活就穿不上（服务端也拦）。 */
    private static boolean isActivated(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof WeaponItem) {
            return true;
        }
        ArtifactStatsComponent stats = stack.getOrDefault(
                ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT);
        return stats.activated;
    }

    // ====================================================================
    //  详情卡
    // ====================================================================

    private static UIElement artifactDetail(ItemStack stack, int slot) {
        UIElement box = new UIElement().addClass("ce-detail");
        if (!(stack.getItem() instanceof ArtifactItem artifact)) {
            box.addChild(label(Component.translatable(slotKey(slot)), "ce-title-name"));
            box.addChild(note("gui.minegenshin.character_equip.empty_slot"));
            return box;
        }
        ArtifactStatsComponent stats = stack.getOrDefault(
                ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT);
        box.addChild(label(stack.getHoverName().copy(), "ce-title-name"));
        box.addChild(label(Component.literal("★".repeat(Math.max(1, artifact.getStar()))), "ce-star"));
        box.addChild(label(Component.translatable("gui.minegenshin.character_equip.level_format",
                stats.level, stats.getMaxLevel(artifact.getStar())), "ce-value"));
        box.addChild(label(Component.translatable("artifact.type."
                + artifact.getType().name().toLowerCase(Locale.ROOT)), "ce-note"));
        if (stats.mainStat != null && stats.mainStat.isInitialized()) {
            box.addChild(label(Component.literal(statText(stats.mainStat)), "ce-value-base"));
        }
        if (stats.subStats != null) {
            for (TeyvatItemStat sub : stats.subStats) {
                if (sub.isInitialized()) {
                    box.addChild(label(Component.literal(statText(sub)), "ce-value-dim"));
                }
            }
        }
        box.addChild(label(Component.translatable(stats.activated
                ? "gui.minegenshin.character_equip.unlocked"
                : "gui.minegenshin.character_equip.locked"),
                stats.activated ? "ce-value-extra" : "ce-note"));
        return box;
    }

    /**
     * 一张武器详情卡 —— 武器页的右栏、武器选择页的右栏、子页面里的小卡共用同一份。
     *
     * <p>内容照原神那一页自上而下排：名字 → 星级 → 类型 → 等级 / 精炼 → 主词条（基础攻击力）
     * → 副词条。空手进来时（没装备武器 / 还没选）给一句占位说明。
     */
    private static UIElement weaponSummary(ItemStack stack) {
        UIElement box = new UIElement().addClass("ce-detail");
        if (!(stack.getItem() instanceof WeaponItem weapon)) {
            box.addChild(sectionTitle("gui.minegenshin.character_equip.page.weapon"));
            box.addChild(note("message.minegenshin.no_weapon_equipped"));
            return box;
        }
        WeaponStatsComponent stats = stack.getOrDefault(
                ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT);
        box.addChild(label(stack.getHoverName().copy(), "ce-title-name"));
        box.addChild(label(Component.literal("★".repeat(Math.max(1, weapon.getStar()))), "ce-star"));
        box.addChild(label(Component.translatable(weaponTypeKey(stack)), "ce-note"));
        box.addChild(label(Component.translatable("gui.minegenshin.character_equip.level_format",
                stats.level, stats.getMaxLevel()), "ce-value"));
        if (weapon.canRefine()) {
            box.addChild(label(Component.translatable("gui.minegenshin.character_equip.refinement",
                    stats.refinementRank), "ce-value-dim"));
        }
        if (stats.mainStat != null && stats.mainStat.isInitialized()) {
            box.addChild(label(Component.literal(statText(stats.mainStat)), "ce-value-base"));
        }
        if (stats.subStat != null && stats.subStat.isInitialized()) {
            box.addChild(label(Component.literal(statText(stats.subStat)), "ce-value-dim"));
        }
        box.addChild(sectionTitle("gui.minegenshin.character_equip.weapon_passive"));
        box.addChild(note("gui.minegenshin.character_equip.weapon_passive_none"));
        return box;
    }

    /** 武器那条「类型」文字：弓 / 法器 / 双手剑 / 长柄武器 / 单手剑。 */
    private static String weaponTypeKey(ItemStack stack) {
        if (stack.getItem() instanceof Bow) {
            return "gui.minegenshin.character_equip.weapon_type.bow";
        }
        if (stack.getItem() instanceof Catalyst) {
            return "gui.minegenshin.character_equip.weapon_type.catalyst";
        }
        if (stack.getItem() instanceof Claymore) {
            return "gui.minegenshin.character_equip.weapon_type.claymore";
        }
        if (stack.getItem() instanceof Polearm) {
            return "gui.minegenshin.character_equip.weapon_type.polearm";
        }
        return "gui.minegenshin.character_equip.weapon_type.sword";
    }

    /** 一条词条的文字：「元素精通 +187」/「暴击率 +31.1%」。 */
    private static String statText(TeyvatItemStat stat) {
        if (stat == null || stat.getAttribute() == null) {
            return "";
        }
        String name = I18n.get(stat.getAttribute().translationKey());
        String value = stat.getKind() == TeyvatItemStat.StatKind.PERCENT
                ? fmtPercent(stat.getValue())
                : fmtNumber(stat.getValue());
        return name + " +" + value;
    }

    // ====================================================================
    //  属性行
    // ====================================================================

    /** 生命值上限 / 攻击力 / 防御力：总值（白）+ 基础值（黄）+ 额外值（绿，带 + 号）。 */
    private static UIElement baseStatRow(State st, AttributeType type) {
        PGCharacterData data = st.character.getData();
        double total = data.getAttributeTotalValue(type);
        double base = data.getAttributeBaseValue(type);
        double extra = total - base;
        boolean breakdown = base >= 0.5;
        return statRow(type, fmtNumber(total),
                breakdown ? fmtNumber(base) : "",
                breakdown && extra >= 0.5 ? fmtNumber(extra) : "");
    }

    /** 只有总值的一项（元素精通 / 体力上限 —— 没有基础值与额外值）。 */
    private static UIElement valueStatRow(State st, AttributeType type) {
        return statRow(type, fmtNumber(st.character.getData().getAttributeTotalValue(type)), "", "");
    }

    /** 百分比属性：三格都换算成百分比，基础值存不存在由数据说了算。 */
    private static UIElement percentStatRow(State st, AttributeType type) {
        PGCharacterData data = st.character.getData();
        double total = data.getAttributeTotalValue(type);
        double base = data.getAttributeBaseValue(type);
        double extra = total - base;
        boolean breakdown = Math.abs(base) >= 0.0005;
        return statRow(type, fmtPercent(total),
                breakdown ? fmtPercent(base) : "",
                breakdown && Math.abs(extra) >= 0.0005 ? fmtPercent(extra) : "");
    }

    private static UIElement statRow(AttributeType type, String total, String base, String extra) {
        UIElement row = new UIElement().addClass("ce-row");
        row.addChildren(
                label(Component.translatable(type.translationKey()), "ce-name-sm"),
                label(Component.literal(total), "ce-value"),
                label(Component.literal(base), "ce-value-base"),
                label(Component.literal(extra.isEmpty() ? "" : "+" + extra), "ce-value-extra"));
        return row;
    }

    /** 大数字带千位分隔（13,226），和原神面板一致。 */
    private static String fmtNumber(double value) {
        return String.format(Locale.ROOT, "%,d", Math.round(value));
    }

    /** 属性表里存的是小数（0.05 = 5%），显示时换算成百分比。 */
    private static String fmtPercent(double ratio) {
        return String.format(Locale.ROOT, "%.1f%%", ratio * 100.0);
    }

    /**
     * 当前玩家算不算「作弊模式」—— 只看原版权限等级 2（开作弊 / OP）。
     *
     * <p>命座升级是服务端要求 GM 权限的操作，非作弊时那枚按钮直接压暗。
     */
    private static boolean hasCheatPermission(Player player) {
        return player != null && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    // ====================================================================
    //  数据指纹：服务端推回来的东西变了就重画一次
    // ====================================================================

    /**
     * 把「页面上显示的那些数据」压成一串指纹。
     *
     * <h2>为什么要这一层</h2>
     * 控件树是按「点下去那一刻」的数据画的，而升级 / 换装的结果是服务端算完再推回来的
     * （{@code playerCharactersRPCPacket} 会把整份附件重新反序列化一遍）。没有这一层，
     * 玩家点完升级，面板会一直停在旧数字上，直到他自己再去点别的才刷新。
     *
     * <p>LDLib2 每客户端 tick 会给根元素派一次 {@code tick}，在那里比一次指纹最省事：
     * 没变就什么也不做，变了才重建 —— 滚动位置和选中项都保得住。
     */
    private static String dataStamp(State st) {
        StringBuilder sb = new StringBuilder(256);
        // 顶栏那条头像条也显示「当前在场是谁」，切人之后要跟着重画
        sb.append(st.attachment.getCurrentCharacterIndex()).append('/');
        for (int i = 0; i < 4; i++) {
            PGCharacter member = st.attachment.getPartyCharacter(i);
            sb.append(member == null ? "-" : member.getCharacterUUID()).append('/');
        }
        PGCharacter character = viewed(st);
        if (character == null) {
            return sb.toString();
        }
        sb.append(character.getCharacterUUID()).append('/');
        PGCharacterData data = character.getData();
        if (data != null) {
            sb.append(data.getLevel()).append('/').append(data.getCurrentExp()).append('/')
                    .append(data.getMaxExp()).append('/').append(data.getAscensionPhase()).append('/')
                    .append(data.getConstellation()).append('/')
                    .append(data.getNormalAttackLevel()).append('/')
                    .append(data.getElementalSkillLevel()).append('/')
                    .append(data.getElementalBurstLevel()).append('/')
                    .append(data.getManualNormalAttack()).append('/')
                    .append(data.getManualElementalSkill()).append('/')
                    .append(data.getManualElementalBurst()).append('/')
                    .append(data.getNormalAttackLevelCap()).append('/')
                    .append(data.getElementalSkillLevelCap()).append('/')
                    .append(data.getElementalBurstLevelCap()).append('/')
                    .append(Math.round(data.getAttributeTotalValue(ModAttributes.MAX_HP.value()))).append('/')
                    .append(Math.round(data.getAttributeTotalValue(ModAttributes.ATK.value()))).append('/')
                    .append(Math.round(data.getAttributeTotalValue(ModAttributes.DEF.value()))).append('/');
            ArtifactInventory inventory = data.getArtifactInventory();
            for (int slot = 0; slot < ArtifactInventory.SLOT_COUNT; slot++) {
                sb.append(stackKey(inventory.getItem(slot))).append('/');
            }
        }
        if (st.backpack != null) {
            appendStacks(sb, st.backpack.getCategoryList(Backpack.Category.DEVELOPMENT));
            appendStacks(sb, st.backpack.getCategoryList(Backpack.Category.WEAPONS));
            appendStacks(sb, st.backpack.getCategoryList(Backpack.Category.ARTIFACTS));
        }
        var playerInventory = st.player.getInventory();
        for (int i = 0; i < INVENTORY_MAIN_SLOTS && i < playerInventory.getContainerSize(); i++) {
            sb.append(stackKey(playerInventory.getItem(i))).append('/');
        }
        return sb.toString();
    }

    private static void appendStacks(StringBuilder sb, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            sb.append(stackKey(stack)).append('/');
        }
    }

    /**
     * 一格物品的指纹：种类 + 数量 + 等级 + 突破 + 经验 + 精炼 + 激活状态。
     *
     * <p>升级只加经验不升级、或者精炼变了的时候，少了后几项就抓不住 —— 那正是
     * 「升级了但面板不动」的来源。
     */
    private static String stackKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "-";
        }
        int level = -1;
        int exp = -1;
        int extra = 0;
        boolean activated = false;
        ArtifactStatsComponent artifact = stack.get(ModDataComponents.ARTIFACT_STATS.get());
        if (artifact != null) {
            level = artifact.level;
            exp = artifact.exp;
            activated = artifact.activated;
        }
        WeaponStatsComponent weapon = stack.get(ModDataComponents.WEAPON_STATS.get());
        if (weapon != null) {
            level = weapon.level * 1000 + weapon.ascended;
            exp = weapon.exp;
            extra = weapon.refinementRank;
        }
        return stack.getItem().getDescriptionId() + '#' + stack.getCount() + '#' + level
                + '#' + exp + '#' + extra + '#' + activated;
    }

    // ====================================================================
    //  页面状态
    // ====================================================================

    private static final class State {
        final Player player;
        /** 每 tick 由 {@link #pullFresh} 重新取一遍（服务端同步会整份换对象）。 */
        PlayerCharactersAttachment attachment;
        @Nullable
        Backpack backpack;

        /** 本地点了写操作、等回包时置位：tick 里见到就先重画一次再清掉。 */
        boolean dirty;

        @Nullable
        PGCharacter character;
        int partyIndex;
        Page page = Page.STATS;
        SubPage subPage = SubPage.NONE;
        int artifactSlot = ArtifactInventory.SLOT_FLOWER;

        @Nullable
        Owned sel;
        int selConstellation = 1;
        int selTalent = TALENT_NORMAL;
        /** 天赋详情卡上那对小页签：0 = 天赋介绍，1 = 详细属性。 */
        int talentTab;

        int levelTarget = TARGET_CHARACTER;
        /** 属性页那个「详细信息」折起来了没有。 */
        boolean showDetails;
        int matSource = -1;
        int matIndex = -1;
        int matCount = 1;
        int matExpValue;

        float[] orbX = ORB_DEFAULT_X.clone();
        float[] orbY = ORB_DEFAULT_Y.clone();
        int orbDragSlot = -1;
        float orbDragStartX;
        float orbDragStartY;
        float orbDragStartNx;
        float orbDragStartNy;
        boolean orbDragMoved;

        @Nullable
        GenshinPreviewPlayer previewAnimatable;
        @Nullable
        String previewAnimationName;

        final List<UIElement> avatarButtons = new ArrayList<>();
        final Map<Page, CustomToggle> tabs = new EnumMap<>(Page.class);
        final UIElement[] orbBySlot = new UIElement[ARTIFACT_TYPES.length];

        @Nullable
        ScrollerView stripScroll;
        @Nullable
        UIElement strip;
        @Nullable
        UIElement elemIcon;
        @Nullable
        Label topName;
        @Nullable
        UIElement menu;
        @Nullable
        UIElement stage;
        @Nullable
        UIElement panel;
        @Nullable
        UIElement detailCard;
        @Nullable
        UIElement main;
        @Nullable
        UIElement bottom;
        @Nullable
        UIElement bottomLeft;
        @Nullable
        UIElement bottomRight;
        @Nullable
        UIElement sub;
        @Nullable
        UIElement orbLayer;
        @Nullable
        Label viewOnlyNote;

        State(Player player, PlayerCharactersAttachment attachment, @Nullable Backpack backpack) {
            this.player = player;
            this.attachment = attachment;
            this.backpack = backpack;
        }
    }
}
