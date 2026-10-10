package com.linweiyun.genshin.core.system.combat.damage;

import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;

/**
 * 伤害日志里用的<b>中文名字</b>（元素 / 攻击类型 / 属性）。
 *
 * <p>只服务日志 —— 不参与任何计算，也不走 i18n（服务端日志的语言应该稳定，
 * 不该跟着玩家客户端语言变）。名字口径和 lang 文件里的加成条目对齐
 * （`火元素伤害加成`、`普通攻击伤害加成`…）。
 */
public final class DamageLabels {

    private DamageLabels() {
    }

    /** 元素名：`anemo` → 风。 */
    public static String element(GenshinElement element) {
        if (element == null) {
            return "未知";
        }
        return switch (element.getId()) {
            case "anemo" -> "风";
            case "pyro" -> "火";
            case "hydro" -> "水";
            case "electro" -> "雷";
            case "cryo" -> "冰";
            case "geo" -> "岩";
            case "dendro" -> "草";
            case "fysikos", "physical" -> "物理";
            default -> element.getId();
        };
    }

    /** 元素伤害加成的名字：风 → 风元素伤害加成。 */
    public static String elementBonus(GenshinElement element) {
        String name = element(element);
        return "物理".equals(name) ? "物理伤害加成" : name + "元素伤害加成";
    }

    /** 攻击类型加成 / 反应的名字。 */
    public static String attackType(AttackType attackType) {
        if (attackType == null) {
            return "未知";
        }
        return switch (attackType) {
            case NORMAL_ATTACK -> "普通攻击";
            case CHARGED_ATTACK -> "重击";
            case PLUNGING_ATTACK -> "下落攻击";
            case ELEMENTAL_SKILL -> "元素战技";
            case ELEMENTAL_BURST -> "元素爆发";
            case LUNAR_CHARGED -> "月曜反应";
            case ELECTRO_CHARGED -> "感电反应";
            case SWIRL -> "扩散反应";
            case STELLAR_SWIRL -> "星烁反应";
            case STELLAR_CONDUCE -> "星超导反应";
            case SPECIAL -> "特殊";
            case MONSTER -> "怪物";
        };
    }

    /** 伤害加成条目的名字：普通攻击 → 普通攻击伤害加成。 */
    public static String attackTypeBonus(AttackType attackType) {
        String name = attackType(attackType);
        return name.endsWith("反应") ? name + "伤害加成" : name + "伤害加成";
    }

    /** 属性名（基础区里那个「谁 × 谁的倍率」）。 */
    public static String attribute(String code) {
        return switch (code) {
            case "atk" -> "攻击力";
            case "hp" -> "生命值";
            case "def" -> "防御力";
            case "em" -> "元素精通";
            default -> code;
        };
    }
}
