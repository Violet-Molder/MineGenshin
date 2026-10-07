package com.linweiyun.genshin.core.system.compat;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.TeyvatConvertedDamageSource;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import javax.annotation.Nullable;

/**
 * 与其他 MOD 的战斗兼容入口。
 *
 * <h2>为什么落在 {@link LivingIncomingDamageEvent}</h2>
 * 这个事件在 {@code LivingEntity#hurt} 的最前面触发，而且改的是
 * {@code DamageContainer} 里的伤害值本身 —— 也就是说：
 * <ul>
 *   <li>拿到的是其他 MOD 传进来的<b>原始伤害</b>（还没过护甲 / 附魔 / 吸收）；</li>
 *   <li>{@code setAmount} 改出来的值真的会被后面整条原版管线用上；</li>
 *   <li>目标涵盖所有 {@code LivingEntity}，包括本 MOD 自己的怪物。</li>
 * </ul>
 * 唯一改不动的是伤害源本身（{@code DamageContainer} 里的 source 是 final），
 * 所以原神模式下要换伤害源时走的是「取消这次 + 带新伤害源重打一次」——
 * 和 {@code LivingEntityHurtMixin} 里怪物攻击力换算用的是同一套写法。
 *
 * <h2>只认「玩家打出去」的伤害</h2>
 * 判定条件是攻击者那一侧是玩家：先看伤害源的「造成实体」，退一步再看抛射物的主人
 * （见 {@link #attackerPlayer}）。认不出发起者就不换算。
 * 玩家<b>挨打</b>是另一条路（{@code PlayerHurtInterceptor}），两边互不干涉。
 * 本 MOD 自己的伤害（{@link ModDamageSource}）本来就已经是角色口径，直接放行。
 */
@EventBusSubscriber
public class CompatEventHandler {

    /** 每 tick 维护属性桥（角色攻击力里的 minecraft 加成 / 玩家身上的角色属性）。 */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        PlayerStatBridge.tick(event.getEntity());
    }

    /**
     * 死亡重生后把「链接」这条记账带过去。
     *
     * <p>链接在不在决定了玩家身上那两条属性加成该不该重新挂回去，
     * 丢掉它就会表现成「死一次之后角色属性就不算数了」。
     * （附件本身也标了 copyOnDeath，这里再保险一次，且不挑世界是否处于入侵中。）
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }
        event.getEntity().setData(AttachmentRegistration.COMPAT_LINK_ATTACHMENT,
                event.getOriginal().getData(AttachmentRegistration.COMPAT_LINK_ATTACHMENT));
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!CompatConfig.enabled()) {
            return;
        }

        DamageSource source = event.getSource();

        // 本 MOD 的伤害 / 已经换算过的伤害，一律放行（后者也是防递归的标记）
        if (source instanceof ModDamageSource
                || source instanceof CompatConvertedDamageSource
                || source instanceof TeyvatConvertedDamageSource) {
            return;
        }

        // 只处理「玩家打出去的」伤害
        Player player = attackerPlayer(source);
        if (player == null) {
            return;
        }

        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }

        boolean genshinMode = PlayerStatBridge.isGenshinMode(player);
        boolean linked = PlayerStatBridge.isLinked(player);
        if (!genshinMode && !linked) {
            // 没开过原神模式的玩家，属性全是原版的，没什么好换算
            return;
        }

        if (!genshinMode && usesPlayerAttackPower(source)) {
            // 非原神模式下这类伤害本身就是拿玩家的 ATTACK_DAMAGE 算出来的，
            // 已经吃到了「角色攻击力 × 0.7」那条属性加成，再按倍率乘一遍就是重复加成
            return;
        }

        PlayerCharactersAttachment characters =
                player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        PGCharacter current = characters.getCurrentCharacter();
        if (current == null) {
            return;
        }

        float rawDamage = event.getAmount();
        if (rawDamage <= 0.0F) {
            return;
        }

        double playerAttack = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double characterAttack = current.getData().getAttributeTotalValue(ModAttributes.ATK.value());
        float converted = DamageConversion.convert(rawDamage, playerAttack, characterAttack);

        // 换算不出来（没武器 / 没角色）或倍率正好是 1 —— 保持原样
        if (converted <= 0.0F || Math.abs(converted - rawDamage) < 0.0001F) {
            return;
        }

        if (genshinMode) {
            // 原神模式：换成角色口径 + 带上当前角色的元素，再走一遍原版伤害管线
            CompatConvertedDamageSource convertedSource =
                    new CompatConvertedDamageSource(source, current.getElemental());
            event.setCanceled(true);
            target.hurt(convertedSource, converted);
        } else {
            // 非原神模式：伤害源原封不动，只把数值换掉
            event.setAmount(converted);
        }
    }

    /**
     * 这次伤害是不是「由玩家的攻击力属性算出来的」。
     *
     * <p>判据是原版里那几个直接用 {@code getAttributeValue(ATTACK_DAMAGE)} 算伤害的类型：
     * <ul>
     *   <li>{@link DamageTypes#PLAYER_ATTACK} —— {@code Player#attack} 打出来的一切近战，
     *       横扫、自动旋转攻击都在这里面。</li>
     * </ul>
     * 非原神模式下这些伤害已经通过属性加成吃到了角色的那份攻击力，所以不能再乘一次倍率。
     *
     * <p>反例也说清楚：原版长矛（{@link DamageTypes#SPEAR}）读的是 {@code ATTACK_DAMAGE}
     * 的<b>基础值</b>，属性修饰符加不到它头上，所以它没吃到角色加成，照常换算；别的 MOD
     * 那种「近身放法术」用的也是自己的伤害类型，不在这里面，同样照常换算。
     */
    private static boolean usesPlayerAttackPower(DamageSource source) {
        return source.is(DamageTypes.PLAYER_ATTACK);
    }

    /**
     * 找出这次伤害的发起玩家。
     *
     * <p>首选伤害源里的「造成实体」—— 原版和绝大多数 MOD 都把射手 / 施法者填在这里；
     * 少数 MOD 只填直接实体（抛射物本身）不填造成实体，这时退一步看抛射物的主人是不是玩家。
     * 都认不出来（比如伤害挂在一个召唤物身上）就不换算，宁可不改也不猜。
     */
    private static @Nullable Player attackerPlayer(DamageSource source) {
        if (source.getEntity() instanceof Player player) {
            return player;
        }
        if (source.getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() instanceof Player owner) {
            return owner;
        }
        return null;
    }
}
