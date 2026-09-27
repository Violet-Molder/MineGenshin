package com.linweiyun.genshin.client.render.character.appearance;

import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * 木偶背后的<b>发条</b>（骨骼 {@code FKey}）的<b>常态恒转</b>。
 *
 * <h2>它和别的道具不是一类东西</h2>
 * 坐骑 / 法吉偶 / 屏幕 / 红茶都是「某些状态才出场」（见 {@link CharacterPropBones}），
 * 发条反过来：<b>不管什么状态都得一直转</b>（用户口径「永远旋转」）。所以它既不能挂进
 * 那张按状态名出场的名单，也不能靠动画关键帧 —— 仓库里 101 段动画<b>没有任何一段</b>
 * 写过 {@code Key} / {@code FKey} / {@code ysmGlowKey}（{@code rg} 0 命中），
 * 光靠动画它就会永远停在 rest 姿态。给每段补关键帧则是 101 处的重复劳动，漏一处就停一次。
 *
 * <h2>为什么写在渲染期</h2>
 * 角度是「当前时刻」的纯函数：{@code 角度 = −360° × 已过刻数 ÷ 100}。
 * 渲染期每帧现算，天然覆盖闲置、奔跑、跳跃、飞行、战斗、重击、游泳，
 * 以及配置页 / 装备页预览 —— 不需要新增动画条目，也就不涉及动画 key 的命名问题。
 * 快照旋转是<b>绕骨骼自身 pivot 的增量</b>，{@code FKey} 在 geo 里没有基础旋转、
 * 也没有任何动画写它，所以写进去的就是绝对值。
 *
 * <h2>速度与方向抄自素材</h2>
 * 母本 {@code animations/main.animation.json} 的 {@code pre_parallel5}：
 * {@code FKey → {"0.0": 0.0, "5.0": [0.0, -360.0, 0.0]}}，{@code loop: true}，
 * 也就是 <b>5 秒一圈、绕 Y 轴负向</b>（俯视看是顺时针，和用户给的参考视频一致）。
 */
public final class CharacterPuppetBones {

    /**
     * 发条本体（齿轮 + 它的发光层 {@code ysmGlowKey} 都是它的子骨，转它一个就够）。
     *
     * <p>骨骼链：{@code Body2 → Key → FKey → ysmGlowKey}。
     * {@code Key} 是 0 方块的挂点，基础旋转 {@code [-90, 17.5, -90]} —— 不要写它，
     * 那记旋转是「把刻度盘摆正」用的，改它就是连姿态一起改。
     */
    public static final String WIND_UP_BONE = "FKey";

    /** 转一圈的秒数 —— 照抄母本 {@code pre_parallel5} 的 5.0 秒 / 360°。 */
    public static final float SECONDS_PER_TURN = 5f;

    /** 一圈多少刻（GeckoLib 用 20 刻 = 1 秒）。 */
    private static final float TICKS_PER_TURN = SECONDS_PER_TURN * 20f;

    /** 每刻转过的角度。负号 = 顺时针（与母本 {@code [0, -360, 0]} 同向）。 */
    private static final float DEGREES_PER_TICK = -360f / TICKS_PER_TURN;

    /** 没有玩家时的空实现（预览 / 异常路径）。 */
    private static final RenderPassInfo.BoneUpdater<GeoRenderState> NONE = (renderPassInfo, snapshots) -> {
    };

    private CharacterPuppetBones() {
    }

    /**
     * 这一帧的发条角度。
     *
     * <p>用 {@code player.tickCount + partialTick} 而不是只看 {@code tickCount}：
     * 不然 20 刻/秒的整数台阶会让齿轮在 60fps 下「一顿一顿」，加上插值才是匀速转。
     * 插值量从渲染状态里取（{@code performRenderPass} 传进来的那个），
     * 所以第三人称、第一人称、页面预览三条路拿到的都是各自那一趟的正确值。
     */
    public static RenderPassInfo.BoneUpdater<GeoRenderState> updaterFor(@Nullable Player player) {
        if (player == null) {
            return NONE;
        }
        return (renderPassInfo, snapshots) -> {
            final float ticks = player.tickCount + renderPassInfo.renderState().getPartialTick();
            snapshots.ifPresent(WIND_UP_BONE, snapshot ->
                    snapshot.setRotation(0f, DEGREES_PER_TICK * ticks, 0f));
        };
    }
}
