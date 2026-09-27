package com.linweiyun.genshin.client.combat.action;

import com.linweiyun.genshin.core.system.combat.action.data.ActionStep;

import com.linweiyun.genshin.core.character.CharacterHelper;

import com.linweiyun.genshin.client.combat.AttackApproach;
import com.linweiyun.genshin.client.combat.BurstDive;
import com.linweiyun.genshin.client.camera.ThirdPersonCamera;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.network.ActionServer;
import com.linweiyun.genshin.core.system.combat.animation.action.CharacterActionHandler;
import com.linweiyun.genshin.core.system.combat.animation.action.CharacterActions;
import com.linweiyun.genshin.core.system.combat.action.ActionContext;
import com.linweiyun.genshin.core.system.combat.action.ActionDefinition;
import com.linweiyun.genshin.core.system.combat.action.ActionKind;
import com.linweiyun.genshin.core.system.combat.action.ActionSet;
import com.linweiyun.genshin.core.system.combat.action.InterruptReason;
import com.linweiyun.genshin.core.system.combat.action.data.Engagement;
import com.linweiyun.genshin.client.combat.state.ActionStateMachine;
import com.linweiyun.genshin.client.combat.state.AnimationAvailability;
import com.linweiyun.genshin.core.system.combat.targeting.CombatTargeting;
import com.linweiyun.genshin.core.system.combat.targeting.TargetPolicy;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 数据驱动的动作编排 —— 所有角色共用这一个实现。
 *
 * <p>参考2 是「一个角色一个 {@code XxxActionHandler} + 一个 {@code XxxComboClient}」，时序硬编码在客户端。
 * 本项目保留了另一条链路：<b>角色资源 → 角色天赋</b>（如 {@code VesnaResources.ACTION_DATA} →
 * {@code VesnaSkill.buildActionSet()} → {@link ActionSet}），
 * 所以这里只做两件事：
 *
 * <ol>
 *   <li>从当前角色的 {@link ActionSet} 里取 {@link ActionDefinition}，
 *       把它的 {@code ActionStep} 数值喂给 {@link ActionStateMachine}（动画名 / 总刻数 / 硬直 / 定身）；</li>
 *   <li>把请求转给服务端，由服务端继续走 {@code ActionManager → SkillBase} 结算伤害。</li>
 * </ol>
 *
 * <p>时序只有一份数据源：改 {@code XxxResources.ACTION_DATA}，客户端动画和服务端伤害同时生效。
 *
 * <h2>从 ActionStep 推导出的客户端时序</h2>
 * <pre>
 * 0            prepareTicks          protectDuration      duration
 * ├─ 准备阶段 ────┼──── 执行期 ────────────┼──── 后摇 ────┤
 *    可打断          不可打断               可取消
 * </pre>
 * <ul>
 *   <li>{@code totalTicks} = {@code step.duration}</li>
 *   <li>{@code lockDelay}（准备阶段）= {@code step.prepareTicks}，这段时间锁还没生效</li>
 *   <li>{@code lockFrames}（执行期）= {@code protectDuration - prepareTicks}；
 *       没配执行期（{@code protectDuration <= prepareTicks}）时退化成
 *       {@code min(duration / 4, 8)} —— 保证「按下去马上有反应，但要等一拍才能接下一段」。</li>
 *   <li>{@code movementLock}（定身）= 有执行期就用执行期本身（大招那种整段定身），
 *       否则 {@code min(lockFrames, 5)}。</li>
 * </ul>
 */
