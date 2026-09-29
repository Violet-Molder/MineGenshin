package com.linweiyun.genshin.core.world.leveling;

import com.linweiyun.genshin.config.entity.EntityHealthConfig;
import com.linweiyun.genshin.config.entity.EntityAttackConfig;

public class MonsterStatCalculator {

    public static float calculateHealth(int level, float entityMultiplier, float environmentMultiplier) {
        return (float) (EntityHealthConfig.getHealthBase(level) * entityMultiplier * environmentMultiplier);
    }

    public static float calculateAttack(int level, float entityMultiplier, float environmentMultiplier) {
        return (float) (EntityAttackConfig.getAttackBase(level) * entityMultiplier * environmentMultiplier);
    }
}