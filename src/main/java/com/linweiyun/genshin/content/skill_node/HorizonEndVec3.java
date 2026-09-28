package com.linweiyun.genshin.content.skill_node;

import com.linweiyun.genshin.core.system.combat.CombatAim;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class HorizonEndVec3 {

    private final Player player;
    private final float distance;

    public HorizonEndVec3(Player player) {
        this(player, 1.0f);
    }

    public HorizonEndVec3(Player player, float distance) {
        this.player = player;
        this.distance = distance;
    }

    public Vec3 execute() {
        Vec3 horizontal = CombatAim.horizontal(player);
        if (horizontal.lengthSqr() < 1e-6) {
            return Vec3.ZERO;
        }
        return horizontal.normalize().scale(distance);
    }
}