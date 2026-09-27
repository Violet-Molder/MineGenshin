package com.linweiyun.genshin.core.system.combat.animation.config;

import com.geckolib.animation.RawAnimation;
import org.jetbrains.annotations.Nullable;

/**
 * 一个角色的常态动画集合（站 / 走 / 跑 / 蹲 / 睡 / 爬 / 游泳 / 跳跃 / 落地 / 急停）。
 *
 * <p>把「动画名」抽成数据，让 {@code PlayerAnimationController}
 * 的运动状态机只写一份、所有角色共用。
 *
 * <h2>循环的 14 条 + 可选的落地两档 / 急停 / 飞行三态</h2>
 * 前 14 条都是<b>循环</b>（某一类运动一直在做）。剩下的都是可选的：
 * <ul>
 *   <li>{@link #landing()} / {@link #landingLight()} —— <b>落地两档</b>，
 *       只在「空中落地的那一瞬」播<b>一次</b>：狠狠落地用前者（深屈膝那种），
 *       走台阶、小跳用后者。所以走 {@link OneShot}，引擎里带自己的刻数，
 *       按落地那一刻的下坠速度分档（见 {@code PlayerAnimationController}）。</li>
 *   <li>{@link #runStop()} —— 跑步急停，同样播一次。</li>
 *   <li>{@link #fly()} / {@link #flyUp()} / {@link #flyDown()} —— <b>飞行三态</b>
 *       （水平巡航 / 上升 / 下降），都是循环。三条各自成段，引擎只按竖直位移挑一条。</li>
 * </ul>
 *
 * <p>没配这些槽的旧角色（{@link #of} 的老写法）行为完全不变：
 * 落地直接 idle、停车直接 idle、飞行照旧走跳/落，跟以前一样。
 * 新角色按需用 {@link #withTransitions} / {@link #withLightLanding} / {@link #withFlight} 接上。
 *
 * <p>移植自参考2 的同名类，仅把 GeckoLib 4 的 API 换成 GeckoLib 5（包名 software.bernie → com.geckolib）。
 */
