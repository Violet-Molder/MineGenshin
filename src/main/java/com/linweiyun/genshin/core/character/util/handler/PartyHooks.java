package com.linweiyun.genshin.core.character.util.handler;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.function.Consumer;

/** 队伍钩子分发：前台角色走 front*，后台角色走 back*。 */
public final class PartyHooks {

    private PartyHooks() {
    }

    public static void damage(Player player, LivingEntity target, ModDamageSpec spec) {
        if (player == null || target == null) {
            return;
        }
        each(player, front -> front.frontDamage(player, target, spec), back -> back.backDamage(player, target, spec));
    }

    public static void normalAttack(Player player) {
        each(player, front -> front.frontNormalAttack(player), back -> back.backNormalAttack(player));
    }

    public static void skill(Player player, int skillTime) {
        each(player, front -> front.frontSkill(player, skillTime), back -> back.backSkill(player, skillTime));
    }

    public static void burst(Player player) {
        each(player, front -> front.frontBurst(player), back -> back.backBurst(player));
    }

    private static void each(Player player, Consumer<PGCharacter> frontAction, Consumer<PGCharacter> backAction) {
        if (player == null) {
            return;
        }
        PlayerCharactersAttachment attachment = player.getData(
                AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        if (attachment == null) {
            return;
        }
        PGCharacter front = attachment.getCurrentCharacter();
        for (int i = 0; i < 4; i++) {
            PGCharacter member = attachment.getPartyCharacter(i);
            if (member == null) {
                continue;
            }
            if (member == front) {
                frontAction.accept(member);
            } else {
                backAction.accept(member);
            }
        }
    }
}