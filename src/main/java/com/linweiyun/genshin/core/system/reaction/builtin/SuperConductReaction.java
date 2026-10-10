package com.linweiyun.genshin.core.system.reaction.builtin;

import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.genshin.core.system.performance.BoundedLruMap;
import com.linweiyun.elementlib.core.system.reaction.ElementalReaction;
import com.linweiyun.elementlib.api.ElementalReactionType;
import com.linweiyun.elementlib.core.system.reaction.ReactionContext;
import com.linweiyun.elementlib.core.system.reaction.ReactionResult;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import com.linweiyun.genshin.core.system.registry.register.ModReactionTypes;

/**
 * 超导反应 —— 雷 + 冰，<b>剧变反应</b>。
 *
 * <h2>消耗</h2>
 * 注册比 1:1，双方同时消耗，哪边先耗尽就停（同基类规则）：
 * 后手是 A 还是 B 由 {@code attackerIsA} 映射回固定槽位，之后按槽位扣减。
 *
 * <h2>伤害</h2>
 * 消耗结束后对目标结算一次<b>冰元素剧变伤害</b>：
 * 走 {@link ModDamageSpec#transformative} → {@code TransformativeDamage} 管线，
 * 倍率取 {@code ReactionConfig.SUPERCONDUCT}。
 *
 * <p>同一目标两次超导伤害之间至少隔 {@value #DAMAGE_COOLDOWN_TICKS} tick（0.5 秒）：
 * 冷却期内反应照常发生、元素照常消耗，只是不再结算伤害。方块端没有实体，
 * 只做消耗，不出伤害。
 */
public class SuperConductReaction extends ElementalReaction {

    public static final Logger LOGGER = ModLog.getLogger(LogGroup.ELEMENT);

    /** 超导伤害冷却：同一目标 10 tick 内不重复结算伤害（元素照常消耗）。 */
    private static final int DAMAGE_COOLDOWN_TICKS = 10;

    /**
     * 同一目标上次结算超导伤害的时刻。
     *
     * <p>键是实体 UUID，只 put 不 remove 的静态表会随刷怪无限增长（内存泄漏），
     * 所以用有界 LRU：被淘汰等价于那条冷却记录自然过期。</p>
     */
    private static final Map<UUID, Long> lastDamageTick = BoundedLruMap.create();

    public SuperConductReaction(java.util.function.Supplier<ElementalReactionType> reactionType,
                                String elementAId, String elementBId,
                                float ratioA, float ratioB, int basePriority) {
        super(reactionType, elementAId, elementBId, ratioA, ratioB, basePriority);
    }

    @Override
    public ReactionResult execute(ReactionContext context) {
        GenshinElement elA = getElementA();
        GenshinElement elB = getElementB();
        GenshinElement attackerMain = context.attackerElement().getMainElement();
        boolean attackerIsA = (attackerMain == elA);

        GenshinElement defenderTarget = attackerIsA ? elB : elA;
        float totalDefenderUnit = sumConsumable(context.targetContainer(), defenderTarget);
        if (totalDefenderUnit <= 0f) {
            return ReactionResult.builder(type()).build();
        }

        float attackerUnit = context.attackerUnit();
        float[] consumed = attackerIsA
                ? calculateConsumption(attackerUnit, totalDefenderUnit)
                : calculateConsumption(totalDefenderUnit, attackerUnit);
        float consumedA = consumed[0];
        float consumedB = consumed[1];
        if (consumedA <= 0f || consumedB <= 0f) {
            return ReactionResult.builder(type()).build();
        }

        consumeElementUnit(context.targetContainer(), elB, consumedB);
        consumeElementUnit(context.targetContainer(), elA, consumedA);

        boolean converted = StellarConduceReaction.convert(context);
        if (!converted) {
            applyDamageOffCooldown(context);
        }
        ElementalReactionType convertedType = attackerMain == ModElements.ELECTRO.get()
                ? ModReactionTypes.STELLAR_CONDUCE.get()
                : ModReactionTypes.STELLAR_CONDUCE.get();

        return ReactionResult.builder(converted ? convertedType : type())
                .reacted()
                .consumedAttacker(attackerIsA ? consumedA : consumedB)
                .consumedDefender(attackerIsA ? consumedB : consumedA)
                .build();
    }

    /**
     * 结算一次冰元素剧变伤害；冷却期内或方块端（无实体）直接跳过。
     *
     * <p>飘字不在这里发 —— 由 {@code ElementalReactionManager} 统一按反应类型出字，
     * 见 {@code shouldShowIndicator}。
     */
    private static void applyDamageOffCooldown(ReactionContext context) {
        LivingEntity target = context.targetEntity();
        if (target == null) return;

        long gameTime = target.level().getGameTime();
        Long last = lastDamageTick.get(target.getUUID());
        if (last != null && gameTime - last < DAMAGE_COOLDOWN_TICKS) {
            return;
        }
        lastDamageTick.put(target.getUUID(), gameTime);

        ModDamageSpec spec = ModDamageSpec.transformative(
                ModReactionTypes.SUPERCONDUCT.get(), ModElements.CYRO.get(), AttackType.SPECIAL);
        ModDamageSource source = ModDamageSource.from(spec, context.attackerEntity());
        if (target.level() instanceof ServerLevel serverLevel) {
            target.hurt(source, 0f);
        }
    }
}
