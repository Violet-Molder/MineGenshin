package com.linweiyun.genshin.core.character.ib;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * 对方模组（IB）里一个角色的资源定义 —— 由 {@code ib_character/&lt;id&gt;/basics.json} 与
 * {@code ib_character/&lt;id&gt;/renderer/render.json} 归拢而成，字段含义与对方保持一致。
 *
 * @param id          对方侧的角色 id（{@code basics.json} 的 {@code ID}）
 * @param model       模型文件，形如 {@code imaginary_branch:geo/miyabi.geo.json}
 * @param animation   动画文件，形如 {@code imaginary_branch:animations/miyabi.animation.json}
 * @param texture     模型贴图，形如 {@code imaginary_branch:textures/character/miyabi_texture.png}
 * @param itemTexture 角色信物图标，形如 {@code imaginary_branch:ib_character/miyabi/item/item.png}；可能没有
 * @param bodyScale   模型整体缩放；读不到时为 1
 * @param lang        对方给的本地化文案，键形如 {@code zh_cn.name} / {@code en_us.item}
 */
public record IBRenderDefinition(
        String id,
        ResourceLocation model,
        ResourceLocation animation,
        ResourceLocation texture,
        @Nullable ResourceLocation itemTexture,
        float bodyScale,
        Map<String, String> lang) {

    /** 按 locale + 后缀取一条文案（对方自己的回退规则：先精确 locale，再 en_us）。 */
    @Nullable
    public String localized(String locale, String key) {
        String exact = this.lang.get(locale + "." + key);
        if (exact != null && !exact.isBlank()) {
            return exact;
        }
        String english = this.lang.get("en_us." + key);
        return english != null && !english.isBlank() ? english : null;
    }
}
