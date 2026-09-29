// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character.util.appearance;

import com.linweiyun.genshin.core.system.poise.WeaponPoiseTable;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class ShenheAppearanceData extends CharacterAppearanceData {
   public static final ShenheAppearanceData INSTANCE = new ShenheAppearanceData();
   public static final int OPTION_LEG_LEFT = 0;
   public static final int OPTION_LEG_RIGHT = 1;
   public static final int OPTION_CAT_EARS = 2;
   public static final int OPTION_FJO = 3;
   public static final int OPTION_WEAPON = 4;
   private static final int FJO_SHIFT = 8;
   private static final String MAIN_HAND_BONE = "RightSword";
   private static final String OFF_HAND_BONE = "LeftSword";
   private static final String FJO_BONE = "FJO";

   private ShenheAppearanceData() {
   }

   public SockType sock(int mask, boolean left) {
      return CharacterAppearance.sock(mask, left);
   }

   public boolean shoes(int mask, boolean left) {
      return CharacterAppearance.shoes(mask, left);
   }

   public int withSock(int mask, boolean left, SockType sock) {
      return CharacterAppearance.withSock(mask, left, sock);
   }

   public int withShoes(int mask, boolean left, boolean shoes) {
      return CharacterAppearance.withShoes(mask, left, shoes);
   }

   public boolean catEarsVisible(int mask) {
      return !CharacterAppearance.catEarsHidden(mask);
   }

   public int withCatEarsVisible(int mask, boolean visible) {
      return CharacterAppearance.withCatEarsHidden(mask, !visible);
   }

   @Override
   public int optionCount() {
      return 5;
   }

   @Override
   public String optionNameKey(int index) {
      return switch (index) {
         case 0 -> "gui.minegenshin.character_config.leg_left";
         case 1 -> "gui.minegenshin.character_config.leg_right";
         case 2 -> "gui.minegenshin.character_config.cat_ears";
         case 3 -> "gui.minegenshin.character_config.fjo";
         case 4 -> "gui.minegenshin.character_config.weapon";
         default -> throw new IndexOutOfBoundsException("外观项下标越界: " + index);
      };
   }

   @Override
   public CharacterAppearanceData.OptionKind optionKind(int index) {
      return index != 0 && index != 1 ? CharacterAppearanceData.OptionKind.TOGGLE : CharacterAppearanceData.OptionKind.CHOICE;
   }

   @Override
   public boolean optionHandWritten(int index) {
      return index == 0 || index == 1 || index == 2;
   }

   @Override
   public int optionValueCount(int index) {
      return index != 0 && index != 1 ? 0 : SockType.values().length;
   }

   @Override
   public String optionValueNameKey(int index, int value) {
      return index != 0 && index != 1
         ? ""
         : "gui.minegenshin.character_config.sock."
            + SockType.values()[Math.max(0, Math.min(value, SockType.values().length - 1))].name().toLowerCase(Locale.ROOT);
   }

   @Override
   public int optionValue(int mask, int index) {
      return switch (index) {
         case 0 -> this.sock(mask, true).ordinal();
         case 1 -> this.sock(mask, false).ordinal();
         case 2 -> this.catEarsVisible(mask) ? 1 : 0;
         case 3 -> this.fjoVisible(mask) ? 1 : 0;
         case 4 -> this.showWeapon(mask) ? 1 : 0;
         default -> throw new IndexOutOfBoundsException("外观项下标越界: " + index);
      };
   }

   @Override
   public int withOptionValue(int mask, int index, int value) {
      return switch (index) {
         case 0 -> this.withSock(mask, true, SockType.values()[Math.max(0, Math.min(value, SockType.values().length - 1))]);
         case 1 -> this.withSock(mask, false, SockType.values()[Math.max(0, Math.min(value, SockType.values().length - 1))]);
         case 2 -> this.withCatEarsVisible(mask, value != 0);
         case 3 -> this.withFjoVisible(mask, value != 0);
         case 4 -> this.withShowWeapon(mask, value != 0);
         default -> throw new IndexOutOfBoundsException("外观项下标越界: " + index);
      };
   }

   public boolean fjoVisible(int mask) {
      return (mask >> 8 & 1) != 0;
   }

   public int withFjoVisible(int mask, boolean visible) {
      int cleared = mask & -257;
      return cleared | (visible ? 1 : 0) << 8;
   }

   @Override
   public Map<String, Boolean> normalStateBones(int mask, WeaponPoiseTable.WeaponClass weaponType) {
      boolean weapon = this.showWeapon(mask);
      Map<String, Boolean> bones = new LinkedHashMap<>();
      bones.put("RightSword", weapon);
      bones.put("LeftSword", weapon);
      bones.put("FJO", this.fjoVisible(mask));
      return bones;
   }
}
