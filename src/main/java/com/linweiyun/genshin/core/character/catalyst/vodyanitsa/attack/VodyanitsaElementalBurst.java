package com.linweiyun.genshin.core.character.catalyst.vodyanitsa.attack;

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
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 沃雅妮莎的<b>元素爆发</b>。
 *
 * <p>对 14×6×14 范围造成大额水伤。
 */
public final class VodyanitsaElementalBurst {

    private static final double HALF_WIDTH = 7.0;
    private static final double HALF_HEIGHT = 3.0;
    private static final float MULTIPLIER = 5.0f;

    private VodyanitsaElementalBurst() {
    }

    public static void execute(Player player, PGCharacter character) {
        Level level = player.level();
        if (level.isClientSide()) return;

        AABB box = new AABB(
                player.getX() - HALF_WIDTH, player.getY() - HALF_HEIGHT, player.getZ() - HALF_WIDTH,
                player.getX() + HALF_WIDTH, player.getY() + HALF_HEIGHT, player.getZ() + HALF_WIDTH);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive());

        for (LivingEntity target : targets) {
            ModDamageSpec spec = ModDamageSpec.builder(AttackType.ELEMENTAL_BURST, ModElements.HYDRO.get())
                    .multiplier(MULTIPLIER)
                    .elementAmount(1.0f)
                    .attackerCharacter(character)
                    .build();
            ModDamageSource source = ModDamageSource.from(spec, player);
            if (target.level() instanceof ServerLevel serverLevel) {
                target.hurt(source, 0f);
            }
        }

        // —— 后续效果 ——
        // 目前无特殊效果
    }
}