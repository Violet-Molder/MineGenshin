package com.linweiyun.genshin.api.damage;

/**
 * 一条伤害飘字的数据契约：公共侧只负责填这个对象，客户端决定怎么画。
 *
 * @param originX 起点（攻击方）坐标
 * @param targetX 落点（受击方）坐标
 * @param mergeKey 合并键：同一次连击里保持不变，0 表示不参与合并
 * @param merge    是否是「并入已有飘字」的更新包
 */
public record DamageIndicatorData(
        double originX, double originY, double originZ,
        double targetX, double targetY, double targetZ,
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
) {}
