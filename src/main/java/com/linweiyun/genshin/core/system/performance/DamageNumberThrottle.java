package com.linweiyun.genshin.core.system.performance;

import com.linweiyun.genshin.config.PerformanceConfig;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 伤害飘字生成侧的合并 / 节流（计算优化模块）。
 *
 * <h2>它解决什么</h2>
 * 攻速堆高以后，同一个目标每 tick 都能刷出好几条飘字：每条都要过一次网络广播、
 * 客户端再各自维护一条动画。真正卡住的不是画那几笔画，而是<b>条数</b>。
 * 这里在生成侧就把「同一目标的连续伤害」并成<b>一条会累加的数字</b>，
 * 让客户端的活跃飘字数从「随攻击次数线性增长」变成「每目标常驻 1 条」。
 *
 * <h2>判定规则（只在密集攻击下才会生效）</h2>
 * <ul>
 *   <li><b>快速连击</b>：同一目标、同一配色，两条之间的间隔 ≤ {@code merge_gap_ms}（默认 140ms）
 *       → 并入同一个桶，数字累加；</li>
 *   <li><b>洪水兜底</b>：同一目标在 {@code flood_window_ms}（默认 500ms）内已经飘出
 *       {@code flood_count}（默认 6）条以上 → 之后即便间隔变大也继续并入，防止「打一下飘一条」堆屏；</li>
 *   <li>间隔一旦超过 {@code merge_gap_ms} 且不再洪水，桶结束，下一条重新单独起一条。</li>
 * </ul>
 *
 * <p><b>合并的桶按「签名」区分</b>：签名 = 样式 + 顶色 + 底色 + 斜体 + 文字形状。
 * 数字（全为数字的文字）用统一的形状标记，所以「100」和「250」会并成「350」；
 * 而「月感电」这类固定文案只和自己完全相同的文案合并，不会把两种反应并到一条里。</p>
 *
 * <h2>调用约定（重要）</h2>
 * 返回的 {@link Plan} 是<b>每线程复用</b>的一只对象（结论字段每次调用就地改写）。
 * 调用方必须「拿到就读」，不要把返回值存进字段、放进集合、或者跨调用持有——
 * 它下一次调用就被覆盖了。这样每次伤害不会再多出一次小对象分配。
 *
 * <p>纯表现层逻辑：<b>不参与任何伤害结算</b>，关掉 {@code damage-number.merge} 即回到逐条飘字。</p>
 */
public final class DamageNumberThrottle {

    /**
     * 一次生成请求的结论。字段可变，实例按线程复用（见类注释的调用约定）。
     */
    public static final class Plan {
        /** true = 并入已有飘字（客户端应更新那条），false = 新起一条 */
        public boolean merge;
        /** 合并键：同一次连击里保持不变，客户端按它找到那条飘字；0 = 不参与合并 */
        public int mergeKey;
        /** 数字类飘字合并后的累计值；非数字类是 {@link Float#NaN} */
        public float total;
        /** 本次最终要显示的文字（数字类已格式化为累计值），恒非 null */
        public String text = "";

        private Plan() {}
    }

    /** 单个目标的最近生成记录条数（洪水判定用） */
    private static final int RECENT_SIZE = 8;
    /** 目标状态保留时长：超过这么久没飘过字就丢掉 */
    private static final long STATE_TTL_MS = 8_000L;
    /** 目标状态上限，超出时做一次过期清理 */
    private static final int MAX_STATES = 512;

    private static final Map<UUID, TargetState> STATES = new HashMap<>();

    /** 每线程一只结论对象，避免每次伤害都新建（服务端多维度是多线程 tick 的） */
    private static final ThreadLocal<Plan> PLAN = ThreadLocal.withInitial(Plan::new);

    private static int nextMergeKey = 1;

    // 统计（给调试/验证用）
    private static long spawnRequests;
    private static long mergedRequests;

