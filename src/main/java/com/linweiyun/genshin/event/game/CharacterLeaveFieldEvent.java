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
 * 角色退场事件 —— 该角色不再出战；{@link #next()} 为 {@code null} 表示没有接替者。
 */
public final class CharacterLeaveFieldEvent extends Event implements ElibIdentifiedEvent {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(Minegenshin.MOD_ID, "character_leave_field");

    private final ServerLevel level;
    private final long gameTime;
    private final ServerPlayer player;
    private final PGCharacter character;
    @Nullable private final PGCharacter next;
    private final SwitchCause cause;

    public CharacterLeaveFieldEvent(ServerLevel level, long gameTime, ServerPlayer player,
                                    PGCharacter character, @Nullable PGCharacter next,
                                    SwitchCause cause) {
        this.level = level;
        this.gameTime = gameTime;
        this.player = player;
        this.character = character;
        this.next = next;
        this.cause = cause;
    }

    @Override
    public Identifier eventId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "角色退场";
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

    /** 退场的那个角色。 */
    public PGCharacter character() {
        return character;
    }

    /** 接替出场的角色；没有接替者时为 {@code null}。 */
    @Nullable
    public PGCharacter next() {
        return next;
    }

    public SwitchCause cause() {
        return cause;
    }

    @Override
    public String toString() {
        return "CharacterLeaveFieldEvent[" + character.getCharacterUUID() + " cause=" + cause + "]";
    }
}