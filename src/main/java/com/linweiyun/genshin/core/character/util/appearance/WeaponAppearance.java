// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character.util.appearance;

public enum WeaponAppearance {
   FIST,
   SWORD,
   POLEARM,
   CLAYMORE,
   CATALYST,
   BOW;

   private static final WeaponAppearance[] VALUES = values();

   public static WeaponAppearance byOrdinal(int ordinal) {
      return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : FIST;
   }

   public String nameKey() {
      return "gui.minegenshin.character_config.weapon." + this.name().toLowerCase();
   }
}
