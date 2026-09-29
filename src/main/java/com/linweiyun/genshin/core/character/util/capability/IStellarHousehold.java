package com.linweiyun.genshin.core.character.util.capability;

import com.linweiyun.genshin.core.element.GenshinElement;
import com.linweiyun.genshin.core.system.reaction.StellarGlimmerBranch;
import org.jetbrains.annotations.Nullable;

/**
 * 「户口天赋」—— 一条反应体系的<b>转化 + 体系加成</b>，写在同一个天赋里。
 *
 * <h2>什么叫户口</h2>
 * 「户口」是这条体系在队伍里的<b>来源</b>：谁有户口，谁就负责
 * ①把基础反应转化成体系反应（冰扩散 → 星扩散）、②给全队提供体系的基础伤害提升。
 * 这两件事是绑在一起的 —— 有户口才有转化，也就才有那份加成。
 *
 * <pre>
 * 星扩散户口（薇斯娜）：冰扩散 → 星扩散，并按她的攻击力给全队星扩散基础伤害提升
 * 星超导户口（未定）：  某反应 → 星超导，并给星超导的基础伤害提升
 * 月体系户口：          同理（见各自体系）
 * </pre>
 *
 * <h2>和「状态」的区别（重要）</h2>
 * <b>能进入体系状态 ≠ 有户口。</b>
 * 有的角色自己没户口，但天赋写着「附近触发星扩散时进入辉映·星扩散状态并获得加成」——
 * 那是<b>状态</b>侧的能力（见 {@link IStellarStateHolder}），与户口无关。
 * 反过来，有户口的角色通常也会给自己上状态，但那仍然要另外写。
 *
 * <p>所以查询分两条路：
 * <ul>
 *   <li>「队伍里有没有人能把冰扩散转成星扩散」→ 查<b>户口</b>（{@code StellarGlimmer.householdOf}）；</li>
 *   <li>「谁能被挂上星扩散状态」→ 查<b>状态持有者</b>（{@link IStellarStateHolder}）。</li>
 * </ul>
 */
public interface IStellarHousehold {

    /**
     * 我提供的星烁户口；没有就返回 {@code null}。
     *
     * <p>每次查询现算（基础加成随面板变化），所以实现里直接 new 一个即可。
     */
    @Nullable
    StellarHousehold stellarHousehold();

    /**
     * 一份星烁户口：分支 + 转化规则 + 体系基础伤害提升。
     *
     * @param branch        这一户口负责哪个分支（星扩散 / 星超导）
     * @param fromElement   从哪条基础反应转化过来（星扩散 = 冰）
     * @param toElement     转成哪条体系反应（星扩散 = 风；星超导 = 雷）
     * @param baseBonusMult 给全队的体系<b>基础</b>伤害倍率提升（{@code 0.14} = +14%）；
     *                      取队伍里最高的那一份，不叠加
     */
    record StellarHousehold(StellarGlimmerBranch branch,
                            GenshinElement fromElement,
                            GenshinElement toElement,
                            float baseBonusMult) {

        /** 这条户口能不能把 {@code spreadElement} 的扩散转成体系反应。 */
        public boolean converts(GenshinElement spreadElement) {
            return fromElement != null && fromElement == spreadElement;
        }
    }
}