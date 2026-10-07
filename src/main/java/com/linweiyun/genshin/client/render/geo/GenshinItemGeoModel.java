package com.linweiyun.genshin.client.render.geo;

import software.bernie.geckolib.animatable.GeoAnimatable;
import com.linweiyun.genshin.asset.GenshinAssets;
import com.linweiyun.genshin.asset.GeoAssetKind;
import com.linweiyun.genshin.asset.GeoPathOverrides;
import net.minecraft.resources.ResourceLocation;

/**
 * MOD 自己的 GeckoLib <b>物品</b>模型基类 —— 路径按 {@code item/<物品名>/<物品名>.*} 推。
 *
 * <p>布局（由 {@link GenshinGeoCache} 扫描，所以不受 GeckoLib「只能放 geckolib/ 下」的限制）：
 * <pre>
 * assets/minegenshin/item/test_sword/test_sword.geo.json
 * assets/minegenshin/item/test_sword/test_sword.animation.json   ← 可选
 * assets/minegenshin/item/test_sword/test_sword.png
 * </pre>
 * 需要临时改某个物品的模型时不用动这个类。
 */
public abstract class GenshinItemGeoModel<T extends GeoAnimatable> extends GenshinGeoModel<T> {

    private final ResourceLocation modelId;
    private final ResourceLocation textureId;
    private final ResourceLocation animationId;

    /**
     * @param itemName 物品名（注册名去掉命名空间），如 {@code test_sword}
     */
    protected GenshinItemGeoModel(String itemName) {
        this.modelId = GenshinAssets.itemModel(itemName, GenshinAssets.defaultItemModelFile(itemName));
        this.textureId = GenshinAssets.itemTexture(itemName, GenshinAssets.defaultItemTextureFile(itemName));
        this.animationId = GenshinAssets.itemAnimation(itemName, GenshinAssets.defaultItemAnimationFile(itemName));
        setPaths(modelId, textureId, animationId);
    }

    @Override
    public ResourceLocation getModelResource(T animatable) {
        return GeoPathOverrides.resolve(
                GeoAssetKind.MODEL, this, modelId);
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        return GeoPathOverrides.resolve(
                GeoAssetKind.TEXTURE, this, textureId);
    }

    @Override
    public ResourceLocation getAnimationResource(T animatable) {
        return GeoPathOverrides.resolve(
                GeoAssetKind.ANIMATION, this, animationId);
    }
}
