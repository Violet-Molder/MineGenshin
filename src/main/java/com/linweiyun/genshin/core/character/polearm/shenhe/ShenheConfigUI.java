// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character.polearm.shenhe;

import com.linweiyun.genshin.client.render.character.bones.BoneRenderState;
import com.linweiyun.genshin.client.render.character.bones.BoneUpdater;
import com.linweiyun.genshin.config.character.ShenheTalentConfig;
import com.linweiyun.genshin.config.character.TalentConfigSource;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.util.appearance.ShenheAppearanceData;
import com.linweiyun.genshin.core.character.util.appearance.SockType;
import com.linweiyun.genshin.core.character.util.config.CharacterConfigPage;
import com.linweiyun.genshin.core.character.util.config.CharacterConfigScreen;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
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
   private static final BoneUpdater<BoneRenderState> PREVIEW_SCREEN_PARK = (renderPassInfo, snapshots) -> {
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
   protected BoneUpdater<BoneRenderState> previewBones() {
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
      // 同 CharacterConfigPage#optionChoice：先定当前值，再装候选渲染器（否则 LDLib2 会拿 null 渲染一次）
      sock.setSelected(ShenheAppearanceData.INSTANCE.sock(character.getAppearance(), left), false);
      UIElementProvider<SockType> sockProvider = UIElementProvider.text(s -> Component.translatable(sockKey(s)));
      sock.setCandidateUIProvider(s -> sockProvider.apply(s).setOverflowVisible(true).addClass("cc-sock-item"));
      sock.setCandidates(List.of(SockType.values()));
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

   /**
    * 申鹤要显示在「技能倍率」页签里的那张表 —— 面板骨架、页签、输入框全在基类
    * （{@link CharacterConfigScreen#buildTalentScroller}），子类只声明用哪张。
    */
   @Override
   protected TalentConfigSource talentConfig() {
      return ShenheTalentConfig.SOURCE;
   }

   private static String sockKey(SockType sock) {
      return "gui.minegenshin.character_config.sock." + (sock == null ? "bare" : sock.name().toLowerCase(Locale.ROOT));
   }
}