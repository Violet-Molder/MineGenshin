// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.network;

import com.linweiyun.genshin.config.GenshinConfig;
import com.linweiyun.genshin.config.character.TalentConfigs;
import com.linweiyun.genshin.content.items.artifact.ArtifactItem;
import com.linweiyun.genshin.content.items.artifact.inventory.ArtifactInventory;
import com.linweiyun.genshin.content.items.component.ArtifactStatsComponent;
import com.linweiyun.genshin.content.items.component.WeaponStatsComponent;
import com.linweiyun.genshin.content.items.development.AdviceBookItem;
import com.linweiyun.genshin.content.items.weapon.WeaponItem;
import com.linweiyun.genshin.core.attachment.AdventurerInfoAttachment;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.Backpack;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.PGCharacterData;
import com.linweiyun.genshin.core.character.talent.TalentUpgradeCost;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import com.linweiyun.genshin.menu.CharacterInfoMenu;
import com.linweiyun.genshin.core.system.combat.action.ActionManager;
import com.linweiyun.genshin.core.system.combat.action.InterruptReason;
import com.linweiyun.genshin.core.system.combat.animation.server.ServerAnimationTicker;
import com.linweiyun.genshin.core.system.combat.attack.PlungeState;
import com.linweiyun.genshin.core.system.combat.flight.GenshinFlight;
import com.linweiyun.genshin.core.system.compat.PlayerStatBridge;
import com.linweiyun.genshin.core.system.registry.register.ModDataComponents;
import com.linweiyun.genshin.core.system.wish.WishSystem;
import com.lowdragmc.lowdraglib2.gui.factory.PlayerUIMenuType;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacket;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;
import com.lowdragmc.lowdraglib2.syncdata.rpc.RPCSender;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import org.slf4j.Logger;

public class NetworkManager {
   private static final Map<UUID, Integer> EDIT_TARGET = new ConcurrentHashMap<>();
   private static final Random RANDOM = new Random();
   private static final Logger LOGGER = ModLog.getLogger(LogGroup.CORE);
   public static final int EQUIP_LEVEL_TARGET_CHARACTER = -1;
   public static final int EQUIP_LEVEL_TARGET_WEAPON = -2;
   public static final int EQUIP_MATERIAL_SOURCE_INVENTORY = 0;
   public static final int EQUIP_MATERIAL_SOURCE_BACKPACK = 1;

   private static PGCharacter writeTarget(ServerPlayer player, PlayerCharactersAttachment attachment) {
      Integer uuid = EDIT_TARGET.get(player.getUUID());
      if (uuid != null) {
         PGCharacter target = attachment.getCharacterByUUID(uuid);
         if (target != null && target.getData() != null) {
            return target;
         }
      }

      return attachment.getCurrentCharacter();
   }

   @RPCPacket("setEditTargetRPCPacket")
   public static void setEditTargetRPCPacket(RPCSender sender, int characterUUID) {
      if (!sender.isServer()) {
         ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
         EDIT_TARGET.put(player.getUUID(), characterUUID);
      }
   }

   public static void sendSetEditTargetToServer(int characterUUID) {
      RPCPacketDistributor.rpcToServer("setEditTargetRPCPacket", new Object[]{characterUUID});
   }

   public static void init() {
   }

   @RPCPacket("primogemRPCPacket")
   public static void primogemRPCPacket(RPCSender sender, int amount) {
      if (sender.isServer()) {
         ClientHandler.primogemClientHandler(amount);
      } else {
         ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
         player.setData(AttachmentRegistration.PRIMOGEM_ATTACHMENT.get(), amount);
      }
   }

   public static void setPrimogemToServer(int amount) {
      RPCPacketDistributor.rpcToServer("primogemRPCPacket", new Object[]{amount});
   }

   public static void setPrimogemToPlayer(ServerPlayer player, int amount) {
      RPCPacketDistributor.rpcToPlayer(player, "primogemRPCPacket", new Object[]{amount});
   }

