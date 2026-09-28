package com.linweiyun.genshin.core.character.polearm.shenhe;

import com.linweiyun.genshin.config.character.ShenheTalentConfig;
import com.linweiyun.genshin.content.entities.area.TalismanSpiritArea;
import com.linweiyun.genshin.content.skill_node.AreaEntityCollector;
import com.linweiyun.genshin.content.skill_node.DashSystem;
import com.linweiyun.genshin.content.skill_node.RushesForward;
import com.linweiyun.genshin.content.skill_node.SkillHelper;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.claymore.ClaymoreSkill;
import com.linweiyun.genshin.core.character.talent.SkillBase;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.action.ActionSet;
import com.linweiyun.genshin.core.system.combat.action.data.ActionStep;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import com.linweiyun.genshin.core.system.combat.CombatAim;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;

/**
 * 申鹤的<b>技能</b>（重命名前叫 {@code ShenheTalent}）—— 普攻 / 重击 / 战技 / 大招的动作数据与伤害结算。
 *
 * <p>搬运时只做了两件事：① 类名与父类换成 {@link SkillBase}
 * （申鹤临时改为大剑类之后，父类再往下换成 {@link ClaymoreSkill} —— 重击因此变成持续型，见下方注释）；
 * ② 把原来内联在 {@code elementalSkill} 里的<b>突破天赋</b>发放（冰凌、突破天赋 2 的增伤）
 * 改成一行调用 {@link ShenheTalent}（见 {@link #elementalSkill}）。
 * 数值、顺序、日志、条件一个都没动。
 */
