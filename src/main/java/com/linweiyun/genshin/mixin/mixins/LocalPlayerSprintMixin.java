package com.linweiyun.genshin.mixin.mixins;

import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 疾跑不再要求「朝前」：只要移动向量非零，就当作可以起跑 / 可以保持疾跑。
 *
 * <p>替掉两处 {@code Input#hasForwardImpulse()} 判断：一处是 {@code aiStep} 里
 * 「保持疾跑」的判据，一处是 {@code hasEnoughImpulseToStartSprinting} 里
 * 「能不能起跑」的判据。两处都换成 {@link #minegenshin$isMoving(Input)}，
 * 于是横向、后退移动也能进入并保持疾跑。</p>
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerSprintMixin {

    /** {@code aiStep} 里的「保持疾跑」判据。 */
    @Redirect(method = "aiStep",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/Input;hasForwardImpulse()Z"))
    private boolean minegenshin$keepSprintingInAnyDirection(Input input) {
        return minegenshin$isMoving(input);
    }

    /** {@code hasEnoughImpulseToStartSprinting} 里的「能不能起跑」判据。 */
    @Redirect(method = "hasEnoughImpulseToStartSprinting",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/Input;hasForwardImpulse()Z"))
    private boolean minegenshin$startSprintingInAnyDirection(Input input) {
        return minegenshin$isMoving(input);
    }

    /** 本帧有没有位移输入。 */
    private static boolean minegenshin$isMoving(Input input) {
        return input.getMoveVector().lengthSquared() > 0.0F;
    }
}
