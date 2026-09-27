package com.linweiyun.genshin.client.render.gui.screen;

import com.linweiyun.genshin.core.asset.ItemIcons;
import com.linweiyun.genshin.content.items.artifact.ArtifactItem;
import com.linweiyun.genshin.content.items.artifact.type.ArtifactType;
import com.linweiyun.genshin.content.items.component.ArtifactStatsComponent;
import com.linweiyun.genshin.content.items.component.WeaponStatsComponent;
import com.linweiyun.genshin.content.items.weapon.WeaponItem;
import com.linweiyun.genshin.content.stat.TeyvatItemStat;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.Backpack;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.content.items.artifact.inventory.ArtifactInventory;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.linweiyun.genshin.core.system.registry.register.ModDataComponents;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUIClientAccess;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;

/**
 * 圣遗物选择/管理界面
 *
 * 架构核心：
 * - Screen 本身持有全部状态，不使用静态方法传递状态
 * - ModularUI 在 init() 之前就已完整构建且所有面板均为可见状态
 * - LDLib2 的调试系统（F12）在 init() → setScreenAndInit() 时扫描整个组件树
 * - 所有 UI 容器和内容在注册前就已存在于树中，确保调试系统能完整追踪
 */
public class ScreenArtifactEquip extends Screen {

    private static final Logger LOG = ModLog.getLogger(LogGroup.RENDER);

    final ModularUI modularUI;

    // ======== 常量 ========
    private static final ArtifactType[] TYPES = ArtifactType.values();
    private static final String[] LABEL_KEYS = {
        "gui.minegenshin.artifact_equip.flower",
        "gui.minegenshin.artifact_equip.plume",
        "gui.minegenshin.artifact_equip.sands",
        "gui.minegenshin.artifact_equip.goblet",
        "gui.minegenshin.artifact_equip.circlet",
        "gui.minegenshin.artifact_equip.weapon"
    };
    private static final String BORDER = "minegenshin:gui/selected_border.png";

    public ScreenArtifactEquip(ModularUI modularUI) {
        super(Component.empty());
        this.modularUI = modularUI;
    }

    @Override
    public void init() {
        super.init();
        ModularUIClientAccess.setScreenAndInit(this.modularUI, this);
        this.addRenderableWidget(ModularUIClientAccess.getWidget(modularUI));
        LOG.info("ScreenArtifactEquip init() → LDLib2 UI已注册");
    }

    @Override
    public void removed() {
        super.removed();
        OPEN_STATE = null;
    }

    // ================================================================
    //  外部数据变化 → 立刻重画
    // ================================================================

    /**
     * 当前打开的界面状态。服务端把「已激活的圣遗物」推回来时要用它重画界面 ——
     * 界面不是每刻重建的，只在点击/选择时重建，所以数据变了必须显式刷新，
     * 否则点了「激活」之后面板会一直停在「未激活」，直到玩家再点一次别的才更新。
     */
    private static St OPEN_STATE;

    /**
     * 背包数据被外部改动（例如服务端推回了激活后的圣遗物）后，让当前打开的界面立刻重画。
     *
     * <p>会先从背包**重新读一份**当前选中的那一件 —— 点击时存进 {@code st.selectedEnt}
     * 的是那一刻的旧拷贝，不重新读的话详情面板还是显示未激活。
     */
    public static void refreshIfOpen() {
        St st = OPEN_STATE;      // 界面关闭时 removed() 会把它置空，所以这里不用再查当前 screen
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (st == null || mc.player == null) return;

        Backpack bp = mc.player.getData(AttachmentRegistration.BACKPACK_ATTACHMENT);

        Ent sel = st.selectedEnt;
        if (sel != null && sel.fromBackpack && sel.backpackIdx >= 0) {
            int globalSlot = getCategoryOffset(Backpack.Category.ARTIFACTS) + sel.backpackIdx;
            ItemStack fresh = bp.getItem(globalSlot);
            if (!fresh.isEmpty()) {
                st.selectedEnt = new Ent(fresh.copy(), true, sel.backpackIdx, true, -1, null, -1);
            }
        }

        if (st.lc != null) fillLC(st.lc, bp, st);
        if (st.mp != null && st.rp != null) fillDetail(st.mp, st.rp, bp, st);
    }

    // ================================================================
    //  工厂方法：构建完整 UI（所有内容在 init() 前已就位）
    // ================================================================

