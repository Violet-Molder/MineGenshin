package com.linweiyun.genshin.client.render.optimize.walk;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;

/**
 * 骨骼旋转 —— {@code RenderUtil#optionalRotateZYX} 的零分配版本。
 *
 * <h2>为什么原版每根骨骼都要 new</h2>
 * GeckoLib 的 {@code optionalRotateZYX} 里每次都 {@code new Quaternionf()}，
 * 哪怕最终只是转 0 度也先建对象再判空。一个 83 骨骼的模型，每帧光这一个点
 * 就是 83 次分配（cube 层另算 204 次）。
 *
 * <h2>复用一个实例安全吗</h2>
 * 安全。这个四元数只在方法内部「填值 → 立刻交给 {@code PoseStack.mulPose}」，
 * {@code mulPose} 会把值读进矩阵、不保留引用；而 26.2 的实体渲染在客户端主线程上
 * 是串行的，不存在两个线程同时用同一个实例的情况。分支结构与原版逐条对齐
 * （单轴走单轴旋转，多轴走 ZYX），所以结果与原来逐位一致。
 */
public final class Rotations {

    /** 复用的临时四元数 —— 见类注释「复用一个实例安全吗」。 */
    private static final Quaternionf REUSED = new Quaternionf();

    private Rotations() {
    }

    /**
     * 绕 Z、Y、X 依次旋转（与 {@code RenderUtil#optionalRotateZYX} 等价）。
     *
     * @param reuseAlloc 复用一个实例（{@code render-optimize.zero-alloc-walk}）；
     *                   {@code false} 时与原版一样每次新建，用于对照
     */
    public static void rotateZYX(PoseStack poseStack, double z, double y, double x, boolean reuseAlloc) {
        if (z == 0 && y == 0 && x == 0) {
            return;
        }

        final Quaternionf quat = reuseAlloc ? REUSED : new Quaternionf();

        if (x == 0 && y == 0) {
            quat.rotationZ((float) z);
        } else if (x == 0 && z == 0) {
            quat.rotationY((float) y);
        } else if (y == 0 && z == 0) {
            quat.rotationX((float) x);
        } else {
            quat.rotationZYX((float) z, (float) y, (float) x);
        }

        poseStack.mulPose(quat);
    }
}
