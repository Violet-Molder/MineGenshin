package com.linweiyun.genshin.core.system.registry.register;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.menu.CharacterInfoMenu;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(net.minecraft.core.registries.BuiltInRegistries.MENU, Minegenshin.MOD_ID);

    public static final Supplier<MenuType<CharacterInfoMenu>> CHARACTER_INFO_MENU =
            MENUS.register("character_info", () -> new MenuType<>(CharacterInfoMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final Supplier<MenuType<ModularUIContainerMenu>> BACKPACK_UI_MENU =
            MENUS.register("backpack_ui", () -> new MenuType<>(
                    (containerId, inventory) -> {
                        throw new UnsupportedOperationException("ModularUIContainerMenu must be created via IContainerUIHolder");
                    },
                    FeatureFlags.DEFAULT_FLAGS
            ));
    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}