package com.linweiyun.genshin.core.system.performance;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 世界文字颜色的解析缓存（计算优化模块）。
 *
 * <h2>解决什么</h2>
 * 飘字工厂每次生成飘字都要按元素/反应去配置里取一次颜色字符串，
 * 再 {@code Integer.decode} 解析成 ARGB。这条路径在每次伤害结算里会走 1~3 遍，
 * 高频攻击下就是「同一串十六进制被反复解析」。
 *
 * <p>这里做两级缓存：</p>
 * <ol>
 *   <li><b>槽位记忆</b>：按 {@link ModConfigSpec.ConfigValue} 记住「上一次的原始字符串 → 解析结果」。
 *       配置没改时命中一次引用/字符串相等就比较完，不再解析；配置改了字符串自然不同，
 *       下一次调用就会重新解析，<b>不需要监听配置重载事件</b>。</li>
 *   <li><b>字符串表</b>：十六进制串 → ARGB 的小表，给「多个槽位填同一个颜色」的情况兜底。</li>
 * </ol>
 *
 * <p>两个表都无界风险极低（受配置项数量约束），仍设了上限以防配置被反复热改。</p>
 */
public final class DamageTextColorCache {

    /** 解析结果兜底值：解析失败时用的白色 */
    public static final int FALLBACK = 0xFFFFFF;

    private static final int MAX_PARSE_ENTRIES = 128;

    private static final Map<ModConfigSpec.ConfigValue<String>, Slot> SLOTS = new IdentityHashMap<>();
    private static final Map<String, Integer> PARSED = new HashMap<>();

    private DamageTextColorCache() {}

    private static final class Slot {
        String lastRaw;
        int lastValue = FALLBACK;
    }

    /**
     * 取配置项对应的颜色。同一配置项在值不变时不再解析字符串。
     */
    public static int colorOf(ModConfigSpec.ConfigValue<String> configValue) {
        if (configValue == null) {
            return FALLBACK;
        }
        String raw;
        try {
            raw = configValue.get();
        } catch (Throwable ignored) {
            // 配置尚未加载（例如数据生成阶段）时不要让飘字把整个伤害流程带崩
            return FALLBACK;
        }
        if (raw == null) {
            return FALLBACK;
        }

        Slot slot = SLOTS.computeIfAbsent(configValue, key -> new Slot());
        if (raw.equals(slot.lastRaw)) {
            return slot.lastValue;
        }

        int parsed = parseHex(raw);
        slot.lastRaw = raw;
        slot.lastValue = parsed;
        return parsed;
    }

    /**
     * 解析 {@code #RRGGBB} / {@code RRGGBB} / {@code 0xRRGGBB} 形式的颜色字符串，结果进小表缓存。
     */
    public static int parseHex(String hex) {
        if (hex == null || hex.isEmpty()) {
            return FALLBACK;
        }

        Integer cached = PARSED.get(hex);
        if (cached != null) {
            return cached;
        }

        int parsed;
        try {
            parsed = Integer.decode(hex.startsWith("#") ? hex : "#" + hex) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            parsed = FALLBACK;
        }

        if (PARSED.size() >= MAX_PARSE_ENTRIES) {
            PARSED.clear();
        }
        PARSED.put(hex, parsed);
        return parsed;
    }

    /** 配置被批量热改、或需要强制重解析时调用。 */
    public static void clear() {
        SLOTS.clear();
        PARSED.clear();
    }
}
