package com.linweiyun.genshin.client.render.gui.hud;

import com.google.common.base.Suppliers;
import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.lowdragmc.lowdraglib2.gui.hud.ModularHudLayer;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.slf4j.Logger;

@EventBusSubscriber(value = Dist.CLIENT)
public class DebugInfoScreen {
    private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);
    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
//        var hudUICache =
//                Suppliers.memoize(
//                        () -> ModularUI.of(UI.of(
//                                buildDebugInfoHud()
//                        )));
//        event.registerAboveAll(Minegenshin.id("debug_hud"), (ModularHudLayer) hudUICache::get);
    }

//    private static UIElement buildDebugInfoHud() {
//        var root = new UIElement().setId("root")
//                .layout(l -> l.widthPercent(100).heightPercent(100))
//                .lss("position", "absolute");
//
//        var attributeDebug = new Label();
//        attributeDebug.bindDataSource(
//                        SupplierDataSource.of(
//                                () -> {
//                                    var player = Minecraft.getInstance().player;
//                                    if (player == null) return Component.empty();
//                                    var attachment =
//                                            player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
//                                    var character = attachment.getCurrentCharacter();
//                                    if (character != null) {
//                                        var data = character.getData();
//                                        var ATK = data.getAttribute(ModAttributes.ATK.get());
//                                        var DEF = data.getAttribute(ModAttributes.DEF.value());
//                                        var MAXHP = data.getAttribute(ModAttributes.MAX_HP.value());
//                                        return Component.literal(
//                                                "当前攻击力：" + ATK.getTotalValue() + "当前基础攻击力：" + ATK.getBaseValue() + "当前额外攻击力百分比：" + ATK.getTotalPercentModifierDisplay() + "%"
//                                                        + "\n当前防御力：" + DEF.getTotalValue() + "当前基础防御力：" + DEF.getBaseValue() + "当前额外防御力百分比：" + DEF.getTotalPercentModifierDisplay() + "%"
//                                                        + "\n当前生命值：" + MAXHP.getTotalValue() + "当前基础生命值：" + MAXHP.getBaseValue() + "当前额外生命值百分比：" + MAXHP.getTotalPercentModifierDisplay() + "%"
//                                                        + "\n当前经验值：" + data.getCurrentExp() + " / " + data.getMaxExp()
//                                        );
//                                    }
//                                    return Component.empty();
//        })).setId("character-attribute");;
//        root.addChild(attributeDebug);
//        return root;
//    }

}