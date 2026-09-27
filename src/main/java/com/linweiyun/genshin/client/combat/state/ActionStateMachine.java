// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.client.combat.state;

import com.linweiyun.genshin.client.combat.AttackApproach;
import com.linweiyun.genshin.client.combat.BurstDive;
import com.linweiyun.genshin.client.combat.PlungeAttack;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import com.linweiyun.genshin.core.network.ActionServer;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.linweiyun.genshin.core.system.combat.action.InterruptReason;
import com.linweiyun.genshin.core.system.combat.animation.action.CharacterActionHandler;
import com.linweiyun.genshin.core.system.combat.animation.action.CharacterActions;
import com.linweiyun.genshin.core.system.combat.animation.config.CharacterAnimations;
import com.linweiyun.genshin.core.system.combat.targeting.CombatTargeting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.Clone;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@EventBusSubscriber(modid = "minegenshin", value = Dist.CLIENT)
public final class ActionStateMachine {
   private static final Logger LOGGER = ModLog.getLogger(LogGroup.COMBAT);
   public static final String DEFAULT_STATE = "default";
   public static final int PRIO_NORMAL = 1;
   public static final int PRIO_ATTACK = 2;
   public static final int PRIO_DODGE = 3;
   public static final int PRIO_FINAL = 4;
   public static String currentState = "default";
   public static int currentPriority = 1;
   private static boolean currentStateLoops = false;
   public static int animationTick = 0;
   public static int actionLockFrames = 0;
   public static int lockDelayFrames = 0;
   public static int movementLockFrames = 0;
   public static int comboStage = 1;
   public static int comboWindowFrames = 0;
   public static int attackHoldTimer = 0;
   public static boolean isAttackButtonDown = false;
   public static boolean isSkillButtonDown = false;
   public static int skillHoldTimer = 0;
   private static boolean skillHoldTriggered = false;
   public static boolean chargedAttackTriggered = false;
   private static boolean deferredNormalAttack = false;
   public static final int SKILL_HOLD_TICKS = 20;
   @Nullable
   private static String followUpState = null;
   private static int followUpTicks = 0;
   private static boolean approachFrozen = false;
   private static double currentAttackRange = 3.0;
   private static int actionSequence = 0;
   public static final int FOLLOW_UP_LOCK_TICKS = 12;

   public static int actionSequence() {
      return actionSequence;
   }

   public static boolean currentStateLoops() {
      return currentStateLoops;
   }

   private ActionStateMachine() {
   }

   public static void queueClientWork(int delayTicks, Runnable action) {
      ClientTaskQueue.enqueue(delayTicks, action);
   }

