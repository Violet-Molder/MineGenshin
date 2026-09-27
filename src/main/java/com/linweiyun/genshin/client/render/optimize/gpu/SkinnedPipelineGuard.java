package com.linweiyun.genshin.client.render.optimize.gpu;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.client.render.optimize.RenderOptimize;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import java.lang.reflect.Method;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.ModList;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * GPU 蒙皮「这次能不能画」的环境自检。
 *
 * <h2>为什么必须有这一层</h2>
 * GPU 蒙皮与 CPU 蒙皮有一个本质区别：<b>CPU 路径把顶点写进原版自己的顶点缓冲</b>
 * （走 {@code VertexConsumer#addVertex}，与 GeckoLib 原路径逐字节相同，缓冲布局由原版决定，
 * 谁改了格式都不会错位）；而 <b>GPU 路径自带一份顶点格式和一条派生管线</b> ——
 * 顶点缓冲是我们按 32 字节/顶点自己打包上传的，画的时候必须由<b>同一条管线</b>按<b>同一份布局</b>去读。
 *
 * <p>光影模组（Iris 一类）会按<b>程序身份</b>选定着色器：它把「顶点格式身份 → 着色器程序」做成一张
 * 固定映射表（{@code ShaderKey}），只认原版那几个格式常量，<b>认不出的格式一律落进通用兜底程序</b>。
 * 我们那条派生管线声明的格式是我们自己的 32 字节布局，光影包明显不认识 —— 于是它选出来的那条程序
 * <b>不是我们的 {@code entity_skinned.vsh}</b>：实际执行的那条着色器按它自己的约定去读我们的顶点缓冲，
 * 位置就从「UV / 骨骼编号 / 法线修正掩码」这些字段上取到一些小整数，画出来是
 * <b>散布在角色周围、有的坐标看着很远、颜色发黑</b>的三角形，而模型本体因为顶点全错位而看不见。
 * 这就是「光影下模型消失 + 满屏黑块」的成因，也是 GPU 路径在光影下必须让位的理由。</p>
 *
 * <p>所以这里做两件事，任一命中就<b>让 GPU 蒙皮这一帧让位给 CPU 蒙皮</b>（CPU 路径照常优化，
 * 只是不再把顶点搬进显存）：</p>
 * <ol>
 *   <li><b>光影包在用</b>：通过 Iris 的稳定 API（反射调用，本模组不依赖 Iris）读
 *       {@code isShaderPackInUse()}。装了 Iris 但 API 读不到时按「有光影」处理 ——
 *       宁可少一项优化，也不要画出错位的顶点。平时每秒最多真探一次；光影包换包 / 开关时由
 *       软依赖混入（{@code minegenshin.iris.mixins.json}）立刻把缓存作废，
 *       见 {@link #invalidateShaderState()}。</li>
 *   <li><b>管线自检</b>：画之前核对「基管线的顶点格式还是原版 entity 格式」「派生管线的顶点格式
 *       还是我们自己的 {@link SkinnedMesh#FORMAT}」「派生管线的顶点着色器还是我们的」。
 *       这三条只要有一条不成立，就说明管线被外部改过，一律回退。这一条不认模组名字，
 *       对任何动过管线的模组都成立。</li>
 * </ol>
 *
 * <p><b>不涉及绘制正确性的东西一律不管</b>：失败只影响这一项可选优化，绝不抛异常、绝不影响
 * GeckoLib 模型本身的渲染（回退后走的是 CPU 路径）。</p>
 */
public final class SkinnedPipelineGuard {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);

    /** Iris 的稳定 API（v0）。反射调用：没装光影时这里就是一次 {@code ClassNotFoundException}。 */
    private static final String IRIS_API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";
    private static final String IRIS_MOD_ID = "iris";
    /** 光影包开 / 关要能在秒级生效，又不值得每帧反射一次，所以一秒探一次。 */
    private static final long SHADER_POLL_INTERVAL_NANOS = 1_000_000_000L;

    /**
     * 我们派生管线时假定的基管线顶点格式 —— {@code DefaultVertexFormat.ENTITY} 的<b>字面量副本</b>。
     *
     * <p>刻意不复用 {@code DefaultVertexFormat.ENTITY} 常量：那个常量本身就可能被光影模组换掉，
     * 用它来对比等于拿被改过的答案去对账。这里按名字 / 格式 / 顺序重建一份，
     * 一旦运行时的格式与它不等（多出 {@code mc_Entity} 之类），自检立刻不通过。</p>
     */
    private static final VertexFormat EXPECTED_ENTITY_FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("UV1", GpuFormat.RG16_SINT)
            .addAttribute("UV2", GpuFormat.RG16_SINT)
            .addAttribute("Normal", GpuFormat.RGBA8_SNORM)
            .build();

    /** 我们的顶点着色器（见 {@code SkinnedPipelines#derive}）。 */
    private static final Identifier SKINNED_VERTEX_SHADER = Minegenshin.id("core/entity_skinned");

    /** 光影模组是否加载；{@code null} = 还没问到（ModList 没就绪时不缓存，下一轮再问）。 */
    private static @Nullable Boolean irisLoaded;
    private static boolean irisApiProbed;
    private static @Nullable Object irisApiInstance;
    private static @Nullable Method shaderPackMethod;

    private static boolean shaderPackInUse;
    private static long nextShaderPollNanos;

    /** 自检发现管线被外部改过（只在被问到、且真的在尝试走 GPU 路径时才置位）。 */
    private static boolean pipelineMismatch;

    private static boolean shaderPackWarned;
    private static boolean probeWarned;
    private static boolean pipelineWarned;

    private SkinnedPipelineGuard() {
    }

    /**
     * 这一帧允许不允许用 GPU 蒙皮。
     *
     * <p>调用点在 {@link SkinnedPipelines#skinnedFor}：每帧每个模型一次（不是每个顶点一次），
     * 自检本身是两次小 Map 比较 + 一次 {@code System.nanoTime()}，可以忽略。</p>
     *
     * @param base    该 RenderType 平时用的原版 entity 管线
     * @param derived 我们派生的 skinned 管线
     * @return {@code false} = 调用方必须回退 CPU 蒙皮
     */
    public static boolean allows(RenderPipeline base, RenderPipeline derived) {
        if (shaderPackInUse() && !RenderOptimize.gpuSkinningUnderShaders()) {
            if (!shaderPackWarned) {
                shaderPackWarned = true;
                LOGGER.info("[RenderOptimize] 检测到光影包正在生效：GPU 蒙皮自动让位给 CPU 蒙皮。"
                        + "光影模组是按「顶点格式身份 → 着色器程序」的固定映射挑程序的，只认原版那几个格式常量；"
                        + "它认不出我们这条派生管线的自有格式，于是实际执行的程序不是我们的 entity_skinned 顶点着色器"
                        + "（表现为模型消失、周围散落黑块）。想让 GPU 蒙皮强行上线（仅排查用），"
                        + "打开 performance.toml 的 render-optimize.gpu-skinning-under-shaders");
            }
            return false;
        }

        final String mismatch = mismatch(base, derived);
        pipelineMismatch = mismatch != null;
        if (mismatch != null) {
            if (!pipelineWarned) {
                pipelineWarned = true;
                LOGGER.warn("[RenderOptimize] 渲染管线已被外部修改（{}）：GPU 蒙皮自动让位给 CPU 蒙皮。"
                        + "外部模组改过管线后会按自己的顶点格式挑着色器程序，我们那条派生管线拿不到自己的顶点着色器；"
                        + "继续用会画出错位的几何（模型消失、周围散落黑块）", mismatch);
            }
            return false;
        }
        return true;
    }

    /**
     * GPU 路径当前被谁挡下的（F3 用）。
     *
     * @return {@code null} = 没被挡下；否则是简短的 ASCII 标记（调试屏默认字体没有中文字形）
     */
    public static @Nullable String suppressionNote() {
        if (shaderPackInUse() && !RenderOptimize.gpuSkinningUnderShaders()) {
            return "shader pack";
        }
        return pipelineMismatch ? "vertex format" : null;
    }

    /** 管线三方对账：基管线格式 → 原版 entity；派生管线格式 → 我们的；派生管线顶点着色器 → 我们的。 */
    private static @Nullable String mismatch(RenderPipeline base, RenderPipeline derived) {
        if (!EXPECTED_ENTITY_FORMAT.equals(base.getVertexFormatBinding(0))) {
            return "基管线的顶点格式已被外部替换（与原版 entity 格式对不上）";
        }
        if (derived.getVertexFormatBinding(0) != SkinnedMesh.FORMAT) {
            return "派生管线的顶点格式被替换";
        }
        if (!SKINNED_VERTEX_SHADER.equals(derived.getVertexShader())) {
            return "派生管线的顶点着色器被替换";
        }
        return null;
    }

    /** 光影包在不在用。每秒最多真探一次，其余时间读缓存。 */
    private static boolean shaderPackInUse() {
        final long now = System.nanoTime();
        if (now < nextShaderPollNanos) {
            return shaderPackInUse;
        }

        nextShaderPollNanos = now + SHADER_POLL_INTERVAL_NANOS;
        shaderPackInUse = pollShaderPack();
        return shaderPackInUse;
    }

    /**
     * 光影包状态可能刚变过（由软依赖混入在 Iris 的重载入口上调用）—— 把轮询的时间戳清零，
     * 下一次询问立刻重新探测，不必再等那 1 秒。
     *
     * <p>只在渲染线程上被调用（Iris 的重载入口本来就在渲染线程），写的又只是一个
     * {@code long}，所以不加同步：最坏情况是这一帧读到旧的布尔值，下一帧就纠正了。</p>
     *
     * <p>没装 Iris 时这个方法永远不会被调用，那 1 秒的轮询就是唯一的通道 ——
     * 也就是说这不是「必须有的挂钩」，只是一个把切换延迟从 1 秒压到 1 帧的加速器。</p>
     */
    public static void invalidateShaderState() {
        nextShaderPollNanos = 0L;
    }

    private static boolean pollShaderPack() {
        if (!irisLoaded()) {
            return false;
        }

        final Method method = shaderPackMethod();
        if (method == null) {
            // 装了光影模组但 API 读不到（版本差异）：按「有光影」处理，宁可少一项优化
            return true;
        }

        try {
            final Object value = method.invoke(irisApiInstance);
            return value instanceof Boolean inUse && inUse;
        } catch (Throwable failure) {
            if (!probeWarned) {
                probeWarned = true;
                LOGGER.warn("[RenderOptimize] 读取光影包状态失败，按「有光影」处理：GPU 蒙皮让位给 CPU 蒙皮", failure);
            }
            return true;
        }
    }

    /** ModList 没就绪时返回 {@code false} 且不缓存，下一轮再问。 */
    private static boolean irisLoaded() {
        final Boolean cached = irisLoaded;
        if (cached != null) {
            return cached;
        }

        try {
            final ModList mods = ModList.get();
            if (mods == null) {
                return false;
            }
            final boolean loaded = mods.isLoaded(IRIS_MOD_ID);
            irisLoaded = loaded;
            return loaded;
        } catch (Throwable ignored) {
            // 加载早期 ModList 还没建好：这一轮当「没装」，但不写缓存
            return false;
        }
    }

    /** 只探一次 Iris API；探不到就返回 {@code null}（调用方按「有光影」处理）。 */
    private static @Nullable Method shaderPackMethod() {
        if (irisApiProbed) {
            return shaderPackMethod;
        }
        irisApiProbed = true;

        try {
            final Class<?> apiClass = Class.forName(IRIS_API_CLASS, false, SkinnedPipelineGuard.class.getClassLoader());
            final Object instance = apiClass.getMethod("getInstance").invoke(null);
            final Method method = apiClass.getMethod("isShaderPackInUse");
            irisApiInstance = instance;
            shaderPackMethod = method;
        } catch (Throwable failure) {
            if (!probeWarned) {
                probeWarned = true;
                LOGGER.warn("[RenderOptimize] 光照模组已加载但读不到光影包状态 API（{}），"
                        + "保守起见按「有光影」处理：GPU 蒙皮让位给 CPU 蒙皮", IRIS_API_CLASS, failure);
            }
        }
        return shaderPackMethod;
    }
}
