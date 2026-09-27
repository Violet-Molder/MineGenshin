package com.linweiyun.genshin.core.asset;

import com.linweiyun.genshin.Minegenshin;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * 本 MOD 的资产目录约定 —— <b>唯一知道「文件放哪」的地方</b>。
 *
 * <h2>统一布局</h2>
 * <pre>
 * assets/minegenshin/
 * ├── character/&lt;角色id&gt;/     ← 角色：一个角色一个文件夹，内部再按类型分
 * │      &lt;角色id&gt;.animation.json                动画（模型/动画留对象根，文件名自带类型后缀）
 * │      textures/&lt;角色id&gt;.png                   角色模型贴图
 * │      textures/avatar.png  avatar_hud.png     列表头像 / HUD 头像
 * │      textures/pose_prepare.png  pose_already.png
 * │      textures/skill.png  burst.png           战技 / 爆发图标
 * │      sounds.json  sounds/*.ogg               音效定义与音频
 * ├── item/&lt;物品id&gt;/          ← 物品：定义 / 平面模型（入口文件）+ 贴图与图标
 * │      definition.json  model.json             （原版入口，见 AssetRedirects）
 * │      textures/texture.png  icon.png          平面贴图 / GUI 图标（geo 物品另有 textures/&lt;id&gt;.png）
 * ├── block/&lt;方块id&gt;/         ← 方块：状态 / 模型（入口文件）+ 贴图 + 方块物品
 * │      blockstate.json  model.json
 * │      textures/texture.png  icon.png
 * │      blockitem/{definition,model}.json  blockitem/textures/…
 * ├── entity/&lt;实体id&gt;/        ← 提瓦特实体
 * │      &lt;id&gt;.geo.json  &lt;id&gt;.animation.json  textures/&lt;id&gt;.png
 * ├── gui/  icon/&lt;分类&gt;/      ← 共用界面贴图、跨对象图标（背包 / 装备槽用）
 * └── lss/  lang/             ← 样式表、语言文件（语言文件是原版硬性入口，一个语言一个文件）
 * </pre>
 *
 * <p><b>入口按对象、对象内部按类型。</b>原版引擎钉死的入口层（{@code items/}、{@code models/}、
 * {@code blockstates/}、{@code textures/} 四个根）由 {@code core.asset.AssetRedirects} +
 * {@code FileToIdConverterRedirectMixin} 在读取时改写成上面的布局，<b>只对本 MOD 命名空间生效</b>，
 * 整合包里其它 MOD 的目录完全不受影响。
 *
 * <h2>注意 GeckoLib 的扫描根</h2>
 * GeckoLib 5.5.6 只会扫描 {@code assets/<ns>/geckolib/models/**} 与
 * {@code assets/<ns>/geckolib/animations/**}（见 {@code GeckoLibResources.MODELS_PATH} /
 * {@code ANIMATIONS_PATH}，是硬编码常量），也没有公开接口能加根目录。本 MOD 的做法是
 * <b>自己扫、自己烘培</b>：{@code GenshinGeoCache} 扫 {@code character/}、{@code item/}、
 * {@code entity/} 三个根（只认 {@code .geo.json} / {@code .animation.json} 两种后缀），
 * 烘培结果由 {@code GenshinGeoModel} 覆盖的两个 public 方法取用
 * （{@code GeoModel#getBakedModel} 与 {@code GeoModel#getBakedAnimation} 都是 public 非 final，
 * 所有查找都收敛在这两个方法上）。这样<b>不动 GeckoLib 任何全局状态</b>，整合包里其它 MOD 完全不受影响。
 * 贴图不受这个限制，放哪都行。
 *
 * <h2>改路径怎么办</h2>
 * 不要满工程改字符串 —— 用 {@link GeoPathOverrides} 注册一条规则，见
 * {@link #installDefaults()}。
 */
public final class GenshinAssets {

    private GenshinAssets() {
    }

    public static final String MOD_ID = Minegenshin.MOD_ID;

