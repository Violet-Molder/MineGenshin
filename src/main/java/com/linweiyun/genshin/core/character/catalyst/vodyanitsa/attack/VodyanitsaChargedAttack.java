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
 * 沃雅妮莎的<b>重击</b>（远程水伤，大范围）。
 */
public final class VodyanitsaChargedAttack {

    private static final int ATTACK_RANGE = 12;
    private static final float AOE_RANGE = 2.0f;
    private static final float MULTIPLIER = 1.5f;

    private VodyanitsaChargedAttack() {
    }

    public static void execute(Player player, PGCharacter character) {
        Level level = player.level();
        if (level.isClientSide()) return;

        LivingEntity target = new TargetSeeker(player, ATTACK_RANGE,
                TargetSeeker.TargetingType.LINE_OF_SIGHT).execute();
        if (target == null) return;

        Vec3 center = target.position();
        List<LivingEntity> targets = new AreaEntityCollector(level,
                center.add(-AOE_RANGE, -AOE_RANGE, -AOE_RANGE),
                center.add(AOE_RANGE, AOE_RANGE, AOE_RANGE), AOE_RANGE).execute();

        for (LivingEntity hit : targets) {
            if (hit == player) continue;

            ModDamageSpec spec = ModDamageSpec.builder(AttackType.CHARGED_ATTACK, ModElements.HYDRO.get())
                    .multiplier(MULTIPLIER)
                    .elementAmount(AttachmentType.WEAK.getInitialAmount())
                    .attackerCharacter(character)
                    .build();
            ModDamageSource source = ModDamageSource.from(spec, player);
            if (hit.level() instanceof ServerLevel serverLevel) {
                hit.hurtServer(serverLevel, source, 0f);
            }
        }
    }
}