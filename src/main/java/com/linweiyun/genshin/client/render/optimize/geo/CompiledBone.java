package com.linweiyun.genshin.client.render.optimize.geo;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.cuboid.GeoCube;
import org.jspecify.annotations.Nullable;

/**
 * 预编译后的一根骨骼：它自己的几何（扁平数组）+ 它的子骨骼。
 *
 * <h2>顶点表的坐标系</h2>
 * {@link #vertices} / {@link #normals} 里的数据是<b>骨骼空间</b>的，并且已经把
 * 「所属 cube 绕自身轴心的旋转」折了进去 —— 也就是 GeckoLib 在 {@code GeoCube#render}
 * 里对 PoseStack 做的那三下（{@code translateToPivotPoint} → {@code rotate} →
 * {@code translateAwayFromPivotPoint}）。
 *
 * <p>推导（{@code p} = cube 轴心 / 16，{@code R} = cube 旋转）：那一串对位姿的作用是
 * {@code T(p)·R·T(-p)}，作用在一个顶点 {@code v} 上就是 {@code R·(v-p)+p}；
 * 对法线 {@code n} 则是 {@code R·n}（PoseStack 的平移不改法线）。因为 {@code p} 和
 * {@code R} 都是<b>烘焙后的常量</b>，这两步可以只在编译时算一次。</p>
 *
 * <p>每帧剩下的工作就只有「骨骼世界矩阵 × 这些常量」，正是
 * {@link com.linweiyun.genshin.client.render.optimize.walk.BoneWalker} 要做的事。</p>
 *
 * <h2>数据布局</h2>
 * <ul>
 *   <li>{@link #vertices}：每顶点 5 个 float（x, y, z, u, v），按「cube 顺序 → quad 顺序 →
 *       顶点顺序」展平，与 GeckoLib 逐 quad 逐顶点写出的<b>顺序完全一致</b>；</li>
 *   <li>{@link #normals}：每 quad 3 个 float；</li>
 *   <li>{@link #normalFixMask}：每 quad 一个 byte，装着
 *       {@code RenderUtil#fixInvertedFlatCube} 那三个条件 —— 位 0/1/2 分别对应
 *       「x 分量为负且 cube 在 y 或 z 方向厚度为 0」等判据。这一项<b>不能</b>折进顶点表，
 *       因为原版就是拿「已经变换到世界空间」的法线去判符号的，必须留到最后一步做。</li>
 * </ul>
 *
 * <p>不做顶点去重 / 面索引：26.2 的这条路径是「顺序写进顶点缓冲」，不是索引绘制，
 * 去重只能省内存、省不了写入次数，反而会让编译期多一张索引表。索引化的收益要等
 * GPU 蒙皮那一阶段（把几何常驻显存）才成立。</p>
 */
public final class CompiledBone {

    /** 原始骨骼对象。轴心、基础旋转、{@code frameSnapshot}、以及骨骼位置监听都还从这里读。 */
    public final GeoBone source;

    /** 预编译顶点：每顶点 5 个 float（x, y, z, u, v）。没有几何时是空数组。 */
    public final float[] vertices;

    /** 预编译法线：每 quad 3 个 float。 */
    public final float[] normals;

    /** 每 quad 一个 byte 的法线符号修正掩码（bit0=x / bit1=y / bit2=z）。 */
    public final byte[] normalFixMask;

    /** quad 数量。{@code vertices.length == quadCount * 20}、{@code normals.length == quadCount * 3}。 */
    public final int quadCount;

    /**
     * 「现场模式」用的原始 cube 表（{@code geo-precompile} 关掉时走这条路）。
     *
     * <p>没有几何的骨骼（纯分组骨骼）为 {@code null}。</p>
     */
    public final GeoCube @Nullable [] cubes;

    /** 子骨骼，顺序与 {@code GeoBone#children()} 一致。 */
    public CompiledBone[] children = new CompiledBone[0];