    /** 角色资源根目录（对应 GeckoLib 的额外扫描根）。 */
    public static final String CHARACTER_ROOT = "character";
    /** 物品资源根目录。 */
    public static final String ITEM_ROOT = "item";
    /** 实体资源根目录。 */
    public static final String ENTITY_ROOT = "entity";
    /** GUI 图标根目录。 */
    public static final String ICON_ROOT = "icon";
    /** 角色音效目录名：{@code character/<角色id>/sounds/}。 */
    public static final String SOUNDS_DIR = "sounds";
    /**
     * 角色音效定义文件名：{@code character/<角色id>/sounds.json}。
     *
     * <p>格式和原版 {@code assets/<命名空间>/sounds.json} 一样（只是 {@code name} 相对角色音效目录），
     * 见 {@code CharacterSounds}。放在角色目录里，角色文件夹就是<b>真正自包含</b>的：
     * 模型、贴图、动画、音效、音效定义全在一个目录下，拷走就能用。
     */
    public static final String SOUND_DEFINITION_FILE = "sounds.json";

    /**
     * 共用模型 / 贴图 / 动画目录（角色自己目录里缺的那一项回落到这里）。
     *
     * <p>本 MOD 基本上只有一套角色模型 —— 它就是<b>林薇云</b>的模型，
     * 所以这一档直接指向她自己的目录：别的角色缺项时回落到她，她自己也用这一份。
     * <pre>
     * character/linweiyun/linweiyun.geo.json
     * character/linweiyun/textures/linweiyun.png
     * character/linweiyun/linweiyun.animation.json  （可选：角色没有自己的动画文件时借它）
     * </pre>
     * <b>读哪一份由运行期决定，不由代码决定</b>：每个角色都先看
     * {@code character/<角色id>/}，那里没有的项才借这里（三项各自独立判断）。
     * 所以「自己的模型 + 共用的贴图」这种组合是正常状态，把文件放进角色目录就自动生效。
     */
    public static final String DEFAULT_ASSET_DIR = CHARACTER_ROOT + "/" + "linweiyun";
    /** 共用模型的文件名。 */
    public static final String DEFAULT_MODEL_FILE = "linweiyun.geo.json";
    /** 共用贴图的文件名。 */
    public static final String DEFAULT_TEXTURE_FILE = "linweiyun.png";
    /** 共用动画的文件名（可缺省：没有它时角色只能借到模型与贴图）。 */
    public static final String DEFAULT_ANIMATION_FILE = "linweiyun.animation.json";

    // ==================== 角色 ====================

    /** 共用模型的完整相对路径：{@code character/default/default.geo.json}。 */
    public static String defaultModelPath() {
        return DEFAULT_ASSET_DIR + "/" + DEFAULT_MODEL_FILE;
    }

    /** 共用贴图的完整相对路径：{@code character/default/textures/default.png}。 */
    public static String defaultTexturePath() {
        return DEFAULT_ASSET_DIR + "/" + ModAssetPaths.TEXTURE_DIR + "/" + DEFAULT_TEXTURE_FILE;
    }

    /** 共用动画的完整相对路径：{@code character/default/default.animation.json}。 */
    public static String defaultAnimationPath() {
        return DEFAULT_ASSET_DIR + "/" + DEFAULT_ANIMATION_FILE;
    }

    /** 共用模型的缓存键：{@code minegenshin:character/default/default}。 */
    public static Identifier defaultModel() {
        return fromModelPath(defaultModelPath());
    }

    /** 共用贴图的位置：{@code minegenshin:character/default/textures/default.png}。 */
    public static Identifier defaultTexture() {
        return fromTexturePath(defaultTexturePath());
    }

    /** 共用动画的缓存键：{@code minegenshin:character/default/default}。 */
    public static Identifier defaultAnimation() {
        return fromAnimationPath(defaultAnimationPath());
    }

    /** 某个角色的动画文件相对路径：{@code character/<角色id>/<角色id>.animation.json}。 */
    public static String characterAnimationPath(String characterId) {
        return CHARACTER_ROOT + "/" + characterId + "/" + characterId + ".animation.json";
    }

    /** 某个角色的专属模型相对路径（大多数角色用不到）：{@code character/<角色id>/<角色id>.geo.json}。 */
    public static String characterModelPath(String characterId) {
        return CHARACTER_ROOT + "/" + characterId + "/" + characterId + ".geo.json";
    }

