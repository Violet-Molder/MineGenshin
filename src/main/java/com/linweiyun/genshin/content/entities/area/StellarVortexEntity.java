package com.linweiyun.genshin.content.entities.area;

import com.linweiyun.genshin.config.reaction.ReactionConfig;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.elementlib.core.attachment.StatusContainer;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.elementlib.core.system.about.AttachmentProfile;
import com.linweiyun.elementlib.core.system.about.AttachmentSource;
import com.linweiyun.elementlib.core.system.about.ElementalAttachmentHelper;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.elementlib.api.ElementalReactionType;
import com.linweiyun.genshin.content.skill_node.GatherPull;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

import java.util.*;
import com.linweiyun.elementlib.core.attachment.ElementalAttachments;
import com.linweiyun.genshin.core.system.registry.register.ModReactionTypes;

public class StellarVortexEntity extends AreaEntity {
    public static final Logger LOGGER = ModLog.getLogger(LogGroup.CONTENT);

    private static final int EXPLODE_TIMER = 60;
    private static final int MAX_LEVEL = 6;
    private static final float DEFAULT_HORIZONTAL_RANGE = 5.0f;
    private static final float LEVEL3_HORIZONTAL_RANGE = 7.0f;
    private static final int LEVEL3_THRESHOLD = 3;
    private static final float ICE_ATTACH_QUANTITY = 1.0f;

    /**
     * 星璇的牵引等级：<b>最低档 L1</b> —— 初始削韧等于 1 档韧性条的长度，
     * 所以原版普通生物被它砸一下就破韧、随即被拉走；
     * 大体型（劫掠兽那一类，4 档）要更高档的聚怪才打得动，
     * 三个 BOSS 与其它模组的实体是免疫，任何等级都拉不动。
     * 等级只决定<b>初始削韧多大</b>，和「拉得多快」无关，速度看下面那条。
     */
    private static final GatherPull.Level GATHER_LEVEL = GatherPull.Level.L1;

    /** 牵引速度：<b>1 格 / 1.5 秒</b>（用户口径）。 */
    private static final double GATHER_BLOCKS_PER_SECOND = 1.0 / 1.5;

    /**
     * 牵引核心的半宽/半高 —— 目标进到「星璇中心这一小圈」里就不再受力。
     *
     * <p>核心是<b>一块区域</b>而不是一个点（这正是 {@link GatherPull} 用两个 AABB 的原因）：
     * 到位就该停，不然一群怪会叠在同一个坐标上。
     */
    private static final double GATHER_CORE_HALF = 0.5;
    private static final double GATHER_CORE_HALF_HEIGHT = 1.0;

    /**
     * 牵引期间的持续削韧：每秒再削掉多少（<b>占位值</b>，用户已裁定韧性表先不细调）。
     *
     * <p>「聚怪本身会削韧」那条规则有两截：<b>一开始的高额初始削韧</b>（等级决定，
     * 在 {@link GatherPull} 里）与这里的<b>持续削韧</b>。前者负责打破，后者负责压住 ——
     * 破韧后持续牵引还会把韧性恢复按住（强控），所以拉着的时候破绽窗口不会自己走完。
     */
    private static final float GATHER_POISE_PER_SECOND = 4f;

    @Persisted(key = "sv_level")
    private int vortexLevel = 1;

    @Persisted(key = "sv_explode_timer")
    private int explodeTimer = EXPLODE_TIMER;

    @Persisted(key = "sv_exploded")
    private boolean exploded;

    /**
     * 这一枚是不是「<b>流荡风旋</b>」（沃雅妮莎突破天赋 1）。
     *
     * <p>流荡风旋和星辉风旋其它行为完全一致，只有两点不同：
     * <b>创造</b>与<b>引爆</b>时会降低附近敌人的风元素抗性 35%（6 秒）。
     * 创造那一次由 `SwirlReaction` 直接做（那一刻手里有坐标）；引爆要靠这个标记。
     */
    @Persisted(key = "sv_flowing")
    private boolean flowingSwirl;

    public boolean isFlowingSwirl() {
        return flowingSwirl;
    }

