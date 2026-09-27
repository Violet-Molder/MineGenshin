package com.linweiyun.genshin.config.character;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;

public class ArlecchinoAttributeConfig {

    public static ModConfigSpec.IntValue HP_01, HP_02, HP_03, HP_04, HP_05, HP_06, HP_07, HP_08, HP_09, HP_10;
    public static ModConfigSpec.IntValue HP_11, HP_12, HP_13, HP_14, HP_15, HP_16, HP_17, HP_18, HP_19, HP_20;
    public static ModConfigSpec.IntValue HP_21, HP_22, HP_23, HP_24, HP_25, HP_26, HP_27, HP_28, HP_29, HP_30;
    public static ModConfigSpec.IntValue HP_31, HP_32, HP_33, HP_34, HP_35, HP_36, HP_37, HP_38, HP_39, HP_40;
    public static ModConfigSpec.IntValue HP_41, HP_42, HP_43, HP_44, HP_45, HP_46, HP_47, HP_48, HP_49, HP_50;
    public static ModConfigSpec.IntValue HP_51, HP_52, HP_53, HP_54, HP_55, HP_56, HP_57, HP_58, HP_59, HP_60;
    public static ModConfigSpec.IntValue HP_61, HP_62, HP_63, HP_64, HP_65, HP_66, HP_67, HP_68, HP_69, HP_70;
    public static ModConfigSpec.IntValue HP_71, HP_72, HP_73, HP_74, HP_75, HP_76, HP_77, HP_78, HP_79, HP_80;
    public static ModConfigSpec.IntValue HP_81, HP_82, HP_83, HP_84, HP_85, HP_86, HP_87, HP_88, HP_89, HP_90;
    public static ModConfigSpec.IntValue HP_91, HP_92, HP_93, HP_94, HP_95, HP_96;

    public static ModConfigSpec.IntValue DEF_01, DEF_02, DEF_03, DEF_04, DEF_05, DEF_06, DEF_07, DEF_08, DEF_09, DEF_10;
    public static ModConfigSpec.IntValue DEF_11, DEF_12, DEF_13, DEF_14, DEF_15, DEF_16, DEF_17, DEF_18, DEF_19, DEF_20;
    public static ModConfigSpec.IntValue DEF_21, DEF_22, DEF_23, DEF_24, DEF_25, DEF_26, DEF_27, DEF_28, DEF_29, DEF_30;
    public static ModConfigSpec.IntValue DEF_31, DEF_32, DEF_33, DEF_34, DEF_35, DEF_36, DEF_37, DEF_38, DEF_39, DEF_40;
    public static ModConfigSpec.IntValue DEF_41, DEF_42, DEF_43, DEF_44, DEF_45, DEF_46, DEF_47, DEF_48, DEF_49, DEF_50;
    public static ModConfigSpec.IntValue DEF_51, DEF_52, DEF_53, DEF_54, DEF_55, DEF_56, DEF_57, DEF_58, DEF_59, DEF_60;
    public static ModConfigSpec.IntValue DEF_61, DEF_62, DEF_63, DEF_64, DEF_65, DEF_66, DEF_67, DEF_68, DEF_69, DEF_70;
    public static ModConfigSpec.IntValue DEF_71, DEF_72, DEF_73, DEF_74, DEF_75, DEF_76, DEF_77, DEF_78, DEF_79, DEF_80;
    public static ModConfigSpec.IntValue DEF_81, DEF_82, DEF_83, DEF_84, DEF_85, DEF_86, DEF_87, DEF_88, DEF_89, DEF_90;
    public static ModConfigSpec.IntValue DEF_91, DEF_92, DEF_93, DEF_94, DEF_95, DEF_96;

    public static ModConfigSpec.IntValue ATK_01, ATK_02, ATK_03, ATK_04, ATK_05, ATK_06, ATK_07, ATK_08, ATK_09, ATK_10;
    public static ModConfigSpec.IntValue ATK_11, ATK_12, ATK_13, ATK_14, ATK_15, ATK_16, ATK_17, ATK_18, ATK_19, ATK_20;
    public static ModConfigSpec.IntValue ATK_21, ATK_22, ATK_23, ATK_24, ATK_25, ATK_26, ATK_27, ATK_28, ATK_29, ATK_30;
    public static ModConfigSpec.IntValue ATK_31, ATK_32, ATK_33, ATK_34, ATK_35, ATK_36, ATK_37, ATK_38, ATK_39, ATK_40;
    public static ModConfigSpec.IntValue ATK_41, ATK_42, ATK_43, ATK_44, ATK_45, ATK_46, ATK_47, ATK_48, ATK_49, ATK_50;
    public static ModConfigSpec.IntValue ATK_51, ATK_52, ATK_53, ATK_54, ATK_55, ATK_56, ATK_57, ATK_58, ATK_59, ATK_60;
    public static ModConfigSpec.IntValue ATK_61, ATK_62, ATK_63, ATK_64, ATK_65, ATK_66, ATK_67, ATK_68, ATK_69, ATK_70;
    public static ModConfigSpec.IntValue ATK_71, ATK_72, ATK_73, ATK_74, ATK_75, ATK_76, ATK_77, ATK_78, ATK_79, ATK_80;
    public static ModConfigSpec.IntValue ATK_81, ATK_82, ATK_83, ATK_84, ATK_85, ATK_86, ATK_87, ATK_88, ATK_89, ATK_90;
    public static ModConfigSpec.IntValue ATK_91, ATK_92, ATK_93, ATK_94, ATK_95, ATK_96;

