package com.linweiyun.genshin.core.asset.pack;

import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import com.linweiyun.genshin.core.asset.ModAssetPaths;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 把 {@link GeoPack} 从资源管理器里读出来 —— 「包内 geo 资源」的唯一入口。
 *
 * <h2>为什么走资源管理器，不走 classpath</h2>
 * 整包文件放在 {@code assets/minegenshin/} 下，用 {@code ResourceManager} 读就与其它资源
 * 完全同一条通道：开发期（{@code build/resources/main}）、正式 jar、资源包覆盖、
 * 资源重载（F3+T）全都自动生效，不需要额外猜类加载器的行为。
 *
 * <h2>它对外只提供「逻辑路径 → 条目字节」</h2>
 * 缓存扫描拿到的是「磁盘上有什么」+「包内有什么」两份清单，两边同名时用哪一份由
 * {@link #usePacked} 裁定：<b>整包里的条目优先</b>，对象目录里的 {@code local/} 那一份
 * 例外（它永远走磁盘，是唯一能在不改包的情况下覆盖同名条目的入口）。
 * 存在性判断（{@link #contains}）兼顾两边。
 *
 * <h2>缓存策略</h2>
 * 只记住「上一次用的那个 {@link ResourceManager}」的结果：同一个管理器（同一次资源加载）
 * 不会重复处理这 MB 级的包；换成新管理器（资源重载）就重新读一次。没有全局可变状态，
 * 所以客户端与集成服务端各自持有不同管理器时也不会互相把对方的结果冲掉。
 *
 * <p>读不出来时一律返回空表并<b>只提示一次</b>，绝不抛异常 ——
 * 拿不到读取器的构建里读不出来是预期行为。
 */
public final class GeoPackSource {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CORE);

    /** 与 {@code Minegenshin.MOD_ID} 一致；这里不引 mod 主类，保持本包只依赖 JDK + Minecraft 基础类。 */
    private static final String NAMESPACE = "minegenshin";

    /** 整包文件的位置：{@code minegenshin:geo/georesources.minegenshin}。 */
    private static final Identifier PACK_ID =
            Identifier.fromNamespaceAndPath(NAMESPACE, GeoPack.RESOURCE_PATH);

    /** 一次性提示：读不出来时说明原因，别在每次扫描时刷屏。 */
    private static volatile boolean explained;

    /**
     * 见过的资源管理器个数。启动早期（模组构造阶段）会先拿到一个还没有本 MOD 资源的
     * 资源管理器，此时「找不到整包文件」是正常的，不能据此报错；等真正的资源加载
     * 之后仍然找不到，才是真的缺文件。
     */
    private static volatile int managersSeen;

    private static volatile ResourceManager lastManager;
    private static volatile Map<Identifier, byte[]> lastEntries = Map.of();

    private GeoPackSource() {
    }

    // ==================== 查询 ====================

    /** 包内全部 geo 资源：逻辑路径（带 {@code .geo.json} / {@code .animation.json}）→ 条目字节。 */
    public static Map<Identifier, byte[]> entries(@Nullable ResourceManager manager) {
        if (manager == null) {
            return Map.of();
        }
        if (manager == lastManager) {
            return lastEntries;
        }
        synchronized (GeoPackSource.class) {
            if (manager == lastManager) {
                return lastEntries;
            }
            Map<Identifier, byte[]> loaded = load(manager);
            lastEntries = loaded;
            lastManager = manager;
            return loaded;
        }
    }

    /** 包内有没有这条逻辑路径（给「这个 id 有没有模型 / 动画」这类存在性判断用）。 */
    public static boolean contains(@Nullable ResourceManager manager, @Nullable Identifier filePath) {
        return filePath != null && entries(manager).containsKey(filePath);
    }

    /** 当前环境里能读到几条包内资源；0 表示拿不到读取器、或者没有包。 */
    public static int size(@Nullable ResourceManager manager) {
        return entries(manager).size();
    }

    /**
     * 同一个逻辑路径两边都有时，用哪一份 —— 判据只有这一处，缓存扫描都问这里。
     *
     * <ul>
     *   <li>整包里有这条条目 → <b>用整包那份</b>。磁盘上同名位置另有一份随包副本，
     *       两份内容并不等价，以磁盘为准会把资源读错；</li>
     *   <li>路径落在对象目录的 {@code local/} 里 → <b>用磁盘那份</b>。这类文件从来不进包，
     *       是「明文直读、当场改当场看」的通道，也是覆盖同名条目的唯一入口；</li>
     *   <li>整包里没有这条条目 → 用磁盘那份（包没跟着更新、或这个文件本来就不进包）。
     * </ul>
     *
     * @param raw       逻辑路径（资源位置，带扩展名）
     * @param available 整包里有没有这条条目
     * @return true = 用整包里那条；false = 用磁盘上那个文件
     */
    public static boolean usePacked(@Nullable Identifier raw, boolean available) {
        if (!available || raw == null) {
            return false;
        }
        return !ModAssetPaths.isLocalFile(raw);
    }

    // ==================== 读取 ====================

    private static Map<Identifier, byte[]> load(ResourceManager manager) {
        int seen = managersSeen;
        managersSeen = seen + 1;

        Optional<Resource> found = manager.getResource(PACK_ID);
        if (found.isEmpty()) {
            if (seen == 0) {
                // 启动早期还没走到资源加载，这个管理器里本来就没有本 MOD 的资源。
                LOGGER.debug("[GeoPackSource] 启动早期的资源管理器里还没有整包文件，等资源加载后再读");
            } else {
                explainOnce("资源管理器里找不到整包文件（" + PACK_ID + "）："
                        + "本次构建没有打包内置资源；模型 / 动画将不会显示");
            }
            return Map.of();
        }

        byte[] raw;
        try (InputStream in = found.get().open()) {
            raw = in.readAllBytes();
        } catch (IOException e) {
            explainOnce("读取整包文件失败：" + PACK_ID, e);
            return Map.of();
        }

        Map<String, byte[]> items;
        try {
            items = GeoPack.load(raw, GeoPack.RESOURCE_PATH);
        } catch (IllegalStateException e) {
            explainOnce("整包文件内容残缺（被截断或改动过）：" + PACK_ID, e);
            return Map.of();
        }
        if (items == null) {
            if (GeoPackReaders.isBaseline()) {
                explainOnce("这个整包需要读取器才能打开，当前环境没有读取器，读不出来（属预期）");
            } else {
                explainOnce("整包打不开：内容对不上：" + PACK_ID);
            }
            return Map.of();
        }

        Map<Identifier, byte[]> entries = new LinkedHashMap<>(Math.max(4, items.size() * 2));
        long bytes = 0L;
        for (Map.Entry<String, byte[]> item : items.entrySet()) {
            entries.put(Identifier.fromNamespaceAndPath(NAMESPACE, item.getKey()), item.getValue());
            bytes += item.getValue().length;
        }

        LOGGER.info("[GeoPackSource] 已读入整包：{} 条资源（共 {} 字节）", entries.size(), bytes);
        return Map.copyOf(entries);
    }

    private static void explainOnce(String message) {
        if (explained) {
            return;
        }
        explained = true;
        LOGGER.warn("[GeoPackSource] {}", message);
    }

    private static void explainOnce(String message, Throwable cause) {
        if (explained) {
            return;
        }
        explained = true;
        LOGGER.warn("[GeoPackSource] {}", message, cause);
    }
}
