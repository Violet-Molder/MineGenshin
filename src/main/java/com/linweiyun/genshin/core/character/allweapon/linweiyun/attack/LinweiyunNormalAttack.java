package com.linweiyun.genshin.core.character.allweapon.linweiyun.attack;

import com.linweiyun.genshin.config.character.LinweiyunTalentConfig;
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
 * 林薇云（<b>所有形态</b>）的普攻。
 *
 * <p>面朝方向 2.5 格扇形判定，收招段（第 3 段）双倍结算。
 * 风元素，弱附着，走普通攻击衰减组。
 */
public final class LinweiyunNormalAttack {

    private static final float REACH = 2.5f;
    private static final float INFLATE = 1.0f;

    private LinweiyunNormalAttack() {
    }

    public static void execute(Player player, PGCharacter character, int comboStage) {
        Level level = player.level();
        if (level.isClientSide()) return;

        int naLevel = Math.max(1, character.getData().getNormalAttackLevel());
        float multiplier = (float) (
                LinweiyunTalentConfig.getNABase(comboStage)
                        + LinweiyunTalentConfig.getNAPerLevel(comboStage) * (naLevel - 1));

        Vec3 startPos = player.position();
        Vec3 lookDir = CombatAim.direction(player);
        Vec3 endPos = startPos.add(lookDir.scale(REACH));

        List<LivingEntity> targets = new AreaEntityCollector(level, startPos, endPos, INFLATE).execute();

        for (LivingEntity target : targets) {
            if (target == player) continue;
            ModDamageSpec spec = ModDamageSpec.builder(AttackType.NORMAL_ATTACK, ModElements.ANEMO.get())
                    .multiplier(multiplier)
                    .elementAmount(AttachmentType.WEAK.getInitialAmount())
                    .attackerCharacter(character)
                    .build();
            ModDamageSource source = ModDamageSource.from(spec, player);
            if (target.level() instanceof ServerLevel serverLevel) {
                target.hurtServer(serverLevel, source, 0f);
                // 第 3 段收招双倍
                if (comboStage == 3 && target.isAlive()) {
                    target.hurtServer(serverLevel, source, 0f);
                }
            }
        }
    }
}