    static void register(ModConfigSpec.Builder builder) {
        builder.push("attribute");

        builder.push("hp");
        HP_01 = builder.translation("minegenshin.configuration.level.1").defineInRange("lv1", 1011, 0, 1000000);
        HP_02 = builder.translation("minegenshin.configuration.level.2").defineInRange("lv2", 1095, 0, 1000000);
        HP_03 = builder.translation("minegenshin.configuration.level.3").defineInRange("lv3", 1179, 0, 1000000);
        HP_04 = builder.translation("minegenshin.configuration.level.4").defineInRange("lv4", 1264, 0, 1000000);
        HP_05 = builder.translation("minegenshin.configuration.level.5").defineInRange("lv5", 1348, 0, 1000000);
        HP_06 = builder.translation("minegenshin.configuration.level.6").defineInRange("lv6", 1433, 0, 1000000);
        HP_07 = builder.translation("minegenshin.configuration.level.7").defineInRange("lv7", 1517, 0, 1000000);
        HP_08 = builder.translation("minegenshin.configuration.level.8").defineInRange("lv8", 1602, 0, 1000000);
        HP_09 = builder.translation("minegenshin.configuration.level.9").defineInRange("lv9", 1687, 0, 1000000);
        HP_10 = builder.translation("minegenshin.configuration.level.10").defineInRange("lv10", 1771, 0, 1000000);
        HP_11 = builder.translation("minegenshin.configuration.level.11").defineInRange("lv11", 1856, 0, 1000000);
        HP_12 = builder.translation("minegenshin.configuration.level.12").defineInRange("lv12", 1941, 0, 1000000);
        HP_13 = builder.translation("minegenshin.configuration.level.13").defineInRange("lv13", 2026, 0, 1000000);
        HP_14 = builder.translation("minegenshin.configuration.level.14").defineInRange("lv14", 2112, 0, 1000000);
        HP_15 = builder.translation("minegenshin.configuration.level.15").defineInRange("lv15", 2197, 0, 1000000);
        HP_16 = builder.translation("minegenshin.configuration.level.16").defineInRange("lv16", 2282, 0, 1000000);
        HP_17 = builder.translation("minegenshin.configuration.level.17").defineInRange("lv17", 2368, 0, 1000000);
        HP_18 = builder.translation("minegenshin.configuration.level.18").defineInRange("lv18", 2453, 0, 1000000);
        HP_19 = builder.translation("minegenshin.configuration.level.19").defineInRange("lv19", 2539, 0, 1000000);
        HP_20 = builder.translation("minegenshin.configuration.level.20").defineInRange("lv20", 2624, 0, 1000000);
        HP_21 = builder.translation("minegenshin.configuration.level.20.b").defineInRange("lv20b", 3491, 0, 1000000);
        HP_22 = builder.translation("minegenshin.configuration.level.21").defineInRange("lv21", 3577, 0, 1000000);
        HP_23 = builder.translation("minegenshin.configuration.level.22").defineInRange("lv22", 3663, 0, 1000000);
        HP_24 = builder.translation("minegenshin.configuration.level.23").defineInRange("lv23", 3749, 0, 1000000);
        HP_25 = builder.translation("minegenshin.configuration.level.24").defineInRange("lv24", 3835, 0, 1000000);
        HP_26 = builder.translation("minegenshin.configuration.level.25").defineInRange("lv25", 3921, 0, 1000000);
        HP_27 = builder.translation("minegenshin.configuration.level.26").defineInRange("lv26", 4008, 0, 1000000);
        HP_28 = builder.translation("minegenshin.configuration.level.27").defineInRange("lv27", 4094, 0, 1000000);
        HP_29 = builder.translation("minegenshin.configuration.level.28").defineInRange("lv28", 4180, 0, 1000000);
        HP_30 = builder.translation("minegenshin.configuration.level.29").defineInRange("lv29", 4267, 0, 1000000);
        HP_31 = builder.translation("minegenshin.configuration.level.30").defineInRange("lv30", 4353, 0, 1000000);
        HP_32 = builder.translation("minegenshin.configuration.level.31").defineInRange("lv31", 4440, 0, 1000000);
        HP_33 = builder.translation("minegenshin.configuration.level.32").defineInRange("lv32", 4527, 0, 1000000);
        HP_34 = builder.translation("minegenshin.configuration.level.33").defineInRange("lv33", 4614, 0, 1000000);
        HP_35 = builder.translation("minegenshin.configuration.level.34").defineInRange("lv34", 4700, 0, 1000000);
        HP_36 = builder.translation("minegenshin.configuration.level.35").defineInRange("lv35", 4787, 0, 1000000);
        HP_37 = builder.translation("minegenshin.configuration.level.36").defineInRange("lv36", 4875, 0, 1000000);
        HP_38 = builder.translation("minegenshin.configuration.level.37").defineInRange("lv37", 4962, 0, 1000000);
        HP_39 = builder.translation("minegenshin.configuration.level.38").defineInRange("lv38", 5049, 0, 1000000);
        HP_40 = builder.translation("minegenshin.configuration.level.39").defineInRange("lv39", 5136, 0, 1000000);
        HP_41 = builder.translation("minegenshin.configuration.level.40").defineInRange("lv40", 5224, 0, 1000000);
        HP_42 = builder.translation("minegenshin.configuration.level.40.b").defineInRange("lv40b", 5840, 0, 1000000);
        HP_43 = builder.translation("minegenshin.configuration.level.41").defineInRange("lv41", 5927, 0, 1000000);
        HP_44 = builder.translation("minegenshin.configuration.level.42").defineInRange("lv42", 6015, 0, 1000000);
        HP_45 = builder.translation("minegenshin.configuration.level.43").defineInRange("lv43", 6103, 0, 1000000);
        HP_46 = builder.translation("minegenshin.configuration.level.44").defineInRange("lv44", 6190, 0, 1000000);
        HP_47 = builder.translation("minegenshin.configuration.level.45").defineInRange("lv45", 6278, 0, 1000000);
        HP_48 = builder.translation("minegenshin.configuration.level.46").defineInRange("lv46", 6366, 0, 1000000);
        HP_49 = builder.translation("minegenshin.configuration.level.47").defineInRange("lv47", 6454, 0, 1000000);
        HP_50 = builder.translation("minegenshin.configuration.level.48").defineInRange("lv48", 6542, 0, 1000000);
        HP_51 = builder.translation("minegenshin.configuration.level.49").defineInRange("lv49", 6631, 0, 1000000);
        HP_52 = builder.translation("minegenshin.configuration.level.50").defineInRange("lv50", 6719, 0, 1000000);
        HP_53 = builder.translation("minegenshin.configuration.level.50.b").defineInRange("lv50b", 7540, 0, 1000000);
        HP_54 = builder.translation("minegenshin.configuration.level.51").defineInRange("lv51", 7628, 0, 1000000);
        HP_55 = builder.translation("minegenshin.configuration.level.52").defineInRange("lv52", 7717, 0, 1000000);
        HP_56 = builder.translation("minegenshin.configuration.level.53").defineInRange("lv53", 7805, 0, 1000000);
        HP_57 = builder.translation("minegenshin.configuration.level.54").defineInRange("lv54", 7894, 0, 1000000);
        HP_58 = builder.translation("minegenshin.configuration.level.55").defineInRange("lv55", 7983, 0, 1000000);
        HP_59 = builder.translation("minegenshin.configuration.level.56").defineInRange("lv56", 8072, 0, 1000000);
        HP_60 = builder.translation("minegenshin.configuration.level.57").defineInRange("lv57", 8161, 0, 1000000);
        HP_61 = builder.translation("minegenshin.configuration.level.58").defineInRange("lv58", 8250, 0, 1000000);
        HP_62 = builder.translation("minegenshin.configuration.level.59").defineInRange("lv59", 8339, 0, 1000000);
        HP_63 = builder.translation("minegenshin.configuration.level.60").defineInRange("lv60", 8429, 0, 1000000);
        HP_64 = builder.translation("minegenshin.configuration.level.60.b").defineInRange("lv60b", 9045, 0, 1000000);
        HP_65 = builder.translation("minegenshin.configuration.level.61").defineInRange("lv61", 9134, 0, 1000000);
        HP_66 = builder.translation("minegenshin.configuration.level.62").defineInRange("lv62", 9223, 0, 1000000);
        HP_67 = builder.translation("minegenshin.configuration.level.63").defineInRange("lv63", 9313, 0, 1000000);
        HP_68 = builder.translation("minegenshin.configuration.level.64").defineInRange("lv64", 9402, 0, 1000000);
        HP_69 = builder.translation("minegenshin.configuration.level.65").defineInRange("lv65", 9492, 0, 1000000);
        HP_70 = builder.translation("minegenshin.configuration.level.66").defineInRange("lv66", 9582, 0, 1000000);
        HP_71 = builder.translation("minegenshin.configuration.level.67").defineInRange("lv67", 9671, 0, 1000000);
        HP_72 = builder.translation("minegenshin.configuration.level.68").defineInRange("lv68", 9761, 0, 1000000);
        HP_73 = builder.translation("minegenshin.configuration.level.69").defineInRange("lv69", 9851, 0, 1000000);
        HP_74 = builder.translation("minegenshin.configuration.level.70").defineInRange("lv70", 9941, 0, 1000000);
        HP_75 = builder.translation("minegenshin.configuration.level.70.b").defineInRange("lv70b", 10557, 0, 1000000);
        HP_76 = builder.translation("minegenshin.configuration.level.71").defineInRange("lv71", 10647, 0, 1000000);
        HP_77 = builder.translation("minegenshin.configuration.level.72").defineInRange("lv72", 10738, 0, 1000000);
        HP_78 = builder.translation("minegenshin.configuration.level.73").defineInRange("lv73", 10828, 0, 1000000);
        HP_79 = builder.translation("minegenshin.configuration.level.74").defineInRange("lv74", 10918, 0, 1000000);
        HP_80 = builder.translation("minegenshin.configuration.level.75").defineInRange("lv75", 11009, 0, 1000000);
        HP_81 = builder.translation("minegenshin.configuration.level.76").defineInRange("lv76", 11099, 0, 1000000);
        HP_82 = builder.translation("minegenshin.configuration.level.77").defineInRange("lv77", 11190, 0, 1000000);
        HP_83 = builder.translation("minegenshin.configuration.level.78").defineInRange("lv78", 11281, 0, 1000000);
        HP_84 = builder.translation("minegenshin.configuration.level.79").defineInRange("lv79", 11372, 0, 1000000);
        HP_85 = builder.translation("minegenshin.configuration.level.80").defineInRange("lv80", 11463, 0, 1000000);
        HP_86 = builder.translation("minegenshin.configuration.level.80.b").defineInRange("lv80b", 12080, 0, 1000000);
        HP_87 = builder.translation("minegenshin.configuration.level.81").defineInRange("lv81", 12171, 0, 1000000);
        HP_88 = builder.translation("minegenshin.configuration.level.82").defineInRange("lv82", 12262, 0, 1000000);
        HP_89 = builder.translation("minegenshin.configuration.level.83").defineInRange("lv83", 12353, 0, 1000000);
        HP_90 = builder.translation("minegenshin.configuration.level.84").defineInRange("lv84", 12444, 0, 1000000);
        HP_91 = builder.translation("minegenshin.configuration.level.85").defineInRange("lv85", 12535, 0, 1000000);
        HP_92 = builder.translation("minegenshin.configuration.level.86").defineInRange("lv86", 12627, 0, 1000000);
        HP_93 = builder.translation("minegenshin.configuration.level.87").defineInRange("lv87", 12717, 0, 1000000);
        HP_94 = builder.translation("minegenshin.configuration.level.88").defineInRange("lv88", 12810, 0, 1000000);
        HP_95 = builder.translation("minegenshin.configuration.level.89").defineInRange("lv89", 12902, 0, 1000000);
        HP_96 = builder.translation("minegenshin.configuration.level.90").defineInRange("lv90", 12993, 0, 1000000);
        builder.pop();

        builder.push("def");
        DEF_01 = builder.translation("minegenshin.configuration.level.1").defineInRange("lv1", 65, 0, 100000);
        DEF_02 = builder.translation("minegenshin.configuration.level.2").defineInRange("lv2", 70, 0, 100000);
        DEF_03 = builder.translation("minegenshin.configuration.level.3").defineInRange("lv3", 75, 0, 100000);
        DEF_04 = builder.translation("minegenshin.configuration.level.4").defineInRange("lv4", 81, 0, 100000);
        DEF_05 = builder.translation("minegenshin.configuration.level.5").defineInRange("lv5", 86, 0, 100000);
        DEF_06 = builder.translation("minegenshin.configuration.level.6").defineInRange("lv6", 92, 0, 100000);
        DEF_07 = builder.translation("minegenshin.configuration.level.7").defineInRange("lv7", 97, 0, 100000);
        DEF_08 = builder.translation("minegenshin.configuration.level.8").defineInRange("lv8", 102, 0, 100000);
        DEF_09 = builder.translation("minegenshin.configuration.level.9").defineInRange("lv9", 108, 0, 100000);
        DEF_10 = builder.translation("minegenshin.configuration.level.10").defineInRange("lv10", 113, 0, 100000);
        DEF_11 = builder.translation("minegenshin.configuration.level.11").defineInRange("lv11", 119, 0, 100000);
        DEF_12 = builder.translation("minegenshin.configuration.level.12").defineInRange("lv12", 124, 0, 100000);
        DEF_13 = builder.translation("minegenshin.configuration.level.13").defineInRange("lv13", 129, 0, 100000);
        DEF_14 = builder.translation("minegenshin.configuration.level.14").defineInRange("lv14", 135, 0, 100000);
        DEF_15 = builder.translation("minegenshin.configuration.level.15").defineInRange("lv15", 140, 0, 100000);
        DEF_16 = builder.translation("minegenshin.configuration.level.16").defineInRange("lv16", 146, 0, 100000);
        DEF_17 = builder.translation("minegenshin.configuration.level.17").defineInRange("lv17", 151, 0, 100000);
        DEF_18 = builder.translation("minegenshin.configuration.level.18").defineInRange("lv18", 157, 0, 100000);
        DEF_19 = builder.translation("minegenshin.configuration.level.19").defineInRange("lv19", 162, 0, 100000);
        DEF_20 = builder.translation("minegenshin.configuration.level.20").defineInRange("lv20", 168, 0, 100000);
        DEF_21 = builder.translation("minegenshin.configuration.level.20.b").defineInRange("lv20b", 223, 0, 100000);
        DEF_22 = builder.translation("minegenshin.configuration.level.21").defineInRange("lv21", 229, 0, 100000);
        DEF_23 = builder.translation("minegenshin.configuration.level.22").defineInRange("lv22", 234, 0, 100000);
        DEF_24 = builder.translation("minegenshin.configuration.level.23").defineInRange("lv23", 239, 0, 100000);
        DEF_25 = builder.translation("minegenshin.configuration.level.24").defineInRange("lv24", 245, 0, 100000);
        DEF_26 = builder.translation("minegenshin.configuration.level.25").defineInRange("lv25", 250, 0, 100000);
        DEF_27 = builder.translation("minegenshin.configuration.level.26").defineInRange("lv26", 256, 0, 100000);
        DEF_28 = builder.translation("minegenshin.configuration.level.27").defineInRange("lv27", 262, 0, 100000);
        DEF_29 = builder.translation("minegenshin.configuration.level.28").defineInRange("lv28", 267, 0, 100000);
        DEF_30 = builder.translation("minegenshin.configuration.level.29").defineInRange("lv29", 273, 0, 100000);
        DEF_31 = builder.translation("minegenshin.configuration.level.30").defineInRange("lv30", 278, 0, 100000);
        DEF_32 = builder.translation("minegenshin.configuration.level.31").defineInRange("lv31", 284, 0, 100000);
        DEF_33 = builder.translation("minegenshin.configuration.level.32").defineInRange("lv32", 289, 0, 100000);
        DEF_34 = builder.translation("minegenshin.configuration.level.33").defineInRange("lv33", 295, 0, 100000);
        DEF_35 = builder.translation("minegenshin.configuration.level.34").defineInRange("lv34", 300, 0, 100000);
        DEF_36 = builder.translation("minegenshin.configuration.level.35").defineInRange("lv35", 306, 0, 100000);
        DEF_37 = builder.translation("minegenshin.configuration.level.36").defineInRange("lv36", 311, 0, 100000);
        DEF_38 = builder.translation("minegenshin.configuration.level.37").defineInRange("lv37", 317, 0, 100000);
        DEF_39 = builder.translation("minegenshin.configuration.level.38").defineInRange("lv38", 323, 0, 100000);
        DEF_40 = builder.translation("minegenshin.configuration.level.39").defineInRange("lv39", 328, 0, 100000);
        DEF_41 = builder.translation("minegenshin.configuration.level.40").defineInRange("lv40", 334, 0, 100000);
        DEF_42 = builder.translation("minegenshin.configuration.level.40.b").defineInRange("lv40b", 373, 0, 100000);
        DEF_43 = builder.translation("minegenshin.configuration.level.41").defineInRange("lv41", 379, 0, 100000);
        DEF_44 = builder.translation("minegenshin.configuration.level.42").defineInRange("lv42", 384, 0, 100000);
        DEF_45 = builder.translation("minegenshin.configuration.level.43").defineInRange("lv43", 390, 0, 100000);
        DEF_46 = builder.translation("minegenshin.configuration.level.44").defineInRange("lv44", 395, 0, 100000);
        DEF_47 = builder.translation("minegenshin.configuration.level.45").defineInRange("lv45", 401, 0, 100000);
        DEF_48 = builder.translation("minegenshin.configuration.level.46").defineInRange("lv46", 407, 0, 100000);
        DEF_49 = builder.translation("minegenshin.configuration.level.47").defineInRange("lv47", 412, 0, 100000);
        DEF_50 = builder.translation("minegenshin.configuration.level.48").defineInRange("lv48", 418, 0, 100000);
        DEF_51 = builder.translation("minegenshin.configuration.level.49").defineInRange("lv49", 424, 0, 100000);
        DEF_52 = builder.translation("minegenshin.configuration.level.50").defineInRange("lv50", 429, 0, 100000);
        DEF_53 = builder.translation("minegenshin.configuration.level.50.b").defineInRange("lv50b", 482, 0, 100000);
        DEF_54 = builder.translation("minegenshin.configuration.level.51").defineInRange("lv51", 487, 0, 100000);
        DEF_55 = builder.translation("minegenshin.configuration.level.52").defineInRange("lv52", 493, 0, 100000);
        DEF_56 = builder.translation("minegenshin.configuration.level.53").defineInRange("lv53", 499, 0, 100000);
        DEF_57 = builder.translation("minegenshin.configuration.level.54").defineInRange("lv54", 504, 0, 100000);
        DEF_58 = builder.translation("minegenshin.configuration.level.55").defineInRange("lv55", 510, 0, 100000);
        DEF_59 = builder.translation("minegenshin.configuration.level.56").defineInRange("lv56", 516, 0, 100000);
        DEF_60 = builder.translation("minegenshin.configuration.level.57").defineInRange("lv57", 521, 0, 100000);
        DEF_61 = builder.translation("minegenshin.configuration.level.58").defineInRange("lv58", 527, 0, 100000);
        DEF_62 = builder.translation("minegenshin.configuration.level.59").defineInRange("lv59", 533, 0, 100000);
        DEF_63 = builder.translation("minegenshin.configuration.level.60").defineInRange("lv60", 538, 0, 100000);
        DEF_64 = builder.translation("minegenshin.configuration.level.60.b").defineInRange("lv60b", 578, 0, 100000);
        DEF_65 = builder.translation("minegenshin.configuration.level.61").defineInRange("lv61", 583, 0, 100000);
        DEF_66 = builder.translation("minegenshin.configuration.level.62").defineInRange("lv62", 589, 0, 100000);
        DEF_67 = builder.translation("minegenshin.configuration.level.63").defineInRange("lv63", 595, 0, 100000);
        DEF_68 = builder.translation("minegenshin.configuration.level.64").defineInRange("lv64", 601, 0, 100000);
        DEF_69 = builder.translation("minegenshin.configuration.level.65").defineInRange("lv65", 606, 0, 100000);
        DEF_70 = builder.translation("minegenshin.configuration.level.66").defineInRange("lv66", 612, 0, 100000);
        DEF_71 = builder.translation("minegenshin.configuration.level.67").defineInRange("lv67", 618, 0, 100000);
        DEF_72 = builder.translation("minegenshin.configuration.level.68").defineInRange("lv68", 624, 0, 100000);
        DEF_73 = builder.translation("minegenshin.configuration.level.69").defineInRange("lv69", 629, 0, 100000);
        DEF_74 = builder.translation("minegenshin.configuration.level.70").defineInRange("lv70", 635, 0, 100000);
        DEF_75 = builder.translation("minegenshin.configuration.level.70.b").defineInRange("lv70b", 674, 0, 100000);
        DEF_76 = builder.translation("minegenshin.configuration.level.71").defineInRange("lv71", 680, 0, 100000);
        DEF_77 = builder.translation("minegenshin.configuration.level.72").defineInRange("lv72", 686, 0, 100000);
        DEF_78 = builder.translation("minegenshin.configuration.level.73").defineInRange("lv73", 692, 0, 100000);
        DEF_79 = builder.translation("minegenshin.configuration.level.74").defineInRange("lv74", 697, 0, 100000);
        DEF_80 = builder.translation("minegenshin.configuration.level.75").defineInRange("lv75", 703, 0, 100000);
        DEF_81 = builder.translation("minegenshin.configuration.level.76").defineInRange("lv76", 709, 0, 100000);
        DEF_82 = builder.translation("minegenshin.configuration.level.77").defineInRange("lv77", 715, 0, 100000);
        DEF_83 = builder.translation("minegenshin.configuration.level.78").defineInRange("lv78", 721, 0, 100000);
        DEF_84 = builder.translation("minegenshin.configuration.level.79").defineInRange("lv79", 727, 0, 100000);
        DEF_85 = builder.translation("minegenshin.configuration.level.80").defineInRange("lv80", 732, 0, 100000);
        DEF_86 = builder.translation("minegenshin.configuration.level.80.b").defineInRange("lv80b", 772, 0, 100000);
        DEF_87 = builder.translation("minegenshin.configuration.level.81").defineInRange("lv81", 778, 0, 100000);
        DEF_88 = builder.translation("minegenshin.configuration.level.82").defineInRange("lv82", 783, 0, 100000);
        DEF_89 = builder.translation("minegenshin.configuration.level.83").defineInRange("lv83", 789, 0, 100000);
        DEF_90 = builder.translation("minegenshin.configuration.level.84").defineInRange("lv84", 795, 0, 100000);
        DEF_91 = builder.translation("minegenshin.configuration.level.85").defineInRange("lv85", 801, 0, 100000);
        DEF_92 = builder.translation("minegenshin.configuration.level.86").defineInRange("lv86", 807, 0, 100000);
        DEF_93 = builder.translation("minegenshin.configuration.level.87").defineInRange("lv87", 812, 0, 100000);
        DEF_94 = builder.translation("minegenshin.configuration.level.88").defineInRange("lv88", 818, 0, 100000);
        DEF_95 = builder.translation("minegenshin.configuration.level.89").defineInRange("lv89", 824, 0, 100000);
        DEF_96 = builder.translation("minegenshin.configuration.level.90").defineInRange("lv90", 830, 0, 100000);
        builder.pop();

        builder.push("atk");
        ATK_01 = builder.translation("minegenshin.configuration.level.1").defineInRange("lv1", 24, 0, 100000);
        ATK_02 = builder.translation("minegenshin.configuration.level.2").defineInRange("lv2", 26, 0, 100000);
        ATK_03 = builder.translation("minegenshin.configuration.level.3").defineInRange("lv3", 28, 0, 100000);
        ATK_04 = builder.translation("minegenshin.configuration.level.4").defineInRange("lv4", 30, 0, 100000);
        ATK_05 = builder.translation("minegenshin.configuration.level.5").defineInRange("lv5", 32, 0, 100000);
        ATK_06 = builder.translation("minegenshin.configuration.level.6").defineInRange("lv6", 34, 0, 100000);
        ATK_07 = builder.translation("minegenshin.configuration.level.7").defineInRange("lv7", 35, 0, 100000);
        ATK_08 = builder.translation("minegenshin.configuration.level.8").defineInRange("lv8", 37, 0, 100000);
        ATK_09 = builder.translation("minegenshin.configuration.level.9").defineInRange("lv9", 39, 0, 100000);
        ATK_10 = builder.translation("minegenshin.configuration.level.10").defineInRange("lv10", 41, 0, 100000);
        ATK_11 = builder.translation("minegenshin.configuration.level.11").defineInRange("lv11", 43, 0, 100000);
        ATK_12 = builder.translation("minegenshin.configuration.level.12").defineInRange("lv12", 45, 0, 100000);
        ATK_13 = builder.translation("minegenshin.configuration.level.13").defineInRange("lv13", 47, 0, 100000);
        ATK_14 = builder.translation("minegenshin.configuration.level.14").defineInRange("lv14", 49, 0, 100000);
        ATK_15 = builder.translation("minegenshin.configuration.level.15").defineInRange("lv15", 51, 0, 100000);
        ATK_16 = builder.translation("minegenshin.configuration.level.16").defineInRange("lv16", 53, 0, 100000);
        ATK_17 = builder.translation("minegenshin.configuration.level.17").defineInRange("lv17", 55, 0, 100000);
        ATK_18 = builder.translation("minegenshin.configuration.level.18").defineInRange("lv18", 57, 0, 100000);
        ATK_19 = builder.translation("minegenshin.configuration.level.19").defineInRange("lv19", 59, 0, 100000);
        ATK_20 = builder.translation("minegenshin.configuration.level.20").defineInRange("lv20", 61, 0, 100000);
        ATK_21 = builder.translation("minegenshin.configuration.level.20.b").defineInRange("lv20b", 82, 0, 100000);
        ATK_22 = builder.translation("minegenshin.configuration.level.21").defineInRange("lv21", 84, 0, 100000);
        ATK_23 = builder.translation("minegenshin.configuration.level.22").defineInRange("lv22", 86, 0, 100000);
        ATK_24 = builder.translation("minegenshin.configuration.level.23").defineInRange("lv23", 88, 0, 100000);
        ATK_25 = builder.translation("minegenshin.configuration.level.24").defineInRange("lv24", 90, 0, 100000);
        ATK_26 = builder.translation("minegenshin.configuration.level.25").defineInRange("lv25", 92, 0, 100000);
        ATK_27 = builder.translation("minegenshin.configuration.level.26").defineInRange("lv26", 94, 0, 100000);
        ATK_28 = builder.translation("minegenshin.configuration.level.27").defineInRange("lv27", 96, 0, 100000);
        ATK_29 = builder.translation("minegenshin.configuration.level.28").defineInRange("lv28", 98, 0, 100000);
        ATK_30 = builder.translation("minegenshin.configuration.level.29").defineInRange("lv29", 100, 0, 100000);
        ATK_31 = builder.translation("minegenshin.configuration.level.30").defineInRange("lv30", 102, 0, 100000);
        ATK_32 = builder.translation("minegenshin.configuration.level.31").defineInRange("lv31", 104, 0, 100000);
        ATK_33 = builder.translation("minegenshin.configuration.level.32").defineInRange("lv32", 106, 0, 100000);
        ATK_34 = builder.translation("minegenshin.configuration.level.33").defineInRange("lv33", 108, 0, 100000);
        ATK_35 = builder.translation("minegenshin.configuration.level.34").defineInRange("lv34", 110, 0, 100000);
        ATK_36 = builder.translation("minegenshin.configuration.level.35").defineInRange("lv35", 112, 0, 100000);
        ATK_37 = builder.translation("minegenshin.configuration.level.36").defineInRange("lv36", 114, 0, 100000);
        ATK_38 = builder.translation("minegenshin.configuration.level.37").defineInRange("lv37", 116, 0, 100000);
        ATK_39 = builder.translation("minegenshin.configuration.level.38").defineInRange("lv38", 118, 0, 100000);
        ATK_40 = builder.translation("minegenshin.configuration.level.39").defineInRange("lv39", 120, 0, 100000);
        ATK_41 = builder.translation("minegenshin.configuration.level.40").defineInRange("lv40", 122, 0, 100000);
        ATK_42 = builder.translation("minegenshin.configuration.level.40.b").defineInRange("lv40b", 137, 0, 100000);
        ATK_43 = builder.translation("minegenshin.configuration.level.41").defineInRange("lv41", 139, 0, 100000);
        ATK_44 = builder.translation("minegenshin.configuration.level.42").defineInRange("lv42", 141, 0, 100000);
        ATK_45 = builder.translation("minegenshin.configuration.level.43").defineInRange("lv43", 143, 0, 100000);
        ATK_46 = builder.translation("minegenshin.configuration.level.44").defineInRange("lv44", 145, 0, 100000);
        ATK_47 = builder.translation("minegenshin.configuration.level.45").defineInRange("lv45", 147, 0, 100000);
        ATK_48 = builder.translation("minegenshin.configuration.level.46").defineInRange("lv46", 149, 0, 100000);
        ATK_49 = builder.translation("minegenshin.configuration.level.47").defineInRange("lv47", 151, 0, 100000);
        ATK_50 = builder.translation("minegenshin.configuration.level.48").defineInRange("lv48", 153, 0, 100000);
        ATK_51 = builder.translation("minegenshin.configuration.level.49").defineInRange("lv49", 155, 0, 100000);
        ATK_52 = builder.translation("minegenshin.configuration.level.50").defineInRange("lv50", 157, 0, 100000);
        ATK_53 = builder.translation("minegenshin.configuration.level.50.b").defineInRange("lv50b", 176, 0, 100000);
        ATK_54 = builder.translation("minegenshin.configuration.level.51").defineInRange("lv51", 178, 0, 100000);
        ATK_55 = builder.translation("minegenshin.configuration.level.52").defineInRange("lv52", 180, 0, 100000);
        ATK_56 = builder.translation("minegenshin.configuration.level.53").defineInRange("lv53", 182, 0, 100000);
        ATK_57 = builder.translation("minegenshin.configuration.level.54").defineInRange("lv54", 185, 0, 100000);
        ATK_58 = builder.translation("minegenshin.configuration.level.55").defineInRange("lv55", 187, 0, 100000);
        ATK_59 = builder.translation("minegenshin.configuration.level.56").defineInRange("lv56", 189, 0, 100000);
        ATK_60 = builder.translation("minegenshin.configuration.level.57").defineInRange("lv57", 191, 0, 100000);
        ATK_61 = builder.translation("minegenshin.configuration.level.58").defineInRange("lv58", 193, 0, 100000);
        ATK_62 = builder.translation("minegenshin.configuration.level.59").defineInRange("lv59", 195, 0, 100000);
        ATK_63 = builder.translation("minegenshin.configuration.level.60").defineInRange("lv60", 197, 0, 100000);
        ATK_64 = builder.translation("minegenshin.configuration.level.60.b").defineInRange("lv60b", 211, 0, 100000);
        ATK_65 = builder.translation("minegenshin.configuration.level.61").defineInRange("lv61", 214, 0, 100000);
        ATK_66 = builder.translation("minegenshin.configuration.level.62").defineInRange("lv62", 216, 0, 100000);
        ATK_67 = builder.translation("minegenshin.configuration.level.63").defineInRange("lv63", 218, 0, 100000);
        ATK_68 = builder.translation("minegenshin.configuration.level.64").defineInRange("lv64", 220, 0, 100000);
        ATK_69 = builder.translation("minegenshin.configuration.level.65").defineInRange("lv65", 222, 0, 100000);
        ATK_70 = builder.translation("minegenshin.configuration.level.66").defineInRange("lv66", 224, 0, 100000);
        ATK_71 = builder.translation("minegenshin.configuration.level.67").defineInRange("lv67", 226, 0, 100000);
        ATK_72 = builder.translation("minegenshin.configuration.level.68").defineInRange("lv68", 228, 0, 100000);
        ATK_73 = builder.translation("minegenshin.configuration.level.69").defineInRange("lv69", 230, 0, 100000);
        ATK_74 = builder.translation("minegenshin.configuration.level.70").defineInRange("lv70", 232, 0, 100000);
        ATK_75 = builder.translation("minegenshin.configuration.level.70.b").defineInRange("lv70b", 247, 0, 100000);
        ATK_76 = builder.translation("minegenshin.configuration.level.71").defineInRange("lv71", 249, 0, 100000);
        ATK_77 = builder.translation("minegenshin.configuration.level.72").defineInRange("lv72", 251, 0, 100000);
        ATK_78 = builder.translation("minegenshin.configuration.level.73").defineInRange("lv73", 253, 0, 100000);
        ATK_79 = builder.translation("minegenshin.configuration.level.74").defineInRange("lv74", 255, 0, 100000);
        ATK_80 = builder.translation("minegenshin.configuration.level.75").defineInRange("lv75", 257, 0, 100000);
        ATK_81 = builder.translation("minegenshin.configuration.level.76").defineInRange("lv76", 259, 0, 100000);
        ATK_82 = builder.translation("minegenshin.configuration.level.77").defineInRange("lv77", 262, 0, 100000);
        ATK_83 = builder.translation("minegenshin.configuration.level.78").defineInRange("lv78", 264, 0, 100000);
        ATK_84 = builder.translation("minegenshin.configuration.level.79").defineInRange("lv79", 266, 0, 100000);
        ATK_85 = builder.translation("minegenshin.configuration.level.80").defineInRange("lv80", 268, 0, 100000);
        ATK_86 = builder.translation("minegenshin.configuration.level.80.b").defineInRange("lv80b", 282, 0, 100000);
        ATK_87 = builder.translation("minegenshin.configuration.level.81").defineInRange("lv81", 285, 0, 100000);
        ATK_88 = builder.translation("minegenshin.configuration.level.82").defineInRange("lv82", 287, 0, 100000);
        ATK_89 = builder.translation("minegenshin.configuration.level.83").defineInRange("lv83", 289, 0, 100000);
        ATK_90 = builder.translation("minegenshin.configuration.level.84").defineInRange("lv84", 291, 0, 100000);
        ATK_91 = builder.translation("minegenshin.configuration.level.85").defineInRange("lv85", 293, 0, 100000);
        ATK_92 = builder.translation("minegenshin.configuration.level.86").defineInRange("lv86", 295, 0, 100000);
        ATK_93 = builder.translation("minegenshin.configuration.level.87").defineInRange("lv87", 297, 0, 100000);
        ATK_94 = builder.translation("minegenshin.configuration.level.88").defineInRange("lv88", 299, 0, 100000);
        ATK_95 = builder.translation("minegenshin.configuration.level.89").defineInRange("lv89", 302, 0, 100000);
        ATK_96 = builder.translation("minegenshin.configuration.level.90").defineInRange("lv90", 304, 0, 100000);
        builder.pop();

        builder.pop();
    }

