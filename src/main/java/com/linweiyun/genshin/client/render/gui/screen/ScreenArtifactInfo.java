package com.linweiyun.genshin.client.render.gui.screen;

import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import com.linweiyun.genshin.menu.CharacterInfoMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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

   /** 槽位全部摆在屏幕外，底板也一并留空。 */
   @Override
   protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      for (int i = 0; i < ((CharacterInfoMenu)this.menu).getArtifactInventory().slotCount(); i++) {
         Slot var4 = ((CharacterInfoMenu)this.menu).getSlot(i);
      }

      return super.mouseClicked(mouseX, mouseY, button);
   }
}
