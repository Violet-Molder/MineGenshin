package com.linweiyun.genshin.mixin.mixins;

import com.linweiyun.genshin.client.camera.CameraZoom;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 把 Ctrl + 滚轮 调出来的距离倍数乘进原版的第三人称机位距离。
 *
 * <p>只改传进 {@code getMaxZoom} 的「想拉多远」；那条方法内部照旧往八个方向打射线做碰撞，
 * 撞墙就把距离收到墙前，所以放大后依然不会穿墙。
 */
@Mixin(Camera.class)
public abstract class CameraZoomMixin {

    @ModifyVariable(method = "getMaxZoom", at = @At("HEAD"), argsOnly = true)
    private float minegenshin$applyZoomFactor(float maxZoom) {
        return maxZoom * CameraZoom.factor();
    }
}
