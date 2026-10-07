package com.linweiyun.genshin.content.items;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.linweiyun.genshin.core.character.ModCharacters;
import com.linweiyun.genshin.core.world.TeyvatWorldInvasion;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class DarkFragment extends Item {

    public DarkFragment(Properties properties) {
        super(properties.rarity(Rarity.EPIC).fireResistant());
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack heldStack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.success(heldStack);
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResultHolder.success(heldStack);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.success(heldStack);

        TeyvatWorldInvasion invasion = TeyvatWorldInvasion.get(serverLevel);
        if (invasion.isInvaded()) {
            serverPlayer.sendSystemMessage(Component.translatable("message.minegenshin.world_already_invaded"));
            return InteractionResultHolder.fail(heldStack);
        }

        invasion.setInvaded(true);

        for (ServerPlayer p : serverLevel.getServer().getPlayerList().getPlayers()) {
            NetworkManager.setInvasionStatusToPlayer(p, true);
        }

        PlayerCharactersAttachment playerData = serverPlayer.getData(
                AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        PGCharacter shenhe = ModCharacters.getByUUID(135001);
        if (shenhe != null && !playerData.hasCharacter(135001)) {
            playerData.addCharacterToPlayer(serverPlayer, shenhe);
            playerData.setPartyCharacterToPlayer(serverPlayer, 0, 135001);
            playerData.syncToPlayer(serverPlayer);
        }

        heldStack.shrink(1);

        serverPlayer.sendSystemMessage(Component.translatable("message.minegenshin.world_invasion_activated"));
        serverLevel.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.minegenshin.world_invasion_broadcast"), false);

        return InteractionResultHolder.consume(heldStack);
    }
}