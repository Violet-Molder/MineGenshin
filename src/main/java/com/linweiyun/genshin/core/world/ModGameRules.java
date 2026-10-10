package com.linweiyun.genshin.core.world;

import net.minecraft.world.level.GameRules;

/**
 * 本 MOD 的游戏规则。
 *
 * <p>游戏规则必须在任何世界加载之前注册完，所以 {@link #register()} 要在模组构造里调一次
 * （调用它会让这个类的静态字段完成初始化，注册就发生在那一步）。
 */
public final class ModGameRules {

    /**
     * 攻击能不能破坏方块。
     *
     * <p>{@code false}（默认）：只有被点名的方块参与韧性 —— 写进
     * {@code #minegenshin:attack_breakable} 标签的、或 {@code BlockToughnessRules.register}
     * 显式登记过的；其余方块一律打不动（元素附着照常）。
     * <p>{@code true}：所有有硬度的方块都参与，行为与「按硬度换算」那套一致。
     */
    public static final GameRules.Key<GameRules.BooleanValue> ATTACK_BREAKS_BLOCKS = GameRules.register(
            "minegenshin:attack_breaks_blocks", GameRules.Category.MISC, GameRules.BooleanValue.create(false));

    private ModGameRules() {
    }

    public static void register() {
    }
}
