package com.linweiyun.genshin.core.character;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * 一个角色自己的<b>配置页</b>（LDLib2 构建）。
 *
 * <h2>为什么是一个接口、而不是一个具体的 Screen 类</h2>
 * 页面的形态由角色自己决定（申鹤是「模型预览 + 腿部变体 + 倍率」，以后别的角色可能是
 * 别的控件），基类不该认识它们。所以：
 * <ul>
 *   <li>基类 {@link PGCharacter} 只持有一个 {@link ICharacterConfigUI} 槽位；</li>
 *   <li>子类在自己的<b>无参构造器</b>里 new 一个实现（照 {@code talent} 的写法）；</li>
 *   <li>打开时统一走 {@code ScreenNavigator.openCharacterConfigScreen(player)}，
 *       它把返回的 {@link ModularUI} 塞进 LDLib2 的 {@code ModularUIScreen}。</li>
 * </ul>
 *
 * <h2>为什么用 {@code ModularUI.of(ui, player)}</h2>
 * LDLib2 官方 agent 指南的判定：<b>只要 UI 需要 Player 数据或服务端状态，就必须走
 * 「基于 Menu 的 UI」，用 {@code ModularUI.of(ui, player)}</b>。
 * 本页要显示 / 改写这个玩家名下的角色数据（倍率、外观），所以按那条路走 ——
 * 项目里 {@code ScreenAscension} 已经是同一个用法，照着来。
 *
 * <h2>实现约定</h2>
 * <ul>
 *   <li><b>静态样式写进 {@code .lss} 样式表</b>，Java 这边只给元素 {@code setId}，
 *       需要随状态变化的（选中态、颜色）才用 {@code addClass}；</li>
 *   <li>{@link #title()} 用于窗口标题栏。</li>
 * </ul>
 */
public interface ICharacterConfigUI {

    /**
     * 构建这个角色的配置页。
     *
     * <p>只会在<b>客户端</b>、且玩家按下按键打开页面时调用一次
     * （每次打开都重新构建，不缓存控件树 —— 控件树里可能持有 Scene 这类
     * 需要释放的渲染资源）。
     *
     * @param player 打开页面的玩家
     * @param character 页面所属的角色（就是 {@code player} 当前出战的那一个）
     */
    ModularUI createConfigUI(Player player, PGCharacter character);

    /** 页面标题。 */
    Component title();
}
