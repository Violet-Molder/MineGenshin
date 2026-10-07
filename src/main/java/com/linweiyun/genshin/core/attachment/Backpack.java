package com.linweiyun.genshin.core.attachment;

import com.linweiyun.genshin.content.items.TeyvatItem;
import com.linweiyun.genshin.content.items.artifact.ArtifactItem;
import com.linweiyun.genshin.content.items.development.CharacterDevelopmentItem;
import com.linweiyun.genshin.content.items.food.FoodItem;
import com.linweiyun.genshin.content.items.furnishing.FurnishingItem;
import com.linweiyun.genshin.content.items.gadget.GadgetItem;
import com.linweiyun.genshin.content.items.material.MaterialItem;
import com.linweiyun.genshin.content.items.precious.PreciousItem;
import com.linweiyun.genshin.content.items.quest.QuestItem;
import com.linweiyun.genshin.content.items.weapon.WeaponItem;
import com.linweiyun.genshin.client.render.gui.menu.BackpackUI;
import com.lowdragmc.lowdraglib2.gui.factory.IContainerUIHolder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;

public class Backpack implements IPersistedSerializable, Container, IContainerUIHolder {
    public static final Codec<Backpack> CODEC = PersistedParser.createCodec(Backpack::new);
    public static final StreamCodec<ByteBuf, Backpack> STREAM_CODEC = PersistedParser.createStreamCodec(Backpack::new);
    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CORE);

    public enum Category {
        WEAPONS("weapons", 100, WeaponItem.class),
        ARTIFACTS("artifacts", 100, ArtifactItem.class),
        DEVELOPMENT("development", 100, CharacterDevelopmentItem.class),
        FOOD("food", 100, FoodItem.class),
        MATERIALS("materials", 100, MaterialItem.class),
        GADGET("gadgets", 100, GadgetItem.class),
        QUEST("quests", 100, QuestItem.class),
        PRECIOUS("precious", 100, PreciousItem.class),
        FURNISHINGS("furnishings", 100, FurnishingItem.class);
        public final String displayName;
        public final int maxCapacity;
        public final Class<? extends TeyvatItem> itemClass;

        Category(String displayName, int maxCapacity, Class<? extends TeyvatItem> itemClass) {
            this.displayName = displayName;
            this.maxCapacity = maxCapacity;
            this.itemClass = itemClass;
        }
    }
    @Persisted(key = "weapons")
    private final ArrayList<ItemStack> weapons = new ArrayList<>(Collections.nCopies(Category.WEAPONS.maxCapacity, ItemStack.EMPTY));
    @Persisted(key = "artifacts")
    private final ArrayList<ItemStack> artifacts = new ArrayList<>(Collections.nCopies(Category.ARTIFACTS.maxCapacity, ItemStack.EMPTY));
    @Persisted(key = "development")
    private final ArrayList<ItemStack> development = new ArrayList<>(Collections.nCopies(Category.DEVELOPMENT.maxCapacity, ItemStack.EMPTY));
    @Persisted(key = "food")
    private final ArrayList<ItemStack> food = new ArrayList<>(Collections.nCopies(Category.FOOD.maxCapacity, ItemStack.EMPTY));
    @Persisted(key = "materials")
    private final ArrayList<ItemStack> materials = new ArrayList<>(Collections.nCopies(Category.MATERIALS.maxCapacity, ItemStack.EMPTY));
    @Persisted(key = "gadgets")
    private final ArrayList<ItemStack> gadgets = new ArrayList<>(Collections.nCopies(Category.GADGET.maxCapacity, ItemStack.EMPTY));
    @Persisted(key = "quests")
    private final ArrayList<ItemStack> quests = new ArrayList<>(Collections.nCopies(Category.QUEST.maxCapacity, ItemStack.EMPTY));
    @Persisted(key = "precious")
    private final ArrayList<ItemStack> precious = new ArrayList<>(Collections.nCopies(Category.PRECIOUS.maxCapacity, ItemStack.EMPTY));
    @Persisted(key = "furnishings")
    private final ArrayList<ItemStack> furnishings = new ArrayList<>(Collections.nCopies(Category.FURNISHINGS.maxCapacity, ItemStack.EMPTY));

    private final boolean[] dirtyFlags = new boolean[Category.ARTIFACTS.maxCapacity];
    private Runnable onChange = () -> {};
    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }
    public IItemHandlerModifiable asResourceHandler() {
        return new InvWrapper(this);
    }

    // ==================== slot 路由辅助方法 ====================

    /**
     * 根据全局 slot 索引找到对应的分类列表
     */
    private ArrayList<ItemStack> getCategoryList(int slot) {
        int offset = 0;
        for (var category : Category.values()) {
            if (slot < offset + category.maxCapacity) {
                return switch (category) {
                    case WEAPONS -> weapons;
                    case ARTIFACTS -> artifacts;
                    case DEVELOPMENT -> development;
                    case FOOD -> food;
                    case MATERIALS -> materials;
                    case GADGET -> gadgets;
                    case QUEST -> quests;
                    case PRECIOUS -> precious;
                    case FURNISHINGS -> furnishings;
                };
            }
            offset += category.maxCapacity;
        }
        throw new IndexOutOfBoundsException("Slot: " + slot);
    }

    /**
     * 将全局 slot 索引转换为所属分类列表内的本地索引
     */
    private int getLocalIndex(int slot) {
        int offset = 0;
        for (var category : Category.values()) {
            if (slot < offset + category.maxCapacity) {
                return slot - offset;
            }
            offset += category.maxCapacity;
        }
        throw new IndexOutOfBoundsException("Slot: " + slot);
    }

    /**
     * 根据全局 slot 索引找到对应的分类
     */
    private Category getCategory(int slot) {
        int offset = 0;
        for (var category : Category.values()) {
            if (slot < offset + category.maxCapacity) {
                return category;
            }
            offset += category.maxCapacity;
        }
        throw new IndexOutOfBoundsException("Slot: " + slot);
    }

    // ==================== Container 接口实现 ====================

    @Override
    public int getContainerSize() {
        int total = 0;
        for (var category : Category.values()) {
            total += category.maxCapacity;
        }
        return total;
    }

    @Override
    public boolean isEmpty() {
        return weapons.stream().allMatch(ItemStack::isEmpty)
                && artifacts.stream().allMatch(ItemStack::isEmpty)
                && development.stream().allMatch(ItemStack::isEmpty)
                && food.stream().allMatch(ItemStack::isEmpty)
                && materials.stream().allMatch(ItemStack::isEmpty)
                && gadgets.stream().allMatch(ItemStack::isEmpty)
                && quests.stream().allMatch(ItemStack::isEmpty)
                && precious.stream().allMatch(ItemStack::isEmpty)
                && furnishings.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return getCategoryList(slot).get(getLocalIndex(slot));
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        var list = getCategoryList(slot);
        int localIndex = getLocalIndex(slot);
        ItemStack stack = ContainerHelper.removeItem(list, localIndex, amount);
        this.setChanged();
        return stack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        var list = getCategoryList(slot);
        int localIndex = getLocalIndex(slot);
        ItemStack stack = ContainerHelper.takeItem(list, localIndex);
        this.setChanged();
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack itemStack) {
        var list = getCategoryList(slot);
        int localIndex = getLocalIndex(slot);
        itemStack.limitSize(this.getMaxStackSize(itemStack));
        list.set(localIndex, itemStack);
        this.setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (stack.isEmpty()) return true;
        Category category = getCategory(slot);
        return category.itemClass.isInstance(stack.getItem());
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public void setChanged() {
        onChange.run();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        weapons.replaceAll(ignored -> ItemStack.EMPTY);
        artifacts.replaceAll(ignored -> ItemStack.EMPTY);
        development.replaceAll(ignored -> ItemStack.EMPTY);
        food.replaceAll(ignored -> ItemStack.EMPTY);
        materials.replaceAll(ignored -> ItemStack.EMPTY);
        gadgets.replaceAll(ignored -> ItemStack.EMPTY);
        quests.replaceAll(ignored -> ItemStack.EMPTY);
        precious.replaceAll(ignored -> ItemStack.EMPTY);
        furnishings.replaceAll(ignored -> ItemStack.EMPTY);
        this.setChanged();
    }

    // ==================== IContainerUIHolder ====================

    @Override
    public ModularUI createUI(Player player) {
        return BackpackUI.createUI(player, this);
    }

    @Override
    public boolean isStillValid(Player player) {
        return true;
    }

    // ==================== 分类辅助方法（兼容旧 GenshinBackpack API） ====================

    public ArrayList<ItemStack> getCategoryList(Category category) {
        return switch (category) {
            case WEAPONS -> weapons;
            case ARTIFACTS -> artifacts;
            case DEVELOPMENT -> development;
            case FOOD -> food;
            case MATERIALS -> materials;
            case GADGET -> gadgets;
            case QUEST -> quests;
            case PRECIOUS -> precious;
            case FURNISHINGS -> furnishings;
        };
    }

    public void addItemToCategory(Category category, ItemStack stack) {
        ArrayList<ItemStack> list = getCategoryList(category);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).isEmpty()) {
                list.set(i, stack);
                this.setChanged();
                return;
            }
        }
    }

    public ItemStack removeItemFromCategory(Category category, int localIndex) {
        ArrayList<ItemStack> list = getCategoryList(category);
        if (localIndex < 0 || localIndex >= list.size()) return ItemStack.EMPTY;
        ItemStack stack = list.get(localIndex);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        list.set(localIndex, ItemStack.EMPTY);
        this.setChanged();
        return stack;
    }
}