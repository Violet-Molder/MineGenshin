package com.linweiyun.genshin.core.network;

import com.linweiyun.genshin.content.items.artifact.ArtifactItem;
import com.linweiyun.genshin.content.items.artifact.ArtifactLevelData;
import com.linweiyun.genshin.content.items.weapon.WeaponItem;
import com.linweiyun.genshin.content.items.weapon.WeaponLevelData;
import com.linweiyun.genshin.content.items.component.ArtifactStatsComponent;
import com.linweiyun.genshin.content.items.component.WeaponStatsComponent;
import com.linweiyun.genshin.content.items.development.AdviceBookItem;
import com.linweiyun.genshin.config.character.CharacterXpConfig;
import com.linweiyun.genshin.core.attachment.*;
import com.linweiyun.genshin.content.items.artifact.inventory.ArtifactInventory;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.PGCharacterData;
import com.linweiyun.genshin.core.system.combat.action.ActionManager;
import com.linweiyun.genshin.core.system.combat.action.InterruptReason;
import com.linweiyun.genshin.core.system.combat.animation.server.ServerAnimationTicker;
import com.linweiyun.genshin.core.system.registry.register.ModDataComponents;
import com.linweiyun.genshin.core.menu.CharacterInfoMenu;
import com.linweiyun.genshin.core.system.wish.WishSystem;
import com.lowdragmc.lowdraglib2.gui.factory.PlayerUIMenuType;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacket;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;
import com.lowdragmc.lowdraglib2.syncdata.rpc.RPCSender;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Random;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkManager {

  /**
   * 「这一次写操作打在哪个角色身上」—— 玩家 UUID → 角色 UUID。
   *
   * <p>历史上所有换装 / 升级 / 命座 / 天赋接口都是对 {@code attachment.getCurrentCharacter()}
   * （也就是<b>场上正在操控的那位</b>）生效的，于是页面里只要换到别的角色看，所有按钮就得禁用，
   * 顶栏还要挂一条「仅查看」。用户口径是「只要拥有这个角色就能改」，所以这里加一层编辑目标：
   * 客户端打开某位角色的面板时先报一次 UUID，服务端把随后的写操作落到那位身上。
   *
   * <p>目标只在<b>确实拥有</b>该角色时才认（见 {@link #writeTarget}），随机 UUID 打不进来。
   */
  private static final Map<UUID, Integer> EDIT_TARGET = new ConcurrentHashMap<>();

  /** 解析「这一条写操作该改谁」：优先用客户端报上来的编辑目标，没报过就退回场上那位。 */
  private static PGCharacter writeTarget(ServerPlayer player, PlayerCharactersAttachment attachment) {
    Integer uuid = EDIT_TARGET.get(player.getUUID());
    if (uuid != null) {
      PGCharacter target = attachment.getCharacterByUUID(uuid);
      // getCharacterByUUID 只认「已拥有」列表里的角色，随机 UUID 打不进来
      if (target != null && target.getData() != null) {
        return target;
      }
    }
    return attachment.getCurrentCharacter();
  }

  /** 客户端切换「正在查看并编辑的角色」时调一次（见 CharacterEquipUI#viewCharacter）。 */
  @RPCPacket("setEditTargetRPCPacket")
  public static void setEditTargetRPCPacket(RPCSender sender, int characterUUID) {
    if (!sender.isServer()) {
      ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
      EDIT_TARGET.put(player.getUUID(), characterUUID);
    }
  }

  public static void sendSetEditTargetToServer(int characterUUID) {
    RPCPacketDistributor.rpcToServer("setEditTargetRPCPacket", characterUUID);
  }
  private static final Random RANDOM = new Random();
  private static final Logger LOGGER = ModLog.getLogger(LogGroup.CORE);

  public static void init() {}

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
    RPCPacketDistributor.rpcToServer("primogemRPCPacket", amount);
  }

  public static void setPrimogemToPlayer(ServerPlayer player, int amount) {
    RPCPacketDistributor.rpcToPlayer(player, "primogemRPCPacket", amount);
  }

  @RPCPacket("adventurerInfoRPCPacket")
  public static void adventurerInfoRPCPacket(RPCSender sender, CompoundTag data) {
    if (sender.isServer()) {
      ClientHandler.adventurerInfoClientHandler(data);
    } else {
      ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
      AdventurerInfoAttachment attachment = player.getData(AttachmentRegistration.ADVENTURER_INFO_ATTACHMENT);
      attachment.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, player.registryAccess(), data));
    }
  }

  public static void setAdventurerInfoToServer(CompoundTag data) {
    RPCPacketDistributor.rpcToServer("adventurerInfoRPCPacket", data);
  }

  public static void setAdventurerInfoToPlayer(ServerPlayer player, CompoundTag data) {
    RPCPacketDistributor.rpcToPlayer(player, "adventurerInfoRPCPacket", data);
  }

  @RPCPacket("genshinModeRPCPacket")
  public static void genshinModeRPCPacket(RPCSender sender, boolean isGenshinMode) {
    if (sender.isServer()) {
      ClientHandler.genshinModeClientHandler(isGenshinMode);
    } else {
      ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
      if (isGenshinMode && !hasAlivePartyCharacter(player)) {
        player.sendSystemMessage(Component.translatable("message.minegenshin.no_alive_character"));
        setGenshinModeToPlayer(player, false);
        return;
      }
      if (isGenshinMode) {
        PlayerCharactersAttachment attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        PGCharacter current = attachment.getCurrentCharacter();
        if (current == null || current.getData().getCurrentHP() <= 0) {
          for (int i = 0; i < 4; i++) {
            PGCharacter c = attachment.getPartyCharacter(i);
            if (c != null && c.getData().getCurrentHP() > 0) {
              attachment.setCurrentCharacterIndex(i);
              break;
            }
          }
        }
        attachment.syncToPlayer(player);
        setGenshinModeToPlayer(player, true);
      } else {
        setGenshinModeToPlayer(player, false);
      }
      player.setData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT.get(), isGenshinMode);
      // 模式真变了：建立 / 解除「玩家血量 ↔ 角色血量」的折算，并维护玩家身上的角色属性
      com.linweiyun.genshin.core.system.compat.PlayerStatBridge
              .onGenshinModeChanged(player, isGenshinMode);
      player.sendSystemMessage(Component.literal(isGenshinMode ? "已进入原神模式" : "已退出原神模式"));
    }
  }

  private static boolean hasAlivePartyCharacter(ServerPlayer player) {
    PlayerCharactersAttachment attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
    for (int uuid : attachment.getPartyCharacterUUIDs()) {
      if (uuid == 0) continue;
      PGCharacter c = attachment.getCharacterByUUID(uuid);
      if (c != null && c.getData().getCurrentHP() > 0) return true;
    }
    return false;
  }

  public static void setGenshinModeToPlayer(ServerPlayer player, boolean isGenshinMode) {
    RPCPacketDistributor.rpcToPlayer(player, "genshinModeRPCPacket", isGenshinMode);
  }

  public static void setGenshinModeToServer(boolean isGenshinMode) {
    RPCPacketDistributor.rpcToServer("genshinModeRPCPacket", isGenshinMode);
  }

  @RPCPacket("playerCharactersRPCPacket")
  public static void playerCharactersRPCPacket(RPCSender sender, CompoundTag data) {
    if (sender.isServer()) {
      ClientHandler.playerCharactersClientHandler(data);
    } else {
      ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
      PlayerCharactersAttachment attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
      attachment.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, player.registryAccess(), data));
      attachment.fixCharacterTypes();
      attachment.bindAllOwners(player);
    }
  }
  public static void setPlayerCharactersToServer(CompoundTag data) {
    RPCPacketDistributor.rpcToServer("playerCharactersRPCPacket", data);
  }
  public static void setPlayerCharactersToPlayer(ServerPlayer player, CompoundTag data) {
    RPCPacketDistributor.rpcToPlayer(player, "playerCharactersRPCPacket", data);
  }

  @RPCPacket("characterDataRPCPacket")
  public static void characterDataRPCPacket(RPCSender sender, int uuid, CompoundTag data) {
    if (sender.isServer()) {
      ClientHandler.characterDataClientHandler(uuid, data);
    } else {
      ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
      PlayerCharactersAttachment attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
      PGCharacter character = attachment.getCharacterByUUID(uuid);
      if (character != null) {
        character.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, player.registryAccess(), data));
      }
    }
  }

  public static void setCharacterDataToServer(int uuid, CompoundTag data) {
    RPCPacketDistributor.rpcToServer("characterDataRPCPacket", uuid, data);
  }

  public static void setCharacterDataToPlayer(ServerPlayer player, int uuid, CompoundTag data) {
    RPCPacketDistributor.rpcToPlayer(player, "characterDataRPCPacket", uuid, data);
  }

  /**
   * 角色配置页改倍率 → 写服务端那份配置。
   *
   * <h2>为什么必须发这一趟</h2>
   * 倍率表（{@code ShenheTalentConfig} / {@code character.toml}）注册的是
   * {@code ModConfig.Type.COMMON}：<b>两端各持有一份、NeoForge 不会自动同步</b>。
   * 伤害在服务端算，所以只在客户端 {@code set()} 是「改了个寂寞」——
   * 页面必须把这趟包发上来，让服务端的那份也写掉。
   *
   * <p>方向按本项目的 RPC 约定写：{@code sender.isServer()} 为 true 表示
   * 包到了<b>客户端</b>（服务端发的），false 表示到了<b>服务端</b>（客户端发的）。
   */
  @RPCPacket("talentMultiplierRPCPacket")
  public static void talentMultiplierRPCPacket(RPCSender sender, String key, double value) {
    if (sender.isServer()) {
      // 客户端收到：不用做任何事（客户端那份在本地 set 时已经写过）
      return;
    }
    if (!com.linweiyun.genshin.config.character.ShenheTalentConfig.setByKey(key, value)) {
      LOGGER.warn("[NetworkManager] 忽略未知的倍率 key: {}", key);
      return;
    }
    com.linweiyun.genshin.config.GenshinConfig.CHARACTER_SPEC.save();
  }

  public static void setTalentMultiplierToServer(String key, double value) {
    RPCPacketDistributor.rpcToServer("talentMultiplierRPCPacket", key, value);
  }

  @RPCPacket("setPartyCharacterRPCPacket")
  public static void setPartyCharacterRPCPacket(RPCSender sender, int index, int characterUUID) {
    if (sender.isServer()) {
      ClientHandler.setPartyCharacterClientHandler(index, characterUUID);
    } else {
      ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
      PlayerCharactersAttachment attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
      attachment.setPartyCharacter(index, characterUUID);
    }
  }

  public static void setPartyCharacterToServer(int index, int characterUUID) {
    RPCPacketDistributor.rpcToServer("setPartyCharacterRPCPacket", index, characterUUID);
  }

  public static void setPartyCharacterToPlayer(ServerPlayer player, int index, int characterUUID) {
    RPCPacketDistributor.rpcToPlayer(player, "setPartyCharacterRPCPacket", index, characterUUID);
  }

  @RPCPacket("characterSelectionRPCPacket")
  public static void characterSelectionRPCPacket(RPCSender sender, int index) {
    if (sender.isServer()) {
      ClientHandler.characterSelectionClientHandler(index);
    } else {
      ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
      PlayerCharactersAttachment attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);

      // ⭐ 切换角色：服务端无条件打断当前动作
      // 防止"服务端还没同步新角色、普攻 RPC 先到、request() 被旧角色动作拒绝"的情况
      ActionManager.get(player).interrupt(InterruptReason.SWITCH_CHARACTER);

      attachment.setCurrentCharacterIndex(index);
    }
  }

  public static void setCharacterSelectionToPlayer(ServerPlayer player, int index) {
    RPCPacketDistributor.rpcToPlayer(player, "characterSelectionRPCPacket", index);
  }

  public static void setCharacterSelectionToServer(int index) {
    RPCPacketDistributor.rpcToServer("characterSelectionRPCPacket", index);
  }

  @RPCPacket("removePartyCharacterRPCPacket")
  public static void removePartyCharacterRPCPacket(RPCSender sender, int index) {
    if (sender.isServer()) {
      ClientHandler.removePartyCharacterClientHandler(index);
    } else {
      ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
      PlayerCharactersAttachment attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
      attachment.removePartyCharacter(index);
    }
  }

  public static void removePartyCharacterToServer(int index) {
    RPCPacketDistributor.rpcToServer("removePartyCharacterRPCPacket", index);
  }

  public static void removePartyCharacterToPlayer(ServerPlayer player, int index) {
    RPCPacketDistributor.rpcToPlayer(player, "removePartyCharacterRPCPacket", index);
  }

  @RPCPacket("addCharacterRPCPacket")
  public static void addCharacterRPCPacket(RPCSender sender, CompoundTag characterData) {
    if (sender.isServer()) {
      ClientHandler.addCharacterClientHandler(characterData);
    } else {
      ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
      PlayerCharactersAttachment attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
      PGCharacter character = new PGCharacter();
      character.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, player.registryAccess(), characterData));
      attachment.addCharacter(character, player);
    }
  }

  public static void addCharacterToServer(CompoundTag characterData) {
    RPCPacketDistributor.rpcToServer("addCharacterRPCPacket", characterData);
  }

  public static void addCharacterToPlayer(ServerPlayer player, CompoundTag characterData) {
    RPCPacketDistributor.rpcToPlayer(player, "addCharacterRPCPacket", characterData);
  }

  @RPCPacket("removeCharacterRPCPacket")
  public static void removeCharacterRPCPacket(RPCSender sender, int uuid) {
    if (sender.isServer()) {
      ClientHandler.removeCharacterClientHandler(uuid);
    } else {
      ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
      PlayerCharactersAttachment attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
      attachment.removeCharacter(uuid);
    }
  }

  public static void removeCharacterToServer(int uuid) {
    RPCPacketDistributor.rpcToServer("removeCharacterRPCPacket", uuid);
  }

  public static void removeCharacterToPlayer(ServerPlayer player, int uuid) {
    RPCPacketDistributor.rpcToPlayer(player, "removeCharacterRPCPacket", uuid);
  }

  // ===== 注意：characterActiveSkillRPCPacket / characterActiveBurstRPCPacket /
  //       characterNormalAttackRPCPacket / characterChargedAttackRPCPacket
  //       已迁移至 ActionServer。 =====

  @RPCPacket("artifactLevelUpRPCPacket")
  public static void artifactLevelUpRPCPacket(RPCSender sender, ItemStack stack, int expAmount) {
    if (sender.isServer()) {
    } else {
      ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
      if (stack.isEmpty() || !(stack.getItem() instanceof ArtifactItem art)) return;
      ArtifactStatsComponent stats = stack.getOrDefault(ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT);
      int star = art.getStar();
      PlayerCharactersAttachment attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
      PGCharacter currentChar = writeTarget(player, attachment);
      if (currentChar != null) {
        PGCharacterData charData = currentChar.getData();
        if (charData != null) {
          ArtifactInventory inv = charData.getArtifactInventory();
          int slot = ArtifactInventory.typeToSlot(art.getType());
          stats.setOnStatsChanged(() -> inv.markDirty(slot));
        }
      }
      long added = stats.addExp(expAmount, star, art.getType());
      if (added <= 0) return;
      stack.set(ModDataComponents.ARTIFACT_STATS.get(), stats);
      RPCPacketDistributor.rpcToPlayer(player, "artifactLevelUpRPCPacket", stack, 0);
    }
  }

  public static void sendArtifactLevelUpToServer(ItemStack artifact, int expAmount) {
    RPCPacketDistributor.rpcToServer("artifactLevelUpRPCPacket", artifact, expAmount);
  }

  public static void sendArtifactLevelUpToPlayer(ServerPlayer player, ItemStack artifact) {
    RPCPacketDistributor.rpcToPlayer(player, "artifactLevelUpRPCPacket", artifact, 0);
  }

  @RPCPacket("equipOrSwapArtifactRPCPacket")
  public static void equipOrSwapArtifactRPCPacket(RPCSender sender, int artifactSlotIndex, int inventorySlotIndex) {
    if (sender.isServer()) {
      ClientHandler.equipOrSwapArtifactClientHandler(artifactSlotIndex, inventorySlotIndex);
    } else {
      ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
      PlayerCharactersAttachment attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
      PGCharacter currentChar = writeTarget(player, attachment);
      if (currentChar == null || currentChar.getData() == null) return;
      PGCharacterData charData = currentChar.getData();
      ArtifactInventory artifactInv = charData.getArtifactInventory();

      if (artifactSlotIndex < 0 || artifactSlotIndex >= artifactInv.slotCount()) return;

      Backpack backpack = player.getData(AttachmentRegistration.BACKPACK_ATTACHMENT);

      // 5~10 都是武器槽（全武器类角色一个武器种类一格）
      if (artifactSlotIndex >= ArtifactInventory.SLOT_WEAPON) {
        var weaponList = backpack.getCategoryList(Backpack.Category.WEAPONS);
        if (inventorySlotIndex < 0 || inventorySlotIndex >= weaponList.size()) return;
        ItemStack newWeapon = weaponList.get(inventorySlotIndex);
        if (newWeapon.isEmpty() || !(newWeapon.getItem() instanceof WeaponItem)) return;
        if (!ArtifactInventory.isValidForSlot(artifactSlotIndex, newWeapon)) return;
        if (!isWeaponCompatibleWithCharacter(currentChar, newWeapon)) return;

        ItemStack oldWeapon = artifactInv.getItem(artifactSlotIndex);
        weaponList.set(inventorySlotIndex, ItemStack.EMPTY);
        artifactInv.setItem(artifactSlotIndex, newWeapon.copy());
        if (!oldWeapon.isEmpty()) {
          backpack.addItemToCategory(Backpack.Category.WEAPONS, oldWeapon.copy());
        }
      } else {
        var artifactList = backpack.getCategoryList(Backpack.Category.ARTIFACTS);
        if (inventorySlotIndex < 0 || inventorySlotIndex >= artifactList.size()) return;
        ItemStack newArtifact = artifactList.get(inventorySlotIndex);
        if (newArtifact.isEmpty() || !(newArtifact.getItem() instanceof ArtifactItem)) return;
        if (!ArtifactInventory.isValidForSlot(artifactSlotIndex, newArtifact)) return;

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

  /** 这把武器能不能装到现在这一格上：单武器角色看限定类，全武器类角色看当前选中的种类。 */
  private static boolean isWeaponCompatibleWithCharacter(PGCharacter character, ItemStack weaponStack) {
    return character.canEquipWeapon(weaponStack);
  }

  public static void sendEquipOrSwapArtifactToServer(int artifactSlotIndex, int inventorySlotIndex) {
    RPCPacketDistributor.rpcToServer("equipOrSwapArtifactRPCPacket", artifactSlotIndex, inventorySlotIndex);
  }

  /**
   * 把<b>玩家主物品栏</b>（快捷栏 + 主背包）第 {@code playerSlotIndex} 格的东西装到角色的
   * 某个装备位上。
   *
   * <h2>为什么不能复用上面那条</h2>
   * {@code equipOrSwapArtifactRPCPacket} 的 {@code inventorySlotIndex} 是
   * <b>模组背包某个分类里的下标</b>（服务端拿 {@code getCategoryList(cat).get(index)} 取件），
   * 跟原版物品栏的槽位号完全不是一个坐标系。装备页的武器 / 圣遗物列表要同时列
   * 「模组背包里的」和「玩家物品栏里的」（用户口径：「武器列表同时读取玩家物品栏和原神背包里
   * 对应类型的武器 + 圣遗物也这样」），所以物品栏那一侧单开一条包。
   *
   * <p>换下来的旧件照旧退回<b>模组背包</b>对应分类 —— 和原版那条包的行为保持一致，
   * 玩家不会因为「从物品栏穿」而把旧件弄丢。
   */
  @RPCPacket("equipFromInventoryRPCPacket")
  public static void equipFromInventoryRPCPacket(RPCSender sender, int artifactSlotIndex, int playerSlotIndex) {
    if (sender.isServer()) {
      return;
    }
    ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
    PlayerCharactersAttachment attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
    PGCharacter currentChar = writeTarget(player, attachment);
    if (currentChar == null || currentChar.getData() == null) return;
    PGCharacterData charData = currentChar.getData();
    ArtifactInventory artifactInv = charData.getArtifactInventory();
    if (artifactSlotIndex < 0 || artifactSlotIndex >= artifactInv.slotCount()) return;

    var inventory = player.getInventory();
    if (playerSlotIndex < 0 || playerSlotIndex >= inventory.getContainerSize()) return;
    ItemStack newItem = inventory.getItem(playerSlotIndex);
    if (newItem.isEmpty()) return;
    if (!ArtifactInventory.isValidForSlot(artifactSlotIndex, newItem)) return;

    Backpack backpack = player.getData(AttachmentRegistration.BACKPACK_ATTACHMENT);
    boolean weaponSlot = artifactSlotIndex >= ArtifactInventory.SLOT_WEAPON;
    if (weaponSlot) {
      if (!isWeaponCompatibleWithCharacter(currentChar, newItem)) return;
    }

    ItemStack oldItem = artifactInv.getItem(artifactSlotIndex);
    ItemStack moved = newItem.copy();
    inventory.setItem(playerSlotIndex, ItemStack.EMPTY);
    artifactInv.setItem(artifactSlotIndex, moved);
    if (!oldItem.isEmpty() && backpack != null) {
      backpack.addItemToCategory(
              weaponSlot ? Backpack.Category.WEAPONS : Backpack.Category.ARTIFACTS, oldItem.copy());
    }

    currentChar.recalculateDirtyArtifactSlots();
    attachment.syncToPlayer(player);
  }

  public static void sendEquipFromInventoryToServer(int artifactSlotIndex, int playerSlotIndex) {
    RPCPacketDistributor.rpcToServer("equipFromInventoryRPCPacket", artifactSlotIndex, playerSlotIndex);
  }

  @RPCPacket("unequipArtifactRPCPacket")
  public static void unequipArtifactRPCPacket(RPCSender sender, int artifactSlotIndex) {
    if (sender.isServer()) {
      ClientHandler.unequipArtifactClientHandler(artifactSlotIndex);
    } else {
      ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
      PlayerCharactersAttachment attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
      PGCharacter currentChar = writeTarget(player, attachment);
      if (currentChar == null || currentChar.getData() == null) return;
      PGCharacterData charData = currentChar.getData();
      ArtifactInventory artifactInv = charData.getArtifactInventory();

      if (artifactSlotIndex < 0 || artifactSlotIndex >= artifactInv.slotCount()) return;
      ItemStack oldArtifact = artifactInv.getItem(artifactSlotIndex);
      if (oldArtifact.isEmpty()) return;

      artifactInv.setItem(artifactSlotIndex, ItemStack.EMPTY);

      Backpack backpack = player.getData(AttachmentRegistration.BACKPACK_ATTACHMENT);
      if (artifactSlotIndex >= ArtifactInventory.SLOT_WEAPON) {
        backpack.addItemToCategory(Backpack.Category.WEAPONS, oldArtifact.copy());
      } else {
        backpack.addItemToCategory(Backpack.Category.ARTIFACTS, oldArtifact.copy());
      }

      currentChar.recalculateDirtyArtifactSlots();
      attachment.syncToPlayer(player);
    }
  }

  public static void sendUnequipArtifactToServer(int artifactSlotIndex) {
    RPCPacketDistributor.rpcToServer("unequipArtifactRPCPacket", artifactSlotIndex);
  }

  // ==========================================================================
  // 角色装备页（按键 U）：升级 / 命座
  // ==========================================================================

  /**
   * 「升级」的目标哨兵：角色本人 / 武器。其余非负值 = 圣遗物槽位下标
   * （见 {@link ArtifactInventory#SLOT_FLOWER} 那一组常量）。
   */
  public static final int EQUIP_LEVEL_TARGET_CHARACTER = -1;
  public static final int EQUIP_LEVEL_TARGET_WEAPON = -2;

  /**
   * 升级一个目标（角色 / 武器 / 某一件<b>已装备</b>的圣遗物）。
   *
   * <h2>为什么统一走经验书</h2>
   * 本模组的成长资源就是 {@code AdviceBookItem}（经验书）：用一本给当前角色的
   * 角色经验 + 身上五件圣遗物 + 武器各加一份，并消耗掉那一本。
   * 装备页上那三个「升级」按钮因此都发这一条包，由服务端<b>找一本经验书用掉</b>，
   * 再把这份经验只加给 {@code target} 指定的那一个 —— 不新造经济，
   * 也不允许「什么都没消耗就把等级刷上去」。
   *
   * <p>顺序是「先判目标能不能升，再吃书」：反过来的话，一件满级圣遗物会把书白吃掉。
   */
  @RPCPacket("equipLevelUpRPCPacket")
  public static void equipLevelUpRPCPacket(RPCSender sender, int target) {
    if (sender.isServer()) {
      return;
    }
    ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
    PlayerCharactersAttachment attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
    PGCharacter character = writeTarget(player, attachment);
    if (character == null || character.getData() == null) {
      player.sendSystemMessage(Component.translatable("message.minegenshin.no_character_selected"));
      return;
    }
    PGCharacterData data = character.getData();

    if (!canLevelUpTarget(player, data, target)) {
      return;
    }

    int exp = consumeAdviceBook(player);
    if (exp <= 0) {
      player.sendSystemMessage(Component.translatable("message.minegenshin.advice_book_low"));
      return;
    }

    addEquipLevelExp(character, target, exp);

    attachment.syncToPlayer(player);
  }

  public static void sendEquipLevelUpToServer(int target) {
    RPCPacketDistributor.rpcToServer("equipLevelUpRPCPacket", target);
  }

  /** 升级材料取自哪里：玩家主物品栏（含快捷栏）/ 模组背包的 develop 分类。 */
  public static final int EQUIP_MATERIAL_SOURCE_INVENTORY = 0;
  public static final int EQUIP_MATERIAL_SOURCE_BACKPACK = 1;

  /**
   * 「先判目标能不能升」——两条升级包（单本 / 批量）共用这一份前置条件。
   *
   * <p>顺序理由同 {@link #equipLevelUpRPCPacket}：反过来的话，一件满级装备会把材料白吃掉。
   * 不通过时顺便把原因发回给玩家（满级 / 没穿武器）。
   */
  private static boolean canLevelUpTarget(ServerPlayer player, PGCharacterData data, int target) {
    ArtifactInventory inv = data.getArtifactInventory();
    if (target == EQUIP_LEVEL_TARGET_CHARACTER) {
      if (data.getLevel() >= 90) {
        player.sendSystemMessage(Component.translatable("message.minegenshin.character_max_level_reached"));
        return false;
      }
      return true;
    }
    if (target == EQUIP_LEVEL_TARGET_WEAPON) {
      ItemStack weapon = data.getWeapon();
      if (weapon.isEmpty() || !(weapon.getItem() instanceof WeaponItem)) {
        player.sendSystemMessage(Component.translatable("message.minegenshin.no_weapon_equipped"));
        return false;
      }
      return true;
    }
    // 升级目标里的"某一格"只可能是 5 件圣遗物（武器走 EQUIP_LEVEL_TARGET_WEAPON）
    if (target < 0 || target > ArtifactInventory.SLOT_CIRCLET) {
      return false;
    }
    ItemStack artifact = inv.getItem(target);
    if (artifact.isEmpty() || !(artifact.getItem() instanceof ArtifactItem art)) {
      return false;
    }
    ArtifactStatsComponent stats =
            artifact.getOrDefault(ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT);
    if (stats.level >= stats.getMaxLevel(art.getStar())) {
      player.sendSystemMessage(Component.translatable("message.minegenshin.artifact_max_level"));
      return false;
    }
    return true;
  }

 /** 把一份经验加给 {@code target}（角色 / 武器 / 某件已装备的圣遗物）。 */
  private static void addEquipLevelExp(PGCharacter character, int target, int exp) {
    if (character == null || character.getData() == null || exp <= 0) {
      return;
    }
    PGCharacterData data = character.getData();
    ArtifactInventory inv = data.getArtifactInventory();
    // 用户口径（2026-09-27）：**溢出经验不保留** —— 角色和武器一视同仁。
    // 到本次上限能吃多少就吃多少，多出来的这截直接丢掉（客户端那条确认框说的就是这件事）。
    // room 与客户端读的是同一份（见 PGCharacter#characterExpRoom / #weaponExpRoom / #artifactExpRoom），
    // 所以界面说「会溢出 X」时服务端丢的正好也是 X。
    long room = target == EQUIP_LEVEL_TARGET_CHARACTER
            ? character.characterExpRoom()
            : target == EQUIP_LEVEL_TARGET_WEAPON
                    ? character.weaponExpRoom()
                    : character.artifactExpRoom(target);
    int applied = (int) Math.max(0L, Math.min(exp, room));
    if (applied <= 0) {
      return;
    }
    exp = applied;
    if (target == EQUIP_LEVEL_TARGET_CHARACTER) {
      character.addExp(exp);
    } else if (target == EQUIP_LEVEL_TARGET_WEAPON) {
      ItemStack weapon = data.getWeapon();
      if (weapon.getItem() instanceof WeaponItem weaponItem) {
        WeaponStatsComponent stats = weapon.getOrDefault(
                ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT);
        // 让「词条变了」这件事落到背包里那一格上（和 AdviceBookItem 同一条路数）
        stats.setOnStatsChanged(() -> inv.markDirty(ArtifactInventory.SLOT_WEAPON));
        stats.addExp(exp, weaponItem.getStar());
        weapon.set(ModDataComponents.WEAPON_STATS.get(), stats);
        inv.markDirty(ArtifactInventory.SLOT_WEAPON);
        character.recalculateWeaponSlot();
      }
    } else {
      ItemStack artifact = inv.getItem(target);
      if (artifact.getItem() instanceof ArtifactItem art) {
        ArtifactStatsComponent stats = artifact.getOrDefault(
                ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT);
        stats.setOnStatsChanged(() -> inv.markDirty(target));
        stats.addExp(exp, art.getStar(), art.getType());
        artifact.set(ModDataComponents.ARTIFACT_STATS.get(), stats);
        inv.markDirty(target);
        character.recalculateDirtyArtifactSlots();
      }
    }
  }

  /**
   * 批量升级：把「某一格材料」的 {@code count} 本一次性喂给 {@code target}。
   *
   * <h2>和单本那条的区别</h2>
   * {@link #equipLevelUpRPCPacket} 是「随便找一本用掉」——按钮点一下走一本，
   * 页面上的「升级」直接就出结果。装备页新版的升级子页面要的是
   * 「玩家自己挑材料 + 自己定数量」（用户口径：读玩家背包和原神背包里的所有经验书、
   * 可以 + - 或直接设数量、然后点升级），所以材料<b>由客户端指名</b>：
   * {@code materialSource} 选坐标系，{@code materialSlot} 是那一格，{@code count} 是本数。
   *
   * <p>服务端仍然只信自己那份数据：按坐标重新取那一格，确认它真是经验书，
   * 再把本数夹到「这一格实际有的数量」以内 —— 客户端报多少都不影响上限。
   */
  @RPCPacket("equipLevelUpBatchRPCPacket")
  public static void equipLevelUpBatchRPCPacket(RPCSender sender, int target, int materialSource,
                                                int materialSlot, int count) {
    if (sender.isServer()) {
      return;
    }
    ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
    PlayerCharactersAttachment attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
    PGCharacter character = writeTarget(player, attachment);
    if (character == null || character.getData() == null) {
      player.sendSystemMessage(Component.translatable("message.minegenshin.no_character_selected"));
      return;
    }
    PGCharacterData data = character.getData();

    if (!canLevelUpTarget(player, data, target)) {
      return;
    }

    ItemStack material;
    boolean fromBackpack = materialSource == EQUIP_MATERIAL_SOURCE_BACKPACK;
    Backpack backpack = player.getData(AttachmentRegistration.BACKPACK_ATTACHMENT);
    var inventory = player.getInventory();
    if (fromBackpack) {
      if (backpack == null) return;
      var list = backpack.getCategoryList(Backpack.Category.DEVELOPMENT);
      if (materialSlot < 0 || materialSlot >= list.size()) return;
      material = list.get(materialSlot);
    } else {
      if (materialSlot < 0 || materialSlot >= inventory.getContainerSize()) return;
      material = inventory.getItem(materialSlot);
    }
    if (!(material.getItem() instanceof AdviceBookItem book)) {
      return;
    }

    int available = material.getCount();
    if (available <= 0) {
      return;
    }
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

  public static void sendEquipLevelUpBatchToServer(int target, int materialSource,
                                                   int materialSlot, int count) {
    RPCPacketDistributor.rpcToServer("equipLevelUpBatchRPCPacket", target, materialSource,
            materialSlot, count);
  }

  /**
   * 找一本经验书用掉，返回它带的经验值（找不到返回 0）。
   *
   * <p>先翻主物品栏（含副手），再翻模组背包的「develop」分类 —— 玩家把书放哪儿都行，
   * 但只吃<b>一本</b>：单击一次升级就是一本，和「用一本经验书」这件事的粒度一致。
   */
  private static int consumeAdviceBook(ServerPlayer player) {
    var inventory = player.getInventory();
    for (int i = 0; i < inventory.getContainerSize(); i++) {
      ItemStack stack = inventory.getItem(i);
      if (stack.getItem() instanceof AdviceBookItem book) {
        stack.shrink(1);
        return book.getExpValue();
      }
    }
    Backpack backpack = player.getData(AttachmentRegistration.BACKPACK_ATTACHMENT);
    if (backpack != null) {
      var list = backpack.getCategoryList(Backpack.Category.DEVELOPMENT);
      for (ItemStack stack : list) {
        if (stack.getItem() instanceof AdviceBookItem book) {
          stack.shrink(1);
          backpack.setChanged();
          return book.getExpValue();
        }
      }
    }
    return 0;
  }

  /**
   * 突破命之座（把命座等级 +1）。
   *
   * <p>没有命座材料这套经济（{@code addConstellation} 目前只有抽卡的「抽到重复角色」会调），
   * 所以这条包是<b>调试口</b>：服务端自己再判一次权限等级 ≥ 2，非作弊模式一律拒绝。
   * 页面那边也是同一条口径 —— 非作弊时连按钮都不建。
   */
  @RPCPacket("upgradeConstellationRPCPacket")
  public static void upgradeConstellationRPCPacket(RPCSender sender) {
    if (sender.isServer()) {
      return;
    }
    ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
    if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
      return;
    }
    PlayerCharactersAttachment attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
    PGCharacter character = writeTarget(player, attachment);
    if (character == null) {
      player.sendSystemMessage(Component.translatable("message.minegenshin.no_character_selected"));
      return;
    }
    if (!character.addConstellation()) {
      player.sendSystemMessage(Component.translatable("message.minegenshin.constellation_max"));
      return;
    }
    attachment.syncToPlayer(player);
    player.sendSystemMessage(Component.translatable("message.minegenshin.constellation_up_success",
            character.getConstellation()));
  }

  public static void sendUpgradeConstellationToServer() {
    RPCPacketDistributor.rpcToServer("upgradeConstellationRPCPacket");
  }

  @RPCPacket("openBackpackRPCPacket")
  public static void openBackpackRPCPacket(RPCSender sender) {
    if (!sender.isServer()) {
      ServerPlayer serverPlayer = sender.asPlayer();
      if (serverPlayer == null) return;
      PlayerUIMenuType.openUI(serverPlayer,
              Identifier.fromNamespaceAndPath("minegenshin", "backpack"));
    }
  }
  public static void openBackpackUIToServer() {
    RPCPacketDistributor.rpcToServer("openBackpackRPCPacket");
  }

  @RPCPacket("primogemWish")
  public static void primogemWish(RPCSender sender) {
    if (!sender.isServer()) {
      ServerPlayer serverPlayer = sender.asPlayer();
      if (serverPlayer == null) return;
      WishSystem.performWish(serverPlayer);
    }
  }

  public static void wishEventToServer() {
    RPCPacketDistributor.rpcToServer("primogemWish");
  }

  @RPCPacket("openCharacterInfoRPCPacket")
  public static void openCharacterInfoRPCPacket(RPCSender sender) {
    if (!sender.isServer()) {
      ServerPlayer serverPlayer = sender.asPlayer();
      if (serverPlayer == null) return;
      serverPlayer.openMenu(new net.minecraft.world.MenuProvider() {
        @Override
        public Component getDisplayName() {
          return Component.literal("Character Info");
        }
        @Override
        public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int containerId, net.minecraft.world.entity.player.Inventory inventory, net.minecraft.world.entity.player.Player p) {
          return new CharacterInfoMenu(containerId, inventory);
        }
      });
    }
  }

  public static void openCharacterInfoScreenToServer() {
    RPCPacketDistributor.rpcToServer("openCharacterInfoRPCPacket");
  }

  @RPCPacket("activateArtifactRPCPacket")
  public static void activateArtifactRPCPacket(RPCSender sender, int inventorySlotIndex) {
    // 只处理「客户端 → 服务端」这一向：抽词条是服务端的事。
    if (sender.isServer()) return;

    ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
    Backpack backpack = player.getData(AttachmentRegistration.BACKPACK_ATTACHMENT);
    int globalSlot = getCategoryOffset(Backpack.Category.ARTIFACTS) + inventorySlotIndex;
    ItemStack stack = backpack.getItem(globalSlot);
    if (stack.isEmpty() || !(stack.getItem() instanceof ArtifactItem)) return;

    ArtifactStatsComponent stats = stack.getOrDefault(
            ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT);

    if (!stats.activated) {
      ArtifactItem.initializeArtifactStackIfNeeded(stack);
      backpack.setItem(globalSlot, stack);
      LOGGER.info("服务端激活圣遗物: 背包索引 {}", inventorySlotIndex);
    }

    // ⚠️ 抽完（或本来就已经激活）一定要把**服务端这一份权威数据**发回客户端：
    // 客户端不许自己抽（`new Random()` 不是同一份，会抽成另一套词条），
    // 而背包 attachment 的同步不保证在这一刻就到，所以这里显式推一次，
    // 否则 UI 会一直停在「未激活」、按钮看起来是坏的。
    RPCPacketDistributor.rpcToPlayer(player, "artifactActivatedRPCPacket", inventorySlotIndex, stack);
  }

  /** 服务端 → 客户端：把抽好的那件圣遗物推回去（客户端只负责放进槽位）。 */
  @RPCPacket("artifactActivatedRPCPacket")
  public static void artifactActivatedRPCPacket(RPCSender sender, int inventorySlotIndex, ItemStack stack) {
    if (!sender.isServer()) return;     // 只有服务端会发这一向
    ClientHandler.applyActivatedArtifactClientHandler(inventorySlotIndex, stack);
  }

  public static void sendActivateArtifactToServer(int inventorySlotIndex) {
    RPCPacketDistributor.rpcToServer("activateArtifactRPCPacket", inventorySlotIndex);
  }

  public static void sendActivateArtifactToPlayer(ServerPlayer player, int inventorySlotIndex) {
    RPCPacketDistributor.rpcToPlayer(player, "activateArtifactRPCPacket", inventorySlotIndex);
  }

  /**
   * 激活<b>玩家主物品栏</b>（快捷栏 + 主背包）第 {@code playerSlotIndex} 格里那件圣遗物。
   *
   * <h2>为什么另开一条</h2>
   * {@code activateArtifactRPCPacket} 的 {@code inventorySlotIndex} 是<b>模组背包</b>
   * {@code ARTIFACTS} 分类里的下标；角色装备页（按键 U）的圣遗物列表同时列模组背包和玩家
   * 物品栏（用户口径），玩家物品栏里那件点「激活」原先发的是上面那条包 —— 服务端拿着
   * 物品栏槽位号去翻模组背包，取到的是别的格子，于是看上去「激活按钮是坏的」。
   * 这条按原版物品栏槽位号取值，抽完再把服务端那份权威数据推回客户端。
   */
  @RPCPacket("activateInventoryArtifactRPCPacket")
  public static void activateInventoryArtifactRPCPacket(RPCSender sender, int playerSlotIndex) {
    if (sender.isServer()) return;     // 只有客户端会发这一向

    ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
    var inventory = player.getInventory();
    if (playerSlotIndex < 0 || playerSlotIndex >= inventory.getContainerSize()) return;

    ItemStack stack = inventory.getItem(playerSlotIndex);
    if (stack.isEmpty() || !(stack.getItem() instanceof ArtifactItem)) return;

    ArtifactStatsComponent stats = stack.getOrDefault(
            ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT);
    if (!stats.activated) {
      ArtifactItem.initializeArtifactStackIfNeeded(stack);
      inventory.setItem(playerSlotIndex, stack);
      LOGGER.info("服务端激活圣遗物（玩家物品栏）: 槽位 {}", playerSlotIndex);
    }

    RPCPacketDistributor.rpcToPlayer(player, "artifactActivatedInventoryRPCPacket",
            playerSlotIndex, stack);
  }

  /** 服务端 → 客户端：把抽好的那件圣遗物推回去（客户端只负责写回物品栏那一格）。 */
  @RPCPacket("artifactActivatedInventoryRPCPacket")
  public static void artifactActivatedInventoryRPCPacket(RPCSender sender, int playerSlotIndex,
                                                        ItemStack stack) {
    if (!sender.isServer()) return;    // 只有服务端会发这一向
    ClientHandler.applyActivatedInventoryArtifact(playerSlotIndex, stack);
  }

  public static void sendActivateInventoryArtifactToServer(int playerSlotIndex) {
    RPCPacketDistributor.rpcToServer("activateInventoryArtifactRPCPacket", playerSlotIndex);
  }

  private static int getCategoryOffset(Backpack.Category target) {
    int offset = 0;
    for (var category : Backpack.Category.values()) {
      if (category == target) break;
      offset += category.maxCapacity;
    }
    return offset;
  }

  @RPCPacket("backpackTakeOutFromSlotRPCPacket")
  public static void backpackTakeOutFromSlotRPCPacket(RPCSender sender, int slotIndex) {
    if (sender.isServer()) {
    } else {
      ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
      Backpack backpack = player.getData(AttachmentRegistration.BACKPACK_ATTACHMENT);
      ItemStack removed = backpack.getItem(slotIndex);
      if (removed.isEmpty()) return;
      backpack.setItem(slotIndex, ItemStack.EMPTY);
      giveItemToPlayer(player, removed);
    }
  }

  public static void sendBackpackTakeOutFromSlotToServer(int slotIndex) {
    RPCPacketDistributor.rpcToServer("backpackTakeOutFromSlotRPCPacket", slotIndex);
  }

  public static void giveItemToPlayer(ServerPlayer player, ItemStack stack) {
    int remaining = stack.getCount();

    net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
    for (int i = 0; i < inventory.getContainerSize(); i++) {
      if (remaining <= 0) break;

      ItemStack existing = inventory.getItem(i);
      if (existing.isEmpty()) continue;

      if (ItemStack.isSameItemSameComponents(existing, stack)
              && existing.getCount() < existing.getMaxStackSize()) {
        int canAdd = Math.min(remaining, existing.getMaxStackSize() - existing.getCount());
        existing.grow(canAdd);
        remaining -= canAdd;
      }
    }

    if (remaining > 0) {
      for (int i = 0; i < inventory.getContainerSize(); i++) {
        if (remaining <= 0) break;

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
      net.minecraft.world.entity.item.ItemEntity itemEntity =
              new net.minecraft.world.entity.item.ItemEntity(
                      player.level(), player.getX(), player.getY() + 0.5, player.getZ(), dropStack);
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
      AdventurerInfoAttachment advInfo = player.getData(AttachmentRegistration.ADVENTURER_INFO_ATTACHMENT);
      if (!advInfo.canBreakthroughWorldLevel()) {
        player.sendSystemMessage(Component.translatable("message.minegenshin.adventure_rank_breakthrough_not_met"));
        return;
      }
      player.giveExperienceLevels(-30);
      advInfo.breakthroughWorldLevel();
      advInfo.syncToPlayer(player);
      player.sendSystemMessage(Component.translatable("message.minegenshin.adventure_rank_breakthrough_success", advInfo.getWorldLevel()));
    }
  }

  public static void sendAscendAdventureRankToServer() {
    RPCPacketDistributor.rpcToServer("ascendAdventureRankRPCPacket");
  }

  @RPCPacket("ascendCharacterRPCPacket")
  public static void ascendCharacterRPCPacket(RPCSender sender) {
    if (!sender.isServer()) {
      ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
      PlayerCharactersAttachment attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
      PGCharacter character = writeTarget(player, attachment);
      if (character == null || character.getData() == null) {
        player.sendSystemMessage(Component.translatable("message.minegenshin.no_character_selected"));
        return;
      }
      int maxLevelForPhase = character.getData().getAscensionPhase() == 0
              ? 20
              : Math.min((character.getData().getAscensionPhase() + 3) * 10, 90);
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
      player.sendSystemMessage(Component.translatable("message.minegenshin.character_breakthrough_success", character.getData().getAscensionPhase()));
    }
  }

  public static void sendAscendCharacterToServer() {
    RPCPacketDistributor.rpcToServer("ascendCharacterRPCPacket");
  }

  @RPCPacket("ascendWeaponRPCPacket")
  public static void ascendWeaponRPCPacket(RPCSender sender) {
    if (!sender.isServer()) {
      ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
      int primogem = player.getData(AttachmentRegistration.PRIMOGEM_ATTACHMENT);
      if (primogem < 1600) {
        player.sendSystemMessage(Component.translatable("message.minegenshin.weapon_primogem_low"));
        return;
      }
      if (player.experienceLevel < 10) {
        player.sendSystemMessage(Component.translatable("message.minegenshin.weapon_exp_low"));
        return;
      }
      PlayerCharactersAttachment attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
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
    RPCPacketDistributor.rpcToServer("ascendWeaponRPCPacket");
  }

  @RPCPacket("upgradeNormalAttackRPCPacket")
  public static void upgradeNormalAttackRPCPacket(RPCSender sender) {
    if (!sender.isServer()) {
      ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
      PlayerCharactersAttachment attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
      PGCharacter character = writeTarget(player, attachment);
      if (character == null || character.getData() == null) {
        player.sendSystemMessage(Component.translatable("message.minegenshin.no_character_selected"));
        return;
      }
      // 先判「还能不能升」，再判材料 —— 满级时就该报满级，而不是报「原石不足」
      if (!character.getData().canUpgradeNormalAttack()) {
        player.sendSystemMessage(Component.translatable("message.minegenshin.normal_attack_max_level"));
        return;
      }
      // ⚠️ 消耗按【手动次数】查表，不按有效等级：手动 4 级 +3 命显示 7 级时，
      //    下一次消耗的仍然是「4→5」那一档
      int manual = character.getData().getManualNormalAttack();
      int costPrimogem = com.linweiyun.genshin.core.character.talent.TalentUpgradeCost.primogem(manual);
      int costExp = com.linweiyun.genshin.core.character.talent.TalentUpgradeCost.experienceLevels(manual);

      int primogem = player.getData(AttachmentRegistration.PRIMOGEM_ATTACHMENT);
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
      player.sendSystemMessage(Component.translatable("message.minegenshin.normal_attack_upgrade_success", character.getData().getNormalAttackLevel()));
    }
  }

  public static void sendUpgradeNormalAttackToServer() {
    RPCPacketDistributor.rpcToServer("upgradeNormalAttackRPCPacket");
  }

  @RPCPacket("upgradeElementalSkillRPCPacket")
  public static void upgradeElementalSkillRPCPacket(RPCSender sender) {
    if (!sender.isServer()) {
      ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
      PlayerCharactersAttachment attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
      PGCharacter character = writeTarget(player, attachment);
      if (character == null || character.getData() == null) {
        player.sendSystemMessage(Component.translatable("message.minegenshin.no_character_selected"));
        return;
      }
      if (!character.getData().canUpgradeElementalSkill()) {
        player.sendSystemMessage(Component.translatable("message.minegenshin.elemental_skill_max_level"));
        return;
      }
      // 消耗按【手动次数】查表（命座 +3 抬高的是有效等级，不该抬价）
      int manual = character.getData().getManualElementalSkill();
      int costPrimogem = com.linweiyun.genshin.core.character.talent.TalentUpgradeCost.primogem(manual);
      int costExp = com.linweiyun.genshin.core.character.talent.TalentUpgradeCost.experienceLevels(manual);

      int primogem = player.getData(AttachmentRegistration.PRIMOGEM_ATTACHMENT);
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
      player.sendSystemMessage(Component.translatable("message.minegenshin.elemental_skill_upgrade_success", character.getData().getElementalSkillLevel()));
    }
  }

  public static void sendUpgradeElementalSkillToServer() {
    RPCPacketDistributor.rpcToServer("upgradeElementalSkillRPCPacket");
  }

  @RPCPacket("upgradeElementalBurstRPCPacket")
  public static void upgradeElementalBurstRPCPacket(RPCSender sender) {
    if (!sender.isServer()) {
      ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
      PlayerCharactersAttachment attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
      PGCharacter character = writeTarget(player, attachment);
      if (character == null || character.getData() == null) {
        player.sendSystemMessage(Component.translatable("message.minegenshin.no_character_selected"));
        return;
      }
      if (!character.getData().canUpgradeElementalBurst()) {
        player.sendSystemMessage(Component.translatable("message.minegenshin.elemental_burst_max_level"));
        return;
      }
      // 消耗按【手动次数】查表（5 命 +3 抬高的是有效等级，不该抬价）
      int manual = character.getData().getManualElementalBurst();
      int costPrimogem = com.linweiyun.genshin.core.character.talent.TalentUpgradeCost.primogem(manual);
      int costExp = com.linweiyun.genshin.core.character.talent.TalentUpgradeCost.experienceLevels(manual);

      int primogem = player.getData(AttachmentRegistration.PRIMOGEM_ATTACHMENT);
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
      player.sendSystemMessage(Component.translatable("message.minegenshin.elemental_burst_upgrade_success", character.getData().getElementalBurstLevel()));
    }
  }

  public static void sendUpgradeElementalBurstToServer() {
    RPCPacketDistributor.rpcToServer("upgradeElementalBurstRPCPacket");
  }

  @RPCPacket("downgradeWorldLevelRPCPacket")
  public static void downgradeWorldLevelRPCPacket(RPCSender sender) {
    if (!sender.isServer()) {
      ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
      AdventurerInfoAttachment advInfo = player.getData(AttachmentRegistration.ADVENTURER_INFO_ATTACHMENT);
      if (!advInfo.canDowngradeWorldLevel()) {
        player.sendSystemMessage(Component.translatable("message.minegenshin.world_level_downgrade_not_met"));
        return;
      }
      advInfo.downgradeWorldLevel();
      advInfo.syncToPlayer(player);
      player.sendSystemMessage(Component.translatable("message.minegenshin.world_level_downgrade_success", advInfo.getWorldLevel()));
    }
  }

  public static void sendDowngradeWorldLevelToServer() {
    RPCPacketDistributor.rpcToServer("downgradeWorldLevelRPCPacket");
  }

  @RPCPacket("restoreWorldLevelRPCPacket")
  public static void restoreWorldLevelRPCPacket(RPCSender sender) {
    if (!sender.isServer()) {
      ServerPlayer player = Objects.requireNonNull(sender.asPlayer());
      AdventurerInfoAttachment advInfo = player.getData(AttachmentRegistration.ADVENTURER_INFO_ATTACHMENT);
      if (!advInfo.canRestoreWorldLevel()) {
        player.sendSystemMessage(Component.translatable("message.minegenshin.world_level_no_restore"));
        return;
      }
      advInfo.restoreWorldLevel();
      advInfo.syncToPlayer(player);
      player.sendSystemMessage(Component.translatable("message.minegenshin.world_level_restore_success", advInfo.getWorldLevel()));
    }
  }

  public static void sendRestoreWorldLevelToServer() {
    RPCPacketDistributor.rpcToServer("restoreWorldLevelRPCPacket");
  }

  @RPCPacket("invasionStatusRPCPacket")
  public static void invasionStatusRPCPacket(RPCSender sender, boolean invaded) {
    if (sender.isServer()) {
      ClientHandler.invasionStatusClientHandler(invaded);
    }
  }

  public static void setInvasionStatusToPlayer(ServerPlayer player, boolean invaded) {
    RPCPacketDistributor.rpcToPlayer(player, "invasionStatusRPCPacket", invaded);
  }

  @RPCPacket("minegenshin:entity_sync")
  public static void entitySyncRPCPacket(RPCSender sender, int entityId, CompoundTag payload) {
    if (sender.isServer()) {
      ClientHandler.entitySyncClientHandler(entityId, payload);
    }
  }

  public static void sendEntitySyncToPlayer(ServerPlayer player, int entityId, CompoundTag payload) {
    RPCPacketDistributor.rpcToPlayer(player, "minegenshin:entity_sync", entityId, payload);
  }

  @RPCPacket("minegenshin:character_sync")
  public static void characterSyncRPCPacket(RPCSender sender, int characterUUID, CompoundTag payload) {
    if (sender.isServer()) {
      ClientHandler.characterSyncClientHandler(characterUUID, payload);
    }
  }

  public static void sendCharacterSyncToPlayer(ServerPlayer player, int characterUUID, CompoundTag payload) {
    RPCPacketDistributor.rpcToPlayer(player, "minegenshin:character_sync", characterUUID, payload);
  }

  // ========================================================================
  // 动作动画状态同步
  // 客户端 → 服务端：状态机每次切状态/复位都上报一次。
  // 服务端 → 客户端：不再走包，直接写 ANIMATION_STATE_ATTACHMENT 后 syncData，
  // NeoForge 会把它推给所有能收到这个玩家的客户端（含玩家自己）。
  // ========================================================================

  @RPCPacket("minegenshin:animation_state")
  public static void animationStateRPCPacket(RPCSender sender, String stateName, int totalTicks) {
    if (!sender.isServer()) {
      ServerPlayer player = sender.asPlayer();
      if (player == null) return;
      ServerAnimationTicker.apply(player, stateName, totalTicks);
    }
  }

  public static void sendAnimationStateToServer(String stateName, int totalTicks) {
    RPCPacketDistributor.rpcToServer("minegenshin:animation_state", stateName, totalTicks);
  }

  // ========================================================================
  // 身体朝向同步（视角独立 / 视角跟随的联机一致性）
  // 客户端 → 服务端：本机身体朝向变了才发（阈值见 BodyYawSync）。
  // 服务端 → 客户端：写 BODY_YAW_ATTACHMENT 后 syncData，NeoForge 推给所有跟踪者。
  // ========================================================================

  @RPCPacket("minegenshin:body_yaw")
  public static void bodyYawRPCPacket(RPCSender sender, float yaw) {
    if (!sender.isServer()) {
      ServerPlayer player = sender.asPlayer();
      if (player == null) return;
      ServerAnimationTicker.applyBodyYaw(player, yaw);
    }
  }

  public static void sendBodyYawToServer(float yaw) {
    if (!Float.isFinite(yaw)) return;
    RPCPacketDistributor.rpcToServer("minegenshin:body_yaw", yaw);
  }
}
