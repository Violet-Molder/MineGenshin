package com.linweiyun.genshin.event.game;

import com.linweiyun.elementlib.api.event.ElibIdentifiedEvent;
import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.system.combat.damage.DamageOutcome;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

/**
 * 造成实际伤害事件 —— 目标血量数值真的变小了才广播；被护盾全挡下、被元素免疫都不发。
 */
public final class DamageDealtEvent extends Event implements ElibIdentifiedEvent {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Minegenshin.MOD_ID, "damage_dealt");

    private final ServerLevel level;
    private final long gameTime;
    private final DamageOutcome outcome;
    private final ModDamageSpec spec;
    @Nullable private final Entity attacker;
    @Nullable private final PGCharacter attackerCharacter;
    private final LivingEntity target;
    @Nullable private final PGCharacter targetCharacter;

    public DamageDealtEvent(ServerLevel level, long gameTime, DamageOutcome outcome,
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
    public ResourceLocation eventId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "造成伤害";
    }

    public ServerLevel level() {
        return level;
    }

    public long gameTime() {
        return gameTime;
    }

    /** 两阶段结果（这一条固定是 {@code damaged == true}）。 */
    public DamageOutcome outcome() {
        return outcome;
    }

    public ModDamageSpec spec() {
        return spec;
    }

    @Nullable
    public Entity attacker() {
        return attacker;
    }

    @Nullable
    public PGCharacter attackerCharacter() {
        return attackerCharacter;
    }

    public LivingEntity target() {
        return target;
    }

    @Nullable
    public PGCharacter targetCharacter() {
        return targetCharacter;
    }

    @Override
    public String toString() {
        return "DamageDealtEvent[atk=" + spec.getAttackType() + " dmg=" + outcome.finalDamage()
                + " crit=" + spec.isCrit() + " killed=" + outcome.killed() + "]";
    }
}