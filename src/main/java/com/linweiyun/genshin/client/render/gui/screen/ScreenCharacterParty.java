package com.linweiyun.genshin.client.render.gui.screen;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUIClientAccess;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import com.linweiyun.genshin.asset.source.CharacterResourceSources;

public class ScreenCharacterParty extends Screen {
  final ModularUI modularUI;

  public ScreenCharacterParty(ModularUI modularUI) {
    super(Component.empty());
    this.modularUI = modularUI;
  }

  public void init() {
    super.init();
    ModularUIClientAccess.setScreenAndInit(this.modularUI, this);
    this.addRenderableWidget(ModularUIClientAccess.getWidget(modularUI));
  }

  public static ModularUI createModularUI(Player player) {
    var stylesheet = StylesheetManager.INSTANCE.getStylesheetSafe(
            Identifier.parse("minegenshin:lss/character_party.lss"));
    var charactersAttachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);

    UIElement root = new UIElement().setId("root");
    UIElement characterPartyContainer = new UIElement().setId("character-party-container");

    root.layout(layout -> {
      layout.widthPercent(100);
      layout.heightPercent(100);
    });

    for (int i = 0; i < 4; i++) {
      UIElement characterButton = new UIElement().setId("character-button");
      UIElement characterButtonImage = new UIElement().setId("character-button-image-empty");
      boolean haveCharacter = false;

      PGCharacter partyChar = charactersAttachment.getPartyCharacter(i);
      if (partyChar != null) {
          String textureId = partyChar.getTextureId();
          characterButtonImage
                  .setId("character-button-image-" + partyChar.getName().getString())
                  .addClass("character-button-image-pose")
                  .style(style -> style.overlay(
                          SpriteTexture.of(CharacterResourceSources.splashReady(textureId))));
          haveCharacter = true;
      }

      int index = haveCharacter ? i : -1;
      characterButton
              .addChild(characterButtonImage)
              .addEventListener(UIEvents.CLICK,
                      e -> ScreenNavigator.openCharacterSelectScreen(player, index));
      characterPartyContainer.addChild(characterButton);
    }

    root.addChild(characterPartyContainer);
    var ui = UI.of(root, stylesheet);
    return ModularUI.of(ui, player);
  }
}
