package com.linweiyun.genshin.core.system.toughness;

import com.linweiyun.genshin.config.PoiseConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** 方块韧性表：显式注册优先，未注册的按硬度换算。 */
public final class BlockToughnessRules {

    public static final float NOT_PARTICIPATING = -1f;

    /** 大剑两刀消耗石头 80% 推出：214.8 / 0.8 / 1.5 = 179。 */
    public static final float HARDNESS_TO_TOUGHNESS = 179f;

    /** 配置里的换算倍率默认值。 */
    public static final float DEFAULT_HARDNESS_MULTIPLIER = 3f;

    private record Entry(Predicate<BlockState> matcher, float toughness) {
    }

    private static final List<Entry> RULES = new ArrayList<>();

    private BlockToughnessRules() {
    }

    public static void register(Predicate<BlockState> matcher, float toughness) {
        RULES.add(new Entry(matcher, toughness));
    }

    public static float toughnessOf(BlockState state, @Nullable BlockGetter level, @Nullable BlockPos pos) {
        if (state == null) {
            return NOT_PARTICIPATING;
        }
        for (Entry entry : RULES) {
            if (entry.matcher().test(state)) {
                return entry.toughness();
            }
        }
        // 流体不参与：硬度对流体没有意义，武器也破坏不了水/岩浆（含其它模组的液体方块）
        if (state.isAir() || state.is(Blocks.WATER) || state.is(Blocks.LAVA)
                || state.getBlock() instanceof LiquidBlock) {
            return NOT_PARTICIPATING;
        }
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness <= 0f) {
            return NOT_PARTICIPATING;
        }
        float multiplier = (float) PoiseConfig.value(
                PoiseConfig.BLOCK_HARDNESS_MULTIPLIER, DEFAULT_HARDNESS_MULTIPLIER);
        return hardness * HARDNESS_TO_TOUGHNESS * multiplier;
    }

    public static boolean participates(BlockState state, @Nullable BlockGetter level, @Nullable BlockPos pos) {
        return toughnessOf(state, level, pos) > 0f;
    }
}
