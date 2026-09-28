package com.linweiyun.genshin.core.system.shield;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.StatusContainer;
import com.linweiyun.genshin.core.element.GenshinElement;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.status.StatusInstance;
import com.linweiyun.genshin.core.system.about.ElementalAttachmentHelper;
import com.linweiyun.genshin.core.system.about.ElementalAttachmentInstance;
import com.linweiyun.genshin.core.system.combat.CombatAim;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.compat.PlayerStatBridge;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 护盾结算 —— 伤害与元素附着打进来时，先问盾。
 *
 * <h2>两条入口</h2>
 * <ul>
 *   <li>{@link #absorbDamage} —— 在 {@code LivingEntity#hurtServer} 的 HEAD 调用，
 *       返回「还有多少伤害能落到本体身上」。伤害本身照常走原版/本 MOD 管线，
 *       这里只决定它能不能到本体。</li>
 *   <li>{@link #onElementalAttack} —— 在 {@code DirectDamagePipeline} 的附着步骤之前调用，
 *       决定这次附着要不要被盾吞掉、以及要扣多少盾量。</li>
 * </ul>
 *
 * <h2>什么扣盾量、什么不扣</h2>
 * <table border="1">
 *   <caption>按 {@link ShieldBreakType} 分</caption>
 *   <tr><th>类型</th><th>伤害</th><th>元素附着</th><th>削韧</th></tr>
 *   <tr><td>{@link ShieldBreakType#ELEMENT} 元素盾</td><td>不扣</td><td>扣</td><td>扣</td></tr>
 *   <tr><td>{@link ShieldBreakType#DAMAGE} 伤害盾</td><td>扣</td><td>不扣</td><td>不扣</td></tr>
 *   <tr><td>{@link ShieldBreakType#MIXED} 混合盾</td><td>扣</td><td>扣</td><td>扣</td></tr>
 *   <tr><td>{@link ShieldBreakType#POISE} 白盾</td><td>不扣</td><td>不扣</td><td>扣</td></tr>
 * </table>
 *
 * <p>所以「纯元素盾」的表现是：伤害一点都进不来，但盾量只被元素和削韧磨掉 ——
 * 打九万伤害和打一百伤害对盾的影响完全一样。
 *
 * <p><b>非原神模式的兜底</b>：原版 / 其他 MOD 的普通攻击既没有 {@code ModDamageSpec}
 * （没有削韧值），也不带元素附着 —— 纯元素盾会永远打不破。所以接收端给这类攻击补一条
 * 「按物理非钝击算」的兜底削韧（{@link #plainAttackPoise}），每次
 * {@value #PLAIN_ATTACK_POISE} 点。原神模式玩家的攻击自带口径，不走这条。
 */
public final class ShieldService {

    /** 盾牌型盾的正面扇形半角（度）：只有落在这个范围内的攻击才打在盾面上。 */
    public static final float HELD_FRONT_HALF_ANGLE = 70f;

    /**
     * 非原神模式下「普通攻击」的兜底削韧量：按<b>物理非钝击</b>算，每次 10。
     *
     * <p>和本 MOD 一次普通攻击的基准削韧（{@code ModDamageSpec: 10}）同刻度 ——
     * 削韧与韧性条统一走文献口径（方案 §5 的方案 C），盾那边用
     * {@link #POISE_TO_SHIELD} 折算，所以这个数字看起来变大了，盾的手感没变。
     */
    public static final float PLAIN_ATTACK_POISE = 10f;

    /**
     * <b>削韧刻度 → 盾量刻度</b>的显式折算：扣盾量 = 削韧值 × 这个系数。
     *
     * <h2>为什么需要它</h2>
     * 削韧字段现在同时服务两个系统，而两者的刻度差约 100 倍：
     * <ul>
     *   <li><b>韧性条</b>走文献口径（普攻 10、小型敌人条长 30）；</li>
     *   <li><b>盾量</b>是本项目自己既有的设计（冰史莱姆盾 8.0），一个数量级上的小刻度。</li>
     * </ul>
     * 直接把文献口径的削韧拿去扣盾，一下就能把冰史莱姆的盾打穿。
     * 所以在这里把两者解耦：盾的既有数值一个都不用动，改刻度只影响这一个系数。
     *
     * <p>{@code 0.01} 是这么定的：文献口径的一次普攻（10）× 0.01 = 0.1 盾量，
     * 与改造前 {@link #PLAIN_ATTACK_POISE} 的旧值（0.1）一致 —— 盾的手感不变。
     */
    public static final float POISE_TO_SHIELD = 0.01f;

    private ShieldService() {
    }

    /**
     * 把改动推给客户端。
     *
     * <p>⚠️ NeoForge 的附件是「<b>原地改对象不算改</b>」—— 不调 {@code setData} 的话
     * 服务端自己看得到新数值，客户端永远拿的是旧值（血条下面的盾条就是这么消失的）。
     * 项目里 {@code StatusTickHandler} 每 tick 手动 setData 也是同一个原因。
     */
    private static void push(LivingEntity entity, ShieldState state) {
        entity.setData(AttachmentRegistration.SHIELD.get(), state);
    }

    // ==================== 存取 ====================

    public static ShieldState get(LivingEntity entity) {
        return entity.getData(AttachmentRegistration.SHIELD.get());
    }

    public static boolean has(LivingEntity entity) {
        return get(entity).isActive();
    }

    /**
     * 是不是「罩型」护盾 —— 只有这种才把盾量画在血条下面。
     *
     * <p>盾牌型（{@link ShieldShape#HELD}）的盾量画在手持物品模型上，
     * 而且不随动画位移（那一块目前是占位）。
     */
    public static boolean hasAuraShield(LivingEntity entity) {
        ShieldState state = get(entity);
        if (!state.isActive()) {
            return false;
        }
        ShieldProfile profile = state.profile();
        return profile != null && profile.shape() == ShieldShape.AURA;
    }

    /**
     * 有盾期间，攻击<b>进不进韧性条</b> —— 不进。
     *
     * <p>用户口径（R9）：<b>护盾 = 霸体</b>，对玩家对敌人都一样。
     * 所以盾在的时候这一步直接挡掉：削韧不会累积到目标的韧性条上，
     * 也就谈不上「有盾还能被打到破韧」。盾自己照旧被磨 ——
     * 那是 {@link #absorbDamage} 里的既有行为（「护盾也受破韧影响」）。
     *
     * <p>唯一的事实来源放在这里（而不是让每个调用点自己判断），
     * 免得以后出现「某个入口忘了问盾」这种漏网。
     */
    public static boolean blocksPoise(LivingEntity entity) {
        return isSuperArmorShield(get(entity));
    }

    /**
     * 有盾期间，控制效果（含聚怪牵引）<b>生不生效</b> —— 不生效。
     *
     * <p>与 {@link #blocksPoise} 同源：护盾 = 霸体，所以「有盾时聚怪也无效」。
     * 拆成两个方法是因为调用方不同（一个在伤害收口、一个在控制系统），
     * 但判断依据必须是同一份，不许各写一份。
     */
    public static boolean blocksControl(LivingEntity entity) {
        return blocksPoise(entity);
    }

    private static boolean isSuperArmorShield(ShieldState state) {
        if (!state.isActive()) {
            return false;
        }
        ShieldProfile profile = state.profile();
        // 模板丢了但盾还在（理论上不该发生）：按霸体处理，宁可挡住也不要漏防
        return profile == null || profile.grantsSuperArmor();
    }

    /**
     * 套盾（换盾）。
     *
     * @param durationTicks 持续刻数；{@link ShieldState#FOREVER} = 永续
     */
    public static void grant(LivingEntity entity, ShieldProfile profile, float shieldValue, int durationTicks) {
        ShieldState state = get(entity);
        state.apply(profile, shieldValue, durationTicks,
                entity.level().getGameTime(), CombatAim.yaw(entity));
        push(entity, state);

        // 免疫类盾：把身上已经挂着的敌对元素直接清掉（冰盾要顺带解除寒元素减速与冻结）
        if (profile.immuneToChill()) {
            purgeAttachment(entity, ModElements.CYRO.get());
        }
        if (profile.immuneToFreeze()) {
            purgeAttachment(entity, ModElements.FROZEN.get());
        }
    }

    public static void clear(LivingEntity entity) {
        ShieldState state = get(entity);
        state.clear();
        push(entity, state);
    }

    /** 服务端每 tick 调用：处理到期。 */
    public static void tick(LivingEntity entity) {
        ShieldState state = get(entity);
        if (state.isActive()) {
            state.tickDuration(entity.level().getGameTime());
            push(entity, state);
        }
    }

    /** 把某个元素从身上彻底移除（会触发 {@code onDetach}，例如解除冻结/减速）。 */
    private static void purgeAttachment(LivingEntity entity, GenshinElement element) {
        StatusContainer container = entity.getData(AttachmentRegistration.CONTAINER);
        if (container == null) {
            return;
        }
        ElementalAttachmentHelper.consume(container, element, Float.MAX_VALUE);
        container.removeFirst(inst -> inst instanceof ElementalAttachmentInstance ea
                && ea.getElement() == element);
    }

    // ==================== 伤害入口 ====================

    /**
     * 伤害进来时先过盾。
     *
     * @param incoming     原始伤害（已经过伤害管线计算）
     * @param poiseDamage  本次攻击的削韧量（0 表示不削韧）
     * @return 还能落到<b>本体</b>身上的伤害；盾全吃下时为 0
     */
    public static float absorbDamage(LivingEntity target, DamageSource source, float incoming, float poiseDamage) {
        if (incoming <= 0f && poiseDamage <= 0f) {
            return incoming;
        }
        ShieldState state = get(target);
        if (!state.isActive()) {
            return incoming;
        }
        ShieldProfile profile = state.profile();
        if (profile == null) {
            return incoming;
        }
        // 盾牌型：只挡正面
        if (profile.shape() == ShieldShape.HELD && !isFrontal(target, state, source)) {
            return incoming;
        }

        long gameTime = target.level().getGameTime();
        state.markHit(gameTime);
        ShieldElement element = elementOf(source);
        float damageThrough = incoming;

        if (profile.effect() == ShieldEffect.FULL) {
            // 全抵挡：伤害全部记在盾上，再按「盾吃不吃伤害」决定扣不扣盾量
            damageThrough = absorbByShield(target, state, profile, element, incoming, incoming);
        } else {
            float toShield = incoming * state.partialRatio();
            damageThrough = absorbByShield(target, state, profile, element, incoming, toShield);
        }

        // 削韧：除了纯伤害盾之外都会磨盾（用户口径：正常攻击 = 0.15 削韧 + 元素消耗）。
        // 例外：被盾整个吞掉的元素（冰盾遇水、遇冰）连削韧也不吃 —— 只受击，盾一点都不掉。
        boolean swallowed = profile.blocksAttachment(element);
        if (!swallowed && profile.breakType() != ShieldBreakType.DAMAGE && poiseDamage > 0f) {
            state.consume(poiseDamage * POISE_TO_SHIELD);
            onShieldChanged(target, state, profile);
        }

        push(target, state);
        return Math.max(0f, damageThrough);
    }

    /**
     * 把伤害记到盾上。
     *
     * @param incoming 完整伤害
     * @param toShield 这一次按效用划分给盾的那一份伤害
     * @return 穿透到本体的伤害
     */
    private static float absorbByShield(LivingEntity target, ShieldState state, ShieldProfile profile,
                                        ShieldElement element, float incoming, float toShield) {
        boolean shieldEatsDamage = profile.breakType() == ShieldBreakType.DAMAGE
                || profile.breakType() == ShieldBreakType.MIXED;
        if (!shieldEatsDamage) {
            // 元素盾/白盾：伤害打不进本体，也不扣盾量
            return 0f;
        }

        float absorbMultiplier = Math.max(0f, profile.absorbMultiplier(element));
        if (absorbMultiplier <= 0f) {
            return incoming;
        }

        // 盾量能吸收的伤害 = 剩余盾量 / 吸收倍率
        float absorbableDamage = state.value() / absorbMultiplier;
        float absorbed = Math.min(toShield, absorbableDamage);
        state.consume(absorbed * absorbMultiplier);
        onShieldChanged(target, state, profile);
        return incoming - absorbed;
    }

    /** 破盾时清一下状态（表现交给调用方/实体自己）。 */
    private static void onShieldChanged(LivingEntity target, ShieldState state, ShieldProfile profile) {
        if (!state.isActive()) {
            state.clear();
        }
    }

    /** 盾牌型：判断攻击是不是从正面来的。 */
    private static boolean isFrontal(LivingEntity target, ShieldState state, DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker == null) {
            // 没有来源方向（环境伤害等）：盾牌型不挡
            return false;
        }
        Vec3 toAttacker = attacker.position().subtract(target.position());
        if (toAttacker.x * toAttacker.x + toAttacker.z * toAttacker.z < 1.0E-4) {
            return true;
        }
        float attackYaw = (float) (Mth.atan2(-toAttacker.x, toAttacker.z) * 180.0 / Math.PI);
        float diff = Math.abs(Mth.wrapDegrees(attackYaw - state.heldYaw()));
        return diff <= HELD_FRONT_HALF_ANGLE;
    }

    private static ShieldElement elementOf(@Nullable DamageSource source) {
        if (source instanceof ModDamageSource modSource && modSource.getSpec() != null) {
            return ShieldElement.of(modSource.getSpec().getElement());
        }
        // 其他 MOD 打进来、被换算成角色口径的伤害：元素类型挂在换算伤害源上
        GenshinElement convertedElement = com.linweiyun.genshin.core.system.compat.CompatConvertedDamageSource
                .elementOf(source);
        if (convertedElement != null) {
            return ShieldElement.of(convertedElement);
        }
        return ShieldElement.PHYSICAL;
    }

    /**
     * 非原神模式下，这次「不走本 MOD 伤害管线」的普通攻击该给盾多少削韧。
     *
     * <p>为什么需要这条：原版 / 其他 MOD 的攻击在接收端只有
     * {@code LivingEntity#hurtServer} 那个入口，既没有 {@code ModDamageSpec} 可读削韧，
     * 也不一定带元素附着。纯元素盾只吃元素与削韧，于是「剑砍上去一点反应都没有、盾永远打不破」。
     * 这里把它按<b>物理非钝击</b>处理，每次给 {@link #PLAIN_ATTACK_POISE} 点削韧，
     * 让非原神模式下的普通攻击也能磨盾。
     *
     * <p>三种情况不给：
     * <ul>
     *   <li>没有发起者的伤害（摔落 / 火焰 / 仙人掌 / 虚空…）—— 那不是「普通攻击」；</li>
     *   <li>原神模式玩家的攻击 —— 那类攻击走 {@code ModDamageSpec}，自带削韧与元素口径；</li>
     *   <li>盾本来就不吃这一下（伤害盾不吃削韧、被盾整个吞掉的元素连削韧也不吃）——
     *       这些判断统一在 {@link #absorbDamage} 里做，这里只负责给值。</li>
     * </ul>
     *
     * @return 本次要给盾的削韧量；0 表示不给
     */
    public static float plainAttackPoise(@Nullable DamageSource source) {
        if (source == null) {
            return 0f;
        }
        if (source.getEntity() == null && source.getDirectEntity() == null) {
            return 0f;
        }
        Player attacker = attackerPlayer(source);
        if (attacker != null && PlayerStatBridge.isGenshinMode(attacker)) {
            return 0f;
        }
        return PLAIN_ATTACK_POISE;
    }

    /** 找出这次伤害的发起玩家（和 {@code CompatEventHandler} 同一口径：造成实体优先，其次抛射物主人）。 */
    @Nullable
    private static Player attackerPlayer(@Nullable DamageSource source) {
        if (source == null) {
            return null;
        }
        if (source.getEntity() instanceof Player player) {
            return player;
        }
        if (source.getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() instanceof Player owner) {
            return owner;
        }
        return null;
    }

    // ==================== 元素附着入口 ====================

    /**
     * 元素攻击打上来时先过盾，返回「这次附着/反应怎么处理」。
     */
    public static AttachDecision onElementalAttack(LivingEntity target, @Nullable GenshinElement element,
                                                   float unit, boolean canAttach) {
        if (!canAttach || element == null) {
            return canAttach ? AttachDecision.ALLOW : AttachDecision.BLOCK;
        }
        ShieldState state = get(target);
        if (!state.isActive()) {
            return AttachDecision.ALLOW;
        }
        ShieldProfile profile = state.profile();
        if (profile == null) {
            return AttachDecision.ALLOW;
        }

        ShieldElement slot = ShieldElement.of(element);

        // ① 被吞掉的附着：不附着、不反应、不消耗元素量。
        //    但这次攻击<b>仍然算「受击」</b> —— 盾会闪、会记一次挨打，只是伤害和元素消耗都是 0。
        //    （冰盾遇水/遇冰就是这条路：水直接消失，盾一点都不掉。）
        if (profile.blocksAttachment(slot)) {
            state.markHit(target.level().getGameTime());
            push(target, state);
            return AttachDecision.BLOCK;
        }

        // ② 元素盾 / 混合盾按「每单位附着消耗多少盾量」扣。
        //    关键：那个元素<b>照常附着</b>，然后和盾自带的元素（冰盾就是冰附着）反应 ——
        //    融化把双方都吃掉，所以身上不会留下火；同时盾按元素表掉量。
        //    也就是「火挂上去 → 和盾的冰反应 → 触发融化 → 扣盾」，而不是「火直接消失」。
        if (profile.breakType() == ShieldBreakType.ELEMENT || profile.breakType() == ShieldBreakType.MIXED) {
            float cost = profile.consumePerUnit(slot) * unit;
            if (cost > 0f) {
                state.consume(cost);
                onShieldChanged(target, state, profile);
            }
        }
        state.markHit(target.level().getGameTime());
        push(target, state);
        return AttachDecision.ALLOW;
    }

    /** 这次元素附着怎么处理。 */
    public enum AttachDecision {
        /** 正常：附着 + 反应。 */
        ALLOW,
        /** 只反应不附着：元素被盾吃掉了（火破冰盾），按「盾自挂元素反应」处理。 */
        REACT_ONLY,
        /** 什么都不做：元素被盾整个吞掉（冰盾遇水）。 */
        BLOCK
    }

    /** 读一下当前盾量（调试/表现用）。 */
    public static float shieldValue(LivingEntity entity) {
        return get(entity).value();
    }

    /** 盾的元素（盾条颜色用）；没有元素的白盾返回 null。 */
    @Nullable
    public static GenshinElement shieldElement(LivingEntity entity) {
        ShieldProfile profile = get(entity).profile();
        return profile == null ? null : profile.element();
    }

    /** 身上有没有能反应的非瞬发元素（给「风/岩要不要参与」这类判断复用）。 */
    public static boolean hasReactiveAura(LivingEntity entity) {
        StatusContainer container = entity.getData(AttachmentRegistration.CONTAINER);
        if (container == null) {
            return false;
        }
        for (StatusInstance inst : container.getAll()) {
            if (inst.isFinished() || !(inst instanceof ElementalAttachmentInstance ea)) {
                continue;
            }
            GenshinElement element = ea.getElement();
            if (element == null || element.isInstant() || element == ModElements.FYSIKOS.get()) {
                continue;
            }
            return true;
        }
        return false;
    }
}
