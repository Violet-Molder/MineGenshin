package com.linweiyun.genshin.core.character.polearm.arlecchino;

import com.linweiyun.genshin.content.skill_node.AreaEntityCollector;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.talent.SkillBase;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.action.ActionSet;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.genshin.core.system.about.AttachmentType;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import com.linweiyun.genshin.core.system.combat.CombatAim;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 阿蕾奇诺的<b>技能</b>（重命名前叫 {@code ArlecchinoTalent}）——
 * 普攻的动作数据与伤害结算。
 *
 * <p>她目前没有突破天赋与命座实现，所以 {@link ArlecchinoTalent} /
 * {@link ArlecchinoConstellation} 是空壳。
 */
public class ArlecchinoSkill extends SkillBase {

    /** 连招倍率，<b>按段从 0 基排列</b>（下标 0 = 第 1 段）。 */
    private static final float[] COMBO_MULTIPLIERS = {0.41f, 0.42f, 0.55f, 0.35f, 0.68f};

    /** 近战判定框：面前 2.5 格、半径 1.0（长柄，和申鹤同一套取法）。 */
    private static final float ATTACK_REACH = 2.5f;
    private static final float ATTACK_INFLATE = 1.0f;

    // ==================== 参数覆盖 ====================

    @Override
    public int getMaxCombo() { return 5; }

    @Override
    protected ActionSet buildDefaultActionSet(PGCharacter character) {
        return super.buildDefaultActionSet(character);
    }

    // ==================== 普攻 ====================

    @Override
    public void attack(Player player, PGCharacter character, int comboStage) {
        Level level = player.level();
        if (level.isClientSide()) return;

        // ⚠️ {@code COMBO_MULTIPLIERS} 按段从 0 基排列，所以要用 {@code comboStage - 1}。
        //    原来那句 {@code comboStage % getMaxCombo()} 得到的是 1,2,3,4,0 —— 整条连招的倍率绕了一圈：
        //    第 1 段吃第 2 段的倍率、第 5 段绕回第 1 段。
        int stage = comboStage - 1;
        float multiplier = stage >= 0 && stage < COMBO_MULTIPLIERS.length ? COMBO_MULTIPLIERS[stage] : 1.0f;

        // ⚠️ 原来这一下是 {@code player.hurtServer(...)} —— 打的是<b>自己</b>，
        //    敌人在旁边一点伤害都吃不到、玩家反倒每刀掉血。
        //    长柄近战按申鹤同款判定框取目标：沿视线面前 2.5 格、半径 1.0
        //    （和她自己的 {@code ArlecchinoResources.ATTACK_RANGE}（框架近战默认 3.0）同一量级）。
        Vec3 startPos = player.position();
        Vec3 endPos = startPos.add(CombatAim.direction(player).scale(ATTACK_REACH));
        List<LivingEntity> targets = new AreaEntityCollector(level, startPos, endPos, ATTACK_INFLATE).execute();

        for (LivingEntity target : targets) {
            if (target == player) continue;

            ModDamageSpec spec = ModDamageSpec.builder(AttackType.NORMAL_ATTACK, ModElements.PYRO.get())
                    .multiplier(multiplier)
                    .elementAmount(AttachmentType.WEAK.getInitialAmount())
                    .attackerCharacter(character)
                    .build();
            ModDamageSource source = ModDamageSource.from(spec, player);
            if (target.level() instanceof ServerLevel serverLevel) {
                target.hurtServer(serverLevel, source, 0f);
            }
        }
    }
}
