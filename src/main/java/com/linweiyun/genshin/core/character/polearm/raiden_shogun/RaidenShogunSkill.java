package com.linweiyun.genshin.core.character.polearm.raiden_shogun;

import com.linweiyun.genshin.config.character.ShenheTalentConfig;
import com.linweiyun.genshin.content.entities.area.TalismanSpiritArea;
import com.linweiyun.genshin.content.skill_node.AreaEntityCollector;
import com.linweiyun.genshin.content.skill_node.RushesForward;
import com.linweiyun.genshin.content.skill_node.SkillHelper;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.talent.SkillBase;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.action.ActionSet;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.genshin.core.system.combat.decay.DecayGroups;
import com.linweiyun.genshin.content.entities.ModEntities;
import com.linweiyun.genshin.core.system.about.AttachmentType;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import com.linweiyun.genshin.core.system.combat.CombatAim;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * 雷电将军的<b>技能</b>（重命名前叫 {@code RaidenShogunTalent}）——
 * 普攻 / 重击 / 战技 / 大招的动作数据与伤害结算。
 *
 * <p>搬运时只做了两件事：① 类名与父类换成 {@link SkillBase}；
 * ② 把原来内联在 {@code elementalSkill} 里的突破天赋发放（冰凌、突破天赋 2 的增伤）
 * 改成一行调用 {@link RaidenShogunTalent}。
 * 数值、顺序、日志、条件一个都没动（包括 {@code elementalBurst} 里那两处
 * {@code isClientSide} 的写法差异 —— 那是历史写法，重构不动它）。
 */
