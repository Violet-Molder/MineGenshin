package com.linweiyun.genshin.core.character.talent;
import com.linweiyun.genshin.core.system.combat.action.data.ComboData;
import com.linweiyun.genshin.core.system.combat.action.data.SkillData;
import com.linweiyun.genshin.core.system.combat.action.data.BurstData;
import com.linweiyun.genshin.core.system.combat.action.data.DodgeData;

import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.system.combat.action.ActionDefinition;
import com.linweiyun.genshin.core.system.combat.action.ActionKind;
import com.linweiyun.genshin.core.system.combat.action.ActionSet;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterActionData;
import com.linweiyun.genshin.core.system.combat.action.data.ActionStep;
import net.minecraft.world.entity.player.Player;

/**
 * 技能基类（普攻 / 重击 / 战技 / 爆发 / 闪避）—— 动作数据与伤害结算。
 * <p>
 * <b>这个类就是重命名前的 {@code core.character.talent.TalentBase}</b>：
 * 成员一个没少（{@code onCastStart} / {@code buildActionSet} / {@code buildDefaultActionSet} /
 * {@code getMaxCombo} / {@code getChargeTicks} / {@code attack} / {@code chargeAttack} /
 * {@code elementalSkill} / {@code elementalBurst} / {@code dodge} / {@code SetBuilder}），
 * 只换了类名。
 *
 * <h2>三个协作者怎么分（本仓库的统一布局）</h2>
 * <pre>
 *   core/character/talent/SkillBase.java          XxxSkill          招式：动作数据 + 伤害结算
 *   core/character/talent/TalentBase.java         XxxTalent         天赋：突破天赋 / 被动
 *   core/character/talent/ConstellationBase.java  XxxConstellation  命座：C1..C6
 *   core/character/{weapon}/{name}/Xxx.java       主类              只做中转/调度
 * </pre>
 * 三个基类同包（都在 {@code core.character.talent} 下）；每个角色的三个协作者文件放在角色自己的包
 * （{@code core.character.{weapon}.{name}}）里，和主类并排。
 *
 * <p>时序由 {@link ActionStep} 提供（唯一来源），不再自行定义前摇/执行/后摇。
 * 子类只需覆盖回调方法和 maxCombo。
 *
 * <h2>「这一招能不能放」写在别处</h2>
 * 出手的前置判断（能量、CD、姿态…）不在这里，而在
 * {@link PGCharacter#canCast(net.minecraft.world.entity.player.Player, ActionKind, int)} ——
 * 因为<b>客户端也要问一次</b>（不通过就不播动画）。
 * 所以凡是<b>双端都需要的条件</b>都放角色那边（只读 {@code PGCharacterData} 那类同步数据）；
 * 技能只负责「放出来之后干什么」。
 */
public class SkillBase {

    public int getMaxCombo() { return 1; }

    public int getChargeTicks() { return 20; }

    /**
     * 这个角色的重击是不是<b>持续型</b>（按住进入状态、松手/到时结束）。
     *
     * <p>默认 {@code false} = 触发型：蓄力到阈值放一招，打完为止。
     * 大剑那一类走 {@code ClaymoreSkill} 的实现，返回 {@code true}；
     * 客户端 {@code ResourceDrivenActionHandler.tickCharge} 据此分两条路走。
     */
    public boolean isSustainedChargedAttack() { return false; }

    /**
     * 持续型重击的最高持续时间（刻）；不是持续型时返回 0。
     *
     * <p>客户端拿它当持续状态的时长（到时自动结束），服务端拿它当动作总时长。
     * 见 {@link #isSustainedChargedAttack()}。
     */
    public int getChargedAttackMaxTicks() { return 0; }

    // ==================== 回调（子类覆盖实现具体伤害/效果逻辑） ====================

    public void attack(Player player, PGCharacter character, int comboStage) {}
    public void chargeAttack(Player player, PGCharacter character) {}
    public void elementalSkill(Player player, PGCharacter character, int skillTime) {}
    public void elementalBurst(Player player, PGCharacter character) {}
    public void dodge(Player player, PGCharacter character) {}

    /**
     * 招式<b>触发那一刻</b>（动作刚开始，早于任何伤害点）调用一次。
     *
     * <h2>和 {@link #attack}/{@link #elementalSkill} 的分工</h2>
     * 那几个回调跑在 {@code hits[].delay} 的伤害点上 —— 它们是「效果在第几帧出」。
     * 这个方法跑在 tick 0，是「这一招<b>已经放出来了</b>」。
     *
     * <p>凡是<b>触发即生效</b>的东西都放这里：换姿态、开模式、扣子弹、进入某个状态。
     * 放在伤害点上的话，前摇/准备阶段被打断就会出现
     * 「CD 已经在转、资源已经扣了，但模式没进去、效果没出来」。
     *
     * <p>服务端触发（客户端那一帧只是开始播动画 + 发请求）。
     *
     * @param kind 这一招是什么（普攻/重击/战技/大招/闪避）
     */
    public void onCastStart(Player player, PGCharacter character, ActionKind kind) {}

    /**
     * 每刻调用一次（<b>仅服务端</b>，由角色主类的 {@code tick} 转发）。
     *
     * <p>给「招式自己会持续跑」的技能用：挥出那一下只是开始，之后还要周期性结算
     * （例：沃雅妮莎的「遥久之歌」每 3 秒打一次、每 1.5 秒回一次血）。
     * 默认空实现。
     *
     * <p>为什么由主类转发而不是 {@code PGCharacter.tick} 统一调：
     * 触发条件属于招式自己的状态（例：歌还在唱才跑），写在主类的 {@code tick} 里
     * 顺序与条件才和重构前一致。
     */
    public void tick(Player player, PGCharacter character) {}

