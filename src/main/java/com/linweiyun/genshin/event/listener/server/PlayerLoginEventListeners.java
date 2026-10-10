package com.linweiyun.genshin.event.listener.server;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.sync.CharacterDataSyncEventHandler;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.VersionChecker;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber
public class PlayerLoginEventListeners {
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        CharacterDataSyncEventHandler.handle(event);
        ModList.get().getModContainerById(Minegenshin.MOD_ID).ifPresent(modContainer -> {
            VersionChecker.CheckResult result = VersionChecker.getResult(modContainer.getModInfo());
            if (result.status() == VersionChecker.Status.OUTDATED) {
                String recommendedVersion = result.target().toString();
                Component message = Component.literal(
                        "§e[YourMod] §f发现新版本！§a推荐稳定版: " + recommendedVersion + " §7(当前: " + modContainer.getModInfo().getVersion() + ")"
                );
                event.getEntity().sendSystemMessage(message);
            }
        });
    }
}
