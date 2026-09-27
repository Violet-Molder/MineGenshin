package com.linweiyun.genshin.core.system.performance;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 有上限的 LRU 表（计算优化模块）。
 *
 * <h2>它解决什么</h2>
 * 战斗里有很多「按 UUID 记一下上次触发是第几刻」的小表，写法基本都是
 * {@code private static final Map<UUID, Long> X = new HashMap<>();} 然后只 put 不 remove。
 * 这种表的键是<b>实体 UUID</b>，而实体是会不断产生的（刷怪、玩家进出、跨维度），
 * 于是静态表只涨不落 —— 是内存泄漏，也是「长时间游戏后卡顿」的常见来源。
 *
 * <h2>为什么用 LRU 而不是 TTL</h2>
 * 这些表存的都是「最近一次触发时刻」，按访问顺序淘汰最久没用到的键，
 * 淘汰效果等价于那条冷却记录自然过期：最坏情况是某个早已不再被打的目标
 * 少了一条冷却记录，多触发一次反应 —— 与冷却到期的结果没有区别。
 *
 * <p>只适用于<b>单线程访问</b>的表（服务端逻辑、客户端渲染各自一份）。</p>
 */
public final class BoundedLruMap {

    /** 默认上限：够放「当前场景所有相关实体 + 最近打过的一批」 */
    public static final int DEFAULT_MAX_ENTRIES = 4096;

    private BoundedLruMap() {}

    /**
     * 建一张带访问顺序的有上限表：get / put 都会把键挪到「最近使用」一端，
     * 超出上限时淘汰另一端（最久没被碰过的键）。
     *
     * @param maxEntries 上限；小于 1 时按 1 处理
     */
    public static <K, V> Map<K, V> create(int maxEntries) {
        int limit = Math.max(1, maxEntries);
        return new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > limit;
            }
        };
    }

    public static <K, V> Map<K, V> create() {
        return create(DEFAULT_MAX_ENTRIES);
    }
}
