package com.linweiyun.genshin.core.network;

import com.linweiyun.genshin.core.attachment.AdventurerInfoAttachment;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.Backpack;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.sync.ISyncCharacter;
import com.linweiyun.genshin.core.sync.ISyncManagedEntity;
import com.linweiyun.genshin.core.world.TeyvatWorldInvasion;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;

import java.util.BitSet;

public class ClientHandler {

    // ========== 原石 ==========
    public static void primogemClientHandler(int amount) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.player.isRemoved()) {
            mc.player.setData(AttachmentRegistration.PRIMOGEM_ATTACHMENT, amount);
        }
    }

    // ========== 原神模式 ==========
    public static void genshinModeClientHandler(boolean isGenshinMode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.player.isRemoved()) {
            mc.player.setData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT, isGenshinMode);
        }
    }

    // ========== 冒险者信息 ==========
    public static void adventurerInfoClientHandler(CompoundTag data) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.player.isRemoved()) {
            AdventurerInfoAttachment attachment =
                    mc.player.getData(AttachmentRegistration.ADVENTURER_INFO_ATTACHMENT);
            attachment.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, mc.player.registryAccess(), data));
        }
    }

    // ========== 角色整体同步 ==========
    public static void playerCharactersClientHandler(CompoundTag data) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.player.isRemoved()) {
            PlayerCharactersAttachment attachment =
                    mc.player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            attachment.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, mc.player.registryAccess(), data));
            attachment.fixCharacterTypes();
        }
    }

    // ========== 单个角色数据 ==========
    public static void characterDataClientHandler(int uuid, CompoundTag data) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.player.isRemoved()) {
            PlayerCharactersAttachment attachment = mc.player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            PGCharacter character = attachment.getCharacterByUUID(uuid);
            if (character != null) {
                character.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, mc.player.registryAccess(), data));
            }
        }
    }

    // ========== 角色选择 ==========
    public static void characterSelectionClientHandler(int index) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.player.isRemoved()) {
            PlayerCharactersAttachment attachment =
                    mc.player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            attachment.setCurrentCharacterIndex(index);
        }
    }

    // ========== 设置队伍角色 ==========
    public static void setPartyCharacterClientHandler(int index, int characterUUID) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.player.isRemoved()) {
            PlayerCharactersAttachment attachment = mc.player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            attachment.setPartyCharacter(index, characterUUID);
        }
    }

    // ========== 移除队伍角色 ==========
    public static void removePartyCharacterClientHandler(int index) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.player.isRemoved()) {
            PlayerCharactersAttachment attachment = mc.player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            attachment.removePartyCharacter(index);
        }
    }

    // ========== 添加角色 ==========
    public static void addCharacterClientHandler(CompoundTag characterData) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.player.isRemoved()) {
            PlayerCharactersAttachment attachment = mc.player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            PGCharacter character = new PGCharacter();
            character.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, mc.player.registryAccess(), characterData));
            attachment.addCharacter(character, mc.player);
        }
    }

    // ========== 移除角色 ==========
    public static void removeCharacterClientHandler(int uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.player.isRemoved()) {
            PlayerCharactersAttachment attachment = mc.player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            attachment.removeCharacter(uuid);
        }
    }

    // ========== 圣遗物穿戴/更换 ==========
    public static void equipOrSwapArtifactClientHandler(int artifactSlotIndex, int inventorySlotIndex) {
        // 客户端不需要额外处理，数据通过 PlayerCharactersAttachment 同步回来
    }

    // ========== 圣遗物卸下 ==========
    public static void unequipArtifactClientHandler(int artifactSlotIndex) {
        // 客户端不需要额外处理，数据通过 PlayerCharactersAttachment 同步回来
    }

    /**
     * 客户端处理服务端推回来的「已激活圣遗物」：**直接采用服务端那一份**。
     *
     * <p>⚠️ 绝对不要在这里自己抽词条：`buildInitialStats(new Random())` 用的是**客户端**的随机数，
     * 抽出来的主副词条和服务端那次完全不同 —— 本地存着另一套词条，
     * 等它被同步/卸下写回服务端时，玩家就看到「穿一次再卸下来副词条变了一套」。
     */
    public static void applyActivatedArtifactClientHandler(int inventorySlotIndex, ItemStack activated) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return;
        if (activated == null || activated.isEmpty()) return;
        Backpack backpack = player.getData(AttachmentRegistration.BACKPACK_ATTACHMENT);
        int globalSlot = 0;
        for (var category : Backpack.Category.values()) {
            if (category == Backpack.Category.ARTIFACTS) break;
            globalSlot += category.maxCapacity;
        }
        globalSlot += inventorySlotIndex;
        backpack.setItem(globalSlot, activated.copy());

        // 界面不是每刻重建的 —— 不显式刷新的话，点了「激活」之后面板会一直停在未激活，
        // 要玩家再点一次别的圣遗物才更新。
        com.linweiyun.genshin.client.render.gui.screen.ScreenArtifactEquip.refreshIfOpen();
        // 新的角色装备页（按键 U）同样要立刻重画：它持有的是「点选那一刻」的旧拷贝。
        com.linweiyun.genshin.client.render.gui.screen.CharacterEquipUI.refreshIfOpen();
    }

    /**
     * 玩家主物品栏里那件圣遗物被激活之后，服务端把它推回来 —— 写回原版物品栏对应那一格。
     *
     * <p>同 {@link #applyActivatedArtifactClientHandler}：客户端只负责采用服务端那一份，
     * 绝不本地抽词条（本地随机数和服务端不是同一套）。
     */
    public static void applyActivatedInventoryArtifact(int playerSlotIndex, ItemStack activated) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return;
        if (activated == null || activated.isEmpty()) return;
        var inventory = player.getInventory();
        if (playerSlotIndex < 0 || playerSlotIndex >= inventory.getContainerSize()) return;
        inventory.setItem(playerSlotIndex, activated.copy());
        // 角色装备页（按键 U）拿着的是点选那一刻的旧拷贝，必须显式重画
        com.linweiyun.genshin.client.render.gui.screen.CharacterEquipUI.refreshIfOpen();
    }

