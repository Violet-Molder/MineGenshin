package com.linweiyun.genshin.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * <b>游戏机制</b>配置（{@code minegenshin/gameplay.toml}）。
 *
 * <p>和别的配置文件一样，这里只放「一句话能说清、而且真有人会想改」的规则开关 / 倍率；
 * 具体数值表（摔伤曲线、前摇刻数这类）都写在代码里，就近看得见。
 */
public class GameplayConfig {

    /**
     * 原神模式飞行时，鞘翅（滑翔装备）的耐久消耗倍率 —— 相对原版。
     *
     * <p>原版滑翔是<b>每 20 刻扣 1 点</b>耐久；飞行的速度就是照这个换算的
     * （{@code 20 / 倍率} 刻扣 1 点，取整后不足 1 刻按 1 刻算）。
     * 默认 <b>2.5</b>：自由飞行比滑翔强得多（能悬停、能上下），代价就打在这儿。
     */
    public static ModConfigSpec.DoubleValue FLIGHT_ELYTRA_DURABILITY_MULTIPLIER;

    /** 取倍率，配置还没建好 / 读失败时按默认 2.5 走。 */
    public static double flightElytraDurabilityMultiplier() {
        try {
            return FLIGHT_ELYTRA_DURABILITY_MULTIPLIER == null
                    ? DEFAULT_FLIGHT_ELYTRA_DURABILITY_MULTIPLIER
                    : FLIGHT_ELYTRA_DURABILITY_MULTIPLIER.get();
        } catch (RuntimeException e) {
            return DEFAULT_FLIGHT_ELYTRA_DURABILITY_MULTIPLIER;
        }
    }

    /** 默认倍率（也是配置读不到时的兜底）。 */
    public static final double DEFAULT_FLIGHT_ELYTRA_DURABILITY_MULTIPLIER = 2.5;

    public static void register(ModConfigSpec.Builder builder) {
        builder.push("flight");
        FLIGHT_ELYTRA_DURABILITY_MULTIPLIER = builder
                .translation("minegenshin.configuration.gameplay.flight_elytra_durability_multiplier")
                .comment("原神模式飞行时鞘翅的耐久消耗倍率（相对原版每 20 刻扣 1 点）。"
                        + "默认 2.5：自由飞行能悬停、能上下，代价就打在这儿")
                .defineInRange("elytra-durability-multiplier",
                        DEFAULT_FLIGHT_ELYTRA_DURABILITY_MULTIPLIER, 0.0, 100.0);
        builder.pop();
    }
}