    public static ModularUI createModularUI(Player player, int slotIndex) {
        var ss = StylesheetManager.INSTANCE.getStylesheetSafe(
                Identifier.parse("minegenshin:lss/artifact_equip.lss"));

        var ca = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        var cc = ca.getCurrentCharacter();
        var root = new UIElement().setId("root");
        root.layout(l -> { l.widthPercent(100); l.heightPercent(100); });

        if (cc == null || cc.getData() == null) {
            root.addChild(new Label().setText(Component.translatable("gui.minegenshin.artifact_equip.no_character")));
            return ModularUI.of(UI.of(root, ss), player);
        }

        var cd = cc.getData();
        var inv = cd.getArtifactInventory();
        var bp = player.getData(AttachmentRegistration.BACKPACK_ATTACHMENT);

        // ===== 状态对象 =====
        var st = new St(slotIndex,
                slotIndex == 5 ? null :
                        slotIndex >= 0 && slotIndex < 5 ? ArtifactInventory.slotToType(slotIndex) : ArtifactType.FLOWER);
        st.currentCharUUID = cc.getCharacterUUID();
        st.characterTextureId = cc.getTextureId();
        st.attachment = ca;
        st.inv = inv;
        OPEN_STATE = st;        // 服务端推数据回来时要能重画界面，见 refreshIfOpen()
        autoSel(bp, st);

        // ===== 窗口容器 =====
        var win = new UIElement().setId("window");

        // ---------- 顶部 5 槽位 ----------
        var top = new UIElement().setId("top-section");
        var srow = new UIElement().setId("artifact-slots-row");
        for (int i = 0; i < 6; i++) {
            int si = i;
            var se = slotEl(i, inv);
            se.addEventListener(UIEvents.MOUSE_ENTER, e -> {
                se.style(s -> s.overlay(SpriteTexture.of(BORDER)));
                se.transform(t -> t.scale(1.1f));
            });
            se.addEventListener(UIEvents.MOUSE_LEAVE, e -> {
                if (!(st.slot == si && st.selectedEnt == null)) {
                    se.style(s -> s.overlay(null));
                    se.transform(t -> t.scale(1f));
                }
            });
            se.addEventListener(UIEvents.MOUSE_DOWN, e -> {
                se.transform(t -> t.scale(1f));
            });
            se.addEventListener(UIEvents.CLICK, e -> clickSlot(si, bp, st));
            srow.addChild(se);
        }
        top.addChild(srow);

        // ---------- 详情区域 ----------
        var det = new UIElement().setId("detail-section");
        var lp = new UIElement().setId("left-panel");
        var tc = new UIElement().setId("type-toggle-container");
        var src = new UIElement().setId("list-scroller-container");

        var sv = new ScrollerView();
        sv.setId("artifact-list-scroller-view");
        var lc = new UIElement().setId("artifact-list-container");
        sv.addScrollViewChild(lc);
        src.addChild(sv);

        lp.addChildren(tc, src);
        var mp = new UIElement().setId("middle-panel");
        var rp = new UIElement().setId("right-panel");
        det.addChildren(lp, mp, rp);

        win.addChildren(top, det);
        root.addChild(win);

        // ===== 把关键引用存到 St =====
        st.top = top;
        st.det = det;
        st.lc = lc;
        st.mp = mp;
        st.rp = rp;

        // ===== 填充初始内容 =====
        fillTC(tc, bp, st);
        fillLC(lc, bp, st);
        fillDetail(mp, rp, bp, st);

        // ===== 内容默认隐藏 =====
        det.setDisplay(false);
        if (slotIndex >= 0 && slotIndex < 6) {
            top.setDisplay(false);
            det.setDisplay(true);
        }

        LOG.info("createModularUI完成，详情页内容已填充但默认隐藏");
        return ModularUI.of(UI.of(root, ss), player);
    }

    // ================================================================
    //  点击顶部槽位
    // ================================================================

    private static void clickSlot(int si, Backpack bp, St st) {
        LOG.info("点击槽位[{}]", si);
        st.slot = si;
        st.type = si == 5 ? null : ArtifactInventory.slotToType(si);
        st.sel = null;
        st.top.setDisplay(false);
        st.det.setDisplay(true);
        autoSel(bp, st);

        var tc = byId(st.det, "type-toggle-container");
        if (tc != null) fillTC(tc, bp, st);
        if (st.lc != null) fillLC(st.lc, bp, st);
        if (st.mp != null && st.rp != null) fillDetail(st.mp, st.rp, bp, st);
    }

    // ================================================================
    //  辅助：构建单个槽位元素
    // ================================================================

    private static UIElement slotEl(int idx, ArtifactInventory inv) {
        var s = inv.getItem(idx);
        var e = new UIElement().addClass("equipped-slot");
        if (!s.isEmpty()) e.style(x -> x.background(SpriteTexture.of(tex(s))));
        else e.addClass("empty-slot");
        return e;
    }

    // ================================================================
    //  自动选中第一个物品
    //  优先级：当前角色已穿戴 → 其他角色已穿戴 → 背包已激活 → 背包未激活
    // ================================================================

