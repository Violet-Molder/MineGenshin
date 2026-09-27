package com.linweiyun.genshin.client.render.optimize.gpu;

import com.linweiyun.genshin.Minegenshin;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.RenderPipelines;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.jspecify.annotations.Nullable;

/**
 * GPU 蒙皮专用管线族。
 *
 * <h2>为什么是「族」而不是一条手搓管线</h2>
 * 一条手搓管线只能覆盖一种 RenderType —— 之前只覆盖 {@code ENTITY_CUTOUT}，于是实体的
 * solid / translucent / 偏移等变体全都得回退 CPU 蒙皮。而 NeoForge 给 {@link RenderPipeline}
 * 加了 {@code toBuilder()}，它把原管线的 location / 两个着色器 / <b>全部 shader defines</b> /
 * 全部 bind group layout / depthStencil / polygonMode / cull / colorTargetStates / 顶点格式 /
 * 图元拓扑 / stencil 原样带过来（见 {@code com/mojang/blaze3d/pipeline/RenderPipeline.java:163-195}）。
 *
 * <p>所以这里的做法是「从任意原版 entity 管线派生」：只用
 * {@code withVertexShader} 换掉顶点着色器、{@code withVertexBinding(0, ...)} 换掉顶点格式、
 * {@code withBindGroupLayout} 追加一组 {@code SkinData} 常量缓冲
 * （{@code withBindGroupLayout} 是 append，见 {@code RenderPipeline.java:283-290}；
 * {@code withVertexBinding} 是覆盖同号绑定，见 {@code RenderPipeline.java:328-331}）。
 * <b>片元着色器刻意不动</b>——它沿用基管线的 {@code core/entity}（或自发光版），
 * 这正是「一条派生逻辑覆盖整个 entity 家族」的原因：片元阶段的 ALPHA_CUTOUT、混合、
 * 覆盖层采样等行为全都来自基管线本身。
 *
 * <p>静态字段只构建描述对象、绝不创建显存资源：管线注册发生在设备初始化之前，
 * 此时 {@code RenderSystem.getDevice()} 还不能用。
 */
public final class SkinnedPipelines {
    /** 骨骼矩阵 + 光照 / 覆盖层 + 颜色，一块 std140 常量缓冲。名字必须与 GLSL 里的块名一致。 */
    public static final BindGroupLayout SKIN_DATA_LAYOUT = BindGroupLayout.builder()
            .withUniform("SkinData", UniformType.UNIFORM_BUFFER)
            .build();

    /**
     * 可以派生 skinned 版本的原版 entity 管线白名单。
     *
     * <p>纳入条件：顶点着色器只要没有 {@code DISSOLVE} / {@code APPLY_TEXTURE_MATRIX} 分支，
     * 我们的 {@code entity_skinned.vsh} 就能逐分支对上（它支持
     * {@code PER_FACE_LIGHTING} / {@code NO_CARDINAL_LIGHTING} / {@code EMISSIVE} / {@code NO_OVERLAY}，
     * 与原版 {@code core/entity.vsh} 一致）。</p>
     *
     * <p><b>刻意排除</b>（原版定义见 {@code net/minecraft/client/renderer/RenderPipelines.java}）：
     * {@code ENTITY_CUTOUT_DISSOLVE}（:266 带 {@code DISSOLVE} 且多挂一组 DISSOLVE_MASK_SAMPLER）与
     * {@code BREEZE_WIND}（:322 带 {@code APPLY_TEXTURE_MATRIX}）——这两个 define 分支在我们的
     * 顶点着色器里<b>不存在</b>，硬派生会得到「该溶解的没溶解、该套纹理矩阵的没套」的画面差异。
     * 另外 {@code END_CRYSTAL_BEAM} / {@code BANNER_PATTERN} 之类不属于 GeckoLib 的模型几何，
     * 一并排除，白名单只留「模型几何会用到」的那批。</p>
     */
    private static final List<RenderPipeline> WHITELIST = List.of(
            RenderPipelines.ENTITY_SOLID,
            RenderPipelines.ENTITY_SOLID_Z_OFFSET_FORWARD,
            RenderPipelines.ENTITY_CUTOUT,
            RenderPipelines.ENTITY_CUTOUT_CULL,
            RenderPipelines.ENTITY_CUTOUT_Z_OFFSET,
            RenderPipelines.ENTITY_TRANSLUCENT,
            RenderPipelines.ENTITY_TRANSLUCENT_CULL,
            RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE);

    /** 「基管线 → 派生管线」。键用身份比较：原版管线是单例，同名不同实例不该命中。 */
    private static final Map<RenderPipeline, RenderPipeline> DERIVED = new IdentityHashMap<>();

    private SkinnedPipelines() {
    }

    /**
     * 取某条基管线对应的 skinned 版本。
     *
     * <p>返回前先过一道环境自检（{@link SkinnedPipelineGuard}）：光影包在用、或管线被外部
     * 改过顶点布局时一律返回 {@code null} —— GPU 路径自带顶点格式，只有在「基管线还是原版那条、
     * 派生管线也还是我们那条」时按它读才不会错位。CPU 路径没有这个约束（顶点写进原版自己的缓冲）。</p>
     *
     * @return 不在白名单里、管线还没注册、或环境自检不通过时为 {@code null}；
     *         调用方据此<b>回退 CPU 蒙皮</b>，而不是拿一条不存在的管线去画
     */
    public static @Nullable RenderPipeline skinnedFor(RenderPipeline base) {
        final RenderPipeline derived = DERIVED.get(base);
        if (derived == null) {
            return null;
        }
        return SkinnedPipelineGuard.allows(base, derived) ? derived : null;
    }

    /**
     * GPU 路径当前被环境挡下的原因，供 F3 的 {@code mg-render} 一行显示。
     *
     * @return {@code null} = 没被挡下
     */
    public static @Nullable String suppressionNote() {
        return SkinnedPipelineGuard.suppressionNote();
    }

    /** 派生一条 skinned 管线：只换顶点格式与顶点着色器，再追加 SkinData 常量缓冲。 */
    private static RenderPipeline derive(RenderPipeline base) {
        return base.toBuilder()
                // location 必须唯一：注册表按 location 建索引，沿用基管线会直接撞名。
                .withLocation(Minegenshin.id("pipeline/skinned_" + base.getLocation().getPath().replace('/', '_')))
                .withVertexShader(Minegenshin.id("core/entity_skinned"))
                .withBindGroupLayout(SKIN_DATA_LAYOUT)
                .withVertexBinding(0, SkinnedMesh.FORMAT)
                .build();
    }

    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        for (int i = 0; i < WHITELIST.size(); i++) {
            final RenderPipeline base = WHITELIST.get(i);
            final RenderPipeline derived = derive(base);
            event.registerPipeline(derived);
            DERIVED.put(base, derived);
        }
    }
}
