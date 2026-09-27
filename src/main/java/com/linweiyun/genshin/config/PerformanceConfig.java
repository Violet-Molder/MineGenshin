package com.linweiyun.genshin.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 性能优化系统的配置（{@code minegenshin/performance.toml}）。
 *
 * <p>分两块，对应两个优化模块：</p>
 * <ul>
 *   <li><b>damage-number</b>（计算侧）—— 飘字生成时的合并 / 节流；</li>
 *   <li><b>indicator-render</b>（渲染侧）—— 每帧能看到几条、要不要剔除、缓存多大。</li>
 * </ul>
 *
 * <p>全部默认开启，且都只在「攻击频率高到会卡」的场景里才会改变观感；
 * 正常节奏的战斗里这些参数几乎不会被触发。</p>
 */
public class PerformanceConfig {

    // ===================== 计算侧：飘字生成 =====================

    /** 飘字生成侧的合并总开关 */
    public static ModConfigSpec.BooleanValue DAMAGE_NUMBER_MERGE;
    /** 同一目标、同一配色的伤害数字，间隔不超过该毫秒数就并进同一条数字里（累加） */
    public static ModConfigSpec.IntValue DAMAGE_NUMBER_MERGE_GAP_MS;
    /** 洪水阈值：同一目标在该窗口内超过这么多条飘字后，即使间隔变大也继续并入同一条 */
    public static ModConfigSpec.IntValue DAMAGE_NUMBER_FLOOD_WINDOW_MS;
    public static ModConfigSpec.IntValue DAMAGE_NUMBER_FLOOD_COUNT;

    // ===================== 渲染侧：飘字呈现 =====================

    /** 单帧最多提交多少条飘字（按离相机由近到远保留） */
    public static ModConfigSpec.IntValue MAX_RENDERED_INDICATORS;
    /** 相机背后的飘字直接不提交（半空间剔除） */
    public static ModConfigSpec.BooleanValue CULL_BEHIND_CAMERA;
    /** 字形几何缓存条数（文字→字形，命中后不再走字体排版） */
    public static ModConfigSpec.IntValue GLYPH_CACHE_SIZE;
    /** 同一 RenderType 的多条飘字合并成一次几何提交 */
    public static ModConfigSpec.BooleanValue BATCH_SUBMIT;
    /** 飘字要不要画阴影（关掉能省掉一半的顶点写入） */
    public static ModConfigSpec.BooleanValue SHADOW;
    /** 飘字阴影的右下偏移量（字体像素）。0 = 贴在文字底下，1 = 原版阴影的距离 */
    public static ModConfigSpec.DoubleValue SHADOW_OFFSET;

    // ===================== 渲染侧：角色几何 =====================

    /** 角色几何优化总开关（关掉 = 完全回到 GeckoLib 原路径） */
    public static ModConfigSpec.BooleanValue RENDER_OPTIMIZE_CHARACTER;
    /** 几何预编译：把每个 cube 的 pivot/旋转折进顶点表，只在模型变化时重编一次 */
    public static ModConfigSpec.BooleanValue RENDER_OPTIMIZE_GEO_PRECOMPILE;
    /** 零分配骨骼遍历：复用一个四元数，不再每个骨骼 new 一个 */
    public static ModConfigSpec.BooleanValue RENDER_OPTIMIZE_ZERO_ALLOC_WALK;
    /** 顶点直写：手写矩阵乘法，不再每顶点 new Vector4f */
    public static ModConfigSpec.BooleanValue RENDER_OPTIMIZE_DIRECT_VERTEX;
    /** GPU 蒙皮：顶点常驻显存，每帧只上传骨骼矩阵 */
    public static ModConfigSpec.BooleanValue RENDER_OPTIMIZE_GPU_SKINNING;
    /** 光影包生效时是否仍然使用 GPU 蒙皮（默认否，只作排查用） */
    public static ModConfigSpec.BooleanValue RENDER_OPTIMIZE_GPU_SKINNING_UNDER_SHADERS;
    /** F3 的 mg-render 读数 */
    public static ModConfigSpec.BooleanValue RENDER_OPTIMIZE_STATS;

    // ===================== 计算侧：热路径日志 =====================

    /** 热路径日志节流总开关（详见 {@code core.system.performance.HotPathLog}） */
    public static ModConfigSpec.BooleanValue HOT_PATH_LOG_THROTTLE;
    /** 同一个调用点每秒钟最多写几条日志，多出来的压住并给一条汇总 */
    public static ModConfigSpec.IntValue HOT_PATH_LOG_MAX_PER_SECOND;

