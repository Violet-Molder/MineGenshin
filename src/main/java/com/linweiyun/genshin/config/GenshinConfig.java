package com.linweiyun.genshin.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import com.linweiyun.genshin.config.character.CharacterConfig;

import com.linweiyun.genshin.config.entity.EntityConfig;
import com.linweiyun.genshin.config.weapon.WeaponConfig;
import com.linweiyun.genshin.config.artifact.ArtifactConfig;
import com.linweiyun.genshin.config.reaction.ReactionConfig;
import com.linweiyun.genshin.core.system.compat.CompatConfig;

public class GenshinConfig {

    public static final ModConfigSpec.Builder CHARACTER_BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.Builder WORLD_TEXT_COLOR_BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.Builder ENTITY_BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.Builder WEAPON_BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.Builder ARTIFACT_BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.Builder REACTION_BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.Builder PERFORMANCE_BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.Builder COMPATIBILITY_BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.Builder POISE_BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec CHARACTER_SPEC;
    public static final ModConfigSpec WORLD_TEXT_COLOR_SPEC;
    public static final ModConfigSpec ENTITY_SPEC;
    public static final ModConfigSpec WEAPON_SPEC;
    public static final ModConfigSpec ARTIFACT_SPEC;
    public static final ModConfigSpec REACTION_SPEC;
    public static final ModConfigSpec PERFORMANCE_SPEC;
    public static final ModConfigSpec COMPATIBILITY_SPEC;
    public static final ModConfigSpec POISE_SPEC;

    static {
        CharacterConfig.register(CHARACTER_BUILDER);
        WorldTextColorConfig.register(WORLD_TEXT_COLOR_BUILDER);
        EntityConfig.register(ENTITY_BUILDER);
        WeaponConfig.register(WEAPON_BUILDER);
        ArtifactConfig.register(ARTIFACT_BUILDER);
        ReactionConfig.register(REACTION_BUILDER);
        PerformanceConfig.register(PERFORMANCE_BUILDER);
        CompatConfig.register(COMPATIBILITY_BUILDER);
        PoiseConfig.register(POISE_BUILDER);

        CHARACTER_SPEC = CHARACTER_BUILDER.build();
        WORLD_TEXT_COLOR_SPEC = WORLD_TEXT_COLOR_BUILDER.build();
        ENTITY_SPEC = ENTITY_BUILDER.build();
        WEAPON_SPEC = WEAPON_BUILDER.build();
        ARTIFACT_SPEC = ARTIFACT_BUILDER.build();
        REACTION_SPEC = REACTION_BUILDER.build();
        PERFORMANCE_SPEC = PERFORMANCE_BUILDER.build();
        COMPATIBILITY_SPEC = COMPATIBILITY_BUILDER.build();
        POISE_SPEC = POISE_BUILDER.build();
    }
}
