package com.linweiyun.genshin.client.render.gui.component;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.system.poise.PoiseService;
import com.linweiyun.genshin.core.system.poise.PoiseState;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.integration.kjs.KJSBindings;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.appliedenergistics.yoga.YogaOverflow;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 怪物血条下面那条<b>削韧条</b> —— 和 {@link MobHealthBar} 并排写的同一个范式。
 *
 * <h2>它画什么</h2>
 * 数据源读 {@link PoiseService#ratio}（破韧期间恒为 0），所以：
 * <ul>
 *   <li>没破韧：金色条按「已攒削韧 ÷ 这一档的长度」从右往左长；</li>
 *   <li>破韧：条是空的，样式类切到 {@code broken}（LSS 里可以给它换个色）。</li>
 * </ul>
 *
 * <p>「要不要画」由调用方决定：{@link PoiseService#peek} 拿不到状态（这只怪根本没挨过削韧）
 * 就不画，免得每只路过的怪都顶一条空槽。
 */
@ParametersAreNonnullByDefault
@KJSBindings
@LDLRegister(name = "mob-poise-bar", group = "minegenshin", registry = "ldlib2:ui_element")
public class MobPoiseBar extends ProgressBar {

    /**
     * 开了 {@code overflow: hidden} 的裁剪层至少要占这么多<b>物理像素</b>，不足就整层不画。
     *
     * <p>和 {@link HPProgressBar#MIN_CLIP_PIXELS} 同一件事、同一个理由：
     * LDLib2 把裁剪框四舍五入到物理像素，宽度不到半个像素就是 0 宽，
     * 而 {@code RenderPass#enableScissor} 碰到 0 宽/0 高会直接抛
     * {@code IllegalArgumentException: Scissor size must be >0}，把渲染线程打崩。
     * 削韧条尤其容易踩到这条：破韧瞬间比例正好是 0。
     */
    private static final float MIN_CLIP_PIXELS = 1.0f;

    public final UIElement barIcon;

    private LivingEntity trackedEntity;

    public MobPoiseBar() {
        this.barContainer.layout(layout -> {
            layout.paddingAll(0);
            layout.widthPercent(100);
            layout.heightPercent(100);
        });
        this.bar
                .addChild(barIcon = new UIElement())
                .layout(layout -> {
                    layout.positionType(TaffyPosition.ABSOLUTE);
                    layout.overflow(YogaOverflow.HIDDEN);
                })
                .style(s -> s.background(SpriteTexture.of(
                        ResourceLocation.fromNamespaceAndPath("minegenshin", "gui/empty.png"))));

        // 填充纹理（复用血条那张白条，颜色交给 LSS 的样式类）
        this.barIcon.style(s -> s.background(SpriteTexture.of(
                Minegenshin.id("gui/short_character_hp_bar_white.png"))));

        // 空槽背景纹理
        this.barContainer(c -> c.style(s -> s.background(
                SpriteTexture.of(Minegenshin.id("gui/short_character_hp_green.png")))));

        addClass("normal");
        this.label.setText("");

        // HUD 的 ldlib2 层是「边渲染边 tick」，这条监听每帧来一次 ——
        // 比例没变时基类不会重写填充条宽度，但界面缩放、窗口大小、布局刚算出来
        // 都会让「够不够 1 像素」的答案变掉，所以要跟着每帧复查一次。
        addEventListener(UIEvents.TICK, event -> refreshClipVisibility());
    }

    /**
     * 基类每次重写填充条（{@link #bar}）的宽度都会经过这里，紧跟其后判一次这层还该不该画。
     *
     * <p>必须和写宽度在同一次调用里判：写宽度在 ldlib2 的 tick 阶段，布局与绘制排在它之后，
     * 中间没有缓冲 —— 比例刚跳到 0 的那一帧就会直接把 0 宽裁剪框交下去。
     */
    @Override
    protected void updateProgressBarStyle(float normalizedValue) {
        super.updateProgressBarStyle(normalizedValue);
        applyClipVisibility(bar, normalizedValue);
    }

    /** 每帧复查填充层的裁剪框（界面缩放变了 / 首帧量不到宽度时要把这层补回来）。 */
    private void refreshClipVisibility() {
        applyClipVisibility(bar, getNormalizedValue());
    }

    /**
     * 按「裁剪框两个方向都够 {@link #MIN_CLIP_PIXELS} 个物理像素」写这一层的显隐。
     *
     * <p>判据取 {@code layer.isDisplayed()} 的<b>实时状态</b>，不能缓存成布尔字段：
     * 裁剪层的默认状态就是可见，而「还没判过」和「判成不可见」都是 false，
     * 两者分不开 —— 一条从首帧起宽度就是 0 的条会永远不写 {@code setDisplay(false)}，
     * 于是永远带着 0 宽裁剪框进绘制。这一点和 {@link HPProgressBar} 完全一致。
     */
    private void applyClipVisibility(UIElement layer, float widthRatio) {
        boolean visible = isClipBigEnough(widthRatio, 1.0f);
        if (layer.isDisplayed() != visible) {
            layer.setDisplay(visible);
        }
    }

    /** 比例换算成物理像素后够不够画；参照系取整条削韧条的宽度（{@link #barBackground}）。 */
    private boolean isClipBigEnough(float widthRatio, float heightRatio) {
        if (!Float.isFinite(widthRatio) || !Float.isFinite(heightRatio)
                || widthRatio <= 0.0f || heightRatio <= 0.0f) {
            return false;
        }
        float referenceWidth = barBackground.getSizeWidth();
        float referenceHeight = barBackground.getSizeHeight();
        if (!Float.isFinite(referenceWidth) || !Float.isFinite(referenceHeight)
                || referenceWidth <= 0.0f || referenceHeight <= 0.0f) {
            // 布局还没算出来，宽度无从判断，先不画
            return false;
        }
        float scale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        return widthRatio * referenceWidth * scale >= MIN_CLIP_PIXELS
                && heightRatio * referenceHeight * scale >= MIN_CLIP_PIXELS;
    }

    /** 绑定目标实体，并建立削韧比例数据源。 */
    public MobPoiseBar track(LivingEntity entity) {
        this.trackedEntity = entity;
        this.bindDataSource(SupplierDataSource.of(() -> {
            if (trackedEntity == null || !trackedEntity.isAlive()) return 0f;
            return PoiseService.ratio(trackedEntity);
        }));
        return this;
    }

    /** 每帧按破没破切换样式类，用于 LSS 中的颜色覆盖。 */
    public void updateStyleClass() {
        if (trackedEntity == null) return;
        PoiseState state = PoiseService.peek(trackedEntity);
        removeClass("normal");
        removeClass("broken");
        if (state != null && state.isBroken()) {
            addClass("broken");
        } else {
            addClass("normal");
        }
    }

    public LivingEntity getTrackedEntity() {
        return trackedEntity;
    }
}
