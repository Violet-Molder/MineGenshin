package com.linweiyun.genshin.core.character.sword.miyabi;

import com.linweiyun.genshin.core.character.IBCharacter;
import com.linweiyun.genshin.core.character.sword.SwordCharacter;
import com.linweiyun.genshin.core.character.talent.ConstellationBase;
import com.linweiyun.genshin.core.character.util.capability.IStellarHousehold;
import com.linweiyun.genshin.core.character.util.capability.IStellarStateHolder;
import com.linweiyun.genshin.core.character.util.type.CharacterAscendAttribute;
import com.linweiyun.genshin.content.effect.character.CharacterEffectHelper;
import com.linweiyun.genshin.content.effect.character.CharacterEffectInstance;
import com.linweiyun.genshin.content.effect.character.ICharacterEffect;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterActionData;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterRenderRepository;
import com.linweiyun.genshin.core.system.registry.register.ModCharacterEffects;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.core.system.reaction.StellarGlimmer;
import com.linweiyun.genshin.core.system.reaction.StellarGlimmerBranch;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 星见雅（Miyabi）—— 风元素五星单手剑，<b>联动角色</b>：模型 / 动画 / 贴图来自对方模组（IB）。
 *
 * <h2>资源</h2>
 * 本 MOD 里没有她的美术资源，只有一份说明「每样从哪读」的来源表：
 * {@code assets/minegenshin/character/miyabi/resources.json}。
 * 模型 / 动画 / 贴图指到对方，头像用对方的角色信物图标，立绘留给我们自己。
 *
 * <h2>对方不在时</h2>
 * 角色不注册（见 {@code ModCharacters}），因此这个类只会在对方也在的实例里出现。
 * 类本身不引用对方的任何类型，所以对方不在时本 MOD 照样能构建、能启动。
 *
 * <h2>分工</h2>
 * 对方只提供「门禁（角色在不在）+ 美术素材（模型 / 贴图 / 动画文件 / 信物图标）」；
 * 动画系统、动作时序、骨骼规则、反应户口全部是本 MOD 的。
 *
 * <h2>数值与招式</h2>
 * 动作表见 {@link MiyabiResources}，招式微调见 {@link MiyabiSkill}。
 */
public class Miyabi extends SwordCharacter implements IBCharacter, IStellarHousehold, IStellarStateHolder {

    /** 落霜每层持续时间（刻）。 */
    public static final int FROST_DURATION_TICKS = 200;

    /** 落霜层数上限；后续命座会提高，读数组长度即可。 */
    public static final int MAX_FROST_STACKS = 8;

    /** 星雪状态与覆雪状态的持续时间（刻）。 */
    public static final int SNOW_STATE_TICKS = 160;
    public static final int SNOW_COVER_TICKS = 160;

    /** 深雪冷却（刻）：平时 8 秒，这一下开出星雪才是 15 秒。 */
    public static final int SHORT_DEEP_SNOW_COOLDOWN_TICKS = 8 * 20;
    public static final int DEEP_SNOW_COOLDOWN_TICKS = 15 * 20;

    /** 飞雪自己的冷却（刻），与深雪独立。 */
    public static final int FLYING_SNOW_COOLDOWN_TICKS = 80;

    /** 深雪自己的剩余冷却（刻）；星雪期间 E 显示飞雪的冷却，两者各算各的。 */
    @Persisted(key = "miyabiDeepSnowCd")
    protected int deepSnowCooldownTicks;

    /** 辉映·星超导下深雪消耗几层落霜以上进入星雪。 */
    public static final int SNOW_STATE_FROST_THRESHOLD = 4;

    /** 星雪的动作状态键：飞雪可用，元素战技与重击都换掉。 */
    public static final String SNOW_ACTION_STATE = "snow";

    /** 星雪的动作状态键（飞雪冷却中 / 本轮已放过）：只有重击换成霜月。 */
    public static final String SNOW_CHARGE_ACTION_STATE = "snow_charge";

