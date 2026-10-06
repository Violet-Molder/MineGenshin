package com.linweiyun.genshin.core.character.polearm.arlecchino.attack;

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
 * 阿蕾奇诺的<b>普通攻击（普攻连段）</b>。
 *
 * <p>长柄 5 段，面前 2.5 格 × 半径 1.0 的近战扇形判定。
 * 伤害实施与后续效果（目前还没有天赋/命座需要在这里触发）全部集中在这一处。
 */
public final class ArlecchinoNormalAttack {

    /** 连招倍率，按段从 1 基排列（下标 0 = 第 1 段）。 */
    private static final float[] COMBO_MULTIPLIERS = {0.41f, 0.42f, 0.55f, 0.35f, 0.68f};

    /** 近战判定：面前 2.5 格、半径 1.0。 */
    private static final float ATTACK_REACH = 2.5f;
    private static final float ATTACK_INFLATE = 1.0f;

    private ArlecchinoNormalAttack() {
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

        float multiplier = resolveMultiplier(comboStage);

        // 近战扇形判定
        Vec3 startPos = player.position();
        Vec3 endPos = startPos.add(CombatAim.direction(player).scale(ATTACK_REACH));
        List<LivingEntity> targets = new AreaEntityCollector(level, startPos, endPos, ATTACK_INFLATE).execute();

        for (LivingEntity target : targets) {
            if (target == player) continue;

            ModDamageSpec spec = ModDamageSpec.builder(AttackType.NORMAL_ATTACK, ModElements.PYRO.get())
                    .multiplier(multiplier)
                    .elementAmount(AttachmentType.WEAK.getInitialAmount())
                    .attackerCharacter(character)
                    .build();
            ModDamageSource source = ModDamageSource.from(spec, player);
            if (target.level() instanceof ServerLevel serverLevel) {
                target.hurtServer(serverLevel, source, 0f);
            }
        }

        // —— 后续效果 ——
        // 目前阿蕾奇诺还没有天赋/命座需要在这里触发
        // 以后如果有：能量回复、触发物、特殊Buff等全部写在这里
    }

    /**
     * 根据连段序号查倍率。
     * comboStage 是 1 基（1 = 第 1 段），COMBO_MULTIPLIERS 是 0 基。
     */
    private static float resolveMultiplier(int comboStage) {
        int index = comboStage - 1;
        if (index >= 0 && index < COMBO_MULTIPLIERS.length) {
            return COMBO_MULTIPLIERS[index];
        }
        return 1.0f;
    }
}