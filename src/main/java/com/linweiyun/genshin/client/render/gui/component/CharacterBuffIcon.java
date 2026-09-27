package com.linweiyun.genshin.client.render.gui.component;

import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableUIElement;
import org.jetbrains.annotations.Nullable;

/**
 * 一个「按数据源换图」的图标槽 —— 角色 buff 栏用。
 *
 * <p>和 {@link StackBindUIElement} 同一套写法：数据源给一个贴图路径字符串，
 * {@code setValue} 里把背景换成对应贴图；给空串就把背景清掉（等于这一格不显示）。
 *
 * <p>为什么要专门一个类：元素附着图标是<b>动态</b>的（有几个元素、分别是哪个），
 * 而 LDLib2 的样式是静态的，只能靠 {@code setValue} 这种「值变了才改样式」的钩子。
 */
public class CharacterBuffIcon extends BindableUIElement<String> {

    /** 空串 = 这一格没有图标。 */
    private String value = "";

    @Override
    public String getValue() {
        return this.value;
    }

    @Override
    public BindableUIElement<String> setValue(@Nullable String next, boolean notify) {
        String texture = next == null ? "" : next;
        if (!texture.equals(this.value)) {
            this.value = texture;
            this.style(s -> s.background(texture.isEmpty() ? null : SpriteTexture.of(texture)));
        }
        return this;
    }
}