    /** 落霜每层的剩余时间；0 = 该层不存在。 */
    @DescSynced
    @Persisted(key = "miyabiFrost")
    protected int[] frostTicks = new int[MAX_FROST_STACKS];

    @Persisted(key = "miyabiRime")
    protected int rimeStacks;

    /** 星雪下按 2:1 转烈霜时攒下的单层落霜。 */
    @Persisted(key = "miyabiFrostOverflow")
    protected int frostOverflow;

    @Persisted(key = "miyabiSnowState")
    protected int snowStateTicks;

    @Persisted(key = "miyabiSnowCover")
    protected int snowCoverTicks;

    @Persisted(key = "miyabiSnowCoverStacks")
    protected int snowCoverStacks;

    @Persisted(key = "miyabiFlyingSnowCd")
    protected int flyingSnowCooldownTicks;

    /** 本次星雪周期是否已经放过飞雪。 */
    @Persisted(key = "miyabiSnowEUsed")
    protected boolean snowEUsed;

    private transient float[] skillHitPlan = new float[0];
    private transient int skillHitIndex;
    private transient boolean skillHitStellar;

    /** 资源目录名 / 渲染登记表的键 / 动画登记表的键。 */
    public static final String ID = "miyabi";

    /** 角色 UID。 */
    public static final int UID = 115201;

    /** 她在对方模组里的角色 id —— 与本角色同名，写出来是为了让这条联动一眼可见。 */
    public static final String IB_ID = "miyabi";

    public Miyabi() {
        super(
                UID,
                5,
                Component.translatable("character.name.miyabi"),
                ModElements.CYRO.getId().toString(),
                CharacterAscendAttribute.CDG,
                SHORT_DEEP_SNOW_COOLDOWN_TICKS,
                DEEP_SNOW_COOLDOWN_TICKS,
                60.0F,
                ID,
                statGrowthMap()
        );
        this.skill = new MiyabiSkill();
        this.talent = new MiyabiTalent();
        this.constellation = new ConstellationBase();
        CharacterRenderRepository.register(MiyabiResources.RENDER_DATA);
    }

    @Override
    public String ibCharacterId() {
        return IB_ID;
    }

    @Override
    public CharacterActionData getActionData() {
        return MiyabiResources.ACTION_DATA;
    }

    @Override
    public Map<ResourceLocation, Supplier<List<? extends Integer>>> getStatGrowthMap() {
        return statGrowthMap();
    }

    /**
     * 星见雅的<b>星超导户口</b>：本角色在队伍里时星超导成立，并按攻击力给全队基础伤害提升
     * （实现在 {@link MiyabiTalent#stellarHousehold}）。
     */
    @Override
    public IStellarHousehold.StellarHousehold stellarHousehold() {
        return getTalent() instanceof MiyabiTalent talent ? talent.stellarHousehold(this) : null;
    }

    @Override
    protected void characterTick(Player player) {
        if (player.level().isClientSide()) {
            return;
        }
        for (int i = 0; i < frostTicks.length; i++) {
            if (frostTicks[i] > 0) {
                frostTicks[i]--;
            }
        }
        if (snowCoverTicks > 0 && --snowCoverTicks == 0) {
            snowCoverStacks = 0;
        }
        if (snowStateTicks > 0) {
            snowStateTicks--;
        }
        if (flyingSnowCooldownTicks > 0) {
            flyingSnowCooldownTicks--;
        }

        if (deepSnowCooldownTicks > 0) {
            deepSnowCooldownTicks--;
        }

        // E 的冷却按当前是哪一个战技来显示：星雪里还没放飞雪就是飞雪的 4 秒，其余时候是深雪的
        int wantedCooldown = isSnowState() && !snowEUsed ? flyingSnowCooldownTicks : deepSnowCooldownTicks;
        if ((int) getData().getElementalSkillCooldownTick() != wantedCooldown) {
            getData().setElementalSkillCooldownTick(wantedCooldown);
        }
    }

