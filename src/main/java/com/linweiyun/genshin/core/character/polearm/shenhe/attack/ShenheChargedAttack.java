package com.linweiyun.genshin.core.character.polearm.shenhe.attack;

import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.elementlib.core.element.GenshinElement;
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
 * 申鹤的<b>重击</b>（大剑持续型重击）。
 *
 * <p>申鹤临时改为大剑类，所以她的重击是「持续型」—— 按住左键蓄力到阈值后进入
 * 持续重击状态，每 {@code INTERVAL} 刻结算一次伤害，直到松手或到达最长时间。
 *
 * <p>伤害实施 + 后续效果全部集中在这里。
 */
public final class ShenheChargedAttack {

    /** 持续重击的判定半径（格）。 */
    private static final float RADIUS = 2.5f;

    /** 每一下的倍率（占位值，真正数值在 {@code ClaymoreSkill} 子类覆盖中）。 */
    private static final float MULTIPLIER = 1.0f;

    private ShenheChargedAttack() {
    }

    /**
     * 执行持续重击的<b>一次伤害结算</b>。
     * 由 {@code ClaymoreSkill.chargeAttack()} 在每次伤害点时调用。
     *
     * @param player    攻击者
     * @param character 攻击者角色
     */
    public static void execute(Player player, PGCharacter character) {
        Level level = player.level();
        if (level.isClientSide() || character == null) return;

        double radius = RADIUS;
        AABB box = new AABB(
                player.getX() - radius, player.getY() - 1.0, player.getZ() - radius,
                player.getX() + radius, player.getY() + 2.0, player.getZ() + radius);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive());

        GenshinElement element = ModElements.CYRO.get();
        for (LivingEntity target : targets) {
            ModDamageSpec spec = ModDamageSpec.builder(AttackType.CHARGED_ATTACK, element)
                    .multiplier(MULTIPLIER)
                    .elementAmount(AttachmentType.WEAK.getInitialAmount())
                    .attackerCharacter(character)
                    .build();
            ModDamageSource source = ModDamageSource.from(spec, player);
            if (target.level() instanceof ServerLevel serverLevel) {
                target.hurtServer(serverLevel, source, 0f);
            }
        }

        // —— 后续效果 ——
        // 目前申鹤重击没有额外效果
    }
}