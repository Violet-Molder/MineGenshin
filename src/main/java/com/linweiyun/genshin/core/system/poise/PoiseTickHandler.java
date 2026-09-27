package com.linweiyun.genshin.core.system.poise;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * 韧性的逐刻结算 —— 自然衰减（自恢复）与破韧后的驻留计时。
 *
 * <p>照 {@link com.linweiyun.genshin.core.system.shield.ShieldTickHandler} 的写法：
 * 单独一个订阅者，不复用别的 tick 处理器（职责不同：那个管护盾到期，这个管韧性恢复）。
 *
 * <p>{@code hasData} 先判一次：只有挨过削韧、真的建出韧性状态的实体才需要逐刻算，
 * 不给每一只路过的生物都挂附件。
 */
@EventBusSubscriber
public final class PoiseTickHandler {

    private PoiseTickHandler() {
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        if (living.level().isClientSide()) {
            return;
        }
        if (!living.hasData(AttachmentRegistration.POISE.get())) {
            return;
        }
        PoiseService.tick(living);
    }
}
