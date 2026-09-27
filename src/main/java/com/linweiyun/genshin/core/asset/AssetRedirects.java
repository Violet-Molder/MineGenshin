package com.linweiyun.genshin.core.asset;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 资源重定向层 —— 把「原版按固定路径读取的入口文件」接到本项目自己的对象布局上。
 *
 * <h2>为什么需要它</h2>
 * 原版有几处资源入口是<b>写死路径</b>的，项目自己的布局（{@code item/&lt;id&gt;/}、{@code block/&lt;id&gt;/}）
 * 本来供不上：{@code items/&lt;id&gt;.json}（物品定义）、{@code models/&lt;路径&gt;.json}（模型）、
 * {@code blockstates/&lt;id&gt;.json}（方块状态）、{@code textures/&lt;路径&gt;.png}（图集贴图）。
 *
 * <p>实测这四条通道<b>都</b>经由 {@link net.minecraft.resources.FileToIdConverter}：
 * <pre>
 * BlockStateModelLoader   FileToIdConverter.json("blockstates").listMatchingResourceStacks(manager)
 * ClientItemInfoLoader    FileToIdConverter.json("items").listMatchingResources(manager)
 * ModelManager            FileToIdConverter.json("models").listMatchingResources(manager)
 * DirectoryLister（图集）  new FileToIdConverter("textures/&lt;source&gt;", ".png").listMatchingResources(manager)
 * </pre>
 * 所以 {@code FileToIdConverterRedirectMixin} 在返回的 map 里补进下面这些「虚拟入口」，
 * 原版就能读到我们布局里的文件 —— 不改原版任何加载逻辑，也不碰 GeckoLib。
 *
 * <h2>映射规则</h2>
 * <table border="1">
 *   <caption>原版文件 id → 我们布局里的真实文件</caption>
 *   <tr><td>{@code blockstates/&lt;方块id&gt;.json}</td><td>{@code block/&lt;方块id&gt;/blockstate.json}</td></tr>
 *   <tr><td>{@code items/&lt;物品id&gt;.json}</td><td>{@code item/&lt;物品id&gt;/definition.json}，方块物品默认同名时取 {@code block/&lt;方块id&gt;/blockitem/definition.json}</td></tr>
 *   <tr><td>{@code models/&lt;其余路径&gt;.json}</td><td>{@code &lt;其余路径&gt;.json}（去掉 {@code models/} 前缀）</td></tr>
 *   <tr><td>{@code textures/&lt;其余路径&gt;.png}</td><td>{@code &lt;其余路径&gt;.png}（去掉 {@code textures/} 前缀）</td></tr>
 * </table>
 *
 * <p>三条硬性边界：<b>只处理 {@code minegenshin} 命名空间</b>、<b>只处理上表四个根</b>、
 * <b>只在原版没有同名真实文件时才补</b>（调用方用 {@code putIfAbsent}，方便临时做 A/B 对照）。
 * 整合包里其它 MOD 的 {@code entity/}、{@code item/} 之类目录完全不受影响。
 */
