package com.linweiyun.genshin.core.system.compat;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 与其他 MOD 的兼容配置（{@code minegenshin/compatibility.toml}）。
 *
 * <p>两颗系数就是本模块的全部可调项，默认值即需求里写死的那两个数：
 * 原神模式下玩家原版攻击力 × 15 进角色基础攻击力；关掉原神模式后角色属性 × 0.7 落到玩家身上。
 */
public class CompatConfig {

    /** 兼容模块总开关。关掉 = 完全回到「其他 MOD 的伤害不换算」的旧行为 */
    public static ModConfigSpec.BooleanValue ENABLE;

    /** 原神模式：玩家原版攻击力 × 这个值，作为 {@code minecraft} 来源写进当前角色的基础攻击力 */
    public static ModConfigSpec.DoubleValue PLAYER_ATTACK_SCALE;

    /** 非原神模式：角色属性 × 这个值，直接作用在玩家身上 */
    public static ModConfigSpec.DoubleValue CHARACTER_STAT_SCALE;

    public static void register(ModConfigSpec.Builder builder) {
        builder.push("compatibility");

        ENABLE = builder
                .comment("与其他 MOD 的战斗兼容总开关。关掉后其他 MOD 打出的伤害不再按角色口径换算，"
                        + "非原神模式也不再往玩家身上套角色属性")
                .define("enable", true);

        PLAYER_ATTACK_SCALE = builder
                .comment("原神模式：玩家原版的攻击力属性 × 该值，加到当前角色的基础攻击力上（来源写 minecraft）。"
                        + "这条加成只在原神模式生效，退出时移除")
                .defineInRange("player_attack_scale", 15.0D, 0.0D, 1000.0D);

        CHARACTER_STAT_SCALE = builder
                .comment("非原神模式：当前角色的生命上限 / 攻击力 × 该值，直接加到玩家身上（防御力不参与）。"
                        + "玩家生命上限 = 自己的上限 + 角色上限 × 该值；角色生命值按同一倍率在两边折算")
                .defineInRange("character_stat_scale", 0.7D, 0.0D, 100.0D);

        builder.pop();
    }

    public static boolean enabled() {
        return ENABLE.get();
    }

    public static double playerAttackScale() {
        return PLAYER_ATTACK_SCALE.get();
    }

    public static double characterStatScale() {
        return CHARACTER_STAT_SCALE.get();
    }
}
