package com.linweiyun.genshin.core.status;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * <b>原神式移速还原：走 / 跑 / 疾跑</b>。
 *
 * <p>原版只有「走（普通移动）」「冲刺」两档，这里还原成原神的三种：走（慢）、跑（正常）、
 * 疾跑（冲刺）。规则：
 *
 * <pre>
 * 疾跑（冲刺）   ← 玩家进入了原版冲刺模式（isSprinting()）
 * 走 / 跑        ← 其它时候由 WALK_MODE_ATTACHMENT 决定
 * </pre>
 *
 * <p>也就是说「走 / 跑 → 疾跑」的切换看的是原版冲刺有没有进来；而「走 ↔ 跑」由玩家
 * 按按钮切换（见 {@code KeyInputHandler} / {@code NetworkManager#setWalkModeToggleToServer}）。
 * 疾跑时不做任何减速，所以冲刺依旧是原版全速。
 *
 * <p>只在<b>原神模式</b>下生效：退出原神模式就还原回原版的纯走 / 跑两档。每 tick 重算，
 * 不维护额外状态，切按钮、进冲刺都会立刻被下一次 tick 收走。
 */
@EventBusSubscriber
public final class WalkRunSprintHandler {

    /** 「走」状态减速修饰符的 id。 */
    private static final Identifier WALK_MODIFIER_ID =
            Identifier.fromNamespaceAndPath(Minegenshin.MOD_ID, "walk_mode_slow");

    /** 「走」相对「跑」的速度系数（0.65 = 走只有跑的 65%）。 */
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
        // 疾跑（冲刺）时不做减速；只有「走」且没在冲刺时才慢下来
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