package com.linweiyun.genshin.core.system.reaction.builtin;

import com.linweiyun.genshin.content.items.weapon.catalyst.HymnTheMaelstrom;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.elementlib.core.attachment.StatusContainer;
import com.linweiyun.genshin.core.character.util.capability.IStellarStateHolder;
import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.elementlib.core.system.about.AttachmentProfile;
import com.linweiyun.elementlib.core.system.about.AttachmentSource;
import com.linweiyun.elementlib.core.system.about.ElementalAttachmentHelper;
import com.linweiyun.elementlib.core.system.about.ElementalAttachmentInstance;
import com.linweiyun.elementlib.core.system.about.host.EntityHost;
import com.linweiyun.genshin.core.system.combat.damage.DamageIndicatorFactory;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.genshin.core.system.performance.BoundedLruMap;
import com.linweiyun.elementlib.core.system.reaction.ElementalReaction;
import com.linweiyun.elementlib.core.system.reaction.ReactionContext;
import com.linweiyun.genshin.content.effect.character.CharacterEffectHelper;
import com.linweiyun.genshin.content.effect.character.CharacterEffectInstance;
import com.linweiyun.genshin.content.effect.character.ICharacterEffect;
import com.linweiyun.genshin.content.effect.character.impl.RadianceStellarSwirlEffect;
import com.linweiyun.genshin.core.system.reaction.ReactionPriorityCalculator;
import com.linweiyun.genshin.core.system.registry.register.ModCharacterEffects;
import com.linweiyun.elementlib.core.system.reaction.ReactionResult;
import com.linweiyun.genshin.core.system.registry.ModRegistries;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.elementlib.api.ElementalReactionType;
import com.linweiyun.genshin.content.entities.area.StellarVortexEntity;
import com.linweiyun.genshin.core.character.catalyst.vodyanitsa.VodyanitsaTalent;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.content.entities.ModEntities;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

import java.util.*;
import com.linweiyun.elementlib.core.attachment.ElementalAttachments;
import com.linweiyun.genshin.core.system.registry.register.ModReactionTypes;
import com.linweiyun.genshin.core.attachment.ElementContainerQueries;

public class SwirlReaction extends ElementalReaction {
    public static final Logger LOGGER = ModLog.getLogger(LogGroup.ELEMENT);

    public static final float WEAK_SWIRL_SPREAD = 2.2f;
    public static final float STRONG_SWIRL_SPREAD = 3.4f;

    private static final int SWIRL_COOLDOWN_TICKS = 20;
    private static final double SWIRL_RADIUS = 5.0;

    /**
     * 同一目标两次扩散的间隔记录。
     *
     * <p>键是实体 UUID、只 put 不 remove 的静态表会随刷怪一直涨（内存泄漏），
     * 所以用有界 LRU：淘汰效果等价于那条冷却记录自然过期。</p>
     */
    private static final Map<UUID, Long> lastSwirlTick = BoundedLruMap.create();

    private static final String PYRO_ID = ModElements.PYRO.getId().toString();
    private static final String HYDRO_ID = ModElements.HYDRO.getId().toString();
    private static final String ELECTRO_ID = ModElements.ELECTRO.getId().toString();
    private static final String CYRO_ID = ModElements.CYRO.getId().toString();
    private static final String ANEMO_ID = ModElements.ANEMO.getId().toString();
    private static final String FROZEN_ID = ModElements.FROZEN.getId().toString();

    private static final Set<String> SWIRLABLE_IDS = Set.of(PYRO_ID, HYDRO_ID, ELECTRO_ID, CYRO_ID);
    private static final List<String> SPREAD_PRIORITY_IDS = List.of(PYRO_ID, HYDRO_ID, ELECTRO_ID, CYRO_ID);

    public SwirlReaction(java.util.function.Supplier<ElementalReactionType> type,
                         String elementAId, String elementBId,
                         float ratioA, float ratioB, int basePriority) {
        super(type, elementAId, elementBId, ratioA, ratioB, basePriority);
    }

