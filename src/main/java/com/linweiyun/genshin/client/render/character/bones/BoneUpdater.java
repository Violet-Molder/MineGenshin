package com.linweiyun.genshin.client.render.character.bones;

import java.util.function.Consumer;

/**
 * 骨骼每帧调整规则。
 *
 * <p>实现通过 {@link RenderPassView#renderState()} 读本帧状态，通过 {@link BoneSnapshots}
 * 按名字取骨骼并修改（旋转 / 平移 / 缩放 / 显隐）。同一个规则可以被叠加执行。
 *
 * @param <S> 本帧的渲染状态类型
 */
@FunctionalInterface
public interface BoneUpdater<S> {

    void run(RenderPassView<S> renderPassInfo, BoneSnapshots snapshots);
}