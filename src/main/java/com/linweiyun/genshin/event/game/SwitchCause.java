package com.linweiyun.genshin.event.game;

/** 一次角色切换是怎么发生的。 */
public enum SwitchCause {

    /** 玩家按键切人。 */
    KEY,

    /** 当前角色倒下，自动换到下一个活着的角色。 */
    INCAPACITATED,

    /** 队伍增删导致索引被挪动。 */
    PARTY_CHANGED,

    /** 死亡重生后恢复索引（不构成一次真实的登场/退场）。 */
    RESTORE,

    /** 命令或脚本触发。 */
    COMMAND
}