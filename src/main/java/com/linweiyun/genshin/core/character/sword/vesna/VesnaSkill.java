package com.linweiyun.genshin.core.character.sword.vesna;
import com.linweiyun.genshin.content.skill_node.ElementalOrbSpawner;
import com.linweiyun.genshin.core.system.combat.CombatAim;
import com.linweiyun.genshin.core.system.combat.action.data.ActionStep;
import com.linweiyun.genshin.core.system.combat.action.data.Hit;

import java.util.List;

import com.linweiyun.genshin.content.entities.teyvat.skill.vesna.VesnaAttackProjectile;
import com.linweiyun.genshin.content.entities.teyvat.skill.vesna.VesnaSpiritSwordEntity;
import com.linweiyun.genshin.content.skill_node.AreaEntityCollector;
import com.linweiyun.genshin.content.skill_node.TargetSeeker;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.sword.vesna.attack.VesnaChargedAttack;
import com.linweiyun.genshin.core.character.sword.vesna.attack.VesnaNormalAttack;
import com.linweiyun.genshin.core.character.sword.vesna.attack.VesnaPlungeAttack;
import com.linweiyun.genshin.core.character.talent.SkillBase;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.action.ActionDefinition;
import com.linweiyun.genshin.core.system.combat.action.ActionKind;
import com.linweiyun.genshin.core.system.combat.action.ActionSet;
import com.linweiyun.genshin.core.system.combat.action.data.CharacterActionData;
import com.linweiyun.genshin.core.system.combat.attack.BurstLanding;
import com.linweiyun.elementlib.core.system.combat.decay.DecaySequence;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.elementlib.core.system.combat.decay.DecayGroup;
import com.linweiyun.elementlib.core.system.combat.decay.DecayGroups;
import com.linweiyun.elementlib.core.system.about.AttachmentType;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.genshin.core.system.reaction.StellarGlimmer;
import com.linweiyun.elementlib.api.ElementalReactionType;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import com.linweiyun.genshin.core.system.registry.register.ModReactionTypes;

/**
 * 薇斯娜的<b>技能</b>（重命名前叫 {@code VesnaTalent}）——
 * 普攻 / 重击 / 战技（巡风列装 + 翔风剑）/ 大招的动作数据与伤害结算。
 *
 * <h2>搬运时只动了三处</h2>
 * <ol>
 *   <li>类名与父类换成 {@link SkillBase}；</li>
 *   <li>命座分支走一行调用 {@link VesnaConstellation}（1 命的免剑气 / 次数上限、6 命的「变移」窗口）；</li>
 *   <li>突破天赋调用走 {@link VesnaTalent}（整肃的叠层 / 清层、大权加成）。</li>
 * </ol>
 * 数值、顺序、日志文本、RNG 用法一个都没动 —— 日志里的 {@code [VesnaTalent]} 标签
 * 保持原样不改成 {@code [VesnaSkill]}，免得按标签过滤日志的人对不上。
 */
