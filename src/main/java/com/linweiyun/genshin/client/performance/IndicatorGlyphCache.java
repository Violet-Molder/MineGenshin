package com.linweiyun.genshin.client.performance;

import com.linweiyun.genshin.config.PerformanceConfig;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 飘字的<b>字形几何缓存</b>（渲染优化模块）。
 *
 * <h2>解决什么</h2>
 * 字体排版（{@code font.prepareText}：拆字符 → 查字形 → 生成 {@link TextRenderable}）
 * 是每个飘字每帧的开销大头，而飘字的文字在它整个生命周期里基本不变
 * ——同一条数字要连着画十几帧，每帧都重新排版一遍纯属浪费。
 *
 * <p>这里把「文字 + 是否斜体」映射到<b>已经排好版的字形列表</b>。
 * 字形几何是<b>文字本地坐标</b>（顶点变换在消费时由位姿完成，见 {@code VertexConsumer#addVertex(Matrix4f, ...)}），
 * 所以同一份缓存可以在不同飘字、不同位姿、不同帧之间反复使用。</p>
 *
 * <h2>失效</h2>
 * <ul>
 *   <li><b>字体换了</b>（资源重载会重建 {@code Minecraft#font}）：缓存整体作废，
 *       因为 {@link TextRenderable} 抓着旧的字体图集纹理；</li>
 *   <li><b>条数超上限</b>：LRU 淘汰最久未用的条目。</li>
 * </ul>
 *
 * <p>只有主线程会碰它（渲染回调线程），所以不做同步。</p>
 */
public final class IndicatorGlyphCache {

    /** 全亮光照坐标：世界空间飘字不受方块光照影响 */
    public static final int FULL_BRIGHT = 0xF000F0;

    /** bounds 取不到时的兜底行区间（字体像素，y 向下） */
    private static final float FALLBACK_LINE_TOP = -7.0f;
    private static final float FALLBACK_LINE_BOTTOM = 1.0f;

    /**
     * 一条排好版的文字。
     *
     * <p>{@code glyphsByType} 按字体图集的 {@link RenderType} 分组——同一张图集的字形
     * 共用一次几何提交。</p>
     */
    public static final class Label {
        public final Map<RenderType, List<TextRenderable>> glyphsByType;
        /** 文字像素包围盒的上下沿（字体像素，y 向下），渐变按它取端点 */
        public final float top;
        public final float bottom;
        /** 排版时用的字体实例，用来识别资源重载 */
        public final Font font;

        private Label(Map<RenderType, List<TextRenderable>> glyphsByType,
                      float top, float bottom, Font font) {
            this.glyphsByType = glyphsByType;
            this.top = top;
            this.bottom = bottom;
            this.font = font;
        }
    }

    /**
     * 字形表。
     *
     * <p>斜体分成两张表、键就是文字本身 —— 用 {@code String} 当下标不会在查询时新建对象
     * （早先用 {@code record Key(text, italic)} 做键，每帧每飘字都要新建一条记录）。</p>
     */
    private static final Map<String, Label> CACHE_PLAIN = newLruCache();
    private static final Map<String, Label> CACHE_ITALIC = newLruCache();

    private static Map<String, Label> newLruCache() {
        return new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Label> eldest) {
                return size() > capacity;
            }
        };
    }

    private static int capacity = 512;
    private static Font cacheFont;

    private IndicatorGlyphCache() {}

    /**
     * 取（或建立）一段文字的排版结果。命中缓存时不做任何字符拆分与字形查找。
     */
    public static Label get(Font font, String text, boolean italic) {
        if (font == null || text == null || text.isEmpty()) {
            return null;
        }
        if (cacheFont != font) {
            // 资源重载换了字体：旧字形抓着旧图集，整体作废
            // （真正的兜底是 IndicatorPerfReloadListener：Minecraft#font 实例在重载后并不会换）
            clear();
            cacheFont = font;
        }

        Map<String, Label> cache = italic ? CACHE_ITALIC : CACHE_PLAIN;
        Label cached = cache.get(text);
        if (cached != null) {
            return cached;
        }

        Label built = build(font, text, italic);
        if (built != null) {
            syncCapacity();
            cache.put(text, built);
        }
        return built;
    }

    /** 资源重载 / 关闭世界时清空。 */
    public static void clear() {
        CACHE_PLAIN.clear();
        CACHE_ITALIC.clear();
        cacheFont = null;
    }

    /** 当前缓存条数（调试用）。 */
    public static int size() {
        return CACHE_PLAIN.size() + CACHE_ITALIC.size();
    }

    // ============================ 内部 ============================

    private static void syncCapacity() {
        int configured;
        try {
            configured = PerformanceConfig.GLYPH_CACHE_SIZE.get();
        } catch (Throwable ignored) {
            configured = 512;
        }
        capacity = Math.max(16, configured);
    }

    private static Label build(Font font, String text, boolean italic) {
        if (font == null) {
            return null;
        }

        // 颜色交给渐变消费者逐顶点算，这里只固定「斜体」这一项样式，且不要字体自带的阴影
        Style style = Style.EMPTY.withItalic(italic);
        FormattedCharSequence sequence = Component.literal(text).withStyle(style).getVisualOrderText();
        float left = -font.width(sequence) / 2.0f;
        Font.PreparedText prepared = font.prepareText(sequence, left, 0.0f, 0xFFFFFFFF, false, false, 0);

        float yTop = FALLBACK_LINE_TOP;
        float yBottom = FALLBACK_LINE_BOTTOM;
        var bounds = prepared.bounds();
        if (bounds != null && bounds.height() > 0) {
            yTop = bounds.top();
            yBottom = bounds.bottom();
        }

        Map<RenderType, List<TextRenderable>> byType = new LinkedHashMap<>(2);
        prepared.visit(new Font.GlyphVisitor() {
            @Override
            public void acceptRenderable(TextRenderable renderable) {
                if (renderable == null) {
                    return;
                }
                RenderType type = renderable.renderType(Font.DisplayMode.SEE_THROUGH, false);
                byType.computeIfAbsent(type, key -> new ArrayList<>(8)).add(renderable);
            }
        });

        if (byType.isEmpty()) {
            return null;
        }
        return new Label(byType, yTop, yBottom, font);
    }
}
