package com.linweiyun.genshin.client.render.gui.screen;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.network.chat.Component;

/**
 * 角色配置页的<b>宿主 Screen</b>。
 *
 * <p>只做一件事：把 {@code ICharacterConfigUI} 建好的 {@link ModularUI} 挂到 LDLib2 自带的
 * {@link ModularUIScreen} 上。页面本身长什么样、有哪些控件，全在角色自己的
 * {@code ICharacterConfigUI} 实现里 —— 这样加新角色不用碰这个类。
 *
 * <p>为什么继承 {@code ModularUIScreen} 而不是照 {@code ScreenAscension} 自己写
 * {@code init()}：LDLib2 这个基类的 {@code init()} 已经把
 * {@code ModularUIClientAccess.setScreenAndInit} + {@code addRenderableWidget} +
 * 居中 + 初始聚焦全做了，{@code isPauseScreen()} 也已经是 false。
 * 自己再抄一遍只会多一份要跟着 LDLib2 版本改的代码。
 */
public class ScreenCharacterConfig extends ModularUIScreen {

    public ScreenCharacterConfig(ModularUI modularUI, Component title) {
        super(modularUI, title);
    }
}
