package com.linweiyun.genshin.content.items.development;

import com.linweiyun.genshin.content.items.TeyvatItem;
import com.linweiyun.genshin.content.items.artifact.inventory.ArtifactInventory;
import com.linweiyun.genshin.content.items.artifact.type.ArtifactType;
import com.linweiyun.genshin.content.items.component.ArtifactStatsComponent;
import com.linweiyun.genshin.content.items.component.WeaponStatsComponent;
import com.linweiyun.genshin.content.items.weapon.WeaponItem;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.PGCharacterData;
import com.linweiyun.genshin.core.system.registry.register.ModDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class AdviceBookItem extends CharacterDevelopmentItem {

    public static final int EXP_VALUE = 1000;

    private final int expValue;

    public AdviceBookItem(Properties properties) {
        this(properties, EXP_VALUE);
    }

    public AdviceBookItem(Properties properties, int expValue) {
        super(properties);
        this.expValue = expValue;
    }

    public int getExpValue() {
        return expValue;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
                                                           @NotNull InteractionHand usedHand) {
        ItemStack heldStack = player.getItemInHand(usedHand);
        if (level.isClientSide()) {
            return InteractionResultHolder.success(heldStack);
        }

        boolean isGenshinMode = player.getData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT);
        if (!isGenshinMode) {
            return InteractionResultHolder.pass(heldStack);
        }

        PlayerCharactersAttachment attachment =
                player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        PGCharacter character = attachment.getCurrentCharacter();
        if (character == null) {
            return InteractionResultHolder.pass(heldStack);
        }

        PGCharacterData data = character.getData();
        if (data == null) {
            return InteractionResultHolder.pass(heldStack);
        }

        character.addExp(expValue);

        ArtifactInventory inv = data.getArtifactInventory();
        addArtifactExpIfPresent(data.getFlower(), ArtifactType.FLOWER, inv);
        addArtifactExpIfPresent(data.getPlume(), ArtifactType.PLUME, inv);
        addArtifactExpIfPresent(data.getSands(), ArtifactType.SANDS, inv);
        addArtifactExpIfPresent(data.getGoblet(), ArtifactType.GOBLET, inv);
        addArtifactExpIfPresent(data.getCirclet(), ArtifactType.CIRCLET, inv);
        addWeaponExpIfPresent(data.getWeapon(), inv);

        heldStack.shrink(1);

        player.sendSystemMessage(
                Component.literal("使用了经验书，当前角色获得 " + expValue + " 经验"));

        return InteractionResultHolder.consume(heldStack);
    }

    private void addArtifactExpIfPresent(ItemStack stack, ArtifactType type,
                                          ArtifactInventory inv) {
        if (stack.isEmpty()) return;
        if (!(stack.getItem() instanceof TeyvatItem teyvatItem)) return;
        int star = teyvatItem.getStar();
        if (star <= 0) return;

        ArtifactStatsComponent stats = stack.get(ModDataComponents.ARTIFACT_STATS);
        if (stats == null) return;

        int slot = ArtifactInventory.typeToSlot(type);
        stats.setOnStatsChanged(() -> inv.markDirty(slot));
        stats.addExp(expValue, star, type);
        stack.set(ModDataComponents.ARTIFACT_STATS, stats);
    }

    private void addWeaponExpIfPresent(ItemStack stack, ArtifactInventory inv) {
        if (stack.isEmpty()) return;
        if (!(stack.getItem() instanceof WeaponItem weapon)) return;
        int star = weapon.getStar();
        if (star <= 0) return;

        WeaponStatsComponent stats = stack.get(ModDataComponents.WEAPON_STATS.get());
        if (stats == null) return;

        stats.setOnStatsChanged(() -> inv.markDirty(ArtifactInventory.SLOT_WEAPON));
        stats.addExp(expValue, star);
        stack.set(ModDataComponents.WEAPON_STATS.get(), stats);
    }
}