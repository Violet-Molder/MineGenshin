package com.linweiyun.genshin.asset;

import org.jetbrains.annotations.Nullable;

/**
 * 资源类别 —— 「资源文件夹名」的唯一来源。
 *
 * <h2>四种类别</h2>
 * <pre>
 * assets/minegenshin/
 * ├── item/&lt;id&gt;/        物品
 * ├── block/&lt;id&gt;/       方块 —— 方块状态（blockstate.json）与方块物品（blockitem/）都归这里
 * ├── entity/&lt;id&gt;/      实体
 * └── character/&lt;id&gt;/   角色
 * </pre>
 *
 * <p>方块状态不单独开一类：它只属于某一个方块，分开会让「同一个 id 的资源」被劈成两半。
 * 方块对应的物品（blockitem）同理，放在 {@code block/<id>/blockitem/} 子目录下。
 *
 * <p>文件夹名不带任何前缀 —— 它们全部位于 {@code assets/minegenshin/} 命名空间之下，
 * 与原版的 {@code blockstates/}、{@code models/}、{@code textures/}、以及
 * 1.21.4 之后新增的物品定义目录 {@code items/} 天然隔离。
 * {@link ModAssetPaths} 的所有路径都以「类别文件夹 + 一个斜杠」开头再拼接，
 * 所以 {@code items/}、{@code blockstates/} 这类同前缀目录不会被误收。
 */
public enum AssetCategory {

    /** 物品。 */
    ITEM("item"),

    /** 方块（含方块状态与方块物品）。 */
    BLOCK("block"),

    /** 实体。 */
    ENTITY("entity"),

    /** 角色。 */
    CHARACTER("character");

    private final String folder;

    AssetCategory(String folder) {
        this.folder = folder;
    }

    public String folder() {
        return this.folder;
    }

    /**
     * 反查类别：路径是不是以该类别文件夹开头。
     *
     * <p>必须带斜杠比较，否则 {@code items/xxx.json} 会被当成 {@link #ITEM}。
     */
    public boolean matchesPath(String path) {
        return path != null && path.startsWith(this.folder + "/");
    }

    /** 按文件夹名反查类别；不是四种之一时返回 null。 */
    @Nullable
    public static AssetCategory byFolder(@Nullable String folder) {
        if (folder == null) {
            return null;
        }
        for (AssetCategory category : values()) {
            if (category.folder.equals(folder)) {
                return category;
            }
        }
        return null;
    }
}
