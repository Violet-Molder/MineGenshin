package com.linweiyun.genshin.client.render.geo;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.cache.animation.Animation;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.linweiyun.genshin.client.render.geo.AssetGeoCache.DirFiles;
import com.linweiyun.genshin.asset.AssetCategory;
import com.linweiyun.genshin.asset.AssetSet;
import com.linweiyun.genshin.asset.ModAssetPaths;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * 「类别 + id」驱动的 GeckoLib 模型基类。
 *
 * <p>用法只有一行：
 * <pre>
 * new CategoryGeoModel&lt;&gt;(AssetCategory.ENTITY, "test1")
 * </pre>
 * 之后模型 / 贴图 / 动画三个路径全部按统一布局推出来，代码里不再出现任何路径字符串。
 *
 * <h2>三级缓存查找</h2>
 * <ol>
 *   <li>{@link AssetGeoCache#files(String)}：按<b>目录</b>拿到该 id 目录下实际存在的
 *       模型 / 动画 / 贴图（不看文件名），这是主路径；</li>
 *   <li>{@link GenshinGeoCache}：角色那套按 key 的共享缓存；</li>
 *   <li>GeckoLib 自己的缓存：{@code geckolib/models}、{@code geckolib/animations}
 *       （给「临时把文件丢进 GeckoLib 原生目录」的应急手段留路）。</li>
 * </ol>
 *
 * <h2>逐项回退</h2>
 * {@link #withFallback} 可以挂一个「借用目录」：本目录缺哪个文件就补哪个，
 * 三种资源<b>各自独立</b>回退。所以 test2 不写贴图时会自动借用 test1 的贴图，
 * 而它自己的模型仍然生效 —— 资源一旦补齐就自动切换，不用改代码。
 *
 * <p>和 {@link GenshinGeoModel} 的区别：那个是「角色专用」（默认路径带 {@code character/default/}
 * 的共用模型逻辑、还要参与 {@code GeoPathOverrides} 规则链）；这个只做「类别 + id」，
 * 四种资源一视同仁，也不注册任何全局规则。
 */
public class CategoryGeoModel<T extends GeoAnimatable> extends GeoModel<T> {
    private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);
    private static final Set<String> WARNED = Collections.synchronizedSet(new HashSet<>());
    private final AssetCategory category;
    private final String id;
    private final AssetSet assets;
    @Nullable
    private AssetSet fallback;

    public CategoryGeoModel(AssetCategory category, String id) {
        this.category = category;
        this.id = id;
        this.assets = AssetSet.of(category, id);
    }

    /** 挂一个「借用目录」：本目录缺哪种资源就补哪种（模型 / 动画 / 贴图各自独立）。 */
    public CategoryGeoModel<T> withFallback(AssetCategory fallbackCategory, String fallbackId) {
        this.fallback = AssetSet.of(fallbackCategory, fallbackId);
        return this;
    }

    public AssetCategory category() {
        return this.category;
    }

    public String assetId() {
        return this.id;
    }

    public AssetSet assets() {
        return this.assets;
    }

    /** 合并本目录与借用目录：本目录有的用本目录的，缺的才用借来的。 */
    private DirFiles files() {
        DirFiles own = AssetGeoCache.files(this.assets.dir());
        if (this.fallback == null) {
            return own;
        }

        if (own.model() != null && own.animation() != null && own.texture() != null) {
            return own;
        }

        DirFiles spare = AssetGeoCache.files(this.fallback.dir());
        return new DirFiles(
            own.model() != null ? own.model() : spare.model(),
            own.animation() != null ? own.animation() : spare.animation(),
            own.texture() != null ? own.texture() : spare.texture()
        );
    }

    public Identifier getModelResource(GeoRenderState renderState) {
        Identifier found = this.files().model();
        if (found != null) {
            return ModAssetPaths.modelKeyOf(found);
        }

        this.warnMissingOnce("模型", this.assets.modelCandidates());
        return this.assets.modelKey();
    }

    public Identifier getTextureResource(GeoRenderState renderState) {
        Identifier found = this.files().texture();
        if (found != null) {
            return found;
        }

        this.warnMissingOnce("贴图", this.assets.textureCandidates());
        return this.assets.textureFile();
    }

    public Identifier getAnimationResource(T animatable) {
        Identifier found = this.files().animation();
        if (found != null) {
            return ModAssetPaths.animationKeyOf(found);
        }

        this.warnMissingOnce("动画", this.assets.animationCandidates());
        return this.assets.animationKey();
    }

    public BakedGeoModel getBakedModel(Identifier location) {
        BakedGeoModel own = AssetGeoCache.model(location);
        if (own != null) {
            return own;
        }

        BakedGeoModel shared = GenshinGeoCache.model(location);
        return shared != null ? shared : super.getBakedModel(location);
    }

    @Nullable
    public Animation getBakedAnimation(T animatable, String name) throws RuntimeException {
        Identifier file = this.getAnimationResource(animatable);
        Animation own = AssetGeoCache.animation(file, name);
        if (own != null) {
            return own;
        }

        Animation shared = GenshinGeoCache.animation(file, this.getAnimationResourceFallbacks(animatable), name);
        if (shared != null) {
            return shared;
        }

        this.warnAnimationNameOnce(file, name);
        return super.getBakedAnimation(animatable, name);
    }

    private void warnAnimationNameOnce(Identifier file, String name) {
        if (WARNED.add("anim:" + this.assets.dir() + "/" + name)) {
            Identifier indexed = this.files().animation();
            Set<String> available = AssetGeoCache.animationNames(file);
            LOGGER.error(
                "[CategoryGeoModel] 动画 '{}' 解析失败。目录 {}：索引到的动画文件={}，请求的键={}，该键下可用的动画名={}  ‖ 动画文件 MISSING → 文件没被扫到（看 [AssetGeoCache] 目录清单与烘培失败日志）；可用动画名为空 → 文件在但 GeckoLib 没烘出来；列表里没有 '{}' → 改 json 里的动画名，或改 TestAction 的映射",
                new Object[]{name, this.assets.dir(), indexed == null ? "MISSING" : indexed, file, available.isEmpty() ? "（空）" : available, name}
            );
        }
    }

    private void warnMissingOnce(String what, Identifier[] expected) {
        if (WARNED.add(this.assets.dir() + "/" + what)) {
            Set<String> present = AssetGeoCache.knownDirs().contains(this.assets.dir()) ? this.presentFiles() : Set.of();
            LOGGER.warn(
                "[CategoryGeoModel] {}/{} 缺少{}。期望其一：{}；该目录下现有：{}",
                new Object[]{
                    this.category.folder(),
                    this.id,
                    what,
                    Arrays.stream(expected).map(Identifier::toString).toList(),
                    present.isEmpty() ? "（整个目录没被索引到，检查文件是否在 assets/minegenshin/" + this.assets.dir() + "/ 下）" : present
                }
            );
        }
    }

    private Set<String> presentFiles() {
        Set<String> names = new TreeSet<>();
        DirFiles files = this.files();
        if (files.model() != null) {
            names.add(files.model().getPath());
        }

        if (files.animation() != null) {
            names.add(files.animation().getPath());
        }

        if (files.texture() != null) {
            names.add(files.texture().getPath());
        }

        return names;
    }
}
