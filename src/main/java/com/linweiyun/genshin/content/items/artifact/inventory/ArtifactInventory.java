// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.content.items.artifact.inventory;

import com.linweiyun.genshin.content.items.artifact.ArtifactItem;
import com.linweiyun.genshin.content.items.artifact.type.ArtifactType;
import com.linweiyun.genshin.content.items.component.ArtifactStatsComponent;
import com.linweiyun.genshin.content.items.weapon.WeaponItem;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import com.linweiyun.genshin.core.system.registry.register.ModDataComponents;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import java.util.List;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

public class ArtifactInventory implements Container, IPersistedSerializable {
   private static final Logger LOGGER = ModLog.getLogger(LogGroup.CONTENT);
   public static final int SLOT_COUNT = 6;
   public static final int SLOT_FLOWER = 0;
   public static final int SLOT_PLUME = 1;
   public static final int SLOT_SANDS = 2;
   public static final int SLOT_GOBLET = 3;
   public static final int SLOT_CIRCLET = 4;
   public static final int SLOT_WEAPON = 5;
   @Persisted(key = "flower")
   private ItemStack flower = ItemStack.EMPTY;
   @Persisted(key = "plume")
   private ItemStack plume = ItemStack.EMPTY;
   @Persisted(key = "sands")
   private ItemStack sands = ItemStack.EMPTY;
   @Persisted(key = "goblet")
   private ItemStack goblet = ItemStack.EMPTY;
   @Persisted(key = "circlet")
   private ItemStack circlet = ItemStack.EMPTY;
   @Persisted(key = "weapon")
   private ItemStack weapon = ItemStack.EMPTY;
   private final boolean[] dirtyFlags = new boolean[this.slotCount()];
   private Runnable onChange = () -> {};

   public int slotCount() {
      return 6;
   }

   public void setOnChange(Runnable onChange) {
      this.onChange = onChange;
   }

   public IItemHandlerModifiable asResourceHandler() {
      return new InvWrapper(this);
   }

   public static int typeToSlot(ArtifactType type) {
      return switch (type) {
         case FLOWER -> 0;
         case PLUME -> 1;
         case SANDS -> 2;
         case GOBLET -> 3;
         case CIRCLET -> 4;
      };
   }

   public static ArtifactType slotToType(int slot) {
      return switch (slot) {
         case 0 -> ArtifactType.FLOWER;
         case 1 -> ArtifactType.PLUME;
         case 2 -> ArtifactType.SANDS;
         case 3 -> ArtifactType.GOBLET;
         case 4 -> ArtifactType.CIRCLET;
         default -> null;
      };
   }

   public static boolean isValidForSlot(int slot, ItemStack stack) {
      if (stack.isEmpty()) {
         return true;
      }

      if (slot >= 5) {
         return stack.getItem() instanceof WeaponItem;
      }

      if (stack.getItem() instanceof ArtifactItem artifact) {
         ArtifactStatsComponent stats = (ArtifactStatsComponent)stack.getOrDefault(
            (DataComponentType)ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT
         );
         if (!stats.activated) {
            return false;
         }

         ArtifactType expected = slotToType(slot);
         return artifact.getType() == expected;
      } else {
         return false;
      }
   }

   public void markDirty(int slot) {
      if (slot >= 0 && slot < this.slotCount()) {
         this.dirtyFlags[slot] = true;
      }
   }

   public boolean isDirty(int slot) {
      return slot >= 0 && slot < this.slotCount() && this.dirtyFlags[slot];
   }

   public void clearDirty(int slot) {
      if (slot >= 0 && slot < this.slotCount()) {
         this.dirtyFlags[slot] = false;
      }
   }

   public boolean hasDirtySlots() {
      for (int i = 0; i < this.slotCount(); i++) {
         if (this.dirtyFlags[i]) {
            return true;
         }
      }

      return false;
   }

   protected ItemStack getStackBySlot(int slot) {
      return switch (slot) {
         case 0 -> this.flower;
         case 1 -> this.plume;
         case 2 -> this.sands;
         case 3 -> this.goblet;
         case 4 -> this.circlet;
         case 5 -> this.weapon;
         default -> throw new IllegalStateException("Unexpected value: " + slot);
      };
   }

   protected void setStackBySlot(int slot, ItemStack stack) {
      switch (slot) {
         case 0:
            this.flower = stack;
            break;
         case 1:
            this.plume = stack;
            break;
         case 2:
            this.sands = stack;
            break;
         case 3:
            this.goblet = stack;
            break;
         case 4:
            this.circlet = stack;
            break;
         case 5:
            this.weapon = stack;
      }
   }

   public int getContainerSize() {
      return this.slotCount();
   }

   public boolean isEmpty() {
      return this.flower.isEmpty() && this.plume.isEmpty() && this.sands.isEmpty() && this.goblet.isEmpty() && this.circlet.isEmpty() && this.weapon.isEmpty();
   }

   public @NonNull ItemStack getItem(int slot) {
      return this.getStackBySlot(slot);
   }

   public @NonNull ItemStack removeItem(int slot, int amount) {
      ItemStack existing = this.getStackBySlot(slot);
      if (existing.isEmpty()) {
         return ItemStack.EMPTY;
      }

      int toRemove = Math.min(amount, existing.getCount());
      ItemStack result = existing.copyWithCount(toRemove);
      if (toRemove >= existing.getCount()) {
         this.setStackBySlot(slot, ItemStack.EMPTY);
         this.markDirty(slot);
      } else {
         existing.shrink(toRemove);
      }

      this.setChanged();
      LOGGER.info("ARTIFACTremoveItem");
      return result;
   }

   public @NonNull ItemStack removeItemNoUpdate(int slot) {
      ItemStack existing = this.getStackBySlot(slot);
      if (existing.isEmpty()) {
         return ItemStack.EMPTY;
      }

      this.setStackBySlot(slot, ItemStack.EMPTY);
      this.markDirty(slot);
      return existing;
   }

   public void setItem(int slot, ItemStack stack) {
      if (stack.isEmpty() || isValidForSlot(slot, stack)) {
         this.setStackBySlot(slot, stack);
         this.markDirty(slot);
         this.setChanged();
         LOGGER.info("ARTIFACTsetItem");
      }
   }

   public boolean canPlaceItem(int slot, ItemStack stack) {
      return isValidForSlot(slot, stack);
   }

   public int getMaxStackSize() {
      return 1;
   }

   public void setChanged() {
      this.onChange.run();
   }

   public boolean stillValid(Player player) {
      return true;
   }

   public void clearContent() {
      this.flower = ItemStack.EMPTY;
      this.plume = ItemStack.EMPTY;
      this.sands = ItemStack.EMPTY;
      this.goblet = ItemStack.EMPTY;
      this.circlet = ItemStack.EMPTY;

      for (int i = 0; i < this.slotCount(); i++) {
         this.markDirty(i);
      }

      this.setChanged();
      LOGGER.info("ARTIFACTclearContent");
   }

   public List<ItemStack> getAllArtifactsAsList() {
      return List.of(this.flower, this.plume, this.sands, this.goblet, this.circlet);
   }

   public ArtifactInventory copy() {
      ArtifactInventory inv = new ArtifactInventory();
      inv.flower = this.flower.copy();
      inv.plume = this.plume.copy();
      inv.sands = this.sands.copy();
      inv.goblet = this.goblet.copy();
      inv.circlet = this.circlet.copy();
      return inv;
   }
}
