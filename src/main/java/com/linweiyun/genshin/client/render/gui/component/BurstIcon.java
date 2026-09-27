package com.linweiyun.genshin.client.render.gui.component;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.integration.kjs.KJSBindings;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@KJSBindings
@LDLRegister(name = "bursticon", group = "minegenshin", registry = "ldlib2:ui_element")
public class BurstIcon extends UIElement {
  public BurstIcon() {
    super();
  }
}
