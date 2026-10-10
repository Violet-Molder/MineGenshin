package com.linweiyun.genshin.config.character;

import com.linweiyun.genshin.config.util.StringDoubleValue;
import com.linweiyun.genshin.core.character.sword.miyabi.MiyabiTalent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 星见雅的技能倍率表：逐级一项，配置页与详细属性页都改这里。 */
public class MiyabiTalentConfig {

    private static final String GROUP_NA = "miyabi-normal-attack";
    private static final String GROUP_SKILL = "miyabi-elemental-skill";

    private static final List<String> GROUPS = new ArrayList<>();
    private static final Map<String, StringDoubleValue> BY_KEY = new LinkedHashMap<>();
    private static final Map<String, String> GROUP_OF_KEY = new LinkedHashMap<>();

    private MiyabiTalentConfig() {
    }

    public static void register(ModConfigSpec.Builder builder) {
        builder.push("miyabi-talent");
        GROUPS.add(GROUP_NA);
        GROUPS.add(GROUP_SKILL);
        for (int lv = 1; lv <= 15; lv++) {
            for (int stage = 1; stage <= 5; stage++) {
                define(builder, GROUP_NA, "myb-na" + stage + "-" + lv, MiyabiTalent.defaultNormalAttack(stage, lv));
            }
            define(builder, GROUP_NA, "myb-charged-" + lv, MiyabiTalent.defaultCharged(lv));
            define(builder, GROUP_NA, "myb-frost_moon-" + lv, MiyabiTalent.defaultFrostMoon(lv));
            if (lv <= 13) {
                define(builder, GROUP_NA, "myb-qi-" + lv, MiyabiTalent.defaultSwordQi(lv));
            }
        }
        for (int lv = 1; lv <= 10; lv++) {
            define(builder, GROUP_SKILL, "myb-deep_snow-" + lv, MiyabiTalent.defaultDeepSnow(lv));
            define(builder, GROUP_SKILL, "myb-deep_snow_conduce-" + lv, MiyabiTalent.defaultDeepSnowConduce(lv));
            define(builder, GROUP_SKILL, "myb-flying_snow-" + lv, MiyabiTalent.defaultFlyingSnow(lv));
            define(builder, GROUP_SKILL, "myb-snow_cover-" + lv, MiyabiTalent.snowCoverPerStack(lv));
            define(builder, GROUP_SKILL, "myb-rime-" + lv, MiyabiTalent.rimePerStack(lv));
        }
        builder.pop();
    }

    private static void define(ModConfigSpec.Builder builder, String group, String key, double defaultValue) {
        StringDoubleValue value = StringDoubleValue.defineInRange(builder, key, defaultValue, 0.0, 100.0);
        BY_KEY.put(key, value);
        GROUP_OF_KEY.put(key, group);
    }

    /** 取配置值；没登记就返回 fallback。 */
    public static double value(String key, double fallback) {
        StringDoubleValue value = BY_KEY.get(key);
        return value == null ? fallback : value.get();
    }

    public static final TalentConfigSource SOURCE = new TalentConfigSource() {
        @Override
        public List<String> groups() {
            return List.copyOf(GROUPS);
        }

        @Override
        public List<String> keysOf(String group) {
            List<String> out = new ArrayList<>();
            for (Map.Entry<String, StringDoubleValue> entry : BY_KEY.entrySet()) {
                if (group.equals(GROUP_OF_KEY.get(entry.getKey()))) {
                    out.add(entry.getKey());
                }
            }
            return out;
        }

        @Override
        public Double getByKey(String key) {
            StringDoubleValue value = BY_KEY.get(key);
            return value == null ? null : value.get();
        }

        @Override
        public boolean setByKey(String key, double value) {
            StringDoubleValue target = BY_KEY.get(key);
            if (target == null) {
                return false;
            }
            target.set(value);
            return true;
        }
    };
}