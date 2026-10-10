package com.linweiyun.genshin.mixin.mixins;

import net.minecraft.client.CameraType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CameraType.class)
public abstract class CameraTypeMixin {

    @Inject(method = "cycle()Lnet/minecraft/client/CameraType;", at = @At("HEAD"), cancellable = true)
    private void minegenshin$skipThirdPersonFront(CallbackInfoReturnable<CameraType> cir) {
        CameraType self = (CameraType) (Object) this;
        cir.setReturnValue(self == CameraType.FIRST_PERSON
                ? CameraType.THIRD_PERSON_BACK
                : CameraType.FIRST_PERSON);
    }
}
