package com.linweiyun.genshin.client.render.character;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 从一件物品反解出它的 GeckoLib geo 模型与贴图。
 *
 * <p>物品是 {@code GeoItem} 时，{@code GeoRenderProvider} 给出它的 {@link GeoItemRenderer}，
 * 渲染器里挂着 {@link GeoModel}，模型按物品实例报出自己的模型/贴图路径。
 *
 * <p>反解失败（不是 geo 物品、渲染器还没建、资源还没加载）返回 {@code null}，
 * 调用方退回「整个物品模型」的画法。
 */
public final class GeoItemModelResolver {

    private GeoItemModelResolver() {
    }

    /** 一件物品的 geo 模型与贴图。 */
    public record Resolved(ResourceLocation modelId, ResourceLocation textureId) {
    }

    /**
     * @return 模型/贴图路径；不是 GeckoLib geo 物品或无法反解时返回 {@code null}
     */
    @Nullable
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Resolved resolve(ItemStack stack, Player player) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        try {
            if (!(stack.getItem() instanceof GeoItem)) {
                return null;
            }

            Object providerRenderer = GeoRenderProvider.of(stack).getGeoItemRenderer();
            if (!(providerRenderer instanceof GeoItemRenderer renderer)) {
                return null;
            }

            GeoModel model = renderer.getGeoModel();
            if (model == null) {
                return null;
            }

            GeoAnimatable item = (GeoAnimatable) stack.getItem();
            BakedGeoModel baked = model.getBakedModel(model.getModelResource(item));
            if (baked == null) {
                return null;
            }

            return new Resolved(
                    model.getModelResource(item),
                    model.getTextureResource(item));
        } catch (Exception e) {
            return null;
        }
    }

    /** 模型 id → 烘培好的模型；还没加载出来时返回 {@code null}。 */
    @Nullable
    public static BakedGeoModel bakedModel(ResourceLocation modelId) {
        if (modelId == null) {
            return null;
        }
        return GeckoLibCache.getBakedModels().get(modelId);
    }
}