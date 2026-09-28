package com.linweiyun.genshin.core.character.polearm.test;

import com.linweiyun.genshin.core.system.combat.animation.config.CharacterAnimations;
import com.linweiyun.genshin.core.system.combat.animation.config.LocomotionAnims;

import java.util.Set;

/**
 * test 的动画接线 —— 照抄林薇云的<b>长柄那一档</b>。
 *
 * <p>她的动画文件（test 复用同一份）里有：
 * <pre>
 * fly_idle_polearm   水平悬停
 * fly_up_polearm     上升
 * fly_down_polearm   下降
 * shenhe_attack_1/2  普攻两段（长柄动作数据引用的就是这两个名字）
 * </pre>
 *
 * <p>常态（idle / walk / run / jump …）直接复用 {@link LocomotionAnims#DEFAULT}。
 *
 * <p><b>必须登记</b>：在 {@code CharacterAnimationRegistry.registerAll()} 里加一行，
 * 否则这个角色的普攻 / 战技 / 爆发会<b>静默失效</b>
 * （{@code CharacterActions.getFor} 查不到就返回空编排，按键不播动画也不发请求）。
 */
public final class TestAnimations implements CharacterAnimations {

    public static final TestAnimations INSTANCE = new TestAnimations();

    /** 常态 + 飞行三态（长柄那三条）。 */
    public static final LocomotionAnims LOCOMOTION = LocomotionAnims.DEFAULT
            .withFlight("fly_idle_polearm", "fly_up_polearm", "fly_down_polearm");

    /** 硬切（0 刻过渡）的动作动画名单。 */
    public static final Set<String> SPECIAL_ANIMS = Set.of(
            "shenhe_attack_1",
            "shenhe_attack_2",
            "skill"
    );

    private TestAnimations() {
    }

    @Override
    public LocomotionAnims locomotion() {
        return LOCOMOTION;
    }

    @Override
    public Set<String> specialAnims() {
        return SPECIAL_ANIMS;
    }

    @Override
    public int exitTransitionTicks() {
        return 5;
    }

    @Override
    public String soundForState(String stateName) {
        return null;
    }
}