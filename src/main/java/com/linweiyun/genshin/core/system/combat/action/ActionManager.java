// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.system.combat.action;

import com.linweiyun.genshin.content.items.weapon.WeaponItem;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import com.linweiyun.genshin.core.system.combat.attack.PlungeState;
import com.linweiyun.genshin.core.system.combat.targeting.CombatTargeting;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class ActionManager {
   private static final Logger LOGGER = ModLog.getLogger(LogGroup.COMBAT);
   private static final Map<String, ActionManager> MANAGERS = new ConcurrentHashMap<>();
   private ActionState current;
   private ActionDefinition buffered;
   private PGCharacter activeCharacter;
   private int lastComboIndex = 0;
   private long lastComboEndTick = Long.MIN_VALUE;

   public static ActionManager get(Player player) {
      String key = (player.level().isClientSide() ? "C:" : "S:") + player.getUUID();
      return MANAGERS.computeIfAbsent(key, k -> new ActionManager());
   }

   public static void remove(Player player) {
      String key = (player.level().isClientSide() ? "C:" : "S:") + player.getUUID();
      MANAGERS.remove(key);
   }

   public boolean requestNormalAttack(Player player, PGCharacter character) {
      return this.requestNormalAttack(player, character, -1);
   }

   public boolean requestNormalAttack(Player player, PGCharacter character, int requestedStage) {
      String side = player.level().isClientSide() ? "CLIENT" : "SERVER";
      ActionSet set = character.getActionSet(player);
      if (set == null) {
         LOGGER.warn("[ActionManager] [{}] requestNormalAttack: actionSet=null (talent={})", side, character.getTalentDebugInfo());
         return false;
      } else if (set.getNormalComboSize() == 0) {
         LOGGER.warn("[ActionManager] [{}] requestNormalAttack: comboSize=0", side);
         return false;
      } else {
         int idx = this.resolveNextComboIndex(player, set, requestedStage);
         return this.request(player, character, set.getNormalAttack(idx));
      }
   }

   public boolean requestChargedAttack(Player player, PGCharacter character) {
      String side = player.level().isClientSide() ? "CLIENT" : "SERVER";
      ActionSet set = character.getActionSet(player);
      if (set == null) {
         LOGGER.warn("[ActionManager] [{}] requestChargedAttack: actionSet=null", side);
         return false;
      } else {
         return this.request(player, character, set.getChargedAttack());
      }
   }

   public boolean requestElementalSkill(Player player, PGCharacter character, int skillTime) {
      String side = player.level().isClientSide() ? "CLIENT" : "SERVER";
      LOGGER.info("[ActionManager] [{}] requestElementalSkill skillTime={}", side, skillTime);
      ActionSet set = character.getActionSet(player);
      if (set == null) {
         LOGGER.warn("[ActionManager] [{}] actionSet=null (talent={})", side, character.getTalentDebugInfo());
         return false;
      } else {
         boolean longPress = skillTime >= 1000;
         ActionDefinition def = longPress ? set.getElementalSkillHold() : set.getElementalSkillTap();
         if (def == null) {
            LOGGER.warn(
               "[ActionManager] [{}] elementalSkill{} def=null (comboSize={})", new Object[]{side, longPress ? "Hold" : "Tap", set.getNormalComboSize()}
            );
            return false;
         } else if (!character.canCast(player, def.kind, skillTime)) {
            LOGGER.info("[ActionManager] [{}] canCast=false, rejected", side);
            character.sendCastFailedMessage(player, def.kind);
            return false;
         } else if (!this.request(player, character, def)) {
            LOGGER.info("[ActionManager] [{}] request() rejected (busy?)", side);
            return false;
         } else {
            character.applyElementalSkillCooldown(player, skillTime);
            notifyWeaponAbilityCast(player, character, def.kind);
            return true;
         }
      }
   }

   private static void notifyWeaponAbilityCast(Player player, PGCharacter character, ActionKind kind) {
      if (!player.level().isClientSide()) {
         ItemStack weapon = character.getData().getWeapon();
         if (weapon != null && !weapon.isEmpty() && weapon.getItem() instanceof WeaponItem weaponItem) {
            weaponItem.onAbilityCast(player, character, kind);
         }
      }
   }

   public boolean requestElementalBurst(Player player, PGCharacter character) {
      String side = player.level().isClientSide() ? "CLIENT" : "SERVER";
      LOGGER.info("[ActionManager] [{}] requestElementalBurst", side);
      ActionSet set = character.getActionSet(player);
      if (set == null) {
         LOGGER.warn("[ActionManager] [{}] actionSet=null", side);
         return false;
      } else {
         ActionDefinition def = set.getElementalBurst();
         if (def == null) {
            LOGGER.warn("[ActionManager] [{}] burst def=null", side);
            return false;
         } else if (!character.canCast(player, def.kind, 0)) {
            LOGGER.info("[ActionManager] [{}] burst canCast=false", side);
            character.sendCastFailedMessage(player, def.kind);
            return false;
         } else if (!this.request(player, character, def)) {
            LOGGER.info("[ActionManager] [{}] burst request() rejected", side);
            return false;
         } else {
            character.applyElementalBurstCooldown(player);
            notifyWeaponAbilityCast(player, character, ActionKind.ELEMENTAL_BURST);
            LOGGER.info("[ActionManager] [{}] burst STARTED", side);
            return true;
         }
      }
   }

   public boolean requestDodge(Player player, PGCharacter character) {
      ActionSet set = character.getActionSet(player);
      if (set == null) {
         return false;
      }

      ActionDefinition def = set.getDodge();
      return def == null ? false : this.request(player, character, def);
   }

   private boolean request(Player player, PGCharacter character, ActionDefinition def) {
      if (def == null) {
         return false;
      }

      // 下落攻击期间一切照常动作都拦掉（攻击 / 重击 / 战技 / 大招 / 闪避都走这里）。
      // 客户端那边由状态机优先级 + 输入拦截挡住，这里是服务端那一份权威 ——
      // 包被伪造也得弹回来。落地那一下不走 ActionManager（见 CharacterTickHandler）。
      if (PlungeState.isPlunging(player)) {
         LOGGER.info("[ActionManager] [{}] 下落攻击中，拒绝动作 kind={}",
                 player.level().isClientSide() ? "CLIENT" : "SERVER", def.kind);
         return false;
      }

      if (this.activeCharacter != null && this.activeCharacter != character) {
         if (this.current != null && !this.current.isFinished()) {
            this.current.interrupt(InterruptReason.SWITCH_CHARACTER);
         }

         this.buffered = null;
         this.resetCombo();
      }

      this.activeCharacter = character;
      if (this.current != null && !this.current.isFinished()) {
         if (this.current.isProtected()) {
            LOGGER.info("[ActionManager] [{}] 当前动作在执行期内，请求被拒 kind={}", player.level().isClientSide() ? "CLIENT" : "SERVER", def.kind);
            return false;
         }

         this.current.interrupt(InterruptReason.MANUAL);
         if (!def.isCombo()) {
            this.resetCombo();
         }
      }

      this.start(player, character, def);
      return true;
   }

   private void start(Player player, PGCharacter character, ActionDefinition def) {
      ActionContext ctx = new ActionContext(player, character, def);
      this.current = new ActionState(def, ctx);
      this.scheduleStepMovement(player, character, def);
   }

   private void scheduleStepMovement(Player player, PGCharacter character, ActionDefinition def) {
      if (!player.level().isClientSide() && def.step != null) {
         boolean skipByLock = CombatTargeting.isLocked(player)
                 && (def.step.engagement == null || def.step.engagement.approachStep);
         if (!skipByLock) {
            ServerActionExecutor.execute(player, def.step, character.getTextureId());
         }
      }
   }

   public void tick(Player player, PGCharacter character) {
      if (this.activeCharacter == null || this.activeCharacter == character) {
         if (this.current != null && !this.current.isFinished()) {
            this.current.tick();
            if (this.current.isFinished()) {
               ActionDefinition def = this.current.getDefinition();
               if (def.isCombo()) {
                  this.lastComboIndex = def.comboIndex;
                  this.lastComboEndTick = player.level().getGameTime();
               }

               if (this.buffered != null) {
                  ActionDefinition next = this.buffered;
                  this.buffered = null;
                  this.start(player, character, next);
               }
            }
         } else {
            if (this.buffered != null) {
               ActionDefinition next = this.buffered;
               this.buffered = null;
               this.start(player, character, next);
            }
         }
      }
   }

   public void interrupt(InterruptReason reason) {
      if (this.current != null && !this.current.isFinished()) {
         if (reason != InterruptReason.CHARGE_RELEASE) {
            boolean forced = reason == InterruptReason.SWITCH_CHARACTER || reason == InterruptReason.DEATH || reason == InterruptReason.JUMP;
            if (!forced) {
               if (!this.current.isProtected()) {
                  this.current.interrupt(reason);
                  this.buffered = null;
               }
            } else {
               this.current.interrupt(reason);
               this.buffered = null;
               this.resetCombo();
               if (reason == InterruptReason.SWITCH_CHARACTER || reason == InterruptReason.DEATH) {
                  this.activeCharacter = null;
               }
            }
         } else if (isSustainedChargedAttack(this.current)) {
            this.current.interrupt(reason);
            this.buffered = null;
         }
      }
   }

   private static boolean isSustainedChargedAttack(@Nullable ActionState state) {
      if (state == null) {
         return false;
      }

      ActionDefinition def = state.getDefinition();
      return def != null && def.kind == ActionKind.CHARGED_ATTACK && def.step != null && def.step.loopAnimation;
   }

   public boolean isBusy() {
      return this.current != null && !this.current.isFinished();
   }

   public boolean isMovementBlocked() {
      return this.current != null && !this.current.isFinished() && this.current.isProtected();
   }

   public boolean isAttackBlocked() {
      return this.current != null && !this.current.isFinished() && this.current.isProtected();
   }

   public ActionState getCurrent() {
      return this.current;
   }

   private int resolveNextComboIndex(Player player, ActionSet set) {
      return this.resolveNextComboIndex(player, set, -1);
   }

   private int resolveNextComboIndex(Player player, ActionSet set, int requestedStage) {
      if (requestedStage >= 1) {
         return (requestedStage - 1) % set.getNormalComboSize() + 1;
      }

      if (this.current != null && !this.current.isFinished() && this.current.getDefinition().isCombo()) {
         return this.current.getDefinition().comboIndex + 1;
      }

      if (this.lastComboIndex <= 0) {
         return 1;
      }

      ActionDefinition lastDef = set.getNormalAttack(this.lastComboIndex);
      if (lastDef == null) {
         return 1;
      }

      long elapsed = player.level().getGameTime() - this.lastComboEndTick;
      return elapsed > lastDef.comboWindow() ? 1 : this.lastComboIndex + 1;
   }

   public void resetCombo() {
      this.lastComboIndex = 0;
      this.lastComboEndTick = Long.MIN_VALUE;
      this.buffered = null;
   }
}