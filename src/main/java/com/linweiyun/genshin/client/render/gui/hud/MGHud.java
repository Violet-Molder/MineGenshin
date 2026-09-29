package com.linweiyun.genshin.client.render.gui.hud;

import com.google.common.base.Suppliers;
import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.client.render.gui.component.BooleanDisplayBindUIElement;
import com.linweiyun.genshin.client.render.gui.component.HPProgressBar;
import com.linweiyun.genshin.client.render.gui.component.SkillProgressBar;
import com.linweiyun.genshin.client.render.gui.component.StackBindUIElement;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.core.character.PGCharacterData;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.status.StatusAccessor;
import com.linweiyun.genshin.core.system.about.ElementalAttachmentInstance;
import com.linweiyun.genshin.core.world.TeyvatWorldInvasion;
import com.linweiyun.genshin.client.render.gui.component.CharacterBuffIcon;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import dev.vfyjxf.taffy.style.TaffyDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.slf4j.Logger;

@EventBusSubscriber(value = Dist.CLIENT)
public class MGHud {
    public static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);
    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        var hudUICache =
                Suppliers.memoize(
                        () -> {
                            var stylesheet =
                                    StylesheetManager.INSTANCE.getStylesheetSafe(
                                            Minegenshin.id("lss/hud/character_party.lss"));
                            var ui = UI.of(buildCharacterPartyHud(), stylesheet);
                            return ModularUI.of(ui);
                        });
        event.registerAboveAll(Minegenshin.id("simple_hud"), (MyModularHudLayer) hudUICache::get);
        // 原神模式下屏幕正下方那一条：角色经验条 + 居中饱食度（原版贴图，走原生 GUI 层）
        event.registerAbove(
                VanillaGuiLayers.HOTBAR,
                Minegenshin.id("genshin_bottom_hud"),
                (GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) ->
                        GenshinBottomHud.render(graphics));
        HudLayerOverride.onRegisterGuiLayers(event);
    }

    private static UIElement buildCharacterPartyHud() {
        var root =
                new BooleanDisplayBindUIElement()
                        .bindDataSource(
                                SupplierDataSource.of(
                                        () -> {
                                            var player = Minecraft.getInstance().player;
                                            if (player == null) return false;
                                            if (!TeyvatWorldInvasion.isClientInvaded()) return false;
                                            boolean isGenShin = player.getData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT);
                                            return isGenShin;
                                        }))
                        .setId("root")
                        .layout(l -> l.widthPercent(100).heightPercent(100))
                        .lss("position", "absolute");

        var characterList = new UIElement();
        var currentContent =
                (BooleanDisplayBindUIElement)
                        new BooleanDisplayBindUIElement().setId("current-content").addClass("__unselected__");
        var characterLevel = new Label();
        var currentCharacterHP = new HPProgressBar();
        var characterSkillIcon = new SkillProgressBar(40, 40);
        var characterBurstIcon = new SkillProgressBar(40, 40);
        characterList.setId("character_list");

        for (int i = 0; i < 4; i++) {
            int slotIndex = i;
            var characterSate =
                    (BooleanDisplayBindUIElement)
                            new BooleanDisplayBindUIElement().setId("character_state").addClass("__unselected__");
            var characterIcon = (StackBindUIElement) new StackBindUIElement().setId("character_icon");
            var iconLeftUIElement = new UIElement().setId("icon-left");
            var characterName = (Label) new Label().setId("character_name");
            var selectedGroup =
                    (BooleanDisplayBindUIElement)
                            new BooleanDisplayBindUIElement()
                                    .setId("selected_group")
                                    .addClass("__unselected__")
                                    .addClass("display");
            var notSelectedGroup =
                    (BooleanDisplayBindUIElement)
                            new BooleanDisplayBindUIElement()
                                    .setId("not_selected_group")
                                    .addClass("__unselected__")
                                    .addClass("display");
            var characterHP = (HPProgressBar) new HPProgressBar().setId("character_party_hp");

            characterSate
                    .bindDataSource(
                            SupplierDataSource.of(
                                    () -> {
                                        var player = Minecraft.getInstance().player;
                                        if (player != null) {
                                            var attachment =
                                                    player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
                                            return attachment.getPartyCharacter(slotIndex) != null;
                                        }
                                        return false;
                                    }))
                    .layout(
                            layout -> {
                                layout.flexDirection(FlexDirection.ROW);
                                layout.gapAll(6);
                                layout.display(TaffyDisplay.FLEX);
                            });

            characterIcon.bindDataSource(
                    SupplierDataSource.of(
                            () -> {
                                var player = Minecraft.getInstance().player;
                                if (player != null) {
                                    var attachment =
                                            player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
                                    return attachment.getPartyCharacter(slotIndex);
                                }
                                return null;
                            }));

            selectedGroup.bindDataSource(
                    SupplierDataSource.of(
                            () -> {
                                var player = Minecraft.getInstance().player;
                                if (player != null) {
                                    var attachment =
                                            player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
                                    return attachment.getCurrentCharacterIndex() == slotIndex;
                                }
                                return false;
                            }));

            notSelectedGroup.bindDataSource(SupplierDataSource.of(() -> !selectedGroup.getValue()));

            characterName.bindDataSource(
                    SupplierDataSource.of(
                            () -> {
                                PGCharacter character = characterIcon.getValue();
                                if (character == null) return Component.literal("");
                                return character.getName();
                            }));

            characterHP
                    .bindDataSource(
                            SupplierDataSource.of(
                                    () -> {
                                        PGCharacter character = characterIcon.getValue();
                                        if (character == null) return 1f;
                                        PGCharacterData data = character.getData();
                                        if (data != null) {
                                            return (float) (data.getCurrentHP() / data.getAttributeTotalValue(ModAttributes.MAX_HP.value()));
                                        }
                                        return 1f;
                                    }))
                    .label
                    .setText("")
                    .setId("character_party_hp");
            characterHP.layout(
                    layout -> {
                        layout.width(80);
                        layout.height(4);
                        layout.aspectRatio(20);
                    });
            characterHP
                    .barIcon
                    .style(
                            s ->
                                    s.background(
                                            SpriteTexture.of(
                                                    Minegenshin.id("gui/short_character_hp_bar_green.png"))))
                    .layout(
                            l -> {
                                l.width(80);
                                l.aspectRatio(20);
                            });
            characterHP.barContainer(
                    c ->
                            c.style(
                                    s ->
                                            s.background(
                                                    SpriteTexture.of(
                                                            Minegenshin.id("gui/short_character_hp_green.png")))));

            characterList.addChild(
                    characterSate.addChildren(
                            characterIcon,
                            iconLeftUIElement.addChildren(
                                    characterName,
                                    selectedGroup.addChildren(),
                                    notSelectedGroup.addChildren(characterHP))));
        }

        characterSkillIcon
                .bindDataSource(
                        SupplierDataSource.of(
                                () -> {
                                    var player = Minecraft.getInstance().player;
                                    if (player == null) return 0f;
                                    var attachment =
                                            player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
                                    var character = attachment.getCurrentCharacter();
                                    if (character != null) {
                                        return character.getSkillDisplayCooldown()
                                                / character.getSkillDisplayMaxCooldown();
                                    }
                                    return 0f;
                                }))
                .layout(
                        layout -> {
                            layout.width(40);
                            layout.height(40);
                        })
                .setId("character_skill_icon");
        characterSkillIcon.bindCharacterSource(
                SupplierDataSource.of(
                        () -> {
                            var player = Minecraft.getInstance().player;
                            if (player != null) {
                                var attachment =
                                        player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
                                return attachment.getCurrentCharacter();
                            }
                            return null;
                        }),
                0);
        characterSkillIcon.label.bindDataSource(
                SupplierDataSource.of(
                        () -> {
                            characterSkillIcon.label.textStyle(style -> style.fontSize(14));
                            var player = Minecraft.getInstance().player;
                            if (player == null) return Component.literal("");
                            var attachment =
                                    player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
                            var character = attachment.getCurrentCharacter();
                            if (character != null) {
                                float currentSkillCD =
                                        Math.round(character.getSkillDisplayCooldown() / 20 * 10f) / 10f;
                                if (currentSkillCD <= 0) return Component.literal("");
                                return Component.literal(String.valueOf(currentSkillCD));
                            }
                            return Component.literal("");
                        }));

        characterBurstIcon
                .bindDataSource(
                        SupplierDataSource.of(
                                () -> {
                                    var player = Minecraft.getInstance().player;
                                    if (player == null) return 0f;
                                    var attachment =
                                            player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
                                    var character = attachment.getCurrentCharacter();
                                    if (character != null) {
                                        return character.getBurstDisplayCooldown()
                                                / character.getBurstDisplayMaxCooldown();
                                    }
                                    return 0f;
                                }))
                .layout(
                        layout -> {
                            layout.width(40);
                            layout.height(40);
                        })
                .setId("character_burst_icon");
        characterBurstIcon.bindCharacterSource(
                SupplierDataSource.of(
                        () -> {
                            var player = Minecraft.getInstance().player;
                            if (player != null) {
                                var attachment =
                                        player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
                                return attachment.getCurrentCharacter();
                            }
                            return null;
                        }),
                0);
        characterBurstIcon.label.bindDataSource(
                SupplierDataSource.of(
                        () -> {
                            characterBurstIcon.label.textStyle(style -> style.fontSize(14));
                            var player = Minecraft.getInstance().player;
                            if (player == null) return Component.literal("");
                            var attachment =
                                    player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
                            var character = attachment.getCurrentCharacter();
                            if (character != null) {
                                float currentBurstCD =
                                        Math.round(character.getBurstDisplayCooldown() / 20 * 10f) / 10f;
                                if (currentBurstCD <= 0) return Component.literal("");
                                return Component.literal(String.valueOf(currentBurstCD));
                            }
                            return Component.literal("");
                        }));

        currentCharacterHP
                .bindDataSource(
                        SupplierDataSource.of(
                                () -> {
                                    var player = Minecraft.getInstance().player;
                                    if (player == null) return 0f;
                                    var attachment =
                                            player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
                                    var character = attachment.getCurrentCharacter();
                                    if (character != null) {
                                        var data = character.getData();
                                        return (float) (data.getCurrentHP() / data.getAttributeTotalValue(ModAttributes.MAX_HP.value()));
                                    }
                                    return 0f;
                                }))
                .setId("current_character_hp")
                .layout(
                        l -> {
                            l.width(210);
                            l.heightAuto();
                            l.aspectRatio(35);
                        });
        currentCharacterHP
                .barIcon
                .style(
                        s ->
                                s.background(
                                        SpriteTexture.of(
                                                Minegenshin.id("gui/long_character_hp_bar_green.png"))))
                .layout(
                        l -> {
                            l.width(210);
                            l.aspectRatio(35);
                        });
        // 拖尾那条白条必须和填充同款形状（长条配长白条），否则两端楔形的斜边对不上
        currentCharacterHP.trailTexture(Minegenshin.id("gui/long_character_hp_bar_white.png"));
        currentCharacterHP.barContainer(
                c ->
                        c.style(
                                s ->
                                        s.background(
                                                SpriteTexture.of(
                                                        Minegenshin.id("gui/long_character_hp_green.png")))));
        currentCharacterHP.label.bindDataSource(
                SupplierDataSource.of(
                        () -> {
                            var player = Minecraft.getInstance().player;
                            if (player == null) return Component.empty();
                            var attachment =
                                    player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
                            var character = attachment.getCurrentCharacter();
                            if (character != null) {
                                var data = character.getData();
                                if (data != null) {
                                    return Component.literal(
                                            Math.round(data.getCurrentHP())
                                                    + "/"
                                                    + Math.round(data.getAttributeTotalValue(ModAttributes.MAX_HP.value())));
                                }

                            }
                            return Component.empty();
                        }));

        characterLevel.bindDataSource(
                        SupplierDataSource.of(
                                () -> {
                                    var player = Minecraft.getInstance().player;
                                    if (player == null) return Component.empty();
                                    var attachment =
                                            player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
                                    var character = attachment.getCurrentCharacter();
                                    if (character != null) {
                                        var data = character.getData();
                                        if (data != null) {
                                            return Component.literal("Lv." + data.getLevel());
                                        }
                                    }
                                    return Component.empty();
                                }))
                .setId("character-level");

        // 属性调试

        currentContent.bindDataSource(
                SupplierDataSource.of(
                        () -> {
                            var player = Minecraft.getInstance().player;
                            if (player != null) {
                                var attachment =
                                        player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
                                return attachment.getCurrentCharacter() != null;
                            }
                            return false;
                        }));

        var energy =
                new Label()
                        .bindDataSource(
                                SupplierDataSource.of(
                                        () -> {
                                            var player = Minecraft.getInstance().player;
                                            if (player == null) return Component.literal("");
                                            var attachment =
                                                    player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
                                            var character = attachment.getCurrentCharacter();
                                            if (character != null) {
                                                var data = character.getData();
                                                if (data != null) {
                                                    return Component.literal(
                                                            Math.round(data.getCurrentObtainingEnergy() * 10f) / 10f
                                                                    + "/"
                                                                    + character.getMaxObtainingEnergy());
                                                }
                                            }
                                            return Component.literal("");
                                        }));

        var characterBuffBar = buildCharacterBuffBar();

        root.addChildren(
                characterList,
                currentContent
                        .addChildren(
                                characterLevel,
                                buildLeftBuffBarReserve(),
                                characterBuffBar,
                                currentCharacterHP,
                                new UIElement()
                                        .setId("skill-content")
                                        .addChildren(characterSkillIcon, characterBurstIcon, energy))
                        .layout(
                                l -> {
                                    l.widthPercent(100);
                                    l.heightPercent(100);
                                }));

        return root;
    }

    /** 出战角色的元素附着图标栏，最多显示这么多格。 */
    private static final int CHARACTER_BUFF_SLOTS = 5;
    /** 每格 buff 图标的边长 */
    private static final int CHARACTER_BUFF_ICON_SIZE = 18;
    /** 相邻两格 buff 图标之间的间距 */
    private static final int CHARACTER_BUFF_ICON_GAP = 2;
    /**
     * 元素附着栏离屏幕中线的距离。
     *
     * <p>底栏中间那条饱食度是整行居中、宽 81（中线 ±40.5），这里再让开 4.5 像素，
     * 元素附着就正好排在它右边；中线左边按同样的距离留一条空 buff 栏。
     */
    private static final float CHARACTER_BUFF_BAR_CENTER_OFFSET = 45.0f;
    /**
     * buff 栏离屏幕下沿的距离。
     *
     * <p>取 39 是跟饱食度那一行（39~48）底边对齐：18 高的图标往上长到 53，正好不压到
     * 下面那条经验条（32~37），也不会跟快捷栏（0~22）打架。
     */
    private static final float CHARACTER_BUFF_BAR_BOTTOM = 39.0f;

    /**
     * 出战角色的「元素附着」图标栏。
     *
     * <p>每格是一个 {@link CharacterBuffIcon}，数据源是 {@link #currentCharacterBuffIcon(int)}：
     * 返回贴图路径就显示，返回空串就那一格不显示。
     * 附着是挂在上场角色的 {@code StatusContainer} 上（不是玩家本体），所以<b>切人即换图标</b>。
     *
     * <p>位置：屏幕中线右边 45 像素起、离下沿 39 像素，也就是底栏饱食度那一行的右侧；
     * 左边对称的一条空栏见 {@link #buildLeftBuffBarReserve()}。
     */
    private static UIElement buildCharacterBuffBar() {
        var bar = new UIElement().setId("character_buff_bar");
        for (int i = 0; i < CHARACTER_BUFF_SLOTS; i++) {
            int slot = i;
            var icon = new CharacterBuffIcon()
                    .bindDataSource(SupplierDataSource.of(() -> currentCharacterBuffIcon(slot)));
            icon.layout(l -> {
                l.width(CHARACTER_BUFF_ICON_SIZE);
                l.height(CHARACTER_BUFF_ICON_SIZE);
            });
            bar.addChildren(icon);
        }
        // 横着排（ldlib 的默认方向是竖排，不写这一行图标会一列摞起来）
        bar.layout(l -> {
            l.positionType(TaffyPosition.ABSOLUTE);
            l.flexDirection(FlexDirection.ROW);
            // #root 是 layout-direction: rtl，方向是继承的：不显式写回 ltr 的话，
            // row 会从右往左填，图标就全跑到这条栏的右端（也就是技能图标那一侧）去。
            l.direction(TaffyDirection.LTR);
            l.gapColumn(CHARACTER_BUFF_ICON_GAP);
            l.height(CHARACTER_BUFF_ICON_SIZE);
            l.leftPercent(50.0f);
            l.marginLeft(CHARACTER_BUFF_BAR_CENTER_OFFSET);
            l.bottom(CHARACTER_BUFF_BAR_BOTTOM);
        });
        return bar;
    }

    /**
     * 屏幕中线左边那条<b>留空的</b> buff 栏。
     *
     * <p>现在什么都不画，只按 {@link #buildCharacterBuffBar()} 的格数把位置占住：
     * 以后要显示别的 buff（食物、药剂、护盾这些）就有地方摆，也不会把元素附着挤到别处去。
     */
    private static UIElement buildLeftBuffBarReserve() {
        var bar = new UIElement().setId("buff_bar_left");
        bar.layout(l -> {
            l.positionType(TaffyPosition.ABSOLUTE);
            l.flexDirection(FlexDirection.ROW);
            l.direction(TaffyDirection.LTR);
            l.gapColumn(CHARACTER_BUFF_ICON_GAP);
            l.height(CHARACTER_BUFF_ICON_SIZE);
            l.width(CHARACTER_BUFF_SLOTS * CHARACTER_BUFF_ICON_SIZE
                    + (CHARACTER_BUFF_SLOTS - 1) * CHARACTER_BUFF_ICON_GAP);
            l.rightPercent(50.0f);
            l.marginRight(CHARACTER_BUFF_BAR_CENTER_OFFSET);
            l.bottom(CHARACTER_BUFF_BAR_BOTTOM);
        });
        return bar;
    }

    /**
     * 第 {@code slot} 格该显示哪个元素的图标。
     *
     * <p>遍历上场角色的附着，跳过已结束的、单位量已归零的、以及物理（{@link ModElements#FYSIKOS}，
     * 它不是真正的元素附着），剩下的按顺序填格子。没有就返回空串。
     */
    private static String currentCharacterBuffIcon(int slot) {
        var player = Minecraft.getInstance().player;
        if (player == null) return "";
        var attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        var character = attachment == null ? null : attachment.getCurrentCharacter();
        if (character == null) return "";
        var container = StatusAccessor.of(character.getData());
        if (container == null) return "";

        int seen = 0;
        for (var instance : container.getAll()) {
            if (!instance.isFinished() && instance instanceof ElementalAttachmentInstance attached) {
                var element = attached.getElement();
                if (element != null
                        && attached.getUnit() > 0f
                        && element != ModElements.FYSIKOS.get()
                        && !element.isEffectCarrier()
                        && seen++ == slot) {
                    return "minegenshin:icon/elemental/" + element.getId() + ".png";
                }
            }
        }
        return "";
    }
}