    private static void autoSel(Backpack bp, St st) {
        int si = st.slot;
        if (si < 0 || si >= 6) return;

        // weapon mode
        if (st.type == null) {
            autoSelWeapon(bp, st);
            return;
        }

        // 1. 当前角色已穿戴
        var currentChar = st.attachment.getCharacterByUUID(st.currentCharUUID);
        if (currentChar != null && currentChar.getData() != null) {
            var currentInv = currentChar.getData().getArtifactInventory();
            var eq = currentInv.getItem(si);
            if (!eq.isEmpty()) {
                st.selectedEnt = new Ent(eq.copy(), false, -1, true,
                        st.currentCharUUID, st.characterTextureId, si);
                return;
            }
        }

        // 2. 其他角色已穿戴
        for (var ch : st.attachment.getOwnedCharacters()) {
            if (ch == null || ch.getData() == null) continue;
            if (ch.getCharacterUUID() == st.currentCharUUID) continue;
            var chInv = ch.getData().getArtifactInventory();
            var chEq = chInv.getItem(si);
            if (chEq.isEmpty()) continue;
            if (!(chEq.getItem() instanceof ArtifactItem ai)) continue;
            if (ai.getType() != st.type) continue;
            st.selectedEnt = new Ent(chEq.copy(), false, -1, true,
                    ch.getCharacterUUID(), ch.getTextureId(), si);
            return;
        }

        // 3. 背包已激活
        var arr = bp.getCategoryList(Backpack.Category.ARTIFACTS);
        for (int i = 0; i < arr.size(); i++) {
            var s = arr.get(i);
            if (s.isEmpty()) continue;
            if (!(s.getItem() instanceof ArtifactItem ai)) continue;
            if (ai.getType() != st.type) continue;
            var stats = s.getOrDefault(ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT);
            if (stats.activated) {
                st.selectedEnt = new Ent(s.copy(), true, i, true, -1, null, -1);
                return;
            }
        }

        // 4. 背包未激活
        for (int i = 0; i < arr.size(); i++) {
            var s = arr.get(i);
            if (s.isEmpty()) continue;
            if (!(s.getItem() instanceof ArtifactItem ai)) continue;
            if (ai.getType() != st.type) continue;
            st.selectedEnt = new Ent(s.copy(), true, i, false, -1, null, -1);
            return;
        }

        st.selectedEnt = null;
    }

    private static void autoSelWeapon(Backpack bp, St st) {
        int si = ArtifactInventory.SLOT_WEAPON;
        Class<? extends WeaponItem> allowedClass = getAllowedWeaponClass(st);

        // 1. 当前角色已穿戴
        var currentChar = st.attachment.getCharacterByUUID(st.currentCharUUID);
        if (currentChar != null && currentChar.getData() != null) {
            var eq = currentChar.getData().getWeapon();
            if (!eq.isEmpty()) {
                st.selectedEnt = new Ent(eq.copy(), false, -1, true,
                        st.currentCharUUID, st.characterTextureId, si);
                return;
            }
        }

        // 2. 其他角色已穿戴
        for (var ch : st.attachment.getOwnedCharacters()) {
            if (ch == null || ch.getData() == null) continue;
            if (ch.getCharacterUUID() == st.currentCharUUID) continue;
            var chEq = ch.getData().getWeapon();
            if (chEq.isEmpty()) continue;
            if (!isWeaponCompatible(chEq, allowedClass)) continue;
            st.selectedEnt = new Ent(chEq.copy(), false, -1, true,
                    ch.getCharacterUUID(), ch.getTextureId(), si);
            return;
        }

        // 3. 背包
        var arr = bp.getCategoryList(Backpack.Category.WEAPONS);
        for (int i = 0; i < arr.size(); i++) {
            var s = arr.get(i);
            if (s.isEmpty()) continue;
            if (!isWeaponCompatible(s, allowedClass)) continue;
            st.selectedEnt = new Ent(s.copy(), true, i, true, -1, null, -1);
            return;
        }

        st.selectedEnt = null;
    }

    // ================================================================
    //  填充类型切换按钮
    // ================================================================

    private static void fillTC(UIElement c, Backpack bp, St st) {
        c.clearAllChildren();
        for (int i = 0; i < TYPES.length; i++) {
            var t = TYPES[i];
            var b = new Button();
            b.setText(Component.translatable(LABEL_KEYS[i]));
            b.addClass("type-toggle-btn");
            if (t == st.type) b.addClass("type-toggle-active");
            b.setOnClick(e -> {
                st.type = t;
                st.sel = null;
                st.slot = ArtifactInventory.typeToSlot(t);
                autoSel(bp, st);
                fillTC(c, bp, st);
                if (st.lc != null) fillLC(st.lc, bp, st);
                if (st.mp != null && st.rp != null) fillDetail(st.mp, st.rp, bp, st);
            });
            c.addChild(b);
        }
        // 武器按钮
        var wb = new Button();
        wb.setText(Component.translatable(LABEL_KEYS[5]));
        wb.addClass("type-toggle-btn");
        if (st.type == null) wb.addClass("type-toggle-active");
        wb.setOnClick(e -> {
            st.type = null;
            st.sel = null;
            st.slot = ArtifactInventory.SLOT_WEAPON;
            autoSel(bp, st);
            fillTC(c, bp, st);
            if (st.lc != null) fillLC(st.lc, bp, st);
            if (st.mp != null && st.rp != null) fillDetail(st.mp, st.rp, bp, st);
        });
        c.addChild(wb);
        c.markAsInternal();
    }

    // ================================================================
    //  填充武器列表
    // ================================================================

