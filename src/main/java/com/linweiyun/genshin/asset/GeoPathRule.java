package com.linweiyun.genshin.asset;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * 一条路径改写规则。
 *
 * <p>{@link GeoPathOverrides} 会按注册顺序依次问每条规则；<b>返回 {@code null} 表示这条规则不管</b>，
 * 交给下一条。全部都不管时用 {@link GenshinAssets} 的默认约定。
 *
 * <p>典型写法：
 * <pre>
 * // 只对某一种资源生效
 * GeoPathOverrides.forKind(GeoAssetKind.MODEL, (kind, owner, original) ->
 *         original.getPath().startsWith("character/vesna/") ? MY_PATH : null);
 *
 * // 只对某个实例生效（做 A/B 对比时很有用）
 * GeoPathOverrides.forOwner(myModel, (kind, owner, original) -> DEBUG_PATH);
 *
 * // 只对某一类对象生效
 * GeoPathOverrides.forOwnerClass(TeyvatEntityModel.class, (kind, owner, original) -> ENTITY_PATH);
 * </pre>
 */
@FunctionalInterface
public interface GeoPathRule {

    /**
     * @param kind     这一条问的是哪类资源
     * @param owner    发起解析的对象（我们的 {@code GeoModel} / 物品渲染器实例），可能为 null
     * @param original 不加任何规则时会用的路径
     * @return 改写后的路径；{@code null} 表示不处理
     */
    @Nullable
    Identifier resolve(GeoAssetKind kind, @Nullable Object owner, Identifier original);

    /** 命名空间过滤的便捷包装。 */
    default GeoPathRule forNamespace(String namespace) {
        return (kind, owner, original) ->
                namespace.equals(original.getNamespace()) ? resolve(kind, owner, original) : null;
    }
}