    /** 某个角色的专属贴图相对路径（大多数角色用不到）：{@code character/<角色id>/textures/<角色id>.png}。 */
    public static String characterTexturePath(String characterId) {
        return CHARACTER_ROOT + "/" + characterId + "/" + ModAssetPaths.TEXTURE_DIR + "/" + characterId + ".png";
    }

    /** 默认模型文件名：{@code <id>.geo.json} */
    public static String defaultModelFile(String characterId) {
        return characterId + ".geo.json";
    }

    /** 默认动画文件名：{@code <id>.animation.json} */
    public static String defaultAnimationFile(String characterId) {
        return characterId + ".animation.json";
    }

    /** 默认贴图文件名：{@code <id>.png} */
    public static String defaultTextureFile(String characterId) {
        return characterId + ".png";
    }

    /**
     * 把「相对路径」转成 GeckoLib 的模型 id：剥掉 {@code .geo.json} 后缀。
     *
     * <p>例：{@code character/default/default.geo.json} → {@code minegenshin:character/default/default}
     */
    public static Identifier fromModelPath(String relativePath) {
        return id(stripSuffix(relativePath, ".geo.json"));
    }

    /** 把「相对路径」转成动画 id：剥掉 {@code .animation.json} 后缀。 */
    public static Identifier fromAnimationPath(String relativePath) {
        return id(stripAnimationSuffix(relativePath));
    }

    /** 把「相对路径」转成贴图位置（保留扩展名）。 */
    public static Identifier fromTexturePath(String relativePath) {
        return id(relativePath);
    }

    /**
     * 角色模型路径。
     *
     * <p>返回的是 <b>GeckoLib 的模型 id</b>：文件 {@code character/vesna/vesna.geo.json}
     * 对应的 id 是 {@code minegenshin:character/vesna/vesna}（GeckoLib 会把 {@code .geo.json} 后缀剥掉再进缓存）。
     */
    public static Identifier characterModel(String characterId, String fileName) {
        return id(CHARACTER_ROOT + "/" + characterId + "/" + stripSuffix(fileName, ".geo.json"));
    }

    /** 角色动画路径（同样剥掉 {@code .animation.json} 后缀）。 */
    public static Identifier characterAnimation(String characterId, String fileName) {
        return id(CHARACTER_ROOT + "/" + characterId + "/" + stripAnimationSuffix(fileName));
    }

    /** 角色贴图路径（贴图进 {@code textures/} 子目录，保留扩展名）。 */
    public static Identifier characterTexture(String characterId, String fileName) {
        return ModAssetPaths.textureIn(CHARACTER_ROOT + "/" + characterId, fileName);
    }

    // ==================== 物品 ====================

    public static String defaultItemModelFile(String itemName) {
        return itemName + ".geo.json";
    }

    public static String defaultItemAnimationFile(String itemName) {
        return itemName + ".animation.json";
    }

    public static String defaultItemTextureFile(String itemName) {
        return itemName + ".png";
    }

    public static Identifier itemModel(String itemName, String fileName) {
        return id(ITEM_ROOT + "/" + itemName + "/" + stripSuffix(fileName, ".geo.json"));
    }

    public static Identifier itemAnimation(String itemName, String fileName) {
        return id(ITEM_ROOT + "/" + itemName + "/" + stripAnimationSuffix(fileName));
    }

    public static Identifier itemTexture(String itemName, String fileName) {
        return ModAssetPaths.textureIn(ITEM_ROOT + "/" + itemName, fileName);
    }

    // ==================== 实体 ====================

    public static Identifier entityModel(String entityName, String fileName) {
        return id(ENTITY_ROOT + "/" + entityName + "/" + stripSuffix(fileName, ".geo.json"));
    }

    public static Identifier entityAnimation(String entityName, String fileName) {
        return id(ENTITY_ROOT + "/" + entityName + "/" + stripAnimationSuffix(fileName));
    }

    public static Identifier entityTexture(String entityName, String fileName) {
        return ModAssetPaths.textureIn(ENTITY_ROOT + "/" + entityName, fileName);
    }

    // ==================== 角色音效 ====================

    /** 某个角色的音效目录（不含命名空间）：{@code character/<角色id>/sounds}。 */
    public static String characterSoundsDir(String characterId) {
        return CHARACTER_ROOT + "/" + characterId + "/" + SOUNDS_DIR;
    }

