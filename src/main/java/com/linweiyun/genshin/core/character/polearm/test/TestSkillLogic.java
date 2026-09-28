package com.linweiyun.genshin.core.character.polearm.test;

import com.linweiyun.genshin.config.character.LinweiyunTalentConfig;
import com.linweiyun.genshin.content.entities.ModEntities;
import com.linweiyun.genshin.content.entities.area.TalismanSpiritArea;
import com.linweiyun.genshin.content.skill_node.AreaEntityCollector;
import com.linweiyun.genshin.content.skill_node.DashSystem;
import com.linweiyun.genshin.content.skill_node.RushesForward;
import com.linweiyun.genshin.content.skill_node.SkillHelper;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.talent.SkillCastHooks;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.about.AttachmentType;
import com.linweiyun.genshin.core.system.combat.CombatAim;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.genshin.core.system.combat.decay.DecayGroups;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * test 的技能逻辑 —— <b>照抄林薇云</b>（数值表复用 {@link LinweiyunTalentConfig}）。
 *
 * <p>与 {@code LinweiyunSkillLogic} 唯一的差别是<b>多了一步通知</b>：
 * 客户端那一份执行完之后调 {@link SkillCastHooks#fire}，
 * 让客户端表现层（也就是 Photon 特效）有机会接上 —— 见
 * {@code TestCharacterFx#onSkillCast}。
 *
 * <p>为什么放在客户端分支里而不是服务端：招式本体两端各跑一份
 * （{@code SkillBase.buildActionSet} 把 {@code elementalSkill} 挂在 ActionSet 的
 * {@code onActiveStart} 上，而 ActionSet 两端都有）。特效只属于客户端，
 * 放在服务端还得再发一次包，没必要。
 */
final class TestSkillLogic {

    /** 战技点按前冲的距离（格）与持续刻数 —— 照抄林薇云 / 申鹤。 */
    private static final float SKILL_DASH_DISTANCE = 10f;
    private static final int SKILL_DASH_TICKS = 10;

    /** 普攻段数。 */
    static int maxCombo() {
        return 3;
    }

    /** 普攻：面朝方向 2.5 格扇形判定，收招段双倍结算。 */
    static void attack(Player player, PGCharacter character, int comboStage) {
        Level level = player.level();
        if (level.isClientSide()) {
            return;
        }
        int stage = comboStage;
        int naLevel = Math.max(1, character.getData().getNormalAttackLevel());

        float multiplier = (float) (
                LinweiyunTalentConfig.getNABase(stage)
                        + LinweiyunTalentConfig.getNAPerLevel(stage) * (naLevel - 1));

        Vec3 startPos = player.position();
        Vec3 lookDir = CombatAim.direction(player);
        Vec3 endPos = startPos.add(lookDir.scale(2.5f));

        List<LivingEntity> targets = new AreaEntityCollector(level, startPos, endPos, 1.0f).execute();

        for (LivingEntity target : targets) {
            if (target != player) {
                ModDamageSpec spec = ModDamageSpec.builder(AttackType.NORMAL_ATTACK, ModElements.ANEMO.get())
                        .multiplier(multiplier)
                        .elementAmount(AttachmentType.WEAK.getInitialAmount())
                        .attackerCharacter(character)
                        .build();
                ModDamageSource source = ModDamageSource.from(spec, player);
                if (target.level() instanceof ServerLevel serverLevel) {
                    target.hurtServer(serverLevel, source, 0f);
                    if (stage == maxCombo()) {
                        target.hurtServer(serverLevel, source, 0f);
                    }
                }
            }
        }
    }

    /**
     * 元素战技：点按（{@code skillType < 1000}）= 前冲 10 格；
     * 长按（{@code 1000}）= 自身周围 5×4×5 一发更高的伤害。两边都会僵直 10 刻。
     */
    static void elementalSkill(Player player, PGCharacter character, int skillType) {
        Level level = player.level();
        int skillLevel = character.getData().getElementalSkillLevel();

        if (skillType < 1000) {
            float pressDamage = LinweiyunTalentConfig.getSkillPressDamage(skillLevel);
            Vec3 delta = new RushesForward(player, SKILL_DASH_DISTANCE).execute();

            if (level.isClientSide()) {
                DashSystem.startDash(player, delta, SKILL_DASH_TICKS);
                // ↓↓↓ 特效入口：客户端这一份执行完之后通知表现层 ↓↓↓
                SkillCastHooks.fire(player, skillType);
                return;
            }

            DashSystem.startDamageDash(player, delta, SKILL_DASH_TICKS, hitEntity -> {
                ModDamageSpec spec = ModDamageSpec.builder(
                                AttackType.ELEMENTAL_SKILL, ModElements.ANEMO.get())
                        .multiplier(pressDamage)
                        .elementAmount(AttachmentType.WEAK.getInitialAmount())
                        .decayGroup(DecayGroups.SHENHE_SKILL)
                        .attackerCharacter(character)
                        .build();
                ModDamageSource source = ModDamageSource.from(spec, player);
                if (hitEntity.level() instanceof ServerLevel serverLevel) {
                    hitEntity.hurtServer(serverLevel, source, 0f);
                }
            });

            new SkillHelper(player, 10).addStun();
            return;
        }

        if (level.isClientSide()) {
            SkillCastHooks.fire(player, skillType);
            return;
        }

        float holdDamage = LinweiyunTalentConfig.getSkillHoldDamage(skillLevel);
        AABB holdBox = new AABB(
                player.getX() - 2.5, player.getY() - 2, player.getZ() - 2.5,
                player.getX() + 2.5, player.getY() + 2, player.getZ() + 2.5);
        List<LivingEntity> holdTargets = level.getEntitiesOfClass(LivingEntity.class, holdBox,
                e -> e != player && !(e instanceof Player));
        for (LivingEntity target : holdTargets) {
            ModDamageSpec spec = ModDamageSpec.builder(AttackType.ELEMENTAL_SKILL, ModElements.ANEMO.get())
                    .multiplier(holdDamage)
                    .elementAmount(AttachmentType.WEAK.getInitialAmount())
                    .decayGroup(DecayGroups.SHENHE_SKILL)
                    .attackerCharacter(character)
                    .build();
            ModDamageSource source = ModDamageSource.from(spec, player);
            if (target.level() instanceof ServerLevel serverLevel) {
                target.hurtServer(serverLevel, source, 0f);
            }
        }

        new SkillHelper(player, 10).addStun();
    }

    /** 元素爆发：身周 12×4×12 一发伤害，并在原地留一片领域。 */
    static void elementalBurst(Player player, PGCharacter character) {
        Level level = player.level();

        if (level.isClientSide()) {
            // 大招特效想接在这里就打开这行（当前范例只做了战技）
            // SkillCastHooks.fire(player, 0);
            return;
        }

        int burstLevel = character.getData().getElementalBurstLevel();
        float castDamage = LinweiyunTalentConfig.getBurstCastDamage(burstLevel);
        AABB castBox = new AABB(
                player.getX() - 6.0, player.getY() - 2.0, player.getZ() - 6.0,
                player.getX() + 6.0, player.getY() + 2.0, player.getZ() + 6.0);

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, castBox, e -> e != player);
        for (LivingEntity target : targets) {
            ModDamageSpec spec = ModDamageSpec.builder(AttackType.ELEMENTAL_BURST, ModElements.ANEMO.get())
                    .multiplier(castDamage)
                    .elementAmount(1.0f)
                    .attackerCharacter(character)
                    .build();
            ModDamageSource source = ModDamageSource.from(spec, player);
            if (target.level() instanceof ServerLevel serverLevel) {
                target.hurtServer(serverLevel, source, 0f);
            }
        }

        PlayerCharactersAttachment attachment =
                player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        PGCharacter currentChar = attachment.getCurrentCharacter();

        TalismanSpiritArea field = ModEntities.FIELD_TALISMAN_SPIRIT.get()
                .create(level, EntitySpawnReason.EVENT);
        if (field != null) {
            field.setPos(player.position());
            if (currentChar != null) {
                field.setOwner(player, currentChar);
            }
            level.addFreshEntity(field);
        }
    }

    private TestSkillLogic() {
    }
}