    @Override
    public boolean canMatch(GenshinElement attackerElement, GenshinElement defenderElement) {
        GenshinElement attackerMain = attackerElement.getMainElement();
        GenshinElement defenderMain = defenderElement.getMainElement();
        if (attackerMain == null || defenderMain == null) return false;

        GenshinElement anemo = resolveElement(ANEMO_ID);
        if (attackerMain == anemo && isSwirlable(defenderMain)) return true;
        return false;
    }

    @Override
    public boolean isBlocked(ReactionContext context) {
        // 扩散以实体为中心向周围传播，方块端没有实体载体，直接不参与（否则下面会 NPE）。
        if (context.targetEntity() == null) return true;
        long gameTime = context.targetEntity().level().getGameTime();
        UUID targetId = context.targetEntity().getUUID();
        Long last = lastSwirlTick.get(targetId);
        if (last != null && gameTime - last < SWIRL_COOLDOWN_TICKS) {
            return true;
        }
        return false;
    }

    @Override
    public ReactionResult execute(ReactionContext ctx) {
        GenshinElement anemoEl = resolveElement(ANEMO_ID);
        GenshinElement attackerMain = ctx.attackerElement().getMainElement();
        boolean attackerIsAnemo = (attackerMain == anemoEl);

        GenshinElement spreadElement;
        GenshinElement defenderTarget;

        if (attackerIsAnemo) {
            spreadElement = findSpreadElement(ctx.targetContainer());
            if (spreadElement == null) {
                return ReactionResult.builder(type()).build();
            }
            defenderTarget = spreadElement;
        } else {
            defenderTarget = anemoEl;
            spreadElement = attackerMain;
        }

        float totalDefenderUnit = sumConsumable(ctx.targetContainer(), defenderTarget);
        if (totalDefenderUnit <= 0f) {
            return ReactionResult.builder(type()).build();
        }

        float attackerQty = ctx.attackerUnit();
        float consumedPyroSide;
        float consumedAnemoSide;

        float[] consumed;
        if (attackerIsAnemo) {
            consumed = calculateConsumption(totalDefenderUnit, attackerQty);
        } else {
            consumed = calculateConsumption(attackerQty, totalDefenderUnit);
        }
        consumedPyroSide = consumed[0];
        consumedAnemoSide = consumed[1];
        if (consumedPyroSide <= 0f || consumedAnemoSide <= 0f) {
            return ReactionResult.builder(type()).build();
        }

        // 在消耗元素前收集星扩散贡献者（消耗后 isFinished 会返回 true）
        ServerLevel serverLevel = ctx.targetEntity() != null
                && ctx.targetEntity().level() instanceof ServerLevel sl ? sl : null;
        boolean isStellarSwirl = attackerIsAnemo && spreadElement == ModElements.CYRO.get()
                && serverLevel != null
                && ReactionPriorityCalculator.hasStellarSwirlHousehold(serverLevel);
        PGCharacter triggerCharacter = null;
        List<PGCharacter> preConsumeWindContributors = List.of();
        if (isStellarSwirl) {
            triggerCharacter = resolveTriggerCharacter(ctx.attackerEntity());
            preConsumeWindContributors = new ArrayList<>(ElementContainerQueries.getActiveContributors(
                    ctx.targetContainer(), serverLevel, serverLevel.getGameTime(),
                    ModElements.CYRO.get(), ModElements.ANEMO.get()));
        }

        if (attackerIsAnemo) {
            consumeElementUnit(ctx.targetContainer(), defenderTarget, consumedPyroSide);
            consumeElementUnit(ctx.targetContainer(), anemoEl, consumedAnemoSide);
        } else {
            consumeElementUnit(ctx.targetContainer(), defenderTarget, consumedAnemoSide);
            consumeElementUnit(ctx.targetContainer(), spreadElement, consumedPyroSide);
        }

        lastSwirlTick.put(ctx.targetEntity().getUUID(), ctx.targetEntity().level().getGameTime());

        // 星扩散转化检测：扩冰 + 队伍有星扩散参与者
        if (isStellarSwirl) {
            handleStellarSwirl(ctx, serverLevel, spreadElement, triggerCharacter, preConsumeWindContributors);
        } else {
            float spreadQuantity = calculateSpreadQuantity(consumedAnemoSide);
            applySwirlDamage(ctx, spreadElement, ctx.targetEntity());
            spreadToNearby(ctx, spreadElement, spreadQuantity, ctx.targetEntity());
        }

        return ReactionResult.builder(isStellarSwirl ? ModReactionTypes.STELLAR_SWIRL.get() : type())
                .reacted()
                .consumedAttacker(attackerIsAnemo ? consumedAnemoSide : consumedPyroSide)
                .consumedDefender(attackerIsAnemo ? consumedPyroSide : consumedAnemoSide)
                .build();
    }

