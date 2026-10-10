// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character.talent;

import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.elementlib.core.system.about.AttachmentType;
import com.linweiyun.genshin.core.system.combat.action.ActionDefinition;
import com.linweiyun.genshin.core.system.combat.action.ActionKind;
import com.linweiyun.genshin.core.system.combat.action.ActionSet;
import com.linweiyun.genshin.core.system.combat.action.data.ActionStep;
import com.linweiyun.genshin.core.system.combat.action.data.BurstData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterActionData;
import com.linweiyun.genshin.core.system.combat.action.data.ComboData;
import com.linweiyun.genshin.core.system.combat.action.data.DodgeData;
import com.linweiyun.genshin.core.system.combat.action.data.SkillData;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.genshin.core.system.combat.flight.GenshinFlight;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 一个角色的招式基类 —— 普攻 / 重击 / <b>下落攻击</b> / 战技 / 爆发 / 闪避都在这里，
 * 需要改某个招式的角色就覆盖对应方法。
 */
public class SkillBase {
   /**
    * 下落攻击的默认动画名。
    *
    * <p>角色动画文件里没有这个名字时 {@code AnimationAvailability} 会拦住切换 ——
    * 状态与伤害照常走，只是视觉上停在上一帧。林薇云现在还没做下落攻击动画，
    * 等她那份素材进仓库后按这个名字（或覆盖 {@link #plungingAnimation()}）接上即可。
    */
   public static final String DEFAULT_PLUNGING_ANIM = "attack_plunge";

   /** 下落攻击落地的冲击半径（格）——「基类方法，大部分角色都是这样」。 */
   public static final double DEFAULT_PLUNGING_RADIUS = 3.0;

   /** 下落攻击落地的基准倍率（占位：100% 攻击力）。逐角色的正式数值来了再覆盖。 */
   public static final float DEFAULT_PLUNGING_MULTIPLIER = 1.0f;

   /** 下落攻击的加速下坠速度（格 / 刻，<b>正数 = 向下</b>，和玩家那套 Y 轴符号相反）。 */
   public static final double DEFAULT_PLUNGING_FALL_SPEED = 1.5;

   /** 起飞前摇的默认动画名（二连跳之后的「展开 / 蓄势」那一段）。 */
   public static final String DEFAULT_FLY_START_ANIM = "fly_start";

   /** 起飞前摇的默认刻数 —— 每个角色（林薇云是每个武器形态）不一样，覆盖 {@link #flyStartTicks()}。 */
   public static final int DEFAULT_FLY_START_TICKS = 8;

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

   /**
    * <b>下落攻击</b> —— 「坠落中按普攻」触发的那个状态，落地时结算这一下。
    *
    * <p>和 {@link #attack} / {@link #chargeAttack} 是同一个位置的东西：<b>招式本体</b>。
    * 基类的实现就是「大部分角色都长这样」的那一份 —— 落地时对身周
    * {@link #DEFAULT_PLUNGING_RADIUS} 格内的敌人打一发 {@link AttackType#PLUNGING_ATTACK}；
    * 有自己花样的角色覆盖它即可。
    *
    * <p><b>只跑服务端</b>：伤害结算一律在服务端，客户端那一份（加速下坠、动作状态、
    * 锁输入）在 {@code client.combat.PlungeAttack} 里，两边由 RPC 对齐。
    *
    * <p>「加速下坠」和「摔落伤害减免」不在这里 —— 前者是客户端每刻的物理，
    * 后者走 {@code FallDamage} 那条曲线（下落攻击把免伤区间抬到 38 格）。
    */
   public void plungingAttack(Player player, PGCharacter character) {
      if (player.level().isClientSide() || character == null) {
         return;
      }

      float multiplier = this.plungingDamageMultiplier(character);
      if (multiplier <= 0f) {
         return;
      }

      double radius = this.plungingImpactRadius();
      AABB impactBox = new AABB(
            player.getX() - radius, player.getY() - 1.0, player.getZ() - radius,
            player.getX() + radius, player.getY() + 2.0, player.getZ() + radius);
      List<LivingEntity> targets = player.level().getEntitiesOfClass(LivingEntity.class, impactBox,
            e -> e != player && e.isAlive());

      GenshinElement element = this.plungingElement(character);
      for (LivingEntity target : targets) {
         ModDamageSpec spec = ModDamageSpec.builder(AttackType.PLUNGING_ATTACK, element)
               .multiplier(multiplier)
               .elementAmount(AttachmentType.WEAK.getInitialAmount())
               .attackerCharacter(character)
               .build();
         ModDamageSource source = ModDamageSource.from(spec, player);
         if (target.level() instanceof ServerLevel serverLevel) {
            target.hurt(source, 0f);
         }
      }
   }

