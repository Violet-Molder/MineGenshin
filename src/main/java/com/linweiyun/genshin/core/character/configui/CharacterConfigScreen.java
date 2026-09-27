// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character.configui;

import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo.BoneUpdater;
import com.linweiyun.genshin.client.render.character.GenshinPreviewPlayer;
import com.linweiyun.genshin.client.render.gui.component.CustomToggle;
import com.linweiyun.genshin.core.character.ICharacterConfigUI;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.appearance.CharacterAppearanceData;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ToggleGroupElement;
import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public abstract class CharacterConfigScreen implements ICharacterConfigUI {
   protected static final String PREVIEW_ANIMATION = "extra48";
   private GenshinPreviewPlayer previewAnimatable;

   @Override
   public final ModularUI createConfigUI(Player player, PGCharacter character) {
      int[] previewMask = new int[]{character.getAppearance()};
      UIElement root = new UIElement().setId("cc-root");
      root.layout(l -> {
         l.widthPercent(100.0F);
         l.heightPercent(100.0F);
      });
      UIElement body = new UIElement().setId("cc-body");
      body.addChildren(
         new UIElement[]{this.buildTitlebar(player, character), new UIElement().setId("cc-title-line"), this.buildContent(player, character, previewMask)}
      );
      UIElement window = new UIElement().setId("cc-window");
      window.addChild(body);
      root.addChild(window);
      Stylesheet stylesheet = StylesheetManager.INSTANCE.getStylesheetSafe(CharacterConfigPage.STYLESHEET);
      return ModularUI.of(UI.of(root, new Stylesheet[]{stylesheet}), player);
   }

   private UIElement buildTitlebar(Player player, PGCharacter character) {
      UIElement titlebar = new UIElement().setId("cc-titlebar");
      Label title = new Label();
      title.setId("cc-title");
      title.setText(this.titlebarText(character));
      title.layout(l -> l.height(16.0F));
      titlebar.addChild(title);
      this.fillTitlebar(titlebar, player, character);
      return titlebar;
   }

   private UIElement buildContent(Player player, PGCharacter character, int[] previewMask) {
      UIElement right = new UIElement().setId("cc-right");
      right.addChildren(new UIElement[]{this.buildAppearance(player, character, previewMask), this.buildInfoPane(player, character)});
      UIElement content = new UIElement().setId("cc-content");
      content.addChildren(
         new UIElement[]{CharacterConfigPage.buildPreview(player, character, previewMask, this.previewAnimatable(), this.previewBones()), right}
      );
      return content;
   }

   private UIElement buildAppearance(Player player, PGCharacter character, int[] previewMask) {
      UIElement box = new UIElement();
      UIElement rows = CharacterConfigPage.buildAppearanceBox(box, this.appearanceTitleKey());
      this.fillAppearance(rows, player, character, previewMask);
      return box;
   }

   private UIElement buildInfoPane(Player player, PGCharacter character) {
      UIElement box = new UIElement().setId("cc-info");
      List<CharacterConfigScreen.InfoPage> pages = this.infoPages(player, character);
      if (pages.isEmpty()) {
         return box;
      }

      if (pages.size() == 1) {
         box.addChild(pages.get(0).content());
         return box;
      }

      ToggleGroupElement tabs = new ToggleGroupElement();
      tabs.setId("cc-info-tabs");
      tabs.toggleGroup.setAllowEmpty(false);
      List<UIElement> buttons = new ArrayList<>();

      for (CharacterConfigScreen.InfoPage page : pages) {
         CustomToggle tab = new CustomToggle();
         tab.addClass("cc-tab");
         tab.setButtonText(Component.translatable(page.titleKey()));
         tab.layout(l -> l.height(12.0F));
         UIElement content = page.content();
         tab.setOnToggleChanged(on -> content.setDisplay(on));
         buttons.add(tab);
      }

      tabs.addChildren(buttons.toArray(UIElement[]::new));
      box.addChild(tabs);

      for (int i = 0; i < pages.size(); i++) {
         UIElement content = pages.get(i).content();
         content.setDisplay(i == 0);
         box.addChild(content);
      }

      ((CustomToggle)buttons.get(0)).setOn(true);
      return box;
   }

   protected Component titlebarText(PGCharacter character) {
      return this.title();
   }

   protected void fillTitlebar(UIElement titlebar, Player player, PGCharacter character) {
   }

   protected String previewAnimation() {
      return "extra48";
   }

   @Nullable
   protected BoneUpdater<GeoRenderState> previewBones() {
      return null;
   }

   protected String appearanceTitleKey() {
      return "gui.minegenshin.character_config.appearance_section";
   }

   protected void fillAppearance(UIElement rows, Player player, PGCharacter character, int[] previewMask) {
      fillAppearanceOptions(rows, character, previewMask);
      if (!character.isAllWeaponCharacter()) {
         rows.addChild(CharacterConfigPage.weaponClassRow(character));
      }
   }

   protected static void fillAppearanceOptions(UIElement rows, PGCharacter character, int[] previewMask) {
      CharacterAppearanceData appearance = character.appearanceData();

      for (int index = 0; index < appearance.optionCount(); index++) {
         if (!appearance.optionHandWritten(index)) {
            rows.addChild(CharacterConfigPage.optionRow(character, index, previewMask));
         }
      }
   }

   protected List<CharacterConfigScreen.InfoPage> infoPages(Player player, PGCharacter character) {
      return List.of(new CharacterConfigScreen.InfoPage(null, CharacterConfigPage.buildStatsScroller(character)));
   }

   protected static boolean hasCheatPermission(Player player) {
      return player != null && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
   }

   protected GenshinPreviewPlayer previewAnimatable() {
      if (this.previewAnimatable == null) {
         this.previewAnimatable = new GenshinPreviewPlayer(this.previewAnimation());
      }

      return this.previewAnimatable;
   }

   public record InfoPage(@Nullable String titleKey, UIElement content) {
   }
}