    private DamageNumberThrottle() {}

    private static final class TargetState {
        final long[] recentMs = new long[RECENT_SIZE];
        int recentIndex;
        int recentFilled;

        long lastSpawnMs;
        int bucketKey;
        int bucketSignature;
        boolean bucketNumeric;
        float bucketTotal;
    }

    /**
     * 规划一次「文字类」飘字（伤害数字文本、反应名、自定义文案都走这里）。
     *
     * @param target     飘字挂靠的目标（桶按目标区分）
     * @param text       本次要显示的文字；纯数字的会被当作伤害数字参与累加
     * @param style      飘字样式序号
     * @param topColor   顶部颜色
     * @param bottomColor 底部颜色
     * @param italic     是否斜体
     * @return 生成结论（每线程复用，拿到就读，不要持有）
     */
    public static synchronized Plan plan(LivingEntity target, String text, byte style,
                                         int topColor, int bottomColor, boolean italic) {
        boolean numeric = text != null && isNumeric(text);
        float delta = numeric ? parseNumber(text) : Float.NaN;
        return resolve(target, text, delta, style, topColor, bottomColor, italic);
    }

    /**
     * 规划一次「数值类」飘字。
     *
     * <p>比 {@link #plan} 少一趟「数值 → 字符串 → 再解析回数值」：
     * 伤害结算本来就是算出了 float 伤害，早先的写法先 {@code String.valueOf(Math.round(v))}
     * 交给这里，这里再 {@code Float.parseFloat} 回来累加，等于每次伤害白跑一遍
     * 数字格式化 + 数字解析。</p>
     *
     * @param value 伤害数值（非有限值按 0 处理）
     * @return 生成结论（每线程复用，拿到就读，不要持有）
     */
    public static synchronized Plan planNumber(LivingEntity target, float value, byte style,
                                               int topColor, int bottomColor, boolean italic) {
        if (!Float.isFinite(value)) {
            value = 0f;
        }
        return resolve(target, formatNumber(value), value, style, topColor, bottomColor, italic);
    }

    /** 服务器停机 / 客户端断开时清空目标状态。 */
    public static synchronized void clear() {
        STATES.clear();
        nextMergeKey = 1;
    }

    /** 当前记录了多少个目标的状态（调试用）。 */
    public static synchronized int trackedTargets() {
        return STATES.size();
    }

    /** 累计的生成请求数 / 被合并数 / 跟踪目标数（调试用）。 */
    public static synchronized long[] stats() {
        return new long[]{spawnRequests, mergedRequests, STATES.size()};
    }

    // ============================ 内部 ============================

    /**
     * 合并判定的主体。
     *
     * @param delta 伤害增量；非数字（文字类）传 {@link Float#NaN}
     */
    private static Plan resolve(LivingEntity target, String text, float delta, byte style,
                                int topColor, int bottomColor, boolean italic) {
        spawnRequests++;
        Plan plan = PLAN.get();

        if (target == null || text == null || text.isEmpty()) {
            plan.merge = false;
            plan.mergeKey = 0;
            plan.total = Float.NaN;
            plan.text = text == null ? "" : text;
            return plan;
        }

        boolean numeric = !Float.isNaN(delta);
        int signature = signature(style, topColor, bottomColor, italic, numeric ? null : text);

        long now = System.currentTimeMillis();
        TargetState state = STATES.get(target.getUUID());
        if (state == null) {
            state = new TargetState();
            STATES.put(target.getUUID(), state);
            pruneIfCrowded(now);
        } else if (now - state.lastSpawnMs > STATE_TTL_MS) {
            // 太久没打这个目标：整桶作废，避免拿旧数字继续累加
            state.bucketKey = 0;
            state.bucketTotal = 0f;
            state.bucketSignature = 0;
            state.bucketNumeric = false;
            state.recentFilled = 0;
            state.recentIndex = 0;
        }

        boolean sameBucket = state.bucketKey != 0 && state.bucketSignature == signature;
        boolean withinGap = sameBucket && (now - state.lastSpawnMs) <= mergeGapMs();
        boolean flood = sameBucket && countRecent(state, now) >= floodCount();
        boolean merge = mergeEnabled() && (withinGap || flood);

        recordSpawn(state, now);
        state.lastSpawnMs = now;

        if (merge) {
            mergedRequests++;
            plan.merge = true;
            plan.mergeKey = state.bucketKey;
            if (state.bucketNumeric && numeric) {
                state.bucketTotal += delta;
                plan.total = state.bucketTotal;
                plan.text = formatNumber(state.bucketTotal);
            } else {
                // 文字类：条数照样并掉，文字保持不变
                plan.total = Float.NaN;
                plan.text = text;
            }
            return plan;
        }

        int key = nextMergeKey++;
        if (nextMergeKey <= 0) {
            nextMergeKey = 1;
        }
        state.bucketKey = key;
        state.bucketSignature = signature;
        state.bucketNumeric = numeric;
        state.bucketTotal = numeric ? delta : 0f;

        plan.merge = false;
        plan.mergeKey = key;
        plan.total = state.bucketTotal;
        plan.text = text;
        return plan;
    }

