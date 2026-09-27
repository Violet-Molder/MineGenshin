// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character.appearance;

import com.linweiyun.genshin.core.system.poise.WeaponPoiseTable;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class CharacterAppearanceData {
   private static final Map<WeaponPoiseTable.WeaponClass, String> SHARED_WEAPON_BONES = Map.of(
      WeaponPoiseTable.WeaponClass.SWORD,
      "sword",
      WeaponPoiseTable.WeaponClass.CLAYMORE,
      "claymore",
      WeaponPoiseTable.WeaponClass.POLEARM,
      "long",
      WeaponPoiseTable.WeaponClass.CATALYST,
      "magic",
      WeaponPoiseTable.WeaponClass.BOW,
      "bow"
   );
   public static final int SHOW_WEAPON_SHIFT = 7;
   public static final int SHOW_WEAPON_OPTION = 0;

   public int defaults() {
      return 0;
   }

   public boolean showWeapon(int mask) {
      return (mask >> 7 & 1) != 0;
   }

   public int withShowWeapon(int mask, boolean show) {
      int cleared = mask & -129;
      return cleared | (show ? 1 : 0) << 7;
   }

   public int weaponSlotOffset(int mask) {
      return 0;
   }

   public abstract int optionCount();

   public abstract String optionNameKey(int var1);

   public boolean optionHandWritten(int index) {
      return false;
   }

   public CharacterAppearanceData.OptionKind optionKind(int index) {
      return CharacterAppearanceData.OptionKind.TOGGLE;
   }

   public int optionValueCount(int index) {
      return 0;
   }

   public String optionValueNameKey(int index, int value) {
      return "";
   }

   public int optionValue(int mask, int index) {
      return index == 0 && this.showWeapon(mask) ? 1 : 0;
   }

   public int withOptionValue(int mask, int index, int value) {
      return index == 0 ? this.withShowWeapon(mask, value != 0) : mask;
   }

   public Map<String, Boolean> normalStateBones(int mask, WeaponPoiseTable.WeaponClass weaponType) {
      boolean show = this.showWeapon(mask);
      String shown = SHARED_WEAPON_BONES.get(weaponType);
      Map<String, Boolean> bones = new LinkedHashMap<>();

      for (String bone : SHARED_WEAPON_BONES.values()) {
         bones.put(bone, show && bone.equals(shown));
      }

      // 放飞骨骼（飞行时武器脱离手的那几根）常态一律藏着 —— 亮起来由飞行分支负责
      for (String flightBone : this.flightBones().values()) {
         bones.put(flightBone, false);
      }

      return bones;
   }

   /**
    * <b>放飞骨骼</b>：常态骨骼名 → 飞行时用来挂同一把武器的另一根骨骼名（默认没有）。
    *
    * <p>用来解决「近战武器挂在手上、但飞行时武器要和手分离」这件事：模型里再复制一根
    * 挂在身体（而不是手）上的骨骼，飞行时把手骨藏掉、把放飞骨骼亮起来 —— 于是武器的位姿
    * 不再被手臂的动画拖着走。挂点、显隐切换见
    * {@code LinweiyunResources} 与 {@code CharacterAppearanceOptionBones}。
    *
    * <p>键值都是<b>模型骨骼名</b>，所以是按模型给的口径（默认空 = 这个角色的武器一直挂在手上）。
    */
   public Map<String, String> flightBones() {
      return Map.of();
   }

   public enum OptionKind {
      TOGGLE,
      CHOICE;
   }
}
