package com.linweiyun.genshin.mixin.mixins;


import com.linweiyun.genshin.content.entities.teyvat.TeyvatEntityStats;
import com.linweiyun.genshin.content.entities.teyvat.TeyvatLiving;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.system.combat.attack.HurtEntityHelper;
import com.linweiyun.genshin.core.system.combat.damage.DamageIndicatorFactory;
import com.linweiyun.genshin.core.system.combat.damage.DamageOutcome;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.genshin.core.system.combat.damage.TeyvatConvertedDamageSource;
import com.linweiyun.genshin.event.game.DamageCalculatedEvent;
import com.linweiyun.genshin.event.game.DamageDealtEvent;
import com.linweiyun.genshin.core.system.shield.ShieldService;
import com.linweiyun.genshin.core.system.control.ControlService;
import com.linweiyun.genshin.core.system.performance.HotPathLog;
import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.elementlib.core.system.about.ElementalAttachable;
import com.linweiyun.elementlib.api.event.ElibEvents;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.world.TeyvatWorldInvasion;
import com.linweiyun.elementlib.api.ElementalReactionType;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.linweiyun.genshin.core.system.registry.register.ModReactionTypes;

@Mixin(LivingEntity.class)
public class LivingEntityHurtMixin {
    private static final Logger LOGGER = ModLog.getLogger(LogGroup.MIXIN);


    @Shadow
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Shadow
    private void playSecondaryHurtSound(DamageSource source) {

    }

    @Shadow
    protected void playHurtSound(DamageSource source) {

    }

    @Shadow
    protected int lastHurtByPlayerMemoryTime;