    public static int getHp(int level) {
        return switch (level) {
            case 1 -> HP_01.get();
            case 2 -> HP_02.get();
            case 3 -> HP_03.get();
            case 4 -> HP_04.get();
            case 5 -> HP_05.get();
            case 6 -> HP_06.get();
            case 7 -> HP_07.get();
            case 8 -> HP_08.get();
            case 9 -> HP_09.get();
            case 10 -> HP_10.get();
            case 11 -> HP_11.get();
            case 12 -> HP_12.get();
            case 13 -> HP_13.get();
            case 14 -> HP_14.get();
            case 15 -> HP_15.get();
            case 16 -> HP_16.get();
            case 17 -> HP_17.get();
            case 18 -> HP_18.get();
            case 19 -> HP_19.get();
            case 20 -> HP_20.get();
            case 21 -> HP_21.get();
            case 22 -> HP_22.get();
            case 23 -> HP_23.get();
            case 24 -> HP_24.get();
            case 25 -> HP_25.get();
            case 26 -> HP_26.get();
            case 27 -> HP_27.get();
            case 28 -> HP_28.get();
            case 29 -> HP_29.get();
            case 30 -> HP_30.get();
            case 31 -> HP_31.get();
            case 32 -> HP_32.get();
            case 33 -> HP_33.get();
            case 34 -> HP_34.get();
            case 35 -> HP_35.get();
            case 36 -> HP_36.get();
            case 37 -> HP_37.get();
            case 38 -> HP_38.get();
            case 39 -> HP_39.get();
            case 40 -> HP_40.get();
            case 41 -> HP_41.get();
            case 42 -> HP_42.get();
            case 43 -> HP_43.get();
            case 44 -> HP_44.get();
            case 45 -> HP_45.get();
            case 46 -> HP_46.get();
            case 47 -> HP_47.get();
            case 48 -> HP_48.get();
            case 49 -> HP_49.get();
            case 50 -> HP_50.get();
            case 51 -> HP_51.get();
            case 52 -> HP_52.get();
            case 53 -> HP_53.get();
            case 54 -> HP_54.get();
            case 55 -> HP_55.get();
            case 56 -> HP_56.get();
            case 57 -> HP_57.get();
            case 58 -> HP_58.get();
            case 59 -> HP_59.get();
            case 60 -> HP_60.get();
            case 61 -> HP_61.get();
            case 62 -> HP_62.get();
            case 63 -> HP_63.get();
            case 64 -> HP_64.get();
            case 65 -> HP_65.get();
            case 66 -> HP_66.get();
            case 67 -> HP_67.get();
            case 68 -> HP_68.get();
            case 69 -> HP_69.get();
            case 70 -> HP_70.get();
            case 71 -> HP_71.get();
            case 72 -> HP_72.get();
            case 73 -> HP_73.get();
            case 74 -> HP_74.get();
            case 75 -> HP_75.get();
            case 76 -> HP_76.get();
            case 77 -> HP_77.get();
            case 78 -> HP_78.get();
            case 79 -> HP_79.get();
            case 80 -> HP_80.get();
            case 81 -> HP_81.get();
            case 82 -> HP_82.get();
            case 83 -> HP_83.get();
            case 84 -> HP_84.get();
            case 85 -> HP_85.get();
            case 86 -> HP_86.get();
            case 87 -> HP_87.get();
            case 88 -> HP_88.get();
            case 89 -> HP_89.get();
            case 90 -> HP_90.get();
            case 91 -> HP_91.get();
            case 92 -> HP_92.get();
            case 93 -> HP_93.get();
            case 94 -> HP_94.get();
            case 95 -> HP_95.get();
            case 96 -> HP_96.get();
            default -> 0;
        };
    }

