// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character.configui;

import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo.BoneUpdater;
import com.linweiyun.genshin.client.render.character.GenshinPreviewPlayer;
import com.linweiyun.genshin.client.render.gui.component.CustomToggle;
import com.linweiyun.genshin.config.character.TalentConfigSource;
import com.linweiyun.genshin.core.character.ICharacterConfigUI;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.appearance.CharacterAppearanceData;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
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

   /**
    * 「外观」那一块 —— K 页右侧用它，U 键装备页最下面也用它（见 {@link ICharacterConfigUI}）。
    *
    * <p>盒子与滚动区由 {@code CharacterConfigPage.buildAppearanceBox} 给
    * （样式挂在 {@code #cc-appearance} / {@code #cc-appearance-scroller} 上），
    * <b>具体有哪些装扮项由角色自己的配置页子类在 {@link #fillAppearance} 里决定</b>。
    */
   @Override
   public UIElement buildAppearanceSection(Player player, PGCharacter character, int[] previewMask) {
      UIElement box = new UIElement();
      UIElement rows = CharacterConfigPage.buildAppearanceBox(box, this.appearanceTitleKey());
      this.fillAppearance(rows, player, character, previewMask);
      return box;
   }

   /** K 页右侧的外观块就是 {@link #buildAppearanceSection}。 */
   private UIElement buildAppearance(Player player, PGCharacter character, int[] previewMask) {
      return this.buildAppearanceSection(player, character, previewMask);
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
      UIElement stats = CharacterConfigPage.buildStatsScroller(character);
      TalentConfigSource talents = this.talentConfig();

      // 没声明倍率表（或没有 OP 权限）时只有属性页；声明了就多一个「技能倍率」页签。
      // 顺带一提：op 判断和申鹤那边一致，普通玩家看不到倍率页。
      if (talents == null || !hasCheatPermission(player)) {
         return List.of(new CharacterConfigScreen.InfoPage(null, stats));
      }

      return List.of(
              new CharacterConfigScreen.InfoPage("gui.minegenshin.character_config.tab.stats", stats),
              new CharacterConfigScreen.InfoPage("gui.minegenshin.character_config.tab.talent", this.buildTalentScroller(talents))
      );
   }

   /**
    * 本角色「技能倍率」页签要显示哪张倍率表 —— <b>由角色自己的配置页子类声明</b>
    * （每个角色可调的装扮与技能值都不一样，见各角色包下的 {@code *ConfigUI}）。
    *
    * <p>返回 {@code null}（默认）表示这个角色不出倍率页，只显示属性页。
    */
   @Nullable
   protected TalentConfigSource talentConfig() {
      return null;
   }

   /**
    * 「技能倍率」页签的内容 —— 各角色共用的一份。
    *
    * <p>页面不认识字段名：它只按 {@link TalentConfigSource} 给出的分组 / key 列行，
    * 读值显示、改值写回（写下去和手改 TOML 一样），并把改动同步给服务端
    * （{@code NetworkManager.setTalentMultiplierToServer}，服务端按 key 反查是哪张表）。
    * 所以新增一个角色只要在 {@code TalentConfigs} 里登记它自己的倍率表，
    * 这个页签就会自动出现（见 {@code CharacterConfigUI}）。
    */
   protected UIElement buildTalentScroller(TalentConfigSource source) {
      ScrollerView scroller = CharacterConfigPage.newScroller("cc-talent-scroller");
      UIElement list = new UIElement().setId("cc-talent-list");

      for (String group : source.groups()) {
         Label groupLabel = new Label();
         groupLabel.addClass("cc-group-title");
         groupLabel.setText(Component.translatable("gui.minegenshin.character_config.group." + group));
         groupLabel.layout(l -> l.height(12.0F));
         list.addChild(groupLabel);

         for (String key : source.keysOf(group)) {
            list.addChild(this.buildTalentRow(source, key));
         }
      }

      scroller.addScrollViewChild(list);
      return scroller;
   }

   /** 一行倍率：名字 + 可编辑的数字框（0 ~ 100，和配置项的范围一致）。 */
   private UIElement buildTalentRow(TalentConfigSource source, String key) {
      UIElement row = new UIElement();
      row.addClass("cc-talent-row");

      Label name = new Label();
      name.addClass("cc-talent-name");
      name.setText(Component.translatable("gui.minegenshin.character_config.talent." + key));
      name.layout(l -> l.height(11.0F));

      TextField field = new TextField();
      field.addClass("cc-talent-field");
      field.layout(l -> l.height(12.0F));
      field.setOverflowVisible(true);
      field.setNumbersOnlyDouble(0.0, 100.0);
      Double value = source.getByKey(key);
      field.setText(value == null ? "0" : trimNumber(value), false);
      field.setTextResponder(text -> {
         try {
            double parsed = Double.parseDouble(text.trim());
            source.setByKey(key, parsed);
            NetworkManager.setTalentMultiplierToServer(key, parsed);
         } catch (NumberFormatException ignored) {
            // 输入途中（空串 / 半个数字）不处理，等下一次合法输入
         }
      });

      row.addChildren(new UIElement[]{name, new UIElement().addClass("cc-talent-spacer"), field});
      return row;
   }

   /** 倍率显示成文本：整数不带小数点，其余最多 6 位小数。 */
   protected static String trimNumber(double value) {
      return value == Math.rint(value)
              ? String.valueOf((long) value)
              : String.valueOf(Math.round(value * 1000000.0) / 1000000.0);
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
