package com.linweiyun.genshin.client.render.gui.screen;

import com.linweiyun.genshin.content.items.component.WeaponStatsComponent;
import com.linweiyun.genshin.content.items.weapon.WeaponItem;
import com.linweiyun.genshin.core.attachment.AdventurerInfoAttachment;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.linweiyun.genshin.core.system.registry.register.ModDataComponents;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUIClientAccess;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class ScreenAscension extends Screen {
    final ModularUI modularUI;
    final Player player;

    private Label advRankLabel;
    private Label advExpLabel;
    private Label worldLevelLabel;
    private Button worldLevelBtn;
    private Label charExpLabel;
    private Label charLevelLabel;

    private Label weaponInfoLabel;
    private Button weaponBreakthroughBtn;
    private Label normalAttackLabel;
    private Button normalAttackBtn;
    private Label elementalSkillLabel;
    private Button elementalSkillBtn;
    private Label elementalBurstLabel;
    private Button elementalBurstBtn;

    public ScreenAscension(Player player) {
        super(Component.empty());
        this.player = player;
        this.modularUI = createModularUI();
    }

    @Override
    public void init() {
        super.init();
        ModularUIClientAccess.setScreenAndInit(this.modularUI, this);
        this.addRenderableWidget(ModularUIClientAccess.getWidget(modularUI));
    }

    @Override
    public void tick() {
        super.tick();
        refreshLabels();
    }

    private void refreshLabels() {
        AdventurerInfoAttachment advInfo = player.getData(AttachmentRegistration.ADVENTURER_INFO_ATTACHMENT);
        advRankLabel.setText(Component.translatable("gui.minegenshin.ascension.adventure_rank", advInfo.getAdventureRank()));
        if (advInfo.isMaxRank()) {
            advExpLabel.setText(Component.translatable("gui.minegenshin.ascension.adventure_exp_max"));
        } else {
            advExpLabel.setText(Component.translatable("gui.minegenshin.ascension.adventure_exp", advInfo.getCurrentExp(), advInfo.getExpToNextRank()));
        }
        worldLevelLabel.setText(Component.translatable("gui.minegenshin.ascension.world_level", advInfo.getWorldLevel(), advInfo.getBreakthroughLevel()));

        if (advInfo.canDowngradeWorldLevel()) {
            worldLevelBtn.setText(Component.translatable("gui.minegenshin.ascension.lower_world_level"));
            worldLevelBtn.setVisible(true);
        } else if (advInfo.canRestoreWorldLevel()) {
            worldLevelBtn.setText(Component.translatable("gui.minegenshin.ascension.restore_world_level"));
            worldLevelBtn.setVisible(true);
        } else {
            worldLevelBtn.setVisible(false);
        }

        PlayerCharactersAttachment charAttachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        PGCharacter currentChar = charAttachment.getCurrentCharacter();
        if (currentChar != null && currentChar.getData() != null) {
            charExpLabel.setText(Component.translatable("gui.minegenshin.ascension.character_exp", currentChar.getData().getCurrentExp(), currentChar.getData().getMaxExp()));
            charLevelLabel.setText(Component.translatable("gui.minegenshin.ascension.character_level", currentChar.getData().getLevel(), currentChar.getData().getAscensionPhase()));
            charExpLabel.setVisible(true);
            charLevelLabel.setVisible(true);

            ItemStack weaponStack = currentChar.getData().getWeapon();
            if (!weaponStack.isEmpty() && weaponStack.getItem() instanceof WeaponItem weapon) {
                WeaponStatsComponent stats = weaponStack.getOrDefault(
                        ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT);
                weaponInfoLabel.setText(Component.translatable("gui.minegenshin.ascension.weapon_info", stats.level, stats.ascended, stats.exp));
                weaponInfoLabel.setVisible(true);
                weaponBreakthroughBtn.setVisible(stats.canAscend());
            } else {
                weaponInfoLabel.setText(Component.translatable("gui.minegenshin.ascension.weapon_unequipped"));
                weaponInfoLabel.setVisible(true);
                weaponBreakthroughBtn.setVisible(false);
            }

            // 上限按【每个天赋各自】的 cap 显示：普攻 10；战技/爆发 3 命 / 5 命后是 13
            normalAttackLabel.setText(Component.translatable("gui.minegenshin.ascension.normal_attack_label",
                    currentChar.getData().getNormalAttackLevel(), currentChar.getData().getNormalAttackLevelCap()));
            elementalSkillLabel.setText(Component.translatable("gui.minegenshin.ascension.elemental_skill_label",
                    currentChar.getData().getElementalSkillLevel(), currentChar.getData().getElementalSkillLevelCap()));
            elementalBurstLabel.setText(Component.translatable("gui.minegenshin.ascension.elemental_burst_label",
                    currentChar.getData().getElementalBurstLevel(), currentChar.getData().getElementalBurstLevelCap()));

            normalAttackLabel.setVisible(true);
            elementalSkillLabel.setVisible(true);
            elementalBurstLabel.setVisible(true);
            normalAttackBtn.setVisible(currentChar.getData().canUpgradeNormalAttack());
            elementalSkillBtn.setVisible(currentChar.getData().canUpgradeElementalSkill());
            elementalBurstBtn.setVisible(currentChar.getData().canUpgradeElementalBurst());
        } else {
            charExpLabel.setVisible(false);
            charLevelLabel.setVisible(false);
            weaponInfoLabel.setVisible(false);
            weaponBreakthroughBtn.setVisible(false);
            normalAttackLabel.setVisible(false);
            elementalSkillLabel.setVisible(false);
            elementalBurstLabel.setVisible(false);
            normalAttackBtn.setVisible(false);
            elementalSkillBtn.setVisible(false);
            elementalBurstBtn.setVisible(false);
        }
    }

    private ModularUI createModularUI() {
        var stylesheet = StylesheetManager.INSTANCE.getStylesheetSafe(
                Identifier.parse("minegenshin:lss/ascension.lss"));

        AdventurerInfoAttachment advInfo = player.getData(AttachmentRegistration.ADVENTURER_INFO_ATTACHMENT);
        PlayerCharactersAttachment charAttachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        PGCharacter currentChar = charAttachment.getCurrentCharacter();

        var root = new UIElement().setId("root");
        var window = new UIElement().setId("window");

        var titleLabel = new Label();
        titleLabel.setId("ascension-title");
        titleLabel.setText(Component.translatable("gui.minegenshin.ascension.title"));

        var advInfoContainer = new UIElement().setId("adv-info-container");

        advRankLabel = new Label();
        advRankLabel.setId("adv-rank-label");
        advRankLabel.setText(Component.translatable("gui.minegenshin.ascension.adventure_rank", advInfo.getAdventureRank()));

        advExpLabel = new Label();
        advExpLabel.setId("adv-exp-label");
        if (advInfo.isMaxRank()) {
            advExpLabel.setText(Component.translatable("gui.minegenshin.ascension.adventure_exp_max"));
        } else {
            advExpLabel.setText(Component.translatable("gui.minegenshin.ascension.adventure_exp", advInfo.getCurrentExp(), advInfo.getExpToNextRank()));
        }

        worldLevelLabel = new Label();
        worldLevelLabel.setId("world-level-label");
        worldLevelLabel.setText(Component.translatable("gui.minegenshin.ascension.world_level", advInfo.getWorldLevel(), advInfo.getBreakthroughLevel()));

        var advBreakthroughBtn = new Button();
        advBreakthroughBtn.setId("adv-breakthrough-btn");
        advBreakthroughBtn.setText(Component.translatable("gui.minegenshin.ascension.adventure_rank_up"));
        advBreakthroughBtn.setOnClick(e -> {
            if (advInfo.canBreakthroughWorldLevel()) {
                NetworkManager.sendAscendAdventureRankToServer();
            } else {
                if (player.isLocalPlayer()) {
                    player.sendSystemMessage(Component.translatable("gui.minegenshin.ascension.cannot_rank_up"));
                }
            }
        });

        worldLevelBtn = new Button();
        worldLevelBtn.setId("world-level-btn");
        worldLevelBtn.setOnClick(e -> {
            if (advInfo.canDowngradeWorldLevel()) {
                NetworkManager.sendDowngradeWorldLevelToServer();
            } else if (advInfo.canRestoreWorldLevel()) {
                NetworkManager.sendRestoreWorldLevelToServer();
            }
        });
        if (advInfo.canDowngradeWorldLevel()) {
            worldLevelBtn.setText(Component.translatable("gui.minegenshin.ascension.lower_world_level"));
        } else if (advInfo.canRestoreWorldLevel()) {
            worldLevelBtn.setText(Component.translatable("gui.minegenshin.ascension.restore_world_level"));
        } else {
            worldLevelBtn.setVisible(false);
        }

        advInfoContainer.addChildren(advRankLabel, advExpLabel, worldLevelLabel, advBreakthroughBtn, worldLevelBtn);

        var charInfoContainer = new UIElement().setId("char-info-container");
        if (currentChar != null && currentChar.getData() != null) {
            charExpLabel = new Label();
            charExpLabel.setId("char-exp-label");
            charExpLabel.setText(Component.translatable("gui.minegenshin.ascension.character_exp", currentChar.getData().getCurrentExp(), currentChar.getData().getMaxExp()));

            charLevelLabel = new Label();
            charLevelLabel.setId("char-level-label");
            charLevelLabel.setText(Component.translatable("gui.minegenshin.ascension.character_level", currentChar.getData().getLevel(), currentChar.getData().getAscensionPhase()));

            var charBreakthroughBtn = new Button();
            charBreakthroughBtn.setId("char-breakthrough-btn");
            charBreakthroughBtn.setText(Component.translatable("gui.minegenshin.ascension.character_ascend"));

            final int maxLevelForPhase = currentChar.getData().getAscensionPhase() == 0
                    ? 20
                    : Math.min((currentChar.getData().getAscensionPhase() + 3) * 10, 90);

            charBreakthroughBtn.setOnClick(e -> {
                boolean canAscend = currentChar.getData().getLevel() >= maxLevelForPhase
                        && currentChar.getData().getLevel() < 90;
                if (canAscend) {
                    NetworkManager.sendAscendCharacterToServer();
                } else {
                    if (player.isLocalPlayer()) {
                        player.sendSystemMessage(Component.translatable("gui.minegenshin.ascension.cannot_ascend", maxLevelForPhase));
                    }
                }
            });

            weaponInfoLabel = new Label();
            weaponInfoLabel.setId("weapon-info-label");
            weaponBreakthroughBtn = new Button();
            weaponBreakthroughBtn.setId("char-breakthrough-btn");
            weaponBreakthroughBtn.setText(Component.translatable("gui.minegenshin.ascension.weapon_ascend"));
            weaponBreakthroughBtn.setOnClick(e -> NetworkManager.sendAscendWeaponToServer());

            normalAttackLabel = new Label();
            normalAttackLabel.setId("talent-level-label");
            normalAttackBtn = new Button();
            normalAttackBtn.setId("char-breakthrough-btn");
            normalAttackBtn.setText(Component.translatable("gui.minegenshin.ascension.normal_attack_upgrade"));
            normalAttackBtn.setOnClick(e -> NetworkManager.sendUpgradeNormalAttackToServer());

            elementalSkillLabel = new Label();
            elementalSkillLabel.setId("talent-level-label");
            elementalSkillBtn = new Button();
            elementalSkillBtn.setId("char-breakthrough-btn");
            elementalSkillBtn.setText(Component.translatable("gui.minegenshin.ascension.elemental_skill_upgrade"));
            elementalSkillBtn.setOnClick(e -> NetworkManager.sendUpgradeElementalSkillToServer());

            elementalBurstLabel = new Label();
            elementalBurstLabel.setId("talent-level-label");
            elementalBurstBtn = new Button();
            elementalBurstBtn.setId("char-breakthrough-btn");
            elementalBurstBtn.setText(Component.translatable("gui.minegenshin.ascension.elemental_burst_upgrade"));
            elementalBurstBtn.setOnClick(e -> NetworkManager.sendUpgradeElementalBurstToServer());

            charInfoContainer.addChildren(charExpLabel, charLevelLabel, charBreakthroughBtn,
                    weaponInfoLabel, weaponBreakthroughBtn,
                    normalAttackLabel, normalAttackBtn,
                    elementalSkillLabel, elementalSkillBtn,
                    elementalBurstLabel, elementalBurstBtn);
        } else {
            charExpLabel = new Label();
            charExpLabel.setId("char-exp-label");
            charExpLabel.setText(Component.translatable("gui.minegenshin.ascension.char_exp_empty"));
            charExpLabel.setVisible(false);

            charLevelLabel = new Label();
            charLevelLabel.setId("char-level-label");
            charLevelLabel.setText(Component.translatable("gui.minegenshin.ascension.char_level_empty"));

            var noCharLabel = new Label();
            noCharLabel.setId("no-char-label");
            noCharLabel.setText(Component.translatable("gui.minegenshin.ascension.no_character"));

            weaponInfoLabel = new Label();
            weaponInfoLabel.setId("weapon-info-label");
            weaponBreakthroughBtn = new Button();
            weaponBreakthroughBtn.setId("char-breakthrough-btn");

            normalAttackLabel = new Label();
            normalAttackBtn = new Button();
            elementalSkillLabel = new Label();
            elementalSkillBtn = new Button();
            elementalBurstLabel = new Label();
            elementalBurstBtn = new Button();

            charInfoContainer.addChildren(charExpLabel, charLevelLabel, noCharLabel);
        }

        window.addChildren(titleLabel, advInfoContainer, charInfoContainer);

        root.layout(layout -> {
            layout.widthPercent(100f);
            layout.heightPercent(100f);
        });

        root.addChildren(window);

        var ui = UI.of(root, stylesheet);
        return ModularUI.of(ui, player);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}