package com.linweiyun.genshin.core.system.performance;

import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;

/**
 * 「每个游戏刻最多算一次」的快照（计算优化模块）。
 *
 * <h2>它解决什么</h2>
 * 有些查询是<b>全维度级别</b>的（例如「队伍里有没有某个角色」要遍历所有玩家的队伍），
 * 却挂在<b>每实体每刻</b>的判定里。30 只带元素附着的怪、10 个玩家，
 * 就是每刻 30 次 × 40 次 instanceof —— 一秒 24000 次，而队伍构成一秒都不会变一次。
 *
 * <p>把这类查询按「维度 + 游戏刻」缓存一格：同一刻第二次问就是一次 map 查表。
 * 一刻的延迟是可接受的：队伍换人在下一 tick 生效。</p>
 *
 * <h2>形态</h2>
 * 显式两步（{@link #getOrNull} → 自己算 → {@link #put}），不接 lambda，
 * 免掉每次调用一个捕获变量的 lambda 分配。
 *
 * <p>实例的方法都加锁：服务端各维度是并行 tick 的，同一次查询可能来自不同线程。</p>
 */
public final class TickSnapshot {

    /** 兜底：正常服务器不会超过这个维度数 */
    private static final int MAX_LEVELS = 16;

    private final Map<ServerLevel, Snapshot> snapshots = new HashMap<>();

    private static final class Snapshot {
        long tick;
        boolean value;
    }

    /**
     * 取该维度在该游戏刻的已算结果。
     *
     * @return 已算过就是结论；没算过（或已经换刻）返回 {@code null}，调用方算完请 {@link #put}
     */
    public synchronized Boolean getOrNull(ServerLevel level, long tick) {
        Snapshot snapshot = snapshots.get(level);
        if (snapshot == null || snapshot.tick != tick) {
            return null;
        }
        return snapshot.value;
    }

    /** 记下该维度该游戏刻的结论。 */
    public synchronized void put(ServerLevel level, long tick, boolean value) {
        if (snapshots.size() >= MAX_LEVELS && !snapshots.containsKey(level)) {
            snapshots.clear();
        }
        Snapshot snapshot = snapshots.get(level);
        if (snapshot == null) {
            snapshot = new Snapshot();
            snapshots.put(level, snapshot);
        }
        snapshot.tick = tick;
        snapshot.value = value;
    }

    /** 服务器停机 / 换世界时清空（否则会一直持有 ServerLevel 引用）。 */
    public synchronized void clear() {
        snapshots.clear();
    }
}
