package com.linweiyun.genshin.content.items.food;

import com.linweiyun.genshin.content.items.TeyvatItem;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public class FoodItem extends TeyvatItem {
    private final CharacterFoodProperties foodProperties;

    public FoodItem(CharacterFoodProperties food, Properties properties) {
        super(properties);
        this.foodProperties = food;
    }

    private CharacterFoodProperties getFood() {
        return foodProperties != null ? foodProperties : CharacterFoodProperties.EMPTY;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (getFood().hasHeal()) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(player.getItemInHand(hand));
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return getFood().eatDurationTicks();
    }

    @Override
    public @NonNull UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.EAT;
    }

    @Override
    public @NotNull ItemStack finishUsingItem(
            @NotNull ItemStack stack, @NotNull Level level, @NotNull LivingEntity livingEntity) {
        if (!(livingEntity instanceof Player player)) return stack;
        if (level.isClientSide()) return stack;

        CharacterFoodProperties food = getFood();
        if (!food.hasHeal()) return stack;

        PlayerCharactersAttachment attachment =
                player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);

        PGCharacter currentChar = attachment.getCurrentCharacter();
        if (currentChar != null) {
            float maxHP = (float) currentChar.getData().getAttributeTotalValue(
                    ModAttributes.MAX_HP.get());
            currentChar.getData().healHP(food.calculateHeal(maxHP));
        }

        if (food.reviveFallen()) {
            for (int i = 0; i < 4; i++) {
                PGCharacter partyChar = attachment.getPartyCharacter(i);
                if (partyChar == null) continue;
                if (partyChar.getData().getCurrentHP() <= 0) {
                    float maxHP = (float) partyChar.getData().getAttributeTotalValue(
                            ModAttributes.MAX_HP.get());
                    partyChar.revive((int) food.calculateHeal(maxHP));
                }
            }
        }

        attachment.syncToPlayer((ServerPlayer) player);

        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return stack;
    }
}