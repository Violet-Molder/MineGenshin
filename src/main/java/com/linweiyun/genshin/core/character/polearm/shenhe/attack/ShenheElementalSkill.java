package com.linweiyun.genshin.core.character.polearm.shenhe.attack;

import com.linweiyun.genshin.config.character.ShenheTalentConfig;
import com.linweiyun.genshin.content.skill_node.DashSystem;
import com.linweiyun.genshin.content.skill_node.RushesForward;
import com.linweiyun.genshin.content.skill_node.SkillHelper;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.polearm.shenhe.ShenheTalent;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.List;
import com.linweiyun.genshin.core.system.combat.decay.ModDecayGroups;

/**
 * 申鹤的<b>元素战技</b>（点按 / 长按）。
 *
 * <p>点按：向前冲刺 10 格，命中路径上的敌人并造成冰伤 + 僵直。
 * 长按：自身周围 5×4×5 范围造成冰伤 + 僵直。
 *
 * <p>两种按键都会触发突破天赋：
 * <ul>
 *   <li>突破天赋 1（{@link ShenheTalent#grantIcyQuills}）：冰凌</li>
 *   <li>突破天赋 2（{@link ShenheTalent#grantAscend2DamageBonus}）：增伤</li>
 * </ul>
 *
 * <p>伤害实施 + 天赋触发全部写在这里，不再分散。
 */
public final class ShenheElementalSkill {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);

    private static final float SKILL_DASH_DISTANCE = 10f;
    private static final int SKILL_DASH_TICKS = 10;

    private ShenheElementalSkill() {
    }

    /**
     * 执行元素战技。
     *
     * @param player    攻击者
     * @param character 攻击者角色
     * @param skillTime 按键时长（{@code < 1000} = 点按，{@code >= 1000} = 长按）
     */
    public static void execute(Player player, PGCharacter character, int skillTime) {
        Level level = player.level();
        String side = level.isClientSide() ? "CLIENT" : "SERVER";
        LOGGER.info("[ShenheElementalSkill] [{}] enter skillTime={}", side, skillTime);

        int skillLevel = character.getData().getElementalSkillLevel();

        if (skillTime < 1000) {
            // ========== 点按 ==========
            executeTap(player, character, skillLevel);
        } else {
            // ========== 长按 ==========
            executeHold(player, character, skillLevel);
        }
    }

    private static void executeTap(Player player, PGCharacter character, int skillLevel) {
        Level level = player.level();
        float pressDamage = ShenheTalentConfig.getSkillPressDamage(skillLevel);
        Vec3 delta = new RushesForward(player, SKILL_DASH_DISTANCE).execute();

        if (level.isClientSide()) {
            DashSystem.startDash(player, delta, SKILL_DASH_TICKS);
        } else {
            DashSystem.startDamageDash(player, delta, SKILL_DASH_TICKS, hitEntity -> {
                ModDamageSpec spec = ModDamageSpec.builder(AttackType.ELEMENTAL_SKILL, ModElements.CYRO.get())
                        .multiplier(pressDamage)
                        .elementAmount(AttachmentType.WEAK.getInitialAmount())
                        .decayGroup(ModDecayGroups.SHENHE_SKILL)
                        .attackerCharacter(character)
                        .build();
                ModDamageSource source = ModDamageSource.from(spec, player);
                if (hitEntity.level() instanceof ServerLevel serverLevel) {
                    hitEntity.hurtServer(serverLevel, source, 0f);
                }
            });
        }

        if (level.isClientSide()) return;

        // —— 后续效果 ——
        // 突破天赋 1：冰凌（5 根 / 10 秒）
        // 突破天赋 2：队伍 E/Q 增伤（10 秒）
        if (character.getTalent() instanceof ShenheTalent passive) {
            passive.grantIcyQuills(player, character, 5, 200);
            passive.grantAscend2DamageBonus(player, character, false);
        }

        new SkillHelper(player, 10).addStun();
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
                target.hurtServer(serverLevel, source, 0f);
            }
        }

        // —— 后续效果 ——
        // 突破天赋 1：冰凌（7 根 / 15 秒）
        // 突破天赋 2：普攻/重击/下落增伤（15 秒）
        if (character.getTalent() instanceof ShenheTalent passive) {
            passive.grantIcyQuills(player, character, 7, 300);
            passive.grantAscend2DamageBonus(player, character, true);
        }

        new SkillHelper(player, 10).addStun();
    }
}