public class RaidenShogunSkill extends SkillBase {
    public static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);

    // ==================== 参数覆盖 ====================

    @Override
    public int getMaxCombo() { return 5; }

    @Override
    protected ActionSet buildDefaultActionSet(PGCharacter character) {
        return super.buildDefaultActionSet(character);
    }

    @Override
    public void attack(Player player, PGCharacter character, int comboStage) {
        Level level = player.level();
        if (level.isClientSide()) return;


        // ⚠️ 不能对 comboStage 取模：{@code getNABase/getNAPerLevel} 是 **1 基**的（只有 case 1..5），
        //    原来那句 {@code comboStage % getMaxCombo()} 会把第 5 段算成第 0 段 →
        //    倍率落到 {@code default -> 0.0}，**第 5 段普攻恒为 0 伤害**。
        //    连段号本来就是 1..getMaxCombo()，这里只做一次夹取。
        int stage = Math.min(Math.max(1, comboStage), getMaxCombo());
        // 配置的 getNABase/getNAPerLevel 是 1~5 段
        int naLevel = Math.max(1, character.getData().getNormalAttackLevel());

        float multiplier = (float) (
                ShenheTalentConfig.getNABase(stage)
                        + ShenheTalentConfig.getNAPerLevel(stage) * (naLevel - 1)
        );

        Vec3 startPos = player.position();
        Vec3 lookDir = CombatAim.direction(player);
        Vec3 endPos = startPos.add(lookDir.scale(2.5f));

        List<LivingEntity> targets = new AreaEntityCollector(level, startPos, endPos, 1.0f).execute();

        for (LivingEntity target : targets) {
            if (target != player) {
                ModDamageSpec spec = ModDamageSpec.builder(AttackType.NORMAL_ATTACK, ModElements.ELECTRO.get())
                        .multiplier(multiplier)
                        .elementAmount(AttachmentType.ULTRA_STRONG.getInitialAmount())
                        .attackerCharacter(character)
                        .build();
                ModDamageSource source = ModDamageSource.from(spec, player);
                if (target.level() instanceof ServerLevel serverLevel) {
                    target.hurtServer(serverLevel, source, 0f);
                    if (stage == 4) {
                        target.hurtServer(serverLevel, source, 0f);
                    }
                }
            }
        }
    }

    @Override
    public void chargeAttack(Player player, PGCharacter character) {
        Level level = player.level();
        Vec3 startPos = player.position();
        Vec3 dashTotal = new RushesForward(player, 10).execute();
        Vec3 rawEndPos = startPos.add(dashTotal);
        HitResult hit = level.clip(new ClipContext(startPos, rawEndPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 endPos = hit.getLocation();
        List<LivingEntity> entities = new ArrayList<>();
        entities = new AreaEntityCollector(level, startPos, endPos, 0.5f).execute();
        entities.forEach(
                entity -> {
                    if (entity != player) {
                        ModDamageSpec spec = ModDamageSpec.builder(AttackType.ELEMENTAL_SKILL, ModElements.ELECTRO.get())
                                .multiplier(3.5f)
                                .elementAmount(AttachmentType.WEAK.getInitialAmount())
                                .decayGroup(DecayGroups.SHENHE_SKILL)
                                .attackerCharacter(character)
                                .build();
                        ModDamageSource source = ModDamageSource.from(spec, player);
                        if (entity.level() instanceof ServerLevel serverLevel) {
                            entity.hurtServer(serverLevel, source, 0f);
                        }
                    }

                }
        );
    }

    @Override
    public void elementalSkill(Player player, PGCharacter character, int skillType) {
        Level level = player.level();
        PlayerCharactersAttachment attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        int skillLevel = character.getData().getElementalSkillLevel();

        if (skillType < 1000) {
            // ========== 点按 ==========

            // 伤害：向前冲刺并伤害路径上的敌人
            Vec3 startPos = player.position();
            Vec3 dashTotal = new RushesForward(player, 10).execute();
            Vec3 rawEndPos = startPos.add(dashTotal);
            HitResult hit = level.clip(new ClipContext(startPos, rawEndPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            Vec3 endPos = hit.getLocation();
            List<LivingEntity> entities = new ArrayList<>();
            entities = new AreaEntityCollector(level, startPos, endPos, 0.5f).execute();

            float pressDamage = ShenheTalentConfig.getSkillPressDamage(skillLevel);

            entities.forEach(entity -> {
                if (entity != player) {
                    ModDamageSpec spec = ModDamageSpec.builder(AttackType.ELEMENTAL_SKILL, ModElements.CYRO.get())
                            .multiplier(pressDamage)
                            .elementAmount(AttachmentType.WEAK.getInitialAmount())
                            .decayGroup(DecayGroups.SHENHE_SKILL)
                            .attackerCharacter(character)
                            .build();
                    ModDamageSource source = ModDamageSource.from(spec, player);
                    if (entity.level() instanceof ServerLevel serverLevel) {
                        entity.hurtServer(serverLevel, source, 0f);
                    }
                }
            });

            // 冰凌：5根，持续10s / 突破天赋2：点按 - 队伍内所有角色元素战技和元素爆发伤害+15%，持续10s
            if (!level.isClientSide()) {
                if (character.getTalent() instanceof RaidenShogunTalent passive) {
                    passive.grantIcyQuills(player, character, 5, 200);
                    passive.grantAscend2DamageBonus(player, character, false);
                }
            }
            new SkillHelper(player, 10).addStun();

        } else {
            // ========== 长按 ==========

            // 伤害：收集周围5x5x4范围（y=4）内的非玩家实体并造成伤害
            float holdDamage = ShenheTalentConfig.getSkillHoldDamage(skillLevel);

            AABB holdBox = new AABB(
                    player.getX() - 2.5, player.getY() - 2, player.getZ() - 2.5,
                    player.getX() + 2.5, player.getY() + 2, player.getZ() + 2.5);

            List<LivingEntity> holdTargets = level.getEntitiesOfClass(LivingEntity.class, holdBox,
                    e -> e != player && !(e instanceof Player));

            for (LivingEntity target : holdTargets) {
                ModDamageSpec spec = ModDamageSpec.builder(AttackType.ELEMENTAL_SKILL, ModElements.CYRO.get())
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

            // 冰凌：7根，持续15s / 突破天赋2：长按 - 队伍内所有角色普攻、重击、下落攻击伤害+15%，持续15s
            if (!level.isClientSide()) {
                if (character.getTalent() instanceof RaidenShogunTalent passive) {
                    passive.grantIcyQuills(player, character, 7, 300);
                    passive.grantAscend2DamageBonus(player, character, true);
                }
            }
            new SkillHelper(player, 10).addStun();
        }
    }

    public void elementalBurst(Player player, PGCharacter character) {
        Level level = player.level();
        int burstLevel = character.getData().getElementalBurstLevel();

        // 施放直伤 —— 大范围冰伤
        if (!level.isClientSide()) {
            float castDamage = ShenheTalentConfig.getBurstCastDamage(burstLevel);
            AABB castBox = new AABB(
                    player.getX() - 6.0, player.getY() - 2.0, player.getZ() - 6.0,
                    player.getX() + 6.0, player.getY() + 2.0, player.getZ() + 6.0);

            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, castBox,
                    e -> e != player);
            for (LivingEntity target : targets) {
                ModDamageSpec spec = ModDamageSpec.builder(AttackType.ELEMENTAL_BURST, ModElements.CYRO.get())
                        .multiplier(castDamage)
                        .elementAmount(1.0f)
                        .attackerCharacter(character)
                        .build();
                ModDamageSource source = ModDamageSource.from(spec, player);
                if (target.level() instanceof ServerLevel serverLevel) {
                    target.hurtServer(serverLevel, source, 0f);
                }
            }
        }

        // 生成领域
        PlayerCharactersAttachment attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        PGCharacter currentChar = attachment.getCurrentCharacter();

        TalismanSpiritArea field = ModEntities.FIELD_TALISMAN_SPIRIT.get()
                .create(player.level(), EntitySpawnReason.EVENT);
        if (field != null) {
            field.setPos(player.position());
            if (currentChar != null) {
                field.setOwner(player, currentChar);
            }
            boolean added = player.level().addFreshEntity(field);
        }
    }
}
