package com.linweiyun.genshin.client.damage;

import com.linweiyun.genshin.client.performance.IndicatorFramePlanner;
import com.linweiyun.genshin.client.performance.IndicatorGlyphCache;
import com.linweiyun.genshin.client.performance.IndicatorPerfStats;
import com.linweiyun.genshin.config.PerformanceConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.ARGB;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 世界空间「上→下渐变」文字提交器（渲染优化模块的提交端）。
 *
 * <p>走的是香草命名牌那条管线：把字体图集的 {@link RenderType} 交给
 * {@link SubmitNodeCollector#submitCustomGeometry}，再让每个字形自己
 * {@link TextRenderable#render} 出顶点。字形顶点自带一个颜色，所以这里包一层
 * {@link VertexConsumer}，按顶点在<b>文字行内的本地 y</b> 现算颜色，得到真正的逐顶点垂直渐变
 * ——不需要遮罩贴图，也不需要每帧重新排版。</p>
 *
 * <h2>三条性能约束</h2>
 * <ol>
 *   <li><b>排版结果来自缓存</b>：字形几何由 {@link IndicatorGlyphCache} 提供，
 *       同一条文字连着画十几帧也只排版一次；</li>
 *   <li><b>同 RenderType 合并提交</b>：香草的自定义几何本来就按 RenderType 分桶、
 *       共用一个顶点缓冲，所以多条飘字合成一次提交不会改变绘制顺序，却能省掉每条一次的
 *       位姿拷贝与提交对象；</li>
 *   <li><b>顶点变换不再装箱</b>：{@code VertexConsumer} 默认的
 *       {@code addVertex(Matrix4fc, ...)} 每个顶点都会新建一个 {@link Vector3f}，
 *       这里改成复用同一组临时变量。</li>
 * </ol>
 *
 * <p>回调本身跑在香草的 feature 渲染阶段（不是提交的那一刻），所以「这一段花了多少时间」
 * 就是飘字真实的顶点生产开销 —— 这里顺手量给 {@link IndicatorPerfStats}，
 * F3 上显示为 {@code vert}。</p>
 *
 * <p>调用前 poseStack 必须是<b>飘字锚点之外</b>的世界位姿；每条飘字的锚点位姿
 * （相机相对位置 → 相机朝向 → 世界尺度）由 {@link IndicatorFramePlanner} 烘焙好放在
 * {@link IndicatorFramePlanner.Entry#anchor} 里。</p>
 */
public final class GradientTextRenderer {

    /** 全亮光照坐标：世界空间飘字不受方块光照影响 */
    public static final int FULL_BRIGHT = IndicatorGlyphCache.FULL_BRIGHT;

    /**
     * 阴影右下偏移的兜底值（字体像素），配置没读出来时用这个。
     *
     * <p>取香草的一半而不是整整一个像素：飘字在世界空间里被放大（约 1.36 倍命名牌），
     * 同一个「1 字体像素」落到屏幕上会比原版文字阴影更明显，看起来像离本体偏远。
     * 真实取值走 {@link PerformanceConfig#SHADOW_OFFSET}，这里是它读不到时的垫底。</p>
     */
    private static final float FALLBACK_SHADOW_OFFSET_PX = 0.5f;
    /**
     * 阴影沿本地 z 往观察者方向挪的量（字体像素，纯防打架的亚像素补偿）。
     *
     * <p>SEE_THROUGH 管线没有深度测试，真正决定「正文压住阴影」的是提交顺序
     * ——先阴影趟、后正文趟，所以这个值只在与深度测试的管线搭配时才有意义。</p>
     */
    private static final float SHADOW_DEPTH_PX = 0.03f;
    /** 阴影亮度系数，与香草 ARGB.scaleRGB(color, 0.25) 一致 */
    private static final float SHADOW_BRIGHTNESS = 0.25f;

    /**
     * 阴影不透明度的兜底倍率（配置没读出来时用）。
     *
     * <p>阴影是同一行字再写一遍：不透明度拉满时，笔画边上那半像素会跟正文糊在一起、
     * 看起来又粗又脏 —— 所以默认给正文的一半不到，只当作一点点托底。
     */
    private static final float FALLBACK_SHADOW_ALPHA = 0.45f;

    /** 按 RenderType 分组的临时表（每帧复用） */
    private static final Map<RenderType, List<IndicatorFramePlanner.Entry>> GROUPS = new LinkedHashMap<>(4);

    /**
     * 本帧要不要画阴影（每帧读一次配置）。
     *
     * <p>阴影趟是整条链路里最实的一块固定开销：每个飘字都要把同一批字形再发一遍顶点、
     * 只是压暗并右下偏移。关掉它顶点写入直接减半，代价是数字压在浅色背景上略微难读。</p>
     */
    private static boolean shadowEnabled = true;

    /** 本帧的阴影右下偏移（字体像素，每帧读一次配置） */
    private static float shadowOffsetPx = FALLBACK_SHADOW_OFFSET_PX;

    /** 本帧的阴影不透明度倍率（每帧读一次配置） */
    private static float shadowAlpha = FALLBACK_SHADOW_ALPHA;

    private GradientTextRenderer() {}

    /**
     * 批量提交一整帧的飘字。
     *
     * @param collector 当前帧的提交收集器
     * @param poseStack 世界位姿栈（飘字锚点之外）
     * @param entries   {@link IndicatorFramePlanner#plan} 的结果
     */
    public static void submitBatch(SubmitNodeCollector collector,
                                   PoseStack poseStack,
                                   List<IndicatorFramePlanner.Entry> entries) {
        if (collector == null || poseStack == null || entries == null || entries.isEmpty()) {
            return;
        }

        if (!batchSubmit()) {
            refreshShadowSettings();
            submitEach(collector, poseStack, entries);
            return;
        }

        refreshShadowSettings();
        GROUPS.clear();
        for (IndicatorFramePlanner.Entry entry : entries) {
            for (RenderType type : entry.label.glyphsByType.keySet()) {
                GROUPS.computeIfAbsent(type, key -> new ArrayList<>(16)).add(entry);
            }
        }
        for (Map.Entry<RenderType, List<IndicatorFramePlanner.Entry>> group : GROUPS.entrySet()) {
            submitGroup(collector, poseStack, group.getKey(), group.getValue());
        }
        GROUPS.clear();
    }

    // ============================ 提交内部 ============================

    private static void submitEach(SubmitNodeCollector collector,
                                   PoseStack poseStack,
                                   List<IndicatorFramePlanner.Entry> entries) {
        for (IndicatorFramePlanner.Entry entry : entries) {
            for (Map.Entry<RenderType, List<TextRenderable>> byType : entry.label.glyphsByType.entrySet()) {
                RenderType type = byType.getKey();
                List<TextRenderable> glyphs = byType.getValue();
                collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
                    long startedAt = System.nanoTime();
                    BatchGlyphConsumer consumer = new BatchGlyphConsumer(buffer);
                    renderGlyphs(entry, glyphs, consumer);
                    IndicatorPerfStats.addVertexNanos(System.nanoTime() - startedAt);
                    IndicatorPerfStats.addVertices(consumer.vertexCount());
                });
            }
        }
    }

    private static void submitGroup(SubmitNodeCollector collector,
                                    PoseStack poseStack,
                                    RenderType type,
                                    List<IndicatorFramePlanner.Entry> group) {
        collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
            long startedAt = System.nanoTime();
            BatchGlyphConsumer consumer = new BatchGlyphConsumer(buffer);
            for (IndicatorFramePlanner.Entry entry : group) {
                List<TextRenderable> glyphs = entry.label.glyphsByType.get(type);
                if (glyphs != null) {
                    renderGlyphs(entry, glyphs, consumer);
                }
            }
            IndicatorPerfStats.addVertexNanos(System.nanoTime() - startedAt);
            IndicatorPerfStats.addVertices(consumer.vertexCount());
        });
    }

    /**
     * 画一条飘字：先把整行按「阴影位姿 + 压暗颜色」发一遍顶点，再按「原位姿 + 渐变颜色」发一遍。
     *
     * <p>两趟同缓冲、顺序即层级，所以正文稳定压在阴影上面。</p>
     */
    private static void renderGlyphs(IndicatorFramePlanner.Entry entry,
                                     List<TextRenderable> glyphs,
                                     BatchGlyphConsumer consumer) {
        consumer.begin(entry);

        if (shadowEnabled) {
            consumer.setDarken(true);
            consumer.setLocalOffset(shadowOffsetPx, shadowOffsetPx, -SHADOW_DEPTH_PX);
            for (TextRenderable glyph : glyphs) {
                glyph.render(entry.anchor, consumer, FULL_BRIGHT, false);
            }
        }

        consumer.setDarken(false);
        consumer.setLocalOffset(0.0f, 0.0f, 0.0f);
        for (TextRenderable glyph : glyphs) {
            glyph.render(entry.anchor, consumer, FULL_BRIGHT, false);
        }
    }

    private static boolean batchSubmit() {
        try {
            return PerformanceConfig.BATCH_SUBMIT.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** 飘字阴影开关；配置没加载时按「画阴影」处理（与旧观感一致）。 */
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

    /**
     * 逐顶点上色的 {@link VertexConsumer}。
     *
     * <p>香草字形按「左上 → 左下 → 右下 → 右上」发顶点（{@code addVertex(位姿, x, y, z)}），
     * 再 {@code setColor}，因此 setColor 时总能拿到当前顶点的本地 y；
     * 位姿与本地偏移在这里一次性乘好再写进顶点，避免默认实现每个顶点都新建向量。</p>
     */
    private static final class BatchGlyphConsumer implements VertexConsumer {

        private final VertexConsumer delegate;
        /** 锚点位姿 ∘ 本地偏移：整趟（阴影趟 / 正文趟）里是常量，进顶点循环前算一次 */
        private final Matrix4f combined = new Matrix4f();
        private final Matrix4f localOffset = new Matrix4f();
        private final Vector3f transformed = new Vector3f();

        /** 本趟的锚点位姿（begin 时锁定；一次字形渲染里不会变） */
        private Matrix4fc anchor;
        /** combined 需要重算（begin / setLocalOffset 之后置位） */
        private boolean combinedDirty = true;

        private int topColor = 0xFFFFFF;
        private int bottomColor = 0xFFFFFF;
        private int alpha8 = 255;
        private float yTop = -7.0f;
        private float yBottom = 1.0f;
        private float currentY = Float.NaN;
        private boolean darken;
        /** 这个消费器一共写出去多少顶点（F3 的读数用） */
        private int vertexCount;

        private BatchGlyphConsumer(VertexConsumer delegate) {
            this.delegate = delegate;
        }

        int vertexCount() {
            return this.vertexCount;
        }

        void begin(IndicatorFramePlanner.Entry entry) {
            this.topColor = entry.topColor & 0xFFFFFF;
            this.bottomColor = entry.bottomColor & 0xFFFFFF;
            this.anchor = entry.anchor;
            this.combinedDirty = true;
            if (entry.label != null) {
                this.yTop = entry.label.top;
                this.yBottom = entry.label.bottom;
            }
            this.alpha8 = entry.alpha8;
        }

        void setDarken(boolean darken) {
            this.darken = darken;
        }

        void setLocalOffset(float x, float y, float z) {
            this.localOffset.translation(x, y, z);
            this.combinedDirty = true;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            // 字形渲染统一走带位姿的重载（见 BakedSheetGlyph#render），这条只是契约兜底：
            // 至少把阴影偏移补上，不至于让正文和阴影完全重合。
            this.currentY = y;
            this.vertexCount++;
            this.localOffset.transformPosition(x, y, z, this.transformed);
            this.delegate.addVertex(this.transformed.x, this.transformed.y, this.transformed.z);
            return this;
        }

        @Override
        public VertexConsumer addVertex(Matrix4fc pose, float x, float y, float z) {
            this.currentY = y;
            this.vertexCount++;
            if (this.combinedDirty) {
                // 「锚点位姿 ∘ 本地偏移」在一趟里不变：早先每个顶点都重算一次 4×4 乘，
                // 按 128 条 × 7 字 × 4 顶点 × 2 趟 就是每帧上万次多余的矩阵乘
                this.combined.set(this.anchor != null ? this.anchor : pose).mul(this.localOffset);
                this.combinedDirty = false;
            }
            this.combined.transformPosition(x, y, z, this.transformed);
            this.delegate.addVertex(this.transformed.x, this.transformed.y, this.transformed.z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            this.delegate.setColor(this.resolveColor(ARGB.alpha(color)));
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            this.delegate.setColor(this.resolveColor(alpha));
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
        public VertexConsumer setLineWidth(float width) {
            this.delegate.setLineWidth(width);
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
            return ARGB.color(alpha, rgb);
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