    private static void fillWeaponLC(UIElement c, Backpack bp, St st) {
        c.clearAllChildren();
        st.sel = null;

        int si = ArtifactInventory.SLOT_WEAPON;
        var list = new ArrayList<Ent>();
        Class<? extends WeaponItem> allowedClass = getAllowedWeaponClass(st);

        // 1. 当前角色已穿戴
        var currentChar = st.attachment.getCharacterByUUID(st.currentCharUUID);
        if (currentChar != null && currentChar.getData() != null) {
            var eq = currentChar.getData().getWeapon();
            if (!eq.isEmpty()) {
                list.add(new Ent(eq.copy(), false, -1, true,
                        st.currentCharUUID, st.characterTextureId, si));
            }
        }

        // 2. 其他角色已穿戴
        for (var ch : st.attachment.getOwnedCharacters()) {
            if (ch == null || ch.getData() == null) continue;
            if (ch.getCharacterUUID() == st.currentCharUUID) continue;
            var chEq = ch.getData().getWeapon();
            if (chEq.isEmpty()) continue;
            if (!isWeaponCompatible(chEq, allowedClass)) continue;
            list.add(new Ent(chEq.copy(), false, -1, true,
                    ch.getCharacterUUID(), ch.getTextureId(), si));
        }

        // 3. 背包内武器（仅显示与角色类型匹配的武器）
        var arr = bp.getCategoryList(Backpack.Category.WEAPONS);
        var backpackList = new ArrayList<Ent>();
        for (int i = 0; i < arr.size(); i++) {
            var s = arr.get(i);
            if (s.isEmpty()) continue;
            if (!isWeaponCompatible(s, allowedClass)) continue;
            backpackList.add(new Ent(s.copy(), true, i, true, -1, null, -1));
        }
        backpackList.sort((a, b) -> compareWeapons(a.s, b.s));
        list.addAll(backpackList);

        renderList(c, list, bp, st);
    }

    private static int compareWeapons(ItemStack a, ItemStack b) {
        if (!(a.getItem() instanceof WeaponItem wa)) return 1;
        if (!(b.getItem() instanceof WeaponItem wb)) return -1;
        int c = Integer.compare(wb.getStar(), wa.getStar());
        if (c != 0) return c;
        var sa = a.getOrDefault(ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT);
        var sb = b.getOrDefault(ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT);
        return Integer.compare(sb.level, sa.level);
    }

    // ================================================================
    //  填充圣遗物列表
    //  分组顺序：
    //    1. 当前角色已穿戴（仅 1 个）
    //    2. 其他角色已穿戴（按 ownedCharacters 顺序）
    //    3. 背包未穿戴已激活（按排序规则）
    //    4. 背包未穿戴未激活（按排序规则）
    //  所有已穿戴项右上角叠加佩戴者头像。
    // ================================================================

    private static void fillLC(UIElement c, Backpack bp, St st) {
        c.clearAllChildren();
        st.sel = null;

        if (st.type == null) {
            fillWeaponLC(c, bp, st);
            return;
        }

        int si = ArtifactInventory.typeToSlot(st.type);
        var list = new ArrayList<Ent>();

        // 1. 当前角色已穿戴
        var currentChar = st.attachment.getCharacterByUUID(st.currentCharUUID);
        if (currentChar != null && currentChar.getData() != null) {
            var currentInv = currentChar.getData().getArtifactInventory();
            var currentEq = currentInv.getItem(si);
            if (!currentEq.isEmpty()) {
                list.add(new Ent(currentEq.copy(), false, -1, true,
                        st.currentCharUUID, st.characterTextureId, si));
            }
        }

        // 2. 其他角色已穿戴
        for (var ch : st.attachment.getOwnedCharacters()) {
            if (ch == null || ch.getData() == null) continue;
            if (ch.getCharacterUUID() == st.currentCharUUID) continue;
            var chInv = ch.getData().getArtifactInventory();
            var chEq = chInv.getItem(si);
            if (chEq.isEmpty()) continue;
            if (!(chEq.getItem() instanceof ArtifactItem ai)) continue;
            if (ai.getType() != st.type) continue;
            list.add(new Ent(chEq.copy(), false, -1, true,
                    ch.getCharacterUUID(), ch.getTextureId(), si));
        }

        // 3+4. 背包内未穿戴
        var arr = bp.getCategoryList(Backpack.Category.ARTIFACTS);
        var activatedList = new ArrayList<Ent>();
        var inactivatedList = new ArrayList<Ent>();
        for (int i = 0; i < arr.size(); i++) {
            var s = arr.get(i);
            if (s.isEmpty()) continue;
            if (!(s.getItem() instanceof ArtifactItem ai)) continue;
            if (ai.getType() != st.type) continue;
            var stats = s.getOrDefault(ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT);
            if (stats.activated) activatedList.add(new Ent(s.copy(), true, i, true, -1, null, -1));
            else inactivatedList.add(new Ent(s.copy(), true, i, false, -1, null, -1));
        }
        activatedList.sort((a, b) -> compareEntries(a.s, b.s));
        inactivatedList.sort((a, b) -> compareEntries(a.s, b.s));
        list.addAll(activatedList);
        list.addAll(inactivatedList);

        renderList(c, list, bp, st);
    }

