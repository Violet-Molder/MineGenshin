package com.linweiyun.genshin.core.character.catalyst.columbina.attack;

import com.linweiyun.genshin.content.skill_node.AreaEntityCollector;
import com.linweiyun.genshin.content.skill_node.TargetSeeker;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 哥伦比娅的<b>重击</b>（百分比生命值伤害）。
 *
 * <p>对索敌目标造成 3% 生命值上限的直接伤害。
 */
public final class ColumbinaChargedAttack {

    private static final float CHARGED_HP_RATIO = 0.03f;
    private static final int ATTACK_RANGE = 10;
    private static final float AOE_RANGE = 1.5f;

    private ColumbinaChargedAttack() {
    }

    /**
     * 执行重击。
     */
    public static void execute(Player player, PGCharacter character) {
        Level level = player.level();
        if (level.isClientSide()) return;

        LivingEntity primaryTarget = new TargetSeeker(player, ATTACK_RANGE,
                TargetSeeker.TargetingType.LINE_OF_SIGHT).execute();
        if (primaryTarget == null) return;

        Vec3 center = primaryTarget.position();
        List<LivingEntity> targets = new AreaEntityCollector(level,
                center.add(-AOE_RANGE, -AOE_RANGE, -AOE_RANGE),
                center.add(AOE_RANGE, AOE_RANGE, AOE_RANGE),
                AOE_RANGE).execute();

        for (LivingEntity target : targets) {
            if (target == player) continue;

            ModDamageSpec spec = ModDamageSpec.lunarDirectHp(CHARGED_HP_RATIO);
            ModDamageSource source = ModDamageSource.from(spec, player);
            if (target.level() instanceof ServerLevel serverLevel) {
                target.hurt(source, 0f);
            }
        }

        // —— 后续效果 ——
    }
}