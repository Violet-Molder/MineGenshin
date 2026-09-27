package com.linweiyun.genshin.client.render.gui.screen;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.network.chat.Component;

/**
 * 角色装备页（按键 U）的<b>宿主 Screen</b>。
 *
 * <p>和 {@link ScreenCharacterConfig} 同一个写法：只把
 * {@link CharacterEquipUI} 建好的 {@link ModularUI} 挂到 LDLib2 自带的
 * {@link ModularUIScreen} 上。页面长什么样、有哪些控件、怎么刷新全在新页里 ——
 * 这个类只多管一件事：关页面时把 {@link CharacterEquipUI} 里那份
 * {@code OPEN_STATE} 松开（服务端推数据回来时会按它重画，页面关了就不该再被写）。
 */
public class ScreenCharacterEquip extends ModularUIScreen {

    public ScreenCharacterEquip(ModularUI modularUI, Component title) {
        super(modularUI, title);
    }

    @Override
    public void removed() {
        super.removed();
        CharacterEquipUI.clearOpenState();
    }
}
