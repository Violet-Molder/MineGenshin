package com.linweiyun.genshin.core.sync;

import com.linweiyun.genshin.core.network.NetworkManager;
import com.lowdragmc.lowdraglib2.syncdata.IManaged;
import com.lowdragmc.lowdraglib2.syncdata.holder.IManagedHolder;
import com.lowdragmc.lowdraglib2.syncdata.holder.IPersistManagedHolder;
import com.lowdragmc.lowdraglib2.syncdata.ref.IRef;
import com.lowdragmc.lowdraglib2.syncdata.storage.IManagedStorage;
import com.lowdragmc.lowdraglib2.utils.ByteBufUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import com.lowdragmc.lowdraglib2.utils.TagBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

import java.util.BitSet;

/**
 * LDLib2 managed field sync interface for Entities.
 * <p>
 * Entity classes implementing this interface get automatic server-to-client
 * sync via {@code @DescSynced} and persistence via {@code @Persisted},
 * powered by {@link com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage}.
 * <p>
 * Usage:
 * <pre>{@code
 * public class MyEntity extends Entity implements ISyncManagedEntity {
 *     @Getter
 *     private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);
 *
 *     @Persisted @DescSynced
 *     private int someValue;
 *
 *     // In tick() on server side:
 *     if (!level().isClientSide()) {
 *         passivelySync();
 *     }
 *
 *     // In create() after setting initial values:
 *     sync(true);
 * }
 * }</pre>
 */
public interface ISyncManagedEntity extends IManaged, IManagedHolder, IPersistManagedHolder {

    Entity getSelf();

    @Override
    default ServerLevel getServerLevel() {
        if (getSelf().level() instanceof ServerLevel serverLevel) {
            return serverLevel;
        }
        throw new IllegalStateException("Entity is not on a server level");
    }

    @Override
    default ChunkPos getTrackingPos() {
        return getSelf().chunkPosition();
    }

    @Override
    default IManagedStorage getRootStorage() {
        return getSyncStorage();
    }

    /**
     * Perform a sync now. Call this after setting initial values on the server,
     * or periodically in tick() for delta updates.
     *
     * @param force if true, all fields will be synced; otherwise only dirty fields
     */
    default void sync(boolean force) {
        var entity = getSelf();
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;
        if (entity.isRemoved()) return;

        var rootStorage = getRootStorage();
        for (var field : rootStorage.getNonLazyFields()) {
            field.update();
        }
        if (rootStorage.hasDirtySyncFields() || force) {
            var changed = new BitSet();
            var syncedFields = rootStorage.getSyncFields();
            if (syncedFields.length == 0) return;

            var data = ByteBufUtil.writeCustomData(buffer -> {
                for (int i = 0; i < syncedFields.length; i++) {
                    var field = syncedFields[i];
                    if (force || field.isSyncDirty()) {
                        changed.set(i);
                        field.readSyncToStream(buffer);
                        field.clearSyncDirty();
                    }
                }
            }, serverLevel.registryAccess());

            if (changed.isEmpty() && !force) return;

            var extra = new CompoundTag();
            writeCustomSyncData(serverLevel.registryAccess(), extra);

            var payload = new CompoundTag();
            payload.putLongArray("changed", changed.toLongArray());
            payload.putByteArray("data", data);
            if (!extra.isEmpty()) {
                payload.put("extra", extra);
            }

            var entityId = entity.getId();
            for (var player : serverLevel.players()) {
                NetworkManager.sendEntitySyncToPlayer(player, entityId, payload);
            }
        }
    }

    /**
     * Perform a delta sync (only dirty fields). Call this in tick() on the server side.
     */
    default void passivelySync() {
        sync(false);
    }

    /**
     * Handle a sync packet received on the client.
     */
    default void handleSyncPacket(RegistryAccess registryAccess, BitSet changed, byte[] data, CompoundTag extra) {
        ByteBufUtil.readCustomData(data, buffer -> {
            var storage = getRootStorage();
            var syncedFields = storage.getSyncFields();
            for (int i = 0; i < syncedFields.length; i++) {
                if (changed.get(i)) {
                    var field = syncedFields[i];
                    field.writeSyncFromStream(buffer);
                }
            }
        }, registryAccess);
        readCustomSyncData(registryAccess, extra);
    }

    /**
     * Write custom data to the sync packet. Will always be included.
     */
    default void writeCustomSyncData(HolderLookup.Provider provider, CompoundTag tag) {
    }

    /**
     * Read custom data from the sync packet.
     */
    default void readCustomSyncData(HolderLookup.Provider provider, CompoundTag tag) {
    }

    /**
     * Serialize all synced fields for initial client spawn.
     * Call this in getAddEntityPacket() or similar.
     */
    default CompoundTag serializeInitialData(HolderLookup.Provider provider) {
        var tag = new CompoundTag();
        var customTag = new CompoundTag();
        writeCustomSyncData(provider, customTag);
        if (!customTag.isEmpty()) {
            tag.put("custom", customTag);
        }

        var list = new ListTag();
        var syncedFields = getRootStorage().getSyncFields();
        var ctx = provider.createSerializationContext(NbtOps.INSTANCE);
        for (IRef<?> syncedField : syncedFields) {
            list.add(TagBuilder.compound().add("d", syncedField.readInitialSync(ctx)).build());
        }
        if (!list.isEmpty()) {
            tag.put("managed", list);
        }
        return tag;
    }

    /**
     * Deserialize initial data on the client when entity is first created.
     */
    default void deserializeInitialData(HolderLookup.Provider provider, CompoundTag tag) {
        var customTag = tag.getCompound("custom");
        readCustomSyncData(provider, customTag);

        var list = tag.getList("managed", Tag.TAG_COMPOUND);
        var syncedFields = getRootStorage().getSyncFields();
        if (syncedFields.length != list.size()) {
            return;
        }
        var ctx = provider.createSerializationContext(NbtOps.INSTANCE);
        for (int i = 0; i < list.size(); i++) {
            var data = list.getCompound(i).get("d");
            syncedFields[i].writeInitialSync(ctx, data);
        }
    }
}