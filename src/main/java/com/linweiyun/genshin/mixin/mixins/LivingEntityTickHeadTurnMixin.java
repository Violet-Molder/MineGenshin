package com.linweiyun.genshin.mixin.mixins;

import com.linweiyun.genshin.client.camera.ThirdPersonCamera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 第三人称的「视角独立」：<b>转视角时角色不转身</b>。
 *
 * <h2>原版这个方法做两件事</h2>
 * <ol>
 *   <li>身体朝传入目标插值（{@code * 0.3F}）；</li>
 *   <li>把身体往视角方向拽，好让「头部相对身体」不超过
 *       {@link LivingEntity#getMaxHeadRotationRelativeToBody()}（默认 50°）。</li>
 * </ol>
 * 第 2 条就是「站着不动转鼠标、身体慢慢跟着转」的来源，也是本模组要去掉的那一条。
 * 方法结束后还会把「头部相对身体超过 ±90°」时把传入的头部朝向翻转 180° 再返回，
 * 那一步照旧保留。
 *
 * <h2>本模组怎么改</h2>
 * 分三种情形，判断在 {@link ThirdPersonCamera#bodyFacing}：
 * <ul>
 *   <li>{@code VANILLA}（怪物 / 其他玩家 / 没进原神模式 / 第一人称）→ 一行不改，原版照跑；</li>
 *   <li>{@code INDEPENDENT}（常态第三人称）→ 只保留第 1 条：身体目标改成「本刻的实际移动方向」，
 *       不做第 2 条；</li>
 *   <li>{@code FOLLOW}（视角跟随，大剑持续重击）→ 两条都不做：身体由
 *       {@link ThirdPersonCamera} 用鼠标驾驶，这里再插一次值会与鼠标抢方向盘。</li>
 * </ul>
 *
 * <h2>身体目标为什么不用传进来的那个</h2>
 * 原版在「移动方向与视线夹角落在 95°~265°」之间时会把身体目标翻 180°，而 WASD 是<b>镜头</b>参照的：
 * 按 S 时的移动方向正对镜头、夹角正好 180°，于是被翻成「背对镜头」。本模组反过来 ——
 * <b>身体永远朝实际移动方向</b>，按 S 就是正对镜头往后退。所以这里按本刻位移自己算角度
 * （{@code atan2(movedZ, movedX)} 换成 MC 的 yaw，再乘 {@code 0.3F} 插值），
 * 移动阈值沿用原版的 {@code 2.5E-3F}；站着不动时目标 = 当前角度，等价于「不动就不改」。
 *
 * <p>挥臂（{@code attackAnim > 0}）时原版传给本方法的目标是<b>视角方向</b>，
 * 照它插值就等于「攻击一下就把人转向镜头」，与视角独立冲突；攻击该朝哪由
 * {@code AttackApproach} 自己管（它只写 {@code yBodyRot}，从不碰镜头），所以这一支跳过不转。
 *
 * <p>{@code yBodyRot} 正是模型朝向（{@code CharacterRenderDispatcher} 读它），
 * 所以「不动它」就等于「转视角时角色不转身」。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityTickHeadTurnMixin {

    @Inject(method = "tickHeadTurn(FF)F", at = @At("HEAD"), cancellable = true)
    private void minegenshin$independentBodyFacing(float bodyRotTarget, float headRot,
                                                  CallbackInfoReturnable<Float> cir) {
        LivingEntity self = (LivingEntity) (Object) this;

        ThirdPersonCamera.BodyFacing facing = ThirdPersonCamera.bodyFacing(self);
        if (facing == ThirdPersonCamera.BodyFacing.VANILLA) {
            return;
        }

        if (facing == ThirdPersonCamera.BodyFacing.INDEPENDENT && self.attackAnim <= 0.0F) {
            // 身体目标 = 本刻的实际位移方向；不移动就保持原角度。
            // 参数 bodyRotTarget 故意不用：它带着原版那次 180° 翻转，照抄就回到「一按 S 背过去」。
            float target = self.yBodyRot;
            double movedX = self.getX() - self.xo;
            double movedZ = self.getZ() - self.zo;
            if (movedX * movedX + movedZ * movedZ > 2.5E-3F) {
                // 与原版同一个换算：atan2(dz, dx) 是世界方向角，减 90° 得到 MC 的 yaw
                target = (float) Mth.atan2(movedZ, movedX) * (180.0F / (float) Math.PI) - 90.0F;
            }
            float delta = Mth.wrapDegrees(target - self.yBodyRot);
            self.yBodyRot += delta * 0.3F;
        }

        // 头部照旧：差超过 ±90° 时翻 180°（原版那一步），只是身体不再被拉向视角。
        float headDelta = Mth.wrapDegrees(self.getYRot() - self.yBodyRot);
        cir.setReturnValue(headDelta < -90.0F || headDelta >= 90.0F ? headRot * -1.0F : headRot);
    }
}