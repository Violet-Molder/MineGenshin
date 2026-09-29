package com.linweiyun.genshin.mixin.compat;

import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.neoforged.fml.ModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * {@code minegenshin.iris.mixins.json} 的门禁：装了光影（Iris）才让这一份混入生效。
 *
 * <h2>软依赖是怎么做到「没装也不崩」的</h2>
 * 四道保险互相独立，任何一道单独成立都不会有问题：
 * <ol>
 *   <li>混入配置自己写 {@code "required": false} —— 目标类找不到时 Mixin 只记一条跳过，
 *       不会当成配置错误；</li>
 *   <li>混入类用 {@code @Mixin(targets = "net.irisshaders.iris.Iris")} 的<b>字符串</b>形态 +
 *       {@code @Pseudo}，配置里不出现任何 Iris 的类型引用，所以「本模组自己的类」在编译期与
 *       运行期都不需要 Iris 在场；</li>
 *   <li>这一层门禁：{@link #shouldApplyMixin} 在 Iris 不在场时直接返回 {@code false}，
 *       那一条混入连尝试注入都不会发生（也就不会在日志里留下「目标类找不到」的警告）；</li>
 *   <li>注入器一律 {@code require = 0}（配置里的 {@code injectors.defaultRequire}）：
 *       万一将来 Iris 改了方法名，底层只记一条警告 —— 丢的是这一项小优化，不是客户端。</li>
 * </ol>
 *
 * <p>探测结果只在首次使用时算一次：一次会话里装了就是装了。探测本身刻意做成
 * 「先按类存在性判断、再退到 NeoForge 的模组列表」，因为混入插件跑得比模组列表还早 ——
 * {@code ModList.get()} 在这个时点通常会抛，所以它只能是兜底，不能是主判据。</p>
 *
 * <p><b>探测本身绝不抛异常</b>：任何一步失败都按「没有 Iris」处理（代价是这一项优化不生效），
 * 而不是让启动流程在这里炸掉。</p>
 */
public class IrisMixinPlugin implements IMixinConfigPlugin {

    /** Iris 的 modid 与入口类。都只是字符串，不在配置里引用 Iris 的任何类型。 */
    private static final String IRIS_MOD_ID = "iris";
    private static final String IRIS_ENTRY_CLASS = "net.irisshaders.iris.Iris";
    private static final String CONFIG_NAME = "minegenshin.iris.mixins.json";

    private static boolean probed;
    private static boolean irisPresent;

    @Override
    public void onLoad(String mixinPackage) {
        probeOnce();
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        probeOnce();
        return irisPresent;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    /** 首次调用时探测一次并记一条日志；之后直接读缓存。探测与日志都不会抛出。 */
    private static void probeOnce() {
        if (probed) {
            return;
        }
        probed = true;

        try {
            irisPresent = detectIris();
        } catch (Throwable failure) {
            irisPresent = false;
        }

        try {
            if (irisPresent) {
                ModLog.getLogger(LogGroup.MIXIN).info("[MineGenshin] 检测到光影模组（{}）：启用 Iris 兼容混入（{}）",
                        IRIS_MOD_ID, CONFIG_NAME);
            } else {
                // 按 INFO 记一次：整合包作者看图就能确认「软依赖确实做了判断」，
                // 而不是「这条链路是不是根本没加载」。
                ModLog.getLogger(LogGroup.MIXIN).info("[MineGenshin] 未检测到光影模组（{}）：{} 整份跳过，其余功能不受影响",
                        IRIS_MOD_ID, CONFIG_NAME);
            }
        } catch (Throwable ignored) {
            // 日志系统在这个时点还没就绪也无所谓，探测结果已经拿到了
        }
    }

    /**
     * Iris 在不在场。
     *
     * <p>主判据是「类在不在类路径上」——混入配置本身是按 {@code [[mixins]]} 在模组扫描之后
     * 才加载的，所以这时 Iris 的类已经能解析到。两个类加载器都试一次，是因为混入插件自己
     * 由谁加载取决于运行环境；抛异常时直接换下一个。</p>
     */
    private static boolean detectIris() {
        final ClassLoader context = Thread.currentThread().getContextClassLoader();
        final ClassLoader own = IrisMixinPlugin.class.getClassLoader();

        if (classPresent(context)) {
            return true;
        }
        if (context != own && classPresent(own)) {
            return true;
        }
        return modListSaysIris();
    }

    private static boolean classPresent(ClassLoader loader) {
        if (loader == null) {
            return false;
        }
        try {
            // initialize = false：只查存在性，绝不在这里触发 Iris 的静态初始化
            Class.forName(IRIS_ENTRY_CLASS, false, loader);
            return true;
        } catch (Throwable absent) {
            return false;
        }
    }

    /**
     * 兜底：问 NeoForge 的模组列表。
     *
     * <p>单独一个方法是有意为之 —— {@code ModList} 这个类型万一在混入插件的类加载器里
     * 解析不到，校验错误会落在「调用这个方法」的那条指令上，从而被调用方的
     * {@code catch (Throwable)} 收住；写在同一个方法里就未必收得住了。</p>
     */
    private static boolean modListSaysIris() {
        try {
            final ModList mods = ModList.get();
            return mods != null && mods.isLoaded(IRIS_MOD_ID);
        } catch (Throwable ignored) {
            // 混入插件跑得比模组列表早，这里抛是正常现象
            return false;
        }
    }
}
