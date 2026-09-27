package com.linweiyun.genshin.core.system.about;

import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import org.slf4j.Logger;

/**
 * 冻元素动态衰减状态 —— 挂在 StatusContainer 上，独立于具体的 FROZEN 实例
 *
 * 衰减规则：
 *   冻结中（有 FROZEN 实例活着）：每秒衰减率 +0.1
 *     例：第1秒 0.4 → 第2秒 0.5 → 第3秒 0.6 → ...
 *   脱离冻结后（所有 FROZEN 实例消失）：每秒衰减率 -0.2，直到回到 0.4
 *     例：冻结结束时衰减率 1.2 → 1秒后 1.0 → 2秒后 0.8 → 3秒后 0.6 → 4秒后 0.4
 *
 * 为什么挂在 StatusContainer 而不是 ElementalAttachmentInstance 上？
 *   因为 FROZEN 实例消失后"脱离后恢复"逻辑还需要继续运行。
 *   挂在容器上意味着：即使所有 FROZEN 都消失了，衰减率状态还在，
 *   新生成的 FROZEN 实例会继承"当前还没恢复完的衰减率"
 *   → 连续冻结持续时间越来越短
 */
public class FrozenDecayState implements IPersistedSerializable {
    public static final Logger LOGGER = ModLog.getLogger(LogGroup.ELEMENT);
    /** 初始/最小衰减率：每秒衰减 0.4 元素量 */
    public static final float MIN_DECAY_RATE = 0.4f;

    /** 冻结中每秒衰减率增长量 */
    public static final float GROW_PER_SECOND = 0.1f;

    /** 脱离冻结后每秒衰减率恢复量 */
    public static final float RECOVER_PER_SECOND = 0.2f;

    @Persisted(key = "frozen_current_decay")
    private float currentDecayRate;

    /** tick 计数器：累计到 20 才真正改一次 decayRate（20 tick = 1秒） */
    @Persisted(key = "frozen_tick_counter")
    private int tickCounter;
    /** 是否活跃：只有衰减率被抬升到 MIN 以上过，或者当前有 FROZEN 时才是 true */
    @Persisted(key = "frozen_active")
    private boolean active;


    public FrozenDecayState() {
        this.currentDecayRate = MIN_DECAY_RATE;
    }
    /**
     * 冻结反应生成 FROZEN 时调用
     * 激活衰减状态，让 onTick 开始工作
     */
    public void activate() {
        this.active = true;
    }

    /**
     * 每 tick 都可以调，内部只有凑够 20 tick（1秒）才真正增减衰减率
     * 这样 StatusContainer.tick() 里直接调就行，不用自己算"1秒 = 20 tick"
     *
     * @param hasFrozenAlive 当前容器里是否有 FROZEN 实例活着
     */
    public void onTick(boolean hasFrozenAlive) {
        // 不活跃就直接跳过（从未冻结过，或已恢复完毕且无 FROZEN）
        if (!active) return;

        tickCounter++;
        if (tickCounter < 20) return;
        tickCounter = 0;

        if (hasFrozenAlive) {
            currentDecayRate += GROW_PER_SECOND;
        } else {
            // 已经回到 MIN 就不再递减，直接标记非活跃，彻底停掉
            if (currentDecayRate <= MIN_DECAY_RATE) {
                currentDecayRate = MIN_DECAY_RATE;
                active = false;
                return;
            }
            currentDecayRate -= RECOVER_PER_SECOND;
            if (currentDecayRate <= MIN_DECAY_RATE) {
                currentDecayRate = MIN_DECAY_RATE;
                active = false;
            }
        }
    }

    public float getCurrentDecayRate() {
        return currentDecayRate;
    }
}