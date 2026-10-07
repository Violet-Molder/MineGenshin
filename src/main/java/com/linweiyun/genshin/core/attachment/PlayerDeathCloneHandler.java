package com.linweiyun.genshin.core.attachment;

import com.linweiyun.genshin.core.attachment.AdventurerInfoAttachment;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.elementlib.core.attachment.StatusContainer;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.linweiyun.genshin.core.world.TeyvatWorldInvasion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import com.linweiyun.elementlib.core.attachment.ElementalAttachments;

@EventBusSubscriber
public class PlayerDeathCloneHandler {

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level() instanceof ServerLevel sl && !TeyvatWorldInvasion.get(sl).isInvaded()) return;

        var original = event.getOriginal();
        var entity = event.getEntity();

        // 简单 immutable 类型 — 只能 setData
        entity.setData(AttachmentRegistration.PRIMOGEM_ATTACHMENT,
                original.getData(AttachmentRegistration.PRIMOGEM_ATTACHMENT));
        entity.setData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT,
                original.getData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT));

        // 冒险者信息 — 复制持久化数据
        AdventurerInfoAttachment dstInfo = entity.getData(AttachmentRegistration.ADVENTURER_INFO_ATTACHMENT);
        AdventurerInfoAttachment srcInfo = original.getData(AttachmentRegistration.ADVENTURER_INFO_ATTACHMENT);
        dstInfo.setAdventureRank(srcInfo.getAdventureRank());
        dstInfo.setWorldLevel(srcInfo.getWorldLevel());
        dstInfo.setBreakthroughLevel(srcInfo.getBreakthroughLevel());
        dstInfo.setCurrentExp(srcInfo.getCurrentExp());

        // 复杂对象
        PlayerCharactersAttachment dstChar = entity.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        PlayerCharactersAttachment srcChar = original.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        dstChar.getOwnedCharacters().clear();
        dstChar.getOwnedCharacters().addAll(srcChar.getOwnedCharacters());
        dstChar.getSheetCharacterUUIDs().clear();
        dstChar.getSheetCharacterUUIDs().addAll(srcChar.getSheetCharacterUUIDs());
        dstChar.getPartyCharacterUUIDs().clear();
        dstChar.getPartyCharacterUUIDs().addAll(srcChar.getPartyCharacterUUIDs());
        // 静默恢复索引：死亡重生不是一次切人，不广播切换事件
        dstChar.restoreCurrentIndex(srcChar.getCurrentCharacterIndex());

        StatusContainer dstStatus = ElementalAttachments.container(entity);
        StatusContainer srcStatus = ElementalAttachments.container(original);
        dstStatus.clear();
        for (var inst : srcStatus.getAll()) {
            dstStatus.add(inst.copy());
        }
        // === 同步到客户端 ===
        NetworkManager.setPrimogemToPlayer(player, player.getData(AttachmentRegistration.PRIMOGEM_ATTACHMENT));
        NetworkManager.setGenshinModeToPlayer(player, player.getData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT));
        dstInfo.syncToPlayer(player);
        dstChar.syncToPlayer(player);
        dstChar.fixCharacterTypes();
    }
}
