package com.linweiyun.genshin.core.system.poise;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.content.entities.teyvat.ITeyvatBoss;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;

import java.util.Set;

/**
 * 韧性档位 —— 本项目「谁能被控制、谁多硬」的唯一总表。
 *
 * <h2>为什么只有 4 档，而不是照搬原神的韧性类型表</h2>
 * 原神那套是「一种韧性类型 = 4 个参数（条长 / 每秒衰减 / 重置时间 / 内置霸体系数）」，
 * 按类型查表。本项目现阶段<b>不分那么多类型</b>：只分 4 档，
 * 而且<b>档位由抗牵引等级直接派生</b>——抗牵引等级 1 的怪就是 1 档韧性，4 的就是 4 档。
 *
 * <p>于是「牵引拉不动它」和「它很耐打（难破韧）」是同一件事的两种表现，不会出现
 * 「拉不动但很好破韧」这种自相矛盾的组合。以后要拆开时，把 {@link #poiseTier} 单独接一张表即可。
 *
 * <h2>数值是占位，且只写在这一处</h2>
 * 每一档对应的韧性条长度、每秒衰减、重置时间填的是<b>本项目自己的手感值</b>
 * （见 {@link #TIER_PROFILES}）：骨架来自文献里「小型 → 低阶 → 中阶 → 高阶」
 * 那四行的递增关系，<b>第 1 档（普通生物）已按用户口径上调</b>（条长 30 → 100，
 * 见那张表下面的说明），其余各档仍是占位、随时可以整表替换。
 * 每只怪还能用 {@code minegenshin:poise}（档位）与 {@code minegenshin:poise_max}
 * （逐实例的条长）单独覆盖，不必改这张表。
 * <p>要调数值<b>只改这一张表</b>，不要散到调用方；调用方一律走
 * {@link #profileOf(int)} / {@link #profileOf(LivingEntity)}。
 *
 * <h2>来源表（暂定）</h2>
 * <table border="1">
 *   <caption>按来源查 {@link #gatherResist}</caption>
 *   <tr><th>来源</th><th>抗牵引等级</th><th>韧性档位</th><th>聚怪拉得动吗</th></tr>
 *   <tr><td>原版普通生物（僵尸、骷髅、牛……）</td><td>1</td><td>1</td><td>任何一档都行 —— 最低档那笔初始削韧就够打破</td></tr>
 *   <tr><td>原版大体型（劫掠兽、铁傀儡、恶魂、远古守卫者、疣猪兽、僵尸疣猪兽）</td><td>4</td><td>4</td><td>只有最高档 —— 初始削韧得够打破它那条 4 档韧性</td></tr>
 *   <tr><td>原版三个 BOSS（末影龙、凋灵、监守者）</td><td>{@link #IMMUNE}</td><td>4（封顶）</td><td>谁都拉不动</td></tr>
 *   <tr><td>本模组自己的生物（冰史莱姆等）</td><td>1</td><td>1</td><td>任何一档；以后按怪物单独配</td></tr>
 *   <tr><td>其他模组的实体</td><td>{@link #IMMUNE}</td><td>4（封顶）</td><td>暂不支持，谁都拉不动</td></tr>
 * </table>
 *
 * <p><b>BOSS 只免疫牵引，不免疫破韧</b>：它们是 4 档韧性（最耐打）而不是无限韧性，
 * 所以照样能被打到破韧 —— 「拉不动」和「打不破」是两件事。
 * 而聚怪那条路上，这两件事会合并：聚怪先砸一笔初始削韧，<b>打不破就拉不走</b>
 * （见 {@code content.skill_node.GatherPull}），所以「低等级聚怪拉不动硬怪」
 * 和「它很耐打」是同一个原因，没有第二道门槛。
 * 要改成完全不可破韧，就在这里让 {@link #poiseTier} 对免疫目标返回 {@link #IMMUNE}。
 *
 * <p><b>首领另外还有一条</b>：{@link #BOSS_SUPER_ARMOR 默认霸体强度}远高于普通生物，
 * 所以「破韧期间随便打断」那条对它们不成立 —— 打不断是默认状态，
 * 只有破绽窗口（{@code ControlService.openGap}）与直通口（{@code ControlService.force}）
 * 那两条路能打断；本模组的首领还会另外覆盖 {@code Controllable#blocksControl} 把入口堵上。
 */
public final class PoiseTiers {

    /** 档位上限：韧性暂时只分 4 档。 */
    public static final int MAX_TIER = 4;

    /** 谁也控制不了。比最高档大即是免疫，不要写成 5 这种「看起来还能再加档」的数。 */
    public static final int IMMUNE = Integer.MAX_VALUE;

    /** 原版普通生物：最低档牵引也拉得动。 */
    public static final int VANILLA_RESIST = 1;

