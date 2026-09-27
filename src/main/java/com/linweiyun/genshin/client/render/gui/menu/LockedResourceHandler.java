package com.linweiyun.genshin.client.render.gui.menu;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * 只读的资源处理器包装器。
 *
 * 用于在背包界面里展示“已装备”的圣遗物：
 * 读操作透传给底层 handler，所有写入 / 提取一律拒绝，
 * 从而禁止玩家在背包界面里存取已装备的圣遗物。
 */
public class LockedResourceHandler implements ResourceHandler<ItemResource> {

    private final ResourceHandler<ItemResource> delegate;

    public LockedResourceHandler(ResourceHandler<ItemResource> delegate) {
        this.delegate = delegate;
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public ItemResource getResource(int index) {
        return delegate.getResource(index);
    }

    @Override
    public long getAmountAsLong(int index) {
        return delegate.getAmountAsLong(index);
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        // 容量返回 0，任何物品都无法放入
        return 0L;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        // 一律视为不合法，禁止任何资源进入
        return false;
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        // 返回 0，表示一点都没插入
        return 0;
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        // 返回 0，表示一点都没提取
        return 0;
    }
}