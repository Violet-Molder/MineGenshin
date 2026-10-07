package com.linweiyun.genshin.client.performance;

import com.linweiyun.elementlib.core.element.GenshinElement;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 世界空间 HUD（血条 / 等级 / 元素图标）的<b>渲染缓存</b>（渲染优化模块）。
 *
 * <h2>为什么需要</h2>
 * 血条这条路每帧都要对<b>每个</b>战斗中的实体跑一遍，所以任何「看起来只要一行」的调用
 * 都会乘以「实体数 × 帧数」。这里收掉的三处都是这么被抓出来的：
 *
 * <ol>
 *   <li><b>元素图标贴图</b>：不缓存就要
 *       {@code ResourceLocation.fromNamespaceAndPath("minegenshin", "icon/elemental/" + id + ".png")}
 *       —— 每个图标、每个实体、每一帧都要拼一次字符串再 new 一个 {@link ResourceLocation}。
 *       现在按元素实例查表，稳态下零分配；</li>
 *   <li><b>血条用的 {@link RenderType}</b>：{@code RenderType.entityTranslucent(texture)}
 *       内部是 {@code Util.memoize(BiFunction)}，<b>每次调用都要新建一个 {@code Pair} 当缓存键</b>
 *       （见 {@code Util.memoize/2}）。一条血条要取 4~6 次，等于每实体每帧白造 4~6 个对象。
 *       这里把它提到「一类贴图只取一次」；</li>
 *   <li><b>等级文字 {@code "Lv." + level}</b>：拼接 + {@code Font#width} 排版都在每帧每实体重跑，
 *       而等级取值只有几十种。缓存文案本身与量得的宽度即可。</li>
 * </ol>
 *
 * <p>三个表都只有渲染主线程读写，所以不加同步；{@link #clear()} 在资源重载时由
 * {@link IndicatorPerfReloadListener} 调用。</p>
 */
public final class HudRenderCaches {

    /** 元素 → 图标 {@link RenderType}（按实例建表，元素是注册表里的单例） */
    private static final Map<GenshinElement, RenderType> ELEMENT_ICON_TYPES = new IdentityHashMap<>();

    /** 等级 → {@code "Lv.N"} 文案 */
    private static final Map<Integer, String> LEVEL_LABELS = new HashMap<>();
    /** 等级 → 该文案在当前字体下的像素宽度 */
    private static final Map<Integer, Integer> LEVEL_WIDTHS = new HashMap<>();
    /** 宽度表对应的字体实例；资源重载换了字体就整表作废 */
    private static Font widthFont;

    private HudRenderCaches() {}

    /**
     * 取某个元素的图标 {@link RenderType}。
     *
     * <p>第一次见到这个元素才拼字符串、建 ResourceLocation 并查一次 RenderType 缓存；
     * 之后每帧每实体都只是一次 IdentityHashMap 查询。</p>
     */
    public static RenderType elementIcon(GenshinElement element) {
        if (element == null) {
            return null;
        }
        RenderType cached = ELEMENT_ICON_TYPES.get(element);
        if (cached != null) {
            return cached;
        }
        RenderType built = RenderType.entityTranslucent(ResourceLocation.fromNamespaceAndPath(
                "minegenshin", "icon/elemental/" + element.getId() + ".png"));
        ELEMENT_ICON_TYPES.put(element, built);
        return built;
    }

    /**
     * 等级文字：{@code "Lv.N"}。
     *
     * <p>等级是小整数，装箱走 {@code Integer} 自带的缓存（-128~127），所以查表本身不分配。</p>
     */
    public static String levelLabel(int level) {
        String cached = LEVEL_LABELS.get(level);
        if (cached != null) {
            return cached;
        }
        String built = "Lv." + level;
        if (LEVEL_LABELS.size() < 512) {
            LEVEL_LABELS.put(level, built);
        }
        return built;
    }

    /**
     * 等级文字在当前字体下的宽度。
     *
     * <p>{@code Font#width(String)} 要走一遍字形推进量计算，等级取值又只有几十种，
     * 所以按「字体实例 + 等级」缓存。</p>
     */
    public static int levelLabelWidth(Font font, int level) {
        if (font == null) {
            return 0;
        }
        if (widthFont != font) {
            LEVEL_WIDTHS.clear();
            widthFont = font;
        }
        Integer cached = LEVEL_WIDTHS.get(level);
        if (cached != null) {
            return cached;
        }
        int built = font.width(levelLabel(level));
        LEVEL_WIDTHS.put(level, built);
        return built;
    }

    /** 资源重载 / 退出世界时清空（表本身很小，直接丢最省事）。 */
    public static void clear() {
        ELEMENT_ICON_TYPES.clear();
        LEVEL_LABELS.clear();
        LEVEL_WIDTHS.clear();
        widthFont = null;
    }

    /** 当前缓存条数（调试用）。 */
    public static int size() {
        return ELEMENT_ICON_TYPES.size() + LEVEL_LABELS.size() + LEVEL_WIDTHS.size();
    }
}
