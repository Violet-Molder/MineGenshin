package com.linweiyun.genshin.core.system.poise;

import com.linweiyun.genshin.content.attribute.AttributeContainer;
import com.linweiyun.genshin.content.attribute.AttributeInstance;
import com.linweiyun.genshin.content.attribute.AttributeType;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.system.control.ControlService;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import com.linweiyun.genshin.core.system.poise.impact.ImpactSolver;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.core.system.shield.ShieldService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * 韧性结算 —— 「没有破韧的时候，敌人不吃控制」这条规则的唯一收口。
 *
 * <h2>它负责什么</h2>
 * <ul>
 *   <li><b>累积</b>：{@link #accumulate} 把一次攻击的削韧值记到目标身上，攒满就 {@link #markBroken 破韧}；</li>
 *   <li><b>自恢复</b>：{@link #tick} 每刻按「每秒衰减 ÷ 20」往回掉 ——
 *       所以哪怕一直被攻击（每秒削 < 每秒衰减）也恢复得回来；</li>
 *   <li><b>驻留</b>：破韧后有「重置时间」一段窗口（破绽期），走完立刻恢复；</li>
 *   <li><b>联机系数</b>：上限 = 档位长度 × 在线人数（单机恒为 1）。</li>
 * </ul>
 *
 * <h2>它不负责什么</h2>
 * <ul>
 *   <li>不问护盾 —— 「有盾不进韧性条」那条门在 {@link ShieldService#blocksPoise}，
 *       由调用方（{@code LivingEntityHurtMixin}）在调 {@link #accumulate} 之前先问；</li>
 *   <li>不施加控制 —— 控制只有一个入口：每次命中由伤害管线调一次
 *       {@link ControlService#onHit}，剩下的判定与效果都在
 *       {@code core.system.control.Controllable} 上（实体内置方法）。<b>本类只回答「破没破」</b>，
 *       不再自己动手推（推多远的重量门槛在 {@link ImpactSolver}）、也不自己动手打断；</li>
 *   <li>不判护盾以外的东西 —— 有盾时这一笔整个不进韧性条（{@link ShieldService#blocksPoise}），
 *       破韧后的控制则连盾那道门一起由 {@code Controllable} 判。</li>
 * </ul>
 *
 * <h2>破韧期间：每一次命中都算数</h2>
 * ⚠️ 口径更正（2026-09-25）：早先的实现是「冲量只在破韧那一瞬间施加一次」。
 * 用户更正为<b>破韧期间敌人处于几乎无控制抗性，随便攻击都能打断其目前的动作</b>——
 * 所以「破韧期间每一次命中都有一次小控制」这件事落在<b>命中入口</b>上：
 * 伤害管线每挨一下调一次 {@link ControlService#onHit}，判据问目标自己的
 * {@code Controllable}（破韧 / 抗打断系数 / 首领例外）。破韧那一下只是这些命中里的第一次
 * （{@link #onBreak} 因此只剩日志）。
 *
 * <h2>档位从哪来</h2>
 * 优先读目标显式配过的 {@code POISE} 属性（怪物配在它的属性表里，玩家配在当前角色上），
 * 没配过就退回 {@link PoiseTiers#poiseTier}（由抗牵引等级派生）——
 * 「按牵引等级赋予对应等级的韧性」就是这条兜底。
 *
 * <h2>条长从哪来（上限是可变属性）</h2>
 * ⚠️ 口径更正（2026-09-25）：上限<b>不再只由档位推</b>。用户口径：「韧性上限应该是一个可变属性。
 * 对于已经出现的实体基本上是固定的，但是对一个注册表单例比如僵尸，不同环境下的韧性可能不一样。」
 * 所以 {@link #maxValue} 先读 {@code POISE_MAX} 属性（逐实例给，僵尸在普通地区与副本里可以不同），
 * 没配才退回档位长度。档位仍然管另一半：<b>每秒衰减与重置时间</b>。
 * 上限缩小会把当前值一起钳下来，所以「中途改上限」也是安全的。
 *
 * <h2>霸体系数（{@code SUPER_ARMOR}）</h2>
 * 属性 {@code SUPER_ARMOR} 是<b>霸体强度</b>，默认 1，<b>越高越耐</b>：
 * 实际削韧 = 攻击削韧 ÷ 霸体强度。文献那套「系数越小越耐、0 = 免疫」是反方向，
 * 两套口径的换算只发生在 {@link #poiseFactorOf} 这一个方法里。
 */
public final class PoiseService {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.COMBAT);

    /**
     * 一秒有多少刻 —— 项目跑 20 tick/s。
     *
     * <p>用它把文献的「每秒衰减 / 重置多少秒」换算成「每刻」。
     * ⚠️ 这里是「每秒衰减 × 一个时间片的长度」，不是一个叫「20」的魔法数：
     * 以后真接了可变 tick 率，改的是这一个常量，不是散落各处的 {@code /20}。
     */
    public static final float TICKS_PER_SECOND = 20f;

    private PoiseService() {
    }

    /**
     * 把状态推给客户端。
     *
     * <p>⚠️ NeoForge 的附件是「原地改对象不算改」—— 不调 {@code setData} 的话，
     * 服务端自己看得到新数值，客户端永远拿的是旧值（血条下面那条削韧条就画不出来）。
     */
    private static void push(LivingEntity entity, PoiseState state) {
        entity.setData(AttachmentRegistration.POISE.get(), state);
    }

    // ==================== 存取 ====================

    public static PoiseState get(LivingEntity entity) {
        return entity.getData(AttachmentRegistration.POISE.get());
    }

    /**
     * 「有状态才读」—— 客户端每帧扫全场实体时用这个，避免给每一只路过的怪
     * 都凭空挂一个韧性附件。
     */
    @Nullable
    public static PoiseState peek(LivingEntity entity) {
        return entity.hasData(AttachmentRegistration.POISE.get()) ? get(entity) : null;
    }

    public static boolean isBroken(LivingEntity entity) {
        PoiseState state = peek(entity);
        return state != null && state.isBroken();
    }

    /** 削韧条的比例（0~1）；没状态或已破韧都是 0。 */
    public static float ratio(LivingEntity entity) {
        PoiseState state = peek(entity);
        return state == null ? 0f : state.ratio();
    }

    /** 这条韧性「有没有内容」—— 攒过削韧或者正破着（表现层决定画不画）。 */
    public static boolean isEngaged(LivingEntity entity) {
        PoiseState state = peek(entity);
        return state != null && state.isEngaged();
    }

    // ==================== 档位与上限 ====================

    /**
     * 目标的韧性档位（1–4）。
     *
     * <p>显式配过 {@code POISE} 属性就以属性为准（用户给某只怪单独调档的出口），
     * 没配过则按 {@link PoiseTiers#poiseTier} 从抗牵引等级派生。
     */
    public static int tierOf(LivingEntity entity) {
        Double explicit = attributeOrNull(entity, ModAttributes.POISE.get());
        if (explicit != null) {
            return Math.clamp((int) Math.round(explicit), 1, PoiseTiers.MAX_TIER);
        }
        return PoiseTiers.poiseTier(entity);
    }

    public static PoiseTiers.TierProfile profileOf(LivingEntity entity) {
        return PoiseTiers.profileOf(tierOf(entity));
    }

    /**
     * 联机增强系数 —— 文献口径「韧性条实际长度 = 标准长度 × 联机系数，一般等于联机玩家数」。
     *
     * <p>单机恒为 1；服务端取在线人数并把最小钳到 1（人数拿不到时按 1 处理，
     * 宁可当单机也不要算出个 0 让所有人都瞬间破韧）。
     */
    public static float multiplayerCoefficient(LivingEntity entity) {
        if (entity.level() instanceof ServerLevel serverLevel && serverLevel.getServer() != null) {
            int players = serverLevel.getServer().getPlayerList().getPlayers().size();
            return Math.max(1, players);
        }
        return 1f;
    }

    /**
     * 这一条韧性条的长度（已乘联机系数）。
     *
     * <p><b>上限是可变属性</b>：先读 {@code POISE_MAX}（配了就用它当基准长度，逐实例可不同），
     * 没配（0 / 负数）才退回档位长度 {@link PoiseTiers.TierProfile#length()}。
     * 两者都要乘联机系数 —— 文献口径「韧性条实际长度 = 标准长度 × 联机玩家数」对两种来源都成立。
     */
    public static float maxValue(LivingEntity entity) {
        Double explicit = attributeOrNull(entity, ModAttributes.POISE_MAX.get());
        if (explicit != null && explicit > 0.0) {
            return (float) (explicit.doubleValue() * multiplayerCoefficient(entity));
        }
        return profileOf(entity).length() * multiplayerCoefficient(entity);
    }

    // ==================== 霸体系数 ====================

    /**
     * 霸体强度（{@code SUPER_ARMOR}）：默认 1，越大越耐；≤0 一律按 1 处理。
     *
     * <p>「0 按 1 处理」是刻意的：文献里 0 表示「完全免疫削韧」，
     * 但那类免疫（护盾、无敌帧、冻结）在本项目走的是显式门控，
     * 不该让一个没配准的 0 变成「全场无敌」。
     *
     * <p>没配过属性时的默认值不是常数 1：首领走
     * {@link PoiseTiers#defaultSuperArmor}（高到普通攻击打不断）。
     * 这一个系数<b>两处都用</b>：除以它算实际削韧，拿它当打断门槛
     * （见 {@code core.system.control.Controllable#runControl}）。
     */
    public static float superArmorOf(LivingEntity entity) {
        Double explicit = attributeOrNull(entity, ModAttributes.SUPER_ARMOR.get());
        if (explicit == null) {
            return PoiseTiers.defaultSuperArmor(entity);
        }
        float value = explicit.floatValue();
        return value > 0f ? value : 1f;
    }

    /** 削韧倍率 = 1 ÷ 霸体强度。乘在攻击的削韧值上。 */
    public static float poiseFactorOf(LivingEntity entity) {
        return 1f / superArmorOf(entity);
    }

    // ==================== 结算 ====================

    /**
     * 攒一笔削韧，攒满即破韧。
     *
     * @param poiseDamage 本次攻击的削韧值（负数 / 0 直接忽略）
     * @param source      伤害来源，仅用于破韧日志与后续的冲击解算；可以为 null
     */
    public static void accumulate(LivingEntity target, float poiseDamage, @Nullable DamageSource source) {
        if (poiseDamage <= 0f || target.level().isClientSide()) {
            return;
        }
        // 护盾 = 霸体：有盾时攻击不进韧性条（盾自己照旧被磨，那一步在 ShieldService 里）
        if (ShieldService.blocksPoise(target)) {
            return;
        }

        float amount = poiseDamage * poiseFactorOf(target);
        if (amount <= 0f) {
            return;
        }

        float max = maxValue(target);
        PoiseState state = get(target);
        state.markHit(target.level().getGameTime());
        state.add(amount, max);
        if (state.isFull() && !state.isBroken()) {
            state.markBroken();
            onBreak(target, source);
        }
        // 控制不在这里做：破韧后的「打断 + 击退」是命中入口的事
        // （伤害管线紧接着调 ControlService.onHit），本类只管这一条韧性条。
        push(target, state);
    }

    /**
     * 每刻结算：没破韧就自然衰减（自恢复），破韧了就走驻留计时。
     *
     * <p>由 {@code PoiseTickHandler} 对「身上已经有韧性状态」的实体调用。
     */
    public static void tick(LivingEntity entity) {
        PoiseState state = get(entity);
        PoiseTiers.TierProfile profile = profileOf(entity);
        float max = maxValue(entity);

        if (state.isBroken()) {
            // 强控（悬浮一类）会把驻留计时按住 —— 按住期间这一条就一直是破绽
            if (!state.consumePause()) {
                state.advanceResidence();
                if (state.residenceTicks() >= Math.round(profile.resetSeconds() * TICKS_PER_SECOND)) {
                    // 驻留走完：削韧值瞬间归零，韧性重新生效
                    state.reset();
                }
            }
            push(entity, state);
            return;
        }

        if (state.value() <= 0f) {
            // 空条：没有可见变化，不推 —— 少一次没必要的同步
            return;
        }
        state.decay(profile.decayPerSecond() / TICKS_PER_SECOND);
        state.refreshMax(max);
        push(entity, state);
    }

    /**
     * 强制破韧 —— 冻结这类「无视韧性直接生效、并且当场破韧」的效果走这里。
     *
     * @param reason 只用于日志（例如 {@code "freeze"}）
     */
    public static void forceBreak(LivingEntity target, String reason) {
        if (target.level().isClientSide()) {
            return;
        }
        PoiseState state = get(target);
        state.refreshMax(maxValue(target));
        state.markHit(target.level().getGameTime());
        if (!state.isBroken()) {
            state.markBroken();
            LOGGER.debug("[Poise] force break {} by {}", target.getName().getString(), reason);
            onBreak(target, null);
        }
        push(target, state);
    }

    /**
     * 把破韧驻留计时暂停若干刻 —— 强控（悬浮一类）用的。
     *
     * <p>语义是「被强控按住的这段时间不算恢复时间」，见方案 §3.2：
     * 只有强控会走这条，软控/击退不暂停驻留。
     */
    public static void pauseReset(LivingEntity target, int ticks) {
        if (ticks <= 0 || target.level().isClientSide()) {
            return;
        }
        PoiseState state = peek(target);
        if (state == null || !state.isBroken()) {
            return;
        }
        state.pauseResidence(ticks);
        push(target, state);
    }

    /** 韧性整个清空（复活、换阶段一类）。 */
    public static void clear(LivingEntity entity) {
        PoiseState state = peek(entity);
        if (state == null) {
            return;
        }
        state.reset();
        push(entity, state);
    }

    /**
     * 破韧的那个瞬间。
     *
     * <p>只记一笔日志 —— 表现不在这里做。破韧与「破韧期间」的表现是<b>同一套</b>：
     * 冲量、打断都跟在 {@link #accumulate} 尾部那一段里，破韧这一下天然也算一次命中。
     * 留这个方法是为了让「破韧这一刻」永远只有一个入口，不要散到调用方。
     */
    private static void onBreak(LivingEntity target, @Nullable DamageSource source) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Poise] {} broke (tier={}, max={}, by={})",
                    target.getName().getString(), tierOf(target), maxValue(target), source);
        }
    }

    // ==================== 属性读取 ====================

    /**
     * 读目标<b>显式配过</b>的属性值；没配过返回 null。
     *
     * <p>判据必须区分「配过 1」和「没配过」，否则 {@code POISE} 的默认 1
     * 会和「未配置、该按抗牵引等级派生」撞在一起。
     * 走 {@link AttributeContainer#has} / {@link com.linweiyun.genshin.core.character.PGCharacterData#getAttribute}
     * 这两个「在不在」的查询，而不是读默认值。
     */
    @Nullable
    public static Double attributeOrNull(LivingEntity entity, AttributeType type) {
        if (entity instanceof Player player) {
            PGCharacter character = currentCharacter(player);
            if (character != null) {
                AttributeInstance instance = character.getData().getAttribute(type);
                if (instance != null) {
                    return instance.getTotalValue();
                }
            }
            return null;
        }
        if (entity.hasData(AttachmentRegistration.ENTITY_STATS.get())) {
            AttributeContainer container =
                    entity.getData(AttachmentRegistration.ENTITY_STATS.get()).attributes();
            if (container != null && container.has(type)) {
                return container.getTotalValue(type);
            }
        }
        return null;
    }

    @Nullable
    private static PGCharacter currentCharacter(Player player) {
        PlayerCharactersAttachment attachment =
                player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        return attachment == null ? null : attachment.getCurrentCharacter();
    }
}
