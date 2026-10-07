package com.linweiyun.genshin.core.system.registry;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.content.attribute.AttributeType;
import com.linweiyun.genshin.content.effect.character.ICharacterEffect;
import com.linweiyun.genshin.content.items.artifact.ArtifactSet;
import com.linweiyun.genshin.core.character.PGCharacter;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

/**
 * 本模组自身的注册表。元素 / 反应 / 状态实例类型的注册表由 elementlib 提供
 * （见 elementlib 的 {@code core.system.registry.ModRegistries}）。
 */
@EventBusSubscriber
public class ModRegistries {

    public static final ResourceKey<Registry<AttributeType>> ATTRIBUTE_TYPE_REGISTRY_KEY =
            ResourceKey.createRegistryKey(
                    ResourceLocation.fromNamespaceAndPath(Minegenshin.MOD_ID, "attribute_types"));

    public static final ResourceKey<Registry<PGCharacter>> CHARACTER_REGISTRY_KEY =
            ResourceKey.createRegistryKey(
                    ResourceLocation.fromNamespaceAndPath(Minegenshin.MOD_ID, "characters"));

    public static final ResourceKey<Registry<ICharacterEffect>> CHARACTER_EFFECT_REGISTRY_KEY =
            ResourceKey.createRegistryKey(
                    ResourceLocation.fromNamespaceAndPath(Minegenshin.MOD_ID, "character_effects"));

    public static final ResourceKey<Registry<ArtifactSet>> ARTIFACT_SET_REGISTRY_KEY =
            ResourceKey.createRegistryKey(
                    ResourceLocation.fromNamespaceAndPath(Minegenshin.MOD_ID, "artifact_sets"));

    // ======== 注册表 ========
    public static final Registry<AttributeType> ATTRIBUTE_TYPE_REGISTRY =
            new RegistryBuilder<>(ATTRIBUTE_TYPE_REGISTRY_KEY)
                    .sync(true)
                    .create();

    public static final Registry<PGCharacter> CHARACTER_REGISTRY =
            new RegistryBuilder<>(CHARACTER_REGISTRY_KEY)
                    .sync(true)
                    .create();

    public static final Registry<ICharacterEffect> CHARACTER_EFFECT_REGISTRY =
            new RegistryBuilder<>(CHARACTER_EFFECT_REGISTRY_KEY)
                    .sync(true)
                    .defaultKey(Minegenshin.id("empty"))
                    .maxId(256)
                    .create();

    public static final Registry<ArtifactSet> ARTIFACT_SET_REGISTRY =
            new RegistryBuilder<>(ARTIFACT_SET_REGISTRY_KEY)
                    .sync(true)
                    .create();

    public static final DeferredRegister<AttributeType> ATTRIBUTE_TYPES =
            DeferredRegister.create(ATTRIBUTE_TYPE_REGISTRY, Minegenshin.MOD_ID);

    public static final DeferredRegister<PGCharacter> CHARACTERS =
            DeferredRegister.create(CHARACTER_REGISTRY, Minegenshin.MOD_ID);

    public static final DeferredRegister<ICharacterEffect> CHARACTER_EFFECTS =
            DeferredRegister.create(CHARACTER_EFFECT_REGISTRY, Minegenshin.MOD_ID);

    public static final DeferredRegister<ArtifactSet> ARTIFACT_SETS =
            DeferredRegister.create(ARTIFACT_SET_REGISTRY, Minegenshin.MOD_ID);

    @SubscribeEvent
    public static void registerRegistries(NewRegistryEvent event) {
        event.register(ATTRIBUTE_TYPE_REGISTRY);
        event.register(CHARACTER_REGISTRY);
        event.register(CHARACTER_EFFECT_REGISTRY);
        event.register(ARTIFACT_SET_REGISTRY);
    }
}
