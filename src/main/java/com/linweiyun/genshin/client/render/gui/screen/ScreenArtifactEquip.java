// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.client.render.gui.screen;

import com.linweiyun.genshin.content.items.artifact.ArtifactItem;
import com.linweiyun.genshin.content.items.artifact.ArtifactSet;
import com.linweiyun.genshin.content.items.artifact.inventory.ArtifactInventory;
import com.linweiyun.genshin.content.items.artifact.type.ArtifactType;
import com.linweiyun.genshin.content.items.component.ArtifactStatsComponent;
import com.linweiyun.genshin.content.items.component.WeaponStatsComponent;
import com.linweiyun.genshin.content.items.weapon.WeaponItem;
import com.linweiyun.genshin.content.stat.TeyvatItemStat;
import com.linweiyun.genshin.asset.ItemIcons;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.Backpack;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.PGCharacterData;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.linweiyun.genshin.core.system.registry.register.ModDataComponents;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUIClientAccess;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import java.util.ArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import com.linweiyun.genshin.asset.source.CharacterResourceSources;

public class ScreenArtifactEquip extends Screen {
   private static final Logger LOG = ModLog.getLogger(LogGroup.RENDER);
   final ModularUI modularUI;
   private static final ArtifactType[] TYPES = ArtifactType.values();
   private static final String[] LABEL_KEYS = new String[]{
      "gui.minegenshin.artifact_equip.flower",
      "gui.minegenshin.artifact_equip.plume",
      "gui.minegenshin.artifact_equip.sands",
      "gui.minegenshin.artifact_equip.goblet",
      "gui.minegenshin.artifact_equip.circlet",
      "gui.minegenshin.artifact_equip.weapon"
   };
   private static final String BORDER = "minegenshin:gui/selected_border.png";
   private static ScreenArtifactEquip.St OPEN_STATE;

   public ScreenArtifactEquip(ModularUI modularUI) {
      super(Component.empty());
      this.modularUI = modularUI;
   }

   public void init() {
      super.init();
      ModularUIClientAccess.setScreenAndInit(this.modularUI, this);
      this.addRenderableWidget(ModularUIClientAccess.getWidget(this.modularUI));
      LOG.info("ScreenArtifactEquip init() → LDLib2 UI已注册");
   }

   public void removed() {
      super.removed();
      OPEN_STATE = null;
   }

   public static void refreshIfOpen() {
      ScreenArtifactEquip.St st = OPEN_STATE;
      Minecraft mc = Minecraft.getInstance();
      if (st != null && mc.player != null) {
         Backpack bp = (Backpack)mc.player.getData(AttachmentRegistration.BACKPACK_ATTACHMENT);
         ScreenArtifactEquip.Ent sel = st.selectedEnt;
         if (sel != null && sel.fromBackpack && sel.backpackIdx >= 0) {
            int globalSlot = getCategoryOffset(Backpack.Category.ARTIFACTS) + sel.backpackIdx;
            ItemStack fresh = bp.getItem(globalSlot);
            if (!fresh.isEmpty()) {
               st.selectedEnt = new ScreenArtifactEquip.Ent(fresh.copy(), true, sel.backpackIdx, true, -1, null, -1);
            }
         }

         if (st.lc != null) {
            fillLC(st.lc, bp, st);
         }

         if (st.mp != null && st.rp != null) {
            fillDetail(st.mp, st.rp, bp, st);
         }
      }
   }

