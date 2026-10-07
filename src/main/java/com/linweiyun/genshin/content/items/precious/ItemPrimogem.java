package com.linweiyun.genshin.content.items.precious;

import com.linweiyun.genshin.content.items.artifact.type.ArtifactType;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.PGCharacterData;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.lowdragmc.lowdraglib2.syncdata.annotation.RPCMethod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class ItemPrimogem extends PreciousItem {

  public ItemPrimogem(Properties properties) {
    super(properties);
  }

  @Override
  @RPCMethod
  public InteractionResultHolder<ItemStack> use(
      Level level, @NotNull Player player, @NotNull InteractionHand usedHand) {
    if (!level.isClientSide()) {
      ItemStack stack = player.getItemInHand(usedHand);

      int primogem =
          player.getData(AttachmentRegistration.PRIMOGEM_ATTACHMENT.get()) + stack.getCount();
      player.setData(AttachmentRegistration.PRIMOGEM_ATTACHMENT.get(), primogem);
      System.out.println(player);
      NetworkManager.setPrimogemToPlayer((ServerPlayer) player, primogem);
      player.sendSystemMessage(
          Component.literal(
              "你获得了 " + stack.getCount() + " 个原石，" + "当前原石数量为 " + player.getData(AttachmentRegistration.PRIMOGEM_ATTACHMENT.get())));
      stack.shrink(stack.getCount());
      if (player.getData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT)){
        PlayerCharactersAttachment attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT.get());
        PGCharacter character = attachment.getCurrentCharacter();
        character.unequipArtifact(ArtifactType.FLOWER);
        character.unequipArtifact(ArtifactType.PLUME);
        character.unequipArtifact(ArtifactType.SANDS);
        character.unequipArtifact(ArtifactType.GOBLET);
        character.unequipArtifact(ArtifactType.CIRCLET);
        PGCharacterData data = character.getData();
      }


    }
    return InteractionResultHolder.success(player.getItemInHand(usedHand));
  }
}