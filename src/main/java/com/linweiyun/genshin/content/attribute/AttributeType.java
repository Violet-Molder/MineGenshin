package com.linweiyun.genshin.content.attribute;

import com.linweiyun.genshin.Minegenshin;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import net.minecraft.resources.ResourceLocation;

import com.linweiyun.genshin.core.system.registry.ModRegistries;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

public class AttributeType implements IPersistedSerializable {
    public static final Codec<AttributeType> CODEC = PersistedParser.createCodec(AttributeType::new);
    public static final StreamCodec<ByteBuf, AttributeType> STREAM_CODEC = PersistedParser.createStreamCodec(AttributeType::new);

    // 让 PersistedParser 能识别这些字段
    @Persisted(key = "attr_id")
    private ResourceLocation id;

    @Persisted(key = "attr_translation_key")
    private String translationKey;

    @Persisted(key = "attr_default_value")
    private float defaultValue;

    public AttributeType() {}

    public AttributeType(ResourceLocation id, String translationKey, float defaultValue) {
        this.id = id;
        this.translationKey = translationKey;
        this.defaultValue = defaultValue;
    }

    public AttributeType(String path, String translationKey, float defaultValue) {
        this(ResourceLocation.fromNamespaceAndPath(Minegenshin.MOD_ID, path), translationKey, defaultValue);
    }

    public ResourceLocation id() { return id; }
    public String translationKey() { return translationKey; }
    public float defaultValue() { return defaultValue; }

    public static AttributeType byId(ResourceLocation id) {
        return ModRegistries.ATTRIBUTE_TYPE_REGISTRY.get(id);
    }
}