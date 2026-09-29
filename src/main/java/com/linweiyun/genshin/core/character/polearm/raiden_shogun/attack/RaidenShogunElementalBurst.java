package com.linweiyun.genshin.core.character.polearm.raiden_shogun.attack;

import com.linweiyun.genshin.config.character.ShenheTalentConfig;
import com.linweiyun.genshin.content.entities.ModEntities;
import com.linweiyun.genshin.content.entities.area.TalismanSpiritArea;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 雷电将军的<b>元素爆发</b>。
 *
 * <p>对身周 12×4×12 格的敌人造成雷伤，然后留下神符领域。
 *
 * <p>伤害实施 + 后续效果全部在这里完成。
 */
public final class RaidenShogunElementalBurst {

    private static final double BURST_HALF_WIDTH = 6.0;
    private static final double BURST_HALF_HEIGHT = 2.0;

    private RaidenShogunElementalBurst() {
    }

    /**
     * 执行元素爆发。
     *
     * @param player    攻击者
     * @param character 攻击者角色
     */
    public static void execute(Player player, PGCharacter character) {
        Level level = player.level();
        if (level.isClientSide()) return;

        int burstLevel = character.getData().getElementalBurstLevel();
        float castDamage = ShenheTalentConfig.getBurstCastDamage(burstLevel);

        // 伤害结算
        AABB castBox = new AABB(
                player.getX() - BURST_HALF_WIDTH, player.getY() - BURST_HALF_HEIGHT, player.getZ() - BURST_HALF_WIDTH,
                player.getX() + BURST_HALF_WIDTH, player.getY() + BURST_HALF_HEIGHT, player.getZ() + BURST_HALF_WIDTH);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, castBox,
                e -> e != player);
        for (LivingEntity target : targets) {
            ModDamageSpec spec = ModDamageSpec.builder(AttackType.ELEMENTAL_BURST, ModElements.ELECTRO.get())
                    .multiplier(castDamage)
                    .elementAmount(1.0f)
                    .attackerCharacter(character)
                    .build();
            ModDamageSource source = ModDamageSource.from(spec, player);
            if (target.level() instanceof ServerLevel serverLevel) {
                target.hurtServer(serverLevel, source, 0f);
            }
        }

        // —— 后续效果 ——
        // 召唤神符领域
        PlayerCharactersAttachment attachment =
                player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        PGCharacter currentChar = attachment.getCurrentCharacter();

        TalismanSpiritArea field = ModEntities.FIELD_TALISMAN_SPIRIT.get()
                .create(player.level(), EntitySpawnReason.EVENT);
        if (field != null) {
            field.setPos(player.position());
            if (currentChar != null) {
                field.setOwner(player, currentChar);
            }
            player.level().addFreshEntity(field);
        }
    }
}