    /** 把它变成流荡风旋（沃雅妮莎施放战技时转化，或创造时判定遥久之歌覆盖）。 */
    public void markFlowingSwirl() {
        this.flowingSwirl = true;
    }

    // 星璇存活期间所有触发过星扩散的角色（只增不减，用于冰伤统计）
    private final List<PGCharacter> accumulatedContributors = new ArrayList<>();

    // 最后一次星扩散的触发者（作为星扩散的冰段的伤害源）
    private transient PGCharacter lastStellarTriggerCharacter;

    /**
     * 上面那位角色的稳定 id（{@link PGCharacter#getCharacterUUID()}，0 = 未知）。
     *
     * <p>「最后一次让这枚星璇<b>生成 / 升级（合并进来）</b>的星扩散触发者」要落盘 ——
     * {@code @Persisted} 对<b>实体</b>无效，所以只有这个 int 走
     * {@link #addAdditionalSaveData}/{@link #readAdditionalSaveData}，
     * 运行时再把 {@code PGCharacter} 引用按 id 找回来（找不回则退化）。
     */
    private int lastStellarTriggerCharacterUUID;

    // 伤害最高的贡献者（用于视觉效果等）
    private transient PGCharacter lastTopContributor;

    /**
     * 每刻用的牵引节点。位置固定、只有半径会随等级变，所以按半径失效重建 ——
     * 别每刻 new 一个新的（这是每刻都会跑的热路径）。
     */
    private transient GatherPull gatherPull;

    /** {@link #gatherPull} 是按哪个水平半径建的；和当前半径不一致就重建。 */
    private transient float gatherPullRadius = -1f;

