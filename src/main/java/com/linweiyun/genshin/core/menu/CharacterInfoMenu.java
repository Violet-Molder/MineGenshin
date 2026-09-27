// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.menu;

import com.linweiyun.genshin.content.items.artifact.inventory.ArtifactInventory;
import com.linweiyun.genshin.content.items.artifact.inventory.ArtifactSlot;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import com.linweiyun.genshin.core.system.registry.register.ModMenus;
import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.slf4j.Logger;

public class CharacterInfoMenu extends AbstractContainerMenu {
   private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);
   private final ArtifactInventory artifactInventory;
   private final int weaponSlotIndex;

   public CharacterInfoMenu(int containerId, Inventory playerInventory) {
      super(ModMenus.CHARACTER_INFO_MENU.get(), containerId);
      Player player = playerInventory.player;
      PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
      PGCharacter character = attachment.getCurrentCharacter();
      if (character != null && character.getData() != null) {
         this.artifactInventory = character.getData().getArtifactInventory();
      } else {
         this.artifactInventory = new ArtifactInventory();
      }

      this.weaponSlotIndex = character != null && character.getData() != null ? character.getData().activeWeaponSlot() : 5;

      for (int i = 0; i < this.artifactInventory.slotCount(); i++) {
         this.addSlot(new ArtifactSlot(this.artifactInventory, i, -9999, -9999));
      }

      for (int row = 0; row < 3; row++) {
         for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col + row * 9 + 9, -9999, -9999));
         }
      }

      for (int col = 0; col < 9; col++) {
         this.addSlot(new Slot(playerInventory, col, -9999, -9999));
      }

      ModularUI modularUI = this.createModularUI(player);
      if (this instanceof IModularUIHolderMenu holder) {
         holder.setModularUI(modularUI);
      }
   }

   private ModularUI createModularUI(Player player) {
      UIElement root = new UIElement();
      Stylesheet stylesheet = StylesheetManager.INSTANCE.getStylesheetSafe(Identifier.parse("minegenshin:lss/character_info.lss"));
      UIElement backGround = new UIElement();
      UIElement character_list_container = new UIElement()
         .setId("character_list_container")
         .layout(layoutStyle -> layoutStyle.flexDirection(FlexDirection.ROW));
      UIElement artifact_container = new UIElement().setId("artifact_container").layout(layoutStyle -> layoutStyle.flexDirection(FlexDirection.ROW));
      ResourceHandler<ItemResource> artifactHandler = this.artifactInventory.asResourceHandler();

      for (int i = 0; i < 5; i++) {
         artifact_container.addChildren(new UIElement[]{boundSlot(artifactHandler, i)});
      }

      artifact_container.addChildren(new UIElement[]{boundSlot(artifactHandler, this.weaponSlotIndex)});
      root.addChildren(new UIElement[]{backGround.addChildren(new UIElement[]{character_list_container, artifact_container, new InventorySlots()})});
      UI ui = UI.of(root, new Stylesheet[]{stylesheet});
      return ModularUI.of(ui, player);
   }

   private static ItemSlot boundSlot(ResourceHandler<ItemResource> handler, int index) {
      ItemSlot slot = new ItemSlot();
      slot.bind(handler, index);
      return slot;
   }

   public ArtifactInventory getArtifactInventory() {
      return this.artifactInventory;
   }

   public ItemStack quickMoveStack(Player player, int index) {
      Slot slot = (Slot)this.slots.get(index);
      if (!slot.hasItem()) {
         return ItemStack.EMPTY;
      }

      ItemStack slotStack = slot.getItem();
      ItemStack result = slotStack.copy();
      int artifactSlots = this.artifactInventory.slotCount();
      int totalSlots = this.slots.size();
      if (index < artifactSlots) {
         if (!this.moveItemStackTo(slotStack, artifactSlots, totalSlots, true)) {
            return ItemStack.EMPTY;
         }
      } else if (!this.moveItemStackTo(slotStack, 0, artifactSlots, false)) {
         return ItemStack.EMPTY;
      }

      if (slotStack.isEmpty()) {
         slot.setByPlayer(ItemStack.EMPTY);
      } else {
         slot.setChanged();
      }

      return result;
   }

   public void clicked(int slotIndex, int buttonNum, ContainerInput containerInput, Player player) {
      if (slotIndex >= 0 && slotIndex < this.artifactInventory.slotCount()) {
         if (slotIndex < this.slots.size()) {
            this.getSlot(slotIndex);
         } else {
            Object var10000 = null;
         }
      }

      super.clicked(slotIndex, buttonNum, containerInput, player);
   }

   public boolean stillValid(Player player) {
      return true;
   }
}
