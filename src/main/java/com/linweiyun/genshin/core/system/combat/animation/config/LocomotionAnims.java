// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.system.combat.animation.config;

import software.bernie.geckolib.animation.RawAnimation;
import org.jetbrains.annotations.Nullable;

public record LocomotionAnims(
   RawAnimation idle,
   RawAnimation walk,
   RawAnimation run,
   RawAnimation sprint,
   RawAnimation walkBack,
   RawAnimation crouch,
   RawAnimation crouchWalk,
   RawAnimation sleep,
   RawAnimation climb,
   RawAnimation waterIdle,
   RawAnimation waterWalk,
   RawAnimation waterWalkBack,
   RawAnimation swim,
   RawAnimation jump,
   RawAnimation jumpDown,
   @Nullable LocomotionAnims.OneShot landing,
   @Nullable LocomotionAnims.OneShot landingLight,
   @Nullable LocomotionAnims.OneShot runStop,
   @Nullable RawAnimation fly,
   @Nullable RawAnimation flyUp,
   @Nullable RawAnimation flyDown
) {
   public static final LocomotionAnims DEFAULT = of(
         "idle", "walk", "run", "walk_back", "crouch", "crouch_walk", "sleep", "climb", "water", "water_walk", "water_walk_back", "swim", "jump", "jump_down"
      )
      .withTransitions("landing", 2, null, 0);

   public static LocomotionAnims of(
      String idle,
      String walk,
      String run,
      String walkBack,
      String crouch,
      String crouchWalk,
      String sleep,
      String climb,
      String waterIdle,
      String waterWalk,
      String waterWalkBack,
      String swim,
      String jump,
      String jumpDown
   ) {
      return new LocomotionAnims(
         loop(idle),
         loop(walk),
         loop(run),
         loop("sprint"),
         loop(walkBack),
         loop(crouch),
         loop(crouchWalk),
         loop(sleep),
         loop(climb),
         loop(waterIdle),
         loop(waterWalk),
         loop(waterWalkBack),
         loop(swim),
         loop(jump),
         loop(jumpDown),
         null,
         null,
         null,
         null,
         null,
         null
      );
   }

   public LocomotionAnims withSprint(@Nullable String sprint) {
      return new LocomotionAnims(
         this.idle,
         this.walk,
         this.run,
         sprint == null ? null : loop(sprint),
         this.walkBack,
         this.crouch,
         this.crouchWalk,
         this.sleep,
         this.climb,
         this.waterIdle,
         this.waterWalk,
         this.waterWalkBack,
         this.swim,
         this.jump,
         this.jumpDown,
         this.landing,
         this.landingLight,
         this.runStop,
         this.fly,
         this.flyUp,
         this.flyDown
      );
   }

   public LocomotionAnims withTransitions(@Nullable String landing, int landingTicks, @Nullable String runStop, int runStopTicks) {
      return new LocomotionAnims(
         this.idle,
         this.walk,
         this.run,
         this.sprint,
         this.walkBack,
         this.crouch,
         this.crouchWalk,
         this.sleep,
         this.climb,
         this.waterIdle,
         this.waterWalk,
         this.waterWalkBack,
         this.swim,
         this.jump,
         this.jumpDown,
         landing == null ? null : new LocomotionAnims.OneShot(playOnce(landing), landingTicks),
         this.landingLight,
         runStop == null ? null : new LocomotionAnims.OneShot(playOnce(runStop), runStopTicks),
         this.fly,
         this.flyUp,
         this.flyDown
      );
   }

   public LocomotionAnims withLightLanding(@Nullable String landingLight, int landingLightTicks) {
      return new LocomotionAnims(
         this.idle,
         this.walk,
         this.run,
         this.sprint,
         this.walkBack,
         this.crouch,
         this.crouchWalk,
         this.sleep,
         this.climb,
         this.waterIdle,
         this.waterWalk,
         this.waterWalkBack,
         this.swim,
         this.jump,
         this.jumpDown,
         this.landing,
         landingLight == null ? null : new LocomotionAnims.OneShot(playOnce(landingLight), landingLightTicks),
         this.runStop,
         this.fly,
         this.flyUp,
         this.flyDown
      );
   }

   public LocomotionAnims withFlight(@Nullable String fly, @Nullable String flyUp, @Nullable String flyDown) {
      return new LocomotionAnims(
         this.idle,
         this.walk,
         this.run,
         this.sprint,
         this.walkBack,
         this.crouch,
         this.crouchWalk,
         this.sleep,
         this.climb,
         this.waterIdle,
         this.waterWalk,
         this.waterWalkBack,
         this.swim,
         this.jump,
         this.jumpDown,
         this.landing,
         this.landingLight,
         this.runStop,
         fly == null ? null : loop(fly),
         flyUp == null ? null : loop(flyUp),
         flyDown == null ? null : loop(flyDown)
      );
   }

   private static RawAnimation loop(String animationName) {
      return RawAnimation.begin().thenLoop(animationName);
   }

   private static RawAnimation playOnce(String animationName) {
      return RawAnimation.begin().thenPlayAndHold(animationName);
   }

   public record OneShot(RawAnimation animation, int ticks) {
   }
}