   @SubscribeEvent
   public static void onClientTick(Post event) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         tickPendingWork(player);
         tickEngagement(player);
         tickHeldButtons(player);
         tickActionCountdowns();
         if (!interruptOnNormalInput(player)) {
            tickPassiveAndRemote(player);
            tickAnimationTimeline();
         }
      }
   }

   private static void tickPendingWork(LocalPlayer player) {
      ActionInputFreeze.restore(player);
      ClientTaskQueue.tick();
   }

   private static void tickEngagement(LocalPlayer player) {
      CombatTargeting.tick(player);
      AttackApproach.tick(player);
      BurstDive.tick(player);
      PlungeAttack.tick(player);
   }

   private static void tickHeldButtons(LocalPlayer player) {
      if (isAttackButtonDown) {
         attackHoldTimer++;
         if (currentPriority < 4 && !chargedAttackTriggered) {
            CharacterActionHandler handler = handlerFor(player);
            if (handler != null) {
               handler.tickCharge(player, attackHoldTimer);
            }
         }
      } else {
         attackHoldTimer = 0;
      }

      if (isSkillButtonDown && !skillHoldTriggered) {
         skillHoldTimer++;
         if (skillHoldTimer >= 20 && hasSkillHoldVariant(player)) {
            skillHoldTriggered = trySkill(player, true);
         }
      }
   }

   private static void tickActionCountdowns() {
      if (!approachFrozen) {
         if (lockDelayFrames > 0) {
            lockDelayFrames--;
         } else {
            if (actionLockFrames > 0) {
               actionLockFrames--;
            }

            if (movementLockFrames > 0) {
               movementLockFrames--;
            }
         }
      }

      if (!actionLocked() && comboWindowFrames > 0) {
         comboWindowFrames--;
         if (comboWindowFrames <= 0) {
            comboStage = 1;
         }
      }
   }

   private static boolean interruptOnNormalInput(LocalPlayer player) {
      if (currentStateLoops()) {
         return false;
      }

      if (!movementFrozen() && !actionLocked() && animationTick > 0 && !"default".equals(currentState)) {
         ClientInput input = player.input;
         boolean isMoving = input.getMoveVector().lengthSquared() > 1.0E-5F;
         boolean isJumping = input.keyPresses.jump();
         boolean isCrouching = input.keyPresses.shift();
         if (isMoving || isJumping || isCrouching) {
            resetToDefault();
            ActionServer.interruptActionToServer(InterruptReason.JUMP.ordinal());
            return true;
         }
      }

      return false;
   }

   private static void tickPassiveAndRemote(LocalPlayer player) {
      CharacterActionHandler handler = handlerFor(player);
      if (handler != null) {
         handler.passiveTick(player);
      }

      AnimationStateSync.tickRemoteStateSounds();
   }

   private static void tickAnimationTimeline() {
      if (!approachFrozen) {
         if (animationTick > 0) {
            animationTick--;
         } else if (!"default".equals(currentState)) {
            if (followUpState != null) {
               startFollowUp();
            } else {
               resetToDefault();
            }
         }
      }
   }

   private static void startFollowUp() {
      String next = followUpState;
      int ticks = followUpTicks;
      int priority = currentPriority;
      followUpState = null;
      followUpTicks = 0;
      int lock = Math.min(ticks, 12);
      LOGGER.info("[MineGenshin][收尾] 接上 '{}'（{} 刻，前 {} 刻不可被移动打断）", new Object[]{next, ticks, lock});
      changeState(next, priority, ticks, lock, 0);
   }

   @SubscribeEvent
   public static void onMovementInputUpdate(MovementInputUpdateEvent event) {
      if (movementFrozen()) {
         if (event.getEntity() instanceof LocalPlayer player) {
            event.getInput().keyPresses = Input.EMPTY;
            ActionInputFreeze.install(player);
         }
      }
   }

   @SubscribeEvent
   public static void onClientPlayerRespawn(Clone event) {
      // 复活 / 重登时把下落攻击状态一起收掉，否则新身体永远「不许动」
      PlungeAttack.cancel();
      resetToDefault();
   }

   public static boolean actionLocked() {
      return lockDelayFrames <= 0 && actionLockFrames > 0;
   }

   public static boolean movementFrozen() {
      return lockDelayFrames <= 0 && movementLockFrames > 0;
   }

   public static boolean canInterrupt(int requestedPriority) {
      return currentPriority >= 4 ? false : !actionLocked();
   }

   public static void pressAttack(Player player) {
      isAttackButtonDown = true;
      attackHoldTimer = 0;
      chargedAttackTriggered = false;
      // 坠落中按下普攻 = 下落攻击：不走普攻，也不走大剑那套「按住先蓄力」
      if (PlungeAttack.tryBegin(player)) {
         deferredNormalAttack = false;
         return;
      }
      deferredNormalAttack = handlerFor(player).deferNormalAttackOnPress(player);
      if (!deferredNormalAttack) {
         tryAttack(player);
      }
   }

   public static void tryAttack(Player player) {
      if (currentPriority < 4) {
         if (canInterrupt(2)) {
            handlerFor(player).attack(player);
         }
      }
   }

   public static boolean trySkill(Player player, boolean longPress) {
      return !canInterrupt(2) ? false : handlerFor(player).skill(player, longPress);
   }

   public static void tryDodge(Player player) {
      if (canInterrupt(3)) {
         CharacterActionHandler handler = handlerFor(player);
         if (handler != null) {
            handler.dodge(player);
         }
      }
   }

   public static void tryUltimate(Player player) {
      if (canInterrupt(4)) {
         CharacterActionHandler handler = handlerFor(player);
         if (handler != null) {
            handler.ultimate(player);
         }
      }
   }

   public static void releaseAttack(Player player) {
      boolean deferred = deferredNormalAttack && isAttackButtonDown;
      isAttackButtonDown = false;
      int chargeTime = attackHoldTimer;
      attackHoldTimer = 0;
      boolean chargedTriggered = chargedAttackTriggered;
      chargedAttackTriggered = false;
      deferredNormalAttack = false;
      CharacterActionHandler handler = handlerFor(player);
      if (handler != null) {
         handler.releaseAttack(player, chargeTime);
      }

      if (deferred && !chargedTriggered) {
         tryAttack(player);
      }
   }

   public static void pressSkill(Player player) {
      isSkillButtonDown = true;
      skillHoldTimer = 0;
      skillHoldTriggered = false;
      if (!hasSkillHoldVariant(player)) {
         trySkill(player, false);
      }
   }

   public static void releaseSkill(Player player) {
      boolean wasHoldVariant = hasSkillHoldVariant(player);
      isSkillButtonDown = false;
      if (wasHoldVariant && !skillHoldTriggered) {
         trySkill(player, false);
      }

      skillHoldTimer = 0;
      skillHoldTriggered = false;
   }

   private static boolean hasSkillHoldVariant(Player player) {
      PGCharacter character = currentCharacter(player);
      return character == null ? false : character.getSkillShortMaxCooldownTick() != character.getSkillLongMaxCooldownTick();
   }

   @Nullable
   private static PGCharacter currentCharacter(Player player) {
      PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
      return attachment == null ? null : attachment.getCurrentCharacter();
   }

   public static void queueFollowUpState(String stateName, int totalTicks) {
      if (stateName != null && !stateName.isEmpty() && totalTicks > 0) {
         followUpState = stateName;
         followUpTicks = totalTicks;
         LOGGER.info("[MineGenshin][收尾] 排队 '{}'（{} 刻）", stateName, totalTicks);
      } else {
         followUpState = null;
         followUpTicks = 0;
      }
   }

   public static void clearFollowUpState() {
      followUpState = null;
      followUpTicks = 0;
   }

   public static void setApproachFrozen(boolean frozen) {
      approachFrozen = frozen;
   }

   public static boolean isApproachFrozen() {
      return approachFrozen;
   }

   public static void setCurrentAttackRange(double range) {
      currentAttackRange = range > 0.0 ? range : 3.0;
   }

   public static double currentAttackRange() {
      return currentAttackRange;
   }

   public static void resumeFromApproach(int totalTicks) {
      resumeFromApproach(totalTicks, 0, 0);
   }

   public static void resumeFromApproach(int totalTicks, int lockFrames, int movementLock) {
      approachFrozen = false;
      animationTick = Math.max(1, totalTicks);
      actionLockFrames = Math.max(0, lockFrames);
      movementLockFrames = Math.max(0, movementLock);
      lockDelayFrames = 0;
   }

   public static void changeState(String newState, int priority, int totalTicks, int lockFrames) {
      changeState(newState, priority, totalTicks, lockFrames, -1, 0);
   }

   public static void changeState(String newState, int priority, int totalTicks, int lockFrames, int movementLockTicks) {
      changeState(newState, priority, totalTicks, lockFrames, movementLockTicks, 0);
   }

   public static void changeState(String newState, int priority, int totalTicks, int lockFrames, int movementLockTicks, int lockDelayTicks) {
      changeState(newState, priority, totalTicks, lockFrames, movementLockTicks, lockDelayTicks, false);
   }

   public static void changeState(
      String newState, int priority, int totalTicks, int lockFrames, int movementLockTicks, int lockDelayTicks, boolean loopAnimation
   ) {
      LocalPlayer player = Minecraft.getInstance().player;
      AttackApproach.cancel();
      BurstDive.cancel();
      dispatchCleanup(currentState, player);
      if (followUpState != null) {
         LOGGER.info("[MineGenshin][收尾] 排队中的 '{}' 被 '{}' 顶掉", followUpState, newState);
      }

      clearFollowUpState();
      currentState = newState;
      currentPriority = priority;
      animationTick = totalTicks;
      currentStateLoops = loopAnimation;
      actionSequence++;
      actionLockFrames = lockFrames;
      lockDelayFrames = Math.max(0, lockDelayTicks);
      if (movementLockTicks >= 0) {
         movementLockFrames = movementLockTicks;
      }

      if (priority >= 2) {
         comboWindowFrames = 30;
      }

      if (player != null) {
         playLocalSound(player, soundForState(player, newState));
         sendStateToServer(newState, totalTicks);
      }
   }

   public static void resetToDefault() {
      LocalPlayer player = Minecraft.getInstance().player;
      AttackApproach.cancel();
      BurstDive.cancel();
      dispatchCleanup(currentState, player);
      currentState = "default";
      currentPriority = 1;
      animationTick = 0;
      currentStateLoops = false;
      actionLockFrames = 0;
      lockDelayFrames = 0;
      movementLockFrames = 0;
      clearFollowUpState();
      ClientTaskQueue.clear();
      if (player != null) {
         sendStateToServer("default", 0);
      }
   }

   private static void sendStateToServer(String stateName, int totalTicks) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null && Minecraft.getInstance().getConnection() != null) {
         NetworkManager.sendAnimationStateToServer(stateName, totalTicks);
      }
   }

   private static void dispatchCleanup(String stateToClean, @Nullable Player player) {
      if (player != null && !"default".equals(stateToClean)) {
         CharacterActionHandler handler = handlerFor(player);
         if (handler != null) {
            handler.onStateInterrupted(stateToClean, player);
         }
      }
   }

   public static void playLocalSound(@Nullable Player player, @Nullable String soundId) {
      playLocalSound(player, soundId, 1.0F, 1.0F);
   }

   public static void playLocalSound(@Nullable Player player, @Nullable String soundId, float volume, float pitch) {
      if (player != null && soundId != null && !soundId.isEmpty()) {
         Identifier id = Identifier.tryParse(soundId);
         if (id != null) {
            SoundEvent soundEvent = (SoundEvent)BuiltInRegistries.SOUND_EVENT.getValue(id);
            if (soundEvent == null) {
               soundEvent = SoundEvent.createVariableRangeEvent(id);
            }

            player.level()
               .playLocalSound(
                  player.getX(), player.getY(), player.getZ(), soundEvent, SoundSource.PLAYERS, Math.max(0.01F, volume), Math.max(0.01F, pitch), false
               );
         }
      }
   }

   @Nullable
   private static CharacterActionHandler handlerFor(Player player) {
      return CharacterActions.getFor(player);
   }

   @Nullable
   private static String soundForState(Player player, String stateName) {
      CharacterAnimations animations = CharacterActions.animationsFor(player);
      return animations == null ? null : animations.soundForState(stateName);
   }
}