public final class AssetRedirects {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CORE);
    private static final String NS = Minegenshin.MOD_ID;

    /** 原版入口层的根目录；不在其中的前缀一律不碰。 */
    private static final Set<String> VANILLA_ROOTS = Set.of("blockstates", "items", "models", "textures");

    /**
     * 原版目录 → 参与镜像的本项目布局根。
     *
     * <p>只有这三条：原版的模型只可能是物品/方块模型，图集也只认
     * {@code assets/minecraft/atlases/items.json}（{@code source: item}）与
     * {@code blocks.json}（{@code source: block}）这两个目录源。
     * <b>绝不能按 {@code textures/**} 一把梭</b> —— 那会把我们的头像、立绘、图标塞进
     * 盾牌/旗帜/音符盒/调色板等每一个图集目录，既污染 sprite 表，又会让
     * {@code PalettedPermutations} 去把我们的图当调色板图加载（实测会刷一屏 ERROR）。
     */
    private static final Map<String, String[]> MIRROR_DIRECTORIES = Map.of(
            "models", new String[]{AssetCategory.ITEM.folder(), AssetCategory.BLOCK.folder()},
            "textures/item", new String[]{AssetCategory.ITEM.folder()},
            "textures/block", new String[]{AssetCategory.BLOCK.folder()});

    /** 放在布局里、但<b>不是</b>原版模型的 json：入口文件、音效定义。 */
    private static final Set<String> NON_MODEL_FILES = Set.of(
            ModAssetPaths.BLOCKSTATE_FILE,
            ModAssetPaths.DEFINITION_FILE,
            "sounds.json");

    private static final String GEO_SUFFIX = ".geo.json";
    private static final String ANIMATION_SUFFIX = ".animation.json";
    private static final String ANIMATIONS_SUFFIX = ".animations.json";

    /** 显式登记的「方块物品名 != 方块名」：物品 id → 方块 id。 */
    private static final Map<Identifier, Identifier> BLOCK_ITEM_OVERRIDES = new ConcurrentHashMap<>();

    /** 注入日志去重：前缀 → 上次打印的条数。 */
    private static final Map<String, Integer> LAST_LOGGED = new ConcurrentHashMap<>();

    private AssetRedirects() {
    }

    /**
     * 登记一个「方块物品的注册 id 与方块 id 不同名」的情况。
     *
     * <p>默认约定是方块物品与方块同名（{@code block/<方块id>/blockitem/definition.json} ← {@code items/<同名>.json}），
     * 只有不同名时才需要在这里补一条。
     */
    public static void registerBlockItem(Identifier blockId, Identifier itemId) {
        if (blockId != null && itemId != null) {
            BLOCK_ITEM_OVERRIDES.put(itemId, blockId);
        }
    }

    /**
     * 给某个原版目录算出要补进文件表的条目。
     *
     * @param vanillaDirectory 原版目录，如 {@code blockstates}、{@code models}、{@code textures/item}
     * @param manager          资源管理器（用来确认目标文件真的存在）
     * @return 原版文件 id → 真实 {@link Resource}；不需要改写时返回空 map
     */
    public static Map<Identifier, Resource> resolve(@Nullable String vanillaDirectory,
                                                    @Nullable ResourceManager manager) {
        if (vanillaDirectory == null || manager == null) {
            return Map.of();
        }

        String directory = trimSlashes(vanillaDirectory);
        int slash = directory.indexOf('/');
        String root = slash < 0 ? directory : directory.substring(0, slash);
        if (!VANILLA_ROOTS.contains(root)) {
            return Map.of();
        }

        Map<Identifier, Identifier> plan = switch (root) {
            case "blockstates" -> blockStateEntries();
            case "items" -> itemDefinitionEntries();
            case "models" -> mirrorEntries(manager, directory, ".json", true, "models/", false);
            case "textures" -> mirrorEntries(manager, directory, ".png", false, "textures/", true);
            default -> Map.of();
        };

        return materialize(directory, plan, manager);
    }

    // ==================== 规则 ====================

    /** {@code blockstates/<方块id>.json} ← {@code block/<方块id>/blockstate.json}。 */
    private static Map<Identifier, Identifier> blockStateEntries() {
        Map<Identifier, Identifier> plan = new LinkedHashMap<>();
        for (Identifier blockId : BuiltInRegistries.BLOCK.keySet()) {
            if (isOurs(blockId)) {
                plan.put(id("blockstates/" + blockId.getPath() + ".json"),
                        id(ModAssetPaths.dir(AssetCategory.BLOCK, blockId.getPath()) + "/"
                                + ModAssetPaths.BLOCKSTATE_FILE));
            }
        }
        return plan;
    }

    /** {@code items/<物品id>.json} ← {@code item/<物品id>/definition.json}，方块物品回落到 {@code block/<方块id>/blockitem/definition.json}。 */
    private static Map<Identifier, Identifier> itemDefinitionEntries() {
        Map<Identifier, Identifier> plan = new LinkedHashMap<>();

        for (Identifier itemId : BuiltInRegistries.ITEM.keySet()) {
            if (isOurs(itemId)) {
                plan.put(id("items/" + itemId.getPath() + ".json"),
                        id(ModAssetPaths.dir(AssetCategory.ITEM, itemId.getPath()) + "/"
                                + ModAssetPaths.DEFINITION_FILE));
            }
        }

        // 方块物品：默认与方块同名；不同名的由 registerBlockItem 补进来。
        for (Identifier blockId : BuiltInRegistries.BLOCK.keySet()) {
            if (!isOurs(blockId)) {
                continue;
            }
            plan.putIfAbsent(id("items/" + blockId.getPath() + ".json"),
                    id(ModAssetPaths.blockItemDir(blockId.getPath()) + "/" + ModAssetPaths.DEFINITION_FILE));
        }
        BLOCK_ITEM_OVERRIDES.forEach((itemId, blockId) ->
                plan.put(id("items/" + itemId.getPath() + ".json"),
                        id(ModAssetPaths.blockItemDir(blockId.getPath()) + "/" + ModAssetPaths.DEFINITION_FILE)));

        return plan;
    }

    /**
     * 镜像：布局里的任意文件 {@code <X>} 额外暴露成 {@code <根>/<X>}。
     *
     * <p>{@code models} 时 {@code <X>} 是 json（排除入口文件与 GeckoLib 文件），
     * {@code textures} 时 {@code <X>} 是 png。
     *
     * <p>注意虚拟路径是 {@code <原版根>/<我们布局里的完整相对路径>}：
     * {@code item/primogem/texture.png} → {@code textures/item/primogem/texture.png}
     * （我们布局的类别目录名与原版图集目录同名，这不是巧合，是刻意对齐的结果）。
     *
     * <p>{@code stripLayoutTextureDir} 为真时，会把布局里的 {@code /textures} 这一层去掉再拼虚拟路径：
     * {@code item/primogem/textures/texture.png} → {@code textures/item/primogem/texture.png}。
     * 因为 {@code textures/} 是<b>布局</b>（对象内部按类型分类），不是资源身份的一部分 ——
     * sprite id 必须保持 {@code minegenshin:item/&lt;id&gt;/texture}，模型 JSON 才不用改。
     */
    private static Map<Identifier, Identifier> mirrorEntries(ResourceManager manager,
                                                             String directory,
                                                             String suffix,
                                                             boolean models,
                                                             String vanillaPrefix,
                                                             boolean stripLayoutTextureDir) {
        String[] roots = MIRROR_DIRECTORIES.get(directory);
        if (roots == null || roots.length == 0) {
            return Map.of();
        }

        Map<Identifier, Identifier> plan = new LinkedHashMap<>();

        for (Identifier file : listLayout(manager, suffix, roots)) {
            String path = file.getPath();
            if (models && isNonModel(path)) {
                continue;
            }
            Identifier vanillaId = id(vanillaPrefix
                    + (stripLayoutTextureDir ? stripTextureDir(path) : path));
            Identifier mirrored = id(path);
            if (vanillaId.equals(mirrored)) {
                continue;
            }
            // 免打包目录（local/）里的贴图与对象目录里那份<b>指向同一个虚拟入口</b>：
            // item/x/local/textures/texture.png 与 item/x/textures/texture.png 都映射成
            // textures/item/x/texture.png，同名时以 local/ 那份为准（与对象目录里的规则一致）。
            Identifier previous = plan.get(vanillaId);
            if (previous == null || (ModAssetPaths.isLocalFile(file) && !ModAssetPaths.isLocalFile(previous))) {
                plan.put(vanillaId, mirrored);
            }
        }
        return plan;
    }

    private static boolean isNonModel(String path) {
        if (path.endsWith(GEO_SUFFIX) || path.endsWith(ANIMATION_SUFFIX) || path.endsWith(ANIMATIONS_SUFFIX)) {
            return true;
        }
        String file = path.substring(path.lastIndexOf('/') + 1);
        return NON_MODEL_FILES.contains(file);
    }

    /**
     * 去掉布局里的 {@code /textures} 与 {@code /local} 两层：
     * {@code item/x/textures/a.png} → {@code item/x/a.png}，
     * {@code item/x/local/textures/a.png} → {@code item/x/a.png}（两者是同一个虚拟入口）。
     */
    private static String stripTextureDir(String path) {
        return stripLayer(stripLayer(path, ModAssetPaths.TEXTURE_DIR), ModAssetPaths.LOCAL_DIR);
    }

    /** 去掉路径里的第一段 {@code /<层名>/}。 */
    private static String stripLayer(String path, String layer) {
        String marker = "/" + layer + "/";
        int at = path.indexOf(marker);
        return at < 0 ? path : path.substring(0, at) + "/" + path.substring(at + marker.length());
    }

    // ==================== 工具 ====================

    /** 列出本 MOD 布局里某个后缀的文件。 */
    private static List<Identifier> listLayout(ResourceManager manager, String suffix, String... roots) {
        List<Identifier> found = new ArrayList<>();
        for (String root : roots) {
            // 带斜杠再比一次：避免 item/ 与 items/ 这类同前缀目录互相误伤
            manager.listResources(root, candidate -> candidate.getPath().startsWith(root + "/")
                            && candidate.getPath().endsWith(suffix))
                    .keySet()
                    .forEach(candidate -> {
                        if (isOurs(candidate)) {
                            found.add(candidate);
                        }
                    });
        }
        return found;
    }

    /** 计划表 → 真实文件表（目标文件不存在就跳过），并打一条去重日志。 */
    private static Map<Identifier, Resource> materialize(String directory,
                                                         Map<Identifier, Identifier> plan,
                                                         ResourceManager manager) {
        if (plan.isEmpty()) {
            return Map.of();
        }

        Map<Identifier, Resource> resolved = new LinkedHashMap<>();
        for (Map.Entry<Identifier, Identifier> entry : plan.entrySet()) {
            Resource resource = manager.getResource(entry.getValue()).orElse(null);
            if (resource != null) {
                resolved.put(entry.getKey(), resource);
            }
        }

        logInjection(directory, resolved);
        return resolved;
    }

    private static void logInjection(String directory, Map<Identifier, Resource> resolved) {
        Integer previous = LAST_LOGGED.put(directory, resolved.size());
        if (resolved.isEmpty() || (previous != null && previous == resolved.size())) {
            return;
        }
        List<String> samples = new ArrayList<>(3);
        for (Map.Entry<Identifier, Resource> entry : resolved.entrySet()) {
            if (samples.size() >= 3) {
                break;
            }
            samples.add(entry.getKey() + "（实读 " + entry.getValue().sourcePackId() + "）");
        }
        LOGGER.info("[AssetRedirects] 原版目录 '{}' 注入 {} 条虚拟入口：{}",
                directory, resolved.size(), String.join("、", samples));
    }

    private static boolean isOurs(@Nullable Identifier id) {
        return id != null && NS.equals(id.getNamespace());
    }

    private static String trimSlashes(String value) {
        String trimmed = value;
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NS, path);
    }
}
