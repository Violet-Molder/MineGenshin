// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.config.character;

import net.neoforged.neoforge.common.ModConfigSpec.Builder;

public class LinweiyunConfig {
   public static void register(Builder builder) {
      builder.push("linweiyun");
      LinweiyunAttributeConfig.register(builder);
      // 技能倍率表（照抄申鹤那份，key 带 lwy- 前缀）—— 配置页的「技能倍率」页签读它
      LinweiyunTalentConfig.register(builder);
      // 让服务端同步包（按 key 查表）能找到这张表
      TalentConfigs.register(LinweiyunTalentConfig.SOURCE);
      builder.pop();
   }
}