    /** 本模组自己的生物：暂定与普通生物同档，以后按怪物单独配。 */
    public static final int OWN_RESIST = 1;

    /** 原版大体型：只有最高档拉得动。 */
    public static final int LARGE_RESIST = 4;

    /**
     * 首领一档（{@link ITeyvatBoss} 与原版三个 BOSS）默认的<b>霸体强度</b> ——
     * 抗打断与抗削韧共用这一个系数。
     *
     * <p>取 10 是刻意的：比文献 0–9 表里最强的一档（击飞 9）还高一档，
     * 含义就是「<b>普通攻击打不断首领</b>」。首领的破绽由它自己的节奏给
     * （{@code ControlService.openGap}），要更直接就走直通口（{@code ControlService.force}）。
     * 数值是占位值，要调就改这一处，或给某只怪显式配 {@code SUPER_ARMOR} 属性。
     *
     * <p>「部分精英怪也这样」不用改代码：给那只怪配一个 3~5 的 {@code SUPER_ARMOR}
     * 就得到「轻击打不断、击退/击飞才打断」的表现。
     */
    public static final float BOSS_SUPER_ARMOR = 10f;

    /**
     * 原版「大体型」名单 —— 身体大、站得稳，低档风场拽不动。
     * 只按体型判断，与是不是精英怪无关。
     */
    private static final Set<EntityType<?>> LARGE_VANILLA_TYPES = Set.of(
            EntityTypes.RAVAGER,
            EntityTypes.IRON_GOLEM,
            EntityTypes.GHAST,
            EntityTypes.ELDER_GUARDIAN,
            EntityTypes.HOGLIN,
            EntityTypes.ZOGLIN);

    private PoiseTiers() {
    }

    /**
     * 目标的抗牵引等级 —— 它同时是<b>韧性档位</b>与<b>聚怪免疫标记</b>的来源。
     *
     * <p>口径更正后（见 {@code GatherPull}）：聚怪不再按这个值比大小，
     * 而是靠自己的「初始削韧」打破对方的韧性才拉得动 —— 所以这个值在这里的作用变成
     * ①决定对方有几档韧性、②{@link #isImmune} 为真时表示「谁都拉不动」。
     *
     * <p>判据是<b>来源</b>而不是个体：原版按体型分档，本模组的怪先一律 1，
     * 原版三个 BOSS 与其它模组的实体直接免疫。
     *
     * <p>以后要给某只怪单独调档，改这一处即可 —— 不要把这个判断散到调用方去。
     */
    public static int gatherResist(LivingEntity target) {
        // 首领那一档：本模组 BOSS（接口已在项目里）+ 原版三个 BOSS
        // （口径与 EntityDeathDropHandler 里的 boss 掉落一致）
        if (isBossTier(target)) {
            return IMMUNE;
        }

        EntityType<?> type = target.getType();
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (id == null) {
            // 没进注册表（理论上不该发生）：按最宽松处理，别让它变成隐形免疫
            return VANILLA_RESIST;
        }

        String namespace = id.getNamespace();
        if (Minegenshin.MOD_ID.equals(namespace)) {
            return OWN_RESIST;
        }
        if (isForeign(target)) {
            // 其它模组的实体：暂不支持
            return IMMUNE;
        }

        return LARGE_VANILLA_TYPES.contains(type) ? LARGE_RESIST : VANILLA_RESIST;
    }

    /**
     * 这个实体是不是「别的模组的东西」—— 本模组<b>暂不支持</b>的实体。
     *
     * <p>判据与 {@link #gatherResist} 里那条一致，只是抽出来给别处共用
     * （最直接的一处是 {@code Controllable#interruptAction}：不敢对别人的 Goal
     * 乱调 {@code stop()}）。取不到注册名（理论上不该发生）时按「不是外来户」处理 ——
     * 不要让它变成隐形免疫。
     */
    public static boolean isForeign(LivingEntity target) {
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        if (id == null) {
            return false;
        }
        String namespace = id.getNamespace();
        return !Minegenshin.MOD_ID.equals(namespace)
                && !Identifier.DEFAULT_NAMESPACE.equals(namespace);
    }

    /**
     * 目标的<b>默认</b>霸体强度（抗打断系数 / 抗削韧系数）：首领一档，其余都是 1。
     *
     * <p>只负责「没显式配过属性时给什么」，读属性的那一半在
     * {@link PoiseService#superArmorOf}：显式配过以属性为准（用户给某只怪单独调的口子）。
     */
    public static float defaultSuperArmor(LivingEntity target) {
        return isBossTier(target) ? BOSS_SUPER_ARMOR : 1f;
    }

