package com.linweiyun.genshin.content.entities.area;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.elementlib.core.attachment.StatusContainer;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.elementlib.core.system.about.ElementalAttachmentInstance;
import com.linweiyun.genshin.core.system.reaction.builtin.ElectroChargedReaction;
import com.linweiyun.elementlib.api.ElementalReactionType;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.*;
import com.linweiyun.elementlib.core.attachment.ElementalAttachments;
import com.linweiyun.genshin.core.system.registry.register.ModReactionTypes;
import com.linweiyun.genshin.core.attachment.ElementContainerQueries;

public class ThunderCloudEntity extends AreaEntity {
    public static final Logger LOGGER = ModLog.getLogger(LogGroup.CONTENT);
    private static final int TICK_INTERVAL = 40;
    private static final int DEFAULT_DURATION = 121;
    private static final float CONSUME_PER_ATTACK = 0.4f;
    private static final float HORIZONTAL_RANGE = 7.0f;
    private static final float VERTICAL_RANGE = 7.0f;

    // 当前周期的贡献者集合（每个周期只添加不删除，结算后清空）
    private transient Set<PGCharacter> periodContributors = new LinkedHashSet<>();

    // 当前周期最后一次产生水/雷附着的角色（只在没有月感电触发者时当兜底）
    private transient PGCharacter lastDamageSourceChar;

    /**
     * 造成月感电反应的<b>那一下的触发者</b>（最近一次）—— 周期结算的伤害归他。
     *
     * <p>注意口径：<b>不是</b>挂水 / 挂雷的人，也<b>不是</b> dot 自己。
     */
    private transient PGCharacter lastLunarTriggerCharacter;

    /**
     * 上面那位角色的稳定 id（{@link PGCharacter#getCharacterUUID()}，0 = 未知）。
     *
     * <p>{@code @Persisted} 对<b>实体</b>无效，所以只有这个 int 走
     * {@link #addAdditionalSaveData}/{@link #readAdditionalSaveData}，
     * 运行时再把 {@code PGCharacter} 引用按 id 找回来（找不回就退化回旧行为）。
     */
    private int lastLunarTriggerCharacterUUID;

    @Persisted(key = "tc_tick_counter")
    private int tickCounter;

    private final List<Vec3> particlePositions = new ArrayList<>();
    private boolean particlesCalculated;

