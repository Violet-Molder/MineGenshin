package com.linweiyun.genshin.client.camera;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.client.combat.AttackApproach;
import com.linweiyun.genshin.client.combat.state.ActionStateMachine;
import com.linweiyun.genshin.client.render.character.AttachmentHelper;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.slf4j.Logger;

/**
 * 第三人称视角系统 —— <b>玩家的视角与角色的朝向互相独立</b>。
 *
 * <h2>1. 只有两个机位</h2>
 * 正常玩只有「第一人称」与「第三人称正后方」两个模式：按视角键在两者之间来回切，
 * <b>第三人称正脸（{@code THIRD_PERSON_FRONT}，「第二人称」）不再启用</b>。
 * 见 {@code CameraTypeMixin}（按键那条路）与本类 {@link #onClientTick} 第一段（旧存档里的残留值）。
 *
 * <h2>2. 转视角不转身（视角独立）</h2>
 * 原版第三人称里，鼠标一转，身体会被「头部相对身体的 ±50° 夹角」那条规则拖着一起转；
 * 本模组把它切掉：<b>转视角时角色不转身</b>。
 * 这是绝大多数第三人称单机游戏的做法 —— 玩家的视角永远 100% 归玩家，
 * 角色的朝向是角色自己的事（打怪时由 {@code AttackApproach} 把它转向目标）。
 *
 * <p>实现只有一处：{@code LivingEntityTickHeadTurnMixin} 拦下
 * {@code LivingEntity.tickHeadTurn}，去掉「把身体拉向视角」那一半。
 * 模型朝向读的就是 {@code yBodyRot}（见 {@code CharacterRenderDispatcher}），
 * 所以不动它 = 转视角时角色不转身。
 *
 * <h2>2.1 按 S 后退：身体转成<b>正对镜头</b>，镜头<b>不</b>归位</h2>
 * 原版规则 1（「移动中的身体朝向 = 移动方向」）在后退时会被翻 180°，结果是一按 S 就把后背
 * 留给玩家。本模组把那次翻转去掉（见 {@code LivingEntityTickHeadTurnMixin}）：<b>身体永远朝
 * 实际移动方向</b>，而 S 的移动方向正指着镜头，于是人<b>正对镜头往后退</b>；斜向后退
 * （S+A/D）则朝斜后的实际方向走 —— 任何方向都看不到后脑勺。
 *
 * <p>镜头这一侧要做的是<b>不参与自动归位</b>：人在正对镜头后退时镜头要是还转到人背后，
 * 画面就从正面滑到背面；更麻烦的是方向键是镜头参照的，镜头一转，S 的方向也跟着翻，
 * 「后退」会当场变成「往前跑」。
 *
 * <p>判据用<b>输入键</b>（{@code keyPresses.backward()}）而不是「目标方向与视线的夹角」：
 * 斜向后退是 135°、纯侧移是 90°，用夹角卡阈值很容易把侧移一起误伤（侧移是要归位的）。
 *
 * <h2>3. WASD 一律以<b>镜头</b>为参照（与角色朝向无关，这块故意不动）</h2>
 * 按 WASD 往哪走只看镜头，跟角色此刻面朝哪无关。
 *
 * <p>为什么一行都不用改：原版把输入向量转成世界方向用的就是 {@code getYRot()}
 * （{@code LocalPlayer.java:1127-1131} 那段 {@code globalXA = xxa * cos - zza * sin}），
 * 而本模组的 {@code yRot} 改成「镜头朝向」之后，参照系天然就是镜头。
 *
 * <p>反过来试过把 WASD 改成「以角色为参照」（W 往身体面朝方向走、A/D 转身体），
 * 实机手感是错的 —— 镜头一转，走路方向跟着偏，就不是第三人称游戏该有的操作了。
 * 所以这里一行输入都不碰。
 *
 * <h2>3.1 一直按 A/D 会绕大圈 —— 这是设计预期</h2>
 * A/D 以<b>当前镜头</b>为基准，而镜头又会（见下）慢慢矫正到身体朝向，
 * 于是「一直按住 A」会变成慢慢拐大弯绕圈：横移方向跟着镜头一起转。
 * 这不是 bug，就是要的效果（想直线横移就按住鼠标别让镜头矫正）。
 *
 * <p><b>按 S 不会这样</b>：按着 S（含斜后退）根本不参与自动归位，所以不会边退边拐，见 2.1 节。
 *
 * <h2>4. 镜头自动归位 / 视角跟随</h2>
 * 两种「镜头自己动」的情形，都是<b>按渲染帧</b>推进的（{@link #onComputeCameraAngles}）：
 * <ul>
 *   <li><b>自动归位</b>（常态）：按着前进 / 侧移、又有一两秒没人为转视角时，镜头<b>很慢很慢地</b>
 *       转到和角色朝向一致，省得「一直侧着身子走」；人一动鼠标立刻让位。
 *       三点规则：①<b>按后退不归位</b>（正对镜头后退时镜头再绕到人背后，S 就变成往前跑了，
 *       见 2.1 节）；
 *       ②强度从 0 <b>渐入</b>（{@link #ALIGN_RAMP_SECONDS} 秒升满），不是一到点就按满速转；
 *       ③速度上限只有 {@link #ALIGN_MAX_SPEED_PER_SECOND} 度/秒 —— 它的作用是让画面自然，
 *       不是赶紧把镜头掰回去。</li>
 *   <li><b>视角跟随</b>（{@link #setFollowBody(boolean)}，目前唯一触发点是大剑持续重击）：
 *       鼠标改为驾驶角色，镜头<b>紧跟着</b>角色背后。</li>
 * </ul>
 *
 * <p>为什么必须在<b>帧</b>里做、而不是在 tick 里做：鼠标增量是每帧写进 {@code yRot} 的
 * （{@code MouseHandler.handleAccumulatedMovement} 每帧调一次 {@code Entity.turn}），
 * 而每刻只有 20 次。按 tick 收敛时，鼠标一直在动的这段时间里镜头永远差着约 1.86 倍的
 * 每刻增量，停手后才慢慢荡回去 —— 画面就是又卡又不对。
 * 所以这里按帧、按真实帧间隔做指数收敛，
 * 并且把 {@code yRotO} 和 {@code yRot} 写成同一个值（这一帧渲染出来的角度就是刚算好的角度），
 * 收敛速度与帧率无关，也不会再被 tick 级的 {@code rotLerp} 拉回去。
 *
 * <h2>只在原神模式下接管</h2>
 * 没进原神模式时玩家就是原版玩家，这里一行都不动（全交给原版）——
 * 免得「卸下角色饰品之后视角变得不对劲」。
 */
