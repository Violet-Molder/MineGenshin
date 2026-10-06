package com.linweiyun.genshin.mixin.mixins;

import com.linweiyun.elementlib.core.system.about.block.BlockElementStore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.jspecify.annotations.Nullable;

/**
 * <b>冰的「破坏即变水」守卫</b> —— 属于元素体系的冰，融化只能由元素链决定。
 *
 * <p>原版 {@code IceBlock.playerDestroy}：不是精准采集、且下方方块是固体或液体时，
 * 会把这一格<b>写成水</b>。也就是说「挖掉冰 → 出现水」这条转化与原神反应毫无关系，
 * 只是表现上像融化 —— 用手的破坏得到一个只有反应才能产生的结果，内核就被绕过去了。
 *
 * <p>本 Mixin 只拦<b>元素生成的浮冰</b>（{@code FROSTED_ICE} 且这一格身上有元素容器，
 * 即「水遇冰结出来的浮冰」）：它的消融必须由 {@code BlockElementMigrations} 里的元素规则决定。
 * 天然冰（冰 / 浮冰砖 / 蓝冰）与玩家没碰过的方块<b>一律保持原版行为</b>
 * —— 原版「挖冰出水」是挖掘这件事的物理结果，不属于元素链，我们不去改它。
 */
@Mixin(IceBlock.class)
public abstract class IceBlockMeltGuardMixin {

    @Inject(method = "playerDestroy", at = @At("HEAD"), cancellable = true)
    private void genshin$blockDestroyMelt(Level level, Player player, BlockPos pos, BlockState state,
                                          @Nullable BlockEntity blockEntity, ItemStack destroyedWith,
                                          CallbackInfo ci) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // 只拦元素生成的浮冰；天然冰族保持原版
        if (!state.is(Blocks.FROSTED_ICE)) {
            return;
        }
        // 这一格在元素体系里（有容器）→ 不允许原版用「破坏」把它变成水。
        // 元素态跟着方块一起消失：否则原坐标会留下「水 + 残留 FROZEN 容器」，
        // 这块水下次被任何元素一碰就会又冻回去。
        if (BlockElementStore.peek(serverLevel, pos) != null) {
            BlockElementStore.clear(serverLevel, pos);
            ci.cancel();
        }
    }
}
