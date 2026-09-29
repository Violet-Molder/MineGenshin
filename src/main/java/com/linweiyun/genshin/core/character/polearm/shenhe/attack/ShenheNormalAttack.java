package com.linweiyun.genshin.core.character.polearm.shenhe.attack;

import com.linweiyun.genshin.config.character.ShenheTalentConfig;
import com.linweiyun.genshin.content.skill_node.AreaEntityCollector;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.about.AttachmentType;
import com.linweiyun.genshin.core.system.combat.CombatAim;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 申鹤的<b>普通攻击</b>（3 段近战连击）。
 *
 * <p>倍率来自 {@link ShenheTalentConfig}，支持等级成长。
 * 收招段（第 3 段）会双倍结算一次。
 *
 * <p>攻击实施 + 后续效果（目前无特殊天赋效果）全部在这里完成。
 */
public final class ShenheNormalAttack {

    /** 近战判定：面前 2.5 格、半径 1.0。 */
    private static final float ATTACK_REACH = 2.5f;
    private static final float ATTACK_INFLATE = 1.0f;

    private ShenheNormalAttack() {
    }

    /**
     * 执行一段普攻。
     *
     * @param player      攻击者
     * @param character   攻击者角色
     * @param comboStage  连段序号（1 = 第 1 段，2 = 第 2 段，3 = 第 3 段）
     */
    public static void execute(Player player, PGCharacter character, int comboStage) {
        Level level = player.level();
        if (level.isClientSide()) return;

        int stage = comboStage;
        int naLevel = Math.max(1, character.getData().getNormalAttackLevel());

        float multiplier = (float) (
                ShenheTalentConfig.getNABase(stage)
                        + ShenheTalentConfig.getNAPerLevel(stage) * (naLevel - 1));

        // 近战扇形判定
        Vec3 startPos = player.position();
        Vec3 lookDir = CombatAim.direction(player);
        Vec3 endPos = startPos.add(lookDir.scale(ATTACK_REACH));

        List<LivingEntity> targets = new AreaEntityCollector(level, startPos, endPos, ATTACK_INFLATE).execute();

        for (LivingEntity target : targets) {
            if (target == player) continue;

            // 伤害结算
            ModDamageSpec spec = ModDamageSpec.builder(AttackType.NORMAL_ATTACK, ModElements.CYRO.get())
                    .multiplier(multiplier)
                    .elementAmount(AttachmentType.WEAK.getInitialAmount())
                    .attackerCharacter(character)
                    .build();
            ModDamageSource source = ModDamageSource.from(spec, player);
            if (target.level() instanceof ServerLevel serverLevel) {
                target.hurtServer(serverLevel, source, 0f);

                // 收招段双倍结算
                if (stage == 3) {
                    target.hurtServer(serverLevel, source, 0f);
                }
            }
        }

        // —— 后续效果 ——
        // 目前申鹤普攻没有特殊的天赋/命座触发效果
        // 以后如果有（如特殊能量回复、标记积累等）写在这里
    }
}