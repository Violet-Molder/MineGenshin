// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.content.items.artifact.inventory;

import com.linweiyun.genshin.core.character.util.appearance.WeaponAppearance;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import net.minecraft.world.item.ItemStack;

public class AllWeaponArtifactInventory extends ArtifactInventory {
   public static final int SLOT_WEAPON_LAST = 10;
   @Persisted(key = "weapon2")
   private ItemStack weapon2 = ItemStack.EMPTY;
   @Persisted(key = "weapon3")
   private ItemStack weapon3 = ItemStack.EMPTY;
   @Persisted(key = "weapon4")
   private ItemStack weapon4 = ItemStack.EMPTY;
   @Persisted(key = "weapon5")
   private ItemStack weapon5 = ItemStack.EMPTY;
   @Persisted(key = "weapon6")
   private ItemStack weapon6 = ItemStack.EMPTY;

   public static int slotFor(WeaponAppearance weapon) {
      return 5 + weapon.ordinal();
   }

   @Override
   public int slotCount() {
      return 11;
   }

   private ItemStack extraAt(int slot) {
      return switch (slot) {
         case 6 -> this.weapon2;
         case 7 -> this.weapon3;
         case 8 -> this.weapon4;
         case 9 -> this.weapon5;
         default -> this.weapon6;
      };
   }

   private void setExtraAt(int slot, ItemStack stack) {
      switch (slot) {
         case 6:
            this.weapon2 = stack;
            break;
         case 7:
            this.weapon3 = stack;
            break;
         case 8:
            this.weapon4 = stack;
            break;
         case 9:
            this.weapon5 = stack;
            break;
         default:
            this.weapon6 = stack;
      }
   }

   @Override
   protected ItemStack getStackBySlot(int slot) {
      return slot > 5 ? this.extraAt(slot) : super.getStackBySlot(slot);
   }

   @Override
   protected void setStackBySlot(int slot, ItemStack stack) {
      if (slot > 5) {
         this.setExtraAt(slot, stack);
      } else {
         super.setStackBySlot(slot, stack);
      }
   }

   @Override
   public boolean isEmpty() {
      if (!super.isEmpty()) {
         return false;
      }

      for (int slot = 6; slot <= 10; slot++) {
         if (!this.extraAt(slot).isEmpty()) {
            return false;
         }
      }

      return true;
   }

   public AllWeaponArtifactInventory copy() {
      AllWeaponArtifactInventory copy = new AllWeaponArtifactInventory();

      for (int slot = 0; slot < this.slotCount(); slot++) {
         copy.setItem(slot, this.getItem(slot).copy());
      }

      return copy;
   }

   @Override
   public void clearContent() {
      super.clearContent();

      for (int slot = 5; slot <= 10; slot++) {
         this.setItem(slot, ItemStack.EMPTY);
      }
   }
}
