package com.linweiyun.genshin.client.performance;

import com.linweiyun.genshin.client.damage.DamageIndicator;
import com.linweiyun.genshin.config.PerformanceConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

/**
 * 飘字的<b>每帧可见性规划</b>（渲染优化模块）。
 *
 * <h2>每帧做三件事</h2>
 * <ol>
 *   <li><b>剔除</b>：透明度已经归零的、超出距离的、在相机背后的一律不进提交列表；</li>
 *   <li><b>限量</b>：可见条数超过上限（默认 128）时按「离相机近的优先」保留，
 *       保证再密集的战斗也不会让每帧的提交量无限膨胀；</li>
 *   <li><b>烘焙锚点位姿</b>：把「相机相对位置 → 相机朝向 → 世界尺度」直接算成一个
 *       世界空间矩阵，交给渲染器逐顶点使用，省掉每帧的 {@code pushPose/popPose} 拷贝。</li>
 * </ol>
 *
 * <p>整个过程复用同一批 {@link Entry} 与 {@link Matrix4f} 实例、相机参数都以接口传入，
 * <b>稳态下每帧零分配</b>（早先这里每帧都要新建一个相机朝向的 {@code Vec3}）。</p>
 *
 * <p>耗时与条数交给 {@link IndicatorPerfStats}，按 F3 可以直接看到这两项：
 * 规划本身是几十微秒量级，飘字的真实开销在顶点生产那边。</p>
 */
public final class IndicatorFramePlanner {

    /** 旧 HUD 的 scale 以 GUI 像素为单位，换算到世界空间按 1080p / 自动 GUI 尺度校准 */
    public static final float GUI_PIXEL_TO_BLOCK = 0.0155f;

    /** 超过该距离不再提交几何（服务端广播半径 48，留足余量） */
    private static final double MAX_RENDER_DISTANCE = 96.0;
    private static final double MAX_RENDER_DISTANCE_SQR = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;

    /** 淡出尾声的透明度阈值，低于它就没必要再提交 */
    private static final float MIN_ALPHA = 0.004f;

    /** 一条待提交的飘字 */
    public static final class Entry {
        public IndicatorGlyphCache.Label label;
        /** 世界空间锚点位姿：事件位姿 ∘ 平移 ∘ 相机朝向 ∘ 缩放 */
        public final Matrix4f anchor = new Matrix4f();
        /** 相对相机的位置（采样时写入，烘焙位姿时用） */
        public float relX;
        public float relY;
        public float relZ;
        /** 这一帧的缩放（世界尺度前的字体像素倍率） */
        public float scale = 1.0f;
        /** 顶/底颜色（从飘字上拷贝一份，提交端不必再解引用飘字） */
        public int topColor = 0xFFFFFF;
        public int bottomColor = 0xFFFFFF;
        public float alpha;
        public int alpha8;
        public float distanceSqr;
    }

    /** 每帧复用的 Entry 池（稳态零分配） */
    private static final List<Entry> POOL = new ArrayList<>(64);
    private static final List<Entry> ENTRIES = new ArrayList<>(64);

    private IndicatorFramePlanner() {}

    /**
     * 规划本帧要提交的飘字。
     *
     * @param active    当前活跃飘字（{@link com.linweiyun.genshin.client.damage.DamageIndicatorManager#getActive()}）
     * @param framePose 事件位姿栈的当前位姿（世界渲染上下文）
     * @param camPos    相机位置
     * @param camRot    相机朝向（命名牌口径的 billboard 旋转）
     * @param camForward 相机朝向单位向量（用于剔除背后的飘字）
     * @param font      当前字体（资源重载后会换实例）
     * @return 可提交列表；<b>内容每帧被改写，不能跨帧持有</b>
     */
    public static List<Entry> plan(List<DamageIndicator> active,
                                   Matrix4fc framePose,
                                   Vec3 camPos,
                                   Quaternionf camRot,
                                   Vector3fc camForward,
                                   Font font) {
        ENTRIES.clear();
        if (active.isEmpty() || font == null) {
            IndicatorPerfStats.recordPlan(0L, active.size(), 0);
            return ENTRIES;
        }

        long startedAt = System.nanoTime();
        int maxRendered = maxRendered();
        boolean cullBehind = cullBehindCamera();

        // 每帧只取一次时钟：这一批飘字全部按同一个 now 采样动画
        long now = System.currentTimeMillis();
        float fwdX = camForward.x();
        float fwdY = camForward.y();
        float fwdZ = camForward.z();
        boolean cull = cullBehind && (fwdX != 0.0f || fwdY != 0.0f || fwdZ != 0.0f);

        for (DamageIndicator indicator : active) {
            // 先按「本轮已保留条数」取池子里的条目；被剔除的话这一格下一条继续用，不进列表
            Entry entry = entryAt(ENTRIES.size());
            indicator.sampleFrame(now, camPos.x, camPos.y, camPos.z, entry);

            if (entry.alpha <= MIN_ALPHA) {
                continue;
            }

            float distanceSqr = entry.relX * entry.relX + entry.relY * entry.relY + entry.relZ * entry.relZ;
            if (distanceSqr > MAX_RENDER_DISTANCE_SQR) {
                continue;
            }

            if (cull && fwdX * entry.relX + fwdY * entry.relY + fwdZ * entry.relZ <= 0.0f) {
                continue;
            }

            IndicatorGlyphCache.Label label = indicator.labelFor(font);
            if (label == null) {
                continue;
            }

            entry.label = label;
            entry.topColor = indicator.topColor;
            entry.bottomColor = indicator.bottomColor;
            entry.distanceSqr = distanceSqr;
            ENTRIES.add(entry);
        }

        if (ENTRIES.size() > maxRendered) {
            // 近的优先：超出上限的部分直接不提交（远端飘字本来也看不清）
            ENTRIES.sort((a, b) -> Float.compare(a.distanceSqr, b.distanceSqr));
            ENTRIES.subList(maxRendered, ENTRIES.size()).clear();
        }

        for (Entry entry : ENTRIES) {
            float scale = entry.scale * GUI_PIXEL_TO_BLOCK;
            entry.anchor.set(framePose)
                    .translate(entry.relX, entry.relY, entry.relZ)
                    .rotate(camRot)
                    // y 取负：字体像素坐标的 y 向下，世界的 y 向上
                    .scale(scale, -scale, scale);
        }
        IndicatorPerfStats.recordPlan(System.nanoTime() - startedAt, active.size(), ENTRIES.size());
        return ENTRIES;
    }

    /** 当前帧规划出的条数（调试用）。 */
    public static int plannedCount() {
        return ENTRIES.size();
    }

    /**
     * 退出世界时清空每帧复用表。
     *
     * <p>规划表里抓着 {@link DamageIndicator} 的引用，退世界后不清会把最后一批
     * 飘字对象一直拖住；池子本身也顺手丢掉，换世界后重新按需长回来。</p>
     */
    public static void reset() {
        ENTRIES.clear();
        for (Entry entry : POOL) {
            entry.label = null;
        }
        POOL.clear();
    }

    private static Entry entryAt(int index) {
        if (index < POOL.size()) {
            return POOL.get(index);
        }
        Entry entry = new Entry();
        POOL.add(entry);
        return entry;
    }

    private static int maxRendered() {
        return Math.max(1, readInt(PerformanceConfig.MAX_RENDERED_INDICATORS, 128));
    }

    private static boolean cullBehindCamera() {
        try {
            return PerformanceConfig.CULL_BEHIND_CAMERA.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static int readInt(net.neoforged.neoforge.common.ModConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
