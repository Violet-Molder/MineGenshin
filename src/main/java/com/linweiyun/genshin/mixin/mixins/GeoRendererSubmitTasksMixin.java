package com.linweiyun.genshin.mixin.mixins;

import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.linweiyun.genshin.client.render.optimize.GeoRenderIntercept;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把所有 GeckoLib 渲染器的几何提交接到本模组的优化系统上。
 *
 * <h2>为什么注入接口的 default 方法</h2>
 * {@code GeoRenderer#submitRenderTasks} 是接口 default 方法（GeckoLib 5.5.6，
 * {@code GeoRenderer.java:172}），而 {@code GeoEntityRenderer} / {@code GeoReplacedEntityRenderer} /
 * {@code GeoObjectRenderer} / {@code GeoItemRenderer} 都不覆写它 —— 上游 {@code performRenderPass}
 * （{@code GeoRenderer.java:125}）也只调这一处。所以在这一个 HEAD 上注入，就覆盖了所有
 * GeckoLib 模型的几何提交，而不是「本模组的角色 + 每个实体各写一遍」。
 *
 * <p>注入点不取消时，GeckoLib 的原始实现照常执行 —— 也就是说 {@code trySubmit} 返回
 * {@code false}（missing model 等）时，行为与没装这个 mixin 时逐句相同。</p>
 *
 * <h2>为什么这里必须是 interface 而不是 class</h2>
 * Mixin 按<b>混入类自身</b>的形态决定混入子类型（{@code MixinInfo#getTypeFor} →
 * {@code MixinInfo#getVariant}）：类 → {@code SubType.Standard}，接口 → {@code SubType.Interface}。
 * 子类型对目标类型有硬性要求：{@code SubType.Standard.targetMustBeInterface = false}，
 * 它的 {@code validateTarget} 在「目标是接口」时直接抛
 * {@code @Mixin target type mismatch: ... is an interface}（Mixin 0.8.7
 * {@code MixinInfo.java:529-536, 595-597, 702-716}）。{@code GeoRenderer} 是接口，所以写成
 * {@code abstract class} 会在混入准备阶段就失败 —— 而且 {@code require = 0} 挡不住这类
 * 目标类型校验，它跟注入点匹配是两码事。
 *
 * <p>写成接口则走 {@code SubType.Interface}：目标必须是接口（正好相符）、
 * {@code superName} 必须是 {@code java/lang/Object}（接口天然如此）、
 * 处理器换成 {@code MixinPreProcessorInterface}，注入器在
 * {@code Feature.INJECTORS_IN_INTERFACE_MIXINS} 可用且启用时正常生效
 * （该特性在 Java 8+ 兼容级别下恒为启用，见 {@code MixinEnvironment.java:1155-1167}）。
 * 处理器写成 {@code private}：接口私有方法自 Java 9 起合法
 * （{@code PRIVATE_METHODS_IN_INTERFACES}），这也正是 Mixin 期待的注入处理器形态 ——
 * LDLib2 的 {@code ui/ContainerEventHandlerMixin}（接口目标 + {@code private} 处理器 +
 * {@code @Inject(cancellable = true)}）就是同一运行环境下的现成例证。</p>
 *
 * <h2>{@code require = 0} 是刻意的</h2>
 * 注入失败（例如 Mixin 版本对这个注入点有额外限制）时只记一条警告，不让客户端启动崩掉：
 * 后果是「其它 GeckoLib 模型继续走原路径」，角色仍然通过
 * {@code CharacterRenderer#submitRenderTasks} 的覆写享受优化 —— 属于降级而不是事故。
 */
@Mixin(GeoRenderer.class)
public interface GeoRendererSubmitTasksMixin {

    @Inject(
            method = "submitRenderTasks("
                    + "Lcom/geckolib/renderer/base/RenderPassInfo;"
                    + "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;"
                    + "Lnet/minecraft/client/renderer/rendertype/RenderType;"
                    + ")V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void minegenshin$interceptGeometrySubmit(RenderPassInfo<?> renderPassInfo,
                                                     OrderedSubmitNodeCollector renderTasks,
                                                     @Nullable RenderType renderType,
                                                     CallbackInfo ci) {
        if (GeoRenderIntercept.trySubmit(renderPassInfo, renderTasks, renderType)) {
            ci.cancel();
        }
    }
}
