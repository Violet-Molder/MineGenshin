package com.linweiyun.genshin.asset.pack;

import com.geckolib.loading.loader.GeckoLibGsonLoader;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/**
 * GeckoLib 默认 Gson 加载器的「能读整包」版本。
 *
 * <p>只覆盖「读文件」两步，烘培（{@code bakeGeckoLibModelFile} /
 * {@code bakeGeckoLibAnimationsFile}）完全交给父类，因此
 * 「模型文件里塞了动画」「动画文件里塞了模型」这类校验、以及动画名 / 骨骼解析规则，
 * 都和 GeckoLib 原生一字不差。
 *
 * <p>两个来源<b>都支持</b>：
 * <ul>
 *   <li>磁盘文件（{@link #deserializeGeckoLibModelFile} / {@link #deserializeGeckoLibAnimationFile}）——
 *       字节先过 {@link GeoPackReaders} 找到的读取器；</li>
 *   <li>整包（{@link #readPacked}）—— 条目字节来自 {@link GeoPackSource}，直接解析。</li>
 * </ul>
 */
public class GenshinGsonLoader extends GeckoLibGsonLoader {

    @Override
    public JsonObject deserializeGeckoLibModelFile(Identifier resourcePath, Resource resource) throws RuntimeException {
        return GeoJsonReader.read(resource, resourcePath);
    }

    @Override
    public JsonObject deserializeGeckoLibAnimationFile(Identifier resourcePath, Resource resource) throws RuntimeException {
        return GeoJsonReader.read(resource, resourcePath);
    }

    /**
     * 读整包里的一条 geo / 动画资源。
     *
     * <p>与上面两个方法同一个出口（{@link GeoJsonReader}），所以解析规则与报错信息完全一致。
     */
    public JsonObject readPacked(Identifier resourcePath, byte[] packed) throws RuntimeException {
        return GeoJsonReader.read(null, packed, resourcePath);
    }
}
