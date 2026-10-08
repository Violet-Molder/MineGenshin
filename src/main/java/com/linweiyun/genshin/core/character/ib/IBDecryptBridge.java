package com.linweiyun.genshin.core.character.ib;

import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.IOException;

/**
 * 调用对方模组的模型解密模块 —— <b>只调用，不改动</b>。
 *
 * <h2>为什么这么做</h2>
 * 对方的真模型是 XOR 之后伪装成 {@code sounds/&lt;id&gt;_bgm.ogg} 的，还原算法在对方代码里。
 * 本 MOD 不复制、不重写那套算法：对方的资源包正常情况下已经在全局生效
 * （{@link IBLlink#modelBytes} 的直读路径），只有直读拿到的不是明文时才会走到这里，
 * 调用对方自己对外那一层（{@code net.luoshu.imaginarybranch.characters.model.ModelDataPack}）取回明文。
 *
 * <h2>为什么用反射</h2>
 * 本 MOD 不依赖对方（对方不在时同样要能构建、能启动），所以不能在编译期引用它的类型。
 * 反射调用的入口是对方<b>公开</b>的那一层：一个无参构造 + {@code getResource(PackType, ResourceLocation)}，
 * 与对方自己的资源包走的是同一条路径。
 *
 * <h2>失败怎么办</h2>
 * 对方不在、类找不到、接口对不上、调用抛异常 —— 一律返回 {@code null}，由调用方按「这个模型拿不到」
 * 处理（跳过该资源、日志一条警告）。这里绝不抛异常出去，也绝不尝试兜底解密。
 */
public final class IBDecryptBridge {

    /** 对方提供模型明文的那一层。 */
    private static final String MODEL_PACK_CLASS = "net.luoshu.imaginarybranch.characters.model.ModelDataPack";

    /** 取明文的入口：{@code getResource(PackType, ResourceLocation)} → {@code IoSupplier<InputStream>}。 */
    private static final String GET_RESOURCE_METHOD = "getResource";

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);

    private static volatile boolean unavailable;

    private IBDecryptBridge() {
    }

    /**
     * 取对方某个角色的模型明文。
     *
     * @param ibCharacterId 对方侧角色 id
     * @return 解密后的模型 JSON 字节；这条路走不通时返回 null
     */
    @Nullable
    public static byte[] modelBytes(String ibCharacterId) {
        if (unavailable || ibCharacterId == null || ibCharacterId.isEmpty()) {
            return null;
        }

        try {
            Class<?> packClass = Class.forName(MODEL_PACK_CLASS);
            Object pack = packClass.getDeclaredConstructor().newInstance();
            Object supplier = packClass
                    .getMethod(GET_RESOURCE_METHOD, PackType.class, ResourceLocation.class)
                    .invoke(pack, PackType.CLIENT_RESOURCES,
                            IBLink.asset("geo/" + ibCharacterId + ".geo.json"));

            if (!(supplier instanceof IoSupplier<?> ioSupplier)) {
                return null;
            }

            try (InputStream in = (InputStream) ioSupplier.get()) {
                return in == null ? null : in.readAllBytes();
            }
        } catch (ClassNotFoundException | NoSuchMethodException notThere) {
            unavailable = true;
            LOGGER.info("[IBDecryptBridge] 对方没有可调用的模型解密入口（{}），后续不再尝试", MODEL_PACK_CLASS);
            return null;
        } catch (ReflectiveOperationException | IOException | RuntimeException failed) {
            LOGGER.warn("[IBDecryptBridge] 调用对方解密模块失败：{}", failed.toString());
            return null;
        }
    }
}
