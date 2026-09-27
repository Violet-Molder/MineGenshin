// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.client.render.gui.screen;

import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import com.linweiyun.genshin.core.menu.CharacterInfoMenu;
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

   public void init() {
      super.init();
      this.titleLabelX = -9999;
      this.titleLabelY = -9999;
      this.inventoryLabelX = -9999;
      this.inventoryLabelY = -9999;
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      for (int i = 0; i < ((CharacterInfoMenu)this.menu).getArtifactInventory().slotCount(); i++) {
         Slot var4 = ((CharacterInfoMenu)this.menu).getSlot(i);
      }

      return super.mouseClicked(event, doubleClick);
   }
}
