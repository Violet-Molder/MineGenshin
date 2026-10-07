package com.linweiyun.genshin.client.render.gui.component;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.character.PGCharacterData;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataProvider;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Clip;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.integration.kjs.KJSBindings;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.lowdragmc.lowdraglib2.syncdata.ISubscription;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.LinkedHashMap;
import java.util.Map;

@ParametersAreNonnullByDefault
@KJSBindings
@LDLRegister(name = "hp-progress-bar", group = "minegenshin", registry = "ldlib2:ui_element")
public class HPProgressBar extends ProgressBar {

    /**
     * 拖尾颜色（ARGB）。
     *
     * <p>和世界里怪物血条的拖尾同色 —— 那边是 {@code COLOR_TRAIL = {0.70, 0.50, 0.10}} 乘在白色血条贴图上，
     * 这里直接把同样的比例写成一个颜色常量乘在白条上，两处观感一致。
     */
    public static final int TRAIL_COLOR = 0xFFB3801A;

    /** 拖尾用的白条贴图：原色白，靠 {@link #TRAIL_COLOR} 乘出拖尾色 */
    private static final Identifier TRAIL_TEXTURE =
            Minegenshin.id("gui/short_character_hp_bar_white.png");

    /** 拖尾每秒衰减的比例，与怪物血条一致（0.3/s ≈ 每帧 0.005 × 60fps）。 */
    private static final float TRAIL_DECAY_PER_SECOND = 0.3f;

    /** 单帧最多推进多少秒：卡顿 / 切窗口回来后不要让拖尾一次跳完 */
    private static final float TRAIL_MAX_STEP_SECONDS = 0.5f;

    /** 宽度变化小于这个值不写布局，省掉每帧一次无意义的样式失效 */
    private static final float TRAIL_EPSILON = 0.0005f;

    /**
     * 带 {@code Clip.SCISSOR} 的裁剪层至少要占这么多<b>物理像素</b>，不足就整层不画。
     *
     * <p>LDLib2 会把裁剪框四舍五入到物理像素（{@code PreciseScissor#quantize} 的
     * {@code Math.round(边 × 界面缩放)}），宽度不到半个物理像素时结果就是 0 宽；
     * 而 {@code RenderPass#enableScissor} 碰到宽或高 ≤ 0 会直接抛
     * {@code IllegalArgumentException: Scissor size must be >0, was 0x8}，把渲染线程打崩
     * —— 0x8 正是这里的血条：高 4 gui 像素 × 界面缩放 2 = 8 物理像素。
     */
    private static final float MIN_CLIP_PIXELS = 1.0f;

    protected final Map<IDataProvider<PGCharacterData>, ISubscription> characterSources =
            new LinkedHashMap<>();
    public final UIElement barIcon;

    /**
     * 受伤拖尾的<b>裁剪层</b>：压在填充色下面（{@code zIndex = -1}），宽度按拖尾比例。
     *
     * <p>和填充那条（{@code bar}）一个做法：这层自己只负责「裁到多宽」，里面那张
     * {@link #trailIcon} 永远按整条血条的尺寸原样画，多出来的部分被 {@code SCISSOR} 切掉。
     * 左右两端的楔形斜边因此不会被压扁 —— 宽度一缩，斜边角度就跟填充条不一样了，
     * 满血时绿色条也就盖不住底下那截错位的三角形。
     *
     * <p>宽度百分比是相对 {@code barBackground}（也就是整条血条的宽度）算的，
     * 和填充那一层同一个参照系，所以外面怎么改血条宽高都不影响拖尾对齐。
     */
    public final UIElement trailClip;

    /**
     * 拖尾用的<b>整条</b>白条贴图（尺寸与 {@link #barIcon} 一致，永不缩放）。
     *
     * <p>贴图必须和填充条同款：两端楔形在贴图里固定 50 px 长，长条贴图 2100 宽、短条 1200 宽，
     * 把短白条当长条拖尾画，斜边斜度会差将近一倍。换贴图见 {@link #trailTexture(Identifier)}。
     */
    public final UIElement trailIcon;

    /** {@link #trailIcon} 当前写进布局的尺寸，用来判断要不要重写（每帧比对，尺寸变了才写） */
    private float trailIconWidth = -1.0f;
    private float trailIconHeight = -1.0f;

