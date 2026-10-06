package com.linweiyun.genshin.core.system.shield;

import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.genshin.core.element.ModElements;
import org.jetbrains.annotations.Nullable;

/**
 * 护盾元素表的<b>固定槽位</b> —— 让「每个元素的消耗/吸收」能用定长数组存。
 *
 * <p>顺序是契约：改顺序会让已经存进存档的盾数据错位，只能在末尾追加。
 */
public enum ShieldElement {

    /** 物理（无元素）。 */
    PHYSICAL(0),
    PYRO(1),
    HYDRO(2),
    ELECTRO(3),
    CYRO(4),
    ANEMO(5),
    GEO(6),
    DENDRO(7),
    /** 冻结（水+冰的复合状态）。 */
    FROZEN(8);

    /** 表里的槽位数。 */
    public static final int COUNT = 9;

    private final int slot;

    ShieldElement(int slot) {
        this.slot = slot;
    }

    public int slot() {
        return this.slot;
    }

    /**
     * 把游戏里的元素对象映射到槽位。
     *
     * <p>用引用比较：元素是注册表里的单例，`==` 就是最准的判定。
     *
     * @return 认不出来时返回 {@link #PHYSICAL}（当作物理处理，不会误吃元素加成）
     */
    public static ShieldElement of(@Nullable GenshinElement element) {
        if (element == null) {
            return PHYSICAL;
        }
        if (element == ModElements.PYRO.get()) return PYRO;
        if (element == ModElements.HYDRO.get()) return HYDRO;
        if (element == ModElements.ELECTRO.get()) return ELECTRO;
        if (element == ModElements.CYRO.get()) return CYRO;
        if (element == ModElements.ANEMO.get()) return ANEMO;
        if (element == ModElements.GEO.get()) return GEO;
        if (element == ModElements.DENDRO.get()) return DENDRO;
        if (element == ModElements.FROZEN.get()) return FROZEN;
        return PHYSICAL;
    }
}
