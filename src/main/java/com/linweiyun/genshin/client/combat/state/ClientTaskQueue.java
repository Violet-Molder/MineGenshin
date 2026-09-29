package com.linweiyun.genshin.client.combat.state;

import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端延迟任务队列（1 tick 一步）。
 *
 * <p>动作编排里有大量「延迟若干刻再执行」的纯表现类工作（命中音效、{@code swing()} 手臂等），
 * 它们不能直接在服务端回包或按键回调里做，必须摊到后续若干客户端 tick 上。
 * 队列被打断时整体清空（见 {@code ActionStateMachine#resetToDefault}）。
 *
 * <p>整块逻辑原先内嵌在 {@code ActionStateMachine} 里，与其状态推进无关，故独立成类。
 */
public final class ClientTaskQueue {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.COMBAT);

    /** 客户端延迟任务队列（1 tick 一步），被打断时整体清空。 */
    private static final List<Task> TASKS = new ArrayList<>();

    private ClientTaskQueue() {
    }

    /** 排入一个延迟任务，{@code delayTicks} 刻后执行。 */
    public static void enqueue(int delayTicks, Runnable action) {
        TASKS.add(new Task(delayTicks, action));
    }

    /** 清空队列 —— 被打断的那一刀，剩下的表现不再补演。 */
    public static void clear() {
        TASKS.clear();
    }

    /** 由客户端 tick 驱动，把队列里所有任务的倒计时减一。 */
    public static void tick() {
        if (TASKS.isEmpty()) {
            return;
        }

        // 快照复制：允许任务在执行过程中继续安全地注册新任务
        List<Task> tasksToRun = new ArrayList<>(TASKS);
        TASKS.clear();

        for (Task task : tasksToRun) {
            task.delay--;

            if (task.delay <= 0) {
                try {
                    task.action.run();
                } catch (Exception exception) {
                    LOGGER.error("[MineGenshin] 客户端延迟任务执行失败", exception);
                }
            } else {
                TASKS.add(task);
            }
        }
    }

    /** 一个待执行的客户端延迟任务。 */
    public static final class Task {
        public int delay;
        public final Runnable action;

        public Task(int delay, Runnable action) {
            this.delay = delay;
            this.action = action;
        }
    }
}
