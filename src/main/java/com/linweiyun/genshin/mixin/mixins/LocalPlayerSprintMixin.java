// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.mixin.mixins;

import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerSprintMixin {
   @Redirect(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;hasForwardImpulse()Z"))
   private boolean minegenshin$sprintWindowAcceptsAnyDirection(ClientInput input) {
      return minegenshin$isMoving(input);
   }

   @Redirect(method = "canStartSprinting", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;hasForwardImpulse()Z"))
   private boolean minegenshin$canStartSprintingInAnyDirection(ClientInput input) {
      return minegenshin$isMoving(input);
   }

   @Redirect(method = "shouldStopRunSprinting", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;hasForwardImpulse()Z"))
   private boolean minegenshin$keepRunSprintingInAnyDirection(ClientInput input) {
      return minegenshin$isMoving(input);
   }

   @Redirect(method = "shouldStopSwimSprinting", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;hasForwardImpulse()Z"))
   private boolean minegenshin$keepSwimSprintingInAnyDirection(ClientInput input) {
      return minegenshin$isMoving(input);
   }

   @Redirect(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Input;backward()Z"))
   private boolean minegenshin$backwardKeepsSprintWindow(Input input) {
      return false;
   }

   private static boolean minegenshin$isMoving(ClientInput input) {
      return input.getMoveVector().lengthSquared() > 0.0F;
   }
}
