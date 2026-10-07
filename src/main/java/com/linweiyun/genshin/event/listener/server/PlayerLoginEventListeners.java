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
            // 2. 获取版本检查结果
            VersionChecker.CheckResult result = VersionChecker.getResult(modContainer.getModInfo());

            // 3. 判断状态，如果不是最新稳定版就发送提示
            if (result.status() == VersionChecker.Status.OUTDATED) {
                // 获取推荐的稳定版本号
                String recommendedVersion = result.target().toString();

                // 构建提示消息
                Component message = Component.literal(
                        "§e[YourMod] §f发现新版本！§a推荐稳定版: " + recommendedVersion + " §7(当前: " + modContainer.getModInfo().getVersion() + ")"
                );

                // 发送消息给玩家
                event.getEntity().sendSystemMessage(message);
            }
        });
    }
}
