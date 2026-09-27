package com.linweiyun.genshin.core.asset;

import com.linweiyun.genshin.Minegenshin;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * 统一资源布局的路径拼装 —— 唯一知道「文件放哪」的地方。
 *
 * <h2>完整布局</h2>
 * <pre>
 * assets/minegenshin/
 * ├── item/&lt;id&gt;/
 * │      &lt;id&gt;.json               GeckoLib 模型（也接受 &lt;id&gt;.geo.json）
 * │      &lt;id&gt;.animation.json     动画
 * │      &lt;id&gt;.png                贴图
 * ├── block/&lt;id&gt;/
 * │      blockstate.json         方块状态定义（等价于原版 blockstates/&lt;id&gt;.json）
 * │      &lt;id&gt;.json               方块模型
 * │      &lt;id&gt;.animation.json
 * │      &lt;id&gt;.png
 * │      blockitem/&lt;名字&gt;.json   该方块对应物品的资源，名字默认与方块 id 相同
 * │      blockitem/&lt;名字&gt;.animation.json
 * │      blockitem/&lt;名字&gt;.png
 * ├── entity/&lt;id&gt;/
 * │      &lt;id&gt;.json
 * │      &lt;id&gt;.animation.json
 * │      &lt;id&gt;.png
 * └── character/&lt;id&gt;/
 *        （维持 {@code GenshinAssets} 的既有约定：<id>.animation.json + 可选 <id>.geo.json / <id>.png）
 *   ── 任意对象目录内都可以再有：
 *      local/&lt;名字&gt;.geo.json     免打包：明文直读、永不进资源包
 *      local/&lt;名字&gt;.animation.json
 *      local/textures/&lt;名字&gt;.png 免打包贴图（结构与外层一致：贴图在 textures/ 里）
 * </pre>
 *
 * <h2>两种资源</h2>
 * <ul>
 *   <li><b>打包资源</b>：对象目录下的 {@code .geo.json} / {@code .animation.json}，
 *       以及对象目录 {@code textures/} 下的 {@code .png}（贴图），构建期被收进
 *       {@code .minegenshin} 资源包，产物里看不到文件名
 *       （{@code gui/} 与 {@code icon/} 那类跨对象的共用界面贴图不进包）；</li>
 *   <li><b>免打包资源</b>：对象目录里 {@code local/} 子目录下的同类文件，
 *       明文放在仓库里、明文直读，打包时被跳过（见 {@link #LOCAL_DIR}）。</li>
 * </ul>
 *
 * <h2>两种「标识符」不要混淆</h2>
 * <ul>
 *   <li><b>文件位置</b>：带扩展名，给 {@code ResourceManager} 用，例如
 *       {@code minegenshin:entity/test1/test1.json}。</li>
 *   <li><b>资源键</b>：去掉扩展名，给 GeckoLib 的缓存用，例如
 *       {@code minegenshin:entity/test1/test1}。GeckoLib 的
 *       {@code GeckoLibResources.SUFFIX_STRIPPER} 会把 {@code .geo.json} 与 {@code .json}
 *       都剥成同一个键，所以 {@code test1.json} 与 {@code test1.geo.json} 是同一个键 ——
 *       这正是两种写法可以并存的原因。</li>
 * </ul>
 */
public final class ModAssetPaths {

    public static final String JSON_SUFFIX = ".json";
    public static final String GEO_SUFFIX = ".geo.json";
    public static final String ANIMATION_SUFFIX = ".animation.json";
    public static final String ANIMATIONS_SUFFIX = ".animations.json";
    public static final String PNG_SUFFIX = ".png";

    /** 方块状态定义的文件名：{@code block/&lt;id&gt;/blockstate.json}。 */
    public static final String BLOCKSTATE_FILE = "blockstate.json";

    /** 方块物品资源所在子目录名：{@code block/&lt;id&gt;/blockitem/}。 */
    public static final String BLOCK_ITEM_DIR = "blockitem";

    /**
     * 对象目录内部的<b>贴图</b>子目录名：{@code &lt;类别&gt;/&lt;id&gt;/textures/}。
     *
     * <p>一个角色 / 方块以后会有很多张图（模型贴图、头像、HUD 头像、立绘、技能图标……），
     * 所以对象目录里按类型再分一层：贴图全部进 {@code textures/}，音频进 {@code sounds/}
     * （{@link GenshinAssets#SOUNDS_DIR}），模型与动画留在对象根（文件名已经带类型后缀）。
     *
     * <p><b>这一层是「布局」而不是「资源身份」</b>：镜像给原版入口时会把这段 {@code textures/}
     * 去掉，所以 {@code item/&lt;id&gt;/textures/texture.png} 的图集 sprite id 仍然是
     * {@code minegenshin:item/&lt;id&gt;/texture}（见 {@link AssetRedirects}）。
     */
    public static final String TEXTURE_DIR = "textures";

    /**
     * 对象目录内部的<b>免打包</b>子目录名：{@code <类别>/<id>/local/}。
     *
     * <p>放在这里的 {@code .geo.json} / {@code .animation.json} 与被打包的那些<b>同样是资源</b>：
	 * 照样能被扫到、照样能烘培、照样当这个对象的模型 / 动画用，唯一的区别是
     * <b>明文直读、永不进 {@code .minegenshin} 资源包</b>。所以它适合放
     * 「想明文留档 / 临时改着看效果 / 不想进资源包」的模型与动画。
     *
     * <p>贴图同理，只是多一层：结构与对象目录一致，即
     * {@code <对象目录>/local/textures/<文件名>.png}；同一个 sprite / 贴图位置两边都有时，
     * 以 {@code local/} 里那份为准。
     *
     * <p>三条规则：
     * <ol>
     *   <li><b>不参与打包与还原</b>：打包工具跳过整段 {@code local/} 路径，校验闸门也不把它当残留；</li>
     *   <li><b>不算资源身份</b>：算缓存键 / 目录归属时会去掉这一层 ——
     *       {@code character/vesna/local/vesna.geo.json} 与
     *       {@code character/vesna/vesna.geo.json} 是<b>同一个键</b>；</li>
     *   <li><b>同名时它优先</b>：两边都定义了同一个键，用 {@code local/} 里那份。</li>
     * </ol>
     */
    public static final String LOCAL_DIR = "local";

    /**
     * 原版物品定义的文件名：{@code item/&lt;id&gt;/definition.json}。
     *
     * <p>它对应原版的 {@code items/&lt;id&gt;.json}，由 {@code AssetRedirects} 供料 ——
     * 之所以不叫 {@code items.json}，是为了在同一个对象目录里能和模型（{@code model.json}）
     * 各占一个名字、一眼看出谁是什么。
     */
    public static final String DEFINITION_FILE = "definition.json";

    /** 原版模型的文件名：{@code <类别>/&lt;id&gt;/model.json}（对应 {@code models/&lt;路径&gt;.json}）。 */
    public static final String MODEL_FILE = "model.json";

    /** 图集贴图的文件名：{@code <类别>/&lt;id&gt;/texture.png}（对应 {@code textures/&lt;路径&gt;.png}）。 */
    public static final String TEXTURE_FILE = "texture.png";

    private ModAssetPaths() {
    }

    // ==================== 原版入口文件（由 AssetRedirects 供料） ====================

    /** 物品定义：{@code item/<物品id>/definition.json}。 */
    public static Identifier itemDefinition(String itemId) {
        return inDir(dir(AssetCategory.ITEM, itemId), DEFINITION_FILE, "");
    }

    /** 平面物品模型：{@code item/<物品id>/model.json}。 */
    public static Identifier itemModel(String itemId) {
        return inDir(dir(AssetCategory.ITEM, itemId), MODEL_FILE, "");
    }

    /** 平面物品贴图：{@code item/<物品id>/textures/texture.png}。 */
    public static Identifier itemTexture(String itemId) {
        return textureIn(dir(AssetCategory.ITEM, itemId), TEXTURE_FILE);
    }

    /** 方块模型：{@code block/<方块id>/model.json}。 */
    public static Identifier blockModel(String blockId) {
        return inDir(dir(AssetCategory.BLOCK, blockId), MODEL_FILE, "");
    }

    /** 方块贴图：{@code block/<方块id>/textures/texture.png}。 */
    public static Identifier blockTexture(String blockId) {
        return textureIn(dir(AssetCategory.BLOCK, blockId), TEXTURE_FILE);
    }

    /** 方块物品定义：{@code block/<方块id>/blockitem/definition.json}。 */
    public static Identifier blockItemDefinition(String blockId) {
        return inDir(blockItemDir(blockId), DEFINITION_FILE, "");
    }

    /** 方块物品模型：{@code block/<方块id>/blockitem/model.json}。 */
    public static Identifier blockItemModel(String blockId) {
        return inDir(blockItemDir(blockId), MODEL_FILE, "");
    }

    /** 方块物品贴图：{@code block/<方块id>/blockitem/textures/texture.png}。 */
    public static Identifier blockItemTexture(String blockId) {
        return textureIn(blockItemDir(blockId), TEXTURE_FILE);
    }

    // ==================== 对象目录内的贴图 ====================

    /** 某个对象目录的贴图目录：{@code <对象目录>/textures}。 */
    public static String textureDir(String objectDir) {
        return objectDir + "/" + TEXTURE_DIR;
    }

    /** 某个对象目录下的贴图文件：{@code <对象目录>/textures/<文件名>}。 */
    public static Identifier textureIn(String objectDir, String fileName) {
        return Minegenshin.id(textureDir(objectDir) + "/" + fileName);
    }

    /**
     * 贴图的「归属对象目录」：把布局里的 {@code textures} 这一层去掉。
     *
     * <pre>
     * item/primogem                  → item/primogem
     * item/primogem/textures         → item/primogem
     * character/vesna/local          → character/vesna
     * character/vesna/local/textures → character/vesna
     * block/x/blockitem/textures     → block/x/blockitem
     * </pre>
     *
     * <p>缓存按「对象目录」索引模型 / 动画 / 贴图三件套；贴图与免打包资源各多了一层
     * 布局目录（{@link #TEXTURE_DIR} / {@link #LOCAL_DIR}），必须靠这个换算归位。
     * 两层叠着也照样归位：{@code <对象目录>/local/textures} 里的贴图属于那个对象目录。
     */
    @Nullable
    public static String objectDirOf(@Nullable String dir) {
        if (dir == null) {
            return null;
        }
        String result = dir;
        boolean stripped = true;
        while (stripped) {
            stripped = false;
            for (String layer : new String[]{TEXTURE_DIR, LOCAL_DIR}) {
                String suffix = "/" + layer;
                if (result.endsWith(suffix)) {
                    result = result.substring(0, result.length() - suffix.length());
                    stripped = true;
                }
            }
        }
        return result;
    }

    /** 这个目录是不是某个对象目录里的免打包子目录：{@code <对象目录>/local}。 */
    public static boolean isLocalDir(@Nullable String dir) {
        return dir != null && dir.endsWith("/" + LOCAL_DIR);
    }

    /** 这个资源位置是不是落在免打包子目录里（路径里带一段 {@code local/}）。 */
    public static boolean isLocalFile(@Nullable Identifier raw) {
        return raw != null && raw.getPath().contains("/" + LOCAL_DIR + "/");
    }

    /**
     * 去掉路径里的 {@code local} 这一层 —— 免打包资源与打包资源因此共享同一个键。
     *
     * <pre>
     * character/vesna/local/vesna.geo.json → character/vesna/vesna.geo.json
     * character/vesna/vesna.geo.json       → 原样返回
     * </pre>
     *
     * <p>只去掉<b>第一处</b>（对象目录里不会有两层）；命名空间不变。
     */
    public static Identifier withoutLocalDir(@Nullable Identifier raw) {
        if (raw == null) {
            return null;
        }
        String marker = "/" + LOCAL_DIR + "/";
        String path = raw.getPath();
        int at = path.indexOf(marker);
        if (at < 0) {
            return raw;
        }
        return Identifier.fromNamespaceAndPath(raw.getNamespace(), path.substring(0, at + 1) + path.substring(at + marker.length()));
    }

    // ==================== 目录 ====================

    /** 某个类别下某个 id 的目录：{@code <类别>/<id>}。 */
    public static String dir(AssetCategory category, String id) {
        return category.folder() + "/" + id;
    }

    /** 某个方块对应的物品资源目录：{@code block/<方块id>/blockitem}。 */
    public static String blockItemDir(String blockId) {
        return dir(AssetCategory.BLOCK, blockId) + "/" + BLOCK_ITEM_DIR;
    }

    // ==================== 文件位置 ====================

    /** 类别目录下的任意文件：{@code <类别>/<id>/<relative>}。 */
    public static Identifier file(AssetCategory category, String id, String relative) {
        return Minegenshin.id(dir(category, id) + "/" + relative);
    }

    /** 按目录 + 基名拼文件：{@code <目录>/<基名><扩展名>}。 */
    public static Identifier inDir(String directory, String baseName, String suffix) {
        return Minegenshin.id(directory + "/" + baseName + suffix);
    }

    /** 模型文件：{@code <类别>/<id>/<id>.json}。 */
    public static Identifier geoModel(AssetCategory category, String id) {
        return file(category, id, id + JSON_SUFFIX);
    }

    /** 模型文件（GeckoLib 原生后缀写法）：{@code <类别>/<id>/<id>.geo.json}。 */
    public static Identifier geoModelWithSuffix(AssetCategory category, String id) {
        return file(category, id, id + GEO_SUFFIX);
    }

    /** 动画文件：{@code <类别>/<id>/<id>.animation.json}。 */
    public static Identifier animation(AssetCategory category, String id) {
        return file(category, id, id + ANIMATION_SUFFIX);
    }

    /** 贴图文件：{@code <类别>/<id>/<id>.png}。 */
    public static Identifier texture(AssetCategory category, String id) {
        return file(category, id, id + PNG_SUFFIX);
    }

    /** 方块状态定义：{@code block/<id>/blockstate.json}。 */
    public static Identifier blockState(String blockId) {
        return file(AssetCategory.BLOCK, blockId, BLOCKSTATE_FILE);
    }

    /** 方块物品的模型：{@code block/<方块id>/blockitem/<物品名>.json}。 */
    public static Identifier blockItemGeoModel(String blockId, String itemName) {
        return inDir(blockItemDir(blockId), itemName, JSON_SUFFIX);
    }

    /** 方块物品的模型（GeckoLib 原生后缀写法）。 */
    public static Identifier blockItemGeoModelWithSuffix(String blockId, String itemName) {
        return inDir(blockItemDir(blockId), itemName, GEO_SUFFIX);
    }

    /** 方块物品的动画：{@code block/<方块id>/blockitem/<物品名>.animation.json}。 */
    public static Identifier blockItemAnimation(String blockId, String itemName) {
        return inDir(blockItemDir(blockId), itemName, ANIMATION_SUFFIX);
    }

    /** 方块物品的贴图：{@code block/<方块id>/blockitem/<物品名>.png}。 */
    public static Identifier blockItemTexture(String blockId, String itemName) {
        return inDir(blockItemDir(blockId), itemName, PNG_SUFFIX);
    }

    // ==================== 资源键（喂给 GeckoLib） ====================

    /**
     * 模型 / 动画的缓存键：{@code <类别>/<id>/<id>}（无扩展名）。
     *
     * <p>GeckoLib 对模型和动画剥掉的是同一个后缀集合（{@code .geo.json} / {@code .animation.json} /
     * {@code .json}），所以两者共用同一个键。
     */
    public static Identifier assetKey(AssetCategory category, String id) {
        return Minegenshin.id(dir(category, id) + "/" + id);
    }

    /** 方块物品的缓存键：{@code block/<方块id>/blockitem/<物品名>}。 */
    public static Identifier blockItemKey(String blockId, String itemName) {
        return Minegenshin.id(blockItemDir(blockId) + "/" + itemName);
    }

    // ==================== 后缀判定 ====================

    /**
     * 模型文件判定：{@code .json} 结尾，但不是动画、也不是方块状态定义。
     *
     * <p>{@code .geo.json} 同样算模型 —— 两种写法都收。
     */
    public static boolean isGeoModelFile(Identifier raw) {
        if (raw == null) {
            return false;
        }
        String path = raw.getPath();
        if (!path.endsWith(JSON_SUFFIX) || path.endsWith(ANIMATION_SUFFIX) || path.endsWith(ANIMATIONS_SUFFIX)) {
            return false;
        }
        return !path.endsWith("/" + BLOCKSTATE_FILE);
    }

    /** 动画文件判定：{@code .animation.json} 或 {@code .animations.json} 结尾。 */
    public static boolean isAnimationFile(Identifier raw) {
        if (raw == null) {
            return false;
        }
        String path = raw.getPath();
        return path.endsWith(ANIMATION_SUFFIX) || path.endsWith(ANIMATIONS_SUFFIX);
    }

    /** 从文件位置算出模型缓存键（剥 {@code .geo.json}，再剥 {@code .json}）。 */
    public static Identifier modelKeyOf(Identifier raw) {
        return strip(raw, GEO_SUFFIX, JSON_SUFFIX);
    }

    /** 从文件位置算出动画缓存键（剥 {@code .animation.json} / {@code .animations.json} / {@code .json}）。 */
    public static Identifier animationKeyOf(Identifier raw) {
        return strip(raw, ANIMATION_SUFFIX, ANIMATIONS_SUFFIX, JSON_SUFFIX);
    }

    private static Identifier strip(@Nullable Identifier raw, String... suffixes) {
        if (raw == null) {
            return null;
        }
        String path = raw.getPath();
        for (String suffix : suffixes) {
            if (path.endsWith(suffix)) {
                path = path.substring(0, path.length() - suffix.length());
                break;
            }
        }
        return Identifier.fromNamespaceAndPath(raw.getNamespace(), path);
    }

    // ==================== 反查 ====================

    /** 这个资源位置属于哪个类别；不在四种统一布局里就返回 null。 */
    @Nullable
    public static AssetCategory categoryOf(@Nullable Identifier raw) {
        if (raw == null || !Minegenshin.MOD_ID.equals(raw.getNamespace())) {
            return null;
        }
        String path = raw.getPath();
        for (AssetCategory category : AssetCategory.values()) {
            if (category.matchesPath(path)) {
                return category;
            }
        }
        return null;
    }

    /**
     * 从资源位置里取出 id：类别文件夹之后的<b>第一段</b>。
     *
     * <pre>
     * entity/test1/test1.json            → test1
     * block/first_block/blockitem/a.json → first_block
     * item/test_sword/test_sword.png     → test_sword
     * </pre>
     */
    @Nullable
    public static String idOf(@Nullable Identifier raw) {
        AssetCategory category = categoryOf(raw);
        if (category == null) {
            return null;
        }
        String remainder = raw.getPath().substring(category.folder().length() + 1);
        int slash = remainder.indexOf('/');
        String id = slash < 0 ? remainder : remainder.substring(0, slash);
        return id.isEmpty() ? null : id;
    }

    /**
     * 从资源位置里取出「作用」：类别 + id 之后剩下的相对路径，也就是文件名。
     *
     * <pre>
     * entity/test1/test1.json            → test1.json
     * block/first_block/blockstate.json  → blockstate.json
     * block/first_block/blockitem/a.png  → blockitem/a.png
     * </pre>
     */
    @Nullable
    public static String roleOf(@Nullable Identifier raw) {
        AssetCategory category = categoryOf(raw);
        if (category == null) {
            return null;
        }
        String remainder = raw.getPath().substring(category.folder().length() + 1);
        int slash = remainder.indexOf('/');
        if (slash < 0) {
            return null;
        }
        String role = remainder.substring(slash + 1);
        return role.isEmpty() ? null : role;
    }

    /** 这个资源位置是不是方块状态定义。 */
    public static boolean isBlockState(@Nullable Identifier raw) {
        return raw != null
                && AssetCategory.BLOCK.matchesPath(raw.getPath())
                && raw.getPath().endsWith("/" + BLOCKSTATE_FILE);
    }

    /** 这个资源位置是不是方块物品资源（{@code block/<id>/blockitem/…}）。 */
    public static boolean isBlockItem(@Nullable Identifier raw) {
        return raw != null
                && AssetCategory.BLOCK.matchesPath(raw.getPath())
                && raw.getPath().contains("/" + BLOCK_ITEM_DIR + "/");
    }

    /**
     * 资源位置所在目录：去掉最后一段。
     *
     * <pre>
     * entity/test1/test1.json             → entity/test1
     * block/first_block/blockitem/a.png   → block/first_block/blockitem
     * </pre>
     */
    @Nullable
    public static String dirOf(@Nullable Identifier raw) {
        if (raw == null) {
            return null;
        }
        String path = raw.getPath();
        int slash = path.lastIndexOf('/');
        return slash <= 0 ? null : path.substring(0, slash);
    }

    /**
     * 资源位置的基名：最后一段去掉已知扩展名。
     *
     * <pre>
     * entity/test1/test1.animation.json   → test1
     * entity/test1/test1.geo.json         → test1
     * entity/test1/test1.png              → test1
     * block/first_block/blockstate.json   → blockstate
     * </pre>
     */
    @Nullable
    public static String baseNameOf(@Nullable Identifier raw) {
        if (raw == null) {
            return null;
        }
        String path = raw.getPath();
        int slash = path.lastIndexOf('/');
        String file = slash < 0 ? path : path.substring(slash + 1);
        for (String suffix : new String[]{ANIMATION_SUFFIX, ANIMATIONS_SUFFIX, GEO_SUFFIX, JSON_SUFFIX, PNG_SUFFIX}) {
            if (file.endsWith(suffix)) {
                return file.substring(0, file.length() - suffix.length());
            }
        }
        return file;
    }

    /** 目录的最后一段：{@code entity/test1} → {@code test1}。 */
    @Nullable
    public static String dirNameOf(@Nullable String dir) {
        if (dir == null) {
            return null;
        }
        int slash = dir.lastIndexOf('/');
        return slash < 0 ? dir : dir.substring(slash + 1);
    }
}
