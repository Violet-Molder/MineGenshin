package com.linweiyun.genshin.core.character.sword.vesna;

import com.linweiyun.genshin.core.character.sword.SwordCharacter;
import com.linweiyun.genshin.core.character.util.capability.IStellarHousehold;
import com.linweiyun.genshin.core.character.util.capability.IStellarStateHolder;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.reaction.StellarGlimmerBranch;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterActionData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderRepository;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.core.character.util.type.CharacterAscendAttribute;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import lombok.Getter;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static net.minecraft.network.chat.Component.literal;
import static net.minecraft.network.chat.Component.translatable;

/**
 * 薇斯娜（Vesna）—— 风元素五星单手剑。
 *
 * <h2>主类只做中转/调度</h2>
 * 三个协作者：{@link VesnaSkill}（招式）/ {@link VesnaTalent}（突破天赋）/
 * {@link VesnaConstellation}（命座）。它们都在无参构造器里建好
 * （客户端反序列化走 {@code getDeclaredConstructor().newInstance()}，会跑到构造器）。
 *
 * <p>主类上剩下的东西只有两类：
 * <ol>
 *   <li><b>持久化状态字段</b>（{@code @Persisted} / {@code @DescSynced}）——
 *       键属于存档格式，搬家会让老存档读不出来，所以字段与它的读写访问器留在这里
 *       （{@code decreeTicks}、{@code bianyiWindowEndTick}、{@code vesnaEnergy}…）；
 *       <b>怎么用</b>这些状态在协作者里；</li>
 *   <li><b>框架钩子的转发</b>（{@code getSovereigntyBonus} / {@code stellarHousehold} /
 *       {@code getStellarGlimmerBonus} / {@code getOwnElevationBonus} / {@code tick} …）——
 *       一行调用对应协作者，不写实现。</li>
 * </ol>
 */
