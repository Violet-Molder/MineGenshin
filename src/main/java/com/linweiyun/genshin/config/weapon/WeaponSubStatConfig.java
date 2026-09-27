package com.linweiyun.genshin.config.weapon;

import com.linweiyun.genshin.config.util.StringDoubleValue;
import net.neoforged.neoforge.common.ModConfigSpec;

public class WeaponSubStatConfig {

    // 副词条等级乘数
    public static StringDoubleValue MULT_01, MULT_02, MULT_03, MULT_04, MULT_05, MULT_06, MULT_07, MULT_08, MULT_09, MULT_10;
    public static StringDoubleValue MULT_11, MULT_12, MULT_13, MULT_14, MULT_15, MULT_16, MULT_17, MULT_18, MULT_19;

    // 五星 tier44
    public static StringDoubleValue SUB_5_TIER44_CRIT_RATE, SUB_5_TIER44_CRIT_DMG, SUB_5_TIER44_ENERGY_RECHARGE;
    public static StringDoubleValue SUB_5_TIER44_ATK_PERCENT, SUB_5_TIER44_PHYSICAL_PERCENT, SUB_5_TIER44_HP_PERCENT;
    public static ModConfigSpec.IntValue SUB_5_TIER44_ELEMENTAL_MASTERY;
    // 五星 tier46
    public static StringDoubleValue SUB_5_TIER46_CRIT_RATE, SUB_5_TIER46_CRIT_DMG, SUB_5_TIER46_ENERGY_RECHARGE;
    public static StringDoubleValue SUB_5_TIER46_ATK_PERCENT, SUB_5_TIER46_PHYSICAL_PERCENT, SUB_5_TIER46_HP_PERCENT;
    public static ModConfigSpec.IntValue SUB_5_TIER46_ELEMENTAL_MASTERY;
    // 五星 tier48
    public static StringDoubleValue SUB_5_TIER48_CRIT_RATE, SUB_5_TIER48_CRIT_DMG, SUB_5_TIER48_ENERGY_RECHARGE;
    public static StringDoubleValue SUB_5_TIER48_ATK_PERCENT, SUB_5_TIER48_PHYSICAL_PERCENT, SUB_5_TIER48_HP_PERCENT;
    public static ModConfigSpec.IntValue SUB_5_TIER48_ELEMENTAL_MASTERY;
    // 五星 tier49
    public static StringDoubleValue SUB_5_TIER49_CRIT_RATE, SUB_5_TIER49_CRIT_DMG, SUB_5_TIER49_ENERGY_RECHARGE;
    public static StringDoubleValue SUB_5_TIER49_ATK_PERCENT, SUB_5_TIER49_PHYSICAL_PERCENT;
    public static ModConfigSpec.IntValue SUB_5_TIER49_ELEMENTAL_MASTERY;

    // 四星 tier41
    public static StringDoubleValue SUB_4_TIER41_CRIT_RATE, SUB_4_TIER41_CRIT_DMG, SUB_4_TIER41_ENERGY_RECHARGE;
    public static StringDoubleValue SUB_4_TIER41_ATK_PERCENT, SUB_4_TIER41_PHYSICAL_PERCENT;
    public static ModConfigSpec.IntValue SUB_4_TIER41_ELEMENTAL_MASTERY;
    // 四星 tier42
    public static StringDoubleValue SUB_4_TIER42_CRIT_RATE, SUB_4_TIER42_CRIT_DMG, SUB_4_TIER42_ENERGY_RECHARGE;
    public static StringDoubleValue SUB_4_TIER42_ATK_PERCENT, SUB_4_TIER42_PHYSICAL_PERCENT;
    public static ModConfigSpec.IntValue SUB_4_TIER42_ELEMENTAL_MASTERY;
    // 四星 tier44
    public static StringDoubleValue SUB_4_TIER44_CRIT_RATE, SUB_4_TIER44_CRIT_DMG, SUB_4_TIER44_ENERGY_RECHARGE;
    public static StringDoubleValue SUB_4_TIER44_ATK_PERCENT, SUB_4_TIER44_PHYSICAL_PERCENT;
    public static ModConfigSpec.IntValue SUB_4_TIER44_ELEMENTAL_MASTERY;
    // 四星 tier45
    public static StringDoubleValue SUB_4_TIER45_CRIT_RATE, SUB_4_TIER45_CRIT_DMG, SUB_4_TIER45_ENERGY_RECHARGE;
    public static StringDoubleValue SUB_4_TIER45_ATK_PERCENT, SUB_4_TIER45_PHYSICAL_PERCENT;
    public static ModConfigSpec.IntValue SUB_4_TIER45_ELEMENTAL_MASTERY;