    public static int getDef(int level) {
        return switch (level) {
            case 1 -> DEF_01.get();
            case 2 -> DEF_02.get();
            case 3 -> DEF_03.get();
            case 4 -> DEF_04.get();
            case 5 -> DEF_05.get();
            case 6 -> DEF_06.get();
            case 7 -> DEF_07.get();
            case 8 -> DEF_08.get();
            case 9 -> DEF_09.get();
            case 10 -> DEF_10.get();
            case 11 -> DEF_11.get();
            case 12 -> DEF_12.get();
            case 13 -> DEF_13.get();
            case 14 -> DEF_14.get();
            case 15 -> DEF_15.get();
            case 16 -> DEF_16.get();
            case 17 -> DEF_17.get();
            case 18 -> DEF_18.get();
            case 19 -> DEF_19.get();
            case 20 -> DEF_20.get();
            case 21 -> DEF_21.get();
            case 22 -> DEF_22.get();
            case 23 -> DEF_23.get();
            case 24 -> DEF_24.get();
            case 25 -> DEF_25.get();
            case 26 -> DEF_26.get();
            case 27 -> DEF_27.get();
            case 28 -> DEF_28.get();
            case 29 -> DEF_29.get();
            case 30 -> DEF_30.get();
            case 31 -> DEF_31.get();
            case 32 -> DEF_32.get();
            case 33 -> DEF_33.get();
            case 34 -> DEF_34.get();
            case 35 -> DEF_35.get();
            case 36 -> DEF_36.get();
            case 37 -> DEF_37.get();
            case 38 -> DEF_38.get();
            case 39 -> DEF_39.get();
            case 40 -> DEF_40.get();
            case 41 -> DEF_41.get();
            case 42 -> DEF_42.get();
            case 43 -> DEF_43.get();
            case 44 -> DEF_44.get();
            case 45 -> DEF_45.get();
            case 46 -> DEF_46.get();
            case 47 -> DEF_47.get();
            case 48 -> DEF_48.get();
            case 49 -> DEF_49.get();
            case 50 -> DEF_50.get();
            case 51 -> DEF_51.get();
            case 52 -> DEF_52.get();
            case 53 -> DEF_53.get();
            case 54 -> DEF_54.get();
            case 55 -> DEF_55.get();
            case 56 -> DEF_56.get();
            case 57 -> DEF_57.get();
            case 58 -> DEF_58.get();
            case 59 -> DEF_59.get();
            case 60 -> DEF_60.get();
            case 61 -> DEF_61.get();
            case 62 -> DEF_62.get();
            case 63 -> DEF_63.get();
            case 64 -> DEF_64.get();
            case 65 -> DEF_65.get();
            case 66 -> DEF_66.get();
            case 67 -> DEF_67.get();
            case 68 -> DEF_68.get();
            case 69 -> DEF_69.get();
            case 70 -> DEF_70.get();
            case 71 -> DEF_71.get();
            case 72 -> DEF_72.get();
            case 73 -> DEF_73.get();
            case 74 -> DEF_74.get();
            case 75 -> DEF_75.get();
            case 76 -> DEF_76.get();
            case 77 -> DEF_77.get();
            case 78 -> DEF_78.get();
            case 79 -> DEF_79.get();
            case 80 -> DEF_80.get();
            case 81 -> DEF_81.get();
            case 82 -> DEF_82.get();
            case 83 -> DEF_83.get();
            case 84 -> DEF_84.get();
            case 85 -> DEF_85.get();
            case 86 -> DEF_86.get();
            case 87 -> DEF_87.get();
            case 88 -> DEF_88.get();
            case 89 -> DEF_89.get();
            case 90 -> DEF_90.get();
            case 91 -> DEF_91.get();
            case 92 -> DEF_92.get();
            case 93 -> DEF_93.get();
            case 94 -> DEF_94.get();
            case 95 -> DEF_95.get();
            case 96 -> DEF_96.get();
            default -> 0;
        };
    }

