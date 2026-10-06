package com.linweiyun.genshin.core.system.reaction;

import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.registry.register.ModReactionTypes;
import com.linweiyun.elementlib.api.ElementalReactionType;
import org.jetbrains.annotations.Nullable;

/**
 * 辉映·星烁（Radiance: Stellar Glimmer）的两个分支。
 *
 * <h2>命名关系</h2>
 * <pre>
 * 辉映·星烁（Radiance: Stellar Glimmer）  ← 统称，不是一条独立反应
 *   ├─ 辉映·星超导（Radiance: Stellar Conduce）
 *   └─ 辉映·星扩散（Radiance: Stellar Glimmer-Swirl，就是原来的星扩散）
 * </pre>
 *
 * <h2>为什么要分「分支」而不是直接判断反应类型</h2>
 * 加成来源经常写得很粗：有的 buff/天赋写的是「<b>星烁反应加成</b>」——
 * 那星扩散和星超导<b>都要加</b>；有的只写了其中一个（例如薇斯娜的天赋只写星扩散）——
 * 那就<b>只加那一个</b>。用分支枚举正好表达这两种写法：
 *
 * <pre>
 * // 两种都加
 * public float getStellarGlimmerBonus(StellarGlimmerBranch branch) { return 0.20f; }
 *
 * // 只加星扩散
 * public float getStellarGlimmerBonus(StellarGlimmerBranch branch) {
 *     return branch == StellarGlimmerBranch.SWIRL ? 0.20f : 0f;
 * }
 * </pre>
 */
public enum StellarGlimmerBranch {

    /** 辉映·星扩散 —— 风/冰星辉扩散。 */
    SWIRL("辉映·星扩散"),

    /** 辉映·星超导 —— 雷/冰星辉超导。 */
    CONDUCE("辉映·星超导");

    private final String displayName;

    StellarGlimmerBranch(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** 这条反应属于哪个分支；不是星烁反应就返回 null。 */
    @Nullable
    public static StellarGlimmerBranch of(@Nullable ElementalReactionType reactionType) {
        if (reactionType == null) {
            return null;
        }
        if (ModReactionTypes.is(reactionType, ModReactionTypes.STELLAR_SWIRL_WIND)
                || ModReactionTypes.is(reactionType, ModReactionTypes.STELLAR_SWIRL_ICE)) {
            return SWIRL;
        }
        if (ModReactionTypes.is(reactionType, ModReactionTypes.STELLAR_CONDUCE_ELECTRO)
                || ModReactionTypes.is(reactionType, ModReactionTypes.STELLAR_CONDUCE_ICE)) {
            return CONDUCE;
        }
        return null;
    }

    /** 这条反应是不是星烁反应（星扩散或星超导）。 */
    public static boolean isStellarGlimmer(@Nullable ElementalReactionType reactionType) {
        return of(reactionType) != null;
    }

    /**
     * 这条反应结算时用哪个元素吃抗性区。
     *
     * <p>和「哪个元素触发」无关 —— 星扩散-风按风抗算、星扩散-冰按冰抗算，
     * 超导同理（雷 / 冰）。
     */
    public static GenshinElement damageElementOf(@Nullable ElementalReactionType reactionType) {
        if (ModReactionTypes.is(reactionType, ModReactionTypes.STELLAR_SWIRL_WIND)) {
            return ModElements.ANEMO.get();
        }
        if (ModReactionTypes.is(reactionType, ModReactionTypes.STELLAR_CONDUCE_ELECTRO)) {
            return ModElements.ELECTRO.get();
        }
        return ModElements.CYRO.get();
    }
}
