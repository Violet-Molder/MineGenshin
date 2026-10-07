package com.linweiyun.genshin.core.system;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * <b>原神式移速还原：走 / 跑 / 疾跑</b>。
 */
@EventBusSubscriber
public final class WalkRunSprintHandler {

    private static final ResourceLocation WALK_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(Minegenshin.MOD_ID, "walk_mode_slow");

    private static final double WALK_SPEED_FACTOR = 0.65;

    private WalkRunSprintHandler() {
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }

        boolean walkMode =
                player.hasData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT)
                        && player.getData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT)
                        && player.getData(AttachmentRegistration.WALK_MODE_ATTACHMENT);
        boolean shouldSlow = walkMode && !player.isSprinting();
        boolean alreadySlowed = speed.hasModifier(WALK_MODIFIER_ID);

        if (shouldSlow && !alreadySlowed) {
            speed.addTransientModifier(new AttributeModifier(
                    WALK_MODIFIER_ID, -(1.0 - WALK_SPEED_FACTOR), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (!shouldSlow && alreadySlowed) {
            speed.removeModifier(WALK_MODIFIER_ID);
        }
    }
}