package com.linweiyun.genshin.asset.pack;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.GsonHelper;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import software.bernie.geckolib.loading.math.MathParser;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;

/**
 * GeckoLib 的 Gson 加载器。
 *
 * <p>负责把 geo 模型 / 动画的 JSON 文本解析成 {@link JsonObject}，再烘培成
 * {@link BakedGeoModel} / {@link BakedAnimations}。读取与烘培都用 GeckoLib 自己的
 * 反序列化器，因此模型文件里塞动画、动画文件里塞模型这类校验，以及动画名、骨骼解析规则，
 * 都和 GeckoLib 原生一致。
 *
 * <p>JSON 文本有两个来源：
 * <ul>
 *   <li>磁盘文件（{@link #deserializeGeckoLibModelFile} / {@link #deserializeGeckoLibAnimationFile}）——
 *       字节先过 {@link GeoPackReaders} 找到的读取器；</li>
 *   <li>整包（{@link #readPacked}）—— 条目字节来自 {@link GeoPackSource}，直接解析。</li>
 * </ul>
 */
public class GenshinGsonLoader {

    /**
     * 读取磁盘上的 geo 模型 JSON。
     *
     * @param resourcePath 资源路径，仅用于日志与报错
     * @param resource     资源本体，字节可能来自整包读取器
     * @return 解析后的 JSON 对象
     */
    public JsonObject deserializeGeckoLibModelFile(ResourceLocation resourcePath, Resource resource) throws RuntimeException {
        return GeoJsonReader.read(resource, resourcePath);
    }

    /**
     * 读取磁盘上的动画 JSON。
     *
     * @param resourcePath 资源路径，仅用于日志与报错
     * @param resource     资源本体，字节可能来自整包读取器
     * @return 解析后的 JSON 对象
     */
    public JsonObject deserializeGeckoLibAnimationFile(ResourceLocation resourcePath, Resource resource) throws RuntimeException {
        return GeoJsonReader.read(resource, resourcePath);
    }

    /**
     * 读整包里的一条 geo / 动画资源。
     *
     * <p>与上面两个方法同一个出口（{@link GeoJsonReader}），所以解析规则与报错信息完全一致。
     *
     * @param resourcePath 资源路径，仅用于日志与报错
     * @param packed       整包里的条目字节
     * @return 解析后的 JSON 对象
     */
    public JsonObject readPacked(ResourceLocation resourcePath, byte[] packed) throws RuntimeException {
        return GeoJsonReader.read(null, packed, resourcePath);
    }

    /**
     * 把 geo 模型 JSON 烘培成骨骼模型。
     *
     * @param resourcePath 资源路径，用命名空间挑选对应的烘培工厂
     * @param json         模型文件根对象
     * @return 烘培好的模型
     */
    public BakedGeoModel bakeGeckoLibModelFile(ResourceLocation resourcePath, JsonObject json) {
        Model model = KeyFramesAdapter.GEO_GSON.fromJson(json, Model.class);
        return BakedModelFactory.getForNamespace(resourcePath.getNamespace())
                .constructGeoModel(GeometryTree.fromModel(model));
    }

    /**
     * 把动画 JSON 烘培成动画集。
     *
     * @param resourcePath 资源路径，仅用于日志与报错
     * @param json         动画文件根对象，动画条目在其 {@code animations} 成员下
     * @param mathParser   Molang 解析器，供调用方复用同一份变量注册表
     * @return 烘培好的动画集
     */
    public BakedAnimations bakeGeckoLibAnimationsFile(ResourceLocation resourcePath, JsonObject json, MathParser mathParser) {
        return KeyFramesAdapter.GEO_GSON.fromJson(GsonHelper.getAsJsonObject(json, "animations"), BakedAnimations.class);
    }
}
