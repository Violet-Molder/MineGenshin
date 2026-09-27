package com.linweiyun.genshin.core.system.combat.attack;

import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「这个玩家现在<b>正处于下落攻击状态</b>」—— 双端各存一份的轻量状态。
 *
 * <h2>这个状态是干什么的</h2>
 * 下落攻击不是「一段固定时长的动作」，而是一个<b>从按下到落地</b>的持续状态：
 * 期间加速下坠、锁住一切输入（切人 / 攻击 / 移动 / 技能 / 切原神模式 / 开界面），
 * 落地那一刻才结算伤害。所以它记的是「什么时候开始」，结束由落地那一刻决定。
 *
 * <h2>为什么键里带 C: / S:</h2>
 * 单机里客户端和集成服务端跑在<b>同一个 JVM</b>、同一份类静态字段里，
 * 只按 UUID 存会让两边的状态互相串（客户端的按下被服务端读成「已经在攻击」）。
 * {@code ActionManager} 用的是同一套前缀，这里照抄。
 *
 * <h2>「刚落地」那一格为什么也要记</h2>
 * 落地摔伤（{@code Player#causeFallDamage}）和结束状态（{@code CharacterTickHandler}）
 * 在同一刻发生，但<b>谁先谁后不保证</b>（摔伤走移动包处理、状态走 PlayerTickEvent）。
 * 谁先跑都得按「下落攻击那条免伤曲线」算，所以结束的那一刻也留一格记录，
 * 让摔伤那边能认出「这一摔是带着下落攻击落地的」。
 */
public final class PlungeState {

    /**
     * 下落攻击的安全上限（刻）—— 10 秒。
     *
     * <p>正常的下落攻击几刻到几十刻就落地了，这个值只是「万一没落地」的兜底
     * （蛛网里下坠速度被打没、客户端卡住、包丢了…），到点就当作打空结束，
     * 免得状态卡住之后所有输入永远被拦。两端用同一个值。
     */
    public static final int MAX_TICKS = 200;

    /** 正在下落攻击的玩家 → 开始那一刻的刻数。 */
    private static final Map<String, Entry> ACTIVE = new ConcurrentHashMap<>();

    /** 刚刚落地（结束那一格）的玩家 → 结束时的刻数。只用来让摔伤认这一格。 */
    private static final Map<String, Long> JUST_LANDED = new ConcurrentHashMap<>();

    private PlungeState() {
    }

    /**
     * 开始下落攻击（已经在状态里就刷新，不重复记）。
     *
     * @param characterUuid 起手时出战角色的 uuid —— 换人之后那一个<b>不继承</b>这次下落攻击
     *                      （比如摔伤把前一个角色打倒了，下坠还没落地）
     */
    public static void begin(Player player, int characterUuid) {
        if (player == null) {
            return;
        }
        String key = key(player);
        ACTIVE.put(key, new Entry(player.level().getGameTime(), characterUuid));
        JUST_LANDED.remove(key);
    }

    /** 起手时出战的是哪个角色（不在状态里返回 {@link Integer#MIN_VALUE}）。 */
    public static int characterUuid(@Nullable Player player) {
        if (player == null) {
            return Integer.MIN_VALUE;
        }
        Entry entry = ACTIVE.get(key(player));
        return entry == null ? Integer.MIN_VALUE : entry.characterUuid();
    }

    /** 结束下落攻击（落地 / 取消），并留下「刚从下落攻击落地」那一格的记录。 */
    public static void end(Player player) {
        if (player == null) {
            return;
        }
        String key = key(player);
        if (ACTIVE.remove(key) != null) {
            long now = player.level().getGameTime();
            JUST_LANDED.put(key, now);
            // 过期的「刚落地」记录没有意义，顺手扫掉（每边最多几个玩家，代价可以忽略）
            JUST_LANDED.entrySet().removeIf(e -> e.getValue() < now);
        }
    }

    /** 正在下落攻击中。 */
    public static boolean isPlunging(@Nullable Player player) {
        return player != null && ACTIVE.containsKey(key(player));
    }

    /** 这一次下落攻击已经持续了多少刻（不在状态里就是 0）。 */
    public static int elapsedTicks(@Nullable Player player) {
        if (player == null) {
            return 0;
        }
        Entry entry = ACTIVE.get(key(player));
        return entry == null ? 0 : (int) (player.level().getGameTime() - entry.startTick());
    }

    /**
     * 正在下落攻击，或者<b>这一格刚落地</b>。
     *
     * <p>摔落伤害按不按下落攻击算，看的是这个：结束刻与落地刻是同一格，两边谁先跑都对。
     */
    public static boolean isPlungingOrJustLanded(@Nullable Player player) {
        if (player == null) {
            return false;
        }
        String key = key(player);
        if (ACTIVE.containsKey(key)) {
            return true;
        }
        Long landedTick = JUST_LANDED.get(key);
        return landedTick != null && landedTick == player.level().getGameTime();
    }

    /** 彻底清掉（下线上线、换档这种场合）。 */
    public static void clear(@Nullable Player player) {
        if (player == null) {
            return;
        }
        String key = key(player);
        ACTIVE.remove(key);
        JUST_LANDED.remove(key);
    }

    private static String key(Player player) {
        return (player.level().isClientSide() ? "C:" : "S:") + player.getUUID();
    }

    /** 一次下落攻击的起手快照。 */
    private record Entry(long startTick, int characterUuid) {
    }
}
