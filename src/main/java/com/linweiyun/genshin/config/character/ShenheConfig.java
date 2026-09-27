package com.linweiyun.genshin.config.character;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ShenheConfig {
    public static void register(ModConfigSpec.Builder builder) {
        builder.push("shenhe");
        ShenheAttributeConfig.register(builder);
      ShenheTalentConfig.register(builder);
      // 让服务端同步包（按 key 查表）能找到这张表
      TalentConfigs.register(ShenheTalentConfig.SOURCE);
        builder.pop();
    }
}