@Getter
public class Vesna extends SwordCharacter implements IStellarHousehold, IStellarStateHolder {
    public static final String ID = "vesna";    public static final int UID = 115001;
    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);

    public static final int WIND_RIDER_DURATION_TICKS = 15 * 20;
    /** 每层剑气 = 6 点能量 */
    public static final float ENERGY_PER_QIQI = 6f;
    /** 翔风剑一次消耗 1 层剑气 */
    public static final float SPECIAL_SKILL_ENERGY_COST = ENERGY_PER_QIQI;
    /** 开 E 获得两层剑气 */
    public static final float WIND_RIDER_ENTER_ENERGY = ENERGY_PER_QIQI * 2;
    /** 一次巡风列装最多能放几次最高境界（三阶）翔风剑（0 命）。命座 1 会 +1。 */
    public static final int BASE_LV3_USES_IN_WINDRIDER = 3;
    /** 整肃最多叠几层 —— 这是 {@link #decreeTicks} 的数组尺寸（状态形状，规则在 {@link VesnaTalent}）。 */
    public static final int MAX_DECREE_STACKS = 6;

    @DescSynced @Persisted(key = "vesnaEnergy")
    protected float vesnaEnergy;
    @DescSynced @Persisted(key = "vesnaMaxEnergy")
    protected float vesnaMaxEnergy;
    @DescSynced @Persisted(key = "windriderActive")
    protected boolean windriderActive;
    @DescSynced @Persisted(key = "windriderRemainingTicks")
    protected int windriderRemainingTicks;

    /** 下一次施放的翔风剑阶级（1~3）。技能读取它当本次施放等级。 */
    @DescSynced @Persisted(key = "xiangfengJianLevel")
    protected int xiangfengJianLevel = 0;

    /** 本次巡风列装已施放的三阶次数，达到 {@code VesnaConstellation.maxLv3UsesInWindrider} 次退出模式。 */
    @DescSynced @Persisted(key = "lv3UsesInWindrider")
    protected int lv3UsesInWindrider = 0;

    /**
     * 这一次施放是不是「进入巡风列装」的那一下。
     *
     * <p>在<b>触发那一刻</b>定下来（{@code VesnaSkill.onCastStart}），伤害点只读它。
     * 为什么不能等到伤害点再判断「模式开没开」：模式现在触发时就开了，
     * 到伤害点再看会把入门那一刀误当成翔风剑（扣剑气、打错倍率、推进阶级）。
     *
     * <p>纯服务端运行时状态，故意不加 {@code @Persisted/@DescSynced}。
     */
    protected boolean entryCast = false;

    // ==================== 整肃（大权区的来源）的状态本体 ====================

    /**
     * 整肃：每一层自己的剩余刻数，{@code 0} = 这一层不存在。
     *
     * <p>用「每层一个计时器」而不是一个总时长 —— 文案写的是
     * 「每层独立计算持续时间」，一个总时长做不到「先叠的那层先掉」。
     *
     * <p>叠层 / 掉层 / 清空的<b>规则</b>在 {@link VesnaTalent}（这个数组只是状态本体）。
     */
    @DescSynced @Persisted(key = "decreeTicks")
    protected int[] decreeTicks = new int[MAX_DECREE_STACKS];

    public Vesna() {
        super(UID, 5, translatable("character.name.vesna"),
                ModElements.ANEMO.getId().toString(), CharacterAscendAttribute.CR,
                18 * 20, 15 * 20, 60f, ID,
                Map.of(
                        ModAttributes.MAX_HP.getId(), Vesna::getAllHp,
                        ModAttributes.ATK.getId(), Vesna::getAllAtk,
                        ModAttributes.DEF.getId(), Vesna::getAllDef
                ));
        CharacterRenderRepository.register(VesnaResources.RENDER_DATA);
        // 三个协作者都在无参构造器里建：客户端反序列化走
        // clazz.getDeclaredConstructor().newInstance()（会跑到这里），双端都拿得到实例。
        this.skill = new VesnaSkill();
        this.talent = new VesnaTalent();
        this.constellation = new VesnaConstellation();
        this.vesnaEnergy = 0;
        this.vesnaMaxEnergy = 18;
    }

    @Override
    public CharacterActionData getActionData() {
        return VesnaResources.ACTION_DATA;
    }

    @Override
    public Map<Identifier, Supplier<List<? extends Integer>>> getStatGrowthMap() {
        return Map.of(
                ModAttributes.MAX_HP.getId(), Vesna::getAllHp,
                ModAttributes.ATK.getId(), Vesna::getAllAtk,
                ModAttributes.DEF.getId(), Vesna::getAllDef
        );
    }

    // ==================== 转发：大权 / 户口 / 命座加成 ====================

    /**
     * 大权区加成 = 整肃层数 × 10%（实现在 {@link VesnaTalent#sovereigntyBonus}）。
     *
     * <p>只作用在「灵剑」那几段（翔风剑二阶第二段 / 三阶两段 / 大招）——
     * 具体哪几段由技能造伤害时决定，不是全局生效。
     */
    @Override
    public float getSovereigntyBonus() {
        return getTalent() instanceof VesnaTalent talent ? talent.sovereigntyBonus(this) : 0f;
    }

    /**
     * 薇斯娜的<b>星扩散户口</b>：冰扩散 → 星扩散，并按攻击力给全队基础伤害提升
     * （实现在 {@link VesnaTalent#stellarHousehold}）。
     */
    @Override
    public IStellarHousehold.StellarHousehold stellarHousehold() {
        return getTalent() instanceof VesnaTalent talent ? talent.stellarHousehold(this) : null;
    }

    /** 命座 1：巡风列装模式下的星扩散反应伤害加成（实现在 {@link VesnaConstellation}）。 */
    @Override
    public float getStellarGlimmerBonus(StellarGlimmerBranch branch) {
        return getConstellationObj() instanceof VesnaConstellation constellation
                ? constellation.stellarGlimmerBonus(this, branch)
                : 0f;
    }

    /** 命座 6：薇斯娜造成的星扩散反应伤害<b>擢升</b> 20%（实现在 {@link VesnaConstellation}）。 */
    @Override
    public float getOwnElevationBonus(StellarGlimmerBranch branch) {
        return getConstellationObj() instanceof VesnaConstellation constellation
                ? constellation.elevationBonus(this, branch)
                : 0f;
    }

    // ==================== 同步 ====================

    /**
     * 技能状态变化后调它：常规同步 + 多标一次「整包同步」。
     *
     * <p>为什么不能只靠 {@code syncRealtimeState()}：那是 LDLib2 的增量包，
     * 客户端收不到（客户端没绑 ownerPlayer，处理函数直接返回）。
     * 真正到得了客户端的是 {@code CharacterTickHandler} 里「{@code data.isDirty()} → 整包同步」
     * 那条路，而它只认 {@code PGCharacterData.markDirty()}。
     *
     * <p>不标的话：没有 CD 在转的时候 {@code PGCharacterData.tick()} 不会标 dirty，
     * 于是「进入巡风列装 / 剑气变化」永远传不到客户端 ——
     * 表现就是能量条不出现、图标不显示技能状态。
     *
     * <p><b>只在这些「事件型」的改状态方法里调</b>，不要在每 tick 的路径上调：
     * 整包同步发的是整份角色数据，每 tick 发一次没必要。
     *
     * <p>（三个协作者也要用它，所以是 public 的。）
     */
    public void syncSkillState() {
        syncRealtimeState();
        getData().markDirty();
    }

    // ==================== 能量 ====================

    public void addEnergy(float value) {
        this.vesnaEnergy = Math.min(vesnaEnergy + value, vesnaMaxEnergy);
        syncSkillState();
    }

    public void consumeEnergy(float value) {
        this.vesnaEnergy = Math.max(vesnaEnergy - value, 0);
        syncSkillState();
    }

    // ==================== 模式切换 ====================

    public void activateWindriderMode() {
        this.windriderActive = true;
        this.windriderRemainingTicks = WIND_RIDER_DURATION_TICKS;
        this.vesnaEnergy = WIND_RIDER_ENTER_ENERGY;
        this.xiangfengJianLevel = 0;
        this.lv3UsesInWindrider = 0;
        this.entryCast = false;   // 进门这一刀由技能紧接着打标记
        syncSkillState();

        // 命座 2：进入巡风列装直接拿满整肃（在天赋的清层之后跑，所以清完就填满）
        // —— 命座规则实现在 VesnaConstellation。
        if (getConstellationObj() instanceof VesnaConstellation constellation) {
            constellation.onWindriderEnter(this);
        }
    }

    public void exitWindriderMode() {
        this.windriderActive = false;
        this.windriderRemainingTicks = 0;
        this.vesnaEnergy = 0f;
        this.xiangfengJianLevel = 0;
        this.lv3UsesInWindrider = 0;
        this.entryCast = false;
        syncSkillState();
    }

    // ==================== 字段 setter（供协作者修改） ====================

    public void setXiangfengJianLevel(int level) {
        this.xiangfengJianLevel = Math.max(0, Math.min(3, level));
        syncSkillState();
    }

    public void setLv3UsesInWindrider(int n) {
        this.lv3UsesInWindrider = Math.max(0, n);
        syncSkillState();
    }

    // ==================== 入门那一刀 ====================

    /** 标记「这一刀是进入巡风列装的那一下」（触发那一刻调用）。 */
    public void markEntryCast() {
        this.entryCast = true;
    }

    /** 取出并清掉入门标记；{@code true} = 这一刀是入门刀。 */
    public boolean consumeEntryCast() {
        boolean value = this.entryCast;
        this.entryCast = false;
        return value;
    }

    // ==================== 满命「变移」窗口的状态本体 ====================

    /**
     * 命座 6：「变移」窗口的绝对到期时刻（0 = 窗口没开）。
     *
     * <p>用<b>绝对到期时刻</b>而不是倒计时：这样客户端不用等服务端每刻同步，
     * 自己拿本地游戏时间比一下就知道窗口还在不在（{@link #hasBianyiWindow}）。
     * 开关窗口的规则在 {@link VesnaConstellation}。
     */
    @DescSynced @Persisted(key = "bianyiWindowEndTick")
    protected long bianyiWindowEndTick;

    /** 命座 6：是否处于「变移」窗口内（双端都能判，用各自的游戏时间比）。 */
    public boolean hasBianyiWindow(Player player) {
        return bianyiWindowEndTick > 0L && player.level().getGameTime() < bianyiWindowEndTick;
    }

    /** 写入「变移」窗口的到期刻（由 {@link VesnaConstellation} 算好后传进来）。 */
    public void setBianyiWindowEndTick(long tick) {
        this.bianyiWindowEndTick = tick;
    }

    // ==================== action state ====================

    @Override
    public String getActionStateKey(Player player) {
        // 满命窗口内：普攻 / E 点按都换成「翔风剑·变移」，放完窗口关掉、键自然回到普通队列
        if (hasBianyiWindow(player)) return "bianyi";
        if (!windriderActive) return "default";
        return "windrider_" + xiangfengJianLevel;
    }

    @Override
    public float getSkillDisplayCooldown() {
        if (windriderActive) return 0f;
        return super.getSkillDisplayCooldown();
    }

    // ==================== 大招的「权威数值」====================
    //
    // 大招能量 60 / 冷却 15 秒。这两个值也会被写进角色数据（HUD 读的就是它们），
    // 而老存档里存的是旧值（80 能量 / 10 秒冷却）—— 数据是持久化的，
    // 光改构造器对**已经存在的角色**无效，所以这里做一次自愈：
    // 读的时候发现和常量不一致就改回去。

    public static final float BURST_ENERGY_COST = 60f;
    public static final int BURST_COOLDOWN_TICKS = 15 * 20;

    @Override
    public int getBurstMaxCooldownTick() {
        if (getData().getBurstMaxCooldownTick() != BURST_COOLDOWN_TICKS) {
            getData().setBurstMaxCooldownTick(BURST_COOLDOWN_TICKS);
        }
        return BURST_COOLDOWN_TICKS;
    }

    @Override
    public float getMaxObtainingEnergy() {
        if (getData().getMaxObtainingEnergy() != BURST_ENERGY_COST) {
            getData().setMaxObtainingEnergy(BURST_ENERGY_COST);
        }
        return BURST_ENERGY_COST;
    }

    // ==================== E 钩子覆写 ====================

    @Override
    public boolean canUseElementalSkill(Player player, int skillTime) {
        if (windriderActive) {
            // 命座 1：本次进入模式后的第一次最高境界翔风剑免剑气 —— 没剑气也放得出来
            //（判据实现在 VesnaConstellation）
            if (getConstellationObj() instanceof VesnaConstellation constellation
                    && constellation.lv3CastIsFreeNow(this)) {
                return true;
            }
            return vesnaEnergy >= SPECIAL_SKILL_ENERGY_COST;
        }
        return super.canUseElementalSkill(player, skillTime);
    }

    @Override
    public void applyElementalSkillCooldown(Player player, int skillTime) {
        if (player.level().isClientSide()) return;

        // ⚠️ 判断依据是「这一刀是不是进入巡风列装的那一下」（entryCast），
        //    不能再用 windriderActive —— 模式现在是在「触发那一刻」就开的
        //    （VesnaSkill.onCastStart，跑在 request() 里面，早于本方法），
        //    到这里它已经是 true。之前就是这么漏掉 CD 的：
        //    CD 没设 → PGCharacterData 不标 dirty → 整包同步不触发 →
        //    客户端连模式/剑气都收不到（能量条不出现、图标不显示 CD、能直接再开一次）。
        if (entryCast) {
            getData().setElementalSkillCooldownTick(18 * 20);
            syncRealtimeState();
        }
    }

    @Override
    public void sendSkillCooldownMessage(Player player) {
        if (windriderActive) {
            player.sendSystemMessage(literal("§4剑气不足！"));
        } else {
            super.sendSkillCooldownMessage(player);
        }
    }

    // ==================== tick（只做调度） ====================

    @Override
    protected void characterTick(Player player) {
        if (player.level().isClientSide()) return;

        if (windriderActive && windriderRemainingTicks > 0) {
            windriderRemainingTicks--;
            if (windriderRemainingTicks <= 0) {
                exitWindriderMode();
            }
        }

        if (windriderActive) {
            syncRealtimeState();
        }

        // 整肃每层独立倒计时 + 退场清层 + 突破 4 的队伍加成（实现在 VesnaTalent）
        getTalent().tick(player, this);
        // 命座 2 的「满层整肃 → 攻击力 +40%」（实现在 VesnaConstellation）
        getConstellationObj().tick(player, this);
    }

    private static final int[] VESNA_STAT_INDICES = {
            0, 19, 20, 40, 41, 51, 52, 62, 63, 73, 74, 84, 85, 95
    };

    private static final double[] VESNA_HP = {
            1032, 2678, 3563, 5332, 5961, 6858, 7697, 8603, 9232, 10147, 10776, 11701, 12330, 13262
    };

    private static final double[] VESNA_ATK = {
            27.56, 71.48, 95.11, 142.32, 159.1, 183.05, 205.43, 229.63, 246.42, 270.83, 287.62, 312.31, 329.1, 353.98
    };

    private static final double[] VESNA_DEF = {
            56.84, 147.44, 196.17, 293.54, 328.17, 377.56, 423.73, 473.63, 508.26, 558.62, 593.25, 644.17, 678.8, 730.13
    };

    private static final int VESNA_MAX_STAT_INDEX = 96;

    private static List<Integer> buildAttributeList(double[] milestones) {
        List<Integer> out = new ArrayList<>(VESNA_MAX_STAT_INDEX);
        for (int statIndex = 0; statIndex < VESNA_MAX_STAT_INDEX; statIndex++) {
            out.add((int) Math.floor(interpolateStat(statIndex, milestones)));
        }
        return out;
    }

    private static double interpolateStat(int statIndex, double[] milestones) {
        if (statIndex <= VESNA_STAT_INDICES[0]) {
            return milestones[0];
        }
        for (int i = 1; i < VESNA_STAT_INDICES.length; i++) {
            if (statIndex <= VESNA_STAT_INDICES[i]) {
                int fromIdx = VESNA_STAT_INDICES[i - 1];
                int toIdx = VESNA_STAT_INDICES[i];
                double from = milestones[i - 1];
                double to = milestones[i];
                if (toIdx == fromIdx) {
                    return from;
                }
                double t = (double) (statIndex - fromIdx) / (toIdx - fromIdx);
                return from + (to - from) * t;
            }
        }
        return milestones[milestones.length - 1];
    }

    public static List<Integer> getAllHp() {
        return buildAttributeList(VESNA_HP);
    }

    public static List<Integer> getAllAtk() {
        return buildAttributeList(VESNA_ATK);
    }

    public static List<Integer> getAllDef() {
        return buildAttributeList(VESNA_DEF);
    }
}
