package com.linweiyun.genshin.client.render.character;

import com.linweiyun.genshin.client.fx.AnchorPose;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * <b>「武器骨骼这一帧在世界哪儿」的缓存。</b>
 *
 * <p>骨骼位姿只有在<b>渲染的那一瞬间</b>才存在（它是动画采样 + 层级变换累乘的结果，
 * 不是实体状态），所以由渲染层 {@link WeaponAnchorGeoLayer} 每帧写、由 Photon 的帧回调读。
 * 这是 GeckoLib 官方给的路子 —— {@code RenderPassInfo#addBonePositionListener} 的注释原话是
 * "the only time they actually have a position of any kind"。
 *
 * <h2>新鲜度用「渲染帧」而不是「实体刻」</h2>
 * 时间戳直接取 {@link CharacterRenderDispatcher#renderFrame()}（它由 {@code RenderFrameEvent.Pre}
 * 每帧自增一次）。角色在第一人称、离屏、或被别的模组挡住时根本不会渲染，
 * 这时缓存自然过期，{@link #fresh} 返回 {@code null}，
 * 调用方就<b>保持上一次的位姿</b>，而不是把特效瞬移到世界原点。
 *
 * <p>读写都发生在渲染线程（写：模型提交阶段；读：Photon 的每帧回调），
 * 所以普通 {@link HashMap} 足够，不需要并发容器。
 */
public final class WeaponAnchorCache {

    /** 超过这么多<b>渲染帧</b>没更新就算过期。留几帧余量，避免帧序错位时闪一下。 */
    private static final int MAX_AGE_FRAMES = 3;

    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();

    /** 一次捕获的结果：世界坐标 + 世界朝向。 */
    public record Entry(Vector3f position, Quaternionf rotation, int frame) {
        public AnchorPose toPose() {
            return new AnchorPose(new Vector3f(position), new Quaternionf(rotation), new Vector3f(1, 1, 1));
        }
    }

    /** 由渲染层调用：写入某一帧的骨骼世界位姿。 */
    public static void put(Player player, Vector3f position, Quaternionf rotation) {
        int now = CharacterRenderDispatcher.renderFrame();
        ENTRIES.put(player.getUUID(), new Entry(
                new Vector3f(position),
                new Quaternionf(rotation).normalize(),
                now));
        if (ENTRIES.size() > 64) {
            ENTRIES.entrySet().removeIf(e -> now - e.getValue().frame() > MAX_AGE_FRAMES);
        }
    }

    /** 最近几帧内的骨骼位姿；没渲染过 / 已经过期时返回 {@code null}。 */
    @Nullable
    public static Entry fresh(Player player) {
        Entry entry = ENTRIES.get(player.getUUID());
        if (entry == null) {
            return null;
        }
        return CharacterRenderDispatcher.renderFrame() - entry.frame() > MAX_AGE_FRAMES ? null : entry;
    }

    public static void clear() {
        ENTRIES.clear();
    }

    private WeaponAnchorCache() {
    }
}
