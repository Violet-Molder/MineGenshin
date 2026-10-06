package com.linweiyun.genshin.core.system.toughness;

import com.linweiyun.elementlib.core.module.ElibModuleHost;
import com.linweiyun.elementlib.core.module.ElibModuleHosts;
import com.linweiyun.elementlib.core.module.host.BlockModuleHost;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.system.poise.WeaponPoiseTable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** 方块韧性：每次左键交互扣一笔，归零交给破坏处理。 */
public final class BlockToughnessService {

    private static final float FALLBACK_POISE = 10f;

    private BlockToughnessService() {
    }

    public static void hit(ServerLevel level, BlockPos pos, Player player, float poise) {
        BlockState state = level.getBlockState(pos);
        float max = BlockToughnessRules.toughnessOf(state, level, pos);
        if (max <= 0f) {
            return;
        }
        ElibModuleHost host = ElibModuleHosts.of(level, pos);
        if (!(host instanceof BlockModuleHost blockHost)) {
            return;
        }
        BlockToughnessData data = blockHost.transientContainer().ensure(ToughnessTypes.BLOCK_TOUGHNESS);
        int stateId = Block.getId(state);
        if (!data.initialized() || data.stateId() != stateId) {
            data.reset(max, stateId);
        }
        long now = level.getGameTime();
        if (data.lastHitTick() == now) {
            return;
        }
        data.markHit(now);
        data.damage(poise > 0f ? poise : poiseOf(player));
        int stage = Math.clamp((int) (data.consumedRatio() * 10f), 0, 9);
        level.destroyBlockProgress(player.getId(), pos, stage);
        if (data.current() <= 0f) {
            ToughnessBreakHandler.breakBlock(level, pos, player);
        }
    }

    private static float poiseOf(Player player) {
        PlayerCharactersAttachment chars = player.getData(
                AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        PGCharacter character = chars == null ? null : chars.getCurrentCharacter();
        if (character == null) {
            return FALLBACK_POISE;
        }
        WeaponPoiseTable.WeaponClass weapon = WeaponPoiseTable.weaponOf(character);
        return weapon == null ? FALLBACK_POISE : WeaponPoiseTable.normalPoise(weapon);
    }
}
