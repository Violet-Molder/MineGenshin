// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.client.render.character.appearance;

import com.linweiyun.genshin.client.render.character.bones.BoneRenderState;
import com.linweiyun.genshin.client.render.character.bones.BoneUpdater;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public final class CharacterPuppetBones {
   public static final String WIND_UP_BONE = "FKey";
   public static final float SECONDS_PER_TURN = 5.0F;
   private static final float TICKS_PER_TURN = 100.0F;
   private static final float DEGREES_PER_TICK = -3.6F;
   private static final BoneUpdater<BoneRenderState> NONE = (renderPassInfo, snapshots) -> {};

   private CharacterPuppetBones() {
   }

   public static BoneUpdater<BoneRenderState> updaterFor(@Nullable Player player) {
      return player == null ? NONE : (renderPassInfo, snapshots) -> {
         float ticks = player.tickCount + renderPassInfo.renderState().getPartialTick();
         snapshots.ifPresent("FKey", snapshot -> snapshot.setRotation(0.0F, -3.6F * ticks, 0.0F));
      };
   }
}
