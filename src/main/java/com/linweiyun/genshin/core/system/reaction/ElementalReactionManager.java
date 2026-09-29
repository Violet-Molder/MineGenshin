package com.linweiyun.genshin.core.system.reaction;

import com.linweiyun.genshin.core.attachment.StatusContainer;
import com.linweiyun.genshin.core.element.GenshinElement;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.status.StatusInstance;
import com.linweiyun.genshin.core.system.about.ElementalAttachmentHelper;
import com.linweiyun.genshin.core.system.about.ElementalAttachmentInstance;
import com.linweiyun.genshin.core.system.about.host.ElementalHost;
import com.linweiyun.genshin.core.system.combat.damage.DamageIndicatorFactory;
import com.linweiyun.genshin.core.system.registry.ModRegistries;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 元素反应管理器 —— 单例（用静态方法）
 *
 * 核心流程（附着入口调用 {@link #tryReactFor(ElementalHost, ReactionContext)}）：
 *   1. 拿到目标身上所有先手元素
 *   2. 拿到本次后手附着的元素和量
 *   3. 收集所有"后手元素 + 某个先手元素"能触发的反应
 *   4. 按默认优先级排序（优先级小的先）
 *   5. 依次执行反应，每一轮消耗后检查后手是否还有剩余
 *   6. 反应飘字由本管理器内部处理（不依赖伤害管线）
 */
public class ElementalReactionManager {

    public static final Logger LOGGER = ModLog.getLogger(LogGroup.ELEMENT);
    // 默认优先级顺序表只有一份，在 ReactionPriorityCalculator 里。

    public static boolean canElementReact(GenshinElement attackerElement,
                                          StatusContainer container) {
        return canElementReact(attackerElement, container, null);
    }

    /**
     * 同 {@link #canElementReact(GenshinElement, StatusContainer)}，但把宿主的第二段筛查也算进去。
     *
     * <p>瞬发元素（风/岩）只为「触发一次反应」而来 —— 宿主拒绝了它全部候选反应时，
     * 这一次附着就不该发生（也就不会在目标身上留下一个永远不会反应的瞬发元素）。
     *
     * @param host 目标宿主；{@code null} 表示没有宿主信息，视为允许全部反应
     */
    public static boolean canElementReact(GenshinElement attackerElement,
                                          StatusContainer container,
                                          ElementalHost host) {
        GenshinElement attackerMain = attackerElement.getMainElement();
        if (attackerMain == null) return false;

        for (StatusInstance inst : container.getAll()) {
            if (inst.isFinished()) continue;
            if (!(inst instanceof ElementalAttachmentInstance ea)) continue;
            if (ea.getElement().isInstant()) continue;
            if (ea.getElement() == ModElements.FYSIKOS.get()) continue;

            GenshinElement defenderMain = ea.getElement().getMainElement();
            if (defenderMain == null) continue;

            for (ElementalReaction reaction : ModRegistries.ELEMENTAL_REACTIONS_REGISTRY) {
                if (!reaction.canMatch(attackerMain, defenderMain)) continue;
                if (!acceptsReaction(host, attackerElement, ea.getElement(), reaction)) continue;
                return true;
            }
        }
        return false;
    }

    // ==================== 反应入口 ====================

    /**
     * <b>宿主感知的反应入口</b> —— 附着入口内部调用它，所以任何来源（攻击、环境、自身、
     * 反应内二次写入）触发的反应都走这里，飘字出口按宿主决定：
     * 生物发在目标头上、方块发在方块位置、角色宿主（没有实体也没有坐标）不发字。
     */
    public static ReactionResult tryReactFor(ElementalHost host, ReactionContext context) {
        return runReactions(context, reactionType -> {
            if (host == null) {
                return;
            }
            if (host.level() != null && host.blockPos() != null) {
                DamageIndicatorFactory.reactionAtBlock(host.level(), host.blockPos(), reactionType);
                return;
            }
            // 角色宿主没有实体，但会给出「角色在世界上对应谁」（出战角色的拥有者）——
            // 环境附着触发的蒸发 / 冻结就是靠这一条才有字可出。
            var anchor = host.indicatorAnchor();
            if (anchor != null) {
                DamageIndicatorFactory.reaction(anchor, reactionType);
            }
        });
    }

    // ==================== 内部 ====================

    /** 反应飘字出口 —— 实体端 / 方块端的唯一差异就这一处。 */
    @FunctionalInterface
    private interface ReactionIndicator {
        void show(ElementalReactionType reactionType);
    }

    /**
     * 反应统一执行体 —— 实体端与方块端共用。
     *
     * <p>流程：收集先手元素 → 配出候选反应并按优先级排序 → 依次执行并扣减后手量 →
     * 后手残留处理 → 逐个发飘字（出口由 {@link ReactionIndicator} 决定）。
     */
    private static ReactionResult runReactions(ReactionContext context, ReactionIndicator indicator) {
        float remainingAttackerQty = context.attackerUnit();
        List<ElementalAttachmentInstance> defenders = collectDefenders(context);
        if (defenders.isEmpty()) {
            return ReactionResult.builder(null).build();
        }

        List<Candidate> candidates = buildCandidates(context, defenders);

        ReactionResult firstAmplified = null;
        boolean anyReactionOccurred = false;
        List<ElementalReactionType> reactionTypes = new ArrayList<>();

        for (Candidate cand : candidates) {
            if (remainingAttackerQty <= 0f) break;
            if (cand.defender.isFinished()) continue;

            ReactionContext roundContext = new ReactionContext(
                    context.attackerElement(),
                    remainingAttackerQty,
                    context.attackerSource(),
                    context.attackerProfile(),
                    context.damageSpec(),
                    context.attackerEntity(),
                    context.targetContainer(),
                    context.targetEntity(),
                    context.targetHost()
            );

            ReactionResult result = cand.reaction.execute(roundContext);

            // 反应实现必须返回结果；null 当作「没发生」，不再让调用方崩在 isReacted() 上。
            if (result == null || !result.isReacted()) continue;
            // 反应成立的宿主侧效果（例：火把冰烧成水）—— 由反应自己决定，不写进方块迁移规则里
            try {
                cand.reaction.applyHostEffect(roundContext);
            } catch (Throwable t) {
                LOGGER.error(" [Reaction] 宿主效果执行失败 reaction={}", cand.reaction.getReactionType(), t);
            }
            anyReactionOccurred = true;
            remainingAttackerQty -= result.getConsumedAttacker();
            // 反应自己可以声明「这次不出飘字」（例：方块上的水结冰/冰化水）
            if (cand.reaction.showsIndicator(roundContext)) {
                reactionTypes.add(result.getReactionType());
            }
            if (firstAmplified == null && result.isAmplified()) {
                firstAmplified = result;
            }
        }

        if (anyReactionOccurred) {
            applyAttackerResidual(context, remainingAttackerQty);
            for (ElementalReactionType rt : reactionTypes) {
                if (shouldShowIndicator(rt)) indicator.show(rt);
            }
        }

        return firstAmplified != null ? firstAmplified :
                ReactionResult.builder(null).build();
    }

    /** 反应飘字默认都要发；月感电与星烁分支由各自的伤害链自己出字，这里跳过。 */
    private static boolean shouldShowIndicator(ElementalReactionType reactionType) {
        return reactionType != null
                && reactionType != ElementalReactionType.LUNAR_CHARGED
                && !StellarGlimmerBranch.isStellarGlimmer(reactionType);
    }

    private static List<ElementalAttachmentInstance> collectDefenders(ReactionContext context) {
        List<ElementalAttachmentInstance> result = new ArrayList<>();
        GenshinElement attackerMain = context.attackerElement().getMainElement();

        for (StatusInstance inst : context.targetContainer().getAll()) {
            if (inst.isFinished()) continue;
            if (!(inst instanceof ElementalAttachmentInstance ea)) continue;
            GenshinElement defMain = ea.getElement().getMainElement();
            if (defMain == attackerMain) continue;
            if (ea.getElement() == ModElements.FYSIKOS.get()) continue;
            if (ea.getElement().isInstant()) continue;
            result.add(ea);
        }
        return result;
    }

    private static List<Candidate> buildCandidates(ReactionContext context,
                                                   List<ElementalAttachmentInstance> defenders) {
        List<Candidate> candidates = new ArrayList<>();
        for (ElementalAttachmentInstance defender : defenders) {
            GenshinElement defenderMain = defender.getElement().getMainElement();
            GenshinElement attackerMain = context.attackerElement().getMainElement();
            for (ElementalReaction reaction : ModRegistries.ELEMENTAL_REACTIONS_REGISTRY) {
                if (!reaction.canMatch(attackerMain, defenderMain)) continue;
                // 第二段筛查：宿主收不收这个反应。
                // 被拒绝 = 这一次不反应、先手元素留在身上 → 冰水共存（而不是生成冻元素）。
                if (!acceptsReaction(context.targetHost(), context.attackerElement(),
                        defender.getElement(), reaction)) {
                    continue;
                }
                if (reaction.isBlocked(context)) continue;
                int priority = reaction.getBasePriority();
                if (priority < 0) {
                    priority = ReactionPriorityCalculator.computeFor(defender.getElement());
                }
                candidates.add(new Candidate(reaction, defender, priority));
            }
        }
        candidates.sort(Comparator.comparingInt(c -> c.priority));
        return candidates;
    }

    /**
     * 问宿主「这个反应能不能发生在你身上」。
     *
     * <p>调用链：宿主（{@link ElementalHost#acceptsReaction}）→ 生物宿主委托给实体自己实现的
     * {@code ElementalAttachable.onReactElement}（默认 true）。所以一个生物只要实现
     * {@code onReactElement} 一处，就能表达「允许挂水、但不接受冻结」。
     *
     * @param attackerElement 后手（本次附着）元素
     * @param defenderElement 先手（宿主身上已有）元素
     */
    private static boolean acceptsReaction(ElementalHost host, GenshinElement attackerElement,
                                           GenshinElement defenderElement,
                                           ElementalReaction reaction) {
        if (host == null) {
            return true;
        }
        return host.acceptsReaction(attackerElement, defenderElement, reaction.getReactionType());
    }

    private static void applyAttackerResidual(ReactionContext context, float remainingQty) {
        if (remainingQty <= 0f) return;
        if (context.attackerFollowsNoResidualRule()) {
            ElementalAttachmentHelper.consume(
                    context.targetContainer(), context.attackerElement(), Float.MAX_VALUE);
        }
    }

    private static class Candidate {
        final ElementalReaction reaction;
        final ElementalAttachmentInstance defender;
        final int priority;

        Candidate(ElementalReaction r, ElementalAttachmentInstance d, int p) {
            this.reaction = r; this.defender = d; this.priority = p;
        }
    }
}
