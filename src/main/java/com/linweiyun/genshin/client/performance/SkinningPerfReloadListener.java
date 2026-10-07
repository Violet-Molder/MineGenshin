package com.linweiyun.genshin.client.performance;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * 资源重载挂点：本模组的渲染缓存需要在重载后整体失效。
 *
 * <p>重载会换掉 {@code BakedGeoModel} 等实例，任何挂在实例上的缓存都必须显式失效，
 * 否则键一换、条目就成了取不到的垃圾。这里与 {@link IndicatorPerfReloadListener}
 * 同一个挂点；要读资源时在 {@link #prepare} 取，只在主线程做的收尾放 {@link #apply}。
 */
public final class SkinningPerfReloadListener extends SimplePreparableReloadListener<Void> {

    @Override
    protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        return null;
    }

    @Override
    protected void apply(Void unused, ResourceManager resourceManager, ProfilerFiller profiler) {
    }
}
