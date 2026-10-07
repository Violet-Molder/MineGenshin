package com.linweiyun.genshin.client.performance;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

/**
 * 把飘字的每帧开销读数挂进 F3 调试屏（渲染优化模块的观测端）。
 *
 * <p>挂在调试屏左侧文字列表的末尾：按 F3 展开调试覆盖层时跟着出现。
 * 服务器要求精简调试信息（{@code reducedDebugInfo}）时调试屏由原版自行裁剪，
 * 这里不做额外判断。</p>
 */
@EventBusSubscriber(value = Dist.CLIENT)
public final class IndicatorDebugEntry {

    private IndicatorDebugEntry() {}

    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        event.getLeft().add(IndicatorPerfStats.line());
    }
}
