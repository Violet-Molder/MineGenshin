package com.linweiyun.genshin.config.character;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;

public class ColumbinaAttributeConfig {

    public static ModConfigSpec.IntValue HP_01, HP_02, HP_03, HP_04, HP_05, HP_06, HP_07, HP_08, HP_09, HP_10, HP_11, HP_12;

    public static ModConfigSpec.IntValue DEF_01, DEF_02, DEF_03, DEF_04, DEF_05, DEF_06, DEF_07, DEF_08, DEF_09, DEF_10;
    public static ModConfigSpec.IntValue DEF_11, DEF_12, DEF_13, DEF_14, DEF_15, DEF_16, DEF_17, DEF_18, DEF_19, DEF_20;
    public static ModConfigSpec.IntValue DEF_21;

    public static ModConfigSpec.IntValue ATK_01, ATK_02, ATK_03, ATK_04, ATK_05, ATK_06, ATK_07, ATK_08, ATK_09, ATK_10;
    public static ModConfigSpec.IntValue ATK_11, ATK_12, ATK_13, ATK_14, ATK_15, ATK_16, ATK_17, ATK_18, ATK_19, ATK_20;
    public static ModConfigSpec.IntValue ATK_21;

    static void register(ModConfigSpec.Builder builder) {
        builder.push("attribute");

        builder.push("hp");
        HP_01 = builder.translation("minegenshin.configuration.level.1").defineInRange("lv1", 1144, 0, 1000000);
        HP_02 = builder.translation("minegenshin.configuration.level.2").defineInRange("lv2", 1239, 0, 1000000);
        HP_03 = builder.translation("minegenshin.configuration.level.3").defineInRange("lv3", 1334, 0, 1000000);
        HP_04 = builder.translation("minegenshin.configuration.level.4").defineInRange("lv4", 1430, 0, 1000000);
        HP_05 = builder.translation("minegenshin.configuration.level.5").defineInRange("lv5", 1525, 0, 1000000);
        HP_06 = builder.translation("minegenshin.configuration.level.6").defineInRange("lv6", 1621, 0, 1000000);
        HP_07 = builder.translation("minegenshin.configuration.level.7").defineInRange("lv7", 1716, 0, 1000000);
        HP_08 = builder.translation("minegenshin.configuration.level.8").defineInRange("lv8", 1812, 0, 1000000);
        HP_09 = builder.translation("minegenshin.configuration.level.9").defineInRange("lv9", 1908, 0, 1000000);
        HP_10 = builder.translation("minegenshin.configuration.level.10").defineInRange("lv10", 2003, 0, 1000000);
        HP_11 = builder.translation("minegenshin.configuration.level.11").defineInRange("lv11", 2099, 0, 1000000);
        HP_12 = builder.translation("minegenshin.configuration.level.12").defineInRange("lv12", 2195, 0, 1000000);
        builder.pop();

        builder.push("def");
        DEF_01 = builder.translation("minegenshin.configuration.level.1").defineInRange("lv1", 10, 0, 100000);
        DEF_02 = builder.translation("minegenshin.configuration.level.2").defineInRange("lv2", 12, 0, 100000);
        DEF_03 = builder.translation("minegenshin.configuration.level.3").defineInRange("lv3", 14, 0, 100000);
        DEF_04 = builder.translation("minegenshin.configuration.level.4").defineInRange("lv4", 16, 0, 100000);
        DEF_05 = builder.translation("minegenshin.configuration.level.5").defineInRange("lv5", 18, 0, 100000);
        DEF_06 = builder.translation("minegenshin.configuration.level.6").defineInRange("lv6", 20, 0, 100000);
        DEF_07 = builder.translation("minegenshin.configuration.level.7").defineInRange("lv7", 22, 0, 100000);
        DEF_08 = builder.translation("minegenshin.configuration.level.8").defineInRange("lv8", 24, 0, 100000);
        DEF_09 = builder.translation("minegenshin.configuration.level.9").defineInRange("lv9", 26, 0, 100000);
        DEF_10 = builder.translation("minegenshin.configuration.level.10").defineInRange("lv10", 28, 0, 100000);
        DEF_11 = builder.translation("minegenshin.configuration.level.11").defineInRange("lv11", 30, 0, 100000);
        DEF_12 = builder.translation("minegenshin.configuration.level.12").defineInRange("lv12", 32, 0, 100000);
        DEF_13 = builder.translation("minegenshin.configuration.level.13").defineInRange("lv13", 34, 0, 100000);
        DEF_14 = builder.translation("minegenshin.configuration.level.14").defineInRange("lv14", 36, 0, 100000);
        DEF_15 = builder.translation("minegenshin.configuration.level.15").defineInRange("lv15", 38, 0, 100000);
        DEF_16 = builder.translation("minegenshin.configuration.level.16").defineInRange("lv16", 40, 0, 100000);
        DEF_17 = builder.translation("minegenshin.configuration.level.17").defineInRange("lv17", 42, 0, 100000);
        DEF_18 = builder.translation("minegenshin.configuration.level.18").defineInRange("lv18", 44, 0, 100000);
        DEF_19 = builder.translation("minegenshin.configuration.level.19").defineInRange("lv19", 46, 0, 100000);
        DEF_20 = builder.translation("minegenshin.configuration.level.20").defineInRange("lv20", 48, 0, 100000);
        DEF_21 = builder.translation("minegenshin.configuration.level.20b").defineInRange("lv20b", 50, 0, 100000);
        builder.pop();

        builder.push("atk");
        ATK_01 = builder.translation("minegenshin.configuration.level.1").defineInRange("lv1", 5, 0, 100000);
        ATK_02 = builder.translation("minegenshin.configuration.level.2").defineInRange("lv2", 6, 0, 100000);
        ATK_03 = builder.translation("minegenshin.configuration.level.3").defineInRange("lv3", 7, 0, 100000);
        ATK_04 = builder.translation("minegenshin.configuration.level.4").defineInRange("lv4", 8, 0, 100000);
        ATK_05 = builder.translation("minegenshin.configuration.level.5").defineInRange("lv5", 9, 0, 100000);
        ATK_06 = builder.translation("minegenshin.configuration.level.6").defineInRange("lv6", 10, 0, 100000);
        ATK_07 = builder.translation("minegenshin.configuration.level.7").defineInRange("lv7", 11, 0, 100000);
        ATK_08 = builder.translation("minegenshin.configuration.level.8").defineInRange("lv8", 12, 0, 100000);
        ATK_09 = builder.translation("minegenshin.configuration.level.9").defineInRange("lv9", 13, 0, 100000);
        ATK_10 = builder.translation("minegenshin.configuration.level.10").defineInRange("lv10", 14, 0, 100000);
        ATK_11 = builder.translation("minegenshin.configuration.level.11").defineInRange("lv11", 15, 0, 100000);
        ATK_12 = builder.translation("minegenshin.configuration.level.12").defineInRange("lv12", 16, 0, 100000);
        ATK_13 = builder.translation("minegenshin.configuration.level.13").defineInRange("lv13", 17, 0, 100000);
        ATK_14 = builder.translation("minegenshin.configuration.level.14").defineInRange("lv14", 18, 0, 100000);
        ATK_15 = builder.translation("minegenshin.configuration.level.15").defineInRange("lv15", 19, 0, 100000);
        ATK_16 = builder.translation("minegenshin.configuration.level.16").defineInRange("lv16", 20, 0, 100000);
        ATK_17 = builder.translation("minegenshin.configuration.level.17").defineInRange("lv17", 21, 0, 100000);
        ATK_18 = builder.translation("minegenshin.configuration.level.18").defineInRange("lv18", 22, 0, 100000);
        ATK_19 = builder.translation("minegenshin.configuration.level.19").defineInRange("lv19", 23, 0, 100000);
        ATK_20 = builder.translation("minegenshin.configuration.level.20").defineInRange("lv20", 24, 0, 100000);
        ATK_21 = builder.translation("minegenshin.configuration.level.20b").defineInRange("lv20b", 25, 0, 100000);
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
            default -> 0;
        };
    }

    public static List<Integer> getAllHp() {
        return List.of(
                HP_01.get(), HP_02.get(), HP_03.get(), HP_04.get(), HP_05.get(),
                HP_06.get(), HP_07.get(), HP_08.get(), HP_09.get(), HP_10.get(),
                HP_11.get(), HP_12.get()
        );
    }

    public static List<Integer> getAllAtk() {
        return List.of(
                ATK_01.get(), ATK_02.get(), ATK_03.get(), ATK_04.get(), ATK_05.get(),
                ATK_06.get(), ATK_07.get(), ATK_08.get(), ATK_09.get(), ATK_10.get(),
                ATK_11.get(), ATK_12.get(), ATK_13.get(), ATK_14.get(), ATK_15.get(),
                ATK_16.get(), ATK_17.get(), ATK_18.get(), ATK_19.get(), ATK_20.get(),
                ATK_21.get()
        );
    }

    public static List<Integer> getAllDef() {
        return List.of(
                DEF_01.get(), DEF_02.get(), DEF_03.get(), DEF_04.get(), DEF_05.get(),
                DEF_06.get(), DEF_07.get(), DEF_08.get(), DEF_09.get(), DEF_10.get(),
                DEF_11.get(), DEF_12.get(), DEF_13.get(), DEF_14.get(), DEF_15.get(),
                DEF_16.get(), DEF_17.get(), DEF_18.get(), DEF_19.get(), DEF_20.get(),
                DEF_21.get()
        );
    }
}