    // 三星 tier38
    public static StringDoubleValue SUB_3_TIER38_CRIT_RATE, SUB_3_TIER38_CRIT_DMG, SUB_3_TIER38_ENERGY_RECHARGE;
    public static StringDoubleValue SUB_3_TIER38_ATK_PERCENT, SUB_3_TIER38_PHYSICAL_PERCENT;
    public static ModConfigSpec.IntValue SUB_3_TIER38_ELEMENTAL_MASTERY;
    // 三星 tier39
    public static StringDoubleValue SUB_3_TIER39_CRIT_RATE, SUB_3_TIER39_CRIT_DMG, SUB_3_TIER39_ENERGY_RECHARGE;
    public static StringDoubleValue SUB_3_TIER39_ATK_PERCENT, SUB_3_TIER39_PHYSICAL_PERCENT;
    public static ModConfigSpec.IntValue SUB_3_TIER39_ELEMENTAL_MASTERY;
    // 三星 tier40
    public static StringDoubleValue SUB_3_TIER40_CRIT_RATE, SUB_3_TIER40_CRIT_DMG, SUB_3_TIER40_ENERGY_RECHARGE;
    public static StringDoubleValue SUB_3_TIER40_ATK_PERCENT, SUB_3_TIER40_PHYSICAL_PERCENT;
    public static ModConfigSpec.IntValue SUB_3_TIER40_ELEMENTAL_MASTERY;
    // 三星 tier41
    public static StringDoubleValue SUB_3_TIER41_CRIT_RATE, SUB_3_TIER41_CRIT_DMG, SUB_3_TIER41_ENERGY_RECHARGE;
    public static StringDoubleValue SUB_3_TIER41_ATK_PERCENT, SUB_3_TIER41_PHYSICAL_PERCENT;
    public static ModConfigSpec.IntValue SUB_3_TIER41_ELEMENTAL_MASTERY;

