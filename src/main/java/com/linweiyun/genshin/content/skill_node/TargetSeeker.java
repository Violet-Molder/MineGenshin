package com.linweiyun.genshin.content.skill_node;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.LockedTargetData;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.List;

public class TargetSeeker {

    public enum SourceType {
        CHARACTER,
        ALLY_SUMMON_FREE,
        ALLY_SUMMON_COOP,
        TRACKING_FREE,
        TRACKING_COOP
    }

    public enum TargetingType {
        LINE_OF_SIGHT,
        RADIUS
    }

    public enum TargetFilter {
        ALL,
        NON_PLAYER,
        HOSTILE_ONLY
    }

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CONTENT);

    private static final double TUNNEL_HALF_SIZE = 2.5;
    private static final double PRIORITY_THRESHOLD = 0.5;
    private static final double RANGE_SQ_EPSILON = 4.0;
    private static final int LOCK_DURATION_TICKS = 80;

    private final Entity source;
    private final double range;
    private final TargetingType targetingType;
    private final SourceType sourceType;
    private final PGCharacter associatedCharacter;
    private final TargetFilter targetFilter;

    /**
     * 搜索中心。
     * <ul>
     *   <li>null（默认）→ 以 {@link #source} 当前位置为中心</li>
     *   <li>非 null → 以该世界坐标为圆心搜索（用于投射物"索敌范围不跟随移动"的场景）</li>
     * </ul>
     */
    private final Vec3 searchCenter;

    // ==================== 构造 ====================

    public TargetSeeker(Entity source, double range, TargetingType targetingType,
                        SourceType sourceType, PGCharacter associatedChar, TargetFilter targetFilter,
                        Vec3 searchCenter) {
        this.source = source;
        this.range = range;
        this.targetingType = targetingType;
        this.sourceType = sourceType;
        this.associatedCharacter = associatedChar;
        this.targetFilter = targetFilter;
        this.searchCenter = searchCenter;
    }

    public TargetSeeker(Entity source, double range, TargetingType targetingType,
                        SourceType sourceType, PGCharacter associatedChar, TargetFilter targetFilter) {
        this(source, range, targetingType, sourceType, associatedChar, targetFilter, null);
    }

    public TargetSeeker(Entity source, double range, TargetingType targetingType,
                        SourceType sourceType, PGCharacter associatedChar) {
        this(source, range, targetingType, sourceType, associatedChar, TargetFilter.ALL);
    }

    public TargetSeeker(Entity source, double range, TargetingType type) {
        this(source, range, type, SourceType.CHARACTER, null);
    }

    // ==================== 主流程 ====================

    public LivingEntity execute() {
        if (source == null || range <= 0) return null;

        Level level = source.level();
        long tick = level.getGameTime();

        return switch (sourceType) {
            case CHARACTER -> executeCharacter(level, tick);
            case ALLY_SUMMON_FREE -> executeAllySummonFree(level, tick);
            case ALLY_SUMMON_COOP -> executeAllySummonCoop(level, tick);
            case TRACKING_FREE -> executeTrackingFree(level);
            case TRACKING_COOP -> executeTrackingCoop(level);
        };
    }

    private LivingEntity executeCharacter(Level level, long tick) {
        LockedTargetData lockData = source.getData(AttachmentRegistration.LOCKED_TARGET);
        if (lockData.isValid(tick)) {
            Entity locked = level.getEntity(lockData.targetId());
            if (locked instanceof LivingEntity livingLocked
                    && locked.isAlive()
                    && source.distanceTo(livingLocked) <= range
                    && hasVisionOf(livingLocked)) {

                if (livingLocked instanceof Monster) {
                    refreshLock(tick, livingLocked);
                    return livingLocked;
                }

                LivingEntity closerHostile = findClosestHostileInRange();
                if (closerHostile != null && source.distanceToSqr(closerHostile) < source.distanceToSqr(livingLocked)) {
                    source.setData(AttachmentRegistration.LOCKED_TARGET,
                            new LockedTargetData(closerHostile.getUUID(), tick + LOCK_DURATION_TICKS));
                    return closerHostile;
                }
                refreshLock(tick, livingLocked);
                return livingLocked;
            }
            source.setData(AttachmentRegistration.LOCKED_TARGET, LockedTargetData.EMPTY);
        }

        List<LivingEntity> candidates = switch (targetingType) {
            case LINE_OF_SIGHT -> collectLineOfSight();
            case RADIUS -> collectRadius();
        };
        if (candidates.isEmpty()) return null;

        LivingEntity target = selectBestTarget(candidates);
        if (target != null) {
            source.setData(AttachmentRegistration.LOCKED_TARGET,
                    new LockedTargetData(target.getUUID(), tick + LOCK_DURATION_TICKS));
        }
        return target;
    }

    private LivingEntity executeAllySummonFree(Level level, long tick) {
        LockedTargetData lockData = source.getData(AttachmentRegistration.LOCKED_TARGET);
        if (lockData.isValid(tick)) {
            Entity locked = level.getEntity(lockData.targetId());
            if (locked instanceof LivingEntity livingLocked
                    && locked.isAlive()
                    && source.distanceTo(livingLocked) <= range) {
                refreshLock(tick, livingLocked);
                return livingLocked;
            }
            source.setData(AttachmentRegistration.LOCKED_TARGET, LockedTargetData.EMPTY);
        }

        List<LivingEntity> candidates = collectRadius();
        if (candidates.isEmpty()) return null;

        LivingEntity target = selectBestTarget(candidates);
        if (target != null) {
            source.setData(AttachmentRegistration.LOCKED_TARGET,
                    new LockedTargetData(target.getUUID(), tick + LOCK_DURATION_TICKS));
        }
        return target;
    }

    private LivingEntity executeAllySummonCoop(Level level, long tick) {
        LivingEntity charTarget = getCharacterLockedTarget(level, tick);
        if (charTarget != null && charTarget.isAlive() && source.distanceTo(charTarget) <= range) {
            source.setData(AttachmentRegistration.LOCKED_TARGET,
                    new LockedTargetData(charTarget.getUUID(), tick + LOCK_DURATION_TICKS));
            return charTarget;
        }
        return executeAllySummonFree(level, tick);
    }

    private LivingEntity executeTrackingFree(Level level) {
        List<LivingEntity> candidates = collectRadius();
        if (candidates.isEmpty()) return null;
        return selectBestTarget(candidates);
    }

    private LivingEntity executeTrackingCoop(Level level) {
        long tick = level.getGameTime();

        LivingEntity charTarget = getCharacterLockedTarget(level, tick);
        if (charTarget != null && charTarget.isAlive()) {
            return charTarget;
        }
        return executeTrackingFree(level);
    }

    private LivingEntity getCharacterLockedTarget(Level level, long tick) {
        if (associatedCharacter == null) return null;

        Player ownerPlayer = associatedCharacter.getData().getOwnerPlayer();
        if (ownerPlayer == null) return null;

        LockedTargetData lockData = ownerPlayer.getData(AttachmentRegistration.LOCKED_TARGET);
        if (!lockData.isValid(tick)) return null;

        Entity locked = level.getEntity(lockData.targetId());
        if (locked instanceof LivingEntity livingLocked && livingLocked.isAlive()) {
            return livingLocked;
        }
        return null;
    }

    // ==================== 锁定 / 刷新 ====================

    private boolean hasVisionOf(Entity target) {
        if (source instanceof LivingEntity livingSource) {
            if (!livingSource.hasLineOfSight(target)) return false;
            Vec3 lookDir = source.getLookAngle().normalize();
            Vec3 toTarget = target.getBoundingBox().getCenter()
                    .subtract(source.getEyePosition()).normalize();
            return toTarget.dot(lookDir) > 0.3;
        }
        return true;
    }

    private void refreshLock(long tick, LivingEntity target) {
        source.setData(AttachmentRegistration.LOCKED_TARGET,
                new LockedTargetData(target.getUUID(), tick + LOCK_DURATION_TICKS));
    }

    public static void clearLock(Entity source) {
        source.setData(AttachmentRegistration.LOCKED_TARGET, LockedTargetData.EMPTY);
    }

    // ==================== 中心点 ====================

    private Vec3 getCenter() {
        return (searchCenter != null) ? searchCenter : source.position();
    }

    // ==================== 候选收集 ====================

    private List<LivingEntity> collectLineOfSight() {
        Vec3 eyePos = source.getEyePosition();
        Vec3 lookDir = source.getLookAngle().normalize();
        Vec3 endPos = eyePos.add(lookDir.scale(range));

        AABB searchBox = new AABB(eyePos, endPos).inflate(TUNNEL_HALF_SIZE);

        Player ownerPlayer = (associatedCharacter != null) ? associatedCharacter.getData().getOwnerPlayer() : null;

        return source.level().getEntitiesOfClass(LivingEntity.class, searchBox, entity ->
                entity.isAlive()
                        && !entity.isSpectator()
                        && entity != source
                        && entity != ownerPlayer
                        && matchesFilter(entity)
                        && isInForwardTunnel(entity)
        );
    }

    private boolean isInForwardTunnel(Entity target) {
        Vec3 eyePos = source.getEyePosition();
        Vec3 lookDir = source.getLookAngle().normalize();
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        Vec3 toTarget = targetCenter.subtract(eyePos);

        double forwardDist = toTarget.dot(lookDir);
        if (forwardDist < 0 || forwardDist > range) return false;

        Vec3 projected = eyePos.add(lookDir.scale(forwardDist));
        Vec3 perpendicular = targetCenter.subtract(projected);

        return perpendicular.lengthSqr() <= TUNNEL_HALF_SIZE * TUNNEL_HALF_SIZE;
    }

    private List<LivingEntity> collectRadius() {
        Vec3 center = getCenter();
        double rangeSq = range * range + RANGE_SQ_EPSILON;

        AABB searchBox = new AABB(
                center.x - range, center.y - range, center.z - range,
                center.x + range, center.y + range, center.z + range);

        Player ownerPlayer = (associatedCharacter != null) ? associatedCharacter.getData().getOwnerPlayer() : null;

        return source.level().getEntitiesOfClass(LivingEntity.class, searchBox, entity ->
                entity.isAlive()
                        && !entity.isSpectator()
                        && entity != source
                        && entity != ownerPlayer
                        && matchesFilter(entity)
                        && entity.position().distanceToSqr(center) <= rangeSq
        );
    }

    private boolean matchesFilter(LivingEntity entity) {
        return switch (targetFilter) {
            case ALL -> true;
            case NON_PLAYER -> !(entity instanceof Player);
            case HOSTILE_ONLY -> entity instanceof Monster;
        };
    }

    private LivingEntity findClosestHostileInRange() {
        Vec3 center = getCenter();
        double rangeSq = range * range + RANGE_SQ_EPSILON;

        AABB searchBox = new AABB(
                center.x - range, center.y - range, center.z - range,
                center.x + range, center.y + range, center.z + range);

        return source.level().getEntitiesOfClass(LivingEntity.class, searchBox, entity ->
                        entity instanceof Monster
                                && entity.isAlive()
                                && !entity.isSpectator()
                                && entity != source
                                && entity.position().distanceToSqr(center) <= rangeSq)
                .stream()
                .min(Comparator.comparingDouble(e -> e.position().distanceToSqr(center)))
                .orElse(null);
    }

    // ==================== 优先级排序 ====================

    private LivingEntity selectBestTarget(List<LivingEntity> candidates) {
        Vec3 center = getCenter();

        List<LivingEntity> hostile = candidates.stream()
                .filter(e -> e instanceof Monster)
                .sorted(Comparator.comparingDouble(e -> e.position().distanceToSqr(center)))
                .toList();
        List<LivingEntity> friendly = candidates.stream()
                .filter(e -> !(e instanceof Monster))
                .sorted(Comparator.comparingDouble(e -> e.position().distanceToSqr(center)))
                .toList();

        if (targetFilter == TargetFilter.HOSTILE_ONLY) {
            return hostile.isEmpty() ? null : hostile.get(0);
        }

        if (hostile.isEmpty() && friendly.isEmpty()) return null;
        if (hostile.isEmpty()) return friendly.get(0);
        if (friendly.isEmpty()) return hostile.get(0);

        double threshold = range * PRIORITY_THRESHOLD;
        double closestHostileDist = hostile.get(0).position().distanceTo(center);
        double closestFriendlyDist = friendly.get(0).position().distanceTo(center);

        if (closestFriendlyDist <= threshold && closestHostileDist > range - threshold) {
            return friendly.get(0);
        }
        return hostile.get(0);
    }
}