    @Shadow
    protected EntityReference<Player> lastHurtByPlayer;

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void onLivingEntityHurtServer(ServerLevel level, DamageSource source, float damage,
                                          CallbackInfoReturnable<Boolean> cir) {

        LivingEntity self = (LivingEntity) (Object) this;

        // 护盾：伤害还没落地之前先问盾。
        //
        // ⚠️ 两个「不能在这里扣盾」的情况：
        //   ① ModDamageSource —— 它的真实伤害在下面才算出来，传进来的 damage 通常是 0；
        //   ② 怪物攻击的「换算」分支 —— 它会带着换算后的伤害<b>递归调用</b> hurtServer，
        //      在这里扣一次、递归里再扣一次就双倍消耗了。所以让它去递归那一层扣。
        boolean convertedBelow = usesTeyvatConversion(level, source);
        if (!convertedBelow && !(source instanceof ModDamageSource)) {
            // 原版伤害源没有 ModDamageSpec，也就没有削韧信息 —— 非原神模式下的普通攻击
            // 在这里按「物理非钝击」兜底给一点削韧，否则纯元素盾永远磨不掉。
            float poise = ShieldService.plainAttackPoise(source);
            float through = ShieldService.absorbDamage(self, source, damage, poise);
            if (damage > 0f && through <= 0f) {
                // 盾全吃下了：本次不构成受伤
                cir.setReturnValue(false);
                return;
            }
            damage = through;
            // 控制入口：削韧与控制一起交给目标自己判（有盾/免疫 → 什么都不做；
            // 没破韧 → 只削韧；破韧 → 再打断动作 + 击退）。
            // 每一次命中都走这里，所以破韧期间是「随便一下都算数」，不只是破韧那一下。
            ControlService.onHit(self, poise, source);
        }

        if (TeyvatWorldInvasion.get(level).isInvaded()
                && !(source instanceof ModDamageSource)
                && !(source instanceof TeyvatConvertedDamageSource)) {
            Entity attacker = source.getEntity();
            if (attacker instanceof TeyvatLiving teyvatAttacker && attacker instanceof LivingEntity livingAttacker) {
                TeyvatEntityStats stats = teyvatAttacker.getEntityStats();
                float teyvatAttack = stats.attack();
                if (teyvatAttack > 0) {
                    float vanillaAttack = (float) livingAttacker.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
                    if (vanillaAttack > 0) {
                        float convertedDamage = damage / vanillaAttack * teyvatAttack;
                        cir.cancel();
                        TeyvatConvertedDamageSource newSource = new TeyvatConvertedDamageSource(source);
                        boolean result = self.hurtServer(level, newSource, convertedDamage);
                        cir.setReturnValue(result);
                        return;
                    }
                }
            }
        }

        if (!(source instanceof ModDamageSource modSource)) {
            return;
        }

        if (!TeyvatWorldInvasion.get(level).isInvaded()) return;

        LivingEntity target = self;

        // 目标已死亡（如多段连击前段已击杀），跳过后续伤害和事件触发
        if (!target.isAlive()) {
            cir.setReturnValue(false);
            return;
        }

        ModDamageSpec spec = modSource.getSpec();
        PGCharacter attackerCharacter = spec.getAttackerCharacter();
        GenshinElement element = spec.getElement();
        // 方块附着：带元素的伤害都顺手留给落点周围的环境（领域持续伤害、下落攻击等都走这里）
        com.linweiyun.genshin.core.system.combat.attack.DamageBlockAttack.onElementalDamage(
                level, target, source.getEntity(), element,
                com.linweiyun.elementlib.core.system.about.AttachmentProfile.forAmount(spec.getElementAmount()),
                spec.getElementAmount());
        float rawDamage = HurtEntityHelper.calculateFinalModDamage(
                modSource, attackerCharacter, target);

        // 护盾：ModDamageSource 的真实伤害到这里才算出来，所以在这里扣盾
        // 带攻击者：普攻 / 重击 / 下坠的基准削韧按「武器类型」查表（见 WeaponPoiseTable）
        float poiseDamage = spec.getPoiseDamage(attackerCharacter);
        float shieldAbsorbed = 0f;
        float finalDamage = rawDamage;
        if (rawDamage > 0f) {
            finalDamage = ShieldService.absorbDamage(target, source, rawDamage, poiseDamage);
            shieldAbsorbed = Math.max(0f, rawDamage - finalDamage);
        }

        // 控制入口（玩家与怪物共用）：削韧与控制一起交给目标自己判 ——
        // 没破韧 → 只进韧性条；破韧 → 打断动作 + 击退；首领 → 破韧也拦，
        // 只认破绽窗口（ControlService.openGap）与直通口（ControlService.force）。
        // 顺序固定在护盾之后：有盾时会被判成「免疫」，连韧性条都不进。
        ControlService.onHit(target, poiseDamage, source);

        PGCharacter targetCharacter = resolveTargetCharacter(target);

        DamageContainer container = new DamageContainer(source, finalDamage);
        if (target.isAlive() && CommonHooks.onEntityIncomingDamage(target, container)) {
            // 被钩子取消：仍属命中并已结算，只是没落到血量上
            postDamageCalculated(level, spec, source, attackerCharacter, target, targetCharacter,
                    new DamageOutcome(true, false, rawDamage, shieldAbsorbed, finalDamage,
                            DamageOutcome.DamageBlockReason.INCOMING_CANCELLED, false));
            cir.setReturnValue(false);
            return;
        }

        // 阶段一：伤害结算完成（扣血之前）
        DamageOutcome.DamageBlockReason blockReason = DamageOutcome.DamageBlockReason.NONE;
        if (finalDamage <= 0f) {
            if (element != null && ElementalAttachable.isImmuneToDamage(target, element)) {
                blockReason = DamageOutcome.DamageBlockReason.IMMUNITY;
            } else if (shieldAbsorbed > 0f) {
                blockReason = DamageOutcome.DamageBlockReason.SHIELD;
            } else {
                blockReason = DamageOutcome.DamageBlockReason.ZERO;
            }
        }
        postDamageCalculated(level, spec, source, attackerCharacter, target, targetCharacter,
                new DamageOutcome(true, false, rawDamage, shieldAbsorbed, finalDamage, blockReason, false));

        // 阶段二：扣血。原神模式玩家的血量在 PGCharacter 上，前后值必须走同一条路径读
        float healthBefore = healthOf(target, targetCharacter);
        if (targetCharacter != null) {
            targetCharacter.hurt(finalDamage);
        } else {
            target.setHealth(Math.max(target.getHealth() - finalDamage, 0));
        }
        float healthAfter = healthOf(target, targetCharacter);

        boolean damaged = finalDamage > 0f && healthAfter < healthBefore;
        boolean killed = target.isDeadOrDying()
                || (targetCharacter != null && targetCharacter.getData().getCurrentHP() <= 0.0);
        if (damaged) {
            DamageDealtEvent event = new DamageDealtEvent(level, level.getGameTime(),
                    new DamageOutcome(true, true, rawDamage, shieldAbsorbed, finalDamage,
                            DamageOutcome.DamageBlockReason.NONE, killed),
                    spec, source.getEntity(), attackerCharacter, target, targetCharacter);
            ElibEvents.post(event);

            if (source.getEntity() instanceof net.minecraft.world.entity.player.Player hookPlayer) {
                com.linweiyun.genshin.core.character.util.handler.PartyHooks.damage(hookPlayer, target, spec);
            }

            // 普通攻击产球由伤害管线直接驱动
            com.linweiyun.genshin.core.system.combat.damage.NormalAttackOrbProducer.tryProduce(
                    level, spec, source.getEntity(), attackerCharacter);
        }

        if (finalDamage > 0f) {
            try {
                boolean isCrit = spec.isCrit();
                // 暴击参数是常量：早先每次暴击都要新建一个 Builder + 一个 Options
                DamageIndicatorFactory.Options options = isCrit
                        ? DamageIndicatorFactory.Options.CRIT
                        : DamageIndicatorFactory.Options.DEFAULT;

                if (spec.getDamageType() == ModDamageSpec.DamageType.LUNAR) {
                    boolean isDirectLunar = spec.getAtkMultiplier() > 0 || spec.getHpMultiplier() > 0;
                    if (!isDirectLunar) {
                        // 雷暴云周期结算每跳都会再飘一次「月感电」，走和反应触发同一套斜体配色
                        DamageIndicatorFactory.lunarReactionGradient(
                                target, ModReactionTypes.LUNAR_CHARGED.get());
                    }

                    // 伤害数字与「月感电」反应文字同一套：顶部月色 → 白色 + 斜体
                    DamageIndicatorFactory.Options lunarOptions = isCrit
                            ? DamageIndicatorFactory.Options.LUNAR_CRIT
                            : DamageIndicatorFactory.Options.LUNAR;
                    DamageIndicatorFactory.lunarDamageGradient(
                            target, modSource, finalDamage, lunarOptions);
                } else if (spec.getDamageType() == ModDamageSpec.DamageType.STELLAR) {
                    handleStellarDamageIndicator(target, modSource, spec, finalDamage, options);
                } else if (element == ModElements.HYDRO.get()) {
                    int hydroColor = DamageIndicatorFactory.getColorForElement(ModElements.HYDRO.get());
                    if (isCrit) {
                        DamageIndicatorFactory.critGradient(
                                target, modSource, finalDamage,
                                0xFFFFFF, hydroColor, options);
                    } else {
                        DamageIndicatorFactory.damageGradient(
                                target, modSource, finalDamage,
                                0xFFFFFF, hydroColor, options);
                    }
                } else {
                    if (isCrit) {
                        DamageIndicatorFactory.crit(target, modSource, finalDamage, element, options);
                    } else {
                        DamageIndicatorFactory.damage(target, modSource, finalDamage, element, options);
                    }
                }
            } catch (Throwable t) {
                LOGGER.error("[DI-Mixin] DamageIndicatorFactory threw", t);
            }
        }

        level.broadcastDamageEvent(target, source);

        if (target.isDeadOrDying()) {
            target.makeSound(getDeathSound());
            playSecondaryHurtSound(source);

            if (modSource.getEntity() instanceof Player player) {
                lastHurtByPlayerMemoryTime = 100;
                lastHurtByPlayer = EntityReference.of(player);
            }

            target.die(source);
        } else {
            playHurtSound(source);
        }

        CommonHooks.onLivingDamagePost(target, container);

        cir.setReturnValue(true);
    }

