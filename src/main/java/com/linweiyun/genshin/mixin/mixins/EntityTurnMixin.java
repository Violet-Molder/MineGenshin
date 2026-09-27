package com.linweiyun.genshin.mixin.mixins;

import com.linweiyun.genshin.client.camera.ThirdPersonCamera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 「视角跟随」期间，鼠标的<b>水平</b>增量写给身体而不是镜头。
 *
 * <h2>为什么要拦在源头</h2>
 * {@code Entity.turn(yRot, xRot)}（{@code MouseHandler.turnPlayer} 唯一调用它）
 * 干的事是：{@code yRot += 增量}、{@code xRot += 增量}，<b>并且把
 * {@code yRotO} / {@code xRotO} 一起加上去</b>（插值基准）。
 *
 * <p>如果改成「先让原版转完镜头，再由 {@code ThirdPersonCamera} 把这一份搬回身体」，
 * 那么 {@code yRotO} 已经被带偏了 —— 渲染时镜头角度取的是
 * {@code Mth.rotLerp(partialTick, yRotO, yRot)}（{@code Entity.getViewYRot}），
 * 起点是「被鼠标转过的角度」、终点是「搬回去的角度」，于是每帧先跳一下再滑回来。
 *
 * <p>拦在这里之后，鼠标的水平增量当场就落到身体上，镜头一份都不留。
 * 镜头自己是<b>按帧</b>由 {@code ThirdPersonCamera.onComputeCameraAngles} 平滑地
 * 追着身体写（{@code yRot} 与 {@code yRotO} 写成同一个值），所以这里对
 * {@code yRot} / {@code yRotO} 的「减回去」只是兜底：万一相机那条路因为任何原因没跑，
 * 鼠标也不会偷偷把镜头从身体上带走。
 *
 * <h2>具体怎么做</h2>
 * 注入在 {@code TAIL}（不取消）：原版该做的事照做一遍（俯仰、夹角、坐骑回调都在里面），
 * 然后把水平那一份<b>原样减回镜头、加给身体</b>：
 * {@code yRot -= delta}、{@code yRotO -= delta}、{@code yBodyRot += delta}。
 * 不重写整段方法体，是为了不跟着原版版本更新漂移。
 *
 * <h2>只管本机玩家 + 原神模式 + 第三人称 + 正在跟随</h2>
 * 判断统一在 {@link ThirdPersonCamera#bodyFacing}（返回 {@code FOLLOW} 才动手）：
 * 怪物、其他玩家、原版玩家、第一人称、没在跟随的情况一律原封不动
 * （第一人称的镜头就是身体朝向，绝不能把鼠标转给身体）。
 */
@Mixin(Entity.class)
public abstract class EntityTurnMixin {

    @Inject(method = "turn(DD)V", at = @At("TAIL"))
    private void minegenshin$steerBodyWithMouse(double yaw, double pitch, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;

        if (ThirdPersonCamera.bodyFacing(self) != ThirdPersonCamera.BodyFacing.FOLLOW) {
            return;
        }
        if (!(self instanceof LivingEntity living)) {
            return;
        }

        // 与 Entity.turn 里那一行同一个换算（乘 0.15）
        float delta = (float) yaw * 0.15F;
        if (delta == 0.0F) {
            return;
        }

        self.setYRot(self.getYRot() - delta);
        self.yRotO -= delta;
        living.yBodyRot += delta;
    }
}