   public static ModularUI createModularUI(Player player, int slotIndex) {
      Stylesheet ss = StylesheetManager.INSTANCE.getStylesheetSafe(Identifier.parse("minegenshin:lss/artifact_equip.lss"));
      PlayerCharactersAttachment ca = (PlayerCharactersAttachment)player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
      PGCharacter cc = ca.getCurrentCharacter();
      UIElement root = new UIElement().setId("root");
      root.layout(l -> {
         l.widthPercent(100.0F);
         l.heightPercent(100.0F);
      });
      if (cc != null && cc.getData() != null) {
         PGCharacterData cd = cc.getData();
         ArtifactInventory inv = cd.getArtifactInventory();
         Backpack bp = (Backpack)player.getData(AttachmentRegistration.BACKPACK_ATTACHMENT);
         ScreenArtifactEquip.St st = new ScreenArtifactEquip.St(
            slotIndex, slotIndex == 5 ? null : (slotIndex >= 0 && slotIndex < 5 ? ArtifactInventory.slotToType(slotIndex) : ArtifactType.FLOWER)
         );
         st.currentCharUUID = cc.getCharacterUUID();
         st.characterTextureId = cc.getTextureId();
         st.attachment = ca;
         st.inv = inv;
         OPEN_STATE = st;
         autoSel(bp, st);
         UIElement win = new UIElement().setId("window");
         UIElement top = new UIElement().setId("top-section");
         UIElement srow = new UIElement().setId("artifact-slots-row");

         for (int i = 0; i < 6; i++) {
            int si = i;
            UIElement se = slotEl(i, inv);
            se.addEventListener("mouseEnter", e -> {
               se.style(s -> s.overlay(SpriteTexture.of("minegenshin:gui/selected_border.png")));
               se.transform(t -> t.scale(1.1F));
            });
            se.addEventListener("mouseLeave", e -> {
               if (st.slot != si || st.selectedEnt != null) {
                  se.style(s -> s.overlay(null));
                  se.transform(t -> t.scale(1.0F));
               }
            });
            se.addEventListener("mouseDown", e -> se.transform(t -> t.scale(1.0F)));
            se.addEventListener("mouseClick", e -> clickSlot(si, bp, st));
            srow.addChild(se);
         }

         top.addChild(srow);
         UIElement det = new UIElement().setId("detail-section");
         UIElement lp = new UIElement().setId("left-panel");
         UIElement tc = new UIElement().setId("type-toggle-container");
         UIElement src = new UIElement().setId("list-scroller-container");
         ScrollerView sv = new ScrollerView();
         sv.setId("artifact-list-scroller-view");
         UIElement lc = new UIElement().setId("artifact-list-container");
         sv.addScrollViewChild(lc);
         src.addChild(sv);
         lp.addChildren(new UIElement[]{tc, src});
         UIElement mp = new UIElement().setId("middle-panel");
         UIElement rp = new UIElement().setId("right-panel");
         det.addChildren(new UIElement[]{lp, mp, rp});
         win.addChildren(new UIElement[]{top, det});
         root.addChild(win);
         st.top = top;
         st.det = det;
         st.lc = lc;
         st.mp = mp;
         st.rp = rp;
         fillTC(tc, bp, st);
         fillLC(lc, bp, st);
         fillDetail(mp, rp, bp, st);
         det.setDisplay(false);
         if (slotIndex >= 0 && slotIndex < 6) {
            top.setDisplay(false);
            det.setDisplay(true);
         }

         LOG.info("createModularUI完成，详情页内容已填充但默认隐藏");
         return ModularUI.of(UI.of(root, new Stylesheet[]{ss}), player);
      } else {
         root.addChild(new Label().setText(Component.translatable("gui.minegenshin.artifact_equip.no_character")));
         return ModularUI.of(UI.of(root, new Stylesheet[]{ss}), player);
      }
   }

   private static void clickSlot(int si, Backpack bp, ScreenArtifactEquip.St st) {
      LOG.info("点击槽位[{}]", si);
      st.slot = si;
      st.type = si == 5 ? null : ArtifactInventory.slotToType(si);
      st.sel = null;
      st.top.setDisplay(false);
      st.det.setDisplay(true);
      autoSel(bp, st);
      UIElement tc = byId(st.det, "type-toggle-container");
      if (tc != null) {
         fillTC(tc, bp, st);
      }

      if (st.lc != null) {
         fillLC(st.lc, bp, st);
      }

      if (st.mp != null && st.rp != null) {
         fillDetail(st.mp, st.rp, bp, st);
      }
   }

   private static UIElement slotEl(int idx, ArtifactInventory inv) {
      ItemStack s = inv.getItem(idx);
      UIElement e = new UIElement().addClass("equipped-slot");
      if (!s.isEmpty()) {
         e.style(x -> x.background(SpriteTexture.of(tex(s))));
      } else {
         e.addClass("empty-slot");
      }

      return e;
   }

   private static void autoSel(Backpack bp, ScreenArtifactEquip.St st) {
      int si = st.slot;
      if (si >= 0 && si < 6) {
         if (st.type == null) {
            autoSelWeapon(bp, st);
         } else {
            PGCharacter currentChar = st.attachment.getCharacterByUUID(st.currentCharUUID);
            if (currentChar != null && currentChar.getData() != null) {
               ArtifactInventory currentInv = currentChar.getData().getArtifactInventory();
               ItemStack eq = currentInv.getItem(si);
               if (!eq.isEmpty()) {
                  st.selectedEnt = new ScreenArtifactEquip.Ent(eq.copy(), false, -1, true, st.currentCharUUID, st.characterTextureId, si);
                  return;
               }
            }

            for (PGCharacter ch : st.attachment.getOwnedCharacters()) {
               if (ch != null && ch.getData() != null && ch.getCharacterUUID() != st.currentCharUUID) {
                  ArtifactInventory chInv = ch.getData().getArtifactInventory();
                  ItemStack chEq = chInv.getItem(si);
                  if (!chEq.isEmpty() && chEq.getItem() instanceof ArtifactItem ai && ai.getType() == st.type) {
                     st.selectedEnt = new ScreenArtifactEquip.Ent(chEq.copy(), false, -1, true, ch.getCharacterUUID(), ch.getTextureId(), si);
                     return;
                  }
               }
            }

            ArrayList<ItemStack> arr = bp.getCategoryList(Backpack.Category.ARTIFACTS);

            for (int i = 0; i < arr.size(); i++) {
               ItemStack s = arr.get(i);
               if (!s.isEmpty() && s.getItem() instanceof ArtifactItem ai && ai.getType() == st.type) {
                  ArtifactStatsComponent stats = (ArtifactStatsComponent)s.getOrDefault(
                     (DataComponentType)ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT
                  );
                  if (stats.activated) {
                     st.selectedEnt = new ScreenArtifactEquip.Ent(s.copy(), true, i, true, -1, null, -1);
                     return;
                  }
               }
            }

            for (int i = 0; i < arr.size(); i++) {
               ItemStack s = arr.get(i);
               if (!s.isEmpty() && s.getItem() instanceof ArtifactItem ai && ai.getType() == st.type) {
                  st.selectedEnt = new ScreenArtifactEquip.Ent(s.copy(), true, i, false, -1, null, -1);
                  return;
               }
            }

            st.selectedEnt = null;
         }
      }
   }

