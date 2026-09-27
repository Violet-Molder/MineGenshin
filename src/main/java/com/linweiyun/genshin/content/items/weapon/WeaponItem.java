package com.linweiyun.genshin.content.items.weapon;

import com.linweiyun.genshin.content.attribute.AttributeType;
import com.linweiyun.genshin.content.items.TeyvatItem;
import com.linweiyun.genshin.content.items.component.WeaponStatsComponent;
import com.linweiyun.genshin.content.stat.TeyvatItemStat;
import com.linweiyun.genshin.core.attachment.StatusContainer;
import com.linweiyun.genshin.core.element.GenshinElement;
import com.linweiyun.genshin.core.system.about.AttachmentProfile;
import com.linweiyun.genshin.core.system.about.AttachmentSource;
import com.linweiyun.genshin.core.system.about.ElementalAttachmentHelper;
import com.linweiyun.genshin.core.system.registry.register.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.function.Consumer;

public class WeaponItem extends TeyvatItem {

    protected int tier = 1;
    protected DeferredHolder<AttributeType, AttributeType> subStatAttribute;
    protected boolean canRefine = true;

    /**
     * 主词条的额外修正（默认 0）。
     *
     * <p>{@code tier} 给的是整数基础攻击力（五星 44/46/48/49），而有些武器的主词条是小数
     * （例如蝶变 47.54）—— 用这个差值补齐。只影响「喂给角色的那个数值」，
     * 升级/突破的成长曲线照旧。
     */
    protected double mainStatDelta = 0.0;

    /** 主词条修正值（见 {@link #mainStatDelta}）。 */
    public double getMainStatDelta() {
        return mainStatDelta;
    }

    // ==================== 武器被动钩子 ====================

    /**
     * 装备者施放了元素战技 / 元素爆发（<b>服务端</b>，受理成功那一刻）。
     *
     * <p>默认什么都不做；带被动的武器覆写它。
     */
    public void onAbilityCast(net.minecraft.world.entity.player.Player player,
                              com.linweiyun.genshin.core.character.PGCharacter character,
                              com.linweiyun.genshin.core.system.combat.action.ActionKind kind) {
    }

    /**
     * 装备者<b>退场</b>（切到别的角色）时调用 —— 清被动留下的 buff、重置轮换顺序。
     *
     * <p>默认什么都不做；带被动的武器覆写它。
     */
    public void onLeaveField(net.minecraft.world.entity.player.Player player,
                             com.linweiyun.genshin.core.character.PGCharacter character) {
    }

    public WeaponItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    // ==================== 武器被动钩子（续） ====================

    /**
     * 装备者自身提供的<b>治疗加成</b>（小数，{@code 0.04 = 4%}），默认 0。
     *
     * <p>由 {@code PGCharacter#getHealingBonus} 聚合，角色治疗时消费。
     */
    public float getHealingBonus() {
        return 0f;
    }

    /**
     * 装备者完成了一次治疗（<b>服务端</b>）。
     *
     * <p>这是「角色打出的治疗」的唯一窄口：目前只有沃雅妮莎的
     * {@code VodyanitsaSkill#songHeal}（后台也照常跑）走这里。
     * 默认什么都不做；带「治疗触发」类被动的武器覆写它。
     *
     * @param healer    施疗的玩家
     * @param character 施疗的角色（= 装备者）
     * @param target    被治疗方的实体锚点（角色数据没有实体，传宿主玩家）
     * @param amount    本次实际治疗量
     */
    public void onHeal(net.minecraft.world.entity.player.Player healer,
                       com.linweiyun.genshin.core.character.PGCharacter character,
                       LivingEntity target, float amount) {
    }

    /** 服务端把「装备者治疗了」分发给该角色当前装备的武器。 */
    public static void notifyHeal(net.minecraft.world.entity.player.Player healer,
                                  com.linweiyun.genshin.core.character.PGCharacter character,
                                  LivingEntity target, float amount) {
        if (healer == null || character == null || healer.level().isClientSide()) return;
        ItemStack stack = character.getData().getWeapon();
        if (!stack.isEmpty() && stack.getItem() instanceof WeaponItem weapon) {
            weapon.onHeal(healer, character, target, amount);
        }
    }

    public int getTier() {
        return tier;
    }

    public DeferredHolder<AttributeType, AttributeType> getSubStatAttribute() {
        return subStatAttribute;
    }

