package com.linweiyun.genshin.core.character.polearm.raiden_shogun.attack;

import com.linweiyun.genshin.content.skill_node.AreaEntityCollector;
import com.linweiyun.genshin.content.skill_node.RushesForward;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.element.ModElements;
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
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import com.linweiyun.genshin.core.system.combat.decay.ModDecayGroups;

/**
 * 雷电将军的<b>重击</b>（触发型突刺）。
 *
 * <p>向前冲刺 10 格，对路径上的敌人造成雷伤。
 *
 * <p>伤害实施 + 后续效果全部在这里完成。
 */
public final class RaidenShogunChargedAttack {

    private static final float DASH_DISTANCE = 10f;
    private static final float ATTACK_INFLATE = 0.5f;
    private static final float MULTIPLIER = 3.5f;

    private RaidenShogunChargedAttack() {
    }

    /**
     * 执行重击。
     *
     * @param player    攻击者
     * @param character 攻击者角色
     */
    public static void execute(Player player, PGCharacter character) {
        Level level = player.level();
        if (level.isClientSide()) return;

        Vec3 startPos = player.position();
        Vec3 dashTotal = new RushesForward(player, DASH_DISTANCE).execute();
        Vec3 rawEndPos = startPos.add(dashTotal);
        HitResult hit = level.clip(new ClipContext(startPos, rawEndPos,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 endPos = hit.getLocation();

        List<LivingEntity> targets = new AreaEntityCollector(level, startPos, endPos, ATTACK_INFLATE).execute();
        for (LivingEntity target : targets) {
            if (target == player) continue;

            ModDamageSpec spec = ModDamageSpec.builder(AttackType.CHARGED_ATTACK, ModElements.ELECTRO.get())
                    .multiplier(MULTIPLIER)
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
        // 目前无特殊效果
    }
}