    /**
     * 这只生物算不算「首领那一档」：本模组的 {@link ITeyvatBoss}
     * 与<b>原版三个 BOSS</b>（末影龙、凋灵、监守者）—— 名单与 {@link #gatherResist} 里那份一致。
     *
     * <p>这一档同时意味着两件事：<b>10 倍耐削韧</b>（系数除以它）与
     * <b>破韧也打不断</b>（系数高过最强的一档击飞 9，见
     * {@code core.system.control.Controllable#runControl}）。
     */
    public static boolean isBossTier(LivingEntity target) {
        return target instanceof ITeyvatBoss
                || target instanceof EnderDragon
                || target instanceof WitherBoss
                || target instanceof Warden;
    }

    /**
     * 目标的韧性档位（1–{@link #MAX_TIER}）。
     *
     * <p>现阶段直接由 {@link #gatherResist} 派生，免疫目标封顶到最高档。
     * 这是「按牵引等级赋予对应等级的韧性」那一条的落点。
     */
    public static int poiseTier(LivingEntity target) {
        return Math.clamp(gatherResist(target), 1, MAX_TIER);
    }

    /** 这个抗性值是不是「免疫」。 */
    public static boolean isImmune(int resist) {
        return resist >= IMMUNE;
    }

    // ==================== 每档的参数（占位值） ====================

    /**
     * 一档韧性的三个参数 —— 照文献「韧性类型」表那一行的同一套口径。
     *
     * @param length         韧性条计量长度（攒满这么多削韧值就破韧）
     * @param decayPerSecond 每秒自然衰减（自恢复速度）
     * @param resetSeconds   破韧后的驻留时间（秒）；0 = 不驻留，破韧那一刻就开始恢复
     */
    public record TierProfile(float length, float decayPerSecond, float resetSeconds) {
    }

    /**
     * 4 档韧性参数表 —— <b>占位值，等填表</b>。下标 0 是 1 档。
     *
     * <p>取文献「韧性类型」表里递增的那四行做骨架，但<b>第 1 档按用户口径整体上调</b>
     * （2026-09-25：「上调普通怪物的基础韧性值」）：
     * 原来 30 的条长一下普攻就破了，普通怪几乎一直在破韧状态里，于是「没破韧不吃控制」
     * 这条主玩法反而看不见。现在：
     *
     * <table border="1">
     *   <caption>1 档（普通生物）实际要几下普攻破韧 —— 抗打断系数 1</caption>
     *   <tr><th>武器</th><th>普攻削韧</th><th>几下破（条长 100）</th></tr>
     *   <tr><td>单手剑</td><td>50</td><td>2 下</td></tr>
     *   <tr><td>长柄武器</td><td>45.8</td><td>3 下</td></tr>
     *   <tr><td>双手剑</td><td>107.4</td><td>1 下（大剑本来就该一下）</td></tr>
     *   <tr><td>弓</td><td>15.7</td><td>7 下</td></tr>
     *   <tr><td>法器</td><td>10.2</td><td>10 下（文献里法器就是最轻的，没改武器表）</td></tr>
     * </table>
     *
     * <p>第 2 / 3 档跟着抬高，只是为了让「1 &lt; 2 &lt; 3 &lt; 4」这条递增关系继续成立
     * （现阶段没有任何生物落在 2、3 档：档位由抗牵引等级派生，只有 1 与 4）。
     * <b>第 4 档（大体型与 BOSS）一个数都没动</b> —— 这次的口径只针对普通怪。
     *
     * <p>每秒衰减跟着条长一起抬（1→5/s），否则条长了三倍、恢复时间也跟着长三倍，
     * 「哪怕一直被攻击也会恢复」那条自恢复的手感就没了。四档的满条恢复时间因此都落在 14–20 秒。
     *
     * <p>这是<b>唯一</b>该写数值的地方 —— 改表不动代码；武器那张表（{@link WeaponPoiseTable}）
     * 是另一个旋钮，要「降低角色的削韧模板」就改那边，两边互不覆盖。
     */
    private static final TierProfile[] TIER_PROFILES = {
            new TierProfile(100f, 5f, 5f),
            new TierProfile(140f, 8f, 5f),
            new TierProfile(200f, 12f, 3f),
            new TierProfile(280f, 20f, 2f)
    };

    /** 取某一档的参数；超出 1–{@link #MAX_TIER} 会被钳进区间（配错也不崩）。 */
    public static TierProfile profileOf(int tier) {
        return TIER_PROFILES[Math.clamp(tier, 1, MAX_TIER) - 1];
    }

    /**
     * 取某个目标的档位参数。
     *
     * <p>⚠️ 这里走的是 {@link #poiseTier}（由抗牵引等级派生）。
     * 如果目标显式配过 {@code POISE} 属性，档位以属性为准 ——
     * 那个判断在 {@link PoiseService#tierOf}，本方法只负责「给档位取参数」。
     */
    public static TierProfile profileOf(LivingEntity target) {
        return profileOf(poiseTier(target));
    }
}
