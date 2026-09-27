package com.linweiyun.genshin.client.damage;

import com.linweiyun.genshin.client.performance.IndicatorFramePlanner;
import com.linweiyun.genshin.client.performance.IndicatorGlyphCache;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

/**
 * 一条伤害飘字（客户端实例）。
 *
 * <p>除了动画参数，它自己还兼作<b>渲染缓存</b>：本地化后的文字与
 * {@link IndicatorGlyphCache} 的排版结果都挂在实例上。
 * 飘字一动就画十几帧，这样每帧只做「读字段」，不必再走
 * {@code I18n.get}（内部含一次 {@code String.format}）与字体排版。</p>
 *
 * <p>被合并的伤害数字会直接改写同一条实例（见 {@link #applyMerge}），
 * 所以文字、颜色、时长这些字段不是 final。</p>
 */
public class DamageIndicator {

    private static final long MOVE_MS = 200;
    private static final long FADE_MS = 300;
    private static final double RISE_HEIGHT = 0.23;

    /** 攻击点：飘字的出发位置 */
    public Vec3 origin;
    /** 落点：飘字飘向的位置 */
    public Vec3 target;
    /** 本地化键或纯文本 */
    public String text;
    public int topColor;
    public int bottomColor;
    public final byte style;
    public final boolean italic;
    /** 合并键：同一次连击里保持不变，用来把后续数字并进这一条（0 = 不参与合并） */
    public final int mergeKey;

    public float baseScale;
    public float startScale;
    public long lifetimeMs;

    private long spawnTime;

    // ---- 渲染缓存：本地化文字 + 排版结果 ----
    private String labelSource;
    private Language labelLanguage;
    private Font labelFont;
    private IndicatorGlyphCache.Label label;

    public DamageIndicator(Vec3 origin, Vec3 target, String text,
                           int topColor, int bottomColor, byte style,
                           boolean italic,
                           float baseScale, float startScale, long lifetimeMs) {
        this(origin, target, text, topColor, bottomColor, style, italic,
                baseScale, startScale, lifetimeMs, 0);
    }

    public DamageIndicator(Vec3 origin, Vec3 target, String text,
                           int topColor, int bottomColor, byte style,
                           boolean italic,
                           float baseScale, float startScale, long lifetimeMs,
                           int mergeKey) {
        this.origin = origin;
        this.target = target;
        this.text = text;
        this.topColor = topColor;
        this.bottomColor = bottomColor;
        this.style = style;
        this.italic = italic;
        this.mergeKey = mergeKey;
        this.baseScale = baseScale;
        this.startScale = startScale;
        this.lifetimeMs = lifetimeMs;
        this.spawnTime = System.currentTimeMillis();
    }

    public boolean isExpired() {
        return getAgeMs() >= lifetimeMs;
    }

    public Vec3 getCurrentPosition() {
        return getCurrentPosition(System.currentTimeMillis());
    }

    public float getScale() {
        return getScale(System.currentTimeMillis());
    }

    public float getAlpha() {
        return getAlpha(System.currentTimeMillis());
    }

    /** 这条飘字已经活了多久（合并判定用） */
    public long getAgeMs() {
        return getAgeMs(System.currentTimeMillis());
    }

    // ---- 指定时间戳的重载：渲染时每帧只取一次时钟，一批飘字共用同一个 now ----

    public Vec3 getCurrentPosition(long now) {
        long elapsed = now - spawnTime;
        float tMove = Math.min(1f, elapsed / (float) MOVE_MS);
        Vec3 basePos = origin.lerp(target, easeOutCubic(tMove));
        float tRise = Math.min(1f, elapsed / (float) safeLifetime());
        return basePos.add(0, RISE_HEIGHT * tRise, 0);
    }

    public float getScale(long now) {
        long elapsed = now - spawnTime;
        return startScale - (startScale - baseScale) * easeOutCubic(Math.min(1f, elapsed / (float) MOVE_MS));
    }

