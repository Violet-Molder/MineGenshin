package com.linweiyun.genshin.client.sound;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.util.Map;

/**
 * 「把角色音效补进原版声音表」的写入端 —— 一个<b>鸭子接口</b>。
 *
 * <h2>为什么不直接 mixin {@code SoundManager#apply}</h2>
 * 那是最直觉的注入点（原版刚 clear + 填完自己的表），但它的第一个参数是
 * {@code SoundManager.Preparations} —— 一个 <b>protected 嵌套类</b>，
 * 我们的 handler 写不出这个类型，而 Mixin 对 {@code @Inject} 要求
 * <b>签名逐参数完全一致</b>（用 {@code Object} 顶替也不行，实测直接崩在启动阶段）：
 *
 * <pre>
 * InvalidInjectionException: Invalid descriptor ... Expected
 * (Lnet/minecraft/client/sounds/SoundManager$Preparations;...)V but found (Ljava/lang/Object;...)V
 * </pre>
 *
 * <p>所以换成两半，<b>完全不碰无法访问的类型</b>：
 * <ol>
 *   <li>{@code SoundManagerMixin} 只做一件事：把 {@code SoundManager} 里那两张
 *       private 表通过这个接口暴露出来（{@code @Shadow} 字段 + 普通方法，
 *       没有 {@code @Inject}，也就没有签名匹配这回事）；</li>
 *   <li>触发时机改用 NeoForge 自己的 {@code SoundEngineLoadEvent} ——
 *       它在 {@code SoundManager#apply} 的最后一行 {@code soundEngine.reload()} 里触发，
 *       也就是<b>原版表刚填好之后</b>，时机和注入到 {@code apply} 的 TAIL 完全等价。</li>
 * </ol>
 *
 * <h2>两张表分别是什么</h2>
 * <ul>
 *   <li>{@link #genshin$soundEvents()} —— 事件 → 声音列表（{@code SoundManager.registry}）；</li>
 *   <li>{@link #genshin$soundFiles()} —— 资源路径 → 文件（{@code SoundManager.soundCache}）。
 *       它同时被 {@code SoundEngine} 持有（{@code ResourceProvider.fromMap} 按引用捕获），
 *       所以往里放东西就等于「让播放器找得到这个 ogg」。</li>
 * </ul>
 */
public interface CharacterSoundSink {

    /** 事件表：{@code SoundManager.registry}。 */
    Map<ResourceLocation, WeighedSoundEvents> genshin$soundEvents();

    /** 文件表：{@code SoundManager.soundCache}（key 是 {@link Sound#getPath()} 那种完整资源路径）。 */
    Map<ResourceLocation, Resource> genshin$soundFiles();
}
