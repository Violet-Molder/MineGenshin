package com.linweiyun.genshin.core.network;

import com.linweiyun.genshin.api.damage.DamageIndicatorData;
import com.linweiyun.genshin.api.damage.DamageIndicatorSink;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import com.lowdragmc.lowdraglib2.networking.both.PacketRPCPacket;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacket;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketHandler;
import com.lowdragmc.lowdraglib2.syncdata.rpc.RPCSender;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

public final class DamageIndicatorRpc {
    public static final Logger LOGGER = ModLog.getLogger(LogGroup.CORE);

    /** RPC 注册名，发送侧要先靠它拿到编解码器 */
    private static final String PACKET_ID = "genshin:damage_indicator";

    private DamageIndicatorRpc() {}

    @RPCPacket(PACKET_ID)
    public static void damageIndicatorRPCPacket(
            RPCSender sender,
            double targetX, double targetY, double targetZ,
            double originX, double originY, double originZ,
            String text,
            int topColor,
            int bottomColor,
            byte style,
            boolean italic,
            float baseScale,
            float startScale,
            int durationMs,
            int mergeKey,
            boolean merge
    ) {
        if (sender.isServer()) {
            DamageIndicatorSink.show(new DamageIndicatorData(
                    originX, originY, originZ,
                    targetX, targetY, targetZ,
                    text,
                    topColor,
                    bottomColor,
                    style,
                    italic,
                    baseScale,
                    startScale,
                    durationMs,
                    mergeKey,
                    merge
            ));
        }
    }

    public static void sendToPlayer(
            ServerPlayer player,
            double targetX, double targetY, double targetZ,
            double originX, double originY, double originZ,
            String text,
            int topColor,
            int bottomColor,
            byte style,
            boolean italic,
            float baseScale,
            float startScale,
            int durationMs,
            int mergeKey,
            boolean merge
    ) {
        RPCPacketDistributor.rpcToPlayer(
                player,
                PACKET_ID,
                targetX, targetY, targetZ,
                originX, originY, originZ,
                text,
                topColor, bottomColor,
                style,
                italic,
                baseScale, startScale,
                durationMs,
                mergeKey, merge
        );
    }

    /**
     * 按半径广播一条飘字 —— <b>参数编码一次，发给半径内的所有玩家</b>。
     *
     * <p>{@link RPCPacketDistributor#rpcToPlayer} 是「对每个玩家各调一次」的接口，
     * 也就是同一条飘字有几名玩家在附近就要重复编码几遍（每次还多一组装箱参数的 varargs 数组
     * 与一个包对象）。高攻速下这是服务端最贵的一段：伤害结算本身是几十纳秒，
     * 编 5 遍包就是几微秒。
     * 这里改成先编码成 {@code byte[]}、组一个 {@link PacketRPCPacket}，
     * 再交给 NeoForge 的 {@code sendToPlayersNear} 广播：距离筛选与逐玩家发送交给
     * {@code PlayerList}，我们只付一次编码成本。</p>
     *
     * <p>参数顺序与 {@link #sendToPlayer} 完全一致，另外多了「以谁为圆心、半径多大」三个值。
     * 编解码器拿不到（理论上不该发生）时退回逐玩家发送，功能不受影响。</p>
     */
    public static void sendToNearby(
            ServerLevel level,
            double centerX, double centerY, double centerZ, double radius,
            double targetX, double targetY, double targetZ,
            double originX, double originY, double originZ,
            String text,
            int topColor,
            int bottomColor,
            byte style,
            boolean italic,
            float baseScale,
            float startScale,
            int durationMs,
            int mergeKey,
            boolean merge
    ) {
        byte[] data = encode(targetX, targetY, targetZ, originX, originY, originZ,
                text, topColor, bottomColor, style, italic,
                baseScale, startScale, durationMs, mergeKey, merge);

        if (data == null) {
            // 编解码器不可用：退回逐玩家发送
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(centerX, centerY, centerZ) <= radius * radius) {
                    sendToPlayer(player,
                            targetX, targetY, targetZ,
                            originX, originY, originZ,
                            text, topColor, bottomColor, style, italic,
                            baseScale, startScale, durationMs, mergeKey, merge);
                }
            }
            return;
        }

        PacketDistributor.sendToPlayersNear(level, null, centerX, centerY, centerZ, radius,
                PacketRPCPacket.of(PACKET_ID, data));
    }

    /**
     * 把一个飘字包编码成字节。
     *
     * @return 编码结果；编解码器不可用时返回 {@code null}
     */
    private static byte[] encode(
            double targetX, double targetY, double targetZ,
            double originX, double originY, double originZ,
            String text,
            int topColor,
            int bottomColor,
            byte style,
            boolean italic,
            float baseScale,
            float startScale,
            int durationMs,
            int mergeKey,
            boolean merge
    ) {
        try {
            RPCPacketHandler handler = RPCPacketDistributor.getSafePacketHandler(PACKET_ID);
            byte[] data = handler.args2Bytes(
                    targetX, targetY, targetZ,
                    originX, originY, originZ,
                    text,
                    topColor, bottomColor,
                    style,
                    italic,
                    baseScale, startScale,
                    durationMs,
                    mergeKey, merge);
            // EMPTY 处理器会回一个空数组 —— 那说明这个包没注册上，别当成合法载荷发出去
            return data == null || data.length == 0 ? null : data;
        } catch (Throwable t) {
            LOGGER.error("[DI-Rpc] 编码飘字包失败，退回逐玩家发送", t);
            return null;
        }
    }

    /**
     * 方块位置反应飘字 —— 目标位置和出现位置设为同一坐标（方块中心）。
     * 复用现有 RPC 通道。
     */
    public static void sendReactionAtPos(
            ServerPlayer player,
            String text,
            Vec3 pos,
            int topColor,
            int bottomColor,
            byte style,
            boolean italic,
            float baseScale,
            float startScale,
            int durationMs
    ) {
        RPCPacketDistributor.rpcToPlayer(
                player,
                PACKET_ID,
                pos.x, pos.y, pos.z,
                pos.x, pos.y, pos.z,
                text,
                topColor, bottomColor,
                style,
                italic,
                baseScale, startScale,
                durationMs,
                0, false
        );
    }
}
