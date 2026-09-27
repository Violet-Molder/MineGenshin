package com.linweiyun.genshin.core.system.control;

import com.linweiyun.genshin.core.system.poise.impact.ImpactLevel;
import net.minecraft.world.damagesource.DamageSource;
import org.jetbrains.annotations.Nullable;

/**
 * <b>一次控制请求</b> —— 「什么效果、削多少韧、多强的冲击、从哪来、按住多久」这一个包裹。
 *
 * <h2>为什么削韧也在这里</h2>
 * 用户口径（2026-09-25）：「<b>所有控制类效果走一个入口</b>，然后实体在这里判断
 * 是削韧、还是控制、还是直接免疫拦住。」
 * 所以一次命中不是「先去削韧、再去控制」两条路，而是<b>同一个请求</b>进
 * {@link Controllable#applyControl}：
 *
 * <pre>
 *   一次命中 = { 削韧 50, 冲击 轻击(2), 来源 = 玩家 }
 *     → 实体判定：有盾 / 免疫 → 什么都不做
 *                 没破韧      → 只有削韧那一笔落地
 *                 已破韧      → 削韧落地 + 打断动作 + 击退
 * </pre>
 *
 * <h2>四种请求</h2>
 * <table border="1">
 *   <caption>工厂方法与用途</caption>
 *   <tr><th>工厂</th><th>类型</th><th>什么时候用</th></tr>
 *   <tr><td>{@link #hit}</td><td>{@link ControlType#SOFT}</td>
 *       <td>每一次命中（普攻、技能、反应伤害上的那一下）—— 削韧与冲击一起带进来</td></tr>
 *   <tr><td>{@link #interrupt}</td><td>{@link ControlType#SOFT}</td>
 *       <td>只打断、不推（{@link #hit} 不带冲击的那种）</td></tr>
 *   <tr><td>{@link #poise}</td><td>{@link ControlType#POISE}</td>
 *       <td>只削韧不带控制（聚怪那笔初始削韧、反应自己那笔削韧）</td></tr>
 *   <tr><td>{@link #skill}</td><td>{@link ControlType#LEVITATE} / {@link ControlType#GATHER}</td>
 *       <td>悬浮与牵引：每刻问一次门，位移由节点自己执行</td></tr>
 *   <tr><td>{@link #freeze}</td><td>{@link ControlType#FREEZE}</td>
 *       <td>冻结的 NoAI（冻住 / 解冻）—— 和别的控制走同一道门，但无视韧性</td></tr>
 * </table>
 *
 * <h2>命中类请求会消费破绽窗口</h2>
 * 只有 {@link #hitDriven()} 为真的请求（{@link ControlType#SOFT}）会认破绽窗口，
 * 因为窗口是留给人打的，不是留给风场每刻刷的。
 *
 * @param type   效果类别
 * @param poise  这一笔要削多少韧（还没乘过霸体系数的原始值）；≤0 表示这一下不削韧
 * @param impact 这一下的冲击（力值 → 冲量）；技能类与纯削韧请求为 {@code null}
 * @param source 伤害来源，用于破韧日志与「往哪边推」；可以为 {@code null}
 * @param ticks  这一下要把目标按住多少刻；≤0 表示用默认值
 * @param frozen 只有 {@link ControlType#FREEZE} 用它：true = 冻住、false = 解冻
 */
public record ControlRequest(ControlType type, float poise, @Nullable ImpactLevel impact,
                             @Nullable DamageSource source, int ticks, boolean frozen) {

    /**
     * 一次命中把目标按住多少刻 —— <b>占位值</b>，随手感调：10 刻 = 0.5 秒。
     *
     * <p>它只决定「按住多久」，不决定「打不断」—— 那是抗打断系数与
     * {@link Controllable#blocksControl} 的事。破韧期间每挨一下都会续租一次，
     * 所以连续挨打的目标会一直被按着。
     */
    public static final int HIT_TICKS = 10;

    /**
     * 一次命中：削韧与冲击一起带进来，控制那一半在破韧之后才生效。
     *
     * @param poise  这一下的削韧值（按武器表 / 招式系数算好后的原始值）
     * @param impact 这一下的冲击；{@code null} 表示<b>只打断、不推</b>
     *               （打断强度按微颤 1 算，位移一点都不给）；
     *               要「既不打断也不推」就传 {@link ImpactLevel#NONE}
     * @param source 伤害来源；{@code null} 表示没有攻击者（也就没有推动方向）
     */
    public static ControlRequest hit(float poise, @Nullable ImpactLevel impact,
                                     @Nullable DamageSource source) {
        return new ControlRequest(ControlType.SOFT, poise, impact, source, HIT_TICKS, true);
    }

    /**
     * <b>只打断、不推</b> —— 用户口径「击退是次要的，不一定有，
     * 最主要的是打断他的动作」的直接写法。
     *
     * <p>等价于 {@code hit(poise, null, source)}：打断强度按微颤 1 算，
     * 所以能打断普通敌人（系数 1），打不断配了 3~5 的精英怪 ——
     * 想打断得更重就给一个真的冲击等级（{@code hit}），或者
     * {@link ImpactLevel#custom(int, float, float) 自定}一个力值为 0 的等级。
     */
    public static ControlRequest interrupt(float poise, @Nullable DamageSource source) {
        return hit(poise, null, source);
    }

    /** 只削韧、不带控制（聚怪的初始削韧、反应自己那笔削韧）。 */
    public static ControlRequest poise(float poise, @Nullable DamageSource source) {
        return new ControlRequest(ControlType.POISE, poise, null, source, 0, true);
    }

    /** 技能持续施加的控制（悬浮 / 聚怪牵引）：只过门，位移由节点自己执行。 */
    public static ControlRequest skill(ControlType type) {
        return new ControlRequest(type, 0f, null, null, 0, true);
    }

    /**
     * 技能持续施加的控制，并声明「这一轮我要按住它这么多刻」——
     * 只有 {@link ControlType#pausesPoiseReset()} 为真的类别会拿它去暂停韧性恢复。
     */
    public static ControlRequest skill(ControlType type, int ticks) {
        return new ControlRequest(type, 0f, null, null, ticks, true);
    }

    /**
     * 冻结 / 解冻 —— {@code ColdElement} 每 tick 按「身上有没有冻元素」发一次。
     *
     * <p>它和别的控制走同一道门（所以首领一类实体能在这里免疫冻结），
     * 但<b>不看韧性</b>：冻结可以无视韧性直接成立。
     * {@code frozen = false} 是解冻，永远放行 —— 不然目标会被永远冻住。
     */
    public static ControlRequest freeze(boolean frozen) {
        return new ControlRequest(ControlType.FREEZE, 0f, null, null, 0, frozen);
    }

    /** 这一下是不是「命中带来的」—— 决定要不要消费破绽窗口、要不要执行打断与击退。 */
    public boolean hitDriven() {
        return type != null && type.hitDriven();
    }

    /** 有效按住时长：没写就用 {@link #HIT_TICKS}。 */
    public int holdTicks() {
        return ticks > 0 ? ticks : HIT_TICKS;
    }
}