public class ShenheSkill extends ClaymoreSkill {
    public static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);

    private static final float SKILL_DASH_DISTANCE  = 10f;
    private static final int   SKILL_DASH_TICKS     = 10;

    /**
     * 普攻段数 = <b>3</b>。
     *
     * <p>木偶这套普攻动画只有三段（{@code sword_idle_attack_01/02/03}，
     * 名字与时长在 {@link ShenheResources}），所以原来的 5 段里多出来的两段没有任何动画可播
     * （{@code AnimationAvailability} 会把不存在的动画名拦住 ——
     * 那两段就是「伤害数字在跳、人站着不动」）。
     * 连段数必须和 {@code ShenheResources.MAX_COMBO} 一致
     * （{@code SkillBase.buildDefaultActionSet} 取两者较小值）。
     *
     * <p>倍率表 {@link ShenheTalentConfig} 里的第 4 / 5 段因此不再被取到，
     * 第 3 段成为收招段（额外一次 {@code hurtServer}，见 {@code attack}）。
     */
    @Override
    public int getMaxCombo() { return 3; }

    @Override
    protected ActionSet buildDefaultActionSet(PGCharacter character) {
        return super.buildDefaultActionSet(character);
    }

    @Override
    public void attack(Player player, PGCharacter character, int comboStage) {
        Level level = player.level();
        if (level.isClientSide()) return;
        int stage = comboStage;
        int naLevel = Math.max(1, character.getData().getNormalAttackLevel());

        float multiplier = (float) (
                ShenheTalentConfig.getNABase(stage)
                        + ShenheTalentConfig.getNAPerLevel(stage) * (naLevel - 1));

        Vec3 startPos = player.position();
        Vec3 lookDir = CombatAim.direction(player);
        Vec3 endPos = startPos.add(lookDir.scale(2.5f));

        List<LivingEntity> targets = new AreaEntityCollector(level, startPos, endPos, 1.0f).execute();

        for (LivingEntity target : targets) {
            if (target != player) {
                ModDamageSpec spec = ModDamageSpec.builder(AttackType.NORMAL_ATTACK, ModElements.CYRO.get())
                        .multiplier(multiplier)
                        .elementAmount(AttachmentType.WEAK.getInitialAmount())
                        .attackerCharacter(character)
                        .build();
                ModDamageSource source = ModDamageSource.from(spec, player);
                if (target.level() instanceof ServerLevel serverLevel) {
                    target.hurtServer(serverLevel, source, 0f);
                    // 收招段双倍结算：连段 5 段时是第 5 段，改成 3 段后收招段就是第 3 段。
                    if (stage == getMaxCombo()) {
                        target.hurtServer(serverLevel, source, 0f);
                    }
                }
            }
        }
    }

    // 重击：不再自己实现 —— 临时改为大剑之后，重击是「持续型」那一套
    // （按住进入状态、松手/到时结束、每 0.5 秒结算一圈），实现全部在
    // ClaymoreSkill.buildDefaultActionSet + chargeAttack 里，这里刻意不覆盖：
    // 原来那段是「突刺 10 格 + 3.5 倍率火焰伤害」的复制粘贴产物（元素写成了火、
    // 和她的冰属性对不上），而且持续型重击每 0.5 秒都会调一次 chargeAttack，
    // 留着它等于每半秒把人往前推 10 格。

    /**
     * 持续重击播哪个动画。
     *
     * <p>这一段本来该是<b>木偶（桑多涅）</b>的 —— 木偶角色本体还没做，
     * 所以现在把她的重击动画套在申鹤身上验收：
     * {@link ShenheResources#CHARGED_ATTACK_ANIMATION}（在单独的文件
     * {@code character/shenhe/shenhe_puppet.animation.json} 里，
     * 由 {@link ShenheResources#RENDER_DATA} 挂成额外动画文件）。
     *
     * <p>不覆盖的话，默认会沿用动作表里那段重击借来的动画名
     * （{@code SkillBase} 的兜底重击就是点按战技那一段，名字是 {@code "skill"}），
     * 和她这套「坐飞行坐骑 + 屏幕 + FJO」的表现对不上。
     *
     * <p>时长对得上：动画 6.0 秒、持续型重击上限 100 刻（5 秒），
     * 所以循环点永远落在状态结束之后，按住期间不会看到「重新起手」。
     */
    @Override
    protected String getChargedAttackAnimation(PGCharacter character, @Nullable ActionStep source) {
        return ShenheResources.CHARGED_ATTACK_ANIMATION;
    }

    @Override
    public void elementalSkill(Player player, PGCharacter character, int skillType) {
        Level level = player.level();
        String side = level.isClientSide() ? "CLIENT" : "SERVER";
        LOGGER.info("[ShenheTalent.elementalSkill] [{}] enter skillType={}", side, skillType);

        int skillLevel = character.getData().getElementalSkillLevel();

        if (skillType < 1000) {
            float pressDamage = ShenheTalentConfig.getSkillPressDamage(skillLevel);
            Vec3 delta = new RushesForward(player, SKILL_DASH_DISTANCE).execute();
            LOGGER.info("[ShenheTalent.elementalSkill] [{}] delta={}", side, delta);

            if (level.isClientSide()) {
                DashSystem.startDash(player, delta, SKILL_DASH_TICKS);
                LOGGER.info("[ShenheTalent.elementalSkill] [{}] startDash called", side);
            } else {
                DashSystem.startDamageDash(player, delta, SKILL_DASH_TICKS, hitEntity -> {
                    ModDamageSpec spec = ModDamageSpec.builder(
                                    AttackType.ELEMENTAL_SKILL, ModElements.CYRO.get())
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
                LOGGER.info("[ShenheTalent.elementalSkill] [{}] startDamageDash called", side);
            }

            if (level.isClientSide()) return;

            // 突破天赋 1 + 突破天赋 2：点按 —— 冰凌（5 根 / 10 秒）+ 队伍 E/Q 增伤（10 秒）
            // 原来这两段内联在这里，现在只留调用，实现在 ShenheTalent。
            if (character.getTalent() instanceof ShenheTalent passive) {
                passive.grantIcyQuills(player, character, 5, 200);
                passive.grantAscend2DamageBonus(player, character, false);
            }

            new SkillHelper(player, 10).addStun();

        } else {
            if (level.isClientSide()) return;

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

            // 突破天赋 1 + 突破天赋 2：长按 —— 冰凌（7 根 / 15 秒）+ 普攻/重击/下落增伤（15 秒）
            if (character.getTalent() instanceof ShenheTalent passive) {
                passive.grantIcyQuills(player, character, 7, 300);
                passive.grantAscend2DamageBonus(player, character, true);
            }

            new SkillHelper(player, 10).addStun();
        }
    }

    @Override
    public void elementalBurst(Player player, PGCharacter character) {
        Level level = player.level();
        if (level.isClientSide()) return;

        int burstLevel = character.getData().getElementalBurstLevel();

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
}