    /**
     * 这次伤害会不会走下面那个「怪物攻击力换算」分支。
     *
     * <p>那个分支会带着换算后的伤害<b>递归调用</b> {@code hurtServer}，
     * 所以护盾必须在递归的那一层扣，否则同一次攻击会被扣两遍。
     */
    /** 受击方是原神模式玩家时返回其当前出战角色（血量在角色上），否则返回 null。 */
    @Nullable
    private static PGCharacter resolveTargetCharacter(LivingEntity target) {
        if (!(target instanceof Player player)) {
            return null;
        }
        if (!Boolean.TRUE.equals(player.getData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT))) {
            return null;
        }
        PlayerCharactersAttachment attachment =
                player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        return attachment == null ? null : attachment.getCurrentCharacter();
    }

    /** 读血量：原神模式玩家读角色血量，其它读实体血量。 */
    private static float healthOf(LivingEntity target, @Nullable PGCharacter targetCharacter) {
        return targetCharacter != null
                ? (float) targetCharacter.getData().getCurrentHP()
                : target.getHealth();
    }

    /** 广播伤害结算完成事件。 */
    private static void postDamageCalculated(ServerLevel level, ModDamageSpec spec, DamageSource source,
                                             @Nullable PGCharacter attackerCharacter, LivingEntity target,
                                             @Nullable PGCharacter targetCharacter, DamageOutcome outcome) {
        DamageCalculatedEvent event = new DamageCalculatedEvent(level, level.getGameTime(), outcome, spec,
                source.getEntity(), attackerCharacter, target, targetCharacter);
        ElibEvents.post(event);
    }

    private static boolean usesTeyvatConversion(ServerLevel level, DamageSource source) {
        if (!TeyvatWorldInvasion.get(level).isInvaded()) return false;
        if (source instanceof ModDamageSource || source instanceof TeyvatConvertedDamageSource) return false;
        Entity attacker = source.getEntity();
        if (!(attacker instanceof TeyvatLiving teyvatAttacker)
                || !(attacker instanceof LivingEntity livingAttacker)) {
            return false;
        }
        if (teyvatAttacker.getEntityStats().attack() <= 0f) return false;
        return livingAttacker.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) > 0;
    }

    /**
     * 星烁（星扩散 / 星超导）伤害数字。
     *
     * <p>与反应文字同一套：白顶 → 对应分支的星辉底色（风 {@code #68FBCA} / 冰 {@code #99FBFB}）+ 斜体，
     * 具体取色在 {@link DamageIndicatorFactory#stellarDamageGradient} 里按伤害元素决定。</p>
     */
    private static void handleStellarDamageIndicator(LivingEntity target, DamageSource source,
                                                     ModDamageSpec spec, float finalDamage,
                                                     DamageIndicatorFactory.Options options) {
        DamageIndicatorFactory.stellarDamageGradient(target, source, finalDamage, spec.getElement(), options);
    }
}
