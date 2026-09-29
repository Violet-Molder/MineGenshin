// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character.fist;

import com.linweiyun.genshin.content.items.weapon.WeaponItem;
import com.linweiyun.genshin.content.items.weapon.fist.Fist;
import com.linweiyun.genshin.core.character.util.type.CharacterAscendAttribute;
import com.linweiyun.genshin.core.character.PGCharacter;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public abstract class FistCharacter extends PGCharacter {
   protected FistCharacter(
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
   }

   @Override
   public Class<? extends WeaponItem> getAllowedWeaponClass() {
      return Fist.class;
   }
}
