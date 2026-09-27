package com.linweiyun.genshin.core.character;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.CharacterHelper;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.linweiyun.genshin.core.system.combat.targeting.CombatTargeting;
import com.linweiyun.genshin.core.world.TeyvatWorldInvasion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber
public class CharacterTickHandler {
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();

        // 顺序敏感：四步必须按此顺序执行，因此留在同一个监听方法里依次调用，
        // 而不是拆成多个 @SubscribeEvent —— 跨类监听的执行顺序不确定。
        tickTargetingLock(player);
        if (isNotInvadedOnServer(player)) return;
        PlayerCharactersAttachment attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        tickPartyCharacters(player, attachment);
        checkAllCharactersDown(player, attachment);
        syncDirtyCharacters(player, attachment);
    }

    /**
     * 索敌锁：服务端每刻校验。
     *
     * <p>客户端那份由客户端状态机驱动；服务端这份是「攻击请求包喂进来」的，
     * 没有人校验它就会**永远不松**——于是动作自带的位移从此不再执行（两端行为分叉）。
     */
    private static void tickTargetingLock(Player player) {
        if (!player.level().isClientSide()) {
            CombatTargeting.tick(player);
        }
    }

    /** 服务端且未入侵 → 跳过后续全部角色推进。 */
    private static boolean isNotInvadedOnServer(Player player) {
        return !player.level().isClientSide() && player.level() instanceof ServerLevel sl
                && !TeyvatWorldInvasion.get(sl).isInvaded();
    }

    /** 推进队伍里每个角色的状态。 */
    private static void tickPartyCharacters(Player player, PlayerCharactersAttachment attachment) {
        if (!player.level().isClientSide()) {
            for (int uuid : attachment.getPartyCharacterUUIDs()) {
                PGCharacter character = CharacterHelper.getCharacterByUUID(player, uuid);
                if (character != null) {
                    character.tick(player);
                }
            }
        }
    }

    /** 全队倒下时关闭原神模式并同步给客户端。 */
    private static void checkAllCharactersDown(Player player, PlayerCharactersAttachment attachment) {
        if (!player.level().isClientSide()) {
            Boolean genshinMode = player.getData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT);
            if (Boolean.TRUE.equals(genshinMode)) {
                boolean allDead = true;
                for (int i = 0; i < 4; i++) {
                    PGCharacter pc = attachment.getPartyCharacter(i);
                    if (pc != null && pc.getData().getCurrentHP() > 0) {
                        allDead = false;
                        break;
                    }
                }
                if (allDead) {
                    player.setData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT, false);
                    if (player instanceof ServerPlayer sp) {
                        NetworkManager.setGenshinModeToPlayer(sp, false);
                    }
                    // 全灭被动退出：这里也要把角色属性折算回玩家身上
                    com.linweiyun.genshin.core.system.compat.PlayerStatBridge
                            .onGenshinModeChanged(player, false);
                }
            }
        }
    }

    /** 脏标记驱动的角色数据整包同步。 */
    private static void syncDirtyCharacters(Player player, PlayerCharactersAttachment attachment) {
        for (PGCharacter character : attachment.getOwnedCharacters()) {
            if (character.getData().isDirty()) {
                character.getData().clearDirty();
                if (player instanceof ServerPlayer serverPlayer) {
                    attachment.syncSingleCharacterToPlayer(serverPlayer, character);
                }

            }
        }
    }
}
