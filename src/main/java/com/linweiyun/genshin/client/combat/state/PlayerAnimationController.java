package com.linweiyun.genshin.client.combat.state;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationPoint;
import com.geckolib.animation.state.AnimationTest;
import com.linweiyun.genshin.core.character.CharacterHelper;
import com.linweiyun.genshin.core.system.combat.animation.action.CharacterActions;
import com.linweiyun.genshin.core.system.combat.animation.animatable.IPlayerAnimatableProxy;
import com.linweiyun.genshin.core.system.combat.animation.config.CharacterAnimations;
import com.linweiyun.genshin.core.system.combat.animation.config.FirstPersonAnims;
import com.linweiyun.genshin.core.system.combat.animation.config.LocomotionAnims;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import org.slf4j.Logger;

/**
 * 玩家动画控制器 —— 决定「这一帧该播哪个动画」。
 *
 * <p>只放<b>所有角色通用</b>的基础/常态决策（站/走/跑/跳/落/飞/泳 + 落地缓冲、急停
 * 这两段一次性过渡）与动作动画的选择 —— 这里不认识任何具体角色，
 * 角色差异全部走
 * {@link CharacterAnimations} 数据（动画名、特殊动画名单、过渡刻数）。
 *
 * <h2>两种状态来源</h2>
 * <ul>
 *   <li><b>本地玩家</b>：读 {@link ActionStateMachine#currentState}，按键当帧就出动画（0 延迟）。</li>
 *   <li><b>其他玩家</b>：读同步过来的动画状态附件。</li>
 * </ul>
 *
 * <h2>智能顺切</h2>
 * 常态动画之间平滑过渡 {@code exitTransitionTicks} 刻；只要涉及特殊动作动画，
 * 过渡时间归零硬切，避免动作姿势被插值混形。
 *
 * <h2>常态里也有「一次性的那两下」</h2>
 * 纯粹的常态循环（站/走/跑/跳/落）之外，还有两段<b>只播一次</b>的收势：
 * 「从空中落到地面」的落地缓冲、和「疾跑停下来」的急停。它们不是新状态机，
 * 而是 {@code pickLocomotionRaw} 里的一对沿判断 —— 见
 * {@link #LOCO_TRANSIENT} 与 {@code LocomotionAnims.OneShot}。
 * 角色没配这两条时（{@code LocomotionAnims#landing()} 为 null）行为跟以前完全一样。
 *
 * <h2>飞行三态</h2>
 * 接了飞行素材的角色（{@code LocomotionAnims#fly()} 非 null）飞起来时不再播跳/落，
 * 而是按竖直位移在「上升 / 水平巡航 / 下降」三条循环里挑一条 —— 见 {@link #pickLocomotionRaw}。
 *
 * <h2>动画保护</h2>
 * 每一个要播的动画名都先过 {@link AnimationAvailability}：角色动画文件里没有这个名字时
 * <b>保持当前动画不动</b>，绝不切进空状态 —— 空状态会让模型露出原始姿态（部件、特效全露、
 * 角色呆站着），而且要等到下一个存在的动画才会恢复。常态动画缺失时退到 idle。
 *
 * <h2>与参考2 的差异</h2>
 * 参考2 在构造控制器时就把 {@code CharacterAnimations} 固定下来；本项目支持中途换角色，
 * 所以每帧按玩家当前角色现查（{@link CharacterActions#animationsFor(Player)}）。
 */
