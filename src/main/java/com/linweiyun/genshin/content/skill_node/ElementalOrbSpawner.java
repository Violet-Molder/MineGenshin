package com.linweiyun.genshin.content.skill_node;

import com.linweiyun.genshin.content.entities.misc.ElementalOrb;
import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.genshin.content.entities.ModEntities;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class ElementalOrbSpawner {

    private final Level level;
    private final GenshinElement element;
    private final int count;
    private final boolean isParticle;
    private final Entity target;
    private final Vec3 pos;

    public ElementalOrbSpawner(Level level, GenshinElement element, int count,
                               boolean isParticle, Entity target) {
        this.level = level;
        this.element = element;
        this.count = count;
        this.isParticle = isParticle;
        this.target = target;
        this.pos = null;
    }

    public ElementalOrbSpawner(Level level, GenshinElement element, int count,
                               boolean isParticle, Vec3 pos) {
        this.level = level;
        this.element = element;
        this.count = count;
        this.isParticle = isParticle;
        this.target = null;
        this.pos = pos;
    }

    public void execute() {
        if (level.isClientSide()) {
            return;
        }
        EntityType<ElementalOrb> orbType = ModEntities.ELEMENTAL_ORB.get();

        if (target != null) {
            spawnAroundTarget(orbType);
        } else if (pos != null) {
            spawnAtPosition(orbType);
        }
    }

    private void spawnAroundTarget(EntityType<ElementalOrb> orbType) {
        Vec3 center = target.position();
        for (int i = 0; i < count; i++) {
            ElementalOrb orb = new ElementalOrb(orbType, level, element, isParticle);
            double offsetX = (target.getRandom().nextDouble() - 0.5) * 2.0;
            double offsetY = target.getRandom().nextDouble() * 1.5 + 0.5;
            double offsetZ = (target.getRandom().nextDouble() - 0.5) * 2.0;
            orb.setPos(center.x + offsetX, center.y + offsetY, center.z + offsetZ);
            level.addFreshEntity(orb);
        }
    }

    private void spawnAtPosition(EntityType<ElementalOrb> orbType) {
        for (int i = 0; i < count; i++) {
            ElementalOrb orb = new ElementalOrb(orbType, level, element, isParticle);
            if (count == 1) {
                orb.setPos(pos.x, pos.y, pos.z);
            } else {
                double spread = 1.5;
                double offsetX = (Math.random() - 0.5) * spread * 2.0;
                double offsetY = (Math.random() - 0.5) * spread * 2.0;
                double offsetZ = (Math.random() - 0.5) * spread * 2.0;
                orb.setPos(pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ);
            }
            level.addFreshEntity(orb);
        }
    }
}