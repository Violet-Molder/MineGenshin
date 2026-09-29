package com.linweiyun.genshin.core.character.util.handler;

import com.linweiyun.genshin.content.items.TeyvatItem;
import com.linweiyun.genshin.content.items.artifact.inventory.ArtifactInventory;
import com.linweiyun.genshin.content.items.artifact.type.ArtifactType;
import com.linweiyun.genshin.content.items.component.ArtifactStatsComponent;
import com.linweiyun.genshin.content.items.component.WeaponStatsComponent;
import com.linweiyun.genshin.content.items.weapon.WeaponItem;
import com.linweiyun.genshin.core.attachment.AdventurerInfoAttachment;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.PGCharacterData;
import com.linweiyun.genshin.core.system.registry.register.ModDataComponents;
import com.linweiyun.genshin.core.world.TeyvatWorldInvasion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

@EventBusSubscriber
public class CharacterExpGainHandler {

    @SubscribeEvent
    public static void onPickupXpOrb(PlayerXpEvent.XpChange event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide()){
            if (player.level() instanceof ServerLevel sl && !TeyvatWorldInvasion.get(sl).isInvaded()) return;
            boolean isGenshinMode = player.getData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT);

            if (isGenshinMode) {
                int amount = event.getAmount();
                PlayerCharactersAttachment attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
                PGCharacter character = attachment.getCurrentCharacter();
                if (character == null) return;
                character.addExp(amount);

                AdventurerInfoAttachment advInfo = player.getData(AttachmentRegistration.ADVENTURER_INFO_ATTACHMENT);
                advInfo.addExp(amount);
                advInfo.syncToPlayer((ServerPlayer) player);

                PGCharacterData data = character.getData();
                if (data != null) {
                    ArtifactInventory inv = data.getArtifactInventory();
                    addArtifactExp(data.getFlower(), ArtifactType.FLOWER, amount, inv);
                    addArtifactExp(data.getPlume(), ArtifactType.PLUME, amount, inv);
                    addArtifactExp(data.getSands(), ArtifactType.SANDS, amount, inv);
                    addArtifactExp(data.getGoblet(), ArtifactType.GOBLET, amount, inv);
                    addArtifactExp(data.getCirclet(), ArtifactType.CIRCLET, amount, inv);

                    addWeaponExp(data.getWeapon(), amount, inv);
                }
            }
        }
    }

    private static void addArtifactExp(ItemStack stack, ArtifactType type, int amount, ArtifactInventory inv) {
        if (stack.isEmpty()) return;
        if (!(stack.getItem() instanceof TeyvatItem teyvatItem)) return;
        int star = teyvatItem.getStar();
        if (star <= 0) return;

        ArtifactStatsComponent stats = stack.get(ModDataComponents.ARTIFACT_STATS);
        if (stats == null) return;

        int slot = ArtifactInventory.typeToSlot(type);
        stats.setOnStatsChanged(() -> inv.markDirty(slot));
        stats.addExp(amount, star, type);
        stack.set(ModDataComponents.ARTIFACT_STATS, stats);
    }

    private static void addWeaponExp(ItemStack stack, int amount, ArtifactInventory inv) {
        if (stack.isEmpty()) return;
        if (!(stack.getItem() instanceof WeaponItem weapon)) return;
        int star = weapon.getStar();
        if (star <= 0) return;

        WeaponStatsComponent stats = stack.get(ModDataComponents.WEAPON_STATS.get());
        if (stats == null) return;

        stats.setOnStatsChanged(() -> inv.markDirty(ArtifactInventory.SLOT_WEAPON));
        stats.addExp(amount, star);
        stack.set(ModDataComponents.WEAPON_STATS.get(), stats);
    }
}