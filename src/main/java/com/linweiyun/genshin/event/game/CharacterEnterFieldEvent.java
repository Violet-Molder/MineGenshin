package com.linweiyun.genshin.event.game;

import com.linweiyun.elementlib.api.event.ElibIdentifiedEvent;
import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.character.PGCharacter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

/**
 * 角色登场事件 —— 该角色成为出战角色；首次登场时 {@link #previous()} 为 {@code null}。
 */
public final class CharacterEnterFieldEvent extends Event implements ElibIdentifiedEvent {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Minegenshin.MOD_ID, "character_enter_field");

    private final ServerLevel level;
    private final long gameTime;
    private final ServerPlayer player;
    private final PGCharacter character;
    @Nullable private final PGCharacter previous;
    private final SwitchCause cause;

    public CharacterEnterFieldEvent(ServerLevel level, long gameTime, ServerPlayer player,
                                    PGCharacter character, @Nullable PGCharacter previous,
                                    SwitchCause cause) {
        this.level = level;
        this.gameTime = gameTime;
        this.player = player;
        this.character = character;
        this.previous = previous;
        this.cause = cause;
    }

    @Override
    public ResourceLocation eventId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "角色登场";
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

    public PGCharacter character() {
        return character;
    }

    /** 换下去的那个角色；首次登场时为 {@code null}。 */
    @Nullable
    public PGCharacter previous() {
        return previous;
    }

    public SwitchCause cause() {
        return cause;
    }

    @Override
    public String toString() {
        return "CharacterEnterFieldEvent[" + character.getCharacterUUID() + " cause=" + cause + "]";
    }
}