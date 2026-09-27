// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.config.character;

import net.neoforged.neoforge.common.ModConfigSpec.Builder;

public class LinweiyunConfig {
   public static void register(Builder builder) {
      builder.push("linweiyun");
      LinweiyunAttributeConfig.register(builder);
      builder.pop();
   }
}
