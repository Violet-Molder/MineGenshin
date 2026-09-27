package com.linweiyun.genshin.core.character.catalyst.vodyanitsa;

import com.linweiyun.genshin.core.system.combat.action.data.CharacterActionData;
import com.linweiyun.genshin.core.system.combat.action.data.ActionStep;
import com.linweiyun.genshin.core.system.combat.action.data.BurstData;
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
 * 沃雅妮莎的动作数据表。
 *
 * <h2>两条硬要求</h2>
 * <ol>
 *   <li><b>不产生通用位移</b>：招式里 {@code moves} 全空，
 *       并且 {@code engagement} 关掉框架突进与「出手瞬间吸附一小步」
 *       （{@code withDash(false).withAdhesion(0, 0)}）—— 她只需要转向。</li>
 *   <li><b>索敌 13 格</b>（普通攻击那一档；战技/大招也一样，避免贴脸才出手）——
 *       注意<b>索敌半径</b>（{@code Engagement.withAcquireRange}）和
 *       <b>生效攻击距离</b>（{@link ActionStep#withAttackRange}）是两件事，
 *       两个都要设，只设前者她仍会贴脸出手。</li>
 * </ol>
 *
 * <p>动画名先按通用命名写（{@code attack_1..4} / {@code charge} / {@code skill} / {@code burst}），
 * 角色没有对应动画时框架只会 warn、不切动画，伤害照常结算。
 */
public final class VodyanitsaResources {

    private VodyanitsaResources() {
    }

    /**
     * 生效攻击距离 <b>13 格</b> —— 普攻四段 / 重击 / 战技 / 大招都一样。
     *
     * <p>⚠️ 光设 {@code Engagement.withAcquireRange(13)} 只能让她<b>在 13 格内选得到目标</b>，
     * 框架「够得着就停下出手」用的是 {@link ActionStep#effectiveAttackRange()} ——
     * 不显式 {@code withAttackRange} 的话它就是 {@code DEFAULT_MELEE_RANGE}（近战 3 格），
     * 于是她还是会贴到脸上才打（法器角色的 13 格射程就白设了）。
     */
    public static final float ATTACK_RANGE = 13.0f;

    /** 索敌 13 格；不突进、不吸附（只转向）。 */
    private static final Engagement NO_GENERIC_MOVE = Engagement.melee()
            .withDash(false)
            .withAdhesion(0, 0)
            .withAcquireRange(13.0)
            .withKeepRange(15.0);

    public static final CharacterActionData ACTION_DATA = build();

    private static CharacterActionData build() {
        Map<Integer, ActionStep> combo = new LinkedHashMap<>();

        combo.put(1, step("attack_1", 20, 4, 2, 1.5, 0, 2));
        combo.put(2, step("attack_2", 22, 4, 2, 1.5, 0, 2));
        combo.put(3, step("attack_3", 24, 5, 2, 2.0, 0, 2));
        combo.put(4, step("attack_4", 30, 6, 2, 2.5, 0, 2));

        // 重击：向前掷出水球（伤害由天赋结算，这里只给时长/伤害点）
        ActionStep charge = new ActionStep(
                "charge", 30, 10, 3,
                List.of(),
                List.of(new Hit(10, 3.0, 1.5, 3.5)),
                List.of(),
                0, 2, 0, 0
        ).withEngagement(NO_GENERIC_MOVE).withAttackRange(ATTACK_RANGE);

        // 战技：一次范围水伤 + 获得「遥久之歌」；冷却 16 秒
        ActionStep skill = new ActionStep(
                "skill", 30, 14, 3,
                List.of(),
                List.of(new Hit(10, 4.0, 1.0, 4.0)),
                List.of(),
                0, 0, Vodyanitsa.SKILL_COOLDOWN_TICKS, 0
        ).withEngagement(NO_GENERIC_MOVE).withAttackRange(ATTACK_RANGE);

        // 大招：一段范围水伤；整段都是执行期（大招那种绝对霸体），冷却 15 秒
        ActionStep burst = new ActionStep(
                "burst", 40, 40, 4,
                List.of(),
                List.of(new Hit(12, 6.0, 1.0, 6.0)),
                List.of(),
                0, 0, Vodyanitsa.BURST_COOLDOWN_TICKS, 0
        ).withEngagement(NO_GENERIC_MOVE).withAttackRange(ATTACK_RANGE);

        // 闪避：唯一允许位移的一段（这是闪避本身）
        ActionStep dodge = new ActionStep(
                "dodge_front", 12, 0, 3,
                List.of(new Move(0, 2.0)),
                List.of(),
                List.of(),
                0, 0, 0, 0
        );

        return new CharacterActionData(
                new ComboData(4, combo),
                new SkillData(skill, null),
                new BurstData(burst, Vodyanitsa.BURST_ENERGY_COST),
                new DodgeData(dodge));
    }

    /** 普攻一段：不带任何通用位移，只有伤害点。 */
    private static ActionStep step(String animation, int duration, int hitDelay, int priority,
                                   double scope, int soundCharge, int comboWindow) {
        return new ActionStep(
                animation, duration, hitDelay + 2, priority,
                List.of(),
                List.of(new Hit(hitDelay, 1.5, 1.5, scope)),
                List.of(),
                0, soundCharge, 0, comboWindow
        ).withEngagement(NO_GENERIC_MOVE).withAttackRange(ATTACK_RANGE);
    }
}
