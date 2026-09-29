package com.linweiyun.genshin.core.system.control;

import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import com.linweiyun.genshin.core.system.combat.action.ActionManager;
import com.linweiyun.genshin.core.system.combat.action.InterruptReason;
import com.linweiyun.genshin.core.system.poise.PoiseService;
import com.linweiyun.genshin.core.system.poise.PoiseTiers;
import com.linweiyun.genshin.core.system.poise.impact.ImpactLevel;
import com.linweiyun.genshin.core.system.poise.impact.ImpactSolver;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>控制接收方</b> —— 所有控制类效果只走<b>一个入口</b> {@link #applyControl}，
 * 由实体自己判定这一次是「削韧」、「控制」还是「直接免疫拦住」；
 * 打断动作、击退、冻结的 NoAI 都是<b>实体内置方法</b>。
 *
 * <h2>谁能拿到它</h2>
 * 本接口挂在 {@code TeyvatLiving} 上（{@code TeyvatLiving extends Controllable}），
 * 而原版 {@code LivingEntity} 由 {@code LivingEntityTeyvatMixin} 注入
 * {@code NonTeyvatEntity extends TeyvatLiving} —— 所以<b>每一个活体都有内置方法与入口</b>，
 * 本模组的怪与首领还能覆盖它们；原版生物拿到的是一套现成的默认实现，
 * 不需要为它多写一个类。
 *
 * <h2>一个入口，四种判定</h2>
 * <pre>
 *   applyControl(请求)
 *     ├─ 削韧那一笔（请求带削韧时）先落地 —— 它不问破没破，只看护盾
 *     └─ 再看控制：
 *          REFUSED → 免疫：有盾 = 霸体，或实体的 blocksControl（首领）拦下 —— 控制不生效
 *          POISE   → 削韧：没破韧，这一下只把韧性条往下推，不打断、不推
 *          WINDOW  → 控制：破绽窗口里那一下，无视抗打断系数
 *          CONTROL → 控制：破韧期间，命中类还要比「打断强度 ≥ 抗打断系数」
 * </pre>
 *
 * <p><b>注</b>：{@link Verdict#POISE} 与 {@link Verdict#REFUSED} 对调用方都是「控制没生效」，
 * 分开只是为了读日志与以后做表现（例如「被盾弹开」和「没破韧」是两种手感）。
 *
 * <h2>谁在执行</h2>
 * <ul>
 *   <li><b>命中带来的</b>（{@link ControlType#SOFT}、{@link ControlType#IMPACT}）：
 *       主效果是<b>打断动作</b> {@link #interruptAction}（强行回到静止），
 *       <b>击退是次要的、可以没有</b> {@link #knockback} —— 这一下没带冲击就一点都不推；
 *       两个都是内置方法，覆盖成「推不动」只改一个方法；</li>
 *   <li><b>冻结的 NoAI</b>（{@link ControlType#FREEZE}）：{@link #freezeAction}，
 *       和别的控制同一道门，但判定里<b>不看韧性</b>（冻结可以无视韧性直接成立）；</li>
 *   <li><b>技能每刻持续施加的</b>（{@link ControlType#LEVITATE}、{@link ControlType#GATHER}）：
 *       入口只开门，位移由节点自己执行（范围盒在那里）。</li>
 * </ul>
 *
 * <h2>绕过入口：{@link #forceControl}</h2>
 * 首领的技能在「抬手 / 蓄力 / 收招 / 破盾瞬间」这类时刻要<b>强行</b>给一次控制，
 * 就走 {@link ControlService#force}（内部调 {@link #forceControl}）：
 * 不问韧性、不问系数、不问窗口，直接把内置方法跑一遍。
 * 另有 {@link ControlService#openGap} 那种更轻的写法 —— 开一个一次性窗口，
 * 让「接下来那一下命中」自己从入口通过。
 */
public interface Controllable {

    /**
     * 日志口 —— 接口里只能放常量，所以它是 {@code public} 的（同理下面的
     * {@link #WITCH_DRINKING_MODIFIER}）。只在打断那一下用，不在热路径上。
     */
    Logger LOGGER = ModLog.getLogger(LogGroup.COMBAT);

    /**
     * 女巫喝药减速的修饰符 id —— 原版 {@code Witch} 用它加 -25% 移速，
     * <b>只在「喝完」时才拆</b>；打断时要照同一个 id 帮它拆掉（见 {@link #cancelWitchDrink}）。
     */
    Identifier WITCH_DRINKING_MODIFIER = Identifier.withDefaultNamespace("drinking");

    /** 这一次请求的判定结果。 */
    enum Verdict {
        /** 免疫 / 有盾：什么都不做（削韧也进不去）。 */
        REFUSED,
        /** 没破韧：只有削韧那一笔落地，控制不生效（R1）。 */
        POISE,
        /** 破韧期间：控制生效（命中类还要过抗打断系数）。 */
        CONTROL,
        /** 破绽窗口里那一次：控制生效，且无视抗打断系数。 */
        WINDOW
    }

    /** 自己 —— 本接口由活体实现（见类注释），所以这一步永远是活体。 */
    default LivingEntity controllableSelf() {
        return (LivingEntity) this;
    }

    // ==================== 入口 ====================

    /**
     * <b>所有控制类效果唯一的入口</b>：削韧先落地，然后由 {@link #verdict} 决定控制生不生效。
     *
     * @return true = 控制生效了（{@link Verdict#POISE} 与 {@link Verdict#REFUSED} 都返回 false）
     */
    default boolean applyControl(ControlRequest request) {
        LivingEntity self = controllableSelf();
        if (request == null || request.type() == null || self == null
                || self.level().isClientSide() || !self.isAlive()) {
            return false;
        }
        // ① 削韧那一笔先落地：它不问破没破（破没破正是它决定的），只看护盾 ——
        //    有盾 = 霸体，PoiseService 内部会把这一笔整个挡掉。
        if (request.poise() > 0f) {
            PoiseService.accumulate(self, request.poise(), request.source());
        }
        // ② 控制那一半：此刻读到的 isBroken 已经包含「这一下正好把它打破」的结果，
        //    所以破韧的那一下本身也算控制（破韧期间第一次命中）。
        Verdict verdict = verdict(request);
        if (verdict != Verdict.CONTROL && verdict != Verdict.WINDOW) {
            return false;
        }
        runControl(request, verdict == Verdict.WINDOW);
        return true;
    }

    /**
     * 判定这一次请求算不算放行 —— <b>只判不做</b>，也不消费破绽窗口
     * （窗口在 {@link #runControl} 真正执行时才用掉）。
     *
     * <p>技能节点（悬浮 / 聚怪）每刻问的就是这一条：它们的请求
     * {@link ControlRequest#hitDriven()} 为 false，所以既不会消费窗口，也不会执行内置效果。
     */
    default boolean allowsControl(ControlRequest request) {
        Verdict verdict = verdict(request);
        return verdict == Verdict.CONTROL || verdict == Verdict.WINDOW;
    }

    /**
     * 门控 —— 「什么效果、在什么情况下能落到它身上」的唯一判断处。
     *
     * <p>顺序不许调换，理由：
     * <ol>
     *   <li><b>冻结</b>排在最前：它<b>无视韧性</b>（用户口径「冻结可以无视韧性直接冻结」），
     *       只问实体吃不吃冻结（首领免疫）；解冻永远放行；</li>
     *   <li><b>有盾 = 霸体</b>：玩家与敌人同一套（R9）。盾还在，控制一律无效 ——
     *       连破绽窗口都挡掉；</li>
     *   <li><b>破绽窗口</b>：排在「没破韧」之前，窗口的语义才是「这一次一定成立」；</li>
     *   <li><b>没破韧</b>：控制不生效（R1），但已经落地的削韧不受影响；</li>
     *   <li><b>{@link #blocksControl}</b>：实体内置的例外，给首领用。</li>
     * </ol>
     */
    default Verdict verdict(ControlRequest request) {
        LivingEntity self = controllableSelf();
        if (request == null || request.type() == null || self == null
                || self.level().isClientSide() || !self.isAlive()) {
            return Verdict.REFUSED;
        }
        // ① 冻结：无视韧性，只问实体吃不吃
        if (request.type() == ControlType.FREEZE) {
            if (!request.frozen()) {
                return Verdict.CONTROL;   // 解冻永远放行，不然会被永久冻住
            }
            return blocksControl(request) ? Verdict.REFUSED : Verdict.CONTROL;
        }
        // ② 有盾 = 霸体：控制一律无效，破绽窗口也挡
        if (ControlService.hasSuperArmorShield(self)) {
            return Verdict.REFUSED;
        }
        // ③ 破绽窗口（一次性）：只有命中类请求认得它
        if (request.hitDriven() && ControlService.hasGap(self)) {
            return Verdict.WINDOW;
        }
        // ④ 没破韧：控制不生效 —— 这一下最多只是把韧性条推下去
        if (!PoiseService.isBroken(self)) {
            return request.poise() > 0f ? Verdict.POISE : Verdict.REFUSED;
        }
        // ⑤ 破韧了但实体要拦（首领）：拦的是控制，削韧照吃
        if (blocksControl(request)) {
            return Verdict.REFUSED;
        }
        return Verdict.CONTROL;
    }

    /**
     * 覆盖点：<b>破韧也照样拦</b>吗？默认 false（破韧了就放行）。
     *
     * <p>首领（{@code ITeyvatBoss}）覆盖成 true —— 它的控制只能从
     * {@link ControlService#force} 或破绽窗口那两条路进来，连冻结也一并免疫。
     * 精英怪不用覆盖这一条：把 {@code super_armor} 配到 3~5 就得到
     * 「轻击打不断、击退/击飞才打断」。
     */
    default boolean blocksControl(ControlRequest request) {
        return false;
    }

    // ==================== 直通口 ====================

    /**
     * <b>绕过入口</b>直接触发控制 —— 不问韧性、不问系数、不问窗口。
     *
     * <p>给「条件已经达成」的场合用：首领技能在抬手 / 蓄力 / 收招 / 破盾瞬间强行打断一次、
     * 剧情脚本、调试指令。普通攻击<b>不要</b>走这条，那条路必须过
     * {@link #applyControl}，否则「破韧前不吃控制」会被绕开。
     */
    default void forceControl(ControlRequest request) {
        LivingEntity self = controllableSelf();
        if (request == null || request.type() == null || self == null || self.level().isClientSide()) {
            return;
        }
        if (request.poise() > 0f) {
            PoiseService.accumulate(self, request.poise(), request.source());
        }
        runControl(request, true);
    }

    /**
     * 执行一次放行的控制 —— 入口与直通口共用。
     *
     * @param window true = 破绽窗口 / 直通口那一次：打断无视抗打断系数，并把窗口用掉
     */
    default void runControl(ControlRequest request, boolean window) {
        switch (request.type()) {
            // 只削韧：入口里已经落完了，这里什么都不用做
            case POISE -> {
            }
            // 冻结的 NoAI：和别的控制同一道门，判定里已经放行
            case FREEZE -> freezeAction(request.frozen());
            // 悬浮与牵引：门开了，位移由节点自己执行（范围盒在那边）
            case LEVITATE, GATHER -> {
            }
            case SOFT, IMPACT -> applyHitControl(request, window);
        }
    }

    /**
     * 命中带来的那一半：<b>打断动作（主）</b> + <b>击退（次）</b>。
     *
     * <p>用户口径（2026-09-25）：「<b>击退是次要的，不一定有，最主要的是打断他的动作</b>
     * —— 插入他的 AI 逻辑，让其强行回到静止状态，比如女巫本来在喝药直接打断。」
     * 所以两件事彻底拆开，判据<b>故意不同</b>：
     * <ul>
     *   <li><b>打断（主）</b>：只要这一下带「冲击」（{@link ImpactLevel} 等级 &gt; 0），
     *       就判 «打断强度 ≥ 抗打断系数» —— 普通敌人系数 1，所以破韧期间随便一下都能打断
     *       （微颤 1 ≥ 1，连弓/法器都算）；精英怪配到 3~5 就得靠击退/击飞档的强度。
     *       请求没写冲击（{@code null}）时按微颤 1 算，也就是「只打断、不推」。
     *       唯一例外是 {@link ImpactLevel#NONE 无影响}（等级 0）：既不打断也不推。
     *       破绽窗口那一次（{@code window}）与直通口无视这条比较；</li>
     *   <li><b>击退（次）</b>：与系数无关，只按重量解算（{@link ImpactSolver}）——
     *       「推不推得动」和「打不打得断」是两件事，所以「打断而不推」是合法且常用的组合；
     *       冲击为 {@code null} 时一点都不推。首领由 {@link #blocksControl} 整条拦下，
     *       所以连推都不推。</li>
     * </ul>
     */
    default void applyHitControl(ControlRequest request, boolean window) {
        LivingEntity self = controllableSelf();
        ImpactLevel impact = request.impact();
        int strength = impact == null ? ImpactLevel.TREMBLE.interruptStrength()
                : impact.interruptStrength();
        if (strength <= 0) {
            // 无影响（等级 0）：既不打断也不推
            return;
        }
        if (window || strength >= PoiseService.superArmorOf(self)) {
            if (window) {
                ControlService.consumeGap(self);
            }
            interruptAction(request.holdTicks());
        }
        knockback(impact, ControlService.originOf(request.source()));
    }

    // ==================== 实体内置效果 ====================

    /**
     * <b>实体内置方法：打断当前动作 —— 强行回到静止状态。</b>
     *
     * <p>这是控制里的<b>主效果</b>（用户口径：「击退是次要的，不一定有，
     * 最主要的是打断他的动作」）。做法就是「插入它的 AI 逻辑」，四步：
     * <ol>
     *   <li>{@link #cancelOngoingAction 取消进行中的动作} —— 含女巫喝药那种
     *       写在 {@code aiStep} 里、光靠 AI 开关拦不住的状态；</li>
     *   <li><b>把正在跑的 Goal 逐个 {@code stop()}</b> —— 这才是真的取消动作，
     *       Goal 自己的 {@code stop()} 会跑，冷却与复位都在那里；只按住 AI 不取消的话，
     *       松开之后它会接着把那一刀挥完；</li>
     *   <li>移动控制器回到待机 + 停掉导航 + {@link #haltMovement 清掉移动意图与水平动量}
     *       —— 按住期间原版不给摩擦，不清动量它会一直滑；</li>
     *   <li>{@link ControlService#openStandStill 开一个静止窗口}（很短）：窗口内
     *       {@code MobServerAiStepMixin} 直接掐掉目标的 <b>{@code serverAiStep()}</b> ——
     *       AI 的总闸（探测、目标与 Goal 的取舍、导航、{@code customServerAiStep}、
     *       移动/视线/跳跃三个控制器都在它下面），一处拦下 = 走 AI 的动作整类都停下；
     *       闸门够不到的（女巫喝药在 {@code aiStep}、苦力怕引信在 {@code tick}）由窗口里
     *       <b>每刻</b>重调一次的 {@link #cancelOngoingAction} 按住 ——
     *       不然它们下一 tick 就自己接上了。目的是打断当前的 AI 循环，不是长期锁住它。</li>
     * </ol>
     *
     * <p><b>玩家</b>：取消动作要走 {@link ActionManager}（内部自带「执行期不可打断」），
     * 所以这里换成 {@code ActionManager.interrupt}，不是去停 Goal。
     * <b>别的模组的实体</b>整个跳过：它的 Goal 怎么处理 {@code stop()} 我们不知道。
     *
     * @param ticks 按住多少刻（≤0 用 {@link ControlRequest#HIT_TICKS}）
     */
    default void interruptAction(int ticks) {
        LivingEntity self = controllableSelf();
        if (self == null || self.level().isClientSide()) {
            return;
        }
        int hold = ticks > 0 ? ticks : ControlRequest.HIT_TICKS;
        if (self instanceof Player player) {
            ActionManager.get(player).interrupt(InterruptReason.DAMAGE);
            return;
        }
        if (!(self instanceof Mob mob) || PoiseTiers.isForeign(mob)) {
            return;
        }
        cancelOngoingAction(mob);
        stopRunningGoals(mob);
        mob.getMoveControl().setWait();
        mob.getNavigation().stop();
        haltMovement(mob);
        ControlService.openStandStill(mob, hold);
        LOGGER.debug("[Control] 打断 {} 的动作（静止窗口 {} 刻）", mob.getName().getString(), hold);
    }

    /**
     * <b>实体内置方法：取消「正在进行中的动作」。</b>
     *
     * <p>「停 Goal + 禁 AI」<b>拦不住所有动作</b>：有些怪把动作写在自己的
     * {@code aiStep} 里，而那段逻辑根本不看 AI 开关。最典型的就是
     * {@link Witch 女巫喝药} —— 她的 {@code aiStep} 每刻自己推进 {@code usingTime}，
     * 冻住（{@code setNoAi(true)}）她照样会把那瓶药喝完；她也不走
     * {@link LivingEntity#stopUsingItem()} 那条通用 use 通道，而是用自己的
     * {@code DATA_USING_ITEM}。所以打断必须点名清掉这类状态：
     * <ul>
     *   <li>通用 use 动作（吃喝、举盾、拉弓）→ {@link LivingEntity#stopUsingItem()}；</li>
     *   <li>挥击动画停在半途 → {@code swinging} / {@code swingTime} / {@code attackAnim} 归零；</li>
     *   <li>女巫的喝药 → {@link #cancelWitchDrink(Witch)}（只把标记翻掉<b>不够</b>，见那边）。</li>
     * </ul>
     *
     * <p><b>本模组的怪或首领要打断自己特有的动作</b>（读条、变形、蓄力状态机），
     * <b>覆盖这个方法加一句即可</b>（纯 Java，不用碰 mixin）；它会被
     * {@link #interruptAction} 与静止窗口里的每刻钩子（{@code ControlService.onServerTick}）
     * 各调一次，所以里面写的必须是<b>幂等</b>的。
     *
     * <p><b>原版 / 别的模组的生物</b>没法在这里点名（它们的私有字段模组碰不到），
     * 那条路是 {@link TickActionSuppressor}：写一个 mixin 实现它，
     * 压制逻辑由本方法转发过去（苦力怕引信、劫掠兽的计时器就是这么接的）。
     */
    default void cancelOngoingAction(Mob mob) {
        mob.stopUsingItem();
        mob.swinging = false;
        mob.swingTime = 0;
        mob.attackAnim = 0.0F;
        mob.oAttackAnim = 0.0F;
        if (mob instanceof Witch witch && witch.isDrinkingPotion()) {
            cancelWitchDrink(witch);
        }
        // 不写在 AI 管线里的状态机（原版那些私有的计时器 / 触发标记）：
        // 由各自的 mixin 实现 TickActionSuppressor 接上，这里不点名任何一只怪。
        if (mob instanceof TickActionSuppressor suppressor) {
            suppressor.minegenshin$suppressTickAction();
        }
    }

    /**
     * <b>把女巫手里那瓶药真正作废。</b>
     *
     * <p>原版 {@code Witch#aiStep} 的喝药是<b>她自己的另一套</b>：{@code isDrinkingPotion()}
     * 为真就推进私有的 {@code usingTime}，走完才把药喝掉、清主手、拆掉减速。
     * 所以只把标记翻成 {@code false} 会留下三件事 —— 这正是「打了她还在喝、
     * 手上一直拿着药瓶」的原因：
     * <ol>
     *   <li><b>主手那瓶药还在</b>（原版只在喝完时清），表现上就是「手里一直拿着瓶子」；</li>
     *   <li>下一 tick 她直接进「挑一瓶喝」的分支，随机判定一过就<b>重新起手</b>，
     *       看上去完全没被打断；</li>
     *   <li>{@code SPEED_MODIFIER_DRINKING}（-25% 速度）只在喝完时移除 ——
     *       被打断就永远留在身上，她后半辈子都是慢的。</li>
     * </ol>
     *
     * <p>所以这里三件事一起做：翻标记、把主手的药拿走、拆掉喝药减速。
     * {@code usingTime} 不用管：她下次起手会重新赋值，而「标记 + 主手」一起清掉之后，
     * 那一瓶已经不可能被喝掉了。
     */
    static void cancelWitchDrink(Witch witch) {
        witch.setUsingItem(false);
        witch.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        AttributeInstance speed = witch.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(WITCH_DRINKING_MODIFIER);
        }
    }

    /**
     * <b>实体内置方法：把移动清到静止。</b>
     *
     * <p>打断要的是「<b>当场</b>停下来」，所以不等摩擦慢慢磨：移动意图
     * （{@code xxa / yya / zza}，原版每刻用它们算 {@code travel}）与水平动量一起清掉。
     * 竖直分量留着 —— 免得把正在腾空的怪按到地面高度。
     *
     * <p>静止窗口里 AI 整段不跑（{@code serverAiStep} 被闸门掐掉），别的新 Goal
     * 也改不了这几个值；这一步是让「回到静止」在打断的那一刻就成立，而不是等下一拍。
     */
    static void haltMovement(Mob mob) {
        mob.xxa = 0.0F;
        mob.yya = 0.0F;
        mob.zza = 0.0F;
        Vec3 motion = mob.getDeltaMovement();
        mob.setDeltaMovement(0.0, motion.y, 0.0);
        mob.hurtMarked = true;      // 服务端改速度后必须标一下，客户端才收得到
        mob.setSprinting(false);
    }

    /**
     * <b>实体内置方法：击退。</b>
     *
     * <p><b>次要效果</b>：一次命中的主效果是 {@link #interruptAction 打断动作}，
     * 击退只是「这一下确实推得动」时顺带的位移，可以是完全没有。
     *
     * <p>判定（重量门槛、空中规则）与冲量施加全在 {@link ImpactSolver}，
     * 这里只是把它挂到实体上，让首领一类的目标能覆盖成「推不动」。
     */
    default void knockback(@Nullable ImpactLevel impact, @Nullable Vec3 origin) {
        LivingEntity self = controllableSelf();
        if (self == null || impact == null || self.level().isClientSide()) {
            return;
        }
        if (self instanceof Player) {
            // 玩家的位移交给原版击退：我们这套冲量是「替换水平分量」的，
            // 落在玩家身上会把疾跑与自己的移动一起抹掉。
            return;
        }
        ImpactSolver.apply(self, impact, origin);
    }

    /**
     * <b>实体内置方法：冻结的 NoAI。</b>
     *
     * <p>原来这一步直接写在 {@code ColdElement} 里（每 tick 按有没有冻元素
     * {@code setNoAi}），现在搬到实体上：这样它和打断、击退走<b>同一个入口</b>，
     * 首领一类实体在 {@link #blocksControl} 里说一句就能连冻结一起免疫，
     * 而任何实体想改「冻住长什么样」（比如冻结期间还能转头）也只改这一个方法。
     *
     * <p>幂等：状态没变就不碰 {@code NoAI}，避免每 tick 反复写。
     * 与 {@link MovementHold} 抢同一个开关是安全的 —— 那边记着「持握前的 NoAI」，
     * 冻结每 tick 会重算，谁错了都会被对方纠回来。
     */
    default void freezeAction(boolean frozen) {
        LivingEntity self = controllableSelf();
        if (!(self instanceof Mob mob) || self.level().isClientSide()) {
            return;
        }
        if (mob.isNoAi() != frozen) {
            mob.setNoAi(frozen);
        }
    }

    /**
     * 停掉这只怪当前在跑的所有 Goal。
     *
     * <p>先快照再停，避免边遍历边改集合（{@code stop()} 会动 GoalSelector 的状态）。
     */
    static void stopRunningGoals(Mob mob) {
        List<WrappedGoal> running = null;
        for (WrappedGoal wrapped : mob.getGoalSelector().getAvailableGoals()) {
            if (wrapped.isRunning()) {
                if (running == null) {
                    running = new ArrayList<>(4);
                }
                running.add(wrapped);
            }
        }
        if (running == null) {
            return;
        }
        for (WrappedGoal wrapped : running) {
            wrapped.stop();
        }
    }
}
