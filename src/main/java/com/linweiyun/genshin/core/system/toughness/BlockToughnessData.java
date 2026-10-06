package com.linweiyun.genshin.core.system.toughness;

import com.linweiyun.elementlib.core.module.ElibModuleData;
import com.linweiyun.elementlib.core.module.ElibModuleType;

/** 单格方块的韧性；放在区块瞬态区，重载即回满。 */
public class BlockToughnessData implements ElibModuleData {

    private float current = -1f;
    private float max = -1f;
    private int stateId = -1;
    private long lastHitTick = Long.MIN_VALUE;

    @Override
    public ElibModuleType<?> type() {
        return ToughnessTypes.BLOCK_TOUGHNESS;
    }

    public boolean initialized() {
        return max > 0f;
    }

    public int stateId() {
        return stateId;
    }

    public long lastHitTick() {
        return lastHitTick;
    }

    public void markHit(long tick) {
        this.lastHitTick = tick;
    }

    public float current() {
        return current;
    }

    public float max() {
        return max;
    }

    public void reset(float max, int stateId) {
        this.max = max;
        this.current = max;
        this.stateId = stateId;
    }

    public void damage(float amount) {
        current = Math.max(0f, current - Math.max(0f, amount));
    }

    public float consumedRatio() {
        return max <= 0f ? 0f : Math.clamp(1f - current / max, 0f, 1f);
    }
}