    public static int getAtk(int level) {
        return switch (level) {
            case 1 -> ATK_01.get();
            case 2 -> ATK_02.get();
            case 3 -> ATK_03.get();
            case 4 -> ATK_04.get();
            case 5 -> ATK_05.get();
            case 6 -> ATK_06.get();
            case 7 -> ATK_07.get();
            case 8 -> ATK_08.get();
            case 9 -> ATK_09.get();
            case 10 -> ATK_10.get();
            case 11 -> ATK_11.get();
            case 12 -> ATK_12.get();
            case 13 -> ATK_13.get();
            case 14 -> ATK_14.get();
            case 15 -> ATK_15.get();
            case 16 -> ATK_16.get();
            case 17 -> ATK_17.get();
            case 18 -> ATK_18.get();
            case 19 -> ATK_19.get();
            case 20 -> ATK_20.get();
            case 21 -> ATK_21.get();
            case 22 -> ATK_22.get();
            case 23 -> ATK_23.get();
            case 24 -> ATK_24.get();
            case 25 -> ATK_25.get();
            case 26 -> ATK_26.get();
            case 27 -> ATK_27.get();
            case 28 -> ATK_28.get();
            case 29 -> ATK_29.get();
            case 30 -> ATK_30.get();
            case 31 -> ATK_31.get();
            case 32 -> ATK_32.get();
            case 33 -> ATK_33.get();
            case 34 -> ATK_34.get();
            case 35 -> ATK_35.get();
            case 36 -> ATK_36.get();
            case 37 -> ATK_37.get();
            case 38 -> ATK_38.get();
            case 39 -> ATK_39.get();
            case 40 -> ATK_40.get();
            case 41 -> ATK_41.get();
            case 42 -> ATK_42.get();
            case 43 -> ATK_43.get();
            case 44 -> ATK_44.get();
            case 45 -> ATK_45.get();
            case 46 -> ATK_46.get();
            case 47 -> ATK_47.get();
            case 48 -> ATK_48.get();
            case 49 -> ATK_49.get();
            case 50 -> ATK_50.get();
            case 51 -> ATK_51.get();
            case 52 -> ATK_52.get();
            case 53 -> ATK_53.get();
            case 54 -> ATK_54.get();
            case 55 -> ATK_55.get();
            case 56 -> ATK_56.get();
            case 57 -> ATK_57.get();
            case 58 -> ATK_58.get();
            case 59 -> ATK_59.get();
            case 60 -> ATK_60.get();
            case 61 -> ATK_61.get();
            case 62 -> ATK_62.get();
            case 63 -> ATK_63.get();
            case 64 -> ATK_64.get();
            case 65 -> ATK_65.get();
            case 66 -> ATK_66.get();
            case 67 -> ATK_67.get();
            case 68 -> ATK_68.get();
            case 69 -> ATK_69.get();
            case 70 -> ATK_70.get();
            case 71 -> ATK_71.get();
            case 72 -> ATK_72.get();
            case 73 -> ATK_73.get();
            case 74 -> ATK_74.get();
            case 75 -> ATK_75.get();
            case 76 -> ATK_76.get();
            case 77 -> ATK_77.get();
            case 78 -> ATK_78.get();
            case 79 -> ATK_79.get();
            case 80 -> ATK_80.get();
            case 81 -> ATK_81.get();
            case 82 -> ATK_82.get();
            case 83 -> ATK_83.get();
            case 84 -> ATK_84.get();
            case 85 -> ATK_85.get();
            case 86 -> ATK_86.get();
            case 87 -> ATK_87.get();
            case 88 -> ATK_88.get();
            case 89 -> ATK_89.get();
            case 90 -> ATK_90.get();
            case 91 -> ATK_91.get();
            case 92 -> ATK_92.get();
            case 93 -> ATK_93.get();
            case 94 -> ATK_94.get();
            case 95 -> ATK_95.get();
            case 96 -> ATK_96.get();
            default -> 0;
        };
    }

