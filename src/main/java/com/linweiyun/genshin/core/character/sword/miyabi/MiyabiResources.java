package com.linweiyun.genshin.core.character.sword.miyabi;

import com.linweiyun.genshin.core.system.combat.action.data.ActionStep;
import com.linweiyun.genshin.core.system.combat.action.data.BurstData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterActionData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderData;
import com.linweiyun.genshin.core.system.combat.action.data.ComboData;
import com.linweiyun.genshin.core.system.combat.action.data.DodgeData;
import com.linweiyun.genshin.core.system.combat.action.data.Engagement;
import com.linweiyun.genshin.core.system.combat.action.data.Hit;
import com.linweiyun.genshin.core.system.combat.action.data.Move;
import com.linweiyun.genshin.core.system.combat.action.data.SkillData;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 星见雅的动作与渲染数据。
 *
 * <h2>渲染数据走来源表</h2>
 * {@link CharacterRenderData#sourced} 表示「模型 / 动画 / 贴图按
 * {@code character/miyabi/resources.json} 解析」，而不是照本 MOD 的 {@code character/} 目录找。
 * 那份文件里模型指向对方模组（IB），本 MOD 里没有她的模型文件。
 *
 * <h2>动作数据</h2>
 * 动画名按素材里的实际名字写，时长与<b>伤害点的段数、延时</b>都照抄素材，只有倍率是本 MOD 自己配的：
 * <ul>
 *   <li><b>普攻五段</b>：{@code attack_1..attack_5}，40 / 48 / 25 / 30 / 50 刻，
 *       伤害点 1 / 1 / 2 / 4 / 4 下（见 {@link #COMBO_HITS}）；</li>
 *   <li><b>战技</b>：取「能量满」的那一套 —— 第一段 {@code skill_energy}（25 刻），
 *       收尾接 {@code skill_energy_continue}（60 刻），伤害点 8 刻起每 2 刻一下共 11 下；</li>
 *   <li><b>重击</b>：取「能量不满」的那一套 —— {@code heavy_1}（15 刻），
 *       伤害点 3 下（素材里那三道斩击），见 {@link #CHARGED_STEP}；</li>
 *   <li><b>大招</b>：{@code final}（素材里 5 秒，100 刻），伤害点 44 刻一下 + 73 刻起每 5 刻一下共 5 下。</li>
 * </ul>
 */
public final class MiyabiResources {

    /** 普攻连段数。 */
    public static final int MAX_COMBO = 5;

    /** 生效攻击距离取框架的近战默认值（3 格）。 */
    public static final float ATTACK_RANGE = ActionStep.DEFAULT_MELEE_RANGE;

    /** 每段普攻的时长（刻），与素材那五段一一对应。 */
    private static final int[] COMBO_TICKS = {40, 48, 25, 30, 50};

    /** 每段普攻的伤害点（刻）—— 段数与延时照抄素材：1 / 1 / 2 / 4 / 4 下。 */
    private static final int[][] COMBO_HITS = {
            {3},
            {3},
            {3, 6},
            {10, 12, 14, 16},
            {2, 4, 6, 8},
    };

    /** 战技第一段（能量满）。 */
    private static final String SKILL_ANIM = "skill_energy";

    /** 深雪用的普通特殊技动画。 */
    private static final String DEEP_SNOW_ANIM = "skill_no_energy";

    /** 霜月用的满蓄重击动画。 */
    private static final String FROST_MOON_ANIM = "heavy_3";

    private static final int DEEP_SNOW_TICKS = 43;
    private static final int[] DEEP_SNOW_HITS = {12};

    private static final int FROST_MOON_TICKS = 58;
    private static final int[] FROST_MOON_HITS = {10};

    /** 战技收尾段（能量满那一套的第二段）。 */
    public static final String SKILL_FOLLOW_UP = "skill_energy_continue";

    /** 战技两段各自的刻数。 */
    private static final int SKILL_TICKS = 25;
    private static final int SKILL_FOLLOW_UP_TICKS = 60;

    /**
     * 战技的伤害点（刻）—— 素材里「能量满 + 非冲刺」那一套：8 刻起每 2 刻一下，共 11 下。
     *
     * <p>最后两下（26 / 28 刻）比第一段动画（25 刻）还晚，所以这一段的状态多留 4 刻把这些伤害点装下
     * —— 动画自己只播 25 刻，多出来的几刻停在最后一帧，之后照常接收尾段 {@link #SKILL_FOLLOW_UP}。
     */
    private static final int[] SKILL_HITS = {8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28};

    /** 战技这一段的实际时长（刻）：够放完 {@link #SKILL_HITS}。 */
    private static final int SKILL_STEP_TICKS = 29;

    /** 大招的伤害点（刻）—— 素材里 44 刻一下，再在 73 刻起每 5 刻一下共 5 下。 */
    private static final int[] BURST_HITS = {44, 73, 78, 83, 88, 93};

    /**
     * 下落攻击用的状态名。
     *
     * <p>素材里没有下劈那一段，用普攻第二段的中间姿态顶 —— 状态名走 {@code plunge}，
     * 由 {@link #PLUNGE_CLIP} 指到那条动画（见 {@code PlayerAnimationController} 的别名解析）。
     */
    public static final String PLUNGE_STATE = "plunge";

    /** {@link #PLUNGE_STATE} 实际播的那条动画。 */
    public static final String PLUNGE_CLIP = "attack_2";

    /** 模型作者。 */
    private static final String MODEL_AUTHOR = "下一只风筝";
    private static final String MODEL_AUTHOR_URL = "https://space.bilibili.com/281665959";

    /** 大招动画名与时长（刻）—— 素材里这条 5 秒。 */
    private static final String BURST_ANIM = "final";
    private static final int BURST_TICKS = 100;

    /** 大招能量消耗 —— 与角色构造器里声明的 {@code maxObtainingEnergy} 保持一致。 */
    private static final float BURST_ENERGY = 60.0F;

    private static final Engagement MELEE = Engagement.melee();

    /** 重击伤害点在第几刻 —— 斩击也在这同一刻生成。 */
    public static final int CHARGED_HIT_TICK = 4;

    /**
     * 重击（能量不满的那一套）：{@code heavy_1}，15 刻、前 9 刻不可打断。
     *
     * <p>素材里重击按「能量」和「同步深度」分好几段（heavy_1 / 2 / 3），这里按约定只取第一段；
     * 这一下不直接结算伤害：第 {@link #CHARGED_HIT_TICK} 刻把三道剑气放出去，
     * 伤害由剑气按碰撞自己打（与素材侧同一套做法）。
     */
    public static final ActionStep CHARGED_STEP = melee(new ActionStep(
            "heavy_1", 15, 9, 2,
            List.of(),
            hits(CHARGED_HIT_TICK),
            List.of(),
            0, 2, 0, 8
    ));

    public static final CharacterRenderData RENDER_DATA = CharacterRenderData.sourced(
            Miyabi.ID,
            animMapping(),
            1.0F
    ).withModelAuthor(MODEL_AUTHOR, MODEL_AUTHOR_URL);

    public static final CharacterActionData ACTION_DATA = build();

    private MiyabiResources() {
    }

    /** 常态动画映射 + 下落攻击那条别名。 */
    private static Map<String, String> animMapping() {
        Map<String, String> mapping = new LinkedHashMap<>(CharacterRenderData.defaultAnimMapping());
        mapping.put(PLUNGE_STATE, PLUNGE_CLIP);
        return Map.copyOf(mapping);
    }

    private static CharacterActionData build() {
        Map<Integer, ActionStep> combo = new LinkedHashMap<>();
        for (int stage = 1; stage <= MAX_COMBO; stage++) {
            int duration = COMBO_TICKS[stage - 1];
            int[] delays = COMBO_HITS[stage - 1];
            combo.put(stage, melee(new ActionStep(
                    "attack_" + stage, duration, protectFor(duration, delays), 2,
                    List.of(),
                    hits(delays),
                    List.of(),
                    0, 0, 0, 8
            )));
        }

        ActionStep skill = deepSnowStep();

        ActionStep burst = melee(new ActionStep(
                BURST_ANIM, BURST_TICKS, protectFor(BURST_TICKS, BURST_HITS), 4,
                List.of(),
                hits(BURST_HITS),
                List.of(),
                0, 0, 0, 0
        ));

        ActionStep dodge = new ActionStep(
                "dodge_front", 12, 0, 3,
                List.of(new Move(0, 1.5)),
                List.of(),
                List.of(),
                0, 0, 0, 0
        );

        return new CharacterActionData(
                new ComboData(MAX_COMBO, combo),
                new SkillData(skill, null),
                new BurstData(burst, BURST_ENERGY),
                new DodgeData(dodge));
    }

    /** 深雪：普通特殊技动画，单段斩击。 */
    public static ActionStep deepSnowStep() {
        return melee(new ActionStep(
                DEEP_SNOW_ANIM, DEEP_SNOW_TICKS, protectFor(DEEP_SNOW_TICKS, DEEP_SNOW_HITS), 3,
                List.of(),
                hits(DEEP_SNOW_HITS),
                List.of(),
                0, 0, 0, 0
        ));
    }

    /** 飞雪：强化特殊技两段，多段伤害。 */
    public static ActionStep flyingSnowStep() {
        return melee(new ActionStep(
                SKILL_ANIM, SKILL_STEP_TICKS, protectFor(SKILL_STEP_TICKS, SKILL_HITS), 3,
                List.of(),
                hits(SKILL_HITS),
                List.of(),
                0, 0, 0, 0
        )).withComboEnd(SKILL_FOLLOW_UP, SKILL_FOLLOW_UP_TICKS);
    }

    /** 霜月：满蓄重击动画。 */
    public static ActionStep frostMoonStep() {
        return melee(new ActionStep(
                FROST_MOON_ANIM, FROST_MOON_TICKS, protectFor(FROST_MOON_TICKS, FROST_MOON_HITS), 2,
                List.of(),
                hits(FROST_MOON_HITS),
                List.of(),
                0, 2, 0, 8
        ));
    }

    private static ActionStep melee(ActionStep step) {
        return step == null ? null : step.withEngagement(MELEE).withAttackRange(ATTACK_RANGE);
    }

    /**
     * 一组伤害点：位置只是表达「打在身前」，结算用的是技能自己的盒子（见 {@code MiyabiDamage}）。
     */
    private static List<Hit> hits(int... delays) {
        List<Hit> list = new ArrayList<>(delays.length);
        for (int delay : delays) {
            list.add(new Hit(delay, 0.0, 1.5, 3.0));
        }
        return List.copyOf(list);
    }

    /**
     * 执行期要盖住最后一个伤害点 —— 否则多段招式的后半段会算进后摇里，走一步就全没了。
     */
    private static int protectFor(int duration, int[] delays) {
        int last = delays.length == 0 ? 0 : delays[delays.length - 1];
        return Math.min(duration, last + 1);
    }
}
