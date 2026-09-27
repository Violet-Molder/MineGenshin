package com.linweiyun.genshin.core.system.control;

/**
 * <b>静止窗口的第三层接入点：非 AI 管线的动作压制。</b>
 *
 * <h2>它补的是哪一段</h2>
 * 打断的第一层是 AI 闸门（{@code Mob.serverAiStep()} 在窗口内被整段掐掉），
 * 所以<b>走 AI 管线</b>的动作全都会被拦住 —— 包括 Goal、Brain、
 * {@code customServerAiStep} 里的读条。
 *
 * <p>但原版有一类动作<b>根本不经过 AI</b>，写在生物自己的 {@code tick()} /
 * {@code aiStep()} 里，闸门再怎么掐也拦不住它们：
 * <ul>
 *   <li><b>苦力怕引信</b>：{@code Creeper.tick()} 自己推进私有的 {@code swell}，
 *       攒到 {@code maxSwell} 就炸 —— 原版自己的 {@code setNoAi(true)} 都挡不住；</li>
 *   <li><b>劫掠兽</b>：{@code attackTick / stunnedTick / roarTick} 在 {@code aiStep} 里递减，
 *       咆哮那一下是它自己发动的。</li>
 * </ul>
 *
 * <p>这些状态是私有字段，只能在 mixin 里摸。所以约定：<b>每为一种这样的生物写一个 mixin，
 * 让目标类实现本接口</b>（{@code @Mixin(Creeper.class) abstract class ... implements TickActionSuppressor}），
 * 把「回到静止」写进 {@link #minegenshin$suppressTickAction()}。
 * 之后 {@link Controllable#cancelOngoingAction} 会顺着这个接口自动把它一起按下去，
 * 不需要在核心代码里点名任何一只怪。
 *
 * <h2>谁该用它、谁不该</h2>
 * <ul>
 *   <li><b>原版 / 别的模组的生物</b>：用 mixin 实现本接口（核心代码碰不到它们的私有字段）；</li>
 *   <li><b>本模组自己的生物</b>：<b>不用</b>这个接口 —— 直接在类里覆盖
 *       {@link Controllable#cancelOngoingAction}，纯 Java、不要 mixin。</li>
 * </ul>
 *
 * <p>方法名带 {@code minegenshin$} 前缀是老规矩：mixin 往目标类里加的方法要避开别人的命名。
 */
public interface TickActionSuppressor {

    /**
     * 把这一类「不走 AI 管线」的动作当场作废（每刻都会被调一次，必须幂等）。
     */
    void minegenshin$suppressTickAction();
}