    static void register(ModConfigSpec.Builder builder) {
        builder.push("subStat");

        builder.push("level-coefficient");
        MULT_01 = StringDoubleValue.defineInRange(builder.translation("minegenshin.configuration.level.1"), "lv1", 1.000, 0.0, 100.0);
        MULT_02 = StringDoubleValue.defineInRange(builder.translation("minegenshin.configuration.level.5"), "lv5", 1.162, 0.0, 100.0);
        MULT_03 = StringDoubleValue.defineInRange(builder.translation("minegenshin.configuration.level.10"), "lv10", 1.363, 0.0, 100.0);
        MULT_04 = StringDoubleValue.defineInRange(builder.translation("minegenshin.configuration.level.15"), "lv15", 1.565, 0.0, 100.0);
        MULT_05 = StringDoubleValue.defineInRange(builder.translation("minegenshin.configuration.level.20"), "lv20", 1.767, 0.0, 100.0);
        MULT_06 = StringDoubleValue.defineInRange(builder.translation("minegenshin.configuration.level.25"), "lv25", 1.969, 0.0, 100.0);
        MULT_07 = StringDoubleValue.defineInRange(builder.translation("minegenshin.configuration.level.30"), "lv30", 2.171, 0.0, 100.0);
        MULT_08 = StringDoubleValue.defineInRange(builder.translation("minegenshin.configuration.level.35"), "lv35", 2.373, 0.0, 100.0);
        MULT_09 = StringDoubleValue.defineInRange(builder.translation("minegenshin.configuration.level.40"), "lv40", 2.575, 0.0, 100.0);
        MULT_10 = StringDoubleValue.defineInRange(builder.translation("minegenshin.configuration.level.45"), "lv45", 2.777, 0.0, 100.0);
        MULT_11 = StringDoubleValue.defineInRange(builder.translation("minegenshin.configuration.level.50"), "lv50", 2.979, 0.0, 100.0);
        MULT_12 = StringDoubleValue.defineInRange(builder.translation("minegenshin.configuration.level.55"), "lv55", 3.181, 0.0, 100.0);
        MULT_13 = StringDoubleValue.defineInRange(builder.translation("minegenshin.configuration.level.60"), "lv60", 3.383, 0.0, 100.0);
        MULT_14 = StringDoubleValue.defineInRange(builder.translation("minegenshin.configuration.level.65"), "lv65", 3.585, 0.0, 100.0);
        MULT_15 = StringDoubleValue.defineInRange(builder.translation("minegenshin.configuration.level.70"), "lv70", 3.786, 0.0, 100.0);
        MULT_16 = StringDoubleValue.defineInRange(builder.translation("minegenshin.configuration.level.75"), "lv75", 3.988, 0.0, 100.0);
        MULT_17 = StringDoubleValue.defineInRange(builder.translation("minegenshin.configuration.level.80"), "lv80", 4.190, 0.0, 100.0);
        MULT_18 = StringDoubleValue.defineInRange(builder.translation("minegenshin.configuration.level.85"), "lv85", 4.392, 0.0, 100.0);
        MULT_19 = StringDoubleValue.defineInRange(builder.translation("minegenshin.configuration.level.90"), "lv90", 4.594, 0.0, 100.0);
        builder.pop();

        builder.push("5star");

        builder.push("tier1");
        SUB_5_TIER44_CRIT_RATE = StringDoubleValue.defineInRange(builder, "crit-rate", 0.096, 0.0, 1.0);
        SUB_5_TIER44_CRIT_DMG = StringDoubleValue.defineInRange(builder, "crit-dmg", 0.192, 0.0, 3.0);
        SUB_5_TIER44_ENERGY_RECHARGE = StringDoubleValue.defineInRange(builder, "energy-recharge", 0.160, 0.0, 1.0);
        SUB_5_TIER44_ATK_PERCENT = StringDoubleValue.defineInRange(builder, "atk", 0.144, 0.0, 1.0);
        SUB_5_TIER44_PHYSICAL_PERCENT = StringDoubleValue.defineInRange(builder, "physical-bonus", 0.180, 0.0, 1.0);
        SUB_5_TIER44_HP_PERCENT = StringDoubleValue.defineInRange(builder, "hp", 0.144, 0.0, 1.0);
        SUB_5_TIER44_ELEMENTAL_MASTERY = builder.defineInRange("elemental-mastery", 58, 0, 1000);
        builder.pop();

        builder.push("tier2");
        SUB_5_TIER46_CRIT_RATE = StringDoubleValue.defineInRange(builder, "crit-rate", 0.072, 0.0, 1.0);
        SUB_5_TIER46_CRIT_DMG = StringDoubleValue.defineInRange(builder, "crit-dmg", 0.144, 0.0, 3.0);
        SUB_5_TIER46_ENERGY_RECHARGE = StringDoubleValue.defineInRange(builder, "energy-recharge", 0.120, 0.0, 1.0);
        SUB_5_TIER46_ATK_PERCENT = StringDoubleValue.defineInRange(builder, "atk", 0.108, 0.0, 1.0);
        SUB_5_TIER46_PHYSICAL_PERCENT = StringDoubleValue.defineInRange(builder, "physical-bonus", 0.135, 0.0, 1.0);
        SUB_5_TIER46_HP_PERCENT = StringDoubleValue.defineInRange(builder, "hp", 0.108, 0.0, 1.0);
        SUB_5_TIER46_ELEMENTAL_MASTERY = builder.defineInRange("elemental-mastery", 43, 0, 1000);
        builder.pop();

        builder.push("tier3");
        SUB_5_TIER48_CRIT_RATE = StringDoubleValue.defineInRange(builder, "crit-rate", 0.048, 0.0, 1.0);
        SUB_5_TIER48_CRIT_DMG = StringDoubleValue.defineInRange(builder, "crit-dmg", 0.096, 0.0, 3.0);
        SUB_5_TIER48_ENERGY_RECHARGE = StringDoubleValue.defineInRange(builder, "energy-recharge", 0.080, 0.0, 1.0);
        SUB_5_TIER48_ATK_PERCENT = StringDoubleValue.defineInRange(builder, "atk", 0.072, 0.0, 1.0);
        SUB_5_TIER48_PHYSICAL_PERCENT = StringDoubleValue.defineInRange(builder, "physical-bonus", 0.090, 0.0, 1.0);
        SUB_5_TIER48_HP_PERCENT = StringDoubleValue.defineInRange(builder, "hp", 0.072, 0.0, 1.0);
        SUB_5_TIER48_ELEMENTAL_MASTERY = builder.defineInRange("elemental-mastery", 29, 0, 1000);
        builder.pop();

        builder.push("tier4");
        SUB_5_TIER49_CRIT_RATE = StringDoubleValue.defineInRange(builder, "crit-rate", 0.024, 0.0, 1.0);
        SUB_5_TIER49_CRIT_DMG = StringDoubleValue.defineInRange(builder, "crit-dmg", 0.048, 0.0, 3.0);
        SUB_5_TIER49_ENERGY_RECHARGE = StringDoubleValue.defineInRange(builder, "energy-recharge", 0.040, 0.0, 1.0);
        SUB_5_TIER49_ATK_PERCENT = StringDoubleValue.defineInRange(builder, "atk", 0.036, 0.0, 1.0);
        SUB_5_TIER49_PHYSICAL_PERCENT = StringDoubleValue.defineInRange(builder, "physical-bonus", 0.045, 0.0, 1.0);
        SUB_5_TIER49_ELEMENTAL_MASTERY = builder.defineInRange("elemental-mastery", 14, 0, 1000);
        builder.pop();

        builder.pop();

        builder.push("4star");

        builder.push("tier1");
        SUB_4_TIER41_CRIT_RATE = StringDoubleValue.defineInRange(builder, "crit-rate", 0.080, 0.0, 1.0);
        SUB_4_TIER41_CRIT_DMG = StringDoubleValue.defineInRange(builder, "crit-dmg", 0.160, 0.0, 3.0);
        SUB_4_TIER41_ENERGY_RECHARGE = StringDoubleValue.defineInRange(builder, "energy-recharge", 0.133, 0.0, 1.0);
        SUB_4_TIER41_ATK_PERCENT = StringDoubleValue.defineInRange(builder, "atk", 0.120, 0.0, 1.0);
        SUB_4_TIER41_PHYSICAL_PERCENT = StringDoubleValue.defineInRange(builder, "physical-bonus", 0.150, 0.0, 1.0);
        SUB_4_TIER41_ELEMENTAL_MASTERY = builder.defineInRange("elemental-mastery", 48, 0, 1000);
        builder.pop();

        builder.push("tier2");
        SUB_4_TIER42_CRIT_RATE = StringDoubleValue.defineInRange(builder, "crit-rate", 0.060, 0.0, 1.0);
        SUB_4_TIER42_CRIT_DMG = StringDoubleValue.defineInRange(builder, "crit-dmg", 0.120, 0.0, 3.0);
        SUB_4_TIER42_ENERGY_RECHARGE = StringDoubleValue.defineInRange(builder, "energy-recharge", 0.100, 0.0, 1.0);
        SUB_4_TIER42_ATK_PERCENT = StringDoubleValue.defineInRange(builder, "atk", 0.090, 0.0, 1.0);
        SUB_4_TIER42_PHYSICAL_PERCENT = StringDoubleValue.defineInRange(builder, "physical-bonus", 0.113, 0.0, 1.0);
        SUB_4_TIER42_ELEMENTAL_MASTERY = builder.defineInRange("elemental-mastery", 36, 0, 1000);
        builder.pop();

        builder.push("tier3");
        SUB_4_TIER44_CRIT_RATE = StringDoubleValue.defineInRange(builder, "crit-rate", 0.040, 0.0, 1.0);
        SUB_4_TIER44_CRIT_DMG = StringDoubleValue.defineInRange(builder, "crit-dmg", 0.080, 0.0, 3.0);
        SUB_4_TIER44_ENERGY_RECHARGE = StringDoubleValue.defineInRange(builder, "energy-recharge", 0.067, 0.0, 1.0);
        SUB_4_TIER44_ATK_PERCENT = StringDoubleValue.defineInRange(builder, "atk", 0.060, 0.0, 1.0);
        SUB_4_TIER44_PHYSICAL_PERCENT = StringDoubleValue.defineInRange(builder, "physical-bonus", 0.075, 0.0, 1.0);
        SUB_4_TIER44_ELEMENTAL_MASTERY = builder.defineInRange("elemental-mastery", 24, 0, 1000);
        builder.pop();

        builder.push("tier4");
        SUB_4_TIER45_CRIT_RATE = StringDoubleValue.defineInRange(builder, "crit-rate", 0.020, 0.0, 1.0);
        SUB_4_TIER45_CRIT_DMG = StringDoubleValue.defineInRange(builder, "crit-dmg", 0.040, 0.0, 3.0);
        SUB_4_TIER45_ENERGY_RECHARGE = StringDoubleValue.defineInRange(builder, "energy-recharge", 0.033, 0.0, 1.0);
        SUB_4_TIER45_ATK_PERCENT = StringDoubleValue.defineInRange(builder, "atk", 0.030, 0.0, 1.0);
        SUB_4_TIER45_PHYSICAL_PERCENT = StringDoubleValue.defineInRange(builder, "physical-bonus", 0.038, 0.0, 1.0);
        SUB_4_TIER45_ELEMENTAL_MASTERY = builder.defineInRange("elemental-mastery", 12, 0, 1000);
        builder.pop();

        builder.pop();

        builder.push("3star");

        builder.push("tier1");
        SUB_3_TIER38_CRIT_RATE = StringDoubleValue.defineInRange(builder, "crit-rate", 0.074, 0.0, 1.0);
        SUB_3_TIER38_CRIT_DMG = StringDoubleValue.defineInRange(builder, "crit-dmg", 0.147, 0.0, 3.0);
        SUB_3_TIER38_ENERGY_RECHARGE = StringDoubleValue.defineInRange(builder, "energy-recharge", 0.122, 0.0, 1.0);
        SUB_3_TIER38_ATK_PERCENT = StringDoubleValue.defineInRange(builder, "atk", 0.110, 0.0, 1.0);
        SUB_3_TIER38_PHYSICAL_PERCENT = StringDoubleValue.defineInRange(builder, "physical-bonus", 0.138, 0.0, 1.0);
        SUB_3_TIER38_ELEMENTAL_MASTERY = builder.defineInRange("elemental-mastery", 44, 0, 1000);
        builder.pop();

        builder.push("tier2");
        SUB_3_TIER39_CRIT_RATE = StringDoubleValue.defineInRange(builder, "crit-rate", 0.064, 0.0, 1.0);
        SUB_3_TIER39_CRIT_DMG = StringDoubleValue.defineInRange(builder, "crit-dmg", 0.128, 0.0, 3.0);
        SUB_3_TIER39_ENERGY_RECHARGE = StringDoubleValue.defineInRange(builder, "energy-recharge", 0.107, 0.0, 1.0);
        SUB_3_TIER39_ATK_PERCENT = StringDoubleValue.defineInRange(builder, "atk", 0.096, 0.0, 1.0);
        SUB_3_TIER39_PHYSICAL_PERCENT = StringDoubleValue.defineInRange(builder, "physical-bonus", 0.120, 0.0, 1.0);
        SUB_3_TIER39_ELEMENTAL_MASTERY = builder.defineInRange("elemental-mastery", 38, 0, 1000);
        builder.pop();

        builder.push("tier3");
        SUB_3_TIER40_CRIT_RATE = StringDoubleValue.defineInRange(builder, "crit-rate", 0.048, 0.0, 1.0);
        SUB_3_TIER40_CRIT_DMG = StringDoubleValue.defineInRange(builder, "crit-dmg", 0.096, 0.0, 3.0);
        SUB_3_TIER40_ENERGY_RECHARGE = StringDoubleValue.defineInRange(builder, "energy-recharge", 0.080, 0.0, 1.0);
        SUB_3_TIER40_ATK_PERCENT = StringDoubleValue.defineInRange(builder, "atk", 0.072, 0.0, 1.0);
        SUB_3_TIER40_PHYSICAL_PERCENT = StringDoubleValue.defineInRange(builder, "physical-bonus", 0.090, 0.0, 1.0);
        SUB_3_TIER40_ELEMENTAL_MASTERY = builder.defineInRange("elemental-mastery", 29, 0, 1000);
        builder.pop();

        builder.push("tier4");
        SUB_3_TIER41_CRIT_RATE = StringDoubleValue.defineInRange(builder, "crit-rate", 0.048, 0.0, 1.0);
        SUB_3_TIER41_CRIT_DMG = StringDoubleValue.defineInRange(builder, "crit-dmg", 0.096, 0.0, 3.0);
        SUB_3_TIER41_ENERGY_RECHARGE = StringDoubleValue.defineInRange(builder, "energy-recharge", 0.080, 0.0, 1.0);
        SUB_3_TIER41_ATK_PERCENT = StringDoubleValue.defineInRange(builder, "atk", 0.072, 0.0, 1.0);
        SUB_3_TIER41_PHYSICAL_PERCENT = StringDoubleValue.defineInRange(builder, "physical-bonus", 0.090, 0.0, 1.0);
        SUB_3_TIER41_ELEMENTAL_MASTERY = builder.defineInRange("elemental-mastery", 29, 0, 1000);
        builder.pop();

        builder.pop();
        builder.pop(); // subStat
    }

    public static double getMultiplier(int level) {
        if (level >= 90) return MULT_19.get();
        if (level >= 85) return MULT_18.get();
        if (level >= 80) return MULT_17.get();
        if (level >= 75) return MULT_16.get();
        if (level >= 70) return MULT_15.get();
        if (level >= 65) return MULT_14.get();
        if (level >= 60) return MULT_13.get();
        if (level >= 55) return MULT_12.get();
        if (level >= 50) return MULT_11.get();
        if (level >= 45) return MULT_10.get();
        if (level >= 40) return MULT_09.get();
        if (level >= 35) return MULT_08.get();
        if (level >= 30) return MULT_07.get();
        if (level >= 25) return MULT_06.get();
        if (level >= 20) return MULT_05.get();
        if (level >= 15) return MULT_04.get();
        if (level >= 10) return MULT_03.get();
        if (level >= 5) return MULT_02.get();
        return MULT_01.get();
    }
}