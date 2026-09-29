package com.linweiyun.genshin.core.character.util.appearance;

/**
 * 角色的<b>外观选项</b>——每条腿的腿部变体 + 猫耳挂件。
 *
 * <h2>为什么打包成一个 int</h2>
 * 它要进 {@code PGCharacterData} 的 {@code @Persisted} / {@code @DescSynced} 字段：
 * 一个 int 好入档、好同步、加字段时不用动 NBT 结构。以后要加别的部位
 * （手臂的 LS 变体、别的挂饰……）直接往高位继续排即可。
 *
 * <h2>每条腿控制两件事</h2>
 * <ol>
 *   <li><b>穿不穿鞋子</b>（1 位）；</li>
 *   <li><b>裸腿 / 白丝 / 黑丝</b>（2 位）。</li>
 * </ol>
 * 两者的耦合规则在 {@link LegBoneRules}：<b>不穿鞋时</b>，袜子类型还要决定露出来的
 * 那只脚用哪套网格（{@code jiao1} / {@code bs} / {@code hs}）——
 * 这就是「不穿鞋的情况下 2 会影响 1」。
 *
 * <h2>位布局</h2>
 * <pre>
 *   bit 0-1  左腿袜子 (0=BARE 1=WHITE 2=BLACK)
 *   bit 2    左腿鞋子 (1=穿鞋)
 *   bit 3-4  右腿袜子
 *   bit 5    右腿鞋子
 *   bit 6    猫耳隐藏 (1=藏起来，规则见 {@link EarBoneRules})
 * </pre>
 *
 * <h2>猫耳那一位为什么是「隐藏」而不是「显示」</h2>
 * 老存档里这一位恒为 0。写成「1=显示」的话，升级后所有老角色的猫耳会集体消失
 * （模型上本来就长着耳朵，视觉默认值是「显示」）；写成「1=隐藏」，0 就是显示，
 * 老存档的表现一个字都不变。{@link #DEFAULT_MASK} 因此也不用动。
 */
public final class CharacterAppearance {

    /**
     * 出厂默认外观：<b>黑丝 + 穿鞋</b>。
     *
     * <p>改这一行就能改所有角色的默认腿部变体 —— 页面里玩家可以逐条腿改，
     * 这里只决定「没动过设置的人看到什么」。
     */
    public static final int DEFAULT_MASK =
            pack(SockType.BLACK, true, SockType.BLACK, true);

    private static final int SOCK_BITS = 0b11;
    private static final int LEFT_SOCK_SHIFT = 0;
    private static final int LEFT_SHOES_SHIFT = 2;
    private static final int RIGHT_SOCK_SHIFT = 3;
    private static final int RIGHT_SHOES_SHIFT = 5;
    private static final int CAT_EARS_HIDDEN_SHIFT = 6;

    private CharacterAppearance() {
    }

    public static int pack(SockType leftSock, boolean leftShoes,
                           SockType rightSock, boolean rightShoes) {
        return ((leftSock.ordinal() & SOCK_BITS) << LEFT_SOCK_SHIFT)
                | ((leftShoes ? 1 : 0) << LEFT_SHOES_SHIFT)
                | ((rightSock.ordinal() & SOCK_BITS) << RIGHT_SOCK_SHIFT)
                | ((rightShoes ? 1 : 0) << RIGHT_SHOES_SHIFT);
    }

    public static SockType sock(int mask, boolean left) {
        int shift = left ? LEFT_SOCK_SHIFT : RIGHT_SOCK_SHIFT;
        return SockType.byOrdinal((mask >> shift) & SOCK_BITS);
    }

    public static boolean shoes(int mask, boolean left) {
        int shift = left ? LEFT_SHOES_SHIFT : RIGHT_SHOES_SHIFT;
        return ((mask >> shift) & 1) != 0;
    }

    public static int withSock(int mask, boolean left, SockType sock) {
        int shift = left ? LEFT_SOCK_SHIFT : RIGHT_SOCK_SHIFT;
        int cleared = mask & ~(SOCK_BITS << shift);
        return cleared | ((sock.ordinal() & SOCK_BITS) << shift);
    }

    public static int withShoes(int mask, boolean left, boolean shoes) {
        int shift = left ? LEFT_SHOES_SHIFT : RIGHT_SHOES_SHIFT;
        int cleared = mask & ~(1 << shift);
        return cleared | ((shoes ? 1 : 0) << shift);
    }

    /** 猫耳挂件当前是不是被藏起来了（出厂默认：显示）。 */
    public static boolean catEarsHidden(int mask) {
        return ((mask >> CAT_EARS_HIDDEN_SHIFT) & 1) != 0;
    }

    /** 改猫耳的显示 / 隐藏。 */
    public static int withCatEarsHidden(int mask, boolean hidden) {
        int cleared = mask & ~(1 << CAT_EARS_HIDDEN_SHIFT);
        return cleared | ((hidden ? 1 : 0) << CAT_EARS_HIDDEN_SHIFT);
    }
}
