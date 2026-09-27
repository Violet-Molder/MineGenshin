// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.client.render.character;

import com.geckolib.animatable.GeoReplacedEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager.ControllerRegistrar;
import com.geckolib.util.GeckoLibUtil;
import com.linweiyun.genshin.client.combat.state.PlayerAnimationController;
import com.linweiyun.genshin.core.system.combat.animation.animatable.IPlayerAnimatableProxy;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public class GenshinReplacedPlayer implements GeoReplacedEntity, IPlayerAnimatableProxy {
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this, false);
   @Nullable
   private Player playerEntity;

   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(PlayerAnimationController.create(this));
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }

   @Nullable
   @Override
   public Player getPlayerEntity() {
      return this.playerEntity;
   }

   @Override
   public void setPlayerEntity(@Nullable Player player) {
      this.playerEntity = player;
   }
}
