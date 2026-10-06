package com.linweiyun.genshin.core.system.poise;

import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.genshin.core.system.control.ControlRequest;
import com.linweiyun.genshin.core.system.control.ControlService;
import com.linweiyun.genshin.core.system.poise.impact.ImpactLevel;
import com.linweiyun.elementlib.api.ElementalReactionType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.Map;
import com.linweiyun.genshin.core.system.registry.register.ModReactionTypes;

/**
 * <b>反应自带的削韧与冲击</b> —— 文献「角色数据-反应」那张 23 行表的代码化。
 *
 * <h2>为什么反应要单独一张表</h2>
 * 剧变反应（超载、扩散、感电…）的伤害不吃攻击力，削韧也不吃武器 ——
 * 超载就是 90、扩散就是 130，谁放的都一样。所以它既不能用
 * {@code ModDamageSpec.defaultPoise(AttackType)} 的占位值（那里是「按攻击类型」），
 * 也不能用 {@link WeaponPoiseTable}（那里是「按武器类型」）。
 *
 * <h2>对敌 / 对角色是两行</h2>
 * 文献里绽放系对敌人是 25/50，对角色只有 5/10（而且冲击类型是 0）——
 * 打到玩家身上本来就该轻得多。所以表里存的是 {@link Pair}，
 * 由 {@link #apply} 按 {@code target instanceof Player} 挑一行。
 * 表里没写「对角色」那一行的（超载、扩散…），两行共用同一个值。
 *
 * <h2>怎么落地的</h2>
 * 反应伤害在 {@code HurtEntityHelper} 里结算，那里是唯一同时拿得到
 * 「反应类型 + 目标 + 伤害来源」的地方，所以 {@link #apply} 就挂在那儿，
 * 每次反应伤害结算时调一次。<b>这里的这一笔只负责削韧</b>：
 * 它走控制入口的「只削韧」请求（{@link ControlRequest#poise} →
 * {@link ControlService#apply}），攒满就破韧；<b>冲量与打断不在这里</b> ——
 * 反应那一下伤害本身还会经过命中入口，由目标自己判「这一下该不该被控制」，
 * 所以「超载一炸正好破韧」会把敌人炸飞，「没破韧」就只是掉一条韧性条。
 *
 * <p>反应伤害的 {@code AttackType}（SPECIAL / SWIRL / LUNAR_CHARGED …）在
 * {@code defaultPoise} 里都是 0，所以不会和攻击类型那条路重复扣一次。
 */
public final class ReactionPoiseTable {

    /**
     * 一次反应给目标的削韧与冲击。
     *
     * @param poise  削韧值（同文献刻度）
     * @param impact 破韧那一刻施加的冲击
     */
    public record Entry(float poise, ImpactLevel impact) {
    }

    /** 对敌 / 对角色两行。 */
    public record Pair(Entry enemy, Entry character) {

        /** 按目标是不是玩家挑一行。 */
        public Entry forTarget(LivingEntity target) {
            return target instanceof Player ? character : enemy;
        }
    }

    /** 只有一行（对敌对角色同值）时的简写。 */
    private static Pair both(float poise, ImpactLevel impact) {
        Entry entry = new Entry(poise, impact);
        return new Pair(entry, entry);
    }

