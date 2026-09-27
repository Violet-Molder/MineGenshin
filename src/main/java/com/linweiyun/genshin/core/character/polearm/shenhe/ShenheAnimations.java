// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character.polearm.shenhe;

import com.linweiyun.genshin.core.system.combat.animation.config.CharacterAnimations;
import com.linweiyun.genshin.core.system.combat.animation.config.LocomotionAnims;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

public final class ShenheAnimations implements CharacterAnimations {
   public static final ShenheAnimations INSTANCE = new ShenheAnimations();
   public static final LocomotionAnims LOCOMOTION = LocomotionAnims.of(
         "idle", "walk", "run", "walk_back", "sneak", "sneaking", "sleep", "climb", "swim_stand", "swim_stand", "swim_stand", "swim", "jump", "jump_down"
      )
      .withTransitions("landing_heavy", 12, "run_stop", 25)
      .withLightLanding("landing_light", 3)
      .withFlight("fly", "fly_up", "fly_down");
   public static final Set<String> SPECIAL_ANIMS = Set.of(
      "sword_idle_attack_01",
      "sword_idle_attack_02",
      "sword_idle_attack_03",
      "sword_idle_attack_end",
      "landing_heavy",
      "landing_light",
      "sword_jump_attack",
      "skill",
      "skill_hold",
      "burst",
      "dodge_front",
      "dodge_back",
      "dodge_left",
      "dodge_right",
      "decoding_mode"
   );
   public static final int EXIT_TRANSITION_TICKS = 5;
   public static final Map<String, String> STATE_SOUNDS = Map.ofEntries();

   private ShenheAnimations() {
   }

   @Override
   public LocomotionAnims locomotion() {
      return LOCOMOTION;
   }

   @Override
   public Set<String> specialAnims() {
      return SPECIAL_ANIMS;
   }

   @Override
   public int exitTransitionTicks() {
      return 5;
   }

   @Nullable
   @Override
   public String soundForState(String stateName) {
      return STATE_SOUNDS.get(stateName);
   }
}
