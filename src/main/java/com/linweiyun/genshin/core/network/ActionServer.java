package com.linweiyun.genshin.core.network;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.system.combat.action.ActionManager;
import com.linweiyun.genshin.core.system.combat.action.InterruptReason;
import com.linweiyun.genshin.core.system.combat.attack.BurstLanding;
import com.linweiyun.genshin.core.system.combat.attack.PlungeState;
import com.linweiyun.genshin.core.system.combat.targeting.CombatTargeting;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacket;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;
import com.lowdragmc.lowdraglib2.syncdata.rpc.RPCSender;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class ActionServer {

    private ActionServer() {}

    @RPCPacket("characterNormalAttackRPCPacket")
    public static void characterNormalAttackRPCPacket(RPCSender sender, int comboStage, int targetEntityId) {
        if (!sender.isServer()) {
            ServerPlayer sp = sender.asPlayer();
            if (sp == null) return;
            PlayerCharactersAttachment attachment =
                    sp.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            PGCharacter character = attachment.getCurrentCharacter();
            if (character != null) {
                lockTarget(sp, targetEntityId);
                ActionManager.get(sp).requestNormalAttack(sp, character, comboStage);
            }
        }
    }

    /**
     * @param target 客户端锁定的目标；服务端据此判断要不要跳过动作自带的位移，可为 null
     */
    public static void performNormalAttackToServer(int comboStage, @Nullable Entity target) {
        RPCPacketDistributor.rpcToServer("characterNormalAttackRPCPacket", comboStage, entityId(target));
    }

    @RPCPacket("characterChargedAttackRPCPacket")
    public static void characterChargedAttackRPCPacket(RPCSender sender, int targetEntityId) {
        if (!sender.isServer()) {
            ServerPlayer sp = sender.asPlayer();
            if (sp == null) return;
            PlayerCharactersAttachment attachment =
                    sp.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            PGCharacter character = attachment.getCurrentCharacter();
            if (character != null) {
                lockTarget(sp, targetEntityId);
                ActionManager.get(sp).requestChargedAttack(sp, character);
            }
        }
    }

    public static void performChargedAttackToServer(@Nullable Entity target) {
        RPCPacketDistributor.rpcToServer("characterChargedAttackRPCPacket", entityId(target));
    }

    @RPCPacket("characterActiveSkillRPCPacket")
    public static void characterActiveSkillRPCPacket(RPCSender sender, int isLong, int targetEntityId) {
        if (!sender.isServer()) {
            ServerPlayer sp = sender.asPlayer();
            if (sp == null) return;
            PlayerCharactersAttachment attachment =
                    sp.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            PGCharacter character = attachment.getCurrentCharacter();
            if (character != null) {
                lockTarget(sp, targetEntityId);
                ActionManager.get(sp).requestElementalSkill(sp, character, isLong);
            }
        }
    }

    public static void triggerCharacterSkill(int isLong, @Nullable Entity target) {
        RPCPacketDistributor.rpcToServer("characterActiveSkillRPCPacket", isLong, entityId(target));
    }

    @RPCPacket("characterActiveBurstRPCPacket")
    public static void characterActiveBurstRPCPacket(RPCSender sender, int targetEntityId) {
        if (!sender.isServer()) {
            ServerPlayer sp = sender.asPlayer();
            if (sp == null) return;
            PlayerCharactersAttachment attachment =
                    sp.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            PGCharacter character = attachment.getCurrentCharacter();
            if (character != null) {
                lockTarget(sp, targetEntityId);
                ActionManager.get(sp).requestElementalBurst(sp, character);
            }
        }
    }

    public static void triggerCharacterBurst(@Nullable Entity target) {
        RPCPacketDistributor.rpcToServer("characterActiveBurstRPCPacket", entityId(target));
    }

    /**
     * 跃起下坠大招的<b>落点</b>：客户端在「开始下坠」那一刻算好并送来。
     *
     * <p>落点一旦送到就不再变 —— 下坠类大招不追踪，服务端的落地伤害打的就是这里。
     * 见 {@code BurstLanding}。
     */
    @RPCPacket("characterBurstLandingRPCPacket")
    public static void characterBurstLandingRPCPacket(RPCSender sender, double x, double y, double z) {
        if (!sender.isServer()) {
            ServerPlayer sp = sender.asPlayer();
            if (sp == null) return;
            BurstLanding.set(sp, new Vec3(x, y, z));
        }
    }

    public static void sendBurstLandingToServer(double x, double y, double z) {
        RPCPacketDistributor.rpcToServer("characterBurstLandingRPCPacket", x, y, z);
    }

    @RPCPacket("characterInterruptRPCPacket")
    public static void characterInterruptRPCPacket(RPCSender sender, int reasonOrdinal) {
        if (!sender.isServer()) {
            ServerPlayer sp = sender.asPlayer();
            if (sp == null) return;
            InterruptReason[] all = InterruptReason.values();
            if (reasonOrdinal < 0 || reasonOrdinal >= all.length) return;
            ActionManager.get(sp).interrupt(all[reasonOrdinal]);
        }
    }

    public static void interruptActionToServer(int reasonOrdinal) {
        RPCPacketDistributor.rpcToServer("characterInterruptRPCPacket", reasonOrdinal);
    }

    @RPCPacket("characterDodgeRPCPacket")
    public static void characterDodgeRPCPacket(RPCSender sender) {
        if (!sender.isServer()) {
            ServerPlayer sp = sender.asPlayer();
            if (sp == null) return;
            PlayerCharactersAttachment attachment =
                    sp.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            PGCharacter character = attachment.getCurrentCharacter();
            if (character != null) {
                ActionManager.get(sp).requestDodge(sp, character);
            }
        }
    }

   public static void triggerCharacterDodge() {
      RPCPacketDistributor.rpcToServer("characterDodgeRPCPacket");
   }

   /**
    * <b>下落攻击开始</b>：客户端在「坠落中按下普攻」的那一刻发过来。
    *
    * <p>落地结算不在这个包里 —— 服务端自己盯 {@code onGround}（见
    * {@code CharacterTickHandler#tickPlungeAttack}）：下坠几刻到不了地是物理说了算，
    * 让客户端报「我落地了」只会多一条能被伪造的包。
    */
   @RPCPacket("characterPlungingAttackRPCPacket")
   public static void characterPlungingAttackRPCPacket(RPCSender sender) {
      if (!sender.isServer()) {
         ServerPlayer sp = sender.asPlayer();
         if (sp == null) return;
         if (!Boolean.TRUE.equals(sp.getData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT))) return;
         PlayerCharactersAttachment attachment =
                 sp.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
         PGCharacter character = attachment.getCurrentCharacter();
         if (character == null || character.getData().getCurrentHP() <= 0.0) return;
         PlungeState.begin(sp, character.getCharacterUUID());
      }
   }

   public static void performPlungingAttackToServer() {
      RPCPacketDistributor.rpcToServer("characterPlungingAttackRPCPacket");
   }

   /**
    * <b>下落攻击中途作废</b>：客户端那边因为落水 / 上梯 / 又飞起来 / 换人而收状态，
    * 但服务端是靠自己的落地检测收的 —— 不告诉它，那条状态会挂到玩家下次落地，
    * 于是「正常落地」也会按下落攻击结算。
    */
   @RPCPacket("characterPlungeCancelRPCPacket")
   public static void characterPlungeCancelRPCPacket(RPCSender sender) {
      if (!sender.isServer()) {
         ServerPlayer sp = sender.asPlayer();
         if (sp == null) return;
         PlungeState.clear(sp);
      }
   }

   public static void cancelPlungingAttackToServer() {
      RPCPacketDistributor.rpcToServer("characterPlungeCancelRPCPacket");
   }

   // ==================== 工具 ====================

    /** 没有目标时传 -1（{@code level().getEntity(-1)} 拿到 null）。 */
    private static int entityId(@Nullable Entity target) {
        return target == null ? -1 : target.getId();
    }

    /**
     * 服务端也记一份锁 —— 召唤物要问「主人正在打谁」，而主人可能已经切到后台。
     *
     * <p>所有招式（普攻 / 重击 / 战技 / 大招）的请求都带目标，所以这条锁永远跟着
     * 客户端最近一次出手走；带 -1 时不覆盖已有目标，只是刷新「最近出手时间」。
     */
    private static void lockTarget(ServerPlayer sp, int targetEntityId) {
        LivingEntity target = sp.level().getEntity(targetEntityId) instanceof LivingEntity living
                ? living
                : null;
        CombatTargeting.lock(sp, target);
    }
}
