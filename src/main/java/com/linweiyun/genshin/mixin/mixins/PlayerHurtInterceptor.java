package com.linweiyun.genshin.mixin.mixins;

import com.linweiyun.genshin.content.entities.teyvat.ITeyvatBoss;
import com.linweiyun.genshin.content.entities.teyvat.monster.TeyvatMonster;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.element.GenshinElement;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.damage.CombatEntityAccessor;
import com.linweiyun.genshin.core.system.combat.damage.CombatMath;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.TeyvatConvertedDamageSource;
import com.linweiyun.genshin.core.world.TeyvatWorldInvasion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class PlayerHurtInterceptor {

    @Inject(method = "actuallyHurt", at = @At("HEAD"), cancellable = true)
    private void onPlayerActuallyHurt(ServerLevel level, DamageSource source, float damage,
                                      CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (!player.getData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT)) return;
        if (!TeyvatWorldInvasion.get(level).isInvaded()) return;
        if (source instanceof ModDamageSource) return;

        Entity attacker = source.getEntity();
        boolean isTeyvatMonsterOrBoss = attacker instanceof TeyvatMonster
                || attacker instanceof ITeyvatBoss;

        // 已经换算过的伤害源不再吃那个 ×2.5：
        //   TeyvatConvertedDamageSource —— 怪物攻击力换算，已经是原神口径；
        //   CompatConvertedDamageSource —— 玩家打出去的其他 MOD 伤害换算，同样已经是角色口径。
        boolean isAlreadyConverted = source instanceof TeyvatConvertedDamageSource
                || source instanceof com.linweiyun.genshin.core.system.compat.CompatConvertedDamageSource;
        float adjustedDamage = damage;
        if (!isAlreadyConverted && !isTeyvatMonsterOrBoss && attacker != null) {
            adjustedDamage = damage * 2.5f;
        }

        PlayerCharactersAttachment attachment =
                player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        PGCharacter current = attachment.getCurrentCharacter();
        if (current != null) {
            LivingEntity livingAttacker = attacker instanceof LivingEntity le ? le : null;
            int attackerLevel = CombatEntityAccessor.getAttackerLevel(livingAttacker, null);
            double defense = CombatEntityAccessor.getDefenderDefense(player, current);
            float defenseZone = CombatMath.defenseZone(attackerLevel, defense);

            GenshinElement element = elementFromSource(source);
            float rawResistance = CombatEntityAccessor.getDefenderResistance(player, current, element);
            float resistanceZone = CombatMath.resistanceZone(rawResistance);

            adjustedDamage *= defenseZone * resistanceZone;

            current.hurt(adjustedDamage);
            ci.cancel();
        }
    }

    private static GenshinElement elementFromSource(DamageSource source) {
        // 换算伤害源自己带着元素类型，直接用它，别再按伤害类型猜
        GenshinElement converted =
                com.linweiyun.genshin.core.system.compat.CompatConvertedDamageSource.elementOf(source);
        if (converted != null) {
            return converted;
        }
        if (source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.CAMPFIRE)
                || source.is(DamageTypes.ON_FIRE) || source.is(DamageTypes.LAVA)
                || source.is(DamageTypes.HOT_FLOOR) || source.is(DamageTypes.FIREBALL)
                || source.is(DamageTypes.UNATTRIBUTED_FIREBALL)
                || source.is(DamageTypes.SULFUR_CUBE_HOT)) {
            return ModElements.PYRO.get();
        }
        if (source.is(DamageTypes.LIGHTNING_BOLT)) {
            return ModElements.ELECTRO.get();
        }
        if (source.is(DamageTypes.FREEZE)) {
            return ModElements.CYRO.get();
        }
        if (source.is(DamageTypes.DROWN)) {
            return ModElements.HYDRO.get();
        }
        return ModElements.FYSIKOS.get();
    }
}
