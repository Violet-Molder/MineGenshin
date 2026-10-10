package com.linweiyun.genshin.client.render.gui.text;

import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.elementlib.core.element.GenshinElement;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Locale;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

/**
 * 天赋描述的富文本：语言文件里写纯文本，这里按标记上色。
 *
 * <p>标记：行首 {@code #} = 小标题；{@code {冰元素伤害}} / {@code {冰元素范围伤害}} / {@code {冰元素抗性}} = 元素色；
 * {@code [[名词]]} = 名词解释（暂只做颜色 + 下划线）；{@code {v:键}} = 该天赋当前等级下的数值（按百分比显示）。
 */
public final class CharacterText {

    private static final int SUBTITLE_COLOR = 0xFFD166;
    private static final int TERM_COLOR = 0xFFD166;

    private CharacterText() {
    }

    public static MutableComponent parse(String raw) {
        return parse(raw, null);
    }

    public static MutableComponent parse(String raw, @Nullable Function<String, Double> values) {
        MutableComponent out = Component.empty();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        String[] lines = raw.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                out.append(Component.literal("\n"));
            }
            String line = lines[i];
            if (line.startsWith("#")) {
                out.append(Component.literal(line.substring(1)).withStyle(
                        Style.EMPTY.withColor(TextColor.fromRgb(SUBTITLE_COLOR)).withBold(true)));
                continue;
            }
            out.append(inline(line, values));
        }
        return out;
    }

    private static MutableComponent inline(String line, @Nullable Function<String, Double> values) {
        MutableComponent out = Component.empty();
        int i = 0;
        while (i < line.length()) {
            int termStart = line.indexOf("[[", i);
            int elemStart = line.indexOf('{', i);
            int next = -1;
            boolean term = false;
            if (termStart >= 0 && (elemStart < 0 || termStart < elemStart)) {
                next = termStart;
                term = true;
            } else if (elemStart >= 0) {
                next = elemStart;
            }
            if (next < 0) {
                out.append(Component.literal(line.substring(i)));
                break;
            }
            if (next > i) {
                out.append(Component.literal(line.substring(i, next)));
            }
            if (term) {
                int end = line.indexOf("]]", next);
                if (end < 0) {
                    out.append(Component.literal(line.substring(next)));
                    break;
                }
                out.append(Component.literal(line.substring(next + 2, end)).withStyle(
                        Style.EMPTY.withColor(TextColor.fromRgb(TERM_COLOR)).withUnderlined(true)));
                i = end + 2;
            } else {
                int end = line.indexOf('}', next);
                if (end < 0) {
                    out.append(Component.literal(line.substring(next)));
                    break;
                }
                String token = line.substring(next + 1, end);
                MutableComponent styled = token.startsWith("v:")
                        ? valueToken(token.substring(2), values)
                        : elementToken(token);
                out.append(styled == null ? Component.literal("{" + token + "}") : styled);
                i = end + 1;
            }
        }
        return out;
    }

    private static MutableComponent valueToken(String key, @Nullable Function<String, Double> values) {
        if (values == null) {
            return null;
        }
        Double value = values.apply(key);
        if (value == null) {
            return null;
        }
        return Component.literal(String.format(Locale.ROOT, "%.1f%%", value * 100.0));
    }

    private static MutableComponent elementToken(String token) {
        for (GenshinElement element : ElementText.all()) {
            String name = Component.translatable(elementKey(element)).getString();
            if (token.equals(name + suffix("minegenshin.element.damage"))) {
                return ElementText.damage(element);
            }
            if (token.equals(name + suffix("minegenshin.element.area_damage"))) {
                return ElementText.areaDamage(element);
            }
            if (token.equals(name + suffix("minegenshin.element.resistance"))) {
                return ElementText.resistance(element);
            }
        }
        return null;
    }

    private static String suffix(String key) {
        return Component.translatable(key).getString();
    }

    private static String elementKey(GenshinElement element) {
        return "minegenshin.element." + element.getId();
    }
}