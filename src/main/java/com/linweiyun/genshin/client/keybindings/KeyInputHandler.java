// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.client.keybindings;

import com.linweiyun.genshin.client.combat.state.ActionStateMachine;
import com.linweiyun.genshin.client.combat.PlungeAttack;
import com.linweiyun.genshin.client.combat.GenshinFlightController;
import com.linweiyun.genshin.client.render.gui.screen.ScreenNavigator;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.linweiyun.genshin.core.system.combat.flight.GenshinFlight;
import com.linweiyun.genshin.core.world.TeyvatWorldInvasion;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;

@EventBusSubscriber(Dist.CLIENT)
public class KeyInputHandler {
   private static boolean wasRKeyDown = false;
   private static boolean wasGKeyDown = false;
   private static boolean wasVKeyDown = false;
   private static boolean wasOKeyDown = false;
   private static boolean wasCharInfoKeyDown = false;
   private static boolean wasArtifactKeyDown = false;
   private static boolean wasArtifactKey2Down = false;
   private static boolean wasConfigKeyDown = false;
   private static boolean wasWishKeyDown = false;

   @SubscribeEvent
   public static void onKeyInput(Post event) {
      Minecraft mc = Minecraft.getInstance();
      Player player = mc.player;
      if (player != null) {
         if (TeyvatWorldInvasion.isClientInvaded()) {
            boolean isInGenshinMode = isInGenshinMode(player);
            // 下落攻击期间「什么键都不接」（用户口径）：切角色 / 切原神模式 / 开界面一律无效化。
            // 只拦动作、不拦边沿检测 —— wasXxxDown 照常更新，所以松手之后也不会补触发一下。
            boolean plunging = PlungeAttack.isActive();
            PlayerCharactersAttachment charactersAttachment = (PlayerCharactersAttachment)player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            PGCharacter character = charactersAttachment == null ? null : charactersAttachment.getCurrentCharacter();
            boolean isWishDown = ((KeyMapping)KeyMappingRegistry.WISH_KEY.get()).isDown();
            if (isWishDown && !wasWishKeyDown && !plunging) {
               NetworkManager.wishEventToServer();
            }

            wasWishKeyDown = isWishDown;
            boolean isGDown = ((KeyMapping)KeyMappingRegistry.G_KEY.get()).isDown();
            if (isGDown && !wasGKeyDown && !plunging) {
               NetworkManager.setGenshinModeToServer(!isInGenshinMode);
            }

            wasGKeyDown = isGDown;
            boolean isVDown = ((KeyMapping)KeyMappingRegistry.V_KEY.get()).isDown();
            // 切角色：下落攻击 / 飞行（含起飞前摇）期间一律无效化。
            // 判据和服务端那条一样（GenshinFlight.isFlying）—— 只拦客户端不改本地索引，服务端却拒的话两边会不同步
            boolean lockSwitching = plunging
                    || GenshinFlightController.isWindingUp()
                    || GenshinFlight.isFlying(player);
            if (isVDown && !wasVKeyDown && isInGenshinMode && !lockSwitching) {
               switchToNextAvailableCharacter(player, charactersAttachment);
            }

            wasVKeyDown = isVDown;
            boolean isODown = ((KeyMapping)KeyMappingRegistry.O_KEY.get()).isDown();
            if (isODown && !wasOKeyDown && !plunging) {
               ScreenNavigator.openCharacterPartyScreen(player);
            }

            wasOKeyDown = isODown;
            boolean isCharInfoDown = ((KeyMapping)KeyMappingRegistry.CHARACTER_INFO_SCREEN_KEY.get()).isDown();
            if (isCharInfoDown && !wasCharInfoKeyDown && !plunging) {
               ScreenNavigator.openArtifactEquipScreen(player, -1);
            }

            wasCharInfoKeyDown = isCharInfoDown;
            boolean isArtifactDown = ((KeyMapping)KeyMappingRegistry.ARTIFACT_EQUIP_SCREEN_KEY.get()).isDown();
            if (isArtifactDown && !wasArtifactKeyDown && !plunging) {
               ScreenNavigator.openBackpackScreen(player);
            }

            wasArtifactKeyDown = isArtifactDown;
            boolean isArtifact2Down = ((KeyMapping)KeyMappingRegistry.ARTIFACT_EQUIP_SCREEN_KEY_2.get()).isDown();
            if (isArtifact2Down && !wasArtifactKey2Down && !plunging) {
               ScreenNavigator.openAscensionScreen(player);
            }

            wasArtifactKey2Down = isArtifact2Down;
            boolean isConfigDown = ((KeyMapping)KeyMappingRegistry.CONFIG_SCREEN_KEY.get()).isDown();
            if (isConfigDown && !wasConfigKeyDown && !plunging) {
               ScreenNavigator.openCharacterConfigScreen(player);
            }

            wasConfigKeyDown = isConfigDown;
            if (character == null) {
               ActionStateMachine.isAttackButtonDown = false;
               ActionStateMachine.releaseSkill(player);
            }
         }
      }
   }

   private static boolean isInGenshinMode(Player player) {
      return player.hasData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT) && (Boolean)player.getData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT);
   }

   private static void switchToNextAvailableCharacter(Player player, PlayerCharactersAttachment attachment) {
      if (attachment != null) {
         ActionStateMachine.resetToDefault();
         ActionStateMachine.comboStage = 1;
         int currentIndex = attachment.getCurrentCharacterIndex();

         for (int i = 1; i <= 4; i++) {
            int nextIndex = (currentIndex + i) % 4;
            PGCharacter character = attachment.getPartyCharacter(nextIndex);
            if (character != null && character.getData().getCurrentHP() > 0.0) {
               attachment.setCurrentCharacterIndex(nextIndex);
               NetworkManager.setCharacterSelectionToServer(nextIndex);
               player.sendSystemMessage(Component.translatable("key.minegenshin.switched_character", new Object[]{character.getName().getString()}));
               return;
            }
         }
      }
   }
}
