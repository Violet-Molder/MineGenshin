package com.linweiyun.genshin.client.render.optimize.geo;

import com.geckolib.cache.model.BakedGeoModel;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * 一个 {@link BakedGeoModel} 的预编译结果：骨骼树 + 每根骨骼的扁平几何。
 *
 * <p>这一层只保存「模型自身的常量」。骨骼每帧的位姿、动画快照、显隐都还是从
 * {@link CompiledBone#source} 指向的原始 {@code GeoBone} 上读，
 * 所以 GeckoLib 的动画系统、{@code BoneUpdater}、骨骼挂点层全部照常工作 ——
 * 优化只换掉了「怎么把几何写进顶点缓冲」，没有换掉「模型怎么被摆位」。</p>
 */
public final class CompiledGeoModel {

    /**
     * 编译来源，用于缓存键比对与调试。
     *
     * <h2>为什么是弱引用</h2>
     * {@code GeoCompileCache} 拿 {@link BakedGeoModel} 当 {@link java.util.WeakHashMap} 的键，
     * 值里若原样塞回这个键，条目就永远不会被回收 —— 每次资源重载、每次模型重建都会多留一整份
     * 预编译几何（每份是「顶点数 × 5 float」量级）。这里只留弱引用：模型还活着时读到的就是它，
     * 模型被换掉后返回 {@code null}，正好说明「这条缓存该走了」。
     *
     * <p>取用请走 {@link #source()}，不要再加回强引用字段。
     */
    private final WeakReference<BakedGeoModel> source;

    /** 编译来源；模型已被回收时返回 {@code null}（只用于调试 / 日志）。 */
    public @Nullable BakedGeoModel source() {
        return this.source.get();
    }

    /** 顶层骨骼，顺序与 {@code BakedGeoModel#topLevelBones()} 一致。 */
    public final CompiledBone[] roots;

    /** 骨骼总数。 */
    public final int boneCount;

    /** quad 总数（模型常量，与动画无关）。 */
    public final int quadCount;

    /** 顶点总数（= quadCount × 4 的理论值，实际写入会按骨骼显隐减少）。 */
    public final int vertexCount;

    /**
     * 骨骼名 → 预编译骨骼。
     *
     * <p>与 {@code BakedGeoModel#getBone} 的语义一致（同名骨骼以先遍历到的为准），
     * 但省掉了 {@code Optional} 包装和 map 的惰性初始化 —— 挂点层每帧都要按名字取骨骼。</p>
     */
    public final Map<String, CompiledBone> boneLookup;

    /**
     * 按骨骼编号（深度优先前序）索引的骨骼表，{@code bonesByIndex[i].index == i}。
     *
     * <p>这是 GPU 蒙皮的骨架顺序：每根骨骼的矩阵按这个顺序写进常量缓冲，
     * 顶点缓冲里的顶点也按这个顺序连续排布。见 {@link CompiledBone#index}。</p>
     */
    public final CompiledBone[] bonesByIndex;

    CompiledGeoModel(BakedGeoModel source, CompiledBone[] roots,
                     int boneCount, int quadCount, int vertexCount) {
        this.source = new WeakReference<>(source);
        this.roots = roots;
        this.boneCount = boneCount;
        this.quadCount = quadCount;
        this.vertexCount = vertexCount;
        this.boneLookup = buildLookup(roots);

        CompiledBone[] byIndex = new CompiledBone[boneCount];
        for (CompiledBone root : roots) {
            collectByIndex(root, byIndex);
        }
        this.bonesByIndex = byIndex;
    }

    /** 按名字取预编译骨骼；没有就返回 {@code null}。 */
    public @Nullable CompiledBone bone(String name) {
        return this.boneLookup.get(name);
    }

    /** 按骨骼编号取预编译骨骼（GPU 蒙皮的调色板顺序）。 */
    public CompiledBone boneAt(int index) {
        return this.bonesByIndex[index];
    }

    private static Map<String, CompiledBone> buildLookup(CompiledBone[] roots) {
        Map<String, CompiledBone> lookup = new HashMap<>();
        for (CompiledBone root : roots) {
            collect(root, lookup);
        }
        return lookup;
    }

    private static void collectByIndex(CompiledBone bone, CompiledBone[] out) {
        out[bone.index] = bone;
        for (CompiledBone child : bone.children) {
            collectByIndex(child, out);
        }
    }

    private static void collect(CompiledBone bone, Map<String, CompiledBone> out) {
        out.put(bone.source.name(), bone);
        for (CompiledBone child : bone.children) {
            collect(child, out);
        }
    }
}