   private static void autoSelWeapon(Backpack bp, ScreenArtifactEquip.St st) {
      int si = 5;
      PGCharacter currentChar = st.attachment.getCharacterByUUID(st.currentCharUUID);
      if (currentChar != null && currentChar.getData() != null) {
         ItemStack eq = currentChar.getData().getWeapon();
         if (!eq.isEmpty()) {
            st.selectedEnt = new ScreenArtifactEquip.Ent(eq.copy(), false, -1, true, st.currentCharUUID, st.characterTextureId, si);
            return;
         }
      }

      for (PGCharacter ch : st.attachment.getOwnedCharacters()) {
         if (ch != null && ch.getData() != null && ch.getCharacterUUID() != st.currentCharUUID) {
            ItemStack chEq = ch.getData().getWeapon();
            if (!chEq.isEmpty() && isWeaponCompatible(st, chEq)) {
               st.selectedEnt = new ScreenArtifactEquip.Ent(chEq.copy(), false, -1, true, ch.getCharacterUUID(), ch.getTextureId(), si);
               return;
            }
         }
      }

      ArrayList<ItemStack> arr = bp.getCategoryList(Backpack.Category.WEAPONS);

      for (int i = 0; i < arr.size(); i++) {
         ItemStack s = arr.get(i);
         if (!s.isEmpty() && isWeaponCompatible(st, s)) {
            st.selectedEnt = new ScreenArtifactEquip.Ent(s.copy(), true, i, true, -1, null, -1);
            return;
         }
      }

      st.selectedEnt = null;
   }

   private static void fillTC(UIElement c, Backpack bp, ScreenArtifactEquip.St st) {
      c.clearAllChildren();

      for (int i = 0; i < TYPES.length; i++) {
         ArtifactType t = TYPES[i];
         Button b = new Button();
         b.setText(Component.translatable(LABEL_KEYS[i]));
         b.addClass("type-toggle-btn");
         if (t == st.type) {
            b.addClass("type-toggle-active");
         }

         b.setOnClick(e -> {
            st.type = t;
            st.sel = null;
            st.slot = ArtifactInventory.typeToSlot(t);
            autoSel(bp, st);
            fillTC(c, bp, st);
            if (st.lc != null) {
               fillLC(st.lc, bp, st);
            }

            if (st.mp != null && st.rp != null) {
               fillDetail(st.mp, st.rp, bp, st);
            }
         });
         c.addChild(b);
      }

      Button wb = new Button();
      wb.setText(Component.translatable(LABEL_KEYS[5]));
      wb.addClass("type-toggle-btn");
      if (st.type == null) {
         wb.addClass("type-toggle-active");
      }

      wb.setOnClick(e -> {
         st.type = null;
         st.sel = null;
         st.slot = 5;
         autoSel(bp, st);
         fillTC(c, bp, st);
         if (st.lc != null) {
            fillLC(st.lc, bp, st);
         }

         if (st.mp != null && st.rp != null) {
            fillDetail(st.mp, st.rp, bp, st);
         }
      });
      c.addChild(wb);
      c.markAsInternal();
   }

   private static void fillWeaponLC(UIElement c, Backpack bp, ScreenArtifactEquip.St st) {
      c.clearAllChildren();
      st.sel = null;
      int si = 5;
      ArrayList<ScreenArtifactEquip.Ent> list = new ArrayList<>();
      PGCharacter currentChar = st.attachment.getCharacterByUUID(st.currentCharUUID);
      if (currentChar != null && currentChar.getData() != null) {
         ItemStack eq = currentChar.getData().getWeapon();
         if (!eq.isEmpty()) {
            list.add(new ScreenArtifactEquip.Ent(eq.copy(), false, -1, true, st.currentCharUUID, st.characterTextureId, si));
         }
      }

      for (PGCharacter ch : st.attachment.getOwnedCharacters()) {
         if (ch != null && ch.getData() != null && ch.getCharacterUUID() != st.currentCharUUID) {
            ItemStack chEq = ch.getData().getWeapon();
            if (!chEq.isEmpty() && isWeaponCompatible(st, chEq)) {
               list.add(new ScreenArtifactEquip.Ent(chEq.copy(), false, -1, true, ch.getCharacterUUID(), ch.getTextureId(), si));
            }
         }
      }

      ArrayList<ItemStack> arr = bp.getCategoryList(Backpack.Category.WEAPONS);
      ArrayList<ScreenArtifactEquip.Ent> backpackList = new ArrayList<>();

      for (int i = 0; i < arr.size(); i++) {
         ItemStack s = arr.get(i);
         if (!s.isEmpty() && isWeaponCompatible(st, s)) {
            backpackList.add(new ScreenArtifactEquip.Ent(s.copy(), true, i, true, -1, null, -1));
         }
      }

      backpackList.sort((a, b) -> compareWeapons(a.s, b.s));
      list.addAll(backpackList);
      renderList(c, list, bp, st);
   }