    public WeaponStatsComponent getStats(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT);
    }

    public WeaponStatsComponent getOrInitStats(ItemStack stack) {
        WeaponStatsComponent stats = getStats(stack);
        if (stats.uid == 0 && stack.getItem() instanceof WeaponItem weapon) {
            stats = new WeaponStatsComponent();
            stats.tier = weapon.tier;
            stats.subStatType = weapon.subStatAttribute != null ? weapon.subStatAttribute.get() : new AttributeType();
            stats.uid = weapon.getUID();
            stats.initStats(star);
            stack.set(ModDataComponents.WEAPON_STATS.get(), stats);
        }
        return stats;
    }

    public void addExp(ItemStack stack, int amount) {
        WeaponStatsComponent stats = getOrInitStats(stack);
        stats.addExp(amount, star);
        stack.set(ModDataComponents.WEAPON_STATS.get(), stats);
    }

    public boolean ascend(ItemStack stack) {
        WeaponStatsComponent stats = getOrInitStats(stack);
        boolean result = stats.ascend(star);
        if (result) {
            stack.set(ModDataComponents.WEAPON_STATS.get(), stats);
        }
        return result;
    }

    public boolean canAscend(ItemStack stack) {
        WeaponStatsComponent stats = getOrInitStats(stack);
        return stats.canAscend();
    }

    public boolean canRefine() {
        return canRefine;
    }

    public void setCanRefine(boolean canRefine) {
        this.canRefine = canRefine;
    }

    public boolean increaseRefinement(ItemStack stack) {
        if (!canRefine) return false;
        WeaponStatsComponent stats = getOrInitStats(stack);
        boolean result = stats.increaseRefinement(star);
        if (result) {
            stack.set(ModDataComponents.WEAPON_STATS.get(), stats);
        }
        return result;
    }

    public void attach(LivingEntity target, StatusContainer container,
                       GenshinElement element,
                       AttachmentSource source,
                       AttachmentProfile profile) {
        ElementalAttachmentHelper.attach(target, container, element, source, profile);
    }

    public int getUID() {
        if (star <= 0) return 0;
        int typeDigit = getWeaponTypeDigit();
        int uid = 100000 + star * 10000 + typeDigit * 1000 + tier * 100;
        return uid;
    }

    /** 武器类型那一位数字：六种武器各占一档（认类只问 {@code WeaponPoiseTable}，不自己 instanceof）。 */
    private int getWeaponTypeDigit() {
        return switch (com.linweiyun.genshin.core.system.poise.WeaponPoiseTable.weaponOfClass(getClass())) {
            case POLEARM -> 1;
            case SWORD -> 2;
            case CLAYMORE -> 3;
            case BOW -> 4;
            case CATALYST -> 5;
            case FIST -> 6;
            case UNKNOWN -> 0;
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        WeaponStatsComponent stats = getOrInitStats(stack);

        builder.accept(Component.literal("★".repeat(star)).withStyle(ChatFormatting.GOLD));

        if (stats.refinementRank > 1) {
            builder.accept(Component.translatable("item.minegenshin.weapon.refinement_rank", stats.refinementRank).withStyle(ChatFormatting.AQUA));
        }

        if (stats.mainStat != null && stats.mainStat.isInitialized()) {
            ChatFormatting starColor = getStarColor();
            builder.accept(Component.literal(buildStatText(stats.mainStat)).withStyle(starColor, ChatFormatting.BOLD));
        }

        if (stats.subStat != null && stats.subStat.isInitialized()) {
            builder.accept(Component.empty());
            builder.accept(Component.literal(buildStatText(stats.subStat)).withStyle(ChatFormatting.GRAY));
        }
    }

    private String buildStatText(TeyvatItemStat stat) {
        if (!stat.isInitialized()) return "";
        String attrName = Component.translatable(stat.getAttribute().translationKey()).getString();
        if (stat.getKind() == TeyvatItemStat.StatKind.PERCENT) {
            return attrName + " +" + String.format("%.1f%%", stat.getValue() * 100);
        } else {
            return attrName + " +" + String.format("%.0f", stat.getValue());
        }
    }

    private ChatFormatting getStarColor() {
        return switch (star) {
            case 5 -> ChatFormatting.YELLOW;
            case 4 -> ChatFormatting.LIGHT_PURPLE;
            case 3 -> ChatFormatting.AQUA;
            case 2 -> ChatFormatting.WHITE;
            default -> ChatFormatting.GRAY;
        };
    }
}
