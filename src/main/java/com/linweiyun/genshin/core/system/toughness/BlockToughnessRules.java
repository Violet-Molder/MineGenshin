package com.linweiyun.genshin.core.system.toughness;

import com.linweiyun.genshin.config.PoiseConfig;
import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.world.ModGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 方块韧性表。
 *
 * <p>谁能被攻击破坏由三档决定，顺序如下：
 * <ol>
 *   <li>{@link #register} 显式登记过的 —— 永远参与；</li>
 *   <li>{@link #ATTACK_BREAKABLE} 标签里的 —— 参与；</li>
 *   <li>其余方块：游戏规则 {@code minegenshin:attack_breaks_blocks} 打开才按硬度换算参与
 *       （默认关闭，也就是打不动）。</li>
 * </ol>
 */
public final class BlockToughnessRules {

    public static final float NOT_PARTICIPATING = -1f;

    /** 「可以被攻击破坏」的方块标签 —— 提瓦特矿物之类以后加进这个标签即可。 */
    public static final TagKey<Block> ATTACK_BREAKABLE =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Minegenshin.MOD_ID, "attack_breakable"));

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
        if (!state.is(ATTACK_BREAKABLE) && !attackBreakingEnabled(level)) {
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

    /** 这个方块有没有被「点名」可破坏（不看作弊规则）。 */
    public static boolean namedBreakable(BlockState state) {
        return state != null && state.is(ATTACK_BREAKABLE);
    }

    private static boolean attackBreakingEnabled(@Nullable BlockGetter level) {
        return level instanceof Level world
                && world.getGameRules().getBoolean(ModGameRules.ATTACK_BREAKS_BLOCKS);
    }
}
