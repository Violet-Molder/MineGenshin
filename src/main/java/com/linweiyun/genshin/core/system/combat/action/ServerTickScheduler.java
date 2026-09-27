package com.linweiyun.genshin.core.system.combat.action;

import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 服务端 tick 延时调度器。
 * <p>
 * 使用 ConcurrentLinkedQueue 收集延时任务，
 * 在服务端 tick 时递减计数器，归零时执行 Runnable。
 * <p>
 * 需在服务端 tick 事件中调用 {@link #tick()} 驱动。
 */
public final class ServerTickScheduler {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.COMBAT);
    private static final ConcurrentLinkedQueue<Task> QUEUE = new ConcurrentLinkedQueue<>();

    private ServerTickScheduler() {}

    /**
     * 安排一个延时任务。
     * @param delayTicks 延时（刻），<=0 时立即执行
     */
    public static void schedule(int delayTicks, Runnable action) {
        if (delayTicks <= 0) {
            action.run();
        } else {
            QUEUE.add(new Task(action, delayTicks));
        }
    }

    /**
     * 每服务端 tick 调用一次。
     * 所有计数器 -1，归零的任务被执行后移除。
     */
    public static void tick() {
        if (QUEUE.isEmpty()) return;
        List<Task> ready = new ArrayList<>();
        for (Task t : QUEUE) {
            t.remaining--;
            if (t.remaining <= 0) ready.add(t);
        }
        for (Task t : ready) {
            try {
                t.action.run();
            } catch (Exception e) {
                LOGGER.error("[ServerTickScheduler] task error", e);
            }
        }
        QUEUE.removeAll(ready);
    }

    public static int pendingCount() {
        return QUEUE.size();
    }

    private static final class Task {
        final Runnable action;
        int remaining;

        Task(Runnable action, int remaining) {
            this.action = action;
            this.remaining = remaining;
        }
    }
}