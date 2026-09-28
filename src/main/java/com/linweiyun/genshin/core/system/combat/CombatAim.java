package com.linweiyun.genshin.core.system.combat;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class CombatAim {

    private CombatAim() {
    }

    public static float yaw(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            Float synced = player.getData(AttachmentRegistration.BODY_YAW_ATTACHMENT);
            if (synced != null && Float.isFinite(synced)) {
                return synced;
            }
        }
        if (entity instanceof LivingEntity living) {
            return living.yBodyRot;
        }
        return entity.getYRot();
    }

    public static Vec3 direction(Entity entity) {
        double yawRad = Math.toRadians(yaw(entity));
        double pitchRad = entity instanceof LivingEntity living ? Math.toRadians(living.getXRot()) : 0.0;
        double cos = Math.cos(pitchRad);
        return new Vec3(-Math.sin(yawRad) * cos, -Math.sin(pitchRad), Math.cos(yawRad) * cos);
    }

    public static Vec3 horizontal(Entity entity) {
        double yawRad = Math.toRadians(yaw(entity));
        return new Vec3(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
    }
}
