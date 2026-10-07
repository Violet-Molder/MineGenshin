package com.linweiyun.genshin.core.system.combat.animation.config;

import software.bernie.geckolib.animation.RawAnimation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * 一个角色的动画配置入口：常态动画集合 + 动作动画名单 + 过渡刻数 + 音效表。
 *
 * <p>由角色的动画常量类实现（如 {@code VesnaAnimations}），交给 {@code PlayerAnimationController}
 * 使用，使运动状态机逻辑全工程只写一份。
 *
 * <p>移植自参考2 的同名接口。
 */
public interface CharacterAnimations {

    /** 常态动画集合。 */
    LocomotionAnims locomotion();

    /**
     * 常态动画集合，<b>按玩家现状挑</b>；默认就是 {@link #locomotion()}。
     *
     * <p>给人形按「当前形态」换片段的角色用：林薇云有六种武器形态，飞行时该播
     * {@code fly_sword / fly_idle_polearm / fly_claymore / fly_catalyst / fly_bow}（拳头用通用 {@code fly}），
     * 静止的 {@link #locomotion()} 表达不了这件事 —— 它拿不到玩家。
     */
    default LocomotionAnims locomotionFor(Player player) {
        return locomotion();
    }

    /**
     * <b>水平飞行</b>（既不上升也不下降）时用哪条飞行片段；默认就是 {@link LocomotionAnims#fly()}。
     *
     * <p>给「飞行分好几档」的角色用：林薇云长柄形态有站着悬停 / 往前飞 / 疾跑冲刺三条，
     * 而 {@link LocomotionAnims} 只有 fly / flyUp / flyDown 三格，表达不了「水平这一档内部还分几种」。
     *
     * <p>返回的名字查不到动画时，调用方会退回 idle（见 {@code PlayerAnimationController}）。
     *
     * @param moving 这一帧是不是在水平移动（本地玩家看输入，别的玩家看位移）
     */
    default RawAnimation flyVariant(Player player, LocomotionAnims loco, boolean moving) {
        return loco.fly();
    }

    /**
     * 「特殊动画」名单（普攻 / 战技 / 闪避 / 大招）。
     *
     * <p>这些动画进出时硬切（0 刻过渡）而非平滑过渡，否则姿势会被插值混成四不像。
     */
    Set<String> specialAnims();

    /** 常态动画互相切换时的过渡刻数。 */
    int exitTransitionTicks();

    /**
     * 第一人称动画配置（每个角色一个开关）。
     *
     * <p>默认关。开了之后：写了 {@code fp_*} 动画就用它，没写就复用普通动画 + 转机位。
     * 详见 {@link FirstPersonAnims}。
     */
    default FirstPersonAnims firstPerson() {
        return FirstPersonAnims.DISABLED;
    }

    /** 某个动作状态对应的音效 id（如 {@code "minegenshin:vesna_attack_1"}），没有则返回 {@code null}。 */
    @Nullable
    String soundForState(String stateName);
}
