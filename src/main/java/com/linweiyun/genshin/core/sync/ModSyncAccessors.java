package com.linweiyun.genshin.core.sync;

import com.linweiyun.genshin.core.character.PGCharacter;
import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import com.lowdragmc.lowdraglib2.syncdata.accessor.direct.CustomDirectAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Map;

public final class ModSyncAccessors {

    private static boolean registered = false;

    public static void register() {
        if (registered) return;
        registered = true;
        registerLivingEntityAccessor();
        registerPGCharacterAccessor();
    }

    private static HolderLookup.Provider getRegistryAccess() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) return server.registryAccess();
        var level = Minecraft.getInstance().level;
        if (level != null) return level.registryAccess();
        throw new IllegalStateException("No registry access available");
    }

    private static void registerLivingEntityAccessor() {
        var streamCodec = new StreamCodec<RegistryFriendlyByteBuf, LivingEntity>() {
            @Override
            public LivingEntity decode(RegistryFriendlyByteBuf buf) {
                int entityId = buf.readVarInt();
                var level = Minecraft.getInstance().level;
                if (level == null) return null;
                var entity = level.getEntity(entityId);
                return entity instanceof LivingEntity living ? living : null;
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, LivingEntity entity) {
                buf.writeVarInt(entity.getId());
            }
        };

        AccessorRegistries.registerAccessor(
                CustomDirectAccessor.builder(LivingEntity.class, true)
                        .codec(UUIDUtil.CODEC.xmap(
                                uuid -> {
                                    var server = ServerLifecycleHooks.getCurrentServer();
                                    if (server == null) return null;
                                    for (var level : server.getAllLevels()) {
                                        var entity = level.getEntity(uuid);
                                        if (entity instanceof LivingEntity living) {
                                            return living;
                                        }
                                    }
                                    return null;
                                },
                                entity -> entity.getUUID()
                        ))
                        .streamCodec(streamCodec)
                        .customMark(
                                LivingEntity::getUUID,
                                (uuid, entity) -> entity != null && uuid.equals(entity.getUUID())
                        )
                        .build(),
                100
        );
    }

    private static final String TAG_CLASS = "_pgchar_class";

    /**
     * 老存档里<b>改过名的物品 id</b> → 现在的 id。
     *
     * <p>物品改注册名（例如 {@code diebian} → {@code beyond_the_chrysalis}）之后，老存档里那份 NBT
     * 还是旧 id，{@code ItemStack.CODEC} 一解析就抛
     * {@code Unknown registry key in ResourceKey[minecraft:root / minecraft:item]: minegenshin:diebian}
     * —— 整份角色数据都读不出来，玩家会卡在「无效的玩家数据 / Couldn't place player in world」。
     * 所以在<b>反序列化之前</b>先把 tag 里的旧 id 改写成新 id。
     *
     * <p>⚠️ 以后凡是改物品注册名，都要往这里补一条。
     */
    private static final Map<String, String> LEGACY_ITEM_IDS = Map.of(
            "minegenshin:diebian", "minegenshin:beyond_the_chrysalis");

    /**
     * 递归把 tag 里所有 {@code "id": 旧物品 id} 改写成新 id。
     *
     * <p>只匹配 {@link #LEGACY_ITEM_IDS} 里登记过的值，其它字符串（包括别的 {@code id} 字段）一律不动，
     * 所以不会误伤。
     */
    private static void migrateLegacyItemIds(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            String id = compound.contains("id") ? compound.getString("id") : null;
            if (id != null) {
                String replacement = LEGACY_ITEM_IDS.get(id);
                if (replacement != null) {
                    compound.putString("id", replacement);
                }
            }
            for (String key : new ArrayList<>(compound.getAllKeys())) {
                Tag child = compound.get(key);
                if (child != null) {
                    migrateLegacyItemIds(child);
                }
            }
        } else if (tag instanceof ListTag list) {
            for (Tag child : list) {
                migrateLegacyItemIds(child);
            }
        }
    }

    /**
     * Deserialize a PGCharacter from NBT, creating the correct subclass instance.
     */
    private static PGCharacter deserializeFromTag(CompoundTag tag, HolderLookup.Provider access) {
        migrateLegacyItemIds(tag);
        var className = tag.getString(TAG_CLASS);
        PGCharacter c;
        if (!className.isEmpty()) {
            try {
                var clazz = Class.forName(className);
                c = (PGCharacter) clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                c = new PGCharacter();
            }
        } else {
            c = new PGCharacter();
        }
        c.deserializeNBT(access, tag);
        return c;
    }

    /**
     * Serialize a PGCharacter to NBT, recording the actual subclass name.
     */
    private static CompoundTag serializeToTag(PGCharacter c, HolderLookup.Provider access) {
        var tag = c.serializeNBT(access);
        tag.putString(TAG_CLASS, c.getClass().getName());
        return tag;
    }

    private static void registerPGCharacterAccessor() {
        var streamCodec = new StreamCodec<RegistryFriendlyByteBuf, PGCharacter>() {
            @Override
            public PGCharacter decode(RegistryFriendlyByteBuf buf) {
                var tag = buf.readNbt();
                if (tag == null || tag.isEmpty()) return null;
                try {
                    return deserializeFromTag(tag, buf.registryAccess());
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, PGCharacter c) {
                if (c == null) {
                    buf.writeNbt(null);
                    return;
                }
                try {
                    buf.writeNbt(serializeToTag(c, buf.registryAccess()));
                } catch (Exception e) {
                    e.printStackTrace();
                    buf.writeNbt(null);
                }
            }
        };

        AccessorRegistries.registerAccessor(
                CustomDirectAccessor.builder(PGCharacter.class, true)
                        .codec(CompoundTag.CODEC.xmap(
                                tag -> {
                                    if (tag.isEmpty()) return null;
                                    try {
                                        return deserializeFromTag(tag, getRegistryAccess());
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        return null;
                                    }
                                },
                                c -> {
                                    if (c == null) return new CompoundTag();
                                    try {
                                        return serializeToTag(c, getRegistryAccess());
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        return new CompoundTag();
                                    }
                                }
                        ))
                        .streamCodec(streamCodec)
                        .codecMark()
                        .build(),
                100
        );
    }
}