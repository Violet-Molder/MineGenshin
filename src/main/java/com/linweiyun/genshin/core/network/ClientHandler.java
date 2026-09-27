// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.network;

import com.linweiyun.genshin.client.render.gui.screen.CharacterEquipUI;
import com.linweiyun.genshin.client.render.gui.screen.ScreenArtifactEquip;
import com.linweiyun.genshin.core.attachment.AdventurerInfoAttachment;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.Backpack;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.sync.ISyncCharacter;
import com.linweiyun.genshin.core.sync.ISyncManagedEntity;
import com.linweiyun.genshin.core.world.TeyvatWorldInvasion;
import java.util.BitSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;

public class ClientHandler {
   public static void primogemClientHandler(int amount) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && !mc.player.isRemoved()) {
         mc.player.setData(AttachmentRegistration.PRIMOGEM_ATTACHMENT, amount);
      }
   }

   public static void genshinModeClientHandler(boolean isGenshinMode) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && !mc.player.isRemoved()) {
         mc.player.setData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT, isGenshinMode);
      }
   }

   public static void adventurerInfoClientHandler(CompoundTag data) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && !mc.player.isRemoved()) {
         AdventurerInfoAttachment attachment = (AdventurerInfoAttachment)mc.player.getData(AttachmentRegistration.ADVENTURER_INFO_ATTACHMENT);
         attachment.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, mc.player.registryAccess(), data));
      }
   }

   public static void playerCharactersClientHandler(CompoundTag data) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && !mc.player.isRemoved()) {
         PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)mc.player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
         attachment.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, mc.player.registryAccess(), data));
         attachment.fixCharacterTypes();
      }
   }

   public static void characterDataClientHandler(int uuid, CompoundTag data) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && !mc.player.isRemoved()) {
         PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)mc.player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
         PGCharacter character = attachment.getCharacterByUUID(uuid);
         if (character != null) {
            character.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, mc.player.registryAccess(), data));
         }
      }
   }

   public static void characterSelectionClientHandler(int index) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && !mc.player.isRemoved()) {
         PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)mc.player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
         attachment.setCurrentCharacterIndex(index);
      }
   }

   public static void setPartyCharacterClientHandler(int index, int characterUUID) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && !mc.player.isRemoved()) {
         PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)mc.player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
         attachment.setPartyCharacter(index, characterUUID);
      }
   }

   public static void removePartyCharacterClientHandler(int index) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && !mc.player.isRemoved()) {
         PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)mc.player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
         attachment.removePartyCharacter(index);
      }
   }

   public static void addCharacterClientHandler(CompoundTag characterData) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && !mc.player.isRemoved()) {
         PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)mc.player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
         PGCharacter character = new PGCharacter();
         character.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, mc.player.registryAccess(), characterData));
         attachment.addCharacter(character, mc.player);
      }
   }

   public static void removeCharacterClientHandler(int uuid) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && !mc.player.isRemoved()) {
         PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)mc.player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
         attachment.removeCharacter(uuid);
      }
   }

   public static void equipOrSwapArtifactClientHandler(int artifactSlotIndex, int inventorySlotIndex) {
   }

   public static void unequipArtifactClientHandler(int artifactSlotIndex) {
   }

   public static void applyActivatedArtifactClientHandler(int inventorySlotIndex, ItemStack activated) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         if (activated != null && !activated.isEmpty()) {
            Backpack backpack = (Backpack)player.getData(AttachmentRegistration.BACKPACK_ATTACHMENT);
            int globalSlot = 0;

            for (Backpack.Category category : Backpack.Category.values()) {
               if (category == Backpack.Category.ARTIFACTS) {
                  break;
               }

               globalSlot += category.maxCapacity;
            }

            globalSlot += inventorySlotIndex;
            backpack.setItem(globalSlot, activated.copy());
            ScreenArtifactEquip.refreshIfOpen();
            CharacterEquipUI.refreshIfOpen();
         }
      }
   }

   public static void applyActivatedInventoryArtifact(int playerSlotIndex, ItemStack activated) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         if (activated != null && !activated.isEmpty()) {
            Inventory inventory = player.getInventory();
            if (playerSlotIndex >= 0 && playerSlotIndex < inventory.getContainerSize()) {
               inventory.setItem(playerSlotIndex, activated.copy());
               CharacterEquipUI.refreshIfOpen();
            }
         }
      }
   }

   public static void invasionStatusClientHandler(boolean invaded) {
      TeyvatWorldInvasion.setClientInvaded(invaded);
   }

   public static void entitySyncClientHandler(int entityId, CompoundTag payload) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         if (mc.level.getEntity(entityId) instanceof ISyncManagedEntity syncEntity) {
            BitSet changed = BitSet.valueOf(payload.getLongArray("changed").orElse(new long[0]));
            byte[] data = payload.getByteArray("data").orElse(new byte[0]);
            CompoundTag extra = payload.getCompoundOrEmpty("extra");
            syncEntity.handleSyncPacket(mc.level.registryAccess(), changed, data, extra);
         }
      }
   }

   public static void characterSyncClientHandler(int characterUUID, CompoundTag payload) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)mc.player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
         if (attachment.getCharacterByUUID(characterUUID) instanceof ISyncCharacter syncChar) {
            syncChar.handleCharacterSyncPacket(payload);
         }
      }
   }
}
