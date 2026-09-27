// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character.allweapon.linweiyun;

import com.linweiyun.genshin.core.character.appearance.WeaponAppearance;
import com.linweiyun.genshin.core.character.talent.SkillBase;
import java.util.Map;

public class LinweiyunSkill extends SkillBase {
   private static final Map<WeaponAppearance, Integer> COMBO = Map.of(
      WeaponAppearance.FIST, 1, WeaponAppearance.SWORD, 0, WeaponAppearance.POLEARM, 1, WeaponAppearance.CATALYST, 1, WeaponAppearance.BOW, 1
   );
   private final WeaponAppearance form;

   public LinweiyunSkill(WeaponAppearance form) {
      this.form = form;
   }

   public WeaponAppearance form() {
      return this.form;
   }

   @Override
   public int getMaxCombo() {
      return COMBO.getOrDefault(this.form, 1);
   }
}
