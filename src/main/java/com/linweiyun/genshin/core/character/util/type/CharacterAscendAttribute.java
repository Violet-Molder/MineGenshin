package com.linweiyun.genshin.core.character.util.type;

import java.util.Set;

public enum CharacterAscendAttribute {
    ATK("攻击力", Category.PERCENT),
    HP("生命值", Category.PERCENT),
    DEF("防御力", Category.PERCENT),
    CR("暴击率", Category.BASE),
    CDG("暴击伤害", Category.BASE),
    HB("治疗加成", Category.BASE),
    ELEMENTAL_BONUS("元素伤害加成", Category.BASE),
    EM("元素精通", Category.BASE),
    ER("元素充能效率", Category.BASE);

    public enum Category { PERCENT, BASE }

    private final String displayName;
    private final Category category;

    CharacterAscendAttribute(String displayName, Category category) {
        this.displayName = displayName;
        this.category = category;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Category getCategory() {
        return category;
    }

    public static final Set<CharacterAscendAttribute> PERCENT_STATS =
            Set.of(ATK, HP, DEF);
}