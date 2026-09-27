package com.linweiyun.genshin.client.render.character.appearance;

import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.linweiyun.genshin.core.character.appearance.EarBoneRules;
import com.linweiyun.genshin.core.character.appearance.LegBoneRules;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 把 {@code LegBoneRules}（腿部变体）/ {@code EarBoneRules}（猫耳挂件）的「该藏哪些骨骼」
 * 合成一张名单，再翻译成 GeckoLib 的逐趟骨骼覆盖（{@link RenderPassInfo.BoneUpdater}）。
 *
 * <p>名字从 {@code LegAppearanceBones} 改成现在这个：掩码从 6 位（两条腿）扩到 7 位之后，
 * 它管的已经不只是腿 —— 猫耳也走同一张「外观掩码 → 骨骼隐藏器」的表。两个规则表本身仍然
 * 各管一摊（腿 / 耳），这里只负责合并与缓存。
 *
 * <h2>为什么要 BoneUpdater，而不是改动画 / 改 geo / 存到渲染器字段上</h2>
 * <ul>
 *   <li><b>不改动画</b>：三套腿部网格在 {@code shenhe.animation.json} 里
 *       <b>一次都没被引用</b>（0 处关键帧），它们完全靠父级
 *       {@code {S}Leg} / {@code {S}LowerLeg} / {@code {S}Foot} 带动；
 *       猫耳同理（101 个动画里 {@code Ear*} 出现 0 次，见 {@code EarBoneRules}）。
 *       给每个动画补「隐藏另两套 / 隐藏耳朵」的关键帧既没必要、也不可能覆盖全部动画。</li>
 *   <li><b>不改 geo</b>：Bedrock 几何格式<b>没有</b>骨骼可见性字段，
 *       {@code GeometryBone} 反序列化器根本不读 visible —— 加字段没用。</li>
 *   <li><b>不存渲染器字段</b>：渲染器是按 charId 缓存的<b>单例</b>
 *       （{@code CharacterRenderDispatcher.RENDERERS}），存进去就要担心过期；
 *       而且页面预览要用<b>另一套</b>外观渲染同一个角色，单例字段会打架。
 *       BoneUpdater 是「这一趟渲染临时用」的，天然没有这两个问题。</li>
 * </ul>
 *
 * <p>两条渲染链路都会吃到它：本模组的几何优化（{@code GeoRenderIntercept} → {@code renderPosed}）
 * 和 GeckoLib 兜底渲染，最后都要经 {@code BoneSnapshot#isHidden / areChildrenHidden} 过一遍骨骼遍历。
 *
 * <p>项目里的先例：{@code FirstPersonCharacterRenderer.HIDE_HEAD}（第一人称藏头）、
 * {@code BoneMountGeoLayer}（藏挂点原骨骼）。
 */
public final class CharacterAppearanceBones {

    /** 掩码有效位数：两条腿 6 位 + 猫耳 1 位。 */
    private static final int MASK_BITS = 7;
    private static final int MASK_LIMIT = 1 << MASK_BITS;
    private static final int MASK_MASK = MASK_LIMIT - 1;

    /**
     * 逐掩码缓存一份 updater（0~127）。
     *
     * <p>隐藏名单是常量推导出来的，每帧重建只会白白产生垃圾 ——
     * {@code renderCharacter} 是每帧每玩家都要走的路径。
     */
    private static final RenderPassInfo.BoneUpdater<GeoRenderState>[] CACHE = newUpdaterCache();

    private CharacterAppearanceBones() {
    }

    @SuppressWarnings("unchecked")
    private static RenderPassInfo.BoneUpdater<GeoRenderState>[] newUpdaterCache() {
        RenderPassInfo.BoneUpdater<GeoRenderState>[] cache = new RenderPassInfo.BoneUpdater[MASK_LIMIT];
        for (int mask = 0; mask < cache.length; mask++) {
            cache[mask] = updaterOf(hiddenBones(mask));
        }
        return cache;
    }

    /** 掩码对应的骨骼隐藏器；掩码越界时退化成「裸腿不穿鞋、耳朵照常显示」。 */
    public static RenderPassInfo.BoneUpdater<GeoRenderState> forMask(int mask) {
        return CACHE[mask & MASK_MASK];
    }

    /** 整个外观掩码要藏的骨骼：腿部变体（两腿各一套）+ 猫耳。 */
    private static List<String> hiddenBones(int mask) {
        List<String> hidden = new ArrayList<>(LegBoneRules.hiddenBones(mask));
        hidden.addAll(EarBoneRules.hiddenBones(mask));
        return hidden;
    }

    private static RenderPassInfo.BoneUpdater<GeoRenderState> updaterOf(List<String> hidden) {
        return (renderPassInfo, snapshots) -> {
            for (String bone : hidden) {
                snapshots.ifPresent(bone, snapshot -> {
                    // skipRender 藏这一根自己的立方体，skipChildrenRender 藏整棵子树 ——
                    // 变体子树的根自己通常是 0 立方（纯容器），真正要藏的都在子级里。
                    snapshot.skipRender(true);
                    snapshot.skipChildrenRender(true);
                });
            }
        };
    }

    /** 给 {@code performRenderPass} 用的重载：没有角色数据时不加任何覆盖。 */
    @Nullable
    public static RenderPassInfo.BoneUpdater<GeoRenderState> forMaskOrNull(@Nullable Integer mask) {
        return mask == null ? null : forMask(mask);
    }
}
