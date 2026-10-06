package com.linweiyun.genshin.core.sync;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.linweiyun.genshin.core.character.ModCharacters;
import com.linweiyun.genshin.core.world.TeyvatWorldInvasion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class CharacterDataSyncEventHandler {
    public static void handle(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {

            boolean invaded = false;
            if (serverPlayer.level() instanceof ServerLevel serverLevel) {
                invaded = TeyvatWorldInvasion.get(serverLevel).isInvaded();
            }
            NetworkManager.setInvasionStatusToPlayer(serverPlayer, invaded);

            if (!invaded) return;

            int primogem = serverPlayer.getData(AttachmentRegistration.PRIMOGEM_ATTACHMENT);
            NetworkManager.setPrimogemToPlayer(serverPlayer, primogem);

            boolean isGenshinMode = serverPlayer.getData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT);
            NetworkManager.setGenshinModeToPlayer(serverPlayer, isGenshinMode);

            var adventurerInfo = serverPlayer.getData(AttachmentRegistration.ADVENTURER_INFO_ATTACHMENT);
            adventurerInfo.syncToPlayer(serverPlayer);

            PlayerCharactersAttachment playerData = serverPlayer.getData(
                    AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            playerData.fixCharacterTypes();
            playerData.bindAllOwners(serverPlayer);

            PGCharacter shenhe = ModCharacters.getByUUID(135001);
            if (shenhe != null && !playerData.hasCharacter(135001)) {
                playerData.addCharacterToPlayer(serverPlayer, shenhe);
                playerData.setPartyCharacterToPlayer(serverPlayer, 0, 135001);
            }

            playerData.syncToPlayer(serverPlayer);
        }
    }
}