    // ==================== ActionSet 构建 ====================

    public ActionSet buildActionSet(PGCharacter character, String stateKey) {
        return buildDefaultActionSet(character);
    }

    protected ActionSet buildDefaultActionSet(PGCharacter character) {
        int maxCombo = getMaxCombo();
        CharacterActionData actionData = character.getActionData();

        // 没有专属 ACTION_DATA 的角色（大部分角色现在还只有壳）走一套通用兜底，
        // 否则 buildActionSet 会返回空集，普攻/战技/大招全部静默失效。
        if (actionData == null) {
            actionData = CharacterActionData.fallback(maxCombo);
        }

        SetBuilder sb = setBuilder();
        if (actionData != null) {
            ComboData combo = actionData.combo();
            if (combo != null) {
                // 段数 0 = 「不排连段、只出一下」：按一下仍然先触发一次普攻
                //（林薇云的单手剑形态就是这个口径），所以这里保底 1 段。
                int steps = Math.max(1, Math.min(maxCombo, combo.maxCombo()));
                for (int stage = 1; stage <= steps; stage++) {
                    ActionStep step = combo.getStep(stage);
                    if (step != null) {
                        final int s = stage;
                        sb.addNormalAttack(
                                ActionDefinition.builder(ActionKind.NORMAL_ATTACK)
                                        .comboIndex(s)
                                        .step(step)
                                        .onCastStart(ctx -> onCastStart(ctx.player, ctx.character,
                                                ActionKind.NORMAL_ATTACK))
                                        .onActiveStart(ctx -> {
                                            attack(ctx.player, ctx.character, s);
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
                                    .onCastStart(ctx -> onCastStart(ctx.player, ctx.character,
                                            ActionKind.ELEMENTAL_SKILL_TAP))
                                    .onActiveStart(ctx -> elementalSkill(ctx.player, ctx.character, 0))
                                    .build()
                    );
                }
                if (skill.hold() != null) {
                    sb.addSkillHold(
                            ActionDefinition.builder(ActionKind.ELEMENTAL_SKILL_HOLD)
                                    .step(skill.hold())
                                    .onCastStart(ctx -> onCastStart(ctx.player, ctx.character,
                                            ActionKind.ELEMENTAL_SKILL_HOLD))
                                    .onActiveStart(ctx -> elementalSkill(ctx.player, ctx.character, 1000))
                                    .build()
                    );
                }
            }

            BurstData burst = actionData.burst();
            if (burst != null && burst.step() != null) {
                sb.addBurst(
                        ActionDefinition.builder(ActionKind.ELEMENTAL_BURST)
                                .step(burst.step())
                                .onCastStart(ctx -> onCastStart(ctx.player, ctx.character,
                                        ActionKind.ELEMENTAL_BURST))
                                .onActiveStart(ctx -> elementalBurst(ctx.player, ctx.character))
                                .build()
                );
            }

            DodgeData dodgeData = actionData.dodge();
            if (dodgeData != null && dodgeData.step() != null) {
                sb.addDodge(
                        ActionDefinition.builder(ActionKind.DODGE)
                                .step(dodgeData.step())
                                .onCastStart(ctx -> onCastStart(ctx.player, ctx.character,
                                        ActionKind.DODGE))
                                .onActiveStart(ctx -> dodge(ctx.player, ctx.character))
                                .build()
                );
            }
        }

        // 兜底：重击用基础步（子类可覆盖 buildActionSet 自定义）
        if (actionData != null && actionData.skill() != null && actionData.skill().tap() != null) {
            sb.addCharged(
                    ActionDefinition.builder(ActionKind.CHARGED_ATTACK)
                            .step(actionData.skill().tap())
                            .onCastStart(ctx -> onCastStart(ctx.player, ctx.character,
                                    ActionKind.CHARGED_ATTACK))
                            .onActiveStart(ctx -> chargeAttack(ctx.player, ctx.character))
                            .build()
            );
        }

        return sb.build();
    }

    // ==================== 构建器 ====================

    protected SetBuilder setBuilder() { return new SetBuilder(null); }
    protected SetBuilder setBuilder(ActionSet parent) { return new SetBuilder(parent); }

    public final class SetBuilder {
        private final ActionSet.Builder inner;

        private SetBuilder(ActionSet parent) {
            this.inner = (parent != null) ? ActionSet.deriveFrom(parent) : ActionSet.builder();
        }

        public SetBuilder addNormalAttack(ActionDefinition def) { inner.addNormalAttack(def); return this; }
        public SetBuilder addCharged(ActionDefinition def)      { inner.chargedAttack(def); return this; }
        public SetBuilder addSkillTap(ActionDefinition def)     { inner.elementalSkillTap(def); return this; }
        public SetBuilder addSkillHold(ActionDefinition def)    { inner.elementalSkillHold(def); return this; }
        public SetBuilder addBurst(ActionDefinition def)        { inner.elementalBurst(def); return this; }
        public SetBuilder addDodge(ActionDefinition def)        { inner.dodge(def); return this; }
        public SetBuilder clearNormalCombo()                    { inner.clearNormalCombo(); return this; }

        public ActionSet build() { return inner.build(); }
    }
}