    private static void renderList(UIElement c, ArrayList<Ent> list, Backpack bp, St st) {
        for (int i = 0; i < list.size(); i++) {
            var e = list.get(i);
            var el = new UIElement().addClass("artifact-list-item");
            el.style(x -> x.background(SpriteTexture.of(tex(e.s))));
            if (!e.activated) el.addClass("artifact-list-inactivated");

            // 已穿戴的物品：右上角叠加佩戴者头像
            if (!e.fromBackpack && e.ownerTextureId != null) {
                var avatar = new UIElement().addClass("artifact-avatar-overlay");
                avatar.style(x -> x.background(SpriteTexture.of(
                        "minegenshin:character/"
                                + e.ownerTextureId + "/textures/avatar_hud.png")));
                el.addChild(avatar);
            }

            boolean sel = isSelected(e, st);
            if (sel) {
                el.style(x -> x.overlay(SpriteTexture.of(BORDER)));
                el.transform(t -> t.scale(1.1f));
                st.sel = el;
            }

            el.addEventListener(UIEvents.MOUSE_ENTER, ev -> {
                if (st.sel != el) {
                    el.style(x -> x.overlay(SpriteTexture.of(BORDER)));
                    el.transform(t -> t.scale(1.1f));
                }
            });
            el.addEventListener(UIEvents.MOUSE_LEAVE, ev -> {
                if (st.sel != el) {
                    el.style(x -> x.overlay(null));
                    el.transform(t -> t.scale(1f));
                }
            });
            el.addEventListener(UIEvents.MOUSE_DOWN, ev -> {
                el.transform(t -> t.scale(1f));
            });
            el.addEventListener(UIEvents.CLICK, ev -> {
                var prev = st.sel;
                if (prev != null && prev != el) {
                    prev.style(x -> x.overlay(null));
                    prev.transform(t -> t.scale(1f));
                }
                st.sel = el;
                el.style(x -> x.overlay(SpriteTexture.of(BORDER)));
                el.transform(t -> t.scale(1.1f));
                st.selectedEnt = e;

                if (st.mp != null && st.rp != null) {
                    fillDetail(st.mp, st.rp, bp, st);
                }
            });

            c.addChild(el);
        }
        c.markAsInternal();
    }

    /**
     * 判断条目是否为当前选中项。
     * 背包物品按 backpackIdx 判定；已穿戴按 ownerUUID + ownerSlot 判定。
     */
    private static boolean isSelected(Ent e, St st) {
        if (st.selectedEnt == null) return false;
        if (e.fromBackpack != st.selectedEnt.fromBackpack) return false;
        if (e.fromBackpack) return e.backpackIdx == st.selectedEnt.backpackIdx;
        return e.ownerUUID == st.selectedEnt.ownerUUID
                && e.ownerSlot == st.selectedEnt.ownerSlot;
    }

    /**
     * 单个圣遗物的组内比较器：
     *   星级降序 → 等级降序 → 套装号升序 → 部位序
     */
    private static int compareEntries(ItemStack a, ItemStack b) {
        if (!(a.getItem() instanceof ArtifactItem aa)) return 1;
        if (!(b.getItem() instanceof ArtifactItem bb)) return -1;
        int c = Integer.compare(bb.getStar(), aa.getStar());
        if (c != 0) return c;
        var sa = a.getOrDefault(ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT);
        var sb = b.getOrDefault(ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT);
        c = Integer.compare(sb.level, sa.level);
        if (c != 0) return c;
        int setIda = aa.getSet() != null && aa.getSet().get() != null ? aa.getSet().get().setId() : 999;
        int setIdb = bb.getSet() != null && bb.getSet().get() != null ? bb.getSet().get().setId() : 999;
        c = Integer.compare(setIda, setIdb);
        if (c != 0) return c;
        return Integer.compare(aa.getType().ordinal(), bb.getType().ordinal());
    }

    // ================================================================
    //  填充武器详情面板
    // ================================================================

