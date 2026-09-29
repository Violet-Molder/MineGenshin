package com.linweiyun.genshin.core.system.reaction.builtin;

import com.linweiyun.genshin.config.reaction.ReactionConfig;
import com.linweiyun.genshin.core.element.GenshinElement;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.status.StatusInstance;
import com.linweiyun.genshin.core.system.about.ElementalAttachmentInstance;
import com.linweiyun.genshin.core.system.reaction.ElementalReaction;
import com.linweiyun.genshin.core.system.reaction.ReactionContext;
import com.linweiyun.genshin.core.system.reaction.ReactionResult;
import com.linweiyun.genshin.core.system.reaction.ElementalReactionType;
import com.linweiyun.genshin.core.system.about.block.BlockElementRules;
import com.linweiyun.genshin.core.system.about.block.BlockElementStore;
import com.linweiyun.genshin.core.system.about.host.BlockHost;
import net.minecraft.world.level.block.state.BlockState;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import org.slf4j.Logger;
/**
 * 融化反应 —— 增幅反应
 *
 * 消耗比：火:冰 = 1:2（火1份，冰2份，同时消耗）
 *   先手冰(或冻) 后手火 → 倍率 2.0（火融化，火克冰）
 *   先手火 后手冰(或冻) → 倍率 1.5（冰融化，冰被克）
 *
 * 冰和冻可以共同被消耗（通过 getMainElement 都归并到 CYRO）
 */
public class MeltReaction extends ElementalReaction {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.ELEMENT);

    private static float getDominantMultiplier() { return (float) ReactionConfig.MELT.get(); }
    private static float getSubmissiveMultiplier() { return (float) ReactionConfig.MELT_NEGATIVE.get(); }

    public MeltReaction(ElementalReactionType type,
                        String elementAId, String elementBId,
                        float ratioA, float ratioB, int basePriority) {
        super(type, elementAId, elementBId, ratioA, ratioB, basePriority);
    }

    @Override
    public ReactionResult execute(ReactionContext ctx) {
        GenshinElement elA = getElementA();
        GenshinElement elB = getElementB();
        GenshinElement attackerMain = ctx.attackerElement().getMainElement();
        boolean attackerIsA = (attackerMain == elA);

        // 找到目标身上主元素为 elementB 的所有实例（CYRO 主元素，可能是 CYRO 或 FROZEN）
        GenshinElement defenderTarget = attackerIsA ? elB : elA;
        float totalDefenderQty = sumMainElementQuantity(ctx, defenderTarget);

        if (totalDefenderQty <= 0f) {
            return ReactionResult.builder(reactionType).build();
        }

        float attackerQty = ctx.attackerUnit();

        // 按比例同时消耗
        float consumedA, consumedB;
        if (attackerIsA) {
            float[] consumed = calculateConsumption(attackerQty, totalDefenderQty);
            consumedA = consumed[0]; consumedB = consumed[1];
        } else {
            float[] consumed = calculateConsumption(totalDefenderQty, attackerQty);
            consumedA = consumed[0]; consumedB = consumed[1];
        }

        // 从容器里扣
        consumeElementUnit(ctx.targetContainer(), elB, consumedB);
        consumeElementUnit(ctx.targetContainer(), elA, consumedA);
        // 倍率
        boolean dominant = attackerIsA;
        float multiplier = dominant ? getDominantMultiplier() : getSubmissiveMultiplier();
        float consumedAttacker = attackerIsA ? consumedA : consumedB;

        return ReactionResult.builder(reactionType)
                .reacted()
                .consumedAttacker(consumedAttacker)
                .consumedDefender(attackerIsA ? consumedB : consumedA)
                .amplified(multiplier)
                .build();
    }

    private float sumMainElementQuantity(ReactionContext ctx, GenshinElement mainTarget) {
        float sum = 0f;
        for (StatusInstance inst : ctx.targetContainer().getAll()) {
            if (inst.isFinished()) continue;
            if (!(inst instanceof ElementalAttachmentInstance ea)) continue;
            if (ea.getElement().getMainElement() == mainTarget) {
                sum += ea.getUnit();
            }
        }
        return sum;
    }

    /**
     * 融化对<b>方块</b>的意义：冰被火吃掉 → 这一格变成水。
     *
     * <p>只在「宿主是方块」且「这一格确实是冰族」时生效；生物身上只是消耗附着与算增幅伤害。
     * 这里清掉容器 —— 化成的水是新鲜的水，自带水元素由 {@code BlockSelfAura} 在下次读取时补上。
     */
    @Override
    public void applyHostEffect(ReactionContext context) {
        if (!(context.targetHost() instanceof BlockHost blockHost) || !blockHost.isValid()) {
            return;
        }
        BlockState state = blockHost.state();
        if (!BlockElementRules.isIceFamily(state)) {
            return;
        }
        // 关键：把容器里的「冰/冻」也清掉。调用方（applyElement / 范围扫描）手里握着这同一个
        // 容器对象，之后还会 commit 一次并按容器内容跑状态迁移 —— 留着冻元素的话，
        // 迁移会把刚化开的水又冻回去（表现就是「火打浮冰没反应」）。
        for (StatusInstance inst : new java.util.ArrayList<>(context.targetContainer().getAll())) {
            if (inst instanceof ElementalAttachmentInstance ea
                    && (ea.getElement() == ModElements.CYRO.get()
                    || ea.getElement() == ModElements.FROZEN.get())) {
                context.targetContainer().remove(inst);
            }
        }
        // 化回来要还原成「冻之前的水」：水位从记录里取（流动水冻成的冰化开就还是流动水）；
        // 没有记录（天然冰）则按完整水源处理。
        Integer waterLevel = BlockElementStore.takeWaterLevel(blockHost.level(), blockHost.blockPos());
        BlockElementStore.clear(blockHost.level(), blockHost.blockPos());
        net.minecraft.world.level.block.state.BlockState water =
                net.minecraft.world.level.block.Blocks.WATER.defaultBlockState();
        if (waterLevel != null) {
            water = water.setValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL, waterLevel);
        }
        // flag=2：只把新状态发给客户端，**不触发邻居更新** —— 避免放置瞬间就把周围的水流拉过来重算，
// 让「冻之前是什么水位、化开就还是什么水位」至少在这一刻成立。
        // （原版随后的流体 tick 仍会按它自己的规则重算；要 100% 锁住得改流体规则，见回复说明。）
        blockHost.level().setBlock(blockHost.blockPos(), water, 2);
    }


    /** 方块上的形态变化（水结冰 / 冰化水）不显示文字；生物身上照常。 */
    @Override
    public boolean showsIndicator(ReactionContext context) {
        return !(context.targetHost() instanceof com.linweiyun.genshin.core.system.about.host.BlockHost);
    }
}
