package com.linweiyun.genshin.config.character;

import com.linweiyun.genshin.config.util.StringDoubleValue;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 林薇云的<b>技能倍率表</b> —— 结构与数值照抄申鹤那份（{@link ShenheTalentConfig}），
 * 只把 key 加上 {@code lwy-} 前缀，好和申鹤的 key 在同一份配置里区分开。
 *
 * <p>为什么 key 要加前缀：倍率项是「按 key 全局查」的 —— 配置页写值后要用
 * {@code NetworkManager.setTalentMultiplierToServer(key, value)} 同步给服务端，
 * 服务端只拿到一个 key，得能唯一定位到哪张表（见 {@link TalentConfigs#setByKeyGlobal}）。
 *
 * <p>数值暂时与申鹤完全一致（用户口径：「先把申鹤的技能照抄过来」）；
 * 官方数值到位后改 TOML 即可，不用动代码。
 *
 * <p>分组顺序 = 页面上的显示顺序，所以用 {@link LinkedHashMap}；
 * 页面按 key 取翻译键 {@code gui.minegenshin.character_config.talent.<key>}。
 */
public class LinweiyunTalentConfig {

    /** 倍率项在配置文件里的原始 key → 值对象（配置页按 key 取值 / 写值）。 */
    private static final Map<String, StringDoubleValue> BY_KEY = new LinkedHashMap<>();

    /** 每项属于哪个分组（对应配置文件里的 {@code normal-attack} / {@code elemental-skill} / {@code elemental-burst}）。 */
    private static final Map<String, String> GROUP_OF_KEY = new LinkedHashMap<>();

    private static final List<String> GROUPS = new ArrayList<>();

    private static final String GROUP_NA = "normal-attack";
    private static final String GROUP_SKILL = "elemental-skill";
    private static final String GROUP_BURST = "elemental-burst";

    /** 普攻倍率表（1~6 段；本角色连段数见 {@code LinweiyunSkill#getMaxCombo()}）。 */
    public static StringDoubleValue NA_BASE_1, NA_BASE_2, NA_BASE_3, NA_BASE_4, NA_BASE_5, NA_BASE_6;
    public static StringDoubleValue NA_PER_LEVEL_1, NA_PER_LEVEL_2, NA_PER_LEVEL_3, NA_PER_LEVEL_4,
            NA_PER_LEVEL_5, NA_PER_LEVEL_6;

    public static StringDoubleValue SKILL_PRESS_BASE;
    public static StringDoubleValue SKILL_PRESS_PER_LEVEL;
    public static StringDoubleValue SKILL_HOLD_BASE;
    public static StringDoubleValue SKILL_HOLD_PER_LEVEL;
    /** 照抄申鹤的「冰翎 · 增伤」两档：林薇云暂时没有对应被动，先留着数值。 */
    public static StringDoubleValue DMG_BONUS_BASE;
    public static StringDoubleValue DMG_BONUS_PER_LEVEL;

    public static StringDoubleValue BURST_CAST_BASE;
    public static StringDoubleValue BURST_CAST_PER_LEVEL;
    public static StringDoubleValue BURST_RES_SHRED_BASE;
    public static StringDoubleValue BURST_RES_SHRED_PER_LEVEL;
    public static StringDoubleValue BURST_DOT_BASE;
    public static StringDoubleValue BURST_DOT_PER_LEVEL;

    /** 配置页的取用接口（见 {@link TalentConfigs}）。 */
    public static final TalentConfigSource SOURCE = new TalentConfigSource() {
        @Override
        public List<String> groups() {
            return LinweiyunTalentConfig.groups();
        }

        @Override
        public List<String> keysOf(String group) {
            return LinweiyunTalentConfig.keysOf(group);
        }

        @Override
        public Double getByKey(String key) {
            return LinweiyunTalentConfig.getByKey(key);
        }

        @Override
        public boolean setByKey(String key, double value) {
            return LinweiyunTalentConfig.setByKey(key, value);
        }
    };

    static void register(ModConfigSpec.Builder builder) {
        builder.push("talent");

        GROUPS.clear();
        GROUPS.add(GROUP_NA);
        GROUPS.add(GROUP_SKILL);
        GROUPS.add(GROUP_BURST);

        builder.push("normal-attack");
        NA_BASE_1 = define(builder, GROUP_NA, "lwy-nab1", 0.433);
        NA_BASE_2 = define(builder, GROUP_NA, "lwy-nab2", 0.402);
        NA_BASE_3 = define(builder, GROUP_NA, "lwy-nab3", 0.533);
        NA_BASE_4 = define(builder, GROUP_NA, "lwy-nab4", 0.263);
        NA_BASE_5 = define(builder, GROUP_NA, "lwy-nab5", 0.656);
        NA_BASE_6 = define(builder, GROUP_NA, "lwy-nab6", 0.7122);
        NA_PER_LEVEL_1 = define(builder, GROUP_NA, "lwy-nap1", 0.0482);
        NA_PER_LEVEL_2 = define(builder, GROUP_NA, "lwy-nap2", 0.0450);
        NA_PER_LEVEL_3 = define(builder, GROUP_NA, "lwy-nap3", 0.0595);
        NA_PER_LEVEL_4 = define(builder, GROUP_NA, "lwy-nap4", 0.0294);
        NA_PER_LEVEL_5 = define(builder, GROUP_NA, "lwy-nap5", 0.0733);
        NA_PER_LEVEL_6 = define(builder, GROUP_NA, "lwy-nap6", 0.07954);
        builder.pop();

        builder.push("elemental-skill");
        SKILL_PRESS_BASE = define(builder, GROUP_SKILL, "lwy-press-damage", 1.39);
        SKILL_PRESS_PER_LEVEL = define(builder, GROUP_SKILL, "lwy-press-per-level", 0.1308);
        SKILL_HOLD_BASE = define(builder, GROUP_SKILL, "lwy-hold-damage", 1.888);
        SKILL_HOLD_PER_LEVEL = define(builder, GROUP_SKILL, "lwy-hold-per-level", 0.177);
        DMG_BONUS_BASE = define(builder, GROUP_SKILL, "lwy-dmg-bonus", 0.457);
        DMG_BONUS_PER_LEVEL = define(builder, GROUP_SKILL, "lwy-dmg-bonus-per-level", 0.04275);
        builder.pop();

        builder.push("elemental-burst");
        BURST_CAST_BASE = define(builder, GROUP_BURST, "lwy-burst-skill-damage", 1.01);
        BURST_CAST_PER_LEVEL = define(builder, GROUP_BURST, "lwy-burst-skill-damage-per-level", 0.0942);
        BURST_RES_SHRED_BASE = define(builder, GROUP_BURST, "lwy-burst-res-decrease", 0.06);
        BURST_RES_SHRED_PER_LEVEL = define(builder, GROUP_BURST, "lwy-burst-res-decrease-per-level", 0.0075);
        BURST_DOT_BASE = define(builder, GROUP_BURST, "lwy-burst-dot", 0.331);
        BURST_DOT_PER_LEVEL = define(builder, GROUP_BURST, "lwy-burst-dot-per-level", 0.0311);
        builder.pop();

        builder.pop();
    }

    /** 定义一项并登记进页面的取用表（范围和申鹤那份一致：0 ~ 100）。 */
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

    /** 按 key 写倍率（写的是 {@code ConfigValue}，服务端调用才真正影响伤害）。 */
    public static boolean setByKey(String key, double value) {
        StringDoubleValue target = BY_KEY.get(key);
        if (target == null) {
            return false;
        }
        target.set(value);
        return true;
    }

    /** 全部 key（按定义顺序）。 */
    public static List<String> allKeys() {
        return new ArrayList<>(BY_KEY.keySet());
    }

    public static double getNABase(int segment) {
        return switch (segment) {
            case 1 -> NA_BASE_1.get();
            case 2 -> NA_BASE_2.get();
            case 3 -> NA_BASE_3.get();
            case 4 -> NA_BASE_4.get();
            case 5 -> NA_BASE_5.get();
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
