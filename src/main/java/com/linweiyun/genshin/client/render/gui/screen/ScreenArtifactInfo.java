package com.linweiyun.genshin.client.render.gui.screen;

import com.linweiyun.genshin.core.menu.CharacterInfoMenu;
import com.linweiyun.genshin.content.items.artifact.inventory.ArtifactInventory;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.slf4j.Logger;

public class ScreenArtifactInfo extends AbstractContainerScreen<CharacterInfoMenu> {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);

    public ScreenArtifactInfo(CharacterInfoMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, Component.empty());
    }

    @Override
    public void init() {
        super.init();
        // 隐藏标题
        this.titleLabelX = -9999;
        this.titleLabelY = -9999;
        this.inventoryLabelX = -9999;
        this.inventoryLabelY = -9999;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (int i = 0; i < ArtifactInventory.SLOT_COUNT; i++) {
            Slot slot = this.menu.getSlot(i);
        }
        return super.mouseClicked(event, doubleClick);
    }
}