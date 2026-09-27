// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character.configui;

import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo.BoneUpdater;
import com.linweiyun.genshin.client.keybindings.KeyMappingRegistry;
import com.linweiyun.genshin.client.render.character.CharacterRenderDispatcher;
import com.linweiyun.genshin.client.render.character.GenshinPreviewPlayer;
import com.linweiyun.genshin.client.render.character.appearance.CharacterAppearanceBones;
import com.linweiyun.genshin.client.render.character.appearance.CharacterAppearanceOptionBones;
import com.linweiyun.genshin.client.render.character.appearance.CharacterFaceBones;
import com.linweiyun.genshin.content.attribute.AttributeType;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.PGCharacterData;
import com.linweiyun.genshin.core.character.appearance.CharacterAppearanceData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderRepository;
import com.linweiyun.genshin.core.system.poise.WeaponPoiseTable;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public final class CharacterConfigPage {
   public static final Identifier STYLESHEET = Identifier.parse("minegenshin:lss/character_config.lss");
   public static final float PREVIEW_CENTER_Y = 1.3F;
   public static final float PREVIEW_ZOOM = 2.8F;
   public static final float PREVIEW_YAW = 115.0F;
   public static final float PREVIEW_PITCH = 12.0F;
   public static final int FULL_BRIGHT = 15728880;

   private CharacterConfigPage() {
   }

   public static UIElement buildPreview(
      Player player, PGCharacter character, int[] previewMask, GenshinPreviewPlayer previewAnimatable, @Nullable BoneUpdater<GeoRenderState> extraBones
   ) {
      String characterId = character.getTextureId();
      UIElement preview = new UIElement().setId("cc-preview");
      Scene scene = new Scene();
      scene.setId("cc-scene");
      scene.setOverflowVisible(true);
      preview.addChild(scene);
      CharacterRenderData data = CharacterRenderRepository.get(characterId);
      finishPreview(preview, data);
      ClientLevel level = Minecraft.getInstance().level;
      if (level != null && data != null) {
         scene.createScene(level);
         scene.setCenter(new Vector3f(0.0F, 1.3F, 0.0F));
         scene.setZoom(2.8F);
         scene.setCameraYawAndPitch(115.0F, 12.0F);
         scene.setShowHoverBlockTips(false);
         scene.setRenderSelect(false);
         scene.setRenderFacing(false);
         WorldSceneRenderer renderer = (WorldSceneRenderer)scene.getRenderer();
         if (renderer == null) {
            return preview;
         }

         renderer.setAfterBuiltinSubmit(
            ctx -> {
               CharacterRenderDispatcher.RenderTarget target = CharacterRenderDispatcher.targetFor(player, characterId, data);
               if (target != null) {
                  previewAnimatable.setPlayerEntity(player);
                  PoseStack poseStack = ctx.poseStack();
                  poseStack.pushPose();

                  try {
                     poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
                     BoneUpdater<GeoRenderState> bones = CharacterRenderDispatcher.combine(
                        CharacterAppearanceBones.forMask(previewMask[0]), CharacterAppearanceOptionBones.updaterForPreview(previewMask[0], character)
                     );
                     bones = CharacterRenderDispatcher.combine(bones, CharacterFaceBones.updaterForState(previewAnimatable.previewAnimationName()));
                     if (extraBones != null) {
                        bones = CharacterRenderDispatcher.combine(bones, extraBones);
                     }

                     target.renderer()
                        .performRenderPass(previewAnimatable, player, poseStack, ctx.submitStorage(), ctx.cameraState(), 15728880, ctx.partialTicks(), bones);
                  } finally {
                     poseStack.popPose();
                  }
               }
            }
         );
         return preview;
      } else {
         return preview;
      }
   }

   private static void finishPreview(UIElement preview, @Nullable CharacterRenderData data) {
      if (data != null && data.modelAuthor() != null) {
         Label credit = new Label();
         credit.setId("cc-credit");
         credit.addClass("cc-credit");
         credit.setText(Component.translatable("gui.minegenshin.character_config.model_author", new Object[]{data.modelAuthor()}));
         credit.layout(l -> l.height(10.0F));
         if (data.modelAuthorUrl() != null) {
            credit.addClass("cc-credit-link");
            String url = data.modelAuthorUrl();
            credit.addEventListener("mouseClick", event -> Util.getPlatform().openUri(url));
         }

         preview.addChild(credit);
      }

      Label hint = new Label();
      hint.setId("cc-preview-hint");
      hint.setText(
         Component.translatable(
            "gui.minegenshin.character_config.hint", new Object[]{((KeyMapping)KeyMappingRegistry.CONFIG_SCREEN_KEY.get()).getTranslatedKeyMessage()}
         )
      );
      preview.addChild(hint);
   }

   public static UIElement buildStatsScroller(PGCharacter character) {
      ScrollerView scroller = newScroller("cc-stats-scroller");
      PGCharacterData data = character.getData();
      UIElement list = new UIElement().setId("cc-stats-list");
      list.addChild(buildStatsGroupTitle("base"));
      list.addChild(buildBaseStatRow(data, (AttributeType)ModAttributes.MAX_HP.value()));
      list.addChild(buildBaseStatRow(data, (AttributeType)ModAttributes.ATK.value()));
      list.addChild(buildBaseStatRow(data, (AttributeType)ModAttributes.DEF.value()));
      list.addChild(buildValueStatRow(data, (AttributeType)ModAttributes.ELEMENTAL_MASTERY.value()));
      list.addChild(buildValueStatRow(data, (AttributeType)ModAttributes.MAX_STAMINA.value()));
      list.addChild(buildStatsGroupTitle("advanced"));
      AttributeType[] advanced = new AttributeType[]{
         (AttributeType)ModAttributes.CR.value(),
         (AttributeType)ModAttributes.CDG.value(),
         (AttributeType)ModAttributes.HB.value(),
         (AttributeType)ModAttributes.IHB.value(),
         (AttributeType)ModAttributes.ER.value(),
         (AttributeType)ModAttributes.CDR.value(),
         (AttributeType)ModAttributes.SS.value()
      };

      for (AttributeType type : advanced) {
         list.addChild(buildPercentStatRow(data, type));
      }

      list.addChild(buildStatsGroupTitle("elemental"));
      AttributeType[] elements = new AttributeType[]{
         (AttributeType)ModAttributes.PYRO_BONUS.value(),
         (AttributeType)ModAttributes.PYRO_RES.value(),
         (AttributeType)ModAttributes.HYDRO_BONUS.value(),
         (AttributeType)ModAttributes.HYDRO_RES.value(),
         (AttributeType)ModAttributes.DENDRO_BONUS.value(),
         (AttributeType)ModAttributes.DENDRO_RES.value(),
         (AttributeType)ModAttributes.ELECTRO_BONUS.value(),
         (AttributeType)ModAttributes.ELECTRO_RES.value(),
         (AttributeType)ModAttributes.ANEMO_BONUS.value(),
         (AttributeType)ModAttributes.ANEMO_RES.value(),
         (AttributeType)ModAttributes.CYRO_BONUS.value(),
         (AttributeType)ModAttributes.CYRO_RES.value(),
         (AttributeType)ModAttributes.GEO_BONUS.value(),
         (AttributeType)ModAttributes.GEO_RES.value(),
         (AttributeType)ModAttributes.PHYSICAL_BONUS.value(),
         (AttributeType)ModAttributes.PHYSICAL_RES.value()
      };

      for (AttributeType type : elements) {
         list.addChild(buildPercentStatRow(data, type));
      }

      scroller.addScrollViewChild(list);
      return scroller;
   }

   public static ScrollerView newScroller(String id) {
      ScrollerView scroller = new ScrollerView();
      scroller.setId(id);
      scroller.layout(l -> l.minWidth(24.0F));
      scroller.viewPort.layout(l -> l.minWidth(16.0F).minHeight(16.0F));
      installDragScroll(scroller);
      return scroller;
   }

   private static void installDragScroll(ScrollerView scroller) {
      boolean[] dragging = new boolean[]{false};
      float[] lastY = new float[]{0.0F};
      scroller.viewPort.addEventListener("mouseDown", event -> {
         dragging[0] = event.button == 0 && !insideField(event.target);
         lastY[0] = event.y;
      });
      scroller.viewPort.addEventListener("mouseMove", event -> {
         if (dragging[0]) {
            if (!scroller.viewPort.isMouseDown(0)) {
               dragging[0] = false;
            } else {
               float dy = event.y - lastY[0];
               lastY[0] = event.y;
               float range = scroller.getContainerHeight() - scroller.viewPort.getContentHeight();
               if (!(range <= 0.0F) && dy != 0.0F) {
                  scroller.verticalScroller.setNormalizedValue(scroller.verticalScroller.getNormalizedValue() - dy / range);
               }
            }
         }
      });
      scroller.viewPort.addEventListener("mouseUp", event -> dragging[0] = false);
      scroller.viewPort.addEventListener("mouseLeave", event -> dragging[0] = false);
   }

   private static boolean insideField(UIElement target) {
      for (UIElement element = target; element != null; element = element.getParent()) {
         if (element instanceof TextField) {
            return true;
         }
      }

      return false;
   }

   private static Label buildStatsGroupTitle(String key) {
      Label title = new Label();
      title.addClass("cc-group-title");
      title.setText(Component.translatable("gui.minegenshin.character_config.stats." + key));
      title.layout(l -> l.height(12.0F));
      return title;
   }

   private static UIElement buildBaseStatRow(PGCharacterData data, AttributeType type) {
      double total = data.getAttributeTotalValue(type);
      double base = data.getAttributeBaseValue(type);
      double extra = total - base;
      boolean breakdown = base >= 0.5;
      return buildStatRow(type, formatValue(total), breakdown ? formatValue(base) : "", breakdown && extra >= 0.5 ? formatValue(extra) : "");
   }

   private static UIElement buildValueStatRow(PGCharacterData data, AttributeType type) {
      return buildStatRow(type, formatValue(data.getAttributeTotalValue(type)), "", "");
   }

   private static UIElement buildPercentStatRow(PGCharacterData data, AttributeType type) {
      double total = data.getAttributeTotalValue(type);
      double base = data.getAttributeBaseValue(type);
      double extra = total - base;
      boolean breakdown = Math.abs(base) >= 5.0E-4;
      return buildStatRow(type, formatPercent(total), breakdown ? formatPercent(base) : "", breakdown && Math.abs(extra) >= 5.0E-4 ? formatPercent(extra) : "");
   }

   private static UIElement buildStatRow(AttributeType type, String total, String base, String extra) {
      UIElement row = new UIElement();
      row.addClass("cc-stat-row");
      Label name = new Label();
      name.addClass("cc-stat-name");
      name.setText(Component.translatable(type.translationKey()));
      name.layout(l -> l.height(11.0F));
      row.addChildren(
         new UIElement[]{
            name,
            new UIElement().addClass("cc-stat-spacer"),
            statCell("cc-stat-value", total),
            statCell("cc-stat-base", base),
            statCell("cc-stat-extra", extra.isEmpty() ? "" : "+" + extra)
         }
      );
      return row;
   }

   private static Label statCell(String styleClass, String text) {
      Label cell = new Label();
      cell.addClass(styleClass);
      cell.setText(Component.literal(text));
      cell.layout(l -> l.height(11.0F));
      return cell;
   }

   private static String formatValue(double value) {
      return String.format(Locale.ROOT, "%,d", Math.round(value));
   }

   private static String formatPercent(double ratio) {
      return String.format(Locale.ROOT, "%.1f%%", ratio * 100.0);
   }

   public static Label sectionTitle(String key) {
      Label title = new Label();
      title.addClass("cc-section-title");
      title.setText(Component.translatable(key));
      title.layout(l -> l.height(13.0F));
      return title;
   }

   public static UIElement buildAppearanceBox(UIElement box, String titleKey) {
      box.setId("cc-appearance");
      box.addChild(sectionTitle(titleKey));
      ScrollerView scroller = newScroller("cc-appearance-scroller");
      UIElement list = new UIElement().setId("cc-appearance-list");
      scroller.addScrollViewChild(list);
      box.addChild(scroller);
      return list;
   }

   public static Label rowName(String key) {
      Label name = new Label();
      name.addClass("cc-leg-name");
      name.setText(Component.translatable(key));
      name.layout(l -> l.height(12.0F));
      return name;
   }

   public static UIElement weaponClassRow(PGCharacter character) {
      UIElement row = new UIElement();
      row.addClass("cc-leg-row");
      row.addChild(rowName("gui.minegenshin.character_config.weapon_class"));
      Label value = new Label();
      value.addClass("cc-readonly");
      value.setText(Component.translatable(weaponClassKey(character)));
      value.layout(l -> l.height(12.0F));
      row.addChild(value);
      return row;
   }

   private static String weaponClassKey(PGCharacter character) {
      WeaponPoiseTable.WeaponClass weaponClass = character.currentWeaponType();
      return weaponClass == WeaponPoiseTable.WeaponClass.UNKNOWN
         ? "gui.minegenshin.character_config.weapon_class_unknown"
         : "gui.minegenshin.character_config.weapon." + weaponClass.name().toLowerCase(Locale.ROOT);
   }

   public static UIElement optionRow(PGCharacter character, int index, int[] previewMask) {
      CharacterAppearanceData appearance = character.appearanceData();
      UIElement row = new UIElement();
      row.addClass("cc-leg-row");
      row.addChild(rowName(appearance.optionNameKey(index)));
      UIElement controls = new UIElement().addClass("cc-leg-controls");
      row.addChild(controls);
      if (appearance.optionKind(index) == CharacterAppearanceData.OptionKind.CHOICE) {
         controls.addChild(optionChoice(appearance, character, index, previewMask));
         return row;
      } else {
         Toggle toggle = new Toggle();
         toggle.addClass("cc-shoes-toggle");
         toggle.setText(Component.translatable("gui.minegenshin.character_config.show"));
         toggle.layout(l -> l.height(12.0F));
         toggle.setOn(appearance.optionValue(character.getAppearance(), index) != 0, false);
         toggle.setOnToggleChanged(on -> {
            character.setAppearance(appearance.withOptionValue(character.getAppearance(), index, on ? 1 : 0));
            applyPreview(character, previewMask);
         });
         controls.addChild(toggle);
         return row;
      }
   }

   private static Selector<Integer> optionChoice(CharacterAppearanceData appearance, PGCharacter character, int index, int[] previewMask) {
      Selector<Integer> selector = new Selector();
      selector.addClass("cc-sock-selector");
      selector.layout(l -> l.height(12.0F));

      // ⚠️ 顺序要紧：LDLib2 的 setCandidateUIProvider 会立刻拿「当前值」渲染一次候选，
      // 所以必须先把当前值定下来，否则那个 lambda 会被喂 null（打开配置页直接 NPE 崩）。
      selector.setSelected(appearance.optionValue(character.getAppearance(), index), false);

      // 候选渲染再兜一层 null：万一以后有「值还没定」的路径，显示第 0 项的名字而不是崩。
      UIElementProvider<Integer> provider = UIElementProvider.text(
              valuex -> Component.translatable(appearance.optionValueNameKey(index, valuex == null ? 0 : valuex)));
      selector.setCandidateUIProvider(valuex -> provider.apply(valuex).setOverflowVisible(true).addClass("cc-sock-item"));

      List<Integer> values = new ArrayList<>();

      for (int value = 0; value < appearance.optionValueCount(index); value++) {
         values.add(value);
      }

      selector.setCandidates(values);
      selector.setOnValueChanged(valuex -> {
         if (valuex != null) {
            character.setAppearance(appearance.withOptionValue(character.getAppearance(), index, valuex));
            applyPreview(character, previewMask);
         }
      });
      return selector;
   }

   public static void applyPreview(PGCharacter character, int[] previewMask) {
      previewMask[0] = character.getAppearance();
      PlayerCharactersAttachment attachment = Minecraft.getInstance().player == null
         ? null
         : (PlayerCharactersAttachment)Minecraft.getInstance().player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
      if (attachment != null) {
         attachment.syncSingleCharacterToServer(character);
      }
   }
}
