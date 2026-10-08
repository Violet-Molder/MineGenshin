package com.linweiyun.genshin.client.render.gui.screen;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import com.linweiyun.genshin.asset.source.CharacterResourceSources;

public class ScreenCharacterSelect extends Screen {
  final ModularUI modularUI;

  public ScreenCharacterSelect(ModularUI modularUI) {
    super(Component.empty());
    this.modularUI = modularUI;
  }

  public void init() {
    super.init();
      this.modularUI.setScreenAndInit(this);
      this.addRenderableWidget(this.modularUI.getWidget());
      setFocused(this.modularUI.getWidget());
  }

    public enum SortMethod {
        STAR("star", "gui.minegenshin.character_select.sort_star"),
        LEVEL("level", "gui.minegenshin.character_select.sort_level");

        private final String value;
        private final String description;

        SortMethod(String value, String description) {
            this.value = value;
            this.description = description;
        }

        @Override
        public String toString() { return I18n.get(this.description); }

        public String getValue() { return value; }
    }

    public static ModularUI createModularUI(Player player, int index) {
        var stylesheet = StylesheetManager.INSTANCE.getStylesheetSafe(
                ResourceLocation.parse("minegenshin:lss/character_select.lss"));
        var charactersAttachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);

        var root = new UIElement().setId("root");
        var characterView = new UIElement().setId("character-view");
        // ★ 用 ScrollerView 把角色列表「框」起来：内容超出时自动出现滚动条，
        //   不再手搓「把 character-list 的 bottom 往上挪」那套偏移。
        var characterScroller = new ScrollerView();
        characterScroller.setId("character-scroller");
        characterScroller.scrollerStyle(style -> style
                .mode(ScrollerMode.VERTICAL)
                .verticalScrollDisplay(ScrollDisplay.AUTO));
        var characterContainer = new UIElement().setId("character-container");
        var sortSelector = new Selector<SortMethod>();
        var characterListContainer = new UIElement().setId("character-list-container");
        var characterList = new UIElement().setId("character-list");
        AtomicReference<PGCharacter> selectedCharacter = new AtomicReference<>();
        AtomicReference<Integer> selectedUUID = new AtomicReference<>(0);
        var deployButton = new Button();
        var removeCharacterButton = new Button();
        var switchCharacterButton = new Button();
        var currentCharacter = new UIElement();
        AtomicReference<SortMethod> currentSort = new AtomicReference<>(SortMethod.STAR);
        AtomicReference<UIElement> selectedCharacterButton = new AtomicReference<>();

        root.layout(layout -> {
            layout.widthPercent(100f);
            layout.heightPercent(100f);
        });

        // ========== 出战按钮 ==========
        deployButton
                .setText(Component.translatable("gui.minegenshin.character_select.deploy"))
                .setOnClick(e -> {
                    Integer uuid = selectedUUID.get();
                    if (uuid == null || uuid == 0) return;
                    List<Integer> partyUUIDs = charactersAttachment.getPartyCharacterUUIDs();
                    if (partyUUIDs.contains(uuid)) return;
                    int emptyIndex = -1;
                    for (int i = 0; i < 4; i++) {
                        if (charactersAttachment.getPartyCharacter(i) == null) {
                            emptyIndex = i;
                            break;
                        }
                    }
                    if (emptyIndex != -1) {
                        charactersAttachment.setPartyCharacterToServer(emptyIndex, uuid);
                        ScreenNavigator.openCharacterPartyScreen(player);
                    }
                })
                .setId("deploy-button")
                .setDisplay(false);

        // ========== 换下按钮 ==========
        removeCharacterButton
                .setText(Component.translatable("gui.minegenshin.character_select.recall"))
                .setOnClick(e -> {
                    boolean removed = charactersAttachment.removePartyCharacterToServer(index);
                    if (removed) {
                        ScreenNavigator.openCharacterPartyScreen(player);
                    }
                })
                .setDisplay(false);

        // ========== 更换按钮 ==========
        switchCharacterButton
                .setText(Component.translatable("gui.minegenshin.character_select.swap"))
                .setOnClick(e -> {
                    Integer uuid = selectedUUID.get();
                    if (uuid == null || uuid == 0) return;
                    List<Integer> partyUUIDs = charactersAttachment.getPartyCharacterUUIDs();
                    int existingSlot = partyUUIDs.indexOf(uuid);
                    charactersAttachment.setPartyCharacterToServer(index, uuid);
                    if (existingSlot != -1) {
                        PGCharacter oldChar = charactersAttachment.getPartyCharacter(index);
                        if (oldChar != null) {
                            charactersAttachment.setPartyCharacterToServer(existingSlot, oldChar.getCharacterUUID());
                        }
                    }
                    ScreenNavigator.openCharacterPartyScreen(player);
                })
                .setDisplay(false);

        // ========== 当前角色立绘 ==========
        if (index != -1) {
            removeCharacterButton.setDisplay(true);
            PGCharacter partyChar = charactersAttachment.getPartyCharacter(index);
            if (partyChar != null) {
                currentCharacter
                        .setId("Pose-Stand-" + partyChar.getName().getString())
                        .addClass("character-pose")
                        .style(style -> style.background(
                                SpriteTexture.of(CharacterResourceSources.splash(partyChar.getTextureId()))));

            }
        }

