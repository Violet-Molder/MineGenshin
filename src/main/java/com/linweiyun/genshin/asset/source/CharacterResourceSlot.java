package com.linweiyun.genshin.asset.source;

import org.jetbrains.annotations.Nullable;

/**
 * 角色的资源槽位 —— {@code resources.json} 里可逐项声明来源的那些资源。
 *
 * <p>{@link #key()} 就是 {@code resources.json} 里的字段名；没写的槽位走本 MOD 的默认约定
 * （{@code character/&lt;id&gt;/&lt;id&gt;.geo.json} 那一套），所以老角色不写这个文件也照旧。
 */
public enum CharacterResourceSlot {

    /** 模型：{@code character/<id>/<id>.geo.json}。 */
    MODEL("model"),

    /** 主动画文件：{@code character/<id>/<id>.animation.json}。 */
    ANIMATION("animation"),

    /** 模型贴图：{@code character/<id>/textures/<id>.png}。 */
    TEXTURE("texture"),

    /** 角色选择界面的头像：{@code character/<id>/textures/avatar.png}。 */
    AVATAR("avatar"),

    /** HUD / 装备页的小头像：{@code character/<id>/textures/avatar_hud.png}。 */
    AVATAR_HUD("avatar_hud"),

    /** 立绘（准备动作）：{@code character/<id>/textures/pose_prepare.png}。 */
    SPLASH("splash"),

    /** 立绘（已登场动作）：{@code character/<id>/textures/pose_already.png}。 */
    SPLASH_READY("splash_ready");

    private final String key;

    CharacterResourceSlot(String key) {
        this.key = key;
    }

    public String key() {
        return this.key;
    }

    /** 按字段名反查；不是已知槽位时返回 null。 */
    @Nullable
    public static CharacterResourceSlot byKey(@Nullable String key) {
        if (key == null) {
            return null;
        }
        for (CharacterResourceSlot slot : values()) {
            if (slot.key.equals(key)) {
                return slot;
            }
        }
        return null;
    }
}
