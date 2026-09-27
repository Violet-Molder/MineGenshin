package com.linweiyun.genshin.client.performance;

import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * 把飘字的每帧开销读数挂进 F3 调试屏（渲染优化模块的观测端）。
 *
 * <p>注册见 {@code MinegenshinClient#registerDebugEntries}，走 NeoForge 的
 * {@code RegisterDebugEntriesEvent} —— 这是 Neo 26.2 提供的正式扩展点
 * （香草的调试屏已经改成「条目注册表」形态），所以不需要 mixin 去改调试屏本身。</p>
 *
 * <p>条目挂在默认档案（{@code DebugScreenProfile.DEFAULT}）的
 * {@code IN_OVERLAY}：按 F3 展开调试覆盖层时跟着出现，不想看时可以在
 * F3 的调试选项菜单里单独关掉这一条，不影响其它条目。
 * 服务器要求精简调试信息（{@code reducedDebugInfo}）时不显示。</p>
 */
public final class IndicatorDebugEntry implements DebugScreenEntry {

    @Override
    public void display(DebugScreenDisplayer displayer,
                        @Nullable Level serverOrClientLevel,
                        @Nullable LevelChunk clientChunk,
                        @Nullable LevelChunk serverChunk) {
        displayer.addLine(IndicatorPerfStats.line());
    }

    @Override
    public boolean isAllowed(boolean reducedDebugInfo) {
        return !reducedDebugInfo;
    }
}
