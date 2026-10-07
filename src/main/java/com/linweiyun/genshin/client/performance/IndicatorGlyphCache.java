package com.linweiyun.genshin.client.performance;

import com.linweiyun.genshin.config.PerformanceConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 飘字的<b>排版结果缓存</b>。
 *
 * <p>把「文字 + 是否斜体」映射到<b>已经排好序的字符序列、居中左端点与行上下沿</b>。
 * 飘字的文字在它整个生命周期里基本不变，同一条数字要连着画十几帧，
 * 命中缓存时就不必每帧重新拼 {@code Component} 与 {@code FormattedCharSequence}。</p>
 *
 * <p>缓存条目里的坐标都是<b>文字本地字体像素</b>（x 向右、y 向下，原点在基线左端），
 * 因此同一份结果可以在不同飘字、不同位姿、不同帧之间反复使用。</p>
 *
 * <h2>失效</h2>
 * <ul>
 *   <li><b>字体换了</b>（资源重载会重建 {@code Minecraft#font}）：缓存整体作废，
 *       条目里的量宽与字形来源都属于旧字体；</li>
 *   <li><b>条数超上限</b>：LRU 淘汰最久未用的条目。</li>
 * </ul>
 *
 * <p>只有主线程会碰它（渲染回调线程），所以不做同步。</p>
 */
public final class IndicatorGlyphCache {

    /** 全亮光照坐标：世界空间飘字不受方块光照影响 */
    public static final int FULL_BRIGHT = 0xF000F0;

    /** 行上下沿的取值（字体像素，y 向下；相对基线，向上为负） */
    private static final float FALLBACK_LINE_TOP = -7.0f;
    private static final float FALLBACK_LINE_BOTTOM = 1.0f;

    /**
     * 一条排好版的文字。字段全部是只读的，可跨帧复用。
     */
    public static final class Label {
        /** 排好序的字符序列（斜体等样式已经写进字符样式里） */
        public final FormattedCharSequence sequence;
        /** 居中绘制时的左端点（字体像素，相对基线中点） */
        public final float left;
        /** 渐变取端点用的行上下沿（字体像素，y 向下；向上为负） */
        public final float top;
        public final float bottom;
        /** 建立这条结果时用的字体实例 */
        public final Font font;

        private Label(FormattedCharSequence sequence, float left, float top, float bottom, Font font) {
            this.sequence = sequence;
            this.left = left;
            this.top = top;
            this.bottom = bottom;
            this.font = font;
        }
    }

    /**
     * 文字表。
     *
     * <p>斜体分成两张表、键就是文字本身：用 {@code String} 当下标，查询时不会新建对象。</p>
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
     * 取（或建立）一段文字的排版结果。
     *
     * @param font   当前字体
     * @param text   要排版的纯文字
     * @param italic 是否斜体
     * @return 排版结果；{@code font} 或 {@code text} 为空时返回 {@code null}
     */
    public static Label get(Font font, String text, boolean italic) {
        if (font == null || text == null || text.isEmpty()) {
            return null;
        }
        if (cacheFont != font) {
            // 字体换了：旧结果的量宽与字形来源都属于旧字体，整体作废
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

        // 颜色交给渐变消费者逐顶点算，这里只固定「斜体」这一项样式
        Style style = Style.EMPTY.withItalic(italic);
        FormattedCharSequence sequence = Component.literal(text).withStyle(style).getVisualOrderText();
        float left = -font.width(sequence) / 2.0f;
        return new Label(sequence, left, FALLBACK_LINE_TOP, FALLBACK_LINE_BOTTOM, font);
    }
}
