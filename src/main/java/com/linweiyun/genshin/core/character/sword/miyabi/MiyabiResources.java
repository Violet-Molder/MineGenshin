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
 * 时序照抄通用兜底表（{@link CharacterActionData#fallback(int)} 的刻数），
 * <b>动画名按对方动画文件里的实际名字写</b>：
 * 普攻 {@code attack_1..5} 两边同名，战技是 {@code skill_no_energy}，大招是 {@code final}
 * （兜底表里那两条叫 {@code skill} / {@code burst}，对方的文件里没有这两个名字 ——
 * 名字对不上时动画会被可用性检查拦掉，只剩伤害，所以这里必须写对方的真名）。
 */
public final class MiyabiResources {

    /** 连段数。 */
    public static final int MAX_COMBO = 5;

    /** 生效攻击距离取框架的近战默认值（3 格）。 */
    public static final float ATTACK_RANGE = ActionStep.DEFAULT_MELEE_RANGE;

    /** 战技动画名（对方动画文件里的名字）。 */
    private static final String SKILL_ANIM = "skill_no_energy";

    /** 大招动画名（对方动画文件里的名字）。 */
    private static final String BURST_ANIM = "final";

    /** 大招能量消耗 —— 与角色构造器里声明的 {@code maxObtainingEnergy} 保持一致。 */
    private static final float BURST_ENERGY = 60.0F;

    private static final Engagement MELEE = Engagement.melee();

    public static final CharacterRenderData RENDER_DATA = CharacterRenderData.sourced(
            Miyabi.ID,
            CharacterRenderData.defaultAnimMapping(),
            1.0F
    );

    public static final CharacterActionData ACTION_DATA = build();

    private MiyabiResources() {
    }

    private static CharacterActionData build() {
        Map<Integer, ActionStep> combo = new LinkedHashMap<>();
        for (int stage = 1; stage <= MAX_COMBO; stage++) {
            combo.put(stage, melee(new ActionStep(
                    "attack_" + stage, 20, 0, 2,
                    List.of(),
                    List.of(new Hit(4, 0.0, 1.5, 3.0)),
                    List.of(),
                    0, 0, 0, 8
            )));
        }

        ActionStep skill = melee(new ActionStep(
                SKILL_ANIM, 25, 6, 3,
                List.of(),
                List.of(new Hit(8, 0.0, 1.5, 3.0)),
                List.of(),
                0, 0, 0, 0
        ));

        ActionStep burst = melee(new ActionStep(
                BURST_ANIM, 40, 40, 4,
                List.of(),
                List.of(new Hit(10, 0.0, 1.5, 4.0)),
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

    private static ActionStep melee(ActionStep step) {
        return step == null ? null : step.withEngagement(MELEE).withAttackRange(ATTACK_RANGE);
    }
}
