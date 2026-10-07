package com.linweiyun.genshin.core.system.combat.action;

import com.linweiyun.genshin.core.system.combat.action.data.ActionStep;
import com.linweiyun.genshin.core.system.combat.action.data.Hit;
import com.linweiyun.elementlib.api.ElibAttackAction;
import com.linweiyun.elementlib.api.ElibAttackTrigger;
import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.elementlib.core.system.about.AttachmentProfile;
import com.linweiyun.elementlib.core.system.about.AttachmentSource;
import com.linweiyun.elementlib.core.system.attack.ElibAttackPipeline;
import com.linweiyun.genshin.core.system.combat.attack.ElementalAttackSweep;
import net.minecraft.server.level.ServerLevel;
import com.linweiyun.genshin.core.system.poise.HitPoise;
import com.linweiyun.genshin.core.system.poise.HitPoiseDamage;
import com.linweiyun.genshin.core.system.poise.HitImpact;
import com.linweiyun.genshin.core.system.poise.WeaponPoiseTable;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.genshin.core.system.poise.impact.ImpactLevel;
import com.linweiyun.genshin.core.system.performance.HotPathLog;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import lombok.Getter;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.Consumer;

/**
 * 动作运行时状态机。
 * <p>
 * 不再使用 PRECAST/ACTIVE/POSTCAST 阶段模型。
 * 改为基于 {@link ActionStep} 的 tick 计数 + hitDelay 驱动：
 * <ul>
 *     <li>每 tick 递增计数器</li>
 *     <li>在 hitDelay 指定的 tick 触发 onActiveStart 回调</li>
 *     <li>达到 totalDuration 则完成</li>
 * </ul>
 *
 * <h2>三个窗口（见 {@link ActionStep#protectDuration}）</h2>
 * <pre>
 * 0            prepareTicks          protectDuration      duration
 * ├─ 准备阶段 ────┼──── 执行期 ────────────┼──── 后摇 ────┤
 *    可打断          不可打断               可取消
 * </pre>
 * {@code onCastStart} 在构造时（tick 0）触发一次 —— 它在准备阶段之前，
 * 也就是「触发即生效」的那些逻辑该待的地方。
 */
public class ActionState {
    private static final Logger LOGGER = ModLog.getLogger(LogGroup.COMBAT);

    @Getter private final ActionDefinition definition;
    @Getter private final ActionContext context;

    private final int totalDuration;
    private final int protectDuration;
    /** 准备阶段长度：这段时间内还能被打断。 */
    private final int prepareTicks;
    private final int[] hitDelays;
    private final BitSet hitsFired;

    /** 这个动作没有配 hits：{@code onActiveStart} 只在 tick 0 触发一次。 */
    private final boolean firesWithoutHits;

    private int tickCount;
    @Getter private boolean finished;

    public ActionState(ActionDefinition definition, ActionContext context) {
        this.definition = definition;
        this.context = context;
        this.totalDuration = definition.totalDuration();
        this.protectDuration = definition.protectDuration();
        this.prepareTicks = Math.max(0, definition.prepareTicks());

        ActionStep step = definition.step;
        if (step != null && step.hits != null) {
            List<Integer> delays = new ArrayList<>();
            for (Hit hit : step.hits) {
                delays.add(hit.delay);
            }
            this.hitDelays = delays.stream().mapToInt(Integer::intValue).toArray();
        } else {
            this.hitDelays = new int[0];
        }

        // 没有配 hits 的动作（例如闪避）没有触发点，补一个 tick 0 —— 否则 onActiveStart 永远不会被调用
        this.firesWithoutHits = this.hitDelays.length == 0;
        this.hitsFired = new BitSet(Math.max(1, hitDelays.length));

        this.tickCount = 0;
        this.finished = false;


        // ⭐ 触发那一刻（tick 0）—— 「触发即生效」钩子。
        //    必须在 checkHits() 之前：它是「这一招已经放出来了」，不是「伤害在第几帧」。
        //    换姿态 / 开模式 / 扣资源放这里，才不会出现「CD 转了但前摇被打断、模式没进去」。
        fire(definition.getOnCastStart());

        // tick 0 触发伤害（delay=0 的 hit）
        checkHits();
    }

    public void tick() {
        if (finished) return;
        tickCount++;
        context.tickTotal();

        // 检查本 tick 是否有命中
        checkHits();

        // 时长到期 → 完成
        if (tickCount >= totalDuration) {
            finish();
            return;
        }

        // 执行期内不接新动作，由 ActionManager 检查
    }

    private void checkHits() {
        // 没有 hits 的动作：只在 tick 0 触发一次
        if (firesWithoutHits) {
            if (tickCount == 0 && !hitsFired.get(0)) {
                hitsFired.set(0);
                fireDamagePoint(-1);
            }
            return;
        }

        for (int i = 0; i < hitDelays.length; i++) {
            if (!hitsFired.get(i) && tickCount >= hitDelays[i]) {
                hitsFired.set(i);
                fireDamagePoint(i);
            }
        }
    }

