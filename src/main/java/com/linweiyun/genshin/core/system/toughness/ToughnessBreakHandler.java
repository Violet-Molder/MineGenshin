package com.linweiyun.genshin.core.system.toughness;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 韧性归零的破坏：按钻石镐等价判定掉落。
 *
 * <p>26.2 没有 {@code BlockEvent.BreakEvent}，用可取消的 {@link BlockDropsEvent} 代替；
 * 事件被取消 = 这次不改方块。
 */
public final class ToughnessBreakHandler {

    private ToughnessBreakHandler() {
    }

    public static void breakBlock(ServerLevel level, BlockPos pos, Player player) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getBlock() instanceof LiquidBlock) {
            return;
        }
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        boolean drops = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        List<ItemEntity> loot = new ArrayList<>();
        if (drops) {
            for (ItemStack stack : Block.getDrops(state, level, pos, blockEntity, player, tool)) {
                loot.add(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack));
            }
        }
        BlockDropsEvent event = new BlockDropsEvent(level, pos, state, blockEntity, loot, player, tool);
        if (NeoForge.EVENT_BUS.post(event).isCanceled()) {
            return;
        }
        if (!level.removeBlock(pos, false)) {
            return;
        }
        level.destroyBlockProgress(player.getId(), pos, -1);
        for (ItemEntity item : event.getDrops()) {
            level.addFreshEntity(item);
        }
        level.levelEvent(2001, pos, Block.getId(state));
    }
}
