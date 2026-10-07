package com.linweiyun.genshin.event.game;

import com.linweiyun.elementlib.api.event.ElibIdentifiedEvent;
import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.system.combat.damage.DamageOutcome;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

/**
 * 伤害结算完成事件 —— 数值算完、还没落到血量上。
 *
 * <p>{@code outcome.hit()} 恒为真，没掉血的原因见 {@code outcome.blockReason()}；
 * 「真的掉血了」是 {@link DamageDealtEvent}。
 */
public final class DamageCalculatedEvent extends Event implements ElibIdentifiedEvent {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(Minegenshin.MOD_ID, "damage_calculated");

    private final ServerLevel level;
    private final long gameTime;
    private final DamageOutcome outcome;
    private final ModDamageSpec spec;
    @Nullable private final Entity attacker;
    @Nullable private final PGCharacter attackerCharacter;
    private final LivingEntity target;
    @Nullable private final PGCharacter targetCharacter;

    public DamageCalculatedEvent(ServerLevel level, long gameTime, DamageOutcome outcome,
                                 ModDamageSpec spec, @Nullable Entity attacker,
                                 @Nullable PGCharacter attackerCharacter, LivingEntity target,
                                 @Nullable PGCharacter targetCharacter) {
        this.level = level;
        this.gameTime = gameTime;
        this.outcome = outcome;
        this.spec = spec;
        this.attacker = attacker;
        this.attackerCharacter = attackerCharacter;
        this.target = target;
        this.targetCharacter = targetCharacter;
    }

    @Override
    public Identifier eventId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "伤害结算";
    }

    public ServerLevel level() {
        return level;
    }

    public long gameTime() {
        return gameTime;
    }

    /** 两阶段结果（这一条固定是 {@code hit == true}）。 */
    public DamageOutcome outcome() {
        return outcome;
    }

    /** 这一下伤害的完整规格：攻击类型、元素、元素量、反应类型、是否暴击。 */
    public ModDamageSpec spec() {
        return spec;
    }

    /** 直接造成伤害的实体（抛射物场景下可能就是抛射物自己）。 */
    @Nullable
    public Entity attacker() {
        return attacker;
    }

    /** 攻击者角色；怪物 / 环境伤害时为 {@code null}。 */
    @Nullable
    public PGCharacter attackerCharacter() {
        return attackerCharacter;
    }

    public LivingEntity target() {
        return target;
    }

    /** 目标也是原神模式玩家时非空。 */
    @Nullable
    public PGCharacter targetCharacter() {
        return targetCharacter;
    }

    @Override
    public String toString() {
        return "DamageCalculatedEvent[atk=" + spec.getAttackType() + " raw=" + outcome.rawDamage()
                + " shield=" + outcome.shieldAbsorbed() + " final=" + outcome.finalDamage()
                + " reason=" + outcome.blockReason() + "]";
    }
}