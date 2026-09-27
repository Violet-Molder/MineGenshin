package com.linweiyun.genshin.config.artifact;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ArtifactLevelConfig {
    // ====================== 3星 12项 ======================
    public static ModConfigSpec.IntValue XP_3STAR_00, XP_3STAR_01, XP_3STAR_02, XP_3STAR_03, XP_3STAR_04, XP_3STAR_05;
    public static ModConfigSpec.IntValue XP_3STAR_06, XP_3STAR_07, XP_3STAR_08, XP_3STAR_09, XP_3STAR_10, XP_3STAR_11;

    // ====================== 4星 16项 ======================
    public static ModConfigSpec.IntValue XP_4STAR_00, XP_4STAR_01, XP_4STAR_02, XP_4STAR_03, XP_4STAR_04, XP_4STAR_05, XP_4STAR_06, XP_4STAR_07;
    public static ModConfigSpec.IntValue XP_4STAR_08, XP_4STAR_09, XP_4STAR_10, XP_4STAR_11, XP_4STAR_12, XP_4STAR_13, XP_4STAR_14, XP_4STAR_15;

    // ====================== 5星 20项 ======================
    public static ModConfigSpec.IntValue XP_5STAR_00, XP_5STAR_01, XP_5STAR_02, XP_5STAR_03, XP_5STAR_04, XP_5STAR_05, XP_5STAR_06, XP_5STAR_07, XP_5STAR_08, XP_5STAR_09;
    public static ModConfigSpec.IntValue XP_5STAR_10, XP_5STAR_11, XP_5STAR_12, XP_5STAR_13, XP_5STAR_14, XP_5STAR_15, XP_5STAR_16, XP_5STAR_17, XP_5STAR_18, XP_5STAR_19;


    static void register(ModConfigSpec.Builder builder) {
        builder.push("levelUpExp");

        // ========== 3星 ==========
        builder.push("3star");
        XP_3STAR_00 = builder.translation("minegenshin.configuration.level.0t1").comment("Lv.0 -> Lv.1").defineInRange("0t1", 1800, 0, 10000000);
        XP_3STAR_01 = builder.translation("minegenshin.configuration.level.1t2").comment("Lv.1 -> Lv.2").defineInRange("1t2", 2225, 0, 10000000);
        XP_3STAR_02 = builder.translation("minegenshin.configuration.level.2t3").comment("Lv.2 -> Lv.3").defineInRange("2t3", 2650, 0, 10000000);
        XP_3STAR_03 = builder.translation("minegenshin.configuration.level.3t4").comment("Lv.3 -> Lv.4").defineInRange("3t4", 3100, 0, 10000000);
        XP_3STAR_04 = builder.translation("minegenshin.configuration.level.4t5").comment("Lv.4 -> Lv.5").defineInRange("4t5", 3550, 0, 10000000);
        XP_3STAR_05 = builder.translation("minegenshin.configuration.level.5t6").comment("Lv.5 -> Lv.6").defineInRange("5t6", 4000, 0, 10000000);
        XP_3STAR_06 = builder.translation("minegenshin.configuration.level.6t7").comment("Lv.6 -> Lv.7").defineInRange("6t7", 4500, 0, 10000000);
        XP_3STAR_07 = builder.translation("minegenshin.configuration.level.7t8").comment("Lv.7 -> Lv.8").defineInRange("7t8", 5000, 0, 10000000);
        XP_3STAR_08 = builder.translation("minegenshin.configuration.level.8t9").comment("Lv.8 -> Lv.9").defineInRange("8t9", 5525, 0, 10000000);
        XP_3STAR_09 = builder.translation("minegenshin.configuration.level.9t10").comment("Lv.9 -> Lv.10").defineInRange("9t10", 6075, 0, 10000000);
        XP_3STAR_10 = builder.translation("minegenshin.configuration.level.10t11").comment("Lv.10 -> Lv.11").defineInRange("10t11", 6625, 0, 10000000);
        XP_3STAR_11 = builder.translation("minegenshin.configuration.level.11t12").comment("Lv.11 -> Lv.12").defineInRange("11t12", 7225, 0, 10000000);
        builder.pop();

        // ========== 4星 ==========
        builder.push("4star");
        XP_4STAR_00 = builder.translation("minegenshin.configuration.level.0t1").comment("Lv.0 -> Lv.1").defineInRange("0t1", 2400, 0, 10000000);
        XP_4STAR_01 = builder.translation("minegenshin.configuration.level.1t2").comment("Lv.1 -> Lv.2").defineInRange("1t2", 2975, 0, 10000000);
        XP_4STAR_02 = builder.translation("minegenshin.configuration.level.2t3").comment("Lv.2 -> Lv.3").defineInRange("2t3", 3550, 0, 10000000);
        XP_4STAR_03 = builder.translation("minegenshin.configuration.level.3t4").comment("Lv.3 -> Lv.4").defineInRange("3t4", 4125, 0, 10000000);
        XP_4STAR_04 = builder.translation("minegenshin.configuration.level.4t5").comment("Lv.4 -> Lv.5").defineInRange("4t5", 4725, 0, 10000000);
        XP_4STAR_05 = builder.translation("minegenshin.configuration.level.5t6").comment("Lv.5 -> Lv.6").defineInRange("5t6", 5350, 0, 10000000);
        XP_4STAR_06 = builder.translation("minegenshin.configuration.level.6t7").comment("Lv.6 -> Lv.7").defineInRange("6t7", 6000, 0, 10000000);
        XP_4STAR_07 = builder.translation("minegenshin.configuration.level.7t8").comment("Lv.7 -> Lv.8").defineInRange("7t8", 6675, 0, 10000000);
        XP_4STAR_08 = builder.translation("minegenshin.configuration.level.8t9").comment("Lv.8 -> Lv.9").defineInRange("8t9", 7375, 0, 10000000);
        XP_4STAR_09 = builder.translation("minegenshin.configuration.level.9t10").comment("Lv.9 -> Lv.10").defineInRange("9t10", 8100, 0, 10000000);
        XP_4STAR_10 = builder.translation("minegenshin.configuration.level.10t11").comment("Lv.10 -> Lv.11").defineInRange("10t11", 8850, 0, 10000000);
        XP_4STAR_11 = builder.translation("minegenshin.configuration.level.11t12").comment("Lv.11 -> Lv.12").defineInRange("11t12", 9625, 0, 10000000);
        XP_4STAR_12 = builder.translation("minegenshin.configuration.level.12t13").comment("Lv.12 -> Lv.13").defineInRange("12t13", 10425, 0, 10000000);
        XP_4STAR_13 = builder.translation("minegenshin.configuration.level.13t14").comment("Lv.13 -> Lv.14").defineInRange("13t14", 12125, 0, 10000000);
        XP_4STAR_14 = builder.translation("minegenshin.configuration.level.14t15").comment("Lv.14 -> Lv.15").defineInRange("14t15", 14075, 0, 10000000);
        XP_4STAR_15 = builder.translation("minegenshin.configuration.level.15t16").comment("Lv.15 -> Lv.16").defineInRange("15t16", 16300, 0, 10000000);
        builder.pop();

        // ========== 5星 ==========
        builder.push("5star");
        XP_5STAR_00 = builder.translation("minegenshin.configuration.level.0t1").comment("Lv.0 -> Lv.1").defineInRange("0t1", 3000, 0, 10000000);
        XP_5STAR_01 = builder.translation("minegenshin.configuration.level.1t2").comment("Lv.1 -> Lv.2").defineInRange("1t2", 3725, 0, 10000000);
        XP_5STAR_02 = builder.translation("minegenshin.configuration.level.2t3").comment("Lv.2 -> Lv.3").defineInRange("2t3", 4425, 0, 10000000);
        XP_5STAR_03 = builder.translation("minegenshin.configuration.level.3t4").comment("Lv.3 -> Lv.4").defineInRange("3t4", 5150, 0, 10000000);
        XP_5STAR_04 = builder.translation("minegenshin.configuration.level.4t5").comment("Lv.4 -> Lv.5").defineInRange("4t5", 5900, 0, 10000000);
        XP_5STAR_05 = builder.translation("minegenshin.configuration.level.5t6").comment("Lv.5 -> Lv.6").defineInRange("5t6", 6675, 0, 10000000);
        XP_5STAR_06 = builder.translation("minegenshin.configuration.level.6t7").comment("Lv.6 -> Lv.7").defineInRange("6t7", 7500, 0, 10000000);
        XP_5STAR_07 = builder.translation("minegenshin.configuration.level.7t8").comment("Lv.7 -> Lv.8").defineInRange("7t8", 8350, 0, 10000000);
        XP_5STAR_08 = builder.translation("minegenshin.configuration.level.8t9").comment("Lv.8 -> Lv.9").defineInRange("8t9", 9225, 0, 10000000);
        XP_5STAR_09 = builder.translation("minegenshin.configuration.level.9t10").comment("Lv.9 -> Lv.10").defineInRange("9t10", 10125, 0, 10000000);
        XP_5STAR_10 = builder.translation("minegenshin.configuration.level.10t11").comment("Lv.10 -> Lv.11").defineInRange("10t11", 11050, 0, 10000000);
        XP_5STAR_11 = builder.translation("minegenshin.configuration.level.11t12").comment("Lv.11 -> Lv.12").defineInRange("11t12", 12025, 0, 10000000);
        XP_5STAR_12 = builder.translation("minegenshin.configuration.level.12t13").comment("Lv.12 -> Lv.13").defineInRange("12t13", 13025, 0, 10000000);
        XP_5STAR_13 = builder.translation("minegenshin.configuration.level.13t14").comment("Lv.13 -> Lv.14").defineInRange("13t14", 15150, 0, 10000000);
        XP_5STAR_14 = builder.translation("minegenshin.configuration.level.14t15").comment("Lv.14 -> Lv.15").defineInRange("14t15", 17600, 0, 10000000);
        XP_5STAR_15 = builder.translation("minegenshin.configuration.level.15t16").comment("Lv.15 -> Lv.16").defineInRange("15t16", 20375, 0, 10000000);
        XP_5STAR_16 = builder.translation("minegenshin.configuration.level.16t17").comment("Lv.16 -> Lv.17").defineInRange("16t17", 23500, 0, 10000000);
        XP_5STAR_17 = builder.translation("minegenshin.configuration.level.17t18").comment("Lv.17 -> Lv.18").defineInRange("17t18", 27050, 0, 10000000);
        XP_5STAR_18 = builder.translation("minegenshin.configuration.level.18t19").comment("Lv.18 -> Lv.19").defineInRange("18t19", 31050, 0, 10000000);
        XP_5STAR_19 = builder.translation("minegenshin.configuration.level.19t20").comment("Lv.19 -> Lv.20").defineInRange("19t20", 35575, 0, 10000000);
        builder.pop();

        builder.pop();
    }

    public static int get3Star(int fromLevel) {
        return switch (fromLevel) {
            case 0 -> XP_3STAR_00.get();
            case 1 -> XP_3STAR_01.get();
            case 2 -> XP_3STAR_02.get();
            case 3 -> XP_3STAR_03.get();
            case 4 -> XP_3STAR_04.get();
            case 5 -> XP_3STAR_05.get();
            case 6 -> XP_3STAR_06.get();
            case 7 -> XP_3STAR_07.get();
            case 8 -> XP_3STAR_08.get();
            case 9 -> XP_3STAR_09.get();
            case 10 -> XP_3STAR_10.get();
            case 11 -> XP_3STAR_11.get();
            default -> 0;
        };
    }

    public static int get4Star(int fromLevel) {
        return switch (fromLevel) {
            case 0 -> XP_4STAR_00.get();
            case 1 -> XP_4STAR_01.get();
            case 2 -> XP_4STAR_02.get();
            case 3 -> XP_4STAR_03.get();
            case 4 -> XP_4STAR_04.get();
            case 5 -> XP_4STAR_05.get();
            case 6 -> XP_4STAR_06.get();
            case 7 -> XP_4STAR_07.get();
            case 8 -> XP_4STAR_08.get();
            case 9 -> XP_4STAR_09.get();
            case 10 -> XP_4STAR_10.get();
            case 11 -> XP_4STAR_11.get();
            case 12 -> XP_4STAR_12.get();
            case 13 -> XP_4STAR_13.get();
            case 14 -> XP_4STAR_14.get();
            case 15 -> XP_4STAR_15.get();
            default -> 0;
        };
    }

    public static int get5Star(int fromLevel) {
        return switch (fromLevel) {
            case 0 -> XP_5STAR_00.get();
            case 1 -> XP_5STAR_01.get();
            case 2 -> XP_5STAR_02.get();
            case 3 -> XP_5STAR_03.get();
            case 4 -> XP_5STAR_04.get();
            case 5 -> XP_5STAR_05.get();
            case 6 -> XP_5STAR_06.get();
            case 7 -> XP_5STAR_07.get();
            case 8 -> XP_5STAR_08.get();
            case 9 -> XP_5STAR_09.get();
            case 10 -> XP_5STAR_10.get();
            case 11 -> XP_5STAR_11.get();
            case 12 -> XP_5STAR_12.get();
            case 13 -> XP_5STAR_13.get();
            case 14 -> XP_5STAR_14.get();
            case 15 -> XP_5STAR_15.get();
            case 16 -> XP_5STAR_16.get();
            case 17 -> XP_5STAR_17.get();
            case 18 -> XP_5STAR_18.get();
            case 19 -> XP_5STAR_19.get();
            default -> 0;
        };
    }

    /**
     * 计算从 currentLv 升到 targetLv 所需总经验
     * @param star 星级 3/4/5
     * @param currentLv 当前等级
     * @param targetLv 目标等级
     * @return 总经验
     */
    public static long getTotalExp(int star, int currentLv, int targetLv) {
        if (targetLv <= currentLv) return 0;
        long total = 0;
        for (int lv = currentLv; lv < targetLv; lv++) {
            total += switch (star) {
                case 3 -> get3Star(lv);
                case 4 -> get4Star(lv);
                case 5 -> get5Star(lv);
                default -> 0;
            };
        }
        return total;
    }
}