    /**
     * 反应表 —— 数值逐行对齐 {@code 知识库/数据/原神-韧性力学.json → 角色数据-反应}。
     *
     * <p>复合冲击（超导「击退，240，300」这类）用 {@link ImpactLevel#custom} 原样表达力值；
     * 表里只有等级数字的就用 {@link ImpactLevel#ofLevel}。
     */
    private static final Map<ElementalReactionType, Pair> TABLE = Map.ofEntries(
            // 超导：敌人受到的范围伤害（5米）30 削韧，冲击「击退，240，300」
            Map.entry(ModReactionTypes.SUPERCONDUCT.get(),
                    both(30f, ImpactLevel.custom(ImpactLevel.Hardiness.KNOCKBACK, 240f, 300f))),
            // 扩散：单体伤害 130 / 冲击 1（另有「范围伤害 30 / 冲击 1」一行，范围那一跳由扩散自己核）
            Map.entry(ModReactionTypes.SWIRL.get(), both(130f, ImpactLevel.ofLevel(1))),
            // 碎冰：30 / 冲击 3
            Map.entry(ModReactionTypes.SHATTERED.get(), both(30f, ImpactLevel.ofLevel(3))),
            // 超载：90 / 冲击 5
            Map.entry(ModReactionTypes.OVERLOAD.get(), both(90f, ImpactLevel.ofLevel(5))),
            // 感电：中心与传导都是 130 / 冲击 2
            Map.entry(ModReactionTypes.ELECTRO_CHARGED.get(), both(130f, ImpactLevel.ofLevel(2))),
            // 燃烧：30 / 冲击 0（对敌对角色同一行）
            Map.entry(ModReactionTypes.BURNING.get(), both(30f, ImpactLevel.ofLevel(0))),
            // 绽放：敌人 25 / 3，角色 5 / 0
            Map.entry(ModReactionTypes.BLOOM.get(), new Pair(
                    new Entry(25f, ImpactLevel.ofLevel(3)),
                    new Entry(5f, ImpactLevel.ofLevel(0)))),
            // 烈绽放：敌人 50 / 2，角色 10 / 0
            Map.entry(ModReactionTypes.BURGEON.get(), new Pair(
                    new Entry(50f, ImpactLevel.ofLevel(2)),
                    new Entry(10f, ImpactLevel.ofLevel(0)))),
            // 超绽放：敌人 50 / 2，角色 10 / 0
            Map.entry(ModReactionTypes.HYPERBLOOM.get(), new Pair(
                    new Entry(50f, ImpactLevel.ofLevel(2)),
                    new Entry(10f, ImpactLevel.ofLevel(0)))),
            // 月感电（雷暴云）：130 / 2
            Map.entry(ModReactionTypes.LUNAR_CHARGED.get(), both(130f, ImpactLevel.ofLevel(2))),
            // 月结晶（月笼协奏）：30 / 2
            Map.entry(ModReactionTypes.LUNAR_CRYSTALLIZE.get(), both(30f, ImpactLevel.ofLevel(2))),
            // 星扩散（风伤）：20 / 2；「星辉风旋小 60 / 击飞 100,600」「大 80 / 击飞 100,600」
            // 是风旋那两个实体各自结算的，不走反应伤害这条线（见 StellarVortexEntity）
            Map.entry(ModReactionTypes.STELLAR_SWIRL_WIND.get(), both(20f, ImpactLevel.ofLevel(2))),
            // 冻结消失：30 / 2。本项目冻结的直接破韧走 PoiseService.forceBreak（见 PoiseFreezeBreak），
            // 这一行留给以后真的做「解冻伤害」时用
            Map.entry(ModReactionTypes.FROZEN.get(), both(30f, ImpactLevel.ofLevel(2)))
    );

    /*
     * 表里没有、也不想含糊塞进来的两行（文献有、本项目还没有对应概念）：
     *   「撞击 30 / 1」  —— 碰撞伤害，本项目没有这条伤害来源；
     *   「丰穰之核绽放 50 / 3（角色 5 / 0）」 —— 丰穰之核还没有实现，
     *     以后做的时候加一个枚举值再补一行即可。
     *
     * 另一处「故意不填」：星扩散的**冰**分支（STELLAR_SWIRL_ICE）。
     * 文献那张反应表只给了星扩散的「风伤 20 / 2」与两个风旋，没有冰分支那一行，
     * 所以宁可留着不猜 —— 要加就先补文献。
     */

    private ReactionPoiseTable() {
    }

    /** 这个反应在表里有没有登记；没有返回 {@code null}。 */
    @Nullable
    public static Pair of(@Nullable ElementalReactionType reactionType) {
        return reactionType == null ? null : TABLE.get(reactionType);
    }

    /**
     * 结算一次反应自带的削韧与破韧冲击。
     *
     * <p>做的两件事：
     * <ol>
     *   <li>如果这一条反应伤害的规格还<b>没写过冲击</b>，把反应自己的冲击写进去 ——
     *       于是「正好被这一下打破韧」时施加的是反应那一档（超载就是击飞，不是普攻的轻击）；</li>
     *   <li>把这笔削韧交给控制入口（{@link ControlRequest#poise}）；</li>
     *   <li>这一笔只削韧、不带控制 —— 冲量与打断由反应伤害那一次的命中入口负责，
     *       {@link Pair#impact()} 只是写进伤害规格，等那一次命中自己去取。</li>
     * </ol>
     *
     * <p>护盾 = 霸体那道门在实体的判定里（{@code Controllable#verdict} +
     * {@code PoiseService} 内部），这里不重复判断。
     *
     * @param target 挨了这一下反应的目标
     * @param reactionType 反应类型；表里没有就什么都不做
     * @param source 这一条反应伤害的来源（可为 null，那就不带破韧冲击）
     */
    public static void apply(LivingEntity target, @Nullable ElementalReactionType reactionType,
                             @Nullable DamageSource source) {
        if (!com.linweiyun.genshin.config.PoiseConfig.isOn(
                com.linweiyun.genshin.config.PoiseConfig.REACTION_POISE)) {
            return;
        }
        Pair pair = of(reactionType);
        if (pair == null || target == null || target.level().isClientSide()) {
            return;
        }
        Entry entry = pair.forTarget(target);
        if (entry == null || entry.poise() <= 0f) {
            return;
        }
        // 反应自己的冲击作为「这一下的冲击」：招式没写过才盖，写过的以招式为重
        if (source instanceof ModDamageSource modSource) {
            ModDamageSpec spec = modSource.getSpec();
            if (spec != null && spec.getHitImpact() == null) {
                spec.withHitImpact(entry.impact());
            }
        }
        ControlService.apply(target, ControlRequest.poise(entry.poise(), source));
    }
}
