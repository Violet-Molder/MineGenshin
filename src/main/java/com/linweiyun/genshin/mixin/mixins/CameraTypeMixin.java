package com.linweiyun.genshin.mixin.mixins;

import net.minecraft.client.CameraType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 第三人称只留两个机位：<b>第一人称 ↔ 第三人称正后方</b>，
 * {@code THIRD_PERSON_FRONT}（「第二人称」正脸）不再启用。
 *
 * <h2>为什么注入 {@code cycle} 而不是拦截视角键</h2>
 * 视角键（{@code Minecraft} 里的 {@code keyTogglePerspective}）拿到的是
 * {@code options.getCameraType().cycle()} —— 原版换机位<b>只有这一个入口</b>，
 * 改这里就等于把这个三档循环收成两档：谁调都一样，不依赖按键处理那一段代码的顺序。
 * 顺带也就不会出现「切到正脸、下一 tick 被别的逻辑拽回来」那种闪一下的中间态。
 *
 * <h2>残留值</h2>
 * 存档里存着正脸的旧值、或者别的模组直接写 {@code options.setCameraType(...)} 的情况，
 * 由 {@code ThirdPersonCamera.onClientTick} 每 tick 钳回正后方兜住。
 */
@Mixin(CameraType.class)
public abstract class CameraTypeMixin {

    @Inject(method = "cycle()Lnet/minecraft/client/CameraType;", at = @At("HEAD"), cancellable = true)
    private void minegenshin$skipThirdPersonFront(CallbackInfoReturnable<CameraType> cir) {
        CameraType self = (CameraType) (Object) this;

        // 原版：VALUES[(ordinal + 1) % 3]，也就是 一 → 三后方 → 三正脸 → 一。
        // 现在去掉中间那一档：第一人称直接到正后方，其它（含正脸自己）都回第一人称。
        cir.setReturnValue(self == CameraType.FIRST_PERSON
                ? CameraType.THIRD_PERSON_BACK
                : CameraType.FIRST_PERSON);
    }
}
