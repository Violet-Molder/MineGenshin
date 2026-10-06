package com.linweiyun.genshin.core.character.util;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * 角色来源键（{@code 角色UUID::类名}）—— 附着记「谁挂的」、反应查「谁挂的」都用这一个格式。
 */
public final class CharacterKeys {

    private CharacterKeys() {
    }

    public static String keyOf(PGCharacter character) {
        return character.getCharacterUUID() + "::" + character.getClass().getSimpleName();
    }

    /** 从键回到角色（遍历维度内所有玩家队伍）。 */
    @Nullable
    public static PGCharacter resolve(@Nullable ServerLevel level, @Nullable String key) {
        if (level == null || key == null || key.isEmpty()) {
            return null;
        }
        String uuidPart = key.split("::", 2)[0];
        int uuid;
        try {
            uuid = Integer.parseInt(uuidPart);
        } catch (NumberFormatException e) {
            return null;
        }
        for (ServerPlayer player : level.players()) {
            PlayerCharactersAttachment att = player.getData(
                    AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            if (att == null) continue;
            PGCharacter ch = att.getCharacterByUUID(uuid);
            if (ch != null) return ch;
        }
        return null;
    }
}
