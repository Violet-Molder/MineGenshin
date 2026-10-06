package com.linweiyun.genshin.core.character.sword.vesna.attack;

import com.linweiyun.genshin.config.character.ShenheTalentConfig;
import com.linweiyun.genshin.content.entities.teyvat.skill.vesna.VesnaAttackProjectile;
import com.linweiyun.genshin.content.skill_node.AreaEntityCollector;
import com.linweiyun.genshin.content.skill_node.ElementalOrbSpawner;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.sword.vesna.Vesna;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.elementlib.core.system.about.AttachmentType;
import com.linweiyun.genshin.core.system.combat.CombatAim;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 薇斯娜的<b>普通攻击</b>（6 段近战连击，风元素）。
 *
 * <p>命中后触发：
 * <ul>
 *   <li>风元素微粒（每段 50% 概率）；</li>
 *   <li>巡风列装模式下发射风铃弹射物（按段数 1~3 枚）。</li>
 * </ul>
 *
 * <p>第 6 段会双倍结算一次。
 */
public final class VesnaNormalAttack {

    private static final float ATTACK_REACH = 2.5f;
    private static final float ATTACK_INFLATE = 1.0f;
    private static final float NORMAL_ATTACK_PARTICLE_CHANCE = 0.5f;

    private VesnaNormalAttack() {
    }

    public static void execute(Player player, PGCharacter character, int comboStage) {
        Level level = player.level();

        int stage = Math.max(1, comboStage);
        int naLevel = Math.max(1, character.getData().getNormalAttackLevel());
        float multiplier = (float) (
                ShenheTalentConfig.getNABase(stage)
                        + ShenheTalentConfig.getNAPerLevel(stage) * (naLevel - 1));

        Vec3 startPos = player.position();
        Vec3 lookDir = CombatAim.direction(player);
        Vec3 endPos = startPos.add(lookDir.scale(ATTACK_REACH));
        List<LivingEntity> targets = new AreaEntityCollector(level, startPos, endPos, ATTACK_INFLATE).execute();

        for (LivingEntity target : targets) {
            if (target != player && target.isAlive()) {
                ModDamageSpec spec = ModDamageSpec.builder(AttackType.NORMAL_ATTACK, ModElements.ANEMO.get())
                        .multiplier(multiplier)
                        .elementAmount(AttachmentType.ULTRA_STRONG.getInitialAmount())
                        .attackerCharacter(character)
                        .build();
                ModDamageSource source = ModDamageSource.from(spec, player);
                if (target.level() instanceof ServerLevel serverLevel) {
                    target.hurtServer(serverLevel, source, 0f);
                    // 第 6 段双倍结算
                    if (stage == 6 && target.isAlive()) {
                        target.hurtServer(serverLevel, source, 0f);
                    }
                }
            }
        }

        // —— 后续效果 ——
        if (level.isClientSide() || !(character instanceof Vesna vesna)) return;

        // 风元素微粒
        if (level.getRandom().nextFloat() < NORMAL_ATTACK_PARTICLE_CHANCE) {
            new ElementalOrbSpawner(level, ModElements.ANEMO.get(), 1, true, player.position()).execute();
        }

        // 巡风列装模式：发射风铃
        if (!vesna.isWindriderActive()) return;

        int bellCount = getBellCountForStage(stage);
        int skillLevel = character.getData().getElementalSkillLevel();
        for (int i = 0; i < bellCount; i++) {
            VesnaAttackProjectile projectile = VesnaAttackProjectile.create(
                    level, vesna, player.position(), skillLevel);
            if (projectile != null) {
                level.addFreshEntity(projectile);
                vesna.addEnergy(1);
            }
        }
    }

    private static int getBellCountForStage(int stage) {
        return switch (stage) {
            case 1, 2, 4, 5 -> 1;
            case 3 -> 2;
            case 6 -> 3;
            default -> 0;
        };
    }
}