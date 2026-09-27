// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.config.character;

import net.neoforged.neoforge.common.ModConfigSpec.Builder;

public class CharacterConfig {
   public static void register(Builder builder) {
      CharacterXpConfig.register(builder);
      ShenheConfig.register(builder);
      ColumbinaConfig.register(builder);
      ArlecchinoConfig.register(builder);
      LinweiyunConfig.register(builder);
   }
}
