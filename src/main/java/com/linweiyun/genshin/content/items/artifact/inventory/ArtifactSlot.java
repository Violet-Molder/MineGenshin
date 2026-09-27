package com.linweiyun.genshin.content.items.artifact.inventory;

import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

public class ArtifactSlot extends Slot {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CONTENT);

    private final int artifactSlotIndex;

    public ArtifactSlot(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
        this.artifactSlotIndex = slot;
    }

    @Override
    public boolean mayPlace(@NonNull ItemStack stack) {
        return ArtifactInventory.isValidForSlot(artifactSlotIndex, stack);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public void set(@NonNull ItemStack stack) {
        super.set(stack);
    }

    @Override
    public @NonNull ItemStack remove(int amount) {
        return super.remove(amount);
    }
}