public class VesnaSkill extends SkillBase {
    public static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);

    // ==================== 本角色独立衰减序列 ====================

    /**
     * 薇斯娜风铃独立衰减组别。
     * <p>弱附着（100 序列，每 3 次附着 1 次），与普通攻击 / 战技各自独立计数。
     */
    public static final DecayGroup VESNA_WIND_BELL_DECAY = new DecayGroup(
            50,
            DecaySequence.DEFAULT_ELEMENT,
            DecaySequence.DEFAULT_DAMAGE,
            DecaySequence.DEFAULT_POISE
    );

    // ==================== 数据表 ====================
    // 索引 = skillLevel - 1（skillLevel 1~15），倍率都是攻击力百分比（1.0 = 100%）

    public static final float[] SKILL_DAMAGE = {
            0.40f, 0.43f, 0.46f, 0.50f, 0.53f, 0.56f, 0.60f, 0.64f, 0.68f, 0.72f,
            0.76f, 0.80f, 0.85f, 0.90f, 0.95f
    };

    public static final float[] XFJ_LV1 = {
            0.40f, 0.43f, 0.46f, 0.50f, 0.53f, 0.56f, 0.60f, 0.64f, 0.68f, 0.72f,
            0.76f, 0.80f, 0.85f, 0.90f, 0.95f
    };

    public static final float[] XFJ_LV2_MAIN = {
            0.60f, 0.645f, 0.69f, 0.75f, 0.795f, 0.84f, 0.90f, 0.96f, 1.02f, 1.08f,
            1.14f, 1.20f, 1.275f, 1.35f, 1.425f
    };

    public static final float[] XFJ_LV2_SWORD = {
            1.12f, 1.204f, 1.288f, 1.40f, 1.484f, 1.568f, 1.68f, 1.792f, 1.904f, 2.016f,
            2.128f, 2.24f, 2.38f, 2.52f, 2.66f
    };

    public static final float[] XFJ_LV3_SWORD = {
            0.448f, 0.4816f, 0.5152f, 0.56f, 0.5936f, 0.6272f, 0.672f, 0.7168f, 0.7616f, 0.8064f,
            0.8512f, 0.896f, 0.952f, 1.008f, 1.064f
    };

    public static final float[] XFJ_LV3_FINAL = {
            1.568f, 1.6856f, 1.8032f, 1.96f, 2.0776f, 2.1952f, 2.352f, 2.5088f, 2.6656f, 2.8224f,
            2.9792f, 3.136f, 3.332f, 3.528f, 3.724f
    };

    public static final float[] WIND_BELL_DAMAGE = {
            0.104f, 0.1118f, 0.1196f, 0.13f, 0.1378f, 0.1456f, 0.156f, 0.1664f, 0.1768f, 0.1872f,
            0.1976f, 0.208f, 0.221f, 0.234f, 0.247f
    };

    // ==================== 三阶 E 的突进时序 ====================

    /**
     * 三阶 E（翔风剑·三阶）<b>从第几刻开始冻结动画并突进</b>。
     *
     * <p>原神那边这一招是「起跳 → 人消失 → 化作细长螺旋 → 朝目标突刺」，
     * 位移在<b>变身之后</b>，所以不能一按下就冻住冲出去。
     *
     * <p><b>这个值要按动画改</b>：数一下动画里「升空/变身」演完、开始突刺的那一帧，
     * 换算成刻（1 秒 = 20 刻）填进来。填 0 = 恢复「按下即突进」。
     *
     * <p>改它一个数就够了 —— 执行期（{@code protectDuration}）会跟着自动算成
     * 「这个值 + 最后一个伤害点 + {@link #EXECUTION_TAIL_TICKS}」，
     * 不会出现「延后的那几刻没人保护」。
     */
    public static final int THREE_STAGE_DASH_DELAY = 8;

    /** 执行期在最后一个伤害点之后再留几刻（命中硬直，别让后一帧就能取消）。 */
    private static final int EXECUTION_TAIL_TICKS = 2;

    // ==================== 大招（跃起下坠刺击）====================

    /**
     * 大招「致礼·献予女皇陛下」的<b>灵剑伤害</b>倍率表（Lv1~Lv15）。
     *
     * <p>灵剑伤害与「灵剑星扩散伤害」是<b>同一档数值</b>（转成星扩散的风段时用同一个倍率），
     * 所以只有这一张表。
     */
    public static final float[] BURST_SWORD_DAMAGE = {
            2.632f, 2.8294f, 3.0268f, 3.29f, 3.4874f, 3.6848f, 3.948f,
            4.2112f, 4.4744f, 4.7376f, 5.0008f, 5.264f, 5.593f, 5.922f, 6.251f
    };

    /** 落地伤害半径（格）—— 以落点为圆心「方圆 12 格」。 */
    public static final float BURST_RADIUS = 12.0f;

    /** 大招回复的剑气（巡风列装之外也生效）。 */
    public static final float BURST_ENERGY_GAIN = 6f;

    /**
     * 六段普攻每一段产风元素微粒的概率。
     *
     * <p>0.5 × 6 段 ≈ 一整套 3 个（文案要求「随机产、平均 3 个左右」）。
     */
    public static final float NORMAL_ATTACK_PARTICLE_CHANCE = 0.5f;

    /** 一个动作步里最后一个伤害点的时刻（刻）；没有 hits 时是 0。 */
    private static int lastHitDelay(ActionStep step) {
        int last = 0;
        if (step != null && step.hits != null) {
            for (Hit hit : step.hits) {
                last = Math.max(last, hit.delay);
            }
        }
        return last;
    }

    // ==================== 工具方法 ====================

    private static float at(float[] table, int skillLevel) {
        int idx = Math.max(1, Math.min(table.length, skillLevel)) - 1;
        return table[idx];
    }

    public static float getWindBellDamageMultiplier(int skillLevel) {
        return at(WIND_BELL_DAMAGE, skillLevel);
    }

    // ==================== 参数覆盖 ====================

    @Override
    public int getMaxCombo() { return 6; }

    // ──── action set 构建 ────
    // 时序全部来自 CharacterActionData，这里只处理不同 stateKey 下的动画名差异。

    @Override
    public ActionSet buildActionSet(PGCharacter character, String stateKey) {
        ActionSet base = super.buildActionSet(character, stateKey);

        // 满命「翔风剑·变移」窗口：普攻槽 + E 点按槽都换成变移那一段，其余（大招/闪避/重击/长按）不变。
        // 这样客户端与服务端都只从 getActionSet 取表，天然选中同一招，谁都不用写 if。
        if ("bianyi".equals(stateKey)) {
            ActionDefinition bianyi = ActionDefinition.builder(ActionKind.SPECIAL)
                    .step(VesnaResources.BIANYI_STEP)
                    .onCastStart(ctx -> onCastStart(ctx.player, ctx.character, ActionKind.SPECIAL))
                    .onActiveStart(ctx -> bianyiDamage(ctx.player, ctx.character))
                    .build();
            return setBuilder(base)
                    .clearNormalCombo()
                    .addNormalAttack(bianyi)
                    .addSkillTap(bianyi)
                    .build();
        }

        String skillAnim = switch (stateKey) {
            case "windrider_0" -> "skill_energy";
            case "windrider_1" -> "skill_energy_continue";
            case "windrider_2" -> "heavy_3";
            default -> null;
        };

        if (skillAnim == null) return base;

        CharacterActionData actionData = character.getActionData();
        if (actionData == null || actionData.skill() == null || actionData.skill().tap() == null)
            return base;

        ActionStep oldTap = actionData.skill().tap();
        boolean threeStage = "windrider_2".equals(stateKey);

        // 三阶 E：原神的效果是「起跳 → 人消失 → 化作细长螺旋 → 朝目标突刺」。
        // 那一段位移发生在「变身」之后，所以突进要等前几刻播完再开始
        // （ActionStep#dashStartDelay）。一阶/二阶还是按下即冻结。
        int dashDelay = threeStage ? THREE_STAGE_DASH_DELAY : 0;

        // 执行期要盖住「延后突进的那几刻 + 所有伤害点」：
        // 前几刻是变身、后面是突刺，这两段都属于执行期，不该被人打断。
        int protect = threeStage
                ? Math.min(oldTap.duration,
                        dashDelay + lastHitDelay(oldTap) + EXECUTION_TAIL_TICKS)
                : oldTap.protectDuration;

        ActionStep newTap = new ActionStep(
                skillAnim,
                oldTap.duration, protect, oldTap.priority,
                oldTap.moves, oldTap.hits, oldTap.sounds,
                oldTap.skillCharge, oldTap.finalCharge, oldTap.cooldown, oldTap.comboWindow
        );
        // 只换了动画名与上面那两个值，三窗口的其余部分照抄
        newTap.prepareTicks = oldTap.prepareTicks;
        newTap.dashStartDelay = dashDelay;

        return setBuilder(base)
                .addSkillTap(
                        ActionDefinition.builder(ActionKind.ELEMENTAL_SKILL_TAP)
                                .step(newTap)
                                .onCastStart(ctx -> onCastStart(ctx.player, ctx.character,
                                        ActionKind.ELEMENTAL_SKILL_TAP))
                                .onActiveStart(ctx -> elementalSkill(ctx.player, ctx.character, 0))
                                .build()
                )
                .build();
    }

    // ==================== 普攻/重击/下落 = 转发到独立类 ====================

    @Override
    public void attack(Player player, PGCharacter character, int stage) {
        VesnaNormalAttack.execute(player, character, stage);
    }

    @Override
    public void chargeAttack(Player player, PGCharacter character) {
        VesnaChargedAttack.execute(player, character);
    }

    @Override
    public void plungingAttack(Player player, PGCharacter character) {
        VesnaPlungeAttack.execute(player, character);
    }

    // ==================== E ====================

    /**
     * 战技的<b>触发那一刻</b>（动作开始，早于伤害点）—— 「触发即生效」的东西都放这里。
     *
     * <h2>巡风列装为什么必须在这里进</h2>
     * CD 是在服务端受理请求那一刻就设的（{@code applyElementalSkillCooldown}），
     * 而模式要到伤害点（第 6 刻）才开。中间那 6 刻只要被打断，
     * 就变成「CD 转了、模式没进去」—— 玩家看到的是技能白按。
     *
     * <p>现在：受理 = 进模式 = 设 CD，三件事同一刻，之后执行期不再可打断
     * （{@code VesnaResources} 里 skill 步的 {@code protectDuration = 8}，
     * 见 {@code ActionStep#protectDuration} 的三窗口图）。
     *
     * <p>模式里再按 E（翔风剑）不在这里处理：那一刀是「消耗剑气 + 打伤害」，
     * 都发生在伤害点，触发时只标记「这一刀不是入门刀」。
     */
    @Override
    public void onCastStart(Player player, PGCharacter character, ActionKind kind) {
        // 满命「翔风剑·变移」：出手那一刻就把窗口关掉 ——
        // 放完这一下，普攻/战技立刻恢复正常队列（正好符合文案「释放后恢复正常队列」）。
        // ⚠️ 它不叠整肃、不清整肃、不扣剑气、不转 CD，所以放在下面那些逻辑之前直接 return。
        if (kind == ActionKind.SPECIAL) {
            if (player.level().isClientSide()) return;
            if (character instanceof Vesna bianyiUser
                    && bianyiUser.getConstellationObj() instanceof VesnaConstellation constellation) {
                constellation.consumeBianyiWindow(bianyiUser);
            }
            return;
        }
        if (kind != ActionKind.ELEMENTAL_SKILL_TAP && kind != ActionKind.ELEMENTAL_SKILL_HOLD) {
            return;
        }
        if (player.level().isClientSide()) return;
        if (!(character instanceof Vesna vesna)) return;

        if (vesna.isWindriderActive()) {
            // 模式里按 E = 翔风剑（特殊元素战技）：<b>不清整肃</b>，只是又要叠一层。
            // 顺手清一次可能残留的入门标记（上一刀要是没走到伤害点，标记会剩下来，
            // 不清的话这一刀会被当成入门刀：不打伤害、还白送一次模式）。
            vesna.consumeEntryCast();
            return;
        }

        // 只有「进入巡风列装」那一下清空整肃层数（= 施放元素战技「操典·制胜有道」）；
        // 翔风剑属于模式期内的特殊战技，<b>不清</b>，只在伤害点后叠一层。
        // （整肃那套队列实现在 VesnaTalent 里。）
        VesnaTalent talent = (VesnaTalent) vesna.getTalent();
        talent.clearDecree(vesna);

        vesna.activateWindriderMode();
        vesna.markEntryCast();
        player.sendSystemMessage(Component.literal("§a进入巡风列装"));
    }

    @Override
    public void elementalSkill(Player player, PGCharacter character, int skillTime) {
        Level level = player.level();
        if (level.isClientSide()) return;
        if (!(character instanceof Vesna vesna)) return;

        int skillLevel = character.getData().getElementalSkillLevel();

        // 这一刀是不是「进入巡风列装」那一下？触发那一刻就定好了。
        // ⚠️ 不能在这里读 isWindriderActive()：模式在触发时就已经开了，
        //    那样会把入门刀误判成翔风剑（扣剑气、打错倍率、还推进阶级）。
        if (vesna.consumeEntryCast()) {
            dealWindriderEnterDamage(player, vesna, skillLevel);
            return;
        }

        // 先叠整肃、再结算这一刀的伤害 —— 这一刀**自己那一层也算进去**。
        // 不能「打完才叠」：那样每次翔风剑都比实际少一层（表现就是「丢一段」）；
        // 而且只有先叠，第六层才可能在「三阶#3 / 大招」那一刻刚好叠满。
        VesnaTalent talent = (VesnaTalent) vesna.getTalent();
        VesnaConstellation constellation = (VesnaConstellation) vesna.getConstellationObj();
        if (talent.hasSpringRiteTalent(vesna)) {
            talent.grantDecree(vesna);
        }

        int castLevel = vesna.getXiangfengJianLevel();
        if (constellation.lv3CastIsFreeNow(vesna)) {
            // 命座 1：每次进入巡风列装后的第一次最高境界翔风剑不消耗剑气
            // （判据在 VesnaConstellation；日志留在这里，免得 canUseElementalSkill 的长按重试把它刷屏）
            LOGGER.info("[薇斯娜] 命座 1：最高境界翔风剑免剑气（本次进入模式的第一次）");
        } else {
            vesna.consumeEnergy(Vesna.SPECIAL_SKILL_ENERGY_COST);
        }
        castXiangFengJian(player, vesna, skillLevel, castLevel);
        advanceAfterCast(vesna, castLevel);
        sendXiangfengMsg(player, castLevel);

        // 满命：放过最高境界（三阶）翔风剑之后开 5 秒「变移」窗口。
        // ⚠️ 必须在 advanceAfterCast 之后无条件开 —— 那里面可能已经退出巡风列装，
        //    而窗口本来就允许在模式外存在（不过 exitWindriderMode 不清它，见 Vesna）。
        if (castLevel >= 2) {
            constellation.openBianyiWindow(player, vesna);
        }
    }

    // ==================== 满命：翔风剑·变移 ====================

    /** 变移第一段：薇斯娜 150% 攻击力的风元素伤害。 */
    private static final float BIANYI_HIT_MULTIPLIER = 1.5f;
    /** 变移第二段（灵剑）：薇斯娜 200% 攻击力的伤害；星扩散状态下转为星扩散的风段直伤。 */
    private static final float BIANYI_SPIRIT_SWORD_MULTIPLIER = 2.0f;
    /** 变移的攻击范围（格）。 */
    private static final float BIANYI_RANGE = 2.5f;
    /** 巡风列装模式下额外唤出的风翎数量。 */
    private static final int BIANYI_WIND_PLUME_COUNT = 2;

    /**
     * 满命：翔风剑·变移的伤害。
     *
     * <ol>
     *   <li>150% 攻击力的<b>风元素伤害</b>（普通直伤）；</li>
     *   <li>200% 攻击力的<b>灵剑伤害</b> ——
     *       「灵剑」现在<b>不生成真实实体</b>（还没做特效），它的含义就是这一下伤害：
     *       处于<b>辉映·星扩散</b>时转换成「星扩散的风段」直伤（走星烁管线）；</li>
     *   <li>巡风列装模式下额外唤出<b>风翎</b>协同攻击（复用风铃弹射物）。</li>
     * </ol>
     */
    private void bianyiDamage(Player player, PGCharacter character) {
        Level level = player.level();
        if (level.isClientSide()) return;
        if (!(character instanceof Vesna vesna)) return;

        // 和翔风剑一样：优先打锁着的目标，没目标就打正前方
        LivingEntity primaryTarget = new TargetSeeker(
                player, 10.0, TargetSeeker.TargetingType.LINE_OF_SIGHT).execute();
        Vec3 center = primaryTarget != null
                ? primaryTarget.position()
                : player.position().add(CombatAim.direction(player).scale(2.0));

        // ① 150% 风元素伤害
        dealAoeAnemoDamage(player, vesna, center, BIANYI_RANGE, BIANYI_HIT_MULTIPLIER,
                AttachmentType.WEAK.getInitialAmount(), false);

        // ② 200% 灵剑：星扩散下转成星扩散的风段直伤（吃户口基础倍率与大权）
        dealAoeAnemoDamage(player, vesna, center, BIANYI_RANGE, BIANYI_SPIRIT_SWORD_MULTIPLIER,
                AttachmentType.WEAK.getInitialAmount(), hasRadianceStellarSwirl(vesna),
                AttackType.ELEMENTAL_SKILL, DecayGroups.DEFAULT_ELEMENTAL_SKILL,
                vesna.getSovereigntyBonus());

        // ③ 巡风列装模式下额外唤出风翎协同攻击
        if (vesna.isWindriderActive()) {
            int skillLevel = character.getData().getElementalSkillLevel();
            for (int i = 0; i < BIANYI_WIND_PLUME_COUNT; i++) {
                VesnaAttackProjectile plume = VesnaAttackProjectile.create(
                        level, vesna, player.position(), skillLevel);
                if (plume != null) {
                    level.addFreshEntity(plume);
                    vesna.addEnergy(1);
                }
            }
        }

        LOGGER.info("[薇斯娜] 满命·翔风剑·变移 | 星扩散={} | 巡风列装={} | 整肃={}层",
                hasRadianceStellarSwirl(vesna), vesna.isWindriderActive(), decreeStacks(vesna));
    }

    /** 当前有几层整肃（整肃的存储与规则在 {@link VesnaTalent} 里）。 */
    private static int decreeStacks(Vesna vesna) {
        return ((VesnaTalent) vesna.getTalent()).decreeStacks(vesna);
    }

    /** 入门那一下的伤害：模式已经在触发时开好了，这里只结算 AoE。 */
    private void dealWindriderEnterDamage(Player player, Vesna vesna, int skillLevel) {
        Vec3 center = player.position().add(CombatAim.direction(player).scale(2.0));
        float aoeRange = 2.5f;
        float mult = at(SKILL_DAMAGE, skillLevel);
        dealAoeAnemoDamage(player, vesna, center, aoeRange, mult,
                AttachmentType.WEAK.getInitialAmount(), false);
    }
    private static void sendXiangfengMsg(Player player, int castLevel) {
        String name = switch (castLevel) {
            case 0 -> "一阶";
            case 1 -> "二阶";
            case 2 -> "三阶";
            default -> "";
        };
        player.sendSystemMessage(Component.literal("§e翔风剑·" + name));
    }

    private void castXiangFengJian(Player player, Vesna vesna, int skillLevel, int castLevel) {
        boolean stellarSwirl = hasRadianceStellarSwirl(vesna);

        LivingEntity primaryTarget = new TargetSeeker(
                player, 10.0, TargetSeeker.TargetingType.LINE_OF_SIGHT).execute();
        if (primaryTarget == null) return;

        float aoeRange = 2.0f;
        Vec3 center = primaryTarget.position();

        switch (castLevel) {
            case 0 -> {
                float mult = at(XFJ_LV1, skillLevel);
                dealAoeAnemoDamage(player, vesna, center, aoeRange, mult,
                        AttachmentType.WEAK.getInitialAmount(), false);
            }
            case 1 -> {
                float mainMult = at(XFJ_LV2_MAIN, skillLevel);
                dealAoeAnemoDamage(player, vesna, center, aoeRange, mainMult,
                        AttachmentType.WEAK.getInitialAmount(), false);

                float swordMult = at(XFJ_LV2_SWORD, skillLevel);
                // 二阶的第二段就是灵剑，吃整肃
                spawnSpiritSword(player, vesna, center, aoeRange, swordMult,
                        stellarSwirl, 0f, vesna.getSovereigntyBonus());
            }
            case 2 -> {
                float swordMult = at(XFJ_LV3_SWORD, skillLevel);
                float sovereignty = vesna.getSovereigntyBonus();
                for (int i = 0; i < 4; i++) {
                    float attach = (i == 0) ? AttachmentType.WEAK.getInitialAmount() : 0f;
                    // 三阶这四段属于「灵剑」，吃整肃
                    dealAoeAnemoDamage(player, vesna, center, aoeRange, swordMult,
                            attach, stellarSwirl,
                            AttackType.ELEMENTAL_SKILL, DecayGroups.DEFAULT_ELEMENTAL_SKILL, sovereignty);
                }

                float finalMult = at(XFJ_LV3_FINAL, skillLevel);
                spawnSpiritSword(player, vesna, center, aoeRange, finalMult,
                        stellarSwirl, 0f, sovereignty);
            }
            default -> LOGGER.warn("[VesnaTalent] unexpected castLevel={}", castLevel);
        }
    }

    private void advanceAfterCast(Vesna vesna, int castLevel) {
        if (castLevel >= 2) {
            int used = vesna.getLv3UsesInWindrider() + 1;
            // 次数上限跟命座走：0 命 3 次、命座 1 时 4 次（VesnaConstellation.maxLv3UsesInWindrider）
            if (used >= ((VesnaConstellation) vesna.getConstellationObj()).maxLv3UsesInWindrider(vesna)) {
                vesna.exitWindriderMode();
            } else {
                vesna.setLv3UsesInWindrider(used);
            }
        } else {
            vesna.setXiangfengJianLevel(castLevel + 1);
        }
    }

    // ==================== 星扩散判定 ====================

    /**
     * 这一刀要不要按「星扩散」结算。
     *
     * <p>用 {@link StellarGlimmer#hasSwirl} 而不是自己翻效果列表：
     * 星烁有两个分支（星扩散 / 星超导），互斥且星超导优先 ——
     * 薇斯娜的加成<b>只认星扩散</b>（文案只写了星扩散），所以身上是星超导时不转化。
     */
    private static boolean hasRadianceStellarSwirl(Vesna vesna) {
        return StellarGlimmer.hasSwirl(vesna);
    }

    // ==================== 大招：跃起下坠刺击 ====================

    /**
     * 大招落地：以<b>落点</b>为圆心、方圆 {@value #BURST_RADIUS} 格内造成
     */
    @Override
    public void elementalBurst(Player player, PGCharacter character) {
        Level level = player.level();
        if (level.isClientSide()) return;
        if (!(character instanceof Vesna vesna)) return;

        Vec3 landing = BurstLanding.consume(player);
        if (landing == null) {
            // 兜底：没收到落点（例如动作系统关闭、或包丢了）就按自己脚下打
            landing = player.position();
            LOGGER.warn("[VesnaTalent] 大招没有收到落点，按当前位置结算 | player={}", player.getName().getString());
        }

        boolean stellarSwirl = hasRadianceStellarSwirl(vesna);
        // 倍率随「元素爆发等级」走（表见 BURST_SWORD_DAMAGE，Lv1~Lv15）
        float burstMultiplier = at(BURST_SWORD_DAMAGE, character.getData().getElementalBurstLevel());
        LOGGER.info("[VesnaTalent] 大招落地 | 落点=({}, {}, {}) | 半径={} | 倍率={} | 星扩散={} | 整肃={}层",
                String.format(java.util.Locale.ROOT, "%.2f", landing.x),
                String.format(java.util.Locale.ROOT, "%.2f", landing.y),
                String.format(java.util.Locale.ROOT, "%.2f", landing.z),
                BURST_RADIUS, burstMultiplier, stellarSwirl, decreeStacks(vesna));

        // 这一发按「开大之前已有的层数」结算（先取出来，别被下面的叠层改掉）
        float sovereignty = vesna.getSovereigntyBonus();
        // 并回复 6 点剑气 —— 巡风列装之外也生效（先开大再开 E 就能满剑气）
        vesna.addEnergy(BURST_ENERGY_GAIN);

        dealAoeAnemoDamage(player, vesna, landing, BURST_RADIUS, burstMultiplier,
                AttachmentType.STRONG.getInitialAmount(), stellarSwirl,
                AttackType.ELEMENTAL_BURST, DecayGroups.DEFAULT_ELEMENTAL_BURST,
                sovereignty);

        // 叠层放在伤害**之后**：大招是「叠到满层」的那一下之一，
        // 但它自己不吃这一层 —— 第六层整肃就是「能叠上、却没有任何伤害吃得到」。
        VesnaTalent vesnaTalent = (VesnaTalent) vesna.getTalent();
        if (vesnaTalent.hasSpringRiteTalent(vesna)) {
            vesnaTalent.grantDecree(vesna);
        }
    }

    // ==================== 伤害工具 ====================

    /** 元素战技的默认版本（附着与衰减都按战技那一套，不吃整肃）。 */
    private void dealAoeAnemoDamage(Player player, PGCharacter character,
                                    Vec3 center, float aoeRange, float multiplier,
                                    float elementAmount, boolean stellarSwirl) {
        dealAoeAnemoDamage(player, character, center, aoeRange, multiplier, elementAmount, stellarSwirl,
                AttackType.ELEMENTAL_SKILL, DecayGroups.DEFAULT_ELEMENTAL_SKILL, 0f);
    }

    /**
     * 风元素范围伤害（大招 / 战技 / 灵剑共用）。
     *
     * @param stellarSwirl      是否转化为「星扩散的风段」（走星烁管线）
     * @param attackType        攻击类型（决定增伤/衰减标签）
     * @param decayGroup        衰减组别（战技 / 大招各一套）
     * @param sovereigntyBonus  这一段的「大权」加成（整肃层数 ×10%；不是灵剑那几段就传 0）
     */
    private void dealAoeAnemoDamage(Player player, PGCharacter character,
                                    Vec3 center, float aoeRange, float multiplier,
                                    float elementAmount, boolean stellarSwirl,
                                    AttackType attackType, DecayGroup decayGroup,
                                    float sovereigntyBonus) {
        Level level = player.level();
        if (level.isClientSide()) return;

        List<LivingEntity> targets = new AreaEntityCollector(level,
                center.add(-aoeRange, -aoeRange, -aoeRange),
                center.add(aoeRange, aoeRange, aoeRange),
                aoeRange).execute();

                for (LivingEntity target : targets) {
                    if (target == player) continue;

                    ModDamageSpec spec;
                    if (stellarSwirl) {
                        // 转成星扩散的风段时用<b>同一个倍率</b>（文案里「灵剑伤害」与「灵剑星扩散伤害」同值）
                        spec = ModDamageSpec.stellarDirect(
                                ModReactionTypes.STELLAR_SWIRL.get(), ModElements.ANEMO.get(),
                                elementAmount, multiplier)
                                .withStellarBaseBonusMult(
                                        StellarGlimmer.swirlBaseBonusMult(level))
                                .withSovereignty(sovereigntyBonus);
                        spec.setStellarContributors(List.of(character));
                    } else {
                        spec = ModDamageSpec.builder(attackType, ModElements.ANEMO.get())
                                .multiplier(multiplier)
                                .elementAmount(elementAmount)
                                .decayGroup(decayGroup)
                                .attackerCharacter(character)
                                .build()
                                .withSovereignty(sovereigntyBonus);
                    }
                    ModDamageSource source = ModDamageSource.from(spec, player);
                    if (target.level() instanceof ServerLevel serverLevel) {
                        target.hurtServer(serverLevel, source, 0f);
                    }
                }
    }

    // ==================== 灵剑实体生成 ====================

    private void spawnSpiritSword(Player player, Vesna vesna, Vec3 center, float aoeRange,
                                  float multiplier, boolean stellarSwirl, float elementAmount,
                                  float sovereigntyBonus) {
        Level level = player.level();
        if (level.isClientSide()) return;

        Vec3 look = CombatAim.direction(player);
        Vec3 from = player.position().add(look.scale(1.5)).add(0, 2.5, 0);
        Vec3 to = center.add(0, 0.5, 0);

        VesnaSpiritSwordEntity sword = VesnaSpiritSwordEntity.create(
                level, vesna, from, to, multiplier, aoeRange, stellarSwirl, elementAmount,
                sovereigntyBonus);
        if (sword != null) {
            level.addFreshEntity(sword);
        }

        // 「翔风剑的灵剑段」固定产 1 个风元素微粒 —— 不管有没有转成星扩散。
        // 二阶第二段与三阶收尾灵剑都走这里，所以只在这一处生成即可。
        new ElementalOrbSpawner(level, ModElements.ANEMO.get(), 1, true, center).execute();
    }
}