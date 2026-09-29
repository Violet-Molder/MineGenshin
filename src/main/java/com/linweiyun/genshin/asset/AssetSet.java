package com.linweiyun.genshin.asset;

import net.minecraft.resources.Identifier;

/**
 * 「一个 id 的一整套资源」—— 类别 + id → 模型 / 动画 / 贴图 三个位置。
 *
 * <p>它是路径解析的入口：拿到类别和 id 之后，代码里不该再出现任何手写路径字符串。
 *
 * <pre>
 * AssetSet.of(AssetCategory.ENTITY, "test1")
 *     modelFile()     → minegenshin:entity/test1/test1.json
 *     animationFile() → minegenshin:entity/test1/test1.animation.json
 *     textureFile()   → minegenshin:entity/test1/test1.png
 *     modelKey()      → minegenshin:entity/test1/test1        （给 GeckoLib 缓存）
 *
 * AssetSet.blockItem("first_block", "first_block")
 *     modelFile()     → minegenshin:block/first_block/blockitem/first_block.json
 * </pre>
 *
 * <p>方块状态（{@code block/&lt;id&gt;/blockstate.json}）不属于 GeckoLib 的三件套，
 * 单独走 {@link ModAssetPaths#blockState(String)}。
 *
 * @param dir      目录（不含命名空间），形如 {@code entity/test1}
 * @param baseName 基名，形如 {@code test1}
 */
public record AssetSet(String dir, String baseName) {

    /** 标准三件套：{@code <类别>/<id>/} 下全部以 id 命名。 */
    public static AssetSet of(AssetCategory category, String id) {
        return new AssetSet(ModAssetPaths.dir(category, id), id);
    }

    /** 方块的三件套：{@code block/<id>/}，等价于 {@code of(BLOCK, id)}。 */
    public static AssetSet block(String blockId) {
        return of(AssetCategory.BLOCK, blockId);
    }

    /**
     * 方块物品的三件套：{@code block/<方块id>/blockitem/<物品名>}。
     *
     * @param blockId  方块 id
     * @param itemName 物品名；通常与方块 id 相同，不同名时靠它区分
     */
    public static AssetSet blockItem(String blockId, String itemName) {
        return new AssetSet(ModAssetPaths.blockItemDir(blockId), itemName);
    }

    // ==================== 文件位置 ====================

    /** 模型文件：{@code <dir>/<baseName>.json}。 */
    public Identifier modelFile() {
        return ModAssetPaths.inDir(this.dir, this.baseName, ModAssetPaths.JSON_SUFFIX);
    }

    /** 模型文件的另一种写法：{@code <dir>/<baseName>.geo.json}。 */
    public Identifier modelFileWithSuffix() {
        return ModAssetPaths.inDir(this.dir, this.baseName, ModAssetPaths.GEO_SUFFIX);
    }

    /** 动画文件：{@code <dir>/<baseName>.animation.json}。 */
    public Identifier animationFile() {
        return ModAssetPaths.inDir(this.dir, this.baseName, ModAssetPaths.ANIMATION_SUFFIX);
    }

    /** 贴图文件：{@code <dir>/textures/<baseName>.png}。 */
    public Identifier textureFile() {
        return ModAssetPaths.textureIn(this.dir, this.baseName + ModAssetPaths.PNG_SUFFIX);
    }

    // ==================== 原版入口文件（由 AssetRedirects 供料） ====================

    /** 物品定义：{@code <dir>/definition.json}（原版入口 {@code items/<id>.json}）。 */
    public Identifier definitionFile() {
        return ModAssetPaths.inDir(this.dir, ModAssetPaths.DEFINITION_FILE, "");
    }

    /** 原版模型：{@code <dir>/model.json}（原版入口 {@code models/<路径>.json}）。 */
    public Identifier vanillaModelFile() {
        return ModAssetPaths.inDir(this.dir, ModAssetPaths.MODEL_FILE, "");
    }

    /** 图集贴图：{@code <dir>/textures/texture.png}（原版入口 {@code textures/<路径>.png}）。 */
    public Identifier vanillaTextureFile() {
        return ModAssetPaths.textureIn(this.dir, ModAssetPaths.TEXTURE_FILE);
    }

    // ==================== 资源键 ====================

    /** 模型缓存键：{@code <dir>/<baseName>}。 */
    public Identifier modelKey() {
        return net.minecraft.resources.Identifier.fromNamespaceAndPath(
                com.linweiyun.genshin.Minegenshin.MOD_ID, this.dir + "/" + this.baseName);
    }

    /** 动画缓存键（与模型同键，见 {@link ModAssetPaths#assetKey}）。 */
    public Identifier animationKey() {
        return modelKey();
    }

    // ==================== 候选序列（给解析用） ====================

    /** 模型的候选文件，按优先级排列：{@code .json} 优先，其次 {@code .geo.json}。 */
    public Identifier[] modelCandidates() {
        return new Identifier[]{modelFile(), modelFileWithSuffix()};
    }

    /** 动画的候选文件。 */
    public Identifier[] animationCandidates() {
        return new Identifier[]{animationFile()};
    }

    /** 贴图的候选文件。 */
    public Identifier[] textureCandidates() {
        return new Identifier[]{textureFile()};
    }

    /** 调试用：{@code entity/test1/test1}。 */
    public String describe() {
        return this.dir + "/" + this.baseName;
    }
}
