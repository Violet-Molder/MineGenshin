// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.content.items.weapon;

import com.linweiyun.genshin.content.attribute.AttributeType;
import com.linweiyun.genshin.content.items.TeyvatItem;
import com.linweiyun.genshin.content.items.component.WeaponStatsComponent;
import com.linweiyun.genshin.content.stat.TeyvatItemStat;
import com.linweiyun.elementlib.core.attachment.StatusContainer;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.elementlib.core.system.about.AttachmentProfile;
import com.linweiyun.elementlib.core.system.about.AttachmentSource;
import com.linweiyun.elementlib.core.system.about.ElementalAttachmentHelper;
import com.linweiyun.genshin.core.system.combat.action.ActionKind;
import com.linweiyun.genshin.core.system.poise.WeaponPoiseTable;
import com.linweiyun.genshin.core.system.registry.register.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.neoforged.neoforge.registries.DeferredHolder;

public class WeaponItem extends TeyvatItem {
   protected int tier = 1;
   protected DeferredHolder<AttributeType, AttributeType> subStatAttribute;
   protected boolean canRefine = true;
   protected double mainStatDelta = 0.0;

   public double getMainStatDelta() {
      return this.mainStatDelta;
   }

   public void onAbilityCast(Player player, PGCharacter character, ActionKind kind) {
   }

   public void onLeaveField(Player player, PGCharacter character) {
   }

   public WeaponItem(Properties properties) {
      super(properties.stacksTo(1));
   }

   public float getHealingBonus() {
      return 0.0F;
   }

   public void onHeal(Player healer, PGCharacter character, LivingEntity target, float amount) {
   }

   public static void notifyHeal(Player healer, PGCharacter character, LivingEntity target, float amount) {
      if (healer != null && character != null && !healer.level().isClientSide()) {
         ItemStack stack = character.getData().getWeapon();
         if (!stack.isEmpty() && stack.getItem() instanceof WeaponItem weapon) {
            weapon.onHeal(healer, character, target, amount);
         }
      }
   }

   public int getTier() {
      return this.tier;
   }

   public DeferredHolder<AttributeType, AttributeType> getSubStatAttribute() {
      return this.subStatAttribute;
   }

   public WeaponStatsComponent getStats(ItemStack stack) {
      return (WeaponStatsComponent)stack.getOrDefault((DataComponentType)ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT);
   }

   public WeaponStatsComponent getOrInitStats(ItemStack stack) {
      WeaponStatsComponent stats = this.getStats(stack);
      if (stats.uid == 0L && stack.getItem() instanceof WeaponItem weapon) {
         stats = new WeaponStatsComponent();
         stats.tier = weapon.tier;
         stats.subStatType = weapon.subStatAttribute != null ? (AttributeType)weapon.subStatAttribute.get() : new AttributeType();
         stats.uid = weapon.getUID();
         stats.initStats(this.star);
         stack.set((DataComponentType)ModDataComponents.WEAPON_STATS.get(), stats);
      }

      return stats;
   }

   public void addExp(ItemStack stack, int amount) {
      WeaponStatsComponent stats = this.getOrInitStats(stack);
      stats.addExp(amount, this.star);
      stack.set((DataComponentType)ModDataComponents.WEAPON_STATS.get(), stats);
   }

   public boolean ascend(ItemStack stack) {
      WeaponStatsComponent stats = this.getOrInitStats(stack);
      boolean result = stats.ascend(this.star);
      if (result) {
         stack.set((DataComponentType)ModDataComponents.WEAPON_STATS.get(), stats);
      }

      return result;
   }

   public boolean canAscend(ItemStack stack) {
      WeaponStatsComponent stats = this.getOrInitStats(stack);
      return stats.canAscend();
   }

   public boolean canRefine() {
      return this.canRefine;
   }

   public void setCanRefine(boolean canRefine) {
      this.canRefine = canRefine;
   }

   public boolean increaseRefinement(ItemStack stack) {
      if (!this.canRefine) {
         return false;
      }

      WeaponStatsComponent stats = this.getOrInitStats(stack);
      boolean result = stats.increaseRefinement(this.star);
      if (result) {
         stack.set((DataComponentType)ModDataComponents.WEAPON_STATS.get(), stats);
      }

      return result;
   }

   public void attach(LivingEntity target, StatusContainer container, GenshinElement element, AttachmentSource source, AttachmentProfile profile) {
      ElementalAttachmentHelper.attach(target, container, element, source, profile);
   }

   public int getUID() {
      if (this.star <= 0) {
         return 0;
      }

      int typeDigit = this.getWeaponTypeDigit();
      return 100000 + this.star * 10000 + typeDigit * 1000 + this.tier * 100;
   }

   private int getWeaponTypeDigit() {
      return switch (WeaponPoiseTable.weaponOfClass(this.getClass())) {
         case POLEARM -> 1;
         case SWORD -> 2;
         case CLAYMORE -> 3;
         case BOW -> 4;
         case CATALYST -> 5;
         case FIST -> 6;
         case UNKNOWN -> 0;
      };
   }

   public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> builder, TooltipFlag flag) {
      WeaponStatsComponent stats = this.getOrInitStats(stack);
      builder.add(Component.literal("★".repeat(this.star)).withStyle(ChatFormatting.GOLD));
      if (stats.refinementRank > 1) {
         builder.add(Component.translatable("item.minegenshin.weapon.refinement_rank", new Object[]{stats.refinementRank}).withStyle(ChatFormatting.AQUA));
      }

      if (stats.mainStat != null && stats.mainStat.isInitialized()) {
         ChatFormatting starColor = this.getStarColor();
         builder.add(Component.literal(this.buildStatText(stats.mainStat)).withStyle(new ChatFormatting[]{starColor, ChatFormatting.BOLD}));
      }

      if (stats.subStat != null && stats.subStat.isInitialized()) {
         builder.add(Component.empty());
         builder.add(Component.literal(this.buildStatText(stats.subStat)).withStyle(ChatFormatting.GRAY));
      }
   }

   private String buildStatText(TeyvatItemStat stat) {
      if (!stat.isInitialized()) {
         return "";
      }

      String attrName = Component.translatable(stat.getAttribute().translationKey()).getString();
      return stat.getKind() == TeyvatItemStat.StatKind.PERCENT
         ? attrName + " +" + String.format("%.1f%%", stat.getValue() * 100.0)
         : attrName + " +" + String.format("%.0f", stat.getValue());
   }

   private ChatFormatting getStarColor() {
      return switch (this.star) {
         case 2 -> ChatFormatting.WHITE;
         case 3 -> ChatFormatting.AQUA;
         case 4 -> ChatFormatting.LIGHT_PURPLE;
         case 5 -> ChatFormatting.YELLOW;
         default -> ChatFormatting.GRAY;
      };
   }
}
