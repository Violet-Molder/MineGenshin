package com.linweiyun.genshin.client.damage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 活跃飘字列表（客户端）。
 *
 * <p>除了「新增 / 过期回收」，它还负责<b>合并</b>：服务端在判断出「这一串伤害该并成一条」时，
 * 会带着同一个 {@code mergeKey} 再发一次，这里就把它改写到已有的那条上，
 * 于是活跃条数不再是「攻击次数」，而是「目标数」。</p>
 */
public final class DamageIndicatorManager {

    private static final List<DamageIndicator> ACTIVE = new ArrayList<>();
    /** 合并键 → 那条飘字；只放 mergeKey != 0 的 */
    private static final Map<Integer, DamageIndicator> BY_MERGE_KEY = new HashMap<>();

    /** 活跃条数硬上限：兜底防止异常刷屏把列表撑爆 */
    private static final int MAX_INDICATORS = 256;

    private DamageIndicatorManager() {}

    public static void add(DamageIndicator indicator) {
        ACTIVE.add(indicator);
        if (indicator.mergeKey != 0) {
            BY_MERGE_KEY.put(indicator.mergeKey, indicator);
        }
        while (ACTIVE.size() > MAX_INDICATORS) {
            removeAt(0);
        }
    }

    /**
     * 服务端飘字入口：需要合并且能找到上一条时改写它，否则新增一条。
     */
    public static void upsert(DamageIndicator incoming, boolean merge) {
        if (merge && incoming.mergeKey != 0) {
            DamageIndicator existing = BY_MERGE_KEY.get(incoming.mergeKey);
            if (existing != null && !existing.isExpired()) {
                existing.applyMerge(incoming.target, incoming.text,
                        incoming.topColor, incoming.bottomColor,
                        incoming.baseScale, incoming.startScale, incoming.lifetimeMs);
                return;
            }
            BY_MERGE_KEY.remove(incoming.mergeKey);
        }
        add(incoming);
    }

    /** 由客户端 Tick 事件驱动，移除过期飘字 */
    public static void tick() {
        if (ACTIVE.isEmpty()) {
            return;
        }
        ACTIVE.removeIf(DamageIndicator::isExpired);
        BY_MERGE_KEY.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    public static List<DamageIndicator> getActive() {
        return ACTIVE;
    }

    /** 当前活跃条数（调试用） */
    public static int activeCount() {
        return ACTIVE.size();
    }

    public static void clear() {
        ACTIVE.clear();
        BY_MERGE_KEY.clear();
    }

    private static void removeAt(int index) {
        DamageIndicator removed = ACTIVE.remove(index);
        if (removed.mergeKey != 0 && BY_MERGE_KEY.get(removed.mergeKey) == removed) {
            BY_MERGE_KEY.remove(removed.mergeKey);
        }
    }
}
