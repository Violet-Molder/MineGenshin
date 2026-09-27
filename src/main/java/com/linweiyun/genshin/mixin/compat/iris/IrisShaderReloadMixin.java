package com.linweiyun.genshin.mixin.compat.iris;

import com.linweiyun.genshin.client.render.optimize.gpu.SkinnedPipelineGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 光影包状态一变，立刻让 GPU 蒙皮的环境自检重新探一次（软依赖，装了 Iris 才生效）。
 *
 * <h2>为什么需要这一下</h2>
 * {@link SkinnedPipelineGuard} 平时是「每秒最多真探一次光影包状态」——这是为避免每帧反射
 * 而做的取舍。代价是：<b>玩家按下开/关光影、或在光影选择界面里换一包的那一刻，
 * 自检最长会滞后一秒才知道</b>，而这一秒里 GPU 蒙皮还在按「没有光影」判断，于是正好把
 * 那批错位的黑块画出来。注入 Iris 自己的重载入口就能把这个窗口压到 0：
 * 状态一变，缓存立刻作废，下一帧就重新探测。
 *
 * <h2>注入点为什么是这两个</h2>
 * 都是 Iris 26.2 的公开静态入口（{@code common/.../net/irisshaders/iris/Iris.java}）：
 * <ul>
 *   <li>{@code reload()}（:569）—— 换光影包、以及开关光影都会走到这里
 *       （{@code toggleShaders} 改完配置就直接调 {@code reload()}）；它内部会销毁并重建
 *       渲染管线，所以这正是「管线被换掉」的那个时刻。</li>
 *   <li>{@code loadShaderpack()}（:234）—— 进世界时按当前配置装载光影包走这条。
 *       {@code reload()} 内部也会调它，重复清一次缓存只是两次字段写，无副作用。</li>
 * </ul>
 *
 * <h2>为什么整份配置可以「没装 Iris 就当不存在」</h2>
 * 目标类用字符串写（{@code @Mixin(targets = ...)}）并且标了 {@link Pseudo}，本类里不出现
 * Iris 的任何类型；配置是 {@code "required": false}、注入器是 {@code require = 0}，
 * 再加上 {@code IrisMixinPlugin} 的门禁 —— 没装 Iris 时这一条混入不会被应用，
 * 装了但将来改了方法名时也只记一条警告。任何情况下都不影响本模组的其它功能。
 *
 * <p>本类只做一件事：把自检缓存作废，不碰 Iris 的任何状态、也不读它的字段。</p>
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.Iris", remap = false)
public class IrisShaderReloadMixin {

    @Inject(method = "reload()V", at = @At("HEAD"), require = 0)
    private void minegenshin$onShaderPackReload(CallbackInfo ci) {
        SkinnedPipelineGuard.invalidateShaderState();
    }

    @Inject(method = "loadShaderpack()V", at = @At("HEAD"), require = 0)
    private void minegenshin$onShaderPackLoad(CallbackInfo ci) {
        SkinnedPipelineGuard.invalidateShaderState();
    }
}
