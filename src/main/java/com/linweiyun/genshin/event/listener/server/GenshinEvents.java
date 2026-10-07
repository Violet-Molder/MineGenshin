package com.linweiyun.genshin.event.listener.server;

import com.linweiyun.elementlib.api.event.ElibEvents;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import com.linweiyun.genshin.content.effect.character.artifact.TenacityOfTheMillelith4;
import com.linweiyun.genshin.content.items.weapon.WeaponItem;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.system.combat.action.ActionKind;
import com.linweiyun.genshin.event.game.CharacterLeaveFieldEvent;
import com.linweiyun.genshin.event.game.ElementalBurstCastEvent;
import com.linweiyun.genshin.event.game.ElementalSkillCastEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 本模组注册事件监听者的地方，{@link #init()} 是唯一登记入口。
 *
 * <p>事件本身在 {@code event/game}（以及 elementlib 的 {@code api/event}），订阅在 {@code event/listener}。
 * 当前登记：千岩牢固四件套（伤害结算）、武器被动（技能释放 / 角色退场）。
 */
public final class GenshinEvents {

    private GenshinEvents() {
    }

    public static void init() {
        // 统一的事件日志走本模组的 EVENT 组（按系统），功能域的日志由各触发点自己打
        ElibEvents.setEventLogger(ModLog.getLogger(LogGroup.EVENT));
        ElibEvents.register(TenacityOfTheMillelith4.class);
        ElibEvents.register(GenshinEvents.class);
    }

    // ==================== 武器被动 ====================

    @SubscribeEvent
    public static void onElementalSkillCast(ElementalSkillCastEvent event) {
        notifyWeaponAbilityCast(event.player(), event.character(), event.kind());
    }

    @SubscribeEvent
    public static void onElementalBurstCast(ElementalBurstCastEvent event) {
        notifyWeaponAbilityCast(event.player(), event.character(), ActionKind.ELEMENTAL_BURST);
    }

    /** 角色退场时触发武器退场被动。 */
    @SubscribeEvent
    public static void onCharacterLeaveField(CharacterLeaveFieldEvent event) {
        WeaponItem weaponItem = weaponOf(event);
        if (weaponItem != null) {
            weaponItem.onLeaveField(event.player(), event.character());
        }
    }

    private static void notifyWeaponAbilityCast(Player player, PGCharacter character, ActionKind kind) {
        ItemStack weapon = character.getData().getWeapon();
        if (weapon != null && !weapon.isEmpty() && weapon.getItem() instanceof WeaponItem weaponItem) {
            weaponItem.onAbilityCast(player, character, kind);
        }
    }

    private static WeaponItem weaponOf(CharacterLeaveFieldEvent event) {
        ItemStack weapon = event.character().getData().getWeapon();
        if (weapon == null || weapon.isEmpty() || !(weapon.getItem() instanceof WeaponItem weaponItem)) {
            return null;
        }
        return weaponItem;
    }
}
