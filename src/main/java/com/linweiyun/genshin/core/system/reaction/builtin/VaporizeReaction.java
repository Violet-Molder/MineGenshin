package com.linweiyun.genshin.core.system.reaction.builtin;

import com.linweiyun.genshin.config.reaction.ReactionConfig;
import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.elementlib.core.status.StatusInstance;
import com.linweiyun.elementlib.core.system.about.ElementalAttachmentInstance;
import com.linweiyun.elementlib.core.system.reaction.ElementalReaction;
import com.linweiyun.elementlib.core.system.reaction.ReactionContext;
import com.linweiyun.genshin.core.system.reaction.ReactionPriorityCalculator;
import com.linweiyun.elementlib.core.system.reaction.ReactionResult;
import com.linweiyun.elementlib.api.ElementalReactionType;

/**
 * 蒸发反应 —— 增幅反应
 *
 * 消耗比：火:水 = 1:2（火1份，水2份，同时消耗）
 *   先手水后手火 → 倍率 2.0（火蒸发，火克水）
 *   先手火后手水 → 倍率 1.5（水蒸发，水被克）
 *
 * 例外：冻结状态下（目标身上有 FROZEN）禁止蒸发反应
 *
 * TODO: 完整的蒸发倍率取决于火元素精通和水元素精通，当前先硬编码倍率
 */
public class VaporizeReaction extends ElementalReaction {

    private static float getDominantMultiplier() { return (float) ReactionConfig.VAPORIZE.get(); }
    private static float getSubmissiveMultiplier() { return (float) ReactionConfig.VAPORIZE_NEGATIVE.get(); }

    public VaporizeReaction(java.util.function.Supplier<ElementalReactionType> type,
                            String elementAId, String elementBId,
                            float ratioA, float ratioB, int basePriority) {
        super(type, elementAId, elementBId, ratioA, ratioB, basePriority);
    }

    @Override
    public boolean isBlocked(ReactionContext context) {
        // 冻结状态下禁止蒸发
        return ReactionPriorityCalculator.hasFrozen(context.targetContainer());
    }

    @Override
    public ReactionResult execute(ReactionContext ctx) {
        // 确定先手是 A 还是 B，以及实际对应的 GenshinElement
        GenshinElement elA = getElementA();
        GenshinElement elB = getElementB();
        GenshinElement attackerMain = ctx.attackerElement().getMainElement();
        boolean attackerIsA = (attackerMain == elA);

        // 找到目标身上对应的先手实例
        ElementalAttachmentInstance defInstance = findDefenderInstance(ctx,
                attackerIsA ? elB : elA);

        if (defInstance == null || defInstance.isFinished()) {
            return ReactionResult.builder(type()).build();
        }

        float attackerQty = ctx.attackerUnit();
        float defenderQty = defInstance.getUnit();

        // 按比例同时消耗
        float consumedA, consumedB;
        float[] consumed;
        if (attackerIsA) {
            // 后手是 A，先手是 B
            consumed = calculateConsumption(attackerQty, defenderQty);
        } else {
            // 后手是 B，先手是 A
            consumed = calculateConsumption(defenderQty, attackerQty);
            // consumed[0] 是 A 的消耗，consumed[1] 是 B 的消耗
        }
        consumedA = consumed[0];
        consumedB = consumed[1];

        consumeElementUnit(ctx.targetContainer(), elB, consumedB);
        consumeElementUnit(ctx.targetContainer(), elA, consumedA);

        // 判断克制关系 → 倍率
        // elementA 是克制方（消耗少的）
        // attackerIsA = true → 后手是克制方 → 倍率 2.0
        // attackerIsA = false → 后手是被克制方 → 倍率 1.5
        boolean dominant = attackerIsA;
        float multiplier = dominant ? getDominantMultiplier() : getSubmissiveMultiplier();

        float consumedAttacker = attackerIsA ? consumedA : consumedB;

        return ReactionResult.builder(type())
                .reacted()
                .consumedAttacker(consumedAttacker)
                .consumedDefender(attackerIsA ? consumedB : consumedA)
                .amplified(multiplier)
                .build();
    }

    private ElementalAttachmentInstance findDefenderInstance(ReactionContext ctx,
                                                             GenshinElement targetMain) {
        // 遍历容器，找到主元素匹配的先手实例（优先找主元素精确匹配的，再找类元素）
        ElementalAttachmentInstance subElementMatch = null;
        for (StatusInstance inst : ctx.targetContainer().getAll()) {
            if (inst.isFinished()) continue;
            if (!(inst instanceof ElementalAttachmentInstance ea)) continue;
            GenshinElement eaElement = ea.getElement();
            if (eaElement.getMainElement() != targetMain) continue;
            // 精确匹配优先
            if (eaElement == targetMain) return ea;
            if (subElementMatch == null) subElementMatch = ea;
        }
        return subElementMatch;
    }

}