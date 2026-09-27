package com.linweiyun.genshin.mixin.mixins;

import com.linweiyun.genshin.core.system.control.ControlService;
import com.linweiyun.genshin.core.system.control.TickActionSuppressor;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <b>苦力怕引信 ← 通用打断的第三层</b>（用户实测反馈：被打了照样炸）。
 *
 * <h2>为什么光有 AI 闸门拦不住它</h2>
 * 它的引信<b>不在 AI 管线里</b>：{@code Creeper.tick()} 自己的两行就是全部 ——
 * {@code swell += getSwellDir()}，然后 {@code if (swell >= maxSwell) explodeCreeper();}。
 * {@code SwellGoal} 只负责<b>点火</b>（把 {@code swellDir} 拨到 1），
 * 点着之后就没它的事了。所以「停 Goal」「掐 AI」都对已经在走的引信无效，
 * 连原版自己的 {@code setNoAi(true)} 都拦不住（NoAI 苦力怕照样炸）。
 *
 * <h2>怎么拦</h2>
 * 把它拨回<b>没点火</b>的样子：{@code swellDir = -1}（消退方向）、
 * {@code swell = 0}、{@code oldSwell = 0}（表现上的「膨胀程度」也跟着归零，
 * 不会出现「没炸但身体一直是鼓的」）。这三个都是私有字段，所以这里用
 * {@code @Accessor} 开门。
 *
 * <p>两处都接：
 * <ul>
 *   <li><b>本刻的 HEAD 注入</b>：打断这一下和它的 {@code tick()} 谁先谁后不确定，
 *       如果它在本刻晚一点 tick，只在刻末压制就会漏掉「29 → 30 → 炸」这一帧；</li>
 *   <li><b>{@link TickActionSuppressor}</b>：窗口里每刻再压一次
 *       （由 {@code Controllable.cancelOngoingAction} 转发）。</li>
 * </ul>
 *
 * <p><b>已知边界</b>：{@code maxSwell} 被 NBT 改成 1 这种极端情况下，
 * 本刻 tick 仍可能凑满引信。正常生成的苦力怕是 30，走不到。
 */
@Mixin(Creeper.class)
public abstract class CreeperFuseMixin implements TickActionSuppressor {

    /** 引信已经走了多久（原版私有字段 {@code swell}）。 */
    @Accessor("swell")
    protected abstract int minegenshin$swell();

    @Accessor("swell")
    protected abstract void minegenshin$setSwell(int value);

    /** 上一刻的引信值，只影响渲染出来的膨胀程度。 */
    @Accessor("oldSwell")
    protected abstract void minegenshin$setOldSwell(int value);

    @Override
    public void minegenshin$suppressTickAction() {
        // 方向拨到「消退」：本刻 tick 里的 swell += dir 也会把它往下推，凑不满
        ((Creeper) (Object) this).setSwellDir(-1);
        if (minegenshin$swell() != 0) {
            minegenshin$setSwell(0);
        }
        minegenshin$setOldSwell(0);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void minegenshin$cutFuseWhileInterrupted(CallbackInfo ci) {
        if (ControlService.hasStandStill((Creeper) (Object) this)) {
            minegenshin$suppressTickAction();
        }
    }
}
