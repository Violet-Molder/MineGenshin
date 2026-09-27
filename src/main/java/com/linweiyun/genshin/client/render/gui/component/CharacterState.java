package com.linweiyun.genshin.client.render.gui.component;

import com.linweiyun.genshin.client.render.gui.component.BooleanDisplayBindUIElement;
import com.linweiyun.genshin.client.render.gui.component.StackBindUIElement;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataProvider;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.ITickable;
import com.lowdragmc.lowdraglib2.integration.kjs.KJSBindings;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.lowdragmc.lowdraglib2.syncdata.ISubscription;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@ParametersAreNonnullByDefault
@KJSBindings
@LDLRegister(name = "character", group = "minegenshin", registry = "ldlib2:ui_element")
public class CharacterState extends UIElement {

  public Player player = null;
  public final StackBindUIElement characterIcon;
  public final BooleanDisplayBindUIElement selectedGroup = new BooleanDisplayBindUIElement();
  public final BooleanDisplayBindUIElement noSelectedGroup = new BooleanDisplayBindUIElement();
  public final UIElement selectedElement;
  public final UIElement noSelectedElement;
  public final UIElement selectedBackground;
  public final UIElement noSelectedBackground;
  public final Label characterName1;
  public final Label characterName2;
  public final ProgressBar characterHP;
  public final BurstIcon burstIcon;
  public PlayerCharactersAttachment charactersAttachment;
  public boolean isSelected;
  public int id;

  protected final Map<IDataProvider<PlayerCharactersAttachment>, ISubscription> characterDataSources =
          new LinkedHashMap<>();
  protected final Map<IDataProvider<Boolean>, ISubscription> selectedDataSources =
          new LinkedHashMap<>();
  protected final Map<IDataProvider<PGCharacter>, ISubscription> characterSources =
          new LinkedHashMap<>();
  private final AtomicReference<PGCharacter> character = new AtomicReference<>();

  public CharacterState() {
    super();
    this.lss("layout-direction", "rtl")
            .lss("width", 80)
            .lss("flex-direction", "row")
            .lss("gap-all", 0);
    this.characterIcon = new StackBindUIElement();
    this.burstIcon = new BurstIcon();
    this.characterName1 = new Label();
    this.characterName2 = new Label();
    this.noSelectedBackground = new UIElement();
    this.selectedBackground = new UIElement();

    this.addChildren(
            characterIcon.lss("height", 40).lss("width", 40).lss("right", 30),
            selectedElement = new UIElement()
                    .addChildren(characterName1.textStyle(text -> text.fontSize(9)))
                    .lss("width", 42)
                    .lss("right", 30)
                    .lss("top", 5)
                    .setDisplay(isSelected),
            noSelectedElement = new UIElement()
                    .addChildren(
                            characterName2.textStyle(text -> text.fontSize(9)),
                            characterHP = new ProgressBar())
                    .lss("width", 42)
                    .lss("right", 30)
                    .lss("top", 5)
                    .setDisplay(!isSelected));
  }

  public CharacterState(int id) {
    this();
    this.id = id;
  }

  public CharacterState bindCharacterDataSource(IDataProvider<PlayerCharactersAttachment> dataProvider) {
    if (dataProvider instanceof ITickable tickable) {
      UIEventListener tickableListener = e -> tickable.tick();
      addEventListener(UIEvents.TICK, tickableListener);
    }
    var subscription = dataProvider.registerListener(this::setCharactersAttachment, true);
    this.characterDataSources.put(dataProvider, subscription);
    return this;
  }

  public CharacterState bindSelectionDataSource(IDataProvider<Boolean> dataProvider) {
    if (dataProvider instanceof ITickable tickable) {
      UIEventListener tickableListener = e -> tickable.tick();
      addEventListener(UIEvents.TICK, tickableListener);
    }
    var subscription = dataProvider.registerListener(this::setSelectionState, true);
    this.selectedDataSources.put(dataProvider, subscription);
    return this;
  }

  public CharacterState bindCharacterSource(IDataProvider<PGCharacter> dataProvider) {
    if (dataProvider instanceof ITickable tickable) {
      UIEventListener tickableListener = e -> tickable.tick();
      addEventListener(UIEvents.TICK, tickableListener);
    }
    var subscription = dataProvider.registerListener(this::setCharacter, true);
    this.characterSources.put(dataProvider, subscription);
    return this;
  }

  public CharacterState unbindCharacterDataSource(IDataProvider<PlayerCharactersAttachment> dataProvider) {
    var removed = this.characterDataSources.remove(dataProvider);
    if (removed != null) removed.unsubscribe();
    return this;
  }

  public CharacterState unbindSelectionDataSource(IDataProvider<Boolean> dataProvider) {
    var removed = this.selectedDataSources.remove(dataProvider);
    if (removed != null) removed.unsubscribe();
    return this;
  }

  public CharacterState setCharactersAttachment(PlayerCharactersAttachment value) {
    if (value != this.charactersAttachment) {
      this.charactersAttachment = value;
      character.set(value.getPartyCharacter(id));
      applyCharacterData();
    }
    return this;
  }

  public CharacterState setSelectionState(@Nullable Boolean value) {
    this.isSelected = Boolean.TRUE.equals(value);
    if (this.isSelected) {
      selectedBackground.setDisplay(true);
      noSelectedBackground.setDisplay(false);
      selectedElement.setDisplay(true);
      noSelectedElement.setDisplay(false);
    } else {
      selectedBackground.setDisplay(false);
      noSelectedBackground.setDisplay(true);
      selectedElement.setDisplay(false);
      noSelectedElement.setDisplay(true);
    }
    markAsInternal();
    return this;
  }

  public CharacterState setCharacter(PGCharacter value) {
    if (value != this.character.get()) {
      this.character.set(value);
      if (this.charactersAttachment != null) {
        character.set(charactersAttachment.getPartyCharacter(id));
      }
    }
    characterName1.bindDataSource(SupplierDataSource.of(() -> {
      PGCharacter c = character.get();
      if (c == null) return Component.literal("");
      return c.getName();
    }));
    characterName2.bindDataSource(SupplierDataSource.of(() -> {
      PGCharacter c = character.get();
      if (c == null) return Component.literal("");
      return c.getName();
    }));
    characterIcon.bindDataSource(SupplierDataSource.of(character::get));
    characterHP.bindDataSource(SupplierDataSource.of(() -> {
      PGCharacter c = character.get();
      if (c == null) return 0.0f;
      double maxHP = c.getData().getAttributeTotalValue(ModAttributes.MAX_HP.value());
      return maxHP > 0 ? (float) (c.getData().getCurrentHP() / maxHP) : 0f;
    }));
    return this;
  }

  private void applyCharacterData() {
    PGCharacter c = character.get();
    if (c == null) {
      characterIcon.style(s -> s.background(null));
      characterName1.setText(Component.literal(""));
      characterName2.setText(Component.literal(""));
      return;
    }
    isSelected = charactersAttachment.getCurrentCharacterIndex() == id;
    characterHP.bindDataSource(SupplierDataSource.of(() -> {
      double maxHP = c.getData().getAttributeTotalValue(ModAttributes.MAX_HP.value());
      return maxHP > 0 ? (float) (c.getData().getCurrentHP() / maxHP) : 0f;
    }));
  }
}