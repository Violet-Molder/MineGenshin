package com.linweiyun.genshin.content.entities.teyvat;

import com.linweiyun.genshin.content.attribute.AttributeContainer;
import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

public class TeyvatEntityStats implements IPersistedSerializable {
    private static final float DEFAULT_RES = 0.10f;

    @Persisted(key = "level")
    private int level;

    @Persisted(key = "combat_ticks")
    private int combatTicks;

    @Persisted(key = "targeting")
    private boolean targeting;

    @Persisted(key = "environment_multiplier")
    private float environmentMultiplier;

    @Persisted(key = "attributes")
    private AttributeContainer attributes;

    public TeyvatEntityStats() {
        this.level = 0;
        this.combatTicks = 0;
        this.targeting = false;
        this.environmentMultiplier = 1.0f;
        this.attributes = createDefaultAttributes();
    }

    public TeyvatEntityStats(int level, int combatTicks, boolean targeting,
                             float environmentMultiplier, AttributeContainer attributes) {
        this.level = level;
        this.combatTicks = combatTicks;
        this.targeting = targeting;
        this.environmentMultiplier = environmentMultiplier;
        this.attributes = attributes;
    }

    private static AttributeContainer createDefaultAttributes() {
        AttributeContainer container = new AttributeContainer();
        container.setBaseValue(ModAttributes.ATK.get(), 0);
        container.setBaseValue(ModAttributes.DEF.get(), 0);
        container.setBaseValue(ModAttributes.PHYSICAL_RES.get(), DEFAULT_RES);
        container.setBaseValue(ModAttributes.PYRO_RES.get(), DEFAULT_RES);
        container.setBaseValue(ModAttributes.HYDRO_RES.get(), DEFAULT_RES);
        container.setBaseValue(ModAttributes.ANEMO_RES.get(), DEFAULT_RES);
        container.setBaseValue(ModAttributes.ELECTRO_RES.get(), DEFAULT_RES);
        container.setBaseValue(ModAttributes.DENDRO_RES.get(), DEFAULT_RES);
        container.setBaseValue(ModAttributes.CYRO_RES.get(), DEFAULT_RES);
        container.setBaseValue(ModAttributes.GEO_RES.get(), DEFAULT_RES);
        return container;
    }

    public static final TeyvatEntityStats DEFAULT = new TeyvatEntityStats(
            0, 0, false, 1.0f, createDefaultAttributes()
    );

    public static final Codec<TeyvatEntityStats> CODEC = PersistedParser.createCodec(TeyvatEntityStats::new);
    public static final StreamCodec<ByteBuf, TeyvatEntityStats> STREAM_CODEC = PersistedParser.createStreamCodec(TeyvatEntityStats::new);

    public int level() { return level; }
    public int combatTicks() { return combatTicks; }
    public boolean targeting() { return targeting; }
    public float environmentMultiplier() { return environmentMultiplier; }
    public AttributeContainer attributes() { return attributes; }

    public float attack() {
        return (float) attributes.getBaseValue(ModAttributes.ATK.get());
    }

    public float physicalResistance() {
        return (float) attributes.getTotalValue(ModAttributes.PHYSICAL_RES.get());
    }

    public float pyroResistance() {
        return (float) attributes.getTotalValue(ModAttributes.PYRO_RES.get());
    }

    public float hydroResistance() {
        return (float) attributes.getTotalValue(ModAttributes.HYDRO_RES.get());
    }

    public float anemoResistance() {
        return (float) attributes.getTotalValue(ModAttributes.ANEMO_RES.get());
    }

    public float electroResistance() {
        return (float) attributes.getTotalValue(ModAttributes.ELECTRO_RES.get());
    }

    public float dendroResistance() {
        return (float) attributes.getTotalValue(ModAttributes.DENDRO_RES.get());
    }

    public float cyroResistance() {
        return (float) attributes.getTotalValue(ModAttributes.CYRO_RES.get());
    }

