package com.linweiyun.genshin.client.combat.state;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.network.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 身体朝向（{@code yBodyRot}）的联机同步。
 *
 * <h2>为什么必须同步</h2>
 * 本模组的「视角独立」是在<b>本地</b>改 {@code yBodyRot} 的
 * （{@code LivingEntityTickHeadTurnMixin} 自己按位移算角度、{@code EntityTurnMixin}
 * 在「视角跟随」下把鼠标增量写给身体）。这两处都只改本机内存：
 * <ul>
 *   <li>原版客户端只把 {@code yRot}/{@code xRot}（视线）发上去，
 *       {@code yBodyRot} 一个字节都不发；</li>
 *   <li>服务端给别的玩家算身体朝向时只能拿视线角去凑（原版 {@code tickHeadTurn} 的第 2 条）。</li>
 * </ul>
 * 于是出现「我这台机器上角色侧着走，别人那边看我始终正对着他」这种分叉 ——
 * 别人看到的是「身体被视线拖着走」的版本，和我自己看到的不是同一套。
 *
 * <h2>怎么做</h2>
 * 每刻把本机身体朝向变了的部分上报（阈值 {@value #SEND_THRESHOLD} 度，避免站着不动也刷包），
 * 服务端写进 {@link AttachmentRegistration#BODY_YAW_ATTACHMENT} 并 {@code syncData}，
 * NeoForge 负责推给所有跟踪这个玩家的客户端。渲染时远端玩家直接读这份值
 * （见 {@code CharacterRenderDispatcher}），不再用原版那份「视线角」。
 *
 * <p>插值在本地做：每刻把「这一刻收到的新值」推成 {@code 当前/上一刻} 两个数，
 * 渲染用 {@link Mth#rotLerp}，所以收到包的那一刻不会看到角度跳变。
 */
@EventBusSubscriber(modid = Minegenshin.MOD_ID, value = Dist.CLIENT)
public final class BodyYawSync {

    /** 上报阈值（度）：小于这个差值的转动不上报，站着不动时一包都不发。 */
    private static final float SEND_THRESHOLD = 0.25F;

    /** 远端玩家 UUID → 上一刻/这一刻的身体朝向（用来插值）。 */
    private static final Map<UUID, Float> PREVIOUS = new HashMap<>();
    private static final Map<UUID, Float> CURRENT = new HashMap<>();

    /** 上一次上报出去的本机身体朝向；{@code NaN} = 还没报过（进世界后第一刻强制报一次）。 */
    private static float lastSentYaw = Float.NaN;

    private BodyYawSync() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            lastSentYaw = Float.NaN;
            PREVIOUS.clear();
            CURRENT.clear();
            return;
        }

        if (minecraft.level != null) {
            for (Player other : minecraft.level.players()) {
                if (other == player) {
                    continue;
                }
                float yaw = receivedYaw(other);
                if (Float.isNaN(yaw)) {
                    continue;
                }
                UUID id = other.getUUID();
                Float previousTick = CURRENT.get(id);
                if (previousTick != null) {
                    PREVIOUS.put(id, previousTick);
                }
                CURRENT.put(id, yaw);
            }

            // 玩家走了就把条目清掉，别让静态表无限长
            if (CURRENT.size() > minecraft.level.players().size() + 4) {
                CURRENT.keySet().removeIf(uuid ->
                        minecraft.level.players().stream().noneMatch(p -> p.getUUID().equals(uuid)));
                PREVIOUS.keySet().removeIf(uuid -> !CURRENT.containsKey(uuid));
            }
        }

        sendLocalYaw(player);
    }

    /** 本机身体朝向变了就上报一次。 */
    private static void sendLocalYaw(LocalPlayer player) {
        float yaw = Mth.wrapDegrees(player.yBodyRot);

        if (!Float.isNaN(lastSentYaw)
                && Math.abs(Mth.wrapDegrees(yaw - lastSentYaw)) < SEND_THRESHOLD) {
            return;
        }

        lastSentYaw = yaw;
        NetworkManager.sendBodyYawToServer(yaw);
    }

    /** 远端玩家同步过来的身体朝向；没收到过就是 {@code NaN}。 */
    private static float receivedYaw(Player player) {
        Float value = player.getData(AttachmentRegistration.BODY_YAW_ATTACHMENT);
        return value == null ? Float.NaN : value;
    }

    /**
     * 渲染这个远端玩家时该用的身体朝向。
     *
     * <p>没收到过同步值时退回原版那套（{@code yBodyRotO → yBodyRot} 插值），
     * 不会更差。
     */
    public static float renderYaw(Player player, float partialTick) {
        UUID id = player.getUUID();
        Float current = CURRENT.get(id);
        if (current == null) {
            return Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot);
        }
        Float previous = PREVIOUS.get(id);
        return previous == null ? current : Mth.rotLerp(partialTick, previous, current);
    }
}
