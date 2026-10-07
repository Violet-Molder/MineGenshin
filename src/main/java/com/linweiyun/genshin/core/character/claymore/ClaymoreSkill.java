package com.linweiyun.genshin.core.character.claymore;

import com.linweiyun.genshin.content.skill_node.AreaEntityCollector;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.talent.SkillBase;
import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.elementlib.core.system.about.AttachmentType;
import com.linweiyun.genshin.core.system.combat.action.ActionDefinition;
import com.linweiyun.genshin.core.system.combat.action.ActionKind;
import com.linweiyun.genshin.core.system.combat.action.ActionSet;
import com.linweiyun.genshin.core.system.combat.action.data.ActionStep;
import com.linweiyun.genshin.core.system.combat.action.data.Engagement;
import com.linweiyun.genshin.core.system.combat.action.data.Hit;
import com.linweiyun.genshin.core.system.combat.action.data.SoundCue;
import com.linweiyun.genshin.core.system.combat.action.data.SoundRef;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 大剑的<b>技能基类</b> —— 与别的武器类唯一的硬差别就是重击这一招。
 *
 * <h2>重击：不是「触发一次」，而是「进入一个状态」</h2>
 * 单手剑 / 长枪 / 法器 / 弓的重击是<b>触发型</b>：蓄力到阈值 → 放一招 → 打完为止
 * （{@code ResourceDrivenActionHandler.tickCharge} 那一套，触发一次就
 * {@code chargedAttackTriggered = true} 收工）。
 *
 * <p>大剑不是。按住左键蓄力到阈值之后，角色<b>进入持续重击状态</b>：一边转一边持续结算，
 * 直到<b>松手</b>或<b>到达最高持续时间</b>才结束。所以这一招在动作表里是一段
 * <b>长时长 + 循环动画 + 等间隔伤害点</b>的 ActionStep，而不是一脚踢出去就结束。
 *
 * <pre>
 * 按住左键                                     松手
 * ├──────── 蓄力（getChargeTicks）────────┼──── 持续重击（最长 MaxTicks）────┤
 *                                          ↑ 进状态、开视角跟随
 *                                          伤害点每 interval 刻一下
 * </pre>
 *
 * <h2>两端各自负责什么</h2>
 * <ul>
 *   <li><b>客户端</b>（{@code ResourceDrivenActionHandler} → {@code ActionStateMachine}）：
 *       过阈值就 {@code changeState(循环动画, 总时长 = MaxTicks, 硬直 = MaxTicks, 定身 = 0)}，
 *       松手时回常态。定身 0 = 人走得动；硬直铺满整段 = 这一招整段都是执行期（绝对霸体）。</li>
 *   <li><b>服务端</b>（{@code ActionManager} → {@link com.linweiyun.genshin.core.system.combat.action.ActionState}）：
 *       跑同一段 ActionStep，但玩家松手时客户端会发一条
 *       {@code InterruptReason.CHARGE_RELEASE} 收招 —— 否则松手之后服务端还会把
 *       剩下几十刻的伤害点打完。</li>
 * </ul>
 *
 * <h2>位移是「兼容」的，不是「打断」</h2>
 * 持续重击期间按 WASD（以及跳跃、潜行）<b>不会取消这一招</b>：人带着重击动画走位，
 * 伤害照原节奏结算，动画也一直是这一招的循环动画 —— 状态还在时
 * {@code PlayerAnimationController} 挑的永远是动作动画，不会被换成走 / 跑。
 *
 * <pre>
 * 按住左键                       进状态                        松手（或到时）
 * ├───── 蓄力 ─────┼══════════ 持续重击 ══════════┤
 *                              ↑ WASD / 跳 / 蹲 都在这一段里共处：
 *                                人走位，转圈的动画照播、结算照走
 * </pre>
 *
 * <p><b>为什么要专门写这一条</b>：一般大剑角色的重击是「抡起大剑转圈圈」，
 * 移动就该是「带着这圈一起走」；木偶那种「坐到载具上指挥人偶攻击」的重击同理 ——
 * 移动时保持这个状态（位移交给载具去消费），动画与结算一个都不变。
 * 一句话：<b>动画基本上还是重击的动画，只是跟着位移</b>。
 *
 * <p>出口只有三个：<b>松手</b>（客户端发 {@code CHARGE_RELEASE}）、
 * <b>到时</b>（{@link #getChargedAttackMaxTicks()}）、<b>强制打断</b>
 * （换人 / 死亡 —— 见 {@code ActionManager.interrupt} 的 forced 分支）。
 *
 * <p>实现落在两处：定身 0（人走得动）+ {@code ActionStateMachine.interruptOnNormalInput}
 * 对持续型状态（{@code currentStateLoops}）的<b>整段豁免</b>（走位顶不掉状态）。
 * 后者是显式的，不依赖「执行期恰好铺满整段」那个巧合 ——
 * 那个巧合在动作系统开关关闭时不成立（{@code changeState} 会把硬直清零）。
 *
 * <h2>角色要覆盖什么</h2>
 * 默认实现给了一套「转一圈、打周围一圈」的物理重击，够跑通流程；
 * 数值与表现都留了钩子：
 * {@link #getChargedAttackMaxTicks()} / {@link #getChargedAttackHitInterval()} /
 * {@link #getChargedAttackMultiplier(PGCharacter)} / {@link #getChargedAttackElement(PGCharacter)} /
 * {@link #getChargedAttackRadius()} / {@link #getChargedAttackAnimation(PGCharacter, ActionStep)}，
 * 以及每一下的伤害入口 {@link #chargeAttack(Player, PGCharacter)}（每次伤害点调一次）
 * 与首尾钩子 {@link #onChargedAttackStart(Player, PGCharacter)} /
 * {@link #onChargedAttackEnd(Player, PGCharacter)}。
 */
public class ClaymoreSkill extends SkillBase {

    /** 持续重击的最高持续时间（刻）—— 5 秒。 */
    public static final int DEFAULT_CHARGED_MAX_TICKS = 100;

    /**
     * 进持续重击状态所需的按住刻数 —— 6 刻（0.3 秒）。
     *
     * <p>{@link com.linweiyun.genshin.core.character.talent.SkillBase#getChargeTicks()}
     * 给所有武器用的是 20 刻（1 秒），那是给「蓄力之后放一招」的触发型重击留的读条；
     * 大剑的重击不是放一招，而是按住就进状态，1 秒前摇只会让人觉得没反应。
     * 6 刻仍然远大于一次点击（1～3 刻），所以单击 / 按住照样分得开。
     */
    public static final int DEFAULT_CHARGED_TRIGGER_TICKS = 6;

    /** 持续重击的伤害间隔（刻）—— 每 0.5 秒一下。 */
    public static final int DEFAULT_CHARGED_HIT_INTERVAL = 10;

    /** 持续重击每一段的削韧系数（重击比普攻重，1.0 = 攻击类型基准）。 */
    public static final double DEFAULT_CHARGED_POISE = 1.2;

    /** 持续重击的判定半径（格）—— 以自己为中心的一圈。 */
    public static final float DEFAULT_CHARGED_RADIUS = 2.5f;

    /** 动作数据里没有战技段、因而借不到动画名时的兜底（缺席会被动画保护挡住，只结算伤害）。 */
    public static final String DEFAULT_CHARGED_ANIMATION = "heavy_attack";

    // ==================== 大剑重击 = 持续型 ====================

    /** 大剑的重击是持续型：客户端据此走「进状态 / 松手结束」那条路。 */
    @Override
    public boolean isSustainedChargedAttack() {
        return true;
    }

    /** 按住多久进持续重击状态。见 {@link #DEFAULT_CHARGED_TRIGGER_TICKS}。 */
    @Override
    public int getChargeTicks() {
        return DEFAULT_CHARGED_TRIGGER_TICKS;
    }

    /**
     * 持续重击最长维持多少刻（到点自动停）。
     *
     * <p>原神的持续重击其实是<b>体力</b>在限制长度；本 MOD 还没有体力系统，
     * 所以先用时间上限兜底，想要更长/更短改这里。
     */
    @Override
    public int getChargedAttackMaxTicks() {
        return DEFAULT_CHARGED_MAX_TICKS;
    }

    /** 持续重击每多少刻结算一次伤害。 */
    public int getChargedAttackHitInterval() {
        return DEFAULT_CHARGED_HIT_INTERVAL;
    }

    /** 每一次伤害点的削韧系数。 */
    public double getChargedAttackPoise() {
        return DEFAULT_CHARGED_POISE;
    }

    /** 持续重击的判定半径（格）。 */
    public float getChargedAttackRadius() {
        return DEFAULT_CHARGED_RADIUS;
    }

    /**
     * 持续重击<b>每一下</b>的倍率（攻击力百分比，1.0 = 100%）。
     *
     * <p>默认是占位值 —— 真正的数值应该在角色自己的配置里（照着
     * {@code ShenheTalentConfig} 那种「基础 + 每级成长」写），这里覆盖取用。
     */
    public float getChargedAttackMultiplier(PGCharacter character) {
        return 1.0f;
    }

    /**
     * 持续重击每一下的元素。
     *
     * <p>默认<b>物理</b>（{@link ModElements#FYSIKOS}）—— 原神的大剑重击本来就是物理伤害
     * （除非被附魔/元素转化）。要改成元素伤害就在这里覆盖，附着会自动按
     * {@link AttachmentType#WEAK} 走（物理不附着）。
     */
    public GenshinElement getChargedAttackElement(PGCharacter character) {
        return ModElements.FYSIKOS.get();
    }

    /**
     * 持续重击播哪个动画。
     *
     * <p>默认沿用角色动作表里那段重击步的动画名（没有就是
     * {@link #DEFAULT_CHARGED_ANIMATION}）。写 {@code heavy_loop} 这类循环动画时，
     * {@link ActionStep#loopAnimation} 会把它循环播，而不是播完停在最后一帧。
     */
    protected String getChargedAttackAnimation(PGCharacter character, @Nullable ActionStep source) {
        return source == null || source.animation == null || source.animation.isEmpty()
                ? DEFAULT_CHARGED_ANIMATION
                : source.animation;
    }

    /**
     * 持续重击<b>进入状态那一刻</b>（服务端）。
     *
     * <p>和 {@link #chargeAttack} 的区别：那个是「第几刻打出这一下」，这个是
     * 「重击开始了」——触发即生效的东西（姿态、消耗、状态标记）放这里。
     */
    public void onChargedAttackStart(Player player, PGCharacter character) {
    }

    /**
     * 持续重击<b>结束那一刻</b>（服务端）—— 松手、到时、被打断都会走到这里。
     *
     * <p>收招那一下（原神的「停止挥砍时的收尾斩」）就放这里。
     */
    public void onChargedAttackEnd(Player player, PGCharacter character) {
    }

    /**
     * 持续重击<b>每一个伤害点</b>的伤害结算（服务端，每次伤害点调一次）。
     *
     * <p>默认实现：以自己为中心扫一圈、按 {@link #getChargedAttackMultiplier} 结算一次
     * {@link AttackType#CHARGED_ATTACK}。角色要更精细的表现（改成打前方、加特效、
     * 改成打锁定的目标）就覆盖它 —— 它会被调用很多次，别在这里做一次性的事
     * （那些属于 {@link #onChargedAttackStart}）。
     */
    @Override
    public void chargeAttack(Player player, PGCharacter character) {
        Level level = player.level();
        if (level.isClientSide()) return;

        float radius = getChargedAttackRadius();
        float multiplier = getChargedAttackMultiplier(character);
        GenshinElement element = getChargedAttackElement(character);
        boolean elemental = element != null && element != ModElements.FYSIKOS.get();

        Vec3 center = player.position();
        List<LivingEntity> targets = new AreaEntityCollector(level,
                center.add(-radius, -1.0, -radius),
                center.add(radius, 2.0, radius),
                radius).execute();

        for (LivingEntity target : targets) {
            if (target == player || !target.isAlive()) continue;

            ModDamageSpec.Builder spec = ModDamageSpec.builder(AttackType.CHARGED_ATTACK, element)
                    .multiplier(multiplier)
                    .attackerCharacter(character);
            if (elemental) {
                // 物理不附着；元素化的重击按弱附着走
                spec.elementAmount(AttachmentType.WEAK.getInitialAmount());
            }

            ModDamageSource source = ModDamageSource.from(spec.build(), player);
            if (target.level() instanceof ServerLevel serverLevel) {
                target.hurt(source, 0f);
            }
        }
    }

    // ==================== 动作表 ====================

    /**
     * 把重击那一格换成<b>持续型</b>的一段：
     * 总时长 = 最高持续时间、执行期铺满整段（走位打不断）、动画循环、伤害点等间隔。
     *
     * <p>其余招式（普攻 / 战技 / 大招 / 闪避）原样来自
     * {@link SkillBase#buildDefaultActionSet(PGCharacter)}。
     */
    @Override
    protected ActionSet buildDefaultActionSet(PGCharacter character) {
        ActionSet base = super.buildDefaultActionSet(character);
        return setBuilder(base)
                .addCharged(buildSustainedChargedAttack(character, base.getChargedAttack()))
                .build();
    }

    /**
     * 造出持续重击那一段。
     *
     * @param fallback 动作表里原本那一段重击（{@code SkillBase} 的兜底：战技点按步）；
     *                 只借它的动画名、音效与优先级，时序全部重写。可以为 {@code null}。
     */
    private ActionDefinition buildSustainedChargedAttack(PGCharacter character,
                                                        @Nullable ActionDefinition fallback) {
        ActionStep source = fallback == null ? null : fallback.step;

        int maxTicks = Math.max(2, getChargedAttackMaxTicks());
        int interval = Math.max(1, getChargedAttackHitInterval());
        String animation = getChargedAttackAnimation(character, source);
        int priority = source == null ? 2 : source.priority;
        List<SoundRef> sounds = source == null ? List.of() : source.sounds;

        ActionStep hold = new ActionStep(
                animation,
                maxTicks,
                // 执行期铺满整段：这段时间里挨打也不会把重击取消掉。
                // 「走位也不取消」真正的把关不在这里，而在客户端那条显式豁免
                // （ActionStateMachine.interruptOnNormalInput 对持续型状态整段放行）——
                // 这里只是顺带，别把它当成唯一的保障，见类注释的「位移是兼容的」。
                // 结束只有两个正常出口 —— 松手（客户端发 CHARGE_RELEASE）、到时（两边各自到点）。
                maxTicks,
                priority,
                // 位移留空：持续重击没有前冲，走位交给玩家自己（大剑重击可以边走边转）
                List.of(),
                buildChargedHits(maxTicks, interval),
                sounds,
                0f, 0f, 0, 0
        ).withEngagement(Engagement.melee().withDash(false).withAdhesion(0, 0))
         .withLoopAnimation(true)
         // 身体朝向的**例外**：持续重击期间不按「索敌到才转向」来，而是让身体跟着镜头
         // （玩家靠转鼠标决定这一圈往哪抡；镜头紧跟着身体背后）。
         // 其余出招一律是默认的 TARGET：索敌到转向目标、没索敌保持原朝向 —— 见 ActionBodyFacing。
         .withCameraFacing();

        // 音效编排照搬（旧的 sounds 列表已经带过来了）
        if (source != null) {
            for (SoundCue cue : source.soundCues) {
                hold.withSoundCue(cue);
            }
        }

        return ActionDefinition.builder(ActionKind.CHARGED_ATTACK)
                .step(hold)
                .onCastStart(ctx -> {
                    onCastStart(ctx.player, ctx.character, ActionKind.CHARGED_ATTACK);
                    onChargedAttackStart(ctx.player, ctx.character);
                })
                .onActiveStart(ctx -> chargeAttack(ctx.player, ctx.character))
                .onComplete(ctx -> onChargedAttackEnd(ctx.player, ctx.character))
                .onInterrupt(ctx -> onChargedAttackEnd(ctx.player, ctx.character))
                .build();
    }

    /**
     * 持续重击的伤害点：第 0 刻先来一下，之后每 {@code interval} 刻一下。
     *
     * <p>{@code Hit} 在这里只当<b>时间轴</b>用（delay 决定第几刻结算、poise 决定削多少韧），
     * 位置与范围不参与结算 —— 伤害范围是 {@link #getChargedAttackRadius()} 那一圈，
     * 见 {@link #chargeAttack}。
     *
     * <p>「松手提前结束」不需要在这里预留什么：服务端到点之前就被收招了，
     * 剩下的伤害点根本不会跑到。
     */
    private List<Hit> buildChargedHits(int maxTicks, int interval) {
        List<Hit> hits = new ArrayList<>();
        for (int tick = 0; tick < maxTicks; tick += interval) {
            hits.add(new Hit(tick, 0.0, 1.0, getChargedAttackRadius(), getChargedAttackPoise()));
        }
        return hits;
    }
}
