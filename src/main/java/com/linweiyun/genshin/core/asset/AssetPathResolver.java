package com.linweiyun.genshin.core.asset;

import com.linweiyun.genshin.core.asset.pack.GeoPackSource;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 按「类别 + id」解析资源 —— 存在性检查 + 候选回退 + 枚举。
 *
 * <h2>它解决什么</h2>
 * {@link ModAssetPaths} 只会拼字符串，文件在不在它不知道。真正要出问题时（贴图忘了放、
 * 模型写成了 {@code .geo.json}）都发生在「文件不存在」上，所以这里统一做一次解析：
 * <ul>
 *   <li>{@link #resolveModel} 之类的候选回退：{@code <id>.json} 找不到就试 {@code <id>.geo.json}。</li>
 *   <li>{@link #existing} 只判断存在性，不做回退，给「必须明确知道用的是哪个文件」的场景。</li>
 *   <li>{@link #listIds} 枚举某个类别下所有已存在的 id，给调试命令 / 资源校验用。</li>
 * </ul>
 *
 * <h2>双端可用</h2>
 * 所有方法都显式接收 {@link ResourceManager}，服务端传
 * {@code server.getResourceManager()}，客户端传
 * {@code Minecraft.getInstance().getResourceManager()}。
 * 这里刻意<b>不</b>引用任何客户端类，好让本类在服务端也能安全加载。
 * 不缓存任何东西 —— 资源重载后结果必须立刻变。
 *
 * <h2>整包</h2>
 * geo 模型 / 动画 / 对象目录里的贴图都不再以单个文件存在，全在 {@code .minegenshin} 整包里
 * （见 {@link GeoPackSource}）。所以存在性判断除了问资源管理器，还要问一次包内索引，
 * 否则「包里明明有模型」会被判成 MISSING（拿不到整包读取器时包内索引恒为空，
 * 结果就是这些模型被判成 MISSING，这是预期行为）。包外的资源
 * （音效、{@code gui/} 与 {@code icon/} 那类共用界面贴图、原版入口文件）不受影响，
 * 照旧只看资源管理器。
 */
public final class AssetPathResolver {

    private AssetPathResolver() {
    }

    // ==================== 基础查询 ====================

    /** 文件是否存在（磁盘上的真实文件，或整包里的条目）。 */
    public static boolean existing(@Nullable ResourceManager resourceManager, @Nullable Identifier location) {
        if (resourceManager == null || location == null) {
            return false;
        }
        return resourceManager.getResource(location).isPresent()
                || GeoPackSource.contains(resourceManager, location);
    }

    /**
     * 按候选顺序取第一个存在的文件。
     *
     * @return 命中的文件；一个都不存在时返回 null
     */
    @Nullable
    public static Identifier firstExisting(@Nullable ResourceManager resourceManager, Identifier... candidates) {
        if (resourceManager == null || candidates == null) {
            return null;
        }
        for (Identifier candidate : candidates) {
            if (existing(resourceManager, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    // ==================== 套装解析 ====================

    /** 模型文件（{@code .json} 优先，其次 {@code .geo.json}）；都不在时返回 null。 */
    @Nullable
    public static Identifier resolveModel(@Nullable ResourceManager resourceManager, AssetSet set) {
        return firstExisting(resourceManager, set.modelCandidates());
    }

    /** 动画文件；不在时返回 null。 */
    @Nullable
    public static Identifier resolveAnimation(@Nullable ResourceManager resourceManager, AssetSet set) {
        return firstExisting(resourceManager, set.animationCandidates());
    }

    /** 贴图文件；不在时返回 null。 */
    @Nullable
    public static Identifier resolveTexture(@Nullable ResourceManager resourceManager, AssetSet set) {
        return firstExisting(resourceManager, set.textureCandidates());
    }

    // ==================== 枚举 ====================

    /** 某个类别下的全部资源文件。 */
    public static Map<Identifier, Resource> listCategory(@Nullable ResourceManager resourceManager,
                                                         AssetCategory category) {
        if (resourceManager == null) {
            return Map.of();
        }
        String root = category.folder();
        Map<Identifier, Resource> found = resourceManager.listResources(root, id -> category.matchesPath(id.getPath()));
        return found;
    }

    /**
     * 某个类别下已经存在资源的 id 集合（{@code <类别>/<id>/…} 里的 {@code <id>}）。
     *
     * <p>返回的是插入顺序稳定的集合，方便直接打印。
     */
    public static Set<String> listIds(@Nullable ResourceManager resourceManager, AssetCategory category) {
        Set<String> ids = new LinkedHashSet<>();
        for (Identifier location : listCategory(resourceManager, category).keySet()) {
            String id = ModAssetPaths.idOf(location);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** 某个类别下某个 id 已经存在的「角色」文件名（{@code test1.json} / {@code blockitem/a.png} …）。 */
    public static Set<String> listRoles(@Nullable ResourceManager resourceManager,
                                        AssetCategory category, String id) {
        Set<String> roles = new LinkedHashSet<>();
        for (Identifier location : listCategory(resourceManager, category).keySet()) {
            if (!id.equals(ModAssetPaths.idOf(location))) {
                continue;
            }
            String role = ModAssetPaths.roleOf(location);
            if (role != null) {
                roles.add(role);
            }
        }
        return roles;
    }

    /** 调试用：把某个 id 的三件套存在情况打成一行。 */
    public static String describe(@Nullable ResourceManager resourceManager, AssetSet set) {
        return set.describe()
                + " model=" + presence(resolveModel(resourceManager, set))
                + " animation=" + presence(resolveAnimation(resourceManager, set))
                + " texture=" + presence(resolveTexture(resourceManager, set));
    }

    private static String presence(@Nullable Identifier location) {
        return location == null ? "MISSING" : location.toString();
    }

    /**
     * 取一个 Optional 形式的资源；给需要读文件内容的调用方用。
     *
     * <p>贴图可以直接用：{@link com.linweiyun.genshin.core.asset.pack.GeoPackResources}
     * 已经把整包里的贴图条目接到了资源管理器上，这里拿到的就是包内那份。
     * geo 模型 / 动画没有逐文件的 {@link Resource}（它们只由缓存扫描按条目字节读，
     * 见 {@link com.linweiyun.genshin.core.asset.pack.GeoJsonReader}）。
     */
    public static Optional<Resource> resource(@Nullable ResourceManager resourceManager, @Nullable Identifier location) {
        if (resourceManager == null || location == null) {
            return Optional.empty();
        }
        return resourceManager.getResource(location);
    }
}
