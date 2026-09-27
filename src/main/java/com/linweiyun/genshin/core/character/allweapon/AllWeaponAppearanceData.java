// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character.allweapon;

import com.linweiyun.genshin.core.character.appearance.CharacterAppearanceData;
import com.linweiyun.genshin.core.character.appearance.WeaponAppearance;

public final class AllWeaponAppearanceData extends CharacterAppearanceData {
   public static final AllWeaponAppearanceData INSTANCE = new AllWeaponAppearanceData();
   private static final int WEAPON_BITS = 7;
   private static final int WEAPON_SHIFT = 8;
   public static final int OPTION_WEAPON = 0;
   public static final int OPTION_SHOW_WEAPON = 1;

   private AllWeaponAppearanceData() {
   }

   @Override
   public int optionCount() {
      return 2;
   }

   @Override
   public String optionNameKey(int index) {
      return switch (index) {
         case 0 -> "gui.minegenshin.character_config.weapon_appearance";
         case 1 -> "gui.minegenshin.character_config.show_weapon";
         default -> throw new IndexOutOfBoundsException("外观项下标越界: " + index);
      };
   }

   @Override
   public CharacterAppearanceData.OptionKind optionKind(int index) {
      return index == 0 ? CharacterAppearanceData.OptionKind.CHOICE : CharacterAppearanceData.OptionKind.TOGGLE;
   }

   @Override
   public int optionValueCount(int index) {
      return index == 0 ? WeaponAppearance.values().length : 0;
   }

   @Override
   public String optionValueNameKey(int index, int value) {
      return index == 0 ? WeaponAppearance.byOrdinal(value).nameKey() : "";
   }

   @Override
   public int optionValue(int mask, int index) {
      // 她是两项：0 = 武器形态（存在 bit 8~10），1 = 显示武器（基类约定存在 bit 7）。
      // ⚠️ index 1 必须显式转给基类的 showWeapon —— 基类的 optionValue/withOptionValue 只认
      // 「index 0 = 显示武器」，直接落回 super 的话这一档永远读写不到，开关点了等于没点
      // （表现就是「勾了常态显示武器，武器还是不出现」）。
      return switch (index) {
         case 0 -> this.weapon(mask).ordinal();
         case 1 -> super.showWeapon(mask) ? 1 : 0;
         default -> super.optionValue(mask, index);
      };
   }

   @Override
   public int withOptionValue(int mask, int index, int value) {
      return switch (index) {
         case 0 -> this.withWeapon(mask, WeaponAppearance.byOrdinal(value));
         case 1 -> super.withShowWeapon(mask, value != 0);
         default -> super.withOptionValue(mask, index, value);
      };
   }

   public WeaponAppearance weapon(int mask) {
      return WeaponAppearance.byOrdinal(mask >> 8 & 7);
   }

   public int withWeapon(int mask, WeaponAppearance weapon) {
      int cleared = mask & -1793;
      return cleared | (weapon.ordinal() & 7) << 8;
   }

   @Override
   public int weaponSlotOffset(int mask) {
      return this.weapon(mask).ordinal();
   }
}