    /** 本帧该显示的拖尾比例（≥ 当前血量比例） */
    private float trailRatio = 1.0f;
    /** 上次推进拖尾的墙钟毫秒 */
    private long trailLastMillis;
    /** 是否已经用第一帧的真实血量起过头（没有「上一帧」就不补历史拖尾） */
    private boolean trailPrimed;

    public HPProgressBar() {
        this.barContainer.layout(layout -> {
            layout.paddingAll(0);
            layout.widthPercent(100);
            layout.heightPercent(100);
        });
        this.bar
                .addChild(barIcon = new UIElement())
                .layout(layout -> {
                    layout.positionType(TaffyPosition.ABSOLUTE);
                })
                .style(s -> {
                    s.background(SpriteTexture.of(
                            Identifier.fromNamespaceAndPath("minegenshin", "gui/empty.png")));
                    s.clip(Clip.SCISSOR);
                });

        this.trailIcon = new UIElement();
        this.trailIcon
                .layout(layout -> {
                    layout.positionType(TaffyPosition.ABSOLUTE);
                    layout.left(0);
                    layout.top(0);
                })
                .style(s -> s.background(SpriteTexture.of(TRAIL_TEXTURE).setColor(TRAIL_COLOR)));

        this.trailClip = new UIElement();
        this.trailClip
                .layout(layout -> {
                    layout.positionType(TaffyPosition.ABSOLUTE);
                    layout.left(0);
                    layout.top(0);
                    layout.heightPercent(100);
                    layout.widthPercent(100);
                })
                .style(s -> {
                    s.zIndex(-1);
                    s.clip(Clip.SCISSOR);
                })
                .addChild(this.trailIcon);
        this.barBackground.addChild(this.trailClip);

        // HUD 的 ldlib2 层是「边渲染边 tick」，所以这条监听实际上是每帧来一次，拖尾不会一格一格跳
        addEventListener(
                UIEvents.TICK,
                event -> {
                    tickTrail();
                    refreshClipVisibility();
                });
    }

    /**
     * 基类每次重写填充条（{@link #bar}）的宽度都会经过这里，紧跟其后判一次这层还该不该画。
     *
     * <p>必须和写宽度在同一次调用里判，不能只靠每帧 tick：写宽度发生在 ldlib2 的 tick 阶段，
     * 而布局与绘制排在它之后（{@code ModularUIWidget#extractRenderState} 的顺序是
     * tick → calculateStyleAndLayout → draw），中间没有「晚一帧」的缓冲 —— 血量比例刚跳到
     * 极小值的那一帧就会直接把裁剪框交下去。
     */
    @Override
    protected void updateProgressBarStyle(float normalizedValue) {
        super.updateProgressBarStyle(normalizedValue);
        applyClipVisibility(bar, normalizedValue);
    }

    /**
     * 每帧复查两层的裁剪框。
     *
     * <p>血量比例没变时基类不会重写宽度，但界面缩放、窗口大小乃至「布局刚算出来」都会让
     * 「够不够 1 像素」的答案变掉，所以跟着 HUD 的每帧 tick 再判一次，顺便把首帧因为量不到
     * 宽度而没画的层补回来。
     */
    private void refreshClipVisibility() {
        applyClipVisibility(bar, getNormalizedValue());
        applyClipVisibility(trailClip, trailRatio);
    }

    /**
     * 按「裁剪框两个方向都够 {@link #MIN_CLIP_PIXELS} 个物理像素」写一层的显隐。
     *
     * <p>不足 1 像素本来也看不见，整层不画就不会有 0 宽的裁剪框；比例回到看得见的范围会自动
     * 恢复显示。拿不到参照宽度（首帧布局还没算出来）时一律先判为不可见。
     *
     * @param layer      要控制的裁剪层
     * @param widthRatio 该层宽度占整条血条宽度的比例
     */
    private void applyClipVisibility(UIElement layer, float widthRatio) {
        // 两层裁剪层都是整条血条的高度（heightPercent(100)），所以高度比例恒为 1
        boolean visible = isClipBigEnough(widthRatio, 1.0f);
        // 判据取元素自己的实时显隐，不能缓存成布尔字段：裁剪层的默认状态就是 FLEX（可见），
        // 而「还没判过」和「判成不可见」都是 false，两者分不开 —— 一条从首帧起宽度就是 0 的
        // 血条会永远判成 false，于是永远不写 setDisplay(false)，一直带着 0 宽裁剪框进绘制。
        if (layer.isDisplayed() != visible) {
            layer.setDisplay(visible);
        }
    }

