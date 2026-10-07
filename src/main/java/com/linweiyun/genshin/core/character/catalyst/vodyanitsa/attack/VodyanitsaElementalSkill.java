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
 * 沃雅妮莎的<b>元素战技</b>。
 *
 * <p>以自身为中心造成 8×4×8 范围水伤。
 */
public final class VodyanitsaElementalSkill {

    private static final float HALF_WIDTH = 4.0f;
    private static final float HALF_HEIGHT = 2.0f;
    private static final float MULTIPLIER = 2.0f;

    private VodyanitsaElementalSkill() {
    }

    public static void execute(Player player, PGCharacter character, int skillTime) {
        Level level = player.level();
        if (level.isClientSide()) return;

        AABB box = new AABB(
                player.getX() - HALF_WIDTH, player.getY() - HALF_HEIGHT, player.getZ() - HALF_WIDTH,
                player.getX() + HALF_WIDTH, player.getY() + HALF_HEIGHT, player.getZ() + HALF_WIDTH);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive());

        for (LivingEntity target : targets) {
            ModDamageSpec spec = ModDamageSpec.builder(AttackType.ELEMENTAL_SKILL, ModElements.HYDRO.get())
                    .multiplier(MULTIPLIER)
                    .elementAmount(AttachmentType.WEAK.getInitialAmount())
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