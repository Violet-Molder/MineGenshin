package com.linweiyun.genshin.core.character.allweapon.linweiyun;

import com.linweiyun.genshin.core.system.combat.animation.config.CharacterAnimations;
import com.linweiyun.genshin.core.system.combat.animation.config.LocomotionAnims;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * 林薇云的动画接线。
 *
 * <p>常态部分直接复用 {@link LocomotionAnims#DEFAULT}（名字全通用：idle / walk / run / jump /
 * landing …），另外把<b>飞行三态</b>接上：她的动画文件里有 {@code fly}，
 * 所以三条都用它 —— 不接的话飞着上升会掉进「空中」分支去播 jump
 * （就是「飞行时不停触发跳跃动画」那个毛病）。
 *
 * <p>她的动画文件里另有五条按形态区分的 {@code fly_sword / fly_polearm / fly_claymore /
 * fly_catalyst / fly_bow}，目前还没有按形态切换飞行片段的地方（{@code LocomotionAnims}
 * 存的是固定名字），先用通用那条 {@code fly}；以后要做形态化飞行，从这里换实现即可。
 *
 * <p>动作（普攻 / 战技 / 爆发）走通用 {@code ResourceDrivenActionHandler}，
 * 所以 {@link #specialAnims()} 先留空 —— 和兜底配置一样「一律按常态处理」。
 */
public final class LinweiyunAnimations implements CharacterAnimations {

    public static final LinweiyunAnimations INSTANCE = new LinweiyunAnimations();

    /** 常态 + 飞行三态（三条都指向她唯一的 {@code fly}）。 */
    public static final LocomotionAnims LOCOMOTION = LocomotionAnims.DEFAULT.withFlight("fly", "fly", "fly");

    public static final Map<String, String> STATE_SOUNDS = Map.ofEntries();

    private LinweiyunAnimations() {
    }

    @Override
    public LocomotionAnims locomotion() {
        return LOCOMOTION;
    }

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
        return STATE_SOUNDS.get(stateName);
    }
}
