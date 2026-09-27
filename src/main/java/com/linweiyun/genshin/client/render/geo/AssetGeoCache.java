package com.linweiyun.genshin.client.render.geo;

import com.geckolib.cache.animation.Animation;
import com.geckolib.cache.animation.BakedAnimations;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.loading.math.MathParser;
import com.google.gson.JsonObject;
import com.linweiyun.genshin.core.asset.AssetCategory;
import com.linweiyun.genshin.core.asset.ModAssetPaths;
import com.linweiyun.genshin.core.asset.pack.GeoJsonReader;
import com.linweiyun.genshin.core.asset.pack.GeoPackSource;
import com.linweiyun.genshin.core.asset.pack.GenshinGsonLoader;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.PreparableReloadListener.PreparationBarrier;
import net.minecraft.server.packs.resources.PreparableReloadListener.SharedState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;

/**
 * 统一布局资源索引缓存：扫描 {@code item/}、{@code block/}、{@code entity/} 三个根，
 * 把与目录同名的模型 / 动画 / 贴图预烘培成 GeckoLib 的对象，供 {@link CategoryGeoModel}
 * 按「目录」一次性取用（{@link #files(String)}）。
 *
 * <p><b>既注册成客户端资源重载监听器，也保留懒加载兜底。</b>
 * 注册见 {@code MinegenshinClient#addReloadListeners}：这样每次资源重载（含 F3+T）都会重新扫描，
 * 索引不会在第一次重载之后变成陈旧的。
 *
 * <p>但「注册成监听器」本身有历史教训：监听器一旦抛异常，<b>整个客户端资源重载都会失败</b>，
 * 表现是「所有资源驱动的东西一起失效」，排查时完全指不到这里。所以这里有三道保险：
 * {@link #reload} 返回的 future 用 {@code exceptionally} 兜住、{@link #scan} 用
 * try/catch(Throwable) 把失败限制在「本次跳过统一布局资源」、以及
 * {@link #warmUp()} / {@link #ensureLoaded()} 守住「监听器还没跑」的那个窗口。
 *
 * <p>另外两类防御也是那次事故的直接产物：
 * <ul>
 *   <li>{@link #scan} 用 try/catch(Throwable) 把失败限制在「本次跳过统一布局资源」；</li>
 *   <li>所有 Map 复制都走 {@link #copyNonNull} —— GeckoLib 的 loader 解析失败时
 *       会往 Map 里塞 null，直接 {@code Map.copyOf} 会 NPE 并炸掉整个重载。</li>
 * </ul>
 *
 * <p>还有一类防御针对的是「<b>资源还没就绪就扫了一遍</b>」：客户端初始化阶段
 * {@code Minecraft#getResourceManager()} 可能还没有任何资源，扫出来是空的。
 * 这时如果照样落锚（{@code reloaded = true}），索引会永远停在「空」的状态 ——
 * 历史现象就是日志里 {@link #apply} 一个文件都扫不到、{@code CategoryGeoModel} 每次都回退到
 * {@code GenshinGeoCache}。所以空扫描<b>不落锚</b>，留着重扫机会（上限见 {@link #MAX_EMPTY_SCANS}）。
 *
 * <h2>两种资源</h2>
 * 对象目录里的 {@code local/} 子目录是<b>免打包</b>的（见 {@code ModAssetPaths.LOCAL_DIR}）：
 * 里面的模型 / 动画 / 贴图原样直读、永不进整包，算目录归属时会去掉这一层 ——
 * {@code item/x/local/y.geo.json} 与 {@code item/x/y.geo.json} 落在同一个对象目录下，
 * 且<b>同名时以 {@code local/} 那份为准</b>（{@code preferModel} / {@code preferAnimation} /
 * {@code preferTexture} 里各有一条与扫描顺序无关的判定）。
 */
