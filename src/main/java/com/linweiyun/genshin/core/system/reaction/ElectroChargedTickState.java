package com.linweiyun.genshin.core.system.reaction;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.StatusContainer;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.status.StatusInstance;
import com.linweiyun.genshin.core.system.about.ElementalAttachmentInstance;
import com.linweiyun.genshin.core.system.combat.damage.DamageIndicatorFactory;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.genshin.core.system.performance.HotPathLog;
import com.linweiyun.genshin.core.system.reaction.builtin.ElectroChargedReaction;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;

import java.util.UUID;

public class ElectroChargedTickState implements IPersistedSerializable {
    public static final Logger LOGGER = ModLog.getLogger(LogGroup.ELEMENT);
    private static final float CONSUME_PER_TICK = 0.4f;
    private static final int TICK_INTERVAL = 20;

    @Persisted(key = "ec_active")
    private boolean active;

    @Persisted(key = "ec_tick_counter")
    private int tickCounter;

    @Persisted(key = "ec_attacker_uuid")
    private UUID attackerUUID;

    private transient StatusContainer container;
    private transient LivingEntity targetEntity;

    public ElectroChargedTickState() {
        this.active = false;
        this.tickCounter = 0;
        this.attackerUUID = null;
    }

    public void setContainer(StatusContainer container) {
        this.container = container;
    }

    public void onActiveTrigger(net.minecraft.world.entity.Entity attacker, LivingEntity target) {
        this.active = true;
        this.tickCounter = 0;
        if (attacker != null) {
            this.attackerUUID = attacker.getUUID();
        }
        this.targetEntity = target;
        dealDamage(target, false);
    }

    public void onTick() {
        if (container == null) return;

        if (!active) {
            tryAutoActivate();
            if (!active) return;
        }

        tickCounter++;
        if (tickCounter < TICK_INTERVAL) return;
        tickCounter = 0;

        LivingEntity target = resolveTarget();
        if (target == null || !target.isAlive()) {
            active = false;
            return;
        }

        ElementalAttachmentInstance hydroInst = ElectroChargedReaction.findElement(container, ModElements.HYDRO.get());
        ElementalAttachmentInstance electroInst = ElectroChargedReaction.findElement(container, ModElements.ELECTRO.get());

        if (hydroInst == null || electroInst == null) {
            active = false;
            return;
        }

        float hydroBefore = hydroInst.getUnit();
        float electroBefore = electroInst.getUnit();

        if (hydroBefore <= 0 || electroBefore <= 0) {
            active = false;
            return;
        }

        float consumedHydro = Math.min(CONSUME_PER_TICK, hydroBefore);
        float consumedElectro = Math.min(CONSUME_PER_TICK, electroBefore);

        hydroInst.consume(consumedHydro);
        electroInst.consume(consumedElectro);

        dealDamage(target, true);

        if (hydroInst.getUnit() <= 0 || electroInst.getUnit() <= 0) {
            active = false;
        }
    }

    private void tryAutoActivate() {
        if (ReactionPriorityCalculator.hasFrozen(container)) return;

        LivingEntity target = resolveTarget();
        if (target == null || !target.isAlive()) return;
        if (!(target.level() instanceof ServerLevel level)) return;

        if (ReactionPriorityCalculator.hasColumbinaInParty(level)) return;

        ElementalAttachmentInstance hydroInst =
                ElectroChargedReaction.findElement(container, ModElements.HYDRO.get());
        ElementalAttachmentInstance electroInst =
                ElectroChargedReaction.findElement(container, ModElements.ELECTRO.get());

        if (hydroInst != null && electroInst != null
                && hydroInst.getUnit() > 0 && electroInst.getUnit() > 0) {
            active = true;
            tickCounter = TICK_INTERVAL - 1;
            targetEntity = target;
            // 每次挂上水雷都会走这里：攻速高时一秒几十条，节流后再看（见 HotPathLog）
            if (HotPathLog.allow(LOGGER, "electro-charged-activate", "感电自激活")) {
                LOGGER.info("[感电自激活] target={}", target.getName().getString());
            }
        }
    }

    private void dealDamage(LivingEntity target, boolean chain) {
        LivingEntity attacker = resolveAttacker();
        LivingEntity calcAttacker = attacker != null ? attacker : target;

        ModDamageSpec spec = ModDamageSpec.transformative(
                ElementalReactionType.ELECTRO_CHARGED, ModElements.ELECTRO.get());
        ModDamageSource source = ModDamageSource.from(spec, calcAttacker);

        // 每次感电结算都会走这里（还有连锁），是这条反应里最热的一条日志
        if (HotPathLog.allow(LOGGER, "electro-charged-tick", "感电触发")) {
            LOGGER.info("[感电触发] target={} | chain={} | calcAttacker={}",
                    target.getName().getString(), chain, calcAttacker.getName().getString());
        }

        // 伤害入口统一走 hurtServer（原版那条 @Deprecated 的 hurt(DamageSource,float) 已经被替换掉）
        if (target.level() instanceof ServerLevel serverLevel) {
            target.hurtServer(serverLevel, source, 0f);
        }
        DamageIndicatorFactory.reaction(target, ElementalReactionType.ELECTRO_CHARGED);

        if (chain) {
            chainNearbyWet(target, source, spec);
        }
    }

    private void chainNearbyWet(LivingEntity source, ModDamageSource template, ModDamageSpec spec) {
        if (!(source.level() instanceof ServerLevel level)) return;
        double r = 5.0;
        double rSq = r * r;

        for (LivingEntity nearby : level.getEntitiesOfClass(
                LivingEntity.class,
                source.getBoundingBox().inflate(r),
                e -> e != source && e.isAlive() && source.distanceToSqr(e) <= rSq)) {

            StatusContainer nc = nearby.getData(AttachmentRegistration.CONTAINER);
            if (nc == null) continue;

            ElementalAttachmentInstance h = ElectroChargedReaction.findElement(nc, ModElements.HYDRO.get());
            if (h != null && h.getUnit() > 0) {
                ModDamageSource chainSource = ModDamageSource.from(spec, source);
                nearby.hurtServer(level, chainSource, 0f);
                DamageIndicatorFactory.reaction(nearby, ElementalReactionType.ELECTRO_CHARGED);
            }
        }
    }

    private LivingEntity resolveTarget() {
        if (targetEntity != null && targetEntity.isAlive()) return targetEntity;
        if (container != null) {
            for (StatusInstance inst : container.getAll()) {
                if (inst instanceof ElementalAttachmentInstance ea && ea.getOwner() != null) {
                    return ea.getOwner();
                }
            }
        }
        return null;
    }

    private LivingEntity resolveAttacker() {
        if (attackerUUID == null) return null;
        LivingEntity target = resolveTarget();
        if (target != null && target.level() instanceof ServerLevel level) {
            return (LivingEntity) level.getEntity(attackerUUID);
        }
        return null;
    }
}
