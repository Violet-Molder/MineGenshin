package com.linweiyun.genshin.core.character.util.handler;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.util.CharacterHelper;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.linweiyun.genshin.core.system.combat.attack.PlungeState;
import com.linweiyun.genshin.core.system.combat.flight.GenshinFlight;
import com.linweiyun.genshin.core.system.combat.targeting.CombatTargeting;
import com.linweiyun.genshin.core.world.TeyvatWorldInvasion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber
public class CharacterTickHandler {
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();

        // 顺序敏感：四步必须按此顺序执行，因此留在同一个监听方法里依次调用，
        // 而不是拆成多个 @SubscribeEvent —— 跨类监听的执行顺序不确定。
        tickTargetingLock(player);
        // 飞行维护（原神模式给 / 收创造飞行、按倍率扣鞘翅耐久）不分入侵与否都要跑：
        // 入侵结束时原神模式会被强制关掉，这一份也得跟着收飞行能力
        GenshinFlight.tick(player);
        if (isNotInvadedOnServer(player)) {
            // 非入侵态角色系统整体停摆：下落攻击状态也不能留着 —— 留着就是「输入永远被拦」
            PlungeState.end(player);
            return;
        }
        PlayerCharactersAttachment attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        tickPlungeAttack(player, attachment);
        com.linweiyun.genshin.content.entities.area.StellarPrismEntity.tickPlayerField(player);
        tickPartyCharacters(player, attachment);
        checkAllCharactersDown(player, attachment);
        syncDirtyCharacters(player, attachment);
    }

    /**
     * <b>下落攻击的落地结算</b>——服务端自己盯 {@code onGround}，不信客户端报的「我落地了」。
     *
     * <p>怎么算这一下由角色的技能决定（{@link PGCharacter#performPlungingAttack} →
     * {@code SkillBase.plungingAttack}，基类就是「身周一圈下落攻击伤害」）。
     *
     * <p>摔落伤害那一份不在这里：它由 {@code Player#causeFallDamage} 的拦截按
     * 「下落攻击曲线」算（38 格以内免伤），两边靠 {@link PlungeState} 的
     * 「刚落地」记录对齐 —— 落地那一格谁先跑都按同一条曲线。
     */
    private static void tickPlungeAttack(Player player, PlayerCharactersAttachment attachment) {
        if (player.level().isClientSide() || !PlungeState.isPlunging(player)) return;

        PGCharacter character = attachment.getCurrentCharacter();
        // 人没了 / 倒了 / 退出原神模式 → 这个状态作废（客户端那边同样会取消）
        if (character == null || character.getData().getCurrentHP() <= 0.0
                || !Boolean.TRUE.equals(player.getData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT))) {
            PlungeState.end(player);
            return;
        }
        // 已经换成别的角色了（例如摔伤把起手那个角色打倒、系统自动换人）：
        // 这一下不归新人，直接作废，别让下一个角色替她砸
        if (PlungeState.characterUuid(player) != character.getCharacterUUID()) {
            PlungeState.end(player);
            return;
        }

        if (player.onGround()) {
            // 砸到地面：结算这一下，状态结束
            character.performPlungingAttack(player);
            // 下落攻击落地：把元素留给落点周围的环境（没砸到实体时也要附着）
            if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                com.linweiyun.genshin.core.system.combat.attack.DamageBlockAttack.onElementalDamage(
                        serverLevel, player, player, character.getElemental(),
                        com.linweiyun.elementlib.core.system.about.AttachmentProfile.WEAK,
                        com.linweiyun.elementlib.core.system.about.AttachmentProfile.WEAK.getBaseQuantity());
            }
            PlungeState.end(player);
        } else if (player.getAbilities().flying) {
            // 创造飞行会自己回来：这里把它按掉继续下劈，不能当成「打空」把这一下作废
            player.getAbilities().flying = false;
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                serverPlayer.onUpdateAbilities();
            }
        } else if (player.isInWater() || player.onClimbable()
                || player.isSpectator()) {
            // 没砸到地面（水里 / 梯子 / 开飞）：这一下打空，伤害不放
            PlungeState.end(player);
        } else if (player.isFallFlying()) {
            // 鞘翅姿态自己回来了：按掉继续下劈，不作废这一下
            player.stopFallFlying();
        } else if (PlungeState.elapsedTicks(player) > PlungeState.MAX_TICKS) {
            // 兜底：怎么都不落地（蛛网 / 卡住 / 包丢了）就按打空结束，别把输入锁死
            PlungeState.end(player);
        }
    }

    /**
     * 索敌锁：服务端每刻校验。
     *
     * <p>客户端那份由客户端状态机驱动；服务端这份是「攻击请求包喂进来」的，
     * 没有人校验它就会**永远不松**——于是动作自带的位移从此不再执行（两端行为分叉）。
     */
    private static void tickTargetingLock(Player player) {
        if (!player.level().isClientSide()) {
            CombatTargeting.tick(player);
        }
    }

    /** 服务端且未入侵 → 跳过后续全部角色推进。 */
    private static boolean isNotInvadedOnServer(Player player) {
        return !player.level().isClientSide() && player.level() instanceof ServerLevel sl
                && !TeyvatWorldInvasion.get(sl).isInvaded();
    }

    /** 推进队伍里每个角色的状态。 */
    private static void tickPartyCharacters(Player player, PlayerCharactersAttachment attachment) {
        if (!player.level().isClientSide()) {
            for (int uuid : attachment.getPartyCharacterUUIDs()) {
                PGCharacter character = CharacterHelper.getCharacterByUUID(player, uuid);
                if (character != null) {
                    character.tick(player, character == attachment.getCurrentCharacter());
                }
            }
        }
    }

    /** 全队倒下时关闭原神模式并同步给客户端。 */
    private static void checkAllCharactersDown(Player player, PlayerCharactersAttachment attachment) {
        if (!player.level().isClientSide()) {
            Boolean genshinMode = player.getData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT);
            if (Boolean.TRUE.equals(genshinMode)) {
                boolean allDead = true;
                for (int i = 0; i < 4; i++) {
                    PGCharacter pc = attachment.getPartyCharacter(i);
                    if (pc != null && pc.getData().getCurrentHP() > 0) {
                        allDead = false;
                        break;
                    }
                }
                if (allDead) {
                    player.setData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT, false);
                    if (player instanceof ServerPlayer sp) {
                        NetworkManager.setGenshinModeToPlayer(sp, false);
                    }
                    // 全灭被动退出：这里也要把角色属性折算回玩家身上
                    com.linweiyun.genshin.core.system.compat.PlayerStatBridge
                            .onGenshinModeChanged(player, false);
                }
            }
        }
    }

    /** 脏标记驱动的角色数据整包同步。 */
    private static void syncDirtyCharacters(Player player, PlayerCharactersAttachment attachment) {
        for (PGCharacter character : attachment.getOwnedCharacters()) {
            if (character.getData().isDirty()) {
                character.getData().clearDirty();
                if (player instanceof ServerPlayer serverPlayer) {
                    attachment.syncSingleCharacterToPlayer(serverPlayer, character);
                }

            }
        }
    }
}