    /**
     * 深雪 / 飞雪的冷却分开记：开出星雪的那一下是 {@link #DEEP_SNOW_COOLDOWN_TICKS}，
     * 其余深雪是 {@link #SHORT_DEEP_SNOW_COOLDOWN_TICKS}；飞雪自己不影响深雪的冷却。
     */
    @Override
    public void applyElementalSkillCooldown(Player player, int skillTime) {
        getData().setElementalSkillStacks(getData().getElementalSkillStacks() - 1);
        if (!(isSnowState() && snowEUsed)) {
            deepSnowCooldownTicks = isSnowState() ? DEEP_SNOW_COOLDOWN_TICKS : SHORT_DEEP_SNOW_COOLDOWN_TICKS;
        }
        getData().setElementalSkillCooldownTick(
                isSnowState() && !snowEUsed ? flyingSnowCooldownTicks : deepSnowCooldownTicks);
        syncRealtimeState();
    }

    @Override
    public String getActionStateKey(Player player) {
        if (!isSnowState()) {
            return "default";
        }
        return isFlyingSnowMode() ? SNOW_ACTION_STATE : SNOW_CHARGE_ACTION_STATE;
    }

    public boolean isFlyingSnowMode() {
        return snowStateTicks > 0 && !snowEUsed && flyingSnowCooldownTicks <= 0;
    }

    @Override
    public int stellarConduceDurationTicks() {
        return 160;
    }

    @Override
    public float getStellarGlimmerBonus(StellarGlimmerBranch branch) {
        if (branch != StellarGlimmerBranch.CONDUCE || snowCoverTicks <= 0) {
            return 0f;
        }
        return snowCoverStacks * MiyabiTalent.snowCoverPerStack(getData().getElementalSkillLevel());
    }

    public boolean isSnowState() {
        return snowStateTicks > 0;
    }

    /** 星雪视为处于辉映·星超导状态，所以两种来源都算。 */
    public boolean isConduce() {
        return snowStateTicks > 0 || StellarGlimmer.hasConduce(this);
    }

    public int frostStacks() {
        int count = 0;
        for (int ticks : frostTicks) {
            if (ticks > 0) {
                count++;
            }
        }
        return count;
    }

    public void addFrostStack() {
        if (snowStateTicks > 0) {
            frostOverflow++;
            if (frostOverflow >= 2) {
                frostOverflow -= 2;
                rimeStacks++;
            }
            return;
        }
        for (int i = 0; i < frostTicks.length; i++) {
            if (frostTicks[i] <= 0) {
                frostTicks[i] = FROST_DURATION_TICKS;
                return;
            }
        }
    }

    public int consumeFrostStacks() {
        int count = frostStacks();
        java.util.Arrays.fill(frostTicks, 0);
        return count;
    }

    public int rimeStacks() {
        return rimeStacks;
    }

    public void addRimeStacks(int stacks) {
        rimeStacks += stacks;
    }

    public int consumeRimeStacks() {
        int count = rimeStacks;
        rimeStacks = 0;
        return count;
    }

    public void enterSnowState(Player player) {
        snowStateTicks = SNOW_STATE_TICKS;
        flyingSnowCooldownTicks = FLYING_SNOW_COOLDOWN_TICKS;
        snowEUsed = false;
        ICharacterEffect snowEffect = ModCharacterEffects.MIYABI_SNOW_STATE_EFFECT.get();
        if (snowEffect != null) {
            CharacterEffectHelper.addEffect(player, this,
                    new CharacterEffectInstance(snowEffect, SNOW_STATE_TICKS, 0, false));
        }
    }

    public boolean isFlyingSnowReady() {
        return flyingSnowCooldownTicks <= 0;
    }

    public void grantSnowCover(int stacks) {
        snowCoverStacks = stacks;
        snowCoverTicks = SNOW_COVER_TICKS;
    }

