package com.linweiyun.genshin.core.system.registry.register;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.elementlib.core.attachment.StatusContainer;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * ItemStack 专用的 DataComponentType 注册
 * 让物品可以携带 StatusContainer（武器元素附魔等）
 */
public class ModStatusDataComponents {
    public static final DeferredRegister.DataComponents REGISTRAR =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Minegenshin.MOD_ID);

    public static final Supplier<DataComponentType<StatusContainer>> CONTAINER =
            REGISTRAR.register("status_container",
                    () -> DataComponentType.<StatusContainer>builder()
                            .persistent(StatusContainer.CODEC)
                            .build());

    public static void register(IEventBus bus) {
        REGISTRAR.register(bus);
    }
}