   private static int compareWeapons(ItemStack a, ItemStack b) {
      if (a.getItem() instanceof WeaponItem wa) {
         if (b.getItem() instanceof WeaponItem wb) {
            int c = Integer.compare(wb.getStar(), wa.getStar());
            if (c != 0) {
               return c;
            }

            WeaponStatsComponent sa = (WeaponStatsComponent)a.getOrDefault(
               (DataComponentType)ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT
            );
            WeaponStatsComponent sb = (WeaponStatsComponent)b.getOrDefault(
               (DataComponentType)ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT
            );
            return Integer.compare(sb.level, sa.level);
         } else {
            return -1;
         }
      } else {
         return 1;
      }
   }

   private static void fillLC(UIElement c, Backpack bp, ScreenArtifactEquip.St st) {
      c.clearAllChildren();
      st.sel = null;
      if (st.type == null) {
         fillWeaponLC(c, bp, st);
      } else {
         int si = ArtifactInventory.typeToSlot(st.type);
         ArrayList<ScreenArtifactEquip.Ent> list = new ArrayList<>();
         PGCharacter currentChar = st.attachment.getCharacterByUUID(st.currentCharUUID);
         if (currentChar != null && currentChar.getData() != null) {
            ArtifactInventory currentInv = currentChar.getData().getArtifactInventory();
            ItemStack currentEq = currentInv.getItem(si);
            if (!currentEq.isEmpty()) {
               list.add(new ScreenArtifactEquip.Ent(currentEq.copy(), false, -1, true, st.currentCharUUID, st.characterTextureId, si));
            }
         }

         for (PGCharacter ch : st.attachment.getOwnedCharacters()) {
            if (ch != null && ch.getData() != null && ch.getCharacterUUID() != st.currentCharUUID) {
               ArtifactInventory chInv = ch.getData().getArtifactInventory();
               ItemStack chEq = chInv.getItem(si);
               if (!chEq.isEmpty() && chEq.getItem() instanceof ArtifactItem ai && ai.getType() == st.type) {
                  list.add(new ScreenArtifactEquip.Ent(chEq.copy(), false, -1, true, ch.getCharacterUUID(), ch.getTextureId(), si));
               }
            }
         }

         ArrayList<ItemStack> arr = bp.getCategoryList(Backpack.Category.ARTIFACTS);
         ArrayList<ScreenArtifactEquip.Ent> activatedList = new ArrayList<>();
         ArrayList<ScreenArtifactEquip.Ent> inactivatedList = new ArrayList<>();

         for (int i = 0; i < arr.size(); i++) {
            ItemStack s = arr.get(i);
            if (!s.isEmpty() && s.getItem() instanceof ArtifactItem ai && ai.getType() == st.type) {
               ArtifactStatsComponent stats = (ArtifactStatsComponent)s.getOrDefault(
                  (DataComponentType)ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT
               );
               if (stats.activated) {
                  activatedList.add(new ScreenArtifactEquip.Ent(s.copy(), true, i, true, -1, null, -1));
               } else {
                  inactivatedList.add(new ScreenArtifactEquip.Ent(s.copy(), true, i, false, -1, null, -1));
               }
            }
         }

         activatedList.sort((a, b) -> compareEntries(a.s, b.s));
         inactivatedList.sort((a, b) -> compareEntries(a.s, b.s));
         list.addAll(activatedList);
         list.addAll(inactivatedList);
         renderList(c, list, bp, st);
      }
   }

