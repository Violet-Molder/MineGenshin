package com.linweiyun.genshin.core.character.sword.miyabi;

import com.linweiyun.genshin.core.system.combat.animation.config.CharacterAnimations;
import com.linweiyun.genshin.core.system.combat.animation.config.LocomotionAnims;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * 星见雅的动画配置：常态动画 + 动作动画名单 + 音效表。
 *
 * <p>动画名沿用美术素材里的名字（提供素材的联动模组与本 MOD 是同一套命名）：
 * 普攻 {@code attack_1..attack_5}、战技 {@code skill_energy} / {@code skill_energy_continue}、
 * 重击 {@code heavy_1..heavy_3}、大招 {@code final}、闪避 {@code dodge_*}。
 *
 * <p>音效也在素材那一侧（{@code imaginary_branch:miyabi_*}）；星见雅只有在联动模组加载时才会被注册，
 * 所以直接引用它的音效 id 是安全的。
 */
public final class MiyabiAnimations implements CharacterAnimations {

    public static final MiyabiAnimations INSTANCE = new MiyabiAnimations();

    private static final String SOUND_NS = "imaginary_branch:miyabi_";

    /**
     * 常态动画。
     *
     * <p>飞行三态必须接：{@code PlayerAnimationController} 的飞行分支要求 {@code loco.fly() != null}，
     * 没接就会掉进「空中」分支、按竖直速度在 jump / jump_down 之间切。
     */
    private static final LocomotionAnims LOCOMOTION = LocomotionAnims.DEFAULT.withFlight("fly", "fly", "fly");

    /** 动作动画：进出硬切，不参与常态过渡。 */
    public static final Set<String> SPECIAL_ANIMS = Set.of(
            "attack_1", "attack_2", "attack_3", "attack_4", "attack_5",
            "heavy_1", "heavy_2", "heavy_3",
            "skill_no_energy", "skill_energy", "skill_energy_continue",
            "dodge_front", "dodge_back", "dodge_left", "dodge_right",
            // 下落攻击：状态名是我们自己的，实际播普攻第二段（别名见 MiyabiResources#PLUNGE_CLIP）
            MiyabiResources.PLUNGE_STATE,
            "final");

    private static final Map<String, String> STATE_SOUNDS = Map.ofEntries(
            Map.entry("attack_1", SOUND_NS + "attack_1"),
            Map.entry("attack_2", SOUND_NS + "attack_2"),
            Map.entry("attack_3", SOUND_NS + "attack_3"),
            Map.entry("attack_4", SOUND_NS + "attack_4"),
            Map.entry("attack_5", SOUND_NS + "attack_5"),
            Map.entry("skill_energy", SOUND_NS + "skill"),
            Map.entry("skill_energy_continue", SOUND_NS + "skill_no_energy"),
            Map.entry("skill_no_energy", SOUND_NS + "skill_no_energy"),
            Map.entry("heavy_1", SOUND_NS + "atk_h1"),
            Map.entry("heavy_2", SOUND_NS + "atk_h2"),
            Map.entry("heavy_3", SOUND_NS + "atk_h3"),
            // 闪避：素材里没有星见雅的闪避音效事件（只有 alice / anbi / hutao / zhao 有），
            // 这里就不挂 —— 查不到事件时 playLocalSound 会静默跳过。
            Map.entry("final", SOUND_NS + "final"));

    private MiyabiAnimations() {
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

    @Nullable
    @Override
    public String soundForState(String stateName) {
        return STATE_SOUNDS.get(stateName);
    }
}
