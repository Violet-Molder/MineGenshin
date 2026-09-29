package com.linweiyun.genshin.core.character.util.config;

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
