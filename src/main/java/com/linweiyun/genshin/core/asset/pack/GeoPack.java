package com.linweiyun.genshin.core.asset.pack;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * 自带 geo 资源的那个「整包文件」在<b>公开代码这一侧</b>的全部内容：文件放在哪、怎么读。
 *
 * <h2>包里面的排布不在这里</h2>
 * 条目怎么摆、怎么取出来，全在读取器里（见 {@link GeoPackReaders}）。这边只做一件事：
 * 把文件字节递过去。所以这一侧既没有格式，也没有任何算法。
 *
 * <h2>为什么合成一个文件</h2>
 * 逐文件分发时，文件名与目录会漏出「有哪些角色、各有多少个文件、哪个是模型哪个是动画」。
 * 合成一个包之后，磁盘上只有 {@link #RESOURCE_PATH} 这一条路径，条目名整段在包里面；
 * 顺带一次读入就能拿到全部索引，不必为每个资源各开一次流。
 *
 * <p>本类只用 JDK 与注解：构建期工具与游戏运行期都要能加载它。
 */
public final class GeoPack {

    /** 整包文件的后缀。 */
    public static final String SUFFIX = ".minegenshin";

    /** 整包文件在 {@code assets/minegenshin/} 之下的相对路径。 */
    public static final String RESOURCE_PATH = "geo/georesources" + SUFFIX;

    private GeoPack() {
    }

    /**
     * 读整包文件，得到「逻辑路径 → 条目字节」。
     *
     * <p>本方法<b>不判断</b>任何格式：是不是本类型的包、内容对不对，全部由
     * {@link GeoPackReaders} 给出的读取器决定。没有读取器时这里直接返回 null ——
     * 包读不出来、模型 / 动画显示不出来，但构建与启动都不受影响。
     *
     * @param raw   整包文件的原始字节
     * @param where 出问题时用于定位的位置，可为 null
     * @return 条目表；读不出来时返回 null
     * @throws IllegalStateException 读取器已认下这是本类型的包、但内容残缺（文件被截断 / 改坏）
     */
    @Nullable
    public static Map<String, byte[]> load(@Nullable byte[] raw, @Nullable String where) {
        if (raw == null || raw.length == 0) {
            return null;
        }
        return GeoPackReaders.get().open(raw, where);
    }
}
