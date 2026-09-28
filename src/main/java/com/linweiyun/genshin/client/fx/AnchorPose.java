package com.linweiyun.genshin.client.fx;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 一次播放的 <b>Root 位姿</b>：位置 + 朝向（+ 缩放）。
 *
 * <p>Photon 的 {@code FXRuntime} 有一个恒存在的空 Root（{@code FXRuntime.ROOT_UUID}），
 * 编辑器里没有指定父级的对象都挂在它下面。所以我们<b>只动 Root</b>，
 * 整棵对象树就跟着走 —— 这也是内置 {@code BlockEffectExecutor} 的做法。
 *
 * <p>注意单位：{@link #rotation} 是 <b>四元数（弧度语义）</b>，
 * 和 {@code IFXEffectExecutor#setRotation(double, double, double)} 那个
 * <b>角度制</b>的便捷重载不是一回事，混用会得到 57.3 倍的旋转错误。
 */
public record AnchorPose(Vector3f position, Quaternionf rotation, Vector3f scale) {

    public static AnchorPose at(Vector3f position) {
        return new AnchorPose(position, new Quaternionf(), new Vector3f(1, 1, 1));
    }

    public static AnchorPose at(Vector3f position, Quaternionf rotation) {
        return new AnchorPose(position, rotation, new Vector3f(1, 1, 1));
    }

    /** 绕 Y 轴（世界朝上）转 {@code yawDegrees} 度。Minecraft 的实体朝向就是绕 Y 的偏航。 */
    public static Quaternionf yaw(float yawDegrees) {
        return new Quaternionf().rotationY((float) Math.toRadians(yawDegrees));
    }

    /** 同时带上俯仰（{@code pitch}）与偏航，常用于「朝视线方向」。 */
    public static Quaternionf look(float yawDegrees, float pitchDegrees) {
        return new Quaternionf().rotationYXZ(
                (float) Math.toRadians(-yawDegrees),
                (float) Math.toRadians(pitchDegrees),
                0f);
    }
}
