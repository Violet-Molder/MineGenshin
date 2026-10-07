package com.linweiyun.genshin.mixin.mixins;

import com.linweiyun.genshin.client.combat.state.ActionStateMachine;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 持续型招式期间，把本机玩家的走路镜头摇晃归零。
 *
 * <p>{@code Player#aiStep} 每刻算一次 {@code bob} / {@code oBob}（走路镜头摇晃的值），
 * 这里在它算完之后把两个值清零 —— 等价于「这段时间不算走路」。只影响本机玩家，
 * 且只在持续型状态里生效（{@link ActionStateMachine#currentStateLoops()}），
 * 状态一结束就自动恢复。</p>
 */
@Mixin(Player.class)
public abstract class WalkBobSuppressMixin {

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void minegenshin$suppressWalkBob(CallbackInfo ci) {
        Player self = (Player) (Object) this;

        if (!(self instanceof LocalPlayer)) {
            return;
        }
        if (!ActionStateMachine.currentStateLoops()) {
            return;
        }

        self.bob = 0.0F;
        self.oBob = 0.0F;
    }
}