    /** 某个角色的音效定义文件：{@code character/<角色id>/sounds.json}。 */
    public static String characterSoundDefinitionPath(String characterId) {
        return CHARACTER_ROOT + "/" + characterId + "/" + SOUND_DEFINITION_FILE;
    }

    /**
     * 角色音效的<b>声音位置</b>（Sound 的 location，不带扩展名）：
     * {@code minegenshin:character/<角色id>/sounds/<文件名>}。
     *
     * <p>真实文件是 {@code assets/minegenshin/character/<角色id>/sounds/<文件名>.ogg}，
     * 由 {@link #soundAssetPath(Identifier)} 还原。
     */
    public static Identifier characterSound(String characterId, String fileName) {
        return id(characterSoundsDir(characterId) + "/" + fileName.replace('\\', '/'));
    }

    /**
     * 声音位置 → <b>实际资源路径</b>。
     *
     * <p>原版固定拼 {@code sounds/<path>.ogg}（见 {@code Sound#getPath}），
     * 所以声音文件只能放 {@code sounds/} 下；本 MOD 的角色音效不走那条路 ——
     * 自定义的 {@code CharacterSounds.CharacterSound} 直接覆写 {@code getPath()}，
     * 指回角色目录，这里是那条覆写用的换算。
     */
    public static Identifier soundAssetPath(Identifier location) {
        if (location == null) {
            return null;
        }
        String path = location.getPath();
        return path.endsWith(".ogg")
                ? location
                : Identifier.fromNamespaceAndPath(location.getNamespace(), path + ".ogg");
    }

    /** 这个声音位置是不是角色目录下的（{@code character/<id>/sounds/…}）。 */
    public static boolean isCharacterSound(@Nullable Identifier location) {
        if (location == null) {
            return false;
        }
        String path = location.getPath();
        return path.startsWith(CHARACTER_ROOT + "/") && path.contains("/" + SOUNDS_DIR + "/");
    }

    /**
     * 角色音效的事件名 —— {@code character/<角色id>/sounds.json} 里的 key 怎么变成事件 id。
     *
     * <pre>
     * 角色 vesna，key = "attack_1"        → minegenshin:vesna_attack_1   （自动带角色前缀）
     * 角色 vesna，key = "vesna_attack_1"  → minegenshin:vesna_attack_1   （已经带了就不重复加）
     * key = "other_mod:custom"           → other_mod:custom             （写了命名空间就照用）
     * </pre>
     *
     * <p>自动带前缀是为了让<b>每个角色都能用短名</b>（{@code attack_1}），
     * 整个角色文件夹拷到别的角色 id 下也不会跟人撞名。
     */
    public static Identifier characterSoundEvent(String characterId, String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        if (key.indexOf(':') >= 0) {
            return Identifier.tryParse(key);
        }
        String path = key.startsWith(characterId + "_") ? key : characterId + "_" + key;
        return id(path);
    }

    /**
     * 从音效定义文件的路径反查角色 id。
     *
     * @param definitionFile 形如 {@code minegenshin:character/vesna/sounds.json}
     * @return 角色 id（{@code vesna}）；不符合布局时返回 null
     */
    @Nullable
    public static String characterIdOfSoundDefinition(@Nullable Identifier definitionFile) {
        if (definitionFile == null || !MOD_ID.equals(definitionFile.getNamespace())) {
            return null;
        }
        String path = definitionFile.getPath();
        String prefix = CHARACTER_ROOT + "/";
        String suffix = "/" + SOUND_DEFINITION_FILE;
        if (!path.startsWith(prefix) || !path.endsWith(suffix)) {
            return null;
        }
        String id = path.substring(prefix.length(), path.length() - suffix.length());
        return (id.isEmpty() || id.indexOf('/') >= 0) ? null : id;
    }

    // ==================== 图标 ====================

    /**
     * GUI 图标路径：{@code minegenshin:icon/<分类>/<名字>.png}。
     *
     * <p>背包 / 装备槽要画的是<b>图标</b>，不是物品的贴图 ——
     * 物品的 png 是给 3D 模型用的 UV 展开图，直接画出来是一片错位的色块。
     *
     * @param category 分类，如 {@code "item"}、{@code "weapon"}、{@code "artifact"}
     * @param name     名字，不带扩展名
     */
    public static Identifier icon(String category, String name) {
        return id(ICON_ROOT + "/" + category + "/" + stripSuffix(name, ".png") + ".png");
    }

