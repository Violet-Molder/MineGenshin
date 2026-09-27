package com.linweiyun.genshin.client.performance;

import net.minecraft.server.packs.resources.PreparableReloadListener;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 飘字性能系统的<b>资源重载挂点</b>：重载生效后把抓着字体图集的缓存整体作废。
 *
 * <h2>为什么必须有这一条</h2>
 * {@link IndicatorGlyphCache} 里缓存的 {@code TextRenderable} 抓着<b>排版当时的字形</b>
 * （图集 UV / sprite），而 {@code Minecraft#font} 的实例<b>在资源重载后不会变</b>：
 * {@code FontManager#apply} 会 {@code FontSet::close} 再就地重建 FontSet，壳里的
 * {@code CachedFontProvider} 只是 {@code invalidate()}，
 * 所以「按字体实例判断有没有换字体」这条失效条件永远不成立。
 * 一旦玩家按 F3+T、切换资源包或改字体选项，缓存里就全是旧图集的 UV ——
 * 飘字会花屏/错位，直到退出世界（那条路才由 {@code DamageIndicatorRenderer#onLoggingOut} 兜住）。
 *
 * <p>注册见 {@code MinegenshinClient#addReloadListeners}，与 {@code GenshinGeoCache} /
 * {@code AssetGeoCache} 走同一个挂点。这里不需要读任何资源，所以不做准备阶段，
 * 等屏障等到主线程后直接清。</p>
 */
public final class IndicatorPerfReloadListener implements PreparableReloadListener {

    @Override
    public CompletableFuture<Void> reload(SharedState sharedState, Executor prepExecutor,
                                          PreparationBarrier barrier, Executor applyExecutor) {
        return CompletableFuture.<Void>completedFuture(null)
                .thenCompose(barrier::wait)
                .thenRunAsync(IndicatorPerfReloadListener::clearCaches, applyExecutor);
    }

    /** 只有渲染主线程会碰这两个缓存，重载的 apply 也回到主线程，所以直接清即可。 */
    private static void clearCaches() {
        IndicatorGlyphCache.clear();
        IndicatorFramePlanner.reset();
        HudRenderCaches.clear();
    }
}
