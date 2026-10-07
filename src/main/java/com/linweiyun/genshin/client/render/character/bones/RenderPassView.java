package com.linweiyun.genshin.client.render.character.bones;

/**
 * 一次渲染趟的视图：本帧状态 + 本次能改的骨骼集合。
 *
 * @param <S> 本帧的渲染状态类型
 */
public final class RenderPassView<S> {

    private final S renderState;
    private final BoneSnapshots snapshots;

    public RenderPassView(S renderState, BoneSnapshots snapshots) {
        this.renderState = renderState;
        this.snapshots = snapshots;
    }

    /** 本帧状态。 */
    public S renderState() {
        return renderState;
    }

    /** 本次渲染能改的骨骼集合。 */
    public BoneSnapshots snapshots() {
        return snapshots;
    }
}