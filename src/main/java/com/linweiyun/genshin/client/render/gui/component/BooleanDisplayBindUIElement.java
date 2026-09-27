package com.linweiyun.genshin.client.render.gui.component;

import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableUIElement;
import com.lowdragmc.lowdraglib2.integration.kjs.KJSBindings;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@KJSBindings
@LDLRegister(name = "boolean-bind-uie", group = "minegenshin", registry = "ldlib2:ui_element")
public class BooleanDisplayBindUIElement extends BindableUIElement<Boolean> {
  public boolean display;

  @Override
  public Boolean getValue() {
    return this.display;
  }

  @Override
  public BindableUIElement<Boolean> setValue(@Nullable Boolean value, boolean notify) {
    boolean newDisplay = Boolean.TRUE.equals(value);
    this.display = newDisplay;
    if (newDisplay) {
      this.addClass("__selected__");
      this.removeClass("__unselected__");
    } else {
      this.addClass("__unselected__");
      this.removeClass("__selected__");
    }
    return this;
  }
}