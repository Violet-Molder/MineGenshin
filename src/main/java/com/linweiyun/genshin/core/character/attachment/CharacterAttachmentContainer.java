package com.linweiyun.genshin.core.character.attachment;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import com.mojang.serialization.MapCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * 角色附件容器 —— 存储所有挂载到某个角色身上的 CharacterAttachment
 * <p>
 * 本类放在 PGCharacterData 中参与全套序列化 / 网络同步。
 * 多态序列化参考 StatusContainer：写入时按 typeId 记录类型标签，
 * 读取时通过 CharacterAttachmentTypes.create(typeId) 重建正确的子类实例。
 * <p>
 * 依赖方向：附件容器不依赖任何具体附件类，具体附件类通过注册表注入。
 */
public class CharacterAttachmentContainer implements IPersistedSerializable {
    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);

    private final Map<String, CharacterAttachment> attachments = new HashMap<>();

    public CharacterAttachmentContainer() {}

    // ========== 存取 ==========

    @SuppressWarnings("unchecked")
    public <T extends CharacterAttachment> T get(String typeId) {
        return (T) attachments.get(typeId);
    }

    public void put(CharacterAttachment attachment) {
        if (attachment == null) return;
        attachments.put(attachment.getTypeId(), attachment);
    }

    public void remove(String typeId) {
        attachments.remove(typeId);
    }

    public boolean has(String typeId) {
        return attachments.containsKey(typeId);
    }

    public Map<String, CharacterAttachment> getAll() {
        return attachments;
    }

    // ========== 多态序列化（NBT 路径） ==========

    @Override
    public void serialize(@NotNull ValueOutput output) {
        CompoundTag root = new CompoundTag();
        HolderLookup.Provider provider = Platform.getFrozenRegistry();
        ListTag list = new ListTag();
        for (CharacterAttachment attachment : attachments.values()) {
            try (var reporter = new ProblemReporter.ScopedCollector(LDLib2.LOGGER)) {
                var elemOut = TagValueOutput.createWithContext(reporter, provider);
                attachment.serialize(elemOut);
                list.add(elemOut.buildResult());
            }
        }
        root.put("list", list);
        output.store(root);
    }

    @Override
    public void deserialize(@NotNull ValueInput input) {
        CompoundTag root = input.read(MapCodec.assumeMapUnsafe(CompoundTag.CODEC))
                .orElse(new CompoundTag());
        HolderLookup.Provider provider = input.lookup();
        attachments.clear();
        if (root.contains("list")) {
            ListTag list = root.getList("list").orElse(new ListTag());
            for (int i = 0; i < list.size(); i++) {
                CompoundTag tag = list.getCompoundOrEmpty(i);
                String typeId = tag.getString("type_id").orElse("");
                CharacterAttachment attachment = CharacterAttachmentTypes.create(typeId);
                if (attachment != null) {
                    try (var reporter = new ProblemReporter.ScopedCollector(LDLib2.LOGGER)) {
                        attachment.deserialize(TagValueInput.create(reporter, provider, tag));
                    }
                    attachments.put(typeId, attachment);
                }
            }
        }
    }

    // ========== 多态序列化（网络路径） ==========

    @Override
    public void writeToBuff(ByteBuf buf) {
        CompoundTag root = new CompoundTag();
        HolderLookup.Provider provider = Platform.getFrozenRegistry();
        ListTag list = new ListTag();
        for (CharacterAttachment attachment : attachments.values()) {
            try (var reporter = new ProblemReporter.ScopedCollector(LDLib2.LOGGER)) {
                var elemOut = TagValueOutput.createWithContext(reporter, provider);
                attachment.serialize(elemOut);
                list.add(elemOut.buildResult());
            }
        }
        root.put("list", list);
        new FriendlyByteBuf(buf).writeNbt(root);
    }

    @Override
    public void readFromBuff(ByteBuf buf) {
        CompoundTag root = new FriendlyByteBuf(buf).readNbt();
        if (root == null) return;
        HolderLookup.Provider provider = Platform.getFrozenRegistry();
        attachments.clear();
        if (root.contains("list")) {
            ListTag list = root.getList("list").orElse(new ListTag());
            for (int i = 0; i < list.size(); i++) {
                CompoundTag tag = list.getCompoundOrEmpty(i);
                String typeId = tag.getString("type_id").orElse("");
                CharacterAttachment attachment = CharacterAttachmentTypes.create(typeId);
                if (attachment != null) {
                    try (var reporter = new ProblemReporter.ScopedCollector(LDLib2.LOGGER)) {
                        attachment.deserialize(TagValueInput.create(reporter, provider, tag));
                    }
                    attachments.put(typeId, attachment);
                }
            }
        }
    }
}