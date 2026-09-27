package com.linweiyun.genshin.content.skill_node;

import com.linweiyun.genshin.core.system.control.ControlService;
import com.linweiyun.genshin.core.system.control.ControlType;
import com.linweiyun.genshin.core.system.control.MovementHold;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * <b>悬浮节点</b> —— 把目标抬离地面并按住一段时间（风场、泡影那一类）。
 *
 * <h2>它是「强控」，所以破韧前完全不生效</h2>
 * 悬浮走 {@link ControlService#canApply} 那道门：<b>有盾 = 霸体 → 不生效；
 * 没破韧 → 不生效</b>。这就是「韧性没破的时候敌人不受控制效果影响」在悬浮上的落点 ——
 * 所以未破韧的怪<b>不会</b>被悬浮抬起来，只会照常掉血。
 *
 * <p>而且这道门每刻都重判一次：目标韧性中途恢复、或者被套上护盾，悬浮会当场脱落
 * （它掉回去），不需要谁去专门收尾。
 *
 * <h2>生效期间按住韧性恢复</h2>
 * 悬浮是强控（{@link ControlType#LEVITATE}）：被按住的那段时间不算对方的恢复时间，
 * 破绽窗口因此被延长。落地、松开或到期后恢复照常走。
 *
 * <h2>怎么「吊住」一个目标</h2>
 * 每刻做三件事，都在实体自己的 tick 之后（{@code ServerTickEvent.Post}）：
 * <ol>
 *   <li>竖直速度按「离目标高度还差多少」去修 —— 差得多就抬快一点，到了就抵消重力悬住；</li>
 *   <li>水平速度按比例衰减，并用 {@link MovementHold} 按住它的移动 AI —— 不然它会自己走开；</li>
 *   <li>清掉坠落距离 —— 否则落地那一刻会按悬浮高度吃一次摔伤。</li>
 * </ol>
 * 目标高度取「开始悬浮时它所在的 Y + {@code height}」，所以是往上抬 {@code height} 格，
 * 而不是吸到某个绝对高度 —— 山崖边的怪和在坑里的怪抬起来的手感一致。
 *
 * <p>非 {@code Mob} 的实体（玩家、盔甲架）也能被抬，但按不住 AI ——
 * {@link MovementHold} 只对 Mob 生效，这条对它们是公开的已知限制。
 */
@EventBusSubscriber
public final class Levitate {

    /** 默认抬升高度：2.5 格（略高于一只怪，肉眼能看出它离地）。 */
    public static final double DEFAULT_HEIGHT = 2.5;

    /** 默认持续时间：3 秒。 */
    public static final int DEFAULT_DURATION_TICKS = 60;

    /** 位置误差 → 竖直速度的增益。 */
    private static final double HOVER_GAIN = 0.4;

    /** 抵消原版重力用的固定项，让平衡点正好落在目标高度上而不是差一点。 */
    private static final double GRAVITY_BIAS = 0.08;

    /** 竖直速度上限：抬得再远也是匀速上升，不会把人弹飞。 */
    private static final double MAX_VERTICAL_SPEED = 0.5;

    /** 悬浮期间水平速度保留的比例（保留一点飘，但别让它自己跑掉）。 */
    private static final double HORIZONTAL_KEEP = 0.6;

    /** 强控按住韧性恢复时，每刻续上的暂停刻数（略大于 1，吸收 tick 顺序错位）。 */
    private static final int RESET_HOLD_TICKS = 5;

    /** 正在悬浮中的目标。 */
    private static final List<Lift> ACTIVE = new ArrayList<>();

    private Levitate() {
    }

    /**
     * 把目标抬起来（已经抬着的会被续时）。
     *
     * @param height        抬离「开始悬浮那一刻的 Y」多少格
     * @param durationTicks 持续刻数
     * @return 是否真的悬浮中；门没过（有盾 / 没破韧）时返回 false，且什么都不做
     */
    public static boolean lift(LivingEntity target, double height, int durationTicks) {
        if (target == null || durationTicks <= 0 || target.level().isClientSide()) {
            return false;
        }
        if (!ControlService.canApply(target, ControlType.LEVITATE)) {
            return false;
        }

        Lift existing = find(target);
        if (existing != null) {
            existing.remainingTicks = Math.max(existing.remainingTicks, durationTicks);
            return true;
        }

        Lift lift = new Lift(target, target.getY(), height, durationTicks);
        ACTIVE.add(lift);
        hold(lift);
        return true;
    }

    /** 用默认高度与时长抬一次。 */
    public static boolean lift(LivingEntity target) {
        return lift(target, DEFAULT_HEIGHT, DEFAULT_DURATION_TICKS);
    }

    /**
     * 把范围里所有能抬的目标一起抬起来。
     *
     * @param owner 施法者：它自己不会被抬，也决定查哪个世界
     * @return 本次真正抬起来（或已在悬浮中被续时）的目标
     */
    public static List<LivingEntity> field(Entity owner, AABB range, double height, int durationTicks) {
        List<LivingEntity> lifted = new ArrayList<>();
        if (owner == null || owner.level().isClientSide()) {
            return lifted;
        }
        for (LivingEntity target : owner.level().getEntitiesOfClass(LivingEntity.class, range)) {
            if (target == owner) {
                continue;
            }
            if (lift(target, height, durationTicks)) {
                lifted.add(target);
            }
        }
        return lifted;
    }

    /** 当前悬浮中的目标数量（诊断用）。 */
    public static int activeCount() {
        return ACTIVE.size();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Lift> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            Lift lift = iterator.next();
            lift.remainingTicks--;
            if (lift.remainingTicks <= 0 || !hold(lift)) {
                iterator.remove();
            }
        }
    }

    /**
     * 每刻把目标按住一帧。
     *
     * @return 还应继续悬浮吗（门没过、目标没了就返回 false）
     */
    private static boolean hold(Lift lift) {
        LivingEntity target = lift.target;
        if (!target.isAlive() || target.isRemoved()) {
            return false;
        }
        // 门每刻重判：韧性恢复 / 被套盾 / 上了别的霸体，悬浮当场脱落
        if (!ControlService.canApply(target, ControlType.LEVITATE)) {
            return false;
        }

        double error = (lift.baseY + lift.height) - target.getY();
        double vertical = Math.clamp(error * HOVER_GAIN + GRAVITY_BIAS,
                -MAX_VERTICAL_SPEED, MAX_VERTICAL_SPEED);
        Vec3 motion = target.getDeltaMovement();
        target.setDeltaMovement(motion.x * HORIZONTAL_KEEP, vertical, motion.z * HORIZONTAL_KEEP);
        target.resetFallDistance();
        target.hurtMarked = true;

        MovementHold.hold(target);
        ControlService.onStrongControlApplied(target, ControlType.LEVITATE, RESET_HOLD_TICKS);
        return true;
    }

    private static Lift find(LivingEntity target) {
        for (Lift lift : ACTIVE) {
            if (lift.target == target) {
                return lift;
            }
        }
        return null;
    }

    /** 一个正在悬浮的目标：抬的基准高度、抬多高、还剩几刻。 */
    private static final class Lift {
        private final LivingEntity target;
        private final double baseY;
        private final double height;
        private int remainingTicks;

        private Lift(LivingEntity target, double baseY, double height, int remainingTicks) {
            this.target = target;
            this.baseY = baseY;
            this.height = height;
            this.remainingTicks = remainingTicks;
        }
    }
}
