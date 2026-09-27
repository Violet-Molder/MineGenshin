package com.linweiyun.genshin.client.performance;

import com.linweiyun.genshin.client.render.optimize.gpu.SkinDataStorage;
import com.linweiyun.genshin.client.render.optimize.gpu.SkinnedMeshCache;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * GPU 蒙皮性能系统的<b>资源重载挂点</b>：重载生效后把抓着显存的那两张缓存整体关掉。
 *
 * <h2>为什么必须有这一条</h2>
 * GPU 蒙皮的顶点缓冲与骨骼常量缓冲是<b>显存对象</b>，钥匙是「编译后的模型」（{@code CompiledGeoModel}）。
 * 玩家按 F3+T、切换资源包或改模型相关选项时，GeckoLib 会把 {@code BakedGeoModel} 及我们挂在它上面的
 * 编译结果整个换掉 —— 旧实例再也没有人去取，可它的顶点缓冲还占着显存。
 * 于是每重载一次显存就往上抬一截，换几次资源包就能把显存吃到告警。缓存自己感受不到这件事：
 * 键是弱引用，键一死条目就消失，而「条目消失」并不等于「缓冲被释放」。
 *
 * <p>所以这里显式 {@code closeAll()}，与 {@code IndicatorPerfReloadListener} 同一个挂点、同一套理由：
 * 那一条失效的是字体图集 UV，这一条失效的是显存缓冲，都属于「缓存不自知、必须外部通知」的类型。</p>
 *
 * <p>注册见 {@code MinegenshinClient#addReloadListeners}。这里不需要读任何资源，所以不做准备阶段，
 * 等屏障等到主线程后直接关 —— 关缓冲也只在主线程做，避免和渲染线程抢设备。</p>
 */
public final class SkinningPerfReloadListener implements PreparableReloadListener {

    @Override
    public CompletableFuture<Void> reload(SharedState sharedState, Executor prepExecutor,
                                          PreparationBarrier barrier, Executor applyExecutor) {
        return CompletableFuture.<Void>completedFuture(null)
                .thenCompose(barrier::wait)
                .thenRunAsync(SkinningPerfReloadListener::closeGpuCaches, applyExecutor);
    }

    /** 两个缓存都是渲染主线程独占的，重载的 apply 也回到主线程，所以直接关即可。 */
    private static void closeGpuCaches() {
        SkinnedMeshCache.closeAll();
        SkinDataStorage.closeAll();
    }
}
