package com.linweiyun.genshin.core.character.claymore.sandrone;

import com.linweiyun.genshin.core.system.combat.action.data.CharacterActionData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderData;
import com.linweiyun.genshin.core.system.combat.action.data.ComboData;
import com.linweiyun.genshin.core.system.combat.action.data.Engagement;
import com.linweiyun.genshin.core.system.combat.action.data.ActionStep;
import com.linweiyun.genshin.core.system.combat.action.data.Hit;
import com.linweiyun.genshin.core.system.combat.action.data.Move;
import com.linweiyun.genshin.core.system.combat.action.data.SkillData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TestResources {

    /** 骨骼替换 / Photon 锚点用的长柄骨骼名。 */

    public static final CharacterRenderData RENDER_DATA = CharacterRenderData.character(
            SandroneCharacter.ID,
            CharacterRenderData.defaultAnimMapping(),
            1.0F
    );

    /** 普攻两段的动作数据 —— 照抄林薇云的长柄形态。 */
    public static final CharacterActionData POLEARM_ACTION_DATA = buildPolearm();

    private static CharacterActionData buildPolearm() {
        Map<Integer, ActionStep> comboSteps = new LinkedHashMap<>();
        comboSteps.put(1, new ActionStep(
                "shenhe_attack_1",
                40,
                16,
                30,
                List.of(new Move(10, 2)),
                List.of(new Hit(12, 0.5, 0.5, 2)),
                List.of(),
                0, 0, 0, 8
        ).withEngagement(Engagement.melee().withApproachStep(false)));
        comboSteps.put(2, new ActionStep(
                "shenhe_attack_2",
                40,
                20,
                30,
                List.of(new Move(10, 2)),
                List.of(new Hit(15, 0.5, 0.5, 2)),
                List.of(),
                0, 0, 0, 8
        ).withEngagement(Engagement.melee().withApproachStep(false)));

        ActionStep skillTap = new ActionStep(
                "skill", 25, 14, 3,
                List.of(),
                List.of(new Hit(8, 0.0, 1.5, 3.0)),
                List.of(), 0f, 0f, 0, 0
        ).withEngagement(Engagement.melee().withDash(false).withAdhesion(0.0, 0.0));

        return new CharacterActionData(
                new ComboData(2, comboSteps),
                new SkillData(skillTap, null),
                null, null
        );
    }

    private TestResources() {
    }
}