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
      return index == 0 ? this.weapon(mask).ordinal() : super.optionValue(mask, index);
   }

   @Override
   public int withOptionValue(int mask, int index, int value) {
      return index == 0 ? this.withWeapon(mask, WeaponAppearance.byOrdinal(value)) : super.withOptionValue(mask, index, value);
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
