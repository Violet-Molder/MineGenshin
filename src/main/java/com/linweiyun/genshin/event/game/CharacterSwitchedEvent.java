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
 * 切换角色事件 —— 出战角色从 {@link #from()} 变成 {@link #to()}。
 *
 * <p>广播顺序为退场 → 登场 → 切换；广播时 {@code currentCharacterIndex} 已是新值，
 * 「之前是谁」只从本事件读取。
 */
public final class CharacterSwitchedEvent extends Event implements ElibIdentifiedEvent {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(Minegenshin.MOD_ID, "character_switched");

    private final ServerLevel level;
    private final long gameTime;
    private final ServerPlayer player;
    @Nullable private final PGCharacter from;
    private final PGCharacter to;
    private final int fromIndex;
    private final int toIndex;
    private final SwitchCause cause;

    public CharacterSwitchedEvent(ServerLevel level, long gameTime, ServerPlayer player,
                                  @Nullable PGCharacter from, PGCharacter to,
                                  int fromIndex, int toIndex, SwitchCause cause) {
        this.level = level;
        this.gameTime = gameTime;
        this.player = player;
        this.from = from;
        this.to = to;
        this.fromIndex = fromIndex;
        this.toIndex = toIndex;
        this.cause = cause;
    }

    @Override
    public Identifier eventId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "切换角色";
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

    /** 退场角色；首次登场时为 {@code null}。 */
    @Nullable
    public PGCharacter from() {
        return from;
    }

    public PGCharacter to() {
        return to;
    }

    public int fromIndex() {
        return fromIndex;
    }

    public int toIndex() {
        return toIndex;
    }

    public SwitchCause cause() {
        return cause;
    }

    @Override
    public String toString() {
        return "CharacterSwitchedEvent[" + (from == null ? "-" : from.getCharacterUUID())
                + " -> " + to.getCharacterUUID() + " cause=" + cause + "]";
    }
}