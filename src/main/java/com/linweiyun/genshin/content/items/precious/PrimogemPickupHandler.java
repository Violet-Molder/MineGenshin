package com.linweiyun.genshin.content.items.precious;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.network.NetworkManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.TriState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

@EventBusSubscriber
public class PrimogemPickupHandler {

    @SubscribeEvent
    public static void onPickupPrimogem(ItemEntityPickupEvent.Pre event) {
        ItemEntity itemEntity = event.getItemEntity();
        ItemStack stack = itemEntity.getItem();
        if (!(stack.getItem() instanceof ItemPrimogem)) return;

        Player player = event.getPlayer();
        if (player.level().isClientSide()) return;

        int primogem = player.getData(AttachmentRegistration.PRIMOGEM_ATTACHMENT.get()) + stack.getCount();
        player.setData(AttachmentRegistration.PRIMOGEM_ATTACHMENT.get(), primogem);
        NetworkManager.setPrimogemToPlayer((ServerPlayer) player, primogem);
        player.sendSystemMessage(
                Component.literal("你获得了 " + stack.getCount() + " 个原石，" + "当前原石数量为 " + primogem));

        itemEntity.discard();
        event.setCanPickup(TriState.FALSE);
    }
}
