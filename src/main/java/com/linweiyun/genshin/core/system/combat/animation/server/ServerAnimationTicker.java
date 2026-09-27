package com.linweiyun.genshin.core.system.combat.animation.server;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.attachment.AnimationState;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.system.combat.targeting.CombatTargeting;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端动画到期复位（兜底）+ 远端同步。
 *
 * <p>移植自参考2 的 {@code ServerAnimationTicker}。远端玩家的动作动画靠
 * {@link AttachmentRegistration#ANIMATION_STATE_ATTACHMENT} 同步，正常由客户端播完发复位包。
 * 万一客户端崩了或丢包，这里用倒计时强制把状态复位，
 * 避免那个玩家在别人眼里永远定格在出招姿势。
 *
 * <p>倒计时放在本类的静态内存表里而非附件本身：它是纯瞬态的服务端计数，
 * 不该被存盘、复制或同步给客户端。
 *
 * <p>客户端 → 服务端的状态由 {@code NetworkManager.animationStateRPCPacket} 落到
 * {@link #apply(ServerPlayer, String, int)}。
 */
@EventBusSubscriber(modid = Minegenshin.MOD_ID)
public final class ServerAnimationTicker {

    /** 玩家 UUID → 当前动作剩余刻数。只在服务端主线程读写。 */
    private static final Map<UUID, Integer> REMAINING_TICKS = new HashMap<>();

    private ServerAnimationTicker() {
    }

    /**
     * 客户端上报了一次状态切换：写入同步附件并登记倒计时。
     *
     * @param totalTicks 动画总刻数；{@code <= 0} 表示这次是复位包
     */
    public static void apply(ServerPlayer player, String stateName, int totalTicks) {
        AnimationState state = new AnimationState(stateName, totalTicks);

        player.setData(AttachmentRegistration.ANIMATION_STATE_ATTACHMENT, state);
        // 推给所有能收到这个玩家的客户端（含玩家自己）
        player.syncData(AttachmentRegistration.ANIMATION_STATE_ATTACHMENT);

        if (totalTicks <= 0) {
            REMAINING_TICKS.remove(player.getUUID());
        } else {
            REMAINING_TICKS.put(player.getUUID(), totalTicks);
        }
    }

    /** 取消某个玩家的倒计时。 */
    public static void clear(ServerPlayer player) {
        REMAINING_TICKS.remove(player.getUUID());
    }

    /**
     * 客户端上报了本机身体朝向：写进同步附件并推给所有跟踪者。
     *
     * <p>不在这里做任何限流 —— 客户端已经按「变化超过阈值」才发（见 {@code BodyYawSync}），
     * 服务端收到就照发，多一道记账只会让两边的值更容易不一致。
     */
    public static void applyBodyYaw(ServerPlayer player, float yaw) {
        if (!Float.isFinite(yaw)) {
            return;
        }

        player.setData(AttachmentRegistration.BODY_YAW_ATTACHMENT, yaw);
        player.syncData(AttachmentRegistration.BODY_YAW_ATTACHMENT);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        Integer remaining = REMAINING_TICKS.get(player.getUUID());

        if (remaining == null) {
            return;
        }

        if (remaining > 1) {
            REMAINING_TICKS.put(player.getUUID(), remaining - 1);
            return;
        }

        // 到期：复位动画状态并重新同步给跟踪者
        REMAINING_TICKS.remove(player.getUUID());

        AnimationState current = player.getData(AttachmentRegistration.ANIMATION_STATE_ATTACHMENT);

        if (current == null || current.isDefault()) {
            // 客户端已经自己复位过了，什么都不用做
            return;
        }

        player.setData(AttachmentRegistration.ANIMATION_STATE_ATTACHMENT, AnimationState.DEFAULT);
        player.syncData(AttachmentRegistration.ANIMATION_STATE_ATTACHMENT);
    }

    /** 玩家下线时清掉他的计数，避免内存表里留下再也用不到的条目。 */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clear(player);
            // 服务端那份索敌锁同样按玩家 uuid 存在静态表里，玩家走了就得清
            CombatTargeting.onPlayerRemoved(player);
        }
    }
}
