package com.linweiyun.genshin.core.sync;

import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.lowdragmc.lowdraglib2.syncdata.IManaged;
import com.lowdragmc.lowdraglib2.syncdata.holder.IManagedHolder;
import com.lowdragmc.lowdraglib2.syncdata.holder.IPersistManagedHolder;
import com.lowdragmc.lowdraglib2.syncdata.storage.IManagedStorage;
import com.lowdragmc.lowdraglib2.utils.ByteBufUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.BitSet;

/**
 * LDLib2 managed field sync interface for PGCharacter and subclasses.
 * Used to sync realtime character state (HP, energy, cooldown) to the owning player's GUI.
 */
public interface ISyncCharacter extends IManaged, IManagedHolder, IPersistManagedHolder {

    PGCharacter getSelfCharacter();

    @Override
    default ServerLevel getServerLevel() {
        var player = getSelfCharacter().getData().getOwnerPlayer();
        if (player != null && player.level() instanceof ServerLevel serverLevel) {
            return serverLevel;
        }
        throw new IllegalStateException("Character's owner player is not on a server level");
    }

    @Override
    default ChunkPos getTrackingPos() {
        var player = getSelfCharacter().getData().getOwnerPlayer();
        if (player != null) {
            return player.chunkPosition();
        }
        return ChunkPos.ZERO;
    }

    @Override
    default IManagedStorage getRootStorage() {
        return getSyncStorage();
    }

    default void syncToClient() {
        var character = getSelfCharacter();
        var player = character.getData().getOwnerPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ServerLevel serverLevel = serverPlayer.serverLevel();

        var rootStorage = getRootStorage();
        for (var field : rootStorage.getNonLazyFields()) {
            field.update();
        }
        if (!rootStorage.hasDirtySyncFields()) return;

        var syncedFields = rootStorage.getSyncFields();
        if (syncedFields.length == 0) return;

        var changed = new BitSet();
        var data = ByteBufUtil.writeCustomData(buffer -> {
            for (int i = 0; i < syncedFields.length; i++) {
                var field = syncedFields[i];
                if (field.isSyncDirty()) {
                    changed.set(i);
                    field.readSyncToStream(buffer);
                    field.clearSyncDirty();
                }
            }
        }, serverLevel.registryAccess());

        if (changed.isEmpty()) return;

        var payload = new CompoundTag();
        payload.putLongArray("changed", changed.toLongArray());
        payload.putByteArray("data", data);

        int characterUUID = character.getCharacterUUID();
        NetworkManager.sendCharacterSyncToPlayer(serverPlayer, characterUUID, payload);
    }

    default void handleCharacterSyncPacket(CompoundTag payload) {
        var changed = BitSet.valueOf(payload.getLongArray("changed"));
        var data = payload.getByteArray("data");

        var player = getSelfCharacter().getData().getOwnerPlayer();
        if (player == null) return;
        var level = player.level();
        ByteBufUtil.readCustomData(data, buffer -> {
            var storage = getRootStorage();
            var syncedFields = storage.getSyncFields();
            for (int i = 0; i < syncedFields.length; i++) {
                if (changed.get(i)) {
                    var field = syncedFields[i];
                    field.writeSyncFromStream(buffer);
                }
            }
        }, level.registryAccess());
    }
}