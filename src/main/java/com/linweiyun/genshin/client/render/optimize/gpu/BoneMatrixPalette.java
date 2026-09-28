package com.linweiyun.genshin.client.render.optimize.gpu;

import com.geckolib.animation.state.BoneSnapshot;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.base.RenderPassInfo;
import com.linweiyun.genshin.client.render.optimize.geo.CompiledBone;
import com.linweiyun.genshin.client.render.optimize.geo.CompiledGeoModel;
import com.linweiyun.genshin.client.render.optimize.walk.BoneWalker;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.ByteBuffer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * 把「本帧这一根根骨骼的世界矩阵」写进常量缓冲 —— GPU 蒙皮每帧唯一的 CPU 侧工作量。
 */
public final class BoneMatrixPalette {

    private static final float[] MATRICES = new float[SkinDataStorage.MAX_BONES * 16];
    private static final float[] NORMAL_MATRICES = new float[SkinDataStorage.MAX_BONES * 9];

    /** std140 里 {@code LightOverlay} 的起始偏移 = Bones + NormalBones 的字节数。 */
    private static final int LIGHT_OVERLAY_OFFSET = SkinDataStorage.MAX_BONES * 64 + SkinDataStorage.MAX_BONES * 48;

    /** std140 里 {@code Bones[]} 的定长字节数。法线块必须从这里开始，不能紧接「已写骨骼数」之后。 */
    private static final int NORMAL_BLOCK_OFFSET = SkinDataStorage.MAX_BONES * 64;

    private BoneMatrixPalette() {
    }

    /**
     * 计算本帧的骨骼矩阵并写入常量缓冲，同时把「可见顶点区间」写进 {@code runsOut}。
     *
     * @param runsOut      每两个 int 一条 run：[首顶点下标, 顶点数]，容量需 ≥ {@code boneCount * 2}
     * @param runCountOut  实际 run 条数的出口（长度 1 的数组，避免额外分配）
     * @return 绑定到 {@code SkinData} 的切片；骨骼数超限 / 设备不可用 / 无可绘制几何时为 {@code null}
     */
    public static @Nullable GpuBufferSlice compute(CompiledGeoModel model, PoseStack poseStack,
                                                   @Nullable RenderPassInfo<?> info,
                                                   int packedLight, int packedOverlay, int renderColor,
                                                   int[] runsOut, int[] runCountOut) {
        if (model.boneCount > SkinDataStorage.MAX_BONES) {
            return null;
        }

        final SkinDataStorage storage = SkinDataStorage.get();
        if (storage == null) {
            return null;
        }

        final int[] cursor = {0};
        final int[] runCount = {0};
        final CompiledBone[] roots = model.roots;
        for (int i = 0; i < roots.length; i++) {
            collect(roots[i], poseStack, info, runsOut, runCount, cursor);
        }

        if (runCount[0] == 0) {
            return null;
        }

        final int boneCount = model.boneCount;
        final GpuBufferSlice slice = storage.write(data -> writeUniform(data, boneCount, packedLight, packedOverlay, renderColor));
        if (slice == null) {
            return null;
        }

        runCountOut[0] = runCount[0];
        return slice;
    }

    /**
     * 深度优先前序走一遍骨骼树：记矩阵 + 收可见区间。
     *
     * <p>顺序与 {@link BoneWalker} 的 {@code walk} 完全一致（先自己、再子树），
     * 所以「游标走过的顶点数」正好等于顶点缓冲里的下标 —— 隐藏骨骼时同样要把游标推过去，
     * 因为它的顶点在缓冲里仍然占着位置，只是不画。</p>
     */
    private static void collect(CompiledBone bone, PoseStack poseStack, @Nullable RenderPassInfo<?> info,
                               int[] runsOut, int[] runCount, int[] cursor) {
        final GeoBone source = bone.source;
        final BoneSnapshot snapshot = source.frameSnapshot;
        final boolean selfHidden = snapshot != null && snapshot.isHidden();
        final boolean childrenHidden = snapshot != null && snapshot.areChildrenHidden();

        poseStack.pushPose();
        // 复用四元数的那一档：与 CPU 路径开 FLAG_ZERO_ALLOC_WALK 时走的是同一个方法、同一分支。
        BoneWalker.prepBone(poseStack, source, snapshot, info, true);
        storeMatrices(bone.index, poseStack);

        final int quads = bone.quadCount;
        if (!selfHidden && quads > 0) {
            appendRun(runsOut, runCount, cursor[0], quads * 4);
        }
        cursor[0] += quads * 4;

        if (!childrenHidden) {
            final CompiledBone[] children = bone.children;
            for (int i = 0; i < children.length; i++) {
                collect(children[i], poseStack, info, runsOut, runCount, cursor);
            }
        } else {
            // 子树被藏掉时必须「跳过」而不是「停住」：它们的顶点在缓冲里仍然占着位置，
            // 少推这一段，后面所有兄弟骨骼的区间都会前移，画到别人的顶点上去。
            // 编译期已经算好子树顶点数，这里一步到位，不进子树。
            cursor[0] += bone.subtreeVertexCount - quads * 4;
        }

        poseStack.popPose();
    }

