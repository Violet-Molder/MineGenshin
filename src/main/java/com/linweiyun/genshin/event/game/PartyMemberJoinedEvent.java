package com.linweiyun.genshin.event.game;

import com.linweiyun.elementlib.api.event.ElibIdentifiedEvent;
import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.character.PGCharacter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

/**
 * 角色进入所选队伍事件 —— 某个队伍位被写入角色；{@link #shifted()} 表示这次写入是否挪动了其它队伍位。
 */
public final class PartyMemberJoinedEvent extends Event implements ElibIdentifiedEvent {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(Minegenshin.MOD_ID, "party_member_joined");

    private final ServerLevel level;
    private final long gameTime;
    private final ServerPlayer player;
    private final int index;
    private final PGCharacter character;
    @Nullable private final PGCharacter replaced;
    private final boolean shifted;

    public PartyMemberJoinedEvent(ServerLevel level, long gameTime, ServerPlayer player,
                                  int index, PGCharacter character,
                                  @Nullable PGCharacter replaced, boolean shifted) {
        this.level = level;
        this.gameTime = gameTime;
        this.player = player;
        this.index = index;
        this.character = character;
        this.replaced = replaced;
        this.shifted = shifted;
    }

    @Override
    public Identifier eventId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "进入队伍";
    }

    public ServerLevel level() {
        return level;
    }

    public long gameTime() {
        return gameTime;
    }

    public ServerPlayer player() {
        return player;
    }

    /** 队伍位（0-3）。 */
    public int index() {
        return index;
    }

    /** 进队的角色。 */
    public PGCharacter character() {
        return character;
    }

    /** 这个位置原来的人；空位进人时为 {@code null}。 */
    @Nullable
    public PGCharacter replaced() {
        return replaced;
    }

    /** 这次写入是否顺带挪动了其它队伍位（例如把重复角色换到别的位置）。 */
    public boolean shifted() {
        return shifted;
    }

    @Override
    public String toString() {
        return "PartyMemberJoinedEvent[index=" + index + " uuid=" + character.getCharacterUUID() + "]";
    }
}