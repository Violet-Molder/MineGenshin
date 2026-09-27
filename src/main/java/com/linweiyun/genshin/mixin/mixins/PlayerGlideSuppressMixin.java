package com.linweiyun.genshin.mixin.mixins;

import com.linweiyun.genshin.core.system.combat.flight.GenshinFlight;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <b>原神模式下关掉原版的鞘翅滑翔</b>。
 *
 * <h2>为什么要关</h2>
 * 26.2 里「空中按跳」= 开鞘翅滑翔（{@code LocalPlayer#aiStep} →
 * {@code Player#tryToStartFallFlying()}）。而原神模式的开飞方式改成了<b>二连跳</b>
 * （原版那套创造飞行开关，见 {@code GenshinFlight}）：
 *
 * <ul>
 *   <li>带着鞘翅时，第一下跳之后第二下会被「滑翔」抢走 —— 二连跳根本轮不到飞行开关；</li>
 *   <li>滑翔姿态还会把「空中普攻 = 下落攻击」顶掉（滑翔中按左键不该是滑翔的那套）。</li>
 * </ul>
 *
 * <p>所以原神模式里直接把滑翔的入口关死：鞘翅在这一套里是「飞行的燃料」（扣耐久），
 * 不再负责滑翔。想恢复滑翔就把这个 mixin 摘掉。
 */
@Mixin(Player.class)
public class PlayerGlideSuppressMixin {

    @Inject(method = "tryToStartFallFlying", at = @At("HEAD"), cancellable = true)
    private void minegenshin$noGlideInGenshinMode(CallbackInfoReturnable<Boolean> cir) {
        Player player = (Player) (Object) this;
        // 创造 / 旁观照常（用户口径：创造模式还是正常的）；只有生存玩家在原神模式里不滑翔
        if (GenshinFlight.isGenshinMode(player) && !player.isCreative() && !player.isSpectator()) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}
