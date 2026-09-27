package com.linweiyun.genshin.config.character;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;

public class CharacterXpConfig {

    public static ModConfigSpec.IntValue XP_01;
    public static ModConfigSpec.IntValue XP_02;
    public static ModConfigSpec.IntValue XP_03;
    public static ModConfigSpec.IntValue XP_04;
    public static ModConfigSpec.IntValue XP_05;
    public static ModConfigSpec.IntValue XP_06;
    public static ModConfigSpec.IntValue XP_07;
    public static ModConfigSpec.IntValue XP_08;
    public static ModConfigSpec.IntValue XP_09;
    public static ModConfigSpec.IntValue XP_10;
    public static ModConfigSpec.IntValue XP_11;
    public static ModConfigSpec.IntValue XP_12;
    public static ModConfigSpec.IntValue XP_13;
    public static ModConfigSpec.IntValue XP_14;
    public static ModConfigSpec.IntValue XP_15;
    public static ModConfigSpec.IntValue XP_16;
    public static ModConfigSpec.IntValue XP_17;
    public static ModConfigSpec.IntValue XP_18;
    public static ModConfigSpec.IntValue XP_19;
    public static ModConfigSpec.IntValue XP_20;
    public static ModConfigSpec.IntValue XP_21;
    public static ModConfigSpec.IntValue XP_22;
    public static ModConfigSpec.IntValue XP_23;
    public static ModConfigSpec.IntValue XP_24;
    public static ModConfigSpec.IntValue XP_25;
    public static ModConfigSpec.IntValue XP_26;
    public static ModConfigSpec.IntValue XP_27;
    public static ModConfigSpec.IntValue XP_28;
    public static ModConfigSpec.IntValue XP_29;
    public static ModConfigSpec.IntValue XP_30;
    public static ModConfigSpec.IntValue XP_31;
    public static ModConfigSpec.IntValue XP_32;
    public static ModConfigSpec.IntValue XP_33;
    public static ModConfigSpec.IntValue XP_34;
    public static ModConfigSpec.IntValue XP_35;
    public static ModConfigSpec.IntValue XP_36;
    public static ModConfigSpec.IntValue XP_37;
    public static ModConfigSpec.IntValue XP_38;
    public static ModConfigSpec.IntValue XP_39;
    public static ModConfigSpec.IntValue XP_40;
    public static ModConfigSpec.IntValue XP_41;
    public static ModConfigSpec.IntValue XP_42;
    public static ModConfigSpec.IntValue XP_43;
    public static ModConfigSpec.IntValue XP_44;
    public static ModConfigSpec.IntValue XP_45;
    public static ModConfigSpec.IntValue XP_46;
    public static ModConfigSpec.IntValue XP_47;
    public static ModConfigSpec.IntValue XP_48;
    public static ModConfigSpec.IntValue XP_49;
    public static ModConfigSpec.IntValue XP_50;
    public static ModConfigSpec.IntValue XP_51;
    public static ModConfigSpec.IntValue XP_52;
    public static ModConfigSpec.IntValue XP_53;
    public static ModConfigSpec.IntValue XP_54;
    public static ModConfigSpec.IntValue XP_55;
    public static ModConfigSpec.IntValue XP_56;
    public static ModConfigSpec.IntValue XP_57;
    public static ModConfigSpec.IntValue XP_58;
    public static ModConfigSpec.IntValue XP_59;
    public static ModConfigSpec.IntValue XP_60;
    public static ModConfigSpec.IntValue XP_61;
    public static ModConfigSpec.IntValue XP_62;
    public static ModConfigSpec.IntValue XP_63;
    public static ModConfigSpec.IntValue XP_64;
    public static ModConfigSpec.IntValue XP_65;
    public static ModConfigSpec.IntValue XP_66;
    public static ModConfigSpec.IntValue XP_67;
    public static ModConfigSpec.IntValue XP_68;
    public static ModConfigSpec.IntValue XP_69;
    public static ModConfigSpec.IntValue XP_70;
    public static ModConfigSpec.IntValue XP_71;
    public static ModConfigSpec.IntValue XP_72;
    public static ModConfigSpec.IntValue XP_73;
    public static ModConfigSpec.IntValue XP_74;
    public static ModConfigSpec.IntValue XP_75;
    public static ModConfigSpec.IntValue XP_76;
    public static ModConfigSpec.IntValue XP_77;
    public static ModConfigSpec.IntValue XP_78;
    public static ModConfigSpec.IntValue XP_79;
    public static ModConfigSpec.IntValue XP_80;
    public static ModConfigSpec.IntValue XP_81;
    public static ModConfigSpec.IntValue XP_82;
    public static ModConfigSpec.IntValue XP_83;
    public static ModConfigSpec.IntValue XP_84;
    public static ModConfigSpec.IntValue XP_85;
    public static ModConfigSpec.IntValue XP_86;
    public static ModConfigSpec.IntValue XP_87;
    public static ModConfigSpec.IntValue XP_88;
    public static ModConfigSpec.IntValue XP_89;

