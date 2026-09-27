package com.linweiyun.genshin.core.character.appearance;

import java.util.ArrayList;
import java.util.List;

/**
 * 猫耳挂件 → <b>该藏哪根骨骼</b> 的规则表（纯函数，双端可用，不碰任何渲染类）。
 *
 * <h2>实测的骨骼结构</h2>
 * {@code shenhe.geo.json}（572 根）里耳朵是一棵很小的子树，挂在 {@code Head} 下面：
 * <pre>
 *   Head
 *   └─ Ear        0 立方，纯容器（pivot y≈34.8，就在头顶）
 *      ├─ EarRight  16 立方
 *      └─ EarLeft   16 立方
 * </pre>
 * 名字别按「左右」去猜：{@code EarRight} 的 pivot x = −2.77、{@code EarLeft} 的
 * x = +2.34，而模型里 −X 是角色左手侧 —— 名字是贴图作者按「角色自己的左右」起的，
 * 和坐标轴方向相反。反正现在是一起开一起关，认 {@code Ear} 这个父级即可。
 *
 * <h2>为什么只能靠「渲染期藏骨骼」，不能靠动画</h2>
 * {@code shenhe.animation.json} 的 101 个动画里<b>没有一个</b>关键帧引用过耳朵
 * （{@code Ear} / {@code EarRight} / {@code EarLeft} 出现 0 次），耳朵完全靠父级
 * {@code Head} 带着动。所以「给动画补一条隐藏关键帧」既没有落点、也覆盖不了全部动画；
 * 而 Bedrock 几何格式根本没有骨骼可见性字段。结论与腿部变体一致：走
 * {@code CharacterAppearanceBones} 那套 {@code RenderPassInfo.BoneUpdater}。
 *
 * <h2>为什么藏 {@code Ear} 而不是分别藏左右耳</h2>
 * {@code skipChildrenRender} 会连整棵子树一起藏，所以藏父级 {@code Ear}
 * 就等于同时藏掉两只耳朵。以后要做「单只耳朵」的开关，把这里换成按
 * {@code EarLeft} / {@code EarRight} 各占一位即可，规则表本身不用改结构。
 */
public final class EarBoneRules {

    /** 耳朵子树的根 —— 藏它 = 两只耳朵一起消失。 */
    public static final String ROOT = "Ear";

    private EarBoneRules() {
    }

    /**
     * 当前外观下要隐藏的耳朵骨骼（没藏就是空表）。
     *
     * @param mask 外观位掩码，见 {@link CharacterAppearance}
     */
    public static List<String> hiddenBones(int mask) {
        if (!CharacterAppearance.catEarsHidden(mask)) {
            return List.of();
        }
        List<String> hidden = new ArrayList<>(1);
        hidden.add(ROOT);
        return hidden;
    }
}
