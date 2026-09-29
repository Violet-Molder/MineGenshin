package com.linweiyun.genshin.asset;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * GeckoLib 资源路径的「快速改写」注册表。
 *
 * <h2>为什么需要它</h2>
 * 项目里能拿到 GeckoLib 资源路径的地方有几处互不相干：角色的 {@code CharacterRenderData}、
 * 物品的 {@code GeoItemRenderer}、将来提瓦特实体的模型……资产目录一旦要调整，
 * 就得满工程找。这里把这些路径统一过一个可插拔的规则链，改目录只要注册一条规则。
 *
 * <h2>作用范围：只影响本 MOD 的对象</h2>
 * 规则链只在<b>我们自己的</b> {@link GenshinGeoModel} / 物品渲染器里被调用，
 * 不碰 GeckoLib 的任何全局行为，所以整合包里其它 MOD 的解析完全不受影响。
 * 默认规则（{@link GenshinAssets#installDefaults()}）还额外限定 `minegenshin:` 命名空间。
 *
 * <h2>顺序</h2>
 * 后注册的先问（像栈一样），方便在调试时临时插一条压住既有规则。
 */
public final class GeoPathOverrides {

    /** 后进先出：后注册的规则先被询问。 */
    private static final List<GeoPathRule> RULES = new CopyOnWriteArrayList<>();

    private GeoPathOverrides() {
    }

    // ==================== 注册 ====================

    /** 注册一条规则。 */
    public static void register(GeoPathRule rule) {
        if (rule != null) {
            RULES.add(rule);
        }
    }

    /** 只对某一种资源类别生效。 */
    public static void forKind(GeoAssetKind kind, GeoPathRule rule) {
        register((k, owner, original) -> k == kind ? rule.resolve(k, owner, original) : null);
    }

    /** 只对某个实例生效（按引用相等判断）。 */
    public static void forOwner(Object owner, GeoPathRule rule) {
        register((k, o, original) -> o == owner ? rule.resolve(k, o, original) : null);
    }

    /** 只对某一类对象生效。 */
    public static void forOwnerClass(Class<?> ownerType, GeoPathRule rule) {
        register((k, o, original) ->
                (o != null && ownerType.isInstance(o)) ? rule.resolve(k, o, original) : null);
    }

    /** 只对某个命名空间生效（默认规则用的就是这个）。 */
    public static void forNamespace(String namespace, GeoPathRule rule) {
        register(rule.forNamespace(namespace));
    }

    /** 撤掉所有规则（调试用；撤掉后只剩默认约定）。 */
    public static void clear() {
        RULES.clear();
    }

    // ==================== 解析 ====================

    /**
     * 依次问每条规则，取第一条给出结果的。
     *
     * @param kind     资源类别
     * @param owner    发起解析的对象，可为 null
     * @param original 不加规则时会用的路径
     * @return 最终路径；没有任何规则接管时原样返回 {@code original}
     */
    public static Identifier resolve(GeoAssetKind kind, @Nullable Object owner, Identifier original) {
        if (original == null) {
            return null;
        }
        for (int i = RULES.size() - 1; i >= 0; i--) {
            Identifier rewritten = RULES.get(i).resolve(kind, owner, original);
            if (rewritten != null) {
                return rewritten;
            }
        }
        return original;
    }

    /** 当前规则条数，调试用。 */
    public static int size() {
        return RULES.size();
    }
}
