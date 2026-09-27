package com.linweiyun.genshin.core.system.control;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * <b>AI 持握</b> —— 一段时间内把一只 {@link Mob} 的移动 AI 按住，松开时自动还原。
 *
 * <h2>为什么需要它</h2>
 * 「破韧前拉不动、破韧后拖得走」卡在位移的<b>来源</b>上：怪自己每刻都在用
 * {@code MoveControl} 造位移，外面只额外给它一份位移，它照样能走回去；
 * 要的是绝对控制（例如破韧后被聚怪拖走），就必须把它的移动来源掐掉，
 * 只看得到牵引那一份位移。
 * 原版没有「暂停移动 AI」的开关，最省的是 {@code Mob#setNoAi(true)} ——
 * 冻结（{@code ColdElement#applyFreeze}）用的就是它。
 *
 * <h2>为什么是「租约」而不是引用计数</h2>
 * 引用计数要求每个持握者都在结束时记得释放，而控制效果是每刻刷新的：
 * 中途被移除（实体死亡、区块卸载、技能被打断）时那条 {@code release} 就漏了，
 * 于是怪永久定住 —— 这是最难排查的一类 bug。这里的语义是<b>租约</b>：
 * 「我按住它到这一时刻」，每刻刷新一次；不再刷新就在几刻内自动过期还原。
 *
 * <h2>还原成什么</h2>
 * 记下<b>第一次持握时</b>的 {@code isNoAi()}，过期时写回那个值 ——
 * 本来就不吃 AI 的怪（被别的系统永久禁 AI、或被命名牌定住）不会被我们改坏。
 * 与冻结抢同一个开关也能自愈：{@code ColdAura} 每刻都会把 NoAI 重算一遍，
 * 我们万一还原错了，它下一 tick 就纠回来。
 *
 * <p>非 {@link Mob} 的实体（玩家、盔甲架）不受影响，持握请求直接忽略 ——
 * 玩家的「动作被打断」走 {@link Controllable#interruptAction} 那条路（取消动作），不是这条。
 */
@EventBusSubscriber
public final class MovementHold {

    /**
     * 租约默认时长：4 刻。
     *
     * <p>刷新周期是 1 刻，留 3 刻余量吸收「持握方与被持握者的 tick 先后顺序」这种错位，
     * 又短到让「持握方没了」这件事在肉眼看来是立刻松开。
     */
    public static final int DEFAULT_LEASE_TICKS = 4;

    /** 正在被按住的目标 → 它的租约。用 IdentityHashMap：同一实体按实例比，不走 equals。 */
    private static final Map<LivingEntity, Lease> LEASES = new IdentityHashMap<>();

    private MovementHold() {
    }

    /**
     * 按住目标一段租约 —— 每刻调一次即为持续持握（自动续租）。
     *
     * @param leaseTicks 本次租约的刻数；≤0 时只刷新当刻
     */
    public static void hold(LivingEntity target, int leaseTicks) {
        if (!(target instanceof Mob mob) || target.level().isClientSide()) {
            return;
        }
        long now = mob.level().getGameTime();
        Lease lease = LEASES.get(mob);
        if (lease == null) {
            lease = new Lease(mob, mob.isNoAi());
            LEASES.put(mob, lease);
        }
        lease.expireAt = now + Math.max(1, leaseTicks);
        if (!mob.isNoAi()) {
            mob.setNoAi(true);
        }
    }

    /** 用 {@link #DEFAULT_LEASE_TICKS} 按一下。 */
    public static void hold(LivingEntity target) {
        hold(target, DEFAULT_LEASE_TICKS);
    }

    /** 目标现在是否被（任何一方）按着。 */
    public static boolean isHeld(LivingEntity target) {
        return LEASES.containsKey(target);
    }

    /**
     * 主动松开。
     *
     * <p>只在<b>确定没有别的持握者</b>时调 —— 这条不参与「谁按的」的记账，
     * 别人还在刷新租约的话，它下一 tick 会把 NoAI 重新按住，但那一帧是松的。
     * 大多数情况下什么都不用做：停止刷新就是松开。
     */
    public static void release(LivingEntity target) {
        Lease lease = LEASES.remove(target);
        if (lease != null) {
            lease.restore();
        }
    }

    /** 当前被按住的实体数量（诊断用）。 */
    public static int activeCount() {
        return LEASES.size();
    }

    /**
     * 租约到期/目标消失时还原 —— 挂在整个 tick 的最后（{@code ServerTickEvent.Post}），
     * 挨个实体 tick 清理会漏掉「持握方先 tick、目标后 tick」的错位。
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (LEASES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<LivingEntity, Lease>> iterator = LEASES.entrySet().iterator();
        while (iterator.hasNext()) {
            Lease lease = iterator.next().getValue();
            boolean gone = lease.mob.isRemoved() || !lease.mob.isAlive();
            if (!gone && lease.mob.level().getGameTime() < lease.expireAt) {
                continue;
            }
            iterator.remove();
            if (!gone) {
                lease.restore();
            }
        }
    }

    /** 一次持握：目标、第一次持握时的 NoAI、这次的到期时刻。 */
    private static final class Lease {
        private final Mob mob;

        /** 第一次持握时本来的 NoAI —— 还原成它，而不是无脑置 false。 */
        private final boolean noAiBefore;

        private long expireAt;

        private Lease(Mob mob, boolean noAiBefore) {
            this.mob = mob;
            this.noAiBefore = noAiBefore;
        }

        private void restore() {
            if (mob.isRemoved()) {
                return;
            }
            if (mob.isNoAi() != noAiBefore) {
                mob.setNoAi(noAiBefore);
            }
        }
    }
}