   private static void renderList(UIElement c, ArrayList<ScreenArtifactEquip.Ent> list, Backpack bp, ScreenArtifactEquip.St st) {
      for (int i = 0; i < list.size(); i++) {
         ScreenArtifactEquip.Ent e = list.get(i);
         UIElement el = new UIElement().addClass("artifact-list-item");
         el.style(x -> x.background(SpriteTexture.of(tex(e.s))));
         if (!e.activated) {
            el.addClass("artifact-list-inactivated");
         }

         if (!e.fromBackpack && e.ownerTextureId != null) {
            UIElement avatar = new UIElement().addClass("artifact-avatar-overlay");
            avatar.style(x -> x.background(SpriteTexture.of(CharacterResourceSources.avatarHud(e.ownerTextureId))));
            el.addChild(avatar);
         }

         boolean sel = isSelected(e, st);
         if (sel) {
            el.style(x -> x.overlay(SpriteTexture.of("minegenshin:gui/selected_border.png")));
            el.transform(t -> t.scale(1.1F));
            st.sel = el;
         }

         el.addEventListener("mouseEnter", ev -> {
            if (st.sel != el) {
               el.style(x -> x.overlay(SpriteTexture.of("minegenshin:gui/selected_border.png")));
               el.transform(t -> t.scale(1.1F));
            }
         });
         el.addEventListener("mouseLeave", ev -> {
            if (st.sel != el) {
               el.style(x -> x.overlay(null));
               el.transform(t -> t.scale(1.0F));
            }
         });
         el.addEventListener("mouseDown", ev -> el.transform(t -> t.scale(1.0F)));
         el.addEventListener("mouseClick", ev -> {
            UIElement prev = st.sel;
            if (prev != null && prev != el) {
               prev.style(x -> x.overlay(null));
               prev.transform(t -> t.scale(1.0F));
            }

            st.sel = el;
            el.style(x -> x.overlay(SpriteTexture.of("minegenshin:gui/selected_border.png")));
            el.transform(t -> t.scale(1.1F));
            st.selectedEnt = e;
            if (st.mp != null && st.rp != null) {
               fillDetail(st.mp, st.rp, bp, st);
            }
         });
         c.addChild(el);
      }

      c.markAsInternal();
   }

   private static boolean isSelected(ScreenArtifactEquip.Ent e, ScreenArtifactEquip.St st) {
      if (st.selectedEnt == null) {
         return false;
      } else if (e.fromBackpack != st.selectedEnt.fromBackpack) {
         return false;
      } else {
         return e.fromBackpack
            ? e.backpackIdx == st.selectedEnt.backpackIdx
            : e.ownerUUID == st.selectedEnt.ownerUUID && e.ownerSlot == st.selectedEnt.ownerSlot;
      }
   }

   private static int compareEntries(ItemStack a, ItemStack b) {
      if (a.getItem() instanceof ArtifactItem aa) {
         if (!(b.getItem() instanceof ArtifactItem bb)) {
            return -1;
         } else {
            int c = Integer.compare(bb.getStar(), aa.getStar());
            if (c != 0) {
               return c;
            }

            ArtifactStatsComponent sa = (ArtifactStatsComponent)a.getOrDefault(
               (DataComponentType)ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT
            );
            ArtifactStatsComponent sb = (ArtifactStatsComponent)b.getOrDefault(
               (DataComponentType)ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT
            );
            c = Integer.compare(sb.level, sa.level);
            if (c != 0) {
               return c;
            }

            int setIda = aa.getSet() != null && aa.getSet().get() != null ? ((ArtifactSet)aa.getSet().get()).setId() : 999;
            int setIdb = bb.getSet() != null && bb.getSet().get() != null ? ((ArtifactSet)bb.getSet().get()).setId() : 999;
            c = Integer.compare(setIda, setIdb);
            return c != 0 ? c : Integer.compare(aa.getType().ordinal(), bb.getType().ordinal());
         }
      } else {
         return 1;
      }
   }

