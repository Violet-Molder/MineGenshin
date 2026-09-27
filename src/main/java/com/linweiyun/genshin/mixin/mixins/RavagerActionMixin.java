package com.linweiyun.genshin.mixin.mixins;

import com.linweiyun.genshin.core.system.control.ControlService;
import com.linweiyun.genshin.core.system.control.TickActionSuppressor;
import net.minecraft.world.entity.monster.Ravager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <b>劫掠兽的三个动作计时器 ← 通用打断的第三层。</b>
 *
 * <h2>为什么要有它</h2>
 * 劫掠兽的攻击、被击晕、咆哮是三个私有计时器，在 {@code Ravager.aiStep()} 里递减
 * （不在 AI 管线，闸门拦不住）：
 * <ul>
 *   <li>{@code attackTick}：扑咬的动画与判定窗口（{@code attackTick = 10} 起跳）；</li>
 *   <li>{@code stunnedTick}：被击晕，走到 0 时<b>自己接一段咆哮</b>；</li>
 *   <li>{@code roarTick}：走到 10 那一下 {@code roar()} —— 击退 + 拉长玩家硬直。</li>
 * </ul>
 * 三个都归零 = 它从「正在干一件事」变回完全静止的那一态；
 * 顺带 {@code isImmobile()} 也回到 false（它就是拿这三个值当的判据）。
 *
 * <h2>两处都接</h2>
 * 与苦力怕同一套：{@code aiStep} 的 HEAD 注入处理「本刻先后不确定」，
 * {@link TickActionSuppressor} 让窗口里每刻再压一次。
 */
@Mixin(Ravager.class)
public abstract class RavagerActionMixin implements TickActionSuppressor {

    @Accessor("attackTick")
    protected abstract void minegenshin$setAttackTick(int value);

    @Accessor("stunnedTick")
    protected abstract void minegenshin$setStunnedTick(int value);

    @Accessor("roarTick")
    protected abstract void minegenshin$setRoarTick(int value);

    @Override
    public void minegenshin$suppressTickAction() {
        minegenshin$setAttackTick(0);
        minegenshin$setStunnedTick(0);
        minegenshin$setRoarTick(0);
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void minegenshin$clearActionTicksWhileInterrupted(CallbackInfo ci) {
        if (ControlService.hasStandStill((Ravager) (Object) this)) {
            minegenshin$suppressTickAction();
        }
    }
}
