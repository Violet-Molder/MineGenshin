package com.linweiyun.genshin.client.render.gui.menu;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.Comparator;

/**
 * 圣遗物排序方式枚举。
 *
 * 排序规则：
 *   1. 先分三组：已穿戴 → 未穿戴已激活 → 未穿戴未激活。
 *   2. 组内按各自排序方法排序。
 *   3. 所有方法最终都以部位顺序（生之花、死之羽、时之沙、空之杯、理之冠）作为兜底。
 */
public enum ArtifactSortMethod {
    STAR("star", "gui.minegenshin.backpack.sort_star"),
    LEVEL("level", "gui.minegenshin.backpack.sort_level"),
    SET("set", "gui.minegenshin.backpack.sort_set");

    private final String value;
    private final String translationKey;

    ArtifactSortMethod(String value, String translationKey) {
        this.value = value;
        this.translationKey = translationKey;
    }

    @Override
    public String toString() { return I18n.get(translationKey); }
    public String getValue() { return value; }

    /**
     * 组内比较器（不包含分组逻辑，分组由调用方处理）。
     */
    public Comparator<Entry> comparator() {
        Comparator<Entry> base = switch (this) {
            case STAR -> Comparator
                    .comparingInt((Entry e) -> -e.star())     // 星级高的在前
                    .thenComparingInt(e -> -e.level())       // 再按等级高在前
                    .thenComparingInt(Entry::setId);         // 再按套装号小在前
            case LEVEL -> Comparator
                    .comparingInt((Entry e) -> -e.level())   // 等级高的在前
                    .thenComparingInt(e -> -e.star())        // 再按星级高在前
                    .thenComparingInt(Entry::setId);         // 再按套装号小在前
            case SET -> Comparator
                    .comparingInt(Entry::setId)              // 套装号小的在前
                    .thenComparingInt(e -> -e.star())        // 再按星级高在前
                    .thenComparingInt(e -> -e.level());      // 再按等级高在前
        };
        return base.thenComparingInt(Entry::typeOrdinal);    // 兜底：生之花、死之羽、时之沙、空之杯、理之冠
    }

    /**
     * 可排序条目。
     *
     * stack                        物品堆
     * globalSlot                   背包全局槽位；已穿戴时为 -1
     * equipped                     是否被角色穿戴
     * activated                    是否已激活（未激活不能穿戴、不能获取经验）
     * star                         星级
     * level                        等级
     * setId                        套装编号
     * typeOrdinal                  部位序数 0=花 1=羽 2=沙 3=杯 4=冠
     * handler                      该物品所在容器的 ResourceHandler
     * containerSlot                该物品在所属容器内的索引
     * key                          稳定标识，用于在多次刷新之间保持选中状态
     * equippedByCharacterName      装备该圣遗物的角色显示名；未装备时为 null
     * equippedByCharacterTextureId 装备该圣遗物的角色贴图 ID；未装备时为 null
     */
    public record Entry(ItemStack stack, int globalSlot, boolean equipped,
                        boolean activated,
                        int star, int level, int setId, int typeOrdinal,
                        ResourceHandler<ItemResource> handler, int containerSlot,
                        String key,
                        String equippedByCharacterName,
                        String equippedByCharacterTextureId) {}
}