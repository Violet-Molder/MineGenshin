package com.linweiyun.genshin.core.character.polearm.arlecchino.attack;

import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.elementlib.core.system.about.AttachmentType;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 阿蕾奇诺的<b>下落攻击</b>。
 *
 * <p>默认实现完全复用 {@code SkillBase.plungingAttack()} 的通用逻辑：落地时对身周
 * 3 格内的敌人打一发下落攻击。如果未来阿蕾奇诺有自定义下落攻击，在这里重写。
 */
public final class ArlecchinoPlungeAttack {

    private static final double DEFAULT_RADIUS = 3.0;
    private static final float DEFAULT_MULTIPLIER = 1.0f;

    private ArlecchinoPlungeAttack() {
    }

    /**
     * 执行下落攻击（落地伤害）。
     *
     * @param player    攻击者
     * @param character 攻击者角色
     */
    public static void execute(Player player, PGCharacter character) {
        if (player.level().isClientSide() || character == null) return;

        double radius = DEFAULT_RADIUS;
        AABB impactBox = new AABB(
                player.getX() - radius, player.getY() - 1.0, player.getZ() - radius,
                player.getX() + radius, player.getY() + 2.0, player.getZ() + radius);
        List<LivingEntity> targets = player.level().getEntitiesOfClass(LivingEntity.class, impactBox,
                e -> e != player && e.isAlive());

        for (LivingEntity target : targets) {
            ModDamageSpec spec = ModDamageSpec.builder(AttackType.PLUNGING_ATTACK, ModElements.PYRO.get())
                    .multiplier(DEFAULT_MULTIPLIER)
                    .elementAmount(AttachmentType.WEAK.getInitialAmount())
                    .attackerCharacter(character)
                    .build();
            ModDamageSource source = ModDamageSource.from(spec, player);
            if (target.level() instanceof ServerLevel serverLevel) {
                target.hurt(source, 0f);
            }
        }

        // —— 后续效果 ——
        // 如果有天赋/命座在下落攻击时触发，写在这里
    }
}