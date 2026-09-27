package com.linweiyun.genshin.client.render.geo;

import com.geckolib.cache.GeckoLibResources;
import com.geckolib.cache.animation.Animation;
import com.geckolib.cache.animation.BakedAnimations;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.loading.math.MathParser;
import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.asset.ModAssetPaths;
import com.linweiyun.genshin.core.asset.GenshinAssets;
import com.linweiyun.genshin.core.asset.pack.GeoPackSource;
import com.linweiyun.genshin.core.asset.pack.GenshinGsonLoader;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 本 MOD 自己的 GeckoLib 资源缓存（模型 + 动画）。
 *
 * <h2>为什么不用 GeckoLib 的缓存</h2>
 * GeckoLib 5.5.6 的扫描根是<b>硬编码</b>的 {@code assets/<ns>/geckolib/{models,animations}}，
 * 没有公开接口能加根目录。本 MOD 想让角色资源集中在
 * {@code assets/minegenshin/character/<角色id>/}，所以自己扫、自己烘培、自己存。
 *
 * <h2>为什么不用 Mixin 改 GeckoLib</h2>
 * 所有模型/动画查找都收敛在 {@code GeoModel.getBakedModel(Identifier)} 与
 * {@code GeoModel.getBakedAnimation(T, String)} 这两个 <b>public 非 final</b> 方法上
 * （`RenderPassInfo.create` 与 `AnimationProcessor` 都走它们），
 * 所以在自己的 {@link GenshinGeoModel} 里覆盖这两个方法就够了 ——
 * 不碰 GeckoLib 任何内部状态，<b>整合包里其它 MOD 完全不受影响</b>。
 *
 * <h2>烘培用的是 GeckoLib 的公开 API</h2>
 * {@link GeckoLibGsonLoader} 与 {@link MathParser} 都是 public，
 * 所以烘培结果与 GeckoLib 自己烘的一模一样，不存在两套解析逻辑。
 *
 * <h2>扫描范围</h2>
 * 只扫本 MOD 命名空间下的 {@code character/}、{@item}/、{@code entity/} 三个根，
 * 且只认 {@code .geo.json} 与 {@code .animation.json} 两种后缀 ——
 * 所以 {@code assets/minegenshin/items/xxx.json}（物品定义）这类同前缀文件不会被误收。
 *
 * <h2>整包</h2>
 * 仓库里没有逐文件的 {@code .geo.json} / {@code .animation.json}，全部收在一个
 * {@code .minegenshin} 整包里（见 {@link com.linweiyun.genshin.core.asset.pack.GeoPack}）。
 * 扫描时把「磁盘上有什么」与「包里有什么」拼成同一张候选表：包内条目按同一套路径 / 后缀规则
 * 参与扫描，读取走 {@link GenshinGsonLoader#readPacked}。找得到整包读取器的环境才读得出包，
 * 找不到的环境包内为空，模型 / 动画自然显示不出来。
 * 两边同名时用哪一份由 {@link GeoPackSource#usePacked} 裁定：<b>包内条目优先</b>
 * （磁盘上同名位置另有一份随包副本，两份不等价），{@code local/} 那一份例外。
 *
 * <h2>两种资源</h2>
 * 对象目录里还有一个免打包子目录 {@code local/}（见 {@code ModAssetPaths.LOCAL_DIR}）：
 * 里面的 {@code .geo.json} / {@code .animation.json} 直接放在仓库里、原样直读、永不进整包。
 * 它不算资源身份（键会去掉 {@code local} 这一层），所以
 * {@code character/vesna/local/vesna.geo.json} 就是 {@code character/vesna/vesna} 这个模型；
 * 与包内同名时以它为准（先处理包与对象根、后处理 {@code local/}，后面的覆盖前面的）。
 */
public final class GenshinGeoCache implements PreparableReloadListener {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);

    /**
     * GeckoLib 的默认烘培器（Gson）的「能读整包」版。
     *
     * <p>声明成具体类型：包内条目走 {@code readPacked} 这条入口，读取器接口里没有这个方法。
     */
    private static final GenshinGsonLoader LOADER = new GenshinGsonLoader();

    /**
     * 我们扫的根目录。
     *
     * <p>前三行是「统一布局」，最后两行是 <b>GeckoLib 原生根</b>。
     *
     * <p>为什么两套都扫：{@code geckolib/models/character/default/default.geo.json} 与
     * {@code character/default/default.geo.json} 剥掉前后缀后是<b>同一个键</b>
     * （{@code character/default/default}），所以文件放哪边都行、都能查到。
     * 这也让本 MOD 的缓存和 GeckoLib 自己的缓存天然对齐 —— 万一我们的扫描出了问题，
     * 把文件放到 {@code geckolib/} 下就能绕过，不需要改代码。
     *
     * <p><b>不要加尾斜杠</b>：{@code ResourceManager.listResources(String, ...)} 的第一参是目录，
     * 传 {@code "character/"} 会一个文件都找不到。目录层级靠后面的
     * {@code path.startsWith(root + "/")} 再确认一次。
     */
    private static final String[] ROOTS = {
            GenshinAssets.CHARACTER_ROOT,
            GenshinAssets.ITEM_ROOT,
            GenshinAssets.ENTITY_ROOT,
            "geckolib/models",
            "geckolib/animations",
    };

    private static final String MODEL_SUFFIX = ".geo.json";
    private static final String ANIMATION_SUFFIX = ".animation.json";

    /** 烘培好的模型：键是剥掉前缀后缀的资源路径（与 GeckoLib 的缓存键一致）。 */
    private static volatile Map<Identifier, BakedGeoModel> models = Map.of();

    /** 烘培好的动画文件：键同上，值是整个文件。 */
    private static volatile Map<Identifier, BakedAnimations> animations = Map.of();

    /**
     * 重载监听器有没有跑过。
     *
     * <p>没跑过时查询会触发一次同步兜底扫描 —— 渲染不能因为监听器注册时机的问题就整片空白。
     */
    private static volatile boolean reloaded = false;

    /** 由 {@code AddClientReloadListenersEvent} 注册，所以必须是 public。 */
    public GenshinGeoCache() {
    }

    // ==================== 查询（被 GenshinGeoModel 调用） ====================

    /** @return 我们自己烘的模型；没有就返回 null（调用方回落到 GeckoLib 的缓存） */
    @Nullable
    public static BakedGeoModel model(Identifier location) {
        if (location == null) {
            return null;
        }
        ensureLoaded();
        return models.get(location);
    }

    /** 从我们自己的动画文件里取一条动画。 */
    @Nullable
    public static Animation animation(Identifier animationFile, String name) {
        BakedAnimations baked = animationFile(animationFile);
        return baked == null || name == null ? null : baked.getAnimation(name);
    }

    /**
     * 多文件查找：先主文件，再依次问每个回退文件。
     *
     * <p>对应 GeckoLib 自己的 {@code BakedAnimationCache.getAnimation(file, fallbacks, name)}，
     * 只是查的是我们自己的缓存。动画按「角色三件套 / 第一人称 / 动作包」分成多个文件时靠这个兜住。
     *
     * @param fallbacks 回退文件；传 null 或空数组表示只查主文件
     */
    @Nullable
    public static Animation animation(Identifier animationFile, @Nullable Identifier[] fallbacks, String name) {
        Animation found = animation(animationFile, name);
        if (found != null || fallbacks == null) {
            return found;
        }
        for (Identifier fallback : fallbacks) {
            found = animation(fallback, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 从一组动画文件里把所有动画名收集起来（供 {@code AnimationAvailability} 校验用）。 */
    public static void collectAnimationNames(Identifier primary, @Nullable Identifier[] fallbacks,
                                             java.util.Set<String> out) {
        collectFrom(primary, out);
        if (fallbacks != null) {
            for (Identifier fallback : fallbacks) {
                collectFrom(fallback, out);
            }
        }
    }

    private static void collectFrom(Identifier file, java.util.Set<String> out) {
        BakedAnimations baked = animationFile(file);
        if (baked != null) {
            out.addAll(baked.animations().keySet());
        }
    }

    /** 整个动画文件；没有就返回 null。 */
    @Nullable
    public static BakedAnimations animationFile(Identifier animationFile) {
        if (animationFile == null) {
            return null;
        }
        ensureLoaded();
        return animations.get(animationFile);
    }

    /**
     * 兜底：重载监听器还没跑过（注册时机不对、或压根没注册成功）就自己扫一次。
     *
     * <p>只做一次，之后靠正常重载流程。
     */
    private static void ensureLoaded() {
        if (reloaded) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        ResourceManager resourceManager = minecraft.getResourceManager();
        if (resourceManager == null) {
            return;
        }
        synchronized (GenshinGeoCache.class) {
            if (reloaded) {
                return;
            }
            LOGGER.warn("[GenshinGeoCache] 重载监听器未生效，改为首次查询时同步扫描");
            Scanned scanned = new GenshinGeoCache().scan(resourceManager);
            new GenshinGeoCache().apply(scanned);
        }
    }

    /** 调试用。 */
    public static int modelCount() {
        return models.size();
    }

    /** 调试用。 */
    public static int animationFileCount() {
        return animations.size();
    }

    // ==================== 资源重载 ====================

    @Override
    public CompletableFuture<Void> reload(SharedState sharedState, Executor prepExecutor,
                                          PreparationBarrier barrier, Executor applyExecutor) {
        ResourceManager resourceManager = sharedState.resourceManager();

        return CompletableFuture
                .supplyAsync(() -> scan(resourceManager), prepExecutor)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(this::apply, applyExecutor);
    }

    private record Scanned(Map<Identifier, BakedGeoModel> models,
                           Map<Identifier, BakedAnimations> animations) {
    }

    private Scanned scan(ResourceManager resourceManager) {
        Map<Identifier, BakedGeoModel> foundModels = new HashMap<>(models);
        Map<Identifier, BakedAnimations> foundAnimations = new HashMap<>(animations);
        MathParser mathParser = MathParser.createWithDeduplication();

        // 整包里的条目：同名时用哪一份由 GeoPackSource.usePacked 裁定（整包优先，local/ 例外）
        Map<Identifier, byte[]> packedEntries = GeoPackSource.entries(resourceManager);

        for (String root : ROOTS) {
            Map<Identifier, Resource> resources;
            try {
                resources = resourceManager.listResources(root, id -> id.getNamespace().equals(Minegenshin.MOD_ID));
            } catch (Exception e) {
                LOGGER.warn("[GenshinGeoCache] 扫描根 '{}' 失败：{}", root, e.toString());
                continue;
            }

            Set<Identifier> candidates = new LinkedHashSet<>(resources.keySet());
            for (Identifier packed : packedEntries.keySet()) {
                if (packed.getPath().startsWith(root + "/") && !resources.containsKey(packed)) {
                    candidates.add(packed);
                }
            }

            // 免打包目录（<对象目录>/local/）里的文件最后处理：两边同名时由 local/ 那份覆盖包里那份
            List<Identifier> ordered = new ArrayList<>(candidates.size());
            List<Identifier> localFiles = new ArrayList<>(0);
            for (Identifier candidate : candidates) {
                if (ModAssetPaths.isLocalFile(candidate)) {
                    localFiles.add(candidate);
                } else {
                    ordered.add(candidate);
                }
            }
            ordered.addAll(localFiles);

            int before = foundModels.size() + foundAnimations.size();
            int fromDisk = 0;
            int fromPack = 0;
            for (Identifier raw : ordered) {
                String path = raw.getPath();

                // 只收这两个后缀：既是格式判断，也顺手挡掉 assets/minegenshin/items/xxx.json 这类同前缀文件
                boolean isModel = path.endsWith(MODEL_SUFFIX);
                boolean isAnimation = path.endsWith(ANIMATION_SUFFIX);
                if (!isModel && !isAnimation) {
                    continue;
                }
                if (!path.startsWith(root)) {
                    continue;
                }

                // 免打包目录不算资源身份：character/x/local/y 与 character/x/y 是同一个键
                Identifier key = ModAssetPaths.withoutLocalDir(GeckoLibResources.stripPrefixAndSuffix(raw));

                // 两个来源二选一：判据统一在 GeoPackSource.usePacked（整包优先、local/ 例外）。
                // 同名位置在磁盘上另有一份随包副本，两份不等价，不能按「磁盘优先」来挑。
                byte[] packed = GeoPackSource.usePacked(raw, packedEntries.containsKey(raw))
                        ? packedEntries.get(raw)
                        : null;
                Resource onDisk = packed == null ? resources.get(raw) : null;
                if (packed == null && onDisk == null) {
                    continue;
                }
                if (packed != null) {
                    fromPack++;
                } else {
                    fromDisk++;
                }

                try {
                    if (isModel) {
                        var json = onDisk != null
                                ? LOADER.deserializeGeckoLibModelFile(raw, onDisk)
                                : LOADER.readPacked(raw, packed);
                        foundModels.put(key, LOADER.bakeGeckoLibModelFile(raw, json));
                    } else {
                        var json = onDisk != null
                                ? LOADER.deserializeGeckoLibAnimationFile(raw, onDisk)
                                : LOADER.readPacked(raw, packed);
                        foundAnimations.put(key, LOADER.bakeGeckoLibAnimationsFile(raw, json, mathParser));
                    }
                } catch (Exception e) {
                    LOGGER.error("[GenshinGeoCache] 烘培失败：{}", raw, e);
                }
            }

            LOGGER.info("[GenshinGeoCache] 扫描根 '{}'：命中资源 {} 个（磁盘 {} / 资源包 {}），烘培成功 {} 个",
                    root, candidates.size(), fromDisk, fromPack,
                    foundModels.size() + foundAnimations.size() - before);
        }

        return new Scanned(Map.copyOf(foundModels), Map.copyOf(foundAnimations));
    }

    private void apply(Scanned scanned) {
        models = scanned.models();
        animations = scanned.animations();
        reloaded = true;

        LOGGER.info("[GenshinGeoCache] 已加载 {} 个模型 / {} 个动画文件", models.size(), animations.size());

        // 把实际拿到的键打出来 —— 路径对不上时一眼就能看出是「没扫到」还是「键不一致」
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("[GenshinGeoCache] 模型键: {}", models.keySet().stream()
                    .map(Identifier::toString).sorted().toList());
            LOGGER.info("[GenshinGeoCache] 动画键: {}", animations.keySet().stream()
                    .map(Identifier::toString).sorted().toList());
        }
    }

    /** 供 {@code JsonOps} 之类需要序列化上下文的场景兜底（当前未使用，留个入口）。 */
    public static JsonOps jsonOps() {
        return JsonOps.INSTANCE;
    }
}
