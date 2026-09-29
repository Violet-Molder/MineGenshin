// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character.util.type;

import com.linweiyun.genshin.core.system.poise.WeaponPoiseTable;
import org.jetbrains.annotations.Nullable;

public enum CharacterWeaponClass {
   SWORD,
   CLAYMORE,
   POLEARM,
   CATALYST,
   BOW,
   FIST,
   ALL_WEAPON;

   public static CharacterWeaponClass of(WeaponPoiseTable.WeaponClass weaponType) {
      return switch (weaponType) {
         case SWORD -> SWORD;
         case CLAYMORE -> CLAYMORE;
         case POLEARM -> POLEARM;
         case CATALYST -> CATALYST;
         case BOW -> BOW;
         case FIST -> FIST;
         case UNKNOWN -> ALL_WEAPON;
      };
   }

   @Nullable
   public WeaponPoiseTable.WeaponClass weaponType() {
      return switch (this) {
         case SWORD -> WeaponPoiseTable.WeaponClass.SWORD;
         case CLAYMORE -> WeaponPoiseTable.WeaponClass.CLAYMORE;
         case POLEARM -> WeaponPoiseTable.WeaponClass.POLEARM;
         case CATALYST -> WeaponPoiseTable.WeaponClass.CATALYST;
         case BOW -> WeaponPoiseTable.WeaponClass.BOW;
         case FIST -> WeaponPoiseTable.WeaponClass.FIST;
         case ALL_WEAPON -> null;
      };
   }
}