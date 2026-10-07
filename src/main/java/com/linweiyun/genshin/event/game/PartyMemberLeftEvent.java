package com.linweiyun.genshin.event.game;

import com.linweiyun.elementlib.api.event.ElibIdentifiedEvent;
import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.character.PGCharacter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * 角色退出所选队伍事件。
 *
 * <p>{@link #shifted()} 为真表示移出后其它队伍位被前移补齐 —— 缓存队伍构成的监听者此时要整体重建；
 * {@link #currentIndexMoved()} 表示出战角色的队伍位索引是否因此变化。
 */
public final class PartyMemberLeftEvent extends Event implements ElibIdentifiedEvent {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Minegenshin.MOD_ID, "party_member_left");

    private final ServerLevel level;
    private final long gameTime;
    private final ServerPlayer player;
    private final int index;
    private final PGCharacter character;
    private final boolean shifted;
    private final boolean indexMoved;

    public PartyMemberLeftEvent(ServerLevel level, long gameTime, ServerPlayer player,
                                int index, PGCharacter character, boolean shifted,
                                boolean indexMoved) {
        this.level = level;
        this.gameTime = gameTime;
        this.player = player;
        this.index = index;
        this.character = character;
        this.shifted = shifted;
        this.indexMoved = indexMoved;
    }

    @Override
    public ResourceLocation eventId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "退出队伍";
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

    /** 退队前所在的队伍位（0-3）。 */
    public int index() {
        return index;
    }

    /** 退队的角色。 */
    public PGCharacter character() {
        return character;
    }

    /** 其它队伍位是否被前移补齐。 */
    public boolean shifted() {
        return shifted;
    }

    /** 出战角色的队伍位索引是否因此发生了变化。 */
    public boolean currentIndexMoved() {
        return indexMoved;
    }

    @Override
    public String toString() {
        return "PartyMemberLeftEvent[index=" + index + " uuid=" + character.getCharacterUUID()
                + " shifted=" + shifted + "]";
    }
}