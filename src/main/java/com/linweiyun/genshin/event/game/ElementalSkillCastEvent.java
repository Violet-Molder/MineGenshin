package com.linweiyun.genshin.event.game;

import com.linweiyun.elementlib.api.event.ElibIdentifiedEvent;
import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.system.combat.action.ActionKind;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * 释放元素战技事件 —— 请求被接受、CD 被扣、动作进入 tick 0 时广播；前摇被打断也算释放过。
 */
public final class ElementalSkillCastEvent extends Event implements ElibIdentifiedEvent {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Minegenshin.MOD_ID, "elemental_skill_cast");

    private final ServerLevel level;
    private final long gameTime;
    private final ServerPlayer player;
    private final PGCharacter character;
    private final ActionKind kind;
    private final boolean longPress;
    private final int skillTime;

    public ElementalSkillCastEvent(ServerLevel level, long gameTime, ServerPlayer player,
                                   PGCharacter character, ActionKind kind,
                                   boolean longPress, int skillTime) {
        this.level = level;
        this.gameTime = gameTime;
        this.player = player;
        this.character = character;
        this.kind = kind;
        this.longPress = longPress;
        this.skillTime = skillTime;
    }

    @Override
    public ResourceLocation eventId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "释放战技";
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

    /** {@code ELEMENTAL_SKILL_TAP} 或 {@code ELEMENTAL_SKILL_HOLD}。 */
    public ActionKind kind() {
        return kind;
    }

    public boolean longPress() {
        return longPress;
    }

    /** 客户端原始输入里的长按时间。 */
    public int skillTime() {
        return skillTime;
    }

    @Override
    public String toString() {
        return "ElementalSkillCastEvent[char=" + character.getCharacterUUID()
                + " kind=" + kind + "]";
    }
}