package com.linweiyun.genshin.core.system.combat.action.data;

public enum ActionBodyFacing {

    TARGET,

    CAMERA,

    MOVEMENT;

    public boolean takesOverBodyFacing() {
        return this != MOVEMENT;
    }
}