//    // ========== 原神背包同步（服务端→客户端） ==========
//    public static void genshinBackpackClientHandler(CompoundTag data) {
//        Minecraft mc = Minecraft.getInstance();
//        if (mc.player != null && !mc.player.isRemoved()) {
//            GenshinBackpack backpack =
//                    mc.player.getData(AttachmentRegistration.GENSHIN_BACKPACK_ATTACHMENT);
//            backpack.setSuppressDirty(true);
//            backpack.deserialize(TagValueInput.create(
//                    ProblemReporter.DISCARDING, mc.player.registryAccess(), data));
//            backpack.setSuppressDirty(false);
//            backpack.clearDirty();
//            }
//    }

    public static void invasionStatusClientHandler(boolean invaded) {
        TeyvatWorldInvasion.setClientInvaded(invaded);
    }

    public static void entitySyncClientHandler(int entityId, CompoundTag payload) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var entity = mc.level.getEntity(entityId);
        if (entity instanceof ISyncManagedEntity syncEntity) {
            var changed = BitSet.valueOf(payload.getLongArray("changed").orElse(new long[0]));
            var data = payload.getByteArray("data").orElse(new byte[0]);
            var extra = payload.getCompoundOrEmpty("extra");
            syncEntity.handleSyncPacket(mc.level.registryAccess(), changed, data, extra);
        }
    }

    public static void characterSyncClientHandler(int characterUUID, CompoundTag payload) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var attachment = mc.player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        var character = attachment.getCharacterByUUID(characterUUID);
        if (character instanceof ISyncCharacter syncChar) {
            syncChar.handleCharacterSyncPacket(payload);
        }
    }
}
