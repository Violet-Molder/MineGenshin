// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character.polearm.shenhe;

import com.linweiyun.genshin.config.character.ShenheAttributeConfig;
import com.linweiyun.genshin.core.character.CharacterAscendAttribute;
import com.linweiyun.genshin.core.character.IStellarStateHolder;
import com.linweiyun.genshin.core.character.appearance.ShenheAppearanceData;
import com.linweiyun.genshin.core.character.claymore.ClaymoreCharacter;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterActionData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderRepository;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

public class Shenhe extends ClaymoreCharacter implements IStellarStateHolder {
   private static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);
   public static final String ID = "shenhe";

   public Shenhe() {
      super(
         135001,
         5,
         Component.translatable("character.name.shenhe"),
         ModElements.CYRO.getId().toString(),
         CharacterAscendAttribute.ATK,
         200,
         300,
         200,
         80.0F,
         "shenhe",
         Map.of(
            ModAttributes.MAX_HP.getId(),
            ShenheAttributeConfig::getAllHp,
            ModAttributes.ATK.getId(),
            ShenheAttributeConfig::getAllAtk,
            ModAttributes.DEF.getId(),
            ShenheAttributeConfig::getAllDef
         )
      );
      this.skill = new ShenheSkill();
      this.talent = new ShenheTalent();
      this.constellation = new ShenheConstellation();
      this.configUI = new ShenheConfigUI();
      this.appearanceData = ShenheAppearanceData.INSTANCE;
      CharacterRenderRepository.register(ShenheResources.RENDER_DATA);
   }

   @Override
   public CharacterActionData getActionData() {
      return ShenheResources.ACTION_DATA;
   }

   @Override
   public boolean runsTalentOnClient() {
      return true;
   }

   @Override
   public Map<Identifier, Supplier<List<? extends Integer>>> getStatGrowthMap() {
      return Map.of(
         ModAttributes.MAX_HP.getId(),
         ShenheAttributeConfig::getAllHp,
         ModAttributes.ATK.getId(),
         ShenheAttributeConfig::getAllAtk,
         ModAttributes.DEF.getId(),
         ShenheAttributeConfig::getAllDef
      );
   }
}