    public static List<Integer> getAllHp() {
        return List.of(
                HP_01.get(), HP_02.get(), HP_03.get(), HP_04.get(), HP_05.get(),
                HP_06.get(), HP_07.get(), HP_08.get(), HP_09.get(), HP_10.get(),
                HP_11.get(), HP_12.get(), HP_13.get(), HP_14.get(), HP_15.get(),
                HP_16.get(), HP_17.get(), HP_18.get(), HP_19.get(), HP_20.get(),
                HP_21.get(), HP_22.get(), HP_23.get(), HP_24.get(), HP_25.get(),
                HP_26.get(), HP_27.get(), HP_28.get(), HP_29.get(), HP_30.get(),
                HP_31.get(), HP_32.get(), HP_33.get(), HP_34.get(), HP_35.get(),
                HP_36.get(), HP_37.get(), HP_38.get(), HP_39.get(), HP_40.get(),
                HP_41.get(), HP_42.get(), HP_43.get(), HP_44.get(), HP_45.get(),
                HP_46.get(), HP_47.get(), HP_48.get(), HP_49.get(), HP_50.get(),
                HP_51.get(), HP_52.get(), HP_53.get(), HP_54.get(), HP_55.get(),
                HP_56.get(), HP_57.get(), HP_58.get(), HP_59.get(), HP_60.get(),
                HP_61.get(), HP_62.get(), HP_63.get(), HP_64.get(), HP_65.get(),
                HP_66.get(), HP_67.get(), HP_68.get(), HP_69.get(), HP_70.get(),
                HP_71.get(), HP_72.get(), HP_73.get(), HP_74.get(), HP_75.get(),
                HP_76.get(), HP_77.get(), HP_78.get(), HP_79.get(), HP_80.get(),
                HP_81.get(), HP_82.get(), HP_83.get(), HP_84.get(), HP_85.get(),
                HP_86.get(), HP_87.get(), HP_88.get(), HP_89.get(), HP_90.get(),
                HP_91.get(), HP_92.get(), HP_93.get(), HP_94.get(), HP_95.get(),
                HP_96.get()
        );
    }

