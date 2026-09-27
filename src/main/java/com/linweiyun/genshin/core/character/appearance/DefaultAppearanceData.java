// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character.appearance;

public final class DefaultAppearanceData extends CharacterAppearanceData {
   public static final DefaultAppearanceData INSTANCE = new DefaultAppearanceData();

   private DefaultAppearanceData() {
   }

   @Override
   public int optionCount() {
      return 1;
   }

   @Override
   public String optionNameKey(int index) {
      if (index != 0) {
         throw new IndexOutOfBoundsException("外观项下标越界: " + index);
      } else {
         return "gui.minegenshin.character_config.show_weapon";
      }
   }
}
