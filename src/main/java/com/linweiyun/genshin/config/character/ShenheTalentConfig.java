package com.linweiyun.genshin.config.character;

import com.linweiyun.genshin.config.util.StringDoubleValue;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ShenheTalentConfig {

    /**
     * 倍率项在配置文件里的原始 key → 值对象。
     *
     * <p>给 LDLib2 的配置页（{@code ShenheConfigUI}）用：页面不认识字段名，
     * 只按 key 取值 / 写值，写下去的效果和手改 TOML 完全一样
     * （{@link StringDoubleValue#set(double)} 写的就是 {@code ModConfigSpec.ConfigValue}）。
     *
     * <p>顺序 = 页面上的显示顺序，所以是 {@link LinkedHashMap}。
     */
    private static final Map<String, StringDoubleValue> BY_KEY = new LinkedHashMap<>();

    /** 每项属于哪个分组（对应配置文件里的 {@code normal-attack} / {@code elemental-skill} / {@code elemental-burst}）。 */
    private static final Map<String, String> GROUP_OF_KEY = new LinkedHashMap<>();

    private static final List<String> GROUPS = new ArrayList<>();

    private static final String GROUP_NA = "normal-attack";
    private static final String GROUP_SKILL = "elemental-skill";
    private static final String GROUP_BURST = "elemental-burst";

    /**
     * 普攻倍率表（1~6 段）。
     *
     * <p>⚠️ 第 6 段是给<b>薇斯娜</b>用的：她的 {@code getMaxCombo() == 6}，
     * 表里没有第 6 段就会落到 {@code default -> 0.0}，那一整段普攻恒为 0 伤害。
     * 默认值暂取<b>第 2 段</b>的数值（她的第 6 段本来就复用第 2 段的动画 {@code attack_2}），
     * 官方数值到位后改 TOML 即可，不用动代码。
     */
    public static StringDoubleValue NA_BASE_1, NA_BASE_2, NA_BASE_3, NA_BASE_4, NA_BASE_5, NA_BASE_6;
    public static StringDoubleValue NA_PER_LEVEL_1, NA_PER_LEVEL_2, NA_PER_LEVEL_3, NA_PER_LEVEL_4,
            NA_PER_LEVEL_5, NA_PER_LEVEL_6;

    public static StringDoubleValue SKILL_PRESS_BASE;
    public static StringDoubleValue SKILL_PRESS_PER_LEVEL;
    public static StringDoubleValue SKILL_HOLD_BASE;
    public static StringDoubleValue SKILL_HOLD_PER_LEVEL;
    public static StringDoubleValue ICY_QUILL_BASE;
    public static StringDoubleValue ICY_QUILL_PER_LEVEL;

    public static StringDoubleValue BURST_CAST_BASE;
    public static StringDoubleValue BURST_CAST_PER_LEVEL;
    public static StringDoubleValue BURST_RES_SHRED_BASE;
    public static StringDoubleValue BURST_RES_SHRED_PER_LEVEL;
    public static StringDoubleValue BURST_DOT_BASE;
    public static StringDoubleValue BURST_DOT_PER_LEVEL;

    /** 配置页的取用接口（见 {@link TalentConfigs}）；通用页按角色 id 取到它。 */
    public static final TalentConfigSource SOURCE = new TalentConfigSource() {
        @Override
        public List<String> groups() {
            return ShenheTalentConfig.groups();
        }

        @Override
        public List<String> keysOf(String group) {
            return ShenheTalentConfig.keysOf(group);
        }

        @Override
        public Double getByKey(String key) {
            return ShenheTalentConfig.getByKey(key);
        }

        @Override
        public boolean setByKey(String key, double value) {
            return ShenheTalentConfig.setByKey(key, value);
        }
    };

    static void register(ModConfigSpec.Builder builder) {
        builder.push("talent");

        GROUPS.clear();
        GROUPS.add(GROUP_NA);
        GROUPS.add(GROUP_SKILL);
        GROUPS.add(GROUP_BURST);

        builder.push("normal-attack");
        NA_BASE_1 = define(builder, GROUP_NA, "nab1", 0.433);
        NA_BASE_2 = define(builder, GROUP_NA, "nab2", 0.402);
        NA_BASE_3 = define(builder, GROUP_NA, "nab3", 0.533);
        NA_BASE_4 = define(builder, GROUP_NA, "nab4", 0.263);
        NA_BASE_5 = define(builder, GROUP_NA, "nab5", 0.656);
        // 第 6 段（薇斯娜）—— 需求：10 级 = 142.8%。按本表一贯的「每级成长 ≈ 基础 × 0.1117」反推：
        //   0.7122 + 9 × 0.07954 = 1.4281 → 142.8%（官方数值到位后改这一项或 TOML 即可）
        NA_BASE_6 = define(builder, GROUP_NA, "nab6", 0.7122);
        NA_PER_LEVEL_1 = define(builder, GROUP_NA, "nap1", 0.0482);
        NA_PER_LEVEL_2 = define(builder, GROUP_NA, "nap2", 0.0450);
        NA_PER_LEVEL_3 = define(builder, GROUP_NA, "nap3", 0.0595);
        NA_PER_LEVEL_4 = define(builder, GROUP_NA, "nap4", 0.0294);
        NA_PER_LEVEL_5 = define(builder, GROUP_NA, "nap5", 0.0733);
        // 第 6 段（薇斯娜）—— 10 级 142.8%（推导见上面 nab6 的注释）
        NA_PER_LEVEL_6 = define(builder, GROUP_NA, "nap6", 0.07954);
        builder.pop();

        builder.push("elemental-skill");
        SKILL_PRESS_BASE = define(builder, GROUP_SKILL, "shehe-press-damage", 1.39);
        SKILL_PRESS_PER_LEVEL = define(builder, GROUP_SKILL, "shehe-press-per-level", 0.1308);
        SKILL_HOLD_BASE = define(builder, GROUP_SKILL, "shehe-hold-damage", 1.888);
        SKILL_HOLD_PER_LEVEL = define(builder, GROUP_SKILL, "shehe-hold-per-level", 0.177);
        ICY_QUILL_BASE = define(builder, GROUP_SKILL, "shehe-dmg-bonus", 0.457);
        ICY_QUILL_PER_LEVEL = define(builder, GROUP_SKILL, "shehe-dmg-bonus-per-level", 0.04275);
        builder.pop();

        builder.push("elemental-burst");
        BURST_CAST_BASE = define(builder, GROUP_BURST, "shehe-burst-skill-damage", 1.01);
        BURST_CAST_PER_LEVEL = define(builder, GROUP_BURST, "shehe-burst-skill-damage-per-level", 0.0942);
        BURST_RES_SHRED_BASE = define(builder, GROUP_BURST, "shehe-burst-res-decrease", 0.06);
        BURST_RES_SHRED_PER_LEVEL = define(builder, GROUP_BURST, "shehe-burst-res-decrease-per-level", 0.0075);
        BURST_DOT_BASE = define(builder, GROUP_BURST, "shehe-burst-dot", 0.331);
        BURST_DOT_PER_LEVEL = define(builder, GROUP_BURST, "shehe-burst-dot-per-level", 0.0311);
        builder.pop();

        builder.pop();
    }

    /** 定义一项并登记进页面的取用表（范围 0 ~ 100）。 */
    private static StringDoubleValue define(ModConfigSpec.Builder builder, String group, String key, double defaultValue) {
        StringDoubleValue value = StringDoubleValue.defineInRange(builder, key, defaultValue, 0.0, 100.0);
        BY_KEY.put(key, value);
        GROUP_OF_KEY.put(key, group);
        return value;
    }

    // ==================== 给配置页用的取用接口 ====================

    /** 页面显示顺序下的全部分组 id。 */
    public static List<String> groups() {
        return Collections.unmodifiableList(GROUPS);
    }

    /** 某个分组里的 key（保持定义顺序）。 */
    public static List<String> keysOf(String group) {
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, String> entry : GROUP_OF_KEY.entrySet()) {
            if (entry.getValue().equals(group)) {
                keys.add(entry.getKey());
            }
        }
        return keys;
    }

    /** 按 key 读倍率；key 不存在返回 null。 */
    public static Double getByKey(String key) {
        StringDoubleValue value = BY_KEY.get(key);
        return value == null ? null : value.get();
    }

    /**
     * 按 key 写倍率。
     *
     * <p>写的是 {@code ModConfigSpec.ConfigValue}，所以<b>服务端</b>调用才真正影响伤害
     * （角色倍率表是 {@code ModConfig.Type.COMMON}，两端各有一份、不自动同步）。
     * 客户端那边改完要发 {@code NetworkManager.setTalentMultiplierToServer} 把服务端那份也写掉。
     *
     * @return 写成功（key 存在且值被夹紧后写入）
     */
    public static boolean setByKey(String key, double value) {
        StringDoubleValue target = BY_KEY.get(key);
        if (target == null) return false;
        target.set(value);
        return true;
    }

    /** 全部 key（按定义顺序）。 */
    public static List<String> allKeys() {
        return new ArrayList<>(BY_KEY.keySet());
    }

    /** 五星单手剑普攻逐级倍率表（每段 15 级）。 */
    private static final double[][] NA_TABLE = {
            {0.4042, 0.4371, 0.4700, 0.5170, 0.5499, 0.5875, 0.6392, 0.6909, 0.7426, 0.7990, 0.8554, 0.9118, 0.9682, 1.0246, 1.0810},
            {0.4868, 0.5264, 0.5660, 0.6226, 0.6622, 0.7075, 0.7698, 0.8320, 0.8943, 0.9622, 1.0301, 1.0980, 1.1660, 1.2339, 1.3018},
            {0.2808, 0.3036, 0.3265, 0.3592, 0.3820, 0.4081, 0.4440, 0.4800, 0.5159, 0.5551, 0.5942, 0.6334, 0.6726, 0.7118, 0.7510},
            {0.5917, 0.6398, 0.6880, 0.7568, 0.8050, 0.8600, 0.9357, 1.0114, 1.0870, 1.1696, 1.2522, 1.3347, 1.4173, 1.4998, 1.5824},
            {0.6244, 0.6752, 0.7260, 0.7986, 0.8494, 0.9075, 0.9874, 1.0672, 1.1471, 1.2342, 1.3213, 1.4084, 1.4956, 1.5827, 1.6698},
            {0.7224, 0.7812, 0.8400, 0.9240, 0.9828, 1.0500, 1.1424, 1.2348, 1.3272, 1.4280, 1.5288, 1.6296, 1.7304, 1.8312, 1.9320},
    };

    /** 普攻某段、某级的倍率（占攻击力）；越界时钳到表内。 */
    public static double getNormalAttack(int segment, int level) {
        if (segment < 1 || segment > NA_TABLE.length) {
            return 0.0;
        }
        double[] row = NA_TABLE[segment - 1];
        return row[Math.max(1, Math.min(row.length, level)) - 1];
    }

    /** 五星单手剑重击逐级倍率（占攻击力）。 */
    private static final double[] CHARGED_TABLE = {
            1.3304, 1.4387, 1.5470, 1.7017, 1.8100, 1.9338, 2.1039, 2.2741, 2.4443, 2.6299,
            2.8155, 3.0012, 3.1868, 3.3725, 3.5581
    };

    public static double getChargedAttack(int level) {
        return CHARGED_TABLE[Math.max(1, Math.min(CHARGED_TABLE.length, level)) - 1];
    }

    public static double getNABase(int segment) {
        return switch (segment) {
            case 1 -> NA_BASE_1.get();
            case 2 -> NA_BASE_2.get();
            case 3 -> NA_BASE_3.get();
            case 4 -> NA_BASE_4.get();
            case 5 -> NA_BASE_5.get();
            // 第 6 段：薇斯娜 getMaxCombo()==6；缺这一档会落到 0.0，那一段普攻 0 伤害。
            case 6 -> NA_BASE_6.get();
            default -> 0.0;
        };
    }

    public static double getNAPerLevel(int segment) {
        return switch (segment) {
            case 1 -> NA_PER_LEVEL_1.get();
            case 2 -> NA_PER_LEVEL_2.get();
            case 3 -> NA_PER_LEVEL_3.get();
            case 4 -> NA_PER_LEVEL_4.get();
            case 5 -> NA_PER_LEVEL_5.get();
            case 6 -> NA_PER_LEVEL_6.get();
            default -> 0.0;
        };
    }

    public static float getSkillPressDamage(int skillLevel) {
        return (float) (SKILL_PRESS_BASE.get() + SKILL_PRESS_PER_LEVEL.get() * (skillLevel - 1));
    }

    public static float getSkillHoldDamage(int skillLevel) {
        return (float) (SKILL_HOLD_BASE.get() + SKILL_HOLD_PER_LEVEL.get() * (skillLevel - 1));
    }

    public static float getBurstCastDamage(int burstLevel) {
        return (float) (BURST_CAST_BASE.get() + BURST_CAST_PER_LEVEL.get() * (burstLevel - 1));
    }

    public static float getBurstDotDamage(int burstLevel) {
        return (float) (BURST_DOT_BASE.get() + BURST_DOT_PER_LEVEL.get() * (burstLevel - 1));
    }
}
