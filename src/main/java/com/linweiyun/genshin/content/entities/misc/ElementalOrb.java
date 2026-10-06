package com.linweiyun.genshin.content.entities.misc;

import com.linweiyun.genshin.core.character.util.CharacterHelper;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.PGCharacterData;
import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class ElementalOrb extends Entity {

    public static final String ELEMENT_FYSIKOS_ID = "fysikos";

    public static final EntityDataAccessor<String> DATA_ELEMENT =
            SynchedEntityData.defineId(ElementalOrb.class, EntityDataSerializers.STRING);
    public static final EntityDataAccessor<Boolean> DATA_IS_PARTICLE =
            SynchedEntityData.defineId(ElementalOrb.class, EntityDataSerializers.BOOLEAN);

    private Player followingPlayer;

    public ElementalOrb(EntityType<? extends ElementalOrb> entityType, Level level) {
        super(entityType, level);
    }

    public ElementalOrb(EntityType<? extends ElementalOrb> entityType, Level level,
                        GenshinElement element, boolean isParticle) {
        this(entityType, level);
        this.getEntityData().set(DATA_ELEMENT, element.getId());
        this.getEntityData().set(DATA_IS_PARTICLE, isParticle);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ELEMENT, ELEMENT_FYSIKOS_ID);
        builder.define(DATA_IS_PARTICLE, true);
    }

    @Override
    protected MovementEmission getMovementEmission() {
        return MovementEmission.NONE;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.03;
    }

    @Override
    public void tick() {
        super.tick();
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();

        if (this.isEyeInFluid(FluidTags.WATER)) {
            this.setUnderwaterMovement();
        } else {
            this.applyGravity();
        }

        if (this.level().getFluidState(this.blockPosition()).is(FluidTags.LAVA)) {
            this.setDeltaMovement(
                    (this.random.nextFloat() - this.random.nextFloat()) * 0.2F,
                    0.2F,
                    (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);
        }

        if (!this.level().noCollision(this.getBoundingBox())) {
            this.moveTowardsClosestSpace(
                    this.getX(),
                    (this.getBoundingBox().minY + this.getBoundingBox().maxY) / 2.0,
                    this.getZ());
        }

        if (this.tickCount % 20 == 1) {
            this.scanForEntities();
        }

        if (this.followingPlayer != null
                && (this.followingPlayer.isSpectator() || this.followingPlayer.isDeadOrDying())) {
            this.followingPlayer = null;
        }

        if (this.followingPlayer != null) {
            Vec3 vec3 = new Vec3(
                    this.followingPlayer.getX() - this.getX(),
                    this.followingPlayer.getY() + (double) this.followingPlayer.getEyeHeight() / 2.0 - this.getY(),
                    this.followingPlayer.getZ() - this.getZ());
            double d0 = vec3.lengthSqr();
            if (d0 < 64.0) {
                double d1 = 1.0 - Math.sqrt(d0) / 8.0;
                this.setDeltaMovement(this.getDeltaMovement().add(vec3.normalize().scale(d1 * d1 * 0.1)));
            }
        }

        this.move(MoverType.SELF, this.getDeltaMovement());
        float f = 0.98F;
        if (this.onGround()) {
            BlockPos pos = this.getBlockPosBelowThatAffectsMyMovement();
            f = this.level().getBlockState(pos).getFriction(this.level(), pos, this) * 0.98F;
        }

        this.setDeltaMovement(this.getDeltaMovement().multiply(f, 0.98, f));
        if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0, -0.9, 1.0));
        }

        if (this.tickCount >= 6000) {
            this.discard();
        }
    }

    public BlockPos getBlockPosBelowThatAffectsMyMovement() {
        return this.getOnPos(0.999999F);
    }

    private void scanForEntities() {
        Player nearest = this.level().getNearestPlayer(this, 8.0);
        if (nearest != null && !nearest.isSpectator() && !nearest.isDeadOrDying()) {
            this.followingPlayer = nearest;
        } else if (this.followingPlayer != null
                && this.followingPlayer.distanceToSqr(this) > 64.0) {
            this.followingPlayer = null;
        }
    }

    private void setUnderwaterMovement() {
        Vec3 vec3 = this.getDeltaMovement();
        this.setDeltaMovement(
                vec3.x * 0.99,
                Math.min(vec3.y + 5.0E-4, 0.06),
                vec3.z * 0.99);
    }

    public GenshinElement getElement() {
        String elementId = this.getEntityData().get(DATA_ELEMENT);
        if (elementId != null && !elementId.isEmpty()) {
            return com.linweiyun.elementlib.core.system.registry.ModRegistries.ELEMENT_REGISTRY
                    .get(Identifier.fromNamespaceAndPath("minegenshin", elementId))
                    .map(r -> r.value())
                    .orElse(ModElements.FYSIKOS.get());
        }
        return ModElements.FYSIKOS.get();
    }

    public boolean isParticle() {
        return this.getEntityData().get(DATA_IS_PARTICLE);
    }

    @Override
    public void playerTouch(Player player) {
        if (!player.level().isClientSide()) {
            if (player.takeXpDelay == 0) {
                player.takeXpDelay = 2;
                player.take(this, 1);
                addElementalEnergy(player);
                this.discard();
            }
        }
    }

    @Override
    public final boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    private void addElementalEnergy(Player player) {
        PGCharacter character = CharacterHelper.getCurrentCharacter(player);
        if (character == null) {
            return;
        }

        PGCharacterData data = character.getData();
        float baseEnergy = isParticle() ? 1.0f : 3.0f;
        float multiplier = getEnergyMultiplier(character);

        float energyGain = baseEnergy * multiplier;
        data.addElementalEnergy(energyGain);
    }

    private float getEnergyMultiplier(PGCharacter character) {
        String orbElementId = this.getEntityData().get(DATA_ELEMENT);

        if (ELEMENT_FYSIKOS_ID.equals(orbElementId)) {
            return 2.0f;
        }

        GenshinElement charElement = character.getElemental();
        if (charElement != null && orbElementId.equals(charElement.getId())) {
            return 3.0f;
        }

        return 1.0f;
    }
}