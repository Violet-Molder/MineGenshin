// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.client.render.character.appearance;

import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo.BoneUpdater;
import com.linweiyun.genshin.client.combat.state.AnimationStateSync;
import com.linweiyun.genshin.core.character.polearm.shenhe.ShenheResources;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public final class CharacterFaceBones {
   public static final String DEFAULT_FACE_BONE = "MEyes";
   public static final String STANDBY_FACE_ROOT = "yushe";
   public static final String CLOSED_EYE_FACE_BONE = "biyang1";
   public static final List<String> STANDBY_SIBLING_BONES = List.of(
      "kuqi1", "kuqi2", "jidon", "wuyu", "biyang_Left", "biyang_Right", "biyang_RightJD", "biyang_LeftJD"
   );
   private static final BoneUpdater<GeoRenderState> HIDE_STANDBY = (renderPassInfo, snapshots) -> snapshots.ifPresent("yushe", snapshot -> {
      snapshot.skipRender(true);
      snapshot.skipChildrenRender(true);
   });
   private static final BoneUpdater<GeoRenderState> SHOW_CLOSED_EYE = (renderPassInfo, snapshots) -> {
      snapshots.ifPresent("MEyes", snapshot -> {
         snapshot.skipRender(true);
         snapshot.skipChildrenRender(true);
      });

      for (String bone : STANDBY_SIBLING_BONES) {
         snapshots.ifPresent(bone, snapshot -> {
            snapshot.skipRender(true);
            snapshot.skipChildrenRender(true);
         });
      }
   };

   private CharacterFaceBones() {
   }

   public static BoneUpdater<GeoRenderState> updaterFor(@Nullable Player player) {
      return updaterForState(player == null ? null : AnimationStateSync.stateOf(player));
   }

   public static BoneUpdater<GeoRenderState> updaterForState(@Nullable String animationState) {
      return animationState != null && ShenheResources.CLOSED_EYE_ANIMATIONS.contains(animationState) ? SHOW_CLOSED_EYE : HIDE_STANDBY;
   }
}
