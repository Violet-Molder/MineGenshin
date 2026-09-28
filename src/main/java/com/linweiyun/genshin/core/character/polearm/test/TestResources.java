package com.linweiyun.genshin.core.character.polearm.test;

import com.linweiyun.genshin.core.asset.GenshinAssets;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterActionData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterBoneMount;
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

/**
 * test 的资源常量 —— <b>模型 / 贴图 / 动画全部复用林薇云那套</b>，动作数据也照抄。
 *
 * <h2>为什么要显式写路径，而不是用 {@code CharacterRenderData.character(id, ...)}</h2>
 * {@code character(id, ...)} 会按 id 拼出 {@code character/<id>/<id>.geo.json} 之类的路径，
 * 那要求 {@code assets/minegenshin/character/test/} 真的存在。test 现在没有自己的素材，
 * 所以这里用<b>显式路径</b>的构造函数指向林薇云的三个文件：
 * <pre>
 * character/linweiyun/linweiyun.geo.json
 * character/linweiyun/textures/linweiyun.png
 * character/linweiyun/linweiyun.animation.json
 * </pre>
 *
 * <p>注意 {@code id} 仍然是 {@code "test"} —— 它决定的是「查表键」与
 * {@link com.linweiyun.genshin.client.render.character.BoneMountGeoLayer} 的挂点来源，
 * 与文件路径无关。
 *
 * <h2>骨骼挂点</h2>
 * {@code long} 是林薇云模型里<b>长柄武器</b>的挂点骨骼（她那份资源里也是这个名字）。
 * 我们把武器槽的物品画在它上面，于是：
 * <ul>
 *   <li>角色手上真的会握着装备的长柄；</li>
 *   <li>Photon 的武器特效可以直接用骨骼 {@code long} 的世界位姿当锚点（见
 *       {@code WeaponAnchorGeoLayer}），不需要自己算手部偏移。</li>
 * </ul>
 * 槽位空着时这根骨骼保持原样渲染（模型自带的那把），不会凭空少一块。
 */
public final class TestResources {

    /** 骨骼替换 / Photon 锚点用的长柄骨骼名。 */
    public static final String POLEARM_BONE = "long";

    public static final CharacterRenderData RENDER_DATA = new CharacterRenderData(
            TestCharacter.ID,
            GenshinAssets.defaultModelPath(),
            GenshinAssets.defaultTexturePath(),
            GenshinAssets.defaultAnimationPath(),
            CharacterRenderData.defaultAnimMapping(),
            1.0F,
            CharacterBoneMount.of(POLEARM_BONE)
    ).withModelAuthor("下一只风筝", "https://space.bilibili.com/281665959");

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