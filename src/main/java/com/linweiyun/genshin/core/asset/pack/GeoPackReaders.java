package com.linweiyun.genshin.core.asset.pack;

import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Stream;

/**
 * 找当前运行环境里的「整包读取器」：第一个能用的就用，一个都没有就退回 {@link Baseline}。
 *
 * <h2>两种环境都成立</h2>
 * <ul>
 *   <li><b>有读取器</b>：实现由 {@link ServiceLoader} 找到，包里的模型 / 动画正常读出来；</li>
 *   <li><b>没有读取器</b>：退回 {@link Baseline} —— 单条资源原样直读、整包读不出来。
 *       于是包里的资源显示不出来，但构建与启动都不会崩。</li>
 * </ul>
 *
 * <h2>读取器从哪来</h2>
 * 按顺序找，第一个能用的就用：
 * <ol>
 *   <li>已经在 classpath 上的实现（开发期把读取器直接挂进 classpath 的场景）；</li>
 *   <li>读取器目录里的 {@code *.jar}：
 *       <ul>
 *         <li>系统属性 {@code -Dminegenshin.plugins.dir=<目录>[;目录…]} 指定的目录；</li>
 *         <li>否则是游戏目录下的 {@code config/minegenshin/plugins/}。</li>
 *       </ul>
 *       jar 用<b>独立类加载器</b>加载，主 jar 不依赖它，删掉也不影响主程序。</li>
 * </ol>
 *
 * <p>判断只做一次并缓存；{@link #reload()} 供调试时重新找（换读取器不用重启游戏）。
 * 本类在目录不可达时安静地退回直读 —— 找不到读取器是正常情况，不是错误。
 */
public final class GeoPackReaders {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CORE);

    /** 读取器目录的系统属性（多个目录用 {@code ;} 或 {@code ,} 分隔）。 */
    public static final String READER_DIR_PROPERTY = "minegenshin.plugins.dir";

    /** 游戏目录下的默认读取器位置：{@code config/minegenshin/plugins}。 */
    private static final String[] READER_DIR_PARTS = {"config", "minegenshin", "plugins"};

    private static final String JAR_SUFFIX = ".jar";

    /** 一个读取器都没有时用的直读实现。 */
    private static final GeoPackReader BASELINE = new Baseline();

    private static volatile GeoPackReader resolved;

    /** 读取器类加载器要一直留着：实现类活在它里面，关掉就再也加载不了了。 */
    private static volatile URLClassLoader readerLoader;

    private GeoPackReaders() {
    }

    /** 当前生效的读取器；只会解析一次。 */
    public static GeoPackReader get() {
        GeoPackReader local = resolved;
        if (local != null) {
            return local;
        }
        synchronized (GeoPackReaders.class) {
            if (resolved == null) {
                resolved = resolve();
            }
            return resolved;
        }
    }

    /** 是不是「没有读取器、只能原样直读」的状态；给报错信息用。 */
    public static boolean isBaseline() {
        return get() == BASELINE;
    }

    /** 重新找一次读取器（调试用；换读取器不用重启游戏）。 */
    public static void reload() {
        synchronized (GeoPackReaders.class) {
            resolved = null;
            URLClassLoader previous = readerLoader;
            readerLoader = null;
            if (previous != null) {
                try {
                    previous.close();
                } catch (IOException e) {
                    LOGGER.debug("[GeoPackReaders] 关闭旧的读取器加载器失败", e);
                }
            }
            resolved = resolve();
        }
    }

    // ==================== 查找 ====================

    private static GeoPackReader resolve() {
        GeoPackReader onClasspath = firstService(GeoPackReaders.class.getClassLoader());
        if (onClasspath != null) {
            LOGGER.info("[GeoPackReaders] 整包读取器已启用（来自 classpath）");
            return onClasspath;
        }

        for (Path directory : readerDirectories()) {
            GeoPackReader fromDirectory = fromDirectory(directory);
            if (fromDirectory != null) {
                LOGGER.info("[GeoPackReaders] 整包读取器已启用（来自 {}）", directory);
                return fromDirectory;
            }
        }

        return BASELINE;
    }

    /** 读取器目录列表：先看系统属性，再看游戏目录下的默认位置。 */
    private static List<Path> readerDirectories() {
        List<Path> directories = new ArrayList<>(2);

        String configured = System.getProperty(READER_DIR_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            for (String part : configured.split("[;,]")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    directories.add(Path.of(trimmed));
                }
            }
            return directories;
        }

        Path gameDirectory = gameDirectory();
        if (gameDirectory != null) {
            directories.add(gameDirectory.resolve(String.join("/", READER_DIR_PARTS)));
        }
        return directories;
    }

    @Nullable
    private static Path gameDirectory() {
        try {
            return FMLPaths.GAMEDIR.get();
        } catch (Throwable e) {
            // 不在游戏进程里（构建期工具、自检）就会走到这：没有目录也算正常。
            return null;
        }
    }

    @Nullable
    private static GeoPackReader fromDirectory(Path directory) {
        if (!Files.isDirectory(directory)) {
            return null;
        }
        List<URL> jars = new ArrayList<>();
        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(JAR_SUFFIX))
                    .sorted()
                    .forEach(path -> {
                        try {
                            jars.add(path.toUri().toURL());
                        } catch (Exception e) {
                            LOGGER.warn("[GeoPackReaders] 读取读取器失败：{}", path, e);
                        }
                    });
        } catch (IOException e) {
            LOGGER.warn("[GeoPackReaders] 读取器目录读不了：{}", directory, e);
            return null;
        }
        if (jars.isEmpty()) {
            return null;
        }

        URLClassLoader loader = new URLClassLoader(
                jars.toArray(URL[]::new), GeoPackReaders.class.getClassLoader());
        GeoPackReader reader = firstService(loader);
        if (reader == null) {
            try {
                loader.close();
            } catch (IOException e) {
                LOGGER.debug("[GeoPackReaders] 关闭没用上的读取器加载器失败", e);
            }
            return null;
        }
        readerLoader = loader;
        return reader;
    }

    @Nullable
    private static GeoPackReader firstService(@Nullable ClassLoader loader) {
        if (loader == null) {
            return null;
        }
        try {
            for (GeoPackReader reader : ServiceLoader.load(GeoPackReader.class, loader)) {
                if (reader != null) {
                    return reader;
                }
            }
        } catch (Throwable e) {
            // 读取器坏了就当没有读取器：主程序照常跑，包里的资源读不出来而已。
            LOGGER.warn("[GeoPackReaders] 读取器加载失败，按「没有读取器」处理", e);
        }
        return null;
    }

    /** 没有读取器时的兜底实现，见 {@link #BASELINE}。 */
    private static final class Baseline implements GeoPackReader {

        @Override
        public byte[] read(byte[] raw, @Nullable String where) {
            return raw;
        }

        @Override
        @Nullable
        public Map<String, byte[]> open(byte[] raw, @Nullable String where) {
            return null;
        }
    }
}
