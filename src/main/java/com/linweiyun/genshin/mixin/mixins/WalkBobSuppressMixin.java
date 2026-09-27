package com.linweiyun.genshin.mixin.mixins;

import com.linweiyun.genshin.client.combat.state.ActionStateMachine;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 持续型招式（大剑重击 / 木偶重击）期间，<b>关掉走路镜头摇晃</b>。
 *
 * <h2>为什么会有这个需求</h2>
 * 那段动画里角色是「坐上飞行坐骑、微微浮空」的，但玩家照样能用 WASD 位移。
 * 位移是按「走路」算的，于是镜头照旧一上一下地晃 —— 一边浮空骑坐骑一边走路摇晃，
 * 观感直接崩掉。
 *
 * <p>用户明确要求<b>不要动常态设置</b>（{@code 选项 → 视角摇晃} 保持原样，平时该晃还晃），
 * 所以这里只在那一段状态里把摇晃归零，等价于「这段时间不算走路」。
 *
 * <h2>注入点</h2>
 * {@code AbstractClientPlayer#updateBob()} 是本机玩家每刻算「这一帧的摇晃量」的唯一入口
 * （原版实现：站在地上时取水平速度的 1/10 当目标值，交给 {@code avatarState().updateBob(f)}
 * 做跟随；在空中直接喂 0）。这里在方法头取消掉、并把当前值清零，
 * 于是镜头晃动<b>立刻</b>归零，而不是慢慢淡出。
 *
 * <p>只管<b>本机玩家</b>（别人的屏幕不在我们这边），并且只在持续型状态里生效
 * （{@link ActionStateMachine#currentStateLoops()}）—— 状态一结束就自动恢复，
 * 不需要任何一处记得关。
 */
@Mixin(AbstractClientPlayer.class)
public abstract class WalkBobSuppressMixin {

    @Inject(method = "updateBob()V", at = @At("HEAD"), cancellable = true)
    private void minegenshin$suppressWalkBob(CallbackInfo ci) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;

        if (!(self instanceof LocalPlayer)) {
            return;
        }
        if (!ActionStateMachine.currentStateLoops()) {
            return;
        }

        self.avatarState().resetBob();
        ci.cancel();
    }
}
