package com.linweiyun.genshin.core.character.polearm.raiden_shogun.attack;

import com.linweiyun.genshin.config.character.ShenheTalentConfig;
import com.linweiyun.genshin.content.skill_node.AreaEntityCollector;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.elementlib.core.system.about.AttachmentType;
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
 * 雷电将军的<b>普通攻击</b>（5 段近战连击）。
 *
 * <p>倍率来自 {@link ShenheTalentConfig}（目前共享申鹤的配置表）。
 * 第 4 段会双倍结算一次。
 *
 * <p>攻击实施 + 后续效果全部在这里完成。
 */
public final class RaidenShogunNormalAttack {

    private static final float ATTACK_REACH = 2.5f;
    private static final float ATTACK_INFLATE = 1.0f;

    private RaidenShogunNormalAttack() {
    }

    /**
     * 执行一段普攻。
     *
     * @param player      攻击者
     * @param character   攻击者角色
     * @param comboStage  连段序号（1 = 第 1 段，2 = 第 2 段……）
     */
    public static void execute(Player player, PGCharacter character, int comboStage) {
        Level level = player.level();
        if (level.isClientSide()) return;

        int stage = Math.min(Math.max(1, comboStage), 5);
        int naLevel = Math.max(1, character.getData().getNormalAttackLevel());

        float multiplier = (float) (
                ShenheTalentConfig.getNABase(stage)
                        + ShenheTalentConfig.getNAPerLevel(stage) * (naLevel - 1));

        Vec3 startPos = player.position();
        Vec3 lookDir = CombatAim.direction(player);
        Vec3 endPos = startPos.add(lookDir.scale(ATTACK_REACH));
        List<LivingEntity> targets = new AreaEntityCollector(level, startPos, endPos, ATTACK_INFLATE).execute();

        for (LivingEntity target : targets) {
            if (target == player) continue;

            ModDamageSpec spec = ModDamageSpec.builder(AttackType.NORMAL_ATTACK, ModElements.ELECTRO.get())
                    .multiplier(multiplier)
                    .elementAmount(AttachmentType.ULTRA_STRONG.getInitialAmount())
                    .attackerCharacter(character)
                    .build();
            ModDamageSource source = ModDamageSource.from(spec, player);
            if (target.level() instanceof ServerLevel serverLevel) {
                target.hurt(source, 0f);

                // 第 4 段双倍结算
                if (stage == 4) {
                    target.hurt(source, 0f);
                }
            }
        }

        // —— 后续效果 ——
        // 目前无特殊效果
    }
}