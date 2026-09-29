package com.linweiyun.genshin.core.system.reaction.builtin;

import com.linweiyun.genshin.content.items.weapon.catalyst.HymnTheMaelstrom;
import com.linweiyun.genshin.core.attachment.StatusContainer;
import com.linweiyun.genshin.core.element.GenshinElement;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.status.StatusInstance;
import com.linweiyun.genshin.core.system.about.AttachmentProfile;
import com.linweiyun.genshin.core.system.about.AttachmentSource;
import com.linweiyun.genshin.core.system.about.ElementalAttachmentHelper;
import com.linweiyun.genshin.core.system.about.ElementalAttachmentInstance;
import com.linweiyun.genshin.core.system.reaction.ElementalReaction;
import com.linweiyun.genshin.core.system.reaction.ReactionContext;
import com.linweiyun.genshin.core.system.reaction.ReactionResult;
import com.linweiyun.genshin.core.system.reaction.ElementalReactionType;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

/**
 * 冻结反应 —— 特殊反应（非增幅非聚变）
 *
 * 消耗比：水:冰 = 1:1
 *   1水 + 1冰 同时消耗 → 生成 2 倍消耗量的 FROZEN（冻）元素在目标身上
 *
 * 关键规则：
 *   - 不能与 FROZEN 本身发生冻结反应（已经冻住了不能再冻）
 *   - 冻结反应不是增幅反应，不影响伤害
 *   - 冻结反应产生 FROZEN 元素（通过 ElementalAttachmentHelper.attach 添加）
 *   - 方块端冻结（targetEntity 为 null）：直接向容器添加 FROZEN，不走实体流程
 *
 * TODO: 完整冻结效果（减速、冻结实体等）暂不实现，待后续补充
 * TODO: 冻结藏冰/藏水逻辑 —— 冻结反应发生后还能残留额外的冰或水
 */
public class FreezeReaction extends ElementalReaction {
    public static final Logger LOGGER = ModLog.getLogger(LogGroup.ELEMENT);

    /**
     * 冻结反应的生成物倍率
     * 消耗掉的(水+冰)总量 × 2 = 生成的 FROZEN 元素量
     * 例：消耗 0.8水 + 0.8冰 = 1.6总量 → 生成 3.2 FROZEN
     */
    private static final float FROZEN_MULTIPLIER = 2.0f;

    /**
     * 注册时的参数约定（遵循 ElementalReaction 基类规范）：
     *   elementA = HYDRO（水，消耗 ratioA=1 份）
     *   elementB = CYRO （冰，消耗 ratioB=1 份）
     *   消耗比 ratioA:ratioB = 1:1 → 同时消耗，无克制方
     */
    public FreezeReaction(ElementalReactionType type,
                          String elementAId, String elementBId,
                          float ratioA, float ratioB, int basePriority) {
        super(type, elementAId, elementBId, ratioA, ratioB, basePriority);
    }

    /**
     * canMatch —— 反应选择阶段的粗筛
     *
     * 覆盖基类默认：精确元素前置过滤，冻元素不参与冻结反应的任何一侧配对
     * 基类默认是主元素归并（FROZEN→CYRO 会被算进冰侧），这里在归并之前就把 FROZEN 拦掉
     */
    @Override
    public boolean canMatch(GenshinElement attackerElement, GenshinElement defenderElement) {
        if (attackerElement == ModElements.FROZEN.get()) return false;
        if (defenderElement == ModElements.FROZEN.get()) return false;
        return super.canMatch(attackerElement, defenderElement);
    }

    /**
     * canConsume —— 执行消耗阶段的细筛（覆盖基类）
     *
     * 基类默认是 MAIN 策略（主元素归并匹配），会把 FROZEN（主元素CYRO）算进冰侧一起消耗
     * 这里覆盖成 EXACT 语义：排除 FROZEN 实例，只让精确的 CYRO / HYDRO 参与消耗
     */
    @Override
    public boolean canConsume(ElementalAttachmentInstance instance, GenshinElement slotElement) {
        if (instance.getElement() == ModElements.FROZEN.get()) return false;
        return super.canConsume(instance, slotElement);
    }

    /**
     * isBlocked —— 判断这个反应是否应该被禁止
     */
    @Override
    public boolean isBlocked(ReactionContext context) {
        if (context.attackerElement() == ModElements.FROZEN.get()) return true;
        return false;
    }

