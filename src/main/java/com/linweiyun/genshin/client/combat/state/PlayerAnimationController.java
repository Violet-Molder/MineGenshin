// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.client.combat.state;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.RawAnimation.Stage;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationPoint;
import com.geckolib.animation.state.AnimationTest;
import com.linweiyun.genshin.core.character.util.CharacterHelper;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.linweiyun.genshin.core.system.combat.animation.action.CharacterActions;
import com.linweiyun.genshin.core.system.combat.animation.animatable.IPlayerAnimatableProxy;
import com.linweiyun.genshin.core.system.combat.animation.config.CharacterAnimations;
import com.linweiyun.genshin.core.system.combat.animation.config.FirstPersonAnims;
import com.linweiyun.genshin.core.system.combat.animation.config.LocomotionAnims;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public final class PlayerAnimationController {
   private static final Logger LOGGER = ModLog.getLogger(LogGroup.ANIMATION);
   private static final double LANDING_MIN_FALL_SPEED = 0.125;
   private static final double LANDING_HEAVY_FALL_SPEED = 0.45;
   private static final double LANDING_LIGHT_FALL_SPEED = 0.06;
   private static final double FLIGHT_VERTICAL_SPEED = 0.02;
   private static final Map<Player, PlayerAnimationController.LocoTransient> LOCO_TRANSIENT = new WeakHashMap<>();
   private static final Map<Player, String> LAST_CHARACTER_ID = new WeakHashMap<>();
   private static final Map<Player, String> LOG_LAST_ANIMATION = new WeakHashMap<>();
   private static final Map<Player, String> LOG_LAST_STATE = new WeakHashMap<>();
   /** 本地玩家上一次广播出去的飞行片段名（变了才发包，不刷屏）。 */
   private static final Map<Player, String> LAST_SENT_FLIGHT_CLIP = new WeakHashMap<>();
   private static int lastEmptyFrameTick = Integer.MIN_VALUE;

   private PlayerAnimationController() {
   }

   public static <T extends GeoAnimatable & IPlayerAnimatableProxy> AnimationController<T> create(T animatable) {
      return new AnimationController("movement_controller", 5, state -> {
         Player player = animatable.getPlayerEntity();
         return handle(state, player);
      });
   }

   public static <T extends GeoAnimatable & IPlayerAnimatableProxy> AnimationController<T> createIdleOnly(T animatable) {
      return createPreview(animatable, null);
   }

   public static <T extends GeoAnimatable & IPlayerAnimatableProxy> AnimationController<T> createPreview(T animatable, @Nullable String animationName) {
      return new AnimationController("preview_idle", 5, state -> {
         Player player = animatable.getPlayerEntity();
         if (player == null) {
            return PlayState.STOP;
         }

         RawAnimation target = resolvePreviewAnimation(player, animationName);
         if (target == null) {
            return state.controller().getCurrentAnimationPoint() == null ? PlayState.STOP : PlayState.CONTINUE;
         }

         state.controller().setTransitionTicks(0);
         return state.setAndContinue(target);
      });
   }

   @Nullable
   private static RawAnimation resolvePreviewAnimation(Player player, @Nullable String animationName) {
      if (animationName != null && AnimationAvailability.existsFor(player, animationName)) {
         return RawAnimation.begin().thenLoop(animationName);
      }

      RawAnimation idle = CharacterActions.animationsFor(player).locomotionFor(player).idle();
      String idleName = targetName(idle);
      return idleName != null && AnimationAvailability.existsFor(player, idleName) ? idle : null;
   }

   private static <T extends GeoAnimatable> PlayState handle(AnimationTest<T> state, @Nullable Player player) {
      if (player == null) {
         return PlayState.STOP;
      }

      AnimationController<T> controller = state.controller();
      CharacterAnimations animations = CharacterActions.animationsFor(player);

      resetOnCharacterChange(player, controller);
      boolean isLocalPlayer = player == Minecraft.getInstance().player;
      String targetAnim = AnimationStateSync.stateOf(player);
      boolean hasActionState = targetAnim != null && !targetAnim.isEmpty() && !"default".equals(targetAnim);
      double movedX = player.getX() - player.xo;
      double movedY = player.getY() - player.yo;
      double movedZ = player.getZ() - player.zo;
      boolean isMoving;
      if (isLocalPlayer && player instanceof LocalPlayer localPlayer) {
         isMoving = ActionStateMachine.actionLockFrames <= 0 && localPlayer.input.getMoveVector().lengthSquared() > 1.0E-5F;
      } else {
         isMoving = movedX * movedX + movedZ * movedZ > 5.0E-5;
      }

      RawAnimation target = hasActionState
         ? pickAction(player, targetAnim, isLocalPlayer && ActionStateMachine.currentStateLoops())
         : pickLocomotion(player, animations.locomotionFor(player), isMoving, movedX, movedY, movedZ);
      logAnimationFlow(player, target, targetAnim, hasActionState);
      if (hasActionState) {
         suspendLocomotionTransient(player);
      }

      if (target == null) {
         return controller.getCurrentAnimationPoint() == null ? PlayState.STOP : PlayState.CONTINUE;
      }

      if (isLocalPlayer && ActionStateMachine.isApproachFrozen() && targetName(target).equals(currentAnimationName(controller))) {
         return PlayState.PAUSE;
      }

      boolean isTargetSpecial = animations.specialAnims().contains(targetName(target));
      String currentPlayingAnim = currentAnimationName(controller);
      boolean isCurrentlySpecial = animations.specialAnims().contains(currentPlayingAnim);
      if (previousWasOneShot(animations, player, currentPlayingAnim)) {
         controller.reset();
      }

      boolean nothingPlaying = controller.getCurrentAnimationPoint() == null || controller.getCurrentTimelineTime() < 0.0;
      if (!isTargetSpecial && !nothingPlaying && !previousWasOneShot(animations, player, currentPlayingAnim) && (!isCurrentlySpecial || hasActionState)) {
         controller.setTransitionTicks(animations.exitTransitionTicks());
      } else {
         controller.setTransitionTicks(0);
      }

      return state.setAndContinue(target);
   }

   private static boolean previousWasOneShot(CharacterAnimations animations, Player player, @Nullable String previousName) {
      if (previousName == null) {
         return false;
      }

      LocomotionAnims loco = animations.locomotionFor(player);

      for (LocomotionAnims.OneShot oneShot : new LocomotionAnims.OneShot[]{loco.landing(), loco.landingLight(), loco.runStop()}) {
         if (oneShot != null && previousName.equals(targetName(oneShot.animation()))) {
            return true;
         }
      }

      return false;
   }

   private static void logAnimationFlow(Player player, @Nullable RawAnimation target, String state, boolean hasActionState) {
      if (player == Minecraft.getInstance().player && LOGGER.isInfoEnabled()) {
         String stateName = state != null && !state.isEmpty() ? state : "default";
         String previousState = LOG_LAST_STATE.put(player, stateName);
         if (!Objects.equals(previousState, stateName)) {
            LOGGER.info("状态 {} → {}（刻={}）", new Object[]{previousState, stateName, player.tickCount});
         }

         String name = target == null ? null : targetName(target);
         String previous = LOG_LAST_ANIMATION.get(player);
         if (!Objects.equals(previous, name)) {
            if (name == null) {
               LOG_LAST_ANIMATION.remove(player);
            } else {
               LOG_LAST_ANIMATION.put(player, name);
            }
         }
      }
   }

   private static boolean resetOnCharacterChange(Player player, AnimationController<?> controller) {
      String characterId = CharacterHelper.getActiveCharacterId(player);
      String previous = LAST_CHARACTER_ID.get(player);
      if (Objects.equals(previous, characterId)) {
         return false;
      }

      LAST_CHARACTER_ID.put(player, characterId);
      if (previous == null) {
         return false;
      }

      controller.reset();
      LOCO_TRANSIENT.remove(player);
      return true;
   }

   @Nullable
   private static RawAnimation pickAction(Player player, String animationName, boolean loop) {
      FirstPersonAnims firstPerson = CharacterActions.animationsFor(player).firstPerson();
      if (firstPerson.enabled() && isFirstPerson(player)) {
         String fpName = firstPerson.resolve(animationName, true);
         if (fpName != null && AnimationAvailability.existsFor(player, fpName)) {
            return loop ? RawAnimation.begin().thenLoop(fpName) : RawAnimation.begin().thenPlayAndHold(fpName);
         }
      }

      if (!AnimationAvailability.existsFor(player, animationName)) {
         return null;
      } else {
         return loop ? RawAnimation.begin().thenLoop(animationName) : RawAnimation.begin().thenPlayAndHold(animationName);
      }
   }

   public static boolean isFirstPerson(Player player) {
      Minecraft minecraft = Minecraft.getInstance();
      return player == minecraft.player && minecraft.options != null && minecraft.options.getCameraType().isFirstPerson();
   }

   public static FirstPersonAnims firstPersonFor(Player player) {
      return !isFirstPerson(player) ? FirstPersonAnims.DISABLED : CharacterActions.animationsFor(player).firstPerson();
   }

   @Nullable
   private static RawAnimation pickLocomotion(Player player, LocomotionAnims loco, boolean isMoving, double movedX, double movedY, double movedZ) {
      RawAnimation picked = pickLocomotionRaw(player, loco, isMoving, movedX, movedY, movedZ);
      return existingOrIdle(player, loco, picked);
   }

   private static double bodyForwardDot(Player player, double movedX, double movedZ) {
      float bodyYawRad = player.yBodyRot * (float) (Math.PI / 180.0);
      double forwardX = -Mth.sin(bodyYawRad);
      double forwardZ = Mth.cos(bodyYawRad);
      return movedX * forwardX + movedZ * forwardZ;
   }

   private static RawAnimation pickLocomotionRaw(Player player, LocomotionAnims loco, boolean isMoving, double movedX, double movedY, double movedZ) {
      if (player.isSleeping()) {
         return loco.sleep();
      }

      if (player.onClimbable()) {
         return loco.climb();
      }

      if (player.isInWater()) {
         if (player.isSwimming()) {
            return loco.swim();
         } else if (isMoving) {
            return bodyForwardDot(player, movedX, movedZ) < -0.01 ? loco.waterWalkBack() : loco.waterWalk();
         } else {
            return loco.waterIdle();
         }
      } else {
         PlayerAnimationController.LocoTransient transientState = LOCO_TRANSIENT.computeIfAbsent(player, key -> new PlayerAnimationController.LocoTransient());
         if (isFlying(player) && loco.fly() != null) {
            transientState.airborne = false;
            transientState.oneShot = null;
            transientState.wasRunning = false;
            transientState.fallSpeed = 0.0;
            // 别的玩家：用他**广播过来的**飞行片段，不再自己按速度猜 ——
            // 他松手的那一刻，我们这边看到的速度还在衰减，猜出来会晚一拍（用户报的延迟）。
            RawAnimation broadcast = broadcastFlightClip(player, loco);
            if (broadcast != null) {
               return broadcast;
            }
            // 本地玩家：**直接看按键**。飞行时松开空格后人还会带着惯性继续往上飘，
            // 拿竖直速度判断的话要等速度掉到阈值以下才切回水平 —— 用户报的「停下来
            // 还要等近一秒才恢复」就是这个。按键是瞬时状态，松手立刻切。
            // 别的玩家看不到输入，只能按位移判断（阈值 0.008，比原来的 0.02 灵敏些）。
            boolean ascending;
            boolean descending;
            if (player instanceof LocalPlayer localPlayer) {
               ascending = localPlayer.input.keyPresses.jump();
               descending = localPlayer.input.keyPresses.shift();
            } else {
               ascending = movedY > 0.008;
               descending = movedY < -0.008;
            }

            if (ascending) {
               return announceFlightClip(player, loco.flyUp() != null ? loco.flyUp() : loco.fly());
            } else if (descending) {
               return announceFlightClip(player, loco.flyDown() != null ? loco.flyDown() : loco.fly());
            } else {
               // 水平这一档还能细分（例如林薇云长柄：悬停 / 往前飞 / 疾跑冲刺）
               return announceFlightClip(player, CharacterActions.animationsFor(player).flyVariant(player, loco, isMoving));
            }
         } else {
            if (!player.onGround()) {
               transientState.airborne = true;
               transientState.fallSpeed = movedY;
               transientState.oneShot = null;
               transientState.wasRunning = false;
               return movedY > 0.01 ? loco.jump() : loco.jumpDown();
            }

            if (transientState.airborne) {
               transientState.airborne = false;
               LocomotionAnims.OneShot landing = pickLanding(loco, transientState.fallSpeed);
               if (landing != null) {
                  beginOneShot(transientState, landing, player.tickCount);
               }
            }

            if (holdingOneShot(transientState, loco, isMoving, player)) {
               return transientState.oneShot.animation();
            }

            if (isMoving) {
               double dotProduct = bodyForwardDot(player, movedX, movedZ);
               if (player.isCrouching()) {
                  transientState.wasRunning = false;
                  return loco.crouchWalk();
               } else if (player.isSprinting()) {
                  transientState.wasRunning = true;
                  return loco.run();
               } else {
                  transientState.wasRunning = false;
                  return dotProduct < -0.01 ? loco.walkBack() : loco.walk();
               }
            } else {
               if (player.isCrouching()) {
                  transientState.wasRunning = false;
                  return loco.crouch();
               }

               if (transientState.wasRunning) {
                  transientState.wasRunning = false;
                  if (loco.runStop() != null) {
                     beginOneShot(transientState, loco.runStop(), player.tickCount);
                     return transientState.oneShot.animation();
                  }
               }

               return loco.idle();
            }
         }
      }
   }

   @Nullable
   private static LocomotionAnims.OneShot pickLanding(LocomotionAnims loco, double fallSpeed) {
      if (loco.landingLight() != null) {
         if (loco.landing() != null && fallSpeed <= -0.45) {
            return loco.landing();
         } else {
            return fallSpeed <= -0.06 ? loco.landingLight() : null;
         }
      } else {
         return loco.landing() != null && fallSpeed <= -0.125 ? loco.landing() : null;
      }
   }

   private static boolean isFlying(Player player) {
      return player.isFallFlying() ? true : player instanceof LocalPlayer && player.getAbilities().flying;
   }

   /**
    * 本地玩家选了哪条飞行片段，就顺着现成的动画状态包广播出去（变了才发）。
    *
    * <p>这一条是用户 2026-09-27 点的：「不是有广播动画状态的包吗」—— 飞行升降本来是
    * 各客户端自己按速度猜的，猜不准也猜得晚；现在改成**谁在飞谁说了算**，
    * 别人照收发的片段播。
    */
   private static RawAnimation announceFlightClip(Player player, @Nullable RawAnimation picked) {
      if (player instanceof LocalPlayer && picked != null) {
         String name = targetName(picked);
         if (name != null && !name.equals(LAST_SENT_FLIGHT_CLIP.get(player))) {
            LAST_SENT_FLIGHT_CLIP.put(player, name);
            NetworkManager.sendAnimationStateToServer(name, 0);
         }
      }
      return picked;
   }

   /** 远端玩家广播来的飞行片段（正好是这一档的 fly / flyUp / flyDown 就用它，循环播）。 */
   @Nullable
   private static RawAnimation broadcastFlightClip(Player player, LocomotionAnims loco) {
      if (player instanceof LocalPlayer) {
         return null;
      }
      String state = AnimationStateSync.stateOf(player);
      if (state == null || state.isEmpty()) {
         return null;
      }
      for (RawAnimation candidate : new RawAnimation[]{loco.fly(), loco.flyUp(), loco.flyDown()}) {
         if (candidate != null && state.equals(targetName(candidate))) {
            return RawAnimation.begin().thenLoop(state);
         }
      }
      return null;
   }

   private static boolean holdingOneShot(PlayerAnimationController.LocoTransient state, LocomotionAnims loco, boolean isMoving, Player player) {
      if (state.oneShot == null) {
         return false;
      }

      boolean landing = state.oneShot == loco.landing() || state.oneShot == loco.landingLight();
      if (isMoving && !landing) {
         state.oneShot = null;
         return false;
      }

      if (player.tickCount - state.oneShotStartTick < state.oneShot.ticks()) {
         return true;
      }

      state.oneShot = null;
      return false;
   }

   private static void beginOneShot(PlayerAnimationController.LocoTransient state, LocomotionAnims.OneShot oneShot, int nowTick) {
      state.oneShot = oneShot;
      state.oneShotStartTick = nowTick;
   }

   private static void suspendLocomotionTransient(Player player) {
      PlayerAnimationController.LocoTransient state = LOCO_TRANSIENT.get(player);
      if (state != null) {
         state.wasRunning = false;
         state.oneShot = null;
      }
   }

   @Nullable
   private static RawAnimation existingOrIdle(Player player, LocomotionAnims loco, RawAnimation wanted) {
      String name = targetName(wanted);
      if (name != null && AnimationAvailability.existsFor(player, name)) {
         return wanted;
      }

      String idleName = targetName(loco.idle());
      return idleName != null && AnimationAvailability.existsFor(player, idleName) ? loco.idle() : null;
   }

   @Nullable
   private static String targetName(@Nullable RawAnimation raw) {
      return raw != null && !raw.getAnimationStages().isEmpty() ? ((Stage)raw.getAnimationStages().getFirst()).animationName() : null;
   }

   private static String currentAnimationName(AnimationController<?> controller) {
      AnimationPoint point = controller.getCurrentAnimationPoint();
      return point != null && point.animation() != null ? point.animation().name() : "";
   }

   private static final class LocoTransient {
      boolean airborne;
      double fallSpeed;
      boolean wasRunning;
      @Nullable
      LocomotionAnims.OneShot oneShot;
      int oneShotStartTick;
   }
}
