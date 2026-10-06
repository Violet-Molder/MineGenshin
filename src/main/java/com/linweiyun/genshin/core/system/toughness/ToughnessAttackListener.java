package com.linweiyun.genshin.core.system.toughness;

import com.linweiyun.elementlib.api.ElibAttackAction;
import com.linweiyun.elementlib.api.ElibAttackOutcome;
import com.linweiyun.elementlib.api.ElibAttackTrigger;
import com.linweiyun.elementlib.core.module.ElibModuleHost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** 方块左键 → 方块韧性。 */
public final class ToughnessAttackListener {

    private ToughnessAttackListener() {
    }

    /** 参与韧性、且这次是左键点方块的，才收进攻击宿主。 */
    public static boolean interested(ElibAttackAction action, ServerLevel level, BlockPos pos, BlockState state) {
        if (!BlockToughnessRules.participates(state, level, pos)) {
            return false;
        }
        return action.trigger() == ElibAttackTrigger.BLOCK_LEFT_CLICK
                || action.trigger() == ElibAttackTrigger.ACTION_DAMAGE_POINT;
    }

    public static void onAttack(ElibAttackOutcome outcome) {
        ElibAttackAction action = outcome.action();
        if (action == null) {
            return;
        }
        if (!(action.attacker() instanceof Player player)
                || !(action.attacker().level() instanceof ServerLevel level)) {
            return;
        }
        if (action.trigger() == ElibAttackTrigger.BLOCK_LEFT_CLICK) {
            BlockPos target = action.targetBlock() != null
                    ? action.targetBlock()
                    : clickedBlock(level, player, action);
            if (target != null) {
                tryHit(outcome, level, target, player, action.poise());
            }
            return;
        }
        if (action.trigger() == ElibAttackTrigger.ACTION_DAMAGE_POINT) {
            for (ElibModuleHost host : outcome.hosts()) {
                if (host.blockPos() != null && host.level() != null) {
                    tryHit(outcome, host.level(), host.blockPos(), player, action.poise());
                }
            }
        }
    }

    /**
     * 韧性按「附着之前」的方块判定：水在冻结前不算硬方块，所以冻出来的浮冰不会被这一下打碎；
     * 本来就存在的浮冰、冰族方块照常参与。
     */
    private static void tryHit(ElibAttackOutcome outcome, ServerLevel level, BlockPos pos,
                               Player player, float poise) {
        BlockState before = stateBefore(outcome, pos);
        if (before != null && !BlockToughnessRules.participates(before, level, pos)) {
            return;
        }
        if (!BlockToughnessRules.participates(level.getBlockState(pos), level, pos)) {
            return;
        }
        BlockToughnessService.hit(level, pos, player, poise);
    }

    @Nullable
    private static BlockState stateBefore(ElibAttackOutcome outcome, BlockPos pos) {
        for (ElibModuleHost host : outcome.hosts()) {
            if (host.blockPos() != null && host.blockPos().equals(pos)) {
                return outcome.blockStateOf(host);
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos clickedBlock(ServerLevel level, Player player, ElibAttackAction action) {
        Vec3 start = player.getEyePosition();
        Vec3 direction = action.direction().lengthSqr() < 1.0E-6
                ? player.getViewVector(1.0f)
                : action.direction().normalize();
        Vec3 end = start.add(direction.scale(Math.max(1.0, action.reach())));
        BlockHitResult hit = level.clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
    }
}