   @RPCPacket("adventurerInfoRPCPacket")
   public static void adventurerInfoRPCPacket(RPCSender sender, CompoundTag data) {
      if (sender.isServer()) {
         ClientHandler.adventurerInfoClientHandler(data);
      } else {
         ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
         AdventurerInfoAttachment attachment = (AdventurerInfoAttachment)player.getData(AttachmentRegistration.ADVENTURER_INFO_ATTACHMENT);
         attachment.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, player.registryAccess(), data));
      }
   }

   public static void setAdventurerInfoToServer(CompoundTag data) {
      RPCPacketDistributor.rpcToServer("adventurerInfoRPCPacket", new Object[]{data});
   }

   public static void setAdventurerInfoToPlayer(ServerPlayer player, CompoundTag data) {
      RPCPacketDistributor.rpcToPlayer(player, "adventurerInfoRPCPacket", new Object[]{data});
   }

   @RPCPacket("genshinModeRPCPacket")
   public static void genshinModeRPCPacket(RPCSender sender, boolean isGenshinMode) {
      if (sender.isServer()) {
         ClientHandler.genshinModeClientHandler(isGenshinMode);
      } else {
         ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
         // 下落攻击期间不接受模式切换（客户端那边也拦了，这里是服务端的权威那一份）
         if (PlungeState.isPlunging(player)) {
            return;
         }
         if (isGenshinMode && !hasAlivePartyCharacter(player)) {
            player.sendSystemMessage(Component.translatable("message.minegenshin.no_alive_character"));
            setGenshinModeToPlayer(player, false);
            return;
         }

         if (!isGenshinMode) {
            setGenshinModeToPlayer(player, false);
         } else {
            PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            PGCharacter current = attachment.getCurrentCharacter();
            if (current == null || current.getData().getCurrentHP() <= 0.0) {
               for (int i = 0; i < 4; i++) {
                  PGCharacter c = attachment.getPartyCharacter(i);
                  if (c != null && c.getData().getCurrentHP() > 0.0) {
                     attachment.setCurrentCharacterIndex(i);
                     break;
                  }
               }
            }

            attachment.syncToPlayer(player);
            setGenshinModeToPlayer(player, true);
         }

         player.setData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT.get(), isGenshinMode);
         PlayerStatBridge.onGenshinModeChanged(player, isGenshinMode);
         player.sendSystemMessage(Component.literal(isGenshinMode ? "已进入原神模式" : "已退出原神模式"));
      }
   }

   private static boolean hasAlivePartyCharacter(ServerPlayer player) {
      PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);

      for (int uuid : attachment.getPartyCharacterUUIDs()) {
         if (uuid != 0) {
            PGCharacter c = attachment.getCharacterByUUID(uuid);
            if (c != null && c.getData().getCurrentHP() > 0.0) {
               return true;
            }
         }
      }

      return false;
   }

   public static void setGenshinModeToPlayer(ServerPlayer player, boolean isGenshinMode) {
      RPCPacketDistributor.rpcToPlayer(player, "genshinModeRPCPacket", new Object[]{isGenshinMode});
   }

   public static void setGenshinModeToServer(boolean isGenshinMode) {
      RPCPacketDistributor.rpcToServer("genshinModeRPCPacket", new Object[]{isGenshinMode});
   }

   @RPCPacket("walkModeToggleRPCPacket")
   public static void walkModeToggleRPCPacket(RPCSender sender) {
      if (!sender.isServer()) {
         ServerPlayer player = sender.asPlayer();
         if (player == null) {
            return;
         }

         boolean walkMode = !player.getData(AttachmentRegistration.WALK_MODE_ATTACHMENT);
         player.setData(AttachmentRegistration.WALK_MODE_ATTACHMENT.get(), walkMode);
         player.sendSystemMessage(Component.translatable(
                 walkMode ? "message.minegenshin.walk_mode_on" : "message.minegenshin.walk_mode_off"));
      }
   }

   public static void setWalkModeToggleToServer() {
      RPCPacketDistributor.rpcToServer("walkModeToggleRPCPacket", new Object[0]);
   }

   @RPCPacket("playerCharactersRPCPacket")
   public static void playerCharactersRPCPacket(RPCSender sender, CompoundTag data) {
      if (sender.isServer()) {
         ClientHandler.playerCharactersClientHandler(data);
      } else {
         ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
         PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
         attachment.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, player.registryAccess(), data));
         attachment.fixCharacterTypes();
         attachment.bindAllOwners(player);
      }
   }

   public static void setPlayerCharactersToServer(CompoundTag data) {
      RPCPacketDistributor.rpcToServer("playerCharactersRPCPacket", new Object[]{data});
   }

   public static void setPlayerCharactersToPlayer(ServerPlayer player, CompoundTag data) {
      RPCPacketDistributor.rpcToPlayer(player, "playerCharactersRPCPacket", new Object[]{data});
   }

   @RPCPacket("characterDataRPCPacket")
   public static void characterDataRPCPacket(RPCSender sender, int uuid, CompoundTag data) {
      if (sender.isServer()) {
         ClientHandler.characterDataClientHandler(uuid, data);
      } else {
         ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
         PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
         PGCharacter character = attachment.getCharacterByUUID(uuid);
         if (character != null) {
            character.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, player.registryAccess(), data));
         }
      }
   }

   public static void setCharacterDataToServer(int uuid, CompoundTag data) {
      RPCPacketDistributor.rpcToServer("characterDataRPCPacket", new Object[]{uuid, data});
   }

   public static void setCharacterDataToPlayer(ServerPlayer player, int uuid, CompoundTag data) {
      RPCPacketDistributor.rpcToPlayer(player, "characterDataRPCPacket", new Object[]{uuid, data});
   }

   @RPCPacket("talentMultiplierRPCPacket")
   public static void talentMultiplierRPCPacket(RPCSender sender, String key, double value) {
      if (!sender.isServer()) {
         // 按 key 全局查表：各角色的倍率 key 自带前缀（申鹤 nab1… / 林薇云 lwy-nab1…），
         // 所以这里不需要知道是哪个角色（见 TalentConfigs#setByKeyGlobal）。
         if (!TalentConfigs.setByKeyGlobal(key, value)) {
            LOGGER.warn("[NetworkManager] 忽略未知的倍率 key: {}", key);
         } else {
            GenshinConfig.CHARACTER_SPEC.save();
         }
      }
   }

   public static void setTalentMultiplierToServer(String key, double value) {
      RPCPacketDistributor.rpcToServer("talentMultiplierRPCPacket", new Object[]{key, value});
   }

   @RPCPacket("setPartyCharacterRPCPacket")
   public static void setPartyCharacterRPCPacket(RPCSender sender, int index, int characterUUID) {
      if (sender.isServer()) {
         ClientHandler.setPartyCharacterClientHandler(index, characterUUID);
      } else {
         ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
         PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
         attachment.setPartyCharacter(index, characterUUID);
      }
   }

   public static void setPartyCharacterToServer(int index, int characterUUID) {
      RPCPacketDistributor.rpcToServer("setPartyCharacterRPCPacket", new Object[]{index, characterUUID});
   }

   public static void setPartyCharacterToPlayer(ServerPlayer player, int index, int characterUUID) {
      RPCPacketDistributor.rpcToPlayer(player, "setPartyCharacterRPCPacket", new Object[]{index, characterUUID});
   }

   @RPCPacket("characterSelectionRPCPacket")
   public static void characterSelectionRPCPacket(RPCSender sender, int index) {
      if (sender.isServer()) {
         ClientHandler.characterSelectionClientHandler(index);
      } else {
         ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
         // 下落攻击 / 自由飞行期间不接受切换角色（同上）
         if (PlungeState.isPlunging(player) || GenshinFlight.isFlying(player)) {
            return;
         }
         PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
         ActionManager.get(player).interrupt(InterruptReason.SWITCH_CHARACTER);
         attachment.setCurrentCharacterIndex(index);
      }
   }

   public static void setCharacterSelectionToPlayer(ServerPlayer player, int index) {
      RPCPacketDistributor.rpcToPlayer(player, "characterSelectionRPCPacket", new Object[]{index});
   }

   public static void setCharacterSelectionToServer(int index) {
      RPCPacketDistributor.rpcToServer("characterSelectionRPCPacket", new Object[]{index});
   }

   @RPCPacket("removePartyCharacterRPCPacket")
   public static void removePartyCharacterRPCPacket(RPCSender sender, int index) {
      if (sender.isServer()) {
         ClientHandler.removePartyCharacterClientHandler(index);
      } else {
         ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
         PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
         attachment.removePartyCharacter(index);
      }
   }

   public static void removePartyCharacterToServer(int index) {
      RPCPacketDistributor.rpcToServer("removePartyCharacterRPCPacket", new Object[]{index});
   }

   public static void removePartyCharacterToPlayer(ServerPlayer player, int index) {
      RPCPacketDistributor.rpcToPlayer(player, "removePartyCharacterRPCPacket", new Object[]{index});
   }

   @RPCPacket("addCharacterRPCPacket")
   public static void addCharacterRPCPacket(RPCSender sender, CompoundTag characterData) {
      if (sender.isServer()) {
         ClientHandler.addCharacterClientHandler(characterData);
      } else {
         ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
         PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
         PGCharacter character = new PGCharacter();
         character.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, player.registryAccess(), characterData));
         attachment.addCharacter(character, player);
      }
   }

   public static void addCharacterToServer(CompoundTag characterData) {
      RPCPacketDistributor.rpcToServer("addCharacterRPCPacket", new Object[]{characterData});
   }

   public static void addCharacterToPlayer(ServerPlayer player, CompoundTag characterData) {
      RPCPacketDistributor.rpcToPlayer(player, "addCharacterRPCPacket", new Object[]{characterData});
   }

   @RPCPacket("removeCharacterRPCPacket")
   public static void removeCharacterRPCPacket(RPCSender sender, int uuid) {
      if (sender.isServer()) {
         ClientHandler.removeCharacterClientHandler(uuid);
      } else {
         ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
         PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
         attachment.removeCharacter(uuid);
      }
   }

   public static void removeCharacterToServer(int uuid) {
      RPCPacketDistributor.rpcToServer("removeCharacterRPCPacket", new Object[]{uuid});
   }

   public static void removeCharacterToPlayer(ServerPlayer player, int uuid) {
      RPCPacketDistributor.rpcToPlayer(player, "removeCharacterRPCPacket", new Object[]{uuid});
   }

   @RPCPacket("artifactLevelUpRPCPacket")
   public static void artifactLevelUpRPCPacket(RPCSender sender, ItemStack stack, int expAmount) {
      if (!sender.isServer()) {
         ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
         if (stack.isEmpty() || !(stack.getItem() instanceof ArtifactItem art)) {
            return;
         }

         ArtifactStatsComponent var12 = (ArtifactStatsComponent)stack.getOrDefault(
            (DataComponentType)ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT
         );
         int star = art.getStar();
         PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
         PGCharacter currentChar = writeTarget(player, attachment);
         if (currentChar != null) {
            PGCharacterData charData = currentChar.getData();
            if (charData != null) {
               ArtifactInventory inv = charData.getArtifactInventory();
               int slot = ArtifactInventory.typeToSlot(art.getType());
               var12.setOnStatsChanged(() -> inv.markDirty(slot));
            }
         }

         long added = var12.addExp(expAmount, star, art.getType());
         if (added <= 0L) {
            return;
         }

         stack.set((DataComponentType)ModDataComponents.ARTIFACT_STATS.get(), var12);
         RPCPacketDistributor.rpcToPlayer(player, "artifactLevelUpRPCPacket", new Object[]{stack, 0});
      }
   }

   public static void sendArtifactLevelUpToServer(ItemStack artifact, int expAmount) {
      RPCPacketDistributor.rpcToServer("artifactLevelUpRPCPacket", new Object[]{artifact, expAmount});
   }

   public static void sendArtifactLevelUpToPlayer(ServerPlayer player, ItemStack artifact) {
      RPCPacketDistributor.rpcToPlayer(player, "artifactLevelUpRPCPacket", new Object[]{artifact, 0});
   }

   @RPCPacket("equipOrSwapArtifactRPCPacket")
   public static void equipOrSwapArtifactRPCPacket(RPCSender sender, int artifactSlotIndex, int inventorySlotIndex) {
      if (sender.isServer()) {
         ClientHandler.equipOrSwapArtifactClientHandler(artifactSlotIndex, inventorySlotIndex);
      } else {
         ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
         PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
         PGCharacter currentChar = writeTarget(player, attachment);
         if (currentChar == null || currentChar.getData() == null) {
            return;
         }

         PGCharacterData charData = currentChar.getData();
         ArtifactInventory artifactInv = charData.getArtifactInventory();
         if (artifactSlotIndex < 0 || artifactSlotIndex >= artifactInv.slotCount()) {
            return;
         }

         Backpack backpack = (Backpack)player.getData(AttachmentRegistration.BACKPACK_ATTACHMENT);
         if (artifactSlotIndex >= 5) {
            ArrayList<ItemStack> weaponList = backpack.getCategoryList(Backpack.Category.WEAPONS);
            if (inventorySlotIndex < 0 || inventorySlotIndex >= weaponList.size()) {
               return;
            }

            ItemStack newWeapon = weaponList.get(inventorySlotIndex);
            if (newWeapon.isEmpty() || !(newWeapon.getItem() instanceof WeaponItem)) {
               return;
            }

            if (!ArtifactInventory.isValidForSlot(artifactSlotIndex, newWeapon)) {
               return;
            }

            if (!isWeaponCompatibleWithCharacter(currentChar, newWeapon)) {
               return;
            }

            ItemStack oldWeapon = artifactInv.getItem(artifactSlotIndex);
            weaponList.set(inventorySlotIndex, ItemStack.EMPTY);
            artifactInv.setItem(artifactSlotIndex, newWeapon.copy());
            if (!oldWeapon.isEmpty()) {
               backpack.addItemToCategory(Backpack.Category.WEAPONS, oldWeapon.copy());
            }
         } else {
            ArrayList<ItemStack> artifactList = backpack.getCategoryList(Backpack.Category.ARTIFACTS);
            if (inventorySlotIndex < 0 || inventorySlotIndex >= artifactList.size()) {
               return;
            }

            ItemStack newArtifact = artifactList.get(inventorySlotIndex);
            if (newArtifact.isEmpty() || !(newArtifact.getItem() instanceof ArtifactItem)) {
               return;
            }

            if (!ArtifactInventory.isValidForSlot(artifactSlotIndex, newArtifact)) {
               return;
            }

            ItemStack oldArtifact = artifactInv.getItem(artifactSlotIndex);
            artifactList.set(inventorySlotIndex, ItemStack.EMPTY);
            artifactInv.setItem(artifactSlotIndex, newArtifact.copy());
            if (!oldArtifact.isEmpty()) {
               backpack.addItemToCategory(Backpack.Category.ARTIFACTS, oldArtifact.copy());
            }
         }

         currentChar.recalculateDirtyArtifactSlots();
         attachment.syncToPlayer(player);
      }
   }

   private static boolean isWeaponCompatibleWithCharacter(PGCharacter character, ItemStack weaponStack) {
      return character.canEquipWeapon(weaponStack);
   }

   public static void sendEquipOrSwapArtifactToServer(int artifactSlotIndex, int inventorySlotIndex) {
      RPCPacketDistributor.rpcToServer("equipOrSwapArtifactRPCPacket", new Object[]{artifactSlotIndex, inventorySlotIndex});
   }

   @RPCPacket("equipFromInventoryRPCPacket")
   public static void equipFromInventoryRPCPacket(RPCSender sender, int artifactSlotIndex, int playerSlotIndex) {
      if (!sender.isServer()) {
         ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
         PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
         PGCharacter currentChar = writeTarget(player, attachment);
         if (currentChar != null && currentChar.getData() != null) {
            PGCharacterData charData = currentChar.getData();
            ArtifactInventory artifactInv = charData.getArtifactInventory();
            if (artifactSlotIndex >= 0 && artifactSlotIndex < artifactInv.slotCount()) {
               Inventory inventory = player.getInventory();
               if (playerSlotIndex >= 0 && playerSlotIndex < inventory.getContainerSize()) {
                  ItemStack newItem = inventory.getItem(playerSlotIndex);
                  if (!newItem.isEmpty()) {
                     if (ArtifactInventory.isValidForSlot(artifactSlotIndex, newItem)) {
                        Backpack backpack = (Backpack)player.getData(AttachmentRegistration.BACKPACK_ATTACHMENT);
                        boolean weaponSlot = artifactSlotIndex >= 5;
                        if (!weaponSlot || isWeaponCompatibleWithCharacter(currentChar, newItem)) {
                           ItemStack oldItem = artifactInv.getItem(artifactSlotIndex);
                           ItemStack moved = newItem.copy();
                           inventory.setItem(playerSlotIndex, ItemStack.EMPTY);
                           artifactInv.setItem(artifactSlotIndex, moved);
                           if (!oldItem.isEmpty() && backpack != null) {
                              backpack.addItemToCategory(weaponSlot ? Backpack.Category.WEAPONS : Backpack.Category.ARTIFACTS, oldItem.copy());
                           }

                           currentChar.recalculateDirtyArtifactSlots();
                           attachment.syncToPlayer(player);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public static void sendEquipFromInventoryToServer(int artifactSlotIndex, int playerSlotIndex) {
      RPCPacketDistributor.rpcToServer("equipFromInventoryRPCPacket", new Object[]{artifactSlotIndex, playerSlotIndex});
   }

   @RPCPacket("unequipArtifactRPCPacket")
   public static void unequipArtifactRPCPacket(RPCSender sender, int artifactSlotIndex) {
      if (sender.isServer()) {
         ClientHandler.unequipArtifactClientHandler(artifactSlotIndex);
      } else {
         ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
         PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
         PGCharacter currentChar = writeTarget(player, attachment);
         if (currentChar == null || currentChar.getData() == null) {
            return;
         }

         PGCharacterData charData = currentChar.getData();
         ArtifactInventory artifactInv = charData.getArtifactInventory();
         if (artifactSlotIndex < 0 || artifactSlotIndex >= artifactInv.slotCount()) {
            return;
         }

         ItemStack oldArtifact = artifactInv.getItem(artifactSlotIndex);
         if (oldArtifact.isEmpty()) {
            return;
         }

         artifactInv.setItem(artifactSlotIndex, ItemStack.EMPTY);
         Backpack backpack = (Backpack)player.getData(AttachmentRegistration.BACKPACK_ATTACHMENT);
         if (artifactSlotIndex >= 5) {
            backpack.addItemToCategory(Backpack.Category.WEAPONS, oldArtifact.copy());
         } else {
            backpack.addItemToCategory(Backpack.Category.ARTIFACTS, oldArtifact.copy());
         }

         currentChar.recalculateDirtyArtifactSlots();
         attachment.syncToPlayer(player);
      }
   }

   public static void sendUnequipArtifactToServer(int artifactSlotIndex) {
      RPCPacketDistributor.rpcToServer("unequipArtifactRPCPacket", new Object[]{artifactSlotIndex});
   }

   @RPCPacket("equipLevelUpRPCPacket")
   public static void equipLevelUpRPCPacket(RPCSender sender, int target) {
      if (!sender.isServer()) {
         ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
         PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
         PGCharacter character = writeTarget(player, attachment);
         if (character != null && character.getData() != null) {
            PGCharacterData data = character.getData();
            if (canLevelUpTarget(player, data, target)) {
               int exp = consumeAdviceBook(player);
               if (exp <= 0) {
                  player.sendSystemMessage(Component.translatable("message.minegenshin.advice_book_low"));
               } else {
                  addEquipLevelExp(character, target, exp);
                  attachment.syncToPlayer(player);
               }
            }
         } else {
            player.sendSystemMessage(Component.translatable("message.minegenshin.no_character_selected"));
         }
      }
   }

   public static void sendEquipLevelUpToServer(int target) {
      RPCPacketDistributor.rpcToServer("equipLevelUpRPCPacket", new Object[]{target});
   }

   private static boolean canLevelUpTarget(ServerPlayer player, PGCharacterData data, int target) {
      ArtifactInventory inv = data.getArtifactInventory();
      if (target == -1) {
         if (data.getLevel() >= 90) {
            player.sendSystemMessage(Component.translatable("message.minegenshin.character_max_level_reached"));
            return false;
         } else {
            return true;
         }
      } else if (target == -2) {
         ItemStack weapon = data.getWeapon();
         if (!weapon.isEmpty() && weapon.getItem() instanceof WeaponItem) {
            return true;
         }

         player.sendSystemMessage(Component.translatable("message.minegenshin.no_weapon_equipped"));
         return false;
      } else if (target >= 0 && target <= 4) {
         ItemStack artifact = inv.getItem(target);
         if (!artifact.isEmpty() && artifact.getItem() instanceof ArtifactItem art) {
            ArtifactStatsComponent stats = (ArtifactStatsComponent)artifact.getOrDefault(
               (DataComponentType)ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT
            );
            if (stats.level >= stats.getMaxLevel(art.getStar())) {
               player.sendSystemMessage(Component.translatable("message.minegenshin.artifact_max_level"));
               return false;
            } else {
               return true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static void addEquipLevelExp(PGCharacter character, int target, int exp) {
      if (character != null && character.getData() != null && exp > 0) {
         PGCharacterData data = character.getData();
         ArtifactInventory inv = data.getArtifactInventory();
         long room = target == -1 ? character.characterExpRoom() : (target == -2 ? character.weaponExpRoom() : character.artifactExpRoom(target));
         int applied = (int)Math.max(0L, Math.min(exp, room));
         if (applied > 0) {
            exp = applied;
            if (target == -1) {
               character.addExp(exp);
            } else if (target == -2) {
               ItemStack weapon = data.getWeapon();
               if (weapon.getItem() instanceof WeaponItem weaponItem) {
                  WeaponStatsComponent stats = (WeaponStatsComponent)weapon.getOrDefault(
                     (DataComponentType)ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT
                  );
                  stats.setOnStatsChanged(() -> inv.markDirty(5));
                  stats.addExp(exp, weaponItem.getStar());
                  weapon.set((DataComponentType)ModDataComponents.WEAPON_STATS.get(), stats);
                  inv.markDirty(5);
                  character.recalculateWeaponSlot();
               }
            } else {
               ItemStack artifact = inv.getItem(target);
               if (artifact.getItem() instanceof ArtifactItem art) {
                  ArtifactStatsComponent stats = (ArtifactStatsComponent)artifact.getOrDefault(
                     (DataComponentType)ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT
                  );
                  stats.setOnStatsChanged(() -> inv.markDirty(target));
                  stats.addExp(exp, art.getStar(), art.getType());
                  artifact.set((DataComponentType)ModDataComponents.ARTIFACT_STATS.get(), stats);
                  inv.markDirty(target);
                  character.recalculateDirtyArtifactSlots();
               }
            }
         }
      }
   }

   @RPCPacket("equipLevelUpBatchRPCPacket")
   public static void equipLevelUpBatchRPCPacket(RPCSender sender, int target, int materialSource, int materialSlot, int count) {
      if (!sender.isServer()) {
         ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
         PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
         PGCharacter character = writeTarget(player, attachment);
         if (character != null && character.getData() != null) {
            PGCharacterData data = character.getData();
            if (canLevelUpTarget(player, data, target)) {
               boolean fromBackpack = materialSource == 1;
               Backpack backpack = (Backpack)player.getData(AttachmentRegistration.BACKPACK_ATTACHMENT);
               Inventory inventory = player.getInventory();
               ItemStack material;
               if (fromBackpack) {
                  if (backpack == null) {
                     return;
                  }

                  ArrayList<ItemStack> list = backpack.getCategoryList(Backpack.Category.DEVELOPMENT);
                  if (materialSlot < 0 || materialSlot >= list.size()) {
                     return;
                  }

                  material = list.get(materialSlot);
               } else {
                  if (materialSlot < 0 || materialSlot >= inventory.getContainerSize()) {
                     return;
                  }

                  material = inventory.getItem(materialSlot);
               }

               if (material.getItem() instanceof AdviceBookItem book) {
                  int available = material.getCount();
                  if (available > 0) {
                     int used = Math.max(1, Math.min(count, available));
                     material.shrink(used);
                     if (fromBackpack) {
                        if (material.isEmpty()) {
                           backpack.getCategoryList(Backpack.Category.DEVELOPMENT).set(materialSlot, ItemStack.EMPTY);
                        }

                        backpack.setChanged();
                     } else {
                        if (material.isEmpty()) {
                           inventory.setItem(materialSlot, ItemStack.EMPTY);
                        }

                        inventory.setChanged();
                     }

                     addEquipLevelExp(character, target, book.getExpValue() * used);
                     attachment.syncToPlayer(player);
                  }
               }
            }
         } else {
            player.sendSystemMessage(Component.translatable("message.minegenshin.no_character_selected"));
         }
      }
   }

   public static void sendEquipLevelUpBatchToServer(int target, int materialSource, int materialSlot, int count) {
      RPCPacketDistributor.rpcToServer("equipLevelUpBatchRPCPacket", new Object[]{target, materialSource, materialSlot, count});
   }

   private static int consumeAdviceBook(ServerPlayer player) {
      Inventory inventory = player.getInventory();

      for (int i = 0; i < inventory.getContainerSize(); i++) {
         ItemStack stack = inventory.getItem(i);
         if (stack.getItem() instanceof AdviceBookItem book) {
            stack.shrink(1);
            return book.getExpValue();
         }
      }

      Backpack backpack = (Backpack)player.getData(AttachmentRegistration.BACKPACK_ATTACHMENT);
      if (backpack != null) {
         for (ItemStack stack : backpack.getCategoryList(Backpack.Category.DEVELOPMENT)) {
            if (stack.getItem() instanceof AdviceBookItem book) {
               stack.shrink(1);
               backpack.setChanged();
               return book.getExpValue();
            }
         }
      }

      return 0;
   }

   @RPCPacket("upgradeConstellationRPCPacket")
   public static void upgradeConstellationRPCPacket(RPCSender sender) {
      if (!sender.isServer()) {
         ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
         if (player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            PGCharacter character = writeTarget(player, attachment);
            if (character == null) {
               player.sendSystemMessage(Component.translatable("message.minegenshin.no_character_selected"));
            } else if (!character.addConstellation()) {
               player.sendSystemMessage(Component.translatable("message.minegenshin.constellation_max"));
            } else {
               attachment.syncToPlayer(player);
               player.sendSystemMessage(Component.translatable("message.minegenshin.constellation_up_success", new Object[]{character.getConstellation()}));
            }
         }
      }
   }

   public static void sendUpgradeConstellationToServer() {
      RPCPacketDistributor.rpcToServer("upgradeConstellationRPCPacket", new Object[0]);
   }

   @RPCPacket("openBackpackRPCPacket")
   public static void openBackpackRPCPacket(RPCSender sender) {
      if (!sender.isServer()) {
         ServerPlayer serverPlayer = sender.asPlayer();
         if (serverPlayer == null) {
            return;
         }

         PlayerUIMenuType.openUI(serverPlayer, Identifier.fromNamespaceAndPath("minegenshin", "backpack"));
      }
   }

   public static void openBackpackUIToServer() {
      RPCPacketDistributor.rpcToServer("openBackpackRPCPacket", new Object[0]);
   }

   @RPCPacket("primogemWish")
   public static void primogemWish(RPCSender sender) {
      if (!sender.isServer()) {
         ServerPlayer serverPlayer = sender.asPlayer();
         if (serverPlayer == null) {
            return;
         }

         WishSystem.performWish(serverPlayer);
      }
   }

   public static void wishEventToServer() {
      RPCPacketDistributor.rpcToServer("primogemWish", new Object[0]);
   }

   @RPCPacket("openCharacterInfoRPCPacket")
   public static void openCharacterInfoRPCPacket(RPCSender sender) {
      if (!sender.isServer()) {
         ServerPlayer serverPlayer = sender.asPlayer();
         if (serverPlayer == null) {
            return;
         }

         serverPlayer.openMenu(new MenuProvider() {
            public Component getDisplayName() {
               return Component.literal("Character Info");
            }

            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
               return new CharacterInfoMenu(containerId, inventory);
            }
         });
      }
   }

   public static void openCharacterInfoScreenToServer() {
      RPCPacketDistributor.rpcToServer("openCharacterInfoRPCPacket", new Object[0]);
   }

   @RPCPacket("activateArtifactRPCPacket")
   public static void activateArtifactRPCPacket(RPCSender sender, int inventorySlotIndex) {
      if (!sender.isServer()) {
         ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
         Backpack backpack = (Backpack)player.getData(AttachmentRegistration.BACKPACK_ATTACHMENT);
         int globalSlot = getCategoryOffset(Backpack.Category.ARTIFACTS) + inventorySlotIndex;
         ItemStack stack = backpack.getItem(globalSlot);
         if (!stack.isEmpty() && stack.getItem() instanceof ArtifactItem) {
            ArtifactStatsComponent stats = (ArtifactStatsComponent)stack.getOrDefault(
               (DataComponentType)ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT
            );
            if (!stats.activated) {
               ArtifactItem.initializeArtifactStackIfNeeded(stack);
               backpack.setItem(globalSlot, stack);
               LOGGER.info("服务端激活圣遗物: 背包索引 {}", inventorySlotIndex);
            }

            RPCPacketDistributor.rpcToPlayer(player, "artifactActivatedRPCPacket", new Object[]{inventorySlotIndex, stack});
         }
      }
   }

   @RPCPacket("artifactActivatedRPCPacket")
   public static void artifactActivatedRPCPacket(RPCSender sender, int inventorySlotIndex, ItemStack stack) {
      if (sender.isServer()) {
         ClientHandler.applyActivatedArtifactClientHandler(inventorySlotIndex, stack);
      }
   }

   public static void sendActivateArtifactToServer(int inventorySlotIndex) {
      RPCPacketDistributor.rpcToServer("activateArtifactRPCPacket", new Object[]{inventorySlotIndex});
   }

   public static void sendActivateArtifactToPlayer(ServerPlayer player, int inventorySlotIndex) {
      RPCPacketDistributor.rpcToPlayer(player, "activateArtifactRPCPacket", new Object[]{inventorySlotIndex});
   }

   @RPCPacket("activateInventoryArtifactRPCPacket")
   public static void activateInventoryArtifactRPCPacket(RPCSender sender, int playerSlotIndex) {
      if (!sender.isServer()) {
         ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
         Inventory inventory = player.getInventory();
         if (playerSlotIndex >= 0 && playerSlotIndex < inventory.getContainerSize()) {
            ItemStack stack = inventory.getItem(playerSlotIndex);
            if (!stack.isEmpty() && stack.getItem() instanceof ArtifactItem) {
               ArtifactStatsComponent stats = (ArtifactStatsComponent)stack.getOrDefault(
                  (DataComponentType)ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT
               );
               if (!stats.activated) {
                  ArtifactItem.initializeArtifactStackIfNeeded(stack);
                  inventory.setItem(playerSlotIndex, stack);
                  LOGGER.info("服务端激活圣遗物（玩家物品栏）: 槽位 {}", playerSlotIndex);
               }

               RPCPacketDistributor.rpcToPlayer(player, "artifactActivatedInventoryRPCPacket", new Object[]{playerSlotIndex, stack});
            }
         }
      }
   }

   @RPCPacket("artifactActivatedInventoryRPCPacket")
   public static void artifactActivatedInventoryRPCPacket(RPCSender sender, int playerSlotIndex, ItemStack stack) {
      if (sender.isServer()) {
         ClientHandler.applyActivatedInventoryArtifact(playerSlotIndex, stack);
      }
   }

   public static void sendActivateInventoryArtifactToServer(int playerSlotIndex) {
      RPCPacketDistributor.rpcToServer("activateInventoryArtifactRPCPacket", new Object[]{playerSlotIndex});
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

   @RPCPacket("backpackTakeOutFromSlotRPCPacket")
   public static void backpackTakeOutFromSlotRPCPacket(RPCSender sender, int slotIndex) {
      if (!sender.isServer()) {
         ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
         Backpack backpack = (Backpack)player.getData(AttachmentRegistration.BACKPACK_ATTACHMENT);
         ItemStack removed = backpack.getItem(slotIndex);
         if (removed.isEmpty()) {
            return;
         }

         backpack.setItem(slotIndex, ItemStack.EMPTY);
         giveItemToPlayer(player, removed);
      }
   }

   public static void sendBackpackTakeOutFromSlotToServer(int slotIndex) {
      RPCPacketDistributor.rpcToServer("backpackTakeOutFromSlotRPCPacket", new Object[]{slotIndex});
   }

   public static void giveItemToPlayer(ServerPlayer player, ItemStack stack) {
      int remaining = stack.getCount();
      Inventory inventory = player.getInventory();

      for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
         ItemStack existing = inventory.getItem(i);
         if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
            int canAdd = Math.min(remaining, existing.getMaxStackSize() - existing.getCount());
            existing.grow(canAdd);
            remaining -= canAdd;
         }
      }

      if (remaining > 0) {
         for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            ItemStack existing = inventory.getItem(i);
            if (existing.isEmpty()) {
               int toAdd = Math.min(remaining, stack.getMaxStackSize());
               inventory.setItem(i, stack.copyWithCount(toAdd));
               remaining -= toAdd;
            }
         }
      }

      if (remaining > 0) {
         ItemStack dropStack = stack.copyWithCount(remaining);
         ItemEntity itemEntity = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), dropStack);
         player.level().addFreshEntity(itemEntity);
      }
   }

   @RPCPacket("ascendAdventureRankRPCPacket")
   public static void ascendAdventureRankRPCPacket(RPCSender sender) {
      if (!sender.isServer()) {
         ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
         if (player.experienceLevel < 30) {
            player.sendSystemMessage(Component.translatable("message.minegenshin.adventure_rank_exp_low"));
            return;
         }

         AdventurerInfoAttachment advInfo = (AdventurerInfoAttachment)player.getData(AttachmentRegistration.ADVENTURER_INFO_ATTACHMENT);
         if (!advInfo.canBreakthroughWorldLevel()) {
            player.sendSystemMessage(Component.translatable("message.minegenshin.adventure_rank_breakthrough_not_met"));
            return;
         }

         player.giveExperienceLevels(-30);
         advInfo.breakthroughWorldLevel();
         advInfo.syncToPlayer(player);
         player.sendSystemMessage(Component.translatable("message.minegenshin.adventure_rank_breakthrough_success", new Object[]{advInfo.getWorldLevel()}));
      }
   }

   public static void sendAscendAdventureRankToServer() {
      RPCPacketDistributor.rpcToServer("ascendAdventureRankRPCPacket", new Object[0]);
   }

   @RPCPacket("ascendCharacterRPCPacket")
   public static void ascendCharacterRPCPacket(RPCSender sender) {
      if (!sender.isServer()) {
         ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
         PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
         PGCharacter character = writeTarget(player, attachment);
         if (character == null || character.getData() == null) {
            player.sendSystemMessage(Component.translatable("message.minegenshin.no_character_selected"));
            return;
         }

         int maxLevelForPhase = character.getData().getAscensionPhase() == 0 ? 20 : Math.min((character.getData().getAscensionPhase() + 3) * 10, 90);
         if (character.getData().getLevel() < maxLevelForPhase) {
            player.sendSystemMessage(Component.translatable("message.minegenshin.character_not_max_level"));
            return;
         }

         if (character.getData().getLevel() >= 90) {
            player.sendSystemMessage(Component.translatable("message.minegenshin.character_max_level_reached"));
            return;
         }

         character.ascend();
         attachment.syncToPlayer(player);
         player.sendSystemMessage(
            Component.translatable("message.minegenshin.character_breakthrough_success", new Object[]{character.getData().getAscensionPhase()})
         );
      }
   }

   public static void sendAscendCharacterToServer() {
      RPCPacketDistributor.rpcToServer("ascendCharacterRPCPacket", new Object[0]);
   }

   @RPCPacket("ascendWeaponRPCPacket")
   public static void ascendWeaponRPCPacket(RPCSender sender) {
      if (!sender.isServer()) {
         ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
         int primogem = (Integer)player.getData(AttachmentRegistration.PRIMOGEM_ATTACHMENT);
         if (primogem < 1600) {
            player.sendSystemMessage(Component.translatable("message.minegenshin.weapon_primogem_low"));
            return;
         }

         if (player.experienceLevel < 10) {
            player.sendSystemMessage(Component.translatable("message.minegenshin.weapon_exp_low"));
            return;
         }

         PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
         PGCharacter character = writeTarget(player, attachment);
         if (character == null) {
            player.sendSystemMessage(Component.translatable("message.minegenshin.no_character_selected"));
            return;
         }

         ItemStack weaponStack = character.getData().getWeapon();
         if (weaponStack.isEmpty() || !(weaponStack.getItem() instanceof WeaponItem weapon)) {
            player.sendSystemMessage(Component.translatable("message.minegenshin.no_weapon_equipped"));
            return;
         }

         if (!weapon.canAscend(weaponStack)) {
            player.sendSystemMessage(Component.translatable("message.minegenshin.weapon_not_meet_requirements"));
            return;
         }

         if (!weapon.ascend(weaponStack)) {
            player.sendSystemMessage(Component.translatable("message.minegenshin.weapon_breakthrough_failed"));
            return;
         }

         character.getData().getArtifactInventory().markDirty(3);
         character.recalculateWeaponSlot();
         player.setData(AttachmentRegistration.PRIMOGEM_ATTACHMENT, primogem - 1600);
         setPrimogemToPlayer(player, primogem - 1600);
         player.giveExperienceLevels(-10);
         attachment.syncToPlayer(player);
         player.sendSystemMessage(Component.translatable("message.minegenshin.weapon_breakthrough_success"));
      }
   }

   public static void sendAscendWeaponToServer() {
      RPCPacketDistributor.rpcToServer("ascendWeaponRPCPacket", new Object[0]);
   }

   @RPCPacket("upgradeNormalAttackRPCPacket")
   public static void upgradeNormalAttackRPCPacket(RPCSender sender) {
      if (!sender.isServer()) {
         ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
         PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
         PGCharacter character = writeTarget(player, attachment);
         if (character == null || character.getData() == null) {
            player.sendSystemMessage(Component.translatable("message.minegenshin.no_character_selected"));
            return;
         }

         if (!character.getData().canUpgradeNormalAttack()) {
            player.sendSystemMessage(Component.translatable("message.minegenshin.normal_attack_max_level"));
            return;
         }

         int manual = character.getData().getManualNormalAttack();
         int costPrimogem = TalentUpgradeCost.primogem(manual);
         int costExp = TalentUpgradeCost.experienceLevels(manual);
         int primogem = (Integer)player.getData(AttachmentRegistration.PRIMOGEM_ATTACHMENT);
         if (primogem < costPrimogem) {
            player.sendSystemMessage(Component.translatable("message.minegenshin.normal_attack_primogem_low"));
            return;
         }

         if (player.experienceLevel < costExp) {
            player.sendSystemMessage(Component.translatable("message.minegenshin.normal_attack_exp_low"));
            return;
         }

         character.upgradeNormalAttack();
         player.setData(AttachmentRegistration.PRIMOGEM_ATTACHMENT, primogem - costPrimogem);
         setPrimogemToPlayer(player, primogem - costPrimogem);
         player.giveExperienceLevels(-costExp);
         attachment.syncToPlayer(player);
         player.sendSystemMessage(
            Component.translatable("message.minegenshin.normal_attack_upgrade_success", new Object[]{character.getData().getNormalAttackLevel()})
         );
      }
   }

   public static void sendUpgradeNormalAttackToServer() {
      RPCPacketDistributor.rpcToServer("upgradeNormalAttackRPCPacket", new Object[0]);
   }

   @RPCPacket("upgradeElementalSkillRPCPacket")
   public static void upgradeElementalSkillRPCPacket(RPCSender sender) {
      if (!sender.isServer()) {
         ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
         PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
         PGCharacter character = writeTarget(player, attachment);
         if (character == null || character.getData() == null) {
            player.sendSystemMessage(Component.translatable("message.minegenshin.no_character_selected"));
            return;
         }

         if (!character.getData().canUpgradeElementalSkill()) {
            player.sendSystemMessage(Component.translatable("message.minegenshin.elemental_skill_max_level"));
            return;
         }

         int manual = character.getData().getManualElementalSkill();
         int costPrimogem = TalentUpgradeCost.primogem(manual);
         int costExp = TalentUpgradeCost.experienceLevels(manual);
         int primogem = (Integer)player.getData(AttachmentRegistration.PRIMOGEM_ATTACHMENT);
         if (primogem < costPrimogem) {
            player.sendSystemMessage(Component.translatable("message.minegenshin.elemental_skill_primogem_low"));
            return;
         }

         if (player.experienceLevel < costExp) {
            player.sendSystemMessage(Component.translatable("message.minegenshin.elemental_skill_exp_low"));
            return;
         }

         character.upgradeElementalSkill();
         player.setData(AttachmentRegistration.PRIMOGEM_ATTACHMENT, primogem - costPrimogem);
         setPrimogemToPlayer(player, primogem - costPrimogem);
         player.giveExperienceLevels(-costExp);
         attachment.syncToPlayer(player);
         player.sendSystemMessage(
            Component.translatable("message.minegenshin.elemental_skill_upgrade_success", new Object[]{character.getData().getElementalSkillLevel()})
         );
      }
   }

   public static void sendUpgradeElementalSkillToServer() {
      RPCPacketDistributor.rpcToServer("upgradeElementalSkillRPCPacket", new Object[0]);
   }

   @RPCPacket("upgradeElementalBurstRPCPacket")
   public static void upgradeElementalBurstRPCPacket(RPCSender sender) {
      if (!sender.isServer()) {
         ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
         PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
         PGCharacter character = writeTarget(player, attachment);
         if (character == null || character.getData() == null) {
            player.sendSystemMessage(Component.translatable("message.minegenshin.no_character_selected"));
            return;
         }

         if (!character.getData().canUpgradeElementalBurst()) {
            player.sendSystemMessage(Component.translatable("message.minegenshin.elemental_burst_max_level"));
            return;
         }

         int manual = character.getData().getManualElementalBurst();
         int costPrimogem = TalentUpgradeCost.primogem(manual);
         int costExp = TalentUpgradeCost.experienceLevels(manual);
         int primogem = (Integer)player.getData(AttachmentRegistration.PRIMOGEM_ATTACHMENT);
         if (primogem < costPrimogem) {
            player.sendSystemMessage(Component.translatable("message.minegenshin.elemental_burst_primogem_low"));
            return;
         }

         if (player.experienceLevel < costExp) {
            player.sendSystemMessage(Component.translatable("message.minegenshin.elemental_burst_exp_low"));
            return;
         }

         character.upgradeElementalBurst();
         player.setData(AttachmentRegistration.PRIMOGEM_ATTACHMENT, primogem - costPrimogem);
         setPrimogemToPlayer(player, primogem - costPrimogem);
         player.giveExperienceLevels(-costExp);
         attachment.syncToPlayer(player);
         player.sendSystemMessage(
            Component.translatable("message.minegenshin.elemental_burst_upgrade_success", new Object[]{character.getData().getElementalBurstLevel()})
         );
      }
   }

   public static void sendUpgradeElementalBurstToServer() {
      RPCPacketDistributor.rpcToServer("upgradeElementalBurstRPCPacket", new Object[0]);
   }

   @RPCPacket("downgradeWorldLevelRPCPacket")
   public static void downgradeWorldLevelRPCPacket(RPCSender sender) {
      if (!sender.isServer()) {
         ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
         AdventurerInfoAttachment advInfo = (AdventurerInfoAttachment)player.getData(AttachmentRegistration.ADVENTURER_INFO_ATTACHMENT);
         if (!advInfo.canDowngradeWorldLevel()) {
            player.sendSystemMessage(Component.translatable("message.minegenshin.world_level_downgrade_not_met"));
            return;
         }

         advInfo.downgradeWorldLevel();
         advInfo.syncToPlayer(player);
         player.sendSystemMessage(Component.translatable("message.minegenshin.world_level_downgrade_success", new Object[]{advInfo.getWorldLevel()}));
      }
   }

   public static void sendDowngradeWorldLevelToServer() {
      RPCPacketDistributor.rpcToServer("downgradeWorldLevelRPCPacket", new Object[0]);
   }

   @RPCPacket("restoreWorldLevelRPCPacket")
   public static void restoreWorldLevelRPCPacket(RPCSender sender) {
      if (!sender.isServer()) {
         ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
         AdventurerInfoAttachment advInfo = (AdventurerInfoAttachment)player.getData(AttachmentRegistration.ADVENTURER_INFO_ATTACHMENT);
         if (!advInfo.canRestoreWorldLevel()) {
            player.sendSystemMessage(Component.translatable("message.minegenshin.world_level_no_restore"));
            return;
         }

         advInfo.restoreWorldLevel();
         advInfo.syncToPlayer(player);
         player.sendSystemMessage(Component.translatable("message.minegenshin.world_level_restore_success", new Object[]{advInfo.getWorldLevel()}));
      }
   }

   public static void sendRestoreWorldLevelToServer() {
      RPCPacketDistributor.rpcToServer("restoreWorldLevelRPCPacket", new Object[0]);
   }

   @RPCPacket("invasionStatusRPCPacket")
   public static void invasionStatusRPCPacket(RPCSender sender, boolean invaded) {
      if (sender.isServer()) {
         ClientHandler.invasionStatusClientHandler(invaded);
      }
   }

   public static void setInvasionStatusToPlayer(ServerPlayer player, boolean invaded) {
      RPCPacketDistributor.rpcToPlayer(player, "invasionStatusRPCPacket", new Object[]{invaded});
   }

   @RPCPacket("minegenshin:entity_sync")
   public static void entitySyncRPCPacket(RPCSender sender, int entityId, CompoundTag payload) {
      if (sender.isServer()) {
         ClientHandler.entitySyncClientHandler(entityId, payload);
      }
   }

   public static void sendEntitySyncToPlayer(ServerPlayer player, int entityId, CompoundTag payload) {
      RPCPacketDistributor.rpcToPlayer(player, "minegenshin:entity_sync", new Object[]{entityId, payload});
   }

   @RPCPacket("minegenshin:character_sync")
   public static void characterSyncRPCPacket(RPCSender sender, int characterUUID, CompoundTag payload) {
      if (sender.isServer()) {
         ClientHandler.characterSyncClientHandler(characterUUID, payload);
      }
   }

   public static void sendCharacterSyncToPlayer(ServerPlayer player, int characterUUID, CompoundTag payload) {
      RPCPacketDistributor.rpcToPlayer(player, "minegenshin:character_sync", new Object[]{characterUUID, payload});
   }

   @RPCPacket("minegenshin:animation_state")
   public static void animationStateRPCPacket(RPCSender sender, String stateName, int totalTicks) {
      if (!sender.isServer()) {
         ServerPlayer player = sender.asPlayer();
         if (player == null) {
            return;
         }

         ServerAnimationTicker.apply(player, stateName, totalTicks);
      }
   }

   public static void sendAnimationStateToServer(String stateName, int totalTicks) {
      RPCPacketDistributor.rpcToServer("minegenshin:animation_state", new Object[]{stateName, totalTicks});
   }

   @RPCPacket("minegenshin:body_yaw")
   public static void bodyYawRPCPacket(RPCSender sender, float yaw) {
      if (!sender.isServer()) {
         ServerPlayer player = sender.asPlayer();
         if (player == null) {
            return;
         }

         ServerAnimationTicker.applyBodyYaw(player, yaw);
      }
   }

   public static void sendBodyYawToServer(float yaw) {
      if (Float.isFinite(yaw)) {
         RPCPacketDistributor.rpcToServer("minegenshin:body_yaw", new Object[]{yaw});
      }
   }
}