    /**
     * execute —— 冻结反应核心执行逻辑
     *
     * 整体流程分 4 步：
     *   ① 定向 —— 判断后手对应注册参数的 A 还是 B，确定先手要找哪种元素
     *   ② 算量 —— 遍历目标容器算出先手元素的总量（调用基类 sumConsumable，内部走 canConsume 过滤），按消耗比算双方各扣多少
     *   ③ 扣减 —— 后手元素从全局消耗，先手元素调用基类 consumeFromContainer（内部走 canConsume 过滤）
     *   ④ 生成 —— (消耗总量 × 2) 份 FROZEN 附加到目标身上
     *
     *   方块端执行（targetEntity 为 null）：跳过实体附着和武器被动，直接向容器添加 FROZEN。
     *
     * ===== 变量名说明 =====
     *   attacker     = 后手（本次附着的那一方，来自 attacker 攻击）
     *   defender     = 先手（目标身上已有的那一方，来自 defender 身上的附着）
     *   elementA/B   = 注册时声明的两个元素，A 和 B 只是"槽位"，不代表后手或先手
     *   attackerIsA  = 判断后手是不是"注册时放在 A 槽位的那个元素"（布尔标志，后续所有分支的根基）
     *
     * ===== 为什么要分 A/B 槽位？ =====
     *   ElementalReaction 基类要求每个反应在注册时固定消耗比 ratioA:ratioB
     *   但实际触发时，后手可能是 A 也可能是 B（例：水冻冰 / 冰冻水）
     *   所以 attackerIsA 这个布尔变量就是用来把"随机先后手"映射回"固定 A/B 槽位"
     */
    @Override
    public ReactionResult execute(ReactionContext ctx) {
        GenshinElement elA = getElementA();
        GenshinElement elB = getElementB();

        GenshinElement attackerMain = ctx.attackerElement().getMainElement();

        boolean attackerIsA = (attackerMain == elA);

        GenshinElement defenderTarget = attackerIsA ? elB : elA;

        float totalDefenderUnit = sumConsumable(ctx.targetContainer(), defenderTarget);

        if (totalDefenderUnit <= 0f) {
            return ReactionResult.builder(reactionType).build();
        }

        float attackerQty = ctx.attackerUnit();

        float consumedA, consumedB;

        if (attackerIsA) {
            float[] consumed = calculateConsumption(attackerQty, totalDefenderUnit);
            consumedA = consumed[0]; consumedB = consumed[1];
        } else {
            float[] consumed = calculateConsumption(totalDefenderUnit, attackerQty);
            consumedA = consumed[0]; consumedB = consumed[1];
        }

        float totalConsumed = consumedA + consumedB;

        float consumedAttacker = attackerIsA ? consumedA : consumedB;

        java.util.Set<String> cyroKeysBefore = new java.util.LinkedHashSet<>();
        java.util.Set<String> hydroKeysBefore = new java.util.LinkedHashSet<>();
        for (var inst : ctx.targetContainer().getAll()) {
            if (inst.isFinished()) continue;
            if (!(inst instanceof ElementalAttachmentInstance ea)) continue;
            GenshinElement el = ea.getElement();
            String key = ea.getSourceCharacterKey();
            if (key == null || key.isEmpty()) continue;
            if (el == ModElements.CYRO.get()) {
                cyroKeysBefore.add(key);
            } else if (el == ModElements.HYDRO.get()) {
                hydroKeysBefore.add(key);
            } else if (el == ModElements.FROZEN.get()) {
                cyroKeysBefore.addAll(ea.getFrozenCyroSourceKeys());
                hydroKeysBefore.addAll(ea.getFrozenHydroSourceKeys());
            }
        }

        consumeElementUnit(ctx.targetContainer(), elB, consumedB);
        consumeElementUnit(ctx.targetContainer(), elA, consumedA);

        float frozenBefore = 0f;
        for (StatusInstance inst : ctx.targetContainer().getAll()) {
            if (inst.isFinished()) continue;
            if (!(inst instanceof ElementalAttachmentInstance ea)) continue;
            if (ea.getElement() == ModElements.FROZEN.get()) {
                frozenBefore += ea.getUnit();
            }
        }

        if (totalConsumed > 0f) {
            float frozenQty = totalConsumed * FROZEN_MULTIPLIER;

            // 生成物 FROZEN 也是「附着」，所以同样走统一入口：宿主筛查照问、覆盖规则照走。
            // 用 attachInternal —— 反应内部产生的附着不再回头触发反应（否则会递归）。
            AttachmentProfile frozenProfile = new AttachmentProfile(frozenQty, 1.0f, 0.0f, 999.0f);
            com.linweiyun.genshin.core.system.about.host.ElementalHost frozenHost = ctx.targetHost();
            if (frozenHost == null && ctx.targetEntity() != null) {
                frozenHost = com.linweiyun.genshin.core.system.about.host.EntityHost.of(ctx.targetEntity());
            }
            if (frozenHost != null) {
                ElementalAttachmentHelper.attachInternal(
                        frozenHost, ModElements.FROZEN.get(), AttachmentSource.SPECIAL, frozenProfile);
            } else {
                // 没有宿主信息（理论上不该发生）：退回直接写容器，至少不让冻结丢效果
                ElementalAttachmentInstance frozenInst = new ElementalAttachmentInstance(
                        ModElements.FROZEN.get(), AttachmentSource.SPECIAL, frozenProfile, frozenQty);
                ctx.targetContainer().add(frozenInst);
            }

            for (var inst : ctx.targetContainer().getAll()) {
                if (inst.isFinished()) continue;
                if (!(inst instanceof ElementalAttachmentInstance ea)) continue;
                if (ea.getElement() != ModElements.FROZEN.get()) continue;
                for (String k : cyroKeysBefore) ea.addFrozenCyroSource(k);
                for (String k : hydroKeysBefore) ea.addFrozenHydroSource(k);
            }

            StatusContainer container = ctx.targetContainer();
            if (container.getFrozenDecayState() != null) {
                container.getFrozenDecayState().activate();
            }

            // 武器被动（漩流颂歌）：仅实体端
            if (ctx.targetEntity() != null
                    && ctx.targetEntity().level() instanceof ServerLevel serverLevel) {
                HymnTheMaelstrom.markReactionTriggers(
                        serverLevel, ctx.attackerEntity(),
                        ctx.targetEntity().getX(), ctx.targetEntity().getY(), ctx.targetEntity().getZ());
            }
        }

        float defenderConsumedQty = attackerIsA ? consumedB : consumedA;
        GenshinElement defenderElementConsumed = attackerIsA ? elB : elA;

        return ReactionResult.builder(reactionType)
                .reacted()
                .consumedAttacker(consumedAttacker)
                .consumedDefender(defenderConsumedQty)
                .build();
    }

    /** 方块上的形态变化（水结冰 / 冰化水）不显示文字；生物身上照常。 */
    @Override
    public boolean showsIndicator(ReactionContext context) {
        return !(context.targetHost() instanceof com.linweiyun.genshin.core.system.about.host.BlockHost);
    }
}
