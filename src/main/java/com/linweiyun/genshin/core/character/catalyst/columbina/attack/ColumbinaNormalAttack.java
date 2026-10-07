package com.linweiyun.genshin.core.character.catalyst.columbina.attack;

import com.linweiyun.genshin.content.skill_node.AreaEntityCollector;
import com.linweiyun.genshin.content.skill_node.TargetSeeker;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.elementlib.core.system.about.AttachmentType;
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
 * 哥伦比娅的<b>普通攻击</b>（法器 3 段远程）。
 *
 * <p>索敌 10 格，命中后造成水伤。第 3 段范围扩大。
 */
public final class ColumbinaNormalAttack {

    private static final float[] COMBO_MULTIPLIERS = {0.4f, 0.45f, 0.6f};
    private static final int ATTACK_RANGE = 10;

    private ColumbinaNormalAttack() {
    }

    /**
     * 执行一段普攻。
     */
    public static void execute(Player player, PGCharacter character, int comboStage) {
        Level level = player.level();
        if (level.isClientSide()) return;

        int stage = comboStage - 1;
        float multiplier = stage >= 0 && stage < COMBO_MULTIPLIERS.length
                ? COMBO_MULTIPLIERS[stage] : 1.0f;

        LivingEntity primaryTarget = new TargetSeeker(player, ATTACK_RANGE,
                TargetSeeker.TargetingType.LINE_OF_SIGHT).execute();
        if (primaryTarget == null) return;

        float aoeRange = stage == 2 ? 1.0f : 0.5f;
        Vec3 targetPos = primaryTarget.position();
        List<LivingEntity> targets = new AreaEntityCollector(level,
                targetPos.add(-aoeRange, -aoeRange, -aoeRange),
                targetPos.add(aoeRange, aoeRange, aoeRange),
                aoeRange).execute();

        for (LivingEntity target : targets) {
            if (target == player) continue;

            ModDamageSpec spec = ModDamageSpec.builder(AttackType.NORMAL_ATTACK, ModElements.HYDRO.get())
                    .multiplier(multiplier)
                    .elementAmount(AttachmentType.ULTRA_STRONG.getInitialAmount())
                    .attackerCharacter(character)
                    .build();
            ModDamageSource source = ModDamageSource.from(spec, player);
            if (target.level() instanceof ServerLevel serverLevel) {
                target.hurt(source, 0f);
            }
        }

        // —— 后续效果 ——
    }
}