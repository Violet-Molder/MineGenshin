package com.linweiyun.genshin.client.combat.state;

import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.Nullable;

/**
 * 动作锁的「定身」输入层操作：顶替 / 还原本地玩家的输入实例，并提供整份输入清零。
 *
 * <p>状态机只决定「什么时候该冻、什么时候该解」，实际的替换动作由这里完成。
 */
public final class ActionInputFreeze {

    /** 定身期间顶替玩家输入的共享替身。 */
    private static final Input FROZEN_INPUT = new FrozenInput();

    @Nullable
    private static Input savedInput;

    private ActionInputFreeze() {
    }

    /**
     * 用零向量替身顶替玩家当前输入实例。
     *
     * <p>重复调用不会覆盖已保存的原实例 —— 否则「冻结期间又被冻结一次」
     * 会把替身自己存成原实例，解冻后玩家就永久定身了。
     */
    public static void install(LocalPlayer player) {
        if (player.input != FROZEN_INPUT) {
            savedInput = player.input;
            player.input = FROZEN_INPUT;
        }
    }

    /**
     * 移动锁结束后把玩家原本的输入实例还回去（由每 tick 的状态机统一处理）。
     *
     * <p>{@code savedInput} 为空时<b>什么都不做</b>：宁可多冻一 tick，也不能塞一个
     * 没被保存过的空输入进去 —— 那会让玩家这一局再也动不了。
     */
    public static void restore(LocalPlayer player) {
        if (player.input != FROZEN_INPUT || savedInput == null) {
            return;
        }
        player.input = savedInput;
        savedInput = null;
    }

    /**
     * 把一份输入实例的方向、跳跃、下蹲全部清零。
     *
     * @param input 要清空的输入实例，通常是事件里的那一份
     */
    public static void clear(Input input) {
        input.leftImpulse = 0.0F;
        input.forwardImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    /**
     * 定身用的输入替身：移动向量恒为零向量，前进冲量为零，各方向键恒为松开。
     */
    private static final class FrozenInput extends Input {
        @Override
        public Vec2 getMoveVector() {
            return Vec2.ZERO;
        }

        @Override
        public boolean hasForwardImpulse() {
            return false;
        }
    }
}
