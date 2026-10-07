package com.linweiyun.genshin.client.performance;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * 飘字性能系统的<b>资源重载挂点</b>：重载生效后把抓着字体图集的缓存整体作废。
 *
 * <h2>为什么必须有这一条</h2>
 * {@link IndicatorGlyphCache} 里缓存的是按当时字体量出来的宽度，而
 * {@code Minecraft#font} 的实例<b>在资源重载后不会变</b>：{@code FontManager#apply}
 * 会就地重建 FontSet，壳里的字体对象还是同一个。所以「按字体实例判断有没有换字体」
 * 这条失效条件永远不成立，一旦玩家按 F3+T、切换资源包或改字体选项，
 * 缓存里就全是旧字体的量宽 —— 飘字会错位，直到退出世界
 * （那条路才由 {@code DamageIndicatorRenderer#onLoggingOut} 兜住）。
 *
 * <p>注册见 {@code MinegenshinClient#addReloadListeners}，与 {@code GenshinGeoCache} /
 * {@code AssetGeoCache} 走同一个挂点。这里不需要读任何资源，所以不做准备阶段，
 * 等屏障等到主线程后直接清。
 */
public final class IndicatorPerfReloadListener extends SimplePreparableReloadListener<Void> {

    @Override
    protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        return null;
    }

    /** 只有渲染主线程会碰这两个缓存，重载的 apply 也回到主线程，所以直接清即可。 */
    @Override
    protected void apply(Void unused, ResourceManager resourceManager, ProfilerFiller profiler) {
        IndicatorGlyphCache.clear();
        IndicatorFramePlanner.reset();
        HudRenderCaches.clear();
    }
}
