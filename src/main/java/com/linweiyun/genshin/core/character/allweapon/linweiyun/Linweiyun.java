// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character.allweapon.linweiyun;

import com.linweiyun.genshin.config.character.LinweiyunAttributeConfig;
import com.linweiyun.genshin.core.character.util.type.CharacterAscendAttribute;
import com.linweiyun.genshin.core.character.allweapon.AllWeaponCharacter;
import com.linweiyun.genshin.core.character.util.appearance.WeaponAppearance;
import com.linweiyun.genshin.core.character.talent.SkillBase;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterActionData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderRepository;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class Linweiyun extends AllWeaponCharacter {
   public static final String ID = "linweiyun";
   public static final int UID = 105001;

   public Linweiyun() {
      super(
              UID,
         5,
         Component.translatable("character.name.linweiyun"),
         ModElements.ANEMO.getId().toString(),
         CharacterAscendAttribute.ATK,
         400,
         400,
         80.0F,
         "linweiyun",
         Map.of(
            ModAttributes.MAX_HP.getId(),
            LinweiyunAttributeConfig::getAllHp,
            ModAttributes.ATK.getId(),
            LinweiyunAttributeConfig::getAllAtk,
            ModAttributes.DEF.getId(),
            LinweiyunAttributeConfig::getAllDef
         )
      );
      // 她自己的配置页（装扮项走 AllWeaponAppearanceData，倍率表走 LinweiyunTalentConfig）
      this.configUI = LinweiyunConfigUI.INSTANCE;
      CharacterRenderRepository.register(LinweiyunResources.RENDER_DATA);
   }

   @Override
   protected SkillBase createFormSkill(WeaponAppearance form) {
      return form == WeaponAppearance.CLAYMORE ? new LinweiyunClaymoreSkill() : new LinweiyunSkill(form);
   }

   @Override
   public CharacterActionData getActionData() {
      if (currentWeaponForm() == WeaponAppearance.POLEARM) {
         return LinweiyunResources.POLEARM_ACTION_DATA;
      }
      return null;
   }

   @Override
   public Map<ResourceLocation, Supplier<List<? extends Integer>>> getStatGrowthMap() {
      return Map.of(
         ModAttributes.MAX_HP.getId(),
         LinweiyunAttributeConfig::getAllHp,
         ModAttributes.ATK.getId(),
         LinweiyunAttributeConfig::getAllAtk,
         ModAttributes.DEF.getId(),
         LinweiyunAttributeConfig::getAllDef
      );
   }
}
