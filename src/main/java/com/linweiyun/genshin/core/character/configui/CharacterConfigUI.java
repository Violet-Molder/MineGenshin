// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character.configui;

import com.linweiyun.genshin.core.character.PGCharacter;
import net.minecraft.network.chat.Component;

public class CharacterConfigUI extends CharacterConfigScreen {
   public static final CharacterConfigUI INSTANCE = new CharacterConfigUI();

   @Override
   public Component title() {
      return Component.translatable("gui.minegenshin.character_config.title_generic");
   }

   @Override
   protected Component titlebarText(PGCharacter character) {
      return Component.translatable("gui.minegenshin.character_config.title_format", new Object[]{character.getName()});
   }
}
