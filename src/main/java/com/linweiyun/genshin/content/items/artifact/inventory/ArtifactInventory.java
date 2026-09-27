package com.linweiyun.genshin.content.items.artifact.inventory;

import com.linweiyun.genshin.content.items.artifact.ArtifactItem;
import com.linweiyun.genshin.content.items.artifact.type.ArtifactType;
import com.linweiyun.genshin.content.items.component.ArtifactStatsComponent;
import com.linweiyun.genshin.content.items.weapon.WeaponItem;
import com.linweiyun.genshin.core.system.registry.register.ModDataComponents;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

import java.util.List;

/**
 * 圣遗物背包（ArtifactInventory）
 * <p>
 * 该类用于管理角色的圣遗物装备栏，包含5个固定槽位（对应原神中的5种圣遗物类型）。
 * 实现了 Minecraft 原版的 {@link Container} 接口，使其可以作为标准容器与原版系统交互；
 * 同时实现了 {@link IPersistedSerializable} 接口，支持通过 LowDragLib2 的持久化注解进行数据同步和存档。
 * <p>
 * 5个槽位分别为：
 * - 生之花（Flower）
 * - 死之羽（Plume）
 * - 时之沙（Sands）
 * - 空之杯（Goblet）
 * - 理之冠（Circlet）
 */
    public class ArtifactInventory implements Container, IPersistedSerializable {

    /** 日志记录器，用于输出调试信息 */
    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CONTENT);

    /** 装备槽位总数，5个圣遗物 + 1个武器 */
    public static final int SLOT_COUNT = 6;

    // ==================== 槽位索引常量 ====================
    /** 生之花槽位索引 */
    public static final int SLOT_FLOWER = 0;
    /** 死之羽槽位索引 */
    public static final int SLOT_PLUME = 1;
    /** 时之沙槽位索引 */
    public static final int SLOT_SANDS = 2;
    /** 空之杯槽位索引 */
    public static final int SLOT_GOBLET = 3;
    /** 理之冠槽位索引 */
    public static final int SLOT_CIRCLET = 4;
    /** 武器槽位索引 */
    public static final int SLOT_WEAPON = 5;


    // ==================== 持久化字段（会被自动保存到存档） ====================
    /** 生之花（Flower）圣遗物 */
    @Persisted(key = "flower")
    private ItemStack flower = ItemStack.EMPTY;
    /** 死之羽（Plume）圣遗物 */
    @Persisted(key = "plume")
    private ItemStack plume = ItemStack.EMPTY;
    /** 时之沙（Sands）圣遗物 */
    @Persisted(key = "sands")
    private ItemStack sands = ItemStack.EMPTY;
    /** 空之杯（Goblet）圣遗物 */
    @Persisted(key = "goblet")
    private ItemStack goblet = ItemStack.EMPTY;
    /** 理之冠（Circlet）圣遗物 */
    @Persisted(key = "circlet")
    private ItemStack circlet = ItemStack.EMPTY;
    /** 武器（Weapon） */
    @Persisted(key = "weapon")
    private ItemStack weapon = ItemStack.EMPTY;

    // ==================== 运行时状态字段（不会被持久化） ====================
    /**
     * 脏标记数组，用于追踪哪些槽位的数据发生了变化。
     * 常用于客户端-服务端同步或存档保存优化。
     */
    private final boolean[] dirtyFlags = new boolean[slotCount()];

    /** 这个背包用到几个槽位；全武器类角色（林薇云）在子类里覆盖成 11 */
    public int slotCount() {
        return SLOT_COUNT;
    }

    /**
     * 数据变化回调函数，当容器内容发生变化时触发。
     * 默认是一个空操作，可通过 {@link #setOnChange(Runnable)} 设置自定义回调。
     */
    private Runnable onChange = () -> {};

    /** 默认构造方法，创建一个空的圣遗物背包 */
    public ArtifactInventory() {}

    /**
     * 设置数据变化时的回调函数
     *
     * @param onChange 回调接口，当容器内容变化时执行
     */
    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /**
     * 将当前容器包装为 NeoForge 的资源处理器（ResourceHandler）
     * <p>
     * 这使得圣遗物背包可以与 NeoForge 的传输系统（如管道、自动化设备）交互，
     * 同时也被 LowDragLib2 的 UI 系统用于绑定槽位显示。
     *
     * @return 包装后的 ItemResource 处理器
     */
    public ResourceHandler<ItemResource> asResourceHandler() {
        return VanillaContainerWrapper.of(this);
    }

    // ==================== 类型与槽位转换工具方法 ====================

    /**
     * 将圣遗物类型转换为对应的槽位索引
     *
     * @param type 圣遗物类型枚举
     * @return 对应的槽位索引（0-4）
     */
    public static int typeToSlot(ArtifactType type) {
        return switch (type) {
            case FLOWER -> SLOT_FLOWER;
            case PLUME -> SLOT_PLUME;
            case SANDS -> SLOT_SANDS;
            case GOBLET -> SLOT_GOBLET;
            case CIRCLET -> SLOT_CIRCLET;
        };
    }

    /**
     * 将槽位索引转换为对应的圣遗物类型
     *
     * @param slot 槽位索引（0-4）
     * @return 对应的圣遗物类型枚举
     * @throws IndexOutOfBoundsException 如果槽位索引不合法
     */
    public static ArtifactType slotToType(int slot) {
        return switch (slot) {
            case SLOT_FLOWER -> ArtifactType.FLOWER;
            case SLOT_PLUME -> ArtifactType.PLUME;
            case SLOT_SANDS -> ArtifactType.SANDS;
            case SLOT_GOBLET -> ArtifactType.GOBLET;
            case SLOT_CIRCLET -> ArtifactType.CIRCLET;
            default -> null;
        };
    }

    /**
     * 检查指定物品是否可以放入指定槽位
     *
     * 规则：
     * - 空物品堆（ItemStack.EMPTY）始终允许放入（表示清空槽位）
     * - 非圣遗物物品不允许放入
     * - 未激活的圣遗物不允许放入
     * - 圣遗物类型必须与槽位类型匹配
     *
     * @param slot  目标槽位索引
     * @param stack 要放入的物品堆
     * @return 如果允许放入则返回 true，否则返回 false
     */
    public static boolean isValidForSlot(int slot, ItemStack stack) {
        if (stack.isEmpty()) return true;
        // 5 及以上都是武器槽：全武器类角色一个武器种类一格（见 AllWeaponArtifactInventory）
        if (slot >= SLOT_WEAPON) {
            return stack.getItem() instanceof WeaponItem;
        }
        if (!(stack.getItem() instanceof ArtifactItem artifact)) return false;

        ArtifactStatsComponent stats = stack.getOrDefault(
                ModDataComponents.ARTIFACT_STATS.get(),
                ArtifactStatsComponent.DEFAULT);
        if (!stats.activated) return false;

        ArtifactType expected = slotToType(slot);
        return artifact.getType() == expected;
    }

    // ==================== 脏标记管理 ====================

    /**
     * 标记指定槽位为"脏"（已修改）状态
     *
     * @param slot 槽位索引
     */
    public void markDirty(int slot) {
        if (slot >= 0 && slot < slotCount()) {
            dirtyFlags[slot] = true;
        }
    }

    /**
     * 检查指定槽位是否为脏状态
     *
     * @param slot 槽位索引
     * @return 如果该槽位被标记为脏则返回 true
     */
    public boolean isDirty(int slot) {
        return slot >= 0 && slot < slotCount() && dirtyFlags[slot];
    }

    /**
     * 清除指定槽位的脏标记
     *
     * @param slot 槽位索引
     */
    public void clearDirty(int slot) {
        if (slot >= 0 && slot < slotCount()) {
            dirtyFlags[slot] = false;
        }
    }

    /**
     * 检查是否有任何槽位处于脏状态
     *
     * @return 如果至少有一个槽位被标记为脏则返回 true
     */
    public boolean hasDirtySlots() {
        for (int i = 0; i < slotCount(); i++) {
            if (dirtyFlags[i]) return true;
        }
        return false;
    }

    // ==================== 私有辅助方法：槽位与字段映射 ====================

    /**
     * 根据槽位索引获取对应的物品堆（子类覆盖它就能多出几格，见 {@code AllWeaponArtifactInventory}）
     *
     * @param slot 槽位索引
     * @return 对应槽位的物品堆
     * @throws IllegalStateException 如果槽位索引不合法
     */
    protected ItemStack getStackBySlot(int slot) {
        return switch (slot) {
            case SLOT_FLOWER -> flower;
            case SLOT_PLUME -> plume;
            case SLOT_SANDS -> sands;
            case SLOT_GOBLET -> goblet;
            case SLOT_CIRCLET -> circlet;
            case SLOT_WEAPON -> weapon;
            default -> throw new IllegalStateException("Unexpected value: " + slot);
        };
    }

    /**
     * 根据槽位索引设置对应的物品堆（私有辅助方法）
     *
     * @param slot  槽位索引
     * @param stack 要设置的物品堆
     */
    protected void setStackBySlot(int slot, ItemStack stack) {
        switch (slot) {
            case SLOT_FLOWER -> flower = stack;
            case SLOT_PLUME -> plume = stack;
            case SLOT_SANDS -> sands = stack;
            case SLOT_GOBLET -> goblet = stack;
            case SLOT_CIRCLET -> circlet = stack;
            case SLOT_WEAPON -> weapon = stack;
        }
    }

    // ==================== Container 接口实现 ====================

    /**
     * 获取容器大小（槽位数量）
     *
     * @return 固定返回 5
     */
    @Override
    public int getContainerSize() {
        return slotCount();
    }

    /**
     * 检查容器是否为空（所有槽位都没有物品）
     *
     * @return 如果所有槽位都为空则返回 true
     */
    @Override
    public boolean isEmpty() {
        return flower.isEmpty() && plume.isEmpty() && sands.isEmpty() && goblet.isEmpty() && circlet.isEmpty() && weapon.isEmpty();
    }

    /**
     * 获取指定槽位的物品
     *
     * @param slot 槽位索引
     * @return 该槽位的物品堆（不会返回 null，空槽位返回 ItemStack.EMPTY）
     */
    @Override
    public @NonNull ItemStack getItem(int slot) {
        return getStackBySlot(slot);
    }

    /**
     * 从指定槽位移除指定数量的物品
     * <p>
     * 如果移除数量大于等于槽位现有数量，则清空该槽位并标记为脏；
     * 否则只减少对应数量。
     *
     * @param slot   槽位索引
     * @param amount 要移除的数量
     * @return 实际移除的物品堆
     */
    @Override
    public @NonNull ItemStack removeItem(int slot, int amount) {
        ItemStack existing = getStackBySlot(slot);
        if (existing.isEmpty()) return ItemStack.EMPTY;
        int toRemove = Math.min(amount, existing.getCount());
        ItemStack result = existing.copyWithCount(toRemove);
        if (toRemove >= existing.getCount()) {
            setStackBySlot(slot, ItemStack.EMPTY);
            markDirty(slot);
        } else {
            existing.shrink(toRemove);
        }
        setChanged();
        LOGGER.info("ARTIFACTremoveItem");
        return result;
    }

    /**
     * 从指定槽位移除所有物品（不触发更新）
     * <p>
     * 与 removeItem 不同，此方法直接清空槽位并返回原有物品，
     * 不会触发容器的 setChanged 回调。适用于需要批量操作或避免递归更新的场景。
     *
     * @param slot 槽位索引
     * @return 被移除的物品堆
     */
    @Override
    public @NonNull ItemStack removeItemNoUpdate(int slot) {
        ItemStack existing = getStackBySlot(slot);
        if (existing.isEmpty()) return ItemStack.EMPTY;
        setStackBySlot(slot, ItemStack.EMPTY);
        markDirty(slot);
        return existing;
    }

    /**
     * 设置指定槽位的物品
     * <p>
     * 在设置前会进行类型校验，只有符合槽位类型的圣遗物才能放入。
     * 设置成功后会标记槽位为脏并触发变化回调。
     *
     * @param slot  槽位索引
     * @param stack 要设置的物品堆
     */
    @Override
    public void setItem(int slot, ItemStack stack) {
        if (!stack.isEmpty() && !isValidForSlot(slot, stack)) return;
        setStackBySlot(slot, stack);
        markDirty(slot);
        setChanged();
        LOGGER.info("ARTIFACTsetItem");
    }

    /**
     * 检查指定物品是否可以放入指定槽位
     *
     * @param slot  槽位索引
     * @param stack 要检查的物品堆
     * @return 如果允许放入则返回 true
     */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return isValidForSlot(slot, stack);
    }

    /**
     * 获取每个槽位的最大堆叠数量
     * <p>
     * 圣遗物每个槽位只能放1个，因此返回 1。
     *
     * @return 固定返回 1
     */
    @Override
    public int getMaxStackSize() {
        return 1;
    }

    /**
     * 标记容器状态已改变，触发变化回调
     * <p>
     * 当容器内容发生变化时应调用此方法，以通知监听者进行相应处理
     *（如数据同步、属性重计算等）。
     */
    @Override
    public void setChanged() {
        onChange.run();
    }

    /**
     * 检查容器对于指定玩家是否仍然有效
     * <p>
     * 当前实现始终返回 true，表示没有额外的距离或条件限制。
     *
     * @param player 要检查的玩家
     * @return 始终返回 true
     */
    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /**
     * 清空容器中的所有物品
     * <p>
     * 将所有5个槽位重置为空，标记所有槽位为脏，并触发变化回调。
     */
    @Override
    public void clearContent() {
        flower = ItemStack.EMPTY;
        plume = ItemStack.EMPTY;
        sands = ItemStack.EMPTY;
        goblet = ItemStack.EMPTY;
        circlet = ItemStack.EMPTY;
        for (int i = 0; i < slotCount(); i++) {
            markDirty(i);
        }
        setChanged();
        LOGGER.info("ARTIFACTclearContent");
    }

    // ==================== 自定义业务方法 ====================

    /**
     * 获取所有圣遗物作为列表
     * <p>
     * 返回一个包含5个槽位物品的不可变列表，顺序为：
     * Flower, Plume, Sands, Goblet, Circlet
     *
     * @return 包含所有圣遗物的 List
     */
    public List<ItemStack> getAllArtifactsAsList() {
        return List.of(flower, plume, sands, goblet, circlet);
    }

    /**
     * 创建当前圣遗物背包的深拷贝
     * <p>
     * 拷贝后的新实例与原实例数据完全独立，修改互不影响。
     * 常用于需要保存快照或传递副本的场景。
     *
     * @return 新的 ArtifactInventory 实例，包含当前所有物品的拷贝
     */
    public ArtifactInventory copy() {
        ArtifactInventory inv = new ArtifactInventory();
        inv.flower = this.flower.copy();
        inv.plume = this.plume.copy();
        inv.sands = this.sands.copy();
        inv.goblet = this.goblet.copy();
        inv.circlet = this.circlet.copy();
        return inv;
    }
}
