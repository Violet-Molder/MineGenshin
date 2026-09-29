package com.linweiyun.genshin.client.render.optimize.gpu;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * 一帧内给「每个模型一份骨骼矩阵」用的常量缓冲环。
 *
 * <p>结构照 {@code DynamicUniformStorage}（原版给 dynamic transforms 用的那套）：一块环形缓冲，
 * 每写一条就前进一个 block，一帧结束翻到下一块。自己写一遍的原因是原版那个类对 blockSize
 * 用的是<b>向下取整</b>（{@code Mth.roundToward}）—— 14368 字节在 alignment 为 64 的驱动上会被截成
 * 14336，写入时直接 {@code BufferOverflowException}。这里改成向上取整，任何 alignment 都安全。</p>
 *
 * <h2>容量</h2>
 * 一帧里每个可见角色写一条，同屏几十个角色时初始 2 条会不够，所以满了就翻倍重建；
 * 旧缓冲要留到帧末才关（这一帧已经发出去的绘制还指着它）。
 */
public final class SkinDataStorage implements AutoCloseable {

    /** 常量缓冲里最多容纳的骨骼数，与 {@code entity_skinned.vsh} 的 {@code Bones[128]} 一致。 */
    public static final int MAX_BONES = 128;

    /**
     * std140 布局的总字节数：{@code mat4 Bones[128]} = 8192、{@code mat3 NormalBones[128]} = 6144、
     * {@code ivec4 LightOverlay} = 16、{@code vec4 Color} = 16。
     */
    public static final int SIZE = MAX_BONES * 64 + MAX_BONES * 48 + 16 + 16;

    /** 往映射出来的字节里写一条数据。 */
    public interface Writer {
        void write(ByteBuffer data);
    }

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);
    private static @Nullable SkinDataStorage instance;
    /** 建过一次失败就彻底不试了：这种失败（驱动不支持 / 映射失败）不会因为下一帧而变好。 */
    private static boolean unavailable;

    private final List<MappableRingBuffer> retired = new ArrayList<>(4);
    private final int blockSize;
    private int capacity;
    private int nextBlock;
    private MappableRingBuffer ring;

    /**
     * 取当前帧的存储。
     *
     * @return 设备还没初始化时为 {@code null}（这种情况不设失败标记，下一帧会重新试）
     */
    public static @Nullable SkinDataStorage get() {
        if (unavailable) {
            return null;
        }

        final SkinDataStorage current = instance;
        if (current != null) {
            return current;
        }

        final GpuDevice device = RenderSystem.tryGetDevice();
        if (device == null) {
            return null;
        }

        try {
            final SkinDataStorage created = new SkinDataStorage(device);
            instance = created;
            return created;
        } catch (Throwable t) {
            unavailable = true;
            LOGGER.warn("[RenderOptimize] 骨骼常量缓冲创建失败，GPU 蒙皮本会话停用", t);
            return null;
        }
    }

    /** 帧末翻块：这一帧写过的所有矩阵都已经提交完毕，指针可以回到起点。 */
    public static void endFrame() {
        final SkinDataStorage current = instance;
        if (current != null) {
            current.rotate();
        }
    }

    /** 资源重载时整块退回（缓存为空或设备不可用时是安全的空操作）。 */
    public static void closeAll() {
        final SkinDataStorage current = instance;
        instance = null;
        if (current != null) {
            current.close();
        }
    }

    private SkinDataStorage(GpuDevice device) {
        final int alignment = device.getDeviceInfo().limits().minUniformOffsetAlignment();
        // 必须向上取整：写入的字节数正好是 SIZE，向下取整会让 block 装不下。
        this.blockSize = Math.ceilDiv(SIZE, alignment) * alignment;
        this.capacity = 2;
        this.ring = new MappableRingBuffer(() -> "MineGenshin skin data x" + this.blockSize,
                GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM, this.blockSize * this.capacity);
    }

    /**
     * 写一条骨骼矩阵数据。
     *
     * @return 可以直接 {@code setUniform("SkinData", slice)} 的切片；失败时 {@code null}
     */
    public @Nullable GpuBufferSlice write(Writer writer) {
        try {
            if (this.nextBlock >= this.capacity) {
                grow();
            }

            final int offset = this.nextBlock * this.blockSize;
            final GpuBufferSlice slice = this.ring.currentBuffer().slice(offset, this.blockSize);

            try (GpuBufferSlice.MappedView view = slice.map(false, true)) {
                final ByteBuffer data = view.data();
                data.position(0);
                writer.write(data);
            }

            this.nextBlock++;
            return slice;
        } catch (Throwable t) {
            LOGGER.warn("[RenderOptimize] 骨骼矩阵写入失败，本次绘制回退 CPU 蒙皮", t);
            return null;
        }
    }

    private void grow() {
        // 旧缓冲不能立刻关：这一帧已经发出去的绘制还在读它，留到 endFrame 统一关。
        this.retired.add(this.ring);
        this.capacity *= 2;
        this.ring = new MappableRingBuffer(() -> "MineGenshin skin data x" + this.blockSize,
                GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM, this.blockSize * this.capacity);
        this.nextBlock = 0;
    }

    private void rotate() {
        this.nextBlock = 0;
        this.ring.rotate();

        if (!this.retired.isEmpty()) {
            for (int i = 0; i < this.retired.size(); i++) {
                this.retired.get(i).close();
            }
            this.retired.clear();
        }
    }

    @Override
    public void close() {
        for (int i = 0; i < this.retired.size(); i++) {
            this.retired.get(i).close();
        }
        this.retired.clear();
        this.ring.close();
    }
}
