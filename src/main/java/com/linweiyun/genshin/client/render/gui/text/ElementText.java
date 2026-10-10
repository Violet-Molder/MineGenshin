package com.linweiyun.genshin.client.render.gui.text;

import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.damage.DamageIndicatorFactory;
import com.linweiyun.elementlib.core.element.GenshinElement;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/** 元素相关文本：自带元素的配置颜色，不用每处再指定。 */
public final class ElementText {

    private static final String KEY_PREFIX = "minegenshin.element.";
    private static final String SUFFIX_DAMAGE = "minegenshin.element.damage";
    private static final String SUFFIX_AREA_DAMAGE = "minegenshin.element.area_damage";
    private static final String SUFFIX_RESISTANCE = "minegenshin.element.resistance";

    private ElementText() {
    }

    public static int color(GenshinElement element) {
        return DamageIndicatorFactory.getColorForElement(element);
    }

    public static Style style(GenshinElement element) {
        return Style.EMPTY.withColor(TextColor.fromRgb(color(element)));
    }

    public static MutableComponent name(GenshinElement element) {
        return Component.translatable(elementKey(element)).withStyle(style(element));
    }

    public static MutableComponent damage(GenshinElement element) {
        return name(element).append(Component.translatable(SUFFIX_DAMAGE).withStyle(style(element)));
    }

    public static MutableComponent areaDamage(GenshinElement element) {
        return name(element).append(Component.translatable(SUFFIX_AREA_DAMAGE).withStyle(style(element)));
    }

    public static MutableComponent resistance(GenshinElement element) {
        return name(element).append(Component.translatable(SUFFIX_RESISTANCE).withStyle(style(element)));
    }

    private static String elementKey(GenshinElement element) {
        return KEY_PREFIX + element.getId();
    }

    public static GenshinElement[] all() {
        return new GenshinElement[]{
                ModElements.PYRO.get(), ModElements.HYDRO.get(), ModElements.ANEMO.get(),
                ModElements.ELECTRO.get(), ModElements.DENDRO.get(), ModElements.CYRO.get(),
                ModElements.GEO.get(), ModElements.FYSIKOS.get()
        };
    }
}