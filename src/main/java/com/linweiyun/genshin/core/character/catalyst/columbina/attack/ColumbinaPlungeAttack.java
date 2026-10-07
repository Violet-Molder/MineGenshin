package com.linweiyun.genshin.core.character.catalyst.columbina.attack;

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
 * 哥伦比娅的<b>下落攻击</b>。
 *
 * <p>落地身周 3 格水伤。
 */
public final class ColumbinaPlungeAttack {

    private static final double DEFAULT_RADIUS = 3.0;
    private static final float DEFAULT_MULTIPLIER = 1.0f;

    private ColumbinaPlungeAttack() {
    }

    public static void execute(Player player, PGCharacter character) {
        if (player.level().isClientSide() || character == null) return;

        AABB impactBox = new AABB(
                player.getX() - DEFAULT_RADIUS, player.getY() - 1.0, player.getZ() - DEFAULT_RADIUS,
                player.getX() + DEFAULT_RADIUS, player.getY() + 2.0, player.getZ() + DEFAULT_RADIUS);
        List<LivingEntity> targets = player.level().getEntitiesOfClass(LivingEntity.class, impactBox,
                e -> e != player && e.isAlive());

        for (LivingEntity target : targets) {
            ModDamageSpec spec = ModDamageSpec.builder(AttackType.PLUNGING_ATTACK, ModElements.HYDRO.get())
                    .multiplier(DEFAULT_MULTIPLIER)
                    .elementAmount(AttachmentType.WEAK.getInitialAmount())
                    .attackerCharacter(character)
                    .build();
            ModDamageSource source = ModDamageSource.from(spec, player);
            if (target.level() instanceof ServerLevel serverLevel) {
                target.hurt(source, 0f);
            }
        }
    }
}