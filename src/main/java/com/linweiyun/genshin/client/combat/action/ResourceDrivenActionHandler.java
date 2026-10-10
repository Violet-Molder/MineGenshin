// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.client.combat.action;

import com.linweiyun.genshin.client.camera.ThirdPersonCamera;
import com.linweiyun.genshin.client.combat.AttackApproach;
import com.linweiyun.genshin.client.combat.BurstDive;
import com.linweiyun.genshin.client.combat.PlungeAttack;
import com.linweiyun.genshin.client.combat.state.ActionStateMachine;
import com.linweiyun.genshin.client.combat.state.AnimationAvailability;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.util.CharacterHelper;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import com.linweiyun.genshin.core.network.ActionServer;
import com.linweiyun.genshin.core.system.combat.action.ActionContext;
import com.linweiyun.genshin.core.system.combat.action.ActionDefinition;
import com.linweiyun.genshin.core.system.combat.action.ActionKind;
import com.linweiyun.genshin.core.system.combat.action.ActionSet;
import com.linweiyun.genshin.core.system.combat.action.InterruptReason;
import com.linweiyun.genshin.core.system.combat.action.data.ActionBodyFacing;
import com.linweiyun.genshin.core.system.combat.action.data.ActionStep;
import com.linweiyun.genshin.core.system.combat.action.data.Engagement;
import com.linweiyun.genshin.core.system.combat.animation.action.CharacterActionHandler;
import com.linweiyun.genshin.core.system.combat.animation.action.CharacterActions;
import com.linweiyun.genshin.core.system.combat.targeting.CombatTargeting;
import com.linweiyun.genshin.core.system.combat.targeting.TargetPolicy;
import com.linweiyun.genshin.core.system.poise.WeaponPoiseTable;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public final class ResourceDrivenActionHandler implements CharacterActionHandler {
   private static final Logger LOGGER = ModLog.getLogger(LogGroup.COMBAT);
   public static final ResourceDrivenActionHandler INSTANCE = new ResourceDrivenActionHandler();
   private static final int MAX_LOCK_FRAMES = 8;
   private static final int DEFAULT_MOVE_LOCK = 5;
   private static final List<String> DODGE_DIRECTIONS = List.of("front", "back", "left", "right");

   private ResourceDrivenActionHandler() {
   }

   @Override
   public void attack(Player player) {
      PGCharacter character = currentCharacter(player);
      if (character != null) {
         ActionSet set = character.getActionSet(player);
         if (set != null && set.getNormalComboSize() != 0) {
            int stage = ActionStateMachine.comboStage;
            if (stage < 1 || stage > set.getNormalComboSize()) {
               stage = 1;
            }

            ActionDefinition def = set.getNormalAttack(stage);
            if (playable(player, def)) {
               if (ActionCastGuard.canCast(player, character, ActionKind.NORMAL_ATTACK, 0)) {
                  boolean isLastStage = stage >= set.getNormalComboSize();
                  ActionStateMachine.comboStage = isLastStage ? 1 : stage + 1;
                  int stageIndex = stage;
                  engageAndPlay(player, def, 2, null, target -> {
                     if (isLastStage && AnimationAvailability.existsFor(player, def.step.comboEndAnim)) {
                        ActionStateMachine.queueFollowUpState(def.step.comboEndAnim, def.step.comboEndTicks);
                     }

                     ActionServer.performNormalAttackToServer(stageIndex, target);
                  });
               }
            }
         }
      }
   }

   @Override
   public boolean deferNormalAttackOnPress(Player player) {
      PGCharacter character = currentCharacter(player);
      if (character == null) {
         return false;
      }
      if (character.isSustainedChargedAttack()) {
         return true;
      }
      // 单手剑 / 长柄武器的重击是「按住接重击」，它开头那一下普攻只能是第 1 段：
      // 连击已经走到第 2 段以后时，按下先不打 —— 松手才按当前段数接着连，按住就直接进重击。
      return ActionStateMachine.comboStage > 1 && holdChargeWeapon(character);
   }

   /** 单手剑 / 长柄武器。 */
   private static boolean holdChargeWeapon(PGCharacter character) {
      WeaponPoiseTable.WeaponClass weapon = WeaponPoiseTable.weaponOf(character);
      return weapon == WeaponPoiseTable.WeaponClass.SWORD
              || weapon == WeaponPoiseTable.WeaponClass.POLEARM;
   }

   @Override
   public boolean skill(Player player, boolean longPress) {
      PGCharacter character = currentCharacter(player);
      if (character == null) {
         return false;
      }

      ActionSet set = character.getActionSet(player);
      if (set == null) {
         return false;
      }

      ActionDefinition def = longPress ? set.getElementalSkillHold() : set.getElementalSkillTap();
      if (!playable(player, def)) {
         return false;
      }

      int holdFlag = longPress ? 1000 : 0;
      ActionKind kind = longPress ? ActionKind.ELEMENTAL_SKILL_HOLD : ActionKind.ELEMENTAL_SKILL_TAP;
      if (!ActionCastGuard.canCast(player, character, def.kind, holdFlag)) {
         return false;
      }

      engageAndPlay(player, def, 2, null, target -> ActionServer.triggerCharacterSkill(holdFlag, target));
      queueFollowUp(player, def);
      return true;
   }

   @Override
   public void ultimate(Player player) {
      PGCharacter character = currentCharacter(player);
      if (character != null) {
         ActionSet set = character.getActionSet(player);
         if (set != null) {
            ActionDefinition def = set.getElementalBurst();
            if (playable(player, def)) {
               if (ActionCastGuard.canCast(player, character, ActionKind.ELEMENTAL_BURST, 0)) {
                  engageAndPlay(player, def, 4, null, ActionServer::triggerCharacterBurst);
                  queueFollowUp(player, def);
                  if (def.step.diveBurst != null && player instanceof LocalPlayer localPlayer) {
                     BurstDive.begin(localPlayer, CombatTargeting.current(player), def.step);
                  }
               }
            }
         }
      }
   }

   @Override
   public void dodge(Player player) {
      PGCharacter character = currentCharacter(player);
      if (character != null) {
         ActionSet set = character.getActionSet(player);
         ActionDefinition def = set == null ? null : set.getDodge();
         if (playable(player, def)) {
            if (ActionCastGuard.canCast(player, character, ActionKind.DODGE, 0)) {
               play(player, def, 3, resolveDodgeAnim(player, def));
               ActionServer.triggerCharacterDodge();
            }
         }
      }
   }

   @Override
   public void tickCharge(Player player, int holdTicks) {
      if (PlungeAttack.isActive()) {
         return;
      }
      PGCharacter character = currentCharacter(player);
      if (character != null) {
         int chargeTicks = character.getChargedAttackChargeTicks();
         if (chargeTicks > 0 && holdTicks >= chargeTicks) {
            if (ActionStateMachine.canInterrupt(2)) {
               ActionSet set = character.getActionSet(player);
               ActionDefinition def = set == null ? null : set.getChargedAttack();
               if (playable(player, def)) {
                  if (ActionCastGuard.canCast(player, character, ActionKind.CHARGED_ATTACK, 0)) {
                     if (character.isSustainedChargedAttack()) {
                        beginSustainedChargedAttack(player, def);
                     } else {
                        engageAndPlay(player, def, 2, null, ActionServer::performChargedAttackToServer);
                     }

                     queueFollowUp(player, def);
                     // 重击打断连击段数：这一下之后的下一次普攻从第 1 段重来
                     ActionStateMachine.comboStage = 1;
                     ActionStateMachine.comboWindowFrames = 0;
                     ActionStateMachine.chargedAttackTriggered = true;
                  }
               }
            }
         }
      }
   }

   @Override
   public void releaseAttack(Player player, int chargeTicks) {
      // 下落攻击是触发式的：点一下进入，期间松开左键不收状态（姿态要一直保持到下劈结束）
      if (PlungeAttack.isActive()) {
         return;
      }
      if (ActionStateMachine.currentStateLoops()) {
         if (player instanceof LocalPlayer) {
            ActionStateMachine.resetToDefault();
            ThirdPersonCamera.setFollowBody(false);
            ActionServer.interruptActionToServer(InterruptReason.CHARGE_RELEASE.ordinal());
         }
      }
   }

   /**
    * 单步动作的「后续片段」：{@link ActionStep#comboEndAnim} 声明这一段播完后接哪条动画。
    *
    * <p>连招本来只在最后一段用它；这里同样给战技 / 重击 / 大招用 —— 两段式招式
    * （例如星见雅的战技 {@code skill_energy} → {@code skill_energy_continue}）靠它表达。
    * 名字查不到时什么都不做，只播第一段。
    */
   private static void queueFollowUp(Player player, ActionDefinition def) {
      String followUp = def.step.comboEndAnim;
      if (followUp != null && AnimationAvailability.existsFor(player, followUp)) {
         ActionStateMachine.queueFollowUpState(followUp, def.step.comboEndTicks);
      }
   }

   private static void beginSustainedChargedAttack(Player player, ActionDefinition def) {
      ActionStep step = def.step;
      int totalTicks = Math.max(1, step.duration);
      ActionStateMachine.setCurrentAttackRange(step.effectiveAttackRange());
      fireLocalTalentHook(player, def);
      ActionSoundScheduler.scheduleActionSounds(player, step);
      if (!AnimationAvailability.existsFor(player, def.animationName())) {
         LOGGER.warn("[MineGenshin] 角色 '{}' 没有持续重击动画 '{}'：照常进状态与结算，只是视觉上停在上一帧（补一个循环动画即可）", CharacterHelper.getActiveCharacterId(player), def.animationName());
      }

      ActionStateMachine.changeState(def.animationName(), 2, totalTicks, step.protectDuration, 0, 0, true);
      holdBodyFacing(player, step);
      ActionServer.performChargedAttackToServer(CombatTargeting.current(player));
   }

   private static void engageAndPlay(
      Player player, ActionDefinition def, int priority, @Nullable String animationOverride, @Nullable Consumer<LivingEntity> serverCall
   ) {
      ActionStep step = def.step;
      float attackRange = step.effectiveAttackRange();
      ActionStateMachine.setCurrentAttackRange(attackRange);
      ResourceDrivenActionHandler.Timing timing = timingFor(step);
      Engagement engagement = step.engagement == null ? Engagement.melee() : step.engagement;
      CombatTargeting.Params params = targetingParams(engagement, attackRange);
      Consumer<LivingEntity> dispatch = serverCall == null ? null : targetx -> {
         fireLocalTalentHook(player, def);
         serverCall.accept(targetx);
      };
      LivingEntity locked = CombatTargeting.current(player);
      LivingEntity acquired = CombatTargeting.acquire(player, params);
      LivingEntity target = acquired != null ? acquired : locked;
      CombatTargeting.lock(player, target, params);

      if (player instanceof LocalPlayer localPlayer && target != null) {
         boolean wantsDash = engagement.wantsDash() && AttackApproach.needsDash(localPlayer, target, attackRange);
         if (wantsDash && step.dashStartDelay > 0) {
            play(player, def, priority, animationOverride, false);
            holdBodyFacing(player, step);
            int sequence = ActionStateMachine.actionSequence();
            int delay = step.dashStartDelay;
            ActionStateMachine.queueClientWork(delay, () -> {
               if (ActionStateMachine.actionSequence() == sequence) {
                  LivingEntity fresh = CombatTargeting.current(localPlayer);
                  LivingEntity now = fresh != null ? fresh : target;
                  if (now != null && AttackApproach.needsDash(localPlayer, now, attackRange)) {
                     beginDash(localPlayer, now, engagement, attackRange, player, step, timing, dispatch);
                  } else {
                     if (now != null) {
                        AttackApproach.faceTarget(localPlayer, now, engagement);
                     }
                     strikeAfterApproach(player, step, timing, dispatch, now);
                  }
               }
            });
            return;
         }

         if (wantsDash) {
            play(player, def, priority, animationOverride, false);
            holdBodyFacing(player, step);
            beginDash(localPlayer, target, engagement, attackRange, player, step, timing, dispatch);
            return;
         }

         play(player, def, priority, animationOverride, true);
         holdBodyFacing(player, step);
         AttackApproach.faceTarget(localPlayer, target, engagement);
         if (engagement == null || engagement.approachStep) {
            AttackApproach.stepToward(localPlayer, target, attackRange, engagement);
         }
         if (dispatch != null) {
            dispatch.accept(target);
         }
         return;
      }

      play(player, def, priority, animationOverride, true);
      holdBodyFacing(player, step);
      if (dispatch != null) {
         dispatch.accept(target);
      }
   }

   private static void holdBodyFacing(Player player, ActionStep step) {
      if (!(player instanceof LocalPlayer)) {
         return;
      }
      ActionBodyFacing mode = step.bodyFacing == null ? ActionBodyFacing.TARGET : step.bodyFacing;
      if (mode.takesOverBodyFacing()) {
         AttackApproach.holdBodyFacing(mode);
      }
   }

   private static void fireLocalTalentHook(Player player, ActionDefinition def) {
      if (player instanceof LocalPlayer) {
         PGCharacter character = currentCharacter(player);
         if (character != null && character.runsTalentOnClient()) {
            Consumer<ActionContext> hook = def.getOnActiveStart();
            if (hook != null) {
               try {
                  hook.accept(new ActionContext(player, character, def));
               } catch (Exception e) {
                  LOGGER.error("[MineGenshin] 客户端本地招式钩子抛异常 kind={}", def.kind, e);
               }
            }
         }
      }
   }

   private static void beginDash(
      LocalPlayer localPlayer,
      LivingEntity target,
      Engagement engagement,
      float attackRange,
      Player player,
      ActionStep step,
      ResourceDrivenActionHandler.Timing timing,
      @Nullable Consumer<LivingEntity> serverCall
   ) {
      AttackApproach.begin(localPlayer, target, engagement, attackRange, () -> strikeAfterApproach(player, step, timing, serverCall, target));
   }

   private static void strikeAfterApproach(
      Player player, ActionStep step, ResourceDrivenActionHandler.Timing timing, @Nullable Consumer<LivingEntity> serverCall, @Nullable LivingEntity target
   ) {
      ActionStateMachine.resumeFromApproach(timing.totalTicks(), timing.lockFrames(), timing.movementLock());
      ActionSoundScheduler.scheduleActionSounds(player, step);
      if (serverCall != null) {
         serverCall.accept(target);
      }
   }

   private static CombatTargeting.Params targetingParams(Engagement engagement, float attackRange) {
      Engagement e = engagement == null ? Engagement.melee() : engagement;
      CombatTargeting.Params base = e.ranged
         ? CombatTargeting.Params.forRange(e.acquireRange > 0.0 ? e.acquireRange : attackRange)
         : CombatTargeting.Params.forChase(attackRange);
      double acquire = e.acquireRange > 0.0 ? e.acquireRange : base.acquireRange();
      double keep = e.keepRange > 0.0 ? e.keepRange : base.keepRange();
      double acquireAngle = e.acquireAngle > 0.0 ? e.acquireAngle : base.acquireAngle();
      double keepAngle = e.keepAngle > 0.0 ? e.keepAngle : base.keepAngle();
      return new CombatTargeting.Params(acquire, acquireAngle, keep, keepAngle, base.attackRange(), base.chase(), TargetPolicy.DEFAULT);
   }

   private static boolean play(Player player, ActionDefinition def, int priorityOverride) {
      return play(player, def, priorityOverride, null, true);
   }

   private static boolean play(Player player, ActionDefinition def, int priorityOverride, @Nullable String animationOverride) {
      return play(player, def, priorityOverride, animationOverride, true);
   }

   private static boolean play(Player player, ActionDefinition def, int priorityOverride, @Nullable String animationOverride, boolean feedbackNow) {
      ActionStep step = def.step;
      ResourceDrivenActionHandler.Timing timing = timingFor(step);
      String animation = animationOverride != null && !animationOverride.isEmpty() ? animationOverride : def.animationName();
      boolean animated = AnimationAvailability.existsFor(player, animation);
      if (animated) {
         ActionStateMachine.changeState(animation, priorityOverride, timing.totalTicks(), timing.lockFrames(), timing.movementLock(), timing.lockDelay());
      } else {
         LOGGER.warn("[MineGenshin] 角色 '{}' 没有动画 '{}'：这次动作只结算伤害，不切动画", CharacterHelper.getActiveCharacterId(player), animation);
      }

      if (feedbackNow) {
         ActionSoundScheduler.scheduleActionSounds(player, step);
      }

      return animated;
   }

   private static String resolveDodgeAnim(Player player, ActionDefinition def) {
      String configured = def.animationName();
      Set<String> specialAnims = CharacterActions.animationsFor(player).specialAnims();
      String base = configured;

      for (String direction : DODGE_DIRECTIONS) {
         String suffix = "_" + direction;
         if (base.endsWith(suffix)) {
            base = base.substring(0, base.length() - suffix.length());
            break;
         }
      }

      String candidate = base + "_" + dodgeDirection(player);
      if (specialAnims.contains(candidate)) {
         return candidate;
      } else {
         return specialAnims.contains(configured) ? configured : candidate;
      }
   }

   private static String dodgeDirection(Player player) {
      Vec2 move = player instanceof LocalPlayer localPlayer ? localPlayer.input.getMoveVector() : Vec2.ZERO;
      if (move.lengthSquared() < 1.0E-5F) {
         return "back";
      } else if (Math.abs(move.y) >= Math.abs(move.x)) {
         return move.y >= 0.0F ? "front" : "back";
      } else {
         return move.x > 0.0F ? "left" : "right";
      }
   }

   private static ResourceDrivenActionHandler.Timing timingFor(@Nullable ActionStep step) {
      int totalTicks = step == null ? 1 : Math.max(1, step.duration);
      int prepare = step == null ? 0 : Math.max(0, Math.min(step.prepareTicks, totalTicks));
      int execution = step == null ? 0 : step.protectDuration - prepare;
      if (execution <= 0) {
         int lock = Math.min(Math.max(1, totalTicks / 4), 8);
         return new ResourceDrivenActionHandler.Timing(totalTicks, lock, prepare, Math.min(lock, 5));
      } else {
         return new ResourceDrivenActionHandler.Timing(totalTicks, execution, prepare, execution);
      }
   }

   private static boolean playable(@Nullable Player player, @Nullable ActionDefinition def) {
      return player != null && def != null && def.step != null;
   }

   @Nullable
   private static PGCharacter currentCharacter(Player player) {
      PlayerCharactersAttachment attachment = (PlayerCharactersAttachment)player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
      return attachment == null ? null : attachment.getCurrentCharacter();
   }

   private record Timing(int totalTicks, int lockFrames, int lockDelay, int movementLock) {
   }
}
