package com.linweiyun.genshin.core.system.poise.impact;

import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import com.linweiyun.genshin.core.system.poise.PoiseService;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 冲击解算 —— 「破韧之后才打得动」这条规则的落点：按重量判定这一下到底推不推得动，
 * 推得动就把冲量加到目标身上。
 *
 * <h2>判定（照文献，别自己发挥）</h2>
 * <pre>
 * 目标在地面：
 *   硬直等级 = 击飞 → 竖直力 ≥ 5.5 × 重量 才击飞；否则降级成击退
 *   硬直等级 = 击退/轻击 → 水平力 ≥ 2 × 重量 才击退/轻击；否则只微颤
 * 目标在空中或攀爬中：
 *   硬直等级 > 微颤 → 一律击飞；否则只微颤
 * </pre>
 *
 * <p><b>「破韧才生效」不在这里判</b>：本类只管「这一下推不推得动」。
 * 未破韧的拒绝由控制系统在调用之前挡掉；破韧瞬间那一下由
 * {@link PoiseService} 的破韧钩子接进来。
 *
 * <h2>力 → 冲量的换算</h2>
 * 文献只给力值（水平 200–1200、竖直 600–900），没给「一格多少速度」。
 * 本机实测的现成口径在 {@code client/combat/AttackApproach}：玩家在地面每刻摩擦衰减约 0.4，
 * 一个冲量 {@code v} 大概滑出 {@code v / 0.4 = 2.5v} 格。取
 * {@link #FORCE_TO_VELOCITY} = {@code 0.001} 格/刻/力值后：
 * <ul>
 *   <li>击退档 200 → 0.2 格/刻 → 约 0.5 格，小半步；</li>
 *   <li>击飞档 480/600 → 水平约 1.2 格、竖直约 2.2 格高，有分量；</li>
 *   <li>微颤不读力值，固定 {@link #TREMBLE_IMPULSE} = 0.05，几乎看不出来。</li>
 * </ul>
 * 手感不对就调这两个常量 —— 判定阈值（5.5 / 2）是文献值，不要动。
 */
public final class ImpactSolver {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.COMBAT);

    /** 没配过重量的实体按 100 —— 文献「挣扎状态的作用对象是重量 ≤ 100」暗示 100 是典型量级。 */
    public static final float DEFAULT_WEIGHT = 100f;

    /** 地面击飞的竖直门槛：竖直力 ≥ 5.5 × 重量。 */
    public static final float LAUNCH_VERTICAL_RATIO = 5.5f;

    /** 地面击退/轻击的水平门槛：水平力 ≥ 2 × 重量。 */
    public static final float KNOCKBACK_HORIZONTAL_RATIO = 2f;

    /** 力值 → 速度（格/刻）。1 力值 = 0.001 格/刻，理由见类注释。 */
    public static final float FORCE_TO_VELOCITY = 0.001f;

    /** 微颤的固定冲量：几乎看不出来的一晃。 */
    public static final float TREMBLE_IMPULSE = 0.05f;

    /** 击飞至少给到 5 档的竖直力，免得自定「击飞，x，0」变成只水平推一下。 */
    public static final float MIN_LAUNCH_VERTICAL_FORCE = 600f;

    /** 击退至少给到 2 档的水平力；「击飞降级成击退」时用得上。 */
    public static final float MIN_KNOCKBACK_HORIZONTAL_FORCE = 200f;

    /** 一次冲击实际发生了什么。 */
    public enum Outcome {
        /** 没发生任何位移。 */
        NONE,
        /** 只微颤。 */
        TREMBLE,
        /** 轻击（水平推开，量级小于击退）。 */
        LIGHT,
        /** 击退。 */
        KNOCKBACK,
        /** 击飞（水平 + 竖直）。 */
        LAUNCH
    }

    /**
     * 解算结果 —— 供调用方查「这一下实际是什么」与调试日志。
     *
     * @param level          传入的冲击等级
     * @param outcome        实际结果
     * @param airborne       判定时目标是不是在空中/攀爬
     * @param weight         判定用的重量
     * @param horizontalSpeed 实际施加的水平速度（格/刻，方向另算）
     * @param verticalSpeed  实际施加的竖直速度（格/刻；击退/微颤为 0，表示没改竖直分量）
     */
    public record Result(ImpactLevel level, Outcome outcome, boolean airborne, float weight,
                         double horizontalSpeed, double verticalSpeed) {

        /** 这一下有没有真的产生位移。 */
        public boolean moved() {
            return outcome != Outcome.NONE;
        }

        /** 没发生位移时的空结果。 */
        public static Result none(ImpactLevel level, boolean airborne, float weight) {
            return new Result(level, Outcome.NONE, airborne, weight, 0.0, 0.0);
        }
    }

    /** 按怪物 id 覆盖重量（正式配置接入之前的出口；不在表里就走实体属性）。 */
    private static final Map<ResourceLocation, Float> WEIGHT_OVERRIDES = new ConcurrentHashMap<>();

    private ImpactSolver() {
    }

    // ==================== 对外入口 ====================

    /**
     * 对目标施加一次冲击。方向默认「朝目标背后」——即目标视线方向的反方向。
     *
     * <p>只在服务端生效；客户端调用直接返回空结果。
     */
    public static Result apply(LivingEntity target, ImpactLevel level) {
        return apply(target, level, null);
    }

    /**
     * 对目标施加一次冲击，并指定「从哪个点打过来」用于计算水平方向。
     *
     * @param awayFrom 攻击来源的位置；为 null 时用目标视线反方向
     */
    public static Result apply(LivingEntity target, ImpactLevel level, @Nullable Vec3 awayFrom) {
        if (target.level().isClientSide()) {
            return Result.none(level, false, DEFAULT_WEIGHT);
        }
        float weight = weightOf(target);
        boolean airborne = !target.onGround() || target.onClimbable();
        if (level == null || level.isNone()) {
            return Result.none(level == null ? ImpactLevel.NONE : level, airborne, weight);
        }

        Outcome outcome = resolve(level, weight, airborne);
        double horizontalSpeed = outcome == Outcome.NONE ? 0.0 : speedOf(outcome, level);
        double verticalSpeed = outcome == Outcome.LAUNCH
                ? velocityOf(Math.max(level.verticalForce(), MIN_LAUNCH_VERTICAL_FORCE))
                : 0.0;

        if (outcome != Outcome.NONE) {
            applyImpulse(target, awayFrom, horizontalSpeed, verticalSpeed, outcome);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Impact] {} <- {}：重量 {}, 空中={}, 结果={}（水平 {} / 竖直 {}）",
                    target.getName().getString(), level, weight, airborne, outcome,
                    horizontalSpeed, verticalSpeed);
        }
        return new Result(level, outcome, airborne, weight, horizontalSpeed, verticalSpeed);
    }

    /**
     * 纯判定：给定等级、重量、在不在空中，返回实际结果。
     *
     * <p>从 {@link #apply} 里拆出来，方便单独核对文献规则。
     */
    public static Outcome resolve(ImpactLevel level, float weight, boolean airborne) {
        if (level == null || level.isNone()) {
            return Outcome.NONE;
        }
        if (airborne) {
            // 空中/攀爬：只要比微颤重，一律击飞
            return level.level() > ImpactLevel.TREMBLE.level() ? Outcome.LAUNCH : Outcome.TREMBLE;
        }
        return switch (level.hardiness()) {
            case NONE -> Outcome.NONE;
            case TREMBLE -> Outcome.TREMBLE;
            case LIGHT -> level.horizontalForce() >= KNOCKBACK_HORIZONTAL_RATIO * weight
                    ? Outcome.LIGHT : Outcome.TREMBLE;
            case KNOCKBACK -> level.horizontalForce() >= KNOCKBACK_HORIZONTAL_RATIO * weight
                    ? Outcome.KNOCKBACK : Outcome.TREMBLE;
            case LAUNCH -> level.verticalForce() >= LAUNCH_VERTICAL_RATIO * weight
                    ? Outcome.LAUNCH : Outcome.KNOCKBACK;
        };
    }

    /** 目标的重量：按怪物 id 覆盖 → 实体属性 → 默认 100。 */
    public static float weightOf(LivingEntity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (id != null) {
            Float override = WEIGHT_OVERRIDES.get(id);
            if (override != null && override > 0f) {
                return override;
            }
        }
        Double explicit = PoiseService.attributeOrNull(entity, ModAttributes.WEIGHT.get());
        if (explicit != null && explicit > 0.0) {
            return explicit.floatValue();
        }
        return DEFAULT_WEIGHT;
    }

    /** 给某一种实体配重量覆盖；传 ≤0 视为移除。 */
    public static void overrideWeight(ResourceLocation entityId, float weight) {
        if (entityId == null) {
            return;
        }
        if (weight > 0f) {
            WEIGHT_OVERRIDES.put(entityId, weight);
        } else {
            WEIGHT_OVERRIDES.remove(entityId);
        }
    }

    /** 清掉所有重量覆盖（资源重载/调试用）。 */
    public static void clearWeightOverrides() {
        WEIGHT_OVERRIDES.clear();
    }

    // ==================== 内部 ====================

    private static double speedOf(Outcome outcome, ImpactLevel level) {
        return switch (outcome) {
            case NONE -> 0.0;
            case TREMBLE -> TREMBLE_IMPULSE;
            case LIGHT -> velocityOf(level.horizontalForce());
            case KNOCKBACK -> velocityOf(Math.max(level.horizontalForce(), MIN_KNOCKBACK_HORIZONTAL_FORCE));
            case LAUNCH -> velocityOf(level.horizontalForce());
        };
    }

    private static double velocityOf(float force) {
        return force * FORCE_TO_VELOCITY;
    }

    /**
     * 施加冲量。
     *
     * <p>水平分量是<b>替换</b>不是叠加：先清掉攒下的 AI 移动速度，
     * 否则会出现「被打飞的同时自己还在往反方向走」的对冲（牵引节点第 ④ 步同一个做法）。
     * 竖直分量只在击飞时替换；击退/微颤保留当前竖直速度，免得把下落中的目标按回地面。
     */
    private static void applyImpulse(LivingEntity target, @Nullable Vec3 awayFrom,
                                     double horizontalSpeed, double verticalSpeed, Outcome outcome) {
        Vec3 direction = horizontalDirection(target, awayFrom);
        Vec3 motion = target.getDeltaMovement();
        double y = outcome == Outcome.LAUNCH ? verticalSpeed : motion.y;
        target.setDeltaMovement(direction.x * horizontalSpeed, y, direction.z * horizontalSpeed);
        target.hurtMarked = true;   // 服务端改速度后必须标一下，客户端才收得到
    }

    /** 水平方向：有来源就背离来源，没有就取目标视线的反方向（默认「从正面打来」）。 */
    private static Vec3 horizontalDirection(LivingEntity target, @Nullable Vec3 awayFrom) {
        double dx;
        double dz;
        if (awayFrom != null) {
            dx = target.getX() - awayFrom.x;
            dz = target.getZ() - awayFrom.z;
        } else {
            Vec3 look = target.getLookAngle();
            dx = -look.x;
            dz = -look.z;
        }
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-6) {
            // 正好重合 / 垂直看：给一个确定的方向，别让目标原地不动
            return new Vec3(1.0, 0.0, 0.0);
        }
        return new Vec3(dx / length, 0.0, dz / length);
    }
}
