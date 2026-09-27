package com.linweiyun.genshin.core.system.control;

/**
 * 控制效果的类别 —— 从「最弱」排到「最强」，因为门槛各不相同。
 *
 * <p>这是用户口径里「软控制 / 强控制」那两档的落点：软控（僵直）和普通控制
 * 都得等破韧，**聚怪牵引也不例外** —— 它的特殊性是「自己会削韧」，见 {@link #GATHER}。
 *
 * <table border="1">
 *   <caption>未破韧 / 已破韧时的表现</caption>
 *   <tr><th>类别</th><th>例子</th><th>谁在执行</th><th>未破韧</th><th>已破韧</th><th>会不会暂停韧性恢复</th></tr>
 *   <tr><td>{@link #POISE}</td><td>只削韧不带控制（聚怪的初始削韧、反应自己那笔削韧）</td>
 *       <td>实体内置方法</td><td>生效（这就是它本身）</td><td>生效</td><td>不会</td></tr>
 *   <tr><td>{@link #SOFT}</td><td>每一次命中的打断动作（破韧期间随便一下都算）</td>
 *       <td>实体内置方法</td><td>不生效</td><td>生效</td><td>不会</td></tr>
 *   <tr><td>{@link #IMPACT}</td><td>纯击退 / 击飞（暂无调用方，预留给不带打断的推力）</td>
 *       <td>实体内置方法</td><td>不生效</td><td>生效</td><td>不会</td></tr>
 *   <tr><td>{@link #LEVITATE}</td><td>长时间悬浮（风场、泡影）</td>
 *       <td>技能节点</td><td>不生效</td><td>生效</td><td><b>会</b></td></tr>
 *   <tr><td>{@link #GATHER}</td><td>聚怪牵引（星涡一类）</td>
 *       <td>技能节点</td><td>不生效，<b>但照常削韧</b></td><td>生效</td><td><b>会</b></td></tr>
 *   <tr><td>{@link #FREEZE}</td><td>冻结的 NoAI（冻住 / 解冻）</td>
 *       <td>实体内置方法</td><td>生效（<b>无视韧性</b>）</td><td>生效</td><td>不会</td></tr>
 * </table>
 *
 * <p><b>「命中带来」与「技能持续施加」</b>由 {@link #hitDriven()} 区分，这条分界管两件事：
 * ① 命中类会消费破绽窗口（见 {@code ControlService.openGap}），技能类不会把它刷掉；
 * ② 命中类的效果由<b>实体内置方法</b>执行（{@code Controllable.interruptAction} /
 * {@code knockback}），技能类只过门、位移由节点自己算。
 *
 * <p>「会不会暂停」是给 {@link ControlService#onStrongControlApplied} 用的：
 * 强控按住目标的期间不算它的恢复时间，破绽窗口因此被延长。
 */
public enum ControlType {

    /**
     * <b>削韧</b> —— 只把韧性条往下推，本身不是控制。
     *
     * <p>它在这里是因为用户口径「所有控制类效果走一个入口，实体在这里判断
     * 是削韧、还是控制、还是直接免疫拦住」：一次命中把削韧与冲击一起带进
     * {@code Controllable#applyControl}，没破韧时判定就是这一档。
     */
    POISE(false, false),

    /** 软控：<b>打断动作</b> —— 一次命中的主效果。破韧前完全不生效。 */
    SOFT(true, false),

    /** 击退 / 击飞（暂无调用方）。破韧前不生效；推不推得动只看重量，与打断门槛无关。 */
    IMPACT(true, false),

    /** 悬浮一类强控：长时间把目标按在空中。破韧前不生效，生效期间暂停韧性恢复。 */
    LEVITATE(false, true),

    /**
     * 聚怪牵引 —— 破韧前不生效，但它<b>自己会削韧</b>。
     *
     * <p>它的特殊性不在「能绕过韧性」，而在「它是唯一自带削韧的控制」：
     * 一次聚怪先砸下一笔高额初始削韧（等级越高越大，见
     * {@code content.skill_node.GatherPull}），把目标打空、打成破韧，随后的牵引才生效。
     * 所以「低等级聚怪拉不动硬怪」不是另一道门槛，而是<b>那笔初始削韧不够</b> ——
     * 拉不动，是因为没打破。
     */
    GATHER(false, true),

    /**
     * <b>冻结</b> —— 就是那个把 {@code NoAI} 打开的禁 AI。
     *
     * <p>它走同一道门，但判定里<b>不看韧性</b>（用户口径「冻结可以无视韧性直接冻结」），
     * 只问实体吃不吃冻结：首领一类 {@code blocksControl} 为真的目标直接免疫；
     * 解冻（{@code frozen = false}）永远放行。
     */
    FREEZE(false, false);

    private final boolean hitDriven;
    private final boolean pausesPoiseReset;

    ControlType(boolean hitDriven, boolean pausesPoiseReset) {
        this.hitDriven = hitDriven;
        this.pausesPoiseReset = pausesPoiseReset;
    }

    /**
     * 这一类控制是不是「命中带来的」—— 随一次伤害一起结算，
     * 效果由实体内置方法执行，并且会消费破绽窗口。
     *
     * <p>false 的是「技能每刻持续施加」的（悬浮、牵引）：只过门，位移由节点执行。
     */
    public boolean hitDriven() {
        return this.hitDriven;
    }

    /** 这种控制生效期间，要不要把破韧驻留计时按住。 */
    public boolean pausesPoiseReset() {
        return this.pausesPoiseReset;
    }
}