    /**
     * 比例换算成物理像素后够不够画。
     *
     * <p>参照系取 {@link #barBackground}：两层裁剪层的宽高百分比都是相对它算的，而它自己的尺寸
     * 跟血量无关（整条血条多宽就多宽），所以读上一帧的布局结果不会算错 —— 比例刚变的这一帧也准。
     */
    private boolean isClipBigEnough(float widthRatio, float heightRatio) {
        if (!Float.isFinite(widthRatio) || !Float.isFinite(heightRatio)
                || widthRatio <= 0.0f || heightRatio <= 0.0f) {
            // 血量数据还没同步（0/0 算出 NaN）或者血量为 0，本来就没东西可画
            return false;
        }
        float referenceWidth = barBackground.getSizeWidth();
        float referenceHeight = barBackground.getSizeHeight();
        if (!Float.isFinite(referenceWidth) || !Float.isFinite(referenceHeight)
                || referenceWidth <= 0.0f || referenceHeight <= 0.0f) {
            // 布局还没算出来，宽度无从判断，先不画
            return false;
        }
        float scale = guiScale();
        return widthRatio * referenceWidth * scale >= MIN_CLIP_PIXELS
                && heightRatio * referenceHeight * scale >= MIN_CLIP_PIXELS;
    }

    /** 当前界面缩放：裁剪框是按物理像素取整的，判据必须带上它 */
    private static float guiScale() {
        return Minecraft.getInstance().getWindow().getGuiScale();
    }

    /**
     * 每帧推一次拖尾。
     *
     * <p>和世界里怪物血条的拖尾同一套规则：掉血时从旧值往下收、涨血/回血直接跟上不留反向拖尾；
     * 衰减按真实流逝时间算，所以高刷屏和 60fps 看到的拖尾速度一样。
     */
    private void tickTrail() {
        float ratio = getNormalizedValue();
        if (!Float.isFinite(ratio)) {
            ratio = 1.0f;
        }
        ratio = Mth.clamp(ratio, 0.0f, 1.0f);

        long now = Util.getMillis();
        if (!trailPrimed) {
            trailPrimed = true;
            trailLastMillis = now;
            setTrailRatio(ratio);
            return;
        }

        float seconds = Mth.clamp((now - trailLastMillis) / 1000.0f, 0.0f, TRAIL_MAX_STEP_SECONDS);
        trailLastMillis = now;

        float trail = trailRatio > ratio
                ? Math.max(ratio, trailRatio - TRAIL_DECAY_PER_SECOND * seconds)
                : ratio;
        setTrailRatio(trail);
    }

    /**
     * 换拖尾贴图（整条白条）。
     *
     * <p>白条必须和填充条是同一款的形状 —— 长条用 {@code long_character_hp_bar_white.png}、
     * 短条用 {@code short_character_hp_bar_white.png}。形状不同的白条画在同宽的盒子里，
     * 两端楔形的斜边就对不上，绿色填充条底下会露出拖尾的尖角。
     */
    public HPProgressBar trailTexture(Identifier texture) {
        this.trailIcon.style(s -> s.background(SpriteTexture.of(texture).setColor(TRAIL_COLOR)));
        return this;
    }

    private void setTrailRatio(float ratio) {
        syncTrailSize();
        if (Math.abs(ratio - trailRatio) < TRAIL_EPSILON) {
            return;
        }
        trailRatio = ratio;
        this.trailClip.layout(layout -> layout.widthPercent(ratio * 100.0f));
        applyClipVisibility(trailClip, ratio);
    }

    /**
     * 把整条拖尾的尺寸对齐到 {@link #barIcon}。
     *
     * <p>拖尾贴图只能整条原样画（要裁的是外面那层），所以它必须和填充条同宽同高；
     * 尺寸直接从 {@code barIcon} 的布局结果取，外面换条、改比例都不用两边一起改。
     */
    private void syncTrailSize() {
        float width = barIcon.getSizeWidth();
        float height = barIcon.getSizeHeight();
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }
        if (Math.abs(width - trailIconWidth) < 0.01f && Math.abs(height - trailIconHeight) < 0.01f) {
            return;
        }
        trailIconWidth = width;
        trailIconHeight = height;
        this.trailIcon.layout(layout -> layout.width(width).height(height));
    }
}
