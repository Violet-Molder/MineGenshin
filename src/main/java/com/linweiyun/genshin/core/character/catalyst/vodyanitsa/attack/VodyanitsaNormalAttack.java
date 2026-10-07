package com.linweiyun.genshin.core.character.catalyst.vodyanitsa.attack;

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
 * 沃雅妮莎的<b>普通攻击</b>（法器 3 段远程水伤）。
 *
 * <p>第 3 段溅射范围翻倍。
 */
public final class VodyanitsaNormalAttack {

    private static final float[] COMBO_MULTIPLIERS = {0.5f, 0.55f, 0.7f};
    private static final int ATTACK_RANGE = 10;

    private VodyanitsaNormalAttack() {
    }

    public static void execute(Player player, PGCharacter character, int comboStage) {
        Level level = player.level();
        if (level.isClientSide()) return;

        int stage = comboStage - 1;
        float multiplier = stage >= 0 && stage < COMBO_MULTIPLIERS.length
                ? COMBO_MULTIPLIERS[stage]
                : 1.0f;

        LivingEntity target = new TargetSeeker(player, ATTACK_RANGE,
                TargetSeeker.TargetingType.LINE_OF_SIGHT).execute();
        if (target == null) return;

        float range = stage == 2 ? 2.0f : 1.0f;
        Vec3 center = target.position();
        List<LivingEntity> targets = new AreaEntityCollector(level,
                center.add(-range, -range, -range),
                center.add(range, range, range),
                range).execute();

        for (LivingEntity hit : targets) {
            if (hit == player) continue;

            ModDamageSpec spec = ModDamageSpec.builder(AttackType.NORMAL_ATTACK, ModElements.HYDRO.get())
                    .multiplier(multiplier)
                    .elementAmount(AttachmentType.WEAK.getInitialAmount())
                    .attackerCharacter(character)
                    .build();
            ModDamageSource source = ModDamageSource.from(spec, player);
            if (hit.level() instanceof ServerLevel serverLevel) {
                hit.hurt(source, 0f);
            }
        }
    }
}