package com.linweiyun.genshin.core.world;

import com.linweiyun.genshin.Minegenshin;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

/**
 * 本 MOD 的游戏规则。
 *
 * <p>26.2 的内置注册表在模组构造期已经冻结，只有在 {@link RegisterEvent} 期间才会被 NeoForge
 * 还原成可写状态，所以注册挂在 {@link #register(IEventBus)} 上，由模组构造调用一次。
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
    @Nullable
    public static GameRule<Boolean> ATTACK_BREAKS_BLOCKS;

    private ModGameRules() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(RegisterEvent.class, ModGameRules::onRegister);
    }

    private static void onRegister(RegisterEvent event) {
        if (Registries.GAME_RULE.equals(event.getRegistryKey())) {
            ATTACK_BREAKS_BLOCKS = GameRules.registerBoolean(
                    Minegenshin.MOD_ID + ":attack_breaks_blocks", GameRuleCategory.MISC, false);
        }
    }
}
