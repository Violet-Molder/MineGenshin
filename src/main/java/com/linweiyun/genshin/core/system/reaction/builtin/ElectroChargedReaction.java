package com.linweiyun.genshin.core.system.reaction.builtin;

import com.linweiyun.elementlib.core.attachment.StatusContainer;
import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.elementlib.core.status.StatusInstance;
import com.linweiyun.elementlib.core.system.about.ElementalAttachmentInstance;
import com.linweiyun.elementlib.core.system.reaction.ElementalReaction;
import com.linweiyun.elementlib.core.system.reaction.ReactionContext;
import com.linweiyun.elementlib.core.system.reaction.ReactionResult;
import com.linweiyun.elementlib.core.system.reaction.ElectroChargedTickState;
import com.linweiyun.genshin.core.system.reaction.ReactionPriorityCalculator;
import com.linweiyun.elementlib.api.ElementalReactionType;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import org.slf4j.Logger;

public class ElectroChargedReaction extends ElementalReaction {
    public static final Logger LOGGER = ModLog.getLogger(LogGroup.ELEMENT);
    public static final float CONSUME_PER_TRIGGER = 0.4f;

    public ElectroChargedReaction(java.util.function.Supplier<ElementalReactionType> type,
                                  String elementAId, String elementBId,
                                  float ratioA, float ratioB, int basePriority) {
        super(type, elementAId, elementBId, ratioA, ratioB, basePriority);
    }

    @Override
    public boolean isBlocked(ReactionContext context) {
        return ReactionPriorityCalculator.hasFrozen(context.targetContainer())
                || ReactionPriorityCalculator.hasColumbinaInParty(context);
    }

    @Override
    public ReactionResult execute(ReactionContext ctx) {
        ElementalAttachmentInstance hydroInst = findElement(ctx.targetContainer(), ModElements.HYDRO.get());
        ElementalAttachmentInstance electroInst = findElement(ctx.targetContainer(), ModElements.ELECTRO.get());

        if (hydroInst == null || electroInst == null) {
            return ReactionResult.builder(type()).build();
        }

        float hydroBefore = hydroInst.getUnit();
        float electroBefore = electroInst.getUnit();

        if (hydroBefore <= 0 || electroBefore <= 0) {
            return ReactionResult.builder(type()).build();
        }

        float consumedHydro = Math.min(CONSUME_PER_TRIGGER, hydroBefore);
        float consumedElectro = Math.min(CONSUME_PER_TRIGGER, electroBefore);

        hydroInst.consume(consumedHydro);
        electroInst.consume(consumedElectro);

        ElectroChargedTickState state = ctx.targetContainer().getElectroChargedTickState();
        state.setContainer(ctx.targetContainer());
        state.onActiveTrigger(ctx.attackerEntity(), ctx.targetEntity());

        return ReactionResult.builder(type())
                .reacted()
                .consumedAttacker(ctx.attackerUnit())
                .consumedDefender(0)
                .build();
    }

    public static ElementalAttachmentInstance findElement(StatusContainer container, GenshinElement element) {
        for (StatusInstance inst : container.getAll()) {
            if (inst.isFinished()) continue;
            if (!(inst instanceof ElementalAttachmentInstance ea)) continue;
            if (ea.getElement() == element) return ea;
        }
        return null;
    }
}
