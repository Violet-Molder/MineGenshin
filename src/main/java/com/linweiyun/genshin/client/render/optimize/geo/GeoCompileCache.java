package com.linweiyun.genshin.client.render.optimize.geo;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.cache.object.GeoCube;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import java.util.List;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * {@link BakedGeoModel} → {@link CompiledGeoModel} 的编译与缓存。
 *
 * <h2>生命周期</h2>
 * 编译是<b>一次性</b>的：同一个 {@code BakedGeoModel} 实例只会编一次，之后每帧直接查表。
 * 缓存用 {@link WeakHashMap} 挂键，模型被资源重载换掉、旧实例没人引用后，条目会自己消失，
 * 不需要监听重载事件（也就不用担心「重载了但缓存没清」这类不同步问题）。
 *
 * <p><b>前提</b>：值里不能强引用键。{@link CompiledGeoModel} 因此只保存模型的弱引用
 * （见 {@link CompiledGeoModel#source()}）—— 否则 WeakHashMap 的键被值拴住，
 * 条目永不回收，每次重载都会多留一份预编译几何。
 *
 * <h2>编译不了怎么办</h2>
 * 任何异常都记一次日志并把该模型记进 {@link #UNSUPPORTED}：这个模型此后<b>永远</b>
 * 走 GeckoLib 原路径，不会每帧重试、也不会刷屏。另外，遇到「一个面不是 4 个顶点」
 * 这类几何（本模组的立方体模型不会出现，别的 MOD 的自定义几何可能出现）也不接管。
 */
public final class GeoCompileCache {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);

    /** 编译结果缓存。键弱引用，模型被回收后条目自动消失。 */
    private static final Map<BakedGeoModel, CompiledGeoModel> CACHE = new WeakHashMap<>();
    /** 已知编不了的模型，避免每帧重试。 */
    private static final Set<BakedGeoModel> UNSUPPORTED = Collections.newSetFromMap(new WeakHashMap<>());
    /** 真正的渲染在客户端主线程，这把锁只是防止资源重载线程和渲染线程撞在一起。 */
    private static final Object LOCK = new Object();

    private static final float[] NO_FLOATS = new float[0];
    private static final byte[] NO_MASKS = new byte[0];

    private GeoCompileCache() {
    }

    /** 取（必要时编译）某个模型的预编译几何；返回 {@code null} 表示这个模型只能走原路径。 */
    public static @Nullable CompiledGeoModel get(BakedGeoModel model) {
        if (model == null) {
            return null;
        }
        synchronized (LOCK) {
            if (UNSUPPORTED.contains(model)) {
                return null;
            }
            CompiledGeoModel cached = CACHE.get(model);
            if (cached != null) {
                return cached;
            }
        }

        // 编译放在锁外：它只读模型、不碰缓存，多个线程撞上也只是白编一次
        CompiledGeoModel compiled = compile(model);

        synchronized (LOCK) {
            if (compiled == null) {
                UNSUPPORTED.add(model);
            } else {
                CACHE.put(model, compiled);
            }
        }
        return compiled;
    }

    /** 已缓存的模型数（调试用）。 */
    public static int size() {
        synchronized (LOCK) {
            return CACHE.size();
        }
    }

    /** 整体作废（预留给「强制重编」这类调试入口）。 */
    public static void clear() {
        synchronized (LOCK) {
            CACHE.clear();
            UNSUPPORTED.clear();
        }
    }

    // ==================== 编译 ====================

    private static @Nullable CompiledGeoModel compile(BakedGeoModel model) {
        try {
            boolean[] supported = {true};
            int[] counters = new int[3]; // 骨骼数 / quad 数 / 顶点数

            List<GeoBone> roots = model.topLevelBones();
            CompiledBone[] compiledRoots = new CompiledBone[roots.size()];
            for (int i = 0; i < roots.size(); i++) {
                compiledRoots[i] = compileBone(roots.get(i), null, supported, counters);
            }

            if (!supported[0]) {
                LOGGER.warn("[RenderOptimize] 模型 '{}' 含非立方体面（面顶点数不是 4），"
                                + "整体回退 GeckoLib 原路径", model.properties().identifier());
                return null;
            }

            return new CompiledGeoModel(model, compiledRoots, counters[0], counters[1], counters[2]);
        } catch (Throwable t) {
            LOGGER.warn("[RenderOptimize] 预编译模型失败，该模型回退 GeckoLib 原路径", t);
            return null;
        }
    }

    private static CompiledBone compileBone(GeoBone bone, @Nullable CompiledBone parent,
                                            boolean[] supported, int[] counters) {
        counters[0]++;

        float[] vertices = NO_FLOATS;
        float[] normals = NO_FLOATS;
        byte[] fixMask = NO_MASKS;
        int quadCount = 0;
        List<GeoCube> cubes = bone.getCubes();

        if (cubes != null && !cubes.isEmpty()) {
            int vertexCount = 0;
            for (GeoCube cube : cubes) {
                if (cube == null || cube.quads() == null) {
                    continue;
                }
                for (GeoQuad quad : cube.quads()) {
                    if (quad != null) {
                        // 顶点表按「每面 4 个顶点」展平（GeckoLib 的立方体面固定是 4 个角），
                        // 万一遇到别的形状就不接管这个模型，别让它悄悄写出错位的几何
                        if (quad.vertices().length != 4) {
                            supported[0] = false;
                        }
                        quadCount++;
                        vertexCount += quad.vertices().length;
                    }
                }
            }

            if (quadCount > 0) {
                vertices = new float[vertexCount * 5];
                normals = new float[quadCount * 3];
                fixMask = new byte[quadCount];
                bakeGeometry(cubes, vertices, normals, fixMask);
            }
        }

        counters[1] += quadCount;
        counters[2] += quadCount * 4;

        // 「现场模式」用的原始 cube 表（{@code geo-precompile} 关掉时走这条路）；
        // getCubes() 给的是 List，这里转成数组存进 CompiledBone。
        GeoCube[] cubeArray = cubes == null || cubes.isEmpty() ? null : cubes.toArray(new GeoCube[0]);
        CompiledBone compiled = new CompiledBone(bone, parent, vertices, normals, fixMask, quadCount, cubeArray);
        // 「先自己、再子树」的前序编号：counters[0] 在进这个方法时已经自增过一次，
        // 所以这一层拿到的下标就是 counters[0] - 1；递归子级时自然接着往下发号。
        // 顶点表与 children 也是同一个前序，于是「顶点缓冲里的一段」正好对应「一根骨骼」。
        compiled.index = counters[0] - 1;

        List<GeoBone> children = bone.getChildBones();
        CompiledBone[] compiledChildren = new CompiledBone[children.size()];
        int subtreeVertices = quadCount * 4;
        for (int i = 0; i < children.size(); i++) {
            compiledChildren[i] = compileBone(children.get(i), compiled, supported, counters);
            subtreeVertices += compiledChildren[i].subtreeVertexCount;
        }
        compiled.children = compiledChildren;
        compiled.subtreeVertexCount = subtreeVertices;

        return compiled;
    }

    /**
     * 把「cube 绕自身轴心的旋转」烘进顶点表。
     *
     * <p>顶点走 {@code R·(v-p)+p}、法线走 {@code R·n}（{@code p} 为轴心 /16，
     * {@code R} 为 cube 旋转），与 GeckoLib 在 PoseStack 上做那三下完全等价 ——
     * 差别只在浮点结合顺序，量级在 1e-6 以内。</p>
     */
    private static void bakeGeometry(List<GeoCube> cubes, float[] vertices, float[] normals, byte[] fixMask) {
        int vi = 0;
        int ni = 0;
        int qi = 0;

        for (GeoCube cube : cubes) {
            if (cube == null || cube.quads() == null) {
                continue;
            }

            final Matrix3f r = cubeRotation(cube);
            // 走 JOML 的访问器而不是字段（字段可见性会随 joml 版本与打包方式变化）
            final float r00 = r.m00();
            final float r01 = r.m01();
            final float r02 = r.m02();
            final float r10 = r.m10();
            final float r11 = r.m11();
            final float r12 = r.m12();
            final float r20 = r.m20();
            final float r21 = r.m21();
            final float r22 = r.m22();
            final float px = (float) (cube.pivot().x() / 16.0);
            final float py = (float) (cube.pivot().y() / 16.0);
            final float pz = (float) (cube.pivot().z() / 16.0);
            final byte mask = CompiledBone.fixMaskFor(cube);

            for (GeoQuad quad : cube.quads()) {
                if (quad == null) {
                    continue;
                }

                final float nx = quad.normal().x();
                final float ny = quad.normal().y();
                final float nz = quad.normal().z();
                normals[ni] = r00 * nx + r10 * ny + r20 * nz;
                normals[ni + 1] = r01 * nx + r11 * ny + r21 * nz;
                normals[ni + 2] = r02 * nx + r12 * ny + r22 * nz;
                fixMask[qi] = mask;

                for (GeoVertex vertex : quad.vertices()) {
                    final float x = vertex.position().x() - px;
                    final float y = vertex.position().y() - py;
                    final float z = vertex.position().z() - pz;
                    vertices[vi] = r00 * x + r10 * y + r20 * z + px;
                    vertices[vi + 1] = r01 * x + r11 * y + r21 * z + py;
                    vertices[vi + 2] = r02 * x + r12 * y + r22 * z + pz;
                    vertices[vi + 3] = vertex.texU();
                    vertices[vi + 4] = vertex.texV();
                    vi += 5;
                }

                ni += 3;
                qi++;
            }
        }
    }

    /**
     * cube 旋转矩阵。
     *
     * <p>分支与 {@code RenderUtil#optionalRotateZYX} 一一对应（单轴时用单轴旋转，
     * 多轴时用 ZYX 顺序），这样烘出来的矩阵与运行时那一串 {@code mulPose} 逐位一致。</p>
     */
    private static Matrix3f cubeRotation(GeoCube cube) {
        final double z = cube.rotation().z();
        final double y = cube.rotation().y();
        final double x = cube.rotation().x();

        Matrix3f matrix = new Matrix3f();
        if (z == 0 && y == 0 && x == 0) {
            return matrix;
        }

        Quaternionf quat = new Quaternionf();
        if (x == 0 && y == 0) {
            quat.rotationZ((float) z);
        } else if (x == 0 && z == 0) {
            quat.rotationY((float) y);
        } else if (y == 0 && z == 0) {
            quat.rotationX((float) x);
        } else {
            quat.rotationZYX((float) z, (float) y, (float) x);
        }
        return matrix.rotation(quat);
    }

}