public final class PlayerAnimationController {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.ANIMATION);

    private PlayerAnimationController() {
    }

    /**
     * 建主控制器 —— 每帧问一次「这一帧该播哪个动画」。
     *
     * <p>控制器名 {@code movement_controller} 与 GeckoLib 里注册的名字一致。
     *
     * @param animatable 角色动画代理对象
     */
    public static <T extends GeoAnimatable & IPlayerAnimatableProxy> AnimationController<T> create(T animatable) {
        return new AnimationController<T>("movement_controller", 5, state -> {
            Player player = animatable.getPlayerEntity();
            return handle(state, player);
        });
    }

    // ==================== 常态里的一次性过渡 ====================

    /**
     * 落地分档的两条下坠速度门槛（格/刻，取的都是离地前最后一刻的竖直位移）。
     *
     * <p>{@code 0.1} 附近是走台阶下一格的速度，{@code 0.45} 往上才是真跳起来过。
     * 角色只接了一档落地时（{@link LocomotionAnims#landingLight()} 为 null）
     * 用 {@link #LANDING_MIN_FALL_SPEED} 这老门槛，行为跟以前一模一样；
     * 接了两档时按下面的分法：真跳/摔下去给重档，踩台阶、缓降着地给轻档，
     * 慢慢蹭到地上（几乎没下坠）两档都不播。
     */
    private static final double LANDING_MIN_FALL_SPEED = 0.125;

    /** 两档都接时，「重落地」的下坠速度门槛：跳跃着地大致落在 0.6~0.8。 */
    private static final double LANDING_HEAVY_FALL_SPEED = 0.45;

    /** 两档都接时，「轻落地」的下坠速度门槛：比老门槛低一半，挪一格台阶也算数。 */
    private static final double LANDING_LIGHT_FALL_SPEED = 0.06;

    /** 飞行三态用来区分上升 / 水平 / 下降的竖直位移门槛（格/刻）。 */
    private static final double FLIGHT_VERTICAL_SPEED = 0.02;

    /**
     * 每个玩家一份的常态过渡短状态。
     *
     * <p><b>为什么键用实体本身、值用 {@code tickCount} 计时</b>：这个控制器是
     * <b>每渲染帧</b>走一遍的（GeckoLib 5 的 {@code performRenderPass}），
     * 自己数「调了几次」在高帧率下会比游戏刻快好几倍；而 {@code tickCount}
     * 本地玩家和其他玩家都是每游戏刻 +1，所以用它当时间轴，
     * 帧率和联机两边都自然对齐。
     *
     * <p>{@link WeakHashMap}：玩家退出/换维度后实体没别处引用，条目自己会被回收，
     * 不会像普通 HashMap 那样把旧实体一直攒着。
     */
    private static final Map<Player, LocoTransient> LOCO_TRANSIENT = new WeakHashMap<>();

    /**
     * 玩家 → 上一次处理时看到的角色 id（用来发现「换角色」）。
     *
     * <h2>为什么换角色必须重置控制器</h2>
     * GeckoLib 的动画 timeline 是<b>按 {@code RawAnimation} 是否相等</b>决定要不要重烘的
     * （{@code AnimationController#checkControllerState}：只有 RawAnimation 变了才走
     * {@code initializeNewAnimation}）。而换角色换的是<b>模型文件</b>，动画名往往没变
     * （都叫 {@code run}），于是控制器会继续拿<b>上一个模型</b>烘出来的 timeline 去驱动新模型：
     * <ul>
     *   <li>新模型的骨骼名字对不上 → 那几根骨骼一个通道都没被写，看起来就是<b>静默不动</b>；</li>
     *   <li>名字碰巧有交集 → 播的其实是<b>上一个角色的动画</b>（用户实测：B 缺 run 却「正常在跑」）。</li>
     * </ul>
     * 所以角色 id 一变就 {@code reset()}：timeline / animationPoint / 当前 RawAnimation 全丢掉，
     * 同一帧里 {@code setAndContinue} 会按<b>新模型</b>重新烘一条 —— 动画名与过渡时刻都按换后角色来。
     */
    private static final Map<Player, String> LAST_CHARACTER_ID = new WeakHashMap<>();

    /**
     * 排查用：玩家 → 上一次真正播出去的动画名 / 上一次看到的动作状态名。
     *
     * <p>用户口径（2026-09-27）：「闪的是突然出现一个残影又消失」+「加 logger，动画开始和结束
     * （包括被切换导致的结束）打印一个日志」。所以这里在<b>本机玩家</b>身上打两种行：
     * <ul>
     *   <li>{@code 动画 A → B}：一条动画被换掉（B 为「无」就是这次没有可用动画、相当于自然结束）；</li>
     *   <li>{@code 状态 X → Y}：动作状态名变了 —— 一次「一闪而过」多半能在这里看到它到底进了什么状态。</li>
     * </ul>
     * 只在 {@code LOGGER.isInfoEnabled()} 且是本机玩家时打，联机时不会刷屏；
     * 想静音把 {@code LogGroup.ANIMATION} 关掉即可。
     */
    private static final Map<Player, String> LOG_LAST_ANIMATION = new WeakHashMap<>();
    private static final Map<Player, String> LOG_LAST_STATE = new WeakHashMap<>();

    /** 上一次打「上一帧没有动画在驱动」那行时的游戏刻（同一刻只打一次）。 */
    private static int lastEmptyFrameTick = Integer.MIN_VALUE;

    /** 一个玩家的常态过渡状态：上一次的滞空事实 + 当前正在播的一次性过渡。 */
    private static final class LocoTransient {
        /** 上一帧是不是在空中。 */
        boolean airborne;
        /** 离地期间最后一刻的竖直位移（用来判断落地是「小台阶」还是「真摔下来」）。 */
        double fallSpeed;
        /** 地面上「疾跑中而且在动」的事实，用来抓「刚刚停下来的那一帧」。 */
        boolean wasRunning;
        /** 正在播的一次性过渡；null = 没有。 */
        @Nullable LocomotionAnims.OneShot oneShot;
        /** 上面那一段是从哪个游戏刻开始播的。 */
        int oneShotStartTick;
    }

    /**
     * 建一个「只播常态（idle）」的控制器 —— 角色配置页里的模型预览用。
     *
     * <h2>为什么预览不能复用玩家自己那条控制器</h2>
     * {@link PlayerAnimationController#create} 选出来的动画跟着玩家的<b>真实状态</b>走：
     * 开配置页之前刚打完一招，
     * 动作动画是 {@code thenPlayAndHold}（播完停在最后一帧等状态机接手），预览里就会一直
     * 定在那一帧的攻击姿势；在水里、在天上同理。预览要的是「站着」这一件事，
     * 所以给它一条只认 idle 的控制器，与世界的动画互不影响（关掉页面也不用做任何收尾）。
     *
     * <p>动画时间不用自己推：GeckoLib 5 的进度是按渲染刻算的，只要控制器每帧被走到
     * （{@code performRenderPass} 里会走），idle 自己就会动起来。
     *
     * @param animatable 预览专用的动画代理对象
     */
    public static <T extends GeoAnimatable & IPlayerAnimatableProxy> AnimationController<T> createIdleOnly(T animatable) {
        return createPreview(animatable, null);
    }

    /**
     * 建一条「只播点名那条动画」的预览控制器 —— 角色配置页想给某个角色来一条专属姿势时用
     * （点哪条由调用方给，底层不认识）。
     *
     * <p>点名的那条在<b>当前角色的动画文件里不存在</b>时退回常态（idle）—— 换角色预览、
     * 或者模型还没补这条动画时，看到的是一张正常的站姿，而不是原始姿态（部件、特效全露）。
     * 连 idle 都没有就保持当前帧。所以调用方只管点名，不用自己判存在性。
     *
     * <p>点名的那条按 {@code thenLoop} 播：YSM 转过来的姿势动画（例如 {@code extra48}）
     * 是一条循环的静态姿势，播完停在最后一帧与循环它在画面上等价，但 {@code thenLoop}
     * 少一次「有没有播完」的状态判断。
     *
     * @param animatable    预览专用的动画代理对象
     * @param animationName 点名的动画名；{@code null} = 只播常态（等价于 {@link #createIdleOnly}）
     */
    public static <T extends GeoAnimatable & IPlayerAnimatableProxy> AnimationController<T> createPreview(
            T animatable, @Nullable String animationName) {
        return new AnimationController<T>("preview_idle", 5, state -> {
            Player player = animatable.getPlayerEntity();
            if (player == null) {
                return PlayState.STOP;
            }

            RawAnimation target = resolvePreviewAnimation(player, animationName);
            if (target == null) {
                // 点名的和 idle 都没有：保持当前帧，别闪回原始姿态
                return state.controller().getCurrentAnimationPoint() == null
                        ? PlayState.STOP : PlayState.CONTINUE;
            }

            // 首帧也要硬切：带过渡进来会从「原始姿态」插值，部件与特效会先全露一遍
            state.controller().setTransitionTicks(0);
            return state.setAndContinue(target);
        });
    }

    /** 预览这一帧该播哪条：点名的优先，模型里没有就退到角色登记的 idle；都没有返回 {@code null}。 */
    @Nullable
    private static RawAnimation resolvePreviewAnimation(Player player, @Nullable String animationName) {
        if (animationName != null && AnimationAvailability.existsFor(player, animationName)) {
            return RawAnimation.begin().thenLoop(animationName);
        }
        RawAnimation idle = CharacterActions.animationsFor(player).locomotion().idle();
        String idleName = targetName(idle);
        return idleName != null && AnimationAvailability.existsFor(player, idleName) ? idle : null;
    }

    private static <T extends GeoAnimatable> PlayState handle(AnimationTest<T> state, @Nullable Player player) {
        if (player == null) {
            return PlayState.STOP;
        }

        AnimationController<T> controller = state.controller();
        CharacterAnimations animations = CharacterActions.animationsFor(player);

        // 排查用：上一帧其实"没有动画在驱动"吗？
        // GeckoLib 在 playState == STOP 时会跳过 initializeNewAnimation，模型就以原始姿态画出去 ——
        // 用户实机那帧的特征（站着、木偶朝左、武器挂在另一个高度）正是原始姿态。这里只在
        // 本机玩家、且上一帧 timeline / animationPoint 为空时打一行，行里带上当时的动画名。
        if (player == Minecraft.getInstance().player && LOGGER.isInfoEnabled()
                && player.tickCount != lastEmptyFrameTick
                && (controller.getTimeline() == null || controller.getCurrentAnimationPoint() == null)) {
            lastEmptyFrameTick = player.tickCount;
            LOGGER.info("上一帧没有动画在驱动骨骼（上一帧动画={}，状态={}，刻={}）",
                    currentAnimationName(controller), AnimationStateSync.stateOf(player), player.tickCount);
        }

        // 0. 换角色 = 换模型文件：先把上一个模型的 timeline 丢掉（见 LAST_CHARACTER_ID 的注释）。
        //    放在最前面 —— 这一帧后面的动画名 / 过渡时刻都按换后角色算，重置也在同一帧生效。
        resetOnCharacterChange(player, controller);

        // 1. 取目标状态：本地玩家读状态机，其他玩家读同步变量
        boolean isLocalPlayer = player == Minecraft.getInstance().player;
        String targetAnim = AnimationStateSync.stateOf(player);
        boolean hasActionState = targetAnim != null && !targetAnim.isEmpty()
                && !ActionStateMachine.DEFAULT_STATE.equals(targetAnim);

        // 2. 计算运动状态
        double movedX = player.getX() - player.xo;
        double movedY = player.getY() - player.yo;
        double movedZ = player.getZ() - player.zo;

        boolean isMoving;
        if (isLocalPlayer && player instanceof LocalPlayer localPlayer) {
            // 本地玩家用原始输入判定：更灵敏，且动作锁期间视为不动
            isMoving = ActionStateMachine.actionLockFrames <= 0
                    && localPlayer.input.getMoveVector().lengthSquared() > 1.0E-5f;
        } else {
            isMoving = (movedX * movedX + movedZ * movedZ) > 0.00005;
        }

        // 3. 选出这一帧该播的动画；null = 没有可用动画，保持当前帧不动
        RawAnimation target = hasActionState
                ? pickAction(player, targetAnim,
                        isLocalPlayer && ActionStateMachine.currentStateLoops())
                : pickLocomotion(player, animations.locomotion(), isMoving, movedX, movedY, movedZ);

        // 3.5 排查用：动画与状态的每一次「起 / 止 / 被切走」都留一行日志
        logAnimationFlow(player, target, targetAnim, hasActionState);

        // 动作状态期间常态那条路根本不走：把「疾跑中」「正在播收势」这两个事实清掉，
        // 否则打完一套动作回到常态时，会拿几分钟前的「他刚才在跑」去补一段急停。
        // 滞空标记（airborne）留着 —— 空中放完招落地，落地缓冲还是该给。
        if (hasActionState) {
            suspendLocomotionTransient(player);
        }

        if (target == null) {
            // 有东西在播就继续播，避免闪回原始姿态
            return controller.getCurrentAnimationPoint() == null ? PlayState.STOP : PlayState.CONTINUE;
        }

        // 突进期间：把动作暂停在起手那一帧，贴到目标再解冻继续播。
        //
        // ⚠️ 只对**本机玩家**生效：`isApproachFrozen` 是全局（一份）状态，
        // 而其他玩家的控制器也会走这里 —— 不加这个判断的话，只要名字撞上
        // （比如双方都在 attack_1），旁边的人也会跟着一起冻住。
        if (isLocalPlayer && ActionStateMachine.isApproachFrozen()
                && targetName(target).equals(currentAnimationName(controller))) {
            return PlayState.PAUSE;
        }

        // 4. 智能顺切：决定本次切换用多少刻过渡
        //
        // GeckoLib 5 的非 triggered 路径会直接读 transitionTicks 字段
        // （AnimationController#initializeNewAnimation），所以这里设的值当帧就生效。
        boolean isTargetSpecial = animations.specialAnims().contains(targetName(target));
        String currentPlayingAnim = currentAnimationName(controller);
        boolean isCurrentlySpecial = animations.specialAnims().contains(currentPlayingAnim);

        // 从「一次性过渡」（落地缓冲 / 急停）回到循环动画：把控制器整个重置一遍。
        //
        // 用户实机：**不跳不闪，跳一下之后一直闪**；姿态探针显示那一帧的数值正好是
        // `run` 时间轴的**起点姿势**（`Robot_Root` 归零、`Root` 在 catmullrom 过冲那一档），
        // 也就是**时间轴在反复重启**。一次性动画是 `thenPlayAndHold`（停在最后一帧），
        // 它留下的 animationPoint / 过渡点会被下一段循环时间轴当成"起点"反复混进来。
        // reset() 把时间轴、animationPoint、过渡点一起丢掉（见 GeckoLib#AnimationController#reset），
        // 下一句 setAndContinue 会按**当前模型与动画名**重建一条干净的循环时间轴。
        if (previousWasOneShot(animations, currentPlayingAnim)) {
            controller.reset();
        }

        // 还没有任何动画在播（首次渲染 / 控制器刚被重置）时也要硬切：
        // 带过渡的切换会从「原始姿态」插值进来，那几帧部件和特效会全部露出来。
        boolean nothingPlaying = controller.getCurrentAnimationPoint() == null
                || controller.getCurrentTimelineTime() < 0;

        if (isTargetSpecial || nothingPlaying || previousWasOneShot(animations, currentPlayingAnim)
                || (isCurrentlySpecial && !hasActionState)) {
            // 进入动作 / 从定格姿势回常态：硬切，避免姿势被插值混形
            controller.setTransitionTicks(0);
        } else {
            controller.setTransitionTicks(animations.exitTransitionTicks());
        }

        return state.setAndContinue(target);
    }

    // ==================== 选动画 ====================

    /**
     * 上一条动画是不是「播一次就停」的那种（落地缓冲 / 急停）——从它们回到循环动画时必须<b>硬切</b>。
     *
     * <p>用户实机（2026-09-27）：「直接跑不闪；**跳一下之后再跑就一直闪**」。
     * 那一帧的姿势在 `Root` / `Robot_Root` 上是**两个姿势来回跳**（跑的姿态 ⇄ 落地那一档），
     * 正是「带过渡地切回循环」被反复重放的样子：落地是 `thenPlayAndHold`（停在最后一帧），
     * 从它切回 `run` 时走的是 `exitTransitionTicks` 的**插值**，而 GeckoLib 每帧会按
     * 当前动画重算时间轴 —— 于是一圈一圈地混形，看上去就是一帧的"另一个人"。
     * 硬切（过渡 0 刻）就没有这层混形。
     */
    private static boolean previousWasOneShot(CharacterAnimations animations, @Nullable String previousName) {
        if (previousName == null) {
            return false;
        }
        final LocomotionAnims loco = animations.locomotion();
        for (LocomotionAnims.OneShot oneShot : new LocomotionAnims.OneShot[]{
                loco.landing(), loco.landingLight(), loco.runStop()}) {
            if (oneShot != null && previousName.equals(targetName(oneShot.animation()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 打「动画起止 / 状态切换」的日志（排查一闪而过的残影，见 {@link #LOG_LAST_ANIMATION}）。
     *
     * <p>只对本机玩家、且日志组开着时打。
     */
    private static void logAnimationFlow(Player player, @Nullable RawAnimation target,
                                         String state, boolean hasActionState) {
        if (player != Minecraft.getInstance().player || !LOGGER.isInfoEnabled()) {
            return;
        }

        String stateName = state == null || state.isEmpty() ? ActionStateMachine.DEFAULT_STATE : state;
        String previousState = LOG_LAST_STATE.put(player, stateName);
        if (!Objects.equals(previousState, stateName)) {
            LOGGER.info("状态 {} → {}（刻={}）", previousState, stateName, player.tickCount);
        }

        String name = target == null ? null : targetName(target);
        String previous = LOG_LAST_ANIMATION.get(player);
        if (Objects.equals(previous, name)) {
            return;
        }
        if (name == null) {
            LOG_LAST_ANIMATION.remove(player);
        } else {
            LOG_LAST_ANIMATION.put(player, name);
        }
        LOGGER.info("动画 {} → {}（状态={}，刻={}，动作状态={}）",
                previous == null ? "无" : previous, name == null ? "无（这次没有可用动画）" : name,
                stateName, player.tickCount, hasActionState);
    }

    /**
     * 这个玩家换角色了就把控制器整个重置（返回 {@code true}）。
     *
     * <p>第一次看到某个玩家（{@code previous == null}）不重置：那时还没有旧 timeline 要丢，
     * 而且首帧就该走「什么都没有 → 硬切」那条路。
     *
     * <p>顺带把常态过渡的短状态（滞空事实 / 正在播的那次落地缓冲或急停）也丢掉 ——
     * 那是上一个角色身上的事，带到新角色身上会补一段莫名其妙的收势。
     */
    private static boolean resetOnCharacterChange(Player player, AnimationController<?> controller) {
        String characterId = CharacterHelper.getActiveCharacterId(player);
        String previous = LAST_CHARACTER_ID.get(player);
        if (Objects.equals(previous, characterId)) {
            return false;
        }
        LAST_CHARACTER_ID.put(player, characterId);
        if (previous == null) {
            return false;
        }
        controller.reset();
        LOCO_TRANSIENT.remove(player);
        return true;
    }

    /**
     * 动作动画：播一次，然后<b>停在最后一帧</b>等状态机接手。
     *
     * <p><b>为什么是 {@code thenPlayAndHold} 而不是 {@code thenPlay}</b>：
     * 动作长度和状态时长是对齐的（比如 {@code attack_1} 动画 2.0 秒、这一段也是 40 刻），
     * 用 {@code thenPlay} 的话动画会在最后一刻「播完即失效」，
     * 而状态机要到下一帧才切走 —— 中间那一帧模型没有任何动画在驱动，
     * 就露出<b>原始姿态</b>（所有部件与特效全开、角色呆站），闪一下很难看。
     *
     * <p>改成「播完停在最后一帧」后，最后一帧会一直撑到状态机切到下一个状态
     * （收尾动画 / 常态），中间不存在空档。副作用正好是作者想要的：
     * 「hold on last frame」型的动画（比如 {@code air_attack_long}）现在真的会保持住。
     *
     * <p>角色没有这个动画时返回 {@code null}（调用方会保持当前帧不动）。
     *
     * @param loop 这一段动作是不是<b>持续型</b>（大剑的持续重击：按住期间一直转）。
     *             {@code true} 用 {@code thenLoop} 让动画循环；{@code false}（其余所有招式）
     *             用 {@code thenPlayAndHold} 播完停在最后一帧。见
     *             {@code ActionStateMachine#currentStateLoops()}。
     */
    @Nullable
    private static RawAnimation pickAction(Player player, String animationName, boolean loop) {
        // 第一人称优先试 fp_ 版；没写就照播普通版（复用模式，由机位把角度摆正）
        FirstPersonAnims firstPerson = CharacterActions.animationsFor(player).firstPerson();
        if (firstPerson.enabled() && isFirstPerson(player)) {
            String fpName = firstPerson.resolve(animationName, true);
            if (fpName != null && AnimationAvailability.existsFor(player, fpName)) {
                return loop
                        ? RawAnimation.begin().thenLoop(fpName)
                        : RawAnimation.begin().thenPlayAndHold(fpName);
            }
        }

        if (!AnimationAvailability.existsFor(player, animationName)) {
            return null;
        }
        return loop
                ? RawAnimation.begin().thenLoop(animationName)
                : RawAnimation.begin().thenPlayAndHold(animationName);
    }

    /** 这一帧是不是第一人称视角。 */
    public static boolean isFirstPerson(Player player) {
        Minecraft minecraft = Minecraft.getInstance();
        return player == minecraft.player
                && minecraft.options != null
                && minecraft.options.getCameraType().isFirstPerson();
    }

    /** 当前玩家（本地）的第一人称配置；非本地或没开就返回 {@link FirstPersonAnims#DISABLED}。 */
    public static FirstPersonAnims firstPersonFor(Player player) {
        if (!isFirstPerson(player)) {
            return FirstPersonAnims.DISABLED;
        }
        return CharacterActions.animationsFor(player).firstPerson();
    }

    /**
     * 常态运动状态机。
     *
     * <p>选出来的动画同样要先确认存在；缺失时退到 idle，idle 也没有就返回 {@code null}
     * （保持当前动画），避免「冲刺结束/上岸瞬间」这类切换把模型打成原始姿态。
     */
    @Nullable
    private static RawAnimation pickLocomotion(Player player, LocomotionAnims loco,
                                               boolean isMoving,
                                               double movedX, double movedY, double movedZ) {
        RawAnimation picked = pickLocomotionRaw(player, loco, isMoving, movedX, movedY, movedZ);
        return existingOrIdle(player, loco, picked);
    }

    /**
     * 本刻位移投影到<b>身体朝向</b>上的点积：正 = 人在朝自己正前方走，负 = 朝背后走。
     *
     * <h2>为什么参照系是身体朝向，而不是视线方向</h2>
     * 本模组第三人称下身体永远朝<b>实际移动方向</b>（见
     * {@code LivingEntityTickHeadTurnMixin}），所以按 S 时人是<b>正对镜头</b>走回来的。
     * 而「位移 vs 视线」的点积在后退时照旧是负的（相对镜头就是向后走），
     * 拿它选动画就会给一个正面朝你走来的人播「后退走路」—— 腿倒着迈（月步）。
     * 换身体朝向当参照就没有这个矛盾：朝哪走就播哪边的动画。
     *
     * <p>{@code yBodyRot} 就是模型朝向（{@code CharacterRenderDispatcher} 读它渲染），
     * 所以动画方向和画出来的模型方向天然一致；转身那几刻它自己会从旧朝向插值过去，
     * 于是「正在转身」会自然掠过一下反向动画，不必额外处理。
     *
     * <p>水平前向的换算：MC 的 yaw 0 朝 +Z、逆时针为正，所以单位前向 = (-sin, +cos)。
     */
    private static double bodyForwardDot(Player player, double movedX, double movedZ) {
        float bodyYawRad = player.yBodyRot * ((float) Math.PI / 180.0F);
        double forwardX = -Mth.sin(bodyYawRad);
        double forwardZ = Mth.cos(bodyYawRad);
        return movedX * forwardX + movedZ * forwardZ;
    }

    private static RawAnimation pickLocomotionRaw(Player player, LocomotionAnims loco,
                                                  boolean isMoving,
                                                  double movedX, double movedY, double movedZ) {
        if (player.isSleeping()) {
            return loco.sleep();
        }

        if (player.onClimbable()) {
            return loco.climb();
        }

        if (player.isInWater()) {
            if (player.isSwimming()) {
                return loco.swim();
            }

            if (isMoving) {
                // 用「移动方向 vs 身体朝向」的点积区分前进/后退，理由见 bodyForwardDot
                return bodyForwardDot(player, movedX, movedZ) < -0.01
                        ? loco.waterWalkBack() : loco.waterWalk();
            }

            return loco.waterIdle();
        }

        LocoTransient transientState = LOCO_TRANSIENT.computeIfAbsent(player, key -> new LocoTransient());

        // ---- 飞行：水平巡航 / 上升 / 下降，三条各自成段 ----
        //
        // 放在水里之后、地面那一套之前：飞着的时候 onGround 大多为 false，
        // 要是让它先落到下面的空中分支，飞起来就永远在播 jump / jump_down 了。
        if (isFlying(player) && loco.fly() != null) {
            // 飞起来就把「地面上攒的事实」全清掉：飞行途中不算滞空（否则解除飞行、
            // 脚一沾地就会误触发一段落地缓冲），也不该带着地面的急停上天。
            transientState.airborne = false;
            transientState.oneShot = null;
            transientState.wasRunning = false;
            transientState.fallSpeed = 0;

            if (movedY > FLIGHT_VERTICAL_SPEED) {
                return loco.flyUp() != null ? loco.flyUp() : loco.fly();
            }
            if (movedY < -FLIGHT_VERTICAL_SPEED) {
                return loco.flyDown() != null ? loco.flyDown() : loco.fly();
            }
            return loco.fly();
        }

        // ---- 空中：起跳 / 下落 ----
        if (!player.onGround()) {
            transientState.airborne = true;
            transientState.fallSpeed = movedY;
            // 又飞起来了：落地缓冲 / 急停立刻作废，别带着一段地面收势上天
            transientState.oneShot = null;
            transientState.wasRunning = false;
            return movedY > 0.01 ? loco.jump() : loco.jumpDown();
        }

        // ---- 这一帧刚落地：接一段落地缓冲 ----
        //
        // 判定用「上一帧还在空中」这个沿，而不是看当前竖直速度 —— 落到地上的那一帧
        // movedY 已经归零，现算现判永远不成立。下坠速度取的是离地前最后一刻存的，
        // 所以走台阶（慢）不触发、真跳一下（快）才触发。
        if (transientState.airborne) {
            transientState.airborne = false;
            LocomotionAnims.OneShot landing = pickLanding(loco, transientState.fallSpeed);
            if (landing != null) {
                beginOneShot(transientState, landing, player.tickCount);
            }
        }

        // ---- 一次性过渡（落地缓冲 / 急停）还该不该继续播 ----
        //
        // 判定必须放在「在地面且在动」那一堆分支<b>之上</b>：落地缓冲是「落地那一下的
        // 读感」，跳跃前进落地时方向键还按着，落点那帧就同时满足 isMoving ——
        // 要是等到下面才处理，移动分支会先把它清掉，等于落地缓冲从来没接上过。
        if (holdingOneShot(transientState, loco, isMoving, player)) {
            return transientState.oneShot.animation();
        }

        // ---- 在地面且在动：蹲走 / 跑 / 后退 / 走 ----
        if (isMoving) {
            double dotProduct = bodyForwardDot(player, movedX, movedZ);

            if (player.isCrouching()) {
                transientState.wasRunning = false;
                return loco.crouchWalk();
            }
            if (player.isSprinting()) {
                // 收势（急停）一动起来就让给跑；落地缓冲会不会被让掉由上一步判，
                // 那边特意留着它 —— 见 holdingOneShot 的注释。
                transientState.wasRunning = true;
                return loco.run();
            }

            transientState.wasRunning = false;
            if (dotProduct < -0.01) {
                return loco.walkBack();
            }

            return loco.walk();
        }

        // 走到这里 oneShot 一定是空的：上面那道检查要么已经判定「该播」并 return 了，
        // 要么把它清掉了 —— 所以下面的分支不必再自己清它。
        if (player.isCrouching()) {
            transientState.wasRunning = false;
            return loco.crouch();
        }

        // ---- 站住这一帧：刚从疾跑停下来 → 播一段急停 ----
        if (transientState.wasRunning) {
            transientState.wasRunning = false;
            if (loco.runStop() != null) {
                beginOneShot(transientState, loco.runStop(), player.tickCount);
                // 这一帧刚起的急停立刻交出去，别等到下一帧才显示（否则会闪一帧 idle）
                return transientState.oneShot.animation();
            }
        }

        return loco.idle();
    }

    /**
     * 这一下落地该播哪一档（两档都没接 / 只是慢慢蹭到地上就返回 {@code null}）。
     *
     * <p>只接了一档的老角色走 {@link #LANDING_MIN_FALL_SPEED} 这条老门槛，行为不变；
     * 接了两档的角色真跳给重档、挪台阶给轻档。
     *
     * @param fallSpeed 离地前最后一刻的竖直位移（负数 = 在往下掉）
     */
    @Nullable
    private static LocomotionAnims.OneShot pickLanding(LocomotionAnims loco, double fallSpeed) {
        if (loco.landingLight() == null) {
            return loco.landing() != null && fallSpeed <= -LANDING_MIN_FALL_SPEED ? loco.landing() : null;
        }
        if (loco.landing() != null && fallSpeed <= -LANDING_HEAVY_FALL_SPEED) {
            return loco.landing();
        }
        return fallSpeed <= -LANDING_LIGHT_FALL_SPEED ? loco.landingLight() : null;
    }

    /**
     * 这一帧该不该走飞行三态。
     *
     * <p>创造飞行只有<b>本地玩家</b>读得到（{@code abilities.flying} 不下发给别的客户端），
     * 所以联机时飞行三态目前只在本地玩家身上严格生效；鞘翅（{@code isFallFlying}）
     * 两边都有这个标记，远端玩家滑翔时也会走飞行三态。
     */
    private static boolean isFlying(Player player) {
        if (player.isFallFlying()) {
            return true;
        }
        return player instanceof LocalPlayer && player.getAbilities().flying;
    }

    /**
     * 正在播的那段一次性过渡还该不该继续播 —— 该就保持 {@code oneShot} 返回 {@code true}，
     * 否则把它清掉返回 {@code false}。
     *
     * <h2>落地缓冲与急停「让位」的时机不一样</h2>
     * 急停是刹车收势，<b>一动起来就该让给跑/走</b>，不然按回方向键还压着刹车姿势；
     * 落地缓冲正好相反：跳跃前进落地时方向键一定还按着，只要一移动就清掉，
     * 「跳跃落地」这条最常见的路径就永远看不到落地缓冲 —— 所以<b>移动中只让急停让位，
     * 落地缓冲照播到 {@code ticks} 到点</b>。
     *
     * <p>区分两者用引用比较而不是名字：{@code OneShot} 是 {@code LocomotionAnims} 里的
     * 字段实例，而角色的 {@code LocomotionAnims} 是每角色一份的常量，
     * {@code state.oneShot == loco.landing()} 就等价于「这段是落地缓冲」。
     * 落地有两档（重/轻），两档都算「落地缓冲」，所以判据要同时看两个槽 ——
     * 漏掉轻档的话，轻落地会被当成急停，方向键一按就没了。
     */
    private static boolean holdingOneShot(LocoTransient state, LocomotionAnims loco,
                                          boolean isMoving, Player player) {
        if (state.oneShot == null) {
            return false;
        }
        boolean landing = state.oneShot == loco.landing() || state.oneShot == loco.landingLight();
        if (isMoving && !landing) {
            state.oneShot = null;
            return false;
        }
        if (player.tickCount - state.oneShotStartTick < state.oneShot.ticks()) {
            return true;
        }
        state.oneShot = null;
        return false;
    }

    /** 起一段一次性过渡，记下起播的游戏刻。 */
    private static void beginOneShot(LocoTransient state, LocomotionAnims.OneShot oneShot, int nowTick) {
        state.oneShot = oneShot;
        state.oneShotStartTick = nowTick;
    }

    /**
     * 动作状态接管时把常态过渡按暂停 —— 只清「地面上攒的事实」和「正在播的收势」。
     *
     * <p>{@code airborne} 特意不动：空中放招（比如以后接空中普攻）落地时，
     * 「上一帧还在空中」这个沿必须还在，落地缓冲才接得上。
     */
    private static void suspendLocomotionTransient(Player player) {
        LocoTransient state = LOCO_TRANSIENT.get(player);
        if (state == null) {
            return;
        }
        state.wasRunning = false;
        state.oneShot = null;
    }

    /** 目标动画不存在时退回 idle；idle 也不存在就返回 {@code null}。 */
    @Nullable
    private static RawAnimation existingOrIdle(Player player, LocomotionAnims loco, RawAnimation wanted) {
        String name = targetName(wanted);
        if (name != null && AnimationAvailability.existsFor(player, name)) {
            return wanted;
        }

        String idleName = targetName(loco.idle());
        if (idleName != null && AnimationAvailability.existsFor(player, idleName)) {
            return loco.idle();
        }

        return null;
    }

    // ==================== 工具 ====================

    /** RawAnimation 第一段的动画名。 */
    @Nullable
    private static String targetName(@Nullable RawAnimation raw) {
        if (raw == null || raw.getAnimationStages().isEmpty()) {
            return null;
        }
        return raw.getAnimationStages().getFirst().animationName();
    }

    /** 当前控制器真正在播的动画名（GeckoLib 5：从 AnimationPoint 反查）。 */
    private static String currentAnimationName(AnimationController<?> controller) {
        AnimationPoint point = controller.getCurrentAnimationPoint();
        return point == null || point.animation() == null ? "" : point.animation().name();
    }
}
