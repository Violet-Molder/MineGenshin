// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character.allweapon;

import com.linweiyun.genshin.content.items.artifact.inventory.AllWeaponArtifactInventory;
import com.linweiyun.genshin.content.items.weapon.WeaponItem;
import com.linweiyun.genshin.core.character.util.type.CharacterAscendAttribute;
import com.linweiyun.genshin.core.character.util.type.CharacterWeaponClass;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.util.appearance.WeaponAppearance;
import com.linweiyun.genshin.core.character.claymore.ClaymoreSkill;
import com.linweiyun.genshin.core.character.talent.SkillBase;
import com.linweiyun.genshin.core.system.poise.WeaponPoiseTable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public abstract class AllWeaponCharacter extends PGCharacter {
   private final SkillBase[] formSkills = new SkillBase[WeaponAppearance.values().length];

   protected AllWeaponCharacter(
      int uuid,
      int rarity,
      Component name,
      String elementId,
      CharacterAscendAttribute ascendAttribute,
      int maxStamina,
      int staminaRecovery,
      float energyMax,
      String resourceId,
      Map<Identifier, Supplier<List<? extends Integer>>> statGrowth
   ) {
      super(uuid, rarity, name, elementId, ascendAttribute, maxStamina, staminaRecovery, energyMax, resourceId, statGrowth);
      this.appearanceData = AllWeaponAppearanceData.INSTANCE;
      this.getData().installArtifactInventory(new AllWeaponArtifactInventory());
   }

   @Override
   public Class<? extends WeaponItem> getAllowedWeaponClass() {
      return WeaponItem.class;
   }

   @Override
   public CharacterWeaponClass characterWeaponClass() {
      return CharacterWeaponClass.ALL_WEAPON;
   }

   @Override
   public WeaponPoiseTable.WeaponClass currentWeaponType() {
      return switch (this.currentWeaponForm()) {
         case FIST -> WeaponPoiseTable.WeaponClass.FIST;
         case SWORD -> WeaponPoiseTable.WeaponClass.SWORD;
         case POLEARM -> WeaponPoiseTable.WeaponClass.POLEARM;
         case CLAYMORE -> WeaponPoiseTable.WeaponClass.CLAYMORE;
         case CATALYST -> WeaponPoiseTable.WeaponClass.CATALYST;
         case BOW -> WeaponPoiseTable.WeaponClass.BOW;
      };
   }

   public WeaponAppearance currentWeaponForm() {
      return WeaponAppearance.byOrdinal(this.appearanceData().weaponSlotOffset(this.getAppearance()));
   }

   @Nullable
   @Override
   public SkillBase getSkill() {
      int index = this.currentWeaponForm().ordinal();
      SkillBase cached = this.formSkills[index];
      if (cached == null) {
         cached = this.createFormSkill(this.currentWeaponForm());
         this.formSkills[index] = cached;
      }

      return cached;
   }

   protected SkillBase createFormSkill(WeaponAppearance form) {
      return form == WeaponAppearance.CLAYMORE ? new ClaymoreSkill() : new SkillBase();
   }

   @Override
   public String getActionStateKey(Player player) {
      return "form_" + this.currentWeaponForm().name().toLowerCase(Locale.ROOT);
   }
}
