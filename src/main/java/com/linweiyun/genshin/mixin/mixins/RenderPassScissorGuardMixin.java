package com.linweiyun.genshin.mixin.mixins;

import com.mojang.blaze3d.systems.RenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 把「尺寸为 0 的裁剪框」挡在引擎前面：宽度/高度小于 1 时夹到 1 像素。
 *
 * <h2>为什么需要这个兜底</h2>
 * LDLib2 的精确裁剪（{@code PreciseScissor.quantize}，26.2.2.39）把带小数的 GUI 矩形按
 * {@code Math.round(edge * guiScale)} 量化到物理像素，宽度用 {@code Math.max(0, x1 - x0)}
 * 兜负值 —— 「窄于一个物理像素」的裁剪会被量化成 {@code 0x…}，而 LDLib2 源码注释里写明这是
 * <b>有意接受</b>的结果（{@code ClipRect.EMPTY = (0,0,0,0)}，语义是「什么都不画」）。
 * 它注入在 {@code GuiRenderer.enableScissor} 的 HEAD，量化完直接调
 * {@code renderPass.enableScissor(...)}，不做任何非零检查。
 *
 * <p>但 MC 26.2 的 {@code RenderPass#enableScissor(int, int, int, int)} 只接受
 * {@code width > 0 && height > 0}，否则
 * {@code throw new IllegalArgumentException("Scissor size must be >0, was " + w + "x" + h)}。
 * 两边口径冲突：同样一个退化成 0 宽的裁剪框，LDLib2 当合法、26.2 当调用错误，表现就是
 * <b>打开界面的第一帧直接崩在 {@code GuiRenderer.executeDraw}</b>（本项目实测
 * {@code Scissor size must be >0, was 0x32}，栈顶是
 * {@code Minecraft.setScreenAndShow ← ScreenNavigator.openCharacterConfigScreen}）。
 *
 * <h2>为什么不是在界面代码里「小心一点」</h2>
 * 界面侧当然该把尺寸写对（本项目已经给所有会裁剪的元素补了 INLINE 的最小尺寸，见
 * {@code ShenheConfigUI}），但裁剪框是不是 0 由<b>布局在任意一帧的挤压结果</b>决定，
 * 而不是某一行代码写错 —— 窗口被拉小、GUI 缩放变大、上个版本没预料到的嵌套百分比，
 * 都可能让某个内容盒在这一帧变成 0。引擎侧的这条不变式（「裁剪框至少 1 像素」）才是
 * 唯一能保证「以后不会再因为同一类原因崩」的地方：0 尺寸下夹到 1 像素，代价是可能多画
 * 一行/一列几乎不可见的像素，收益是任何 LDLib2 界面（含别的模组的）都不会再因此崩。
 *
 * <h2>{@code require = 0} 是刻意的</h2>
 * 引擎方法签名一旦变动，注入失败只记一条警告、不会让客户端启动崩掉 —— 那时退化成
 * 「和没有这个 mixin 一样」，也就是回到「界面侧必须自己保证非零尺寸」的现状，属于降级而不是事故。
 */
@Mixin(RenderPass.class)
public class RenderPassScissorGuardMixin {

    /**
     * {@code enableScissor(int x, int y, int width, int height)} —— 第 3 个 int 参数。
     * 用 {@code ordinal} 而不是参数名定位：生产环境里参数名不可靠，序号才稳。
     */
    @ModifyVariable(
            method = "enableScissor(IIII)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 2,
            require = 0)
    private int minegenshin$clampScissorWidth(int width) {
        return Math.max(1, width);
    }

    /** 同上，第 4 个 int 参数（{@code height}）。 */
    @ModifyVariable(
            method = "enableScissor(IIII)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 3,
            require = 0)
    private int minegenshin$clampScissorHeight(int height) {
        return Math.max(1, height);
    }
}
