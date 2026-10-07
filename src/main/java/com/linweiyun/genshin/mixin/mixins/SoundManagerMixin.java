package com.linweiyun.genshin.mixin.mixins;

import com.linweiyun.genshin.client.sound.CharacterSoundSink;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

/**
 * 把 {@code SoundManager} 的两张 private 表暴露给角色音效加载器。
 *
 * <h2>这个 mixin 为什么一个 {@code @Inject} 都没有</h2>
 * 往 {@code apply} 的末尾注入本来是最自然的做法，但那个方法的第一个参数
 * {@code SoundManager.Preparations} 是 protected 嵌套类，而 Mixin 要求 handler
 * 签名<b>逐参数完全一致</b>（{@code Object} 顶替会直接启动崩溃，实测过）。
 *
 * <p>所以这里只做「字段暴露」——{@code @Shadow} 字段 + 普通方法，没有签名匹配；
 * 触发时机交给 NeoForge 的 {@code SoundEngineLoadEvent}，见
 * {@code CharacterSounds#onSoundEngineLoad}。
 *
 * <p>两个字段在 {@code SoundManager} 里都是 {@code final} 的 {@code HashMap}，
 * 我们只 put、不替换引用，所以 {@code SoundEngine} 那边持有的
 * {@code ResourceProvider.fromMap(this.soundCache)} 也能看到新条目。
 */
@Mixin(SoundManager.class)
public abstract class SoundManagerMixin implements CharacterSoundSink {

    @Shadow
    @Final
    private Map<ResourceLocation, WeighedSoundEvents> registry;

    @Shadow
    @Final
    private Map<ResourceLocation, Resource> soundCache;

    @Override
    public Map<ResourceLocation, WeighedSoundEvents> genshin$soundEvents() {
        return this.registry;
    }

    @Override
    public Map<ResourceLocation, Resource> genshin$soundFiles() {
        return this.soundCache;
    }
}