    static void register(ModConfigSpec.Builder builder) {
        builder.push("levelUpExp");

        XP_01 = builder.translation("minegenshin.configuration.level.1t2").comment("Lv.1 -> Lv.2").defineInRange("1t2", 1000, 0, 10000000);
        XP_02 = builder.translation("minegenshin.configuration.level.2t3").comment("Lv.2 -> Lv.3").defineInRange("2t3", 1325, 0, 10000000);
        XP_03 = builder.translation("minegenshin.configuration.level.3t4").comment("Lv.3 -> Lv.4").defineInRange("3t4", 1700, 0, 10000000);
        XP_04 = builder.translation("minegenshin.configuration.level.4t5").comment("Lv.4 -> Lv.5").defineInRange("4t5", 2150, 0, 10000000);
        XP_05 = builder.translation("minegenshin.configuration.level.5t6").comment("Lv.5 -> Lv.6").defineInRange("5t6", 2625, 0, 10000000);
        XP_06 = builder.translation("minegenshin.configuration.level.6t7").comment("Lv.6 -> Lv.7").defineInRange("6t7", 3150, 0, 10000000);
        XP_07 = builder.translation("minegenshin.configuration.level.7t8").comment("Lv.7 -> Lv.8").defineInRange("7t8", 3725, 0, 10000000);
        XP_08 = builder.translation("minegenshin.configuration.level.8t9").comment("Lv.8 -> Lv.9").defineInRange("8t9", 4350, 0, 10000000);
        XP_09 = builder.translation("minegenshin.configuration.level.9t10").comment("Lv.9 -> Lv.10").defineInRange("9t10", 5000, 0, 10000000);
        XP_10 = builder.translation("minegenshin.configuration.level.10t11").comment("Lv.10 -> Lv.11").defineInRange("10t11", 5700, 0, 10000000);
        XP_11 = builder.translation("minegenshin.configuration.level.11t12").comment("Lv.11 -> Lv.12").defineInRange("11t12", 6450, 0, 10000000);
        XP_12 = builder.translation("minegenshin.configuration.level.12t13").comment("Lv.12 -> Lv.13").defineInRange("12t13", 7225, 0, 10000000);
        XP_13 = builder.translation("minegenshin.configuration.level.13t14").comment("Lv.13 -> Lv.14").defineInRange("13t14", 8050, 0, 10000000);
        XP_14 = builder.translation("minegenshin.configuration.level.14t15").comment("Lv.14 -> Lv.15").defineInRange("14t15", 8925, 0, 10000000);
        XP_15 = builder.translation("minegenshin.configuration.level.15t16").comment("Lv.15 -> Lv.16").defineInRange("15t16", 9825, 0, 10000000);
        XP_16 = builder.translation("minegenshin.configuration.level.16t17").comment("Lv.16 -> Lv.17").defineInRange("16t17", 10750, 0, 10000000);
        XP_17 = builder.translation("minegenshin.configuration.level.17t18").comment("Lv.17 -> Lv.18").defineInRange("17t18", 11725, 0, 10000000);
        XP_18 = builder.translation("minegenshin.configuration.level.18t19").comment("Lv.18 -> Lv.19").defineInRange("18t19", 12725, 0, 10000000);
        XP_19 = builder.translation("minegenshin.configuration.level.19t20").comment("Lv.19 -> Lv.20").defineInRange("19t20", 13775, 0, 10000000);
        XP_20 = builder.translation("minegenshin.configuration.level.20t21").comment("Lv.20 -> Lv.21").defineInRange("20t21", 14875, 0, 10000000);
        XP_21 = builder.translation("minegenshin.configuration.level.21t22").comment("Lv.21 -> Lv.22").defineInRange("21t22", 16800, 0, 10000000);
        XP_22 = builder.translation("minegenshin.configuration.level.22t23").comment("Lv.22 -> Lv.23").defineInRange("22t23", 18000, 0, 10000000);
        XP_23 = builder.translation("minegenshin.configuration.level.23t24").comment("Lv.23 -> Lv.24").defineInRange("23t24", 19250, 0, 10000000);
        XP_24 = builder.translation("minegenshin.configuration.level.24t25").comment("Lv.24 -> Lv.25").defineInRange("24t25", 20550, 0, 10000000);
        XP_25 = builder.translation("minegenshin.configuration.level.25t26").comment("Lv.25 -> Lv.26").defineInRange("25t26", 21875, 0, 10000000);
        XP_26 = builder.translation("minegenshin.configuration.level.26t27").comment("Lv.26 -> Lv.27").defineInRange("26t27", 23250, 0, 10000000);
        XP_27 = builder.translation("minegenshin.configuration.level.27t28").comment("Lv.27 -> Lv.28").defineInRange("27t28", 24650, 0, 10000000);
        XP_28 = builder.translation("minegenshin.configuration.level.28t29").comment("Lv.28 -> Lv.29").defineInRange("28t29", 26100, 0, 10000000);
        XP_29 = builder.translation("minegenshin.configuration.level.29t30").comment("Lv.29 -> Lv.30").defineInRange("29t30", 27575, 0, 10000000);
        XP_30 = builder.translation("minegenshin.configuration.level.30t31").comment("Lv.30 -> Lv.31").defineInRange("30t31", 29100, 0, 10000000);
        XP_31 = builder.translation("minegenshin.configuration.level.31t32").comment("Lv.31 -> Lv.32").defineInRange("31t32", 30650, 0, 10000000);
        XP_32 = builder.translation("minegenshin.configuration.level.32t33").comment("Lv.32 -> Lv.33").defineInRange("32t33", 32250, 0, 10000000);
        XP_33 = builder.translation("minegenshin.configuration.level.33t34").comment("Lv.33 -> Lv.34").defineInRange("33t34", 33875, 0, 10000000);
        XP_34 = builder.translation("minegenshin.configuration.level.34t35").comment("Lv.34 -> Lv.35").defineInRange("34t35", 35550, 0, 10000000);
        XP_35 = builder.translation("minegenshin.configuration.level.35t36").comment("Lv.35 -> Lv.36").defineInRange("35t36", 37250, 0, 10000000);
        XP_36 = builder.translation("minegenshin.configuration.level.36t37").comment("Lv.36 -> Lv.37").defineInRange("36t37", 38975, 0, 10000000);
        XP_37 = builder.translation("minegenshin.configuration.level.37t38").comment("Lv.37 -> Lv.38").defineInRange("37t38", 40750, 0, 10000000);
        XP_38 = builder.translation("minegenshin.configuration.level.38t39").comment("Lv.38 -> Lv.39").defineInRange("38t39", 42575, 0, 10000000);
        XP_39 = builder.translation("minegenshin.configuration.level.39t40").comment("Lv.39 -> Lv.40").defineInRange("39t40", 44425, 0, 10000000);
        XP_40 = builder.translation("minegenshin.configuration.level.40t41").comment("Lv.40 -> Lv.41").defineInRange("40t41", 46300, 0, 10000000);
        XP_41 = builder.translation("minegenshin.configuration.level.41t42").comment("Lv.41 -> Lv.42").defineInRange("41t42", 50625, 0, 10000000);
        XP_42 = builder.translation("minegenshin.configuration.level.42t43").comment("Lv.42 -> Lv.43").defineInRange("42t43", 52700, 0, 10000000);
        XP_43 = builder.translation("minegenshin.configuration.level.43t44").comment("Lv.43 -> Lv.44").defineInRange("43t44", 54775, 0, 10000000);
        XP_44 = builder.translation("minegenshin.configuration.level.44t45").comment("Lv.44 -> Lv.45").defineInRange("44t45", 56900, 0, 10000000);
        XP_45 = builder.translation("minegenshin.configuration.level.45t46").comment("Lv.45 -> Lv.46").defineInRange("45t46", 59075, 0, 10000000);
        XP_46 = builder.translation("minegenshin.configuration.level.46t47").comment("Lv.46 -> Lv.47").defineInRange("46t47", 61275, 0, 10000000);
        XP_47 = builder.translation("minegenshin.configuration.level.47t48").comment("Lv.47 -> Lv.48").defineInRange("47t48", 63525, 0, 10000000);
        XP_48 = builder.translation("minegenshin.configuration.level.48t49").comment("Lv.48 -> Lv.49").defineInRange("48t49", 65800, 0, 10000000);
        XP_49 = builder.translation("minegenshin.configuration.level.49t50").comment("Lv.49 -> Lv.50").defineInRange("49t50", 68125, 0, 10000000);
        XP_50 = builder.translation("minegenshin.configuration.level.50t51").comment("Lv.50 -> Lv.51").defineInRange("50t51", 70457, 0, 10000000);
        XP_51 = builder.translation("minegenshin.configuration.level.51t52").comment("Lv.51 -> Lv.52").defineInRange("51t52", 76500, 0, 10000000);
        XP_52 = builder.translation("minegenshin.configuration.level.52t53").comment("Lv.52 -> Lv.53").defineInRange("52t53", 79050, 0, 10000000);
        XP_53 = builder.translation("minegenshin.configuration.level.53t54").comment("Lv.53 -> Lv.54").defineInRange("53t54", 81650, 0, 10000000);
        XP_54 = builder.translation("minegenshin.configuration.level.54t55").comment("Lv.54 -> Lv.55").defineInRange("54t55", 84275, 0, 10000000);
        XP_55 = builder.translation("minegenshin.configuration.level.55t56").comment("Lv.55 -> Lv.56").defineInRange("55t56", 86950, 0, 10000000);
        XP_56 = builder.translation("minegenshin.configuration.level.56t57").comment("Lv.56 -> Lv.57").defineInRange("56t57", 89650, 0, 10000000);
        XP_57 = builder.translation("minegenshin.configuration.level.57t58").comment("Lv.57 -> Lv.58").defineInRange("57t58", 92400, 0, 10000000);
        XP_58 = builder.translation("minegenshin.configuration.level.58t59").comment("Lv.58 -> Lv.59").defineInRange("58t59", 95175, 0, 10000000);
        XP_59 = builder.translation("minegenshin.configuration.level.59t60").comment("Lv.59 -> Lv.60").defineInRange("59t60", 98000, 0, 10000000);
        XP_60 = builder.translation("minegenshin.configuration.level.60t61").comment("Lv.60 -> Lv.61").defineInRange("60t61", 100875, 0, 10000000);
        XP_61 = builder.translation("minegenshin.configuration.level.61t62").comment("Lv.61 -> Lv.62").defineInRange("61t62", 108950, 0, 10000000);
        XP_62 = builder.translation("minegenshin.configuration.level.62t63").comment("Lv.62 -> Lv.63").defineInRange("62t63", 112050, 0, 10000000);
        XP_63 = builder.translation("minegenshin.configuration.level.63t64").comment("Lv.63 -> Lv.64").defineInRange("63t64", 115175, 0, 10000000);
        XP_64 = builder.translation("minegenshin.configuration.level.64t65").comment("Lv.64 -> Lv.65").defineInRange("64t65", 118325, 0, 10000000);
        XP_65 = builder.translation("minegenshin.configuration.level.65t66").comment("Lv.65 -> Lv.66").defineInRange("65t66", 121525, 0, 10000000);
        XP_66 = builder.translation("minegenshin.configuration.level.66t67").comment("Lv.66 -> Lv.67").defineInRange("66t67", 124775, 0, 10000000);
        XP_67 = builder.translation("minegenshin.configuration.level.67t68").comment("Lv.67 -> Lv.68").defineInRange("67t68", 128075, 0, 10000000);
        XP_68 = builder.translation("minegenshin.configuration.level.68t69").comment("Lv.68 -> Lv.69").defineInRange("68t69", 131400, 0, 10000000);
        XP_69 = builder.translation("minegenshin.configuration.level.69t70").comment("Lv.69 -> Lv.70").defineInRange("69t70", 134775, 0, 10000000);
        XP_70 = builder.translation("minegenshin.configuration.level.70t71").comment("Lv.70 -> Lv.71").defineInRange("70t71", 138175, 0, 10000000);
        XP_71 = builder.translation("minegenshin.configuration.level.71t72").comment("Lv.71 -> Lv.72").defineInRange("71t72", 148700, 0, 10000000);
        XP_72 = builder.translation("minegenshin.configuration.level.72t73").comment("Lv.72 -> Lv.73").defineInRange("72t73", 152375, 0, 10000000);
        XP_73 = builder.translation("minegenshin.configuration.level.73t74").comment("Lv.73 -> Lv.74").defineInRange("73t74", 156075, 0, 10000000);
        XP_74 = builder.translation("minegenshin.configuration.level.74t75").comment("Lv.74 -> Lv.75").defineInRange("74t75", 159825, 0, 10000000);
        XP_75 = builder.translation("minegenshin.configuration.level.75t76").comment("Lv.75 -> Lv.76").defineInRange("75t76", 163600, 0, 10000000);
        XP_76 = builder.translation("minegenshin.configuration.level.76t77").comment("Lv.76 -> Lv.77").defineInRange("76t77", 167425, 0, 10000000);
        XP_77 = builder.translation("minegenshin.configuration.level.77t78").comment("Lv.77 -> Lv.78").defineInRange("77t78", 171300, 0, 10000000);
        XP_78 = builder.translation("minegenshin.configuration.level.78t79").comment("Lv.78 -> Lv.79").defineInRange("78t79", 175225, 0, 10000000);
        XP_79 = builder.translation("minegenshin.configuration.level.79t80").comment("Lv.79 -> Lv.80").defineInRange("79t80", 179175, 0, 10000000);
        XP_80 = builder.translation("minegenshin.configuration.level.80t81").comment("Lv.80 -> Lv.81").defineInRange("80t81", 183175, 0, 10000000);
        XP_81 = builder.translation("minegenshin.configuration.level.81t82").comment("Lv.81 -> Lv.82").defineInRange("81t82", 216225, 0, 10000000);
        XP_82 = builder.translation("minegenshin.configuration.level.82t83").comment("Lv.82 -> Lv.83").defineInRange("82t83", 243025, 0, 10000000);
        XP_83 = builder.translation("minegenshin.configuration.level.83t84").comment("Lv.83 -> Lv.84").defineInRange("83t84", 273100, 0, 10000000);
        XP_84 = builder.translation("minegenshin.configuration.level.84t85").comment("Lv.84 -> Lv.85").defineInRange("84t85", 306800, 0, 10000000);
        XP_85 = builder.translation("minegenshin.configuration.level.85t86").comment("Lv.85 -> Lv.86").defineInRange("85t86", 344600, 0, 10000000);
        XP_86 = builder.translation("minegenshin.configuration.level.86t87").comment("Lv.86 -> Lv.87").defineInRange("86t87", 386950, 0, 10000000);
        XP_87 = builder.translation("minegenshin.configuration.level.87t88").comment("Lv.87 -> Lv.88").defineInRange("87t88", 434225, 0, 10000000);
        XP_88 = builder.translation("minegenshin.configuration.level.88t89").comment("Lv.88 -> Lv.89").defineInRange("88t89", 487625, 0, 10000000);
        XP_89 = builder.translation("minegenshin.configuration.level.89t90").comment("Lv.89 -> Lv.90").defineInRange("89t90", 547200, 0, 10000000);

        builder.pop();
    }

