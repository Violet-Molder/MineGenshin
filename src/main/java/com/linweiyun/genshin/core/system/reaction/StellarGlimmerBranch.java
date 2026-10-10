package com.linweiyun.genshin.core.system.reaction;

import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.registry.register.ModReactionTypes;
import com.linweiyun.elementlib.api.ElementalReactionType;
import org.jetbrains.annotations.Nullable;

/**
 * 星烁体系（辉映·星烁，Radiance: Stellar Glimmer）的两个分支。
 *
 * <h2>命名关系</h2>
 * <pre>
 * 辉映·星烁   统称 —— 是<b>体系</b>，不是一条反应
 *   ├─ 星扩散   反应名；对应的<b>状态</b>叫「辉映·星扩散」
 *   └─ 星超导   反应名；对应的<b>状态</b>叫「辉映·星超导」
 * </pre>
 *
 * <p>要点有两条：
 * <ul>
 *   <li><b>反应名不带元素后缀</b>：星扩散就是星扩散，风段 / 冰段靠飘字底部颜色区分
 *       （见 {@code DamageIndicatorFactory} 的星烁配色），不写成「星扩散（风）」这种名字；</li>
 *   <li><b>辉映是状态（体系），不是反应</b>：「辉映·星扩散」只用来叫那个 buff，
 *       被触发出来的那条反应叫星扩散。</li>
 * </ul>
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

    /** 星扩散 —— 风段 / 冰段两段，对应状态「辉映·星扩散」。 */
    SWIRL("星扩散", "辉映·星扩散"),

    /** 星超导 —— 雷段 / 冰段两段，对应状态「辉映·星超导」。 */
    CONDUCE("星超导", "辉映·星超导");

    private final String reactionName;
    private final String stateName;

    StellarGlimmerBranch(String reactionName, String stateName) {
        this.reactionName = reactionName;
        this.stateName = stateName;
    }

    /** 反应名（星扩散 / 星超导）—— 飘字、日志里说「这条反应」时用它。 */
    public String reactionName() {
        return reactionName;
    }

    /** 状态名（辉映·星扩散 / 辉映·星超导）—— 说那个 buff 时用它。 */
    public String stateName() {
        return stateName;
    }

    /** 这条反应属于哪个分支；不是星烁反应就返回 null。 */
    @Nullable
    public static StellarGlimmerBranch of(@Nullable ElementalReactionType reactionType) {
        if (reactionType == null) {
            return null;
        }
        if (ModReactionTypes.is(reactionType, ModReactionTypes.STELLAR_SWIRL)
                || ModReactionTypes.is(reactionType, ModReactionTypes.STELLAR_SWIRL)) {
            return SWIRL;
        }
        if (ModReactionTypes.is(reactionType, ModReactionTypes.STELLAR_CONDUCE)
                || ModReactionTypes.is(reactionType, ModReactionTypes.STELLAR_CONDUCE)) {
            return CONDUCE;
        }
        return null;
    }

    /** 这条反应是不是星烁反应（星扩散或星超导）。 */
    public static boolean isStellarGlimmer(@Nullable ElementalReactionType reactionType) {
        return of(reactionType) != null;
    }

}
