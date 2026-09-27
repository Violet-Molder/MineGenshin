// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.client.render.gui.screen;

import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo.BoneUpdater;
import com.linweiyun.genshin.client.combat.state.AnimationAvailability;
import com.linweiyun.genshin.client.keybindings.KeyMappingRegistry;
import com.linweiyun.genshin.client.render.character.CharacterRenderDispatcher;
import com.linweiyun.genshin.client.render.character.GenshinPreviewPlayer;
import com.linweiyun.genshin.client.render.character.appearance.CharacterAppearanceBones;
import com.linweiyun.genshin.client.render.character.appearance.CharacterAppearanceOptionBones;
import com.linweiyun.genshin.client.render.character.appearance.CharacterFaceBones;
import com.linweiyun.genshin.client.render.character.appearance.CharacterPropBones;
import com.linweiyun.genshin.client.render.character.appearance.CharacterPuppetBones;
import com.linweiyun.genshin.client.render.gui.component.CustomToggle;
import com.linweiyun.genshin.config.character.CharacterXpConfig;
import com.linweiyun.genshin.content.attribute.AttributeType;
import com.linweiyun.genshin.content.items.artifact.ArtifactItem;
import com.linweiyun.genshin.content.items.artifact.inventory.ArtifactInventory;
import com.linweiyun.genshin.content.items.artifact.type.ArtifactType;
import com.linweiyun.genshin.content.items.component.ArtifactStatsComponent;
import com.linweiyun.genshin.content.items.component.WeaponStatsComponent;
import com.linweiyun.genshin.content.items.development.AdviceBookItem;
import com.linweiyun.genshin.content.items.weapon.WeaponItem;
import com.linweiyun.genshin.content.stat.TeyvatItemStat;
import com.linweiyun.genshin.core.asset.ItemIcons;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.Backpack;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.PGCharacterData;
import com.linweiyun.genshin.core.character.ICharacterConfigUI;
import com.linweiyun.genshin.core.character.configui.CharacterConfigPage;
import com.linweiyun.genshin.core.character.talent.TalentUpgradeCost;
import com.linweiyun.genshin.core.element.GenshinElement;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderRepository;
import com.linweiyun.genshin.core.system.poise.WeaponPoiseTable;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.core.system.registry.register.ModDataComponents;
import com.lowdragmc.lowdraglib2.client.scene.SceneRenderContext;
import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public final class CharacterEquipUI {
   private static final Identifier STYLESHEET = Identifier.parse("minegenshin:lss/character_equip.lss");
   private static final int TARGET_CHARACTER = -1;
   private static final int TARGET_WEAPON = -2;
   private static final int FULL_BRIGHT = 15728880;
   private static final float PREVIEW_CENTER_Y = 1.2F;
   private static final float PREVIEW_ZOOM = 3.1F;
   private static final float PREVIEW_YAW = 115.0F;
   private static final float PREVIEW_PITCH = 12.0F;
   private static final String PREVIEW_ANIMATION_IDLE = "extra48";
   private static final String PREVIEW_ANIMATION_IDLE_PLAIN = "idle";
   private static final String PREVIEW_ANIMATION_EQUIP = "extra_equip";
   private static final ArtifactType[] ARTIFACT_TYPES = new ArtifactType[]{
      ArtifactType.FLOWER, ArtifactType.PLUME, ArtifactType.SANDS, ArtifactType.GOBLET, ArtifactType.CIRCLET
   };
   private static final float[] ORB_DEFAULT_X = new float[]{0.8F, 0.84F, 0.72F, 0.18F, 0.16F};
   private static final float[] ORB_DEFAULT_Y = new float[]{0.38F, 0.56F, 0.74F, 0.66F, 0.4F};
   private static final int INVENTORY_MAIN_SLOTS = 36;
   @Nullable
   private static CharacterEquipUI.State OPEN_STATE;
   private static final int TALENT_NORMAL = 0;
   private static final int TALENT_SKILL = 1;
   private static final int TALENT_BURST = 2;
   private static final int TALENT_PASSIVE_1 = 3;
   private static final int TALENT_PASSIVE_2 = 4;

   private CharacterEquipUI() {
   }

   public static void clearOpenState() {
      OPEN_STATE = null;
   }

   public static void refreshIfOpen() {
      CharacterEquipUI.State st = OPEN_STATE;
      if (st != null) {
         pullFresh(st);
         rebuild(st);
      }
   }

   public static ModularUI createModularUI(Player player) {
      Stylesheet stylesheet = StylesheetManager.INSTANCE.getStylesheetSafe(STYLESHEET);
      // 最下面那块「外观」直接复用 K 页的外观盒（#cc-appearance 及其 cc-* 行），所以连配置页的样式表一起挂上。
      Stylesheet configStylesheet = StylesheetManager.INSTANCE.getStylesheetSafe(CharacterConfigPage.STYLESHEET);
      UIElement root = new UIElement().setId("ce-root");
      root.layout(l -> {
         l.widthPercent(100.0F);
         l.heightPercent(100.0F);
      });
      PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
      if (attachment != null && attachment.getCurrentCharacter() != null) {
         CharacterEquipUI.State st = new CharacterEquipUI.State(player, attachment, (Backpack)player.getData(AttachmentRegistration.BACKPACK_ATTACHMENT));
         st.partyIndex = Math.max(0, attachment.getCurrentCharacterIndex());
         st.character = attachment.getCurrentCharacter();
         NetworkManager.sendSetEditTargetToServer(st.character.getCharacterUUID());
         UIElement window = new UIElement().setId("ce-window");
         UIElement body = new UIElement().setId("ce-body");
         Label title = new Label();
         title.setId("ce-title");
         title.setText(Component.translatable("gui.minegenshin.character_equip.title"));
         title.layout(l -> l.height(15.0F));
         st.stripScroll = new ScrollerView();
         st.stripScroll.setId("ce-strip-scroll");
         st.stripScroll
            .scrollerStyle(s -> s.mode(ScrollerMode.HORIZONTAL).horizontalScrollDisplay(ScrollDisplay.AUTO).verticalScrollDisplay(ScrollDisplay.NEVER));
         st.stripScroll.viewPort.style(s -> s.background(IGuiTexture.EMPTY));
         st.stripScroll.viewPort.layout(l -> l.paddingAll(3.0F).minWidth(16.0F).minHeight(16.0F));
         st.strip = new UIElement().setId("ce-strip");
         st.stripScroll.addScrollViewChild(st.strip);
         UIElement stripFrame = new UIElement().setId("ce-strip-frame");
         stripFrame.addChild(st.stripScroll);
         UIElement topbar = new UIElement().setId("ce-topbar");
         topbar.addChildren(new UIElement[]{title, stripFrame});
         UIElement titleLine = new UIElement().setId("ce-title-line");
         st.menu = new UIElement().setId("ce-menu");
         st.panel = new UIElement().setId("ce-panel");
         st.bottomRight = new UIElement().setId("ce-actions");
         st.bottomRight.addClass("ce-row");
         st.viewOnlyNote = new Label();
         st.viewOnlyNote.addClass("ce-viewonly");
         st.viewOnlyNote.setText(Component.translatable("gui.minegenshin.character_equip.view_only"));
         st.viewOnlyNote.layout(l -> l.display(TaffyDisplay.NONE).height(13.0F));
         st.sub = new UIElement().setId("ce-sub");
         st.sub.layout(l -> l.display(TaffyDisplay.NONE));
         st.main = new UIElement().setId("ce-main");
         st.stage = buildStage(st);
         st.main.addChildren(new UIElement[]{st.menu, st.stage, st.panel});
         // 外观槽位放在 main 下面 = 这一页的最下面（main 是 flex:1，会自己吃满剩余高度）
         st.appearance = new UIElement().setId("ce-appearance");
         body.addChildren(new UIElement[]{topbar, titleLine, st.main, st.appearance, st.viewOnlyNote, st.sub});
         window.addChild(body);
         root.addChild(window);
         fillMenu(st);
         rebuild(st);
         String[] stamp = new String[]{dataStamp(st)};
         root.addEventListener("tick", event -> {
            if (OPEN_STATE == st) {
               pullFresh(st);
               if (st.dirty) {
                  st.dirty = false;
                  rebuild(st);
                  stamp[0] = dataStamp(st);
               } else {
                  String now = dataStamp(st);
                  if (!now.equals(stamp[0])) {
                     stamp[0] = now;
                     rebuild(st);
                  }
               }
            }
         });
         OPEN_STATE = st;
          return ModularUI.of(UI.of(root, new Stylesheet[]{stylesheet, configStylesheet}), player);
      } else {
         root.addChild(new Label().setText(Component.translatable("gui.minegenshin.artifact_equip.no_character")));
         return ModularUI.of(UI.of(root, new Stylesheet[]{stylesheet}), player);
      }
   }

   private static void fillStrip(CharacterEquipUI.State st) {
      st.strip.clearAllChildren();
      st.avatarButtons.clear();
      PGCharacter viewed = st.character;

      for (PGCharacter member : st.attachment.getOwnedCharacters()) {
         if (member != null) {
            boolean viewedNow = viewed != null && member.getCharacterUUID() == viewed.getCharacterUUID();
            UIElement avatar = new UIElement().addClass("ce-avatar");
            avatar.style(s -> s.background(SpriteTexture.of(avatarTexture(member))));
            if (viewedNow) {
               avatar.addClass("ce-avatar-viewed");
            } else {
               int uuid = member.getCharacterUUID();
               avatar.addEventListener("mouseClick", event -> viewCharacter(st, uuid));
            }

            st.avatarButtons.add(avatar);
            st.strip.addChild(avatar);
         }
      }
   }

   private static void viewCharacter(CharacterEquipUI.State st, int uuid) {
      PGCharacter target = st.attachment.getCharacterByUUID(uuid);
      if (target != null) {
         if (st.character == null || target.getCharacterUUID() != st.character.getCharacterUUID()) {
            int partyIdx = partyIndexOf(st.attachment, uuid);
            NetworkManager.sendSetEditTargetToServer(uuid);
            st.partyIndex = partyIdx;
            st.character = target;
            clearSelection(st);
            st.artifactSlot = 0;
            resetSubPageState(st);
            st.orbX = (float[])ORB_DEFAULT_X.clone();
            st.orbY = (float[])ORB_DEFAULT_Y.clone();
            fillStrip(st);
            rebuild(st);
         }
      }
   }

   private static void resetSubPageState(CharacterEquipUI.State st) {
      st.matSource = -1;
      st.matIndex = -1;
      st.matCount = 1;
      st.matExpValue = 0;
      st.sel = null;
   }

   private static int partyIndexOf(PlayerCharactersAttachment attachment, int uuid) {
      for (int i = 0; i < 4; i++) {
         PGCharacter member = attachment.getPartyCharacter(i);
         if (member != null && member.getCharacterUUID() == uuid) {
            return i;
         }
      }

      return -1;
   }

   private static boolean isViewOnly(CharacterEquipUI.State st) {
      return false;
   }

   private static String avatarTexture(PGCharacter character) {
      return "minegenshin:character/" + character.getTextureId() + "/textures/avatar_hud.png";
   }

   private static void fillMenu(CharacterEquipUI.State st) {
      st.menu.clearAllChildren();

      for (CharacterEquipUI.Page page : CharacterEquipUI.Page.values()) {
         CustomToggle tab = new CustomToggle();
         tab.addClass("ce-tab");
         tab.setButtonText(Component.literal("◆ ").append(Component.translatable(page.key())));
         tab.layout(l -> l.height(15.0F));
         tab.setOn(page == st.page, false);
         tab.setOnToggleChanged(on -> {
            if (st.page == page) {
               if (!on) {
                  tab.setOn(true, false);
               }
            } else if (on) {
               st.page = page;
               st.subPage = CharacterEquipUI.SubPage.NONE;
               rebuild(st);
            }
         });
         st.tabs.put(page, tab);
         st.menu.addChild(tab);
      }
   }

   private static void stepCharacter(CharacterEquipUI.State st, int delta) {
      List<PGCharacter> owned = st.attachment.getOwnedCharacters();
      if (owned != null && owned.size() >= 2 && st.character != null) {
         int current = -1;

         for (int i = 0; i < owned.size(); i++) {
            PGCharacter member = owned.get(i);
            if (member != null && member.getCharacterUUID() == st.character.getCharacterUUID()) {
               current = i;
               break;
            }
         }

         if (current >= 0) {
            int next = Math.floorMod(current + delta, owned.size());
            PGCharacter target = owned.get(next);
            if (target != null) {
               viewCharacter(st, target.getCharacterUUID());
            }
         }
      }
   }

   private static UIElement buildStage(CharacterEquipUI.State st) {
      UIElement stage = new UIElement().setId("ce-stage");
      Scene scene = new Scene();
      scene.setId("ce-scene");
      scene.setOverflowVisible(true);
      stage.addChild(scene);
      st.orbLayer = new UIElement().setId("ce-orb-layer");
      st.orbLayer.setAllowHitTest(false);
      st.orbLayer.layout(l -> l.positionType(TaffyPosition.ABSOLUTE).left(0.0F).top(0.0F).widthPercent(100.0F).heightPercent(100.0F));
      stage.addChild(st.orbLayer);
      Label credit = new Label();
      credit.setId("ce-stage-credit");
      credit.addClass("ce-credit");
      credit.layout(l -> l.height(10.0F));
      credit.addEventListener("mouseClick", event -> {
         String url = previewAuthorUrl(st);
         if (url != null) {
            Util.getPlatform().openUri(url);
         }
      });
      stage.addChild(credit);
      st.previewCredit = credit;
      Label hint = new Label();
      hint.setId("ce-stage-hint");
      hint.setText(Component.translatable("gui.minegenshin.character_equip.hint"));
      hint.layout(l -> l.height(9.0F));
      stage.addChild(hint);
      st.hint = hint;
      stage.addEventListener("mouseMove", event -> {
         if (st.orbDragSlot >= 0) {
            if (!stage.isMouseDown(0)) {
               st.orbDragSlot = -1;
            } else {
               float w = st.orbLayer.getSizeWidth();
               float h = st.orbLayer.getSizeHeight();
               if (!(w <= 0.0F) && !(h <= 0.0F)) {
                  float dx = event.x - st.orbDragStartX;
                  float dy = event.y - st.orbDragStartY;
                  if (Math.abs(dx) > 3.0F || Math.abs(dy) > 3.0F) {
                     st.orbDragMoved = true;
                  }

                  int slot = st.orbDragSlot;
                  st.orbX[slot] = clamp(st.orbDragStartNx + dx / w, 0.05F, 0.95F);
                  st.orbY[slot] = clamp(st.orbDragStartNy + dy / h, 0.06F, 0.94F);
                  UIElement orb = st.orbBySlot[slot];
                  if (orb != null) {
                     float nx = st.orbX[slot];
                     float ny = st.orbY[slot];
                     orb.layout(l -> l.leftPercent(nx * 100.0F).topPercent(ny * 100.0F));
                  }
               }
            }
         }
      });
      stage.addEventListener("mouseUp", event -> {
         if (event.button == 0) {
            st.orbDragSlot = -1;
         }
      });
      ClientLevel level = Minecraft.getInstance().level;
      if (level == null) {
         return stage;
      }

      scene.createScene(level);
      scene.setCenter(new Vector3f(0.0F, 1.2F, 0.0F));
      scene.setZoom(3.1F);
      scene.setCameraYawAndPitch(115.0F, 12.0F);
      scene.setShowHoverBlockTips(false);
      scene.setRenderSelect(false);
      scene.setRenderFacing(false);
      WorldSceneRenderer renderer = (WorldSceneRenderer)scene.getRenderer();
      if (renderer == null) {
         return stage;
      }

      renderer.setAfterBuiltinSubmit(ctx -> renderPreview(st, ctx));
      return stage;
   }

   private static float clamp(float value, float min, float max) {
      return Math.max(min, Math.min(max, value));
   }

   private static String elementName(@Nullable GenshinElement element) {
      return element == null ? "-" : I18n.get("minegenshin.configuration.elemental." + element.getId() + "_color", new Object[0]);
   }

   private static void refreshOrbs(CharacterEquipUI.State st) {
      if (st.orbLayer != null) {
         st.orbLayer.clearAllChildren();
         st.orbLayer.layout(l -> l.display(TaffyDisplay.NONE));
      }
   }

   private static String slotKey(int slot) {
      return switch (slot) {
         case 0 -> "gui.minegenshin.character_equip.slot.flower";
         case 1 -> "gui.minegenshin.character_equip.slot.plume";
         case 2 -> "gui.minegenshin.character_equip.slot.sands";
         case 3 -> "gui.minegenshin.character_equip.slot.goblet";
         case 4 -> "gui.minegenshin.character_equip.slot.circlet";
         default -> "gui.minegenshin.artifact_equip.weapon";
      };
   }

   private static String partIcon(int slot) {
      return switch (slot) {
         case 0 -> "minegenshin:gui/character_equip/part_flower.png";
         case 1 -> "minegenshin:gui/character_equip/part_plume.png";
         case 2 -> "minegenshin:gui/character_equip/part_sands.png";
         case 3 -> "minegenshin:gui/character_equip/part_goblet.png";
         case 4 -> "minegenshin:gui/character_equip/part_circlet.png";
         default -> "minegenshin:gui/character_equip/part_flower.png";
      };
   }

   private static void renderPreview(CharacterEquipUI.State st, SceneRenderContext ctx) {
      PGCharacter viewed = st.character;
      if (viewed != null && st.previewAnimatable != null) {
         String charId = viewed.getTextureId();
         CharacterRenderData data = CharacterRenderRepository.get(charId);
         PoseStack poseStack = ctx.poseStack();
         poseStack.pushPose();

         try {
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            if (data == null || !data.isValid()) {
               return;
            }

            CharacterRenderDispatcher.RenderTarget target = CharacterRenderDispatcher.targetFor(st.player, charId, data);
            if (target != null) {
               st.previewAnimatable.setPlayerEntity(st.player);
               BoneUpdater<GeoRenderState> bones = CharacterRenderDispatcher.combine(
                  CharacterAppearanceBones.forMask(viewed.getAppearance()), CharacterPropBones.hideAllUpdater()
               );
               bones = CharacterRenderDispatcher.combine(bones, CharacterAppearanceOptionBones.updaterForPreview(viewed.getAppearance(), viewed));
               bones = CharacterRenderDispatcher.combine(
                  bones,
                  CharacterRenderDispatcher.combine(CharacterFaceBones.updaterForState(st.previewAnimationName), CharacterPuppetBones.updaterFor(st.player))
               );
               target.renderer()
                  .performRenderPass(st.previewAnimatable, st.player, poseStack, ctx.submitStorage(), ctx.cameraState(), 15728880, ctx.partialTicks(), bones);
               return;
            }
         } finally {
            poseStack.popPose();
         }
      }
   }

   @Nullable
   private static String previewAnimationFor(CharacterEquipUI.State st) {
      String id = st.character == null ? null : st.character.getTextureId();
      if (st.page == CharacterEquipUI.Page.ARTIFACT && AnimationAvailability.exists(id, "extra_equip")) {
         return "extra_equip";
      } else if (AnimationAvailability.exists(id, "extra48")) {
         return "extra48";
      } else {
         return AnimationAvailability.exists(id, "idle") ? "idle" : null;
      }
   }

   private static void ensurePreviewAnimatable(CharacterEquipUI.State st) {
      String want = previewAnimationFor(st);
      String charId = st.character == null ? null : st.character.getTextureId();
      boolean sameAnimation = want == null ? st.previewAnimationName == null : want.equals(st.previewAnimationName);
      boolean sameCharacter = charId == null ? st.previewCharacterId == null : charId.equals(st.previewCharacterId);
      if (st.previewAnimatable == null || !sameAnimation || !sameCharacter) {
         st.previewAnimatable = new GenshinPreviewPlayer(want);
         st.previewAnimationName = want;
         st.previewCharacterId = charId;
      }
   }

   @Nullable
   private static PGCharacter viewed(CharacterEquipUI.State st) {
      if (st.character == null) {
         return null;
      }

      PGCharacter fresh = st.attachment.getCharacterByUUID(st.character.getCharacterUUID());
      if (fresh != null) {
         st.character = fresh;
      }

      return st.character;
   }

   private static void pullFresh(CharacterEquipUI.State st) {
      PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)st.player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
      if (attachment != null) {
         st.attachment = attachment;
      }

      st.backpack = (Backpack)st.player.getData(AttachmentRegistration.BACKPACK_ATTACHMENT);
   }

   private static void markDirty(CharacterEquipUI.State st) {
      st.dirty = true;
   }

   private static void resolveSelection(CharacterEquipUI.State st) {
      CharacterEquipUI.Owned sel = st.sel;
      if (sel != null) {
         int slot = Math.max(0, Math.min(ARTIFACT_TYPES.length - 1, st.artifactSlot));

         for (CharacterEquipUI.Owned candidate : sel.stack().getItem() instanceof WeaponItem ? ownWeapons(st) : ownArtifacts(st, slot)) {
            if (candidate.source() == sel.source() && candidate.index() == sel.index()) {
               st.sel = candidate;
               return;
            }
         }

         st.sel = null;
      }
   }

   private static void rebuild(CharacterEquipUI.State st) {
      pullFresh(st);
      resolveSelection(st);
      if (st.main != null && viewed(st) != null && st.character.getData() != null) {
         st.tabs.forEach((page, tab) -> tab.setOn(page == st.page, false));
         st.viewOnlyNote.layout(l -> l.display(isViewOnly(st) ? TaffyDisplay.FLEX : TaffyDisplay.NONE));
         fillStrip(st);
         ensurePreviewAnimatable(st);
         boolean sub = st.subPage != CharacterEquipUI.SubPage.NONE;
         st.main.layout(l -> l.display(sub ? TaffyDisplay.NONE : TaffyDisplay.FLEX));
         st.sub.layout(l -> l.display(sub ? TaffyDisplay.FLEX : TaffyDisplay.NONE));
         if (st.appearance != null) {
            // 进子页面（选武器 / 选圣遗物 / 升级）时把外观也收起来，和 main 一致
            st.appearance.layout(l -> l.display(sub ? TaffyDisplay.NONE : TaffyDisplay.FLEX));
         }
         if (sub) {
            st.sub.clearAllChildren();
            switch (st.subPage) {
               case ARTIFACT_CHANGE:
                  buildArtifactChangePage(st);
                  break;
               case WEAPON_CHANGE:
                  buildWeaponChangePage(st);
                  break;
               default:
                  buildLevelUpPage(st);
            }

            refreshOrbs(st);
         } else {
            st.panel.clearAllChildren();
            st.bottomRight.clearAllChildren();
            st.detailCard = new UIElement().setId("ce-detail-card");
            st.detailCard.layout(l -> l.display(TaffyDisplay.NONE));
            switch (st.page) {
               case STATS:
                  buildStatsPage(st);
                  break;
               case WEAPON:
                  buildWeaponPage(st);
                  break;
               case ARTIFACT:
                  buildArtifactPage(st);
                  break;
               case CONSTELLATION:
                  buildConstellationPage(st);
                  break;
               case TALENT:
                  buildTalentPage(st);
                  break;
               case PROFILE:
                  buildProfilePage(st);
            }

            st.panel.addChild(st.detailCard);
            st.panel.addChild(st.bottomRight);
            fillPreviewCredit(st);
            fillHint(st);
            fillAppearanceSection(st);
            refreshOrbs(st);
         }
      }
   }

   /**
    * 最下面那块「外观」—— 跟 K 页右侧那块同源：标题 + 这个角色可调的装扮项
    * （申鹤是腿 / 鞋 / 袜 / 猫耳，林薇云是武器形态 + 显示武器……）。
    *
    * <p>内容由角色自己的配置页给（{@link ICharacterConfigUI#buildAppearanceSection}），
    * 所以两个界面天然一致；换角色、换页都会重建，不会串到上一个角色身上。
    */
   private static void fillAppearanceSection(CharacterEquipUI.State st) {
      if (st.appearance == null) {
         return;
      }
      st.appearance.clearAllChildren();
      PGCharacter character = viewed(st);
      ICharacterConfigUI configUI = character == null ? null : character.getConfigUI();
      if (configUI == null) {
         return;
      }
      st.appearance.addChild(configUI.buildAppearanceSection(st.player, character, new int[]{character.getAppearance()}));
   }

   private static void fillHint(CharacterEquipUI.State st) {
      if (st.hint != null) {
         Label var10000 = st.hint;
         switch (st.page) {
            default:
               var10000.setText(
                  Component.translatable(
                     "gui.minegenshin.character_equip.hint",
                     new Object[]{((KeyMapping)KeyMappingRegistry.CHARACTER_INFO_SCREEN_KEY.get()).getTranslatedKeyMessage()}
                  )
               );
         }
      }
   }

   @Nullable
   private static String previewAuthorUrl(CharacterEquipUI.State st) {
      CharacterRenderData render = st.character == null ? null : CharacterRenderRepository.get(st.character.getTextureId());
      return render == null ? null : render.modelAuthorUrl();
   }

   private static void fillPreviewCredit(CharacterEquipUI.State st) {
      if (st.previewCredit != null) {
         CharacterRenderData render = st.character == null ? null : CharacterRenderRepository.get(st.character.getTextureId());
         boolean hasAuthor = render != null && render.modelAuthor() != null;
         st.previewCredit.layout(l -> l.display(hasAuthor ? TaffyDisplay.FLEX : TaffyDisplay.NONE));
         if (hasAuthor) {
            st.previewCredit.setText(Component.translatable("gui.minegenshin.character_config.model_author", new Object[]{render.modelAuthor()}));
            st.previewCredit.removeClass("ce-link");
            if (previewAuthorUrl(st) != null) {
               st.previewCredit.addClass("ce-link");
            }
         }
      }
   }

   private static void openSubPage(CharacterEquipUI.State st, CharacterEquipUI.SubPage page) {
      st.subPage = page;
      if (page == CharacterEquipUI.SubPage.LEVEL_UP) {
         st.matSource = -1;
         st.matIndex = -1;
         st.matCount = 1;
         st.matExpValue = 0;
      } else {
         clearSelection(st);
      }

      rebuild(st);
   }

   private static void buildStatsPage(CharacterEquipUI.State st) {
      PGCharacterData data = st.character.getData();
      st.panel.addChild(characterNamePlate(st));
      int cap = levelCap(data.getAscensionPhase());
      UIElement levelRow = new UIElement().addClass("ce-row");
      levelRow.addChildren(
         new UIElement[]{
            label(Component.translatable("gui.minegenshin.character_equip.level_format", new Object[]{data.getLevel(), cap}), "ce-level"),
            label(Component.translatable("gui.minegenshin.character_equip.exp", new Object[]{data.getCurrentExp(), data.getMaxExp()}), "ce-exp-num")
         }
      );
      st.panel.addChild(levelRow);
      st.panel.addChild(expBar(data.getCurrentExp() / Math.max(1.0, data.getMaxExp())));
      ScrollerView scroller = newScroller("ce-stats-scroller");
      UIElement list = new UIElement().setId("ce-stats-list");
      list.addChild(baseStatRow(st, (AttributeType)ModAttributes.MAX_HP.value()));
      list.addChild(baseStatRow(st, (AttributeType)ModAttributes.ATK.value()));
      list.addChild(baseStatRow(st, (AttributeType)ModAttributes.DEF.value()));
      list.addChild(valueStatRow(st, (AttributeType)ModAttributes.ELEMENTAL_MASTERY.value()));
      list.addChild(valueStatRow(st, (AttributeType)ModAttributes.MAX_STAMINA.value()));
      if (st.showDetails) {
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
            list.addChild(percentStatRow(st, type));
         }
      }

      scroller.addScrollViewChild(list);
      st.panel.addChild(scroller);
      st.panel
         .addChild(
            actionButton(
               Component.translatable(st.showDetails ? "gui.minegenshin.character_equip.detail_less" : "gui.minegenshin.character_equip.detail_info"),
               true,
               () -> {
                  st.showDetails = !st.showDetails;
                  rebuild(st);
               }
            )
         );
      st.panel.addChild(friendshipRow());
      Label profile = new Label();
      profile.addClass("ce-story");
      profile.setText(
         Component.translatable("gui.minegenshin.character_equip.profile_intro", new Object[]{st.character.getName(), elementName(st.character.getElemental())})
      );
      profile.layout(l -> l.widthPercent(100.0F));
      st.panel.addChild(profile);
      boolean viewOnly = isViewOnly(st);
      boolean canAscend = data.getAscensionPhase() < 6 && data.getLevel() >= cap;
      st.bottomRight
         .addChildren(
            new UIElement[]{
               actionButton(Component.translatable("gui.minegenshin.character_equip.level_up"), !viewOnly, () -> openLevelUpPage(st, -1)),
               actionButton(Component.translatable("gui.minegenshin.character_equip.ascend"), !viewOnly && canAscend, () -> {
                  NetworkManager.sendAscendCharacterToServer();
                  markDirty(st);
               })
            }
         );
   }

   private static UIElement characterNamePlate(CharacterEquipUI.State st) {
      UIElement box = new UIElement().addClass("ce-nameplate");
      UIElement line = new UIElement().addClass("ce-row");
      line.addChild(label(st.character.getName(), "ce-nameplate-name"));
      GenshinElement element = st.character.getElemental();
      if (element != null) {
         UIElement icon = new UIElement().addClass("ce-elem-sm");
         icon.style(s -> s.background(SpriteTexture.of("minegenshin:icon/elemental/" + element.getId() + ".png")));
         line.addChild(icon);
      }

      box.addChild(line);
      box.addChild(label(Component.literal("★".repeat(Math.max(1, st.character.getStarRating()))), "ce-star"));
      return box;
   }

   private static UIElement friendshipRow() {
      UIElement row = new UIElement().addClass("ce-row");
      Label name = label(Component.translatable("gui.minegenshin.character_equip.friendship"), "ce-name-sm");
      UIElement bar = expBar(0.0);
      bar.addClass("ce-bar-inrow");
      row.addChildren(new UIElement[]{name, bar, label(Component.literal("—"), "ce-value-dim")});
      return row;
   }

   private static void buildWeaponPage(CharacterEquipUI.State st) {
      PGCharacterData data = st.character.getData();
      ItemStack equipped = data.getWeapon();
      st.panel.addChild(weaponSummary(equipped));
      boolean viewOnly = isViewOnly(st);
      WeaponStatsComponent stats = equipped.isEmpty()
         ? null
         : (WeaponStatsComponent)equipped.getOrDefault((DataComponentType)ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT);
      boolean hasWeapon = !equipped.isEmpty() && stats != null;
      st.bottomRight
         .addChildren(
            new UIElement[]{
               actionButton(
                  Component.translatable("gui.minegenshin.character_equip.change"), !viewOnly, () -> openSubPage(st, CharacterEquipUI.SubPage.WEAPON_CHANGE)
               ),
               actionButton(Component.translatable("gui.minegenshin.character_equip.level_up"), !viewOnly && hasWeapon, () -> openLevelUpPage(st, -2)),
               actionButton(Component.translatable("gui.minegenshin.character_equip.ascend"), !viewOnly && hasWeapon && stats.canAscend(), () -> {
                  NetworkManager.sendAscendWeaponToServer();
                  markDirty(st);
               }),
               actionButton(Component.translatable("gui.minegenshin.character_equip.unequip"), !viewOnly && hasWeapon, () -> {
                  NetworkManager.sendUnequipArtifactToServer(weaponSlot(st));
                  clearSelection(st);
                  markDirty(st);
               })
            }
         );
   }

   private static void buildWeaponChangePage(CharacterEquipUI.State st) {
      st.sub.addChild(subHeader(st, Component.translatable("gui.minegenshin.character_equip.weapon_change_title")));
      UIElement body = new UIElement().setId("ce-sub-body");
      List<CharacterEquipUI.Owned> items = ownWeapons(st);
      ScrollerView left = newScroller("ce-weapon-change-scroller");
      left.addScrollViewChild(bigGrid(items, 8, st));
      body.addChild(left);
      UIElement right = new UIElement().setId("ce-change-right");
      right.layout(l -> l.width(196.0F).flexShrink(0.0F));
      ItemStack sel = st.sel == null ? ItemStack.EMPTY : st.sel.stack();
      right.addChild(weaponSummary(sel));
      boolean viewOnly = isViewOnly(st);
      boolean sameSlot = st.sel != null && st.sel.source() != CharacterEquipUI.Source.EQUIPPED;
      UIElement buttons = buttonRow();
      buttons.addChild(
         actionButton(
            Component.translatable("gui.minegenshin.character_equip.equip"),
            !viewOnly && sameSlot && sel.getItem() instanceof WeaponItem,
            () -> equip(st, weaponSlot(st))
         )
      );
      right.addChild(buttons);
      body.addChild(right);
      st.sub.addChild(body);
   }

   private static void buildArtifactPage(CharacterEquipUI.State st) {
      PGCharacterData data = st.character.getData();
      ArtifactInventory inv = data.getArtifactInventory();
      UIElement slots = new UIElement().addClass("ce-slot-row");

      for (int i = 0; i < ARTIFACT_TYPES.length; i++) {
         int slot = i;
         ItemStack equipped = inv.getItem(slot);
         UIElement cell = new UIElement().addClass("ce-slot");
         if (!equipped.isEmpty()) {
            cell.style(s -> s.background(SpriteTexture.of(ItemIcons.pathOf(equipped))));
         } else {
            cell.addClass("ce-slot-dim");
         }

         if (slot == st.artifactSlot) {
            cell.addClass("ce-slot-selected");
         }

         cell.addEventListener("mouseClick", event -> {
            st.artifactSlot = slot;
            clearSelection(st);
            rebuild(st);
         });
         slots.addChild(cell);
      }

      st.panel.addChild(slots);
      ScrollerView scroller = newScroller("ce-artifact-detail");
      UIElement box = new UIElement().setId("ce-artifact-detail-box");
      box.addChild(baseStatRow(st, (AttributeType)ModAttributes.MAX_HP.value()));
      box.addChild(baseStatRow(st, (AttributeType)ModAttributes.ATK.value()));
      box.addChild(baseStatRow(st, (AttributeType)ModAttributes.DEF.value()));
      box.addChild(valueStatRow(st, (AttributeType)ModAttributes.ELEMENTAL_MASTERY.value()));
      box.addChild(sectionTitle("gui.minegenshin.character_equip.set_effect"));
      box.addChild(note("gui.minegenshin.character_equip.set_effect_none"));
      box.addChild(sectionTitle(slotKey(st.artifactSlot)));
      box.addChild(artifactDetail(inv.getItem(st.artifactSlot), st.artifactSlot));
      scroller.addScrollViewChild(box);
      st.panel.addChild(scroller);
      boolean viewOnly = isViewOnly(st);
      boolean hasArtifact = !inv.getItem(st.artifactSlot).isEmpty();
      st.bottomRight
         .addChildren(
            new UIElement[]{
               actionButton(
                  Component.translatable("gui.minegenshin.character_equip.change"), true, () -> openSubPage(st, CharacterEquipUI.SubPage.ARTIFACT_CHANGE)
               ),
               actionButton(Component.translatable("gui.minegenshin.character_equip.unequip"), !viewOnly && hasArtifact, () -> {
                  NetworkManager.sendUnequipArtifactToServer(st.artifactSlot);
                  markDirty(st);
               }),
               actionButton(
                  Component.translatable("gui.minegenshin.character_equip.level_up"), !viewOnly && hasArtifact, () -> openLevelUpPage(st, st.artifactSlot)
               )
            }
         );
   }

   private static void buildProfilePage(CharacterEquipUI.State st) {
      PGCharacter character = st.character;
      PGCharacterData data = character.getData();
      GenshinElement element = character.getElemental();
      st.panel.addChild(characterNamePlate(st));
      ScrollerView scroller = newScroller("ce-profile-scroller");
      UIElement list = new UIElement().setId("ce-profile-list");
      list.addChild(sectionTitle("gui.minegenshin.character_equip.page.profile"));
      list.addChild(infoRow("gui.minegenshin.character_equip.info_element", Component.literal(elementName(element))));
      list.addChild(
         infoRow(
            "gui.minegenshin.character_equip.info_level",
            Component.translatable("gui.minegenshin.character_equip.level_format", new Object[]{data.getLevel(), levelCap(data.getAscensionPhase())})
         )
      );
      list.addChild(infoRow("gui.minegenshin.character_equip.info_ascension", Component.literal(String.valueOf(data.getAscensionPhase()))));
      list.addChild(
         infoRow(
            "gui.minegenshin.character_equip.info_constellation",
            Component.translatable("gui.minegenshin.character_equip.constellation_count", new Object[]{character.getConstellation(), 6})
         )
      );
      list.addChild(sectionTitle("gui.minegenshin.character_equip.model"));
      CharacterRenderData render = CharacterRenderRepository.get(character.getTextureId());
      if (render != null && render.modelAuthor() != null) {
         Label credit = new Label();
         credit.addClass("ce-note");
         credit.setText(Component.translatable("gui.minegenshin.character_config.model_author", new Object[]{render.modelAuthor()}));
         credit.layout(l -> l.height(11.0F));
         if (render.modelAuthorUrl() != null) {
            credit.addClass("ce-link");
            String url = render.modelAuthorUrl();
            credit.addEventListener("mouseClick", event -> Util.getPlatform().openUri(url));
         }

         list.addChild(credit);
      } else if (render != null) {
         list.addChild(note("gui.minegenshin.character_equip.model_none"));
      } else {
         list.addChild(note("gui.minegenshin.character_equip.no_model"));
      }

      scroller.addScrollViewChild(list);
      st.panel.addChild(scroller);
   }

   private static int levelCap(int ascensionPhase) {
      return ascensionPhase <= 0 ? 20 : Math.min((ascensionPhase + 3) * 10, 90);
   }

   private static Label sectionTitle(String key) {
      Label title = new Label();
      title.addClass("ce-section");
      title.setText(Component.translatable(key));
      title.layout(l -> l.height(11.0F));
      return title;
   }

   private static Label note(String key) {
      Label label = new Label();
      label.addClass("ce-note");
      label.setText(Component.translatable(key));
      label.layout(l -> l.height(10.0F));
      return label;
   }

   private static UIElement infoRow(String key, Component value) {
      UIElement row = new UIElement().addClass("ce-row");
      row.addChildren(new UIElement[]{label(Component.translatable(key), "ce-name"), label(value, "ce-value")});
      return row;
   }

   private static Label label(Component text, String styleClass) {
      Label label = new Label();
      label.addClass(styleClass);
      label.setText(text);
      label.layout(l -> l.height(11.0F));
      return label;
   }

   private static Label wrapLabel(Component text) {
      Label label = new Label();
      label.addClass("ce-text");
      label.setText(text);
      return label;
   }

   private static Label line(String key, String styleClass, Object... args) {
      return line(args.length == 0 ? Component.translatable(key) : Component.translatable(key, args), styleClass);
   }

   private static Label line(Component text, String styleClass) {
      Label label = new Label();
      label.addClass(styleClass);
      label.setText(text);
      label.layout(l -> l.widthPercent(100.0F));
      return label;
   }

   private static UIElement expBar(double ratio) {
      return expBar(ratio, ratio).track();
   }

   private static CharacterEquipUI.ExpBar expBar(double ratio, double previewRatio) {
      UIElement track = new UIElement().addClass("ce-bar-track");
      UIElement fill = new UIElement().addClass("ce-bar-fill");
      fill.layout(l -> l.widthPercent(percentOf(ratio)));
      UIElement ghost = new UIElement().addClass("ce-bar-ghost");
      track.addChildren(new UIElement[]{fill, ghost});
      CharacterEquipUI.ExpBar bar = new CharacterEquipUI.ExpBar(track, ghost);
      bar.setPreview(ratio, previewRatio);
      return bar;
   }

   private static float percentOf(double ratio) {
      return (float)Math.max(0.0, Math.min(1.0, ratio)) * 100.0F;
   }

   private static UIElement buttonRow() {
      return new UIElement().addClass("ce-row");
   }

   private static Button actionButton(Component text, boolean enabled, Runnable action) {
      Button button = new Button();
      button.addClass("ce-action");
      if (!enabled) {
         button.addClass("ce-action-off");
      }

      button.setText(text);
      button.layout(l -> l.height(16.0F).minWidth(46.0F));
      button.setOnClick(event -> {
         if (enabled) {
            action.run();
         }
      });
      return button;
   }

   private static ScrollerView newScroller(String id) {
      ScrollerView scroller = new ScrollerView();
      scroller.setId(id);
      scroller.addClass("ce-scroller");
      scroller.scrollerStyle(s -> s.mode(ScrollerMode.VERTICAL).verticalScrollDisplay(ScrollDisplay.AUTO).horizontalScrollDisplay(ScrollDisplay.NEVER));
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

   private static void buildConstellationPage(CharacterEquipUI.State st) {
      PGCharacter character = st.character;
      int owned = character.getConstellation();
      Label count = new Label();
      count.addClass("ce-note");
      count.setText(Component.translatable("gui.minegenshin.character_equip.constellation_count", new Object[]{owned, 6}));
      count.layout(l -> l.height(10.0F));
      st.panel.addChild(count);
      UIElement listBox = new UIElement().setId("ce-con-list-box");
      ScrollerView scroller = newScroller("ce-con-scroller");
      UIElement list = new UIElement().setId("ce-con-list").addClass("ce-scroll-body");

      for (int n = 1; n <= 6; n++) {
         list.addChild(constellationRow(st, n));
      }

      scroller.addScrollViewChild(list);
      listBox.addChild(scroller);
      st.panel.addChild(listBox);
      int n = clampConstellation(st.selConstellation);
      boolean unlocked = character.hasConstellation(n);
      st.detailCard.layout(l -> l.display(TaffyDisplay.FLEX));
      ScrollerView detailScroll = newScroller("ce-con-detail");
      UIElement detail = new UIElement().addClass("ce-detail");
      detail.addChild(line("gui.minegenshin.character_equip.constellation_level", "ce-note", n));
      detail.addChild(line(Component.literal(constellationName(character, n)), "ce-title-name"));
      detail.addChild(
         line(unlocked ? "gui.minegenshin.character_equip.unlocked" : "gui.minegenshin.character_equip.locked", unlocked ? "ce-value-dim" : "ce-note")
      );
      detail.addChild(wrapLabel(Component.literal(constellationDesc(character, n))));
      detailScroll.addScrollViewChild(detail);
      st.detailCard.addChild(detailScroll);
      boolean canUpgrade = false;
      st.bottomRight.addChild(actionButton(Component.translatable("gui.minegenshin.character_equip.constellation_up"), canUpgrade, () -> {}));
   }

   private static UIElement constellationRow(CharacterEquipUI.State st, int n) {
      boolean unlocked = st.character.hasConstellation(n);
      UIElement row = new UIElement().addClass("ce-list-row");
      if (n == clampConstellation(st.selConstellation)) {
         row.addClass("ce-list-row-on");
      }

      UIElement dot = new UIElement().addClass("ce-con-dot");
      if (unlocked) {
         dot.addClass("ce-con-dot-on");
      }

      Label name = new Label();
      name.addClass(unlocked ? "ce-con-name-on" : "ce-con-name");
      name.setText(Component.literal(constellationName(st.character, n)));
      name.layout(l -> l.height(11.0F));
      row.addChildren(new UIElement[]{dot, name});
      row.addEventListener("mouseClick", event -> {
         st.selConstellation = n;
         rebuild(st);
      });
      return row;
   }

   private static int clampConstellation(int value) {
      return Math.max(1, Math.min(6, value));
   }

   private static String constellationName(PGCharacter character, int n) {
      String id = character.getTextureId();
      return tr(
         "gui.minegenshin.character_equip.constellation." + id + "." + n + ".name",
         "constellation.minegenshin." + id + "." + n + ".name",
         "gui.minegenshin.character_equip.constellation." + n
      );
   }

   private static String constellationDesc(PGCharacter character, int n) {
      String id = character.getTextureId();
      return tr(
         "gui.minegenshin.character_equip.constellation." + id + "." + n + ".desc",
         "constellation.minegenshin." + id + "." + n + ".desc",
         "constellation.minegenshin." + id + "." + n + ".applied",
         "gui.minegenshin.character_equip.constellation_desc_none"
      );
   }

   private static void buildTalentPage(CharacterEquipUI.State st) {
      UIElement listBox = new UIElement().setId("ce-talent-list-box");
      ScrollerView scroller = newScroller("ce-talent-scroller");
      UIElement list = new UIElement().setId("ce-talent-list").addClass("ce-scroll-body");

      for (int kind = 0; kind <= 4; kind++) {
         list.addChild(talentRow(st, kind));
      }

      scroller.addScrollViewChild(list);
      listBox.addChild(scroller);
      st.panel.addChild(listBox);
      int kind = clampTalent(st.selTalent);
      boolean unlocked = talentUnlocked(st, kind);
      boolean active = kind <= 2;
      st.detailCard.layout(l -> l.display(TaffyDisplay.FLEX));
      UIElement card = new UIElement().addClass("ce-detail");
      card.addChild(line(talentKindKey2(kind), "ce-note"));
      card.addChild(line(Component.literal(talentName(st.character, kind)), "ce-title-name"));
      if (active) {
         card.addChild(line("gui.minegenshin.character_equip.talent_level", "ce-value-dim", talentLevel(st, kind), talentLevelCap(st, kind)));
      }

      UIElement tabs = buttonRow();
      CustomToggle intro = new CustomToggle();
      intro.addClass("ce-subtab");
      intro.setButtonText(Component.translatable("gui.minegenshin.character_equip.talent_tab_intro"));
      intro.layout(l -> l.height(14.0F).flex(1.0F));
      intro.setOn(st.talentTab == 0, false);
      intro.setOnToggleChanged(on -> {
         if (on && st.talentTab != 0) {
            st.talentTab = 0;
            rebuild(st);
         } else if (!on && st.talentTab == 0) {
            intro.setOn(true, false);
         }
      });
      CustomToggle detailTab = new CustomToggle();
      detailTab.addClass("ce-subtab");
      detailTab.setButtonText(Component.translatable("gui.minegenshin.character_equip.talent_tab_detail"));
      detailTab.layout(l -> l.height(14.0F).flex(1.0F));
      detailTab.setOn(st.talentTab == 1, false);
      detailTab.setOnToggleChanged(on -> {
         if (on && st.talentTab != 1) {
            st.talentTab = 1;
            rebuild(st);
         } else if (!on && st.talentTab == 1) {
            detailTab.setOn(true, false);
         }
      });
      tabs.addChildren(new UIElement[]{intro, detailTab});
      card.addChild(tabs);
      ScrollerView detailScroll = newScroller("ce-talent-detail");
      UIElement detail = new UIElement();
      if (st.talentTab == 0) {
         detail.addChild(
            line(unlocked ? "gui.minegenshin.character_equip.unlocked" : "gui.minegenshin.character_equip.locked", unlocked ? "ce-value-dim" : "ce-note")
         );
         detail.addChild(wrapLabel(Component.literal(talentDesc(st.character, kind))));
      } else {
         detail.addChild(
            line("gui.minegenshin.character_equip.talent_level", "ce-value-dim", active ? talentLevel(st, kind) : 0, active ? talentLevelCap(st, kind) : 0)
         );
         detail.addChild(wrapLabel(Component.translatable("gui.minegenshin.character_equip.talent_desc_none")));
      }

      detailScroll.addScrollViewChild(detail);
      card.addChild(detailScroll);
      if (active && talentCanUpgrade(st, kind)) {
         int manual = manualTalentUpgrades(st, kind);
         card.addChild(
            line("gui.minegenshin.character_equip.upgrade_cost", "ce-cost-line", TalentUpgradeCost.primogem(manual), TalentUpgradeCost.experienceLevels(manual))
         );
      }

      st.detailCard.addChild(card);
      boolean canUpgrade = !isViewOnly(st) && talentCanUpgrade(st, kind);
      st.bottomRight.addChild(actionButton(Component.translatable("gui.minegenshin.character_equip.level_up"), canUpgrade, () -> upgradeTalent(st, kind)));
   }

   private static String talentKindKey2(int kind) {
      return "gui.minegenshin.character_equip.talent.kind." + talentKindKey(kind);
   }

   private static UIElement talentRow(CharacterEquipUI.State st, int kind) {
      boolean unlocked = talentUnlocked(st, kind);
      UIElement row = new UIElement().addClass("ce-list-row");
      if (kind == clampTalent(st.selTalent)) {
         row.addClass("ce-list-row-on");
      }

      UIElement dot = new UIElement().addClass("ce-con-dot");
      if (unlocked) {
         dot.addClass("ce-con-dot-on");
      }

      Label name = new Label();
      name.addClass(unlocked ? "ce-con-name-on" : "ce-con-name");
      name.setText(Component.literal(talentName(st.character, kind)));
      name.layout(l -> l.height(11.0F));
      row.addChildren(new UIElement[]{dot, name});
      if (kind <= 2) {
         row.addChild(label(Component.literal(String.valueOf(talentLevel(st, kind))), "ce-level-num"));
      }

      row.addEventListener("mouseClick", event -> {
         st.selTalent = kind;
         rebuild(st);
      });
      return row;
   }

   private static int clampTalent(int value) {
      return Math.max(0, Math.min(4, value));
   }

   private static int talentLevel(CharacterEquipUI.State st, int kind) {
      PGCharacterData data = st.character.getData();

      return switch (kind) {
         case 1 -> data.getElementalSkillLevel();
         case 2 -> data.getElementalBurstLevel();
         default -> data.getNormalAttackLevel();
      };
   }

   private static int talentLevelCap(CharacterEquipUI.State st, int kind) {
      PGCharacterData data = st.character.getData();

      return switch (kind) {
         case 1 -> data.getElementalSkillLevelCap();
         case 2 -> data.getElementalBurstLevelCap();
         default -> data.getNormalAttackLevelCap();
      };
   }

   private static int manualTalentUpgrades(CharacterEquipUI.State st, int kind) {
      PGCharacterData data = st.character.getData();

      return switch (kind) {
         case 1 -> data.getManualElementalSkill();
         case 2 -> data.getManualElementalBurst();
         default -> data.getManualNormalAttack();
      };
   }

   private static boolean talentUnlocked(CharacterEquipUI.State st, int kind) {
      int phase = st.character.getData().getAscensionPhase();

      return switch (kind) {
         case 3 -> phase >= 1;
         case 4 -> phase >= 4;
         default -> true;
      };
   }

   private static boolean talentCanUpgrade(CharacterEquipUI.State st, int kind) {
      PGCharacterData data = st.character.getData();

      return switch (kind) {
         case 0 -> data.canUpgradeNormalAttack();
         case 1 -> data.canUpgradeElementalSkill();
         case 2 -> data.canUpgradeElementalBurst();
         default -> false;
      };
   }

   private static void upgradeTalent(CharacterEquipUI.State st, int kind) {
      switch (kind) {
         case 0:
            NetworkManager.sendUpgradeNormalAttackToServer();
            break;
         case 1:
            NetworkManager.sendUpgradeElementalSkillToServer();
            break;
         case 2:
            NetworkManager.sendUpgradeElementalBurstToServer();
      }

      markDirty(st);
   }

   private static String talentKindKey(int kind) {
      return switch (kind) {
         case 1 -> "skill";
         case 2 -> "burst";
         case 3 -> "passive1";
         case 4 -> "passive2";
         default -> "normal";
      };
   }

   private static String talentName(PGCharacter character, int kind) {
      String id = character.getTextureId();
      String kindKey = talentKindKey(kind);
      return tr(
         "gui.minegenshin.character_equip.talent." + id + "." + kindKey + ".name",
         "gui.minegenshin.character_equip.talent." + kindKey + ".name",
         "gui.minegenshin.character_equip.talent." + kindKey
      );
   }

   private static String talentDesc(PGCharacter character, int kind) {
      String id = character.getTextureId();
      String kindKey = talentKindKey(kind);
      return tr(
         "gui.minegenshin.character_equip.talent." + id + "." + kindKey + ".desc",
         "gui.minegenshin.character_equip.talent." + kindKey + ".desc",
         "gui.minegenshin.character_equip.talent_desc_none"
      );
   }

   private static String tr(String... keys) {
      for (String key : keys) {
         String value = I18n.get(key, new Object[0]);
         if (!value.equals(key)) {
            return value;
         }
      }

      return keys[keys.length - 1];
   }

   private static void buildArtifactChangePage(CharacterEquipUI.State st) {
      st.sub
         .addChild(
            subHeader(
               st, Component.translatable("gui.minegenshin.character_equip.change_title", new Object[]{Component.translatable(slotKey(st.artifactSlot))})
            )
         );
      UIElement body = new UIElement().setId("ce-sub-body");
      List<CharacterEquipUI.Owned> items = ownArtifacts(st, st.artifactSlot);
      ScrollerView left = newScroller("ce-change-scroller");
      left.addScrollViewChild(bigGrid(items, 8, st));
      body.addChild(left);
      UIElement right = new UIElement().setId("ce-change-right");
      right.layout(l -> l.width(196.0F).flexShrink(0.0F));
      ItemStack sel = st.sel == null ? ItemStack.EMPTY : st.sel.stack();
      right.addChild(artifactDetail(sel, st.artifactSlot));
      boolean viewOnly = isViewOnly(st);
      boolean sameSlot = st.sel != null && st.sel.source() != CharacterEquipUI.Source.EQUIPPED;
      boolean activated = isActivated(sel);
      UIElement buttons = buttonRow();
      buttons.addChild(
         actionButton(Component.translatable("gui.minegenshin.character_equip.equip"), !viewOnly && sameSlot && activated, () -> equip(st, st.artifactSlot))
      );
      buttons.addChild(
         actionButton(
            Component.translatable("gui.minegenshin.character_equip.activate"),
            !viewOnly
               && sameSlot
               && !activated
               && (st.sel.source() == CharacterEquipUI.Source.BACKPACK || st.sel.source() == CharacterEquipUI.Source.INVENTORY),
            () -> {
               if (st.sel.source() == CharacterEquipUI.Source.INVENTORY) {
                  NetworkManager.sendActivateInventoryArtifactToServer(st.sel.index());
               } else {
                  NetworkManager.sendActivateArtifactToServer(st.sel.index());
               }

               markDirty(st);
            }
         )
      );
      right.addChild(buttons);
      body.addChild(right);
      st.sub.addChild(body);
   }

   private static void buildLevelUpPage(CharacterEquipUI.State st) {
      st.sub
         .addChild(subHeader(st, Component.translatable("gui.minegenshin.character_equip.level_up_title", new Object[]{levelTargetName(st, st.levelTarget)})));
      UIElement body = new UIElement().setId("ce-sub-body");
      List<CharacterEquipUI.Mat> mats = levelMaterials(st);
      ScrollerView left = newScroller("ce-mat-scroller");
      UIElement matList = new UIElement().setId("ce-mat-list").addClass("ce-scroll-body");
      if (mats.isEmpty()) {
         matList.addChild(note("gui.minegenshin.character_equip.no_material"));
      } else {
         for (CharacterEquipUI.Mat mat : mats) {
            matList.addChild(materialRow(st, mat));
         }
      }

      left.addScrollViewChild(matList);
      body.addChild(left);
      UIElement right = new UIElement().addClass("ce-detail");
      right.layout(l -> l.width(200.0F).flexShrink(0.0F));
      Label afterLevel = label(Component.literal(""), "ce-value-extra");
      Label afterDetail = label(Component.literal(""), "ce-story");
      UIElement statPreview = new UIElement().addClass("ce-stat-preview");
      CharacterEquipUI.ExpBar bar = levelBar(st);
      Runnable refresh = () -> {
         refreshLevelUpPreview(st, afterLevel, afterDetail);
         fillStatPreview(st, statPreview);
         double[] ratios = levelBarRatios(st);
         bar.setPreview(ratios[0], ratios[1]);
      };
      right.addChild(levelHeadLine(st));
      right.addChild(bar.track());
      right.addChild(afterLevel);
      right.addChild(afterDetail);
      right.addChild(statPreview);
      right.addChild(quantityRow(st, mats, refresh));
      right.addChild(
         actionButton(
            Component.translatable("gui.minegenshin.character_equip.level_up"),
            !isViewOnly(st) && canLevelUp(st) && st.matSource >= 0 && st.matCount > 0 && maxUsableCount(st, mats) > 0,
            () -> requestLevelUp(st)
         )
      );
      if (st.levelTarget == -1 || st.levelTarget == -2) {
         right.addChild(actionButton(Component.translatable("gui.minegenshin.character_equip.ascend"), !isViewOnly(st) && canAscendTarget(st), () -> {
            ascendTarget(st);
            markDirty(st);
         }));
      }

      body.addChild(right);
      st.sub.addChild(body);
      refresh.run();
   }

   private static UIElement subHeader(CharacterEquipUI.State st, Component title) {
      UIElement head = new UIElement().setId("ce-sub-head");
      Button back = new Button();
      back.addClass("ce-action");
      back.setText(Component.translatable("gui.minegenshin.character_equip.back"));
      back.layout(l -> l.height(16.0F).width(46.0F));
      back.setOnClick(event -> {
         st.subPage = CharacterEquipUI.SubPage.NONE;
         clearSelection(st);
         rebuild(st);
      });
      Label label = new Label();
      label.setId("ce-sub-title");
      label.setText(title);
      label.layout(l -> l.height(15.0F));
      head.addChildren(new UIElement[]{back, label});
      return head;
   }

   private static UIElement levelHeadLine(CharacterEquipUI.State st) {
      UIElement row = new UIElement().addClass("ce-row");
      row.addChildren(
         new UIElement[]{label(Component.literal(levelTargetName(st, st.levelTarget)), "ce-name"), label(Component.literal(currentLevelText(st)), "ce-value")}
      );
      return row;
   }

   private static CharacterEquipUI.ExpBar levelBar(CharacterEquipUI.State st) {
      double[] ratios = levelBarRatios(st);
      return expBar(ratios[0], ratios[1]);
   }

   private static double[] levelBarRatios(CharacterEquipUI.State st) {
      int exp = Math.max(0, st.matExpValue) * Math.max(0, st.matCount);
      if (st.levelTarget == -1) {
         PGCharacterData data = st.character.getData();
         double max = Math.max(1.0, data.getMaxExp());
         double current = Math.max(0, data.getCurrentExp());
         int[] preview = previewCharacterExp(st, exp);
         double used = Math.max(0, exp - preview[1]);
         double after = preview[0] > data.getLevel() ? max : current + used;
         return new double[]{current / max, after / max};
      }

      if (st.levelTarget == -2) {
         ItemStack weapon = st.character.getData().getWeapon();
         WeaponStatsComponent stats = weapon.isEmpty()
            ? null
            : (WeaponStatsComponent)weapon.getOrDefault((DataComponentType)ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT);
         if (stats == null) {
            return new double[]{0.0, 0.0};
         }

         int star = weapon.getItem() instanceof WeaponItem item ? item.getStar() : 1;
         double max = Math.max(1.0, stats.getExpToNextLevel(star));
         double current = Math.max(0, stats.exp);
         int[] preview = previewWeaponExp(st, exp);
         double after = preview != null && preview[0] <= stats.level ? current + Math.max(0.0, preview[2] - current) : max;
         return new double[]{current / max, after / max};
      } else {
         ItemStack artifact = st.character.getData().getArtifactInventory().getItem(st.levelTarget);
         ArtifactStatsComponent stats = artifact.isEmpty()
            ? null
            : (ArtifactStatsComponent)artifact.getOrDefault((DataComponentType)ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT);
         if (stats == null) {
            return new double[]{0.0, 0.0};
         }

         int star = artifact.getItem() instanceof ArtifactItem item ? item.getStar() : 1;
         double max = Math.max(1.0, stats.getExpToNextLevel(star));
         double current = Math.max(0, stats.exp);
         int[] preview = previewArtifactExp(st, exp);
         double after = preview != null && preview[0] <= stats.level ? current + Math.max(0.0, preview[2] - current) : max;
         return new double[]{current / max, after / max};
      }
   }

   private static UIElement quantityRow(CharacterEquipUI.State st, List<CharacterEquipUI.Mat> mats, Runnable refresh) {
      UIElement row = new UIElement().addClass("ce-row");
      row.addChild(label(Component.translatable("gui.minegenshin.character_equip.use_count"), "ce-name-sm"));
      int max = maxUsableCount(st, mats);
      st.matCount = clampCount(st, max);
      TextField field = new TextField();
      field.addClass("ce-count-field");
      field.setOverflowVisible(true);
      field.layout(l -> l.height(16.0F).width(40.0F));
      field.setNumbersOnlyInt(1, Math.max(1, max));
      field.setText(String.valueOf(clampCount(st, max)), false);
      Runnable sync = () -> {
         field.setText(String.valueOf(clampCount(st, max)), false);
         refresh.run();
      };
      field.setTextResponder(text -> {
         int parsed;
         try {
            parsed = Integer.parseInt(text.trim());
         } catch (NumberFormatException ignored) {
            return;
         }

         st.matCount = Math.max(1, Math.min(parsed, Math.max(1, max)));
         refresh.run();
      });
      Button minus = stepButton("−", () -> {
         st.matCount = Math.max(1, st.matCount - 1);
         sync.run();
      });
      Button plus = stepButton("＋", () -> {
         st.matCount = Math.max(1, Math.min(Math.max(1, max), st.matCount + 1));
         sync.run();
      });
      row.addChildren(new UIElement[]{minus, field, plus});
      Button fill = new Button();
      fill.addClass("ce-action");
      fill.setText(Component.translatable("gui.minegenshin.character_equip.quick_fill"));
      fill.layout(l -> l.height(16.0F).flex(1.0F));
      fill.setOnClick(event -> {
         st.matCount = Math.max(1, max);
         rebuild(st);
      });
      row.addChild(fill);
      return row;
   }

   private static int clampCount(CharacterEquipUI.State st, int max) {
      return Math.max(1, Math.min(st.matCount, Math.max(1, max)));
   }

   private static Button stepButton(String text, Runnable action) {
      Button button = new Button();
      button.addClass("ce-step");
      button.setText(Component.literal(text));
      button.layout(l -> l.height(16.0F).width(16.0F));
      button.setOnClick(event -> action.run());
      return button;
   }

   private static int availableMaterialCount(CharacterEquipUI.State st, List<CharacterEquipUI.Mat> mats) {
      for (CharacterEquipUI.Mat mat : mats) {
         if (mat.source() == st.matSource && mat.index() == st.matIndex) {
            return mat.stack().getCount();
         }
      }

      return 0;
   }

   private static long expRoom(CharacterEquipUI.State st) {
      if (st.levelTarget == -1) {
         return st.character.characterExpRoom();
      } else {
         return st.levelTarget == -2 ? st.character.weaponExpRoom() : st.character.artifactExpRoom(st.levelTarget);
      }
   }

   private static int maxUsableCount(CharacterEquipUI.State st, List<CharacterEquipUI.Mat> mats) {
      int available = availableMaterialCount(st, mats);
      if (available <= 0) {
         return 0;
      }

      long room = expRoom(st);
      if (room <= 0L) {
         return 0;
      }

      long per = Math.max(1, st.matExpValue);
      long byRoom = (room + per - 1L) / per;
      return (int)Math.max(1L, Math.min(available, byRoom));
   }

   private static long overflowExp(CharacterEquipUI.State st) {
      return Math.max(0L, grantedExp(st) - expRoom(st));
   }

   private static void requestLevelUp(CharacterEquipUI.State st) {
      long overflow = overflowExp(st);
      if (overflow <= 0L) {
         sendLevelUp(st);
      } else {
         openLevelUpConfirm(st, overflow);
      }
   }

   private static void openLevelUpConfirm(CharacterEquipUI.State st, long overflow) {
      UIElement overlay = new UIElement().addClass("ce-modal");
      overlay.layout(l -> l.positionType(TaffyPosition.ABSOLUTE).left(0.0F).top(0.0F).widthPercent(100.0F).heightPercent(100.0F));
      UIElement panel = new UIElement().addClass("ce-modal-panel");
      panel.addChild(label(Component.translatable("gui.minegenshin.character_equip.level_up"), "ce-modal-title"));
      Label info = new Label();
      info.addClass("ce-modal-text");
      info.setText(Component.translatable("gui.minegenshin.character_equip.level_up_overflow", new Object[]{fmtNumber(overflow)}));
      info.layout(l -> l.widthPercent(100.0F));
      panel.addChild(info);
      UIElement buttons = new UIElement().addClass("ce-modal-buttons");
      buttons.addChildren(new UIElement[]{actionButton(Component.translatable("gui.minegenshin.character_equip.confirm"), true, () -> {
         sendLevelUp(st);
         overlay.removeSelf();
      }), actionButton(Component.translatable("gui.minegenshin.character_equip.cancel"), true, overlay::removeSelf)});
      panel.addChild(buttons);
      overlay.addChild(panel);
      overlay.addEventListener("mouseClick", event -> {
         if (event.target == overlay) {
            overlay.removeSelf();
         }
      });
      st.sub.addChild(overlay);
   }

   private static void sendLevelUp(CharacterEquipUI.State st) {
      NetworkManager.sendEquipLevelUpBatchToServer(st.levelTarget, st.matSource, st.matIndex, st.matCount);
      markDirty(st);
   }

   private static boolean canAscendTarget(CharacterEquipUI.State st) {
      PGCharacterData data = st.character.getData();
      if (st.levelTarget != -1) {
         if (st.levelTarget == -2) {
            ItemStack weapon = data.getWeapon();
            return !(weapon.getItem() instanceof WeaponItem)
               ? false
               : ((WeaponStatsComponent)weapon.getOrDefault((DataComponentType)ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT)).canAscend();
         } else {
            return false;
         }
      } else {
         int cap = levelCap(data.getAscensionPhase());
         return data.getAscensionPhase() < 6 && data.getLevel() >= cap;
      }
   }

   private static void ascendTarget(CharacterEquipUI.State st) {
      if (st.levelTarget == -1) {
         NetworkManager.sendAscendCharacterToServer();
      } else if (st.levelTarget == -2) {
         NetworkManager.sendAscendWeaponToServer();
      }
   }

   private static UIElement materialRow(CharacterEquipUI.State st, CharacterEquipUI.Mat mat) {
      UIElement row = new UIElement().addClass("ce-list-row");
      if (mat.source() == st.matSource && mat.index() == st.matIndex) {
         row.addClass("ce-list-row-on");
      }

      UIElement icon = new UIElement().addClass("ce-mat-icon");
      icon.style(s -> s.background(SpriteTexture.of(ItemIcons.pathOf(mat.stack()))));
      row.addChildren(
         new UIElement[]{
            icon,
            label(mat.stack().getHoverName().copy(), "ce-name"),
            label(Component.literal("x" + mat.stack().getCount()), "ce-value"),
            label(Component.translatable("gui.minegenshin.character_equip.exp_per_book", new Object[]{mat.expValue()}), "ce-value-dim")
         }
      );
      row.addEventListener("mouseClick", event -> {
         st.matSource = mat.source();
         st.matIndex = mat.index();
         st.matExpValue = mat.expValue();
         st.matCount = 1;
         rebuild(st);
      });
      return row;
   }

   private static void refreshLevelUpPreview(CharacterEquipUI.State st, Label levelLabel, Label detailLabel) {
      int exp = grantedExp(st);
      String overflow = fmtNumber(overflowExp(st));
      if (st.levelTarget == -1) {
         PGCharacterData data = st.character.getData();
         int[] preview = previewCharacterExp(st, exp);
         levelLabel.setText(
            afterLevelText(
               I18n.get("gui.minegenshin.character_equip.level_format", new Object[]{data.getLevel(), levelCap(data.getAscensionPhase())}),
               Math.max(0, preview[0] - data.getLevel())
            )
         );
         detailLabel.setText(Component.translatable("gui.minegenshin.character_equip.exp_to_add", new Object[]{exp, overflow}));
      } else if (st.levelTarget == -2) {
         int[] preview = previewWeaponExp(st, exp);
         levelLabel.setText(
            (Component)(preview == null
               ? Component.translatable("gui.minegenshin.character_equip.exp_max")
               : afterLevelText(currentLevelText(st), Math.max(0, preview[0] - currentRawLevel(st))))
         );
         detailLabel.setText(Component.translatable("gui.minegenshin.character_equip.exp_to_add", new Object[]{exp, overflow}));
      } else {
         int[] preview = previewArtifactExp(st, exp);
         levelLabel.setText(
            (Component)(preview == null
               ? Component.translatable("gui.minegenshin.character_equip.exp_max")
               : afterLevelText(currentLevelText(st), Math.max(0, preview[0] - currentRawLevel(st))))
         );
         detailLabel.setText(Component.translatable("gui.minegenshin.character_equip.exp_to_add", new Object[]{exp, overflow}));
      }
   }

   private static Component afterLevelText(String currentText, int gains) {
      return gains <= 0
         ? Component.literal(currentText)
         : Component.translatable("gui.minegenshin.character_equip.level_gain", new Object[]{currentText, gains});
   }

   private static int grantedExp(CharacterEquipUI.State st) {
      return Math.max(0, st.matExpValue) * Math.max(0, st.matCount);
   }

   private static int currentRawLevel(CharacterEquipUI.State st) {
      PGCharacterData data = st.character.getData();
      if (st.levelTarget == -1) {
         return data.getLevel();
      } else if (st.levelTarget == -2) {
         ItemStack weapon = data.getWeapon();
         return ((WeaponStatsComponent)weapon.getOrDefault((DataComponentType)ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT)).level;
      } else {
         ItemStack artifact = data.getArtifactInventory().getItem(st.levelTarget);
         return ((ArtifactStatsComponent)artifact.getOrDefault((DataComponentType)ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT)).level;
      }
   }

   private static void fillStatPreview(CharacterEquipUI.State st, UIElement box) {
      box.clearAllChildren();
      int exp = grantedExp(st);
      if (exp > 0) {
         PGCharacterData data = st.character.getData();
         if (st.levelTarget == -1) {
            int[] preview = previewCharacterExp(st, exp);
            int statIndex = Math.max(0, preview[0] - 1 + data.getAscensionPhase());
            addCharacterStatPreview(box, st, (AttributeType)ModAttributes.MAX_HP.value(), statIndex);
            addCharacterStatPreview(box, st, (AttributeType)ModAttributes.ATK.value(), statIndex);
            addCharacterStatPreview(box, st, (AttributeType)ModAttributes.DEF.value(), statIndex);
         } else if (st.levelTarget == -2) {
            ItemStack weapon = data.getWeapon();
            if (weapon.getItem() instanceof WeaponItem item) {
               WeaponStatsComponent now = (WeaponStatsComponent)weapon.getOrDefault(
                  (DataComponentType)ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT
               );
               WeaponStatsComponent after = now.copy();
               after.addExp(exp, item.getStar());
               addStatPreview(box, now.mainStat, after.mainStat);
               addStatPreview(box, now.subStat, after.subStat);
            }
         } else {
            ItemStack artifact = data.getArtifactInventory().getItem(st.levelTarget);
            if (artifact.getItem() instanceof ArtifactItem item) {
               ArtifactStatsComponent now = (ArtifactStatsComponent)artifact.getOrDefault(
                  (DataComponentType)ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT
               );
               ArtifactStatsComponent after = now.copy();
               after.addExp(exp, item.getStar(), item.getType());
               addStatPreview(box, now.mainStat, after.mainStat);
            }
         }
      }
   }

   private static void addCharacterStatPreview(UIElement box, CharacterEquipUI.State st, AttributeType type, int statIndex) {
      PGCharacterData data = st.character.getData();
      double current = data.getAttributeTotalValue(type);
      int newBase = st.character.getStatAtLevel(type, statIndex);
      String gain = "";
      if (newBase > 0) {
         double percent = data.getAttributePercentModifier(type) + data.getAttributeTempPercentModifier(type);
         double flat = data.getAttributeFlatModifier(type) + data.getAttributeTempFlatModifier(type);
         double delta = newBase * (1.0 + percent) + flat - current;
         gain = Math.abs(delta) < 0.5 ? "" : fmtNumber(delta);
      }

      box.addChild(statRow(type, fmtNumber(current), "", gain));
   }

   private static void addStatPreview(UIElement box, @Nullable TeyvatItemStat now, @Nullable TeyvatItemStat after) {
      if (now != null && now.getAttribute() != null && now.isInitialized()) {
         String gain = "";
         if (after != null) {
            double delta = after.getValue() - now.getValue();
            double epsilon = now.getKind() == TeyvatItemStat.StatKind.PERCENT ? 5.0E-4 : 0.5;
            gain = Math.abs(delta) < epsilon ? "" : statPlainText(now, delta);
         }

         box.addChild(statRow(now.getAttribute(), statPlainText(now), "", gain));
      }
   }

   private static String statPlainText(TeyvatItemStat stat) {
      return stat == null ? "" : statPlainText(stat, stat.getValue());
   }

   private static String statPlainText(TeyvatItemStat stat, double value) {
      return stat.getKind() == TeyvatItemStat.StatKind.PERCENT ? fmtPercent(value) : fmtNumber(value);
   }

   private static boolean canLevelUp(CharacterEquipUI.State st) {
      PGCharacterData data = st.character.getData();
      if (st.levelTarget == -1) {
         return data.getLevel() < levelCap(data.getAscensionPhase());
      }

      if (st.levelTarget == -2) {
         ItemStack weapon = data.getWeapon();
         if (weapon.getItem() instanceof WeaponItem item) {
            WeaponStatsComponent stats = (WeaponStatsComponent)weapon.getOrDefault(
               (DataComponentType)ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT
            );
            return stats.level < stats.getMaxLevel();
         } else {
            return false;
         }
      } else {
         ItemStack artifact = data.getArtifactInventory().getItem(st.levelTarget);
         if (artifact.getItem() instanceof ArtifactItem item) {
            ArtifactStatsComponent stats = (ArtifactStatsComponent)artifact.getOrDefault(
               (DataComponentType)ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT
            );
            return stats.level < stats.getMaxLevel(item.getStar());
         } else {
            return false;
         }
      }
   }

   private static String currentLevelText(CharacterEquipUI.State st) {
      PGCharacterData data = st.character.getData();
      if (st.levelTarget == -1) {
         return I18n.get("gui.minegenshin.character_equip.level_format", new Object[]{data.getLevel(), levelCap(data.getAscensionPhase())});
      } else if (st.levelTarget == -2) {
         ItemStack weapon = data.getWeapon();
         WeaponStatsComponent stats = (WeaponStatsComponent)weapon.getOrDefault(
            (DataComponentType)ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT
         );
         return I18n.get("gui.minegenshin.character_equip.level_format", new Object[]{stats.level, stats.getMaxLevel()});
      } else {
         ItemStack artifact = data.getArtifactInventory().getItem(st.levelTarget);
         ArtifactStatsComponent stats = (ArtifactStatsComponent)artifact.getOrDefault(
            (DataComponentType)ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT
         );
         int star = artifact.getItem() instanceof ArtifactItem item ? item.getStar() : 1;
         return I18n.get("gui.minegenshin.character_equip.level_format", new Object[]{stats.level, stats.getMaxLevel(star)});
      }
   }

   private static String levelTargetName(CharacterEquipUI.State st, int target) {
      PGCharacterData data = st.character.getData();
      if (target == -1) {
         return st.character.getName().getString();
      } else if (target == -2) {
         ItemStack weapon = data.getWeapon();
         return weapon.isEmpty() ? I18n.get("gui.minegenshin.character_equip.page.weapon", new Object[0]) : weapon.getHoverName().getString();
      } else {
         ItemStack artifact = data.getArtifactInventory().getItem(target);
         return artifact.isEmpty() ? I18n.get(slotKey(target), new Object[0]) : artifact.getHoverName().getString();
      }
   }

   private static void openLevelUpPage(CharacterEquipUI.State st, int target) {
      st.levelTarget = target;
      clearSelection(st);
      openSubPage(st, CharacterEquipUI.SubPage.LEVEL_UP);
   }

   private static int[] previewCharacterExp(CharacterEquipUI.State st, int exp) {
      PGCharacterData data = st.character.getData();
      List<Integer> table = CharacterXpConfig.getAllXp();
      int level = data.getLevel();
      int cap = levelCap(data.getAscensionPhase());
      int current = Math.max(0, data.getCurrentExp());
      int remain = Math.max(0, exp);

      while (level < cap && remain > 0) {
         int index = Math.max(0, Math.min(level - 1, table.size() - 1));
         int need = Math.max(1, table.get(index)) - current;
         if (remain >= need) {
            remain -= need;
            level++;
            current = 0;
         } else {
            current += remain;
            remain = 0;
         }
      }

      return new int[]{level, remain, current};
   }

   @Nullable
   private static int[] previewWeaponExp(CharacterEquipUI.State st, int exp) {
      ItemStack weapon = st.character.getData().getWeapon();
      if (weapon.getItem() instanceof WeaponItem item) {
         WeaponStatsComponent stats = ((WeaponStatsComponent)weapon.getOrDefault(
               (DataComponentType)ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT
            ))
            .copy();
         stats.addExp(exp, item.getStar());
         return new int[]{stats.level, stats.getMaxLevel(), stats.exp, stats.storedExp};
      } else {
         return null;
      }
   }

   @Nullable
   private static int[] previewArtifactExp(CharacterEquipUI.State st, int exp) {
      ItemStack artifact = st.character.getData().getArtifactInventory().getItem(st.levelTarget);
      if (artifact.getItem() instanceof ArtifactItem item) {
         ArtifactStatsComponent stats = ((ArtifactStatsComponent)artifact.getOrDefault(
               (DataComponentType)ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT
            ))
            .copy();
         stats.addExp(exp, item.getStar(), item.getType());
         return new int[]{stats.level, stats.getMaxLevel(item.getStar()), stats.exp};
      } else {
         return null;
      }
   }

   private static List<CharacterEquipUI.Owned> ownWeapons(CharacterEquipUI.State st) {
      List<CharacterEquipUI.Owned> out = new ArrayList<>();
      ItemStack equipped = st.character.getData().getWeapon();
      if (!equipped.isEmpty()) {
         out.add(new CharacterEquipUI.Owned(equipped.copy(), CharacterEquipUI.Source.EQUIPPED, weaponSlot(st)));
      }

      if (st.backpack != null) {
         List<ItemStack> list = st.backpack.getCategoryList(Backpack.Category.WEAPONS);

         for (int i = 0; i < list.size(); i++) {
            ItemStack stack = list.get(i);
            if (!stack.isEmpty() && st.character.canEquipWeapon(stack)) {
               out.add(new CharacterEquipUI.Owned(stack.copy(), CharacterEquipUI.Source.BACKPACK, i));
            }
         }
      }

      collectInventory(st.player, st.character::canEquipWeapon, out);
      return out;
   }

   private static int weaponSlot(CharacterEquipUI.State st) {
      return st.character == null ? 5 : st.character.getData().activeWeaponSlot();
   }

   private static List<CharacterEquipUI.Owned> ownArtifacts(CharacterEquipUI.State st, int slot) {
      List<CharacterEquipUI.Owned> out = new ArrayList<>();
      ArtifactInventory inventory = st.character.getData().getArtifactInventory();
      ItemStack equipped = inventory.getItem(slot);
      if (!equipped.isEmpty()) {
         out.add(new CharacterEquipUI.Owned(equipped.copy(), CharacterEquipUI.Source.EQUIPPED, slot));
      }

      ArtifactType wanted = slotType(slot);
      if (wanted != null && st.backpack != null) {
         List<ItemStack> list = st.backpack.getCategoryList(Backpack.Category.ARTIFACTS);

         for (int i = 0; i < list.size(); i++) {
            ItemStack stack = list.get(i);
            if (stack.getItem() instanceof ArtifactItem artifact && artifact.getType() == wanted) {
               out.add(new CharacterEquipUI.Owned(stack.copy(), CharacterEquipUI.Source.BACKPACK, i));
            }
         }
      }

      if (wanted != null) {
         collectInventory(st.player, stackx -> stackx.getItem() instanceof ArtifactItem artifactx && artifactx.getType() == wanted, out);
      }

      return out;
   }

   private static void collectInventory(Player player, Predicate<ItemStack> filter, List<CharacterEquipUI.Owned> out) {
      Inventory inventory = player.getInventory();

      for (int i = 0; i < 36 && i < inventory.getContainerSize(); i++) {
         ItemStack stack = inventory.getItem(i);
         if (!stack.isEmpty() && filter.test(stack)) {
            out.add(new CharacterEquipUI.Owned(stack.copy(), CharacterEquipUI.Source.INVENTORY, i));
         }
      }
   }

   private static ArtifactType slotType(int slot) {
      return slot >= 0 && slot < ARTIFACT_TYPES.length ? ARTIFACT_TYPES[slot] : null;
   }

   private static List<CharacterEquipUI.Mat> levelMaterials(CharacterEquipUI.State st) {
      List<CharacterEquipUI.Mat> out = new ArrayList<>();
      if (st.backpack != null) {
         List<ItemStack> list = st.backpack.getCategoryList(Backpack.Category.DEVELOPMENT);

         for (int i = 0; i < list.size(); i++) {
            ItemStack stack = list.get(i);
            if (stack.getItem() instanceof AdviceBookItem book) {
               out.add(new CharacterEquipUI.Mat(stack.copy(), 1, i, book.getExpValue()));
            }
         }
      }

      Inventory inventory = st.player.getInventory();

      for (int i = 0; i < 36 && i < inventory.getContainerSize(); i++) {
         ItemStack stack = inventory.getItem(i);
         if (stack.getItem() instanceof AdviceBookItem book) {
            out.add(new CharacterEquipUI.Mat(stack.copy(), 0, i, book.getExpValue()));
         }
      }

      return out;
   }

   private static UIElement grid(List<CharacterEquipUI.Owned> items, int columns, CharacterEquipUI.State st) {
      return gridOf(items, columns, st, false);
   }

   private static UIElement bigGrid(List<CharacterEquipUI.Owned> items, int columns, CharacterEquipUI.State st) {
      return gridOf(items, columns, st, true);
   }

   private static UIElement gridOf(List<CharacterEquipUI.Owned> items, int columns, CharacterEquipUI.State st, boolean big) {
      UIElement grid = new UIElement().addClass("ce-grid");
      if (items.isEmpty()) {
         grid.addChild(note("gui.minegenshin.character_equip.empty_list"));
         return grid;
      }

      UIElement row = new UIElement().addClass("ce-grid-row");
      grid.addChild(row);

      for (int i = 0; i < items.size(); i++) {
         if (i > 0 && i % columns == 0) {
            row = new UIElement().addClass("ce-grid-row");
            grid.addChild(row);
         }

         row.addChild(big ? bigSlot(items.get(i), st) : smallSlot(items.get(i), st));
      }

      return grid;
   }

   private static UIElement smallSlot(CharacterEquipUI.Owned owned, CharacterEquipUI.State st) {
      UIElement slot = new UIElement().addClass("ce-slot");
      if (!owned.stack().isEmpty()) {
         slot.style(s -> s.background(SpriteTexture.of(ItemIcons.pathOf(owned.stack()))));
      }

      if (owned.stack().getItem() instanceof ArtifactItem && !isActivated(owned.stack())) {
         slot.addClass("ce-slot-dim");
      }

      addAvatarOverlay(slot, owned, st);
      if (isSelected(st, owned)) {
         slot.addClass("ce-slot-selected");
         slot.transform(t -> t.scale(1.1F));
      }

      installSlotAnimation(slot);
      slot.addEventListener("mouseClick", event -> select(st, owned));
      return slot;
   }

   private static UIElement bigSlot(CharacterEquipUI.Owned owned, CharacterEquipUI.State st) {
      UIElement slot = new UIElement().addClass("ce-big-slot");
      if (!owned.stack().isEmpty()) {
         slot.style(s -> s.background(SpriteTexture.of(ItemIcons.pathOf(owned.stack()))));
      }

      if (owned.stack().getItem() instanceof ArtifactItem && !isActivated(owned.stack())) {
         slot.addClass("ce-slot-dim");
      }

      addAvatarOverlay(slot, owned, st);
      if (isSelected(st, owned)) {
         slot.addClass("ce-big-slot-selected");
         slot.transform(t -> t.scale(1.1F));
      }

      installSlotAnimation(slot);
      slot.addEventListener("mouseClick", event -> select(st, owned));
      return slot;
   }

   private static void installSlotAnimation(UIElement slot) {
      slot.addEventListener("mouseEnter", event -> {
         slot.transform(t -> t.scale(1.1F));
         slot.style(s -> s.zIndex(10));
      });
      slot.addEventListener("mouseLeave", event -> {
         if (!slot.hasClass("ce-slot-selected") && !slot.hasClass("ce-big-slot-selected")) {
            slot.transform(t -> t.scale(1.0F));
         }

         slot.style(s -> s.zIndex(0));
      });
      slot.addEventListener("mouseDown", event -> slot.transform(t -> t.scale(1.0F)));
   }

   private static void addAvatarOverlay(UIElement slot, CharacterEquipUI.Owned owned, CharacterEquipUI.State st) {
      if (owned.source() == CharacterEquipUI.Source.EQUIPPED && st.character != null) {
         UIElement avatar = new UIElement().addClass("ce-avatar-overlay");
         avatar.style(s -> s.background(SpriteTexture.of(avatarTexture(st.character))));
         slot.addChild(avatar);
      }
   }

   private static boolean isSelected(CharacterEquipUI.State st, CharacterEquipUI.Owned owned) {
      return st.sel != null && st.sel.source() == owned.source() && st.sel.index() == owned.index();
   }

   private static void select(CharacterEquipUI.State st, CharacterEquipUI.Owned owned) {
      st.sel = owned;
      rebuild(st);
   }

   private static void clearSelection(CharacterEquipUI.State st) {
      st.sel = null;
   }

   private static void equip(CharacterEquipUI.State st, int slot) {
      CharacterEquipUI.Owned sel = st.sel;
      if (sel != null && sel.source() != CharacterEquipUI.Source.EQUIPPED && !isViewOnly(st)) {
         if (slot >= 5) {
            if (!(sel.stack().getItem() instanceof WeaponItem)) {
               return;
            }
         } else if (!isActivated(sel.stack()) || !ArtifactInventory.isValidForSlot(slot, sel.stack())) {
            return;
         }

         if (sel.source() == CharacterEquipUI.Source.INVENTORY) {
            NetworkManager.sendEquipFromInventoryToServer(slot, sel.index());
         } else {
            NetworkManager.sendEquipOrSwapArtifactToServer(slot, sel.index());
         }

         markDirty(st);
         if (slot >= 0 && slot < ARTIFACT_TYPES.length) {
            st.artifactSlot = slot;
         }

         clearSelection(st);
         rebuild(st);
      }
   }

   private static boolean isActivated(ItemStack stack) {
      if (stack == null || stack.isEmpty()) {
         return false;
      }

      if (stack.getItem() instanceof WeaponItem) {
         return true;
      }

      ArtifactStatsComponent stats = (ArtifactStatsComponent)stack.getOrDefault(
         (DataComponentType)ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT
      );
      return stats.activated;
   }

   private static UIElement artifactDetail(ItemStack stack, int slot) {
      UIElement box = new UIElement().addClass("ce-detail");
      if (stack.getItem() instanceof ArtifactItem artifact) {
         ArtifactStatsComponent var7 = (ArtifactStatsComponent)stack.getOrDefault(
            (DataComponentType)ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT
         );
         box.addChild(label(stack.getHoverName().copy(), "ce-title-name"));
         box.addChild(label(Component.literal("★".repeat(Math.max(1, artifact.getStar()))), "ce-star"));
         box.addChild(
            label(
               Component.translatable("gui.minegenshin.character_equip.level_format", new Object[]{var7.level, var7.getMaxLevel(artifact.getStar())}),
               "ce-value"
            )
         );
         box.addChild(label(Component.translatable("artifact.type." + artifact.getType().name().toLowerCase(Locale.ROOT)), "ce-note"));
         if (var7.mainStat != null && var7.mainStat.isInitialized()) {
            box.addChild(label(Component.literal(statText(var7.mainStat)), "ce-value-base"));
         }

         if (var7.subStats != null) {
            for (TeyvatItemStat sub : var7.subStats) {
               if (sub.isInitialized()) {
                  box.addChild(label(Component.literal(statText(sub)), "ce-value-dim"));
               }
            }
         }

         box.addChild(
            label(
               Component.translatable(var7.activated ? "gui.minegenshin.character_equip.unlocked" : "gui.minegenshin.character_equip.locked"),
               var7.activated ? "ce-value-extra" : "ce-note"
            )
         );
         return box;
      } else {
         box.addChild(label(Component.translatable(slotKey(slot)), "ce-title-name"));
         box.addChild(note("gui.minegenshin.character_equip.empty_slot"));
         return box;
      }
   }

   private static UIElement weaponSummary(ItemStack stack) {
      UIElement box = new UIElement().addClass("ce-detail");
      if (stack.getItem() instanceof WeaponItem weapon) {
         WeaponStatsComponent var4 = (WeaponStatsComponent)stack.getOrDefault(
            (DataComponentType)ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT
         );
         box.addChild(label(stack.getHoverName().copy(), "ce-title-name"));
         box.addChild(label(Component.literal("★".repeat(Math.max(1, weapon.getStar()))), "ce-star"));
         box.addChild(label(Component.translatable(weaponTypeKey(stack)), "ce-note"));
         box.addChild(label(Component.translatable("gui.minegenshin.character_equip.level_format", new Object[]{var4.level, var4.getMaxLevel()}), "ce-value"));
         if (weapon.canRefine()) {
            box.addChild(label(Component.translatable("gui.minegenshin.character_equip.refinement", new Object[]{var4.refinementRank}), "ce-value-dim"));
         }

         if (var4.mainStat != null && var4.mainStat.isInitialized()) {
            box.addChild(label(Component.literal(statText(var4.mainStat)), "ce-value-base"));
         }

         if (var4.subStat != null && var4.subStat.isInitialized()) {
            box.addChild(label(Component.literal(statText(var4.subStat)), "ce-value-dim"));
         }

         box.addChild(sectionTitle("gui.minegenshin.character_equip.weapon_passive"));
         box.addChild(note("gui.minegenshin.character_equip.weapon_passive_none"));
         return box;
      } else {
         box.addChild(sectionTitle("gui.minegenshin.character_equip.page.weapon"));
         box.addChild(note("message.minegenshin.no_weapon_equipped"));
         return box;
      }
   }

   private static String weaponTypeKey(ItemStack stack) {
      if (stack.getItem() instanceof WeaponItem weapon) {
         return switch (WeaponPoiseTable.weaponOfClass(weapon.getClass())) {
            case BOW -> "gui.minegenshin.character_equip.weapon_type.bow";
            case CATALYST -> "gui.minegenshin.character_equip.weapon_type.catalyst";
            case CLAYMORE -> "gui.minegenshin.character_equip.weapon_type.claymore";
            case POLEARM -> "gui.minegenshin.character_equip.weapon_type.polearm";
            case SWORD -> "gui.minegenshin.character_equip.weapon_type.sword";
            case FIST -> "gui.minegenshin.character_equip.weapon_type.fist";
            case UNKNOWN -> "gui.minegenshin.character_equip.weapon_type.unknown";
         };
      } else {
         return "gui.minegenshin.character_equip.weapon_type.unknown";
      }
   }

   private static String statText(TeyvatItemStat stat) {
      if (stat != null && stat.getAttribute() != null) {
         String name = I18n.get(stat.getAttribute().translationKey(), new Object[0]);
         String value = stat.getKind() == TeyvatItemStat.StatKind.PERCENT ? fmtPercent(stat.getValue()) : fmtNumber(stat.getValue());
         return name + " +" + value;
      } else {
         return "";
      }
   }

   private static UIElement baseStatRow(CharacterEquipUI.State st, AttributeType type) {
      PGCharacterData data = st.character.getData();
      double total = data.getAttributeTotalValue(type);
      double base = data.getAttributeBaseValue(type);
      double extra = total - base;
      boolean breakdown = base >= 0.5;
      return statRow(type, fmtNumber(total), breakdown ? fmtNumber(base) : "", breakdown && extra >= 0.5 ? fmtNumber(extra) : "");
   }

   private static UIElement valueStatRow(CharacterEquipUI.State st, AttributeType type) {
      return statRow(type, fmtNumber(st.character.getData().getAttributeTotalValue(type)), "", "");
   }

   private static UIElement percentStatRow(CharacterEquipUI.State st, AttributeType type) {
      PGCharacterData data = st.character.getData();
      double total = data.getAttributeTotalValue(type);
      double base = data.getAttributeBaseValue(type);
      double extra = total - base;
      boolean breakdown = Math.abs(base) >= 5.0E-4;
      return statRow(type, fmtPercent(total), breakdown ? fmtPercent(base) : "", breakdown && Math.abs(extra) >= 5.0E-4 ? fmtPercent(extra) : "");
   }

   private static UIElement statRow(AttributeType type, String total, String base, String extra) {
      UIElement row = new UIElement().addClass("ce-row");
      UIElement gap = new UIElement().addClass("ce-stat-gap");
      row.addChildren(
         new UIElement[]{
            label(Component.translatable(type.translationKey()), "ce-stat-name"),
            label(Component.literal(total), "ce-stat-white"),
            label(Component.literal(base), "ce-stat-yellow"),
            gap,
            label(Component.literal(extra.isEmpty() ? "" : "+" + extra), "ce-stat-green")
         }
      );
      return row;
   }

   private static String fmtNumber(double value) {
      return String.format(Locale.ROOT, "%,d", Math.round(value));
   }

   private static String fmtPercent(double ratio) {
      return String.format(Locale.ROOT, "%.1f%%", ratio * 100.0);
   }

   private static boolean hasCheatPermission(Player player) {
      return player != null && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
   }

   private static String dataStamp(CharacterEquipUI.State st) {
      StringBuilder sb = new StringBuilder(256);
      sb.append(st.attachment.getCurrentCharacterIndex()).append('/');

      for (int i = 0; i < 4; i++) {
         PGCharacter member = st.attachment.getPartyCharacter(i);
         sb.append(member == null ? "-" : member.getCharacterUUID()).append('/');
      }

      PGCharacter character = viewed(st);
      if (character == null) {
         return sb.toString();
      }

      sb.append(character.getCharacterUUID()).append('/');
      PGCharacterData data = character.getData();
      if (data != null) {
         sb.append(data.getLevel())
            .append('/')
            .append(data.getCurrentExp())
            .append('/')
            .append(data.getMaxExp())
            .append('/')
            .append(data.getAscensionPhase())
            .append('/')
            .append(data.getConstellation())
            .append('/')
            .append(data.getNormalAttackLevel())
            .append('/')
            .append(data.getElementalSkillLevel())
            .append('/')
            .append(data.getElementalBurstLevel())
            .append('/')
            .append(data.getManualNormalAttack())
            .append('/')
            .append(data.getManualElementalSkill())
            .append('/')
            .append(data.getManualElementalBurst())
            .append('/')
            .append(data.getNormalAttackLevelCap())
            .append('/')
            .append(data.getElementalSkillLevelCap())
            .append('/')
            .append(data.getElementalBurstLevelCap())
            .append('/')
            .append(Math.round(data.getAttributeTotalValue((AttributeType)ModAttributes.MAX_HP.value())))
            .append('/')
            .append(Math.round(data.getAttributeTotalValue((AttributeType)ModAttributes.ATK.value())))
            .append('/')
            .append(Math.round(data.getAttributeTotalValue((AttributeType)ModAttributes.DEF.value())))
            .append('/');
         ArtifactInventory inventory = data.getArtifactInventory();

         for (int slot = 0; slot < inventory.slotCount(); slot++) {
            sb.append(stackKey(inventory.getItem(slot))).append('/');
         }
      }

      if (st.backpack != null) {
         appendStacks(sb, st.backpack.getCategoryList(Backpack.Category.DEVELOPMENT));
         appendStacks(sb, st.backpack.getCategoryList(Backpack.Category.WEAPONS));
         appendStacks(sb, st.backpack.getCategoryList(Backpack.Category.ARTIFACTS));
      }

      Inventory playerInventory = st.player.getInventory();

      for (int i = 0; i < 36 && i < playerInventory.getContainerSize(); i++) {
         sb.append(stackKey(playerInventory.getItem(i))).append('/');
      }

      return sb.toString();
   }

   private static void appendStacks(StringBuilder sb, List<ItemStack> stacks) {
      for (ItemStack stack : stacks) {
         sb.append(stackKey(stack)).append('/');
      }
   }

   private static String stackKey(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         int level = -1;
         int exp = -1;
         int extra = 0;
         boolean activated = false;
         ArtifactStatsComponent artifact = (ArtifactStatsComponent)stack.get((DataComponentType)ModDataComponents.ARTIFACT_STATS.get());
         if (artifact != null) {
            level = artifact.level;
            exp = artifact.exp;
            activated = artifact.activated;
         }

         WeaponStatsComponent weapon = (WeaponStatsComponent)stack.get((DataComponentType)ModDataComponents.WEAPON_STATS.get());
         if (weapon != null) {
            level = weapon.level * 1000 + weapon.ascended;
            exp = weapon.exp;
            extra = weapon.refinementRank;
         }

         return stack.getItem().getDescriptionId() + "#" + stack.getCount() + "#" + level + "#" + exp + "#" + extra + "#" + activated;
      } else {
         return "-";
      }
   }

   private record ExpBar(UIElement track, UIElement ghost) {
      void setPreview(double ratio, double previewRatio) {
         float percent = CharacterEquipUI.percentOf(ratio);
         float preview = Math.max(percent, CharacterEquipUI.percentOf(previewRatio));
         float extra = preview - percent;
         this.ghost.layout(l -> l.widthPercent(extra).display(extra > 0.01F ? TaffyDisplay.FLEX : TaffyDisplay.NONE));
      }
   }

   private record Mat(ItemStack stack, int source, int index, int expValue) {
   }

   private record Owned(ItemStack stack, CharacterEquipUI.Source source, int index) {
   }

   private enum Page {
      STATS,
      WEAPON,
      ARTIFACT,
      CONSTELLATION,
      TALENT,
      PROFILE;

      String key() {
         return "gui.minegenshin.character_equip.page." + this.name().toLowerCase(Locale.ROOT);
      }
   }

   private enum Source {
      BACKPACK,
      INVENTORY,
      EQUIPPED;
   }

   private static final class State {
      final Player player;
      PlayerCharactersAttachment attachment;
      @Nullable
      Backpack backpack;
      boolean dirty;
      @Nullable
      PGCharacter character;
      int partyIndex;
      CharacterEquipUI.Page page = CharacterEquipUI.Page.STATS;
      CharacterEquipUI.SubPage subPage = CharacterEquipUI.SubPage.NONE;
      int artifactSlot = 0;
      @Nullable
      CharacterEquipUI.Owned sel;
      int selConstellation = 1;
      int selTalent = 0;
      int talentTab;
      int levelTarget = -1;
      boolean showDetails;
      int matSource = -1;
      int matIndex = -1;
      int matCount = 1;
      int matExpValue;
      float[] orbX = (float[])CharacterEquipUI.ORB_DEFAULT_X.clone();
      float[] orbY = (float[])CharacterEquipUI.ORB_DEFAULT_Y.clone();
      int orbDragSlot = -1;
      float orbDragStartX;
      float orbDragStartY;
      float orbDragStartNx;
      float orbDragStartNy;
      boolean orbDragMoved;
      @Nullable
      GenshinPreviewPlayer previewAnimatable;
      @Nullable
      String previewAnimationName;
      @Nullable
      String previewCharacterId;
      final List<UIElement> avatarButtons = new ArrayList<>();
      final Map<CharacterEquipUI.Page, CustomToggle> tabs = new EnumMap<>(CharacterEquipUI.Page.class);
      final UIElement[] orbBySlot = new UIElement[CharacterEquipUI.ARTIFACT_TYPES.length];
      @Nullable
      ScrollerView stripScroll;
      @Nullable
      UIElement strip;
      @Nullable
      UIElement elemIcon;
      @Nullable
      Label topName;
      @Nullable
      UIElement menu;
      @Nullable
      UIElement stage;
      @Nullable
      UIElement panel;
      @Nullable
      UIElement detailCard;
      @Nullable
      UIElement main;
      @Nullable
      UIElement bottomRight;
      @Nullable
      UIElement sub;
      /** 最下面那块「外观」（内容和 K 页同一份，由角色自己的配置页给）。 */
      @Nullable
      UIElement appearance;
      @Nullable
      UIElement orbLayer;
      @Nullable
      Label hint;
      @Nullable
      Label previewCredit;
      @Nullable
      Label viewOnlyNote;

      State(Player player, PlayerCharactersAttachment attachment, @Nullable Backpack backpack) {
         this.player = player;
         this.attachment = attachment;
         this.backpack = backpack;
      }
   }

   private enum SubPage {
      NONE,
      ARTIFACT_CHANGE,
      WEAPON_CHANGE,
      LEVEL_UP;
   }
}