   /** 下落攻击落地的倍率（占位 100% 攻击力）。**/
   protected float plungingDamageMultiplier(PGCharacter character) {
      return DEFAULT_PLUNGING_MULTIPLIER;
   }

   /** 下落攻击落地的冲击半径（格）。 */
   protected double plungingImpactRadius() {
      return DEFAULT_PLUNGING_RADIUS;
   }

   /** 下落攻击用哪个元素结算：默认跟着角色自己的元素走，没有元素就按物理算。 */
   protected GenshinElement plungingElement(PGCharacter character) {
      GenshinElement elemental = character.getElemental();
      return elemental == null ? ModElements.FYSIKOS.get() : elemental;
   }

   /** 下落攻击播放哪条动画（角色可以用自己的名字覆盖）。 */
   public String plungingAnimation() {
      return DEFAULT_PLUNGING_ANIM;
   }

   /**
    * 下落攻击停在这条动画的哪一刻（刻，可带小数 —— 素材里的时间轴按秒写，1 秒 = 20 刻）；负数 = 整条循环播。
    *
    * <p>给「素材里没有下劈那一段、只能借别段动画的一个姿态」的角色用：
    * 下坠期间动画时间轴钉在这一刻，看起来就是举着武器下劈。
    */
   public double plungingAnimationHoldTick() {
      return -1.0;
   }

   /** 下落攻击的加速下坠速度（格 / 刻，正数 = 向下）。 */
   public double plungingFallSpeed() {
      return DEFAULT_PLUNGING_FALL_SPEED;
   }

   /**
    * 落地后接着播的状态名（走动画别名映射）；返回 null = 不续播，直接回常。
    *
    * <p>给「下坠时钉在某个姿态」的角色用：落地后从 {@link #plungingAnimationHoldTick()} 那一刻继续把同一条动画播完。
    */
   public String plungingRecoveryAnimation() {
      return null;
   }

   /** 落地续播状态的动画总长（刻）；&lt;= 0 表示不续播。 */
   public int plungingRecoveryTicks() {
      return 0;
   }

   /** 落地那一刻播放的音效 id；null = 不放。 */
   public String plungingLandingSound() {
      return null;
   }

   /** 落地音效音量。 */
   public float plungingLandingSoundVolume() {
      return 1.0F;
   }

   public void elementalSkill(Player player, PGCharacter character, int skillTime) {
   }

   public void elementalBurst(Player player, PGCharacter character) {
   }

   public void dodge(Player player, PGCharacter character) {
   }

   /**
    * <b>这一招现在放得出来吗</b>（招式自己的门禁，和冷却 / 能量是两回事）。
    *
    * <p>基类只管一条规则：<b>自由飞行期间不能放技能和大招</b>（普攻、闪避、下落攻击照常）。
    * 拦的是招式而不是按键：部分角色的部分技能允许在空中 / 飞行中放，
    * 那种角色覆盖本方法、放行自己允许的招式即可。
    *
    * <p>被 {@code PGCharacter#canCast} 与客户端的 {@code ActionCastGuard} 共用，
    * 两端同一条规则（客户端连动画都不会播）。
    */
   public boolean canCast(Player player, ActionKind kind) {
      if (!GenshinFlight.isFlying(player)) {
         return true;
      }
      return switch (kind) {
         case ELEMENTAL_SKILL_TAP, ELEMENTAL_SKILL_HOLD, ELEMENTAL_BURST -> false;
         default -> true;
      };
   }

   /** 二连跳之后、真正起飞之前那段前摇的刻数（每个角色不同；林薇云是每个武器形态一档）。 */
   public int flyStartTicks() {
      return DEFAULT_FLY_START_TICKS;
   }

   /** 起飞前摇播哪条动画。 */
   public String flyStartAnimation() {
      return DEFAULT_FLY_START_ANIM;
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