    private static void fillWeaponDetail(UIElement mp, UIElement rp,
                                         Backpack bp, St st) {
        mp.clearAllChildren();
        rp.clearAllChildren();

        int slot = ArtifactInventory.SLOT_WEAPON;
        var ent = st.selectedEnt;
        if (ent == null || ent.s.isEmpty()) {
            rp.addChild(new Label().setText(Component.translatable("gui.minegenshin.artifact_equip.no_weapon")).setId("info-empty"));
            rp.markAsInternal();
            return;
        }

        ItemStack disp = ent.s;

        var ic = new UIElement().addClass("artifact-detail-icon");
        ic.style(x -> x.background(SpriteTexture.of(tex(disp))));
        ic.markAsInternal();
        mp.addChild(ic);
        mp.markAsInternal();

        if (!(disp.getItem() instanceof WeaponItem wi)) return;
        var stats = disp.getOrDefault(ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT);
        int star = wi.getStar();

        var info = new UIElement().setId("info-container");
        info.addChild(new Label().setText(disp.getHoverName()).addClass("info-name"));
        info.addChild(new Label().setText(Component.literal("★".repeat(star)).withStyle(ChatFormatting.GOLD)).addClass("info-star"));

        if (!ent.fromBackpack && ent.ownerTextureId != null) {
            PGCharacter owner = st.attachment.getCharacterByUUID(ent.ownerUUID);
            String ownerName = owner != null ? owner.getName().getString() : "?";
            ChatFormatting color = (ent.ownerUUID == st.currentCharUUID) ? ChatFormatting.GOLD : ChatFormatting.AQUA;
            info.addChild(new Label().setText(
                    Component.translatable("gui.minegenshin.artifact_equip.equipped_by", ownerName).withStyle(color)).addClass("info-level"));
        }

        info.addChild(new Label().setText(Component.translatable("gui.minegenshin.artifact_equip.level_format", stats.level).withStyle(ChatFormatting.GRAY)).addClass("info-level"));

        if (stats.mainStat != null && stats.mainStat.isInitialized())
            info.addChild(new Label().setText(Component.literal(statTxt(stats.mainStat)).withStyle(ChatFormatting.YELLOW)).addClass("info-main-stat"));
        if (stats.subStat != null && stats.subStat.isInitialized())
            info.addChild(new Label().setText(Component.literal(statTxt(stats.subStat)).withStyle(ChatFormatting.GRAY)).addClass("info-sub-stat"));

        // ============ 按钮 ============
        var bc = new UIElement().setId("button-container");
        var act = new Button();
        act.addClass("action-button");

        if (ent.fromBackpack) {
            var te = disp.copy();
            boolean currentSlotEmpty = currentSlotIsEmpty(st);
            act.setText(Component.translatable(currentSlotEmpty ? "gui.minegenshin.artifact_equip.equip" : "gui.minegenshin.artifact_equip.change"));
            int ii = ent.backpackIdx;
            act.setOnClick(e -> {
                if (!ArtifactInventory.isValidForSlot(slot, te)) return;
                NetworkManager.sendEquipOrSwapArtifactToServer(slot, ii);
                var currentChar = st.attachment.getCharacterByUUID(st.currentCharUUID);
                if (currentChar != null && currentChar.getData() != null) {
                    var curInv = currentChar.getData().getArtifactInventory();
                    var old = curInv.getItem(slot);
                    bp.removeItemFromCategory(Backpack.Category.WEAPONS, ii);
                    curInv.setItem(slot, te.copy());
                    if (!old.isEmpty()) bp.addItemToCategory(Backpack.Category.WEAPONS, old.copy());
                }
                autoSel(bp, st);
                if (st.lc != null) fillWeaponLC(st.lc, bp, st);
                fillWeaponDetail(mp, rp, bp, st);
            });
        } else if (ent.ownerUUID == st.currentCharUUID) {
            act.setText(Component.translatable("gui.minegenshin.artifact_equip.unequip"));
            var tu = disp.copy();
            act.setOnClick(e -> {
                NetworkManager.sendUnequipArtifactToServer(slot);
                var currentChar = st.attachment.getCharacterByUUID(st.currentCharUUID);
                if (currentChar != null && currentChar.getData() != null) {
                    var curInv = currentChar.getData().getArtifactInventory();
                    curInv.setItem(slot, ItemStack.EMPTY);
                }
                bp.addItemToCategory(Backpack.Category.WEAPONS, tu);
                autoSel(bp, st);
                if (st.lc != null) fillWeaponLC(st.lc, bp, st);
                fillWeaponDetail(mp, rp, bp, st);
            });
        } else {
            act.setText(Component.translatable("gui.minegenshin.artifact_equip.swap"));
            int targetUUID = ent.ownerUUID;
            act.setOnClick(e -> {
                var currentChar = st.attachment.getCharacterByUUID(st.currentCharUUID);
                var targetChar = st.attachment.getCharacterByUUID(targetUUID);
                if (currentChar == null || currentChar.getData() == null
                        || targetChar == null || targetChar.getData() == null) return;
                var curInv = currentChar.getData().getArtifactInventory();
                var tgtInv = targetChar.getData().getArtifactInventory();

                ItemStack a = curInv.getItem(slot);
                ItemStack b = tgtInv.getItem(slot);

                if (!ArtifactInventory.isValidForSlot(slot, b)) return;
                if (!ArtifactInventory.isValidForSlot(slot, a)) return;

                curInv.setItem(slot, b.copy());
                tgtInv.setItem(slot, a.copy());

                st.attachment.syncToServer();

                autoSel(bp, st);
                if (st.lc != null) fillWeaponLC(st.lc, bp, st);
                fillWeaponDetail(mp, rp, bp, st);
            });
        }

        bc.addChild(act);
        info.addChild(bc);
        rp.addChild(info);
        rp.markAsInternal();
    }

    // ================================================================
    //  填充详情面板
    // ================================================================