    public static List<Integer> getAllXp() {
        return List.of(
            XP_01.get(), XP_02.get(), XP_03.get(), XP_04.get(), XP_05.get(),
            XP_06.get(), XP_07.get(), XP_08.get(), XP_09.get(), XP_10.get(),
            XP_11.get(), XP_12.get(), XP_13.get(), XP_14.get(), XP_15.get(),
            XP_16.get(), XP_17.get(), XP_18.get(), XP_19.get(), XP_20.get(),
            XP_21.get(), XP_22.get(), XP_23.get(), XP_24.get(), XP_25.get(),
            XP_26.get(), XP_27.get(), XP_28.get(), XP_29.get(), XP_30.get(),
            XP_31.get(), XP_32.get(), XP_33.get(), XP_34.get(), XP_35.get(),
            XP_36.get(), XP_37.get(), XP_38.get(), XP_39.get(), XP_40.get(),
            XP_41.get(), XP_42.get(), XP_43.get(), XP_44.get(), XP_45.get(),
            XP_46.get(), XP_47.get(), XP_48.get(), XP_49.get(), XP_50.get(),
            XP_51.get(), XP_52.get(), XP_53.get(), XP_54.get(), XP_55.get(),
            XP_56.get(), XP_57.get(), XP_58.get(), XP_59.get(), XP_60.get(),
            XP_61.get(), XP_62.get(), XP_63.get(), XP_64.get(), XP_65.get(),
            XP_66.get(), XP_67.get(), XP_68.get(), XP_69.get(), XP_70.get(),
            XP_71.get(), XP_72.get(), XP_73.get(), XP_74.get(), XP_75.get(),
            XP_76.get(), XP_77.get(), XP_78.get(), XP_79.get(), XP_80.get(),
            XP_81.get(), XP_82.get(), XP_83.get(), XP_84.get(), XP_85.get(),
            XP_86.get(), XP_87.get(), XP_88.get(), XP_89.get()
        );
    }

