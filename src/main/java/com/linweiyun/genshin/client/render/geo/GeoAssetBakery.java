package com.linweiyun.genshin.client.render.geo;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;

/**
 * 把 geo / 动画 JSON 烘成 GeckoLib 的运行时对象。
 *
 * <p>本 MOD 的资源目录不在 GeckoLib 自己扫描的 {@code geo} / {@code animations} 根下，
 * 所以缓存层自己读文件、自己烘培，两个缓存（{@link GenshinGeoCache} /
 * {@link AssetGeoCache}）共用这一份烘培规则。</p>
 */
final class GeoAssetBakery {

    private GeoAssetBakery() {
    }

    /**
     * 模型 JSON → {@link BakedGeoModel}。
     *
     * @param id   模型资源路径，按命名空间选择烘培工厂
     * @param json 模型 JSON 本体
     */
    static BakedGeoModel model(ResourceLocation id, JsonObject json) {
        Model model = KeyFramesAdapter.GEO_GSON.fromJson(json, Model.class);
        return BakedModelFactory.getForNamespace(id.getNamespace()).constructGeoModel(GeometryTree.fromModel(model));
    }

    /**
     * 动画 JSON → {@link BakedAnimations}。
     *
     * @param json 动画 JSON 本体；动画条目在 {@code animations} 字段下
     */
    static BakedAnimations animations(JsonObject json) {
        return KeyFramesAdapter.GEO_GSON.fromJson(GsonHelper.getAsJsonObject(json, "animations"), BakedAnimations.class);
    }
}
