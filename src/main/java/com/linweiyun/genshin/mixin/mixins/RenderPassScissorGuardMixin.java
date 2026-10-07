package com.linweiyun.genshin.mixin.mixins;

import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 把「尺寸小于 1 像素的裁剪框」挡在引擎前面：宽度/高度夹到至少 1。
 *
 * <p>LDLib2 的精确裁剪把带小数的 GUI 矩形量化到物理像素，宽度用
 * {@code Math.max(0, x1 - x0)} 兜负值 —— 「窄于一个物理像素」的裁剪会被量化成 0 宽。
 * 引擎侧的这条不变式（裁剪框至少 1 像素）保证界面不会因为某一帧的布局挤压而拿到
 * 0 尺寸矩形。</p>
 *
 * <p>{@code require = 0}：引擎方法签名一旦变动，注入失败只记一条警告，
 * 行为与本 mixin 不存在时相同。</p>
 */
@Mixin(RenderSystem.class)
public class RenderPassScissorGuardMixin {

    /**
     * {@code enableScissor(int x, int y, int width, int height)} 的第 3 个 int 参数（宽度）。
     * 用 {@code ordinal} 而不是参数名定位：生产环境里参数名不可靠，序号才稳。
     * 目标方法是静态的，所以处理方法是静态的。
     */
    @ModifyVariable(
            method = "enableScissor(IIII)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 2,
            require = 0)
    private static int minegenshin$clampScissorWidth(int width) {
        return Math.max(1, width);
    }

    /** 同上，第 4 个 int 参数（{@code height}）。 */
    @ModifyVariable(
            method = "enableScissor(IIII)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 3,
            require = 0)
    private static int minegenshin$clampScissorHeight(int height) {
        return Math.max(1, height);
    }
}
