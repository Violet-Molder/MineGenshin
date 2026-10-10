package com.linweiyun.genshin.core.character.polearm.raiden_shogun;

import com.linweiyun.genshin.config.character.ShenheAttributeConfig;
import com.linweiyun.genshin.core.character.polearm.PolearmCharacter;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterActionData;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.core.character.util.type.CharacterAscendAttribute;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class RaidenShogun extends PolearmCharacter {

   /** 元素战技的协同攻击持续时间（刻），后台也生效。 */
   public static final int COORDINATED_DURATION_TICKS = 30 * 20;

   /** 协同攻击冷却（刻），0.8 秒。 */
   public static final int COORDINATED_COOLDOWN_TICKS = 16;

   /** 协同攻击的伤害倍率（占攻击力）。 */
   public static final float COORDINATED_DAMAGE = 1.0f;

   @com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted(key = "raidenCoordinated")
   protected int coordinatedTicks;

   @com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted(key = "raidenCoordinatedGate")
   protected long coordinatedGateTick;

   public void beginCoordinated() {
      coordinatedTicks = COORDINATED_DURATION_TICKS;
   }

   public boolean coordinatedActive() {
      return coordinatedTicks > 0;
   }

   public boolean coordinatedReady(long gameTime) {
      return coordinatedTicks > 0 && gameTime >= coordinatedGateTick;
   }

   public void markCoordinated(long gameTime) {
      coordinatedGateTick = gameTime + COORDINATED_COOLDOWN_TICKS;
   }

   @Override
   public void backTick(net.minecraft.world.entity.player.Player player) {
      if (!player.level().isClientSide() && coordinatedTicks > 0) {
         coordinatedTicks--;
      }
   }

   /** 后台钩子：队伍造成伤害时，若协同攻击就绪就打一次雷元素协同攻击。 */
   @Override
   public void backDamage(net.minecraft.world.entity.player.Player player,
                            net.minecraft.world.entity.LivingEntity target,
                            com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec spec) {
      if (coordinatedAttackInProgress || target == null || !target.isAlive()) {
         return;
      }
      long now = player.level().getGameTime();
      if (!coordinatedReady(now)) {
         return;
      }
      markCoordinated(now);
      coordinatedAttackInProgress = true;
      try {
         com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec strike =
                 com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec
                         .builder(com.linweiyun.genshin.core.system.combat.attack.AttackType.ELEMENTAL_SKILL,
                                 com.linweiyun.genshin.core.element.ModElements.ELECTRO.get())
                         .multiplier(COORDINATED_DAMAGE)
                         .elementAmount(com.linweiyun.elementlib.core.system.about.AttachmentType.WEAK.getInitialAmount())
                         .decayGroup(com.linweiyun.genshin.core.system.combat.decay.ModDecayGroups.RAIDEN_COORDINATED)
                         .attackerCharacter(this)
                         .build();
         target.hurt(com.linweiyun.genshin.core.system.combat.damage.ModDamageSource.from(strike, player), 0f);
      } finally {
         coordinatedAttackInProgress = false;
      }
   }

   /** 协同攻击自身造成的伤害不再触发协同攻击。 */
   private static boolean coordinatedAttackInProgress;

    public RaidenShogun() {
        super(135003, 5, Component.translatable("character.name.raiden_shogun"),
            ModElements.ELECTRO.getId().toString(), CharacterAscendAttribute.ATK,
            10 * 20,  10 * 20, 80f, "raiden_shogun",
            Map.of(
                    ModAttributes.MAX_HP.getId(), ShenheAttributeConfig::getAllHp,
                    ModAttributes.ATK.getId(), ShenheAttributeConfig::getAllAtk,
                    ModAttributes.DEF.getId(), ShenheAttributeConfig::getAllDef
            ));
        // 三个协作者都在无参构造器里建（客户端反序列化走 newInstance()，会跑到这里）。
        this.skill = new RaidenShogunSkill();
        this.talent = new RaidenShogunTalent();
        this.constellation = new RaidenShogunConstellation();
    }

    /**
     * 数据驱动的动作配置 —— 客户端与服务端都从这一份表构建 {@code ActionSet}
     * （时序 / 动画名 / 索敌形态），伤害仍在 {@link RaidenShogunSkill} 里结算。
     *
     * <p>不覆写这个方法的话会落到 {@code SkillBase} 的通用兜底表；接上自己的表之后
     * 角色才真正接进当前的动作系统。
     */
    @Override
    public CharacterActionData getActionData() {
        return RaidenShogunResources.ACTION_DATA;
    }

    @Override
    public Map<Identifier, Supplier<List<? extends Integer>>> getStatGrowthMap() {
        return Map.of(
                ModAttributes.MAX_HP.getId(), ShenheAttributeConfig::getAllHp,
                ModAttributes.ATK.getId(), ShenheAttributeConfig::getAllAtk,
                ModAttributes.DEF.getId(), ShenheAttributeConfig::getAllDef
        );
    }
}
