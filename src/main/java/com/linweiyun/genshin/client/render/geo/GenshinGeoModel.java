package com.linweiyun.genshin.client.render.geo;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.cache.animation.Animation;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.model.GeoModel;
import com.linweiyun.genshin.core.asset.GeoAssetKind;
import com.linweiyun.genshin.core.asset.GeoPathOverrides;
import com.linweiyun.genshin.core.asset.GenshinAssets;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 本 MOD 所有 GeckoLib 模型的基类 —— 资源路径与缓存的统一入口。
 *
 * <h2>它做了三件事</h2>
 * <ol>
 *   <li><b>路径过规则链</b>：三个路径方法都把结果交给
 *       {@link GeoPathOverrides}，需要临时改目录时注册一条规则即可，
 *       不用动任何模型类（见 {@link GenshinAssets#installDefaults()}）。</li>
 *   <li><b>先查自己的缓存</b>：覆盖 {@link #getBakedModel(Identifier)} 与
 *       {@link #getBakedAnimation(GeoAnimatable, String)}，先问 {@link GenshinGeoCache}，
 *       没有再回落到 GeckoLib 的缓存。这样 {@code assets/minegenshin/character/...}
 *       这种 GeckoLib 扫不到的目录也能用。</li>
 *   <li><b>记住自己属于谁</b>：实现 {@link GenshinAssets.CharacterAssetOwner}，
 *       默认路径规则靠它把旧布局重定向到 {@code character/<角色id>/}。</li>
 * </ol>
 *
 * <h2>子类要做什么</h2>
 * 只实现 {@link #getAnimationResource(GeoAnimatable)}（返回动画文件的 Identifier）即可，
 * 模型与贴图默认按 {@code characterId} 推出来。也可以直接覆盖三个路径方法写死。
 */
public abstract class GenshinGeoModel<T extends GeoAnimatable> extends GeoModel<T>
        implements GenshinAssets.CharacterAssetOwner {

    /** 默认路径（未命中任何规则时使用）。 */
    private Identifier defaultModel;
    private Identifier defaultTexture;
    private Identifier defaultAnimation;

    /**
     * 角色数据里<b>声明</b>的那三项（读取时的第二候选）。
     *
     * <p>只有角色数据声明了「不在自己目录里」的路径时才用得上；声明的是自己目录那条时
     * 它与主选相同，{@link AssetFallback} 会自动跳过它。
     */
    private Identifier declaredModel;
    private Identifier declaredTexture;
    private Identifier declaredAnimation;

    /**
     * <b>共用目录</b>里的三项（读取时的最后一档，即 {@code character/default/} 那一套）。
     *
     * <p>角色用它们是「先看自己目录、没有才借共用的那一份」，三项各自独立判断，
     * 判据在 {@link AssetFallback}。不设置 = 不借（行为与从前一致）。
     */
    private Identifier sharedModel;
    private Identifier sharedTexture;
    private Identifier sharedAnimation;

    /** 这个模型属于哪个角色；为 null 时默认规则不接管。 */
    @Nullable
    private String characterId;

    /**
     * 额外的动画文件（回退文件）。
     *
     * <p>动画不要求全塞一个文件里 —— 第一个查不到就顺着这张表往下找。
     * 典型用法：三件套一个文件、第一人称动画另一个文件、某个动作包再来一个。
     */
    private Identifier[] animationFallbacks = new Identifier[0];

    protected GenshinGeoModel() {
    }

    protected GenshinGeoModel(@Nullable String characterId) {
        setCharacterId(characterId);
    }

    // ==================== 配置 ====================

    /** 换一套默认路径（例如切换角色时）。传 null 的项保持不变。 */
    public void setPaths(@Nullable Identifier model, @Nullable Identifier texture,
                         @Nullable Identifier animation) {
        if (model != null) this.defaultModel = model;
        if (texture != null) this.defaultTexture = texture;
        if (animation != null) this.defaultAnimation = animation;
    }

    /** 写「角色数据里声明的那条」（第二候选）。传 null 的项保持不变。 */
    public void setDeclaredPaths(@Nullable Identifier model, @Nullable Identifier texture,
                                 @Nullable Identifier animation) {
        if (model != null) this.declaredModel = model;
        if (texture != null) this.declaredTexture = texture;
        if (animation != null) this.declaredAnimation = animation;
    }

    /**
     * 写「共用目录那一套」（最后一档）。传 null 的项保持不变。
     *
     * <p>角色模型按 {@link GenshinAssets#defaultModel()} / {@link GenshinAssets#defaultTexture()} /
     * {@link GenshinAssets#defaultAnimation()} 挂，也就是 {@code character/default/} 里的三项。
     */
    public void setSharedPaths(@Nullable Identifier model, @Nullable Identifier texture,
                               @Nullable Identifier animation) {
        if (model != null) this.sharedModel = model;
        if (texture != null) this.sharedTexture = texture;
        if (animation != null) this.sharedAnimation = animation;
    }

    /** 换角色：同时按新角色重算默认路径。 */
    public void setCharacterId(@Nullable String characterId) {
        this.characterId = characterId;
        if (characterId != null && !characterId.isEmpty()) {
            setPaths(
                    GenshinAssets.characterModel(characterId, GenshinAssets.defaultModelFile(characterId)),
                    GenshinAssets.characterTexture(characterId, GenshinAssets.defaultTextureFile(characterId)),
                    GenshinAssets.characterAnimation(characterId, GenshinAssets.defaultAnimationFile(characterId)));
        }
    }

    /** 设置额外的动画文件（相对 {@code assets/minegenshin/} 的路径）。 */
    public void setAnimationFallbackPaths(@Nullable List<String> relativePaths) {
        if (relativePaths == null || relativePaths.isEmpty()) {
            this.animationFallbacks = new Identifier[0];
            return;
        }
        this.animationFallbacks = relativePaths.stream()
                .filter(p -> p != null && !p.isEmpty())
                .map(GenshinAssets::fromAnimationPath)
                .toArray(Identifier[]::new);
    }

    /** 主文件之外的动画文件。 */
    @Override
    public Identifier[] getAnimationResourceFallbacks(T animatable) {
        return animationFallbacks;
    }

    /** 全部动画文件（主 + 回退），给缓存与存在性校验用。 */
    public Identifier[] allAnimationFiles(T animatable) {
        Identifier primary = getAnimationResource(animatable);
        Identifier[] fallbacks = getAnimationResourceFallbacks(animatable);
        Identifier[] all = new Identifier[fallbacks.length + 1];
        all[0] = primary;
        System.arraycopy(fallbacks, 0, all, 1, fallbacks.length);
        return all;
    }

    @Nullable
    @Override
    public String assetCharacterId() {
        return characterId;
    }

    // ==================== 路径 ====================

    @Nullable
    public Identifier defaultModelResource() {
        return defaultModel;
    }

    @Nullable
    public Identifier defaultTextureResource() {
        return defaultTexture;
    }

    @Nullable
    public Identifier defaultAnimationResource() {
        return defaultAnimation;
    }

    @Override
    public Identifier getModelResource(com.geckolib.renderer.base.GeoRenderState renderState) {
        return AssetFallback.model(rewrite(GeoAssetKind.MODEL, defaultModel),
                rewrite(GeoAssetKind.MODEL, declaredModel),
                rewrite(GeoAssetKind.MODEL, sharedModel));
    }

    @Override
    public Identifier getTextureResource(com.geckolib.renderer.base.GeoRenderState renderState) {
        return AssetFallback.texture(rewrite(GeoAssetKind.TEXTURE, defaultTexture),
                rewrite(GeoAssetKind.TEXTURE, declaredTexture),
                rewrite(GeoAssetKind.TEXTURE, sharedTexture));
    }

    @Override
    public Identifier getAnimationResource(T animatable) {
        return AssetFallback.animation(rewrite(GeoAssetKind.ANIMATION, defaultAnimation),
                rewrite(GeoAssetKind.ANIMATION, declaredAnimation),
                rewrite(GeoAssetKind.ANIMATION, sharedAnimation));
    }

    /** 把默认路径过一遍规则链；链上没人接管就原样返回。 */
    @Nullable
    private Identifier rewrite(GeoAssetKind kind, @Nullable Identifier original) {
        if (original == null) {
            return null;
        }
        return GeoPathOverrides.resolve(kind, this, original);
    }

    // ==================== 缓存 ====================

    /**
     * 先查本 MOD 自己的缓存，再回落 GeckoLib 的。
     *
     * <p>{@code RenderPassInfo.create} 与 {@code GeoRenderLayer#getDefaultBakedModel}
     * 都只走这个入口，所以覆盖它就等于覆盖了全部模型查找。
     */
    @Override
    public BakedGeoModel getBakedModel(Identifier location) {
        BakedGeoModel own = GenshinGeoCache.model(location);
        return own != null ? own : super.getBakedModel(location);
    }

    /**
     * 先查本 MOD 自己的动画缓存（主文件 + 所有回退文件），再回落 GeckoLib 的。
     *
     * <p>{@code AnimationProcessor.getOrCreateAnimation} 只走这个入口。
     */
    @Nullable
    @Override
    public Animation getBakedAnimation(T animatable, String name) throws RuntimeException {
        Animation own = GenshinGeoCache.animation(
                getAnimationResource(animatable), getAnimationResourceFallbacks(animatable), name);
        return own != null ? own : super.getBakedAnimation(animatable, name);
    }
}
