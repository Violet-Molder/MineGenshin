package com.linweiyun.genshin.core.character.attachment;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.system.registry.ModRegistries;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

/**
 * 根据 typeId 从 Minecraft 注册表创建 CharacterAttachment 子类实例的工具类。
 * <p>
 * 对照 StatusInstanceTypes 的 create() 逻辑，typeId 会被转换为 {@code minegenshin:typeId}
 * 的 ResourceLocation 后从 {@link ModRegistries#CHARACTER_ATTACHMENT_TYPE_REGISTRY} 查找。
 */
public final class CharacterAttachmentTypes {
    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);

    private CharacterAttachmentTypes() {}

    public static CharacterAttachment create(String typeId) {
        Identifier id = typeId.contains(":") ? Identifier.parse(typeId) : Minegenshin.id(typeId);
        CharacterAttachmentType<?> type =
                ModRegistries.CHARACTER_ATTACHMENT_TYPE_REGISTRY.getOptional(id).orElse(null);
        if (type != null && type.constructor() != null) {
            return type.constructor().get();
        }
        LOGGER.warn("[CharacterAttachmentTypes] 未找到 typeId='{}' (id={}) 的注册条目", typeId, id);
        return null;
    }
}