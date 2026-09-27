package com.linweiyun.genshin.core.system.control;

import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.genshin.core.system.poise.PoiseService;
import com.linweiyun.genshin.core.system.poise.impact.ImpactLevel;
import com.linweiyun.genshin.core.system.shield.ShieldService;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 控制的<b>静态入口与公用件</b>。
 *
 * <h2>职责怎么分（三个类，别搞混）</h2>
 * <table border="1">
 *   <caption>控制系统的三个面</caption>
 *   <tr><th>类</th><th>管什么</th></tr>
 *   <tr><td>{@link ControlType} / {@link ControlRequest}</td>
 *       <td>描述「这是什么控制、多强、从哪来」</td></tr>
 *   <tr><td>{@link Controllable}</td>
 *       <td><b>实体内置方法</b>（打断动作、击退）与<b>唯一入口</b> {@code applyControl}
 *           —— 门控与效果都在实体上，所以首领覆盖一个方法就能拦住</td></tr>
 *   <tr><td>本类</td>
 *       <td>没有实体可挂的那部分：<b>破绽窗口</b>、<b>命中入口</b> {@link #onHit}、
 *           护盾的集中读口、强控的「按住韧性恢复」</td></tr>
 * </table>
 *
 * <h2>命中从哪进来</h2>
 * 伤害管线每挨一下调一次 {@link #onHit}：它把「这一下是什么冲击」从伤害来源里取出来，
 * 包成一个 {@link ControlRequest#hit} 交给目标的 {@link Controllable#applyControl}。
 * <b>只在这一处取冲击</b>，所以玩家侧与怪物侧不会再各写一份判据
 * （早先那两份就是这么漂开的）。
 *
 * <h2>破绽窗口</h2>
 * 首领一类的目标平时打不断，只有它自己的节奏上（抬手 / 蓄力 / 收招 / 破盾瞬间）
 * 才露出一瞬间的破绽：{@link #openGap} 开一个<b>一次性</b>窗口，
 * 窗口期内<b>下一次命中</b>无视韧性与抗打断系数直接打断，然后窗口就用掉了。
 * 想更直接（不靠命中触发）就用 {@link #force}。
 *
 * <h2>静止窗口</h2>
 * {@link #openStandStill} 是「打断」的另一半：接下来很短的一段时间里，目标被三个层各管一段 ——
 * <ol>
 *   <li><b>AI 闸门</b>（{@code MobServerAiStepMixin}）：窗口内目标的 {@code serverAiStep()}
 *       被整段掐掉。那是 AI 的总闸，Goal、Brain、导航、探测、三个控制器全在它下面，
 *       所以「走 AI 的动作」这一整类不用点名就都停了；</li>
 *   <li><b>每刻取消钩子</b>（本类的 {@code onServerTick}）：窗口里每刻调一次
 *       {@link Controllable#cancelOngoingAction} —— 拦那些不写在 AI 管线里的动作，
 *       并且给模组自己的生物留一个纯 Java 的覆盖点；</li>
 *   <li><b>{@link TickActionSuppressor}</b>：面对的是原版里连闸门都拦不住的状态机
 *       （苦力怕引信在 {@code tick()}、劫掠兽的计时器在 {@code aiStep()}）——
 *       每为一种这样的生物写一个 mixin 实现它，核心代码不用点名任何一只怪。</li>
 * </ol>
 * 三层都是通用落点，不依赖具体是哪只怪，也不依赖 {@link MovementHold}
 * （那条路是给牵引/悬浮用的长按住）。
 */
@EventBusSubscriber
public final class ControlService {

    /** 开着破绽窗口的目标 → 窗口到期的游戏刻。只影响服务端判定，不需要同步。 */
    private static final Map<LivingEntity, Long> GAPS = new IdentityHashMap<>();

    /** 开着<b>静止窗口</b>的目标 → 窗口到期的游戏刻。同上，纯服务端。 */
    private static final Map<LivingEntity, Long> STANDSTILL = new IdentityHashMap<>();

    private ControlService() {
    }

    // ==================== 取控制接收方 ====================

    /**
     * 目标身上的控制接收方；理论上永远拿得到（每一个 {@code LivingEntity}
     * 都由 {@code LivingEntityTeyvatMixin} 注入了 {@link Controllable}），
     * 拿不到就按「什么都不做」处理，不要在这里抛异常打断伤害管线。
     */
    @Nullable
    public static Controllable of(@Nullable LivingEntity target) {
        return target instanceof Controllable controllable ? controllable : null;
    }

    /** 目标现在是不是「有霸体盾」—— 集中的读取口，避免各处自己去问护盾。 */
    public static boolean hasSuperArmorShield(LivingEntity target) {
        return ShieldService.blocksControl(target);
    }

    // ==================== 三个入口 ====================

    /**
     * 走<b>唯一入口</b>：门控 + 内置效果。绝大多数调用方要的是这个。
     *
     * @return true = 放行了
     */
    public static boolean apply(@Nullable LivingEntity target, ControlRequest request) {
        Controllable controllable = of(target);
        return controllable != null && controllable.applyControl(request);
    }

    /**
     * 走<b>直通口</b>：绕过门控，直接执行内置效果。
     *
     * <p>只在「条件已经达成」时用（首领技能的那一次强行打断、脚本、调试）。
     */
    public static void force(@Nullable LivingEntity target, ControlRequest request) {
        Controllable controllable = of(target);
        if (controllable != null) {
            controllable.forceControl(request);
        }
    }

    /**
     * 技能节点用的<b>门控查询</b>：这类控制现在能不能落到它身上。
     *
     * <p>只问不做 —— 悬浮、聚怪这些的位移由节点自己执行（范围盒在那边）。
     * 传进来的是技能类请求，所以不会消费破绽窗口。
     */
    public static boolean canApply(LivingEntity target, ControlType type) {
        Controllable controllable = of(target);
        return controllable != null && controllable.allowsControl(ControlRequest.skill(type));
    }

    /**
     * <b>命中入口</b> —— 伤害管线每挨一下调一次。
     *
     * <p>「削韧本质上就是给敌人造成一次控制」：破韧期间每一次命中都会走这里，
     * 于是每一次命中都有「打断动作 + 击退」这一次小控制；没破韧时判定在
     * {@link Controllable#verdict} 里落成 {@link Controllable.Verdict#POISE} ——
     * 只有削韧那一笔进韧性条，控制不生效。
     *
     * @param poise  这一下的削韧值（已经按武器表 / 招式系数算好）
     * @param source 这一下的伤害来源；可以为 {@code null}（纯削韧、无来源的冲击）
     * @return true = 这一下真的控制到了它
     */
    public static boolean onHit(@Nullable LivingEntity target, float poise, @Nullable DamageSource source) {
        if (target == null) {
            return false;
        }
        return apply(target, ControlRequest.hit(poise, impactOf(source), source));
    }

    /**
     * 这一下的<b>冲击</b>是什么。
     *
     * <p>招式没写过冲击时，{@link ModDamageSpec#getHitImpact} 会按攻击者的武器类型查表。
     * <b>不是本模组的伤害源</b>（摔伤、火焰、别的模组）没有这个概念，按
     * {@link ImpactLevel#TREMBLE 微颤}处理 —— 也就是「够得着普通敌人、推不动、
     * 也打不断硬目标」。
     */
    public static ImpactLevel impactOf(@Nullable DamageSource source) {
        if (!(source instanceof ModDamageSource modSource) || modSource.getSpec() == null) {
            return ImpactLevel.TREMBLE;
        }
        // 连攻击者一起传给规格：招式没显式写冲击时，按他的武器类型查表
        ModDamageSpec spec = modSource.getSpec();
        ImpactLevel level = spec.getHitImpact(spec.getAttackerCharacter());
        return level == null ? ImpactLevel.TREMBLE : level;
    }

    /** 冲击的来源点（攻击者位置），用来算「往哪边推」；没有攻击者就是 {@code null}。 */
    @Nullable
    public static Vec3 originOf(@Nullable DamageSource source) {
        Entity attacker = source == null ? null : source.getEntity();
        return attacker == null ? null : attacker.position();
    }

    // ==================== 破绽窗口 ====================

    /**
     * 开一个破绽窗口 —— 「特定时机被攻击会提供一次打断效果」的入口。
     *
     * <p>给首领一类高抗打断系数的目标用：它们平时打不断，只有在自己的节奏上
     * （抬手、蓄力、收招、破盾瞬间）才露出一次破绽。窗口是<b>一次性</b>的：
     * 被谁打断一次就消耗掉，不会整个窗口期间被反复打断。
     *
     * <p>窗口被消费发生在 {@link Controllable#verdict} 第 ② 步，所以它只管命中类请求；
     * 悬浮 / 聚怪那种每刻刷新的技能不会把窗口白白刷掉。
     *
     * @param ticks 窗口长度（刻）；≤0 或客户端调用什么都不做
     */
    public static void openGap(LivingEntity target, int ticks) {
        if (target == null || ticks <= 0 || target.level().isClientSide()) {
            return;
        }
        GAPS.put(target, target.level().getGameTime() + ticks);
    }

    /** 目标现在有没有开着的破绽窗口（只查询，不消费）。 */
    public static boolean hasGap(@Nullable LivingEntity target) {
        Long until = target == null ? null : GAPS.get(target);
        return until != null && target.level().getGameTime() <= until;
    }

    /**
     * 消费破绽窗口：开着就返回 true 并把它用掉。
     *
     * <p>只有 {@link Controllable#verdict} 该调它 —— 一个「命中」只问一次。
     */
    static boolean consumeGap(LivingEntity target) {
        Long until = GAPS.get(target);
        if (until == null) {
            return false;
        }
        GAPS.remove(target);
        return target.level().getGameTime() <= until;
    }

    // ==================== 强控的附加效果 ====================

    /**
     * 强控真的施加成功之后调一次 —— 把破韧驻留计时按住，破绽窗口就不会在
     * 被控制期间自己走完。
     *
     * <p>只有 {@link ControlType#pausesPoiseReset()} 为真的类别走这条
     * （悬浮、牵引）；软控与击退不暂停。
     *
     * @param ticks 这次控制预计还要持续多少刻
     */
    public static void onStrongControlApplied(LivingEntity target, ControlType type, int ticks) {
        if (!type.pausesPoiseReset() || ticks <= 0) {
            return;
        }
        PoiseService.pauseReset(target, ticks);
    }

    /**
     * 破绽窗口的过期清理 —— 挂在整个 tick 的最后，照 {@link MovementHold} 的写法。
     *
     * <p>窗口没被消费掉（这一轮没人打它）时就只是一条过期记录，
     * 实体被卸载 / 死亡也一样：不清就会随着「开过窗口的怪」一起慢漏。
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!GAPS.isEmpty()) {
            Iterator<Map.Entry<LivingEntity, Long>> iterator = GAPS.entrySet().iterator();
            while (iterator.hasNext()) {
                LivingEntity target = iterator.next().getKey();
                if (target.isRemoved() || !target.isAlive()
                        || target.level().getGameTime() > GAPS.get(target)) {
                    iterator.remove();
                }
            }
        }

        if (STANDSTILL.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<LivingEntity, Long>> standStill = STANDSTILL.entrySet().iterator();
        while (standStill.hasNext()) {
            Map.Entry<LivingEntity, Long> entry = standStill.next();
            LivingEntity target = entry.getKey();
            if (target.isRemoved() || !target.isAlive()
                    || target.level().getGameTime() > entry.getValue()) {
                standStill.remove();
                continue;
            }
            // 闸门（MobServerAiStepMixin）已经把 AI 那一整段按住了，
            // 这里补的是闸门够不着的部分：不写在 AI 管线里的动作，要每刻重新按下去
            // （女巫喝药下一 tick 就会自己接上，压一次是不够的）。
            if (target instanceof Mob mob) {
                holdStill(mob);
            }
        }
    }

    /**
     * 静止窗口里每刻做的事 —— AI 闸门之外的补充。
     *
     * <p>两件事：导航停住（窗口里它本来就不 tick，停一下免得窗口结束顺着旧路径继续走），
     * 以及继续取消进行中的动作。后者交给实体自己的
     * {@link Controllable#cancelOngoingAction}：本模组的怪在那里覆盖，
     * 原版的怪由 {@link TickActionSuppressor} 的 mixin 实现接上。
     */
    private static void holdStill(Mob mob) {
        mob.getNavigation().stop();
        Controllable controllable = of(mob);
        if (controllable != null) {
            controllable.cancelOngoingAction(mob);
        }
    }

    // ==================== 静止窗口 ====================

    /**
     * 开一个<b>静止窗口</b> —— 打断「当前 AI 循环」的那一段。
     *
     * <p>窗口内目标的 {@code Mob.serverAiStep()} 被整段掐掉（{@code MobServerAiStepMixin}），
     * 于是 AI 总闸以下的东西一次全停：探测、目标与 Goal 的取舍、导航、
     * {@code customServerAiStep}（Brain 类怪的 {@code getBrain().tick()} 在这里面）、
     * 移动 / 视线 / 跳跃三个控制器。闸门够不着的（不写在 AI 管线里的状态机）
     * 由窗口内<b>每刻</b>的 {@link Controllable#cancelOngoingAction} 补上。
     * 窗口<b>很短</b>（默认 {@link ControlRequest#HIT_TICKS}）——
     * 目的是打断，不是把目标定住；要长时间按住用 {@link MovementHold}。
     *
     * <p>窗口本身只是一个服务端时间戳：不动 {@code NoAI} 标记、不进 NBT，
     * 所以不会留下一只永久定身的怪，也不跟冻结抢同一个开关。
     * 当刻该做的（停 Goal、取消动作、清动量）在 {@link Controllable#interruptAction} 里
     * 已经做完了，窗口管的是「接下来别立刻重新起手」。
     *
     * @param ticks 窗口长度（刻）；≤0 或客户端调用什么都不做
     */
    public static void openStandStill(LivingEntity target, int ticks) {
        if (target == null || ticks <= 0 || target.level().isClientSide()) {
            return;
        }
        STANDSTILL.put(target, target.level().getGameTime() + ticks);
    }

    /**
     * 目标现在在不在静止窗口里。
     *
     * <p>AI 闸门（{@code MobServerAiStepMixin}）与每刻的取消钩子都问这一条；
     * 纯服务端的时间戳，客户端永远是 false。
     */
    public static boolean hasStandStill(@Nullable LivingEntity target) {
        Long until = target == null ? null : STANDSTILL.get(target);
        return until != null && target.level().getGameTime() <= until;
    }
}