    private static GenshinElement resolveElement(String id) {
        if (PYRO_ID.equals(id)) return ModElements.PYRO.get();
        if (HYDRO_ID.equals(id)) return ModElements.HYDRO.get();
        if (ELECTRO_ID.equals(id)) return ModElements.ELECTRO.get();
        if (CYRO_ID.equals(id)) return ModElements.CYRO.get();
        if (ANEMO_ID.equals(id)) return ModElements.ANEMO.get();
        if (FROZEN_ID.equals(id)) return ModElements.FROZEN.get();
        return null;
    }

    private boolean isSwirlable(GenshinElement element) {
        Identifier key = com.linweiyun.elementlib.core.system.registry.ModRegistries.ELEMENT_REGISTRY.getKey(element);
        return key != null && SWIRLABLE_IDS.contains(key.toString());
    }

    private boolean hasHiddenSwirlable(StatusContainer container) {
        for (String id : SPREAD_PRIORITY_IDS) {
            GenshinElement elem = resolveElement(id);
            if (elem != null && ElectroChargedReaction.findElement(container, elem) != null) {
                return true;
            }
        }
        return false;
    }

    private GenshinElement findSpreadElement(StatusContainer container) {
        GenshinElement frozenEl = resolveElement(FROZEN_ID);
        ElementalAttachmentInstance frozenInst =
                ElectroChargedReaction.findElement(container, frozenEl);
        if (frozenInst != null) {
            for (String id : SPREAD_PRIORITY_IDS) {
                GenshinElement elem = resolveElement(id);
                if (elem == null) continue;
                ElementalAttachmentInstance hidden =
                        ElectroChargedReaction.findElement(container, elem);
                if (hidden != null) {
                    return elem;
                }
            }
            return resolveElement(CYRO_ID);
        }

        for (String id : SPREAD_PRIORITY_IDS) {
            GenshinElement elem = resolveElement(id);
            if (elem == null) continue;
            ElementalAttachmentInstance inst =
                    ElectroChargedReaction.findElement(container, elem);
            if (inst != null && inst.getUnit() > 0) {
                return elem;
            }
        }
        return null;
    }

    private float calculateSpreadQuantity(float consumedAnemoSide) {
        if (consumedAnemoSide >= 2.0f) return STRONG_SWIRL_SPREAD;
        return WEAK_SWIRL_SPREAD;
    }

    private void applySwirlDamage(ReactionContext ctx, GenshinElement spreadElement, LivingEntity target) {
        ModDamageSpec spec = ModDamageSpec.transformative(type(), spreadElement, AttackType.SWIRL);
        ModDamageSource source = ModDamageSource.from(spec, ctx.attackerEntity());
        // 伤害入口统一走 hurtServer（原版那条 @Deprecated 的 hurt(DamageSource,float) 已经被替换掉）
        if (target.level() instanceof ServerLevel serverLevel) {
            target.hurtServer(serverLevel, source, 0f);
        }
        DamageIndicatorFactory.reaction(target, type());
    }