    private static void fillDetail(UIElement mp, UIElement rp,
                                   Backpack bp, St st) {
        mp.clearAllChildren();
        rp.clearAllChildren();

        int slot = st.slot;
        if (slot < 0 || slot >= 6) return;

        if (st.type == null) {
            fillWeaponDetail(mp, rp, bp, st);
            return;
        }

        var ent = st.selectedEnt;
        if (ent == null || ent.s.isEmpty()) {
            rp.addChild(new Label().setText(Component.translatable("gui.minegenshin.artifact_equip.no_artifact")).setId("info-empty"));
            rp.markAsInternal();
            return;
        }

        ItemStack disp = ent.s;

        // 大图标
        var ic = new UIElement().addClass("artifact-detail-icon");
        ic.style(x -> x.background(SpriteTexture.of(tex(disp))));
        ic.markAsInternal();
        mp.addChild(ic);
        mp.markAsInternal();

        if (!(disp.getItem() instanceof ArtifactItem ai)) return;
        var stats = disp.getOrDefault(ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT);
        int star = ai.getStar();
        boolean activated = stats.activated;

        var info = new UIElement().setId("info-container");
        info.addChild(new Label().setText(disp.getHoverName()).addClass("info-name"));
        info.addChild(new Label().setText(Component.literal("★".repeat(star)).withStyle(ChatFormatting.GOLD)).addClass("info-star"));

        if (!activated) {
            info.addChild(new Label().setText(
                    Component.translatable("gui.minegenshin.artifact_equip.not_activated")
                            .withStyle(ChatFormatting.RED)).addClass("info-inactivated"));
        }

        if (!ent.fromBackpack && ent.ownerTextureId != null) {
            PGCharacter owner = st.attachment.getCharacterByUUID(ent.ownerUUID);
            String ownerName = owner != null ? owner.getName().getString() : "?";
            ChatFormatting color = (ent.ownerUUID == st.currentCharUUID) ? ChatFormatting.GOLD : ChatFormatting.AQUA;
            info.addChild(new Label().setText(
                    Component.translatable("gui.minegenshin.artifact_equip.equipped_by", ownerName).withStyle(color)).addClass("info-level"));
        }

        info.addChild(new Label().setText(Component.translatable("gui.minegenshin.artifact_equip.level_format", stats.level).withStyle(ChatFormatting.GRAY)).addClass("info-level"));
        long ex = stats.getExpToNextLevel(star);
        info.addChild(new Label().setText(
                Component.translatable(ex > 0 ? "gui.minegenshin.artifact_equip.exp_format" : "gui.minegenshin.artifact_equip.exp_max", stats.exp, ex).withStyle(ChatFormatting.GRAY)).addClass("info-exp"));

        if (stats.mainStat != null && stats.mainStat.isInitialized())
            info.addChild(new Label().setText(Component.literal(statTxt(stats.mainStat)).withStyle(ChatFormatting.YELLOW)).addClass("info-main-stat"));
        if (stats.subStats != null) for (var ss : stats.subStats) {
            if (!ss.isInitialized()) continue;
            info.addChild(new Label().setText(Component.literal(statTxt(ss))
                    .withStyle(ss.isUnlocked()?ChatFormatting.GRAY:ChatFormatting.DARK_GRAY)).addClass("info-sub-stat"));
        }

        // ============ 按钮 ============
        var bc = new UIElement().setId("button-container");
        var act = new Button();
        act.addClass("action-button");

        if (ent.fromBackpack) {
            // ===== 来自背包 =====
            if (!activated) {
                // 未激活 → 按钮变为"激活"
                act.setText(Component.translatable("gui.minegenshin.artifact_equip.activate"));
                int ii = ent.backpackIdx;
                act.setOnClick(e -> {
                    NetworkManager.sendActivateArtifactToServer(ii);
                    // ⚠️ 这里**不要**再调 ArtifactItem.initializeArtifactStackIfNeeded 本地补一份：
                    // 它内部是 `new Random()`，客户端的随机数和服务端那次不是同一份，
                    // 本地会存下一套不同的词条，等被写回服务端时玩家就看到「卸载一次词条变了」。
                    // 服务端抽完会把背包同步过来，UI 下一次刷新就是权威数据。
                    int globalSlot = getCategoryOffset(Backpack.Category.ARTIFACTS) + ii;
                    ItemStack local = bp.getItem(globalSlot);
                    if (!local.isEmpty() && local.getItem() instanceof ArtifactItem) {
                        // 保持选中：直接更新 selectedEnt 指向激活后的物品，
                        // 不调用 autoSel（autoSel 会把选中跳到优先级最高的物品）。
                        // 背包索引不变，因此 isSelected 依然能匹配到这个条目。
                        st.selectedEnt = new Ent(local.copy(), true, ii, true, -1, null, -1);
                    }
                    if (st.lc != null) fillLC(st.lc, bp, st);
                    fillDetail(mp, rp, bp, st);
                });
            } else {
                // 已激活 → 穿戴 / 更换
                boolean currentSlotEmpty = currentSlotIsEmpty(st);
                act.setText(Component.translatable(currentSlotEmpty ? "gui.minegenshin.artifact_equip.equip" : "gui.minegenshin.artifact_equip.change"));
                int ii = ent.backpackIdx;
                var te = disp.copy();
                act.setOnClick(e -> {
                    if (!ArtifactInventory.isValidForSlot(slot, te)) return;
                    NetworkManager.sendEquipOrSwapArtifactToServer(slot, ii);
                    var currentChar = st.attachment.getCharacterByUUID(st.currentCharUUID);
                    if (currentChar != null && currentChar.getData() != null) {
                        var curInv = currentChar.getData().getArtifactInventory();
                        var old = curInv.getItem(slot);
                        bp.removeItemFromCategory(Backpack.Category.ARTIFACTS, ii);
                        curInv.setItem(slot, te.copy());
                        if (!old.isEmpty()) bp.addItemToCategory(Backpack.Category.ARTIFACTS, old.copy());
                    }
                    autoSel(bp, st);
                    if (st.lc != null) fillLC(st.lc, bp, st);
                    fillDetail(mp, rp, bp, st);
                });
            }
        } else if (ent.ownerUUID == st.currentCharUUID) {
            // ===== 当前角色已穿戴 → 卸下 =====
            act.setText(Component.translatable("gui.minegenshin.artifact_equip.unequip"));
            var tu = disp.copy();
            act.setOnClick(e -> {
                NetworkManager.sendUnequipArtifactToServer(slot);
                var currentChar = st.attachment.getCharacterByUUID(st.currentCharUUID);
                if (currentChar != null && currentChar.getData() != null) {
                    var curInv = currentChar.getData().getArtifactInventory();
                    curInv.setItem(slot, ItemStack.EMPTY);
                }
                bp.addItemToCategory(Backpack.Category.ARTIFACTS, tu);
                autoSel(bp, st);
                if (st.lc != null) fillLC(st.lc, bp, st);
                fillDetail(mp, rp, bp, st);
            });
        } else {
            // ===== 其他角色已穿戴 → 交换 =====
            act.setText(Component.translatable("gui.minegenshin.artifact_equip.swap"));
            int targetUUID = ent.ownerUUID;
            act.setOnClick(e -> {
                var currentChar = st.attachment.getCharacterByUUID(st.currentCharUUID);
                var targetChar = st.attachment.getCharacterByUUID(targetUUID);
                if (currentChar == null || currentChar.getData() == null
                        || targetChar == null || targetChar.getData() == null) return;
                var curInv = currentChar.getData().getArtifactInventory();
                var tgtInv = targetChar.getData().getArtifactInventory();

                ItemStack a = curInv.getItem(slot);
                ItemStack b = tgtInv.getItem(slot);

                if (!ArtifactInventory.isValidForSlot(slot, b)) return;
                if (!ArtifactInventory.isValidForSlot(slot, a)) return;

                curInv.setItem(slot, b.copy());
                tgtInv.setItem(slot, a.copy());

                currentChar.recalculateDirtyArtifactSlots();
                targetChar.recalculateDirtyArtifactSlots();

                st.attachment.syncToServer();

                autoSel(bp, st);
                if (st.lc != null) fillLC(st.lc, bp, st);
                fillDetail(mp, rp, bp, st);
            });
        }

        var up = new Button();
        up.setText(Component.translatable("gui.minegenshin.artifact_equip.upgrade"));
        up.addClass("upgrade-button");
        up.setOnClick(e -> NetworkManager.sendArtifactLevelUpToServer(disp, 10000));
        if (!activated || stats.level >= stats.getMaxLevel(star)) up.setDisplay(false);

        bc.addChildren(act, up);
        info.addChild(bc);
        rp.addChild(info);
        rp.markAsInternal();
    }