    public float getAlpha(long now) {
        long elapsed = now - spawnTime;
        long fadeStart = lifetimeMs - FADE_MS;
        if (elapsed < fadeStart) return 1f;
        return Math.max(0f, 1f - (elapsed - fadeStart) / (float) FADE_MS);
    }

    public long getAgeMs(long now) {
        return now - spawnTime;
    }

    /**
     * 把这一帧渲染要用的量<b>一次算完</b>写进规划条目：相机相对位置、缩放、透明度。
     *
     * <p>这是渲染热路径上每帧每飘字都要走的唯一一段动画计算。过去拆成
     * {@code getCurrentPosition()} + {@code getScale()} + {@code getAlpha()} 三次调用，
     * 就对应三次 {@code System.currentTimeMillis()} 和两三个临时 {@link Vec3}；
     * 现在一批飘字共用一个 {@code now}，结果直接落到条目字段（连坐标都用 float 存）。</p>
     *
     * @param now  本帧统一的时间戳（毫秒）
     * @param camX 相机位置（世界空间，用来算相对坐标）
     * @param out  本帧复用的规划条目
     */
    public void sampleFrame(long now, double camX, double camY, double camZ,
                            IndicatorFramePlanner.Entry out) {
        long elapsed = now - spawnTime;
        float eased = easeOutCubic(Math.min(1f, elapsed / (float) MOVE_MS));

        double px = origin.x + (target.x - origin.x) * eased;
        double py = origin.y + (target.y - origin.y) * eased + RISE_HEIGHT * Math.min(1f, elapsed / (float) safeLifetime());
        double pz = origin.z + (target.z - origin.z) * eased;

        out.relX = (float) (px - camX);
        out.relY = (float) (py - camY);
        out.relZ = (float) (pz - camZ);
        out.scale = startScale - (startScale - baseScale) * eased;

        float alpha = getAlpha(now);
        out.alpha = alpha;
        out.alpha8 = ARGB.as8BitChannel(Math.min(1f, alpha));
    }

    /** lifetimeMs 为 0（配置写坏）时不至于除以 0 得到 NaN */
    private long safeLifetime() {
        return lifetimeMs <= 0L ? 1L : lifetimeMs;
    }

    /**
     * 把一条新的伤害并进这条飘字：数字变大、重新起跳，从当前位置继续飘。
     *
     * <p>起点取「当前动画位置」，所以合并时不会突然后退或瞬移。</p>
     */
    public void applyMerge(Vec3 newTarget, String newText,
                           int newTopColor, int newBottomColor,
                           float newBaseScale, float newStartScale, long newLifetimeMs) {
        this.origin = getCurrentPosition();
        this.target = newTarget;
        this.text = newText;
        this.topColor = newTopColor;
        this.bottomColor = newBottomColor;
        this.baseScale = newBaseScale;
        this.startScale = newStartScale;
        this.lifetimeMs = newLifetimeMs;
        this.spawnTime = System.currentTimeMillis();
        // 文字变了，排版结果作废（下一次规划时重建）
        this.label = null;
        this.labelSource = null;
    }

    /**
     * 取这条飘字的排版结果：字体或语言换了、文字改了才重建，其余时候直接命中字段。
     */
    public IndicatorGlyphCache.Label labelFor(Font font) {
        if (label != null && labelFont == font
                && labelLanguage == Language.getInstance()
                && text.equals(labelSource)) {
            return label;
        }

        String localized = I18n.get(text);
        IndicatorGlyphCache.Label built = IndicatorGlyphCache.get(font, localized, italic);
        if (built != null) {
            this.label = built;
            this.labelFont = font;
            this.labelLanguage = Language.getInstance();
            this.labelSource = text;
        }
        return built;
    }

    /** 是否使用了渐变色 */
    public boolean isGradient() {
        return topColor != bottomColor;
    }

    private static float easeOutCubic(float t) {
        float p = 1f - t;
        return 1f - p * p * p;
    }
}
