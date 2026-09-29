package com.linweiyun.genshin.core.system.registry;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.content.effect.character.ICharacterEffect;
import com.linweiyun.genshin.content.attribute.AttributeType;
import com.linweiyun.genshin.content.items.artifact.ArtifactSet;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.element.GenshinElement;
import com.linweiyun.genshin.core.status.StatusInstanceType;
import com.linweiyun.genshin.core.system.reaction.ElementalReaction;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

@EventBusSubscriber
public class ModRegistries {
    // 属性类型注册表实例 —— 存储所有注册的属性类型（如生命值、攻击伤害等）
    public static final ResourceKey<Registry<AttributeType>> ATTRIBUTE_TYPE_REGISTRY_KEY =
            ResourceKey.createRegistryKey(
                    Identifier.fromNamespaceAndPath(Minegenshin.MOD_ID, "attribute_types"));


    public static final ResourceKey<Registry<PGCharacter>> CHARACTER_REGISTRY_KEY =
            ResourceKey.createRegistryKey(
                    Identifier.fromNamespaceAndPath(Minegenshin.MOD_ID, "characters"));


    public static final ResourceKey<Registry<ICharacterEffect>> CHARACTER_EFFECT_REGISTRY_KEY =
            ResourceKey.createRegistryKey(
                    Identifier.fromNamespaceAndPath(Minegenshin.MOD_ID, "character_effects"));

    public static final ResourceKey<Registry<ElementalReaction>> REACTION_REGISTRY_KEY =
            ResourceKey.createRegistryKey(
                    Identifier.fromNamespaceAndPath(Minegenshin.MOD_ID, "elemental_reactions"));

    public static final ResourceKey<Registry<ArtifactSet>> ARTIFACT_SET_REGISTRY_KEY =
            ResourceKey.createRegistryKey(
                    Identifier.fromNamespaceAndPath(Minegenshin.MOD_ID, "artifact_sets"));

    public static final ResourceKey<Registry<StatusInstanceType<?>>> STATUS_INSTANCE_TYPE_REGISTRY_KEY =
            ResourceKey.createRegistryKey(
                    Identifier.fromNamespaceAndPath(Minegenshin.MOD_ID, "status_instance_types"));

    public static final ResourceKey<Registry<GenshinElement>> ELEMENT_REGISTRY_KEY =
            ResourceKey.createRegistryKey(
                    Identifier.fromNamespaceAndPath(Minegenshin.MOD_ID, "elements"));

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

    public static final Registry<ElementalReaction> ELEMENTAL_REACTIONS_REGISTRY =
            new RegistryBuilder<>(REACTION_REGISTRY_KEY)
                    .sync(true)
                    .maxId(64)
                    .create();

    public static final Registry<ArtifactSet> ARTIFACT_SET_REGISTRY =
            new RegistryBuilder<>(ARTIFACT_SET_REGISTRY_KEY)
                    .sync(true)
                    .create();

    public static final Registry<StatusInstanceType<?>> STATUS_INSTANCE_TYPE_REGISTRY =
            new RegistryBuilder<>(STATUS_INSTANCE_TYPE_REGISTRY_KEY)
                    .sync(true)
                    .maxId(256)
                    .create();

    public static final Registry<GenshinElement> ELEMENT_REGISTRY =
            new RegistryBuilder<>(ELEMENT_REGISTRY_KEY)
                    .sync(true)
                    .maxId(32)
                    .create();

    public static final DeferredRegister<AttributeType> ATTRIBUTE_TYPES =
            DeferredRegister.create(ATTRIBUTE_TYPE_REGISTRY, Minegenshin.MOD_ID);

    public static final DeferredRegister<PGCharacter> CHARACTERS =
            DeferredRegister.create(CHARACTER_REGISTRY, Minegenshin.MOD_ID);

    public static final DeferredRegister<ICharacterEffect> CHARACTER_EFFECTS =
            DeferredRegister.create(CHARACTER_EFFECT_REGISTRY, Minegenshin.MOD_ID);

    public static final DeferredRegister<ElementalReaction> ELEMENTAL_REACTIONS =
            DeferredRegister.create(ELEMENTAL_REACTIONS_REGISTRY, Minegenshin.MOD_ID);

    public static final DeferredRegister<ArtifactSet> ARTIFACT_SETS =
            DeferredRegister.create(ARTIFACT_SET_REGISTRY, Minegenshin.MOD_ID);

    public static final DeferredRegister<StatusInstanceType<?>> STATUS_INSTANCE_TYPES =
            DeferredRegister.create(STATUS_INSTANCE_TYPE_REGISTRY, Minegenshin.MOD_ID);

    public static final DeferredRegister<GenshinElement> ELEMENTS =
            DeferredRegister.create(ELEMENT_REGISTRY, Minegenshin.MOD_ID);

    @SubscribeEvent
    public static void registerRegistries(NewRegistryEvent event) {
        event.register(ATTRIBUTE_TYPE_REGISTRY);
        event.register(ELEMENT_REGISTRY);
        event.register(CHARACTER_REGISTRY);
        event.register(CHARACTER_EFFECT_REGISTRY);
        event.register(ELEMENTAL_REACTIONS_REGISTRY);
        event.register(ARTIFACT_SET_REGISTRY);
        event.register(STATUS_INSTANCE_TYPE_REGISTRY);
    }
}