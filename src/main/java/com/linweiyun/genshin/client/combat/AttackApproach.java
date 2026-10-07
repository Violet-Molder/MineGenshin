package com.linweiyun.genshin.client.combat;
import com.linweiyun.genshin.core.system.combat.action.data.ActionBodyFacing;
import com.linweiyun.genshin.core.system.combat.action.data.ActionStep;

import com.linweiyun.genshin.core.system.combat.action.data.Engagement;
import com.linweiyun.genshin.client.camera.ThirdPersonCamera;
import com.linweiyun.genshin.client.combat.state.ActionStateMachine;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 近战突进 / 转向 —— 「锁到目标之后怎么贴上去」。
 *
 * <h2>一次攻击的三种情况</h2>
 * <table border="1">
 *   <caption>索敌距离 &gt; 攻击距离</caption>
 *   <tr><th>情况</th><th>怎么做</th></tr>
 *   <tr><td>目标在攻击距离内</td><td>只滑向目标（不位移），然后立刻进入后续动画</td></tr>
 *   <tr><td>目标在攻击距离外、索敌范围内</td><td>滑向目标 + 冲过去，到攻击距离后进入后续动画</td></tr>
 *   <tr><td>远程招式（{@link Engagement#ranged}）</td><td><b>永不突进</b>，只滑向目标</td></tr>
 *   <tr><td>没有目标</td><td>什么都不做，照旧执行动作自带的 {@code moves} 位移</td></tr>
 * </table>
 *
 * <h2>保护性原则：接近任务做不下去就开打</h2>
 * 突进的目的是<b>快速把人带进自己的攻击范围</b>，不是「百分百贴身」。
 * 所以除了「到位」和「够得着」两条正常出口，还有一条兜底出口：
 * <b>只要接近这件事停下来了就立刻出手</b> —— 撞墙、卡住、目标在方块后面、
 * 目标在头顶/地板下打转、目标跑得和自己一样快……不管因为什么。
 *
 * <p>判据是 {@link #DASH_BLOCKED_PATIENCE_TICKS}（身体没动）与
 * {@link #DASH_STALL_PATIENCE_TICKS}（距离没缩小），任一成立即停。
 * 没有它的时候，追不到的情况会一路追满 {@link #MAX_APPROACH_TICKS} 刻，
 * 表现就是「在够不着的目标旁边逗留一会儿才挥刀」。
 *
 * <h2>转向为什么是「滑」而不是「瞬移」</h2>
 * 一按就把人掰到目标方向，看起来像贴图被翻转了一下 —— 缺了「发力」的过程。
 * 这里用<b>衰减式追角</b>：每刻转掉剩余夹角的一部分，再夹在一个速度区间里。
 *
 * <pre>
 * step = clamp(剩余夹角 × TURN_GAIN, TURN_MIN_SPEED, TURN_MAX_SPEED)
 * </pre>
 *
 * <ul>
 *   <li>夹角大 → 先吃 {@link #TURN_MAX_SPEED}，180° 大约 6~7 刻（0.3 秒）转完，够快；</li>
 *   <li>夹角小 → 速度按比例掉下来，最后几度是「收着劲」靠上去的，不会一顿一顿；</li>
 *   <li>永远不会一帧到位（上限 {@link #TURN_MAX_SPEED} 度/刻 = 900°/秒）。</li>
 * </ul>
 *
 * <p>转向是<b>持续推进</b>的：按下的那一刻起算，动作动画同时开始播，两者并行 ——
 * 所以「转过去」不会给按键加延迟，只有难看的瞬移被去掉了。
 *
 * <h2>为什么冲刺期间要「冻结」动画</h2>
 * 参考原神/一般 3A 的做法：攻击动画的<b>起手小动作</b>先播出来（比如手搭到剑柄上），
 * 然后保持这个姿势冲过去，到位后再接着播剩下的部分。
 *
 * <p>实现上不需要额外准备一套突进动画 —— 直接让状态机在突进期间把动画<b>暂停</b>在起手那一帧
 * （{@code PlayState.PAUSE}），到位时解冻并<b>从这一刻重新计时</b>。
 * 这样 {@code hits[].delay} 永远是「从动画继续播放起算」，
 * 距离远近不会让伤害点飘掉。音效同理，跟着到位那一刻重新排期。
 *
 * <h2>为什么位移在客户端做</h2>
 * 客户端是本机玩家的移动权威，改 {@code deltaMovement} 服务端会跟着走；
 * 而「什么时候到位」决定动画何时解冻，必须零延迟，所以放在客户端。
 * 到位之后才把攻击请求发给服务端，服务端的伤害计时也就跟着从到位那刻起算。
 *
 * <h2>参数来自招式</h2>
 * 停几格、冲多快、最多冲几刻、最快转多少度，全部由
 * {@code ActionStep.engagement} 带进来 ——
 * 想按角色/按招式调手感，不用改这个类。
 */
public final class AttackApproach {

    // ==================== 转向 ====================

    /** 每刻消掉剩余夹角的比例；越大越「急」。 */
    public static final float TURN_GAIN = 0.45f;

    /** 最小角速度（度/刻）——小角度也要有点惯性，不然最后几度会顿住。 */
    public static final float TURN_MIN_SPEED = 6.0f;

    /** 最大角速度（度/刻，45 ≈ 900°/秒）：大角度转身也不会一帧到位。 */
    public static final float TURN_MAX_SPEED = 45.0f;

    /** 夹角小于这个度数就算「转到了」（度）。 */
    public static final float TURN_TOLERANCE = 1.5f;

    /**
     * 一次动作最多持续转向多少刻（防止目标绕圈时人一直跟着转）。
     *
     * <p><b>突进期间不消耗</b>这个预算（追着人跑当然要一直对着他），所以实际持续时间
     * 上限是「突进刻数 + 这个预算」，最坏情况 {@link #MAX_APPROACH_TICKS} + 本值。
     */
    public static final int TURN_BUDGET_TICKS = 16;

    // ==================== 突进默认值（可被招式覆盖） ====================

    /**
     * 突进终点：贴到目标身前多近就停（格）。
     *
     * <p><b>不是停在攻击距离上</b> —— 攻击距离只用来判断「要不要突进」，
     * 真突进就一路贴到跟前，这样近战才站得住位置，不会在五六格外干挥。
     *
     * <p>但也不要「必须紧贴」：目标也在动，死磕精确距离会让到位判定永远差一点，
     * 人明明已经贴脸了还在追着跑。实际到位线见 {@link #ARRIVE_SLACK}。
     */
    public static final double STOP_DISTANCE = 1.4;

    /**
     * 到位宽容度（格）：进到「{@link #STOP_DISTANCE} + 这个」就算到了。
     *
     * <p>为什么必须留：冲刺的时候目标也在动，要求精确贴住会出现
     * 「已经很近了，但因为它挪了一步，所以永远没到位」—— 表现就是贴着人鬼畜。
     * 差不多就出手，剩下的交给命中判定。
     */
    public static final double ARRIVE_SLACK = 0.6;

    /** 突进速度（格/刻）。<b>全向</b>：目标是飞的、在脚下的，都朝它直线过去。 */
    public static final double DASH_SPEED = 1.0;

    /** 兜底：最多突进多少刻，超时也要继续打（防止目标一直跑导致永远不出手）。 */
    public static final int MAX_APPROACH_TICKS = 20;

    /**
     * 「已经够得着」连续多少刻就强制开打。
     *
     * <p>目标在到位线附近来回动时，光靠精确距离判定会让人一直跟着跑；
     * 够得着（进到这一招的攻击距离）连续这么多刻就直接出手 —— 反正已经打得到了。
     */
    public static final int ARRIVE_PATIENCE_TICKS = 3;

    // ==================== 保护性原则（追不动了就开打） ====================

    /**
     * 本刻实际位移小于这个值（格）就当成「人被挡住了」。
     *
     * <p>突进每刻会给 1 格左右的冲量，正常情况下位移不会低于 0.5；
     * 撞墙/顶住方块时会掉到 0.05 以下。
     */
    public static final double DASH_BLOCKED_EPSILON = 0.05;

    /** 连续这么多刻「身体没动」→ 立刻停手开打。 */
    public static final int DASH_BLOCKED_PATIENCE_TICKS = 2;

    /** 距离每刻缩小不到这个值（格）就当成「没在接近」。 */
    public static final double DASH_PROGRESS_EPSILON = 0.05;

    /** 连续这么多刻「没接近」→ 立刻停手开打。 */
    public static final int DASH_STALL_PATIENCE_TICKS = 2;

    // ==================== 吸附（每次出手推一下） ====================

    /**
     * 吸附带（格）：目标在「这一招的攻击距离 + 这个」以内时，出手瞬间朝它推一小段。
     *
     * <p>超出这个范围就不是吸附的事了 —— 那是突进（从远处冲过去）。
     */
    public static final double ADHESION_BAND = 1.5;

    /** 出手那一下的推力（格/刻的冲量，≈ 半个格的位移）。别调太大，它就是「一小步」。 */
    public static final double ADHESION_STEP_SPEED = 0.22;

    /**
     * 冲量 → 实际位移的粗略换算系数。
     *
     * <p>玩家在地面上每刻衰减约 0.4（摩擦），一个冲量 {@code v} 大概滑出 {@code v / 0.4 = 2.5v} 格。
     * 用它来「按缺口大小收力」，免得贴脸时还往前撞一下。
     */
    public static final double ADHESION_TRAVEL_FACTOR = 2.5;

    /** 目标比这个还近就不再推（推了会穿模 / 穿过目标丢锁）。 */
    public static final double ADHESION_MIN_DISTANCE = 0.9;

    // ==================== 状态 ====================

    /** 到位后「重新计时」的动画总时长由调用方给，这里只负责通知。 */
    private static Runnable onArrive;

    private static int targetId;

    // 转向（独立于突进：到位之后还要把最后几度转完）
    private static boolean facing;
    private static int facingTicksLeft;
    private static float facingTurnSpeed = TURN_MAX_SPEED;

    // 突进
    private static boolean dashing;
    private static int dashTicksLeft;
    private static double dashStopDistance = STOP_DISTANCE;
    private static double dashSpeed = DASH_SPEED;
    /** 这一招的生效攻击距离 —— 决定「到位线」和吸附带。 */
    private static double dashAttackRange = 3.0;
    /** 连续多少刻「已经够得着」（用于 {@link #ARRIVE_PATIENCE_TICKS} 的兜底开打）。 */
    private static int nearTicks;

    private static ActionBodyFacing bodyFacing = ActionBodyFacing.MOVEMENT;

    // 保护性原则用的两个「还没停下来吗」探测器（见 DASH_STALL_PATIENCE_TICKS）
    /** 上一刻玩家所在位置 —— 用来判断「身体到底有没有动」。 */
    @Nullable
    private static Vec3 lastPos;
    /** 上一刻到目标的距离 —— 用来判断「还在不在接近」。 */
    private static double lastDistance = Double.MAX_VALUE;
    /** 连续多少刻身体没动 / 距离没缩小。 */
    private static int blockedTicks;
    private static int stallTicks;

    private AttackApproach() {
    }

    /** 正在突进。 */
    public static boolean isActive() {
        return dashing;
    }

    // ==================== 身体朝向的接管 ====================

    public static void holdBodyFacing(@Nullable ActionBodyFacing policy) {
        ActionBodyFacing next = policy == null ? ActionBodyFacing.MOVEMENT : policy;
        bodyFacing = next;
        if (next == ActionBodyFacing.CAMERA) {
            ThirdPersonCamera.setFollowBody(true);
        }
    }

    public static boolean holdsBodyFacing() {
        return bodyFacing.takesOverBodyFacing();
    }

    public static ActionBodyFacing bodyFacing() {
        return bodyFacing;
    }

    public static boolean wantsCameraFacing() {
        return bodyFacing == ActionBodyFacing.CAMERA;
    }

    /** 这次攻击需不需要突进（目标在攻击距离外）。 */
    public static boolean needsDash(Player player, @Nullable LivingEntity target, double attackRange) {
        return target != null && player.distanceTo(target) > attackRange;
    }

    // ==================== 开始 ====================

    /**
     * 开始贴脸流程：转向 + 突进，到位后回调。
     *
     * <p>调用方负责先用 {@link Engagement#wantsDash()} 判断要不要走这条路径；
     * 只要走这条，就一定会突进。
     *
     * @param player      本机玩家
     * @param target      锁定目标
     * @param engagement  这一招的交战形态（转向速度 / 停几格 / 冲多快 / 最多几刻）
     * @param attackRange 这一招的生效攻击距离（决定到位线，见 {@link #ARRIVE_SLACK}）
     * @param onArrive    到位后干什么（继续播动画 + 发音效 + 发服务端请求）
     */
    public static void begin(LocalPlayer player, LivingEntity target, Engagement engagement,
                             double attackRange, Runnable onArrive) {
        startFacing(player, target, engagement);

        AttackApproach.targetId = target.getId();
        AttackApproach.onArrive = onArrive;
        AttackApproach.dashing = true;
        AttackApproach.nearTicks = 0;
        AttackApproach.blockedTicks = 0;
        AttackApproach.stallTicks = 0;
        AttackApproach.lastPos = null;
        AttackApproach.lastDistance = Double.MAX_VALUE;
        AttackApproach.dashTicksLeft = engagement != null && engagement.maxApproachTicks > 0
                ? engagement.maxApproachTicks
                : MAX_APPROACH_TICKS;
        AttackApproach.dashStopDistance = engagement != null && engagement.stopDistance > 0
                ? engagement.stopDistance
                : STOP_DISTANCE;
        AttackApproach.dashSpeed = engagement != null && engagement.dashSpeed > 0
                ? engagement.dashSpeed
                : DASH_SPEED;
        AttackApproach.dashAttackRange = attackRange > 0 ? attackRange : 3.0;

        // 冻结动画：状态机把当前动作停在这一帧
        ActionStateMachine.setApproachFrozen(true);
    }

    /**
     * 到位线：进到这个距离就算「到了」。
     *
     * <p>= {@code min(攻击距离, 停靠距离 + 宽容度)}。攻击距离是硬上限
     * （超过它就算到位也够不着），宽容度负责「不用紧贴」。
     */
    private static double arriveDistance() {
        double slack = Math.max(0.0, dashStopDistance + ARRIVE_SLACK);
        return Math.min(dashAttackRange, slack);
    }

    /**
     * 只转向不位移 —— 目标已经在攻击距离内、或者这一招是远程招式时用这个。
     *
     * <p>转向会在接下来几刻里持续推进，动画照常播，互不等待。
     *
     * <p><b>必须在起完状态（{@code changeState}）之后调</b>：换状态会走一次
     * {@link #cancel()}，早于它登记就会被清掉。
     *
     * @return 有没有开始转向（没目标就返回 false）
     */
    public static boolean faceTarget(LocalPlayer player, @Nullable LivingEntity target,
                                     @Nullable Engagement engagement) {
        if (target == null) {
            return false;
        }
        startFacing(player, target, engagement);
        return true;
    }

    private static void startFacing(LocalPlayer player, LivingEntity target, @Nullable Engagement engagement) {
        targetId = target.getId();
        facing = true;
        facingTicksLeft = TURN_BUDGET_TICKS;
        facingTurnSpeed = engagement != null && engagement.turnSpeed > 0
                ? engagement.turnSpeed
                : TURN_MAX_SPEED;
    }

    public static void cancel() {
        clearDash();
        facing = false;
        facingTicksLeft = 0;

        if (bodyFacing == ActionBodyFacing.CAMERA) {
            ThirdPersonCamera.setFollowBody(false);
        }
        bodyFacing = ActionBodyFacing.MOVEMENT;

        ActionStateMachine.setApproachFrozen(false);
    }

    // ==================== 每刻 ====================

    /** 每客户端 tick 调一次（在状态机 tick 之前）。 */
    public static void tick(LocalPlayer player) {
        if (!facing && !dashing) {
            return;
        }

        LivingEntity target = resolveTarget(player);

        // 目标没了 → 突进立刻收摊（保留原速度别把人定在半路），转向也停
        if (target == null) {
            if (dashing) {
                finish(player, false);
            }
            facing = false;
            facingTicksLeft = 0;
            return;
        }

        tickFacing(player, target);

        if (!dashing) {
            return;
        }

        // 超时 → 照样继续打，只是不位移了
        if (--dashTicksLeft <= 0) {
            finish(player, false);
            return;
        }

        double distance = player.distanceTo(target);

        // 到位判定：进到「停靠距离 + 宽容度」就出手。
        // 不要要求精确贴住 —— 目标同时在动，死磕距离会变成「明明贴脸了却永远没到位」。
        if (distance <= arriveDistance()) {
            finish(player, true);
            return;
        }

        // 兜底：已经「够得着」（进到这一招的攻击距离）却还没进到位线 → 连续几刻就强制开打。
        //
        // 为什么需要：目标在到位线附近来回动时，距离会一直在线上弹，
        // 只靠精确判定会出现「人在旁边跟着跑，动画却一直冻着不出手」。
        // 够得着就已经能打中了，别墨迹。
        if (distance <= dashAttackRange) {
            if (++nearTicks >= ARRIVE_PATIENCE_TICKS) {
                finish(player, true);
                return;
            }
        } else {
            nearTicks = 0;
        }

        // ── 保护性原则：接近这件事只要做不下去了，就当场停下来开打 ──
        //
        // 突进的目的是「快速把人带进自己的攻击范围」，不是「百分百贴身」。
        // 所以撞墙、卡住、目标在方块后面或头顶打转、目标跑得和自己一样快……
        // <b>不管因为什么</b>，只要停下来就出手，绝不继续追。
        //
        // 没有这一条时，追不到的情况会一路追满 MAX_APPROACH_TICKS(20 刻) 才出手，
        // 表现就是「在地板下那个目标的头顶逗留一会儿」「贴着墙蹭半天」。
        // 用两个互相独立的迹象判断「停下来了」，任一成立都算：
        if (lastPos != null && player.position().distanceTo(lastPos) < DASH_BLOCKED_EPSILON) {
            blockedTicks++;                 // ① 身体几乎没动 → 被方块挡住了
        } else {
            blockedTicks = 0;
        }
        lastPos = player.position();

        if (distance >= lastDistance - DASH_PROGRESS_EPSILON) {
            stallTicks++;                   // ② 距离不再缩小 → 追不上（或目标一样快）
        } else {
            stallTicks = 0;
        }
        lastDistance = distance;

        if (blockedTicks >= DASH_BLOCKED_PATIENCE_TICKS
                || stallTicks >= DASH_STALL_PATIENCE_TICKS) {
            // hardStop = true：原地急停 + 立刻走「到位」那条路（解冻动画、结算伤害）
            finish(player, true);
            return;
        }

        // 突进方向：<b>全向</b>（含 Y）。
        // 早期只推水平分量，目标是飞的就永远差一段高度 → 到位判定迟迟不成立 → 在头顶鬼畜；
        // 在脚下的同理。现在朝目标位置直线过去，竖直方向也一起给速度。
        Vec3 delta = target.position().subtract(player.position());
        double remaining = delta.length();
        if (remaining < 1.0E-6) {
            finish(player, true);
            return;
        }

        // ⚠️ 这里**不按剩余距离收力**。
        //
        // 早期版本写的是 min(dashSpeed, distance - 到位线)：靠近到位线时力度会被压到很小，
        // 而目标如果正在往外走（怪的速度量级差不多就是 0.2 格/刻），两边速度刚好抵消 →
        // 距离永远卡在到位线上一点点，人就「跟着目标跑」直到 20 刻超时，动画全程冻着。
        // 现在全程满速，靠「到达目标身前」这一条防冲过头：最多一步冲到目标身上，下一帧就会判定到位。
        double step = Math.min(dashSpeed, remaining);
        Vec3 velocity = delta.scale(step / remaining);
        player.setDeltaMovement(velocity.x, velocity.y, velocity.z);
        player.hurtMarked = true;
    }

    /**
     * 吸附：<b>出手的瞬间朝目标推一小段</b>，一次一小步。
     *
     * <h2>不是「一直跟着」</h2>
     * 早先的版本是在整个动作期间每刻都往目标拉，那会变成「人像被吸住一样一直贴过去」。
     * 要的是另一种手感：<b>每一刀各自带一小步</b> ——
     *
     * <pre>
     * 第 1 刀：目标在 2.4 格外 → 这一刀把它带进 2.0
     * 目标挪开半步 → 人站着不动（这一刀已经打完了）
     * 第 2 刀：再带一小步，又贴回去
     * </pre>
     *
     * 因为每次只走一小段、而连招之间间隔很短，看起来就是「攻击自带吸附」，
     * 既不会站着干挥，也不会像磁铁一样被拖着走。
     *
     * <h2>和突进的分工</h2>
     * <ul>
     *   <li>缺口 ≤ {@link #ADHESION_BAND} → 推一小步（这个方法）；</li>
     *   <li>缺口更大 → 交给突进（{@link #begin} 那条路），那是「从远处冲过去」。</li>
     * </ul>
     * 所以吸附不该被频繁触发，突进也不该被小碎步触发。
     *
     * <p><b>参数从哪来</b>（这就是「吸附参数是怎么确定的」的完整链路）：
     * <pre>
     * 生效范围 = 这一招的攻击距离（ActionStep.attackRange / effectiveAttackRange()）
     *            + 吸附带（Engagement.adhesionBand，默认全局 ADHESION_BAND）
     * 一步多大力 = Engagement.adhesionStep（默认全局 ADHESION_STEP_SPEED）
     * 力度换算、贴身下限 = 全局 ADHESION_TRAVEL_FACTOR / ADHESION_MIN_DISTANCE
     * </pre>
     *
     * @param attackRange 这一招的生效攻击距离
     * @param engagement  这一招的交战形态；{@code null} 表示全用全局默认
     */
    public static void stepToward(LocalPlayer player, LivingEntity target, double attackRange,
                                  @Nullable Engagement engagement) {
        // 招式里写负数（-1 = USE_DEFAULT）→ 用全局常量；写 0 → 这一招明确关掉吸附。
        // （判定用 >= 0：这样「0 = 关掉」和「-1 = 用默认」才是两件事，和文档一致。）
        double band = engagement != null && engagement.adhesionBand >= 0
                ? engagement.adhesionBand
                : ADHESION_BAND;
        double maxStep = engagement != null && engagement.adhesionStep >= 0
                ? engagement.adhesionStep
                : ADHESION_STEP_SPEED;
        if (band <= 0 || maxStep <= 0) {
            return;
        }

        double distance = player.distanceTo(target);
        if (distance > attackRange + band || distance <= ADHESION_MIN_DISTANCE) {
            return;
        }

        Vec3 delta = target.position().subtract(player.position());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal < 1.0E-6) {
            return;
        }

        // 按缺口收力：贴脸时不推、离得远时推满（也就半个格）
        double gap = Math.min(distance - ADHESION_MIN_DISTANCE, band);
        double speed = Math.min(maxStep, gap / ADHESION_TRAVEL_FACTOR);

        Vec3 nudge = new Vec3(delta.x, 0, delta.z).scale(speed / horizontal);
        player.setDeltaMovement(player.getDeltaMovement().add(nudge));
        player.hurtMarked = true;
    }

    /**
     * 转向一步。
     *
     * <p>突进期间不消耗预算 —— 追着人跑的时候当然要一直对着他。
     *
     * <p><b>两个必须遵守的约束</b>：
     * <ol>
     *   <li><b>绝不过冲</b>：本刻转动量必须夹在「剩余夹角」以内。早期版本直接
     *       {@code yRot + sign*speed}，而 {@link #TURN_MIN_SPEED} 有 10° ——
     *       剩余夹角只剩 5° 时会一步跨到对面，下一帧再跨回来，
     *       于是镜头<b>左右乱晃</b>（±5° 的极限环，一次攻击能抖大半秒）。</li>
     *   <li><b>只转身体，绝不碰镜头</b>：写的是 {@code yBodyRot}（模型朝向），
     *       不是 {@code yRot}（本机玩家的镜头就是它）。碰了镜头就会「晃一下」——
     *       哪怕只转 5°，玩家也能感觉到画面被拽走。玩家的视角永远 100% 归玩家。</li>
     * </ol>
     */
    private static void tickFacing(LocalPlayer player, LivingEntity target) {
        if (!facing) {
            return;
        }

        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        if (dx * dx + dz * dz < 1.0E-6) {
            facing = false;
            return;
        }

        float targetYaw = yawTo(dx, dz);
        float delta = Mth.wrapDegrees(targetYaw - player.yBodyRot);

        if (Math.abs(delta) <= TURN_TOLERANCE) {
            // 已经对上，直接吸附掉最后那不到 1.5 度
            setBodyYaw(player, targetYaw);
            facing = false;
            return;
        }

        if (!dashing && --facingTicksLeft <= 0) {
            facing = false;
            return;
        }

        // 衰减式追角：大角度先吃上限，小角度按比例收劲。
        // approachDegrees 会在目标角度上夹住，天然不会过冲。
        float minSpeed = Math.min(TURN_MIN_SPEED, facingTurnSpeed);
        float speed = Math.max(minSpeed, Math.min(Math.abs(delta) * TURN_GAIN, facingTurnSpeed));
        setBodyYaw(player, Mth.approachDegrees(player.yBodyRot, targetYaw, speed));
    }

    /**
     * 到位：急停 + 解冻动画并回调（回调里会重置计时、排音效并发服务端请求）。
     *
     * @param hardStop 是不是「贴到目标」的正常到位；true 时把水平速度清零做急停，
     *                 false（目标没了/超时）就保留原速度，别把人定在半路
     */
    private static void finish(LocalPlayer player, boolean hardStop) {
        Runnable callback = onArrive;
        clearDash();

        if (hardStop) {
            // 急停：水平速度归零，竖直保留（还在下落就继续掉）
            player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
            player.hurtMarked = true;
        }

        ActionStateMachine.setApproachFrozen(false);

        if (callback != null) {
            callback.run();
        }
    }

    private static void clearDash() {
        dashing = false;
        dashTicksLeft = 0;
        onArrive = null;

        // 保护性原则的探测器也要清：下一次突进从零开始数
        nearTicks = 0;
        blockedTicks = 0;
        stallTicks = 0;
        lastPos = null;
        lastDistance = Double.MAX_VALUE;
    }

    // ==================== 转向工具 ====================

    /** 由水平方向算出的 MC 偏航角。 */
    private static float yawTo(double dx, double dz) {
        return (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
    }

    private static void setBodyYaw(Player player, float yaw) {
        // 只写身体朝向：模型会转过去对准目标，而玩家的镜头一动不动。
        // （写 yRot / yHeadRot 都会带着镜头走，实测会有明显的「晃一下」。）
        player.setYBodyRot(yaw);
    }

    // ==================== 内部 ====================

    @Nullable
    private static LivingEntity resolveTarget(LocalPlayer player) {
        if (targetId == 0 || player.level() == null) {
            return null;
        }
        return player.level().getEntity(targetId) instanceof LivingEntity living && living.isAlive()
                ? living
                : null;
    }
}
