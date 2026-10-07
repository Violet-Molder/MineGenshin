package com.linweiyun.genshin.mixin.mixins;

import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 疾跑不再要求「朝前」：只要移动向量非零，就当作可以起跑 / 可以保持疾跑。
 *
 * <p>要替三处「必须向前」的判据：{@code aiStep} 里「保持疾跑」那处是
 * {@code Input#hasForwardImpulse()}；{@code hasEnoughImpulseToStartSprinting} 里有两处 ——
 * 一处方法调用、一处直接读 {@code forwardImpulse} 字段，两处都要覆盖。
 * 三处统一换成 {@link #minegenshin$isMoving(Input)} / 等价的冲量，
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

    /**
     * {@code hasEnoughImpulseToStartSprinting} 里直接读字段的那一处：字段不是方法调用，
     * 单独用字段重定向覆盖。有位移输入时按「冲量足够」给值。
     */
    @Redirect(method = "hasEnoughImpulseToStartSprinting",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/Input;forwardImpulse:F"))
    private float minegenshin$impulseInAnyDirection(Input input) {
        return minegenshin$isMoving(input) ? 1.0F : input.forwardImpulse;
    }

    /** 本帧有没有位移输入。 */
    private static boolean minegenshin$isMoving(Input input) {
        return input.getMoveVector().lengthSquared() > 0.0F;
    }
}
