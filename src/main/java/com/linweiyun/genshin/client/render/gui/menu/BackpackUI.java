package com.linweiyun.genshin.client.render.gui.menu;

import com.linweiyun.genshin.core.asset.ItemIcons;
import com.linweiyun.genshin.content.items.artifact.ArtifactItem;
import com.linweiyun.genshin.content.items.artifact.ArtifactSet;
import com.linweiyun.genshin.content.items.artifact.inventory.ArtifactInventory;
import com.linweiyun.genshin.content.items.component.ArtifactStatsComponent;
import com.linweiyun.genshin.content.items.component.WeaponStatsComponent;
import com.linweiyun.genshin.content.items.weapon.WeaponItem;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.Backpack;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.linweiyun.genshin.core.system.registry.register.ModDataComponents;
import com.linweiyun.genshin.client.render.gui.component.CustomToggle;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class BackpackUI {
    private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);
    private static final int SLOTS_PER_ROW = 10;

    public static ModularUI createUI(Player player, Backpack backpack) {
        var root = new UIElement().setId("root");
        var stylesheet = StylesheetManager.INSTANCE.getStylesheetSafe(
                Identifier.parse("minegenshin:lss/backpack.lss"));

        // ========== Window ==========
        var window = new UIElement().setId("window");

        // ========== Panel ==========
        var sidebarPanel = new UIElement().setId("sidebar-panel");
        var contentPanel = new UIElement().setId("content-panel");

        // ========== Section（分类切换区） ==========
        var categorySection = new UIElement().setId("category-section");
        var categoryGroup = new ToggleGroupElement();
        for (var category : Backpack.Category.values()) {
            String toggleId = category.name().toLowerCase() + "-toggle";
            var toggle = new CustomToggle();
            toggle.setId(toggleId).addClass("category-toggle");
            toggle.setButtonText(I18n.get("gui.minegenshin.backpack.category." + category.name().toLowerCase()));
            categoryGroup.addChild(toggle);
        }
        UIElement firstChild = categoryGroup.getChildren().get(0);
        if (firstChild instanceof Toggle firstToggle) {
            firstToggle.setOn(true);
        }
        var backpackContainer = new UIElement().setId("backpack_container");

        AtomicReference<Backpack.Category> currentCategoryRef =
                new AtomicReference<>(Backpack.Category.WEAPONS);
        categoryGroup.setId("category_group");
        categoryGroup.toggleGroup.setAllowEmpty(false);

        // ========== 排序状态（圣遗物排序） ==========
        AtomicReference<ArtifactSortMethod> sortMethodRef = new AtomicReference<>(ArtifactSortMethod.STAR);

        // ========== 选中状态（用稳定 key 追踪，兼容已装备条目） ==========
        AtomicReference<String> selectedKeyRef = new AtomicReference<>(null);

        // ========== 模式容器 ==========
        var iconModeSection = new UIElement().setId("icon-mode-section");
        var iconGenshinBackpackWindow = new ScrollerView();
        iconGenshinBackpackWindow.setId("icon-genshin-backpack-window");
        var iconPlayerInventoryWindow = new UIElement().setId("icon-player-inventory-window");

        var detailPanel = new UIElement().setId("detail-panel-container");

        AtomicReference<UIElement> iconGrid = new AtomicReference<>(null);
        UIElement initialGrid = refreshIconList(backpack, Backpack.Category.WEAPONS, detailPanel,
                player, iconGenshinBackpackWindow, iconGrid, currentCategoryRef, selectedKeyRef,
                sortMethodRef.get());
        iconGrid.set(initialGrid);
        iconGrid.get().setId("icon-grid-container");

        var slotModeSection = new UIElement().setId("slot-mode-section");
        slotModeSection.setDisplay(false);

        // 当前模式是否槽位模式
        AtomicBoolean isSlotMode = new AtomicBoolean(false);

        // ========== 玩家背包 ==========
        var playerInventoryWindow = new UIElement().setId("player_inventory_window");

        // ========== 槽位网格 ==========
        ResourceHandler<ItemResource> handler = backpack.asResourceHandler();
        var genshinBackpackWindow = new ScrollerView();
        genshinBackpackWindow.setId("genshin_backpack_window");

        // 全额渲染 9 个分类各自的所有槽位，非当前分类通过 setDisplay(false) 隐藏。
        Runnable rebuildSlotGrids = () -> {
            genshinBackpackWindow.clearAllScrollViewChildren();
            Backpack.Category currentCat = currentCategoryRef.get();
            for (var cat : Backpack.Category.values()) {
                var grid = buildGridForCategory(cat, handler, backpack, player, sortMethodRef.get());
                grid.setId(cat.name().toLowerCase() + "_grid");
                grid.setDisplay(cat == currentCat);
                genshinBackpackWindow.addScrollViewChild(grid);
            }
        };

        rebuildSlotGrids.run();

        // ========== 排序选择器（仅圣遗物分类显示） ==========
        var sortSelector = new Selector<ArtifactSortMethod>();
        sortSelector.setId("artifact-sort-selector");
        sortSelector.setSelected(ArtifactSortMethod.STAR, false);
        sortSelector.setCandidates(List.of(ArtifactSortMethod.values()));
        sortSelector.setOnValueChanged(method -> {
            sortMethodRef.set(method);
            if (isSlotMode.get()) {
                rebuildSlotGrids.run();
            } else {
                iconGenshinBackpackWindow.clearAllScrollViewChildren();
                iconGrid.set(refreshIconList(backpack, currentCategoryRef.get(), detailPanel,
                        player, iconGenshinBackpackWindow, iconGrid, currentCategoryRef,
                        selectedKeyRef, method));
                iconGenshinBackpackWindow.addScrollViewChild(iconGrid.get());
            }
        });
        sortSelector.setDisplay(false);

        // ========== “整理”按钮（仅圣遗物 + 槽位模式显示） ==========
        var organizeBtn = new Button();
        organizeBtn.setId("artifact-organize-button");
        organizeBtn.setText(Component.translatable("gui.minegenshin.backpack.organize"));
        organizeBtn.addClass("item-action-btn");
        organizeBtn.setDisplay(false);
        organizeBtn.setOnClick(e -> {
            Backpack.Category cat = currentCategoryRef.get();
            if (cat != Backpack.Category.ARTIFACTS) return;
            organizeBackpack(backpack, cat, sortMethodRef.get());
            rebuildSlotGrids.run();
        });

        // 圣遗物专属控件容器（排序 + 整理）
        var artifactControls = new UIElement().setId("artifact-controls");
        artifactControls.addChildren(sortSelector, organizeBtn);
        artifactControls.setDisplay(false);

        var modeSwitch = new Switch();
        modeSwitch.setId("mode-switch");
        modeSwitch.setOnSwitchChanged(isOn -> {
            isSlotMode.set(isOn);
            slotModeSection.setDisplay(isOn);
            iconModeSection.setDisplay(!isOn);
            organizeBtn.setDisplay(isOn && currentCategoryRef.get() == Backpack.Category.ARTIFACTS);
            if (!isOn) {
                iconGenshinBackpackWindow.clearAllScrollViewChildren();
                iconGrid.set(refreshIconList(backpack, currentCategoryRef.get(), detailPanel,
                        player, iconGenshinBackpackWindow, iconGrid, currentCategoryRef,
                        selectedKeyRef, sortMethodRef.get()));
                iconGenshinBackpackWindow.addScrollViewChild(iconGrid.get());
            }
        });

        // 全额渲染，槽位网格不需要动态扩展；
        // 图标模式的重排由“切分类 / 切模式 / 整理”手动触发。
        backpack.setOnChange(() -> { });

        // 监听分类切换
        Map<String, Backpack.Category> toggleCategoryMap = new HashMap<>();
        for (var category : Backpack.Category.values()) {
            toggleCategoryMap.put(category.name().toLowerCase() + "-toggle", category);
        }

        for (var child : categoryGroup.getChildren()) {
            if (child instanceof Toggle toggle) {
                toggle.setOnToggleChanged(isOn -> {
                    if (!isOn) return;
                    Toggle current = categoryGroup.toggleGroup.getCurrentToggle();
                    if (current == null) return;
                    Backpack.Category cat = toggleCategoryMap.get(current.getId());
                    currentCategoryRef.set(cat);

                    // 切分类时清空选中，让 refreshIconList 自动选中第一项
                    selectedKeyRef.set(null);

                    // 圣遗物控件仅在圣遗物分类时显示
                    boolean isArtifact = cat == Backpack.Category.ARTIFACTS;
                    artifactControls.setDisplay(isArtifact);
                    sortSelector.setDisplay(isArtifact);
                    organizeBtn.setDisplay(isArtifact && isSlotMode.get());

                    // 切换槽位网格显示
                    for (var grid : genshinBackpackWindow.viewContainer.getChildren()) {
                        grid.setDisplay(grid.getId().equals(cat.name().toLowerCase() + "_grid"));
                    }

                    // 刷新图标模式（自动选中第一个物品）
                    iconGenshinBackpackWindow.clearAllScrollViewChildren();
                    iconGrid.set(refreshIconList(backpack, cat, detailPanel,
                            player, iconGenshinBackpackWindow, iconGrid, currentCategoryRef,
                            selectedKeyRef, sortMethodRef.get()));
                    iconGenshinBackpackWindow.addScrollViewChild(iconGrid.get());
                });
            }
        }

        root.layout(layout -> {
            layout.widthPercent(100f);
            layout.heightPercent(100f);
        });

        sidebarPanel.layout(layout -> layout.flexDirection(FlexDirection.COLUMN));

        // ========== 组装 UI 树 ==========
        root.addChildren(
                window.addChildren(
                        sidebarPanel.addChildren(
                                categorySection.addChildren(categoryGroup)
                        ),
                        contentPanel.addChildren(
                                backpackContainer.addChildren(
                                        iconModeSection.addChildren(
                                                iconGenshinBackpackWindow.addScrollViewChild(iconGrid.get()),
                                                iconPlayerInventoryWindow.addChildren(detailPanel)
                                        ),
                                        slotModeSection.addChildren(
                                                genshinBackpackWindow,
                                                playerInventoryWindow.addChildren(new InventorySlots())
                                        ),
                                        modeSwitch
                                )
                        ),
                        artifactControls
                )
        );

        var ui = UI.of(root, stylesheet);
        return ModularUI.of(ui, player);
    }

    // ========================================================================
    //  辅助方法
    // ========================================================================

    private static int getCategoryOffset(Backpack.Category target) {
        int offset = 0;
        for (var category : Backpack.Category.values()) {
            if (category == target) break;
            offset += category.maxCapacity;
        }
        return offset;
    }

    /**
     * 完整版：非圣遗物返回占位条目，其它信息原样保留。
     */
    private static ArtifactSortMethod.Entry toEntry(
            ItemStack stack, int globalSlot, boolean equipped,
            ResourceHandler<ItemResource> handler, int containerSlot,
            String key, String equippedByCharacterName, String equippedByCharacterTextureId) {

        if (stack.getItem() instanceof WeaponItem weapon) {
            WeaponStatsComponent stats = stack.getOrDefault(
                    ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT);
            return new ArtifactSortMethod.Entry(
                    stack, globalSlot, equipped, true,
                    weapon.getStar(), stats.level, 0, 0,
                    handler, containerSlot, key,
                    equippedByCharacterName, equippedByCharacterTextureId);
        }

        if (!(stack.getItem() instanceof ArtifactItem artifact)) {
            return new ArtifactSortMethod.Entry(
                    stack, globalSlot, equipped, false,
                    0, 0, 0, 0,
                    handler, containerSlot, key,
                    equippedByCharacterName, equippedByCharacterTextureId);
        }
        ArtifactStatsComponent stats = stack.getOrDefault(
                ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT);
        int setId = 999;
        DeferredHolder<ArtifactSet, ArtifactSet> setHolder = artifact.getSet();
        if (setHolder != null && setHolder.get() != null) {
            setId = setHolder.get().setId();
        }
        int typeOrdinal = artifact.getType() != null ? artifact.getType().ordinal() : 99;
        return new ArtifactSortMethod.Entry(
                stack, globalSlot, equipped, stats.activated,
                artifact.getStar(), stats.level, setId, typeOrdinal,
                handler, containerSlot, key,
                equippedByCharacterName, equippedByCharacterTextureId);
    }

    /**
     * 简化版：不需要角色信息时使用（背包内物品 / 整理逻辑）。
     */
    private static ArtifactSortMethod.Entry toEntry(
            ItemStack stack, int globalSlot, boolean equipped,
            ResourceHandler<ItemResource> handler, int containerSlot,
            String key) {
        return toEntry(stack, globalSlot, equipped, handler, containerSlot, key, null, null);
    }

    /**
     * 收集某分类的所有物品（背包 + 已穿戴圣遗物），并按所选排序方式排序。
     * 排序分三组：
     *   组 1 — 已穿戴
     *   组 2 — 未穿戴但已激活
     *   组 3 — 未穿戴且未激活
     * 组内按所选排序方式排序。
     */
    private static List<ArtifactSortMethod.Entry> collectSorted(
            Backpack backpack, Backpack.Category category,
            Player player, ArtifactSortMethod method) {

        List<ArtifactSortMethod.Entry> list = new ArrayList<>();
        int offset = getCategoryOffset(category);
        int totalSlots = category.maxCapacity;

        // 缓存 backpack 的 handler，让每个背包槽位都能绑定到正确的容器
        ResourceHandler<ItemResource> backpackHandler = backpack.asResourceHandler();

        // 1. 收集背包内物品
        for (int i = 0; i < totalSlots; i++) {
            ItemStack stack = backpack.getItem(offset + i);
            if (stack.isEmpty()) continue;
            int globalSlot = offset + i;
            String key = "b:" + globalSlot;
            list.add(toEntry(stack, globalSlot, false, backpackHandler, globalSlot, key));
        }

        // 2. 收集角色穿戴的物品
        if (category == Backpack.Category.ARTIFACTS || category == Backpack.Category.WEAPONS) {
            try {
                var attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
                if (attachment != null) {
                    for (PGCharacter character : attachment.getOwnedCharacters()) {
                        if (character == null || character.getData() == null) continue;
                        ArtifactInventory inv = character.getData().getArtifactInventory();
                        if (inv == null) continue;
                        ResourceHandler<ItemResource> lockedHandler =
                                new LockedResourceHandler(inv.asResourceHandler());
                        int charUUID = character.getCharacterUUID();
                        String charName = character.getName().getString();
                        String charTextureId = character.getTextureId();
                        if (category == Backpack.Category.ARTIFACTS) {
                            for (int i = 0; i < 5; i++) {
                                ItemStack stack = inv.getItem(i);
                                if (stack.isEmpty()) continue;
                                String key = "e:" + charUUID + ":" + i;
                                list.add(toEntry(stack, -1, true, lockedHandler, i, key,
                                        charName, charTextureId));
                            }
                        } else {
                            ItemStack stack = inv.getItem(ArtifactInventory.SLOT_WEAPON);
                            if (!stack.isEmpty()) {
                                String key = "e:" + charUUID + ":w";
                                list.add(toEntry(stack, -1, true, lockedHandler,
                                        ArtifactInventory.SLOT_WEAPON, key,
                                        charName, charTextureId));
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                LOGGER.warn("收集已装备物品失败: {}", ex.getMessage());
            }
        }

        // 3. 排序
        if (category == Backpack.Category.ARTIFACTS) {
            Comparator<ArtifactSortMethod.Entry> cmp = Comparator
                    .comparingInt((ArtifactSortMethod.Entry e) ->
                            e.equipped() ? 0 : (e.activated() ? 1 : 2))
                    .thenComparing(method.comparator());
            list.sort(cmp);
        } else if (category == Backpack.Category.WEAPONS) {
            Comparator<ArtifactSortMethod.Entry> cmp = Comparator
                    .comparingInt((ArtifactSortMethod.Entry e) ->
                            e.equipped() ? 0 : 1)
                    .thenComparing(method.comparator());
            list.sort(cmp);
        }
        return list;
    }

    /**
     * 立即对指定分类的背包数据按排序规则进行整理（重排背包内物品位置）。
     * 只影响背包内的物品（不涉及角色穿戴的圣遗物）。
     *
     * 圣遗物分类下：已激活的排在前面，未激活的排在后面；组内按排序规则。
     */
    private static void organizeBackpack(Backpack backpack, Backpack.Category category,
                                         ArtifactSortMethod method) {
        int offset = getCategoryOffset(category);
        int totalSlots = category.maxCapacity;

        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < totalSlots; i++) {
            ItemStack stack = backpack.getItem(offset + i);
            if (!stack.isEmpty()) stacks.add(stack);
        }

        if (category == Backpack.Category.ARTIFACTS) {
            ResourceHandler<ItemResource> h = backpack.asResourceHandler();
            stacks.sort((a, b) -> {
                ArtifactSortMethod.Entry ea = toEntry(a, 0, false, h, 0, "");
                ArtifactSortMethod.Entry eb = toEntry(b, 0, false, h, 0, "");
                int ga = ea.activated() ? 0 : 1;
                int gb = eb.activated() ? 0 : 1;
                if (ga != gb) return Integer.compare(ga, gb);
                return method.comparator().compare(ea, eb);
            });
        } else if (category == Backpack.Category.WEAPONS) {
            ResourceHandler<ItemResource> h = backpack.asResourceHandler();
            stacks.sort((a, b) -> {
                ArtifactSortMethod.Entry ea = toEntry(a, 0, false, h, 0, "");
                ArtifactSortMethod.Entry eb = toEntry(b, 0, false, h, 0, "");
                return method.comparator().compare(ea, eb);
            });
        }

        for (int i = 0; i < totalSlots; i++) {
            ItemStack newStack = i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY;
            backpack.setItem(offset + i, newStack);
        }
    }

    /**
     * 供背包 Screen 的 onClose 调用：屏幕关闭时把圣遗物背包按排序规则整理一次。
     * 排序使用 STAR 方式（星级降序 → 等级降序 → 套装号升序 → 部位序），
     * 分组：已激活在前，未激活在后。
     */
    public static void organizeArtifactsOnClose(Player player) {
        if (player == null) return;
        try {
            Backpack backpack = player.getData(AttachmentRegistration.BACKPACK_ATTACHMENT);
            organizeBackpack(backpack, Backpack.Category.ARTIFACTS, ArtifactSortMethod.STAR);
        } catch (Exception ex) {
            LOGGER.warn("关闭背包时排序失败: {}", ex.getMessage());
        }
    }

    // ========================================================================
    //  图标模式
    // ========================================================================

    private static UIElement refreshIconList(
            Backpack backpack, Backpack.Category category,
            UIElement detailPanel,
            Player player,
            ScrollerView iconGenshinBackpackWindow,
            AtomicReference<UIElement> iconGrid,
            AtomicReference<Backpack.Category> currentCategoryRef,
            AtomicReference<String> selectedKeyRef,
            ArtifactSortMethod sortMethod) {

        var gridContainer = new UIElement().setId("icon-grid-container");
        AtomicReference<UIElement> selectedElementRef = new AtomicReference<>();

        List<ArtifactSortMethod.Entry> sorted = collectSorted(backpack, category, player, sortMethod);

        String curKey = selectedKeyRef.get();
        String finalCurKey = curKey;
        boolean stillExists = curKey != null && sorted.stream().anyMatch(e -> finalCurKey.equals(e.key()));
        if (!stillExists) {
            curKey = sorted.isEmpty() ? null : sorted.get(0).key();
        }

        ArtifactSortMethod.Entry selectedEntry = null;

        for (ArtifactSortMethod.Entry entry : sorted) {
            ItemStack stack = entry.stack();
            boolean equipped = entry.equipped();

            var itemElement = new UIElement().addClass("backpack-icon-item");
            itemElement.style(s -> s.background(SpriteTexture.of(getItemTexturePath(stack))));
            if (equipped) {
                itemElement.addClass("equipped-artifact");
            }

            // 已装备的圣遗物：右上角叠加缩小版角色头像
            if (equipped && entry.equippedByCharacterTextureId() != null) {
                var avatarOverlay = new UIElement().addClass("equipped-avatar-overlay");
                avatarOverlay.style(s -> s.background(SpriteTexture.of(
                        "minegenshin:character/"
                                + entry.equippedByCharacterTextureId() + "/textures/avatar_hud.png")));
                itemElement.addChild(avatarOverlay);
            }

            if (entry.key().equals(curKey)) {
                itemElement.transform(t -> t.scale(1.1f));
                itemElement.style(s -> s.overlay(new ColorBorderTexture(1, 0xFFFFFFFF)));
                selectedElementRef.set(itemElement);
                selectedEntry = entry;
            }

            itemElement.addEventListener(UIEvents.MOUSE_ENTER, e -> {
                if (selectedElementRef.get() != itemElement) {
                    itemElement.transform(t -> t.scale(1.1f));
                    itemElement.style(s -> s.overlay(new ColorBorderTexture(1, 0xFFFFFFFF)));
                }
                itemElement.style(s -> s.zIndex(10));
            });
            itemElement.addEventListener(UIEvents.MOUSE_LEAVE, e -> {
                if (selectedElementRef.get() != itemElement) {
                    itemElement.transform(t -> t.scale(1f));
                    itemElement.style(s -> s.overlay(null));
                }
                itemElement.style(s -> s.zIndex(0));
            });
            itemElement.addEventListener(UIEvents.MOUSE_DOWN, e -> {
                itemElement.transform(t -> t.scale(1f));
            });
            itemElement.addEventListener(UIEvents.CLICK, e -> {
                UIElement prev = selectedElementRef.getAndSet(itemElement);
                if (prev != null && prev != itemElement) {
                    prev.transform(t -> t.scale(1f));
                    prev.style(s -> s.overlay(null));
                    prev.style(s -> s.zIndex(0));
                }
                itemElement.transform(t -> t.scale(1.1f));
                itemElement.style(s -> s.overlay(new ColorBorderTexture(1, 0xFFFFFFFF)));
                itemElement.style(s -> s.zIndex(10));
                selectedKeyRef.set(entry.key());
                refreshDetailPanelWithButtons(detailPanel, entry,
                        backpack, player, iconGenshinBackpackWindow, iconGrid,
                        currentCategoryRef, selectedKeyRef, sortMethod);
            });

            gridContainer.addChild(itemElement);
        }

        selectedKeyRef.set(curKey);
        if (selectedEntry != null) {
            refreshDetailPanelWithButtons(detailPanel, selectedEntry,
                    backpack, player, iconGenshinBackpackWindow, iconGrid,
                    currentCategoryRef, selectedKeyRef, sortMethod);
        } else {
            detailPanel.clearAllChildren();
            detailPanel.addChild(new Label().setText(Component.translatable("gui.minegenshin.backpack.no_selection")));
            detailPanel.markAsInternal();
        }

        return gridContainer;
    }

    private static void refreshDetailPanelWithButtons(
            UIElement container, ArtifactSortMethod.Entry entry,
            Backpack backpack, Player player,
            ScrollerView iconGenshinBackpackWindow,
            AtomicReference<UIElement> iconGrid,
            AtomicReference<Backpack.Category> currentCategoryRef,
            AtomicReference<String> selectedKeyRef,
            ArtifactSortMethod sortMethod) {

        container.clearAllChildren();

        ItemStack stack = entry.stack();
        boolean equipped = entry.equipped();
        int globalSlotIndex = entry.globalSlot();

        if (stack.isEmpty()) {
            container.addChild(new Label().setText(Component.translatable("gui.minegenshin.backpack.no_selection")));
            return;
        }

        var scroller = new ScrollerView();
        scroller.setId("detail-scroller");

        var nameLabel = new Label().setText(stack.getHoverName());
        nameLabel.addClass("detail-line");
        scroller.addScrollViewChild(nameLabel);

        if (equipped) {
            String ownerName = entry.equippedByCharacterName();
            Component text = (ownerName != null && !ownerName.isEmpty())
                    ? Component.translatable("gui.minegenshin.backpack.equipped_by", ownerName).withStyle(ChatFormatting.GOLD)
                    : Component.translatable("gui.minegenshin.backpack.equipped").withStyle(ChatFormatting.GOLD);
            var equippedLabel = new Label().setText(text);
            equippedLabel.addClass("detail-line");
            scroller.addScrollViewChild(equippedLabel);
        }

        List<Component> tooltipLines = new ArrayList<>();
        stack.getItem().appendHoverText(stack, Item.TooltipContext.EMPTY, TooltipDisplay.DEFAULT,
                tooltipLines::add, TooltipFlag.NORMAL);

        for (Component line : tooltipLines) {
            String text = line.getString();
            String[] lines = text.split("\n");
            for (String singleLine : lines) {
                var label = new Label().setText(Component.literal(singleLine).withStyle(line.getStyle()));
                label.addClass("detail-line");
                scroller.addScrollViewChild(label);
            }
        }

        container.addChild(scroller);

        var buttonContainer = new UIElement().setId("button-container");

        if (!equipped) {
            var takeOutBtn = new Button();
            takeOutBtn.setText(Component.translatable("gui.minegenshin.backpack.take_out"));
            takeOutBtn.addClass("item-action-btn");
            takeOutBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> {
                NetworkManager.sendBackpackTakeOutFromSlotToServer(globalSlotIndex);
                backpack.setItem(globalSlotIndex, ItemStack.EMPTY);
                selectedKeyRef.set(null);
                iconGenshinBackpackWindow.clearAllScrollViewChildren();
                iconGrid.set(refreshIconList(backpack, currentCategoryRef.get(), container,
                        player, iconGenshinBackpackWindow, iconGrid, currentCategoryRef,
                        selectedKeyRef, sortMethod));
                iconGenshinBackpackWindow.addScrollViewChild(iconGrid.get());
            });
            buttonContainer.addChild(takeOutBtn);
        }

        var detailBtn = new Button();
        detailBtn.setText(Component.translatable("gui.minegenshin.backpack.detail"));
        detailBtn.addClass("item-action-btn");
        buttonContainer.addChild(detailBtn);

        container.addChild(buttonContainer);
    }

    /**
     * GUI 里这件物品画哪张图。
     *
     * <p>走 {@link ItemIcons}：优先物品自己的图标
     * {@code minegenshin:item/<物品名>/icon.png}，没有才退回物品自己的贴图 ——
     * geo 物品的贴图是 3D 模型的 UV 图集，直接当 2D 精灵画会是一坨错位色块。
     */
    private static String getItemTexturePath(ItemStack stack) {
        return ItemIcons.pathOf(stack);
    }

    // ========================================================================
    //  槽位网格
    // ========================================================================

    private static UIElement buildGridForCategory(Backpack.Category category,
                                                  ResourceHandler<ItemResource> handler,
                                                  Backpack backpack,
                                                  Player player,
                                                  ArtifactSortMethod sortMethod) {
        var gridContainer = new UIElement().setId("grid_container");

        List<ArtifactSortMethod.Entry> sorted = collectSorted(backpack, category, player, sortMethod);

        int offset = getCategoryOffset(category);
        int totalSlots = category.maxCapacity;

        Set<Integer> usedIndices = new HashSet<>();
        for (var e : sorted) {
            if (e.globalSlot() >= 0) usedIndices.add(e.globalSlot());
        }
        List<Integer> emptyIndices = new ArrayList<>();
        for (int i = 0; i < category.maxCapacity; i++) {
            int g = offset + i;
            if (!usedIndices.contains(g)) emptyIndices.add(g);
        }

        int rowCount = (totalSlots + SLOTS_PER_ROW - 1) / SLOTS_PER_ROW;
        int emptyIdx = 0;

        for (int row = 0; row < rowCount; row++) {
            var rowContainer = new UIElement().setId("row_" + row + "_container").addClass("row_container");
            for (int col = 0; col < SLOTS_PER_ROW; col++) {
                int slotCounter = row * SLOTS_PER_ROW + col;
                if (slotCounter >= totalSlots) break;

                var wrapper = new UIElement().addClass("backpack-slot").setId("backpack_slot");

                if (slotCounter < sorted.size()) {
                    ArtifactSortMethod.Entry entry = sorted.get(slotCounter);

                    var slot = new ItemSlot().bind(entry.handler(), entry.containerSlot());
                    slot.layout(l -> l.widthPercent(100).heightPercent(100));
                    wrapper.addChild(slot);

                    if (entry.equipped()) {
                        wrapper.addClass("equipped-slot");
                        if (entry.equippedByCharacterTextureId() != null) {
                            var avatarOverlay = new UIElement().addClass("equipped-avatar-overlay");
                            avatarOverlay.style(s -> s.background(SpriteTexture.of(
                                    "minegenshin:character/"
                                            + entry.equippedByCharacterTextureId() + "/textures/avatar_hud.png")));
                            wrapper.addChild(avatarOverlay);
                        }
                    }
                } else if (emptyIdx < emptyIndices.size()) {
                    var slot = new ItemSlot().bind(handler, emptyIndices.get(emptyIdx));
                    slot.layout(l -> l.widthPercent(100).heightPercent(100));
                    wrapper.addChild(slot);
                    emptyIdx++;
                } else {
                    var slot = new ItemSlot().bind(handler, offset);
                    slot.layout(l -> l.widthPercent(100).heightPercent(100));
                    wrapper.addChild(slot);
                }

                rowContainer.addChild(wrapper);
            }
            gridContainer.addChild(rowContainer);
        }
        return gridContainer;
    }
}