    /** 与前一条 run 首尾相接就并进它：少一次 {@code drawIndexed}，也少一次状态切换。 */
    private static void appendRun(int[] runsOut, int[] runCount, int firstVertex, int vertexCount) {
        final int count = runCount[0];
        if (count > 0) {
            final int last = 2 * (count - 1);
            if (runsOut[last] + runsOut[last + 1] == firstVertex) {
                runsOut[last + 1] += vertexCount;
                return;
            }
        }

        runsOut[2 * count] = firstVertex;
        runsOut[2 * count + 1] = vertexCount;
        runCount[0] = count + 1;
    }

    /**
     * 把当前 PoseStack 顶上的两个矩阵摊进复用数组。
     *
     * <p>一律走 JOML 访问器：本仓库这版 joml 的字段可见性不保证，访问器才是稳定 API
     * （{@link BoneWalker} 里也是这么做的）。</p>
     */
    private static void storeMatrices(int boneIndex, PoseStack poseStack) {
        final Matrix4f pose = poseStack.last().pose();
        final int m = boneIndex * 16;
        MATRICES[m] = pose.m00();
        MATRICES[m + 1] = pose.m01();
        MATRICES[m + 2] = pose.m02();
        MATRICES[m + 3] = pose.m03();
        MATRICES[m + 4] = pose.m10();
        MATRICES[m + 5] = pose.m11();
        MATRICES[m + 6] = pose.m12();
        MATRICES[m + 7] = pose.m13();
        MATRICES[m + 8] = pose.m20();
        MATRICES[m + 9] = pose.m21();
        MATRICES[m + 10] = pose.m22();
        MATRICES[m + 11] = pose.m23();
        MATRICES[m + 12] = pose.m30();
        MATRICES[m + 13] = pose.m31();
        MATRICES[m + 14] = pose.m32();
        MATRICES[m + 15] = pose.m33();

        final Matrix3f normal = poseStack.last().normal();
        final int n = boneIndex * 9;
        NORMAL_MATRICES[n] = normal.m00();
        NORMAL_MATRICES[n + 1] = normal.m01();
        NORMAL_MATRICES[n + 2] = normal.m02();
        NORMAL_MATRICES[n + 3] = normal.m10();
        NORMAL_MATRICES[n + 4] = normal.m11();
        NORMAL_MATRICES[n + 5] = normal.m12();
        NORMAL_MATRICES[n + 6] = normal.m20();
        NORMAL_MATRICES[n + 7] = normal.m21();
        NORMAL_MATRICES[n + 8] = normal.m22();
    }

    /**
     * 按 std140 布局写一条记录。
     *
     * <p>只写前 {@code boneCount} 条：着色器用骨骼编号索引，编号一定小于 boneCount，
     * 后面的空间留着不管（同一块会被下一帧覆盖）。</p>
     */
    private static void writeUniform(ByteBuffer data, int boneCount, int packedLight, int packedOverlay, int renderColor) {
        for (int i = 0; i < boneCount; i++) {
            final int base = i * 16;
            for (int k = 0; k < 16; k++) {
                data.putFloat(MATRICES[base + k]);
            }
        }

        // std140 的 mat3 是「3 列，每列 4 个 float」：写 3 个分量后必须跳过第 4 个。
        // 数组是定长的，所以法线块固定从 Bones[128] 之后开始 —— 不能紧接上面写的 boneCount 条矩阵，
        // 否则每个骨骼读到的都是别人（或上一帧残留）的法线矩阵，光照会逐帧乱跳。
        data.position(NORMAL_BLOCK_OFFSET);
        for (int i = 0; i < boneCount; i++) {
            final int base = i * 9;
            for (int column = 0; column < 3; column++) {
                final int c = base + column * 3;
                data.putFloat(NORMAL_MATRICES[c]);
                data.putFloat(NORMAL_MATRICES[c + 1]);
                data.putFloat(NORMAL_MATRICES[c + 2]);
                data.putFloat(0f);
            }
        }

        data.position(LIGHT_OVERLAY_OFFSET);
        // 与着色器对齐：LightOverlay.xy 给方块光、.zw 给覆盖层（和原版 UV2 / UV1 的分工一样）。
        data.putInt(packedLight & 0xFFFF);
        data.putInt((packedLight >>> 16) & 0xFFFF);
        data.putInt(packedOverlay & 0xFFFF);
        data.putInt((packedOverlay >>> 16) & 0xFFFF);
        // 顶点色走常量缓冲后就不再是逐顶点属性了；这里按 ARGB 拆成 (r,g,b,a)/255，
        // 与原版顶点色写入的顺序一致。
        data.putFloat(((renderColor >> 16) & 0xFF) / 255f);
        data.putFloat(((renderColor >> 8) & 0xFF) / 255f);
        data.putFloat((renderColor & 0xFF) / 255f);
        data.putFloat(((renderColor >>> 24) & 0xFF) / 255f);
    }
}
