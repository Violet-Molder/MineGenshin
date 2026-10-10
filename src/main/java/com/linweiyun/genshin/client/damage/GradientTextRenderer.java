package com.linweiyun.genshin.client.damage;

import com.linweiyun.genshin.client.performance.IndicatorFramePlanner;
import com.linweiyun.genshin.client.performance.IndicatorGlyphCache;
import com.linweiyun.genshin.client.performance.IndicatorPerfStats;
import com.linweiyun.genshin.config.PerformanceConfig;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 世界空间「上→下渐变」文字绘制器。
 *
 * <p>文字本体交给 {@link Font#drawInBatch}，但字形写顶点前会把
 * {@link MultiBufferSource} 换成这里的包装：每个字形仍然自己写顶点，
 * 而颜色由 {@link GradientConsumer} 按顶点在<b>文字行内的本地 y</b> 现算，
 * 得到真正的逐顶点垂直渐变。不需要遮罩贴图，也不需要每帧重新排版。</p>
 *
 * <h2>三条性能约束</h2>
 * <ol>
 *   <li><b>排版结果来自缓存</b>：文字与量宽由 {@link IndicatorGlyphCache} 提供，
 *       同一条文字连着画十几帧也只查表一次；</li>
 *   <li><b>同 RenderType 合并</b>：{@link MultiBufferSource.BufferSource} 本来就按
 *       RenderType 分桶、共用一个顶点缓冲，多条飘字合成一次绘制不会改变绘制顺序，
 *       却能省掉每条一次的提交对象；</li>
 *   <li><b>顶点变换不再装箱</b>：包装消费器直接转发调用方给的矩阵，不额外新建
 *       {@link org.joml.Vector3f}。</li>
 * </ol>
 *
 * <p>顶点生产的耗时与条数记给 {@link IndicatorPerfStats}，在 F3 上显示为 {@code vert}。</p>
 */
public final class GradientTextRenderer {

    /** 全亮光照坐标：世界空间飘字不受方块光照影响 */
    public static final int FULL_BRIGHT = IndicatorGlyphCache.FULL_BRIGHT;

    /**
     * 阴影右下偏移的兜底值（字体像素），配置没读出来时用这个。
     *
     * <p>取香草的一半而不是整整一个像素：飘字在世界空间里被放大，
     * 同一个「1 字体像素」落到屏幕上会比原版文字阴影更明显，看起来像离本体偏远。</p>
     */
    private static final float FALLBACK_SHADOW_OFFSET_PX = 0.5f;
    /**
     * 阴影沿本地 z 往观察者方向挪的量（字体像素，防深度打架的亚像素补偿）。
     */
    private static final float SHADOW_DEPTH_PX = 0.03f;
    /** 阴影亮度系数 */
    private static final float SHADOW_BRIGHTNESS = 0.25f;

    /**
     * 阴影不透明度的兜底倍率（配置没读出来时用）。
     *
     * <p>阴影是同一行字再写一遍：不透明度拉满时，笔画边上那半像素会跟正文糊在一起、
     * 看起来又粗又脏 —— 所以默认给正文的一半不到，只当作一点点托底。
     */
    private static final float FALLBACK_SHADOW_ALPHA = 0.45f;

    /** 逐顶点上色共用的缓冲包装（只有渲染主线程会碰） */
    private static final GradientBufferSource BUFFER_SOURCE = new GradientBufferSource();
    /** 阴影趟复用的一份位姿，避免每条飘字新建矩阵 */
    private static final Matrix4f SHADOW_MATRIX = new Matrix4f();

    /**
     * 本帧要不要画阴影（每帧读一次配置）。
     *
     * <p>阴影趟是整条链路里最实的固定开销：每个飘字都要把同一批字形再写一遍顶点、
     * 只是压暗并右下偏移。关掉它顶点写入直接减半，代价是数字压在浅色背景上略微难读。</p>
     */
    private static boolean shadowEnabled = true;

    /** 本帧的阴影右下偏移（字体像素，每帧读一次配置） */
    private static float shadowOffsetPx = FALLBACK_SHADOW_OFFSET_PX;

    /** 本帧的阴影不透明度倍率（每帧读一次配置） */
    private static float shadowAlpha = FALLBACK_SHADOW_ALPHA;

    private GradientTextRenderer() {}

    /**
     * 一次画完整帧的飘字。
     *
     * @param bufferSource 本帧的顶点来源，必须还没 {@code endBatch}
     * @param entries      {@link IndicatorFramePlanner#plan} 的结果
     */
    public static void submitBatch(MultiBufferSource bufferSource, List<IndicatorFramePlanner.Entry> entries) {
        if (bufferSource == null || entries == null || entries.isEmpty()) {
            return;
        }
        refreshShadowSettings();
        BUFFER_SOURCE.setSource(bufferSource);
        for (IndicatorFramePlanner.Entry entry : entries) {
            renderEntry(entry);
        }
    }

    /**
     * 画一条飘字：先把整行按「阴影位姿 + 压暗颜色」写一遍顶点，再按「原位姿 + 渐变颜色」写一遍。
     *
     * <p>两趟同缓冲、顺序即层级，所以正文稳定压在阴影上面。</p>
     */
    private static void renderEntry(IndicatorFramePlanner.Entry entry) {
        IndicatorGlyphCache.Label label = entry.label;
        if (label == null || label.font == null) {
            return;
        }

        GradientConsumer consumer = BUFFER_SOURCE.consumer();
        consumer.begin(entry);
        long startedAt = System.nanoTime();

        if (shadowEnabled) {
            SHADOW_MATRIX.set(entry.anchor).translate(shadowOffsetPx, shadowOffsetPx, -SHADOW_DEPTH_PX);
            consumer.setDarken(true);
            label.font.drawInBatch(label.sequence, label.left, 0.0f, 0xFFFFFFFF, false,
                    SHADOW_MATRIX, BUFFER_SOURCE, Font.DisplayMode.SEE_THROUGH, 0, FULL_BRIGHT);
        }

        consumer.setDarken(false);
        label.font.drawInBatch(label.sequence, label.left, 0.0f, 0xFFFFFFFF, false,
                entry.anchor, BUFFER_SOURCE, Font.DisplayMode.SEE_THROUGH, 0, FULL_BRIGHT);

        IndicatorPerfStats.addVertexNanos(System.nanoTime() - startedAt);
        IndicatorPerfStats.addVertices(consumer.vertexCount());
    }

    /** 飘字阴影开关；配置没加载时按「画阴影」处理。 */
    private static boolean shadowEnabled() {
        try {
            return PerformanceConfig.SHADOW.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** 飘字阴影的右下偏移（字体像素）；配置没加载时用兜底值。 */
    private static float shadowOffset() {
        try {
            return (float) PerformanceConfig.SHADOW_OFFSET.get().doubleValue();
        } catch (Throwable ignored) {
            return FALLBACK_SHADOW_OFFSET_PX;
        }
    }

    /** 飘字阴影的不透明度倍率（0~1）；配置没加载时用兜底值。 */
    private static float shadowAlpha() {
        try {
            return (float) PerformanceConfig.SHADOW_ALPHA.get().doubleValue();
        } catch (Throwable ignored) {
            return FALLBACK_SHADOW_ALPHA;
        }
    }

    /** 一帧只读一次配置：整帧的阴影开关与偏移必须是同一份快照。 */
    private static void refreshShadowSettings() {
        shadowEnabled = shadowEnabled();
        shadowOffsetPx = shadowOffset();
        shadowAlpha = shadowAlpha();
    }

    // ============================ 顶点包装 ============================

    /**
     * 把 {@link Font#drawInBatch} 取缓冲的请求接到当前帧的来源上，
     * 并统一返回同一个 {@link GradientConsumer}。
     */
    private static final class GradientBufferSource implements MultiBufferSource {

        private final GradientConsumer consumer = new GradientConsumer();
        private MultiBufferSource delegate;

        void setSource(MultiBufferSource delegate) {
            this.delegate = delegate;
        }

        GradientConsumer consumer() {
            return this.consumer;
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            // 每个 RenderType 一个真实消费器；包装器逐次转发，所以这里直接换掉内部引用即可
            this.consumer.setDelegate(this.delegate.getBuffer(type));
            return this.consumer;
        }
    }

    /**
     * 逐顶点上色的 {@link VertexConsumer}。
     *
     * <p>香草字形按「左上 → 左下 → 右下 → 右上」写顶点
     * （{@code addVertex(位姿, x, y, z)}）再 {@code setColor}，
     * 因此 setColor 时总能拿到当前顶点的本地 y。</p>
     */
    private static final class GradientConsumer implements VertexConsumer {

        private VertexConsumer delegate;

        private int topColor = 0xFFFFFF;
        private int bottomColor = 0xFFFFFF;
        private int alpha8 = 255;
        private float yTop = -7.0f;
        private float yBottom = 1.0f;
        private float currentY = Float.NaN;
        private boolean darken;
        /** 这个消费器一共写出去多少顶点（F3 的读数用） */
        private int vertexCount;

        void setDelegate(VertexConsumer delegate) {
            this.delegate = delegate;
        }

        int vertexCount() {
            return this.vertexCount;
        }

        void begin(IndicatorFramePlanner.Entry entry) {
            this.topColor = entry.topColor & 0xFFFFFF;
            this.bottomColor = entry.bottomColor & 0xFFFFFF;
            this.alpha8 = entry.alpha8;
            this.vertexCount = 0;
            if (entry.label != null) {
                this.yTop = entry.label.top;
                this.yBottom = entry.label.bottom;
            }
        }

        void setDarken(boolean darken) {
            this.darken = darken;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.currentY = y;
            this.vertexCount++;
            this.delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer addVertex(Matrix4f pose, float x, float y, float z) {
            this.currentY = y;
            this.vertexCount++;
            this.delegate.addVertex(pose, x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            this.delegate.setColor(this.resolveColor(alpha));
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            this.delegate.setColor(this.resolveColor(color >>> 24));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            this.delegate.setNormal(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setLight(int packedLight) {
            this.delegate.setLight(packedLight);
            return this;
        }

        @Override
        public VertexConsumer setOverlay(int packedOverlay) {
            this.delegate.setOverlay(packedOverlay);
            return this;
        }

        /** 取顶点本地 y 在行区间里的比例，插值出 top→bottom 的颜色 */
        private int resolveColor(int incomingAlpha8) {
            float span = this.yBottom - this.yTop;
            float t = 0.0f;
            if (span > 0.0001f && !Float.isNaN(this.currentY)) {
                t = (this.currentY - this.yTop) / span;
                t = Math.max(0.0f, Math.min(1.0f, t));
            }

            int rgb = lerpRgb(this.topColor, this.bottomColor, t);
            if (this.darken) {
                rgb = scaleRgb(rgb, SHADOW_BRIGHTNESS);
            }

            int alpha = this.alpha8;
            if (this.darken) {
                alpha = Math.round(alpha * shadowAlpha);
            }
            if (incomingAlpha8 < 255) {
                alpha = alpha * Math.max(0, incomingAlpha8) / 255;
            }
            return alpha << 24 | rgb;
        }
    }

    private static int lerpRgb(int from, int to, float t) {
        int fromR = from >> 16 & 0xFF;
        int fromG = from >> 8 & 0xFF;
        int fromB = from & 0xFF;
        int toR = to >> 16 & 0xFF;
        int toG = to >> 8 & 0xFF;
        int toB = to & 0xFF;

        int r = Math.round(fromR + (toR - fromR) * t);
        int g = Math.round(fromG + (toG - fromG) * t);
        int b = Math.round(fromB + (toB - fromB) * t);
        return r << 16 | g << 8 | b;
    }

    private static int scaleRgb(int rgb, float scale) {
        int r = Math.round((rgb >> 16 & 0xFF) * scale);
        int g = Math.round((rgb >> 8 & 0xFF) * scale);
        int b = Math.round((rgb & 0xFF) * scale);
        return r << 16 | g << 8 | b;
    }
}
