package com.linweiyun.genshin.client.combat.state;
import com.linweiyun.genshin.core.system.combat.action.data.ActionStep;

import com.linweiyun.genshin.core.character.CharacterHelper;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.client.combat.AttackApproach;
import com.linweiyun.genshin.client.combat.BurstDive;
import com.linweiyun.genshin.core.system.combat.targeting.CombatTargeting;
import com.linweiyun.genshin.core.system.combat.animation.action.CharacterActionHandler;
import com.linweiyun.genshin.core.system.combat.animation.action.CharacterActions;
import com.linweiyun.genshin.core.system.combat.animation.config.CharacterAnimations;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.network.ActionServer;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.linweiyun.genshin.core.system.combat.action.InterruptReason;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * 客户端动作状态机 —— 动画控制系统的中枢，<b>只服务本地玩家</b>
 * （远程玩家的动画靠同步变量驱动，见 {@code AnimationStateSync}）。
 *
 * <p>移植自参考2 的 {@code ActionStateMachine}，本项目做了三处适配：
 * <ol>
 *   <li>状态机与动画时长仍由「资源 → 技能」链路提供（{@code VesnaResources.ACTION_DATA} →
 *       {@code VesnaSkill} → {@code ActionSet}），动作类只负责把数值喂进来。</li>
 *   <li>音效 id 随动画同步包一起发出，服务端再广播给其他玩家。</li>
 *   <li>所有角色都接入完整动作系统（前摇 / 硬直 / 移动封锁 / 延迟伤害），
 *       没有「关掉动作系统」那一档：{@code changeState} 永远写三类计时器，
 *       {@code canInterrupt} 永远按执行期 / 后摇的规则判。</li>
 * </ol>
 *
 * <h2>动画层级（优先级）</h2>
 * <ul>
 *   <li>1 = 常态（走跑跳蹲游）</li>
 *   <li>2 = 普攻 / 战技</li>
 *   <li>3 = 闪避</li>
 *   <li>4 = 大招（绝对霸体，不可打断）</li>
 * </ul>
 *
 * <h2>四个计时器</h2>
 * <ul>
 *   <li>{@link #animationTick} —— 动画总时长，归零即动作结束并自动回常态。</li>
 *   <li>{@link #lockDelayFrames} —— <b>准备阶段</b>剩余刻数（吟唱/读条）。
 *       这段时间里硬直和定身都还没生效，所以还能被打断。</li>
 *   <li>{@link #actionLockFrames} —— <b>执行期</b>（伤害已经/正在打出来）剩余刻数，
 *       期间同级或更低优先级不能打断。</li>
 *   <li>{@link #movementLockFrames} —— 额外的移动封锁（定身）。</li>
 * </ul>
 *
 * <p>准备阶段 → 执行期 → 后摇，这个三窗口模型的数据来源是
 * {@code ActionStep.prepareTicks} / {@code ActionStep.protectDuration}，
 * 见 {@code ActionStep#protectDuration} 的图。
 */
@EventBusSubscriber(modid = Minegenshin.MOD_ID, value = Dist.CLIENT)
public final class ActionStateMachine {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.COMBAT);

    /** 常态状态名。与 {@code ActionStep.animation} 的取值域不冲突。 */
    public static final String DEFAULT_STATE = "default";

    public static final int PRIO_NORMAL = 1;
    public static final int PRIO_ATTACK = 2;
    public static final int PRIO_DODGE = 3;
    public static final int PRIO_FINAL = 4;

    // ---- 状态机核心内存（只服务本地玩家）----

    /** 当前动作状态名；{@link #DEFAULT_STATE} 表示常态。 */
    public static String currentState = DEFAULT_STATE;

    /** 动画层级：1 常态 / 2 普攻·技能 / 3 闪避 / 4 大招。 */
    public static int currentPriority = PRIO_NORMAL;

    /**
     * 当前动作是不是<b>持续型</b>（大剑重击那种：进状态、松手或到时才结束）。
     *
     * <p>由 {@link #changeState(String, int, int, int, int, int, boolean)} 的
     * {@code loopAnimation} 写入，任何别的 {@code changeState} / {@link #resetToDefault()}
     * 都会把它关掉 —— 所以它天然是一面「现在到底还在不在持续招式里」的旗子，
     * 不需要各处自己维护一份、也就不会漏关。三个地方读它：
     * <ul>
     *   <li>{@code PlayerAnimationController}：动画要循环播（而不是播完停在最后一帧）；</li>
     *   <li>{@code ThirdPersonCamera}：视角跟随只在持续期间挂着，松手/到时/被打断自动落回；</li>
     *   <li>{@link #interruptOnNormalInput}：<b>这段状态整段豁免「常态输入打断」</b>
     *       —— 持续招式与位移是兼容关系，走位/跳跃/潜行都只是带着它走，不取消它。</li>
     * </ul>
     */
    private static boolean currentStateLoops = false;

    /** 当前动作剩余总刻数。 */
    public static int animationTick = 0;

    /** 执行期（硬直）剩余刻数 —— 结束之后就是可以随便取消的后摇。 */
    public static int actionLockFrames = 0;

    /**
     * 准备阶段剩余刻数（吟唱 / 读条）。
     *
     * <p>这段时间里 {@link #actionLockFrames} 与 {@link #movementLockFrames}
     * <b>都还没开始倒数</b>（见 {@link #actionLocked()} / {@link #movementFrozen()}），
     * 所以准备阶段是可以被主动打断的：走开、跳跃、下一招都能让它作废。
     */
    public static int lockDelayFrames = 0;

    /** 移动封锁剩余刻数。 */
    public static int movementLockFrames = 0;

    /** 当前连击段数（1 起）。 */
    public static int comboStage = 1;

    /** 连击保持窗口：硬直结束后还能接续下一段的剩余刻数。 */
    public static int comboWindowFrames = 0;

    /** 左键按住时长（用于重击蓄力判定）。 */
    public static int attackHoldTimer = 0;

    /** 左键是否处于按住状态。 */
    public static boolean isAttackButtonDown = false;

    /** 战技键是否处于按住状态（短按 / 长按有区别的角色才用得上）。 */
    public static boolean isSkillButtonDown = false;

    /** 战技键按住时长（刻）。 */
    public static int skillHoldTimer = 0;

    /** 长按战技是否已经触发过（避免按住期间反复触发）。 */
    private static boolean skillHoldTriggered = false;

    /** 重击是否已经因为这次按住触发过（松手才复位）。 */
    public static boolean chargedAttackTriggered = false;

    /**
     * 这一次按住把普攻延后了（大剑：见 {@code CharacterActionHandler#deferNormalAttackOnPress}）。
     *
     * <p>为 {@code true} 时，松手那一刻若这次按住没进过重击状态，就补一次普攻 ——
     * 单击依旧是普攻。触发型重击（单手剑 / 长柄）永远是 {@code false}，按下即出普攻。
     */
    private static boolean deferredNormalAttack = false;

    /** 长按战技的判定阈值（刻）：对齐旧的 1000ms 手感。 */
    public static final int SKILL_HOLD_TICKS = 20;

    /**
     * 收尾动画（例如普攻最后一段的 {@code xxx_end}）。
     *
     * <p>当前动作播完时如果这里排着一个名字，状态机不会回常态，而是直接续上它 ——
     * 否则模型会停在上一段的最后一帧，看起来像卡住。
     */
    @Nullable
    private static String followUpState = null;

    /** 收尾动画播多少刻。 */
    private static int followUpTicks = 0;

    /**
     * 突进冻结 —— 近战贴脸期间把动画暂停在起手那一帧。
     *
     * <p>由客户端 {@code AttackApproach} 开关；{@code PlayerAnimationController}
     * 看到它为 true 就返回 {@link com.geckolib.animation.object.PlayState#PAUSE}。
     */
    private static boolean approachFrozen = false;

    /** 当前动作的生效攻击距离，突进判定用（由动作编排在切状态时写入）。 */
    private static double currentAttackRange = 3.0;

    /**
     * 动作序号：每切一次状态 +1。
     *
     * <p>用来给「音效随机」做确定性种子 —— 同一刀在所有客户端算出同一个变体，
     * 不需要额外同步包。
     */
    private static int actionSequence = 0;

    public static int actionSequence() {
        return actionSequence;
    }

    /** 当前动作是不是持续型（循环动画那段）；见 {@link #currentStateLoops}。 */
    public static boolean currentStateLoops() {
        return currentStateLoops;
    }

    private ActionStateMachine() {
    }

    // ---- 延迟任务队列 ----

    /** 排入一个客户端延迟任务；队列本体见 {@link ClientTaskQueue}。 */
    public static void queueClientWork(int delayTicks, Runnable action) {
        ClientTaskQueue.enqueue(delayTicks, action);
    }

    // ---- 每客户端 tick ----

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        tickPendingWork(player);
        tickEngagement(player);
        tickHeldButtons(player);
        tickActionCountdowns();

        if (interruptOnNormalInput(player)) {
            return;
        }

        tickPassiveAndRemote(player);
        tickAnimationTimeline();
    }

    //
    // 以下为每 tick 的执行步骤。顺序敏感：整段原先就是 onClientTick 的连续语句，
    // 只是按「一件事一个方法」拆开，逻辑与顺序一字未改，勿重排。
    //

    /** 每 tick 的固定前置：还原定身输入，并推进延迟任务队列。 */
    private static void tickPendingWork(LocalPlayer player) {
        ActionInputFreeze.restore(player);
        ClientTaskQueue.tick();
    }

    /** 索敌校验 + 突进/转向 + 下坠大招（都在动画判定之前，保证当帧就生效）。 */
    private static void tickEngagement(LocalPlayer player) {
        CombatTargeting.tick(player);
        AttackApproach.tick(player);
        BurstDive.tick(player);
    }

    /** 按住类按键：左键蓄力判定与战技长按变体。 */
    private static void tickHeldButtons(LocalPlayer player) {
        // 左键按住 → 交给当前角色的动作编排做蓄力判定
        if (isAttackButtonDown) {
            attackHoldTimer++;

            // 大招期间吞掉蓄力指令；重击一次按住只触发一次
            if (currentPriority < PRIO_FINAL && !chargedAttackTriggered) {
                CharacterActionHandler handler = handlerFor(player);
                if (handler != null) {
                    handler.tickCharge(player, attackHoldTimer);
                }
            }
        } else {
            attackHoldTimer = 0;
        }

        // 战技按住 → 短按/长按有区别的角色等到阈值再放长按版
        if (isSkillButtonDown && !skillHoldTriggered) {
            skillHoldTimer++;
            if (skillHoldTimer >= SKILL_HOLD_TICKS && hasSkillHoldVariant(player)) {
                // 只有真的放出去了才算触发；被硬直挡下来的话下一 tick 继续试
                skillHoldTriggered = trySkill(player, true);
            }
        }
    }

    /** 锁与连击窗口的倒计时。 */
    private static void tickActionCountdowns() {
        // 倒计时：准备阶段 → 执行期（硬直）、定身。
        //
        // ⚠️ 突进期间整块冻结（和下面的动画时间轴一样）：突进属于<b>执行期</b>，
        //    最长可以飞 MAX_APPROACH_TICKS 刻。让锁在飞行途中流失的话会出现
        //    「人还在飞过去，锁已经过期」→ 一个移动输入就把这一刀连同还没发出的服务端请求吞掉。
        //    到位时 resumeFromApproach(...) 会把执行期重新起算。
        if (!approachFrozen) {
            if (lockDelayFrames > 0) {
                // 准备阶段：硬直与定身都还没开始（这段时间可以被打断）
                lockDelayFrames--;
            } else {
                if (actionLockFrames > 0) {
                    actionLockFrames--;
                }
                if (movementLockFrames > 0) {
                    movementLockFrames--;
                }
            }
        }

        // 只有硬直彻底结束（进入后摇）时，连击窗口才开始流失
        if (!actionLocked() && comboWindowFrames > 0) {
            comboWindowFrames--;

            if (comboWindowFrames <= 0) {
                comboStage = 1; // 超时才重置连击段数
            }
        }
    }

    /**
     * 常态输入打断高级动作的后摇。
     *
     * <p>1 级常态输入打断高级动作的后摇（准备阶段也算「可以打断」，执行期不行）。
     * 定身期间输入已经被换成 FrozenInput，这里再看一遍输入会读到「玩家其实按着 W」，
     * 所以定身没结束就不做打断判定 —— 否则一按攻击就被自己的移动输入取消。
     *
     * <p><b>持续型状态（大剑重击）整段不参与这个判定</b>：它跟位移是<b>兼容</b>关系而不是
     * 互斥关系，按 WASD / 空格 / 潜行只是带着这一招走，不是取消它。见方法内的注释与
     * {@code ClaymoreSkill} 的类注释。
     *
     * @return 是否已经复位回常态；为 {@code true} 时调用方应立即结束本 tick
     */
    private static boolean interruptOnNormalInput(LocalPlayer player) {
        // ⭐ 持续型状态（目前只有大剑重击）与位移兼容：走位 / 跳跃 / 潜行都不取消它，
        //    出口只有三个 —— 松手（releaseAttack → CHARGE_RELEASE）、到时（动画时间轴走完）、
        //    以及换人/死亡那种强制打断。
        //
        //    为什么必须显式写这一条，而不是靠下面的 actionLocked() 挡住：
        //    持续重击确实会把执行期铺满整段（所以现在「碰巧」也打不断），
        //    但那是「执行期霸体」的副作用，跟「位移兼容」是两件事 ——
        //    以后哪个角色把 protectDuration 改成「盖住最后一个伤害点」而不是整段，
        //    移动就会立刻把这一招顶掉。
        if (currentStateLoops()) {
            return false;
        }

        if (!movementFrozen() && !actionLocked()
                && animationTick > 0 && !DEFAULT_STATE.equals(currentState)) {
            ClientInput input = player.input;
            boolean isMoving = input.getMoveVector().lengthSquared() > 1.0E-5f;
            boolean isJumping = input.keyPresses.jump();
            boolean isCrouching = input.keyPresses.shift();

            if (isMoving || isJumping || isCrouching) {
                resetToDefault();
                // 只复位本地表现的话，服务端还会把剩下的 hits 打完（伤害没有动画配）
                ActionServer.interruptActionToServer(InterruptReason.JUMP.ordinal());
                return true;
            }
        }
        return false;
    }

    /** 被动捕获与远端补音效：格挡成功之类的「服务端置位 → 客户端播动画」。 */
    private static void tickPassiveAndRemote(LocalPlayer player) {
        // 被动捕获：格挡成功之类的「服务端置位 → 客户端播动画」
        CharacterActionHandler handler = handlerFor(player);
        if (handler != null) {
            handler.passiveTick(player);
        }

        // 其他玩家切动作时按状态名本地补音效
        AnimationStateSync.tickRemoteStateSounds();
    }

    /** 动画时间轴倒数，含收尾接续与自然播完复位。 */
    private static void tickAnimationTimeline() {
        // 动画总时长倒数。
        //
        // ⚠️ 突进期间**整块跳过**：动画被冻在起手帧（渲染那边返回 PAUSE），时间轴就不该再走。
        //    否则「动画自然播完 → resetToDefault」会抢在到位之前发生，
        //    把这一刀连同还没发出去的服务端请求一起吞掉（攻击没伤害、冷却也不扣）。
        //    突进自己有 MAX_APPROACH_TICKS 兜底，冻结不会卡死。
        if (!approachFrozen) {
            if (animationTick > 0) {
                animationTick--;
            } else if (!DEFAULT_STATE.equals(currentState)) {
                if (followUpState != null) {
                    // 还有收尾动画要接（例如普攻最后一段 → xxx_end）：
                    // 不切回常态，直接续上，否则模型会停在上一段的最后一帧
                    startFollowUp();
                } else {
                    // 自然播完 → 回常态，并断开连击
                    resetToDefault();
                }
            }
        }
    }

    /**
     * 收尾动画至少要演多少刻才允许被常态输入打断。
     *
     * <p><b>为什么需要</b>：收尾状态原来是不带任何锁的（{@code lockFrames = 0}），
     * 于是「下一帧就结束」—— 玩家只要按着 WASD（打起来基本都按着），
     * 收尾动画<b>一帧都看不到</b>就被 {@code resetToDefault} 顶掉了，
     * 表现就是「第六段打完没有任何收尾」。
     *
     * <p>给一个短锁之后：这几刻内移动不会取消它（但输入没有被冻结，人照样能走），
     * 之后随便打断 —— 也就是「后摇可以被打断」，只是先演一下。
     * 闪避（优先级 3）和大招（4）不受这个锁限制，随时能取消。
     */
    public static final int FOLLOW_UP_LOCK_TICKS = 12;

    /** 把排队中的收尾动画接上。 */
    private static void startFollowUp() {
        String next = followUpState;
        int ticks = followUpTicks;
        int priority = currentPriority;

        followUpState = null;
        followUpTicks = 0;

        int lock = Math.min(ticks, FOLLOW_UP_LOCK_TICKS);
        LOGGER.info("[MineGenshin][收尾] 接上 '{}'（{} 刻，前 {} 刻不可被移动打断）", next, ticks, lock);
        changeState(next, priority, ticks, lock, 0);
    }

    // ---- 按键物理拦截（0 延迟定身）----

    @SubscribeEvent
    public static void onMovementInputUpdate(MovementInputUpdateEvent event) {
        if (!movementFrozen()) {
            return;
        }

        if (!(event.getEntity() instanceof LocalPlayer player)) {
            return;
        }

        // 清掉跳跃 / 潜行 / 疾跑，并把移动向量换成零
        event.getInput().keyPresses = Input.EMPTY;

        ActionInputFreeze.install(player);
    }

    /** 本地玩家重生时复位状态机（字段是静态的，不会随玩家实体重建，否则大招姿势会残留）。 */
    @SubscribeEvent
    public static void onClientPlayerRespawn(ClientPlayerNetworkEvent.Clone event) {
        resetToDefault();
    }

    // ---- 打断规则 ----

    /**
     * 硬直（执行期）是否已经生效。
     *
     * <p>准备阶段（{@link #lockDelayFrames} &gt; 0）里恒为 false —— 那段时间只是吟唱，
     * 被打断是正常玩法，不是 bug。
     */
    public static boolean actionLocked() {
        return lockDelayFrames <= 0 && actionLockFrames > 0;
    }

    /** 定身是否已经生效（准备阶段里还没开始定身，人可以走开取消吟唱）。 */
    public static boolean movementFrozen() {
        return lockDelayFrames <= 0 && movementLockFrames > 0;
    }

    /**
     * 现在能不能被一个新动作打断。
     *
     * @param requestedPriority 新动作的层级（{@code PRIO_*}）。目前只用于「大招期间不可打断」
     *                          这一条 —— 见下面为什么层级比较不再参与判定
     */
    public static boolean canInterrupt(int requestedPriority) {
        // 大招期间不可打断（绝对霸体）
        if (currentPriority >= PRIO_FINAL) {
            return false;
        }

        // ⭐ 执行期内谁都打不断 —— 连闪避、大招也不行。
        //
        // 这一段是技能「真正在发生」的部分：位移 + 动画 + 伤害点都在里面。
        // 放人进来 = 「CD 扣了、能量没了、效果没出来」，正是要修的那个 bug。
        // 想取消只能等执行期结束 —— <b>后摇才是设计上留的取消窗口</b>。
        //
        // （层级比较 requestedPriority > currentPriority 以前用在这里，现在删掉了：
        //   高阶动作能越过执行期的话，翔风剑这种「触发即位移 + 伤害」的招式
        //   照样会被闪避顶掉。准备阶段没有锁，吟唱依旧可以被高级动作取消。）
        if (actionLocked()) {
            return false;
        }

        // 准备阶段 / 后摇 / 常态：任何输入都能接上
        return true;
    }

    // ---- 动作入口（供键位调用）----

    /**
     * 左键按下。
     *
     * <p>默认按下即出普攻（原来的行为）。大剑这类持续型重击的角色由
     * {@link CharacterActionHandler#deferNormalAttackOnPress} 声明「延后」：按下先不出招，
     * 于是按住左键不会先打掉一段普攻再进重击 —— 该补的普攻由 {@link #releaseAttack} 负责。
     */
    public static void pressAttack(Player player) {
        isAttackButtonDown = true;
        attackHoldTimer = 0;
        chargedAttackTriggered = false;

        deferredNormalAttack = handlerFor(player).deferNormalAttackOnPress(player);
        if (deferredNormalAttack) {
            return;
        }

        tryAttack(player);
    }

    public static void tryAttack(Player player) {
        if (currentPriority >= PRIO_FINAL) {
            return;
        }

        if (!canInterrupt(PRIO_ATTACK)) {
            return;
        }

        handlerFor(player).attack(player);
    }

    /** @return 这次战技请求有没有真的放出去（被硬直挡住、或角色没配这个变体都算 false） */
    public static boolean trySkill(Player player, boolean longPress) {
        if (!canInterrupt(PRIO_ATTACK)) {
            return false;
        }

        return handlerFor(player).skill(player, longPress);
    }

    public static void tryDodge(Player player) {
        if (!canInterrupt(PRIO_DODGE)) {
            return;
        }

        CharacterActionHandler handler = handlerFor(player);
        if (handler != null) {
            handler.dodge(player);
        }
    }

    public static void tryUltimate(Player player) {
        if (!canInterrupt(PRIO_FINAL)) {
            return;
        }

        CharacterActionHandler handler = handlerFor(player);
        if (handler != null) {
            handler.ultimate(player);
        }
    }

    /**
     * 左键松开：交回角色的蓄力判定，并处理「延后的那一下普攻」。
     *
     * <p>大剑按住左键时按下那一刻没出普攻（见 {@link #pressAttack}）。若这次按住
     * <b>没进过重击状态</b>（松得太早、或被硬直挡下），就在这里补一次普攻，
     * 保证「单击 = 普攻」的手感还在；已经进过重击状态的就不用补了。
     */
    public static void releaseAttack(Player player) {
        // 只认「这一次按住真的按着」：换人/死亡那条清理路径会把按下标记抹掉，
        // 那种情况下不该在松手时替新角色补一刀。
        boolean deferred = deferredNormalAttack && isAttackButtonDown;

        isAttackButtonDown = false;
        int chargeTime = attackHoldTimer;
        attackHoldTimer = 0;
        boolean chargedTriggered = chargedAttackTriggered;
        chargedAttackTriggered = false;
        deferredNormalAttack = false;

        CharacterActionHandler handler = handlerFor(player);
        if (handler != null) {
            handler.releaseAttack(player, chargeTime);
        }

        if (deferred && !chargedTriggered) {
            tryAttack(player);
        }
    }

    /**
     * 战技键按下。
     *
     * <p>短按 / 长按有区别的角色（短 CD ≠ 长 CD）先不出手，等松手或按住到
     * {@link #SKILL_HOLD_TICKS} 刻再决定；没有区别的角色按下即放，保证 0 延迟。
     */
    public static void pressSkill(Player player) {
        isSkillButtonDown = true;
        skillHoldTimer = 0;
        skillHoldTriggered = false;

        if (!hasSkillHoldVariant(player)) {
            trySkill(player, false);
        }
    }

    /** 战技键松开：没触发过长按就补一个短按。 */
    public static void releaseSkill(Player player) {
        boolean wasHoldVariant = hasSkillHoldVariant(player);

        isSkillButtonDown = false;

        if (wasHoldVariant && !skillHoldTriggered) {
            trySkill(player, false);
        }

        skillHoldTimer = 0;
        skillHoldTriggered = false;
    }

    /** 这个角色的短按和长按是不是两套（CD 不同即视为两套）。 */
    private static boolean hasSkillHoldVariant(Player player) {
        PGCharacter character = currentCharacter(player);
        if (character == null) {
            return false;
        }
        return character.getSkillShortMaxCooldownTick() != character.getSkillLongMaxCooldownTick();
    }

    @Nullable
    private static PGCharacter currentCharacter(Player player) {
        PlayerCharactersAttachment attachment =
                player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        return attachment == null ? null : attachment.getCurrentCharacter();
    }

    // ---- 状态切换 / 复位 ----

    /**
     * 给当前动作排一个收尾动画：这次动作播完时自动接上它，而不是切回常态。
     *
     * <p>用于普攻最后一段这类「打完要跟一个 {@code xxx_end}」的动作 ——
     * 不接的话模型会停在最后一帧。任何后续的 {@link #changeState} / {@link #resetToDefault}
     * 都会把它清掉，所以不会在别的动作后面乱接。
     */
    public static void queueFollowUpState(String stateName, int totalTicks) {
        if (stateName == null || stateName.isEmpty() || totalTicks <= 0) {
            followUpState = null;
            followUpTicks = 0;
            return;
        }
        followUpState = stateName;
        followUpTicks = totalTicks;
        LOGGER.info("[MineGenshin][收尾] 排队 '{}'（{} 刻）", stateName, totalTicks);
    }

    /** 取消排队中的收尾动画。 */
    public static void clearFollowUpState() {
        followUpState = null;
        followUpTicks = 0;
    }

    // ---- 突进冻结 / 攻击距离 ----

    /** 突进期间冻结动画（渲染控制器会返回 PAUSE）。 */
    public static void setApproachFrozen(boolean frozen) {
        approachFrozen = frozen;
    }

    public static boolean isApproachFrozen() {
        return approachFrozen;
    }

    /** 记录这次动作的生效攻击距离（索敌/吸附用它判断「够不够得着」）。 */
    public static void setCurrentAttackRange(double range) {
        currentAttackRange = range > 0 ? range : 3.0;
    }

    /** 这次动作的生效攻击距离。 */
    public static double currentAttackRange() {
        return currentAttackRange;
    }

    /**
     * 突进到位：解冻动画，并把总时长<b>从这一刻重新起算</b>。
     *
     * <p>这是「伤害延迟按到位时刻算」的关键 —— {@code hits[].delay} 本来是相对动画起点，
     * 突进把起点推后了，所以这里把 {@code animationTick} 拨回满值，
     * 动画则从冻结的那一帧继续往下播。
     */
    public static void resumeFromApproach(int totalTicks) {
        resumeFromApproach(totalTicks, 0, 0);
    }

    /**
     * 突进到位：解冻动画 + 整段执行期（动画时间轴、硬直、定身）<b>全部从这一刻重新起算</b>。
     *
     * <p>为什么锁也要重算：<b>飞过去本身就是执行期的一部分</b>。飞了 10 刻的话，
     * 按「从按下那一刻算」的锁早就过期了，于是刚接上的动画会被一个移动输入取消，
     * 连还没结算的伤害一起丢掉（表现就是「冲刺过去，人一到就收招，没伤害」）。
     * 重算之后语义才一致：<b>到位 = 执行期开始</b>，保护时间一个不少。
     *
     * @param lockFrames    执行期硬直刻数（同 {@code changeState} 的 lockFrames）
     * @param movementLock  到位后的定身刻数
     */
    public static void resumeFromApproach(int totalTicks, int lockFrames, int movementLock) {
        approachFrozen = false;
        animationTick = Math.max(1, totalTicks);

        actionLockFrames = Math.max(0, lockFrames);
        movementLockFrames = Math.max(0, movementLock);
        lockDelayFrames = 0; // 已经飞过来了：准备阶段早就结束，到位就是执行期
    }

    /** 切到新状态，<b>不动</b>移动锁（上一状态设下的定身会继续自然倒数完）。 */
    public static void changeState(String newState, int priority, int totalTicks, int lockFrames) {
        changeState(newState, priority, totalTicks, lockFrames, -1, 0);
    }

    public static void changeState(String newState, int priority, int totalTicks, int lockFrames,
                                   int movementLockTicks) {
        changeState(newState, priority, totalTicks, lockFrames, movementLockTicks, 0);
    }

    /**
     * 切到新状态：清理旧状态残留、写入状态与计时器、按需设置移动锁与准备阶段，并自动发同步包给服务端。
     *
     * @param movementLockTicks 移动封锁刻数；传负数表示保持当前值不变
     * @param lockDelayTicks    <b>准备阶段</b>刻数：这么多刻内硬直与定身都还没生效，
     *                          所以这段时间还能被打断（吟唱）。0 = 触发即执行期
     */
    public static void changeState(String newState, int priority, int totalTicks, int lockFrames,
                                   int movementLockTicks, int lockDelayTicks) {
        changeState(newState, priority, totalTicks, lockFrames, movementLockTicks, lockDelayTicks, false);
    }

    /**
     * 同上一个重载，额外声明这段动作是<b>持续型</b>（动画循环播、视角跟随可挂）。
     *
     * @param loopAnimation 见 {@link #currentStateLoops}；只有「进状态、松手/到时才结束」
     *                      的招式才传 {@code true}（目前只有大剑的持续重击）
     */
    public static void changeState(String newState, int priority, int totalTicks, int lockFrames,
                                   int movementLockTicks, int lockDelayTicks, boolean loopAnimation) {
        LocalPlayer player = Minecraft.getInstance().player;

        // 换动作 → 上一次出手的突进 / 下坠大招作废。
        // 不取消的话会出现「已经闪避走了，半秒后突然补一刀」——
        // 它们是「上一次出手」的延续，新状态一开始就该结束。
        // （自己的起手是先 changeState 再 begin，所以不会误杀自己）
        AttackApproach.cancel();
        BurstDive.cancel();

        dispatchCleanup(currentState, player);

        // 换动作了 → 上一段排的收尾动画作废（收尾自己接自己时不走这里）
        // 打日志是因为「排了收尾却一直没播」最常见的原因就是被下一次出手顶掉，
        // 有这一行能一眼看出来（不然只能猜）。
        if (followUpState != null) {
            LOGGER.info("[MineGenshin][收尾] 排队中的 '{}' 被 '{}' 顶掉", followUpState, newState);
        }
        clearFollowUpState();

        currentState = newState;
        currentPriority = priority;
        animationTick = totalTicks;
        currentStateLoops = loopAnimation;
        actionSequence++;

        actionLockFrames = lockFrames;
        lockDelayFrames = Math.max(0, lockDelayTicks);

        if (movementLockTicks >= 0) {
            movementLockFrames = movementLockTicks;
        }

        if (priority >= PRIO_ATTACK) {
            comboWindowFrames = 30;
        }

        if (player != null) {
            // 本地 0 延迟播放；服务端会给其他玩家播且排除自己，所以不会重音
            // （远端玩家由各自的客户端按同一个状态名查 CharacterAnimations.soundForState）
            playLocalSound(player, soundForState(player, newState));
            sendStateToServer(newState, totalTicks);
        }
    }

    /** 回常态。清空延迟任务队列并通知服务端把远端表现也复位。 */
    public static void resetToDefault() {
        LocalPlayer player = Minecraft.getInstance().player;

        // 回常态 = 这次出手结束了：还在突进/下坠的话立刻停
        // （不走到位回调，也不补发攻击请求）
        AttackApproach.cancel();
        BurstDive.cancel();

        dispatchCleanup(currentState, player);

        currentState = DEFAULT_STATE;
        currentPriority = PRIO_NORMAL;
        animationTick = 0;
        currentStateLoops = false;
        actionLockFrames = 0;
        lockDelayFrames = 0;
        movementLockFrames = 0;

        clearFollowUpState();
        ClientTaskQueue.clear();

        if (player != null) {
            sendStateToServer(DEFAULT_STATE, 0);
        }
    }

    private static void sendStateToServer(String stateName, int totalTicks) {
        LocalPlayer player = Minecraft.getInstance().player;

        // 连接还没建立/已断开时跳过（例如重生瞬间被状态机复位触发）
        if (player == null || Minecraft.getInstance().getConnection() == null) {
            return;
        }

        NetworkManager.sendAnimationStateToServer(stateName, totalTicks);
    }

    /** 把状态被覆盖的消息交给当前角色的动作编排，用于清理残留。 */
    private static void dispatchCleanup(String stateToClean, @Nullable Player player) {
        if (player == null || DEFAULT_STATE.equals(stateToClean)) {
            return;
        }

        CharacterActionHandler handler = handlerFor(player);
        if (handler != null) {
            handler.onStateInterrupted(stateToClean, player);
        }
    }

    // ---- 音效 ----

    /** 本地瞬间播放音效（音量/音高默认 1）。 */
    public static void playLocalSound(@Nullable Player player, @Nullable String soundId) {
        playLocalSound(player, soundId, 1.0F, 1.0F);
    }

    /**
     * 本地瞬间播放音效。
     *
     * <p><b>不要求音效在注册表里</b>：角色音效是数据文件定义的
     * （{@code character/<角色id>/sounds.json}），没有走过 {@code DeferredRegister}，
     * 所以查不到时现造一个 {@link SoundEvent#createVariableRangeEvent} ——
     * 客户端按 location 去声音注册表找文件，能找到就响。
     * 两条路都没有时静默跳过，不会报错、也不会刷屏。
     *
     * @param volume 音量（0.01 起，0 会被原版当成不播）
     * @param pitch  音高（0.01 起）
     */
    public static void playLocalSound(@Nullable Player player, @Nullable String soundId,
                                      float volume, float pitch) {
        if (player == null || soundId == null || soundId.isEmpty()) {
            return;
        }

        Identifier id = Identifier.tryParse(soundId);
        if (id == null) {
            return;
        }

        SoundEvent soundEvent = BuiltInRegistries.SOUND_EVENT.getValue(id);
        if (soundEvent == null) {
            soundEvent = SoundEvent.createVariableRangeEvent(id);
        }

        player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), soundEvent,
                SoundSource.PLAYERS, Math.max(0.01F, volume), Math.max(0.01F, pitch), false);
    }

    // ---- 内部工具 ----

    /** 取本地玩家当前角色的动作编排；没戴角色饰品时返回 null。 */
    @Nullable
    private static CharacterActionHandler handlerFor(Player player) {
        return CharacterActions.getFor(player);
    }

    /** 取本地玩家当前角色某个状态的音效 id。 */
    @Nullable
    private static String soundForState(Player player, String stateName) {
        CharacterAnimations animations = CharacterActions.animationsFor(player);
        return animations == null ? null : animations.soundForState(stateName);
    }

}