    private void spreadToNearby(ReactionContext ctx, GenshinElement spreadElement,
                                float spreadQuantity, LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level)) return;
        double rSq = SWIRL_RADIUS * SWIRL_RADIUS;

        ModDamageSpec dmgSpec = ModDamageSpec.transformative(type(), spreadElement, AttackType.SWIRL);
        AttachmentProfile spreadProfile = createSpreadProfile(spreadQuantity);

        for (LivingEntity nearby : level.getEntitiesOfClass(
                LivingEntity.class,
                target.getBoundingBox().inflate(SWIRL_RADIUS),
                e -> e != target && e.isAlive() && target.distanceToSqr(e) <= rSq)) {

            ModDamageSource dmgSource = ModDamageSource.from(dmgSpec, ctx.attackerEntity());
            nearby.hurtServer(level, dmgSource, 0f);
            DamageIndicatorFactory.reaction(nearby, type());

            StatusContainer nearbyContainer = ElementalAttachments.container(nearby);
            if (nearbyContainer != null) {
                EntityHost nearbyHost = EntityHost.of(nearby);
                // 扩散把元素「再挂」到旁边的人身上，走的是同一个宿主入口：
                // 拒收这次附着的目标不会跟着反应（与直接攻击同一条规则）。
                // 挂上之后的反应由附着入口接着做（附着 → 附着内反应），这里不再单独调反应系统；
                // 带上传染规格与攻击者，让入口内部的反应拿到完整上下文。
                if (nearbyHost == null) {
                    continue;
                }
                ElementalAttachmentHelper.attach(nearbyHost, spreadElement,
                        AttachmentSource.SPECIAL, spreadProfile,
                        com.linweiyun.elementlib.core.system.about.AttachContext.reactionWrite(
                                ctx.attackerEntity()));
            }
        }
    }

    private static AttachmentProfile createSpreadProfile(float quantity) {
        float t = 7f + 2.5f * quantity;
        float v = quantity / t;
        return new AttachmentProfile(quantity, 1.0f, v, t);
    }

    /** 同上：有界 LRU，避免静态表随实体 UUID 无限增长。 */
    private static final Map<UUID, Long> stellarSwirlCooldown = BoundedLruMap.create();
    private static final int STELLAR_SWIRL_COOLDOWN_TICKS = 4;

    private void handleStellarSwirl(ReactionContext ctx, ServerLevel level, GenshinElement spreadElement,
                                      PGCharacter triggerCharacter, List<PGCharacter> windContributorList) {

        long gameTime = level.getGameTime();
        UUID targetId = ctx.targetEntity().getUUID();
        Long lastSS = stellarSwirlCooldown.get(targetId);
        if (lastSS != null && gameTime - lastSS < STELLAR_SWIRL_COOLDOWN_TICKS) {
            return;
        }
        stellarSwirlCooldown.put(targetId, gameTime);

        // 血红之证四件套：触发者穿着四件套时挂上 10 秒 buff
        applyScarletProofBuff(ctx.attackerEntity() instanceof Player p ? p : null, triggerCharacter);

        // 武器被动（漩流颂歌）：附近的队伍成员触发星扩散 → 打开 5 秒强化窗口
        HymnTheMaelstrom.markReactionTriggers(
                level, ctx.attackerEntity(),
                ctx.targetEntity().getX(), ctx.targetEntity().getY(), ctx.targetEntity().getZ());

        if (ctx.targetEntity() instanceof LivingEntity livingTarget) {
            DamageIndicatorFactory.stellarWindReactionGradient(livingTarget,
                    ModReactionTypes.STELLAR_SWIRL.get());
        }

        double x = ctx.targetEntity().getX();
        double y = ctx.targetEntity().getY();
        double z = ctx.targetEntity().getZ();

        StellarVortexEntity existing = StellarVortexEntity.findExisting(level, x, y, z, 10.0f);

        final StellarVortexEntity vortex;
        if (existing != null) {
            vortex = existing;
        } else {
            StellarVortexEntity created = new StellarVortexEntity(ModEntities.STELLAR_VORTEX.get(), level);
            if (created == null) return;
            created.setPos(x, y, z);
            created.setLevel(1);
            level.addFreshEntity(created);
            vortex = created;
        }

        // 突破天赋 1（沃雅妮莎）：遥久之歌持续期间触发星扩散 → 改为创造「流荡风旋」。
        // 数据表现：创造 / 引爆时给周围敌人降 35% 风抗（6 秒）。
        //
        // 两条约束：
        //   ① 必须排在 triggerWindDamage 之前，否则这一下的风伤吃不到自己刚降的抗；
        //   ② 「合并进已有风旋」那一支也要判 flowing，否则场上已有星辉风旋时后续触发转不成流荡风旋。
        boolean flowing = VodyanitsaTalent.songCovers(level, x, y, z);
        if (flowing) {
            vortex.markFlowingSwirl();
            VodyanitsaTalent.shredWindAround(level, x, y, z);
        }

        // 将本次风贡献者加入累积列表（用于后续冰爆炸）
        vortex.addAllContributors(windContributorList);
        if (existing != null) {
            vortex.incrementLevel(triggerCharacter);
        } else {
            // 新建这一枚的那一次星扩散也算「落进这枚星璇」—— 冰段伤害源要记下它，
            // 否则这枚星璇一直没被升级时冰段就没有来源（会退化回第一个贡献者）。
            vortex.recordStellarTrigger(triggerCharacter);
        }
        vortex.triggerWindDamage(triggerCharacter, windContributorList);

        applyRadianceToParticipants(level, x, y, z);
    }

    /**
     * 从攻击者实体解析触发角色的 PGCharacter
     */
    private PGCharacter resolveTriggerCharacter(Entity attacker) {
        if (!(attacker instanceof Player player)) return null;
        PlayerCharactersAttachment att = player.getData(
                AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        return att != null ? att.getCurrentCharacter() : null;
    }

    /**
     * 触发星扩散时，给「穿着血红之证四件套」的触发者挂上那 10 秒 buff
     * （暴击率 +16% / 星扩散伤害 +40%）。
     *
     * <p>判定用四件套的常驻效果 {@code ScarletProof4}（穿着四件才在），
     * 加上的 {@code ScarletProofBuffEffect} 才是真正改属性的那 10 秒。
     */
    private static void applyScarletProofBuff(Player player, PGCharacter triggerCharacter) {
        if (player == null || triggerCharacter == null) {
            return;
        }
        if (!triggerCharacter.getData().getEffectContainer()
                .hasEffectOfType(com.linweiyun.genshin.content.effect.character.artifact.ScarletProof4.class)) {
            return;
        }
        var buff = ModCharacterEffects.SCARLET_PROOF_BUFF_EFFECT.get();
        if (buff == null) {
            return;
        }
        CharacterEffectHelper.addEffect(player, triggerCharacter,
                new CharacterEffectInstance(buff,
                        com.linweiyun.genshin.content.effect.character.artifact.ScarletProofBuffEffect
                                .DURATION_TICKS,
                        0, false));
    }

    private void applyRadianceToParticipants(ServerLevel level, double x, double y, double z) {
        ICharacterEffect radianceEffect = ModCharacterEffects.RADIANCE_STELLAR_SWIRL_EFFECT.get();
        if (radianceEffect == null) return;

        // 突破天赋 1（沃雅妮莎）：遥久之歌持续期间，队伍附近的角色进入辉映·星扩散时
        // 持续时间延长 4 秒。
        boolean songCovers = VodyanitsaTalent.songCovers(level, x, y, z);
        int extend = songCovers ? VodyanitsaTalent.RADIANCE_EXTEND_TICKS : 0;
        int duration = RadianceStellarSwirlEffect.DURATION_TICKS + extend;

        for (Player p : level.players()) {
            PlayerCharactersAttachment att = p.getData(
                    AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            for (int i = 0; i < 4; i++) {
                PGCharacter character = att.getPartyCharacter(i);
                // 状态持有者（能进入星烁状态的角色）才吃这个 buff —— 和户口无关
                if (character instanceof IStellarStateHolder holder
                        && holder.canHoldStellarState()) {
                    CharacterEffectInstance instance = new CharacterEffectInstance(
                            radianceEffect, duration, 0, false);
                    CharacterEffectHelper.addEffect(p, character, instance);
                    // 排查用：挂的时候把时长打出来（含天赋 1 那 4 秒有没有加上）

                }
            }
        }
    }
}
