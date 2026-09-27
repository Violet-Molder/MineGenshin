package com.linweiyun.genshin.client.render.optimize.gpu;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.RenderSystem;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import java.util.OptionalLong;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * 本模组 GPU 蒙皮绘制的<b>真 GPU 时间</b>（时间戳查询）。
 *
 * <h2>为什么需要它</h2>
 * {@code walk} / {@code us/model} 量的是「CPU 交给渲染管线多少活」，而把顶点搬到显存之后
 * 真正要回答的问题变成「GPU 有没有变贵」。这个问题只能用 GPU 侧的计时回答，所以这里用
 * {@code createTimestampQueryPool} + {@code CommandEncoder#writeTimestamp} 打一对时间戳，
 * 构造与 vanilla 的 {@code TimerQuery}（F3 里 {@code GPU: xx%} 的来源）完全同构：
 * 三格轮转、每格两个时间戳（开始 / 结束）、隔帧取回、乘 {@code timestampPeriod} 换成纳秒。
 *
 * <h2>量的是哪一段</h2>
 * {@code SkinnedFeatureRenderer#executeGroup} 让整组绘制共用一条 command buffer：
 * 开始时间戳写在第一个 render pass 之前、结束时间戳写在最后一个之后。于是量到的是
 * <b>这一段 GPU 时间轴上的跨度</b> —— 它包含了绘制之间的 CPU 间隙（记录命令的时间），
 * 所以是「本模组这批绘制的 GPU 占用」的上界，而不是逐 pass 精确求和。
 * 这个偏差方向是安全的（宁可高估自己的开销），而且和 vanilla 报 GPU 利用率的口径一致。
 *
 * <h2>失败怎么办</h2>
 * 驱动不支持时间戳查询、查询池创建失败、取回抛异常：一律把整条读数永久置为不可用
 * （{@link #nanos()} 返回 0，F3 那一栏直接不显示），绝不影响绘制本身。
 * GPU 蒙皮是可选优化，读数更是可选中的可选。
 */
public final class SkinnedGpuTimer {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);

    /** 三格轮转：写第 N 帧的槽位时，第 N-2 帧的值已经可以取回，不需要等 GPU 追上来。 */
    private static final int ROTATIONS = 3;
    /** 一格两个时间戳（开始 / 结束）。 */
    private static final int POOL_SIZE = ROTATIONS * 2;

    private static @Nullable SkinnedGpuTimer instance;
    /** 建过一次失败就彻底不试：这类失败（驱动不支持 / 设备限制）不会因为下一帧而变好。 */
    private static boolean unavailable;

    private final GpuQueryPool pool;
    private final float timestampPeriod;
    private final long[] resultsNanos = new long[ROTATIONS];
    private final boolean[] valid = new boolean[ROTATIONS];
    /** 该格已经写了开始时间戳、还没写结束（或异常中断）。 */
    private final boolean[] pending = new boolean[ROTATIONS];

    private int rotation = ROTATIONS - 1;
    /** 本帧是否已经打过开始时间戳：防止 begin/end 不成对时写出错位的结束戳。 */
    private boolean recording;

    private SkinnedGpuTimer(GpuDevice device) {
        this.pool = device.createTimestampQueryPool(POOL_SIZE);
        this.timestampPeriod = device.getDeviceInfo().timestampPeriod();
    }

    /**
     * 本模组这批绘制的 GPU 时间（纳秒，最近三帧平均）。
     *
     * @return 0 表示还没有可用值（第一帧、读数被禁用、或设备不支持）
     */
    public static long nanos() {
        final SkinnedGpuTimer timer = current();
        if (timer == null) {
            return 0L;
        }

        long sum = 0L;
        int sampled = 0;
        for (int i = 0; i < ROTATIONS; i++) {
            timer.pollSlot(i);
            if (timer.valid[i]) {
                sum += timer.resultsNanos[i];
                sampled++;
            }
        }
        return sampled == 0 ? 0L : sum / sampled;
    }

    /** 在第一个 render pass 之前调用；{@code encoder} 必须是这一组绘制共用的那一条。 */
    public static void begin(CommandEncoder encoder) {
        final SkinnedGpuTimer timer = instance();
        if (timer == null) {
            return;
        }

        try {
            timer.rotation = (timer.rotation + 1) % ROTATIONS;
            final int slot = timer.rotation;

            // 这一格如果还欠着上次取回的值：先试着取一次，取不到就直接丢掉。
            // 丢掉一格只让平均值少一帧，但能保证绝不会把「上一帧的时间戳」当成新值读进来。
            if (timer.pending[slot]) {
                timer.pollSlot(slot);
                timer.pending[slot] = false;
            }

            encoder.writeTimestamp(timer.pool, slot * 2);
            timer.recording = true;
        } catch (Throwable failure) {
            timer.recording = false;
            disable(failure);
        }
    }

    /** 在最后一个 render pass 之后调用。 */
    public static void end(CommandEncoder encoder) {
        final SkinnedGpuTimer timer = current();
        if (timer == null || !timer.recording) {
            return;
        }

        try {
            encoder.writeTimestamp(timer.pool, timer.rotation * 2 + 1);
            timer.pending[timer.rotation] = true;
        } catch (Throwable failure) {
            disable(failure);
        } finally {
            timer.recording = false;
        }
    }

    /** 整个读数下线（不再建池、不再打点）。 */
    public static void disable(@Nullable Throwable cause) {
        if (unavailable) {
            return;
        }
        unavailable = true;
        instance = null;
        LOGGER.warn("[RenderOptimize] GPU 时间戳查询不可用，F3 不再显示 GPU 时间（不影响绘制）", cause);
    }

    private static @Nullable SkinnedGpuTimer instance() {
        if (unavailable) {
            return null;
        }

        final SkinnedGpuTimer existing = instance;
        if (existing != null) {
            return existing;
        }

        final GpuDevice device = RenderSystem.tryGetDevice();
        if (device == null) {
            // 设备还没就绪：这不是失败，下一帧再试
            return null;
        }

        try {
            final SkinnedGpuTimer created = new SkinnedGpuTimer(device);
            instance = created;
            return created;
        } catch (Throwable failure) {
            disable(failure);
            return null;
        }
    }

    /** 已建好的实例；不负责创建（供取值路径用，避免取值时反而去建池）。 */
    private static @Nullable SkinnedGpuTimer current() {
        return unavailable ? null : instance;
    }

    private void pollSlot(int slot) {
        if (!this.pending[slot]) {
            return;
        }

        try {
            final OptionalLong[] values = this.pool.getValues(slot * 2, 2);
            if (values.length < 2 || values[0].isEmpty() || values[1].isEmpty()) {
                // GPU 还没执行到：留到下一帧再取
                return;
            }

            final long delta = values[1].getAsLong() - values[0].getAsLong();
            this.resultsNanos[slot] = (long) ((float) delta * this.timestampPeriod);
            this.valid[slot] = true;
            this.pending[slot] = false;
        } catch (Throwable failure) {
            disable(failure);
        }
    }
}
