package com.linweiyun.genshin.core.character.util.appearance;

import java.util.ArrayList;
import java.util.List;

/**
 * 腿部变体 → <b>该藏哪些骨骼</b> 的规则表（纯函数，双端可用，不碰任何渲染类）。
 *
 * <h2>为什么只能按「子树根」藏，不能按名字前缀筛</h2>
 * {@code shenhe.geo.json}（实测 572 根骨骼）里左右腿的名字被复制粘贴搞乱了：
 * <pre>
 *   RightLeg_heisi  底下挂着 RightLeg_baisi8/9/10、LeftLeg_baisi5
 *   RightLeg_baisi  底下挂着 LeftLeg_baisi3
 *   LeftLeg_heisi   底下挂着 RightLeg_baisi5 …
 * </pre>
 * 所以「按 {@code Left/Right/baisi/heisi} 前缀匹配」会直接把另一条腿的网格切掉。
 * 正确做法是<b>只认变体子树的根骨骼</b>，整棵子树一起隐藏
 * （{@code skipRender + skipChildrenRender}），子骨骼叫什么名字都不影响。
 *
 * <h2>实测的变体分组</h2>
 * <pre>
 *   大腿  {S}Leg       → {S}Leg_tui(9) / {S}Leg_baisi(14) / {S}Leg_heisi(14)      三选一
 *   小腿  {S}LowerLeg  → {S}LowerLeg_tui(11) / _bs(15) / _hs(15)                  三选一
 *   脚    {S}Foot      → {S}Foot_1(鞋,73) 与 {S}Foot_jiao(脚,0) 整棵互斥
 *          穿鞋时   {S}Foot_1 内部 {S}Foot_1tui / _1bs / _1hs 三选一
 *          不穿鞋时 {S}Foot_jiao 内部 M{S}Foot_jiao1 / M{S}Foot_bs / M{S}Foot_hs 三选一
 * </pre>
 * 膝盖那几块（{@code {S}_xigai_tui} / {@code _tui3} / {@code _tui5}）分别挂在三套大腿
 * 子树里，跟着子树一起切，不用单独列。
 *
 * <h2>不参与变体的一条</h2>
 * {@code {S}_xigai_tui2}（7 立方，挂在 {@code Body3} 上、pivot y≈14.42 的髋/大腿根位置）
 * <b>不在任何变体子树里，也没有同位置的三套替身</b>，所以三套外观下都保持显示。
 */
public final class LegBoneRules {

    public static final String LEFT = "Left";
    public static final String RIGHT = "Right";

    private LegBoneRules() {
    }

    /** 大腿三选一的子树根：裸腿 {@code _tui} / 白丝 {@code _baisi} / 黑丝 {@code _heisi}。 */
    private static String thigh(String side, SockType sock) {
        return switch (sock) {
            case BARE -> side + "Leg_tui";
            case WHITE -> side + "Leg_baisi";
            case BLACK -> side + "Leg_heisi";
        };
    }

    /** 小腿三选一的子树根：{@code _tui} / {@code _bs} / {@code _hs}。 */
    private static String calf(String side, SockType sock) {
        return switch (sock) {
            case BARE -> side + "LowerLeg_tui";
            case WHITE -> side + "LowerLeg_bs";
            case BLACK -> side + "LowerLeg_hs";
        };
    }

    /** 不穿鞋时脚掌三选一：{@code M{S}Foot_jiao1} / {@code M{S}Foot_bs} / {@code M{S}Foot_hs}。 */
    private static String bareFoot(String side, SockType sock) {
        return switch (sock) {
            case BARE -> "M" + side + "Foot_jiao1";
            case WHITE -> "M" + side + "Foot_bs";
            case BLACK -> "M" + side + "Foot_hs";
        };
    }

    /** 穿鞋时鞋内衬三选一：{@code {S}Foot_1tui} / {@code _1bs} / {@code _1hs}。 */
    private static String shoeLining(String side, SockType sock) {
        return switch (sock) {
            case BARE -> side + "Foot_1tui";
            case WHITE -> side + "Foot_1bs";
            case BLACK -> side + "Foot_1hs";
        };
    }

    private static void hideAllBut(String side, List<String> out, SockType keep,
                                   SockType[] all, java.util.function.BiFunction<String, SockType, String> naming) {
        for (SockType candidate : all) {
            if (candidate == keep) continue;
            out.add(naming.apply(side, candidate));
        }
    }

    private static final SockType[] ALL_SOCKS = SockType.VALUES;

    /**
     * 某一条腿在当前外观下要隐藏的骨骼（整棵子树）。
     *
     * @param mask 外观位掩码，见 {@link CharacterAppearance}
     * @param left true = 左腿
     */
    public static List<String> hiddenBones(int mask, boolean left) {
        String side = left ? LEFT : RIGHT;
        SockType sock = CharacterAppearance.sock(mask, left);
        boolean shoes = CharacterAppearance.shoes(mask, left);

        List<String> hidden = new ArrayList<>(8);

        // 1) 大腿：藏掉另外两套
        hideAllBut(side, hidden, sock, ALL_SOCKS, LegBoneRules::thigh);
        // 2) 小腿：藏掉另外两套
        hideAllBut(side, hidden, sock, ALL_SOCKS, LegBoneRules::calf);
        // 3) 脚：鞋与脚整棵互斥；留下的那一棵内部再按袜子类型三选一
        if (shoes) {
            hidden.add(side + "Foot_jiao");
            hideAllBut(side, hidden, sock, ALL_SOCKS, LegBoneRules::shoeLining);
        } else {
            hidden.add(side + "Foot_1");
            hideAllBut(side, hidden, sock, ALL_SOCKS, LegBoneRules::bareFoot);
        }
        return hidden;
    }

    /** 两条腿在当前外观下要隐藏的骨骼。 */
    public static List<String> hiddenBones(int mask) {
        List<String> hidden = new ArrayList<>(16);
        hidden.addAll(hiddenBones(mask, true));
        hidden.addAll(hiddenBones(mask, false));
        return hidden;
    }
}
