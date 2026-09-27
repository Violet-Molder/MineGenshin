package com.linweiyun.genshin.core.system.performance;

import com.linweiyun.genshin.config.PerformanceConfig;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * 热路径日志节流（计算优化模块）。
 *
 * <h2>它解决什么</h2>
 * 有些日志是「每次伤害 / 每次反应结算」都写的（感电触发、月感电刷云、元素战技扫方块……）。
 * 单看一条不贵，但攻速堆高以后一秒能写上几十上百条：
 * <ul>
 *   <li>SLF4J 先按模板把参数拼成字符串（参数里还有 {@code getName().getString()} 这种）；</li>
 *   <li>再进 appender 落盘 —— 这一步是<b>同步 + 加锁</b>的，写日志的线程要等 I/O。</li>
 * </ul>
 * 于是「打得多」直接变成「主线程等磁盘」。战斗卡顿里这部分很隐蔽，因为日志本身看不见耗时。
 *
 * <h2>怎么节流</h2>
 * 每个 {@code key} 独立计数：每 {@value #WINDOW_MS} ms 最多放行 N 条（默认取
 * {@code performance.toml} 的 {@code logging.hot_path_max_per_second}），
 * 超出的先压住；等到下一个窗口开始时，补一条「刚才又压了 M 条」的汇总。
 * 这样既保住了「第一次一定看得见」，又不会让日志量随攻击速度线性上涨。
 *
 * <p>调用点的写法：<b>先问再拼</b> ——
 * {@code if (HotPathLog.allow(LOGGER, "key", "说明")) { LOGGER.info(...); }}</p>
 *
 * <p>日志可能来自不同线程（服务端每个维度各一条 tick 线程），所以状态用
 * {@link HashMap} 存、按 key 加锁，不用全局锁。</p>
 */
public final class HotPathLog {

    /** 计数窗口（毫秒）：一个窗口放行若干条 */
    private static final long WINDOW_MS = 1000L;
    /** 兜底上限：调用点的 key 数量是有界的，这里只是防止有人拿动态 key 调用 */
    private static final int MAX_KEYS = 256;

    private static final Map<String, State> STATES = new HashMap<>();

    private HotPathLog() {}

    private static final class State {
        long windowStartMs;
        int emitted;
        int suppressed;
    }

    /**
     * 这次日志该不该输出。
     *
     * @param logger 目标 logger（窗口切换时用它补汇总行）
     * @param key    调用点标识（同一处日志用同一个常量字符串）
     * @param label  汇总行里显示的名字，例如「感电触发」
     */
    public static boolean allow(Logger logger, String key, String label) {
        if (!enabled()) {
            return true;
        }

        long now = System.currentTimeMillis();
        int budget = maxPerWindow();

        State state = state(key);
        synchronized (state) {
            if (now - state.windowStartMs >= WINDOW_MS) {
                // 上一个窗口压下来的量补一条汇总，不然「日志少了」会让人以为没触发
                if (state.suppressed > 0) {
                    logger.info("[{}] 上 1 秒另有 {} 条同类日志被节流（performance.toml: logging.hot_path_max_per_second）",
                            label, state.suppressed);
                }
                state.windowStartMs = now;
                state.emitted = 0;
                state.suppressed = 0;
            }

            if (state.emitted < budget) {
                state.emitted++;
                return true;
            }
            state.suppressed++;
            return false;
        }
    }

    /** 测试 / 重载用：清空全部计数。 */
    public static void clear() {
        synchronized (STATES) {
            STATES.clear();
        }
    }

    private static State state(String key) {
        synchronized (STATES) {
            State state = STATES.get(key);
            if (state == null) {
                if (STATES.size() >= MAX_KEYS) {
                    STATES.clear();
                }
                state = new State();
                state.windowStartMs = System.currentTimeMillis();
                STATES.put(key, state);
            }
            return state;
        }
    }

    // ---- 配置读取：配置没加载时退回默认值 ----

    private static boolean enabled() {
        try {
            return PerformanceConfig.HOT_PATH_LOG_THROTTLE.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static int maxPerWindow() {
        try {
            return Math.max(1, PerformanceConfig.HOT_PATH_LOG_MAX_PER_SECOND.get());
        } catch (Throwable ignored) {
            return 8;
        }
    }
}
