package com.linweiyun.genshin.core.character.talent;

import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * <b>「技能被放出来了」的通知点</b> —— 把 {@link SkillBase} 的招式执行
 * 和「表现层想干点什么」解耦开。
 *
 * <h2>为什么需要这一层</h2>
 * 招式本体（{@code SkillBase#elementalSkill} / {@code elementalBurst}）跑在<b>公共包</b>里，
 * 而特效、音效、镜头这些都在<b>客户端</b>。公共包<b>不可以</b>直接引用客户端类 ——
 * 专用服务器加载到这个类就会崩（项目约定里明确写过这一条）。
 *
 * <p>所以这里只放一个纯公共的接口 + 一张监听表：招式执行完调用 {@link #fire}，
 * 客户端在自己的初始化里 {@link #register} 一个监听器去放特效。
 * <b>专用服务器上没有任何监听器，{@code fire} 就是一次空循环。</b>
 *
 * <h2>用法</h2>
 * <pre>{@code
 * // 客户端初始化（Dist.CLIENT 的 @EventBusSubscriber 里）
 * SkillCastHooks.register(TestCharacterFx::onSkillCast);
 * }</pre>
 *
 * <p>已经接上的招式：元素战技点按 / 长按、元素爆发（见 {@code TestSkillLogic}）。
 */
public final class SkillCastHooks {

    /** 一次技能释放。 */
    @FunctionalInterface
    public interface Listener {
        /**
         * @param player    释放者（调用方已保证在客户端侧也有一份同 tick 的执行）
         * @param skillType 点按 / 长按的区分，与 {@code SkillBase#elementalSkill} 的入参一致
         *                  （{@code < 1000} 为点按，{@code 1000} 为长按）；爆发固定传 {@code 0}
         */
        void onSkillCast(Player player, int skillType);
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    public static void register(Listener listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    public static void unregister(Listener listener) {
        LISTENERS.remove(listener);
    }

    /** 由招式本体调用；两端都会走到，客户端侧才有人听。 */
    public static void fire(Player player, int skillType) {
        for (Listener listener : LISTENERS) {
            listener.onSkillCast(player, skillType);
        }
    }

    private SkillCastHooks() {
    }
}
