package com.linweiyun.genshin.core.system.reaction.builtin;

import com.linweiyun.genshin.content.entities.area.StellarPrismEntity;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.damage.DamageIndicatorFactory;
import com.linweiyun.genshin.core.system.reaction.ReactionPriorityCalculator;
import com.linweiyun.genshin.core.system.registry.register.ModReactionTypes;
import com.linweiyun.elementlib.api.ElementalReactionType;
import com.linweiyun.elementlib.core.system.reaction.ReactionContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * 星超导 —— 超导的星烁转化反应。入口在 {@link SuperConductReaction} 内：
 * 队伍中存在星超导户口时，把这次超导转为星超导：生成 / 刷新极星辉域，并不再结算超导自身的伤害。
 */
public final class StellarConduceReaction {

    private StellarConduceReaction() {
    }

    /** @return true = 已转为星超导（调用方不要再走超导那套结算） */
    public static boolean convert(ReactionContext context) {
        LivingEntity target = context.targetEntity();
        if (target == null || !(target.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!ReactionPriorityCalculator.hasStellarConduceHousehold(level)) {
            return false;
        }
        // 转化本身与领域无关：领域只影响触发之后的效果，任何领域侧异常都不允许挡住反应
        try {
        Player owner = context.attackerEntity() instanceof Player player ? player : null;
        StellarPrismEntity.spawnOrRefresh(level, target.position(), owner, resolveCharacter(context.attackerEntity()));
        // 星超导按后手（触发）元素分：先冰后雷 = 雷，先雷后冰 = 冰
        com.linweiyun.elementlib.core.element.GenshinElement triggerElement =
                context.attackerElement().getMainElement();
        boolean electroTriggered = triggerElement == ModElements.ELECTRO.get();
        ElementalReactionType convertedType = electroTriggered
                ? ModReactionTypes.STELLAR_CONDUCE.get()
                : ModReactionTypes.STELLAR_CONDUCE.get();
        DamageIndicatorFactory.stellarReactionGradient(target, convertedType, triggerElement);
        } catch (RuntimeException e) {
            return true;
        }
        return true;
    }

    @Nullable
    private static PGCharacter resolveCharacter(@Nullable Entity attacker) {
        if (!(attacker instanceof Player player)) {
            return null;
        }
        PlayerCharactersAttachment attachment = player.getData(
                AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        return attachment == null ? null : attachment.getCurrentCharacter();
    }
}