    public static List<Integer> getAllAtk() {
        return List.of(
                ATK_01.get(), ATK_02.get(), ATK_03.get(), ATK_04.get(), ATK_05.get(),
                ATK_06.get(), ATK_07.get(), ATK_08.get(), ATK_09.get(), ATK_10.get(),
                ATK_11.get(), ATK_12.get(), ATK_13.get(), ATK_14.get(), ATK_15.get(),
                ATK_16.get(), ATK_17.get(), ATK_18.get(), ATK_19.get(), ATK_20.get(),
                ATK_21.get(), ATK_22.get(), ATK_23.get(), ATK_24.get(), ATK_25.get(),
                ATK_26.get(), ATK_27.get(), ATK_28.get(), ATK_29.get(), ATK_30.get(),
                ATK_31.get(), ATK_32.get(), ATK_33.get(), ATK_34.get(), ATK_35.get(),
                ATK_36.get(), ATK_37.get(), ATK_38.get(), ATK_39.get(), ATK_40.get(),
                ATK_41.get(), ATK_42.get(), ATK_43.get(), ATK_44.get(), ATK_45.get(),
                ATK_46.get(), ATK_47.get(), ATK_48.get(), ATK_49.get(), ATK_50.get(),
                ATK_51.get(), ATK_52.get(), ATK_53.get(), ATK_54.get(), ATK_55.get(),
                ATK_56.get(), ATK_57.get(), ATK_58.get(), ATK_59.get(), ATK_60.get(),
                ATK_61.get(), ATK_62.get(), ATK_63.get(), ATK_64.get(), ATK_65.get(),
                ATK_66.get(), ATK_67.get(), ATK_68.get(), ATK_69.get(), ATK_70.get(),
                ATK_71.get(), ATK_72.get(), ATK_73.get(), ATK_74.get(), ATK_75.get(),
                ATK_76.get(), ATK_77.get(), ATK_78.get(), ATK_79.get(), ATK_80.get(),
                ATK_81.get(), ATK_82.get(), ATK_83.get(), ATK_84.get(), ATK_85.get(),
                ATK_86.get(), ATK_87.get(), ATK_88.get(), ATK_89.get(), ATK_90.get(),
                ATK_91.get(), ATK_92.get(), ATK_93.get(), ATK_94.get(), ATK_95.get(),
                ATK_96.get()
        );
    }