    /** 物品图标：{@code icon/item/<name>.png} */
    public static Identifier itemIcon(String itemName) {
        return icon("item", itemName);
    }

    // ==================== 默认规则 ====================

    /**
     * 安装默认路径规则。
     *
     * <p>默认只接管<b>本 MOD 命名空间的 character 资源</b>：把「旧的分散布局」重定向到
     * {@code character/<角色id>/} 下。别的资源族（item / entity）默认不碰，
     * 需要时自己注册一条即可：
     *
     * <pre>
     * // 把物品也统一到 item/&lt;物品名&gt;/
     * GeoPathOverrides.forNamespace(GenshinAssets.MOD_ID, (kind, owner, original) -> {
     *     if (kind != GeoAssetKind.MODEL && kind != GeoAssetKind.ANIMATION) return null;
     *     String path = original.getPath();
     *     if (!path.startsWith("item/") &amp;&amp; !path.startsWith("geckolib/")) return null;
     *     ...
     * });
     *
     * // 只改提瓦特实体的模型
     * GeoPathOverrides.forOwnerClass(TeyvatEntityModel.class, (kind, owner, original) -&gt;
     *         kind == GeoAssetKind.MODEL ? GenshinAssets.entityModel(name, fileName) : null);
     * </pre>
     *
     * <p>由 {@code MinegenshinClient.onClientSetup} 调用。
     */
    public static void installDefaults() {
        // 只对本 MOD 命名空间生效 —— 整合包里其它 MOD 的路径完全不受影响
        GeoPathOverrides.register((kind, owner, original) -> {
            if (!MOD_ID.equals(original.getNamespace())) {
                return null;
            }

            String path = original.getPath();

            // ★ 已经在统一布局里就别动 —— 规则必须幂等。
            //   不然后面这段会把 character/default/default 改写成 character/<角色id>/default，
            //   共用模型直接找不到（这个坑踩过）。
            if (path.startsWith(CHARACTER_ROOT + "/") || path.startsWith(ITEM_ROOT + "/")
                    || path.startsWith(ENTITY_ROOT + "/")) {
                return null;
            }

            if (!(owner instanceof CharacterAssetOwner charOwner)) {
                return null;
            }
            String characterId = charOwner.assetCharacterId();
            if (characterId == null || characterId.isEmpty()) {
                return null;
            }

            String file = lastSegment(path);
            if (file == null || file.isEmpty()) {
                return null;
            }

            // 只兜「旧布局」：GeckoLib 的默认模型会把路径猜成无目录的 <名字>，这里拉进角色目录
            return switch (kind) {
                case MODEL -> characterModel(characterId, ensureSuffix(file, ".geo.json"));
                case ANIMATION -> characterAnimation(characterId, ensureSuffix(file, ".animation.json"));
                case TEXTURE -> characterTexture(characterId, ensureSuffix(file, ".png"));
            };
        });
    }

    /**
     * 能被路径规则反查「我是哪个角色」的模型。
     *
     * <p>实现放在 {@code GenshinGeoModel} 上 —— 角色模型建好之后就知道自己的角色 id 了。
     */
    public interface CharacterAssetOwner {
        @Nullable
        String assetCharacterId();
    }

    // ==================== 工具 ====================

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** 把 {@code a/b/c} 里的 {@code c} 取出来。 */
    @Nullable
    private static String lastSegment(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static String stripSuffix(String value, String suffix) {
        return (value != null && value.endsWith(suffix))
                ? value.substring(0, value.length() - suffix.length())
                : value;
    }

    /** 剥掉 {@code .animation.json} / {@code .animations.json} / {@code .json}。 */
    private static String stripAnimationSuffix(String value) {
        if (value == null) {
            return null;
        }
        for (String suffix : new String[]{".animation.json", ".animations.json", ".json"}) {
            if (value.endsWith(suffix)) {
                return value.substring(0, value.length() - suffix.length());
            }
        }
        return value;
    }

    private static String ensureSuffix(String value, String suffix) {
        return value.endsWith(suffix) ? value : value + suffix;
    }
}
