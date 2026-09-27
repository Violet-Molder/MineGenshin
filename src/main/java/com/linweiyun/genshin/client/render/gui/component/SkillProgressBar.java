package com.linweiyun.genshin.client.render.gui.component;

import com.linweiyun.genshin.core.character.PGCharacterData;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataProvider;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.ITickable;
import com.lowdragmc.lowdraglib2.integration.kjs.KJSBindings;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.lowdragmc.lowdraglib2.syncdata.ISubscription;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.resources.Identifier;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.LinkedHashMap;
import java.util.Map;

@ParametersAreNonnullByDefault
@KJSBindings
@LDLRegister(name = "skill-progress-bar", group = "minegenshin", registry = "ldlib2:ui_element")
public class SkillProgressBar extends ProgressBar {
    private PGCharacter character;
    public final UIElement barIcon;
    protected final Map<IDataProvider<PGCharacter>, ISubscription> characterSources =
            new LinkedHashMap<>();

    private static final int SKILL_ICON_SIZE = 40;

    public SkillProgressBar() {
        super();
        this.barContainer.layout(layout -> {
            layout.paddingAll(0);
            layout.positionType(TaffyPosition.ABSOLUTE);
        });
        bar.layout(layout -> {
                    layout.positionType(TaffyPosition.ABSOLUTE);
                    layout.width(SKILL_ICON_SIZE);
                    layout.height(SKILL_ICON_SIZE);
                    layout.positionType(TaffyPosition.ABSOLUTE);
                })
                .style(s -> s.opacity(1));
        bar.addChild(barIcon = new UIElement().layout(layout -> {
            layout.width(SKILL_ICON_SIZE);
            layout.height(SKILL_ICON_SIZE);
            layout.positionType(TaffyPosition.ABSOLUTE);
        }));
        this.label.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE));
    }

    public SkillProgressBar(int width, int height) {
        super();
        this.barContainer.layout(layout -> layout.paddingAll(0));
        bar.layout(layout -> {
                    layout.positionType(TaffyPosition.ABSOLUTE);
                    layout.width(width);
                    layout.height(height);
                    layout.positionType(TaffyPosition.ABSOLUTE);
                })
                .style(s -> s.background(SpriteTexture.of(
                        Identifier.fromNamespaceAndPath("minegenshin", "gui/empty.png"))));
        bar.addChild(barIcon = new UIElement().layout(layout -> {
            layout.width(width);
            layout.height(height);
            layout.positionType(TaffyPosition.ABSOLUTE);
        }));
        this.label.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE));
    }

    public SkillProgressBar bindCharacterSource(
            IDataProvider<PGCharacter> characterProvider, int flagProvider) {
        if (characterProvider instanceof ITickable tickable) {
            UIEventListener tickableListener = e -> tickable.tick();
            addEventListener(UIEvents.TICK, tickableListener);
        }
        ISubscription subscription = null;
        if (flagProvider == 0) {
            subscription = characterProvider.registerListener(this::setCharacterSkill, true);
        } else if (flagProvider == 1) {
            subscription = characterProvider.registerListener(this::setCharacterBurst, true);
        }
        if (subscription != null) {
            this.characterSources.put(characterProvider, subscription);
        }
        return this;
    }

    private void setCharacterBurst(PGCharacter character) {
        this.character = character;
        if (character == null) return;
        String textureId = character.getTextureId();
        this.barContainer.style(s -> s.background(
                SpriteTexture.of(Identifier.fromNamespaceAndPath("minegenshin",
                        "character/" + textureId + "/textures/burst.png"))));

        this.barIcon.style(s -> s.background(
                SpriteTexture.of(Identifier.fromNamespaceAndPath("minegenshin", "gui/skill_cd.png"))));
    }

    private void setCharacterSkill(PGCharacter character) {
        this.character = character;
        if (character == null) return;
        String textureId = character.getTextureId();
        this.barContainer.style(s -> s.background(
                SpriteTexture.of(Identifier.fromNamespaceAndPath("minegenshin",
                        "character/" + textureId + "/textures/skill.png"))));
        this.barIcon.style(s -> s.background(
                SpriteTexture.of(Identifier.fromNamespaceAndPath("minegenshin", "gui/skill_cd.png"))));
    }

    public SkillProgressBar unbindCharacterSource(IDataProvider<PGCharacterData> dataProvider) {
        var removed = this.characterSources.remove(dataProvider);
        if (removed != null) removed.unsubscribe();
        return this;
    }
}
