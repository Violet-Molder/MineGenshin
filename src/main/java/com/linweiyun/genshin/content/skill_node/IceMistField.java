package com.linweiyun.genshin.content.skill_node;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.elementlib.core.attachment.StatusContainer;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.genshin.core.status.StatusAccessor;
import com.linweiyun.elementlib.core.system.about.AttachmentProfile;
import com.linweiyun.elementlib.core.system.about.AttachmentSource;
import com.linweiyun.elementlib.core.system.about.ElementalAttachmentHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * <b>冰雾区域</b>（技能节点）—— 在地面铺一层持续存在、周期性造成冰元素伤害的雾。
 *
 * <h2>形状</h2>
 * 3×3×1：<b>只有最底下那一层</b>。也就是雾不往上飘，站进去才吃伤害 ——
 * 这决定了应对方式就是「走出去」。
 *
 * <h2>结算节奏</h2>
 * 每 {@link #TICK_INTERVAL} 刻（0.5 秒）结算一次，每次伤害 = 攻击力 × 0.3。
 *
 * <h2>元素附着</h2>
 * 只有这个技能会给玩家的<b>场上角色</b>（不是玩家本体）挂弱冰附着 ——
 * 所以切人会把减速带走，切回来只要附着还在就还在。
 *
 * <p>和 {@link GroundMarker} 一样是「只做效果、不做 AI」的节点，由调用方决定
 * 放哪、放多久。
 */
@EventBusSubscriber
public final class IceMistField {

    /** 每次结算的间隔刻数（0.5 秒）。 */
    public static final int TICK_INTERVAL = 10;

    /** 每次结算的伤害倍率（占攻击力的比例）。 */
    public static final float DAMAGE_MULTIPLIER = 0.3f;

    /** 3×3 的半宽。 */
    public static final double RADIUS = 1.5D;

    /** 每 tick 撒的粒子数。 */
    private static final int PARTICLE_COUNT = 10;

    private static final List<Field> ACTIVE = new ArrayList<>();

    private IceMistField() {
    }

    /**
     * 放一片冰雾。
     *
     * @param center        中心（一般取施法者前方若干格的地面）
     * @param durationTicks 持续刻数
     * @param caster        施法者（决定伤害来源与伤害换算用的基础攻击力）
     * @param attackDamage  施法者的原版 {@code ATTACK_DAMAGE}，伤害 = 它 × {@link #DAMAGE_MULTIPLIER}
     */
    public static void spawn(ServerLevel level, Vec3 center, int durationTicks,
                             LivingEntity caster, float attackDamage) {
        if (level == null || caster == null || durationTicks <= 0) {
            return;
        }
        ACTIVE.add(new Field(level, center, durationTicks, caster, attackDamage));
    }

    public static int activeCount() {
        return ACTIVE.size();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Field> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            Field field = iterator.next();
            field.age++;
            spawnParticles(field);
            if (field.age % TICK_INTERVAL == 0) {
                applyDamage(field);
            }
            if (field.age >= field.durationTicks) {
                iterator.remove();
            }
        }
    }

    /** 粒子：只在最底下那一层铺满 3×3。 */
    private static void spawnParticles(Field field) {
        field.level.sendParticles(ParticleTypes.SNOWFLAKE,
                field.center.x, field.center.y + 0.1D, field.center.z,
                PARTICLE_COUNT, RADIUS, 0.05D, RADIUS, 0.01D);
    }

    private static void applyDamage(Field field) {
        double height = 2.0D;
        AABB area = AABB.ofSize(
                new Vec3(field.center.x, field.center.y + height / 2.0D, field.center.z),
                RADIUS * 2.0D, height, RADIUS * 2.0D);

        ModDamageSpec spec = ModDamageSpec.builder(AttackType.MONSTER, ModElements.CYRO.get())
                .multiplier(DAMAGE_MULTIPLIER)
                .elementAmount(AttachmentProfile.WEAK.getBaseQuantity())
                .build();
        ModDamageSource source = ModDamageSource.from(spec, field.caster);
        for (LivingEntity victim : field.level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (victim == field.caster || !victim.isAlive()) {
                continue;
            }
            victim.hurtServer(field.level, source, 0f);
        }
    }

    /** 一片正在生效的冰雾。 */
    private static final class Field {
        private final ServerLevel level;
        private final Vec3 center;
        private final int durationTicks;
        private final LivingEntity caster;
        private final float attackDamage;
        private int age;

        private Field(ServerLevel level, Vec3 center, int durationTicks,
                      LivingEntity caster, float attackDamage) {
            this.level = level;
            this.center = center;
            this.durationTicks = durationTicks;
            this.caster = caster;
            this.attackDamage = attackDamage;
        }
    }
}
