package com.linweiyun.genshin.client.camera;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.client.render.character.AttachmentHelper;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 原神模式第三人称下的镜头距离 —— 按住 Ctrl 滚轮调整。
 *
 * <p>倍数乘在原版的第三人称机位距离上：1 倍就是原版距离，最远 2 倍、最近 0.5 倍。
 * 这里只改「想拉多远」，真正的落点仍由原版 {@code Camera#getMaxZoom} 的镜头碰撞决定
 * ——它会从眼睛往八个方向各打一条射线，撞到方块就把距离收到墙前，所以镜头不会穿墙，
 * 也不会跑到角色所在空间之外。
 */
@EventBusSubscriber(modid = Minegenshin.MOD_ID, value = Dist.CLIENT)
public final class CameraZoom {

    /** 最近：原版距离的一半。 */
    public static final float MIN_FACTOR = 0.5F;

    /** 最远：原版距离的两倍。 */
    public static final float MAX_FACTOR = 2.0F;

    /** 滚轮一格的距离倍数（向前滚是拉近，所以取的是它的倒数）。 */
    private static final float FACTOR_PER_NOTCH = 1.1F;

    private static float factor = 1.0F;

    private CameraZoom() {
    }

    /**
     * 当前生效的距离倍数。
     *
     * <p>不满足「原神模式 + 第三人称 + 看的还是自己」时恒为 1，也就是完全交给原版。
     */
    public static float factor() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getCameraEntity() != minecraft.player) {
            return 1.0F;
        }
        if (minecraft.options.getCameraType().isFirstPerson()) {
            return 1.0F;
        }
        return AttachmentHelper.isGenshinMode(minecraft.player) ? factor : 1.0F;
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null) {
            return;
        }
        if (minecraft.options.getCameraType().isFirstPerson()
                || !AttachmentHelper.isGenshinMode(minecraft.player)) {
            return;
        }
        if (!isControlDown(minecraft)) {
            return;
        }

        double delta = event.getScrollDeltaY();
        if (delta == 0.0) {
            return;
        }
        // delta 向前滚为正；向前滚拉近，所以指数取负
        factor = Mth.clamp((float) (factor * Math.pow(FACTOR_PER_NOTCH, -delta)), MIN_FACTOR, MAX_FACTOR);
        // 这一下滚轮归镜头，不换快捷栏
        event.setCanceled(true);
    }

    private static boolean isControlDown(Minecraft minecraft) {
        long window = minecraft.getWindow().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }
}