    public static List<Integer> getAllDef() {
        return List.of(
                DEF_01.get(), DEF_02.get(), DEF_03.get(), DEF_04.get(), DEF_05.get(),
                DEF_06.get(), DEF_07.get(), DEF_08.get(), DEF_09.get(), DEF_10.get(),
                DEF_11.get(), DEF_12.get(), DEF_13.get(), DEF_14.get(), DEF_15.get(),
                DEF_16.get(), DEF_17.get(), DEF_18.get(), DEF_19.get(), DEF_20.get(),
                DEF_21.get(), DEF_22.get(), DEF_23.get(), DEF_24.get(), DEF_25.get(),
                DEF_26.get(), DEF_27.get(), DEF_28.get(), DEF_29.get(), DEF_30.get(),
                DEF_31.get(), DEF_32.get(), DEF_33.get(), DEF_34.get(), DEF_35.get(),
                DEF_36.get(), DEF_37.get(), DEF_38.get(), DEF_39.get(), DEF_40.get(),
                DEF_41.get(), DEF_42.get(), DEF_43.get(), DEF_44.get(), DEF_45.get(),
                DEF_46.get(), DEF_47.get(), DEF_48.get(), DEF_49.get(), DEF_50.get(),
                DEF_51.get(), DEF_52.get(), DEF_53.get(), DEF_54.get(), DEF_55.get(),
                DEF_56.get(), DEF_57.get(), DEF_58.get(), DEF_59.get(), DEF_60.get(),
                DEF_61.get(), DEF_62.get(), DEF_63.get(), DEF_64.get(), DEF_65.get(),
                DEF_66.get(), DEF_67.get(), DEF_68.get(), DEF_69.get(), DEF_70.get(),
                DEF_71.get(), DEF_72.get(), DEF_73.get(), DEF_74.get(), DEF_75.get(),
                DEF_76.get(), DEF_77.get(), DEF_78.get(), DEF_79.get(), DEF_80.get(),
                DEF_81.get(), DEF_82.get(), DEF_83.get(), DEF_84.get(), DEF_85.get(),
                DEF_86.get(), DEF_87.get(), DEF_88.get(), DEF_89.get(), DEF_90.get(),
                DEF_91.get(), DEF_92.get(), DEF_93.get(), DEF_94.get(), DEF_95.get(),
                DEF_96.get()
        );
    }
}