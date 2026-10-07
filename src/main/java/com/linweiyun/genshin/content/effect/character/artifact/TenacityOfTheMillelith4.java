package com.linweiyun.genshin.content.effect.character.artifact;

import com.linweiyun.genshin.content.effect.character.CharacterEffectHelper;
import com.linweiyun.genshin.content.effect.character.CharacterEffectInstance;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.event.game.DamageCalculatedEvent;
import com.linweiyun.genshin.core.system.registry.register.ModCharacterEffects;
import com.linweiyun.genshin.core.system.combat.attack.AttackType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 千岩牢固 · 四件套：元素战技命中敌人后，队伍中附近的所有角色攻击力提升 20%，持续 3 秒，每 0.5 秒至多触发一次。
 *
 * <p>本类只是「穿着四件套」的标记，真正改属性的是触发出来的 {@link TenacityOfTheMillelithBuff}。
 * 触发入口是 {@link #onDamageCalculated(DamageCalculatedEvent)}；闸门是角色身上的持久化游戏刻。
 */
public class TenacityOfTheMillelith4 extends ArtifactSetEffect {

    /** 触发后 buff 持续 3 秒。 */
    public static final int BUFF_DURATION_TICKS = 3 * 20;

    /** 触发间隔下限：每 0.5 秒至多触发一次。 */
    public static final int TRIGGER_GATE_TICKS = 10;

    /** 队伍里最多 4 个角色（和仓库其它地方一致）。 */
    private static final int PARTY_SIZE = 4;

    /** 元素战技命中敌人时触发（服务端）；不是元素战技、没穿满四件套或还在 0.5 秒闸门内直接返回。 */
    @SubscribeEvent
    public static void onDamageCalculated(DamageCalculatedEvent event) {
        if (event.spec().getAttackType() != AttackType.ELEMENTAL_SKILL) return;
        LivingEntity target = event.target();
        if (target == null || target.level().isClientSide()) return;
        PGCharacter attacker = event.attackerCharacter();
        if (attacker == null) return;
        if (!(event.attacker() instanceof Player holder)) return;

        // 穿着四件套才在（四件套是常驻标记，触发出来的才是那 3 秒 buff）
        if (!attacker.getData().getEffectContainer().hasEffectOfType(TenacityOfTheMillelith4.class)) return;

        long now = target.level().getGameTime();
        if (now < attacker.getData().getTenacity4GateTick()) return;
        attacker.getData().setTenacity4GateTick(now + TRIGGER_GATE_TICKS);

        PlayerCharactersAttachment attachment =
                holder.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        if (attachment == null) return;

        for (int i = 0; i < PARTY_SIZE; i++) {
            PGCharacter member = attachment.getPartyCharacter(i);
            if (member == null) continue;
            CharacterEffectHelper.addEffect(holder, member, new CharacterEffectInstance(
                    ModCharacterEffects.TENACITY_OF_THE_MILLELITH_BUFF_EFFECT.get(),
                    BUFF_DURATION_TICKS, 0, false));
        }
    }
}
