package com.linweiyun.genshin.client.fx;

import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.IEffectExecutor;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * <b>一直挂在某个角色身上的 Photon 特效</b>（常驻光环 / 武器特效 / 攻击拖尾）。
 *
 * <p>它自己就是一个 {@code IEffectExecutor}：Photon 每 tick / 每帧回调时，
 * 我们按 {@link PoseProvider} 算出的位姿去动 Runtime 的 <b>Root</b>。
 *
 * <h2>生命周期</h2>
 * <pre>
 * 每客户端 tick：anchor.setWanted(条件);  anchor.tick();
 * </pre>
 * <ul>
 *   <li>{@code wanted = true} 且当前没有可用 Runtime → {@code createRuntime()} + {@code emit()}；</li>
 *   <li>{@code wanted = false} → {@code destroy(false)}，残留自然消散；</li>
 *   <li>Runtime 被粒子引擎丢弃（切世界、{@code /photon_client clear_particles}）或一次性效果播完
 *       → 重新创建。<b>{@code isValid()} 是缓存 Runtime 时唯一正确的存活检查。</b></li>
 * </ul>
 *
 * <h2>资源缺失怎么办</h2>
 * {@code FXHelper.getFX} 在 {@code .fx} 还没导出时返回 {@code null}，
 * 这时整个锚点是<b>静默空转</b>的 —— 特效作者还没做素材时，游戏照常跑，不会崩也不会刷日志。
 * 这也是 Photon 官方建议的处理方式（加载失败是正常结果，不是异常）。
 */
public final class FxAnchor {

    /** 每帧算一次 Root 位姿；返回 {@code null} 表示「这一帧不动」（保持上次的位姿）。 */
    @FunctionalInterface
    public interface PoseProvider {
        @Nullable
        AnchorPose pose(Player player, float partialTicks);
    }

    private final Player player;
    private final Level level;
    @Nullable
    private final FX fx;
    private final PoseProvider poseProvider;
    /** 稳定的随机源，让使用 Random 函数的 Authored 效果在重播时可复现。 */
    private final RandomSource random = RandomSource.create(20260928L);

    @Nullable
    private FXRuntime runtime;
    private boolean wanted;

    public FxAnchor(Identifier fxId, Player player, PoseProvider poseProvider) {
        this.player = player;
        this.level = player.level();
        this.fx = FXHelper.getFX(fxId);
        this.poseProvider = poseProvider;
    }

    /** 资源没加载出来（还没导出 / 路径写错）时为 false。 */
    public boolean hasFx() {
        return fx != null;
    }

    public void setWanted(boolean wanted) {
        this.wanted = wanted;
    }

    /** 当前是否有一个真正在跑的实例。 */
    public boolean isAlive() {
        return runtime != null && runtime.isValid();
    }

    /** 每客户端 tick 调一次。 */
    public void tick() {
        if (fx == null) {
            return;
        }
        if (!wanted) {
            destroy(false);
            return;
        }
        // 没实例、实例被引擎丢弃、或一次性效果已经播完 —— 都重建一个
        if (runtime == null || !runtime.isValid() || runtime.isFinished()) {
            runtime = fx.createRuntime();
            runtime.emit(new Executor());
        }
    }

    public void destroy(boolean force) {
        if (runtime != null) {
            runtime.destroy(force);
            runtime = null;
        }
    }

    // ==================== Photon ↔ 本锚点的桥 ====================

    /**
     * Photon 的播放上下文。只把回调转回 {@link FxAnchor}，
     * 顺便提供 {@link Level} 与随机源。
     */
    private final class Executor implements IEffectExecutor {

        @Override
        public Level getLevel() {
            return level;
        }

        @Override
        public RandomSource getRandomSource() {
            return random;
        }

        /** 每 tick，每对象一次 —— 低频逻辑（这里啥也不用做）。 */
        @Override
        public void updateFXObjectTick(IFXObject object) {
            // 只处理 Root：动 Root 等于动整棵树，动别的对象会和 Authored 层级互相打架
        }

        /** 每帧，每对象一次 —— 平滑跟随就写在这里（拿得到 partialTicks）。 */
        @Override
        public void updateFXObjectFrame(IFXObject object, float partialTicks) {
            if (runtime == null || object != runtime.getRoot()) {
                return;
            }
            AnchorPose pose = poseProvider.pose(player, partialTicks);
            if (pose == null) {
                return;
            }
            object.updatePos(pose.position());
            object.updateRotation(pose.rotation());
            object.updateScale(pose.scale());
        }
    }
}
