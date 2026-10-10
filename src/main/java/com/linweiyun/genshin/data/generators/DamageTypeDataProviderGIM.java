package com.linweiyun.genshin.data.generators;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.system.registry.DamageTypeRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.world.damagesource.DamageEffects;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DeathMessageType;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class DamageTypeDataProviderGIM extends DatapackBuiltinEntriesProvider {

    public DamageTypeDataProviderGIM(
            PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(
                output,
                registries,
                new RegistrySetBuilder()
                        .add(Registries.DAMAGE_TYPE, bootstrap -> {
                            bootstrap.register(
                                    DamageTypeRegistry.NORMAL_ATTACK,
                                    new DamageType(
                                            "normal_attack",
                                            DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER,
                                            0.1f,
                                            DamageEffects.HURT,
                                            DeathMessageType.DEFAULT));
                            bootstrap.register(
                                    DamageTypeRegistry.CHARGED_ATTACK,
                                    new DamageType(
                                            "charged_attack",
                                            DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER,
                                            0.1f,
                                            DamageEffects.HURT,
                                            DeathMessageType.DEFAULT));
                            bootstrap.register(
                                    DamageTypeRegistry.PLUNGING_ATTACK,
                                    new DamageType(
                                            "plunging_attack",
                                            DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER,
                                            0.1f,
                                            DamageEffects.HURT,
                                            DeathMessageType.DEFAULT));
                            bootstrap.register(
                                    DamageTypeRegistry.ELEMENTAL_SKILL,
                                    new DamageType(
                                            "elemental_skill",
                                            DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER,
                                            0.1f,
                                            DamageEffects.HURT,
                                            DeathMessageType.DEFAULT));
                            bootstrap.register(
                                    DamageTypeRegistry.ELEMENTAL_BURST,
                                    new DamageType(
                                            "elemental_burst",
                                            DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER,
                                            0.1f,
                                            DamageEffects.HURT,
                                            DeathMessageType.DEFAULT));
                            bootstrap.register(
                                    DamageTypeRegistry.SPECIAL,
                                    new DamageType(
                                            "special",
                                            DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER,
                                            0.1f,
                                            DamageEffects.HURT,
                                            DeathMessageType.DEFAULT));
                            bootstrap.register(
                                    DamageTypeRegistry.MONSTER,
                                    new DamageType(
                                            "monster",
                                            DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER,
                                            0.1f,
                                            DamageEffects.HURT,
                                            DeathMessageType.DEFAULT
                                            ));
                            bootstrap.register(
                                    DamageTypeRegistry.LUNAR_CHARGED,
                                    new DamageType(
                                            "lunar_charged",
                                            DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER,
                                            0.1f,
                                            DamageEffects.HURT,
                                            DeathMessageType.DEFAULT));
                            bootstrap.register(
                                    DamageTypeRegistry.ELECTRO_CHARGED,
                                    new DamageType(
                                            "electro_charged",
                                            DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER,
                                            0.1f,
                                            DamageEffects.HURT,
                                            DeathMessageType.DEFAULT));
                            bootstrap.register(
                                    DamageTypeRegistry.SWIRL,
                                    new DamageType(
                                            "swirl",
                                            DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER,
                                            0.1f,
                                            DamageEffects.HURT,
                                            DeathMessageType.DEFAULT));
                            bootstrap.register(
                                    DamageTypeRegistry.STELLAR_CONDUCE,
                                    new DamageType(
                                            "stellar_conduce",
                                            DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER,
                                            0.1f,
                                            DamageEffects.HURT,
                                            DeathMessageType.DEFAULT));
                            bootstrap.register(
                                    DamageTypeRegistry.STELLAR_SWIRL,
                                    new DamageType(
                                            "stellar_swirl",
                                            DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER,
                                            0.1f,
                                            DamageEffects.HURT,
                                            DeathMessageType.DEFAULT));
                        }),
                Set.of(Minegenshin.MOD_ID));
    }
}