    public StellarVortexEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.duration = EXPLODE_TIMER + 10;
        this.shapeType = AreaShapeType.CYLINDER;
        this.horizontalRadius = DEFAULT_HORIZONTAL_RANGE;
        this.verticalRadius = 3.0f;
    }

    public PGCharacter getOwnerCharacter() {
        return lastTopContributor;
    }

    /**
     * 从玩家队伍中查找指定 PGCharacter 所属的 Player
     */
    public Player resolveOwnerPlayer(ServerLevel level, PGCharacter character) {
        if (character == null) return null;
        Player directOwner = character.getData().getOwnerPlayer();
        if (directOwner != null) return directOwner;

        int targetUuid = character.getCharacterUUID();
        for (Player p : level.players()) {
            PlayerCharactersAttachment att = p.getData(
                    AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            for (int i = 0; i < 4; i++) {
                PGCharacter c = att.getPartyCharacter(i);
                if (c != null && c.getCharacterUUID() == targetUuid) {
                    return p;
                }
            }
        }
        return null;
    }

    /**
     * 记下「最后一次让这枚星璇生成 / 升级」的星扩散触发者 —— 它就是星扩散的冰段段的伤害源。
     *
     * <p>生成（新建星璇）和升级（合并进已有星璇）两条路都要调；影响范围之外触发的星扩散
     * 走的是「新建星璇」，不会落到这一枚头上，所以天然不计入。
     */
    public void recordStellarTrigger(PGCharacter triggerCharacter) {
        if (triggerCharacter == null) return;
        this.lastStellarTriggerCharacter = triggerCharacter;
        this.lastStellarTriggerCharacterUUID = triggerCharacter.getCharacterUUID();
    }

    /**
     * 取回冰段伤害源角色：先看运行时引用，引用丢了（实体重载）就按落盘的 id 在玩家队伍里找。
     *
     * @return 找不到时返回 {@code null}（调用方退化到「第一个贡献者」）
     */
    public PGCharacter resolveStellarTriggerCharacter() {
        if (lastStellarTriggerCharacter == null && lastStellarTriggerCharacterUUID != 0
                && this.level() instanceof ServerLevel level) {
            lastStellarTriggerCharacter =
                    findCharacterByUUID(level, lastStellarTriggerCharacterUUID);
        }
        return lastStellarTriggerCharacter;
    }

    /** 在所有玩家的队伍里按 {@code PGCharacter.getCharacterUUID()} 找回角色（找不到返回 null）。 */
    private static PGCharacter findCharacterByUUID(ServerLevel level, int uuid) {
        for (Player p : level.players()) {
            PlayerCharactersAttachment att = p.getData(
                    AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            if (att == null) continue;
            PGCharacter c = att.getCharacterByUUID(uuid);
            if (c != null) return c;
        }
        return null;
    }

    /**
     * 向累积贡献者列表添加角色（用于星扩散的冰段爆炸时统计）
     */
    public void addContributor(PGCharacter character) {
        if (character != null && !accumulatedContributors.contains(character)) {
            accumulatedContributors.add(character);

        }
    }

    public void addAllContributors(Collection<PGCharacter> chars) {
        for (PGCharacter c : chars) {
            addContributor(c);
        }
    }

    public List<PGCharacter> getAccumulatedContributors() {
        return Collections.unmodifiableList(accumulatedContributors);
    }

    public void setLevel(int level) {
        this.vortexLevel = level;
    }

    public int getVortexLevel() {
        return vortexLevel;
    }

    /**
     * 每次星扩散触发时调用，升级并记录最后触发者
     * @param triggerCharacter 本次触发星扩散的角色
     */
    public void incrementLevel(PGCharacter triggerCharacter) {
        recordStellarTrigger(triggerCharacter);
        this.vortexLevel = Math.min(MAX_LEVEL, vortexLevel + 1);
        if (this.vortexLevel >= LEVEL3_THRESHOLD) {
            this.horizontalRadius = LEVEL3_HORIZONTAL_RANGE;
            this.setHorizontalRadius(LEVEL3_HORIZONTAL_RANGE);
        }
        if (this.vortexLevel >= MAX_LEVEL) {
            explode();
        }
        LOGGER.info("[星辉风旋] 等级提升至 {} trigger={} pos={}",
                vortexLevel,
                triggerCharacter != null ? triggerCharacter.getName() : "none",
                this.position());
    }

    private void explode() {
        if (exploded) return;
        exploded = true;

        if (!(this.level() instanceof ServerLevel level)) return;

        // 流荡风旋：引爆时也要降一次附近敌人的风抗（突破天赋 1），
        // 并给「引爆后 5 秒内」这个窗口盖章（沃雅妮莎 A4/2 命的触发条件之一）
        if (flowingSwirl) {
            com.linweiyun.genshin.core.character.catalyst.vodyanitsa.VodyanitsaTalent
                    .shredWindAround(level, this.getX(), this.getY(), this.getZ());
            com.linweiyun.genshin.content.effect.character.vodyanitsa.VodyanitsaSongEffects
                    .markFlowingDetonation(level.getGameTime());
        }

        double iceCoefficient = vortexLevel <= 2
                ? ReactionConfig.STELLAR_SWIRL_ICE_COEFFICIENT_LOW.get()
                : ReactionConfig.STELLAR_SWIRL_ICE_COEFFICIENT_HIGH.get();

        AABB area = getAreaOfInfluence();
        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class, area, e -> e.isAlive() && !e.equals(this));

        List<PGCharacter> contributorList = new ArrayList<>(accumulatedContributors);
        if (contributorList.isEmpty()) {
            LOGGER.warn("[星辉风旋] 爆炸时无贡献者，跳过");
            this.discard();
            return;
        }
        this.lastTopContributor = contributorList.get(0);

        // 星扩散的冰段的伤害源 = 最后一次让这枚星璇生成 / 升级的星扩散触发者；
        // 找不到（离线 / 存档里没有）就退化到「第一个贡献者」。
        PGCharacter iceSource = resolveStellarTriggerCharacter();
        if (iceSource == null) {
            iceSource = contributorList.get(0);
        }
        Player iceSourcePlayer = resolveOwnerPlayer(level, iceSource);
        if (iceSourcePlayer == null) {
            iceSource = contributorList.get(0);
            iceSourcePlayer = resolveOwnerPlayer(level, iceSource);
        }
        if (iceSourcePlayer == null) {
            LOGGER.warn("[星辉风旋] 爆炸时无法解析伤害源玩家，跳过");
            this.discard();
            return;
        }

        for (LivingEntity target : targets) {
            ModDamageSpec spec = buildIceSpec(iceCoefficient, contributorList)
                    .withAttackerCharacter(iceSource);
            spec = spec.withStellarBaseBonusMult(com.linweiyun.genshin.core.system.reaction.StellarGlimmer.swirlBaseBonusMult(level));
            ModDamageSource source = ModDamageSource.from(spec, iceSourcePlayer);
            target.hurtServer(level, source, 0f);

            StatusContainer container = ElementalAttachments.container(target);
            ElementalAttachmentHelper.attach(
                    target, container, ModElements.CYRO.get(),
                    AttachmentSource.SPECIAL,
                    createIceAttachmentProfile(),
                    null, level.getGameTime());
        }


        this.discard();
    }

    /**
     * 触发星扩散的风段的区域伤害
     * @param triggerCharacter 本次触发星扩散的角色（作为伤害源）
     * @param windContributors  本次触发时目标身上的冰+风附着角色（作为贡献者）
     */
    public void triggerWindDamage(PGCharacter triggerCharacter, List<PGCharacter> windContributors) {
        if (!(this.level() instanceof ServerLevel level)) return;

        double windCoefficient = ReactionConfig.STELLAR_SWIRL_WIND_COEFFICIENT.get();

        AABB area = getAreaOfInfluence();
        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class, area, e -> e.isAlive() && !e.equals(this));

        List<PGCharacter> contributorList = new ArrayList<>(windContributors);
        if (contributorList.isEmpty()) {
            LOGGER.warn("[星辉风旋] 风伤时无贡献者，跳过");
            return;
        }

        // 星扩散的风段的伤害源 = 触发者本人
        Player windSourcePlayer = resolveOwnerPlayer(level, triggerCharacter);
        if (windSourcePlayer == null) {
            windSourcePlayer = resolveOwnerPlayer(level, contributorList.get(0));
        }
        if (windSourcePlayer == null) {
            LOGGER.warn("[星辉风旋] 风伤时无法解析伤害源玩家，跳过");
            return;
        }

        for (LivingEntity target : targets) {
            ModDamageSpec spec = buildWindSpec(windCoefficient, contributorList)
                    .withAttackerCharacter(triggerCharacter);
            spec = spec.withStellarBaseBonusMult(com.linweiyun.genshin.core.system.reaction.StellarGlimmer.swirlBaseBonusMult(level));
            ModDamageSource source = ModDamageSource.from(spec, windSourcePlayer);
            target.hurtServer(level, source, 0f);
        }

        this.lastTopContributor = contributorList.isEmpty() ? null : contributorList.get(0);

        LOGGER.info("[星辉风旋] 星扩散的风段伤害 level={} targets={} trigger={} windContributors={}",
                vortexLevel, targets.size(),
                triggerCharacter != null ? triggerCharacter.getName() : "none",
                contributorList.size());
    }

    private static ModDamageSpec buildWindSpec(double coefficient, List<PGCharacter> contributors) {
        return ModDamageSpec.stellarReaction(ModReactionTypes.STELLAR_SWIRL_WIND.get(),
                ModElements.ANEMO.get(), (float) coefficient, 0f, 0f, contributors);
    }

    private static ModDamageSpec buildIceSpec(double coefficient, List<PGCharacter> contributors) {
        return ModDamageSpec.stellarReaction(ModReactionTypes.STELLAR_SWIRL_ICE.get(),
                ModElements.CYRO.get(), (float) coefficient, 0f, 0f, contributors);
    }

    /** 兼容调用点保留的空方法：参数由 {@code ModDamageSpec.stellarReaction(...)} 工厂写入。 */
    private static void setSpecFields(ModDamageSpec spec, float coefficient, float baseBonusMult, float baseBonusFlat) {
        // 空实现：参数由工厂方法写入。
    }

    @Override
    protected void serverTick() {
        super.serverTick();
        if (this.isRemoved() || exploded) return;

        applyGatherPull();

        explodeTimer--;
        if (explodeTimer <= 0) {
            explode();
        }
    }

    /**
     * 每刻把自己范围内的敌人往星璇位置拉一次 —— 存在期间一直在拉，不是只有生成那一瞬。
     *
     * <p>三条规则都落在 {@link GatherPull} 里，这里只负责「提供参数」：
     * <ul>
     *   <li><b>等级 L1</b>：初始削韧 = 1 档韧性条长，原版普通生物一下破韧、随即被拉；</li>
     *   <li><b>速度 1 格 / 1.5 秒</b>；</li>
     *   <li><b>不拉玩家</b>：星璇是己方技能产物，把玩家自己拽过去是纯粹的负体验。</li>
     * </ul>
     * 没被这一笔打破的敌人<b>只会掉韧性条、不会被拉动</b>（未破韧就是不能聚怪）；
     * 打破之后才被按住 AI 拖走；有护盾的敌人整个不生效 —— 这些判据都在
     * {@link GatherPull} 与 {@code ControlService} 里，本类不重复判断。
     */
    private void applyGatherPull() {
        GatherPull pull = gatherPull();
        if (pull != null) {
            pull.execute();
        }
    }

    /** 取本枚星璇的牵引节点（按半径缓存，等级 3 扩大范围时会自动重建一次）。 */
    private GatherPull gatherPull() {
        if (gatherPull != null && gatherPullRadius == this.horizontalRadius) {
            return gatherPull;
        }
        AABB core = AABB.ofSize(this.position(),
                GATHER_CORE_HALF * 2.0, GATHER_CORE_HALF_HEIGHT * 2.0, GATHER_CORE_HALF * 2.0);
        this.gatherPull = new GatherPull(this, GATHER_LEVEL, core, getAreaOfInfluence(),
                GatherPull.perTick(GATHER_BLOCKS_PER_SECOND))
                .withPoisePerSecond(GATHER_POISE_PER_SECOND)
                .withFilter(target -> !(target instanceof Player));
        this.gatherPullRadius = this.horizontalRadius;
        return this.gatherPull;
    }

    public AABB getAreaOfInfluence() {
        double hw = horizontalRadius;
        double hh = verticalRadius;
        return new AABB(
                position().x - hw, position().y - hh, position().z - hw,
                position().x + hw, position().y + hh, position().z + hw);
    }

    public boolean containsPos(double x, double y, double z) {
        double hw = horizontalRadius;
        double hh = verticalRadius;
        return x >= position().x - hw && x <= position().x + hw
                && y >= position().y - hh && y <= position().y + hh
                && z >= position().z - hw && z <= position().z + hw;
    }

    public static StellarVortexEntity findExisting(ServerLevel level, double x, double y, double z, float reuseRange) {
        List<StellarVortexEntity> all = level.getEntitiesOfClass(
                StellarVortexEntity.class,
                new AABB(x - reuseRange, y - reuseRange, z - reuseRange, x + reuseRange, y + reuseRange, z + reuseRange),
                e -> e.isAlive() && !e.exploded);
        for (StellarVortexEntity v : all) {
            double dx = x - v.getX();
            double dy = y - v.getY();
            double dz = z - v.getZ();
            if (dx * dx + dy * dy + dz * dz <= reuseRange * reuseRange) return v;
        }
        return null;
    }

    private static AttachmentProfile createIceAttachmentProfile() {
        return new AttachmentProfile(ICE_ATTACH_QUANTITY, 1.0f,
                ICE_ATTACH_QUANTITY / 9.5f, 9.5f);
    }

    // ==================== 落盘（@Persisted 对实体无效，必须真写 NBT） ====================

    @Override
    public void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        super.readAdditionalSaveData(input);
        this.lastStellarTriggerCharacterUUID = input.getIntOr("mg_stellar_trigger", 0);
        // 引用在这里丢，后面按 id 找回来
        this.lastStellarTriggerCharacter = null;
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("mg_stellar_trigger", this.lastStellarTriggerCharacterUUID);
    }
}
