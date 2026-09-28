package com.linweiyun.genshin.client.combat;
import com.linweiyun.genshin.core.system.combat.action.data.ActionStep;

import com.linweiyun.genshin.core.network.ActionServer;
import com.linweiyun.genshin.core.system.combat.CombatAim;
import com.linweiyun.genshin.core.system.combat.action.data.ActionStep.DiveBurst;
import com.linweiyun.genshin.core.system.combat.targeting.CombatTargeting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 「跃起下坠刺击」大招的<b>客户端位移序列</b>。
 *
 * <h2>两个阶段</h2>
 * <pre>
 * ① 跃起（jumpTicks 刻）   动画负责起跳与转身，代码只把人匀速抬到 jumpHeight 高
 * ② 下坠（diveTicks 刻）   开始那一刻<b>锁死落点</b>并发给服务端，然后按缓动扎下去
 * </pre>
 *
 * <h2>为什么落点要「锁」</h2>
 * 这类大招<b>不追踪</b>：角度取决于「开始下坠时」双方的位置关系，
 * 之后敌人怎么跑都不管 —— 打空是设计的一部分。
 * 服务端的落地伤害打的也是这个点，所以锁定的那一刻要
 * {@link ActionServer#sendBurstLandingToServer 把落点送过去}。
 *
 * <h2>和伤害时序的关系</h2>
 * 服务端在动作的 {@code hits[].delay} 那一刻结算落地伤害，
 * 而 {@code delay} 就是 {@code jumpTicks + diveTicks}（见 {@code ActionStep.withDiveBurst}）。
 * 也就是说：<b>动画演完跃起 → 下坠 diveTicks 刻 → 落地，和服务端的伤害是同一刻</b>。
 */
public final class BurstDive {

    private enum Phase {JUMP, DIVE}

    private static boolean active;
    private static Phase phase = Phase.JUMP;
    private static DiveBurst config;
    /** 阶段内已经过了几刻。 */
    private static int phaseTicks;
    /** 跃起点与跃起结束点。 */
    private static Vec3 startPos = Vec3.ZERO;
    private static Vec3 apex = Vec3.ZERO;
    /** 锁定的落点（下坠开始那一刻定下，之后不再变）。 */
    private static Vec3 landing = Vec3.ZERO;
    /** 锁定时的目标 id（只为「算落点」用一次，之后不再读它的位置）。 */
    private static int targetId;

    private BurstDive() {
    }

    public static boolean isActive() {
        return active;
    }

    // ==================== 开始 / 结束 ====================

    /**
     * 开始这一发下坠大招（由大招出手流程在锁敌、播动画、发请求之后调用）。
     *
     * @param target 这一发锁到的目标；没有目标时往正前方砸
     */
    public static void begin(LocalPlayer player, @Nullable LivingEntity target,
                             ActionStep step) {
        DiveBurst config = step == null ? null : step.diveBurst;
        if (config == null) {
            return;
        }

        BurstDive.config = config;
        BurstDive.active = true;
        BurstDive.phase = Phase.JUMP;
        BurstDive.phaseTicks = 0;
        BurstDive.startPos = player.position();
        BurstDive.apex = startPos;
        BurstDive.landing = Vec3.ZERO;
        BurstDive.targetId = target == null ? 0 : target.getId();

        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0f;
    }

    /** 取消（换动作 / 回常态时调）。 */
    public static void cancel() {
        active = false;
        config = null;
        phaseTicks = 0;
    }

    // ==================== 每刻 ====================

    /** 每客户端 tick 调一次（在状态机 tick 之前，和突进同一批）。 */
    public static void tick(LocalPlayer player) {
        if (!active || config == null) {
            return;
        }

        // 全程免摔伤：这一段是「自己飞过去的」，不该按自由落体算
        player.fallDistance = 0f;
        phaseTicks++;

        if (phase == Phase.JUMP) {
            tickJump(player);
            return;
        }
        tickDive(player);
    }

    /** ① 跃起：匀速抬到 jumpHeight，水平不动（动画负责起跳姿态）。 */
    private static void tickJump(LocalPlayer player) {
        double rise = config.jumpHeight / config.jumpTicks;
        player.setDeltaMovement(0, rise, 0);
        player.hurtMarked = true;
        player.fallDistance = 0f;

        if (phaseTicks >= config.jumpTicks) {
            apex = player.position();
            landing = lockLanding(player);
            // 落点定死了 → 立刻告诉服务端，它的落地伤害打的就是这里
            ActionServer.sendBurstLandingToServer(landing.x, landing.y, landing.z);
            phase = Phase.DIVE;
            phaseTicks = 0;
        }
    }

    /** ② 下坠：从跃起终点直线扎向锁定的落点（缓入，越掉越快）。 */
    private static void tickDive(LocalPlayer player) {
        double progress = Math.min(1.0, phaseTicks / (double) config.diveTicks);
        double eased = progress * progress; // 缓入：起步慢、越接近落地越快
        Vec3 next = apex.lerp(landing, eased);

        // 撞到方块就停在原地（不再往下钻），但继续走完刻数 ——
        // 服务端的伤害时刻是固定的，这里不能提前结束，否则表现和伤害对不上
        Vec3 delta = next.subtract(player.position());
        if (player.level().noCollision(player, player.getBoundingBox().move(delta))) {
            player.setPos(next.x, next.y, next.z);
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.fallDistance = 0f;

        if (phaseTicks >= config.diveTicks) {
            end(player);
        }
    }

    private static void end(LocalPlayer player) {
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.fallDistance = 0f;
        cancel();
    }

    // ==================== 落点 ====================

    /**
     * 锁定落点：<b>只看这一刻</b>目标在哪。
     *
     * <pre>
     * 期望落点 = 锁定目标的位置（没目标就自己脚下）
     * 水平限制 = 最多离跃起点 maxDistance 格（防止一步跨半个地图）
     * 竖直     = 从跃起高度往下打一条射线，落在第一个方块上（打不到就保持原高度）
     * </pre>
     */
    private static Vec3 lockLanding(LocalPlayer player) {
        Vec3 desired = desiredLanding(player);
        Vec3 from = apex;

        Vec3 horizontal = new Vec3(desired.x - from.x, 0, desired.z - from.z);
        if (horizontal.length() > config.maxDistance) {
            horizontal = horizontal.normalize().scale(config.maxDistance);
        }
        double x = from.x + horizontal.x;
        double z = from.z + horizontal.z;

        double y = groundBelow(player, x, z, from.y + 1.0);
        if (Double.isNaN(y)) {
            // 脚下是空的（比如目标在飞）→ 就按目标的高度落
            y = desired.y;
        }
        return new Vec3(x, y, z);
    }

    /** 期望落点：锁定目标的位置；没有目标就往面朝方向一点。 */
    private static Vec3 desiredLanding(LocalPlayer player) {
        if (targetId != 0) {
            Entity entity = player.level().getEntity(targetId);
            if (entity instanceof LivingEntity living && living.isAlive()) {
                return living.position();
            }
        }
        LivingEntity locked = CombatTargeting.current(player);
        if (locked != null) {
            return locked.position();
        }
        Vec3 forward = CombatAim.horizontal(player);
        return player.position().add(forward.scale(2.0));
    }

    /** 从 (x, yStart, z) 往下找地面；找不到返回 NaN。 */
    private static double groundBelow(LocalPlayer player, double x, double z, double yStart) {
        Vec3 from = new Vec3(x, yStart, z);
        Vec3 to = new Vec3(x, yStart - 48.0, z);
        BlockHitResult hit = player.level().clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) {
            return Double.NaN;
        }
        return hit.getLocation().y;
    }
}
