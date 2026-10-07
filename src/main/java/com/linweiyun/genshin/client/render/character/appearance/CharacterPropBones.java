// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.client.render.character.appearance;

import com.linweiyun.genshin.client.render.character.bones.BoneRenderState;
import com.linweiyun.genshin.client.render.character.bones.BoneUpdater;
import com.linweiyun.genshin.client.combat.state.AnimationStateSync;
import com.linweiyun.genshin.core.character.polearm.shenhe.ShenheResources;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public final class CharacterPropBones {
   private static final Logger LOGGER = ModLog.getLogger(LogGroup.ANIMATION);
   private static final Map<Player, Integer> LOG_LAST_VISIBLE = new WeakHashMap<>();
   public static final List<String> RIG_BONES = List.of("MFly", "FJO");
   public static final String MOUNT_BONE = "MFly";
   public static final String FJO_BONE = "FJO";
   public static final String TEA_BONE = "tea";
   public static final List<String> SCREEN_BONES = List.of("ysmGlow_texiao", "ysmGlow_texiao2");
   public static final List<String> PROP_BONES = List.copyOf(Stream.concat(Stream.concat(RIG_BONES.stream(), SCREEN_BONES.stream()), Stream.of("tea")).toList());
   public static final float SCREEN_FORWARD_PUSH = -8.0F;

   private CharacterPropBones() {
   }

   public static boolean showMount(@Nullable Player player) {
      if (player == null) {
         return false;
      }

      String state = AnimationStateSync.stateOf(player);
      return "decoding_mode".equals(state) || ShenheResources.FLIGHT_ANIMATIONS.contains(state);
   }

   public static boolean showFjo(@Nullable Player player) {
      return player != null && "decoding_mode".equals(AnimationStateSync.stateOf(player));
   }

   public static boolean showScreen(@Nullable Player player) {
      return player != null && ShenheResources.SCREEN_ANIMATIONS.contains(AnimationStateSync.stateOf(player));
   }

   public static boolean showTea(@Nullable Player player) {
      return player != null && ShenheResources.TEA_ANIMATIONS.contains(AnimationStateSync.stateOf(player));
   }

   public static BoneUpdater<BoneRenderState> updaterFor(@Nullable Player player) {
      boolean mount = showMount(player);
      boolean fjo = showFjo(player);
      boolean screen = showScreen(player);
      boolean tea = showTea(player);
      logVisibility(player, mount, fjo, screen, tea);
      if (mount && fjo && screen && tea) {
         return makeUpdater(List.of());
      }

      List<String> hidden = new ArrayList<>(5);
      if (!mount) {
         hidden.add("MFly");
      }

      if (!fjo) {
         hidden.add("FJO");
      }

      if (!screen) {
         hidden.addAll(SCREEN_BONES);
      }

      if (!tea) {
         hidden.add("tea");
      }

      return makeUpdater(hidden);
   }

   private static void logVisibility(@Nullable Player player, boolean mount, boolean fjo, boolean screen, boolean tea) {
      if (player != null && player == Minecraft.getInstance().player && LOGGER.isInfoEnabled()) {
         int bits = (mount ? 1 : 0) | (fjo ? 2 : 0) | (screen ? 4 : 0) | (tea ? 8 : 0);
         Integer previous = LOG_LAST_VISIBLE.put(player, bits);
      }
   }

   public static BoneUpdater<BoneRenderState> hideAllUpdater() {
      return makeUpdater(PROP_BONES);
   }

   private static BoneUpdater<BoneRenderState> makeUpdater(List<String> hidden) {
      return (renderPassInfo, snapshots) -> {
         for (String bone : hidden) {
            snapshots.ifPresent(bone, snapshot -> {
               snapshot.skipRender(true);
               snapshot.skipChildrenRender(true);
               snapshot.setScale(0.0F, 0.0F, 0.0F);
            });
         }

         for (String bone : SCREEN_BONES) {
            snapshots.ifPresent(bone, snapshot -> snapshot.setTranslateZ(snapshot.getTranslateZ() + -8.0F));
         }
      };
   }
}
