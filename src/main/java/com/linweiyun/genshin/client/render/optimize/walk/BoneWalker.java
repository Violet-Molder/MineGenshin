package com.linweiyun.genshin.client.render.optimize.walk;

import com.geckolib.animation.state.BoneSnapshot;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.GeoQuad;
import com.geckolib.cache.model.GeoVertex;
import com.geckolib.cache.model.cuboid.GeoCube;
import com.geckolib.renderer.base.RenderPassInfo;
import com.linweiyun.genshin.client.render.optimize.RenderOptimize;
import com.linweiyun.genshin.client.render.optimize.geo.CompiledBone;
import com.linweiyun.genshin.client.render.optimize.geo.CompiledGeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * 骨骼遍历 + 顶点写出 —— GeckoLib {@code BakedGeoModel#render} 那条链的等价实现。
 *
 * <h2>与原路径逐项对齐的地方</h2>
 * <ol>
 *   <li><b>遍历顺序</b>：顶层骨骼顺序 → 每根骨骼「先自己、再子树」，与原版
 *       {@code positionAndRender} / {@code renderChildren} 一致，所以顶点写出的<b>顺序</b>不变；</li>
 *   <li><b>显隐</b>：{@code frameSnapshot.isHidden()} 跳过自己的几何、
 *       {@code areChildrenHidden()} 跳过子树，与 {@code CuboidGeoBone#render} /
 *       {@code GeoBone#renderChildren} 的判据一一对应；</li>
 *   <li><b>骨骼位姿</b>：动画位移 → 平移到自己轴心 → 基础旋转＋动画旋转（ZYX）→
 *       动画缩放 → 骨骼位置监听 → 平移回轴心。顺序与
 *       {@code RenderUtil#prepMatrixForBoneAndUpdateListeners} 完全相同；</li>
 *   <li><b>法线符号修正</b>：{@code fixInvertedFlatCube} 的三条判据照做，
 *       而且是在法线变换到世界空间之后才判 —— 与原版同一时机；</li>
 *   <li><b>顶点属性</b>：位置 / 颜色 / UV / UV1(overlay) / UV2(方块光) / 法线，
 *       走的是 {@code VertexConsumer} 的组合式 {@code addVertex}，与原版调用的是同一个方法。</li>
 * </ol>
 *
 * <h2>与原路径不同的地方（只有两处，都是刻意的）</h2>
 * <ul>
 *   <li><b>cube 层不再进 PoseStack</b>：cube 的轴心平移与旋转在编译期已经折进顶点表
 *       （见 {@link CompiledBone}）。语义等价，浮点结合顺序不同，误差在 1e-6 量级；</li>
 *   <li><b>不再 new 临时向量</b>：顶点位置内联矩阵乘法算，法线用一个 float 三元组，
 *       旋转复用四元数（见 {@link Rotations}）。</li>
 * </ul>
 *
 * <h2>为什么递归而不是显式栈</h2>
 * 递归本身不产生堆分配，栈深等于骨骼树的深度（角色模型不到 10 层）；
 * 而「用深度槽位数组替代 PoseStack」这一步省下的只是几十字节的矩阵拷贝，
 * 却要让骨骼位置监听、{@code PerBoneRender} 这些依赖 PoseStack 的扩展点跟着改。
 * 热路径上真正贵的是那 1.1 万次堆分配，先把它干掉。
 */
public final class BoneWalker {

    private BoneWalker() {
    }

    /**
     * 把整个模型的几何写进顶点缓冲。
     *
     * @param poseStack 已经摆好「模型根位姿」的栈（调用方负责 push/set/pop）
     * @param info      当前渲染趟的信息，用于派发骨骼位置监听；没有监听时传 {@code null} 也行
     * @param flags     {@link RenderOptimize} 的子项位掩码
     * @return 实际写出的顶点数（骨骼被隐藏时小于模型的顶点总数）
     */
    public static int render(CompiledGeoModel model, PoseStack poseStack, @Nullable RenderPassInfo<?> info,
                             VertexConsumer sink, int packedLight, int packedOverlay, int renderColor,
                             int flags) {
        final CompiledBone[] roots = model.roots;
        int written = 0;
        for (int i = 0; i < roots.length; i++) {
            written += walk(roots[i], poseStack, info, sink, packedLight, packedOverlay, renderColor, flags);
        }
        return written;
    }

    /**
     * 只写「{@code target} 及其子树」的几何 —— 骨骼挂点层要的「把那根骨骼从源模型里抠出来」。
     *
     * <h2>为什么等价于「整模型隐藏 + 只开这一支」</h2>
     * GeckoLib 的做法是给全树建 {@code BoneSnapshot} 并标记隐藏，再整模型渲染一次。
     * 但「隐藏」只是让 {@code positionAndRender} 跳过几何，<b>祖先链的骨骼位姿照样逐级乘进
     * PoseStack</b> —— 也就是说，目标骨骼最终拿到的矩阵是
     * {@code 模型根位姿 · Π(根→目标的每根祖先的 prepMatrixForBone)}，
     * 然后才在它自己的 {@code prepMatrixForBone} 里画自己的几何。
     *
     * <p>所以这里正是这么做的：先沿 {@link CompiledBone#pathFromRoot()} 把祖先链压栈并逐级
     * {@code prepBone}，再让 {@link #walk} 处理目标骨骼本身（它会自己做 push/prep/几何/子级/pop）。
     * 路径是编译期常量，全程零递归查找、零 {@code BoneSnapshot} 分配，
     * 也不再遍历 83 根骨骼去做隐藏标记。</p>
     *
     * <p>祖先链上每一级的 prep 都带上 {@code info}，所以骨骼位置监听（若有）照旧被派发，
     * 与 {@code RenderUtil#prepMatrixForBoneAndUpdateListeners} 的时机一致。</p>
     *
     * @return 实际写出的顶点数
     */
    public static int renderBoneSubtree(CompiledBone target, PoseStack poseStack,
                                        @Nullable RenderPassInfo<?> info, VertexConsumer sink,
                                        int packedLight, int packedOverlay, int renderColor, int flags) {
        final CompiledBone[] path = target.pathFromRoot();
        final boolean reuseRotation = (flags & RenderOptimize.FLAG_ZERO_ALLOC_WALK) != 0;

        int pushed = 0;
        for (int i = 0; i < path.length - 1; i++) {
            final CompiledBone node = path[i];
            poseStack.pushPose();
            prepBone(poseStack, node.source, node.source.frameSnapshot, info, reuseRotation);
            pushed++;
        }

        final int written = walk(target, poseStack, info, sink, packedLight, packedOverlay, renderColor, flags);

        for (int i = 0; i < pushed; i++) {
            poseStack.popPose();
        }
        return written;
    }

    // ==================== 骨骼 ====================

    private static int walk(CompiledBone bone, PoseStack poseStack, @Nullable RenderPassInfo<?> info,
                            VertexConsumer sink, int packedLight, int packedOverlay, int renderColor,
                            int flags) {
        final GeoBone source = bone.source;
        final BoneSnapshot snapshot = source.frameSnapshot;

        final boolean selfHidden = snapshot != null && snapshot.isHidden();
        final boolean childrenHidden = snapshot != null && snapshot.areChildrenHidden();

        poseStack.pushPose();
        prepBone(poseStack, source, snapshot, info,
                (flags & RenderOptimize.FLAG_ZERO_ALLOC_WALK) != 0);

        int written = 0;
        if (!selfHidden) {
            final boolean directVertex = (flags & RenderOptimize.FLAG_DIRECT_VERTEX) != 0;
            written = (flags & RenderOptimize.FLAG_GEO_PRECOMPILE) != 0
                    ? writePrecompiled(bone, poseStack, sink, packedLight, packedOverlay, renderColor, directVertex)
                    : writeLive(bone, poseStack, sink, packedLight, packedOverlay, renderColor, directVertex);
        }

        if (!childrenHidden) {
            final CompiledBone[] children = bone.children;
            for (int i = 0; i < children.length; i++) {
                written += walk(children[i], poseStack, info, sink, packedLight, packedOverlay, renderColor, flags);
            }
        }

        poseStack.popPose();
        return written;
    }

    /**
     * 把一根骨骼的位姿摆到 PoseStack 上。
     *
     * <p>逐句对应 {@code RenderUtil#prepMatrixForBoneAndUpdateListeners}：
     * 动画位移（{@code BoneSnapshot#translate}，内部已经是 {@code (-x/16, y/16, z/16)}）→
     * 平移到轴心 → 旋转 → 动画缩放 → 通知监听者 → 平移回轴心。</p>
     *
     * <p>缩放刻意仍然走 {@code PoseStack.scale}：非均匀缩放会顺带把法线矩阵改成
     * 「乘 1/s 并标记不可信」，那是原版的语义，自己手写一遍只会引入偏差。</p>
     */
    public static void prepBone(PoseStack poseStack, GeoBone bone, @Nullable BoneSnapshot snapshot,
                                @Nullable RenderPassInfo<?> info, boolean reuseRotation) {
        if (snapshot != null) {
            snapshot.translate(poseStack);
        }

        bone.translateToPivotPoint(poseStack);

        float xRot = bone.baseRotX();
        float yRot = bone.baseRotY();
        float zRot = bone.baseRotZ();
        if (snapshot != null) {
            xRot += snapshot.getRotX();
            yRot += snapshot.getRotY();
            zRot += snapshot.getRotZ();
        }
        Rotations.rotateZYX(poseStack, zRot, yRot, xRot, reuseRotation);

        if (snapshot != null) {
            snapshot.scale(poseStack);
        }

        if (info != null) {
            bone.updateBonePositionListeners(poseStack, info);
        }

        bone.translateAwayFromPivotPoint(poseStack);
    }

    // ==================== 几何：预编译模式 ====================

    private static int writePrecompiled(CompiledBone bone, PoseStack poseStack, VertexConsumer sink,
                                        int packedLight, int packedOverlay, int renderColor,
                                        boolean directVertex) {
        final int quadCount = bone.quadCount;
        if (quadCount == 0) {
            return 0;
        }

        // 矩阵元素一律走 JOML 的访问器，不碰字段：字段可见性会随 joml 的版本与
        // 打包方式变化（本仓库这一版就编不过），访问器才是稳定 API。
        // 在循环外读一次到局部量，循环体里就只剩纯算术。
        final Matrix4f pose = poseStack.last().pose();
        final float p00 = pose.m00();
        final float p01 = pose.m01();
        final float p02 = pose.m02();
        final float p10 = pose.m10();
        final float p11 = pose.m11();
        final float p12 = pose.m12();
        final float p20 = pose.m20();
        final float p21 = pose.m21();
        final float p22 = pose.m22();
        final float p30 = pose.m30();
        final float p31 = pose.m31();
        final float p32 = pose.m32();

        final Matrix3f normal = poseStack.last().normal();
        final float n00 = normal.m00();
        final float n01 = normal.m01();
        final float n02 = normal.m02();
        final float n10 = normal.m10();
        final float n11 = normal.m11();
        final float n12 = normal.m12();
        final float n20 = normal.m20();
        final float n21 = normal.m21();
        final float n22 = normal.m22();

        final float[] vertices = bone.vertices;
        final float[] normals = bone.normals;
        final byte[] masks = bone.normalFixMask;

        int vi = 0;
        int ni = 0;
        for (int q = 0; q < quadCount; q++) {
            final float nx = normals[ni];
            final float ny = normals[ni + 1];
            final float nz = normals[ni + 2];

            // 注意系数顺序：GeckoLib 走的是 Matrix3f.transform(...)（= M·n，列主序），
            // 也就是 x' = m00·x + m10·y + m20·z。写成 m00·x + m01·y + m02·z 就等于拿
            // 法线矩阵的转置去变换，姿势一转法线就朝反方向偏，光照跟着错。
            float wx = n00 * nx + n10 * ny + n20 * nz;
            float wy = n01 * nx + n11 * ny + n21 * nz;
            float wz = n02 * nx + n12 * ny + n22 * nz;

            final byte mask = masks[q];
            if ((mask & 1) != 0 && wx < 0f) {
                wx = -wx;
            }
            if ((mask & 2) != 0 && wy < 0f) {
                wy = -wy;
            }
            if ((mask & 4) != 0 && wz < 0f) {
                wz = -wz;
            }

            for (int k = 0; k < 4; k++) {
                final float x = vertices[vi];
                final float y = vertices[vi + 1];
                final float z = vertices[vi + 2];
                final float u = vertices[vi + 3];
                final float v = vertices[vi + 4];

                if (directVertex) {
                    sink.addVertex(
                            p00 * x + p10 * y + p20 * z + p30,
                            p01 * x + p11 * y + p21 * z + p31,
                            p02 * x + p12 * y + p22 * z + p32,
                            renderColor, u, v, packedOverlay, packedLight, wx, wy, wz);
                } else {
                    final Vector4f transformed = pose.transform(new Vector4f(x, y, z, 1f));
                    sink.addVertex(transformed.x, transformed.y, transformed.z,
                            renderColor, u, v, packedOverlay, packedLight, wx, wy, wz);
                }
                vi += 5;
            }
            ni += 3;
        }
        return quadCount * 4;
    }

    // ==================== 几何：现场模式 ====================

    /**
     * 不做预编译的兜底路径：cube 的轴心变换每帧现做（与原版一样对 PoseStack
     * push/translate/rotate/translate/pop），但顶点与法线仍然零分配写。
     *
     * <p>它存在的意义是「万一预编译出来的几何有问题，可以单独关掉那一项排查」，
     * 以及给性能对照提供一个中间档。</p>
     */
    private static int writeLive(CompiledBone bone, PoseStack poseStack, VertexConsumer sink,
                                 int packedLight, int packedOverlay, int renderColor,
                                 boolean directVertex) {
        final GeoCube[] cubes = bone.cubes;
        if (cubes == null || cubes.length == 0) {
            return 0;
        }

        int written = 0;
        for (int c = 0; c < cubes.length; c++) {
            final GeoCube cube = cubes[c];
            if (cube == null || cube.quads() == null) {
                continue;
            }

            poseStack.pushPose();
            cube.translateToPivotPoint(poseStack);
            cube.rotate(poseStack);
            cube.translateAwayFromPivotPoint(poseStack);

            final Matrix4f pose = poseStack.last().pose();
            final float p00 = pose.m00();
            final float p01 = pose.m01();
            final float p02 = pose.m02();
            final float p10 = pose.m10();
            final float p11 = pose.m11();
            final float p12 = pose.m12();
            final float p20 = pose.m20();
            final float p21 = pose.m21();
            final float p22 = pose.m22();
            final float p30 = pose.m30();
            final float p31 = pose.m31();
            final float p32 = pose.m32();

            final Matrix3f normal = poseStack.last().normal();
            final float n00 = normal.m00();
            final float n01 = normal.m01();
            final float n02 = normal.m02();
            final float n10 = normal.m10();
            final float n11 = normal.m11();
            final float n12 = normal.m12();
            final float n20 = normal.m20();
            final float n21 = normal.m21();
            final float n22 = normal.m22();
            final byte mask = CompiledBone.fixMaskFor(cube);

            final GeoQuad[] quads = cube.quads();
            for (int q = 0; q < quads.length; q++) {
                final GeoQuad quad = quads[q];
                if (quad == null) {
                    continue;
                }

                final float nx = quad.normalX();
                final float ny = quad.normalY();
                final float nz = quad.normalZ();

                // 与 writePrecompiled 同一套系数顺序（见那里的注释）
                float wx = n00 * nx + n10 * ny + n20 * nz;
                float wy = n01 * nx + n11 * ny + n21 * nz;
                float wz = n02 * nx + n12 * ny + n22 * nz;

                if ((mask & 1) != 0 && wx < 0f) {
                    wx = -wx;
                }
                if ((mask & 2) != 0 && wy < 0f) {
                    wy = -wy;
                }
                if ((mask & 4) != 0 && wz < 0f) {
                    wz = -wz;
                }

                final GeoVertex[] vertices = quad.vertices();
                for (int k = 0; k < vertices.length; k++) {
                    final GeoVertex vertex = vertices[k];
                    final float x = vertex.posX();
                    final float y = vertex.posY();
                    final float z = vertex.posZ();
                    final float u = vertex.texU();
                    final float v = vertex.texV();

                    if (directVertex) {
                        sink.addVertex(
                                p00 * x + p10 * y + p20 * z + p30,
                                p01 * x + p11 * y + p21 * z + p31,
                                p02 * x + p12 * y + p22 * z + p32,
                                renderColor, u, v, packedOverlay, packedLight, wx, wy, wz);
                    } else {
                        final Vector4f transformed = pose.transform(new Vector4f(x, y, z, 1f));
                        sink.addVertex(transformed.x, transformed.y, transformed.z,
                                renderColor, u, v, packedOverlay, packedLight, wx, wy, wz);
                    }
                    written++;
                }
            }

            poseStack.popPose();
        }
        return written;
    }
}
