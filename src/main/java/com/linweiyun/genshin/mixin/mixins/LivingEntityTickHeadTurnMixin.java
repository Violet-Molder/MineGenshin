package com.linweiyun.genshin.mixin.mixins;

import com.linweiyun.genshin.client.camera.ThirdPersonCamera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第三人称的「视角独立」：<b>转视角时角色不转身</b>。
 *
 * <h2>原版这里做了什么</h2>
 * {@code LivingEntity.tick()} 每 tick 先算出一个「身体该朝哪」，再交给本方法
 * （见反编译 {@code LivingEntity.java:2755} 一带）：站着不动时那个目标就是身体当前角度，
 * 移动中换成移动方向，挥臂（{@code attackAnim > 0}）时换成视角方向。
 *
 * <p>而 {@code tickHeadTurn}（{@code LivingEntity.java:2908}）里其实是<b>两件事</b>：
 * <ol>
 *   <li>身体朝传入目标插值（{@code * 0.3F}）；</li>
 *   <li>再把身体往视角方向拽，好让「头部相对身体」不超过
 *       {@code getMaxHeadRotationRelativeToBody()}（默认 50°）。</li>
 * </ol>
 * 第 2 条就是「站着不动转鼠标，身体慢慢跟着转」的来源 —— 也正是本模组要去掉的那一条。
 *
 * <h2>怎么改</h2>
 * 分三种情形，判断在 {@link ThirdPersonCamera#bodyFacing}：
 * <ul>
 *   <li>{@code VANILLA}（怪物 / 其他玩家 / 没进原神模式 / 第一人称）→ 一行不改，原版照跑；</li>
 *   <li>{@code INDEPENDENT}（常态第三人称）→ 只保留第 1 条，第 2 条整个不做；</li>
 *   <li>{@code FOLLOW}（视角跟随，大剑持续重击）→ 整个跳过。身体由
 *       {@code ThirdPersonCamera} 用鼠标驾驶，这里再插一次值就会和鼠标抢方向盘。</li>
 * </ul>
 *
 * <h2>INDEPENDENT 下的写法：自己算方向，<b>不抄原版那段 180° 翻转</b></h2>
 * 原版在「移动方向与视线夹角落在 95°~265°」之间时会把身体目标翻 180°
 * （{@code LivingEntity.java:2916-2921} 的 {@code yBodyRotT = walkDirection - 180.0F}）。
 * 而 WASD 是<b>镜头</b>参照的（见 {@link ThirdPersonCamera} 类注释第 3 条），按 S 时的移动方向
 * 正对镜头、夹角正好 180°，于是被翻成「背对镜头」—— 这就是「一按 S 角色立刻背过去」的
 * <b>唯一</b>来源。
 *
 * <p>本模组反过来：<b>身体永远朝实际移动方向</b>，不翻。按 S 时移动方向指着镜头，人就
 * <b>正对镜头往后退</b>（斜向后退 S+A/D 同理，朝斜后的实际方向走）。所以这里不再走原版传进来的
 * {@code targetBodyRot}，而是按本刻位移自己算角度 —— 公式与原版同一套（{@code atan2(movedZ,
 * movedX)} 换成 MC 的 yaw，再乘 {@code 0.3F} 插值），移动阈值也用原版那个
 * {@code 0.0025000002F}；站着不动时目标 = 当前角度，等价于原版「不动就不改」。
 *
 * <p>「转视角不转身」仍然成立：没位移时这段什么都不做，转鼠标一个字段都不会变。
 *
 * <p>按 A/D 侧移时身体照旧侧过去（原版规则 1），不要在这里加夹角限制 —— 加了侧移就不转了，
 * 实测手感是错的。只有「后退该朝哪」这一处与原版相反。
 *
 * <p>挥臂（{@code attackAnim > 0}）时原版那一路传进来的是<b>视角方向</b>，
 * 照原版插值就等于「攻击一下就把人转向镜头」，和「视角独立」冲突；
 * 攻击该朝哪由 {@code AttackApproach} 自己管（它只写 {@code yBodyRot}，从不碰镜头），
 * 所以这一支跳过不转。
 *
 * <p>{@code yBodyRot} 正是模型朝向（{@code CharacterRenderDispatcher} 读它），
 * 所以「不动它」=「转视角时角色不转身」。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityTickHeadTurnMixin {

    @Inject(method = "tickHeadTurn(F)V", at = @At("HEAD"), cancellable = true)
    private void minegenshin$independentBodyFacing(float targetBodyRot, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;

        ThirdPersonCamera.BodyFacing facing = ThirdPersonCamera.bodyFacing(self);
        if (facing == ThirdPersonCamera.BodyFacing.VANILLA) {
            return;
        }

        if (facing == ThirdPersonCamera.BodyFacing.INDEPENDENT && self.attackAnim <= 0.0F) {
            // 身体目标 = 本刻的实际位移方向；不移动就保持原角度。
            //
            // 只比原版少一件事：原版「移动方向与视线夹角 > 95° 就把目标翻 180°」那条分支
            // （LivingEntity.java:2916-2921）这里不做。按 S 时移动方向正对镜头，翻面就等于
            // 把背留给玩家 —— 本模组要的是正对镜头往后退，见类注释。
            //
            // 参数 targetBodyRot 故意不用：它带着原版那次翻转，照抄就回到「一按 S 背过去」。
            // 注入签名必须和原版方法一致，所以参数留着不用。
            float target = self.yBodyRot;
            double movedX = self.getX() - self.xo;
            double movedZ = self.getZ() - self.zo;
            if (movedX * movedX + movedZ * movedZ > 0.0025000002F) {
                // 与原版同一个换算：atan2(dz, dx) 是世界方向角，减 90° 得到 MC 的 yaw
                target = (float) Mth.atan2(movedZ, movedX) * (180.0F / (float) Math.PI) - 90.0F;
            }
            float delta = Mth.wrapDegrees(target - self.yBodyRot);
            self.yBodyRot += delta * 0.3F;
        }
        // 原版第 2 条（把身体拉向视角以维持头部 ±50°）两条分支都不做 —— 那正是
        // 「转视角时身体跟着转」的来源，而 FOLLOW 下的身体另有驾驶员。

        ci.cancel();
    }
}
