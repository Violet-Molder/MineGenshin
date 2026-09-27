// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.client.render.gui.menu;

import com.linweiyun.genshin.client.render.gui.component.CustomToggle;
import com.linweiyun.genshin.content.items.artifact.ArtifactItem;
import com.linweiyun.genshin.content.items.artifact.ArtifactSet;
import com.linweiyun.genshin.content.items.artifact.inventory.ArtifactInventory;
import com.linweiyun.genshin.content.items.component.ArtifactStatsComponent;
import com.linweiyun.genshin.content.items.component.WeaponStatsComponent;
import com.linweiyun.genshin.content.items.weapon.WeaponItem;
import com.linweiyun.genshin.core.asset.ItemIcons;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.Backpack;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.linweiyun.genshin.core.system.registry.register.ModDataComponents;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Switch;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ToggleGroupElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.vfyjxf.taffy.style.FlexDirection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.slf4j.Logger;

public class BackpackUI {
   private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);
   private static final int SLOTS_PER_ROW = 10;

   public static ModularUI createUI(Player player, Backpack backpack) {
      UIElement root = new UIElement().setId("root");
      Stylesheet stylesheet = StylesheetManager.INSTANCE.getStylesheetSafe(Identifier.parse("minegenshin:lss/backpack.lss"));
      UIElement window = new UIElement().setId("window");
      UIElement sidebarPanel = new UIElement().setId("sidebar-panel");
      UIElement contentPanel = new UIElement().setId("content-panel");
      UIElement categorySection = new UIElement().setId("category-section");
      ToggleGroupElement categoryGroup = new ToggleGroupElement();

      for (Backpack.Category category : Backpack.Category.values()) {
         String toggleId = category.name().toLowerCase() + "-toggle";
         CustomToggle toggle = new CustomToggle();
         toggle.setId(toggleId).addClass("category-toggle");
         toggle.setButtonText(I18n.get("gui.minegenshin.backpack.category." + category.name().toLowerCase(), new Object[0]));
         categoryGroup.addChild(toggle);
      }

      UIElement firstChild = (UIElement)categoryGroup.getChildren().get(0);
      if (firstChild instanceof Toggle firstToggle) {
         firstToggle.setOn(true);
      }

      UIElement backpackContainer = new UIElement().setId("backpack_container");
      AtomicReference<Backpack.Category> currentCategoryRef = new AtomicReference<>(Backpack.Category.WEAPONS);
      categoryGroup.setId("category_group");
      categoryGroup.toggleGroup.setAllowEmpty(false);
      AtomicReference<ArtifactSortMethod> sortMethodRef = new AtomicReference<>(ArtifactSortMethod.STAR);
      AtomicReference<String> selectedKeyRef = new AtomicReference<>(null);
      UIElement iconModeSection = new UIElement().setId("icon-mode-section");
      ScrollerView iconGenshinBackpackWindow = new ScrollerView();
      iconGenshinBackpackWindow.setId("icon-genshin-backpack-window");
      UIElement iconPlayerInventoryWindow = new UIElement().setId("icon-player-inventory-window");
      UIElement detailPanel = new UIElement().setId("detail-panel-container");
      AtomicReference<UIElement> iconGrid = new AtomicReference<>(null);
      UIElement initialGrid = refreshIconList(
         backpack, Backpack.Category.WEAPONS, detailPanel, player, iconGenshinBackpackWindow, iconGrid, currentCategoryRef, selectedKeyRef, sortMethodRef.get()
      );
      iconGrid.set(initialGrid);
      iconGrid.get().setId("icon-grid-container");
      UIElement slotModeSection = new UIElement().setId("slot-mode-section");
      slotModeSection.setDisplay(false);
      AtomicBoolean isSlotMode = new AtomicBoolean(false);
      UIElement playerInventoryWindow = new UIElement().setId("player_inventory_window");
      ResourceHandler<ItemResource> handler = backpack.asResourceHandler();
      ScrollerView genshinBackpackWindow = new ScrollerView();
      genshinBackpackWindow.setId("genshin_backpack_window");
      Runnable rebuildSlotGrids = () -> {
         genshinBackpackWindow.clearAllScrollViewChildren();
         Backpack.Category currentCat = currentCategoryRef.get();

         for (Backpack.Category cat : Backpack.Category.values()) {
            UIElement grid = buildGridForCategory(cat, handler, backpack, player, sortMethodRef.get());
            grid.setId(cat.name().toLowerCase() + "_grid");
            grid.setDisplay(cat == currentCat);
            genshinBackpackWindow.addScrollViewChild(grid);
         }
      };
      rebuildSlotGrids.run();
      Selector<ArtifactSortMethod> sortSelector = new Selector();
      sortSelector.setId("artifact-sort-selector");
      sortSelector.setSelected(ArtifactSortMethod.STAR, false);
      sortSelector.setCandidates(List.of(ArtifactSortMethod.values()));
      sortSelector.setOnValueChanged(
         method -> {
            sortMethodRef.set(method);
            if (isSlotMode.get()) {
               rebuildSlotGrids.run();
            } else {
               iconGenshinBackpackWindow.clearAllScrollViewChildren();
               iconGrid.set(
                  refreshIconList(
                     backpack, currentCategoryRef.get(), detailPanel, player, iconGenshinBackpackWindow, iconGrid, currentCategoryRef, selectedKeyRef, method
                  )
               );
               iconGenshinBackpackWindow.addScrollViewChild(iconGrid.get());
            }
         }
      );
      sortSelector.setDisplay(false);
      Button organizeBtn = new Button();
      organizeBtn.setId("artifact-organize-button");
      organizeBtn.setText(Component.translatable("gui.minegenshin.backpack.organize"));
      organizeBtn.addClass("item-action-btn");
      organizeBtn.setDisplay(false);
      organizeBtn.setOnClick(e -> {
         Backpack.Category cat = currentCategoryRef.get();
         if (cat == Backpack.Category.ARTIFACTS) {
            organizeBackpack(backpack, cat, sortMethodRef.get());
            rebuildSlotGrids.run();
         }
      });
      UIElement artifactControls = new UIElement().setId("artifact-controls");
      artifactControls.addChildren(new UIElement[]{sortSelector, organizeBtn});
      artifactControls.setDisplay(false);
      Switch modeSwitch = new Switch();
      modeSwitch.setId("mode-switch");
      modeSwitch.setOnSwitchChanged(
         isOn -> {
            isSlotMode.set(isOn);
            slotModeSection.setDisplay(isOn);
            iconModeSection.setDisplay(!isOn);
            organizeBtn.setDisplay(isOn && currentCategoryRef.get() == Backpack.Category.ARTIFACTS);
            if (!isOn) {
               iconGenshinBackpackWindow.clearAllScrollViewChildren();
               iconGrid.set(
                  refreshIconList(
                     backpack,
                     currentCategoryRef.get(),
                     detailPanel,
                     player,
                     iconGenshinBackpackWindow,
                     iconGrid,
                     currentCategoryRef,
                     selectedKeyRef,
                     sortMethodRef.get()
                  )
               );
               iconGenshinBackpackWindow.addScrollViewChild(iconGrid.get());
            }
         }
      );
      backpack.setOnChange(() -> {});
      Map<String, Backpack.Category> toggleCategoryMap = new HashMap<>();

      for (Backpack.Category category : Backpack.Category.values()) {
         toggleCategoryMap.put(category.name().toLowerCase() + "-toggle", category);
      }

      for (UIElement child : categoryGroup.getChildren()) {
         if (child instanceof Toggle toggle) {
            toggle.setOnToggleChanged(
               isOn -> {
                  if (isOn) {
                     Toggle current = categoryGroup.toggleGroup.getCurrentToggle();
                     if (current != null) {
                        Backpack.Category cat = toggleCategoryMap.get(current.getId());
                        currentCategoryRef.set(cat);
                        selectedKeyRef.set(null);
                        boolean isArtifact = cat == Backpack.Category.ARTIFACTS;
                        artifactControls.setDisplay(isArtifact);
                        sortSelector.setDisplay(isArtifact);
                        organizeBtn.setDisplay(isArtifact && isSlotMode.get());

                        for (UIElement grid : genshinBackpackWindow.viewContainer.getChildren()) {
                           grid.setDisplay(grid.getId().equals(cat.name().toLowerCase() + "_grid"));
                        }

                        iconGenshinBackpackWindow.clearAllScrollViewChildren();
                        iconGrid.set(
                           refreshIconList(
                              backpack, cat, detailPanel, player, iconGenshinBackpackWindow, iconGrid, currentCategoryRef, selectedKeyRef, sortMethodRef.get()
                           )
                        );
                        iconGenshinBackpackWindow.addScrollViewChild(iconGrid.get());
                     }
                  }
               }
            );
         }
      }

      root.layout(layout -> {
         layout.widthPercent(100.0F);
         layout.heightPercent(100.0F);
      });
      sidebarPanel.layout(layout -> layout.flexDirection(FlexDirection.COLUMN));
      root.addChildren(
         new UIElement[]{
            window.addChildren(
               new UIElement[]{
                  sidebarPanel.addChildren(new UIElement[]{categorySection.addChildren(new UIElement[]{categoryGroup})}),
                  contentPanel.addChildren(
                     new UIElement[]{
                        backpackContainer.addChildren(
                           new UIElement[]{
                              iconModeSection.addChildren(
                                 new UIElement[]{
                                    iconGenshinBackpackWindow.addScrollViewChild(iconGrid.get()),
                                    iconPlayerInventoryWindow.addChildren(new UIElement[]{detailPanel})
                                 }
                              ),
                              slotModeSection.addChildren(
                                 new UIElement[]{genshinBackpackWindow, playerInventoryWindow.addChildren(new UIElement[]{new InventorySlots()})}
                              ),
                              modeSwitch
                           }
                        )
                     }
                  ),
                  artifactControls
               }
            )
         }
      );
      UI ui = UI.of(root, new Stylesheet[]{stylesheet});
      return ModularUI.of(ui, player);
   }

   private static int getCategoryOffset(Backpack.Category target) {
      int offset = 0;

      for (Backpack.Category category : Backpack.Category.values()) {
         if (category == target) {
            break;
         }

         offset += category.maxCapacity;
      }

      return offset;
   }

   private static ArtifactSortMethod.Entry toEntry(
      ItemStack stack,
      int globalSlot,
      boolean equipped,
      ResourceHandler<ItemResource> handler,
      int containerSlot,
      String key,
      String equippedByCharacterName,
      String equippedByCharacterTextureId
   ) {
      if (stack.getItem() instanceof WeaponItem weapon) {
         WeaponStatsComponent stats = (WeaponStatsComponent)stack.getOrDefault(
            (DataComponentType)ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT
         );
         return new ArtifactSortMethod.Entry(
            stack,
            globalSlot,
            equipped,
            true,
            weapon.getStar(),
            stats.level,
            0,
            0,
            handler,
            containerSlot,
            key,
            equippedByCharacterName,
            equippedByCharacterTextureId
         );
      } else if (stack.getItem() instanceof ArtifactItem artifact) {
         ArtifactStatsComponent var15 = (ArtifactStatsComponent)stack.getOrDefault(
            (DataComponentType)ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT
         );
         int setId = 999;
         DeferredHolder<ArtifactSet, ArtifactSet> setHolder = artifact.getSet();
         if (setHolder != null && setHolder.get() != null) {
            setId = ((ArtifactSet)setHolder.get()).setId();
         }

         int typeOrdinal = artifact.getType() != null ? artifact.getType().ordinal() : 99;
         return new ArtifactSortMethod.Entry(
            stack,
            globalSlot,
            equipped,
            var15.activated,
            artifact.getStar(),
            var15.level,
            setId,
            typeOrdinal,
            handler,
            containerSlot,
            key,
            equippedByCharacterName,
            equippedByCharacterTextureId
         );
      } else {
         return new ArtifactSortMethod.Entry(
            stack, globalSlot, equipped, false, 0, 0, 0, 0, handler, containerSlot, key, equippedByCharacterName, equippedByCharacterTextureId
         );
      }
   }

   private static ArtifactSortMethod.Entry toEntry(
      ItemStack stack, int globalSlot, boolean equipped, ResourceHandler<ItemResource> handler, int containerSlot, String key
   ) {
      return toEntry(stack, globalSlot, equipped, handler, containerSlot, key, null, null);
   }

   private static List<ArtifactSortMethod.Entry> collectSorted(Backpack backpack, Backpack.Category category, Player player, ArtifactSortMethod method) {
      List<ArtifactSortMethod.Entry> list = new ArrayList<>();
      int offset = getCategoryOffset(category);
      int totalSlots = category.maxCapacity;
      ResourceHandler<ItemResource> backpackHandler = backpack.asResourceHandler();

      for (int i = 0; i < totalSlots; i++) {
         ItemStack stack = backpack.getItem(offset + i);
         if (!stack.isEmpty()) {
            int globalSlot = offset + i;
            String key = "b:" + globalSlot;
            list.add(toEntry(stack, globalSlot, false, backpackHandler, globalSlot, key));
         }
      }

      if (category == Backpack.Category.ARTIFACTS || category == Backpack.Category.WEAPONS) {
         try {
            PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            if (attachment != null) {
               for (PGCharacter character : attachment.getOwnedCharacters()) {
                  if (character != null && character.getData() != null) {
                     ArtifactInventory inv = character.getData().getArtifactInventory();
                     if (inv != null) {
                        ResourceHandler<ItemResource> lockedHandler = new LockedResourceHandler(inv.asResourceHandler());
                        int charUUID = character.getCharacterUUID();
                        String charName = character.getName().getString();
                        String charTextureId = character.getTextureId();
                        if (category == Backpack.Category.ARTIFACTS) {
                           for (int i = 0; i < 5; i++) {
                              ItemStack stack = inv.getItem(i);
                              if (!stack.isEmpty()) {
                                 String key = "e:" + charUUID + ":" + i;
                                 list.add(toEntry(stack, -1, true, lockedHandler, i, key, charName, charTextureId));
                              }
                           }
                        } else {
                           for (int i = 5; i < inv.slotCount(); i++) {
                              ItemStack stack = inv.getItem(i);
                              if (!stack.isEmpty()) {
                                 String key = "e:" + charUUID + ":w" + i;
                                 list.add(toEntry(stack, -1, true, lockedHandler, i, key, charName, charTextureId));
                              }
                           }
                        }
                     }
                  }
               }
            }
         } catch (Exception ex) {
            LOGGER.warn("收集已装备物品失败: {}", ex.getMessage());
         }
      }

      if (category == Backpack.Category.ARTIFACTS) {
         Comparator<ArtifactSortMethod.Entry> cmp = Comparator.<ArtifactSortMethod.Entry>comparingInt(e -> e.equipped() ? 0 : (e.activated() ? 1 : 2))
            .thenComparing(method.comparator());
         list.sort(cmp);
      } else if (category == Backpack.Category.WEAPONS) {
         Comparator<ArtifactSortMethod.Entry> cmp = Comparator.<ArtifactSortMethod.Entry>comparingInt(e -> e.equipped() ? 0 : 1)
            .thenComparing(method.comparator());
         list.sort(cmp);
      }

      return list;
   }

   private static void organizeBackpack(Backpack backpack, Backpack.Category category, ArtifactSortMethod method) {
      int offset = getCategoryOffset(category);
      int totalSlots = category.maxCapacity;
      List<ItemStack> stacks = new ArrayList<>();

      for (int i = 0; i < totalSlots; i++) {
         ItemStack stack = backpack.getItem(offset + i);
         if (!stack.isEmpty()) {
            stacks.add(stack);
         }
      }

      if (category == Backpack.Category.ARTIFACTS) {
         ResourceHandler<ItemResource> h = backpack.asResourceHandler();
         stacks.sort((a, b) -> {
            ArtifactSortMethod.Entry ea = toEntry(a, 0, false, h, 0, "");
            ArtifactSortMethod.Entry eb = toEntry(b, 0, false, h, 0, "");
            int ga = ea.activated() ? 0 : 1;
            int gb = eb.activated() ? 0 : 1;
            return ga != gb ? Integer.compare(ga, gb) : method.comparator().compare(ea, eb);
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

   public static void organizeArtifactsOnClose(Player player) {
      if (player != null) {
         try {
            Backpack backpack = (Backpack)player.getData(AttachmentRegistration.BACKPACK_ATTACHMENT);
            organizeBackpack(backpack, Backpack.Category.ARTIFACTS, ArtifactSortMethod.STAR);
         } catch (Exception ex) {
            LOGGER.warn("关闭背包时排序失败: {}", ex.getMessage());
         }
      }
   }

   private static UIElement refreshIconList(
      Backpack backpack,
      Backpack.Category category,
      UIElement detailPanel,
      Player player,
      ScrollerView iconGenshinBackpackWindow,
      AtomicReference<UIElement> iconGrid,
      AtomicReference<Backpack.Category> currentCategoryRef,
      AtomicReference<String> selectedKeyRef,
      ArtifactSortMethod sortMethod
   ) {
      UIElement gridContainer = new UIElement().setId("icon-grid-container");
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
         UIElement itemElement = new UIElement().addClass("backpack-icon-item");
         itemElement.style(s -> s.background(SpriteTexture.of(getItemTexturePath(stack))));
         if (equipped) {
            itemElement.addClass("equipped-artifact");
         }

         if (equipped && entry.equippedByCharacterTextureId() != null) {
            UIElement avatarOverlay = new UIElement().addClass("equipped-avatar-overlay");
            avatarOverlay.style(
               s -> s.background(SpriteTexture.of("minegenshin:character/" + entry.equippedByCharacterTextureId() + "/textures/avatar_hud.png"))
            );
            itemElement.addChild(avatarOverlay);
         }

         if (entry.key().equals(curKey)) {
            itemElement.transform(t -> t.scale(1.1F));
            itemElement.style(s -> s.overlay(new ColorBorderTexture(1, -1)));
            selectedElementRef.set(itemElement);
            selectedEntry = entry;
         }

         itemElement.addEventListener("mouseEnter", e -> {
            if (selectedElementRef.get() != itemElement) {
               itemElement.transform(t -> t.scale(1.1F));
               itemElement.style(s -> s.overlay(new ColorBorderTexture(1, -1)));
            }

            itemElement.style(s -> s.zIndex(10));
         });
         itemElement.addEventListener("mouseLeave", e -> {
            if (selectedElementRef.get() != itemElement) {
               itemElement.transform(t -> t.scale(1.0F));
               itemElement.style(s -> s.overlay(null));
            }

            itemElement.style(s -> s.zIndex(0));
         });
         itemElement.addEventListener("mouseDown", e -> itemElement.transform(t -> t.scale(1.0F)));
         itemElement.addEventListener(
            "mouseClick",
            e -> {
               UIElement prev = selectedElementRef.getAndSet(itemElement);
               if (prev != null && prev != itemElement) {
                  prev.transform(t -> t.scale(1.0F));
                  prev.style(s -> s.overlay(null));
                  prev.style(s -> s.zIndex(0));
               }

               itemElement.transform(t -> t.scale(1.1F));
               itemElement.style(s -> s.overlay(new ColorBorderTexture(1, -1)));
               itemElement.style(s -> s.zIndex(10));
               selectedKeyRef.set(entry.key());
               refreshDetailPanelWithButtons(
                  detailPanel, entry, backpack, player, iconGenshinBackpackWindow, iconGrid, currentCategoryRef, selectedKeyRef, sortMethod
               );
            }
         );
         gridContainer.addChild(itemElement);
      }

      selectedKeyRef.set(curKey);
      if (selectedEntry != null) {
         refreshDetailPanelWithButtons(
            detailPanel, selectedEntry, backpack, player, iconGenshinBackpackWindow, iconGrid, currentCategoryRef, selectedKeyRef, sortMethod
         );
      } else {
         detailPanel.clearAllChildren();
         detailPanel.addChild(new Label().setText(Component.translatable("gui.minegenshin.backpack.no_selection")));
         detailPanel.markAsInternal();
      }

      return gridContainer;
   }

   private static void refreshDetailPanelWithButtons(
      UIElement container,
      ArtifactSortMethod.Entry entry,
      Backpack backpack,
      Player player,
      ScrollerView iconGenshinBackpackWindow,
      AtomicReference<UIElement> iconGrid,
      AtomicReference<Backpack.Category> currentCategoryRef,
      AtomicReference<String> selectedKeyRef,
      ArtifactSortMethod sortMethod
   ) {
      container.clearAllChildren();
      ItemStack stack = entry.stack();
      boolean equipped = entry.equipped();
      int globalSlotIndex = entry.globalSlot();
      if (stack.isEmpty()) {
         container.addChild(new Label().setText(Component.translatable("gui.minegenshin.backpack.no_selection")));
      } else {
         ScrollerView scroller = new ScrollerView();
         scroller.setId("detail-scroller");
         TextElement nameLabel = new Label().setText(stack.getHoverName());
         nameLabel.addClass("detail-line");
         scroller.addScrollViewChild(nameLabel);
         if (equipped) {
            String ownerName = entry.equippedByCharacterName();
            Component text = ownerName != null && !ownerName.isEmpty()
               ? Component.translatable("gui.minegenshin.backpack.equipped_by", new Object[]{ownerName}).withStyle(ChatFormatting.GOLD)
               : Component.translatable("gui.minegenshin.backpack.equipped").withStyle(ChatFormatting.GOLD);
            TextElement equippedLabel = new Label().setText(text);
            equippedLabel.addClass("detail-line");
            scroller.addScrollViewChild(equippedLabel);
         }

         List<Component> tooltipLines = new ArrayList<>();
         stack.getItem().appendHoverText(stack, TooltipContext.EMPTY, TooltipDisplay.DEFAULT, tooltipLines::add, TooltipFlag.NORMAL);

         for (Component line : tooltipLines) {
            String text = line.getString();
            String[] lines = text.split("\n");

            for (String singleLine : lines) {
               TextElement label = new Label().setText(Component.literal(singleLine).withStyle(line.getStyle()));
               label.addClass("detail-line");
               scroller.addScrollViewChild(label);
            }
         }

         container.addChild(scroller);
         UIElement buttonContainer = new UIElement().setId("button-container");
         if (!equipped) {
            Button takeOutBtn = new Button();
            takeOutBtn.setText(Component.translatable("gui.minegenshin.backpack.take_out"));
            takeOutBtn.addClass("item-action-btn");
            takeOutBtn.addEventListener(
               "mouseDown",
               e -> {
                  NetworkManager.sendBackpackTakeOutFromSlotToServer(globalSlotIndex);
                  backpack.setItem(globalSlotIndex, ItemStack.EMPTY);
                  selectedKeyRef.set(null);
                  iconGenshinBackpackWindow.clearAllScrollViewChildren();
                  iconGrid.set(
                     refreshIconList(
                        backpack,
                        currentCategoryRef.get(),
                        container,
                        player,
                        iconGenshinBackpackWindow,
                        iconGrid,
                        currentCategoryRef,
                        selectedKeyRef,
                        sortMethod
                     )
                  );
                  iconGenshinBackpackWindow.addScrollViewChild(iconGrid.get());
               }
            );
            buttonContainer.addChild(takeOutBtn);
         }

         Button detailBtn = new Button();
         detailBtn.setText(Component.translatable("gui.minegenshin.backpack.detail"));
         detailBtn.addClass("item-action-btn");
         buttonContainer.addChild(detailBtn);
         container.addChild(buttonContainer);
      }
   }

   private static String getItemTexturePath(ItemStack stack) {
      return ItemIcons.pathOf(stack);
   }

   private static UIElement buildGridForCategory(
      Backpack.Category category, ResourceHandler<ItemResource> handler, Backpack backpack, Player player, ArtifactSortMethod sortMethod
   ) {
      UIElement gridContainer = new UIElement().setId("grid_container");
      List<ArtifactSortMethod.Entry> sorted = collectSorted(backpack, category, player, sortMethod);
      int offset = getCategoryOffset(category);
      int totalSlots = category.maxCapacity;
      Set<Integer> usedIndices = new HashSet<>();

      for (ArtifactSortMethod.Entry e : sorted) {
         if (e.globalSlot() >= 0) {
            usedIndices.add(e.globalSlot());
         }
      }

      List<Integer> emptyIndices = new ArrayList<>();

      for (int i = 0; i < category.maxCapacity; i++) {
         int g = offset + i;
         if (!usedIndices.contains(g)) {
            emptyIndices.add(g);
         }
      }

      int rowCount = (totalSlots + 10 - 1) / 10;
      int emptyIdx = 0;

      for (int row = 0; row < rowCount; row++) {
         UIElement rowContainer = new UIElement().setId("row_" + row + "_container").addClass("row_container");

         for (int col = 0; col < 10; col++) {
            int slotCounter = row * 10 + col;
            if (slotCounter >= totalSlots) {
               break;
            }

            UIElement wrapper = new UIElement().addClass("backpack-slot").setId("backpack_slot");
            if (slotCounter < sorted.size()) {
               ArtifactSortMethod.Entry entry = sorted.get(slotCounter);
               ItemSlot slot = new ItemSlot().bind(entry.handler(), entry.containerSlot());
               slot.layout(l -> l.widthPercent(100.0F).heightPercent(100.0F));
               wrapper.addChild(slot);
               if (entry.equipped()) {
                  wrapper.addClass("equipped-slot");
                  if (entry.equippedByCharacterTextureId() != null) {
                     UIElement avatarOverlay = new UIElement().addClass("equipped-avatar-overlay");
                     avatarOverlay.style(
                        s -> s.background(SpriteTexture.of("minegenshin:character/" + entry.equippedByCharacterTextureId() + "/textures/avatar_hud.png"))
                     );
                     wrapper.addChild(avatarOverlay);
                  }
               }
            } else if (emptyIdx < emptyIndices.size()) {
               ItemSlot slot = new ItemSlot().bind(handler, emptyIndices.get(emptyIdx));
               slot.layout(l -> l.widthPercent(100.0F).heightPercent(100.0F));
               wrapper.addChild(slot);
               emptyIdx++;
            } else {
               ItemSlot slot = new ItemSlot().bind(handler, offset);
               slot.layout(l -> l.widthPercent(100.0F).heightPercent(100.0F));
               wrapper.addChild(slot);
            }

            rowContainer.addChild(wrapper);
         }

         gridContainer.addChild(rowContainer);
      }

      return gridContainer;
   }
}
