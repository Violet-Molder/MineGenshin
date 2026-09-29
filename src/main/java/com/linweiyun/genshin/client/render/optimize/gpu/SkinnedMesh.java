package com.linweiyun.genshin.client.render.optimize.gpu;

import com.linweiyun.genshin.client.render.optimize.geo.CompiledBone;
import com.linweiyun.genshin.client.render.optimize.geo.CompiledGeoModel;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

/**
 * 一份「常驻显存」的模型几何：顶点位置/UV 是模型空间的常量，法线是骨骼空间的常量。
 *
 * <h2>为什么顶点表能常驻</h2>
 * CPU 蒙皮每帧要重算 4896 个顶点（约 1.2 万次矩阵乘法）再写进动态顶点缓冲；而顶点本身
 * 只有「骨骼世界矩阵」一个变量。把它们连同骨骼编号一起放进一块静止的顶点缓冲后，每帧
 * 就只剩 83 根骨骼的矩阵要传 —— 顶点数据一辈子只上传一次。
 *
 * <h2>顶点格式的属性顺序不能改</h2>
 * Vulkan 后端是按 {@code VertexFormat#getElements()} 的顺序从 0 开始依次分配属性 location 的
 * （见 {@code VulkanRenderPipeline} 里的 {@code attribLocation++}），而 GLSL 顶点输入的 location
 * 由声明顺序决定。所以这里的顺序必须与 {@code entity_skinned.vsh} 的
 * {@code Position → UV0 → Normal → BoneIds} 逐字一致，换顺序 = 属性错位。
 *
 * <p>顶点在缓冲里的排布顺序就是 {@link CompiledGeoModel#bonesByIndex}
 * （先自己、再子树的深度优先前序），于是「一根骨骼的几何」永远是缓冲里的一段连续区间 ——
 * 隐藏骨骼时跳过一个区间即可，不需要索引表。</p>
 */
