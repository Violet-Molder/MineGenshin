package com.linweiyun.genshin.client.render.gui.hud;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.character.PGCharacterData;
import com.linweiyun.genshin.core.system.compat.PlayerStatBridge;
import com.linweiyun.genshin.core.world.TeyvatWorldInvasion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodData;

/**
 * 原神模式下的底栏：角色经验条 + 居中饱食度。
 *
 * <p>底栏从下往上依次是（数字是「离屏幕下沿多少像素」）：
 * <pre>
 *   0  ~ 22   原版快捷栏（原样不动）
 *   24 ~ 30   我们的长血条（{@code character_party.lss} 里 {@code #current_character_hp} 的 bottom 也是 24）
 *   32 ~ 37   原版经验条贴图，填的是<b>当前出战角色</b>的经验进度
 *   39 ~ 48   居中的原版饱食度图标
 * </pre>
 *
 * <p>用原版贴图直接 blit 的写法（不吃 ldlib）：{@code minecraft:hud/experience_bar_background}、
 * {@code minecraft:hud/food_full} 这些是 GUI 图集里的精灵，ldlib2 的 {@code SpriteTexture} 只按
 * 贴图文件路径取图，取不到图集精灵。所以这几样单独走一个原生 {@link net.minecraft.client.gui.GuiLayer}。
 *
 * <p>经验条的比例来自当前出战角色：{@code 当前经验 / 本次升级所需经验}，超过就按满条画
 * （升到突破上限后经验会溢出，这时候让条停在满格而不是超出去）。
 */
public final class GenshinBottomHud {

    /** 原版经验条贴图宽度 */
    private static final int XP_BAR_WIDTH = 182;
    /** 原版经验条贴图高度 */
    private static final int XP_BAR_HEIGHT = 5;
    /** 经验条下沿离屏幕下沿的像素：血条上沿（30）再往上留 2 像素 */
    private static final int XP_BAR_BOTTOM = 32;

    /** 一组元素之间的竖直间距 */
    private static final int ROW_GAP = 2;
    /** 饱食度图标边长 */
    private static final int FOOD_ICON_SIZE = 9;
    /** 饱食度图标水平间距 */
    private static final int FOOD_ICON_STEP = 8;
    /** 饱食度图标个数 */
    private static final int FOOD_ICON_COUNT = 10;

    private static final Identifier EXPERIENCE_BAR_BACKGROUND =
            Identifier.withDefaultNamespace("hud/experience_bar_background");
    private static final Identifier EXPERIENCE_BAR_PROGRESS =
            Identifier.withDefaultNamespace("hud/experience_bar_progress");
    private static final Identifier FOOD_EMPTY = Identifier.withDefaultNamespace("hud/food_empty");
    private static final Identifier FOOD_HALF = Identifier.withDefaultNamespace("hud/food_half");
    private static final Identifier FOOD_FULL = Identifier.withDefaultNamespace("hud/food_full");
    private static final Identifier FOOD_EMPTY_HUNGER =
            Identifier.withDefaultNamespace("hud/food_empty_hunger");
    private static final Identifier FOOD_HALF_HUNGER =
            Identifier.withDefaultNamespace("hud/food_half_hunger");
    private static final Identifier FOOD_FULL_HUNGER =
            Identifier.withDefaultNamespace("hud/food_full_hunger");

    private GenshinBottomHud() {
    }

    /** 这一层该不该画：只认「处于入侵世界 + 开着原神模式」。 */
    public static boolean shouldRender() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null
                && TeyvatWorldInvasion.isClientInvaded()
                && PlayerStatBridge.isGenshinMode(player);
    }

    public static void render(GuiGraphicsExtractor graphics) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !shouldRender()) {
            return;
        }

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        int xpTop = screenHeight - (XP_BAR_BOTTOM + XP_BAR_HEIGHT);
        int xpLeft = (screenWidth - XP_BAR_WIDTH) / 2;

        renderExperienceBar(graphics, player, xpLeft, xpTop);
        // 和原版一样：创造 / 旁观不画饱食度（那两排血心也是同一套可见性）
        if (canHurtPlayer()) {
            renderFood(graphics, player, screenWidth, xpTop - ROW_GAP - FOOD_ICON_SIZE);
        }
    }

    private static boolean canHurtPlayer() {
        var gameMode = Minecraft.getInstance().gameMode;
        return gameMode != null && gameMode.canHurtPlayer();
    }

    private static void renderExperienceBar(GuiGraphicsExtractor graphics, LocalPlayer player,
                                            int left, int top) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, EXPERIENCE_BAR_BACKGROUND,
                left, top, XP_BAR_WIDTH, XP_BAR_HEIGHT);

        // 原版这里是 progress = experienceProgress * 183，照抄；比例已经夹在 0~1，条不会画出去
        int progress = (int) (experienceRatio(player) * (XP_BAR_WIDTH + 1));
        if (progress > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, EXPERIENCE_BAR_PROGRESS,
                    XP_BAR_WIDTH, XP_BAR_HEIGHT, 0, 0, left, top, progress, XP_BAR_HEIGHT);
        }
    }

    private static void renderFood(GuiGraphicsExtractor graphics, LocalPlayer player,
                                   int screenWidth, int top) {
        FoodData foodData = player.getFoodData();
        int food = foodData.getFoodLevel();
        boolean hunger = player.hasEffect(MobEffects.HUNGER);

        Identifier empty = hunger ? FOOD_EMPTY_HUNGER : FOOD_EMPTY;
        Identifier half = hunger ? FOOD_HALF_HUNGER : FOOD_HALF;
        Identifier full = hunger ? FOOD_FULL_HUNGER : FOOD_FULL;

        int rowWidth = FOOD_ICON_STEP * (FOOD_ICON_COUNT - 1) + FOOD_ICON_SIZE;
        int left = (screenWidth - rowWidth) / 2;

        for (int i = 0; i < FOOD_ICON_COUNT; i++) {
            int x = left + i * FOOD_ICON_STEP;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, empty, x, top, FOOD_ICON_SIZE, FOOD_ICON_SIZE);
            if (i * 2 + 1 < food) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, full, x, top, FOOD_ICON_SIZE, FOOD_ICON_SIZE);
            } else if (i * 2 + 1 == food) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, half, x, top, FOOD_ICON_SIZE, FOOD_ICON_SIZE);
            }
        }
    }

    /**
     * 当前出战角色的升级进度（0~1）。
     *
     * <p>卡在突破上限时经验会溢过「本次升级所需」，这里夹成满条。
     */
    private static float experienceRatio(LocalPlayer player) {
        var attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        var character = attachment == null ? null : attachment.getCurrentCharacter();
        if (character == null) {
            return 0f;
        }
        PGCharacterData data = character.getData();
        if (data == null || data.getMaxExp() <= 0) {
            return 0f;
        }
        return Mth.clamp((float) data.getCurrentExp() / data.getMaxExp(), 0f, 1f);
    }
}
