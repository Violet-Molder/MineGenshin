// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character.polearm.shenhe;

import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo.BoneUpdater;
import com.linweiyun.genshin.config.character.ShenheTalentConfig;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.appearance.ShenheAppearanceData;
import com.linweiyun.genshin.core.character.appearance.SockType;
import com.linweiyun.genshin.core.character.configui.CharacterConfigPage;
import com.linweiyun.genshin.core.character.configui.CharacterConfigScreen;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class ShenheConfigUI extends CharacterConfigScreen {
   private static final String SCREEN_BONE = "ysmGlow_texiao";
   private static final String SCREEN_GEAR_BONE = "ysmGlow_texiao2";
   private static final float PREVIEW_SCREEN_PUSH = -8.0F;
   private static final float PREVIEW_SCREEN_SCALE = 0.6F;
   private static final float GEAR_SCALE_COMP_X = 2.8999999F;
   private static final float GEAR_SCALE_COMP_Y = -0.19999999F;
   private static final float GEAR_SCALE_COMP_Z = 0.009999847F;
   private static final BoneUpdater<GeoRenderState> PREVIEW_SCREEN_PARK = (renderPassInfo, snapshots) -> {
      snapshots.ifPresent("ysmGlow_texiao", snapshot -> {
         snapshot.setTranslateZ(snapshot.getTranslateZ() + -8.0F);
         snapshot.setScale(snapshot.getScaleX() * 0.6F, snapshot.getScaleY() * 0.6F, snapshot.getScaleZ() * 0.6F);
      });
      snapshots.ifPresent("ysmGlow_texiao2", snapshot -> {
         snapshot.setTranslateX(snapshot.getTranslateX() + 2.8999999F);
         snapshot.setTranslateY(snapshot.getTranslateY() + -0.19999999F);
         snapshot.setTranslateZ(snapshot.getTranslateZ() + -8.0F + 0.009999847F);
         snapshot.setScale(snapshot.getScaleX() * 0.6F, snapshot.getScaleY() * 0.6F, snapshot.getScaleZ() * 0.6F);
      });
   };

   @Override
   protected BoneUpdater<GeoRenderState> previewBones() {
      return PREVIEW_SCREEN_PARK;
   }

   @Override
   public Component title() {
      return Component.translatable("gui.minegenshin.character_config.title");
   }

   @Override
   protected String appearanceTitleKey() {
      return "gui.minegenshin.character_config.appearance";
   }

   @Override
   protected void fillAppearance(UIElement rows, Player player, PGCharacter character, int[] previewMask) {
      rows.addChildren(
         new UIElement[]{
            this.buildLegRow(character, previewMask, true), this.buildLegRow(character, previewMask, false), this.buildEarRow(character, previewMask)
         }
      );
      fillAppearanceOptions(rows, character, previewMask);
   }

   private UIElement buildLegRow(PGCharacter character, int[] previewMask, boolean left) {
      UIElement row = new UIElement();
      row.addClass("cc-leg-row");
      Label name = new Label();
      name.addClass("cc-leg-name");
      name.setText(Component.translatable(left ? "gui.minegenshin.character_config.leg_left" : "gui.minegenshin.character_config.leg_right"));
      name.layout(l -> l.height(12.0F));
      Toggle shoes = new Toggle();
      shoes.addClass("cc-shoes-toggle");
      shoes.setText(Component.translatable("gui.minegenshin.character_config.shoes"));
      shoes.layout(l -> l.height(12.0F));
      shoes.setOn(ShenheAppearanceData.INSTANCE.shoes(character.getAppearance(), left), false);
      shoes.setOnToggleChanged(on -> {
         character.setAppearance(ShenheAppearanceData.INSTANCE.withShoes(character.getAppearance(), left, on));
         CharacterConfigPage.applyPreview(character, previewMask);
      });
      Selector<SockType> sock = new Selector();
      sock.addClass("cc-sock-selector");
      sock.layout(l -> l.height(12.0F));
      UIElementProvider<SockType> sockProvider = UIElementProvider.text(s -> Component.translatable(sockKey(s)));
      sock.setCandidateUIProvider(s -> sockProvider.apply(s).setOverflowVisible(true).addClass("cc-sock-item"));
      sock.setCandidates(List.of(SockType.values()));
      sock.setSelected(ShenheAppearanceData.INSTANCE.sock(character.getAppearance(), left), false);
      sock.setOnValueChanged(value -> {
         if (value != null) {
            character.setAppearance(ShenheAppearanceData.INSTANCE.withSock(character.getAppearance(), left, value));
            CharacterConfigPage.applyPreview(character, previewMask);
         }
      });
      UIElement controls = new UIElement().addClass("cc-leg-controls");
      controls.addChildren(new UIElement[]{shoes, sock});
      row.addChildren(new UIElement[]{name, controls});
      return row;
   }

   private UIElement buildEarRow(PGCharacter character, int[] previewMask) {
      UIElement row = new UIElement();
      row.addClass("cc-leg-row");
      Label name = new Label();
      name.addClass("cc-leg-name");
      name.setText(Component.translatable("gui.minegenshin.character_config.cat_ears"));
      name.layout(l -> l.height(12.0F));
      Toggle ears = new Toggle();
      ears.addClass("cc-ear-toggle");
      ears.setText(Component.translatable("gui.minegenshin.character_config.show"));
      ears.layout(l -> l.height(12.0F));
      ears.setOn(ShenheAppearanceData.INSTANCE.catEarsVisible(character.getAppearance()), false);
      ears.setOnToggleChanged(on -> {
         character.setAppearance(ShenheAppearanceData.INSTANCE.withCatEarsVisible(character.getAppearance(), on));
         CharacterConfigPage.applyPreview(character, previewMask);
      });
      UIElement controls = new UIElement().addClass("cc-leg-controls");
      controls.addChild(ears);
      row.addChildren(new UIElement[]{name, controls});
      return row;
   }

   @Override
   protected List<CharacterConfigScreen.InfoPage> infoPages(Player player, PGCharacter character) {
      UIElement stats = CharacterConfigPage.buildStatsScroller(character);
      return !hasCheatPermission(player)
         ? List.of(new CharacterConfigScreen.InfoPage(null, stats))
         : List.of(
            new CharacterConfigScreen.InfoPage("gui.minegenshin.character_config.tab.stats", stats),
            new CharacterConfigScreen.InfoPage("gui.minegenshin.character_config.tab.talent", this.buildTalentScroller())
         );
   }

   private UIElement buildTalentScroller() {
      ScrollerView scroller = CharacterConfigPage.newScroller("cc-talent-scroller");
      UIElement list = new UIElement().setId("cc-talent-list");

      for (String group : ShenheTalentConfig.groups()) {
         Label groupLabel = new Label();
         groupLabel.addClass("cc-group-title");
         groupLabel.setText(Component.translatable(groupKey(group)));
         groupLabel.layout(l -> l.height(12.0F));
         list.addChild(groupLabel);

         for (String key : ShenheTalentConfig.keysOf(group)) {
            list.addChild(this.buildTalentRow(key));
         }
      }

      scroller.addScrollViewChild(list);
      return scroller;
   }

   private UIElement buildTalentRow(String key) {
      UIElement row = new UIElement();
      row.addClass("cc-talent-row");
      Label name = new Label();
      name.addClass("cc-talent-name");
      name.setText(Component.translatable(talentKey(key)));
      name.layout(l -> l.height(11.0F));
      TextField field = new TextField();
      field.addClass("cc-talent-field");
      field.layout(l -> l.height(12.0F));
      field.setOverflowVisible(true);
      field.setNumbersOnlyDouble(0.0, 100.0);
      Double value = ShenheTalentConfig.getByKey(key);
      field.setText(value == null ? "0" : trimNumber(value), false);
      field.setTextResponder(text -> {
         try {
            double parsed = Double.parseDouble(text.trim());
            ShenheTalentConfig.setByKey(key, parsed);
            NetworkManager.setTalentMultiplierToServer(key, parsed);
         } catch (NumberFormatException var4x) {
         }
      });
      row.addChildren(new UIElement[]{name, new UIElement().addClass("cc-talent-spacer"), field});
      return row;
   }

   private static String sockKey(SockType sock) {
      return "gui.minegenshin.character_config.sock." + (sock == null ? "bare" : sock.name().toLowerCase(Locale.ROOT));
   }

   private static String groupKey(String group) {
      return "gui.minegenshin.character_config.group." + group;
   }

   private static String talentKey(String key) {
      return "gui.minegenshin.character_config.talent." + key;
   }

   private static String trimNumber(double value) {
      return value == Math.rint(value) ? String.valueOf((long)value) : String.valueOf(Math.round(value * 1000000.0) / 1000000.0);
   }
}