    /** 从顶层骨骼到自己的路径（含自己）。骨骼树不可变，所以这是编译期常量。 */
    private final CompiledBone[] pathFromRoot;

    /**
     * 本骨骼在 {@link CompiledGeoModel#bonesByIndex} 里的下标。
     *
     * <h2>为什么需要它</h2>
     * GPU 蒙皮要把「每根骨骼的矩阵」按固定顺序写进一块常量缓冲，顶点里只带一个骨骼下标
     * （见 {@code gpu.SkinnedMesh}）。所以编译期就得给每根骨骼定一个稳定编号。
     *
     * <p>编号就是「先自己、再子树」的深度优先前序 —— 和
     * {@link #children} 的编译顺序、顶点表的展平顺序三者完全一致，
     * 于是「顶点缓冲区里的一段连续顶点」正好对应「一根骨骼」，
     * 隐藏骨骼时只要跳过一个连续区间，不需要索引表。</p>
     */
    public int index;

    /**
     * 本骨骼<b>连同整棵子树</b>一共占多少顶点（= 它们在顶点缓冲里那段连续区间的长度）。
     *
     * <p>GPU 蒙皮里「藏掉一棵子树」= 那段顶点不画，但游标仍要跨过去（见
     * {@code BoneMatrixPalette#collect}）。有了这个编译期常量，跳过时不必递归子树，
     * 一次加法就到下一个兄弟骨骼的区间。</p>
     */
    public int subtreeVertexCount;

    CompiledBone(GeoBone source, @Nullable CompiledBone parent, float[] vertices, float[] normals,
                 byte[] normalFixMask, int quadCount, GeoCube @Nullable [] cubes) {
        this.source = source;
        this.vertices = vertices;
        this.normals = normals;
        this.normalFixMask = normalFixMask;
        this.quadCount = quadCount;
        this.cubes = cubes;

        final CompiledBone[] ancestors = parent == null ? EMPTY_PATH : parent.pathFromRoot;
        final CompiledBone[] path = new CompiledBone[ancestors.length + 1];
        if (ancestors.length > 0) {
            System.arraycopy(ancestors, 0, path, 0, ancestors.length);
        }
        path[ancestors.length] = this;
        this.pathFromRoot = path;
    }

    private static final CompiledBone[] EMPTY_PATH = new CompiledBone[0];

    /**
     * 从顶层骨骼到自己的路径，含自己。
     *
     * <p>骨骼树不可变，所以这条路径是常量：编译时随骨骼一起算好，运行时直接返回现成数组，
     * 既不分配也不递归。骨骼挂点层用它把「模型根 → 目标骨骼」的祖先链一次摆完。</p>
     */
    public CompiledBone[] pathFromRoot() {
        return this.pathFromRoot;
    }

    /** 这根骨骼自己有没有可渲染的面（不含子树）。 */
    public boolean hasGeometry() {
        return this.quadCount > 0;
    }

    /**
     * {@code RenderUtil#fixInvertedFlatCube} 的三个条件，编译成掩码。
     *
     * <p>三个条件只跟 {@code size()} 有关、与具体哪个面无关，所以同一个 cube 的
     * 所有面共用一份掩码。真正的符号判定必须留到运行时：原版就是拿「已经变换到世界空间」
     * 的法线去判「这一分量是不是负」，提前判会得到不一样的结果。</p>
     *
     * <p>「现场模式」直接用它现算；预编译模式在编译期把它存进
     * {@link #normalFixMask}。</p>
     */
    public static byte fixMaskFor(GeoCube cube) {
        final double sx = cube.size().x();
        final double sy = cube.size().y();
        final double sz = cube.size().z();

        int mask = 0;
        if (sy == 0.0 || sz == 0.0) {
            mask |= 1;
        }
        if (sx == 0.0 || sz == 0.0) {
            mask |= 2;
        }
        if (sx == 0.0 || sy == 0.0) {
            mask |= 4;
        }
        return (byte) mask;
    }
}
