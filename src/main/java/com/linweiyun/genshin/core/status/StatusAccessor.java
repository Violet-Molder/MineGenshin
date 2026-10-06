package com.linweiyun.genshin.core.status;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.elementlib.core.attachment.StatusContainer;
import com.linweiyun.genshin.core.character.PGCharacterData;
import com.linweiyun.genshin.core.system.registry.register.ModStatusDataComponents;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;
import com.linweiyun.elementlib.core.attachment.ElementalAttachments;

/**
 * 宿主访问器 —— 四种现有载体 → StatusContainer（写入统一走 ElementalHost 入口）
 *
 * LivingEntity / BlockEntity → NeoForge Attachment
 * PGCharacterData           → @Persisted 字段
 * ItemStack                 → DataComponent
 * BlockPos（方块）           → Chunk 数据里的 StatusContainer（返回只读快照）
 */
public class StatusAccessor {
    public static final Logger LOGGER = ModLog.getLogger(LogGroup.CORE);

    public static StatusContainer of(LivingEntity entity) {
        return ElementalAttachments.container(entity);
    }

    public static StatusContainer of(BlockEntity blockEntity) {
        return blockEntity.getData(ElementalAttachments.CONTAINER);
    }

    public static StatusContainer of(PGCharacterData data) {
        return data.getStatusContainer();
    }

    public static StatusContainer of(ItemStack stack) {
        StatusContainer c = stack.get(ModStatusDataComponents.CONTAINER);
        return c != null ? c : StatusContainer.EMPTY;
    }

    /**
     * 方块身上的容器（只读快照）。
     *
     * <p>方块容器现在是一等公民（见 {@code BlockElementStore}），但外部读取仍然给
     * <b>拷贝</b>：调用方拿到快照，不能顺着它改到 Chunk 数据里去。
     * 需要写入请走宿主入口 {@code ElementalAttachmentHelper.attach(BlockHost, ...)}。
     */
    public static StatusContainer of(ServerLevel level, BlockPos pos) {
        // 走宿主层的只读入口（不建容器、不补自附着、不落盘）—— 见 element-host.md 的读写纪律
        StatusContainer stored = com.linweiyun.elementlib.core.system.about.host.BlockHost.of(level, pos).peekContainer();
        return stored == null ? StatusContainer.EMPTY : stored.copy();
    }
}
