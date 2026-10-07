package com.linweiyun.genshin.client.render.optimize;

import com.linweiyun.genshin.config.PerformanceConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jspecify.annotations.Nullable;

/**
 * 角色几何优化系统的开关入口。
 *
 * <h2>这一层解决什么</h2>
 * GeckoLib 5.5.6 的骨骼渲染路径是「每个骨骼 push/pop 一次 PoseStack、每个 cube 再 push/pop 一次」，
 * 并且在热路径上<b>每个顶点、每个法线、每次旋转都新建一个临时对象</b>：一个
 * 83 骨骼 / 204 cube / 1224 面 / 4896 顶点的角色模型，每帧光堆分配就有约 1.13 万次
 * （4896 个 {@code Vector4f} + 4896 个 {@code Vector3f} + 1224 个法线 {@code Vector3f}
 * + 287 个 {@code Quaternionf}）。攻击频率一高、场上角色一多，这些短命对象会持续喂给
 * GC，表现出来就是帧时间抖动和掉帧。
 *
 * <h2>三个互相独立的子项</h2>
 * <ul>
 *   <li>{@link #FLAG_GEO_PRECOMPILE}：一个 cube 的「绕自身轴心旋转」是常量，可以折进顶点表，
 *       每个模型只编译一次；之后每帧不再对 cube 做 push/translate/rotate/translate/pop。</li>
 *   <li>{@link #FLAG_ZERO_ALLOC_WALK}：骨骼旋转共用一个 {@code Quaternionf} 实例。</li>
 *   <li>{@link #FLAG_DIRECT_VERTEX}：顶点位置用内联矩阵乘法直接算出，不走
 *       {@code pose.transform(new Vector4f(...))}。</li>
 * </ul>
 * 三个开关都关掉（或总开关关掉）时 {@link #characterFlags()} 返回 {@code 0}，
 * 渲染器会原样回退到 GeckoLib 的默认实现 —— 所以这几项可以逐条开、逐条对照，
 * 也可以一键回到优化前的行为。
 *
 * <h2>为什么每帧读配置也不心疼</h2>
 * 一次 {@code ConfigValue#get()} 就是一次 {@code HashMap} 查找，纳秒量级；
 * 调用点是「每个渲染 pass 一次」（每个角色每帧一次），不是每个顶点一次。
 * 换成缓存 + 配置事件刷新反而会引入热重载不同步的风险，这里选了简单且永远最新的做法。
 */
public final class RenderOptimize {

    /** 使用预编译几何表（cube 变换已折进顶点）。 */
    public static final int FLAG_GEO_PRECOMPILE = 1;
    /** 骨骼旋转复用四元数实例。 */
    public static final int FLAG_ZERO_ALLOC_WALK = 1 << 1;
    /** 顶点位置直写（内联矩阵乘法）。 */
    public static final int FLAG_DIRECT_VERTEX = 1 << 2;

    /** 三个子项全开。 */
    public static final int FLAG_ALL = FLAG_GEO_PRECOMPILE | FLAG_ZERO_ALLOC_WALK | FLAG_DIRECT_VERTEX;

    private RenderOptimize() {
    }

    /** 角色几何优化总开关。 */
    public static boolean characterGeometryEnabled() {
        return flag(PerformanceConfig.RENDER_OPTIMIZE_CHARACTER, true);
    }

    /**
     * GPU 蒙皮开关，读配置项 {@code RENDER_OPTIMIZE_GPU_SKINNING}。
     *
     * @return 配置值；配置未就绪时返回 {@code true}
     */
    public static boolean gpuSkinningEnabled() {
        return flag(PerformanceConfig.RENDER_OPTIMIZE_GPU_SKINNING, true);
    }

    /**
     * 光影包生效时是否继续使用 GPU 蒙皮，读配置项
     * {@code RENDER_OPTIMIZE_GPU_SKINNING_UNDER_SHADERS}。
     *
     * @return 配置值；配置未就绪时返回 {@code false}
     */
    public static boolean gpuSkinningUnderShaders() {
        return flag(PerformanceConfig.RENDER_OPTIMIZE_GPU_SKINNING_UNDER_SHADERS, false);
    }

    /** F3 的 mg-render 读数是否显示。 */
    public static boolean statsEnabled() {
        return flag(PerformanceConfig.RENDER_OPTIMIZE_STATS, true);
    }

    /**
     * 本帧生效的子项位掩码。
     *
     * <p>返回 {@code 0} 表示「一项都没开」，渲染器据此直接走原路径。</p>
     */
    public static int characterFlags() {
        if (!characterGeometryEnabled()) {
            return 0;
        }

        int flags = 0;
        if (flag(PerformanceConfig.RENDER_OPTIMIZE_GEO_PRECOMPILE, true)) {
            flags |= FLAG_GEO_PRECOMPILE;
        }
        if (flag(PerformanceConfig.RENDER_OPTIMIZE_ZERO_ALLOC_WALK, true)) {
            flags |= FLAG_ZERO_ALLOC_WALK;
        }
        if (flag(PerformanceConfig.RENDER_OPTIMIZE_DIRECT_VERTEX, true)) {
            flags |= FLAG_DIRECT_VERTEX;
        }
        return flags;
    }

    /**
     * 读一个布尔配置，读不到就用默认值。
     *
     * <p>{@code value} 为 null 表示配置类还没注册（开发环境的极早期调用）；
     * {@code get()} 抛异常表示配置spec 还没加载完。两种都按默认值走，
     * 保证任何调用时机都不会因为「配置没好」而崩在渲染线程上。</p>
     */
    private static boolean flag(ModConfigSpec.@Nullable BooleanValue value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return value.get();
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
