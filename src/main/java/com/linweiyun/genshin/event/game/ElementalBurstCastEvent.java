package com.linweiyun.genshin.event.game;

import com.linweiyun.elementlib.api.event.ElibIdentifiedEvent;
import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.character.PGCharacter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** 释放元素爆发事件 —— 口径同 {@link ElementalSkillCastEvent}。 */
public final class ElementalBurstCastEvent extends Event implements ElibIdentifiedEvent {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(Minegenshin.MOD_ID, "elemental_burst_cast");

    private final ServerLevel level;
    private final long gameTime;
    private final ServerPlayer player;
    private final PGCharacter character;

    public ElementalBurstCastEvent(ServerLevel level, long gameTime, ServerPlayer player,
                                   PGCharacter character) {
        this.level = level;
        this.gameTime = gameTime;
        this.player = player;
        this.character = character;
    }

    @Override
    public Identifier eventId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "释放爆发";
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

    @Override
    public String toString() {
        return "ElementalBurstCastEvent[char=" + character.getCharacterUUID() + "]";
    }
}