    public static int get(int level) {
        return switch (level) {
            case 1 -> XP_01.get();
            case 2 -> XP_02.get();
            case 3 -> XP_03.get();
            case 4 -> XP_04.get();
            case 5 -> XP_05.get();
            case 6 -> XP_06.get();
            case 7 -> XP_07.get();
            case 8 -> XP_08.get();
            case 9 -> XP_09.get();
            case 10 -> XP_10.get();
            case 11 -> XP_11.get();
            case 12 -> XP_12.get();
            case 13 -> XP_13.get();
            case 14 -> XP_14.get();
            case 15 -> XP_15.get();
            case 16 -> XP_16.get();
            case 17 -> XP_17.get();
            case 18 -> XP_18.get();
            case 19 -> XP_19.get();
            case 20 -> XP_20.get();
            case 21 -> XP_21.get();
            case 22 -> XP_22.get();
            case 23 -> XP_23.get();
            case 24 -> XP_24.get();
            case 25 -> XP_25.get();
            case 26 -> XP_26.get();
            case 27 -> XP_27.get();
            case 28 -> XP_28.get();
            case 29 -> XP_29.get();
            case 30 -> XP_30.get();
            case 31 -> XP_31.get();
            case 32 -> XP_32.get();
            case 33 -> XP_33.get();
            case 34 -> XP_34.get();
            case 35 -> XP_35.get();
            case 36 -> XP_36.get();
            case 37 -> XP_37.get();
            case 38 -> XP_38.get();
            case 39 -> XP_39.get();
            case 40 -> XP_40.get();
            case 41 -> XP_41.get();
            case 42 -> XP_42.get();
            case 43 -> XP_43.get();
            case 44 -> XP_44.get();
            case 45 -> XP_45.get();
            case 46 -> XP_46.get();
            case 47 -> XP_47.get();
            case 48 -> XP_48.get();
            case 49 -> XP_49.get();
            case 50 -> XP_50.get();
            case 51 -> XP_51.get();
            case 52 -> XP_52.get();
            case 53 -> XP_53.get();
            case 54 -> XP_54.get();
            case 55 -> XP_55.get();
            case 56 -> XP_56.get();
            case 57 -> XP_57.get();
            case 58 -> XP_58.get();
            case 59 -> XP_59.get();
            case 60 -> XP_60.get();
            case 61 -> XP_61.get();
            case 62 -> XP_62.get();
            case 63 -> XP_63.get();
            case 64 -> XP_64.get();
            case 65 -> XP_65.get();
            case 66 -> XP_66.get();
            case 67 -> XP_67.get();
            case 68 -> XP_68.get();
            case 69 -> XP_69.get();
            case 70 -> XP_70.get();
            case 71 -> XP_71.get();
            case 72 -> XP_72.get();
            case 73 -> XP_73.get();
            case 74 -> XP_74.get();
            case 75 -> XP_75.get();
            case 76 -> XP_76.get();
            case 77 -> XP_77.get();
            case 78 -> XP_78.get();
            case 79 -> XP_79.get();
            case 80 -> XP_80.get();
            case 81 -> XP_81.get();
            case 82 -> XP_82.get();
            case 83 -> XP_83.get();
            case 84 -> XP_84.get();
            case 85 -> XP_85.get();
            case 86 -> XP_86.get();
            case 87 -> XP_87.get();
            case 88 -> XP_88.get();
            case 89 -> XP_89.get();
            default -> 0;
        };
    }
}