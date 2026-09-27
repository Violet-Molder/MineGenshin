package com.linweiyun.genshin.client.render.gui.hud;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.system.compat.PlayerStatBridge;
import com.linweiyun.genshin.core.world.TeyvatWorldInvasion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * 接管原版 HUD 的几层，让原神模式 / 兼容模式各自的底栏不打架。
 *
 * <h2>原神模式</h2>
 * <ul>
 *   <li>{@code PLAYER_HEALTH}：原版血心不画 —— 玩家血量由本 MOD 的长血条代表。
 *       但 {@code leftHeight} 照旧 +10 占位，护甲行不会因此掉一格。</li>
 *   <li>{@code FOOD_LEVEL}：原版右侧食物图标不画（{@link GenshinBottomHud} 在经验条上方居中画），
 *       {@code rightHeight} +10 占位，氧气泡不会因此掉一行。</li>
 *   <li>{@code CONTEXTUAL_INFO_BAR_BACKGROUND} / {@code CONTEXTUAL_INFO_BAR} / {@code EXPERIENCE_LEVEL}：
 *       原版经验条、等级数字、定位条都占着屏幕正下方那一条，原神模式下那一条已经给了角色经验条，
 *       所以整条都不画。</li>
 * </ul>
 * 已知取舍：原神模式下伤害吸收 / 困难模式血心的闪烁特效、以及定位条与跳跃蓄力条都不再显示 ——
 * 它们本来就和角色血条抢同一片位置。
 *
 * <h2>非原神模式</h2>
 * 只有「从原神模式退出来过」的玩家（{@link PlayerStatBridge#hasCharacterHealth}）才动：
 * 生命上限被角色抬到几百点后，原版血心会一层层往上摞成一堵墙，这里换成我们的短血条。
 * 没链接过的玩家一切照原版。
 */
public final class HudLayerOverride {

    /** 兼容模式下短血条用的三张贴图（都是 1200×60，按比例裁左侧一段来画） */
    private static final int BAR_TEXTURE_WIDTH = 1200;
    private static final int BAR_TEXTURE_HEIGHT = 60;
    private static final Identifier COMPAT_BAR_BACKGROUND =
            Minegenshin.id("gui/short_character_hp_green.png");
    private static final Identifier COMPAT_BAR_FILL =
            Minegenshin.id("gui/short_character_hp_bar_green.png");
    /** 拖尾层：白条乘拖尾色，和世界里怪物血条一个色 */
    private static final Identifier COMPAT_BAR_TRAIL =
            Minegenshin.id("gui/short_character_hp_bar_white.png");
    private static final int COMPAT_BAR_TRAIL_COLOR = 0xFFB3801A;

    /** 短血条尺寸：和右侧队友血条一致 */
    private static final int COMPAT_BAR_WIDTH = 80;
    private static final int COMPAT_BAR_HEIGHT = 4;
    /** 血心那一行高 9，短血条 4 高，往上偏 2 像素视觉上居中 */
    private static final int COMPAT_BAR_TOP_OFFSET = 2;

    /** 条里那行血量数字的字号倍数：4 像素高的条里塞得下，又还看得清 */
    private static final float COMPAT_BAR_TEXT_SCALE = 0.5f;
    /** 原版字体的行高，用来把数字在条里竖直居中 */
    private static final float FONT_LINE_HEIGHT = 9.0f;
    /** 血量数字的颜色：白字带阴影，压在绿色填充上也看得清 */
    private static final int COMPAT_BAR_TEXT_COLOR = 0xFFFFFFFF;

    /** 拖尾每秒衰减比例，和 {@code HPProgressBar} / 世界里怪物血条一致 */
    private static final float TRAIL_DECAY_PER_SECOND = 0.3f;
    /** 单帧最多推进多少秒，切窗口回来别让拖尾一次跳完 */
    private static final float TRAIL_MAX_STEP_SECONDS = 0.5f;

    private static float compatTrailRatio = 1.0f;
    private static long compatTrailMillis;
    private static boolean compatTrailPrimed;

    private HudLayerOverride() {
    }

    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.wrapLayer(VanillaGuiLayers.PLAYER_HEALTH, HudLayerOverride::wrapPlayerHealth);
        event.wrapLayer(VanillaGuiLayers.FOOD_LEVEL, HudLayerOverride::wrapFoodLevel);
        event.wrapLayer(VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND,
                HudLayerOverride::wrapContextualInfoBar);
        event.wrapLayer(VanillaGuiLayers.CONTEXTUAL_INFO_BAR, HudLayerOverride::wrapContextualInfoBar);
        event.wrapLayer(VanillaGuiLayers.EXPERIENCE_LEVEL, HudLayerOverride::wrapContextualInfoBar);
    }

    // ==================== 血量那一行 ====================

    private static GuiLayer wrapPlayerHealth(GuiLayer original) {
        return (graphics, deltaTracker) -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || !canHurtPlayer()) {
                original.render(graphics, deltaTracker);
                return;
            }

            if (isGenshinMode(player)) {
                reserveHealthRow();
                return;
            }

            if (TeyvatWorldInvasion.isClientInvaded() && PlayerStatBridge.hasCharacterHealth(player)) {
                renderCompatHealthBar(graphics, player);
                reserveHealthRow();
                return;
            }

            original.render(graphics, deltaTracker);
        };
    }

    /** 不画血心也要把那一行的高度记上，护甲 / 上方的原版叠层才不会整体往下挪。 */
    private static void reserveHealthRow() {
        Minecraft.getInstance().gui.hud.leftHeight += 10;
    }

    /**
     * 兼容模式（非原神模式）下的短血条：底槽 + 拖尾 + 填充。
     *
     * <p>比例从玩家属性反推（角色那段血占角色生命上限多少），不是读角色数据 ——
     * 非原神模式下角色的 currentHP 是冻结的。
     */
    private static void renderCompatHealthBar(GuiGraphicsExtractor graphics, LocalPlayer player) {
        float ratio = Mth.clamp(PlayerStatBridge.characterHealthRatio(player), 0.0f, 1.0f);
        float trail = tickCompatTrail(ratio);

        int x = graphics.guiWidth() / 2 - 91;
        int y = graphics.guiHeight() - Minecraft.getInstance().gui.hud.leftHeight + COMPAT_BAR_TOP_OFFSET;

        blitCropped(graphics, COMPAT_BAR_BACKGROUND, x, y, 1.0f, -1);
        if (trail > 0.0f) {
            blitCropped(graphics, COMPAT_BAR_TRAIL, x, y, trail, COMPAT_BAR_TRAIL_COLOR);
        }
        if (ratio > 0.0f) {
            blitCropped(graphics, COMPAT_BAR_FILL, x, y, ratio, -1);
        }
        renderCompatHealthText(graphics, player, x, y);
    }

    /**
     * 按比例<b>裁</b>出贴图左边那一截（和出战长条一个口径）。
     *
     * <p>几何宽度和采样区段一起缩：{@code ratio} 只决定「画到哪一刀切」，贴图本身不会被压扁。
     * 只缩宽度不改采样，两端楔形的斜边角度就变了，填充条底下会露出拖尾的尖角。
     *
     * @param ratio 要显示的比例（0~1）
     * @param color 染色，{@code -1} 表示原色
     */
    private static void blitCropped(GuiGraphicsExtractor graphics, Identifier texture,
                                    int x, int y, float ratio, int color) {
        int width = Math.round(COMPAT_BAR_WIDTH * ratio);
        if (width <= 0) {
            return;
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0f, 0.0f,
                width, COMPAT_BAR_HEIGHT,
                Math.max(1, Math.round(BAR_TEXTURE_WIDTH * ratio)), BAR_TEXTURE_HEIGHT,
                BAR_TEXTURE_WIDTH, BAR_TEXTURE_HEIGHT,
                color);
    }

    /**
     * 条里的血量数字：玩家当前血量 / 上限，取整、半号字体，画在整条正中。
     *
     * <p>这里显示的是<b>玩家本体</b>的血量（本 MOD 的角色加成已经并进上限里了），
     * 和别的界面看到的那个数一致；条本身画的是「角色那一截占了多少」，两者在最后那点
     * 玩家自己的血量（低于原版上限）上会略有出入。
     */
    private static void renderCompatHealthText(GuiGraphicsExtractor graphics, LocalPlayer player,
                                               int x, int y) {
        String text = Math.round(player.getHealth()) + "/" + Math.round(player.getMaxHealth());
        Font font = Minecraft.getInstance().font;

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x + COMPAT_BAR_WIDTH / 2.0f,
                y + (COMPAT_BAR_HEIGHT - FONT_LINE_HEIGHT * COMPAT_BAR_TEXT_SCALE) / 2.0f);
        pose.scale(COMPAT_BAR_TEXT_SCALE, COMPAT_BAR_TEXT_SCALE);
        graphics.text(font, text, -font.width(text) / 2, 0, COMPAT_BAR_TEXT_COLOR, true);
        pose.popMatrix();
    }

    /**
     * 拖尾推进，规则和 {@code HPProgressBar} 里那条一样：掉血才留拖尾，回血直接跟上；
     * 衰减按真实流逝时间算。
     */
    private static float tickCompatTrail(float ratio) {
        long now = Util.getMillis();
        long elapsed = now - compatTrailMillis;
        // 首帧、或者中间断了一大截（切进原神模式、切出窗口）都重新起步，不补旧账
        if (!compatTrailPrimed || elapsed > TRAIL_MAX_STEP_SECONDS * 1000.0f) {
            compatTrailPrimed = true;
            compatTrailMillis = now;
            compatTrailRatio = ratio;
            return ratio;
        }

        float seconds = Mth.clamp(elapsed / 1000.0f, 0.0f, TRAIL_MAX_STEP_SECONDS);
        compatTrailMillis = now;
        compatTrailRatio = compatTrailRatio > ratio
                ? Math.max(ratio, compatTrailRatio - TRAIL_DECAY_PER_SECOND * seconds)
                : ratio;
        return compatTrailRatio;
    }

    // ==================== 饱食度 / 正下方那一条 ====================

    private static GuiLayer wrapFoodLevel(GuiLayer original) {
        return (graphics, deltaTracker) -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null && canHurtPlayer() && isGenshinMode(player)) {
                // 原神模式：饱食度挪到经验条上方居中，右侧那排空出来但高度照记
                Minecraft.getInstance().gui.hud.rightHeight += 10;
                return;
            }
            original.render(graphics, deltaTracker);
        };
    }

    /** 原版经验条 / 等级数字 / 定位条 / 跳跃蓄力条都在屏幕正下方那一条，原神模式整条让给角色经验条。 */
    private static GuiLayer wrapContextualInfoBar(GuiLayer original) {
        return (graphics, deltaTracker) -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null && isGenshinMode(player)) {
                return;
            }
            original.render(graphics, deltaTracker);
        };
    }

    private static boolean isGenshinMode(LocalPlayer player) {
        return TeyvatWorldInvasion.isClientInvaded() && PlayerStatBridge.isGenshinMode(player);
    }

    /** 和原版血心 / 饱食度同一套可见性：创造、旁观时这几样本来就不画。 */
    private static boolean canHurtPlayer() {
        var gameMode = Minecraft.getInstance().gameMode;
        return gameMode != null && gameMode.canHurtPlayer();
    }
}