    public float geoResistance() {
        return (float) attributes.getTotalValue(ModAttributes.GEO_RES.get());
    }

    public float getElementResistance(GenshinElement element) {
        if (element == ModElements.PYRO.get()) return pyroResistance();
        if (element == ModElements.HYDRO.get()) return hydroResistance();
        if (element == ModElements.ANEMO.get()) return anemoResistance();
        if (element == ModElements.ELECTRO.get()) return electroResistance();
        if (element == ModElements.DENDRO.get()) return dendroResistance();
        if (element == ModElements.CYRO.get()) return cyroResistance();
        if (element == ModElements.GEO.get()) return geoResistance();
        return physicalResistance();
    }

    public int getDefense() {
        return level * 5 + 500;
    }

    public TeyvatEntityStats withLevel(int newLevel) {
        return new TeyvatEntityStats(newLevel, combatTicks, targeting, environmentMultiplier, attributes);
    }

    public TeyvatEntityStats withCombatTicks(int ticks) {
        return new TeyvatEntityStats(level, ticks, targeting, environmentMultiplier, attributes);
    }

    public TeyvatEntityStats withTargeting(boolean targeting) {
        return new TeyvatEntityStats(level, combatTicks, targeting, environmentMultiplier, attributes);
    }

    public TeyvatEntityStats withEnvironmentMultiplier(float multiplier) {
        return new TeyvatEntityStats(level, combatTicks, targeting, multiplier, attributes);
    }

    public TeyvatEntityStats withAttack(float newAttack) {
        AttributeContainer copy = attributes.copy();
        copy.setBaseValue(ModAttributes.ATK.get(), newAttack);
        return new TeyvatEntityStats(level, combatTicks, targeting, environmentMultiplier, copy);
    }

    public TeyvatEntityStats withElementResistance(GenshinElement element, float resistance) {
        AttributeContainer copy = attributes.copy();
        if (element == ModElements.PYRO.get()) copy.setBaseValue(ModAttributes.PYRO_RES.get(), resistance);
        else if (element == ModElements.HYDRO.get()) copy.setBaseValue(ModAttributes.HYDRO_RES.get(), resistance);
        else if (element == ModElements.ANEMO.get()) copy.setBaseValue(ModAttributes.ANEMO_RES.get(), resistance);
        else if (element == ModElements.ELECTRO.get()) copy.setBaseValue(ModAttributes.ELECTRO_RES.get(), resistance);
        else if (element == ModElements.DENDRO.get()) copy.setBaseValue(ModAttributes.DENDRO_RES.get(), resistance);
        else if (element == ModElements.CYRO.get()) copy.setBaseValue(ModAttributes.CYRO_RES.get(), resistance);
        else if (element == ModElements.GEO.get()) copy.setBaseValue(ModAttributes.GEO_RES.get(), resistance);
        else copy.setBaseValue(ModAttributes.PHYSICAL_RES.get(), resistance);
        return new TeyvatEntityStats(level, combatTicks, targeting, environmentMultiplier, copy);
    }

    public TeyvatEntityStats withPhysicalResistance(float resistance) {
        AttributeContainer copy = attributes.copy();
        copy.setBaseValue(ModAttributes.PHYSICAL_RES.get(), resistance);
        return new TeyvatEntityStats(level, combatTicks, targeting, environmentMultiplier, copy);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TeyvatEntityStats that)) return false;
        return level == that.level
                && combatTicks == that.combatTicks
                && targeting == that.targeting
                && Float.compare(that.environmentMultiplier, environmentMultiplier) == 0
                && attributes.equals(that.attributes);
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(level);
        result = 31 * result + Integer.hashCode(combatTicks);
        result = 31 * result + Boolean.hashCode(targeting);
        result = 31 * result + Float.hashCode(environmentMultiplier);
        result = 31 * result + attributes.hashCode();
        return result;
    }
}