   private static void fillWeaponDetail(UIElement mp, UIElement rp, Backpack bp, ScreenArtifactEquip.St st) {
      mp.clearAllChildren();
      rp.clearAllChildren();
      int slot = 5;
      ScreenArtifactEquip.Ent ent = st.selectedEnt;
      if (ent != null && !ent.s.isEmpty()) {
         ItemStack disp = ent.s;
         UIElement ic = new UIElement().addClass("artifact-detail-icon");
         ic.style(x -> x.background(SpriteTexture.of(tex(disp))));
         ic.markAsInternal();
         mp.addChild(ic);
         mp.markAsInternal();
         if (disp.getItem() instanceof WeaponItem wi) {
            WeaponStatsComponent var17 = (WeaponStatsComponent)disp.getOrDefault(
               (DataComponentType)ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT
            );
            int star = wi.getStar();
            UIElement info = new UIElement().setId("info-container");
            info.addChild(new Label().setText(disp.getHoverName()).addClass("info-name"));
            info.addChild(new Label().setText(Component.literal("★".repeat(star)).withStyle(ChatFormatting.GOLD)).addClass("info-star"));
            if (!ent.fromBackpack && ent.ownerTextureId != null) {
               PGCharacter owner = st.attachment.getCharacterByUUID(ent.ownerUUID);
               String ownerName = owner != null ? owner.getName().getString() : "?";
               ChatFormatting color = ent.ownerUUID == st.currentCharUUID ? ChatFormatting.GOLD : ChatFormatting.AQUA;
               info.addChild(
                  new Label()
                     .setText(Component.translatable("gui.minegenshin.artifact_equip.equipped_by", new Object[]{ownerName}).withStyle(color))
                     .addClass("info-level")
               );
            }

            info.addChild(
               new Label()
                  .setText(Component.translatable("gui.minegenshin.artifact_equip.level_format", new Object[]{var17.level}).withStyle(ChatFormatting.GRAY))
                  .addClass("info-level")
            );
            if (var17.mainStat != null && var17.mainStat.isInitialized()) {
               info.addChild(new Label().setText(Component.literal(statTxt(var17.mainStat)).withStyle(ChatFormatting.YELLOW)).addClass("info-main-stat"));
            }

            if (var17.subStat != null && var17.subStat.isInitialized()) {
               info.addChild(new Label().setText(Component.literal(statTxt(var17.subStat)).withStyle(ChatFormatting.GRAY)).addClass("info-sub-stat"));
            }

            UIElement bc = new UIElement().setId("button-container");
            Button act = new Button();
            act.addClass("action-button");
            if (ent.fromBackpack) {
               ItemStack te = disp.copy();
               boolean currentSlotEmpty = currentSlotIsEmpty(st);
               act.setText(Component.translatable(currentSlotEmpty ? "gui.minegenshin.artifact_equip.equip" : "gui.minegenshin.artifact_equip.change"));
               int ii = ent.backpackIdx;
               act.setOnClick(e -> {
                  if (ArtifactInventory.isValidForSlot(slot, te)) {
                     NetworkManager.sendEquipOrSwapArtifactToServer(slot, ii);
                     PGCharacter currentChar = st.attachment.getCharacterByUUID(st.currentCharUUID);
                     if (currentChar != null && currentChar.getData() != null) {
                        ArtifactInventory curInv = currentChar.getData().getArtifactInventory();
                        ItemStack old = curInv.getItem(slot);
                        bp.removeItemFromCategory(Backpack.Category.WEAPONS, ii);
                        curInv.setItem(slot, te.copy());
                        if (!old.isEmpty()) {
                           bp.addItemToCategory(Backpack.Category.WEAPONS, old.copy());
                        }
                     }

                     autoSel(bp, st);
                     if (st.lc != null) {
                        fillWeaponLC(st.lc, bp, st);
                     }

                     fillWeaponDetail(mp, rp, bp, st);
                  }
               });
            } else if (ent.ownerUUID == st.currentCharUUID) {
               act.setText(Component.translatable("gui.minegenshin.artifact_equip.unequip"));
               ItemStack tu = disp.copy();
               act.setOnClick(e -> {
                  NetworkManager.sendUnequipArtifactToServer(slot);
                  PGCharacter currentChar = st.attachment.getCharacterByUUID(st.currentCharUUID);
                  if (currentChar != null && currentChar.getData() != null) {
                     ArtifactInventory curInv = currentChar.getData().getArtifactInventory();
                     curInv.setItem(slot, ItemStack.EMPTY);
                  }

                  bp.addItemToCategory(Backpack.Category.WEAPONS, tu);
                  autoSel(bp, st);
                  if (st.lc != null) {
                     fillWeaponLC(st.lc, bp, st);
                  }

                  fillWeaponDetail(mp, rp, bp, st);
               });
            } else {
               act.setText(Component.translatable("gui.minegenshin.artifact_equip.swap"));
               int targetUUID = ent.ownerUUID;
               act.setOnClick(e -> {
                  PGCharacter currentChar = st.attachment.getCharacterByUUID(st.currentCharUUID);
                  PGCharacter targetChar = st.attachment.getCharacterByUUID(targetUUID);
                  if (currentChar != null && currentChar.getData() != null && targetChar != null && targetChar.getData() != null) {
                     ArtifactInventory curInv = currentChar.getData().getArtifactInventory();
                     ArtifactInventory tgtInv = targetChar.getData().getArtifactInventory();
                     ItemStack a = curInv.getItem(slot);
                     ItemStack b = tgtInv.getItem(slot);
                     if (ArtifactInventory.isValidForSlot(slot, b)) {
                        if (ArtifactInventory.isValidForSlot(slot, a)) {
                           curInv.setItem(slot, b.copy());
                           tgtInv.setItem(slot, a.copy());
                           st.attachment.syncToServer();
                           autoSel(bp, st);
                           if (st.lc != null) {
                              fillWeaponLC(st.lc, bp, st);
                           }

                           fillWeaponDetail(mp, rp, bp, st);
                        }
                     }
                  }
               });
            }

            bc.addChild(act);
            info.addChild(bc);
            rp.addChild(info);
            rp.markAsInternal();
         }
      } else {
         rp.addChild(new Label().setText(Component.translatable("gui.minegenshin.artifact_equip.no_weapon")).setId("info-empty"));
         rp.markAsInternal();
      }
   }

