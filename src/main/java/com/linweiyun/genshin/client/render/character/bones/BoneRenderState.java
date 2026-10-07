package com.linweiyun.genshin.client.render.character.bones;

/**
 * 一帧渲染中提供给骨骼规则的状态。
 *
 * <p>目前只有帧插值进度：它用于把「按时间推进」的骨骼动画算到亚刻精度。
 */
public final class BoneRenderState {

    private final float partialTick;

    public BoneRenderState(float partialTick) {
        this.partialTick = partialTick;
    }

    public float getPartialTick() {
        return partialTick;
    }
}