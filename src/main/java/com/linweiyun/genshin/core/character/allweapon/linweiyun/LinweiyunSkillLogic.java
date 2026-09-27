package com.linweiyun.genshin.core.character.allweapon.linweiyun;

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
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import com.linweiyun.genshin.core.system.about.AttachmentType;
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
import org.slf4j.Logger;

import java.util.List;

/**
 * 林薇云的技能<b>逻辑</b> —— 数值、顺序、条件全部照抄申鹤的 {@code ShenheSkill}，
 * 由两个形态技能共用（{@link LinweiyunSkill} 覆盖普通形态、{@link LinweiyunClaymoreSkill}
 * 覆盖大剑形态），所以抽成静态方法而不是各写一份。
 *
 * <h2>和申鹤那份的三处差异（都是必然的）</h2>
 * <ol>
 *   <li><b>元素</b>：申鹤写死冰（{@code CYRO}），林薇云是风（{@code ANEMO}）；</li>
 *   <li><b>倍率来源</b>：{@link LinweiyunTalentConfig}（key 带 {@code lwy-} 前缀）；</li>
 *   <li><b>被动天赋</b>：申鹤战技里要发冰凌 / 突破天赋 2 的增伤，那两段绑在 {@code ShenheTalent}
 *       上；林薇云还没有自己的被动天赋类，<b>先不接</b>（倍率表里对应两档也留着没删）。</li>
 * </ol>
 *
 * <p>重击不在这里：大剑形态走 {@code ClaymoreSkill} 的持续型重击，其余形态走默认点按重击，
 * 和申鹤那边的处置一致（见 {@code ShenheSkill} 里关于重击的注释）。
 */
final class LinweiyunSkillLogic {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);

    /** 战技点按前冲的距离（格）与持续刻数 —— 照抄申鹤。 */
    private static final float SKILL_DASH_DISTANCE = 10f;
    private static final int SKILL_DASH_TICKS = 10;

    /**
     * 普攻段数 —— 照抄申鹤的 <b>3</b> 段。
     *
     * <p>⚠️ 林薇云现在的动画文件里只有移动那几条（idle / walk / run / jump / fly…），
     * <b>还没有普攻 / 战技 / 爆发的动画</b>，所以这几段暂时只有伤害数字、人是站着不动的
     * （动画名不存在时 {@code AnimationAvailability} 会拦住，不会闪原始姿态）。
     * 补上动画后如果要改段数，这里和 {@link LinweiyunTalentConfig} 的倍率表一起改。
     */
    static int maxCombo() {
        return 3;
    }

    /** 普攻一段：面朝方向 2.5 格的扇形判定，收招段（第 maxCombo 段）双倍结算。 */
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
        Vec3 lookDir = player.getLookAngle();
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
                    // 收招段双倍结算（照抄申鹤那边「第 5 段收招」的写法，段数改成 3 后就是第 3 段）
                    if (stage == maxCombo()) {
                        target.hurtServer(serverLevel, source, 0f);
                    }
                }
            }
        }
    }

    /**
     * 元素战技：点按（{@code skillType < 1000}）= 前冲 10 格、途中撞到的敌人吃一次伤害 + 10 刻僵直；
     * 长按 = 自身周围 5×4×5 的敌人吃一发更高的伤害 + 僵直。两边都照抄申鹤。
     */
    static void elementalSkill(Player player, PGCharacter character, int skillType) {
        Level level = player.level();
        String side = level.isClientSide() ? "CLIENT" : "SERVER";
        LOGGER.info("[LinweiyunSkill.elementalSkill] [{}] enter skillType={}", side, skillType);

        int skillLevel = character.getData().getElementalSkillLevel();

        if (skillType < 1000) {
            float pressDamage = LinweiyunTalentConfig.getSkillPressDamage(skillLevel);
            Vec3 delta = new RushesForward(player, SKILL_DASH_DISTANCE).execute();

            if (level.isClientSide()) {
                DashSystem.startDash(player, delta, SKILL_DASH_TICKS);
            } else {
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
            }

            if (level.isClientSide()) {
                return;
            }

            // 申鹤在这里发冰凌 + 突破天赋 2 的增伤；林薇云还没有被动天赋类，先不接（见类注释）。
            new SkillHelper(player, 10).addStun();

        } else {
            if (level.isClientSide()) {
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
    }

    /** 元素爆发：身周 12×4×12 的敌人吃一发伤害，然后在原地留下一片领域（复用申鹤的神符领域实体）。 */
    static void elementalBurst(Player player, PGCharacter character) {
        Level level = player.level();
        if (level.isClientSide()) {
            return;
        }

        int burstLevel = character.getData().getElementalBurstLevel();

        float castDamage = LinweiyunTalentConfig.getBurstCastDamage(burstLevel);
        AABB castBox = new AABB(
                player.getX() - 6.0, player.getY() - 2.0, player.getZ() - 6.0,
                player.getX() + 6.0, player.getY() + 2.0, player.getZ() + 6.0);

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, castBox,
                e -> e != player);
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
                .create(player.level(), EntitySpawnReason.EVENT);
        if (field != null) {
            field.setPos(player.position());
            if (currentChar != null) {
                field.setOwner(player, currentChar);
            }
            player.level().addFreshEntity(field);
        }
    }

    private LinweiyunSkillLogic() {
    }
}
