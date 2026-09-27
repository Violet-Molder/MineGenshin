// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character.talent;

import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.system.combat.action.ActionDefinition;
import com.linweiyun.genshin.core.system.combat.action.ActionKind;
import com.linweiyun.genshin.core.system.combat.action.ActionSet;
import com.linweiyun.genshin.core.system.combat.action.data.ActionStep;
import com.linweiyun.genshin.core.system.combat.action.data.BurstData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterActionData;
import com.linweiyun.genshin.core.system.combat.action.data.ComboData;
import com.linweiyun.genshin.core.system.combat.action.data.DodgeData;
import com.linweiyun.genshin.core.system.combat.action.data.SkillData;
import net.minecraft.world.entity.player.Player;

public class SkillBase {
   public int getMaxCombo() {
      return 1;
   }

   public int getChargeTicks() {
      return 20;
   }

   public boolean isSustainedChargedAttack() {
      return false;
   }

   public int getChargedAttackMaxTicks() {
      return 0;
   }

   public void attack(Player player, PGCharacter character, int comboStage) {
   }

   public void chargeAttack(Player player, PGCharacter character) {
   }

   public void elementalSkill(Player player, PGCharacter character, int skillTime) {
   }

   public void elementalBurst(Player player, PGCharacter character) {
   }

   public void dodge(Player player, PGCharacter character) {
   }

   public void onCastStart(Player player, PGCharacter character, ActionKind kind) {
   }

   public void tick(Player player, PGCharacter character) {
   }

   public ActionSet buildActionSet(PGCharacter character, String stateKey) {
      return this.buildDefaultActionSet(character);
   }

   protected ActionSet buildDefaultActionSet(PGCharacter character) {
      int maxCombo = this.getMaxCombo();
      CharacterActionData actionData = character.getActionData();
      if (actionData == null) {
         actionData = CharacterActionData.fallback(maxCombo);
      }

      SkillBase.SetBuilder sb = this.setBuilder();
      if (actionData != null) {
         ComboData combo = actionData.combo();
         if (combo != null) {
            int steps = Math.max(1, Math.min(maxCombo, combo.maxCombo()));

            for (int stage = 1; stage <= steps; stage++) {
               ActionStep step = combo.getStep(stage);
               if (step != null) {
                  int s = stage;
                  sb.addNormalAttack(
                     ActionDefinition.builder(ActionKind.NORMAL_ATTACK)
                        .comboIndex(s)
                        .step(step)
                        .onCastStart(ctx -> this.onCastStart(ctx.player, ctx.character, ActionKind.NORMAL_ATTACK))
                        .onActiveStart(ctx -> {
                           this.attack(ctx.player, ctx.character, s);
                           if (ctx.character.spawnsNormalAttackParticle()) {
                              ctx.character.trySpawnNormalAttackParticle(ctx.player);
                           }
                        })
                        .build()
                  );
               }
            }
         }

         SkillData skill = actionData.skill();
         if (skill != null) {
            if (skill.tap() != null) {
               sb.addSkillTap(
                  ActionDefinition.builder(ActionKind.ELEMENTAL_SKILL_TAP)
                     .step(skill.tap())
                     .onCastStart(ctx -> this.onCastStart(ctx.player, ctx.character, ActionKind.ELEMENTAL_SKILL_TAP))
                     .onActiveStart(ctx -> this.elementalSkill(ctx.player, ctx.character, 0))
                     .build()
               );
            }

            if (skill.hold() != null) {
               sb.addSkillHold(
                  ActionDefinition.builder(ActionKind.ELEMENTAL_SKILL_HOLD)
                     .step(skill.hold())
                     .onCastStart(ctx -> this.onCastStart(ctx.player, ctx.character, ActionKind.ELEMENTAL_SKILL_HOLD))
                     .onActiveStart(ctx -> this.elementalSkill(ctx.player, ctx.character, 1000))
                     .build()
               );
            }
         }

         BurstData burst = actionData.burst();
         if (burst != null && burst.step() != null) {
            sb.addBurst(
               ActionDefinition.builder(ActionKind.ELEMENTAL_BURST)
                  .step(burst.step())
                  .onCastStart(ctx -> this.onCastStart(ctx.player, ctx.character, ActionKind.ELEMENTAL_BURST))
                  .onActiveStart(ctx -> this.elementalBurst(ctx.player, ctx.character))
                  .build()
            );
         }

         DodgeData dodgeData = actionData.dodge();
         if (dodgeData != null && dodgeData.step() != null) {
            sb.addDodge(
               ActionDefinition.builder(ActionKind.DODGE)
                  .step(dodgeData.step())
                  .onCastStart(ctx -> this.onCastStart(ctx.player, ctx.character, ActionKind.DODGE))
                  .onActiveStart(ctx -> this.dodge(ctx.player, ctx.character))
                  .build()
            );
         }
      }

      if (actionData != null && actionData.skill() != null && actionData.skill().tap() != null) {
         sb.addCharged(
            ActionDefinition.builder(ActionKind.CHARGED_ATTACK)
               .step(actionData.skill().tap())
               .onCastStart(ctx -> this.onCastStart(ctx.player, ctx.character, ActionKind.CHARGED_ATTACK))
               .onActiveStart(ctx -> this.chargeAttack(ctx.player, ctx.character))
               .build()
         );
      }

      return sb.build();
   }

   protected SkillBase.SetBuilder setBuilder() {
      return new SkillBase.SetBuilder(null);
   }

   protected SkillBase.SetBuilder setBuilder(ActionSet parent) {
      return new SkillBase.SetBuilder(parent);
   }

   public final class SetBuilder {
      private final ActionSet.Builder inner;

      private SetBuilder(ActionSet parent) {
         this.inner = parent != null ? ActionSet.deriveFrom(parent) : ActionSet.builder();
      }

      public SkillBase.SetBuilder addNormalAttack(ActionDefinition def) {
         this.inner.addNormalAttack(def);
         return this;
      }

      public SkillBase.SetBuilder addCharged(ActionDefinition def) {
         this.inner.chargedAttack(def);
         return this;
      }

      public SkillBase.SetBuilder addSkillTap(ActionDefinition def) {
         this.inner.elementalSkillTap(def);
         return this;
      }

      public SkillBase.SetBuilder addSkillHold(ActionDefinition def) {
         this.inner.elementalSkillHold(def);
         return this;
      }

      public SkillBase.SetBuilder addBurst(ActionDefinition def) {
         this.inner.elementalBurst(def);
         return this;
      }

      public SkillBase.SetBuilder addDodge(ActionDefinition def) {
         this.inner.dodge(def);
         return this;
      }

      public SkillBase.SetBuilder clearNormalCombo() {
         this.inner.clearNormalCombo();
         return this;
      }

      public ActionSet build() {
         return this.inner.build();
      }
   }
}