    private static void recordSpawn(TargetState state, long now) {
        state.recentMs[state.recentIndex] = now;
        state.recentIndex = (state.recentIndex + 1) % RECENT_SIZE;
        if (state.recentFilled < RECENT_SIZE) {
            state.recentFilled++;
        }
    }

    private static int countRecent(TargetState state, long now) {
        int window = floodWindowMs();
        int count = 0;
        for (int i = 0; i < state.recentFilled; i++) {
            if (now - state.recentMs[i] <= window) {
                count++;
            }
        }
        return count;
    }

    private static void pruneIfCrowded(long now) {
        if (STATES.size() <= MAX_STATES) {
            return;
        }
        Iterator<Map.Entry<UUID, TargetState>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().lastSpawnMs > STATE_TTL_MS) {
                it.remove();
            }
        }
    }

    private static int signature(byte style, int topColor, int bottomColor, boolean italic, String textShape) {
        int result = style;
        result = 31 * result + (topColor & 0xFFFFFF);
        result = 31 * result + (bottomColor & 0xFFFFFF);
        result = 31 * result + (italic ? 1 : 0);
        result = 31 * result + (textShape == null ? 0 : textShape.hashCode());
        return result == 0 ? 1 : result;
    }

    /** 纯数字（可带正负号）才算「数字类」，这类才做累加。 */
    private static boolean isNumeric(String text) {
        int len = text.length();
        if (len == 0) {
            return false;
        }
        int start = (text.charAt(0) == '-' || text.charAt(0) == '+') ? 1 : 0;
        if (start >= len) {
            return false;
        }
        for (int i = start; i < len; i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static float parseNumber(String text) {
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    private static String formatNumber(float value) {
        return String.valueOf(Math.round(value));
    }

    // ---- 配置读取：配置没加载时退回默认值，不让飘字路径抛异常 ----

    private static boolean mergeEnabled() {
        try {
            return PerformanceConfig.DAMAGE_NUMBER_MERGE.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static int mergeGapMs() {
        try {
            return PerformanceConfig.DAMAGE_NUMBER_MERGE_GAP_MS.get();
        } catch (Throwable ignored) {
            return 140;
        }
    }

    private static int floodWindowMs() {
        try {
            return PerformanceConfig.DAMAGE_NUMBER_FLOOD_WINDOW_MS.get();
        } catch (Throwable ignored) {
            return 500;
        }
    }

    private static int floodCount() {
        try {
            return PerformanceConfig.DAMAGE_NUMBER_FLOOD_COUNT.get();
        } catch (Throwable ignored) {
            return 6;
        }
    }
}
