package com.linweiyun.genshin.core.character.sword.vesna.attack;

import com.linweiyun.genshin.content.entities.teyvat.skill.vesna.VesnaAttackProjectile;
import com.linweiyun.genshin.content.skill_node.AreaEntityCollector;
import com.linweiyun.genshin.content.skill_node.TargetSeeker;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.sword.vesna.Vesna;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.elementlib.api.ElementalReactionType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import com.linweiyun.genshin.core.system.registry.register.ModReactionTypes;

/**
 * 薇斯娜的<b>重击</b>。
 *
 * <p>对索敌目标造成星扩散的风段伤害（基于 {@link ElementalReactionType#STELLAR_SWIRL_WIND}）。
 * 巡风列装模式下额外发射 2 枚风铃弹射物。
 */
public final class VesnaChargedAttack {

    private static final double ATTACK_RANGE = 10.0;
    private static final float AOE_RANGE = 1.5f;

    private VesnaChargedAttack() {
    }

    public static void execute(Player player, PGCharacter character) {
        Level level = player.level();
        if (level.isClientSide()) return;

        LivingEntity primaryTarget = new TargetSeeker(
                player, ATTACK_RANGE, TargetSeeker.TargetingType.LINE_OF_SIGHT).execute();
        if (primaryTarget == null) return;

        Vec3 center = primaryTarget.position();
        List<LivingEntity> targets = new AreaEntityCollector(level,
                center.add(-AOE_RANGE, -AOE_RANGE, -AOE_RANGE),
                center.add(AOE_RANGE, AOE_RANGE, AOE_RANGE),
                AOE_RANGE).execute();

        for (LivingEntity target : targets) {
            if (target == player) continue;

            ModDamageSpec spec = ModDamageSpec.stellarDirect(
                    ModReactionTypes.STELLAR_SWIRL.get(), ModElements.ANEMO.get(), 1.0f, 0.5f);
            spec.setStellarContributors(List.of(character));
            ModDamageSource source = ModDamageSource.from(spec, player);
            if (target.level() instanceof ServerLevel serverLevel) {
                target.hurt(source, 0f);
            }
        }

        // —— 后续效果 ——
        if (!(character instanceof Vesna vesna)) return;
        if (!vesna.isWindriderActive()) return;

        int skillLevel = character.getData().getElementalSkillLevel();
        for (int i = 0; i < 2; i++) {
            VesnaAttackProjectile projectile = VesnaAttackProjectile.create(
                    level, vesna, player.position(), skillLevel);
            if (projectile != null) {
                level.addFreshEntity(projectile);
                vesna.addEnergy(1);
            }
        }
    }
}