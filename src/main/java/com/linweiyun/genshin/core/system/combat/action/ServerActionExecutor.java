package com.linweiyun.genshin.core.system.combat.action;

import com.linweiyun.genshin.core.system.combat.action.data.ActionStep;
import com.linweiyun.genshin.core.system.combat.action.data.Hit;
import com.linweiyun.genshin.core.system.combat.action.data.Move;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import com.linweiyun.genshin.core.system.combat.CombatAim;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端动作执行器 —— 只负责 {@code ActionStep.moves} 的延时位移。
 *
 * <p>延时通过 {@link ServerTickScheduler} 实现，主线程安全。
 *
 * <p><b>伤害不在这里结算</b>：伤害由角色技能负责（{@code ActionDefinition.onActiveStart} →
 * {@code SkillBase.attack / elementalSkill / ...}），{@code ActionStep.hits} 只在
 * {@link ActionState} 里当时间轴用。这里对 hits 只做日志记录，方便排查时序。
 */
public final class ServerActionExecutor {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.COMBAT);

    private ServerActionExecutor() {}

    /**
     * 排入一个 ActionStep 的位移任务。
     * @param player 动作发起玩家
     * @param step   动作步骤配置
     * @param characterId 角色 ID (textureId)，用于日志追踪
     */
    public static void execute(Player player, ActionStep step, String characterId) {
        if (player.level().isClientSide()) return;

        for (Move move : step.moves) {
            if (move.delay > 0) {
                ServerTickScheduler.schedule(move.delay, () -> applyMove(player, move, step.moveAllowsVertical));
            } else {
                applyMove(player, move, step.moveAllowsVertical);
            }
        }

        if (!step.hits.isEmpty()) {
            int total = 0;
            for (Hit hit : step.hits) {
                total += previewHitTargets(player, hit);
            }
        }
    }

    // ==================== 位移 ====================

    /**
     * 给一次位移冲量。
     *
     * <p>方向取<b>角色朝向</b>（{@code yBodyRot}，玩家走同步过来的那份）在水平面上的分量；
     * 竖直方向由 {@code ActionStep.moveAllowsVertical} 决定要不要带视线俯仰。
     */
    private static void applyMove(Entity entity, Move move, boolean allowVertical) {
        if (!entity.isAlive() || entity.isRemoved()) return;

        Vec3 forward = CombatAim.horizontal(entity);
        double y = allowVertical ? entity.getLookAngle().y * move.speed : 0.0;
        entity.setDeltaMovement(entity.getDeltaMovement().add(
                forward.x * move.speed, y, forward.z * move.speed));
        entity.hurtMarked = true;
    }

    // ==================== AOE 伤害检测 ====================

    /**
     * 预览一次伤害点会命中几个目标，只用于日志。
     *
     * <p>真正的伤害由角色天赋结算（那里才有倍率、附着、衰减、反应），
     * 这里算出来的目标列表不能当作结算依据，否则就会变成两套伤害。
     */
    public static int previewHitTargets(Entity source, Hit hit) {
        if (!source.isAlive() || source.isRemoved()) return 0;

        Vec3 look = CombatAim.direction(source);
        Vec3 center = source.position().add(
                look.x * hit.forward,
                hit.yOffset + source.getEyeHeight() * 0.5,
                look.z * hit.forward);
        double r = hit.scope;

        List<LivingEntity> targets = new ArrayList<>();
        for (LivingEntity e : source.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(center.x - r, center.y - r, center.z - r,
                        center.x + r, center.y + r, center.z + r))) {
            if (e == source) continue;
            if (!e.isAlive()) continue;
            targets.add(e);
        }
        return targets.size();
    }
}