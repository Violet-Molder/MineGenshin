package com.linweiyun.genshin.mixin.mixins;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.system.combat.attack.PlungeState;
import com.linweiyun.genshin.core.system.combat.damage.FallDamage;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <b>摔落伤害改成「最大生命值的百分比」</b>。
 *
 * <h2>拦在哪一层</h2>
 * 落地链是 {@code Entity#checkFallDamage → Block#fallOn（干草 / 粘液块那几种方块有自己的 fallOn）
 * → entity#causeFallDamage → Player#causeFallDamage}，真正扣血发生在
 * {@code LivingEntity#causeFallDamage} 里（那一层还负责「被爆炸 / 风弹顶起来那一摔」的折算）。
 * 这里在玩家的入口 HEAD 取消，后面整条链都不跑：既不会重复扣血，也不用去动怪物共用的那一层。
 *
 * <h2>参数含义</h2>
 * 第一个参数是<b>本次落地的实际高度（格）</b>：原版「安全高度 3 格」是在
 * {@code calculateFallDamage} 里才扣掉的，而那一层被这里整个跳过，所以表里的格数直接对得上实际高度。
 * 实际用值时取 {@code max(player.fallDistance, 参数)}：石笋那条会传「高度 + 2.5」，取大的更接近真实。
 * 第二个参数是方块自带的减免乘数（普通方块 1.0、粘液块 0.0、干草与蜂蜜 0.2、石笋 2.0）。
 *
 * <h2>不受影响的两件事</h2>
 * <ul>
 *   <li>能飞就不吃摔伤（{@code mayFly}）：不拦，交回原版；</li>
 *   <li>被爆炸 / 风弹顶起来的那一摔：{@code isIgnoringFallDamageFromCurrentImpulse} 那一档直接放行。</li>
 * </ul>
 *
 * <p>伤害扣在<b>当前出战角色</b>身上（{@link PGCharacter#hurt}），走角色血量池，与护盾无关。
 * 原版那条 {@code FALL_ONE_CM} 统计也随之跳过。
 */
@Mixin(Player.class)
public class PlayerFallDamageMixin {

    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void minegenshin$percentFallDamage(float fallDistance, float multiplier, DamageSource source,
                                               CallbackInfoReturnable<Boolean> cir) {
        Player player = (Player) (Object) this;

        // 客户端不算这个（客户端的 Level 不是 ServerLevel，原版本来也不会调到这里）
        if (player.level().isClientSide()) {
            return;
        }
        // 不在原神模式：原样走原版
        if (!Boolean.TRUE.equals(player.getData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT))) {
            return;
        }
        // 「能飞就不吃摔伤」照旧：原版 Player#causeFallDamage 用的就是这一条
        // （NeoForge 的 mayFly()，不是 @Deprecated 的 Abilities#mayfly 字段）
        if (player.mayFly()) {
            return;
        }
        // 「被爆炸 / 风弹顶起来的那一摔不算」也照旧：这一档原版自己会处理，放行
        if (player.isIgnoringFallDamageFromCurrentImpulse() && player.currentImpulseImpactPos != null) {
            return;
        }

        PlayerCharactersAttachment attachment =
                player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        PGCharacter character = attachment == null ? null : attachment.getCurrentCharacter();
        if (character == null || character.getData().getCurrentHP() <= 0.0) {
            return;
        }

        double height = Math.max(player.fallDistance, fallDistance);
        // 这一摔是不是「下落攻击落的地」：正在下落攻击，或者这一格刚从下落攻击落地
        boolean plunging = PlungeState.isPlungingOrJustLanded(player);
        // 方块自带的减免乘数：普通方块 1.0 / 粘液块 0.0 / 干草 0.2 / 石笋 2.0
        float fraction = FallDamage.damageFraction(height, plunging) * Math.max(0f, multiplier);
        fraction = Math.min(fraction, 1f);

        if (fraction > 0f) {
            double maxHp = character.getData().getAttributeTotalValue(ModAttributes.MAX_HP.value());
            character.hurt((float) (maxHp * fraction));
        }

        cir.setReturnValue(false);
        cir.cancel();
    }
}