    /** 深雪起手：消耗落霜、结算覆雪与星雪、排好这一招的逐段倍率。 */
    public void planDeepSnow(Player player, int level) {
        boolean conduce = isConduce();
        if (!conduce) {
            addFrostStack();
            skillHitPlan = new float[]{MiyabiTalent.deepSnow(level)};
            skillHitIndex = 0;
            skillHitStellar = false;
            return;
        }

        int consumed = consumeFrostStacks();
        float multiplier = MiyabiTalent.deepSnowConduce(level) * (1f + 0.10f * consumed);
        grantSnowCover(consumed);
        if (consumed > SNOW_STATE_FROST_THRESHOLD) {
            addRimeStacks(consumed);
            enterSnowState(player);
        }
        skillHitPlan = new float[]{multiplier * (1f + MiyabiTalent.CONDUCE_MULTIPLIER_BONUS)};
        skillHitIndex = 0;
        skillHitStellar = true;
    }

    /** 飞雪起手：消耗烈霜，按第一段 + 后续等分的多段倍率排好。 */
    public void planFlyingSnow(int level) {
        int consumed = consumeRimeStacks();
        float total = MiyabiTalent.flyingSnowTotal(level)
                * (1f + MiyabiTalent.rimePerStack(level) * consumed);
        float first = Math.round(total * 0.40f * 100f) / 100f;
        int segments = 10;
        float each = (total - first) / segments;
        float[] plan = new float[segments + 1];
        plan[0] = first;
        for (int i = 1; i < plan.length; i++) {
            plan[i] = each;
        }
        skillHitPlan = plan;
        skillHitIndex = 0;
        skillHitStellar = true;
        snowEUsed = true;
    }

    public float nextSkillHitMultiplier() {
        if (skillHitIndex >= skillHitPlan.length) {
            return 0f;
        }
        return skillHitPlan[skillHitIndex++];
    }

    public boolean isSkillHitStellar() {
        return skillHitStellar;
    }

    private static Map<ResourceLocation, Supplier<List<? extends Integer>>> statGrowthMap() {
        return Map.of(
                ModAttributes.MAX_HP.getId(), Miyabi::getAllHp,
                ModAttributes.ATK.getId(), Miyabi::getAllAtk,
                ModAttributes.DEF.getId(), Miyabi::getAllDef
        );
    }

    private static final int[] STAT_INDICES = {
            0, 19, 20, 40, 41, 51, 52, 62, 63, 73, 74, 84, 85, 95
    };

    private static final double[] BASE_HP = {
            1030, 2671, 3554, 5317, 5944, 6839, 7675, 8579, 9207, 10119, 10746, 11669, 12296, 13226
    };

    private static final double[] BASE_ATK = {
            27, 69, 92, 137, 154, 177, 198, 222, 238, 262, 278, 302, 318, 342
    };

    private static final double[] BASE_DEF = {
            59, 152, 202, 302, 338, 389, 437, 488, 524, 576, 611, 664, 700, 752
    };

    private static final int MAX_STAT_INDEX = 96;

    public static List<Integer> getAllHp() {
        return buildAttributeList(BASE_HP);
    }

    public static List<Integer> getAllAtk() {
        return buildAttributeList(BASE_ATK);
    }

    public static List<Integer> getAllDef() {
        return buildAttributeList(BASE_DEF);
    }

    private static List<Integer> buildAttributeList(double[] milestones) {
        List<Integer> out = new ArrayList<>(MAX_STAT_INDEX);
        for (int statIndex = 0; statIndex < MAX_STAT_INDEX; statIndex++) {
            out.add((int) Math.floor(interpolateStat(statIndex, milestones)));
        }
        return out;
    }

    private static double interpolateStat(int statIndex, double[] milestones) {
        if (statIndex <= STAT_INDICES[0]) {
            return milestones[0];
        }
        for (int i = 1; i < STAT_INDICES.length; i++) {
            if (statIndex <= STAT_INDICES[i]) {
                int fromIdx = STAT_INDICES[i - 1];
                int toIdx = STAT_INDICES[i];
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
}
