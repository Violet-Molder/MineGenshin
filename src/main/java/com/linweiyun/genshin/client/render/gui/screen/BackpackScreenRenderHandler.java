package com.linweiyun.genshin.client.render.gui.screen;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.world.TeyvatWorldInvasion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(value = Dist.CLIENT)
public class BackpackScreenRenderHandler {

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        int screenWidth = event.getScreen().width;
        int screenHeight = event.getScreen().height;

        if (event.getScreen() instanceof InventoryScreen) {
            renderPrimogemOnInventory(
                    event.getGuiGraphics(), Minecraft.getInstance().player, false, screenWidth, screenHeight);
        } else if (event.getScreen() instanceof CreativeModeInventoryScreen creativeScreen) {
            if (creativeScreen.isInventoryOpen()) {
                renderPrimogemOnInventory(
                        event.getGuiGraphics(),
                        Minecraft.getInstance().player,
                        true,
                        screenWidth,
                        screenHeight);
            }
        }
    }

    private static void renderPrimogemOnInventory(
            GuiGraphics guiGraphics,
            Player player,
            boolean isCreativeInventory,
            int screenWidth,
            int screenHeight) {
        if (player == null) return;
        if (!TeyvatWorldInvasion.isClientInvaded()) return;
        int primogemCount = player.getData(AttachmentRegistration.PRIMOGEM_ATTACHMENT);
        int x;
        int y;
        if (isCreativeInventory) {
            x = screenWidth / 2 + 30;
            y = screenHeight / 2 - 35;
        } else {
            x = screenWidth / 2 + 40;
            y = screenHeight / 2 - 20;
        }

        String text = ":";
        String count = String.valueOf(primogemCount);
        Font font = Minecraft.getInstance().font;
        guiGraphics.blit(Minegenshin.id("item/primogem/textures/icon.png"), x, y, 0, 0, 16, 16);
        guiGraphics.drawString(font, text, x + 18, y + 5, 0xFF000000, false);
        guiGraphics.drawString(font, count, x + 21, y + 6, 0xFF000000, false);
    }
}
