package com.linweiyun.genshin.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import javax.annotation.Nullable;

/**
 * 韧性系统的配置（{@code minegenshin/poise.toml}）。
 *
 * <p>韧性本身的数值（每档条长、每秒衰减、重置时间、武器基准表）都写在代码里，
 * 因为它们要么是「一张表改一处」的常量，要么以后会接进逐角色的配置 ——
 * 这里只放<b>开关类</b>的东西：那些一句话就能说清、而且真有人会想关掉的规则。
 */
public class PoiseConfig {

    /**
     * 冻结是不是直接破韧。
     *
     * <p>用户口径：「冻结可以无视韧性直接冻结，或者说直接破韧也行」。
     * 默认<b>开</b>：冻住的那一瞬间把目标的韧性判为破韧（0 削韧值、进入破韧驻留），
     * 于是碎冰、被冻住的怪挨打会掉得更多这类后续表现都能接上；
     * 冻结<b>本身照常生效</b>，这个开关不会让冻结失效。
     *
     * <p>关掉就完全回到旧行为：冻结照常冻，但韧性条不动。
     */
    public static ModConfigSpec.BooleanValue FREEZE_FORCE_BREAK;

    /**
     * 反应是不是自带削韧与冲击。
     *
     * <p>默认<b>开</b>：超载 90 / 击飞、扩散 130、感电 130 这些按
     * {@code ReactionPoiseTable} 那张表走。关掉只影响「反应额外削的那一笔」，
     * 攻击本身的削韧照旧。
     */
    public static ModConfigSpec.BooleanValue REACTION_POISE;

    /**
     * 方块韧性：硬度换算倍率。
     *
     * <p>实际韧性 = 硬度 × 179 × 本值。默认 <b>3</b>；调大 = 方块更耐打。
     */
    public static ModConfigSpec.DoubleValue BLOCK_HARDNESS_MULTIPLIER;

    /**
     * 读一个开关。
     *
     * <p>这两个开关都在「每次冻结 / 每次反应伤害」的热路径上，而配置对象在
     * 模组构造期才建好；静态初始化中间态（或某个提前跑起来的测试）里读它会抛。
     * 那种情况下按<b>开</b>处理 —— 默认行为就是开，宁可按默认跑也不要为了读个配置把战斗打断。
     */
    public static boolean isOn(@Nullable ModConfigSpec.BooleanValue value) {
        try {
            return value == null || value.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** 读一个数值；配置还没建好（或读失败）时用 fallback。 */
    public static double value(@Nullable ModConfigSpec.DoubleValue value, double fallback) {
        try {
            return value == null ? fallback : value.get();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public static void register(ModConfigSpec.Builder builder) {
        builder.push("freeze");
        FREEZE_FORCE_BREAK = builder
                .translation("minegenshin.configuration.poise.freeze_force_break")
                .comment("冻结直接破韧：冻住的那一刻把目标韧性判为破韧（冻结本身照常生效）。关掉 = 冻结照常但不碰韧性")
                .define("force-break", true);
        builder.pop();

        builder.push("reaction");
        REACTION_POISE = builder
                .translation("minegenshin.configuration.poise.reaction_poise")
                .comment("反应自带削韧与冲击：超载 90/击飞、扩散 130、感电 130…（见 ReactionPoiseTable）。关掉只去掉反应额外削的那一笔")
                .define("poise", true);
        builder.pop();

        builder.push("block");
        BLOCK_HARDNESS_MULTIPLIER = builder
                .translation("minegenshin.configuration.poise.block_hardness_multiplier")
                .comment("方块韧性换算倍率：实际韧性 = 硬度 × 179 × 本值。默认 3，调大 = 方块更耐打")
                .defineInRange("hardness-multiplier", 3.0D, 0.0D, 1000.0D);
        builder.pop();
    }
}
