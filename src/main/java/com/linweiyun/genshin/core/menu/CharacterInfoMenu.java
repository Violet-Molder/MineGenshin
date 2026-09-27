package com.linweiyun.genshin.core.menu;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.content.items.artifact.inventory.ArtifactInventory;
import com.linweiyun.genshin.content.items.artifact.inventory.ArtifactSlot;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.system.registry.register.ModMenus;
import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.slf4j.Logger;

/**
 * 角色信息菜单（容器菜单）
 * <p>
 * 该类用于显示和管理当前角色的圣遗物（Artifact）装备界面，
 * 同时整合玩家背包槽位，支持通过 LowDragLib2 的模块化 UI 系统进行渲染。
 * 继承自 Minecraft 原版的 AbstractContainerMenu，因此具备完整的容器交互逻辑（如点击、快速移动等）。
 */
public class CharacterInfoMenu extends AbstractContainerMenu {

    /** 日志记录器，用于输出调试或错误信息 */
    private static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);

    /** 当前角色的圣遗物背包（ArtifactInventory），存储角色装备槽中的圣遗物 */
    private final ArtifactInventory artifactInventory;

    /**
     * 构造方法：创建角色信息菜单实例
     *
     * @param containerId    容器窗口的唯一 ID，由服务端分配
     * @param playerInventory 玩家的背包（包含主物品栏与快捷栏）
     */
    public CharacterInfoMenu(int containerId, Inventory playerInventory) {
        // 调用父类构造，注册菜单类型为 CHARACTER_INFO_MENU
        super(ModMenus.CHARACTER_INFO_MENU.get(), containerId);

        Player player = playerInventory.player;

        // 从玩家数据附件（Attachment）中获取玩家角色信息
        PlayerCharactersAttachment attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        PGCharacter character = attachment.getCurrentCharacter();

        // 如果当前有角色且角色数据不为空，则使用该角色的圣遗物背包；否则创建一个空的背包
        if (character != null && character.getData() != null) {
            this.artifactInventory = character.getData().getArtifactInventory();
        } else {
            this.artifactInventory = new ArtifactInventory();
        }

        // 添加圣遗物槽位到容器（共 SLOT_COUNT 个，位置暂时设为屏幕外 -9999，由 UI 系统接管实际渲染位置）
        for (int i = 0; i < ArtifactInventory.SLOT_COUNT; i++) {
            this.addSlot(new ArtifactSlot(artifactInventory, i, -9999, -9999));
        }

        // 添加玩家主物品栏槽位（3行 x 9列 = 27个槽位）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, -9999, -9999));
            }
        }
        // 添加玩家快捷栏槽位（9个槽位）
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, -9999, -9999));
        }

        // 创建模块化 UI 并设置到当前菜单（如果当前类实现了 IModularUIHolderMenu 接口）
        var modularUI = createModularUI(player);
        if (this instanceof IModularUIHolderMenu holder) {
            holder.setModularUI(modularUI);
        }
    }

    /**
     * 创建模块化 UI 界面
     * <p>
     * 使用 LowDragLib2 的 UI 系统构建界面结构：
     * - 根节点 root 包含背景 backGround
     * - backGround 包含角色列表容器、圣遗物容器和玩家背包槽位
     *
     * @param player 当前玩家实体
     * @return 构建好的 ModularUI 实例
     */
    private ModularUI createModularUI(Player player) {
        // UI 根节点
        var root = new UIElement();

        // 加载样式表文件（LSS 格式，类似 CSS，用于定义 UI 元素样式）
        var stylesheet = StylesheetManager.INSTANCE.getStylesheetSafe(
                Identifier.parse("minegenshin:lss/character_info.lss"));

        // 背景层 UI 元素
        var backGround = new UIElement();

        // 角色列表容器，使用横向（ROW）弹性布局
        var character_list_container = new UIElement().setId("character_list_container")
                .layout(layoutStyle -> layoutStyle.flexDirection(FlexDirection.ROW));

        // 圣遗物容器，同样使用横向弹性布局
        var artifact_container = new UIElement().setId("artifact_container")
                .layout(layoutStyle -> layoutStyle.flexDirection(FlexDirection.ROW));

        // 将圣遗物背包转换为资源处理器，用于绑定到 UI 的 ItemSlot
        ResourceHandler<ItemResource> artifactHandler = artifactInventory.asResourceHandler();
        for (int i = 0; i < ArtifactInventory.SLOT_COUNT; i++) {
            var slot = new ItemSlot();
            slot.bind(artifactHandler, i); // 将每个槽位绑定到对应的资源处理器索引
            artifact_container.addChildren(slot);
        }

        // 组装 UI 层级结构：root -> backGround -> [character_list_container, artifact_container, InventorySlots]
        root.addChildren(
                backGround.addChildren(
                        character_list_container,
                        artifact_container,
                        new InventorySlots() // 玩家背包槽位组件
                )
        );

        // 创建 UI 实例并包装为 ModularUI
        var ui = UI.of(root, stylesheet);
        return ModularUI.of(ui, player);
    }

    /**
     * 获取当前菜单关联的圣遗物背包
     *
     * @return ArtifactInventory 实例
     */
    public ArtifactInventory getArtifactInventory() {
        return artifactInventory;
    }

    /**
     * 快速移动物品（Shift+点击）逻辑
     * <p>
     * 规则：
     * - 如果点击的是圣遗物槽位（index < artifactSlots），尝试将物品移动到玩家背包
     * - 如果点击的是玩家背包槽位，尝试将物品移动到圣遗物槽位
     *
     * @param player 当前玩家
     * @param index  被点击的槽位索引
     * @return 移动后的物品堆副本（如果移动失败则返回空堆）
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack slotStack = slot.getItem();
        ItemStack result = slotStack.copy();

        int artifactSlots = ArtifactInventory.SLOT_COUNT;
        int totalSlots = this.slots.size();

        if (index < artifactSlots) {
            // 从圣遗物槽位移动到玩家背包（true 表示从后往前填充）
            if (!this.moveItemStackTo(slotStack, artifactSlots, totalSlots, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 从玩家背包移动到圣遗物槽位（false 表示从前往后填充）
            if (!this.moveItemStackTo(slotStack, 0, artifactSlots, false)) {
                return ItemStack.EMPTY;
            }
        }

        // 如果物品全部移出，清空原槽位；否则标记槽位状态已改变
        if (slotStack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }

    /**
     * 处理玩家点击槽位的逻辑
     * <p>
     * 当前对圣遗物槽位有特殊判断逻辑（预留扩展点），其余情况调用父类默认处理。
     *
     * @param slotIndex     被点击的槽位索引
     * @param buttonNum     鼠标按键编号（0=左键，1=右键）
     * @param containerInput 点击类型（如普通点击、Shift点击等）
     * @param player        当前玩家
     */
    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput containerInput, Player player) {
        if (slotIndex >= 0 && slotIndex < ArtifactInventory.SLOT_COUNT) {
            // 如果点击的是圣遗物槽位，获取对应槽位实例（当前为预留逻辑）
            Slot slot = slotIndex < this.slots.size() ? this.getSlot(slotIndex) : null;
        }
        super.clicked(slotIndex, buttonNum, containerInput, player);
    }

    /**
     * 判断容器是否仍然有效（是否可以继续打开）
     * <p>
     * 当前实现始终返回 true，表示该菜单没有额外的距离或条件限制。
     *
     * @param player 当前玩家
     * @return 始终返回 true
     */
    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