@EventBusSubscriber(modid = Minegenshin.MOD_ID, value = Dist.CLIENT)
public final class ThirdPersonCamera {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);

    /** 视角跟随：镜头朝身体收敛的时间常数（秒）。越小越硬。 */
    private static final float FOLLOW_TAU_SECONDS = 0.09F;

    /** 自动归位：需要「一直没人为转视角」多少刻才开始。30 刻 = 1.5 秒。 */
    private static final int ALIGN_DELAY_TICKS = 30;

    /**
     * 自动归位的时间常数（秒）—— 比跟随慢得多，要的就是「缓缓转过去」。
     *
     * <p>它只决定<b>收敛形状</b>（离得远时走得快、快到位时越来越慢）；
     * 真正管「慢不慢」的是下面的速度上限与渐入权重，调手感先调那两个。
     */
    private static final float ALIGN_TAU_SECONDS = 1.6F;

    /** 自动归位时镜头每秒最多转多少度（再急也不甩头）。 */
    private static final float ALIGN_MAX_SPEED_PER_SECOND = 30.0F;

    /**
     * 自动归位的「渐入」时长（秒）：条件满足后，强度从 0 慢慢升到 100%。
     *
     * <h2>为什么必须有这一段</h2>
     * 静默时间一到就按满速开始转的话，镜头会从完全不动突然变成一直在动，
     * 那一下的<b>突变</b>比矫正本身显眼得多。有了渐入，前一两秒几乎看不出来，
     * 等察觉时镜头已经在缓缓转了。
     *
     * <p>条件一断（动了鼠标 / 松开方向键 / 按了 S）权重<b>立刻归零</b>，
     * 不做淡出 —— 鼠标永远优先，晚一帧让位都会显得黏。
     */
    private static final float ALIGN_RAMP_SECONDS = 1.6F;

    /** 夹角小于它就一次吸附到位，免得到最后差一点点永远转不完。 */
    private static final float SNAP_DEGREES = 0.15F;

    /** 判定「人动过视角」的最小角度差（度/帧）：比这还小当噪声。 */
    private static final float MANUAL_EPSILON = 0.02F;

    /**
     * 「从来没人为转过视角」的占位刻号；取足够负的数，减出来不会溢出成负数。
     *
     * <p>必须声明在 {@link #lastManualTick} <b>之前</b>：Java 不允许字段初始化器用简单名
     * 引用文本上声明在后面的字段（哪怕被引用的是常量），否则报「非法前向引用」。
     */
    private static final int NO_MANUAL_TICK = -100000;

    /**
     * 身体朝向该由谁说了算。
     *
     * <p>{@code LivingEntityTickHeadTurnMixin} 与 {@code EntityTurnMixin} 按这个分路走。
     */
    public enum BodyFacing {
        /** 原版：身体被视角拖着走（非本机玩家 / 没进原神模式 / 第一人称）。 */
        VANILLA,
        /** 视角独立：A/D 转角色、鼠标只管镜头。 */
        INDEPENDENT,
        /** 视角跟随：身体由鼠标驾驶、镜头追着身体（大剑持续重击期间）。 */
        FOLLOW,
        ACTION
    }

    /** 视角跟随是否开启（入口见 {@link #setFollowBody(boolean)}）。 */
    private static boolean followBody = false;

    /** 上一次相机 setup 时留下的镜头朝向（我们写进去的值，或我们观察到的值）。 */
    private static float lastYaw = Float.NaN;

    /** 上一次相机 setup 的时间戳（纳秒），用来算真实帧间隔。 */
    private static long lastCameraNanos = 0L;

    /** 最后一次「人为转视角」的刻号 —— 自动归位靠它算静默时间。 */
    private static int lastManualTick = NO_MANUAL_TICK;

    /**
     * 自动归位当前的强度权重（0..1）：条件一直满足时按 {@link #ALIGN_RAMP_SECONDS} 升到满，
     * 条件一断立刻归零。见 {@link #onComputeCameraAngles}。
     */
    private static float alignWeight = 0.0F;

    private ThirdPersonCamera() {
    }

    // ==================== 视角跟随入口 ====================

    /**
     * 开关视角跟随。
     *
     * <p><b>开启后不会一直挂着</b>：只有持续型招式（{@link ActionStateMachine#currentStateLoops()}）
     * 期间有效，那段状态一结束就自动落回「视角独立」。所以调用方只需要在进状态时打开，
     * 不需要（也不应该）在每个结束出口各自记得关 —— 松手、到时、被打断、切角色都能自动关干净。
     */
    public static void setFollowBody(boolean follow) {
        if (follow == followBody) {
            return;
        }
        followBody = follow;
        // 模式切换时忘掉上一段的状态，免得拿旧值去算收敛
        lastYaw = Float.NaN;
        lastCameraNanos = 0L;
        lastManualTick = NO_MANUAL_TICK;
        alignWeight = 0.0F;
        LOGGER.info("[MineGenshin][视角] 视角跟随{}", follow ? "开启" : "关闭");
    }

    /** 视角跟随现在开没开。 */
    public static boolean isFollowBody() {
        return followBody;
    }

    // ==================== 每客户端 tick ====================

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        // ① 第三人称只留两个模式：正脸不再启用。
        //    按键那条路由 CameraTypeMixin 直接跳过它，这里兜住「存档/其它模组塞进来的旧值」。
        if (minecraft.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }

        // ② 跟随只在持续型招式期间有效；那个状态一结束就自动落回
        //    （状态机的复位是唯一出口，所以这里不会漏关）。
        //    例外：这一招在数据里明确要求「身体跟随镜头」（ActionBodyFacing.CAMERA）时，
        //    哪怕状态不循环也保持跟随 —— 一直到这一招结束（AttackApproach.cancel）为止。
        if (followBody && !ActionStateMachine.currentStateLoops() && !AttackApproach.wantsCameraFacing()) {
            setFollowBody(false);
        }
    }

    // ==================== 每渲染帧：镜头朝向的平滑 ====================

    /**
     * 相机角度事件 —— 每帧一次（{@code Camera.alignWithEntity} 里发出，正好在
     * 「设朝向」与「沿朝向把镜头拉远」之前），拿来平滑镜头朝向。
     *
     * <p>注意这里读到、写入的都是 {@code yRot}：相机原生的取角走的是
     * {@code LivingEntity.getViewYRot} → {@code yHeadRot}（每刻在 {@code Player.aiStep}
     * 里同步成 {@code yRot}），所以只写 {@code yRot} 还不够 —— 本方法最后会用
     * {@code event.setYaw} 把这一帧真正渲染的角度也定成同一个值。
     *
     * <p>这里同时写 {@code yRot}（玩家视线，攻击/拾取都读它）和事件的 yaw（这一帧真正渲染的角度），
     * 两者保持一致；{@code yRotO} 也写成同一个值，让 {@code rotLerp} 这一帧不再做二次插值。
     */
    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        BodyFacing facing = player == null ? BodyFacing.VANILLA : bodyFacing(player);
        if (player == null || event.getCamera().getEntity() != player
                || facing == BodyFacing.VANILLA) {
            lastYaw = Float.NaN;
            lastCameraNanos = 0L;
            alignWeight = 0.0F;
            return;
        }

        long now = Util.getNanos();
        float dt = lastCameraNanos == 0L
                ? 0.0F
                : Mth.clamp((now - lastCameraNanos) / 1.0e9F, 0.0F, 0.1F);
        lastCameraNanos = now;

        float current = player.getYRot();

        // 「人为转了视角」的判据：镜头朝向我们上帧留下的值不一样了。
        // 我们自己收敛时写的就是标好的值，差值恒为 0，所以只有鼠标（或别的模组/传送）会触发。
        if (!Float.isNaN(lastYaw) && Math.abs(Mth.wrapDegrees(current - lastYaw)) > MANUAL_EPSILON) {
            lastManualTick = player.tickCount;
        }

        float tau;
        float maxDegreesPerSecond;
        if (followBody) {
            // 跟随：一直追着身体，不设速度上限（鼠标快甩时镜头也要跟得住）。
            tau = FOLLOW_TAU_SECONDS;
            maxDegreesPerSecond = 0.0F;
            alignWeight = 0.0F;
        } else {
            // 常态：只有「按着前进/侧移 + 1.5 秒没人为转视角」才缓缓归位，其余时间一行不碰，
            // 原版鼠标视角完全接管（yRotO 也就还留着原版的插值）。
            //
            // 动作接管期间（ACTION）一并跳过：那时候身体可能正被 AttackApproach 转向索敌目标，
            // 镜头要是跟着归位，就成了「一攻击镜头就被拽到目标方向」——
            // 和「镜头永远 100% 归玩家」冲突。
            if (player.tickCount - lastManualTick < ALIGN_DELAY_TICKS || !shouldAlign(player)
                    || facing == BodyFacing.ACTION) {
                // 条件一断就把权重清零：鼠标 / 后退键 / 停下脚步永远优先
                alignWeight = 0.0F;
                lastYaw = current;
                return;
            }
            // 渐入：从 0 慢慢升到 1，避免「开始矫正」那一下的突变
            alignWeight = Math.min(1.0F, alignWeight + dt / ALIGN_RAMP_SECONDS);
            tau = ALIGN_TAU_SECONDS;
            maxDegreesPerSecond = ALIGN_MAX_SPEED_PER_SECOND;
        }

        // 跟随那条路不带权重（一直满功率），只有自动归位才渐入
        float weight = followBody ? 1.0F : alignWeight;

        float delta = Mth.wrapDegrees(player.yBodyRot - current);
        float next;
        if (Math.abs(delta) <= SNAP_DEGREES) {
            next = player.yBodyRot;
        } else {
            // 指数收敛：dt 越大走得越多，帧率高低都是同一条曲线
            float step = delta * (1.0F - (float) Math.exp(-dt / tau)) * weight;
            if (maxDegreesPerSecond > 0.0F) {
                float cap = maxDegreesPerSecond * dt;
                step = Mth.clamp(step, -cap, cap);
            }
            next = Mth.wrapDegrees(current + step);
        }

        player.setYRot(next);
        // 插值两端写成同一个值：这一帧渲染出来的镜头角度就是刚算好的角度。
        // 只写 yRot 不写 yRotO 的话，渲染会拿「本刻开始时的朝向」当插值起点，
        // 镜头就会慢半刻、并且每刻一顿。
        player.yRotO = next;
        event.setYaw(next);
        lastYaw = next;
    }

    /**
     * 这刻该不该做「镜头自动归位」。
     *
     * <p>两条规则：
     * <ul>
     *   <li><b>按后退不归位</b>：按着 S 时人是正对镜头往后退的（身体朝实际移动方向，
     *       见 {@code LivingEntityTickHeadTurnMixin}）。镜头要是还转到人背后，画面会从正面
     *       滑到背面；更糟的是方向键是镜头参照的，镜头一转 S 的方向跟着翻，
     *       「后退」当场变成「往前跑」。所以这一路镜头一动不动。
     *       斜向后退（S+A / S+D）同样算后退：理由是同一个。</li>
     *   <li>前进 / 侧移（W、A、D 且没有 S）才算「侧着身子走」，才值得归位。</li>
     * </ul>
     */
    private static boolean shouldAlign(LocalPlayer player) {
        if (isBackpedaling(player)) {
            return false;
        }
        return player.input.up || player.input.left || player.input.right;
    }

    /**
     * 这个实体这刻是不是在按后退键（S）。
     *
     * <p>只给 {@link #shouldAlign} 用：身体朝哪由 {@code LivingEntityTickHeadTurnMixin}
     * 按实际位移算，与这里无关；这里只管「这段路镜头要不要自动归位」。
     *
     * <p>只对本机玩家有意义，其余实体一律 {@code false}。定身期间玩家的输入实例被
     * {@code ActionInputFreeze} 换成替身（按键为空），这里自然读到「没在后退」——
     * 那段时间人也确实动不了，两边一致。
     */
    public static boolean isBackpedaling(Entity entity) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && entity == player && player.input.down;
    }

    // ==================== 给 mixin 用 ====================

    /**
     * 这个实体的身体朝向该由谁说了算。
     *
     * <p>只有「本机玩家 + 原神模式 + 第三人称」才接管；其余（怪物、其他玩家、
     * 原版玩家、第一人称）一律返回 {@link BodyFacing#VANILLA} 交给原版。
     *
     * <p>两个 mixin 与相机事件都走这一个判断：{@code LivingEntityTickHeadTurnMixin} 决定
     * 「身体要不要跟着视角转」，{@code EntityTurnMixin} 决定「鼠标的水平增量写给谁」。
     * （WASD 不在这里分流 —— 它一律走原版，见类注释第 3 条。）
     */
    public static BodyFacing bodyFacing(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (entity != minecraft.player || !(entity instanceof Player player)) {
            return BodyFacing.VANILLA;
        }
        if (!AttachmentHelper.isGenshinMode(player)) {
            return BodyFacing.VANILLA;
        }
        if (minecraft.options.getCameraType().isFirstPerson()) {
            // 第一人称看不到自己的身体，让原版规则照跑（身体 = 视角方向，W = 往镜头前走）
            return BodyFacing.VANILLA;
        }
        if (followBody) {
            // 跟随优先：CAMERA 那一档既是「接管」也是「跟随」，跟随期间身体由鼠标驾驶。
            return BodyFacing.FOLLOW;
        }
        // 出招期间把身体朝向从「跟随位移」手里接管过来：
        // 索敌到由 AttackApproach 转向目标，没索敌就一个字段都不动。
        return AttackApproach.holdsBodyFacing() ? BodyFacing.ACTION : BodyFacing.INDEPENDENT;
    }
}
