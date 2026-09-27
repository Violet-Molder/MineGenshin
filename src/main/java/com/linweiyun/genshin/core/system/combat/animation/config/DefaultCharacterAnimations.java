package com.linweiyun.genshin.core.system.combat.animation.config;

import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * 动画系统的兜底配置：给「只提供了渲染数据、还没写 Java 动画系统」的角色用。
 *
 * <p>只解决渲染问题（常态动画用最通用的名字），不提供任何动作动画、音效或动作编排。
 * 移植自参考2 的同名类。
 */
public final class DefaultCharacterAnimations implements CharacterAnimations {

    public static final DefaultCharacterAnimations INSTANCE = new DefaultCharacterAnimations();

    /**
     * 常态动画。
     *
     * <p><b>飞行三态必须接</b>：{@code PlayerAnimationController} 的飞行分支要求
     * {@code loco.fly() != null}，没接就会掉进「空中」分支 —— 而那里按竖直速度在
     * {@code jump} / {@code jump_down} 之间切，表现就是<b>飞着上升时不停播跳跃动画</b>。
     * 共用模型（{@code character/default/default.animation.json}）里本来就有 {@code fly}，
     * 所以三条都用它；确实没有这条动画的角色会被 {@code AnimationAvailability} 退到 idle。
     */
    private static final LocomotionAnims LOCOMOTION = LocomotionAnims.DEFAULT.withFlight("fly", "fly", "fly");

    private DefaultCharacterAnimations() {
    }

    @Override
    public LocomotionAnims locomotion() {
        return LOCOMOTION;
    }

    /** 空集合：不知道哪些是动作动画，一律按常态处理。 */
    @Override
    public Set<String> specialAnims() {
        return Set.of();
    }

    @Override
    public int exitTransitionTicks() {
        return 5;
    }

    @Nullable
    @Override
    public String soundForState(String stateName) {
        return null;
    }
}
