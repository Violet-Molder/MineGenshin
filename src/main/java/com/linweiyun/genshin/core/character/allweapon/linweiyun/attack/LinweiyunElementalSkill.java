package com.linweiyun.genshin.core.character.allweapon.linweiyun.attack;

import com.linweiyun.genshin.config.character.LinweiyunTalentConfig;
import com.linweiyun.genshin.content.skill_node.DashSystem;
import com.linweiyun.genshin.content.skill_node.RushesForward;
import com.linweiyun.genshin.content.skill_node.SkillHelper;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.about.AttachmentType;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.genshin.core.system.combat.decay.DecayGroups;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.List;

/**
 * 林薇云（<b>所有形态</b>）的元素战技。
 *
 * <p>{@code skillType < 1000} = 点按：前冲 10 格，路径上敌人吃一次伤害 + 10 刻僵直。<br>
 * {@code skillType >= 1000} = 长按：自身周围 5×4×5 范围内敌人吃一发更高伤害 + 僵直。
 */
public final class LinweiyunElementalSkill {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);

    private static final float SKILL_DASH_DISTANCE = 10f;
    private static final int SKILL_DASH_TICKS = 10;

    private LinweiyunElementalSkill() {
    }

    public static void execute(Player player, PGCharacter character, int skillType) {
        Level level = player.level();
        String side = level.isClientSide() ? "CLIENT" : "SERVER";
        LOGGER.info("[LinweiyunElementalSkill] [{}] enter skillType={}", side, skillType);

        int skillLevel = character.getData().getElementalSkillLevel();

        if (skillType < 1000) {
            // —— 点按 ——
            float pressDamage = LinweiyunTalentConfig.getSkillPressDamage(skillLevel);
            Vec3 delta = new RushesForward(player, SKILL_DASH_DISTANCE).execute();

            if (level.isClientSide()) {
                DashSystem.startDash(player, delta, SKILL_DASH_TICKS);
            } else {
                DashSystem.startDamageDash(player, delta, SKILL_DASH_TICKS, hitEntity -> {
                    ModDamageSpec spec = ModDamageSpec.builder(
                                    AttackType.ELEMENTAL_SKILL, ModElements.ANEMO.get())
                            .multiplier(pressDamage)
                            .elementAmount(AttachmentType.WEAK.getInitialAmount())
                            .decayGroup(DecayGroups.SHENHE_SKILL)
                            .attackerCharacter(character)
                            .build();
                    ModDamageSource source = ModDamageSource.from(spec, player);
                    if (hitEntity.level() instanceof ServerLevel serverLevel) {
                        hitEntity.hurtServer(serverLevel, source, 0f);
                    }
                });
            }

            if (level.isClientSide()) return;
            new SkillHelper(player, 10).addStun();

        } else {
            // —— 长按 ——
            if (level.isClientSide()) return;

            float holdDamage = LinweiyunTalentConfig.getSkillHoldDamage(skillLevel);
            AABB holdBox = new AABB(
                    player.getX() - 2.5, player.getY() - 2, player.getZ() - 2.5,
                    player.getX() + 2.5, player.getY() + 2, player.getZ() + 2.5);
            List<LivingEntity> holdTargets = level.getEntitiesOfClass(LivingEntity.class, holdBox,
                    e -> e != player && !(e instanceof Player));
            for (LivingEntity target : holdTargets) {
                ModDamageSpec spec = ModDamageSpec.builder(AttackType.ELEMENTAL_SKILL, ModElements.ANEMO.get())
                        .multiplier(holdDamage)
                        .elementAmount(AttachmentType.WEAK.getInitialAmount())
                        .decayGroup(DecayGroups.SHENHE_SKILL)
                        .attackerCharacter(character)
                        .build();
                ModDamageSource source = ModDamageSource.from(spec, player);
                if (target.level() instanceof ServerLevel serverLevel) {
                    target.hurtServer(serverLevel, source, 0f);
                }
            }
            new SkillHelper(player, 10).addStun();
        }
    }
}