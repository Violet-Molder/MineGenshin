// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.client.render.gui.screen;

import com.linweiyun.genshin.core.character.util.CharacterHelper;
import com.linweiyun.genshin.core.character.util.config.ICharacterConfigUI;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.util.config.CharacterConfigUI;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class ScreenNavigator {
   public static void openCharacterSelectScreen(Player player, int index) {
      ModularUI modularUI = ScreenCharacterSelect.createModularUI(player, index);
      Minecraft.getInstance().setScreenAndShow(new ScreenCharacterSelect(modularUI));
   }

   public static void openCharacterPartyScreen(Player player) {
      ModularUI modularUI = ScreenCharacterParty.createModularUI(player);
      Minecraft.getInstance().setScreenAndShow(new ScreenCharacterParty(modularUI));
   }

   public static void openCharacterInfoScreen(Player player) {
      NetworkManager.openCharacterInfoScreenToServer();
   }

   public static void openArtifactEquipScreen(Player player, int slotIndex) {
      ModularUI modularUI = CharacterEquipUI.createModularUI(player);
      Minecraft.getInstance().setScreenAndShow(new ScreenCharacterEquip(modularUI, Component.translatable("gui.minegenshin.character_equip.title")));
   }

   public static void openBackpackScreen(Player player) {
      NetworkManager.openBackpackUIToServer();
   }

   public static void openAscensionScreen(Player player) {
      Minecraft.getInstance().setScreenAndShow(new ScreenAscension(player));
   }

   public static void openCharacterConfigScreen(Player player) {
      PGCharacter character = CharacterHelper.getCurrentCharacter(player);
      if (character == null) {
         player.sendSystemMessage(Component.translatable("gui.minegenshin.character_config.no_character"));
      } else {
         ICharacterConfigUI own = character.getConfigUI();
         ICharacterConfigUI configUI = own != null ? own : CharacterConfigUI.INSTANCE;
         ModularUI modularUI = configUI.createConfigUI(player, character);
         if (modularUI != null) {
            Minecraft.getInstance().setScreenAndShow(new ScreenCharacterConfig(modularUI, configUI.title()));
         }
      }
   }
}
