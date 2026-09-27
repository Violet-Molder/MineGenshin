package com.linweiyun.genshin.core.system.combat.action;

public enum InterruptReason {
    JUMP,             // 玩家跳跃（主动；能打断准备阶段 / 后摇，执行期不行）
    KNOCKBACK,        // 被击退
    DAMAGE,           // 受到伤害
    SWITCH_CHARACTER, // 切换角色（无视阶段强制打断）
    DEATH,            // 死亡（无视阶段强制打断）
    MANUAL,           // 手动/程序触发
    /**
     * 持续型重击<b>松手收招</b>（大剑）。
     *
     * <p>持续重击的执行期铺满整段（不然走位就能把重击取消掉），而普通原因在执行期内
     * 会被 {@code ActionManager.interrupt} 直接丢弃 —— 于是「松手」得走强制那一类：
     * 玩家松开左键就是要收招，没有别的条件可讲。见 {@code ActionManager.interrupt}。
     */
    CHARGE_RELEASE
}