   private static void fillDetail(UIElement mp, UIElement rp, Backpack bp, ScreenArtifactEquip.St st) {
      mp.clearAllChildren();
      rp.clearAllChildren();
      int slot = st.slot;
      if (slot >= 0 && slot < 6) {
         if (st.type == null) {
            fillWeaponDetail(mp, rp, bp, st);
         } else {
            ScreenArtifactEquip.Ent ent = st.selectedEnt;
            if (ent != null && !ent.s.isEmpty()) {
               ItemStack disp = ent.s;
               UIElement ic = new UIElement().addClass("artifact-detail-icon");
               ic.style(x -> x.background(SpriteTexture.of(tex(disp))));
               ic.markAsInternal();
               mp.addChild(ic);
               mp.markAsInternal();
               if (disp.getItem() instanceof ArtifactItem ai) {
                  ArtifactStatsComponent var20 = (ArtifactStatsComponent)disp.getOrDefault(
                     (DataComponentType)ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT
                  );
                  int star = ai.getStar();
                  boolean activated = var20.activated;
                  UIElement info = new UIElement().setId("info-container");
                  info.addChild(new Label().setText(disp.getHoverName()).addClass("info-name"));
                  info.addChild(new Label().setText(Component.literal("★".repeat(star)).withStyle(ChatFormatting.GOLD)).addClass("info-star"));
                  if (!activated) {
                     info.addChild(
                        new Label()
                           .setText(Component.translatable("gui.minegenshin.artifact_equip.not_activated").withStyle(ChatFormatting.RED))
                           .addClass("info-inactivated")
                     );
                  }

                  if (!ent.fromBackpack && ent.ownerTextureId != null) {
                     PGCharacter owner = st.attachment.getCharacterByUUID(ent.ownerUUID);
                     String ownerName = owner != null ? owner.getName().getString() : "?";
                     ChatFormatting color = ent.ownerUUID == st.currentCharUUID ? ChatFormatting.GOLD : ChatFormatting.AQUA;
                     info.addChild(
                        new Label()
                           .setText(Component.translatable("gui.minegenshin.artifact_equip.equipped_by", new Object[]{ownerName}).withStyle(color))
                           .addClass("info-level")
                     );
                  }

                  info.addChild(
                     new Label()
                        .setText(
                           Component.translatable("gui.minegenshin.artifact_equip.level_format", new Object[]{var20.level}).withStyle(ChatFormatting.GRAY)
                        )
                        .addClass("info-level")
                  );
                  long ex = var20.getExpToNextLevel(star);
                  info.addChild(
                     new Label()
                        .setText(
                           Component.translatable(
                                 ex > 0L ? "gui.minegenshin.artifact_equip.exp_format" : "gui.minegenshin.artifact_equip.exp_max", new Object[]{var20.exp, ex}
                              )
                              .withStyle(ChatFormatting.GRAY)
                        )
                        .addClass("info-exp")
                  );
                  if (var20.mainStat != null && var20.mainStat.isInitialized()) {
                     info.addChild(new Label().setText(Component.literal(statTxt(var20.mainStat)).withStyle(ChatFormatting.YELLOW)).addClass("info-main-stat"));
                  }

                  if (var20.subStats != null) {
                     for (TeyvatItemStat ss : var20.subStats) {
                        if (ss.isInitialized()) {
                           info.addChild(
                              new Label()
                                 .setText(Component.literal(statTxt(ss)).withStyle(ss.isUnlocked() ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY))
                                 .addClass("info-sub-stat")
                           );
                        }
                     }
                  }

                  UIElement bc = new UIElement().setId("button-container");
                  Button act = new Button();
                  act.addClass("action-button");
                  if (ent.fromBackpack) {
                     if (!activated) {
                        act.setText(Component.translatable("gui.minegenshin.artifact_equip.activate"));
                        int ii = ent.backpackIdx;
                        act.setOnClick(e -> {
                           NetworkManager.sendActivateArtifactToServer(ii);
                           int globalSlot = getCategoryOffset(Backpack.Category.ARTIFACTS) + ii;
                           ItemStack local = bp.getItem(globalSlot);
                           if (!local.isEmpty() && local.getItem() instanceof ArtifactItem) {
                              st.selectedEnt = new ScreenArtifactEquip.Ent(local.copy(), true, ii, true, -1, null, -1);
                           }

                           if (st.lc != null) {
                              fillLC(st.lc, bp, st);
                           }

                           fillDetail(mp, rp, bp, st);
                        });
                     } else {
                        boolean currentSlotEmpty = currentSlotIsEmpty(st);
                        act.setText(Component.translatable(currentSlotEmpty ? "gui.minegenshin.artifact_equip.equip" : "gui.minegenshin.artifact_equip.change"));
                        int ii = ent.backpackIdx;
                        ItemStack te = disp.copy();
                        act.setOnClick(e -> {
                           if (ArtifactInventory.isValidForSlot(slot, te)) {
                              NetworkManager.sendEquipOrSwapArtifactToServer(slot, ii);
                              PGCharacter currentChar = st.attachment.getCharacterByUUID(st.currentCharUUID);
                              if (currentChar != null && currentChar.getData() != null) {
                                 ArtifactInventory curInv = currentChar.getData().getArtifactInventory();
                                 ItemStack old = curInv.getItem(slot);
                                 bp.removeItemFromCategory(Backpack.Category.ARTIFACTS, ii);
                                 curInv.setItem(slot, te.copy());
                                 if (!old.isEmpty()) {
                                    bp.addItemToCategory(Backpack.Category.ARTIFACTS, old.copy());
                                 }
                              }

                              autoSel(bp, st);
                              if (st.lc != null) {
                                 fillLC(st.lc, bp, st);
                              }

                              fillDetail(mp, rp, bp, st);
                           }
                        });
                     }
                  } else if (ent.ownerUUID == st.currentCharUUID) {
                     act.setText(Component.translatable("gui.minegenshin.artifact_equip.unequip"));
                     ItemStack tu = disp.copy();
                     act.setOnClick(e -> {
                        NetworkManager.sendUnequipArtifactToServer(slot);
                        PGCharacter currentChar = st.attachment.getCharacterByUUID(st.currentCharUUID);
                        if (currentChar != null && currentChar.getData() != null) {
                           ArtifactInventory curInv = currentChar.getData().getArtifactInventory();
                           curInv.setItem(slot, ItemStack.EMPTY);
                        }

                        bp.addItemToCategory(Backpack.Category.ARTIFACTS, tu);
                        autoSel(bp, st);
                        if (st.lc != null) {
                           fillLC(st.lc, bp, st);
                        }

                        fillDetail(mp, rp, bp, st);
                     });
                  } else {
                     act.setText(Component.translatable("gui.minegenshin.artifact_equip.swap"));
                     int targetUUID = ent.ownerUUID;
                     act.setOnClick(e -> {
                        PGCharacter currentChar = st.attachment.getCharacterByUUID(st.currentCharUUID);
                        PGCharacter targetChar = st.attachment.getCharacterByUUID(targetUUID);
                        if (currentChar != null && currentChar.getData() != null && targetChar != null && targetChar.getData() != null) {
                           ArtifactInventory curInv = currentChar.getData().getArtifactInventory();
                           ArtifactInventory tgtInv = targetChar.getData().getArtifactInventory();
                           ItemStack a = curInv.getItem(slot);
                           ItemStack b = tgtInv.getItem(slot);
                           if (ArtifactInventory.isValidForSlot(slot, b)) {
                              if (ArtifactInventory.isValidForSlot(slot, a)) {
                                 curInv.setItem(slot, b.copy());
                                 tgtInv.setItem(slot, a.copy());
                                 currentChar.recalculateDirtyArtifactSlots();
                                 targetChar.recalculateDirtyArtifactSlots();
                                 st.attachment.syncToServer();
                                 autoSel(bp, st);
                                 if (st.lc != null) {
                                    fillLC(st.lc, bp, st);
                                 }

                                 fillDetail(mp, rp, bp, st);
                              }
                           }
                        }
                     });
                  }

                  Button up = new Button();
                  up.setText(Component.translatable("gui.minegenshin.artifact_equip.upgrade"));
                  up.addClass("upgrade-button");
                  up.setOnClick(e -> NetworkManager.sendArtifactLevelUpToServer(disp, 10000));
                  if (!activated || var20.level >= var20.getMaxLevel(star)) {
                     up.setDisplay(false);
                  }

                  bc.addChildren(new UIElement[]{act, up});
                  info.addChild(bc);
                  rp.addChild(info);
                  rp.markAsInternal();
               }
            } else {
               rp.addChild(new Label().setText(Component.translatable("gui.minegenshin.artifact_equip.no_artifact")).setId("info-empty"));
               rp.markAsInternal();
            }
         }
      }
   }