public final class ResourceDrivenActionHandler implements CharacterActionHandler {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.COMBAT);

    public static final ResourceDrivenActionHandler INSTANCE = new ResourceDrivenActionHandler();

    /** 硬直上限：动作再长也不会把人钉太久，否则手感发闷。 */
    private static final int MAX_LOCK_FRAMES = 8;

    /** 普攻的默认定身刻数（对齐参考2 普攻 1 段的 5 刻）。 */
    private static final int DEFAULT_MOVE_LOCK = 5;

    private ResourceDrivenActionHandler() {
    }

    // ==================== 按键入口 ====================

    @Override
    public void attack(Player player) {
        PGCharacter character = currentCharacter(player);
        if (character == null) return;

        ActionSet set = character.getActionSet(player);
        if (set == null || set.getNormalComboSize() == 0) {
            // 还没配动作数据的角色：这一下什么都不做（所有角色都走模型 + 完整动作系统，
            // 没有「退回摆臂」那一档了）
            return;
        }

        int stage = ActionStateMachine.comboStage;
        if (stage < 1 || stage > set.getNormalComboSize()) {
            stage = 1;
        }

        ActionDefinition def = set.getNormalAttack(stage);
        if (!playable(player, def)) return;

        // 前置判断（普攻默认没门槛，但钩子对所有招式统一）
        if (!ActionCastGuard.canCast(player, character, ActionKind.NORMAL_ATTACK, 0)) return;

        // 客户端只负责段数推进与动画；伤害由服务端按同一个 ActionSet 结算
        boolean isLastStage = stage >= set.getNormalComboSize();
        ActionStateMachine.comboStage = isLastStage ? 1 : stage + 1;
        final int stageIndex = stage;

        engageAndPlay(player, def, ActionStateMachine.PRIO_ATTACK, null, target -> {
            // 收尾动画：最后一段打完自动接上，否则模型会停在那一帧
            if (isLastStage && def.step.comboEndAnim != null
                    && AnimationAvailability.existsFor(player, def.step.comboEndAnim)) {
                ActionStateMachine.queueFollowUpState(def.step.comboEndAnim, def.step.comboEndTicks);
            }
            ActionServer.performNormalAttackToServer(stageIndex, target);
        });
    }

    /**
     * 大剑（持续型重击）按下左键<b>不</b>立刻出普攻。
     *
     * <p>否则按住左键会先打掉一段普攻、再进重击状态（见 {@code ClaymoreSkill} 的类注释）。
     * 延后的那一下由 {@code ActionStateMachine.releaseAttack} 在「这次按住没进重击状态」
     * 时补上 —— 单击依旧是普攻，按住就只有重击。
     * 单手剑 / 长柄（触发型重击）保持按下即出普攻，不受影响。
     */
    @Override
    public boolean deferNormalAttackOnPress(Player player) {
        PGCharacter character = currentCharacter(player);
        return character != null && character.isSustainedChargedAttack();
    }

    @Override
    public boolean skill(Player player, boolean longPress) {
        PGCharacter character = currentCharacter(player);
        if (character == null) return false;

        ActionSet set = character.getActionSet(player);
        if (set == null) return false;

        ActionDefinition def = longPress ? set.getElementalSkillHold() : set.getElementalSkillTap();
        if (!playable(player, def)) return false;

        final int holdFlag = longPress ? 1000 : 0;
        final ActionKind kind = longPress ? ActionKind.ELEMENTAL_SKILL_HOLD : ActionKind.ELEMENTAL_SKILL_TAP;

        // 放不出来就到此为止：不播动画、不发请求（否则就是「动画播了但没效果」）。
        // ⚠️ 用 def.kind 而不是上面按「按了哪个键」推出来的 kind —— 两者可能不一样：
        //    满命薇斯娜的「翔风剑·变移」占的是 E 点按槽，但它的 kind 是 SPECIAL
        //    （不扣剑气、不转 CD）；用 ELEMENTAL_SKILL_TAP 去判会被 CD / 剑气拦下来，
        //    而服务端判的是 def.kind，两端就会分叉。
        if (!ActionCastGuard.canCast(player, character, def.kind, holdFlag)) return false;

        engageAndPlay(player, def, ActionStateMachine.PRIO_ATTACK, null,
                target -> ActionServer.triggerCharacterSkill(holdFlag, target));
        return true;
    }

    @Override
    public void ultimate(Player player) {
        PGCharacter character = currentCharacter(player);
        if (character == null) return;

        ActionSet set = character.getActionSet(player);
        if (set == null) return;

        ActionDefinition def = set.getElementalBurst();
        if (!playable(player, def)) return;

        if (!ActionCastGuard.canCast(player, character, ActionKind.ELEMENTAL_BURST, 0)) return;

        engageAndPlay(player, def, ActionStateMachine.PRIO_FINAL, null,
                ActionServer::triggerCharacterBurst);

        // 「跃起下坠刺击」类大招：动画只管起跳与摆姿态，位移交给客户端序列
        // （落点在开始下坠那一刻锁死，之后不再追踪 —— 见 BurstDive / ActionStep.diveBurst）
        if (def.step.diveBurst != null && player instanceof LocalPlayer localPlayer) {
            BurstDive.begin(localPlayer, CombatTargeting.current(player), def.step);
        }
    }

    @Override
    public void dodge(Player player) {
        PGCharacter character = currentCharacter(player);
        if (character == null) return;

        ActionSet set = character.getActionSet(player);
        ActionDefinition def = set == null ? null : set.getDodge();
        if (!playable(player, def)) return;

        if (!ActionCastGuard.canCast(player, character, ActionKind.DODGE, 0)) return;

        // 闪避不索敌：方向由玩家输入决定，自动转向会把「往后闪」变成「往怪身上闪」
        play(player, def, ActionStateMachine.PRIO_DODGE, resolveDodgeAnim(player, def));
        ActionServer.triggerCharacterDodge();
    }

    /**
     * 长按左键的蓄力判定。
     *
     * <p>阈值来自角色自己的 {@code getChargedAttackChargeTicks()}，重击的动画与结算走
     * {@link ActionSet#getChargedAttack()}（同样是资源 → 天赋那条链路）。
     */
    @Override
    public void tickCharge(Player player, int holdTicks) {
        PGCharacter character = currentCharacter(player);
        if (character == null) return;

        int chargeTicks = character.getChargedAttackChargeTicks();
        if (chargeTicks <= 0 || holdTicks < chargeTicks) return;
        if (!ActionStateMachine.canInterrupt(ActionStateMachine.PRIO_ATTACK)) return;

        ActionSet set = character.getActionSet(player);
        ActionDefinition def = set == null ? null : set.getChargedAttack();
        if (!playable(player, def)) return;

        if (!ActionCastGuard.canCast(player, character, ActionKind.CHARGED_ATTACK, 0)) return;

        if (character.isSustainedChargedAttack()) {
            // 大剑：这一招不是「放一次」，而是「进一个状态」—— 见 ClaymoreSkill 的类注释
            beginSustainedChargedAttack(player, def);
        } else {
            engageAndPlay(player, def, ActionStateMachine.PRIO_ATTACK, null,
                    ActionServer::performChargedAttackToServer);
        }

        // 一次按住只触发一次重击
        ActionStateMachine.chargedAttackTriggered = true;
    }

    /**
     * 左键松开 —— <b>持续型重击的第二个出口</b>（另一个是到达最高持续时间，由状态机自己到时复位）。
     *
     * <p>触发型重击不需要这一步：它触发一次就自己打完了。持续型的执行期铺满整段
     * （不然走位就能把它取消掉），服务端那边的普通打断会被执行期直接丢掉，
     * 所以松手得显式发一条 {@link InterruptReason#CHARGE_RELEASE} 过去收招，
     * 否则服务端会继续把剩下的伤害点打完。
     */
    @Override
    public void releaseAttack(Player player, int chargeTicks) {
        // 只有当「此刻确实还在持续重击状态里」才收招：
        // 普通的一下点击松手、或者重击早就到时/被打断了，都不能在这里去动状态机。
        if (!ActionStateMachine.currentStateLoops()) return;
        if (!(player instanceof LocalPlayer)) return;

        ActionStateMachine.resetToDefault();
        ThirdPersonCamera.setFollowBody(false);
        ActionServer.interruptActionToServer(InterruptReason.CHARGE_RELEASE.ordinal());
    }


    private static void beginSustainedChargedAttack(Player player, ActionDefinition def) {
        ActionStep step = def.step;
        int totalTicks = Math.max(1, step.duration);

        ActionStateMachine.setCurrentAttackRange(step.effectiveAttackRange());

        fireLocalTalentHook(player, def);
        ActionSoundScheduler.scheduleActionSounds(player, step);

        if (!AnimationAvailability.existsFor(player, def.animationName())) {
            LOGGER.warn("[MineGenshin] 角色 '{}' 没有持续重击动画 '{}'：照常进状态与结算，"
                            + "只是视觉上停在上一帧（补一个循环动画即可）",
                    CharacterHelper.getActiveCharacterId(player), def.animationName());
        }

        // 进状态：总时长 = 最高持续时间，执行期铺满整段（挨打也打不断），定身 0（可以边走边转）。
        // loop = true 顺带把「这是持续型」这条信息挂到状态机上：动画循环播、视角跟随跟着它挂/摘，
        // 并且让常态输入打断（走位/跳/蹲）对这一段整段失效 —— 见 ActionStateMachine.interruptOnNormalInput。
        ActionStateMachine.changeState(def.animationName(), ActionStateMachine.PRIO_ATTACK,
                totalTicks, step.protectDuration, 0, 0, true);

        // 视角跟随：申鹤（以及以后所有大剑）重击持续期间镜头跟着角色；
        // 状态一结束（松手/到时/被打断/切角色）会自动落回 —— 见 ThirdPersonCamera.tick
        ThirdPersonCamera.setFollowBody(true);

        ActionServer.performChargedAttackToServer(CombatTargeting.current(player));
    }

    // ==================== 索敌 → 转向/突进 → 发服务端 ====================

    /**
     * 一个招式的完整出手流程，<b>所有招式共用这一条</b>：
     *
     * <ol>
     *   <li>按<b>这一招</b>的参数索敌（{@link ActionStep#engagement}）；</li>
     *   <li>近战招式且目标在攻击距离外 → 突进（动画冻在起手帧，到位再解冻）；</li>
     *   <li>其余情况 → 只转向（几刻内滑过去，不位移）；</li>
     *   <li>动画/音效照常排期，服务端请求带上目标。</li>
     * </ol>
     *
     * <p><b>远程招式</b>（{@code Engagement.ranged}）走的就是第 3 条：
     * 索敌距离 = 攻击距离，够不着就不锁、也永远不会突进。
     *
     * @param serverCall 真正发给服务端的请求；参数是这一招锁到的目标（可能为 null）。
     *                   放在这里是因为突进路径要等「到位那一刻」才发，
     *                   服务端的伤害计时才会跟着从到位起算。
     */
    private static void engageAndPlay(Player player, ActionDefinition def, int priority,
                                      @Nullable String animationOverride,
                                      @Nullable Consumer<LivingEntity> serverCall) {
        ActionStep step = def.step;
        float attackRange = step.effectiveAttackRange();
        ActionStateMachine.setCurrentAttackRange(attackRange);

        // 客户端时序（动画总长 / 执行期锁 / 准备阶段 / 定身）——
        // 突进路径也要用同一份：到位时靠它把执行期重新起算。
        Timing timing = timingFor(step);

        Engagement engagement = step.engagement == null ? Engagement.melee() : step.engagement;
        CombatTargeting.Params params = targetingParams(engagement, attackRange);

        // 客户端本地钩子：声明了「客户端也要跑一次天赋」的角色（例如申鹤的突刺），
        // 在出手这一刻本地执行一次这一招的 onActiveStart；服务端请求照旧发。
        Consumer<LivingEntity> dispatch = serverCall == null ? null : target -> {
            fireLocalTalentHook(player, def);
            serverCall.accept(target);
        };

        // 先看现成的锁（远程招式够不着也仍然会对着它转），没有再按这一招的参数找
        LivingEntity locked = CombatTargeting.current(player);
        LivingEntity acquired = CombatTargeting.acquire(player, params);
        final LivingEntity target = acquired != null ? acquired : locked;
        CombatTargeting.lock(player, target, params);

        if (player instanceof LocalPlayer localPlayer && target != null) {
            // 目标在攻击距离外 → 先贴上去：起手动作冻在那一帧，到位再解冻接着播
            boolean wantsDash = engagement.wantsDash()
                    && AttackApproach.needsDash(localPlayer, target, attackRange);

            if (wantsDash && step.dashStartDelay > 0) {
                // 前 N 刻是动作本身的一部分（起跳 / 人消失 / 变成长枪螺旋…），
                // 这几刻动画照常播、人也还在原地；第 N 刻才冻结并冲出去。
                // 见 ActionStep#dashStartDelay。
                play(player, def, priority, animationOverride, false);

                final int sequence = ActionStateMachine.actionSequence();
                final int delay = step.dashStartDelay;

                ActionStateMachine.queueClientWork(delay, () -> {
                    // 中途换招了（闪避/被打断/接了下一段）→ 这次突进作废，别再补一刀
                    if (ActionStateMachine.actionSequence() != sequence) {
                        return;
                    }

                    // 目标可能已经死了、也可能自己走过来了 → 重新判一次
                    LivingEntity fresh = CombatTargeting.current(localPlayer);
                    LivingEntity now = fresh != null ? fresh : target;

                    if (now != null && AttackApproach.needsDash(localPlayer, now, attackRange)) {
                        beginDash(localPlayer, now, engagement, attackRange, player, step, timing, dispatch);
                    } else {
                        // 不用冲了：时间零点同样是第 N 刻，直接出手
                        strikeAfterApproach(player, step, timing, dispatch, now);
                    }
                });
                return;
            }

            if (wantsDash) {
                // 按下即冻结（默认）：起手动作停在第一帧，摆臂和音效都等到位那一刻，
                // 否则声音会在冻结期间就响
                play(player, def, priority, animationOverride, false);
                beginDash(localPlayer, target, engagement, attackRange, player, step, timing, dispatch);
                return;
            }

            // 已经在范围内 / 这一招是远程 → 只转向（滑过去）+ 出手瞬间的一小步吸附
            AttackApproach.faceTarget(localPlayer, target, engagement);
            AttackApproach.stepToward(localPlayer, target, attackRange, engagement);
        }

        play(player, def, priority, animationOverride, true);
        if (dispatch != null) {
            dispatch.accept(target);
        }
    }

    /**
     * 客户端本地跑一次「这一招出手了」的钩子。
     *
     * <p>只为 {@link PGCharacter#runsTalentOnClient()} 为 {@code true} 的角色执行
     * —— 那些招式的表现层位移由技能自己算（申鹤的 {@code DashSystem}：客户端按格推位置、
     * 服务端沿途扫伤害），而角色天赋现在默认只在服务端跑，不补这一次的话
     * 客户端那段位移永远没人调用。
     */
    private static void fireLocalTalentHook(Player player, ActionDefinition def) {
        if (!(player instanceof LocalPlayer)) return;
        PGCharacter character = currentCharacter(player);
        if (character == null || !character.runsTalentOnClient()) return;

        Consumer<ActionContext> hook = def.getOnActiveStart();
        if (hook == null) return;
        try {
            hook.accept(new ActionContext(player, character, def));
        } catch (Exception e) {
            LOGGER.error("[MineGenshin] 客户端本地招式钩子抛异常 kind={}", def.kind, e);
        }
    }

    /**
     * 开始突进：动画冻在当前这一帧，每刻滑向目标，到位后回调 {@link #strikeAfterApproach}。
     *
     * <p>{@code dashStartDelay = 0} 时这是在按下那一帧调用的（冻在第一帧）；
     * 大于 0 时是在第 N 刻调用的（冻在第 N 帧）。
     */
    private static void beginDash(LocalPlayer localPlayer, LivingEntity target, Engagement engagement,
                                  float attackRange, Player player, ActionStep step,
                                  Timing timing, @Nullable Consumer<LivingEntity> serverCall) {
        AttackApproach.begin(localPlayer, target, engagement, attackRange,
                () -> strikeAfterApproach(player, step, timing, serverCall, target));
    }

    /**
     * 「时间零点」到了：解冻动画、把执行期（动画时钟 / 硬直 / 定身）从这一刻重新起算，
     * 然后摆臂 + 排音效 + 发服务端请求。
     *
     * <p>三条路都汇到这里：正常突进到位、延后突进到位、以及「本来要冲但已经不用冲了」。
     * 服务端的伤害计时也从这一刻起算（{@code hits[].delay} 相对它），
     * 所以动画看到的那一刀和服务端结算的那一刀是同一帧。
     */
    private static void strikeAfterApproach(Player player, ActionStep step,
                                            Timing timing,
                                            @Nullable Consumer<LivingEntity> serverCall,
                                            @Nullable LivingEntity target) {
        ActionStateMachine.resumeFromApproach(timing.totalTicks(),
                timing.lockFrames(), timing.movementLock());
        ActionSoundScheduler.scheduleActionSounds(player, step);
        if (serverCall != null) {
            serverCall.accept(target);
        }
    }

    /**
     * 把招式的交战形态翻译成索敌参数。
     *
     * <p>核心差异就一条：<b>远程招式的索敌距离 = 攻击距离</b>（够得着才锁），
     * 近战招式则用默认的 6 格索敌、9 格保持，差额交给突进补。
     */
    private static CombatTargeting.Params targetingParams(Engagement engagement, float attackRange) {
        Engagement e = engagement == null ? Engagement.melee() : engagement;

        // 没显式写索敌距离时：远程跟攻击距离走（不判路径），近战用全局默认（判「追不追得上」）
        CombatTargeting.Params base = e.ranged
                ? CombatTargeting.Params.forRange(e.acquireRange > 0 ? e.acquireRange : attackRange)
                : CombatTargeting.Params.forChase(attackRange);

        double acquire = e.acquireRange > 0 ? e.acquireRange : base.acquireRange();
        double keep = e.keepRange > 0 ? e.keepRange : base.keepRange();
        double acquireAngle = e.acquireAngle > 0 ? e.acquireAngle : base.acquireAngle();
        double keepAngle = e.keepAngle > 0 ? e.keepAngle : base.keepAngle();

        return new CombatTargeting.Params(acquire, acquireAngle, keep, keepAngle,
                base.attackRange(), base.chase(), TargetPolicy.DEFAULT);
    }

    // ==================== 内部 ====================

    private static boolean play(Player player, ActionDefinition def, int priorityOverride) {
        return play(player, def, priorityOverride, null, true);
    }

    private static boolean play(Player player, ActionDefinition def, int priorityOverride,
                                @Nullable String animationOverride) {
        return play(player, def, priorityOverride, animationOverride, true);
    }

    /**
     * 把 ActionStep 的数值喂给状态机；没有特殊模型的角色改为在伤害点摆一次手臂。
     *
     * <p><b>动画保护</b>：先确认这个名字真的在角色的动画文件里。不存在就<b>不切动画</b> ——
     * 保持当前动画，而不是让 GeckoLib 切进空状态（那会让模型露出原始姿态、角色呆站着）。
     * 伤害、位移、音效、服务端请求都照常，只有「切动画」这一步被跳过。
     *
     * @param animationOverride 覆盖资源里写的动画名（目前只有闪避会按方向换），{@code null} 表示用资源里的
     * @param feedbackNow       要不要现在排「摆臂 + 音效」；突进时传 false，
     *                          等贴到目标那一刻再排（否则声音会在动画冻结期间就响掉）
     * @return 有没有真的切了动画
     */
    private static boolean play(Player player, ActionDefinition def, int priorityOverride,
                                @Nullable String animationOverride, boolean feedbackNow) {
        ActionStep step = def.step;

        Timing timing = timingFor(step);
        String animation = (animationOverride == null || animationOverride.isEmpty())
                ? def.animationName()
                : animationOverride;

        boolean animated = AnimationAvailability.existsFor(player, animation);
        if (animated) {
            ActionStateMachine.changeState(animation, priorityOverride, timing.totalTicks(),
                    timing.lockFrames(), timing.movementLock(), timing.lockDelay());
        } else {
            LOGGER.warn("[MineGenshin] 角色 '{}' 没有动画 '{}'：这次动作只结算伤害，不切动画",
                    CharacterHelper.getActiveCharacterId(player), animation);
        }

        if (feedbackNow) {
            ActionSoundScheduler.scheduleActionSounds(player, step);
        }

        return animated;
    }

    /** 闪避动画的方向后缀。 */
    private static final List<String> DODGE_DIRECTIONS = List.of("front", "back", "left", "right");

    /**
     * 闪避按「输入方向」选动画。
     *
     * <p>资源里写的是基准名（{@code dodge} 或已经带方向的 {@code dodge_front}），
     * 真正存在的是四个方向变体。基准名先剥掉方向后缀再拼上本帧的方向，
     * 拼出来的名字不在角色的特殊动画名单里时退回原值，不会因为动画缺失把角色卡住。
     */
    private static String resolveDodgeAnim(Player player, ActionDefinition def) {
        String configured = def.animationName();
        Set<String> specialAnims = CharacterActions.animationsFor(player).specialAnims();

        String base = configured;
        for (String direction : DODGE_DIRECTIONS) {
            String suffix = "_" + direction;
            if (base.endsWith(suffix)) {
                base = base.substring(0, base.length() - suffix.length());
                break;
            }
        }

        String candidate = base + "_" + dodgeDirection(player);
        if (specialAnims.contains(candidate)) {
            return candidate;
        }

        return specialAnims.contains(configured) ? configured : candidate;
    }

    private static String dodgeDirection(Player player) {
        Vec2 move = player instanceof LocalPlayer localPlayer
                ? localPlayer.input.getMoveVector()
                : Vec2.ZERO;

        // 没有方向输入 → 向后闪（对齐参考2 MiyabiDodgeClient 的默认）
        if (move.lengthSquared() < 1.0E-5f) {
            return "back";
        }
        if (Math.abs(move.y) >= Math.abs(move.x)) {
            return move.y >= 0 ? "front" : "back";
        }
        // moveVector.x 是左正右负（与 xxa 一致）
        return move.x > 0 ? "left" : "right";
    }

    /**
     * 一段动作的客户端时序 —— 见类注释的三窗口图。
     *
     * @param totalTicks   动画总刻数
     * @param lockFrames   执行期硬直刻数（这段时间同级或更低优先级打不断）
     * @param lockDelay    准备阶段刻数（这段时间硬直与定身都还没生效，可以被主动打断）
     * @param movementLock 定身刻数
     */
    private record Timing(int totalTicks, int lockFrames, int lockDelay, int movementLock) {
    }

    private static Timing timingFor(@Nullable ActionStep step) {
        int totalTicks = step == null ? 1 : Math.max(1, step.duration);

        // 准备阶段（吟唱）不能超过总时长
        int prepare = step == null ? 0 : Math.max(0, Math.min(step.prepareTicks, totalTicks));

        // 执行期 = [prepareTicks, protectDuration)
        int execution = step == null ? 0 : step.protectDuration - prepare;

        if (execution <= 0) {
            // 没配执行期：退化成「按下去马上有反应，但要等一拍才能接下一段」
            int lock = Math.min(Math.max(1, totalTicks / 4), MAX_LOCK_FRAMES);
            return new Timing(totalTicks, lock, prepare, Math.min(lock, DEFAULT_MOVE_LOCK));
        }

        // 有执行期：整段执行期都锁 + 定身（大招那种绝对霸体就是 protect = duration）
        return new Timing(totalTicks, execution, prepare, execution);
    }

    private static boolean playable(@Nullable Player player, @Nullable ActionDefinition def) {
        return player != null && def != null && def.step != null;
    }

    @Nullable
    private static PGCharacter currentCharacter(Player player) {
        PlayerCharactersAttachment attachment =
                player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        return attachment == null ? null : attachment.getCurrentCharacter();
    }
}
