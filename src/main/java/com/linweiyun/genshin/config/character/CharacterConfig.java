package com.linweiyun.genshin.config.character;

import net.neoforged.neoforge.common.ModConfigSpec;

public class CharacterConfig {
    public static void register(ModConfigSpec.Builder builder) {
        CharacterXpConfig.register(builder);

        ShenheConfig.register(builder);
        ColumbinaConfig.register(builder);
        ArlecchinoConfig.register(builder);
        LinweiyunConfig.register(builder);
    }
}
