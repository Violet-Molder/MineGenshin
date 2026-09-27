package com.linweiyun.genshin.client.keybindings;

import com.linweiyun.genshin.client.combat.state.ActionStateMachine;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.linweiyun.genshin.core.world.TeyvatWorldInvasion;
import com.linweiyun.genshin.client.render.gui.screen.ScreenNavigator;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 非动作类按键的 tick 处理。
 *
 * <p>普攻 / 战技 / 闪避 / 大招已经在 {@link KeyMappingRegistry} 的 {@code setDown} 里
 * 0 延迟触发；这里只处理切角色、开关原神模式、抽卡与几个界面键。
 *
 * <p>以前的「移动锁 / 后摇打断 / 长按重击」逻辑已经全部搬进
 * {@link ActionStateMachine}，这里不再插手运动状态。
 */
@EventBusSubscriber(value = Dist.CLIENT)
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
    public static void onKeyInput(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        if (!TeyvatWorldInvasion.isClientInvaded()) return;

        boolean isInGenshinMode = isInGenshinMode(player);
        PlayerCharactersAttachment charactersAttachment =
                player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        var character = charactersAttachment == null ? null : charactersAttachment.getCurrentCharacter();

        // H：抽卡（原 R）
        boolean isWishDown = KeyMappingRegistry.WISH_KEY.get().isDown();
        if (isWishDown && !wasWishKeyDown) {
            NetworkManager.wishEventToServer();
        }
        wasWishKeyDown = isWishDown;

        // G：切换原神模式
        boolean isGDown = KeyMappingRegistry.G_KEY.get().isDown();
        if (isGDown && !wasGKeyDown) {
            NetworkManager.setGenshinModeToServer(!isInGenshinMode);
        }
        wasGKeyDown = isGDown;

        // V：切换队伍角色
        boolean isVDown = KeyMappingRegistry.V_KEY.get().isDown();
        if (isVDown && !wasVKeyDown && isInGenshinMode) {
            switchToNextAvailableCharacter(player, charactersAttachment);
        }
        wasVKeyDown = isVDown;

        // O：队伍界面
        boolean isODown = KeyMappingRegistry.O_KEY.get().isDown();
        if (isODown && !wasOKeyDown) {
            ScreenNavigator.openCharacterPartyScreen(player);
        }
        wasOKeyDown = isODown;

        // U：角色信息
        boolean isCharInfoDown = KeyMappingRegistry.CHARACTER_INFO_SCREEN_KEY.get().isDown();
        if (isCharInfoDown && !wasCharInfoKeyDown) {
            ScreenNavigator.openArtifactEquipScreen(player, -1);
        }
        wasCharInfoKeyDown = isCharInfoDown;

        // B：背包
        boolean isArtifactDown = KeyMappingRegistry.ARTIFACT_EQUIP_SCREEN_KEY.get().isDown();
        if (isArtifactDown && !wasArtifactKeyDown) {
            ScreenNavigator.openBackpackScreen(player);
        }
        wasArtifactKeyDown = isArtifactDown;

        // N：升格界面（和「角色配置」换过位，2026-09-27 又换回来了：配置页留在 K）
        boolean isArtifact2Down = KeyMappingRegistry.ARTIFACT_EQUIP_SCREEN_KEY_2.get().isDown();
        if (isArtifact2Down && !wasArtifactKey2Down) {
            ScreenNavigator.openAscensionScreen(player);
        }
        wasArtifactKey2Down = isArtifact2Down;

        // K：角色配置页（页面由角色自己提供，见 ICharacterConfigUI）
        boolean isConfigDown = KeyMappingRegistry.CONFIG_SCREEN_KEY.get().isDown();
        if (isConfigDown && !wasConfigKeyDown) {
            ScreenNavigator.openCharacterConfigScreen(player);
        }
        wasConfigKeyDown = isConfigDown;

        // 角色死亡/切换时把蓄力状态清掉，避免残留的按住标记卡住下一段
        if (character == null) {
            ActionStateMachine.isAttackButtonDown = false;
            ActionStateMachine.releaseSkill(player);
        }
    }

    private static boolean isInGenshinMode(Player player) {
        return player.hasData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT)
                && player.getData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT);
    }

    private static void switchToNextAvailableCharacter(Player player, PlayerCharactersAttachment attachment) {
        if (attachment == null) return;

        // 本地表现立刻复位；服务端的动作打断由 characterSelectionRPCPacket 负责
        ActionStateMachine.resetToDefault();
        ActionStateMachine.comboStage = 1;

        int currentIndex = attachment.getCurrentCharacterIndex();
        for (int i = 1; i <= 4; i++) {
            int nextIndex = (currentIndex + i) % 4;
            PGCharacter character = attachment.getPartyCharacter(nextIndex);
            if (character != null && character.getData().getCurrentHP() > 0) {
                attachment.setCurrentCharacterIndex(nextIndex);
                NetworkManager.setCharacterSelectionToServer(nextIndex);
                player.sendSystemMessage(
                        Component.translatable("key.minegenshin.switched_character",
                                character.getName().getString()));
                return;
            }
        }
    }
}
