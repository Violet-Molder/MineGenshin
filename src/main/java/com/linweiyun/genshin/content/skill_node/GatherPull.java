package com.linweiyun.genshin.content.skill_node;

import com.linweiyun.genshin.core.system.control.ControlService;
import com.linweiyun.genshin.core.system.control.ControlRequest;
import com.linweiyun.genshin.core.system.control.ControlType;
import com.linweiyun.genshin.core.system.control.MovementHold;
import com.linweiyun.genshin.core.system.poise.PoiseTiers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * <b>牵引节点</b> —— 把「牵引范围」里的目标往「牵引核心」拉。
 *
 * <h2>先在前面说清一件事：未破韧是拉不动的</h2>
 * 聚怪不是「破韧前也生效的控制」。它和其它控制一样要等破韧，
 * 它的特殊之处是<b>自己会削韧</b>：
 * <ol>
 *   <li>目标进入范围 → 先砸一笔<b>高额初始削韧</b>（每个目标每次进入范围只砸一次）；</li>
 *   <li>没打破 → <b>只削韧、不拉</b>。它照常走、照常打，只是韧性条在掉；</li>
 *   <li>打破了 → 才开始拉：按住它的移动 AI、清掉攒下的水平冲量，位移全额生效。</li>
 * </ol>
 * 所以「低等级聚怪拉不动硬怪」不是另一道门槛，而是<b>那笔初始削韧不够</b> ——
 * 拉不动，是因为没打破。等级越高，那一笔越大，能打破的目标越硬。
 *
 * <h2>牵引等级：4 档 = 初始削韧的 4 档</h2>
 * 第 N 档的初始削韧 = 第 N 档韧性条的长度（{@link PoiseTiers#profileOf(int)}），
 * 于是「等级 N 一次就能打破 N 档及更低档的目标」：
 * 最低档（{@link Level#L1}）打得动原版普通生物，最高档（{@link Level#L4}）才打得动
 * 大体型（劫掠兽那一类）。免疫的目标（原版三个 BOSS、其它模组的实体）在
 * {@link #canGather} 就被挡住，<b>任何等级都拉不动</b>。
 * 数值只有 {@code PoiseTiers.TIER_PROFILES} 一处，改表即改这里。
 *
 * <h2>为什么是两个 AABB，而不是两个坐标</h2>
 * 因为牵引的终点常常<b>不是一个点，而是中心周围的一片区域</b>：
 * 风眼、泡影这类吸附，目标挤到中心附近就该停住，不必（也不该）被叠成同一个点。所以：
 * <ul>
 *   <li>{@code core}（第一个盒）= <b>牵引核心</b>：往哪牵引。它是一块区域，不是中心点。</li>
 *   <li>{@code range}（第二个盒）= <b>牵引范围</b>：影响范围，谁会被算进来。</li>
 * </ul>
 * 已经在 {@code core} 里的目标<b>不再受力</b>（它已经到地方了）；在 {@code range} 里
 * 但不在 {@code core} 里的目标，朝 {@code core} 的<b>最近点</b>拉。两个盒都能用
 * {@code AABB.ofSize(中心, 宽, 高, 宽)} 现算出来。
 *
 * <h2>速度给的是「位移」，不是「加速度」</h2>
 * 每刻直接推 {@code blocksPerTick} 格的水平位移。这一点是有意的：
 * 「每秒几格」是玩家能看见的事实，而<b>加速度</b>的最终速度由摩擦、地面材质、AI 速度共同决定，
 * 换算成「每秒几格」要反解一堆常数，改个地面就变。用位移则写多少就是多少。
 * 只给水平方向：竖直交给重力与悬浮/击飞，否则贴地怪会被一路吸进地里。
 *
 * <h2>两种用法</h2>
 * <ul>
 *   <li>持续：{@link #sustain(int)} —— 交给本节点自己每刻推进，到期自动停。
 *       这是推荐用法（初始削韧的「每个目标只砸一次」记在实例上，见下）。</li>
 *   <li>单次：{@link #execute()} —— 调用方自己每刻调。
 *       <b>要持续拉就复用同一个实例</b>：每次 {@code new} 一个新的都会把初始削韧重算一遍。</li>
 * </ul>
 *
 * <p>目标离开范围后，它的「已经砸过初始削韧」记录会被清掉，再进来重新砸一次。
 */
@EventBusSubscriber
public final class GatherPull {

    /**
     * 牵引等级，4 档：{@code L1} 最低、{@code L4} 最高。
     *
     * <p>等级只决定<b>初始削韧有多大</b>（也就是能打破多硬的目标），不决定速度 ——
     * 拉得动就是拉得动，想控制力度用 {@code blocksPerTick}，不要让等级兼职。
     */
    public enum Level {
        L1(1),
        L2(2),
        L3(3),
        L4(4);

        private final int value;

        Level(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }

        /**
         * 这一档的<b>初始削韧</b> —— 直接取同档韧性条的长度，见类注释。
         *
         * <p>于是等级与档位是一一对应的：{@code L1} 破 1 档、{@code L4} 破 4 档，
         * 不需要再单开一张表。
         */
        public float burstPoise() {
            return PoiseTiers.profileOf(value).length();
        }

        /** 按数值取档，超出 1–4 会被钳进区间（配置里写大了也不会崩）。 */
        public static Level of(int value) {
            Level[] all = values();
            int index = Math.clamp(value, 1, all.length) - 1;
            return all[index];
        }
    }

    /** 一秒的刻数 —— 速度换算用。 */
    public static final double TICKS_PER_SECOND = 20.0;

    /**
     * 默认牵引速度：每秒 3 格。
     *
     * <p>肉眼看得见、但不会瞬移的手感；要更慢/更快就
     * {@link #withBlocksPerSecond(double)}，别去改这个常量（它是「不指定时」的兜底）。
     */
    public static final double DEFAULT_BLOCKS_PER_SECOND = 3.0;

    /**
     * 破韧后持续牵引时，每刻给韧性驻留续上的暂停刻数。
     *
     * <p>取一个略大于 1 的值：牵引是每刻刷新的，续成 5 刻就能吸收 tick 顺序上的错位，
     * 又短到「牵引一停，驻留立刻接着走」。
     */
    private static final int RESET_HOLD_TICKS = 5;

    /** 正在自动推进的持续牵引。 */
    private static final List<Handle> ACTIVE = new ArrayList<>();

    private final Entity owner;
    private final Level level;
    private final AABB core;
    private final AABB range;
    private final double blocksPerTick;
    private final float poisePerSecond;
    private final Predicate<LivingEntity> filter;

    /** 已经砸过初始削韧的目标；离开范围就清掉，再进来重新砸。 */
    private final Set<UUID> bursted = new HashSet<>();

    /**
     * @param owner           施法者：不会被自己牵引，也决定以谁的世界为执行侧
     * @param level           本次牵引的等级（4 档 = 初始削韧的 4 档）
     * @param core            牵引核心：往哪牵引（区域，不是点）
     * @param range           牵引范围：影响范围
     * @param blocksPerTick   每刻的水平位移（格/刻）
     * @param poisePerSecond  牵引期间每秒再削掉的韧性（0 = 只砸初始那一笔）
     * @param filter          额外的目标过滤（例如「不拉玩家」）；施法者自己始终被排除
     */
    public GatherPull(Entity owner, Level level, AABB core, AABB range, double blocksPerTick,
                      float poisePerSecond, Predicate<LivingEntity> filter) {
        this.owner = owner;
        this.level = level;
        this.core = core;
        this.range = range;
        this.blocksPerTick = blocksPerTick;
        this.poisePerSecond = poisePerSecond;
        this.filter = filter;
    }

    /** 只砸初始那一笔、不额外持续削韧、不额外过滤。 */
    public GatherPull(Entity owner, Level level, AABB core, AABB range, double blocksPerTick) {
        this(owner, level, core, range, blocksPerTick, 0f, target -> true);
    }

    /** 用 {@link #DEFAULT_BLOCKS_PER_SECOND} 建一个牵引节点。 */
    public GatherPull(Entity owner, Level level, AABB core, AABB range) {
        this(owner, level, core, range, perTick(DEFAULT_BLOCKS_PER_SECOND));
    }

    /** 「每秒几格」换算成「每刻几格」。 */
    public static double perTick(double blocksPerSecond) {
        return blocksPerSecond / TICKS_PER_SECOND;
    }

    /** 换一个速度（按「每秒几格」给）。 */
    public GatherPull withBlocksPerSecond(double blocksPerSecond) {
        return new GatherPull(owner, level, core, range, perTick(blocksPerSecond), poisePerSecond, filter);
    }

    /** 追加「牵引期间持续削韧」（每秒多少削韧值）。 */
    public GatherPull withPoisePerSecond(float value) {
        return new GatherPull(owner, level, core, range, blocksPerTick, value, filter);
    }

    /** 限定只拉哪些目标（例如 {@code target -> !(target instanceof Player)}）。 */
    public GatherPull withFilter(Predicate<LivingEntity> value) {
        return new GatherPull(owner, level, core, range, blocksPerTick, poisePerSecond, value);
    }

    /**
     * 执行一次聚怪 —— 持续存在的领域每刻调一次，一次性技能调一次。
     *
     * <p>注意返回值的含义：<b>被这次聚怪处理到的目标</b>，其中包括
     * 「只吃到削韧、还没被拉动」的那些（未破韧时就是这样）。
     *
     * @return 本次被处理到的目标（含只在掉韧性的）
     */
    public List<LivingEntity> execute() {
        List<LivingEntity> handled = new ArrayList<>();
        if (owner.level().isClientSide()) {
            return handled;
        }
        List<LivingEntity> candidates = owner.level().getEntitiesOfClass(LivingEntity.class, range);

        // 目标离开范围就忘掉「砸过初始削韧」这件事，下次进来重新砸
        if (!bursted.isEmpty()) {
            Set<UUID> inRange = new HashSet<>();
            for (LivingEntity candidate : candidates) {
                if (canGather(candidate)) {
                    inRange.add(candidate.getUUID());
                }
            }
            bursted.retainAll(inRange);
        }

        for (LivingEntity target : candidates) {
            if (!canGather(target)) {
                continue;
            }
            if (pullOne(target)) {
                handled.add(target);
            }
        }
        return handled;
    }

    /**
     * 持续聚怪：交给本节点自己每刻推进，到期自动停。
     *
     * <p>「持续」的语义就是每刻调一次 {@link #execute()}，所以这里没有额外状态，
     * 只有一个倒计时和最近一次处理到的目标列表（诊断用）。
     *
     * @param durationTicks 持续刻数；≤0 时返回一个立即失效的句柄
     */
    public Handle sustain(int durationTicks) {
        Handle handle = new Handle(this, durationTicks);
        if (handle.isActive() && !owner.level().isClientSide()) {
            ACTIVE.add(handle);
        }
        return handle;
    }

    /** 正在自动推进的持续牵引数量（诊断用）。 */
    public static int activeCount() {
        return ACTIVE.size();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Handle> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().tick()) {
                iterator.remove();
            }
        }
    }

    /**
     * 对单个目标走一遍聚怪。
     *
     * <p>顺序固定，别调换：<b>先削韧，再看破没破，破了才拉。</b>
     *
     * @return 这个目标算不算「被这次聚怪处理到」
     */
    private boolean pullOne(LivingEntity target) {
        // ① 初始高额削韧：每个目标每次进入范围只砸一次（这才是「聚怪等级」的区别所在）。
        //    走控制入口的「只削韧」请求 —— 它只是把韧性条推下去，不是控制（未破韧不能拉）。
        if (bursted.add(target.getUUID())) {
            ControlService.apply(target, ControlRequest.poise(level.burstPoise(), null));
        }
        // ② 牵引期间的持续削韧（可选）
        if (poisePerSecond > 0f) {
            ControlService.apply(target,
                    ControlRequest.poise((float) (poisePerSecond / TICKS_PER_SECOND), null));
        }
        // ③ 没破韧 → 到此为止。未破韧就是不能聚怪，只掉韧性条
        if (!ControlService.canApply(target, ControlType.GATHER)) {
            return true;
        }
        // ④ 破了韧才拉：按住它的移动 AI、清掉攒下的水平冲量（破韧后没法反抗），
        //    持续牵引同时把韧性恢复按住，破绽窗口不会在拉着的时候自己走完
        MovementHold.hold(target);
        Vec3 motion = target.getDeltaMovement();
        target.setDeltaMovement(0.0, motion.y, 0.0);
        ControlService.onStrongControlApplied(target, ControlType.GATHER, RESET_HOLD_TICKS);

        Vec3 pos = target.position();
        if (core.contains(pos)) {
            return true;
        }

        Vec3 delta = nearestPointInCore(pos).subtract(pos);
        double distance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (distance < 1.0E-6) {
            // 正好在核心区正上/正下方：没有水平分量可给，交给重力
            return true;
        }

        // 离得比一步还近就不越过核心区边界，免得在核心区边上反复抖
        double step = Math.min(blocksPerTick, distance);
        target.move(MoverType.SELF,
                new Vec3(delta.x / distance * step, 0.0, delta.z / distance * step));
        target.hurtMarked = true;
        return true;
    }

    /**
     * 能不能对目标施加聚怪（<b>不判破没破韧</b> —— 没破韧也要砸那一笔初始削韧）。
     *
     * <p>三道门，顺序固定：
     * <ol>
     *   <li><b>护盾</b>：有盾 = 霸体，聚怪整个无效（连削韧那一笔也没有，{@link ControlService} 管）；</li>
     *   <li><b>免疫</b>：三个 BOSS、其它模组的实体 —— 谁也拉不动（{@link PoiseTiers#gatherResist}）；</li>
     *   <li><b>过滤</b>：调用方自己的排除规则（例如不拉玩家、不拉友军）。</li>
     * </ol>
     */
    public boolean canGather(LivingEntity target) {
        if (target == owner) {
            return false;
        }
        if (!target.isAlive() || target.isRemoved() || target.isSpectator()) {
            return false;
        }
        if (!filter.test(target)) {
            return false;
        }
        if (ControlService.hasSuperArmorShield(target)) {
            return false;
        }
        return !PoiseTiers.isImmune(PoiseTiers.gatherResist(target));
    }

    /** 把世界坐标钳进牵引核心盒，得到「离它最近的那个点」。 */
    private Vec3 nearestPointInCore(Vec3 pos) {
        return new Vec3(
                Math.clamp(pos.x, core.minX, core.maxX),
                Math.clamp(pos.y, core.minY, core.maxY),
                Math.clamp(pos.z, core.minZ, core.maxZ));
    }

    /** 一次持续牵引的句柄：看得到进度、处理到了谁，也能提前停。 */
    public static final class Handle {

        private final GatherPull pull;
        private int remainingTicks;
        private boolean stopped;
        private List<LivingEntity> lastHandled = List.of();

        private Handle(GatherPull pull, int durationTicks) {
            this.pull = pull;
            this.remainingTicks = Math.max(0, durationTicks);
        }

        /** 这套聚怪的参数（速度、范围、等级都在里面）。 */
        public GatherPull pull() {
            return pull;
        }

        /** 还在生效吗（到手就失效的句柄返回 false）。 */
        public boolean isActive() {
            return !stopped && remainingTicks > 0;
        }

        /** 还剩几刻（用于表现层提前收尾）。 */
        public int remainingTicks() {
            return remainingTicks;
        }

        /** 最近一刻处理到的目标（含只在掉韧性的）；第一刻之前是空表。 */
        public List<LivingEntity> lastHandled() {
            return lastHandled;
        }

        /** 提前停止（到期会自己停，不调也行）。 */
        public void stop() {
            this.stopped = true;
            this.remainingTicks = 0;
        }

        /** 推进一步；返回 false 表示该出表了。 */
        private boolean tick() {
            if (stopped || remainingTicks <= 0) {
                return false;
            }
            lastHandled = pull.execute();
            remainingTicks--;
            return remainingTicks > 0;
        }
    }
}
