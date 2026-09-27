package com.linweiyun.genshin.client.render.gui.hud;

import com.lowdragmc.lowdraglib2.gui.hud.ModularHudLayer;
import com.lowdragmc.lowdraglib2.math.Size;
import net.minecraft.client.Minecraft;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@FunctionalInterface
public interface MyModularHudLayer extends ModularHudLayer {
  @Override
  default Size getScreenSize() {
    return Size.of(
        Minecraft.getInstance().getWindow().getGuiScaledWidth(),
        Minecraft.getInstance().getWindow().getGuiScaledHeight());
  }
}
