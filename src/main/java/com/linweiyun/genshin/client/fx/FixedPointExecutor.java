package com.linweiyun.genshin.client.fx;

import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.IEffectExecutor;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * <b>「在指定位置生成、然后朝一个方向直线飞出去」</b>的一次性特效。
 *
 * <p>典型用法就是「放了技能之后，身前凭空出现一团东西并往正前方飞」——
 * 位置、朝向、速度全部由调用方（技能代码）给，和角色后续怎么动无关。
 *
 * <h2>位置是怎么推进的</h2>
 * <pre>
 * tick  ：origin += forward × speed                       （推进一个整刻）
 * frame ：pos = origin + forward × speed × partialTicks   （刻内插值，不抖）
 * </pre>
 * 只在 tick 里推进、只在 frame 里按 tick 起点插值算实际位置 ——
 * <b>两个回调都写位置会互相打架</b>（一个按整刻跳、一个按帧插值，加起来就是抖动）。
 *
 * <p>速度是<b>客户端自己的</b>。如果你更希望位移写死在特效里
 * （编辑器里给 Emitter 开 {@code WORLD} 模拟空间 + Velocity over Lifetime），
 * 把 {@code forwardSpeed} 传 0 即可 —— 两种做法都成立，看特效作者的习惯。
 */
public final class FixedPointExecutor implements IEffectExecutor {

    private final Level level;
    private final RandomSource random = RandomSource.create(20260928L);
    private final Quaternionf rotation;
    private final Vec3 forward;
    private final double forwardSpeed;

    /** tick 起点（每 tick 沿 {@link #forward} 前进一次）。 */
    private Vec3 origin;
    /** 还能活多少刻；<= 0 表示不自动结束，靠特效自己播完。 */
    private int remainingTicks;

    private FXRuntime runtime;

    /**
     * @param origin       生成位置（世界坐标）
     * @param yawDegrees   朝向（Minecraft 的偏航角，0 = +Z，90 = -X）
     * @param forwardSpeed 每刻前进多少格；0 = 位移完全交给特效自己
     */
    public FixedPointExecutor(Level level, Vec3 origin, float yawDegrees, double forwardSpeed) {
        this.level = level;
        this.origin = origin;
        this.forwardSpeed = forwardSpeed;
        this.forward = Vec3.directionFromRotation(0f, yawDegrees);
        this.rotation = AnchorPose.yaw(yawDegrees);
    }

    /** 自动结束的刻数。 */
    public FixedPointExecutor lifetime(int ticks) {
        this.remainingTicks = ticks;
        return this;
    }

    /** 创建 + 播放，返回实例（调用方一般只用来判断结束）。 */
    public FXRuntime start(FX fx) {
        runtime = fx.createRuntime();
        runtime.emit(this);
        return runtime;
    }

    /** 已经播完 / 被引擎丢弃，可以从管理表里摘掉了。 */
    public boolean isDone() {
        return runtime == null || runtime.isFinished() || !runtime.isValid();
    }

    @Override
    public Level getLevel() {
        return level;
    }

    @Override
    public RandomSource getRandomSource() {
        return random;
    }

    @Override
    public void updateFXObjectTick(IFXObject object) {
        if (runtime == null || object != runtime.getRoot()) {
            return;
        }
        if (remainingTicks > 0 && --remainingTicks == 0) {
            runtime.destroy(false);
            return;
        }
        origin = origin.add(forward.scale(forwardSpeed));
    }

    @Override
    public void updateFXObjectFrame(IFXObject object, float partialTicks) {
        if (runtime == null || object != runtime.getRoot()) {
            return;
        }
        Vec3 at = origin.add(forward.scale(forwardSpeed * partialTicks));
        object.updatePos(new Vector3f((float) at.x, (float) at.y, (float) at.z));
        object.updateRotation(rotation);
    }
}