public final class AssetGeoCache implements PreparableReloadListener {
    private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);
    /** GeckoLib 烘培器的「能读整包」版；包内条目走 {@code readPacked}，所以用具体类型。 */
    private static final GenshinGsonLoader LOADER = new GenshinGsonLoader();
    private static final String[] ROOTS = new String[]{AssetCategory.ITEM.folder(), AssetCategory.BLOCK.folder(), AssetCategory.ENTITY.folder()};

    /** 空扫描最多重试几次；超过就不再重扫（避免在真的没有资源时每帧都扫）。 */
    private static final int MAX_EMPTY_SCANS = 5;

    /**
     * 原版入口层的文件名 —— 这些文件<b>不是</b> GeckoLib 资源，扫描时必须跳过。
     *
     * <p>{@code block/<id>/blockstate.json} 与 {@code item/<id>/{definition,model}.json} 是给
     * 原版物品定义 / 模型 / 方块状态管线用的（由 {@code core.asset.AssetRedirects} 供料）。
     * 不跳过的话它们会走到「内容判不出类型 → 按文件名当作模型 → 烘培失败」这条路上，
     * 每次扫描都刷一片错误日志。
     *
     * <p>名字与 {@code ModAssetPaths} 的常量保持一致（{@code BLOCKSTATE_FILE} /
     * {@code DEFINITION_FILE} / {@code MODEL_FILE}）。
     */
    private static final String[] VANILLA_ENTRY_FILES = {"blockstate.json", "definition.json", "model.json"};

    private static volatile Map<Identifier, BakedGeoModel> models = Map.of();
    private static volatile Map<Identifier, BakedAnimations> animations = Map.of();
    private static volatile Map<String, AssetGeoCache.DirFiles> index = Map.of();
    private static volatile boolean reloaded = false;
    /** 连续空扫描计数；扫到东西就归零。 */
    private static int emptyScans = 0;

    @Nullable
    public static BakedGeoModel model(@Nullable Identifier location) {
        if (location == null) {
            return null;
        }

        ensureLoaded();
        return models.get(location);
    }

    @Nullable
    public static Animation animation(@Nullable Identifier animationKey, @Nullable String name) {
        if (animationKey != null && name != null) {
            ensureLoaded();
            BakedAnimations baked = animations.get(animationKey);
            if (baked == null) {
                return null;
            }

            Animation exact = baked.getAnimation(name);
            if (exact != null) {
                return exact;
            }

            String matched = fuzzyAnimationName(baked, name);
            if (matched == null) {
                return null;
            }

            LOGGER.info("[AssetGeoCache] 动画名宽松匹配：{} 里没有 '{}'，改用 '{}'", new Object[]{animationKey, name, matched});
            return baked.getAnimation(matched);
        } else {
            return null;
        }
    }

    public static Set<String> animationNames(@Nullable Identifier animationKey) {
        if (animationKey == null) {
            return Set.of();
        }

        ensureLoaded();
        BakedAnimations baked = animations.get(animationKey);
        return baked == null ? Set.of() : baked.animations().keySet();
    }

    @Nullable
    private static String fuzzyAnimationName(BakedAnimations baked, String wanted) {
        String suffix = "." + wanted;
        String candidate = null;

        for (String available : baked.animations().keySet()) {
            if (available.equals(wanted) || available.endsWith(suffix)) {
                if (candidate != null) {
                    return null;
                }

                candidate = available;
            }
        }

        return candidate;
    }

    public static AssetGeoCache.DirFiles files(@Nullable String dir) {
        if (dir == null) {
            return AssetGeoCache.DirFiles.EMPTY;
        }

        ensureLoaded();
        return index.getOrDefault(dir, AssetGeoCache.DirFiles.EMPTY);
    }

    public static int dirCount() {
        return index.size();
    }

    public static void warmUp() {
        ensureLoaded();
    }

    public static int modelCount() {
        return models.size();
    }

    public static Set<String> knownDirs() {
        ensureLoaded();
        return index.keySet();
    }

    private static void ensureLoaded() {
        if (!reloaded) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) {
                ResourceManager resourceManager = minecraft.getResourceManager();
                if (resourceManager != null) {
                    synchronized (AssetGeoCache.class) {
                        if (!reloaded) {
                            LOGGER.info("[AssetGeoCache] 首次查询时同步补扫一次统一布局资源");
                            AssetGeoCache instance = new AssetGeoCache();
                            instance.apply(instance.scan(resourceManager));
                        }
                    }
                }
            }
        }
    }

    public CompletableFuture<Void> reload(SharedState sharedState, Executor prepExecutor, PreparationBarrier barrier, Executor applyExecutor) {
        ResourceManager resourceManager = sharedState.resourceManager();
        return CompletableFuture.<AssetGeoCache.Scanned>supplyAsync(() -> this.scan(resourceManager), prepExecutor)
            .<AssetGeoCache.Scanned>thenCompose(barrier::wait)
            .thenAcceptAsync(this::apply, applyExecutor)
            // 本监听器绝不允许把异常抛回重载链：那会让整个客户端资源重载失败。
            .exceptionally(t -> {
                LOGGER.error("[AssetGeoCache] 重载监听器执行失败，保持上一次的索引（不影响其它资源重载）", t);
                return null;
            });
    }

    private AssetGeoCache.Scanned scan(ResourceManager resourceManager) {
        try {
            return this.scanUnsafe(resourceManager);
        } catch (Throwable t) {
            LOGGER.error("[AssetGeoCache] 扫描失败，本次跳过统一布局资源（不影响其它资源重载）", t);
            return new AssetGeoCache.Scanned(Map.of(), Map.of(), Map.of());
        }
    }

    private AssetGeoCache.Scanned scanUnsafe(ResourceManager resourceManager) {
        Map<Identifier, BakedGeoModel> foundModels = new HashMap<>(models);
        Map<Identifier, BakedAnimations> foundAnimations = new HashMap<>(animations);
        Map<String, AssetGeoCache.DirFiles> foundIndex = new HashMap<>(index);
        MathParser mathParser = MathParser.createWithDeduplication();

        // 整包里的条目：同名时用哪一份由 GeoPackSource.usePacked 裁定（整包优先，local/ 例外）
        Map<Identifier, byte[]> packedEntries = GeoPackSource.entries(resourceManager);

        for (String root : ROOTS) {
            Map<Identifier, Resource> resources;
            try {
                resources = resourceManager.listResources(root, id -> id.getNamespace().equals("minegenshin"));
            } catch (Exception e) {
                LOGGER.warn("[AssetGeoCache] 扫描根 '{}' 失败：{}", root, e.toString());
                continue;
            }

            Set<Identifier> candidates = new LinkedHashSet<>(resources.keySet());
            for (Identifier packed : packedEntries.keySet()) {
                if (packed.getPath().startsWith(root + "/") && !resources.containsKey(packed)) {
                    candidates.add(packed);
                }
            }

            int before = foundModels.size() + foundAnimations.size();
            int fromDisk = 0;
            int fromPack = 0;

            for (Identifier raw : candidates) {
                String path = raw.getPath();
                if (path.startsWith(root + "/")) {
                    if (isVanillaEntryFile(raw)) {
                        continue;
                    }
                    // 两个来源二选一：判据统一在 GeoPackSource.usePacked（整包优先、local/ 例外）。
                    // 同名位置在磁盘上另有一份随包副本，两份不等价，不能按「磁盘优先」来挑。
                    Resource onDisk = resources.get(raw);
                    byte[] packed = GeoPackSource.usePacked(raw, packedEntries.containsKey(raw))
                            ? packedEntries.get(raw)
                            : null;
                    if (onDisk == null && packed == null) {
                        continue;
                    }
                    if (packed != null) {
                        fromPack++;
                    } else if (onDisk != null) {
                        fromDisk++;
                    }
                    // 贴图在对象目录的 textures/ 子目录里，索引时要归到它所属的对象目录
                    String dir = ModAssetPaths.objectDirOf(ModAssetPaths.dirOf(raw));
                    if (dir != null) {
                        if (path.endsWith(".png")) {
                            // 贴图两个来源都算：包里有（onDisk 拿到的那份就是包内条目）或磁盘上有
                            // （local/ 与「包里没有这条」两路）。少了这一条，进包的贴图会被跳过，
                            // 对象目录就只剩模型 / 动画、贴图回落成缺贴图。
                            if (onDisk == null && packed == null) {
                                continue;
                            }
                            foundIndex.merge(dir, new AssetGeoCache.DirFiles(null, null, raw), AssetGeoCache::preferTexture);
                        } else if (path.endsWith(".json") && !ModAssetPaths.isBlockState(raw)) {
                            // 判类型也要看「真正会用的那一份」：整包赢了就传包内字节（磁盘传 null）
                            AssetGeoCache.ContentKind kind = classify(packed == null ? onDisk : null, packed, raw);
                            if (kind == AssetGeoCache.ContentKind.UNKNOWN) {
                                kind = ModAssetPaths.isAnimationFile(raw) ? AssetGeoCache.ContentKind.ANIMATION : AssetGeoCache.ContentKind.MODEL;
                                LOGGER.info("[AssetGeoCache] {} 的内容判不出类型，按文件名当作 {}", raw, kind);
                            }

                            try {
                                if (kind == AssetGeoCache.ContentKind.ANIMATION) {
                                    JsonObject json = packed == null
                                            ? (JsonObject)LOADER.deserializeGeckoLibAnimationFile(raw, onDisk)
                                            : LOADER.readPacked(raw, packed);
                                    BakedAnimations baked = LOADER.bakeGeckoLibAnimationsFile(raw, json, mathParser);
                                    if (baked != null) {
                                        foundAnimations.put(ModAssetPaths.animationKeyOf(raw), baked);
                                        foundIndex.merge(dir, new AssetGeoCache.DirFiles(null, raw, null), AssetGeoCache::preferAnimation);
                                    } else {
                                        LOGGER.warn("[AssetGeoCache] 动画烘培返回 null，跳过：{}", raw);
                                    }
                                } else {
                                    JsonObject json = packed == null
                                            ? (JsonObject)LOADER.deserializeGeckoLibModelFile(raw, onDisk)
                                            : LOADER.readPacked(raw, packed);
                                    BakedGeoModel baked = LOADER.bakeGeckoLibModelFile(raw, json);
                                    if (baked != null) {
                                        foundModels.put(ModAssetPaths.modelKeyOf(raw), baked);
                                        foundIndex.merge(dir, new AssetGeoCache.DirFiles(raw, null, null), AssetGeoCache::preferModel);
                                    } else {
                                        LOGGER.warn("[AssetGeoCache] 模型烘培返回 null，跳过：{}", raw);
                                    }
                                }
                            } catch (Exception e) {
                                LOGGER.error("[AssetGeoCache] 烘培失败（文件读到了，但 GeckoLib 解析不了；这种情况下运行时会报「Unable to find animation file」）：{}", raw, e);
                            }
                        }
                    }
                }
            }

            LOGGER.info(
                "[AssetGeoCache] 扫描根 '{}'：命中资源 {} 个（磁盘 {} / 资源包 {}），新烘培 {} 个",
                new Object[]{root, candidates.size(), fromDisk, fromPack,
                        foundModels.size() + foundAnimations.size() - before}
            );
        }

        return new AssetGeoCache.Scanned(copyNonNull(foundModels), copyNonNull(foundAnimations), copyNonNull(foundIndex));
    }

    private static <K, V> Map<K, V> copyNonNull(Map<K, V> source) {
        Map<K, V> clean = new HashMap<>(source.size());
        source.forEach((key, value) -> {
            if (key != null && value != null) {
                clean.put((K)key, (V)value);
            }
        });
        return Map.copyOf(clean);
    }

    /** 是不是原版入口层的文件（见 {@link #VANILLA_ENTRY_FILES}）；这些不是 GeckoLib 资源。 */
    private static boolean isVanillaEntryFile(Identifier raw) {
        String path = raw.getPath();
        int slash = path.lastIndexOf('/');
        String file = slash < 0 ? path : path.substring(slash + 1);
        for (String name : VANILLA_ENTRY_FILES) {
            if (name.equals(file)) {
                return true;
            }
        }
        return false;
    }

    private static AssetGeoCache.ContentKind classify(@Nullable Resource resource, @Nullable byte[] packed, Identifier id) {
        try {
            // 走统一入口：磁盘上的字节与包内条目在这里走同一条解析路径，判定规则完全一致。
            JsonObject json = GeoJsonReader.read(resource, packed, id);
            if (json.has("animations")) {
                return AssetGeoCache.ContentKind.ANIMATION;
            } else {
                return !json.has("minecraft:geometry") && !json.has("geometry") ? AssetGeoCache.ContentKind.UNKNOWN : AssetGeoCache.ContentKind.MODEL;
            }
        } catch (Exception e) {
            LOGGER.warn("[AssetGeoCache] 读取 {} 失败：{}", id, e.toString());
            return AssetGeoCache.ContentKind.UNKNOWN;
        }
    }

    private static AssetGeoCache.DirFiles preferTexture(AssetGeoCache.DirFiles existing, AssetGeoCache.DirFiles incoming) {
        if (isPlain(incoming.texture()) != isPlain(existing.texture())) {
            return new AssetGeoCache.DirFiles(existing.model(), existing.animation(),
                isPlain(incoming.texture()) ? incoming.texture() : existing.texture());
        }
        if (existing.texture() == null) {
            return new AssetGeoCache.DirFiles(existing.model(), existing.animation(), incoming.texture());
        } else {
            return matchesDirName(incoming.texture()) && !matchesDirName(existing.texture())
                ? new AssetGeoCache.DirFiles(existing.model(), existing.animation(), incoming.texture())
                : existing;
        }
    }

    private static AssetGeoCache.DirFiles preferAnimation(AssetGeoCache.DirFiles existing, AssetGeoCache.DirFiles incoming) {
        if (isPlain(incoming.animation()) != isPlain(existing.animation())) {
            return new AssetGeoCache.DirFiles(existing.model(),
                isPlain(incoming.animation()) ? incoming.animation() : existing.animation(), existing.texture());
        }
        if (existing.animation() == null) {
            return new AssetGeoCache.DirFiles(existing.model(), incoming.animation(), existing.texture());
        } else {
            return matchesDirName(incoming.animation()) && !matchesDirName(existing.animation())
                ? new AssetGeoCache.DirFiles(existing.model(), incoming.animation(), existing.texture())
                : existing;
        }
    }

    private static AssetGeoCache.DirFiles preferModel(AssetGeoCache.DirFiles existing, AssetGeoCache.DirFiles incoming) {
        // 免打包目录（local/）优先：这条不依赖扫描顺序，两个来源谁先谁后结果都一样
        if (isPlain(incoming.model()) != isPlain(existing.model())) {
            return new AssetGeoCache.DirFiles(isPlain(incoming.model()) ? incoming.model() : existing.model(),
                existing.animation(), existing.texture());
        }
        if (existing.model() == null) {
            return new AssetGeoCache.DirFiles(incoming.model(), existing.animation(), existing.texture());
        }

        boolean incomingNamed = matchesDirName(incoming.model());
        boolean existingNamed = matchesDirName(existing.model());
        if (incomingNamed != existingNamed) {
            return new AssetGeoCache.DirFiles(incomingNamed ? incoming.model() : existing.model(), existing.animation(), existing.texture());
        }

        boolean incomingPlain = !incoming.model().getPath().endsWith(".geo.json");
        boolean existingPlain = !existing.model().getPath().endsWith(".geo.json");
        return incomingPlain != existingPlain
            ? new AssetGeoCache.DirFiles(incomingPlain ? incoming.model() : existing.model(), existing.animation(), existing.texture())
            : existing;
    }

    private static boolean matchesDirName(@Nullable Identifier file) {
        String dir = ModAssetPaths.dirOf(file);
        String base = ModAssetPaths.baseNameOf(file);
        return dir != null && base != null && base.equals(ModAssetPaths.dirNameOf(dir));
    }

    /** 这个文件是不是免打包目录（{@code local/}）里的。 */
    private static boolean isPlain(@Nullable Identifier file) {
        return ModAssetPaths.isLocalFile(file);
    }

    private void apply(AssetGeoCache.Scanned scanned) {
        models = scanned.models();
        animations = scanned.animations();
        index = scanned.index();

        boolean empty = index.isEmpty() && models.isEmpty() && animations.isEmpty();
        if (empty && ++emptyScans < MAX_EMPTY_SCANS) {
            LOGGER.warn(
                "[AssetGeoCache] 本次一个文件都没扫到（资源可能还没就绪），保留重扫机会：第 {} / {} 次",
                new Object[]{emptyScans, MAX_EMPTY_SCANS}
            );
            return;
        }
        if (empty) {
            LOGGER.error("[AssetGeoCache] 连续 {} 次没扫到任何统一布局资源，停止重扫", emptyScans);
        }

        emptyScans = 0;
        reloaded = true;
        LOGGER.info("[AssetGeoCache] 已索引 {} 个目录 / {} 个模型 / {} 个动画文件", new Object[]{index.size(), models.size(), animations.size()});
        if (LOGGER.isInfoEnabled()) {
            for (Entry<String, AssetGeoCache.DirFiles> entry : new TreeMap<>(index).entrySet()) {
                AssetGeoCache.DirFiles files = entry.getValue();
                LOGGER.info(
                    "[AssetGeoCache]   {}  model={}  animation={}  texture={}",
                    new Object[]{entry.getKey(), describe(files.model()), describe(files.animation()), describe(files.texture())}
                );
            }

            for (Entry<Identifier, BakedAnimations> entry : new TreeMap<>(animations).entrySet()) {
                LOGGER.info("[AssetGeoCache]   动画 {} 里可用名字：{}", entry.getKey(), entry.getValue().animations().keySet());
            }
        }
    }

    private static String describe(@Nullable Identifier location) {
        return location == null ? "MISSING" : location.toString();
    }

    private enum ContentKind {
        MODEL,
        ANIMATION,
        UNKNOWN;
    }

    public record DirFiles(@Nullable Identifier model, @Nullable Identifier animation, @Nullable Identifier texture) {
        public static final AssetGeoCache.DirFiles EMPTY = new AssetGeoCache.DirFiles(null, null, null);
    }

    private record Scanned(Map<Identifier, BakedGeoModel> models, Map<Identifier, BakedAnimations> animations, Map<String, AssetGeoCache.DirFiles> index) {
    }
}
