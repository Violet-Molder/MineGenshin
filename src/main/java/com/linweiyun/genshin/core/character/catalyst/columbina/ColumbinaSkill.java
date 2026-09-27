package com.linweiyun.genshin.core.character.catalyst.columbina;

import com.linweiyun.genshin.content.skill_node.AreaEntityCollector;
import com.linweiyun.genshin.content.skill_node.TargetSeeker;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.talent.SkillBase;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.action.ActionSet;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.genshin.core.system.about.AttachmentType;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.List;

/**
 * 哥伦比娅的<b>技能</b>（重命名前叫 {@code ColumbinaTalent}）——
 * 普攻 / 重击的动作数据与伤害结算（法器，10 格索敌）。
 *
 * <p>搬运时只改了类名与父类（{@link SkillBase}），其余逐行照抄。
 * 她目前没有突破天赋与命座实现，所以 {@link ColumbinaTalent} /
 * {@link ColumbinaConstellation} 是空壳。
 */
public class ColumbinaSkill extends SkillBase {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);
    private static final float[] COMBO_MULTIPLIERS = {0.4f, 0.45f, 0.6f};
    private static final float CHARGED_HP_RATIO = 0.03f;

    // ==================== 参数覆盖 ====================

    @Override
    public int getMaxCombo() { return 3; }

    // ──── 动作集 ────
    // 时序来自 CharacterActionData，当前角色尚未定义数据驱动配置，暂时沿用父类默认（空集）。
    // 战斗回调（attack / chargeAttack / elementalSkill / elementalBurst）保持不变，独立于动作系统存在。

    @Override
    protected ActionSet buildDefaultActionSet(PGCharacter character) {
        return super.buildDefaultActionSet(character);
    }

    @Override
    public void attack(Player player, PGCharacter character, int comboStage) {
        Level level = player.level();
        if (level.isClientSide()) return;

        // ⚠️ {@code COMBO_MULTIPLIERS} 按段从 0 基排列，所以要用 {@code comboStage - 1}。
        //    原来那句 {@code comboStage % getMaxCombo()} 得到的是 1,2,0 —— 整条连招的倍率绕了一圈：
        //    第 1 段吃第 2 段的倍率、第 3 段绕回第 1 段。
        int stage = comboStage - 1;
        float multiplier = stage >= 0 && stage < COMBO_MULTIPLIERS.length ? COMBO_MULTIPLIERS[stage] : 1.0f;

        LivingEntity primaryTarget = new TargetSeeker(player, 10.0, TargetSeeker.TargetingType.LINE_OF_SIGHT).execute();

        if (primaryTarget == null) {
            return;
        }


        // 范围加成跟着同一个 0 基 stage：{@code stage == 2} = <b>第 3 段</b>（也就是本连招最后一段）。
        // 想让第 2 段吃大范围就把 2 改成 1。
        float aoeRange = stage == 2 ? 1.0f : 0.5f;
        Vec3 targetPos = primaryTarget.position();
        List<LivingEntity> targets = new AreaEntityCollector(level,
                targetPos.add(-aoeRange, -aoeRange, -aoeRange),
                targetPos.add(aoeRange, aoeRange, aoeRange),
                aoeRange).execute();

        for (LivingEntity target : targets) {
            if (target != player) {
                ModDamageSpec spec = ModDamageSpec.builder(AttackType.NORMAL_ATTACK, ModElements.HYDRO.get())
                        .multiplier(multiplier)
                        .elementAmount(AttachmentType.ULTRA_STRONG.getInitialAmount())
                        .attackerCharacter(character)
                        .build();
                ModDamageSource source = ModDamageSource.from(spec, player);
                if (target.level() instanceof ServerLevel serverLevel) {
                    target.hurtServer(serverLevel, source, 0f);
                }
            }
        }
    }

    @Override
    public void chargeAttack(Player player, PGCharacter character) {
        Level level = player.level();
        if (level.isClientSide()) return;

        LivingEntity primaryTarget = new TargetSeeker(player, 10.0, TargetSeeker.TargetingType.LINE_OF_SIGHT).execute();
        if (primaryTarget == null) {
            return;
        }

        float aoeRange = 1.5f;
        Vec3 center = primaryTarget.position();
        List<LivingEntity> targets = new AreaEntityCollector(level,
                center.add(-aoeRange, -aoeRange, -aoeRange),
                center.add(aoeRange, aoeRange, aoeRange),
                aoeRange).execute();


        for (LivingEntity target : targets) {
            if (target != player) {
                ModDamageSpec spec = ModDamageSpec.lunarDirectHp(CHARGED_HP_RATIO);
                ModDamageSource source = ModDamageSource.from(spec, player);
                if (target.level() instanceof ServerLevel serverLevel) {
                    target.hurtServer(serverLevel, source, 0f);
                }
            }
        }
    }
}
