package com.linweiyun.genshin.core.system.combat.action.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据驱动的动作配置（来自 character/{id}/behavior/ 下的 JSON）。
 * 双端安全（只存字符串与数值，不引用客户端/服务端类型）。
 * <p>
 * 包含普攻连段、战技、大招、闪避的完整配置。
 */
public final class CharacterActionData {

    private final ComboData combo;
    private final SkillData skill;
    private final BurstData burst;
    private final DodgeData dodge;

    public CharacterActionData(ComboData combo, SkillData skill, BurstData burst, DodgeData dodge) {
        this.combo = combo;
        this.skill = skill;
        this.burst = burst;
        this.dodge = dodge;
    }

    public ComboData combo() { return combo; }
    public SkillData skill() { return skill; }
    public BurstData burst() { return burst; }
    public DodgeData dodge() { return dodge; }

    // ==================== 兜底配置 ====================

    /**
     * 没写 {@code XxxResources.ACTION_DATA} 的角色的通用动作配置。
     *
     * <p>没有它的话 {@code SkillBase.buildDefaultActionSet()} 会返回空集，
     * 角色的普攻 / 战技 / 大招会全部静默失效（按键毫无反应）。
     *
     * <p>时序只是「够用」的量级：动画名走通用命名，角色动画文件里没有对应名字时
     * {@code AnimationAvailability} 会拦住切换，只结算伤害 + 摆臂。
     * 想调手感就在角色自己的 {@code XxxResources} 里写一份真正的配置。
     *
     * @param maxCombo 该角色天赋声明的连段数
     */
    public static CharacterActionData fallback(int maxCombo) {
        int stages = Math.max(1, maxCombo);

        Map<Integer, ActionStep> comboSteps = new LinkedHashMap<>();
        for (int stage = 1; stage <= stages; stage++) {
            comboSteps.put(stage, new ActionStep(
                    "attack_" + stage, 20, 0, 2,
                    List.of(),
                    List.of(new Hit(4, 0, 1.5, 3.0)),
                    List.of(),
                    0, 0, 0, 8
            ));
        }

        ActionStep skillStep = new ActionStep(
                "skill", 25, 6, 3,
                List.of(),
                List.of(new Hit(8, 0, 1.5, 3.0)),
                List.of(),
                0, 0, 0, 0
        );

        ActionStep burstStep = new ActionStep(
                "burst", 40, 40, 4,
                List.of(),
                List.of(new Hit(10, 0, 1.5, 4.0)),
                List.of(),
                0, 0, 0, 0
        );

        ActionStep dodgeStep = new ActionStep(
                "dodge_front", 12, 0, 3,
                List.of(new Move(0, 1.5)),
                List.of(),
                List.of(),
                0, 0, 0, 0
        );

        return new CharacterActionData(
                new ComboData(stages, comboSteps),
                new SkillData(skillStep, null),
                new BurstData(burstStep, 80f),
                new DodgeData(dodgeStep)
        );
    }

    // ==================== 普攻连段 ====================


    // ==================== 战技 ====================


    // ==================== 大招 ====================


    // ==================== 闪避 ====================


    // ==================== 单步动作 ====================

    /**
     * 一个动作步骤的完整配置。
     */

    // ==================== 交战形态（逐招式） ====================

    /**
     * 一个招式的<b>交战形态</b> —— 「这一招怎么接近目标」。
     *
     * <h2>只分两种大形态</h2>
     * <table border="1">
     *   <caption>近战 vs 远程</caption>
     *   <tr><th></th><th>近战招式（默认）</th><th>远程招式（{@link #ranged()}）</th></tr>
     *   <tr><td>突进</td><td>目标在攻击距离外 → 贴上去</td><td><b>永不突进</b></td></tr>
     *   <tr><td>转向</td><td>滑向目标（快但不瞬移）</td><td>一样滑向目标</td></tr>
     *   <tr><td>索敌距离</td><td>比攻击距离大（默认 6 格），差额交给突进补</td>
     *       <td><b>= 攻击距离</b>（够得着才锁）</td></tr>
     * </table>
     *
     * <h2>为什么判定单位是「招式」而不是「角色」</h2>
     * 同一个角色完全可能既有近战普攻又有远程战技（弓手砍人、法师抡杖）。
     * 按角色分只能二选一，按招式分则各招各的。
     *
     * <h2>怎么调</h2>
     * <pre>
     * // 1. 远程招式 —— 最常见的写法
     * step.ranged();
     *
     * // 2. 近战但不想突进（原地挥砍的大招）
     * step.withEngagement(Engagement.melee().withDash(false));
     *
     * // 3. 长枪：够得着 5 格，索敌 8 格，冲过去停 3 格
     * step.withAttackRange(5f).withEngagement(
     *         Engagement.melee().withAcquireRange(8).withDashProfile(3.0, 0.9, 24));
     *
     * // 4. 飞刀：索敌 12 格、只转向、甩得慢一点
     * step.withEngagement(
     *         Engagement.ranged().withAcquireRange(12).withAngles(40, 65).withTurnSpeed(28));
     * </pre>
     *
     * <p>所有数值为 {@link #USE_DEFAULT}（-1）时用类里的全局默认，见
     * {@code CombatTargeting} / {@code AttackApproach}。
     */

    // ==================== 伤害 ====================


    // ==================== 位移 ====================


    // ==================== 音效 ====================

    /**
     * 一条音效条目。
     *
     * <p>{@code name} 为 null/空 = <b>静音</b>，用来占一份概率（「这刀没喊」）。
     * {@code weight} 是随机权重，只有在 {@link SoundCue.PickMode#PICK_ONE} 里才有意义。
     */

    /**
     * 一个时间点上的音效编排 —— <b>序列与随机的组合单位</b>。
     *
     * <pre>
     * // 序列播四段，第三段从四个里随机抽一个（含「不出声」）
     * step.withSoundCue(SoundCue.play(0, "a"))
     *     .withSoundCue(SoundCue.play(6, "b"))
     *     .withSoundCue(SoundCue.pickOne(12, SoundRef.silent(), "c1", "c2", "c3"))
     *     .withSoundCue(SoundCue.play(18, "d"));
     * </pre>
     */
}