    static void register(ModConfigSpec.Builder builder) {
        builder.push("damage-number");
        DAMAGE_NUMBER_MERGE = builder
                .translation("minegenshin.configuration.performance.damage_number_merge")
                .comment("把密集打出的伤害数字合并成一条累加数字（只影响观感，不影响伤害结算）")
                .define("merge", true);
        DAMAGE_NUMBER_MERGE_GAP_MS = builder
                .translation("minegenshin.configuration.performance.damage_number_merge_gap_ms")
                .comment("同一目标同配色的伤害数字间隔小于该值（毫秒）时并入同一条；调小=更少合并")
                .defineInRange("merge_gap_ms", 140, 0, 2000);
        DAMAGE_NUMBER_FLOOD_WINDOW_MS = builder
                .translation("minegenshin.configuration.performance.damage_number_flood_window_ms")
                .comment("洪水判定窗口（毫秒）")
                .defineInRange("flood_window_ms", 500, 50, 5000);
        DAMAGE_NUMBER_FLOOD_COUNT = builder
                .translation("minegenshin.configuration.performance.damage_number_flood_count")
                .comment("同一目标在该窗口内超过这么多条飘字后，之后的高频飘字一律并入同一条")
                .defineInRange("flood_count", 6, 1, 64);
        builder.pop();

        builder.push("indicator-render");
        MAX_RENDERED_INDICATORS = builder
                .translation("minegenshin.configuration.performance.max_rendered_indicators")
                .comment("单帧最多提交的飘字条数；超出的按距离从远到近丢弃")
                .defineInRange("max_rendered_indicators", 128, 8, 512);
        CULL_BEHIND_CAMERA = builder
                .translation("minegenshin.configuration.performance.cull_behind_camera")
                .comment("相机背后的飘字不提交几何")
                .define("cull_behind_camera", true);
        GLYPH_CACHE_SIZE = builder
                .translation("minegenshin.configuration.performance.glyph_cache_size")
                .comment("字形几何缓存条数（不同文字各占一条）")
                .defineInRange("glyph_cache_size", 512, 16, 4096);
        BATCH_SUBMIT = builder
                .translation("minegenshin.configuration.performance.batch_submit")
                .comment("同 RenderType 的飘字合并成一次几何提交")
                .define("batch_submit", true);
        SHADOW = builder
                .translation("minegenshin.configuration.performance.shadow")
                .comment("给飘字画一道右下方阴影。关掉少写一半顶点（每条飘字现在要发两趟几何：阴影趟 + 正文趟），"
                        + "代价是数字压在雪地/天空这类浅色背景上时略微难读")
                .define("shadow", true);
        SHADOW_OFFSET = builder
                .translation("minegenshin.configuration.performance.shadow_offset")
                .comment("阴影相对文字往右下偏多少，单位是「字体像素」——也就是跟着飘字大小等比缩放的量。"
                        + "0 = 阴影被正文字压住（等于看不见），1 = 与香草文字阴影同距。"
                        + "飘字在世界空间里比原版 HUD 的同类文字大（约 1.36 倍命名牌），"
                        + "同一个 1 像素在屏幕上会更显眼，所以默认取一半")
                .defineInRange("shadow_offset", 0.5D, 0.0D, 2.0D);
        builder.pop();

        builder.push("render-optimize");
        RENDER_OPTIMIZE_CHARACTER = builder
                .translation("minegenshin.configuration.performance.render_optimize_character")
                .comment("角色几何优化的总开关。关掉就完全走 GeckoLib 原路径，用于和优化路径做同场景对照")
                .define("character-geometry", true);
        RENDER_OPTIMIZE_GEO_PRECOMPILE = builder
                .translation("minegenshin.configuration.performance.render_optimize_geo_precompile")
                .comment("几何预编译：把一个 cube 的「绕自身轴心旋转」折进顶点表，每个模型只编译一次。"
                        + "关掉则每帧现算 cube 变换（更接近原实现，也更慢）")
                .define("geo-precompile", true);
        RENDER_OPTIMIZE_ZERO_ALLOC_WALK = builder
                .translation("minegenshin.configuration.performance.render_optimize_zero_alloc_walk")
                .comment("骨骼旋转复用一个四元数实例，不再每根骨骼 new 一个（83 骨的模型每帧省 83 次分配）")
                .define("zero-alloc-walk", true);
        RENDER_OPTIMIZE_DIRECT_VERTEX = builder
                .translation("minegenshin.configuration.performance.render_optimize_direct_vertex")
                .comment("顶点位置用内联矩阵乘法直接算，不再每顶点 new Vector4f"
                        + "（4896 顶点的模型每帧省 4896 次分配）")
                .define("direct-vertex", true);
        RENDER_OPTIMIZE_STATS = builder
                .translation("minegenshin.configuration.performance.render_optimize_stats")
                .comment("在 F3 调试屏显示 mg-render 一行：本帧优化路径渲染了几个模型、耗时、顶点数")
                .define("debug-stats", true);
        RENDER_OPTIMIZE_GPU_SKINNING = builder
                .translation("minegenshin.configuration.performance.render_optimize_gpu_skinning")
                .comment("GPU 蒙皮：顶点常驻显存，每帧只上传每根骨骼的矩阵（83 根骨骼 vs 4896 个顶点）。"
                        + "默认开启；与光影或其它渲染模组出现兼容问题时可以关掉，关掉即完全回到 CPU 蒙皮路径")
                .define("gpu-skinning", true);
        RENDER_OPTIMIZE_GPU_SKINNING_UNDER_SHADERS = builder
                .translation("minegenshin.configuration.performance.render_optimize_gpu_skinning_under_shaders")
                .comment("光影包生效时是否仍然用 GPU 蒙皮。默认关闭：光影是按「程序身份」选着色器的，"
                        + "GPU 蒙皮自带的顶点格式会被交给不认识这份布局的兜底程序去读（模型消失、周围散落黑块），"
                        + "所以检测到光影包时自动让位给 CPU 蒙皮。打开它只用于排查「问题是不是出在这条路径上」")
                .define("gpu-skinning-under-shaders", false);
        builder.pop();

        builder.push("logging");
        HOT_PATH_LOG_THROTTLE = builder
                .translation("minegenshin.configuration.performance.hot_path_log_throttle")
                .comment("对「每次伤害 / 每次反应结算」都会写的日志做限频，避免高频攻击时主线程等磁盘 I/O")
                .define("hot_path_log_throttle", true);
        HOT_PATH_LOG_MAX_PER_SECOND = builder
                .translation("minegenshin.configuration.performance.hot_path_log_max_per_second")
                .comment("同一个日志调用点每秒钟最多输出几条；被压住的条数会在下一秒用一条汇总补出来")
                .defineInRange("hot_path_max_per_second", 8, 1, 200);
        builder.pop();
    }
}
