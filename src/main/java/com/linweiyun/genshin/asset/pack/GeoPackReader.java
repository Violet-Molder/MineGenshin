package com.linweiyun.genshin.asset.pack;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * 整包读取器的接口 —— <b>本仓库里唯一与「怎么把包读出来」有关的公开约定</b>。
 *
 * <p>接口本身不含任何格式细节：两个方法、参数与返回值都是字节。真正的实现在
 * {@link GeoPackReaders} 找到的那一份里。<b>没有读取器时本接口也不会有实现</b>，
 * 读取路径退回原样直读，构建与启动都不受影响。
 *
 * <h2>它管两件事</h2>
 * <ol>
 *   <li><b>单个文件</b>：{@link #read} 把磁盘上的 geo / 动画文件的字节转成可解析的字节；</li>
 *   <li><b>整个 {@code .minegenshin} 整包</b>：{@link #open} 把包拆成一条条条目。
 *       包内排布属于读取器的内部实现，公开代码只负责把字节递进来。</li>
 * </ol>
 */
public interface GeoPackReader {

    /**
     * 读单个资源文件的字节，返回可解析的字节。
     *
     * <p>实现必须是「没经手的原样返回」，因为开发期目录里两种形态都可能出现。
     *
     * @param raw   文件字节
     * @param where 出问题时用于定位的资源路径，可为 null
     */
    byte[] read(byte[] raw, @Nullable String where);

    /**
     * 读整包文件，得到「逻辑路径 → 条目字节」。
     *
     * @param raw   整包文件的原始字节
     * @param where 出问题时用于定位的资源路径，可为 null
     * @return 条目表；没有读取器、不是本类型的包、内容对不上时一律返回 null，
     *         对外表现就是「这个包读不出来」
     * @throws IllegalStateException 只在本实现已认下「这就是本类型的包」但内容残缺时抛
     */
    @Nullable
    Map<String, byte[]> open(byte[] raw, @Nullable String where);
}
