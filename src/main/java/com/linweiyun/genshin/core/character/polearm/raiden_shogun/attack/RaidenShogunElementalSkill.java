package com.linweiyun.genshin.core.character.polearm.raiden_shogun.attack;

import com.linweiyun.genshin.config.character.ShenheTalentConfig;
import com.linweiyun.genshin.content.skill_node.AreaEntityCollector;
import com.linweiyun.genshin.content.skill_node.RushesForward;
import com.linweiyun.genshin.content.skill_node.SkillHelper;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.polearm.raiden_shogun.RaidenShogunTalent;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import com.linweiyun.elementlib.core.system.about.AttachmentType;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.elementlib.core.system.combat.decay.DecayGroups;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.List;
import com.linweiyun.genshin.core.system.combat.decay.ModDecayGroups;

/**
 * 雷电将军的<b>元素战技</b>（点按 / 长按）。
 *
 * <p>点按：向前冲刺 10 格，伤害路径上的敌人。
 * 长按：对周围 5×4×5 范围造成伤害。
 *
 * <p>两种按键都会触发突破天赋（冰凌 + 增伤）。
 *
 * <p>伤害实施 + 天赋触发全部写在这里。
 */
public final class RaidenShogunElementalSkill {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);

    private RaidenShogunElementalSkill() {
    }

    /**
     * 执行元素战技。
     *
     * @param player    攻击者
     * @param character 攻击者角色
     * @param skillTime 按键时长（{@code < 1000} = 点按，{@code >= 1000} = 长按）
     */
    public static void execute(Player player, PGCharacter character, int skillTime) {
        int skillLevel = character.getData().getElementalSkillLevel();

        if (skillTime < 1000) {
            executeTap(player, character, skillLevel);
        } else {
            executeHold(player, character, skillLevel);
        }
    }

    private static void executeTap(Player player, PGCharacter character, int skillLevel) {
        Level level = player.level();

        // 伤害：向前冲刺并伤害路径上的敌人
        Vec3 startPos = player.position();
        Vec3 dashTotal = new RushesForward(player, 10).execute();
        Vec3 rawEndPos = startPos.add(dashTotal);
        HitResult hit = level.clip(new ClipContext(startPos, rawEndPos,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 endPos = hit.getLocation();

        float pressDamage = ShenheTalentConfig.getSkillPressDamage(skillLevel);
        List<LivingEntity> targets = new AreaEntityCollector(level, startPos, endPos, 0.5f).execute();
        for (LivingEntity target : targets) {
            if (target == player) continue;

            ModDamageSpec spec = ModDamageSpec.builder(AttackType.ELEMENTAL_SKILL, ModElements.ELECTRO.get())
                    .multiplier(pressDamage)
                    .elementAmount(AttachmentType.WEAK.getInitialAmount())
                    .decayGroup(ModDecayGroups.SHENHE_SKILL)
                    .attackerCharacter(character)
                    .build();
            ModDamageSource source = ModDamageSource.from(spec, player);
            if (target.level() instanceof ServerLevel serverLevel) {
                target.hurt(source, 0f);
            }
        }

        if (level.isClientSide()) return;

        // —— 后续效果 ——
        // 突破天赋触发
        if (character.getTalent() instanceof RaidenShogunTalent passive) {
            passive.grantIcyQuills(player, character, 5, 200);
            passive.grantAscend2DamageBonus(player, character, false);
        }
        new SkillHelper(player, 10).addStun();
        if (character instanceof com.linweiyun.genshin.core.character.polearm.raiden_shogun.RaidenShogun raiden) {
            raiden.beginCoordinated();
        }
    }

    private static void executeHold(Player player, PGCharacter character, int skillLevel) {
        Level level = player.level();
        if (level.isClientSide()) return;

        float holdDamage = ShenheTalentConfig.getSkillHoldDamage(skillLevel);
        AABB holdBox = new AABB(
                player.getX() - 2.5, player.getY() - 2, player.getZ() - 2.5,
                player.getX() + 2.5, player.getY() + 2, player.getZ() + 2.5);
        List<LivingEntity> holdTargets = level.getEntitiesOfClass(LivingEntity.class, holdBox,
                e -> e != player && !(e instanceof Player));
        for (LivingEntity target : holdTargets) {
            ModDamageSpec spec = ModDamageSpec.builder(AttackType.ELEMENTAL_SKILL, ModElements.CYRO.get())
                    .multiplier(holdDamage)
                    .elementAmount(AttachmentType.WEAK.getInitialAmount())
                    .decayGroup(ModDecayGroups.SHENHE_SKILL)
                    .attackerCharacter(character)
                    .build();
            ModDamageSource source = ModDamageSource.from(spec, player);
            if (target.level() instanceof ServerLevel serverLevel) {
                target.hurt(source, 0f);
            }
        }

        // 突破天赋触发
        if (character.getTalent() instanceof RaidenShogunTalent passive) {
            passive.grantIcyQuills(player, character, 7, 300);
            passive.grantAscend2DamageBonus(player, character, true);
        }
        new SkillHelper(player, 10).addStun();
        if (character instanceof com.linweiyun.genshin.core.character.polearm.raiden_shogun.RaidenShogun raiden) {
            raiden.beginCoordinated();
        }
    }
}