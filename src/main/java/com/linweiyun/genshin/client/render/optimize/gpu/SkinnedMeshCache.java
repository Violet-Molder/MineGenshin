package com.linweiyun.genshin.client.render.optimize.gpu;

import com.linweiyun.genshin.client.render.optimize.geo.CompiledGeoModel;
import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * {@link CompiledGeoModel} → {@link SkinnedMesh} 的显存缓存。
 *
 * <h2>为什么用弱引用键</h2>
 * 预编译模型本身已经被 {@code GeoCompileCache} 缓存，这里的键不能再强引用它，否则
 * 「模型没人用了 → 显存该回收」这条链会断掉。弱引用 + {@link ReferenceQueue} 让模型被 GC 的
 * 那一刻自动把对应的顶点缓冲关掉；另外还有条数上限，防止模型实例频繁更换（GeckoLib 换资源包
 * 会重建模型）时显存一路涨。
 *
 * <p>打包失败（例如编译出的顶点数与统计对不上）也会被记成一条「空条目」：这是刻意的，
 * 失败一次就不再每帧重试，否则一条坏模型会变成每帧一次无用的打包开销。</p>
 *
 * <h2>条数上限定多少</h2>
 * 接管范围扩到「所有 GeckoLib 模型（含实体）」之后，一个整合包里同时存在的 GeckoLib 实体
 * 模型很容易超过几十个。此时条目上限不能再按「只服务本模组的几个角色」来定：
 * 上限一旦小于工作集，淘汰就会每帧发生 —— 被踢掉的模型下一帧重新编译 + 重新上传，
 * 比直接走 CPU 蒙皮还贵。所以上限放到 {@value #MAX_ENTRIES} 条，
 * 并把淘汰顺序改成 LRU（命中就挪到队尾），让「正在用的模型」留在缓存里。
 * 每条条目的大小约是「顶点数 × 32 字节」，角色量级（≈4900 顶点）约 157 KB，
 * 因此上限的显存占用在十几 MB 量级，属于可接受范围。
 */
public final class SkinnedMeshCache {

    private static final int MAX_ENTRIES = 128;

    private static final ReferenceQueue<CompiledGeoModel> QUEUE = new ReferenceQueue<>();
    private static final List<Entry> ENTRIES = new ArrayList<>();

    private SkinnedMeshCache() {
    }

    /**
     * 取（必要时打包）一个模型的顶点缓冲。
     *
     * @return 设备未就绪或打包失败时为 {@code null}，调用方应回退 CPU 蒙皮
     */
    public static @Nullable SkinnedMesh get(CompiledGeoModel model) {
        reap();

        for (int i = 0; i < ENTRIES.size(); i++) {
            final Entry entry = ENTRIES.get(i);
            if (entry.model.get() == model) {
                // 命中即「最近用过」：挪到队尾，淘汰从此只落在最久没用过的那一头（LRU）。
                // 实体模型多的时候，纯 FIFO 会造成「每帧都要用的老模型被新模型挤掉 → 下帧重编译」
                // 的缓存抖动，比不缓存还慢。
                if (i + 1 < ENTRIES.size()) {
                    ENTRIES.remove(i);
                    ENTRIES.add(entry);
                }
                return entry.mesh;
            }
        }

        // 设备没就绪时不要落条目：那不是「这个模型坏了」，只是「现在还没法打包」。
        if (RenderSystem.tryGetDevice() == null) {
            return null;
        }

        final SkinnedMesh mesh = SkinnedMesh.compile(model);
        ENTRIES.add(new Entry(model, mesh));

        while (ENTRIES.size() > MAX_ENTRIES) {
            closeMesh(ENTRIES.remove(0));
        }
        return mesh;
    }

    /** 资源重载：模型实例会整体换掉，这里把显存全退掉，避免每次重载都涨一截。 */
    public static void closeAll() {
        for (int i = 0; i < ENTRIES.size(); i++) {
            closeMesh(ENTRIES.get(i));
        }
        ENTRIES.clear();
    }

    private static void reap() {
        Reference<? extends CompiledGeoModel> collected;
        while ((collected = QUEUE.poll()) != null) {
            for (int i = 0; i < ENTRIES.size(); i++) {
                if (ENTRIES.get(i).model == collected) {
                    closeMesh(ENTRIES.remove(i));
                    break;
                }
            }
        }
    }

    private static void closeMesh(Entry entry) {
        if (entry.mesh != null) {
            entry.mesh.close();
        }
    }

    private static final class Entry {
        private final WeakReference<CompiledGeoModel> model;
        private final @Nullable SkinnedMesh mesh;

        private Entry(CompiledGeoModel model, @Nullable SkinnedMesh mesh) {
            this.model = new WeakReference<>(model, QUEUE);
            this.mesh = mesh;
        }
    }
}
