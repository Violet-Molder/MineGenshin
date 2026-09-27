package com.linweiyun.genshin.core.asset.pack;

import com.geckolib.GeckoLibConstants;
import com.google.gson.JsonObject;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.google.gson.JsonParseException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 把 geo 资源读成 {@link JsonObject} —— 整包里的条目与磁盘上的文件走同一个出口。
 *
 * <h2>两个来源，一个出口</h2>
 * <ul>
 *   <li><b>磁盘上的文件</b>（{@link Resource}）：字节先过一遍 {@link GeoPackReaders}
 *       给出的读取器；</li>
 *   <li><b>整包</b>（{@link GeoPack}）：条目字节由 {@link GeoPackSource} 交付进来，
 *       这里直接解析。</li>
 * </ul>
 * 用哪一份由调用方决定：{@link GeoPackSource#usePacked} 是唯一判据
 * （同一个逻辑路径两边都有时<b>以整包里的条目为准</b>，对象目录里 {@code local/} 那一份例外）。
 * 本方法只管「给你哪份字节，就按同一套规则解析」，自身不做来源判断。
 *
 * <h2>和 GeckoLib 的关系</h2>
 * GeckoLib 的 {@code GeckoLibGsonLoader#readResourceAsJson} 读的是
 * {@code resource.openAsReader()}，而 {@code Resource#openAsReader()} 就是
 * 「UTF-8 解流的 BufferedReader」。这里让字节先过读取器、再走同一条路
 * （字节 → UTF-8 → {@link GsonHelper#parse(Reader)}），所以解析行为与 GeckoLib 自己
 * 读文件时<b>逐字节一致</b>，不存在两套 JSON 规则。
 *
 * <h2>它是读取路径上的一环</h2>
 * 所有读 geo / 动画 JSON 的地方都经这里：
 * <ul>
 *   <li>拿得到读取器 → {@link GeoPackReaders} 给出实现，整包里的条目照常读出来；</li>
 *   <li>拿不到 → {@link GeoPackSource} 连包都读不出来，扫描结果为空，
 *       日志会明说「没有读取器」，而不是静默变成别的问题。</li>
 * </ul>
 *
 * <h2>为什么先整段读进来</h2>
 * 要先看过文件内容才知道该怎么处理，读全量最省事；geo 模型 / 动画都在几百 KB 量级，
 * 而 JSON 解析本来也要把整份内容读进内存。
 */
public final class GeoJsonReader {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CORE);

    /** 读取器的来源只提示一次，避免每次资源重载都刷屏。 */
    private static final AtomicBoolean MODE_LOGGED = new AtomicBoolean();

    private GeoJsonReader() {
    }

    /**
     * 读一个 geo / 动画 JSON 资源。
     *
     * @param resource 资源本体
     * @param id       资源路径，仅用于报错定位，可为 null
     */
    public static JsonObject read(@Nullable Resource resource, @Nullable Identifier id) throws RuntimeException {
        return read(resource, null, id);
    }

    /**
     * 读一个 geo / 动画 JSON 资源，磁盘与整包两个来源二选一。
     *
     * @param resource 磁盘上的资源；包内条目传 null
     * @param packed   整包里的条目字节；磁盘资源存在时忽略
     * @param id       资源路径，仅用于报错定位，可为 null
     */
    public static JsonObject read(@Nullable Resource resource, @Nullable byte[] packed,
                                  @Nullable Identifier id) throws RuntimeException {
        logModeOnce();

        byte[] json;
        if (resource != null) {
            byte[] data = readBytes(resource, id);
            json = GeoPackReaders.get().read(data, id == null ? null : id.toString());
        } else if (packed != null) {
            json = packed;
        } else {
            throw GeckoLibConstants.exception(id,
                    "读不到 geo 资源：磁盘上没有文件，包里也没有这条条目");
        }

        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(json), StandardCharsets.UTF_8)) {
            return GsonHelper.parse(reader);
        } catch (IOException e) {
            // GsonHelper.parse 只在底层流出错时抛 IOException；内存流理论上到不了这里，留着兜底。
            throw GeckoLibConstants.exception(id, "Error reading JSON file", e);
        } catch (JsonParseException e) {
            if (GeoPackReaders.isBaseline()) {
                throw GeckoLibConstants.exception(id,
                        "这条 geo 资源是整包里的条目，但当前构建没有读取器，读不出来", e);
            }
            throw e;
        }
    }

    /** 读原始字节（不过读取器）；给「判类型」这类只需要字节的场景用。 */
    public static byte[] readBytes(Resource resource, @Nullable Identifier id) throws RuntimeException {
        try (InputStream in = resource.open()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw GeckoLibConstants.exception(id, "Error reading file", e);
        }
    }

    private static void logModeOnce() {
        if (!MODE_LOGGED.compareAndSet(false, true)) {
            return;
        }
        if (GeoPackReaders.isBaseline()) {
            LOGGER.warn("[GeoJsonReader] 当前构建没有整包读取器，geo 资源按文件原样直读；"
                    + "包里的模型 / 动画将不会加载");
        } else {
            LOGGER.info("[GeoJsonReader] 整包读取器已启用");
        }
    }
}