public final class SkinnedMesh {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);

    /** 顶点格式：32 字节 / 顶点（0 位置、12 UV、20 法线、24 骨骼编号）。 */
    public static final VertexFormat FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("Normal", GpuFormat.RGBA8_SNORM)
            .addAttribute("BoneIds", GpuFormat.RGBA16_UINT)
            .build();

    private static final int BYTES_PER_VERTEX = 32;

    private final GpuBuffer buffer;
    private final int quadCount;
    private final int vertexCount;

    private SkinnedMesh(GpuBuffer buffer, int quadCount, int vertexCount) {
        this.buffer = buffer;
        this.quadCount = quadCount;
        this.vertexCount = vertexCount;
    }

    /**
     * 把预编译几何打包成顶点缓冲。
     *
     * <p>设备还没就绪、显存申请失败、或打包结果与编译期统计的顶点数对不上时返回 {@code null}，
     * 由调用方回退 CPU 蒙皮 —— 这里不抛异常，因为每帧的渲染循环不该被一条优化路径带崩。</p>
     */
    public static @Nullable SkinnedMesh compile(CompiledGeoModel model) {
        final GpuDevice device = RenderSystem.tryGetDevice();
        if (device == null) {
            return null;
        }

        final int vertexCount = model.vertexCount;
        if (vertexCount <= 0) {
            return null;
        }

        // 必须是「直接缓冲」：LWJGL 给原生层传地址时取的是缓冲区内部的 address 字段
        // （memAddress = position + UNSAFE.getLong(buffer, ADDRESS)），堆缓冲区该字段恒为 0，
        // 于是驱动拿到的是「空指针 + 非零长度」—— 它会在原生层直接读空指针崩掉整个游戏，
        // 而不是抛一个能被捕获的 Java 异常（这正是 2026-09-25 两次 nvoglv64.dll 崩溃的成因）。
        final ByteBuffer data = MemoryUtil.memAlloc(vertexCount * BYTES_PER_VERTEX).order(ByteOrder.nativeOrder());
        try {
            int written = 0;
            final CompiledBone[] bones = model.bonesByIndex;

            for (int i = 0; i < bones.length; i++) {
                written += writeBone(data, bones[i]);
            }

            // 顶点数是编译期算出来的常量；对不上说明布局假设被改过，宁可回退也不要上传错位数据。
            if (written != vertexCount) {
                return null;
            }

            data.flip();
            final GpuBuffer buffer = upload(device, data);
            if (buffer == null) {
                return null;
            }
            return new SkinnedMesh(buffer, model.quadCount, vertexCount);
        } finally {
            // 两个后端都在这次调用内就把数据读走了：GL 是 glNamedBufferStorage 直接拷进显存，
            // Vulkan 是先拷进 CPU 可见的暂存区再记录一次缓冲拷贝。所以这里立刻释放是安全的。
            MemoryUtil.memFree(data);
        }
    }

    /**
     * 申请顶点缓冲并上传数据。
     *
     * <p>显存不足或被驱动拒绝时返回 {@code null} 而不是往上抛：GPU 蒙皮是画质无关的可选优化，
     * 失败应该退回 CPU 蒙皮继续画，而不是把整帧渲染带崩。同一次失败只会记一条日志 ——
     * 上层的 {@link SkinnedMeshCache} 会把失败的模型记成空条目，不会每帧重试（与
     * {@code SkinDataStorage} 的失败处理同一套路）。</p>
     */
    private static @Nullable GpuBuffer upload(GpuDevice device, ByteBuffer data) {
        try {
            return device.createBuffer(() -> "MineGenshin skinned mesh", GpuBuffer.USAGE_VERTEX, data);
        } catch (Throwable failure) {
            LOGGER.warn("[RenderOptimize] 蒙皮顶点缓冲创建失败，该模型回退 CPU 蒙皮", failure);
            return null;
        }
    }

    /** @return 写出的顶点数 */
    private static int writeBone(ByteBuffer data, CompiledBone bone) {
        final int quads = bone.quadCount;
        if (quads == 0) {
            return 0;
        }

        final float[] vertices = bone.vertices;
        final float[] normals = bone.normals;
        final byte[] masks = bone.normalFixMask;
        final int boneIndex = bone.index;

        int vi = 0;
        int ni = 0;
        for (int q = 0; q < quads; q++) {
            // 法线存的是「骨骼空间」的常量，符号修正（fixInvertedFlatCube）留到着色器里做 ——
            // 原版的判据用的是「已经变换到世界空间」的法线，提前判会得到不同结果。
            final byte nx = snorm(normals[ni]);
            final byte ny = snorm(normals[ni + 1]);
            final byte nz = snorm(normals[ni + 2]);
            final short mask = (short) (masks[q] & 0xFF);

            for (int k = 0; k < 4; k++) {
                data.putFloat(vertices[vi]);
                data.putFloat(vertices[vi + 1]);
                data.putFloat(vertices[vi + 2]);
                data.putFloat(vertices[vi + 3]);
                data.putFloat(vertices[vi + 4]);
                data.put(nx).put(ny).put(nz).put((byte) 0);
                data.putShort((short) boneIndex);
                data.putShort((short) 0);
                data.putShort((short) 0);
                data.putShort(mask);
                vi += 5;
            }
            ni += 3;
        }
        return quads * 4;
    }

    private static byte snorm(float value) {
        return (byte) ((int) (Mth.clamp(value, -1f, 1f) * 127f) & 0xFF);
    }

    /** 常驻顶点缓冲，绘制时直接 {@code slice()} 绑到 binding 0。 */
    public GpuBuffer buffer() {
        return this.buffer;
    }

    public int quadCount() {
        return this.quadCount;
    }

    public int vertexCount() {
        return this.vertexCount;
    }

    /** 整块几何的索引数（QUADS 拓扑：每 quad 6 个索引），用来给顺序索引缓冲做容量上界。 */
    public int indexCount() {
        return this.quadCount * 6;
    }

    /** 显存回收：模型被 GC 或资源重载时由 {@link SkinnedMeshCache} 调用。 */
    public void close() {
        this.buffer.close();
    }
}
