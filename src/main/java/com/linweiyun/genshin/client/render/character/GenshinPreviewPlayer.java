// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.client.render.character;

import com.geckolib.animatable.manager.AnimatableManager.ControllerRegistrar;
import com.linweiyun.genshin.client.combat.state.PlayerAnimationController;
import org.jetbrains.annotations.Nullable;

public class GenshinPreviewPlayer extends GenshinReplacedPlayer {
   @Nullable
   private final String previewAnimation;

   public GenshinPreviewPlayer() {
      this(null);
   }

   public GenshinPreviewPlayer(@Nullable String previewAnimation) {
      this.previewAnimation = previewAnimation;
   }

   @Override
   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(PlayerAnimationController.createPreview(this, this.previewAnimation));
   }

   @Nullable
   public String previewAnimationName() {
      return this.previewAnimation;
   }
}
