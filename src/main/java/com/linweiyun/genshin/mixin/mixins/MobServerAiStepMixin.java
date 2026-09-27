package com.linweiyun.genshin.mixin.mixins;

import com.linweiyun.genshin.core.system.control.ControlService;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <b>通用打断的 AI 闸门</b> —— 静止窗口里把生物的 {@code serverAiStep()} 整段掐掉。
 *
 * <h2>为什么闸门下在 {@code serverAiStep}</h2>
 * 原版 AI 的最高一级是 {@code LivingEntity.aiStep()} 里那一句
 * {@code if (this.isEffectiveAi() && !level().isClientSide()) this.serverAiStep();}，
 * 而 {@code serverAiStep()} 是 {@code Mob} 里 {@code protected final} 的<b>总闸</b>，
 * 子类改不了它。它肚子里依次是：
 * <pre>
 *   noActionTime++ → sensing.tick()（探测）
 *   → targetSelector / goalSelector（目标与 Goal 的取舍）
 *   → navigation.tick()
 *   → customServerAiStep(level)   ← Brain 类怪的 getBrain().tick() 就在这里
 *   → moveControl / lookControl / jumpControl
 * </pre>
 * 所以掐掉它一处，等于<b>一次性停掉这套 AI 的全部环节</b>：
 * 正在跑的 Goal 不再 tick、新的 Goal 不会被选上、Brain 的 behavior 不跑、
 * 导航不再更新、视线与移动控制器停下。这就是「打断目标目前的所有动作和行为」
 * 在通用层面能落到的最上游的那一刀。
 *
 * <h2>为什么不直接掐 {@code aiStep}</h2>
 * {@code aiStep} 里还有<b>物理</b>：重力、摩擦、{@code travel}（动量结算）。
 * 整段掐掉会让被击退的怪停在半空、掉不下来也不滑动，看起来像卡住了。
 * 闸门只拦 AI，物理照跑 —— 所以击退、下落、被牵引都还是正常的。
 *
 * <h2>为什么不直接用原版的 {@code setNoAi(true)}</h2>
 * 两个原因：
 * <ol>
 *   <li>它是<b>同步标记</b>、进 NBT：忘了松开就会留下一只永久定身的怪；
 *       窗口是纯服务端的临时时间戳，天然不会写进存档；</li>
 *   <li>冻结（{@code freezeAction}）也抢这个开关 —— 两套机制共用一个标记迟早打架；</li>
 *   <li>它<b>拦不住</b>写在 {@code tick()} / {@code aiStep()} 里的动作（苦力怕照样炸），
 *       所以它连「够用」都不算。</li>
 * </ol>
 *
 * <h2>它拦不住什么（这才是需要有第三层的原因）</h2>
 * 不走 AI 管线的状态机：苦力怕引信（{@code Creeper.tick()}）、女巫喝药
 * （{@code Witch.aiStep()}）、劫掠兽的计时器（{@code Ravager.aiStep()}）……
 * 这些由 {@code ControlService} 每刻调的 {@code Controllable.cancelOngoingAction}
 * 与 {@code TickActionSuppressor} 补上。
 *
 * <p>窗口是服务端的时间戳（{@code ControlService.STANDSTILL}），客户端那个表永远是空的，
 * 所以这一句在客户端是空操作。
 */
@Mixin(Mob.class)
public abstract class MobServerAiStepMixin {

    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void minegenshin$gateAiWhileInterrupted(CallbackInfo ci) {
        if (ControlService.hasStandStill((Mob) (Object) this)) {
            ci.cancel();
        }
    }
}