   private static UIElement byId(UIElement p, String id) {
      if (id.equals(p.getId())) {
         return p;
      }

      for (UIElement c : p.getChildren()) {
         UIElement r = byId(c, id);
         if (r != null) {
            return r;
         }
      }

      return null;
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

   private static boolean currentSlotIsEmpty(ScreenArtifactEquip.St st) {
      PGCharacter currentChar = st.attachment.getCharacterByUUID(st.currentCharUUID);
      return currentChar != null && currentChar.getData() != null ? currentChar.getData().getArtifactInventory().getItem(st.slot).isEmpty() : true;
   }

   private static String tex(ItemStack s) {
      return ItemIcons.pathOf(s);
   }

   private static String statTxt(TeyvatItemStat s) {
      if (!s.isInitialized()) {
         return "";
      }

      String n = Component.translatable(s.getAttribute().translationKey()).getString();
      return s.getKind() == TeyvatItemStat.StatKind.PERCENT
         ? n + " +" + String.format("%.1f%%", s.getValue() * 100.0)
         : n + " +" + String.format("%.0f", s.getValue());
   }

   private static boolean isWeaponCompatible(ScreenArtifactEquip.St st, ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      }

      PGCharacter currentChar = st.attachment.getCharacterByUUID(st.currentCharUUID);
      return currentChar != null && currentChar.canEquipWeapon(stack);
   }

   private record Ent(ItemStack s, boolean fromBackpack, int backpackIdx, boolean activated, int ownerUUID, String ownerTextureId, int ownerSlot) {
   }

   private static final class St {
      int slot;
      ArtifactType type;
      ScreenArtifactEquip.Ent selectedEnt;
      UIElement sel;
      UIElement top;
      UIElement det;
      UIElement lc;
      UIElement mp;
      UIElement rp;
      String characterTextureId;
      int currentCharUUID;
      PlayerCharactersAttachment attachment;
      ArtifactInventory inv;

      St(int slot, ArtifactType type) {
         this.slot = slot;
         this.type = type;
      }
   }
}