    /**
     * 一个<b>伤害点</b>：先把这一招的元素留给范围内的「可附着方块」，再跑天赋自己的伤害结算。
     *
     * <p>放在这里（而不是 {@code ActionManager} 的动作启动处）是因为它才是
     * 「hits[] 里那一下真正发生」的时刻：多段招式每段各附着一次，前摇被打断也不会提前留下元素。
     * 实体那一侧不在这里做 —— 它由 {@code hurtServer → DirectDamagePipeline} 负责，
     * 两边最终都落进 {@code ElementalAttachmentHelper} 这一个附着入口。
     *
     * <p>{@code ElementalAttackSweep} 内部自带门禁（仅服务端 + 仅原神模式 + 必须有元素），
     * 所以客户端这一份状态机跑过去不会有任何副作用。
     */
    private void fireDamagePoint(int hitIndex) {
        // 这一下是哪个伤害点：把 Hit.poise 挂出来，技能里新建的 ModDamageSpec 会自动读走它
        // （见 HitPoise）。成对 push/restore，异常也复原，不会串到下一下。
        float hitCoefficient = hitPoiseCoefficient(hitIndex);
        float previousPoise = HitPoise.push(hitCoefficient);
        ImpactLevel previousImpact = HitImpact.push(hitImpact(hitIndex));
        HitPoiseDamage.reset();
        try {
            fire(definition.getOnActiveStart());
        } finally {
            HitPoise.restore(previousPoise);
            HitImpact.restore(previousImpact);
        }

        // 攻击统一入口：元素附着 + 这一下的实际削韧（技能写过就用技能的，没写用武器表兜底）
        if (!(context.player.level() instanceof ServerLevel) || definition.kind == ActionKind.DODGE) {
            return;
        }
        try {
            GenshinElement element = context.character == null ? null : context.character.getElemental();
            double reach = definition.step != null
                    ? Math.max(2.5, definition.step.effectiveAttackRange())
                    : 2.5;
            float poise = HitPoiseDamage.current();
            if (Float.isNaN(poise)) {
                poise = defaultPoise(hitCoefficient);
            }
            // 招式身份：kindId + originId（AttachmentSource 只表达覆盖规则）
            String kind = definition.kind.name().toLowerCase(java.util.Locale.ROOT);
            ElibAttackPipeline.dispatch(ElibAttackAction.of(context.player, element,
                    ElibAttackTrigger.ACTION_DAMAGE_POINT, AttachmentSource.NORMAL_ATTACK,
                    AttachmentProfile.WEAK, reach)
                    .withKindId(kind)
                    .withOriginId(com.linweiyun.genshin.Minegenshin.id("attack/" + kind))
                    .withPoise(poise));
        } catch (Exception e) {
            LOGGER.error("[ElibAttack] 攻击管线抛异常 kind={} tick={}", definition.kind, tickCount, e);
        }
    }

    /** 没写过显式削韧时的兜底：武器表（拿不到落攻击类型表）× 这一段的系数。 */
    private float defaultPoise(float hitCoefficient) {
        AttackType attackType = attackTypeOf(definition.kind);
        if (attackType == null) {
            return 0f;
        }
        float base = WeaponPoiseTable.basePoise(WeaponPoiseTable.weaponOf(context.character), attackType);
        if (Float.isNaN(base)) {
            base = ModDamageSpec.defaultPoise(attackType);
        }
        return base * (hitCoefficient > 0f ? hitCoefficient : 1f);
    }

    private static AttackType attackTypeOf(ActionKind kind) {
        return switch (kind) {
            case NORMAL_ATTACK -> AttackType.NORMAL_ATTACK;
            case CHARGED_ATTACK -> AttackType.CHARGED_ATTACK;
            case PLUNGING_ATTACK -> AttackType.PLUNGING_ATTACK;
            case ELEMENTAL_SKILL_TAP, ELEMENTAL_SKILL_HOLD -> AttackType.ELEMENTAL_SKILL;
            case ELEMENTAL_BURST -> AttackType.ELEMENTAL_BURST;
            default -> null;
        };
    }

    /** 第 {@code hitIndex} 个伤害点的削韧系数；越界或没有 hits 就是基准 1.0。 */
    private float hitPoiseCoefficient(int hitIndex) {
        ActionStep step = definition.step;
        if (step == null || step.hits == null || hitIndex < 0 || hitIndex >= step.hits.size()) {
            return (float) Hit.DEFAULT_POISE;
        }
        return (float) step.hits.get(hitIndex).poise;
    }

    /** 第 {@code hitIndex} 个伤害点的冲击类型；越界或没有 hits 就是默认微颤。 */
    private ImpactLevel hitImpact(int hitIndex) {
        ActionStep step = definition.step;
        if (step == null || step.hits == null || hitIndex < 0 || hitIndex >= step.hits.size()) {
            return Hit.DEFAULT_IMPACT;
        }
        return step.hits.get(hitIndex).impact;
    }

    private void finish() {
        fire(definition.getOnComplete());
        finished = true;
    }


    public void interrupt(InterruptReason reason) {
        if (finished) return;
        context.markInterrupted(reason);
        fire(definition.getOnInterrupt());
        finished = true;
    }

    /** 当前保护期剩余 tick（不在执行期内时为 0）。 */
    public int protectRemaining() {
        return isProtected() ? Math.max(0, protectDuration - tickCount) : 0;
    }

    /**
     * 当前是否在<b>执行期</b>内 —— 只有执行期不可打断。
     *
     * <p>准备阶段（{@code tickCount < prepareTicks}）可以被打断：那是吟唱，
     * 玩家走开 / 挨打就该作废；执行期一旦开始就必须打完，
     * 否则会出现「CD 扣了、能量没了、效果没出来」。
     */
    public boolean isProtected() {
        return protectDuration > prepareTicks
                && tickCount >= prepareTicks
                && tickCount < protectDuration;
    }

    private void fire(Consumer<ActionContext> hook) {
        if (hook == null) return;
        try {
            hook.accept(context);
        } catch (Exception e) {
            LOGGER.error("[ActionState] hook threw kind={} tick={}", definition.kind, tickCount, e);
        }
    }
}
