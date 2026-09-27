package com.linweiyun.genshin.client.render.gui.component;

import com.linweiyun.genshin.Minegenshin;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Clip;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.integration.kjs.KJSBindings;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@KJSBindings
@LDLRegister(name = "mob-health-bar", group = "minegenshin", registry = "ldlib2:ui_element")
public class MobHealthBar extends ProgressBar {

    /**
     * 带 {@code Clip.SCISSOR} 的裁剪层至少要占这么多<b>物理像素</b>，不足就整层不画。
     *
     * <p>和 {@link HPProgressBar#MIN_CLIP_PIXELS} 同一件事、同一个理由：
     * 裁剪框会被四舍五入到物理像素，宽度不足半个像素就是 0 宽，
     * 而 {@code RenderPass#enableScissor} 碰到 0 宽/0 高会直接抛异常把渲染线程打崩。
     */
    private static final float MIN_CLIP_PIXELS = 1.0f;

    public final UIElement barIcon;

    private LivingEntity trackedEntity;

    public MobHealthBar() {
        this.barContainer.layout(layout -> {
            layout.paddingAll(0);
            layout.widthPercent(100);
            layout.heightPercent(100);
        });
        this.bar
                .addChild(barIcon = new UIElement())
                .layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE))
                .style(s -> {
                    s.background(SpriteTexture.of(
                            Identifier.fromNamespaceAndPath("minegenshin", "gui/empty.png")));
                    s.clip(Clip.SCISSOR);
                });

        // 填充纹理（绿色基础血条）
        this.barIcon.style(s -> s.background(SpriteTexture.of(
                Minegenshin.id("gui/short_character_hp_bar_green.png"))));

        // 空槽背景纹理
        this.barContainer(c -> c.style(s -> s.background(
                SpriteTexture.of(Minegenshin.id("gui/short_character_hp_green.png")))));

        // 不显示数字
        this.label.setText("");

        // 每帧复查一次裁剪框（比例没变但界面缩放 / 布局变了，答案也可能变）
        addEventListener(UIEvents.TICK, event -> refreshClipVisibility());
    }

    /**
     * 基类每次重写填充条（{@link #bar}）的宽度都会经过这里，紧跟其后判一次这层还该不该画 ——
     * 写宽度与判显隐必须在同一次调用里，否则血量刚掉到极小的那一帧就会交下 0 宽裁剪框。
     */
    @Override
    protected void updateProgressBarStyle(float normalizedValue) {
        super.updateProgressBarStyle(normalizedValue);
        applyClipVisibility(bar, normalizedValue);
    }

    private void refreshClipVisibility() {
        applyClipVisibility(bar, getNormalizedValue());
    }

    /**
     * 按「裁剪框两个方向都够 {@link #MIN_CLIP_PIXELS} 个物理像素」写这一层的显隐。
     *
     * <p>判据取 {@code layer.isDisplayed()} 的<b>实时状态</b>，不能缓存成布尔字段
     * （「还没判过」与「判成不可见」分不开）。同 {@link HPProgressBar}。
     */
    private void applyClipVisibility(UIElement layer, float widthRatio) {
        boolean visible = isClipBigEnough(widthRatio, 1.0f);
        if (layer.isDisplayed() != visible) {
            layer.setDisplay(visible);
        }
    }

    /** 比例换算成物理像素后够不够画；参照系取整条血条的宽度（{@link #barBackground}）。 */
    private boolean isClipBigEnough(float widthRatio, float heightRatio) {
        if (!Float.isFinite(widthRatio) || !Float.isFinite(heightRatio)
                || widthRatio <= 0.0f || heightRatio <= 0.0f) {
            return false;
        }
        float referenceWidth = barBackground.getSizeWidth();
        float referenceHeight = barBackground.getSizeHeight();
        if (!Float.isFinite(referenceWidth) || !Float.isFinite(referenceHeight)
                || referenceWidth <= 0.0f || referenceHeight <= 0.0f) {
            return false;
        }
        float scale = Minecraft.getInstance().getWindow().getGuiScale();
        return widthRatio * referenceWidth * scale >= MIN_CLIP_PIXELS
                && heightRatio * referenceHeight * scale >= MIN_CLIP_PIXELS;
    }

    /**
     * 绑定目标实体，并建立血量数据源。
     */
    public MobHealthBar track(LivingEntity entity) {
        this.trackedEntity = entity;
        this.bindDataSource(SupplierDataSource.of(() -> {
            if (trackedEntity == null || !trackedEntity.isAlive()) return 0f;
            float max = trackedEntity.getMaxHealth();
            if (max <= 0) return 0f;
            return Math.clamp(trackedEntity.getHealth() / max, 0f, 1f);
        }));
        return this;
    }

    /**
     * 每帧根据血量比例切换样式类，用于 LSS 中的颜色覆盖。
     */
    public void updateStyleClass() {
        if (trackedEntity == null) return;
        float ratio = Math.clamp(trackedEntity.getHealth() / trackedEntity.getMaxHealth(), 0f, 1f);
        removeClass("normal");
        removeClass("low");
        removeClass("critical");
        if (ratio < 0.3f) {
            addClass("critical");
        } else if (ratio < 0.5f) {
            addClass("low");
        } else {
            addClass("normal");
        }
    }

    public LivingEntity getTrackedEntity() {
        return trackedEntity;
    }
}
