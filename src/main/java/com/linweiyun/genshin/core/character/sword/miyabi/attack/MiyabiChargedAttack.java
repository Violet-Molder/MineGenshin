package com.linweiyun.genshin.core.character.sword.miyabi.attack;

import com.linweiyun.genshin.content.entities.teyvat.skill.miyabi.MiyabiSlashEffect;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.sword.miyabi.Miyabi;
import com.linweiyun.genshin.core.character.sword.miyabi.MiyabiTalent;
import com.linweiyun.genshin.core.character.sword.miyabi.MiyabiResources;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.genshin.core.system.combat.targeting.CombatTargeting;
import com.linweiyun.genshin.core.system.registry.register.ModReactionTypes;
import com.linweiyun.elementlib.core.system.combat.decay.DecayGroups;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 星见雅的重击（能量不满的那一套，{@code heavy_1}）。
 *
 * <p>这一招的伤害<b>由剑气自己打</b>（素材侧也是这么做的）：动作表里那一格只负责在
 * 第 {@code CHARGED_HIT_TICK} 刻把三道剑气放出去，打到谁、打几下由剑气一路上的碰撞决定
 * —— 见 {@link MiyabiSlashEffect}。
 */
public final class MiyabiChargedAttack {

    /** 斩出去的那三道：正前一道 + 左右各偏 0.6。 */
    private static final double[] SLASH_SPREADS = {0.0, 0.6, -0.6};

    private MiyabiChargedAttack() {
    }

    /**
     * 把这一刀的剑气放出去 —— 伤害全程由它们按碰撞结算。
     *
     * <p>发射前先索敌：拿到目标就<b>只把朝向拧过去</b>（不追击、不位移），剑气按角色朝向飞。
     */
    public static void execute(Player player, PGCharacter character) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(character instanceof Miyabi miyabi)) {
            return;
        }

        LivingEntity target = CombatTargeting.current(player);
        if (target == null) {
            target = CombatTargeting.acquire(player,
                    CombatTargeting.Params.forChase(MiyabiResources.ATTACK_RANGE));
        }
        if (target != null) {
            faceWithoutMoving(player, target);
        }

        int naLevel = Math.max(1, character.getData().getNormalAttackLevel());

        if (miyabi.isSnowState()) {
            MiyabiDamage.aroundStellar(player, character, ModReactionTypes.STELLAR_CONDUCE.get(),
                    ModElements.CYRO.get(), com.linweiyun.genshin.core.system.combat.decay.ModDecayGroups.CHARGED_ATTACK,
                    player.position(), 4.0, MiyabiTalent.frostMoon(naLevel));
            float qi = MiyabiTalent.swordQi(naLevel) * (1f + MiyabiTalent.CONDUCE_MULTIPLIER_BONUS);
            for (int i = 0; i < 8; i++) {
                double angle = Math.toRadians(45.0 * i);
                MiyabiSlashEffect.spawnDirection(level, player, Math.cos(angle), Math.sin(angle), qi, true);
            }
            return;
        }

        boolean conduce = miyabi.isConduce();
        MiyabiDamage.forward(player, character, AttackType.CHARGED_ATTACK,
                conduce ? ModElements.CYRO.get() : ModElements.FYSIKOS.get(),
                com.linweiyun.genshin.core.system.combat.decay.ModDecayGroups.CHARGED_ATTACK, MiyabiResources.ATTACK_RANGE, 1.0f, 1.5f,
                MiyabiTalent.chargedAttackMultiplier(naLevel));

        float qi = MiyabiTalent.swordQi(naLevel) * (conduce ? 1f + MiyabiTalent.CONDUCE_MULTIPLIER_BONUS : 1f);
        for (double spread : SLASH_SPREADS) {
            MiyabiSlashEffect.spawn(level, player, spread, qi, conduce);
        }
        if (conduce) {
            miyabi.addFrostStack();
        }
    }

    /** 只改朝向（身体 + 头），不产生任何位移。 */
    private static void faceWithoutMoving(Player player, LivingEntity target) {
        float yaw = (float) (Mth.atan2(target.getZ() - player.getZ(), target.getX() - player.getX())
                * (180.0 / Math.PI)) - 90.0f;
        player.setYRot(yaw);
        player.yRotO = yaw;
        player.setYHeadRot(yaw);
    }
}