public record LocomotionAnims(
        RawAnimation idle,
        RawAnimation walk,
        RawAnimation run,
        RawAnimation walkBack,
        RawAnimation crouch,
        RawAnimation crouchWalk,
        RawAnimation sleep,
        RawAnimation climb,
        RawAnimation waterIdle,
        RawAnimation waterWalk,
        RawAnimation waterWalkBack,
        RawAnimation swim,
        RawAnimation jump,
        RawAnimation jumpDown,
        @Nullable OneShot landing,
        @Nullable OneShot landingLight,
        @Nullable OneShot runStop,
        @Nullable RawAnimation fly,
        @Nullable RawAnimation flyUp,
        @Nullable RawAnimation flyDown) {

    /**
     * 一段「播一次、然后回常态」的过渡动画。
     *
     * @param animation 用 {@code thenPlayAndHold} 包好的动画（播完停在最后一帧，
     *                  避免「播完即失效」那中间一帧露出原始姿态）
     * @param ticks     这段过渡在引擎里占多少刻 —— 到点就交回常态。
     *                  取动画时长略微往下取整：这些素材的尾巴本来就是「站着不动」的收势，
     *                  卡在收势刚开始那一刻切走最干净。
     */
    public record OneShot(RawAnimation animation, int ticks) {
    }

    /** 便捷工厂：把动画名一次性包成循环播放的 RawAnimation。 */
    public static LocomotionAnims of(
            String idle, String walk, String run, String walkBack,
            String crouch, String crouchWalk, String sleep, String climb,
            String waterIdle, String waterWalk, String waterWalkBack, String swim,
            String jump, String jumpDown) {
        return new LocomotionAnims(
                loop(idle), loop(walk), loop(run), loop(walkBack),
                loop(crouch), loop(crouchWalk), loop(sleep), loop(climb),
                loop(waterIdle), loop(waterWalk), loop(waterWalkBack), loop(swim),
                loop(jump), loop(jumpDown), null, null, null,
                null, null, null);
    }

    /**
     * 接上两条一次性过渡，返回新的集合（record 不可变，这里只是换个副本）。
     *
     * <p>只有动画文件里真有这两条的角色才该调它 —— 名字写错不会崩
     * （{@code AnimationAvailability} 会拦住缺失的名字、退回 idle），
     * 但那就是白播一次 idle，不如不接。
     *
     * @param landing    落地缓冲的动画名（{@code null} = 不接）
     * @param landingTicks 落地缓冲占多少刻
     * @param runStop    跑步急停的动画名（{@code null} = 不接）
     * @param runStopTicks 跑步急停占多少刻
     */
    public LocomotionAnims withTransitions(@Nullable String landing, int landingTicks,
                                            @Nullable String runStop, int runStopTicks) {
        return new LocomotionAnims(
                idle, walk, run, walkBack, crouch, crouchWalk, sleep, climb,
                waterIdle, waterWalk, waterWalkBack, swim, jump, jumpDown,
                landing == null ? null : new OneShot(playOnce(landing), landingTicks),
                landingLight,
                runStop == null ? null : new OneShot(playOnce(runStop), runStopTicks),
                fly, flyUp, flyDown);
    }

    /**
     * 补上「轻落地」那一档。
     *
     * <p>老角色只接重档就够了（走台阶不该播落地动画）；申鹤这种两档都有的角色
     * 在这之后调一次，落地时引擎按下坠速度二选一。
     *
     * @param landingLight      轻落地那一档的动画名（{@code null} = 不接，退回只重档）
     * @param landingLightTicks 轻落地占多少刻（素材很短，一般 2~3 刻）
     */
    public LocomotionAnims withLightLanding(@Nullable String landingLight, int landingLightTicks) {
        return new LocomotionAnims(
                idle, walk, run, walkBack, crouch, crouchWalk, sleep, climb,
                waterIdle, waterWalk, waterWalkBack, swim, jump, jumpDown,
                landing, landingLight == null ? null : new OneShot(playOnce(landingLight), landingLightTicks),
                runStop, fly, flyUp, flyDown);
    }

    /**
     * 接上飞行三态（水平巡航 / 上升 / 下降），三条都是循环。
     *
     * <p>素材里没有这三条的角色别调它：不接时引擎完全不看飞行状态，
     * 玩家飞起来照样播跳/落那两条，跟以前一样。
     *
     * @param fly     水平飞行（用户口径：就是座椅那套 fly）
     * @param flyUp   上升时多出来的一段
     * @param flyDown 下降时多出来的一段
     */
    public LocomotionAnims withFlight(@Nullable String fly, @Nullable String flyUp, @Nullable String flyDown) {
        return new LocomotionAnims(
                idle, walk, run, walkBack, crouch, crouchWalk, sleep, climb,
                waterIdle, waterWalk, waterWalkBack, swim, jump, jumpDown,
                landing, landingLight, runStop,
                fly == null ? null : loop(fly),
                flyUp == null ? null : loop(flyUp),
                flyDown == null ? null : loop(flyDown));
    }

    /**
     * 参考2 / 本项目动画 json 里的通用常态动画名。
     *
     * <p>落地缓冲也接上了（{@code landing}，0.1s ≈ 2 刻）：落体速度超过
     * {@code LANDING_MIN_FALL_SPEED} 时播一次「深蹲 → 立刻站直」，播完交回 idle。
     *
     * <p><b>2026-09-27 用户口径</b>：先是「落地延迟太高」（原素材 0.45s / 9 刻，落地后
     * 还端着落地姿势一小会），压到 0.3s / 6 刻之后用户又点名「改 0.1 吧」——
     * 现在素材只剩两帧（0.0 深蹲 → 0.1 站直，见 {@code tools/shorten-default-landing.py}），
     * 这里的刻数跟着写成 2，两边对齐。
     * 跑步急停（runStop）暂时没接：素材里没有对应的收停短片。
     */
    public static final LocomotionAnims DEFAULT = of(
            "idle", "walk", "run", "walk_back",
            "crouch", "crouch_walk", "sleep", "climb",
            "water", "water_walk", "water_walk_back", "swim",
            "jump", "jump_down").withTransitions("landing", 2, null, 0);

    private static RawAnimation loop(String animationName) {
        return RawAnimation.begin().thenLoop(animationName);
    }

    /** 一次性过渡用的包装：播完停在最后一帧，等状态机（或 {@code ticks} 到点）接手。 */
    private static RawAnimation playOnce(String animationName) {
        return RawAnimation.begin().thenPlayAndHold(animationName);
    }
}
