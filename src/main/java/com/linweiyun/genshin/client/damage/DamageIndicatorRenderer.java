package com.linweiyun.genshin.client.damage;

import com.linweiyun.genshin.client.performance.IndicatorFramePlanner;
import com.linweiyun.genshin.client.performance.IndicatorGlyphCache;
import com.linweiyun.genshin.client.performance.IndicatorPerfStats;
import com.linweiyun.genshin.client.performance.HudRenderCaches;
import com.mojang.blaze3d.vertex.PoseStack;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Quaternionf;
import org.joml.Vector3fc;
import org.slf4j.Logger;

import java.util.List;

/**
 * 伤害飘字渲染器 —— 与血条 / 等级文字同为「世界空间提交」模式。
 *
 * <p>每帧在 {@link SubmitCustomGeometryEvent} 里把每个活跃飘字摆到它的世界坐标：
 * 平移到「相机相对位置」→ 乘相机朝向（命名牌口径的 billboard）→ 按世界尺度缩放，
 * 然后交给 {@link GradientTextRenderer} 提交带渐变的文字几何。</p>
 *
 * <p>因为几何本身就活在世界投影里，透视自带的近大远小就是飘字的距离表现，
 * 不再需要旧 HUD 方案那套手动投影 + 距离缩放（旧实现既依赖 GUI 尺度，也要自己算衰减）。</p>
 *
 * <h2>这一层的性能职责（渲染优化模块的入口）</h2>
 * <ol>
 *   <li><b>先规划再提交</b>：可见性剔除、条数上限、锚点位姿烘焙全部交给
 *       {@link IndicatorFramePlanner}，本类只负责取相机参数并调用一次
 *       {@link GradientTextRenderer#submitBatch}；</li>
 *   <li><b>不再逐条 pushPose/popPose</b>：旧实现每条飘字都要压栈、拷一份位姿再出栈，
 *       条数一多就是纯粹的矩阵拷贝开销，现在烘焙进
 *       {@link IndicatorFramePlanner.Entry#anchor}；</li>
 *   <li><b>不再逐条查语言表</b>：{@code I18n.get}（内部含 {@code String.format}）
 *       已经移到 {@link DamageIndicator#labelFor}，只在「文字 / 字体 / 语言」变化时重算。</li>
 * </ol>
 *
 * <p>这一层的开销读数由 {@link IndicatorPerfStats} 记录，按 F3 就能看到
 * 「规划 / 顶点生产各占多少毫秒、提交了几条」，不必借助外部 profiler。</p>
 */
@EventBusSubscriber(value = Dist.CLIENT)
public final class DamageIndicatorRenderer {
    public static final Logger LOGGER = ModLog.getLogger(LogGroup.RENDER);

    /**
     * 旧 HUD 的 scale 以 GUI 像素为单位（scale=2.2 时字高 9×2.2≈20 GUI 像素）。
     * 换算到世界空间时按 1080p / 自动 GUI 尺度校准，保持旧观感：
     * 2.2 × 0.0155 ≈ 0.034 方块/字体像素 ≈ 1.36 倍命名牌。
     *
     * @deprecated 真正的换算量在 {@link IndicatorFramePlanner#GUI_PIXEL_TO_BLOCK}，
     *             这里只是保持外部可读，两者数值必须一致。
     */
    @Deprecated
    public static final float GUI_PIXEL_TO_BLOCK = IndicatorFramePlanner.GUI_PIXEL_TO_BLOCK;

    private DamageIndicatorRenderer() {}

    public static void onIndicatorAdded(DamageIndicator indicator) {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        DamageIndicatorManager.tick();
    }

    @SubscribeEvent
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        List<DamageIndicator> active = DamageIndicatorManager.getActive();
        if (active.isEmpty()) {
            // 没有飘字也照常记一次「本帧 0 条」：否则 F3 上的读数会停在最后一批飘字的数字上，
            // 看不出「现在其实没东西在画」
            IndicatorPerfStats.recordPlan(0L, 0, 0);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        SubmitNodeCollector collector = event.getSubmitNodeCollector();
        PoseStack poseStack = event.getPoseStack();
        Font font = mc.font;
        if (collector == null || poseStack == null || font == null) return;

        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 camPos = camera.position();
        Quaternionf camRot = camera.rotation();
        // 直接把手里的向量接口传下去；这里再包一次 new Vec3(...) 就是每帧一次白分配
        Vector3fc forward = camera.forwardVector();

        List<IndicatorFramePlanner.Entry> entries = IndicatorFramePlanner.plan(
                active, poseStack.last().pose(), camPos, camRot, forward, font);
        GradientTextRenderer.submitBatch(collector, poseStack, entries);
    }

    /**
     * 退出世界时清掉客户端侧残留：活跃飘字列表、字形缓存、每帧规划用的对象池。
     *
     * <p>字形缓存抓着当前字体图集的纹理，换世界 / 换资源包后必须重建；规划池里
     * 抓着飘字实例引用，留着会白白拖住这些对象。</p>
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        DamageIndicatorManager.clear();
        IndicatorGlyphCache.clear();
        IndicatorFramePlanner.reset();
        HudRenderCaches.clear();
    }
}
