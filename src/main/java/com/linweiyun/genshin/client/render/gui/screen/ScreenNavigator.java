package com.linweiyun.genshin.client.render.gui.screen;

import com.linweiyun.genshin.core.character.CharacterHelper;
import com.linweiyun.genshin.core.character.ICharacterConfigUI;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.linweiyun.genshin.client.render.gui.screen.ScreenArtifactEquip;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class ScreenNavigator {
  public static void openCharacterSelectScreen(Player player, int index) {
    var modularUI = ScreenCharacterSelect.createModularUI(player, index);
    Minecraft.getInstance().setScreenAndShow(new ScreenCharacterSelect(modularUI));
  }

  public static void openCharacterPartyScreen(Player player) {
    var modularUI = ScreenCharacterParty.createModularUI(player);
    Minecraft.getInstance().setScreenAndShow(new ScreenCharacterParty(modularUI));
  }
  public static void openCharacterInfoScreen(Player player) {
    NetworkManager.openCharacterInfoScreenToServer();
  }

    public static void openArtifactEquipScreen(Player player, int slotIndex) {
    // 2026-09-26：旧的 ScreenArtifactEquip（U）已由角色装备页取代 ——
    // 新页把「切角色 / 详情 / 属性 / 武器 / 圣遗物 / 命之座 / 天赋 / 资料」收在一屏里，
    // 中间还渲染角色本体（无专属模型就渲染原版玩家 + 皮肤）。
    // 旧的类留着不动（它有自己的静态 OPEN_STATE 与背包回调），只是不再从这个入口打开。
    var modularUI = CharacterEquipUI.createModularUI(player);
    Minecraft.getInstance().setScreenAndShow(new ScreenCharacterEquip(modularUI,
            Component.translatable("gui.minegenshin.character_equip.title")));
  }
  public static void openBackpackScreen(Player player) {
    NetworkManager.openBackpackUIToServer();
  }

  public static void openAscensionScreen(Player player) {
    Minecraft.getInstance().setScreenAndShow(new ScreenAscension(player));
  }

  /**
   * 角色配置页（按键 N）。
   *
   * <p>页面由<b>角色自己</b>提供（{@link PGCharacter#getConfigUI()}），这个类不认识任何具体页面；
   * 没提供页面的角色只发一条提示，不弹空窗 —— 「按了没反应」比「弹个空框」更难排查。
   */
  public static void openCharacterConfigScreen(Player player) {
    PGCharacter character = CharacterHelper.getCurrentCharacter(player);
    if (character == null) {
      player.sendSystemMessage(Component.translatable("gui.minegenshin.character_config.no_character"));
      return;
    }

    ICharacterConfigUI configUI = character.getConfigUI();
    if (configUI == null) {
      player.sendSystemMessage(Component.translatable("gui.minegenshin.character_config.unavailable",
              character.getName()));
      return;
    }

    ModularUI modularUI = configUI.createConfigUI(player, character);
    if (modularUI == null) {
      return;
    }
    Minecraft.getInstance().setScreenAndShow(new ScreenCharacterConfig(modularUI, configUI.title()));
  }
}