    // ================================================================
    //  工具方法
    // ================================================================

    private static UIElement byId(UIElement p, String id) {
        if (id.equals(p.getId())) return p;
        for (var c : p.getChildren()) { var r = byId(c, id); if (r != null) return r; }
        return null;
    }

    private static int getCategoryOffset(Backpack.Category target) {
        int offset = 0;
        for (var category : Backpack.Category.values()) {
            if (category == target) break;
            offset += category.maxCapacity;
        }
        return offset;
    }

    /** 当前角色的 slot 槽位是否为空 */
    private static boolean currentSlotIsEmpty(St st) {
        var currentChar = st.attachment.getCharacterByUUID(st.currentCharUUID);
        if (currentChar == null || currentChar.getData() == null) return true;
        return currentChar.getData().getArtifactInventory().getItem(st.slot).isEmpty();
    }
    /**
     * GUI 里这件物品画哪张图。
     *
     * <p>走 {@link ItemIcons}：优先物品自己的图标
     * {@code minegenshin:item/<物品名>/icon.png}，没有才退回物品自己的贴图 ——
     * geo 物品的贴图是 3D 模型的 UV 图集，直接当 2D 精灵画会是一坨错位色块。
     */
    private static String tex(ItemStack s) {
        return ItemIcons.pathOf(s);
    }

    private static String statTxt(TeyvatItemStat s) {
        if (!s.isInitialized()) return "";
        var n = Component.translatable(s.getAttribute().translationKey()).getString();
        return s.getKind()==TeyvatItemStat.StatKind.PERCENT
                ? n+" +"+String.format("%.1f%%",s.getValue()*100)
                : n+" +"+String.format("%.0f",s.getValue());
    }

    // ================================================================
    //  内部数据类
    // ================================================================

    /**
     * 列表条目。
     *
     * s              物品堆
     * fromBackpack   true 表示来自背包，false 表示来自某个角色的圣遗物栏
     * backpackIdx    背包索引（仅 fromBackpack 时有效）
     * activated      是否已激活
     * ownerUUID      佩戴者角色 UUID（仅 !fromBackpack 时有效）
     * ownerTextureId 佩戴者贴图 ID（仅 !fromBackpack 时有效）
     * ownerSlot      佩戴者身上的部位索引（仅 !fromBackpack 时有效）
     */
    private record Ent(ItemStack s, boolean fromBackpack, int backpackIdx, boolean activated,
                       int ownerUUID, String ownerTextureId, int ownerSlot) {}

    private static final class St {
        int slot;
        ArtifactType type; // null = weapon mode
        Ent selectedEnt;
        UIElement sel;
        UIElement top;
        UIElement det;
        UIElement lc;
        UIElement mp;
        UIElement rp;
        String characterTextureId;
        int currentCharUUID;
        PlayerCharactersAttachment attachment;
        ArtifactInventory inv;
        St(int slot, ArtifactType type) {
            this.slot = slot;
            this.type = type;
        }
    }

    private static Class<? extends WeaponItem> getAllowedWeaponClass(St st) {
        var currentChar = st.attachment.getCharacterByUUID(st.currentCharUUID);
        if (currentChar != null) {
            return currentChar.getAllowedWeaponClass();
        }
        return WeaponItem.class;
    }

    private static boolean isWeaponCompatible(ItemStack stack, Class<? extends WeaponItem> allowedClass) {
        if (stack.isEmpty()) return false;
        return allowedClass.isInstance(stack.getItem());
    }
}
