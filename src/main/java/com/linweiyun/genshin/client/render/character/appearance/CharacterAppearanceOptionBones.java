// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.client.render.character.appearance;

import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo.BoneUpdater;
import com.linweiyun.genshin.client.combat.state.AnimationStateSync;
import com.linweiyun.genshin.core.character.PGCharacter;
import java.util.Map;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public final class CharacterAppearanceOptionBones {
   private CharacterAppearanceOptionBones() {
   }

   @Nullable
   public static BoneUpdater<GeoRenderState> updaterFor(@Nullable Player player, @Nullable PGCharacter character) {
      if (player != null && character != null) {
         boolean normalState = "default".equals(AnimationStateSync.stateOf(player));
         return updater(character.appearanceData().normalStateBones(character.getAppearance(), character.currentWeaponType()), normalState);
      } else {
         return null;
      }
   }

   public static BoneUpdater<GeoRenderState> updaterForPreview(int mask, PGCharacter character) {
      return updater(character.appearanceData().normalStateBones(mask, character.currentWeaponType()), true);
   }

   private static BoneUpdater<GeoRenderState> updater(Map<String, Boolean> bones, boolean normalState) {
      return normalState && !bones.isEmpty() ? (renderPassInfo, snapshots) -> bones.forEach((bone, show) -> {
         float scale = show ? 1.0F : 0.0F;
         snapshots.ifPresent(bone, snapshot -> {
            snapshot.setScale(scale, scale, scale);
            if (show) {
               snapshot.skipRender(false);
               snapshot.skipChildrenRender(false);
            }
         });
      }) : (renderPassInfo, snapshots) -> {};
   }
}