    public ThunderCloudEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.duration = DEFAULT_DURATION;
        this.shapeType = AreaShapeType.CYLINDER;
        this.horizontalRadius = HORIZONTAL_RANGE;
        this.verticalRadius = VERTICAL_RANGE;
        this.tickCounter = 0;
        this.periodContributors = new LinkedHashSet<>();
    }

    /**
     * 初始形成时注入贡献者（来自目标实体身上的水/雷附着计时器角色）
     */
    public void setInitialContributors(Collection<PGCharacter> contributors) {
        this.periodContributors.clear();
        if (contributors != null) {
            this.periodContributors.addAll(contributors);
        }
        LOGGER.info("[雷暴云] 初始贡献者注入 | count={}", periodContributors.size());
    }

    /**
     * 向当前周期追加贡献者（不重置已有贡献者）
     */
    public void addContributors(Collection<PGCharacter> contributors) {
        if (contributors != null) {
            this.periodContributors.addAll(contributors);
        }
    }

    /**
     * 记下「造成月感电反应的那一下」的触发者（最近一次）——
     * 雷暴云每 2 秒的周期结算就归他，和挂水 / 挂雷的人无关。
     */
    public void recordLunarTrigger(PGCharacter triggerCharacter) {
        if (triggerCharacter == null) return;
        this.lastLunarTriggerCharacter = triggerCharacter;
        this.lastLunarTriggerCharacterUUID = triggerCharacter.getCharacterUUID();
    }

    /**
     * 取回月感电触发者：先看运行时引用，引用丢了（实体重载）就按落盘的 id 在玩家队伍里找。
     *
     * @return 找不到时返回 {@code null}（调用方退化回旧行为）
     */
    public PGCharacter resolveLunarTriggerCharacter() {
        if (lastLunarTriggerCharacter == null && lastLunarTriggerCharacterUUID != 0
                && this.level() instanceof ServerLevel level) {
            lastLunarTriggerCharacter =
                    findCharacterByUUID(level, lastLunarTriggerCharacterUUID);
        }
        return lastLunarTriggerCharacter;
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

    /** 找回某个 PGCharacter 所属的 Player（引用 / 队伍两条路）。 */
    private static Player resolveOwnerPlayer(ServerLevel level, PGCharacter character) {
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

    public void refreshDuration() {
        // 寿命现在记在 AreaEntity.expireGameTime 上（绝对时刻），
        // 不要再写 tickCount = 0 —— 那条路已经被废弃了
        this.duration = DEFAULT_DURATION;
        refreshLifetime();
        LOGGER.info("[雷暴云] 刷新持续时间");
    }

    @Override
    protected void serverTick() {
        super.serverTick();

        if (this.isRemoved()) return;
        if (!(this.level() instanceof ServerLevel level)) return;

        long currentTick = level.getGameTime();
        AABB area = getAreaOfInfluence();
        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class, area, e -> e.isAlive() && !e.equals(this));

        // 每个 tick 扫描范围内所有实体，累积水/雷附着贡献者（只添加不删除）
        for (LivingEntity target : targets) {
            StatusContainer container = ElementalAttachments.container(target);
            if (container == null) continue;

            ElementalAttachmentInstance hydro = ElectroChargedReaction.findElement(container, ModElements.HYDRO.get());
            ElementalAttachmentInstance electro = ElectroChargedReaction.findElement(container, ModElements.ELECTRO.get());
            if (hydro == null || electro == null || hydro.getUnit() <= 0 || electro.getUnit() <= 0) continue;

            ServerLevel serverLevel = this.level() instanceof ServerLevel sl ? sl : null;
            Set<PGCharacter> activeChars = ElementContainerQueries.getActiveContributors(
                    container, serverLevel,
                    currentTick, ModElements.HYDRO.get(), ModElements.ELECTRO.get());
            periodContributors.addAll(activeChars);

            PGCharacter lastAttacher = ElementContainerQueries.getLastAttacher(
                    container, serverLevel, currentTick,
                    ModElements.HYDRO.get(), ModElements.ELECTRO.get());
            if (lastAttacher != null) {
                lastDamageSourceChar = lastAttacher;
            }
        }

        tickCounter++;
        if (tickCounter >= TICK_INTERVAL) {
            tickCounter = 0;

            if (!periodContributors.isEmpty()) {
                // 重新计算范围内实体，因为 entities 可能已变化
                List<LivingEntity> freshTargets = level.getEntitiesOfClass(
                        LivingEntity.class, getAreaOfInfluence(), e -> e.isAlive() && !e.equals(this));
                for (LivingEntity target : freshTargets) {
                    if (!targetHasHydroAndElectro(target)) continue;
                    if (isTargetInOtherCloud(target, level)) continue;
                    dealLunarDamage(target, level);
                }
            }

            // 清空当前周期贡献者，开始新周期
            periodContributors.clear();
            lastDamageSourceChar = null;
        }
    }

    private boolean isTargetInOtherCloud(LivingEntity target, ServerLevel level) {
        List<ThunderCloudEntity> allClouds = level.getEntitiesOfClass(
                ThunderCloudEntity.class,
                new AABB(target.getX() - 16, target.getY() - 16, target.getZ() - 16,
                        target.getX() + 16, target.getY() + 16, target.getZ() + 16),
                e -> e.isAlive() && !e.equals(this));
        for (ThunderCloudEntity other : allClouds) {
            if (other.getAreaOfInfluence().contains(target.getX(), target.getY(), target.getZ())) {
                if (other.tickCount < this.tickCount
                        || (other.tickCount == this.tickCount && other.getId() < this.getId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean targetHasHydroAndElectro(LivingEntity target) {
        StatusContainer container = ElementalAttachments.container(target);
        if (container == null) return false;

        ElementalAttachmentInstance hydro = ElectroChargedReaction.findElement(container, ModElements.HYDRO.get());
        ElementalAttachmentInstance electro = ElectroChargedReaction.findElement(container, ModElements.ELECTRO.get());

        if (hydro == null || electro == null) return false;
        return hydro.getUnit() > 0 && electro.getUnit() > 0;
    }

    public void dealLunarDamage(LivingEntity target, ServerLevel level) {
        StatusContainer container = ElementalAttachments.container(target);
        if (container == null) return;

        ElementalAttachmentInstance hydro = ElectroChargedReaction.findElement(container, ModElements.HYDRO.get());
        ElementalAttachmentInstance electro = ElectroChargedReaction.findElement(container, ModElements.ELECTRO.get());

        float consumedHydro = Math.min(CONSUME_PER_ATTACK, hydro.getUnit());
        float consumedElectro = Math.min(CONSUME_PER_ATTACK, electro.getUnit());
        hydro.consume(consumedHydro);
        electro.consume(consumedElectro);

        List<PGCharacter> contributors = new ArrayList<>(periodContributors);
        if (contributors.isEmpty()) return;

        LivingEntity attacker = resolveAttacker(level, target);

        // 伤害归因 = 造成月感电反应的那一下的触发者（最近一次）
        PGCharacter triggerCharacter = resolveLunarTriggerCharacter();
        ModDamageSpec spec = ModDamageSpec.lunar(ModReactionTypes.LUNAR_CHARGED.get());
        if (triggerCharacter != null) {
            spec = spec.withAttackerCharacter(triggerCharacter);
        }
        // 贡献者列表要在 withAttackerCharacter 之后再填（它返回的是新 spec）
        spec.setLunarContributors(contributors);
        ModDamageSource source = ModDamageSource.from(spec, attacker);
        target.hurtServer(level, source, 0f);

        LOGGER.info("[雷暴云攻击] target={} | contributors={} | damageSource={} | lunarTrigger={}",
                target.getName().getString(), contributors.size(),
                lastDamageSourceChar != null ? lastDamageSourceChar.getName() : "none",
                triggerCharacter != null ? triggerCharacter.getName() : "none");
    }

    private LivingEntity resolveAttacker(ServerLevel level, LivingEntity fallback) {
        // 归因口径：造成月感电反应的那一下的触发者（最近一次）
        PGCharacter trigger = resolveLunarTriggerCharacter();
        if (trigger != null) {
            Player owner = resolveOwnerPlayer(level, trigger);
            if (owner != null) return owner;
        }
        // 回退（旧行为）：最近一次挂水 / 挂雷的角色
        if (lastDamageSourceChar != null) {
            Player owner = resolveOwnerPlayer(level, lastDamageSourceChar);
            if (owner != null) return owner;
        }
        // 再回退到贡献者中任意一个的玩家
        for (PGCharacter ch : periodContributors) {
            Player owner = resolveOwnerPlayer(level, ch);
            if (owner != null) return owner;
        }
        return fallback;
    }

    @Override
    protected void clientTick() {
        if (!particlesCalculated) {
            calculateParticlePositions();
            particlesCalculated = true;
        }
        spawnCachedParticles();
    }

    private void calculateParticlePositions() {
        particlePositions.clear();
        double cx = this.getX();
        double cy = this.getY();
        double cz = this.getZ();
        double r = horizontalRadius;
        double h = verticalRadius;

        int rings = 3;
        int perRing = 12;
        for (int ring = 0; ring < rings; ring++) {
            double ringR = r * (ring + 1) / rings;
            for (int i = 0; i < perRing; i++) {
                double angle = 2 * Math.PI * i / perRing;
                double x = cx + ringR * Math.cos(angle);
                double z = cz + ringR * Math.sin(angle);
                particlePositions.add(new Vec3(x, cy, z));
            }
        }

        int lineSteps = 8;
        for (int yStep = 0; yStep < lineSteps; yStep++) {
            double dy = -h + 2 * h * yStep / (lineSteps - 1);
            for (int i = 0; i < 4; i++) {
                double angle = 2 * Math.PI * i / 4;
                double x = cx + r * Math.cos(angle);
                double z = cz + r * Math.sin(angle);
                particlePositions.add(new Vec3(x, cy + dy, z));
            }
        }

        int innerCount = 20;
        for (int i = 0; i < innerCount; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = random.nextDouble() * r;
            double x = cx + dist * Math.cos(angle);
            double z = cz + dist * Math.sin(angle);
            double dy = -h + random.nextDouble() * h;
            particlePositions.add(new Vec3(x, cy + dy, z));
        }
    }

    private void spawnCachedParticles() {
        for (Vec3 pos : particlePositions) {
            this.level().addParticle(ParticleTypes.CLOUD,
                    pos.x + (random.nextFloat() - 0.5f) * 0.3f,
                    pos.y + (random.nextFloat() - 0.5f) * 0.3f,
                    pos.z + (random.nextFloat() - 0.5f) * 0.3f,
                    0, 0, 0);
        }
    }

    public AABB getAreaOfInfluence() {
        double hw = horizontalRadius;
        double hh = verticalRadius;
        return new AABB(
                position().x - hw, position().y - hh, position().z - hw,
                position().x + hw, position().y, position().z + hw);
    }

    // ==================== 落盘（@Persisted 对实体无效，必须真写 NBT） ====================

    @Override
    public void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        super.readAdditionalSaveData(input);
        this.lastLunarTriggerCharacterUUID = input.getIntOr("mg_lunar_trigger", 0);
        // 引用在这里丢，后面按 id 找回来
        this.lastLunarTriggerCharacter = null;
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("mg_lunar_trigger", this.lastLunarTriggerCharacterUUID);
    }
}
