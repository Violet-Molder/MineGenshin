package com.linweiyun.genshin.core.system.combat.action.data;


import com.linweiyun.genshin.core.asset.GenshinAssets;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 角色渲染定义：模型/动画/贴图<b>相对路径</b> + 常态动画映射 + 骨骼挂点。
 *
 * <h2>目录约定</h2>
 * 三个字段都是相对 {@code assets/minegenshin/} 的路径，指向<b>角色自己目录里的位置</b>：
 * <pre>
 * character/&lt;角色id&gt;/&lt;角色id&gt;.geo.json          模型
 * character/&lt;角色id&gt;/textures/&lt;角色id&gt;.png       贴图
 * character/&lt;角色id&gt;/&lt;角色id&gt;.animation.json     动画
 * </pre>
 * 这三个位置里<b>没有的项由读的那一侧回落 {@code character/default/}</b>
 * （三项各自独立，见 {@code AssetFallback}）—— 所以这里写的永远是
 * 「这个角色自己的目录」，而不是「实际会读到的那一份」。
 * 用 {@link #character(String, Map, float, CharacterBoneMount...)} 就是这套标准位置；
 * 要整套换目录仍然用显式路径构造。
 *
 * <p>想换目录不要改这里 —— 用 {@link com.linweiyun.genshin.core.asset.GeoPathOverrides} 注册规则。
 *
 * <p>双端安全（只存字符串）。每个角色在自己的包里定义，通过 {@link CharacterRenderRepository} 注册。
 */
public final class CharacterRenderData {

    private final String id;
    private final String modelPath;
    private final String texturePath;
    private final String animationPath;
    /** 主动画文件之外的额外动画文件（第一人称动画、动作包……）。 */
    private final List<String> extraAnimationPaths;
    private final Map<String, String> animMapping;
    private final float bodyScale;
    private final List<CharacterBoneMount> boneMounts;
    private final List<String> translucentBones;
    /** 这套模型的作者名（署名用）；null = 不署名。 */
    private final String modelAuthor;
    /** 作者的页面（点击署名时打开）；null = 只有名字、不可点。 */
    private final String modelAuthorUrl;

    /**
     * 标准写法：三项都指向<b>角色自己的目录</b>，缺的那几项由读的一侧回落
     * {@code character/default/}。
     *
     * <pre>
     * character/&lt;角色id&gt;/&lt;角色id&gt;.geo.json        模型
     * character/&lt;角色id&gt;/textures/&lt;角色id&gt;.png     贴图
     * character/&lt;角色id&gt;/&lt;角色id&gt;.animation.json   动画
     * </pre>
     *
     * <p>所以「还没画自己的模型」「只做了模型、贴图以后再补」都不需要改代码：
     * 文件放进角色目录就自动生效，没放就借共用的那一份。
     */
    public static CharacterRenderData character(String id, Map<String, String> animMapping,
                                                float bodyScale, CharacterBoneMount... boneMounts) {
        return new CharacterRenderData(id,
                GenshinAssets.characterModelPath(id),
                GenshinAssets.characterTexturePath(id),
                GenshinAssets.characterAnimationPath(id),
                animMapping, bodyScale, boneMounts);
    }

    public CharacterRenderData(String id, String modelPath, String texturePath,
                               String animationPath, Map<String, String> animMapping,
                               float bodyScale) {
        this(id, modelPath, texturePath, animationPath, animMapping, bodyScale, List.of());
    }

    /**
     * @param boneMounts 骨骼挂点；空表示这个角色不做任何骨骼替换。
     *                   一个角色可以挂多根骨骼（剑身、剑鞘、背后的弓……各自独立取内容）。
     */
    public CharacterRenderData(String id, String modelPath, String texturePath,
                               String animationPath, Map<String, String> animMapping,
                               float bodyScale, List<CharacterBoneMount> boneMounts) {
        this(id, modelPath, texturePath, animationPath, animMapping, bodyScale,
                boneMounts == null ? new CharacterBoneMount[0] : boneMounts.toArray(new CharacterBoneMount[0]));
    }

    /** 便捷写法：直接列挂点，不用自己包一层 List。 */
    public CharacterRenderData(String id, String modelPath, String texturePath,
                               String animationPath, Map<String, String> animMapping,
                               float bodyScale, CharacterBoneMount... boneMounts) {
        this(id, modelPath, texturePath, animationPath, List.of(), animMapping, bodyScale,
                boneMounts == null ? new CharacterBoneMount[0] : boneMounts);
    }

    /** 完整构造：带额外动画文件。 */
    public CharacterRenderData(String id, String modelPath, String texturePath,
                               String animationPath, List<String> extraAnimationPaths,
                               Map<String, String> animMapping,
                               float bodyScale, CharacterBoneMount... boneMounts) {
        this(id, modelPath, texturePath, animationPath, extraAnimationPaths, animMapping, bodyScale,
                List.of(), boneMounts);
    }

    /**
     * 最全的构造：额外动画文件 + 半透明骨骼名单。其余构造都汇到这里。
     *
     * @param translucentBones 见 {@link #translucentBones()}；空表示不需要半透明管线。
     */
    private CharacterRenderData(String id, String modelPath, String texturePath,
                                String animationPath, List<String> extraAnimationPaths,
                                Map<String, String> animMapping, float bodyScale,
                                List<String> translucentBones, CharacterBoneMount... boneMounts) {
        this(id, modelPath, texturePath, animationPath, extraAnimationPaths, animMapping, bodyScale,
                translucentBones, boneMounts, null, null);
    }

    /**
     * 最全的构造（含作者署名）。所有其它构造最终都汇到这里。
     *
     * <p>作者信息单独开两个字段而不是塞进 {@code id} / 路径：它是<b>美术的出处</b>，
     * 和「模型放在哪、怎么播」无关，页面只在需要署名的地方读它
     * （见 {@code ShenheConfigUI} 预览下方那一行）。
     */
    private CharacterRenderData(String id, String modelPath, String texturePath,
                                String animationPath, List<String> extraAnimationPaths,
                                Map<String, String> animMapping, float bodyScale,
                                List<String> translucentBones, CharacterBoneMount[] boneMounts,
                                String modelAuthor, String modelAuthorUrl) {
        this.id = id;
        this.modelPath = modelPath;
        this.texturePath = texturePath;
        this.animationPath = animationPath;
        this.extraAnimationPaths = extraAnimationPaths == null ? List.of() : List.copyOf(extraAnimationPaths);
        this.animMapping = animMapping == null ? Collections.emptyMap() : animMapping;
        this.bodyScale = bodyScale;
        this.translucentBones = translucentBones == null ? List.of() : List.copyOf(translucentBones);
        this.boneMounts = boneMounts == null
                ? List.of()
                : java.util.Arrays.stream(boneMounts).filter(m -> m != null && m.isValid()).toList();
        this.modelAuthor = blankToNull(modelAuthor);
        this.modelAuthorUrl = blankToNull(modelAuthorUrl);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public String id() { return id; }
    public String modelPath() { return modelPath; }
    public String texturePath() { return texturePath; }
    public String animationPath() { return animationPath; }

    /** 主文件之外的额外动画文件；空表示动画都在主文件里。 */
    public List<String> extraAnimationPaths() { return extraAnimationPaths; }

    /** 追加一个额外动画文件（第一人称、动作包……）。返回 this 方便链式写。 */
    public CharacterRenderData withAnimationFile(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return this;
        }
        List<String> merged = new java.util.ArrayList<>(extraAnimationPaths);
        merged.add(relativePath);
        return new CharacterRenderData(id, modelPath, texturePath, animationPath, merged,
                animMapping, bodyScale, translucentBones,
                boneMounts.toArray(new CharacterBoneMount[0]), modelAuthor, modelAuthorUrl);
    }

    /** 全部动画文件的相对路径（主 + 额外）。 */
    public List<String> allAnimationPaths() {
        List<String> all = new java.util.ArrayList<>(extraAnimationPaths.size() + 1);
        all.add(animationPath);
        all.addAll(extraAnimationPaths);
        return all;
    }

    /** 骨骼挂点列表；空表示不做骨骼替换。 */
    public List<CharacterBoneMount> boneMounts() { return boneMounts; }

    /**
     * 要用<b>半透明管线</b>渲染的骨骼名；空表示这个角色没有这种部件。
     *
     * <p>给「贴图里带半透明像素」的方块用（YSM 转过来的发光屏幕内屏就是典型）：
     * GeckoLib 默认走 {@code entityCutout}，α 只当阈值用，半透明像素会被画成实心。
     * 名单里的骨骼会在主渲染趟被藏掉、再用 {@code entityTranslucent} 重画一遍，
     * 见 {@code client/render/character/TranslucentBoneGeoLayer}。
     */
    public List<String> translucentBones() { return translucentBones; }

    /** 声明要用半透明管线渲染的骨骼；返回 this 方便链式写。 */
    public CharacterRenderData withTranslucentBones(String... boneNames) {
        if (boneNames == null || boneNames.length == 0) {
            return this;
        }
        return new CharacterRenderData(id, modelPath, texturePath, animationPath, extraAnimationPaths,
                animMapping, bodyScale, java.util.Arrays.asList(boneNames),
                boneMounts.toArray(new CharacterBoneMount[0]), modelAuthor, modelAuthorUrl);
    }

    /**
     * 给这套美术署名。返回 this 方便链式写（写在 {@code CharacterRenderData.character(...)} 后面）。
     *
     * @param author 作者名，会原样显示在配置页预览下方；null / 空白 = 取消署名
     * @param url    作者主页；null / 空白 = 只显示名字、点击无效
     */
    public CharacterRenderData withModelAuthor(String author, String url) {
        return new CharacterRenderData(id, modelPath, texturePath, animationPath, extraAnimationPaths,
                animMapping, bodyScale, translucentBones,
                boneMounts.toArray(new CharacterBoneMount[0]), author, url);
    }

    /** 这套模型的作者名；没有署名时 null。 */
    public String modelAuthor() { return modelAuthor; }

    /** 作者主页；没给（或只有名字）时 null。 */
    public String modelAuthorUrl() { return modelAuthorUrl; }

    /** 模型 id：剥掉 {@code .geo.json} 后缀。 */
    public Identifier modelIdentifier() {
        return GenshinAssets.fromModelPath(modelPath);
    }

    /** 贴图位置（保留扩展名）。 */
    public Identifier textureIdentifier() {
        return GenshinAssets.fromTexturePath(texturePath);
    }

    /** 动画 id：剥掉 {@code .animation.json} 后缀。 */
    public Identifier animationIdentifier() {
        return GenshinAssets.fromAnimationPath(animationPath);
    }

    public Map<String, String> animMapping() { return animMapping; }
    public float bodyScale() { return bodyScale; }

    public boolean isValid() {
        return id != null && !id.isEmpty()
                && modelPath != null && texturePath != null && animationPath != null;
    }

    // ==================== 默认动画映射 ====================

    public static Map<String, String> defaultAnimMapping() {
        return Map.ofEntries(
                Map.entry("idle", "idle"),
                Map.entry("walk", "walk"),
                Map.entry("run", "run"),
                Map.entry("walk_back", "walk_back"),
                Map.entry("crouch", "crouch"),
                Map.entry("crouch_walk", "crouch_walk"),
                Map.entry("jump", "jump"),
                Map.entry("jump_down", "jump_down"),
                Map.entry("air_idle", "idle"),
                Map.entry("air_move", "walk"),
                Map.entry("air_sprint", "run"),
                Map.entry("swim", "swim"),
                Map.entry("climb", "climb"),
                Map.entry("sleep", "sleep")
        );
    }
}
