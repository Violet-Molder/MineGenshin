package com.linweiyun.genshin.client.render.gui.menu;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * 只读的物品槽包装器。
 *
 * 用于在背包界面里展示“已装备”的圣遗物：
 * 读操作透传给底层 handler，所有写入 / 提取一律拒绝，
 * 从而禁止玩家在背包界面里存取已装备的圣遗物。
 */
public class LockedResourceHandler implements IItemHandlerModifiable {

    private final IItemHandlerModifiable delegate;

    public LockedResourceHandler(IItemHandlerModifiable delegate) {
        this.delegate = delegate;
    }

    @Override
    public int getSlots() {
        return delegate.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return delegate.getStackInSlot(slot);
    }

    @Override
    public int getSlotLimit(int slot) {
        // 容量返回 0，任何物品都无法放入
        return 0;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        // 一律视为不合法，禁止任何物品进入
        return false;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        // 原样退回，表示一点都没插入
        return stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        // 返回空，表示一点都没提取
        return ItemStack.EMPTY;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        // 只读：忽略写入
    }
}