        // ========== 渲染角色头像列表 ==========
        Runnable renderCharacterAvatars = () -> {

            characterList.clearAllChildren();

            List<Integer> sheetUUIDs = new ArrayList<>(charactersAttachment.getSheetCharacterUUIDs());
            SortMethod sort = currentSort.get();

            Comparator<Integer> comparator = switch (sort) {
                case STAR -> Comparator
                        .comparingInt((Integer uuid) -> {
                            PGCharacter c = charactersAttachment.getCharacterByUUID(uuid);
                            return c != null ? c.getStarRating() : 0;
                        }).reversed()
                        .thenComparingInt(uuid -> {
                            PGCharacter c = charactersAttachment.getCharacterByUUID(uuid);
                            return c != null ? c.getData().getLevel() : 0;
                        }).reversed()
                        .thenComparingInt(uuid -> uuid);
                case LEVEL -> Comparator
                        .comparingInt((Integer uuid) -> {
                            PGCharacter c = charactersAttachment.getCharacterByUUID(uuid);
                            return c != null ? c.getData().getLevel() : 0;
                        }).reversed()
                        .thenComparingInt(uuid -> {
                            PGCharacter c = charactersAttachment.getCharacterByUUID(uuid);
                            return c != null ? c.getStarRating() : 0;
                        }).reversed()
                        .thenComparingInt(uuid -> uuid);
            };
            sheetUUIDs.sort(comparator);

            for (int uuid : sheetUUIDs) {
                PGCharacter ownedChar = charactersAttachment.getCharacterByUUID(uuid);
                if (ownedChar == null) continue;

                String textureId = ownedChar.getTextureId();
                var characterButton = new UIElement()
                        .addClass("character-avatar")
                        .style(style -> style.background(
                                SpriteTexture.of(CharacterResourceSources.avatar(textureId))));

                characterButton
                        .addEventListener(UIEvents.MOUSE_ENTER, e -> {
                            if (selectedCharacterButton.get() != characterButton) {
                                characterButton.style(style -> style.overlay(
                                        SpriteTexture.of("minegenshin:gui/selected_border.png")));
                                characterButton.transform(transform -> transform.scale(1.08f));
                            }
                        })
                        .addEventListener(UIEvents.MOUSE_LEAVE, e -> {
                            if (selectedCharacterButton.get() != characterButton) {
                                characterButton.style(style -> style.overlay(null));
                                characterButton.transform(transform -> transform.scale(1f));
                            }
                        })
                        .addEventListener(UIEvents.CLICK, e -> {
                            currentCharacter
                                    .setId("Pose-Stand-" + ownedChar.getName().getString())
                                    .addClass("character-pose")
                                    .style(style -> style.background(
                                            SpriteTexture.of(CharacterResourceSources.splash(textureId))));

                            if (selectedCharacterButton.get() != null
                                    && selectedCharacterButton.get() != characterButton) {
                                selectedCharacterButton.get().transform(transform -> transform.scale(1f));
                                selectedCharacterButton.get().style(style -> style.overlay(null));
                                selectedCharacterButton.get().removeClass("character-selected");
                            }
                            selectedCharacterButton.set(characterButton);
                            characterButton.addClass("character-selected");
                            characterButton.transform(transform -> transform.scale(1.08f));
                            characterButton.style(style -> style.overlay(
                                    SpriteTexture.of("minegenshin:gui/selected_border.png")));

                            selectedCharacter.set(ownedChar);
                            selectedUUID.set(uuid);

                            List<Integer> partyUUIDs = charactersAttachment.getPartyCharacterUUIDs();
                            boolean inParty = partyUUIDs.contains(uuid);
                            boolean isCurrentSlotChar = index >= 0 && uuid == partyUUIDs.get(index);

                            deployButton.setDisplay(false);
                            switchCharacterButton.setDisplay(false);
                            removeCharacterButton.setDisplay(false);

                            if (index == -1) {
                                deployButton.setDisplay(!inParty);
                            } else if (isCurrentSlotChar) {
                                removeCharacterButton.setDisplay(charactersAttachment.canRemovePartyCharacter(index));
                            } else if (!inParty) {
                                switchCharacterButton.setDisplay(true);
                            }
                        });
                characterList.addChild(characterButton);
            }
            characterList.markAsInternal();
        };

        // ========== 排序选择器 ==========
        sortSelector
                .setSelected(SortMethod.STAR, false)
                .setCandidates(List.of(SortMethod.values()))
                .setOnValueChanged(selectedSortMethod -> {
                    currentSort.set(selectedSortMethod);
                    renderCharacterAvatars.run();
                    // 换排序后回到顶部，否则会停在一个已经不存在的内容高度上
                    characterScroller.verticalScroller.setNormalizedValue(0f);
                    characterList.markAsInternal();
                });

        root.addChildren(
                characterView.addChildren(
                        characterContainer.addChildren(
                                sortSelector,
                                // ★ 排序器留在外面，只把角色网格放进滚动视图
                                characterScroller.addScrollViewChild(
                                        characterListContainer.addChildren(characterList)),
                                deployButton,
                                removeCharacterButton,
                                switchCharacterButton)),
                currentCharacter);

        renderCharacterAvatars.run();
        var ui = UI.of(root, stylesheet);
        return ModularUI.of(ui, player);
    }
}
