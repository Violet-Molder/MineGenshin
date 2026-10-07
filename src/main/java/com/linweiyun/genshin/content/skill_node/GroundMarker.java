package com.linweiyun.genshin.content.skill_node;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * <b>地面落点提示</b>（技能节点）—— 红色粒子圈出即将被击中的范围。
 *
 * <p>表现分两层：
 * <ol>
 *   <li><b>外圈</b>：固定在目标半径上的红色圆环，从头到尾不变；</li>
 *   <li><b>内圈</b>：从中心向外扩散，在 {@code durationTicks} 内扩到和外圈一样大 ——
 *       扩散满的那一刻就是「砸下来了」，内圈消失、外圈同时消失。</li>
 * </ol>
 *
 * <p>它只负责<b>画</b>，不负责伤害：到点后回调 {@code onImpact}，由调用方决定砸什么。
 * 这样它既能给史莱姆的跃起砸落用，也能给别的「延迟落点」技能用。
 *
 * <p>用 {@code ServerTickEvent} 自己驱动，不做成实体：纯表现、活 1 秒左右，
 * 开一个实体类型不值当。
 */
@EventBusSubscriber
public final class GroundMarker {

    /** 外圈颜色（正红）。 */
    private static final DustParticleOptions OUTER =
            new DustParticleOptions(new Vector3f(0xFF / 255f, 0x20 / 255f, 0x20 / 255f), 1.3f);

    /** 内圈颜色（亮一点，好和固定外圈区分开）。 */
    private static final DustParticleOptions INNER =
            new DustParticleOptions(new Vector3f(0xFF / 255f, 0xB0 / 255f, 0xB0 / 255f), 0.9f);

    /** 圆环采样点数：越大越圆，也越费包。 */
    private static final int RING_POINTS = 24;

    /** 内圈不只是个环，而是「填满到当前进度」的盘，这样一眼能看出进度条。 */
    private static final int INNER_RINGS = 3;

    private static final List<Pending> ACTIVE = new ArrayList<>();

    private GroundMarker() {
    }

    /**
     * 在地面画一个落点提示。
     *
     * @param center        圆心（一般取实体脚底）
     * @param radius        外圈半径
     * @param durationTicks 内圈扩散满所需刻数（也是落地倒计时）
     * @param onImpact      到点回调，参数是圆心
     */
    public static void spawn(ServerLevel level, Vec3 center, double radius, int durationTicks,
                             Consumer<Vec3> onImpact) {
        if (level == null || onImpact == null) {
            return;
        }
        ACTIVE.add(new Pending(level, center, Math.max(0.1, radius), Math.max(1, durationTicks), onImpact));
    }

    /** 有没有正在倒计时的落点提示（调试用）。 */
    public static int activeCount() {
        return ACTIVE.size();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Pending> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            Pending pending = iterator.next();
            pending.age++;
            draw(pending);
            if (pending.age >= pending.totalTicks) {
                iterator.remove();
                pending.onImpact.accept(pending.center);
            }
        }
    }

    private static void draw(Pending pending) {
        float progress = (float) pending.age / pending.totalTicks;
        double innerRadius = pending.radius * Math.min(1.0, progress);

        // ① 固定外圈：范围（一直不变，告诉你「这一圈内都会被打到」）
        for (int i = 0; i < RING_POINTS; i++) {
            double angle = i * (Math.PI * 2.0 / RING_POINTS);
            pending.level.sendParticles(OUTER,
                    pending.center.x + Math.cos(angle) * pending.radius, pending.center.y,
                    pending.center.z + Math.sin(angle) * pending.radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }

        // ② 从中心往外扩散的圆盘：进度（扩满 = 砸下来）
        for (int ring = 1; ring <= INNER_RINGS; ring++) {
            double ringRadius = innerRadius * ring / INNER_RINGS;
            if (ringRadius < 0.15D) {
                continue;
            }
            for (int i = 0; i < RING_POINTS; i++) {
                double angle = i * (Math.PI * 2.0 / RING_POINTS);
                pending.level.sendParticles(INNER,
                        pending.center.x + Math.cos(angle) * ringRadius, pending.center.y,
                        pending.center.z + Math.sin(angle) * ringRadius,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    /** 一个正在倒计时的落点。 */
    private static final class Pending {
        private final ServerLevel level;
        private final Vec3 center;
        private final double radius;
        private final int totalTicks;
        private final Consumer<Vec3> onImpact;
        private int age;

        private Pending(ServerLevel level, Vec3 center, double radius, int totalTicks,
                        Consumer<Vec3> onImpact) {
            this.level = level;
            this.center = center;
            this.radius = radius;
            this.totalTicks = totalTicks;
            this.onImpact = onImpact;
        }
    }
}
